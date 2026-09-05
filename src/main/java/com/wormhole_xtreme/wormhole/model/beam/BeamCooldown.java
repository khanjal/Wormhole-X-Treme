package com.wormhole_xtreme.wormhole.model.beam;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.config.ConfigManager;

/**
 * Per-player beam cooldown. A single duration, unlike gates' {@code StargateRestrictions},
 * which still carries a multi-group cooldown system beaming has no equivalent concept for --
 * a beam destination isn't grouped the way a restriction group grouped gates.
 *
 * <p>Set only once a beam has actually fired, never at the point a player is merely refused
 * one -- checked and enforced by the caller ({@link BeamTravel}), not here, the same split
 * gates already use: setting it at the check as well would spend a cooldown on a trip that
 * had not happened yet and might still not, if the player went offline mid-sequence.
 */
public final class BeamCooldown
{
    private static final Map<UUID, Long> START_NANOS = new ConcurrentHashMap<UUID, Long>();

    private BeamCooldown() {}

    /** Starts a player's cooldown now. */
    public static void start(final Player player)
    {
        START_NANOS.put(player.getUniqueId(), System.nanoTime());
    }

    /**
     * Whether a player is still on cooldown.
     *
     * @param player the player
     * @return true if they must wait
     */
    public static boolean isActive(final Player player)
    {
        return remainingSeconds(player) > 0;
    }

    /**
     * How many seconds a player still has to wait, clearing the record once it has elapsed
     * rather than leaving a stale entry to recompute against forever.
     *
     * @param player the player
     * @return seconds remaining, or 0 if they are free to beam
     */
    public static long remainingSeconds(final Player player)
    {
        final UUID id = player.getUniqueId();
        final Long start = START_NANOS.get(id);
        if (start == null)
        {
            return 0;
        }
        final long elapsedSeconds = (System.nanoTime() - start) / 1_000_000_000L;
        final long cooldownSeconds = ConfigManager.getBeamUseCooldownSeconds();
        if (elapsedSeconds >= cooldownSeconds)
        {
            START_NANOS.remove(id);
            return 0;
        }
        return cooldownSeconds - elapsedSeconds;
    }
}
