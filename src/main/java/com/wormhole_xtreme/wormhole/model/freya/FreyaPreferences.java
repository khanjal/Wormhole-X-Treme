package com.wormhole_xtreme.wormhole.model.freya;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
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
 * Who has asked to keep a companion; the cat herself is never stored.
 *
 * <p>The file exists only while somebody has her on, so a server where nobody has found the
 * command has nothing in its data folder to wonder about.
 */
public final class FreyaPreferences
{
    private static final String ENABLED_KEY = "Enabled";

    /** Insertion-ordered so a save does not reshuffle the file. */
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
     * Turns a companion on or off, saving immediately so a killed server does not forget.
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
     * Reads the file, if there is one. An unparseable file is logged and left untouched.
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
     * Adds one id from the file, dropping it rather than failing everybody else's load.
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

    /** Writes the file out, or deletes it when there is nobody left in it. */
    private static void save()
    {
        final File target = DataLayout.freyaFile();
        // An undeletable file is emptied instead, or the last id would load again next start.
        if (ENABLED.isEmpty() && deleted(target))
        {
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
     * Removes the file, if it is there.
     *
     * @param target
     *            the companion file
     * @return true if it is gone, false if it could not be removed
     */
    private static boolean deleted(final File target)
    {
        try
        {
            Files.deleteIfExists(target.toPath());
            return true;
        }
        catch (final IOException e)
        {
            PluginLog.log(Level.FINE, "Could not remove the companion file; emptying it instead", e);
            return false;
        }
    }

    /** Forgets everyone, without touching the file. For tests and for a reload. */
    public static void clear()
    {
        ENABLED.clear();
    }
}
