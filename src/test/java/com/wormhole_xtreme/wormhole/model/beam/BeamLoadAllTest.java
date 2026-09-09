package com.wormhole_xtreme.wormhole.model.beam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.PluginForTests;

/**
 * Reading beam.yml back at startup.
 *
 * <p>The file holds two quite different things: public destinations anybody may beam to, and
 * per-player places filed under the owner's UUID. Both are read in one pass, and a single bad
 * entry anywhere in either must not cost the rest -- an operator who hand-edits this file, or
 * a player id that stopped parsing, should lose one line and not the lot.
 *
 * <p>{@link BeamYamlManagerTest} covers one entry's round trip. Nothing covered the pass over
 * the file.
 */
class BeamLoadAllTest
{
    private static final UUID OWNER = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    @TempDir
    File dataFolder;

    @BeforeEach
    void setUp() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);

        PluginForTests.install(plugin);

        BeamManager.clear();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        BeamManager.clear();
        PluginForTests.remove();
    }

    /** Writes beam.yml where getBeamFile will look for it. */
    private void writeBeamFile(final String yaml) throws Exception
    {
        final File db = new File(dataFolder, "WormholeXTremeDB");
        assertTrue(db.mkdirs() || db.isDirectory());
        Files.write(new File(db, "beam.yml").toPath(), yaml.getBytes(StandardCharsets.UTF_8));
    }

    /** No file at all is a first run, not a failure. */
    @Test
    void aMissingFileLoadsNothing()
    {
        assertEquals(0, BeamYamlManager.loadAll());
    }

    /** A file that is not a map at all is refused without taking the server down. */
    @Test
    void aFileThatIsNotAMapLoadsNothing() throws Exception
    {
        writeBeamFile("just a sentence\n");

        assertEquals(0, BeamYamlManager.loadAll());
    }

    /** Public destinations are loaded and reachable by name. */
    @Test
    void publicDestinationsAreLoaded() throws Exception
    {
        writeBeamFile("""
            Public:
              spawn:
                World: world
                X: 1.0
                Y: 64.0
                Z: 2.0
              market:
                World: world
                X: 10.0
                Y: 64.0
                Z: 20.0
            """);

        assertEquals(2, BeamYamlManager.loadAll());
        assertNotNull(BeamManager.getPublicDestination("spawn"));
        assertNotNull(BeamManager.getPublicDestination("market"));
    }

    /** A player's own places are loaded under their id, not into the public list. */
    @Test
    void placesAreLoadedAgainstTheirOwner() throws Exception
    {
        writeBeamFile("""
            Places:
              %s:
                hideout:
                  World: world
                  X: 5.0
                  Y: 64.0
                  Z: 5.0
            """.formatted(OWNER));

        assertEquals(1, BeamYamlManager.loadAll());
        assertNotNull(BeamManager.getPlace(OWNER, "hideout"));
        assertNull(BeamManager.getPublicDestination("hideout"),
            "somebody's own place is not a public destination");
    }

    /**
     * A player id that will not parse costs that player's places and nothing else.
     *
     * <p>The whole file is read at startup, so one unreadable id taking the rest down would
     * leave a server with no beam destinations at all.
     */
    @Test
    void anUnreadablePlayerIdCostsOnlyThatPlayer() throws Exception
    {
        writeBeamFile("""
            Public:
              spawn:
                World: world
                X: 1.0
                Y: 64.0
                Z: 2.0
            Places:
              not-a-uuid:
                lost:
                  World: world
                  X: 5.0
                  Y: 64.0
                  Z: 5.0
              %s:
                hideout:
                  World: world
                  X: 6.0
                  Y: 64.0
                  Z: 6.0
            """.formatted(OWNER));

        assertEquals(2, BeamYamlManager.loadAll(), "the public one and the readable player's");
        assertNotNull(BeamManager.getPublicDestination("spawn"));
        assertNotNull(BeamManager.getPlace(OWNER, "hideout"));
    }

    /** A player whose places are not a map is skipped rather than read as one. */
    @Test
    void aPlayerWhosePlacesAreNotAMapIsSkipped() throws Exception
    {
        writeBeamFile("""
            Places:
              %s: nonsense
            """.formatted(OWNER));

        assertEquals(0, BeamYamlManager.loadAll());
    }

    /**
     * A reload replaces what was loaded before rather than adding to it.
     *
     * <p>Checked by what the manager holds, not by the count returned: the count is worked
     * out from the file being read, so it says 1 either way and would agree with itself
     * whether or not anything was cleared first.
     */
    @Test
    void reloadingForgetsWhatTheFileNoLongerSays() throws Exception
    {
        writeBeamFile("""
            Public:
              spawn:
                World: world
                X: 1.0
                Y: 64.0
                Z: 2.0
            """);
        assertEquals(1, BeamYamlManager.loadAll());
        assertNotNull(BeamManager.getPublicDestination("spawn"));

        // The operator deleted spawn and added market.
        writeBeamFile("""
            Public:
              market:
                World: world
                X: 9.0
                Y: 64.0
                Z: 9.0
            """);
        assertEquals(1, BeamYamlManager.loadAll());

        assertNotNull(BeamManager.getPublicDestination("market"));
        assertNull(BeamManager.getPublicDestination("spawn"),
            "a destination removed from the file must not survive the reload");
    }

    /** Sections that are present but not maps are ignored rather than failing the load. */
    @Test
    void sectionsThatAreNotMapsAreIgnored() throws Exception
    {
        writeBeamFile("""
            Public: nonsense
            Places: also nonsense
            """);

        assertEquals(0, BeamYamlManager.loadAll());
    }
}
