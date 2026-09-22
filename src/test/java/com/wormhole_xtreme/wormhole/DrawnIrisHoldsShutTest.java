package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.model.GateSpatialIndex;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.StargateTestSupport;

/**
 * What holds a drawn iris shut now that it is not a wall.
 *
 * <p>A vertical gate's iris is a drawing over air. An honest client stops itself at it, which
 * is what makes it feel solid, but the server has no wall any more: a player who is lagging, a
 * minecart, an arrow and a dropped item all go straight through a picture. Everything the
 * blocks used to do for free is code now, and this is that code.
 *
 * <p>These are the refusals, one per way in. A horizontal gate keeps its real blocks, so it
 * keeps being refused by the wall and must not be refused twice.
 */
class DrawnIrisHoldsShutTest
{
    /** Held: a Location keeps its World weakly, so an inline mock can be collected mid-test. */
    private World world;
    private Player player;
    private Stargate gate;
    private Stargate destination;
    private Block portal;

    private static final int BX = 10, BY = 64, BZ = 20;

    @BeforeEach
    void setUp() throws Exception
    {
        GateSpatialIndex.clear();
        PluginTestSupport.install(mock(WormholeXTreme.class));

        world = mock(World.class);
        when(world.getName()).thenReturn("w");

        portal = mock(Block.class);
        when(portal.getLocation()).thenReturn(new Location(world, BX, BY, BZ));
        when(portal.getX()).thenReturn(Integer.valueOf(BX));
        when(portal.getY()).thenReturn(Integer.valueOf(BY));
        when(portal.getZ()).thenReturn(Integer.valueOf(BZ));
        when(portal.getWorld()).thenReturn(world);
        when(portal.getType()).thenReturn(Material.AIR);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(portal);

        // The gate somebody is walking into: vertical, idle, iris shut.
        gate = new Stargate();
        gate.setGateName("shut");
        gate.setGateWorld(world);
        gate.setGateFacing(BlockFace.NORTH);
        gate.setGatePlayerTeleportLocation(new Location(world, BX + 0.5, BY, BZ + 0.5));
        gate.getGatePortalBlocks().add(new Location(world, BX, BY, BZ));
        gate.setGateIrisActive(true);
        StargateManager.registerStargate(gate);
        StargateManager.addBlockIndex(portal, gate);

        player = mock(Player.class);
        when(player.getName()).thenReturn("walker");
        when(player.isOp()).thenReturn(true);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    }

    @AfterEach
    void tearDown() throws Exception
    {
        StargateManager.removeStargate(gate);
        if (destination != null)
        {
            StargateManager.removeStargate(destination);
        }
        GateSpatialIndex.clear();
        PluginTestSupport.forgetAllGates();
        PluginTestSupport.remove();
    }

    /**
     * Walks the player from outside the gate into its opening.
     *
     * @return the move event, to read the verdict off
     */
    private PlayerMoveEvent walkIntoTheOpening()
    {
        final Location from = new Location(world, BX + 0.5, BY, BZ - 1.5);
        final Location to = new Location(world, BX + 0.5, BY, BZ + 0.5);
        final PlayerMoveEvent event = new PlayerMoveEvent(player, from, to);
        new WormholeXTremePlayerListener().onPlayerMove(event);
        return event;
    }

    // -----------------------------------------------------------------------
    // Walking into one
    // -----------------------------------------------------------------------

    /**
     * A shut iris stops a player, even on a gate nobody has dialled.
     *
     * <p>The move handler used to walk away from any gate that was not active, which was
     * right while the iris was a wall: an idle gate with its iris shut was solid whether or
     * not anything was checking. Drawn, there is nothing there but a picture, so a gate that
     * has never been dialled is exactly the case that needs asking about.
     */
    @Test
    void aShutIrisStopsSomebodyWalkingIntoAnIdleGate()
    {
        assertFalse(gate.isGateActive(), "this gate has not been dialled");

        final PlayerMoveEvent event = walkIntoTheOpening();

        assertTrue(event.isCancelled(), "cancelling the move is what holds them out");
        verify(player).sendMessage(contains("Iris"));
    }

    /**
     * A horizontal gate is left to its wall.
     *
     * <p>Its iris is real blocks, which stop the player without anybody being told to. Adding
     * a refusal on top would mean two mechanisms holding one player back, which is what the
     * rubber-banding this listener already carries a warning about looked like.
     */
    @Test
    void aHorizontalGateIsLeftToItsRealBlocks()
    {
        gate.setGateFacing(BlockFace.UP);

        final PlayerMoveEvent event = walkIntoTheOpening();

        assertFalse(event.isCancelled(), "the blocks refuse this one, not the listener");
        verify(player, never()).sendMessage(contains("Iris"));
    }

    /**
     * Somebody already in the opening can walk out of it.
     *
     * <p>Cancelling a move puts the player back where the move started. For somebody standing
     * in the opening -- the iris shut around them, or they got in before it did -- that is
     * the opening, so every following move is cancelled too and they are stuck until the
     * server drops them. That exact mistake has been made in this listener before.
     */
    @Test
    void somebodyCaughtInsideIsNotTrappedThere()
    {
        final Location inside = new Location(world, BX + 0.5, BY, BZ + 0.5);
        final Location out = new Location(world, BX + 0.5, BY, BZ - 0.5);
        final PlayerMoveEvent event = new PlayerMoveEvent(player, inside, out);

        new WormholeXTremePlayerListener().onPlayerMove(event);

        assertFalse(event.isCancelled(), "a player in the opening has to be able to leave it");
    }

    // -----------------------------------------------------------------------
    // Shooting at one
    // -----------------------------------------------------------------------

    /**
     * An arrow fired into a shut iris is consumed rather than carried through.
     *
     * <p>It used to hit the iris block and stop. With the iris drawn it flies into the cell
     * and reaches the crossing check, which would send it out of the far gate: shooting
     * through a shut iris.
     */
    @Test
    void anArrowThatReachesAShutIrisIsConsumed()
    {
        final Arrow arrow = arrowAtTheOpening();
        dial();
        gate.setGateIrisActive(true);

        assertTrue(GateEntityScanner.sendProjectileThrough(arrow, gate), "the arrow is dealt with here");

        verify(arrow).remove();
        verify(world, never()).spawnArrow(any(Location.class), any(Vector.class), anyFloat(), anyFloat(),
            any(Class.class));
    }

    /**
     * The far gate's iris stops it too.
     *
     * <p>Neither end's iris was ever asked about a projectile. A gate that shut its iris after
     * the wormhole opened went on receiving arrows out of the other end.
     */
    @Test
    void anArrowIsStoppedByTheIrisAtTheFarEndAsWell()
    {
        final Arrow arrow = arrowAtTheOpening();
        dial();
        gate.setGateIrisActive(false);
        destination.setGateIrisActive(true);

        assertTrue(GateEntityScanner.sendProjectileThrough(arrow, gate), "the arrow is dealt with here");

        verify(arrow).remove();
        verify(world, never()).spawnArrow(any(Location.class), any(Vector.class), anyFloat(), anyFloat(),
            any(Class.class));
    }

    /**
     * Gives this gate somewhere to send things, so a refusal is a refusal rather than a
     * gate with nowhere to go.
     */
    private void dial()
    {
        destination = new Stargate();
        destination.setGateName("far");
        destination.setGateWorld(world);
        destination.setGateFacing(BlockFace.SOUTH);
        destination.setGateActive(true);
        destination.setGatePlayerTeleportLocation(new Location(world, 99.5, 70, 99.5));
        StargateTestSupport.target(gate, destination);
        gate.setGateActive(true);
    }

    /**
     * An arrow in flight, in this gate's opening.
     *
     * @return the arrow
     */
    private Arrow arrowAtTheOpening()
    {
        final Arrow arrow = mock(Arrow.class);
        when(arrow.getUniqueId()).thenReturn(UUID.randomUUID());
        when(arrow.getLocation()).thenReturn(new Location(world, BX + 0.5, BY, BZ + 0.5));
        when(arrow.getVelocity()).thenReturn(new Vector(0, 0, -2.4));
        when(arrow.isValid()).thenReturn(true);
        return arrow;
    }

    // -----------------------------------------------------------------------
    // Things drifting into one
    // -----------------------------------------------------------------------

    /**
     * Puts one entity of this type in the opening, as the only thing the sweep will find.
     *
     * @param type
     *            the entity interface to mock
     * @return the entity
     */
    private <T extends org.bukkit.entity.Entity> T inTheOpening(final Class<T> type)
    {
        final T entity = mock(type);
        when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
        when(entity.getLocation()).thenReturn(new Location(world, BX + 0.5, BY, BZ + 0.5));
        when(entity.getPassengers()).thenReturn(java.util.Collections.<org.bukkit.entity.Entity>emptyList());
        when(entity.isInsideVehicle()).thenReturn(false);
        when(entity.isValid()).thenReturn(true);
        when(entity.getVelocity()).thenReturn(new Vector(0, 0, -1));
        when(world.getNearbyEntities(any(org.bukkit.util.BoundingBox.class)))
            .thenReturn(java.util.Collections.<org.bukkit.entity.Entity>singletonList(entity));
        return entity;
    }

    /**
     * A dropped item that reaches a shut iris at the far end is destroyed, not delivered.
     *
     * <p>The entity sweep never asked about either iris. A gate that shut its iris after the
     * wormhole opened went on receiving whatever was tipped into the other end.
     */
    @Test
    void anItemSentAtAShutIrisIsDestroyedRatherThanDelivered() throws Exception
    {
        PluginTestSupport.scheduler(mock(org.bukkit.scheduler.BukkitScheduler.class));
        dial();
        gate.setGateIrisActive(false);
        destination.setGateIrisActive(true);
        final org.bukkit.entity.Item item = inTheOpening(org.bukkit.entity.Item.class);

        try
        {
            GateEntityScanner.create().run();
        }
        finally
        {
            PluginTestSupport.scheduler(null);
        }

        verify(item).remove();
        verify(item, never()).teleport(any(Location.class));
    }

    /**
     * Anything alive is simply not sent, rather than being destroyed.
     *
     * <p>A cow that wanders into a shut gate is a cow standing in a gate. Destroying whatever
     * reaches the iris is for loose items; applied to mobs it would be a gate that kills.
     */
    @Test
    void aMobAtAShutIrisIsLeftStandingRatherThanDestroyed() throws Exception
    {
        PluginTestSupport.scheduler(mock(org.bukkit.scheduler.BukkitScheduler.class));
        dial();
        gate.setGateIrisActive(true);
        final org.bukkit.entity.Zombie zombie = inTheOpening(org.bukkit.entity.Zombie.class);

        try
        {
            GateEntityScanner.create().run();
        }
        finally
        {
            PluginTestSupport.scheduler(null);
        }

        verify(zombie, never()).remove();
        verify(zombie, never()).teleport(any(Location.class));
    }

    // -----------------------------------------------------------------------
    // Walking round one
    // -----------------------------------------------------------------------

    /**
     * Stepping behind a shut iris over an open wormhole restacks it from that side.
     *
     * <p>From behind, the horizon takes the ring and the iris goes a block further off. Nothing
     * but the move listener notices somebody walking round a gate, so without its hook a player
     * goes on seeing the front's picture from the back until they happen to cross a chunk.
     */
    @Test
    void steppingBehindAnOpenGatesShutIrisRestacksItFromThere()
    {
        dial();
        gate.setGateIrisActive(true);
        gate.setGateCustom(true);
        gate.setGateCustomIrisMaterial(Material.IRON_BLOCK);
        gate.setGateCustomPortalMaterial(Material.WATER);
        when(player.isOnline()).thenReturn(true);
        // Everything else in this fixture is the gate's portal block; where the step lands is not.
        final Block ground = mock(Block.class);
        when(ground.getLocation()).thenReturn(new Location(world, BX, BY, BZ + 1));
        when(ground.getType()).thenReturn(Material.AIR);
        when(world.getBlockAt(BX, BY, BZ + 1)).thenReturn(ground);
        // Where the iris goes for a viewer behind: air, and nobody else's opening. Everything
        // else in this fixture answers with the gate's own portal block, which is not free.
        final Block beyond = mock(Block.class);
        when(beyond.getLocation()).thenReturn(new Location(world, BX, BY, BZ - 1));
        when(beyond.getType()).thenReturn(Material.AIR);
        when(beyond.getX()).thenReturn(Integer.valueOf(BX));
        when(beyond.getY()).thenReturn(Integer.valueOf(BY));
        when(beyond.getZ()).thenReturn(Integer.valueOf(BZ - 1));
        when(beyond.getWorld()).thenReturn(world);
        when(world.getBlockAt(BX, BY, BZ - 1)).thenReturn(beyond);
        final org.bukkit.block.data.BlockData horizon = mock(org.bukkit.block.data.BlockData.class);
        final org.bukkit.block.data.BlockData iris = mock(org.bukkit.block.data.BlockData.class);

        try (org.mockito.MockedStatic<com.wormhole_xtreme.wormhole.utils.MaterialUtils> materials =
            org.mockito.Mockito.mockStatic(com.wormhole_xtreme.wormhole.utils.MaterialUtils.class))
        {
            materials.when(() -> com.wormhole_xtreme.wormhole.utils.MaterialUtils.drawnAs(Material.WATER))
                .thenReturn(horizon);
            materials.when(() -> com.wormhole_xtreme.wormhole.utils.MaterialUtils.drawnAs(Material.IRON_BLOCK))
                .thenReturn(iris);
            materials.when(() -> com.wormhole_xtreme.wormhole.utils.MaterialUtils.isAirMaterial(Material.AIR))
                .thenReturn(true);

            // North-facing, so the front is the smaller z. One step from in front to behind: the
            // side has to be judged from where the step ends, not where it began.
            new WormholeXTremePlayerListener().onPlayerMove(new PlayerMoveEvent(player,
                new Location(world, BX + 0.5, BY, BZ - 1.5), new Location(world, BX + 0.5, BY, BZ + 1.5)));
        }

        verify(player).sendBlockChange(org.mockito.ArgumentMatchers.argThat(
            at -> (at != null) && (at.getBlockZ() == BZ)), org.mockito.ArgumentMatchers.eq(horizon));
        verify(player).sendBlockChange(org.mockito.ArgumentMatchers.argThat(
            at -> (at != null) && (at.getBlockZ() == BZ - 1)), org.mockito.ArgumentMatchers.eq(iris));
    }

    // -----------------------------------------------------------------------
    // Swinging at one
    // -----------------------------------------------------------------------

    /**
     * A swing at a drawn iris puts it back, and a flurry of them puts it back once.
     *
     * <p>Mining a block the server does not have hands the client the truth about that cell,
     * which is air, so the iris comes off their screen where they hit it. Nothing else would put
     * it back until they crossed a chunk boundary. But a swing is also every punch and every
     * attack, so a player hammering at the gate must not turn each one into a redraw.
     */
    @Test
    void aSwingAtADrawnIrisRedrawsItOnceNotOnEverySwing() throws Exception
    {
        final org.bukkit.scheduler.BukkitScheduler scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
        PluginTestSupport.scheduler(scheduler);
        when(player.getLocation()).thenReturn(new Location(world, BX + 0.5, BY, BZ - 2.5));
        final org.bukkit.event.player.PlayerAnimationEvent swing =
            mock(org.bukkit.event.player.PlayerAnimationEvent.class);
        when(swing.getPlayer()).thenReturn(player);

        try
        {
            final WormholeXTremePlayerListener listener = new WormholeXTremePlayerListener();
            listener.onPlayerAnimation(swing);
            listener.onPlayerAnimation(swing);
            listener.onPlayerAnimation(swing);
        }
        finally
        {
            PluginTestSupport.scheduler(null);
        }

        // One redraw is two scheduled sends, a tick or so apart. Three swings inside the
        // throttle are still one redraw.
        verify(scheduler, times(2)).scheduleSyncDelayedTask(any(), any(Runnable.class), anyLong());
    }

    // -----------------------------------------------------------------------
    // Building in one
    // -----------------------------------------------------------------------

    /**
     * Nobody builds inside a drawn iris, whatever they are allowed elsewhere.
     *
     * <p>An admin may build in a gate's opening -- that is deliberate. Behind a drawn iris it
     * is not: the cell shows iris to everyone in sight of it, so the block would be invisible,
     * and the opening being empty is what makes the whole thing work.
     */
    @Test
    void nobodyBuildsBehindADrawnIrisEvenAnAdmin()
    {
        assertTrue(WormholeXTremeBlockListener.refusesPlacementIn(player, gate, portal),
            "an op is still not building inside a shut iris");
    }

    /**
     * With the iris open the admin rule is back in charge.
     */
    @Test
    void anAdminMayStillBuildInAnOpenGatesOpening()
    {
        gate.setGateIrisActive(false);

        assertFalse(WormholeXTremeBlockListener.refusesPlacementIn(player, gate, portal),
            "with no iris over it, this is the ordinary opening rule");
    }

    /**
     * A block somebody left in the opening can still be broken while the iris is shut.
     *
     * <p>#243: a block placed in a portal cell could not be broken again, because the cell is
     * indexed to the gate and the break came back as gate structure. The fix allows the break
     * unless the cell holds an iris -- and with a vertical iris drawn over air there is no
     * iris in the cell to protect, so asking only whether the iris is shut would put the bug
     * back for as long as somebody kept it closed.
     */
    @Test
    void aStrayBlockIsStillBreakableBehindADrawnIris()
    {
        assertTrue(WormholeXTremeBlockListener.isStrayBlockInPortal(gate, portal),
            "the drawn iris is not a block, so the block in that cell is somebody's");
    }

    /**
     * A horizontal gate's shut iris really is a gate block, and stays protected.
     */
    @Test
    void aHorizontalGatesShutIrisIsNotAStrayBlock()
    {
        gate.setGateFacing(BlockFace.UP);

        assertFalse(WormholeXTremeBlockListener.isStrayBlockInPortal(gate, portal),
            "that block is the barrier itself");
    }

    // -----------------------------------------------------------------------
    // An idle gate, for everything that is not a player
    // -----------------------------------------------------------------------

    /**
     * A cart rolling at an idle gate's shut iris is stopped where it was, not let through.
     *
     * <p>The cart path walked away from any gate that was not dialled, which was right while
     * the iris was solid. Drawn, the opening is air, and a cart on a rail through the ring
     * rolled straight through the closed iris.
     */
    @Test
    void aCartAtAnIdleGatesShutIrisIsStoppedWhereItWas() throws Exception
    {
        final org.bukkit.entity.Minecart cart = cartAt();
        final Location from = new Location(world, BX + 0.5, BY, BZ - 0.5);
        PluginTestSupport.scheduler(mock(org.bukkit.scheduler.BukkitScheduler.class));

        try
        {
            new WormholeXTremeVehicleListener().onVehicleMove(new org.bukkit.event.vehicle.VehicleMoveEvent(
                cart, from, new Location(world, BX + 0.5, BY, BZ + 0.5)));
        }
        finally
        {
            PluginTestSupport.scheduler(null);
        }

        verify(cart).teleport(from);
        verify(cart).setVelocity(new Vector(0, 0, 0));
    }

    /** With the iris open an idle gate is an empty ring, and a cart rolls on through it. */
    @Test
    void aCartAtAnIdleGateWithItsIrisOpenRollsOn()
    {
        gate.setGateIrisActive(false);
        final org.bukkit.entity.Minecart cart = cartAt();

        new WormholeXTremeVehicleListener().onVehicleMove(new org.bukkit.event.vehicle.VehicleMoveEvent(
            cart, new Location(world, BX + 0.5, BY, BZ - 0.5), new Location(world, BX + 0.5, BY, BZ + 0.5)));

        verify(cart, never()).teleport(any(Location.class));
    }

    /**
     * A cart with nobody in it, rolling east.
     *
     * @return the cart
     */
    private org.bukkit.entity.Minecart cartAt()
    {
        final org.bukkit.entity.Minecart cart = mock(org.bukkit.entity.Minecart.class);
        when(cart.getPassengers()).thenReturn(java.util.Collections.<org.bukkit.entity.Entity>emptyList());
        when(cart.getUniqueId()).thenReturn(UUID.randomUUID());
        when(cart.getVelocity()).thenReturn(new Vector(0, 0, 1));
        return cart;
    }

    /**
     * An item dropped into an idle gate's shut iris is destroyed, the same as at an open one.
     *
     * <p>The entity sweep only ever walked the open gates, so the drawing over an idle gate
     * had nothing behind it and items sat in the opening where no client could see them.
     */
    @Test
    void anItemAtAnIdleGatesShutIrisIsDestroyed()
    {
        final org.bukkit.entity.Item item = inTheOpening(org.bukkit.entity.Item.class);

        GateEntityScanner.create().run();

        verify(item).remove();
    }

    /** A mob at an idle gate's shut iris is left alone, as it is at an open one. */
    @Test
    void aMobAtAnIdleGatesShutIrisIsLeftStanding()
    {
        final org.bukkit.entity.Zombie zombie = inTheOpening(org.bukkit.entity.Zombie.class);

        GateEntityScanner.create().run();

        verify(zombie, never()).remove();
    }

    /**
     * A gate that was never registered draws no iris and answers no swing.
     *
     * <p>Setting the flag is what files a gate in the iris set, and loading sets it partway
     * through reading a gate file. One that then fails to load is never registered and never
     * removed, so without the check it went on drawing a shut iris over its cells until restart.
     */
    @Test
    void aGateNeverRegisteredIsNotTakenForAShutIris()
    {
        gate.setGateIrisActive(false);
        final Stargate halfLoaded = new Stargate();
        halfLoaded.setGateName("half");
        halfLoaded.setGateWorld(world);
        halfLoaded.setGateFacing(BlockFace.NORTH);
        halfLoaded.getGatePortalBlocks().add(new Location(world, BX, BY, BZ));
        halfLoaded.setGateIrisActive(true);
        try
        {
            assertFalse(StargateManager.nearDrawnIris(new Location(world, BX + 0.5, BY, BZ - 1.5)),
                "a gate the server does not have is no iris to swing at");
        }
        finally
        {
            halfLoaded.setGateIrisActive(false);
        }
    }

    /**
     * The far end of a wormhole destroys an item at its shut iris too.
     *
     * <p>That gate is active with no target of its own, so the open-gate sweep, which only takes
     * gates sending somewhere, never reached it, and the idle sweep skipped it for being active.
     * Items sat in its opening behind the drawing.
     */
    @Test
    void anItemAtTheFarEndsShutIrisIsDestroyed()
    {
        gate.setGateActive(true);
        final org.bukkit.entity.Item item = inTheOpening(org.bukkit.entity.Item.class);

        GateEntityScanner.create().run();

        verify(item).remove();
    }

    /**
     * A cart with a rider is turned back the way the forward trip carries one, rider re-seated.
     *
     * <p>A plain teleport is not relied on to bring its passengers along; the forward path has
     * its own for that, which books the re-seat a few ticks later. The bounce used a plain one.
     */
    @Test
    void aRiddenCartTurnedBackAtAShutIrisHasItsRiderReseated() throws Exception
    {
        final org.bukkit.entity.Minecart cart = cartAt();
        final Player rider = mock(Player.class);
        when(rider.getUniqueId()).thenReturn(UUID.randomUUID());
        when(cart.getPassengers()).thenReturn(java.util.List.<org.bukkit.entity.Entity>of(rider));
        final org.bukkit.scheduler.BukkitScheduler scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
        PluginTestSupport.scheduler(scheduler);

        try
        {
            new WormholeXTremeVehicleListener().onVehicleMove(new org.bukkit.event.vehicle.VehicleMoveEvent(
                cart, new Location(world, BX + 0.5, BY, BZ - 0.5), new Location(world, BX + 0.5, BY, BZ + 0.5)));
        }
        finally
        {
            PluginTestSupport.scheduler(null);
        }

        verify(scheduler).scheduleSyncDelayedTask(any(), any(Runnable.class), org.mockito.ArgumentMatchers.eq(5L));
    }
}
