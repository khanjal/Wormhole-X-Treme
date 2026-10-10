package com.wormhole_xtreme.wormhole.model.window;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.ComplexLivingEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.Snowman;
import org.bukkit.entity.Wither;
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

    /** What {@link #roomBox} gives where there is nowhere to look. */
    static final int[] NOWHERE = {};

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
     * Whether a creature in a far room is shown: a mob, not a player ({@link FarPlayers}), not scenery, and not
     * somebody's companion or one of the stand-ins themselves, and nothing another plugin hides by
     * default or a boss whose copy would bring its bar or its parts with it. Nor a snow golem, which
     * lays snow, or a shulker, which teleports, with no AI at all.
     *
     * @param entity
     *            an entity in a far room
     * @return true if it is given a stand-in
     */
    static boolean copied(final Entity entity)
    {
        return (entity instanceof LivingEntity living) && !(entity instanceof Player)
            && !(entity instanceof ArmorStand) && !(entity instanceof ComplexLivingEntity) && !(entity instanceof Wither)
            && !(entity instanceof Snowman) && !(entity instanceof Shulker)
            && !living.isInvisible() && entity.isValid() && entity.isVisibleByDefault()
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
     * @return the box to look in, the same way round, or {@link #NOWHERE} where the two do not meet
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
                return NOWHERE;
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
        return inRoom(far, box, new int[3]);
    }

    /**
     * The same, counting what was passed over.
     *
     * @param far
     *            the far world
     * @param box
     *            {@code {minX, minY, minZ, maxX, maxY, maxZ}}
     * @param tally
     *            filled with chunks not loaded, chunks whose entities had not loaded, and entities not copied
     * @return the creatures to copy; empty when nothing there is loaded
     */
    static List<Entity> inRoom(final World far, final int[] box, final int[] tally)
    {
        return inRoom(far, box, tally, false);
    }

    /**
     * The same, with the players there too when they are shown.
     *
     * @param far
     *            the far world
     * @param box
     *            {@code {minX, minY, minZ, maxX, maxY, maxZ}}
     * @param tally
     *            filled with chunks not loaded, chunks whose entities had not loaded, and entities not copied
     * @param players
     *            true to take the players {@link FarPlayers#copied} lets through, with {@code mirror-show-players} on
     * @return the creatures to copy; empty when nothing there is loaded
     */
    static List<Entity> inRoom(final World far, final int[] box, final int[] tally, final boolean players)
    {
        final Set<Long> ready = readyChunks(far, box, tally);
        if (ready.isEmpty())
        {
            return List.of();
        }
        final List<Entity> found = new ArrayList<>();
        final BoundingBox bounds = new BoundingBox(box[0], box[1], box[2], box[3] + 1.0, box[4] + 1.0, box[5] + 1.0);
        for (final Entity entity : far.getNearbyEntities(bounds))
        {
            final Location at = entity.getLocation();
            if (ready.contains(Windows.chunkKey(at.getBlockX() >> 4, at.getBlockZ() >> 4)))
            {
                if (copied(entity) || (players && FarPlayers.copied(entity)))
                {
                    found.add(entity);
                }
                else
                {
                    tally[2]++;
                }
            }
        }
        return found;
    }

    /** The chunks of a box loaded with their entities, counting the others; loads nothing to find out. */
    private static Set<Long> readyChunks(final World far, final int[] box, final int[] tally)
    {
        final Set<Long> ready = new HashSet<>();
        for (int cx = box[0] >> 4; cx <= (box[3] >> 4); cx++)
        {
            for (int cz = box[2] >> 4; cz <= (box[5] >> 4); cz++)
            {
                // isChunkLoaded first: getChunkAt loads a chunk that is not, and Chunk.getEntities
                // would load its entities, so neither is asked of one the server does not have.
                if (!far.isChunkLoaded(cx, cz))
                {
                    tally[0]++;
                }
                else if (far.getChunkAt(cx, cz).isEntitiesLoaded())
                {
                    ready.add(Windows.chunkKey(cx, cz));
                }
                else
                {
                    tally[1]++;
                }
            }
        }
        return ready;
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
        return choose(candidates, eye, most, wanted -> true);
    }

    /**
     * The same, asking a test of each nearest first and none once the cap is reached: the same
     * answer as testing every one and taking the nearest that pass, for a fraction of the tests.
     *
     * @param candidates
     *            every far creature in the room, in the order the windows were looked through
     * @param eye
     *            the viewer's eye
     * @param most
     *            the cap
     * @param shown
     *            whether one is to be shown; asked only while the cap is not reached
     * @return those to show
     */
    static List<Wanted> choose(final List<Wanted> candidates, final Location eye, final int most,
        final Predicate<Wanted> shown)
    {
        final List<Wanted> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparingDouble(wanted -> apart(wanted.here(), eye)));
        final Set<UUID> taken = new HashSet<>();
        final List<Wanted> chosen = new ArrayList<>();
        for (final Wanted wanted : sorted)
        {
            if ((chosen.size() < most) && !taken.contains(wanted.original().getUniqueId()) && shown.test(wanted))
            {
                taken.add(wanted.original().getUniqueId());
                chosen.add(wanted);
            }
        }
        return chosen;
    }

    /**
     * Which test a creature is shown by: one with a stand-in already is kept by the looser, so the
     * edge of the view does not spawn and remove it on every step; a new one needs the stricter.
     *
     * @param held
     *            the creatures with a stand-in now
     * @param kept
     *            the test a held creature passes to stay
     * @param fresh
     *            the test a new one passes to be spawned
     * @return the test for {@link #choose}
     */
    static Predicate<Wanted> keepOrShow(final Set<UUID> held, final Predicate<Wanted> kept, final Predicate<Wanted> fresh)
    {
        return wanted -> held.contains(wanted.original().getUniqueId()) ? kept.test(wanted) : fresh.test(wanted);
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
