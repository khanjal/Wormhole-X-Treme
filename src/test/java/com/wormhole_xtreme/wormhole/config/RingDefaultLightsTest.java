package com.wormhole_xtreme.wormhole.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;

/**
 * What a ring lights up as when nobody has chosen: a lit redstone lamp pad and a glowstone flash.
 *
 * <p>Both used to default to redstone lamp, so the transport flash was lost in the pad light, and
 * a flash setting that named no block fell back to whatever the pad was.
 */
class RingDefaultLightsTest
{
    @AfterEach
    void clearSettings()
    {
        ConfigTestSupport.clear();
    }

    @Test
    void aFreshServerLightsThePadAsARedstoneLampAndTheFlashAsGlowstone()
    {
        ConfigTestSupport.loadDefaults();

        assertEquals(Material.REDSTONE_LAMP, ConfigManager.getRingDefaultLight());
        assertEquals(Material.GLOWSTONE, ConfigManager.getRingDefaultFlash());
    }

    @Test
    void aFlashSettingThatNamesNoBlockFallsBackToGlowstoneRatherThanThePadLight()
    {
        ConfigTestSupport.set(ConfigKeys.RING_DEFAULT_LIGHT, "SEA_LANTERN");
        ConfigTestSupport.set(ConfigKeys.RING_DEFAULT_FLASH, "banana");

        assertEquals(Material.GLOWSTONE, ConfigManager.getRingDefaultFlash());
    }
}
