package com.wormhole_xtreme.wormhole.config;

/**
 * Gives tests a configuration that behaves like a real one.
 *
 * <p>Nothing populates {@link ConfigManager}'s settings map until a config file is read or
 * written, so in a test every setting is absent and every getter falls back to its "not
 * configured" answer. A test that switches something on with a setter gets no error and no
 * effect — {@code setConfigValue} quietly ignores a key that is not in the map.
 *
 * <p>That is worse than it sounds. A test that enables the use cooldown and then asserts a
 * cancelled trip does not spend it passes whether or not the code is right, because the
 * cooldown was never on in the first place.
 *
 * <p>This lives in the config package because the settings map, {@link Setting} and
 * {@link DefaultSettings} are all package-private, and it is only used by tests.
 */
public final class ConfigTestSupport
{
    /** Static helpers only. */
    private ConfigTestSupport()
    {
    }

    /**
     * Fills the settings map with the plugin's defaults, the way a first run does.
     *
     * <p>Mirrors what {@link Configuration} does when no {@code config.yml} exists.
     */
    public static void loadDefaults()
    {
        for (final Setting setting : DefaultSettings.config)
        {
            ConfigManager.getConfigurations().put(setting.getName(), setting);
        }
    }

    /**
     * Turns one boolean setting on or off, as a config file would have.
     *
     * <p>For a test outside this package that needs a specific setting set. {@link Setting}
     * and the settings map are both package-private, so without this the test's only options
     * are to load every default it does not care about, or to go through reflection.
     *
     * @param key
     *            the setting to set
     * @param value
     *            what it should read as
     */
    public static void set(final ConfigManager.ConfigKeys key, final boolean value)
    {
        ConfigManager.getConfigurations().put(key, new Setting(key, value, "test", "WormholeXTreme"));
    }

    /**
     * Empties the settings map again.
     *
     * <p>The default {@link Setting} objects are shared statics, so a test that changes one
     * changes it for everything that runs afterwards in the same JVM. Tests that call
     * {@link #loadDefaults()} should put back whatever they altered and then clear.
     */
    public static void clear()
    {
        ConfigManager.getConfigurations().clear();
    }
}
