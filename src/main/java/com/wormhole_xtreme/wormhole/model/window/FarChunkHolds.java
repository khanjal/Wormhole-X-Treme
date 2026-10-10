package com.wormhole_xtreme.wormhole.model.window;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.utils.ChunkTickets;

/**
 * Holds a small area in front of a watched window's far side loaded, so the creatures there show on
 * the first look rather than only once somebody has walked over to load it (#296).
 *
 * <p>While a window is being drawn for at least one viewer, the chunks in front of where it goes
 * are held with the plugin's chunk ticket, through {@link ChunkTickets} so a chunk a ring or a pet
 * also holds keeps one ticket. Let go a grace period after the last viewer stops, at once on a
 * shutdown, a reset or the far world unloading. Loaded a couple a tick, nearest the arrival first,
 * asynchronously where the server can, and never generated: a chunk nobody has made stays unmade.
 */
public final class FarChunkHolds
{
    /** How long a window's area is kept after its last viewer, so one flicking in and out does not churn tickets. */
    static final long GRACE_MILLIS = 10_000L;

    /** The most chunks held across every window. */
    static final int MOST_HELD = 400;

    /** How many chunks are asked for a tick. */
    static final int PER_TICK = 2;

    /** One chunk of one world. */
    record Area(String world, int x, int z)
    {
    }

    /** Loads a chunk without generating it, and hands it over, or null for one never generated. */
    @FunctionalInterface
    interface Loader
    {
        void load(World world, int x, int z, Consumer<Chunk> loaded);
    }

    /** One window's hold: the chunks it wants, and when it lets go, or {@link Long#MAX_VALUE} while watched. */
    private static final class Claim
    {
        List<Area> areas = List.of();
        long releaseAt = Long.MAX_VALUE;
    }

    /** Each window's claim, by window name. */
    private static final Map<String, Claim> CLAIMS = new LinkedHashMap<>();

    /** The chunks held, with the chunk {@link ChunkTickets} was given. */
    private static final Map<Area, Chunk> HELD = new HashMap<>();

    /** The chunks still to ask for, nearest their arrival first. */
    private static final Deque<Area> QUEUE = new ArrayDeque<>();

    /** The chunks asked for and not yet handed over. */
    private static final Set<Area> LOADING = new HashSet<>();

    /** Chunks found never generated, not asked for again while wanted. */
    private static final Set<Area> UNMADE = new HashSet<>();

    /** The chunks wanted now, after the cap. */
    private static Set<Area> wanted = Set.of();

    /** Paper's {@code getChunkAtAsync(int, int, boolean)}, or null on a server without it. */
    private static final Method ASYNC = findAsync();

    /** How chunks are loaded. */
    private static Loader loader = FarChunkHolds::loadReal;

    private static int task = -1;
    private static boolean warnedCap;

    /** Static state only. */
    private FarChunkHolds()
    {
    }

    /**
     * The chunks a window's area covers: the arrival's chunk and {@code radius} chunks ahead of it,
     * and {@code radius} either side, nearest the arrival first. Nothing behind it, which no view shows.
     *
     * @param destination
     *            where the window goes, facing the way its view looks
     * @param radius
     *            chunks ahead and either side
     * @return the chunks, nearest first; empty at radius 0
     */
    static List<Area> ahead(final Place destination, final int radius)
    {
        final List<Area> areas = new ArrayList<>();
        if (radius <= 0)
        {
            return areas;
        }
        final WindowShape.Spot forward = WindowShape.aheadOf(destination.yaw());
        final int ax = ((int) Math.floor(destination.x())) >> 4;
        final int az = ((int) Math.floor(destination.z())) >> 4;
        for (int far = 0; far <= radius; far++)
        {
            for (int aside = 0; aside <= radius; aside++)
            {
                for (final int side : (aside == 0) ? new int[] { 0 } : new int[] { -aside, aside })
                {
                    // The step right of forward is (-forward.z, forward.x).
                    areas.add(new Area(destination.worldName(), ax + (far * forward.x()) - (side * forward.z()),
                        az + (far * forward.z()) + (side * forward.x())));
                }
            }
        }
        return areas;
    }

    /**
     * Brings the holds in line with the windows being watched now: their areas are wanted, every
     * other window's is let go once its grace is up, and what is wanted is held up to the cap.
     *
     * @param watched
     *            each watched window's name and area, nearest its viewers first
     * @param now
     *            the time
     */
    static void settle(final Map<String, List<Area>> watched, final long now)
    {
        for (final Map.Entry<String, List<Area>> window : watched.entrySet())
        {
            final Claim claim = CLAIMS.computeIfAbsent(window.getKey(), name -> new Claim());
            claim.areas = window.getValue();
            claim.releaseAt = Long.MAX_VALUE;
        }
        expire(watched, now);
        wanted = capped(watched);
        for (final Area area : new ArrayList<>(HELD.keySet()))
        {
            if (!wanted.contains(area))
            {
                let(area);
            }
        }
        QUEUE.removeIf(area -> !wanted.contains(area));
        UNMADE.retainAll(wanted);
        for (final Area area : wanted)
        {
            if (!HELD.containsKey(area) && !LOADING.contains(area) && !UNMADE.contains(area) && !QUEUE.contains(area))
            {
                QUEUE.add(area);
            }
        }
        if (!QUEUE.isEmpty())
        {
            pace();
        }
    }

    /** Starts the grace of each window no longer watched, and drops the claims whose grace is up. */
    private static void expire(final Map<String, List<Area>> watched, final long now)
    {
        final Iterator<Map.Entry<String, Claim>> claims = CLAIMS.entrySet().iterator();
        while (claims.hasNext())
        {
            final Map.Entry<String, Claim> claim = claims.next();
            if (!watched.containsKey(claim.getKey()))
            {
                if (claim.getValue().releaseAt == Long.MAX_VALUE)
                {
                    claim.getValue().releaseAt = now + GRACE_MILLIS;
                }
                else if (now >= claim.getValue().releaseAt)
                {
                    claims.remove();
                }
            }
        }
    }

    /** Every claimed area, the watched windows' first and nearest first, up to {@link #MOST_HELD}. */
    private static Set<Area> capped(final Map<String, List<Area>> watched)
    {
        final Set<Area> all = new LinkedHashSet<>();
        watched.values().forEach(all::addAll);
        CLAIMS.values().forEach(claim -> all.addAll(claim.areas));
        if (all.size() <= MOST_HELD)
        {
            return all;
        }
        if (!warnedCap)
        {
            warnedCap = true;
            final WormholeXTreme plugin = WormholeXTreme.getThisPlugin();
            if (plugin != null)
            {
                plugin.prettyLog(Level.WARNING, "Views want " + all.size() + " chunks held for their creatures; holding the "
                    + MOST_HELD + " nearest (mirror-entity-load-radius)");
            }
        }
        final Set<Area> kept = new LinkedHashSet<>();
        for (final Area area : all)
        {
            if (kept.size() < MOST_HELD)
            {
                kept.add(area);
            }
        }
        return kept;
    }

    /** Asks for the next few chunks: the pacing task's step. */
    static void step()
    {
        for (int i = 0; (i < PER_TICK) && !QUEUE.isEmpty(); i++)
        {
            final Area area = QUEUE.poll();
            final World world = Bukkit.getWorld(area.world());
            if (world != null)
            {
                LOADING.add(area);
                loader.load(world, area.x(), area.z(), chunk -> loaded(area, chunk));
            }
        }
        if (QUEUE.isEmpty())
        {
            stopPacing();
        }
    }

    /** Holds a chunk once it is in, if it is still wanted. */
    static void loaded(final Area area, final Chunk chunk)
    {
        LOADING.remove(area);
        if (chunk == null)
        {
            UNMADE.add(area);
        }
        else if (wanted.contains(area) && !HELD.containsKey(area))
        {
            ChunkTickets.hold(chunk);
            HELD.put(area, chunk);
        }
    }

    /**
     * How many of a window's chunks are held, for {@code mirror debug}.
     *
     * @param window
     *            the window's name
     * @return {@code {held, wanted}}
     */
    static int[] heldFor(final String window)
    {
        final Claim claim = CLAIMS.get(window);
        if (claim == null)
        {
            return new int[] { 0, 0 };
        }
        int held = 0;
        for (final Area area : claim.areas)
        {
            if (HELD.containsKey(area))
            {
                held++;
            }
        }
        return new int[] { held, claim.areas.size() };
    }

    /** @return true while any window has a claim or any chunk is held, so the sweep keeps settling them */
    static boolean holdsAny()
    {
        return !CLAIMS.isEmpty() || !HELD.isEmpty();
    }

    /** @return how many chunks are held now, across every window */
    static int heldCount()
    {
        return HELD.size();
    }

    /**
     * Lets go of every chunk held in a world that is unloading.
     *
     * @param world
     *            the world's name
     */
    public static void forgetWorld(final String world)
    {
        for (final Area area : new ArrayList<>(HELD.keySet()))
        {
            if (area.world().equals(world))
            {
                let(area);
            }
        }
        QUEUE.removeIf(area -> area.world().equals(world));
        LOADING.removeIf(area -> area.world().equals(world));
        CLAIMS.values().forEach(claim -> claim.areas = claim.areas.stream().filter(area -> !area.world().equals(world)).toList());
        wanted = new LinkedHashSet<>(wanted);
        wanted.removeIf(area -> area.world().equals(world));
    }

    /** Lets go of everything, at once: shutdown, a reset, or the setting turned off. */
    public static void releaseAll()
    {
        for (final Area area : new ArrayList<>(HELD.keySet()))
        {
            let(area);
        }
        CLAIMS.clear();
        QUEUE.clear();
        LOADING.clear();
        UNMADE.clear();
        wanted = Set.of();
        warnedCap = false;
        stopPacing();
    }

    /** Lets go of one chunk. */
    private static void let(final Area area)
    {
        final Chunk chunk = HELD.remove(area);
        if (chunk != null)
        {
            try
            {
                ChunkTickets.release(chunk);
            }
            catch (final RuntimeException gone)
            {
                // Its world has gone, and its ticket with it.
            }
        }
    }

    private static void pace()
    {
        if (task != -1)
        {
            return;
        }
        try
        {
            task = WormholeXTreme.getScheduler().scheduleSyncRepeatingTask(WormholeXTreme.getThisPlugin(),
                FarChunkHolds::step, 1L, 1L);
        }
        catch (final RuntimeException noScheduler)
        {
            // No scheduler yet, during startup or in tests: stepped by hand.
            task = -1;
        }
    }

    private static void stopPacing()
    {
        if (task == -1)
        {
            return;
        }
        final int running = task;
        task = -1;
        try
        {
            WormholeXTreme.getScheduler().cancelTask(running);
        }
        catch (final RuntimeException noScheduler)
        {
            // Gone with the plugin.
        }
    }

    /**
     * Loads a chunk without generating it: on Paper off the main thread, elsewhere on it, after
     * asking whether the chunk was ever generated, since a plain load generates one that was not.
     */
    private static void loadReal(final World world, final int x, final int z, final Consumer<Chunk> loaded)
    {
        if (ASYNC != null)
        {
            try
            {
                final CompletableFuture<?> future = (CompletableFuture<?>) ASYNC.invoke(world, x, z, Boolean.FALSE);
                future.thenAccept(chunk -> onMain(() -> loaded.accept((Chunk) chunk)));
                return;
            }
            catch (final ReflectiveOperationException | RuntimeException | LinkageError notAsync)
            {
                // Loaded the plain way instead.
            }
        }
        loaded.accept(world.isChunkGenerated(x, z) ? world.getChunkAt(x, z) : null);
    }

    /** Runs on the main thread, where Paper already completes the load. */
    private static void onMain(final Runnable work)
    {
        if (Bukkit.isPrimaryThread())
        {
            work.run();
            return;
        }
        try
        {
            WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), work);
        }
        catch (final RuntimeException noScheduler)
        {
            // Stopping: nothing is held.
        }
    }

    private static Method findAsync()
    {
        try
        {
            return World.class.getMethod("getChunkAtAsync", int.class, int.class, boolean.class);
        }
        catch (final NoSuchMethodException | RuntimeException | LinkageError absent)
        {
            return null;
        }
    }

    /**
     * Loads chunks through this instead of the server, for a test.
     *
     * @param stand
     *            the loader, or null for the server's own
     */
    static void loaderWith(final Loader stand)
    {
        loader = (stand == null) ? FarChunkHolds::loadReal : stand;
    }

    /** @return true on a server that loads chunks off the main thread, for a test */
    static boolean loadsAsync()
    {
        return ASYNC != null;
    }
}
