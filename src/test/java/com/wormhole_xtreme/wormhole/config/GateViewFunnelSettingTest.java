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
 * existed; {@code gate-view-funnel-length}, the longest tunnel, ships at 16 (#583); and
 * {@code gate-view-funnel-min-length}, the shortest, at 0 (#587).
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

    /**
     * {@code gate-view-funnel-min-length} ships at 0, and reads as 0 from a file written before it
     * existed: same-size and small-to-large pairs keep the plain view until an admin sets it (#587).
     */
    @Test
    void theLeastLengthShipsAtNoneAndReadsAsNoneWhenMissing()
    {
        assertEquals(0, shipped(ConfigKeys.GATE_VIEW_FUNNEL_MIN_LENGTH));
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW_FUNNEL_MIN_LENGTH, 3);
        assertEquals(3, ConfigManager.getGateViewFunnelMinLength(), "as set");
        ConfigTestSupport.clear();
        assertEquals(0, ConfigManager.getGateViewFunnelMinLength(), "missing: none");
    }

    /** The least length is never below 0 nor past the longest, so a tunnel is never asked to be both (#587). */
    @Test
    void theLeastLengthIsKeptBetweenNoneAndTheLongest()
    {
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW_FUNNEL_LENGTH, 12);
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW_FUNNEL_MIN_LENGTH, 12);
        assertEquals(12, ConfigManager.getGateViewFunnelMinLength(), "as long as the longest");
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW_FUNNEL_MIN_LENGTH, 20);
        assertEquals(12, ConfigManager.getGateViewFunnelMinLength(), "no longer than gate-view-funnel-length");
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW_FUNNEL_MIN_LENGTH, -4);
        assertEquals(0, ConfigManager.getGateViewFunnelMinLength(), "never below none");
    }
}
