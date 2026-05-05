package com.wormhole_xtreme.wormhole.storage;

import java.io.File;
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
     */
    public static void migrateTo(final String backend, final boolean force, final CommandSender sender)
    {
        final Server server = WormholeXTreme.getThisPlugin().getServer();
        if (backend == null)
        {
            sender.sendMessage("Invalid backend specified.");
            return;
        }

        if (backend.equalsIgnoreCase("file") || backend.equalsIgnoreCase("yaml"))
        {
            sender.sendMessage("Starting migration to YAML per-gate files (non-destructive)...");

            // Ensure DB gates are loaded into memory so we can write them out.
            try
            {
                StargateDBManager.loadStargates(server);
            }
            catch (final Throwable t)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Warning: DB load during migration failed: " + t.getMessage());
            }

            final List<Stargate> gates = StargateManager.getAllGates();
            int migrated = 0;
            int skipped = 0;

            File gatesDir = null;
            try
            {
                if (WormholeXTreme.getThisPlugin() != null)
                {
                    gatesDir = new File(WormholeXTreme.getThisPlugin().getDataFolder(), "gates");
                }
            }
            catch (final Exception e)
            {
                // ignore and fallback
            }
            if (gatesDir == null)
            {
                gatesDir = new File("plugins" + File.separator + "WormholeXTreme" + File.separator + "gates");
            }
            if (!gatesDir.exists())
            {
                gatesDir.mkdirs();
            }

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

            sender.sendMessage("Migration complete. Migrated: " + migrated + ", Skipped: " + skipped + ". Files written to: " + gatesDir.getAbsolutePath());
            return;
        }

        sender.sendMessage("Migration target backend '" + backend + "' is not implemented yet. Supported: file, yaml.");
    }
}
