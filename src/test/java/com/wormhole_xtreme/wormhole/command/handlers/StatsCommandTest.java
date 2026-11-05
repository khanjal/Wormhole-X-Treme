package com.wormhole_xtreme.wormhole.command.handlers;

import static com.wormhole_xtreme.wormhole.utils.ChatMatchers.errorBody;
import static com.wormhole_xtreme.wormhole.utils.ChatMatchers.infoBody;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.command.SubCommands;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.stats.PlayerStats;
import com.wormhole_xtreme.wormhole.model.stats.PlayerStats.Counter;

/**
 * {@code /wormhole stats [player]}: your own counts with {@code wormhole.stats}, anybody's with
 * {@code wormhole.stats.others}, and a name the server has never seen said plainly (#237).
 *
 * <p>The second node is the line between a player reading their own numbers and reading everyone
 * else's; a command that showed another's without asking for it would hand every player the second.
 */
class StatsCommandTest
{
    private static final UUID ALICE_ID = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final UUID BOB_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private final StatsCommand command = new StatsCommand();

    private MockedStatic<Bukkit> bukkit;

    @BeforeEach
    void setUp()
    {
        PlayerStats.clear();
        bukkit = mockStatic(Bukkit.class);
        count(ALICE_ID, Counter.TRIPS, 3);
        count(ALICE_ID, Counter.GATES_BUILT, 2);
        count(ALICE_ID, Counter.RINGS, 4);
        count(ALICE_ID, Counter.BEAMS, 5);
        count(BOB_ID, Counter.TRIPS, 2);
        count(BOB_ID, Counter.BEAMS, 3);
    }

    @AfterEach
    void tearDown()
    {
        bukkit.close();
        PlayerStats.clear();
    }

    private static void count(final UUID player, final Counter counter, final int times)
    {
        for (int i = 0; i < times; i++)
        {
            PlayerStats.count(player, counter);
        }
    }

    /** Alice, holding exactly the nodes given; the UUID is stubbed, since every count keys on it. */
    private static Player alice(final String... nodes)
    {
        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(ALICE_ID);
        when(player.getName()).thenReturn("Alice");
        when(player.hasPermission(anyString())).thenReturn(false);
        for (final String node : nodes)
        {
            when(player.hasPermission(node)).thenReturn(true);
        }
        return player;
    }

    /** Bob, known to the server but offline. */
    // Stubs the deprecated lookup by name, which StatsCommand calls since Spigot has no other.
    @SuppressWarnings("deprecation")
    private void bobHasPlayed()
    {
        final OfflinePlayer bob = mock(OfflinePlayer.class);
        when(bob.getUniqueId()).thenReturn(BOB_ID);
        when(bob.getName()).thenReturn("Bob");
        when(bob.hasPlayedBefore()).thenReturn(true);
        bukkit.when(() -> Bukkit.getOfflinePlayer("bob")).thenReturn(bob);
    }

    @Test
    void aPlayerSeesTheirOwnFourCounts()
    {
        final Player alice = alice(StatsCommand.PERMISSION);

        assertTrue(command.execute(alice, new String[] { "stats" }));

        verify(alice).sendMessage(infoBody("Wormhole travels of Alice:"));
        verify(alice).sendMessage(infoBody("Trips through gates: 3"));
        verify(alice).sendMessage(infoBody("Gates built: 2"));
        verify(alice).sendMessage(infoBody("Trips by rings: 4"));
        verify(alice).sendMessage(infoBody("Beams taken: 5"));
    }

    @Test
    void aPlayerWithoutTheNodeIsRefusedTheirOwn()
    {
        final Player alice = alice();

        assertTrue(command.execute(alice, new String[] { "stats" }));

        verify(alice).sendMessage(ConfigManager.MessageStrings.PERMISSION_NO.toString());
        verify(alice, never()).sendMessage(infoBody("Trips through gates: 3"));
    }

    @Test
    void theOthersNodeShowsAnotherPlayersCounts()
    {
        bobHasPlayed();
        final Player alice = alice(StatsCommand.PERMISSION, StatsCommand.OTHERS);

        assertTrue(command.execute(alice, new String[] { "stats", "bob" }));

        verify(alice).sendMessage(infoBody("Wormhole travels of Bob:"));
        verify(alice).sendMessage(infoBody("Trips through gates: 2"));
        verify(alice).sendMessage(infoBody("Beams taken: 3"));
    }

    @Test
    void anotherPlayersCountsAreRefusedWithoutTheOthersNode()
    {
        bobHasPlayed();
        final Player alice = alice(StatsCommand.PERMISSION);

        assertTrue(command.execute(alice, new String[] { "stats", "bob" }));

        verify(alice).sendMessage(ConfigManager.MessageStrings.PERMISSION_NO.toString());
        verify(alice, never()).sendMessage(infoBody("Wormhole travels of Bob:"));
    }

    @Test
    void namingYourselfNeedsOnlyTheOwnNode()
    {
        final Player alice = alice(StatsCommand.PERMISSION);

        assertTrue(command.execute(alice, new String[] { "stats", "alice" }));

        verify(alice).sendMessage(infoBody("Trips through gates: 3"));
    }

    @Test
    @SuppressWarnings("deprecation")
    void aNameTheServerHasNeverSeenIsSaidPlainly()
    {
        final OfflinePlayer nobody = mock(OfflinePlayer.class);
        when(nobody.hasPlayedBefore()).thenReturn(false);
        bukkit.when(() -> Bukkit.getOfflinePlayer("ghost")).thenReturn(nobody);
        final Player alice = alice(StatsCommand.PERMISSION, StatsCommand.OTHERS);

        assertTrue(command.execute(alice, new String[] { "stats", "ghost" }));

        verify(alice).sendMessage(errorBody("No player called ghost has played here."));
    }

    @Test
    void theConsoleNamesAPlayer()
    {
        bobHasPlayed();
        final CommandSender console = mock(CommandSender.class);
        when(console.hasPermission(anyString())).thenReturn(true);

        assertTrue(command.execute(console, new String[] { "stats" }));
        verify(console).sendMessage(errorBody("The console has no stats of its own. Name a player: /wormhole stats <player>"));

        assertTrue(command.execute(console, new String[] { "stats", "bob" }));
        verify(console).sendMessage(infoBody("Trips through gates: 2"));
    }

    @Test
    void tooManyWordsAsksForTheUsage()
    {
        assertFalse(command.execute(alice(StatsCommand.PERMISSION), new String[] { "stats", "bob", "extra" }),
            "false makes /wormhole send the usage line");
    }

    @Test
    void playerNamesAreOfferedOnlyToWhoeverMayLookThemUp()
    {
        final Player online = mock(Player.class);
        when(online.getName()).thenReturn("Bob");
        bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(invocation -> List.of(online));
        final SubCommands.Entry stats = SubCommands.find("stats");

        assertEquals(List.of("Bob"), stats.completeArgs(alice(StatsCommand.OTHERS), new String[] { "stats", "b" }));
        assertEquals(List.of(), stats.completeArgs(alice(StatsCommand.PERMISSION), new String[] { "stats", "b" }),
            "a name offered is a name the command will refuse");
    }
}
