package com.wormhole_xtreme.wormhole.model.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Each block event names the blocks it changed to the views (#609), so a view is built again when
 * the world behind its wall changes, and not on a timer.
 *
 * <p>Events are mocked: some constructors differ across the versions this jar runs on
 * ({@code EntityExplodeEvent} took an explosion result from 1.21). The views are stood in for here,
 * so these tests say which blocks each event names; {@code FixedRebuildTest} says a named block
 * behind a wall makes its view stale.
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

    /** Runs the listener with the views stood in for, holding a view or not, collecting the blocks they are told of. */
    private List<String> heard(final boolean watching, final Runnable fire)
    {
        try (MockedStatic<Windows> windows = mockStatic(Windows.class))
        {
            windows.when(Windows::watchingAny).thenReturn(watching);
            windows.when(() -> Windows.changed(any(World.class), anyInt(), anyInt(), anyInt())).thenAnswer(call ->
            {
                told.add(call.getArgument(1) + "," + call.getArgument(2) + "," + call.getArgument(3));
                return null;
            });
            fire.run();
        }
        return told;
    }

    private List<String> heard(final Runnable fire)
    {
        return heard(true, fire);
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

    /** A bucket poured or filled sets a block no place or break event reports. */
    @Test
    void aBucketPouredOrFilledIsTold()
    {
        final PlayerBucketEmptyEvent pour = mock(PlayerBucketEmptyEvent.class);
        final Block poured = block(1, 60, 1);
        when(pour.getBlock()).thenReturn(poured);
        final PlayerBucketFillEvent fill = mock(PlayerBucketFillEvent.class);
        final Block filled = block(2, 61, 2);
        when(fill.getBlock()).thenReturn(filled);

        assertEquals(List.of("1,60,1", "2,61,2"), heard(() ->
        {
            listener.onBucketEmpty(pour);
            listener.onBucketFill(fill);
        }));
    }

    @Test
    void everyBlockAnExplosionTakesIsTold()
    {
        final EntityExplodeEvent boom = mock(EntityExplodeEvent.class);
        final List<Block> blocks = List.of(block(1, 1, 1), block(2, 2, 2));
        when(boom.blockList()).thenReturn(blocks);
        final BlockExplodeEvent bed = mock(BlockExplodeEvent.class);
        final Block source = block(9, 9, 9);
        when(bed.getBlock()).thenReturn(source);
        final List<Block> around = List.of(block(8, 9, 9));
        when(bed.blockList()).thenReturn(around);

        assertEquals(List.of("1,1,1", "2,2,2", "9,9,9", "8,9,9"), heard(() ->
        {
            listener.onEntityExplode(boom);
            listener.onBlockExplode(bed);
        }));
    }

    /** A block one step along a line, as getRelative would give, stubbed on {@code from}. */
    private Block along(final Block from, final BlockFace face)
    {
        final Block next = block(from.getX() + face.getModX(), from.getY() + face.getModY(), from.getZ() + face.getModZ());
        when(from.getRelative(face)).thenReturn(next);
        return next;
    }

    /** A piston pushing tells the space its head moves into, each block it moves, and each block's neighbours along the line. */
    @Test
    void anExtendingPistonTellsEachBlockItMovesAndWhereTo()
    {
        final BlockPistonExtendEvent push = mock(BlockPistonExtendEvent.class);
        final Block piston = block(0, 64, 0);
        final Block moved = along(piston, BlockFace.NORTH);
        along(moved, BlockFace.NORTH);
        along(moved, BlockFace.SOUTH);
        when(push.getBlock()).thenReturn(piston);
        when(push.getBlocks()).thenReturn(List.of(moved));
        when(push.getDirection()).thenReturn(BlockFace.NORTH);

        assertEquals(List.of("0,64,-1", "0,64,-1", "0,64,-2", "0,64,0"), heard(() -> listener.onExtend(push)));
    }

    /**
     * A sticky piston pulling back: its direction is the way it faces, and the block two out moves one
     * nearer, against that direction. Told only along the piston's direction, the block's new place was
     * missed: it is the block behind it, as the piston sees it.
     */
    @Test
    void aRetractingPistonTellsWhereItsBlockIsPulledTo()
    {
        final BlockPistonRetractEvent pull = mock(BlockPistonRetractEvent.class);
        final Block piston = block(0, 64, 0);
        final Block head = along(piston, BlockFace.NORTH);
        final Block pulled = block(0, 64, -2);
        along(pulled, BlockFace.NORTH);
        when(pulled.getRelative(BlockFace.SOUTH)).thenReturn(head);
        when(pull.getBlock()).thenReturn(piston);
        when(pull.getBlocks()).thenReturn(List.of(pulled));
        when(pull.getDirection()).thenReturn(BlockFace.NORTH);

        final List<String> heard = heard(() -> listener.onRetract(pull));

        assertTrue(heard.contains("0,64,-2"), "where it was: " + heard);
        assertEquals(2, heard.stream().filter("0,64,-1"::equals).count(), "the head's space, and where the block lands: " + heard);
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

    /** The events that change one block, the event's own: each tells it. */
    @Test
    void eachOneBlockEventTellsItsBlock()
    {
        final Map<Class<? extends BlockEvent>, Consumer<BlockEvent>> handlers = Map.of(
            BlockBurnEvent.class, event -> listener.onBurn((BlockBurnEvent) event),
            BlockIgniteEvent.class, event -> listener.onIgnite((BlockIgniteEvent) event),
            LeavesDecayEvent.class, event -> listener.onDecay((LeavesDecayEvent) event),
            BlockFormEvent.class, event -> listener.onForm((BlockFormEvent) event),
            BlockSpreadEvent.class, event -> listener.onSpread((BlockSpreadEvent) event),
            BlockFadeEvent.class, event -> listener.onFade((BlockFadeEvent) event));
        for (final Map.Entry<Class<? extends BlockEvent>, Consumer<BlockEvent>> handler : handlers.entrySet())
        {
            told.clear();
            final BlockEvent event = mock(handler.getKey());
            final Block at = block(3, 70, -4);
            when(event.getBlock()).thenReturn(at);

            assertEquals(List.of("3,70,-4"), heard(() -> handler.getValue().accept(event)), handler.getKey().getSimpleName());
        }
    }

    /** Sand falling, an enderman, a sheep eating grass: the block the entity changes. */
    @Test
    void anEntityChangingABlockTellsIt()
    {
        final EntityChangeBlockEvent sheep = mock(EntityChangeBlockEvent.class);
        final Block grass = block(-7, 63, 12);
        when(sheep.getBlock()).thenReturn(grass);

        assertEquals(List.of("-7,63,12"), heard(() -> listener.onEntityChange(sheep)));
    }

    /**
     * A plant growing tells its block only when it grows into an empty one or out of one: a field of
     * wheat ageing behind a wall would otherwise keep its view rebuilding, and changes nothing a view
     * draws.
     */
    @Test
    void aPlantGrowingIntoAnEmptyBlockIsToldAndACropAgeingIsNot()
    {
        final Function<Boolean, BlockGrowEvent> growth = emptyBefore ->
        {
            final BlockGrowEvent grow = mock(BlockGrowEvent.class);
            final Block at = block(1, 64, 1);
            when(at.isEmpty()).thenReturn(emptyBefore);
            when(grow.getBlock()).thenReturn(at);
            final BlockState after = mock(BlockState.class);
            when(after.getType()).thenReturn(emptyBefore ? Material.SUGAR_CANE : Material.WHEAT);
            when(grow.getNewState()).thenReturn(after);
            return grow;
        };
        final BlockGrowEvent cane = growth.apply(true);
        final BlockGrowEvent wheat = growth.apply(false);

        assertEquals(List.of("1,64,1"), heard(() ->
        {
            listener.onGrowBlock(cane);
            listener.onGrowBlock(wheat);
        }), "the cane into air, not the wheat ageing");
        assertTrue(ViewChangeListener.empty(Material.CAVE_AIR) && !ViewChangeListener.empty(Material.WHEAT));
    }

    /** A tree grown from a sapling, or bone meal's grass and flowers, tells every block it puts down. */
    @Test
    void aGrownTreeOrBoneMealTellsEveryBlockOfIt()
    {
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
        final StructureGrowEvent grow = mock(StructureGrowEvent.class);
        when(grow.getBlocks()).thenReturn(states);
        final BlockFertilizeEvent meal = mock(BlockFertilizeEvent.class);
        when(meal.getBlocks()).thenReturn(states.subList(0, 1));

        assertEquals(List.of("7,64,8", "7,65,8", "7,64,8"), heard(() ->
        {
            listener.onGrow(grow);
            listener.onFertilize(meal);
        }));
    }

    /** With no view held anywhere, an event is not even asked for its blocks: the server's commonest case costs one check. */
    @Test
    void withNoViewHeldAnEventIsNotTouched()
    {
        final BlockFromToEvent flow = mock(BlockFromToEvent.class);
        final BlockPistonExtendEvent push = mock(BlockPistonExtendEvent.class);

        assertEquals(List.of(), heard(false, () ->
        {
            listener.onFlow(flow);
            listener.onExtend(push);
        }));
        verifyNoInteractions(flow, push);
    }

    /**
     * Every handler runs last and only for what happened: a view built again for a block another
     * plugin refused to place would be built for nothing.
     */
    @Test
    void everyHandlerListensLastAndOnlyToWhatHappened()
    {
        int handlers = 0;
        for (final Method method : ViewChangeListener.class.getDeclaredMethods())
        {
            final EventHandler handler = method.getAnnotation(EventHandler.class);
            if (handler == null)
            {
                continue;
            }
            handlers++;
            assertEquals(EventPriority.MONITOR, handler.priority(), method.getName());
            assertTrue(handler.ignoreCancelled(), method.getName() + " ignores cancelled events");
            assertTrue(Event.class.isAssignableFrom(method.getParameterTypes()[0]), method.getName());
        }
        assertEquals(19, handlers, "every handler counted");
    }
}
