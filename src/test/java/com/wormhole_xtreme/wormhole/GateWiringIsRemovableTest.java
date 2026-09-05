package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.model.Stargate;

/**
 * The redstone an admin lays on a gate can be taken back up again.
 *
 * <p>A gate's [RD], [RS] and [RA] cells are indexed as gate blocks, and they have to be: a
 * redstone event arrives carrying only the block it fired on, and the index is how that finds
 * the gate it belongs to. Being indexed also meant being protected from breaking, so a
 * redstone block placed on top of a DHD could not be removed -- the plugin answered a pickaxe
 * with an instruction to remove the entire gate first.
 *
 * <p>These cells are the one part of a gate the plugin expects a person to place, change and
 * remove freely. Protection is for the frame.
 */
class GateWiringIsRemovableTest
{
    private static Block blockAt(final World world, final int x, final int y, final int z)
    {
        final Block b = mock(Block.class);
        when(b.getLocation()).thenReturn(new Location(world, x, y, z));
        when(b.getX()).thenReturn(x);
        when(b.getY()).thenReturn(y);
        when(b.getZ()).thenReturn(z);
        when(b.getWorld()).thenReturn(world);
        return b;
    }

    @Test
    void theDialActivationBlockCanBeBroken()
    {
        final World world = mock(World.class);
        final Stargate gate = new Stargate();
        final Block rd = blockAt(world, 10, 64, 20);
        gate.setGateRedstoneDialActivationBlock(rd);

        assertTrue(WormholeXTremeBlockListener.isRemovableGateWiring(gate, rd),
            "a redstone block on top of the DHD is the admin's, not the gate's");
    }

    @Test
    void theSignCycleBlockCanBeBroken()
    {
        final World world = mock(World.class);
        final Stargate gate = new Stargate();
        final Block rs = blockAt(world, 11, 64, 20);
        gate.setGateRedstoneSignActivationBlock(rs);

        assertTrue(WormholeXTremeBlockListener.isRemovableGateWiring(gate, rs));
    }

    @Test
    void theGateActivatedLeverCanBeBroken()
    {
        final World world = mock(World.class);
        final Stargate gate = new Stargate();
        final Block ra = blockAt(world, 12, 64, 20);
        gate.setGateRedstoneGateActivatedBlock(ra);

        assertTrue(WormholeXTremeBlockListener.isRemovableGateWiring(gate, ra));
    }

    /**
     * Anything else on the gate stays protected.
     *
     * <p>The point of the change is to stop protecting wiring, not to stop protecting gates.
     * A frame block a metre from the DHD must still refuse a pickaxe and say so.
     */
    @Test
    void ablockThatIsNotWiringIsStillProtected()
    {
        final World world = mock(World.class);
        final Stargate gate = new Stargate();
        gate.setGateRedstoneDialActivationBlock(blockAt(world, 10, 64, 20));

        assertFalse(WormholeXTremeBlockListener.isRemovableGateWiring(gate, blockAt(world, 99, 64, 99)),
            "only the wiring cells open up; the rest of the gate is still the gate");
    }

    /**
     * A gate with no redstone at all protects everything, rather than anything.
     *
     * <p>An unwired gate's wiring blocks are null. Treating a null as "matches" would have
     * opened every block on every gate that had never been wired.
     */
    @Test
    void agateWithNoWiringDoesNotTreatEveryBlockAsWiring()
    {
        final World world = mock(World.class);
        final Stargate gate = new Stargate();

        assertFalse(WormholeXTremeBlockListener.isRemovableGateWiring(gate, blockAt(world, 10, 64, 20)),
            "a null wiring block must not match the block being broken");
    }

    @Test
    void nothingMatchesWhenThereIsNoGateOrNoBlock()
    {
        final World world = mock(World.class);
        assertFalse(WormholeXTremeBlockListener.isRemovableGateWiring(null, blockAt(world, 1, 2, 3)));
        assertFalse(WormholeXTremeBlockListener.isRemovableGateWiring(new Stargate(), null));
    }
}
