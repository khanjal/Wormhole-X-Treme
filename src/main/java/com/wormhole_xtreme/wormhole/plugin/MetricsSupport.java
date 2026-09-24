package com.wormhole_xtreme.wormhole.plugin;

import java.util.Locale;
import java.util.logging.Level;

import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.plugin.java.JavaPlugin;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.StargateManager;
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

    private static Metrics metrics;

    private MetricsSupport()
    {
    }

    /**
     * Starts sending if {@code metrics-enabled} allows it.
     *
     * @param plugin
     *            this plugin
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
     *
     * @param plugin
     *            this plugin
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

    /** Public beam destinations and every player's own places. */
    private static int beamDestinations()
    {
        return BeamManager.getAllPublicDestinations().size()
            + BeamManager.getAllPlaces().values().stream().mapToInt(java.util.Map::size).sum();
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
