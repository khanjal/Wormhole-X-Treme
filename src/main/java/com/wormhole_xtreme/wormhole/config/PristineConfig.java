package com.wormhole_xtreme.wormhole.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.utils.YamlMaps;

/**
 * Replaces a config.yml nobody ever changed with the current layout (#599).
 *
 * <p>An existing file keeps the layout it was written in, so an untouched one from an earlier release
 * would never see a newer layout or a changed default. Untouched means every value is what one of
 * the {@link #RELEASES} wrote,
 * judged on values alone, so comments and order do not matter. Anything else is left exactly as it is.
 */
final class PristineConfig
{
    /**
     * Releases whose new-file defaults are frozen in {@code config-defaults-<release>.yml}, material groups
     * included. When a release changes the layout or a default, add the release it replaces here, with its
     * snapshot; never the current one, or an untouched current file would be rewritten every start.
     */
    static final List<String> RELEASES = List.of("1.10.0");

    private static final int MAX_BACKUPS = 100;

    /** Static helpers only; never instantiated. */
    private PristineConfig()
    {
    }

    /**
     * The release whose defaults a file still holds, if any.
     *
     * @param file
     *            the parsed config file
     * @return the first of {@link #RELEASES} it matches, or null
     */
    static String untouchedSince(final Map<String, Object> file)
    {
        return untouchedSince(file, RELEASES);
    }

    /** As {@link #untouchedSince(Map)}, against the given releases. */
    static String untouchedSince(final Map<String, Object> file, final List<String> releases)
    {
        return releases.stream().filter(release -> isUntouched(file, release)).findFirst().orElse(null);
    }

    /**
     * Whether a file holds only what a release wrote, or that plus settings added since at their defaults.
     *
     * @return false for any other value, any key no setting answers to, or a key of that release gone missing
     */
    private static boolean isUntouched(final Map<String, Object> file, final String release)
    {
        final Map<String, Object> shipped = shipped(release);
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

    /** The default a setting added since has now, or null if the key is no setting. */
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

    private static Map<String, Object> shipped(final String release)
    {
        try (InputStream in = PristineConfig.class.getClassLoader().getResourceAsStream("config-defaults-" + release + ".yml"))
        {
            return (in == null) ? Map.of() : YamlMaps.asMap(new Yaml().load(in));
        }
        catch (final IOException | YAMLException e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "Could not read the " + release + " defaults; config.yml is left as it is", e);
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
        final String release;
        try (InputStream in = Files.newInputStream(cfg.toPath()))
        {
            final Object loaded = new Yaml().load(in);
            release = (loaded instanceof Map) ? untouchedSince(YamlMaps.asMap(loaded)) : null;
            if (release == null)
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
                "config.yml holds only the " + release + " defaults but could not be replaced with the new layout; it is left as it is", e);
            return;
        }
        WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, "config.yml was unchanged from " + release + ", so it was replaced with the new"
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
