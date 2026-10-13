package com.wormhole_xtreme.wormhole.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.logging.Level;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.utils.YamlMaps;

/**
 * Replaces a config.yml nobody ever changed with the current layout (#599).
 *
 * <p>An existing file keeps the layout it was written in, so an untouched 1.10.0 one would never
 * see the grouped file or a changed default. Untouched means every value is what 1.10.0 wrote,
 * judged on values alone, so comments and order do not matter. Anything else is left exactly as it is.
 */
final class PristineConfig
{
    /** What 1.10.0 wrote into a new file, including its material groups. */
    private static final String DEFAULTS_1_10_0 = "config-defaults-1.10.0.yml";

    private static final int MAX_BACKUPS = 100;

    /** Static helpers only; never instantiated. */
    private PristineConfig()
    {
    }

    /**
     * Whether a file holds only what 1.10.0 wrote, or that plus settings added since at their defaults.
     *
     * @param file
     *            the parsed config file
     * @return false for any other value, any key no setting answers to, or a 1.10.0 key gone missing
     */
    static boolean isUntouched(final Map<String, Object> file)
    {
        final Map<String, Object> shipped = shipped110();
        if (shipped.isEmpty() || !file.keySet().containsAll(shipped.keySet()))
        {
            return false;
        }
        for (final Map.Entry<String, Object> entry : file.entrySet())
        {
            final Object expected = shipped.containsKey(entry.getKey())
                ? shipped.get(entry.getKey()) : defaultOf(entry.getKey());
            if ((expected == null) || !sameValue(expected, entry.getValue()))
            {
                return false;
            }
        }
        return true;
    }

    /** The default a setting added after 1.10.0 has now, or null if the key is no setting. */
    private static Object defaultOf(final String key)
    {
        for (final Setting setting : DefaultSettings.config)
        {
            if (ConfigurationYAML.kebabKeyName(setting.getName().name()).equals(key))
            {
                return setting.getValue();
            }
        }
        return null;
    }

    private static Map<String, Object> shipped110()
    {
        try (InputStream in = PristineConfig.class.getClassLoader().getResourceAsStream(DEFAULTS_1_10_0))
        {
            return (in == null) ? Map.of() : YamlMaps.asMap(new Yaml().load(in));
        }
        catch (final IOException | YAMLException e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "Could not read the 1.10.0 defaults; config.yml is left as it is", e);
            return Map.of();
        }
    }

    /** Equal values, a whole number being the same as that number written with a point. */
    private static boolean sameValue(final Object expected, final Object actual)
    {
        if ((expected instanceof Number a) && (actual instanceof Number b))
        {
            return Double.parseDouble(a.toString()) == Double.parseDouble(b.toString());
        }
        if ((expected instanceof Map<?, ?> a) && (actual instanceof Map<?, ?> b))
        {
            return a.size() == b.size() && a.entrySet().stream()
                .allMatch(e -> b.containsKey(e.getKey()) && sameValue(e.getValue(), b.get(e.getKey())));
        }
        return expected.equals(actual);
    }

    /**
     * Writes the current default file over an untouched one, after copying it aside.
     *
     * <p>Nothing happens if the file is not a mapping, is customised, or cannot be copied first. The
     * new file is written beside it and moved over, so a failed write leaves the old one.
     *
     * @param cfg
     *            the existing config.yml
     */
    static void replaceIfUntouched(final File cfg)
    {
        try (InputStream in = Files.newInputStream(cfg.toPath()))
        {
            final Object loaded = new Yaml().load(in);
            if (!(loaded instanceof Map) || !isUntouched(YamlMaps.asMap(loaded)))
            {
                return;
            }
        }
        catch (final IOException | YAMLException e)
        {
            return; // the load that follows reports an unreadable file
        }

        final Path backup;
        final File fresh = new File(cfg.getParentFile(), cfg.getName() + ".new");
        try
        {
            backup = copyAside(cfg.toPath());
            ConfigurationYAML.writeFile(fresh, DefaultSettings.config);
            if (!fresh.isFile() || (fresh.length() == 0))
            {
                throw new IOException("the new file was not written");
            }
            Files.move(fresh.toPath(), cfg.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        catch (final IOException e)
        {
            discard(fresh);
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING,
                "config.yml holds only the 1.10.0 defaults but could not be replaced with the new layout; it is left as it is", e);
            return;
        }
        WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, "config.yml was unchanged from 1.10.0, so it was replaced with the new"
            + " layout; the old file is kept as " + backup.getFileName() + ", and any comment you added is only there.");
    }

    private static void discard(final File leftover)
    {
        try
        {
            Files.deleteIfExists(leftover.toPath());
        }
        catch (final IOException ignored)
        {
            // a stray config.yml.new is harmless; the next start overwrites it
        }
    }

    /** Copies the file to config.yml.bak, or the first .bak.N free, never over an existing one. */
    private static Path copyAside(final Path cfg) throws IOException
    {
        for (int n = 0; n < MAX_BACKUPS; n++)
        {
            final Path target = cfg.resolveSibling(cfg.getFileName() + ".bak" + ((n == 0) ? "" : ("." + n)));
            try
            {
                return Files.copy(cfg, target);
            }
            catch (final FileAlreadyExistsException taken)
            {
                // try the next name
            }
        }
        throw new IOException("no free name for a backup of " + cfg.getFileName());
    }
}
