package com.wormhole_xtreme.wormhole.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;

/**
 * The one depth that replaced gate-view-depth, gate-view-full-depth and mirror-view-depth (#589).
 *
 * <p>On the defaults nothing may change: a mirror drawn to 160, a gate's first step 32 and its fill
 * 160. A gate's first step is the shallower of 32 and the view, so a view lowered under it is never
 * outrun by its own first step.
 */
class ViewDepthSettingTest
{
    @AfterEach
    void forgetIt()
    {
        ConfigTestSupport.clear();
    }

    /** As shipped, and missing, the depths are what the three settings defaulted to. */
    @Test
    void theShippedDepthGivesBackTheOldDefaults()
    {
        assertEquals(160, ConfigManager.getViewDepth(), "missing: a mirror's room and a gate's fill");
        assertEquals(32, ConfigManager.getGateViewFirstDepth(), "missing: a gate's first step");

        ConfigTestSupport.loadDefaults();

        assertEquals(160, ConfigManager.getViewDepth(), "as shipped");
        assertEquals(32, ConfigManager.getGateViewFirstDepth(), "as shipped");
    }

    /** A view deeper than the first step leaves the step alone; a shallower one is the step too. */
    @Test
    void theFirstStepIsThirtyTwoOrTheViewIfShallower()
    {
        ConfigTestSupport.set(ConfigKeys.VIEW_DEPTH, 64);
        assertEquals(32, ConfigManager.getGateViewFirstDepth());

        ConfigTestSupport.set(ConfigKeys.VIEW_DEPTH, 20);
        assertEquals(20, ConfigManager.getGateViewFirstDepth(), "never deeper than the view it starts");
        assertEquals(20, ConfigManager.getViewDepth());
    }

    /** Past 160 is read as 160, and under 4 as 4, as each of the three was. */
    @Test
    void theDepthIsReadFromFourTo160()
    {
        ConfigTestSupport.set(ConfigKeys.VIEW_DEPTH, 400);
        assertEquals(160, ConfigManager.getViewDepth());

        ConfigTestSupport.set(ConfigKeys.VIEW_DEPTH, 1);
        assertEquals(4, ConfigManager.getViewDepth());
        assertEquals(4, ConfigManager.getGateViewFirstDepth());
    }
}
