package com.wormhole_xtreme.wormhole.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;

/**
 * {@code gate-view-funnel} ships on, and reads as on from a {@code config.yml} written before it
 * existed; {@code gate-view-funnel-length}, the longest tunnel, ships at 16 (#583).
 *
 * <p>Sixteen so no shipped pair is cut short: Grand to Minimal, the longest, is sixteen.
 */
class GateViewFunnelSettingTest
{
    @AfterEach
    void forgetIt()
    {
        ConfigTestSupport.clear();
    }

    private static Object shipped(final ConfigKeys key)
    {
        return Arrays.stream(DefaultSettings.config).filter(s -> s.getName() == key).findFirst().orElseThrow().getValue();
    }

    @Test
    void itShipsOnWithTunnelsUpToSixteenBlocks()
    {
        assertEquals(Boolean.TRUE, shipped(ConfigKeys.GATE_VIEW_FUNNEL));
        assertEquals(16, shipped(ConfigKeys.GATE_VIEW_FUNNEL_LENGTH));
    }

    @Test
    void aConfigWrittenBeforeItExistedReadsAsOnAtSixteen()
    {
        assertTrue(ConfigManager.isGateViewFunnel());
        assertEquals(16, ConfigManager.getGateViewFunnelLength());
        assertEquals(16, ConfigManager.getGateViewFunnelLongest());
    }

    @Test
    void turnedOffNoTunnelIsAllowedWhateverItsLength()
    {
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW_FUNNEL, false);
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW_FUNNEL_LENGTH, 20);

        assertFalse(ConfigManager.isGateViewFunnel());
        assertEquals(20, ConfigManager.getGateViewFunnelLength(), "kept for when it is turned on again");
        assertEquals(0, ConfigManager.getGateViewFunnelLongest(), "the plain view");
    }

    @Test
    void theLengthIsKeptBetweenOneAndSixtyFour()
    {
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW_FUNNEL_LENGTH, 0);
        assertEquals(1, ConfigManager.getGateViewFunnelLength(), "at least the opening itself: off is the switch's job");
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW_FUNNEL_LENGTH, 1);
        assertEquals(1, ConfigManager.getGateViewFunnelLength());
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW_FUNNEL_LENGTH, 64);
        assertEquals(64, ConfigManager.getGateViewFunnelLength());
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW_FUNNEL_LENGTH, 500);
        assertEquals(64, ConfigManager.getGateViewFunnelLength(), "no longer than 64");
    }
}
