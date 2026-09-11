package com.wormhole_xtreme.wormhole.model.mirror;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

import org.yaml.snakeyaml.Yaml;

import com.wormhole_xtreme.wormhole.utils.DataLayout;
import com.wormhole_xtreme.wormhole.utils.PluginLog;
import com.wormhole_xtreme.wormhole.utils.YamlMaps;
import com.wormhole_xtreme.wormhole.utils.YamlStore;

/**
 * Loads and saves quantum mirrors, in one file.
 *
 * <p>One file rather than one per world, for the reason {@link DataLayout#mirrorFile()} gives:
 * a mirror is required to span two worlds by default, so there is no world it belongs to.
 *
 * <p>A mirror whose destination world is not loaded still loads, and refuses at click time with
 * a message saying so. Dropping it at load would mean a server that started without one world
 * quietly forgot every mirror pointing into it -- and would then write that forgetting back to
 * disk on shutdown, turning a temporary problem into a permanent one.
 */
public final class MirrorYamlManager
{
    /** The key a mirror's banner block is stored under. */
    private static final String BANNER = "Banner";

    /** The section holding where clicking it sends you. */
    private static final String DESTINATION = "Destination";

    private MirrorYamlManager() {}

    /** Static helpers only; this is the file for {@link MirrorManager}. */
    private static Map<String, Object> readFile()
    {
        final File file = DataLayout.mirrorFile();
        if (!file.exists())
        {
            return Collections.emptyMap();
        }
        try (FileInputStream in = new FileInputStream(file))
        {
            return YamlMaps.asMap(new Yaml().load(in));
        }
        // A file that will not parse is an operator's hand edit gone wrong: worth a line in
        // the log, not worth refusing to start over.
        catch (final IOException | RuntimeException e)
        {
            PluginLog.log(Level.WARNING, "Failed to read mirror file: " + e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Loads every stored mirror into {@link MirrorManager}.
     *
     * @return how many were loaded
     */
    public static int loadAll()
    {
        MirrorManager.clear();
        int loaded = 0;
        for (final Map.Entry<String, Object> entry : YamlMaps.asMap(readFile().get("Mirrors")).entrySet())
        {
            final QuantumMirror mirror = readMirror(entry.getKey(), entry.getValue());
            if (mirror != null)
            {
                MirrorManager.add(mirror);
                loaded++;
            }
        }
        return loaded;
    }

    /**
     * Builds one mirror from its stored form.
     *
     * <p>One unreadable entry costs itself and nothing else. The file is read at startup, so
     * anything stronger would let a single hand-edited typo leave a server with no mirrors at
     * all.
     *
     * @param name
     *            the name it was filed under
     * @param value
     *            whatever the file had there
     * @return the mirror, or null if it cannot be read
     */
    static QuantumMirror readMirror(final String name, final Object value)
    {
        if (!(value instanceof Map))
        {
            PluginLog.log(Level.WARNING, "Skipping malformed mirror: " + name);
            return null;
        }
        try
        {
            final Map<String, Object> map = YamlMaps.asMap(value);
            final MirrorBlock banner = MirrorBlock.fromKey(String.valueOf(map.get(BANNER)));
            if (banner == null)
            {
                PluginLog.log(Level.WARNING, "Skipping mirror with an unreadable banner: " + name);
                return null;
            }
            return new QuantumMirror(name, banner, readPoint(map.get(DESTINATION)));
        }
        catch (final RuntimeException e)
        {
            PluginLog.log(Level.WARNING, "Skipping malformed mirror " + name + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Reads a destination, which may legitimately be absent.
     *
     * <p>A mirror with no destination is one an admin has named but not yet pointed anywhere.
     * That is a real state rather than a broken one, and clicking it says so.
     *
     * @param value
     *            the Destination section, or null
     * @return the point, or null if there is none
     */
    private static MirrorPoint readPoint(final Object value)
    {
        if (!(value instanceof Map))
        {
            return null;
        }
        final Map<String, Object> map = YamlMaps.asMap(value);
        final Object world = map.get("World");
        if (world == null)
        {
            return null;
        }
        return new MirrorPoint(String.valueOf(world), number(map.get("X")), number(map.get("Y")),
            number(map.get("Z")), (float) number(map.get("Yaw")), (float) number(map.get("Pitch")));
    }

    private static double number(final Object value)
    {
        return (value instanceof Number n) ? n.doubleValue() : 0.0;
    }

    /** Writes every registered mirror back to disk, atomically. */
    public static void saveAll()
    {
        final Map<String, Object> mirrors = new LinkedHashMap<>();
        for (final QuantumMirror mirror : MirrorManager.all())
        {
            mirrors.put(mirror.name(), writeMirror(mirror));
        }
        final Map<String, Object> root = new LinkedHashMap<>();
        root.put("Mirrors", mirrors);

        final File target = DataLayout.mirrorFile();
        final File parent = target.getParentFile();
        if ((parent != null) && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory())
        {
            PluginLog.log(Level.SEVERE, "Could not create " + parent.getPath()
                + "; mirrors will not be saved.");
            return;
        }
        try
        {
            YamlStore.write(target, root);
        }
        catch (final IOException e)
        {
            PluginLog.log(Level.SEVERE, "Failed to write mirror file: " + e.getMessage());
        }
    }

    /**
     * Turns one mirror into its stored form.
     *
     * <p>Package-private rather than private: a pure map conversion with no file I/O in it, so
     * a test can round-trip it without touching a disk.
     *
     * @param mirror
     *            the mirror to store
     * @return the map to dump
     */
    static Map<String, Object> writeMirror(final QuantumMirror mirror)
    {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put(BANNER, mirror.banner().toKey());
        if (mirror.destination() != null)
        {
            final MirrorPoint point = mirror.destination();
            final Map<String, Object> destination = new LinkedHashMap<>();
            destination.put("World", point.worldName());
            destination.put("X", point.x());
            destination.put("Y", point.y());
            destination.put("Z", point.z());
            destination.put("Yaw", point.yaw());
            destination.put("Pitch", point.pitch());
            map.put(DESTINATION, destination);
        }
        return map;
    }
}
