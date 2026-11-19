package com.wormhole_xtreme.wormhole.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.wormhole_xtreme.wormhole.HeldScheduler;
import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.ping.PingTestSupport;
import com.wormhole_xtreme.wormhole.utils.ChatText;
import com.wormhole_xtreme.wormhole.utils.ChunkTickets;

/**
 * {@code /wormhole ping <gate>}: off unless turned on, a plain answer on a server with no locator
 * bar, and never a way to learn a gate the player could not otherwise see (#16).
 *
 * <p>The privacy cases are the ones that matter most: a gate on a network the player is not on,
 * or one the list rules would not show them, must get the same answer as a name that does not
 * exist, or ping becomes a probe for private gates.
 */
class PingCommandTest
{
    private static final UUID ALICE = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    private static final UUID BOB = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private final HeldScheduler held = new HeldScheduler();

    private final List<ArmorStand> spawned = new ArrayList<>();

    private final Map<ArmorStand, AttributeInstance> ranges = new HashMap<>();

    private World here;

    private World elsewhere;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install();
        held.install();
        ConfigTestSupport.loadDefaults();
        clearGates();
        here = world();
        elsewhere = world();
        PingTestSupport.locatorBar(true, ranges::get);
        PingTestSupport.spawnWith(at -> stand());
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PingTestSupport.reset();
        ChunkTickets.clear();
        clearGates();
        PluginTestSupport.forgetAllGates();
        ConfigTestSupport.clear();
        held.remove();
        PluginTestSupport.remove();
    }

    private static void clearGates()
    {
        for (final Stargate s : List.copyOf(StargateManager.getAllGates()))
        {
            StargateManager.removeStargate(s);
        }
    }

    private static World world()
    {
        final World world = mock(World.class);
        when(world.getUID()).thenReturn(UUID.randomUUID());
        final Chunk chunk = mock(Chunk.class);
        when(chunk.getWorld()).thenReturn(world);
        when(world.getChunkAt(any(Location.class))).thenReturn(chunk);
        return world;
    }

    private ArmorStand stand()
    {
        final ArmorStand stand = mock(ArmorStand.class);
        final AttributeInstance instance = mock(AttributeInstance.class);
        final double[] base = { 0 };
        doAnswer(call ->
        {
            base[0] = call.getArgument(0, Double.class).doubleValue();
            return null;
        }).when(instance).setBaseValue(anyDouble());
        when(instance.getValue()).thenAnswer(call -> Double.valueOf(base[0]));
        ranges.put(stand, instance);
        spawned.add(stand);
        return stand;
    }

    /** An owned gate in a world, on the named network or Public for null. */
    private static Stargate gate(final String name, final UUID owner, final World world, final String network)
    {
        final Stargate gate = new Stargate();
        gate.setGateName(name);
        gate.setGateOwner(owner.toString());
        gate.setGatePlayerTeleportLocation(new Location(world, 5, 64, 5));
        if (network != null)
        {
            gate.setGateNetwork(StargateManager.addStargateNetwork(network));
        }
        StargateManager.registerStargate(gate);
        return gate;
    }

    /** A non-operator in this world holding exactly these nodes. */
    private Player player(final UUID id, final String... nodes)
    {
        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.getName()).thenReturn("pinger");
        when(player.isOp()).thenReturn(false);
        when(player.getWorld()).thenReturn(here);
        when(player.hasPermission(anyString())).thenReturn(false);
        for (final String node : nodes)
        {
            when(player.hasPermission(node)).thenReturn(true);
        }
        return player;
    }

    /** Someone who may ping and see every gate on Public. */
    private Player pinger()
    {
        return player(ALICE, "wormhole.use.ping", "wormhole.list");
    }

    private static void ping(final Player player, final String... args)
    {
        new Ping().onCommand(player, null, "ping", args);
    }

    private static String shown(final Player player)
    {
        final ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(player, atLeastOnce()).sendMessage(sent.capture());
        return String.join("\n", sent.getAllValues().stream().map(ChatText::plain).toList());
    }

    /** As shipped, ping-enabled is false: the command says so and spawns nothing. */
    @Test
    void pingIsOffUntilTheOperatorTurnsItOn()
    {
        gate("Abydos", BOB, here, null);
        final Player alice = pinger();

        ping(alice, "Abydos");

        assertTrue(shown(alice).contains("Pinging gates is turned off on this server."), shown(alice));
        assertTrue(spawned.isEmpty());
    }

    /** Before 1.21.6 there is no locator bar, and the player is pointed at the compass instead. */
    @Test
    void withNoLocatorBarThePlayerIsPointedAtTheCompass()
    {
        ConfigTestSupport.set(ConfigKeys.PING_ENABLED, true);
        PingTestSupport.locatorBar(false, ranges::get);
        gate("Abydos", BOB, here, null);
        final Player alice = pinger();

        ping(alice, "Abydos");

        final String text = shown(alice);
        assertTrue(text.contains("1.21.6") && text.contains("/wormhole compass"), text);
        assertTrue(spawned.isEmpty());
    }

    /** The bar shows only the player's own world, so a gate in another is named and not pinged. */
    @Test
    void aGateInAnotherWorldIsNamedAndNotPinged()
    {
        ConfigTestSupport.set(ConfigKeys.PING_ENABLED, true);
        gate("Chulak", BOB, elsewhere, null);
        final Player alice = pinger();

        ping(alice, "Chulak");

        assertTrue(shown(alice).contains("Chulak is in another world"), shown(alice));
        assertTrue(spawned.isEmpty());
    }

    /** A gate on a network the player is not admitted to is answered as a name that does not exist. */
    @Test
    void aGateOnAPrivateNetworkIsAnsweredAsMissing()
    {
        ConfigTestSupport.set(ConfigKeys.PING_ENABLED, true);
        gate("Secret", BOB, here, "tokra");
        final Player alice = pinger();

        ping(alice, "Secret");

        assertTrue(shown(alice).contains("No gate called Secret to ping."), shown(alice));
        assertTrue(spawned.isEmpty());
    }

    /** Without the list node a player may ping only their own gates, as completion offers them. */
    @Test
    void aGateThePlayerMayNotListIsAnsweredAsMissing()
    {
        ConfigTestSupport.set(ConfigKeys.PING_ENABLED, true);
        gate("Theirs", BOB, here, null);
        gate("Mine", ALICE, here, null);
        final Player alice = player(ALICE, "wormhole.use.ping", "wormhole.list.mine");

        ping(alice, "Theirs");
        assertTrue(shown(alice).contains("No gate called Theirs to ping."), shown(alice));
        assertTrue(spawned.isEmpty());

        ping(alice, "Mine");
        assertEquals(1, spawned.size(), "their own gate pings");
    }

    /** A gate the player may see is put on the bar, and they are told so. */
    @Test
    void aVisibleGateIsPingedAndThePlayerIsTold()
    {
        ConfigTestSupport.set(ConfigKeys.PING_ENABLED, true);
        gate("Abydos", BOB, here, null);
        final Player alice = pinger();

        ping(alice, "Abydos");

        assertEquals(1, spawned.size());
        assertTrue(shown(alice).contains("Abydos is on your locator bar"), shown(alice));
        assertEquals(1, held.pending(), "its expiry is waiting");
    }

    /** A second ping inside the cooldown is refused with the time left, and spawns nothing more. */
    @Test
    void aPingWithinTheCooldownIsRefusedWithTheTimeLeft()
    {
        ConfigTestSupport.set(ConfigKeys.PING_ENABLED, true);
        gate("Abydos", BOB, here, null);
        final Player alice = pinger();

        ping(alice, "Abydos");
        ping(alice, "Abydos");

        assertTrue(shown(alice).contains("You can ping again in 20 seconds."), shown(alice));
        assertEquals(1, spawned.size());
    }

    /** Without wormhole.use.ping the answer is the usual refusal, not the gate's whereabouts. */
    @Test
    void withoutThePingNodeThePlayerIsRefused()
    {
        ConfigTestSupport.set(ConfigKeys.PING_ENABLED, true);
        gate("Abydos", BOB, here, null);
        final Player alice = player(ALICE, "wormhole.list");

        ping(alice, "Abydos");

        assertTrue(shown(alice).contains("You lack the permissions to do this."), shown(alice));
        assertTrue(spawned.isEmpty());
    }

    /** Completion after ping offers the gates a ping would accept, and nothing while ping is off. */
    @Test
    void completionOffersOnlyTheGatesThePlayerMayPing()
    {
        gate("Abydos", BOB, here, null);
        gate("Secret", BOB, here, "tokra");
        final Player alice = pinger();
        final SubCommands.Entry entry = SubCommands.find("ping");

        assertTrue(entry.completeArgs(alice, new String[] { "ping", "" }).isEmpty(), "nothing while ping is off");

        ConfigTestSupport.set(ConfigKeys.PING_ENABLED, true);
        assertEquals(List.of("Abydos"), entry.completeArgs(alice, new String[] { "ping", "" }),
            "the private network's gate is not offered");
        PingTestSupport.locatorBar(false, ranges::get);
        assertTrue(entry.completeArgs(alice, new String[] { "ping", "" }).isEmpty(),
            "nothing on a server with no locator bar, where every ping would be refused");
        assertFalse(entry.completeArgs(mock(ConsoleCommandSender.class),
            new String[] { "ping", "" }).contains("Abydos"), "the console cannot ping");
    }
}
