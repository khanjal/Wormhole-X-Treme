package com.wormhole_xtreme.wormhole.model;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import com.wormhole_xtreme.wormhole.utils.DataLayout;
import com.wormhole_xtreme.wormhole.utils.PluginLog;

/**
 * Moves this fork's own files out of the folder it shared with another fork's database.
 *
 * <p>Every build descended from the 2011 original keeps its gates in
 * {@code WormholeXTremeDB/WormholeXTreme.sqlite}. This fork does not use that database -- it
 * stores a file per gate -- but it had been storing those files, and the rings and the beam
 * destinations, inside the same folder. So one directory was simultaneously the import source
 * from other forks and this fork's live data, and nothing about the layout said which parts
 * were which.
 *
 * <p>This moves ours to {@code data/} and leaves theirs alone.
 *
 * <h2>Why this is not a folder rename</h2>
 *
 * <p>The obvious implementation -- rename {@code WormholeXTremeDB} to {@code data} -- is
 * wrong, and quietly so. It would take the foreign SQLite database along with it, and
 * {@code /wormhole gate import} looks for that database by name in the folder other forks
 * write it to. An operator who had not yet imported would find the offer had silently stopped
 * appearing, with their old server's gates still sitting in a file the plugin no longer looks
 * at. So this moves a known list and steps over everything else, including files it has never
 * heard of, which belong to whoever put them there.
 *
 * <h2>What it will not do</h2>
 *
 * <p>Nothing is deleted and nothing is overwritten. A file that moves is moved rather than
 * copied, so in the ordinary case there is one of it and it is in {@code data/}.
 *
 * <p>Two cases deliberately leave a file in the old folder, and both mean there are then two
 * copies of it on disk. A file already at the destination wins -- that is the one being loaded
 * -- so the one in the old folder stays where it is, inert. And a move that fails is reported
 * by name rather than passed over, because the file is still in the old folder and the recovery
 * is to move it by hand, which is only possible if the log says which one.
 *
 * <p>In both cases {@code data/} holds the copy the plugin reads and the old folder holds one
 * nothing will look at again. Worth knowing before editing a gate file in there and wondering
 * why nothing changed.
 */
public final class LegacyDataFolderMigration
{
    /** What one run of the migration came to. */
    public static final class Result
    {
        private int moved;
        private final List<String> failed = new ArrayList<>();

        /** @return how many files were moved across */
        public int getMoved() { return moved; }

        /** @return the files that could not be moved, which are still in the old folder */
        public List<String> getFailed() { return failed; }

        /** @return true if anything at all happened, so a silent run stays silent */
        public boolean didSomething() { return (moved > 0) || !failed.isEmpty(); }
    }

    /** Static helpers only. */
    private LegacyDataFolderMigration()
    {
    }

    /** Runs the migration against the real folders. */
    public static void migrate()
    {
        report(migrate(DataLayout.legacyData(), DataLayout.data()));
    }

    /**
     * Moves the known files from one directory to another.
     *
     * @param legacy
     *            the old shared folder, which on a new install does not exist
     * @param target
     *            this fork's own data directory
     * @return what happened, for the caller to log
     */
    static Result migrate(final File legacy, final File target)
    {
        final Result result = new Result();
        if (!legacy.isDirectory())
        {
            return result;
        }
        for (final String name : DataLayout.migratableNames())
        {
            final File source = new File(legacy, name);
            if (!source.exists())
            {
                continue;
            }
            if (source.isDirectory())
            {
                moveDirectoryContents(source, new File(target, name), name, result);
            }
            else
            {
                moveOne(source, new File(target, name), name, result);
            }
        }
        return result;
    }

    /**
     * Moves the files inside one directory, rather than the directory itself.
     *
     * <p>Per file rather than a single rename of the folder, because the destination may
     * already exist -- a migration interrupted half way, or an operator who made the folder
     * themselves. Renaming a directory onto an existing one fails on some filesystems and
     * silently nests it on others, and either way the gates that were already at the
     * destination are the ones at risk.
     *
     * @param source
     *            the directory to empty
     * @param destination
     *            the directory to fill, created if it is not there
     * @param label
     *            what to call this in the log
     * @param result
     *            tally to add to
     */
    private static void moveDirectoryContents(final File source, final File destination,
        final String label, final Result result)
    {
        final File[] files = source.listFiles();
        if ((files == null) || (files.length == 0))
        {
            return;
        }
        if (!destination.isDirectory() && !destination.mkdirs() && !destination.isDirectory())
        {
            PluginLog.log(Level.SEVERE, "Could not create " + destination.getPath()
                + "; leaving " + label + " where it is. Move it by hand before restarting.");
            for (final File file : files)
            {
                result.failed.add(label + File.separator + file.getName());
            }
            return;
        }
        for (final File file : files)
        {
            if (file.isFile())
            {
                moveOne(file, new File(destination, file.getName()),
                    label + File.separator + file.getName(), result);
            }
        }
    }

    /**
     * Moves one file, unless something is already there.
     *
     * @param source
     *            the file to move
     * @param destination
     *            where it should end up
     * @param label
     *            what to call it in the log
     * @param result
     *            tally to add to
     */
    private static void moveOne(final File source, final File destination, final String label,
        final Result result)
    {
        if (destination.exists())
        {
            PluginLog.log(Level.INFO, "Keeping the copy of " + label
                + " already in the data folder; the older one is left in "
                + source.getParentFile().getName() + ".");
            return;
        }
        final File parent = destination.getParentFile();
        if ((parent != null) && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory())
        {
            PluginLog.log(Level.SEVERE, "Could not create " + parent.getPath()
                + "; " + label + " is still in the old folder and will not be loaded.");
            result.failed.add(label);
            return;
        }
        if (source.renameTo(destination))
        {
            result.moved++;
        }
        else
        {
            PluginLog.log(Level.SEVERE, "Could not move " + label
                + " into the data folder; it is still in the old one and will not be loaded. "
                + "Move it by hand and restart.");
            result.failed.add(label);
        }
    }

    /**
     * Says what happened, and only when something did.
     *
     * <p>This runs on every startup for ever, so an install with nothing to migrate -- which
     * is every install, after the first one that needed it -- says nothing at all.
     *
     * @param result
     *            what the run came to
     */
    private static void report(final Result result)
    {
        if (!result.didSomething())
        {
            return;
        }
        if (result.getMoved() > 0)
        {
            PluginLog.log(Level.INFO, "Moved " + result.getMoved()
                + " file(s) into the data folder. The old folder is left as it was, and still "
                + "holds any database /wormhole gate import would read.");
        }
        if (!result.getFailed().isEmpty())
        {
            PluginLog.log(Level.SEVERE, result.getFailed().size()
                + " file(s) could not be moved and will not be loaded: "
                + String.join(", ", result.getFailed()));
        }
    }
}
