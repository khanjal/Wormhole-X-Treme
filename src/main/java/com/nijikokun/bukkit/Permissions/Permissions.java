/*
 * ATTRIBUTION: This file uses the package name 'com.nijikokun.bukkit.Permissions' from the
 * original Nijikokun Permissions plugin, but is a newly written minimal adapter
 * for Wormhole X-Treme, NOT a direct copy of the original Nijikokun implementation.
 * Original project: https://github.com/nijikokun/Permissions (deprecated)
 * This stub allows legacy code to instantiate the old plugin class with modern Vault backing.
 * 
 * Wormhole X-Treme is licensed under GPL v3.
 */
package com.nijikokun.bukkit.Permissions;

import com.nijiko.permissions.PermissionHandler;

/**
 * Minimal stub for the old Permissions plugin main class.
 * Written for Wormhole X-Treme to maintain backward compatibility.
 */
public class Permissions {
    public PermissionHandler getHandler() {
        return new PermissionHandler();
    }
}
