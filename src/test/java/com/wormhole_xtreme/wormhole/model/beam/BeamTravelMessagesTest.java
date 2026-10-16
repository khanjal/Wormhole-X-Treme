package com.wormhole_xtreme.wormhole.model.beam;

import static com.wormhole_xtreme.wormhole.utils.ChatMatchers.plainBody;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
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
import com.wormhole_xtreme.wormhole.plugin.EconomySupport;
import com.wormhole_xtreme.wormhole.utils.ChatText;

/**
 * What {@link BeamTravel#travelTo} tells a traveller it turns away.
 *
 * <p>Each refusal carries the one fact a player needs: which world is not loaded, how many
 * seconds are left, what the trip costs. Beaming used to borrow the gate's cooldown line, which
 * split the wait onto a second line headed "Current Wait (in seconds)"; it says the wait in its
 * own sentence now (#438).
 *
 * <p>Only the refusals: every path that is let through goes on to {@link BeamAnimation#start},
 * whose class cannot be loaded without a server (its {@code TRAVELLER_EFFECTS} names
 * {@code PotionEffectType}s), so the "This will cost" and "Charged" lines are left to the lab.
 */
class BeamTravelMessagesTest
{
    private static final String WORLD = "world";

    private Player player;
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<EconomySupport> economy;

    @BeforeEach
    void setUp()
    {
        ConfigTestSupport.loadDefaults();
        BeamManager.clear();
        final World world = mock(World.class);
        when(world.getName()).thenReturn(WORLD);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
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
        ConfigTestSupport.clear();
    }

    /** A destination in an unloaded world names the destination and the world, not a typo. */
    @Test
    void aDestinationInAnUnloadedWorldNamesBoth()
    {
        BeamManager.setPublicDestination(new BeamDestination("Far", new BeamPoint("nether", 0, 64, 0, 0f, 0f), null));

        assertTrue(BeamTravel.travelTo(player, "Far"), "a name that exists is handled, not passed on to the gates");

        verify(player).sendMessage(ChatText.ERROR_HEADER + ChatText.NAME_COLOUR + "Far" + ChatText.BODY_COLOUR
            + " is in world " + ChatText.NAME_COLOUR + "nether" + ChatText.BODY_COLOUR + ", which is not loaded.");
    }

    /** A traveller on cooldown is told how long is left, in the same line, and singular for one. */
    @Test
    void aTravellerOnCooldownIsToldHowLongIsLeft()
    {
        ConfigTestSupport.set(ConfigKeys.BEAM_USE_COOLDOWN_ENABLED, true);
        ConfigTestSupport.set(ConfigKeys.BEAM_USE_COOLDOWN_SECONDS, 30);
        BeamCooldown.start(player);

        assertTrue(BeamTravel.travelTo(player, "Dock"));

        verify(player).sendMessage(plainBody("You must wait longer before beaming again: 30 seconds."));

        ConfigTestSupport.set(ConfigKeys.BEAM_USE_COOLDOWN_SECONDS, 1);
        BeamTravel.travelTo(player, "Dock");

        verify(player).sendMessage(plainBody("You must wait longer before beaming again: 1 second."));
    }

    /** A traveller who cannot pay is told the price, with its currency. */
    @Test
    void aTravellerWhoCannotPayIsToldThePrice()
    {
        ConfigTestSupport.set(ConfigKeys.ECONOMY_ENABLED, true);
        ConfigTestSupport.set(ConfigKeys.BEAM_ECONOMY_USE_COST, 5.0);
        economy.when(() -> EconomySupport.canAfford(player, 5.0)).thenReturn(Boolean.FALSE);

        assertTrue(BeamTravel.travelTo(player, "Dock"));

        verify(player).sendMessage(ChatText.ERROR_HEADER + "You cannot afford this beam. It costs "
            + ChatText.COMMAND_COLOUR + "5.0 coins" + ChatText.BODY_COLOUR + ".");
    }
}
