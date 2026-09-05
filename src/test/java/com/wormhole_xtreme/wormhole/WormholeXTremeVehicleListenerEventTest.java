package com.wormhole_xtreme.wormhole;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.GateSpatialIndex;

/**
 * Tests for vehicle teleport dispatch/behavior (occupied vs unoccupied).
 */
class WormholeXTremeVehicleListenerEventTest
{
    private BukkitScheduler mockScheduler;

    @BeforeEach
    void setUp() throws Exception
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
        // ensure spatial index / gate registry is clean
        GateSpatialIndex.clear();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        // Clear any registry state we modified
        GateSpatialIndex.clear();
    }

    /**
     * Whatever this server version calls a boat.
     *
     * <p>{@code EntityType.BOAT} was removed in 1.21.3, when boats were split into one type
     * per wood. Looking the name up keeps this test compiling across that change, which is
     * the whole reason the production code stopped naming a constant too.
     *
     * @return a boat entity type that exists on the API being built against
     */
    private static EntityType anyBoatType()
    {
        for (final String name : new String[] { "BOAT", "OAK_BOAT" })
        {
            try
            {
                return EntityType.valueOf(name);
            }
            catch (final IllegalArgumentException notThisVersion)
            {
                continue;
            }
        }
        throw new IllegalStateException("no boat entity type found on this API");
    }

    @Test
    void unoccupiedMinecartTeleportsAndReceivesVelocity() throws Exception
    {
        final World world = mock(World.class);
        when(world.getName()).thenReturn("w");

        final int bx = 10, by = 64, bz = 20;
        final Location toLoc = new Location(world, bx + 0.5, by, bz + 0.5);

        final Block ch = mock(Block.class);
        when(ch.getLocation()).thenReturn(new Location(world, bx, by, bz));
        when(world.getBlockAt(bx, by, bz)).thenReturn(ch);
        // An open portal is AIR on the server; the portal material is drawn to clients
        // only. Stubbing WATER here is what let the material-comparison bug pass tests
        // while no vehicle could actually enter a gate in game.
        when(ch.getType()).thenReturn(Material.AIR);
        when(ch.getWorld()).thenReturn(world);

        final Stargate src = new Stargate();
        src.setGateName("src");
        src.setGateActive(true);

        final Stargate target = new Stargate();
        target.setGatePlayerTeleportLocation(new Location(world, 100.5, 70.0, 200.5));
        target.setGateFacing(BlockFace.NORTH);

        // set the target via reflection (setGateTarget is package-private)
        final java.lang.reflect.Field gateTargetField = Stargate.class.getDeclaredField("gateTarget");
        gateTargetField.setAccessible(true);
        gateTargetField.set(src, target);

        StargateManager.addBlockIndex(ch, src);
        // Portal membership comes from the gate's block list, not the block's material.
        src.getGatePortalBlocks().add(new Location(world, bx, by, bz));

        final Minecart cart = mock(Minecart.class);
        when(cart.getPassengers()).thenReturn(Collections.<org.bukkit.entity.Entity>emptyList());
        when(cart.getUniqueId()).thenReturn(UUID.randomUUID());
        when(cart.getVelocity()).thenReturn(new Vector(1.0, 0.0, 0.0));

        final Location fromLoc = new Location(world, bx + 0.5, by, bz - 0.5);
        final VehicleMoveEvent ev = new VehicleMoveEvent(cart, fromLoc, toLoc);

        final WormholeXTremeVehicleListener listener = new WormholeXTremeVehicleListener();
        listener.onVehicleMove(ev);

        // Verify the vehicle was teleported to the forward-and-up safe target
        verify(cart, atLeastOnce()).teleport(any(Location.class));
        verify(cart, atLeastOnce()).setVelocity(any(Vector.class));

        // Cleanup
        StargateManager.removeBlockIndex(ch);
    }

    @Test
    void occupiedBoatTriggersReattachAndResync() throws Exception
    {
        final World world = mock(World.class);
        when(world.getName()).thenReturn("w");

        final int bx = 15, by = 65, bz = 25;
        final Location toLoc = new Location(world, bx + 0.5, by, bz + 0.5);

        final Block ch = mock(Block.class);
        when(ch.getLocation()).thenReturn(new Location(world, bx, by, bz));
        when(world.getBlockAt(bx, by, bz)).thenReturn(ch);
        // An open portal is AIR on the server; the portal material is drawn to clients
        // only. Stubbing WATER here is what let the material-comparison bug pass tests
        // while no vehicle could actually enter a gate in game.
        when(ch.getType()).thenReturn(Material.AIR);
        when(ch.getWorld()).thenReturn(world);

        final Stargate src = new Stargate();
        src.setGateName("srcBoat");
        src.setGateActive(true);

        final Stargate target = new Stargate();
        target.setGatePlayerTeleportLocation(new Location(world, 200.5, 80.0, 300.5));
        target.setGateFacing(BlockFace.EAST);
        final java.lang.reflect.Field gateTargetField2 = Stargate.class.getDeclaredField("gateTarget");
        gateTargetField2.setAccessible(true);
        gateTargetField2.set(src, target);

        StargateManager.addBlockIndex(ch, src);
        // Portal membership comes from the gate's block list, not the block's material.
        src.getGatePortalBlocks().add(new Location(world, bx, by, bz));

        final Boat boat = mock(Boat.class);
        final Player rider = mock(Player.class);
        when(boat.getPassengers()).thenReturn(Collections.<org.bukkit.entity.Entity>singletonList(rider));
        when(boat.getUniqueId()).thenReturn(UUID.randomUUID());
        when(boat.isValid()).thenReturn(true);
        when(rider.isValid()).thenReturn(true);

        when(boat.getVelocity()).thenReturn(new Vector(0.5, 0.0, 0.0));
        // Named rather than referenced as a constant. EntityType.BOAT was removed in
        // 1.21.3 when boats split per wood type, and this test has no interest in which
        // kind of boat it is — only that the code asks the boat rather than assuming.
        when(boat.getType()).thenReturn(anyBoatType());

        // Simulate immediate successful attach when addPassenger is attempted in the reattach task
        when(boat.addPassenger(rider)).thenReturn(true);

        // Make the scheduler execute 5L/3L/2L delayed runnables immediately so the reattach path runs
        doAnswer(inv -> {
            final Runnable r = inv.getArgument(1, Runnable.class);
            final Long delay = inv.getArgument(2, Long.class);
            if (delay == 5L || delay == 3L || delay == 2L)
            {
                r.run();
            }
            return 1;
        }).when(mockScheduler).scheduleSyncDelayedTask(any(), any(Runnable.class), anyLong());

        final Location fromLoc = new Location(world, bx + 0.5, by, bz - 0.5);
        final VehicleMoveEvent ev = new VehicleMoveEvent(boat, fromLoc, toLoc);

        final WormholeXTremeVehicleListener listener = new WormholeXTremeVehicleListener();
        listener.onVehicleMove(ev);

        // Boat should be teleported and have velocity set after reattach
        verify(boat, atLeastOnce()).teleport(any(Location.class));
        verify(boat, atLeastOnce()).setVelocity(any(Vector.class));

        // The re-sync teleport is scheduled (3L) when a boat reattach succeeds
        verify(mockScheduler, atLeastOnce()).scheduleSyncDelayedTask(any(), any(Runnable.class), eq(3L));

        // Cleanup
        StargateManager.removeBlockIndex(ch);
    }
}
