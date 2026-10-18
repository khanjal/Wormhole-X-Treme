package com.wormhole_xtreme.wormhole.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.MarkedYAMLException;
import org.yaml.snakeyaml.error.YAMLException;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.utils.YamlMaps;

/**
 * {@code /wormhole config reload}: reads config.yml again and takes each setting it changes, the
 * way {@code /wormhole config <setting> <value>} would.
 *
 * <p>Only values in memory change. The file is not written, a setting it leaves out keeps its value
 * rather than going back to the default, a value the command would refuse keeps the old one, and a
 * file that cannot be read changes nothing at all. Material groups are not settings and wait for a
 * restart.
 */
public final class ConfigReload
{
    /** Static helpers only. */
    private ConfigReload()
    {
    }

    /**
     * One setting the reload changed.
     *
     * @param key
     *            the setting
     * @param from
     *            what it was
     * @param to
     *            what it is now
     * @param unapplied
     *            why the change could not be applied at once, or null if it was
     */
    public record Change(ConfigKeys key, Object from, Object to, String unapplied)
    {
    }

    /**
     * One value in the file the reload would not take.
     *
     * @param key
     *            the setting
     * @param refusal
     *            why, in the words {@code /wormhole config} uses
     * @param kept
     *            the value it keeps
     */
    public record Rejection(ConfigKeys key, String refusal, Object kept)
    {
    }

    /**
     * What a reload did.
     *
     * @param unreadable
     *            why the file could not be read, in which case nothing changed; null if it was read
     * @param changes
     *            the settings changed
     * @param rejections
     *            the values refused
     */
    public record Report(String unreadable, List<Change> changes, List<Rejection> rejections)
    {
    }

    /**
     * Reloads this server's config.yml.
     *
     * @return what changed
     */
    public static Report reload()
    {
        return reload(ConfigurationYAML.getConfigFile(ConfigManager.configuredPluginName()));
    }

    /**
     * Reloads the settings from one file.
     *
     * @param cfg
     *            the config file
     * @return what changed
     */
    static Report reload(final File cfg)
    {
        final Map<String, Object> map;
        try (InputStream in = Files.newInputStream(cfg.toPath()))
        {
            final Object loaded = new Yaml().load(in);
            if (!(loaded instanceof Map))
            {
                return unreadable(cfg.getName() + " is not a list of settings");
            }
            map = YamlMaps.asMap(loaded);
        }
        catch (final IOException e)
        {
            return unreadable(cfg.getName() + " could not be read: " + e);
        }
        catch (final YAMLException e)
        {
            return unreadable(cfg.getName() + " is not valid YAML: " + problem(e));
        }

        final List<Change> changes = new ArrayList<>();
        final List<Rejection> rejections = new ArrayList<>();
        for (final Setting element : DefaultSettings.config)
        {
            final Setting current = ConfigManager.getConfigurations().get(element.getName());
            final String key = ConfigurationYAML.keyFor(map, element);
            final Object value = (key == null) ? null : map.get(key);
            if ((current != null) && (value != null))
            {
                take(current, ConfigurationYAML.asKindOf(element.getValue(), value), value, changes, rejections);
            }
        }
        return new Report(null, changes, rejections);
    }

    /** Takes one value from the file, if it is a change the command would accept. */
    private static void take(final Setting current, final Object read, final Object written,
        final List<Change> changes, final List<Rejection> rejections)
    {
        if (Objects.equals(current.getValue(), read))
        {
            return;
        }
        final ConfigKeys key = current.getName();
        final ParsedSetting parsed =
            ParsedSetting.read(key, current.getValue(), String.valueOf((read == null) ? written : read));
        if (!parsed.isAccepted())
        {
            rejections.add(new Rejection(key, parsed.getRefusal(), current.getValue()));
            return;
        }
        final Object from = current.getValue();
        if (Objects.equals(from, parsed.getValue()))
        {
            return;
        }
        current.setValue(parsed.getValue());
        String unapplied = null;
        // Taken already; a failure to apply it must say so, not stop the settings after it.
        try
        {
            ConfigManager.follow(key);
        }
        catch (final RuntimeException | LinkageError e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "Could not apply " + key, e);
            unapplied = e.toString();
        }
        changes.add(new Change(key, from, parsed.getValue(), unapplied));
    }

    /** What the parser objected to and where, on one line. */
    private static String problem(final YAMLException e)
    {
        if ((e instanceof MarkedYAMLException marked) && (marked.getProblemMark() != null))
        {
            return marked.getProblem() + " on line " + (marked.getProblemMark().getLine() + 1);
        }
        return String.valueOf(e.getMessage()).lines().findFirst().orElse("");
    }

    private static Report unreadable(final String why)
    {
        return new Report(why, List.of(), List.of());
    }
}
