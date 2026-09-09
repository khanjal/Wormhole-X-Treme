package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.PluginTestSupport;

/**
 * What removing a gate takes with it.
 *
 * <p>Every test in this suite calls removeStargate to tidy up, so it is exercised constantly
 * -- and nothing asserted what it actually removes. A block left in the index after its gate
 * is gone still answers "yes, I belong to a gate", and the gate it names no longer exists.
 *
 * <p>The other half is the network: a sign-powered gate whose dial sign was pointed at the
 * removed one has to stop pointing at it, or its sign names somewhere nobody can go.
 */
class RemoveStargateTest
{
    private World world;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        clearGates();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        clearGates();
        GateSpatialIndex.clear();
        PluginTestSupport.remove();
    }

    private static void clearGates()
    {
        for (final Stargate s : new ArrayList<>(StargateManager.getAllGates()))
        {
            if (s != null)
            {
                StargateManager.removeStargate(s);
            }
        }
    }

    private Block blockAt(final int x, final int y, final int z)
    {
        if (world == null)
        {
            world = mock(World.class);
            when(world.getName()).thenReturn("w");
        }
        final Block b = mock(Block.class);
        when(b.getX()).thenReturn(x);
        when(b.getY()).thenReturn(y);
        when(b.getZ()).thenReturn(z);
        when(b.getWorld()).thenReturn(world);
        when(b.getLocation()).thenReturn(new Location(world, x, y, z));
        return b;
    }

    /** A registered gate in the shared world, with no blocks yet. */
    private Stargate builtGate(final String name)
    {
        final Stargate gate = new Stargate();
        gate.setGateName(name);
        gate.setGateWorld(world != null ? world : mock(World.class));
        StargateManager.registerStargate(gate);
        return gate;
    }

    /**
     * A removed gate's blocks stop belonging to it.
     *
     * <p>Otherwise clicking one still finds a gate, and the gate it finds has been deleted.
     */
    @Test
    void aRemovedGatesBlocksAreNoLongerIndexed()
    {
        final Block structure = blockAt(1, 64, 1);
        final Block portal = blockAt(2, 64, 1);
        final Stargate gate = builtGate("alpha");
        gate.getGateStructureBlocks().add(structure.getLocation());
        gate.getGatePortalBlocks().add(portal.getLocation());
        StargateManager.addBlockIndex(structure, gate);
        StargateManager.addBlockIndex(portal, gate);

        assertTrue(StargateManager.isBlockInGate(structure), "indexed before removal");

        try (MockedStatic<StargateDBManager> db = mockStatic(StargateDBManager.class))
        {
            StargateManager.removeStargate(gate);
        }

        assertFalse(StargateManager.isBlockInGate(structure),
            "a structure block outlives its gate only in the index, and should not");
        assertFalse(StargateManager.isBlockInGate(portal), "and neither does a portal block");
    }

    /**
     * So do the blocks that are not part of the frame.
     *
     * <p>The dial lever, iris lever, dial sign and redstone activators are indexed against
     * the gate separately from its structure, so removing the structure alone leaves them
     * pointing at a gate that is gone.
     */
    @Test
    void aRemovedGatesActivationBlocksAreNoLongerIndexed()
    {
        final Block dialLever = blockAt(3, 64, 1);
        final Block irisLever = blockAt(4, 64, 1);
        final Block dialSign = blockAt(5, 64, 1);
        final Stargate gate = builtGate("alpha");
        gate.setGateDialLeverBlock(dialLever);
        gate.setGateIrisLeverBlock(irisLever);
        gate.setGateDialSignBlock(dialSign);
        StargateManager.addBlockIndex(dialLever, gate);
        StargateManager.addBlockIndex(irisLever, gate);
        StargateManager.addBlockIndex(dialSign, gate);

        assertTrue(StargateManager.isBlockInGate(dialLever), "indexed before removal");

        try (MockedStatic<StargateDBManager> db = mockStatic(StargateDBManager.class))
        {
            StargateManager.removeStargate(gate);
        }

        assertFalse(StargateManager.isBlockInGate(dialLever), "the dial lever is released");
        assertFalse(StargateManager.isBlockInGate(irisLever), "so is the iris lever");
        assertFalse(StargateManager.isBlockInGate(dialSign), "so is the dial sign");
    }

    /** The gate leaves its network's lists. */
    @Test
    void aRemovedGateLeavesItsNetwork()
    {
        final StargateNetwork network = StargateManager.addStargateNetwork("traders");
        final Stargate gate = builtGate("alpha");
        gate.setGateNetwork(network);
        StargateManager.addGateToNetwork(gate, "traders");

        assertTrue(network.getNetworkGateList().contains(gate), "on the network before removal");

        try (MockedStatic<StargateDBManager> db = mockStatic(StargateDBManager.class))
        {
            StargateManager.removeStargate(gate);
        }

        assertFalse(network.getNetworkGateList().contains(gate));
        assertNull(StargateManager.getStargate("alpha"), "and gone from the registry");
    }

    /**
     * A dial sign pointed at the removed gate stops pointing at it.
     *
     * <p>The sign belongs to a different gate entirely, and it names a destination that has
     * just stopped existing. Left alone it would keep offering somewhere nobody can go.
     */
    @Test
    void aSignPointingAtTheRemovedGateIsCleared()
    {
        final StargateNetwork network = StargateManager.addStargateNetwork("traders");

        final Stargate doomed = builtGate("doomed");
        doomed.setGateNetwork(network);
        StargateManager.addGateToNetwork(doomed, "traders");

        final Stargate signGate = builtGate("signy");
        signGate.setGateSignPowered(true);
        signGate.setGateNetwork(network);
        StargateManager.addGateToNetwork(signGate, "traders");
        signGate.setGateDialSignTarget(doomed);

        assertTrue(network.getNetworkSignGateList().contains(signGate),
            "the sign-powered gate is on the network's sign list");

        try (MockedStatic<StargateDBManager> db = mockStatic(StargateDBManager.class))
        {
            StargateManager.removeStargate(doomed);
        }

        assertNull(signGate.getGateDialSignTarget(),
            "a sign may not go on naming a gate that has been removed");
    }
}
