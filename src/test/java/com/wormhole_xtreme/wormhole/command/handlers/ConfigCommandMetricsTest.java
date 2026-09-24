package com.wormhole_xtreme.wormhole.command.handlers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.plugin.MetricsSupport;

/**
 * {@code /wormhole config metrics-enabled} stops or starts bStats at once (#239), as the command
 * promises every setting does; metrics is otherwise only read at startup.
 */
class ConfigCommandMetricsTest
{
    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        ConfigTestSupport.loadDefaults();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    /** Turning it off stops sending now; turning it on starts it; any other setting touches neither. */
    @Test
    void settingTheSwitchStartsOrStopsMetrics()
    {
        try (MockedStatic<MetricsSupport> metrics = mockStatic(MetricsSupport.class))
        {
            new ConfigCommand().execute(mock(org.bukkit.command.ConsoleCommandSender.class),
                new String[] { "config", "metrics-enabled", "false" });
            metrics.verify(MetricsSupport::disableMetrics);
            metrics.verify(() -> MetricsSupport.enableMetrics(any()), never());

            new ConfigCommand().execute(mock(org.bukkit.command.ConsoleCommandSender.class),
                new String[] { "config", "METRICS_ENABLED", "true" });
            metrics.verify(() -> MetricsSupport.enableMetrics(any()));

            metrics.clearInvocations();
            new ConfigCommand().execute(mock(org.bukkit.command.ConsoleCommandSender.class),
                new String[] { "config", "placeholders-enabled", "false" });
            metrics.verifyNoInteractions();
        }
    }
}
