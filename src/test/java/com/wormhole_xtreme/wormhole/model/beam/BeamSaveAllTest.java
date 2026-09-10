package com.wormhole_xtreme.wormhole.model.beam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * Writing beam.yml back out, which nothing covered at all.
 *
 * <p>{@code BeamLoadAllTest} reads a file somebody else wrote, and
 * {@code BeamYamlManagerTest} converts one entry each way. Between them they never called
 * {@link BeamYamlManager#saveAll()}, so the pass that actually produces the file -- both
 * sections, the temp-file swap, and the decision about what to leave out -- ran only on live
 * servers.
 *
 * <p>That matters most for what is <em>absent</em> from the file. A destination with no cost
 * of its own inherits the configured default, and it inherits it because the key is missing
 * rather than because it is zero. If saving ever started writing the absent cost out as
 * {@code 0.0}, every place on the server would quietly become free and stay free, and reading
 * it back would look entirely correct.
 */
class BeamSaveAllTest
{
    private static final UUID OWNER = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final UUID OTHER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @TempDir
    File dataFolder;

    @BeforeEach
    void setUp() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        PluginTestSupport.install(plugin);
        BeamManager.clear();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        BeamManager.clear();
        PluginTestSupport.remove();
    }

    private static BeamDestination at(final String name, final double x, final Double cost)
    {
        return new BeamDestination(name, new BeamPoint("world", x, 64.0, 8.0, 90.0f, 0.0f), cost);
    }

    private String savedText() throws Exception
    {
        return new String(Files.readAllBytes(BeamYamlManager.getBeamFile().toPath()),
            StandardCharsets.UTF_8);
    }

    /**
     * Everything written comes back the same, both sections at once.
     *
     * <p>The two halves of the file are shaped differently -- public destinations sit at the
     * top level, places are nested under an owner's id -- so a round trip that only exercised
     * one of them would miss the other entirely.
     */
    @Test
    void publicDestinationsAndPrivatePlacesBothSurviveTheRoundTrip() throws Exception
    {
        BeamManager.setPublicDestination(at("spawn", 1.0, null));
        BeamManager.setPlace(OWNER, at("home", 2.0, null));
        BeamManager.setPlace(OTHER, at("mine", 3.0, null));

        BeamYamlManager.saveAll();
        BeamManager.clear();
        BeamYamlManager.loadAll();

        assertNotNull(BeamManager.getPublicDestination("spawn"), "the public destination came back");
        assertEquals(1, BeamManager.getAllPublicDestinations().size());
        assertEquals(2, BeamManager.getAllPlaces().size(), "both owners' places came back");
        assertNotNull(BeamManager.getPlace(OWNER, "home"));
        assertNotNull(BeamManager.getPlace(OTHER, "mine"));
    }

    /** Every coordinate survives, not just the name that indexes it. */
    @Test
    void aDestinationComesBackAtTheSamePointItWentInAt() throws Exception
    {
        BeamManager.setPublicDestination(new BeamDestination("far",
            new BeamPoint("nether", -1234.5, 31.0, 6789.25, 177.5f, -22.5f), null));

        BeamYamlManager.saveAll();
        BeamManager.clear();
        BeamYamlManager.loadAll();

        final BeamPoint back = BeamManager.getPublicDestination("far").point();
        assertEquals("nether", back.worldName());
        assertEquals(-1234.5, back.x(), 0.0001, "a negative coordinate survives");
        assertEquals(31.0, back.y(), 0.0001);
        assertEquals(6789.25, back.z(), 0.0001, "and a fractional one");
        assertEquals(177.5f, back.yaw(), 0.0001f, "the facing is part of arriving, not decoration");
        assertEquals(-22.5f, back.pitch(), 0.0001f);
    }

    /**
     * A destination with no cost of its own is written without one.
     *
     * <p>Absent means "inherit whatever the server charges"; zero means "this one is free,
     * whatever the server charges". Writing the first as the second would cost nobody
     * anything today and be impossible to notice, right up until an admin sets a beam cost
     * and finds it does not apply to any destination that existed beforehand.
     */
    @Test
    void aDestinationWithNoCostOfItsOwnIsWrittenWithoutOne() throws Exception
    {
        BeamManager.setPublicDestination(at("spawn", 1.0, null));

        BeamYamlManager.saveAll();

        assertFalse(savedText().contains("Cost"),
            "an unset cost must stay absent, not be written out as zero: " + savedText());

        BeamManager.clear();
        BeamYamlManager.loadAll();
        assertNull(BeamManager.getPublicDestination("spawn").cost(),
            "and it must read back as absent rather than as a real zero");
    }

    /** A cost that was set is written, including one deliberately set to free. */
    @Test
    void aCostThatWasSetSurvives() throws Exception
    {
        BeamManager.setPublicDestination(at("paid", 1.0, Double.valueOf(12.5)));
        BeamManager.setPublicDestination(at("free", 2.0, Double.valueOf(0.0)));

        BeamYamlManager.saveAll();
        BeamManager.clear();
        BeamYamlManager.loadAll();

        assertEquals(Double.valueOf(12.5), BeamManager.getPublicDestination("paid").cost());
        assertEquals(Double.valueOf(0.0), BeamManager.getPublicDestination("free").cost(),
            "a deliberate zero is not the same as no cost, and has to survive as itself");
    }

    /**
     * A player whose last place was removed leaves nothing behind in the file.
     *
     * <p>Their entry would otherwise persist for ever as an empty mapping under a UUID
     * nothing refers to -- harmless, and the sort of thing that accumulates one player at a
     * time until somebody opens the file and cannot tell what is live.
     */
    @Test
    void anOwnerWithNoPlacesLeftIsNotWrittenAtAll() throws Exception
    {
        BeamManager.setPlace(OWNER, at("home", 2.0, null));
        BeamManager.removePlace(OWNER, "home");

        BeamYamlManager.saveAll();

        assertFalse(savedText().contains(OWNER.toString()),
            "an owner with no places should not be written: " + savedText());
    }

    /**
     * The write leaves no temporary file behind.
     *
     * <p>It is written to a .tmp and moved into place so a crash mid-write cannot leave a
     * half-file where the real one should be. A .tmp surviving a successful save would mean
     * the move did not happen and the real file is whatever was there before.
     */
    @Test
    void savingLeavesNoTemporaryFileBehind() throws Exception
    {
        BeamManager.setPublicDestination(at("spawn", 1.0, null));

        BeamYamlManager.saveAll();

        final File target = BeamYamlManager.getBeamFile();
        assertTrue(target.isFile(), "the real file is there");
        assertFalse(new File(target.getAbsolutePath() + ".tmp").exists(),
            "and the temp file it was written through is not");
    }

    /** The data directory is created rather than the save being lost for want of one. */
    @Test
    void theDataDirectoryIsCreatedIfItIsNotThereYet() throws Exception
    {
        final File dir = BeamYamlManager.getBeamFile().getParentFile();
        assertFalse(dir.exists(), "the temp folder starts without one, which is the case under test");

        BeamManager.setPublicDestination(at("spawn", 1.0, null));
        BeamYamlManager.saveAll();

        assertTrue(BeamYamlManager.getBeamFile().isFile(),
            "a first run has no WormholeXTremeDB folder, and saving has to make one");
    }
}
