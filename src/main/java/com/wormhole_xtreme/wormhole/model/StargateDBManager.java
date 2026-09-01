package com.wormhole_xtreme.wormhole.model;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.Server;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.permissions.PermissionsManager.PermissionLevel;

/**
 * Gate persistence.
 *
 * <p>Gates live in one YAML file each under {@code plugins/WormholeXTreme/.../gates}.
 * There used to be a pluggable backend behind this — HSQLDB originally, later SQLite —
 * but a keyed store of a few thousand small records read once at startup gets nothing
 * from a database engine that a folder of files does not already give, and the drivers
 * were most of the plugin's download size. The abstraction went with them.
 */
public class StargateDBManager
{
    /**
     * Loads every stored gate and registers it.
     *
     * @param server
     *            the server, used to resolve worlds
     */
    public static void loadStargates(final Server server)
    {
        StargateYamlManager.loadStargates(server);
    }

    /**
     * Writes a gate to disk, creating or replacing its file.
     *
     * @param s
     *            the gate to store
     */
    public static void saveStargate(final Stargate s)
    {
        StargateYamlManager.saveStargate(s);
    }

    /**
     * Deletes a gate's stored file.
     *
     * @param s
     *            the gate to forget
     */
    public static void removeStargate(final Stargate s)
    {
        StargateYamlManager.removeStargate(s);
    }

    /**
     * Nothing to close: the YAML store holds no handles between operations.
     */
    public static void shutdown()
    {
    }

    /**
     * Legacy individual permissions storage is no longer supported. Return empty map.
     *
     * @return an empty map
     */
    public static ConcurrentHashMap<String, PermissionLevel> getAllIndividualPermissions()
    {
        WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Individual permissions storage is disabled; use Vault/LuckPerms.");
        return new ConcurrentHashMap<String, PermissionLevel>();
    }

    /**
     * No-op for storing individual permissions; recommend using external permission plugin.
     *
     * @param player
     *            the player
     * @param pl
     *            the level
     */
    public static void storeIndividualPermissionInDB(final String player, final PermissionLevel pl)
    {
        WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Skipping storeIndividualPermissionInDB for " + player + " (disabled).");
    }
}
