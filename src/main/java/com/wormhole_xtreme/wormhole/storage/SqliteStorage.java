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
            final PreparedStatement ps = conn.prepareStatement("CREATE TABLE IF NOT EXISTS Stargates (GateName TEXT PRIMARY KEY, Owner TEXT, Network TEXT, WorldName TEXT, WorldEnvironment TEXT, GateData BLOB);");
            ps.execute();
            ps.close();
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

        PreparedStatement stmt = null;
        ResultSet rs = null;
        try
        {
            // Try to read Owner and Network if those columns exist; fall back gracefully if not present.
            try
            {
                stmt = conn.prepareStatement("SELECT GateName, Owner, Network, GateData, WorldName FROM Stargates;");
                rs = stmt.executeQuery();
                while (rs.next())
                {
                    final String name = rs.getString("GateName");
                    final String owner = rs.getString("Owner");
                    final String network = rs.getString("Network");
                    final byte[] data = rs.getBytes("GateData");
                    final String world = rs.getString("WorldName");
                    final Stargate s = GateSerializer.parseVersionedData(data, server.getWorld(world), name, null);
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
                    }
                }
            }
            catch (final SQLException e)
            {
                // Older schema without Owner/Network columns: read without those columns
                if (stmt != null)
                {
                    try { stmt.close(); } catch (final SQLException ignore) {}
                }
                if (rs != null)
                {
                    try { rs.close(); } catch (final SQLException ignore) {}
                }
                stmt = conn.prepareStatement("SELECT GateName, GateData, WorldName FROM Stargates;");
                rs = stmt.executeQuery();
                while (rs.next())
                {
                    final String name = rs.getString("GateName");
                    final byte[] data = rs.getBytes("GateData");
                    final String world = rs.getString("WorldName");
                    final Stargate s = GateSerializer.parseVersionedData(data, server.getWorld(world), name, null);
                    if (s != null)
                    {
                        list.add(s);
                    }
                }
            }
        }
        catch (final SQLException e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "SQLite load error: " + e.getMessage());
        }
        finally
        {
            try
            {
                if (rs != null)
                {
                    rs.close();
                }
                if (stmt != null)
                {
                    stmt.close();
                }
            }
            catch (final SQLException e)
            {
                // ignore
            }
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
        PreparedStatement ps = null;
        try
        {
            ps = conn.prepareStatement("INSERT OR REPLACE INTO Stargates (GateName, Owner, Network, WorldName, WorldEnvironment, GateData) VALUES (?, ?, ?, ?, ?, ?);");
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
        finally
        {
            try
            {
                if (ps != null)
                {
                    ps.close();
                }
            }
            catch (final SQLException e)
            {
                // ignore
            }
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
        PreparedStatement ps = null;
        try
        {
            ps = conn.prepareStatement("DELETE FROM Stargates WHERE GateName = ?;");
            ps.setString(1, s.getGateName());
            ps.executeUpdate();
        }
        catch (final SQLException e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "SQLite delete error for " + s.getGateName() + ": " + e.getMessage());
        }
        finally
        {
            try
            {
                if (ps != null)
                {
                    ps.close();
                }
            }
            catch (final SQLException e)
            {
                // ignore
            }
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
