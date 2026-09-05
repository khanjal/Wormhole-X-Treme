package com.wormhole_xtreme.wormhole.permissions;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
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
        final long cooldownSeconds = ConfigManager.getUseCooldownSeconds();
        getPlayerUseCooldownStart().put(player, System.nanoTime());
        WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(),
            () -> removePlayerUseCooldown(player), cooldownTicks(cooldownSeconds));
    }

    /**
     * How many ticks to wait before clearing a cooldown, for a wait given in seconds.
     *
     * <p>{@code scheduleSyncDelayedTask} takes an {@code int} of ticks, and the obvious
     * {@code (int) (seconds * 20L)} wraps: past 107,374,182 seconds the product exceeds
     * {@link Integer#MAX_VALUE} and comes back negative, which Bukkit runs on the next tick.
     * The result is the exact inverse of what was asked for -- a cooldown set absurdly long
     * becomes no cooldown at all -- and it fails silently, since nothing about a task running
     * early looks like an arithmetic problem.
     *
     * <p>That was unreachable until this setting became real: the value was previously a
     * hardcoded 120 that no command or config file could change. Now that an admin can type a
     * number into {@code config.yml}, both ends of the range need to hold. A negative wait is
     * treated as no wait, and anything too large to schedule saturates at
     * {@link Integer#MAX_VALUE} ticks, which is a little over three years -- long enough to
     * read as "forever", which is what someone setting a number that size meant.
     *
     * @param seconds
     *            the configured wait, which may be any value the file contains
     * @return a tick count that is safe to schedule
     */
    static int cooldownTicks(final long seconds)
    {
        if (seconds <= 0L)
        {
            return 0;
        }
        // Tested against the limit rather than multiplying and checking the product: the
        // product overflows long as well, well before it is compared, so a large enough
        // value came back negative from the very check meant to catch it.
        final long longestExact = Integer.MAX_VALUE / 20L;
        return (seconds > longestExact) ? Integer.MAX_VALUE : (int) (seconds * 20L);
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
            final long cooldown = ConfigManager.getUseCooldownSeconds();
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
