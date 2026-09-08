package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * Laying the redstone wire a gate's [RD] and [RS] markers ask for, and taking it up again.
 *
 * <p>Nothing covered either method. Five mutations -- overwriting a block somebody had built
 * there, never placing the wire at all, never recording it as part of the gate, tearing up
 * whatever happened to be in the cell -- all survived the whole suite.
 *
 * <p>The recording matters as much as the placing. A wire the gate does not count as one of
 * its own blocks is left behind when the gate is removed, and a wire it counts but never
 * placed makes the gate claim a block it does not own.
 */
class RedstoneWireSetupTest
{
    private World world;

    @BeforeEach
    void setUp() throws Exception
    {
        final Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, mock(WormholeXTreme.class));
        world = mock(World.class);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        final Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, null);
    }

    /** A block at a fixed spot, reporting whatever material it is told to. */
    private Block blockOf(final Material type)
    {
        final Block b = mock(Block.class);
        when(b.getType()).thenReturn(type);
        when(b.getLocation()).thenReturn(new Location(world, 1, 64, 1));
        return b;
    }

    private Stargate gateWithDialActivator(final Block block)
    {
        final Stargate gate = new Stargate();
        gate.setGateName("wired");
        gate.setGateWorld(world);
        gate.setGateRedstoneDialActivationBlock(block);
        return gate;
    }

    /** An empty cell gets the wire, and the gate takes ownership of it. */
    @Test
    void anEmptyCellIsWiredAndCountedAsTheGates()
    {
        final Block target = blockOf(Material.AIR);
        final Stargate gate = gateWithDialActivator(target);

        StargateBlockSetup.setupRedstoneDialWire(gate, true);

        verify(target).setType(Material.REDSTONE_WIRE);
        assertEquals(1, gate.getGateStructureBlocks().size(),
            "the wire is one of the gate's blocks now, so removing the gate takes it up");
    }

    /**
     * A cell somebody has built in is left alone.
     *
     * <p>The marker resolves to a spot in the world, and a player may well have put something
     * there. Overwriting it destroys their block to place a wire the gate could have simply
     * reported it could not lay.
     */
    @Test
    void aCellSomebodyHasBuiltInIsNotOverwritten()
    {
        final Block target = blockOf(Material.DIAMOND_BLOCK);
        final Stargate gate = gateWithDialActivator(target);

        StargateBlockSetup.setupRedstoneDialWire(gate, true);

        verify(target, never()).setType(Material.REDSTONE_WIRE);
        assertTrue(gate.getGateStructureBlocks().isEmpty(),
            "and the gate does not claim a block it did not place");
    }

    /** Taking the wire up clears the block and gives up ownership. */
    @Test
    void theWireIsTakenUpAgainAndNoLongerClaimed()
    {
        final Block target = blockOf(Material.REDSTONE_WIRE);
        final Stargate gate = gateWithDialActivator(target);
        gate.getGateStructureBlocks().add(target.getLocation());

        StargateBlockSetup.setupRedstoneDialWire(gate, false);

        verify(target).setType(Material.AIR);
        assertTrue(gate.getGateStructureBlocks().isEmpty(),
            "the gate stops claiming a block it has just cleared");
    }

    /**
     * Taking it up does not clear a block that is not the wire.
     *
     * <p>Between laying and lifting, a player may have replaced it. Clearing whatever is
     * there now would destroy their block on a teardown that was only meant to remove ours.
     */
    @Test
    void somethingElseInTheCellIsNotTornUp()
    {
        final Block target = blockOf(Material.DIAMOND_BLOCK);
        final Stargate gate = gateWithDialActivator(target);

        StargateBlockSetup.setupRedstoneDialWire(gate, false);

        verify(target, never()).setType(Material.AIR);
    }

    /** A gate with no [RD] marker at all does nothing, rather than failing. */
    @Test
    void aGateWithNoDialActivatorDoesNothing()
    {
        final Stargate gate = gateWithDialActivator(null);

        StargateBlockSetup.setupRedstoneDialWire(gate, true);
        // And on the way out, where there is no try/catch to absorb a null dereference --
        // taking up a wire a shape never asked for must not throw on a live teardown.
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
            () -> StargateBlockSetup.setupRedstoneDialWire(gate, false));

        assertFalse(gate.getGateStructureBlocks().iterator().hasNext(),
            "nothing placed and nothing claimed");
    }

    /** The sign-dial wire behaves the same way, on its own block. */
    @Test
    void theSignDialWireFollowsTheSameRules()
    {
        final Block target = blockOf(Material.AIR);
        final Stargate gate = new Stargate();
        gate.setGateName("wired");
        gate.setGateWorld(world);
        gate.setGateRedstoneSignActivationBlock(target);

        StargateBlockSetup.setupRedstoneSignDialWire(gate, true);

        verify(target).setType(Material.REDSTONE_WIRE);
        assertEquals(1, gate.getGateStructureBlocks().size());
    }

    /** And is not overwritten either. */
    @Test
    void theSignDialWireDoesNotOverwriteEither()
    {
        final Block target = blockOf(Material.DIAMOND_BLOCK);
        final Stargate gate = new Stargate();
        gate.setGateName("wired");
        gate.setGateWorld(world);
        gate.setGateRedstoneSignActivationBlock(target);

        StargateBlockSetup.setupRedstoneSignDialWire(gate, true);

        verify(target, never()).setType(Material.REDSTONE_WIRE);
    }
}
