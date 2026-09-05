package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.*;
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
    @SuppressWarnings("unchecked")
    private static void clearFlightGrants() throws Exception
    {
        final java.lang.reflect.Field f =
            WormholeXTremePlayerListener.class.getDeclaredField("portalFlightGranted");
        f.setAccessible(true);
        ((java.util.Set<UUID>) f.get(null)).clear();
    }

    @BeforeEach
    void setUp() throws Exception
    {
        clearFlightGrants();
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
        final java.lang.reflect.Field tf = Stargate.class.getDeclaredField("gateTarget");
        tf.setAccessible(true);
        tf.set(origin, destination);
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
    void aPlayerFloatingUpInsideThePortalKeepsTheExemption()
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
    void aPlayerWhoNeverEntersAPortalIsLeftAlone()
    {
        move(outside(), outside());

        verify(player, never()).setAllowFlight(anyBoolean());
    }

    @Test
    void flightThePluginDidNotGrantIsNotTakenAway()
    {
        // Someone in creative, or with flight from another plugin, walks through a gate.
        // Granting is skipped because they already have it, so leaving must not strip it —
        // that would be this plugin taking away something it never gave.
        when(player.getAllowFlight()).thenReturn(true);

        move(outside(), inside());
        move(inside(), outside());

        verify(player, never()).setAllowFlight(false);
    }

    @Test
    void aCreativePlayerKeepsFlightEvenIfTheyWereGrantedIt()
    {
        // Belt and braces for a player who entered in survival and switched mode inside:
        // game mode is the authority on the way out, not what was recorded on the way in.
        move(outside(), inside());
        when(player.getAllowFlight()).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);

        move(inside(), outside());

        verify(player, never()).setAllowFlight(false);
    }

    @Test
    void aClosedGateGrantsNothing()
    {
        // The exemption follows the drawn portal. No portal, no client-side water, nothing
        // to float on, and no reason to hand out flight.
        destination.setGateActive(false);

        move(outside(), inside());

        verify(player, never()).setAllowFlight(anyBoolean());
    }
}
