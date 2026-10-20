package com.wormhole_xtreme.wormhole.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;

import org.bukkit.command.CommandSender;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.MarkedYAMLException;
import org.yaml.snakeyaml.error.YAMLException;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.utils.Messages;
import com.wormhole_xtreme.wormhole.utils.YamlMaps;

/**
 * {@code /wormhole config -reload}: reads config.yml again and takes each setting it changes, the
 * way {@code /wormhole config <setting> <value>} would.
 *
 * <p>The reload does not write the file, though a change it applies may, as the command's would:
 * turning on {@code gate-material-groups-autodiscover} appends the groups it finds. A setting the
 * file leaves out keeps its value rather than going back to the default, a value the command would
 * refuse keeps the old one, and a file that cannot be read changes nothing at all. The material
 * groups themselves are not settings and wait for a restart.
 */
public final class ConfigReload
{
    /** Text settings whose getters read a bare off, which YAML hands over as false, as their default. */
    private static final Set<ConfigKeys> OFF_MEANS_DEFAULT =
        EnumSet.of(ConfigKeys.GATE_VIEW, ConfigKeys.GATE_IRIS_ANIMATION);

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

    /** A refused value, its reason rendered only once the reload is done. */
    private record Refused(ConfigKeys key, Function<CommandSender, String> refusal, Object kept)
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
     * Reloads this server's config.yml, its report in the server's language.
     *
     * @return what changed
     */
    public static Report reload()
    {
        return reload((CommandSender) null);
    }

    /**
     * Reloads this server's config.yml.
     *
     * @param recipient
     *            who will read the report; null for the server's language
     * @return what changed
     */
    public static Report reload(final CommandSender recipient)
    {
        return reload(recipient, ConfigurationYAML.getConfigFile(ConfigManager.configuredPluginName()));
    }

    /** As {@link #reload(CommandSender, File)}, its report in the server's language. */
    static Report reload(final File cfg)
    {
        return reload(null, cfg);
    }

    /**
     * Reloads the settings from one file, and the language file whether or not {@code server-language}
     * changed, so an edited translation is taken too.
     *
     * @param recipient
     *            who will read the report; null for the server's language
     * @param cfg
     *            the config file
     * @return what changed
     */
    static Report reload(final CommandSender recipient, final File cfg)
    {
        final String name = cfg.getName();
        final Map<String, Object> map;
        try (InputStream in = Files.newInputStream(cfg.toPath()))
        {
            final Object loaded = new Yaml().load(in);
            if (loaded == null)
            {
                return unreadable(Messages.get(recipient, "config.reload.empty", name));
            }
            if (!(loaded instanceof Map))
            {
                return unreadable(Messages.get(recipient, "config.reload.not-settings", name));
            }
            map = YamlMaps.asMap(loaded);
        }
        catch (final NoSuchFileException e)
        {
            return unreadable(Messages.get(recipient, "config.reload.missing", name));
        }
        catch (final IOException e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "Could not reload " + cfg, e);
            return unreadable(Messages.get(recipient, "config.reload.not-read", name));
        }
        catch (final YAMLException e)
        {
            return unreadable(Messages.get(recipient, "config.reload.not-yaml", name, problem(recipient, e)));
        }

        final List<Change> changes = new ArrayList<>();
        final List<Refused> refused = new ArrayList<>();
        for (final Setting element : DefaultSettings.config)
        {
            final Setting current = ConfigManager.getConfigurations().get(element.getName());
            final String key = ConfigurationYAML.keyFor(map, element);
            final Object value = (key == null) ? null : map.get(key);
            if ((current != null) && (value != null))
            {
                take(element, current, value, changes, refused);
            }
        }
        // A changed language was read as it was taken; read it once either way.
        if (changes.stream().noneMatch(change -> change.key() == ConfigKeys.SERVER_LANGUAGE))
        {
            Messages.load();
        }
        // Said only now, so a server-language the reload changed reads in every line.
        return new Report(null, changes,
            refused.stream().map(r -> new Rejection(r.key(), r.refusal().apply(recipient), r.kept())).toList());
    }

    /**
     * Takes one value from the file, in the form the command would store it, if it differs from
     * what is set; a value already stored that the command would refuse is reported every time.
     */
    private static void take(final Setting element, final Setting current, final Object written,
        final List<Change> changes, final List<Refused> refused)
    {
        final ConfigKeys key = current.getName();
        final Object read = ConfigurationYAML.readFrom(element, written);
        if (read == null)
        {
            refused.add(new Refused(key, reader -> ParsedSetting.notOfKind(reader, key, element.getValue(), written),
                current.getValue()));
            return;
        }
        ParsedSetting parsed = ParsedSetting.read(key, current.getValue(), String.valueOf(read));
        // YAML reads a bare off or on as a switch; startup stores its text, which reads as the default.
        if (!parsed.isAccepted() && (written instanceof Boolean) && !(element.getValue() instanceof Boolean))
        {
            if (!OFF_MEANS_DEFAULT.contains(key))
            {
                refused.add(new Refused(key, reader -> Messages.get(reader, "config.reload.switch", key, written),
                    current.getValue()));
                return;
            }
            if (Objects.equals(current.getValue(), read))
            {
                return;
            }
            parsed = ParsedSetting.read(key, current.getValue(), String.valueOf(element.getValue()));
        }
        if (!parsed.isAccepted())
        {
            refused.add(new Refused(key, parsed::getRefusal, current.getValue()));
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
    private static String problem(final CommandSender recipient, final YAMLException e)
    {
        if ((e instanceof MarkedYAMLException marked) && (marked.getProblemMark() != null))
        {
            return Messages.get(recipient, "config.reload.on-line", marked.getProblem(),
                marked.getProblemMark().getLine() + 1);
        }
        return String.valueOf(e.getMessage()).lines().findFirst().orElse("");
    }

    private static Report unreadable(final String why)
    {
        return new Report(why, List.of(), List.of());
    }
}
