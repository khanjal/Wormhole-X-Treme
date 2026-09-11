package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * Turning a mirror into what goes in the file, and back.
 *
 * <p>Mostly exercised through the two conversion methods, the same way {@code BeamYamlManagerTest}
 * does: they are a pure map round trip with no I/O in them. Two tests go through a real file as
 * well, because the conversions meeting the YAML shape and the atomic write is the one thing
 * the unit pair cannot show.
 *
 * <p>What is actually at stake is a mirror going missing on restart. Every field here is one
 * that, read back wrong, either loses the banner (a banner that no longer teleports and no
 * command can see) or loses the destination (a mirror that says it goes nowhere).
 */
class MirrorYamlManagerTest
{
    /**
     * Kept so a test can stub its data folder.
     *
     * <p>Installing a second plugin instead would outlive the teardown: remove() puts back
     * whatever the last install displaced, so a nested pair leaves the first mock in place for
     * whatever class runs next. PluginTestSupport's own doc warns about exactly that.
     */
    private WormholeXTreme plugin;

    @BeforeEach
    void setUp() throws Exception
    {
        // readMirror logs when it skips something; without a plugin that is an NPE rather
        // than a skip.
        plugin = mock(WormholeXTreme.class);
        PluginTestSupport.install(plugin);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PluginTestSupport.remove();
    }

    private static QuantumMirror roundTrip(final QuantumMirror mirror)
    {
        return MirrorYamlManager.readMirror(mirror.name(),
            MirrorYamlManager.writeMirror(mirror));
    }

    /** The ordinary case: everything set, everything comes back. */
    @Test
    void aPointedMirrorSurvivesTheRoundTrip()
    {
        final QuantumMirror before = new QuantumMirror("Museum",
            new MirrorBlock("world", 12, 64, -30),
            new MirrorPoint("snapshot_1_18", 100.5, 65.0, -20.5, 90.0f, -5.0f));

        final QuantumMirror after = roundTrip(before);

        assertEquals(before, after, "a mirror that changes across a save is a mirror that moved");
    }

    /**
     * A named but unpointed mirror survives too.
     *
     * <p>The state between {@code mirror set} and {@code mirror target}. If a restart in that
     * gap dropped the mirror, an admin would have to find the banner and name it again --
     * and would have no way of knowing that is what happened.
     */
    @Test
    void aMirrorWithNoDestinationSurvivesTheRoundTrip()
    {
        final QuantumMirror before =
            new QuantumMirror("New", new MirrorBlock("world", 1, 2, 3), null);

        final QuantumMirror after = roundTrip(before);

        assertNotNull(after, "an unpointed mirror is a real state, not a broken one");
        assertEquals(before.banner(), after.banner());
        assertNull(after.destination());
    }

    /** Coordinates are not rounded on the way through. */
    @Test
    void fractionalCoordinatesAndFacingAreNotRounded()
    {
        final MirrorPoint exact = new MirrorPoint("w", 0.5, 64.0625, -0.5, 177.5f, -12.25f);

        final MirrorPoint after = roundTrip(
            new QuantumMirror("M", new MirrorBlock("w", 0, 64, 0), exact)).destination();

        assertEquals(exact, after,
            "a rounded yaw turns an arriving player to face a wall they were meant to face away from");
    }

    /** A world name with a colon in it survives, because the banner key is parsed from the end. */
    @Test
    void aWorldNameWithAColonSurvives()
    {
        final QuantumMirror before = new QuantumMirror("M",
            new MirrorBlock("my:museum", 5, 64, 5), new MirrorPoint("other", 0, 64, 0, 0, 0));

        assertEquals(before.banner(), roundTrip(before).banner());
    }

    /**
     * A hand-edited entry that is not a map is skipped, not guessed at.
     *
     * <p>The file is read at startup, so one bad entry must cost itself and nothing else --
     * the alternative is a typo leaving a server with no mirrors at all.
     */
    @Test
    void anEntryThatIsNotAMapIsSkipped()
    {
        assertNull(MirrorYamlManager.readMirror("Broken", "just a string"));
        assertNull(MirrorYamlManager.readMirror("Broken", null));
    }

    /** An entry whose banner key will not parse is skipped rather than half-read. */
    @Test
    void anEntryWithAnUnreadableBannerIsSkipped()
    {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("Banner", "not-a-key");

        assertNull(MirrorYamlManager.readMirror("Broken", map),
            "a mirror with no block to click is not a mirror");
    }

    /**
     * Mirrors survive an actual write and read, not just the map conversion.
     *
     * <p>The conversions above are the unit; this is the pair of them through a real file,
     * which is the only place the YAML shape, the atomic write and the reader all meet. A
     * mirror that round-trips in memory and not on disk is a mirror lost on the first restart,
     * and nothing else in the suite would notice.
     */
    @Test
    void mirrorsSurviveAWriteAndReadThroughTheRealFile(@TempDir final File dataFolder)
        throws Exception
    {
        when(plugin.getDataFolder()).thenReturn(dataFolder);

        MirrorManager.clear();
        MirrorManager.add(new QuantumMirror("Museum", new MirrorBlock("world", 12, 64, -30),
            new MirrorPoint("snapshot_1_18", 100.5, 65.0, -20.5, 202.5f, 0.0f)));
        MirrorManager.add(new QuantumMirror("Unpointed", new MirrorBlock("world", 20, 64, 0), null));

        MirrorYamlManager.saveAll();
        MirrorManager.clear();
        final int loaded = MirrorYamlManager.loadAll();

        assertEquals(2, loaded, "both mirrors should come back");
        assertEquals(new MirrorPoint("snapshot_1_18", 100.5, 65.0, -20.5, 202.5f, 0.0f),
            MirrorManager.byName("Museum").destination());
        assertNotNull(MirrorManager.byName("Unpointed"), "including the one going nowhere yet");
        assertNull(MirrorManager.byName("Unpointed").destination());
        assertNotNull(MirrorManager.at(new MirrorBlock("world", 12, 64, -30)),
            "and the block index is rebuilt, or the banner stops answering after a restart");
    }

    /** A server with no mirror file yet is a first run, not a failure. */
    @Test
    void aMissingFileLoadsNothingAndSaysNothing(@TempDir final File dataFolder) throws Exception
    {
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        MirrorManager.clear();

        assertEquals(0, MirrorYamlManager.loadAll());
    }

    /**
     * An entry with no name is skipped rather than taking every other mirror with it.
     *
     * <p>YAML lets a mapping carry a null or empty key, and a mirror with no name cannot go
     * into the registry at all: {@code ConcurrentHashMap} rejects a null key, and the throw
     * would come out of the loop reading the file. One hand-edited entry would then cost the
     * server every mirror it has -- the opposite of the "one bad entry costs itself" this
     * reader is supposed to guarantee.
     */
    @Test
    void anEntryWithNoNameIsSkippedRatherThanThrowing()
    {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("Banner", "world:1:2:3");

        assertNull(MirrorYamlManager.readMirror(null, map), "a null key is not a name");
        assertNull(MirrorYamlManager.readMirror("", map), "nor is an empty one");
        assertNull(MirrorYamlManager.readMirror("   ", map), "nor is whitespace");
    }

    /**
     * And the registry refuses one too, so a future caller cannot reintroduce the abort.
     *
     * <p>Belt and braces on purpose: the reader is the only caller today, and this is the line
     * that keeps the failure impossible rather than merely unreached.
     */
    @Test
    void theRegistryRefusesANamelessMirrorRatherThanThrowing()
    {
        MirrorManager.clear();

        MirrorManager.add(new QuantumMirror(null, new MirrorBlock("world", 1, 2, 3), null));
        MirrorManager.add(new QuantumMirror("  ", new MirrorBlock("world", 4, 5, 6), null));

        assertEquals(0, MirrorManager.count(),
            "a nameless mirror has no key, so it must be refused rather than thrown over");
    }

    /**
     * A destination section missing its world is read as no destination.
     *
     * <p>Rather than as a destination in a world called "null", which is what building the
     * point unconditionally would produce -- and which would then fail at click time with a
     * message naming a world nobody has ever had.
     */
    @Test
    void aDestinationWithNoWorldIsNoDestination()
    {
        final Map<String, Object> destination = new LinkedHashMap<>();
        destination.put("X", 1.0);
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("Banner", "world:1:2:3");
        map.put("Destination", destination);

        final QuantumMirror mirror = MirrorYamlManager.readMirror("M", map);

        assertNotNull(mirror, "the mirror itself is still readable");
        assertNull(mirror.destination());
    }
}
