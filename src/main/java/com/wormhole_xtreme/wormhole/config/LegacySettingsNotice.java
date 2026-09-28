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

    static final long MAX_BYTES = 1024L * 1024L;

    /**
     * Names this file shares with a setting that is not the same setting now. In 2012 disabling
     * permissions support meant not attaching to the old Permissions plugin; now it means ignoring
     * LuckPerms and Vault. Nothing reads the Help plugin setting any more.
     */
    private static final java.util.Set<String> NOT_THE_SAME_SETTING =
        java.util.Set.of("PERMISSIONS_SUPPORT_DISABLE", "HELP_SUPPORT_DISABLE");

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
        final WormholeXTreme plugin = WormholeXTreme.getThisPlugin();
        final File legacy = new File(pluginDirectory, LEGACY_FILE);
        // A real one is a few kilobytes; anything far bigger is not that file.
        if ((plugin == null) || !legacy.isFile() || (legacy.length() > MAX_BYTES))
        {
            return;
        }
        // Runs during plugin load, so nothing it throws may reach the caller: it is only a note.
        try
        {
            // ISO-8859-1 reads any byte, and the file predates any promise of UTF-8.
            final List<String> lines = Files.readAllLines(legacy.toPath(), StandardCharsets.ISO_8859_1);
            final List<String> differing = carryOver(lines, DefaultSettings.config);
            plugin.prettyLog(Level.INFO, "Found " + LEGACY_FILE
                + " from an older Wormhole X-Treme; it is not read, config.yml replaces it. "
                + (differing.isEmpty()
                    ? "None of its settings that still exist differ from the defaults."
                    : "Set these again in config.yml to keep them: " + String.join(", ", differing) + "."));
        }
        catch (final IOException | RuntimeException e)
        {
            plugin.prettyLog(Level.FINE, "Could not read " + LEGACY_FILE, e);
        }
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
                final Setting current = NOT_THE_SAME_SETTING.contains(name) ? null : find(defaults, name);
                final String value = (current == null) ? null
                    : asConfigValue(current.getValue(), line.substring("Value:".length()).trim());
                if ((value != null) && !value.equals(String.valueOf(current.getValue())))
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

    /**
     * The value as config.yml has to spell it for a setting of the default's type, or null if it
     * could not be one. Pasted as the old file wrote it, 45.0 for a whole-number setting fails on
     * first use, and a lower-case log level fails at enable.
     */
    static String asConfigValue(final Object defaultValue, final String written)
    {
        try
        {
            if (defaultValue instanceof Integer)
            {
                final double number = Double.parseDouble(written);
                return (number == Math.rint(number)) && (Math.abs(number) <= Integer.MAX_VALUE)
                    ? String.valueOf((int) number) : null;
            }
            if (defaultValue instanceof Double)
            {
                return String.valueOf(Double.parseDouble(written));
            }
        }
        catch (final NumberFormatException notANumber)
        {
            return null;
        }
        if (defaultValue instanceof Boolean)
        {
            final String flag = written.toLowerCase(Locale.ROOT);
            return ("true".equals(flag) || "false".equals(flag)) ? flag : null;
        }
        final String text = String.valueOf(defaultValue);
        // An upper-case default is a constant name, such as a log level, and is only read in capitals.
        return text.equals(text.toUpperCase(Locale.ROOT)) ? written.toUpperCase(Locale.ROOT) : written;
    }
}
