package com.wormhole_xtreme.wormhole.plugin.map;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;

import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateNetwork;
import com.wormhole_xtreme.wormhole.model.beam.BeamDestination;
import com.wormhole_xtreme.wormhole.model.beam.BeamPoint;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorBlock;
import com.wormhole_xtreme.wormhole.model.mirror.QuantumMirror;
import com.wormhole_xtreme.wormhole.model.ring.Ring;
import com.wormhole_xtreme.wormhole.model.ring.RingPair;
import com.wormhole_xtreme.wormhole.plugin.map.MapSnapshot.BeamMark;
import com.wormhole_xtreme.wormhole.plugin.map.MapSnapshot.Footprint;
import com.wormhole_xtreme.wormhole.plugin.map.MapSnapshot.GateMark;
import com.wormhole_xtreme.wormhole.plugin.map.MapSnapshot.LineMark;
import com.wormhole_xtreme.wormhole.plugin.map.MapSnapshot.MirrorMark;
import com.wormhole_xtreme.wormhole.plugin.map.MapSnapshot.RingMark;

/**
 * Works out what a web map should show from the plugin's current state.
 *
 * <p>A pure function of what it is given, so the picture can be rebuilt whenever a map asks
 * for it and never depends on what was drawn before. It reads only what gates, rings and
 * beams already hold in memory: it runs on the main thread, and must stay cheap there.
 */
public final class MapScanner
{
    /** Static helpers only. */
    private MapScanner()
    {
    }

    /**
     * Builds the picture.
     *
     * @param gates
     *            every gate
     * @param pairs
     *            every ring pair
     * @param beams
     *            the public beam destinations
     * @param mirrors
     *            every quantum mirror
     * @param layers
     *            which layers to fill; one switched off is left empty
     * @return what the map should show
     */
    public static MapSnapshot scan(final Collection<Stargate> gates, final Collection<RingPair> pairs,
        final Collection<BeamDestination> beams, final Collection<QuantumMirror> mirrors,
        final MapLayers layers)
    {
        final Map<String, GateMark> gateMarks = new HashMap<>();
        final Map<String, LineMark> links = new HashMap<>();
        if (layers.gates())
        {
            addGates(gates, layers.irisGates(), gateMarks, links);
        }
        final Map<String, RingMark> ringMarks = new HashMap<>();
        final Map<String, LineMark> ringLinks = new HashMap<>();
        if (layers.rings())
        {
            for (final RingPair pair : pairs)
            {
                addRingPair(pair, ringMarks, ringLinks);
            }
        }
        // Players' private places are deliberately never read: Dynmap shows every marker to every viewer.
        final Map<String, BeamMark> beamMarks = new HashMap<>();
        if (layers.beams())
        {
            for (final BeamDestination beam : beams)
            {
                final BeamMark mark = beamMark(beam);
                if (mark != null)
                {
                    beamMarks.put(mark.id(), mark);
                }
            }
        }
        // Mirrors have no owner and nothing private about them, so every one is shown.
        final Map<String, MirrorMark> mirrorMarks = new HashMap<>();
        if (layers.mirrors())
        {
            for (final QuantumMirror mirror : mirrors)
            {
                final MirrorMark mark = mirrorMark(mirror);
                if (mark != null)
                {
                    mirrorMarks.put(mark.id(), mark);
                }
            }
        }
        return new MapSnapshot(gateMarks, links, ringMarks, ringLinks, beamMarks, mirrorMarks);
    }

    /**
     * Marks the gates, and the lines between dialled pairs.
     *
     * @param gates
     *            every gate
     * @param showIrisGates
     *            false to leave out any gate with an iris code, and any line to one
     * @param marks
     *            where the gates go
     * @param links
     *            where the lines go
     */
    private static void addGates(final Collection<Stargate> gates, final boolean showIrisGates,
        final Map<String, GateMark> marks, final Map<String, LineMark> links)
    {
        for (final Stargate gate : gates)
        {
            final GateMark mark = gateMark(gate, showIrisGates);
            if (mark != null)
            {
                marks.put(mark.id(), mark);
            }
        }
        // Only the gate that dialled holds a target, so the far end's openness is read from it
        // too; but only from a gate that is shown, or a hidden gate would give itself away.
        for (final Stargate gate : gates)
        {
            final Stargate target = gate.getGateTarget();
            final GateMark far = ((target == null) || (target.getGateName() == null)) ? null
                : marks.get(gateId(target.getGateName()));
            if (gate.isGatePortalOpen() && (far != null) && (gate.getGateName() != null)
                && marks.containsKey(gateId(gate.getGateName())))
            {
                marks.put(far.id(), far.withOpen(true));
            }
        }
        for (final Stargate gate : gates)
        {
            final LineMark link = link(gate, marks);
            if (link != null)
            {
                links.put(link.id(), link);
            }
        }
    }

    /**
     * The id a gate is marked under.
     *
     * @param name
     *            the gate's name
     * @return its id
     */
    static String gateId(final String name)
    {
        return name.toLowerCase(Locale.ROOT);
    }

    /**
     * One gate's mark.
     *
     * @param gate
     *            the gate
     * @param showIrisGates
     *            false to leave it out if it has an iris code
     * @return its mark, or null if it is not to be shown
     */
    private static GateMark gateMark(final Stargate gate, final boolean showIrisGates)
    {
        final World world = gate.getGateWorld();
        final String name = gate.getGateName();
        if ((world == null) || (name == null))
        {
            return null;
        }
        if (!showIrisGates && hasIris(gate))
        {
            return null;
        }
        final BoundingBox bounds = gate.getGatePortalBounds();
        final double x;
        final double y;
        final double z;
        Footprint footprint = null;
        if (bounds != null)
        {
            x = bounds.getCenterX();
            y = bounds.getCenterY();
            z = bounds.getCenterZ();
            // The box's maxima already reach past the last block, so it is drawn as it stands.
            footprint = new Footprint(bounds.getMinX(), bounds.getMinZ(), bounds.getMaxX(),
                bounds.getMaxZ(), bounds.getMinY(), bounds.getMaxY());
        }
        else
        {
            final Location at = gate.getGatePlayerTeleportLocation();
            if (at == null)
            {
                return null;
            }
            x = at.getX();
            y = at.getY();
            z = at.getZ();
        }
        final StargateNetwork network = gate.getGateNetwork();
        // Open means formed: a gate still locking chevrons has nothing to travel through yet.
        return new GateMark(gateId(name), world.getName(), name,
            (network == null) ? null : network.getNetworkName(), gate.getGateOwnerName(),
            gate.isGatePortalOpen(), x, y, z, footprint);
    }

    /**
     * Whether a gate has an iris code.
     *
     * @param gate
     *            the gate
     * @return true if it has one
     */
    private static boolean hasIris(final Stargate gate)
    {
        final String code = gate.getGateIrisDeactivationCode();
        return (code != null) && !code.isBlank();
    }

    /**
     * The line from a gate to its target, once the wormhole between them has formed.
     *
     * <p>Keyed on the two names in order, so the pair's two ends make one line, not two.
     *
     * @param gate
     *            the gate
     * @param marks
     *            the gates being shown, so a line never leads to one that is hidden
     * @return the line, or null if there is none to draw
     */
    private static LineMark link(final Stargate gate, final Map<String, GateMark> marks)
    {
        final Stargate target = gate.getGateTarget();
        if (!gate.isGatePortalOpen() || (target == null) || (gate.getGateName() == null)
            || (target.getGateName() == null))
        {
            return null;
        }
        final GateMark here = marks.get(gateId(gate.getGateName()));
        final GateMark there = marks.get(gateId(target.getGateName()));
        // A line is drawn in one world, so a pair across worlds has none.
        if ((here == null) || (there == null) || here.id().equals(there.id())
            || !here.world().equals(there.world()))
        {
            return null;
        }
        final GateMark first = (here.id().compareTo(there.id()) < 0) ? here : there;
        final GateMark second = (first == here) ? there : here;
        return new LineMark(first.id() + "|" + second.id(), first.world(),
            first.name() + " to " + second.name(),
            first.x(), first.y(), first.z(), second.x(), second.y(), second.z());
    }

    /**
     * Marks both ends of a ring pair and the line between them.
     *
     * @param pair
     *            the pair
     * @param marks
     *            where the ends go
     * @param links
     *            where the line goes
     */
    private static void addRingPair(final RingPair pair, final Map<String, RingMark> marks,
        final Map<String, LineMark> links)
    {
        final String world = pair.getWorldName();
        final Ring a = pair.getEndA();
        final Ring b = pair.getEndB();
        if ((world == null) || (pair.getId() == null) || (a == null) || (b == null))
        {
            return;
        }
        final RingMark endA = ringMark(pair, a, "a");
        final RingMark endB = ringMark(pair, b, "b");
        marks.put(endA.id(), endA);
        marks.put(endB.id(), endB);
        links.put(pair.getId(), new LineMark(pair.getId(), world, pair.describe(),
            endA.x(), endA.y(), endA.z(), endB.x(), endB.y(), endB.z()));
    }

    /**
     * One end of a ring pair.
     *
     * @param pair
     *            the pair
     * @param ring
     *            the end
     * @param which
     *            "a" or "b"
     * @return its mark
     */
    private static RingMark ringMark(final RingPair pair, final Ring ring, final String which)
    {
        final String name = ring.getName();
        final String described = pair.describe();
        return new RingMark(pair.getId() + ":" + which, pair.getWorldName(),
            ((name == null) || name.isEmpty()) ? described : name, described, pair.getOwnerName(),
            ring.getAnchorX() + 0.5, ring.getAnchorY(), ring.getAnchorZ() + 0.5);
    }

    /**
     * A public beam destination's mark.
     *
     * @param beam
     *            the destination
     * @return its mark, or null if it records no world
     */
    private static BeamMark beamMark(final BeamDestination beam)
    {
        final BeamPoint point = beam.point();
        if ((beam.name() == null) || (point == null) || (point.worldName() == null))
        {
            return null;
        }
        return new BeamMark(beam.name().toLowerCase(Locale.ROOT), point.worldName(), beam.name(),
            point.x(), point.y(), point.z());
    }

    /**
     * A quantum mirror's mark, at the centre of its banner block.
     *
     * @param mirror
     *            the mirror
     * @return its mark, or null if it records no world
     */
    private static MirrorMark mirrorMark(final QuantumMirror mirror)
    {
        final MirrorBlock banner = mirror.banner();
        if ((mirror.name() == null) || (banner == null) || (banner.worldName() == null))
        {
            return null;
        }
        return new MirrorMark(mirror.name().toLowerCase(Locale.ROOT), banner.worldName(), mirror.name(),
            banner.x() + 0.5, banner.y() + 0.5, banner.z() + 0.5);
    }
}
