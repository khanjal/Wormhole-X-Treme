/*
 * ATTRIBUTION: This file uses the package name 'com.nijiko.permissions' from the original
 * Nijiko Permissions plugin, but is a newly written minimal Vault-compatible adapter
 * for Wormhole X-Treme, NOT a direct copy of the original Nijiko implementation.
 * Original project: https://github.com/nijikokun/Permissions (deprecated)
 * This stub allows legacy code referencing the old API to work with modern Vault permissions.
 * 
 * Wormhole X-Treme is licensed under GPL v3.
 */
package com.nijiko.permissions;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Minimal Vault-compatible stub for old Permissions API handler.
 * Written for Wormhole X-Treme to maintain backward compatibility.
 */
public class PermissionHandler {
    private Object vault = null;
    private java.lang.reflect.Method vaultHasPlayer = null;
    private java.lang.reflect.Method vaultHasString = null;

    public PermissionHandler() {
        try {
            final Class<?> permClass = Class.forName("net.milkbowl.vault.permission.Permission");
            final RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(permClass);
            if (rsp != null) {
                vault = rsp.getProvider();
                // find has(Player, String)
                try { vaultHasPlayer = vault.getClass().getMethod("has", org.bukkit.entity.Player.class, String.class); } catch (final Throwable ignore) {}
                // find has(String, String, String) or has(String, String)
                for (final java.lang.reflect.Method m : vault.getClass().getMethods()) {
                    if (m.getName().equals("has")) {
                        final Class<?>[] pts = m.getParameterTypes();
                        if (pts.length == 3 && pts[0] == String.class && pts[1] == String.class && pts[2] == String.class) {
                            vaultHasString = m; break;
                        }
                        if (pts.length == 2 && pts[0] == String.class && pts[1] == String.class) {
                            vaultHasString = m; break;
                        }
                    }
                }
            }
        } catch (final Throwable ignore) {}
    }

    public PermissionHandler(final Object vaultProvider) {
        try {
            this.vault = vaultProvider;
            try { vaultHasPlayer = vault.getClass().getMethod("has", org.bukkit.entity.Player.class, String.class); } catch (final Throwable ignore) {}
            for (final java.lang.reflect.Method m : vault.getClass().getMethods()) {
                if (m.getName().equals("has")) {
                    final Class<?>[] pts = m.getParameterTypes();
                    if (pts.length == 3 && pts[0] == String.class && pts[1] == String.class && pts[2] == String.class) {
                        vaultHasString = m; break;
                    }
                    if (pts.length == 2 && pts[0] == String.class && pts[1] == String.class) {
                        vaultHasString = m; break;
                    }
                }
            }
        } catch (final Throwable ignore) {}
    }

    public boolean has(final Player player, final String node) {
        if (player == null) return false;
        try {
            if (vault != null && vaultHasPlayer != null) return (Boolean) vaultHasPlayer.invoke(vault, player, node);
            return player.hasPermission(node);
        } catch (final Throwable t) {
            return player.isOp();
        }
    }

    public boolean has(final String playerName, final String node) {
        final Player p = Bukkit.getPlayer(playerName);
        return p != null ? has(p, node) : false;
    }

    public boolean has(final CommandSender sender, final String node) {
        if (sender == null) return false;
        try {
            if (sender instanceof Player) return has((Player) sender, node);
            if (vault != null && vaultHasString != null && sender != null) {
                final Class<?>[] pts = vaultHasString.getParameterTypes();
                if (pts.length == 3) return (Boolean) vaultHasString.invoke(vault, null, sender.getName(), node);
                if (pts.length == 2) return (Boolean) vaultHasString.invoke(vault, sender.getName(), node);
            }
            return sender.hasPermission(node);
        } catch (final Throwable t) {
            return sender.isOp();
        }
    }
}
