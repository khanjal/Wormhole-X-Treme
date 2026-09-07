/*
 * YAML-backed configuration loader for Wormhole X-Treme
 */
package com.wormhole_xtreme.wormhole.config;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import org.yaml.snakeyaml.Yaml;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.MaterialGroupRegistry;

/**
 * Loads and writes plugin configuration via YAML (`config.yml`).
 */
public class ConfigurationYAML
{
    /** Static helpers only; never instantiated. */
    private ConfigurationYAML()
    {
    }


    protected static void loadConfiguration(final String pluginName)
    {
        loadConfiguration(pluginDirectory(pluginName));
    }

    /**
     * Where this plugin keeps its own files.
     *
     * <p>The one place the path is built, so the methods below can be handed a directory
     * instead of a name and be run against somewhere other than a live server.
     *
     * @param pluginName
     *            the plugin's folder name
     * @return its directory, which may not exist yet
     */
    static File pluginDirectory(final String pluginName)
    {
        return new File("plugins" + File.separator + pluginName + File.separator);
    }

    /**
     * Reads config.yml out of the given directory, writing a default one first if there is
     * none, and appending any keys the file does not yet mention.
     *
     * @param directory
     *            the plugin directory to read from
     */
    static void loadConfiguration(final File directory)
    {
        if (!directory.exists())
        {
            directory.mkdir();
        }

        final File cfg = new File(directory, "config.yml");
        if (!cfg.exists())
        {
            writeFile(cfg, DefaultSettings.config);
        }

        try (InputStream in = new FileInputStream(cfg))
        {
            final Object loaded = new Yaml().load(in);
            if (!(loaded instanceof Map))
            {
                return;
            }
            @SuppressWarnings("unchecked")
            final Map<String, Object> map = (Map<String, Object>) loaded;

            final List<Setting> missing = applySettings(map);

            // Material groups are a nested section, so they are read straight off the parsed
            // YAML rather than through the flat Setting/ConfigKeys mechanism. A server with
            // no such section falls back to the built-in Standard group.
            loadMaterialGroups(map.get(MATERIAL_GROUPS_KEY));

            // Appended to the file as well as defaulted in memory, so an admin can see the
            // setting exists and change it.
            if (!missing.isEmpty())
            {
                appendMissingSettings(cfg, missing);
            }
        }
        catch (final IOException e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.SEVERE, "Failed to read config.yml: " + e.getMessage());
        }
    }

    /**
     * Puts every known setting into memory, from the file where it says something.
     *
     * @param map
     *            the parsed config file
     * @return the settings the file did not mention, which the caller appends to it
     */
    private static List<Setting> applySettings(final Map<String, Object> map)
    {
        final List<Setting> missing = new ArrayList<>();
        for (final Setting element : DefaultSettings.config)
        {
            final Object value = valueFor(map, element);
            if (value == null)
            {
                ConfigManager.getConfigurations().put(element.getName(), element);
                missing.add(element);
            }
            else
            {
                final Setting s = settingFrom(element, value);
                ConfigManager.getConfigurations().put(s.getName(), s);
            }
        }
        return missing;
    }

    /**
     * What the file says about one setting, under either spelling of its key.
     *
     * <p>Kebab-case first, then the enum name -- so a config.yml written by a version that
     * spelled it TIMEOUT_SHUTDOWN keeps working rather than silently reverting to the
     * default.
     *
     * @param map
     *            the parsed config file
     * @param element
     *            the setting being looked for
     * @return its value, or null if the file does not mention it
     */
    private static Object valueFor(final Map<String, Object> map, final Setting element)
    {
        final String enumKey = element.getName().name();
        final String kebabKey = kebabKeyName(enumKey);
        if (map.containsKey(kebabKey))
        {
            return map.get(kebabKey);
        }
        return map.containsKey(enumKey) ? map.get(enumKey) : null;
    }

    /**
     * Builds a setting from whatever type the YAML parser handed back.
     *
     * <p>Integer is checked before Number, and the order is load-bearing: Integer is a
     * Number, so folding the two leaves every whole number stored as a Double and the next
     * getIntValue throws a ClassCastException.
     *
     * @param element
     *            the default, for its key and description
     * @param value
     *            the value read from the file
     * @return the setting to store
     */
    private static Setting settingFrom(final Setting element, final Object value)
    {
        if (value instanceof Boolean flag)
        {
            return new Setting(element.getName(), flag, element.getDescription(), SETTING_SECTION);
        }
        if (value instanceof Integer whole)
        {
            return new Setting(element.getName(), whole, element.getDescription(), SETTING_SECTION);
        }
        if (value instanceof Number number)
        {
            return new Setting(element.getName(), number.doubleValue(), element.getDescription(), SETTING_SECTION);
        }
        return new Setting(element.getName(), value.toString(), element.getDescription(), SETTING_SECTION);
    }

    /**
     * Hands the {@code gate-material-groups} section to the registry, tolerating a
     * missing or malformed section.
     *
     * @param raw
     *            the parsed value of the section, may be null
     */
    private static void loadMaterialGroups(final Object raw)
    {
        if (raw instanceof Map)
        {
            @SuppressWarnings("unchecked")
            final Map<String, Object> section = (Map<String, Object>) raw;
            MaterialGroupRegistry.load(section);
        }
        else
        {
            MaterialGroupRegistry.load(null);
        }
    }

    /** The section every Setting is filed under; the same name for all of them. */
    private static final String SETTING_SECTION = "WormholeXTreme";

    /** The config.yml key holding the nested material-group definitions. */
    private static final String MATERIAL_GROUPS_KEY = "gate-material-groups";

    /**
     * Writes discovered material groups into config.yml.
     *
     * <p>Unlike the flat settings, these go <em>inside</em> an existing
     * {@code gate-material-groups:} block rather than being appended to the end of the
     * file. Appending a second top-level key of the same name would parse as a duplicate
     * and silently discard whichever copy lost, taking the admin's own groups with it.
     * When the key is absent entirely, the section is created at the end.
     *
     * @param cfg
     *            the config file
     * @param groups
     *            the groups to write
     * @return true if the file was modified
     */
    static boolean appendMaterialGroups(final File cfg, final List<com.wormhole_xtreme.wormhole.model.MaterialGroup> groups)
    {
        if ((groups == null) || groups.isEmpty())
        {
            return false;
        }
        try
        {
            final String block = renderGroups(groups);
            final List<String> lines = new ArrayList<>(java.nio.file.Files.readAllLines(cfg.toPath()));
            final int sectionStart = indexOfSection(lines);

            if (sectionStart < 0)
            {
                appendNewSection(cfg, block);
            }
            else
            {
                lines.addAll(insertionPoint(lines, sectionStart),
                    java.util.Arrays.asList(block.split("\\R")));
                java.nio.file.Files.write(cfg.toPath(), lines);
            }
            logAdded(groups);
            return true;
        }
        catch (final IOException e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING,
                "Failed to add discovered material groups to config.yml: " + e.getMessage());
            return false;
        }
    }

    /**
     * Writes out the YAML for a set of discovered groups.
     *
     * @param groups
     *            the groups to write
     * @return the block of lines, indented to sit under the section key
     */
    private static String renderGroups(final List<com.wormhole_xtreme.wormhole.model.MaterialGroup> groups)
    {
        final StringBuilder block = new StringBuilder();
        for (final com.wormhole_xtreme.wormhole.model.MaterialGroup g : groups)
        {
            block.append("  # Added automatically from a gate shape using this frame material.")
                 .append(System.lineSeparator());
            block.append("  ").append(g.getName()).append(':').append(System.lineSeparator());
            block.append("    structure: ").append(g.getStructureMaterial().name()).append(System.lineSeparator());
            block.append("    portal: ").append(g.getPortalMaterial().name()).append(System.lineSeparator());
            block.append("    iris: ").append(g.getIrisMaterial().name()).append(System.lineSeparator());
            block.append("    light: ").append(g.getLightMaterial().name()).append(System.lineSeparator());
            block.append("    sign: ").append(g.getSignMaterial().name()).append(System.lineSeparator());
            // Written only when the shape this palette was derived from asked for one.
            // Writing a placeholder would hand the server a palette whose gates must be
            // built differently from the shape that suggested it.
            if (g.getChevronMaterial() != null)
            {
                block.append("    chevron: ").append(g.getChevronMaterial().name()).append(System.lineSeparator());
            }
        }
        return block.toString();
    }

    /**
     * Finds the material groups section.
     *
     * @param lines
     *            the config file
     * @return the line the section key sits on, or -1 if the file has no such section
     */
    private static int indexOfSection(final List<String> lines)
    {
        for (int i = 0; i < lines.size(); i++)
        {
            if (lines.get(i).startsWith(MATERIAL_GROUPS_KEY + ":"))
            {
                return i;
            }
        }
        return -1;
    }

    /**
     * Where new groups belong inside an existing section.
     *
     * <p>The section ends at the first following line that is neither blank, nor a comment,
     * nor indented -- that line belongs to the next top-level key. The point then steps back
     * over any trailing blanks and comments, so the new entries sit with the group
     * definitions rather than below the comment that introduces whatever comes next. Both
     * placements parse; only one of them reads correctly to the next person editing the file.
     *
     * @param lines
     *            the config file
     * @param sectionStart
     *            the line the section key sits on
     * @return the line to insert at
     */
    private static int insertionPoint(final List<String> lines, final int sectionStart)
    {
        int insertAt = lines.size();
        for (int i = sectionStart + 1; i < lines.size(); i++)
        {
            final String line = lines.get(i);
            if (line.trim().isEmpty() || line.startsWith(" ") || line.trim().startsWith("#"))
            {
                continue;
            }
            insertAt = i;
            break;
        }
        while ((insertAt > (sectionStart + 1))
            && (lines.get(insertAt - 1).trim().isEmpty() || lines.get(insertAt - 1).trim().startsWith("#")))
        {
            insertAt--;
        }
        return insertAt;
    }

    /**
     * Starts a material groups section at the end of a config that has none.
     *
     * @param cfg
     *            the config file
     * @param block
     *            the groups to write under it
     * @throws IOException
     *             if the file cannot be written
     */
    private static void appendNewSection(final File cfg, final String block) throws IOException
    {
        try (final java.io.FileWriter writer = new java.io.FileWriter(cfg, StandardCharsets.UTF_8, true))
        {
            writer.write(System.lineSeparator());
            writer.write(MATERIAL_GROUPS_KEY + ":" + System.lineSeparator());
            writer.write(block);
        }
    }

    /**
     * Notes which groups were added and what frame material each came from.
     *
     * @param groups
     *            the groups just written
     */
    private static void logAdded(final List<com.wormhole_xtreme.wormhole.model.MaterialGroup> groups)
    {
        final List<String> names = new ArrayList<>();
        for (final com.wormhole_xtreme.wormhole.model.MaterialGroup g : groups)
        {
            names.add(g.getName() + "=" + g.getStructureMaterial());
        }
        WormholeXTreme.getThisPlugin().prettyLog(Level.INFO,
            "Added " + groups.size() + " material group(s) to config.yml from gate shapes: " + names);
    }

    /**
     * Gets the config.yml file this plugin loads from.
     *
     * @param pluginName
     *            the plugin folder name
     * @return the config file, which may not exist
     */
    static File getConfigFile(final String pluginName)
    {
        return new File(pluginDirectory(pluginName), "config.yml");
    }

    private static void appendMissingSettings(final File cfg, final List<Setting> missing)
    {
        try (final java.io.FileWriter writer = new java.io.FileWriter(cfg, StandardCharsets.UTF_8, true /* append */))
        {
            writer.write(System.lineSeparator());
            writer.write("# --- Added by WormholeXTreme (missing keys) ---" + System.lineSeparator());
            for (final Setting s : missing)
            {
                final String keyName = kebabKeyName(s.getName().name());
                if ((s.getDescription() != null) && (!s.getDescription().isEmpty()))
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
            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO,
                "config.yml was missing " + missing.size() + " key(s); added defaults: " + names.toString());
        }
        catch (final IOException e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING,
                "Failed to append missing config keys: " + e.getMessage());
        }
    }

    protected static void writeFile(final File file, final Setting[] config)
    {
        try
        {
            // The directory to make is the one the file goes in; the caller already decided
            // where that is, so there is nothing to work out from a plugin name.
            final File directory = file.getParentFile();
            if ((directory != null) && !directory.exists())
            {
                directory.mkdir();
            }
            try (final FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8))
            {
                for (final Setting s : config)
                {
                    final String keyName = kebabKeyName(s.getName().name());
                    // (Legacy build-group keys removed; nothing to skip here.)
                    // Write comment description
                    if ((s.getDescription() != null) && (!s.getDescription().isEmpty()))
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
            WormholeXTreme.getThisPlugin().prettyLog(Level.SEVERE, "Failed to write config.yml: " + e.getMessage());
        }
    }

    /**
     * Rewrites the top-level settings in an existing config, leaving everything else alone.
     *
     * <p>Pure, so the part that decides what a saved config should look like can be tested
     * without touching a disk. Only lines starting a top-level key in column zero count: a
     * nested block's children are indented, so a material group's own portal or sign entry
     * is never mistaken for a setting of that name.
     *
     * @param lines
     *            the file as it stands
     * @param values
     *            kebab-case key to the value that should be written for it
     * @param updated
     *            filled in with the keys actually found and rewritten
     * @return the file as it should be written back
     */
    static List<String> updateSettingLines(final List<String> lines,
                                           final java.util.Map<String, String> values,
                                           final java.util.Set<String> updated)
    {
        final List<String> out = new java.util.ArrayList<String>(lines.size());
        for (final String line : lines)
        {
            final int colon = line.indexOf(':');
            if ((colon > 0) && (!line.isEmpty())
                && !Character.isWhitespace(line.charAt(0))
                && (line.charAt(0) != '#'))
            {
                final String key = line.substring(0, colon).trim();
                if (values.containsKey(key))
                {
                    out.add(key + ": " + values.get(key));
                    updated.add(key);
                    continue;
                }
            }
            out.add(line);
        }
        return out;
    }

    /**
     * Persists the running configuration without destroying the rest of the file.
     *
     * <p>This used to regenerate config.yml from the flat setting list on every shutdown,
     * opening it truncating and writing back only the keys it knew about. Everything else
     * went with it: the whole nested gate-material-groups block, permissions-support-disable
     * (skipped deliberately, and so deleted), and every comment an admin had written.
     *
     * <p>Material groups being partly rebuilt by discovery on the next startup is what kept
     * this from being obvious. A group tuned by hand came back with discovered defaults
     * instead of the admin's own portal, iris, light and sign choices, on every restart.
     *
     * <p>It now edits the settings it owns in place and leaves every other line exactly as it
     * found it. A file that does not exist yet is still generated whole, which is the one
     * case where writing the entire thing is the right answer.
     *
     * <p>permissions-support-disable is still never written, but that now means left as the
     * admin has it rather than dropped.
     *
     * @param file
     *            the config file
     * @param pluginName
     *            the plugin's folder name
     */
    protected static void writeCurrentConfiguration(final File file)
    {
        try
        {
            final File directory = file.getParentFile();
            if ((directory != null) && !directory.exists())
            {
                directory.mkdir();
            }

            final Setting[] defaults = DefaultSettings.config;
            final Map<String, String> values = new LinkedHashMap<String, String>();
            final Map<String, Setting> byKey = new LinkedHashMap<String, Setting>();
            collectCurrentValues(defaults, values, byKey);

            if (!file.exists())
            {
                writeFile(file, defaults);
                return;
            }

            final java.util.Set<String> updated = new java.util.HashSet<String>();
            final List<String> rewritten =
                updateSettingLines(java.nio.file.Files.readAllLines(file.toPath()), values, updated);

            rewriteFile(file, rewritten, missingFrom(byKey, updated), values);
        }
        catch (final Exception e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.SEVERE, "Failed to write config.yml: " + e.getMessage());
        }
    }

    /**
     * Reads every setting's current value, ready to be written back.
     *
     * <p>permissions-support-disable is left out on purpose, and that means left as the
     * admin has it rather than dropped: it is absent from the list this writer owns, so an
     * existing line survives the rewrite untouched and no line is invented for a server that
     * never set one.
     *
     * @param defaults
     *            the settings this plugin knows about
     * @param values
     *            filled with each key's value, formatted for YAML
     * @param byKey
     *            filled with each key's default, for its description
     */
    private static void collectCurrentValues(final Setting[] defaults,
                                             final Map<String, String> values,
                                             final Map<String, Setting> byKey)
    {
        for (final Setting def : defaults)
        {
            final ConfigManager.ConfigKeys key = def.getName();
            if (key == ConfigManager.ConfigKeys.PERMISSIONS_SUPPORT_DISABLE)
            {
                continue;
            }
            final Setting runtime = ConfigManager.getConfigurations().get(key);
            final Object value = (runtime != null) ? runtime.getValue() : def.getValue();
            final String keyName = kebabKeyName(key.name());
            values.put(keyName, formatValueForYaml(value));
            byKey.put(keyName, def);
        }
    }

    /**
     * The settings the file never mentioned, so nothing rewrote them in place.
     *
     * @param byKey
     *            every setting this writer owns
     * @param updated
     *            the keys that were found and rewritten
     * @return the ones left over
     */
    private static List<Setting> missingFrom(final Map<String, Setting> byKey,
                                             final java.util.Set<String> updated)
    {
        final List<Setting> missing = new ArrayList<Setting>();
        for (final Map.Entry<String, Setting> e : byKey.entrySet())
        {
            if (!updated.contains(e.getKey()))
            {
                missing.add(e.getValue());
            }
        }
        return missing;
    }

    /**
     * Writes the rewritten file, with anything it never mentioned added at the end.
     *
     * <p>An admin who never sees a setting written down has no way to know it exists, so the
     * missing ones go in with their descriptions rather than only being defaulted in memory.
     *
     * @param file
     *            the config file
     * @param rewritten
     *            the existing lines, with known settings brought up to date
     * @param missing
     *            settings the file never mentioned
     * @param values
     *            each key's value, formatted for YAML
     * @throws IOException
     *             if the file cannot be written
     */
    private static void rewriteFile(final File file, final List<String> rewritten,
                                    final List<Setting> missing, final Map<String, String> values)
        throws IOException
    {
        try (final FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8))
        {
            for (final String line : rewritten)
            {
                writer.write(line + System.lineSeparator());
            }
            if (missing.isEmpty())
            {
                return;
            }
            writer.write(System.lineSeparator());
            writer.write("# --- Added by WormholeXTreme (missing keys) ---" + System.lineSeparator());
            for (final Setting s : missing)
            {
                writeMissingSetting(writer, s, values);
            }
        }
    }

    /**
     * Writes one setting the file did not have, with its explanation above it.
     *
     * @param writer
     *            the file being written
     * @param setting
     *            the setting to add
     * @param values
     *            each key's value, formatted for YAML
     * @throws IOException
     *             if the file cannot be written
     */
    private static void writeMissingSetting(final FileWriter writer, final Setting setting,
                                            final Map<String, String> values) throws IOException
    {
        final String keyName = kebabKeyName(setting.getName().name());
        if ((setting.getDescription() != null) && !setting.getDescription().isEmpty())
        {
            for (final String wrapped : wrapComment(setting.getDescription(), 80))
            {
                writer.write("# " + wrapped + System.lineSeparator());
            }
        }
        writer.write(keyName + ": " + values.get(keyName) + System.lineSeparator());
        writer.write(System.lineSeparator());
    }

    /**
     * The key one setting is written under in config.yml.
     *
     * <p>Package-private rather than private so a test can walk every key this plugin
     * writes and check the config command accepts it back; the two spellings drifting apart
     * is exactly the bug that made this worth pinning. See
     * {@link ConfigManager#settingKey(String)} for the other half.
     *
     * @param constantName
     *            the enum constant's name
     * @return the key as it appears in the file
     */
    static String kebabKeyName(final String constantName)
    {
        return constantName.toLowerCase(Locale.ROOT).replace('_', '-');
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
                if (line.isEmpty())
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
            if (!line.isEmpty())
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
