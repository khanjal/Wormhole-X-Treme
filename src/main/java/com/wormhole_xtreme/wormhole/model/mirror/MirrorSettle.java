package com.wormhole_xtreme.wormhole.model.mirror;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;

/**
 * How long a mirror stays shut for somebody it has just put down in front of one.
 *
 * <p>A mirror arrives a player at the destination banner's own block, which is deliberate --
 * {@link MirrorArrival} explains why -- and it means they land with that banner filling their
 * view, inside or directly under it. A right-click that is still being sent when they get
 * there, from a held button or the client resolving the interaction again at the new position,
 * then lands on the far banner and sends them straight back. On a linked pair that is a round
 * trip inside one second, and what the player sees is a mirror that returned them to where
 * they started. It was reported exactly that way: "clicking the return mirror just takes me
 * back to it instead of the one in world".
 *
 * <p>So an arrival shuts every mirror for that player for a moment. Not just the one they
 * arrived at: the same click can be re-resolved against whatever banner is now in front of
 * them, and on a wall of them that need not be the one they came out of.
 *
 * <p>Keyed by UUID and armed only once a teleport has actually been accepted, both the way
 * {@code BeamCooldown} does it. Arming it at the click instead would spend the window on a
 * trip another plugin went on to refuse.
 */
public final class MirrorSettle
{
    /**
     * How long a mirror ignores the player it just carried.
     *
     * <p>Two seconds, which is longer than it looks. A held right-click repeats several times
     * a second, so a window of a few hundred milliseconds closes the door and reopens it while
     * the button is still down -- the bounce would come back for anybody who clicked rather
     * than tapped. Against that, the only thing this costs is a deliberate return trip made
     * inside two seconds of arriving, which is faster than anybody reads the room they are in.
     */
    static final long SETTLE_NANOS = 2_000_000_000L;

    /** Who arrived when, and whether they have been told why a click did nothing. */
    private static final Map<UUID, Arrival> ARRIVALS = new ConcurrentHashMap<>();

    /**
     * One player's arrival.
     *
     * @param nanos
     *            when they landed
     * @param told
     *            whether the one-line explanation has been said for this arrival
     */
    private record Arrival(long nanos, boolean told)
    {
    }

    /** Static registry only. */
    private MirrorSettle()
    {
    }

    /**
     * Whether an arrival at {@code arrivedNanos} is still settling at {@code nowNanos}.
     *
     * <p>Its own method, taking plain longs, so the window can be tested at either side of its
     * edge without a clock or a sleeping test. Subtraction rather than comparison because
     * {@link System#nanoTime} may be negative and only differences are meaningful.
     *
     * @param arrivedNanos
     *            when the player landed
     * @param nowNanos
     *            the time being asked about
     * @return true if a mirror should ignore them
     */
    static boolean within(final long arrivedNanos, final long nowNanos)
    {
        return (nowNanos - arrivedNanos) < SETTLE_NANOS;
    }

    /** Records that a player has just come through a mirror. */
    public static void arrived(final Player player)
    {
        ARRIVALS.put(player.getUniqueId(), new Arrival(System.nanoTime(), false));
    }

    /**
     * Whether mirrors should ignore this player for the moment.
     *
     * <p>An elapsed record is dropped on the way past rather than left to be recomputed
     * against forever, which is what {@code BeamCooldown} does with its own.
     *
     * @param player
     *            whoever clicked
     * @return true if they arrived through a mirror moments ago
     */
    public static boolean settling(final Player player)
    {
        final UUID id = player.getUniqueId();
        final Arrival arrival = ARRIVALS.get(id);
        if (arrival == null)
        {
            return false;
        }
        if (!within(arrival.nanos(), System.nanoTime()))
        {
            ARRIVALS.remove(id);
            return false;
        }
        return true;
    }

    /**
     * Whether to explain the ignored click, which is true once per arrival.
     *
     * <p>A held button repeats, so saying it every time would put a column of the same line in
     * chat for one press -- the thing this project already fixed once for a player holding
     * forward against a locked gate. Said once, a player who genuinely meant the second click
     * still learns why nothing happened.
     *
     * @param player
     *            whoever clicked
     * @return true the first time it is asked for a given arrival
     */
    public static boolean shouldExplain(final Player player)
    {
        final UUID id = player.getUniqueId();
        final Arrival arrival = ARRIVALS.get(id);
        if ((arrival == null) || arrival.told())
        {
            return false;
        }
        ARRIVALS.put(id, new Arrival(arrival.nanos(), true));
        return true;
    }

    /** Forgets everybody, for a reload and for tests. */
    public static void clear()
    {
        ARRIVALS.clear();
    }
}
