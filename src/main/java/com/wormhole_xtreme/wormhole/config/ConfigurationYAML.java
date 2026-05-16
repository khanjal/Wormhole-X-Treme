/*
 * YAML-backed configuration loader for Wormhole X-Treme
 */
package com.wormhole_xtreme.wormhole.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
            final List<Setting> missing = new ArrayList<>();
            if (loaded instanceof Map)
            {
                @SuppressWarnings("unchecked")
                final Map<String, Object> map = (Map<String, Object>) loaded;
                for (final Setting element : DefaultSettings.config)
                {
                    final String enumKey = element.getName().name();
                    final String kebabKey = kebabKeyName(enumKey);
                    Object value = null;
                    if (map.containsKey(kebabKey))
                    {
                        value = map.get(kebabKey);
                    }
                    else if (map.containsKey(enumKey))
                    {
                        value = map.get(enumKey);
                    }

                    if (value != null)
                    {
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
                        // Key absent in file — use default in memory and queue for append
                        ConfigManager.getConfigurations().put(element.getName(), element);
                        missing.add(element);
                    }
                }
            }
            // Append any missing keys to the existing file so future runs load them normally
            if (!missing.isEmpty())
            {
                appendMissingSettings(cfg, missing);
            }
        }
        catch (final IOException e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.SEVERE, false, "Failed to read config.yml: " + e.getMessage());
        }
    }

    private static void appendMissingSettings(final File cfg, final List<Setting> missing)
    {
        try (final java.io.FileWriter writer = new java.io.FileWriter(cfg, true /* append */))
        {
            writer.write(System.lineSeparator());
            writer.write("# --- Added by WormholeXTreme (missing keys) ---" + System.lineSeparator());
            for (final Setting s : missing)
            {
                final String keyName = kebabKeyName(s.getName().name());
                if ((s.getDescription() != null) && (s.getDescription().length() > 0))
                {
                    for (final String wrapped : wrapComment(s.getDescription(), 80))
                    {
                        writer.write("# " + wrapped + System.lineSeparator());
                    }
                }
                writer.write(keyName + ": " + formatValueForYaml(s.getValue()) + System.lineSeparator());
                writer.write(System.lineSeparator());
            }
            final List<String> names = new java.util.ArrayList<>();
            for (final Setting s : missing) { names.add(s.getName().name()); }
            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, false,
                "config.yml was missing " + missing.size() + " key(s); added defaults: " + names.toString());
        }
        catch (final IOException e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false,
                "Failed to append missing config keys: " + e.getMessage());
        }
    }

    protected static void writeFile(final File file, final PluginDescriptionFile desc, final Setting[] config)
    {
        try
        {
            final File directory = new File("plugins" + File.separator + desc.getName() + File.separator);
            if (!directory.exists())
            {
                directory.mkdir();
            }
            try (final FileWriter writer = new FileWriter(file))
            {
                for (final Setting s : config)
                {
                    final String keyName = kebabKeyName(s.getName().name());
                    // (Legacy build-group keys removed; nothing to skip here.)
                    // Write comment description
                    if ((s.getDescription() != null) && (s.getDescription().length() > 0))
                    {
                        for (final String wrapped : wrapComment(s.getDescription(), 80))
                        {
                            writer.write("# " + wrapped + System.lineSeparator());
                        }
                    }
                    writer.write(keyName + ": " + formatValueForYaml(s.getValue()) + System.lineSeparator());
                    writer.write(System.lineSeparator());
                }
            }
        }
        catch (final Exception e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.SEVERE, false, "Failed to write config.yml: " + e.getMessage());
        }
    }

    /**
     * Write current runtime configuration (from ConfigManager) to YAML.
     */
    protected static void writeCurrentConfiguration(final File file, final PluginDescriptionFile desc)
    {
        try
        {
            final File directory = new File("plugins" + File.separator + desc.getName() + File.separator);
            if (!directory.exists())
            {
                directory.mkdir();
            }
            try (final FileWriter writer = new FileWriter(file))
            {
                final Setting[] defaults = com.wormhole_xtreme.wormhole.config.DefaultSettings.config;
                final java.util.List<String> skipped = new java.util.ArrayList<>();
                for (final Setting def : defaults)
                {
                    final com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys key = def.getName();
                    // Skip permission backend settings entirely
                    if (key == com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys.BUILT_IN_PERMISSIONS_ENABLED
                        || key == com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys.BUILT_IN_DEFAULT_PERMISSION_LEVEL
                        || key == com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys.PERMISSIONS_SUPPORT_DISABLE)
                    {
                        skipped.add(key.name());
                        continue;
                    }
                    // (Legacy build-group keys removed; nothing to skip here.)
                    final Setting runtime = com.wormhole_xtreme.wormhole.config.ConfigManager.getConfigurations().get(key);
                    final Object value = (runtime != null) ? runtime.getValue() : def.getValue();
                    final String keyName = kebabKeyName(key.name());
                    // write description comment
                    if ((def.getDescription() != null) && (def.getDescription().length() > 0))
                    {
                        for (final String wrapped : wrapComment(def.getDescription(), 80))
                        {
                            writer.write("# " + wrapped + System.lineSeparator());
                        }
                    }
                    writer.write(keyName + ": " + formatValueForYaml(value) + System.lineSeparator());
                    writer.write(System.lineSeparator());
                        // Always write storage backend configuration to config.yml so CLI changes can persist when requested
                        if (key == com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys.STORAGE_BACKEND || 
                            key == com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys.STORAGE_SQLITE_PATH || 
                            key == com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys.STORAGE_JDBC_URL || 
                            key == com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys.STORAGE_JDBC_USER || 
                            key == com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys.STORAGE_JDBC_PASSWORD)
                        {
                            // fall through and write these keys
                        }
                }
                if (!skipped.isEmpty())
                {
                    WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, false, "Skipped migrating permission keys: " + skipped.toString());
                }
            }
        }
        catch (final Exception e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.SEVERE, false, "Failed to write migrated config.yml: " + e.getMessage());
        }
    }

    private static String kebabKeyName(final String constantName)
    {
        return constantName.toLowerCase().replace('_', '-');
    }

    private static String formatValueForYaml(final Object value)
    {
        if (value == null)
        {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean)
        {
            return value.toString();
        }
        // For enum types or strings, quote strings that may contain special characters
        final String s = value.toString();
        if (s.matches("^[a-zA-Z0-9_\\-]+$"))
        {
            return s;
        }
        return '"' + s.replace("\"", "\\\"") + '"';
    }

    private static java.util.List<String> wrapComment(final String text, final int width)
    {
        final java.util.List<String> out = new java.util.ArrayList<>();
        if (text == null)
        {
            return out;
        }
        final String[] paragraphs = text.split("\\n");
        for (final String para : paragraphs)
        {
            final String[] words = para.split("\\s+");
            StringBuilder line = new StringBuilder();
            for (final String w : words)
            {
                if (line.length() == 0)
                {
                    line.append(w);
                }
                else if (line.length() + 1 + w.length() <= width - 2)
                {
                    line.append(' ').append(w);
                }
                else
                {
                    out.add(line.toString());
                    line = new StringBuilder(w);
                }
            }
            if (line.length() > 0)
            {
                out.add(line.toString());
            }
            // preserve paragraph break
            out.add("");
        }
        // remove trailing empty line if present
        if (!out.isEmpty() && out.get(out.size() - 1).isEmpty())
        {
            out.remove(out.size() - 1);
        }
        return out;
    }
}
