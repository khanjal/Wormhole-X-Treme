package com.wormhole_xtreme.wormhole.config;

import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;

/** Which kinds of object the web map layers show; {@link ConfigManager} delegates here. */
final class MapSettings
{
    /** Static helpers only. */
    private MapSettings()
    {
    }

    /** Returns true if gates and the lines between dialled pairs are shown on the web map; on when the setting is missing, as it ships. */
    static boolean isMapShowGates()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.MAP_SHOW_GATES);
        return (s == null) || s.getBooleanValue();
    }

    /** Returns true if transport rings are shown on the web map; on when the setting is missing, as it ships. */
    static boolean isMapShowRings()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.MAP_SHOW_RINGS);
        return (s == null) || s.getBooleanValue();
    }

    /** Returns true if public beam destinations are shown on the web map; on when the setting is missing, as it ships. */
    static boolean isMapShowBeams()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.MAP_SHOW_BEAMS);
        return (s == null) || s.getBooleanValue();
    }

    /** Returns true if quantum mirrors are shown on the web map; on when the setting is missing, as it ships. */
    static boolean isMapShowMirrors()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.MAP_SHOW_MIRRORS);
        return (s == null) || s.getBooleanValue();
    }

    /** Returns true if gates with an iris code appear on the web map; on when the setting is missing, as it ships. */
    static boolean isMapShowIrisGates()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.MAP_SHOW_IRIS_GATES);
        return (s == null) || s.getBooleanValue();
    }
}
