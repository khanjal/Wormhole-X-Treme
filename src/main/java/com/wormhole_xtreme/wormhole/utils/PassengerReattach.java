package com.wormhole_xtreme.wormhole.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * Puts a passenger stack back together after the thing it was riding has been moved.
 *
 * <p>Teleporting an entity detaches its passengers, and the client does not reliably accept
 * the re-seat on the first try -- so getting a rider back onto their horse is not one call
 * but a retry loop on a backoff, plus a position-sync fallback for the case where the
 * passenger has been left too far from its parent to be accepted at all.
 *
 * <p>That loop lived on {@code WormholeXTremePlayerListener}, private, which was fine while
 * gates were the only thing that moved a mount. Beaming moves one too, from another package,
 * and re-deriving the retry behaviour there would have meant a second copy of a sequence that
 * took real play-testing to get right -- the same reasoning that moved
 * {@link EntityUtils#collectPassengerPairs} out of the vehicle listener. Re-seating a stack is
 * a fact about entities, not about listening for events.
 *
 * <p>The gate and beam callers differ only in what they pass for {@code exitVelocity}: a gate
 * shoves the mount out along the exit facing, a beam sets it down standing still.
 */
public final class PassengerReattach
{
    /** Maximum re-seat attempts before a passenger is given up on. */
    private static final int MAX_REATTACH_ATTEMPTS = 12;

    private PassengerReattach() {}

    /**
     * Re-seats every passenger of {@code ridden} after it has been teleported, then applies
     * its exit velocity once the whole stack is aboard.
     *
     * <p>Boats additionally need a position re-sync or the client keeps drawing them at the
     * departure point.
     *
     * @param ridden
     *            the boat or mount that was just teleported
     * @param rider
     *            the moving rider, re-seated even when the teleport already detached them
     * @param exitVelocity
     *            velocity to apply once everyone is aboard, may be null
     */
    public static void schedule(final Entity ridden, final Entity rider, final Vector exitVelocity)
    {
        final List<Entity> parents = new ArrayList<Entity>();
        final List<Entity> children = new ArrayList<Entity>();
        EntityUtils.collectPassengerPairs(ridden, parents, children);
        // The teleport has usually already detached the moving player, so they will not
        // appear in the collected tree -- put them back explicitly.
        if (!children.contains(rider))
        {
            parents.add(ridden);
            children.add(rider);
        }

        // 2-tick delay: there is no teleport-ack to wait for, the client re-seats immediately.
        new Reattacher(ridden, parents, children, exitVelocity).scheduleIn(2);
    }

    /**
     * Puts a vehicle's passengers back, retrying while the server keeps refusing.
     *
     * <p>Reschedules itself rather than being driven from outside, which is why it holds its
     * own attempt count: the earlier shape passed a one-element array around so an anonymous
     * Runnable could see the number it was incrementing.
     */
    private static final class Reattacher implements Runnable
    {
        private final Entity ridden;
        private final List<Entity> parents;
        private final List<Entity> children;
        private final boolean[] attached;
        private final Vector exitVelocity;
        private int attempts;

        Reattacher(final Entity ridden, final List<Entity> parents, final List<Entity> children,
            final Vector exitVelocity)
        {
            this.ridden = ridden;
            this.parents = parents;
            this.children = children;
            this.attached = new boolean[children.size()];
            this.exitVelocity = exitVelocity;
        }

        void scheduleIn(final long ticks)
        {
            WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), this, ticks);
        }

        @Override
        public void run()
        {
            attempts++;
            try
            {
                if (!ridden.isValid())
                {
                    return;
                }
                if (seatEveryone() == 0)
                {
                    settle();
                }
                else if (attempts < MAX_REATTACH_ATTEMPTS)
                {
                    // Backoff, capped: a passenger that has not arrived by now is not going to
                    // arrive sooner for being asked more often.
                    scheduleIn(Math.min(1L << Math.max(0, attempts - 1), 20L));
                }
                else
                {
                    WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "Failed to reattach passengers to " + ridden.getUniqueId() + " after " + attempts + " attempts");
                }
            }
            catch (final RuntimeException t)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "Exception during passenger reattach: " + t.getMessage());
            }
        }

        /**
         * Seats {@code passenger} on {@code parent}, retrying once via a position sync.
         *
         * <p>Private on purpose: it re-seats once, with no retry and no backoff, which is only
         * ever correct as a step inside {@link #schedule}. Exposed, it would read like the
         * obvious way to seat a passenger and quietly drop the guarantees that actually make
         * re-seating stick.
         *
         * @param parent
         *            the entity to ride
         * @param passenger
         *            the entity to seat
         * @return true if the passenger ends up aboard
         */
        private static boolean attachPassenger(final Entity parent, final Entity passenger)
        {
            try
            {
                if (parent.addPassenger(passenger))
                {
                    return true;
                }
            }
            catch (final RuntimeException t)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "addPassenger failed: " + t.getMessage());
            }
            // An earlier attempt may already have succeeded without reporting it.
            try
            {
                if (parent.getPassengers().contains(passenger))
                {
                    return true;
                }
            }
            catch (final RuntimeException ignore) { /* an unreadable passenger list just means retry */ }
            // A passenger too far from its parent is refused, so close the gap and retry.
            try
            {
                passenger.teleport(parent.getLocation());
                return parent.addPassenger(passenger);
            }
            catch (final RuntimeException t)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "addPassenger after position sync failed: " + t.getMessage());
            }
            return false;
        }

        /**
         * Seats everyone not already aboard.
         *
         * @return how many are still not seated
         */
        private int seatEveryone()
        {
            int remaining = 0;
            for (int i = 0; i < children.size(); i++)
            {
                if (attached[i])
                {
                    continue;
                }
                final Entity psg = children.get(i);
                try
                {
                    if (!psg.isValid())
                    {
                        continue;
                    }
                    if (attachPassenger(parents.get(i), psg))
                    {
                        attached[i] = true;
                    }
                    else
                    {
                        remaining++;
                    }
                }
                catch (final RuntimeException t)
                {
                    WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "Exception during passenger reattach: " + t.getMessage());
                    remaining++;
                }
            }
            return remaining;
        }

        /** Everyone is aboard: give the vehicle its exit speed, and a boat its client re-sync. */
        private void settle()
        {
            try
            {
                ridden.setVelocity(exitVelocity != null ? exitVelocity : new Vector(0, 0, 0));
                ridden.setFireTicks(0);
            }
            catch (final RuntimeException ignore)
            {
                // velocity and fire state are cosmetic
            }
            if (ridden instanceof Boat)
            {
                resyncBoat();
            }
        }

        /**
         * Nudges a boat back to where it already is.
         *
         * <p>A client that has just re-seated in a boat can draw it in the wrong place until
         * something tells it otherwise.
         */
        private void resyncBoat()
        {
            final Location resyncLoc = ridden.getLocation();
            WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), new Runnable()
            {
                @Override
                public void run()
                {
                    try
                    {
                        if (ridden.isValid())
                        {
                            ridden.teleport(resyncLoc);
                        }
                    }
                    catch (final RuntimeException ignore)
                    {
                        // best effort
                    }
                }
            }, 3L);
        }
    }
}
