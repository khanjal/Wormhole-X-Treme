package com.wormhole_xtreme.wormhole.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;

/**
 * A setting nobody registered is a setting that silently refuses to be set.
 *
 * <p>{@link DefaultSettings#config} is the only thing that ever populates the settings map
 * ({@code Configuration} loops over it, and nothing else adds a key), so a {@link ConfigKeys}
 * constant missing from that array is absent from the map for the life of the server. That
 * has two consequences, and neither one announces itself:
 *
 * <ul>
 * <li>{@code setConfigValue} checks {@code isConfigurationKey} first and returns quietly when
 * it is false, so every setter for that key does nothing at all.</li>
 * <li>The matching getter's {@code isConfigurationKey(...) ? ... : literal} guard always takes
 * the literal branch, so it reports a hardcoded constant forever.</li>
 * </ul>
 *
 * <p>That combination is what {@code /wormhole cooldown one 300} was: it printed "cooldown time
 * set to: 300" and the cooldown stayed at 120, because {@code USE_COOLDOWN_GROUP_ONE},
 * {@code _TWO} and {@code _THREE} had accessors in {@link ConfigManager} but no entry here. The
 * key never reached {@code config.yml} either, so an admin could not work around it by editing
 * the file. The same was true of {@code BUILD_RESTRICTION_*}, whose command was equally inert.
 *
 * <p>This test is the structural guard rather than a test of the cooldown specifically: adding
 * a {@code ConfigKeys} constant and forgetting {@link DefaultSettings} reintroduces exactly
 * that failure for whatever the new setting is, and nothing else in the suite would notice.
 */
class ConfigKeyRegistrationTest
{
    @AfterEach
    void clearSettings()
    {
        ConfigTestSupport.clear();
    }

    /**
     * Every key the enum declares is actually registered.
     *
     * <p>Failure here means the named keys have accessors that lie: their setters no-op and
     * their getters answer a hardcoded literal. Add the key to {@link DefaultSettings#config}
     * with a default and a description, or remove the enum constant and its accessors.
     */
    @Test
    void everyConfigKeyIsRegisteredInDefaultSettings()
    {
        final Set<ConfigKeys> registered = EnumSet.noneOf(ConfigKeys.class);
        for (final Setting setting : DefaultSettings.config)
        {
            registered.add(setting.getName());
        }

        final List<ConfigKeys> unregistered = new ArrayList<>();
        for (final ConfigKeys key : ConfigKeys.values())
        {
            if (!registered.contains(key))
            {
                unregistered.add(key);
            }
        }

        assertTrue(unregistered.isEmpty(),
            "These ConfigKeys have no DefaultSettings entry, so their setters silently do "
                + "nothing and their getters return a hardcoded literal forever: " + unregistered);
    }

    /**
     * No setting is registered twice under the same key.
     *
     * <p>Two entries for one key means whichever comes last in the array wins, and the earlier
     * default and description are written to {@code config.yml} and then ignored -- so the file
     * documents a value the server is not using.
     */
    @Test
    void noConfigKeyIsRegisteredTwice()
    {
        final Set<ConfigKeys> seen = EnumSet.noneOf(ConfigKeys.class);
        final List<ConfigKeys> duplicates = new ArrayList<>();
        for (final Setting setting : DefaultSettings.config)
        {
            if (!seen.add(setting.getName()))
            {
                duplicates.add(setting.getName());
            }
        }
        assertTrue(duplicates.isEmpty(),
            "Registered more than once, so the earlier entry's default is written to config.yml "
                + "and then overridden: " + duplicates);
    }

    /**
     * The use cooldown survives being set, which is the whole point of registering it.
     *
     * <p>This is the specific case the guard above generalises. Run against the old code -- three
     * unregistered group keys -- the setter would no-op and this would read back 120 instead of
     * 300. It passes now only because {@code USE_COOLDOWN_SECONDS} is a real registered setting.
     */
    @Test
    void theUseCooldownReadsBackWhatWasSet()
    {
        ConfigTestSupport.loadDefaults();
        final int original = ConfigManager.getUseCooldownSeconds();
        try
        {
            ConfigManager.setUseCooldownSeconds(300);
            assertEquals(300, ConfigManager.getUseCooldownSeconds(),
                "The cooldown setter did not take. That is what an unregistered key does: it "
                    + "accepts the write, reports success, and leaves the old value in place.");
        }
        finally
        {
            // The Setting objects in DefaultSettings.config are shared statics, so a value left
            // changed here leaks into every test that runs after this one in the same JVM.
            ConfigManager.setUseCooldownSeconds(original);
        }
    }
}
