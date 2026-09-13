package com.wormhole_xtreme.wormhole.model.mirror;

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
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * The gallery in the documentation still shows the presets that ship.
 *
 * <p>{@code docs/MIRRORS.md} carries one drawing per look and, beside each, the layers the
 * plugin actually applies. Both come from {@code scripts/render_mirror_sheets.py}, reading the
 * preset files. The gallery exists to be held up against a stamped banner in game -- which is
 * only worth anything while it matches what the plugin ships.
 *
 * <p>It is also the easiest documentation to let rot. Nothing about editing a {@code .mirror}
 * file makes an image change, and nobody reviewing a one-line colour swap thinks to regenerate
 * eighty-eight SVGs. A gallery that has quietly stopped matching is worse than none, because it
 * is believed -- and worse still here, where somebody comparing it to a banner would conclude
 * the plugin was wrong.
 *
 * <p>So each drawing records a fingerprint of the preset it came from, as an SVG comment of the
 * form {@code &lt;!-- fp plains 1a2b3c4d5e --&gt;}. This recomputes them, and fails with the
 * name of the look and the one command that fixes it.
 */
class MirrorGalleryTest
{
    /** One drawing per look. */
    private static final Path IMAGES = Paths.get("docs/images/mirrors");

    /** Where the presets live. */
    private static final Path PRESETS = Paths.get("src/main/resources/shapes/mirror");

    /** The document the gallery is in. */
    private static final Path DOCUMENT = Paths.get("docs/MIRRORS.md");

    /** How to put it right, said in the failure rather than left to be worked out. */
    private static final String REGENERATE =
        " -- re-run: python scripts/render_mirror_sheets.py";

    /** {@code <!-- fp name fingerprint -->}, as the renderer writes it. */
    private static final Pattern FINGERPRINT =
        Pattern.compile("<!-- fp ([a-z_]+) ([0-9a-f]{10}) -->");

    /** The same digest the renderer takes: the lines that decide what a banner looks like. */
    private static String fingerprint(final Path preset) throws IOException
    {
        final List<String> body = new ArrayList<>();
        for (final String line : Files.readString(preset, StandardCharsets.UTF_8).split("\r?\n"))
        {
            final String trimmed = line.trim();
            if (trimmed.startsWith("Base=") || trimmed.startsWith("Layer=")
                || trimmed.startsWith("Biome=") || trimmed.startsWith("Sheltered="))
            {
                body.add(trimmed);
            }
        }
        try
        {
            final byte[] digest = MessageDigest.getInstance("SHA-1")
                .digest(String.join("\n", body).getBytes(StandardCharsets.UTF_8));
            final StringBuilder hex = new StringBuilder();
            for (final byte b : digest)
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

    /** Every shipped preset, by name, with the fingerprint its drawing should carry. */
    private static Map<String, String> shipped() throws IOException
    {
        final Map<String, String> presets = new TreeMap<>();
        try (Stream<Path> files = Files.list(PRESETS))
        {
            for (final Path preset : files.toList())
            {
                final String name = preset.getFileName().toString();
                if (name.endsWith(".mirror"))
                {
                    presets.put(name.substring(0, name.length() - ".mirror".length()),
                        fingerprint(preset));
                }
            }
        }
        return presets;
    }

    /** Every drawing, by the look it names, with the fingerprint it carries. */
    private static Map<String, String> drawn() throws IOException
    {
        final Map<String, String> found = new TreeMap<>();
        try (Stream<Path> images = Files.list(IMAGES))
        {
            for (final Path image : images.toList())
            {
                final String file = image.getFileName().toString();
                if (!file.endsWith(".svg"))
                {
                    continue;
                }
                final String svg = Files.readString(image, StandardCharsets.UTF_8);
                final Matcher m = FINGERPRINT.matcher(svg);
                assertTrue(m.find(), file + " carries no fingerprint comment" + REGENERATE);
                assertEquals(file.substring(0, file.length() - ".svg".length()), m.group(1),
                    "a drawing is named for one look and fingerprinted for another"
                        + REGENERATE);
                found.put(m.group(1), m.group(2));
            }
        }
        return found;
    }

    /**
     * Every look that ships has a drawing, and nothing drawn has stopped shipping.
     *
     * <p>The first half catches a preset added without a picture. The second catches one
     * deleted or renamed, which leaves a banner in the documentation that nobody can stamp --
     * the quieter of the two, since the page still looks complete.
     */
    @Test
    void everyShippedLookIsDrawn() throws IOException
    {
        final Map<String, String> shipped = shipped();

        assertTrue(shipped.size() > 50,
            "the presets folder should hold the whole library, and found " + shipped.size());
        assertEquals(shipped.keySet(), drawn().keySet(),
            "the gallery and the shipped presets name different looks" + REGENERATE);
    }

    /**
     * No drawing shows a look as it used to be.
     *
     * <p>The failure this exists for is not a missing picture -- it is a picture still there and
     * no longer true. Changing a base colour or swapping a layer leaves the old banner in the
     * documentation, looking exactly as authoritative as it did the day it was correct, and
     * anybody comparing it against a freshly stamped banner would believe the plugin had got it
     * wrong.
     */
    @Test
    void noDrawingShowsAPresetAsItUsedToBe() throws IOException
    {
        final Map<String, String> drawn = drawn();
        final List<String> stale = new ArrayList<>();

        shipped().forEach((name, fingerprint) ->
        {
            final String onPage = drawn.get(name);
            if ((onPage != null) && !onPage.equals(fingerprint))
            {
                stale.add(name);
            }
        });

        assertEquals(List.of(), stale,
            "these looks have changed since their picture was drawn" + REGENERATE);
    }

    /**
     * The document shows every look, and says what each one is made of.
     *
     * <p>The recipe beside each drawing is the half that can be checked against a banner in
     * game. The drawings are approximations of the patterns and a mismatch there proves
     * nothing; a mismatch in the layer list is a real disagreement between the page and the
     * plugin.
     */
    @Test
    void theDocumentShowsEveryLookAndItsLayers() throws IOException
    {
        final String document = Files.readString(DOCUMENT, StandardCharsets.UTF_8);
        final int start = document.indexOf("<!-- gallery:start -->");
        final int end = document.indexOf("<!-- gallery:end -->");
        assertTrue((start > 0) && (end > start), "the gallery markers should still be in "
            + DOCUMENT.getFileName() + "; the renderer writes between them");
        final String gallery = document.substring(start, end);

        final List<String> missing = new ArrayList<>();
        for (final String look : shipped().keySet())
        {
            // The anchor, not just the image: a drawing shown at 26 pixels is unreadable,
            // and the link to the full-size file is the only way the page offers to see one
            // properly -- GitHub's sanitiser allows no stylesheet or script to do it with.
            if (!gallery.contains("<a href=\"images/mirrors/" + look + ".svg\" title=")
                || !gallery.contains("images/mirrors/" + look + ".svg\" width=\"26\"")
                || !gallery.contains("`" + look + "`"))
            {
                missing.add(look);
            }
        }

        assertEquals(List.of(), missing,
            "these looks have a drawing but no row in the gallery" + REGENERATE);
        assertTrue(gallery.contains("Layers, in order"),
            "each table should carry the layer recipe, which is the column somebody comparing"
                + " a stamped banner against this page actually needs");
        assertTrue(gallery.contains("base +"),
            "the recipes should name a base colour and the layers over it");
    }
}
