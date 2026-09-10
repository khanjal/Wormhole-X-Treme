package com.wormhole_xtreme.wormhole.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * A message logged with no plugin installed still goes somewhere.
 *
 * <p>Three storage managers each had their own version of the "is there a plugin to log
 * through" check and they did not agree. {@code BeamYamlManager} fell back to
 * {@code java.util.logging}; {@code RingYamlManager} and {@code StargateYamlManager} returned
 * without logging anything at all.
 *
 * <p>That is worth a test rather than a shrug, because of which messages these are. They are
 * the ones saying a ring file would not write, or a gate file could not be saved -- so the
 * silent version dropped exactly the messages that only ever appear when something has already
 * gone wrong. It also meant the same storage failure was observable in one manager's tests and
 * unobservable in another's, so a test could "pass" by waiting for a message that was never
 * coming.
 */
class PluginLogTest
{
    /** Catches what reaches java.util.logging, so the fallback can be asserted on. */
    private static final class Capture extends Handler
    {
        private final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(final LogRecord logged)
        {
            records.add(logged);
        }

        @Override
        public void flush()
        {
            // Nothing is buffered; the records list is appended to directly.
        }

        @Override
        public void close()
        {
            // Nothing to release; the test removes this handler in its teardown.
        }
    }

    private Logger fallbackLogger;
    private Capture capture;

    @BeforeEach
    void setUp()
    {
        fallbackLogger = Logger.getLogger(PluginLog.class.getName());
        capture = new Capture();
        fallbackLogger.addHandler(capture);
        fallbackLogger.setLevel(Level.ALL);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        fallbackLogger.removeHandler(capture);
        PluginTestSupport.remove();
    }

    /** The behaviour two of the three managers did not have. */
    @Test
    void withNoPluginTheMessageGoesToTheFallbackLoggerRatherThanNowhere() throws Exception
    {
        PluginTestSupport.install(null);

        PluginLog.log(Level.WARNING, "Failed to write ring file overworld.yml");

        assertEquals(1, capture.records.size(),
            "a storage failure logged with no plugin installed used to vanish entirely");
        assertEquals("Failed to write ring file overworld.yml", capture.records.get(0).getMessage());
    }

    /** The exception behind a failure survives the fallback too. */
    @Test
    void theExceptionIsCarriedToTheFallbackLogger() throws Exception
    {
        PluginTestSupport.install(null);
        final Exception cause = new IllegalStateException("disk full");

        PluginLog.log(Level.WARNING, "Failed to write YAML gate file Home.yml", cause);

        assertEquals(1, capture.records.size(), "the message reached the fallback");
        assertEquals(cause, capture.records.get(0).getThrown(),
            "a write failure with the cause stripped off is most of a bug report thrown away");
    }

    /** With a plugin, it logs through the plugin and does not also duplicate to the fallback. */
    @Test
    void withAPluginTheMessageGoesThroughItAndNotToTheFallbackAsWell() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        PluginTestSupport.install(plugin);

        PluginLog.log(Level.WARNING, "Skipping malformed beam entry: home");

        verify(plugin).prettyLog(Level.WARNING, "Skipping malformed beam entry: home");
        assertTrue(capture.records.isEmpty(),
            "logging through both would print every storage warning on the console twice");
    }

    /**
     * The three-argument prettyLog is used when there is an exception.
     *
     * <p>Not the two-argument one with the stack trace dropped: this is the overload that
     * exists precisely so a failure keeps its cause.
     */
    @Test
    void withAPluginAnExceptionUsesTheOverloadThatKeepsIt() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        PluginTestSupport.install(plugin);
        final Exception cause = new IllegalStateException("disk full");

        PluginLog.log(Level.WARNING, "Failed to write YAML gate file Home.yml", cause);

        verify(plugin).prettyLog(Level.WARNING, "Failed to write YAML gate file Home.yml", cause);
    }

    /**
     * {@code isLoggable} asks the plugin, so a guarded hot-path line can be skipped.
     *
     * <p>{@code saveStargate} builds "Saved gate to YAML: " plus an absolute path once per
     * gate, and {@code onDisable} calls it for every gate on the server on every shutdown.
     * {@code prettyLog} takes a String, so that concatenation happens whether or not FINE is
     * enabled unless the call site asks first.
     */
    @Test
    void isLoggableAsksThePluginSoAHotPathLineCanBeSkipped() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        when(plugin.isLoggable(Level.FINE)).thenReturn(false);
        PluginTestSupport.install(plugin);

        assertFalse(PluginLog.isLoggable(Level.FINE),
            "an unguarded FINE line pays for its message on every gate of every shutdown");
    }
}
