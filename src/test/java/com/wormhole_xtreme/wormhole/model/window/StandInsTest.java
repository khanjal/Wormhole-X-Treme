package com.wormhole_xtreme.wormhole.model.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Zombie;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.GateSource;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorSource;
import com.wormhole_xtreme.wormhole.model.mirror.QuantumMirror;
import com.wormhole_xtreme.wormhole.model.window.WindowShape.Spot;
import com.wormhole_xtreme.wormhole.utils.HiddenEntities;

/**
 * The stand-ins a view shows for the creatures in its far room (#296), from spawning to removal.
 *
 * <p>Each stand-in is a real entity in the viewer's world, so a mistake here leaves something
 * behind that everybody's server keeps: a zombie in the wall nobody can see, a stand-in walking
 * out of the mirror into the viewer's room, one respawned every redraw against a protection plugin
 * that refuses it, or a follow task left running for nothing.
 *
 * <p>Through {@link HiddenEntities#creationWith}, never MockBukkit, which leaves
 * {@code setVisibleByDefault} unimplemented.
 */
class StandInsTest
{
    private static final Place ARRIVAL = new Place("far", 100.5, 70.0, -20.5, 0.0f, 0.0f);

    private final List<Entity> made = new ArrayList<>();
    private Entity nextCopy;
    private WormholeXTreme plugin;
    private World here;
    private World far;
    private Player viewer;
    private ViewerDrawing view;
    private WindowState window;
    private BukkitScheduler scheduler;

    @BeforeEach
    void setUp() throws Exception
    {
        plugin = PluginTestSupport.install();
        scheduler = mock(BukkitScheduler.class);
        when(scheduler.scheduleSyncRepeatingTask(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
            .thenReturn(7);
        PluginTestSupport.scheduler(scheduler);
        StandIns.removeEverything();
        here = mock(World.class);
        when(here.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        far = mock(World.class);
        when(far.getName()).thenReturn("far");
        viewer = mock(Player.class);
        when(viewer.getUniqueId()).thenReturn(UUID.randomUUID());
        view = new ViewerDrawing(here);
        final WindowShape shape = WindowShape.of(new BlockPlace("world", 10, 64, 10), BlockFace.NORTH, ARRIVAL);
        final List<Spot> open = List.of(new Spot(10, 63, 11), new Spot(10, 64, 11));
        final MirrorSource source = new MirrorSource(new QuantumMirror("museum", new BlockPlace("world", 10, 64, 10), ARRIVAL),
            mock(Block.class), shape, open, 16);
        final Capture capture = new Capture.Builder("far", true, new Capture.Box(60, 54, -61, 81, 81, 81), mock(BlockData.class))
            .build();
        window = new WindowState(source, capture);
        HiddenEntities.creationWith(new HiddenEntities.Creation()
        {
            @Override
            public <T extends Entity> T create(final World world, final Location at, final Class<T> type)
            {
                final Entity copy = nextCopy;
                if (copy != null)
                {
                    made.add(copy);
                }
                return type.cast(copy);
            }

            @Override
            public <T extends Entity> T add(final World world, final T entity)
            {
                return entity;
            }
        });
    }

    @AfterEach
    void tearDown() throws Exception
    {
        StandIns.removeEverything();
        HiddenEntities.creationWith(null);
        PluginTestSupport.scheduler(null);
        PluginTestSupport.remove();
    }

    /**
     * A stand-in is made inert, looks like its creature at a glance, and is shown to the viewer
     * alone.
     *
     * <p>Left with its AI, a zombie stand-in walks off through the wall it stands in; left
     * persistent, a restart saves it into the viewer's world for good.
     */
    @Test
    void aStandInIsInertLooksLikeItsCreatureAndIsShownOnlyToTheViewer()
    {
        final Zombie original = creature(Zombie.class, 100.5, 70.0, -18.5);
        when(original.getCustomName()).thenReturn("Bob");
        when(original.isCustomNameVisible()).thenReturn(true);
        when(original.isAdult()).thenReturn(false);
        final Zombie copy = copy(Zombie.class);
        when(copy.isAdult()).thenReturn(true);
        nextCopy = copy;

        StandIns.show(viewer, view, List.of(wanted(original)), 0L);

        assertEquals(List.of(copy), made, "one stand-in made");
        verify(copy).setPersistent(false);
        verify(copy).setVisibleByDefault(false);
        verify(copy).setAI(false);
        verify(copy).setSilent(true);
        verify(copy).setInvulnerable(true);
        verify(copy).setGravity(false);
        verify(copy).setCollidable(false);
        verify(copy).setCanPickupItems(false);
        verify(copy).setRemoveWhenFarAway(false);
        verify(copy).addScoreboardTag(StandIns.TAG);
        verify(copy).setCustomName("Bob");
        verify(copy).setCustomNameVisible(true);
        verify(copy).setBaby();
        verify(viewer).showEntity(plugin, copy);
        assertTrue(StandIns.isStandIn(copy), "known for a stand-in, so nothing veils or copies it");
        assertSame(copy, view.standIns.get(original.getUniqueId()).copy, "held by the viewer's drawing");
    }

    /** A sheep's stand-in is the colour of its sheep, not the white a fresh one is. */
    @Test
    void aSheepsStandInIsItsColour()
    {
        final Sheep original = creature(Sheep.class, 100.5, 70.0, -18.5);
        when(original.getType()).thenReturn(EntityType.SHEEP);
        when(original.getColor()).thenReturn(DyeColor.RED);
        final Sheep copy = copy(Sheep.class);
        nextCopy = copy;

        StandIns.show(viewer, view, List.of(wanted(original)), 0L);

        verify(copy).setColor(DyeColor.RED);
    }

    /** A shorn sheep's stand-in is shorn, and a woolly one's is not, whatever a fresh one would be. */
    @Test
    void aSheepsStandInIsShornOnlyIfItsSheepIs()
    {
        for (final boolean shorn : new boolean[] { true, false })
        {
            final Sheep original = creature(Sheep.class, 100.5, 70.0, -18.5);
            when(original.getType()).thenReturn(EntityType.SHEEP);
            when(original.isSheared()).thenReturn(shorn);
            final Sheep copy = copy(Sheep.class);
            when(copy.isSheared()).thenReturn(!shorn);

            StandIns.dress(copy, original);

            verify(copy).setSheared(shorn);
        }
    }

    /** A creature still wanted keeps its stand-in, moved after it, rather than a new one each redraw. */
    @Test
    void aKeptStandInIsMovedAfterItsCreatureNotSpawnedAgain()
    {
        final Zombie original = creature(Zombie.class, 100.5, 70.0, -18.5);
        final Zombie copy = copy(Zombie.class);
        nextCopy = copy;
        StandIns.show(viewer, view, List.of(wanted(original)), 0L);
        when(copy.getLocation()).thenReturn(new Location(here, 10.5, 63.0, 14.5));

        final Location further = new Location(here, 10.5, 63.0, 16.5, 0.0f, 0.0f);
        StandIns.show(viewer, view, List.of(new FarCreatures.Wanted(original, further, window)), 100L);

        assertEquals(1, made.size(), "the same stand-in, not a second");
        verify(copy).teleport(further);
    }

    /** A creature no longer wanted has its stand-in taken out of the world and forgotten. */
    @Test
    void aStandInNoLongerWantedIsRemovedAndForgotten()
    {
        final Zombie original = creature(Zombie.class, 100.5, 70.0, -18.5);
        final Zombie copy = copy(Zombie.class);
        nextCopy = copy;
        StandIns.show(viewer, view, List.of(wanted(original)), 0L);

        StandIns.show(viewer, view, List.of(), 100L);

        verify(copy).remove();
        assertFalse(StandIns.isStandIn(copy));
        assertTrue(view.standIns.isEmpty());
    }

    /**
     * A spawn something refused is not asked again until a few seconds have passed.
     *
     * <p>A region that refuses mob spawns refuses every stand-in, and the drawing redraws a moving
     * viewer ten times a second.
     */
    @Test
    void aRefusedSpawnIsNotAskedAgainForAWhile()
    {
        final Zombie original = creature(Zombie.class, 100.5, 70.0, -18.5);
        final int[] asked = { 0 };
        HiddenEntities.creationWith(new HiddenEntities.Creation()
        {
            @Override
            public <T extends Entity> T create(final World world, final Location at, final Class<T> type)
            {
                asked[0]++;
                return type.cast(asked[0] >= 2 ? nextCopy : null);
            }

            @Override
            public <T extends Entity> T add(final World world, final T entity)
            {
                return entity;
            }
        });
        nextCopy = copy(Zombie.class);

        StandIns.show(viewer, view, List.of(wanted(original)), 0L);
        StandIns.show(viewer, view, List.of(wanted(original)), 1000L);
        assertEquals(1, asked[0], "refused once, and left alone a second later");
        assertTrue(view.standIns.isEmpty());

        StandIns.show(viewer, view, List.of(wanted(original)), 0L + StandIns.REFUSED_MILLIS);

        assertEquals(2, asked[0], "asked again once the wait is over");
        assertSame(nextCopy, view.standIns.get(original.getUniqueId()).copy, "and shown when it is let through");
    }

    /** Between redraws a stand-in follows its creature through the window. */
    @Test
    void aStandInFollowsItsCreature()
    {
        final Zombie original = creature(Zombie.class, 100.5, 70.0, -18.5);
        final Zombie copy = copy(Zombie.class);
        nextCopy = copy;
        StandIns.show(viewer, view, List.of(wanted(original)), 0L);
        when(original.getLocation()).thenReturn(new Location(far, 101.5, 70.0, -16.5, 0.0f, 0.0f));

        StandIns.follow(view);

        final ArgumentCaptor<Location> to = ArgumentCaptor.forClass(Location.class);
        verify(copy).teleport(to.capture());
        final Location at = to.getValue();
        // A block to the far side's left (east, facing south) is a block to the viewer's left here.
        assertEquals(11.5, at.getX(), 1.0e-9);
        assertEquals(63.0, at.getY(), 1.0e-9);
        assertEquals(16.5, at.getZ(), 1.0e-9, "two blocks further in, as it walked two further from the far opening");
        assertSame(here, at.getWorld());
        assertTrue(StandIns.isStandIn(copy), "still shown");
    }

    /**
     * A creature that walks out of the room, towards the far opening, takes its stand-in with it,
     * rather than leading it out of the mirror into the viewer's own room.
     */
    @Test
    void aCreatureLeavingTheRoomTakesItsStandInAway()
    {
        final Zombie original = creature(Zombie.class, 100.5, 70.0, -18.5);
        final Zombie copy = copy(Zombie.class);
        nextCopy = copy;
        StandIns.show(viewer, view, List.of(wanted(original)), 0L);
        // Two blocks nearer the far opening than the arrival block: in front of this one.
        when(original.getLocation()).thenReturn(new Location(far, 100.5, 70.0, -22.5));

        StandIns.follow(view);

        verify(copy, never()).teleport(any(Location.class));
        verify(copy).remove();
        assertTrue(view.standIns.isEmpty());
    }

    /** A creature that has died, unloaded or gone to another world takes its stand-in away. */
    @Test
    void aCreatureGoneOrElsewhereTakesItsStandInAway()
    {
        final Zombie dead = creature(Zombie.class, 100.5, 70.0, -18.5);
        final Zombie travelled = creature(Zombie.class, 101.5, 70.0, -18.5);
        final Zombie stays = creature(Zombie.class, 99.5, 70.0, -18.5);
        final Zombie deadCopy = copy(Zombie.class);
        final Zombie travelledCopy = copy(Zombie.class);
        final Zombie staysCopy = copy(Zombie.class);
        nextCopy = deadCopy;
        StandIns.show(viewer, view, List.of(wanted(dead)), 0L);
        nextCopy = travelledCopy;
        StandIns.show(viewer, view, List.of(wanted(dead), wanted(travelled)), 0L);
        nextCopy = staysCopy;
        StandIns.show(viewer, view, List.of(wanted(dead), wanted(travelled), wanted(stays)), 0L);
        when(dead.isValid()).thenReturn(false);
        final World nether = mock(World.class);
        when(nether.getName()).thenReturn("far_nether");
        when(travelled.getLocation()).thenReturn(new Location(nether, 101.5, 70.0, -18.5));

        StandIns.follow(view);

        verify(deadCopy).remove();
        verify(travelledCopy).remove();
        verify(staysCopy, never()).remove();
        assertEquals(1, view.standIns.size(), "the one whose creature is still there stays");
    }

    /**
     * A stand-in no drawing holds is swept up; one a drawing holds is left.
     *
     * <p>A drawing dropped by some path that forgot its stand-ins would otherwise leave them in the
     * world, invisible to everybody, until the server stopped.
     */
    @Test
    void aStandInNoDrawingHoldsIsSweptUp()
    {
        final Zombie original = creature(Zombie.class, 100.5, 70.0, -18.5);
        final Zombie copy = copy(Zombie.class);
        nextCopy = copy;
        StandIns.show(viewer, view, List.of(wanted(original)), 0L);

        StandIns.sweepStrays(List.of(view));
        verify(copy, never()).remove();
        assertTrue(StandIns.isStandIn(copy), "held, so kept");

        StandIns.sweepStrays(List.of(new ViewerDrawing(here)));

        verify(copy).remove();
        assertFalse(StandIns.isStandIn(copy));
    }

    /** Stopping takes every stand-in away, whoever's it was. */
    @Test
    void stoppingRemovesEveryStandIn()
    {
        final Zombie one = copy(Zombie.class);
        final Zombie other = copy(Zombie.class);
        nextCopy = one;
        StandIns.show(viewer, view, List.of(wanted(creature(Zombie.class, 100.5, 70.0, -18.5))), 0L);
        nextCopy = other;
        StandIns.show(viewer, new ViewerDrawing(here), List.of(wanted(creature(Zombie.class, 100.5, 70.0, -17.5))), 0L);

        StandIns.removeEverything();

        verify(one).remove();
        verify(other).remove();
        assertEquals(0, StandIns.count());
    }

    /**
     * The follow task runs only while a stand-in is shown: started by the first, once, and
     * cancelled when the last has gone.
     *
     * <p>Every viewer of every mirror would otherwise pay for a task that has nothing to move.
     */
    @Test
    void theFollowTaskRunsOnlyWhileAStandInIsShown()
    {
        StandIns.show(viewer, view, List.of(), 0L);
        verify(scheduler, never()).scheduleSyncRepeatingTask(any(Plugin.class), any(Runnable.class), anyLong(), anyLong());

        final Zombie original = creature(Zombie.class, 100.5, 70.0, -18.5);
        nextCopy = copy(Zombie.class);
        StandIns.show(viewer, view, List.of(wanted(original)), 0L);
        StandIns.show(viewer, view, List.of(wanted(original)), 100L);
        verify(scheduler, times(1)).scheduleSyncRepeatingTask(eq(plugin), any(Runnable.class),
            eq(StandIns.FOLLOW_TICKS), eq(StandIns.FOLLOW_TICKS));
        assertTrue(StandIns.following());

        StandIns.show(viewer, view, List.of(), 200L);
        StandIns.sweepStrays(List.of(view));

        verify(scheduler).cancelTask(7);
        assertFalse(StandIns.following());
    }

    /** Where a stand-in would stand in front of the opening, or in a chunk not loaded, it may not. */
    @Test
    void aStandInMayStandOnlyBehindTheOpeningInALoadedChunk()
    {
        assertTrue(StandIns.inRoom(window, new Location(here, 10.5, 63.0, 14.5)), "three behind the opening");
        assertFalse(StandIns.inRoom(window, new Location(here, 10.5, 63.0, 10.5)), "in front of it, in the viewer's room");
        final World unloaded = mock(World.class);
        assertFalse(StandIns.inRoom(window, new Location(unloaded, 10.5, 63.0, 14.5)), "in a chunk not loaded");
        assertNull(StandIns.whereNow(here, new StandIns.StandIn(creature(Zombie.class, 100.5, 70.0, 0.0),
            copy(Zombie.class), window)), "past the view's depth");
    }

    /**
     * Through a gate, a creature in front of the far gate stands behind this one's plane, in the
     * middle of its opening when it stands in front of the far gate's middle.
     */
    @Test
    void throughAGateAStandInStandsBehindItsPlane()
    {
        final Place arrival = new Place("far", 100.5, 70.0, 200.5, 0.0f, 0.0f);
        final WindowShape shape = WindowShape.through(new Spot(10, 64, 20), new Spot(0, 0, -1), arrival, 5, 5);
        final List<Spot> open = new ArrayList<>();
        shape.forEachOpening((x, y, z) -> open.add(new Spot(x, y, z)));
        final GateSource gate = new GateSource("gate:Abydos", mock(Block.class), shape, open, arrival, "Chulak", 16);
        final Capture capture = new Capture.Builder("far", true, new Capture.Box(80, 60, 199, 41, 20, 34), mock(BlockData.class))
            .build();
        final WindowState gateWindow = new WindowState(gate, capture);

        final Location at = StandIns.whereNow(here, new StandIns.StandIn(creature(Zombie.class, 100.5, 70.0, 203.5),
            copy(Zombie.class), gateWindow));

        assertEquals(12.5, at.getX(), 1.0e-9, "the middle of a five-wide opening from 10 to 14");
        assertEquals(64.0, at.getY(), 1.0e-9, "level with the opening's bottom, as the arrival is");
        assertEquals(16.5, at.getZ(), 1.0e-9, "four behind the plane, as it stands three past the arrival");
    }

    private FarCreatures.Wanted wanted(final Entity original)
    {
        return new FarCreatures.Wanted(original, FarCreatures.hereOf(here, window.shape, original.getLocation()), window);
    }

    private <T extends Entity> T creature(final Class<T> type, final double x, final double y, final double z)
    {
        final T entity = mock(type);
        when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
        when(entity.isValid()).thenReturn(true);
        when(entity.getType()).thenReturn(EntityType.ZOMBIE);
        when(entity.getLocation()).thenReturn(new Location(far, x, y, z, 0.0f, 0.0f));
        return entity;
    }

    private static <T extends Entity> T copy(final Class<T> type)
    {
        final T entity = mock(type);
        when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
        when(entity.isValid()).thenReturn(true);
        return entity;
    }
}
