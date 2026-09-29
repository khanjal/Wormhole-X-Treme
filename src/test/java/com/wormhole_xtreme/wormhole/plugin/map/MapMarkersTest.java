package com.wormhole_xtreme.wormhole.plugin.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Server;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.model.beam.BeamDestination;
import com.wormhole_xtreme.wormhole.model.beam.BeamManager;
import com.wormhole_xtreme.wormhole.model.beam.BeamPoint;
import com.wormhole_xtreme.wormhole.plugin.map.MapSnapshot.BeamMark;

/**
 * When the web map is redrawn, and with what (#236).
 *
 * <p>The main thread only looks and compares; drawing happens on a background task. That split
 * is only worth having if it holds: an unchanged server must not queue a draw every few
 * seconds, a burst of changes must queue one draw and not one each, and that draw must show
 * the newest picture rather than whichever was current when it was queued. A draw that landed
 * an older picture would leave a closed wormhole's line on the map until something else moved.
 *
 * <p>The background task is held in a list here rather than run, so each test decides when
 * it runs and can count how many were queued.
 */
class MapMarkersTest
{
    private WormholeXTreme logger;
    private BukkitScheduler scheduler;
    private BukkitTask ticker;
    private Plugin plugin;
    private PluginManager pluginManager;
    private final Recorder map = new Recorder();
    private final List<Runnable> background = new ArrayList<>();
    private MapSnapshot showing = picture("start");

    /** A map that remembers what it was told. */
    private static class Recorder implements MapProvider
    {
        final List<MapSnapshot> applied = new ArrayList<>();
        int clears = 0;

        @Override
        public String name()
        {
            return "Test map";
        }

        @Override
        public void apply(final MapSnapshot snapshot)
        {
            applied.add(snapshot);
        }

        @Override
        public void clear()
        {
            clears++;
        }
    }

    /** A picture that differs from any other of a different name. */
    private static MapSnapshot picture(final String beam)
    {
        return new MapSnapshot(Map.of(), Map.of(), Map.of(), Map.of(),
            Map.of(beam, new BeamMark(beam, "world", beam, 0, 64, 0)), Map.of());
    }

    @BeforeEach
    void setUp() throws Exception
    {
        logger = PluginTestSupport.install();
        scheduler = mock(BukkitScheduler.class);
        ticker = mock(BukkitTask.class);
        when(scheduler.runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong())).thenReturn(ticker);
        PluginTestSupport.scheduler(scheduler);
        plugin = mock(Plugin.class);
        final Server server = mock(Server.class);
        pluginManager = mock(PluginManager.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);
        MapMarkers.setProviderForTest(map);
        MapMarkers.setBackgroundForTest(background::add);
        MapMarkers.setSourceForTest(() -> showing);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        MapMarkers.disable();
        MapMarkers.setProviderForTest(null);
        MapMarkers.setBackgroundForTest(null);
        MapMarkers.setSourceForTest(null);
        MapMarkers.setDynmapClassForTest(null);
        BeamManager.clear();
        ConfigTestSupport.clear();
        PluginTestSupport.scheduler(null);
        PluginTestSupport.remove();
    }

    private void enable()
    {
        ConfigTestSupport.set(ConfigKeys.DYNMAP_ENABLED, true);
        MapMarkers.enable(plugin);
        assertTrue(MapMarkers.isRunning(), "the test needs the map running to say anything");
    }

    private void runBackground()
    {
        final List<Runnable> queued = new ArrayList<>(background);
        background.clear();
        queued.forEach(Runnable::run);
    }

    @Test
    void nothingIsRegisteredWhileTheSettingIsOff()
    {
        MapMarkers.enable(plugin);

        assertFalse(MapMarkers.isRunning());
        verifyNoInteractions(scheduler, pluginManager);
        MapMarkers.tick();
        assertTrue(background.isEmpty(), "a server that did not ask for a map pays nothing for one");
    }

    @Test
    void aServerWithoutDynmapIsToldOnceAndOtherwiseLeftAlone()
    {
        MapMarkers.setProviderForTest(null);
        MapMarkers.setDynmapClassForTest("org.dynmap.NotInstalled");
        ConfigTestSupport.set(ConfigKeys.DYNMAP_ENABLED, true);

        MapMarkers.enable(plugin);

        assertFalse(MapMarkers.isRunning());
        verifyNoInteractions(scheduler, pluginManager);
        verify(logger).prettyLog(eq(Level.WARNING), contains("Dynmap was not found"));
    }

    @Test
    void enablingStartsTheLookAndListensForGates()
    {
        enable();

        verify(scheduler).runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), eq(MapMarkers.PERIOD_TICKS));
        verify(pluginManager).registerEvents(any(Listener.class), eq(plugin));
    }

    @Test
    void aChangeIsDrawnOnceWithTheNewPicture()
    {
        enable();

        MapMarkers.tick();

        assertEquals(1, background.size(), "one change, one draw queued");
        assertTrue(map.applied.isEmpty(), "and nothing drawn on the main thread");
        runBackground();
        assertEquals(List.of(showing), map.applied);
    }

    @Test
    void anUnchangedServerIsNotRedrawn()
    {
        enable();
        MapMarkers.tick();
        runBackground();

        MapMarkers.tick();
        MapMarkers.tick();

        assertTrue(background.isEmpty(), "nothing changed, so nothing should be queued");
        assertEquals(1, map.applied.size());
    }

    @Test
    void twoChangesBeforeTheDrawRunsDrawOnlyTheLatest()
    {
        enable();
        MapMarkers.tick();
        showing = picture("later");
        MapMarkers.tick();

        assertEquals(1, background.size(), "the second change should ride on the draw already queued");
        runBackground();

        assertEquals(1, map.applied.size());
        assertSame(showing, map.applied.get(0),
            "the draw shows the newest picture, not the one current when it was queued");
    }

    @Test
    void aChangeAfterTheDrawStartsQueuesAnotherDraw()
    {
        enable();
        MapMarkers.tick();
        runBackground();

        showing = picture("later");
        MapMarkers.tick();

        assertEquals(1, background.size(), "a change after the last draw must not be lost");
        runBackground();
        assertEquals(2, map.applied.size());
        assertSame(showing, map.applied.get(1));
    }

    @Test
    void aMapThatComesUpIsGivenTheLatestPictureAgain()
    {
        // Dynmap restarting forgets every marker; the picture has not changed, so without
        // this nothing would redraw it until something on the server did.
        enable();
        MapMarkers.tick();
        runBackground();

        MapMarkers.requestDraw();

        assertEquals(1, background.size());
        runBackground();
        assertEquals(2, map.applied.size());
    }

    @Test
    void disableCancelsTheLookAndClearsTheMap()
    {
        enable();
        MapMarkers.tick();

        MapMarkers.disable();

        verify(ticker).cancel();
        assertEquals(1, map.clears, "the plugin's marks should come off the map as it disables");
        assertFalse(MapMarkers.isRunning());
        runBackground();
        assertTrue(map.applied.isEmpty(), "a draw queued before disabling must not redraw after the clear");
    }

    @Test
    void aDrawThatThrowsIsReportedAndTheNextOneStillRuns()
    {
        final RuntimeException broken = new IllegalStateException("map plugin broke");
        final List<MapSnapshot> tried = new ArrayList<>();
        MapMarkers.setProviderForTest(new Recorder()
        {
            @Override
            public void apply(final MapSnapshot snapshot)
            {
                tried.add(snapshot);
                throw broken;
            }
        });
        enable();
        MapMarkers.tick();
        runBackground();
        showing = picture("later");
        MapMarkers.tick();
        runBackground();

        assertEquals(2, tried.size(), "one failed draw must not stop the map being kept up to date");
        verify(logger).prettyLog(eq(Level.WARNING), contains("Test map"), eq(broken));
    }

    @Test
    void theRealScanShowsPublicDestinationsAndNeverPrivatePlaces()
    {
        // Dynmap shows every marker to every viewer, so a private place on the map would be
        // anybody's to find.
        BeamManager.setPublicDestination(new BeamDestination("Market",
            new BeamPoint("world", 1, 64, 1, 0f, 0f), null));
        BeamManager.setPlace(UUID.randomUUID(), new BeamDestination("MyBase",
            new BeamPoint("world", 900, 12, -900, 0f, 0f), null));

        final MapSnapshot snapshot = MapMarkers.scan();

        assertNotNull(snapshot.beams().get("market"), "the public destination is shown");
        assertNull(snapshot.beams().get("mybase"), "the private place is not");
        assertEquals(1, snapshot.beams().size());
    }
}
