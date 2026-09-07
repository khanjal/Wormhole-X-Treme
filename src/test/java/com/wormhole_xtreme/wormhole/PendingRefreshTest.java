package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
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

import com.wormhole_xtreme.wormhole.command.Refresh;
import com.wormhole_xtreme.wormhole.logic.StargateHelper;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateDBManager;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.StargateNetwork;

/**
 * Rebuilding a gate's geometry with {@code /wormhole refresh}.
 *
 * <p>A refresh throws away what the gate knew about its own blocks and detects them again,
 * which is the point -- but everything that is <em>not</em> geometry has to survive: its name,
 * its owner, its iris code and its network. A refresh that lost those would hand the gate
 * back nameless and ownerless, and it saves immediately afterwards, so there would be nothing
 * to undo it with.
 *
 * <p>Only the "that block is not a gate" refusal was covered. The refresh itself was not.
 */
class PendingRefreshTest
{
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private Player player;
    private World world;
    private Block clicked;

    @BeforeEach
    void setUp() throws Exception
    {
        final Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, mock(WormholeXTreme.class));

        world = mock(World.class);
        when(world.getName()).thenReturn("w");

        clicked = mock(Block.class);
        when(clicked.getType()).thenReturn(Material.STONE_BUTTON);
        when(clicked.getWorld()).thenReturn(world);
        when(clicked.getX()).thenReturn(5);
        when(clicked.getY()).thenReturn(64);
        when(clicked.getZ()).thenReturn(5);
        when(clicked.getLocation()).thenReturn(new Location(world, 5, 64, 5));

        player = mock(Player.class);
        when(player.getName()).thenReturn("builder");
        when(player.getUniqueId()).thenReturn(OWNER);

        clearGates();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        Refresh.removePendingRefresh(player);
        clearGates();
        final Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, null);
    }

    private static void clearGates()
    {
        for (final Stargate s : new ArrayList<Stargate>(StargateManager.getAllGates()))
        {
            if (s != null)
            {
                StargateManager.removeStargate(s);
            }
        }
    }

    /** The gate as it stands before the refresh, indexed against the clicked block. */
    private Stargate existingGate()
    {
        final Stargate gate = new Stargate();
        gate.setGateName("alpha");
        gate.setGateOwner(OWNER.toString());
        gate.setGateOwnerName("builder");
        gate.setGateIrisDeactivationCode("secret");
        gate.setGateWorld(world);
        StargateManager.registerStargate(gate);
        StargateManager.addBlockIndex(clicked, gate);
        return gate;
    }

    /** What detection would hand back: fresh geometry and nothing else. */
    private Stargate freshGeometry()
    {
        final Stargate fresh = new Stargate();
        fresh.setGateWorld(world);
        fresh.setGateFacing(BlockFace.NORTH);
        return fresh;
    }

    private boolean clickWithRefreshPending()
    {
        Refresh.addPendingRefresh(player);
        return GateInteractionHandler.handlePlayerInteractEvent(
            new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null, clicked, BlockFace.NORTH));
    }

    /**
     * A refresh keeps everything that is not geometry.
     *
     * <p>Name, owner, owner name, iris code and network all belong to the gate rather than to
     * its blocks, and the refresh saves straight afterwards -- so anything dropped here is
     * dropped for good.
     */
    @Test
    void aRefreshKeepsEverythingThatIsNotGeometry()
    {
        final Stargate existing = existingGate();
        final StargateNetwork network = StargateManager.addStargateNetwork("traders");
        existing.setGateNetwork(network);
        final Stargate fresh = freshGeometry();

        try (MockedStatic<StargateHelper> helper = mockStatic(StargateHelper.class);
             MockedStatic<StargateDBManager> db = mockStatic(StargateDBManager.class))
        {
            helper.when(() -> StargateHelper.checkStargate(any(), any())).thenReturn(fresh);

            assertTrue(clickWithRefreshPending());
        }

        assertEquals("alpha", fresh.getGateName(), "the name is the gate's, not its blocks'");
        assertEquals(OWNER.toString(), fresh.getGateOwner());
        assertEquals("builder", fresh.getGateOwnerName());
        assertEquals("secret", fresh.getGateIrisDeactivationCode());
        assertSame(network, fresh.getGateNetwork(), "and it stays on its network");
        assertSame(fresh, StargateManager.getStargate("alpha"),
            "the refreshed gate is the one registered under the name now");
    }

    /** The refreshed gate is written to disk, since nothing else would. */
    @Test
    void aRefreshedGateIsSaved()
    {
        existingGate();
        final Stargate fresh = freshGeometry();

        try (MockedStatic<StargateHelper> helper = mockStatic(StargateHelper.class);
             MockedStatic<StargateDBManager> db = mockStatic(StargateDBManager.class))
        {
            helper.when(() -> StargateHelper.checkStargate(any(), any())).thenReturn(fresh);

            assertTrue(clickWithRefreshPending());

            db.verify(() -> StargateDBManager.saveStargate(fresh));
        }
        verify(player).sendMessage(contains("refreshed successfully"));
    }

    /**
     * A refresh that cannot find the geometry leaves the gate exactly as it was.
     *
     * <p>The stale registration is only torn down once fresh geometry is in hand. Doing it
     * first would mean a failed detection deleted a working gate, and the player would be
     * told detection failed while their gate quietly stopped existing.
     */
    @Test
    void aFailedDetectionLeavesTheGateAlone()
    {
        final Stargate existing = existingGate();

        try (MockedStatic<StargateHelper> helper = mockStatic(StargateHelper.class);
             MockedStatic<StargateDBManager> db = mockStatic(StargateDBManager.class))
        {
            helper.when(() -> StargateHelper.checkStargate(any(), any())).thenReturn(null);

            assertTrue(clickWithRefreshPending());

            db.verify(() -> StargateDBManager.saveStargate(any()), never());
        }

        verify(player).sendMessage(contains("geometry detection failed"));
        assertSame(existing, StargateManager.getStargate("alpha"),
            "the gate that could not be re-detected is still the one that is registered");
    }

    /**
     * Detection falls back to trying every facing.
     *
     * <p>The click reports a face, and it is usually the right one -- but a player refreshing
     * a gate may well be clicking its side. Rather than refuse, the handler tries the four
     * horizontal facings in turn.
     */
    @Test
    void detectionTriesEveryFacingBeforeGivingUp()
    {
        existingGate();
        final Stargate fresh = freshGeometry();

        try (MockedStatic<StargateHelper> helper = mockStatic(StargateHelper.class);
             MockedStatic<StargateDBManager> db = mockStatic(StargateDBManager.class))
        {
            // Only WEST works, and it is the last of the four tried.
            helper.when(() -> StargateHelper.checkStargate(any(), any())).thenReturn(null);
            helper.when(() -> StargateHelper.checkStargate(any(), org.mockito.ArgumentMatchers.eq(BlockFace.WEST)))
                .thenReturn(fresh);

            assertTrue(clickWithRefreshPending());
        }

        assertNotNull(StargateManager.getStargate("alpha"));
        assertSame(fresh, StargateManager.getStargate("alpha"),
            "a facing the click did not report is still found");
    }
}
