package com.wormhole_xtreme.wormhole.model.beam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.model.stats.PlayerStats;
import com.wormhole_xtreme.wormhole.plugin.EconomySupport;

/**
 * A beam turned away for its price or its cooldown is not counted in the traveller's stats (#237).
 *
 * <p>Beams raise no event, so the count sits in {@link BeamAnimation} on the tick of the real
 * teleport, which a test cannot load; JourneysOnMockServerTest counts the beams that land. Counting
 * where the beam is asked for instead would count every beam turned away here.
 */
class BeamTravelStatsTest
{
    private static final String WORLD = "world";

    /** Fresh each test: a cooldown started here outlives it, keyed on this id. */
    private final UUID travellerId = UUID.randomUUID();

    private Player player;
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<EconomySupport> economy;

    @BeforeEach
    void setUp()
    {
        ConfigTestSupport.loadDefaults();
        BeamManager.clear();
        PlayerStats.clear();
        final World world = mock(World.class);
        when(world.getName()).thenReturn(WORLD);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(travellerId);
        when(player.hasPermission(anyString())).thenReturn(Boolean.FALSE);
        when(player.hasPermission(BeamPermissions.USE)).thenReturn(Boolean.TRUE);
        BeamManager.setPublicDestination(new BeamDestination("Dock", new BeamPoint(WORLD, 1.5, 64.0, 2.5, 0f, 0f), null));

        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.getWorld(WORLD)).thenReturn(world);
        economy = mockStatic(EconomySupport.class);
        economy.when(EconomySupport::isAvailable).thenReturn(Boolean.TRUE);
        economy.when(() -> EconomySupport.currencyName(anyDouble())).thenReturn("coins");
    }

    @AfterEach
    void tearDown()
    {
        economy.close();
        bukkit.close();
        BeamManager.clear();
        PlayerStats.clear();
        ConfigTestSupport.clear();
    }

    @Test
    void aBeamRefusedForItsPriceCountsNothing()
    {
        ConfigTestSupport.set(ConfigKeys.ECONOMY_ENABLED, true);
        ConfigTestSupport.set(ConfigKeys.BEAM_ECONOMY_USE_COST, 5.0);
        economy.when(() -> EconomySupport.canAfford(player, 5.0)).thenReturn(Boolean.FALSE);

        assertTrue(BeamTravel.travelTo(player, "Dock"), "precondition: the name is a destination, and refused");

        assertEquals(PlayerStats.Counts.NONE, PlayerStats.of(travellerId));
    }

    @Test
    void aBeamRefusedForItsCooldownCountsNothing()
    {
        ConfigTestSupport.set(ConfigKeys.BEAM_USE_COOLDOWN_ENABLED, true);
        ConfigTestSupport.set(ConfigKeys.BEAM_USE_COOLDOWN_SECONDS, 30);
        BeamCooldown.start(player);

        assertTrue(BeamTravel.travelTo(player, "Dock"));

        assertEquals(PlayerStats.Counts.NONE, PlayerStats.of(travellerId));
    }
}
