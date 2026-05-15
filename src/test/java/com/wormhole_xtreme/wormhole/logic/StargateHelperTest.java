package com.wormhole_xtreme.wormhole.logic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.model.Stargate;

/**
 * Tests for {@link StargateHelper#computeGateFacingFromGeometry}.
 *
 * <p>Verifies that the variance-based geometry analysis correctly determines the
 * gate's true facing direction from the spatial layout of its structure blocks,
 * and that the DHD block coordinate is properly excluded from that computation.
 */
public class StargateHelperTest
{
    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static Block mockDhd(final int x, final int y, final int z)
    {
        final Block b = mock(Block.class);
        when(b.getX()).thenReturn(x);
        when(b.getY()).thenReturn(y);
        when(b.getZ()).thenReturn(z);
        return b;
    }

    private static void addBlock(final Stargate gate, final int x, final int y, final int z)
    {
        gate.getGateStructureBlocks().add(new Location(null, x, y, z));
    }

    // -----------------------------------------------------------------------
    // Cardinal-direction tests
    // -----------------------------------------------------------------------

    /**
     * Frame spread along X at constant Z; DHD placed south (higher Z) → SOUTH.
     *
     * <p>Layout (top view):
     * <pre>
     *   frame  ░░░░░   Z=5
     *   DHD        *   Z=8
     * </pre>
     */
    @Test
    public void southFacingGateReturnsSOUTH()
    {
        final Stargate gate = new Stargate();
        gate.setGateName("south");
        gate.setGateDialLeverBlock(mockDhd(2, 53, 8));
        addBlock(gate, 0, 52, 5);
        addBlock(gate, 1, 52, 5);
        addBlock(gate, 2, 52, 5);
        addBlock(gate, 3, 52, 5);
        addBlock(gate, 4, 52, 5);

        assertEquals(BlockFace.SOUTH, StargateHelper.computeGateFacingFromGeometry(gate));
    }

    /**
     * Frame spread along X at constant Z; DHD placed north (lower Z) → NORTH.
     */
    @Test
    public void northFacingGateReturnsNORTH()
    {
        final Stargate gate = new Stargate();
        gate.setGateName("north");
        gate.setGateDialLeverBlock(mockDhd(2, 53, 2));
        addBlock(gate, 0, 52, 5);
        addBlock(gate, 1, 52, 5);
        addBlock(gate, 2, 52, 5);
        addBlock(gate, 3, 52, 5);
        addBlock(gate, 4, 52, 5);

        assertEquals(BlockFace.NORTH, StargateHelper.computeGateFacingFromGeometry(gate));
    }

    /**
     * Frame spread along Z at constant X; DHD placed east (higher X) → EAST.
     */
    @Test
    public void eastFacingGateReturnsEAST()
    {
        final Stargate gate = new Stargate();
        gate.setGateName("east");
        gate.setGateDialLeverBlock(mockDhd(8, 53, 2));
        addBlock(gate, 5, 52, 0);
        addBlock(gate, 5, 52, 1);
        addBlock(gate, 5, 52, 2);
        addBlock(gate, 5, 52, 3);
        addBlock(gate, 5, 52, 4);

        assertEquals(BlockFace.EAST, StargateHelper.computeGateFacingFromGeometry(gate));
    }

    /**
     * Frame spread along Z at constant X; DHD placed west (lower X) → WEST.
     */
    @Test
    public void westFacingGateReturnsWEST()
    {
        final Stargate gate = new Stargate();
        gate.setGateName("west");
        gate.setGateDialLeverBlock(mockDhd(2, 53, 2));
        addBlock(gate, 5, 52, 0);
        addBlock(gate, 5, 52, 1);
        addBlock(gate, 5, 52, 2);
        addBlock(gate, 5, 52, 3);
        addBlock(gate, 5, 52, 4);

        assertEquals(BlockFace.WEST, StargateHelper.computeGateFacingFromGeometry(gate));
    }

    // -----------------------------------------------------------------------
    // Edge / degenerate cases
    // -----------------------------------------------------------------------

    /**
     * When varX == varZ the result is indeterminate → null.
     */
    @Test
    public void equalVarianceReturnsNull()
    {
        final Stargate gate = new Stargate();
        gate.setGateName("degenerate");
        gate.setGateDialLeverBlock(mockDhd(5, 53, 5));
        // Symmetric 2×2 arrangement: equal spread in X and Z
        addBlock(gate, 3, 52, 3);
        addBlock(gate, 7, 52, 3);
        addBlock(gate, 3, 52, 7);
        addBlock(gate, 7, 52, 7);

        assertNull(StargateHelper.computeGateFacingFromGeometry(gate));
    }

    /**
     * Null DHD block → null (no NPE).
     */
    @Test
    public void nullDhdBlockReturnsNull()
    {
        final Stargate gate = new Stargate();
        gate.setGateName("nullDhd");
        gate.setGateDialLeverBlock(null);
        addBlock(gate, 0, 52, 5);

        assertNull(StargateHelper.computeGateFacingFromGeometry(gate));
    }

    /**
     * Empty structure block set → null (early-exit guard).
     */
    @Test
    public void emptyStructureBlocksReturnsNull()
    {
        final Stargate gate = new Stargate();
        gate.setGateName("empty");
        gate.setGateDialLeverBlock(mockDhd(2, 53, 8));
        // No structure blocks added

        assertNull(StargateHelper.computeGateFacingFromGeometry(gate));
    }

    /**
     * DHD is the only entry in the structure block set; after exclusion count == 0 → null.
     */
    @Test
    public void onlyDhdInStructureBlocksReturnsNull()
    {
        final Stargate gate = new Stargate();
        gate.setGateName("dhdOnly");
        gate.setGateDialLeverBlock(mockDhd(2, 53, 5));
        gate.getGateStructureBlocks().add(new Location(null, 2, 53, 5)); // same coords as DHD

        assertNull(StargateHelper.computeGateFacingFromGeometry(gate));
    }

    /**
     * DHD block in the structure set at an extreme coordinate must NOT skew the variance.
     *
     * <p>The frame blocks all share Z=5 (spread along X). The DHD is at Z=1000 which
     * would completely dominate the variance if it were included.  After exclusion the
     * result must still be SOUTH (dhdZ=1000 > meanZ=5).
     */
    @Test
    public void dhdBlockExcludedFromVarianceComputation()
    {
        final Stargate gate = new Stargate();
        gate.setGateName("exclude");
        gate.setGateDialLeverBlock(mockDhd(2, 53, 1000));
        // DHD location also present in the structure set
        gate.getGateStructureBlocks().add(new Location(null, 2, 53, 1000));
        addBlock(gate, 0, 52, 5);
        addBlock(gate, 1, 52, 5);
        addBlock(gate, 2, 52, 5);
        addBlock(gate, 3, 52, 5);
        addBlock(gate, 4, 52, 5);

        // Without exclusion, the DHD at Z=1000 would make varZ >> varX → wrong result.
        // With correct exclusion: frame at constant Z=5, varX > varZ → SOUTH.
        assertEquals(BlockFace.SOUTH, StargateHelper.computeGateFacingFromGeometry(gate));
    }
}
