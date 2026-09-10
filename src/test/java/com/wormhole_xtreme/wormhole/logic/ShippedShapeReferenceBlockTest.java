package com.wormhole_xtreme.wormhole.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * The marker reference at the top of every shipped shape says the same thing in every file.
 *
 * <p>That block -- what {@code [S]}, {@code [P]}, {@code :A}, {@code :IA} and the rest mean --
 * is copied into all eleven shapes rather than living anywhere central, and it is what an
 * admin authoring a custom shape actually reads, because it is right there in the file they
 * opened to copy from.
 *
 * <p>Eleven hand-maintained copies is eleven chances to drift, and two of them had. The
 * {@code [C]} chevron marker was documented in {@code Standard.shape} alone, so ten of the
 * eleven files somebody might copy from did not mention the marker exists. And
 * {@code StandardSignDial.shape} told the reader to use {@code MinimalSignDialRedstone}, a
 * shape retired two releases earlier.
 *
 * <p>Fixing those two by hand would leave eleven copies free to drift again, which is the
 * actual defect. These tests are the thing that stops it.
 */
class ShippedShapeReferenceBlockTest
{
    /** Where the shipped shapes live in the source tree. */
    private static final Path SHAPE_DIR = Paths.get("src/main/resources/shapes/gate");

    /** How many shapes ship. A glob that silently matched nothing would pass every test below. */
    private static final int SHIPPED_COUNT = 11;

    /** A marker definition line, e.g. "#    [S] = Stargate Material" or "#    :A = ...". */
    private static final Pattern MARKER_DEFINITION =
        Pattern.compile("^#\\s+(\\[[A-Z]\\]|:[A-Z]{1,2})\\s*=", Pattern.MULTILINE);

    /**
     * A word that looks like one of this project's shape names.
     *
     * <p>Anchored on the seven shape families rather than on capitalisation in general, so an
     * ordinary capitalised word in prose is not mistaken for a shape reference.
     */
    private static final Pattern SHAPE_REFERENCE = Pattern.compile(
        "\\b(?:Standard|Minimal|Even|Horizontal|Large|Grand|Massive)[A-Za-z]*\\b");

    private static List<Path> shippedShapes() throws IOException
    {
        try (Stream<Path> files = Files.list(SHAPE_DIR))
        {
            // toList() rather than collect(toList()): nothing here modifies the result, and
            // an unmodifiable one says so.
            final List<Path> shapes = files
                .filter(p -> p.getFileName().toString().endsWith(".shape"))
                .sorted()
                .toList();
            assertEquals(SHIPPED_COUNT, shapes.size(),
                "expected " + SHIPPED_COUNT + " shipped shapes; a wrong directory or a changed "
                + "extension would leave these tests asserting nothing at all. Found: " + shapes);
            return shapes;
        }
    }

    private static String read(final Path path) throws IOException
    {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Set<String> markersIn(final String text)
    {
        final Set<String> markers = new TreeSet<>();
        final Matcher m = MARKER_DEFINITION.matcher(text);
        while (m.find())
        {
            markers.add(m.group(1));
        }
        return markers;
    }

    /**
     * Every shipped shape documents exactly the same set of markers.
     *
     * <p>The failure that prompted this: {@code [C]} was defined in one file and absent from
     * ten. A shape author copying any of those ten had no way to learn the marker exists,
     * which for a documented-only feature means it may as well not.
     */
    @Test
    void everyShippedShapeDocumentsTheSameMarkers() throws IOException
    {
        final List<Path> shapes = shippedShapes();
        final Map<String, Set<String>> byFile = new TreeMap<>();
        for (final Path shape : shapes)
        {
            byFile.put(shape.getFileName().toString(), markersIn(read(shape)));
        }

        final Set<String> reference = byFile.get("Standard.shape");
        assertTrue((reference != null) && !reference.isEmpty(),
            "Standard.shape should define the marker reference; found " + reference);

        final List<String> drifted = new ArrayList<>();
        for (final Map.Entry<String, Set<String>> entry : byFile.entrySet())
        {
            if (!reference.equals(entry.getValue()))
            {
                final Set<String> missing = new LinkedHashSet<>(reference);
                missing.removeAll(entry.getValue());
                final Set<String> extra = new LinkedHashSet<>(entry.getValue());
                extra.removeAll(reference);
                drifted.add(entry.getKey() + " missing=" + missing + " extra=" + extra);
            }
        }
        assertTrue(drifted.isEmpty(),
            "the reference block has drifted apart; each of these differs from Standard.shape: "
            + drifted);
    }

    /**
     * No shipped shape points the reader at a shape that is not shipped.
     *
     * <p>{@code StandardSignDial.shape} told people to use {@code MinimalSignDialRedstone} for
     * redstone target cycling. That shape was retired when every sign-dial gate gained redstone,
     * so the advice named a file that does not exist and described a step nobody needs. Advice
     * in a reference block is worth less than nothing when it sends someone looking for
     * something that is not there.
     */
    @Test
    void noShippedShapePointsAtAShapeThatIsNotShipped() throws IOException
    {
        final List<Path> shapes = shippedShapes();
        final Set<String> shipped = new TreeSet<>();
        for (final Path shape : shapes)
        {
            final String name = shape.getFileName().toString();
            shipped.add(name.substring(0, name.length() - ".shape".length()));
        }

        final List<String> dangling = new ArrayList<>();
        for (final Path shape : shapes)
        {
            final Matcher m = SHAPE_REFERENCE.matcher(read(shape));
            while (m.find())
            {
                if (!shipped.contains(m.group()))
                {
                    dangling.add(shape.getFileName() + " -> " + m.group());
                }
            }
        }
        assertTrue(dangling.isEmpty(),
            "these name a shape that is not shipped: " + dangling + "; shipped are " + shipped);
    }

    /** The chevron marker in particular, since it is the one that had drifted. */
    @Test
    void everyShippedShapeExplainsTheChevronMarker() throws IOException
    {
        final List<String> silent = new ArrayList<>();
        for (final Path shape : shippedShapes())
        {
            final String text = read(shape).toLowerCase(Locale.ROOT);
            if (!text.contains("[c] = chevron material"))
            {
                silent.add(shape.getFileName().toString());
            }
        }
        assertTrue(silent.isEmpty(),
            "a shape author copying one of these would not learn [C] exists: " + silent);
    }
}
