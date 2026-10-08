package com.wormhole_xtreme.wormhole.plugin.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

import xyz.jpenilla.squaremap.api.HtmlComponentSerializer;
import xyz.jpenilla.squaremap.api.Key;
import xyz.jpenilla.squaremap.api.LayerProvider;
import xyz.jpenilla.squaremap.api.MapWorld;
import xyz.jpenilla.squaremap.api.Pair;
import xyz.jpenilla.squaremap.api.PlayerManager;
import xyz.jpenilla.squaremap.api.Registry;
import xyz.jpenilla.squaremap.api.Squaremap;
import xyz.jpenilla.squaremap.api.WorldIdentifier;
import xyz.jpenilla.squaremap.api.marker.Icon;
import xyz.jpenilla.squaremap.api.marker.Marker;
import xyz.jpenilla.squaremap.api.marker.Polyline;
import xyz.jpenilla.squaremap.api.marker.Rectangle;

/**
 * Drawing a picture on squaremap (#531).
 *
 * <p>squaremap asks each registered layer for its markers whenever it writes a world's marker
 * file, on its own thread. So the provider's job is to have the right layers registered on every
 * world squaremap maps, each answering with that world's marks only, and to swap the answer
 * whole when the picture changes. A layer registered twice is an error in squaremap, and a world
 * that loads after startup has none of ours until something notices.
 *
 * <p>squaremap's API is mocked; its registries are a stand-in that refuses a key registered
 * twice, as squaremap's own do.
 */
class SquaremapMapProviderTest
{
    private final AtomicInteger readyCalls = new AtomicInteger();
    private final List<MapWorld> worlds = new ArrayList<>();
    private final FakeRegistry<BufferedImage> icons = new FakeRegistry<>();
    private final Squaremap squaremap = new FakeSquaremap();
    private SquaremapMapProvider provider;

    /** A registry that refuses a key twice and an unknown key, as squaremap's do. */
    private static final class FakeRegistry<T> implements Registry<T>
    {
        final Map<Key, T> entries = new HashMap<>();
        Key refuse = null;
        int registered = 0;

        @Override
        public void register(final Key key, final T value)
        {
            if (key.equals(refuse))
            {
                throw new IllegalStateException("refused: " + key);
            }
            if (entries.putIfAbsent(key, value) != null)
            {
                throw new IllegalArgumentException("already registered: " + key);
            }
            registered++;
        }

        @Override
        public void unregister(final Key key)
        {
            if (entries.remove(key) == null)
            {
                throw new IllegalArgumentException("not registered: " + key);
            }
        }

        @Override
        public boolean hasEntry(final Key key)
        {
            return entries.containsKey(key);
        }

        @Override
        public T get(final Key key)
        {
            return entries.get(key);
        }

        @Override
        public Iterable<Pair<Key, T>> entries()
        {
            return entries.entrySet().stream().map(e -> Pair.of(e.getKey(), e.getValue())).toList();
        }
    }

    /**
     * squaremap with its worlds and icons. Not a mock: mocking it reaches its HTML serializer, which
     * needs a newer Adventure than the test classpath has.
     */
    private final class FakeSquaremap implements Squaremap
    {
        @Override
        public Collection<MapWorld> mapWorlds()
        {
            return worlds;
        }

        @Override
        public Optional<MapWorld> getWorldIfEnabled(final WorldIdentifier identifier)
        {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public Registry<BufferedImage> iconRegistry()
        {
            return icons;
        }

        @Override
        public PlayerManager playerManager()
        {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public Path webDir()
        {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public HtmlComponentSerializer htmlComponentSerializer()
        {
            throw new UnsupportedOperationException("not used");
        }
    }

    /** Adds a world squaremap maps, and returns its layer registry. */
    private FakeRegistry<LayerProvider> world(final String name)
    {
        final MapWorld world = mock(MapWorld.class);
        final FakeRegistry<LayerProvider> layers = new FakeRegistry<>();
        when(world.identifier()).thenReturn(WorldIdentifier.create("minecraft", name));
        when(world.layerRegistry()).thenReturn(layers);
        worlds.add(world);
        return layers;
    }

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install();
        provider = provider(MapLayers.ALL);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PluginTestSupport.remove();
    }

    private SquaremapMapProvider provider(final MapLayers layers)
    {
        return new SquaremapMapProvider(layers, map -> readyCalls.incrementAndGet(), () -> squaremap,
            world -> world.identifier().value());
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

    private static Collection<Marker> markers(final FakeRegistry<LayerProvider> layers, final String id)
    {
        final LayerProvider layer = layers.get(Key.of(id));
        assertNotNull(layer, id + " should be registered");
        return layer.getMarkers();
    }

    @Test
    void registeringFindsSquaremapAndAsksForTheWholePicture()
    {
        assertFalse(provider.ready());

        provider.register();

        assertTrue(provider.ready());
        assertEquals(1, readyCalls.get());
        provider.unregister();
        assertFalse(provider.ready(), "not after unregistering");
    }

    @Test
    void aSquaremapThatIsNotLoadedIsNeverReady()
    {
        provider = new SquaremapMapProvider(MapLayers.ALL, map -> readyCalls.incrementAndGet(), () ->
        {
            throw new IllegalStateException("The squaremap API is not loaded.");
        }, world -> world.identifier().value());

        provider.register();

        assertFalse(provider.ready());
        assertEquals(0, readyCalls.get(), "nothing to show it on, so no redraw is asked for");
        provider.apply(picture(gate("Abydos", "world")));
    }

    @Test
    void everyWorldGetsEachLayerShowingOnlyItsOwnMarks()
    {
        final FakeRegistry<LayerProvider> world = world("world");
        final FakeRegistry<LayerProvider> nether = world("nether");
        provider.register();

        provider.apply(picture(gate("Abydos", "world"), gate("Hot", "nether")));

        final Collection<Marker> gates = markers(world, MapText.GATES);
        assertEquals(3, gates.size(), "Abydos's icon and opening, and the dialled pair's line: " + gates);
        assertEquals(1, gates.stream().filter(Rectangle.class::isInstance).count());
        assertEquals(1, gates.stream().filter(Polyline.class::isInstance).count());
        final Icon abydos = (Icon) gates.stream().filter(Icon.class::isInstance).findFirst().orElseThrow();
        assertEquals("wormhole_gate_idle", abydos.image().getKey());
        assertEquals(11, abydos.point().x());
        assertEquals(20.5, abydos.point().z());
        assertEquals(2, markers(nether, MapText.GATES).size(), "the nether has only Hot, and no line");
        assertEquals(2, markers(world, MapText.RINGS).size(), "a ring end and its line");
        assertEquals(1, markers(world, MapText.BEAMS).size());
        assertEquals(1, markers(world, MapText.MIRRORS).size());
        assertTrue(markers(nether, MapText.MIRRORS).isEmpty());
        assertTrue(icons.hasEntry(Key.of("wormhole_gate_open")), "the icons are given to squaremap");
        assertEquals(5, icons.registered);
    }

    @Test
    void aNewPictureIsSwappedInWithoutRegisteringAgain()
    {
        final FakeRegistry<LayerProvider> world = world("world");
        provider.register();
        provider.apply(picture(gate("Abydos", "world"), gate("Chulak", "world")));
        final LayerProvider gates = world.get(Key.of(MapText.GATES));

        provider.apply(picture(gate("Abydos", "world").withOpen(true)));

        assertSame(gates, world.get(Key.of(MapText.GATES)), "registered once; squaremap refuses a second");
        assertEquals(4, world.registered);
        assertEquals(5, icons.registered, "and the icons once");
        final Icon abydos = (Icon) gates.getMarkers().stream().filter(Icon.class::isInstance).findFirst().orElseThrow();
        assertEquals("wormhole_gate_open", abydos.image().getKey(), "the open gate shows lit");
        assertEquals(1, gates.getMarkers().stream().filter(Icon.class::isInstance).count(), "Chulak went");
    }

    @Test
    void aLayerSwitchedOffIsNotRegistered()
    {
        final FakeRegistry<LayerProvider> world = world("world");
        provider = provider(new MapLayers(true, true, false, true, true));
        provider.register();

        provider.apply(picture(gate("Abydos", "world")));

        assertFalse(world.hasEntry(Key.of(MapText.RINGS)));
        assertTrue(world.hasEntry(Key.of(MapText.GATES)));
    }

    @Test
    void aWorldLoadedLaterIsFoundMissingItsLayersAndThenGetsThem()
    {
        // squaremap has no event for a world it starts mapping, so the periodic look asks.
        world("world");
        provider.register();
        provider.apply(picture(gate("Abydos", "world")));
        assertFalse(provider.lost(), "nothing missing once drawn");

        final FakeRegistry<LayerProvider> later = world("later");
        assertTrue(provider.lost(), "a world with none of our layers");
        provider.apply(picture(gate("Abydos", "world"), gate("New", "later")));

        assertFalse(provider.lost());
        assertEquals(2, markers(later, MapText.GATES).size());
    }

    @Test
    void anIconSquaremapLostIsGivenBack()
    {
        world("world");
        provider.register();
        provider.apply(picture(gate("Abydos", "world")));

        icons.entries.clear();

        assertTrue(provider.lost());
        provider.apply(picture(gate("Abydos", "world")));
        assertTrue(icons.hasEntry(Key.of("wormhole_gate_idle")));
    }

    @Test
    void aWorldWithNoBukkitWorldIsSkippedAndTheRestDrawn()
    {
        final FakeRegistry<LayerProvider> world = world("world");
        final FakeRegistry<LayerProvider> gone = world("gone");
        provider = new SquaremapMapProvider(MapLayers.ALL, map -> readyCalls.incrementAndGet(), () -> squaremap,
            w ->
            {
                if (w.identifier().value().equals("gone"))
                {
                    throw new NullPointerException("no Bukkit world");
                }
                return w.identifier().value();
            });
        provider.register();

        provider.apply(picture(gate("Abydos", "world")));

        assertTrue(world.hasEntry(Key.of(MapText.GATES)));
        assertFalse(gone.hasEntry(Key.of(MapText.GATES)));
        assertFalse(provider.lost(), "a world that is never drawn on is not missing its layers, or every look redraws");
    }

    @Test
    void anIconSquaremapRefusedIsTriedEachDrawButNotMissedEveryLook()
    {
        // A refused icon stays refused; counting it as lost would rebuild the picture every look.
        world("world");
        icons.refuse = Key.of("wormhole_mirror");
        provider.register();
        provider.apply(picture(gate("Abydos", "world")));

        assertTrue(icons.hasEntry(Key.of("wormhole_gate_idle")), "the other icons are given");
        assertFalse(provider.lost(), "the refused icon is not found missing");

        icons.refuse = null;
        provider.apply(picture(gate("Abydos", "world")));
        assertTrue(icons.hasEntry(Key.of("wormhole_mirror")), "but it is tried again on the next draw");
        icons.entries.remove(Key.of("wormhole_mirror"));
        assertTrue(provider.lost(), "and once squaremap took it, losing it is noticed again");
    }

    @Test
    void clearUnregistersOurLayersAndNoOneElses()
    {
        final FakeRegistry<LayerProvider> world = world("world");
        final LayerProvider spawn = mock(LayerProvider.class);
        world.register(Key.of("squaremap-spawn_icon"), spawn);
        provider.register();
        provider.apply(picture(gate("Abydos", "world")));

        provider.clear();

        assertFalse(world.hasEntry(Key.of(MapText.GATES)));
        assertFalse(world.hasEntry(Key.of(MapText.RINGS)));
        assertFalse(world.hasEntry(Key.of(MapText.BEAMS)));
        assertFalse(world.hasEntry(Key.of(MapText.MIRRORS)));
        assertSame(spawn, world.get(Key.of("squaremap-spawn_icon")));
        assertThrows(IllegalArgumentException.class, () -> world.unregister(Key.of(MapText.GATES)),
            "really gone, not just emptied");
    }

    @Test
    void namesCannotAddMarkup()
    {
        final FakeRegistry<LayerProvider> world = world("world");
        provider.register();

        provider.apply(picture(new GateMark("x", "world", "<b>x</b>", null, null, false, 0, 64, 0, null)));

        final Marker drawn = markers(world, MapText.GATES).stream().filter(Icon.class::isInstance).findFirst().orElseThrow();
        assertEquals("&lt;b&gt;x&lt;/b&gt;", drawn.markerOptions().hoverTooltip());
        assertEquals("<b>&lt;b&gt;x&lt;/b&gt;</b>", drawn.markerOptions().clickTooltip());
        assertInstanceOf(Icon.class, drawn);
    }
}
