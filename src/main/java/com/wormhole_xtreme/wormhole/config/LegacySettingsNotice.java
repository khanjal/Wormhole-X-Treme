package com.wormhole_xtreme.wormhole.config;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * Tells an operator upgrading from a Settings.txt-era build what was in it that config.yml will not have.
 *
 * <p>Settings.txt is not read. This only names the settings in it that still exist and are not at
 * their default, so they can be set again by hand.
 */
final class LegacySettingsNotice
{
    static final String LEGACY_FILE = "Settings.txt";

    private LegacySettingsNotice()
    {
    }

    /**
     * Logs one line if the plugin folder holds a Settings.txt.
     *
     * @param pluginDirectory
     *            the plugin's data folder
     */
    static void announce(final File pluginDirectory)
    {
        final File legacy = new File(pluginDirectory, LEGACY_FILE);
        if (!legacy.isFile())
        {
            return;
        }
        final List<String> lines;
        try
        {
            // ISO-8859-1 reads any byte, and the file predates any promise of UTF-8.
            lines = Files.readAllLines(legacy.toPath(), StandardCharsets.ISO_8859_1);
        }
        catch (final IOException e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "Could not read " + LEGACY_FILE, e);
            return;
        }
        final List<String> differing = carryOver(lines, DefaultSettings.config);
        WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, "Found " + LEGACY_FILE
            + " from an older Wormhole X-Treme; it is not read, config.yml replaces it. "
            + (differing.isEmpty()
                ? "None of its settings that still exist differ from the defaults."
                : "Set these again in config.yml to keep them: " + String.join(", ", differing) + "."));
    }

    /**
     * The settings in a Settings.txt that still exist and differ from their default, as config.yml lines.
     *
     * @param lines
     *            the file, a "Setting: NAME" line followed by a "Value: v" line for each setting
     * @param defaults
     *            the current settings with their default values
     * @return "key: value" for each, in file order
     */
    static List<String> carryOver(final List<String> lines, final Setting[] defaults)
    {
        final List<String> differing = new ArrayList<>();
        String name = null;
        for (final String line : lines)
        {
            if (line.startsWith("Setting:"))
            {
                name = line.substring("Setting:".length()).trim();
            }
            else if (line.startsWith("Value:") && (name != null))
            {
                final String value = line.substring("Value:".length()).trim();
                final Setting current = find(defaults, name);
                if ((current != null) && !sameValue(current.getValue(), value))
                {
                    differing.add(ConfigurationYAML.kebabKeyName(name) + ": " + value);
                }
                name = null;
            }
        }
        return differing;
    }

    private static Setting find(final Setting[] defaults, final String name)
    {
        for (final Setting s : defaults)
        {
            if (s.getName().name().equals(name))
            {
                return s;
            }
        }
        return null;
    }

    private static boolean sameValue(final Object defaultValue, final String written)
    {
        return String.valueOf(defaultValue).toLowerCase(Locale.ROOT).equals(written.toLowerCase(Locale.ROOT));
    }
}
