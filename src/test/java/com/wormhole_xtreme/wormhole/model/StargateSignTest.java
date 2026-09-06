package com.wormhole_xtreme.wormhole.model;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Directional;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for sign placement in {@link Stargate#setupGateSign(boolean)}.
 *
 * <p>The sign is placed at {@code nameHolder.getRelative(gateFacing)} and
 * faces {@code gateFacing}, matching the original master approach.
 */
class StargateSignTest
{
    private Stargate gate;
    private Block nameHolder;
    private Block signPlaceBlock;

    @BeforeEach
    void setUp()
    {
        gate = new Stargate();
        nameHolder = mock(Block.class);
        signPlaceBlock = mock(Block.class);
    }

    // ------------------------------------------------------------------
    // Helper: configure signPlaceBlock to handle setType / getBlockData / getState.
    // ------------------------------------------------------------------
    private SignSide frontSide;

    private Directional stubSignBlock()
    {
        final Directional signData = mock(Directional.class);
        final Sign signState = mock(Sign.class);
        final SignSide signSide = mock(SignSide.class);
        final Location loc = mock(Location.class);

        when(signPlaceBlock.getBlockData()).thenReturn(signData);
        when(signPlaceBlock.getState()).thenReturn(signState);
        when(signPlaceBlock.getLocation()).thenReturn(loc);
        when(signState.getSide(Side.FRONT)).thenReturn(signSide);
        frontSide = signSide;

        return signData;
    }

    // ------------------------------------------------------------------
    // Create: sign placed one step in gateFacing direction (NORTH).
    // ------------------------------------------------------------------
    @Test
    void signFacesGateFacingNorth()
    {
        gate.setGateFacing(BlockFace.NORTH);
        gate.setGateNameBlockHolder(nameHolder);

        when(nameHolder.getRelative(BlockFace.NORTH)).thenReturn(signPlaceBlock);
        final Directional signData = stubSignBlock();

        gate.setupGateSign(true);

        verify(signPlaceBlock).setType(Material.OAK_WALL_SIGN, false);
        verify(signData).setFacing(BlockFace.NORTH);
    }

    /** The top line is the gate's own name, wrapped in dashes. */
    @Test
    void theTopLineIsTheGateNameInDashes()
    {
        gate.setGateFacing(BlockFace.NORTH);
        gate.setGateNameBlockHolder(nameHolder);
        gate.setGateName("Alpha");
        when(nameHolder.getRelative(BlockFace.NORTH)).thenReturn(signPlaceBlock);
        stubSignBlock();

        gate.setupGateSign(true);

        verify(frontSide).setLine(eq(0), contains("-Alpha-"));
    }

    /**
     * A long owner name is cut to thirteen characters.
     *
     * <p>Colour codes do not count toward a sign's visible width, so the truncation is on the
     * text alone. Worth pinning because the limit is a bare number in the middle of the line
     * that builds it, and nothing else would notice if it moved.
     */
    @Test
    void aLongOwnerNameIsCutToThirteenCharacters()
    {
        gate.setGateFacing(BlockFace.NORTH);
        gate.setGateNameBlockHolder(nameHolder);
        gate.setGateName("Alpha");
        gate.setGateOwner("AVeryLongOwnerNameIndeed");
        when(nameHolder.getRelative(BlockFace.NORTH)).thenReturn(signPlaceBlock);
        stubSignBlock();

        gate.setupGateSign(true);

        // Thirteen characters exactly: the line carries the 13-character prefix and not the
        // 14-character one, so widening or narrowing the limit fails this either way.
        verify(frontSide).setLine(eq(2), contains("O:AVeryLongOwne"));
        verify(frontSide, never()).setLine(eq(2), contains("AVeryLongOwner"));
    }

    /** A gate on no network gets no network line, rather than an empty one. */
    @Test
    void aGateWithNoNetworkGetsNoNetworkLine()
    {
        gate.setGateFacing(BlockFace.NORTH);
        gate.setGateNameBlockHolder(nameHolder);
        gate.setGateName("Alpha");
        when(nameHolder.getRelative(BlockFace.NORTH)).thenReturn(signPlaceBlock);
        stubSignBlock();

        gate.setupGateSign(true);

        verify(frontSide, never()).setLine(eq(1), any());
    }

    // ------------------------------------------------------------------
    // Create: sign placed one step in gateFacing direction (SOUTH).
    // ------------------------------------------------------------------
    @Test
    void signFacesGateFacingSouth()
    {
        gate.setGateFacing(BlockFace.SOUTH);
        gate.setGateNameBlockHolder(nameHolder);

        when(nameHolder.getRelative(BlockFace.SOUTH)).thenReturn(signPlaceBlock);
        final Directional signData = stubSignBlock();

        gate.setupGateSign(true);

        verify(signPlaceBlock).setType(Material.OAK_WALL_SIGN, false);
        verify(signData).setFacing(BlockFace.SOUTH);
    }

    // ------------------------------------------------------------------
    // Create: sign placed one step in gateFacing direction (EAST).
    // ------------------------------------------------------------------
    @Test
    void signFacesGateFacingEast()
    {
        gate.setGateFacing(BlockFace.EAST);
        gate.setGateNameBlockHolder(nameHolder);

        when(nameHolder.getRelative(BlockFace.EAST)).thenReturn(signPlaceBlock);
        final Directional signData = stubSignBlock();

        gate.setupGateSign(true);

        verify(signPlaceBlock).setType(Material.OAK_WALL_SIGN, false);
        verify(signData).setFacing(BlockFace.EAST);
    }

    // ------------------------------------------------------------------
    // Destroy: sign at nameHolder.getRelative(gateFacing) is removed.
    // ------------------------------------------------------------------
    @Test
    void destroyRemovesSignAtGateFacingPosition()
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
    // Destroy no-op: block at sign position is not a wall sign.
    // ------------------------------------------------------------------
    @Test
    void destroyDoesNothingWhenSignBlockIsNotWallSign()
    {
        gate.setGateFacing(BlockFace.WEST);
        gate.setGateNameBlockHolder(nameHolder);

        when(nameHolder.getRelative(BlockFace.WEST)).thenReturn(signPlaceBlock);
        when(signPlaceBlock.getType()).thenReturn(Material.AIR);

        gate.setupGateSign(false);

        verify(signPlaceBlock, never()).setType(any());
    }
}
