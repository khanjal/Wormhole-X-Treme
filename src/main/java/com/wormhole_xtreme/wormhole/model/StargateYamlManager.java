package com.wormhole_xtreme.wormhole.model;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.yaml.snakeyaml.Yaml;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.utils.PluginDirectory;
import com.wormhole_xtreme.wormhole.utils.PluginLog;
import com.wormhole_xtreme.wormhole.utils.YamlMaps;
import com.wormhole_xtreme.wormhole.utils.YamlStore;

/**
 * Simple per-gate YAML manager.
 * Stores a small YAML file per gate containing metadata and GateData as base64 bytes.
 */
public class StargateYamlManager
{
    private static final String OWNER_UUID_KEY = "OwnerUUID";
    /** Anything that is not safe in a file name, replaced with an underscore. */
    private static final String UNSAFE_IN_FILENAME = "[^a-zA-Z0-9._-]";

    /** Static helpers only; never instantiated. */
    private StargateYamlManager()
    {
    }

    public static File getGatesDir()
    {
        return PluginDirectory.resolve(PluginDirectory.PLUGIN_FOLDER, "WormholeXTremeDB", "gates");
    }

    public static void loadStargates(final Server server)
    {
        loadStargates(server, getGatesDir());
    }

    /**
     * Reads every gate file in a given directory.
     *
     * <p>Split out from {@link #loadStargates(Server)} for the same reason
     * {@link #saveStargate(Stargate, File)} was: {@link #getGatesDir()} resolves to a real
     * directory, so a test that reads or writes gate files needs somewhere else to point.
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
                    WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "Failed to load gate from " + f.getName(), e);
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
        final Map<String, Object> map = YamlMaps.asMap(yaml.load(in));
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
        final String ownerUuid = (String) map.getOrDefault(OWNER_UUID_KEY, "");
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
     * The file one gate is stored in.
     *
     * <p>A gate with no name has no file. {@code StargateManager.normalizeGateName} returns
     * null rather than throwing, so a nameless gate can reach here -- and this runs for every
     * gate on every shutdown, where an exception would stop the rest of them being saved.
     *
     * <p>Empty counts as no name too: the sanitiser would turn it into a hidden file called
     * ".yml" that the loader would then read back as a gate.
     *
     * @param gateName
     *            the gate's name, or null
     * @return the file name, or null if the gate has no name
     */
    private static String yamlFileNameFor(final String gateName)
    {
        return ((gateName == null) || gateName.isEmpty())
            ? null
            : gateName.replaceAll(UNSAFE_IN_FILENAME, "_") + ".yml";
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
        final String fileName = yamlFileNameFor(s.getGateName());
        if (fileName == null)
        {
            return;
        }
        if (!gatesDir.exists())
        {
            gatesDir.mkdirs();
        }
        final File outFile = new File(gatesDir, fileName);
        final Map<String, Object> map = new HashMap<>();
        map.put("Name", s.getGateName());
        map.put(OWNER_UUID_KEY, s.getGateOwner());
        map.put("OwnerName", ownerNameToSave(s.getStoredGateOwnerName()));
        map.put("Network", s.getGateNetwork() != null ? s.getGateNetwork().getNetworkName() : "");
        map.put("WorldName", s.getGateWorld() != null ? s.getGateWorld().getName() : "");
        map.put("WorldEnvironment", s.getGateWorld() != null ? s.getGateWorld().getEnvironment().toString() : "");
        map.put("GateShape", s.getGateShape() != null ? s.getGateShape().getShapeName() : "Standard");
        final byte[] data = GateSerializer.stargateToBinary(s);
        if (data == null)
        {
            // stargateToBinary returns null when it cannot encode the gate, having logged why.
            // A file without GateData loads as a gate with no blocks, which is worse than no
            // file at all -- and this runs in a loop over every gate on shutdown.
            return;
        }
        map.put("GateData", Base64.getEncoder().encodeToString(data));

        try
        {
            YamlStore.write(outFile, map);
        }
        catch (final IOException e)
        {
            PluginLog.log(Level.WARNING, "Failed to write YAML gate file " + outFile.getName(), e);
        }
        // FINE rather than INFO: this fires once per gate, and onDisable() calls it for
        // every gate on every shutdown whether or not anything changed. At INFO that is
        // one console line per gate on every restart -- for a server with dozens of
        // gates, that is dozens of lines nobody reads, forever. The load-time summary
        // above stays at INFO because "N loaded" is one line regardless of gate count.
        //
        // Guarded because the message is built before prettyLog is called, so an
        // unguarded line pays for getAbsolutePath() and a concatenation per gate per
        // shutdown to throw the result away.
        if (PluginLog.isLoggable(Level.FINE))
        {
            PluginLog.log(Level.FINE, "Saved gate to YAML: " + outFile.getAbsolutePath());
        }
    }

    public static void removeStargate(final Stargate s)
    {
        final String fileName = yamlFileNameFor(s.getGateName());
        if (fileName == null)
        {
            return;
        }
        final File outFile = new File(getGatesDir(), fileName);
        try
        {
            java.nio.file.Files.deleteIfExists(outFile.toPath());
        }
        catch (final java.io.IOException e)
        {
            // Files rather than File.delete: the boolean says only that it did not happen,
            // where the exception says why. This one matters -- a gate whose file survives
            // comes back on the next load, and the reason is what makes that fixable.
            // getGatesDir above tolerates a null plugin, so this cannot assume one either.
            final WormholeXTreme plugin = WormholeXTreme.getThisPlugin();
            if (plugin != null)
            {
                plugin.prettyLog(Level.WARNING, "Could not delete gate file " + outFile.getPath()
                    + "; the gate may come back on next load.", e);
            }
        }
    }

    /**
     * Read the Owner field from a per-gate YAML file if present.
     * Returns null if the file or Owner field is missing.
     */
    public static String readOwnerFromYaml(final String gateName)
    {
        final String fileName = yamlFileNameFor(gateName);
        if (fileName == null)
        {
            return null;
        }
        final File inFile = new File(getGatesDir(), fileName);
        if (!inFile.exists())
        {
            return null;
        }
        final Yaml yaml = new Yaml();
        try (FileInputStream in = new FileInputStream(inFile))
        {
            final Map<String, Object> map = YamlMaps.asMap(yaml.load(in));
            final String ownerUuid = (String) map.getOrDefault(OWNER_UUID_KEY, null);
            final String legacyOwner = (String) map.getOrDefault("Owner", null);
            // Prefer UUID, fall back to legacy name
            return ((ownerUuid != null) && !ownerUuid.isEmpty()) ? ownerUuid : legacyOwner;
        }
        catch (final Exception e)
        {
            if (WormholeXTreme.getThisPlugin() != null)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "Failed to read Owner from YAML for " + gateName, e);
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
