package com.wormhole_xtreme.wormhole.plugin.map;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.beam.BeamManager;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorManager;
import com.wormhole_xtreme.wormhole.model.ring.RingManager;

/**
 * Keeps a web map showing the server's gates, rings, public beam destinations and quantum mirrors (#236).
 *
 * <p>The main thread only looks: every few seconds, and on the tick after a gate is built,
 * removed, opened or shut, it builds a {@link MapSnapshot} from what is already in memory and
 * compares it with the last one. Only a changed picture is handed on, to one background task
 * at a time, which always draws the newest picture there is -- so a slow draw never lands an
 * older picture over a newer one, and changes that arrive while it runs are drawn together.
 *
 * <p>Names no Dynmap type, so it loads on a server without Dynmap; {@link DynmapMapProvider}
 * is named only once Dynmap has been found.
 */
public final class MapMarkers
{
    /** How often the map is brought up to date without an event asking, in ticks. */
    static final long PERIOD_TICKS = 100L;

    /** A class only Dynmap provides. */
    private static final String DYNMAP_CLASS = "org.dynmap.DynmapCommonAPIListener";

    /** Guards drawing, so only one picture is drawn at a time. */
    private static final Object DRAWING = new Object();

    /** The newest picture, waiting to be drawn. */
    private static final AtomicReference<MapSnapshot> latest = new AtomicReference<>();

    /** Whether a draw is already queued, so a burst of changes queues one. */
    private static final AtomicBoolean drawQueued = new AtomicBoolean();

    /** Whether a look on the next tick is already queued. */
    private static final AtomicBoolean lookQueued = new AtomicBoolean();

    /** Whether the map is being kept up to date. */
    private static volatile boolean running = false;

    /** The map being drawn on, while running. */
    // An interface reference swapped whole: volatile is all the synchronisation it needs.
    @SuppressWarnings("java:S3077")
    private static volatile MapProvider provider = null;

    /** Undoes whatever the provider registered with its map plugin. Main thread only. */
    private static Runnable detach = null;

    /** The periodic look. Main thread only. */
    private static BukkitTask ticker = null;

    /** The gate event listener. Main thread only. */
    private static Listener listener = null;

    /** The layers asked for, read once at enable like the rest of the setting. */
    private static MapLayers layers = MapLayers.ALL;

    /** The plugin, for scheduling. */
    private static Plugin owner = null;

    /** The last picture the main thread built. Main thread only. */
    private static MapSnapshot lastSeen = null;

    /** Whether a failed draw has been reported, so a map that keeps failing does not flood the log. */
    private static volatile boolean warned = false;

    /** Test seam: a provider to use instead of looking for Dynmap. */
    private static MapProvider providerForTest = null;

    /** Test seam: the class looked for to decide Dynmap is present. */
    private static String dynmapClass = DYNMAP_CLASS;

    /** Test seam: runs the background draw; null schedules it on Bukkit's async pool. */
    private static Consumer<Runnable> backgroundForTest = null;

    /** Test seam: builds the picture; null reads the real managers. */
    private static Supplier<MapSnapshot> sourceForTest = null;

    /** Static helpers only. */
    private MapMarkers()
    {
    }

    /**
     * Starts keeping the map up to date, if the config asks for it and a map plugin is there.
     *
     * <p>Called from {@code WormholeXTreme.onEnable} once gates, rings, beams and mirrors have loaded.
     *
     * @param plugin
     *            this plugin
     */
    public static void enable(final Plugin plugin)
    {
        if (running || !ConfigManager.isDynmapEnabled())
        {
            return;
        }
        layers = MapLayers.fromConfig();
        MapProvider chosen = providerForTest;
        Runnable undo = () ->
        {
        };
        if (chosen == null)
        {
            try
            {
                Class.forName(dynmapClass);
            }
            catch (final ClassNotFoundException e)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING,
                    "dynmap-enabled is set but Dynmap was not found. Nothing is shown on a map.");
                return;
            }
            // Only reached with Dynmap on the classpath, which is what makes naming its provider safe.
            final DynmapMapProvider dynmap = new DynmapMapProvider(layers, MapMarkers::requestDraw);
            dynmap.register();
            chosen = dynmap;
            undo = dynmap::unregister;
        }
        owner = plugin;
        provider = chosen;
        detach = undo;
        running = true;
        warned = false;
        listener = new MapRefreshListener();
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        ticker = WormholeXTreme.getScheduler().runTaskTimer(plugin, MapMarkers::tick, 20L, PERIOD_TICKS);
        WormholeXTreme.getThisPlugin().prettyLog(Level.INFO,
            "Showing gates, rings, beam destinations and mirrors on " + chosen.name() + ".");
    }

    /**
     * Stops keeping the map up to date and takes this plugin's marks off it.
     *
     * <p>Called from {@code WormholeXTreme.onDisable}. Clears on the main thread, because no
     * task can be scheduled while the plugin disables; it is a handful of deletions.
     */
    public static void disable()
    {
        running = false;
        if (ticker != null)
        {
            ticker.cancel();
            ticker = null;
        }
        if (listener != null)
        {
            HandlerList.unregisterAll(listener);
            listener = null;
        }
        final MapProvider was = provider;
        final Runnable undo = detach;
        provider = null;
        detach = null;
        synchronized (DRAWING)
        {
            try
            {
                if (was != null)
                {
                    was.clear();
                }
            }
            finally
            {
                if (undo != null)
                {
                    undo.run();
                }
            }
        }
        latest.set(null);
        lastSeen = null;
        drawQueued.set(false);
        lookQueued.set(false);
        owner = null;
    }

    /**
     * Asks for a look on the next tick, rather than waiting for the periodic one.
     *
     * <p>Called on the main thread by {@link MapRefreshListener}.
     */
    static void requestRefresh()
    {
        final Plugin plugin = owner;
        if (running && (plugin != null) && lookQueued.compareAndSet(false, true))
        {
            WormholeXTreme.getScheduler().runTask(plugin, MapMarkers::tick);
        }
    }

    /**
     * Has the newest picture drawn again, for a map that has just come up and shows nothing.
     *
     * <p>Called by a provider, from whatever thread its map plugin tells it on.
     */
    static void requestDraw()
    {
        if (running && (latest.get() != null))
        {
            queueDraw();
        }
    }

    /** Looks at the plugin's state, and hands on the picture if it changed. Main thread only. */
    static void tick()
    {
        lookQueued.set(false);
        if (!running)
        {
            return;
        }
        final Supplier<MapSnapshot> source = sourceForTest;
        final MapSnapshot now = (source != null) ? source.get() : scan();
        if (now.equals(lastSeen))
        {
            return;
        }
        lastSeen = now;
        latest.set(now);
        queueDraw();
    }

    /**
     * The picture of the server as it stands.
     *
     * @return what the map should show
     */
    static MapSnapshot scan()
    {
        return MapScanner.scan(StargateManager.getAllGatesUnsorted(), RingManager.getAllPairs(),
            BeamManager.getAllPublicDestinations(), MirrorManager.all(), layers);
    }

    /** Queues one background draw, unless one is already waiting. */
    private static void queueDraw()
    {
        if (!drawQueued.compareAndSet(false, true))
        {
            return;
        }
        final Consumer<Runnable> background = backgroundForTest;
        if (background != null)
        {
            background.accept(MapMarkers::drawLatest);
            return;
        }
        final Plugin plugin = owner;
        if (plugin == null)
        {
            drawQueued.set(false);
            return;
        }
        WormholeXTreme.getScheduler().runTaskAsynchronously(plugin, MapMarkers::drawLatest);
    }

    /** Draws the newest picture. Runs off the main thread. */
    static void drawLatest()
    {
        // Cleared before reading, so a picture set after this line queues a draw of its own.
        drawQueued.set(false);
        synchronized (DRAWING)
        {
            final MapProvider map = provider;
            final MapSnapshot picture = latest.get();
            if (!running || (map == null) || (picture == null))
            {
                return;
            }
            try
            {
                map.apply(picture);
            }
            catch (final Exception | LinkageError e)
            {
                // LinkageError as well as Exception: a map plugin updated under a running
                // server can fail to link here, and a map that cannot draw is not worth a gate.
                if (!warned)
                {
                    warned = true;
                    WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING,
                        "Failed to update the " + map.name() + " markers", e);
                }
            }
        }
    }

    /**
     * Whether the map is being kept up to date.
     *
     * @return true between a successful enable and disable
     */
    public static boolean isRunning()
    {
        return running;
    }

    /**
     * Uses this provider instead of looking for Dynmap, for tests.
     *
     * @param replacement
     *            the provider, or null to look for Dynmap for real
     */
    static void setProviderForTest(final MapProvider replacement)
    {
        providerForTest = replacement;
    }

    /**
     * Looks for this class instead of Dynmap's, so a test can play a server without it.
     *
     * @param className
     *            the class, or null for Dynmap's own
     */
    static void setDynmapClassForTest(final String className)
    {
        dynmapClass = (className != null) ? className : DYNMAP_CLASS;
    }

    /**
     * Runs background draws through this instead of Bukkit's async pool, for tests.
     *
     * @param runner
     *            takes each draw, or null to schedule for real
     */
    static void setBackgroundForTest(final Consumer<Runnable> runner)
    {
        backgroundForTest = runner;
    }

    /**
     * Builds pictures with this instead of reading the managers, for tests.
     *
     * @param source
     *            the picture to see on each look, or null to read the managers
     */
    static void setSourceForTest(final Supplier<MapSnapshot> source)
    {
        sourceForTest = source;
    }
}
