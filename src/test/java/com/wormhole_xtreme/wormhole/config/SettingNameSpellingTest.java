package com.wormhole_xtreme.wormhole.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A setting can be named the way config.yml spells it, not just the way the enum does.
 *
 * <p>The file writes {@code gate-sound-kawoosh}. The settings are enum constants, so the
 * command wanted {@code gate_sound_kawoosh}, and {@code settingNamed} handed whatever was
 * typed straight to {@code ConfigKeys.valueOf}. Pasting the key out of the file therefore
 * answered "No setting called gate-sound-kawoosh." -- which reads as the setting having been
 * removed, when the two names differ only in punctuation nobody has reason to think matters.
 *
 * <p>Found the obvious way: someone whose gate still boomed after the kawoosh default
 * changed went to change the value by hand, typed the key exactly as it appears in their
 * config.yml, and was told there was no such setting.
 *
 * <p>The search behind {@code /wormhole config <partial>} had the same gap, and it fails
 * more quietly: a fragment copied out of the file matched nothing, and an empty list looks
 * like a server with no such settings rather than a name spelled the wrong way.
 */
public class SettingNameSpellingTest
{
    @BeforeEach
    public void loadTheDefaults()
    {
        ConfigTestSupport.loadDefaults();
    }

    @AfterEach
    public void forgetThem()
    {
        ConfigTestSupport.clear();
    }

    @Test
    public void everyKeyThisPluginWritesIntoConfigYmlIsAlsoAKeyItAccepts()
    {
        for (final Setting setting : DefaultSettings.config)
        {
            final String asWritten = ConfigurationYAML.kebabKeyName(setting.getName().name());
            assertNotNull(ConfigManager.describeSetting(asWritten),
                "config.yml has \"" + asWritten + "\" in it, so that is what a server owner "
                    + "copies into /wormhole config -- and it answered \"No setting called "
                    + asWritten + ".\"");
        }
    }

    @Test
    public void bothSpellingsOfANameDescribeTheSameSetting()
    {
        assertEquals(ConfigManager.describeSetting("GATE_SOUND_KAWOOSH"),
            ConfigManager.describeSetting("gate-sound-kawoosh"),
            "the file's spelling and the enum's have to reach one setting, or the command "
                + "answers two different things about the same line of config.yml");
    }

    @Test
    public void aFragmentCopiedOutOfConfigYmlNarrowsTheListTheSameWay()
    {
        final List<String> underscored = ConfigManager.settingNamesMatching("gate_sound");
        final List<String> hyphenated = ConfigManager.settingNamesMatching("gate-sound");
        assertFalse(underscored.isEmpty(), "there are gate sound settings to find");
        assertEquals(underscored, hyphenated,
            "typing part of a name is how somebody finds the setting they half remember, so "
                + "the punctuation they copied out of the file cannot change the answer");
    }

    @Test
    public void nothingTypedListsEverything()
    {
        assertEquals(ConfigManager.settingNames().size(),
            ConfigManager.settingNamesMatching("").size(),
            "/wormhole config with no name lists the lot; an empty needle matches all of it");
    }

    @Test
    public void aNameThatIsNotASettingIsStillNotASetting()
    {
        assertNull(ConfigManager.describeSetting("gate-sound-banana"),
            "accepting either spelling is not the same as accepting anything");
        assertTrue(ConfigManager.settingNamesMatching("banana").isEmpty(),
            "a search for something that is not there still finds nothing");
    }
}
