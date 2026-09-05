package com.wormhole_xtreme.wormhole.model;

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

/**
 * Every shipped shape can take an iris.
 *
 * <p>A shape says where the iris lever hangs with {@code :IA}. Leave it out and
 * {@code StargateBlockSetup.setupIrisLever} works the position out from the DHD button
 * instead — but only for a shape that does <em>not</em> declare
 * {@code REDSTONE_ACTIVATED=TRUE}, because that flag turns the fallback off.
 *
 * <p>{@code StandardSignDial} and {@code EvenSignDial} sat in exactly that gap: no
 * {@code :IA}, and the flag set. They got no iris from the shape and none from the fallback,
 * so choosing a sign dial quietly cost you the iris the plain twin had. Nothing in the files,
 * the README or the code ever said a sign gate should not have one, and
 * {@code HorizontalSignDial} always did — which is what made it an oversight rather than a
 * rule. The block was even still there in all three; only the marker was missing.
 */
public class ShippedShapeIrisTest
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

    /** The layer holding this shape's {@code :IA}, or null if no layer declares one. */
    private static StargateShapeLayer irisLayer(final Stargate3DShape shape)
    {
        for (final StargateShapeLayer layer : shape.getShapeLayers())
        {
            if ((layer != null) && (layer.getLayerIrisActivationPosition().length >= 3))
            {
                return layer;
            }
        }
        return null;
    }

    /**
     * The regression itself: a shape may not both withhold {@code :IA} and switch off the
     * fallback that would have covered for it.
     *
     * <p>Stated as the combination rather than as "every shape declares {@code :IA}" because
     * leaving the marker out is legitimate on its own — most shapes could, and the fallback
     * would place the lever in the same spot. It is only fatal together with the flag, and a
     * new sign-dial shape copied from an existing one is exactly how it would come back.
     */
    @Test
    public void noShapeLeavesItsIrisToAFallbackItHasAlsoTurnedOff() throws Exception
    {
        for (final String name : shippedShapeNames())
        {
            final Stargate3DShape shape = load(name);
            if (!shape.isShapeRedstoneActivated())
            {
                continue;
            }
            assertNotNull(irisLayer(shape),
                name + " declares REDSTONE_ACTIVATED=TRUE, which turns off the lever fallback, and"
                    + " marks no :IA -- so a gate built from it can never have an iris at all");
        }
    }

    /**
     * Every shipped shape marks its iris outright, fallback or no fallback.
     *
     * <p>Stricter than the rule above and deliberately so: the shipped shapes are what a server
     * owner copies to write their own, and one that says where its iris goes teaches the right
     * thing. It also means no shipped gate depends on the fallback guessing correctly.
     */
    @Test
    public void everyShippedShapeSaysWhereItsIrisGoes() throws Exception
    {
        for (final String name : shippedShapeNames())
        {
            assertNotNull(irisLayer(load(name)),
                name + " marks no :IA, so its iris is left to setupIrisLever working the position"
                    + " out from the button rather than to the shape saying");
        }
    }

    /**
     * An {@code :IA} has to be on a frame block.
     *
     * <p>The lever hangs on that cell's gate-facing side, so the cell itself is the wall it
     * hangs on. Mark it on an empty cell and the shape parses, the gate builds, and the lever
     * is placed against nothing.
     */
    @Test
    public void everyIrisMarkerIsOnABlockTheLeverCanHangOn() throws Exception
    {
        int checked = 0;
        for (final String name : shippedShapeNames())
        {
            final Stargate3DShape shape = load(name);
            final StargateShapeLayer layer = irisLayer(shape);
            if (layer == null)
            {
                continue;
            }
            checked++;
            final int[] iris = layer.getLayerIrisActivationPosition();
            boolean onFrame = false;
            for (final Integer[] block : layer.getLayerBlockPositions())
            {
                if ((block[1].intValue() == iris[1]) && (block[2].intValue() == iris[2]))
                {
                    onFrame = true;
                    break;
                }
            }
            assertTrue(onFrame,
                name + " marks :IA on a cell it builds nothing in, so the iris lever has no wall"
                    + " to hang on");
        }
        assertTrue(checked > 0, "no :IA markers were found to check, so this proved nothing");
    }
}
