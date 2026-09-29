package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
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
}
