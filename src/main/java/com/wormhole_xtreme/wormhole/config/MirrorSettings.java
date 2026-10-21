package com.wormhole_xtreme.wormhole.config;

import java.util.logging.Level;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;

/** The mirror limits, messages and view settings; {@link ConfigManager} delegates here. */
final class MirrorSettings
{
    /** Static helpers only. */
    private MirrorSettings()
    {
    }

    /** Whether a setting that is not a whole number has been reported yet. */
    private static boolean warnedNotWhole;

    /**
     * A setting's whole number, or a fallback, said once, for a value written by hand that is not
     * one: getIntValue casts, and a config.yml with "two" in it threw on every read.
     */
    private static int wholeNumberOr(final Setting setting, final int fallback)
    {
        final Object value = setting.getValue();
        if (value instanceof Integer whole)
        {
            return whole.intValue();
        }
        try
        {
            return Integer.parseInt(String.valueOf(value).trim());
        }
        catch (final NumberFormatException notWhole)
        {
            if (!warnedNotWhole)
            {
                warnedNotWhole = true;
                final WormholeXTreme plugin = WormholeXTreme.getThisPlugin();
                if (plugin != null)
                {
                    plugin.prettyLog(Level.WARNING, setting.getName() + " is \"" + value + "\", not a whole number; using "
                        + fallback);
                }
            }
            return fallback;
        }
    }

    /**
     * How many mirrors one world may hold.
     *
     * @return the most, or 0 for no limit
     */
    static int getMirrorPerWorldLimit()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.MIRROR_PER_WORLD_LIMIT);
        return (s == null) ? 1 : Math.max(0, s.getIntValue());
    }

    /**
     * How close a player must be for a mirror's banner to give way to its room, how far they may
     * drift before a mirror they turned on goes off, and how far out its wall is read.
     *
     * @return the distance in blocks, never below one
     */
    static int getMirrorProximityDistance()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.MIRROR_PROXIMITY_DISTANCE);
        return (s == null) ? 16 : Math.max(1, s.getIntValue());
    }

    /**
     * Whether a mirror names itself above the hotbar to whoever is looking at it.
     *
     * <p>Defaults to true when the setting is missing, which is what an existing server's
     * config.yml looks like after an upgrade. A new thing that announces itself is the right
     * default here: the whole complaint this answers is that a mirror gives no sign of being
     * anything but a banner.
     *
     * @return true if looking at a mirror says what it is
     */
    static boolean isMirrorApproachMessage()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.MIRROR_APPROACH_MESSAGE);
        return (s == null) || s.getBooleanValue();
    }

    /**
     * Whether creatures in a mirror's or a gate's far room are shown through it, as stand-ins.
     *
     * <p>False when the setting is missing, as on an upgraded server's config.yml: stand-ins are
     * real entities that other plugins see, so they are something an admin turns on.
     *
     * @return true if a view shows the creatures standing in its far room
     */
    static boolean isMirrorShowEntities()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.MIRROR_SHOW_ENTITIES);
        return (s != null) && s.getBooleanValue();
    }

    /**
     * Whether the players in a far room are shown as well as its mobs; asked only where
     * {@link #isMirrorShowEntities()} is on.
     *
     * <p>False when the setting is missing, as on an upgraded server's config.yml: it shows who is in
     * the destination room, which is for an admin to turn on.
     *
     * @return true if a view shows the players standing in its far room
     */
    static boolean isMirrorShowPlayers()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.MIRROR_SHOW_PLAYERS);
        return (s != null) && s.getBooleanValue();
    }

    /**
     * How many chunks in front of a watched window's far side are held loaded for its creatures.
     *
     * @return 0 to {@link ConfigManager#MOST_ENTITY_LOAD_RADIUS}; 2 when the setting is missing
     */
    static int getMirrorEntityLoadRadius()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.MIRROR_ENTITY_LOAD_RADIUS);
        final int radius = (s == null) ? ConfigManager.DEFAULT_ENTITY_LOAD_RADIUS
            : wholeNumberOr(s, ConfigManager.DEFAULT_ENTITY_LOAD_RADIUS);
        return Math.max(0, Math.min(ConfigManager.MOST_ENTITY_LOAD_RADIUS, radius));
    }
}
