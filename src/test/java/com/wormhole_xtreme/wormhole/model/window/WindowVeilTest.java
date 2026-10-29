package com.wormhole_xtreme.wormhole.model.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.model.GateSource;
import com.wormhole_xtreme.wormhole.model.freya.FreyaCompanion;
import com.wormhole_xtreme.wormhole.model.window.WindowShape.Spot;

/**
 * What a mirror hides from a viewer, and what it judges to be inside the view (#430).
 *
 * <p>The first tests are about the companion: a mirror leaves her visibility alone. She is hidden by default and shown to her owner. For such an entity a hide removes the
 * owner's exception and a show adds one, so a mirror veiling her took her away from her owner and
 * then, on unveiling, showed her to whichever other player had been looking.
 */
class WindowVeilTest
{
    private WormholeXTreme plugin;

    @BeforeEach
    void setUp() throws Exception
    {
        plugin = mock(WormholeXTreme.class);
        PluginTestSupport.install(plugin);
        ConfigTestSupport.clear();
        FreyaCompanion.forgetAll();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        FreyaCompanion.forgetAll();
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    @Test
    void aMirrorNeverVeilsACompanion()
    {
        final World world = mock(World.class);
        final Cat freya = mock(Cat.class);
        when(freya.isValid()).thenReturn(true);
        when(world.spawn(any(Location.class), eq(Cat.class))).thenReturn(freya);
        final Player owner = mock(Player.class);
        when(owner.getUniqueId()).thenReturn(UUID.randomUUID());
        when(owner.getLocation()).thenReturn(new Location(world, 0.0, 64.0, 0.0));
        FreyaCompanion.spawnFor(owner);

        assertFalse(WindowVeil.veilable(freya),
            "veiling her hides her from her owner and unveiling shows her to a stranger");
    }

    @Test
    void anOrdinaryCatIsStillVeiled()
    {
        assertTrue(WindowVeil.veilable(mock(Cat.class)),
            "only the companion is exempt; a real cat inside the view is still hidden");
    }

    @Test
    void playersAreNeverVeiled()
    {
        assertFalse(WindowVeil.veilable(mock(Player.class)), "hiding a player drops them off the tab list");
    }

    // ---- what is inside the view, as judged for the creatures in it (#430) ----

    private static final WindowShape SHAPE = new WindowShape(new Spot(10, 64, 20), new Spot(1, 0, 0),
        new Spot(0, 0, 0), new Spot(1, 0, 0), false, 1, 2);
    private static final List<Spot> OPENING = List.of(new Spot(10, 64, 20), new Spot(10, 65, 20));

    private WindowState window()
    {
        final World world = mock(World.class, withSettings().stubOnly());
        final Block anchor = mock(Block.class, withSettings().stubOnly());
        when(anchor.getWorld()).thenReturn(world);
        return new WindowState(new GateSource("gate:a", anchor, SHAPE, OPENING, null, "Chulak", 16), null);
    }

    private static Location eye()
    {
        return new Location(null, 5.0, 65.0, 20.5);
    }

    /** The first block behind the wall, within the sweep of cells, seen through the opening and covered or not; empty for none. */
    private static int[] cellSeen(final WindowState window, final boolean covered)
    {
        final List<WindowState> seeing = List.of(window);
        for (int x = 11; x <= 14; x++)
        {
            for (int y = 62; y <= 68; y++)
            {
                for (int z = 17; z <= 23; z++)
                {
                    final double[] rect = WindowOcclusion.seenThrough(eye(), window, x, y, z, seeing, 0.0);
                    if ((rect != WindowShape.UNSEEN)
                        && (WindowOcclusion.coveredBy(window, rect, window.openKeys, Set.of(), Windows.MOST_BESIDE) == covered))
                    {
                        return new int[] { x, y, z };
                    }
                }
            }
        }
        return new int[0];
    }

    private static Location at(final int[] cell)
    {
        return new Location(null, cell[0] + 0.5, cell[1] + 0.5, cell[2] + 0.5);
    }

    /** A stand-in the plugin drew is never hidden by a mirror; it would vanish from the view it makes. */
    @Test
    void aStandInIsNeverVeiled()
    {
        final Entity standIn = mock(Entity.class);
        when(standIn.getUniqueId()).thenReturn(UUID.randomUUID());
        when(standIn.getScoreboardTags()).thenReturn(Set.of(StandIns.TAG));

        assertFalse(WindowVeil.veilable(standIn));
    }

    /**
     * Creatures are looked for as far as the deepest view and the proximity distance beyond it.
     *
     * <p>Depth is from the opening and the eye may stand the proximity distance from that. With the
     * view 20 deep, the proximity 8 and a view that reaches 10, the search is 20 + 8 + 1 = 29.
     */
    @Test
    void creaturesAreLookedForAsFarAsTheViewAndTheProximityDistance()
    {
        ConfigTestSupport.set(ConfigKeys.VIEW_DEPTH, 20);
        ConfigTestSupport.set(ConfigKeys.MIRROR_PROXIMITY_DISTANCE, 8);
        final World here = mock(World.class);
        when(here.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of());

        WindowVeil.creaturesInside(here, eye(), 10, List.of(window()), Map.of(), Set.of(), new ArrayList<>());

        final ArgumentCaptor<Double> reach = ArgumentCaptor.forClass(Double.class);
        verify(here).getNearbyEntities(any(Location.class), reach.capture(), anyDouble(), anyDouble());
        assertEquals(29.0, reach.getValue(), 0.0);
    }

    /** A block seen through the opening that the wall does not hide the rest of is not in the view for a creature. */
    @Test
    void aBlockSeenButNotCoveredIsNotInTheViewForACreature()
    {
        final WindowState window = window();
        final int[] cell = cellSeen(window, false);
        assertEquals(3, cell.length, "a cell seen through the opening whose outline the wall does not cover");

        assertFalse(WindowVeil.inAnyView(eye(), at(cell), List.of(window), Map.of(), window.openKeys));
    }

    /** One the opening hides all of is. */
    @Test
    void aBlockSeenAndCoveredIsInTheViewForACreature()
    {
        final WindowState window = window();
        final int[] cell = cellSeen(window, true);
        assertEquals(3, cell.length, "a cell seen through the opening whose outline lands on it");

        assertTrue(WindowVeil.inAnyView(eye(), at(cell), List.of(window), Map.of(), window.openKeys));
    }

    /** A stand-in already shown is kept for the looser test: seen at all, the wall hiding its outline or not. */
    @Test
    void aBlockSeenButNotCoveredIsStillSeenForAStandInAlreadyShown()
    {
        final WindowState window = window();
        final int[] cell = cellSeen(window, false);
        assertEquals(3, cell.length, "a cell seen through the opening");

        assertTrue(WindowVeil.stillSeen(eye(), at(cell), List.of(window), Map.of()));
    }

    /** A room held whole is judged by its depth, not by the eye: the clipped test would call this block unseen. */
    @Test
    void aHeldRoomIsJudgedByItsDepthAndNotByTheEye()
    {
        final WindowState window = window();
        final Location beside = new Location(null, 11.5, 64.5, 40.5);
        final Map<WindowState, Windows.Whole> held = Map.of(window, new Windows.Whole(Map.of(), 50));
        assertFalse(WindowVeil.inAnyView(eye(), beside, List.of(window), Map.of(), window.openKeys),
            "the eye cannot see it through the opening");

        assertFalse(WindowVeil.stillSeen(eye(), beside, List.of(window), Map.of()), "nor is it seen at all");

        assertTrue(WindowVeil.inAnyView(eye(), beside, List.of(window), held, window.openKeys));
        assertTrue(WindowVeil.stillSeen(eye(), beside, List.of(window), held));
    }

    private static Entity creature()
    {
        final Entity entity = mock(Entity.class);
        when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
        return entity;
    }

    /** What has just come inside the view is hidden from the viewer, and what has left it is shown again. */
    @Test
    void veilingHidesWhatCameInsideAndShowsWhatLeft()
    {
        final Player player = mock(Player.class);
        final ViewerDrawing view = new ViewerDrawing(mock(World.class));
        final Entity left = creature();
        final Entity came = creature();
        view.veiled.put(left.getUniqueId(), left);

        WindowVeil.veil(player, view, List.of(came));

        verify(player).showEntity(plugin, left);
        verify(player).hideEntity(plugin, came);
        assertEquals(Set.of(came.getUniqueId()), view.veiled.keySet(), "what is hidden now is only what is inside now");
    }

    /** What stays inside the view is not hidden a second time. */
    @Test
    void veilingLeavesAloneWhatWasAlreadyHidden()
    {
        final Player player = mock(Player.class);
        final ViewerDrawing view = new ViewerDrawing(mock(World.class));
        final Entity stays = creature();
        view.veiled.put(stays.getUniqueId(), stays);

        WindowVeil.veil(player, view, List.of(stays));

        verify(player, never()).hideEntity(any(), any(Entity.class));
        verify(player, never()).showEntity(any(), any(Entity.class));
    }

    /**
     * Two or more refused in one redraw are all left out, and the one that was not refused is kept (#716).
     *
     * <p>Taking one out of the map being looped over threw on whichever creature came next, out of the
     * redraw; one refusal was enough when another creature followed it, and three of four makes that certain.
     */
    @Test
    void severalCreaturesTheServerWillNotHideDoNotBreakTheRedraw()
    {
        final Player player = mock(Player.class);
        final ViewerDrawing view = new ViewerDrawing(mock(World.class));
        final Entity first = creature();
        final Entity second = creature();
        final Entity third = creature();
        final Entity fine = creature();
        for (final Entity refused : List.of(first, second, third))
        {
            doThrow(new IllegalStateException("not hideable")).when(player).hideEntity(any(), eq(refused));
        }

        WindowVeil.veil(player, view, List.of(first, second, third, fine));

        assertEquals(Set.of(fine.getUniqueId()), view.veiled.keySet(), "only the one that was hidden is remembered");
    }

    /** A creature the server refuses to hide is not remembered as hidden, or it would be shown without ever being hidden. */
    @Test
    void aCreatureTheServerWillNotHideIsNotRemembered()
    {
        final Player player = mock(Player.class);
        final ViewerDrawing view = new ViewerDrawing(mock(World.class));
        final Entity refused = creature();
        doThrow(new IllegalStateException("not hideable")).when(player).hideEntity(any(), eq(refused));

        WindowVeil.veil(player, view, List.of(refused));

        assertTrue(view.veiled.isEmpty());
    }
}
