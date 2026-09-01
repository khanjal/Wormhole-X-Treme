package com.wormhole_xtreme.wormhole.logic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.Stargate3DShape;
import com.wormhole_xtreme.wormhole.model.StargateShapeLayer;

/**
 * Where a redstone-capable shape's [RD], [RS] and [RA] cells end up in the world.
 *
 * <p>There are two conventions and the code has to tell them apart. A bare [RA] is not a
 * frame block: it is the empty cell above one, and that cell is where the redstone goes —
 * MinimalSignDialRedstone is written this way. An [S:RA] <em>is</em> the frame block, so the
 * redstone belongs one above it — StandardSignDial is written that way.
 *
 * <p>Assuming either convention alone breaks the other shape, which happened twice: first
 * always adding one, then always not.
 */
public class RedstoneBlockPlacementTest
{
    private Stargate3DShape shape;

    @BeforeEach
    public void setUp() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        final java.lang.reflect.Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, plugin);

        final List<String> lines = Files.readAllLines(
            Paths.get("src/main/resources/GateShapes/3d/MinimalSignDialRedstone.shape"));
        shape = new Stargate3DShape(lines.toArray(new String[0]));
    }

    /** The heights at which the given layer has frame blocks. */
    private static java.util.Set<Integer> structureHeights(final StargateShapeLayer layer)
    {
        final java.util.Set<Integer> ys = new java.util.HashSet<Integer>();
        for (final Integer[] p : layer.getLayerBlockPositions())
        {
            ys.add(p[1]);
        }
        return ys;
    }

    @Test
    public void theRedstoneMarkersAreNotFrameBlocks()
    {
        // Nothing is built at a marker, so detection must not expect the frame material
        // there and the gate must not treat it as part of the structure.
        final StargateShapeLayer dhdLayer = shape.getShapeLayers().get(2);
        for (final Integer[] p : dhdLayer.getLayerBlockPositions())
        {
            final int[] rd = dhdLayer.getLayerRedstoneDialActivationPosition();
            final int[] rs = dhdLayer.getLayerRedstoneSignActivationPosition();
            assertFalse(p[1] == rd[1] && p[2] == rd[2], "[RD] must not also be a frame block");
            assertFalse(p[1] == rs[1] && p[2] == rs[2], "[RS] must not also be a frame block");
        }
    }

    @Test
    public void eachMarkerSitsDirectlyOnAFrameBlock()
    {
        // This is the shape file's own rule, and it is what makes the marker the right
        // place to put redstone rather than the block above it.
        final StargateShapeLayer dhd = shape.getShapeLayers().get(2);
        final java.util.Set<Integer> dhdFrame = structureHeights(dhd);
        assertTrue(dhdFrame.contains(dhd.getLayerRedstoneDialActivationPosition()[1] - 1),
            "[RD] should sit directly on a frame block");
        assertTrue(dhdFrame.contains(dhd.getLayerRedstoneSignActivationPosition()[1] - 1),
            "[RS] should sit directly on a frame block");

        final StargateShapeLayer ring = shape.getShapeLayers().get(1);
        assertTrue(structureHeights(ring).contains(ring.getLayerRedstoneGateActivatedPosition()[1] - 1),
            "[RA] should sit directly on a frame block");
    }

    @Test
    public void theBlockAboveEachMarkerIsPartOfTheFrame()
    {
        // The specific reason the old offset was wrong: one block up is the frame, so the
        // gate was watching a wall instead of the spot the shape marks, and looking for a
        // lever where a frame block always is.
        final StargateShapeLayer dhd = shape.getShapeLayers().get(2);
        final java.util.Set<Integer> dhdFrame = structureHeights(dhd);
        assertTrue(dhdFrame.contains(dhd.getLayerRedstoneDialActivationPosition()[1] + 1),
            "the block above [RD] is frame, which is what the old +1 selected");

        final StargateShapeLayer ring = shape.getShapeLayers().get(1);
        assertTrue(structureHeights(ring).contains(ring.getLayerRedstoneGateActivatedPosition()[1] + 1),
            "the block above [RA] is frame, so a lever there could never be found");
    }

    @Test
    public void theOtherMarkerConventionIsAFrameBlockAndNeedsTheBlockAbove()
    {
        // StandardSignDial writes its marker as [S:RA] — the cell is the frame block, so
        // the lever belongs one above. MinimalSignDialRedstone writes a bare [RA] sitting
        // on a frame block, where the lever belongs at the marker. Assuming either
        // convention on its own breaks the other shape, which is what happened twice.
        final java.util.List<String> lines;
        try
        {
            lines = Files.readAllLines(
                Paths.get("src/main/resources/GateShapes/3d/StandardSignDial.shape"));
        }
        catch (final java.io.IOException e)
        {
            throw new IllegalStateException(e);
        }
        final Stargate3DShape standard = new Stargate3DShape(lines.toArray(new String[0]));

        StargateShapeLayer withRa = null;
        for (final StargateShapeLayer layer : standard.getShapeLayers())
        {
            if (layer != null && layer.getLayerRedstoneGateActivatedPosition().length >= 3)
            {
                withRa = layer;
            }
        }
        assertNotNull(withRa, "StandardSignDial should mark a gate-activated block");

        final int[] ra = withRa.getLayerRedstoneGateActivatedPosition();
        boolean markerIsFrame = false;
        for (final Integer[] b : withRa.getLayerBlockPositions())
        {
            if (b[1].intValue() == ra[1] && b[2].intValue() == ra[2])
            {
                markerIsFrame = true;
            }
        }
        assertTrue(markerIsFrame, "[S:RA] is itself a frame block, unlike a bare [RA]");
    }

    @Test
    public void theShapeIsRedstoneEnabledAndHasADialSignToTarget()
    {
        // [RD] dials whatever the dial sign shows, so a redstone shape without a [D] block
        // would have nothing to dial.
        assertTrue(shape.isShapeRedstoneActivated(), "REDSTONE_ACTIVATED should be TRUE");
        assertTrue(shape.getShapeLayers().get(2).getLayerDialSignPosition().length >= 3,
            "a redstone-dialled shape needs a [D] dial sign block");
    }
}
