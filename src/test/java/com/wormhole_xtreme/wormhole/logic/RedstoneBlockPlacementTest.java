package com.wormhole_xtreme.wormhole.logic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.Stargate3DShape;
import com.wormhole_xtreme.wormhole.model.StargateShapeLayer;

/**
 * Where a shape's [RD], [RS] and [RA] cells put their redstone in the world.
 *
 * <p>Shapes write these two ways. A bare [RA] is not a frame block: it is the empty cell
 * above one, and that cell is where the redstone goes. An [S:RA] <em>is</em> the frame
 * block, so the redstone belongs one above it. Assuming either convention alone puts the
 * component in the wrong place for shapes written the other way, which happened twice —
 * first always adding one, then always not.
 *
 * <p>Both conventions exist to serve one rule, and that rule is what these tests hold the
 * shipped shapes to: the cell a marker resolves to has to be empty. Land it on a frame
 * block and the gate watches a wall for a signal, or looks for a lever somewhere a frame
 * block always is. Nothing errors; the gate's redstone just silently never fires.
 */
public class RedstoneBlockPlacementTest
{
    private static final Path SHAPE_DIR = Paths.get("src/main/resources/GateShapes");

    @BeforeEach
    public void setUp() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        final java.lang.reflect.Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, plugin);
    }

    private static Stargate3DShape load(final String name) throws Exception
    {
        final List<String> lines = Files.readAllLines(SHAPE_DIR.resolve(name + ".shape"));
        return new Stargate3DShape(lines.toArray(new String[0]));
    }

    private static List<String> shippedShapeNames() throws Exception
    {
        final List<String> names = new ArrayList<String>();
        // try-with-resources: Files.list holds an open directory handle until closed.
        try (java.util.stream.Stream<Path> listing = Files.list(SHAPE_DIR))
        {
            for (final Path p : listing.toList())
            {
                final String file = p.getFileName().toString();
                if (file.endsWith(".shape"))
                {
                    names.add(file.substring(0, file.length() - ".shape".length()));
                }
            }
        }
        java.util.Collections.sort(names);
        return names;
    }

    /** True when the layer builds a frame block at the given grid height and column. */
    private static boolean isFrameAt(final StargateShapeLayer layer, final int y, final int col)
    {
        for (final Integer[] p : layer.getLayerBlockPositions())
        {
            if ((p[1].intValue() == y) && (p[2].intValue() == col))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Every marker in every shipped shape has to resolve to a cell nothing is built in.
     *
     * <p>Sweeping the whole directory rather than naming a shape or two is deliberate: the
     * failure is silent in game, so a shape added later should be caught here rather than by
     * someone wiring up redstone that never does anything.
     */
    @Test
    public void everyShippedMarkerResolvesToAnEmptyCell() throws Exception
    {
        int markersChecked = 0;

        for (final String name : shippedShapeNames())
        {
            final Stargate3DShape shape = load(name);
            for (final StargateShapeLayer layer : shape.getShapeLayers())
            {
                if (layer == null)
                {
                    continue;
                }
                final int[][] markers = {
                    layer.getLayerRedstoneDialActivationPosition(),
                    layer.getLayerRedstoneSignActivationPosition(),
                    layer.getLayerRedstoneGateActivatedPosition(),
                };
                final String[] labels = { "[RD]", "[RS]", "[RA]" };

                for (int i = 0; i < markers.length; i++)
                {
                    if (markers[i].length < 3)
                    {
                        continue;
                    }
                    markersChecked++;
                    // A marker's grid height is 0-based the same way the world height is, so
                    // passing the grid height as the base makes the answer a grid height too
                    // and it can be looked straight back up in the layer.
                    final int resolved =
                        StargateHelper.redstoneComponentY(layer, markers[i], markers[i][1]);
                    assertFalse(isFrameAt(layer, resolved, markers[i][2]),
                        name + " puts " + labels[i] + " where the frame is, so redstone there can never fire");
                }
            }
        }

        assertTrue(markersChecked > 0, "no markers were found to check, so this proved nothing");
    }

    @Test
    public void aBareMarkerKeepsItsOwnCellAndAFrameMarkerTakesTheOneAbove() throws Exception
    {
        // Both conventions pinned from one shape, so the branch stays covered no matter
        // which form the shipped shapes happen to use at any given time.
        final Stargate3DShape shape = load("MinimalSignDialRedstone");
        final StargateShapeLayer dhd = shape.getShapeLayers().get(2);
        final int[] bare = dhd.getLayerRedstoneDialActivationPosition();

        assertFalse(isFrameAt(dhd, bare[1], bare[2]), "MinimalSignDialRedstone writes the bare form");
        assertEquals(70, StargateHelper.redstoneComponentY(dhd, bare, 70),
            "a bare marker is already the empty cell, so the redstone goes at the marker");

        // The frame form: aim the same call at a cell this layer does build in.
        final Integer[] frameCell = dhd.getLayerBlockPositions().get(0);
        final int[] asFrameMarker = { 0, frameCell[1].intValue(), frameCell[2].intValue() };
        assertEquals(71, StargateHelper.redstoneComponentY(dhd, asFrameMarker, 70),
            "an [S:RA] style marker is the frame block, so the redstone goes one above it");
    }

    @Test
    public void aBareMarkerSitsDirectlyOnAFrameBlock() throws Exception
    {
        // The shape file's own rule for the bare form, and what makes the marker cell a
        // place redstone can actually be put rather than open air.
        final Stargate3DShape shape = load("MinimalSignDialRedstone");
        final StargateShapeLayer dhd = shape.getShapeLayers().get(2);
        final int[] rd = dhd.getLayerRedstoneDialActivationPosition();
        final int[] rs = dhd.getLayerRedstoneSignActivationPosition();

        assertTrue(isFrameAt(dhd, rd[1] - 1, rd[2]), "[RD] should sit directly on a frame block");
        assertTrue(isFrameAt(dhd, rs[1] - 1, rs[2]), "[RS] should sit directly on a frame block");

        final StargateShapeLayer ring = shape.getShapeLayers().get(1);
        final int[] ra = ring.getLayerRedstoneGateActivatedPosition();
        assertTrue(isFrameAt(ring, ra[1] - 1, ra[2]), "[RA] should sit directly on a frame block");
    }

    @Test
    public void theTwoInputMarkersAreNeverWithinReachOfEachOther() throws Exception
    {
        // A signal counts when it lands on a marked block or on anything touching it, and
        // the check for that is a 3x3x3 box. [RD] and [RS] are both inputs and both act on
        // a closed gate, so putting them within a block of each other gives one pulse two
        // meanings: cycle the destination, then dial whatever it just landed on. They are
        // also both redstone dust, which physically connects when adjacent.
        //
        // Nothing can guard against this the way the open-gate check guards [RA], because
        // on a closed gate both commands are legitimate. Separation is the only fix, and it
        // is why StandardSignDial ships [RD] without [RS]: its DHD has one free block top
        // and every other candidate cell touches it.
        //
        // Layers count as a third axis here — consecutive layers are one block apart along
        // the gate's facing, so markers on neighbouring layers are neighbours in the world.
        for (final String name : shippedShapeNames())
        {
            final Stargate3DShape shape = load(name);
            final List<int[]> dial = new ArrayList<int[]>();
            final List<int[]> cycle = new ArrayList<int[]>();

            int layerIndex = 0;
            for (final StargateShapeLayer layer : shape.getShapeLayers())
            {
                layerIndex++;
                if (layer == null)
                {
                    continue;
                }
                collect(dial, layer.getLayerRedstoneDialActivationPosition(), layerIndex);
                collect(cycle, layer.getLayerRedstoneSignActivationPosition(), layerIndex);
            }

            for (final int[] rd : dial)
            {
                for (final int[] rs : cycle)
                {
                    assertFalse(touching(rd, rs),
                        name + " puts [RD] and [RS] within one block, so one signal would"
                            + " cycle the destination and dial it in the same pulse");
                }
            }
        }
    }

    /** Records a marker as {layer, height, column} if the layer actually has one. */
    private static void collect(final List<int[]> into, final int[] marker, final int layerIndex)
    {
        if (marker.length >= 3)
        {
            into.add(new int[] { layerIndex, marker[1], marker[2] });
        }
    }

    /** Whether two {layer, height, column} points are within one block on every axis. */
    private static boolean touching(final int[] a, final int[] b)
    {
        return (Math.abs(a[0] - b[0]) <= 1)
            && (Math.abs(a[1] - b[1]) <= 1)
            && (Math.abs(a[2] - b[2]) <= 1);
    }

    @Test
    public void aRedstoneDialMarkerAlwaysHasADialSignToRead() throws Exception
    {
        // [RD] dials whatever the dial sign shows, so a shape offering redstone dialling
        // without a [D] block would have nothing to dial.
        for (final String name : shippedShapeNames())
        {
            final Stargate3DShape shape = load(name);
            boolean hasDialMarker = false;
            boolean hasDialSign = false;
            for (final StargateShapeLayer layer : shape.getShapeLayers())
            {
                if (layer == null)
                {
                    continue;
                }
                hasDialMarker |= layer.getLayerRedstoneDialActivationPosition().length >= 3;
                hasDialSign |= layer.getLayerDialSignPosition().length >= 3;
            }
            if (hasDialMarker)
            {
                assertTrue(hasDialSign, name + " has [RD] but no [D] sign block for it to read");
            }
        }
    }

    @Test
    public void theRedstoneShapeAdvertisesItself() throws Exception
    {
        assertTrue(load("MinimalSignDialRedstone").isShapeRedstoneActivated(),
            "REDSTONE_ACTIVATED should be TRUE");
    }
}
