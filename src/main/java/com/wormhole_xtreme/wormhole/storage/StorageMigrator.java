package com.wormhole_xtreme.wormhole.storage;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
        List<Stargate> gates = Collections.emptyList();

        // Explicit source handling (support 'hsqldb' and 'sqlite')
        if (source != null)
        {
            if ("hsqldb".equalsIgnoreCase(source))
            {
                sender.sendMessage("[WX] Reading gates from legacy HSQLDB database...");
                gates = loadFromHsqldb(server, sender);
                if (gates.isEmpty())
                {
                    return; // message already sent inside loadFromHsqldb
                }
                sender.sendMessage("[WX] HSQLDB: " + gates.size() + " gate(s) read.");
            }
            else if ("sqlite".equalsIgnoreCase(source))
            {
                sender.sendMessage("[WX] Reading gates from SQLite database...");
                gates = loadFromSqlite(server, sender);
                if (gates.isEmpty())
                {
                    return; // message already sent inside loadFromSqlite
                }
                sender.sendMessage("[WX] SQLite: " + gates.size() + " gate(s) read.");
            }
            else
            {
                sender.sendMessage("[WX] Unknown source '" + source + "'. Supported sources: hsqldb, sqlite, or omit to use current backend.");
                return;
            }
        }
        else
        {
            // No explicit source: try to auto-detect legacy DBs first (sqlite, then hsqldb),
            // otherwise fall back to the currently configured backend in memory.
            try
            {
                // Prefer SQLite if a DB file exists
                try
                {
                    final String sqlitePath = com.wormhole_xtreme.wormhole.config.ConfigManager.getStorageSqlitePath();
                    final File sqliteFile = new File(sqlitePath);
                    if (sqliteFile.exists())
                    {
                        sender.sendMessage("[WX] Detected SQLite DB at: " + sqliteFile.getAbsolutePath());
                        gates = loadFromSqlite(server, sender);
                        if (!gates.isEmpty())
                        {
                            sender.sendMessage("[WX] SQLite: " + gates.size() + " gate(s) read.");
                        }
                    }
                }
                catch (final Throwable ignore) {}

                // If no gates yet, try legacy HSQLDB
                if (gates.isEmpty())
                {
                    final List<Stargate> hsq = loadFromHsqldb(server, sender);
                    if (!hsq.isEmpty())
                    {
                        gates = hsq;
                        sender.sendMessage("[WX] HSQLDB: " + gates.size() + " gate(s) read.");
                    }
                }

                // If still empty, load from the currently-configured backend
                if (gates.isEmpty())
                {
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
            }
            catch (final Throwable t)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Warning: auto-detect source failed: " + t.getMessage());
                try
                {
                    StargateDBManager.loadStargates(server);
                }
                catch (final Throwable ignored) {}
                gates = StargateManager.getAllGates();
            }
        }

        if (gates.isEmpty())
        {
            sender.sendMessage("[WX] No gates found to migrate.");
            return;
        }

        // --- Write to destination -----------------------------------------------
        if (destination.equalsIgnoreCase("file"))
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

        sender.sendMessage("[WX] Unknown destination '" + destination + "'. Supported: file, sqlite.");
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static void migrateGatesToYaml(final List<Stargate> gates, final boolean force, final CommandSender sender)
    {
        final File gatesDir = com.wormhole_xtreme.wormhole.model.StargateYamlManager.getGatesDir();
        if (!gatesDir.exists())
        {
            gatesDir.mkdirs();
        }

        final int sourceCount = gates.size();
        final int destBefore = countYamlFiles(gatesDir);
        sender.sendMessage("[WX] Source: " + sourceCount + " gate(s) to migrate.");
        sender.sendMessage("[WX] Destination (" + gatesDir.getAbsolutePath() + "): " + destBefore + " gate(s) already present.");

        int transferred = 0;
        int duplicates = 0;
        int failed = 0;
        for (final Stargate s : gates)
        {
            try
            {
                final String fileName = s.getGateName().replaceAll("[^a-zA-Z0-9._-]", "_") + ".yml";
                final File outFile = new File(gatesDir, fileName);
                if (outFile.exists() && !force)
                {
                    duplicates++;
                    continue;
                }
                StargateYamlManager.saveStargate(s);
                transferred++;
            }
            catch (final Exception e)
            {
                failed++;
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Failed to migrate gate " + s.getGateName() + ": " + e.getMessage());
            }
        }

        final int destAfter = countYamlFiles(gatesDir);
        sender.sendMessage("[WX] --- Migration Summary (file) ---");
        sender.sendMessage("[WX]   Source gates:       " + sourceCount);
        sender.sendMessage("[WX]   Transferred:        " + transferred);
        sender.sendMessage("[WX]   Duplicates skipped: " + duplicates);
        sender.sendMessage("[WX]   Failed:             " + failed);
        sender.sendMessage("[WX]   Destination total:  " + destAfter + " gate(s)");
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

        final int sourceCount = gates.size();
        sender.sendMessage("[WX] Source: " + sourceCount + " gate(s) to migrate.");

        // Load existing gate names to detect duplicates and report pre-migration destination count.
        final Set<String> existingNames = new HashSet<String>();
        int destBefore = 0;
        try
        {
            final List<Stargate> existing = sqlite.loadStargates(server);
            destBefore = existing.size();
            for (final Stargate e : existing)
            {
                existingNames.add(e.getGateName());
            }
        }
        catch (final Throwable t)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "SQLite: could not read existing gates for pre-count: " + t.getMessage());
        }
        sender.sendMessage("[WX] Destination (SQLite): " + destBefore + " gate(s) already present.");

        int transferred = 0;
        int duplicates = 0;
        int failed = 0;
        for (final Stargate s : gates)
        {
            try
            {
                if (existingNames.contains(s.getGateName()) && !force)
                {
                    duplicates++;
                    continue;
                }
                sqlite.saveStargate(s);
                transferred++;
            }
            catch (final Exception e)
            {
                failed++;
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "SQLite: failed to write gate " + s.getGateName() + ": " + e.getMessage());
            }
        }

        int destAfter = destBefore + transferred;
        try
        {
            destAfter = sqlite.loadStargates(server).size();
        }
        catch (final Throwable t)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "SQLite: could not read final gate count: " + t.getMessage());
        }
        sqlite.shutdown();

        sender.sendMessage("[WX] --- Migration Summary (sqlite) ---");
        sender.sendMessage("[WX]   Source gates:       " + sourceCount);
        sender.sendMessage("[WX]   Transferred:        " + transferred);
        sender.sendMessage("[WX]   Duplicates skipped: " + duplicates);
        sender.sendMessage("[WX]   Failed:             " + failed);
        sender.sendMessage("[WX]   Destination total:  " + destAfter + " gate(s)");
    }

    /**
     * Load all stargates from the legacy HSQLDB database directly, bypassing the
     * currently-configured backend. Used so you can migrate without first editing config.yml.
     */
    private static int countYamlFiles(final File dir)
    {
        if (!dir.exists() || !dir.isDirectory())
        {
            return 0;
        }
        final File[] files = dir.listFiles(new FilenameFilter()
        {
            @Override
            public boolean accept(final File d, final String name)
            {
                return name.endsWith(".yml");
            }
        });
        return (files != null) ? files.length : 0;
    }

    private static List<Stargate> loadFromHsqldb(final Server server, final CommandSender sender)
    {
        String hsqlBase;
        try
        {
            hsqlBase = new File(WormholeXTreme.getThisPlugin().getDataFolder(), "WormholeXTreme").getAbsolutePath();
        }
        catch (final Throwable t)
        {
            hsqlBase = "plugins" + File.separator + "WormholeXTreme" + File.separator + "WormholeXTreme";
        }
        sender.sendMessage("[WX] HSQLDB: looking for legacy database at: " + hsqlBase + ".properties");
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

    private static List<Stargate> loadFromSqlite(final Server server, final CommandSender sender)
    {
        final String sqlitePath = com.wormhole_xtreme.wormhole.config.ConfigManager.getStorageSqlitePath();
        sender.sendMessage("[WX] SQLite: looking for database at: " + new File(sqlitePath).getAbsolutePath());
        final SqliteStorage sqlite = new SqliteStorage();
        try
        {
            sqlite.initialize();
        }
        catch (final Throwable t)
        {
            sender.sendMessage("[WX] SQLite: failed to initialise: " + t.getMessage());
            return Collections.emptyList();
        }
        final List<Stargate> gates = sqlite.loadStargates(server);
        sqlite.shutdown();
        if (gates == null || gates.isEmpty())
        {
            sender.sendMessage("[WX] SQLite: no gates found or failed to read database.");
            return Collections.emptyList();
        }
        return gates;
    }
}
