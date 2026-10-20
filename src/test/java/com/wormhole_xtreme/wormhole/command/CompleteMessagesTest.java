package com.wormhole_xtreme.wormhole.command;

import static com.wormhole_xtreme.wormhole.utils.ChatText.command;
import static com.wormhole_xtreme.wormhole.utils.ChatText.error;
import static com.wormhole_xtreme.wormhole.utils.ChatText.info;
import static com.wormhole_xtreme.wormhole.utils.ChatText.name;
import static com.wormhole_xtreme.wormhole.utils.ChatText.usage;
import static com.wormhole_xtreme.wormhole.utils.ChatText.value;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions;
import com.wormhole_xtreme.wormhole.plugin.EconomySupport;

/**
 * What {@code /wormhole complete} says, read by key from {@code lang/} (#26).
 *
 * <p>Each line goes through the real command to an English builder, against the text the code built
 * before the move, copied from it, so a reworded template, a swapped argument or a lost branch shows as
 * a changed line; and to a builder whose client reads the test language qq, so a call site that reads the
 * server's language instead of the reader's shows as English. The two ways a cancel can end, a gate
 * called after something already taken, and the fare charged for a gate are each covered.
 */
class CompleteMessagesTest
{
    private static final String USAGE = "/wormhole complete <name> [idc=<code>] [net=<network>]";
    private static final String CANCEL_WORDS = "/wormhole gate complete -cancel";

    private final List<Player> builders = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
    }

    @AfterEach
    void tearDown() throws Exception
    {
        builders.forEach(player -> {
            Complete.removePendingCompletion(player);
            StargateManager.removeIncompleteStargate(player);
        });
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    /** @return a builder who may build anywhere, whose client reads {@code locale} */
    private Player reading(final String locale)
    {
        final Player player = mock(Player.class);
        when(player.getLocale()).thenReturn(locale);
        when(player.getName()).thenReturn("builder");
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOp()).thenReturn(Boolean.TRUE);
        builders.add(player);
        return player;
    }

    private static void run(final Player player, final String... args)
    {
        new Complete().onCommand(player, null, "wormhole", args);
    }

    /** Help, a cancel with nothing waiting, a cancel with a gate waiting, and a name that cannot be used. */
    private void drive(final Player player)
    {
        run(player, "-help");
        run(player, "-cancel");
        run(player, "Waiting");
        run(player, "-cancel");
        run(player, "-gate");
        run(player, "TwelveCharsX");
        run(player, "Fresh", "net=Private");
    }

    /** Each line as the code built it before the move, for a name taken, and for the three that wait for a click. */
    @Test
    void theCompleteLinesReadAsTheyDidBefore()
    {
        final Player player = reading("en");
        final Stargate taken = new Stargate();
        taken.setGateName("Taken");
        StargateManager.registerStargate(taken);
        try
        {
            drive(player);
            run(player, "Taken");
        }
        finally
        {
            StargateManager.removeStargate(taken);
        }

        verify(player).sendMessage(info(usage(USAGE)));
        verify(player).sendMessage(info("For example " + command("/wormhole complete MyGate idc=1234 net=Private")
            + ". " + command("/wx complete") + " works too."));
        verify(player).sendMessage(info("There was no gate completion to cancel."));
        verify(player).sendMessage(info("Gate completion cancelled."));
        verify(player).sendMessage(error("A gate name cannot start with " + command("-")
            + "; words that do are options, like " + command(Complete.CANCEL) + "."));
        verify(player).sendMessage(error("Gate name " + name("TwelveCharsX") + " is too long. Use at most "
            + value("11") + " characters."));
        verify(player).sendMessage(error("A gate called " + name("Taken") + " already exists. Pick another name."));
        verify(player, times(2)).sendMessage(info("Click the DHD lever or button of the gate to complete it."));
        verify(player).sendMessage(info("Add " + command("idc=<code>") + " or " + command("net=<network>") + " after "
            + name("Fresh") + " to set its iris code or network."));
        verify(player, times(2)).sendMessage(info("Run " + command(CANCEL_WORDS) + " to cancel."));
    }

    /** The same lines reach a qq builder in qq. */
    @Test
    void theCompleteLinesAreInTheReadersLanguage()
    {
        final Player player = reading("qq");
        final Stargate taken = new Stargate();
        taken.setGateName("Taken");
        StargateManager.registerStargate(taken);
        try
        {
            drive(player);
            run(player, "Taken");
        }
        finally
        {
            StargateManager.removeStargate(taken);
        }

        verify(player).sendMessage(info(usage(USAGE)));
        verify(player).sendMessage(info("QQ example " + command("/wormhole complete MyGate idc=1234 net=Private") + " "
            + command("/wx complete") + "."));
        verify(player).sendMessage(info("QQ nothing to cancel."));
        verify(player).sendMessage(info("QQ cancelled."));
        verify(player).sendMessage(error("QQ dash " + command("-") + " " + command(Complete.CANCEL) + "."));
        verify(player).sendMessage(error("QQ long " + name("TwelveCharsX") + " " + value("11") + "."));
        verify(player).sendMessage(error("QQ taken " + name("Taken") + "."));
        verify(player, times(2)).sendMessage(info("QQ click dhd."));
        verify(player).sendMessage(info("QQ options " + command("idc=<code>") + " " + command("net=<network>") + " "
            + name("Fresh") + "."));
        verify(player, times(2)).sendMessage(info("QQ cancel " + command(CANCEL_WORDS) + "."));
    }

    /** A gate that will not complete names the half-built one, in the reader's language. */
    @Test
    void aRefusedCompletionNamesTheHalfBuiltGateAsBeforeAndInTheReadersLanguage()
    {
        final Player english = reading("en");
        final Player qq = reading("qq");
        for (final Player player : List.of(english, qq))
        {
            final Stargate halfBuilt = new Stargate();
            halfBuilt.setGateName("Partial");
            StargateManager.addIncompleteStargate(player, halfBuilt);
            try (MockedStatic<StargateManager> manager = mockStatic(StargateManager.class, Mockito.CALLS_REAL_METHODS))
            {
                manager.when(() -> StargateManager.completeStargate(any(), anyString(), anyString(), anyString()))
                    .thenReturn(false);
                run(player, "Fresh");
            }
        }

        verify(english).sendMessage(error("Completing " + name("Partial") + " failed. Check the server log for details."));
        verify(qq).sendMessage(error("QQ failed " + name("Partial") + "."));
    }

    /** Arguments that throw are answered with the usage, in the reader's language. */
    @Test
    void argumentsThatThrowAreAnsweredWithTheUsageAsBeforeAndInTheReadersLanguage()
    {
        final Player english = reading("en");
        final Player qq = reading("qq");
        try (MockedStatic<WXPermissions> permissions = mockStatic(WXPermissions.class))
        {
            permissions.when(() -> WXPermissions.checkWXPermissions(any(Player.class), anyString(), any(WXPermissions.PermissionType.class)))
                .thenThrow(new IllegalStateException("refused"));
            run(english, "Fresh");
            run(qq, "Fresh");
        }

        verify(english).sendMessage(error("Those arguments did not work. " + usage(USAGE)));
        verify(qq).sendMessage(error("QQ bad arguments " + usage(USAGE)));
    }

    /** What a completed gate costs is told with the amount and the currency, in the reader's language. */
    @Test
    void theFareChargedForAGateReadsAsBeforeAndInTheReadersLanguage()
    {
        final Player english = reading("en");
        final Player qq = reading("qq");
        ConfigTestSupport.set(ConfigKeys.ECONOMY_ENABLED, true);
        ConfigTestSupport.set(ConfigKeys.ECONOMY_BUILD_COST, 5.0);
        try (MockedStatic<EconomySupport> economy = mockStatic(EconomySupport.class);
            MockedStatic<StargateManager> manager = mockStatic(StargateManager.class, Mockito.CALLS_REAL_METHODS))
        {
            economy.when(EconomySupport::isAvailable).thenReturn(true);
            economy.when(() -> EconomySupport.canAfford(any(), anyDouble())).thenReturn(true);
            economy.when(() -> EconomySupport.currencyName(anyDouble())).thenReturn("coins");
            manager.when(() -> StargateManager.completeStargate(any(), anyString(), anyString(), anyString()))
                .thenReturn(true);
            assertTrue(Complete.completeAndCharge(english, "Mine", "", "", "failed", null));
            assertTrue(Complete.completeAndCharge(qq, "Mine", "", "", "failed", null));
        }

        verify(english).sendMessage(info("Charged " + value("5.0 coins") + " to build it."));
        verify(qq).sendMessage(info("QQ charged " + value("5.0 coins") + " to build."));
    }

    /** A name asked about from the console reads in the server's language, since nobody's client is asked. */
    @Test
    void aNameAskedAboutWithNoReaderUsesTheServersLanguage()
    {
        assertNull(Complete.whyNotAName(null, "fine"));
        assertEquals("A gate name cannot start with " + command("-") + "; words that do are options, like "
            + command(Complete.CANCEL) + ".", Complete.whyNotAName(null, "-x"));
    }
}
