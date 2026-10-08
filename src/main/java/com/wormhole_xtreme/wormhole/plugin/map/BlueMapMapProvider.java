package com.wormhole_xtreme.wormhole.plugin.map;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;

import javax.imageio.ImageIO;

import com.flowpowered.math.vector.Vector3d;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
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
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Line;
import de.bluecolored.bluemap.api.math.Shape;

/**
 * Draws a {@link MapSnapshot} on BlueMap (#530).
 *
 * <p>The only class that names BlueMap's types, and only loaded once {@link MapMarkers} has found
 * BlueMap, so a server without it never meets them.
 *
 * <p>BlueMap throws away every marker a plugin made each time it reloads, and says so through
 * {@link BlueMapAPI#onEnable}. Each time, this asks for the latest picture to be drawn again from
 * scratch, through the same background draw as every other change.
 *
 * <p>Markers belong to a BlueMap map, not a world, and one world can have several maps (flat and
 * 3D, say), each with its own assets. Each map gets its own marker sets, with icons from its own
 * asset storage. BlueMap keeps a map's marker sets, and a set's markers, in concurrent maps, and
 * writes them out for the web app on its own thread, so they are changed here from the background
 * draw.
 */
public final class BlueMapMapProvider implements MapProvider
{
    /** Line opacity, as BlueMap takes it. */
    private static final float LINE_ALPHA = (float) MapText.LINE_OPACITY;

    /** Fill opacity, as BlueMap takes it. */
    private static final float FILL_ALPHA = (float) MapText.FILL_OPACITY;

    /** Where an icon is anchored: its centre. */
    private static final int ANCHOR = MapText.ICON_SIZE / 2;

    /** Every icon, written into each map's assets. */
    private static final String[] ICONS = {MapText.GATE_OPEN_ICON, MapText.GATE_IDLE_ICON,
        MapText.RINGS_ICON, MapText.BEAM_ICON, MapText.MIRROR_ICON};

    /** Which layers to make; one switched off is not made at all. */
    private final MapLayers layers;

    /** Told when BlueMap comes up, to have the latest picture applied. */
    private final Consumer<MapProvider> onReady;

    /** Hears BlueMap come up. Kept, since unregistering compares by identity. */
    private final Consumer<BlueMapAPI> enabled = this::attach;

    /** Hears BlueMap go. */
    private final Consumer<BlueMapAPI> disabled = api -> detach();

    /**
     * BlueMap's API and which time it came up, swapped as one so an apply can never set up
     * against one API while recording another's generation.
     *
     * @param api
     *            the API, or null while BlueMap is down
     * @param generation
     *            bumped each time BlueMap comes or goes
     */
    private record Session(BlueMapAPI api, int generation)
    {
    }

    /** The current session. */
    // An immutable record swapped whole: volatile is all the synchronisation it needs.
    @SuppressWarnings("java:S3077")
    private volatile Session session = new Session(null, 0);

    /** Counts sessions. */
    private final AtomicInteger generations = new AtomicInteger();

    /** The generation the state below belongs to, or -1 for none. Background draw only. */
    private int setUpFor = -1;

    /** The worlds drawn on this session, so one whose marks have all gone is emptied. Background draw only. */
    private final Set<String> worldsDrawn = new HashSet<>();

    /** This session's marker sets, by map id and then set id. Background draw only. */
    private final Map<String, Map<String, MarkerSet>> sets = new HashMap<>();

    /** This session's icon addresses, by map id and then icon file. Background draw only. */
    private final Map<String, Map<String, String>> icons = new HashMap<>();

    /**
     * Creates the provider, not yet listening to BlueMap.
     *
     * @param layers
     *            which layers to make
     * @param onReady
     *            told when BlueMap comes up, to have the latest picture applied
     */
    public BlueMapMapProvider(final MapLayers layers, final Consumer<MapProvider> onReady)
    {
        this.layers = layers;
        this.onReady = onReady;
    }

    @Override
    public String name()
    {
        return "BlueMap";
    }

    @Override
    public boolean ready()
    {
        return session.api() != null;
    }

    /** Starts listening for BlueMap; called back at once if it is already up. */
    @Override
    public void register()
    {
        BlueMapAPI.onEnable(enabled);
        BlueMapAPI.onDisable(disabled);
    }

    /** Stops listening for BlueMap. */
    @Override
    public void unregister()
    {
        BlueMapAPI.unregisterListener(enabled);
        BlueMapAPI.unregisterListener(disabled);
    }

    /**
     * BlueMap is up, with none of our markers. Only records it: the sets are made on the next
     * apply, which is off BlueMap's thread.
     *
     * @param api
     *            BlueMap's API
     */
    void attach(final BlueMapAPI api)
    {
        session = new Session(api, generations.incrementAndGet());
        onReady.accept(this);
    }

    /** BlueMap is going, and taking our markers with it. */
    void detach()
    {
        session = new Session(null, generations.incrementAndGet());
    }

    @Override
    public void apply(final MapSnapshot snapshot)
    {
        final Session now = session;
        final BlueMapAPI api = now.api();
        if (api == null)
        {
            return;
        }
        if (setUpFor != now.generation())
        {
            forget();
            removeOurSets(api);
            setUpFor = now.generation();
        }
        final Set<String> worlds = new HashSet<>(worldsDrawn);
        snapshot.gates().values().forEach(mark -> worlds.add(mark.world()));
        snapshot.rings().values().forEach(mark -> worlds.add(mark.world()));
        snapshot.beams().values().forEach(mark -> worlds.add(mark.world()));
        snapshot.mirrors().values().forEach(mark -> worlds.add(mark.world()));
        for (final String world : worlds)
        {
            final Optional<BlueMapWorld> mapped = api.getWorld(world);
            if (mapped.isEmpty())
            {
                continue;
            }
            worldsDrawn.add(world);
            for (final BlueMapMap map : mapped.get().getMaps())
            {
                draw(map, world, snapshot);
            }
        }
    }

    @Override
    public void clear()
    {
        final BlueMapAPI api = session.api();
        if (api != null)
        {
            removeOurSets(api);
        }
        setUpFor = -1;
        forget();
    }

    /** Forgets what this session made, so the next apply makes it all again. */
    private void forget()
    {
        worldsDrawn.clear();
        sets.clear();
        icons.clear();
    }

    /**
     * Takes every set of ours off every map, which also removes a layer switched off.
     *
     * @param api
     *            BlueMap's API
     */
    private static void removeOurSets(final BlueMapAPI api)
    {
        for (final BlueMapMap map : api.getMaps())
        {
            final Map<String, MarkerSet> onMap = map.getMarkerSets();
            onMap.remove(MapText.GATES);
            onMap.remove(MapText.RINGS);
            onMap.remove(MapText.BEAMS);
            onMap.remove(MapText.MIRRORS);
        }
    }

    /**
     * Brings one map up to date with one world's part of the picture.
     *
     * @param map
     *            the map
     * @param world
     *            the world it shows
     * @param snapshot
     *            the whole picture
     */
    private void draw(final BlueMapMap map, final String world, final MapSnapshot snapshot)
    {
        final Map<String, String> icon = iconsOf(map);
        if (layers.gates())
        {
            final Map<String, Marker> want = new HashMap<>();
            inWorld(snapshot.gates(), GateMark::world, world).forEach(gate -> addGate(want, gate, icon));
            inWorld(snapshot.gateLinks(), LineMark::world, world).forEach(
                line -> want.put("link:" + line.id(), line(line, MapText.GATE_COLOUR)));
            fill(set(map, MapText.GATES, MapText.GATES_LABEL, 10), want);
        }
        if (layers.rings())
        {
            final Map<String, Marker> want = new HashMap<>();
            inWorld(snapshot.rings(), RingMark::world, world).forEach(ring -> want.put("ring:" + ring.id(),
                point(ring.name(), MapText.ring(ring), ring.x(), ring.y(), ring.z(), icon.get(MapText.RINGS_ICON))));
            inWorld(snapshot.ringLinks(), LineMark::world, world).forEach(
                line -> want.put("link:" + line.id(), line(line, MapText.RING_COLOUR)));
            fill(set(map, MapText.RINGS, MapText.RINGS_LABEL, 11), want);
        }
        if (layers.beams())
        {
            final Map<String, Marker> want = new HashMap<>();
            inWorld(snapshot.beams(), BeamMark::world, world).forEach(beam -> want.put(beam.id(),
                point(beam.name(), MapText.beam(beam), beam.x(), beam.y(), beam.z(), icon.get(MapText.BEAM_ICON))));
            fill(set(map, MapText.BEAMS, MapText.BEAMS_LABEL, 12), want);
        }
        if (layers.mirrors())
        {
            // No line to anywhere: which room a mirror opens onto is chosen at it.
            final Map<String, Marker> want = new HashMap<>();
            inWorld(snapshot.mirrors(), MirrorMark::world, world).forEach(mirror -> want.put(mirror.id(),
                point(mirror.name(), MapText.mirror(mirror), mirror.x(), mirror.y(), mirror.z(),
                    icon.get(MapText.MIRROR_ICON))));
            fill(set(map, MapText.MIRRORS, MapText.MIRRORS_LABEL, 13), want);
        }
    }

    /**
     * The marks of one kind that are in one world.
     *
     * @param <T>
     *            the kind of mark
     * @param marks
     *            every mark of that kind, by id
     * @param worldOf
     *            a mark's world
     * @param world
     *            the world wanted
     * @return those in it
     */
    private static <T> List<T> inWorld(final Map<String, T> marks, final Function<T, String> worldOf,
        final String world)
    {
        return marks.values().stream().filter(mark -> world.equals(worldOf.apply(mark))).toList();
    }

    /**
     * A map's icon addresses, written into its assets the first time.
     *
     * @param map
     *            the map
     * @return where the web app finds each, by icon file
     */
    private Map<String, String> iconsOf(final BlueMapMap map)
    {
        final Map<String, String> known = icons.get(map.getId());
        if (known != null)
        {
            return known;
        }
        final Map<String, String> written = writeIcons(map);
        // Kept only when whole, so a map whose assets failed is tried again on the next draw.
        if (written.size() == ICONS.length)
        {
            icons.put(map.getId(), written);
        }
        return written;
    }

    /**
     * Writes the icons into a map's assets.
     *
     * @param map
     *            the map
     * @return where the web app finds each, by icon file; one missing uses BlueMap's own icon
     */
    private static Map<String, String> writeIcons(final BlueMapMap map)
    {
        final Map<String, String> written = new HashMap<>();
        final AssetStorage assets = map.getAssetStorage();
        for (final String file : ICONS)
        {
            final String name = "wormhole/" + file;
            try (OutputStream out = assets.writeAsset(name))
            {
                if (ImageIO.write(MapText.icon(file), "png", out))
                {
                    written.put(file, assets.getAssetUrl(name));
                }
            }
            catch (final IOException | RuntimeException e)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE,
                    "Failed to give BlueMap map " + map.getId() + " the icon " + file, e);
            }
        }
        return written;
    }

    /**
     * Finds or makes one of our sets on a map, putting it back if something took it off.
     *
     * @param map
     *            the map
     * @param id
     *            the set id
     * @param label
     *            the set's name in the web app
     * @param sorting
     *            where the set sits in the web app's list
     * @return the set
     */
    private MarkerSet set(final BlueMapMap map, final String id, final String label, final int sorting)
    {
        final MarkerSet set = sets.computeIfAbsent(map.getId(), k -> new HashMap<>()).computeIfAbsent(id, k ->
        {
            final MarkerSet made = new MarkerSet(label, true, false);
            made.setSorting(sorting);
            return made;
        });
        if (map.getMarkerSets().get(id) != set)
        {
            map.getMarkerSets().put(id, set);
        }
        return set;
    }

    /**
     * Makes a set hold exactly these markers, touching only what differs.
     *
     * @param set
     *            the set
     * @param want
     *            its markers, by id
     */
    private static void fill(final MarkerSet set, final Map<String, Marker> want)
    {
        set.getMarkers().keySet().removeIf(id -> !want.containsKey(id));
        for (final Map.Entry<String, Marker> marker : want.entrySet())
        {
            if (!marker.getValue().equals(set.get(marker.getKey())))
            {
                set.put(marker.getKey(), marker.getValue());
            }
        }
    }

    /**
     * Adds a gate: a point, and its opening as an area when it has one.
     *
     * @param want
     *            the gates set's markers, by id
     * @param gate
     *            the gate
     * @param icon
     *            this map's icon addresses
     */
    private static void addGate(final Map<String, Marker> want, final GateMark gate, final Map<String, String> icon)
    {
        final String detail = MapText.gate(gate);
        want.put("gate:" + gate.id(), point(gate.name(), detail, gate.x(), gate.y(), gate.z(),
            icon.get(gate.open() ? MapText.GATE_OPEN_ICON : MapText.GATE_IDLE_ICON)));
        final Footprint f = gate.footprint();
        if (f == null)
        {
            return;
        }
        final ExtrudeMarker opening = new ExtrudeMarker(MapText.escape(gate.name()),
            Shape.createRect(f.minX(), f.minZ(), f.maxX(), f.maxZ()), (float) f.minY(), (float) f.maxY());
        opening.setDetail(detail);
        opening.setLineWidth(MapText.LINE_WEIGHT);
        opening.setLineColor(new Color(MapText.GATE_COLOUR, LINE_ALPHA));
        opening.setFillColor(new Color(MapText.GATE_COLOUR, FILL_ALPHA));
        want.put("opening:" + gate.id(), opening);
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
    private static LineMarker line(final LineMark line, final int colour)
    {
        final LineMarker drawn = new LineMarker(MapText.escape(line.label()), new Line(
            new Vector3d(line.x1(), line.y1(), line.z1()), new Vector3d(line.x2(), line.y2(), line.z2())));
        drawn.setDetail(MapText.heading(line.label()));
        drawn.setLineWidth(MapText.LINE_WEIGHT);
        drawn.setLineColor(new Color(colour, LINE_ALPHA));
        return drawn;
    }

    /**
     * A point with one of our icons.
     *
     * @param name
     *            what it is called
     * @param detail
     *            its description, as HTML
     * @param x
     *            east-west
     * @param y
     *            height
     * @param z
     *            north-south
     * @param icon
     *            its icon's address, or null for BlueMap's own
     * @return the marker
     */
    private static POIMarker point(final String name, final String detail, final double x, final double y,
        final double z, final String icon)
    {
        // Escaped: BlueMap's web app puts a label in the page as HTML when it has no detail.
        final POIMarker point = new POIMarker(MapText.escape(name), new Vector3d(x, y, z));
        point.setDetail(detail);
        if (icon != null)
        {
            point.setIcon(icon, ANCHOR, ANCHOR);
        }
        return point;
    }
}
