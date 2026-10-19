package com.wormhole_xtreme.wormhole.model.beam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
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
 * What a beam tells its traveller, read by key from {@code lang/} (#26).
 *
 * <p>Each line is checked twice: for an English player against the concatenation the code built before
 * the move, copied from it, so a reworded template or a swapped argument shows; and for a player whose
 * client reads the test language qq, so a line read in the server's language instead of the player's
 * shows as English. The refusals go through {@link BeamTravel#travelTo}; the lines sent once a beam
 * starts are read from their helpers, since {@link BeamAnimation} cannot be loaded without a server.
 */
class BeamTravelLanguageTest
{
    private static final String WORLD = "world";
    private static final String DOCK = "Dock";
    private static final String FAR = "Far";
    private static final String COINS = "coins";

    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<EconomySupport> economy;

    @BeforeEach
    void setUp()
    {
        ConfigTestSupport.loadDefaults();
        BeamManager.clear();
        final World world = mock(World.class);
        when(world.getName()).thenReturn(WORLD);
        BeamManager.setPublicDestination(new BeamDestination(DOCK, new BeamPoint(WORLD, 1.5, 64.0, 2.5, 0f, 0f), null));
        BeamManager.setPublicDestination(new BeamDestination(FAR, new BeamPoint("nether", 0, 64, 0, 0f, 0f), null));

        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.getWorld(WORLD)).thenReturn(world);
        economy = mockStatic(EconomySupport.class);
        economy.when(EconomySupport::isAvailable).thenReturn(Boolean.TRUE);
        economy.when(() -> EconomySupport.currencyName(anyDouble())).thenReturn(COINS);
    }

    @AfterEach
    void tearDown()
    {
        economy.close();
        bukkit.close();
        BeamManager.clear();
        ConfigTestSupport.clear();
    }

    /** A traveller allowed to beam, whose client reads {@code locale}. */
    private static Player reading(final String locale)
    {
        final Player player = mock(Player.class);
        when(player.getLocale()).thenReturn(locale);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.hasPermission(anyString())).thenReturn(Boolean.FALSE);
        when(player.hasPermission(BeamPermissions.USE)).thenReturn(Boolean.TRUE);
        return player;
    }

    /** Each traveller tries a beam with {@code seconds} of cooldown left, so each is turned away. */
    private static void onCooldown(final int seconds, final Player... players)
    {
        ConfigTestSupport.set(ConfigKeys.BEAM_USE_COOLDOWN_ENABLED, true);
        ConfigTestSupport.set(ConfigKeys.BEAM_USE_COOLDOWN_SECONDS, seconds);
        for (final Player player : players)
        {
            BeamCooldown.start(player);
            assertTrue(BeamTravel.travelTo(player, DOCK), "a destination that exists is handled");
        }
    }

    /** The traveller tries a beam that costs five, and cannot pay. */
    private void cannotPay(final Player player)
    {
        ConfigTestSupport.set(ConfigKeys.ECONOMY_ENABLED, true);
        ConfigTestSupport.set(ConfigKeys.BEAM_ECONOMY_USE_COST, 5.0);
        economy.when(() -> EconomySupport.canAfford(player, 5.0)).thenReturn(Boolean.FALSE);
        assertTrue(BeamTravel.travelTo(player, DOCK), "a destination that exists is handled");
    }

    /** The refusals read as they did before: unloaded world, both cooldown counts, and the price. */
    @Test
    void theRefusalsReadAsTheyDidBefore()
    {
        final Player unloaded = reading("en");
        final Player one = reading("en");
        final Player many = reading("en");
        final Player poor = reading("en");

        assertTrue(BeamTravel.travelTo(unloaded, FAR));
        onCooldown(1, one);
        onCooldown(30, many);
        ConfigTestSupport.set(ConfigKeys.BEAM_USE_COOLDOWN_ENABLED, false);
        cannotPay(poor);

        verify(unloaded).sendMessage(ChatText.error(ChatText.name(FAR) + " is in world " + ChatText.name("nether")
            + ", which is not loaded."));
        verify(one).sendMessage(ChatText.error("You must wait longer before beaming again: "
            + (ChatText.value(String.valueOf(1)) + " second") + "."));
        verify(many).sendMessage(ChatText.error("You must wait longer before beaming again: "
            + (ChatText.value(String.valueOf(30)) + " seconds") + "."));
        verify(poor).sendMessage(ChatText.error("You cannot afford this beam. It costs "
            + ChatText.value(5.0 + " " + COINS) + "."));
    }

    /** The same refusals reach a qq player in qq, one second told apart from several. */
    @Test
    void theRefusalsAreInThePlayersLanguage()
    {
        final Player unloaded = reading("qq");
        final Player one = reading("qq");
        final Player many = reading("qq");
        final Player poor = reading("qq");

        assertTrue(BeamTravel.travelTo(unloaded, FAR));
        onCooldown(1, one);
        onCooldown(30, many);
        ConfigTestSupport.set(ConfigKeys.BEAM_USE_COOLDOWN_ENABLED, false);
        cannotPay(poor);

        verify(unloaded).sendMessage(ChatText.error("QQ " + ChatText.name(FAR) + " unloaded " + ChatText.name("nether")
            + "."));
        verify(one).sendMessage(ChatText.error("QQ wait " + ChatText.value("1") + " one."));
        verify(many).sendMessage(ChatText.error("QQ wait " + ChatText.value("30") + " other."));
        verify(poor).sendMessage(ChatText.error("QQ cannot afford " + ChatText.value("5.0 coins") + "."));
    }

    /** What a paid beam says before it starts and once charged, as before and in qq. */
    @Test
    void theCostNoticesReadAsBeforeAndInThePlayersLanguage()
    {
        final Player english = reading("en");
        final Player qq = reading("qq");
        final String price = ChatText.value(2.5 + " " + COINS);

        assertEquals(List.of(ChatText.info("This will cost " + price + "."), ChatText.info("Charged " + price + ".")),
            List.of(BeamTravel.willCost(english, 2.5), BeamTravel.charged(english, 2.5)));
        assertEquals(List.of(ChatText.info("QQ will cost " + price + "."), ChatText.info("QQ charged " + price + ".")),
            List.of(BeamTravel.willCost(qq, 2.5), BeamTravel.charged(qq, 2.5)));
    }

    /** What the beam itself says, from start to arrival and on failure, as before and in qq. */
    @Test
    void theBeamsOwnLinesReadAsBeforeAndInThePlayersLanguage()
    {
        final Player english = reading("en");
        final Player qq = reading("qq");

        assertEquals(List.of(ChatText.error("You're already beaming somewhere."),
            ChatText.info("Beaming to " + ChatText.name(DOCK) + "..."),
            ChatText.info("Beamed to " + ChatText.name(DOCK) + "."),
            ChatText.error("Something went wrong mid-beam; you have been freed rather than " + "left stuck.")),
            List.of(BeamLines.alreadyBeaming(english), BeamLines.beaming(english, DOCK), BeamLines.beamed(english, DOCK),
                BeamLines.freed(english)));
        assertEquals(List.of(ChatText.error("QQ already beaming."), ChatText.info("QQ beaming " + ChatText.name(DOCK) + "."),
            ChatText.info("QQ beamed " + ChatText.name(DOCK) + "."), ChatText.error("QQ freed.")),
            List.of(BeamLines.alreadyBeaming(qq), BeamLines.beaming(qq, DOCK), BeamLines.beamed(qq, DOCK),
                BeamLines.freed(qq)));
    }
}
