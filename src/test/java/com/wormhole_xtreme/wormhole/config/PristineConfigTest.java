package com.wormhole_xtreme.wormhole.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.MaterialGroupRegistry;
import com.wormhole_xtreme.wormhole.utils.YamlMaps;

/**
 * An untouched 1.10.0 config.yml is replaced with the current layout, and nothing else is (#599).
 *
 * <p>The pristine file here is the frozen 1.10.0 defaults themselves, which is a valid config.yml.
 */
class PristineConfigTest
{
    @TempDir
    File directory;

    private WormholeXTreme plugin;

    @BeforeEach
    void setUp() throws Exception
    {
        plugin = mock(WormholeXTreme.class);
        PluginTestSupport.install(plugin);
        ConfigTestSupport.clear();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        MaterialGroupRegistry.load(null);
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    private static String pristine() throws Exception
    {
        try (InputStream in = PristineConfigTest.class.getClassLoader().getResourceAsStream("config-defaults-1.10.0.yml"))
        {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Map<String, Object> pristineMap() throws Exception
    {
        return new LinkedHashMap<>(YamlMaps.asMap(new Yaml().load(pristine())));
    }

    private File config()
    {
        return new File(directory, "config.yml");
    }

    private void write(final String yaml) throws Exception
    {
        Files.write(config().toPath(), yaml.getBytes(StandardCharsets.UTF_8));
    }

    private String text(final String name) throws Exception
    {
        return Files.readString(new File(directory, name).toPath());
    }

    private void load()
    {
        ConfigurationYAML.loadConfiguration(directory);
    }

    private void verifyReplaced(final int times)
    {
        verify(plugin, times(times)).prettyLog(eq(Level.INFO), contains("was unchanged from 1.10.0"));
    }

    /** An untouched file, comments and key order aside, becomes the current layout, and the old one is kept. */
    @Test
    void anUntouchedFileIsReplacedAndBackedUp() throws Exception
    {
        // The admin's own comments, and a flat setting moved below the groups: neither makes it customised.
        final String lines = pristine();
        final int at = lines.indexOf("log-level:");
        final String first = lines.substring(at, lines.indexOf('\n', at) + 1);
        final String original = "# my notes\n" + lines.replace(first, "") + "# moved\n" + first;
        write(original);

        load();

        final String now = text("config.yml");
        assertTrue(now.contains("ring-animation-speed:"), "the current layout: a setting 1.10.0 lacked is in the file");
        assertFalse(now.contains("ring-hold-ticks"), "and a removed one is not");
        assertTrue(now.contains("# Start here"), "under the grouped heading");
        assertEquals(original, text("config.yml.bak"), "the old file is kept whole");
        verifyReplaced(1);
        verify(plugin, never()).prettyLog(eq(Level.INFO), contains("no longer exist"));
    }

    /** A second start finds the new layout, which is not 1.10.0's, and does nothing. */
    @Test
    void theSecondStartIsANoOp() throws Exception
    {
        write(pristine());
        load();
        final byte[] after = Files.readAllBytes(config().toPath());
        final int files = directory.list().length;

        load();

        assertArrayEquals(after, Files.readAllBytes(config().toPath()));
        assertEquals(files, directory.list().length, "no second backup");
        verifyReplaced(1);
    }

    /** One value changed is a customised file, so it stays exactly as written. */
    @Test
    void oneChangedValueLeavesTheFileAlone() throws Exception
    {
        final String edited = pristine().replace("ring-hold-ticks: 20", "ring-hold-ticks: 21");
        assertNotEquals(pristine(), edited, "the edit applied");
        write(edited);

        load();

        assertTrue(text("config.yml").startsWith(edited), "not replaced");
        assertFalse(new File(directory, "config.yml.bak").exists(), "and nothing copied aside");
        verifyReplaced(0);
    }

    /** A key no setting answers to may be the admin's own, so the file is theirs. */
    @Test
    void anUnknownKeyLeavesTheFileAlone() throws Exception
    {
        final String edited = pristine() + "my-own-note: 1\n";
        write(edited);

        load();

        assertTrue(text("config.yml").startsWith(edited), "not replaced");
        assertFalse(new File(directory, "config.yml.bak").exists());
    }

    /** A file missing a key every listed release wrote is not recognised as untouched. */
    @Test
    void aFileMissingAKeyEveryReleaseWroteLeavesItAlone() throws Exception
    {
        final String older = pristine().replace("sign-glowing-text: false\n", "");
        assertNotEquals(pristine(), older, "the edit applied");
        write(older);

        load();

        assertTrue(text("config.yml").startsWith(older), "not replaced");
        assertFalse(new File(directory, "config.yml.bak").exists());
    }

    /** A material group the admin edited is a change like any other. */
    @Test
    void anEditedMaterialGroupLeavesTheFileAlone() throws Exception
    {
        final String edited = pristine().replace("light: SHROOMLIGHT", "light: GLOWSTONE");
        assertNotEquals(pristine(), edited, "the edit applied");
        write(edited);

        load();

        assertTrue(text("config.yml").startsWith(edited), "not replaced");
        assertFalse(new File(directory, "config.yml.bak").exists());
    }

    /** If the old file cannot be copied aside, it is not replaced, and the log says so. */
    @Test
    void aFailedBackupLeavesTheFile() throws Exception
    {
        final String original = pristine();
        write(original);
        Files.writeString(new File(directory, "config.yml.bak").toPath(), "mine");
        for (int n = 1; n < 100; n++)
        {
            Files.writeString(new File(directory, "config.yml.bak." + n).toPath(), "mine");
        }

        load();

        assertTrue(text("config.yml").startsWith(original), "the file is untouched");
        verify(plugin).prettyLog(eq(Level.WARNING), contains("could not be replaced"), any(Throwable.class));
        verifyReplaced(0);
    }

    /** A backup already there is kept, and the new copy takes the next name. */
    @Test
    void anExistingBackupIsNotClobbered() throws Exception
    {
        final String original = pristine();
        write(original);
        Files.writeString(new File(directory, "config.yml.bak").toPath(), "mine");

        load();

        assertEquals("mine", text("config.yml.bak"));
        assertEquals(original, text("config.yml.bak.1"));
        verify(plugin).prettyLog(eq(Level.INFO), contains("config.yml.bak.1"));
    }

    /** Settings added since 1.10.0 may be in the file at their defaults, not at anything else. */
    @Test
    void aNewerSettingCountsOnlyAtItsDefault() throws Exception
    {
        final Map<String, Object> file = pristineMap();
        file.put("ring-animation-speed", 1);
        assertEquals("1.10.0", PristineConfig.untouchedSince(file), "1 is 1.0 and the default");
        file.put("ring-animation-speed", 2.0);
        assertNull(PristineConfig.untouchedSince(file));
    }

    /** Any listed release will do, so a later cleanup only adds a snapshot; a release not listed matches nothing. */
    @Test
    void anyListedReleaseMatches()
    {
        final Map<String, Object> file = Map.of("timeout-shutdown", 77);

        assertEquals("test-release", PristineConfig.untouchedSince(file, List.of("1.10.0", "test-release")));
        assertNull(PristineConfig.untouchedSince(file, List.of("1.10.0")), "not a 1.10.0 file");
        assertNull(PristineConfig.untouchedSince(Map.of("timeout-shutdown", 78), List.of("test-release")));
        assertFalse(PristineConfig.RELEASES.contains("1.11.0"), "the current release is never listed");
    }

    /** Each listed release's own defaults are recognised as that release, and no newer one. */
    @Test
    void everyListedReleaseIsRecognisedByItsOwnDefaults() throws Exception
    {
        for (final String release : PristineConfig.RELEASES)
        {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("config-defaults-" + release + ".yml"))
            {
                final Map<String, Object> file = YamlMaps.asMap(new Yaml().load(in));
                assertEquals(release, PristineConfig.untouchedSince(file), release);
            }
        }
    }

    /**
     * An old release's file that later releases appended to is still untouched.
     *
     * <p>A 1.5.0 file run on 1.10.0 gains the keys 1.10.0 added, the removed ones among them, and the material
     * groups; none of that is the admin's doing.
     */
    @Test
    void anOldFileLaterReleasesAddedToIsStillUntouched() throws Exception
    {
        final Map<String, Object> file = pristineMap();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("config-defaults-1.3.0.yml"))
        {
            final Map<String, Object> old = YamlMaps.asMap(new Yaml().load(in));
            file.forEach(old::putIfAbsent);
            assertEquals("1.3.0", PristineConfig.untouchedSince(old));
            old.put("ring-hold-ticks", 21);
            assertNull(PristineConfig.untouchedSince(old), "a changed value still counts");
        }
    }

    /** The material groups are seeded after the first start, so a file without them is as untouched as one with. */
    @Test
    void theMaterialGroupsAreOptional() throws Exception
    {
        final Map<String, Object> file = pristineMap();
        assertNotNull(file.remove("gate-material-groups"), "the snapshot had them");

        assertEquals("1.10.0", PristineConfig.untouchedSince(file));
    }

    /**
     * A key that parses to a number or a boolean is the admin's own, and is not a ClassCastException here.
     *
     * <p>The loader after it still fails on such a key (ConfigurationYAML's RENAMED check), as it did before
     * this change; this only keeps the decision from being the first thing to throw.
     */
    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void aKeyThatIsNotTextIsNotUntouched() throws Exception
    {
        final Map file = new LinkedHashMap(pristineMap());
        file.put(2024, "migrated");

        assertNull(PristineConfig.untouchedSince(file));
    }

    /** A new file cut short by a failed write is not moved over the old one. */
    @Test
    void aTruncatedNewFileIsNotComplete() throws Exception
    {
        final File whole = new File(directory, "whole.yml");
        ConfigurationYAML.writeFile(whole, DefaultSettings.config);
        assertTrue(PristineConfig.isComplete(whole));

        final String text = Files.readString(whole.toPath());
        final File cut = new File(directory, "cut.yml");
        Files.writeString(cut.toPath(), text.substring(0, text.length() / 2));
        assertFalse(PristineConfig.isComplete(cut), "half the settings");
        Files.writeString(cut.toPath(), "");
        assertFalse(PristineConfig.isComplete(cut), "none");
    }

    /** A new file that cannot be written is never moved over the old one. */
    @Test
    void aNewFileThatCannotBeWrittenLeavesTheFile() throws Exception
    {
        final String original = pristine();
        write(original);
        Files.createDirectory(new File(directory, "config.yml.new").toPath());

        load();

        assertTrue(config().isFile(), "still a file");
        assertEquals(original, text("config.yml").substring(0, original.length()));
        verify(plugin).prettyLog(eq(Level.WARNING), contains("could not be replaced"), any(Throwable.class));
    }

    /** A backup that already holds these bytes is reused, so a replacement that keeps failing leaves one copy. */
    @Test
    void anIdenticalBackupIsReused() throws Exception
    {
        write(pristine());

        final java.nio.file.Path first = PristineConfig.copyAside(config().toPath());
        final java.nio.file.Path second = PristineConfig.copyAside(config().toPath());

        assertEquals(first, second);
        assertFalse(new File(directory, "config.yml.bak.1").exists());
    }

    /**
     * Two releases that differ in one default cannot be told apart by a value that is the other's.
     *
     * <p>Documents the accepted corner: a 1.8.1 file whose admin set ring-default-flash back to the
     * REDSTONE_LAMP of 1.8.0 reads as an untouched 1.8.0 one. The copy kept as config.yml.bak has it.
     */
    @Test
    void aValueThatIsASiblingReleasesDefaultReadsAsThatRelease() throws Exception
    {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("config-defaults-1.8.1.yml"))
        {
            final Map<String, Object> file = YamlMaps.asMap(new Yaml().load(in));
            assertEquals("1.8.1", PristineConfig.untouchedSince(file));
            file.put("ring-default-flash", "REDSTONE_LAMP");
            assertEquals("1.8.0", PristineConfig.untouchedSince(file));
        }
    }

    /** A key two listed releases wrote differently is accepted at either, since a release in between appended it. */
    @Test
    void aKeyIsAcceptedAtAnyListedReleasesValue()
    {
        final List<String> releases = List.of("test-release-a", "test-release", "test-release-b");

        assertEquals("test-release", PristineConfig.untouchedSince(Map.of("timeout-shutdown", 77, "k", 2), releases),
            "k is not in this release's own file, and the last release wrote 2");
        assertEquals("test-release-a", PristineConfig.untouchedSince(Map.of("timeout-shutdown", 77, "k", 1), releases));
        assertNull(PristineConfig.untouchedSince(Map.of("timeout-shutdown", 77, "k", 3), releases));
    }

    /** The frozen table covers what 1.11.0 removed, or a file holding one could never be recognised. */
    @Test
    void theFrozenDefaultsHoldEveryRemovedSetting() throws Exception
    {
        final Map<String, Object> frozen = pristineMap();
        assertTrue(frozen.keySet().containsAll(RetiredSettings.KEYS));
        assertEquals(116, frozen.size(), "the 115 settings 1.10.0 wrote and its material groups");
    }
}
