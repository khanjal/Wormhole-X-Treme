package com.wormhole_xtreme.wormhole.plugin;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Level;

import org.bstats.bukkit.Metrics;
import org.bstats.charts.AdvancedPie;
import org.bstats.charts.SimplePie;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.StargateShapeRegistry;
import com.wormhole_xtreme.wormhole.model.beam.BeamManager;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorManager;
import com.wormhole_xtreme.wormhole.model.ring.RingManager;

/**
 * Anonymous usage counts sent to bStats (#239): the Minecraft versions and server software it
 * reports by itself, and how much of each feature is in use, in ranges rather than exact counts.
 *
 * <p>The library is shaded under {@code com.wormhole_xtreme.wormhole.libs.bstats}, so it never
 * meets another plugin's copy.
 */
public final class MetricsSupport
{
    /** This plugin's id on bstats.org. */
    static final int BSTATS_ID = 34269;

    /** The portal materials a chart names; any other is {@code other}, so a custom shape cannot add a bar. */
    private static final Set<String> PORTAL_MATERIALS =
        Set.of("water", "lava", "nether_portal", "end_gateway", "end_portal");

    /** What a chart counts a gate under when only loading its chunk would tell. */
    private static final String UNKNOWN = "unknown";

    /** The plugins this one has an optional integration with, as they name themselves. */
    private static final List<String> INTEGRATIONS = List.of("Vault", "LuckPerms", "PlaceholderAPI",
        "CoreProtect", "WorldGuard", "dynmap", "BlueMap", "squaremap", "Pl3xMap");

    private static Metrics metrics;

    private MetricsSupport()
    {
    }

    /**
     * Starts sending if {@code metrics-enabled} allows it.
     */
    public static void enableIfConfigured(final JavaPlugin plugin)
    {
        if (ConfigManager.isMetricsEnabled())
        {
            enableMetrics(plugin);
        }
    }

    /**
     * Starts sending, unless it already is.
     */
    public static synchronized void enableMetrics(final JavaPlugin plugin)
    {
        if (metrics != null)
        {
            return;
        }
        final Metrics started = new Metrics(plugin, BSTATS_ID);
        started.addCustomChart(new SimplePie("gates", () -> range(StargateManager.getAllGatesUnsorted().size())));
        started.addCustomChart(new SimplePie("ring_pairs", () -> range(RingManager.getAllPairs().size())));
        started.addCustomChart(new SimplePie("beam_destinations", () -> range(beamDestinations())));
        started.addCustomChart(new SimplePie("mirrors", () -> range(MirrorManager.all().size())));
        started.addCustomChart(new SimplePie("gate_dial_spin",
            () -> ConfigManager.getGateDialSpinPattern().name().toLowerCase(Locale.ROOT)));
        started.addCustomChart(new SimplePie("gate_view", () -> ConfigManager.getGateView().toLowerCase(Locale.ROOT)));
        started.addCustomChart(new SimplePie("gate_ripple", () -> onOff(ConfigManager.isGateRipple())));
        started.addCustomChart(new SimplePie("gate_sounds", () -> onOff(ConfigManager.isGateSoundsEnabled())));
        started.addCustomChart(new SimplePie("gate_iris_animation",
            () -> irisAnimation(ConfigManager.getGateIrisAnimation(null, null))));
        started.addCustomChart(new AdvancedPie("gate_shapes",
            () -> shapeCounts(StargateManager.getAllGatesUnsorted())));
        started.addCustomChart(new AdvancedPie("portal_material",
            () -> portalMaterialCounts(StargateManager.getAllGatesUnsorted())));
        started.addCustomChart(new AdvancedPie("gate_material",
            () -> frameMaterialCounts(StargateManager.getAllGatesUnsorted())));
        started.addCustomChart(new AdvancedPie("integrations",
            () -> integrations(name -> Bukkit.getPluginManager().isPluginEnabled(name))));
        metrics = started;
        plugin.getLogger().info("Sending anonymous usage counts to bStats; metrics-enabled: false stops it.");
    }

    /** Stops sending, on disable. */
    public static synchronized void disableMetrics()
    {
        final Metrics running = metrics;
        metrics = null;
        if (running != null)
        {
            running.shutdown();
            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, "Stopped sending usage counts to bStats.");
        }
    }

    private static String onOff(final boolean on)
    {
        return on ? "on" : "off";
    }

    /**
     * The iris animation as a chart names it: one of the five there are, so a value written into
     * the config by hand is {@code other} rather than a bar of its own.
     */
    static String irisAnimation(final String animation)
    {
        final String name = String.valueOf(animation).toLowerCase(Locale.ROOT);
        return ConfigManager.irisAnimations().contains(name) ? name : "other";
    }

    /**
     * How many gates there are of each shape: a shape that ships with the plugin by its name,
     * every other, which a server's own file can name anything, as {@code custom}.
     *
     * @return shape to count, spelled as the plugin ships it; empty with no gates
     */
    static Map<String, Integer> shapeCounts(final Collection<Stargate> gates)
    {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        for (final Stargate gate : gates)
        {
            final String shape = (gate.getGateShape() == null) ? null : gate.getGateShape().getShapeName();
            final String shipped = StargateShapeRegistry.shippedName(shape);
            counts.merge((shipped != null) ? shipped : "custom", 1, Integer::sum);
        }
        return counts;
    }

    /**
     * How many gates show each portal material: the five a gate is likely to use by name, any
     * other as {@code other}, and a gate whose material only a chunk load would tell as
     * {@code unknown}, since this runs on the server thread and must not load one. Counted by gate,
     * so one server of many does not stand for them all.
     *
     * @return material to count; empty with no gates
     */
    static Map<String, Integer> portalMaterialCounts(final Collection<Stargate> gates)
    {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        for (final Stargate gate : gates)
        {
            final Material portal = gate.getEffectivePortalMaterialIfLoaded();
            final String material = (portal == null) ? UNKNOWN : portal.name().toLowerCase(Locale.ROOT);
            counts.merge((UNKNOWN.equals(material) || PORTAL_MATERIALS.contains(material)) ? material : "other",
                1, Integer::sum);
        }
        return counts;
    }

    /**
     * How many gates are built from each frame material, by the block's name: whatever a server's
     * material groups are called, the block under them is one of the game's own. A gate whose frame
     * only a chunk load would tell is {@code unknown}, since this runs on the server thread and must
     * not load one.
     *
     * @return material to count; empty with no gates
     */
    static Map<String, Integer> frameMaterialCounts(final Collection<Stargate> gates)
    {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        for (final Stargate gate : gates)
        {
            final Material frame = gate.getEffectiveStructureMaterialIfLoaded();
            counts.merge((frame == null) ? UNKNOWN : frame.name().toLowerCase(Locale.ROOT), 1, Integer::sum);
        }
        return counts;
    }

    /**
     * Which of the plugins this one integrates with the server has, each counted once; {@code none}
     * when it has none, since a chart with no entries is not sent at all.
     *
     * @param present
     *            whether a plugin of that name is enabled
     * @return plugin to 1
     */
    static Map<String, Integer> integrations(final Predicate<String> present)
    {
        final Map<String, Integer> found = new LinkedHashMap<>();
        for (final String name : INTEGRATIONS)
        {
            if (present.test(name))
            {
                found.put(name, 1);
            }
        }
        if (found.isEmpty())
        {
            found.put("none", 1);
        }
        return found;
    }

    /** Public beam destinations and every player's own places. */
    private static int beamDestinations()
    {
        return BeamManager.getAllPublicDestinations().size()
            + BeamManager.getAllPlaces().values().stream().mapToInt(Map::size).sum();
    }

    /**
     * A count as a range, so a chart groups servers of a size rather than listing every number.
     *
     * @param count
     *            how many
     * @return 0, 1-5, 6-20, 21-50, 51-200 or 200+
     */
    static String range(final int count)
    {
        if (count <= 0)
        {
            return "0";
        }
        if (count <= 5)
        {
            return "1-5";
        }
        if (count <= 20)
        {
            return "6-20";
        }
        if (count <= 50)
        {
            return "21-50";
        }
        return (count <= 200) ? "51-200" : "200+";
    }
}
