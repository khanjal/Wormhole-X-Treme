package com.wormhole_xtreme.wormhole.model.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.List;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.model.GateSource;
import com.wormhole_xtreme.wormhole.model.window.WindowShape.Spot;

/**
 * Which windows a player is shown, and whether they are near one (#430).
 *
 * <p>Characterised before the questions moved out of {@code Windows}: each test below fails when
 * the one condition it is named for is taken out of {@link WindowViewers}. The wall is at x = 10
 * and faces west, so a viewer west of it is in front and one east of it is behind.
 */
class WindowViewersTest
{
    private static final WindowShape SHAPE = new WindowShape(new Spot(10, 64, 20), new Spot(1, 0, 0),
        new Spot(0, 0, 0), new Spot(1, 0, 0), false, 1, 2);
    private static final List<Spot> OPENING = List.of(new Spot(10, 64, 20), new Spot(10, 65, 20));

    private World world;
    private World elsewhere;
    private Block anchor;

    @BeforeEach
    void setUp()
    {
        WindowSight.clear();
        Windows.clear();
        ConfigTestSupport.clear();
        final BlockData openData = mock(BlockData.class, withSettings().stubOnly());
        when(openData.isOccluding()).thenReturn(false);
        final Block air = mock(Block.class, withSettings().stubOnly());
        when(air.getBlockData()).thenReturn(openData);
        world = mock(World.class, withSettings().stubOnly());
        when(world.getName()).thenReturn("viewers");
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(air);
        elsewhere = mock(World.class, withSettings().stubOnly());
        anchor = mock(Block.class, withSettings().stubOnly());
        when(anchor.getWorld()).thenReturn(world);
        when(anchor.getX()).thenReturn(10);
        when(anchor.getY()).thenReturn(64);
        when(anchor.getZ()).thenReturn(20);
    }

    @AfterEach
    void tearDown()
    {
        Windows.clear();
        WindowSight.clear();
        ConfigTestSupport.clear();
    }

    private void offer(final String name)
    {
        WindowStore.ACTIVE.put(name,
            new WindowState(new GateSource(name, anchor, SHAPE, OPENING, null, "Chulak", 16), null));
    }

    private Player playerAt(final World in, final double x)
    {
        final Player player = mock(Player.class, withSettings().stubOnly());
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getWorld()).thenReturn(in);
        when(player.getLocation()).thenReturn(new Location(in, x, 64.0, 20.5));
        return player;
    }

    private static Location eyeAt(final World in, final double x)
    {
        return new Location(in, x, 65.0, 20.5);
    }

    /** A viewer on the banner's side, in range and with a clear line, is shown the window. */
    @Test
    void aViewerInFrontInRangeAndSeeingIsShownTheWindow()
    {
        offer("gate:a");
        final Player player = playerAt(world, 5.0);

        assertEquals(1, WindowViewers.seenBy(player, eyeAt(world, 5.0)).size());
    }

    /** Behind the wall is not in front of it, however near. */
    @Test
    void aViewerBehindTheBannerIsShownNothing()
    {
        offer("gate:a");
        final Player player = playerAt(world, 15.0);

        assertTrue(WindowViewers.seenBy(player, eyeAt(world, 15.0)).isEmpty(),
            "five blocks from the wall, but on its far side");
    }

    /** Past the proximity distance (16 by default) a window is not drawn. */
    @Test
    void aViewerFurtherThanTheProximityDistanceIsShownNothing()
    {
        offer("gate:a");
        final Player player = playerAt(world, -20.0);

        assertTrue(WindowViewers.seenBy(player, eyeAt(world, -20.0)).isEmpty(), "thirty blocks out");
    }

    /** Windows come back by name, whatever order the registry holds them in. */
    @Test
    void windowsAreShownInNameOrder()
    {
        final String[] names = { "gate:q", "gate:b", "gate:x", "gate:d", "gate:m", "gate:a", "gate:z", "gate:k" };
        for (final String name : names)
        {
            offer(name);
        }
        final Player player = playerAt(world, 5.0);

        final List<String> shown = WindowViewers.seenBy(player, eyeAt(world, 5.0)).stream().map(WindowState::name)
            .toList();

        assertEquals(List.of("gate:a", "gate:b", "gate:d", "gate:k", "gate:m", "gate:q", "gate:x", "gate:z"), shown);
    }

    /** Somebody just come within range of a window is near it. */
    @Test
    void aPlayerWithinRangeIsNearAWindow()
    {
        offer("gate:a");

        assertTrue(WindowViewers.nearAWindow(playerAt(world, 5.0), new Location(world, 5.0, 64.0, 20.5)));
    }

    /** And somebody a long way off is not. */
    @Test
    void aPlayerFarAwayIsNotNearAWindow()
    {
        offer("gate:a");

        assertFalse(WindowViewers.nearAWindow(playerAt(world, -100.0), new Location(world, -100.0, 64.0, 20.5)));
    }

    /** A window in another world is not near, however close the coordinates are. */
    @Test
    void aPlayerInAnotherWorldIsNotNearAWindow()
    {
        offer("gate:a");

        assertFalse(WindowViewers.nearAWindow(playerAt(elsewhere, 5.0), new Location(elsewhere, 5.0, 64.0, 20.5)),
            "the same coordinates, but over there");
    }
}
