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
import org.bukkit.World;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.logic.StargateHelper;
import com.wormhole_xtreme.wormhole.model.Stargate;

/**
 * Read-only StorageBackend adapter for the legacy HSQLDB gate database.
 *
 * This backend is intentionally read-only — it is only used to load old gate
 * data so it can be migrated to YAML or SQLite via /wx migrate.
 *
 * The HSQLDB data files are expected at:
 *   plugins/WormholeXTreme/WormholeXTreme.properties  (+ .script, .data, .backup)
 *
 * The old schema:
 *   Stargates (Id INTEGER IDENTITY, Name VARCHAR(128), GateData LONGVARBINARY)
 *   VersionInfo (Version INTEGER)
 */
public class HsqldbStorage implements StorageBackend
{
    /** HSQLDB file database path — no extension, HSQLDB appends .properties/.script etc. */
    private String dbPath = null;
    private Connection conn = null;

    /**
     * Build the path to the HSQLDB files from the plugin data folder.
     * Old plugin stored them as   plugins/WormholeXTreme/WormholeXTreme
     */
    private String resolveDbPath()
    {
        try
        {
            final File dataDir = WormholeXTreme.getThisPlugin().getDataFolder();
            return new File(dataDir, "WormholeXTreme").getAbsolutePath();
        }
        catch (final Throwable t)
        {
            return "plugins" + File.separator + "WormholeXTreme" + File.separator + "WormholeXTreme";
        }
    }

    @Override
    public void initialize()
    {
        try
        {
            Class.forName("org.hsqldb.jdbcDriver");
        }
        catch (final ClassNotFoundException e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.SEVERE, false, "HSQLDB driver not found — hsqldb.jar must be in the plugin's lib folder: " + e.getMessage());
            return;
        }

        dbPath = resolveDbPath();

        // Verify that the HSQLDB .properties file exists before trying to connect
        final File propsFile = new File(dbPath + ".properties");
        if (!propsFile.exists())
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false,
                "HSQLDB: no legacy database found at " + propsFile.getAbsolutePath() + " — skipping HSQLDB backend.");
            return;
        }

        try
        {
            // Open read-only so we cannot accidentally corrupt the old files
            conn = DriverManager.getConnection("jdbc:hsqldb:file:" + dbPath + ";readonly=true", "sa", "");
            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, false, "HSQLDB legacy database opened (read-only) at: " + dbPath);
        }
        catch (final SQLException e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "HSQLDB connect failed: " + e.getMessage());
        }
    }

    /** Returns true if the underlying connection is available. */
    public boolean isAvailable()
    {
        return conn != null;
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
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "HSQLDB connection unavailable; skipping legacy load.");
            return list;
        }

        // Determine which worlds the server knows about so we can resolve block locations
        final List<World> worlds = server.getWorlds();

        PreparedStatement stmt = null;
        ResultSet rs = null;
        try
        {
            stmt = conn.prepareStatement("SELECT Name, GateData FROM Stargates;");
            rs = stmt.executeQuery();
            int loaded = 0;
            int failed = 0;
            while (rs.next())
            {
                final String name = rs.getString("Name");
                final byte[] data = rs.getBytes("GateData");
                if (data == null || data.length == 0)
                {
                    WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "HSQLDB: gate '" + name + "' has no data blob — skipping.");
                    failed++;
                    continue;
                }

                // Try each world until parsing succeeds (the world name is embedded inside the byte data)
                Stargate s = null;
                for (final World w : worlds)
                {
                    try
                    {
                        s = StargateHelper.parseVersionedData(data, w, name, null);
                        if (s != null)
                        {
                            break;
                        }
                    }
                    catch (final Throwable t)
                    {
                        // Wrong world — try next
                    }
                }

                if (s != null)
                {
                    list.add(s);
                    loaded++;
                }
                else
                {
                    WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "HSQLDB: could not deserialise gate '" + name + "' in any known world.");
                    failed++;
                }
            }
            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, false,
                "HSQLDB legacy load complete: " + loaded + " loaded, " + failed + " failed.");
        }
        catch (final SQLException e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "HSQLDB load error: " + e.getMessage());
        }
        finally
        {
            try { if (rs != null) rs.close(); } catch (final SQLException ignore) {}
            try { if (stmt != null) stmt.close(); } catch (final SQLException ignore) {}
        }
        return list;
    }

    /** Read-only — saving is not supported. */
    @Override
    public void saveStargate(final Stargate s)
    {
        WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false,
            "HsqldbStorage is read-only. Use /wx migrate to export data to a writable backend.");
    }

    /** Read-only — removal is not supported. */
    @Override
    public void removeStargate(final Stargate s)
    {
        WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false,
            "HsqldbStorage is read-only. Use /wx migrate to export data to a writable backend.");
    }

    @Override
    public void shutdown()
    {
        if (conn != null)
        {
            try
            {
                conn.close();
            }
            catch (final SQLException ignore) {}
            conn = null;
            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, false, "HSQLDB connection closed.");
        }
    }
}
