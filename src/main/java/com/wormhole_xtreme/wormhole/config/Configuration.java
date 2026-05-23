/*
 *   Wormhole X-Treme Plugin for Bukkit
 *   Copyright (C) 2011  Ben Echols
 *                       Dean Bailey
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.wormhole_xtreme.wormhole.config;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.plugin.PluginDescriptionFile;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.permissions.PermissionsManager.PermissionLevel;

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
                WormholeXTreme.getThisPlugin().prettyLog(java.util.logging.Level.INFO, false, "Created default config.yml at: " + yamlFile.getPath());
            }
            catch (final Throwable t)
            {
                WormholeXTreme.getThisPlugin().prettyLog(java.util.logging.Level.WARNING, false, "Failed to write default config.yml: " + t.getMessage());
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
                WormholeXTreme.getThisPlugin().prettyLog(Level.SEVERE, false, "Unable to create new file: " + e.getMessage());
            }
            final BufferedWriter bufferedwriter = new BufferedWriter(new FileWriter(options));

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
            bufferedwriter.close();
        }
        catch (final Exception exception)
        {
            exception.printStackTrace();
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
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Failed to persist config.yml: " + t.getMessage());
        }
    }

}
