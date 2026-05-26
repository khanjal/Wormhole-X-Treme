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
            // Schema matches the legacy WormholeXTreme SQLite format.
            // Name is UNIQUE (not the PK) so that upsert by name works and multiple worlds are supported.
            try (PreparedStatement ps = conn.prepareStatement(
                "CREATE TABLE IF NOT EXISTS Stargates ("
                + "Id INTEGER PRIMARY KEY,"
                + "Name VARCHAR(128) UNIQUE,"
                + "GateData BINARY,"
                + "Network VARCHAR(255),"
                + "World VARCHAR(512) DEFAULT '',"
                + "WorldName VARCHAR(255) DEFAULT '',"
                + "WorldEnvironment VARCHAR(255) DEFAULT '',"
                + "Owner VARCHAR(255),"
                + "GateShape VARCHAR(255) DEFAULT '',"
                + "Message VARCHAR(1024) DEFAULT ''"
                + ");"))
            {
                ps.execute();
            }
            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, false, "SQLite storage initialized at: " + f.getAbsolutePath());

            // Attempt to add a unique index on Name for legacy databases that lack one.
            // This is a no-op if the index already exists.  It will fail silently if
            // there are existing duplicate Name rows — those are handled by loadStargates.
            try (PreparedStatement idx = conn.prepareStatement(
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_stargates_name ON Stargates(Name);"))
            {
                idx.execute();
                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "SQLite: unique index on Name ensured.");
            }
            catch (final SQLException idxEx)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false,
                    "SQLite: could not create unique index on Name (legacy DB may still have duplicate rows): " + idxEx.getMessage());
            }
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
            // ORDER BY Id ASC so that for duplicate Name rows the highest Id (most recently saved)
            // is processed last and wins in the deduplication map below.
            // WorldName may be empty in old records; fall back to World column in that case.
            final String sql = "SELECT Name, Owner, Network, GateData,"
                + " CASE WHEN WorldName IS NOT NULL AND length(WorldName) > 0 THEN WorldName ELSE World END AS WorldName"
                + " FROM Stargates ORDER BY Id ASC;";

            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery())
            {
                final List<org.bukkit.World> serverWorlds = server.getWorlds();
                // Use a LinkedHashMap keyed by Name to deduplicate: later rows (higher Id) overwrite earlier ones.
                final java.util.LinkedHashMap<String, Stargate> dedupeMap = new java.util.LinkedHashMap<String, Stargate>();
                int totalRows = 0;
                int failed = 0;
                while (rs.next())
                {
                    totalRows++;
                    final String name = rs.getString("Name");
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
                        dedupeMap.put(name, s);
                    }
                    else
                    {
                        WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "SQLite: could not deserialise gate '" + name + "' in any known world.");
                        failed++;
                    }
                }

                list.addAll(dedupeMap.values());
                final int dupeRowsRemoved = totalRows - failed - list.size();
                if (dupeRowsRemoved > 0)
                {
                    WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, false,
                        "SQLite: removed " + dupeRowsRemoved + " duplicate Name row(s) from " + totalRows + " total rows.");
                }
                WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, false,
                    "SQLite load complete: " + list.size() + " unique gate(s) loaded, " + failed + " failed.");
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
        final String name = s.getGateName();
        final String owner = s.getGateOwner() != null ? s.getGateOwner() : "";
        final String network = s.getGateNetwork() != null ? s.getGateNetwork().getNetworkName() : "";
        final String worldName = s.getGateWorld() != null ? s.getGateWorld().getName() : "";
        final String worldEnv = s.getGateWorld() != null ? s.getGateWorld().getEnvironment().toString() : "";

        // DELETE before INSERT so that existing rows with this Name (including any
        // duplicate rows in legacy databases without a UNIQUE constraint) are removed
        // before writing the latest state.  This is safer than INSERT OR REPLACE, which
        // only triggers conflict-replacement when a UNIQUE / PRIMARY KEY constraint fires.
        try (PreparedStatement del = conn.prepareStatement("DELETE FROM Stargates WHERE Name = ?;"))
        {
            del.setString(1, name);
            del.executeUpdate();
        }
        catch (final SQLException e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "SQLite: pre-save delete failed for '" + name + "': " + e.getMessage());
        }

        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO Stargates (Name, Owner, Network, WorldName, WorldEnvironment, GateData) VALUES (?, ?, ?, ?, ?, ?);"))
        {
            ps.setString(1, name);
            ps.setString(2, owner);
            ps.setString(3, network);
            ps.setString(4, worldName);
            ps.setString(5, worldEnv);
            ps.setBytes(6, data);
            ps.executeUpdate();
        }
        catch (final SQLException e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "SQLite save error for " + name + ": " + e.getMessage());
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
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM Stargates WHERE Name = ?;"))
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
