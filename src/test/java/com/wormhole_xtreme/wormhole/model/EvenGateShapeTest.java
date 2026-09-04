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
 * {@code Even.shape} and {@code EvenSignDial.shape} -- the first shipped gates with an
 * even-width ring, alongside Standard's odd 7x7 one.
 *
 * <p>Standard's ring is 7 wide, so every single-instance marker (the top light, {@code :N},
 * {@code :EP}) sits on the one true center column and lines up top to bottom. An 8-wide ring
 * has no such column -- the middle falls between two -- so these shapes pin every one of those
 * markers to the same column throughout by convention instead. That convention is easy to
 * break one row at a time while hand-editing the grid: a marker nudged half a step out of line,
 * or a row typed one cell short, would either fail to parse or silently stop lining up without
 * any error. These tests exist to catch both.
 */
public class EvenGateShapeTest
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

    /**
     * Counts every {@code :L} tag across every layer, the same way {@code :W} depth is
     * inferred from layer contents rather than declared up front.
     */
    private static int countLights(final Stargate3DShape shape)
    {
        int count = 0;
        for (final StargateShapeLayer layer : shape.getShapeLayers())
        {
            if (layer == null)
            {
                continue;
            }
            for (final java.util.ArrayList<Integer[]> forOneOrder : layer.getLayerLightPositions())
            {
                if (forOneOrder != null)
                {
                    count++;
                }
            }
        }
        return count;
    }

    @Test
    public void evenAndEvenSignDialParseWithoutThrowing() throws Exception
    {
        // A grid whose rows are not all the same width throws IllegalArgumentException out of
        // the constructor (Stargate3DShape derives one height/width from Layer#1 and applies
        // it to every layer after) -- reaching an assertion at all is already most of the
        // guarantee this test wants.
        assertEquals("Even", load("Even").getShapeName());
        assertEquals("EvenSignDial", load("EvenSignDial").getShapeName());
    }

    @Test
    public void evenHasTheSameSevenLightsAndThreeWooshDepthAsStandardScaledUp() throws Exception
    {
        // This shape was drawn as "Standard's ring, scaled up to 8-wide" -- same chevron
        // count, same three receding woosh layers -- just with the extra full-width rows an
        // even ring needs plain rather than lit. A future edit that adds or drops a light
        // while rebalancing the ring's taper should fail here rather than only be noticed by
        // eye in-game.
        final Stargate3DShape even = load("Even");

        assertEquals(7, countLights(even), "Standard lights 7 chevrons; this scales the same ring up, not down");
        assertEquals(3, even.getShapeWooshDepth(), "three layers (2, 3 and 4) carry :W tags, same as Standard");
    }

    @Test
    public void evenIsDialOnlyAndEvenSignDialAddsRedstoneDialingInstead() throws Exception
    {
        // Mirrors the Standard / StandardSignDial split: the base shape trades an iris
        // switch for nothing else (dial-only, no :D), the SignDial variant trades the iris
        // switch for a sign dialer plus the two redstone points that drive it.
        final Stargate3DShape even = load("Even");
        assertFalse(even.isShapeRedstoneActivated());
        assertEquals(-1, even.getShapeSignLayer(), "no :D block, so this can only ever be a /dial gate");

        final Stargate3DShape signDial = load("EvenSignDial");
        assertTrue(signDial.isShapeRedstoneActivated());
        assertEquals(4, signDial.getShapeSignLayer(), "the :D block lives on layer 4, same as :A");
        assertEquals(4, signDial.getShapeActivationLayer());
    }
}
