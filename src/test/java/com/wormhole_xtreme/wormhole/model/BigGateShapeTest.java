package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * {@code Large.shape}, {@code Grand.shape} and {@code Massive.shape} -- three big, hand-built
 * gates, each deeper than Standard's.
 *
 * <p>All three were hand-authored outside this codebase and needed real fixes before they
 * were safe to ship: {@code Grand} had three rows one cell short of its declared width (a
 * dropped block while copying its ring pattern into a second layer, which shifts every column
 * after the gap rather than erroring); {@code Massive} skipped straight from {@code Layer#11}
 * to {@code Layer#13} with no {@code Layer#12} ever declared, leaving a silent one-block dead
 * gap in the middle of an otherwise continuous 13-step woosh recession. Neither mistake throws
 * on load -- {@link Stargate3DShape} only derives one width/height from Layer#1 and trusts
 * every later row and layer number to match it -- so both would have shipped invisibly and
 * only been noticed by someone watching the gate animate in game. These tests exist so a
 * future edit to any of the three has to own up to reintroducing that shape, rather than just
 * changing behaviour quietly.
 *
 * <p>{@code Large} originally shipped with Standard's exact proportions (one-layer ring, three
 * woosh steps), just wider. It was reworked to match {@code Grand}'s three-layer ring -- a
 * front bezel, the real portal ring, and a second lit ring -- with four woosh steps behind it,
 * proportionally deeper than {@code Grand}'s three for a ring this much narrower.
 */
public class BigGateShapeTest
{
    private static final Path SHAPE_DIR = Paths.get("src/main/resources/GateShapes");

    private static Stargate3DShape load(final String name) throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        final java.lang.reflect.Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, plugin);

        final List<String> lines = Files.readAllLines(SHAPE_DIR.resolve(name + ".shape"));
        return new Stargate3DShape(lines.toArray(new String[0]));
    }

    private static int countMarker(final Stargate3DShape shape, final java.util.function.Function<StargateShapeLayer, int[]> getter)
    {
        int count = 0;
        for (final StargateShapeLayer layer : shape.getShapeLayers())
        {
            if ((layer != null) && (getter.apply(layer).length == 3))
            {
                count++;
            }
        }
        return count;
    }

    /**
     * Every layer index from 1 up to the shape's own layer count has to actually be present.
     * A layer number skipped while authoring the file (Massive's original bug) leaves a
     * {@code null} in the middle of the array instead of failing to parse.
     */
    private static void assertNoGapInLayers(final Stargate3DShape shape)
    {
        for (int i = 1; i < shape.getShapeLayers().size(); i++)
        {
            assertNotNull(shape.getShapeLayers().get(i),
                "Layer#" + i + " is missing -- a layer number was skipped while authoring the file, "
                    + "leaving a silent dead gap in the woosh recession");
        }
    }

    @Test
    public void allThreeParseAndHaveExactlyOneOfEachSingletonMarker() throws Exception
    {
        for (final String name : new String[] { "Large", "Grand", "Massive" })
        {
            final Stargate3DShape shape = load(name);
            assertEquals(name, shape.getShapeName());
            assertEquals(1, countMarker(shape, StargateShapeLayer::getLayerPlayerExitPosition), name + ": :EP");
            assertEquals(1, countMarker(shape, StargateShapeLayer::getLayerMinecartExitPosition), name + ": :EM");
            assertEquals(1, countMarker(shape, StargateShapeLayer::getLayerActivationPosition), name + ": :A");
            assertEquals(1, countMarker(shape, StargateShapeLayer::getLayerNameSignPosition), name + ": :N");
            assertNoGapInLayers(shape);
        }
    }

    @Test
    public void allThreeLightSevenChevronsWithNoGapOrDuplicateInTheOrder() throws Exception
    {
        for (final String name : new String[] { "Large", "Grand", "Massive" })
        {
            final Stargate3DShape shape = load(name);
            final java.util.Set<Integer> orders = new java.util.TreeSet<Integer>();
            for (final StargateShapeLayer layer : shape.getShapeLayers())
            {
                if (layer == null)
                {
                    continue;
                }
                for (int order = 0; order < layer.getLayerLightPositions().size(); order++)
                {
                    if (layer.getLayerLightPositions().get(order) != null)
                    {
                        orders.add(order);
                    }
                }
            }
            assertEquals(java.util.Set.of(1, 2, 3, 4, 5, 6, 7), orders,
                name + ": expected exactly light orders 1-7, matching Standard's convention, got " + orders);
        }
    }

    @Test
    public void grandsRingIsThreeLayersDeepWithNineWooshStepsBehindIt() throws Exception
    {
        // Grand's ring repeats across Layer#1-#3 (a front bezel, the real portal ring, and a
        // second lit ring) before the woosh recession starts at Layer#4 -- unlike Standard,
        // where the ring is one layer and woosh starts at Layer#2. Grand's :EM was placed on
        // Layer#4, not Layer#3, specifically because Layer#3 still duplicates the solid ring
        // frame at that position; this pins that reasoning so a future edit to the ring's
        // depth doesn't silently leave :EM embedded in a wall again.
        //
        // The woosh recession was widened from 3 steps to 9 after review: at 22 wide, Grand
        // had fewer woosh layers than Large (10 wide, 4 steps) despite being more than double
        // the width -- an artifact of the two being hand-built independently, not a deliberate
        // choice. 9 puts it clearly between Large's 4 and Massive's 13, tapering the same
        // 18-wide portal interior down to a point in steps of 2 (18, 16, 14, ..., 2).
        final Stargate3DShape grand = load("Grand");
        assertEquals(9, grand.getShapeWooshDepth());
        assertEquals(11, grand.getShapeActivationLayer());
    }

    @Test
    public void massiveRecedesThirteenStepsWithNoGapAfterTheLayerElevenFix() throws Exception
    {
        final Stargate3DShape massive = load("Massive");
        assertEquals(13, massive.getShapeWooshDepth());
        assertEquals(16, massive.getShapeLayers().size(), "16 slots: index 0 padding plus Layer#1-#15");
        assertEquals(9, massive.getShapeActivationLayer());
    }
}
