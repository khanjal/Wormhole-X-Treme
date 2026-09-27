package com.wormhole_xtreme.wormhole;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.utils.PluginLog;

/**
 * Moves something that is being ridden, and everything riding it, to one place.
 *
 * <p>Spigot and Paper 1.20.4 will not teleport an entity with passengers (Paper takes its own
 * {@code RETAIN_PASSENGERS} flag, which Spigot lacks); Paper 1.21 carries them along. So the
 * riders come off first, the vehicle moves bare, and they follow it, as the beam already did.
 * The caller re-seats them on its own delay, since a teleported player's client must
 * acknowledge the move before it accepts a seat.
 *
 * <p>Players go first: another plugin refusing a player's teleport is the likeliest refusal,
 * and it then stops the trip before anything else has moved. A trip is all or nothing, so a
 * later refusal brings back the players already sent and re-seats everyone where they were.
 */
public final class RiddenTeleport
{
    private RiddenTeleport() {}

    /**
     * Moves {@code root} and its passenger stack to {@code target}.
     *
     * <p>A player who will not come off stops the trip. Anything else that will not, such as
     * another plugin's entity seated on a player, stays aboard and is carried if the server
     * will carry it; if it will not, that refusal is reported like any other.
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
     * @return true if {@code root} and every player aboard moved, the rest sent after them,
     *         unseated; false if the trip was refused anywhere, in which case everyone is back
     *         aboard where they started
     */
    public static boolean move(final Entity root, final Location target, final List<Entity> parents,
        final List<Entity> children)
    {
        final Set<Entity> stuck = unseat(parents, children);
        if (stuck == null)
        {
            reseatWhereTheyAre(parents, children);
            return false;
        }
        final List<Player> sent = new ArrayList<>();
        final List<Location> sentFrom = new ArrayList<>();
        for (final Entity child : children)
        {
            if ((child instanceof Player rider) && !stuck.contains(child) && !sendPlayer(rider, target, sent, sentFrom))
            {
                return undo(sent, sentFrom, parents, children);
            }
        }
        final boolean moved;
        try
        {
            moved = root.teleport(target.clone());
        }
        catch (final RuntimeException e)
        {
            undo(sent, sentFrom, parents, children);
            throw e;
        }
        if (!moved)
        {
            return undo(sent, sentFrom, parents, children);
        }
        for (final Entity child : children)
        {
            if (!(child instanceof Player) && !stuck.contains(child))
            {
                sendAfter(root, child, target);
            }
        }
        return true;
    }

    /** @return false if the server refused to move this player */
    private static boolean sendPlayer(final Player rider, final Location target, final List<Player> sent,
        final List<Location> sentFrom)
    {
        final Location from = rider.getLocation();
        try
        {
            if (!rider.teleport(target.clone()))
            {
                return false;
            }
        }
        catch (final RuntimeException e)
        {
            PluginLog.log(Level.FINE, "Could not send " + rider.getName() + " with their ride", e);
            return false;
        }
        sent.add(rider);
        sentFrom.add(from);
        return true;
    }

    /** Brings back the players already sent, and re-seats everyone where they started. */
    private static boolean undo(final List<Player> sent, final List<Location> sentFrom, final List<Entity> parents,
        final List<Entity> children)
    {
        for (int i = 0; i < sent.size(); i++)
        {
            try
            {
                if (sentFrom.get(i) != null)
                {
                    sent.get(i).teleport(sentFrom.get(i));
                }
            }
            catch (final RuntimeException e)
            {
                PluginLog.log(Level.FINE, "Could not bring back " + sent.get(i).getName(), e);
            }
        }
        reseatWhereTheyAre(parents, children);
        return false;
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
     * @return the non-players that would not come off, or null if a player would not, which
     *         stops the trip there (the ones already off are the caller's to re-seat)
     */
    private static Set<Entity> unseat(final List<Entity> parents, final List<Entity> children)
    {
        final Set<Entity> stuck = Collections.newSetFromMap(new IdentityHashMap<>());
        final boolean[] playerStuck = { false };
        GateDismount.allowWhile(children, () ->
        {
            for (int i = children.size() - 1; i >= 0; i--)
            {
                final Entity child = children.get(i);
                if (!takeOff(parents.get(i), child))
                {
                    if (child instanceof Player)
                    {
                        playerStuck[0] = true;
                        return;
                    }
                    stuck.add(child);
                }
            }
        });
        return playerStuck[0] ? null : stuck;
    }

    /** @return true if {@code child} is off; removePassenger answers true whatever happened */
    private static boolean takeOff(final Entity parent, final Entity child)
    {
        try
        {
            parent.removePassenger(child);
        }
        catch (final RuntimeException e)
        {
            PluginLog.log(Level.FINE, "Could not unseat a passenger before a teleport", e);
        }
        try
        {
            return child.getVehicle() == null;
        }
        catch (final RuntimeException e)
        {
            return false;
        }
    }

    /** Nobody is left elsewhere, so no client is mid-teleport and the seats can be taken now. */
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
