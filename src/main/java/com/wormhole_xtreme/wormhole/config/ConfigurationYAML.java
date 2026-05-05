/*
 * YAML-backed configuration loader for Wormhole X-Treme
 */
package com.wormhole_xtreme.wormhole.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.plugin.PluginDescriptionFile;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.permissions.PermissionsManager.PermissionLevel;

/**
 * Loads and writes plugin configuration via YAML (`config.yml`).
 */
public class ConfigurationYAML
{

    protected static void loadConfiguration(final PluginDescriptionFile desc)
    {
        final File directory = new File("plugins" + File.separator + desc.getName() + File.separator);
        if (!directory.exists())
        {
            directory.mkdir();
        }

        final File cfg = new File(directory, "config.yml");
        if (!cfg.exists())
        {
            // write default file
            writeFile(cfg, desc, DefaultSettings.config);
        }

        try (InputStream in = new FileInputStream(cfg))
        {
            final Yaml yaml = new Yaml();
            final Object loaded = yaml.load(in);
            if (loaded instanceof Map)
            {
                @SuppressWarnings("unchecked")
                final Map<String, Object> map = (Map<String, Object>) loaded;
                for (final Setting element : DefaultSettings.config)
                {
                    final String key = element.getName().name();
                    if (map.containsKey(key))
                    {
                        final Object value = map.get(key);
                        Setting s = null;
                        if (value instanceof Boolean)
                        {
                            s = new Setting(element.getName(), (Boolean) value, element.getDescription(), "WormholeXTreme");
                        }
                        else if (value instanceof Integer)
                        {
                            s = new Setting(element.getName(), (Integer) value, element.getDescription(), "WormholeXTreme");
                        }
                        else if (value instanceof Number)
                        {
                            s = new Setting(element.getName(), ((Number) value).doubleValue(), element.getDescription(), "WormholeXTreme");
                        }
                        else if (element.getName() == ConfigKeys.BUILT_IN_DEFAULT_PERMISSION_LEVEL)
                        {
                            try
                            {
                                s = new Setting(element.getName(), PermissionLevel.valueOf(value.toString()), element.getDescription(), "WormholeXTreme");
                            }
                            catch (final Exception e)
                            {
                                s = new Setting(element.getName(), element.getValue(), element.getDescription(), "WormholeXTreme");
                            }
                        }
                        else
                        {
                            s = new Setting(element.getName(), value.toString(), element.getDescription(), "WormholeXTreme");
                        }
                        ConfigManager.getConfigurations().put(s.getName(), s);
                    }
                    else
                    {
                        // fallback to defaults
                        ConfigManager.getConfigurations().put(element.getName(), element);
                    }
                }
            }
        }
        catch (final IOException e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.SEVERE, false, "Failed to read config.yml: " + e.getMessage());
        }
    }

    protected static void writeFile(final File file, final PluginDescriptionFile desc, final Setting[] config)
    {
        try
        {
            final Map<String, Object> out = new HashMap<>();
            for (final Setting s : config)
            {
                out.put(s.getName().name(), s.getValue());
            }

            final DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setIndent(2);
            final Yaml yaml = new Yaml(options);

            try (FileWriter writer = new FileWriter(file))
            {
                yaml.dump(out, writer);
            }
        }
        catch (final Exception e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.SEVERE, false, "Failed to write config.yml: " + e.getMessage());
        }
    }
}
