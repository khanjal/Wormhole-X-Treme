// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2011 Ben Echols, Dean Bailey. See LICENSE.txt for terms.
package com.wormhole_xtreme.wormhole.permissions;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.logic.StargateUpdateRunnable;
import com.wormhole_xtreme.wormhole.logic.StargateUpdateRunnable.ActionToTake;
import com.wormhole_xtreme.wormhole.model.Stargate;

/**
 * The Class StargateRestrictions.
 * 
 * @author alron
 */
public class StargateRestrictions
{

    /** The Constant playerUseCooldownStart. */
    private static final ConcurrentHashMap<Player, Long> playerUseCooldownStart = new ConcurrentHashMap<Player, Long>();
    
    /** Recently-arrived players map: player -> (gateId, timestamp) */
    private static final ConcurrentHashMap<Player, RecentArrival> playerRecentArrival = new ConcurrentHashMap<Player, RecentArrival>();
    
    /** The Constant playerUseCooldownGroup. */
    // Removed as per new cooldown logic
    // private static final ConcurrentHashMap<Player, RestrictionGroup> playerUseCooldownGroup = new ConcurrentHashMap<Player, RestrictionGroup>();

    /**
     * Adds the player use cooldown.
     * 
     * @param player
     *            the player
     */
    public static void addPlayerUseCooldown(final Player player)
    {
        if (!ConfigManager.isUseCooldownEnabled())
        {
            return;
        }
        // Apply a single cooldown duration for all players when enabled. Default seconds come from ConfigManager compatibility fallback.
        final long cooldownSeconds = ConfigManager.getUseCooldownGroupOne();
        getPlayerUseCooldownStart().put(player, System.nanoTime());
        // scheduleSyncDelayedTask expects an int/ticks on some platforms; cast explicitly to avoid lossy-conversion errors
        WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), new StargateUpdateRunnable(player, ActionToTake.COOLDOWN_REMOVE), (int) (cooldownSeconds * 20L));
    }

    /**
     * Check player use cooldown remaining.
     * 
     * @param player
     *            the player
     * @return the int
     */
    public static long checkPlayerUseCooldownRemaining(final Player player)
    {
        if (getPlayerUseCooldownStart().containsKey(player))
        {
            final long startTime = getPlayerUseCooldownStart().get(player);
            final long currentTime = System.nanoTime();
            final long elapsedTime = (currentTime - startTime) / 1000000000;
            final long cooldown = ConfigManager.getUseCooldownGroupOne();
            return (cooldown >= elapsedTime) ? cooldown - elapsedTime : removePlayerUseCooldown(player);
        }
        return -1;
    }

    /**
     * Gets the player use cooldown group.
     * 
     * @return the player use cooldown group
     */
    // player-use cooldown group map removed; per-player cooldowns are tracked via `playerUseCooldownStart` only.

    /**
     * Gets the player use cooldown list.
     * 
     * @return the player use cooldown list
     */
    private static ConcurrentHashMap<Player, Long> getPlayerUseCooldownStart()
    {
        return playerUseCooldownStart;
    }

    private static ConcurrentHashMap<Player, RecentArrival> getPlayerRecentArrival()
    {
        return playerRecentArrival;
    }

    private static class RecentArrival
    {
        private final long gateId;

        RecentArrival(final long gateId)
        {
            this.gateId = gateId;
        }
    }

    /**
     * Mark the player as having just arrived from a gate. This protects the
     * source gate from immediate re-entry for a short period.
     */
    public static void addPlayerRecentArrival(final Player player, final Stargate fromGate)
    {
        if ((player == null) || (fromGate == null))
        {
            return;
        }
        getPlayerRecentArrival().put(player, new RecentArrival(fromGate.getGateId()));
        // Keep the flag for ~3 seconds (60 ticks) to mirror after-shutdown protection.
        final int timeoutTicks = 60;
        try
        {
            WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), new Runnable()
            {
                @Override
                public void run()
                {
                    try { removePlayerRecentArrival(player); } catch (final Throwable ignore) {}
                }
            }, timeoutTicks);
        }
        catch (final Throwable ignore) {}
    }

    public static boolean isPlayerRecentArrivalFrom(final Player player, final Stargate gate)
    {
        if ((player == null) || (gate == null))
        {
            return false;
        }
        final RecentArrival r = getPlayerRecentArrival().get(player);
        return (r != null) && (r.gateId == gate.getGateId());
    }

    public static void removePlayerRecentArrival(final Player player)
    {
        if (getPlayerRecentArrival().containsKey(player))
        {
            getPlayerRecentArrival().remove(player);
        }
    }

    /** Remove any recent-arrival markers that reference the given gate. */
    public static void removeRecentArrivalsForGate(final Stargate gate)
    {
        if (gate == null)
        {
            return;
        }
        final long gid = gate.getGateId();
        for (final Player p : getPlayerRecentArrival().keySet())
        {
            final RecentArrival r = getPlayerRecentArrival().get(p);
            if ((r != null) && (r.gateId == gid))
            {
                getPlayerRecentArrival().remove(p);
            }
        }
    }

    /**
     * Checks if is player build restricted.
     * 
     * @param player
     *            the player
     * @return true, if is player build restricted
     */
    public static boolean isPlayerBuildRestricted(final Player player)
    {
        // Build restriction feature removed: always allow builds. Permissions should
        // be managed via Vault / LuckPerms.
        return false;
    }

    /**
     * Checks if is player use cooldown.
     * 
     * @param player
     *            the player
     * @return true, if is player use cooldown
     */
    public static boolean isPlayerUseCooldown(final Player player)
    {
        return getPlayerUseCooldownStart().containsKey(player);
    }

    /**
     * Removes the player use cooldown.
     * 
     * @param player
     *            the player
     */
    public static int removePlayerUseCooldown(final Player player)
    {
        if (getPlayerUseCooldownStart().containsKey(player))
        {
            getPlayerUseCooldownStart().remove(player);
        }
        // playerUseCooldownGroup removed
        return 0;
    }
}
