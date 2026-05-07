package com.wormhole_xtreme.wormhole.storage;

import java.util.logging.Level;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;

/**
 * Central factory that initializes and exposes the active StorageBackend.
 */
public class StorageFactory
{
    private static StorageBackend backend = null;

    public static synchronized void initialize()
    {
        if (backend != null)
        {
            return;
        }
        try
        {
            final String b = ConfigManager.getStorageBackend();
            if (b != null && b.equalsIgnoreCase("sqlite"))
            {
                final SqliteStorage s = new SqliteStorage();
                s.initialize();
                backend = s;
                WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, false, "Storage backend: SQLite initialized.");
            }
            else if (b != null && b.equalsIgnoreCase("hsqldb"))
            {
                final HsqldbStorage h = new HsqldbStorage();
                h.initialize();
                backend = h;
                WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, false, "Storage backend: HSQLDB (read-only legacy) initialized.");
            }
            else
            {
                backend = null; // use YAML per-gate files
                WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, false, "Storage backend: using per-gate YAML files.");
            }
        }
        catch (final Exception e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Failed to initialize storage backend: " + e.getMessage());
            backend = null;
        }
    }

    public static StorageBackend getBackend()
    {
        return backend;
    }

    public static void shutdown()
    {
        try
        {
            if (backend != null)
            {
                backend.shutdown();
            }
        }
        catch (final Exception e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Error shutting down storage backend: " + e.getMessage());
        }
    }
}
