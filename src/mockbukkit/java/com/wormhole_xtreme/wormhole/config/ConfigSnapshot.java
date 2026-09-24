package com.wormhole_xtreme.wormhole.config;

import java.util.HashMap;
import java.util.Map;

/** The plugin's settings as they stood at one moment, to put back after a MockBukkit load fills them. */
public final class ConfigSnapshot
{
    private final Map<ConfigManager.ConfigKeys, Setting> settings;

    private ConfigSnapshot()
    {
        settings = new HashMap<>(ConfigManager.getConfigurations());
    }

    /** Takes one now. */
    public static ConfigSnapshot take()
    {
        return new ConfigSnapshot();
    }

    /** Puts every setting back as it was, and drops any added since. */
    public void restore()
    {
        ConfigManager.getConfigurations().clear();
        ConfigManager.getConfigurations().putAll(settings);
    }
}
