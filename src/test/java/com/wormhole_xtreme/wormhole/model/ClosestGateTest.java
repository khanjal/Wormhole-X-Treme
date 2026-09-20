package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * What {@link StargateManager#findClosestStargate} answers, pinned before it is made cheaper.
 *
 * <p>It had no tests at all and three callers -- the compass, the entity listener, and the
 * {@code %wormhole_nearest_gate%} placeholder -- so every one of its answers here was written
 * against the original implementation and run against it before anything was reshaped. That is
 * what makes "no behaviour change" a measurement rather than a claim.
 *
 * <p>The tie is the case worth naming. The original walked {@code getAllGates()}, which returns
 * the gates sorted by name, and kept a strictly closer gate only -- so of two gates exactly the
 * same distance away, the alphabetically first won. Nothing documented that, but the compass
 * points somewhere, and it should keep pointing at the same somewhere.
 */
class ClosestGateTest
{
    /** Held: a Location keeps its World weakly, so an inline mock can be collected mid-test. */
    private World world;
    private World nether;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        PluginTestSupport.forgetAllGates();
        world = mock(World.class);
        when(world.getName()).thenReturn("world");
        nether = mock(World.class);
        when(nether.getName()).thenReturn("nether");
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PluginTestSupport.forgetAllGates();
        PluginTestSupport.remove();
    }

    private Stargate gateAt(final String name, final World in, final int x)
    {
        final Stargate gate = new Stargate();
        gate.setGateName(name);
        gate.setGateWorld(in);
        gate.setGatePlayerTeleportLocation(new Location(in, x, 64, 0));
        StargateManager.registerStargate(gate);
        return gate;
    }

    @Test
    void theNearestGateIsTheOneWithTheSmallestDistance()
    {
        gateAt("far", world, 500);
        final Stargate near = gateAt("near", world, 5);
        gateAt("middling", world, 50);

        assertSame(near, StargateManager.findClosestStargate(new Location(world, 0, 64, 0)));
    }

    @Test
    void aGateInAnotherWorldIsNeverTheNearest()
    {
        // getSquaredDistance answers MAX_VALUE across worlds, so a gate at the same
        // coordinates in the Nether must not measure as zero blocks away.
        gateAt("nether_gate", nether, 0);
        final Stargate here = gateAt("overworld_gate", world, 900);

        assertSame(here, StargateManager.findClosestStargate(new Location(world, 0, 64, 0)),
            "the far gate in this world beats the near one in another");
    }

    @Test
    void thereIsNoNearestGateWhenEveryGateIsInAnotherWorld()
    {
        gateAt("nether_gate", nether, 0);

        assertNull(StargateManager.findClosestStargate(new Location(world, 0, 64, 0)),
            "nothing in this world is not the same as something far away");
    }

    @Test
    void ofTwoGatesTheSameDistanceAwayTheAlphabeticallyFirstWins()
    {
        // The tie-break the sorted walk gave for free. Both are 10 blocks away.
        gateAt("zulu", world, 10);
        gateAt("alpha", world, -10);

        final Stargate closest = StargateManager.findClosestStargate(new Location(world, 0, 64, 0));
        assertEquals("alpha", closest.getGateName(),
            "a tie has to resolve the same way every time, or the compass wanders between two gates");
    }

    @Test
    void theTieBreakIgnoresCaseTheWayTheSortDid()
    {
        // compareToIgnoreCase, not compareTo: "Beta" sorts before "alpha" under the latter.
        gateAt("alpha", world, 10);
        gateAt("Beta", world, -10);

        assertEquals("alpha",
            StargateManager.findClosestStargate(new Location(world, 0, 64, 0)).getGateName());
    }

    @Test
    void aGateWithNowhereToArriveIsSkipped()
    {
        final Stargate noExit = new Stargate();
        noExit.setGateName("unfinished");
        noExit.setGateWorld(world);
        StargateManager.registerStargate(noExit);
        final Stargate real = gateAt("finished", world, 800);

        assertSame(real, StargateManager.findClosestStargate(new Location(world, 0, 64, 0)),
            "a gate with no teleport location measures as unreachably far, not as here");
    }

    @Test
    void thereIsNoNearestGateWithoutAPlaceToMeasureFrom()
    {
        gateAt("alpha", world, 0);

        assertNull(StargateManager.findClosestStargate(null));
    }

    @Test
    void thereIsNoNearestGateWhenThereAreNoGates()
    {
        assertNull(StargateManager.findClosestStargate(new Location(world, 0, 64, 0)));
    }
}
