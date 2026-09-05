package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Logging goes through the two-argument {@code prettyLog}, and the tag it builds is right.
 *
 * <p>{@code prettyLog(Level, boolean, String)} was the only form there was, and the boolean
 * says whether to put the plugin version in the tag. Almost nothing wants that: 268 of the 277
 * calls in the plugin passed a literal {@code false}, and the nine that passed {@code true} are
 * all startup and shutdown lines in {@link WormholeXTreme} itself. So the overwhelmingly common
 * call carried a bare boolean literal that told a reader nothing and sat directly beside the
 * argument that mattered.
 *
 * <p>The two-argument overload is now the normal form. These tests cover the two ways that
 * change could go wrong.
 */
public class PrettyLogTest
{
    /**
     * The short form logs the same line as the long form told not to include the version.
     *
     * <p>This is not a formality. The mechanical rewrite that dropped {@code false} from 268
     * call sites also rewrote the delegation inside the new overload, turning it into
     * {@code prettyLog(severity, message)} calling itself -- infinite recursion on the first
     * line the plugin ever logged.
     *
     * <p>Nothing caught it. It compiled, and the whole suite passed, because every test mocks
     * {@link WormholeXTreme} and a mocked method has no body to recurse in. It would have
     * thrown {@code StackOverflowError} on a real server at startup. This test calls the real
     * two-argument body on a mock so the delegation actually runs, which is the only way the
     * fault is visible without a server.
     */
    @Test
    public void theShortFormDelegatesWithTheVersionTurnedOff()
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        doCallRealMethod().when(plugin).prettyLog(any(Level.class), anyString());

        plugin.prettyLog(Level.WARNING, "a gate went missing");

        verify(plugin).prettyLog(Level.WARNING, false, "a gate went missing");
    }

    /**
     * The tag is the plugin name in brackets, and the version only when asked for.
     *
     * <p>Pinned because the version lookup moved: it used to be read on every call and thrown
     * away 268 times out of 277, and now happens only in the branch that uses it. The visible
     * output has to be unchanged by that.
     */
    @Test
    public void theTagCarriesTheNameAndTheVersionOnlyWhenAskedFor()
    {
        assertEquals("[WormholeXTreme]", WormholeXTreme.prettyTag("WormholeXTreme", null),
            "an ordinary line is tagged with the plugin name alone");
        assertEquals("[WormholeXTreme][v1.5.0]", WormholeXTreme.prettyTag("WormholeXTreme", "1.5.0"),
            "a startup line adds the version, in its own brackets after the name");
    }

    /**
     * Nobody reintroduces the redundant {@code false}.
     *
     * <p>The three-argument form stays public and is still right for the nine lifecycle lines
     * that want the version. What should not come back is passing it {@code false}, which is
     * the two-argument call written the long way.
     *
     * <p>One match is expected and required: the delegation inside the overload itself, in
     * {@link WormholeXTreme}. Anything else is a call site that should have been shortened.
     */
    @Test
    public void noCallSitePassesFalseForTheVersion() throws IOException
    {
        // [^,] stops the first group at the level argument, and DOTALL lets it span lines,
        // since some of these calls wrap. No first argument in the tree contains a comma.
        final Pattern redundant = Pattern.compile("prettyLog\\([^,]*,\\s*false\\s*,", Pattern.DOTALL);

        final List<String> found = new ArrayList<String>();
        try (java.util.stream.Stream<Path> walk = Files.walk(Paths.get("src/main/java")))
        {
            for (final Path source : walk.toList())
            {
                if (!source.getFileName().toString().endsWith(".java"))
                {
                    continue;
                }
                final String text = Files.readString(source, StandardCharsets.UTF_8);
                final Matcher m = redundant.matcher(text);
                while (m.find())
                {
                    found.add(source.getFileName().toString());
                }
            }
        }

        assertEquals(List.of("WormholeXTreme.java"), found,
            "the only prettyLog call allowed to pass false is the delegation inside the "
                + "two-argument overload. Everything else should call prettyLog(level, message) "
                + "-- a bare boolean literal at a call site says nothing to whoever reads it.");
        assertTrue(!found.isEmpty(), "no sources were read, so this proved nothing");
    }
}
