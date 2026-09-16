package com.wormhole_xtreme.wormhole;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Block events for a listener to handle, mocked rather than constructed.
 *
 * <p>Both constructors are marked for removal on newer APIs, and their signatures differ across
 * the supported range. A mock also remembers {@code setCancelled}, which is all a test reads back.
 */
final class BlockEvents
{
    private BlockEvents()
    {
    }

    /** A player hitting a block. */
    static BlockDamageEvent damage(final Player player, final Block block)
    {
        final BlockDamageEvent event = cancellable(mock(BlockDamageEvent.class));
        when(event.getPlayer()).thenReturn(player);
        when(event.getBlock()).thenReturn(block);
        return event;
    }

    /** A player placing a block against another. */
    static BlockPlaceEvent place(final Player player, final Block placed, final Block against)
    {
        final BlockPlaceEvent event = cancellable(mock(BlockPlaceEvent.class));
        when(event.getPlayer()).thenReturn(player);
        when(event.getBlock()).thenReturn(placed);
        when(event.getBlockPlaced()).thenReturn(placed);
        when(event.getBlockAgainst()).thenReturn(against);
        return event;
    }

    private static <E extends Cancellable> E cancellable(final E event)
    {
        final boolean[] cancelled = { false };
        doAnswer(call -> {
            cancelled[0] = call.getArgument(0);
            return null;
        }).when(event).setCancelled(anyBoolean());
        when(event.isCancelled()).thenAnswer(call -> cancelled[0]);
        return event;
    }
}
