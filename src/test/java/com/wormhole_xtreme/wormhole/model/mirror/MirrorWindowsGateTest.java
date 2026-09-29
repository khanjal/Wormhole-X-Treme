package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
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
 * <p>A gate is drawn from a capture seen through its own opening. Served a mirror's capture of the
 * same place, a five-by-five gate drew a view cut to a three-by-two hole, with the real world
 * showing round its edges; and a gate that was a window one sweep had to stay one, and go when
 * released, or a closed gate went on showing where it used to go.
 *
 * <p>Its own class rather than more of {@code MirrorWindowsTest}, which already needs gigabytes.
 */
class MirrorWindowsGateTest
{
    private static final String NAME = "gate:Abydos";

    /** Where travellers through the gate land: facing south. */
    private static final MirrorPoint ARRIVAL = new MirrorPoint("far", 100.5, 70.0, 200.5, 0.0f, 0.0f);

    @TempDir
    File dataFolder;

    private World world;
    private Block anchor;
    private MirrorWindow shape;
    private final List<Spot> open = new ArrayList<>();

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
        shape = MirrorWindow.through(new Spot(10, 64, 20), new Spot(0, 0, -1), ARRIVAL, 5, 5);
        shape.forEachOpening((x, y, z) -> open.add(new Spot(x, y, z)));
    }

    @AfterEach
    void tearDown() throws Exception
    {
        MirrorWindows.clear();
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    /** An empty capture of the far side, as though it had been taken. */
    private static MirrorCapture capture()
    {
        final BlockData air = mock(BlockData.class);
        when(air.getAsString()).thenReturn("minecraft:air");
        return new MirrorCapture.Builder("far", true, new MirrorCapture.Box(90, 60, 190, 20, 20, 20), air).build();
    }

    @Test
    void aGateWithNoCaptureYetIsNotAWindow()
    {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            // The far world is not loaded, so the capture cannot even be started.
            bukkit.when(() -> Bukkit.getWorld(anyString())).thenReturn(null);

            assertFalse(MirrorWindows.offerGate(NAME, anchor, shape, open, ARRIVAL, true),
                "nothing to draw from, so it keeps its horizon");
            assertFalse(MirrorWindows.holdsWindow(NAME));
            bukkit.verify(() -> Bukkit.getWorld("far"));
        }
    }

    @Test
    void aGateIsAWindowOnceItsOwnCaptureIsIn()
    {
        MirrorCaptures.install(ARRIVAL, 5, 5, capture());

        assertTrue(MirrorWindows.offerGate(NAME, anchor, shape, open, ARRIVAL, true), "its capture is in");
        assertTrue(MirrorWindows.holdsWindow(NAME), "and the sweep holds it");
    }

    @Test
    void aMirrorsCaptureOfTheSamePlaceIsNotAGates()
    {
        MirrorCaptures.install(ARRIVAL, capture());
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld(anyString())).thenReturn(null);

            assertFalse(MirrorWindows.offerGate(NAME, anchor, shape, open, ARRIVAL, true),
                "a capture seen through a mirror's three by two is not drawn through a five by five");
        }
    }

    @Test
    void aGateStaysAWindowAcrossSweepsUntilItIsReleased()
    {
        MirrorCaptures.install(ARRIVAL, 5, 5, capture());
        assertTrue(MirrorWindows.offerGate(NAME, anchor, shape, open, ARRIVAL, true));
        MirrorWindows.finish();

        assertTrue(MirrorWindows.offerGate(NAME, anchor, shape, open, ARRIVAL, false), "offered again the next sweep");
        MirrorWindows.finish();
        assertTrue(MirrorWindows.holdsWindow(NAME), "a window between sweeps");

        MirrorWindows.release(NAME);

        assertFalse(MirrorWindows.holdsWindow(NAME), "released by name as its gate closes");
    }
}
