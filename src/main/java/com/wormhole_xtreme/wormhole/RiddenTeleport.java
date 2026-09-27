package com.wormhole_xtreme.wormhole;

import java.util.List;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import com.wormhole_xtreme.wormhole.utils.PluginLog;

/**
 * Moves something that is being ridden, and everything riding it, to one place.
 *
 * <p>Paper 1.20.4 will not teleport an entity with passengers (it returns false unless given its
 * own {@code RETAIN_PASSENGERS} flag, which Spigot lacks); 1.21 carries them along. So the riders
 * come off first, the vehicle moves bare, and they follow it, as the beam already did. The caller
 * re-seats them on its own delay, since a teleported player's client must acknowledge the move
 * before it accepts a seat.
 *
 * <p>The vehicle goes first, so one that will not move stops the trip before any rider has gone.
 */
public final class RiddenTeleport
{
    private RiddenTeleport() {}

    /**
     * Moves {@code root} and its passenger stack to {@code target}.
     *
     * @param root
     *            the vehicle or mount
     * @param target
     *            where it goes; each passenger is sent its own copy, yaw and all
     * @param parents
     *            what each passenger rides, read before this call, in the order
     *            {@link com.wormhole_xtreme.wormhole.utils.EntityUtils#collectPassengerPairs}
     *            gives
     * @param children
     *            the passengers, parallel to {@code parents}
     * @return true if {@code root} moved and its passengers were sent after it, unseated; false
     *         if it would not move, in which case everyone is back aboard where they started
     */
    public static boolean move(final Entity root, final Location target, final List<Entity> parents,
        final List<Entity> children)
    {
        unseat(parents, children);
        final boolean moved;
        try
        {
            moved = root.teleport(target);
        }
        catch (final RuntimeException e)
        {
            reseatWhereTheyAre(parents, children);
            throw e;
        }
        if (!moved)
        {
            reseatWhereTheyAre(parents, children);
            return false;
        }
        for (final Entity child : children)
        {
            try
            {
                child.teleport(target.clone());
            }
            // The caller's re-seat closes the gap to the vehicle if this one did not land.
            catch (final RuntimeException e)
            {
                PluginLog.log(Level.FINE, "Could not send a passenger after " + root.getType(), e);
            }
        }
        return true;
    }

    /** Deepest first, so nothing is left riding anything when the root moves. */
    private static void unseat(final List<Entity> parents, final List<Entity> children)
    {
        GateDismount.allowWhile(() ->
        {
            for (int i = children.size() - 1; i >= 0; i--)
            {
                try
                {
                    parents.get(i).removePassenger(children.get(i));
                }
                catch (final RuntimeException e)
                {
                    PluginLog.log(Level.FINE, "Could not unseat a passenger before a teleport", e);
                }
            }
        });
    }

    /** Nobody moved, so no teleport is waiting on an acknowledgement and the seats can be taken now. */
    private static void reseatWhereTheyAre(final List<Entity> parents, final List<Entity> children)
    {
        for (int i = 0; i < children.size(); i++)
        {
            try
            {
                parents.get(i).addPassenger(children.get(i));
            }
            catch (final RuntimeException e)
            {
                PluginLog.log(Level.FINE, "Could not re-seat a passenger after a refused teleport", e);
            }
        }
    }
}
