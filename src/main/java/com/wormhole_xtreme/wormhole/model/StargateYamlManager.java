package com.wormhole_xtreme.wormhole.model;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
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
        final File GATES_DIR = getGatesDir();
        if (!GATES_DIR.exists())
        {
            GATES_DIR.mkdirs();
            return;
        }

        final File[] files = GATES_DIR.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml") || name.toLowerCase(Locale.ROOT).endsWith(".yaml"));
        if (files == null)
        {
            return;
        }
        final Yaml yaml = new Yaml();
        int loaded = 0;
        int movedExits = 0;
        for (final File f : files)
        {
            try (FileInputStream in = new FileInputStream(f))
            {
                final Object obj = yaml.load(in);
                if (obj instanceof Map)
                {
                    @SuppressWarnings("unchecked")
                    final Map<String, Object> map = (Map<String, Object>) obj;
                    final String name = (String) map.getOrDefault("Name", "");
                    final String ownerUuid = (String) map.getOrDefault("OwnerUUID", "");
                    final String ownerName = (String) map.getOrDefault("OwnerName", "");
                    // Fall back to legacy 'Owner' field (name-based) if OwnerUUID is absent
                    final String legacyOwner = (String) map.getOrDefault("Owner", "");
                    final String owner;
                    if ((ownerUuid != null) && !ownerUuid.isEmpty())
                    {
                        owner = ownerUuid;
                    }
                    else
                    {
                        owner = legacyOwner;
                    }
                    final String gateDataB64 = (String) map.get("GateData");
                    final String worldName = (String) map.getOrDefault("WorldName", "");
                    final String network = (String) map.getOrDefault("Network", "");

                    if (gateDataB64 != null)
                    {
                        final byte[] data = Base64.getDecoder().decode(gateDataB64);
                        final Stargate s = GateSerializer.parseVersionedData(data, server.getWorld(worldName), name, null);
                        if (s != null)
                        {
                            if ((owner != null) && !owner.isEmpty())
                            {
                                s.setGateOwner(owner);
                                // Resolve display name. A name equal to the owner id is not
                                // a name -- it is what the save bug above wrote -- so it is
                                // treated as absent and resolved again below. That is what
                                // heals a file already carrying a UUID as its OwnerName.
                                final String savedName = ownerNameFromSave(ownerName, owner);
                                if (savedName != null)
                                {
                                    s.setGateOwnerName(savedName);
                                }
                                else
                                {
                                    // Try to resolve name from UUID (works for players who have joined at least once)
                                    try
                                    {
                                        final UUID uuid = UUID.fromString(owner);
                                        final String resolved = Bukkit.getOfflinePlayer(uuid).getName();
                                        if (resolved != null)
                                        {
                                            s.setGateOwnerName(resolved);
                                        }
                                    }
                                    catch (final IllegalArgumentException ignored)
                                    {
                                        // Legacy name-based owner: name == owner string
                                        s.setGateOwnerName(owner);
                                    }
                                }
                            }
                            if ((network != null) && !network.isEmpty())
                            {
                                StargateManager.addGateToNetwork(s, network);
                                s.setGateNetwork(StargateManager.getStargateNetwork(network));
                            }
                            // Gates written before the arrival point was moved clear of the
                            // ring still land travellers inside the portal, and loading
                            // restores what was stored rather than recomputing it.
                            if (s.normalizeGatePlayerTeleportLocation())
                            {
                                movedExits++;
                            }
                            StargateManager.addStargate(s);
                            loaded++;
                        }
                    }
                }
            }
            catch (final Exception e)
            {
                if (WormholeXTreme.getThisPlugin() != null)
                {
                    WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Failed to load gate from " + f.getName() + ": " + e.getMessage());
                }
            }
        }
        if (WormholeXTreme.getThisPlugin() != null)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, false, loaded + " Wormholes loaded from YAML directory: " + GATES_DIR.getAbsolutePath());
            if (movedExits > 0)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, false, movedExits + " gates had their arrival point moved out of the portal. Travellers were appearing inside the ring on those.");
            }
        }
    }

    public static void saveStargate(final Stargate s)
    {
        final File GATES_DIR = getGatesDir();
        if (!GATES_DIR.exists())
        {
            GATES_DIR.mkdirs();
        }
        final String fileName = s.getGateName().replaceAll("[^a-zA-Z0-9._-]", "_") + ".yml";
        final File outFile = new File(GATES_DIR, fileName);
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
            try (BufferedWriter w = new BufferedWriter(new FileWriter(tmp)))
            {
                yaml.dump(map, w);
            }
            Files.move(tmp.toPath(), outFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (final IOException e)
        {
            if (WormholeXTreme.getThisPlugin() != null)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Failed to write YAML gate file " + outFile.getName() + ": " + e.getMessage());
            }
        }
        // FINE rather than INFO: this fires once per gate, and onDisable() calls it for
        // every gate on every shutdown whether or not anything changed. At INFO that is
        // one console line per gate on every restart -- for a server with dozens of
        // gates, that is dozens of lines nobody reads, forever. The load-time summary
        // above stays at INFO because "N loaded" is one line regardless of gate count.
        if (WormholeXTreme.getThisPlugin() != null)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Saved gate to YAML: " + outFile.getAbsolutePath());
        }
    }

    public static void removeStargate(final Stargate s)
    {
        final File GATES_DIR = getGatesDir();
        final String fileName = s.getGateName().replaceAll("[^a-zA-Z0-9._-]", "_") + ".yml";
        final File outFile = new File(GATES_DIR, fileName);
        if (outFile.exists())
        {
            outFile.delete();
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
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Failed to read Owner from YAML for " + gateName + ": " + e.getMessage());
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
