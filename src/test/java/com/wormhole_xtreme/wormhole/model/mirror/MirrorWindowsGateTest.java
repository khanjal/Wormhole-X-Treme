package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorWindow.Spot;

/**
 * An open gate offered to the mirror sweep as a window (#516): when it is one, and where its capture comes from.
 *
 * <p>A gate is drawn from a capture kept for the gate it shows and seen through its own opening.
 * Served a mirror's capture of the same place, a five-by-five gate drew a view cut to a three-by-two
 * hole, with the real world showing round its edges; a gate that was a window one sweep had to stay
 * one, and go when released, or a closed gate went on showing where it used to go.
 *
 * <p>Its own class rather than more of {@code MirrorWindowsTest}, which already needs gigabytes.
 */
class MirrorWindowsGateTest
{
    private static final String NAME = "gate:Abydos";

    /** Where travellers through the gate land, in front of Chulak: facing south. */
    private static final MirrorPoint ARRIVAL = new MirrorPoint("far", 100.5, 70.0, 200.5, 0.0f, 0.0f);

    @TempDir
    File dataFolder;

    private World world;
    private Block anchor;
    private GateWindow gate;

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
        MirrorWindows.clear();

        world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(world.getPlayers()).thenReturn(List.of());
        anchor = mock(Block.class);
        when(anchor.getWorld()).thenReturn(world);
        when(anchor.getX()).thenReturn(10);
        when(anchor.getY()).thenReturn(64);
        when(anchor.getZ()).thenReturn(20);
        // A Standard gate's five by five, facing south: looked into northwards.
        final MirrorWindow shape = MirrorWindow.through(new Spot(10, 64, 20), new Spot(0, 0, -1), ARRIVAL, 5, 5);
        final List<Spot> open = new ArrayList<>();
        shape.forEachOpening((x, y, z) -> open.add(new Spot(x, y, z)));
        gate = new GateWindow(NAME, anchor, shape, open, ARRIVAL, "Chulak", 16);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        MirrorWindows.clear();
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    /** A capture of the far side as though it had been taken, reaching {@code ahead} blocks past the arrival. */
    private static MirrorCapture capture(final int ahead)
    {
        final BlockData air = mock(BlockData.class);
        when(air.getAsString()).thenReturn("minecraft:air");
        return new MirrorCapture.Builder("far", true, new MirrorCapture.Box(80, 60, 199, 41, 20, ahead + 2), air).build();
    }

    private static String key()
    {
        return MirrorCaptures.gateKey("Chulak", 5, 5);
    }

    @Test
    void aGateWithNoCaptureYetIsNotAWindow()
    {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            // The far world is not loaded, so the capture cannot even be started.
            bukkit.when(() -> Bukkit.getWorld(anyString())).thenReturn(null);

            assertFalse(MirrorWindows.offerGate(gate, true), "nothing to draw from, so it keeps its horizon");
            assertFalse(MirrorWindows.holdsWindow(NAME));
            bukkit.verify(() -> Bukkit.getWorld("far"));
        }
    }

    @Test
    void aGateIsAWindowOnceTheCaptureOfItsFarGateIsIn()
    {
        MirrorCaptures.install(key(), capture(32));

        assertTrue(MirrorWindows.offerGate(gate, true), "its capture is in");
        assertTrue(MirrorWindows.holdsWindow(NAME), "and the sweep holds it");
    }

    @Test
    void aMirrorsCaptureOfTheSamePlaceIsNotAGates()
    {
        MirrorCaptures.install(ARRIVAL, capture(32));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld(anyString())).thenReturn(null);

            assertFalse(MirrorWindows.offerGate(gate, true),
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
        MirrorCaptures.install(key(), capture(4));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld(anyString())).thenReturn(null);

            assertTrue(MirrorWindows.offerGate(gate, false), "drawn from what there is");
            bukkit.verify(() -> Bukkit.getWorld("far"));
        }
    }

    @Test
    void aFreshCaptureDeepEnoughIsNotTakenAgain()
    {
        MirrorCaptures.install(key(), capture(32));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            assertTrue(MirrorWindows.offerGate(gate, true));
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

            MirrorWindows.prepareGate(gate);

            bukkit.verify(() -> Bukkit.getWorld("far"));
        }
    }

    @Test
    void aGateStaysAWindowAcrossSweepsUntilItIsReleased()
    {
        MirrorCaptures.install(key(), capture(32));
        assertTrue(MirrorWindows.offerGate(gate, true));
        MirrorWindows.finish();

        assertTrue(MirrorWindows.offerGate(gate, false), "offered again the next sweep");
        MirrorWindows.finish();
        assertTrue(MirrorWindows.holdsWindow(NAME), "a window between sweeps");

        MirrorWindows.release(NAME);

        assertFalse(MirrorWindows.holdsWindow(NAME), "released by name as its gate closes");
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
        // Outside the verify: a call to MirrorCaptures inside it would be what is verified.
        final String key = key();
        MirrorCaptures.install(key, capture(20));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
            MockedStatic<MirrorCaptures> captures = mockStatic(MirrorCaptures.class, CALLS_REAL_METHODS))
        {
            captures.when(() -> MirrorCaptures.requestGate(anyString(), anyString(), any(MirrorPoint.class), anyInt(),
                anyInt(), anyInt())).thenReturn(true);

            assertTrue(MirrorWindows.offerGate(gate, false), "the first step is drawn meanwhile");
            captures.verify(() -> MirrorCaptures.requestGate(eq(key), eq("Chulak"), any(MirrorPoint.class), eq(5),
                eq(5), eq(48)), times(1));
        }
    }

    @Test
    void aCaptureThatReachesTheFullDepthIsLeftAlone()
    {
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW_FULL_DEPTH, 48);
        MirrorCaptures.install(key(), capture(60));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
            MockedStatic<MirrorCaptures> captures = mockStatic(MirrorCaptures.class, CALLS_REAL_METHODS))
        {
            assertTrue(MirrorWindows.offerGate(gate, false));
            captures.verify(() -> MirrorCaptures.requestGate(anyString(), anyString(), any(MirrorPoint.class), anyInt(),
                anyInt(), anyInt()), never());
        }
    }

    @Test
    void theFullDepthIsTheFirstStepsWhereItIsOffOrShallower()
    {
        assertEquals(16, MirrorWindows.fullDepthOf(gate), "off");
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW_FULL_DEPTH, 8);
        assertEquals(16, MirrorWindows.fullDepthOf(gate), "shallower than the first step");
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW_FULL_DEPTH, 48);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            // The far world not loaded to ask: as far as it is set.
            assertEquals(48, MirrorWindows.fullDepthOf(gate));
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
        final MirrorCapture held = capture(32);
        final QuantumMirror stand = new QuantumMirror(NAME, MirrorBlock.of(anchor), ARRIVAL);
        final MirrorWindowState gateWindow = new MirrorWindowState(stand, gate.shape(), anchor, gate.open(), held, true, 16);
        final MirrorWindowState mirrorWindow = new MirrorWindowState(stand, gate.shape(), anchor, gate.open(), held, false, 16);

        assertEquals(1_000_000, MirrorWindows.mostFixedFor(gateWindow), "a gate's");
        assertEquals(250_000, MirrorWindows.mostFixedFor(mirrorWindow), "a mirror's, as it was");
    }

    /** A capture of the far side as though taken two minutes ago, reaching {@code ahead} blocks past the arrival. */
    private static MirrorCapture oldCapture(final int ahead)
    {
        final BlockData air = mock(BlockData.class);
        when(air.getAsString()).thenReturn("minecraft:air");
        return new MirrorCapture.Builder("far", true, new MirrorCapture.Box(80, 60, 199, 41, 20, ahead + 2), air)
            .build(System.currentTimeMillis() - 120_000L);
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
        MirrorCaptures.install(key, oldCapture(60));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
            MockedStatic<MirrorCaptures> captures = mockStatic(MirrorCaptures.class, CALLS_REAL_METHODS))
        {
            captures.when(() -> MirrorCaptures.requestGate(anyString(), anyString(), any(MirrorPoint.class), anyInt(),
                anyInt(), anyInt())).thenReturn(true);

            assertTrue(MirrorWindows.offerGate(gate, true), "drawn from the old one meanwhile");
            captures.verify(() -> MirrorCaptures.requestGate(eq(key), anyString(), any(MirrorPoint.class), anyInt(),
                anyInt(), eq(48)), times(1));
            captures.verify(() -> MirrorCaptures.requestGate(eq(key), anyString(), any(MirrorPoint.class), anyInt(),
                anyInt(), eq(16)), never());
        }
    }

    @Test
    void anOldCaptureIsNotRetakenUnlessTheGateHasJustOpened()
    {
        ConfigTestSupport.set(ConfigKeys.GATE_VIEW_FULL_DEPTH, 48);
        MirrorCaptures.install(key(), oldCapture(60));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
            MockedStatic<MirrorCaptures> captures = mockStatic(MirrorCaptures.class, CALLS_REAL_METHODS))
        {
            assertTrue(MirrorWindows.offerGate(gate, false));
            captures.verify(() -> MirrorCaptures.requestGate(anyString(), anyString(), any(MirrorPoint.class), anyInt(),
                anyInt(), anyInt()), never());
        }
    }

    /** The gate's window as the drawing holds it, with a throat of a given depth. */
    private MirrorWindowState held(final int tunnel)
    {
        final QuantumMirror stand = new QuantumMirror(NAME, MirrorBlock.of(anchor), ARRIVAL);
        final MirrorWindowState window = new MirrorWindowState(stand, gate.shape(), anchor, gate.open(), capture(32), true, 16);
        window.tunnel = tunnel;
        return window;
    }

    /** An eye a block in front of the middle of the five-by-five opening. */
    private static final org.bukkit.Location EYE = new org.bukkit.Location(null, 12.5, 66.5, 21.5);

    /**
     * A block seen steeply through the ring, but not down the throat behind it, is not drawn.
     *
     * <p>A gate is one block deep, so from in front of it a line of sight reached far blocks well off
     * to one side, whose outlines hung past the ring. The throat is two blocks more: a block past it
     * is drawn only if it also lands inside the opening at the throat's far end.
     */
    @Test
    void aBlockSeenSteeplyThroughTheRingButNotDownTheThroatIsNotDrawn()
    {
        final MirrorWindowState window = held(2);
        // Five blocks behind the gate and 23 to the side: through the front of the opening, near its edge.
        assertTrue(gate.shape().projected(EYE.getX(), EYE.getY(), EYE.getZ(), 35, 66, 15)[1] < 15.0,
            "seen through the front of the opening");

        assertFalse(MirrorWindows.throughTunnel(window, EYE, 35, 66, 15, 0.0), "but not down the throat");
        assertTrue(MirrorWindows.throughTunnel(window, EYE, 12, 66, 10, 0.0), "straight down it is");
        assertTrue(MirrorWindows.throughTunnel(held(0), EYE, 35, 66, 15, 0.0), "and with no throat, the front decides");
    }

    @Test
    void aBlockInsideTheThroatIsJudgedByTheFrontAlone()
    {
        // Two behind the gate is inside a throat two deep.
        assertTrue(MirrorWindows.throughTunnel(held(2), EYE, 35, 66, 18, 0.0));
    }

    /**
     * The throat's walls are the frame round the opening carried back behind it, in a checkerboard of the two materials.
     *
     * <p>Drawn to the viewer, so nothing in the world changes; without them a line stopped by the
     * throat would show this world through the gap it left in the view.
     */
    @Test
    void theThroatsWallsAreTheFrameCarriedBackBehindTheOpening()
    {
        final MirrorWindowState window = held(2);
        window.frame = List.of(new Spot(9, 64, 20), new Spot(15, 65, 20));
        window.wallMaterials = new org.bukkit.Material[] { org.bukkit.Material.BLUE_ICE, org.bukkit.Material.PACKED_ICE };
        final BlockData blue = mock(BlockData.class);
        final BlockData packed = mock(BlockData.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.createBlockData(org.bukkit.Material.BLUE_ICE)).thenReturn(blue);
            bukkit.when(() -> Bukkit.createBlockData(org.bukkit.Material.PACKED_ICE)).thenReturn(packed);

            final java.util.Map<Long, BlockData> walls = MirrorWindows.tunnelWalls(window);

            assertEquals(4, walls.size(), "two frame cells, two deep");
            // Looked into northwards, so behind the gate is towards smaller z.
            assertEquals(((9 + 64 + 19) % 2 == 0) ? blue : packed, walls.get(MirrorWindows.key(9, 64, 19)));
            assertEquals(((9 + 64 + 18) % 2 == 0) ? blue : packed, walls.get(MirrorWindows.key(9, 64, 18)));
            assertTrue(walls.containsKey(MirrorWindows.key(15, 65, 19)) && walls.containsKey(MirrorWindows.key(15, 65, 18)));
            assertFalse(walls.containsKey(MirrorWindows.key(9, 64, 17)), "no deeper than the throat");
        }
        assertTrue(MirrorWindows.tunnelWalls(held(0)).isEmpty(), "no throat, no walls");
    }
}
