package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * {@code setupRedstone} as a whole: which of the three redstone pieces it lays or lifts.
 *
 * <p>Each piece has its own test ({@code RedstoneWireSetupTest}, {@code RedstoneLeverClaimTest});
 * what none of them says is which pieces a gate gets. A gate that is not sign-powered has no
 * wires to lay, but still has its output lever.
 */
class SetupRedstoneTest
{
    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PluginTestSupport.remove();
    }

    private static Block blockOf(final Material type, final int x)
    {
        final Block b = mock(Block.class);
        when(b.getType()).thenReturn(type);
        when(b.getLocation()).thenReturn(new Location(null, x, 64, 0));
        return b;
    }

    private static Stargate gateOf(final boolean signPowered, final Block dial, final Block sign, final Block lever)
    {
        final Stargate gate = new Stargate();
        gate.setGateSignPowered(signPowered);
        gate.setGateRedstoneDialActivationBlock(dial);
        gate.setGateRedstoneSignActivationBlock(sign);
        gate.setGateRedstoneGateActivatedBlock(lever);
        return gate;
    }

    @Test
    void aSignPoweredGateGetsBothWiresAndTheLever()
    {
        final Block dial = blockOf(Material.AIR, 1);
        final Block sign = blockOf(Material.AIR, 2);
        final Block lever = blockOf(Material.AIR, 3);
        final Stargate gate = gateOf(true, dial, sign, lever);

        StargateRedstone.setupRedstone(gate, true);

        verify(dial).setType(Material.REDSTONE_WIRE);
        verify(sign).setType(Material.REDSTONE_WIRE);
        verify(lever).setType(Material.LEVER);
        assertEquals(3, gate.getGateStructureBlocks().size(), "all three are the gate's own blocks");
    }

    @Test
    void aGateThatIsNotSignPoweredGetsNoWiresButStillItsLever()
    {
        final Block dial = blockOf(Material.AIR, 1);
        final Block sign = blockOf(Material.AIR, 2);
        final Block lever = blockOf(Material.AIR, 3);
        final Stargate gate = gateOf(false, dial, sign, lever);

        StargateRedstone.setupRedstone(gate, true);

        verifyNoInteractions(dial, sign);
        verify(lever).setType(Material.LEVER);
        assertEquals(1, gate.getGateStructureBlocks().size());
    }

    @Test
    void takingAGateDownLiftsBothWiresAndTheLever()
    {
        final Block dial = blockOf(Material.REDSTONE_WIRE, 1);
        final Block sign = blockOf(Material.REDSTONE_WIRE, 2);
        final Block lever = blockOf(Material.LEVER, 3);
        final Stargate gate = gateOf(true, dial, sign, lever);
        gate.getGateStructureBlocks().add(dial.getLocation());
        gate.getGateStructureBlocks().add(sign.getLocation());
        gate.getGateStructureBlocks().add(lever.getLocation());

        StargateRedstone.setupRedstone(gate, false);

        verify(dial).setType(Material.AIR);
        verify(sign).setType(Material.AIR);
        verify(lever).setType(Material.AIR);
        assertTrue(gate.getGateStructureBlocks().isEmpty(), "and the gate no longer claims them");
    }

    @Test
    void takingAGateDownLeavesAnythingElseWhereTheLeverWas()
    {
        final Block lever = blockOf(Material.STONE, 3);
        final Stargate gate = gateOf(false, null, null, lever);

        StargateRedstone.setupRedstone(gate, false);

        verify(lever, never()).setType(ArgumentMatchers.any());
    }
}
