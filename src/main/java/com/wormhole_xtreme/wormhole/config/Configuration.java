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
     * 
     * @param desc
     *            the desc
     */
    protected static void loadConfiguration(final String pluginName)
    {
        // Prefer YAML config if present, otherwise fall back to legacy flat file.
        final File yamlFile = ConfigurationYAML.getConfigFile(pluginName);
        if (yamlFile.exists())
        {
            ConfigurationYAML.loadConfiguration(pluginName);
        }
        else
        {
            // No YAML present: initialize runtime config with defaults and
            // write a new `config.yml`. We no longer read or generate Settings.txt.
            for (final Setting s : DefaultSettings.config)
            {
                ConfigManager.getConfigurations().put(s.getName(), s);
            }
            try
            {
                ConfigurationYAML.writeCurrentConfiguration(yamlFile);
                WormholeXTreme.getThisPlugin().prettyLog(java.util.logging.Level.INFO, "Created default config.yml at: " + yamlFile.getPath());
            }
            catch (final RuntimeException t)
            {
                WormholeXTreme.getThisPlugin().prettyLog(java.util.logging.Level.WARNING, "Failed to write default config.yml: " + t.getMessage());
            }
        }
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
            WormholeXTreme.getThisPlugin().prettyLog(Level.SEVERE, "Unable to create new file: " + e.getMessage());
        }
    }

    /**
     * Write file.
     * 
     * @param desc
     *            the desc
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
                final ArrayList<ConfigKeys> list = new ArrayList<ConfigKeys>(keys);
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
            WormholeXTreme.getThisPlugin().prettyLog(Level.SEVERE, "Failed to write configuration file: " + exception.getMessage());
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
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "Failed to persist config.yml: " + t.getMessage());
        }
    }

}
