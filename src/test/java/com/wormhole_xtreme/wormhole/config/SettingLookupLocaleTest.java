package com.wormhole_xtreme.wormhole.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Finding a setting by name does not depend on what language the server runs in.
 *
 * <p>{@code settingNamed} upper-cases what was typed before looking it up, so
 * {@code ring_default_style} finds {@code RING_DEFAULT_STYLE}. Folded in the JVM's own
 * locale, a Turkish server turns every {@code i} into a dotted capital I (U+0130) instead of
 * an {@code I} -- and since most of this plugin's settings have an {@code i} somewhere in
 * their name, {@code /wormhole config} answered "No setting called ring_default_style."
 * for nearly all of them on such a server.
 *
 * <p>Found while fixing the same mistake one layer up, in the value being written. Worth its
 * own test because the two fail in opposite directions: a value refused says so loudly,
 * while a setting not found reads as a typo, and the setting name on screen looks perfectly
 * correct because the mangling happens after it is read.
 */
public class SettingLookupLocaleTest
{
    /** The locale to put back, since this is JVM-wide state. */
    private Locale before;

    @BeforeEach
    public void speakTurkish()
    {
        before = Locale.getDefault();
        Locale.setDefault(new Locale("tr", "TR"));
        ConfigTestSupport.loadDefaults();
    }

    @AfterEach
    public void speakWhateverWeDidBefore()
    {
        ConfigTestSupport.clear();
        Locale.setDefault(before);
    }

    @Test
    public void aSettingWhoseNameContainsAnIIsStillFoundOnATurkishServer()
    {
        for (final String typed : new String[] {
            "ring_default_style", "RING_DEFAULT_STYLE", "same_world_only", "log_level",
            "gate_sound_iris_open", "beam_use_cooldown_seconds" })
        {
            assertNotNull(ConfigManager.describeSetting(typed),
                "\"" + typed + "\" was not found, so /wormhole config would answer \"No "
                    + "setting called " + typed + ".\" on a server running in Turkish");
        }
    }
}
