package com.wormhole_xtreme.wormhole.model.window;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import com.wormhole_xtreme.wormhole.model.freya.FreyaCompanion;

/**
 * Which creatures in a window's far room a viewer is shown, and where each one's stand-in stands
 * (#296).
 *
 * <p>The far room is read only where the server already has it: a chunk that is not loaded, or
 * whose entities have not loaded yet, is passed over rather than loaded. Nobody there means
 * nothing to show.
 */
public final class FarCreatures
{
    /** The most stand-ins one viewer is shown, nearest their eye first. */
    static final int MOST_PER_VIEWER = 20;

    /**
     * A far creature, and where its stand-in stands in the viewer's world.
     *
     * @param original
     *            the creature in the far room
     * @param here
     *            where it shows, through the window
     * @param window
     *            the window it shows through
     */
    record Wanted(Entity original, Location here, WindowState window)
    {
    }

    /** Static methods only. */
    private FarCreatures()
    {
    }

    /**
     * Whether a creature in a far room is shown: a mob, not a player, not scenery, and not
     * somebody's companion or one of the stand-ins themselves.
     *
     * @param entity
     *            an entity in a far room
     * @return true if it is given a stand-in
     */
    static boolean copied(final Entity entity)
    {
        return (entity instanceof LivingEntity living) && !(entity instanceof Player)
            && !(entity instanceof ArmorStand) && !living.isInvisible() && entity.isValid()
            && !FreyaCompanion.isCompanion(entity) && !StandIns.isStandIn(entity);
    }

    /**
     * The part of a far room worth looking in: what a view this deep could show, inside the capture.
     *
     * @param shape
     *            the window
     * @param depth
     *            how deep its view is drawn
     * @param captured
     *            the capture's box, {@code {minX, minY, minZ, maxX, maxY, maxZ}}
     * @return the box to look in, the same way round, or null where the two do not meet
     */
    static int[] roomBox(final WindowShape shape, final int depth, final int[] captured)
    {
        final int[] view = shape.farBox(depth);
        final int[] box = new int[6];
        for (int axis = 0; axis < 3; axis++)
        {
            box[axis] = Math.max(view[axis], captured[axis]);
            box[axis + 3] = Math.min(view[axis + 3], captured[axis + 3]);
            if (box[axis] > box[axis + 3])
            {
                return null;
            }
        }
        return box;
    }

    /**
     * The creatures standing in a box of the far world, from chunks the server already has with
     * their entities: never a chunk loaded, nor its entities, to answer.
     *
     * @param far
     *            the far world
     * @param box
     *            {@code {minX, minY, minZ, maxX, maxY, maxZ}}
     * @return the creatures to copy; empty when nothing there is loaded
     */
    static List<Entity> inRoom(final World far, final int[] box)
    {
        final Set<Long> ready = new HashSet<>();
        for (int cx = box[0] >> 4; cx <= (box[3] >> 4); cx++)
        {
            for (int cz = box[2] >> 4; cz <= (box[5] >> 4); cz++)
            {
                // isChunkLoaded first: getChunkAt loads a chunk that is not, and Chunk.getEntities
                // would load its entities, so neither is asked of one the server does not have.
                if (far.isChunkLoaded(cx, cz) && far.getChunkAt(cx, cz).isEntitiesLoaded())
                {
                    ready.add(Windows.chunkKey(cx, cz));
                }
            }
        }
        if (ready.isEmpty())
        {
            return List.of();
        }
        final List<Entity> found = new ArrayList<>();
        final BoundingBox bounds = new BoundingBox(box[0], box[1], box[2], box[3] + 1.0, box[4] + 1.0, box[5] + 1.0);
        for (final Entity entity : far.getNearbyEntities(bounds))
        {
            if (copied(entity))
            {
                final Location at = entity.getLocation();
                if (ready.contains(Windows.chunkKey(at.getBlockX() >> 4, at.getBlockZ() >> 4)))
                {
                    found.add(entity);
                }
            }
        }
        return found;
    }

    /**
     * Where a far creature shows behind a window: its position mapped as the blocks are, facing
     * turned or flipped with them.
     *
     * @param here
     *            the viewer's world
     * @param shape
     *            the window
     * @param far
     *            where the creature stands
     * @return where its stand-in stands
     */
    static Location hereOf(final World here, final WindowShape shape, final Location far)
    {
        final double[] at = shape.hereOf(far.getX(), far.getY(), far.getZ());
        return new Location(here, at[0], at[1], at[2], shape.hereYaw(far.getYaw()), far.getPitch());
    }

    /**
     * The stand-ins to show, nearest the eye first, each creature once and no more than the cap.
     *
     * @param candidates
     *            every far creature in view, in the order the windows were looked through
     * @param eye
     *            the viewer's eye
     * @param most
     *            the cap
     * @return those to show
     */
    static List<Wanted> nearest(final List<Wanted> candidates, final Location eye, final int most)
    {
        final List<Wanted> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparingDouble(wanted -> apart(wanted.here(), eye)));
        final Set<UUID> taken = new HashSet<>();
        final List<Wanted> chosen = new ArrayList<>();
        for (final Wanted wanted : sorted)
        {
            if ((chosen.size() < most) && taken.add(wanted.original().getUniqueId()))
            {
                chosen.add(wanted);
            }
        }
        return chosen;
    }

    /** How far apart two places are, squared, by their numbers alone. */
    private static double apart(final Location one, final Location other)
    {
        final double dx = one.getX() - other.getX();
        final double dy = one.getY() - other.getY();
        final double dz = one.getZ() - other.getZ();
        return (dx * dx) + (dy * dy) + (dz * dz);
    }
}
