package com.wormhole_xtreme.wormhole.model;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * Simple per-gate YAML manager.
 * Stores a small YAML file per gate containing metadata and GateData as base64 bytes.
 */
public class StargateYamlManager
{
    public static File getGatesDir()
    {
        try
        {
            if (WormholeXTreme.getThisPlugin() != null)
            {
                return new File(WormholeXTreme.getThisPlugin().getDataFolder(), "WormholeXTremeDB" + File.separator + "gates");
            }
        }
        catch (final Exception e)
        {
            // fallthrough to relative path
        }
        return new File("plugins" + File.separator + "WormholeXTreme" + File.separator + "WormholeXTremeDB" + File.separator + "gates");
    }

    public static void loadStargates(final Server server)
    {
        loadStargates(server, getGatesDir());
    }

    /**
     * Reads every gate file in a given directory.
     *
     * <p>Split out from {@link #loadStargates(Server)} for the same reason
     * {@link #saveStargate(Stargate, File)} was: {@link #getGatesDir()} resolves through
     * {@code JavaPlugin.getDataFolder()}, which is final and cannot be stubbed, so a test
     * needs somewhere else to point.
     *
     * @param server
     *            the server, used to look worlds up by name
     * @param gatesDir
     *            the directory to read from
     */
    static void loadStargates(final Server server, final File gatesDir)
    {
        if (!gatesDir.exists())
        {
            gatesDir.mkdirs();
            return;
        }

        final File[] files = gatesDir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml") || name.toLowerCase(Locale.ROOT).endsWith(".yaml"));
        if (files == null)
        {
            return;
        }
        final Yaml yaml = new Yaml();
        int loaded = 0;
        int movedExits = 0;
        for (final File f : files)
        {
            // Per file: the gates directory is read on startup, and one corrupted file
            // taking the whole load down would lose every gate on the server.
            try (FileInputStream in = new FileInputStream(f))
            {
                final Stargate s = readGate(in, yaml, server);
                if (s == null)
                {
                    continue;
                }
                // Gates written before the arrival point was moved clear of the ring still
                // land travellers inside the portal, and loading restores what was stored
                // rather than recomputing it.
                if (s.normalizeGatePlayerTeleportLocation())
                {
                    movedExits++;
                }
                StargateManager.addStargate(s);
                loaded++;
            }
            catch (final Exception e)
            {
                if (WormholeXTreme.getThisPlugin() != null)
                {
                    WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "Failed to load gate from " + f.getName() + ": " + e.getMessage());
                }
            }
        }
        reportLoad(loaded, movedExits, gatesDir);
    }

    /**
     * Builds one gate from an open gate file.
     *
     * @return the gate, or null if the file describes none
     */
    private static Stargate readGate(final FileInputStream in, final Yaml yaml, final Server server)
    {
        final Object obj = yaml.load(in);
        if (!(obj instanceof Map))
        {
            return null;
        }
        @SuppressWarnings("unchecked")
        final Map<String, Object> map = (Map<String, Object>) obj;
        final String gateDataB64 = (String) map.get("GateData");
        if (gateDataB64 == null)
        {
            return null;
        }
        final String name = (String) map.getOrDefault("Name", "");
        final String worldName = (String) map.getOrDefault("WorldName", "");
        final byte[] data = Base64.getDecoder().decode(gateDataB64);
        final Stargate s = GateSerializer.parseVersionedData(data, server.getWorld(worldName), name, null);
        if (s == null)
        {
            return null;
        }
        applyOwner(s, ownerIdFrom(map), (String) map.getOrDefault("OwnerName", ""));
        applyNetwork(s, (String) map.getOrDefault("Network", ""));
        return s;
    }

    /**
     * Who owns the gate, by whichever field the file carries.
     *
     * <p>OwnerUUID is what is written now. A file old enough to predate it names the owner
     * in Owner instead, as a plain player name.
     */
    private static String ownerIdFrom(final Map<String, Object> map)
    {
        final String ownerUuid = (String) map.getOrDefault("OwnerUUID", "");
        if ((ownerUuid != null) && !ownerUuid.isEmpty())
        {
            return ownerUuid;
        }
        return (String) map.getOrDefault("Owner", "");
    }

    /**
     * Sets the owner and works out what to display for them.
     *
     * <p>A stored name equal to the owner id is not a name -- it is what an old save bug
     * wrote -- so it is treated as absent and resolved again, which is what heals a file
     * already carrying a UUID as its OwnerName.
     */
    private static void applyOwner(final Stargate s, final String owner, final String ownerName)
    {
        if ((owner == null) || owner.isEmpty())
        {
            return;
        }
        s.setGateOwner(owner);
        final String savedName = ownerNameFromSave(ownerName, owner);
        if (savedName != null)
        {
            s.setGateOwnerName(savedName);
            return;
        }
        try
        {
            // Resolves for anyone who has joined the server at least once.
            final String resolved = Bukkit.getOfflinePlayer(UUID.fromString(owner)).getName();
            if (resolved != null)
            {
                s.setGateOwnerName(resolved);
            }
        }
        catch (final IllegalArgumentException notAUuid)
        {
            // Legacy name-based owner: the owner string is the name.
            s.setGateOwnerName(owner);
        }
    }

    /** Puts the gate on its network, registering the network if this is the first gate on it. */
    private static void applyNetwork(final Stargate s, final String network)
    {
        if ((network == null) || network.isEmpty())
        {
            return;
        }
        StargateManager.addGateToNetwork(s, network);
        s.setGateNetwork(StargateManager.getStargateNetwork(network));
    }

    /** One line for the load, and one more only if any gate needed its arrival point moved. */
    private static void reportLoad(final int loaded, final int movedExits, final File gatesDir)
    {
        if (WormholeXTreme.getThisPlugin() == null)
        {
            return;
        }
        WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, loaded + " Wormholes loaded from YAML directory: " + gatesDir.getAbsolutePath());
        if (movedExits > 0)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, movedExits + " gates had their arrival point moved out of the portal. Travellers were appearing inside the ring on those.");
        }
    }


    public static void saveStargate(final Stargate s)
    {
        saveStargate(s, getGatesDir());
    }

    /**
     * Writes one gate's file into a given directory.
     *
     * <p>Split out from {@link #saveStargate(Stargate)} so a test can point the write
     * somewhere other than the live plugin folder. {@link #getGatesDir()} resolves through
     * {@code JavaPlugin.getDataFolder()}, which is {@code final} and therefore cannot be
     * stubbed; the only other way in is reflecting into a private Bukkit field, which is
     * someone else's implementation detail and not something to depend on.
     *
     * @param s
     *            the gate to write
     * @param gatesDir
     *            the directory to write it into, created if it is not there yet
     */
    static void saveStargate(final Stargate s, final File gatesDir)
    {
        if (!gatesDir.exists())
        {
            gatesDir.mkdirs();
        }
        final String fileName = s.getGateName().replaceAll("[^a-zA-Z0-9._-]", "_") + ".yml";
        final File outFile = new File(gatesDir, fileName);
        final Map<String, Object> map = new HashMap<>();
        map.put("Name", s.getGateName());
        map.put("OwnerUUID", s.getGateOwner());
        map.put("OwnerName", ownerNameToSave(s.getStoredGateOwnerName()));
        map.put("Network", s.getGateNetwork() != null ? s.getGateNetwork().getNetworkName() : "");
        map.put("WorldName", s.getGateWorld() != null ? s.getGateWorld().getName() : "");
        map.put("WorldEnvironment", s.getGateWorld() != null ? s.getGateWorld().getEnvironment().toString() : "");
        map.put("GateShape", s.getGateShape() != null ? s.getGateShape().getShapeName() : "Standard");
        final byte[] data = GateSerializer.stargatetoBinary(s);
        map.put("GateData", Base64.getEncoder().encodeToString(data));

        final DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        final Yaml yaml = new Yaml(options);

        // atomic write: write to temp file then move
        try
        {
            final File tmp = new File(outFile.getAbsolutePath() + ".tmp");
            try (BufferedWriter w = new BufferedWriter(new FileWriter(tmp, StandardCharsets.UTF_8)))
            {
                yaml.dump(map, w);
            }
            Files.move(tmp.toPath(), outFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (final IOException e)
        {
            if (WormholeXTreme.getThisPlugin() != null)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "Failed to write YAML gate file " + outFile.getName() + ": " + e.getMessage());
            }
        }
        // FINE rather than INFO: this fires once per gate, and onDisable() calls it for
        // every gate on every shutdown whether or not anything changed. At INFO that is
        // one console line per gate on every restart -- for a server with dozens of
        // gates, that is dozens of lines nobody reads, forever. The load-time summary
        // above stays at INFO because "N loaded" is one line regardless of gate count.
        if (WormholeXTreme.getThisPlugin() != null)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "Saved gate to YAML: " + outFile.getAbsolutePath());
        }
    }

    public static void removeStargate(final Stargate s)
    {
        final File GATES_DIR = getGatesDir();
        final String fileName = s.getGateName().replaceAll("[^a-zA-Z0-9._-]", "_") + ".yml";
        final File outFile = new File(GATES_DIR, fileName);
        // getGatesDir above tolerates a null plugin, so this cannot assume one either.
        final WormholeXTreme plugin = WormholeXTreme.getThisPlugin();
        if (outFile.exists() && !outFile.delete() && (plugin != null))
        {
            plugin.prettyLog(Level.WARNING,
                "Could not delete gate file " + outFile.getPath() + "; the gate may come back on next load.");
        }
    }

    /**
     * Read the Owner field from a per-gate YAML file if present.
     * Returns null if the file or Owner field is missing.
     */
    public static String readOwnerFromYaml(final String gateName)
    {
        final File GATES_DIR = getGatesDir();
        final String fileName = gateName.replaceAll("[^a-zA-Z0-9._-]", "_") + ".yml";
        final File inFile = new File(GATES_DIR, fileName);
        if (!inFile.exists())
        {
            return null;
        }
        final Yaml yaml = new Yaml();
        try (FileInputStream in = new FileInputStream(inFile))
        {
            final Object obj = yaml.load(in);
            if (obj instanceof Map)
            {
                @SuppressWarnings("unchecked")
                final Map<String, Object> map = (Map<String, Object>) obj;
                final String ownerUuid = (String) map.getOrDefault("OwnerUUID", null);
                final String legacyOwner = (String) map.getOrDefault("Owner", null);
                // Prefer UUID, fall back to legacy name
                final String owner = ((ownerUuid != null) && !ownerUuid.isEmpty()) ? ownerUuid : legacyOwner;
                return owner;
            }
        }
        catch (final Exception e)
        {
            if (WormholeXTreme.getThisPlugin() != null)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "Failed to read Owner from YAML for " + gateName + ": " + e.getMessage());
            }
        }
        return null;
    }

    public static void shutdown()
    {
        // nothing to do for YAML
    }

    /**
     * What a gate's {@code OwnerName} field should hold when it is written out.
     *
     * <p>Takes the <em>stored</em> name, which is null when nobody has ever resolved one.
     * {@code Stargate.getGateOwnerName()} would answer the owner id instead, because for
     * display an id beats nothing -- but writing that answer to disk turns it into the
     * gate's name for good: the next load sees a non-empty OwnerName, takes it for a real
     * name, and never tries to resolve the UUID again. One save of a gate whose owner the
     * server had not seen yet was enough to put a UUID on its sign permanently, and
     * refreshing a gate saves it.
     *
     * @param storedName
     *            the gate's stored display name, or null if it has none
     * @return the value to write, empty when there is no name to write
     */
    static String ownerNameToSave(final String storedName)
    {
        return storedName != null ? storedName : "";
    }

    /**
     * The display name a saved gate actually carries, or null if it carries none.
     *
     * <p>A name equal to the owner id is not a name. It is what the bug above wrote, and
     * treating it as absent is what lets an already-written file heal itself: the caller
     * falls through to resolving the UUID again, and the next save stores the real answer.
     *
     * <p>A legacy gate whose owner <em>is</em> a player name reaches the same place by the
     * same rule and still ends up correct -- the caller's UUID parse fails and it sets the
     * owner string as the name, which for those gates is exactly right.
     *
     * @param ownerNameField
     *            the OwnerName value read from the file
     * @param owner
     *            the gate's owner id
     * @return a usable display name, or null if the file has none worth trusting
     */
    static String ownerNameFromSave(final String ownerNameField, final String owner)
    {
        if ((ownerNameField == null) || ownerNameField.isEmpty())
        {
            return null;
        }
        if (ownerNameField.equals(owner))
        {
            return null;
        }
        return ownerNameField;
    }
}
