package com.wormhole_xtreme.wormhole.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.stats.PlayerStats;
import com.wormhole_xtreme.wormhole.utils.ChatText;

/**
 * {@code /wormhole list mine}, {@code owner <player>} and {@code top}, who may use each, and how
 * {@code /dial} and {@code /wormhole go} narrow what they complete (#638).
 *
 * <p>The permission rules are the part that can quietly go wrong: each keyword is open to the
 * list check or to its own node, and a list that was open to the wrong sender would show another
 * player's gates to someone who was only ever allowed their own.
 */
class WXListFilterTest
{
    private static final UUID ALICE = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final UUID BOB = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private MockedStatic<Bukkit> bukkit;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        PlayerStats.clear();
        clearGates();
        bukkit = mockStatic(Bukkit.class);
    }

    @AfterEach
    void tearDown()
    {
        bukkit.close();
        clearGates();
        PluginTestSupport.forgetAllGates();
        PlayerStats.clear();
    }

    private static void clearGates()
    {
        for (final Stargate s : List.copyOf(StargateManager.getAllGates()))
        {
            StargateManager.removeStargate(s);
        }
    }

    private static Stargate gate(final String name, final String owner)
    {
        final Stargate s = new Stargate();
        s.setGateName(name);
        s.setGateOwner(owner);
        StargateManager.registerStargate(s);
        return s;
    }

    private static Player counted()
    {
        return mock(Player.class);
    }

    private static void trips(final String gateName, final int times)
    {
        final Stargate g = new Stargate();
        g.setGateName(gateName);
        for (int i = 0; i < times; i++)
        {
            PlayerStats.countGateTrip(counted(), g);
        }
    }

    /** A non-operator holding exactly these nodes: the list check fails for them, as it does with no permission. */
    private static Player player(final UUID id, final String name, final String... nodes)
    {
        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.getName()).thenReturn(name);
        when(player.isOp()).thenReturn(false);
        when(player.hasPermission(anyString())).thenReturn(false);
        for (final String node : nodes)
        {
            when(player.hasPermission(node)).thenReturn(true);
        }
        return player;
    }

    private static boolean list(final CommandSender sender, final String... args)
    {
        return new WXList().onCommand(sender, null, "list", args);
    }

    private static String shown(final CommandSender sender)
    {
        final ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(sender, atLeastOnce()).sendMessage(sent.capture());
        return String.join("\n", sent.getAllValues().stream().map(ChatText::plain).toList());
    }

    /** The numbered lines of a top list, without the plugin's line header. */
    private static List<String> ranked(final String text)
    {
        return text.lines().map(l -> l.replaceFirst("^.*?(\\d+\\. \\S+: \\d+)$", "$1"))
            .filter(l -> l.matches("\\d+\\. \\S+: \\d+")).toList();
    }

    private static void assertRefused(final String text)
    {
        assertTrue(text.contains("ermission"), text);
    }

    @SuppressWarnings("deprecation")
    private void bobHasPlayedOffline()
    {
        final OfflinePlayer bob = mock(OfflinePlayer.class);
        when(bob.getUniqueId()).thenReturn(BOB);
        when(bob.getName()).thenReturn("Bob");
        when(bob.hasPlayedBefore()).thenReturn(true);
        bukkit.when(() -> Bukkit.getOfflinePlayer("bob")).thenReturn(bob);
    }

    // ---- mine -------------------------------------------------------------

    @Test
    void mineShowsTheGatesTheSenderOwnsByUuidOrLegacyName()
    {
        gate("alpha", ALICE.toString());
        gate("beta", BOB.toString());
        gate("gamma", "alice");
        gate("delta", null);
        final Player alice = player(ALICE, "Alice", WXList.MINE);

        assertTrue(list(alice, "mine"));

        final String text = shown(alice);
        assertTrue(text.contains("alpha"), text);
        assertTrue(text.contains("gamma"), "a legacy gate stored by name: " + text);
        assertFalse(text.contains("beta"), "another player's: " + text);
        assertFalse(text.contains("delta"), "a gate with no owner is nobody's: " + text);
    }

    @Test
    void mineIsAllowedByItsOwnNodeAlone()
    {
        gate("alpha", ALICE.toString());
        final Player alice = player(ALICE, "Alice", WXList.MINE);

        list(alice, "mine");
        list(alice);

        final String text = shown(alice);
        assertTrue(text.contains("Your gates"), text);
        assertRefused(text);
    }

    @Test
    void mineIsAllowedByTheListCheckWithoutItsNode()
    {
        gate("alpha", ALICE.toString());
        final Player alice = player(ALICE, "Alice");
        when(alice.isOp()).thenReturn(true);

        list(alice, "mine");

        assertTrue(shown(alice).contains("alpha"));
    }

    @Test
    void mineIsRefusedToAPlayerWithNeitherNode()
    {
        gate("alpha", ALICE.toString());
        final Player alice = player(ALICE, "Alice");

        list(alice, "mine");

        final String text = shown(alice);
        assertRefused(text);
        assertFalse(text.contains("alpha"), text);
    }

    @Test
    void theConsoleHasNoGatesOfItsOwn()
    {
        gate("alpha", ALICE.toString());
        final CommandSender console = mock(CommandSender.class);

        list(console, "mine");

        final String text = shown(console);
        assertTrue(text.contains("no gates of its own"), text);
        assertFalse(text.contains("alpha"), text);
    }

    @Test
    void aPlayerWithNoGatesIsToldSo()
    {
        gate("beta", BOB.toString());
        final Player alice = player(ALICE, "Alice", WXList.MINE);

        list(alice, "mine");

        assertTrue(shown(alice).contains("No gates found"));
    }

    // ---- owner ------------------------------------------------------------

    @Test
    void ownerShowsAnOfflinePlayersGatesByUuidOrLegacyName()
    {
        gate("alpha", ALICE.toString());
        gate("beta", BOB.toString());
        gate("gamma", "BOB");
        bobHasPlayedOffline();
        final CommandSender console = mock(CommandSender.class);

        assertTrue(list(console, "owner", "bob"));

        final String text = shown(console);
        assertTrue(text.contains("Gates owned by Bob ::"), text);
        assertTrue(text.contains("beta"), text);
        assertTrue(text.contains("gamma"), "a legacy gate stored by name: " + text);
        assertFalse(text.contains("alpha"), text);
    }

    @Test
    void ownerFindsAPlayerWhoIsOnline()
    {
        gate("alpha", ALICE.toString());
        final Player alice = player(ALICE, "Alice");
        bukkit.when(() -> Bukkit.getPlayerExact("Alice")).thenReturn(alice);
        final CommandSender console = mock(CommandSender.class);

        list(console, "owner", "Alice");

        assertTrue(shown(console).contains("alpha"));
    }

    @Test
    void ownerNamingAPlayerTheServerHasNeverSeenSaysSo()
    {
        gate("alpha", ALICE.toString());
        final OfflinePlayer nobody = mock(OfflinePlayer.class);
        when(nobody.hasPlayedBefore()).thenReturn(false);
        bukkit.when(() -> Bukkit.getOfflinePlayer("nobody")).thenReturn(nobody);
        final CommandSender console = mock(CommandSender.class);

        list(console, "owner", "nobody");

        final String text = shown(console);
        assertTrue(text.contains("No player called nobody has played here."), text);
        assertFalse(text.contains("alpha"), text);
    }

    @Test
    void ownerWithNoNameAsksForTheUsageLine()
    {
        assertFalse(list(mock(CommandSender.class), "owner"));
    }

    @Test
    void ownerIsAllowedByItsOwnNodeAlone()
    {
        gate("beta", BOB.toString());
        bobHasPlayedOffline();
        final Player alice = player(ALICE, "Alice", WXList.OWNER);

        list(alice, "owner", "bob");

        assertTrue(shown(alice).contains("beta"));
    }

    @Test
    void ownerIsAllowedByTheListCheckWithoutItsNode()
    {
        gate("beta", BOB.toString());
        bobHasPlayedOffline();
        final Player alice = player(ALICE, "Alice");
        when(alice.isOp()).thenReturn(true);

        list(alice, "owner", "bob");

        assertTrue(shown(alice).contains("beta"));
    }

    /** The own-gates node does not let a player look at anybody else's. */
    @Test
    void ownerIsRefusedToAPlayerHoldingOnlyTheMineNode()
    {
        gate("beta", BOB.toString());
        bobHasPlayedOffline();
        final Player alice = player(ALICE, "Alice", WXList.MINE);

        list(alice, "owner", "bob");

        final String text = shown(alice);
        assertRefused(text);
        assertFalse(text.contains("beta"), text);
    }

    // ---- top --------------------------------------------------------------

    @Test
    void topListsTheMostUsedFirstWithTheirCountsAndBreaksTiesByNameIgnoringCase()
    {
        for (final String name : List.of("alpha", "Bravo", "charlie", "delta"))
        {
            gate(name, null);
        }
        trips("alpha", 3);
        trips("Bravo", 5);
        trips("charlie", 3);
        trips("delta", 1);
        final CommandSender console = mock(CommandSender.class);

        list(console, "top");

        final String text = shown(console);
        assertTrue(text.contains("Busiest gates, by trips ::"), text);
        final List<String> lines = ranked(text);
        assertEquals(List.of("1. Bravo: 5", "2. alpha: 3", "3. charlie: 3", "4. delta: 1"), lines);
    }

    @Test
    void topShowsTenAtMost()
    {
        for (int i = 0; i < 12; i++)
        {
            final String name = String.format("gate%02d", i);
            gate(name, null);
            trips(name, i + 1);
        }
        final CommandSender console = mock(CommandSender.class);

        list(console, "top");

        final List<String> lines = ranked(shown(console));
        assertEquals(10, lines.size(), lines.toString());
        assertEquals("1. gate11: 12", lines.get(0));
        assertEquals("10. gate02: 3", lines.get(9), "the two least used are cut");
    }

    @Test
    void topLeavesOutAGateThatNoLongerExists()
    {
        gate("alpha", null);
        trips("alpha", 2);
        trips("ghost", 9);
        final CommandSender console = mock(CommandSender.class);

        list(console, "top");

        final String text = shown(console);
        assertTrue(text.contains("alpha"), text);
        assertFalse(text.contains("ghost"), text);
    }

    @Test
    void topWithNoTripsCountedSaysSo()
    {
        gate("alpha", null);
        final CommandSender console = mock(CommandSender.class);

        list(console, "top");

        assertTrue(shown(console).contains("No trips counted yet."));
    }

    @Test
    void topNeedsTheListCheckNotTheMineNode()
    {
        gate("alpha", null);
        trips("alpha", 1);
        final Player alice = player(ALICE, "Alice", WXList.MINE, WXList.OWNER);

        list(alice, "top");

        final String text = shown(alice);
        assertRefused(text);
        assertFalse(text.contains("alpha"), text);
    }

    @Test
    void topIsAllowedByTheListCheck()
    {
        gate("alpha", null);
        trips("alpha", 1);
        final Player alice = player(ALICE, "Alice");
        when(alice.isOp()).thenReturn(true);

        list(alice, "top");

        assertTrue(shown(alice).contains("1. alpha: 1"));
    }

    // ---- keyword precedence ----------------------------------------------

    /** A network called {@code mine} cannot be listed by that name: the keyword wins, as the docs say. */
    @Test
    void aKeywordWinsOverANetworkOfTheSameName()
    {
        final Stargate onNetwork = gate("netgate", null);
        onNetwork.setGateNetwork(StargateManager.addStargateNetwork("mine"));
        gate("alpha", ALICE.toString());
        final Player alice = player(ALICE, "Alice", WXList.MINE);

        list(alice, "mine");

        final String text = shown(alice);
        assertTrue(text.contains("alpha"), text);
        assertFalse(text.contains("netgate"), text);
    }

    @Test
    void aNameThatIsNotAKeywordStillFiltersByNetwork()
    {
        final Stargate onNetwork = gate("netgate", null);
        onNetwork.setGateNetwork(StargateManager.addStargateNetwork("minecraft"));
        gate("alpha", ALICE.toString());
        final CommandSender console = mock(CommandSender.class);

        list(console, "minecraft");

        final String text = shown(console);
        assertTrue(text.contains("Gates on network minecraft"), text);
        assertTrue(text.contains("netgate"), text);
        assertFalse(text.contains("alpha"), text);
    }

    // ---- completion of the list command -----------------------------------

    private static List<String> completeList(final CommandSender sender, final String... typed)
    {
        final String[] args = new String[typed.length + 1];
        args[0] = "list";
        System.arraycopy(typed, 0, args, 1, typed.length);
        return SubCommands.find("list").completeArgs(sender, args);
    }

    @Test
    void theConsoleIsOfferedEveryKeyword()
    {
        final List<String> offered = completeList(mock(CommandSender.class), "");

        assertTrue(offered.containsAll(List.of("mine", "owner", "top")), offered.toString());
    }

    @Test
    void aPlayerWithOnlyTheMineNodeIsOfferedOnlyMine()
    {
        final List<String> offered = completeList(player(ALICE, "Alice", WXList.MINE), "");

        assertTrue(offered.contains("mine"), offered.toString());
        assertFalse(offered.contains("owner"), offered.toString());
        assertFalse(offered.contains("top"), offered.toString());
    }

    @Test
    void aPlayerWithOnlyTheOwnerNodeIsOfferedOnlyOwner()
    {
        final List<String> offered = completeList(player(ALICE, "Alice", WXList.OWNER), "");

        assertTrue(offered.contains("owner"), offered.toString());
        assertFalse(offered.contains("mine"), offered.toString());
        assertFalse(offered.contains("top"), offered.toString());
    }

    @Test
    void aTypedPrefixNarrowsTheKeywords()
    {
        assertEquals(List.of("top"), completeList(mock(CommandSender.class), "t"));
    }

    @Test
    void afterOwnerTheOnlinePlayersAreOffered()
    {
        final Player bob = player(BOB, "Bob");
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Collections.<Player>singletonList(bob));
        final CommandSender console = mock(CommandSender.class);

        assertEquals(List.of("Bob"), completeList(console, "owner", "b"));
        assertEquals(List.of(), completeList(console, "mine", "b"), "only owner takes a name");
    }

    // ---- gate names completed for /dial and /wormhole go -----------------

    private static List<String> dial(final CommandSender sender, final String typed)
    {
        return new DialTabCompleter().onTabComplete(sender, null, "dial", new String[] { typed });
    }

    private static List<String> go(final CommandSender sender, final String typed)
    {
        return SubCommands.find("go").completeArgs(sender, new String[] { "go", typed });
    }

    private void threeGates()
    {
        gate("alpha", ALICE.toString());
        gate("apple", BOB.toString());
        gate("amber", "alice");
    }

    @Test
    void aPlayerWithTheListCheckIsOfferedEveryGate()
    {
        threeGates();
        final Player alice = player(ALICE, "Alice");
        when(alice.isOp()).thenReturn(true);

        assertEquals(List.of("alpha", "apple", "amber").stream().sorted().toList(),
            dial(alice, "a").stream().sorted().toList());
        assertEquals(3, go(alice, "a").size());
    }

    @Test
    void aPlayerWithOnlyTheMineNodeIsOfferedOnlyTheirOwnGates()
    {
        threeGates();
        final Player alice = player(ALICE, "Alice", WXList.MINE);

        assertEquals(List.of("alpha", "amber"), dial(alice, "a").stream().sorted().toList());
        assertEquals(List.of("alpha", "amber"), go(alice, "a"));
    }

    @Test
    void aPlayerWithNeitherNodeIsOfferedNoGate()
    {
        threeGates();
        final Player alice = player(ALICE, "Alice");

        assertEquals(List.of(), dial(alice, "a"));
        assertEquals(List.of(), go(alice, "a"));
    }

    @Test
    void theConsoleIsOfferedEveryGate()
    {
        threeGates();
        final CommandSender console = mock(CommandSender.class);

        assertEquals(3, dial(console, "a").size());
        assertEquals(3, go(console, "a").size());
    }

    /** Only dial and go narrow: the gate name that remove takes is still offered to everyone. */
    @Test
    void otherCommandsStillCompleteEveryGateName()
    {
        threeGates();
        final Player alice = player(ALICE, "Alice");

        assertEquals(3, SubCommands.find("remove").completeArgs(alice, new String[] { "remove", "a" }).size());
    }

    // ---- ownership and the stats a removal drops --------------------------

    @Test
    void isOwnedByMatchesAUuidOwnerByIdAndALegacyOwnerByNameIgnoringCase()
    {
        final Stargate byId = gate("alpha", ALICE.toString());
        final Stargate byName = gate("beta", "Alice");

        assertTrue(byId.isOwnedBy(ALICE, "whoever"));
        assertFalse(byId.isOwnedBy(BOB, "Alice"), "a UUID owner is not matched by name");
        assertFalse(byId.isOwnedBy(null, "Alice"));
        assertTrue(byName.isOwnedBy(BOB, "aLICE"));
        assertFalse(byName.isOwnedBy(ALICE, "Bob"), "a legacy owner is not matched by id");
        assertFalse(byName.isOwnedBy(ALICE, null));
        assertFalse(gate("gamma", null).isOwnedBy(ALICE, "Alice"));
    }

    @Test
    void removingAGateDropsItsTripCountSoANewOneOfTheNameStartsAtNone()
    {
        final Stargate alpha = gate("Alpha", null);
        trips("alpha", 4);

        StargateManager.removeStargate(alpha);

        assertEquals(0, PlayerStats.gateTrips("alpha"));
    }

    /** A refresh takes the gate out and puts it back unannounced, and its trips stay with it. */
    @Test
    void aRefreshHandingTheGateBackKeepsItsTripCount()
    {
        final Stargate alpha = gate("alpha", null);
        trips("alpha", 4);

        StargateManager.removeStargate(alpha, null, false);

        assertEquals(4, PlayerStats.gateTrips("alpha"));
    }
}
