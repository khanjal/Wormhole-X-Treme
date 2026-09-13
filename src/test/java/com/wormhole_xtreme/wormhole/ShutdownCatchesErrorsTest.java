package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Nothing in the shutdown path catches {@code Exception} alone.
 *
 * <p>Reported from a live server: {@code NoClassDefFoundError:
 * com/wormhole_xtreme/wormhole/model/mirror/MirrorPackets} thrown out of {@code onDisable},
 * from the call that gives proximity mirrors their look back. The operator had copied a new jar
 * over the running server before restarting it, which is what most operators do -- the plugin
 * classloader goes on reading the file it opened at startup, and any class it had not needed
 * yet is simply gone by the time the server stops. {@code MirrorPackets} loads only when a
 * proximity mirror actually hides or reveals, so whether it was already in memory came down to
 * whether anybody had walked past one that session. Hence "occasionally".
 *
 * <p>What made it worth a test rather than a shrug: {@code NoClassDefFoundError} is an
 * {@link Error}, the restore was wrapped in {@code catch (Exception)}, and so the throw left
 * {@code onDisable} at its first statement. Everything below it was skipped -- the
 * configuration, every gate, the rings, the beam destinations and the mirrors, none of them
 * written. A tidy-up nobody would miss quietly cost the save that everybody would.
 *
 * <p>So the rule is: in this file, past the point where shutting down has begun, a catch reaches
 * past {@code Exception}. Written as a source scan because the thing being guarded is a shape
 * the compiler is perfectly happy with, and because the next person to add a shutdown step will
 * copy the line above it.
 */
class ShutdownCatchesErrorsTest
{
    /**
     * Where the rule applies: {@code onDisable} and the helpers it calls, which sit together
     * between it and {@code onEnable}.
     *
     * <p>Bounded by two markers rather than by parsing Java, and not extended to the end of the
     * file: the startup methods below {@code onEnable} catch {@code Exception} on purpose and
     * are not what this is about. A startup failure happens while the jar is still whole.
     *
     * <p>The region is checked for the steps it should contain before anything is concluded
     * from it. Two markers and a substring are a fragile way to point at code, and the failure
     * this guards against is the quiet one -- somebody moves a method, the region shrinks to
     * nothing, and a test that scans nothing passes forever.
     */
    private static final String FROM = "public void onDisable()";

    /** @see #FROM */
    private static final String TO = "public void onEnable()";

    /** Every step the shutdown path is known to take, used to prove the region is the region. */
    private static final List<String> SHUTDOWN_STEPS =
        List.of("MirrorProximity.restoreAll", "saveRings", "saveBeams", "saveMirrors",
            "disableEconomyQuietly");

    /** The shutdown path's source, between the two markers. */
    private static String shutdownPath() throws IOException
    {
        final Path source =
            Paths.get("src/main/java/com/wormhole_xtreme/wormhole/WormholeXTreme.java");
        final String text = Files.readString(source, StandardCharsets.UTF_8);
        final int from = text.indexOf(FROM);
        final int to = text.indexOf(TO);
        assertTrue(from > 0, "onDisable should still be in " + source.getFileName());
        assertTrue(to > from, "onEnable should still follow onDisable; if the file was"
            + " reordered, this test's idea of the shutdown path has to be reordered with it");
        return text.substring(from, to);
    }

    @Test
    void everyCatchInTheShutdownPathReachesPastException() throws IOException
    {
        final String path = shutdownPath();
        for (final String step : SHUTDOWN_STEPS)
        {
            assertTrue(path.contains(step), "the scanned region should contain " + step
                + "; without it this test is looking at the wrong part of the file");
        }

        // Deliberately not trying to parse Java. Every catch in this file is written on one
        // line, and a clause that names Exception without also naming LinkageError is the
        // shape being refused.
        final Pattern narrow = Pattern.compile("catch \\(final Exception (?!\\| LinkageError)");
        final Matcher m = narrow.matcher(path);
        final List<String> found = new ArrayList<>();
        while (m.find())
        {
            found.add(m.group());
        }

        assertEquals(List.of(), found,
            "a catch in the shutdown path names Exception but not LinkageError. An Error thrown"
                + " while shutting down -- a class the classloader can no longer read, because"
                + " the jar was replaced under a running server -- would escape it and skip"
                + " every save below it.");
    }

    /**
     * The scan actually looked at the shutdown path.
     *
     * <p>Without this the test above passes just as happily against an empty string, a renamed
     * method, or a regex that never matches anything -- which is the failure mode every
     * source-scanning test has, and the reason this project writes the count assertion out.
     */
    @Test
    void theScanReadsARealShutdownPathWithRealCatchesInIt() throws IOException
    {
        final String path = shutdownPath();

        final Matcher wide = Pattern.compile("catch \\(final Exception \\| LinkageError")
            .matcher(path);
        int widened = 0;
        while (wide.find())
        {
            widened++;
        }

        assertTrue(path.length() > 500, "the shutdown path should be more than a few lines");
        assertTrue(widened >= 5, "the shutdown path should have several widened catches in it,"
            + " and found " + widened + " -- if this drops, either they were narrowed again or"
            + " this test is scanning the wrong part of the file");
    }
}
