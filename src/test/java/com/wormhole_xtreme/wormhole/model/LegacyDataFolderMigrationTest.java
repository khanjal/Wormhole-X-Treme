package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * Moving this fork's files out of the folder it shared with another fork's database.
 *
 * <p>Every build descended from the 2011 original keeps its gates in
 * {@code WormholeXTremeDB/WormholeXTreme.sqlite}. This fork does not use that database, but it
 * had been keeping its own gates, rings and beam destinations in the same folder -- so one
 * directory was both the import source from other forks and this fork's live storage.
 *
 * <p>This is the riskiest migration in the plugin, and the reason it gets more tests than the
 * shapes one did. A gate shape that fails to move is re-extracted from the jar on the next
 * start; a gate file that fails to move is a gate that no longer exists as far as the server
 * is concerned, and the blocks are still standing in the world. So the tests below are mostly
 * about what must *not* happen: nothing deleted, nothing overwritten, nothing moved that was
 * not ours, and a failure named rather than swallowed.
 */
class LegacyDataFolderMigrationTest
{
    /** The name every fork gives that database, and the name the importer looks for. */
    private static final String SQLITE = "WormholeXTreme.sqlite";

    @TempDir
    File pluginFolder;

    private File legacy;
    private File target;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        legacy = new File(pluginFolder, "WormholeXTremeDB");
        target = new File(pluginFolder, "data");
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PluginTestSupport.remove();
    }

    private File write(final File dir, final String name, final String body) throws Exception
    {
        dir.mkdirs();
        final File file = new File(dir, name);
        Files.write(file.toPath(), body.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private static String read(final File file) throws Exception
    {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private LegacyDataFolderMigration.Result migrate()
    {
        return LegacyDataFolderMigration.migrate(legacy, target);
    }

    // ---- the ordinary upgrade ------------------------------------------------------------

    /** Gate files are what this is really protecting. */
    @Test
    void gateFilesMoveIntoTheDataFolder() throws Exception
    {
        write(new File(legacy, "gates"), "Home.yml", "Name: Home");

        final LegacyDataFolderMigration.Result result = migrate();

        assertTrue(new File(new File(target, "gates"), "Home.yml").isFile(),
            "a gate left behind is a gate the server no longer knows about, with its blocks "
            + "still standing in the world");
        assertEquals(1, result.getMoved());
        assertTrue(result.getFailed().isEmpty());
    }

    @Test
    void ringFilesMoveIntoTheDataFolder() throws Exception
    {
        write(new File(legacy, "rings"), "world.yml", "World: world");

        migrate();

        assertTrue(new File(new File(target, "rings"), "world.yml").isFile(),
            "one ring file holds every pair in a world, so losing it loses a base's whole "
            + "transport network");
    }

    @Test
    void theBeamFileMovesIntoTheDataFolder() throws Exception
    {
        write(legacy, "beam.yml", "Public: {}");

        migrate();

        assertTrue(new File(target, "beam.yml").isFile(),
            "beam destinations are one file, so it is all of them or none");
    }

    /** All three at once, which is what a real server actually has. */
    @Test
    void everythingOursMovesInOneRun() throws Exception
    {
        write(new File(legacy, "gates"), "Home.yml", "Name: Home");
        write(new File(legacy, "gates"), "Away.yml", "Name: Away");
        write(new File(legacy, "rings"), "world.yml", "World: world");
        write(legacy, "beam.yml", "Public: {}");

        final LegacyDataFolderMigration.Result result = migrate();

        assertEquals(4, result.getMoved(), "two gates, one ring file and the beam file");
        assertTrue(result.getFailed().isEmpty());
    }

    // ---- what must not move --------------------------------------------------------------

    /**
     * The database stays exactly where the importer looks for it.
     *
     * <p>This is the single most important assertion here, and the reason the migration moves
     * a known list rather than renaming the folder. A folder rename would carry this file
     * along, and {@code /wormhole gate import} finds it by name in the folder *other* forks
     * write it to. Someone who had not yet imported would find the offer had quietly stopped
     * appearing, with their old server's gates sitting in a file the plugin no longer reads.
     */
    @Test
    void theForeignDatabaseIsLeftExactlyWhereItWas() throws Exception
    {
        write(legacy, SQLITE, "SQLite format 3");
        write(new File(legacy, "gates"), "Home.yml", "Name: Home");

        migrate();

        assertTrue(new File(legacy, SQLITE).isFile(),
            "moving another fork's database out of the folder it names is how an operator "
            + "silently loses their route back to their old gates");
        assertFalse(new File(target, SQLITE).exists(),
            "and it must not be copied across either -- two of it is its own problem");
    }

    /** The importer can still find the database after a migration has run. */
    @Test
    void theImporterStillFindsTheDatabaseAfterwards() throws Exception
    {
        write(legacy, SQLITE, "SQLite format 3");
        write(new File(legacy, "gates"), "Home.yml", "Name: Home");

        migrate();

        assertTrue(new File(legacy, SQLITE).isFile(),
            "LegacyDatabaseImporter.findDatabase looks in exactly this folder for exactly "
            + "this name");
    }

    /**
     * Files nobody recognises belong to whoever put them there.
     *
     * <p>A migration that swept the whole folder would take these too. They may be another
     * plugin's, or a backup an admin made deliberately before upgrading.
     */
    @Test
    void filesThatAreNotOursAreLeftAlone() throws Exception
    {
        write(legacy, "notes.txt", "my notes");
        write(legacy, "gates-backup.zip", "not really a zip");
        write(new File(legacy, "gates"), "Home.yml", "Name: Home");

        migrate();

        assertTrue(new File(legacy, "notes.txt").isFile(), "not ours to move");
        assertTrue(new File(legacy, "gates-backup.zip").isFile(),
            "a name that merely starts like one of ours is still not one of ours");
        assertFalse(new File(target, "notes.txt").exists());
    }

    // ---- nothing overwritten, nothing deleted --------------------------------------------

    /**
     * A file already in the data folder is the one being loaded, and wins.
     *
     * <p>The case this covers is a migration that ran, and then an older jar being put back
     * so that gates were written to the old folder again. The newer file is the one the
     * plugin has been reading; replacing it with the older copy would silently roll a gate
     * back.
     */
    @Test
    void aFileAlreadyInTheDataFolderIsNotOverwritten() throws Exception
    {
        write(new File(target, "gates"), "Home.yml", "Name: Home current");
        write(new File(legacy, "gates"), "Home.yml", "Name: Home stale");

        final LegacyDataFolderMigration.Result result = migrate();

        assertEquals("Name: Home current", read(new File(new File(target, "gates"), "Home.yml")),
            "the file being loaded must not be replaced by the one left in the old folder");
        // The content assertion above is not enough on its own, and the reason is worth
        // knowing. File.renameTo overwrites an existing destination on POSIX and refuses on
        // Windows, so deleting the guard this test exists for changes nothing a content check
        // can see on a Windows machine -- it silently becomes a test of the filesystem rather
        // than of the code. Confirmed by removing the guard: all fourteen tests here still
        // passed locally, while CI on ubuntu would have caught it.
        //
        // Keeping the existing copy is a deliberate, successful outcome, so it is reported as
        // neither a move nor a failure. Without the guard it is one or the other on every
        // platform, which is what makes these two assertions the load-bearing ones.
        assertEquals(0, result.getMoved(), "keeping what is there is not a move");
        assertTrue(result.getFailed().isEmpty(),
            "nor is it a failure to report: " + result.getFailed());
    }

    /** And the copy that lost is still there, so nothing has been destroyed. */
    @Test
    void theFileThatLostIsStillInTheOldFolder() throws Exception
    {
        write(new File(target, "gates"), "Home.yml", "Name: Home current");
        write(new File(legacy, "gates"), "Home.yml", "Name: Home stale");

        migrate();

        final File left = new File(new File(legacy, "gates"), "Home.yml");
        assertTrue(left.isFile(),
            "nothing is deleted, so an operator can always look at what was there before");
        assertEquals("Name: Home stale", read(left),
            "and it is still the older copy, untouched rather than swapped with the newer one");
    }

    /**
     * A half-migrated folder finishes rather than stalling.
     *
     * <p>A previous run that was interrupted -- a server killed mid-startup, a disk that
     * filled -- leaves some files across and some behind. The next start has to bring the
     * rest, and must not treat "the destination already exists" as "there is nothing to do".
     */
    @Test
    void aPartlyMigratedFolderIsFinishedOffWithoutLosingEitherSide() throws Exception
    {
        write(new File(target, "gates"), "Home.yml", "Name: Home");
        write(new File(legacy, "gates"), "Away.yml", "Name: Away");

        final LegacyDataFolderMigration.Result result = migrate();

        assertTrue(new File(new File(target, "gates"), "Home.yml").isFile(), "the one already across");
        assertTrue(new File(new File(target, "gates"), "Away.yml").isFile(), "and the one still behind");
        assertEquals(1, result.getMoved(), "only the one that actually needed moving");
    }

    // ---- quiet when there is nothing to do -----------------------------------------------

    /** A new install has no old folder at all. */
    @Test
    void noOldFolderDoesNothing()
    {
        final LegacyDataFolderMigration.Result result = migrate();

        assertFalse(target.exists(), "nothing to move means nothing to create");
        assertFalse(result.didSomething(), "and nothing to say about it");
    }

    /**
     * A server holding only the foreign database has nothing of ours to move.
     *
     * <p>This is somebody arriving from another fork who has not imported yet. The migration
     * must not create a data folder or log anything for them.
     */
    @Test
    void aFolderHoldingOnlyTheForeignDatabaseIsUntouched() throws Exception
    {
        write(legacy, SQLITE, "SQLite format 3");

        final LegacyDataFolderMigration.Result result = migrate();

        assertFalse(result.didSomething(), "nothing of ours was in there");
        assertFalse(target.exists(), "and no empty data folder is left behind");
        assertTrue(new File(legacy, SQLITE).isFile());
    }

    /** It runs on every startup for ever, so a second run has to be silent. */
    @Test
    void runningItTwiceMovesNothingTheSecondTime() throws Exception
    {
        write(new File(legacy, "gates"), "Home.yml", "Name: Home");

        migrate();
        final LegacyDataFolderMigration.Result second = migrate();

        assertFalse(second.didSomething(), "an already-migrated server says nothing on startup");
        assertEquals("Name: Home", read(new File(new File(target, "gates"), "Home.yml")));
    }

    // ---- failure is reported, not swallowed ----------------------------------------------

    /**
     * A move that cannot happen names the file rather than passing over it.
     *
     * <p>Simulated by putting a plain file where the destination directory needs to be, so
     * creating it cannot succeed. The gates are still in the old folder afterwards, which is
     * the whole reason the log has to say which ones: the recovery is to move them by hand,
     * and that is only possible if the operator knows what to move.
     *
     * <p>Silently carrying on here would mean a server that starts cleanly, reports no error,
     * and has lost every gate.
     */
    @Test
    void aMoveThatCannotHappenIsReportedByName() throws Exception
    {
        write(new File(legacy, "gates"), "Home.yml", "Name: Home");
        write(new File(legacy, "gates"), "Away.yml", "Name: Away");
        // A file, not a directory, exactly where data/gates needs to be.
        target.mkdirs();
        write(target, "gates", "in the way");

        final LegacyDataFolderMigration.Result result = migrate();

        assertEquals(0, result.getMoved(), "nothing could move");
        assertEquals(2, result.getFailed().size(),
            "both files have to be named, not just the first one hit: " + result.getFailed());
        assertTrue(result.getFailed().stream().anyMatch(f -> f.endsWith("Home.yml")),
            "named so the operator can move it by hand: " + result.getFailed());
        assertTrue(new File(new File(legacy, "gates"), "Home.yml").isFile(),
            "and it is still where it was, rather than half-moved");
        assertTrue(result.didSomething(), "a failure is something to report, not silence");
    }
}
