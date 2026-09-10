package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;

/**
 * What the proximity guards and the incoming-connection check actually measure.
 *
 * <p>Both were walking every gate on the server to answer a question about the handful next
 * to somebody, or the handful that were lit. The distance measurement underneath the first of
 * them also had no idea what a world was.
 */
class GateProximityScanTest
{
    private World overworld;
    private World nether;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install();
        clearGates();
        overworld = mock(World.class);
        when(overworld.getName()).thenReturn("world");
        nether = mock(World.class);
        when(nether.getName()).thenReturn("world_nether");
    }

    @AfterEach
    void tearDown() throws Exception
    {
        clearGates();
        GateSpatialIndex.clear();
        PluginTestSupport.remove();
    }

    private static void clearGates()
    {
        try (MockedStatic<StargateDBManager> db = mockStatic(StargateDBManager.class))
        {
            for (final Stargate s : new ArrayList<>(StargateManager.getAllGates()))
            {
                if (s != null)
                {
                    StargateManager.removeStargate(s);
                }
            }
        }
    }

    /** A registered gate with one structure block at the given place. */
    private Stargate gateAt(final String name, final World world, final int x, final int y, final int z)
    {
        final Stargate gate = new Stargate();
        gate.setGateName(name);
        gate.setGateWorld(world);
        gate.getGateStructureBlocks().add(new Location(world, x, y, z));
        StargateManager.registerStargate(gate);
        return gate;
    }

    /**
     * A gate in another world is not nearby, however well its coordinates line up.
     *
     * <p>The distance measurement compared three coordinates and ignored the world entirely,
     * so a gate standing at x/y/z in the Nether measured as zero blocks from somebody standing
     * at the same x/y/z in the Overworld. What reads this is the guard that stops a lava gate
     * setting fire to what is beside it, so the consequence was that guard firing on the wrong
     * side of a portal -- suppressing a player's fire damage because of a gate in a different
     * dimension.
     */
    @Test
    void aGateInAnotherWorldIsNotZeroBlocksAway()
    {
        final Stargate netherGate = gateAt("nethergate", nether, 100, 64, 100);

        final double sameCoordinatesOtherWorld = StargateManager.distanceSquaredToClosestGateBlock(
            new Location(overworld, 100, 64, 100), netherGate);

        assertEquals(Double.MAX_VALUE, sameCoordinatesOtherWorld,
            "identical coordinates in a different world must not measure as touching");
    }

    /** And a gate in the same world still measures normally. */
    @Test
    void aGateInTheSameWorldStillMeasuresByCoordinates()
    {
        final Stargate gate = gateAt("near", overworld, 100, 64, 100);

        assertEquals(9.0, StargateManager.distanceSquaredToClosestGateBlock(
            new Location(overworld, 103, 64, 100), gate), 1e-9,
            "three blocks east is nine squared blocks away");
    }

    /**
     * An open gate dialled into another one counts as an incoming connection.
     *
     * <p>This is what tells an arrival gate it is the far end of somebody's wormhole rather
     * than a gate that was lit and walked away from.
     */
    @Test
    void anOpenGateDialledIntoAnotherIsAnIncomingConnection()
    {
        final Stargate origin = gateAt("origin", overworld, 0, 64, 0);
        final Stargate destination = gateAt("destination", overworld, 50, 64, 50);
        StargateTestSupport.target(origin, destination);
        origin.setGateActive(true);

        assertTrue(StargateManager.hasIncomingConnection(destination, null),
            "the destination is the far end of an open wormhole");
        assertFalse(StargateManager.hasIncomingConnection(destination, origin),
            "and the gate doing the dialling can be disregarded, which is what dialling needs"
                + " so a gate does not count itself as blocking its own target");
    }

    /**
     * A gate that is not open is dialled nowhere.
     *
     * <p>The check reads the open-gate set rather than filtering every gate, and this is the
     * property that makes those the same answer: a target left set on a gate that has since
     * shut down is not a connection.
     */
    @Test
    void aClosedGateIsNotAnIncomingConnectionEvenWithATargetSet()
    {
        final Stargate origin = gateAt("origin", overworld, 0, 64, 0);
        final Stargate destination = gateAt("destination", overworld, 50, 64, 50);
        StargateTestSupport.target(origin, destination);
        origin.setGateActive(true);
        origin.setGateActive(false);

        assertFalse(StargateManager.hasIncomingConnection(destination, null),
            "a gate that is not lit has dialled nowhere, whatever its target still says");
    }

    /**
     * A gate the registry has never heard of does not count either.
     *
     * <p>The open set follows the active flag alone, so it can hold a gate that was built and
     * lit without ever being registered -- one still being detected. The callers here were
     * filtering the registry before and must go on excluding those.
     */
    @Test
    void anUnregisteredGateIsNotAnIncomingConnection()
    {
        final Stargate destination = gateAt("destination", overworld, 50, 64, 50);

        final Stargate stray = new Stargate();
        stray.setGateName("stray");
        stray.setGateWorld(overworld);
        StargateTestSupport.target(stray, destination);
        stray.setGateActive(true);
        try
        {
            assertFalse(StargateManager.hasIncomingConnection(destination, null),
                "a gate the server does not have cannot be dialled into anything");
        }
        finally
        {
            stray.setGateActive(false);
        }
    }
}
