package com.wormhole_xtreme.wormhole.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.GateSpatialIndex;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * The expansion turns a player into the plain values the arithmetic needs.
 *
 * <p>{@link PlaceholderValuesTest} covers what each placeholder answers. This covers the part
 * that cannot live there: pulling a UUID, a name and a position off whichever kind of player
 * PlaceholderAPI hands over, which is where a placeholder that works for somebody standing in
 * the world and breaks for somebody who logged off would come from.
 *
 * <p>PlaceholderAPI is a provided dependency, so it is on the test classpath and the expansion
 * can simply be constructed here. Registering it cannot be tested without a running
 * PlaceholderAPI plugin, and that is what {@link PlaceholderSupport} exists to survive.
 */
class WormholePlaceholdersTest
{
    private static final UUID ALICE = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private WormholePlaceholders expansion;

    /** Held here: a Location keeps its world weakly, so an inline mock can be collected mid-test. */
    private World world;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        GateSpatialIndex.clear();
        drain();
        world = mock(World.class);
        expansion = new WormholePlaceholders();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        drain();
        GateSpatialIndex.clear();
        PluginTestSupport.remove();
    }

    /** See PlaceholderValuesTest: the registry and the open set are static and shared. */
    private static void drain()
    {
        for (final Stargate existing
            : new java.util.ArrayList<>(StargateManager.getAllGatesUnsorted()))
        {
            existing.setGateActive(false);
            StargateManager.removeStargate(existing);
        }
        for (final Stargate open : new java.util.ArrayList<>(StargateManager.getOpenGates()))
        {
            open.setGateActive(false);
        }
    }

    private Stargate gate(final String name, final String owner, final int x)
    {
        final Stargate gate = new Stargate();
        gate.setGateName(name);
        gate.setGateWorld(world);
        gate.setGateOwner(owner);
        gate.setGatePlayerTeleportLocation(new Location(world, x, 64, 0));
        gate.getGatePortalBlocks().add(new Location(world, x, 64, 0));
        StargateManager.registerStargate(gate);
        return gate;
    }

    @Test
    void theExpansionIdentifiesItselfAsWormhole()
    {
        // The identifier is the %wormhole_ prefix. Changing it silently breaks every
        // scoreboard and tab list on every server already using it.
        assertEquals("wormhole", expansion.getIdentifier());
    }

    @Test
    void theExpansionReportsThisPluginsOwnVersionAndAuthor()
    {
        // PlaceholderAPI shows both in /papi info. The version has to come from the plugin
        // rather than be typed here, or every release would report whatever number somebody
        // last remembered to change.
        final org.bukkit.plugin.PluginDescriptionFile description =
            mock(org.bukkit.plugin.PluginDescriptionFile.class);
        when(description.getVersion()).thenReturn("9.9.9");
        when(WormholeXTreme.getThisPlugin().getDescription()).thenReturn(description);

        assertEquals("9.9.9", expansion.getVersion(),
            "the expansion should report the version the plugin was built as");
        assertEquals("Khan Jal", expansion.getAuthor());
    }

    @Test
    void theExpansionStaysRegisteredAcrossAPlaceholderApiReload()
    {
        // Without this, /papi reload drops the expansion and only one downloaded from
        // PlaceholderAPI's own cloud would come back. Ours arrives with the plugin, so
        // nothing would re-register it until the next server restart.
        assertTrue(expansion.persist(), "the expansion must survive a PlaceholderAPI reload");
    }

    @Test
    void anOnlinePlayerGetsTheirPositionUsedForTheNearestGate()
    {
        gate("far", ALICE.toString(), 500);
        gate("near", ALICE.toString(), 5);

        final Player online = mock(Player.class);
        when(online.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        final OfflinePlayer player = mock(OfflinePlayer.class);
        when(player.getUniqueId()).thenReturn(ALICE);
        when(player.getName()).thenReturn("alice");
        when(player.getPlayer()).thenReturn(online);

        assertEquals("near", expansion.onRequest(player, "nearest_gate"));
    }

    @Test
    void aPlayerWhoHasLoggedOffStillGetsTheCountsButNotTheNearestGate()
    {
        // getPlayer() returns null once somebody is offline, so there is no position to
        // measure from -- but their UUID and name are still known, which is what the two
        // counts need. Answering neither would make the whole expansion useless on a tab
        // list that renders offline players.
        gate("theirs", ALICE.toString(), 0);

        final OfflinePlayer player = mock(OfflinePlayer.class);
        when(player.getUniqueId()).thenReturn(ALICE);
        when(player.getName()).thenReturn("alice");
        when(player.getPlayer()).thenReturn(null);

        assertEquals("1", expansion.onRequest(player, "gates_owned"),
            "their gates are still theirs while they are away");
        assertEquals("1", expansion.onRequest(player, "gates_total"));
        assertEquals("", expansion.onRequest(player, "nearest_gate"),
            "but there is nowhere to measure from");
    }

    @Test
    void aRequestWithNoPlayerAtAllStillAnswersTheServerWideCounts()
    {
        // PlaceholderAPI resolves with a null player for a console broadcast. The counts
        // still mean something; the per-player one must not throw.
        gate("alpha", ALICE.toString(), 0);

        assertEquals("1", expansion.onRequest(null, "gates_total"));
        assertEquals("0", expansion.onRequest(null, "gates_owned"));
        assertEquals("", expansion.onRequest(null, "nearest_gate"));
    }

    @Test
    void aPlaceholderThisExpansionDoesNotOwnComesBackUntouched()
    {
        assertNull(expansion.onRequest(null, "something_else"));
    }

    @Test
    void registeringWithoutARunningPlaceholderApiIsSurvivedRatherThanThrown()
    {
        // There is no PlaceholderAPI plugin here, only its classes, so register() cannot
        // succeed. The point is that it fails quietly: a placeholder that cannot register is
        // not a reason to take the rest of the plugin down with it.
        PlaceholderSupport.reset();

        PlaceholderSupport.enablePlaceholders();

        assertFalse(PlaceholderSupport.isRegistered(),
            "nothing should claim to be registered without PlaceholderAPI running");
    }
}
