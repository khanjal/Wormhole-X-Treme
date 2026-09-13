package com.wormhole_xtreme.wormhole;
import java.io.Console;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import com.wormhole_xtreme.wormhole.command.Dial;
import com.wormhole_xtreme.wormhole.command.Wormhole;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.StargateShapeRegistry;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateDBManager;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.plugin.PermissionsSupport;
import com.wormhole_xtreme.wormhole.plugin.EconomySupport;

/**
 * WormholeXtreme for Bukkit.
 * 
 * @author Ben Echols (Lologarithm)
 * @author Dean Bailey (alron)
 */
public class WormholeXTreme extends JavaPlugin
{

    /** The player listener. */
    private static final WormholeXTremePlayerListener playerListener = new WormholeXTremePlayerListener();
    /** The block listener. */
    private static final WormholeXTremeBlockListener blockListener = new WormholeXTremeBlockListener();
    /** The vehicle listener. */
    private static final WormholeXTremeVehicleListener vehicleListener = new WormholeXTremeVehicleListener();
    /** The entity listener. */
    private static final WormholeXTremeEntityListener entityListener = new WormholeXTremeEntityListener();
    /** The server listener. */
    private static final WormholeXTremeServerListener serverListener = new WormholeXTremeServerListener();
    /** The server listener. */
    private static final WormholeXTremeRedstoneListener redstoneListener = new WormholeXTremeRedstoneListener();

    private static final com.wormhole_xtreme.wormhole.model.beam.BeamFreezeListener beamFreezeListener =
        new com.wormhole_xtreme.wormhole.model.beam.BeamFreezeListener();

    /** Follows projectiles in flight so they cross a gate at the moment they reach it. */
    private static final ProjectileGateTracker projectileTracker = new ProjectileGateTracker();

    /** The Scheduler. */
    private static BukkitScheduler scheduler = null;

    /** The This plugin. */
    private static WormholeXTreme thisPlugin = null;

    /** Opens the one-line count each subsystem logs once it has read its files. */
    private static final String LOADED = "Loaded ";

    /** The log. */
    private static Logger log = null;

    /**
     * Gets the logger.
     * 
     * @return the log
     */
    private static Logger getLog()
    {
        return log;
    }

    /**
     * Gets the scheduler.
     * 
     * @return the scheduler
     */
    public static BukkitScheduler getScheduler()
    {
        return scheduler;
    }

    /**
     * Gets the this plugin.
     * 
     * @return the this plugin
     */
    public static WormholeXTreme getThisPlugin()
    {
        return thisPlugin;
    }

    /**
     * Register commands.
     */
    public static void registerCommands()
    {
        final WormholeXTreme tp = getThisPlugin();
        // Consolidated: register only canonical commands. legacy wx* names are aliases under `wormhole` in plugin.yml
        tp.getCommand("dial").setExecutor(new Dial());
        tp.getCommand("dial").setTabCompleter(new com.wormhole_xtreme.wormhole.command.DialTabCompleter());
        tp.getCommand("wormhole").setExecutor(new Wormhole());
        tp.getCommand("wormhole").setTabCompleter(new com.wormhole_xtreme.wormhole.command.WormholeTabCompleter());
    }

    /**
     * Register events.
     */
    public static void registerEvents(final boolean critical)
    {
        final WormholeXTreme tp = getThisPlugin();
        final PluginManager pm = tp.getServer().getPluginManager();
        if (critical)
        {
            pm.registerEvents(serverListener, tp);
        }
        else
        {
            pm.registerEvents(blockListener, tp);
            pm.registerEvents(playerListener, tp);
            pm.registerEvents(redstoneListener, tp);
            pm.registerEvents(vehicleListener, tp);
            pm.registerEvents(entityListener, tp);
            pm.registerEvents(projectileTracker, tp);
            pm.registerEvents(beamFreezeListener, tp);
            registerDismountListener(pm, tp);
        }
    }

    /**
     * Registers whichever dismount listener this server can actually load.
     *
     * <p>Spigot moved {@code EntityDismountEvent} from {@code org.spigotmc.event.entity} to
     * {@code org.bukkit.event.entity} in 1.20.4, and dropped the old package in 1.20.6. No
     * single import covers the versions this plugin supports, so there is a listener for
     * each and only one of them will resolve on any given server.
     *
     * <p>The failure being caught is {@link NoClassDefFoundError}, raised when the listener
     * class is loaded and its event type is not there. That is an Error rather than an
     * Exception, and this is the one place where catching one is right: it is the documented
     * way to ask a server which API it has, and the answer decides nothing else.
     *
     * @param pm
     *            the plugin manager to register with
     * @param plugin
     *            this plugin
     */
    private static void registerDismountListener(final org.bukkit.plugin.PluginManager pm,
                                                 final WormholeXTreme plugin)
    {
        for (final String candidate : new String[] {
            "com.wormhole_xtreme.wormhole.GateDismountListener",
            "com.wormhole_xtreme.wormhole.LegacyGateDismountListener" })
        {
            try
            {
                final Class<?> type = Class.forName(candidate);
                pm.registerEvents((org.bukkit.event.Listener) type.getDeclaredConstructor().newInstance(), plugin);
                plugin.prettyLog(Level.FINE, "Dismount handling registered via " + candidate);
                return;
            }
            catch (final NoClassDefFoundError notOnThisServer)
            {
                // Not on this server's Bukkit; the loop moves to the next candidate by itself.
            }
            catch (final ReflectiveOperationException | RuntimeException e)
            {
                plugin.prettyLog(Level.FINE,
                    "Could not register " + candidate, e);
            }
        }
        plugin.prettyLog(Level.WARNING,
            "No dismount event found on this server; riders will be able to dismount inside an open gate.");
    }

    // Help integration removed; no setHelp

    /**
     * Sets the log.
     * 
     * @param log
     *            the new log
     */
    private static void setLog(final Logger log)
    {
        WormholeXTreme.log = log;
    }

    /**
     * Sets the pretty log level.
     * 
     * @param level
     *            the new pretty log level
     */
    private static void setPrettyLogLevel(final Level level)
    {
        getLog().setLevel(level);
        getThisPlugin().prettyLog(Level.CONFIG, "Logging set to: " + level);
    }

    /**
     * Sets the scheduler.
     * 
     * @param scheduler
     *            the new scheduler
     */
    private static void setScheduler(final BukkitScheduler scheduler)
    {
        WormholeXTreme.scheduler = scheduler;
    }

    /**
     * Sets the this plugin.
     * 
     * @param thisPlugin
     *            the new this plugin
     */
    private static void setThisPlugin(final WormholeXTreme thisPlugin)
    {
        WormholeXTreme.thisPlugin = thisPlugin;
    }

    /* (non-Javadoc)
     * @see org.bukkit.plugin.Plugin#onDisable()
     */
    @Override
    public void onDisable()
    {
            // Before anything else, and outside the save: whoever was standing far from a
            // proximity mirror is holding a blanked banner that only this plugin was going to
            // take back. Left alone it looks exactly like the plugin having eaten their
            // banners, which is the one impression a shutdown must not leave.
            // Past Exception on purpose, the way disableEconomyQuietly reaches past it. An
            // operator who copies a new jar over a running server leaves this classloader
            // reading a file that is no longer there, so any class it had not loaded yet --
            // MirrorPackets, if no proximity mirror happened to hide that session -- arrives
            // as NoClassDefFoundError. That is an Error, it escaped the old catch, and it took
            // every save below with it: gates, rings, beams and mirrors, none of them written.
            // Cosmetic work must never cost the save.
            try
            {
                com.wormhole_xtreme.wormhole.model.mirror.MirrorProximity.restoreAll();
            }
            catch (final Exception | LinkageError e)
            {
                prettyLog(Level.WARNING, "Failed to restore mirror appearances", e);
            }
            try
            {
                // Persist current runtime configuration to YAML on shutdown
                com.wormhole_xtreme.wormhole.config.Configuration.persistCurrentConfiguration(getThisPlugin().getName());
                final List<Stargate> gates = StargateManager.getAllGates();
                // Every gate is rewritten unconditionally, changed or not -- a clean
                // shutdown is the one moment it costs nothing to guarantee disk matches
                // memory, in case an earlier write failed partway through. The per-gate
                // confirmation is FINE-level (see StargateYamlManager.saveStargate), so
                // this logs one summary line instead of one per gate -- a server with a
                // hundred gates does not need a hundred identical lines on every restart.
                for (final Stargate gate : gates)
                {
                    if (gate.isGateActive() || gate.isGateLightsActive())
                    {
                        gate.shutdownStargate(false);
                    }
                    StargateDBManager.saveStargate(gate);
                }
                if (!gates.isEmpty())
                {
                    prettyLog(Level.INFO, "Saved " + gates.size() + " gate"
                        + (gates.size() == 1 ? "" : "s") + " to disk.");
                }

                saveRings();
                saveBeams();
                saveMirrors();
                StargateDBManager.shutdown();
                disableEconomyQuietly();
                prettyLog(Level.INFO, true, "Successfully shutdown.");
            }
            catch (final Exception | LinkageError e)
            {
                    prettyLog(Level.SEVERE, "Caught exception while shutting down", e);
            }
    }

    /**
     * Puts the rings back and writes them out.
     *
     * <p>Its own method rather than a try inside onDisable's try, and it swallows its own
     * failure: a cycle still mid-animation is put back before its blocks are saved as part of
     * the world, otherwise a server stopped at the wrong moment keeps the rings standing in
     * the floor for good -- but failing to do that must not stop the beams and the database
     * being saved after it.
     */
    private void saveRings()
    {
        try
        {
            com.wormhole_xtreme.wormhole.model.ring.RingTransit.clear();
            for (final String world : ringWorlds())
            {
                com.wormhole_xtreme.wormhole.model.ring.RingYamlManager.saveWorld(world);
            }
        }
        catch (final Exception | LinkageError e)
        {
            prettyLog(Level.WARNING, "Failed to save transport rings", e);
        }
    }

    /**
     * Writes the beam destinations out, and keeps shutting down if it cannot.
     */
    private void saveBeams()
    {
        try
        {
            com.wormhole_xtreme.wormhole.model.beam.BeamYamlManager.saveAll();
        }
        catch (final Exception | LinkageError e)
        {
            prettyLog(Level.WARNING, "Failed to save beam destinations", e);
        }
    }

    /**
     * Writes the quantum mirrors out, and keeps shutting down if it cannot.
     *
     * <p>Belt and braces rather than the only write: every command that changes a mirror saves
     * immediately, so a server killed rather than stopped does not lose one. This catches the
     * case where something changed them without going through a command.
     */
    private void saveMirrors()
    {
        try
        {
            com.wormhole_xtreme.wormhole.model.mirror.MirrorYamlManager.saveAll();
        }
        catch (final Exception | LinkageError e)
        {
            prettyLog(Level.WARNING, "Failed to save quantum mirrors", e);
        }
    }

    /**
     * Lets go of the economy plugin, on the servers that have one.
     *
     * <p>The catch reaches past Exception on purpose: EconomySupport may be absent entirely,
     * which arrives as a LinkageError rather than an exception, and that must not stop the
     * plugin completing its shutdown.
     */
    private void disableEconomyQuietly()
    {
        try
        {
            EconomySupport.disableEconomy();
        }
        catch (final Exception | LinkageError t)
        {
            prettyLog(Level.FINE, "Economy support unavailable during shutdown", t);
        }
    }

    /**
     * Every world that currently holds a ring pair.
     *
     * <p>Saving is per world, so the set of worlds to write is whichever ones have rings in
     * them rather than every world the server has loaded.
     *
     * @return the world names to save
     */
    private static java.util.Set<String> ringWorlds()
    {
        final java.util.Set<String> worlds = new java.util.HashSet<String>();
        for (final com.wormhole_xtreme.wormhole.model.ring.RingPair pair
            : com.wormhole_xtreme.wormhole.model.ring.RingManager.getAllPairs())
        {
            worlds.add(pair.getWorldName());
        }
        return worlds;
    }

    /**
     * Attaches to the economy plugin, if this server is configured to charge for gates.
     *
     * <p>Its own method rather than a try inside onEnable's try, and the catch reaches past
     * Exception for the same reason the shutdown one does: EconomySupport may not be there at
     * all. A server that cannot charge still gets its gates.
     */
    private void enableEconomyIfConfigured()
    {
        if (!ConfigManager.isEconomyEnabled())
        {
            return;
        }
        try
        {
            EconomySupport.enableEconomy();
        }
        catch (final Exception | LinkageError t)
        {
            prettyLog(Level.WARNING, "Failed to enable economy support", t);
        }
    }

    /* (non-Javadoc)
     * @see org.bukkit.plugin.Plugin#onEnable()
     */
    @Override
    public void onEnable()
    {
        logStartupBanner();
        prettyLog(Level.INFO, true, "Enable Beginning.");
        // Try and attach to Permissions and iConomy and Help
        try
        {
            PermissionsSupport.enablePermissions();
            enableEconomyIfConfigured();
        }
        catch (final Exception e)
        {
            prettyLog(Level.WARNING, "Caught Exception while trying to load support plugins.", e);
        }
        registerEvents(true);
        // Before anything reads a stored file. Gates, rings and beam destinations used to
        // live in the same folder as another fork's database; this moves ours out of it, and
        // reading them first would find nothing and load an empty server.
        try
        {
            com.wormhole_xtreme.wormhole.model.LegacyDataFolderMigration.migrate();
        }
        // A migration that throws must not stop the plugin: nothing is deleted, so the files
        // are still in the old folder and the operator has something to recover from.
        catch (final RuntimeException e)
        {
            prettyLog(Level.SEVERE, "Failed to move stored files into the data folder", e);
        }
        // Load stargates.
        prettyLog(Level.INFO, true, "Loading stargates.");
        try
        {
            StargateDBManager.loadStargates(getThisPlugin().getServer());
        }
        catch (final Exception e)
        {
            prettyLog(Level.WARNING, "Failed to load stored gates", e);
            StargateDBManager.loadStargates(getThisPlugin().getServer());
        }
        // Rings load after gates so that a ring overlapping gate blocks is refused against
        // an index that is already populated.
        try
        {
            final int rings = com.wormhole_xtreme.wormhole.model.ring.RingYamlManager.loadAll(
                ConfigManager.getRingReach());
            final int waiting = com.wormhole_xtreme.wormhole.model.ring.RingYamlManager.loadPending();
            prettyLog(Level.INFO, true, LOADED + rings + " transport ring pairs"
                + ((waiting > 0) ? (" and " + waiting + " half-built ones.") : "."));
        }
        // A ring subsystem that cannot load must not stop the gates from working.
        catch (final Exception e)
        {
            prettyLog(Level.WARNING, "Failed to load transport rings", e);
        }
        // A beam subsystem that cannot load must not stop gates or rings from working.
        try
        {
            final int destinations = com.wormhole_xtreme.wormhole.model.beam.BeamYamlManager.loadAll();
            prettyLog(Level.INFO, true, LOADED + destinations + " beam destination"
                + (destinations == 1 ? "" : "s") + ".");
        }
        catch (final Exception e)
        {
            prettyLog(Level.WARNING, "Failed to load beam destinations", e);
        }
        // Likewise a mirror subsystem that cannot load must not stop the other three.
        try
        {
            final int mirrors = com.wormhole_xtreme.wormhole.model.mirror.MirrorYamlManager.loadAll();
            prettyLog(Level.INFO, true, LOADED + mirrors + " quantum mirror"
                + (mirrors == 1 ? "" : "s") + ".");
        }
        catch (final Exception e)
        {
            prettyLog(Level.WARNING, "Failed to load quantum mirrors", e);
        }
        registerEvents(false);
        registerCommands();
        final long entityScanIntervalTicks = ConfigManager.getEntityScanIntervalTicks();
        prettyLog(Level.INFO, true, "Non-player entity gate scan interval: " + entityScanIntervalTicks + " ticks");
        // Periodic sweep: send loose non-player entities that drift into an open
        // wormhole through it. Players and vehicles have their own events; this covers
        // dropped items and wandering mobs, which generate none.
        WormholeXTreme.getScheduler().runTaskTimer(WormholeXTreme.getThisPlugin(),
            GateEntityScanner.create(), 20L, entityScanIntervalTicks);
        // Projectiles cross a portal in about a tick, far too fast for the sweep above to
        // see, so they are followed individually and checked every tick while in flight.
        WormholeXTreme.getScheduler().runTaskTimer(WormholeXTreme.getThisPlugin(),
            ProjectileGateTracker.createTicker(), 20L, 1L);
        // An open wormhole hums. One sweep over the open gates rather than a task per gate:
        // the work is the same and there is nothing per-gate to cancel or leak.
        WormholeXTreme.getScheduler().runTaskTimer(WormholeXTreme.getThisPlugin(),
            com.wormhole_xtreme.wormhole.model.GateSounds::tickAmbient,
            20L, ConfigManager.getGateSoundAmbientTicks());
        // Mirrors set to proximity go dark until somebody walks up to them. One sweep over
        // the registered mirrors, which does nothing at all on a server whose mirrors are
        // all ordinary, and nothing on 1.20 where per-player block updates do not exist.
        WormholeXTreme.getScheduler().runTaskTimer(WormholeXTreme.getThisPlugin(),
            com.wormhole_xtreme.wormhole.model.mirror.MirrorProximity.createTicker(),
            40L, ConfigManager.getMirrorProximityTicks());
        // A mirror names itself above the hotbar to whoever is looking at it. Its own task
        // rather than a second job inside the sweep above: that one walks the mirrors, this
        // one walks the players, and folding them together would mean doing the more expensive
        // of the two loops for the sake of the cheaper. Shares the period because both are
        // about what a player sees when they approach a banner.
        WormholeXTreme.getScheduler().runTaskTimer(WormholeXTreme.getThisPlugin(),
            com.wormhole_xtreme.wormhole.model.mirror.MirrorSignpost.createTicker(),
            40L, ConfigManager.getMirrorProximityTicks());
        // Said after gates have loaded, so it can tell an empty server from a full one.
        com.wormhole_xtreme.wormhole.model.LegacyDatabaseImporter.announceIfWorthwhile();
        prettyLog(Level.INFO, true, "Enable Completed.");
    }

    /* (non-Javadoc)
     * @see org.bukkit.plugin.java.JavaPlugin#onLoad()
     */
    @Override
    public void onLoad()
    {
        setThisPlugin(this);
        setLog(getThisPlugin().getServer().getLogger());
        setScheduler(getThisPlugin().getServer().getScheduler());
        prettyLog(Level.INFO, true, "Load Beginning.");
        // Load our config files and set logging level right away.
        ConfigManager.setupConfigs(getThisPlugin().getName());
        WormholeXTreme.setPrettyLogLevel(ConfigManager.getLogLevel());
        // Load our shapes and internal permissions. Stargates are loaded in onEnable
        // because world creation is not allowed during plugin startup (onLoad).
        StargateShapeRegistry.loadShapes();
        // Mirror looks, beside gate shapes and read the same way. A failure here must not
        // stop a server starting: a mirror with no look is still a working mirror.
        try
        {
            final int presets =
                com.wormhole_xtreme.wormhole.model.mirror.MirrorPresetRegistry.load();
            prettyLog(Level.INFO, true, LOADED + presets + " mirror look"
                + (presets == 1 ? "" : "s") + ".");
        }
        catch (final Exception e)
        {
            prettyLog(Level.WARNING, "Failed to load mirror looks", e);
        }
        prettyLog(Level.INFO, true, "Load Completed.");
    }

    /**
     * Checks whether a message at this level would actually be emitted.
     *
     * <p>{@link #prettyLog} builds its prefix and concatenates the message before the
     * logger gets a chance to discard it, and callers usually build the message eagerly
     * too. On a per-tick path that is pure garbage. Guard those call sites with this.
     *
     * @param severity
     *            the level the message would be logged at
     * @return true if the message would be emitted
     */
    public boolean isLoggable(final Level severity)
    {
        final Logger logger = getLog();
        return (logger != null) && logger.isLoggable(severity);
    }

    /**
     * The gate ring seen face on, with the event horizon inside it.
     *
     * <p>Every glyph here is East-Asian-Width Ambiguous, and that is the constraint, not a
     * coincidence: a terminal set to render Ambiguous as wide doubles them all together, so
     * the ring comes out fat rather than torn. The earlier drawing mixed Ambiguous half
     * blocks with Narrow {@code U+2590} and {@code U+2591}, and those terminals stretched
     * the arcs to ten columns while the middle row stayed at eight.
     */
    private static final String[] RING_BLOCKS = { "  ▄▀▀▄", " █ ▒▒ █", "  ▀▄▄▀" };

    /** The same ring for a console whose charset has no block glyphs at all. */
    private static final String[] RING_ASCII = { "  ,-.", " ( o )", "  `-'" };

    /** Column the text starts at, so both labels line up whichever ring is drawn. */
    private static final int TEXT_COLUMN = 10;

    /**
     * Prints the plugin's name, version and host to the console on startup.
     *
     * <p>Kept to three lines because a banner is a courtesy in a log somebody is reading to
     * find something else.
     *
     * <p>These go through the server logger rather than {@link #prettyLog}, which builds a
     * {@code [WormholeXTreme]} prefix onto every line and would push the drawing sideways.
     */
    private void logStartupBanner()
    {
        if (!isLoggable(Level.INFO))
        {
            return;
        }
        try
        {
            getLog().info("");
            for (final String line : bannerLines(consoleCharset(), getDescription().getVersion(), getServer().getName()))
            {
                getLog().info(line);
            }
            getLog().info("");
        }
        // Decoration only: a console that will not take it must not stop the plugin.
        catch (final RuntimeException ignore) { /* best effort */ }
    }

    /**
     * Builds the three drawn lines of the startup banner.
     *
     * <p>Split out from the logging so the choice of ring can be tested without a server.
     *
     * @param charset
     *            the charset the console will encode the lines with
     * @param version
     *            the plugin version, for the first label
     * @param host
     *            the server implementation name, for the second
     * @return the three lines, art and label already joined
     */
    static String[] bannerLines(final Charset charset, final String version, final String host)
    {
        final String[] ring = canEncode(charset, RING_BLOCKS) ? RING_BLOCKS : RING_ASCII;
        return new String[]
        {
            ring[0],
            pad(ring[1]) + "Wormhole X-Treme v" + version,
            pad(ring[2]) + "Running on " + host,
        };
    }

    /**
     * Pads a line of the drawing out to the column the labels start at.
     *
     * @param art
     *            one line of the ring
     * @return that line, padded
     */
    private static String pad(final String art)
    {
        final StringBuilder padded = new StringBuilder(art);
        while (padded.length() < TEXT_COLUMN)
        {
            padded.append(' ');
        }
        return padded.toString();
    }

    /**
     * Whether every line of a drawing survives this charset.
     *
     * <p>The block glyphs are all in CP437 but in none of the Latin-1 family, and a server
     * whose console is piped through a panel frequently lands on CP1252.
     *
     * @param charset
     *            the charset the console will encode the lines with
     * @param art
     *            the lines of the drawing
     * @return true if the drawing can be written out as it stands
     */
    private static boolean canEncode(final Charset charset, final String[] art)
    {
        if (!charset.canEncode())
        {
            return false;
        }
        final CharsetEncoder encoder = charset.newEncoder();
        for (final String line : art)
        {
            if (!encoder.canEncode(line))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * The charset the console actually writes in, which on Windows is the active code page
     * and not necessarily the JVM default.
     *
     * <p>Package-private so a test can drive the property lookup; there is no console under
     * Surefire, so the tail of this method is all a test would otherwise reach.
     *
     * <p>The {@link Charset#defaultCharset()} at the end is not the mistake {@code
     * PlatformCharsetIsNeverUsedTest} guards against. That one is about writing a file in one
     * charset and reading it back in another. This is asking what the console will do with
     * what we hand it, and with no console to ask, the platform default is the best guess
     * available -- and guessing wrong only costs us the block glyphs.
     *
     * @return the console charset, or the platform default if there is no console
     */
    static Charset consoleCharset()
    {
        // Set by the JVM from the real stream encoding; the second name is the pre-19 spelling.
        for (final String property : new String[] { "stdout.encoding", "sun.stdout.encoding" })
        {
            final String named = System.getProperty(property);
            if ((named != null) && Charset.isSupported(named))
            {
                return Charset.forName(named);
            }
        }
        final Console console = System.console();
        return console != null ? console.charset() : Charset.defaultCharset();
    }

    /**
     * Logs a line tagged with the plugin name.
     *
     * <p>This is the form to use. The three-argument overload below puts the plugin version in
     * the tag as well, which belongs on a startup banner and almost nowhere else -- so 263 of
     * the 273 calls in this plugin passed a bare {@code false} to say "not that one". A boolean
     * literal with no name attached tells a reader nothing, and having it on almost every call
     * taught everyone to skim past the argument sitting next to it.
     *
     * @param severity
     *            the level to log at
     * @param message
     *            the line to log, without the plugin tag
     */
    public void prettyLog(final Level severity, final String message)
    {
        prettyLog(severity, false, message);
    }

    /**
     * Logs a line tagged with the plugin name, and with its version when asked.
     *
     * <p>Prefer {@link #prettyLog(Level, String)}. Reach for this one only where the version is
     * genuinely part of the message -- the startup banner, or a line someone will paste into a
     * bug report. The nine callers left in this class are all lifecycle lines.
     *
     * @param severity
     *            the level to log at
     * @param version
     *            whether to put the plugin version in the tag
     * @param message
     *            the line to log, without the plugin tag
     */
    public void prettyLog(final Level severity, final boolean version, final String message)
    {
        // The version is looked up only in the branch that wants it. It used to be read on
        // every line logged and discarded on almost all of them.
        final String pluginVersion = version ? getThisPlugin().getDescription().getVersion() : null;
        // A supplier, so the tag is not built and joined for a line the level will discard.
        // Every FINE call on a server logging at INFO pays for that otherwise, and this
        // method is how the whole plugin logs.
        getLog().log(severity, () -> prettyTag(getThisPlugin().getName(), pluginVersion) + " " + message);
    }

    /**
     * Logs a line tagged with the plugin name, and what went wrong underneath it.
     *
     * <p>The form to use in a {@code catch}. Every site in this plugin used to append
     * {@code e.getMessage()} to the message instead, which for a {@code NullPointerException}
     * -- the one you most want to see -- is the literal word {@code null}, and for an
     * {@code IOException} is a bare filename with nothing saying what was being done to it.
     * The stack trace, which says where, was thrown away every time.
     *
     * <p>The message is still built lazily, so a {@code FINE} line on a server logging at
     * {@code INFO} costs nothing but the call.
     *
     * @param severity
     *            the level to log at
     * @param message
     *            the line to log, without the plugin tag and without the exception
     * @param thrown
     *            what went wrong; may be null, which logs the line on its own
     */
    public void prettyLog(final Level severity, final String message, final Throwable thrown)
    {
        getLog().log(severity, thrown,
            () -> prettyTag(getThisPlugin().getName(), null) + " " + message);
    }

    /**
     * The bracketed prefix every logged line carries.
     *
     * <p>Its own method, taking plain strings, because the alternative for testing it is a live
     * server: the name and version come off {@code JavaPlugin}, whose accessors are
     * {@code final} and so cannot be stubbed. Pulled out when the version lookup moved into the
     * branch that uses it, so that change had something to be checked by.
     *
     * @param pluginName
     *            the plugin's name
     * @param pluginVersion
     *            the version to include, or null to leave it out
     * @return the prefix, without the trailing space that separates it from the message
     */
    static String prettyTag(final String pluginName, final String pluginVersion)
    {
        final String named = "[" + pluginName + "]";
        return pluginVersion == null ? named : named + "[v" + pluginVersion + "]";
    }

}
