package com.wormhole_xtreme.wormhole.plugin.map;

import com.wormhole_xtreme.wormhole.config.ConfigManager;

/**
 * Which layers the operator wants on the map, and whether gates with an iris code are among
 * the gates shown.
 *
 * <p>A layer switched off is left off the map entirely, not drawn empty, so it is not offered
 * to viewers as a checkbox with nothing behind it.
 *
 * @param gates
 *            gates, and the lines between dialled pairs
 * @param irisGates
 *            gates with an iris code, when gates are shown at all
 * @param rings
 *            transport rings
 * @param beams
 *            public beam destinations
 * @param mirrors
 *            quantum mirrors
 */
public record MapLayers(boolean gates, boolean irisGates, boolean rings, boolean beams, boolean mirrors)
{
    /** Everything shown, as the settings ship. */
    public static final MapLayers ALL = new MapLayers(true, true, true, true, true);

    /**
     * The layers the config asks for.
     *
     * @return the layers
     */
    public static MapLayers fromConfig()
    {
        return new MapLayers(ConfigManager.isDynmapShowGates(), ConfigManager.isDynmapShowIrisGates(),
            ConfigManager.isDynmapShowRings(), ConfigManager.isDynmapShowBeams(),
            ConfigManager.isDynmapShowMirrors());
    }
}
