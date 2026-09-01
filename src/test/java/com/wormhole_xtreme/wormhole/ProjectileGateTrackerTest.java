package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.model.GateSpatialIndex;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * Catching a projectile at the moment it reaches a gate.
 *
 * <p>The periodic sweep cannot do this. Portal blocks are air, so an arrow crosses the ring
 * in about one tick and carries on until it hits something — usually past the gate. Polling
 * every twenty ticks nearly always misses the crossing, and when it did catch one the arrow
 * had already landed and stopped, which is what made arrows trickle out of the destination.
 */
public class ProjectileGateTrackerTest
{
    private World world;
    private Stargate origin;
    private Arrow arrow;
    private Arrow spawned;
    private Runnable ticker;

    private static final int BX = 10, BY = 64, BZ = 20;

    @BeforeEach
    public void setUp() throws Exception
    {
        GateSpatialIndex.clear();
        ProjectileGateTracker.clear();

        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        final java.lang.reflect.Field pf = WormholeXTreme.class.getDeclaredField("thisPlugin");
        pf.setAccessible(true);
        pf.set(null, plugin);

        final org.bukkit.scheduler.BukkitScheduler scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
        when(scheduler.scheduleSyncDelayedTask(any(), any(Runnable.class), anyLong()))
            .thenAnswer(inv -> { inv.getArgument(1, Runnable.class).run(); return 1; });
        final java.lang.reflect.Field sf = WormholeXTreme.class.getDeclaredField("scheduler");
        sf.setAccessible(true);
        sf.set(null, scheduler);

        world = mock(World.class);
        when(world.getName()).thenReturn("w");

        final Block portal = mock(Block.class);
        when(portal.getLocation()).thenReturn(new Location(world, BX, BY, BZ));
        when(portal.getWorld()).thenReturn(world);
        when(portal.getType()).thenReturn(org.bukkit.Material.AIR);
        final Block elsewhere = mock(Block.class);
        when(elsewhere.getLocation()).thenReturn(new Location(world, 0, 0, 0));
        when(elsewhere.getWorld()).thenReturn(world);
        when(elsewhere.getType()).thenReturn(org.bukkit.Material.AIR);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(elsewhere);
        when(world.getBlockAt(BX, BY, BZ)).thenReturn(portal);

        final Stargate destination = new Stargate();
        destination.setGateName("destination");
        destination.setGateWorld(world);
        destination.setGateFacing(BlockFace.EAST);
        destination.setGateActive(true);
        destination.setGatePlayerTeleportLocation(new Location(world, 99.5, 70, 99.5));

        origin = new Stargate();
        origin.setGateName("origin");
        origin.setGateWorld(world);
        origin.setGateFacing(BlockFace.NORTH);
        origin.setGateActive(true);
        origin.setGatePlayerTeleportLocation(new Location(world, BX + 0.5, BY, BZ + 0.5));
        origin.getGatePortalBlocks().add(new Location(world, BX, BY, BZ));
        final java.lang.reflect.Field tf = Stargate.class.getDeclaredField("gateTarget");
        tf.setAccessible(true);
        tf.set(origin, destination);
        StargateManager.addBlockIndex(portal, origin);
        StargateManager.registerStargate(origin);

        arrow = mock(Arrow.class);
        when(arrow.getUniqueId()).thenReturn(UUID.randomUUID());
        when(arrow.isValid()).thenReturn(true);
        when(arrow.getType()).thenReturn(EntityType.ARROW);
        when(arrow.getVelocity()).thenReturn(new Vector(0, 0, -3.0));
        when(arrow.getPickupStatus()).thenReturn(org.bukkit.entity.AbstractArrow.PickupStatus.ALLOWED);

        spawned = mock(Arrow.class);
        when(spawned.getUniqueId()).thenReturn(UUID.randomUUID());
        when(spawned.isValid()).thenReturn(true);
        when(world.spawnArrow(any(Location.class), any(Vector.class), anyFloat(), anyFloat(), any(Class.class)))
            .thenReturn(spawned);

        ticker = ProjectileGateTracker.createTicker();
    }

    @AfterEach
    public void tearDown()
    {
        StargateManager.removeStargate(origin);
        ProjectileGateTracker.clear();
        GateSpatialIndex.clear();
    }

    /** Puts the arrow somewhere, then runs one tick of the tracker. */
    private void arrowAt(final double x, final double y, final double z)
    {
        when(arrow.getLocation()).thenReturn(new Location(world, x, y, z));
    }

    @Test
    public void aLaunchedProjectileIsFollowed()
    {
        arrowAt(0, 64, 0);
        new ProjectileGateTracker().onProjectileLaunch(new ProjectileLaunchEvent(arrow));

        assertEquals(1, ProjectileGateTracker.trackedCount());
    }

    @Test
    public void itCrossesTheGateOnTheTickItArrives()
    {
        new ProjectileGateTracker().onProjectileLaunch(new ProjectileLaunchEvent(arrow));

        // Somewhere else: nothing happens.
        arrowAt(0, 64, 0);
        ticker.run();
        verify(arrow, never()).remove();

        // Now inside the portal: it goes through immediately, not up to a second later.
        arrowAt(BX + 0.5, BY, BZ + 0.5);
        ticker.run();
        verify(arrow).remove();
        verify(world).spawnArrow(any(Location.class), any(Vector.class), anyFloat(), anyFloat(), any(Class.class));
    }

    @Test
    public void aCrossedProjectileStopsBeingFollowedAndItsReplacementTakesOver()
    {
        new ProjectileGateTracker().onProjectileLaunch(new ProjectileLaunchEvent(arrow));
        arrowAt(BX + 0.5, BY, BZ + 0.5);
        ticker.run();

        // The original is gone from the map and the replacement is followed in its place,
        // so an arrow can cross a second gate on the far side.
        assertEquals(1, ProjectileGateTracker.trackedCount());
    }

    @Test
    public void anArrowThatOutrunsTheSamplingIsStillCaught()
    {
        // The case that made arrows only work with a block behind the gate. A drawn bow
        // moves an arrow about three blocks a tick and a portal is one thick, so checking
        // where the arrow *is* steps straight over the gate. Checking where it *went* does
        // not: here it is four blocks short on one tick and two past on the next, never
        // sampled inside the portal, and it must still cross.
        new ProjectileGateTracker().onProjectileLaunch(new ProjectileLaunchEvent(arrow));
        arrowAt(BX + 0.5, BY, BZ + 4.5);
        ticker.run();
        verify(arrow, never()).remove();

        arrowAt(BX + 0.5, BY, BZ - 2.5);
        ticker.run();

        verify(arrow).remove();
        verify(world).spawnArrow(any(Location.class), any(Vector.class), anyFloat(), anyFloat(), any(Class.class));
    }

    @Test
    public void aPathThatMissesTheGateEntirelyDoesNotCross()
    {
        // Sanity on the other side: walking the path must not make near misses count.
        new ProjectileGateTracker().onProjectileLaunch(new ProjectileLaunchEvent(arrow));
        arrowAt(BX + 8.5, BY, BZ + 4.5);
        ticker.run();
        arrowAt(BX + 8.5, BY, BZ - 4.5);
        ticker.run();

        verify(arrow, never()).remove();
    }

    @Test
    public void anInvalidProjectileIsForgotten()
    {
        new ProjectileGateTracker().onProjectileLaunch(new ProjectileLaunchEvent(arrow));
        when(arrow.isValid()).thenReturn(false);
        arrowAt(0, 64, 0);

        ticker.run();

        assertEquals(0, ProjectileGateTracker.trackedCount());
    }

    @Test
    public void anIdleTickCostsNothingWhenNothingIsInFlight()
    {
        assertEquals(0, ProjectileGateTracker.trackedCount());
        clearInvocations(world);

        ticker.run();

        // Nothing in flight means the per-tick pass touches the world at all.
        verifyNoInteractions(world);
    }
}
