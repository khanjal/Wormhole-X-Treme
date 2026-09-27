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
     *            where it goes; the vehicle and each passenger get their own copy, yaw and all
     * @param parents
     *            what each passenger rides, read before this call, in the order
     *            {@link com.wormhole_xtreme.wormhole.utils.EntityUtils#collectPassengerPairs}
     *            gives
     * @param children
     *            the passengers, parallel to {@code parents}
     * @return true if {@code root} moved and its passengers were sent after it, unseated; false
     *         if a passenger would not come off or it would not move, in which case everyone is
     *         back aboard where they started
     */
    public static boolean move(final Entity root, final Location target, final List<Entity> parents,
        final List<Entity> children)
    {
        if (!unseat(parents, children))
        {
            // Moving now would leave a rider aboard: refused on 1.20.4, carried alone on 1.21.
            reseatWhereTheyAre(parents, children);
            return false;
        }
        final boolean moved;
        try
        {
            moved = root.teleport(target.clone());
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
            sendAfter(root, child, target);
        }
        return true;
    }

    /** The caller's re-seat moves a passenger that did not land here to its vehicle, and retries. */
    private static void sendAfter(final Entity root, final Entity child, final Location target)
    {
        try
        {
            if (!child.teleport(target.clone()))
            {
                PluginLog.log(Level.FINE, "A passenger was refused on the way after " + root.getType());
            }
        }
        catch (final RuntimeException e)
        {
            PluginLog.log(Level.FINE, "Could not send a passenger after " + root.getType(), e);
        }
    }

    /**
     * Takes every passenger off, deepest first.
     *
     * @return false if any is still aboard, say because another plugin cancelled the dismount
     */
    private static boolean unseat(final List<Entity> parents, final List<Entity> children)
    {
        final boolean[] allOff = { true };
        GateDismount.allowWhile(children, () ->
        {
            for (int i = children.size() - 1; i >= 0; i--)
            {
                try
                {
                    parents.get(i).removePassenger(children.get(i));
                    // removePassenger answers true whatever happened, so ask the passenger.
                    if (children.get(i).getVehicle() != null)
                    {
                        allOff[0] = false;
                    }
                }
                catch (final RuntimeException e)
                {
                    PluginLog.log(Level.FINE, "Could not unseat a passenger before a teleport", e);
                    allOff[0] = false;
                }
            }
        });
        return allOff[0];
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
