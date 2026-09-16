package com.wormhole_xtreme.wormhole.model.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Cell;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Part;
import com.wormhole_xtreme.wormhole.model.Stargate3DShape;

/**
 * What a gate build preview puts in the world, who sees it, and when it goes.
 *
 * <p>Every display here is a real entity on the server. One that is shown to somebody else, saved
 * with the chunk, or left behind when its owner leaves is a block only one player can see the
 * reason for, so each way a preview ends is checked to take its displays with it.
 */
class GatePreviewsTest
{
    /** Standard's frame and chevrons, and the button on its DHD. */
    private static final int STANDARD_BLOCKS = 19;

    private WormholeXTreme plugin;
    private World world;
    private Player owner;
    private final List<BlockDisplay> spawned = new ArrayList<>();
    private final long[] now = { 1_000_000L };
    private Stargate3DShape standard;

    @BeforeEach
    void setUp() throws Exception
    {
        plugin = mock(WormholeXTreme.class);
        PluginTestSupport.install(plugin);
        ConfigTestSupport.clear();
        standard = new Stargate3DShape(Files.readAllLines(
            Paths.get("src/main/resources/shapes/gate/Standard.shape")).toArray(new String[0]));

        world = mock(World.class);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.createEntity(any(Location.class), eq(BlockDisplay.class))).thenAnswer(inv ->
        {
            final BlockDisplay display = mock(BlockDisplay.class);
            when(display.isValid()).thenReturn(true);
            spawned.add(display);
            return display;
        });
        when(world.addEntity(any(Entity.class))).thenAnswer(inv -> inv.getArgument(0));

        owner = mock(Player.class);
        when(owner.getUniqueId()).thenReturn(UUID.randomUUID());
        when(owner.getWorld()).thenReturn(world);
        standAt(0.5, 0.5, 180f);

        GatePreviews.clock = () -> now[0];
        GatePreviews.online = id -> owner;
        GatePreviews.blockData = material -> (material == Material.STONE_BUTTON)
            ? buttonData() : mock(BlockData.class);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        GatePreviews.clear();
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    private static Directional buttonData()
    {
        final Directional data = mock(Directional.class);
        when(data.getFaces()).thenReturn(Set.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST));
        return data;
    }

    /** Puts the owner's feet at a spot at y 64, looking along a yaw, level. */
    private void standAt(final double x, final double z, final float yaw)
    {
        when(owner.getLocation()).thenReturn(new Location(world, x, 64.0, z, yaw, 0f));
        when(owner.getEyeLocation()).thenReturn(new Location(world, x, 65.62, z, yaw, 0f));
    }

    /** Standard, north of whoever looks north from the origin: its button is two blocks ahead. */
    private List<Cell> standardLookingNorth()
    {
        return GateBlueprint.of(standard, GateBlueprint.inFrontOf(standard, 0, 64, 0, BlockFace.NORTH));
    }

    /**
     * A preview is one display a block, not saved, hidden from everybody, and shown to its owner.
     *
     * <p>The hiding has to happen before the display is added to the world, or every player in
     * range is sent it first.
     */
    @Test
    void aPreviewIsOneHiddenUnsavedDisplayABlockShownOnlyToItsOwner()
    {
        final Player bystander = mock(Player.class);

        assertEquals(GatePreviews.Shown.SHOWN, GatePreviews.show(owner, standard, null));

        assertEquals(STANDARD_BLOCKS, spawned.size());
        for (final BlockDisplay display : spawned)
        {
            final InOrder order = inOrder(display, world, owner);
            order.verify(display).setPersistent(false);
            order.verify(display).setVisibleByDefault(false);
            order.verify(world).addEntity(display);
            order.verify(owner).showEntity(plugin, display);
            verify(display).setBlock(any(BlockData.class));
        }
        verifyNoInteractions(bystander);
    }

    /** Each display stands on a cell of the blueprint, and the button is turned to face the builder. */
    @Test
    void theDisplaysStandOnTheBlueprintWithTheButtonFacingTheBuilder()
    {
        final List<Location> places = new ArrayList<>();
        when(world.createEntity(any(Location.class), eq(BlockDisplay.class))).thenAnswer(inv ->
        {
            places.add(inv.getArgument(0));
            final BlockDisplay display = mock(BlockDisplay.class);
            when(display.isValid()).thenReturn(true);
            return display;
        });
        final Directional button = buttonData();
        GatePreviews.blockData = material -> (material == Material.STONE_BUTTON) ? button : mock(BlockData.class);

        GatePreviews.show(owner, standard, null);

        final List<Cell> cells = standardLookingNorth();
        assertEquals(cells.size(), places.size());
        for (int i = 0; i < cells.size(); i++)
        {
            assertEquals(new Location(world, cells.get(i).x(), cells.get(i).y(), cells.get(i).z()), places.get(i));
        }
        verify(button).setFacing(BlockFace.SOUTH);
    }

    /** A second preview stands beside the first, so every shape can be shown side by side. */
    @Test
    void aSecondPreviewStandsBesideTheFirstRatherThanReplacingIt()
    {
        GatePreviews.show(owner, standard, null);
        standAt(40.5, 0.5, 180f);
        GatePreviews.show(owner, standard, null);

        assertEquals(2, GatePreviews.countOf(owner.getUniqueId()));
        assertEquals(2 * STANDARD_BLOCKS, spawned.size());
        spawned.forEach(display -> verify(display, never()).remove());
    }

    /** Clearing takes away the preview looked at, and leaves the others standing. */
    @Test
    void clearingTakesAwayOnlyThePreviewLookedAt()
    {
        GatePreviews.show(owner, standard, null);
        standAt(40.5, 0.5, 180f);
        GatePreviews.show(owner, standard, null);
        final List<BlockDisplay> first = new ArrayList<>(spawned.subList(0, STANDARD_BLOCKS));
        final List<BlockDisplay> second = new ArrayList<>(spawned.subList(STANDARD_BLOCKS, spawned.size()));

        standAt(40.5, 0.5, 0f);
        assertFalse(GatePreviews.clearLookedAt(owner), "looking south, away from both");
        standAt(40.5, 0.5, 180f);
        assertTrue(GatePreviews.clearLookedAt(owner));

        second.forEach(display -> verify(display).remove());
        first.forEach(display -> verify(display, never()).remove());
        assertEquals(1, GatePreviews.countOf(owner.getUniqueId()));
    }

    /** clear all takes every preview a player has. */
    @Test
    void clearAllTakesEveryPreview()
    {
        GatePreviews.show(owner, standard, null);
        standAt(40.5, 0.5, 180f);
        GatePreviews.show(owner, standard, null);

        assertEquals(2, GatePreviews.clearAll(owner));

        spawned.forEach(display -> verify(display).remove());
        assertEquals(0, GatePreviews.countOf(owner.getUniqueId()));
    }

    /** Quitting or changing world takes a player's previews with them. */
    @Test
    void forgettingAPlayerTakesTheirPreviews()
    {
        GatePreviews.show(owner, standard, null);

        GatePreviews.forget(owner.getUniqueId());

        spawned.forEach(display -> verify(display).remove());
        assertEquals(0, GatePreviews.blocksShown());
    }

    /**
     * A preview times out once its owner stops using build commands, and not while they keep
     * using them.
     */
    @Test
    void aPreviewTimesOutUnlessItsOwnerKeepsUsingBuildCommands()
    {
        GatePreviews.show(owner, standard, null);

        now[0] += 9 * 60_000L;
        standAt(0.5, 0.5, 0f);
        GatePreviews.clearLookedAt(owner);
        now[0] += 6 * 60_000L;
        GatePreviews.tick();
        spawned.forEach(display -> verify(display, never()).remove());

        now[0] += 5 * 60_000L;
        GatePreviews.tick();
        spawned.forEach(display -> verify(display).remove());
        assertEquals(0, GatePreviews.countOf(owner.getUniqueId()));
    }

    /** A gate found where a preview's button stood takes the preview away; one found elsewhere does not. */
    @Test
    void aGateBuiltWhereThePreviewStoodTakesThePreviewAway()
    {
        GatePreviews.show(owner, standard, null);
        final Cell button = standardLookingNorth().stream().filter(c -> c.part() == Part.BUTTON).findFirst()
            .orElseThrow();

        GatePreviews.builtAt(world, button.x() + 1, button.y(), button.z());
        assertEquals(1, GatePreviews.countOf(owner.getUniqueId()));

        GatePreviews.builtAt(world, button.x(), button.y(), button.z());
        assertEquals(0, GatePreviews.countOf(owner.getUniqueId()));
        spawned.forEach(display -> verify(display).remove());
    }

    /** A preview that would take the server past its block limit is refused, and nothing is spawned. */
    @Test
    void aPreviewPastTheServersBlockLimitIsRefused()
    {
        ConfigTestSupport.set(ConfigKeys.GATE_PREVIEW_MAX_BLOCKS, STANDARD_BLOCKS);

        assertEquals(GatePreviews.Shown.SHOWN, GatePreviews.show(owner, standard, null));
        assertEquals(GatePreviews.Shown.OVER_LIMIT, GatePreviews.show(owner, standard, null));

        assertEquals(STANDARD_BLOCKS, spawned.size());
        assertEquals(1, GatePreviews.countOf(owner.getUniqueId()));
    }

    /**
     * A display the server dropped with its chunk comes back once the chunk is loaded again, and
     * not before: a preview never loads a chunk.
     */
    @Test
    void aDisplayDroppedWithItsChunkComesBackOnlyOnceTheChunkIsLoaded()
    {
        GatePreviews.show(owner, standard, null);
        final BlockDisplay dropped = spawned.get(0);
        when(dropped.isValid()).thenReturn(false);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);

        GatePreviews.tick();
        assertEquals(STANDARD_BLOCKS, spawned.size(), "nothing spawned into an unloaded chunk");

        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        GatePreviews.tick();
        assertEquals(STANDARD_BLOCKS + 1, spawned.size(), "the one dropped display, and only that one");
        verify(world, times(STANDARD_BLOCKS + 1)).createEntity(any(Location.class), eq(BlockDisplay.class));
    }

    /** Disabling the plugin takes every preview on the server. */
    @Test
    void disablingTakesEveryPreviewOnTheServer()
    {
        final Player other = mock(Player.class);
        when(other.getUniqueId()).thenReturn(UUID.randomUUID());
        when(other.getLocation()).thenReturn(new Location(world, 80.5, 64.0, 0.5, 180f, 0f));
        GatePreviews.show(owner, standard, null);
        GatePreviews.show(other, standard, null);

        GatePreviews.restoreAll();

        assertEquals(2 * STANDARD_BLOCKS, spawned.size());
        spawned.forEach(display -> verify(display).remove());
        assertEquals(0, GatePreviews.blocksShown());
    }
}
