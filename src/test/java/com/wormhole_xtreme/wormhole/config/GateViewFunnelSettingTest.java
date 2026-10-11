package com.wormhole_xtreme.wormhole.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;

/**
 * {@code gate-view-funnel} narrows a big gate's view over 8 blocks, read so from a {@code config.yml}
 * written before it existed, and 0 turns it off (#583).
 */
class GateViewFunnelSettingTest
{
    @AfterEach
    void forgetIt()
    {
        ConfigTestSupport.clear();
    }

    @Test
    void itShipsAtEightBlocks()
    {
        final Setting shipped = Arrays.stream(DefaultSettings.config).filter(s -> s.getName() == ConfigKeys.GATE_VIEW_FUNNEL)
            .findFirst().orElseThrow();
        assertEquals(8, shipped.getValue());
    }

    @Test
    void aConfigWrittenBeforeItExistedReadsAsEight()
    {
        assertEquals(8, ConfigManager.getGateViewFunnel());
    }

    @Test
    void zeroTurnsItOffAndItIsKeptBetweenNothingAndSixtyFour()
    {
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW_FUNNEL, 0);
        assertEquals(0, ConfigManager.getGateViewFunnel(), "the whole view, not narrowed");
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW_FUNNEL, -3);
        assertEquals(0, ConfigManager.getGateViewFunnel(), "below nothing is off too");
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW_FUNNEL, 1);
        assertEquals(1, ConfigManager.getGateViewFunnel(), "one block: a step straight to the far gate's size");
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW_FUNNEL, 64);
        assertEquals(64, ConfigManager.getGateViewFunnel());
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW_FUNNEL, 500);
        assertEquals(64, ConfigManager.getGateViewFunnel(), "no further than 64");
    }
}
