package com.wormhole_xtreme.wormhole.model.mirror;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.World;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * Loading a window's far side without stopping the server to do it.
 *
 * <p>Reading a block in a chunk that is not loaded loads it, there and then, on the main thread
 * -- and a chunk nobody has visited may have to be generated first. For a window, that stall
 * would come the moment somebody walked up to it.
 *
 * <p>So a window never reads an unloaded chunk; it asks here instead, and leaves that part of the
 * view out until the chunk arrives. Paper has {@code World.getChunkAtAsync} and loads it in the
 * background. Spigot has no such method, so there the requests queue and a couple are loaded per
 * sweep: still on the main thread, but never more than that at once.
 */
final class MirrorChunkLoads
{
    /** Chunks loaded per sweep where nothing can load them in the background. */
    private static final int LOADS_PER_SWEEP = 2;

    /** Paper's {@code World.getChunkAtAsync(int, int)}, or null on a server without it. */
    private static final Method IN_BACKGROUND = findInBackground();

    /** Chunks asked for and not yet loaded, by world and position. */
    private static final Map<String, Request> PENDING = new ConcurrentHashMap<>();

    /**
     * One chunk asked for.
     *
     * @param world
     *            its world
     * @param x
     *            chunk x
     * @param z
     *            chunk z
     * @param loaded
     *            run on the main thread once it is loaded
     * @param inBackground
     *            true if it is already loading in the background
     */
    private record Request(World world, int x, int z, Runnable loaded, boolean inBackground)
    {
    }

    /** Static helpers only. */
    private MirrorChunkLoads()
    {
    }

    /**
     * Asks for a chunk to be loaded, once, however many times it is asked.
     *
     * @param world
     *            the world
     * @param x
     *            chunk x
     * @param z
     *            chunk z
     * @param loaded
     *            run on the main thread once it is loaded
     */
    static void request(final World world, final int x, final int z, final Runnable loaded)
    {
        final String key = world.getName() + ':' + x + ':' + z;
        if (PENDING.containsKey(key))
        {
            return;
        }
        if (!inBackground(world, x, z, key, loaded))
        {
            PENDING.put(key, new Request(world, x, z, loaded, false));
        }
    }

    /** Loads the next few queued chunks, where nothing loads them in the background. */
    static void drain()
    {
        int left = LOADS_PER_SWEEP;
        for (final Map.Entry<String, Request> entry : new ArrayList<>(PENDING.entrySet()))
        {
            final Request request = entry.getValue();
            if ((left > 0) && !request.inBackground())
            {
                left--;
                PENDING.remove(entry.getKey());
                if (request.world().isChunkLoaded(request.x(), request.z())
                    || request.world().loadChunk(request.x(), request.z(), true))
                {
                    request.loaded().run();
                }
            }
        }
    }

    /** Forgets every request, for a test or a reload. */
    static void clear()
    {
        PENDING.clear();
    }

    /** Starts a background load, if this server can do one. @return true if it started */
    private static boolean inBackground(final World world, final int x, final int z,
        final String key, final Runnable loaded)
    {
        if (IN_BACKGROUND == null)
        {
            return false;
        }
        try
        {
            if (!(IN_BACKGROUND.invoke(world, x, z) instanceof CompletableFuture<?> loading))
            {
                return false;
            }
            PENDING.put(key, new Request(world, x, z, loaded, true));
            loading.whenComplete((chunk, failed) ->
            {
                PENDING.remove(key);
                if ((failed == null) && (chunk != null))
                {
                    onMainThread(loaded);
                }
            });
            return true;
        }
        catch (final ReflectiveOperationException | RuntimeException | LinkageError notThisServer)
        {
            return false;
        }
    }

    /** Runs something on the main thread, from whichever thread finished the load. */
    private static void onMainThread(final Runnable task)
    {
        try
        {
            WormholeXTreme.getScheduler().runTask(WormholeXTreme.getThisPlugin(), task);
        }
        catch (final RuntimeException stopping)
        {
            // The plugin is going away. The next view that wants this chunk asks again.
        }
    }

    /** @return Paper's background chunk load, or null on a server that has none */
    private static Method findInBackground()
    {
        try
        {
            return World.class.getMethod("getChunkAtAsync", int.class, int.class);
        }
        catch (final NoSuchMethodException | SecurityException | LinkageError spigot)
        {
            return null;
        }
    }
}
