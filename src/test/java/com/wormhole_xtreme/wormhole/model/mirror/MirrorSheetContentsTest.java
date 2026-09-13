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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * The contact sheets in the documentation still show the presets that ship.
 *
 * <p>{@code docs/MIRRORS.md} carries four SVGs drawn from the preset files by
 * {@code scripts/render_mirror_sheets.py}. Pictures of a library are worth having and are also
 * the easiest documentation to let rot: nothing about editing a {@code .mirror} file makes an
 * image change, and nobody reviewing a one-line colour swap thinks to regenerate four SVGs. A
 * gallery that quietly stops matching the plugin is worse than no gallery, because it is
 * believed.
 *
 * <p>So each sheet records, per look, a fingerprint of the preset it drew -- an SVG comment of
 * the form {@code &lt;!-- fp plains 1a2b3c4d5e --&gt;}. This recomputes them. Change a preset
 * and this fails with the name of the look and the one command that fixes it.
 */
class MirrorSheetContentsTest
{
    /** Where the sheets live. */
    private static final Path IMAGES = Paths.get("docs/images");

    /** Where the presets live. */
    private static final Path PRESETS = Paths.get("src/main/resources/shapes/mirror");

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

    /** Every fingerprint the four sheets carry, by look name. */
    private static Map<String, String> drawn() throws IOException
    {
        final Map<String, String> found = new HashMap<>();
        try (Stream<Path> sheets = Files.list(IMAGES))
        {
            for (final Path sheet : sheets.toList())
            {
                if (!sheet.getFileName().toString().startsWith("mirror-looks-"))
                {
                    continue;
                }
                final Matcher m =
                    FINGERPRINT.matcher(Files.readString(sheet, StandardCharsets.UTF_8));
                while (m.find())
                {
                    found.put(m.group(1), m.group(2));
                }
            }
        }
        return found;
    }

    /** Every shipped preset, by name, with the fingerprint it should have. */
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

    /**
     * Every look that ships is on a sheet, and nothing on a sheet has stopped shipping.
     *
     * <p>The first half catches a preset added without a picture. The second catches one
     * deleted or renamed, which leaves a banner in the documentation that an operator cannot
     * stamp -- the quieter of the two, since the page still looks complete.
     */
    @Test
    void everyShippedLookIsOnASheet() throws IOException
    {
        final Map<String, String> shipped = shipped();
        final Map<String, String> drawn = drawn();

        assertTrue(shipped.size() > 50,
            "the presets folder should have the whole library in it, and found "
                + shipped.size());
        assertEquals(shipped.keySet(), drawn.keySet(),
            "the sheets and the shipped presets name different looks" + REGENERATE);
    }

    /**
     * No sheet is showing a look as it used to be.
     *
     * <p>The failure this exists for is not a missing picture -- it is a picture that is still
     * there and no longer true. Changing a base colour or swapping a layer leaves the old banner
     * in the documentation, looking exactly as authoritative as it did the day it was correct.
     */
    @Test
    void noSheetShowsAPresetAsItUsedToBe() throws IOException
    {
        final Map<String, String> shipped = shipped();
        final Map<String, String> drawn = drawn();
        final List<String> stale = new ArrayList<>();

        shipped.forEach((name, fingerprint) ->
        {
            final String onSheet = drawn.get(name);
            if ((onSheet != null) && !onSheet.equals(fingerprint))
            {
                stale.add(name);
            }
        });

        assertEquals(List.of(), stale,
            "these looks have changed since their picture was drawn" + REGENERATE);
    }

    /** The document points at the sheets, which is the only reason they are drawn. */
    @Test
    void theDocumentShowsEverySheet() throws IOException
    {
        final String document = Files.readString(Paths.get("docs/MIRRORS.md"),
            StandardCharsets.UTF_8);

        for (final String sheet : List.of("green", "weather", "elsewhere", "stamp"))
        {
            assertTrue(document.contains("images/mirror-looks-" + sheet + ".svg"),
                "MIRRORS.md should show the " + sheet + " sheet; an image nothing references is"
                    + " weight in the repository and nothing else");
        }
    }
}
