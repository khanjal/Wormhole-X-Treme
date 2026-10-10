package com.wormhole_xtreme.wormhole.model.ring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

/**
 * What {@link BukkitRingWorld} chooses to show and to carry: only those within sight of a ring's
 * ends are sent its drawing, and only those standing in the ring's actual blocks are its
 * passengers.
 *
 * <p>Both choices were loops that became streams; these pin the choices themselves.
 */
class BukkitRingWorldTest
{
    private final World world = mock(World.class);

    private static Ring endAt(final int x, final int y, final int z)
    {
        final Ring end = mock(Ring.class);
        when(end.getAnchorX()).thenReturn(x);
        when(end.getAnchorY()).thenReturn(y);
        when(end.getAnchorZ()).thenReturn(z);
        return end;
    }

    private static Player playerAt(final double x, final double y, final double z)
    {
        final Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(new Location(null, x, y, z));
        return player;
    }

    private static Entity entityAt(final double x, final double y, final double z)
    {
        final Entity entity = mock(Entity.class);
        when(entity.getLocation()).thenReturn(new Location(null, x, y, z));
        return entity;
    }

    /**
     * The drawing goes to everyone near either end and to nobody else.
     *
     * <p>Players near each end are both sent it, so an audience built from one end alone fails;
     * the player near neither is not, so one built from everybody in the world fails.
     */
    @Test
    void aBlockChangeGoesToThePlayersNearEitherEndAndNoOneElse()
    {
        final RingPair pair = mock(RingPair.class);
        final Ring endA = endAt(0, 64, 0);
        final Ring endB = endAt(1000, 64, 0);
        when(pair.getEndA()).thenReturn(endA);
        when(pair.getEndB()).thenReturn(endB);
        final Player nearA = playerAt(10, 64, 0);
        final Player nearB = playerAt(990, 64, 0);
        final Player nearNeither = playerAt(500, 64, 0);
        when(world.getPlayers()).thenReturn(List.of(nearA, nearNeither, nearB));
        final Block block = mock(Block.class);
        final BlockData data = mock(BlockData.class);
        when(block.getBlockData()).thenReturn(data);
        when(world.getBlockAt(1, 2, 3)).thenReturn(block);

        new BukkitRingWorld(world, pair).reveal(1, 2, 3);

        verify(nearA).sendBlockChange(any(Location.class), any(BlockData.class));
        verify(nearB).sendBlockChange(any(Location.class), any(BlockData.class));
        verify(nearNeither, never()).sendBlockChange(any(Location.class), any(BlockData.class));
    }

    /**
     * A passenger is an entity standing in one of the ring's blocks, not merely in the box
     * around them.
     */
    @Test
    void onlyThoseStandingInTheRingsOwnBlocksAreItsPassengers()
    {
        final Entity inside = entityAt(10.5, 64.0, 20.5);
        final Entity inTheBoxButOutsideTheBlocks = entityAt(11.5, 64.0, 20.5);
        when(world.getNearbyEntities(any(BoundingBox.class)))
            .thenReturn(List.of(inTheBoxButOutsideTheBlocks, inside));
        final List<int[]> blocks = List.of(new int[] { 10, 64, 20 }, new int[] { 12, 64, 22 });

        final List<RingPassenger> found = new BukkitRingWorld(world, null).passengersIn(blocks);

        assertEquals(1, found.size(), "one of the two was in a ring block");
        assertSame(inside, ((BukkitRingPassenger) found.get(0)).getEntity());
    }

    @Test
    void anEmptyVolumeHoldsNoPassengersAndIsNotEvenAsked()
    {
        final List<RingPassenger> found = new BukkitRingWorld(world, null).passengersIn(List.of());

        assertTrue(found.isEmpty());
        verify(world, never()).getNearbyEntities(any(BoundingBox.class));
    }
}
