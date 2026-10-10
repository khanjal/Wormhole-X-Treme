package com.wormhole_xtreme.wormhole.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.StargateBlockSetup.Redrawn;
import com.wormhole_xtreme.wormhole.model.window.Captures;
import com.wormhole_xtreme.wormhole.model.window.Place;
import com.wormhole_xtreme.wormhole.model.window.WindowShape;
import com.wormhole_xtreme.wormhole.model.window.WindowShape.Spot;
import com.wormhole_xtreme.wormhole.model.window.Windows;

/**
 * An open gate drawn as a window onto where it goes, the way a mirror draws its room (#516).
 *
 * <p>An experiment behind {@code gate-view}, which is {@code horizon} by default and changes
 * nothing there. At {@code behind} the far side is drawn behind the horizon; at {@code open} the
 * horizon clears once the far side is ready, and only for whoever is drawn the view: anybody behind
 * the gate, too far off or not drawn it yet sees the horizon as it always is. Only the dialling end
 * of an upright gate with its iris open.
 * The whole opening is drawn, up to {@link #MOST} each way, and only a gate with a frame round
 * its opening: nothing else hides the view's edges as one walks round it.
 *
 * <p>A gate's capture is kept on disk, named for the gate it shows, and is the base it is drawn
 * from after a restart; removing the gate deletes it. It is taken again when able: as a gate
 * dialling it is dialled or opens, once it is a minute old, and while somebody is at the gate it
 * shows, once it is ten minutes old, since most of that gate's chunks are loaded anyway.
 */
public final class GateViews
{
    /** The widest or tallest opening drawn, the one every capture is seen through; a bigger one keeps its horizon. */
    static final int MOST = Captures.GATE_OPENING;

    /** Before a gate's name, so the sweep cannot mistake it for a mirror's. */
    private static final String PREFIX = "gate:";

    /** Gates whose horizon has cleared for their view, by name. */
    private static final Set<String> CLEARED = new HashSet<>();

    /** The cleared gates each player has been sent with nothing in the opening: those they are drawn the view of. */
    private static final Map<UUID, Set<String>> SEES_THROUGH = new HashMap<>();

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
        SEES_THROUGH.clear();
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
        return ((gate != null) && (portal != Material.AIR) && CLEARED.contains(gate.getGateName())) ? Material.AIR
            : portal;
    }

    /**
     * What a gate's opening is drawn as for one player: nothing only where its horizon has cleared and
     * they are drawn its view, the portal for everybody else.
     *
     * <p>The horizon was cleared for everybody, so from behind the gate, or too far off to be drawn the
     * view, it was an empty ring onto this world.
     *
     * @param gate
     *            the gate
     * @param portal
     *            what it would otherwise show
     * @param player
     *            who it is drawn for
     * @return the material to draw
     */
    public static Material horizonFor(final Stargate gate, final Material portal, final Player player)
    {
        final Set<String> through = (player == null) ? null : SEES_THROUGH.get(player.getUniqueId());
        return ((gate != null) && (through != null) && through.contains(gate.getGateName())) ? horizonOf(gate, portal)
            : portal;
    }

    /**
     * Follows the windows a viewer is drawn, sending the opening of each cleared gate they come to be
     * drawn as nothing, and of each they stop being drawn as its horizon again.
     *
     * <p>The drawing's own judgement of who is drawn a view, so the half-space is its: in front of the
     * opening's face, within range and with a clear line to it. Somebody in the plane or inside the
     * opening is not drawn it, and sees the horizon as at any gate they walk into.
     *
     * @param viewer
     *            whose drawing it is
     * @param player
     *            that player, or null when their drawing went with them, which is forgotten unsent
     * @param windows
     *            the windows they are drawn now
     */
    public static void drawn(final UUID viewer, final Player player, final Set<String> windows)
    {
        final Set<String> was = SEES_THROUGH.getOrDefault(viewer, Set.of());
        if (player == null)
        {
            SEES_THROUGH.remove(viewer);
            return;
        }
        // On every redraw of everybody near a window, so a server with no cleared gate stops here.
        if (was.isEmpty() && CLEARED.isEmpty())
        {
            return;
        }
        final Set<String> now = clearedAmong(windows);
        if (now.equals(was))
        {
            return;
        }
        // Each gate only once its send is done, so one skipped or failed is tried again on the next redraw.
        final Set<String> through = new HashSet<>(was);
        for (final String name : now)
        {
            if (!was.contains(name) && (resend(player, name, true) == Redrawn.SENT))
            {
                through.add(name);
            }
        }
        for (final String name : was)
        {
            if (!now.contains(name) && goneFromClient(resend(player, name, false)))
            {
                through.remove(name);
            }
        }
        if (through.isEmpty())
        {
            SEES_THROUGH.remove(viewer);
        }
        else
        {
            SEES_THROUGH.put(viewer, through);
        }
    }

    /** The cleared gates among some windows' names, by gate name. */
    private static Set<String> clearedAmong(final Set<String> windows)
    {
        final Set<String> cleared = new HashSet<>();
        for (final String window : windows)
        {
            if (window.startsWith(PREFIX) && CLEARED.contains(window.substring(PREFIX.length())))
            {
                cleared.add(window.substring(PREFIX.length()));
            }
        }
        return cleared;
    }

    /** Whether a gate's cleared opening is off a client after this: sent the horizon, or the gate draws its own. */
    private static boolean goneFromClient(final Redrawn redrawn)
    {
        return (redrawn == Redrawn.SENT) || (redrawn == Redrawn.NOT_SHOWING);
    }

    /**
     * Sends one player one gate's opening again, cleared or as its horizon.
     *
     * @return what became of it; null if it failed, which leaves it to be tried again
     */
    private static Redrawn resend(final Player player, final String name, final boolean cleared)
    {
        try
        {
            return StargateBlockSetup.redrawHorizonFor(player, StargateManager.getStargate(name), cleared);
        }
        catch (final Exception | LinkageError e)
        {
            final WormholeXTreme plugin = WormholeXTreme.getThisPlugin();
            if ((plugin != null) && plugin.isLoggable(Level.FINE))
            {
                plugin.prettyLog(Level.FINE, "Could not send " + name + "'s opening to " + player.getName(), e);
            }
            return null;
        }
    }

    /**
     * Forgets a player who has left, and whatever gates they were being drawn the view of.
     *
     * @param viewer
     *            who left
     */
    public static void forgetViewer(final UUID viewer)
    {
        SEES_THROUGH.remove(viewer);
    }

    /**
     * Forgets a gate's cleared horizon as its iris opens, before the opening is drawn for everybody.
     *
     * <p>The opening iris paints the opening alike for everybody, behind the gate too; still cleared, it
     * painted nothing for them all, and a crossing over between two sweeps left it so. Cleared again by
     * the next sweep, each viewer drawn the view is sent the cleared opening then.
     *
     * @param gate
     *            the gate whose iris is opening
     */
    public static void irisOpening(final Stargate gate)
    {
        if ((gate != null) && (gate.getGateName() != null))
        {
            unclear(gate.getGateName());
        }
    }

    /** Forgets everybody's being drawn one gate's view, as its horizon stops being cleared. */
    private static void unclear(final String name)
    {
        CLEARED.remove(name);
        SEES_THROUGH.values().removeIf(through -> through.remove(name) && through.isEmpty());
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
        unclear(gate.getGateName());
        OPEN.remove(gate.getGateName());
        Windows.release(PREFIX + gate.getGateName());
    }

    /** Offers every open gate that can show a view to the mirror sweep, and clears or restores horizons to match. */
    public static void offerAll()
    {
        final String level = ConfigManager.getGateView();
        final boolean draws = !"horizon".equals(level);
        final Set<String> open = new HashSet<>();
        final Set<String> clear = new HashSet<>();
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
            final Place at = Place.of(arrival);
            Captures.refreshGate(gate.getGateName(), at,
                Captures.gateFillDepth(at, ConfigManager.getGateViewDepth()), REFRESH_SECONDS);
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
        Captures.forgetGate(gate.getGateName());
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
        final WindowShape shape = ((arrival == null) || (arrival.getWorld() == null)) ? null
            : shapeOf(gate.getGateFacing(), cellsOf(gate), frameOf(gate), Place.of(arrival));
        if (shape != null)
        {
            GateSource.prepare(windowOf(gate, shape));
        }
    }

    /** An open gate as the window drawing sees it. */
    private static GateSource windowOf(final Stargate gate, final WindowShape shape)
    {
        final List<Spot> open = cellsOf(gate);
        final Spot middle = middleOf(open);
        final Stargate target = gate.getGateTarget();
        return new GateSource(PREFIX + gate.getGateName(), gate.getGateWorld().getBlockAt(middle.x(), middle.y(), middle.z()),
            shape, open, Place.of(target.getGatePlayerTeleportLocation()), target.getGateName(),
            ConfigManager.getGateViewDepth());
    }

    /**
     * The cell of an opening nearest its middle: the window's anchor, which the drawing measures a
     * viewer's distance from, and so does {@link #watched}.
     *
     * <p>Not its first cell, a top-row one: somebody at the foot of a Massive gate, six blocks in
     * front, was seventeen from it, past the mirror proximity distance, and was never drawn the view.
     */
    static Spot middleOf(final List<Spot> cells)
    {
        final int[] low = { Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE };
        final int[] high = { Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE };
        for (final Spot cell : cells)
        {
            final int[] at = { cell.x(), cell.y(), cell.z() };
            for (int axis = 0; axis < 3; axis++)
            {
                low[axis] = Math.min(low[axis], at[axis]);
                high[axis] = Math.max(high[axis], at[axis]);
            }
        }
        final Spot aim = new Spot(low[0] + ((high[0] - low[0]) / 2), low[1] + ((high[1] - low[1]) / 2),
            low[2] + ((high[2] - low[2]) / 2));
        return cells.stream().min(Comparator.comparingInt((Spot cell) -> (Math.abs(cell.x() - aim.x())
            + Math.abs(cell.y() - aim.y()) + Math.abs(cell.z() - aim.z())))).orElseThrow();
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
     *            added to if its horizon should be cleared
     */
    private static void offer(final Stargate gate, final boolean crossing, final boolean clears,
        final Set<String> open, final Set<String> clear)
    {
        final WindowShape shape = shapeOf(gate, crossing);
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
        final boolean drawn = GateSource.offer(windowOf(gate, shape), !OPEN.contains(name));
        if (drawn && clears && !crossing)
        {
            clear.add(name);
        }
    }

    /**
     * Whether anybody could be drawn a gate's view: its chunk is loaded and somebody in its world is
     * within the mirror proximity distance of the middle of its opening, measured as the drawing
     * measures it ({@link #middleOf}), so a capture is never started for somebody it would not be drawn for.
     *
     * <p>Measured to the nearest point of the opening, somebody fourteen blocks in front of a Massive
     * gate's foot and off to one side was eight from it and twenty from the middle: the capture,
     * seconds of work in the far world, was started, and the view never drawn.
     *
     * <p>A gate dialled by redstone in a corner of the map nobody is in should not pay for one.
     */
    private static boolean watched(final Stargate gate)
    {
        final World world = gate.getGateWorld();
        if ((world == null) || gate.getGatePortalBlocks().isEmpty())
        {
            return false;
        }
        final Spot middle = middleOf(cellsOf(gate));
        if (!world.isChunkLoaded(middle.x() >> 4, middle.z() >> 4))
        {
            return false;
        }
        final double reach = ConfigManager.getMirrorProximityDistance();
        return world.getPlayers().stream().map(Player::getLocation).anyMatch(at ->
        {
            final double dx = at.getX() - middle.x();
            final double dy = at.getY() - middle.y();
            final double dz = at.getZ() - middle.z();
            return ((dx * dx) + (dy * dy) + (dz * dz)) <= (reach * reach);
        });
    }

    /**
     * Marks the horizon of every gate newly showing an open view cleared, and puts it back on every
     * gate no longer showing one; a gate mid iris crossing is left as it is until the crossing is over.
     *
     * <p>Cleared is not drawn: whoever is drawn the view is sent the opening as nothing once the
     * drawing has settled ({@link #drawn}), and nobody else is sent anything.
     */
    private static void settleHorizons(final Set<String> clear, final Set<String> busy)
    {
        for (final String name : new ArrayList<>(CLEARED))
        {
            if (clear.contains(name) || busy.contains(name))
            {
                continue;
            }
            unclear(name);
            final Stargate gate = StargateManager.getStargate(name);
            // Only a wormhole still showing: a shut iris or a closed gate draws its own.
            if ((gate != null) && gate.isGateActive() && gate.isGatePortalOpen() && !gate.isGateIrisActive())
            {
                gate.fillGateInterior(gate.getEffectivePortalMaterial());
            }
        }
        for (final String name : clear)
        {
            if (StargateManager.getStargate(name) != null)
            {
                CLEARED.add(name);
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
    static WindowShape shapeOf(final Stargate gate, final boolean crossing)
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
        return shapeOf(gate.getGateFacing(), cellsOf(gate), frameOf(gate), Place.of(arrival));
    }

    /**
     * The window an upright opening makes onto an arrival point, if it may show one: plain numbers, for testing.
     *
     * <p>Which gates may is decided here and nowhere else: upright, framed ({@link #framed}), and no
     * bigger than {@link #fits} allows.
     *
     * @param facing
     *            the way the gate faces, which is where it is looked into from
     * @param cells
     *            the opening's cells, all in one upright plane
     * @param frame
     *            the gate's frame blocks
     * @param arrival
     *            where a traveller lands, facing the way they leave
     * @return the window, the whole of the opening, or null for no opening, a gate lying flat, one
     *         with no frame round its opening, or one too big
     */
    static WindowShape shapeOf(final BlockFace facing, final List<Spot> cells, final Set<Spot> frame,
        final Place arrival)
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
        if (!fits(width, height) || !framed(cells, frame, right))
        {
            return null;
        }
        // Right runs along one axis, one way or the other, so the across coordinate is the block's own, signed.
        final Spot plane = cells.get(0);
        final Spot base = (into.x() != 0) ? new Spot(plane.x(), lowY, lowAcross * right.z())
            : new Spot(lowAcross * right.x(), lowY, plane.z());
        return WindowShape.through(base, into, arrival, width, height);
    }

    /**
     * Whether an opening this size may show a view: no wider or taller than the opening every
     * capture is seen through, whose rays were measured to miss nothing out to 160 blocks.
     *
     * @return true if it fits
     */
    static boolean fits(final int width, final int height)
    {
        return (width <= MOST) && (height <= MOST);
    }

    /**
     * Whether a frame closes an opening in on every side, in its own plane: each cell has opening or
     * frame beside it, above and below.
     *
     * <p>A Minimal gate is two portal cells on one frame block, open to the air at the sides and top.
     * Nothing but the ring hides a view's edges as one walks round a freestanding gate, so with no ring
     * the far side would hang in the air beside it. Judged from the gate's own blocks rather than its
     * shape's name, so a custom shape without a frame is treated the same.
     *
     * @param right
     *            one step across the opening
     */
    static boolean framed(final List<Spot> cells, final Set<Spot> frame, final Spot right)
    {
        final Set<Spot> opening = new HashSet<>(cells);
        for (final Spot cell : cells)
        {
            for (final Spot side : new Spot[] { new Spot(cell.x() + right.x(), cell.y(), cell.z() + right.z()),
                new Spot(cell.x() - right.x(), cell.y(), cell.z() - right.z()), new Spot(cell.x(), cell.y() + 1, cell.z()),
                new Spot(cell.x(), cell.y() - 1, cell.z()) })
            {
                if (!opening.contains(side) && !frame.contains(side))
                {
                    return false;
                }
            }
        }
        return true;
    }

    /** A gate's frame blocks as spots. */
    private static Set<Spot> frameOf(final Stargate gate)
    {
        return gate.getGateStructureBlocks().stream()
            .map(cell -> new Spot(cell.getBlockX(), cell.getBlockY(), cell.getBlockZ())).collect(Collectors.toSet());
    }

    /** A gate's portal cells as spots. */
    private static List<Spot> cellsOf(final Stargate gate)
    {
        return gate.getGatePortalBlocks().stream()
            .map(cell -> new Spot(cell.getBlockX(), cell.getBlockY(), cell.getBlockZ())).toList();
    }
}
