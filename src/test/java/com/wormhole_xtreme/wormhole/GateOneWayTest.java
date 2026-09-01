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
import org.bukkit.entity.Entity;
import org.bukkit.entity.Zombie;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.model.GateSpatialIndex;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * A wormhole runs one way, and these tests pin that.
 *
 * <p>Dialling makes the origin gate active with a target and the destination gate active
 * with none. Everything that moves things through a gate keys off having a target, so the
 * destination end is inert: it is an exit, not an entrance. That matters practically — a
 * gate dialled out of a base must not become a door hostile mobs can walk back in through.
 */
public class GateOneWayTest
{
    private World world;

    @BeforeEach
    public void setUp() throws Exception
    {
        GateSpatialIndex.clear();
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        final java.lang.reflect.Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, plugin);

        // markVehicleRecentlyTeleported schedules the un-mark, so the sweep needs a
        // scheduler or it dies before teleporting and every test passes vacuously.
        final org.bukkit.scheduler.BukkitScheduler scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
        // Run delayed tasks inline so the next-tick velocity re-apply is observable here.
        when(scheduler.scheduleSyncDelayedTask(any(), any(Runnable.class), anyLong()))
            .thenAnswer(inv -> { inv.getArgument(1, Runnable.class).run(); return 1; });
        final java.lang.reflect.Field sf = WormholeXTreme.class.getDeclaredField("scheduler");
        sf.setAccessible(true);
        sf.set(null, scheduler);

        world = mock(World.class);
        when(world.getName()).thenReturn("w");
    }

    @AfterEach
    public void tearDown()
    {
        GateSpatialIndex.clear();
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
        return gate;
    }

    private static void setTarget(final Stargate gate, final Stargate target) throws Exception
    {
        final java.lang.reflect.Field f = Stargate.class.getDeclaredField("gateTarget");
        f.setAccessible(true);
        f.set(gate, target);
    }

    /** A zombie standing in the given gate's portal block. */
    private Entity zombieIn(final Stargate gate, final int x, final int y, final int z)
    {
        final Zombie zombie = mock(Zombie.class);
        when(zombie.getUniqueId()).thenReturn(UUID.randomUUID());
        when(zombie.getLocation()).thenReturn(new Location(world, x + 0.5, y, z + 0.5));
        when(zombie.getPassengers()).thenReturn(Collections.<Entity>emptyList());
        when(zombie.isInsideVehicle()).thenReturn(false);
        when(zombie.isValid()).thenReturn(true);
        when(zombie.getVelocity()).thenReturn(new org.bukkit.util.Vector(0, 0, -1.5));
        when(world.getNearbyEntities(any(BoundingBox.class)))
            .thenReturn(Collections.<Entity>singletonList(zombie));
        return zombie;
    }

    @Test
    public void aMobInTheDestinationGateIsNotSentBackUpTheWormhole()
    {
        // origin --> destination. The destination is active but has no target of its own.
        final Stargate destination = gateAt("destination", 10, 64, 20);
        StargateManager.registerStargate(destination);
        final Entity zombie = zombieIn(destination, 10, 64, 20);
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
    public void aMobInTheOriginGateIsSentThrough() throws Exception
    {
        // The mirror of the test above: with a target, the sweep does act, which is what
        // makes the previous test meaningful rather than vacuously green.
        final Stargate destination = gateAt("destination", 99, 70, 99);
        final Stargate origin = gateAt("origin", 10, 64, 20);
        setTarget(origin, destination);
        StargateManager.registerStargate(origin);
        final Entity zombie = zombieIn(origin, 10, 64, 20);
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

    @Test
    public void anInactiveGateSendsNothingEitherWay() throws Exception
    {
        final Stargate destination = gateAt("destination", 99, 70, 99);
        final Stargate origin = gateAt("origin", 10, 64, 20);
        setTarget(origin, destination);
        origin.setGateActive(false);
        StargateManager.registerStargate(origin);
        final Entity zombie = zombieIn(origin, 10, 64, 20);
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
    public void aSweptEntityLeavesPointingOutOfTheDestinationGate() throws Exception
    {
        // An arrow shot north into a gate used to arrive still travelling north, whichever
        // way the far gate faced — often straight back into its own frame.
        final Stargate destination = gateAt("destination", 99, 70, 99);
        destination.setGateFacing(BlockFace.EAST);
        final Stargate origin = gateAt("origin", 10, 64, 20);
        setTarget(origin, destination);
        StargateManager.registerStargate(origin);
        final Entity zombie = zombieIn(origin, 10, 64, 20);
        try
        {
            GateEntityScanner.create().run();

            final org.mockito.ArgumentCaptor<org.bukkit.util.Vector> sent =
                org.mockito.ArgumentCaptor.forClass(org.bukkit.util.Vector.class);
            // Set once immediately and once on the next tick: teleporting clears motion and
            // a same-tick velocity is routinely lost to it, which left arrows dropping.
            verify(zombie, times(2)).setVelocity(sent.capture());
            final org.bukkit.util.Vector v = sent.getValue();

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

    @Test
    public void anEntityAtRestIsNotGivenANextTickReapply()
    {
        // A dropped item sitting in the portal has no momentum to preserve, so it must not
        // cost a scheduled task each time it is swept.
        final Stargate destination = gateAt("destination", 99, 70, 99);
        final Stargate origin = gateAt("origin", 10, 64, 20);
        try
        {
            setTarget(origin, destination);
        }
        catch (final Exception e)
        {
            throw new IllegalStateException(e);
        }
        StargateManager.registerStargate(origin);
        final Entity item = zombieIn(origin, 10, 64, 20);
        when(item.getVelocity()).thenReturn(new org.bukkit.util.Vector(0, 0, 0));
        try
        {
            GateEntityScanner.create().run();
            verify(item, times(1)).setVelocity(any(org.bukkit.util.Vector.class));
        }
        finally
        {
            StargateManager.removeStargate(origin);
        }
    }

    @Test
    public void dialAssignsATargetToTheOriginOnly()
    {
        // The property every other path relies on: only one end of a connection ever holds
        // a target, so only one end can send anything.
        final Stargate origin = gateAt("origin", 10, 64, 20);
        final Stargate destination = gateAt("destination", 99, 70, 99);

        assertNull(origin.getGateTarget(), "a freshly built gate has no target");
        assertNull(destination.getGateTarget(), "a freshly built gate has no target");
    }
}
