package com.wormhole_xtreme.wormhole.model;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.Server;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.logic.StargateHelper;

/**
 * Simple per-gate YAML manager.
 * Stores a small YAML file per gate containing metadata and GateData as base64 bytes.
 */
public class StargateYamlManager
{
    private static File getGatesDir()
    {
        try
        {
            if (WormholeXTreme.getThisPlugin() != null)
            {
                return new File(WormholeXTreme.getThisPlugin().getDataFolder(), "gates");
            }
        }
        catch (final Exception e)
        {
            // fallthrough to relative path
        }
        return new File("plugins" + File.separator + "WormholeXTreme" + File.separator + "gates");
    }

    public static void loadStargates(final Server server)
    {
        final File GATES_DIR = getGatesDir();
        if (!GATES_DIR.exists())
        {
            GATES_DIR.mkdirs();
            return;
        }

        final File[] files = GATES_DIR.listFiles((d, name) -> name.toLowerCase().endsWith(".yml") || name.toLowerCase().endsWith(".yaml"));
        if (files == null)
        {
            return;
        }
        final Yaml yaml = new Yaml();
        int loaded = 0;
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
                    final String gateDataB64 = (String) map.get("GateData");
                    final String network = (String) map.getOrDefault("Network", "");
                    final String worldName = (String) map.getOrDefault("WorldName", "");
                    final String worldEnv = (String) map.getOrDefault("WorldEnvironment", "");

                    if (gateDataB64 != null)
                    {
                        final byte[] data = Base64.getDecoder().decode(gateDataB64);
                        final Stargate s = StargateHelper.parseVersionedData(data, server.getWorld(worldName), name, null);
                        if (s != null)
                        {
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
        map.put("Owner", s.getGateOwner());
        map.put("Network", s.getGateNetwork() != null ? s.getGateNetwork().getNetworkName() : "");
        map.put("WorldName", s.getGateWorld() != null ? s.getGateWorld().getName() : "");
        map.put("WorldEnvironment", s.getGateWorld() != null ? s.getGateWorld().getEnvironment().toString() : "");
        map.put("GateShape", s.getGateShape() != null ? s.getGateShape().getShapeName() : "Standard");
        final byte[] data = StargateHelper.stargatetoBinary(s);
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
        // Log success for diagnostics when plugin context is available
        if (WormholeXTreme.getThisPlugin() != null)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, false, "Saved gate to YAML: " + outFile.getAbsolutePath());
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

    public static void shutdown()
    {
        // nothing to do for YAML
    }
}
