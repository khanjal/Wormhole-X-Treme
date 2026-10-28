package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.plugin.EconomySupport;

/**
 * The fare is the gate entered's cost: its own, else its shape's, else {@code economy-use-cost} (#632).
 */
class GateFareTest
{
    private Player player;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        ConfigTestSupport.clear();
        ConfigTestSupport.set(ConfigKeys.ECONOMY_ENABLED, true);
        ConfigTestSupport.set(ConfigKeys.ECONOMY_USE_COST, 3.0);
        player = mock(Player.class);
        when(player.getName()).thenReturn("traveller");
    }

    @AfterEach
    void tearDown() throws Exception
    {
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    /** With no cost of its own a gate charges the server's, as every gate did. */
    @Test
    void aGateWithNoCostOfItsOwnChargesTheServers()
    {
        try (MockedStatic<EconomySupport> economy = mockStatic(EconomySupport.class))
        {
            economy.when(EconomySupport::isAvailable).thenReturn(true);
            economy.when(() -> EconomySupport.canAfford(player, 3.0)).thenReturn(true);

            assertEquals(3.0, GateFare.affordable(player, new Stargate()));
        }
    }

    /** The gate entered sets the fare, dearer or cheaper than the server's. */
    @Test
    void theGateEnteredSetsTheFare()
    {
        final Stargate big = new Stargate();
        big.setGateUseCost(12.5);
        final Stargate small = new Stargate();
        small.setGateUseCost(1.0);
        try (MockedStatic<EconomySupport> economy = mockStatic(EconomySupport.class))
        {
            economy.when(EconomySupport::isAvailable).thenReturn(true);
            economy.when(() -> EconomySupport.canAfford(player, 12.5)).thenReturn(true);
            economy.when(() -> EconomySupport.canAfford(player, 1.0)).thenReturn(true);

            assertEquals(12.5, GateFare.affordable(player, big));
            assertEquals(1.0, GateFare.affordable(player, small));
        }
    }

    /** A free gate charges nothing and asks nothing of the player's purse. */
    @Test
    void aFreeGateChargesNothingWhateverTheServerSays()
    {
        final Stargate free = new Stargate();
        free.setGateUseCost(0.0);
        try (MockedStatic<EconomySupport> economy = mockStatic(EconomySupport.class))
        {
            economy.when(EconomySupport::isAvailable).thenReturn(true);

            assertEquals(0.0, GateFare.affordable(player, free));
            economy.verify(() -> EconomySupport.canAfford(any(), anyDouble()),
                never());
        }
    }

    /** A traveller who cannot pay this gate's fare is turned away, though they could have paid a cheaper one. */
    @Test
    void aTravellerWhoCannotPayThisGatesFareIsTurnedAway()
    {
        final Stargate dear = new Stargate();
        dear.setGateUseCost(50.0);
        try (MockedStatic<EconomySupport> economy = mockStatic(EconomySupport.class))
        {
            economy.when(EconomySupport::isAvailable).thenReturn(true);
            economy.when(() -> EconomySupport.canAfford(player, 50.0)).thenReturn(false);
            economy.when(() -> EconomySupport.canAfford(player, 3.0)).thenReturn(true);

            assertEquals(-1.0, GateFare.affordable(player, dear));
            verify(player).sendMessage(ConfigManager.MessageStrings
                .ECONOMY_INSUFFICIENT_FUNDS.toString());
        }
    }

    /** Nothing is charged while the economy is off, whatever a gate says. */
    @Test
    void nothingIsChargedWhileTheEconomyIsOff()
    {
        ConfigTestSupport.set(ConfigKeys.ECONOMY_ENABLED, false);
        final Stargate dear = new Stargate();
        dear.setGateUseCost(50.0);

        assertEquals(0.0, GateFare.affordable(player, dear));
    }
}
