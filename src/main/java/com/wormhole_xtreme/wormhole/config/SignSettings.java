package com.wormhole_xtreme.wormhole.config;

import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;

/** The sign text and colour settings; {@link ConfigManager} delegates here. */
final class SignSettings
{
    /** Static helpers only. */
    private SignSettings()
    {
    }

    /**
     * Reads a configured colour name, resolved by {@code SignStyle} at the point of use.
     *
     * <p>Returned as the raw name rather than a resolved colour so the fallback stays with
     * the caller that knows what this particular line should look like.
     *
     * @param key
     *            the colour setting to read
     * @return the configured name, or empty when unset
     */
    private static String colorSetting(final ConfigKeys key)
    {
        final Setting s = ConfigManager.getConfigurations().get(key);
        return s == null ? "" : String.valueOf(s.getStringValue()).trim();
    }

    /**
     * Whether the plugin's own sign text glows.
     *
     * @return true if signs the plugin writes should use glowing text
     */
    static boolean isSignGlowingText()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.SIGN_GLOWING_TEXT);
        return (s == null) || s.getBooleanValue();
    }

    /**
     * Whether a player-placed dial sign is converted to the gate's own sign material.
     *
     * @return true if the dial sign should be made to match the gate's palette
     */
    static boolean isSignDialMatchMaterial()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.SIGN_DIAL_MATCH_MATERIAL);
        return (s == null) || s.getBooleanValue();
    }

    /** @return colour name for a gate's own name, on either sign */
    static String getSignColorGateName()
    {
        return colorSetting(ConfigKeys.SIGN_COLOR_GATE_NAME);
    }

    /** @return colour name for the network line on a gate's name sign */
    static String getSignColorNetwork()
    {
        return colorSetting(ConfigKeys.SIGN_COLOR_NETWORK);
    }

    /** @return colour name for the owner line on a gate's name sign */
    static String getSignColorOwner()
    {
        return colorSetting(ConfigKeys.SIGN_COLOR_OWNER);
    }

    /** @return colour name for the destination currently selected on a dial sign */
    static String getSignColorSelected()
    {
        return colorSetting(ConfigKeys.SIGN_COLOR_SELECTED);
    }

    /** @return colour name for the destinations either side of the selected one */
    static String getSignColorNeighbour()
    {
        return colorSetting(ConfigKeys.SIGN_COLOR_NEIGHBOUR);
    }
}
