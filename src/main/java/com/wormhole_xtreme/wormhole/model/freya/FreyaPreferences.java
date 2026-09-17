package com.wormhole_xtreme.wormhole.model.freya;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import org.yaml.snakeyaml.Yaml;

import com.wormhole_xtreme.wormhole.utils.DataLayout;
import com.wormhole_xtreme.wormhole.utils.PluginLog;
import com.wormhole_xtreme.wormhole.utils.YamlMaps;
import com.wormhole_xtreme.wormhole.utils.YamlStore;

/**
 * Who has asked to keep a companion.
 *
 * <p>The cat herself is never stored. She is an ordinary entity that is spawned fresh, marked
 * not to persist, and removed when her owner leaves; nothing about her survives a session. What
 * survives is one bit per player -- do they want her -- and this is the whole of it.
 *
 * <p>Which is why this file behaves unlike every other store in the plugin. It is not created
 * on startup, not written with defaults, and not left behind empty: it comes into existence
 * the first time somebody turns a companion on, and is deleted the moment the last one turns
 * theirs off. A server where nobody has found the command has no file in its data folder and no
 * reason to wonder what one would have been for. A missing file is the ordinary case and means
 * nobody, never that something failed.
 */
public final class FreyaPreferences
{
    /** The one key in the file. */
    private static final String ENABLED_KEY = "Enabled";

    /**
     * Who wants a companion, by player id.
     *
     * <p>Insertion-ordered so the file does not reshuffle itself on every write, which would
     * make each save look like a change to anyone watching the folder or keeping it in git.
     */
    private static final Set<UUID> ENABLED = new LinkedHashSet<>();

    private FreyaPreferences() {}

    /**
     * @param playerId
     *            the player
     * @return true if they have a companion turned on
     */
    public static boolean isEnabled(final UUID playerId)
    {
        return (playerId != null) && ENABLED.contains(playerId);
    }

    /**
     * Turns a companion on or off, and writes the change out immediately.
     *
     * <p>Saved per change rather than on shutdown, matching how mirrors are written: a server
     * that is killed rather than stopped should not forget who asked.
     *
     * @param playerId
     *            the player
     * @param enabled
     *            whether they want a companion
     * @return true if this actually changed anything
     */
    public static boolean setEnabled(final UUID playerId, final boolean enabled)
    {
        if (playerId == null)
        {
            return false;
        }
        final boolean changed = enabled ? ENABLED.add(playerId) : ENABLED.remove(playerId);
        if (changed)
        {
            save();
        }
        return changed;
    }

    /** @return every player id with a companion turned on, in the order they asked */
    public static Collection<UUID> enabled()
    {
        return Collections.unmodifiableSet(ENABLED);
    }

    /**
     * Reads the file, if there is one.
     *
     * <p>A missing file is the normal case and loads nobody. A file that will not parse is a
     * hand edit gone wrong: worth a line in the log, not worth refusing to start over, and
     * deliberately not overwritten -- a parse failure that silently truncated the file would
     * lose every entry in it.
     *
     * @return how many players were loaded
     */
    public static int loadAll()
    {
        ENABLED.clear();
        final File file = DataLayout.freyaFile();
        if (!file.exists())
        {
            return 0;
        }
        try (FileInputStream in = new FileInputStream(file))
        {
            final Map<String, Object> root = YamlMaps.asMap(new Yaml().load(in));
            for (final Object id : YamlMaps.asList(root.get(ENABLED_KEY)))
            {
                addParsed(id);
            }
        }
        catch (final IOException | RuntimeException e)
        {
            PluginLog.log(Level.WARNING, "Failed to read companion file: " + e.getMessage());
        }
        return ENABLED.size();
    }

    /**
     * Adds one id from the file, if it is one.
     *
     * <p>An unparseable entry is dropped rather than failing the load. The cost of getting
     * this wrong is one player's cat not appearing, which they can fix by typing the word
     * again; the cost of throwing is everybody else's not appearing either.
     *
     * @param id
     *            whatever the file had in the list
     */
    private static void addParsed(final Object id)
    {
        if (id == null)
        {
            return;
        }
        try
        {
            ENABLED.add(UUID.fromString(id.toString()));
        }
        catch (final IllegalArgumentException e)
        {
            PluginLog.log(Level.WARNING, "Ignoring unreadable id in companion file: " + id);
        }
    }

    /**
     * Writes the file out, or deletes it when there is nobody left in it.
     *
     * <p>The delete is the unusual half and the point of the whole class: an empty file would
     * be a permanent record that somebody once found the command, on a server where nobody
     * does any more.
     */
    private static void save()
    {
        final File target = DataLayout.freyaFile();
        if (ENABLED.isEmpty())
        {
            delete(target);
            return;
        }

        final File parent = target.getParentFile();
        if ((parent != null) && !parent.exists() && !parent.mkdirs())
        {
            PluginLog.log(Level.WARNING,
                "Could not create companion data directory " + parent.getAbsolutePath());
            return;
        }

        final Map<String, Object> root = new LinkedHashMap<>();
        final List<String> ids = new ArrayList<>(ENABLED.size());
        for (final UUID id : ENABLED)
        {
            ids.add(id.toString());
        }
        root.put(ENABLED_KEY, ids);
        try
        {
            YamlStore.write(target, root);
        }
        catch (final IOException e)
        {
            PluginLog.log(Level.WARNING, "Failed to write companion file: " + e.getMessage());
        }
    }

    /**
     * Removes the file, quietly.
     *
     * <p>Logged at FINE rather than WARNING: an owner who never knew this file existed should
     * not be told at startup that it could not be removed. The only consequence of a failed
     * delete is a file holding an empty list, which loads as nobody anyway.
     *
     * @param target
     *            the file to remove
     */
    private static void delete(final File target)
    {
        if (target.exists() && !target.delete())
        {
            PluginLog.log(Level.FINE, "Could not remove the companion file; it is now empty.");
        }
    }

    /** Forgets everyone, without touching the file. For tests and for a reload. */
    public static void clear()
    {
        ENABLED.clear();
    }
}
