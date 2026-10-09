package com.wormhole_xtreme.wormhole.model.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.PrivateStatics;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.model.GateSource;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorSource;
import com.wormhole_xtreme.wormhole.model.mirror.QuantumMirror;
import com.wormhole_xtreme.wormhole.model.window.WindowShape.Spot;

/**
 * An open gate offered to the mirror sweep as a window (#516): when it is one, and where its capture comes from.
 *
 * <p>A gate is drawn from a capture kept for the gate it shows and seen through its own opening.
 * Served a mirror's capture of the same place, a five-by-five gate drew a view cut to a three-by-two
 * hole, with the real world showing round its edges; a gate that was a window one sweep had to stay
 * one, and go when released, or a closed gate went on showing where it used to go.
 *
 * <p>Its own class rather than more of {@code WindowsTest}, which already needs gigabytes.
 */
class WindowsGateTest
{
    private static final String NAME = "gate:Abydos";

    /** Where travellers through the gate land, in front of Chulak: facing south. */
    private static final Place ARRIVAL = new Place("far", 100.5, 70.0, 200.5, 0.0f, 0.0f);

    @TempDir
    File dataFolder;

    private World world;
    private Block anchor;
    private GateSource gate;

    @BeforeEach
    void setUp() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        PluginTestSupport.install(plugin);
        PluginTestSupport.scheduler(null);
        ConfigTestSupport.clear();
        // No fill behind the first step unless a test is about it.
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW_FULL_DEPTH, 0);
        Windows.clear();

        world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(world.getPlayers()).thenReturn(List.of());
        anchor = mock(Block.class);
        when(anchor.getWorld()).thenReturn(world);
        when(anchor.getX()).thenReturn(10);
        when(anchor.getY()).thenReturn(64);
        when(anchor.getZ()).thenReturn(20);
        // A Standard gate's five by five, facing south: looked into northwards.
        final WindowShape shape = WindowShape.through(new Spot(10, 64, 20), new Spot(0, 0, -1), ARRIVAL, 5, 5);
        final List<Spot> open = new ArrayList<>();
        shape.forEachOpening((x, y, z) -> open.add(new Spot(x, y, z)));
        gate = new GateSource(NAME, anchor, shape, open, ARRIVAL, "Chulak", 16);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        Windows.clear();
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    /** A capture of the far side as though it had been taken, reaching {@code ahead} blocks past the arrival. */
    private static Capture capture(final int ahead)
    {
        final BlockData air = mock(BlockData.class);
        when(air.getAsString()).thenReturn("minecraft:air");
        return new Capture.Builder("far", true, new Capture.Box(80, 60, 199, 41, 20, ahead + 2), air).build();
    }

    private static String key()
    {
        return Captures.gateKey("Chulak", Captures.GATE_OPENING, Captures.GATE_OPENING);
    }

    @Test
    void aGateWithNoCaptureYetIsNotAWindow()
    {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            // The far world is not loaded, so the capture cannot even be started.
            bukkit.when(() -> Bukkit.getWorld(anyString())).thenReturn(null);

            assertFalse(GateSource.offer(gate, true), "nothing to draw from, so it keeps its horizon");
            assertFalse(Windows.holdsWindow(NAME));
            bukkit.verify(() -> Bukkit.getWorld("far"));
        }
    }

    @Test
    void aGateIsAWindowOnceTheCaptureOfItsFarGateIsIn()
    {
        Captures.install(key(), capture(32));

        assertTrue(GateSource.offer(gate, true), "its capture is in");
        assertTrue(Windows.holdsWindow(NAME), "and the sweep holds it");
    }

    @Test
    void aMirrorsCaptureOfTheSamePlaceIsNotAGates()
    {
        Captures.install(ARRIVAL, capture(32));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld(anyString())).thenReturn(null);

            assertFalse(GateSource.offer(gate, true),
                "a capture seen through a mirror's three by two is not drawn through a five by five");
        }
    }

    /**
     * A capture shallower than the gate's depth is still drawn, and a deeper one is asked for.
     *
     * <p>A capture kept from before {@code gate-view-depth} was raised is the best base there is;
     * showing nothing until the deeper one arrived would be worse than showing it short.
     */
    @Test
    void aShallowCaptureIsDrawnWhileADeeperOneIsTaken()
    {
        Captures.install(key(), capture(4));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld(anyString())).thenReturn(null);

            assertTrue(GateSource.offer(gate, false), "drawn from what there is");
            bukkit.verify(() -> Bukkit.getWorld("far"));
        }
    }

    @Test
    void aFreshCaptureDeepEnoughIsNotTakenAgain()
    {
        Captures.install(key(), capture(32));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            assertTrue(GateSource.offer(gate, true));
            bukkit.verify(() -> Bukkit.getWorld(anyString()), never());
        }
    }

    /**
     * A gate being dialled has its capture asked for then, not after its kawoosh.
     *
     * <p>A capture of somewhere nobody had loaded starts by reading it off the disk, and waiting for
     * the sweep after the kawoosh to ask left a remote gate on its horizon long enough to close.
     */
    @Test
    void aGateBeingDialledHasItsCaptureAskedForThen()
    {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld(anyString())).thenReturn(null);

            GateSource.prepare(gate);

            bukkit.verify(() -> Bukkit.getWorld("far"));
        }
    }

    @Test
    void aGateStaysAWindowAcrossSweepsUntilItIsReleased()
    {
        Captures.install(key(), capture(32));
        assertTrue(GateSource.offer(gate, true));
        Windows.finish();

        assertTrue(GateSource.offer(gate, false), "offered again the next sweep");
        Windows.finish();
        assertTrue(Windows.holdsWindow(NAME), "a window between sweeps");

        Windows.release(NAME);

        assertFalse(Windows.holdsWindow(NAME), "released by name as its gate closes");
    }

    /**
     * A gate's held room is kept from sweep to sweep, and let go once a fresh capture is in.
     *
     * <p>Kept across a new capture, the room built from the old one went on standing in for it in
     * the debug lines and in memory until it aged out. Mirrors keep theirs either way, so this is
     * the one thing the shared offer still asks a source's walk-through answer about.
     */
    @Test
    void aGatesHeldRoomIsKeptUntilAFreshCaptureIsIn() throws ReflectiveOperationException
    {
        Captures.install(key(), capture(32));
        assertTrue(GateSource.offer(gate, false));
        Windows.finish();
        final Map<String, WindowState> active = PrivateStatics.of(Windows.class, "ACTIVE");
        final Map<String, WindowState> offered = PrivateStatics.of(Windows.class, "OFFERED");
        final Map<Long, BlockData> room = new HashMap<>();
        active.get(NAME).fixed = room;
        active.get(NAME).fixedUsedAt = System.currentTimeMillis();

        assertTrue(GateSource.offer(gate, false));
        assertSame(room, offered.get(NAME).fixed, "the same capture: the room built from it is kept");

        Captures.install(key(), capture(32));
        assertTrue(GateSource.offer(gate, false));
        assertNull(offered.get(NAME).fixed, "a fresh capture: the old room is let go");
    }

    /** A mirror's held room, unlike a gate's, outlives a fresh capture until it is rebuilt from it. */
    @Test
    void aMirrorsHeldRoomIsKeptAcrossAFreshCapture() throws ReflectiveOperationException
    {
        final MirrorSource mirror = new MirrorSource(new QuantumMirror("museum", BlockPlace.of(anchor), ARRIVAL), anchor,
            gate.shape(), gate.open(), 16);
        Windows.offer(mirror, capture(32));
        Windows.finish();
        final Map<String, WindowState> active = PrivateStatics.of(Windows.class, "ACTIVE");
        final Map<String, WindowState> offered = PrivateStatics.of(Windows.class, "OFFERED");
        final Map<Long, BlockData> room = new HashMap<>();
        active.get("museum").fixed = room;
        active.get("museum").fixedUsedAt = System.currentTimeMillis();

        Windows.offer(mirror, capture(32));

        assertSame(room, offered.get("museum").fixed, "kept, and rebuilt from the fresh capture when next drawn");
    }

    /**
     * Once the first step is in, the fill out to the full depth is asked for behind it.
     *
     * <p>A remote gate shows its first step at once; what lies past it should follow rather than
     * waiting for somebody to raise the depth and dial again.
     */
    @Test
    void theFirstStepIsFollowedByTheFill()
    {
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW_FULL_DEPTH, 48);
        // Outside the verify: a call to Captures inside it would be what is verified.
        final String key = key();
        Captures.install(key, capture(20));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
            MockedStatic<Captures> captures = mockStatic(Captures.class, CALLS_REAL_METHODS))
        {
            captures.when(() -> Captures.requestGate(anyString(), anyString(), any(Place.class), anyInt(),
                anyInt(), anyInt())).thenReturn(true);

            assertTrue(GateSource.offer(gate, false), "the first step is drawn meanwhile");
            captures.verify(() -> Captures.requestGate(eq(key), eq("Chulak"), any(Place.class), eq(18),
                eq(18), eq(48)), times(1));
        }
    }

    @Test
    void aCaptureThatReachesTheFullDepthIsLeftAlone()
    {
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW_FULL_DEPTH, 48);
        Captures.install(key(), capture(60));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
            MockedStatic<Captures> captures = mockStatic(Captures.class, CALLS_REAL_METHODS))
        {
            assertTrue(GateSource.offer(gate, false));
            captures.verify(() -> Captures.requestGate(anyString(), anyString(), any(Place.class), anyInt(),
                anyInt(), anyInt()), never());
        }
    }

    /**
     * A gate whose fill is in is drawn to the full depth, not its first step's.
     *
     * <p>The depth rides on the source a gate offers each sweep. Offered at its first step, a view
     * captured out to 48 blocks would be drawn to 16, and the rest of the fill never shown.
     */
    @Test
    void aGateWhoseFillIsInIsDrawnToTheFullDepth() throws ReflectiveOperationException
    {
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW_FULL_DEPTH, 48);
        Captures.install(key(), capture(60));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            assertTrue(GateSource.offer(gate, false));
        }
        final Map<String, WindowState> offered = PrivateStatics.of(Windows.class, "OFFERED");

        assertEquals(48, offered.get(NAME).depth(), "the fill's depth, not the first step's 16");
    }

    @Test
    void theFullDepthIsTheFirstStepsWhereItIsOffOrShallower()
    {
        assertEquals(16, gate.fullDepth(), "off");
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW_FULL_DEPTH, 8);
        assertEquals(16, gate.fullDepth(), "shallower than the first step");
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW_FULL_DEPTH, 48);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            // The far world not loaded to ask: as far as it is set.
            assertEquals(48, gate.fullDepth());
        }
    }

    /**
     * A gate's view may hold four times what a mirror's may before its depth is cut.
     *
     * <p>Filled to 160 blocks, a gate's view of open ground passed a mirror's cap a hundred or so
     * blocks out and was cut there, leaving this world showing behind the far side.
     */
    @Test
    void aGatesViewMayHoldMoreThanAMirrorsBeforeItIsCut()
    {
        final Capture held = capture(32);
        final QuantumMirror stand = new QuantumMirror(NAME, BlockPlace.of(anchor), ARRIVAL);
        final WindowState gateWindow = new WindowState(gate, held);
        final WindowState mirrorWindow = new WindowState(new MirrorSource(stand, anchor, gate.shape(), gate.open(), 16), held);

        assertEquals(1_000_000, Windows.mostFixedFor(gateWindow), "a gate's");
        assertEquals(250_000, Windows.mostFixedFor(mirrorWindow), "a mirror's, as it was");
    }

    /** A capture of the far side as though taken two minutes ago, reaching {@code ahead} blocks past the arrival. */
    private static Capture oldCapture(final int ahead)
    {
        return oldCapture(ahead, 120);
    }

    /** A capture of the far side as though taken {@code seconds} ago, reaching {@code ahead} blocks past the arrival. */
    private static Capture oldCapture(final int ahead, final int seconds)
    {
        final BlockData air = mock(BlockData.class);
        when(air.getAsString()).thenReturn("minecraft:air");
        return new Capture.Builder("far", true, new Capture.Box(80, 60, 199, 41, 20, ahead + 2), air)
            .build(System.currentTimeMillis() - (seconds * 1000L));
    }

    /** Opens the gate with a capture installed, and counts the captures asked for, by depth. */
    private Map<Integer, Integer> askedAsItOpens(final Capture held)
    {
        final Map<Integer, Integer> asked = new HashMap<>();
        Captures.install(key(), held);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
            MockedStatic<Captures> captures = mockStatic(Captures.class, CALLS_REAL_METHODS))
        {
            captures.when(() -> Captures.requestGate(anyString(), anyString(), any(Place.class), anyInt(),
                anyInt(), anyInt())).thenAnswer(call ->
                {
                    asked.merge(call.getArgument(5), 1, Integer::sum);
                    return true;
                });
            assertTrue(GateSource.offer(gate, true), "drawn from the capture it has meanwhile");
        }
        return asked;
    }

    /**
     * A capture holding the fill is not retaken as the gate opens until it is ten minutes old.
     *
     * <p>Retaken once a minute old, a gate over open ground dialled every two minutes kept a core
     * sifting -- a minute and a half a fill over open sky -- for as long as it was used.
     */
    @Test
    void aFilledCaptureIsRetakenAsTheGateOpensOnlyOnceTenMinutesOld()
    {
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW_FULL_DEPTH, 48);

        assertEquals(Map.of(), askedAsItOpens(oldCapture(60, 300)), "five minutes old: drawn as it is");
        Captures.clear();
        assertEquals(Map.of(48, 1), askedAsItOpens(oldCapture(60, 660)), "eleven minutes old: retaken, at the depth drawn");
    }

    /** A capture of the first step alone is still retaken as the gate opens once a minute old: it costs seconds. */
    @Test
    void aFirstStepCaptureIsRetakenAsTheGateOpensOnceAMinuteOld()
    {
        assertEquals(Map.of(), askedAsItOpens(oldCapture(20, 30)), "half a minute old: drawn as it is");
        Captures.clear();
        assertEquals(Map.of(16, 1), askedAsItOpens(oldCapture(20, 120)), "two minutes old: retaken");
    }

    /**
     * An old capture is retaken as the gate opens at the depth it is drawn to, not the first step's.
     *
     * <p>Retaken at the first step, a view drawn to its full depth shrank back to it for as long as
     * the fill took -- the far part gone, this world showing past the near part -- and pulled a
     * fogged viewer's chunks in and out with it, every time the gate opened.
     */
    @Test
    void anOldCaptureIsRetakenAtTheDepthItIsDrawnTo()
    {
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW_FULL_DEPTH, 48);
        final String key = key();
        Captures.install(key, oldCapture(60, 660));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
            MockedStatic<Captures> captures = mockStatic(Captures.class, CALLS_REAL_METHODS))
        {
            captures.when(() -> Captures.requestGate(anyString(), anyString(), any(Place.class), anyInt(),
                anyInt(), anyInt())).thenReturn(true);

            assertTrue(GateSource.offer(gate, true), "drawn from the old one meanwhile");
            captures.verify(() -> Captures.requestGate(eq(key), anyString(), any(Place.class), anyInt(),
                anyInt(), eq(48)), times(1));
            captures.verify(() -> Captures.requestGate(eq(key), anyString(), any(Place.class), anyInt(),
                anyInt(), eq(16)), never());
        }
    }

    @Test
    void anOldCaptureIsNotRetakenUnlessTheGateHasJustOpened()
    {
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW_FULL_DEPTH, 48);
        Captures.install(key(), oldCapture(60));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
            MockedStatic<Captures> captures = mockStatic(Captures.class, CALLS_REAL_METHODS))
        {
            assertTrue(GateSource.offer(gate, false));
            captures.verify(() -> Captures.requestGate(anyString(), anyString(), any(Place.class), anyInt(),
                anyInt(), anyInt()), never());
        }
    }

    /**
     * How far a gate's capture reaches is measured the way a traveller through it faces, whichever that is.
     *
     * <p>The debug line that says how far a gate sees reads this; a sign or an axis wrong for one
     * facing would print a negative or a one for every gate facing that way.
     */
    @Test
    void howFarACaptureReachesIsMeasuredTheWayATravellerFaces()
    {
        final BlockData air = mock(BlockData.class);
        when(air.getAsString()).thenReturn("minecraft:air");
        // Yaw 0 south, 90 west, 180 north, 270 east; each box reaches 40 past the arrival at (100, 70, 200) that way.
        final Object[][] facings = {
            { 0.0f, new Capture.Box(80, 60, 199, 41, 20, 42) },
            { 90.0f, new Capture.Box(60, 60, 180, 42, 20, 41) },
            { 180.0f, new Capture.Box(80, 60, 160, 41, 20, 42) },
            { 270.0f, new Capture.Box(99, 60, 180, 42, 20, 41) },
        };
        for (final Object[] facing : facings)
        {
            final Place arrival = new Place("far", 100.5, 70.0, 200.5, (float) facing[0], 0.0f);
            final WindowShape shape = WindowShape.through(new Spot(10, 64, 20), new Spot(0, 0, -1), arrival, 1, 2);
            final Capture held = new Capture.Builder("far", true, (Capture.Box) facing[1], air).build();
            final WindowState window = new WindowState(new GateSource(NAME, anchor, shape, List.of(), arrival, "Chulak", 16),
                held);

            assertEquals(40, Windows.reachAhead(window), "facing yaw " + facing[0]);
        }
    }

    /**
     * A small gate draws from, and asks for, the far gate's one capture, seen through the largest opening.
     *
     * <p>Captures were kept per opening size, so a gate dialled by a Standard and a Grand gate was
     * captured twice. Every smaller opening sees a part of what the largest does, so one serves all.
     */
    @Test
    void aSmallGateAsksForTheFarGatesOneCapture()
    {
        final WindowShape small = WindowShape.through(new Spot(10, 64, 20), new Spot(0, 0, -1), ARRIVAL, 1, 2);
        final GateSource standard = new GateSource(NAME, anchor, small, List.of(new Spot(10, 64, 20), new Spot(10, 65, 20)),
            ARRIVAL, "Chulak", 16);
        final String key = key();
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
            MockedStatic<Captures> captures = mockStatic(Captures.class, CALLS_REAL_METHODS))
        {
            captures.when(() -> Captures.requestGate(anyString(), anyString(), any(Place.class), anyInt(),
                anyInt(), anyInt())).thenReturn(true);

            assertFalse(GateSource.offer(standard, true));
            captures.verify(() -> Captures.requestGate(eq(key), eq("Chulak"), any(Place.class), eq(18), eq(18),
                eq(16)), times(1));
        }
    }

    /** A window of a whole opening of the given size, as a gate's: every cell of it open. */
    private WindowState windowOf(final int wide, final int tall)
    {
        final WindowShape shape = WindowShape.through(new Spot(10, 64, 20), new Spot(0, 0, -1), ARRIVAL, wide, tall);
        final List<Spot> open = new ArrayList<>();
        shape.forEachOpening((x, y, z) -> open.add(new Spot(x, y, z)));
        return new WindowState(new GateSource(NAME, anchor, shape, open, ARRIVAL, "Chulak", 16), capture(32));
    }

    /**
     * Whether a viewer can see into a big gate tries at most {@link Windows#MOST_SIGHT_LINES} lines of sight.
     *
     * <p>It tried every cell of the opening, on the main thread, each redraw: for somebody behind a
     * wall in front of a Grand gate, 274 blocked lines, some 80 ms a redraw against a Large gate's 14.
     * A small opening is still tried cell by cell, so a mirror is judged exactly as it was.
     */
    @Test
    void aBigOpeningIsLookedIntoAlongAtMostSoManyLinesOfSight()
    {
        final Location eye = new Location(world, 19.0, 66.0, 36.0);
        final AtomicInteger lines = new AtomicInteger();
        try (MockedStatic<WindowSight> sight = mockStatic(WindowSight.class))
        {
            sight.when(() -> WindowSight.clearLine(any(), any(), any(), any(), anyLong()))
                .thenAnswer(call -> (lines.incrementAndGet() < 0));

            assertFalse(Windows.canSee(world, eye, windowOf(18, 17), 0L), "every line blocked");
            final int grand = lines.getAndSet(0);
            assertFalse(Windows.canSee(world, eye, windowOf(8, 8), 0L));
            final int large = lines.get();

            assertTrue(grand <= Windows.MOST_SIGHT_LINES, grand + " lines into 306 cells, not every one");
            assertTrue(grand >= (Windows.MOST_SIGHT_LINES / 2), grand + " lines, spread over the opening");
            assertEquals(64, large, "an opening of 64 cells is tried at every one, as before");
        }
    }
}
