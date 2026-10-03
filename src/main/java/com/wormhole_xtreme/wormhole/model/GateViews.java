package com.wormhole_xtreme.wormhole.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.mirror.GateWindow;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorCaptures;
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
 * thing a real build would change. Only the dialling end of an upright gate with its iris open.
 * A capture's rays grow with its hole, so an opening wider or taller than {@link #MOST} is drawn
 * through a {@code MOST}-square window carved at the foot of its middle, and the rest of it keeps
 * its horizon.
 *
 * <p>A gate's capture is kept on disk, named for the gate it shows, and is the base it is drawn
 * from after a restart; removing the gate deletes it. It is taken again when able: as a gate
 * dialling it is dialled or opens, once it is a minute old, and while somebody is at the gate it
 * shows, once it is ten minutes old, since most of that gate's chunks are loaded anyway.
 */
public final class GateViews
{
    /** The widest or tallest window drawn, the one every capture is seen through; a bigger gate has one carved. */
    static final int MOST = MirrorCaptures.GATE_OPENING;

    /** Before a gate's name, so the sweep cannot mistake it for a mirror's. */
    private static final String PREFIX = "gate:";

    /** Gates whose horizon has cleared for their view, by name. */
    private static final Set<String> CLEARED = new HashSet<>();

    /** The window carved in each cleared gate bigger than {@link #MOST}, by name: only its cells are cleared. */
    private static final Map<String, MirrorWindow> CARVED = new HashMap<>();

    /** Gates that could show a view last sweep, so the first sweep after opening is known. */
    private static final Set<String> OPEN = new HashSet<>();

    /** How old a gate's capture may be before somebody standing at that gate has it taken again. */
    static final long REFRESH_SECONDS = 600L;

    /** How often each gate is looked at for somebody standing at it, in milliseconds. */
    private static final long LOOK_MILLIS = 60_000L;

    /** When each gate was last looked at for somebody standing at it, by name. */
    private static final Map<String, Long> LOOKED = new HashMap<>();

    private GateViews()
    {
    }

    /** Forgets every gate, for a reload or a test. */
    public static void clear()
    {
        CLEARED.clear();
        CARVED.clear();
        OPEN.clear();
        LOOKED.clear();
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
        return ((gate != null) && (portal != Material.AIR) && CLEARED.contains(gate.getGateName())
            && !CARVED.containsKey(gate.getGateName())) ? Material.AIR : portal;
    }

    /**
     * What one cell of a gate's opening is drawn as: nothing where it is inside a window carved in
     * a big gate and cleared for its view, otherwise what it was going to be drawn as.
     *
     * <p>Only the portal is cleared, so an iris drawn over the same cells still shows.
     *
     * @param gate
     *            the gate
     * @param drawn
     *            what the cell was going to be drawn as
     * @param cell
     *            the cell
     * @return the material to draw
     */
    public static Material horizonAt(final Stargate gate, final Material drawn, final Location cell)
    {
        final MirrorWindow carved = (gate == null) ? null : CARVED.get(gate.getGateName());
        return ((carved != null) && (drawn == gate.getEffectivePortalMaterial())
            && carved.isOpening(cell.getBlockX(), cell.getBlockY(), cell.getBlockZ())) ? Material.AIR : drawn;
    }

    /**
     * Forgets a gate that has just closed, and takes its view back from whoever has it.
     *
     * <p>Rather than waiting for the next sweep: until then the far side stood behind an empty
     * ring, and a gate dialled again inside that second settled with its horizon already cleared,
     * for a destination that might have no capture at all.
     *
     * @param gate
     *            the gate that closed
     */
    public static void closed(final Stargate gate)
    {
        if ((gate == null) || (gate.getGateName() == null))
        {
            return;
        }
        CLEARED.remove(gate.getGateName());
        CARVED.remove(gate.getGateName());
        OPEN.remove(gate.getGateName());
        MirrorWindows.release(PREFIX + gate.getGateName());
    }

    /** Offers every open gate that can show a view to the mirror sweep, and clears or restores horizons to match. */
    public static void offerAll()
    {
        final String level = ConfigManager.getGateView();
        final boolean draws = !"horizon".equals(level);
        final Set<String> open = new HashSet<>();
        final Map<String, MirrorWindow> clear = new HashMap<>();
        final Set<String> busy = new HashSet<>();
        for (final Stargate gate : StargateManager.getOpenGates())
        {
            // An iris crossing paints the opening a ring at a time and fills it at the end, at any
            // level: a horizon cleared or put back under it is painted over by its next step.
            final boolean crossing = StargateIrisAnimator.isSweeping(gate);
            if (crossing)
            {
                busy.add(gate.getGateName());
            }
            if (draws)
            {
                offer(gate, crossing, "open".equals(level), open, clear);
            }
        }
        OPEN.clear();
        OPEN.addAll(open);
        settleHorizons(clear, busy);
        if (draws)
        {
            refreshWatched(System.currentTimeMillis());
        }
    }

    /**
     * Has the captures of every gate somebody is standing at taken again once they are old: that
     * gate's chunks are loaded anyway, so it costs a moment's reading and nothing off the disk.
     *
     * @param now
     *            the time, so each gate is looked at once a minute rather than every sweep
     */
    static void refreshWatched(final long now)
    {
        for (final Stargate gate : StargateManager.getAllGatesUnsorted())
        {
            // The minute first, so a gate nobody is at costs a map lookup a sweep, not a walk of the players.
            final Long looked = LOOKED.get(gate.getGateName());
            if ((looked == null) || ((now - looked) >= LOOK_MILLIS))
            {
                LOOKED.put(gate.getGateName(), now);
                refreshIfWatched(gate);
            }
        }
    }

    /** Has a gate's captures taken again, if somebody is at it. */
    private static void refreshIfWatched(final Stargate gate)
    {
        final Location arrival = gate.getGatePlayerTeleportLocation();
        if ((arrival != null) && (arrival.getWorld() != null) && watched(gate))
        {
            // To the full depth: somebody is here, so most of the fill's chunks are loaded anyway.
            final MirrorPoint at = MirrorPoint.of(arrival);
            MirrorCaptures.refreshGate(gate.getGateName(), at,
                MirrorCaptures.gateFillDepth(at, ConfigManager.getGateViewDepth()), REFRESH_SECONDS);
        }
    }

    /**
     * Forgets a gate that is being removed, and deletes what it shows.
     *
     * <p>Only a removal: a refresh hands a gate back under the same name, and keeps its captures.
     *
     * @param gate
     *            the gate being removed
     */
    public static void removed(final Stargate gate)
    {
        if ((gate == null) || (gate.getGateName() == null))
        {
            return;
        }
        closed(gate);
        LOOKED.remove(gate.getGateName());
        MirrorCaptures.forgetGate(gate.getGateName());
    }

    /**
     * Starts a gate's capture as it is dialled, before its kawoosh (#516).
     *
     * <p>The first sweep after the kawoosh was the first ask, and a capture of somewhere nobody had
     * loaded then started by reading it off the disk: half a minute or more of plain horizon, long
     * enough for the gate to close first. Only a gate somebody is near, as for the sweep.
     *
     * @param gate
     *            the gate just dialled, with its target set
     */
    public static void dialled(final Stargate gate)
    {
        if ((gate == null) || "horizon".equals(ConfigManager.getGateView()) || (gate.getGateTarget() == null)
            || (gate.getGateTarget().getGateName() == null) || !watched(gate))
        {
            return;
        }
        final Location arrival = gate.getGateTarget().getGatePlayerTeleportLocation();
        final MirrorWindow shape = ((arrival == null) || (arrival.getWorld() == null)) ? null
            : shapeOf(gate.getGateFacing(), cellsOf(gate), MirrorPoint.of(arrival));
        if (shape != null)
        {
            MirrorWindows.prepareGate(windowOf(gate, shape));
        }
    }

    /** An open gate as the window drawing sees it. */
    private static GateWindow windowOf(final Stargate gate, final MirrorWindow shape)
    {
        // Only the window's cells: the rest of a big gate's opening is still its horizon.
        final List<Spot> open = cellsOf(gate).stream().filter(cell -> shape.isOpening(cell.x(), cell.y(), cell.z()))
            .toList();
        final Spot first = open.get(0);
        final Stargate target = gate.getGateTarget();
        return new GateWindow(PREFIX + gate.getGateName(), gate.getGateWorld().getBlockAt(first.x(), first.y(), first.z()),
            shape, open, MirrorPoint.of(target.getGatePlayerTeleportLocation()), target.getGateName(),
            ConfigManager.getGateViewDepth());
    }

    /** Whether a window leaves some of a gate's opening outside it. */
    private static boolean carves(final Stargate gate, final MirrorWindow shape)
    {
        return cellsOf(gate).stream().anyMatch(cell -> !shape.isOpening(cell.x(), cell.y(), cell.z()));
    }

    /**
     * Offers one open gate, if it can show a view now, and notes what became of it.
     *
     * @param crossing
     *            true while its iris is crossing: still drawn, so the view stays behind a closing
     *            iris, but its horizon is left as the crossing paints it
     * @param clears
     *            true at {@code open}, where a drawn view clears the horizon
     * @param open
     *            added to if it could show a view, whether or not anybody is near to be drawn it
     * @param clear
     *            added to if its horizon should be cleared, with the window it is cleared in
     */
    private static void offer(final Stargate gate, final boolean crossing, final boolean clears,
        final Set<String> open, final Map<String, MirrorWindow> clear)
    {
        final MirrorWindow shape = shapeOf(gate, crossing);
        if (shape == null)
        {
            return;
        }
        final String name = gate.getGateName();
        // Open whether or not anybody is near, so walking back into range is not the gate opening again.
        open.add(name);
        if (!watched(gate))
        {
            return;
        }
        final boolean drawn = MirrorWindows.offerGate(windowOf(gate, shape), !OPEN.contains(name));
        if (drawn && clears && !crossing)
        {
            clear.put(name, shape);
        }
    }

    /**
     * Whether anybody could be drawn a gate's view: its chunk is loaded and somebody in its world is
     * within the mirror proximity distance of its opening.
     *
     * <p>Of any of it, not of its first cell: that is a top-row one, and a Grand gate's window at the
     * foot of its opening could be looked into from half as far back as a Standard gate's.
     *
     * <p>A capture is a few seconds of work in the far world, and a gate dialled by redstone in a
     * corner of the map nobody is in should not pay for one.
     */
    private static boolean watched(final Stargate gate)
    {
        final World world = gate.getGateWorld();
        if ((world == null) || gate.getGatePortalBlocks().isEmpty())
        {
            return false;
        }
        final Location first = gate.getGatePortalBlocks().get(0);
        if (!world.isChunkLoaded(first.getBlockX() >> 4, first.getBlockZ() >> 4))
        {
            return false;
        }
        final double reach = ConfigManager.getMirrorProximityDistance();
        final double[] box = boxOf(gate.getGatePortalBlocks());
        return world.getPlayers().stream().map(Player::getLocation).anyMatch(at ->
        {
            final double dx = at.getX() - Math.max(box[0], Math.min(box[3], at.getX()));
            final double dy = at.getY() - Math.max(box[1], Math.min(box[4], at.getY()));
            final double dz = at.getZ() - Math.max(box[2], Math.min(box[5], at.getZ()));
            return ((dx * dx) + (dy * dy) + (dz * dz)) <= (reach * reach);
        });
    }

    /** The smallest box round some cells: lowest x, y and z, then highest. */
    private static double[] boxOf(final List<Location> cells)
    {
        final double[] box = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE,
            -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (final Location cell : cells)
        {
            box[0] = Math.min(box[0], cell.getBlockX());
            box[1] = Math.min(box[1], cell.getBlockY());
            box[2] = Math.min(box[2], cell.getBlockZ());
            box[3] = Math.max(box[3], cell.getBlockX());
            box[4] = Math.max(box[4], cell.getBlockY());
            box[5] = Math.max(box[5], cell.getBlockZ());
        }
        return box;
    }

    /**
     * Clears the horizon of every gate newly showing an open view, and puts it back on every gate no
     * longer showing one; a gate mid iris crossing is left as it is until the crossing is over.
     */
    private static void settleHorizons(final Map<String, MirrorWindow> clear, final Set<String> busy)
    {
        for (final Iterator<String> it = CLEARED.iterator(); it.hasNext();)
        {
            final String name = it.next();
            if (clear.containsKey(name) || busy.contains(name))
            {
                continue;
            }
            it.remove();
            CARVED.remove(name);
            final Stargate gate = StargateManager.getStargate(name);
            // Only a wormhole still showing: a shut iris or a closed gate draws its own.
            if ((gate != null) && gate.isGateActive() && gate.isGatePortalOpen() && !gate.isGateIrisActive())
            {
                gate.fillGateInterior(gate.getEffectivePortalMaterial());
            }
        }
        for (final Map.Entry<String, MirrorWindow> entry : clear.entrySet())
        {
            final Stargate gate = StargateManager.getStargate(entry.getKey());
            if ((gate == null) || !CLEARED.add(entry.getKey()))
            {
                continue;
            }
            if (carves(gate, entry.getValue()))
            {
                // Drawn as its portal, which horizonAt clears inside the window.
                CARVED.put(entry.getKey(), entry.getValue());
                gate.fillGateInterior(gate.getEffectivePortalMaterial());
            }
            else
            {
                gate.fillGateInterior(Material.AIR);
            }
        }
    }

    /**
     * The window an open gate makes, if it can show one now.
     *
     * @param crossing
     *            true while its iris is crossing, when a shut iris still shows the view it is closing over
     * @return the window, or null for a gate that keeps its horizon
     */
    static MirrorWindow shapeOf(final Stargate gate, final boolean crossing)
    {
        if ((gate == null) || !gate.isGateActive() || !gate.isGatePortalOpen() || (gate.isGateIrisActive() && !crossing)
            || (gate.getGateWorld() == null) || (gate.getGateTarget() == null) || (gate.getGateTarget().getGateName() == null))
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
     * @return the window, at most {@link #MOST} each way at the foot of the opening's middle, or null
     *         for no opening, a gate lying flat, or one with no room for that window
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
        final Set<Long> taken = new HashSet<>();
        for (final Spot cell : cells)
        {
            final int across = (cell.x() * right.x()) + (cell.z() * right.z());
            lowAcross = Math.min(lowAcross, across);
            highAcross = Math.max(highAcross, across);
            lowY = Math.min(lowY, cell.y());
            highY = Math.max(highY, cell.y());
            taken.add(cellKey(across, cell.y()));
        }
        final int width = Math.min((highAcross - lowAcross) + 1, MOST);
        final int height = Math.min((highY - lowY) + 1, MOST);
        final int from = lowAcross + ((((highAcross - lowAcross) + 1) - width) / 2);
        final boolean whole = (width == ((highAcross - lowAcross) + 1)) && (height == ((highY - lowY) + 1));
        // A big gate's: the lowest rows it fits in whole, so its floor is the gate's where the ring
        // allows, since a round gate's bottom row can be narrower than the window.
        for (int foot = lowY; (foot + height) <= (highY + 1); foot++)
        {
            if (whole || fits(taken, from, foot, width, height))
            {
                // Right runs along one axis, one way or the other, so the across coordinate is the block's own, signed.
                final Spot plane = cells.get(0);
                final Spot base = (into.x() != 0) ? new Spot(plane.x(), foot, from * right.z())
                    : new Spot(from * right.x(), foot, plane.z());
                return MirrorWindow.through(base, into, arrival, width, height);
            }
        }
        return null;
    }

    /** Whether every cell of a window is part of the opening. */
    private static boolean fits(final Set<Long> taken, final int from, final int foot, final int width, final int height)
    {
        for (int across = from; across < (from + width); across++)
        {
            for (int y = foot; y < (foot + height); y++)
            {
                if (!taken.contains(cellKey(across, y)))
                {
                    return false;
                }
            }
        }
        return true;
    }

    /** One cell of an opening, by how far across it is and its height. */
    private static long cellKey(final int across, final int y)
    {
        return (((long) across) << 32) | (y & 0xFFFFFFFFL);
    }

    /** A gate's portal cells as spots. */
    private static List<Spot> cellsOf(final Stargate gate)
    {
        return gate.getGatePortalBlocks().stream()
            .map(cell -> new Spot(cell.getBlockX(), cell.getBlockY(), cell.getBlockZ())).toList();
    }
}
