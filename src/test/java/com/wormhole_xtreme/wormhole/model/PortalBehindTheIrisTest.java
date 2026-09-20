package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * Where an open gate's event horizon is shown while its iris is shut.
 *
 * <p>Every shipped shape's opening is one block thick -- Standard's twenty-one portal cells are
 * all in one layer, Grand's two hundred and seventy-four likewise -- so a closed iris fills it
 * and there is nowhere left inside the ring for the water. It went missing entirely: close the
 * iris on a dialled gate and the horizon vanished, which reads as the gate having shut rather
 * than having been covered. Worse with a transparent iris, and two of the four shipped palettes
 * have one -- {@code Atlantis} is stained glass, {@code Universe} is too -- so a closed iris was
 * a coloured window onto whatever stood behind the gate.
 *
 * <p>It is drawn one block behind the opening instead. Behind rather than in front because the
 * horizon is sent to clients and collides with nothing, so it can go where the iris cannot: the
 * iris is real blocks and moving it would mean finding room among whatever a player has built,
 * and every check that asks where the barrier is would have to follow it there.
 *
 * <p>These pin the geometry, which is arithmetic over the gate's facing and needs no world. That
 * a cell is only drawn in when it is air is the other half, and belongs with the drawing.
 */
class PortalBehindTheIrisTest
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
        gate.setGateName("IrisGate");
        gate.setGateWorld(world);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PluginTestSupport.forgetAllGates();
        PluginTestSupport.remove();
    }

    /**
     * The horizon goes one block back along the gate's facing, opposite the woosh.
     *
     * <p>The facing is the way the kawoosh comes out, so away from it is behind the gate. Get
     * the sign wrong and the horizon is drawn in front of the iris, hiding the barrier behind
     * the very thing it was meant to cover.
     */
    @Test
    void theHorizonSitsOneBlockBehindEachPortalCell()
    {
        gate.setGateFacing(BlockFace.SOUTH);
        gate.getGatePortalBlocks().add(new Location(world, 10, 64, 20));
        gate.getGatePortalBlocks().add(new Location(world, 10, 65, 20));

        final List<Location> behind = StargateBlockSetup.portalBackdropCells(gate);

        assertEquals(2, behind.size(), "one cell behind each portal cell");
        assertEquals(19, behind.get(0).getBlockZ(),
            "south faces +z, so behind the gate is -z: " + behind.get(0));
        assertEquals(10, behind.get(0).getBlockX(), "and nothing moves sideways");
        assertEquals(64, behind.get(0).getBlockY(), "or up and down");
    }

    /**
     * Each of the four facings goes the opposite way.
     */
    @Test
    void everyFacingPutsTheHorizonOnItsOwnFarSide()
    {
        for (final BlockFace facing : new BlockFace[] { BlockFace.NORTH, BlockFace.SOUTH,
                                                        BlockFace.EAST, BlockFace.WEST })
        {
            final Stargate one = new Stargate();
            one.setGateName("g");
            one.setGateWorld(world);
            one.setGateFacing(facing);
            one.getGatePortalBlocks().add(new Location(world, 0, 64, 0));

            final Location behind = StargateBlockSetup.portalBackdropCells(one).get(0);

            assertEquals(-facing.getModX(), behind.getBlockX(), facing + " moved the wrong way in x");
            assertEquals(-facing.getModZ(), behind.getBlockZ(), facing + " moved the wrong way in z");
        }
    }

    /**
     * A horizontal gate puts it underneath, which is the same rule and worth knowing.
     *
     * <p>{@code Horizontal} and {@code HorizontalSignDial} both ship. Their facing is vertical,
     * so "behind" is below -- the horizon shows under the gate rather than beyond it. That may
     * want revisiting once somebody has stood on one; it is pinned here so a change to it is a
     * decision rather than a surprise.
     */
    @Test
    void aHorizontalGatePutsTheHorizonBelowItself()
    {
        gate.setGateFacing(BlockFace.UP);
        gate.getGatePortalBlocks().add(new Location(world, 4, 70, 9));

        final Location behind = StargateBlockSetup.portalBackdropCells(gate).get(0);

        assertEquals(69, behind.getBlockY(), "up-facing, so behind it is one block down");
    }

    /**
     * Puts a block in the world at a cell, so the air check has something to find.
     *
     * @param at
     *            where
     * @param type
     *            what stands there
     * @return the block, already wired into the world mock
     */
    private Block standing(final Location at, final Material type)
    {
        final Block block = mock(Block.class);
        final BlockData data = mock(BlockData.class);
        when(block.getType()).thenReturn(type);
        when(block.getBlockData()).thenReturn(data);
        when(world.getBlockAt(at.getBlockX(), at.getBlockY(), at.getBlockZ())).thenReturn(block);
        return block;
    }

    /**
     * The horizon is drawn behind the iris, and only where that cell is air.
     *
     * <p>The second half is the promise made to anybody who has built behind a gate: they see
     * what they built, not a sheet of water over it. It is also the half a test of the geometry
     * alone cannot reach, since the geometry does not know what is standing there.
     */
    @Test
    void theHorizonIsDrawnOnlyWhereTheCellBehindIsAir()
    {
        gate.setGateFacing(BlockFace.SOUTH);
        gate.setGateActive(true);
        final Location free = new Location(world, 10, 64, 20);
        final Location blocked = new Location(world, 10, 65, 20);
        gate.getGatePortalBlocks().add(free);
        gate.getGatePortalBlocks().add(blocked);
        standing(new Location(world, 10, 64, 19), Material.AIR);
        standing(new Location(world, 10, 65, 19), Material.STONE);

        final Player watcher = mock(Player.class);
        when(watcher.getLocation()).thenReturn(new Location(world, 10, 64, 24));
        when(world.getPlayers()).thenReturn(List.of(watcher));

        final BlockData horizon = mock(BlockData.class);
        try (MockedStatic<MaterialUtils> materials = mockStatic(MaterialUtils.class))
        {
            materials.when(() -> MaterialUtils.drawnAs(any(Material.class))).thenReturn(horizon);
            materials.when(() -> MaterialUtils.isAirMaterial(Material.AIR)).thenReturn(true);
            materials.when(() -> MaterialUtils.isAirMaterial(Material.STONE)).thenReturn(false);

            StargateBlockSetup.sendPortalBackdrop(gate, true);
        }

        verify(watcher).sendBlockChange(argThat(at -> at.getBlockY() == 64), eq(horizon));
        verify(watcher, never()).sendBlockChange(argThat(at -> at.getBlockY() == 65), eq(horizon));
    }

    /**
     * Somebody too far away is not sent it at all.
     *
     * <p>The same range the open-time send uses. Without this the backdrop would go to everyone
     * in the world every time an iris moved.
     */
    @Test
    void somebodyOutOfRangeIsNotSentTheHorizon()
    {
        gate.setGateFacing(BlockFace.SOUTH);
        gate.setGateActive(true);
        gate.getGatePortalBlocks().add(new Location(world, 10, 64, 20));
        standing(new Location(world, 10, 64, 19), Material.AIR);

        final Player distant = mock(Player.class);
        when(distant.getLocation()).thenReturn(new Location(world, 10, 64, 900));
        when(world.getPlayers()).thenReturn(List.of(distant));

        StargateBlockSetup.sendPortalBackdrop(gate, true);

        verify(distant, never()).sendBlockChange(any(Location.class), any(BlockData.class));
    }

    /**
     * A gate with no facing yet asks for nothing, rather than throwing.
     *
     * <p>A gate is built up field by field as it is detected, and the drawing paths run from
     * events that can arrive part-way through that.
     */
    @Test
    void aGateWithNoFacingHasNowhereToPutIt()
    {
        gate.getGatePortalBlocks().add(new Location(world, 0, 64, 0));

        assertTrue(StargateBlockSetup.portalBackdropCells(gate).isEmpty(),
            "no facing, no behind");
        assertTrue(StargateBlockSetup.portalBackdropCells(null).isEmpty(), "and no gate at all is not a crash");
    }
}
