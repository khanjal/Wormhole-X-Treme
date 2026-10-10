package com.wormhole_xtreme.wormhole.plugin.map;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.beam.BeamManager;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorManager;
import com.wormhole_xtreme.wormhole.model.ring.RingManager;

/**
 * Keeps the web maps showing the server's gates, rings, public beam destinations and quantum mirrors (#236).
 *
 * <p>The main thread only looks: every few seconds, and on the tick after a gate is built,
 * removed, opened or shut, it builds a {@link MapSnapshot} from what is already in memory and
 * compares it with the last one. Only a changed picture is handed on, to one background task
 * at a time, which always draws the newest picture there is on every map that is up -- so a
 * slow draw never lands an older picture over a newer one, and changes that arrive while it
 * runs are drawn together.
 *
 * <p>Names no map plugin's types, so it loads on a server without any of them. Each provider is
 * named only in a lambda that runs once that map plugin's own class has been found.
 */
public final class MapMarkers
{
    /** How often the map is brought up to date without an event asking, in ticks. */
    static final long PERIOD_TICKS = 100L;

    /** How often a dialling gate is checked for its wormhole having formed, in ticks. */
    static final long FORMING_CHECK_TICKS = 20L;

    /** How many times a dialling gate is checked before the periodic look is left to it. */
    static final int FORMING_CHECKS = 20;

    /**
     * A map plugin this one can draw on.
     *
     * @param name
     *            what the log calls it
     * @param setting
     *            the setting that turns it on, for the log
     * @param pluginName
     *            its name in its plugin.yml
     * @param className
     *            a class only it provides, looked for before its provider is named
     * @param wanted
     *            reads the setting
     * @param make
     *            builds its provider from the layers and the ready callback; run only once the
     *            class has been found
     */
    private record Backend(String name, String setting, String pluginName, String className,
        BooleanSupplier wanted, BiFunction<MapLayers, Consumer<MapProvider>, MapProvider> make)
    {
    }

    /**
     * Every map plugin this one can draw on. Lambdas, not constructor references: a reference is
     * linked when this class loads, which would load the provider and the map plugin with it.
     */
    // S1612 wants constructor references, which would link each provider when this class loads.
    @SuppressWarnings("java:S1612")
    private static final List<Backend> BACKENDS = List.of(
        new Backend("Dynmap", "dynmap-enabled", "dynmap", "org.dynmap.DynmapCommonAPIListener",
            ConfigManager::isDynmapEnabled, (chosen, ready) -> new DynmapMapProvider(chosen, ready)),
        new Backend("BlueMap", "bluemap-enabled", "BlueMap", "de.bluecolored.bluemap.api.BlueMapAPI",
            ConfigManager::isBlueMapEnabled, (chosen, ready) -> new BlueMapMapProvider(chosen, ready)),
        new Backend("squaremap", "squaremap-enabled", "squaremap", "xyz.jpenilla.squaremap.api.SquaremapProvider",
            ConfigManager::isSquaremapEnabled, (chosen, ready) -> new SquaremapMapProvider(chosen, ready)),
        new Backend("Pl3xMap", "pl3xmap-enabled", "Pl3xMap", "net.pl3x.map.core.Pl3xMap",
            ConfigManager::isPl3xMapEnabled, (chosen, ready) -> new Pl3xMapMapProvider(chosen, ready)));

    /** Guards drawing, so only one picture is drawn at a time. */
    private static final Object DRAWING = new Object();

    /** The newest picture, waiting to be drawn. */
    private static final AtomicReference<MapSnapshot> latest = new AtomicReference<>();

    /** Whether a draw is already queued, so a burst of changes queues one. */
    private static final AtomicBoolean drawQueued = new AtomicBoolean();

    /** Whether a look on the next tick is already queued. */
    private static final AtomicBoolean lookQueued = new AtomicBoolean();

    /** Whether the maps are being kept up to date. */
    private static volatile boolean running = false;

    /** The maps being drawn on, while running. */
    // An immutable list swapped whole: volatile is all the synchronisation it needs.
    @SuppressWarnings("java:S3077")
    private static volatile List<MapProvider> providers = List.of();

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

    /** The maps whose failed draw has been reported, so a map that keeps failing does not flood the log. */
    private static final Set<MapProvider> warned = ConcurrentHashMap.newKeySet();

    /** Whether a failed look has been reported. Main thread only. */
    private static boolean scanWarned = false;

    /** Set when a draw failed or a map came up, so the next look draws again even though nothing changed. */
    private static final AtomicBoolean redraw = new AtomicBoolean();

    /** Test seam: providers to use instead of looking for map plugins. */
    private static List<MapProvider> providersForTest = null;

    /** Test seam: the class looked for to decide a map plugin is present, by its name. */
    private static final Map<String, String> classesForTest = new HashMap<>();

    /** Test seam: runs the background draw; null schedules it on Bukkit's async pool. */
    private static Consumer<Runnable> backgroundForTest = null;

    /** Test seam: builds the picture; null reads the real managers. */
    private static Supplier<MapSnapshot> sourceForTest = null;

    /** Static helpers only. */
    private MapMarkers()
    {
    }

    /**
     * Starts keeping the maps up to date, if the config asks for one and its plugin is there.
     *
     * <p>Called from {@code WormholeXTreme.onEnable} once gates, rings, beams and mirrors have loaded.
     *
     * @param plugin
     *            this plugin
     */
    public static void enable(final Plugin plugin)
    {
        if (running || !anyWanted())
        {
            return;
        }
        layers = MapLayers.fromConfig();
        final List<MapProvider> found = (providersForTest != null) ? providersForTest : findAll(plugin);
        if (found.isEmpty())
        {
            return;
        }
        owner = plugin;
        providers = List.copyOf(found);
        running = true;
        warned.clear();
        scanWarned = false;
        redraw.set(false);
        try
        {
            listener = new MapRefreshListener();
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
            ticker = WormholeXTreme.getScheduler().runTaskTimer(plugin, MapMarkers::tick, 20L, PERIOD_TICKS);
        }
        catch (final RuntimeException | LinkageError e)
        {
            disable();
            throw e;
        }
        // Last, each undone on failure: a map plugin's listener list can be static, and a hook
        // left in it would outlive this plugin's classloader.
        for (final MapProvider map : found)
        {
            register(map);
        }
        if (providers.isEmpty())
        {
            disable();
        }
    }

    /**
     * Looks for every map plugin that is switched on.
     *
     * @param plugin
     *            this plugin, for its server
     * @return a provider for each one found
     */
    private static List<MapProvider> findAll(final Plugin plugin)
    {
        final List<MapProvider> found = new ArrayList<>();
        for (final Backend backend : BACKENDS)
        {
            final MapProvider map = backend.wanted().getAsBoolean() ? find(plugin, backend) : null;
            if (map != null)
            {
                found.add(map);
            }
        }
        return found;
    }

    /**
     * Looks for one map plugin and, if it is there, makes its provider.
     *
     * @param plugin
     *            this plugin, for its server
     * @param backend
     *            the map plugin
     * @return the provider, or null if the map plugin is missing or its provider could not be made
     */
    private static MapProvider find(final Plugin plugin, final Backend backend)
    {
        try
        {
            Class.forName(classesForTest.getOrDefault(backend.name(), backend.className()));
        }
        catch (final ClassNotFoundException | LinkageError e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING,
                backend.setting() + " is set but " + backend.name() + " was not found. Nothing is shown on a map.");
            return null;
        }
        warnIfInstalledButNotRunning(plugin, backend);
        try
        {
            // Only reached with the map plugin on the classpath, which is what makes naming its provider safe.
            return backend.make().apply(layers, MapMarkers::providerReady);
        }
        catch (final RuntimeException | LinkageError e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING,
                "Failed to start the " + backend.name() + " markers; nothing is shown on it", e);
            return null;
        }
    }

    /**
     * Starts one map listening to its plugin. One that fails is dropped, so the others still show.
     *
     * @param map
     *            the map
     */
    private static void register(final MapProvider map)
    {
        // Before the hook: registering calls back at once when the map is already up.
        WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "Waiting for " + map.name() + " to be ready.");
        try
        {
            map.register();
        }
        catch (final RuntimeException | LinkageError e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING,
                "Failed to start the " + map.name() + " markers; nothing is shown on it", e);
            final List<MapProvider> rest = new ArrayList<>(providers);
            rest.remove(map);
            providers = List.copyOf(rest);
            unregister(map);
        }
    }

    /**
     * Says so once if a map plugin is installed but did not start, which it reports in its own log.
     *
     * <p>Map plugins are soft dependencies, so one has enabled, or failed to, before this plugin
     * enables. The hook is still registered: if the map plugin does start later, the map starts then.
     *
     * @param plugin
     *            this plugin, for its server
     * @param backend
     *            the map plugin
     */
    private static void warnIfInstalledButNotRunning(final Plugin plugin, final Backend backend)
    {
        final Plugin installed = plugin.getServer().getPluginManager().getPlugin(backend.pluginName());
        if ((installed != null) && !installed.isEnabled())
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING,
                backend.setting() + " is set, but " + backend.name() + " is installed and not running (see its"
                    + " own startup errors). Nothing is shown until it starts.");
        }
    }

    /**
     * Whether any map is switched on.
     *
     * @return true if a map's setting is set
     */
    private static boolean anyWanted()
    {
        for (final Backend backend : BACKENDS)
        {
            if (backend.wanted().getAsBoolean())
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Applies a changed map setting now: takes the maps down and, if one is still wanted, puts
     * them back up with the layers the config now asks for, looking for the map plugins again on
     * the way.
     *
     * <p>Called from {@code /wormhole config}, on the main thread.
     */
    public static void followConfig()
    {
        if (running)
        {
            disable();
        }
        if (anyWanted())
        {
            enable(WormholeXTreme.getThisPlugin());
        }
    }

    /**
     * Stops keeping the maps up to date and takes this plugin's marks off them.
     *
     * <p>Called from {@code WormholeXTreme.onDisable}, and by {@link #followConfig()}. Clears on the main thread, because no
     * task can be scheduled while the plugin disables; it is a handful of deletions, after
     * waiting for at most the one draw that may be in flight.
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
        final List<MapProvider> was = providers;
        providers = List.of();
        synchronized (DRAWING)
        {
            for (final MapProvider map : was)
            {
                try
                {
                    map.clear();
                }
                catch (final Exception | LinkageError e)
                {
                    WormholeXTreme.getThisPlugin().prettyLog(Level.FINE,
                        "Failed to take the markers off " + map.name(), e);
                }
                finally
                {
                    unregister(map);
                }
            }
        }
        latest.set(null);
        lastSeen = null;
        redraw.set(false);
        drawQueued.set(false);
        lookQueued.set(false);
        owner = null;
    }

    /**
     * Stops one map listening to its plugin, whatever happens.
     *
     * @param map
     *            the map
     */
    private static void unregister(final MapProvider map)
    {
        try
        {
            map.unregister();
        }
        catch (final Exception | LinkageError e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "Failed to stop listening to " + map.name(), e);
        }
    }

    /**
     * Asks for a look on the next tick, rather than waiting for the periodic one.
     *
     * <p>Called on the main thread by {@link MapRefreshListener}, and from any thread when a map comes up.
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
     * Brings the map up to date once a dialling gate's wormhole has formed.
     *
     * <p>Nothing fires when the kawoosh settles, and how long the chevrons take depends on the
     * gate's shape and ring, so the gate is checked every second until it has formed or shut.
     * Each check reads two flags; the look it asks for is the ordinary coalesced one.
     *
     * @param gate
     *            the gate that has just been dialled
     */
    static void watchForming(final Stargate gate)
    {
        scheduleFormingCheck(gate, FORMING_CHECKS);
    }

    /**
     * Whether there is a map up to draw on.
     *
     * @return true while running and at least one provider is ready
     */
    private static boolean isReady()
    {
        if (!running)
        {
            return false;
        }
        for (final MapProvider map : providers)
        {
            if (map.ready())
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Books the next check on a dialling gate.
     *
     * @param gate
     *            the gate
     * @param left
     *            how many more checks it may have
     */
    private static void scheduleFormingCheck(final Stargate gate, final int left)
    {
        final Plugin plugin = owner;
        if (isReady() && (plugin != null) && (left > 0))
        {
            WormholeXTreme.getScheduler().runTaskLater(plugin, () -> checkForming(gate, left - 1),
                FORMING_CHECK_TICKS);
        }
    }

    /**
     * Asks for a look if the gate's wormhole has formed, or checks again later if it is still
     * dialling.
     *
     * @param gate
     *            the gate
     * @param left
     *            how many more checks it may have
     */
    static void checkForming(final Stargate gate, final int left)
    {
        if (gate.isGatePortalOpen())
        {
            requestRefresh();
        }
        else if (gate.isGateActive())
        {
            scheduleFormingCheck(gate, left);
        }
    }

    /**
     * A map has come up: says so, and has the whole picture drawn on the next tick, changed or
     * not, since a map that has just come up shows nothing of ours.
     *
     * <p>Called by a provider, from whatever thread its map plugin tells it on.
     *
     * @param map
     *            the map that came up
     */
    static void providerReady(final MapProvider map)
    {
        if (!running || !providers.contains(map))
        {
            return;
        }
        WormholeXTreme.getThisPlugin().prettyLog(Level.INFO,
            "Showing gates, rings, beam destinations and mirrors on " + map.name() + ".");
        redraw.set(true);
        requestRefresh();
    }

    /** Looks at the plugin's state, and hands on the picture if it changed. Main thread only. */
    static void tick()
    {
        lookQueued.set(false);
        // Nothing to draw on, so nothing to look at: a map coming up asks for a full look.
        if (!isReady())
        {
            return;
        }
        final MapSnapshot now;
        try
        {
            final Supplier<MapSnapshot> source = sourceForTest;
            now = (source != null) ? source.get() : scan();
        }
        catch (final Exception | LinkageError e)
        {
            // Once, not every five seconds: a look that fails will usually fail the same way next time.
            if (!scanWarned)
            {
                scanWarned = true;
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING,
                    "Failed to work out what the web map should show", e);
            }
            return;
        }
        scanWarned = false;
        final boolean retry = redraw.getAndSet(false) || anyLost();
        if (!retry && now.equals(lastSeen))
        {
            return;
        }
        lastSeen = now;
        latest.set(now);
        queueDraw();
    }

    /**
     * Whether a map that is up has dropped something of ours without saying so.
     *
     * @return true if one has, or cannot tell
     */
    private static boolean anyLost()
    {
        boolean lost = false;
        for (final MapProvider map : providers)
        {
            try
            {
                lost |= map.ready() && map.lost();
            }
            catch (final Exception | LinkageError e)
            {
                // Drawn again, so a failure to look is reported by the draw rather than every five seconds here.
                lost = true;
            }
        }
        return lost;
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

    /** Draws the newest picture on every map that is up. Runs off the main thread. */
    static void drawLatest()
    {
        // Cleared before reading, so a picture set after this line queues a draw of its own.
        drawQueued.set(false);
        synchronized (DRAWING)
        {
            final MapSnapshot picture = latest.get();
            if (picture == null)
            {
                return;
            }
            for (final MapProvider map : providers)
            {
                if (map.ready())
                {
                    draw(map, picture);
                }
            }
        }
    }

    /**
     * Draws a picture on one map, so a map that fails does not stop the others being drawn.
     *
     * @param map
     *            the map
     * @param picture
     *            what to show
     */
    private static void draw(final MapProvider map, final MapSnapshot picture)
    {
        try
        {
            map.apply(picture);
            warned.remove(map);
        }
        catch (final Exception | LinkageError e)
        {
            // LinkageError as well as Exception: a map plugin updated under a running
            // server can fail to link here, and a map that cannot draw is not worth a gate.
            // The next look draws again, since the picture it sees will not have changed.
            redraw.set(true);
            if (warned.add(map))
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING,
                    "Failed to update the " + map.name() + " markers", e);
            }
        }
    }

    /**
     * Whether the maps are being kept up to date.
     *
     * @return true between a successful enable and disable
     */
    public static boolean isRunning()
    {
        return running;
    }

    /**
     * Uses this provider instead of looking for map plugins, for tests.
     *
     * @param replacement
     *            the provider, or null to look for map plugins for real
     */
    static void setProviderForTest(final MapProvider replacement)
    {
        setProvidersForTest((replacement != null) ? List.of(replacement) : null);
    }

    /**
     * The maps being drawn on, for tests.
     *
     * @return the providers, empty while not running
     */
    static List<MapProvider> providers()
    {
        return providers;
    }

    /**
     * Uses these providers instead of looking for map plugins, for tests.
     *
     * @param replacements
     *            the providers, or null to look for map plugins for real
     */
    static void setProvidersForTest(final List<MapProvider> replacements)
    {
        providersForTest = (replacements != null) ? List.copyOf(replacements) : null;
    }

    /**
     * Looks for this class instead of a map plugin's own, so a test can play a server without it.
     *
     * @param name
     *            the map plugin, as the log calls it
     * @param className
     *            the class, or null for the map plugin's own
     */
    static void setPluginClassForTest(final String name, final String className)
    {
        if (className != null)
        {
            classesForTest.put(name, className);
        }
        else
        {
            classesForTest.remove(name);
        }
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
