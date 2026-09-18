package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * Shipped gates light their chevrons in the show's order: down the right side, up the left, and
 * the top one last, with an eighth at the bottom for another world.
 *
 * <p>Seen from the DHD, a cell's column counts from the right. A horizontal gate's "top" is its
 * far edge, Layer#1.
 */
class ChevronOrderTest
{
    /** A light cell: which layer, how high, and how far from the right. */
    private record Light(int order, int layer, int row, int col)
    {
    }

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PluginTestSupport.remove();
    }

    private static List<Light> lights(final String name) throws Exception
    {
        final Stargate3DShape shape = new Stargate3DShape(
            Files.readAllLines(Paths.get("src/main/resources/shapes/gate", name + ".shape")).toArray(new String[0]));
        final List<Light> lights = new ArrayList<>();
        for (int layerIdx = 1; layerIdx < shape.getShapeLayers().size(); layerIdx++)
        {
            final StargateShapeLayer layer = shape.getShapeLayers().get(layerIdx);
            if (layer == null)
            {
                continue;
            }
            for (int order = 0; order < layer.getLayerLightPositions().size(); order++)
            {
                final List<Integer[]> cells = layer.getLayerLightPositions().get(order);
                if (cells == null)
                {
                    continue;
                }
                for (final Integer[] pos : cells)
                {
                    lights.add(new Light(order, layerIdx, pos[1], pos[2]));
                }
            }
        }
        return lights;
    }

    private static void assertShowOrder(final String name, final boolean flat) throws Exception
    {
        final List<Light> lights = lights(name);
        final int minCol = lights.stream().mapToInt(Light::col).min().orElseThrow();
        final int maxCol = lights.stream().mapToInt(Light::col).max().orElseThrow();
        final double centre = (minCol + maxCol) / 2.0;
        final int top = lights.stream().mapToInt(l -> height(l, flat)).max().orElseThrow();

        final List<Integer> orders = lights.stream().map(Light::order).distinct().sorted().toList();
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7), orders.subList(0, Math.min(7, orders.size())),
            name + ": seven chevrons light in a dial within one world");
        assertEquals(8, orders.size(), name + ": an eighth, for another world");
        {
            final int bottom = lights.stream().mapToInt(l -> height(l, flat)).min().orElseThrow();
            assertEquals(bottom, lights.stream().filter(l -> l.order() == 8).mapToInt(l -> height(l, flat)).min()
                .orElseThrow(), name + ": the eighth chevron is at the bottom");
        }
        assertEquals(top, highest(lights, 7, flat), name + ": the seventh chevron is the top one");
        for (final Light light : lights)
        {
            if (light.order() >= 7)
            {
                continue;
            }
            if (light.order() <= 3)
            {
                assertTrue(light.col() < centre, name + ": chevron " + light.order() + " is on the right");
            }
            else
            {
                assertTrue(light.col() > centre, name + ": chevron " + light.order() + " is on the left");
            }
        }
        for (final int[] pair : new int[][] { { 1, 2 }, { 2, 3 } })
        {
            assertTrue(highest(lights, pair[0], flat) > highest(lights, pair[1], flat),
                name + ": the right side lights downward");
        }
        for (final int[] pair : new int[][] { { 4, 5 }, { 5, 6 } })
        {
            assertTrue(highest(lights, pair[0], flat) < highest(lights, pair[1], flat),
                name + ": the left side lights upward");
        }
    }

    /** How high a cell stands; on a flat gate, how far toward its far edge. */
    private static int height(final Light light, final boolean flat)
    {
        return flat ? -light.layer() : light.row();
    }

    /** How high a chevron's highest cell stands. */
    private static int highest(final List<Light> lights, final int order, final boolean flat)
    {
        return lights.stream().filter(l -> l.order() == order).mapToInt(l -> height(l, flat)).max().orElseThrow();
    }

    @Test
    void standingGatesLightDownTheRightUpTheLeftAndTheTopLast() throws Exception
    {
        for (final String name : new String[] { "Standard", "StandardSignDial", "Large", "Grand", "Massive" })
        {
            assertShowOrder(name, false);
        }
    }

    @Test
    void horizontalGatesLightTheirFarEdgeLast() throws Exception
    {
        for (final String name : new String[] { "Horizontal", "HorizontalSignDial" })
        {
            assertShowOrder(name, true);
        }
    }
}
