package com.wormhole_xtreme.wormhole.storage;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.Server;
import org.bukkit.command.CommandSender;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateDBManager;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.StargateYamlManager;

/**
 * Lightweight storage migration utilities.
 */
public class StorageMigrator
{
    /**
     * Migrate gates to the given backend. Currently implements DB -> YAML (file) migration.
     * If force is false existing YAML files will not be overwritten.
     *
     * Usage from in-game or console:
     *   /wx migrate file          — HSQLDB (or current backend) → per-gate YAML files
     *   /wx migrate sqlite        — HSQLDB (or current backend) → SQLite
     *   /wx migrate hsqldb file   — explicitly read from HSQLDB then write to YAML
     *   /wx migrate hsqldb sqlite — explicitly read from HSQLDB then write to SQLite
     */
    public static void migrateTo(final String backend, final boolean force, final CommandSender sender)
    {
        migrateTo(null, backend, force, sender);
    }

    /**
     * Migrate from a named source backend to a named destination backend.
     * Pass null for source to use whichever backend is currently active.
     */
    public static void migrateTo(final String source, final String destination, final boolean force, final CommandSender sender)
    {
        final Server server = WormholeXTreme.getThisPlugin().getServer();
        if (destination == null)
        {
            sender.sendMessage("[WX] Invalid destination backend specified.");
            return;
        }

        // --- Resolve source gates -----------------------------------------------
        List<Stargate> gates;

        final boolean sourceIsHsqldb = "hsqldb".equalsIgnoreCase(source);

        if (sourceIsHsqldb)
        {
            sender.sendMessage("[WX] Reading gates from legacy HSQLDB database...");
            gates = loadFromHsqldb(server, sender);
            if (gates.isEmpty())
            {
                return; // message already sent inside loadFromHsqldb
            }
            sender.sendMessage("[WX] HSQLDB: " + gates.size() + " gate(s) read.");
        }
        else
        {
            // Use whatever is currently in memory (loaded by StargateDBManager on startup)
            // or reload from the active backend now.
            try
            {
                StargateDBManager.loadStargates(server);
            }
            catch (final Throwable t)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Warning: backend load during migration failed: " + t.getMessage());
            }
            gates = StargateManager.getAllGates();
        }

        if (gates.isEmpty())
        {
            sender.sendMessage("[WX] No gates found to migrate.");
            return;
        }

        // --- Write to destination -----------------------------------------------
        if (destination.equalsIgnoreCase("file") || destination.equalsIgnoreCase("yaml"))
        {
            sender.sendMessage("[WX] Starting migration to per-gate YAML files (non-destructive)...");
            migrateGatesToYaml(gates, force, sender);
            return;
        }

        if (destination.equalsIgnoreCase("sqlite"))
        {
            sender.sendMessage("[WX] Starting migration to SQLite...");
            migrateGatesToSqlite(gates, force, sender, server);
            return;
        }

        sender.sendMessage("[WX] Unknown destination '" + destination + "'. Supported: file, yaml, sqlite.");
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static void migrateGatesToYaml(final List<Stargate> gates, final boolean force, final CommandSender sender)
    {
        File gatesDir = null;
        try
        {
            gatesDir = new File(WormholeXTreme.getThisPlugin().getDataFolder(), "gates");
        }
        catch (final Exception e)
        {
            gatesDir = new File("plugins" + File.separator + "WormholeXTreme" + File.separator + "gates");
        }
        if (!gatesDir.exists())
        {
            gatesDir.mkdirs();
        }

        int migrated = 0;
        int skipped = 0;
        for (final Stargate s : gates)
        {
            try
            {
                final String fileName = s.getGateName().replaceAll("[^a-zA-Z0-9._-]", "_") + ".yml";
                final File outFile = new File(gatesDir, fileName);
                if (outFile.exists() && !force)
                {
                    skipped++;
                    continue;
                }
                StargateYamlManager.saveStargate(s);
                migrated++;
            }
            catch (final Exception e)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Failed to migrate gate " + s.getGateName() + ": " + e.getMessage());
            }
        }
        sender.sendMessage("[WX] Migration complete. Migrated: " + migrated + ", Skipped: " + skipped + ". Files: " + gatesDir.getAbsolutePath());
    }

    private static void migrateGatesToSqlite(final List<Stargate> gates, final boolean force, final CommandSender sender, final Server server)
    {
        final SqliteStorage sqlite = new SqliteStorage();
        try
        {
            sqlite.initialize();
        }
        catch (final Throwable t)
        {
            sender.sendMessage("[WX] Failed to initialise SQLite: " + t.getMessage());
            return;
        }

        int migrated = 0;
        for (final Stargate s : gates)
        {
            try
            {
                sqlite.saveStargate(s);
                migrated++;
            }
            catch (final Exception e)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "SQLite: failed to write gate " + s.getGateName() + ": " + e.getMessage());
            }
        }
        sqlite.shutdown();
        sender.sendMessage("[WX] SQLite migration complete. Written: " + migrated + " gate(s).");
    }

    /**
     * Load all stargates from the legacy HSQLDB database directly, bypassing the
     * currently-configured backend. Used so you can migrate without first editing config.yml.
     */
    private static List<Stargate> loadFromHsqldb(final Server server, final CommandSender sender)
    {
        final HsqldbStorage hsql = new HsqldbStorage();
        hsql.initialize();
        if (!hsql.isAvailable())
        {
            sender.sendMessage("[WX] HSQLDB: no legacy database files found — nothing to migrate.");
            return Collections.emptyList();
        }
        final List<Stargate> gates = hsql.loadStargates(server);
        hsql.shutdown();
        return gates;
    }
}
