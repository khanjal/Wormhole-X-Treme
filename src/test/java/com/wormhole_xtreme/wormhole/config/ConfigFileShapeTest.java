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
import java.util.function.IntSupplier;

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

    /**
     * Every group is named, and so is every sub-group but a group's opening run.
     *
     * <p>An unheaded run after the first would read as part of the sub-group above it.
     */
    @Test
    void everyGroupHasAHeadingAndSomeSettings()
    {
        for (final DefaultSettings.Group group : DefaultSettings.groups)
        {
            assertFalse(group.heading().isBlank(), "a group with no heading");
            assertFalse(group.settings().isEmpty(), "an empty group: " + group.heading());
            final List<DefaultSettings.SubGroup> subGroups = group.subGroups();
            for (int i = 0; i < subGroups.size(); i++)
            {
                assertFalse(subGroups.get(i).settings().isEmpty(), "an empty sub-group in " + group.heading());
                assertTrue((i == 0) || !subGroups.get(i).heading().isBlank(),
                    "an unheaded sub-group after the first in " + group.heading());
            }
        }
    }

    /**
     * The groups come commonest first, the sounds after them and the integrations last.
     *
     * <p>From #586: the fourteen integration settings used to open the file, ahead of every gate
     * setting an admin came to change.
     */
    @Test
    void theGroupsComeCommonestFirst()
    {
        final List<String> headings = new ArrayList<>();
        for (final DefaultSettings.Group group : DefaultSettings.groups)
        {
            headings.add(group.heading());
        }

        assertEquals(List.of("General", "Stargates", "Gate views and mirrors", "Gate signs",
            "Transport rings", "Beaming", "Economy", "Stargate sounds", "Transport ring sounds",
            "Beaming sounds", "Integrations"), headings);
    }

    /**
     * Every setting is in a group, and the groups hold every setting there is.
     *
     * <p>A reshuffle that dropped one would leave it out of a new file altogether.
     */
    @Test
    void theGroupsHoldEverySetting()
    {
        final Set<ConfigKeys> grouped = EnumSet.noneOf(ConfigKeys.class);
        for (final DefaultSettings.Group group : DefaultSettings.groups)
        {
            for (final Setting setting : group.settings())
            {
                grouped.add(setting.getName());
            }
        }

        assertEquals(EnumSet.allOf(ConfigKeys.class), grouped, "every setting has a heading");
        assertEquals(ConfigKeys.values().length, DefaultSettings.config.length, "and none is listed twice");
    }

    /**
     * The start-here block names each setting under the group it really is in.
     *
     * <p>Read off the written file: each line of the block names a setting and a heading, and that
     * setting's key must sit between that heading's banner and the next one. The block lists them
     * in the order the file does.
     */
    @Test
    void theStartHereBlockNamesEachSettingUnderItsGroup() throws Exception
    {
        final List<String> lines = write();
        final int firstBanner = indexOf(lines, "# --- ");
        final List<String> block = lines.subList(0, firstBanner).stream()
            .filter(line -> line.startsWith("#   ")).toList();

        assertEquals(DefaultSettings.START_HERE.size(), block.size(), "a line per listed setting: " + block);
        assertTrue(block.size() >= 8, "about ten settings to start with");
        int previous = firstBanner;
        for (final String entry : block)
        {
            final String[] parts = entry.substring(4).split(" {2,}", 2);
            assertEquals(2, parts.length, "no two-space gap between setting and group in: " + entry);
            final int banner = lines.indexOf("# --- " + parts[1] + " ---");
            assertTrue(banner > 0, "no group named " + parts[1]);
            final int next = banner + 1 + indexOf(lines.subList(banner + 1, lines.size()), "# --- ");
            final int at = banner + indexOf(lines.subList(banner, next), parts[0] + ": ");
            assertTrue(at < next, parts[0] + " is not under " + parts[1]);
            assertTrue(at > previous, parts[0] + " is listed out of file order");
            previous = at;
        }
    }

    /**
     * Every sub-group's lighter heading is written, in order, inside its group.
     *
     * <p>A sub-heading is emitted where a setting opens it, so one whose first setting moved would
     * lose its heading without anything else changing.
     */
    @Test
    void theWrittenFileCarriesEverySubHeadingInOrder() throws Exception
    {
        final List<String> headings = write().stream()
            .filter(line -> line.startsWith("# --- ") || line.startsWith("# -- ")).toList();

        final List<String> expected = new ArrayList<>();
        for (final DefaultSettings.Group group : DefaultSettings.groups)
        {
            expected.add("# --- " + group.heading() + " ---");
            for (final DefaultSettings.SubGroup subGroup : group.subGroups())
            {
                if (!subGroup.heading().isEmpty())
                {
                    expected.add("# -- " + subGroup.heading() + " --");
                }
            }
        }
        assertTrue(expected.size() > (DefaultSettings.groups.length + 10), "the big groups are split");
        assertEquals(expected, headings, "each sub-heading inside its group, in order");
    }

    /** @return the first line starting with the prefix, or the size of the list if none does */
    private static int indexOf(final List<String> lines, final String prefix)
    {
        for (int i = 0; i < lines.size(); i++)
        {
            if (lines.get(i).startsWith(prefix))
            {
                return i;
            }
        }
        return lines.size();
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
     * Inside a sub-group the settings run on, with no blank line between them.
     *
     * <p>Where the third of the file that was blank went. A blank line before each banner is the
     * only one left: one above every group, the first included since the start-here block is
     * above it, and one above every sub-heading but one straight under its group's banner. That
     * comes to one per sub-group, counting a group with none as one.
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
                assertTrue((i + 1) < lines.size() && lines.get(i + 1).startsWith("# --"),
                    "a blank line at " + (i + 1) + " that does not open a group or sub-group");
            }
        }
        int expected = 0;
        for (final DefaultSettings.Group group : DefaultSettings.groups)
        {
            expected += group.subGroups().size();
        }
        assertEquals(expected, blanks, "one blank above every banner but a sub-heading opening its group");
    }


    /**
     * A floor an operator could trip over is named in the description.
     *
     * <p>Most of the clamps in {@link ConfigManager} only stop nonsense -- no negative tick
     * counts -- and saying so in the file would be noise. These three sit far enough above
     * nothing that somebody can set a value the plugin then quietly ignores, which is exactly
     * what a comment above the key should admit to.
     *
     * <p>From the review of this change. The countdown's description said the abort window
     * stopped being real below about twenty; the floor is thirty, so every value under it was
     * already being raised and the file was telling operators otherwise. It had said that before
     * this change too, so the trim carried the error forward rather than introducing it.
     */
    @Test
    void aFloorWorthKnowingIsNamedInItsDescription()
    {
        assertFloor(ConfigKeys.RING_COUNTDOWN_TICKS, 1, 30, ConfigManager::getRingCountdownTicks);
        assertFloor(ConfigKeys.RING_REACH, 0, 2, ConfigManager::getRingReach);
        assertFloor(ConfigKeys.ENTITY_SCAN_INTERVAL_TICKS, 1, 5,
            ConfigManager::getEntityScanIntervalTicks);
    }

    /**
     * Sets a setting below its floor, and holds both the clamp and the comment that admits it.
     *
     * @param key
     *            the setting
     * @param below
     *            a value under its floor
     * @param floor
     *            what the getter should answer instead
     * @param getter
     *            the getter that does the clamping
     */
    private static void assertFloor(final ConfigKeys key, final int below, final int floor,
        final IntSupplier getter)
    {
        ConfigTestSupport.set(key, below);

        assertEquals(floor, getter.getAsInt(), key + " should be floored");
        assertTrue(descriptionOf(key).contains(String.valueOf(floor)),
            key + " is floored at " + floor + " but its description never says so: "
                + descriptionOf(key));
    }

    /** @return what config.yml says above one key */
    private static String descriptionOf(final ConfigKeys key)
    {
        for (final Setting setting : DefaultSettings.config)
        {
            if (setting.getName() == key)
            {
                return setting.getDescription();
            }
        }
        throw new AssertionError("no such setting: " + key);
    }

    /** Nothing the writer emits runs past the 80 columns it wraps comments to. */
    @Test
    void noLineRunsPastEightyColumns() throws Exception
    {
        final List<String> over = write().stream().filter(line -> line.length() > 80).toList();

        assertEquals(List.of(), over, "lines past 80 columns");
    }
}
