package com.wormhole_xtreme.wormhole.plugin.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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

import net.pl3x.map.core.Pl3xMap;
import net.pl3x.map.core.image.IconImage;
import net.pl3x.map.core.markers.layer.Layer;
import net.pl3x.map.core.markers.marker.Icon;
import net.pl3x.map.core.markers.marker.Marker;
import net.pl3x.map.core.markers.marker.Polyline;
import net.pl3x.map.core.markers.marker.Rectangle;
import net.pl3x.map.core.registry.IconRegistry;
import net.pl3x.map.core.registry.Registry;
import net.pl3x.map.core.registry.WorldRegistry;
import net.pl3x.map.core.world.World;

/**
 * Drawing a picture on Pl3xMap (#532).
 *
 * <p>Pl3xMap asks each registered layer for its markers on its own thread, so the provider's job
 * is to keep a layer of ours registered on every world, each answering with that world's marks.
 * The case this is built around is {@code /map reload}: Pl3xMap makes every world afresh with no
 * layers of ours, and forgets every icon, while nothing on the server has changed.
 *
 * <p>Pl3xMap is the plugin jar itself, carrying its own copy of Adventure, so it is kept off the
 * test classpath wherever Paper's is on it. That is why this lives in src/pl3xmap, which only the
 * Spigot builds compile: on the Paper builds this class could not even be listed.
 */
class Pl3xMapMapProviderTest
{
    private final AtomicInteger readyCalls = new AtomicInteger();
    private Pl3xMap pl3xmap;
    private WorldRegistry worlds;
    private final Set<String> icons = new HashSet<>();
    private Pl3xMapMapProvider provider;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install();
        pl3xmap = mock(Pl3xMap.class);
        worlds = new WorldRegistry();
        final IconRegistry iconRegistry = mock(IconRegistry.class);
        when(iconRegistry.has(anyString())).thenAnswer(call -> icons.contains(call.<String>getArgument(0)));
        final IconImage image = mock(IconImage.class);
        when(iconRegistry.get(anyString())).thenAnswer(call -> icons.contains(call.<String>getArgument(0)) ? image : null);
        when(iconRegistry.register(anyString(), any(IconImage.class))).thenAnswer(call ->
        {
            icons.add(call.getArgument(0));
            return call.getArgument(1);
        });
        when(pl3xmap.isEnabled()).thenReturn(true);
        when(pl3xmap.getWorldRegistry()).thenReturn(worlds);
        when(pl3xmap.getIconRegistry()).thenReturn(iconRegistry);
        provider = provider(MapLayers.ALL);
        installed(pl3xmap);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        installed(null);
        PluginTestSupport.remove();
    }

    /**
     * Makes this Pl3xMap the one {@code Pl3xMap.api()} returns, as Pl3xMap's own constructor does:
     * an icon marker checks its image against that one's registry.
     */
    private static void installed(final Pl3xMap api) throws ReflectiveOperationException
    {
        final Field field = Class.forName("net.pl3x.map.core.Pl3xMap$Provider").getDeclaredField("api");
        field.setAccessible(true);
        field.set(null, api);
    }

    private Pl3xMapMapProvider provider(final MapLayers layers)
    {
        return new Pl3xMapMapProvider(layers, map -> readyCalls.incrementAndGet(), () -> pl3xmap);
    }

    /** Adds a world Pl3xMap maps, made afresh as Pl3xMap makes it, and returns its layers. */
    private Registry<Layer> world(final String name)
    {
        final World world = mock(World.class);
        final Registry<Layer> layers = new Registry<>();
        when(world.getName()).thenReturn(name);
        when(world.getLayerRegistry()).thenReturn(layers);
        worlds.register(name, world);
        return layers;
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

    private static Collection<Marker<?>> markers(final Registry<Layer> layers, final String id)
    {
        final Layer layer = layers.get(id);
        assertNotNull(layer, id + " should be registered");
        return layer.getMarkers();
    }

    private static Marker<?> marker(final Registry<Layer> layers, final String id, final String key)
    {
        return markers(layers, id).stream().filter(m -> m.getKey().equals(key)).findFirst().orElse(null);
    }

    @Test
    void registeringFindsPl3xMapAndAsksForTheWholePicture()
    {
        assertFalse(provider.ready());

        provider.register();

        assertTrue(provider.ready());
        assertEquals(1, readyCalls.get());
        provider.unregister();
        assertFalse(provider.ready());
    }

    @Test
    void aPl3xMapThatIsNotLoadedOrNotEnabledIsNotReady()
    {
        provider = new Pl3xMapMapProvider(MapLayers.ALL, map -> readyCalls.incrementAndGet(), () -> null);
        provider.register();
        assertFalse(provider.ready(), "not loaded");

        when(pl3xmap.isEnabled()).thenReturn(false);
        provider = provider(MapLayers.ALL);
        provider.register();
        assertFalse(provider.ready(), "part-way through /map reload");
        assertFalse(provider.lost(), "and not asked to be drawn on meanwhile");
        assertEquals(0, readyCalls.get());
    }

    @Test
    void everyWorldGetsEachLayerShowingOnlyItsOwnMarks()
    {
        final Registry<Layer> world = world("world");
        final Registry<Layer> nether = world("nether");
        provider.register();

        provider.apply(picture(gate("Abydos", "world"), gate("Hot", "nether")));

        final Icon abydos = (Icon) marker(world, MapText.GATES, "gate:abydos");
        assertEquals("wormhole_gate_idle", abydos.getImage());
        assertEquals(11, abydos.getPoint().x());
        assertEquals("Abydos", abydos.getOptions().getTooltip().getContent());
        assertTrue(marker(world, MapText.GATES, "opening:abydos") instanceof Rectangle);
        final Polyline line = (Polyline) marker(world, MapText.GATES, "link:abydos|chulak");
        assertEquals(0xCC37B0D8, line.getOptions().getStroke().getColor(), "the gate cyan at 80%, as Pl3xMap takes it");
        assertNull(marker(world, MapText.GATES, "gate:hot"), "a gate is only in its own world");
        assertNotNull(marker(nether, MapText.GATES, "gate:hot"));
        assertNotNull(marker(world, MapText.RINGS, "ring:r1:a"));
        assertNotNull(marker(world, MapText.RINGS, "link:r1"));
        assertNotNull(marker(world, MapText.BEAMS, "market"));
        assertNotNull(marker(world, MapText.MIRRORS, "hall"));
        assertEquals("Stargates", world.get(MapText.GATES).getLabel());
        assertTrue(icons.contains("wormhole_gate_open"), "the icons are given to Pl3xMap");
    }

    @Test
    void aNewPictureIsSwappedInOnTheSameLayer()
    {
        final Registry<Layer> world = world("world");
        provider.register();
        provider.apply(picture(gate("Abydos", "world"), gate("Chulak", "world")));
        final Layer gates = world.get(MapText.GATES);

        provider.apply(picture(gate("Abydos", "world").withOpen(true)));

        assertSame(gates, world.get(MapText.GATES));
        assertEquals("wormhole_gate_open", ((Icon) marker(world, MapText.GATES, "gate:abydos")).getImage());
        assertNull(marker(world, MapText.GATES, "gate:chulak"), "Chulak went");
    }

    @Test
    void afterMapReloadTheLayersAndIconsAreFoundMissingAndPutBack()
    {
        // /map reload makes every world afresh, empty, and forgets the icons; the picture has not
        // changed, so without this the map would stay empty until something on the server did.
        world("world");
        provider.register();
        provider.apply(picture(gate("Abydos", "world")));
        assertFalse(provider.lost(), "nothing missing once drawn");

        worlds.entrySet().clear();
        icons.clear();
        final Registry<Layer> fresh = world("world");

        assertTrue(provider.lost());
        provider.apply(picture(gate("Abydos", "world")));
        assertFalse(provider.lost());
        assertNotNull(marker(fresh, MapText.GATES, "gate:abydos"));
        assertTrue(icons.contains("wormhole_gate_idle"));
    }

    @Test
    void aPointWhoseIconPl3xMapRefusedIsLeftOutAndTheRestDrawn()
    {
        // Pl3xMap throws on an icon marker whose image it does not have, which would lose the
        // whole draw over one unreadable icon.
        final Registry<Layer> world = world("world");
        when(pl3xmap.getIconRegistry().register(anyString(), any(IconImage.class)))
            .thenThrow(new IllegalStateException("Failed to save image"));
        provider.register();

        provider.apply(picture(gate("Abydos", "world")));

        assertNull(marker(world, MapText.GATES, "gate:abydos"), "no icon, so no point");
        assertNotNull(marker(world, MapText.GATES, "opening:abydos"), "but the opening is drawn");
        assertNotNull(marker(world, MapText.GATES, "link:abydos|chulak"));
    }

    @Test
    void aLayerSwitchedOffIsNotRegisteredNorMissed()
    {
        final Registry<Layer> world = world("world");
        provider = provider(new MapLayers(true, true, false, true, true));
        provider.register();

        provider.apply(picture(gate("Abydos", "world")));

        assertFalse(world.has(MapText.RINGS));
        assertTrue(world.has(MapText.GATES));
        assertFalse(provider.lost(), "a layer switched off is not missing");
    }

    @Test
    void clearUnregistersOurLayersAndNoOneElses()
    {
        final Registry<Layer> world = world("world");
        final Layer spawn = mock(Layer.class);
        world.register("pl3xmap_spawn", spawn);
        provider.register();
        provider.apply(picture(gate("Abydos", "world")));

        provider.clear();

        assertFalse(world.has(MapText.GATES));
        assertFalse(world.has(MapText.RINGS));
        assertFalse(world.has(MapText.BEAMS));
        assertFalse(world.has(MapText.MIRRORS));
        assertSame(spawn, world.get("pl3xmap_spawn"));
    }

    @Test
    void namesCannotAddMarkup()
    {
        final Registry<Layer> world = world("world");
        provider.register();

        provider.apply(picture(new GateMark("x", "world", "<b>x</b>", null, null, false, 0, 64, 0, null)));

        final Marker<?> drawn = marker(world, MapText.GATES, "gate:x");
        assertEquals("&lt;b&gt;x&lt;/b&gt;", drawn.getOptions().getTooltip().getContent());
        assertEquals("<b>&lt;b&gt;x&lt;/b&gt;</b>", drawn.getOptions().getPopup().getContent());
    }
}
