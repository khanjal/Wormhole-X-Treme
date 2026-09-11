package com.wormhole_xtreme.wormhole.utils;

import java.io.File;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * Resolves a path inside this plugin's own folder.
 *
 * <p>Every store on disk -- gates, rings, beam destinations, shapes, config.yml and the
 * legacy database read by the importer -- has to answer the same question first: where is
 * the plugin folder? There is only one right answer, {@code getDataFolder()}, because that
 * is whatever the server says it is. Half the callers used to build
 * {@code "plugins" + separator + "WormholeXTreme"} instead, which is whatever directory the
 * JVM happened to start in. The two agree on a stock install and stop agreeing the moment a
 * start script changes directory or a launcher points somewhere else -- and when they
 * disagree nothing fails, the plugin simply reads half its files from one tree and half from
 * another.
 *
 * <p>The relative path survives as a fallback, not as an alternative. It is what runs if a
 * path is ever resolved before the plugin exists -- during a failed startup, say, or from a
 * test that has not installed one. A live server never reaches it.
 */
public final class PluginDirectory
{
    /**
     * The folder name to fall back on, matching {@code name:} in plugin.yml.
     *
     * <p>Only ever used when there is no plugin to ask. A live server takes the name from
     * {@code getDataFolder()}, so renaming the plugin cannot leave this constant stale in a
     * way that matters.
     */
    public static final String PLUGIN_FOLDER = "WormholeXTreme";

    /** Static helpers only. */
    private PluginDirectory()
    {
    }

    /**
     * A path inside the plugin folder.
     *
     * @param fallbackPluginFolder
     *            folder name to use if there is no plugin to ask, normally
     *            {@link #PLUGIN_FOLDER}
     * @param segments
     *            path below the plugin folder, one directory name each; none for the plugin
     *            folder itself
     * @return the resolved file, which may not exist yet
     */
    public static File resolve(final String fallbackPluginFolder, final String... segments)
    {
        try
        {
            final WormholeXTreme plugin = WormholeXTreme.getThisPlugin();
            // The data folder is checked as well as the plugin. A live server always has one,
            // but a mock plugin that was never told about it answers null, and
            // new File((File) null, "data") is a *relative* path rather than an error -- so a
            // test that forgot to stub it writes real files into whatever directory the suite
            // was run from. Treating that as "no usable plugin" sends it to the same fallback
            // as having no plugin at all.
            if ((plugin != null) && (plugin.getDataFolder() != null))
            {
                return append(plugin.getDataFolder(), segments);
            }
        }
        catch (final RuntimeException e)
        {
            // No usable plugin: fall through to the relative path below.
        }
        return append(new File("plugins" + File.separator + fallbackPluginFolder), segments);
    }

    /**
     * Walks down from a base directory, one segment at a time.
     *
     * <p>Segment by segment rather than joining with {@code File.separator} so a caller
     * cannot accidentally pass a separator of the wrong platform.
     *
     * @param base
     *            the directory to start from
     * @param segments
     *            the names to descend through
     * @return the resulting file
     */
    private static File append(final File base, final String... segments)
    {
        File resolved = base;
        for (final String segment : segments)
        {
            resolved = new File(resolved, segment);
        }
        return resolved;
    }
}
