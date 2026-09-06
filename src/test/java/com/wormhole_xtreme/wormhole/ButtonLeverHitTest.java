package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.command.Complete;
import com.wormhole_xtreme.wormhole.command.Refresh;
import com.wormhole_xtreme.wormhole.model.GateSpatialIndex;

/**
 * Clicking a button or lever, when the player is part-way through a command.
 *
 * <p>{@code buttonLeverHit} is the largest method in this class and had no test of its own:
 * {@link GateActivationSwitchTest} covers the helpers pulled out of its tail, not the
 * pending-command branches at its head. Those branches decide what a click means, so they
 * are worth pinning before the method is taken apart.
 */
class ButtonLeverHitTest
{
    private Player player;

    @BeforeEach
    void setUp() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        final Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, plugin);

        GateSpatialIndex.clear();
        player = mock(Player.class);
        when(player.getName()).thenReturn("clicker");
    }

    @AfterEach
    void tearDown()
    {
        Complete.removePendingCompletion(player);
        Refresh.removePendingRefresh(player);
        GateSpatialIndex.clear();
    }

    /** A button at a fixed spot in a world that reports every block as plain stone. */
    private static Block buttonBlock()
    {
        final World w = mock(World.class);
        when(w.getName()).thenReturn("w");

        final Block b = mock(Block.class);
        when(b.getType()).thenReturn(Material.STONE_BUTTON);
        when(b.getWorld()).thenReturn(w);
        when(b.getX()).thenReturn(Integer.valueOf(5));
        when(b.getY()).thenReturn(Integer.valueOf(64));
        when(b.getZ()).thenReturn(Integer.valueOf(5));
        when(b.getLocation()).thenReturn(new Location(w, 5, 64, 5));
        return b;
    }

    private static PlayerInteractEvent clickOn(final Player p, final Block b)
    {
        return new PlayerInteractEvent(p, Action.RIGHT_CLICK_BLOCK, null, b, org.bukkit.block.BlockFace.NORTH);
    }

    /**
     * A refresh waiting on a click, aimed at a block that is not a registered gate, says so
     * rather than doing nothing, and does not stay pending afterwards.
     */
    @Test
    void aPendingRefreshOnSomethingThatIsNotAGateSaysSoAndClears()
    {
        Refresh.addPendingRefresh(player);
        assertTrue(Refresh.isPendingRefresh(player), "the fixture should start with a refresh pending");

        final boolean handled = GateInteractionHandler.handlePlayerInteractEvent(clickOn(player, buttonBlock()));

        assertTrue(handled, "the click was consumed by the pending refresh");
        verify(player).sendMessage(contains("No registered gate found"));
        assertFalse(Refresh.isPendingRefresh(player), "a pending refresh is spent by the click that answers it");
    }

    /**
     * The same for a pending completion: the click is consumed, the player is told detection
     * found nothing, and the completion does not linger to catch the next click too.
     */
    @Test
    void aPendingCompletionOnSomethingThatIsNotAGateSaysSoAndClears()
    {
        Complete.addPendingCompletion(player, "newgate", "", "");
        org.junit.jupiter.api.Assertions.assertNotNull(Complete.getPendingCompletion(player));

        final boolean handled = GateInteractionHandler.handlePlayerInteractEvent(clickOn(player, buttonBlock()));

        assertTrue(handled, "the click was consumed by the pending completion");
        verify(player).sendMessage(contains("No gate detected"));
    }

    /**
     * A completion outranks a refresh when both are pending. Not an arbitrary choice to
     * pin: whichever runs first spends the click, so the order decides what the player gets.
     */
    @Test
    void aPendingCompletionIsAnsweredBeforeAPendingRefresh()
    {
        Complete.addPendingCompletion(player, "newgate", "", "");
        Refresh.addPendingRefresh(player);

        GateInteractionHandler.handlePlayerInteractEvent(clickOn(player, buttonBlock()));

        verify(player).sendMessage(contains("No gate detected"));
        assertTrue(Refresh.isPendingRefresh(player), "the refresh is still waiting for its own click");
    }

    /** With nothing pending and no gate anywhere near, the click is left alone. */
    @Test
    void anOrdinaryButtonClickIsNotConsumed()
    {
        assertFalse(GateInteractionHandler.handlePlayerInteractEvent(clickOn(player, buttonBlock())),
            "a button that is not a DHD and answers no pending command is not ours");
    }
}
