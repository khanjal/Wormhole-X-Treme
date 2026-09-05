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
        final File yamlFile = new File("plugins" + File.separator + pluginName + File.separator + "config.yml");
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
                ConfigurationYAML.writeCurrentConfiguration(yamlFile, pluginName);
                WormholeXTreme.getThisPlugin().prettyLog(java.util.logging.Level.INFO, "Created default config.yml at: " + yamlFile.getPath());
            }
            catch (final Throwable t)
            {
                WormholeXTreme.getThisPlugin().prettyLog(java.util.logging.Level.WARNING, "Failed to write default config.yml: " + t.getMessage());
            }
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
            try
            {
                options.createNewFile();
            }
            catch (final Exception e)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.SEVERE, "Unable to create new file: " + e.getMessage());
            }
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
            final java.io.File yamlFile = new java.io.File("plugins" + java.io.File.separator + pluginName + java.io.File.separator + "config.yml");
            ConfigurationYAML.writeCurrentConfiguration(yamlFile, pluginName);
        }
        catch (final Throwable t)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "Failed to persist config.yml: " + t.getMessage());
        }
    }

}
