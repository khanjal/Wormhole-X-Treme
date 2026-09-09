package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import java.util.UUID;

import org.bukkit.GameMode;
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
 * Standing in a portal without being kicked for flying.
 *
 * <p>A portal is AIR on the server with the portal material drawn over it on the client, so
 * a traveller does not drown in a water gate or burn in a lava one. The cost is that the two
 * disagree about physics: the client is simulating water and floats the player upward, while
 * the server sees them climbing through open air with nothing holding them up, calls that
 * flight, and kicks them.
 *
 * <p>Nothing can make the server agree — the block genuinely is not water. Allowing flight
 * for as long as the player is inside the portal is what stops the disagreement being fatal.
 *
 * <p>Most of what this class has to say is that something did <em>not</em> happen, and a test
 * that only says that passes against code which does nothing at all: with the exemption call
 * deleted from onPlayerMove, every never() check here still went green. So each of them is
 * paired with an assertion that the exemption ran and reached the decision it is being
 * credited with. Keep that pairing when adding to this class.
 */
class PortalFlightExemptionTest
{
    private World world;
    private Player player;
    private Stargate destination;
    private Stargate origin;

    private static final int BX = 10, BY = 64, BZ = 20;

    /** Inside the portal: the block the gate lists. */
    private Location inside()
    {
        return new Location(world, BX + 0.5, BY, BZ + 0.5);
    }

    /** Outside it, and not a portal block of any gate. */
    private Location outside()
    {
        return new Location(world, BX + 0.5, BY, BZ - 3.5);
    }

    /**
     * Empties the record of who has been granted portal flight.
     *
     * <p>It is static and outlives a test, so without this a player left holding the
     * exemption by one test is revoked by the next one and the result depends on the order
     * they ran in.
     */
    private static java.util.Set<UUID> flightGrants() throws Exception
    {
        return PrivateStatics.of(WormholeXTremePlayerListener.class, "portalFlightGranted");
    }

    private static void clearFlightGrants() throws Exception
    {
        flightGrants().clear();
    }

    /**
     * Whether the plugin is currently holding an exemption for the test player.
     *
     * <p>The record is what decides whether leaving takes flight back, so it is worth
     * asserting on directly: watching only the setAllowFlight calls cannot tell "never
     * granted" apart from "granted and then forgotten about".
     */
    private boolean holdsPortalFlight() throws Exception
    {
        return flightGrants().contains(player.getUniqueId());
    }

    @BeforeEach
    void setUp() throws Exception
    {
        clearFlightGrants();
        GateSpatialIndex.clear();
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        PluginTestSupport.install(plugin);

        world = mock(World.class);
        when(world.getName()).thenReturn("w");

        final Block portal = mock(Block.class);
        when(portal.getLocation()).thenReturn(new Location(world, BX, BY, BZ));
        when(portal.getWorld()).thenReturn(world);
        when(portal.getType()).thenReturn(Material.AIR);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(portal);

        destination = new Stargate();
        destination.setGateName("destination");
        destination.setGateWorld(world);
        destination.setGateFacing(BlockFace.NORTH);
        destination.setGateActive(true);
        destination.setGatePlayerTeleportLocation(inside());
        destination.getGatePortalBlocks().add(new Location(world, BX, BY, BZ));
        StargateManager.addBlockIndex(portal, destination);

        origin = new Stargate();
        origin.setGateName("origin");
        origin.setGateWorld(world);
        origin.setGateActive(true);
        origin.setGatePlayerTeleportLocation(new Location(world, 500, 70, 500));
        StargateTestSupport.target(origin, destination);
        StargateManager.registerStargate(origin);

        player = mock(Player.class);
        when(player.getName()).thenReturn("floater");
        when(player.isOp()).thenReturn(true);
        when(player.getUniqueId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-00000000f10a"));
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getAllowFlight()).thenReturn(false);
    }

    @AfterEach
    void tearDown()
    {
        StargateManager.removeStargate(origin);
        destination.setGateActive(false);
        origin.setGateActive(false);
        GateSpatialIndex.clear();
    }

    private void move(final Location from, final Location to)
    {
        new WormholeXTremePlayerListener().onPlayerMove(new PlayerMoveEvent(player, from, to));
    }

    @Test
    void aPlayerInThePortalIsAllowedToFly()
    {
        move(outside(), inside());

        verify(player).setAllowFlight(true);
    }

    @Test
    void aPlayerFloatingUpInsideThePortalKeepsTheExemption() throws Exception
    {
        // The actual complaint: floating in the water and staying there. Each rise is
        // another move event, and none of them may take the exemption away while the
        // player is still in the portal.
        move(outside(), inside());
        when(player.getAllowFlight()).thenReturn(true);

        for (int rise = 0; rise < 5; rise++)
        {
            move(inside(), inside());
        }

        // Granted once on the way in and not handed out again on every rise, and still
        // held afterwards. Without these two the test passes just as happily against a
        // handler that granted nothing at all, or one that dropped the record mid-float
        // and so would never take the flight back on the way out.
        verify(player).setAllowFlight(true);
        assertTrue(holdsPortalFlight(),
            "a player who never left the portal should still be holding the exemption");
        verify(player, never()).setAllowFlight(false);
    }

    @Test
    void leavingThePortalTakesTheExemptionBack()
    {
        move(outside(), inside());
        when(player.getAllowFlight()).thenReturn(true);

        move(inside(), outside());

        verify(player).setAllowFlight(false);
        verify(player).setFlying(false);
    }

    @Test
    void aPlayerWhoNeverEntersAPortalIsLeftAlone() throws Exception
    {
        move(outside(), outside());

        verify(player, never()).setAllowFlight(anyBoolean());
        assertFalse(holdsPortalFlight(),
            "walking about outside a gate should not put a player on the exemption list");

        // The same listener, the same fixture, one step further: it grants the moment the
        // player is actually in the portal. That is the half of this test which fails if
        // the exemption never runs at all, which the absence check above cannot tell apart
        // from working correctly.
        move(outside(), inside());

        verify(player).setAllowFlight(true);
        assertTrue(holdsPortalFlight(),
            "stepping into the portal should grant the exemption");
    }

    @Test
    void flightThePluginDidNotGrantIsNotTakenAway() throws Exception
    {
        // Someone in creative, or with flight from another plugin, walks through a gate.
        // Granting is skipped because they already have it, so leaving must not strip it —
        // that would be this plugin taking away something it never gave.
        when(player.getAllowFlight()).thenReturn(true);

        move(outside(), inside());

        // It did reach them standing in the portal and ask what flight they already had,
        // rather than skipping the portal entirely — and having found flight it did not
        // give, it recorded nothing to take back later.
        verify(player, atLeastOnce()).getAllowFlight();
        assertFalse(holdsPortalFlight(),
            "flight the plugin found rather than granted must not be recorded as granted");

        move(inside(), outside());

        verify(player, never()).setAllowFlight(false);
    }

    @Test
    void aCreativePlayerKeepsFlightEvenIfTheyWereGrantedIt() throws Exception
    {
        // Belt and braces for a player who entered in survival and switched mode inside:
        // game mode is the authority on the way out, not what was recorded on the way in.
        move(outside(), inside());

        // The exemption really was granted, so this is the "was granted" case it claims to
        // be rather than the easy one where there was nothing to take back anyway.
        verify(player).setAllowFlight(true);
        when(player.getAllowFlight()).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);

        move(inside(), outside());

        // Leaving ran and consulted game mode, and released the record on the way past —
        // the flight is left alone, but the plugin no longer believes it owes this player
        // a revoke, so a later switch back to survival is not silently stripped.
        verify(player, atLeastOnce()).getGameMode();
        assertFalse(holdsPortalFlight(),
            "leaving should release the record even when the flight itself is left alone");
        verify(player, never()).setAllowFlight(false);
        verify(player, never()).setFlying(false);
    }

    @Test
    void aClosedGateGrantsNothing() throws Exception
    {
        // The exemption follows the drawn portal. No portal, no client-side water, nothing
        // to float on, and no reason to hand out flight.
        destination.setGateActive(false);

        move(outside(), inside());

        verify(player, never()).setAllowFlight(anyBoolean());
        assertFalse(holdsPortalFlight(),
            "a gate with no portal drawn in it should put nobody on the exemption list");

        // And it is the gate being shut that decides that, not the block being unreachable
        // in this fixture: open the same gate and the same step into the same block does
        // hand out the exemption.
        destination.setGateActive(true);

        move(outside(), inside());

        verify(player).setAllowFlight(true);
        assertTrue(holdsPortalFlight(),
            "the same block in the same gate should grant once the gate is open");
    }
}
