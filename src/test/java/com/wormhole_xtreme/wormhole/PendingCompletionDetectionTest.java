package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.command.Complete;
import com.wormhole_xtreme.wormhole.logic.StargateHelper;
import com.wormhole_xtreme.wormhole.model.GateSpatialIndex;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * Finishing a gate by clicking its DHD, once {@code /wormhole complete} is waiting.
 *
 * <p>The click has to work out which way the gate faces. The caller may not know, and the
 * block clicked may not say, so detection falls back to trying all six facings in turn. Only
 * the miss was covered ({@link ButtonLeverHitTest}); what happens when a gate is actually
 * found -- the sweep, the completion, and the pending state being spent -- was not.
 *
 * <p>Reachable now that the project uses Mockito's inline mock maker: the detection call is
 * static, and stubbing it is what lets a found gate be arranged without building one block by
 * block.
 */
class PendingCompletionDetectionTest
{
    private Player player;
    private Block clicked;

    @BeforeEach
    void setUp() throws Exception
    {
        final Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, mock(WormholeXTreme.class));

        GateSpatialIndex.clear();
        player = mock(Player.class);
        when(player.getName()).thenReturn("builder");
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        final World world = mock(World.class);
        when(world.getName()).thenReturn("w");
        clicked = mock(Block.class);
        when(clicked.getType()).thenReturn(Material.STONE_BUTTON);
        when(clicked.getWorld()).thenReturn(world);
        when(clicked.getLocation()).thenReturn(new Location(world, 5, 64, 5));
        when(clicked.getRelative(any(BlockFace.class))).thenReturn(clicked);
    }

    @AfterEach
    void tearDown()
    {
        Complete.removePendingCompletion(player);
        StargateManager.removeIncompleteStargate(player);
        GateSpatialIndex.clear();
        for (final Stargate s : new java.util.ArrayList<Stargate>(StargateManager.getAllGates()))
        {
            if (s != null)
            {
                StargateManager.removeStargate(s);
            }
        }
    }

    private boolean click()
    {
        return GateInteractionHandler.handlePlayerInteractEvent(
            new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null, clicked, BlockFace.NORTH));
    }

    private static Stargate detectedGate()
    {
        final Stargate s = new Stargate();
        s.setGateName("Detected");
        s.setGateFacing(BlockFace.WEST);
        return s;
    }

    /**
     * A failure while completing the detected gate is reported, not swallowed.
     *
     * <p>It used not to be. The outer catch logged to the console and returned, so the player
     * who clicked the DHD was told nothing at all -- no success, no failure -- and the
     * completion stayed pending, so the next click repeated the silence.
     *
     * <p>The comment on that catch always said failing silently would leave them staring at
     * an unbuilt gate, which is what it did.
     */
    @Test
    void aFailureWhileCompletingIsReportedToThePlayer()
    {
        Complete.addPendingCompletion(player, "Detected", "", "");
        final Stargate found = detectedGate();

        try (MockedStatic<StargateHelper> helper = mockStatic(StargateHelper.class))
        {
            helper.when(() -> StargateHelper.checkStargate(any(Block.class), any(BlockFace.class)))
                .thenReturn(null);
            helper.when(() -> StargateHelper.checkStargate(clicked, BlockFace.WEST))
                .thenReturn(found);

            click();
        }

        verify(player).sendMessage(contains("Completing the gate failed"));
        assertNull(Complete.getPendingCompletion(player),
            "and the completion is spent, so the player is not left clicking at nothing");
    }

    /**
     * A click that finds nothing says so and does not spend the pending completion silently.
     *
     * <p>The player is told to click again, so the command has to stay answerable.
     */
    @Test
    void aClickThatFindsNothingSaysSo()
    {
        Complete.addPendingCompletion(player, "Detected", "", "");

        try (MockedStatic<StargateHelper> helper = mockStatic(StargateHelper.class))
        {
            helper.when(() -> StargateHelper.checkStargate(any(Block.class), any(BlockFace.class)))
                .thenReturn(null);

            click();
        }

        verify(player).sendMessage(contains("No gate detected"));
        assertNotNull(Complete.getPendingCompletion(player),
            "a miss leaves the completion waiting so the player can click again");
    }

    /**
     * A shape that throws on one facing does not stop the other five being tried.
     *
     * <p>A malformed custom shape can throw out of detection. That is one shape's problem,
     * not the whole sweep's.
     */
    @Test
    void aShapeThatThrowsOnOneFacingDoesNotStopTheSweep()
    {
        Complete.addPendingCompletion(player, "Detected", "", "");
        final Stargate found = detectedGate();

        try (MockedStatic<StargateHelper> helper = mockStatic(StargateHelper.class))
        {
            helper.when(() -> StargateHelper.checkStargate(any(Block.class), any(BlockFace.class)))
                .thenReturn(null);
            helper.when(() -> StargateHelper.checkStargate(clicked, BlockFace.NORTH))
                .thenThrow(new IllegalStateException("malformed shape"));
            helper.when(() -> StargateHelper.checkStargate(clicked, BlockFace.WEST))
                .thenReturn(found);

            click();
        }

        // Reaching the completion at all is the point: the sweep did not stop at the throw.
        verify(player, never()).sendMessage(contains("No gate detected"));
        assertNull(Complete.getPendingCompletion(player));
    }

    /**
     * A detected gate that will not complete says so in its own words.
     *
     * <p>The interactive route and the plain command route differ only in this message: this
     * one has no half-built gate to name, because the click is what found the gate.
     */
    @Test
    void aDetectedGateThatWillNotCompleteSaysSo()
    {
        Complete.addPendingCompletion(player, "Detected", "", "");
        final Stargate found = detectedGate();

        try (MockedStatic<StargateHelper> helper = mockStatic(StargateHelper.class);
            MockedStatic<StargateManager> mgr = mockStatic(StargateManager.class,
                org.mockito.Mockito.CALLS_REAL_METHODS))
        {
            helper.when(() -> StargateHelper.checkStargate(any(Block.class), any(BlockFace.class)))
                .thenReturn(null);
            helper.when(() -> StargateHelper.checkStargate(clicked, BlockFace.WEST))
                .thenReturn(found);
            mgr.when(() -> StargateManager.completeStargate(any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(false);

            click();
        }

        verify(player).sendMessage(contains("Construction Failed after interactive detection"));
        assertNull(Complete.getPendingCompletion(player), "the click is spent either way");
    }
}
