package com.wormhole_xtreme.wormhole.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.Server;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.GateSerializer;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * Lightweight SQLite storage scaffold. Implements StorageBackend.
 * Basic read/write methods use a simple table; further schema and migration
 * work can be added later.
 */
public class SqliteStorage implements StorageBackend
{
    private Connection conn;

    @Override
    public void initialize()
    {
        try
        {
            Class.forName("org.sqlite.JDBC");
        }
        catch (final ClassNotFoundException e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.SEVERE, false, "SQLite JDBC driver not found: " + e.getMessage());
            return;
        }

        final String path = ConfigManager.getStorageSqlitePath();

        try
        {
            final File f = new File(path);
            if (!f.getParentFile().exists())
            {
                f.getParentFile().mkdirs();
            }
            conn = DriverManager.getConnection("jdbc:sqlite:" + f.getAbsolutePath());
            conn.setAutoCommit(true);
            // Create minimal table if not exists (schema may be extended later). Include Owner column.
            try (PreparedStatement ps = conn.prepareStatement("CREATE TABLE IF NOT EXISTS Stargates (GateName TEXT PRIMARY KEY, Owner TEXT, Network TEXT, WorldName TEXT, WorldEnvironment TEXT, GateData BLOB);"))
            {
                ps.execute();
            }
            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, false, "SQLite storage initialized at: " + f.getAbsolutePath());
        }
        catch (final SQLException e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.SEVERE, false, "Failed to initialize SQLite storage: " + e.getMessage());
        }
    }

    @Override
    public List<Stargate> loadStargates(final Server server)
    {
        final List<Stargate> list = new ArrayList<>();
        if (conn == null)
        {
            initialize();
        }
        if (conn == null)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "SQLite connection unavailable; skipping load.");
            return list;
        }

        try
        {
            // Try to read Owner and Network if those columns exist; fall back gracefully if not present.
            try
            {
                try (PreparedStatement stmt = conn.prepareStatement("SELECT GateName, Owner, Network, GateData, WorldName FROM Stargates;");
                     ResultSet rs = stmt.executeQuery())
                {
                    final List<org.bukkit.World> serverWorlds = server.getWorlds();
                    int loaded = 0;
                    int failed = 0;
                    while (rs.next())
                    {
                        final String name = rs.getString("GateName");
                        final String owner = rs.getString("Owner");
                        final String network = rs.getString("Network");
                        final byte[] data = rs.getBytes("GateData");
                        final String worldName = rs.getString("WorldName");

                        if (data == null || data.length == 0)
                        {
                            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "SQLite: gate '" + name + "' has no data blob — skipping.");
                            failed++;
                            continue;
                        }

                        // Build a prioritized list of worlds to try parsing against.
                        final java.util.List<org.bukkit.World> tryWorlds = new java.util.ArrayList<org.bukkit.World>();
                        if (worldName != null && !worldName.isEmpty())
                        {
                            final org.bukkit.World w = server.getWorld(worldName);
                            if (w != null)
                            {
                                tryWorlds.add(w);
                            }
                        }
                        // Append all loaded worlds as fallback (avoids missing gates when WorldName mismatches)
                        for (final org.bukkit.World w : serverWorlds)
                        {
                            if (!tryWorlds.contains(w))
                            {
                                tryWorlds.add(w);
                            }
                        }

                        Stargate s = null;
                        for (final org.bukkit.World w : tryWorlds)
                        {
                            try
                            {
                                s = GateSerializer.parseVersionedData(data, w, name, null);
                                if (s != null)
                                {
                                    break;
                                }
                            }
                            catch (final Throwable t)
                            {
                                // Wrong world or corrupt data for this world — try next
                                continue;
                            }
                        }

                        if (s != null)
                        {
                            if ((owner != null) && (owner.length() > 0))
                            {
                                s.setGateOwner(owner);
                            }
                            if ((network != null) && !network.isEmpty())
                            {
                                StargateManager.addGateToNetwork(s, network);
                                s.setGateNetwork(StargateManager.getStargateNetwork(network));
                            }
                            list.add(s);
                            loaded++;
                        }
                        else
                        {
                            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "SQLite: could not deserialise gate '" + name + "' in any known world.");
                            failed++;
                        }
                    }
                    WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, false, "SQLite load complete: " + loaded + " loaded, " + failed + " failed.");
                }
            }
            catch (final SQLException e)
            {
                // Older schema without Owner/Network columns: read without those columns
                try (PreparedStatement stmt = conn.prepareStatement("SELECT GateName, GateData, WorldName FROM Stargates;");
                     ResultSet rs = stmt.executeQuery())
                {
                    final List<org.bukkit.World> serverWorlds = server.getWorlds();
                    int loaded = 0;
                    int failed = 0;
                    while (rs.next())
                    {
                        final String name = rs.getString("GateName");
                        final byte[] data = rs.getBytes("GateData");
                        final String worldName = rs.getString("WorldName");

                        if (data == null || data.length == 0)
                        {
                            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "SQLite: gate '" + name + "' has no data blob — skipping.");
                            failed++;
                            continue;
                        }

                        final java.util.List<org.bukkit.World> tryWorlds = new java.util.ArrayList<org.bukkit.World>();
                        if (worldName != null && !worldName.isEmpty())
                        {
                            final org.bukkit.World w = server.getWorld(worldName);
                            if (w != null)
                            {
                                tryWorlds.add(w);
                            }
                        }
                        for (final org.bukkit.World w : serverWorlds)
                        {
                            if (!tryWorlds.contains(w))
                            {
                                tryWorlds.add(w);
                            }
                        }

                        Stargate s = null;
                        for (final org.bukkit.World w : tryWorlds)
                        {
                            try
                            {
                                s = GateSerializer.parseVersionedData(data, w, name, null);
                                if (s != null)
                                {
                                    break;
                                }
                            }
                            catch (final Throwable t)
                            {
                                continue;
                            }
                        }
                        if (s != null)
                        {
                            list.add(s);
                            loaded++;
                        }
                        else
                        {
                            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "SQLite: could not deserialise gate '" + name + "' in any known world.");
                            failed++;
                        }
                    }
                    WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, false, "SQLite load complete: " + loaded + " loaded, " + failed + " failed.");
                }
            }
        }
        catch (final SQLException e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "SQLite load error: " + e.getMessage());
        }
        return list;
    }

    @Override
    public void saveStargate(final Stargate s)
    {
        if (conn == null)
        {
            initialize();
        }
        if (conn == null)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "SQLite connection unavailable; skipping save for: " + s.getGateName());
            return;
        }

        final byte[] data = GateSerializer.stargatetoBinary(s);
        try (PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO Stargates (GateName, Owner, Network, WorldName, WorldEnvironment, GateData) VALUES (?, ?, ?, ?, ?, ?);"))
        {
            ps.setString(1, s.getGateName());
            ps.setString(2, s.getGateOwner() != null ? s.getGateOwner() : "");
            ps.setString(3, s.getGateNetwork() != null ? s.getGateNetwork().getNetworkName() : "");
            ps.setString(4, s.getGateWorld() != null ? s.getGateWorld().getName() : "");
            ps.setString(5, s.getGateWorld() != null ? s.getGateWorld().getEnvironment().toString() : "");
            ps.setBytes(6, data);
            ps.executeUpdate();
        }
        catch (final SQLException e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "SQLite save error for " + s.getGateName() + ": " + e.getMessage());
        }
    }

    @Override
    public void removeStargate(final Stargate s)
    {
        if (conn == null)
        {
            initialize();
        }
        if (conn == null)
        {
            return;
        }
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM Stargates WHERE GateName = ?;"))
        {
            ps.setString(1, s.getGateName());
            ps.executeUpdate();
        }
        catch (final SQLException e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "SQLite delete error for " + s.getGateName() + ": " + e.getMessage());
        }
    }

    @Override
    public void shutdown()
    {
        try
        {
            if (conn != null && !conn.isClosed())
            {
                conn.close();
            }
        }
        catch (final SQLException e)
        {
            // ignore
        }
    }
}
