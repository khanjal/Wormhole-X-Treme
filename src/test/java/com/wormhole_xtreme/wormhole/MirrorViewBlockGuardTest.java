package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.model.mirror.MirrorWindows;

/**
 * A block drawn over by a mirror's view is left alone: nothing is placed, broken or started on it.
 *
 * <p>From testing: a torch right-clicked into a mirror appeared "momentarily". It was placed on the
 * real air behind the wall, and the next redraw hid it under the view.
 */
class MirrorViewBlockGuardTest
{
    private Player player;
    private Block block;

    @BeforeEach
    void setUp()
    {
        player = mock(Player.class);
        block = mock(Block.class);
        final World world = mock(World.class);
        when(block.getWorld()).thenReturn(world);
    }

    @Test
    void placingIntoTheViewIsRefusedAndTheViewDrawnBack()
    {
        try (MockedStatic<MirrorWindows> mirrors = mockStatic(MirrorWindows.class))
        {
            mirrors.when(() -> MirrorWindows.drew(player, block)).thenReturn(true);
            final BlockPlaceEvent event = BlockEvents.place(player, block, mock(Block.class));

            new WormholeXTremeBlockListener().onBlockPlace(event);

            assertTrue(event.isCancelled(), "a torch into the room would be built unseen behind the wall");
            mirrors.verify(() -> MirrorWindows.resend(player, block, null));
        }
    }

    @Test
    void breakingWhatTheViewDrawsIsRefused()
    {
        try (MockedStatic<MirrorWindows> mirrors = mockStatic(MirrorWindows.class))
        {
            mirrors.when(() -> MirrorWindows.drew(player, block)).thenReturn(true);
            final BlockBreakEvent event = new BlockBreakEvent(block, player);

            new WormholeXTremeBlockListener().onBlockBreak(event);

            assertTrue(event.isCancelled(), "the real block behind the room is not what they hit");
            mirrors.verify(() -> MirrorWindows.resend(player, block, null));
        }
    }

    @Test
    void hittingWhatTheViewDrawsDoesNotStartBreakingIt()
    {
        try (MockedStatic<MirrorWindows> mirrors = mockStatic(MirrorWindows.class))
        {
            mirrors.when(() -> MirrorWindows.drew(player, block)).thenReturn(true);
            final BlockDamageEvent event = BlockEvents.damage(player, block);

            new WormholeXTremeBlockListener().onBlockDamage(event);

            assertTrue(event.isCancelled());
        }
    }

    @Test
    void aBlockNobodyIsShownAsAViewIsLeftToTheRest()
    {
        try (MockedStatic<MirrorWindows> mirrors = mockStatic(MirrorWindows.class))
        {
            final BlockPlaceEvent event = BlockEvents.place(player, block, mock(Block.class));

            new WormholeXTremeBlockListener().onBlockPlace(event);

            assertFalse(event.isCancelled());
        }
    }
}
