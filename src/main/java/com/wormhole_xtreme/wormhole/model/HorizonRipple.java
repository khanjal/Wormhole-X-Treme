package com.wormhole_xtreme.wormhole.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.RippleRings.Cell;
import com.wormhole_xtreme.wormhole.model.window.StandIns;
import com.wormhole_xtreme.wormhole.model.window.WindowShape.Spot;
import com.wormhole_xtreme.wormhole.utils.MaterialUtils;

/**
 * A ripple across an open gate's event horizon: rings from the centre of the opening out, a step
 * each, behind {@code gate-ripple} (#579).
 *
 * <p>A gate ripples when something goes in, when something comes out, and now and then while
 * somebody is near, one ripple at a time and never two within {@link #MIN_GAP_MILLIS}. What a ring
 * is made of depends on the gate and on who is looking:
 *
 * <ul>
 * <li>a viewer drawn the gate's view at {@code gate-view: open}, whose opening is clear, is drawn
 * the gate's own portal material in the opening, ring by ring;</li>
 * <li>anybody else at a gate whose frame stands round the plane in front of its horizon ({@link #isDeep},
 * Grand and Massive of the shipped shapes) is drawn the portal material a block in front of it, in
 * the cells there that are air;</li>
 * <li>anybody else at a flat water gate bigger than two by two is drawn ice in the horizon itself;</li>
 * <li>anybody else at any other gate is drawn nothing.</li>
 * </ul>
 *
 * <p>A ring is drawn for one step and put back as the next is drawn: in the opening, to what
 * {@link GateViews#horizonFor} says that viewer sees; in front, to the real block. Never on a cell
 * an entity is in, only on an upright gate whose wormhole is showing, and only to players within
 * the horizon's reach.
 */
public final class HorizonRipple
{
    /** Ticks a ring shows before the next. */
    static final long STEP_TICKS = 2L;

    /** The least time between the starts of two ripples on one gate, so a minecart line does not set one off every tick. */
    static final long MIN_GAP_MILLIS = 2_000L;

    /** The shortest wait for a ripple of its own, while a gate is open and somebody is near. */
    static final long RANDOM_LOW_MILLIS = 6_000L;

    /** The longest. */
    static final long RANDOM_HIGH_MILLIS = 20_000L;

    /** A flat water gate needs more cells than a two by two has before its horizon ripples in ice. */
    static final int FLAT_MORE_THAN = 4;

    /** What a flat water horizon ripples in: translucent like the water, and on every version. */
    static final Material FLAT_RING = Material.ICE;

    /** The ripple running on each gate, by name. */
    private static final Map<String, Ripple> RUNNING = new HashMap<>();

    /** When each gate last started a ripple, by name. */
    private static final Map<String, Long> LAST = new HashMap<>();

    /** When each gate open with somebody near ripples of its own next, by name. */
    private static final Map<String, Long> NEXT = new HashMap<>();

    /** The time, in milliseconds; a seam for tests. */
    static LongSupplier clock = System::currentTimeMillis;

    /** How long until a gate's next ripple of its own; a seam for tests. */
    static LongSupplier nextWait = () -> ThreadLocalRandom.current().nextLong(RANDOM_LOW_MILLIS, RANDOM_HIGH_MILLIS + 1);

    private HorizonRipple()
    {
    }

    /**
     * Ripples both ends of a crossing: the gate something went into, and the gate it came out of.
     *
     * <p>Decoration on the travel path, so nothing here may stop a trip.
     *
     * @param from
     *            the gate entered
     * @param to
     *            the gate arrived at, or null
     */
    public static void crossed(final Stargate from, final Stargate to)
    {
        startQuietly(from);
        startQuietly(to);
    }

    /** Starts a ripple, logging rather than throwing if it fails. */
    private static void startQuietly(final Stargate gate)
    {
        try
        {
            start(gate);
        }
        catch (final Exception | LinkageError e)
        {
            final WormholeXTreme plugin = WormholeXTreme.getThisPlugin();
            if ((plugin != null) && plugin.isLoggable(Level.FINE))
            {
                plugin.prettyLog(Level.FINE, "Could not ripple " + ((gate == null) ? null : gate.getGateName()), e);
            }
        }
    }

    /**
     * Starts a ripple on a gate, if it may have one now.
     *
     * @param gate
     *            the gate
     * @return true if one started
     */
    static boolean start(final Stargate gate)
    {
        if ((gate == null) || (gate.getGateName() == null) || !ConfigManager.isGateRipple() || !showing(gate)
            || !pluginRunning())
        {
            return false;
        }
        final String name = gate.getGateName();
        final long now = clock.getAsLong();
        final Long last = LAST.get(name);
        if (RUNNING.containsKey(name) || ((last != null) && ((now - last) < MIN_GAP_MILLIS)))
        {
            return false;
        }
        final boolean deep = isDeep(gate);
        final boolean icy = !deep && (gate.getEffectivePortalMaterial() == Material.WATER)
            && (gate.getGatePortalBlocks().size() > FLAT_MORE_THAN);
        // Nothing to draw for anybody: a flat gate of another material whose horizon is not cleared for a view.
        if ((!deep && !icy && (GateViews.horizonOf(gate, gate.getEffectivePortalMaterial()) != Material.AIR))
            || StargateBlockSetup.playersNear(gate, reference(gate)).isEmpty())
        {
            return false;
        }
        LAST.put(name, now);
        NEXT.put(name, now + nextWait.getAsLong());
        final List<List<Location>> rings = ringsOf(gate);
        logStart(gate, rings.size(), deep, icy);
        new IrisSweepDriver<>(rings, new Ripple(gate, deep, icy), () -> STEP_TICKS).start();
        return true;
    }

    /** Whether the plugin can still book a step: Bukkit refuses one from a plugin being disabled. */
    private static boolean pluginRunning()
    {
        final WormholeXTreme plugin = WormholeXTreme.getThisPlugin();
        return (plugin != null) && plugin.isEnabled() && (WormholeXTreme.getScheduler() != null);
    }

    /** Says a ripple started, at {@code log-level: FINE}. */
    private static void logStart(final Stargate gate, final int rings, final boolean deep, final boolean icy)
    {
        final WormholeXTreme plugin = WormholeXTreme.getThisPlugin();
        if ((plugin != null) && plugin.isLoggable(Level.FINE))
        {
            plugin.prettyLog(Level.FINE, "Ripple on " + gate.getGateName() + ": " + rings + " rings, "
                + (deep ? "in front" : (icy ? "ice" : "for a view only")));
        }
    }

    /**
     * Gives each open gate somebody is near a ripple of its own now and then.
     *
     * <p>Run once a second while {@code gate-ripple} is on. A gate's first wait starts when somebody
     * comes near it open, and starts again whenever it ripples for any reason.
     */
    public static void tick()
    {
        if (!ConfigManager.isGateRipple())
        {
            NEXT.clear();
            return;
        }
        final long now = clock.getAsLong();
        final Set<String> watched = new HashSet<>();
        for (final Stargate gate : StargateManager.getOpenGates())
        {
            final String name = gate.getGateName();
            if ((name == null) || !showing(gate) || StargateBlockSetup.playersNear(gate, reference(gate)).isEmpty())
            {
                continue;
            }
            watched.add(name);
            final Long next = NEXT.get(name);
            if (next == null)
            {
                NEXT.put(name, now + nextWait.getAsLong());
            }
            else if (now >= next)
            {
                // Waited again whether or not it starts: refused for a ripple already running, or for
                // nothing to draw, it would otherwise be asked every second.
                NEXT.put(name, now + nextWait.getAsLong());
                start(gate);
            }
        }
        NEXT.keySet().retainAll(watched);
    }

    /**
     * Calls off a gate's ripple and puts back what it drew.
     *
     * @param gate
     *            the gate, or null
     */
    public static void cancel(final Stargate gate)
    {
        if ((gate != null) && (gate.getGateName() != null))
        {
            cancel(gate.getGateName());
        }
    }

    /**
     * Calls off a gate's ripple by name and puts back what it drew.
     *
     * @param name
     *            the gate's name
     */
    static void cancel(final String name)
    {
        final Ripple ripple = RUNNING.remove(name);
        if (ripple != null)
        {
            ripple.callOff();
        }
    }

    /**
     * Forgets a gate that has closed or gone: its ripple, and when it last rippled.
     *
     * @param gate
     *            the gate, or null
     */
    public static void forget(final Stargate gate)
    {
        if ((gate != null) && (gate.getGateName() != null))
        {
            cancel(gate.getGateName());
            LAST.remove(gate.getGateName());
            NEXT.remove(gate.getGateName());
        }
    }

    /** Calls off every ripple, for plugin shutdown, and forgets every gate. */
    public static void cancelAll()
    {
        for (final String name : new ArrayList<>(RUNNING.keySet()))
        {
            cancel(name);
        }
        LAST.clear();
        NEXT.clear();
    }

    /**
     * Forgets a player whose client has dropped the gate's chunks: they left, or changed world.
     *
     * @param viewer
     *            the player
     */
    public static void forgetViewer(final UUID viewer)
    {
        for (final Ripple ripple : RUNNING.values())
        {
            ripple.sent.remove(viewer);
        }
    }

    /** Whether a gate ripples now. */
    static boolean isRippling(final Stargate gate)
    {
        return (gate != null) && RUNNING.containsKey(gate.getGateName());
    }

    /**
     * Whether a gate's opening shows a wormhole a ripple may be drawn over: upright, open with its
     * kawoosh settled, its iris open and not crossing, and its chunk loaded.
     */
    static boolean showing(final Stargate gate)
    {
        final World world = gate.getGateWorld();
        if ((world == null) || !upright(gate.getGateFacing()) || !gate.isGateActive() || !gate.isGatePortalOpen()
            || gate.isGateIrisActive() || StargateIrisAnimator.isSweeping(gate) || gate.getGatePortalBlocks().isEmpty())
        {
            return false;
        }
        final Location first = gate.getGatePortalBlocks().get(0);
        return world.isChunkLoaded(first.getBlockX() >> 4, first.getBlockZ() >> 4);
    }

    /** Whether a facing stands a gate upright: one of the four sides. */
    private static boolean upright(final BlockFace facing)
    {
        return (facing != null) && (facing.getModY() == 0) && ((facing.getModX() == 0) != (facing.getModZ() == 0));
    }

    /** The cell the horizon's reach is measured from, as the horizon drawing measures it. */
    private static Location reference(final Stargate gate)
    {
        final Location first = gate.getGatePortalBlocks().get(0);
        return new Location(gate.getGateWorld(), first.getBlockX(), first.getBlockY(), first.getBlockZ());
    }

    /**
     * Whether a gate's frame stands in the plane a block in front of its horizon, so a ripple there
     * shows as the horizon standing out of the ring.
     */
    static boolean isDeep(final Stargate gate)
    {
        return isDeep(gate.getGateFacing(), spotsOf(gate.getGatePortalBlocks()), spotsOf(gate.getGateStructureBlocks()));
    }

    /**
     * The same, in plain numbers: in the plane a block in front of the opening, the gate has frame on
     * all four sides of it, beside its rows and above and below its columns. Read from the gate's own
     * blocks, not its shape's name. A ring with gaps in it, as Massive's front layer has, counts; a DHD
     * standing in front to one side, as Minimal's does, does not.
     *
     * @param facing
     *            the way the gate faces, which is in front
     * @param cells
     *            the opening's cells
     * @param frame
     *            the gate's frame blocks
     * @return true for a gate with a ring in front of its horizon
     */
    static boolean isDeep(final BlockFace facing, final List<Spot> cells, final List<Spot> frame)
    {
        if (!upright(facing) || cells.isEmpty())
        {
            return false;
        }
        // The opening's plane runs along x for a gate facing north or south, along z for one facing east or west.
        final boolean alongX = facing.getModX() == 0;
        final int front = alongX ? (cells.get(0).z() + facing.getModZ()) : (cells.get(0).x() + facing.getModX());
        final int lowAcross = cells.stream().mapToInt(cell -> acrossOf(cell, alongX)).min().orElseThrow();
        final int highAcross = cells.stream().mapToInt(cell -> acrossOf(cell, alongX)).max().orElseThrow();
        final int lowY = cells.stream().mapToInt(Spot::y).min().orElseThrow();
        final int highY = cells.stream().mapToInt(Spot::y).max().orElseThrow();
        final List<Spot> inFront = frame.stream().filter(block -> (alongX ? block.z() : block.x()) == front).toList();
        final Predicate<Spot> besideRows = block -> (block.y() >= lowY) && (block.y() <= highY);
        final Predicate<Spot> overColumns =
            block -> (acrossOf(block, alongX) >= lowAcross) && (acrossOf(block, alongX) <= highAcross);
        return inFront.stream().anyMatch(besideRows.and(block -> acrossOf(block, alongX) < lowAcross))
            && inFront.stream().anyMatch(besideRows.and(block -> acrossOf(block, alongX) > highAcross))
            && inFront.stream().anyMatch(overColumns.and(block -> block.y() < lowY))
            && inFront.stream().anyMatch(overColumns.and(block -> block.y() > highY));
    }

    /** How far across the opening's plane a block is. */
    private static int acrossOf(final Spot block, final boolean alongX)
    {
        return alongX ? block.x() : block.z();
    }

    /** Locations as spots. */
    private static List<Spot> spotsOf(final List<Location> blocks)
    {
        return blocks.stream().map(at -> new Spot(at.getBlockX(), at.getBlockY(), at.getBlockZ())).toList();
    }

    /**
     * A gate's opening in rings, the centre first, as {@link RippleRings} cuts it.
     *
     * @return the rings, each the gate's own portal locations
     */
    static List<List<Location>> ringsOf(final Stargate gate)
    {
        final boolean alongX = gate.getGateFacing().getModX() == 0;
        final List<Location> portal = gate.getGatePortalBlocks();
        final List<Spot> spots = spotsOf(portal);
        final int lowAcross = spots.stream().mapToInt(spot -> acrossOf(spot, alongX)).min().orElse(0);
        final int highAcross = spots.stream().mapToInt(spot -> acrossOf(spot, alongX)).max().orElse(0);
        final int lowY = spots.stream().mapToInt(Spot::y).min().orElse(0);
        final int highY = spots.stream().mapToInt(Spot::y).max().orElse(0);
        final List<Cell> cells =
            spots.stream().map(spot -> new Cell(acrossOf(spot, alongX) - lowAcross, spot.y() - lowY)).toList();
        return RippleRings.of(cells, (highAcross - lowAcross) + 1, (highY - lowY) + 1).stream()
            .map(ring -> ring.stream().map(portal::get).toList()).toList();
    }

    /**
     * Where each entity near a gate stands, as every block its box reaches into.
     *
     * <p>Stand-ins are a window's drawing, not things standing in the gate.
     */
    static Set<Spot> occupied(final Stargate gate)
    {
        final Set<Spot> taken = new HashSet<>();
        final BoundingBox bounds = gate.getGatePortalBounds();
        if (bounds == null)
        {
            return taken;
        }
        gate.getGateWorld().getNearbyEntities(bounds.clone().expand(1.0)).stream()
            .filter(entity -> !StandIns.isStandIn(entity)).forEach(entity -> addBlocksOf(entity.getBoundingBox(), taken));
        return taken;
    }

    /** Adds every block a box reaches into, not those it only touches. */
    private static void addBlocksOf(final BoundingBox box, final Set<Spot> taken)
    {
        final int lowX = (int) Math.floor(box.getMinX());
        final int lowY = (int) Math.floor(box.getMinY());
        final int lowZ = (int) Math.floor(box.getMinZ());
        final int highX = Math.max(lowX, (int) Math.ceil(box.getMaxX()) - 1);
        final int highY = Math.max(lowY, (int) Math.ceil(box.getMaxY()) - 1);
        final int highZ = Math.max(lowZ, (int) Math.ceil(box.getMaxZ()) - 1);
        for (int x = lowX; x <= highX; x++)
        {
            for (int y = lowY; y <= highY; y++)
            {
                for (int z = lowZ; z <= highZ; z++)
                {
                    taken.add(new Spot(x, y, z));
                }
            }
        }
    }

    /**
     * What one viewer was drawn of the ring showing now.
     *
     * @param player
     *            who
     * @param cells
     *            where
     * @param inPlane
     *            true in the opening, false a block in front of it
     */
    private record Sent(Player player, List<Location> cells, boolean inPlane)
    {
    }

    /** One ripple crossing one gate, and what its ring showing now was drawn as, for whom. */
    private static final class Ripple implements IrisSweepDriver.Canvas<Location>
    {
        private final Stargate gate;
        private final boolean deep;
        private final boolean icy;
        private final Map<UUID, Sent> sent = new HashMap<>();
        private IrisSweepDriver<Location> driver;

        Ripple(final Stargate gate, final boolean deep, final boolean icy)
        {
            this.gate = gate;
            this.deep = deep;
            this.icy = icy;
        }

        @Override
        public boolean stillValid()
        {
            return ConfigManager.isGateRipple() && showing(gate);
        }

        /** Puts the last ring back and draws this one, for each viewer as they see the gate now. */
        @Override
        public void drawRing(final List<Location> ring)
        {
            putBack();
            final List<Player> near = StargateBlockSetup.playersNear(gate, reference(gate));
            if (near.isEmpty())
            {
                return;
            }
            final Set<Spot> taken = occupied(gate);
            final List<Location> inPlane = ring.stream().filter(at -> !taken.contains(spotOf(at))).toList();
            final Material portal = gate.getEffectivePortalMaterial();
            List<Location> inFront = null;
            for (final Player player : near)
            {
                // Drawn the view, their opening is clear: the horizon itself is what crosses it.
                if ((portal != Material.AIR) && (GateViews.horizonFor(gate, portal, player) == Material.AIR))
                {
                    send(player, inPlane, portal, true);
                }
                else if (deep)
                {
                    if (inFront == null)
                    {
                        inFront = inFront(ring, taken);
                    }
                    send(player, inFront, portal, false);
                }
                else if (icy)
                {
                    send(player, inPlane, FLAT_RING, true);
                }
            }
        }

        /** The cells a block in front of a ring that are free to draw in: air, loaded, and nobody in them. */
        private List<Location> inFront(final List<Location> ring, final Set<Spot> taken)
        {
            final BlockFace facing = gate.getGateFacing();
            final World world = gate.getGateWorld();
            return ring.stream()
                .map(at -> new Location(world, (double) at.getBlockX() + facing.getModX(), at.getBlockY(),
                    (double) at.getBlockZ() + facing.getModZ()))
                .filter(front -> world.isChunkLoaded(front.getBlockX() >> 4, front.getBlockZ() >> 4)
                    && !taken.contains(spotOf(front)) && StargateBlockSetup.backdropIsFree(front))
                .toList();
        }

        /** Sends one viewer some cells as a material, and notes it to be put back. */
        private void send(final Player player, final List<Location> cells, final Material material,
            final boolean inPlane)
        {
            if (cells.isEmpty())
            {
                return;
            }
            final BlockData data = MaterialUtils.drawnAcross(material, gate.getGateFacing());
            for (final Location at : cells)
            {
                player.sendBlockChange(at, data);
            }
            sent.put(player.getUniqueId(), new Sent(player, cells, inPlane));
        }

        /**
         * Puts back whatever the ring showing now was drawn as: in the opening, to what that viewer
         * sees there now, if it still shows a wormhole, since anything else draws its own opening; in
         * front, to the real block.
         */
        private void putBack()
        {
            if (sent.isEmpty())
            {
                return;
            }
            final World world = gate.getGateWorld();
            final boolean stillShowing = showing(gate);
            final Material portal = gate.getEffectivePortalMaterial();
            for (final Sent was : sent.values())
            {
                final Player player = was.player();
                if (!player.isOnline() || (world == null) || !world.equals(player.getWorld()))
                {
                    continue;
                }
                if (was.inPlane())
                {
                    if (stillShowing)
                    {
                        final BlockData horizon =
                            MaterialUtils.drawnAcross(GateViews.horizonFor(gate, portal, player), gate.getGateFacing());
                        was.cells().forEach(at -> player.sendBlockChange(at, horizon));
                    }
                }
                else
                {
                    was.cells().stream().filter(at -> world.isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4))
                        .forEach(at -> player.sendBlockChange(at,
                            world.getBlockAt(at.getBlockX(), at.getBlockY(), at.getBlockZ()).getBlockData()));
                }
            }
            sent.clear();
        }

        /** Drops the booked step and puts back the ring showing. */
        void callOff()
        {
            if (driver != null)
            {
                driver.cancel();
            }
            putBack();
        }

        @Override
        public void moveHorizon(final List<Location> ring)
        {
            // A ripple has no far layer: the iris is open.
        }

        @Override
        public IrisSweepDriver.Booking later(final long ticks, final Runnable step)
        {
            final int task = WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), step,
                ticks);
            return () ->
            {
                if (WormholeXTreme.getScheduler() != null)
                {
                    WormholeXTreme.getScheduler().cancelTask(task);
                }
            };
        }

        @Override
        public void register(final IrisSweepDriver<Location> sweep)
        {
            driver = sweep;
            RUNNING.put(gate.getGateName(), this);
        }

        /** Ended or stopped: the last ring goes back. */
        @Override
        public void unregister()
        {
            RUNNING.remove(gate.getGateName(), this);
            putBack();
        }

        @Override
        public void settle()
        {
            // Put back as it unregistered.
        }
    }

    /** A location's block as a spot. */
    private static Spot spotOf(final Location at)
    {
        return new Spot(at.getBlockX(), at.getBlockY(), at.getBlockZ());
    }
}
