package com.wormhole_xtreme.wormhole.plugin.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.plugin.map.MapSnapshot.BeamMark;
import com.wormhole_xtreme.wormhole.plugin.map.MapSnapshot.Footprint;
import com.wormhole_xtreme.wormhole.plugin.map.MapSnapshot.GateMark;
import com.wormhole_xtreme.wormhole.plugin.map.MapSnapshot.LineMark;
import com.wormhole_xtreme.wormhole.plugin.map.MapSnapshot.MirrorMark;
import com.wormhole_xtreme.wormhole.plugin.map.MapSnapshot.RingMark;

import de.bluecolored.bluemap.api.AssetStorage;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.markers.ExtrudeMarker;
import de.bluecolored.bluemap.api.markers.LineMarker;
import de.bluecolored.bluemap.api.markers.Marker;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;

/**
 * Drawing a picture on BlueMap (#530).
 *
 * <p>Two things about BlueMap shape this provider, and the tests here stand for them. BlueMap
 * throws away every marker a plugin made when it reloads, so a BlueMap that comes back must be
 * drawn on again in full though nothing on the server changed. And markers belong to a map, not a
 * world: a world shown flat and in 3D has two maps, each with its own assets, and a gate drawn on
 * only one of them is missing from the other.
 *
 * <p>BlueMap's API objects are mocked; its marker sets and markers are BlueMap's own classes,
 * held in a real map per mocked BlueMap map, as BlueMap holds them.
 */
class BlueMapMapProviderTest
{
    private final AtomicInteger readyCalls = new AtomicInteger();
    private BlueMapMapProvider provider;

    /** A mocked BlueMap: worlds by name, each with its maps, and every map's marker sets. */
    private static final class FakeBlueMap
    {
        final BlueMapAPI api = mock(BlueMapAPI.class);
        final List<BlueMapMap> maps = new ArrayList<>();
        final List<AssetStorage> assets = new ArrayList<>();

        FakeBlueMap()
        {
            when(api.getMaps()).thenReturn(maps);
            when(api.getWorld(any())).thenReturn(Optional.empty());
        }

        /** Adds a world with this many maps, and returns each map's marker sets. */
        List<Map<String, MarkerSet>> world(final String name, final int mapCount) throws IOException
        {
            final List<BlueMapMap> ofWorld = new ArrayList<>();
            final List<Map<String, MarkerSet>> sets = new ArrayList<>();
            for (int i = 0; i < mapCount; i++)
            {
                final String id = name + i;
                final BlueMapMap map = mock(BlueMapMap.class);
                final Map<String, MarkerSet> onMap = new ConcurrentHashMap<>();
                final AssetStorage storage = mock(AssetStorage.class);
                when(map.getId()).thenReturn(id);
                when(map.getMarkerSets()).thenReturn(onMap);
                when(map.getAssetStorage()).thenReturn(storage);
                when(storage.writeAsset(anyString())).thenAnswer(call -> new ByteArrayOutputStream());
                when(storage.getAssetUrl(anyString())).thenAnswer(call -> "maps/" + id + "/assets/" + call.getArgument(0));
                ofWorld.add(map);
                sets.add(onMap);
                assets.add(storage);
            }
            maps.addAll(ofWorld);
            final BlueMapWorld world = mock(BlueMapWorld.class);
            when(world.getMaps()).thenReturn(ofWorld);
            when(api.getWorld(name)).thenReturn(Optional.of(world));
            return sets;
        }
    }

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install();
        provider = new BlueMapMapProvider(MapLayers.ALL, map -> readyCalls.incrementAndGet());
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PluginTestSupport.remove();
    }

    private static GateMark gate(final String name, final String world)
    {
        return new GateMark(name.toLowerCase(), world, name, "Milky Way", "Daniel", false, 11, 65.5, 20.5,
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

    private static MapSnapshot everything()
    {
        return picture(gate("Abydos", "world"), gate("Chulak", "world"));
    }

    private static POIMarker poi(final Map<String, MarkerSet> onMap, final String set, final String id)
    {
        return (POIMarker) onMap.get(set).get(id);
    }

    @Test
    void itIsReadyOnlyWhileBlueMapIsUpAndAnApplyBeforeThenDoesNothing()
    {
        final FakeBlueMap blueMap = new FakeBlueMap();
        provider.apply(everything());
        assertFalse(provider.ready(), "not before BlueMap has come up");

        provider.attach(blueMap.api);
        assertTrue(provider.ready());
        provider.detach();
        assertFalse(provider.ready(), "nor after BlueMap has gone");
        provider.apply(everything());
        verifyNoInteractions(blueMap.api);
    }

    @Test
    void blueMapComingUpAsksForARedrawAndDoesNoMarkerWorkThere()
    {
        // BlueMap says it is up on its own loading thread; the markers are made on the next
        // background draw, like every other change.
        final FakeBlueMap blueMap = new FakeBlueMap();

        provider.attach(blueMap.api);

        assertEquals(1, readyCalls.get(), "the latest picture is asked for");
        verifyNoInteractions(blueMap.api);
    }

    @Test
    void everyMapOfAWorldShowsTheMarksWithItsOwnIcons() throws IOException
    {
        final FakeBlueMap blueMap = new FakeBlueMap();
        final List<Map<String, MarkerSet>> maps = blueMap.world("world", 2);
        provider.attach(blueMap.api);

        provider.apply(everything());

        for (int i = 0; i < maps.size(); i++)
        {
            final Map<String, MarkerSet> onMap = maps.get(i);
            final MarkerSet gates = onMap.get(MapText.GATES);
            assertNotNull(gates, "each of the world's maps has the gates set, not just the first");
            assertInstanceOf(ExtrudeMarker.class, gates.get("opening:abydos"), "the opening is drawn as an area");
            assertInstanceOf(LineMarker.class, gates.get("link:abydos|chulak"), "and the dialled pair's line");
            assertEquals("maps/world" + i + "/assets/wormhole/gate-idle.png",
                poi(onMap, MapText.GATES, "gate:abydos").getIconAddress(),
                "an idle gate has the idle icon, from this map's own assets");
            assertEquals("maps/world" + i + "/assets/wormhole/rings.png",
                poi(onMap, MapText.RINGS, "ring:r1:a").getIconAddress());
            assertNotNull(onMap.get(MapText.RINGS).get("link:r1"));
            assertEquals("maps/world" + i + "/assets/wormhole/beam.png",
                poi(onMap, MapText.BEAMS, "market").getIconAddress());
            assertEquals("maps/world" + i + "/assets/wormhole/mirror.png",
                poi(onMap, MapText.MIRRORS, "hall").getIconAddress());
        }
        final POIMarker abydos = poi(maps.get(0), MapText.GATES, "gate:abydos");
        assertEquals(11, abydos.getPosition().getX());
        assertEquals(65.5, abydos.getPosition().getY());
        assertEquals(20.5, abydos.getPosition().getZ());
        assertTrue(abydos.getDetail().contains("Network: Milky Way"), abydos.getDetail());
    }

    @Test
    void aGateIsDrawnOnlyInItsOwnWorld() throws IOException
    {
        final FakeBlueMap blueMap = new FakeBlueMap();
        final List<Map<String, MarkerSet>> world = blueMap.world("world", 1);
        final List<Map<String, MarkerSet>> nether = blueMap.world("nether", 1);
        provider.attach(blueMap.api);

        provider.apply(picture(gate("Abydos", "world"), gate("Hot", "nether"), gate("Hidden", "unmapped")));

        assertNotNull(world.get(0).get(MapText.GATES).get("gate:abydos"));
        assertNull(world.get(0).get(MapText.GATES).get("gate:hot"), "a gate is only drawn in its own world");
        assertNotNull(nether.get(0).get(MapText.GATES).get("gate:hot"));
        assertNull(nether.get(0).get(MapText.GATES).get("gate:abydos"));
    }

    @Test
    void blueMapComingBackIsDrawnOnAgainInFull() throws IOException
    {
        // A /bluemap reload makes new maps with none of our markers. The picture has not
        // changed, so a provider that only drew what changed would leave BlueMap empty.
        final FakeBlueMap first = new FakeBlueMap();
        first.world("world", 1);
        provider.attach(first.api);
        provider.apply(everything());
        provider.detach();

        final FakeBlueMap second = new FakeBlueMap();
        final List<Map<String, MarkerSet>> fresh = second.world("world", 1);
        provider.attach(second.api);
        provider.apply(everything());

        assertNotNull(fresh.get(0).get(MapText.GATES), "the reloaded map gets the gates set again");
        assertNotNull(fresh.get(0).get(MapText.GATES).get("gate:abydos"));
        assertNotNull(poi(fresh.get(0), MapText.GATES, "gate:abydos").getIconAddress());
        verify(second.assets.get(0)).writeAsset("wormhole/gate.png");
    }

    @Test
    void onlyWhatChangedIsTouched() throws IOException
    {
        final FakeBlueMap blueMap = new FakeBlueMap();
        final List<Map<String, MarkerSet>> maps = blueMap.world("world", 1);
        provider.attach(blueMap.api);
        provider.apply(everything());
        final MarkerSet gates = maps.get(0).get(MapText.GATES);
        final Marker abydos = gates.get("gate:abydos");
        assertNotNull(gates.get("gate:chulak"), "both gates are drawn to begin with");

        provider.apply(picture(gate("Abydos", "world").withOpen(true)));

        assertSame(gates, maps.get(0).get(MapText.GATES), "the set is kept, not remade");
        assertNull(gates.get("gate:chulak"), "a gate that went is taken off");
        assertNull(gates.get("opening:chulak"));
        assertNotEquals(abydos, gates.get("gate:abydos"), "a gate whose wormhole opened is redrawn");
        assertEquals("maps/world0/assets/wormhole/gate.png", poi(maps.get(0), MapText.GATES, "gate:abydos").getIconAddress());

        final Marker opened = gates.get("gate:abydos");
        provider.apply(picture(gate("Abydos", "world").withOpen(true)));
        assertSame(opened, gates.get("gate:abydos"), "an unchanged gate is left alone");
    }

    @Test
    void aWorldWhoseMarksAllWentIsEmptied() throws IOException
    {
        final FakeBlueMap blueMap = new FakeBlueMap();
        final List<Map<String, MarkerSet>> nether = blueMap.world("nether", 1);
        provider.attach(blueMap.api);
        provider.apply(picture(gate("Hot", "nether")));

        provider.apply(picture());

        assertTrue(nether.get(0).get(MapText.GATES).getMarkers().isEmpty(),
            "the last gate in a world is taken off though nothing else is drawn there");
    }

    @Test
    void aLayerSwitchedOffIsNotMadeAndOneLeftOverIsTakenOff() throws IOException
    {
        final FakeBlueMap blueMap = new FakeBlueMap();
        final List<Map<String, MarkerSet>> maps = blueMap.world("world", 1);
        maps.get(0).put(MapText.RINGS, new MarkerSet("Transport rings"));
        provider = new BlueMapMapProvider(new MapLayers(true, true, false, true, true), map -> readyCalls.incrementAndGet());
        provider.attach(blueMap.api);

        provider.apply(everything());

        assertNull(maps.get(0).get(MapText.RINGS), "rings are switched off, so no rings set, not even an old one");
        assertNotNull(maps.get(0).get(MapText.GATES), "while the gates set is made as usual");
    }

    @Test
    void clearTakesEverySetOfOursOffEveryMap() throws IOException
    {
        final FakeBlueMap blueMap = new FakeBlueMap();
        final List<Map<String, MarkerSet>> maps = blueMap.world("world", 2);
        final MarkerSet someoneElses = new MarkerSet("Shops");
        maps.get(1).put("shops", someoneElses);
        provider.attach(blueMap.api);
        provider.apply(everything());

        provider.clear();

        for (final Map<String, MarkerSet> onMap : maps)
        {
            assertNull(onMap.get(MapText.GATES));
            assertNull(onMap.get(MapText.RINGS));
            assertNull(onMap.get(MapText.BEAMS));
            assertNull(onMap.get(MapText.MIRRORS));
        }
        assertSame(someoneElses, maps.get(1).get("shops"), "another plugin's set is left alone");
    }

    @Test
    void namesCannotAddMarkup() throws IOException
    {
        final FakeBlueMap blueMap = new FakeBlueMap();
        final List<Map<String, MarkerSet>> maps = blueMap.world("world", 1);
        provider.attach(blueMap.api);
        final GateMark sly = new GateMark("<b>x</b>", "world", "<b>x</b>", "<i>net</i>", null, false, 0, 64, 0, null);

        provider.apply(picture(sly));

        final POIMarker drawn = poi(maps.get(0), MapText.GATES, "gate:<b>x</b>");
        assertEquals("&lt;b&gt;x&lt;/b&gt;", drawn.getLabel(), "BlueMap shows a label as HTML when there is no detail");
        assertEquals("<b>&lt;b&gt;x&lt;/b&gt;</b><br/>Network: &lt;i&gt;net&lt;/i&gt;", drawn.getDetail());
    }

    @Test
    void anIconBlueMapWillNotTakeFallsBackToItsOwn() throws IOException
    {
        final FakeBlueMap blueMap = new FakeBlueMap();
        final List<Map<String, MarkerSet>> maps = blueMap.world("world", 1);
        when(blueMap.assets.get(0).writeAsset(anyString())).thenThrow(new IOException("read-only"));
        provider.attach(blueMap.api);

        provider.apply(everything());

        final POIMarker abydos = poi(maps.get(0), MapText.GATES, "gate:abydos");
        assertNotNull(abydos, "the gate is still drawn");
        assertFalse(abydos.getIconAddress().contains("wormhole"), abydos.getIconAddress());
    }

    @Test
    void registeringHearsBlueMapComeAndGoAndUnregisteringStops() throws Exception
    {
        final FakeBlueMap blueMap = new FakeBlueMap();
        provider.register();
        try
        {
            BlueMapLifecycle.up(blueMap.api);
            assertTrue(provider.ready(), "BlueMap coming up is heard");
            assertEquals(1, readyCalls.get());
            BlueMapLifecycle.down(blueMap.api);
            assertFalse(provider.ready(), "and going");

            provider.unregister();
            BlueMapLifecycle.up(blueMap.api);
            assertFalse(provider.ready(), "not after unregistering");
            assertEquals(1, readyCalls.get());
        }
        finally
        {
            provider.unregister();
            BlueMapLifecycle.down(blueMap.api);
        }
    }
}
