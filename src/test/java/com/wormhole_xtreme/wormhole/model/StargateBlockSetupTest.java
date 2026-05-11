package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Powerable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link StargateBlockSetup}.
 *
 * <p>Focuses on methods that operate solely on gate-state flags and mocked
 * Bukkit {@link Block} objects so no live Bukkit server is needed.
 */
public class StargateBlockSetupTest
{
    private Stargate gate;

    @BeforeEach
    public void setUp()
    {
        gate = new Stargate();
        gate.setGateName("TestGate");
    }

    // -----------------------------------------------------------------------
    // fillGateInterior
    // -----------------------------------------------------------------------

    @Test
    public void fillGateInteriorWithEmptyPortalBlocksDoesNothing()
    {
        // No blocks in list → no NPE, just a no-op
        assertDoesNotThrow(() -> StargateBlockSetup.fillGateInterior(gate, Material.WATER));
    }

    @Test
    public void fillGateInteriorSetsTypeOnEveryPortalBlock()
    {
        final World world = mock(World.class);
        final Block b1 = mock(Block.class);
        final Block b2 = mock(Block.class);
        gate.setGateWorld(world);
        gate.getGatePortalBlocks().add(new Location(null, 1, 2, 3));
        gate.getGatePortalBlocks().add(new Location(null, 4, 5, 6));
        when(world.getBlockAt(1, 2, 3)).thenReturn(b1);
        when(world.getBlockAt(4, 5, 6)).thenReturn(b2);

        StargateBlockSetup.fillGateInterior(gate, Material.WATER);

        verify(b1).setType(Material.WATER);
        verify(b2).setType(Material.WATER);
    }

    // -----------------------------------------------------------------------
    // deletePortalBlocks
    // -----------------------------------------------------------------------

    @Test
    public void deletePortalBlocksWithEmptyListDoesNothing()
    {
        assertDoesNotThrow(() -> StargateBlockSetup.deletePortalBlocks(gate));
    }

    @Test
    public void deletePortalBlocksSetsAirOnEachBlock()
    {
        final World world = mock(World.class);
        final Block b = mock(Block.class);
        gate.setGateWorld(world);
        gate.getGatePortalBlocks().add(new Location(null, 7, 8, 9));
        when(world.getBlockAt(7, 8, 9)).thenReturn(b);

        StargateBlockSetup.deletePortalBlocks(gate);

        verify(b).setType(Material.AIR);
    }

    // -----------------------------------------------------------------------
    // deleteGateBlocks
    // -----------------------------------------------------------------------

    @Test
    public void deleteGateBlocksWithEmptyListDoesNothing()
    {
        assertDoesNotThrow(() -> StargateBlockSetup.deleteGateBlocks(gate));
    }

    @Test
    public void deleteGateBlocksSetsAirOnEachStructureBlock()
    {
        final World world = mock(World.class);
        final Block b = mock(Block.class);
        gate.setGateWorld(world);
        gate.getGateStructureBlocks().add(new Location(null, 10, 11, 12));
        when(world.getBlockAt(10, 11, 12)).thenReturn(b);

        StargateBlockSetup.deleteGateBlocks(gate);

        verify(b).setType(Material.AIR);
    }

    // -----------------------------------------------------------------------
    // deleteTeleportSign
    // -----------------------------------------------------------------------

    @Test
    public void deleteTeleportSignSetsRelativeBlockToAir()
    {
        final Block signHolder = mock(Block.class);
        final Block teleportSignBlock = mock(Block.class);
        final Sign signState = mock(Sign.class);

        gate.setGateFacing(BlockFace.NORTH);
        gate.setGateDialSignBlock(signHolder);
        gate.setGateDialSign(signState);
        when(signHolder.getRelative(BlockFace.NORTH)).thenReturn(teleportSignBlock);

        StargateBlockSetup.deleteTeleportSign(gate);

        verify(teleportSignBlock).setType(Material.AIR);
    }

    @Test
    public void deleteTeleportSignNoopWhenDialSignBlockIsNull()
    {
        // dialSignBlock is null → no block interaction at all
        gate.setGateDialSignBlock(null);
        assertDoesNotThrow(() -> StargateBlockSetup.deleteTeleportSign(gate));
    }

    @Test
    public void deleteTeleportSignNoopWhenDialSignIsNull()
    {
        final Block signHolder = mock(Block.class);
        gate.setGateDialSignBlock(signHolder);
        gate.setGateDialSign(null);

        StargateBlockSetup.deleteTeleportSign(gate);

        // getRelative should never have been called
        verifyNoInteractions(signHolder);
    }

    // -----------------------------------------------------------------------
    // toggleRedstoneGateActivatedPower
    // -----------------------------------------------------------------------

    @Test
    public void toggleRedstoneGateActivatedPowerNoopWhenNotRedstonePowered()
    {
        final Block leverBlock = mock(Block.class);
        gate.setGateRedstonePowered(false);
        gate.setGateRedstoneGateActivatedBlock(leverBlock);

        StargateBlockSetup.toggleRedstoneGateActivatedPower(gate);

        verifyNoInteractions(leverBlock);
    }

    @Test
    public void toggleRedstoneGateActivatedPowerNoopWhenRedstoneBlockIsNull()
    {
        gate.setGateRedstonePowered(true);
        gate.setGateRedstoneGateActivatedBlock(null);

        // Must not throw a NullPointerException
        assertDoesNotThrow(() -> StargateBlockSetup.toggleRedstoneGateActivatedPower(gate));
    }

    @Test
    public void toggleRedstoneGateActivatedPowerNoopWhenBlockIsNotLever()
    {
        final Block block = mock(Block.class);
        when(block.getType()).thenReturn(Material.OAK_BUTTON);
        gate.setGateRedstonePowered(true);
        gate.setGateRedstoneGateActivatedBlock(block);

        StargateBlockSetup.toggleRedstoneGateActivatedPower(gate);

        verify(block, never()).getBlockData();
    }

    @Test
    public void toggleRedstoneGateActivatedPowerSetsLeverPoweredToTrueWhenGateActive()
    {
        final Block leverBlock = mock(Block.class);
        final Powerable powerable = mock(Powerable.class);
        when(leverBlock.getType()).thenReturn(Material.LEVER);
        when(leverBlock.getBlockData()).thenReturn(powerable);
        gate.setGateRedstonePowered(true);
        gate.setGateActive(true);
        gate.setGateRedstoneGateActivatedBlock(leverBlock);

        StargateBlockSetup.toggleRedstoneGateActivatedPower(gate);

        verify(powerable).setPowered(true);
        verify(leverBlock).setBlockData(powerable);
    }

    @Test
    public void toggleRedstoneGateActivatedPowerSetsLeverPoweredToFalseWhenGateInactive()
    {
        final Block leverBlock = mock(Block.class);
        final Powerable powerable = mock(Powerable.class);
        when(leverBlock.getType()).thenReturn(Material.LEVER);
        when(leverBlock.getBlockData()).thenReturn(powerable);
        gate.setGateRedstonePowered(true);
        gate.setGateActive(false);
        gate.setGateRedstoneGateActivatedBlock(leverBlock);

        StargateBlockSetup.toggleRedstoneGateActivatedPower(gate);

        verify(powerable).setPowered(false);
        verify(leverBlock).setBlockData(powerable);
    }
}
