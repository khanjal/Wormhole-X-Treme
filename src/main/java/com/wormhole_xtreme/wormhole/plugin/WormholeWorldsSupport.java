package com.wormhole_xtreme.wormhole.plugin;

import java.util.logging.Level;

import org.bukkit.plugin.Plugin;

import com.wormhole_xtreme.worlds.WormholeXTremeWorlds;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.StargateDBManager;

/**
 * The Class WormholeWorldsSupport.
 * 
 * @author alron
 */
public class WormholeWorldsSupport
{

    /**
     * Check worlds version.
     * 
     * @param version
     *            the version
     * @return true, if successful
     */
    private static boolean checkWorldsVersion(final String version)
    {
        if ( !version.startsWith("0.5"))
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.SEVERE, false, "Not a supported version of Wormhole Worlds. Recommended is 0.5");
            return false;
        }
        return true;
    }

    /**
     * Disable wormhole worlds.
     */
    public static void disableWormholeWorlds()
    {
        if (WormholeXTreme.getWorldHandler() != null)
        {
            WormholeXTreme.setWorldHandler(null);
            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, false, "Detached from Wormhole Worlds plugin.");
        }
    }

    /**
     * Enable wormhole worlds.
     */
    public static void enableWormholeWorlds()
    {
        if (ConfigManager.isWormholeWorldsSupportEnabled())
        {
            if (WormholeXTreme.getWorldHandler() == null)
            {
                final Plugin worldsTest = WormholeXTreme.getThisPlugin().getServer().getPluginManager().getPlugin("WormholeXTremeWorlds");
                if (worldsTest != null)
                {
                    final String version = worldsTest.getPluginMeta().getVersion();
                    if (checkWorldsVersion(version))
                    {
                        try
                        {
                            WormholeXTreme.setWorldHandler(WormholeXTremeWorlds.getWorldHandler());
                            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, false, "Attached to Wormhole Worlds version " + version);
                            // Worlds support means we can continue our load.
                            StargateDBManager.loadStargates(WormholeXTreme.getThisPlugin().getServer());
                            WormholeXTreme.registerEvents(false);
                            WormholeXTreme.registerCommands();
                            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, true, "Enable Completed.");

                        }
                        catch (final ClassCastException e)
                        {
                            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Failed to get cast to Wormhole Worlds: " + e.getMessage());
                        }
                    }
                }
                else
                {
                    WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, false, "Wormhole Worlds Plugin not yet available Stargates will not load until it enables.");
                }
            }
        }
        else
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, false, "Wormhole X-Treme Worlds Plugin support disabled via configuration (config.yml).");
        }
    }
}
