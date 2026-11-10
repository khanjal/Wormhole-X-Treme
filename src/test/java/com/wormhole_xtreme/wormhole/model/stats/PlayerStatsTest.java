package com.wormhole_xtreme.wormhole.model.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.stats.PlayerStats.Counter;
import com.wormhole_xtreme.wormhole.model.stats.PlayerStats.Counts;
import com.wormhole_xtreme.wormhole.utils.DataLayout;
import com.wormhole_xtreme.wormhole.utils.EntityUtils;
import com.wormhole_xtreme.wormhole.utils.YamlStore;

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

    /**
     * A file that would not read is copied to {@code stats.yml.bad} before the first save writes over
     * it, so an admin's hand edit gone wrong is still there to repair.
     */
    @Test
    void aBrokenFileIsKeptAsBadBeforeItIsWrittenOver() throws Exception
    {
        final File file = DataLayout.statsFile();
        assertTrue(file.getParentFile().mkdirs());
        final String broken = "players: [unclosed\n  : : {";
        Files.writeString(file.toPath(), broken, StandardCharsets.UTF_8);
        final File bad = new File(file.getPath() + ".bad");
        Files.writeString(bad.toPath(), "an older copy", StandardCharsets.UTF_8);
        PlayerStats.load();

        count(ALICE, Counter.TRIPS, 2);
        assertTrue(PlayerStats.saveIfChanged());

        assertEquals(broken, Files.readString(bad.toPath(), StandardCharsets.UTF_8), "the broken file, over the older copy");
        PlayerStats.load();
        assertEquals(2, PlayerStats.of(ALICE).trips(), "and the new counts are what stats.yml holds");
    }

    /** A players section that is not a map would be written over with nothing; it is set aside first. */
    @Test
    void aPlayersSectionThatIsNotAMapIsKeptAsBad() throws Exception
    {
        final File file = DataLayout.statsFile();
        assertTrue(file.getParentFile().mkdirs());
        final String text = "players:\n- " + ALICE + "\n";
        Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
        PlayerStats.load();

        count(BOB, Counter.TRIPS, 2);
        assertTrue(PlayerStats.saveIfChanged());

        assertEquals(text, Files.readString(new File(file.getPath() + ".bad").toPath(), StandardCharsets.UTF_8));
    }

    /** A player id that would not parse is dropped from the next save, so the file is set aside first. */
    @Test
    void aSkippedPlayerIdKeepsTheFileAsBad() throws Exception
    {
        final File file = DataLayout.statsFile();
        assertTrue(file.getParentFile().mkdirs());
        final String text = "players:\n  not-a-uuid:\n    trips: 7\n  " + ALICE + ":\n    trips: 3\n";
        Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
        PlayerStats.load();

        count(ALICE, Counter.TRIPS, 2);
        assertTrue(PlayerStats.saveIfChanged());

        assertEquals(text, Files.readString(new File(file.getPath() + ".bad").toPath(), StandardCharsets.UTF_8));
        PlayerStats.load();
        assertEquals(5, PlayerStats.of(ALICE).trips(), "the readable player kept their counts");
    }

    /** A file that reads but is not a map of players is set aside the same way. */
    @Test
    void aFileThatIsNotAMapIsKeptAsBadToo() throws Exception
    {
        final File file = DataLayout.statsFile();
        assertTrue(file.getParentFile().mkdirs());
        Files.writeString(file.toPath(), "- just\n- a list\n", StandardCharsets.UTF_8);
        PlayerStats.load();

        count(ALICE, Counter.BEAMS, 3);
        assertTrue(PlayerStats.saveIfChanged());

        assertEquals("- just\n- a list\n",
            Files.readString(new File(file.getPath() + ".bad").toPath(), StandardCharsets.UTF_8));
    }

    /** A file that read cleanly is written over with no copy left beside it. */
    @Test
    void aGoodFileLeavesNoBadCopy()
    {
        count(ALICE, Counter.TRIPS, 2);
        assertTrue(PlayerStats.saveIfChanged());
        PlayerStats.load();
        count(ALICE, Counter.TRIPS, 1);
        assertTrue(PlayerStats.saveIfChanged());

        assertFalse(new File(DataLayout.statsFile().getPath() + ".bad").exists());
    }

    /**
     * A write that throws, of any kind, is tried again at the next save rather than given up on:
     * the counts are still only in memory.
     */
    @Test
    void aSaveThatThrowsIsTriedAgainNextTime()
    {
        count(ALICE, Counter.TRIPS, 3);
        try (MockedStatic<YamlStore> store = mockStatic(YamlStore.class))
        {
            store.when(() -> YamlStore.write(any(), any())).thenThrow(new IllegalStateException("disk said no"));

            assertFalse(PlayerStats.saveIfChanged(), "nothing was written");
        }

        assertTrue(PlayerStats.saveIfChanged(), "still owed, so the next save writes it");
        PlayerStats.clear();
        PlayerStats.load();
        assertEquals(3, PlayerStats.of(ALICE).trips());
    }

    /**
     * An Error out of the write -- a class the classloader can no longer read, after a jar swap --
     * passes on up, but the counts are still owed to the next save.
     */
    @Test
    void aSaveThatThrowsAnErrorIsTriedAgainNextTime()
    {
        count(ALICE, Counter.BEAMS, 2);
        try (MockedStatic<YamlStore> store = mockStatic(YamlStore.class))
        {
            store.when(() -> YamlStore.write(any(), any())).thenThrow(new NoClassDefFoundError("gone"));

            assertThrows(NoClassDefFoundError.class, PlayerStats::saveIfChanged);
        }

        assertTrue(PlayerStats.saveIfChanged(), "still owed, so the next save writes it");
        PlayerStats.clear();
        PlayerStats.load();
        assertEquals(2, PlayerStats.of(ALICE).beams());
    }

    @Test
    void onlyARealPlayerIsCounted()
    {
        final Player alice = mock(Player.class);
        when(alice.getUniqueId()).thenReturn(ALICE);
        final Player npc = mock(Player.class);
        when(npc.getUniqueId()).thenReturn(BOB);
        when(npc.hasMetadata(EntityUtils.NPC)).thenReturn(true);

        PlayerStats.countPlayer(alice, Counter.RINGS);
        PlayerStats.countPlayer(alice, Counter.RINGS);
        PlayerStats.countPlayer(npc, Counter.RINGS);
        PlayerStats.countPlayer(mock(Entity.class), Counter.RINGS);
        PlayerStats.countPlayer(null, Counter.RINGS);

        assertEquals(2, PlayerStats.of(ALICE).rings());
        assertEquals(Counts.NONE, PlayerStats.of(BOB), "an NPC plugin's fake player is never counted");
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
    void loadingLeavesNothingToSave()
    {
        count(ALICE, Counter.TRIPS, 2);
        assertTrue(PlayerStats.saveIfChanged());
        count(ALICE, Counter.TRIPS, 1);

        PlayerStats.load();

        assertFalse(PlayerStats.saveIfChanged(), "what was just read is what the file holds");
    }

    private static Stargate gate(final String name)
    {
        final Stargate gate = new Stargate();
        gate.setGateName(name);
        return gate;
    }

    private static Player realPlayer(final UUID id)
    {
        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        return player;
    }

    /** A trip counts for the gate by its name in any case, as the gate list is keyed, and for real players only (#638). */
    @Test
    void aGateTripCountsByLowerCasedNameForARealPlayerOnly()
    {
        final Player npc = realPlayer(BOB);
        when(npc.hasMetadata(EntityUtils.NPC)).thenReturn(true);

        PlayerStats.countGateTrip(realPlayer(ALICE), gate("Atlantis"));
        PlayerStats.countGateTrip(realPlayer(ALICE), gate("ATLANTIS"));
        PlayerStats.countGateTrip(npc, gate("Atlantis"));
        PlayerStats.countGateTrip(mock(Entity.class), gate("Atlantis"));
        PlayerStats.countGateTrip(null, gate("Atlantis"));
        PlayerStats.countGateTrip(realPlayer(ALICE), null);
        PlayerStats.countGateTrip(realPlayer(ALICE), gate(null));

        assertEquals(2, PlayerStats.gateTrips("atlantis"));
        assertEquals(2, PlayerStats.gateTrips("ATLANTIS"));
        assertEquals(0, PlayerStats.gateTrips("Elsewhere"));
        assertEquals(0, PlayerStats.gateTrips(null));
        assertEquals(Counts.NONE, PlayerStats.of(ALICE), "a gate trip alone changes no player's counts");
    }

    @Test
    void gateTripsSurviveASaveAndLoadAndAreOwedUntilSaved() throws Exception
    {
        PlayerStats.countGateTrip(realPlayer(ALICE), gate("Atlantis"));
        PlayerStats.countGateTrip(realPlayer(ALICE), gate("Atlantis"));
        PlayerStats.countGateTrip(realPlayer(ALICE), gate("Abydos"));
        assertTrue(PlayerStats.saveIfChanged(), "a gate trip alone makes the file owed");

        final String text = Files.readString(DataLayout.statsFile().toPath(), StandardCharsets.UTF_8);
        assertTrue(text.contains("gates:"), "under its own section beside players: " + text);
        PlayerStats.clear();
        assertEquals(0, PlayerStats.gateTrips("Atlantis"), "precondition: memory is empty");
        PlayerStats.load();

        assertEquals(2, PlayerStats.gateTrips("Atlantis"));
        assertEquals(1, PlayerStats.gateTrips("abydos"));
        assertFalse(PlayerStats.saveIfChanged(), "what was just read is what the file holds");
    }

    /** A file from before gates were counted, with only players, still loads and starts the gates at none. */
    @Test
    void aFileWithOnlyPlayersLoadsAndLeavesTheGatesAtNothing() throws Exception
    {
        final File file = DataLayout.statsFile();
        assertTrue(file.getParentFile().mkdirs());
        Files.writeString(file.toPath(), "players:\n  " + ALICE + ":\n    trips: 3\n", StandardCharsets.UTF_8);

        assertEquals(1, PlayerStats.load());

        assertEquals(3, PlayerStats.of(ALICE).trips());
        assertEquals(0, PlayerStats.gateTrips("Atlantis"));
        PlayerStats.countGateTrip(realPlayer(ALICE), gate("Atlantis"));
        assertTrue(PlayerStats.saveIfChanged());
        assertFalse(new File(file.getPath() + ".bad").exists(), "a file with no gates section is not a broken one");
    }

    /** A gate called with a number or a word YAML reads as something else still loads under its name. */
    @Test
    void gatesWithNamesYamlReadsAsOtherTypesRoundTrip() throws Exception
    {
        PlayerStats.countGateTrip(realPlayer(ALICE), gate("123"));
        PlayerStats.countGateTrip(realPlayer(ALICE), gate("true"));
        assertTrue(PlayerStats.saveIfChanged());
        PlayerStats.clear();

        PlayerStats.load();

        assertEquals(1, PlayerStats.gateTrips("123"));
        assertEquals(1, PlayerStats.gateTrips("true"));
    }

    @Test
    void aGateSectionThatIsNotAMapIsKeptAsBad() throws Exception
    {
        final File file = DataLayout.statsFile();
        assertTrue(file.getParentFile().mkdirs());
        final String text = "players: {}\ngates:\n- atlantis\n";
        Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
        PlayerStats.load();

        PlayerStats.countGateTrip(realPlayer(ALICE), gate("Abydos"));
        assertTrue(PlayerStats.saveIfChanged());

        assertEquals(text, Files.readString(new File(file.getPath() + ".bad").toPath(), StandardCharsets.UTF_8));
    }

    /** Forgetting a gate drops its count, so a new gate of the name starts at none, and is owed to the file. */
    @Test
    void forgettingAGateDropsItsCountAndIsSaved() throws Exception
    {
        PlayerStats.countGateTrip(realPlayer(ALICE), gate("Atlantis"));
        PlayerStats.countGateTrip(realPlayer(ALICE), gate("Abydos"));
        PlayerStats.saveIfChanged();

        PlayerStats.forgetGate("ATLANTIS");

        assertEquals(0, PlayerStats.gateTrips("Atlantis"));
        assertEquals(1, PlayerStats.gateTrips("Abydos"), "only that gate");
        assertFalse(PlayerStats.saveIfChanged(), "the drop was already written, not left owed");
        PlayerStats.clear();
        PlayerStats.load();
        assertEquals(0, PlayerStats.gateTrips("Atlantis"));
        PlayerStats.forgetGate("never-counted");
        PlayerStats.forgetGate(null);
        assertFalse(PlayerStats.saveIfChanged(), "forgetting a gate with no count changes nothing");
    }

    /** The file itself loses the count at once, so a crash before the next save cannot hand it to a new gate (#638). */
    @Test
    void aForgottenGatesCountIsGoneFromTheFileWrittenAtOnce() throws Exception
    {
        PlayerStats.countGateTrip(realPlayer(ALICE), gate("Atlantis"));
        PlayerStats.countGateTrip(realPlayer(ALICE), gate("Abydos"));
        PlayerStats.saveIfChanged();

        PlayerStats.forgetGate("Atlantis");

        final String text = Files.readString(DataLayout.statsFile().toPath(), StandardCharsets.UTF_8);
        assertFalse(text.contains("atlantis"), "gone from the file, not just memory: " + text);
        assertTrue(text.contains("abydos"), "the other gate stays: " + text);
    }

    @Test
    void clearForgetsGateTripsAsWell()
    {
        PlayerStats.countGateTrip(realPlayer(ALICE), gate("Atlantis"));

        PlayerStats.clear();

        assertEquals(0, PlayerStats.gateTrips("Atlantis"));
    }

    @Test
    void loadReplacesGateTripsHeldInMemory() throws Exception
    {
        PlayerStats.countGateTrip(realPlayer(ALICE), gate("Atlantis"));

        PlayerStats.load();

        assertEquals(0, PlayerStats.gateTrips("Atlantis"), "no file, so nothing counted");
    }
}
