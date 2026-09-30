package com.wormhole_xtreme.wormhole.plugin.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerSet;
import org.dynmap.markers.PolyLineMarker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.plugin.map.MapSnapshot.BeamMark;
import com.wormhole_xtreme.wormhole.plugin.map.MapSnapshot.Footprint;
import com.wormhole_xtreme.wormhole.plugin.map.MapSnapshot.GateMark;
import com.wormhole_xtreme.wormhole.plugin.map.MapSnapshot.LineMark;
import com.wormhole_xtreme.wormhole.plugin.map.MapSnapshot.MirrorMark;
import com.wormhole_xtreme.wormhole.plugin.map.MapSnapshot.RingMark;

/**
 * Drawing a picture on Dynmap, and only what changed (#236).
 *
 * <p>Every marker Dynmap is told about is a message to every browser watching the map, so
 * redrawing a whole server every few seconds is not free: an unchanged mark must be left
 * alone. And everything is made non-persistent, because Dynmap writes persistent markers to
 * its own file, where a gate removed while Dynmap was down would live on after this plugin
 * had forgotten it.
 *
 * <p>Dynmap's marker API is mocked; each marker set hands out a fresh mock per marker, kept
 * by id, so a test can ask which marker was deleted.
 */
class DynmapMapProviderTest
{
    private MarkerAPI api;
    private final Map<String, FakeSet> sets = new HashMap<>();
    private final AtomicInteger readyCalls = new AtomicInteger();
    private DynmapMapProvider provider;

    /** One mocked marker set, and the markers made in it. */
    private static final class FakeSet
    {
        final MarkerSet set = mock(MarkerSet.class);
        final Map<String, Marker> points = new HashMap<>();
        final Map<String, AreaMarker> areas = new HashMap<>();
        final Map<String, PolyLineMarker> lines = new HashMap<>();
        /** A marker id whose creation throws, as Dynmap might part-way through a draw; null for none. */
        String failOn = null;

        FakeSet()
        {
            when(set.createMarker(anyString(), anyString(), anyBoolean(), anyString(), anyDouble(), anyDouble(),
                anyDouble(), any(), anyBoolean())).thenAnswer(call ->
                {
                    if (call.getArgument(0).equals(failOn))
                    {
                        throw new IllegalStateException("Dynmap failed part-way");
                    }
                    final Marker m = mock(Marker.class);
                    points.put(call.getArgument(0), m);
                    return m;
                });
            when(set.findMarker(anyString())).thenAnswer(call -> points.get(call.getArgument(0)));
            when(set.createAreaMarker(anyString(), anyString(), anyBoolean(), anyString(), any(double[].class),
                any(double[].class), anyBoolean())).thenAnswer(call ->
                {
                    final AreaMarker m = mock(AreaMarker.class);
                    areas.put(call.getArgument(0), m);
                    return m;
                });
            when(set.findAreaMarker(anyString())).thenAnswer(call -> areas.get(call.getArgument(0)));
            when(set.createPolyLineMarker(anyString(), anyString(), anyBoolean(), anyString(), any(double[].class),
                any(double[].class), any(double[].class), anyBoolean())).thenAnswer(call ->
                {
                    final PolyLineMarker m = mock(PolyLineMarker.class);
                    lines.put(call.getArgument(0), m);
                    return m;
                });
            when(set.findPolyLineMarker(anyString())).thenAnswer(call -> lines.get(call.getArgument(0)));
        }
    }

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install();
        api = mock(MarkerAPI.class);
        for (final String id : new String[] {DynmapMapProvider.GATES, DynmapMapProvider.RINGS,
            DynmapMapProvider.BEAMS, DynmapMapProvider.MIRRORS})
        {
            final FakeSet fake = new FakeSet();
            sets.put(id, fake);
            when(api.createMarkerSet(eq(id), anyString(), isNull(), anyBoolean())).thenReturn(fake.set);
        }
        final MarkerIcon icon = mock(MarkerIcon.class);
        when(api.createMarkerIcon(anyString(), anyString(), any(InputStream.class))).thenReturn(icon);
        provider = new DynmapMapProvider(MapLayers.ALL, readyCalls::incrementAndGet);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PluginTestSupport.remove();
    }

    private MarkerSet set(final String id)
    {
        return sets.get(id).set;
    }

    private static GateMark gate(final String name)
    {
        return new GateMark(name.toLowerCase(), "world", name, "Milky Way", "Daniel", false, 11, 65.5, 20.5,
            new Footprint(10, 20, 12, 21, 64, 67));
    }

    private static MapSnapshot picture(final GateMark... gates)
    {
        final Map<String, GateMark> byId = new HashMap<>();
        for (final GateMark g : gates)
        {
            byId.put(g.id(), g);
        }
        return new MapSnapshot(byId,
            Map.of("abydos|chulak", new LineMark("abydos|chulak", "world", "Abydos to Chulak", 0, 64, 0, 9, 64, 9)),
            Map.of("r1:a", new RingMark("r1:a", "world", "Mine", "Mine to ? (r1)", null, 0.5, 60, 0.5)),
            Map.of("r1", new LineMark("r1", "world", "Mine to ? (r1)", 0.5, 60, 0.5, 9.5, 90, 9.5)),
            Map.of("market", new BeamMark("market", "world", "Market", 5, 64, 6)),
            Map.of("hall", new MirrorMark("hall", "world", "Hall", 3.5, 65.5, -6.5)));
    }

    private MapSnapshot everything()
    {
        return picture(gate("Abydos"), gate("Chulak"));
    }

    @Test
    void anApplyBeforeDynmapIsUpDoesNothing()
    {
        provider.apply(everything());

        verifyNoInteractions(api);
    }

    @Test
    void itIsReadyOnlyWhileDynmapIsUp()
    {
        assertFalse(provider.ready(), "not before Dynmap has handed over its markers");
        provider.attach(api);
        assertTrue(provider.ready());
        provider.detach();
        assertFalse(provider.ready(), "nor after Dynmap has gone");
        provider.clear();
        provider.detach();
    }

    @Test
    void dynmapComingUpAsksForARedrawAndDoesNoMarkerWorkThere()
    {
        // Dynmap tells us it is up on the main thread; making layers and icons writes files,
        // so that has to wait for the next apply, which is off it.
        provider.attach(api);

        assertEquals(1, readyCalls.get(), "the latest picture should be asked for again");
        verifyNoInteractions(api);
    }

    @Test
    void theFirstApplyMakesEachLayerAndEveryMarkerNonPersistent()
    {
        provider.attach(api);

        provider.apply(everything());

        verify(api).createMarkerSet(DynmapMapProvider.GATES, "Stargates", null, false);
        verify(api).createMarkerSet(DynmapMapProvider.RINGS, "Transport rings", null, false);
        verify(api).createMarkerSet(DynmapMapProvider.BEAMS, "Beam destinations", null, false);
        verify(api).createMarkerSet(DynmapMapProvider.MIRRORS, "Quantum mirrors", null, false);
        final MarkerSet gates = set(DynmapMapProvider.GATES);
        verify(gates).createMarker(eq("abydos"), eq("Abydos"), eq(false), eq("world"), eq(11.0), eq(65.5),
            eq(20.5), any(), eq(false));
        verify(gates).createAreaMarker(eq("abydos"), eq("Abydos"), eq(false), eq("world"), any(double[].class),
            any(double[].class), eq(false));
        verify(gates).createPolyLineMarker(eq("abydos|chulak"), anyString(), eq(false), eq("world"),
            any(double[].class), any(double[].class), any(double[].class), eq(false));
        verify(set(DynmapMapProvider.RINGS)).createMarker(eq("r1:a"), anyString(), eq(false), eq("world"),
            anyDouble(), anyDouble(), anyDouble(), any(), eq(false));
        verify(set(DynmapMapProvider.RINGS)).createPolyLineMarker(eq("r1"), anyString(), eq(false), eq("world"),
            any(double[].class), any(double[].class), any(double[].class), eq(false));
        verify(set(DynmapMapProvider.BEAMS)).createMarker(eq("market"), anyString(), eq(false), eq("world"),
            anyDouble(), anyDouble(), anyDouble(), any(), eq(false));
        verify(set(DynmapMapProvider.MIRRORS)).createMarker(eq("hall"), eq("Hall"), eq(false), eq("world"),
            eq(3.5), eq(65.5), eq(-6.5), any(), eq(false));
    }

    @Test
    void anApplyThatRacesDynmapComingBackIsSetUpAgainOnTheNewApi()
    {
        // Dynmap restarting mid-apply: the apply carries on against the API it started with,
        // and must not record itself as set up for the new one, or the redraw the new one
        // asks for would find nothing to set up and draw into sets that no longer exist.
        final MarkerAPI second = mock(MarkerAPI.class);
        when(second.createMarkerSet(anyString(), anyString(), isNull(), anyBoolean())).thenReturn(mock(MarkerSet.class));
        final AtomicInteger calls = new AtomicInteger();
        when(api.getMarkerSet(DynmapMapProvider.GATES)).thenAnswer(call ->
        {
            if (calls.getAndIncrement() == 0)
            {
                provider.attach(second);
            }
            return null;
        });
        provider.attach(api);
        provider.apply(everything());
        verifyNoInteractions(second);

        provider.apply(everything());

        verify(second).createMarkerSet(DynmapMapProvider.GATES, "Stargates", null, false);
    }

    @Test
    void aDrawThatFailsPartWayIsRedrawnFromScratchWithNothingLeftBehind()
    {
        // A marker made before the failure was never recorded as drawn; left alone it would
        // sit on the map for good. Starting over deletes the set, and it with it.
        provider.attach(api);
        final FakeSet gates = sets.get(DynmapMapProvider.GATES);
        gates.failOn = "chulak";
        assertThrows(IllegalStateException.class, () -> provider.apply(everything()));
        gates.failOn = null;
        for (final Map.Entry<String, FakeSet> made : sets.entrySet())
        {
            when(api.getMarkerSet(made.getKey())).thenReturn(made.getValue().set);
        }
        clearInvocations(api, gates.set);

        provider.apply(everything());

        verify(gates.set).deleteMarkerSet();
        verify(api).createMarkerSet(DynmapMapProvider.GATES, "Stargates", null, false);
        verify(gates.set).createMarker(eq("abydos"), anyString(), anyBoolean(), anyString(), anyDouble(),
            anyDouble(), anyDouble(), any(), anyBoolean());
        verify(gates.set).createMarker(eq("chulak"), anyString(), anyBoolean(), anyString(), anyDouble(),
            anyDouble(), anyDouble(), any(), anyBoolean());
    }

    @Test
    void anIconDynmapKeptFromBeforeIsGivenTheCurrentImage()
    {
        // Dynmap stores an icon between restarts, so without this a redrawn PNG in a new
        // release would never reach the map.
        final MarkerIcon kept = mock(MarkerIcon.class);
        when(api.getMarkerIcon("wormhole_gate_open")).thenReturn(kept);
        provider.attach(api);

        provider.apply(picture(gate("Abydos").withOpen(true)));

        verify(kept).setMarkerIconImage(any(InputStream.class));
        verify(api, never()).createMarkerIcon(eq("wormhole_gate_open"), anyString(), any(InputStream.class));
        verify(sets.get(DynmapMapProvider.GATES).set, atLeastOnce()).createMarker(anyString(), anyString(),
            anyBoolean(), anyString(), anyDouble(), anyDouble(), anyDouble(), eq(kept), anyBoolean());
    }

    @Test
    void aBuiltInIconIsNeverOverwritten()
    {
        final MarkerIcon builtIn = mock(MarkerIcon.class);
        when(builtIn.isBuiltIn()).thenReturn(true);
        when(api.getMarkerIcon("wormhole_rings")).thenReturn(builtIn);
        provider.attach(api);

        provider.apply(everything());

        verify(builtIn).isBuiltIn();
        verify(builtIn, never()).setMarkerIconImage(any(InputStream.class));
    }

    @Test
    void aLayerLeftFromBeforeAReloadIsReplaced()
    {
        final MarkerSet leftover = mock(MarkerSet.class);
        when(api.getMarkerSet(DynmapMapProvider.GATES)).thenReturn(leftover);
        provider.attach(api);

        provider.apply(everything());

        verify(leftover).deleteMarkerSet();
        verify(api).createMarkerSet(DynmapMapProvider.GATES, "Stargates", null, false);
    }

    @Test
    void aSecondIdenticalApplyTouchesNothing()
    {
        provider.attach(api);
        provider.apply(everything());
        final FakeSet gates = sets.get(DynmapMapProvider.GATES);
        final Marker abydos = gates.points.get("abydos");
        clearInvocations(api, abydos);
        for (final FakeSet fake : sets.values())
        {
            clearInvocations(fake.set);
        }

        provider.apply(everything());

        verifyNoInteractions(api, abydos);
        for (final FakeSet fake : sets.values())
        {
            verifyNoInteractions(fake.set);
        }
    }

    @Test
    void aRenamedGateLosesItsOldMarkerAndGainsANewOne()
    {
        provider.attach(api);
        provider.apply(picture(gate("Abydos")));
        final FakeSet gates = sets.get(DynmapMapProvider.GATES);
        final Marker oldPoint = gates.points.get("abydos");
        final AreaMarker oldArea = gates.areas.get("abydos");

        provider.apply(picture(gate("Nagada")));

        verify(oldPoint).deleteMarker();
        verify(oldArea).deleteMarker();
        verify(gates.set).createMarker(eq("nagada"), eq("Nagada"), eq(false), eq("world"), anyDouble(),
            anyDouble(), anyDouble(), any(), eq(false));
    }

    @Test
    void aChangedGateIsRedrawnAndItsNeighbourLeftAlone()
    {
        provider.attach(api);
        provider.apply(picture(gate("Abydos"), gate("Chulak")));
        final FakeSet gates = sets.get(DynmapMapProvider.GATES);
        final Marker chulak = gates.points.get("chulak");
        final Marker abydos = gates.points.get("abydos");
        final GateMark moved = new GateMark("abydos", "world", "Abydos", "Milky Way", "Jack", false, 11, 65.5, 20.5,
            new Footprint(10, 20, 12, 21, 64, 67));

        provider.apply(picture(moved, gate("Chulak")));

        verify(abydos).deleteMarker();
        verify(chulak, never()).deleteMarker();
    }

    @Test
    void aGateOpeningSwapsItsIconAndTouchesNothingElse()
    {
        final MarkerIcon open = mock(MarkerIcon.class);
        final MarkerIcon idle = mock(MarkerIcon.class);
        when(api.createMarkerIcon(eq("wormhole_gate_open"), anyString(), any(InputStream.class))).thenReturn(open);
        when(api.createMarkerIcon(eq("wormhole_gate_idle"), anyString(), any(InputStream.class))).thenReturn(idle);
        provider.attach(api);
        provider.apply(picture(gate("Abydos"), gate("Chulak")));
        final FakeSet gates = sets.get(DynmapMapProvider.GATES);
        verify(gates.set).createMarker(eq("abydos"), anyString(), anyBoolean(), anyString(), anyDouble(),
            anyDouble(), anyDouble(), eq(idle), anyBoolean());
        final Marker abydos = gates.points.get("abydos");
        final Marker chulak = gates.points.get("chulak");
        when(abydos.setMarkerIcon(open)).thenReturn(true);
        clearInvocations(gates.set, abydos, chulak);

        provider.apply(picture(gate("Abydos").withOpen(true), gate("Chulak")));

        verify(abydos).setMarkerIcon(open);
        verify(abydos, never()).deleteMarker();
        verify(gates.set, never()).createMarker(anyString(), anyString(), anyBoolean(), anyString(), anyDouble(),
            anyDouble(), anyDouble(), any(), anyBoolean());
        verifyNoInteractions(chulak);
        clearInvocations(abydos);

        provider.apply(picture(gate("Abydos").withOpen(true), gate("Chulak")));

        verifyNoInteractions(abydos);
    }

    @Test
    void aGateWhoseIconWillNotSwapIsRedrawnOpen()
    {
        final MarkerIcon open = mock(MarkerIcon.class);
        when(api.createMarkerIcon(eq("wormhole_gate_open"), anyString(), any(InputStream.class))).thenReturn(open);
        provider.attach(api);
        provider.apply(picture(gate("Abydos")));
        final Marker abydos = sets.get(DynmapMapProvider.GATES).points.get("abydos");

        provider.apply(picture(gate("Abydos").withOpen(true)));

        verify(abydos).deleteMarker();
        verify(sets.get(DynmapMapProvider.GATES).set).createMarker(eq("abydos"), anyString(), anyBoolean(),
            anyString(), anyDouble(), anyDouble(), anyDouble(), eq(open), anyBoolean());
    }

    @Test
    void aRemovedGateLosesItsMarker()
    {
        provider.attach(api);
        provider.apply(picture(gate("Abydos"), gate("Chulak")));
        final FakeSet gates = sets.get(DynmapMapProvider.GATES);
        final Marker chulak = gates.points.get("chulak");

        provider.apply(picture(gate("Abydos")));

        verify(chulak).deleteMarker();
        verify(gates.areas.get("chulak")).deleteMarker();
    }

    @Test
    void aRemovedMirrorLosesItsMarker()
    {
        provider.attach(api);
        final MapSnapshot with = everything();
        provider.apply(with);
        final Marker hall = sets.get(DynmapMapProvider.MIRRORS).points.get("hall");

        provider.apply(new MapSnapshot(with.gates(), with.gateLinks(), with.rings(), with.ringLinks(),
            with.beams(), Map.of()));

        verify(hall).deleteMarker();
    }

    @Test
    void aGateOrOwnerNameCannotAddMarkupToTheMap()
    {
        provider.attach(api);
        final GateMark sneaky = new GateMark("<b>x</b>", "world", "<b>x</b>", null, "<script>alert(1)</script>",
            false, 0, 64, 0, null);

        provider.apply(picture(sneaky));

        final ArgumentCaptor<String> description = ArgumentCaptor.forClass(String.class);
        verify(sets.get(DynmapMapProvider.GATES).points.get("<b>x</b>")).setDescription(description.capture());
        assertTrue(description.getValue().contains("&lt;b&gt;x&lt;/b&gt;"),
            "the name should be shown as text: " + description.getValue());
        assertFalse(description.getValue().contains("<script>"),
            "a player's name must not become a script on the map page: " + description.getValue());
    }

    @Test
    void ourOwnIconIsUsedWhenDynmapWillTakeIt()
    {
        provider.attach(api);

        provider.apply(everything());

        verify(api).createMarkerIcon(eq("wormhole_gate_open"), anyString(), any(InputStream.class));
        verify(api).createMarkerIcon(eq("wormhole_gate_idle"), anyString(), any(InputStream.class));
        verify(api).createMarkerIcon(eq("wormhole_mirror"), anyString(), any(InputStream.class));
        verify(api, never()).getMarkerIcon("portal");
    }

    @Test
    void aBuiltInIconIsUsedWhenOursCannotBeMade()
    {
        when(api.createMarkerIcon(anyString(), anyString(), any(InputStream.class))).thenReturn(null);
        final MarkerIcon portal = mock(MarkerIcon.class);
        when(api.getMarkerIcon("portal")).thenReturn(portal);
        provider.attach(api);

        provider.apply(everything());

        verify(set(DynmapMapProvider.GATES)).createMarker(eq("abydos"), anyString(), anyBoolean(), anyString(),
            anyDouble(), anyDouble(), anyDouble(), eq(portal), anyBoolean());
    }

    @Test
    void dynmapComingBackRedrawsEverything()
    {
        // A /dynmap reload throws away every non-persistent set. The picture has not changed,
        // so without starting over nothing would be drawn again.
        provider.attach(api);
        provider.apply(everything());
        clearInvocations(api);

        provider.attach(api);
        provider.apply(everything());

        verify(api).createMarkerSet(DynmapMapProvider.GATES, "Stargates", null, false);
        verify(set(DynmapMapProvider.GATES), times(2)).createMarker(eq("abydos"),
            anyString(), anyBoolean(), anyString(), anyDouble(), anyDouble(), anyDouble(), any(), anyBoolean());
    }

    @Test
    void clearTakesEveryLayerOff()
    {
        provider.attach(api);
        provider.apply(everything());
        for (final Map.Entry<String, FakeSet> made : sets.entrySet())
        {
            when(api.getMarkerSet(made.getKey())).thenReturn(made.getValue().set);
        }

        provider.clear();

        for (final FakeSet fake : sets.values())
        {
            verify(fake.set).deleteMarkerSet();
        }
    }

    /**
     * Applies everything with one layer switched off, and checks that layer was never made
     * while the rings -- or, with rings off, the gates -- still were.
     */
    private void assertLayerLeftOff(final MapLayers layers, final String off)
    {
        final MarkerSet leftover = mock(MarkerSet.class);
        when(api.getMarkerSet(off)).thenReturn(leftover);
        provider = new DynmapMapProvider(layers, readyCalls::incrementAndGet);
        provider.attach(api);

        provider.apply(everything());

        verify(api, never()).createMarkerSet(eq(off), anyString(), any(), anyBoolean());
        verify(leftover).deleteMarkerSet();
        final String on = DynmapMapProvider.RINGS.equals(off) ? DynmapMapProvider.GATES : DynmapMapProvider.RINGS;
        verify(set(on), atLeastOnce()).createMarker(anyString(), anyString(), anyBoolean(), anyString(), anyDouble(),
            anyDouble(), anyDouble(), any(), anyBoolean());
        verify(set(off), never()).createMarker(anyString(), anyString(), anyBoolean(), anyString(), anyDouble(),
            anyDouble(), anyDouble(), any(), anyBoolean());
    }

    @Test
    void gatesSwitchedOffAreNeverAddedAsALayer()
    {
        assertLayerLeftOff(new MapLayers(false, true, true, true, true), DynmapMapProvider.GATES);
    }

    @Test
    void ringsSwitchedOffAreNeverAddedAsALayer()
    {
        assertLayerLeftOff(new MapLayers(true, true, false, true, true), DynmapMapProvider.RINGS);
    }

    @Test
    void beamsSwitchedOffAreNeverAddedAsALayer()
    {
        assertLayerLeftOff(new MapLayers(true, true, true, false, true), DynmapMapProvider.BEAMS);
    }

    @Test
    void mirrorsSwitchedOffAreNeverAddedAsALayer()
    {
        assertLayerLeftOff(new MapLayers(true, true, true, true, false), DynmapMapProvider.MIRRORS);
    }
}
