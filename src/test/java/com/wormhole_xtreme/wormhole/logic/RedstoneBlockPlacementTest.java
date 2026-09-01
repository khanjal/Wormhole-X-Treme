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
 * <p>These are markers, not frame blocks — nothing is built at them, and the shape places
 * each directly above an [S] cell, which is what "this block should be on top of a [S]
 * block" means in the shape file. The gate used to register the block one <em>above</em>
 * each marker, which is a frame block. RD and RS still worked by accident, because redstone
 * placed on the marked cell is adjacent to the block above it, but [RA] did not: the
 * gate-activated output only fires when its block is a lever, and the block one above the
 * marker is part of the frame, so the lever a player placed was never found.
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
    public void theShapeIsRedstoneEnabledAndHasADialSignToTarget()
    {
        // [RD] dials whatever the dial sign shows, so a redstone shape without a [D] block
        // would have nothing to dial.
        assertTrue(shape.isShapeRedstoneActivated(), "REDSTONE_ACTIVATED should be TRUE");
        assertTrue(shape.getShapeLayers().get(2).getLayerDialSignPosition().length >= 3,
            "a redstone-dialled shape needs a [D] dial sign block");
    }
}
