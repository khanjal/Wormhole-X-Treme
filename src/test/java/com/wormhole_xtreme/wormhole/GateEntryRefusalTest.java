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
import com.wormhole_xtreme.wormhole.model.StargateTestSupport;

/**
 * How a player is held out of the exit end of an open wormhole.
 *
 * <p>Cancelling the move event is the whole mechanism. The previous implementation also
 * rewrote the event's from/to and fired a teleport at the gate's arrival point, so it
 * rubber-banded the player and pulled them further into the ring while claiming to keep
 * them out.
 */
class GateEntryRefusalTest
{
    private World world;
    private Player player;
    private Stargate destination;
    private Stargate origin;

    private static final int BX = 10, BY = 64, BZ = 20;

    @BeforeEach
    void setUp() throws Exception
    {
        GateSpatialIndex.clear();
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        PluginTestSupport.install(plugin);

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
        StargateTestSupport.target(origin, destination);
        StargateManager.registerStargate(origin);

        player = mock(Player.class);
        when(player.getName()).thenReturn("walker");
        when(player.isOp()).thenReturn(true);
        when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
    }

    @AfterEach
    void tearDown()
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

    /**
     * A gate that is not open does nothing at all.
     *
     * <p>Its frame and its ring are still there to walk through, and the portal blocks are
     * still indexed against it. Nothing had tested that walking into one is simply walking:
     * the move handler checks the gate is active before it does anything, and that check
     * survived every mutation the suite could throw at it.
     */
    @Test
    void walkingIntoAClosedGateIsJustWalking()
    {
        destination.setGateActive(false);

        final PlayerMoveEvent event = walkIntoDestination();

        assertFalse(event.isCancelled(), "a closed gate holds nobody back");
        verify(player, never()).teleport(any(Location.class));
        verify(player, never()).sendMessage(contains("incoming wormhole"));
    }

    /**
     * Only the portal itself counts, not the rest of the gate.
     *
     * <p>A gate's structure blocks are indexed against it too, so standing in its frame finds
     * the gate. Treating that as entering the wormhole would teleport somebody who walked
     * behind the ring rather than through it.
     */
    @Test
    void standingInTheFrameRatherThanThePortalIsNotEntering()
    {
        // Same gate, same block, but no longer one of its portal blocks -- which is what the
        // frame of a gate looks like to this check.
        destination.getGatePortalBlocks().clear();

        final PlayerMoveEvent event = walkIntoDestination();

        assertFalse(event.isCancelled(), "walking through the frame is not walking into the wormhole");
        verify(player, never()).teleport(any(Location.class));
    }


    @Test
    void walkingIntoTheExitEndIsRefused()
    {
        final PlayerMoveEvent event = walkIntoDestination();

        assertTrue(event.isCancelled(), "the move should be cancelled, which is what holds the player back");
        verify(player).sendMessage(contains("incoming wormhole"));
    }

    @Test
    void refusalDoesNotTeleportThePlayer()
    {
        // The old code teleported to the gate's arrival point — inside the ring — so a
        // refusal pulled the player in rather than keeping them out.
        walkIntoDestination();

        verify(player, never()).teleport(any(Location.class));
    }

    @Test
    void refusalLeavesTheEventsOwnFromAndToAlone()
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
    void refusalDoesNotTouchDamageImmunity()
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
    void walkingOutOfTheExitEndIsAllowed()
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
    void aPlayerStandingInTheExitIsNotTrappedByRepeatedAttempts()
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
    void movingWithinTheExitPortalIsAllowed()
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
    void walkingInIsStillRefusedAfterTheWalkingOutFix()
    {
        // The control: letting people out must not have let people in. This is the case
        // the whole refusal exists for — a wormhole is an exit at this end, so a mob or a
        // player must not be able to walk back through it.
        assertTrue(walkIntoDestination().isCancelled(),
            "stepping in from outside is still refused");
        verify(player).sendMessage(contains("incoming wormhole"));
    }

    @Test
    void holdingForwardAgainstTheExitIsRefusedOnceNotEveryTick()
    {
        // Cancelling a move returns the player to event.getFrom() — the exact spot they
        // tried to leave — so someone holding a movement key generates a fresh event every
        // tick with an identical from/to pair. Before this was fixed, every one of those
        // sent its own chat line: holding forward against a locked exit for a couple of
        // seconds meant a wall of identical messages.
        for (int attempt = 0; attempt < 6; attempt++)
        {
            assertTrue(walkIntoDestination().isCancelled(),
                "attempt " + attempt + " should still be refused");
        }
        verify(player, times(1)).sendMessage(contains("incoming wormhole"));
    }
}
