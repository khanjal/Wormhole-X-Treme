package com.wormhole_xtreme.wormhole.integration;

import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;

/**
 * Stands in for WorldGuard's regions, with {@code worldguard-enabled} on as it must be for them to count.
 *
 * <p>Every check reads the setting, so a test installing a region check without it would be
 * asking regions that are switched off and pass whatever the code did.
 */
public final class RegionFlagsTestSupport
{
    /** Static helpers only. */
    private RegionFlagsTestSupport()
    {
    }

    /**
     * Installs a region check and turns the setting on.
     *
     * @param check
     *            what the regions answer
     */
    public static void install(final RegionFlags.Check check)
    {
        ConfigTestSupport.set(ConfigKeys.WORLDGUARD_ENABLED, true);
        RegionFlags.setCheckForTest(check);
    }

    /** Takes the region check away and turns the setting off again. */
    public static void remove()
    {
        RegionFlags.setCheckForTest(null);
        ConfigTestSupport.set(ConfigKeys.WORLDGUARD_ENABLED, false);
    }
}
