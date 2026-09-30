package com.wormhole_xtreme.wormhole.plugin.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

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
import org.mockito.ArgumentCaptor;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.model.Stargate;
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
        boolean up = true;

        @Override
        public String name()
        {
            return "Test map";
        }

        @Override
        public boolean ready()
        {
            return up;
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

    /** Runs the one look the last {@code requestRefresh} booked on the next tick. */
    private void runBookedLook()
    {
        final ArgumentCaptor<Runnable> look = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler, atLeastOnce()).runTask(eq(plugin), look.capture());
        look.getValue().run();
    }

    @Test
    void aMapThatComesUpIsGivenTheLatestPictureAgain()
    {
        // Dynmap restarting forgets every marker; the picture has not changed, so without
        // this nothing would redraw it until something on the server did.
        enable();
        MapMarkers.tick();
        runBackground();

        MapMarkers.providerReady();
        runBookedLook();

        assertEquals(1, background.size(), "an unchanged picture is drawn again for a map that just came up");
        runBackground();
        assertEquals(2, map.applied.size());
    }

    @Test
    void nothingIsLookedAtOrDrawnWhileTheMapIsNotUp()
    {
        final AtomicInteger looks = new AtomicInteger();
        MapMarkers.setSourceForTest(() ->
        {
            looks.incrementAndGet();
            return showing;
        });
        map.up = false;
        enable();

        MapMarkers.tick();
        MapMarkers.tick();
        MapMarkers.tick();

        assertEquals(0, looks.get(), "no scan every five seconds for a map that is not there");
        assertTrue(background.isEmpty());

        map.up = true;
        MapMarkers.tick();
        assertEquals(1, looks.get(), "and looked at as soon as it is");
    }

    @Test
    void aDiallingGateIsNotWatchedWhileTheMapIsNotUp()
    {
        final List<Runnable> checks = holdFormingChecks();
        map.up = false;
        enable();
        final Stargate gate = mock(Stargate.class);
        when(gate.isGateActive()).thenReturn(true);

        MapMarkers.watchForming(gate);

        assertTrue(checks.isEmpty());
        map.up = true;
        MapMarkers.watchForming(gate);
        assertEquals(1, checks.size(), "watched as usual once the map is up");
    }

    /** A Dynmap that is up, with markers on, drawing into one mocked set. */
    private static DynmapCommonAPI dynmapUp(final MarkerAPI markers)
    {
        final DynmapCommonAPI dynmap = mock(DynmapCommonAPI.class);
        when(dynmap.markerAPIInitialized()).thenReturn(true);
        when(dynmap.getMarkerAPI()).thenReturn(markers);
        return dynmap;
    }

    private void enableRealDynmap(final boolean installedAndRunning)
    {
        MapMarkers.setProviderForTest(null);
        ConfigTestSupport.set(ConfigKeys.DYNMAP_ENABLED, true);
        final Plugin installed = mock(Plugin.class);
        when(installed.isEnabled()).thenReturn(installedAndRunning);
        when(pluginManager.getPlugin("dynmap")).thenReturn(installed);
        MapMarkers.enable(plugin);
    }

    @Test
    void dynmapInstalledButNotRunningIsSaidOnceAndNothingIsClaimedOrDrawn()
    {
        // As on a server whose Dynmap does not support its Minecraft version: Dynmap disables
        // itself, and the map must not claim to be showing anything.
        final AtomicInteger looks = new AtomicInteger();
        MapMarkers.setSourceForTest(() ->
        {
            looks.incrementAndGet();
            return showing;
        });
        try
        {
            enableRealDynmap(false);

            MapMarkers.tick();
            MapMarkers.tick();
            MapMarkers.tick();

            verify(logger, times(1)).prettyLog(eq(Level.WARNING), contains("not running"));
            verify(logger, never()).prettyLog(eq(Level.INFO), contains("Showing"));
            assertEquals(0, looks.get());
            assertTrue(background.isEmpty());
            MapMarkers.disable();
            assertFalse(MapMarkers.isRunning());
        }
        finally
        {
            DynmapCommonAPIListener.apiTerminated();
        }
    }

    @Test
    void dynmapStartingLaterIsAnnouncedOnceAndDrawnInFull()
    {
        final MarkerAPI markers = mock(MarkerAPI.class);
        final MarkerSet set = mock(MarkerSet.class);
        when(markers.createMarkerSet(anyString(), anyString(), isNull(), anyBoolean())).thenReturn(set);
        try
        {
            enableRealDynmap(false);
            MapMarkers.tick();

            DynmapCommonAPIListener.apiInitialized(dynmapUp(markers));
            runBookedLook();
            runBackground();

            verify(logger, times(1)).prettyLog(eq(Level.INFO), contains("Showing"));
            verify(set).createMarker(eq("start"), eq("start"), eq(false), eq("world"), anyDouble(), anyDouble(),
                anyDouble(), any(), eq(false));
        }
        finally
        {
            DynmapCommonAPIListener.apiTerminated();
        }
    }

    @Test
    void showingIsSaidWhenDynmapArrivesNotAtEnable()
    {
        final MarkerAPI markers = mock(MarkerAPI.class);
        try
        {
            enableRealDynmap(true);
            verify(logger, never()).prettyLog(eq(Level.INFO), contains("Showing"));
            verify(logger, never()).prettyLog(eq(Level.WARNING), contains("not running"));

            DynmapCommonAPIListener.apiInitialized(dynmapUp(markers));

            verify(logger, times(1)).prettyLog(eq(Level.INFO), contains("Showing"));
        }
        finally
        {
            DynmapCommonAPIListener.apiTerminated();
        }
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

    /** Holds each check a dialling gate books, so a test runs them one at a time. */
    private List<Runnable> holdFormingChecks()
    {
        final List<Runnable> checks = new ArrayList<>();
        when(scheduler.runTaskLater(any(Plugin.class), any(Runnable.class), anyLong())).thenAnswer(call ->
        {
            checks.add(call.getArgument(1));
            return null;
        });
        return checks;
    }

    private static void runNext(final List<Runnable> checks)
    {
        checks.remove(0).run();
    }

    @Test
    void aDiallingGateIsCheckedUntilItsWormholeFormsAndThenLookedAt()
    {
        // Nothing fires when the kawoosh settles, so without this the lit icon would wait for
        // the next periodic look, up to five seconds after the wormhole formed.
        enable();
        final List<Runnable> checks = holdFormingChecks();
        final Stargate gate = mock(Stargate.class);
        when(gate.isGateActive()).thenReturn(true);

        MapMarkers.watchForming(gate);
        assertEquals(1, checks.size());
        verify(scheduler).runTaskLater(eq(plugin), any(Runnable.class), eq(20L));
        runNext(checks);
        assertEquals(1, checks.size(), "still dialling, so it is checked again");
        verify(scheduler, never()).runTask(any(Plugin.class), any(Runnable.class));

        when(gate.isGatePortalOpen()).thenReturn(true);
        runNext(checks);

        verify(scheduler).runTask(eq(plugin), any(Runnable.class));
        assertTrue(checks.isEmpty(), "formed, so the checks stop");
    }

    @Test
    void aGateThatShutsWhileDiallingIsNoLongerChecked()
    {
        enable();
        final List<Runnable> checks = holdFormingChecks();
        final Stargate gate = mock(Stargate.class);
        when(gate.isGateActive()).thenReturn(true);
        MapMarkers.watchForming(gate);
        when(gate.isGateActive()).thenReturn(false);

        runNext(checks);

        assertTrue(checks.isEmpty());
        verify(scheduler, never()).runTask(any(Plugin.class), any(Runnable.class));
    }

    @Test
    void aGateThatNeverFormsIsLeftToThePeriodicLookInTheEnd()
    {
        enable();
        final List<Runnable> checks = holdFormingChecks();
        final Stargate gate = mock(Stargate.class);
        when(gate.isGateActive()).thenReturn(true);
        MapMarkers.watchForming(gate);

        int ran = 0;
        while (!checks.isEmpty() && (ran < 1000))
        {
            runNext(checks);
            ran++;
        }

        assertEquals(MapMarkers.FORMING_CHECKS, ran, "a bounded number of checks, not one a second forever");
    }

    /** A map whose draws fail while {@code failing} is set. */
    private static final class Flaky extends Recorder
    {
        boolean failing = true;

        @Override
        public void apply(final MapSnapshot snapshot)
        {
            super.apply(snapshot);
            if (failing)
            {
                throw new IllegalStateException("map plugin broke");
            }
        }
    }

    @Test
    void aFailedDrawIsRetriedOnTheNextLookThoughNothingChanged()
    {
        // The picture is the same, so without a retry the map would stay half-drawn until
        // something on the server moved.
        final Flaky flaky = new Flaky();
        MapMarkers.setProviderForTest(flaky);
        enable();
        MapMarkers.tick();
        runBackground();
        flaky.failing = false;

        MapMarkers.tick();

        assertEquals(1, background.size(), "the same picture should be drawn again");
        runBackground();
        assertEquals(List.of(showing, showing), flaky.applied);
        MapMarkers.tick();
        assertTrue(background.isEmpty(), "and once it has drawn, not again");
    }

    @Test
    void aNewFailureAfterARecoveryIsReportedAgain()
    {
        final Flaky flaky = new Flaky();
        MapMarkers.setProviderForTest(flaky);
        enable();
        MapMarkers.tick();
        runBackground();
        MapMarkers.tick();
        runBackground();
        verify(logger, times(1)).prettyLog(eq(Level.WARNING), contains("Test map"), any(Throwable.class));

        flaky.failing = false;
        MapMarkers.tick();
        runBackground();
        flaky.failing = true;
        showing = picture("later");
        MapMarkers.tick();
        runBackground();

        verify(logger, times(2)).prettyLog(eq(Level.WARNING), contains("Test map"), any(Throwable.class));
    }

    @Test
    void aLookThatThrowsIsReportedOnceAndNeverEscapesTheTask()
    {
        final IllegalStateException broken = new IllegalStateException("a ring with no name");
        MapMarkers.setSourceForTest(() ->
        {
            throw broken;
        });
        enable();

        MapMarkers.tick();
        MapMarkers.tick();

        verify(logger, times(1)).prettyLog(eq(Level.WARNING), contains("web map"), eq(broken));
        assertTrue(background.isEmpty());

        MapMarkers.setSourceForTest(() -> showing);
        MapMarkers.tick();
        assertEquals(1, background.size(), "a good look afterwards draws as usual");
        MapMarkers.setSourceForTest(() ->
        {
            throw broken;
        });
        MapMarkers.tick();
        verify(logger, times(2)).prettyLog(eq(Level.WARNING), contains("web map"), eq(broken));
    }

    @Test
    void anEnableThatFailsPartWayLeavesNoHookInDynmap()
    {
        // Dynmap keeps its listeners in a static list. A hook left there by an enable that
        // failed would be called by Dynmap for the rest of the server's life, holding this
        // plugin's old classloader, and disable could not reach it.
        MapMarkers.setProviderForTest(null);
        ConfigTestSupport.set(ConfigKeys.DYNMAP_ENABLED, true);
        final IllegalStateException refused = new IllegalStateException("listener refused");
        doThrow(refused).when(pluginManager).registerEvents(any(Listener.class), eq(plugin));

        assertThrows(IllegalStateException.class, () -> MapMarkers.enable(plugin));

        assertFalse(MapMarkers.isRunning());
        final DynmapCommonAPI dynmap = mock(DynmapCommonAPI.class);
        try
        {
            DynmapCommonAPIListener.apiInitialized(dynmap);
            verifyNoInteractions(dynmap);
        }
        finally
        {
            DynmapCommonAPIListener.apiTerminated();
        }
    }

    @Test
    void aSuccessfulEnableHooksIntoDynmapAndDisableUnhooks()
    {
        // The other half of the test above, so its "no interactions" means something: a hook
        // that is registered is called.
        MapMarkers.setProviderForTest(null);
        ConfigTestSupport.set(ConfigKeys.DYNMAP_ENABLED, true);
        final DynmapCommonAPI dynmap = mock(DynmapCommonAPI.class);
        try
        {
            MapMarkers.enable(plugin);
            DynmapCommonAPIListener.apiInitialized(dynmap);
            verify(dynmap).markerAPIInitialized();

            MapMarkers.disable();
            clearInvocations(dynmap);
            DynmapCommonAPIListener.apiInitialized(dynmap);
            verifyNoInteractions(dynmap);
        }
        finally
        {
            DynmapCommonAPIListener.apiTerminated();
        }
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
