package com.wormhole_xtreme.wormhole.model.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.stats.PlayerStats.Counter;
import com.wormhole_xtreme.wormhole.model.stats.PlayerStats.Counts;
import com.wormhole_xtreme.wormhole.utils.DataLayout;

/**
 * {@code stats.yml}: what goes in comes back out, and a file that is missing or broken starts
 * everybody at nothing rather than stopping the plugin (#237).
 *
 * <p>The counts are lifetime counts, so the file is the only place they live across a restart. A
 * save that dropped one counter, or wrote under the name rather than the UUID, would read back
 * looking entirely plausible -- just lower, or attached to nobody.
 */
class PlayerStatsTest
{
    private static final UUID ALICE = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final UUID BOB = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @TempDir
    File dataFolder;

    @BeforeEach
    void setUp() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        PluginTestSupport.install(plugin);
        PlayerStats.clear();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PlayerStats.clear();
        PluginTestSupport.remove();
    }

    private static void count(final UUID player, final Counter counter, final int times)
    {
        for (int i = 0; i < times; i++)
        {
            PlayerStats.count(player, counter);
        }
    }

    @Test
    void eachCounterCountsForItsOwnPlayerOnly()
    {
        count(ALICE, Counter.TRIPS, 3);
        count(ALICE, Counter.GATES_BUILT, 2);
        count(BOB, Counter.RINGS, 2);
        count(BOB, Counter.BEAMS, 3);

        assertEquals(new Counts(3, 2, 0, 0), PlayerStats.of(ALICE));
        assertEquals(new Counts(0, 0, 2, 3), PlayerStats.of(BOB));
    }

    @Test
    void aPlayerWithNoRecordHasNothingCounted()
    {
        assertEquals(Counts.NONE, PlayerStats.of(ALICE));
        assertEquals(Counts.NONE, PlayerStats.of(null));
    }

    @Test
    void savedCountsLoadBackForTheSamePlayers() throws Exception
    {
        count(ALICE, Counter.TRIPS, 3);
        count(ALICE, Counter.GATES_BUILT, 2);
        count(ALICE, Counter.RINGS, 4);
        count(ALICE, Counter.BEAMS, 5);
        count(BOB, Counter.BEAMS, 2);
        assertTrue(PlayerStats.saveIfChanged(), "something was counted, so the file is written");

        final String text = Files.readString(DataLayout.statsFile().toPath(), StandardCharsets.UTF_8);
        assertTrue(text.contains("gates-built: 2"), "written under the key the docs name: " + text);

        PlayerStats.clear();
        assertEquals(2, PlayerStats.load(), "both players come back");

        assertEquals(new Counts(3, 2, 4, 5), PlayerStats.of(ALICE));
        assertEquals(new Counts(0, 0, 0, 2), PlayerStats.of(BOB));
    }

    @Test
    void aMissingFileStartsEverybodyAtNothing()
    {
        count(ALICE, Counter.TRIPS, 2);

        assertFalse(DataLayout.statsFile().exists(), "precondition: no file yet");
        assertEquals(0, PlayerStats.load());
        assertEquals(Counts.NONE, PlayerStats.of(ALICE), "loading replaces what memory held");
    }

    @Test
    void aBrokenFileStartsEmptyWithoutThrowing() throws Exception
    {
        count(ALICE, Counter.TRIPS, 2);
        final File file = DataLayout.statsFile();
        assertTrue(file.getParentFile().mkdirs());
        Files.writeString(file.toPath(), "players: [unclosed\n  : : {", StandardCharsets.UTF_8);

        // A throw here fails the test: a broken file must not stop the plugin enabling.
        assertEquals(0, PlayerStats.load(), "nothing readable, nothing loaded");
        assertEquals(Counts.NONE, PlayerStats.of(ALICE));
    }

    @Test
    void anUnreadableIdLosesOnlyThatPlayer() throws Exception
    {
        final File file = DataLayout.statsFile();
        assertTrue(file.getParentFile().mkdirs());
        Files.writeString(file.toPath(), "players:\n  not-a-uuid:\n    trips: 7\n  " + ALICE + ":\n    trips: 3\n",
            StandardCharsets.UTF_8);

        assertEquals(1, PlayerStats.load());
        assertEquals(3, PlayerStats.of(ALICE).trips());
    }

    /**
     * Nothing counted means nothing written: the periodic save runs every five minutes on the main
     * thread, and a quiet server should not rewrite the same file each time.
     */
    @Test
    void nothingIsWrittenWhenNothingChanged() throws Exception
    {
        assertFalse(PlayerStats.saveIfChanged(), "nothing counted yet");
        assertFalse(DataLayout.statsFile().exists(), "so no file appears");

        count(ALICE, Counter.TRIPS, 2);
        assertTrue(PlayerStats.saveIfChanged());
        final File file = DataLayout.statsFile();
        Files.writeString(file.toPath(), "players: {}\n", StandardCharsets.UTF_8);

        assertFalse(PlayerStats.saveIfChanged(), "saved already, and nothing counted since");
        assertEquals("players: {}\n", Files.readString(file.toPath(), StandardCharsets.UTF_8),
            "the file is left alone");
    }

    @Test
    void loadingLeavesNothingToSave() throws Exception
    {
        count(ALICE, Counter.TRIPS, 2);
        assertTrue(PlayerStats.saveIfChanged());
        count(ALICE, Counter.TRIPS, 1);

        PlayerStats.load();

        assertFalse(PlayerStats.saveIfChanged(), "what was just read is what the file holds");
    }
}
