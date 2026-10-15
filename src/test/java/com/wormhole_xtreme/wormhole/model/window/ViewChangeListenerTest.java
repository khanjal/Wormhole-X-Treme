package com.wormhole_xtreme.wormhole.model.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Each block event names the blocks it changed to the views (#609), so a view is built again when
 * the world behind its wall changes, and not on a timer.
 *
 * <p>Events are mocked: some constructors differ across the versions this jar runs on
 * ({@code EntityExplodeEvent} took an explosion result from 1.21).
 */
class ViewChangeListenerTest
{
    private final ViewChangeListener listener = new ViewChangeListener();
    private World world;
    private final List<String> told = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        world = mock(World.class);
    }

    private Block block(final int x, final int y, final int z)
    {
        final Block block = mock(Block.class);
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(x);
        when(block.getY()).thenReturn(y);
        when(block.getZ()).thenReturn(z);
        return block;
    }

    /** Runs the listener with the views stood in for, collecting the blocks they are told of. */
    private List<String> heard(final Runnable fire)
    {
        try (MockedStatic<Windows> windows = mockStatic(Windows.class))
        {
            windows.when(() -> Windows.changed(any(World.class), anyInt(), anyInt(), anyInt())).thenAnswer(call ->
            {
                told.add(call.getArgument(1) + "," + call.getArgument(2) + "," + call.getArgument(3));
                return null;
            });
            fire.run();
        }
        return told;
    }

    @Test
    void aPlacedOrBrokenBlockIsTold()
    {
        final BlockPlaceEvent place = mock(BlockPlaceEvent.class);
        final Block placed = block(1, 2, 3);
        when(place.getBlockPlaced()).thenReturn(placed);
        final BlockBreakEvent broken = mock(BlockBreakEvent.class);
        final Block gone = block(4, 5, 6);
        when(broken.getBlock()).thenReturn(gone);

        assertEquals(List.of("1,2,3", "4,5,6"), heard(() ->
        {
            listener.onPlace(place);
            listener.onBreak(broken);
        }));
    }

    @Test
    void everyBlockAnExplosionTakesIsTold()
    {
        final EntityExplodeEvent boom = mock(EntityExplodeEvent.class);
        final List<Block> blocks = List.of(block(1, 1, 1), block(2, 2, 2));
        when(boom.blockList()).thenReturn(blocks);

        assertEquals(List.of("1,1,1", "2,2,2"), heard(() -> listener.onEntityExplode(boom)));
    }

    /** A piston changes where its blocks were, where they go, and the space its head moves into. */
    @Test
    void aPistonTellsEachBlockItMovesWhereItGoesAndItsHead()
    {
        final BlockPistonExtendEvent push = mock(BlockPistonExtendEvent.class);
        final Block piston = block(0, 64, 0);
        final Block head = block(0, 64, -1);
        when(piston.getRelative(BlockFace.NORTH)).thenReturn(head);
        final Block moved = head;
        final Block into = block(0, 64, -2);
        when(moved.getRelative(BlockFace.NORTH)).thenReturn(into);
        when(push.getBlock()).thenReturn(piston);
        when(push.getBlocks()).thenReturn(List.of(moved));
        when(push.getDirection()).thenReturn(BlockFace.NORTH);

        assertEquals(List.of("0,64,-1", "0,64,-1", "0,64,-2"), heard(() -> listener.onExtend(push)));
    }

    /** Water flowing on changes the block it flows into, not the one it comes from. */
    @Test
    void aFlowTellsTheBlockFlowedInto()
    {
        final BlockFromToEvent flow = mock(BlockFromToEvent.class);
        final Block from = block(5, 60, 5);
        final Block to = block(5, 59, 5);
        when(flow.getBlock()).thenReturn(from);
        when(flow.getToBlock()).thenReturn(to);

        assertEquals(List.of("5,59,5"), heard(() -> listener.onFlow(flow)));
    }

    /** A tree grown from a sapling tells every block it puts down. */
    @Test
    void aGrownTreeTellsEveryBlockOfIt()
    {
        final StructureGrowEvent grow = mock(StructureGrowEvent.class);
        final List<BlockState> states = new ArrayList<>();
        for (int y = 64; y <= 65; y++)
        {
            final BlockState state = mock(BlockState.class);
            when(state.getWorld()).thenReturn(world);
            when(state.getX()).thenReturn(7);
            when(state.getY()).thenReturn(y);
            when(state.getZ()).thenReturn(8);
            states.add(state);
        }
        when(grow.getBlocks()).thenReturn(states);

        assertEquals(List.of("7,64,8", "7,65,8"), heard(() -> listener.onGrow(grow)));
    }
}
