package com.wormhole_xtreme.wormhole.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * What each {@code %wormhole_...%} placeholder answers.
 *
 * <p>These run without PlaceholderAPI and without a server, which is the point of the split:
 * {@link WormholePlaceholders} extends a PlaceholderAPI type and cannot be loaded unless it is
 * installed, so all the arithmetic lives in {@link PlaceholderValues} where it can be pinned.
 *
 * <p>A placeholder is rebuilt every time a scoreboard or tab list refreshes, so a wrong answer
 * here is not a wrong line once -- it is a wrong line several times a second, on every
 * player's screen.
 */
class PlaceholderValuesTest
{
    private static final String ALICE_ID = "11111111-2222-3333-4444-555555555555";

    /** Held here: a Location keeps its world weakly, so an inline mock can be collected mid-test. */
    private World world;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        PluginTestSupport.forgetAllGates();
        world = mock(World.class);
        when(world.getName()).thenReturn("w");
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PluginTestSupport.forgetAllGates();
        PluginTestSupport.remove();
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
    void theTotalCountsEveryGate()
    {
        gate("alpha", ALICE_ID, 0);
        gate("bravo", ALICE_ID, 10);

        assertEquals("2", PlaceholderValues.resolve("gates_total", ALICE_ID, "alice", null));
    }

    @Test
    void theOpenCountIsOnlyTheGatesWithAWormholeUp()
    {
        gate("alpha", ALICE_ID, 0);
        final Stargate open = gate("bravo", ALICE_ID, 10);
        open.setGateActive(true);

        assertEquals("2", PlaceholderValues.resolve("gates_total", ALICE_ID, "alice", null),
            "both gates still exist");
        assertEquals("1", PlaceholderValues.resolve("gates_open", ALICE_ID, "alice", null),
            "but only one of them is open");
    }

    @Test
    void ownedCountsTheAskingPlayersGatesAndNobodyElses()
    {
        gate("alpha", ALICE_ID, 0);
        gate("bravo", ALICE_ID, 10);
        gate("charlie", "99999999-8888-7777-6666-555555555555", 20);

        assertEquals("2", PlaceholderValues.resolve("gates_owned", ALICE_ID, "alice", null),
            "two of the three are alice's");
    }

    @Test
    void aGateOwnedByNameStillCountsForTheSamePlayer()
    {
        // Gates built before ownership moved to UUIDs record a plain player name. Matching
        // only the UUID would tell a player who has been on the server for years that they
        // own none of the gates they built.
        gate("legacy", "alice", 0);
        gate("modern", ALICE_ID, 10);

        assertEquals("2", PlaceholderValues.resolve("gates_owned", ALICE_ID, "alice", null),
            "the legacy name and the UUID are the same player");
    }

    @Test
    void anotherPlayersLegacyGateIsNotCountedAsYours()
    {
        // The control for the test above: matching on name as well as UUID must not turn into
        // matching on anything at all.
        gate("theirs", "bob", 0);

        assertEquals("0", PlaceholderValues.resolve("gates_owned", ALICE_ID, "alice", null));
    }

    @Test
    void theNearestGateIsTheOneActuallyNearest()
    {
        gate("far", ALICE_ID, 500);
        gate("near", ALICE_ID, 5);

        assertEquals("near", PlaceholderValues.resolve("nearest_gate", ALICE_ID, "alice",
            new Location(world, 0, 64, 0)));
    }

    @Test
    void theNearestGateIsEmptyForSomebodyWhoIsNotOnline()
    {
        // An OfflinePlayer has a name and a UUID but no position. The two counts can still be
        // answered for them; this one cannot, and an empty string says so without leaving the
        // raw placeholder text on somebody's scoreboard.
        gate("alpha", ALICE_ID, 0);

        assertEquals("", PlaceholderValues.resolve("nearest_gate", ALICE_ID, "alice", null));
    }

    @Test
    void theNearestGateIsEmptyWhenThereAreNoGatesAtAll()
    {
        assertEquals("", PlaceholderValues.resolve("nearest_gate", ALICE_ID, "alice",
            new Location(world, 0, 64, 0)));
    }

    @Test
    void aPlaceholderThisPluginDoesNotOwnIsLeftAlone()
    {
        // Null rather than empty, so PlaceholderAPI leaves the text exactly as it found it.
        // Returning "" would blank out a typo, and an operator would see a missing line
        // rather than the placeholder they mistyped.
        assertNull(PlaceholderValues.resolve("gates_totl", ALICE_ID, "alice", null),
            "a near miss should come back untouched, not blank");
        assertNull(PlaceholderValues.resolve(null, ALICE_ID, "alice", null));
    }

    @Test
    void theCountsAreAnsweredForAPlayerWithNoIdentityAtAll()
    {
        // PlaceholderAPI resolves placeholders with no player in contexts like a console
        // broadcast. The server-wide counts still mean something there; the per-player one
        // does not, and must not throw.
        gate("alpha", ALICE_ID, 0);

        assertEquals("1", PlaceholderValues.resolve("gates_total", null, null, null));
        assertEquals("0", PlaceholderValues.resolve("gates_owned", null, null, null));
    }
}
