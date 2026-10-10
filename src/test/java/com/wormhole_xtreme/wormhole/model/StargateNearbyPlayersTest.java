package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/**
 * Who is sent a gate's drawing: the players within 64 blocks of it, measured from the name
 * block when it has one and from where travellers arrive when it does not.
 *
 * <p>The scan sits under every frame of the woosh and the dial, so it is pinned at its edge
 * rather than only at "somebody close, somebody far".
 */
class StargateNearbyPlayersTest
{
    private final World world = mock(World.class);

    private Player playerAt(final double x)
    {
        final Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(new Location(world, x, 64, 0));
        return player;
    }

    private Stargate gateInWorld()
    {
        final Stargate gate = new Stargate();
        gate.setGateWorld(world);
        return gate;
    }

    @Test
    void thoseWithinSixtyFourBlocksAreNearAndThoseBeyondAreNot()
    {
        final Player atTheEdge = playerAt(64);
        final Player justBeyond = playerAt(65);
        final Player onTheOtherSide = playerAt(-64);
        when(world.getPlayers()).thenReturn(List.of(atTheEdge, justBeyond, onTheOtherSide));

        final List<Player> near = StargateBlockSetup.playersNear(gateInWorld(), new Location(world, 0, 64, 0));

        assertEquals(List.of(atTheEdge, onTheOtherSide), near, "64 blocks is still near, 65 is not");
    }

    @Test
    void aGateWithANameBlockIsMeasuredFromIt()
    {
        final Block nameBlock = mock(Block.class);
        when(nameBlock.getLocation()).thenReturn(new Location(world, 500, 64, 0));
        final Player byTheNameBlock = playerAt(520);
        final Player byTheArrivalPoint = playerAt(10);
        when(world.getPlayers()).thenReturn(List.of(byTheNameBlock, byTheArrivalPoint));
        final Stargate gate = gateInWorld();
        gate.setGateNameBlockHolder(nameBlock);
        gate.setGatePlayerTeleportLocation(new Location(world, 0, 64, 0));

        assertEquals(List.of(byTheNameBlock), StargateBlockSetup.nearby(gate),
            "the name block, not the arrival point, is where the gate is");
    }

    @Test
    void aGateWithoutANameBlockIsMeasuredFromWhereTravellersArrive()
    {
        final Player byTheArrivalPoint = playerAt(10);
        final Player far = playerAt(500);
        when(world.getPlayers()).thenReturn(List.of(far, byTheArrivalPoint));
        final Stargate gate = gateInWorld();
        gate.setGatePlayerTeleportLocation(new Location(world, 0, 64, 0));

        assertEquals(List.of(byTheArrivalPoint), StargateBlockSetup.nearby(gate));
    }

    @Test
    void aGateWithNowhereToMeasureFromHasNobodyNear()
    {
        final Player here = playerAt(0);
        when(world.getPlayers()).thenReturn(List.of(here));

        assertTrue(StargateBlockSetup.nearby(gateInWorld()).isEmpty());
    }
}
