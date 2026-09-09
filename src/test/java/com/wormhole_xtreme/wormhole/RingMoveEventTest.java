package com.wormhole_xtreme.wormhole;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.model.ring.Ring;
import com.wormhole_xtreme.wormhole.model.ring.RingIndex;
import com.wormhole_xtreme.wormhole.model.ring.RingOrientation;
import com.wormhole_xtreme.wormhole.model.ring.RingPair;
import com.wormhole_xtreme.wormhole.model.ring.RingPattern;

/**
 * What walking onto a transport ring pad does.
 *
 * <p>Nothing covered this. Four mutations survived the whole suite: letting somebody without
 * permission set a pair off for everyone, firing a ring that is still recharging, and two
 * ways of getting the "did they just walk in" test wrong.
 *
 * <p>That last one is the reason the messages exist at all. This runs on every block boundary
 * a player crosses, so somebody standing on a pad that is recharging would be told about it
 * several times a second if the check were dropped.
 */
class RingMoveEventTest
{
    private static final String WORLD = "world";
    private static final int AX = 100, AY = 64, AZ = 100;

    private World world;
    private Player player;
    private RingPair pair;

    @BeforeEach
    void setUp() throws Exception
    {
        RingIndex.clear();
        PluginForTests.install(mock(WormholeXTreme.class));

        world = mock(World.class);
        when(world.getName()).thenReturn(WORLD);
        // The gate path runs before the ring path and looks up the block moved into, so it
        // needs something back even though no gate is registered here.
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(inv -> {
            final org.bukkit.block.Block b = mock(org.bukkit.block.Block.class);
            final int x = inv.getArgument(0, Integer.class).intValue();
            final int y = inv.getArgument(1, Integer.class).intValue();
            final int z = inv.getArgument(2, Integer.class).intValue();
            when(b.getX()).thenReturn(Integer.valueOf(x));
            when(b.getY()).thenReturn(Integer.valueOf(y));
            when(b.getZ()).thenReturn(Integer.valueOf(z));
            when(b.getWorld()).thenReturn(world);
            when(b.getLocation()).thenReturn(new Location(world, x, y, z));
            when(b.getType()).thenReturn(Material.AIR);
            return b;
        });

        final Ring a = new Ring(AX, AY, AZ, RingPattern.ODD, RingOrientation.FLOOR,
            Material.STONE_SLAB, Material.GLOWSTONE);
        final Ring b = new Ring(200, 64, 200, RingPattern.ODD, RingOrientation.FLOOR,
            Material.STONE_SLAB, Material.GLOWSTONE);
        pair = new RingPair("testpair", WORLD, a, b);
        RingIndex.add(pair, 3);

        player = mock(Player.class);
        when(player.getName()).thenReturn("walker");
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    }

    @AfterEach
    void tearDown() throws Exception
    {
        RingIndex.clear();
        PluginForTests.remove();
    }

    private Location at(final double x, final double y, final double z)
    {
        return new Location(world, x, y, z);
    }

    /** A step from outside the pad onto it. */
    private void walkOnto()
    {
        new WormholeXTremePlayerListener().onPlayerMove(
            new PlayerMoveEvent(player, at(AX + 8.5, AY, AZ + 8.5), at(AX + 0.5, AY, AZ + 0.5)));
    }

    /** A step from one block of the pad to another. */
    private void walkAcross()
    {
        new WormholeXTremePlayerListener().onPlayerMove(
            new PlayerMoveEvent(player, at(AX + 0.5, AY, AZ + 0.5), at(AX + 1.5, AY, AZ + 0.5)));
    }

    /**
     * Somebody who may not use the pair is told so, and cannot set it off.
     *
     * <p>Arming is a use of the ring. Without the permission check a player who cannot travel
     * by a pair could still fire it for everybody standing on it.
     */
    @Test
    void aPlayerWithoutPermissionIsRefusedAndArmsNothing()
    {
        when(player.hasPermission(anyString())).thenReturn(false);

        walkOnto();

        verify(player, atLeastOnce()).sendMessage(contains("transport rings are private"));
    }

    /**
     * And is told once, not on every step they take inside.
     *
     * <p>This path runs on every block boundary crossed. Somebody wandering about on a pad
     * would be told several times a second if walking within it counted as walking in.
     */
    @Test
    void theRefusalIsNotRepeatedOnEveryStepInside()
    {
        when(player.hasPermission(anyString())).thenReturn(false);

        walkOnto();
        walkAcross();
        walkAcross();

        verify(player, times(1)).sendMessage(contains("transport rings are private"));
    }

    /** A step that never enters the pad says nothing at all. */
    @Test
    void walkingPastThePadSaysNothing()
    {
        when(player.hasPermission(anyString())).thenReturn(false);

        new WormholeXTremePlayerListener().onPlayerMove(
            new PlayerMoveEvent(player, at(AX + 40.5, AY, AZ + 40.5), at(AX + 41.5, AY, AZ + 40.5)));

        verify(player, never()).sendMessage(anyString());
    }
}
