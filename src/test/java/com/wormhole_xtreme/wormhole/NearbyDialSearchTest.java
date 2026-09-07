package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.logic.StargateHelper;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions;

/**
 * Clicking beside a gate's DHD rather than on it.
 *
 * <p>When a click finds no gate, the handler probes the 26 blocks around the one clicked
 * looking for a lever or button that turns out to be an unregistered gate's dial. Nothing
 * covered any of it, and it is the kind of search that is easy to make too eager: three of
 * its four guards exist only to stop it prompting somebody who was not building anything.
 */
class NearbyDialSearchTest
{
    private World world;
    private Player player;
    private Block clicked;
    private Map<String, Block> grid;

    private static String key(final int x, final int y, final int z)
    {
        return x + "," + y + "," + z;
    }

    @BeforeEach
    void setUp() throws Exception
    {
        final Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, mock(WormholeXTreme.class));

        grid = new HashMap<>();
        world = mock(World.class);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(invocation ->
        {
            final int x = invocation.getArgument(0);
            final int y = invocation.getArgument(1);
            final int z = invocation.getArgument(2);
            final Block existing = grid.get(key(x, y, z));
            return existing != null ? existing : plain(x, y, z);
        });

        player = mock(Player.class);
        when(player.getName()).thenReturn("builder");

        clicked = plain(0, 64, 0);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        final Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, null);
    }

    /** A block that is nothing in particular. */
    private Block plain(final int x, final int y, final int z)
    {
        final Block b = mock(Block.class);
        when(b.getX()).thenReturn(x);
        when(b.getY()).thenReturn(y);
        when(b.getZ()).thenReturn(z);
        when(b.getWorld()).thenReturn(world);
        when(b.getType()).thenReturn(Material.STONE);
        when(b.getLocation()).thenReturn(new Location(world, x, y, z));
        return b;
    }

    /**
     * Puts a lever at the given offset from the clicked block, with a block behind it in the
     * direction the search will look. Returns the lever.
     */
    private Block leverAt(final int dx, final int dy, final int dz, final Material behind)
    {
        final Block lever = plain(dx, 64 + dy, dz);
        when(lever.getType()).thenReturn(Material.LEVER);
        final Block holder = plain(dx, 64 + dy, dz);
        when(holder.getType()).thenReturn(behind);
        when(lever.getRelative(any(BlockFace.class))).thenReturn(holder);
        when(lever.getBlockData()).thenReturn(null);
        grid.put(key(dx, 64 + dy, dz), lever);
        return lever;
    }

    /** An unregistered gate, which is what the search is looking for. */
    private static Stargate unregisteredGate()
    {
        final Stargate gate = mock(Stargate.class);
        when(gate.getGateDialLeverBlock()).thenReturn(null);
        return gate;
    }

    /** Nothing next to the click means nothing to report. */
    @Test
    void barePlainBlocksFindNothing()
    {
        try (MockedStatic<StargateHelper> helper = mockStatic(StargateHelper.class))
        {
            helper.when(() -> StargateHelper.isPossibleGateFrameMaterial(any())).thenReturn(true);

            assertFalse(GateInteractionHandler.findGateFromNearbyDial(clicked, player));
            verify(player, never()).sendMessage(contains("Valid Stargate Design"));
        }
    }

    /**
     * A lever next to the click whose gate is complete and unregistered gets the player told
     * how to finish it.
     */
    @Test
    void anUnregisteredGateBesideTheClickIsAnnounced()
    {
        leverAt(1, 0, 0, Material.OBSIDIAN);
        final Stargate found = unregisteredGate();

        try (MockedStatic<StargateHelper> helper = mockStatic(StargateHelper.class);
             MockedStatic<StargateManager> manager = mockStatic(StargateManager.class);
             MockedStatic<WXPermissions> perms = mockStatic(WXPermissions.class))
        {
            helper.when(() -> StargateHelper.isPossibleGateFrameMaterial(any())).thenReturn(true);
            helper.when(() -> StargateHelper.checkStargate(any(), any())).thenReturn(found);
            perms.when(() -> WXPermissions.checkWXPermissions(any(Player.class), any(Stargate.class), any()))
                .thenReturn(true);

            assertTrue(GateInteractionHandler.findGateFromNearbyDial(clicked, player));

            verify(player).sendMessage(contains("Valid Stargate Design detected via nearby click"));
            manager.verify(() -> StargateManager.addIncompleteStargate(player, found));
        }
    }

    /**
     * A lever whose backing block is not a frame material is passed over without asking for
     * a shape scan, which is the guard that keeps this cheap.
     */
    @Test
    void aLeverOnNothingIsNotProbed()
    {
        leverAt(1, 0, 0, Material.AIR);

        try (MockedStatic<StargateHelper> helper = mockStatic(StargateHelper.class))
        {
            helper.when(() -> StargateHelper.isPossibleGateFrameMaterial(Material.AIR)).thenReturn(false);
            helper.when(() -> StargateHelper.isPossibleGateFrameMaterial(Material.OBSIDIAN)).thenReturn(true);

            assertFalse(GateInteractionHandler.findGateFromNearbyDial(clicked, player));
            helper.verify(() -> StargateHelper.checkStargate(any(), any()), never());
        }
    }

    /**
     * A gate that is already registered is skipped.
     *
     * <p>Otherwise placing a lever anywhere near a finished gate would prompt the player to
     * complete a gate that already exists.
     */
    @Test
    void anAlreadyRegisteredGateIsNotOfferedAgain()
    {
        leverAt(1, 0, 0, Material.OBSIDIAN);
        final Block existingDial = plain(9, 64, 9);
        final Stargate found = mock(Stargate.class);
        when(found.getGateDialLeverBlock()).thenReturn(existingDial);

        try (MockedStatic<StargateHelper> helper = mockStatic(StargateHelper.class);
             MockedStatic<StargateManager> manager = mockStatic(StargateManager.class))
        {
            helper.when(() -> StargateHelper.isPossibleGateFrameMaterial(any())).thenReturn(true);
            helper.when(() -> StargateHelper.checkStargate(any(), any())).thenReturn(found);
            manager.when(() -> StargateManager.getGateFromBlock(existingDial)).thenReturn(found);

            assertFalse(GateInteractionHandler.findGateFromNearbyDial(clicked, player));
            verify(player, never()).sendMessage(contains("Valid Stargate Design"));
        }
    }

    /** Without the build permission the player is refused rather than offered the gate. */
    @Test
    void aPlayerWhoMayNotBuildIsRefused()
    {
        leverAt(1, 0, 0, Material.OBSIDIAN);
        final Stargate found = unregisteredGate();

        try (MockedStatic<StargateHelper> helper = mockStatic(StargateHelper.class);
             MockedStatic<StargateManager> manager = mockStatic(StargateManager.class);
             MockedStatic<WXPermissions> perms = mockStatic(WXPermissions.class))
        {
            helper.when(() -> StargateHelper.isPossibleGateFrameMaterial(any())).thenReturn(true);
            helper.when(() -> StargateHelper.checkStargate(any(), any())).thenReturn(found);
            perms.when(() -> WXPermissions.checkWXPermissions(any(Player.class), any(Stargate.class), any()))
                .thenReturn(false);

            assertTrue(GateInteractionHandler.findGateFromNearbyDial(clicked, player),
                "the search still found something; the player just may not have it");

            verify(player, never()).sendMessage(contains("Valid Stargate Design"));
            manager.verify(() -> StargateManager.addIncompleteStargate(any(), any()), never());
        }
    }

    /**
     * The block actually clicked is never one of the 26 probed.
     *
     * <p>It has already been tried by the caller, and probing it again would report a gate
     * the direct path just declined to report.
     */
    @Test
    void theClickedBlockItselfIsNotProbed()
    {
        // The clicked block is the only lever anywhere near, and it would match if probed.
        when(clicked.getType()).thenReturn(Material.LEVER);
        grid.put(key(0, 64, 0), clicked);
        final Block holder = plain(0, 64, 0);
        when(holder.getType()).thenReturn(Material.OBSIDIAN);
        when(clicked.getRelative(any(BlockFace.class))).thenReturn(holder);
        when(clicked.getBlockData()).thenReturn(null);
        final Stargate wouldMatch = unregisteredGate();

        try (MockedStatic<StargateHelper> helper = mockStatic(StargateHelper.class))
        {
            helper.when(() -> StargateHelper.isPossibleGateFrameMaterial(any())).thenReturn(true);
            helper.when(() -> StargateHelper.checkStargate(any(), any())).thenReturn(wouldMatch);

            assertFalse(GateInteractionHandler.findGateFromNearbyDial(clicked, player),
                "the centre of the 3x3x3 is skipped, so nothing is found");
        }
    }
}
