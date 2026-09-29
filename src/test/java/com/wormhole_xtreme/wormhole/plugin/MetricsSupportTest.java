package com.wormhole_xtreme.wormhole.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bstats.bukkit.Metrics;
import org.bstats.charts.CustomChart;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;

/**
 * bStats (#239): how counts are sent.
 *
 * <p>Counts go out as ranges, so a chart groups servers by size and no server's exact numbers
 * are sent.
 */
class MetricsSupportTest
{
    private static final Pattern CHART_ID =
        Pattern.compile("\"chartId\":\"([a-z_]+)\"");

    /** Each range starts where the last ends, with none skipped and none overlapping. */
    @Test
    void countsAreSentAsRanges()
    {
        assertEquals("0", MetricsSupport.range(0));
        assertEquals("1-5", MetricsSupport.range(1));
        assertEquals("1-5", MetricsSupport.range(5));
        assertEquals("6-20", MetricsSupport.range(6));
        assertEquals("6-20", MetricsSupport.range(20));
        assertEquals("21-50", MetricsSupport.range(21));
        assertEquals("21-50", MetricsSupport.range(50));
        assertEquals("51-200", MetricsSupport.range(51));
        assertEquals("51-200", MetricsSupport.range(200));
        assertEquals("200+", MetricsSupport.range(201));
    }

    /**
     * Started once however often it is asked, with the five charts, each of which answers; stopped
     * once, and started afresh after, as /wormhole config metrics-enabled does.
     */
    @Test
    void startsOnceWithItsChartsAndStopsOnce() throws Exception
    {
        PluginTestSupport.install(Mockito.mock(WormholeXTreme.class));
        final JavaPlugin plugin = Mockito.mock(JavaPlugin.class);
        Mockito.when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        final List<CustomChart> charts = new ArrayList<>();
        try (MockedConstruction<Metrics> made = Mockito.mockConstruction(
            Metrics.class, (m, context) -> Mockito.doAnswer(call -> charts.add(call.getArgument(0)))
                .when(m).addCustomChart(ArgumentMatchers.any())))
        {
            MetricsSupport.enableMetrics(plugin);
            MetricsSupport.enableMetrics(plugin);
            assertEquals(1, made.constructed().size(), "a second start while running starts nothing");

            final List<String> ids = new ArrayList<>();
            for (final CustomChart chart : charts)
            {
                final Object json = chart.getRequestJsonObject((message, error) -> { throw new AssertionError(message, error); }, true);
                Assertions.assertNotNull(json, "every chart answers");
                final Matcher id = CHART_ID.matcher(json.toString());
                Assertions.assertTrue(id.find(), json.toString());
                ids.add(id.group(1));
            }
            assertEquals(List.of("gates", "ring_pairs", "beam_destinations", "mirrors", "gate_dial_spin"), ids,
                "the ids the charts on bstats.org were made with");

            MetricsSupport.disableMetrics();
            MetricsSupport.disableMetrics();
            Mockito.verify(made.constructed().get(0), Mockito.times(1)).shutdown();

            MetricsSupport.enableMetrics(plugin);
            assertEquals(2, made.constructed().size(), "stopped, it starts afresh");
            MetricsSupport.disableMetrics();
        }
        finally
        {
            PluginTestSupport.remove();
        }
    }

    /** Asked to start while metrics-enabled is false, it does not; true, it does. */
    @Test
    void startsOnlyWhenTheSwitchAllowsIt() throws Exception
    {
        PluginTestSupport.install(Mockito.mock(WormholeXTreme.class));
        final JavaPlugin plugin = Mockito.mock(JavaPlugin.class);
        Mockito.when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        ConfigTestSupport.loadDefaults();
        try (MockedConstruction<Metrics> made =
            Mockito.mockConstruction(Metrics.class))
        {
            ConfigTestSupport.set(
                ConfigManager.ConfigKeys.METRICS_ENABLED, false);
            MetricsSupport.enableIfConfigured(plugin);
            assertEquals(0, made.constructed().size());

            ConfigTestSupport.set(
                ConfigManager.ConfigKeys.METRICS_ENABLED, true);
            MetricsSupport.enableIfConfigured(plugin);
            assertEquals(1, made.constructed().size());
            MetricsSupport.disableMetrics();
        }
        finally
        {
            ConfigTestSupport.clear();
            PluginTestSupport.remove();
        }
    }
}
