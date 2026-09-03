package com.wormhole_xtreme.wormhole.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Where a shape puts its arrivals, relative to its portal.
 *
 * <p>Every way into a gate lands somewhere: players and loose entities at the player exit
 * offset a block outwards, minecarts at the minecart exit. Whether that lands inside the
 * portal decides what a gate may put in its portal blocks while a wormhole is up. Air is
 * always safe. Anything solid is only safe for a shape whose arrivals land clear of it.
 *
 * <p>Upright shapes all land clear: the player exit sits on a frame block and is offset a
 * block outwards, and the minecart exit sits on an ignored block behind the portal plane.
 * The horizontal shapes are the exception, and deliberately so -- a gate you fall through
 * puts you in the plane you fell through, because there is no outwards for it to offset you
 * to.
 *
 * <p>Written down here because it is a property of the shipped shape <em>files</em> rather
 * than of any code, so nothing else would notice a shape that changed sides -- and a new
 * shape is exactly the kind of thing somebody adds without knowing this rule exists.
 */
public class ArrivalIsOutsideThePortalTest
{

    private static List<Path> shippedShapes() throws IOException
    {
        final List<Path> found = new ArrayList<Path>();
        final Path dir = Paths.get("src/main/resources/GateShapes");
        if (Files.isDirectory(dir))
        {
            try (java.util.stream.Stream<Path> listing = Files.list(dir))
            {
                for (final Path p : listing.toList())
                {
                    if (p.getFileName().toString().endsWith(".shape"))
                    {
                        found.add(p);
                    }
                }
            }
        }
        return found;
    }

    /**
     * Shapes whose arrivals land inside their own portal, and may therefore never have
     * anything solid put in it.
     *
     * <p>A gate you fall through has no outwards to be offset to, so this is a property of
     * lying flat rather than an oversight.
     */
    private static final List<String> LANDS_INSIDE =
        java.util.Arrays.asList("Horizontal.shape", "HorizontalSignDial.shape");

    @Test
    public void onlyTheHorizontalShapesLandArrivalsInsideTheirPortal() throws IOException
    {
        final List<Path> shapes = shippedShapes();
        assertFalse(shapes.isEmpty(), "no shape files were read, so this proved nothing");

        final List<String> inside = new ArrayList<String>();
        int exitsChecked = 0;
        for (final Path shape : shapes)
        {
            for (final String line : Files.readAllLines(shape))
            {
                if (line.trim().startsWith("#"))
                {
                    // The legend at the top of every shape file writes out :EP and :EM to
                    // explain them, and is not a layer.
                    continue;
                }
                // Scanned by hand rather than by regex: a cell is just the text between one
                // bracket and the next, and the type is its first character.
                int open = line.indexOf('[');
                while (open >= 0)
                {
                    final int close = line.indexOf(']', open);
                    if (close < 0)
                    {
                        break;
                    }
                    final String cell = line.substring(open + 1, close);
                    if (cell.contains(":EP") || cell.contains(":EM"))
                    {
                        exitsChecked++;
                        if (cell.startsWith("P"))
                        {
                            inside.add(shape.getFileName().toString());
                        }
                    }
                    open = line.indexOf('[', close);
                }
            }
        }

        assertTrue(exitsChecked > 0, "no exit markers were found, so this proved nothing");
        java.util.Collections.sort(inside);
        final List<String> expected = new ArrayList<String>(LANDS_INSIDE);
        java.util.Collections.sort(expected);
        assertEquals(expected, inside,
            "a shape changed which side of its portal it lands arrivals on. Anything listed "
                + "here can only ever have air in its portal blocks; anything not listed can "
                + "be given something solid while a wormhole is up.");
    }
}
