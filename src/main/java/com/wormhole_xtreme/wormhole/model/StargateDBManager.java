package com.wormhole_xtreme.wormhole.model;
import org.bukkit.Server;

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

}
