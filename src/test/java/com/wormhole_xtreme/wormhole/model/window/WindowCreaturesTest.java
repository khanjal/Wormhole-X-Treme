package com.wormhole_xtreme.wormhole.model.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.window.WindowShape.Spot;

/**
 * Which far creatures a viewer is shown, and which far areas are held for them, as the window drawing asks (#430).
 *
 * <p>Written for the move out of {@code Windows}, on the conditions the older stand-in tests left
 * unpinned: each test below fails when the one condition it is named for is taken out of
 * {@link WindowCreatures}. The collaborators that spawn and hold things are static mocks, so what is
 * checked is what the cluster asks of them.
 */
class WindowCreaturesTest
{
    private static final Place ARRIVAL = new Place("far", 100.5, 70.0, -20.5, 0.0f, 0.0f);
    private static final Place NOWHERE = new Place("void", 0.5, 70.0, 0.5, 0.0f, 0.0f);
    private static final WindowShape SHAPE = WindowShape.through(new Spot(10, 64, 20), new Spot(0, 0, -1), ARRIVAL, 3, 3);
    private static final WindowShape REFLECTION = new WindowShape(new Spot(10, 64, 20), new Spot(0, 0, -1),
        new Spot(100, 70, -20), new Spot(0, 0, 1), true, 3);

    private World here;
    private World far;
    private Player viewer;
    private MockedStatic<StandIns> standIns;
    private MockedStatic<ConfigManager> config;
    private MockedStatic<WindowVeil> veil;
    private MockedStatic<FarChunkHolds> holds;
    private MockedStatic<FarCreatures> creatures;
    private MockedStatic<Bukkit> bukkit;

    /** A window source that is only what the drawing asks of one. */
    private record Source(String name, Block anchor, WindowShape shape, List<Spot> open, Place destination)
        implements WindowSource
    {
        @Override
        public boolean walkThrough()
        {
            return false;
        }

        @Override
        public int depth()
        {
            return 16;
        }
    }

    @BeforeEach
    void setUp()
    {
        here = mock(World.class);
        far = mock(World.class);
        viewer = mock(Player.class);
        when(viewer.canSee(any(Entity.class))).thenReturn(true);
        WindowStore.VIEWS.clear();
        WindowStore.ACTIVE.clear();
        standIns = mockStatic(StandIns.class);
        standIns.when(() -> StandIns.inRoom(any(), any(), anyBoolean())).thenReturn(true);
        standIns.when(() -> StandIns.floats(any())).thenReturn(true);
        config = mockStatic(ConfigManager.class);
        config.when(ConfigManager::isMirrorShowEntities).thenReturn(true);
        config.when(ConfigManager::getMirrorEntityLoadRadius).thenReturn(4);
        veil = mockStatic(WindowVeil.class);
        veil.when(() -> WindowVeil.stillSeen(any(), any(), any(), any())).thenReturn(true);
        veil.when(() -> WindowVeil.inAnyView(any(), any(), any(), any(), any())).thenReturn(true);
        holds = mockStatic(FarChunkHolds.class);
        creatures = mockStatic(FarCreatures.class, CALLS_REAL_METHODS);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.getWorld("far")).thenReturn(far);
    }

    @AfterEach
    void tearDown()
    {
        bukkit.close();
        creatures.close();
        holds.close();
        veil.close();
        config.close();
        standIns.close();
        WindowStore.VIEWS.clear();
        WindowStore.ACTIVE.clear();
    }

    private Block anchorAt(final int x, final int y, final int z)
    {
        final Block block = mock(Block.class);
        when(block.getWorld()).thenReturn(here);
        when(block.getX()).thenReturn(x);
        when(block.getY()).thenReturn(y);
        when(block.getZ()).thenReturn(z);
        return block;
    }

    private WindowState window(final String name, final int ax, final int ay, final int az, final WindowShape shape,
        final Place destination)
    {
        final List<Spot> open = new ArrayList<>();
        shape.forEachOpening((x, y, z) -> open.add(new Spot(x, y, z)));
        final WindowState window = new WindowState(new Source(name, anchorAt(ax, ay, az), shape, open, destination), null);
        window.creatures = List.of();
        return window;
    }

    private WindowState window(final String name, final int ax, final int ay, final int az)
    {
        return window(name, ax, ay, az, SHAPE, ARRIVAL);
    }

    private WindowState active(final String name, final int ax, final int ay, final int az, final WindowShape shape,
        final Place destination)
    {
        final WindowState window = window(name, ax, ay, az, shape, destination);
        WindowStore.ACTIVE.put(name, window);
        return window;
    }

    private Entity creature()
    {
        final Entity entity = mock(Entity.class);
        when(entity.isValid()).thenReturn(true);
        when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
        when(entity.getLocation()).thenReturn(new Location(far, 100.5, 70.0, -25.5));
        return entity;
    }

    private ViewerDrawing viewing(final String... mirrors)
    {
        final ViewerDrawing view = new ViewerDrawing(here);
        view.mirrors = Set.of(mirrors);
        return view;
    }

    private static Windows.Redraw drawnFrom(final int x, final int y, final int z)
    {
        return new Windows.Redraw(0, 0, 0, 0, new Spot(x, y, z), 0L);
    }

    private static void shows(final ViewerDrawing view, final Entity original, final WindowState through)
    {
        view.standIns.put(original.getUniqueId(), new StandIns.StandIn(original, mock(Entity.class), through));
    }

    private static WindowCompose.Wholes drawnWhole(final WindowState... windows)
    {
        final Map<WindowState, Windows.Whole> whole = new HashMap<>();
        for (final WindowState window : windows)
        {
            whole.put(window, new Windows.Whole(new HashMap<>(), 1));
        }
        return new WindowCompose.Wholes(whole, new HashMap<>());
    }

    private static WindowCompose.Wholes drawnClipped(final WindowState window)
    {
        return new WindowCompose.Wholes(new HashMap<>(), Map.of(window, new Windows.Whole(new HashMap<>(), 1)));
    }

    private Location eye()
    {
        return new Location(here, 10.5, 65.5, 24.5);
    }

    /** Shows the viewer the creatures of these windows, and gives back what was handed to be spawned. */
    private List<FarCreatures.Wanted> shown(final ViewerDrawing view, final List<WindowState> seeing,
        final WindowCompose.Wholes wholes)
    {
        WindowCreatures.showStandIns(viewer, view, eye(), seeing, wholes, 1000L);
        final ArgumentCaptor<List<FarCreatures.Wanted>> list = ArgumentCaptor.forClass(List.class);
        standIns.verify(() -> StandIns.show(eq(viewer), eq(view), list.capture(), eq(1000L)));
        return list.getValue();
    }

    // --- Following ---

    @Test
    void followingMovesEveryViewersStandInsAndStopsOnlyWhenNoneAreLeft()
    {
        final ViewerDrawing shown = new ViewerDrawing(here);
        shows(shown, creature(), window("gate:Abydos", 0, 0, 0));
        final ViewerDrawing empty = new ViewerDrawing(here);
        WindowStore.VIEWS.put(UUID.randomUUID(), shown);
        WindowStore.VIEWS.put(UUID.randomUUID(), empty);
        standIns.when(StandIns::count).thenReturn(2);

        WindowCreatures.followStandIns();

        standIns.verify(() -> StandIns.follow(shown));
        standIns.verify(() -> StandIns.follow(empty), never());
        standIns.verify(StandIns::stopFollowing, never());

        standIns.when(StandIns::count).thenReturn(0);

        WindowCreatures.followStandIns();

        standIns.verify(StandIns::stopFollowing);
    }

    @Test
    void aFollowThatFailsIsLoggedAndTakesTheStandInsAway()
    {
        final ViewerDrawing view = new ViewerDrawing(here);
        shows(view, creature(), window("gate:Abydos", 0, 0, 0));
        final IllegalStateException failed = new IllegalStateException("no");
        standIns.when(() -> StandIns.follow(view)).thenThrow(failed);

        WindowCreatures.followStandIns(view);

        standIns.verify(() -> StandIns.failedOnce("Could not move the stand-ins shown through a view", failed));
        standIns.verify(() -> StandIns.removeAll(view));
    }

    @Test
    void aDropThatFailsTooLeavesNoneListed()
    {
        final ViewerDrawing view = new ViewerDrawing(here);
        shows(view, creature(), window("gate:Abydos", 0, 0, 0));
        standIns.when(() -> StandIns.follow(view)).thenThrow(new IllegalStateException("no"));
        standIns.when(() -> StandIns.removeAll(view)).thenThrow(new IllegalStateException("also"));

        WindowCreatures.followStandIns(view);

        assertTrue(view.standIns.isEmpty(), "the stray sweep has nothing left listed to trip over");
    }

    // --- Showing ---

    @Test
    void withTheSettingOffTheStandInsAreTakenAwayAndNoneAreShown()
    {
        config.when(ConfigManager::isMirrorShowEntities).thenReturn(false);
        final ViewerDrawing view = new ViewerDrawing(here);
        shows(view, creature(), window("gate:Abydos", 0, 0, 0));

        WindowCreatures.showStandIns(viewer, view, eye(), List.of(), drawnWhole(), 1000L);

        standIns.verify(() -> StandIns.removeAll(view));
        standIns.verify(() -> StandIns.show(any(), any(), any(), anyLong()), never());
    }

    @Test
    void aViewerWhoIsDeadIsShownNoneAndLosesTheirs()
    {
        when(viewer.isDead()).thenReturn(true);
        final ViewerDrawing view = new ViewerDrawing(here);
        shows(view, creature(), window("gate:Abydos", 0, 0, 0));

        WindowCreatures.showStandIns(viewer, view, eye(), List.of(), drawnWhole(), 1000L);

        standIns.verify(() -> StandIns.removeAll(view));
        standIns.verify(() -> StandIns.show(any(), any(), any(), anyLong()), never());
    }

    @Test
    void aLivingViewerWithNoneToLoseKeepsWhatTheyHaveAndIsShownTheRest()
    {
        final ViewerDrawing view = new ViewerDrawing(here);

        assertTrue(shown(view, List.of(), drawnWhole()).isEmpty(), "no windows, no creatures");
        standIns.verify(() -> StandIns.removeAll(any()), never());
    }

    @Test
    void aShowThatFailsIsLoggedAndTakesTheStandInsAway()
    {
        final ViewerDrawing view = new ViewerDrawing(here);
        final IllegalStateException failed = new IllegalStateException("refused");
        standIns.when(() -> StandIns.show(any(), any(), any(), anyLong())).thenThrow(failed);

        WindowCreatures.showStandIns(viewer, view, eye(), List.of(), drawnWhole(), 1000L);

        standIns.verify(() -> StandIns.failedOnce("Could not show the creatures in a view's far room", failed));
        standIns.verify(() -> StandIns.removeAll(view));
    }

    // --- Which creatures ---

    @Test
    void aNewCreatureInADrawnRoomIsShownAndOneInARoomNotDrawnIsNot()
    {
        final WindowState drawn = window("gate:Abydos", 0, 0, 0);
        final WindowState undrawn = window("gate:Chulak", 40, 0, 0);
        final Entity inDrawn = creature();
        final Entity inUndrawn = creature();
        drawn.creatures = List.of(inDrawn);
        undrawn.creatures = List.of(inUndrawn);

        final List<FarCreatures.Wanted> wanted = shown(new ViewerDrawing(here), List.of(drawn, undrawn), drawnWhole(drawn));

        assertEquals(List.of(inDrawn), wanted.stream().map(FarCreatures.Wanted::original).toList(),
            "only the one whose window's room is drawn");
    }

    @Test
    void aNewCreatureIsShownWhenSomeOpeningSeesIt()
    {
        final WindowState window = window("gate:Abydos", 0, 0, 0);
        window.creatures = List.of(creature());
        veil.when(() -> WindowVeil.inAnyView(any(), any(), any(), any(), eq(window.openKeys))).thenReturn(true);
        veil.when(() -> WindowVeil.inAnyView(any(), any(), any(), any(), eq(Set.of()))).thenReturn(false);

        final List<FarCreatures.Wanted> wanted = shown(new ViewerDrawing(here), List.of(window), drawnWhole(window));

        assertEquals(1, wanted.size(), "judged against every opening of the windows seen");
    }

    @Test
    void aHeldCreatureIsKeptByTheLooserTestOnlyInADrawnRoom()
    {
        veil.when(() -> WindowVeil.stillSeen(any(), any(), any(), any())).thenReturn(false);
        final WindowState whole = window("gate:Abydos", 0, 0, 0);
        final WindowState clipped = window("gate:Chulak", 40, 0, 0);
        final WindowState undrawn = window("gate:Dakara", 80, 0, 0);
        final Entity inWhole = creature();
        final Entity inClipped = creature();
        final Entity inUndrawn = creature();
        whole.creatures = List.of(inWhole);
        clipped.creatures = List.of(inClipped);
        undrawn.creatures = List.of(inUndrawn);
        final ViewerDrawing view = new ViewerDrawing(here);
        shows(view, inWhole, whole);
        shows(view, inClipped, clipped);
        shows(view, inUndrawn, undrawn);
        final Map<WindowState, Windows.Whole> wholes = new HashMap<>();
        wholes.put(whole, new Windows.Whole(new HashMap<>(), 1));
        final Map<WindowState, Windows.Whole> clips = new HashMap<>();
        clips.put(clipped, new Windows.Whole(new HashMap<>(), 1));

        final List<FarCreatures.Wanted> wanted = shown(view, List.of(whole, clipped, undrawn),
            new WindowCompose.Wholes(wholes, clips));

        assertEquals(List.of(inUndrawn), wanted.stream().map(FarCreatures.Wanted::original).toList(),
            "a drawn room no longer sees it: it goes; a room not drawn this redraw leaves it as it is");
    }

    @Test
    void aCreatureSeenThroughTwoWindowsIsShownThroughTheNearerOne()
    {
        final WindowState far1 = window("gate:Abydos", 100, 64, 100);
        final WindowState near = window("gate:Chulak", 10, 64, 24);
        final Entity both = creature();
        far1.creatures = List.of(both);
        near.creatures = List.of(both);

        final List<FarCreatures.Wanted> wanted = shown(new ViewerDrawing(here), List.of(far1, near), drawnWhole(far1, near));

        assertEquals(1, wanted.size(), "once");
        assertSame(near, wanted.get(0).window(), "through the window nearest the eye, which the tie goes to");
    }

    @Test
    void aShownCreatureStaysWithTheWindowItWasShownThroughWhileThatOneStillSeesIt()
    {
        final WindowState holding = window("gate:Abydos", 100, 64, 100);
        final WindowState nearer = window("gate:Chulak", 10, 64, 24);
        final Entity both = creature();
        holding.creatures = List.of(both);
        nearer.creatures = List.of(both);
        final ViewerDrawing view = new ViewerDrawing(here);
        shows(view, both, holding);

        final List<FarCreatures.Wanted> wanted = shown(view, List.of(holding, nearer), drawnWhole(holding, nearer));

        assertSame(holding, wanted.get(0).window(), "the nearer window does not take it while the other keeps it");
    }

    @Test
    void aShownCreatureIsHandedToAnotherWindowWhenItsOwnWouldLetItGo()
    {
        final WindowState holding = window("gate:Abydos", 100, 64, 100);
        final WindowState nearer = window("gate:Chulak", 10, 64, 24);
        final Entity both = creature();
        holding.creatures = List.of(both);
        nearer.creatures = List.of(both);
        final ViewerDrawing view = new ViewerDrawing(here);
        shows(view, both, holding);
        standIns.when(() -> StandIns.inRoom(eq(holding), any(), anyBoolean())).thenReturn(false);

        final List<FarCreatures.Wanted> wanted = shown(view, List.of(holding, nearer), drawnWhole(holding, nearer));

        assertSame(nearer, wanted.get(0).window(), "the room it was shown through no longer holds it, so this one takes over");
    }

    @Test
    void aReflectionOffersNoCreaturesAndLeavesNoTally()
    {
        final WindowState mirror = window("gate:Abydos", 0, 0, 0, REFLECTION, ARRIVAL);
        mirror.creatures = List.of(creature());
        final ViewerDrawing view = new ViewerDrawing(here);
        view.tallies.put("gone", new CreatureTally());

        final List<FarCreatures.Wanted> wanted = shown(view, List.of(mirror), drawnWhole(mirror));

        assertTrue(wanted.isEmpty(), "its room is the viewer's own");
        assertFalse(view.tallies.containsKey("gate:Abydos"), "and it is not counted");
        assertFalse(view.tallies.containsKey("gone"), "the last redraw's tallies are not kept");
    }

    @Test
    void whatIsFoundAndShownIsTalliedPerWindow()
    {
        final WindowState window = window("gate:Abydos", 0, 0, 0);
        final Entity valid = creature();
        final Entity gone = creature();
        when(gone.isValid()).thenReturn(false);
        window.creatures = List.of(valid, gone);
        final ViewerDrawing view = new ViewerDrawing(here);

        shown(view, List.of(window), drawnWhole(window));

        final CreatureTally tally = view.tallies.get("gate:Abydos");
        assertEquals(1, tally.found, "an entity that is gone is not found");
        assertEquals(0, tally.players, "and a mob is not a player");
        assertEquals(1, tally.shown, "the one chosen is counted as shown");
    }

    @Test
    void aCreatureSkippedIsTalliedWhy()
    {
        final WindowState window = window("gate:Abydos", 0, 0, 0);
        final Entity hidden = creature();
        when(viewer.canSee(hidden)).thenReturn(false);
        window.creatures = List.of(hidden);
        final ViewerDrawing view = new ViewerDrawing(here);

        shown(view, List.of(window), drawnWhole(window));

        assertEquals(1, view.tallies.get("gate:Abydos").skipped.get(CreatureTally.Skip.HIDDEN), "hidden from the viewer");
    }

    @Test
    void aWindowsCreaturesAreReadOnceAndWhatWasMissedIsKept()
    {
        final WindowState window = window("gate:Abydos", 0, 0, 0);
        window.creatures = null;
        final Capture capture = mock(Capture.class);
        when(capture.worldName()).thenReturn("far");
        when(capture.bounds()).thenReturn(new int[] { 0, 0, 0, 1, 1, 1 });
        final WindowState reading = new WindowState(window.source, capture);
        config.when(ConfigManager::isMirrorShowPlayers).thenReturn(false);
        creatures.when(() -> FarCreatures.roomBox(any(), anyInt(), any())).thenReturn(new int[] { 0, 0, 0, 1, 1, 1 });
        final Entity found = creature();
        creatures.when(() -> FarCreatures.inRoom(eq(far), any(), any(), eq(false))).thenAnswer(call ->
        {
            final int[] tally = call.getArgument(2);
            tally[0] = 3;
            tally[1] = 4;
            tally[2] = 5;
            return List.of(found);
        });

        final List<FarCreatures.Wanted> wanted = shown(new ViewerDrawing(here), List.of(reading), drawnWhole(reading));

        assertEquals(List.of(found), wanted.stream().map(FarCreatures.Wanted::original).toList(), "read from the far room");
        assertEquals(3, reading.notLoaded, "chunks not loaded");
        assertEquals(4, reading.entitiesNotLoaded, "chunks whose creatures had not");
        assertEquals(5, reading.notCopied, "entities not copied");
    }

    @Test
    void aWindowsCreaturesAlreadyReadAreNotReadAgain()
    {
        final WindowState window = window("gate:Abydos", 0, 0, 0);
        final Entity read = creature();
        window.creatures = List.of(read);

        final List<FarCreatures.Wanted> wanted = shown(new ViewerDrawing(here), List.of(window), drawnWhole(window));

        assertEquals(1, wanted.size(), "the creatures this sweep already read are the ones offered");
        creatures.verify(() -> FarCreatures.roomBox(any(), anyInt(), any()), never());
    }

    @Test
    void aViewersVisibleOrVeiledCreatureIsOneTheyMaySee()
    {
        final Entity creature = creature();
        final ViewerDrawing view = new ViewerDrawing(here);
        when(viewer.canSee(creature)).thenReturn(false);

        assertFalse(WindowCreatures.visibleTo(viewer, view, creature), "hidden by something else");

        view.veiled.put(creature.getUniqueId(), creature);
        assertTrue(WindowCreatures.visibleTo(viewer, view, creature), "hidden by this view's own veil");

        final Entity seen = creature();
        assertTrue(WindowCreatures.visibleTo(viewer, new ViewerDrawing(here), seen), "not hidden at all");
    }

    // --- Holding the far areas ---

    private Map<String, List<FarChunkHolds.Area>> held(final long now)
    {
        WindowCreatures.holdFarAreas(now);
        final ArgumentCaptor<Map<String, List<FarChunkHolds.Area>>> watched = ArgumentCaptor.forClass(Map.class);
        holds.verify(() -> FarChunkHolds.settle(watched.capture(), eq(now)));
        return watched.getValue();
    }

    private ViewerDrawing watching(final int x, final int y, final int z, final String... names)
    {
        final ViewerDrawing view = viewing(names);
        view.lastRedraw = drawnFrom(x, y, z);
        WindowStore.VIEWS.put(UUID.randomUUID(), view);
        return view;
    }

    @Test
    void nothingIsHeldWhileTheRadiusIsZero()
    {
        config.when(ConfigManager::getMirrorEntityLoadRadius).thenReturn(0);
        active("gate:Abydos", 5, 0, 0, SHAPE, ARRIVAL);
        watching(0, 0, 0, "gate:Abydos");

        assertTrue(held(1000L).isEmpty(), "no radius, nothing to hold");
    }

    @Test
    void nothingIsHeldWhileCreaturesAreNotShown()
    {
        config.when(ConfigManager::isMirrorShowEntities).thenReturn(false);
        active("gate:Abydos", 5, 0, 0, SHAPE, ARRIVAL);
        watching(0, 0, 0, "gate:Abydos");

        assertTrue(held(1000L).isEmpty(), "no creatures shown, nothing to show them on");
    }

    @Test
    void windowsAreHeldNearestFirstAndTiesGoByName()
    {
        active("zeta", 0, 5, 0, SHAPE, ARRIVAL);
        active("alpha", 5, 0, 0, SHAPE, ARRIVAL);
        active("mid", 0, 0, 5, SHAPE, ARRIVAL);
        active("close", 1, 0, 0, SHAPE, ARRIVAL);
        watching(0, 0, 0, "zeta", "alpha", "mid", "close");

        assertEquals(List.of("close", "alpha", "mid", "zeta"), List.copyOf(held(1000L).keySet()),
            "the nearest, then the three a block apart by name");
    }

    @Test
    void aReflectionAWindowGoneAndAFarWorldNotLoadedAreNotHeld()
    {
        active("reflection", 5, 0, 0, REFLECTION, ARRIVAL);
        active("lost", 5, 0, 0, SHAPE, NOWHERE);
        active("kept", 5, 0, 0, SHAPE, ARRIVAL);
        watching(0, 0, 0, "reflection", "lost", "kept", "gone");

        assertEquals(List.of("kept"), List.copyOf(held(1000L).keySet()),
            "a reflection looks into the viewer's own room, the window 'gone' is no longer offered, and 'void' is not loaded");
    }

    @Test
    void aWindowWatchedByTwoViewersIsHeldByTheNearer()
    {
        active("shared", 10, 0, 0, SHAPE, ARRIVAL);
        active("between", 5, 0, 0, SHAPE, ARRIVAL);
        watching(0, 0, 0, "shared");
        watching(9, 0, 0, "shared");
        watching(0, 0, 3, "between");

        assertEquals(List.of("shared", "between"), List.copyOf(held(1000L).keySet()),
            "the viewer at 9 is a block away; between is 5 from the other, so it comes second");
    }

    @Test
    void aWindowNoViewerHasDrawnYetIsHeldAfterThoseWithAnEye()
    {
        active("unseen", 5, 0, 0, SHAPE, ARRIVAL);
        active("drawn", 50, 0, 0, SHAPE, ARRIVAL);
        final ViewerDrawing neverDrawn = viewing("unseen");
        WindowStore.VIEWS.put(UUID.randomUUID(), neverDrawn);
        watching(0, 0, 0, "drawn");

        assertEquals(List.of("drawn", "unseen"), List.copyOf(held(1000L).keySet()),
            "a viewer with no eye yet is far off, however near the window is");
    }

    @Test
    void aHoldThatFailsIsLoggedOnce()
    {
        active("gate:Abydos", 5, 0, 0, SHAPE, ARRIVAL);
        watching(0, 0, 0, "gate:Abydos");
        final IllegalStateException failed = new IllegalStateException("no");
        holds.when(() -> FarChunkHolds.settle(any(), anyLong())).thenThrow(failed);

        WindowCreatures.holdFarAreas(1000L);

        standIns.verify(() -> StandIns.failedOnce(anyString(), eq(failed)), times(1));
        standIns.verify(() -> StandIns.failedOnce("Could not hold the chunks in front of a view's far side", failed));
    }
}
