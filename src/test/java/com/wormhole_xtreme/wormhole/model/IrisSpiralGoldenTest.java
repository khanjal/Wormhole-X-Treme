package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.logic.GateGrid;

/**
 * The spiral's closing steps on the shipped gates, in shape coordinates, pinned to a file the
 * docs page's port is checked against too.
 *
 * <p>The page draws the spiral from {@code docs/javascripts/iris-sweep.js}, which has no world and
 * no facing: it can only be right if a real gate, built through {@link GateGrid} and swept by
 * {@link IrisSweep} with its facing, turns the same way in the shape's own coordinates whichever
 * way it faces. This builds each gate facing each of the four ways, maps every step back to its
 * shape cell, and requires all four to equal {@code iris/spiral-golden.txt};
 * {@code docs/javascripts/test/iris-sweep.test.js} requires the port to equal the same file. A
 * port that wound the other way fails there. South and east are what the sweep guesses for a
 * gate with no facing, so they alone would pass with the facing dropped; north and west would not.
 *
 * <p>Large and Grand have openings an even number of blocks across, so their centres fall on
 * half blocks: any difference between Java's and V8's rounding or arctangent shows there.
 *
 * <p>Run with {@code -Dwormhole.golden.write=true} to rewrite the file after a deliberate change.
 */
class IrisSpiralGoldenTest
{
    private static final Path SHAPE_DIR = Paths.get("src/main/resources/shapes/gate");
    private static final Path GOLDEN = Paths.get("src/test/resources/iris/spiral-golden.txt");
    private static final List<String> SHAPES = List.of("Standard", "Large", "Massive", "Grand");

    @BeforeEach
    void setUp() throws Exception
    {
        // Reading a shape file logs through the plugin.
        PluginTestSupport.install(mock(WormholeXTreme.class));
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PluginTestSupport.remove();
    }

    @Test
    void aBuiltGateSpiralsTheSameInItsShapeWhicheverWayItFacesAndAsTheGoldenFileSays() throws IOException
    {
        final List<String> south = describe(BlockFace.SOUTH);
        for (final BlockFace facing : new BlockFace[] { BlockFace.NORTH, BlockFace.EAST, BlockFace.WEST })
        {
            assertEquals(south, describe(facing), "a gate facing " + facing
                + " must wind the same way round its shape as one facing south, or its facing is not used");
        }

        if (Boolean.getBoolean("wormhole.golden.write"))
        {
            Files.createDirectories(GOLDEN.getParent());
            Files.write(GOLDEN, south, StandardCharsets.UTF_8);
        }
        final List<String> golden = Files.readAllLines(GOLDEN, StandardCharsets.UTF_8).stream()
            .filter(line -> !line.isEmpty() && !line.startsWith("#"))
            .toList();
        assertTrue(golden.size() > SHAPES.size(), "the golden file has a step per line: " + golden.size());
        assertEquals(golden, south.stream().filter(line -> !line.startsWith("#")).toList(),
            "the plugin's spiral no longer matches iris/spiral-golden.txt; if that is deliberate, rewrite it and"
                + " check the docs page's port still matches");
    }

    /**
     * The arm is a block wide on Standard and Large, and two on Massive and Grand.
     *
     * <p>A fifth of the rim's radius, rounded up: Standard's rim is 2.2 blocks out, Large's 3.8,
     * Massive's 8.2 and Grand's 9.9. Counting rings instead of blocks would make Grand's arm
     * thirteen wide, wider than its radius, and the spiral a clock hand.
     */
    @Test
    void theArmIsABlockWideOnTheSmallerGatesAndTwoOnMassiveAndGrand() throws IOException
    {
        final Map<String, Double> wide = Map.of("Standard", 1.0, "Large", 1.0, "Massive", 2.0, "Grand", 2.0);
        for (final String name : SHAPES)
        {
            final List<Location> cells = opening(name, BlockFace.SOUTH, new IdentityHashMap<>());
            assertEquals(wide.get(name), IrisSweep.closingArm(cells, BlockFace.SOUTH).width(), name + "'s arm");
        }
    }

    /**
     * A shipped gate's opening, built facing that way through the real grid.
     *
     * @param place
     *            filled with each cell's shape position, {0, y, x}
     */
    private static List<Location> opening(final String name, final BlockFace facing,
        final Map<Location, Integer[]> place) throws IOException
    {
        final Stargate3DShape shape = new Stargate3DShape(
            Files.readAllLines(SHAPE_DIR.resolve(name + ".shape")).toArray(new String[0]));
        final GateGrid grid = GateGrid.fromActivationHolder(shape, 100, 64, -200, facing);
        final List<Location> cells = new ArrayList<>();
        final List<StargateShapeLayer> layers = shape.getShapeLayers();
        for (int layerIdx = 1; layerIdx < layers.size(); layerIdx++)
        {
            final StargateShapeLayer layer = layers.get(layerIdx);
            if (layer == null)
            {
                continue;
            }
            for (final Integer[] pos : layer.getLayerPortalPositions())
            {
                final Location at = new Location(null, grid.x(layerIdx, pos[2]), grid.y(pos[1]),
                    grid.z(layerIdx, pos[2]));
                cells.add(at);
                place.put(at, pos);
            }
        }
        return cells;
    }

    /** Every shape's closing spiral, a line per step: "Shape step: x,y x,y ...", cells sorted. */
    private static List<String> describe(final BlockFace facing) throws IOException
    {
        final List<String> out = new ArrayList<>();
        out.add("# Closing spiral steps per shape, seen from the activation switch. x is the shape's column");
        out.add("# from the right as StargateShapeLayer counts it (the docs parser's x), y counts up from the");
        out.add("# bottom row. The same for a gate facing any of the four ways. Written by IrisSpiralGoldenTest;");
        out.add("# iris-sweep.test.js reads it too.");
        for (final String name : SHAPES)
        {
            final Map<Location, Integer[]> place = new IdentityHashMap<>();
            final List<Location> cells = opening(name, facing, place);
            final List<List<Location>> steps =
                IrisSweep.closingOrder(cells, IrisSweep.Style.SPIRAL, IrisSweepDriver.stepCap(), facing);
            for (int i = 0; i < steps.size(); i++)
            {
                final List<Integer[]> step = new ArrayList<>();
                for (final Location at : steps.get(i))
                {
                    step.add(place.get(at));
                }
                step.sort((a, b) -> (a[1].intValue() == b[1].intValue()) ? Integer.compare(a[2], b[2])
                    : Integer.compare(a[1], b[1]));
                final StringBuilder line = new StringBuilder(name).append(' ').append(i + 1).append(':');
                for (final Integer[] pos : step)
                {
                    line.append(' ').append(pos[2]).append(',').append(pos[1]);
                }
                out.add(line.toString());
            }
        }
        return out;
    }
}
