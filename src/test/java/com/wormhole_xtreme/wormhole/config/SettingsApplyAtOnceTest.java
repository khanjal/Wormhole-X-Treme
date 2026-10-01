package com.wormhole_xtreme.wormhole.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.model.StargateShapeRegistry;
import com.wormhole_xtreme.wormhole.model.ring.Ring;
import com.wormhole_xtreme.wormhole.model.ring.RingIndex;
import com.wormhole_xtreme.wormhole.model.ring.RingManager;
import com.wormhole_xtreme.wormhole.model.ring.RingOrientation;
import com.wormhole_xtreme.wormhole.model.ring.RingPair;
import com.wormhole_xtreme.wormhole.model.ring.RingPattern;
import com.wormhole_xtreme.wormhole.plugin.EconomySupport;
import com.wormhole_xtreme.wormhole.plugin.PlaceholderSupport;

/**
 * Settings something reads once rather than where it is used, changed with {@code /wormhole config},
 * take effect at once rather than at the next restart, as the guide says every setting does.
 *
 * <p>The research facility's stage 6 found the hum's interval waiting for a restart; the same was
 * true of these, each read once at enable or at load.
 */
class SettingsApplyAtOnceTest
{
    private static final String WORLD = "world";

    @TempDir
    File dataFolder;

    @BeforeEach
    void setUp() throws Exception
    {
        final WormholeXTreme plugin = PluginTestSupport.install();
        // Applying a setting saves config.yml; without a data folder that is ./plugins.
        when(plugin.getDataFolder()).thenReturn(dataFolder);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        ConfigTestSupport.clear();
        RingManager.clear();
        PlaceholderSupport.setRegistrarForTest(null);
        PlaceholderSupport.reset();
        PluginTestSupport.remove();
    }

    /** Turned on in-game, the economy is attached now; turned off, let go of. */
    @Test
    void theEconomyIsAttachedAndLetGoOfAsTheSettingChanges()
    {
        ConfigTestSupport.set(ConfigKeys.ECONOMY_ENABLED, false);
        try (MockedStatic<EconomySupport> economy = mockStatic(EconomySupport.class))
        {
            ConfigManager.applySetting("economy-enabled", "true");
            economy.verify(EconomySupport::enableEconomy);
            economy.verify(EconomySupport::disableEconomy, never());

            ConfigManager.applySetting("economy-enabled", "false");
            economy.verify(EconomySupport::disableEconomy);
        }
    }

    /** Turned on in-game, the PlaceholderAPI expansion is registered now. */
    @Test
    void placeholdersTurnedOnInGameAreRegisteredAtOnce()
    {
        ConfigTestSupport.set(ConfigKeys.PLACEHOLDERS_ENABLED, false);
        final AtomicInteger registered = new AtomicInteger();
        PlaceholderSupport.setRegistrarForTest(() -> {
            registered.incrementAndGet();
            return true;
        });

        ConfigManager.applySetting("placeholders-enabled", "true");

        assertEquals(1, registered.get(), "registered once, without a restart");
    }

    /**
     * A deeper ring-reach arms the deeper volume at once.
     *
     * <p>Every ring's trigger volume is indexed when it loads, so the new reach used to reach only
     * rings built after it, and a ring removed later was taken out of the index at the new depth,
     * leaving the rest of its old volume armed for a pair that was gone.
     */
    @Test
    void aDeeperRingReachArmsTheDeeperVolumeAtOnce()
    {
        ConfigTestSupport.set(ConfigKeys.RING_REACH, 2);
        final Ring a = new Ring(100, 64, 100, RingPattern.ODD, RingOrientation.FLOOR, Material.STONE_SLAB,
            Material.GLOWSTONE);
        final Ring b = new Ring(200, 64, 200, RingPattern.ODD, RingOrientation.FLOOR, Material.STONE_SLAB,
            Material.GLOWSTONE);
        RingManager.addPair(new RingPair("pair", WORLD, a, b), ConfigManager.getRingReach());
        assertNull(RingIndex.volumeAt(WORLD, 100, 68, 100), "four layers up is past a reach of 2");

        ConfigManager.applySetting("ring-reach", "5");

        assertNotNull(RingIndex.volumeAt(WORLD, 100, 68, 100), "and inside a reach of 5");
    }

    /** Turned on in-game, material groups are looked for now rather than at the next shape load. */
    @Test
    void autodiscoveryTurnedOnInGameLooksForGroupsAtOnce()
    {
        ConfigTestSupport.set(ConfigKeys.GATE_MATERIAL_GROUPS_AUTODISCOVER, false);
        try (MockedStatic<StargateShapeRegistry> shapes = mockStatic(StargateShapeRegistry.class))
        {
            ConfigManager.applySetting("gate-material-groups-autodiscover", "true");

            shapes.verify(StargateShapeRegistry::followAutodiscover);
        }
    }
}
