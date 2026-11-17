package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.utils.MaterialUtils;

/**
 * The parts of the backdrop geometry and drawing that {@code PortalBehindTheIrisTest} does not
 * reach: the forecourt, the front-or-behind test, which cells are free to draw in, and what is
 * sent when the horizon is taken back.
 *
 * <p>Written to pin the behaviour before the cluster moved out of {@code StargateBlockSetup}
 * (#734), so the move could be shown to change nothing.
 */
class PortalBackdropTest
{
    /** Held: a Location keeps its World weakly, so an inline mock can be collected mid-test. */
    private World world;
    private Stargate gate;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install();
        world = mock(World.class);
        when(world.getName()).thenReturn("world");

        gate = new Stargate();
        gate.setGateName("BackdropGate");
        gate.setGateWorld(world);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PluginTestSupport.forgetAllGates();
        PluginTestSupport.remove();
    }

    private Block standing(final int x, final int y, final int z, final Material type)
    {
        final Block block = mock(Block.class);
        final BlockData data = mock(BlockData.class);
        when(block.getType()).thenReturn(type);
        when(block.getBlockData()).thenReturn(data);
        when(world.getBlockAt(x, y, z)).thenReturn(block);
        return block;
    }

    private Player watcherAt(final double x, final double y, final double z)
    {
        final Player p = mock(Player.class);
        when(p.getLocation()).thenReturn(new Location(world, x, y, z));
        return p;
    }

    // -----------------------------------------------------------------------
    // Forecourt and offset
    // -----------------------------------------------------------------------

    /**
     * The forecourt is the mirror of the backdrop: one block along the facing, in portal order.
     */
    @Test
    void theForecourtSitsOneBlockInFrontOfEachPortalCellInOrder()
    {
        gate.setGateFacing(BlockFace.SOUTH);
        gate.getGatePortalBlocks().add(new Location(world, 10, 64, 20));
        gate.getGatePortalBlocks().add(new Location(world, 11, 65, 20));

        final List<Location> front = PortalBackdrop.portalForecourtCells(gate);

        assertEquals(2, front.size(), "one cell per portal cell");
        assertEquals(21, front.get(0).getBlockZ(), "south faces +z, so in front is +z");
        assertEquals(10, front.get(0).getBlockX(), "first cell first");
        assertEquals(64, front.get(0).getBlockY(), "nothing moves up or down");
        assertEquals(11, front.get(1).getBlockX(), "second cell second");
        assertEquals(65, front.get(1).getBlockY(), "and keeps its own height");
        assertSame(world, front.get(0).getWorld(), "in the gate's own world");
    }

    /**
     * A horizontal gate's forecourt is above it, the backdrop's mirror.
     */
    @Test
    void aHorizontalGatesForecourtIsAboveIt()
    {
        gate.setGateFacing(BlockFace.UP);
        gate.getGatePortalBlocks().add(new Location(world, 4, 70, 9));

        assertEquals(71, PortalBackdrop.portalForecourtCells(gate).get(0).getBlockY(),
            "up-facing, so in front of it is one block up");
        assertEquals(69, PortalBackdrop.portalBackdropCells(gate).get(0).getBlockY(),
            "and the backdrop is the other way");
    }

    /**
     * No gate, no world or no facing gives an empty list, from either side.
     */
    @Test
    void aGateMissingItsWorldOrFacingHasNoCellsFromEitherSide()
    {
        gate.setGateFacing(BlockFace.SOUTH);
        gate.getGatePortalBlocks().add(new Location(world, 0, 64, 0));
        final Stargate noWorld = new Stargate();
        noWorld.setGateFacing(BlockFace.SOUTH);
        noWorld.getGatePortalBlocks().add(new Location(world, 0, 64, 0));
        final Stargate noFacing = new Stargate();
        noFacing.setGateWorld(world);
        noFacing.getGatePortalBlocks().add(new Location(world, 0, 64, 0));

        assertTrue(PortalBackdrop.portalForecourtCells(null).isEmpty(), "no gate");
        assertTrue(PortalBackdrop.portalForecourtCells(noWorld).isEmpty(), "no world");
        assertTrue(PortalBackdrop.portalForecourtCells(noFacing).isEmpty(), "no facing");
        assertTrue(PortalBackdrop.portalBackdropCells(noWorld).isEmpty(), "no world, backdrop");
        assertEquals(1, PortalBackdrop.portalForecourtCells(gate).size(), "a whole gate does");
    }

    // -----------------------------------------------------------------------
    // seesFront
    // -----------------------------------------------------------------------

    /**
     * Standing exactly in the plane of the opening counts as the front.
     */
    @Test
    void standingInTheOpeningsPlaneCountsAsTheFront()
    {
        gate.setGateFacing(BlockFace.SOUTH);
        gate.getGatePortalBlocks().add(new Location(world, 10, 64, 20));

        assertTrue(PortalBackdrop.seesFront(gate, new Location(world, 10.5, 64.5, 20.5)),
            "in the plane is the front");
        assertFalse(PortalBackdrop.seesFront(gate, new Location(world, 10.5, 64.5, 20.4)),
            "a hair behind it is not");
        assertTrue(PortalBackdrop.seesFront(gate, new Location(world, 10.5, 64.5, 20.6)),
            "a hair in front is");
    }

    /**
     * Missing facing, eye or portal cells all read as the front.
     */
    @Test
    void whenItCannotTellItAnswersFront()
    {
        gate.getGatePortalBlocks().add(new Location(world, 10, 64, 20));
        assertTrue(PortalBackdrop.seesFront(gate, new Location(world, 10.5, 64, 0.0)),
            "no facing yet");

        final Stargate empty = new Stargate();
        empty.setGateWorld(world);
        empty.setGateFacing(BlockFace.SOUTH);
        assertTrue(PortalBackdrop.seesFront(empty, new Location(world, 10.5, 64, 0.0)),
            "no portal cells yet, from behind where they would be");

        gate.setGateFacing(BlockFace.SOUTH);
        assertTrue(PortalBackdrop.seesFront(gate, null), "no eye");
    }

    /**
     * It is measured from the first portal cell, not the last.
     */
    @Test
    void theFirstPortalCellDecidesTheSide()
    {
        gate.setGateFacing(BlockFace.SOUTH);
        gate.getGatePortalBlocks().add(new Location(world, 10, 64, 20));
        gate.getGatePortalBlocks().add(new Location(world, 10, 64, 25));

        assertTrue(PortalBackdrop.seesFront(gate, new Location(world, 10.5, 64.5, 22.0)),
            "in front of the first cell, though behind the second");
    }

    // -----------------------------------------------------------------------
    // backdropIsFree
    // -----------------------------------------------------------------------

    /**
     * Only plain air that is not another gate's opening is free.
     */
    @Test
    void onlyAirThatIsNotAnotherGatesOpeningIsFree()
    {
        final Block air = standing(1, 64, 1, Material.AIR);
        standing(2, 64, 2, Material.CAVE_AIR);
        standing(3, 64, 3, Material.STONE);
        final Block neighbours = standing(4, 64, 4, Material.AIR);

        try (MockedStatic<StargateManager> manager = mockStatic(StargateManager.class))
        {
            manager.when(() -> StargateManager.isPortalBlock(air)).thenReturn(false);
            manager.when(() -> StargateManager.isPortalBlock(neighbours)).thenReturn(true);

            assertTrue(PortalBackdrop.backdropIsFree(new Location(world, 1, 64, 1)), "air");
            assertTrue(PortalBackdrop.backdropIsFree(new Location(world, 2, 64, 2)), "cave air");
            assertFalse(PortalBackdrop.backdropIsFree(new Location(world, 3, 64, 3)), "stone");
            assertFalse(PortalBackdrop.backdropIsFree(new Location(world, 4, 64, 4)),
                "air that is a neighbouring gate's opening");
        }
    }

    // -----------------------------------------------------------------------
    // sendPortalBackdropTo
    // -----------------------------------------------------------------------

    /**
     * Taking the horizon back sends the real block, and only for the free cells.
     */
    @Test
    void takingTheHorizonBackSendsTheRealBlocksForFreeCellsOnly()
    {
        gate.setGateFacing(BlockFace.SOUTH);
        gate.getGatePortalBlocks().add(new Location(world, 10, 64, 20));
        gate.getGatePortalBlocks().add(new Location(world, 10, 65, 20));
        final Block free = standing(10, 64, 19, Material.AIR);
        standing(10, 65, 19, Material.STONE);
        final BlockData freeData = free.getBlockData();
        final Player player = watcherAt(10, 64, 24);

        PortalBackdrop.sendPortalBackdropTo(player, gate, false);

        verify(player).sendBlockChange(argThat(at -> at.getBlockY() == 64 && at.getBlockZ() == 19),
            eq(freeData));
        verify(player, never()).sendBlockChange(argThat(at -> at.getBlockY() == 65),
            any(BlockData.class));
    }

    /**
     * Showing it sends the gate's horizon, in the gate's own material, to the one player.
     */
    @Test
    void showingTheHorizonToOnePlayerSendsTheDrawnMaterial()
    {
        gate.setGateFacing(BlockFace.SOUTH);
        gate.getGatePortalBlocks().add(new Location(world, 10, 64, 20));
        final Block real = standing(10, 64, 19, Material.AIR);
        final Player player = watcherAt(10, 64, 24);
        final BlockData realData = real.getBlockData();
        final BlockData horizon = mock(BlockData.class);

        try (MockedStatic<MaterialUtils> materials = mockStatic(MaterialUtils.class))
        {
            materials.when(() -> MaterialUtils.drawnAcross(any(Material.class), eq(BlockFace.SOUTH)))
                .thenReturn(horizon);
            materials.when(() -> MaterialUtils.isAirMaterial(Material.AIR)).thenReturn(true);

            PortalBackdrop.sendPortalBackdropTo(player, gate, true);
        }

        verify(player).sendBlockChange(any(Location.class), eq(horizon));
        verify(player, never()).sendBlockChange(any(Location.class), eq(realData));
    }

    /**
     * A gate with no cells sends nothing to anyone.
     */
    @Test
    void aGateWithNoCellsSendsNothingAndAsksNoOneToRedraw()
    {
        final Player player = watcherAt(0, 64, 0);
        when(world.getPlayers()).thenReturn(List.of(player));

        PortalBackdrop.sendPortalBackdropTo(player, gate, true);
        PortalBackdrop.sendPortalBackdrop(gate, true);

        verify(player, never()).sendBlockChange(any(Location.class), any(BlockData.class));
        verify(world, never()).getPlayers();
    }

    /**
     * Everyone in range is sent it, whichever side of the gate they are on.
     */
    @Test
    void everyoneInRangeIsSentItWhicheverSideTheyAreOn()
    {
        gate.setGateFacing(BlockFace.SOUTH);
        gate.getGatePortalBlocks().add(new Location(world, 10, 64, 20));
        standing(10, 64, 19, Material.AIR);
        final Player inFront = watcherAt(10, 64, 30);
        final Player behind = watcherAt(10, 64, 10);
        when(world.getPlayers()).thenReturn(List.of(inFront, behind));

        PortalBackdrop.sendPortalBackdrop(gate, false);

        verify(inFront).sendBlockChange(any(Location.class), any(BlockData.class));
        verify(behind).sendBlockChange(any(Location.class), any(BlockData.class));
    }
}
