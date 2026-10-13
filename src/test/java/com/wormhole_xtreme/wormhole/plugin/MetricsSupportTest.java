package com.wormhole_xtreme.wormhole.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.bstats.bukkit.Metrics;
import org.bstats.charts.CustomChart;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateShape;

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
     * Started once however often it is asked, with its charts, each of which answers (a pie of gates is not sent with no gate to count); stopped
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
                .when(m).addCustomChart(ArgumentMatchers.any()));
            MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class))
        {
            bukkit.when(Bukkit::getPluginManager).thenReturn(Mockito.mock(PluginManager.class));
            MetricsSupport.enableMetrics(plugin);
            MetricsSupport.enableMetrics(plugin);
            assertEquals(1, made.constructed().size(), "a second start while running starts nothing");

            final List<String> ids = new ArrayList<>();
            for (final CustomChart chart : charts)
            {
                final Object json = chart.getRequestJsonObject((message, error) -> { throw new AssertionError(message, error); }, true);
                final String id = chartId(chart);
                ids.add(id);
                if (!List.of("gate_shapes", "portal_material").contains(id))
                {
                    Assertions.assertNotNull(json, id + " answers");
                    Assertions.assertTrue(CHART_ID.matcher(json.toString()).find(), json.toString());
                }
            }
            assertEquals(List.of("gates", "ring_pairs", "beam_destinations", "mirrors", "gate_dial_spin",
                "gate_view", "gate_ripple", "gate_sounds", "gate_iris_animation", "gate_shapes",
                "portal_material", "integrations"), ids,
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

    private static String chartId(final CustomChart chart) throws Exception
    {
        final java.lang.reflect.Field field = CustomChart.class.getDeclaredField("chartId");
        field.setAccessible(true);
        return (String) field.get(chart);
    }

    private static Stargate gate(final String shape, final Material portal)
    {
        final StargateShape gateShape = Mockito.mock(StargateShape.class);
        Mockito.when(gateShape.getShapeName()).thenReturn(shape);
        final Stargate gate = Mockito.mock(Stargate.class);
        Mockito.when(gate.getGateShape()).thenReturn(gateShape);
        Mockito.when(gate.getEffectivePortalMaterial()).thenReturn(portal);
        return gate;
    }

    /**
     * A shipped shape is counted by its name, whatever the capitals, and every other as custom:
     * a server's own shape file can be called anything, and a chart must not grow a bar for each.
     */
    @Test
    void shapesAreCountedByNameWhenTheyShipAndAsCustomWhenTheyDoNot()
    {
        final Map<String, Integer> counts = MetricsSupport.shapeCounts(List.of(
            gate("Standard", Material.WATER), gate("standard", Material.WATER),
            gate("Minimal", Material.WATER), gate("Steve's Hideout", Material.WATER),
            gate("Weird", Material.WATER)));
        assertEquals(Map.of("Standard", 1, "standard", 1, "Minimal", 1, "custom", 2), counts);
    }

    /** A gate with no shape is custom rather than a crash. */
    @Test
    void aGateWithNoShapeIsCustom()
    {
        final Stargate gate = Mockito.mock(Stargate.class);
        assertEquals(Map.of("custom", 1), MetricsSupport.shapeCounts(List.of(gate)));
    }

    /** Water is counted as water, the others named, and any other block as other. */
    @Test
    void portalMaterialsAreCountedPerGateWithTheUncommonOnesAsOther()
    {
        final Map<String, Integer> counts = MetricsSupport.portalMaterialCounts(List.of(
            gate("Standard", Material.WATER), gate("Standard", Material.WATER),
            gate("Standard", Material.LAVA), gate("Standard", Material.NETHER_PORTAL),
            gate("Standard", Material.END_GATEWAY), gate("Standard", Material.END_PORTAL),
            gate("Standard", Material.GLASS)));
        assertEquals(Map.of("water", 2, "lava", 1, "nether_portal", 1, "end_gateway", 1,
            "end_portal", 1, "other", 1), counts);
    }

    /** With no gates there is nothing to count, which the chart leaves unsent. */
    @Test
    void noGatesCountNothing()
    {
        Assertions.assertTrue(MetricsSupport.shapeCounts(List.of()).isEmpty());
        Assertions.assertTrue(MetricsSupport.portalMaterialCounts(List.of()).isEmpty());
    }

    /** Only the plugins that are there are named, and a server with none says none. */
    @Test
    void integrationsNameThePluginsThatArePresentOrNone()
    {
        assertEquals(Map.of("Vault", 1, "dynmap", 1),
            MetricsSupport.integrations(name -> "Vault".equals(name) || "dynmap".equals(name)));
        assertEquals(Map.of("none", 1), MetricsSupport.integrations(name -> false));
    }

    /** A value written in by hand is other, not a bar of its own. */
    @Test
    void anIrisAnimationNobodyShipsIsOther()
    {
        assertEquals("spiral", MetricsSupport.irisAnimation("Spiral"));
        assertEquals("instant", MetricsSupport.irisAnimation("instant"));
        assertEquals("other", MetricsSupport.irisAnimation("wibble"));
        assertEquals("other", MetricsSupport.irisAnimation(null));
    }
}
