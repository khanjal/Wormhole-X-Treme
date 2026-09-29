package com.wormhole_xtreme.wormhole.plugin.map;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;

import org.dynmap.DynmapCommonAPI;
import org.dynmap.DynmapCommonAPIListener;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.GenericMarker;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerSet;
import org.dynmap.markers.PolyLineMarker;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.plugin.map.MapSnapshot.BeamMark;
import com.wormhole_xtreme.wormhole.plugin.map.MapSnapshot.Footprint;
import com.wormhole_xtreme.wormhole.plugin.map.MapSnapshot.GateMark;
import com.wormhole_xtreme.wormhole.plugin.map.MapSnapshot.LineMark;
import com.wormhole_xtreme.wormhole.plugin.map.MapSnapshot.MirrorMark;
import com.wormhole_xtreme.wormhole.plugin.map.MapSnapshot.RingMark;

/**
 * Draws a {@link MapSnapshot} on Dynmap (#236).
 *
 * <p>The only class that names Dynmap's types, and only loaded once {@link MapMarkers} has
 * found Dynmap, so a server without it never meets them.
 *
 * <p>Everything is created non-persistent: Dynmap forgets it on restart, and the picture is
 * rebuilt from the plugin's own state anyway. Only what changed since the last picture is
 * touched, because each marker change is a message to every open browser.
 */
public final class DynmapMapProvider implements MapProvider
{
    /** Marker set id for gates, their openings and the lines between dialled pairs. */
    static final String GATES = "wormhole.gates";

    /** Marker set id for ring ends and the lines between pairs. */
    static final String RINGS = "wormhole.rings";

    /** Marker set id for public beam destinations. */
    static final String BEAMS = "wormhole.beams";

    /** Marker set id for quantum mirrors. */
    static final String MIRRORS = "wormhole.mirrors";

    /** Colour of a gate's opening and of a dialled pair's line: the logo's horizon cyan. */
    private static final int GATE_COLOUR = 0x37B0D8;

    /** Colour of the line between a ring pair's ends: the logo's ring stone. */
    private static final int RING_COLOUR = 0x9AA5B1;

    /** Line opacity for everything drawn. */
    private static final double LINE_OPACITY = 0.8;

    /** Fill opacity for a gate's opening. */
    private static final double FILL_OPACITY = 0.35;

    /** Line weight for everything drawn. */
    private static final int LINE_WEIGHT = 3;

    /** A line break in a marker's HTML description. */
    private static final String BREAK = "<br/>";

    /** Asked to push the latest picture when Dynmap comes up. */
    private final Runnable onReady;

    /** Which layers to make; one switched off is not made at all. */
    private final MapLayers layers;

    /** Dynmap's marker API while it is up, null otherwise. */
    private volatile MarkerAPI api = null;

    /** Bumped each time Dynmap comes up, so the next apply knows to set up its layers again. */
    private final AtomicInteger generation = new AtomicInteger();

    /** The generation the layers below were set up for, or -1 for none. */
    private int setUpFor = -1;

    /** The Dynmap listener, created on {@link #register()}. */
    private Hook hook = null;

    private MarkerSet gateSet = null;
    private MarkerSet ringSet = null;
    private MarkerSet beamSet = null;
    private MarkerSet mirrorSet = null;
    private MarkerIcon gateIcon = null;
    private MarkerIcon ringIcon = null;
    private MarkerIcon beamIcon = null;
    private MarkerIcon mirrorIcon = null;

    /** What was last drawn, by id, so an unchanged mark is left alone. */
    private final Map<String, GateMark> drawnGates = new HashMap<>();
    private final Map<String, LineMark> drawnGateLinks = new HashMap<>();
    private final Map<String, RingMark> drawnRings = new HashMap<>();
    private final Map<String, LineMark> drawnRingLinks = new HashMap<>();
    private final Map<String, BeamMark> drawnBeams = new HashMap<>();
    private final Map<String, MirrorMark> drawnMirrors = new HashMap<>();

    /**
     * Creates the provider, not yet listening to Dynmap.
     *
     * @param layers
     *            which layers to make
     * @param onReady
     *            run when Dynmap comes up, to have the latest picture applied
     */
    public DynmapMapProvider(final MapLayers layers, final Runnable onReady)
    {
        this.layers = layers;
        this.onReady = onReady;
    }

    @Override
    public String name()
    {
        return "Dynmap";
    }

    /** Starts listening for Dynmap; called back at once if it is already up. */
    public void register()
    {
        hook = new Hook(this);
        DynmapCommonAPIListener.register(hook);
    }

    /** Stops listening for Dynmap. */
    public void unregister()
    {
        final Hook h = hook;
        hook = null;
        if (h != null)
        {
            DynmapCommonAPIListener.unregister(h);
        }
    }

    /**
     * Dynmap's markers are ready. Only records them: the layers are made on the next apply,
     * which is off the main thread.
     *
     * @param markers
     *            the marker API, or null if Dynmap has markers turned off
     */
    void attach(final MarkerAPI markers)
    {
        api = markers;
        generation.incrementAndGet();
        if (markers != null)
        {
            onReady.run();
        }
    }

    /** Dynmap has gone, and taken our layers with it. */
    void detach()
    {
        api = null;
        generation.incrementAndGet();
    }

    @Override
    public void apply(final MapSnapshot snapshot)
    {
        final MarkerAPI markers = api;
        if (markers == null)
        {
            return;
        }
        final int current = generation.get();
        if (setUpFor != current)
        {
            setUp(markers);
            setUpFor = current;
        }
        if (gateSet != null)
        {
            sync(snapshot.gates(), drawnGates, this::removeGate, this::drawGate);
            sync(snapshot.gateLinks(), drawnGateLinks, id -> removeLine(gateSet, id),
                (id, line) -> drawLine(gateSet, line, GATE_COLOUR));
        }
        if (ringSet != null)
        {
            sync(snapshot.rings(), drawnRings, id -> remove(ringSet.findMarker(id)), this::drawRing);
            sync(snapshot.ringLinks(), drawnRingLinks, id -> removeLine(ringSet, id),
                (id, line) -> drawLine(ringSet, line, RING_COLOUR));
        }
        if (beamSet != null)
        {
            sync(snapshot.beams(), drawnBeams, id -> remove(beamSet.findMarker(id)), this::drawBeam);
        }
        if (mirrorSet != null)
        {
            sync(snapshot.mirrors(), drawnMirrors, id -> remove(mirrorSet.findMarker(id)), this::drawMirror);
        }
    }

    @Override
    public void clear()
    {
        final MarkerAPI markers = api;
        if (markers != null)
        {
            deleteSet(markers, GATES);
            deleteSet(markers, RINGS);
            deleteSet(markers, BEAMS);
            deleteSet(markers, MIRRORS);
        }
        setUpFor = -1;
        forgetDrawn();
    }

    /**
     * Makes the layers asked for, replacing any left over from before a reload, and removes
     * any that are switched off.
     *
     * @param markers
     *            Dynmap's marker API
     */
    private void setUp(final MarkerAPI markers)
    {
        forgetDrawn();
        gateSet = layer(markers, layers.gates(), GATES, "Stargates", 10);
        ringSet = layer(markers, layers.rings(), RINGS, "Transport rings", 11);
        beamSet = layer(markers, layers.beams(), BEAMS, "Beam destinations", 12);
        mirrorSet = layer(markers, layers.mirrors(), MIRRORS, "Quantum mirrors", 13);
        gateIcon = (gateSet == null) ? null : icon(markers, "wormhole_gate", "Stargate", "gate.png", "portal");
        ringIcon = (ringSet == null) ? null : icon(markers, "wormhole_rings", "Transport rings", "rings.png", "star");
        beamIcon = (beamSet == null) ? null : icon(markers, "wormhole_beam", "Beam destination", "beam.png", "pin");
        mirrorIcon = (mirrorSet == null) ? null : icon(markers, "wormhole_mirror", "Quantum mirror", "mirror.png", "sign");
    }

    /**
     * Makes one layer fresh, or removes it if it is switched off.
     *
     * @param markers
     *            Dynmap's marker API
     * @param wanted
     *            whether the layer is switched on
     * @param id
     *            the set id
     * @param label
     *            the layer's name in the map's layer list
     * @param priority
     *            where the layer sits in that list
     * @return the set, or null if it is switched off or Dynmap would not make it
     */
    private static MarkerSet layer(final MarkerAPI markers, final boolean wanted, final String id,
        final String label, final int priority)
    {
        deleteSet(markers, id);
        if (!wanted)
        {
            return null;
        }
        final MarkerSet set = markers.createMarkerSet(id, label, null, false);
        if (set == null)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING,
                "Dynmap would not make the " + label + " layer; it is not shown.");
            return null;
        }
        set.setLayerPriority(priority);
        set.setHideByDefault(false);
        return set;
    }

    /** Forgets what was drawn, so the next apply draws everything. */
    private void forgetDrawn()
    {
        drawnGates.clear();
        drawnGateLinks.clear();
        drawnRings.clear();
        drawnRingLinks.clear();
        drawnBeams.clear();
        drawnMirrors.clear();
    }

    /**
     * Deletes a marker set, if there is one.
     *
     * @param markers
     *            Dynmap's marker API
     * @param id
     *            the set id
     */
    private static void deleteSet(final MarkerAPI markers, final String id)
    {
        final MarkerSet old = markers.getMarkerSet(id);
        if (old != null)
        {
            old.deleteMarkerSet();
        }
    }

    /**
     * Finds or makes one of this plugin's icons, falling back to one of Dynmap's own.
     *
     * @param markers
     *            Dynmap's marker API
     * @param id
     *            the icon id
     * @param title
     *            the icon's name
     * @param file
     *            its image, under {@code dynmap/} in the jar
     * @param fallback
     *            a Dynmap built-in icon to use instead
     * @return the icon
     */
    private static MarkerIcon icon(final MarkerAPI markers, final String id, final String title,
        final String file, final String fallback)
    {
        MarkerIcon icon = markers.getMarkerIcon(id);
        if (icon == null)
        {
            try (InputStream in = DynmapMapProvider.class.getResourceAsStream("/dynmap/" + file))
            {
                if (in != null)
                {
                    icon = markers.createMarkerIcon(id, title, in);
                }
            }
            catch (final IOException e)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "Failed to read Dynmap icon " + file, e);
            }
        }
        return (icon != null) ? icon : markers.getMarkerIcon(fallback);
    }

    /**
     * Brings one kind of mark up to date: removes what went or changed, then draws what is
     * new or changed, and leaves the rest alone.
     *
     * @param <T>
     *            the kind of mark
     * @param want
     *            what should be drawn, by id
     * @param drawn
     *            what is drawn, by id; updated to match
     * @param remove
     *            removes the mark with an id
     * @param draw
     *            draws a mark
     */
    private static <T> void sync(final Map<String, T> want, final Map<String, T> drawn,
        final Consumer<String> remove, final BiConsumer<String, T> draw)
    {
        for (final Map.Entry<String, T> was : drawn.entrySet())
        {
            if (!was.getValue().equals(want.get(was.getKey())))
            {
                remove.accept(was.getKey());
            }
        }
        for (final Map.Entry<String, T> now : want.entrySet())
        {
            if (!now.getValue().equals(drawn.get(now.getKey())))
            {
                draw.accept(now.getKey(), now.getValue());
            }
        }
        drawn.clear();
        drawn.putAll(want);
    }

    /**
     * Deletes a marker, if it is there.
     *
     * @param marker
     *            the marker, or null
     */
    private static void remove(final GenericMarker marker)
    {
        if (marker != null)
        {
            marker.deleteMarker();
        }
    }

    /**
     * Removes a gate's point and its opening.
     *
     * @param id
     *            the gate's id
     */
    private void removeGate(final String id)
    {
        remove(gateSet.findMarker(id));
        remove(gateSet.findAreaMarker(id));
    }

    /**
     * Removes a line.
     *
     * @param set
     *            the set it is in
     * @param id
     *            its id
     */
    private static void removeLine(final MarkerSet set, final String id)
    {
        remove(set.findPolyLineMarker(id));
    }

    /**
     * Draws a gate as a point, and its opening as an area when it has one.
     *
     * @param id
     *            the gate's id
     * @param gate
     *            the gate's mark
     */
    private void drawGate(final String id, final GateMark gate)
    {
        final StringBuilder html = new StringBuilder(heading(gate.name()));
        if (gate.network() != null)
        {
            html.append(BREAK).append("Network: ").append(escape(gate.network()));
        }
        if (gate.owner() != null)
        {
            html.append(BREAK).append("Owner: ").append(escape(gate.owner()));
        }
        final String description = html.toString();
        final Marker point = gateSet.createMarker(id, gate.name(), false, gate.world(),
            gate.x(), gate.y(), gate.z(), gateIcon, false);
        if (point != null)
        {
            point.setDescription(description);
        }
        final Footprint f = gate.footprint();
        if (f == null)
        {
            return;
        }
        final AreaMarker area = gateSet.createAreaMarker(id, gate.name(), false, gate.world(),
            new double[] {f.minX(), f.maxX(), f.maxX(), f.minX()},
            new double[] {f.minZ(), f.minZ(), f.maxZ(), f.maxZ()}, false);
        if (area != null)
        {
            area.setRangeY(f.maxY(), f.minY());
            area.setLineStyle(LINE_WEIGHT, LINE_OPACITY, GATE_COLOUR);
            area.setFillStyle(FILL_OPACITY, GATE_COLOUR);
            area.setDescription(description);
        }
    }

    /**
     * Draws a straight line.
     *
     * @param set
     *            the set to draw it in
     * @param line
     *            the line
     * @param colour
     *            its colour, as 0xRRGGBB
     */
    private static void drawLine(final MarkerSet set, final LineMark line, final int colour)
    {
        final PolyLineMarker drawn = set.createPolyLineMarker(line.id(), line.label(), false, line.world(),
            new double[] {line.x1(), line.x2()}, new double[] {line.y1(), line.y2()},
            new double[] {line.z1(), line.z2()}, false);
        if (drawn != null)
        {
            drawn.setLineStyle(LINE_WEIGHT, LINE_OPACITY, colour);
            drawn.setDescription(heading(line.label()));
        }
    }

    /**
     * Draws one end of a ring pair.
     *
     * @param id
     *            the end's id
     * @param ring
     *            the end's mark
     */
    private void drawRing(final String id, final RingMark ring)
    {
        final StringBuilder html = new StringBuilder(heading(ring.name()));
        html.append(BREAK).append(escape(ring.pair()));
        if (ring.owner() != null)
        {
            html.append(BREAK).append("Owner: ").append(escape(ring.owner()));
        }
        final Marker point = ringSet.createMarker(id, ring.name(), false, ring.world(),
            ring.x(), ring.y(), ring.z(), ringIcon, false);
        if (point != null)
        {
            point.setDescription(html.toString());
        }
    }

    /**
     * Draws a public beam destination.
     *
     * @param id
     *            the destination's id
     * @param beam
     *            its mark
     */
    private void drawBeam(final String id, final BeamMark beam)
    {
        final Marker point = beamSet.createMarker(id, beam.name(), false, beam.world(),
            beam.x(), beam.y(), beam.z(), beamIcon, false);
        if (point != null)
        {
            point.setDescription(heading(beam.name()) + BREAK + "Beam destination");
        }
    }

    /**
     * Draws a quantum mirror. No line to anywhere: which room a mirror opens onto is chosen at it.
     *
     * @param id
     *            the mirror's id
     * @param mirror
     *            its mark
     */
    private void drawMirror(final String id, final MirrorMark mirror)
    {
        final Marker point = mirrorSet.createMarker(id, mirror.name(), false, mirror.world(),
            mirror.x(), mirror.y(), mirror.z(), mirrorIcon, false);
        if (point != null)
        {
            point.setDescription(heading(mirror.name()) + BREAK + "Quantum mirror");
        }
    }

    /**
     * A name in bold, escaped.
     *
     * @param text
     *            the name
     * @return the markup
     */
    private static String heading(final String text)
    {
        return "<b>" + escape(text) + "</b>";
    }

    /**
     * Escapes text for Dynmap's HTML descriptions, so a gate or player name cannot add markup.
     *
     * @param text
     *            the text
     * @return it, safe to put in HTML
     */
    static String escape(final String text)
    {
        final StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++)
        {
            final char c = text.charAt(i);
            switch (c)
            {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * Hears Dynmap come and go. Its own class because Dynmap's listener is a class to extend,
     * and naming it is what needs Dynmap present.
     */
    private static final class Hook extends DynmapCommonAPIListener
    {
        /** Told when Dynmap comes and goes. */
        private final DynmapMapProvider owner;

        /**
         * Creates the hook.
         *
         * @param owner
         *            told when Dynmap comes and goes
         */
        Hook(final DynmapMapProvider owner)
        {
            this.owner = owner;
        }

        @Override
        public void apiEnabled(final DynmapCommonAPI dynmap)
        {
            if (!dynmap.markerAPIInitialized())
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING,
                    "dynmap-enabled is set, but Dynmap's markers are turned off; nothing is shown on it.");
                owner.detach();
                return;
            }
            owner.attach(dynmap.getMarkerAPI());
        }

        @Override
        public void apiDisabled(final DynmapCommonAPI dynmap)
        {
            owner.detach();
        }
    }
}
