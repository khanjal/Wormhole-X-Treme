package com.wormhole_xtreme.wormhole;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.plugin.PluginManager;
import org.bukkit.Location;
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

    /** Follows projectiles in flight so they cross a gate at the moment they reach it. */
    private static final ProjectileGateTracker projectileTracker = new ProjectileGateTracker();

    /** The Scheduler. */
    private static BukkitScheduler scheduler = null;

    /** The This plugin. */
    private static WormholeXTreme thisPlugin = null;

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
                plugin.prettyLog(Level.FINE, false, "Dismount handling registered via " + candidate);
                return;
            }
            catch (final NoClassDefFoundError notOnThisServer)
            {
                continue;
            }
            catch (final ReflectiveOperationException | RuntimeException e)
            {
                plugin.prettyLog(Level.FINE, false,
                    "Could not register " + candidate + ": " + e.getMessage());
            }
        }
        plugin.prettyLog(Level.WARNING, false,
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
        getThisPlugin().prettyLog(Level.CONFIG, false, "Logging set to: " + level);
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
            try
            {
                // Persist current runtime configuration to YAML on shutdown
                com.wormhole_xtreme.wormhole.config.Configuration.persistCurrentConfiguration(getThisPlugin().getName());
                final ArrayList<Stargate> gates = StargateManager.getAllGates();
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
                    prettyLog(Level.INFO, false, "Saved " + gates.size() + " gate"
                        + (gates.size() == 1 ? "" : "s") + " to disk.");
                }

                // Any cycle still mid-animation is put back before its blocks are saved as
                // part of the world, otherwise a server stopped at the wrong moment keeps
                // the rings standing in the floor for good.
                try
                {
                    com.wormhole_xtreme.wormhole.model.ring.RingTransit.clear();
                    for (final String world : ringWorlds())
                    {
                        com.wormhole_xtreme.wormhole.model.ring.RingYamlManager.saveWorld(world);
                    }
                }
                catch (final Exception e)
                {
                    prettyLog(Level.WARNING, false, "Failed to save transport rings: " + e.getMessage());
                }

                try
                {
                    com.wormhole_xtreme.wormhole.model.beam.BeamYamlManager.saveAll();
                }
                catch (final Exception e)
                {
                    prettyLog(Level.WARNING, false, "Failed to save beam destinations: " + e.getMessage());
                }

                StargateDBManager.shutdown();
                try
                {
                    EconomySupport.disableEconomy();
                }
                catch (final Throwable t)
                {
                    // EconomySupport class may be absent in some deployments; do not let that
                    // prevent the plugin from completing shutdown.
                    prettyLog(Level.FINE, false, "Economy support unavailable during shutdown: " + t.getMessage());
                }
                prettyLog(Level.INFO, true, "Successfully shutdown.");
            }
            catch (final Exception e)
            {
                    prettyLog(Level.SEVERE, false, "Caught exception while shutting down: " + e.getMessage());
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
            if (ConfigManager.isEconomyEnabled())
            {
                try {
                    EconomySupport.enableEconomy();
                } catch (final Throwable t) {
                    prettyLog(Level.WARNING, false, "Failed to enable economy support: " + t.getMessage());
                }
            }
        }
        catch (final Exception e)
        {
            prettyLog(Level.WARNING, false, "Caught Exception while trying to load support plugins." + e.getMessage());
        }
        registerEvents(true);
        // Load stargates.
        prettyLog(Level.INFO, true, "Loading stargates.");
        try
        {
            StargateDBManager.loadStargates(getThisPlugin().getServer());
        }
        catch (final Exception e)
        {
            prettyLog(Level.WARNING, false, "Failed to load stored gates: " + e.getMessage());
            StargateDBManager.loadStargates(getThisPlugin().getServer());
        }
        // Rings load after gates so that a ring overlapping gate blocks is refused against
        // an index that is already populated.
        try
        {
            final int rings = com.wormhole_xtreme.wormhole.model.ring.RingYamlManager.loadAll(
                ConfigManager.getRingReach());
            final int waiting = com.wormhole_xtreme.wormhole.model.ring.RingYamlManager.loadPending();
            prettyLog(Level.INFO, true, "Loaded " + rings + " transport ring pairs"
                + ((waiting > 0) ? (" and " + waiting + " half-built ones.") : "."));
        }
        // A ring subsystem that cannot load must not stop the gates from working.
        catch (final Exception e)
        {
            prettyLog(Level.WARNING, false, "Failed to load transport rings: " + e.getMessage());
        }
        // A beam subsystem that cannot load must not stop gates or rings from working.
        try
        {
            final int destinations = com.wormhole_xtreme.wormhole.model.beam.BeamYamlManager.loadAll();
            prettyLog(Level.INFO, true, "Loaded " + destinations + " beam destination"
                + (destinations == 1 ? "" : "s") + ".");
        }
        catch (final Exception e)
        {
            prettyLog(Level.WARNING, false, "Failed to load beam destinations: " + e.getMessage());
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
            new Runnable()
            {
                @Override
                public void run()
                {
                    com.wormhole_xtreme.wormhole.model.GateSounds.tickAmbient();
                }
            }, 20L, ConfigManager.getGateSoundAmbientTicks());
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
        prettyLog(Level.INFO, true, "Load Completed.");
    }

    /**
     * prettyLog: A quick and dirty way to make log output clean, unified, and with versioning as needed.
     * 
     * @param severity
     *            Level of severity in the form of INFO, WARNING, SEVERE, etc.
     * @param version
     *            true causes version display in log entries.
     * @param message
     *            to prettyLog.
     * 
     */
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
        final Logger log = getLog();
        return log != null && log.isLoggable(severity);
    }

    /**
     * Prints the plugin's name, version and host to the console on startup.
     *
     * <p>A gate ring seen face on, with the event horizon inside it. Kept to three lines
     * because a banner is a courtesy in a log somebody is reading to find something else.
     *
     * <p>These go through the server logger rather than {@link #prettyLog}, which builds a
     * {@code [WormholeXTreme]} prefix onto every line and would push the drawing sideways.
     *
     * <p>The characters are half-block and shade glyphs from the same range other plugins
     * draw their banners with. A console that cannot render them shows replacement marks
     * rather than failing, and only the banner is affected.
     */
    private void logStartupBanner()
    {
        try
        {
            final String version = getDescription().getVersion();
            final String host = getServer().getName();
            getLog().info("");
            getLog().info("  ▄▀▀▄");
            getLog().info(" ▐ ░░ ▌   Wormhole X-Treme v" + version);
            getLog().info("  ▀▄▄▀    Running on " + host);
            getLog().info("");
        }
        // Decoration only: a console that will not take it must not stop the plugin.
        catch (final RuntimeException ignore) {}
    }

    public void prettyLog(final Level severity, final boolean version, final String message)
    {
        final String prettyName = ("[" + getThisPlugin().getName() + "]");
        final String prettyVersion = ("[v" + getThisPlugin().getDescription().getVersion() + "]");
        String prettyLogLine = prettyName;
        if (version)
        {
            prettyLogLine += prettyVersion;
            getLog().log(severity, prettyLogLine + " " + message);
        }
        else
        {
            getLog().log(severity, prettyLogLine + " " + message);
        }
    }

}
