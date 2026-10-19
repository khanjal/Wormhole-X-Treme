package com.wormhole_xtreme.wormhole.config;

/**
 * The web-map layer settings: what Dynmap, BlueMap, squaremap and Pl3xMap are told to draw (#430).
 *
 * <p>Moved out of {@link ConfigManager} with each body unchanged; {@link ConfigManager} keeps a
 * one-line forwarder for every method here, so no caller moved. Every layer is on when its setting
 * is missing, as it ships.
 */
final class MapSettings
{
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
