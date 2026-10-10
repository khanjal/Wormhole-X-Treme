package com.wormhole_xtreme.wormhole.model.window;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Bee;
import org.bukkit.entity.Blaze;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Flying;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Vex;
import org.bukkit.entity.WaterMob;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.material.Colorable;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.utils.HiddenEntities;

/**
 * The stand-ins a view shows for the creatures in its far room (#296): real entities in the
 * viewer's world, which only that viewer is shown.
 *
 * <p>Each one is held by the viewer's drawing, which follows its creature and takes it away when
 * the creature goes or the view ends. Every one alive is also listed here, so nothing else mistakes
 * one for a real creature, and so the sweep can take away any the drawing has lost track of.
 */
public final class StandIns
{
    /** The scoreboard tag every stand-in carries, for an admin or another plugin to tell one apart. */
    public static final String TAG = "wormhole_stand_in";

    /** How often stand-ins follow their creatures, while any are shown. */
    static final long FOLLOW_TICKS = 2L;

    /** How long a spawn something refused is left before it is tried again. */
    static final long REFUSED_MILLIS = 5000L;

    /** How much deeper than the room a stand-in already shown may stand before it is taken away. */
    static final int HELD_SLACK = 1;

    /** How far below a creature's feet its floor starts to be looked for: just under a block's top face. */
    private static final double FLOOR_BELOW = 0.05;

    /** How far below a standing creature's feet its floor may be: a fence, a wall or a gate stands half a block above its block. */
    static final double STAND_BELOW = 0.6;

    /** How far below the feet of a creature in the air its floor may be: mid-jump, or a short fall. */
    static final double FALL_BELOW = 2.0;

    /**
     * Types newer than the 1.20 compile path that hover or swim without being a {@link Flying} or a
     * {@link WaterMob}, by their {@code EntityType} name: the happy ghast, its ghastling a baby of the
     * same type, and the nautili of 26.x. Settable for a test.
     */
    static Set<String> hovering = Set.of("HAPPY_GHAST", "NAUTILUS", "ZOMBIE_NAUTILUS");

    /** How far either side of a creature's middle its floor may be, for one standing over a block's edge. */
    private static final double FLOOR_ASIDE = 0.3;

    /** Least movement, squared, worth a teleport. */
    private static final double LEAST_MOVE = 1.0e-4;

    /** Least turn, in degrees, worth a teleport. */
    private static final float LEAST_TURN = 0.5f;

    /** Every stand-in alive, by its own id, so anything can tell one from a real creature. */
    private static final Map<UUID, Entity> ALL = new ConcurrentHashMap<>();

    /** The task following them, or -1 for none. */
    private static int followTask = -1;

    /** Whether a failure has been logged yet: stand-ins are cosmetic, and one log line is enough. */
    private static boolean warned;

    /** One far creature's stand-in, shown to one viewer through one window. */
    static final class StandIn
    {
        final Entity original;
        final Entity copy;
        /** The window it was last placed through, which says where it goes as its creature moves. */
        WindowState window;

        StandIn(final Entity original, final Entity copy, final WindowState window)
        {
            this.original = original;
            this.copy = copy;
            this.window = window;
        }
    }

    /** Static state only. */
    private StandIns()
    {
    }

    /**
     * Whether an entity is a view's stand-in: one this plugin holds, or one carrying {@link #TAG}.
     *
     * @param entity
     *            any entity
     * @return true for a stand-in
     */
    // A mock entity with no UUID is asked this throughout the tests, and a concurrent map refuses a null key.
    @SuppressWarnings("java:S2589")
    public static boolean isStandIn(final Entity entity)
    {
        if (entity == null)
        {
            return false;
        }
        final UUID id = entity.getUniqueId();
        // Removal only ever touches the registry; the tag also catches one this session never made.
        return ((id != null) && ALL.containsKey(id)) || entity.getScoreboardTags().contains(TAG);
    }

    /**
     * @param view
     *            a viewer's drawing
     * @param window
     *            the name a window is offered under
     * @return true if any of the viewer's stand-ins was placed through it
     */
    static boolean shownThrough(final ViewerDrawing view, final String window)
    {
        for (final StandIn standIn : view.standIns.values())
        {
            if (standIn.window.name().equals(window))
            {
                return true;
            }
        }
        return false;
    }

    /** @return how many stand-ins are alive, across every viewer */
    static int count()
    {
        return ALL.size();
    }

    /** Notes a stand-in just spawned. */
    static void track(final Entity standIn)
    {
        ALL.put(standIn.getUniqueId(), standIn);
    }

    /** Forgets one that has gone. */
    static void untrack(final Entity standIn)
    {
        ALL.remove(standIn.getUniqueId(), standIn);
    }

    /**
     * Shows a viewer these stand-ins and no others: the new spawned, the kept moved after their
     * creatures, the rest taken away.
     *
     * @param viewer
     *            who sees them
     * @param view
     *            their drawing, which holds them
     * @param wanted
     *            the creatures to show, and where
     * @param now
     *            the time, for leaving a refused spawn a while
     */
    static void show(final Player viewer, final ViewerDrawing view, final List<FarCreatures.Wanted> wanted,
        final long now)
    {
        final Set<UUID> keep = new HashSet<>();
        for (final FarCreatures.Wanted one : wanted)
        {
            final UUID id = one.original().getUniqueId();
            final StandIn held = view.standIns.get(id);
            if ((held != null) && held.copy.isValid())
            {
                held.window = one.window();
                moveTo(held, one.here());
                keep.add(id);
            }
            else if (spawnFor(viewer, view, one, now))
            {
                keep.add(id);
            }
        }
        final Iterator<Map.Entry<UUID, StandIn>> held = view.standIns.entrySet().iterator();
        while (held.hasNext())
        {
            final Map.Entry<UUID, StandIn> entry = held.next();
            if (!keep.contains(entry.getKey()))
            {
                remove(entry.getValue().copy);
                held.remove();
            }
        }
        // Only the creatures still wanted keep a refusal against them.
        final Set<UUID> wantedIds = new HashSet<>();
        wanted.forEach(one -> wantedIds.add(one.original().getUniqueId()));
        view.refused.keySet().retainAll(wantedIds);
        if (!view.standIns.isEmpty())
        {
            followWhileShown();
        }
    }

    /**
     * Moves one viewer's stand-ins after their creatures, taking away any whose creature has gone,
     * unloaded or left the room.
     *
     * @param view
     *            the viewer's drawing
     */
    static void follow(final ViewerDrawing view)
    {
        final Iterator<StandIn> held = view.standIns.values().iterator();
        while (held.hasNext())
        {
            final StandIn standIn = held.next();
            final Location at = whereNow(view.world, standIn);
            if (at == null)
            {
                remove(standIn.copy);
                held.remove();
            }
            else
            {
                moveTo(standIn, at);
            }
        }
    }

    /**
     * Where a stand-in stands now its creature has moved.
     *
     * @param here
     *            the viewer's world
     * @param standIn
     *            the stand-in
     * @return where, or null if its creature has gone, unloaded, changed world or left the room
     */
    static Location whereNow(final World here, final StandIn standIn)
    {
        if (!standIn.original.isValid() || !standIn.copy.isValid())
        {
            return null;
        }
        final Location far = standIn.original.getLocation();
        final World farWorld = far.getWorld();
        if ((farWorld == null) || !farWorld.getName().equals(standIn.window.capture.worldName()))
        {
            return null;
        }
        final Location at = FarCreatures.hereOf(here, standIn.window.shape, far);
        return inRoom(standIn.window, at, true) ? at : null;
    }

    /**
     * Whether a stand-in placed here is inside the room a window draws, in a chunk the viewer's world
     * has loaded: never in front of the opening, where it would walk out into the viewer's room.
     *
     * @param window
     *            the window
     * @param at
     *            where the stand-in would stand
     * @return true if it may stand there
     */
    static boolean inRoom(final WindowState window, final Location at)
    {
        return inRoom(window, at, false);
    }

    /**
     * The same, a block deeper for a stand-in already shown, so one at the room's far edge is not
     * taken away and spawned again as its creature wanders across it.
     *
     * @param window
     *            the window
     * @param at
     *            where the stand-in would stand
     * @param held
     *            true for a stand-in already shown
     * @return true if it may stand there
     */
    static boolean inRoom(final WindowState window, final Location at, final boolean held)
    {
        final int x = at.getBlockX();
        final int y = at.getBlockY();
        final int z = at.getBlockZ();
        final World here = at.getWorld();
        // Skipped rather than clamped: a stand-in moved to the world's edge stands somewhere its creature is not.
        return Windows.insideFixed(window, x, y, z, window.depth() + (held ? HELD_SLACK : 0)) && (y >= here.getMinHeight())
            && (y < here.getMaxHeight()) && here.isChunkLoaded(x >> 4, z >> 4)
            && here.getChunkAt(x >> 4, z >> 4).isEntitiesLoaded();
    }

    /**
     * Whether a creature standing on the ground stands on something this viewer has been drawn: a
     * block of the captured room under its feet, sent to them. A capture is old and a clipped view
     * draws only what is seen, so a creature can stand where the drawn room has nothing, and its
     * stand-in would stand on air (#296).
     *
     * @param view
     *            the viewer's drawing, as last sent
     * @param window
     *            the window it is seen through
     * @param far
     *            where the creature stands, in the far world
     * @return true if a drawn, captured block is under its feet or under an edge of it
     */
    static boolean onDrawnFloor(final ViewerDrawing view, final WindowState window, final Location far)
    {
        return onDrawnFloor(view, window, far, STAND_BELOW);
    }

    /**
     * The same, looking for the floor down to a distance below the feet: half a block and a little for
     * one standing, which finds a fence or a wall it stands on in the block below its feet' own, and
     * two blocks for one in the air, mid-jump or falling.
     *
     * @param below
     *            how far below the feet the floor may be
     * @return true if a drawn, captured block is that close under its feet or under an edge of it
     */
    static boolean onDrawnFloor(final ViewerDrawing view, final WindowState window, final Location far,
        final double below)
    {
        final int top = (int) Math.floor(far.getY() - FLOOR_BELOW);
        final int bottom = (int) Math.floor(far.getY() - below);
        for (int y = top; y >= bottom; y--)
        {
            if (drawnFloorAt(view, window, far, y))
            {
                return true;
            }
        }
        return false;
    }

    /** Whether a drawn, captured block at this height is under the feet or under an edge of them. */
    private static boolean drawnFloorAt(final ViewerDrawing view, final WindowState window, final Location far, final int y)
    {
        for (final double[] aside : new double[][] { { 0.0, 0.0 }, { -FLOOR_ASIDE, -FLOOR_ASIDE },
            { FLOOR_ASIDE, -FLOOR_ASIDE }, { -FLOOR_ASIDE, FLOOR_ASIDE }, { FLOOR_ASIDE, FLOOR_ASIDE } })
        {
            final int x = (int) Math.floor(far.getX() + aside[0]);
            final int z = (int) Math.floor(far.getZ() + aside[1]);
            if (!window.capture.isAir(x, y, z))
            {
                final WindowShape.Spot here = window.shape.hereOf(x, y, z);
                if (view.drawn.containsKey(Windows.key(here.x(), here.y(), here.z())))
                {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether a creature needs no floor: no gravity, a flyer, in water, swimming, gliding or climbing.
     *
     * @param creature
     *            a far creature
     * @return true if it is shown wherever it is
     */
    static boolean floats(final Entity creature)
    {
        return !creature.hasGravity() || hovering.contains(String.valueOf(creature.getType()))
            || (creature instanceof Flying) || (creature instanceof Bat) || (creature instanceof Bee)
            || (creature instanceof Parrot) || (creature instanceof Allay) || (creature instanceof Vex)
            || (creature instanceof Blaze) || (creature instanceof WaterMob) || creature.isInWater()
            || ((creature instanceof LivingEntity living) && (living.isClimbing() || living.isSwimming() || living.isGliding()));
    }

    /**
     * How far below its feet a creature's floor may be.
     *
     * @param creature
     *            a far creature that needs a floor
     * @return {@link #STAND_BELOW} on the ground, {@link #FALL_BELOW} in the air
     */
    static double floorReach(final Entity creature)
    {
        return creature.isOnGround() ? STAND_BELOW : FALL_BELOW;
    }

    /**
     * Takes away every stand-in one viewer is shown.
     *
     * @param view
     *            their drawing
     */
    static void removeAll(final ViewerDrawing view)
    {
        view.standIns.values().forEach(standIn -> remove(standIn.copy));
        view.standIns.clear();
        view.refused.clear();
    }

    /** Takes away every stand-in there is, as the plugin stops or reloads. */
    public static void removeEverything()
    {
        for (final Entity standIn : new ArrayList<>(ALL.values()))
        {
            remove(standIn);
        }
        ALL.clear();
        stopFollowing();
        // Once per enable, not once per JVM: a reload that fixed nothing says so again.
        warned = false;
    }

    /**
     * Takes away any stand-in no viewer's drawing holds: one whose drawing was dropped without it.
     *
     * @param views
     *            every viewer's drawing
     */
    static void sweepStrays(final Collection<ViewerDrawing> views)
    {
        if (!ALL.isEmpty())
        {
            final Set<Entity> held = Collections.newSetFromMap(new IdentityHashMap<>());
            views.forEach(view -> view.standIns.values().forEach(standIn -> held.add(standIn.copy)));
            for (final Entity standIn : new ArrayList<>(ALL.values()))
            {
                if (!held.contains(standIn))
                {
                    remove(standIn);
                }
            }
        }
        if (ALL.isEmpty())
        {
            stopFollowing();
        }
    }

    /** Makes a stand-in look like its creature at a glance, and leaves it inert. */
    static void dress(final Entity copy, final Entity original)
    {
        copy.addScoreboardTag(TAG);
        copy.setSilent(true);
        copy.setInvulnerable(true);
        copy.setGravity(false);
        copy.setCustomName(original.getCustomName());
        copy.setCustomNameVisible(original.isCustomNameVisible());
        if (copy instanceof LivingEntity living)
        {
            living.setAI(false);
            living.setCollidable(false);
            living.setCanPickupItems(false);
            living.setRemoveWhenFarAway(false);
            if (original instanceof LivingEntity source)
            {
                wear(living, source);
            }
        }
        if ((copy instanceof Colorable dyed) && (original instanceof Colorable source))
        {
            dyed.setColor(source.getColor());
        }
        // Declared on Sheep up to 1.20.4 and on its Shearable from 1.21; called through Sheep, it links on both.
        if ((copy instanceof Sheep shorn) && (original instanceof Sheep source))
        {
            shorn.setSheared(source.isSheared());
        }
        age(copy, original);
    }

    /** Spawns one stand-in, unless something refused it a moment ago. */
    private static boolean spawnFor(final Player viewer, final ViewerDrawing view, final FarCreatures.Wanted one,
        final long now)
    {
        final UUID id = one.original().getUniqueId();
        final StandIn gone = view.standIns.remove(id);
        if (gone != null)
        {
            remove(gone.copy);
        }
        final Long refusedAt = view.refused.get(id);
        if ((refusedAt != null) && ((now - refusedAt) < REFUSED_MILLIS))
        {
            return false;
        }
        final Entity copy = spawn(viewer, one);
        if (copy == null)
        {
            // A protection plugin that refuses mob spawns would otherwise be asked every redraw.
            view.refused.put(id, now);
            return false;
        }
        view.refused.remove(id);
        view.standIns.put(id, new StandIn(one.original(), copy, one.window()));
        return true;
    }

    /** The stand-in itself, or null if it could not be made or something refused it. */
    // A mock creature that stubs no type has none, where a server always gives one.
    @SuppressWarnings("java:S2583")
    private static Entity spawn(final Player viewer, final FarCreatures.Wanted one)
    {
        final Entity original = one.original();
        final EntityType type = original.getType();
        final Class<? extends Entity> kind = (type == null) ? null : type.getEntityClass();
        if (kind == null)
        {
            return null;
        }
        // Tracked before it is dressed, so a throw anywhere after the entity exists still finds it to remove.
        final Entity[] made = new Entity[1];
        try
        {
            final Entity copy = HiddenEntities.spawnFor(WormholeXTreme.getThisPlugin(), viewer, one.here(), kind,
                entity ->
                {
                    made[0] = entity;
                    track(entity);
                    dress(entity, original);
                });
            if ((copy == null) && (made[0] != null))
            {
                // Refused after it was made: never added, or added and taken out again.
                remove(made[0]);
            }
            return copy;
        }
        catch (final RuntimeException | LinkageError failed)
        {
            if (made[0] != null)
            {
                remove(made[0]);
            }
            failedOnce("Could not spawn a stand-in for a far creature", failed);
            return null;
        }
    }

    /**
     * Logs a stand-in failure, the first time only, so a broken server is told once rather than
     * every redraw of every viewer.
     *
     * @param what
     *            what failed
     * @param failure
     *            why
     */
    static void failedOnce(final String what, final Throwable failure)
    {
        if (!warned)
        {
            warned = true;
            final WormholeXTreme plugin = WormholeXTreme.getThisPlugin();
            if (plugin != null)
            {
                plugin.prettyLog(Level.WARNING, what, failure);
            }
        }
    }

    /** Puts on a stand-in what its creature wears and holds. */
    private static void wear(final LivingEntity copy, final LivingEntity original)
    {
        final EntityEquipment from = original.getEquipment();
        final EntityEquipment to = copy.getEquipment();
        if ((from != null) && (to != null))
        {
            to.setArmorContents(from.getArmorContents());
            to.setItemInMainHand(from.getItemInMainHand());
            to.setItemInOffHand(from.getItemInOffHand());
        }
    }

    /** A baby stand-in for a baby, and a grown one for one that has grown up since. */
    private static void age(final Entity copy, final Entity original)
    {
        if ((copy instanceof Ageable young) && (original instanceof Ageable source) && (young.isAdult() != source.isAdult()))
        {
            if (source.isAdult())
            {
                young.setAdult();
            }
            else
            {
                young.setBaby();
            }
        }
    }

    /** Moves a stand-in to where its creature now shows, if that has changed. */
    // A mock stand-in that stubs no place has none, where a server always gives one.
    @SuppressWarnings("java:S2589")
    private static void moveTo(final StandIn standIn, final Location at)
    {
        final Location was = standIn.copy.getLocation();
        if ((was == null) || moved(was, at))
        {
            standIn.copy.teleport(at);
        }
        age(standIn.copy, standIn.original);
    }

    /** Whether two places differ by enough to send. */
    static boolean moved(final Location was, final Location at)
    {
        final double dx = was.getX() - at.getX();
        final double dy = was.getY() - at.getY();
        final double dz = was.getZ() - at.getZ();
        return (((dx * dx) + (dy * dy) + (dz * dz)) > LEAST_MOVE) || (Math.abs(was.getYaw() - at.getYaw()) > LEAST_TURN)
            || (Math.abs(was.getPitch() - at.getPitch()) > LEAST_TURN);
    }

    /** Takes one stand-in out of the world. */
    private static void remove(final Entity standIn)
    {
        untrack(standIn);
        try
        {
            standIn.remove();
        }
        catch (final RuntimeException gone)
        {
            // Already gone with its chunk or its world.
        }
    }

    /** Starts the task that follows stand-ins, if it is not already running. */
    private static void followWhileShown()
    {
        if (followTask != -1)
        {
            return;
        }
        try
        {
            followTask = WormholeXTreme.getScheduler().scheduleSyncRepeatingTask(WormholeXTreme.getThisPlugin(),
                Windows::followStandIns, FOLLOW_TICKS, FOLLOW_TICKS);
        }
        catch (final RuntimeException noScheduler)
        {
            // No scheduler yet, during startup or in tests: each redraw and sweep still moves them.
            followTask = -1;
        }
    }

    /** Stops the following task, once no stand-in is left to follow. */
    static void stopFollowing()
    {
        if (followTask == -1)
        {
            return;
        }
        final int task = followTask;
        followTask = -1;
        try
        {
            WormholeXTreme.getScheduler().cancelTask(task);
        }
        catch (final RuntimeException noScheduler)
        {
            // The scheduler has gone with the plugin, and the task with it.
        }
    }

    /** @return true while the following task runs, for a test */
    static boolean following()
    {
        return followTask != -1;
    }
}
