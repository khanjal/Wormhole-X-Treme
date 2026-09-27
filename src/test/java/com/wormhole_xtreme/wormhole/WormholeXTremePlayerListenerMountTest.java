package com.wormhole_xtreme.wormhole;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.GateSpatialIndex;
import com.wormhole_xtreme.wormhole.model.StargateTestSupport;
import com.wormhole_xtreme.wormhole.permissions.StargateRestrictions;

/**
 * Tests for player-mounted entities teleport/reattach behavior (horses, pigs, camels).
 */
class WormholeXTremePlayerListenerMountTest
{
    private BukkitScheduler mockScheduler;

    @BeforeEach
    void setUp() throws Exception
    {
        // Install a mock scheduler so scheduling calls don't NPE.
        mockScheduler = mock(BukkitScheduler.class);
        when(mockScheduler.scheduleSyncDelayedTask(any(), any(Runnable.class), anyLong())).thenReturn(1);

        PluginTestSupport.scheduler(mockScheduler);

        // Install a mock plugin instance so prettyLog() calls do not NPE.
        final WormholeXTreme mockPlugin = mock(WormholeXTreme.class);
        PluginTestSupport.install(mockPlugin);

        GateSpatialIndex.clear();
    }

    @AfterEach
    void tearDown()
    {
        GateSpatialIndex.clear();
        PluginTestSupport.forgetAllGates();
    }

    @Test
    void mountReattachForTwoRiders()
    {
        // Arrange world and gate block
        final World world = mock(World.class);
        when(world.getName()).thenReturn("w");

        final int bx = 30, by = 64, bz = 40;
        final Location toLoc = new Location(world, bx + 0.5, by, bz + 0.5);

        final Block ch = mock(Block.class);
        when(ch.getLocation()).thenReturn(new Location(world, bx, by, bz));
        when(ch.getX()).thenReturn(Integer.valueOf(bx));
        when(ch.getY()).thenReturn(Integer.valueOf(by));
        when(ch.getZ()).thenReturn(Integer.valueOf(bz));
        when(ch.getWorld()).thenReturn(world);
        when(world.getBlockAt(bx, by, bz)).thenReturn(ch);
        when(ch.getType()).thenReturn(Material.WATER);

        // Create source and target stargates
        final Stargate src = new Stargate();
        src.setGateName("srcMount");
        src.setGateActive(true);
        src.setGatePortalOpen(true);

        final Stargate target = new Stargate();
        target.setGatePlayerTeleportLocation(new Location(world, 100.5, 70.0, 200.5));
        target.setGateFacing(BlockFace.NORTH);
        StargateTestSupport.target(src, target);

        StargateManager.addBlockIndex(ch, src);
        // isPortalBlock checks getGatePortalBlocks(), so register the block there too.
        src.getGatePortalBlocks().add(new Location(world, bx, by, bz));

        // Create a mount (pig) and two player riders
        final Pig mount = mock(Pig.class);
        final Player rider1 = mock(Player.class);
        final Player rider2 = mock(Player.class);

        when(mount.getUniqueId()).thenReturn(UUID.randomUUID());
        when(mount.isValid()).thenReturn(true);
        when(mount.getType()).thenReturn(EntityType.PIG);

        seatedOn(rider1, mount);
        seatedOn(rider2, mount);
        when(rider1.isValid()).thenReturn(true);
        when(rider1.teleport(any(Location.class))).thenReturn(true);
        when(rider2.teleport(any(Location.class))).thenReturn(true);
        when(rider2.isValid()).thenReturn(true);
        when(rider1.getName()).thenReturn("r1");
        when(rider2.getName()).thenReturn("r2");

        // Track teleport and addPassenger invocations and simulate successful addPassenger
        final java.util.concurrent.atomic.AtomicInteger teleports = new java.util.concurrent.atomic.AtomicInteger(0);
        doAnswer(inv -> { teleports.incrementAndGet(); WormholeXTreme.getThisPlugin().prettyLog(java.util.logging.Level.FINE, "[TEST-DEBUG] mount.teleport called"); return true; }).when(mount).teleport(any(Location.class));
        final java.util.concurrent.atomic.AtomicInteger adds = new java.util.concurrent.atomic.AtomicInteger(0);
        doAnswer(inv -> { adds.incrementAndGet(); return true; }).when(mount).addPassenger(any());

        // Execute delayed runnables immediately so reattach tasks run synchronously in the test.
        doAnswer(inv -> {
            final Runnable r = inv.getArgument(1, Runnable.class);
            try { r.run(); } catch (final Throwable ignore) { /* the stub server is only needed by some paths */ }
            return 1;
        }).when(mockScheduler).scheduleSyncDelayedTask(any(), any(Runnable.class), anyLong());

        // Sanity: ensure our mock wiring is correct
        org.junit.jupiter.api.Assertions.assertNotNull(rider1.getVehicle(), "rider1.getVehicle() should be non-null and return mount");
        org.junit.jupiter.api.Assertions.assertNotNull(rider2.getVehicle(), "rider2.getVehicle() should be non-null and return mount");

        // Act: simulate both players moving into the gate portal block
        // fromLoc must be a different block coordinate from toLoc so hasChangedBlockCoordinates returns true.
        final Location fromLoc = new Location(world, bx + 0.5, by, bz - 1.5);
        final PlayerMoveEvent ev1 = new PlayerMoveEvent(rider1, fromLoc, toLoc);
        final PlayerMoveEvent ev2 = new PlayerMoveEvent(rider2, fromLoc, toLoc);

        final WormholeXTremePlayerListener listener = new WormholeXTremePlayerListener();
        listener.onPlayerMove(ev1);
        listener.onPlayerMove(ev2);

        // Assert: mount was teleported and both riders were reattached
        WormholeXTreme.getThisPlugin().prettyLog(java.util.logging.Level.FINE, "[TEST-DEBUG] teleports=" + teleports.get() + " adds=" + adds.get());
        // JUnit assertions rather than the `assert` keyword, which only evaluates when the
        // JVM runs with -ea and would otherwise let this test pass without checking anything.
        org.junit.jupiter.api.Assertions.assertTrue(teleports.get() > 0, "the mount should have been teleported");
        org.junit.jupiter.api.Assertions.assertTrue(adds.get() >= 2, "both riders should have been re-seated");
        // Five ticks: the riders were teleported too, and a client will not take a seat until
        // it has acknowledged that.
        verify(mockScheduler, atLeastOnce()).scheduleSyncDelayedTask(any(), any(Runnable.class), eq(5L));

        // Cleanup
        StargateManager.removeBlockIndex(ch);
    }

    @Test
    void gateIsDetectedUnderMountWhenRiderClearsThePortal()
    {
        // A tall mount (camel) puts the rider's own block above the portal, so the
        // gate has to be found under the mount or the trip never triggers.
        final World world = mock(World.class);
        when(world.getName()).thenReturn("w");

        final int bx = 30, by = 64, bz = 40;

        // The portal block — where the mount is standing.
        final Block portal = mock(Block.class);
        when(portal.getLocation()).thenReturn(new Location(world, bx, by, bz));
        when(portal.getX()).thenReturn(Integer.valueOf(bx));
        when(portal.getY()).thenReturn(Integer.valueOf(by));
        when(portal.getZ()).thenReturn(Integer.valueOf(bz));
        when(portal.getWorld()).thenReturn(world);
        when(portal.getType()).thenReturn(Material.WATER);
        when(world.getBlockAt(bx, by, bz)).thenReturn(portal);

        // Plain air blocks: where the rider's feet are (two up), and the block the
        // bounding-box fallback also probes (one up from the mount).
        for (final int dy : new int[] { 1, 2 })
        {
            final Block air = mock(Block.class);
            when(air.getLocation()).thenReturn(new Location(world, bx, by + dy, bz));
            when(air.getX()).thenReturn(Integer.valueOf(bx));
            when(air.getY()).thenReturn(Integer.valueOf(by + dy));
            when(air.getZ()).thenReturn(Integer.valueOf(bz));
            when(air.getWorld()).thenReturn(world);
            when(air.getType()).thenReturn(Material.AIR);
            when(world.getBlockAt(bx, by + dy, bz)).thenReturn(air);
        }

        final Stargate src = new Stargate();
        src.setGateName("srcCamel");
        src.setGateActive(true);
        src.setGatePortalOpen(true);

        final Stargate target = new Stargate();
        target.setGatePlayerTeleportLocation(new Location(world, 100.5, 70.0, 200.5));
        target.setGateFacing(BlockFace.NORTH);
        StargateTestSupport.target(src, target);

        StargateManager.addBlockIndex(portal, src);
        src.getGatePortalBlocks().add(new Location(world, bx, by, bz));

        // Mount stands in the portal block; rider sits two blocks higher.
        final Pig mount = mock(Pig.class);
        when(mount.getUniqueId()).thenReturn(UUID.randomUUID());
        when(mount.isValid()).thenReturn(true);
        when(mount.getType()).thenReturn(EntityType.PIG);
        when(mount.getLocation()).thenReturn(new Location(world, bx + 0.5, by, bz + 0.5));

        final Player rider = mock(Player.class);
        seatedOn(rider, mount);
        when(rider.isValid()).thenReturn(true);
        when(rider.getName()).thenReturn("camelRider");
        when(rider.teleport(any(Location.class))).thenReturn(true);

        final java.util.concurrent.atomic.AtomicInteger teleports = new java.util.concurrent.atomic.AtomicInteger(0);
        doAnswer(inv -> { teleports.incrementAndGet(); return true; }).when(mount).teleport(any(Location.class));
        doAnswer(inv -> true).when(mount).addPassenger(any());

        doAnswer(inv -> {
            final Runnable r = inv.getArgument(1, Runnable.class);
            try { r.run(); } catch (final Throwable ignore) { /* the stub server is only needed by some paths */ }
            return 1;
        }).when(mockScheduler).scheduleSyncDelayedTask(any(), any(Runnable.class), anyLong());

        // Rider's own block (by + 2) is NOT a gate block — only the mount's is.
        final Location fromLoc = new Location(world, bx + 0.5, by + 2, bz - 1.5);
        final Location toLoc = new Location(world, bx + 0.5, by + 2, bz + 0.5);

        new WormholeXTremePlayerListener().onPlayerMove(new PlayerMoveEvent(rider, fromLoc, toLoc));

        org.junit.jupiter.api.Assertions.assertTrue(teleports.get() > 0,
            "gate under the mount should have been detected and the mount teleported");

        StargateManager.removeBlockIndex(portal);
    }

    /**
     * Seats a rider who really does get off when the mount is told to let them go.
     *
     * <p>The move asks each passenger whether it is still aboard before it moves the mount, so
     * a rider stubbed to ride forever reads as one another plugin would not let off.
     */
    private static void seatedOn(final Player rider, final Entity mount)
    {
        final boolean[] aboard = { true };
        when(rider.getVehicle()).thenAnswer(call -> aboard[0] ? mount : null);
        when(mount.removePassenger(rider)).thenAnswer(call ->
        {
            aboard[0] = false;
            return Boolean.TRUE;
        });
    }

    /**
     * A gate whose portal block is at {@code bx, by, bz}, leading to a far gate facing north.
     *
     * @return the far gate's arrival point
     */
    private static Location gateAt(final World world, final int bx, final int by, final int bz, final String name)
    {
        final Block ch = mock(Block.class);
        when(ch.getLocation()).thenReturn(new Location(world, bx, by, bz));
        when(ch.getX()).thenReturn(Integer.valueOf(bx));
        when(ch.getY()).thenReturn(Integer.valueOf(by));
        when(ch.getZ()).thenReturn(Integer.valueOf(bz));
        when(ch.getWorld()).thenReturn(world);
        when(world.getBlockAt(bx, by, bz)).thenReturn(ch);
        when(ch.getType()).thenReturn(Material.WATER);

        final Stargate src = new Stargate();
        src.setGateName(name);
        src.setGateActive(true);
        src.setGatePortalOpen(true);

        final Stargate target = new Stargate();
        final Location arrival = new Location(world, 500.5, 70.0, 600.5);
        target.setGatePlayerTeleportLocation(arrival);
        target.setGateFacing(BlockFace.NORTH);
        StargateTestSupport.target(src, target);

        StargateManager.addBlockIndex(ch, src);
        src.getGatePortalBlocks().add(new Location(world, bx, by, bz));
        return arrival;
    }

    private Pig pig()
    {
        final Pig mount = mock(Pig.class);
        when(mount.getUniqueId()).thenReturn(UUID.randomUUID());
        when(mount.isValid()).thenReturn(true);
        when(mount.getType()).thenReturn(EntityType.PIG);
        return mount;
    }

    private static Player riderNamed(final String name)
    {
        final Player rider = mock(Player.class);
        when(rider.getUniqueId()).thenReturn(UUID.randomUUID());
        when(rider.isValid()).thenReturn(true);
        when(rider.getName()).thenReturn(name);
        when(rider.teleport(any(Location.class))).thenReturn(true);
        return rider;
    }

    /** Runs the arrival's tasks at once; not the cooldown's expiry, which is seconds away. */
    private void runTasksAtOnce()
    {
        doAnswer(inv -> {
            if (inv.getArgument(2, Long.class).longValue() < 20L)
            {
                inv.getArgument(1, Runnable.class).run();
            }
            return 1;
        }).when(mockScheduler).scheduleSyncDelayedTask(any(), any(Runnable.class), anyLong());
    }

    /**
     * On Paper 1.20.4 a ridden pig goes through with its rider, and sits them back on it there.
     *
     * <p>1.20.4 will not teleport anything with a passenger, and the plugin ignored the answer:
     * the pig stayed in the gate with the rider on it while the log said they had used the
     * wormhole (#506).
     */
    @Test
    void aMountThatWillNotMoveWithItsRiderAboardStillArrivesWithThemSeated()
    {
        final World world = mock(World.class);
        final Location arrival = gateAt(world, 70, 64, 80, "srcPaper1204");
        final Pig mount = pig();
        final Player rider = riderNamed("rider");
        final Paper1204Riding.Stack stack = Paper1204Riding.refusesWhileRidden(
            mount, new Location(world, 70.5, 64, 80.5), rider);
        runTasksAtOnce();
        com.wormhole_xtreme.wormhole.config.ConfigTestSupport.loadDefaults();
        com.wormhole_xtreme.wormhole.config.ConfigManager.setUseCooldownEnabled(true);
        try
        {
            new WormholeXTremePlayerListener().onPlayerMove(new PlayerMoveEvent(rider,
                new Location(world, 70.5, 64, 78.5), new Location(world, 70.5, 64, 80.5)));

            org.junit.jupiter.api.Assertions.assertEquals(arrival.getX(), stack.at().getX(), 0.001,
                "the pig must reach the far gate rather than stay in this one");
            org.junit.jupiter.api.Assertions.assertTrue(stack.carries(rider),
                "the rider must be back in the saddle at the far end");
            org.junit.jupiter.api.Assertions.assertTrue(StargateRestrictions.isPlayerUseCooldown(rider),
                "a trip that happened spends the cooldown");
        }
        finally
        {
            StargateRestrictions.removePlayerUseCooldown(rider);
            com.wormhole_xtreme.wormhole.config.ConfigManager.setUseCooldownEnabled(false);
            com.wormhole_xtreme.wormhole.config.ConfigTestSupport.clear();
        }
    }

    /**
     * A mount that will not move keeps its rider, at this end, and the trip is not counted.
     *
     * <p>This used to send the player on alone, leaving the mount at the source, and on 1.20.4
     * it did not even notice: the refusal is an answer, not an exception.
     */
    @Test
    void aRiderWhoseMountWillNotMoveStaysAboardAndIsNotChargedForATrip()
    {
        final World world = mock(World.class);
        gateAt(world, 60, 64, 70, "srcStuckMount");
        final Pig mount = pig();
        final Player rider = riderNamed("stranded");
        final Location seat = new Location(world, 60.5, 65, 70.5);
        PetTestSupport.standsWhereTeleported(rider, seat);
        final Paper1204Riding.Stack stack = Paper1204Riding.refusesWhileRidden(
            mount, new Location(world, 60.5, 64, 70.5), rider);
        // Something else refuses: another plugin cancelling the teleport, say.
        when(mount.teleport(any(Location.class))).thenReturn(false);
        runTasksAtOnce();
        com.wormhole_xtreme.wormhole.config.ConfigTestSupport.loadDefaults();
        com.wormhole_xtreme.wormhole.config.ConfigManager.setUseCooldownEnabled(true);
        try
        {
            new WormholeXTremePlayerListener().onPlayerMove(new PlayerMoveEvent(rider,
                new Location(world, 60.5, 64, 68.5), new Location(world, 60.5, 64, 70.5)));

            org.junit.jupiter.api.Assertions.assertEquals(seat, rider.getLocation(), "the rider sent ahead is brought back to where they sat");
            org.junit.jupiter.api.Assertions.assertTrue(stack.carries(rider), "the rider must still be aboard");
            org.junit.jupiter.api.Assertions.assertFalse(StargateRestrictions.isPlayerUseCooldown(rider),
                "no cooldown for a trip that did not happen");
            verify(rider).sendMessage(org.mockito.ArgumentMatchers.contains("could not be sent through"));
        }
        finally
        {
            StargateRestrictions.removePlayerUseCooldown(rider);
            com.wormhole_xtreme.wormhole.config.ConfigManager.setUseCooldownEnabled(false);
            com.wormhole_xtreme.wormhole.config.ConfigTestSupport.clear();
        }
    }

    /** The same when the mount throws rather than refusing. */
    @Test
    void aRiderWhoseMountThrowsIsNotSentOnWithoutIt()
    {
        final World world = mock(World.class);
        gateAt(world, 90, 64, 70, "srcThrowingMount");
        final Pig mount = pig();
        final Player rider = riderNamed("stranded");
        final Location seat = new Location(world, 90.5, 65, 70.5);
        PetTestSupport.standsWhereTeleported(rider, seat);
        final Paper1204Riding.Stack stack = Paper1204Riding.refusesWhileRidden(
            mount, new Location(world, 90.5, 64, 70.5), rider);
        doThrow(new IllegalStateException("mount will not move")).when(mount).teleport(any(Location.class));
        runTasksAtOnce();

        new WormholeXTremePlayerListener().onPlayerMove(new PlayerMoveEvent(rider,
            new Location(world, 90.5, 64, 68.5), new Location(world, 90.5, 64, 70.5)));

        org.junit.jupiter.api.Assertions.assertEquals(seat, rider.getLocation(), "the rider sent ahead is brought back to where they sat");
        org.junit.jupiter.api.Assertions.assertTrue(stack.carries(rider), "the rider must still be aboard");
    }
}
