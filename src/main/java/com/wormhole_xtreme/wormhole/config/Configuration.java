package com.wormhole_xtreme.wormhole.config;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.plugin.PluginDescriptionFile;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;

/**
 * The Class Configuration.
 * Bssed on class "Configuration" from MinecartMania by Afforess.
 */
public class Configuration
{
    /** Static helpers only; never instantiated. */
    private Configuration()
    {
    }


    /** The options. */
    private static File options = null;

    /**
     * Load configuration.
     */
    protected static void loadConfiguration(final String pluginName)
    {
        loadConfiguration(ConfigurationYAML.pluginDirectory(pluginName));
    }

    /**
     * Loads config.yml from the given directory, writing the defaults first on a fresh install.
     *
     * @param directory
     *            the plugin directory
     */
    static void loadConfiguration(final File directory)
    {
        final File yamlFile = new File(directory, "config.yml");
        if (!yamlFile.exists())
        {
            // Defaults in memory first, so a failed write still leaves a working server.
            for (final Setting s : DefaultSettings.config)
            {
                ConfigManager.getConfigurations().put(s.getName(), s);
            }
            // Logs its own failure rather than throwing.
            ConfigurationYAML.writeCurrentConfiguration(yamlFile);
            if (yamlFile.exists())
            {
                WormholeXTreme.getThisPlugin().prettyLog(java.util.logging.Level.INFO, "Created default config.yml at: " + yamlFile.getPath());
            }
            // Only here, where config.yml is first made: that is the moment an upgrade loses Settings.txt.
            LegacySettingsNotice.announce(yamlFile.getParentFile());
        }
        // Read back even a file just written: that read is what seeds and loads gate-material-groups.
        ConfigurationYAML.loadConfiguration(directory);
    }

    /**
     * Makes sure the options file is there to be written to.
     *
     * <p>Its own method rather than a try inside writeFile's try, and it does not rethrow:
     * the writer below creates the file itself if it has to, so a failure here is worth
     * saying out loud but is not a reason to abandon the write.
     */
    private static void createOptionsFileIfMissing()
    {
        try
        {
            if (!options.exists() && !options.createNewFile())
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.SEVERE, "Unable to create " + options.getPath());
            }
        }
        catch (final Exception e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.SEVERE, "Unable to create new file", e);
        }
    }

    /**
     * Write file.
     */
    public static void writeFile(final PluginDescriptionFile desc)
    {
        try
        {
            createOptionsFileIfMissing();
            try (BufferedWriter bufferedwriter = new BufferedWriter(new FileWriter(options, StandardCharsets.UTF_8)))
            {
                ConfigurationFlatFile.createNewHeader(bufferedwriter, desc.getName() + " " + desc.getVersion(), desc.getName() + " Config Settings", true);

                final Set<ConfigKeys> keys = ConfigManager.getConfigurations().keySet();
                final ArrayList<ConfigKeys> list = new ArrayList<>(keys);
                Collections.sort(list);
                for (final ConfigKeys key : list)
                {
                    final Setting s = ConfigManager.getConfigurations().get(key);
                    if (s != null)
                    {
                        ConfigurationFlatFile.createNewSetting(bufferedwriter, s.getName(), s.getValue().toString(), s.getDescription());
                    }
                }
            }
        }
        catch (final Exception exception)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.SEVERE, "Failed to write configuration file", exception);
        }
    }

    /**
     * Persist current runtime configuration to `config.yml`.
     */
    public static void persistCurrentConfiguration(final String pluginName)
    {
        try
        {
            ConfigurationYAML.writeCurrentConfiguration(ConfigurationYAML.getConfigFile(pluginName));
        }
        catch (final RuntimeException t)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "Failed to persist config.yml", t);
        }
    }

}
