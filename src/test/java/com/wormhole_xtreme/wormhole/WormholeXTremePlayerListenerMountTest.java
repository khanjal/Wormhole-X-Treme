package com.wormhole_xtreme.wormhole;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
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

/**
 * Tests for player-mounted entities teleport/reattach behavior (horses, pigs, camels).
 */
public class WormholeXTremePlayerListenerMountTest
{
    private BukkitScheduler mockScheduler;

    @BeforeEach
    public void setUp() throws Exception
    {
        // Install a mock scheduler so scheduling calls don't NPE.
        mockScheduler = mock(BukkitScheduler.class);
        when(mockScheduler.scheduleSyncDelayedTask(any(), any(Runnable.class), anyLong())).thenReturn(1);

        final Field schedField = WormholeXTreme.class.getDeclaredField("scheduler");
        schedField.setAccessible(true);
        schedField.set(null, mockScheduler);

        // Install a mock plugin instance so prettyLog() calls do not NPE.
        final WormholeXTreme mockPlugin = mock(WormholeXTreme.class);
        final Field pluginField = WormholeXTreme.class.getDeclaredField("thisPlugin");
        pluginField.setAccessible(true);
        pluginField.set(null, mockPlugin);

        GateSpatialIndex.clear();
    }

    @AfterEach
    public void tearDown() throws Exception
    {
        GateSpatialIndex.clear();
    }

    @Test
    public void mountReattachForTwoRiders() throws Exception
    {
        // Arrange world and gate block
        final World world = mock(World.class);
        when(world.getName()).thenReturn("w");

        final int bx = 30, by = 64, bz = 40;
        final Location toLoc = new Location(world, bx + 0.5, by, bz + 0.5);

        final Block ch = mock(Block.class);
        when(ch.getLocation()).thenReturn(new Location(world, bx, by, bz));
        when(ch.getWorld()).thenReturn(world);
        when(world.getBlockAt(bx, by, bz)).thenReturn(ch);
        when(ch.getType()).thenReturn(Material.WATER);

        // Create source and target stargates
        final Stargate src = new Stargate();
        src.setGateName("srcMount");
        src.setGateActive(true);

        final Stargate target = new Stargate();
        target.setGatePlayerTeleportLocation(new Location(world, 100.5, 70.0, 200.5));
        target.setGateFacing(BlockFace.NORTH);
        final Field gateTargetField = Stargate.class.getDeclaredField("gateTarget");
        gateTargetField.setAccessible(true);
        gateTargetField.set(src, target);

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

        when(rider1.getVehicle()).thenReturn((Entity) mount);
        when(rider2.getVehicle()).thenReturn((Entity) mount);
        when(rider1.isValid()).thenReturn(true);
        when(rider2.isValid()).thenReturn(true);
        when(rider1.getName()).thenReturn("r1");
        when(rider2.getName()).thenReturn("r2");

        // Track teleport and addPassenger invocations and simulate successful addPassenger
        final java.util.concurrent.atomic.AtomicInteger teleports = new java.util.concurrent.atomic.AtomicInteger(0);
        doAnswer(inv -> { teleports.incrementAndGet(); WormholeXTreme.getThisPlugin().prettyLog(java.util.logging.Level.FINE, "[TEST-DEBUG] mount.teleport called"); return null; }).when(mount).teleport(any(Location.class));
        final java.util.concurrent.atomic.AtomicInteger adds = new java.util.concurrent.atomic.AtomicInteger(0);
        doAnswer(inv -> { adds.incrementAndGet(); return true; }).when(mount).addPassenger(any());

        // Execute delayed runnables immediately so reattach tasks run synchronously in the test.
        doAnswer(inv -> {
            final Runnable r = inv.getArgument(1, Runnable.class);
            try { r.run(); } catch (final Throwable ignore) {}
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
        verify(mockScheduler, atLeastOnce()).scheduleSyncDelayedTask(any(), any(Runnable.class), eq(2L));

        // Cleanup
        StargateManager.removeBlockIndex(ch);
    }

    @Test
    public void gateIsDetectedUnderMountWhenRiderClearsThePortal() throws Exception
    {
        // A tall mount (camel) puts the rider's own block above the portal, so the
        // gate has to be found under the mount or the trip never triggers.
        final World world = mock(World.class);
        when(world.getName()).thenReturn("w");

        final int bx = 30, by = 64, bz = 40;

        // The portal block — where the mount is standing.
        final Block portal = mock(Block.class);
        when(portal.getLocation()).thenReturn(new Location(world, bx, by, bz));
        when(portal.getWorld()).thenReturn(world);
        when(portal.getType()).thenReturn(Material.WATER);
        when(world.getBlockAt(bx, by, bz)).thenReturn(portal);

        // Plain air blocks: where the rider's feet are (two up), and the block the
        // bounding-box fallback also probes (one up from the mount).
        for (final int dy : new int[] { 1, 2 })
        {
            final Block air = mock(Block.class);
            when(air.getLocation()).thenReturn(new Location(world, bx, by + dy, bz));
            when(air.getWorld()).thenReturn(world);
            when(air.getType()).thenReturn(Material.AIR);
            when(world.getBlockAt(bx, by + dy, bz)).thenReturn(air);
        }

        final Stargate src = new Stargate();
        src.setGateName("srcCamel");
        src.setGateActive(true);

        final Stargate target = new Stargate();
        target.setGatePlayerTeleportLocation(new Location(world, 100.5, 70.0, 200.5));
        target.setGateFacing(BlockFace.NORTH);
        final Field gateTargetField = Stargate.class.getDeclaredField("gateTarget");
        gateTargetField.setAccessible(true);
        gateTargetField.set(src, target);

        StargateManager.addBlockIndex(portal, src);
        src.getGatePortalBlocks().add(new Location(world, bx, by, bz));

        // Mount stands in the portal block; rider sits two blocks higher.
        final Pig mount = mock(Pig.class);
        when(mount.getUniqueId()).thenReturn(UUID.randomUUID());
        when(mount.isValid()).thenReturn(true);
        when(mount.getType()).thenReturn(EntityType.PIG);
        when(mount.getLocation()).thenReturn(new Location(world, bx + 0.5, by, bz + 0.5));

        final Player rider = mock(Player.class);
        when(rider.getVehicle()).thenReturn((Entity) mount);
        when(rider.isValid()).thenReturn(true);
        when(rider.getName()).thenReturn("camelRider");

        final java.util.concurrent.atomic.AtomicInteger teleports = new java.util.concurrent.atomic.AtomicInteger(0);
        doAnswer(inv -> { teleports.incrementAndGet(); return null; }).when(mount).teleport(any(Location.class));
        doAnswer(inv -> true).when(mount).addPassenger(any());

        doAnswer(inv -> {
            final Runnable r = inv.getArgument(1, Runnable.class);
            try { r.run(); } catch (final Throwable ignore) {}
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
}
