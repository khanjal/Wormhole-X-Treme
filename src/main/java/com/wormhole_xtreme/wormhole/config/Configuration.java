package com.wormhole_xtreme.wormhole.config;

import java.io.File;
import java.util.logging.Level;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

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
                WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, "Created default config.yml at: " + yamlFile.getPath());
            }
            // Only here, where config.yml is first made: that is the moment an upgrade loses Settings.txt.
            LegacySettingsNotice.announce(yamlFile.getParentFile());
        }
        // Read back even a file just written: that read is what seeds and loads gate-material-groups.
        ConfigurationYAML.loadConfiguration(directory);
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
