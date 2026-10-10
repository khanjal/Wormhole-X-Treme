package com.wormhole_xtreme.wormhole.model.window;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;
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
 * also holds keeps one ticket. A plugin ticket holds its chunk as {@code /forceload} does, which
 * also keeps a ring two chunks wide loaded round it, and that ring can generate terrain at the edge
 * of explored land. Let go a grace period after the last viewer stops, at once on a shutdown, a
 * reset or the far world unloading. Loaded nearest the arrival first: on Paper two a tick,
 * asynchronously, asking for the held chunk itself not to be generated; on Spigot one a tick on the
 * main thread, once the server says the chunk is on disk.
 */
public final class FarChunkHolds
{
    /** How long a window's area is kept after its last viewer, so one flicking in and out does not churn tickets. */
    static final long GRACE_MILLIS = 10_000L;

    /** The most chunks held across every window. */
    static final int MOST_HELD = 400;

    /** How many chunks are asked for a tick, where they load off the main thread. */
    static final int PER_TICK = 2;

    /** The same through Spigot's own loader, which loads, and may finish generating, on the main thread. */
    static final int PER_TICK_ON_MAIN = 1;

    /** How long a load may take before it is given up on. */
    static final long LOAD_TIMEOUT_MILLIS = 30_000L;

    /** How long a chunk that failed to load, or was never generated, is left before it is asked again. */
    static final long RETRY_MILLIS = 60_000L;

    /** One chunk of one world. */
    record Area(String world, int x, int z)
    {
    }

    /** Loads a chunk not to be generated, and hands it over (null for one not on disk), or says why it could not. */
    @FunctionalInterface
    interface Loader
    {
        void load(World world, int x, int z, Consumer<Chunk> loaded, Consumer<Throwable> failed);
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

    /** The chunks asked for and not yet handed over, with when they were asked. */
    private static final Map<Area, Long> LOADING = new HashMap<>();

    /** Chunks never generated or that failed to load, with when they may be asked again. */
    private static final Map<Area, Long> RESTING = new HashMap<>();

    /** The chunks wanted now, after the cap. */
    private static Set<Area> wanted = Set.of();

    /** Paper's {@code getChunkAtAsync(int, int, boolean)}, or null on a server without it. */
    private static Method async = findAsync();

    /** How chunks are loaded. */
    private static Loader loader = FarChunkHolds::loadReal;

    /** Whether that is the server's own loader, which on Spigot loads on the main thread. */
    private static boolean real = true;

    private static int task = -1;
    private static boolean warnedCap;
    private static boolean warnedLoad;

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
        giveUpOnSlowLoads(now);
        wanted = capped(watched);
        for (final Area area : new ArrayList<>(HELD.keySet()))
        {
            if (!wanted.contains(area))
            {
                let(area);
            }
        }
        // Rebuilt in the order wanted, so a newly watched window's arrival is not left behind older windows' edges.
        QUEUE.clear();
        LOADING.keySet().removeIf(area -> !wanted.contains(area));
        RESTING.keySet().removeIf(area -> !wanted.contains(area));
        for (final Area area : wanted)
        {
            if (askable(area, now))
            {
                QUEUE.add(area);
            }
        }
        if (QUEUE.isEmpty())
        {
            stopPacing();
        }
        else
        {
            pace();
        }
    }

    /** Whether a wanted chunk is to be asked for now: not held, not on its way, not resting, and in a loaded world. */
    private static boolean askable(final Area area, final long now)
    {
        final Long resting = RESTING.get(area);
        return !HELD.containsKey(area) && !LOADING.containsKey(area) && !QUEUE.contains(area)
            && ((resting == null) || (now >= resting)) && (Bukkit.getWorld(area.world()) != null);
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

    /** Gives up on loads that never answered, as on any failure. */
    private static void giveUpOnSlowLoads(final long now)
    {
        for (final Map.Entry<Area, Long> loading : new ArrayList<>(LOADING.entrySet()))
        {
            if ((now - loading.getValue()) >= LOAD_TIMEOUT_MILLIS)
            {
                failed(loading.getKey(), null, now);
            }
        }
    }

    /**
     * Every claimed area up to {@link #MOST_HELD}: the chunks already held first, a window's in its
     * grace too, so the cap does not swap which window is cut as viewers move, then the other watched
     * windows' nearest first, then those in their grace.
     */
    private static Set<Area> capped(final Map<String, List<Area>> watched)
    {
        final Set<Area> all = new LinkedHashSet<>();
        watched.values().forEach(areas -> areas.stream().filter(HELD::containsKey).forEach(all::add));
        CLAIMS.values().forEach(claim -> claim.areas.stream().filter(HELD::containsKey).forEach(all::add));
        watched.values().forEach(all::addAll);
        CLAIMS.values().forEach(claim -> all.addAll(claim.areas));
        if (all.size() <= MOST_HELD)
        {
            return all;
        }
        if (!warnedCap)
        {
            warnedCap = true;
            log("Views want " + all.size() + " chunks held for their creatures; holding " + MOST_HELD
                + ", the windows already held first (mirror-entity-load-radius)", null);
        }
        return all.stream().limit(MOST_HELD).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Asks for the next few chunks: the pacing task's step. */
    static void step()
    {
        final long now = Windows.clock.getAsLong();
        final int pace = ((async == null) && real) ? PER_TICK_ON_MAIN : PER_TICK;
        for (int i = 0; (i < pace) && !QUEUE.isEmpty(); i++)
        {
            final Area area = QUEUE.poll();
            final World world = Bukkit.getWorld(area.world());
            if (world != null)
            {
                LOADING.put(area, now);
                try
                {
                    loader.load(world, area.x(), area.z(), chunk -> loaded(area, chunk),
                        failure -> failed(area, failure, Windows.clock.getAsLong()));
                }
                catch (final Exception | LinkageError failure)
                {
                    failed(area, failure, now);
                }
            }
        }
        if (QUEUE.isEmpty())
        {
            stopPacing();
        }
    }

    /** Holds a chunk once it is in; rests one not on disk. */
    static void loaded(final Area area, final Chunk chunk)
    {
        if (LOADING.remove(area) == null)
        {
            // Given up on already, or let go meanwhile.
            return;
        }
        if (chunk == null)
        {
            RESTING.put(area, Windows.clock.getAsLong() + RETRY_MILLIS);
        }
        else
        {
            // Only a wanted chunk is still on its way: settle forgets the rest.
            HELD.computeIfAbsent(area, held ->
            {
                ChunkTickets.hold(chunk);
                return chunk;
            });
        }
    }

    /** A load that failed or never answered: rested a while, and said once. */
    static void failed(final Area area, final Throwable failure, final long now)
    {
        if (LOADING.remove(area) == null)
        {
            return;
        }
        RESTING.put(area, now + RETRY_MILLIS);
        if (!warnedLoad)
        {
            warnedLoad = true;
            log("Could not load chunk " + area.x() + "," + area.z() + " of " + area.world()
                + " for a view's creatures" + ((failure == null) ? ": no answer in " + (LOAD_TIMEOUT_MILLIS / 1000) + "s" : ""),
                failure);
        }
    }

    private static void log(final String message, final Throwable failure)
    {
        final WormholeXTreme plugin = WormholeXTreme.getThisPlugin();
        if (plugin == null)
        {
            return;
        }
        if (failure == null)
        {
            plugin.prettyLog(Level.WARNING, message);
        }
        else
        {
            plugin.prettyLog(Level.WARNING, message, failure);
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

    /** @return how many chunks are on their way, for a test */
    static int loadingCount()
    {
        return LOADING.size();
    }

    /** @return true while the pacing task runs, for a test */
    static boolean pacing()
    {
        return task != -1;
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
        LOADING.keySet().removeIf(area -> area.world().equals(world));
        RESTING.keySet().removeIf(area -> area.world().equals(world));
        CLAIMS.values().forEach(claim -> claim.areas = claim.areas.stream().filter(area -> !area.world().equals(world)).toList());
        wanted = new LinkedHashSet<>(wanted);
        wanted.removeIf(area -> area.world().equals(world));
        if (QUEUE.isEmpty())
        {
            stopPacing();
        }
    }

    /** Lets go of everything, at once: shutdown, or a reset. */
    public static void releaseAll()
    {
        for (final Area area : new ArrayList<>(HELD.keySet()))
        {
            let(area);
        }
        CLAIMS.clear();
        QUEUE.clear();
        LOADING.clear();
        RESTING.clear();
        wanted = Set.of();
        warnedCap = false;
        warnedLoad = false;
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
     * Loads a chunk: on Paper off the main thread, asking for it not to be generated; elsewhere on it,
     * and only once the server says the chunk is on disk, since a plain load generates one that is not.
     */
    static void loadReal(final World world, final int x, final int z, final Consumer<Chunk> loaded,
        final Consumer<Throwable> failed)
    {
        if (async != null)
        {
            final CompletableFuture<?> future;
            try
            {
                future = (CompletableFuture<?>) async.invoke(world, x, z, Boolean.FALSE);
            }
            catch (final ReflectiveOperationException | RuntimeException | LinkageError notAsync)
            {
                failed.accept(notAsync);
                return;
            }
            future.whenComplete((chunk, failure) -> onMain(() ->
            {
                if (failure == null)
                {
                    loaded.accept((Chunk) chunk);
                }
                else
                {
                    failed.accept(failure);
                }
            }));
            return;
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
        real = (stand == null);
    }

    /**
     * Uses this as Paper's asynchronous load, for a test: the API on the compile path has none.
     *
     * @param method
     *            a {@code getChunkAtAsync(int, int, boolean)} to call, or null for the server's own
     */
    static void asyncWith(final Method method)
    {
        async = (method == null) ? findAsync() : method;
    }

    /** Loads as a server without an asynchronous load does, for a test on whichever API it compiles against. */
    static void asyncNone()
    {
        async = null;
    }

    /** @return true on a server that loads chunks off the main thread, for a test */
    static boolean loadsAsync()
    {
        return async != null;
    }
}
