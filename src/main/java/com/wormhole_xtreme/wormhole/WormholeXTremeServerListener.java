package com.wormhole_xtreme.wormhole;

import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
// Help plugin integration removed
import com.wormhole_xtreme.wormhole.plugin.PermissionsSupport;
import com.wormhole_xtreme.wormhole.plugin.WormholeWorldsSupport;

/**
 * WormholeXTreme Server Listener.
 * 
 * @author Ben Echols (Lologarithm)
 * @author Dean Bailey (alron)
 */
class WormholeXTremeServerListener implements Listener
{

    /* (non-Javadoc)
     * @see org.bukkit.event.server.ServerListener#onPluginDisabled(org.bukkit.event.server.PluginEvent)
     */
    @EventHandler
    public void onPluginDisable(final PluginDisableEvent event)
    {
        if (event.getPlugin().getName().equals("Permissions") && !ConfigManager.getPermissionsSupportDisable())
        {
            PermissionsSupport.disablePermissions();
        }
        // Help plugin integration removed
        else if (event.getPlugin().getName().equals("WormholeXTremeWorlds") && ConfigManager.isWormholeWorldsSupportEnabled())
        {
            WormholeWorldsSupport.disableWormholeWorlds();
        }
    }

    /* (non-Javadoc)
     * @see org.bukkit.event.server.ServerListener#onPluginEnabled(org.bukkit.event.server.PluginEvent)
     */
    @EventHandler
    public void onPluginEnable(final PluginEnableEvent event)
    {
        if (event.getPlugin().getName().equals("Permissions") && !ConfigManager.getPermissionsSupportDisable())
        {
            PermissionsSupport.enablePermissions();
        }
        // Help plugin integration removed
        else if (event.getPlugin().getName().equals("WormholeXTremeWorlds") && ConfigManager.isWormholeWorldsSupportEnabled())
        {
            WormholeWorldsSupport.enableWormholeWorlds();
        }
    }
}
