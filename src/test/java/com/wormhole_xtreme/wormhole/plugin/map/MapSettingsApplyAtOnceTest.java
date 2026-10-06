package com.wormhole_xtreme.wormhole.plugin.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Server;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.dynmap.DynmapCommonAPI;
import org.dynmap.DynmapCommonAPIListener;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.plugin.map.MapSnapshot.GateMark;
import com.wormhole_xtreme.wormhole.plugin.map.MapSnapshot.RingMark;

/**
 * The map settings, changed with {@code /wormhole config}, take effect at once (#236).
 *
 * <p>The guide says nothing waits for a restart. The map read its settings once at enable, so
 * turning it on in-game did nothing until the next restart, and a layer switched off stayed on
 * the map. Each change now takes the map down and puts it back up as the config now says.
 */
class MapSettingsApplyAtOnceTest
{
    @TempDir
    File dataFolder;

    private WormholeXTreme plugin;
    private BukkitScheduler scheduler;
    private BukkitTask ticker;
    private PluginManager pluginManager;
    private final List<Runnable> background = new ArrayList<>();

    /** One gate and one ring end, so both layers have something in them. */
    private final MapSnapshot showing = new MapSnapshot(
        Map.of("abydos", new GateMark("abydos", "world", "Abydos", null, null, false, 0, 64, 0, null)),
        Map.of(),
        Map.of("r1:a", new RingMark("r1:a", "world", "Mine", "Mine (r1)", null, 5.5, 60, 5.5)),
        Map.of(), Map.of(), Map.of());

    @BeforeEach
    void setUp() throws Exception
    {
        plugin = PluginTestSupport.install();
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        final Server server = mock(Server.class);
        pluginManager = mock(PluginManager.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);
        scheduler = mock(BukkitScheduler.class);
        ticker = mock(BukkitTask.class);
        when(scheduler.runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong())).thenReturn(ticker);
        PluginTestSupport.scheduler(scheduler);
        MapMarkers.setBackgroundForTest(background::add);
        MapMarkers.setSourceForTest(() -> showing);
        ConfigTestSupport.set(ConfigKeys.DYNMAP_ENABLED, false);
        ConfigTestSupport.set(ConfigKeys.MAP_SHOW_RINGS, true);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        MapMarkers.disable();
        DynmapCommonAPIListener.apiTerminated();
        MapMarkers.setProviderForTest(null);
        MapMarkers.setBackgroundForTest(null);
        MapMarkers.setSourceForTest(null);
        ConfigTestSupport.clear();
        PluginTestSupport.scheduler(null);
        PluginTestSupport.remove();
    }

    /** A map that counts how often it is cleared. */
    private static final class Recorder implements MapProvider
    {
        int clears = 0;

        @Override
        public String name()
        {
            return "Test map";
        }

        @Override
        public boolean ready()
        {
            return true;
        }

        @Override
        public void apply(final MapSnapshot snapshot)
        {
            // Drawing is not what these tests are about.
        }

        @Override
        public void clear()
        {
            clears++;
        }
    }

    @Test
    void turningTheMapOnInGameStartsItWithoutARestart()
    {
        MapMarkers.setProviderForTest(new Recorder());

        final String said = ConfigManager.applySetting("dynmap-enabled", "true");

        assertTrue(MapMarkers.isRunning(), "running now, not at the next restart: " + said);
        verify(pluginManager).registerEvents(any(Listener.class), eq(plugin));
        assertFalse(said.contains("could not be applied"), said);
    }

    @Test
    void turningBlueMapOnInGameStartsTheMapWithoutARestart()
    {
        MapMarkers.setProviderForTest(new Recorder());
        ConfigTestSupport.set(ConfigKeys.BLUEMAP_ENABLED, false);

        final String said = ConfigManager.applySetting("bluemap-enabled", "true");

        assertTrue(MapMarkers.isRunning(), "running now, not at the next restart: " + said);
        ConfigManager.applySetting("bluemap-enabled", "false");
        assertFalse(MapMarkers.isRunning(), "and stopped again when it is turned off");
    }

    @Test
    void turningSquaremapOnInGameStartsTheMapWithoutARestart()
    {
        MapMarkers.setProviderForTest(new Recorder());
        ConfigTestSupport.set(ConfigKeys.SQUAREMAP_ENABLED, false);

        final String said = ConfigManager.applySetting("squaremap-enabled", "true");

        assertTrue(MapMarkers.isRunning(), "running now, not at the next restart: " + said);
        ConfigManager.applySetting("squaremap-enabled", "false");
        assertFalse(MapMarkers.isRunning(), "and stopped again when it is turned off");
    }

    @Test
    void turningPl3xMapOnInGameStartsTheMapWithoutARestart()
    {
        MapMarkers.setProviderForTest(new Recorder());
        ConfigTestSupport.set(ConfigKeys.PL3XMAP_ENABLED, false);

        final String said = ConfigManager.applySetting("pl3xmap-enabled", "true");

        assertTrue(MapMarkers.isRunning(), "running now, not at the next restart: " + said);
        ConfigManager.applySetting("pl3xmap-enabled", "false");
        assertFalse(MapMarkers.isRunning(), "and stopped again when it is turned off");
    }

    @Test
    void turningTheMapOffInGameStopsItAndTakesItsMarksOff()
    {
        final Recorder map = new Recorder();
        MapMarkers.setProviderForTest(map);
        ConfigManager.applySetting("dynmap-enabled", "true");

        ConfigManager.applySetting("dynmap-enabled", "false");

        assertFalse(MapMarkers.isRunning());
        assertEquals(1, map.clears, "the map's marks come off as it stops");
        verify(ticker).cancel();
    }

    /** The marker sets a mocked Dynmap made, by id, in the order it made them. */
    private final Map<String, List<MarkerSet>> made = new HashMap<>();

    private MarkerAPI dynmapMarkers()
    {
        final MarkerAPI markers = mock(MarkerAPI.class);
        when(markers.createMarkerSet(anyString(), anyString(), isNull(), anyBoolean())).thenAnswer(call ->
        {
            final MarkerSet set = mock(MarkerSet.class);
            made.computeIfAbsent(call.getArgument(0), k -> new ArrayList<>()).add(set);
            return set;
        });
        when(markers.getMarkerSet(anyString())).thenAnswer(call ->
        {
            final List<MarkerSet> sets = made.get(call.<String>getArgument(0));
            return (sets == null) ? null : sets.get(sets.size() - 1);
        });
        final DynmapCommonAPI dynmap = mock(DynmapCommonAPI.class);
        when(dynmap.markerAPIInitialized()).thenReturn(true);
        when(dynmap.getMarkerAPI()).thenReturn(markers);
        DynmapCommonAPIListener.apiInitialized(dynmap);
        return markers;
    }

    /** Runs the look Dynmap's arrival booked, and the draw it queued. */
    private void runBookedLookAndDraw()
    {
        final ArgumentCaptor<Runnable> look = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler, atLeastOnce()).runTask(eq(plugin), look.capture());
        look.getValue().run();
        final List<Runnable> queued = new ArrayList<>(background);
        background.clear();
        queued.forEach(Runnable::run);
    }

    private int madeCount(final String id)
    {
        return made.getOrDefault(id, List.of()).size();
    }

    @Test
    void aLayerSwitchedOffInGameLeavesTheMapAndOneSwitchedBackOnReturns()
    {
        final MarkerAPI markers = dynmapMarkers();
        ConfigManager.applySetting("dynmap-enabled", "true");
        runBookedLookAndDraw();
        assertEquals(1, madeCount(DynmapMapProvider.RINGS), "the rings layer is up to begin with");
        final MarkerSet firstRings = made.get(DynmapMapProvider.RINGS).get(0);

        ConfigManager.applySetting("map-show-rings", "false");
        runBookedLookAndDraw();

        verify(firstRings, atLeastOnce()).deleteMarkerSet();
        assertEquals(1, madeCount(DynmapMapProvider.RINGS), "and not made again while switched off");
        assertEquals(2, madeCount(DynmapMapProvider.GATES), "while the gates layer is put back up");
        verify(made.get(DynmapMapProvider.GATES).get(1)).createMarker(eq("abydos"), anyString(), anyBoolean(),
            anyString(), anyDouble(), anyDouble(), anyDouble(), any(), anyBoolean());

        ConfigManager.applySetting("map-show-rings", "true");
        runBookedLookAndDraw();

        assertEquals(2, madeCount(DynmapMapProvider.RINGS), "switched back on, the layer returns");
        verify(made.get(DynmapMapProvider.RINGS).get(1)).createMarker(eq("r1:a"), anyString(), anyBoolean(),
            anyString(), anyDouble(), anyDouble(), anyDouble(), any(), anyBoolean());
        verify(markers, never()).createMarkerSet(eq(DynmapMapProvider.RINGS), anyString(), any(), eq(true));
        verify(scheduler, times(3)).runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong());
    }
}
