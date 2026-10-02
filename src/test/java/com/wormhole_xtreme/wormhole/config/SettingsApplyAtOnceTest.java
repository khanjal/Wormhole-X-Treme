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
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import com.wormhole_xtreme.wormhole.model.MaterialGroupRegistry;
import com.wormhole_xtreme.wormhole.model.StargateShape;
import com.wormhole_xtreme.wormhole.plugin.PermissionsSupport;

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

    /** Turned on in-game, a palette a loaded shape implies is added now, not at the next shape load. */
    @Test
    void autodiscoveryTurnedOnInGameAddsTheGroupAShapeImpliesAtOnce()
    {
        MaterialGroupRegistry.load(Map.of("Standard", Map.of("structure", "OBSIDIAN")));
        final StargateShape diamond = new StargateShape();
        diamond.setShapeStructureMaterial(Material.DIAMOND_BLOCK);
        diamond.setShapeIrisMaterial(Material.GLASS);
        diamond.setShapeLightMaterial(Material.GOLD_BLOCK);
        StargateShapeRegistry.getStargateShapes().put("DiamondTest", diamond);
        ConfigTestSupport.set(ConfigKeys.GATE_MATERIAL_GROUPS_AUTODISCOVER, false);
        try
        {
            assertNull(MaterialGroupRegistry.getGroupByStructureMaterial(Material.DIAMOND_BLOCK), "not yet");

            ConfigManager.applySetting("gate-material-groups-autodiscover", "true");

            assertNotNull(MaterialGroupRegistry.getGroupByStructureMaterial(Material.DIAMOND_BLOCK),
                "the diamond palette, added without a shape reload");
        }
        finally
        {
            StargateShapeRegistry.getStargateShapes().remove("DiamondTest");
            MaterialGroupRegistry.load(null);
        }
    }

    /** A deeper ring-max-ceiling-drop arms a ceiling ring's volume further down at once. */
    @Test
    void aDeeperCeilingDropArmsTheDeeperVolumeAtOnce()
    {
        ConfigTestSupport.set(ConfigKeys.RING_MAX_CEILING_DROP, 10);
        final Ring a = new Ring(100, 64, 100, RingPattern.ODD, RingOrientation.CEILING, Material.STONE_SLAB,
            Material.GLOWSTONE);
        final Ring b = new Ring(200, 64, 200, RingPattern.ODD, RingOrientation.CEILING, Material.STONE_SLAB,
            Material.GLOWSTONE);
        RingManager.addPair(new RingPair("pair", WORLD, a, b), ConfigManager.getRingReach());
        assertNull(RingIndex.volumeAt(WORLD, 100, 64 - 15, 100), "fifteen down is past a drop of 10");

        ConfigManager.applySetting("ring-max-ceiling-drop", "20");

        assertNotNull(RingIndex.volumeAt(WORLD, 100, 64 - 15, 100), "and inside a drop of 20");
    }

    /**
     * A setting that is saved but cannot be applied says so, rather than the command failing.
     *
     * <p>The value is already in config.yml by then, so an exception escaping would leave the
     * operator with an error and a setting that had in fact changed.
     */
    @Test
    void aSettingThatCannotBeAppliedNowSaysSoInWords()
    {
        ConfigTestSupport.set(ConfigKeys.ECONOMY_ENABLED, false);
        final String said;
        try (MockedStatic<EconomySupport> economy = mockStatic(EconomySupport.class))
        {
            economy.when(EconomySupport::enableEconomy).thenThrow(new IllegalStateException("no vault"));

            said = ConfigManager.applySetting("economy-enabled", "true");
        }

        assertTrue(said.contains("could not be applied now") && said.contains("no vault"), said);
        assertTrue(ConfigManager.isEconomyEnabled(), "the value itself was still changed");
    }

    /**
     * permissions-support-disable turned off in-game finds the fallback ready.
     *
     * <p>Startup used to look for a provider only when support was on, so a server started with it
     * off had never looked: turned on later, it asked for nodes nobody could have.
     */
    @Test
    void supportTurnedBackOnInGameLooksForAProviderAgain()
    {
        ConfigTestSupport.set(ConfigKeys.PERMISSIONS_SUPPORT_DISABLE, true);
        ConfigTestSupport.set(ConfigKeys.PERMISSIONS_AUTO_FALLBACK, true);
        try (MockedStatic<PermissionsSupport> permissions = mockStatic(PermissionsSupport.class))
        {
            ConfigManager.applySetting("permissions-support-disable", "false");

            permissions.verify(PermissionsSupport::detectProvider);
        }
    }
}
