package com.wormhole_xtreme.wormhole.model.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Level;

import org.bukkit.Chunk;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
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
    private Chunk hereChunk;
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
        hereChunk = mock(Chunk.class);
        when(hereChunk.isEntitiesLoaded()).thenReturn(true);
        when(here.getChunkAt(anyInt(), anyInt())).thenReturn(hereChunk);
        when(here.getMinHeight()).thenReturn(-64);
        when(here.getMaxHeight()).thenReturn(320);
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

    /**
     * A shorn sheep's stand-in is shorn, and a woolly one's is not, whatever a fresh one would be.
     *
     * <p>Through the methods by name, as the plugin calls them: Paper 26.x deprecates them for removal.
     */
    @Test
    void aSheepsStandInIsShornOnlyIfItsSheepIs() throws ReflectiveOperationException
    {
        final Method isSheared = Sheep.class.getMethod("isSheared");
        final Method setSheared = Sheep.class.getMethod("setSheared", boolean.class);
        for (final boolean shorn : new boolean[] { true, false })
        {
            final Sheep original = creature(Sheep.class, 100.5, 70.0, -18.5);
            when(original.getType()).thenReturn(EntityType.SHEEP);
            when(isSheared.invoke(original)).thenReturn(shorn);
            final Sheep copy = copy(Sheep.class);
            when(isSheared.invoke(copy)).thenReturn(!shorn);

            StandIns.dress(copy, original);

            setSheared.invoke(verify(copy), shorn);
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
        when(unloaded.getMinHeight()).thenReturn(-64);
        when(unloaded.getMaxHeight()).thenReturn(320);
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

    /**
     * A throw while a stand-in is being dressed or shown leaves nothing in the world: it was known
     * for a stand-in before anything could throw, so it is found and removed.
     *
     * <p>Tracked only once the spawn returned, a throw in between left a real, invisible mob in the
     * viewer's world that no sweep could find.
     */
    @Test
    void aThrowWhileDressingOrShowingLeavesNothingInTheWorld()
    {
        final Zombie dressedBadly = copy(Zombie.class);
        doThrow(new IllegalStateException("broken setter")).when(dressedBadly).setSilent(true);
        nextCopy = dressedBadly;
        StandIns.show(viewer, view, List.of(wanted(creature(Zombie.class, 100.5, 70.0, -18.5))), 0L);

        verify(dressedBadly).remove();
        assertFalse(StandIns.isStandIn(dressedBadly));
        assertTrue(view.standIns.isEmpty());

        final Zombie shownBadly = copy(Zombie.class);
        doThrow(new IllegalStateException("broken show")).when(viewer).showEntity(plugin, shownBadly);
        nextCopy = shownBadly;
        StandIns.show(viewer, view, List.of(wanted(creature(Zombie.class, 100.5, 70.0, -17.5))), 0L);

        verify(shownBadly).remove();
        assertFalse(StandIns.isStandIn(shownBadly));
        assertEquals(0, StandIns.count(), "nothing left anywhere");
    }

    /**
     * A stand-in made and dressed, then refused as it is added (a protection plugin cancelling the
     * spawn), is forgotten and removed rather than left known for a stand-in.
     */
    @Test
    void aStandInRefusedAsItIsAddedIsForgotten()
    {
        final Zombie refused = copy(Zombie.class);
        when(refused.isValid()).thenReturn(false);
        nextCopy = refused;

        StandIns.show(viewer, view, List.of(wanted(creature(Zombie.class, 100.5, 70.0, -18.5))), 0L);

        verify(refused).remove();
        assertFalse(StandIns.isStandIn(refused));
        assertEquals(0, StandIns.count());
        verify(viewer, never()).showEntity(plugin, refused);
    }

    /**
     * The documented tag marks a stand-in for everything that asks, yet nothing removes an entity
     * this plugin did not make: a real mob an admin tagged is refused damage, never taken away.
     */
    @Test
    void theTagMarksAStandInButOnlyOursAreEverRemoved()
    {
        final Zombie tagged = copy(Zombie.class);
        when(tagged.getScoreboardTags()).thenReturn(Set.of(StandIns.TAG));
        final Zombie untagged = copy(Zombie.class);

        assertTrue(StandIns.isStandIn(tagged), "the tag alone marks one");
        assertFalse(StandIns.isStandIn(untagged), "and nothing else does");

        StandIns.sweepStrays(List.of(view));
        StandIns.removeEverything();
        verify(tagged, never()).remove();
    }

    /**
     * A creature hidden from the viewer only by this view's own veil may still be shown: a mirror onto
     * the room behind its own wall would otherwise show that room empty. One hidden otherwise may not.
     */
    @Test
    void aCreatureHiddenOnlyByThisViewsVeilMayStillBeShown()
    {
        final Zombie veiled = creature(Zombie.class, 100.5, 70.0, -18.5);
        final Zombie hidden = creature(Zombie.class, 101.5, 70.0, -18.5);
        final Zombie seen = creature(Zombie.class, 102.5, 70.0, -18.5);
        when(viewer.canSee(seen)).thenReturn(true);
        view.veiled.put(veiled.getUniqueId(), veiled);

        assertTrue(Windows.visibleTo(viewer, view, veiled), "hidden by our own veil, so shown");
        assertFalse(Windows.visibleTo(viewer, view, hidden), "hidden by something else, so not");
        assertTrue(Windows.visibleTo(viewer, view, seen), "not hidden at all");
    }

    /** A failure is logged once, not on every redraw of every viewer. */
    @Test
    void aFailureIsLoggedOnce()
    {
        for (int i = 0; i < 3; i++)
        {
            final Zombie broken = copy(Zombie.class);
            doThrow(new IllegalStateException("broken")).when(broken).setSilent(true);
            nextCopy = broken;
            StandIns.show(viewer, view, List.of(wanted(creature(Zombie.class, 100.5, 70.0, -18.5))), i * 10_000L);
        }

        verify(plugin, times(1)).prettyLog(eq(Level.WARNING), any(String.class), any(Throwable.class));
    }

    /** A stand-in wears and holds what its creature does, not what a fresh spawn rolled. */
    @Test
    void aStandInWearsAndHoldsWhatItsCreatureDoes()
    {
        final Zombie original = creature(Zombie.class, 100.5, 70.0, -18.5);
        final EntityEquipment worn = mock(EntityEquipment.class);
        final ItemStack[] armour = { mock(ItemStack.class), mock(ItemStack.class), mock(ItemStack.class), mock(ItemStack.class) };
        final ItemStack sword = mock(ItemStack.class);
        final ItemStack shield = mock(ItemStack.class);
        when(worn.getArmorContents()).thenReturn(armour);
        when(worn.getItemInMainHand()).thenReturn(sword);
        when(worn.getItemInOffHand()).thenReturn(shield);
        when(original.getEquipment()).thenReturn(worn);
        final Zombie copy = copy(Zombie.class);
        final EntityEquipment copied = mock(EntityEquipment.class);
        when(copy.getEquipment()).thenReturn(copied);

        StandIns.dress(copy, original);

        verify(copied).setArmorContents(armour);
        verify(copied).setItemInMainHand(sword);
        verify(copied).setItemInOffHand(shield);
    }

    /** A grown creature's stand-in is grown, though the copy came out a baby. */
    @Test
    void aGrownCreaturesStandInIsGrown()
    {
        final Zombie original = creature(Zombie.class, 100.5, 70.0, -18.5);
        when(original.isAdult()).thenReturn(true);
        final Zombie copy = copy(Zombie.class);
        when(copy.isAdult()).thenReturn(false);

        StandIns.dress(copy, original);

        verify(copy).setAdult();
        verify(copy, never()).setBaby();
    }

    /**
     * A stand-in is never placed in a chunk whose entities have not loaded, nor above or below the
     * world, where the server would refuse or move it.
     */
    @Test
    void aStandInIsNotPlacedWhereEntitiesAreNotLoadedOrOutsideTheWorld()
    {
        final Location behind = new Location(here, 10.5, 63.0, 14.5);
        assertTrue(StandIns.inRoom(window, behind), "the same place, with everything loaded");
        when(hereChunk.isEntitiesLoaded()).thenReturn(false);
        assertFalse(StandIns.inRoom(window, behind), "its chunk loaded but its entities not yet");
        when(hereChunk.isEntitiesLoaded()).thenReturn(true);
        when(here.getMaxHeight()).thenReturn(63);
        assertFalse(StandIns.inRoom(window, behind), "at the top of the world");
        when(here.getMaxHeight()).thenReturn(320);
        when(here.getMinHeight()).thenReturn(64);
        assertFalse(StandIns.inRoom(window, behind), "below the bottom of the world");
    }

    /** A viewer is known to be shown something through a window by their stand-ins alone. */
    @Test
    void aViewerIsKnownToBeShownThroughAWindowByTheirStandIns()
    {
        assertFalse(StandIns.shownThrough(view, "museum"), "nothing shown yet");
        nextCopy = copy(Zombie.class);
        StandIns.show(viewer, view, List.of(wanted(creature(Zombie.class, 100.5, 70.0, -18.5))), 0L);

        assertTrue(StandIns.shownThrough(view, "museum"));
        assertFalse(StandIns.shownThrough(view, "gallery"));
    }

    /**
     * A creature on the ground is offered only where the viewer has been drawn a captured block
     * under its feet, or under an edge of it.
     *
     * <p>The stand-ins seen on air in game (#296): a capture is old and a clipped view draws only
     * what is seen, so a creature can stand where the drawn room has nothing under it.
     */
    @Test
    void aCreatureOnTheGroundNeedsADrawnCapturedBlockUnderItsFeet()
    {
        final WindowState floored = windowWithFloorAt(100, 69, -19);
        final Location onIt = new Location(far, 100.5, 70.0, -18.5);
        assertFalse(StandIns.onDrawnFloor(view, floored, onIt), "the floor is captured but not yet drawn for this viewer");

        final Spot drawn = floored.shape.hereOf(100, 69, -19);
        view.drawn.put(Windows.key(drawn.x(), drawn.y(), drawn.z()), mock(BlockData.class));
        assertTrue(StandIns.onDrawnFloor(view, floored, onIt), "captured and drawn under its feet");
        assertTrue(StandIns.onDrawnFloor(view, floored, new Location(far, 101.2, 70.0, -18.5)),
            "standing over the block's edge, its side still on it");
        final Spot drawnAir = floored.shape.hereOf(102, 69, -19);
        view.drawn.put(Windows.key(drawnAir.x(), drawnAir.y(), drawnAir.z()), mock(BlockData.class));
        assertFalse(StandIns.onDrawnFloor(view, floored, new Location(far, 102.5, 70.0, -18.5)),
            "two blocks along, where the capture has air, drawn as air");
        assertFalse(StandIns.onDrawnFloor(view, floored, new Location(far, 100.5, 71.0, -18.5)),
            "a block up, over the air above the floor");
    }

    /**
     * The add and keep decisions differ, so a creature at an edge is not spawned and removed again
     * and again: one already shown keeps its window while that window is still seen, may stand a
     * block deeper, and needs no drawn floor; a new one needs all three.
     */
    @Test
    void aShownCreatureIsJudgedMoreLooselyThanANewOne()
    {
        when(viewer.canSee(any(Entity.class))).thenReturn(true);
        final WindowState other = new WindowState(new MirrorSource(new QuantumMirror("gallery",
            new BlockPlace("world", 10, 64, 10), ARRIVAL), mock(Block.class), window.shape, window.open, 16), window.capture);
        final Zombie walker = creature(Zombie.class, 100.5, 70.0, -18.5);
        when(walker.isOnGround()).thenReturn(true);
        when(walker.hasGravity()).thenReturn(true);
        final Map<String, WindowState> both = Map.of("museum", window, "gallery", other);
        final Predicate<FarCreatures.Wanted> keeps = wanted -> true;

        assertEquals(CreatureTally.Skip.NO_FLOOR, Windows.whyNot(viewer, view, window, both, keeps, walker),
            "new, on ground with no drawn floor: not offered");
        final Zombie flying = creature(Zombie.class, 100.5, 72.0, -18.5);
        assertNull(Windows.whyNot(viewer, view, window, both, keeps, flying), "with no gravity it needs no floor and floats as it does");
        nextCopy = copy(Zombie.class);
        StandIns.show(viewer, view, List.of(wanted(walker)), 0L);
        assertNull(Windows.whyNot(viewer, view, window, both, keeps, walker), "shown already: kept without a drawn floor");
        assertEquals(CreatureTally.Skip.OTHER_WINDOW, Windows.whyNot(viewer, view, other, both, keeps, walker),
            "not handed to the other window while its own still keeps it");
        assertNull(Windows.whyNot(viewer, view, other, both, wanted -> false, walker),
            "handed over at once when its own window would let it go, so it is in no gap between the two");
        assertNull(Windows.whyNot(viewer, view, other, Map.of("gallery", other), keeps, walker),
            "handed over once its own window is no longer seen");

        // Seventeen blocks in from the opening's middle: past the depth of 16, inside it with the slack.
        when(walker.getLocation()).thenReturn(new Location(far, 100.5, 70.5, -5.5));
        assertNull(Windows.whyNot(viewer, view, window, both, keeps, walker), "a held one a block past the room stays");
        assertNotNull(StandIns.whereNow(here, view.standIns.get(walker.getUniqueId())), "and is followed there");
        StandIns.removeAll(view);
        assertEquals(CreatureTally.Skip.OUT_OF_ROOM, Windows.whyNot(viewer, view, window, both, keeps, walker),
            "a new one there is not offered");
    }

    /**
     * A zombie mid-jump or falling needs a drawn floor within two blocks below it, as one standing
     * does right under it: a jump over terrain the drawn room does not have is not shown on air.
     */
    @Test
    void aJumpingZombieNeedsADrawnFloorWithinTwoBlocks()
    {
        when(viewer.canSee(any(Entity.class))).thenReturn(true);
        final WindowState floored = windowWithFloorAt(100, 69, -19);
        final Map<String, WindowState> seeing = Map.of("museum", floored);
        final Zombie jumping = creature(Zombie.class, 100.5, 71.5, -18.5);
        when(jumping.hasGravity()).thenReturn(true);
        when(jumping.isOnGround()).thenReturn(false);

        assertEquals(CreatureTally.Skip.NO_FLOOR, Windows.whyNot(viewer, view, floored, seeing, w -> true, jumping),
            "in the air over a floor not drawn for this viewer");
        final Spot drawn = floored.shape.hereOf(100, 69, -19);
        view.drawn.put(Windows.key(drawn.x(), drawn.y(), drawn.z()), mock(BlockData.class));
        assertNull(Windows.whyNot(viewer, view, floored, seeing, w -> true, jumping), "a block and a half over a drawn floor");
        when(jumping.getLocation()).thenReturn(new Location(far, 100.5, 73.5, -18.5, 0.0f, 0.0f));
        assertEquals(CreatureTally.Skip.NO_FLOOR, Windows.whyNot(viewer, view, floored, seeing, w -> true, jumping),
            "three and a half over it is past the two blocks a jump or a short fall reaches");
        when(jumping.isInWater()).thenReturn(true);
        assertNull(Windows.whyNot(viewer, view, floored, seeing, w -> true, jumping), "in water it needs no floor");
    }

    /** A creature on a fence or a wall stands half a block above its block: the floor is found in the block below its feet's own. */
    @Test
    void aCreatureOnAFenceOrAWallHasItsFloorFound()
    {
        final WindowState fenced = windowWithFloorAt(100, 69, -19);
        final Spot drawn = fenced.shape.hereOf(100, 69, -19);
        view.drawn.put(Windows.key(drawn.x(), drawn.y(), drawn.z()), mock(BlockData.class));

        assertTrue(StandIns.onDrawnFloor(view, fenced, new Location(far, 100.5, 70.5, -18.5)),
            "standing on top of a fence or a wall, a block and a half above its foot");
        assertFalse(StandIns.onDrawnFloor(view, fenced, new Location(far, 100.5, 71.0, -18.5)),
            "two blocks above it, over air, as before");
    }

    /**
     * A creature standing still for many redraws and follows is spawned once, never removed, and
     * never teleported: nothing is resent to a client for a stand-in that has not moved.
     */
    @Test
    void aCreatureStandingStillIsSpawnedOnceAndNeverMovedOrRemoved()
    {
        final Zombie still = creature(Zombie.class, 100.5, 70.0, -18.5);
        final Zombie copy = followsItsTeleports(copy(Zombie.class));
        nextCopy = copy;

        for (int round = 0; round < 50; round++)
        {
            StandIns.show(viewer, view, List.of(wanted(still)), round * 100L);
            StandIns.follow(view);
            StandIns.follow(view);
        }

        assertEquals(1, made.size(), "spawned once");
        verify(copy, never()).remove();
        verify(copy, never()).teleport(any(Location.class));
        assertTrue(StandIns.isStandIn(copy));
    }

    /**
     * A creature walking along the edge of the view, in and out of the strict view test every redraw
     * while the loose one holds, is spawned once and never removed, and each step is one teleport.
     */
    @Test
    void aCreatureWalkingAlongTheEdgeOfTheViewIsSpawnedOnceAndNeverRemoved()
    {
        final Zombie walker = creature(Zombie.class, 100.5, 70.0, -18.5);
        final Zombie copy = followsItsTeleports(copy(Zombie.class));
        nextCopy = copy;
        final Location eye = new Location(here, 10.5, 65.62, 7.5);
        int steps = 0;
        for (int round = 0; round < 40; round++)
        {
            if ((round % 4) == 3)
            {
                // A step along the edge, a block at a time.
                steps++;
                when(walker.getLocation()).thenReturn(new Location(far, 100.5 + steps, 70.0, -18.5));
            }
            final boolean inStrictView = (round % 2) == 0;
            final List<FarCreatures.Wanted> chosen = FarCreatures.choose(List.of(wanted(walker)), eye,
                FarCreatures.MOST_PER_VIEWER, FarCreatures.keepOrShow(view.standIns.keySet(), w -> true, w -> inStrictView));
            StandIns.show(viewer, view, chosen, round * 100L);
            StandIns.follow(view);
        }

        assertEquals(1, made.size(), "spawned once");
        verify(copy, never()).remove();
        verify(copy, times(steps)).teleport(any(Location.class));
    }

    /** A copy whose place is where it was last teleported, as a real entity's is. */
    private Zombie followsItsTeleports(final Zombie copy)
    {
        final Location[] at = { null };
        when(copy.getLocation()).thenAnswer(call -> at[0]);
        when(copy.teleport(any(Location.class))).thenAnswer(call ->
        {
            at[0] = call.getArgument(0);
            return true;
        });
        made.clear();
        HiddenEntities.creationWith(new HiddenEntities.Creation()
        {
            @Override
            public <T extends Entity> T create(final World world, final Location where, final Class<T> type)
            {
                made.add(copy);
                at[0] = where;
                return type.cast(copy);
            }

            @Override
            public <T extends Entity> T add(final World world, final T entity)
            {
                return entity;
            }
        });
        return copy;
    }

    /** The test window, drawn from a capture holding one solid block. */
    private WindowState windowWithFloorAt(final int x, final int y, final int z)
    {
        final BlockData air = mock(BlockData.class);
        when(air.getAsString()).thenReturn("minecraft:air");
        final BlockData stone = mock(BlockData.class);
        when(stone.getAsString()).thenReturn("minecraft:stone");
        final Capture.Builder builder = new Capture.Builder("far", true, new Capture.Box(60, 54, -61, 81, 81, 81), air);
        builder.put(x, y, z, stone);
        return new WindowState(window.source, builder.build());
    }

    /**
     * A happy ghast and the nautili hover or swim without being a Flying or a WaterMob, and are known
     * by their type's name, which a server older than them does not have.
     */
    @Test
    void creaturesNewerThanTheCompilePathAreKnownByTheirTypesName()
    {
        assertTrue(StandIns.hovering.containsAll(Set.of("HAPPY_GHAST", "NAUTILUS", "ZOMBIE_NAUTILUS")));
        final Zombie zombie = creature(Zombie.class, 100.5, 70.0, -18.5);
        when(zombie.hasGravity()).thenReturn(true);
        assertFalse(StandIns.floats(zombie), "a zombie walks");
        final Set<String> before = StandIns.hovering;
        StandIns.hovering = Set.of("ZOMBIE");
        try
        {
            assertTrue(StandIns.floats(zombie), "a type named in the list needs no floor");
        }
        finally
        {
            StandIns.hovering = before;
        }
    }

    /** A ghast and a phantom fly, known by their type's name now Paper deprecates the Flying they share. */
    @Test
    void aGhastAndAPhantomNeedNoFloor()
    {
        final Ghast ghast = creature(Ghast.class, 100.5, 70.0, -18.5);
        when(ghast.getType()).thenReturn(EntityType.GHAST);
        when(ghast.hasGravity()).thenReturn(true);
        final Phantom phantom = creature(Phantom.class, 100.5, 70.0, -18.5);
        when(phantom.getType()).thenReturn(EntityType.PHANTOM);
        when(phantom.hasGravity()).thenReturn(true);

        assertTrue(StandIns.floats(ghast), "a ghast flies");
        assertTrue(StandIns.floats(phantom), "a phantom flies");
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
