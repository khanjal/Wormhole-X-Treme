package com.wormhole_xtreme.wormhole.utils;

import java.io.File;

/**
 * Every file and folder this plugin owns, named in one place.
 *
 * <p>{@link PluginDirectory} answers where the plugin folder is. This answers what is inside
 * it. The two were one question until the layout started changing under it: the folder name
 * {@code "WormholeXTremeDB"} was written out in four separate files, so moving it meant four
 * edits and a single missed one meant gates read from one folder and written to another.
 *
 * <p>The layout as it stands:
 *
 * <pre>
 * &lt;plugin folder&gt;/
 * ├── config.yml
 * ├── shapes/
 * │   └── gate/*.shape           the shapes gates are built from
 * ├── data/
 * │   ├── gates/&lt;name&gt;.yml       one file per gate
 * │   ├── rings/&lt;world&gt;.yml      one file per world, every pair in it
 * │   └── beam.yml               every destination and place, in one file
 * └── WormholeXTremeDB/
 *     └── WormholeXTreme.sqlite  another fork's database, read by the importer
 * </pre>
 *
 * <p>{@code WormholeXTremeDB} keeps its name because it is not ours to rename. It is what
 * every build descended from the 2011 original calls that folder, and the reason
 * {@code /wormhole gate import} can find one. Nothing is written there.
 */
public final class DataLayout
{
    /** Where this fork keeps what it owns. */
    private static final String DATA = "data";

    /** The folder other forks keep their database in, read but never written. */
    private static final String LEGACY_DATA = "WormholeXTremeDB";

    /** Static helpers only. */
    private DataLayout()
    {
    }

    /** @return the plugin's own folder */
    public static File pluginFolder()
    {
        return PluginDirectory.resolve(PluginDirectory.PLUGIN_FOLDER);
    }

    /** @return config.yml, which sits directly in the plugin folder */
    public static File configFile()
    {
        return new File(pluginFolder(), "config.yml");
    }

    /** @return the directory gate shapes are read from */
    public static File gateShapes()
    {
        return PluginDirectory.resolve(PluginDirectory.PLUGIN_FOLDER, "shapes", "gate");
    }

    /** @return the directory gate shapes used to be read from, emptied on startup */
    public static File legacyGateShapes()
    {
        return PluginDirectory.resolve(PluginDirectory.PLUGIN_FOLDER, "GateShapes");
    }

    /** @return the directory holding everything this fork stores */
    public static File data()
    {
        return PluginDirectory.resolve(PluginDirectory.PLUGIN_FOLDER, DATA);
    }

    /** @return one YAML file per gate */
    public static File gates()
    {
        return PluginDirectory.resolve(PluginDirectory.PLUGIN_FOLDER, DATA, "gates");
    }

    /** @return one YAML file per world, holding every ring pair in it */
    public static File rings()
    {
        return PluginDirectory.resolve(PluginDirectory.PLUGIN_FOLDER, DATA, "rings");
    }

    /** @return the single file holding every beam destination and place */
    public static File beamFile()
    {
        return new File(data(), "beam.yml");
    }

    /**
     * The folder this fork's data used to share with another fork's database.
     *
     * <p>Still read, for two reasons that have nothing to do with each other: the importer
     * looks here for a foreign database, and the migration empties this fork's own files out
     * of it. Those are separate jobs on the same folder, which is precisely why the folder is
     * being split.
     *
     * @return the legacy directory, which on a new install never exists
     */
    public static File legacyData()
    {
        return PluginDirectory.resolve(PluginDirectory.PLUGIN_FOLDER, LEGACY_DATA);
    }

    /**
     * What moves out of {@link #legacyData()} and into {@link #data()}.
     *
     * <p>An explicit list rather than "everything in the folder", because the folder also
     * holds another fork's database. Sweeping the whole directory would move that too, and
     * the importer would then not find it -- turning a migration of our own files into the
     * loss of somebody's route back to their old gates.
     *
     * @return the names to move, relative to each directory
     */
    public static String[] migratableNames()
    {
        return new String[] { "gates", "rings", "beam.yml" };
    }
}
