package com.wormhole_xtreme.wormhole.model.ring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.config.ConfigManager;

/**
 * What the one-viewer drawing of a ring preview sends, to whom, and what it refuses to do (#332).
 *
 * <p>{@link BukkitRingWorld} sends to everyone near a pair. The whole point of a preview is that
 * it does not: a stack raised to check an edit is a wall only its viewer can see, and one
 * that other players also saw would be a wall they could walk into. So the check here is on the
 * other player in the same world, who must receive nothing, and on the three ways a drawing
 * could reach the world -- passengers, travel and delivery -- which must all say no.
 */
class PreviewSurroundingsTest
{
    private static final int FLOOR = 70;

    private World world;
    private Player viewer;
    private Player bystander;
    private RingPair pair;
    private PreviewSurroundings drawing;
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<ConfigManager> config;
    private BlockData anyData;

    @BeforeEach
    void setUp()
    {
        world = mock(World.class);
        viewer = mock(Player.class);
        bystander = mock(Player.class);
        when(viewer.getWorld()).thenReturn(world);
        when(viewer.isOnline()).thenReturn(Boolean.TRUE);
        when(bystander.getWorld()).thenReturn(world);
        when(bystander.isOnline()).thenReturn(Boolean.TRUE);
        when(world.getPlayers()).thenReturn(List.of(viewer, bystander));
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(Boolean.TRUE);
        when(world.getMinHeight()).thenReturn(Integer.valueOf(-64));
        when(world.getMaxHeight()).thenReturn(Integer.valueOf(320));
        // Stone up to the floor, open air above it.
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(call ->
        {
            final Block block = mock(Block.class);
            when(block.isPassable()).thenReturn(Boolean.valueOf(call.getArgument(1, Integer.class) > FLOOR));
            // Non-null, so that "nothing was sent" cannot be true merely because null never
            // matches a typed any().
            when(block.getBlockData()).thenReturn(anyData);
            return block;
        });
        anyData = mock(BlockData.class);
        pair = mock(RingPair.class);
        drawing = new PreviewSurroundings(viewer, world, pair);

        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.createBlockData(any(Material.class))).thenReturn(anyData);
        config = mockStatic(ConfigManager.class);
        config.when(ConfigManager::getRingMaxCeilingDrop).thenReturn(Integer.valueOf(16));
    }

    @AfterEach
    void tearDown()
    {
        config.close();
        bukkit.close();
    }

    /** A block and a slab reach the viewer at the place asked for, and not the other player. */
    @Test
    void aDrawnBlockOrSlabIsSentToTheViewerAndNobodyElse()
    {
        drawing.showBlock(1, 2, 3, Material.GLOWSTONE);
        drawing.showSlab(4, 5, 6, Material.STONE_SLAB, true);

        final ArgumentCaptor<Location> where = ArgumentCaptor.forClass(Location.class);
        verify(viewer, times(2)).sendBlockChange(where.capture(), any(BlockData.class));
        assertEquals(List.of(1, 4), where.getAllValues().stream().map(Location::getBlockX).toList());
        assertEquals(List.of(3, 6), where.getAllValues().stream().map(Location::getBlockZ).toList());
        verify(bystander, never()).sendBlockChange(any(Location.class), any(BlockData.class));
    }

    /** Taking a drawing back shows the viewer what is really there, and only the viewer. */
    @Test
    void revealingSendsTheViewerTheRealBlockAndNobodyElse()
    {
        final BlockData real = mock(BlockData.class);
        final Block block = mock(Block.class);
        when(block.getBlockData()).thenReturn(real);
        when(world.getBlockAt(7, 8, 9)).thenReturn(block);

        drawing.reveal(7, 8, 9);

        verify(viewer).sendBlockChange(any(Location.class), eq(real));
        verify(bystander, never()).sendBlockChange(any(Location.class), any(BlockData.class));
    }

    /** A chunk nobody has loaded has none of the drawing in it, and is not loaded to find out. */
    @Test
    void revealingAnUnloadedChunkNeitherSendsNorLoadsIt()
    {
        when(world.isChunkLoaded(0, 0)).thenReturn(Boolean.FALSE);

        drawing.reveal(1, 2, 3);

        verify(viewer, never()).sendBlockChange(any(Location.class), any(BlockData.class));
        verify(world, never()).getBlockAt(1, 2, 3);
    }

    /**
     * Coordinates mean the same in every world, so a block sent to somebody who has changed
     * world would be drawn over terrain it was never about.
     */
    @Test
    void nothingIsSentToSomebodyWhoHasChangedWorldOrGone()
    {
        when(viewer.getWorld()).thenReturn(mock(World.class));
        drawing.showBlock(1, 2, 3, Material.GLOWSTONE);
        drawing.reveal(1, 2, 3);
        verify(viewer, never()).sendBlockChange(any(Location.class), any(BlockData.class));

        when(viewer.getWorld()).thenReturn(world);
        when(viewer.isOnline()).thenReturn(Boolean.FALSE);
        drawing.showBlock(1, 2, 3, Material.GLOWSTONE);
        verify(viewer, never()).sendBlockChange(any(Location.class), any(BlockData.class));
    }

    /** A client that cannot be sent a block does not stop a clean-up half way. */
    @Test
    void aSendThatFailsDoesNotEscape()
    {
        doThrow(new IllegalStateException("channel closed")).when(viewer)
            .sendBlockChange(any(Location.class), any(BlockData.class));

        drawing.showBlock(1, 2, 3, Material.GLOWSTONE);
        drawing.reveal(1, 2, 3);

        verify(viewer, times(2)).sendBlockChange(any(Location.class), any(BlockData.class));
    }

    /** Nothing is ever carried: no passengers, no travel, no delivery. */
    @Test
    void aDrawingFindsNoPassengersRefusesTravelAndDeliversNobody()
    {
        final RingPassenger passenger = mock(RingPassenger.class);
        final Ring ring = mock(Ring.class);

        assertTrue(drawing.passengersIn(List.of(new int[] { 1, 2, 3 })).isEmpty());
        assertFalse(drawing.mayTravel(passenger, ring, ring));
        drawing.deliver(passenger, ring);

        verifyNoInteractions(passenger);
        verify(world, never()).getNearbyEntities(any(BoundingBox.class));
    }

    /** The one real read: a ceiling ring is measured, and the drop is recorded on it. */
    @Test
    void measuringACeilingRingFindsItsFloorAndRecordsTheDrop()
    {
        final Ring ceiling = new Ring(100, 80, 100, RingPattern.ODD, RingOrientation.CEILING,
            Material.STONE_SLAB, Material.GLOWSTONE);

        assertNull(drawing.survey(ceiling), "a clear room under the ring");

        assertEquals(80 - FLOOR - 1, ceiling.getDrop(), "feet go on the layer above the solid floor");
    }
}
