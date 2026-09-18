package com.wormhole_xtreme.wormhole;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.model.preview.GatePreviews;

/**
 * A right-click on a build preview's button reaches the preview once.
 *
 * <p>The client sends a click on an entity for each hand, so passing both on would dial a preview
 * and shut it down again in the same click.
 */
class PreviewButtonClickTest
{
    private static PlayerInteractEntityEvent click(final Player player, final Interaction box, final EquipmentSlot hand)
    {
        final PlayerInteractEntityEvent event = mock(PlayerInteractEntityEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getRightClicked()).thenReturn(box);
        when(event.getHand()).thenReturn(hand);
        return event;
    }

    @Test
    void theMainHandsClickIsPassedOnAndCancelledAndTheOffHandsIsNot()
    {
        final Player player = mock(Player.class);
        final Interaction box = mock(Interaction.class);
        final PlayerInteractEntityEvent main = click(player, box, EquipmentSlot.HAND);
        final PlayerInteractEntityEvent off = click(player, box, EquipmentSlot.OFF_HAND);
        try (MockedStatic<GatePreviews> previews = mockStatic(GatePreviews.class))
        {
            previews.when(() -> GatePreviews.pressed(player, box)).thenReturn(true);
            final WormholeXTremePlayerListener listener = new WormholeXTremePlayerListener();

            listener.onPlayerInteractEntity(off);
            previews.verify(() -> GatePreviews.pressed(any(), any()), never());

            listener.onPlayerInteractEntity(main);
            previews.verify(() -> GatePreviews.pressed(player, box));
        }
        verify(main).setCancelled(true);
        verify(off, never()).setCancelled(true);
    }
}
