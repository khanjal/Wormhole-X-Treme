package com.wormhole_xtreme.wormhole.plugin.map;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.plugin.map.MapSnapshot.BeamMark;
import com.wormhole_xtreme.wormhole.plugin.map.MapSnapshot.Footprint;
import com.wormhole_xtreme.wormhole.plugin.map.MapSnapshot.GateMark;
import com.wormhole_xtreme.wormhole.plugin.map.MapSnapshot.LineMark;
import com.wormhole_xtreme.wormhole.plugin.map.MapSnapshot.MirrorMark;
import com.wormhole_xtreme.wormhole.plugin.map.MapSnapshot.RingMark;

import net.pl3x.map.core.Pl3xMap;
import net.pl3x.map.core.image.IconImage;
import net.pl3x.map.core.markers.Point;
import net.pl3x.map.core.markers.Vector;
import net.pl3x.map.core.markers.layer.Layer;
import net.pl3x.map.core.markers.marker.Icon;
import net.pl3x.map.core.markers.marker.Marker;
import net.pl3x.map.core.markers.marker.Polyline;
import net.pl3x.map.core.markers.option.Options;
import net.pl3x.map.core.registry.Registry;
import net.pl3x.map.core.world.World;

/**
 * Draws a {@link MapSnapshot} on Pl3xMap (#532).
 *
 * <p>The only class that names Pl3xMap's types, and only loaded once {@link MapMarkers} has found
 * Pl3xMap, so a server without it never meets them.
 *
 * <p>Like squaremap, Pl3xMap pulls: each world has a registry of layers, and Pl3xMap asks each
 * for its markers on its own thread, so drawing here is building the markers and swapping them in
 * whole. Unlike squaremap, Pl3xMap makes its worlds afresh, empty, on {@code /map reload}, and
 * forgets its icons; {@link #lost()} notices a world or an icon without ours, and the next draw
 * puts them back. Pl3xMap's own events are not used: it has no way to unregister a listener.
 *
 * <p>Pl3xMap has no API artifact; this compiles against the plugin built for 1.20.4, the newest
 * that is Java 17. The classes and members used here are the same in the builds for 1.21.11 and
 * 26.1.2, which only add to them.
 */
public final class Pl3xMapMapProvider implements MapProvider
{
    /** Every icon, registered with Pl3xMap by these keys. */
    private static final Map<String, String> ICONS = Map.of(
        MapText.GATE_OPEN_ICON, "wormhole_gate_open",
        MapText.GATE_IDLE_ICON, "wormhole_gate_idle",
        MapText.RINGS_ICON, "wormhole_rings",
        MapText.BEAM_ICON, "wormhole_beam",
        MapText.MIRROR_ICON, "wormhole_mirror");

    /** Where an icon is anchored: its centre. */
    private static final Vector ANCHOR = Vector.of(MapText.ICON_SIZE / 2.0, MapText.ICON_SIZE / 2.0);

    /** Which layers to make; one switched off is not registered at all. */
    private final MapLayers layers;

    /** Told when Pl3xMap is found up, to have the latest picture applied. */
    private final Consumer<MapProvider> onReady;

    /** Finds Pl3xMap; a seam, since Pl3xMap's own lookup is static. */
    private final Supplier<Pl3xMap> find;

    /** Pl3xMap, once found. */
    // An abstract class reference swapped whole: volatile is all the synchronisation it needs.
    @SuppressWarnings("java:S3077")
    private volatile Pl3xMap pl3xmap = null;

    /** What each layer shows, by layer id and then world name. Swapped whole; read on Pl3xMap's thread. */
    // An immutable map swapped whole: volatile is all the synchronisation it needs.
    @SuppressWarnings("java:S3077")
    private volatile Map<String, Map<String, List<Marker<?>>>> showing = Map.of();

    /** Icon keys Pl3xMap refused, still tried on each draw but not looked for in between. */
    private final Set<String> refused = ConcurrentHashMap.newKeySet();

    /**
     * Creates the provider, not yet looking for Pl3xMap.
     *
     * @param layers
     *            which layers to make
     * @param onReady
     *            told when Pl3xMap is found up, to have the latest picture applied
     */
    public Pl3xMapMapProvider(final MapLayers layers, final Consumer<MapProvider> onReady)
    {
        this(layers, onReady, Pl3xMap::api);
    }

    /**
     * Creates the provider with its lookup replaced, for tests.
     *
     * @param layers
     *            which layers to make
     * @param onReady
     *            told when Pl3xMap is found up
     * @param find
     *            finds Pl3xMap, or null when it is not loaded
     */
    Pl3xMapMapProvider(final MapLayers layers, final Consumer<MapProvider> onReady, final Supplier<Pl3xMap> find)
    {
        this.layers = layers;
        this.onReady = onReady;
        this.find = find;
    }

    @Override
    public String name()
    {
        return "Pl3xMap";
    }

    /** Up while Pl3xMap is enabled; it is not part-way through {@code /map reload}. */
    @Override
    public boolean ready()
    {
        final Pl3xMap map = pl3xmap;
        return (map != null) && map.isEnabled();
    }

    /** Looks for Pl3xMap, which has started before this plugin if it is going to. */
    @Override
    public void register()
    {
        pl3xmap = find.get();
        if (ready())
        {
            onReady.accept(this);
        }
    }

    @Override
    public void unregister()
    {
        pl3xmap = null;
    }

    /**
     * {@inheritDoc}
     *
     * <p>A world with no layer of ours yet, which is how every world looks after {@code /map
     * reload} and how a world loaded after startup looks, or an icon Pl3xMap has forgotten.
     */
    @Override
    public boolean lost()
    {
        final Pl3xMap map = pl3xmap;
        if ((map == null) || !map.isEnabled())
        {
            return false;
        }
        for (final String key : ICONS.values())
        {
            // One Pl3xMap refused stays refused, so missing it would redraw every look.
            if (!refused.contains(key) && !map.getIconRegistry().has(key))
            {
                return true;
            }
        }
        for (final World world : map.getWorldRegistry().values())
        {
            for (final String id : wanted())
            {
                if (!world.getLayerRegistry().has(id))
                {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void apply(final MapSnapshot snapshot)
    {
        final Pl3xMap map = pl3xmap;
        if ((map == null) || !map.isEnabled())
        {
            return;
        }
        registerIcons(map);
        showing = markers(snapshot);
        for (final World world : map.getWorldRegistry().values())
        {
            final Registry<Layer> registry = world.getLayerRegistry();
            for (final String id : wanted())
            {
                if (!registry.has(id))
                {
                    registry.register(id, new WorldLayer(id, world.getName()));
                }
            }
        }
    }

    @Override
    public void clear()
    {
        final Pl3xMap map = pl3xmap;
        showing = Map.of();
        refused.clear();
        if (map == null)
        {
            return;
        }
        for (final World world : map.getWorldRegistry().values())
        {
            final Registry<Layer> registry = world.getLayerRegistry();
            for (final String id : new String[] {MapText.GATES, MapText.RINGS, MapText.BEAMS, MapText.MIRRORS})
            {
                registry.unregister(id);
            }
        }
    }

    /**
     * The layer ids switched on.
     *
     * @return the ids
     */
    private List<String> wanted()
    {
        final List<String> ids = new ArrayList<>(4);
        if (layers.gates())
        {
            ids.add(MapText.GATES);
        }
        if (layers.rings())
        {
            ids.add(MapText.RINGS);
        }
        if (layers.beams())
        {
            ids.add(MapText.BEAMS);
        }
        if (layers.mirrors())
        {
            ids.add(MapText.MIRRORS);
        }
        return ids;
    }

    /**
     * Gives Pl3xMap any icon it does not have, which it writes into its web app.
     *
     * @param map
     *            Pl3xMap
     */
    private void registerIcons(final Pl3xMap map)
    {
        for (final Map.Entry<String, String> icon : ICONS.entrySet())
        {
            if (map.getIconRegistry().has(icon.getValue()))
            {
                continue;
            }
            try
            {
                map.getIconRegistry().register(icon.getValue(),
                    new IconImage(icon.getValue(), MapText.icon(icon.getKey()), "png"));
                refused.remove(icon.getValue());
            }
            catch (final IOException | RuntimeException e)
            {
                refused.add(icon.getValue());
                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "Failed to give Pl3xMap the icon " + icon.getKey(), e);
            }
        }
    }

    /**
     * Every layer's markers, by layer id and then world name.
     *
     * @param snapshot
     *            what to show
     * @return the markers, which Pl3xMap may read at any time
     */
    private static Map<String, Map<String, List<Marker<?>>>> markers(final MapSnapshot snapshot)
    {
        final Map<String, Map<String, List<Marker<?>>>> all = new HashMap<>();
        for (final GateMark gate : snapshot.gates().values())
        {
            final String detail = MapText.gate(gate);
            add(all, MapText.GATES, gate.world(), icon("gate:" + gate.id(), gate.x(), gate.z(),
                gate.open() ? MapText.GATE_OPEN_ICON : MapText.GATE_IDLE_ICON, gate.name(), detail));
            final Footprint f = gate.footprint();
            if (f != null)
            {
                add(all, MapText.GATES, gate.world(), Marker.rectangle("opening:" + gate.id(),
                    Point.of(f.minX(), f.minZ()), Point.of(f.maxX(), f.maxZ())).setOptions(
                        drawn(MapText.GATE_COLOUR, gate.name(), detail).fillColor(argb(MapText.GATE_COLOUR, MapText.FILL_OPACITY))));
            }
        }
        snapshot.gateLinks().values().forEach(line -> add(all, MapText.GATES, line.world(), line(line, MapText.GATE_COLOUR)));
        for (final RingMark ring : snapshot.rings().values())
        {
            add(all, MapText.RINGS, ring.world(), icon("ring:" + ring.id(), ring.x(), ring.z(), MapText.RINGS_ICON,
                ring.name(), MapText.ring(ring)));
        }
        snapshot.ringLinks().values().forEach(line -> add(all, MapText.RINGS, line.world(), line(line, MapText.RING_COLOUR)));
        for (final BeamMark beam : snapshot.beams().values())
        {
            add(all, MapText.BEAMS, beam.world(), icon(beam.id(), beam.x(), beam.z(), MapText.BEAM_ICON, beam.name(),
                MapText.beam(beam)));
        }
        for (final MirrorMark mirror : snapshot.mirrors().values())
        {
            // No line to anywhere: which room a mirror opens onto is chosen at it.
            add(all, MapText.MIRRORS, mirror.world(), icon(mirror.id(), mirror.x(), mirror.z(), MapText.MIRROR_ICON,
                mirror.name(), MapText.mirror(mirror)));
        }
        final Map<String, Map<String, List<Marker<?>>>> frozen = new HashMap<>();
        all.forEach((layer, byWorld) ->
        {
            final Map<String, List<Marker<?>>> worlds = new HashMap<>();
            byWorld.forEach((world, list) -> worlds.put(world, List.copyOf(list)));
            frozen.put(layer, Map.copyOf(worlds));
        });
        return Map.copyOf(frozen);
    }

    /**
     * Adds a marker to a layer in a world.
     *
     * @param all
     *            every layer's markers, by layer id and then world name
     * @param layer
     *            the layer id
     * @param world
     *            the world name
     * @param marker
     *            the marker, or null for none
     */
    private static void add(final Map<String, Map<String, List<Marker<?>>>> all, final String layer,
        final String world, final Marker<?> marker)
    {
        if (marker == null)
        {
            return;
        }
        all.computeIfAbsent(layer, k -> new HashMap<>()).computeIfAbsent(world, k -> new ArrayList<>()).add(marker);
    }

    /**
     * A point with one of our icons.
     *
     * @param key
     *            the marker's key, unique in its layer
     * @param x
     *            east-west
     * @param z
     *            north-south
     * @param icon
     *            its icon file
     * @param name
     *            what it is called
     * @param detail
     *            its description, as HTML
     * @return the marker, or null if Pl3xMap does not have its icon
     */
    private static Icon icon(final String key, final double x, final double z, final String icon,
        final String name, final String detail)
    {
        final String image = ICONS.get(icon);
        // Pl3xMap refuses an icon whose image it does not have, so a point whose icon would not register is left out.
        if (!Pl3xMap.api().getIconRegistry().has(image))
        {
            return null;
        }
        return Marker.icon(key, Point.of(x, z), image, MapText.ICON_SIZE).setAnchor(ANCHOR)
            .setOptions(Options.builder().tooltipContent(MapText.escape(name)).popupContent(detail));
    }

    /**
     * A straight line.
     *
     * @param line
     *            the line
     * @param colour
     *            its colour, as 0xRRGGBB
     * @return the marker
     */
    private static Polyline line(final LineMark line, final int colour)
    {
        return Marker.polyline("link:" + line.id(), Point.of(line.x1(), line.z1()), Point.of(line.x2(), line.z2()))
            .setOptions(drawn(colour, line.label(), MapText.heading(line.label())).fill(false));
    }

    /**
     * Options for something drawn in a colour.
     *
     * @param colour
     *            the colour, as 0xRRGGBB
     * @param name
     *            what it is called
     * @param detail
     *            its description, as HTML
     * @return the options, to finish
     */
    private static Options.Builder drawn(final int colour, final String name, final String detail)
    {
        return Options.builder().strokeColor(argb(colour, MapText.LINE_OPACITY)).strokeWeight(MapText.LINE_WEIGHT)
            .tooltipContent(MapText.escape(name)).popupContent(detail);
    }

    /**
     * A colour with an opacity, as Pl3xMap takes it.
     *
     * @param rgb
     *            the colour, as 0xRRGGBB
     * @param opacity
     *            from 0 to 1
     * @return the colour, as 0xAARRGGBB
     */
    static int argb(final int rgb, final double opacity)
    {
        return ((int) Math.round(opacity * 255) << 24) | (rgb & 0xFFFFFF);
    }

    /** One of our layers in one world, read by Pl3xMap whenever it writes that world's markers. */
    private final class WorldLayer extends Layer
    {
        /** The layer id. */
        private final String id;

        /** The world it is registered on. */
        private final String world;

        /**
         * Creates the layer.
         *
         * @param id
         *            the layer id
         * @param world
         *            the world's name
         */
        WorldLayer(final String id, final String world)
        {
            super(id, () -> MapText.label(id));
            this.id = id;
            this.world = world;
            setPriority(MapText.priority(id));
            setZIndex(MapText.priority(id));
        }

        @Override
        public Collection<Marker<?>> getMarkers()
        {
            return showing.getOrDefault(id, Map.of()).getOrDefault(world, List.of());
        }

        /** Pl3xMap's equality compares only the layer's settings, which are the same in every world. */
        @Override
        public boolean equals(final Object o)
        {
            return (o instanceof final WorldLayer other) && super.equals(o) && world.equals(other.world);
        }

        @Override
        public int hashCode()
        {
            return (31 * super.hashCode()) + world.hashCode();
        }
    }
}
