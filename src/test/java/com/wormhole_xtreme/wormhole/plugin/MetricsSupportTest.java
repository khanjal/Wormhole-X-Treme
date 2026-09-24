package com.wormhole_xtreme.wormhole.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * bStats (#239): how counts are sent.
 *
 * <p>Counts go out as ranges, so a chart groups servers by size and no server's exact numbers
 * are sent.
 */
class MetricsSupportTest
{
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
        com.wormhole_xtreme.wormhole.PluginTestSupport.install(org.mockito.Mockito.mock(com.wormhole_xtreme.wormhole.WormholeXTreme.class));
        final org.bukkit.plugin.java.JavaPlugin plugin = org.mockito.Mockito.mock(org.bukkit.plugin.java.JavaPlugin.class);
        org.mockito.Mockito.when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        final java.util.List<org.bstats.charts.CustomChart> charts = new java.util.ArrayList<>();
        try (org.mockito.MockedConstruction<org.bstats.bukkit.Metrics> made = org.mockito.Mockito.mockConstruction(
            org.bstats.bukkit.Metrics.class, (m, context) -> org.mockito.Mockito.doAnswer(call -> charts.add(call.getArgument(0)))
                .when(m).addCustomChart(org.mockito.ArgumentMatchers.any())))
        {
            MetricsSupport.enableMetrics(plugin);
            MetricsSupport.enableMetrics(plugin);
            assertEquals(1, made.constructed().size(), "a second start while running starts nothing");

            final java.util.List<String> ids = new java.util.ArrayList<>();
            for (final org.bstats.charts.CustomChart chart : charts)
            {
                final Object json = chart.getRequestJsonObject((message, error) -> { throw new AssertionError(message, error); }, true);
                org.junit.jupiter.api.Assertions.assertNotNull(json, "every chart answers");
                ids.add(json.toString().replaceAll(".*\"chartId\":\"([a-z_]+)\".*", "$1"));
            }
            assertEquals(java.util.List.of("gates", "ring_pairs", "beam_destinations", "mirrors", "gate_dial_spin"), ids,
                "the ids the charts on bstats.org were made with");

            MetricsSupport.disableMetrics();
            MetricsSupport.disableMetrics();
            org.mockito.Mockito.verify(made.constructed().get(0), org.mockito.Mockito.times(1)).shutdown();

            MetricsSupport.enableMetrics(plugin);
            assertEquals(2, made.constructed().size(), "stopped, it starts afresh");
            MetricsSupport.disableMetrics();
        }
        finally
        {
            com.wormhole_xtreme.wormhole.PluginTestSupport.remove();
        }
    }

    /** Asked to start while metrics-enabled is false, it does not; true, it does. */
    @Test
    void startsOnlyWhenTheSwitchAllowsIt() throws Exception
    {
        com.wormhole_xtreme.wormhole.PluginTestSupport.install(org.mockito.Mockito.mock(com.wormhole_xtreme.wormhole.WormholeXTreme.class));
        final org.bukkit.plugin.java.JavaPlugin plugin = org.mockito.Mockito.mock(org.bukkit.plugin.java.JavaPlugin.class);
        org.mockito.Mockito.when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        com.wormhole_xtreme.wormhole.config.ConfigTestSupport.loadDefaults();
        try (org.mockito.MockedConstruction<org.bstats.bukkit.Metrics> made =
            org.mockito.Mockito.mockConstruction(org.bstats.bukkit.Metrics.class))
        {
            com.wormhole_xtreme.wormhole.config.ConfigTestSupport.set(
                com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys.METRICS_ENABLED, false);
            MetricsSupport.enableIfConfigured(plugin);
            assertEquals(0, made.constructed().size());

            com.wormhole_xtreme.wormhole.config.ConfigTestSupport.set(
                com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys.METRICS_ENABLED, true);
            MetricsSupport.enableIfConfigured(plugin);
            assertEquals(1, made.constructed().size());
            MetricsSupport.disableMetrics();
        }
        finally
        {
            com.wormhole_xtreme.wormhole.config.ConfigTestSupport.clear();
            com.wormhole_xtreme.wormhole.PluginTestSupport.remove();
        }
    }
}
