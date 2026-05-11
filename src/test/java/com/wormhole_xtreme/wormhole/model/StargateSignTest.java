package com.wormhole_xtreme.wormhole.model;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for sign placement in {@link Stargate#setupGateSign(boolean)}.
 *
 * <p>The key invariant: the name sign must always face {@code gateFacing} (the
 * open-portal direction toward the player), regardless of the orientation of
 * the DHD lever/button block.  The old code incorrectly derived the facing
 * from the lever's {@code getFacing()}, which returns {@link BlockFace#UP}
 * when a button is placed on top of the DHD block — causing the sign to end
 * up on a side face instead of the front face.
 */
public class StargateSignTest
{
    private Stargate gate;
    private Block nameHolder;
    private Block signPlaceBlock;
    private Block leverBlock;

    @BeforeEach
    public void setUp()
    {
        gate = new Stargate();
        nameHolder = mock(Block.class);
        signPlaceBlock = mock(Block.class);
        leverBlock = mock(Block.class);
    }

    // ------------------------------------------------------------------
    // Helper: configure signPlaceBlock so it looks like a placeable AIR
    // block that correctly handles setType / getBlockData / getState.
    // ------------------------------------------------------------------
    private Directional stubSignBlock()
    {
        final Directional signData = mock(Directional.class);
        final Sign signState = mock(Sign.class);
        final Location loc = mock(Location.class);

        when(signPlaceBlock.getType()).thenReturn(Material.AIR);
        when(signPlaceBlock.getBlockData()).thenReturn(signData);
        when(signPlaceBlock.getState()).thenReturn(signState);
        when(signPlaceBlock.getLocation()).thenReturn(loc);

        // setLine / update are void no-ops by default in Mockito
        return signData;
    }

    // ------------------------------------------------------------------
    // Core bug regression: DHD button placed on TOP → getFacing() = UP.
    // The sign must still face gateFacing (NORTH), not UP or any side.
    // ------------------------------------------------------------------
    @Test
    public void signFacesGateFacingEvenWhenLeverFacesUp()
    {
        gate.setGateFacing(BlockFace.NORTH);
        gate.setGateNameBlockHolder(nameHolder);
        gate.setGateDialLeverBlock(leverBlock);

        // DHD button on top of a block → getFacing() returns UP
        final Directional leverData = mock(Directional.class);
        when(leverBlock.getBlockData()).thenReturn(leverData);
        when(leverData.getFacing()).thenReturn(BlockFace.UP);

        // The forward-adjacent block (NORTH of nameHolder) is AIR — first candidate
        when(nameHolder.getRelative(BlockFace.NORTH)).thenReturn(signPlaceBlock);
        final Directional signData = stubSignBlock();

        gate.setupGateSign(true);

        // Sign must be placed at the NORTH-facing position
        verify(signPlaceBlock).setType(Material.OAK_WALL_SIGN);
        // And the facing written into the block data must be NORTH, not UP/EAST/WEST/etc.
        verify(signData).setFacing(BlockFace.NORTH);
    }

    // ------------------------------------------------------------------
    // Normal case: no lever block configured at all.
    // ------------------------------------------------------------------
    @Test
    public void signFacesGateFacingWhenNoLeverPresent()
    {
        gate.setGateFacing(BlockFace.EAST);
        gate.setGateNameBlockHolder(nameHolder);
        // gate has no lever block (null)

        when(nameHolder.getRelative(BlockFace.EAST)).thenReturn(signPlaceBlock);
        final Directional signData = stubSignBlock();

        gate.setupGateSign(true);

        verify(signPlaceBlock).setType(Material.OAK_WALL_SIGN);
        verify(signData).setFacing(BlockFace.EAST);
    }

    // ------------------------------------------------------------------
    // Lever correctly faces gateFacing (side-mounted button) — no change.
    // ------------------------------------------------------------------
    @Test
    public void signFacesGateFacingWhenLeverAlreadyFacesGateFacing()
    {
        gate.setGateFacing(BlockFace.SOUTH);
        gate.setGateNameBlockHolder(nameHolder);
        gate.setGateDialLeverBlock(leverBlock);

        final Directional leverData = mock(Directional.class);
        when(leverBlock.getBlockData()).thenReturn(leverData);
        when(leverData.getFacing()).thenReturn(BlockFace.SOUTH); // matches gateFacing

        when(nameHolder.getRelative(BlockFace.SOUTH)).thenReturn(signPlaceBlock);
        final Directional signData = stubSignBlock();

        gate.setupGateSign(true);

        verify(signPlaceBlock).setType(Material.OAK_WALL_SIGN);
        verify(signData).setFacing(BlockFace.SOUTH);
    }

    // ------------------------------------------------------------------
    // Destroy: sign at nameHolder.getRelative(gateFacing) is removed.
    // ------------------------------------------------------------------
    @Test
    public void destroyRemovesSignAtGateFacingPosition()
    {
        gate.setGateFacing(BlockFace.WEST);
        gate.setGateNameBlockHolder(nameHolder);

        final Location loc = mock(Location.class);
        when(nameHolder.getRelative(BlockFace.WEST)).thenReturn(signPlaceBlock);
        when(signPlaceBlock.getType()).thenReturn(Material.OAK_WALL_SIGN);
        when(signPlaceBlock.getLocation()).thenReturn(loc);

        gate.setupGateSign(false);

        verify(signPlaceBlock).setType(Material.AIR);
    }

    // ------------------------------------------------------------------
    // Destroy legacy path: sign is at the holder itself (older gates).
    // ------------------------------------------------------------------
    @Test
    public void destroyLegacyRemovesSignAtHolder()
    {
        gate.setGateFacing(BlockFace.WEST);
        gate.setGateNameBlockHolder(nameHolder);

        final Location loc = mock(Location.class);
        // The forward block is NOT a sign
        when(nameHolder.getRelative(BlockFace.WEST)).thenReturn(signPlaceBlock);
        when(signPlaceBlock.getType()).thenReturn(Material.AIR);

        // But the holder itself is a sign (legacy placement)
        when(nameHolder.getType()).thenReturn(Material.OAK_WALL_SIGN);
        when(nameHolder.getLocation()).thenReturn(loc);

        gate.setupGateSign(false);

        verify(nameHolder).setType(Material.AIR);
    }
}
