package com.wormhole_xtreme.wormhole.model.beam;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;

/**
 * Which players are mid-beam and should not be allowed to walk away from the animation
 * playing around them.
 *
 * <p>Position only. Camera look (yaw/pitch) cannot be locked server-side at all -- that is
 * purely client-rendered, never something the server controls -- so
 * {@link BeamFreezeListener} lets it through untouched and only ever reverts x/y/z.
 */
public final class BeamFreeze
{
    private static final Set<UUID> FROZEN = ConcurrentHashMap.newKeySet();

    private BeamFreeze() {}

    public static void freeze(final Player player)
    {
        FROZEN.add(player.getUniqueId());
    }

    public static void unfreeze(final Player player)
    {
        FROZEN.remove(player.getUniqueId());
    }

    public static boolean isFrozen(final Player player)
    {
        return FROZEN.contains(player.getUniqueId());
    }
}
