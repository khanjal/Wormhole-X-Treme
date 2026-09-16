package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.model.GateSpatialIndex;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.StargateTestSupport;

/**
 * Catching a projectile at the moment it reaches a gate.
 *
 * <p>The periodic sweep cannot do this. Portal blocks are air, so an arrow crosses the ring
 * in about one tick and carries on until it hits something — usually past the gate. Polling
 * every twenty ticks nearly always misses the crossing, and when it did catch one the arrow
 * had already landed and stopped, which is what made arrows trickle out of the destination.
 */
class ProjectileGateTrackerTest
{
    private World world;
    private Stargate origin;
    private Stargate destination;
    private Arrow arrow;
    private Arrow spawned;
    private Runnable ticker;

    private static final int BX = 10, BY = 64, BZ = 20;

    /** Where the destination gate's portal is, for the tests that need one. */
    private static final int DX = 40;

    @BeforeEach
    void setUp() throws Exception
    {
        GateSpatialIndex.clear();
        ProjectileGateTracker.clear();

        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        PluginTestSupport.install(plugin);

        final org.bukkit.scheduler.BukkitScheduler scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
        when(scheduler.scheduleSyncDelayedTask(any(), any(Runnable.class), anyLong()))
            .thenAnswer(inv -> { inv.getArgument(1, Runnable.class).run(); return 1; });
        PluginTestSupport.scheduler(scheduler);

        world = mock(World.class);
        when(world.getName()).thenReturn("w");

        final Block portal = mock(Block.class);
        when(portal.getLocation()).thenReturn(new Location(world, BX, BY, BZ));
        when(portal.getX()).thenReturn(Integer.valueOf(BX));
        when(portal.getY()).thenReturn(Integer.valueOf(BY));
        when(portal.getZ()).thenReturn(Integer.valueOf(BZ));
        when(portal.getWorld()).thenReturn(world);
        when(portal.getType()).thenReturn(org.bukkit.Material.AIR);
        final Block elsewhere = mock(Block.class);
        when(elsewhere.getLocation()).thenReturn(new Location(world, 0, 0, 0));
        when(elsewhere.getX()).thenReturn(Integer.valueOf(0));
        when(elsewhere.getY()).thenReturn(Integer.valueOf(0));
        when(elsewhere.getZ()).thenReturn(Integer.valueOf(0));
        when(elsewhere.getWorld()).thenReturn(world);
        when(elsewhere.getType()).thenReturn(org.bukkit.Material.AIR);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(elsewhere);
        when(world.getBlockAt(BX, BY, BZ)).thenReturn(portal);

        destination = new Stargate();
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
        StargateTestSupport.target(origin, destination);
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
        // The tracker only follows projectiles while a gate is open; the flag refreshes on
        // the ticker, so prime it before any test launches anything.
        ProjectileGateTracker.refreshOpenGateFlagForTest();
    }

    @AfterEach
    void tearDown()
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
    void aLaunchedProjectileIsFollowed()
    {
        arrowAt(0, 64, 0);
        new ProjectileGateTracker().onProjectileLaunch(new ProjectileLaunchEvent(arrow));

        assertEquals(1, ProjectileGateTracker.trackedCount());
    }

    @Test
    void itCrossesTheGateOnTheTickItArrives()
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
    void aCrossedProjectileStopsBeingFollowedAndItsReplacementTakesOver()
    {
        new ProjectileGateTracker().onProjectileLaunch(new ProjectileLaunchEvent(arrow));
        arrowAt(BX + 0.5, BY, BZ + 0.5);
        ticker.run();

        // The original is gone from the map and the replacement is followed in its place,
        // so an arrow can cross a second gate on the far side.
        assertEquals(1, ProjectileGateTracker.trackedCount());
    }

    @Test
    void anArrowThatOutrunsTheSamplingIsStillCaught()
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
    void aPathThatMissesTheGateEntirelyDoesNotCross()
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
    void anInvalidProjectileIsForgotten()
    {
        new ProjectileGateTracker().onProjectileLaunch(new ProjectileLaunchEvent(arrow));
        when(arrow.isValid()).thenReturn(false);
        arrowAt(0, 64, 0);

        ticker.run();

        assertEquals(0, ProjectileGateTracker.trackedCount());
    }

    /**
     * A projectile that never reaches a gate is eventually forgotten.
     *
     * <p>An arrow fired into open sky stays valid until it despawns, which is far longer
     * than a server should hold a reference to it. Without the expiry every such arrow is
     * followed until it happens to become invalid, and a busy server fires a lot of them.
     */
    @Test
    void aProjectileThatNeverArrivesIsEventuallyForgotten()
    {
        new ProjectileGateTracker().onProjectileLaunch(new ProjectileLaunchEvent(arrow));
        arrowAt(BX + 100, BY, BZ + 100);
        assertEquals(1, ProjectileGateTracker.trackedCount(), "followed to begin with");

        // Past its lifetime, without it ever having gone anywhere near the gate.
        for (int i = 0; i <= 201; i++)
        {
            ticker.run();
        }

        assertEquals(0, ProjectileGateTracker.trackedCount(),
            "an arrow that never arrives is dropped rather than followed forever");
    }

    /**
     * A projectile whose handling throws is dropped, not retried every tick.
     *
     * <p>Kept, it would throw again on the next tick and every tick after -- one bad
     * projectile turning into a log line per tick for as long as the server runs.
     */
    @Test
    void aProjectileThatThrowsIsDroppedRatherThanRetriedForever()
    {
        new ProjectileGateTracker().onProjectileLaunch(new ProjectileLaunchEvent(arrow));
        when(arrow.getLocation()).thenThrow(new IllegalStateException("entity gone"));

        assertDoesNotThrow(() -> ticker.run());

        assertEquals(0, ProjectileGateTracker.trackedCount(),
            "the one that threw is forgotten, so it cannot throw again next tick");
    }


    /** A fresh arrow sitting in the portal, as the far gate of a facing pair would hand one back. */
    private Arrow anotherArrowInThePortal()
    {
        final Arrow another = mock(Arrow.class);
        when(another.getUniqueId()).thenReturn(UUID.randomUUID());
        when(another.isValid()).thenReturn(true);
        when(another.getType()).thenReturn(EntityType.ARROW);
        when(another.getVelocity()).thenReturn(new Vector(0, 0, -3.0));
        when(another.getPickupStatus()).thenReturn(org.bukkit.entity.AbstractArrow.PickupStatus.ALLOWED);
        when(another.getLocation()).thenReturn(new Location(world, BX + 0.5, BY, BZ + 0.5));
        return another;
    }

    /** A hit on this projectile, mocked: the event's constructors differ across versions. */
    private static ProjectileHitEvent hitOn(final Arrow projectile)
    {
        final ProjectileHitEvent event = mock(ProjectileHitEvent.class);
        when(event.getEntity()).thenReturn(projectile);
        return event;
    }

    /**
     * Two gates facing each other hand an arrow back and forth a bounded number of times (#298).
     *
     * <p>Each crossing fires a replacement, which used to be followed afresh: nothing carried over,
     * so an arrow between facing gates went round for as long as it flew.
     */
    @Test
    void anArrowBetweenFacingGatesCrossesABoundedNumberOfTimes()
    {
        when(world.spawnArrow(any(Location.class), any(Vector.class), anyFloat(), anyFloat(), any(Class.class)))
            .thenAnswer(inv -> anotherArrowInThePortal());
        new ProjectileGateTracker().onProjectileLaunch(new ProjectileLaunchEvent(arrow));
        arrowAt(BX + 0.5, BY, BZ + 0.5);

        for (int i = 0; i < 20; i++)
        {
            ticker.run();
        }

        verify(world, times(ProjectileGateTracker.MOST_CROSSINGS))
            .spawnArrow(any(Location.class), any(Vector.class), anyFloat(), anyFloat(), any(Class.class));
        assertEquals(0, ProjectileGateTracker.trackedCount(), "and the last one is let fly");
    }

    /** A replacement lives out the original's time, not a fresh allowance of its own. */
    @Test
    void aReplacementKeepsWhatWasLeftOfTheOriginalsTime()
    {
        new ProjectileGateTracker().onProjectileLaunch(new ProjectileLaunchEvent(arrow));
        arrowAt(BX + 100, BY, BZ + 100);
        for (int i = 0; i < 150; i++)
        {
            ticker.run();
        }
        arrowAt(BX + 0.5, BY, BZ + 0.5);
        ticker.run();
        when(spawned.getLocation()).thenReturn(new Location(world, BX + 100, BY, BZ + 100));
        assertEquals(1, ProjectileGateTracker.trackedCount(), "the replacement is followed");

        for (int i = 0; i < 60; i++)
        {
            ticker.run();
        }

        assertEquals(0, ProjectileGateTracker.trackedCount(), "gone when the original's time runs out");
    }

    /**
     * An arrow that crosses a gate and hits the wall behind it in the same tick still goes through.
     *
     * <p>The hit lands mid-tick and the path is walked at the start of the next. Dropping the arrow
     * on the hit stopped arrows crossing any gate with something close behind it.
     */
    @Test
    void anArrowThatHitsTheWallJustBehindTheGateStillCrosses()
    {
        new ProjectileGateTracker().onProjectileLaunch(new ProjectileLaunchEvent(arrow));
        arrowAt(BX + 0.5, BY, BZ + 3.5);
        ticker.run();

        new ProjectileGateTracker().onProjectileHit(hitOn(arrow));
        arrowAt(BX + 0.5, BY, BZ - 1.5);
        ticker.run();

        verify(arrow).remove();
        verify(world).spawnArrow(any(Location.class), any(Vector.class), anyFloat(), anyFloat(), any(Class.class));
    }

    /** An arrow that hit something without crossing a gate is followed no further. */
    @Test
    void anArrowThatHitsSomethingAwayFromAGateIsForgotten()
    {
        new ProjectileGateTracker().onProjectileLaunch(new ProjectileLaunchEvent(arrow));
        arrowAt(BX + 100, BY, BZ + 100);
        ticker.run();

        new ProjectileGateTracker().onProjectileHit(hitOn(arrow));
        arrowAt(BX + 101, BY, BZ + 100);
        ticker.run();

        assertEquals(0, ProjectileGateTracker.trackedCount());
    }

    /** An arrow bounced back into the gate it just came out of is not sent back through it (#298). */
    @Test
    void aReplacementIsNotSentBackIntoTheGateItCameOutOf()
    {
        final Block exitPortal = mock(Block.class);
        when(exitPortal.getX()).thenReturn(Integer.valueOf(DX));
        when(exitPortal.getY()).thenReturn(Integer.valueOf(BY));
        when(exitPortal.getZ()).thenReturn(Integer.valueOf(BZ));
        when(exitPortal.getWorld()).thenReturn(world);
        when(exitPortal.getLocation()).thenReturn(new Location(world, DX, BY, BZ));
        when(exitPortal.getType()).thenReturn(org.bukkit.Material.AIR);
        when(world.getBlockAt(DX, BY, BZ)).thenReturn(exitPortal);
        destination.getGatePortalBlocks().add(new Location(world, DX, BY, BZ));
        StargateTestSupport.target(destination, origin);
        StargateManager.addBlockIndex(exitPortal, destination);
        StargateManager.registerStargate(destination);
        try
        {
            when(spawned.getType()).thenReturn(EntityType.ARROW);
            when(spawned.getVelocity()).thenReturn(new Vector(3.0, 0, 0));
            when(spawned.getLocation()).thenReturn(new Location(world, DX + 0.5, BY, BZ + 0.5));
            new ProjectileGateTracker().onProjectileLaunch(new ProjectileLaunchEvent(arrow));
            arrowAt(BX + 0.5, BY, BZ + 0.5);

            ticker.run();
            ticker.run();

            verify(world, times(1))
                .spawnArrow(any(Location.class), any(Vector.class), anyFloat(), anyFloat(), any(Class.class));
        }
        finally
        {
            StargateManager.removeStargate(destination);
        }
    }

    /**
     * A replacement that hits somebody on arrival is not pushed on again the tick after (#298).
     *
     * <p>The exit velocity goes on twice, the second a tick later, because a spawned arrow loses
     * the first. An arrow that bounced off a player in that tick was sent on through them.
     */
    @Test
    void aReplacementThatHitsOnArrivalIsNotPushedOnAgain() throws Exception
    {
        final List<Runnable> later = new ArrayList<>();
        final org.bukkit.scheduler.BukkitScheduler holding = mock(org.bukkit.scheduler.BukkitScheduler.class);
        when(holding.scheduleSyncDelayedTask(any(), any(Runnable.class), anyLong()))
            .thenAnswer(inv -> { later.add(inv.getArgument(1, Runnable.class)); return 1; });
        PluginTestSupport.scheduler(holding);
        new ProjectileGateTracker().onProjectileLaunch(new ProjectileLaunchEvent(arrow));
        arrowAt(BX + 0.5, BY, BZ + 0.5);
        ticker.run();
        assertFalse(later.isEmpty(), "the second push is booked for next tick");

        new ProjectileGateTracker().onProjectileHit(hitOn(spawned));
        later.forEach(Runnable::run);

        verify(spawned, times(1)).setVelocity(any(Vector.class));
    }

    @Test
    void nothingIsFollowedWhileNoGateIsOpen()
    {
        // The common case on any server: no wormhole open, so a projectile cannot cross
        // anything and following it would be pure cost.
        origin.setGateActive(false);
        ProjectileGateTracker.refreshOpenGateFlagForTest();

        new ProjectileGateTracker().onProjectileLaunch(new ProjectileLaunchEvent(arrow));

        assertEquals(0, ProjectileGateTracker.trackedCount());
    }

    @Test
    void anIdleTickCostsNothingWhenNothingIsInFlight()
    {
        assertEquals(0, ProjectileGateTracker.trackedCount());
        clearInvocations(world);

        ticker.run();

        // Nothing in flight means the per-tick pass touches the world at all.
        verifyNoInteractions(world);
    }
}
