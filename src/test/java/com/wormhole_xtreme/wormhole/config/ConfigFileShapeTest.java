package com.wormhole_xtreme.wormhole.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;

/**
 * The shape of the {@code config.yml} the plugin writes, rather than its values.
 *
 * <p>Eighty-two settings is something an operator scrolls, so the file is written in groups
 * under {@code # --- ... ---} banners, with no blank line between the settings inside a group
 * and descriptions of a sentence or two. None of that is load-bearing for the plugin, which is
 * exactly why it needs a test: nothing else would notice it drifting back into one flat list of
 * three hundred and sixty lines.
 */
class ConfigFileShapeTest
{
    /**
     * The longest a description may be, in characters.
     *
     * <p>About four wrapped lines at the file's 80 columns. Long enough for a setting that
     * genuinely needs a caveat, short enough that nothing grows back into the fourteen-line
     * essay {@code mirror-view-depth} used to carry. Why a setting works the way it does goes in
     * {@code docs/}, which is where the long version of that one now lives.
     */
    private static final int LONGEST_DESCRIPTION = 320;

    @TempDir
    File directory;

    private File cfg;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        ConfigTestSupport.loadDefaults();
        cfg = new File(directory, "config.yml");
    }

    @AfterEach
    void tearDown() throws Exception
    {
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    private List<String> write() throws Exception
    {
        ConfigurationYAML.writeCurrentConfiguration(cfg);
        return Files.readAllLines(cfg.toPath(), StandardCharsets.UTF_8);
    }

    /**
     * No setting is listed under two headings.
     *
     * <p>The groups are the file's order and its headings at once, and {@code config} is
     * flattened from them, so a setting pasted into a second group would quietly be written
     * twice -- once under each -- and the duplicate would be the one the reader kept.
     */
    @Test
    void noSettingIsInTwoGroups()
    {
        final Set<ConfigKeys> seen = EnumSet.noneOf(ConfigKeys.class);
        final List<ConfigKeys> twice = new ArrayList<>();
        for (final DefaultSettings.Group group : DefaultSettings.groups)
        {
            for (final Setting setting : group.settings())
            {
                if (!seen.add(setting.getName()))
                {
                    twice.add(setting.getName());
                }
            }
        }

        assertEquals(List.of(), twice, "each setting belongs under one heading");
        assertEquals(seen.size(), DefaultSettings.config.length,
            "and the flat list is exactly the groups, nothing added or lost");
    }

    /** Every group is named, since the heading is what makes the file navigable. */
    @Test
    void everyGroupHasAHeadingAndSomeSettings()
    {
        for (final DefaultSettings.Group group : DefaultSettings.groups)
        {
            assertFalse(group.heading().isBlank(), "a group with no heading");
            assertTrue(group.settings().length > 0, "an empty group: " + group.heading());
        }
    }

    /**
     * No description runs to an essay.
     *
     * <p>The file is what an operator edits. A comment that fills a screen before the key it
     * describes is not documentation, and this is the only thing standing between the file and
     * the twelve thousand characters of prose it used to carry.
     */
    @Test
    void noDescriptionRunsLongerThanAFewLines()
    {
        final List<String> tooLong = new ArrayList<>();
        for (final Setting setting : DefaultSettings.config)
        {
            final String description = setting.getDescription();
            if ((description != null) && (description.length() > LONGEST_DESCRIPTION))
            {
                tooLong.add(setting.getName() + " (" + description.length() + " chars)");
            }
        }

        assertEquals(List.of(), tooLong,
            "over " + LONGEST_DESCRIPTION + " characters; put the reasoning in docs/ instead");
    }

    /**
     * The written file carries a banner for every group, in the order the groups are declared.
     *
     * <p>Through the real writer and a real file: the banner is emitted where a setting opens a
     * group, so a group whose first setting moved would lose its heading without anything else
     * changing.
     */
    @Test
    void theWrittenFileIsBanneredInGroupOrder() throws Exception
    {
        final List<String> banners = write().stream().filter(line -> line.startsWith("# --- ")).toList();

        final List<String> expected = new ArrayList<>();
        for (final DefaultSettings.Group group : DefaultSettings.groups)
        {
            expected.add("# --- " + group.heading() + " ---");
        }
        assertEquals(expected, banners, "a banner per group, in order");
    }

    /**
     * Inside a group the settings run on, with no blank line between them.
     *
     * <p>Where the third of the file that was blank went. A blank line before each banner is the
     * only one left, so the count of them is the count of groups less the first.
     */
    @Test
    void theOnlyBlankLinesAreTheOnesAboveABanner() throws Exception
    {
        final List<String> lines = write();

        int blanks = 0;
        for (int i = 0; i < lines.size(); i++)
        {
            if (lines.get(i).isEmpty())
            {
                blanks++;
                assertTrue((i + 1) < lines.size() && lines.get(i + 1).startsWith("# --- "),
                    "a blank line at " + (i + 1) + " that does not open a group");
            }
        }
        assertEquals(DefaultSettings.groups.length - 1, blanks,
            "one blank above every banner but the first");
    }

    /** Nothing the writer emits runs past the 80 columns it wraps comments to. */
    @Test
    void noLineRunsPastEightyColumns() throws Exception
    {
        final List<String> over = write().stream().filter(line -> line.length() > 80).toList();

        assertEquals(List.of(), over, "lines past 80 columns");
    }
}
