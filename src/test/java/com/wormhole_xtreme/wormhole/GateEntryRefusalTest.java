package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.model.GateSpatialIndex;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * How a player is held out of the exit end of an open wormhole.
 *
 * <p>Cancelling the move event is the whole mechanism. The previous implementation also
 * rewrote the event's from/to and fired a teleport at the gate's arrival point, so it
 * rubber-banded the player and pulled them further into the ring while claiming to keep
 * them out.
 */
public class GateEntryRefusalTest
{
    private World world;
    private Player player;
    private Stargate destination;
    private Stargate origin;

    private static final int BX = 10, BY = 64, BZ = 20;

    @BeforeEach
    public void setUp() throws Exception
    {
        GateSpatialIndex.clear();
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        final java.lang.reflect.Field pf = WormholeXTreme.class.getDeclaredField("thisPlugin");
        pf.setAccessible(true);
        pf.set(null, plugin);

        world = mock(World.class);
        when(world.getName()).thenReturn("w");

        final Block portal = mock(Block.class);
        when(portal.getLocation()).thenReturn(new Location(world, BX, BY, BZ));
        when(portal.getWorld()).thenReturn(world);
        when(portal.getType()).thenReturn(Material.AIR);
        when(world.getBlockAt(BX, BY, BZ)).thenReturn(portal);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(portal);

        // The destination end: active, holding no target of its own.
        destination = new Stargate();
        destination.setGateName("destination");
        destination.setGateWorld(world);
        destination.setGateFacing(BlockFace.NORTH);
        destination.setGateActive(true);
        destination.setGatePlayerTeleportLocation(new Location(world, BX + 0.5, BY, BZ + 0.5));
        destination.getGatePortalBlocks().add(new Location(world, BX, BY, BZ));
        StargateManager.addBlockIndex(portal, destination);

        // Something dialling into it, so the incoming-wormhole check finds a connection.
        origin = new Stargate();
        origin.setGateName("origin");
        origin.setGateWorld(world);
        origin.setGateActive(true);
        origin.setGatePlayerTeleportLocation(new Location(world, 500, 70, 500));
        final java.lang.reflect.Field tf = Stargate.class.getDeclaredField("gateTarget");
        tf.setAccessible(true);
        tf.set(origin, destination);
        StargateManager.registerStargate(origin);

        player = mock(Player.class);
        when(player.getName()).thenReturn("walker");
        when(player.isOp()).thenReturn(true);
    }

    @AfterEach
    public void tearDown()
    {
        StargateManager.removeStargate(origin);
        GateSpatialIndex.clear();
    }

    private PlayerMoveEvent walkIntoDestination()
    {
        final Location from = new Location(world, BX + 0.5, BY, BZ - 1.5);
        final Location to = new Location(world, BX + 0.5, BY, BZ + 0.5);
        final PlayerMoveEvent event = new PlayerMoveEvent(player, from, to);
        new WormholeXTremePlayerListener().onPlayerMove(event);
        return event;
    }

    @Test
    public void walkingIntoTheExitEndIsRefused()
    {
        final PlayerMoveEvent event = walkIntoDestination();

        assertTrue(event.isCancelled(), "the move should be cancelled, which is what holds the player back");
        verify(player).sendMessage(contains("incoming wormhole"));
    }

    @Test
    public void refusalDoesNotTeleportThePlayer()
    {
        // The old code teleported to the gate's arrival point — inside the ring — so a
        // refusal pulled the player in rather than keeping them out.
        walkIntoDestination();

        verify(player, never()).teleport(any(Location.class));
    }

    @Test
    public void refusalLeavesTheEventsOwnFromAndToAlone()
    {
        // Cancelling returns the player to getFrom(). Rewriting from/to as well meant
        // three mechanisms competing in one tick, which is what caused the rubber-band.
        final Location from = new Location(world, BX + 0.5, BY, BZ - 1.5);
        final Location to = new Location(world, BX + 0.5, BY, BZ + 0.5);
        final PlayerMoveEvent event = new PlayerMoveEvent(player, from, to);

        new WormholeXTremePlayerListener().onPlayerMove(event);

        assertEquals(BZ - 1.5, event.getFrom().getZ(), 1e-9, "from should be untouched");
    }

    @Test
    public void refusalDoesNotTouchDamageImmunity()
    {
        // setNoDamageTicks was copied from the teleport path; nothing here deals damage.
        walkIntoDestination();

        verify(player, never()).setNoDamageTicks(anyInt());
    }

    // -----------------------------------------------------------------------
    // Walking back out
    // -----------------------------------------------------------------------

    /** A step from the portal block itself to the block outside it. */
    private PlayerMoveEvent walkOutOfDestination()
    {
        final Location from = new Location(world, BX + 0.5, BY, BZ + 0.5);
        final Location to = new Location(world, BX + 0.5, BY, BZ - 1.5);
        final PlayerMoveEvent event = new PlayerMoveEvent(player, from, to);
        new WormholeXTremePlayerListener().onPlayerMove(event);
        return event;
    }

    @Test
    public void walkingOutOfTheExitEndIsAllowed()
    {
        // The traveller who just came through arrives standing in the ring, and refusing a
        // move is cancelling it — which holds them exactly where they are. Applying the
        // refusal to someone already inside trapped them: every step cancelled, the message
        // repeated once per move, until the client gave up and dropped the connection.
        final PlayerMoveEvent event = walkOutOfDestination();

        assertFalse(event.isCancelled(), "a player already in the portal must be able to leave it");
        verify(player, never()).sendMessage(contains("incoming wormhole"));
    }

    @Test
    public void aPlayerStandingInTheExitIsNotTrappedByRepeatedAttempts()
    {
        // The failure was not one refused step, it was never being able to take one. Each
        // of these is a fresh event, the way the client retries after a cancelled move.
        for (int attempt = 0; attempt < 5; attempt++)
        {
            assertFalse(walkOutOfDestination().isCancelled(),
                "attempt " + attempt + " should not be cancelled");
        }
        verify(player, never()).sendMessage(contains("incoming wormhole"));
    }

    @Test
    public void movingWithinTheExitPortalIsAllowed()
    {
        // Shuffling inside the ring is still not an entry, so it must not be refused
        // either. Both ends of this move are the portal block.
        final Location from = new Location(world, BX + 0.2, BY, BZ + 0.2);
        final Location to = new Location(world, BX + 0.8, BY, BZ + 0.8);
        final PlayerMoveEvent event = new PlayerMoveEvent(player, from, to);

        new WormholeXTremePlayerListener().onPlayerMove(event);

        assertFalse(event.isCancelled());
    }

    @Test
    public void walkingInIsStillRefusedAfterTheWalkingOutFix()
    {
        // The control: letting people out must not have let people in. This is the case
        // the whole refusal exists for — a wormhole is an exit at this end, so a mob or a
        // player must not be able to walk back through it.
        assertTrue(walkIntoDestination().isCancelled(),
            "stepping in from outside is still refused");
        verify(player).sendMessage(contains("incoming wormhole"));
    }
}
