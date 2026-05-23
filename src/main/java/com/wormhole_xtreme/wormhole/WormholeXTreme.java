/*
 *   Wormhole X-Treme Plugin for Bukkit
 *   Copyright (C) 2011  Ben Echols
 *                       Dean Bailey
 *   Copyright (C) 2026  Justin Harding
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.wormhole_xtreme.wormhole;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import com.nijiko.permissions.PermissionHandler;
import com.wormhole_xtreme.worlds.handler.WorldHandler;
import com.wormhole_xtreme.wormhole.command.Dial;
import com.wormhole_xtreme.wormhole.command.Wormhole;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.StargateShapeRegistry;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateDBManager;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.storage.StorageBackend;
import com.wormhole_xtreme.wormhole.storage.StorageFactory;
import com.wormhole_xtreme.wormhole.permissions.PermissionsManager;
import com.wormhole_xtreme.wormhole.plugin.PermissionsSupport;
import com.wormhole_xtreme.wormhole.plugin.EconomySupport;
import com.wormhole_xtreme.wormhole.plugin.WormholeWorldsSupport;
import com.wormhole_xtreme.wormhole.utils.DBUpdateUtil;

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

    /** The Permissions. */
    private static PermissionHandler permissions = null;

    // Help plugin removed; no external help integration.

    /** The wormhole x treme worlds. */
    private static WorldHandler worldHandler = null;

    /** The Scheduler. */
    private static BukkitScheduler scheduler = null;

    /** The This plugin. */
    private static WormholeXTreme thisPlugin = null;

    /** The log. */
    private static Logger log = null;

    // Help integration removed; no getHelp

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
     * Gets the permissions.
     * 
     * @return the permissions
     */
    public static PermissionHandler getPermissions()
    {
        return permissions;
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
     * Gets the wormhole x treme worlds.
     * 
     * @return the wormhole x treme worlds
     */
    public static WorldHandler getWorldHandler()
    {
        return worldHandler;
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
        }
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
     * Sets the permissions.
     * 
     * @param permissions
     *            the new permissions
     */
    public static void setPermissions(final PermissionHandler permissions)
    {
        WormholeXTreme.permissions = permissions;
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

    /**
     * Sets the wormhole x treme worlds.
     * 
     * @param wormholeXTremeWorlds
     *            the new wormhole x treme worlds
     */
    public static void setWorldHandler(final WorldHandler worldHandler)
    {
        WormholeXTreme.worldHandler = worldHandler;
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
                // Store all our gates
                for (final Stargate gate : gates)
                {
                    if (gate.isGateActive() || gate.isGateLightsActive())
                    {
                        gate.shutdownStargate(false);
                    }
                    StargateDBManager.stargateToSQL(gate);
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
                e.printStackTrace();
            }
    }

    /* (non-Javadoc)
     * @see org.bukkit.plugin.Plugin#onEnable()
     */
    @Override
    public void onEnable()
    {
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
            if (ConfigManager.isWormholeWorldsSupportEnabled())
            {
                WormholeWorldsSupport.enableWormholeWorlds();
            }
        }
        catch (final Exception e)
        {
            prettyLog(Level.WARNING, false, "Caught Exception while trying to load support plugins." + e.getMessage());
            e.printStackTrace();
        }
        registerEvents(true);
        if ( !ConfigManager.isWormholeWorldsSupportEnabled())
        {
            // Now it's safe to load stargates which may create worlds.
            prettyLog(Level.INFO, true, "Wormhole Worlds support disabled in settings; loading stargates now.");
            try
            {
                final String backend = com.wormhole_xtreme.wormhole.config.ConfigManager.getStorageBackend();
                prettyLog(Level.INFO, true, "Selected storage backend: " + backend);
                StorageFactory.initialize();
                final StorageBackend sb = StorageFactory.getBackend();
                if (sb != null)
                {
                    final java.util.List<Stargate> loaded = sb.loadStargates(getThisPlugin().getServer());
                    for (final Stargate s : loaded)
                    {
                        StargateManager.registerStargate(s);
                    }
                }
                else
                {
                    StargateDBManager.loadStargates(getThisPlugin().getServer());
                }
            }
            catch (final Exception e)
            {
                prettyLog(Level.WARNING, false, "Storage initialization failed, falling back to existing DB/YAML logic: " + e.getMessage());
                StargateDBManager.loadStargates(getThisPlugin().getServer());
            }
            registerEvents(false);
            registerCommands();
            prettyLog(Level.INFO, true, "Enable Completed.");
        }
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
        // Make sure DB is up to date with latest SCHEMA
        DBUpdateUtil.updateDB();
        // Load our shapes and internal permissions. Stargates are loaded in onEnable
        // because world creation is not allowed during plugin startup (onLoad).
        StargateShapeRegistry.loadShapes();
        PermissionsManager.loadPermissions();
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
    public void prettyLog(final Level severity, final boolean version, final String message)
    {
        final String prettyName = ("[" + getThisPlugin().getName() + "]");
        final String prettyVersion = ("[v" + getThisPlugin().getPluginMeta().getVersion() + "]");
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
