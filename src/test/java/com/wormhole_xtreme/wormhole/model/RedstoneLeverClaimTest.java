package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * A gate claims the redstone lever already standing in its marker cell (#440). Regen now keeps a
 * gate's redstone, so the fresh gate asks for its lever where the old one placed it; it was
 * skipped as "occupied" and left out of the gate's blocks, so the gate had a lever it did not know.
 */
class RedstoneLeverClaimTest
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

    @Test
    void aLeverAlreadyThereIsClaimedAsTheGatesOwn()
    {
        final Location at = new Location(null, 3, 64, 5);
        final Block lever = mock(Block.class);
        when(lever.getType()).thenReturn(Material.LEVER);
        when(lever.getLocation()).thenReturn(at);
        final Stargate gate = new Stargate();
        gate.setGateRedstoneGateActivatedBlock(lever);

        StargateBlockSetup.setupRedstoneGateActivatedLever(gate, true);
        StargateBlockSetup.setupRedstoneGateActivatedLever(gate, true);

        assertTrue(gate.getGateStructureBlocks().contains(at), "the lever is one of the gate's blocks");
        org.junit.jupiter.api.Assertions.assertEquals(1, gate.getGateStructureBlocks().size(),
            "once, however often it is set up: removing it once must leave no phantom block");
        verify(lever, never()).setType(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void anythingElseInTheCellIsLeftAlone()
    {
        final Block stone = mock(Block.class);
        when(stone.getType()).thenReturn(Material.STONE);
        when(stone.getLocation()).thenReturn(new Location(null, 3, 64, 5));
        final Stargate gate = new Stargate();
        gate.setGateRedstoneGateActivatedBlock(stone);

        StargateBlockSetup.setupRedstoneGateActivatedLever(gate, true);

        verify(stone, never()).setType(org.mockito.ArgumentMatchers.any());
        assertTrue(gate.getGateStructureBlocks().isEmpty(), "somebody's block is not claimed");
    }
}
