package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * The gallery in the documentation still shows the shapes that ship.
 *
 * <p>{@code docs/GATES.md} carries two drawings per shape -- the gate idle and the same gate
 * dialled -- and beside them the grid it occupies and the markers it needs. All of it comes
 * from {@code scripts/render_gate_sheets.py}, reading the {@code .shape} files and the material
 * groups in {@code config.yml}.
 *
 * <p>It is the same trap {@link com.wormhole_xtreme.wormhole.model.mirror.MirrorGalleryTest}
 * exists for, and worse here. Nothing about editing a {@code .shape} file makes a picture
 * change, and nobody reviewing a one-cell grid edit thinks to regenerate twenty-three SVGs. A
 * gallery that has quietly stopped matching is worse than none because it is believed -- and
 * somebody who builds from a stale drawing does not get a broken gate, they get no gate at all,
 * because detection matches the shape exactly or not at all. They then have a pile of obsidian
 * and a page telling them it should have worked.
 *
 * <p>So each drawing records a fingerprint of the file it came from, as an SVG comment of the
 * form {@code <!-- fp Standard idle 1a2b3c4d5e -->}. This recomputes them, and fails with the
 * name of the shape and the one command that fixes it.
 */
class GateGalleryTest
{
    /** Two drawings per shape, and the palette strip. */
    private static final Path IMAGES = Paths.get("docs/images/gates");

    /** Where the shapes live. */
    private static final Path SHAPES = Paths.get("src/main/resources/shapes/gate");

    /** Where the palettes live. */
    private static final Path CONFIG = Paths.get("src/main/resources/config.yml");

    /** The document the gallery is in. */
    private static final Path DOCUMENT = Paths.get("docs/GATES.md");

    /** How to put it right, said in the failure rather than left to be worked out. */
    private static final String REGENERATE =
        " -- re-run: python scripts/render_gate_sheets.py";

    /** {@code <!-- fp Name state fingerprint -->}, as the renderer writes it. */
    private static final Pattern FINGERPRINT =
        Pattern.compile("<!-- fp ([A-Za-z]+) (idle|dialled) ([0-9a-f]{10}) -->");

    /** {@code <!-- fp palettes fingerprint -->}, which the strip carries instead. */
    private static final Pattern PALETTE_FINGERPRINT =
        Pattern.compile("<!-- fp palettes ([0-9a-f]{10}) -->");

    /**
     * The {@code KEY=} lines that change what a gate looks like or how it animates.
     *
     * <p>Deliberately not every setting. A shape file carries lines the drawing cannot show,
     * and folding those in would mean a regeneration that changes no pixel being demanded for
     * an edit that changed no picture -- which is the fastest way to teach everybody to run the
     * renderer without looking at what it produced.
     */
    private static final Set<String> KEYS = Set.of(
        "WOOSH_TICKS", "LIGHT_TICKS", "REDSTONE_ACTIVATED", "MATERIAL_GROUPS",
        "STARGATE_MATERIAL", "PORTAL_MATERIAL", "IRIS_MATERIAL", "ACTIVE_MATERIAL",
        "CHEVRON_MATERIAL", "SIGN_MATERIAL");

    /** The first ten hex digits of the SHA-1 of these lines, as the renderer takes it. */
    private static String digest(final List<String> body)
    {
        try
        {
            final byte[] bytes = MessageDigest.getInstance("SHA-1")
                .digest(String.join("\n", body).getBytes(StandardCharsets.UTF_8));
            final StringBuilder hex = new StringBuilder();
            for (final byte b : bytes)
            {
                hex.append(String.format("%02x", b));
            }
            return hex.substring(0, 10);
        }
        catch (final NoSuchAlgorithmException impossible)
        {
            throw new IllegalStateException("every JVM has SHA-1", impossible);
        }
    }

    /** The same digest the renderer takes of a shape: its name, its grid, and its settings. */
    private static String fingerprint(final Path shape) throws IOException
    {
        final List<String> body = new ArrayList<>();
        for (final String line : Files.readString(shape, StandardCharsets.UTF_8).split("\r?\n"))
        {
            final String trimmed = line.trim();
            if (trimmed.startsWith("Name="))
            {
                body.add("Name=" + trimmed.substring("Name=".length()).trim());
            }
            else if (trimmed.startsWith("Layer#"))
            {
                body.add("Layer#" + Integer.parseInt(
                    trimmed.substring("Layer#".length()).split("=")[0]) + "=");
            }
            else if (trimmed.startsWith("["))
            {
                body.add(trimmed);
            }
            else if (trimmed.contains("=") && !trimmed.startsWith("#"))
            {
                final String key = trimmed.split("=")[0].trim();
                if (KEYS.contains(key))
                {
                    final String value = trimmed.split("=", 2)[1].trim();
                    body.add(key + "=" + value.replaceAll(";$", "").trim());
                }
            }
        }
        return digest(body);
    }

    /** Every shipped shape, by name, with the fingerprint its drawings should carry. */
    private static Map<String, String> shipped() throws IOException
    {
        final Map<String, String> shapes = new TreeMap<>();
        try (Stream<Path> files = Files.list(SHAPES))
        {
            for (final Path shape : files.toList())
            {
                final String name = shape.getFileName().toString();
                if (name.endsWith(".shape"))
                {
                    shapes.put(name.substring(0, name.length() - ".shape".length()),
                        fingerprint(shape));
                }
            }
        }
        return shapes;
    }

    /** Every gate drawing, keyed {@code Name/state}, with the fingerprint it carries. */
    private static Map<String, String> drawn() throws IOException
    {
        final Map<String, String> found = new TreeMap<>();
        try (Stream<Path> images = Files.list(IMAGES))
        {
            for (final Path image : images.toList())
            {
                final String file = image.getFileName().toString();
                if (!file.endsWith(".svg") || file.equals("palettes.svg"))
                {
                    continue;
                }
                final String svg = Files.readString(image, StandardCharsets.UTF_8);
                final Matcher m = FINGERPRINT.matcher(svg);
                assertTrue(m.find(), file + " carries no fingerprint comment" + REGENERATE);
                assertEquals(m.group(1).toLowerCase(Locale.ROOT) + "-" + m.group(2)
                    + ".svg", file,
                    "a drawing is named for one shape and fingerprinted for another"
                        + REGENERATE);
                found.put(m.group(1) + "/" + m.group(2), m.group(3));
            }
        }
        return found;
    }

    /**
     * Every shape that ships is drawn, idle and dialled, and nothing drawn has stopped
     * shipping.
     *
     * <p>The first half catches a shape added without pictures. The second catches one deleted
     * or renamed, which leaves a gate in the documentation that nobody can build -- the quieter
     * of the two, since the page still looks complete.
     */
    @Test
    void everyShippedShapeIsDrawnIdleAndDialled() throws IOException
    {
        final Map<String, String> shipped = shipped();

        final List<String> expected = new ArrayList<>();
        shipped.keySet().forEach(name ->
        {
            expected.add(name + "/idle");
            expected.add(name + "/dialled");
        });
        expected.sort(null);

        assertEquals(expected, new ArrayList<>(drawn().keySet()),
            "the gallery and the shipped shapes name different gates" + REGENERATE);

        // Checked second, so that adding a shape and forgetting the renderer reports the
        // renderer rather than this.
        assertEquals(11, shipped.size(),
            "docs/GATES.md and docs/guide/GATES.md both say eleven shapes ship; a twelfth needs"
                + " those sentences changed as well as the gallery regenerated. Found "
                + shipped.keySet());
    }

    /**
     * No drawing shows a shape as it used to be.
     *
     * <p>The failure this exists for is not a missing picture -- it is a picture still there and
     * no longer true. Moving one cell of a grid leaves the old gate in the documentation,
     * looking exactly as authoritative as it did the day it was correct, and somebody building
     * from it gets a structure detection will not recognise at all.
     */
    @Test
    void noDrawingShowsAShapeAsItUsedToBe() throws IOException
    {
        final Map<String, String> drawn = drawn();
        final List<String> stale = new ArrayList<>();

        shipped().forEach((name, fingerprint) ->
        {
            for (final String state : List.of("idle", "dialled"))
            {
                final String onPage = drawn.get(name + "/" + state);
                if ((onPage != null) && !onPage.equals(fingerprint))
                {
                    stale.add(name + " (" + state + ")");
                }
            }
        });

        assertEquals(List.of(), stale,
            "these shapes have changed since their pictures were drawn" + REGENERATE);
    }

    /**
     * The palette strip still shows the palettes that ship.
     *
     * <p>This one rots from a different file. The drawings come from {@code .shape} files, but
     * the colours in all of them -- and the whole of the strip under "Palettes are separate from
     * shapes" -- come from {@code config.yml}. Renaming a group or swapping its light block
     * changes nothing in {@code shapes/gate}, so nothing else here would notice.
     */
    @Test
    void thePaletteStripStillShowsTheShippedGroups() throws IOException
    {
        final List<String> body = new ArrayList<>();
        for (final String line : Files.readString(CONFIG, StandardCharsets.UTF_8).split("\r?\n"))
        {
            if (line.startsWith("gate-material-groups:")
                || (line.startsWith("  ") && !line.isBlank() && !line.trim().startsWith("#")))
            {
                body.add(line);
            }
        }

        final String svg = Files.readString(IMAGES.resolve("palettes.svg"),
            StandardCharsets.UTF_8);
        final Matcher m = PALETTE_FINGERPRINT.matcher(svg);
        assertTrue(m.find(), "palettes.svg carries no fingerprint comment" + REGENERATE);
        assertEquals(digest(body), m.group(1),
            "the palettes have changed since the strip was drawn" + REGENERATE);
    }

    /**
     * The document shows every shape, both its states, and what it takes to build.
     *
     * <p>The drawings are an approximation -- flat colour, one plane of a solid object -- and a
     * reader who only had those would still not know which layer the DHD is in. The columns
     * beside them are the half that can be checked against a shape file, and the half somebody
     * actually builds from.
     */
    @Test
    void theDocumentShowsEveryShapeAndWhatItTakesToBuild() throws IOException
    {
        final String document = Files.readString(DOCUMENT, StandardCharsets.UTF_8);
        final int start = document.indexOf("<!-- shapes:start -->");
        final int end = document.indexOf("<!-- shapes:end -->");
        assertTrue((start > 0) && (end > start), "the gallery markers should still be in "
            + DOCUMENT.getFileName() + "; the renderer writes between them");
        final String gallery = document.substring(start, end);

        final List<String> missing = new ArrayList<>();
        for (final String shape : shipped().keySet())
        {
            final String file = shape.toLowerCase(Locale.ROOT);
            // The anchors, not just the images: a 23-wide gate shown at 104 pixels is four
            // pixels to the block, and the link to the full-size file is the only way the page
            // offers to see one properly -- GitHub's sanitiser allows no stylesheet or script
            // to do it with.
            if (!gallery.contains("<a href=\"images/gates/" + file + "-idle.svg\">")
                || !gallery.contains("<a href=\"images/gates/" + file + "-dialled.svg\">")
                || !gallery.contains("`" + shape + "`"))
            {
                missing.add(shape);
            }
        }

        assertEquals(List.of(), missing,
            "these shapes have drawings but no row in the gallery" + REGENERATE);
        assertTrue(gallery.contains("| Markers |"),
            "the gallery should list each shape's markers, because the elevation hides the ones"
                + " behind the frame and a builder needs all of them");
        assertTrue(gallery.contains("(layer "),
            "each marker should say which layer it is in, which is the part a flattened drawing"
                + " cannot show");
    }
}
