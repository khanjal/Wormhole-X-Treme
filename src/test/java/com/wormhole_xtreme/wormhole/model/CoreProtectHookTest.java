package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Switch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.plugin.CoreProtectLog;

/**
 * The places that build and take down a gate report to CoreProtect (#238), as the plugin's own
 * user, and always before the change: CoreProtect reads the block standing there as what was
 * removed or replaced.
 */
class CoreProtectHookTest
{
    private final List<String> logged = new ArrayList<>();
    private final Map<List<Integer>, Material> standing = new HashMap<>();
    private World world;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        ConfigTestSupport.loadDefaults();
        ConfigTestSupport.set(ConfigManager.ConfigKeys.COREPROTECT_ENABLED, true);
        // What stands in the cell as the call is made: a placement has to be logged before the write,
        // because CoreProtect reads the cell then as what was replaced.
        CoreProtectLog.setSinkForTest((placed, user, at, type, data) -> logged.add((placed ? "placed " : "removed ")
            + user + " " + type + " " + at.getBlockY()
            + (placed ? " over " + standing.getOrDefault(List.of(at.getBlockX(), at.getBlockY(), at.getBlockZ()), Material.AIR) : "")));
        world = mock(World.class);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(call ->
        {
            final List<Integer> at = List.of(call.getArgument(0), call.getArgument(1), call.getArgument(2));
            final Block block = mock(Block.class);
            final Switch data = mock(Switch.class);
            when(block.getType()).thenAnswer(read -> standing.getOrDefault(at, Material.AIR));
            when(block.getLocation()).thenReturn(new Location(world, at.get(0), at.get(1), at.get(2)));
            when(block.getBlockData()).thenReturn(data);
            when(block.getWorld()).thenReturn(world);
            Mockito.doAnswer(set ->
            {
                standing.put(at, set.getArgument(0));
                return null;
            }).when(block).setType(Mockito.any(Material.class));
            return block;
        });
    }

    @AfterEach
    void tearDown() throws Exception
    {
        CoreProtectLog.setSinkForTest(null);
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    /** Taking a gate down with -destroy logs every frame block as removed, before it goes. */
    @Test
    void takingAGateDownLogsEachBlockRemoved()
    {
        final Stargate gate = new Stargate();
        gate.setGateWorld(world);
        for (final int y : new int[] { 64, 65 })
        {
            standing.put(List.of(0, y, 0), Material.OBSIDIAN);
            gate.getGateStructureBlocks().add(new Location(world, 0, y, 0));
        }

        StargateBlockSetup.deleteGateBlocks(gate);

        assertEquals(List.of("removed #wormhole OBSIDIAN 64", "removed #wormhole OBSIDIAN 65"), logged);
    }

    /** The iris lever is logged as placed when it goes up, and as removed when it comes down. */
    @Test
    void theIrisLeverIsLoggedBothWays()
    {
        final Stargate gate = new Stargate();
        gate.setGateWorld(world);
        gate.setGateFacing(BlockFace.NORTH);
        final Block lever = world.getBlockAt(3, 70, 3);
        gate.setGateIrisLeverBlock(lever);

        StargateBlockSetup.setupIrisLever(gate, true);
        StargateBlockSetup.setupIrisLever(gate, false);

        assertEquals(List.of("placed #wormhole LEVER 70 over AIR", "removed #wormhole LEVER 70"), logged);
    }

    /**
     * Wire is logged once, when it is laid in air. Laid again over itself, as every regen does, it
     * is not a placement, and logging one would add a row per wire per regen. Found by a Fable review.
     */
    @Test
    void wireLaidAgainOverItselfIsNotLoggedAgain()
    {
        final Stargate gate = new Stargate();
        gate.setGateWorld(world);
        gate.setGateRedstoneDialActivationBlock(world.getBlockAt(5, 70, 5));

        StargateBlockSetup.setupRedstoneDialWire(gate, true);
        StargateBlockSetup.setupRedstoneDialWire(gate, true);

        assertEquals(List.of("placed #wormhole REDSTONE_WIRE 70 over AIR"), logged);
    }

    /**
     * A lever put where a block already stands is logged as the placement alone, made before the
     * write, so CoreProtect records the block it replaced and a rollback puts that back. A removal
     * logged too would be a second break of the same block. Found by a Fable review.
     */
    @Test
    void aBlockTheLeverReplacesIsLoggedByThePlacementAlone()
    {
        final Stargate gate = new Stargate();
        gate.setGateWorld(world);
        gate.setGateFacing(BlockFace.NORTH);
        standing.put(List.of(3, 70, 3), Material.STONE);
        gate.setGateIrisLeverBlock(world.getBlockAt(3, 70, 3));

        StargateBlockSetup.setupIrisLever(gate, true);

        assertEquals(List.of("placed #wormhole LEVER 70 over STONE"), logged);
    }

    /**
     * A dial lever regen puts back, where the cell stood empty, is logged as placed. Its twin, the
     * gate-activated lever, already was. Found by a Sonnet review.
     */
    @Test
    void aDialLeverPutBackIsLogged()
    {
        final Stargate gate = new Stargate();
        gate.setGateWorld(world);
        gate.setGateFacing(BlockFace.NORTH);
        gate.setGateDialLeverBlock(world.getBlockAt(5, 66, 5));

        StargateBlockSetup.toggleDialLeverState(gate, true);

        assertEquals(List.of("placed #wormhole LEVER 66 over AIR"), logged);
    }
}
