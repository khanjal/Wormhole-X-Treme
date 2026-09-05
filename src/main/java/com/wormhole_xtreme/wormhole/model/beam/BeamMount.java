package com.wormhole_xtreme.wormhole.model.beam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.utils.EntityUtils;
import com.wormhole_xtreme.wormhole.utils.PassengerReattach;

/**
 * Whatever the traveller was riding when they vanished, and what a beam has to do about it.
 *
 * <p>Beaming used to leave a mount behind without saying so, and not by choice: Bukkit's
 * {@code Entity#teleport(Location)} contract is that "if this entity is riding a vehicle, it
 * will be dismounted prior to teleportation" -- unchanged across the whole 1.20-1.21.10 range
 * this plugin supports. So the single {@code player.teleport(destination)} the sequence used
 * to make silently tipped the traveller off their horse and beamed them alone, leaving the
 * horse standing at the origin.
 *
 * <p>Gates already solved this and beaming now borrows the answer: move the mount, then put
 * the rider back on it ({@link PassengerReattach}, which is where that retry loop lives now
 * that two subsystems need it). Paper's {@code TeleportFlag.EntityState.RETAIN_VEHICLE} would
 * be the one-call version, but it is not in the plain Spigot API at any version in this
 * range, and this plugin ships one jar for Spigot, Paper and Purpur alike.
 *
 * <p>Holding the mount still is the other half, and less obvious. {@link BeamFreeze} locks a
 * traveller by reverting {@code PlayerMoveEvent}, which a rider does not raise -- their
 * position comes from the vehicle, so a frozen player on a horse can still steer it clean out
 * of the departure column during the rise (eighteen ticks by default, which is a long way at
 * a gallop). Rather than fight that, {@link #hold} dismounts the rider at the vanish tick, so
 * the existing freeze does exactly what it was written to do, and switches the mount's AI off
 * so it does not wander off on its own either. Neither is visible to anyone: both are already
 * hidden by {@link BeamVisibility} on that same tick.
 *
 * <p>One instance per sequence, and it is always constructed -- {@link #none} stands in for an
 * unmounted traveller and every method on it is a no-op, so {@code BeamAnimation} never has to
 * branch on null.
 */
final class BeamMount
{
    /** What the traveller was riding, or null if they were on foot. */
    private final Entity mount;

    /**
     * The mount and its whole passenger tree, captured once rather than re-walked. The
     * sequence hides this list at the vanish tick and reveals the same list at the deposit;
     * re-deriving it at the end would miss anything the teleport had since detached, and
     * leave it hidden for good.
     */
    private final List<Entity> stack;

    /** Whether {@link #hold} actually switched AI off, and so owes a {@link #release}. */
    private boolean aiSuspended;

    private BeamMount(final Entity mount, final List<Entity> stack)
    {
        this.mount = mount;
        this.stack = stack;
        this.aiSuspended = false;
    }

    /**
     * A mount that is not there, for a sequence that has not reached its vanish tick yet.
     * Every method on it is a no-op, which is what lets {@code BeamAnimation} hold this field
     * unconditionally instead of null-checking it at each of its four endings.
     *
     * @return an absent mount
     */
    static BeamMount none()
    {
        return new BeamMount(null, Collections.<Entity>emptyList());
    }

    /**
     * Notes what a traveller is riding, at the moment they stop being free to move.
     *
     * @param player the traveller
     * @return a mount, present or not; never null
     */
    static BeamMount capture(final Player player)
    {
        Entity ridden = null;
        try
        {
            ridden = player.getVehicle();
        }
        catch (final RuntimeException ignored)
        {
            // An entity that will not say what it is riding is treated as riding nothing.
        }
        if (ridden == null)
        {
            return none();
        }
        final List<Entity> collected = new ArrayList<Entity>();
        collected.add(ridden);
        EntityUtils.collectPassengerPairs(ridden, new ArrayList<Entity>(), collected);
        return new BeamMount(ridden, collected);
    }

    /**
     * The mount and everything riding it, for {@link BeamVisibility} to hide and reveal.
     *
     * @return the captured stack; empty for an unmounted traveller
     */
    List<Entity> stack()
    {
        return stack;
    }

    /**
     * Parks the mount where it stands and gets the rider off it, so {@link BeamFreeze}'s
     * position lock covers a mounted traveller the same way it covers one on foot.
     *
     * @param rider the traveller to dismount
     */
    void hold(final Player rider)
    {
        if (mount == null)
        {
            return;
        }
        try
        {
            rider.leaveVehicle();
        }
        catch (final RuntimeException ignored)
        {
            // Still worth stopping the mount below even if the dismount was refused.
        }
        if (mount instanceof LivingEntity)
        {
            final LivingEntity living = (LivingEntity) mount;
            try
            {
                if (living.hasAI())
                {
                    living.setAI(false);
                    aiSuspended = true;
                }
            }
            catch (final RuntimeException ignored)
            {
                // A mount that will not give up its AI just stands a little less still.
            }
        }
        try
        {
            mount.setVelocity(new Vector(0, 0, 0));
        }
        catch (final RuntimeException ignored)
        {
            // deliberately silent
        }
    }

    /**
     * Sends the mount after the traveller and re-seats them on it.
     *
     * <p>Called on the teleport tick, immediately after the traveller's own teleport, so the
     * rider is already at the destination and the re-seat is a short hop rather than a
     * cross-world one. No exit velocity: a beam sets a mount down standing still, where a
     * gate shoves it out along the exit facing.
     *
     * @param rider the traveller, already teleported
     * @param destination where the mount is going
     */
    void carry(final Player rider, final Location destination)
    {
        if (mount == null)
        {
            return;
        }
        release();
        if (!mount.isValid())
        {
            return;
        }
        try
        {
            mount.teleport(destination);
        }
        catch (final RuntimeException e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false,
                "Could not beam " + rider.getName() + "'s mount to the destination; they arrive on foot: "
                    + e.getMessage());
            return;
        }
        PassengerReattach.schedule(mount, rider, null);
    }

    /**
     * Gives the mount its AI back, whether or not the sequence ever got as far as moving it.
     *
     * <p>Idempotent, and called from every ending -- the normal one via {@link #carry}, a
     * failed tick via {@code recover}, and a traveller who logs out mid-beam. A mount left
     * with its AI switched off has no way to recover on its own: nothing is still running
     * that would ever turn it back on.
     */
    void release()
    {
        if (!aiSuspended)
        {
            return;
        }
        aiSuspended = false;
        try
        {
            ((LivingEntity) mount).setAI(true);
        }
        catch (final RuntimeException ignored)
        {
            // deliberately silent
        }
    }
}
