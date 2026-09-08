package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * What a caught exception looks like in the log.
 *
 * <p>Every catch site in the plugin used to append {@code e.getMessage()} to its message. For
 * the exception you most want to read -- a {@code NullPointerException} -- that is the literal
 * word {@code null}, and the stack trace saying where it happened was thrown away every time.
 */
class PrettyLogThrowableTest
{
    /** Compiled once: the guard below runs it over every source file in the tree. */
    private static final Pattern GET_MESSAGE = Pattern.compile("\\.getMessage\\(\\)");

    private Logger logger;

    @BeforeEach
    void setUp() throws Exception
    {
        logger = mock(Logger.class);
        set("log", logger);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        set("log", null);
        set("thisPlugin", null);
    }

    private static void set(final String name, final Object value) throws Exception
    {
        final Field f = WormholeXTreme.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(null, value);
    }

    /** A mock installed as the running plugin, with the real logging body. */
    private WormholeXTreme pluginThatReallyLogs() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        when(plugin.getName()).thenReturn("WormholeXTreme");
        // nullable, not any(Throwable.class): a typed any() does not match a null argument,
        // and one of these tests passes null deliberately.
        doCallRealMethod().when(plugin).prettyLog(any(Level.class), anyString(), nullable(Throwable.class));
        set("thisPlugin", plugin);
        return plugin;
    }

    /**
     * Why this overload exists: an NPE has no message.
     *
     * <p>Not a test of the plugin so much as of the assumption every catch site was written
     * on. Appending {@code getMessage()} to a line is how the plugin said what went wrong, and
     * against the one exception that carries no explanation of itself it says "null".
     */
    @Test
    void anExceptionsOwnMessageIsOftenNothingAtAll()
    {
        assertNull(new NullPointerException().getMessage(),
            "an NPE thrown by the runtime carries no message");
        assertEquals("Failed to save gate: null", "Failed to save gate: " + new NullPointerException().getMessage(),
            "which is what the old form put in the log");
    }

    /**
     * The exception is handed to the logger, not flattened into the message.
     *
     * <p>That is what puts the stack trace in the log. The message beside it says what the
     * plugin was doing, which the exception does not know.
     */
    @Test
    void theExceptionReachesTheLoggerWithTheLine() throws Exception
    {
        final WormholeXTreme plugin = pluginThatReallyLogs();
        final IOException boom = new IOException("disk full");

        plugin.prettyLog(Level.WARNING, "Failed to save gate ringworld", boom);

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Supplier<String>> line =
            ArgumentCaptor.forClass(Supplier.class);
        verify(logger).log(eq(Level.WARNING), eq(boom), line.capture());
        assertEquals("[WormholeXTreme] Failed to save gate ringworld", line.getValue().get(),
            "the line is the plugin tag and what it was doing, and nothing of the exception");
    }

    /**
     * The line is still built lazily.
     *
     * <p>{@code Logger.log(Level, Throwable, Supplier)} rather than the string form, so a FINE
     * line on a server logging at INFO costs the call and nothing else. This path is reached
     * from block physics and move handlers, which run thousands of times a second.
     */
    @Test
    void theLineIsNotBuiltUnlessSomethingWillReadIt() throws Exception
    {
        final WormholeXTreme plugin = pluginThatReallyLogs();

        plugin.prettyLog(Level.FINE, "a gate did something", new IllegalStateException());

        verify(logger).log(any(Level.class), any(Throwable.class), any(Supplier.class));
    }

    /** A site with nothing to report passes null, and still gets its line out. */
    @Test
    void aNullThrowableStillLogsTheLine() throws Exception
    {
        final WormholeXTreme plugin = pluginThatReallyLogs();

        plugin.prettyLog(Level.INFO, "nothing went wrong", null);

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Supplier<String>> line = ArgumentCaptor.forClass(Supplier.class);
        verify(logger).log(eq(Level.INFO), eq((Throwable) null), line.capture());
        assertEquals("[WormholeXTreme] nothing went wrong", line.getValue().get());
    }

    /**
     * Nobody puts {@code getMessage()} back into a logged line.
     *
     * <p>The whole point of the overload. A message ending in the exception's own text is a
     * site that should be handing over the exception instead, and it reads the same in a diff
     * either way -- which is why this is checked rather than left to review.
     *
     * <p>It walks back from each {@code getMessage()} to the start of its statement rather
     * than trying to match a whole {@code prettyLog(...)} call. The first version of this test
     * did match the call, with a regex that allowed one level of nested brackets, and so was
     * blind to the two sites whose message contained a parenthesised ternary. Copilot found
     * them; the test had passed.
     */
    @Test
    void noPrettyLogCallFlattensAnExceptionIntoItsMessage() throws IOException
    {
        final List<String> found = new ArrayList<>();
        try (java.util.stream.Stream<Path> walk = Files.walk(Paths.get("src/main/java")))
        {
            for (final Path source : walk.toList())
            {
                if (!source.getFileName().toString().endsWith(".java"))
                {
                    continue;
                }
                final String text = Files.readString(source, StandardCharsets.UTF_8);
                final Matcher m = GET_MESSAGE.matcher(text);
                while (m.find())
                {
                    if (opensAPrettyLogCall(text, m.start()))
                    {
                        found.add(source.getFileName().toString() + ":" + lineOf(text, m.start()));
                    }
                }
            }
        }

        assertEquals(List.of(), found,
            "these calls put an exception's own message in the line. Pass the exception as the "
                + "third argument instead: the message says what the plugin was doing, and the "
                + "logger takes care of what went wrong and where.");
        assertTrue(Files.exists(Paths.get("src/main/java")), "no sources were read, so this proved nothing");
    }

    /**
     * The one-based line {@code at} falls on.
     *
     * @param text
     *            the whole source file
     * @param at
     *            an offset into it
     * @return the line number, so the failure names somewhere to go and look
     */
    private static int lineOf(final String text, final int at)
    {
        int line = 1;
        for (int i = 0; i < at; i++)
        {
            if (text.charAt(i) == '\n')
            {
                line++;
            }
        }
        return line;
    }

    /**
     * Whether the statement containing {@code at} started a {@code prettyLog} call.
     *
     * @param text
     *            the whole source file
     * @param at
     *            where the {@code getMessage()} call begins
     * @return true if a {@code prettyLog(} was opened earlier in the same statement
     */
    private static boolean opensAPrettyLogCall(final String text, final int at)
    {
        final int statementStart = Math.max(text.lastIndexOf(';', at),
            Math.max(text.lastIndexOf('{', at), text.lastIndexOf('}', at)));
        return text.substring(statementStart + 1, at).contains("prettyLog(");
    }
}
