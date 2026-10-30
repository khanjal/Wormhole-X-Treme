package com.wormhole_xtreme.wormhole.model;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Switch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * What {@code toggleDialLeverState} does to the block in a gate's dial-activation slot.
 *
 * <p>Only the redstone-write window was pinned ({@code GateLeverWriteIsGuardedTest}); the other
 * 22 test sites that name the method stub it on a mocked gate. So nothing said that a player's
 * button survives, that a lever is made only for an empty slot on a regenerate, or that the
 * lever follows the gate's state and not its opposite.
 */
class DialLeverStateTest
{
    private World world;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        world = mock(World.class);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PluginTestSupport.forgetAllGates();
        PluginTestSupport.remove();
    }

    /** A slot block in a chunk (0,0) of the test world, whose material is {@code first} and then {@code later}. */
    private Block slot(final Material first, final Material... later)
    {
        final Block b = mock(Block.class);
        when(b.getType()).thenReturn(first, later);
        when(b.getWorld()).thenReturn(world);
        when(b.getX()).thenReturn(Integer.valueOf(1));
        when(b.getZ()).thenReturn(Integer.valueOf(1));
        return b;
    }

    private Stargate gateWith(final Block slot, final boolean active)
    {
        final Stargate gate = new Stargate();
        gate.setGateName("dialLever");
        gate.setGateFacing(BlockFace.EAST);
        gate.setGateDialLeverBlock(slot);
        gate.setGateActive(active);
        return gate;
    }

    @Test
    void anEmptySlotGetsALeverFacingTheGateWhenRegenerating()
    {
        final Block slot = slot(Material.AIR, Material.LEVER);
        final Switch data = mock(Switch.class);
        when(slot.getBlockData()).thenReturn(data);

        StargateRedstone.toggleDialLeverState(gateWith(slot, true), true);

        verify(slot).setType(Material.LEVER);
        verify(data).setFacing(BlockFace.EAST);
        verify(data).setPowered(true);
    }

    @Test
    void anEmptySlotIsLeftEmptyWhenNotRegenerating()
    {
        final Block slot = slot(Material.AIR);

        StargateRedstone.toggleDialLeverState(gateWith(slot, true), false);

        verify(slot, never()).setType(ArgumentMatchers.any());
    }

    @Test
    void aPlayersButtonIsKeptEvenWhenRegenerating()
    {
        final Block slot = slot(Material.OAK_BUTTON);

        StargateRedstone.toggleDialLeverState(gateWith(slot, true), true);

        verify(slot, never()).setType(ArgumentMatchers.any());
        verify(slot, never()).setBlockData(ArgumentMatchers.any());
    }

    @Test
    void aSlotThatCannotBeReadCountsAsEmpty()
    {
        final Block slot = mock(Block.class);
        when(slot.getType()).thenThrow(new IllegalStateException("chunk gone")).thenReturn(Material.LEVER);
        when(slot.getWorld()).thenReturn(world);
        final Switch data = mock(Switch.class);
        when(slot.getBlockData()).thenReturn(data);

        StargateRedstone.toggleDialLeverState(gateWith(slot, false), true);

        verify(slot).setType(Material.LEVER);
    }

    @Test
    void anOpenGatesLeverIsThrownAndItsChunkIsLoaded()
    {
        final Block slot = slot(Material.LEVER);
        final Switch data = mock(Switch.class);
        when(slot.getBlockData()).thenReturn(data);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);

        StargateRedstone.toggleDialLeverState(gateWith(slot, true), false);

        verify(data).setPowered(true);
        verify(slot).setBlockData(data);
        verify(world).loadChunk(0, 0);
        verify(world, never()).unloadChunkRequest(anyInt(), anyInt());
    }

    @Test
    void aClosedGatesLeverIsLetGoAndItsChunkIsReleased()
    {
        final Block slot = slot(Material.LEVER);
        final Switch data = mock(Switch.class);
        when(slot.getBlockData()).thenReturn(data);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);

        StargateRedstone.toggleDialLeverState(gateWith(slot, false), false);

        verify(data).setPowered(false);
        verify(world).unloadChunkRequest(0, 0);
        verify(world, never()).loadChunk(anyInt(), anyInt());
    }
}
