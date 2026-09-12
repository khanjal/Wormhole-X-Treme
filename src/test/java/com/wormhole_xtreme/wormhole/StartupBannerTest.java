package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The startup banner draws the same shape on every console, or gives up and draws letters.
 *
 * <p>The banner used to mix two East-Asian-Width classes. {@code U+2580} UPPER HALF BLOCK,
 * {@code U+2584} LOWER HALF BLOCK and {@code U+258C} LEFT HALF BLOCK are Ambiguous; {@code
 * U+2590} RIGHT HALF BLOCK and {@code U+2591} LIGHT SHADE are Narrow. A terminal set to render
 * Ambiguous as wide -- a standard toggle in PuTTY, Windows Terminal, konsole, iTerm2 and tmux,
 * and the default under a CJK locale -- doubled the first three and left the other two alone.
 * The arcs stretched from six columns to ten while the middle row went from seven to eight, so
 * the ring tore open and the two labels stopped lining up with each other. Same jar, same
 * server, different terminal setting, which is why it only ever happened to some people.
 *
 * <p>Nothing in {@code java.lang.Character} exposes East-Asian width, so the first test below
 * carries the classification as data. That is the honest form: the table is the fact being
 * asserted, and it came from the Unicode 15.1 EastAsianWidth file rather than from looking at
 * the glyphs and guessing.
 */
class StartupBannerTest
{
    /**
     * Block-element glyphs whose East-Asian width is Ambiguous, from Unicode 15.1.
     *
     * <p>Deliberately not the whole block. These are the ones worth drawing with, and a glyph
     * that is not on this list is either Narrow -- the bug -- or was never checked. Either way
     * the test should stop rather than wave it through, so anything absent counts as a
     * failure and whoever adds it looks its width up first.
     */
    private static final String AMBIGUOUS_BLOCK_GLYPHS = "▀▄█▌▒▓";

    /** The two Narrow glyphs the banner used to be drawn with, kept here to name them. */
    private static final String NARROW_BLOCK_GLYPHS = "▐░";

    private static String[] blockRing() throws ReflectiveOperationException
    {
        return PrivateStatics.of(WormholeXTreme.class, "RING_BLOCKS");
    }

    private static String[] asciiRing() throws ReflectiveOperationException
    {
        return PrivateStatics.of(WormholeXTreme.class, "RING_ASCII");
    }

    /**
     * Every glyph in the ring stretches the same way, so a wide terminal gets a fat ring.
     *
     * <p>This is the test that would have caught the original bug. It fails on {@code U+2590}
     * or {@code U+2591} because those are Narrow, which is exactly what the banner shipped
     * with -- the drawing looked right on the machine it was written on and sheared on the
     * ones with the Ambiguous-is-wide setting turned on.
     */
    @Test
    void everyGlyphInTheRingSharesOneWidthClass() throws ReflectiveOperationException
    {
        final List<String> offenders = new ArrayList<>();
        for (final String line : blockRing())
        {
            for (final char glyph : line.toCharArray())
            {
                if ((glyph != ' ') && (AMBIGUOUS_BLOCK_GLYPHS.indexOf(glyph) < 0))
                {
                    offenders.add(String.format("U+%04X", (int) glyph)
                        + (NARROW_BLOCK_GLYPHS.indexOf(glyph) >= 0 ? " (Narrow)" : " (unclassified)"));
                }
            }
        }

        assertTrue(offenders.isEmpty(),
            "these glyphs do not share the Ambiguous width class the rest of the ring has, so a "
                + "terminal rendering Ambiguous as wide will stretch some rows and not others and "
                + "tear the ring apart: " + offenders + ". Look the glyph up in the Unicode "
                + "EastAsianWidth file before drawing with it.");
    }

    /** The ring is drawn at all -- a test over an empty drawing would prove nothing. */
    @Test
    void theRingIsThreeLinesWithInkOnEachOfThem() throws ReflectiveOperationException
    {
        for (final String[] ring : Arrays.asList(blockRing(), asciiRing()))
        {
            assertEquals(3, ring.length, "the banner is three drawn lines");
            for (final String line : ring)
            {
                assertTrue(line.trim().length() >= 3, "this line of the ring is nearly empty: '" + line + "'");
            }
        }
    }

    /** A console that can take the block glyphs gets them. */
    @Test
    void aUtf8ConsoleGetsTheBlockRing() throws ReflectiveOperationException
    {
        final String[] lines = WormholeXTreme.bannerLines(StandardCharsets.UTF_8, "1.6.0", "Paper");

        assertEquals(blockRing()[0], lines[0], "a UTF-8 console should get the drawn ring");
        assertTrue(lines[1].contains("Wormhole X-Treme v1.6.0"), "the version label is on the middle line");
        assertTrue(lines[2].contains("Running on Paper"), "the host label is on the bottom line");
    }

    /**
     * A console whose charset has no block glyphs gets letters instead of question marks.
     *
     * <p>CP1252 is the case that matters: none of the block glyphs exist in it, and a server
     * piped through a hosting panel lands there often. Before the fallback existed every one
     * of those consoles printed a row of {@code ?}.
     */
    @Test
    void aConsoleThatCannotTakeTheBlocksGetsTheAsciiRing() throws ReflectiveOperationException
    {
        assumeTrue(Charset.isSupported("windows-1252"), "no CP1252 on this JDK");
        final Charset cp1252 = Charset.forName("windows-1252");

        final String[] lines = WormholeXTreme.bannerLines(cp1252, "1.6.0", "Paper");

        assertEquals(asciiRing()[0], lines[0], "a CP1252 console should fall back to the ASCII ring");
        assertTrue(cp1252.newEncoder().canEncode(String.join("\n", lines)),
            "the fallback must survive the charset that triggered it, or it has changed nothing");
    }

    /**
     * The block ring still fits CP437, which is what a plain Windows console is.
     *
     * <p>All six candidates are in CP437, so the fallback should not fire there. A future
     * glyph that is Ambiguous but outside CP437 -- the quadrant blocks are the tempting ones
     * -- would quietly cost every cmd.exe user the drawing, and this is what says so.
     */
    @Test
    void theBlockRingStillFitsAPlainWindowsConsole() throws ReflectiveOperationException
    {
        assumeTrue(Charset.isSupported("IBM437"), "no CP437 on this JDK");

        final String[] lines = WormholeXTreme.bannerLines(Charset.forName("IBM437"), "1.6.0", "Paper");

        assertEquals(blockRing()[0], lines[0], "CP437 holds every glyph in the ring, so it should get it");
    }

    /**
     * Both labels start in the same column, whichever ring is drawn.
     *
     * <p>The two drawings are different widths, so the padding has to be computed rather than
     * typed. A version and a host that do not line up is the same visual defect the width bug
     * produced, arrived at from the other direction.
     */
    @Test
    void bothLabelsStartInTheSameColumnInEitherRing()
    {
        for (final Charset charset : Arrays.asList(StandardCharsets.UTF_8, StandardCharsets.US_ASCII))
        {
            final String[] lines = WormholeXTreme.bannerLines(charset, "1.6.0", "Paper");
            final int version = lines[1].indexOf("Wormhole");
            final int host = lines[2].indexOf("Running");

            assertTrue(version > 0, "the version label should be indented past the ring, not at column 0");
            assertEquals(version, host,
                "the version and host labels start in different columns, so the banner reads as "
                    + "ragged: '" + lines[1] + "' against '" + lines[2] + "'");
        }
    }

    /** The version and host given are the ones printed, rather than anything baked in. */
    @Test
    void theBannerPrintsTheVersionAndHostItIsGiven()
    {
        final String[] lines = WormholeXTreme.bannerLines(StandardCharsets.UTF_8, "9.9.9-SNAPSHOT", "Purpur");

        assertTrue(lines[1].endsWith("Wormhole X-Treme v9.9.9-SNAPSHOT"), "got: " + lines[1]);
        assertTrue(lines[2].endsWith("Running on Purpur"), "got: " + lines[2]);
    }

    /** An ASCII console gets the ASCII ring, which is the point of having one. */
    @Test
    void theFallbackRingIsPlainAscii() throws ReflectiveOperationException
    {
        final String[] lines = WormholeXTreme.bannerLines(StandardCharsets.US_ASCII, "1.6.0", "Paper");

        assertArrayEquals(asciiRing(), new String[] { lines[0], lines[1].substring(0, asciiRing()[1].length()),
            lines[2].substring(0, asciiRing()[2].length()) },
            "an ASCII console should get the ASCII ring verbatim");
        assertTrue(StandardCharsets.US_ASCII.newEncoder().canEncode(String.join("\n", lines)),
            "the ASCII fallback has something non-ASCII in it, which defeats its only purpose");
    }
}
