package com.wormhole_xtreme.wormhole.model;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.Server;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.StargateYamlManager;
import com.wormhole_xtreme.wormhole.permissions.PermissionsManager.PermissionLevel;
import com.wormhole_xtreme.wormhole.storage.StorageBackend;
import com.wormhole_xtreme.wormhole.storage.StorageFactory;

/**
 * Adapter for storage backends. Replaces legacy HSQLDB-specific manager.
 * Delegates to configured `StorageBackend` (sqlite/file) or falls back to YAML.
 */
public class StargateDBManager
{
    // Delegates to StorageFactory for backend lifecycle and access

    /**
     * Load stargates using the selected backend. Falls back to YAML loader if no backend is present.
     */
    public static void loadStargates(final Server server)
    {
        final StorageBackend backend = StorageFactory.getBackend();
        if (backend != null)
        {
            final List<Stargate> loaded = backend.loadStargates(server);
            for (final Stargate s : loaded)
            {
                // If backend returned a gate without an Owner, try to recover Owner from per-gate YAML file.
                if ((s.getGateOwner() == null) || (s.getGateOwner().length() == 0))
                {
                    try
                    {
                        final String ownerFromYaml = StargateYamlManager.readOwnerFromYaml(s.getGateName());
                        if ((ownerFromYaml != null) && (ownerFromYaml.length() > 0))
                        {
                            s.setGateOwner(ownerFromYaml);
                        }
                    }
                    catch (final Exception e)
                    {
                        WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Could not recover owner for " + s.getGateName() + " from YAML: " + e.getMessage());
                    }
                }
                StargateManager.registerStargate(s);
            }
            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, false, loaded.size() + " Wormholes loaded from configured storage backend.");
            return;
        }
        // Fallback to YAML per-gate loader
        StargateYamlManager.loadStargates(server);
    }

    /**
     * Save or update a stargate to the active backend. Falls back to YAML if no backend.
     */
    public static void stargateToSQL(final Stargate s)
    {
        final StorageBackend backend = StorageFactory.getBackend();
        if (backend != null)
        {
            try
            {
                backend.saveStargate(s);
                return;
            }
            catch (final Exception e)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Backend save failed, falling back to YAML: " + e.getMessage());
            }
        }
        StargateYamlManager.saveStargate(s);
    }

    /**
     * Remove stargate from storage backend (or YAML). Safe when backend is null.
     */
    protected static void removeStargateFromSQL(final Stargate s)
    {
        final StorageBackend backend = StorageFactory.getBackend();
        if (backend != null)
        {
            try
            {
                backend.removeStargate(s);
                return;
            }
            catch (final Exception e)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Backend delete failed, falling back to YAML remove: " + e.getMessage());
            }
        }
        StargateYamlManager.removeStargate(s);
    }

    /**
     * Shutdown backend if present.
     */
    public static void shutdown()
    {
        StorageFactory.shutdown();
    }

    /**
     * Legacy individual permissions storage is no longer supported. Return empty map.
     */
    public static ConcurrentHashMap<String, PermissionLevel> getAllIndividualPermissions()
    {
        WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Individual permissions storage is disabled; use Vault/LuckPerms.");
        return new ConcurrentHashMap<String, PermissionLevel>();
    }

    /**
     * No-op for storing individual permissions; recommend using external permission plugin.
     */
    public static void storeIndividualPermissionInDB(final String player, final PermissionLevel pl)
    {
        WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Skipping storeIndividualPermissionInDB for " + player + " (disabled).");
    }
}
