package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.wormhole_xtreme.wormhole.model.GateSpatialIndex;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.StargateTestSupport;

/**
 * A wormhole runs one way, and these tests pin that.
 *
 * <p>Dialling makes the origin gate active with a target and the destination gate active
 * with none. Everything that moves things through a gate keys off having a target, so the
 * destination end is inert: it is an exit, not an entrance. That matters practically — a
 * gate dialled out of a base must not become a door hostile mobs can walk back in through.
 */
class GateOneWayTest
{
    private World world;

    @BeforeEach
    void setUp() throws Exception
    {
        GateSpatialIndex.clear();
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        PluginTestSupport.install(plugin);

        // markVehicleRecentlyTeleported schedules the un-mark, so the sweep needs a
        // scheduler or it dies before teleporting and every test passes vacuously.
        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        // Run delayed tasks inline so the next-tick velocity re-apply is observable here.
        when(scheduler.scheduleSyncDelayedTask(any(), any(Runnable.class), anyLong()))
            .thenAnswer(inv -> { inv.getArgument(1, Runnable.class).run(); return 1; });
        PluginTestSupport.scheduler(scheduler);

        world = mock(World.class);
        when(world.getName()).thenReturn("w");
    }

    @AfterEach
    void tearDown()
    {
        GateSpatialIndex.clear();
        PluginTestSupport.forgetAllGates();
    }

    /**
     * Builds a gate with a single portal block at the given coordinates.
     */
    private Stargate gateAt(final String name, final int x, final int y, final int z)
    {
        final Stargate gate = new Stargate();
        gate.setGateName(name);
        gate.setGateWorld(world);
        gate.setGateFacing(BlockFace.NORTH);
        gate.setGatePlayerTeleportLocation(new Location(world, x + 0.5, y, z + 0.5));
        gate.getGatePortalBlocks().add(new Location(world, x, y, z));
        gate.setGateActive(true);
        gate.setGatePortalOpen(true);
        return gate;
    }

    /**
     * A zombie standing at these coordinates, travelling north.
     *
     * <p>Took a gate as well until Sonar noticed nothing read it. Which gate it was standing
     * in is decided by the coordinates, so naming one alongside them said something the
     * method did not honour -- every caller passed the same 10, 64, 20 whether it named the
     * origin or the destination.
     */
    private Entity zombieIn(final int x, final int y, final int z)
    {
        final Zombie zombie = mock(Zombie.class);
        when(zombie.getUniqueId()).thenReturn(UUID.randomUUID());
        when(zombie.getLocation()).thenReturn(new Location(world, x + 0.5, y, z + 0.5));
        when(zombie.getPassengers()).thenReturn(Collections.<Entity>emptyList());
        when(zombie.isInsideVehicle()).thenReturn(false);
        when(zombie.isValid()).thenReturn(true);
        when(zombie.getVelocity()).thenReturn(new Vector(0, 0, -1.5));
        // A real server says whether it moved; an unstubbed mock would say it refused.
        when(zombie.teleport(any(Location.class))).thenReturn(true);
        when(world.getNearbyEntities(any(BoundingBox.class)))
            .thenReturn(Collections.<Entity>singletonList(zombie));
        return zombie;
    }

    @Test
    void aMobInTheDestinationGateIsNotSentBackUpTheWormhole()
    {
        // origin --> destination. The destination is active but has no target of its own.
        final Stargate destination = gateAt("destination", 10, 64, 20);
        StargateManager.registerStargate(destination);
        final Entity zombie = zombieIn(10, 64, 20);
        try
        {
            GateEntityScanner.create().run();
            verify(zombie, never()).teleport(any(Location.class));
        }
        finally
        {
            StargateManager.removeStargate(destination);
        }
    }

    @Test
    void aMobInTheOriginGateIsSentThrough()
    {
        // The mirror of the test above: with a target, the sweep does act, which is what
        // makes the previous test meaningful rather than vacuously green.
        final Stargate destination = gateAt("destination", 99, 70, 99);
        final Stargate origin = gateAt("origin", 10, 64, 20);
        StargateTestSupport.target(origin, destination);
        StargateManager.registerStargate(origin);
        final Entity zombie = zombieIn(10, 64, 20);
        try
        {
            GateEntityScanner.create().run();
            verify(zombie, atLeastOnce()).teleport(any(Location.class));
        }
        finally
        {
            StargateManager.removeStargate(origin);
        }
    }

    /** A gate still dialling has no wormhole yet, so a mob standing in its frame stays put. */
    @Test
    void aMobInAGateStillDiallingIsNotSentThrough()
    {
        final Stargate destination = gateAt("destination", 99, 70, 99);
        final Stargate origin = gateAt("origin", 10, 64, 20);
        StargateTestSupport.target(origin, destination);
        origin.setGatePortalOpen(false);
        StargateManager.registerStargate(origin);
        final Entity zombie = zombieIn(10, 64, 20);
        try
        {
            GateEntityScanner.create().run();
            verify(zombie, never()).teleport(any(Location.class));
        }
        finally
        {
            StargateManager.removeStargate(origin);
        }
    }

    @Test
    void anInactiveGateSendsNothingEitherWay()
    {
        final Stargate destination = gateAt("destination", 99, 70, 99);
        final Stargate origin = gateAt("origin", 10, 64, 20);
        StargateTestSupport.target(origin, destination);
        origin.setGateActive(false);
        StargateManager.registerStargate(origin);
        final Entity zombie = zombieIn(10, 64, 20);
        try
        {
            GateEntityScanner.create().run();
            verify(zombie, never()).teleport(any(Location.class));
        }
        finally
        {
            StargateManager.removeStargate(origin);
        }
    }

    @Test
    void aSweptEntityLeavesPointingOutOfTheDestinationGate()
    {
        // An arrow shot north into a gate used to arrive still travelling north, whichever
        // way the far gate faced — often straight back into its own frame.
        final Stargate destination = gateAt("destination", 99, 70, 99);
        destination.setGateFacing(BlockFace.EAST);
        final Stargate origin = gateAt("origin", 10, 64, 20);
        StargateTestSupport.target(origin, destination);
        StargateManager.registerStargate(origin);
        final Entity zombie = zombieIn(10, 64, 20);
        try
        {
            GateEntityScanner.create().run();

            final ArgumentCaptor<Vector> sent =
                ArgumentCaptor.forClass(Vector.class);
            // Set once immediately and once on the next tick: teleporting clears motion and
            // a same-tick velocity is routinely lost to it, which left arrows dropping.
            verify(zombie, times(2)).setVelocity(sent.capture());
            final Vector v = sent.getValue();

            assertTrue(v.getX() > 0, "should leave heading east, the way the far gate faces");
            assertEquals(0.0, v.getZ(), 1e-9, "the original northward component should be gone");
            // Speed is preserved; only the direction changes, so a slow entity stays slow.
            assertEquals(1.5, v.length(), 1e-6);
        }
        finally
        {
            StargateManager.removeStargate(origin);
        }
    }

    /**
     * A mob with a passenger is swept through with it on Paper 1.20.4.
     *
     * <p>1.20.4 will not teleport anything with a passenger, and the sweep ignored the answer,
     * so a ridden mob stayed in the portal, rider and all, and was marked as having gone (#506).
     */
    @Test
    void aSweptMobCarryingAnotherArrivesWithItsPassengerAboard()
    {
        final Stargate destination = gateAt("destination", 99, 70, 99);
        final Stargate origin = gateAt("origin", 10, 64, 20);
        StargateTestSupport.target(origin, destination);
        StargateManager.registerStargate(origin);
        final Entity zombie = zombieIn(10, 64, 20);
        final Chicken chicken = mock(Chicken.class);
        when(chicken.getUniqueId()).thenReturn(UUID.randomUUID());
        when(chicken.isValid()).thenReturn(true);
        when(chicken.teleport(any(Location.class))).thenReturn(true);
        final Paper1204Riding.Stack stack = Paper1204Riding.refusesWhileRidden(
            zombie, new Location(world, 10.5, 64, 20.5), chicken);
        try
        {
            GateEntityScanner.create().run();

            assertEquals(99.5, stack.at().getX(), 1.5, "the mob must reach the far gate");
            verify(chicken, atLeastOnce()).teleport(any(Location.class));
            assertTrue(stack.carries(chicken), "its passenger rides on at the far end");
        }
        finally
        {
            StargateManager.removeStargate(origin);
        }
    }

    /**
     * A mount with a player aboard is left to the player listener, which asks permission,
     * cooldown and fare first.
     *
     * <p>Once the sweep could carry a whole stack, a rider the player listener had turned away
     * (sitting in the portal on their horse) was swept through on the next scan, past every
     * check that had just refused them.
     */
    @Test
    void aMountWithAPlayerAboardIsNotSwept()
    {
        final Stargate destination = gateAt("destination", 99, 70, 99);
        final Stargate origin = gateAt("origin", 10, 64, 20);
        StargateTestSupport.target(origin, destination);
        StargateManager.registerStargate(origin);
        final Entity horse = zombieIn(10, 64, 20);
        final Player rider = mock(Player.class);
        when(rider.getUniqueId()).thenReturn(UUID.randomUUID());
        when(rider.teleport(any(Location.class))).thenReturn(true);
        final Paper1204Riding.Stack stack = Paper1204Riding.refusesWhileRidden(
            horse, new Location(world, 10.5, 64, 20.5), rider);
        try
        {
            GateEntityScanner.create().run();

            assertEquals(10.5, stack.at().getX(), 0.001, "the horse must stay where the player listener left it");
            verify(rider, never()).teleport(any(Location.class));
            assertTrue(stack.carries(rider));
        }
        finally
        {
            StargateManager.removeStargate(origin);
        }
    }

    /**
     * A passenger whose own teleport fails is fetched to its mount, not left at the source.
     *
     * <p>The sweep used to re-seat once, on the spot, with no retry: a passenger that had not
     * landed beside its mount was refused its seat and stayed behind for good.
     */
    @Test
    void aSweptPassengerWhoseTeleportFailsIsFetchedToItsMount()
    {
        final Stargate destination = gateAt("destination", 99, 70, 99);
        final Stargate origin = gateAt("origin", 10, 64, 20);
        StargateTestSupport.target(origin, destination);
        StargateManager.registerStargate(origin);
        final Entity zombie = zombieIn(10, 64, 20);
        final Chicken chicken = mock(Chicken.class);
        when(chicken.getUniqueId()).thenReturn(UUID.randomUUID());
        when(chicken.isValid()).thenReturn(true);
        final Paper1204Riding.Stack stack = Paper1204Riding.refusesWhileRidden(
            zombie, new Location(world, 10.5, 64, 20.5), chicken);
        final Location[] chickenAt = { new Location(world, 10.5, 65, 20.5) };
        when(chicken.teleport(any(Location.class)))
            .thenThrow(new IllegalStateException("not this tick"))
            .thenAnswer(call ->
            {
                chickenAt[0] = call.getArgument(0);
                return true;
            });
        // Where it stands decides whether the zombie will seat it: not from the far side of a gate.
        when(chicken.getLocation()).thenAnswer(call -> chickenAt[0]);
        try
        {
            GateEntityScanner.create().run();

            assertTrue(chickenAt[0].getX() > 90, "the chicken must be fetched to the far gate");
            assertTrue(stack.carries(chicken), "and ride on there");
        }
        finally
        {
            StargateManager.removeStargate(origin);
        }
    }

    /**
     * An item dropped into an opening is falling when the sweep finds it, since the opening is
     * air. Its fall came out of the far gate as a sideways throw that put it further off the
     * slower the server, which is what failed the 1.20.4 travel boot test on a busy runner.
     */
    @Test
    void anEntityFallingThroughAGateLeavesWithOnlyTheSpeedItHadThroughIt()
    {
        final Stargate destination = gateAt("destination", 99, 70, 99);
        destination.setGateFacing(BlockFace.EAST);
        final Stargate origin = gateAt("origin", 10, 64, 20);
        StargateTestSupport.target(origin, destination);
        StargateManager.registerStargate(origin);
        final Entity falling = zombieIn(10, 64, 20);
        when(falling.getVelocity()).thenReturn(new Vector(0.3, -0.4, -0.2));
        try
        {
            GateEntityScanner.create().run();

            final ArgumentCaptor<Vector> sent =
                ArgumentCaptor.forClass(Vector.class);
            verify(falling, times(2)).setVelocity(sent.capture());
            final Vector v = sent.getValue();
            // 0.2 north into a north-facing gate; the fall and the drift along it are dropped.
            assertEquals(0.2, v.getX(), 1e-9, "should leave east at the 0.2 it was moving through the gate");
            assertEquals(0.0, v.getY(), 1e-9);
            assertEquals(0.0, v.getZ(), 1e-9);
        }
        finally
        {
            StargateManager.removeStargate(origin);
        }
    }

    /**
     * A floor gate still faces the way its DHD does, not up -- Stargate only reads its flat
     * portal as "leave upward" -- so a fall into one is dropped as it is at a wall, and an item
     * dropped through comes out at rest instead of thrown along the far gate's facing.
     */
    @Test
    void aFallThroughAFloorGateIsDroppedToo()
    {
        final Vector through = GateEntityScanner.throughGate(
            new Vector(0, -0.6, 0), BlockFace.NORTH);
        assertEquals(0.0, through.length(), 1e-9);
    }

    /** A gate with no way it faces cannot say what is through it, so the whole velocity is kept. */
    @Test
    void aGateWithNoFacingKeepsTheWholeVelocity()
    {
        final Vector incoming = new Vector(0.3, -0.4, -0.2);
        assertEquals(incoming, GateEntityScanner.throughGate(incoming, null));
        assertEquals(incoming, GateEntityScanner.throughGate(incoming, BlockFace.SELF));
    }

    @Test
    void anEntityAtRestIsNotGivenANextTickReapply()
    {
        // A dropped item sitting in the portal has no momentum to preserve, so it must not
        // cost a scheduled task each time it is swept.
        final Stargate destination = gateAt("destination", 99, 70, 99);
        final Stargate origin = gateAt("origin", 10, 64, 20);
        try
        {
            StargateTestSupport.target(origin, destination);
        }
        catch (final Exception e)
        {
            throw new IllegalStateException(e);
        }
        StargateManager.registerStargate(origin);
        final Entity item = zombieIn(10, 64, 20);
        when(item.getVelocity()).thenReturn(new Vector(0, 0, 0));
        try
        {
            GateEntityScanner.create().run();
            verify(item, times(1)).setVelocity(any(Vector.class));
        }
        finally
        {
            StargateManager.removeStargate(origin);
        }
    }

    @Test
    void dialAssignsATargetToTheOriginOnly()
    {
        // The property every other path relies on: only one end of a connection ever holds
        // a target, so only one end can send anything.
        final Stargate origin = gateAt("origin", 10, 64, 20);
        final Stargate destination = gateAt("destination", 99, 70, 99);

        assertNull(origin.getGateTarget(), "a freshly built gate has no target");
        assertNull(destination.getGateTarget(), "a freshly built gate has no target");
    }
}
