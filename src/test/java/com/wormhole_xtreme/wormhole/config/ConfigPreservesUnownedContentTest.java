package com.wormhole_xtreme.wormhole.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Saving the config keeps everything the plugin does not own.
 *
 * <p>Every clean shutdown persists the running configuration. That used to mean regenerating
 * config.yml from the flat setting list -- opening it truncating and writing back only the
 * keys it knew about -- so everything else was destroyed each time: the whole nested
 * gate-material-groups block, permissions-support-disable, and every comment an admin wrote.
 *
 * <p>It hid behind material-group discovery, which rebuilt groups from the gate shapes on the
 * next startup. A server with stock palettes looked fine. A group somebody had tuned by hand
 * came back with discovered defaults instead of their own portal, iris, light and sign
 * choices, once per restart, for as long as they kept restarting.
 *
 * <p>These pin the file surgery rather than the file writing, so they need no disk.
 */
public class ConfigPreservesUnownedContentTest
{
    private static Map<String, String> values(final String... pairs)
    {
        final Map<String, String> m = new LinkedHashMap<String, String>();
        for (int i = 0; i < pairs.length; i += 2)
        {
            m.put(pairs[i], pairs[i + 1]);
        }
        return m;
    }

    @Test
    public void asettingTheFileCarriesIsUpdatedInPlace()
    {
        final Set<String> updated = new HashSet<String>();
        final List<String> after = ConfigurationYAML.updateSettingLines(
            Arrays.asList("shutdown-timeout: 30"), values("shutdown-timeout", "45"), updated);

        assertEquals(Arrays.asList("shutdown-timeout: 45"), after);
        assertTrue(updated.contains("shutdown-timeout"), "the caller needs to know this was found");
    }

    /**
     * A nested block survives untouched.
     *
     * <p>This is the one that was actually being destroyed: several levels of indented keys
     * under one top-level name, none of it in the flat setting list.
     */
    @Test
    public void anestedMaterialGroupBlockIsLeftExactlyAsItWas()
    {
        final Set<String> updated = new HashSet<String>();
        final List<String> after = ConfigurationYAML.updateSettingLines(Arrays.asList(
            "gate-material-groups:",
            "  Standard:",
            "    structure: OBSIDIAN",
            "    portal: WATER",
            "shutdown-timeout: 30"), values("shutdown-timeout", "45"), updated);

        assertEquals("gate-material-groups:", after.get(0));
        assertEquals("  Standard:", after.get(1));
        assertEquals("    structure: OBSIDIAN", after.get(2));
        assertEquals("    portal: WATER", after.get(3));
        assertEquals("shutdown-timeout: 45", after.get(4));
    }

    /**
     * An indented key is never mistaken for a setting sharing its name.
     *
     * <p>A material group has its own sign entry. Ignore indentation and saving would rewrite
     * that group's sign material from an unrelated top-level setting, silently repainting
     * every gate in that palette.
     */
    @Test
    public void anIndentedKeyIsNotTreatedAsASettingOfTheSameName()
    {
        final Set<String> updated = new HashSet<String>();
        final List<String> after = ConfigurationYAML.updateSettingLines(Arrays.asList(
            "gate-material-groups:",
            "  Atlantis:",
            "    sign: WARPED_WALL_SIGN"), values("sign", "SOMETHING_ELSE"), updated);

        assertEquals("    sign: WARPED_WALL_SIGN", after.get(2),
            "a group's own entry must not be rewritten by a top-level setting of that name");
        assertTrue(updated.isEmpty(), "nothing at the top level matched, so nothing was updated");
    }

    @Test
    public void commentsAndBlankLinesAreKept()
    {
        final Set<String> updated = new HashSet<String>();
        final List<String> after = ConfigurationYAML.updateSettingLines(Arrays.asList(
            "# an admin wrote this and would like to keep it",
            "",
            "shutdown-timeout: 30"), values("shutdown-timeout", "45"), updated);

        assertEquals("# an admin wrote this and would like to keep it", after.get(0));
        assertEquals("", after.get(1));
    }

    /** A commented-out setting stays commented out, rather than becoming live config. */
    @Test
    public void acommentedOutSettingIsNotRevived()
    {
        final Set<String> updated = new HashSet<String>();
        final List<String> after = ConfigurationYAML.updateSettingLines(
            Arrays.asList("#shutdown-timeout: 30"), values("shutdown-timeout", "45"), updated);

        assertEquals("#shutdown-timeout: 30", after.get(0));
        assertTrue(updated.isEmpty());
    }

    /**
     * A key the plugin does not own is left alone.
     *
     * <p>permissions-support-disable is deliberately never written. Under the old
     * regenerate-everything write that meant deleted on every shutdown; leaving unknown lines
     * untouched is what turns "not written" back into "not disturbed".
     */
    @Test
    public void akeyThePluginDoesNotOwnIsUntouched()
    {
        final Set<String> updated = new HashSet<String>();
        final List<String> after = ConfigurationYAML.updateSettingLines(Arrays.asList(
            "permissions-support-disable: true",
            "shutdown-timeout: 30"), values("shutdown-timeout", "45"), updated);

        assertEquals("permissions-support-disable: true", after.get(0));
        assertFalse(updated.contains("permissions-support-disable"));
    }

    /** An absent setting is left for the caller to append with its description. */
    @Test
    public void asettingAbsentFromTheFileIsNotReportedAsUpdated()
    {
        final Set<String> updated = new HashSet<String>();
        ConfigurationYAML.updateSettingLines(
            new ArrayList<String>(), values("sign-glowing-text", "false"), updated);

        assertTrue(updated.isEmpty(), "an absent key must be left for the caller to append");
    }

    @Test
    public void avalueContainingDotsIsReplacedWhole()
    {
        final Set<String> updated = new HashSet<String>();
        final List<String> after = ConfigurationYAML.updateSettingLines(
            Arrays.asList("gate-sound-dial: block.note_block.pling"),
            values("gate-sound-dial", "entity.enderman.teleport"), updated);

        assertEquals("gate-sound-dial: entity.enderman.teleport", after.get(0));
    }

    /**
     * The real shipped config survives a save with its material groups intact.
     *
     * <p>The cases above are synthetic. This one runs the file this plugin actually ships
     * through the same surgery a shutdown performs, because the block that was being lost is
     * defined in that file and its shape -- how deeply nested, how many groups, what sits
     * around it -- is the thing a synthetic example is most likely to get wrong.
     */
    @Test
    public void theShippedConfigKeepsItsMaterialGroupsThroughASave() throws Exception
    {
        final java.nio.file.Path shipped = java.nio.file.Paths.get("src/main/resources/config.yml");
        final List<String> before = java.nio.file.Files.readAllLines(shipped);
        assertTrue(before.contains("gate-material-groups:"),
            "the shipped config must define material groups, or this proves nothing");

        // Every flat setting the plugin owns, as a shutdown would write them.
        final Map<String, String> owned = new LinkedHashMap<String, String>();
        for (final ConfigManager.ConfigKeys key : ConfigManager.ConfigKeys.values())
        {
            owned.put(ConfigurationYAML.kebabKeyName(key.name()), "written-by-save");
        }

        final Set<String> updated = new HashSet<String>();
        final List<String> after = ConfigurationYAML.updateSettingLines(before, owned, updated);

        assertTrue(after.contains("gate-material-groups:"),
            "saving the config used to delete the material groups block entirely");

        // Every indented line is group content and must come back byte for byte.
        for (final String line : before)
        {
            if (line.startsWith(" ") && !line.trim().isEmpty())
            {
                assertTrue(after.contains(line),
                    "saving dropped a nested line the plugin does not own: " + line);
            }
        }
        assertEquals(before.size(), after.size(), "saving must not add or remove lines in place");
    }
}
