package com.wormhole_xtreme.wormhole.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import com.wormhole_xtreme.wormhole.RiddenTeleport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.window.StandIns;
import com.wormhole_xtreme.wormhole.utils.EntityUtils;
import com.wormhole_xtreme.wormhole.utils.PassengerReattach;
import com.wormhole_xtreme.wormhole.utils.WorldUtils;

/**
 * Moving whoever stands in a gate's opening clear before its iris closes over it (#430, #734).
 *
 * <p>{@link StargateBlockSetup#fillGateIris} calls {@link #clearIrisPath} before it places the iris.
 */
final class IrisClearance
{
    private static final String CLEAR_OF_IRIS = " clear of closing iris on gate: ";

    /** Static helpers only. */
    private IrisClearance()
    {
    }

    /**
     * Moves anyone standing in the gate opening clear before the iris covers it.
     *
     * <p>A horizontal gate's iris is real blocks, and
     * {@link StargateBlockSetup#fillGateIris} places them
     * without looking at who was there. Every path that closes an iris can therefore land
     * solid blocks inside a player: the gate shutting down onto an iris that defaults closed,
     * an activation timing out, someone flipping the iris lever, or an IDC being cleared. A
     * traveller walking into the event horizon as the far gate times out is the case that
     * gets reported, because it needs no bad timing on anyone's part -- the shutdown timer
     * picks the moment.
     *
     * <p>A drawn iris cannot bury anybody, and is cleared for anyway: a player left inside one
     * is a player standing in the middle of what everyone else sees as a shut gate.
     *
     * <p>The iris still closes. It is a barrier, and one that could be held open by standing
     * in it would be worth nothing; the occupants are moved rather than the closure refused.
     * They go to the gate's own arrival point, which the shape file already places one block
     * outside the portal, and {@code findSafePlayerLocation} corrects it for terrain that has
     * changed since. A player also gets a few ticks of damage immunity, which is what the
     * remote-iris-locked path in the move listener does for the same reason: the block placed
     * on the tick they leave should not still be able to reach them.
     *
     * <p>Only living entities are moved. Dropped items and arrows do not suffocate, and
     * teleporting a gate's worth of loose items to the exit every time an iris closes would
     * be a surprise of its own.
     *
     * @param gate the gate whose iris is about to close
     */
    static void clearIrisPath(final Stargate gate)
    {
        final World world = gate.getGateWorld();
        final BoundingBox bounds = gate.getGatePortalBounds();
        final Location exit = gate.getGatePlayerTeleportLocation();
        if (world == null || bounds == null || exit == null)
        {
            return;
        }

        // One query for the whole opening, the same shape as the entity sweep: the box
        // encloses the ring, so candidates are still confirmed against the portal blocks.
        final Collection<Entity> candidates = world.getNearbyEntities(bounds);
        if (candidates.isEmpty())
        {
            return;
        }

        Location safe = null;
        final Set<Entity> moved =
            Collections.newSetFromMap(new IdentityHashMap<>());
        for (final Entity entity : candidates)
        {
            try
            {
                // Only a living thing can suffocate, and only one standing where the iris
                // is about to be is in the way.
                if (!(entity instanceof LivingEntity) || StandIns.isStandIn(entity)
                    || !isInIrisPath(gate, entity.getLocation()))
                {
                    continue;
                }

                // Deferred until someone is actually in the way: the safe-location search
                // reads the world, and an iris closing on an empty gate is the normal case.
                if (safe == null)
                {
                    safe = WorldUtils.findSafePlayerLocation(exit);
                }
                moveClearOfIris(outermostVehicle(entity), safe, moved, gate);
            }
            catch (final RuntimeException t)
            {
                // One entity that cannot be moved does not stop the iris closing, or stop
                // the rest of the sweep. Errors are left to propagate rather than being
                // swallowed here, where they would look like an ordinary immovable mob.
                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE,
                    "Failed to move " + entity.getType() + CLEAR_OF_IRIS
                        + gate.getGateName(), t);
            }
        }
    }

    /**
     * Moves a stack clear of a closing iris once, riders and all.
     *
     * <p>A plain teleport of a ridden mount does nothing on Paper 1.20.4, which would leave
     * it and its rider under the iris; the shared ridden move works on every version.
     *
     * @param root
     *            the bottom of the stack
     * @param safe
     *            where it goes
     * @param moved
     *            stacks already moved by this sweep, updated
     * @param gate
     *            the gate, for the log
     */
    private static void moveClearOfIris(final Entity root, final Location safe,
        final Set<Entity> moved, final Stargate gate)
    {
        if (!moved.add(root))
        {
            return;
        }
        final List<Entity> parents = new ArrayList<>();
        final List<Entity> children = new ArrayList<>();
        EntityUtils.collectPassengerPairs(root, parents, children);
        if (!RiddenTeleport.move(root, safe, parents, children))
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE,
                "Could not move " + root.getType() + CLEAR_OF_IRIS + gate.getGateName());
            return;
        }
        if (!children.isEmpty())
        {
            // Five ticks, as at a gate: a rider was teleported, and must acknowledge it first.
            PassengerReattach.schedule(root, parents, children, null, 5L);
        }
        final List<Entity> everyone = new ArrayList<>(children);
        everyone.add(root);
        for (final Entity one : everyone)
        {
            if (one instanceof Player traveller)
            {
                traveller.setNoDamageTicks(5);
            }
        }
        WormholeXTreme.getThisPlugin().prettyLog(Level.FINE,
            "Moved " + root.getType() + CLEAR_OF_IRIS + gate.getGateName());
    }

    /** What an entity is riding, and what that is riding, down to the one on the ground. */
    private static Entity outermostVehicle(final Entity entity)
    {
        Entity root = entity;
        for (int depth = 0; (depth < 16) && (root.getVehicle() != null); depth++)
        {
            root = root.getVehicle();
        }
        return root;
    }

    /**
     * Whether an entity at this location would have iris blocks placed inside it.
     *
     * <p>Both the block the entity stands in and the one above it are checked. A player
     * whose feet are on the block below the opening still has their head in the lowest
     * portal block, and the head is the half that suffocates -- testing the feet alone,
     * which is all the entity sweep needs for deciding who travels, would walk straight
     * past the person most likely to be hurt.
     *
     * <p>Pulled out as its own function because it is the whole decision. What surrounds it
     * -- querying the world for entities, teleporting them -- needs a live server; this does
     * not, and it is the part that can be wrong.
     *
     * @param gate the gate whose iris is closing
     * @param at   where the entity is
     * @return true if the closing iris would occupy the entity's own space
     */
    static boolean isInIrisPath(final Stargate gate, final Location at)
    {
        if (gate == null || at == null)
        {
            return false;
        }
        return gate.isGatePortalBlockAt(at.getBlockX(), at.getBlockY(), at.getBlockZ())
            || gate.isGatePortalBlockAt(at.getBlockX(), at.getBlockY() + 1, at.getBlockZ());
    }
}
