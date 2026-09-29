package com.wormhole_xtreme.wormhole.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorPoint;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorWindow;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorWindow.Spot;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorWindows;

/**
 * An open gate drawn as a window onto where it goes, the way a mirror draws its room (#516).
 *
 * <p>An experiment behind {@code gate-view}, which is {@code horizon} by default and changes
 * nothing there. At {@code behind} the far side is drawn behind the horizon; at {@code open} the
 * horizon clears once the far side is ready -- for everybody, not per viewer, which is the first
 * thing a real build would change. Only the dialling end of an upright gate with its iris open,
 * and only an opening up to {@link #MOST} each way, since a capture's rays grow with its hole.
 */
public final class GateViews
{
    /** The widest or tallest opening drawn; a bigger gate keeps its horizon. */
    static final int MOST = 5;

    /** Before a gate's name, so the sweep cannot mistake it for a mirror's. */
    private static final String PREFIX = "gate:";

    /** Gates whose horizon has cleared for their view, by name. */
    private static final Set<String> CLEARED = new HashSet<>();

    /** Gates that could show a view last sweep, so the first sweep after opening is known. */
    private static final Set<String> OPEN = new HashSet<>();

    private GateViews()
    {
    }

    /** Forgets every gate, for a reload or a test. */
    public static void clear()
    {
        CLEARED.clear();
        OPEN.clear();
    }

    /**
     * What a gate's opening is drawn as once its wormhole is up: the portal, or nothing where its
     * horizon has cleared for a view.
     *
     * @param gate
     *            the gate
     * @param portal
     *            what it would otherwise show
     * @return the material to draw
     */
    public static Material horizonOf(final Stargate gate, final Material portal)
    {
        return ((gate != null) && (portal != Material.AIR) && CLEARED.contains(gate.getGateName())) ? Material.AIR
            : portal;
    }

    /** Offers every open gate that can show a view to the mirror sweep, and clears or restores horizons to match. */
    public static void offerAll()
    {
        final String level = ConfigManager.getGateView();
        final Set<String> open = new HashSet<>();
        final Set<String> clear = new HashSet<>();
        if (!"horizon".equals(level))
        {
            for (final Stargate gate : StargateManager.getOpenGates())
            {
                final MirrorWindow shape = shapeOf(gate);
                if (shape == null)
                {
                    continue;
                }
                final String name = gate.getGateName();
                open.add(name);
                final Location first = gate.getGatePortalBlocks().get(0);
                final boolean drawn = MirrorWindows.offerGate(PREFIX + name,
                    gate.getGateWorld().getBlockAt(first.getBlockX(), first.getBlockY(), first.getBlockZ()), shape,
                    cellsOf(gate), MirrorPoint.of(gate.getGateTarget().getGatePlayerTeleportLocation()),
                    !OPEN.contains(name));
                if (drawn && "open".equals(level))
                {
                    clear.add(name);
                }
            }
        }
        OPEN.clear();
        OPEN.addAll(open);
        settleHorizons(clear);
    }

    /** Clears the horizon of every gate newly showing an open view, and puts it back on every gate no longer showing one. */
    private static void settleHorizons(final Set<String> clear)
    {
        for (final Iterator<String> it = CLEARED.iterator(); it.hasNext();)
        {
            final String name = it.next();
            if (clear.contains(name))
            {
                continue;
            }
            it.remove();
            final Stargate gate = StargateManager.getStargate(name);
            // Only a wormhole still showing: a shut iris or a closed gate draws its own.
            if ((gate != null) && gate.isGateActive() && gate.isGatePortalOpen() && !gate.isGateIrisActive())
            {
                gate.fillGateInterior(gate.getEffectivePortalMaterial());
            }
        }
        for (final String name : clear)
        {
            final Stargate gate = StargateManager.getStargate(name);
            if ((gate != null) && CLEARED.add(name))
            {
                gate.fillGateInterior(Material.AIR);
            }
        }
    }

    /**
     * The window an open gate makes, if it can show one now.
     *
     * @return the window, or null for a gate that keeps its horizon
     */
    static MirrorWindow shapeOf(final Stargate gate)
    {
        if ((gate == null) || !gate.isGateActive() || !gate.isGatePortalOpen() || gate.isGateIrisActive()
            || (gate.getGateWorld() == null) || (gate.getGateTarget() == null))
        {
            return null;
        }
        final Location arrival = gate.getGateTarget().getGatePlayerTeleportLocation();
        if ((arrival == null) || (arrival.getWorld() == null))
        {
            return null;
        }
        return shapeOf(gate.getGateFacing(), cellsOf(gate), MirrorPoint.of(arrival));
    }

    /**
     * The window an upright opening makes onto an arrival point: plain numbers, for testing.
     *
     * @param facing
     *            the way the gate faces, which is where it is looked into from
     * @param cells
     *            the opening's cells, all in one upright plane
     * @param arrival
     *            where a traveller lands, facing the way they leave
     * @return the window, or null for no opening, a gate lying flat, or one bigger than {@link #MOST}
     */
    static MirrorWindow shapeOf(final BlockFace facing, final List<Spot> cells, final MirrorPoint arrival)
    {
        if ((facing == null) || (facing.getModY() != 0) || ((facing.getModX() == 0) == (facing.getModZ() == 0))
            || cells.isEmpty() || (arrival == null))
        {
            return null;
        }
        final Spot into = new Spot(-facing.getModX(), 0, -facing.getModZ());
        final Spot right = new Spot(-into.z(), 0, into.x());
        int lowAcross = Integer.MAX_VALUE;
        int highAcross = Integer.MIN_VALUE;
        int lowY = Integer.MAX_VALUE;
        int highY = Integer.MIN_VALUE;
        for (final Spot cell : cells)
        {
            final int across = (cell.x() * right.x()) + (cell.z() * right.z());
            lowAcross = Math.min(lowAcross, across);
            highAcross = Math.max(highAcross, across);
            lowY = Math.min(lowY, cell.y());
            highY = Math.max(highY, cell.y());
        }
        final int width = (highAcross - lowAcross) + 1;
        final int height = (highY - lowY) + 1;
        if ((width > MOST) || (height > MOST))
        {
            return null;
        }
        // Right runs along one axis, one way or the other, so the across coordinate is the block's own, signed.
        final Spot plane = cells.get(0);
        final Spot base = (into.x() != 0) ? new Spot(plane.x(), lowY, lowAcross * right.z())
            : new Spot(lowAcross * right.x(), lowY, plane.z());
        return MirrorWindow.through(base, into, arrival, width, height);
    }

    /** A gate's portal cells as spots. */
    private static List<Spot> cellsOf(final Stargate gate)
    {
        final List<Spot> cells = new ArrayList<>();
        for (final Location cell : gate.getGatePortalBlocks())
        {
            cells.add(new Spot(cell.getBlockX(), cell.getBlockY(), cell.getBlockZ()));
        }
        return cells;
    }
}
