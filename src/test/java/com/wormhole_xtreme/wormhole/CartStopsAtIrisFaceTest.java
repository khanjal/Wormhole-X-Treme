package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.wormhole_xtreme.wormhole.model.GateSpatialIndex;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * A cart rolling at a shut, drawn iris stops with its front at the iris, not inside it (#491).
 *
 * <p>The check used to ask which block the cart's centre was in, so it fired only once the
 * front half of the cart was already through the drawing, and it put the cart back where it
 * was a tick ago, which still left its nose in the iris. On an upright gate that read as the
 * cart going into the shut iris and coming back out.
 *
 * <p>The gate here is one block thick, its opening the two blocks at (BX, BY, BZ) and
 * (BX + 1, BY, BZ), facing north so a cart meets it travelling along z.
 */
class CartStopsAtIrisFaceTest
{
    /** Half of a minecart's 0.98 width. */
    private static final double CART_HALF = 0.49;
    /** Half of a boat's 1.375 width. */
    private static final double BOAT_HALF = 0.6875;

    private static final int BX = 10, BY = 64, BZ = 20;

    /** Held: a Location keeps its World weakly, so an inline mock can be collected mid-test. */
    private World world;
    private Stargate gate;
    private Block portal;
    private Block portalBeside;

    @BeforeEach
    void setUp() throws Exception
    {
        GateSpatialIndex.clear();
        PluginTestSupport.install(mock(WormholeXTreme.class));
        world = mock(World.class);
        when(world.getName()).thenReturn("w");
        // Each block in the cart's lane answers its own coordinates, so a block in front of
        // the opening is not taken for the opening.
        for (int x = BX - 1; x <= BX + 2; x++)
        {
            for (int z = BZ - 3; z <= BZ + 3; z++)
            {
                final Block b = blockAt(x, z);
                if ((z == BZ) && (x == BX))
                {
                    portal = b;
                }
                else if ((z == BZ) && (x == BX + 1))
                {
                    portalBeside = b;
                }
            }
        }

        gate = new Stargate();
        gate.setGateName("shut");
        gate.setGateWorld(world);
        gate.setGateFacing(BlockFace.NORTH);
        gate.setGatePlayerTeleportLocation(new Location(world, BX + 0.5, BY, BZ - 1.5));
        gate.getGatePortalBlocks().add(new Location(world, BX, BY, BZ));
        gate.getGatePortalBlocks().add(new Location(world, BX + 1, BY, BZ));
        gate.setGateIrisActive(true);
        StargateManager.registerStargate(gate);
        StargateManager.addBlockIndex(portal, gate);
        StargateManager.addBlockIndex(portalBeside, gate);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        StargateManager.removeStargate(gate);
        GateSpatialIndex.clear();
        PluginTestSupport.forgetAllGates();
        PluginTestSupport.remove();
    }

    private Block blockAt(final int x, final int z)
    {
        final Block b = mock(Block.class);
        when(b.getX()).thenReturn(Integer.valueOf(x));
        when(b.getY()).thenReturn(Integer.valueOf(BY));
        when(b.getZ()).thenReturn(Integer.valueOf(z));
        when(b.getWorld()).thenReturn(world);
        when(b.getType()).thenReturn(Material.AIR);
        when(b.getLocation()).thenReturn(new Location(world, x, BY, z));
        when(world.getBlockAt(x, BY, z)).thenReturn(b);
        return b;
    }

    private <T extends Vehicle> T vehicle(final Class<T> type)
    {
        final T veh = mock(type);
        when(veh.getPassengers()).thenReturn(Collections.<Entity>emptyList());
        when(veh.getUniqueId()).thenReturn(UUID.randomUUID());
        when(veh.getVelocity()).thenReturn(new Vector(0, 0, 0.35));
        when(veh.teleport(any(Location.class))).thenReturn(true);
        return veh;
    }

    private void roll(final Vehicle veh, final double fromZ, final double toZ)
    {
        new WormholeXTremeVehicleListener().onVehicleMove(new VehicleMoveEvent(veh,
            new Location(world, BX + 0.5, BY, fromZ), new Location(world, BX + 0.5, BY, toZ)));
    }

    private Location whereItWasPut(final Vehicle veh)
    {
        final ArgumentCaptor<Location> put = ArgumentCaptor.forClass(Location.class);
        verify(veh).teleport(put.capture());
        return put.getValue();
    }

    /**
     * The tick the cart's front first crosses into the iris is the tick it is stopped.
     *
     * <p>Its centre is still a block short of the opening, which is why the old check, asking
     * only about the centre, let it roll on into the drawing for another tick or two.
     */
    @Test
    void aCartIsStoppedTheTickItsFrontReachesTheIris()
    {
        final Minecart cart = vehicle(Minecart.class);

        roll(cart, BZ - 0.6, BZ - 0.25);

        final Location put = whereItWasPut(cart);
        assertTrue(put.getZ() + CART_HALF <= BZ,
            "the cart's front must end at the iris's face, not " + (put.getZ() + CART_HALF - BZ) + " inside it");
        assertTrue(put.getZ() + CART_HALF > BZ - 0.05,
            "and at the face, not a block short of it: front at " + (put.getZ() + CART_HALF));
        verify(cart).setVelocity(new Vector(0, 0, 0));
    }

    /**
     * A cart fast enough that its front skips clean over the one-block iris is caught by its
     * centre, and still put back with its front at the face, not where it was a tick ago.
     */
    @Test
    void aCartFastEnoughToSkipTheIrisIsStillStoppedAtItsFace()
    {
        final Minecart cart = vehicle(Minecart.class);

        roll(cart, BZ - 0.6, BZ + 0.6);

        final Location put = whereItWasPut(cart);
        assertTrue(put.getZ() + CART_HALF <= BZ,
            "put back with its front " + (put.getZ() + CART_HALF - BZ) + " into the iris");
        assertTrue(put.getZ() + CART_HALF > BZ - 0.05,
            "at the face, not where it was a tick ago: front at " + (put.getZ() + CART_HALF));
    }

    /**
     * A cart on a curve, going mostly along the gate as its centre gets in, is put back out
     * across the gate's face, not slid sideways along it with its centre still in the iris.
     */
    @Test
    void aCartOnACurveIsPutBackAcrossTheGatesFace()
    {
        final Minecart cart = vehicle(Minecart.class);

        new WormholeXTremeVehicleListener().onVehicleMove(new VehicleMoveEvent(cart,
            new Location(world, BX - 0.2, BY, BZ - 0.1), new Location(world, BX + 0.4, BY, BZ + 0.1)));

        final Location put = whereItWasPut(cart);
        assertTrue(put.getZ() + CART_HALF <= BZ,
            "the front is " + (put.getZ() + CART_HALF - BZ) + " into the iris");
        assertEquals(BX + 0.4, put.getX(), 1e-9, "left where it was along the gate");
    }

    /** The iris is shut from both sides; a cart from behind stops at the back face. */
    @Test
    void aCartFromBehindStopsAtTheBackOfTheIris()
    {
        final Minecart cart = vehicle(Minecart.class);

        roll(cart, BZ + 1.6, BZ + 1.25);

        final Location put = whereItWasPut(cart);
        assertTrue(put.getZ() - CART_HALF >= BZ + 1,
            "the back face is at z " + (BZ + 1) + "; the cart's front is at " + (put.getZ() - CART_HALF));
        assertTrue(put.getZ() - CART_HALF < BZ + 1.05,
            "at the back face, not short of it: front at " + (put.getZ() - CART_HALF));
        assertEquals(BY, put.getY(), 1e-9, "stopped where it was rolling, not lifted or dropped");
    }

    /** A boat is wider than a cart, so its front reaches the iris sooner. */
    @Test
    void aBoatStopsAtTheIrisByItsOwnWidth()
    {
        final Boat boat = vehicle(Boat.class);

        roll(boat, BZ - 0.9, BZ - 0.6);

        final Location put = whereItWasPut(boat);
        assertTrue(put.getZ() + BOAT_HALF <= BZ, "the boat's bow is " + (put.getZ() + BOAT_HALF - BZ) + " inside");
        assertTrue(put.getZ() + BOAT_HALF > BZ - 0.05,
            "at the face by a boat's width, not a cart's: bow at " + (put.getZ() + BOAT_HALF));
    }

    /** A cart rolling past well short of the iris is left alone. */
    @Test
    void aCartShortOfTheIrisRollsOn()
    {
        final Minecart cart = vehicle(Minecart.class);

        roll(cart, BZ - 2.4, BZ - 1.9);

        verify(cart, never()).teleport(any(Location.class));
    }

    /**
     * A cart already in the opening, the iris shut around it, may roll along and out of it.
     *
     * <p>Rolling sideways across a wide gate its front goes from one block of the opening to
     * the next. Held at the face each time, it would be pinned inside the drawing.
     */
    @Test
    void aCartCaughtInsideTheIrisIsNotTrappedThere()
    {
        final Minecart cart = vehicle(Minecart.class);

        new WormholeXTremeVehicleListener().onVehicleMove(new VehicleMoveEvent(cart,
            new Location(world, BX + 0.3, BY, BZ + 0.5), new Location(world, BX + 0.6, BY, BZ + 0.5)));

        verify(cart, never()).teleport(any(Location.class));
    }

    /** Nor when its centre crosses from one block of the opening to the next. */
    @Test
    void aCartCaughtInsideIsNotPinnedAsItsCentreCrossesTheOpening()
    {
        final Minecart cart = vehicle(Minecart.class);

        new WormholeXTremeVehicleListener().onVehicleMove(new VehicleMoveEvent(cart,
            new Location(world, BX + 0.9, BY, BZ + 0.5), new Location(world, BX + 1.1, BY, BZ + 0.5)));

        verify(cart, never()).teleport(any(Location.class));
    }

    /** With the iris open the idle gate is an empty ring, and the cart rolls into it. */
    @Test
    void aCartRollsOnWhenTheIrisIsOpen()
    {
        gate.setGateIrisActive(false);
        final Minecart cart = vehicle(Minecart.class);

        roll(cart, BZ - 0.6, BZ - 0.25);

        verify(cart, never()).teleport(any(Location.class));
    }

    /** A horizontal gate's iris is real blocks, and the blocks stop the cart themselves. */
    @Test
    void aHorizontalGatesIrisIsLeftToItsBlocks()
    {
        gate.setGateFacing(BlockFace.UP);
        final Minecart cart = vehicle(Minecart.class);

        roll(cart, BZ - 0.6, BZ - 0.25);

        verify(cart, never()).teleport(any(Location.class));
    }
}
