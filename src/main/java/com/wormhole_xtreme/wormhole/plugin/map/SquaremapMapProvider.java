package com.wormhole_xtreme.wormhole.plugin.map;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.plugin.map.MapSnapshot.BeamMark;
import com.wormhole_xtreme.wormhole.plugin.map.MapSnapshot.Footprint;
import com.wormhole_xtreme.wormhole.plugin.map.MapSnapshot.GateMark;
import com.wormhole_xtreme.wormhole.plugin.map.MapSnapshot.LineMark;
import com.wormhole_xtreme.wormhole.plugin.map.MapSnapshot.MirrorMark;
import com.wormhole_xtreme.wormhole.plugin.map.MapSnapshot.RingMark;

import xyz.jpenilla.squaremap.api.BukkitAdapter;
import xyz.jpenilla.squaremap.api.Key;
import xyz.jpenilla.squaremap.api.LayerProvider;
import xyz.jpenilla.squaremap.api.MapWorld;
import xyz.jpenilla.squaremap.api.Point;
import xyz.jpenilla.squaremap.api.Registry;
import xyz.jpenilla.squaremap.api.Squaremap;
import xyz.jpenilla.squaremap.api.SquaremapProvider;
import xyz.jpenilla.squaremap.api.WorldIdentifier;
import xyz.jpenilla.squaremap.api.marker.Marker;
import xyz.jpenilla.squaremap.api.marker.MarkerOptions;

/**
 * Draws a {@link MapSnapshot} on squaremap (#531).
 *
 * <p>The only class that names squaremap's types, and only loaded once {@link MapMarkers} has
 * found squaremap, so a server without it never meets them.
 *
 * <p>squaremap pulls: each world has a registry of layer providers, and squaremap asks each for
 * its markers whenever it writes the map's marker file, on its own thread. So drawing here is
 * only building the markers and swapping them in whole; there is nothing to diff. A layer is
 * registered on every world squaremap maps, once; squaremap keeps its layer registries across
 * its own reloads, and one registered on a world that loads later is found missing by
 * {@link #lost()} and registered on the next draw.
 */
public final class SquaremapMapProvider implements MapProvider
{
    /** Every icon, registered with squaremap by these keys. */
    private static final Map<String, String> ICONS = Map.of(
        MapText.GATE_OPEN_ICON, "wormhole_gate_open",
        MapText.GATE_IDLE_ICON, "wormhole_gate_idle",
        MapText.RINGS_ICON, "wormhole_rings",
        MapText.BEAM_ICON, "wormhole_beam",
        MapText.MIRROR_ICON, "wormhole_mirror");

    /** Which layers to make; one switched off is not registered at all. */
    private final MapLayers layers;

    /** Told when squaremap is found up, to have the latest picture applied. */
    private final Consumer<MapProvider> onReady;

    /** Finds squaremap; a seam, since squaremap's own lookup is static. */
    private final Supplier<Squaremap> find;

    /** The Bukkit name of the world a squaremap world maps; a seam, since squaremap's own lookup is static. */
    private final Function<MapWorld, String> worldName;

    /** squaremap, once found. */
    // An interface reference swapped whole: volatile is all the synchronisation it needs.
    @SuppressWarnings("java:S3077")
    private volatile Squaremap squaremap = null;

    /** What each layer shows, by layer id and then world name. Swapped whole; read on squaremap's thread. */
    // An immutable map swapped whole: volatile is all the synchronisation it needs.
    @SuppressWarnings("java:S3077")
    private volatile Map<String, Map<String, List<Marker>>> showing = Map.of();

    /** World names already worked out, by squaremap world. */
    private final Map<WorldIdentifier, String> names = new ConcurrentHashMap<>();

    /** Icon keys squaremap refused, still tried on each draw but not looked for in between. */
    private final Set<String> refused = ConcurrentHashMap.newKeySet();

    /**
     * Creates the provider, not yet looking for squaremap.
     *
     * @param layers
     *            which layers to make
     * @param onReady
     *            told when squaremap is found up, to have the latest picture applied
     */
    public SquaremapMapProvider(final MapLayers layers, final Consumer<MapProvider> onReady)
    {
        this(layers, onReady, SquaremapProvider::get, world -> BukkitAdapter.bukkitWorld(world).getName());
    }

    /**
     * Creates the provider with its lookups replaced, for tests.
     *
     * @param layers
     *            which layers to make
     * @param onReady
     *            told when squaremap is found up
     * @param find
     *            finds squaremap, throwing IllegalStateException when it is not loaded
     * @param worldName
     *            the Bukkit name of a squaremap world
     */
    SquaremapMapProvider(final MapLayers layers, final Consumer<MapProvider> onReady,
        final Supplier<Squaremap> find, final Function<MapWorld, String> worldName)
    {
        this.layers = layers;
        this.onReady = onReady;
        this.find = find;
        this.worldName = worldName;
    }

    @Override
    public String name()
    {
        return "squaremap";
    }

    @Override
    public boolean ready()
    {
        return squaremap != null;
    }

    /** Looks for squaremap, which has started before this plugin if it is going to. */
    @Override
    public void register()
    {
        try
        {
            squaremap = find.get();
        }
        catch (final IllegalStateException e)
        {
            // Installed but not started: MapMarkers has already said so.
            return;
        }
        onReady.accept(this);
    }

    @Override
    public void unregister()
    {
        squaremap = null;
    }

    /**
     * {@inheritDoc}
     *
     * <p>A world squaremap maps that has no layer of ours yet, which is how a world loaded after
     * startup looks, or an icon squaremap has lost.
     */
    @Override
    public boolean lost()
    {
        final Squaremap map = squaremap;
        if (map == null)
        {
            return false;
        }
        for (final String key : ICONS.values())
        {
            // One squaremap refused stays refused, so missing it would redraw every look.
            if (!refused.contains(key) && !map.iconRegistry().hasEntry(Key.of(key)))
            {
                return true;
            }
        }
        for (final MapWorld world : map.mapWorlds())
        {
            // apply() skips a world Bukkit no longer has, so it would be found missing forever.
            if (nameOf(world) == null)
            {
                continue;
            }
            for (final String id : wanted())
            {
                if (!world.layerRegistry().hasEntry(Key.of(id)))
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
        final Squaremap map = squaremap;
        if (map == null)
        {
            return;
        }
        registerIcons(map);
        showing = markers(snapshot);
        for (final MapWorld world : map.mapWorlds())
        {
            final String name = nameOf(world);
            if (name == null)
            {
                continue;
            }
            final Registry<LayerProvider> registry = world.layerRegistry();
            for (final String id : wanted())
            {
                final Key key = Key.of(id);
                if (!registry.hasEntry(key))
                {
                    registry.register(key, new Layer(id, name));
                }
            }
        }
    }

    @Override
    public void clear()
    {
        final Squaremap map = squaremap;
        showing = Map.of();
        names.clear();
        refused.clear();
        if (map == null)
        {
            return;
        }
        for (final MapWorld world : map.mapWorlds())
        {
            final Registry<LayerProvider> registry = world.layerRegistry();
            for (final String id : new String[] {MapText.GATES, MapText.RINGS, MapText.BEAMS, MapText.MIRRORS})
            {
                final Key key = Key.of(id);
                if (registry.hasEntry(key))
                {
                    registry.unregister(key);
                }
            }
        }
    }

    /**
     * The layer ids switched on.
     *
     * @return the ids, in the order the map lists them
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
     * The Bukkit name of a squaremap world, worked out once.
     *
     * @param world
     *            the squaremap world
     * @return its name, or null if Bukkit no longer has it
     */
    private String nameOf(final MapWorld world)
    {
        try
        {
            return names.computeIfAbsent(world.identifier(), id -> worldName.apply(world));
        }
        catch (final RuntimeException e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE,
                "squaremap world " + world.identifier().asString() + " has no Bukkit world", e);
            return null;
        }
    }

    /**
     * Gives squaremap any icon it does not have yet.
     *
     * @param map
     *            squaremap
     */
    private void registerIcons(final Squaremap map)
    {
        for (final Map.Entry<String, String> icon : ICONS.entrySet())
        {
            final Key key = Key.of(icon.getValue());
            if (map.iconRegistry().hasEntry(key))
            {
                continue;
            }
            try
            {
                map.iconRegistry().register(key, MapText.icon(icon.getKey()));
                refused.remove(icon.getValue());
            }
            catch (final IOException | RuntimeException e)
            {
                refused.add(icon.getValue());
                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "Failed to give squaremap the icon " + icon.getKey(), e);
            }
        }
    }

    /**
     * Every layer's markers, by layer id and then world name.
     *
     * @param snapshot
     *            what to show
     * @return the markers, which squaremap may read at any time
     */
    private static Map<String, Map<String, List<Marker>>> markers(final MapSnapshot snapshot)
    {
        final Map<String, Map<String, List<Marker>>> all = new HashMap<>();
        for (final GateMark gate : snapshot.gates().values())
        {
            final String detail = MapText.gate(gate);
            add(all, MapText.GATES, gate.world(), icon(gate.x(), gate.z(),
                gate.open() ? MapText.GATE_OPEN_ICON : MapText.GATE_IDLE_ICON, gate.name(), detail));
            final Footprint f = gate.footprint();
            if (f != null)
            {
                add(all, MapText.GATES, gate.world(), Marker.rectangle(Point.of(f.minX(), f.minZ()),
                    Point.of(f.maxX(), f.maxZ())).markerOptions(lines(MapText.GATE_COLOUR, gate.name(), detail)
                        .fillColor(new Color(MapText.GATE_COLOUR)).fillOpacity(MapText.FILL_OPACITY)));
            }
        }
        snapshot.gateLinks().values().forEach(line -> add(all, MapText.GATES, line.world(), line(line, MapText.GATE_COLOUR)));
        for (final RingMark ring : snapshot.rings().values())
        {
            add(all, MapText.RINGS, ring.world(), icon(ring.x(), ring.z(), MapText.RINGS_ICON, ring.name(),
                MapText.ring(ring)));
        }
        snapshot.ringLinks().values().forEach(line -> add(all, MapText.RINGS, line.world(), line(line, MapText.RING_COLOUR)));
        for (final BeamMark beam : snapshot.beams().values())
        {
            add(all, MapText.BEAMS, beam.world(), icon(beam.x(), beam.z(), MapText.BEAM_ICON, beam.name(),
                MapText.beam(beam)));
        }
        for (final MirrorMark mirror : snapshot.mirrors().values())
        {
            // No line to anywhere: which room a mirror opens onto is chosen at it.
            add(all, MapText.MIRRORS, mirror.world(), icon(mirror.x(), mirror.z(), MapText.MIRROR_ICON,
                mirror.name(), MapText.mirror(mirror)));
        }
        final Map<String, Map<String, List<Marker>>> frozen = new HashMap<>();
        all.forEach((layer, byWorld) ->
        {
            final Map<String, List<Marker>> worlds = new HashMap<>();
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
     *            the marker
     */
    private static void add(final Map<String, Map<String, List<Marker>>> all, final String layer,
        final String world, final Marker marker)
    {
        all.computeIfAbsent(layer, k -> new HashMap<>()).computeIfAbsent(world, k -> new ArrayList<>()).add(marker);
    }

    /**
     * A point with one of our icons.
     *
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
     * @return the marker
     */
    private static Marker icon(final double x, final double z, final String icon, final String name,
        final String detail)
    {
        return Marker.icon(Point.of(x, z), Key.of(ICONS.get(icon)), MapText.ICON_SIZE)
            .markerOptions(MarkerOptions.builder().hoverTooltip(MapText.escape(name)).clickTooltip(detail));
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
    private static Marker line(final LineMark line, final int colour)
    {
        return Marker.polyline(Point.of(line.x1(), line.z1()), Point.of(line.x2(), line.z2()))
            .markerOptions(lines(colour, line.label(), MapText.heading(line.label())).fill(false));
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
    private static MarkerOptions.Builder lines(final int colour, final String name, final String detail)
    {
        return MarkerOptions.builder().strokeColor(new Color(colour)).strokeWeight(MapText.LINE_WEIGHT)
            .strokeOpacity(MapText.LINE_OPACITY).hoverTooltip(MapText.escape(name)).clickTooltip(detail);
    }

    /** One of our layers in one world, read by squaremap whenever it writes that world's markers. */
    private final class Layer implements LayerProvider
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
        Layer(final String id, final String world)
        {
            this.id = id;
            this.world = world;
        }

        @Override
        public String getLabel()
        {
            return MapText.label(id);
        }

        @Override
        public int layerPriority()
        {
            return MapText.priority(id);
        }

        @Override
        public Collection<Marker> getMarkers()
        {
            return showing.getOrDefault(id, Map.of()).getOrDefault(world, List.of());
        }
    }
}
