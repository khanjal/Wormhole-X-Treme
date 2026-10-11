package com.wormhole_xtreme.wormhole.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.stream.Collectors;
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
    /** Ticks a ring shows before the next on a gate of many rings; a gate of few shows each longer. */
    static final long STEP_TICKS = 2L;

    /** Ticks a whole ripple takes on a small gate, spread over its rings, capped per ring by {@link #SLOWEST_STEP_TICKS}. */
    static final long SMALL_RIPPLE_TICKS = 12L;

    /** The longest a single ring shows. */
    static final long SLOWEST_STEP_TICKS = 8L;

    /** The least time between the starts of two ripples on one gate, so a minecart line does not set one off every tick. */
    static final long MIN_GAP_MILLIS = 2_000L;

    /** The shortest wait for a ripple of its own, while a gate is open and somebody is near. */
    static final long RANDOM_LOW_MILLIS = 3_000L;

    /** The longest. */
    static final long RANDOM_HIGH_MILLIS = 6_000L;

    /** How many rings one wave of a ripple runs ahead of the next. */
    static final int WAVE_GAP = 2;

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

    /** How many waves a ripple of a gate's own has; a seam for tests. */
    static IntSupplier waveCount = () -> wavesFor(ThreadLocalRandom.current().nextInt(100));

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

    /**
     * How many waves a ripple of a gate's own has, for a roll of 0 to 99: one half the time, two
     * three times in ten, three twice in ten.
     *
     * @param roll
     *            a number from 0 to 99
     * @return 1, 2 or 3
     */
    static int wavesFor(final int roll)
    {
        if (roll < 50)
        {
            return 1;
        }
        return (roll < 80) ? 2 : 3;
    }

    /** Starts a ripple of one wave, logging rather than throwing if it fails. */
    private static void startQuietly(final Stargate gate)
    {
        startQuietly(gate, 1);
    }

    /** Starts a ripple, logging rather than throwing if it fails. */
    private static void startQuietly(final Stargate gate, final int waves)
    {
        try
        {
            start(gate, waves);
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
     * Starts a ripple of one wave on a gate, as a crossing does, if it may have one now.
     *
     * @param gate
     *            the gate
     * @return true if one started
     */
    static boolean start(final Stargate gate)
    {
        return start(gate, 1);
    }

    /**
     * Starts a ripple on a gate, if it may have one now.
     *
     * @param gate
     *            the gate
     * @param waves
     *            how many waves, staggered through the same rings ({@link RippleRings#waves})
     * @return true if one started
     */
    static boolean start(final Stargate gate, final int waves)
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
        // Before isDeep, which walks every frame block: nobody near is the usual answer.
        if (StargateBlockSetup.playersNear(gate, reference(gate)).isEmpty())
        {
            return false;
        }
        final boolean deep = isDeep(gate);
        final boolean icy = !deep && (gate.getEffectivePortalMaterial() == Material.WATER)
            && (gate.getGatePortalBlocks().size() > FLAT_MORE_THAN);
        // Nothing to draw for anybody: a flat gate of another material whose horizon is not cleared for a view.
        if (!deep && !icy && (GateViews.horizonOf(gate, gate.getEffectivePortalMaterial()) != Material.AIR))
        {
            return false;
        }
        LAST.put(name, now);
        NEXT.put(name, now + nextWait.getAsLong());
        final List<List<Location>> rings = ringsOf(gate);
        final List<List<Location>> steps = RippleRings.waves(rings.size(), waves, WAVE_GAP).stream()
            .map(lit -> lit.stream().flatMap(ring -> rings.get(ring).stream()).toList()).toList();
        logStart(gate, rings.size(), deep, icy);
        final Ripple ripple = new Ripple(gate, deep, icy);
        // Paced by the rings alone: more waves add steps, never quicken a small gate's slow rings.
        final long pace = stepTicks(rings.size());
        final IrisSweepDriver<Location> driver = new IrisSweepDriver<>(steps, ripple, () -> pace);
        // Registered before its first ring, so a throw while drawing it can still call it off.
        ripple.driver = driver;
        RUNNING.put(name, ripple);
        try
        {
            driver.start();
        }
        catch (final Exception | LinkageError e)
        {
            ripple.fail(e);
        }
        // False if its first ring threw, which called it off.
        return RUNNING.get(name) == ripple;
    }

    /**
     * Ticks one ring shows: a small gate has few rings, so each stays longer or the ripple is too quick to see.
     *
     * @param rings
     *            how many rings the ripple has
     * @return ticks, from {@link #STEP_TICKS} to {@link #SLOWEST_STEP_TICKS}
     */
    static long stepTicks(final int rings)
    {
        if (rings <= 0)
        {
            return STEP_TICKS;
        }
        return Math.max(STEP_TICKS, Math.min(SLOWEST_STEP_TICKS, SMALL_RIPPLE_TICKS / rings));
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
            plugin.prettyLog(Level.FINE, "Ripple on " + gate.getGateName() + ": " + rings + " rings, " + kindOf(deep, icy));
        }
    }

    /** What a ripple is drawn in, for the log. */
    private static String kindOf(final boolean deep, final boolean icy)
    {
        if (deep)
        {
            return "in front";
        }
        return icy ? "ice" : "for a view only";
    }

    /**
     * Gives each open gate somebody is near a ripple of its own now and then.
     *
     * <p>Run once a second while {@code gate-ripple} is on, and not at all while it is off; turning it
     * off forgets every wait ({@link #cancelAll}). A gate's first wait starts when somebody comes near
     * it open, and starts again whenever it ripples for any reason.
     */
    public static void tick()
    {
        final long now = clock.getAsLong();
        final Set<String> watched = new HashSet<>();
        for (final Stargate gate : StargateManager.getOpenGates())
        {
            try
            {
                tickGate(gate, now, watched);
            }
            catch (final Exception | LinkageError e)
            {
                final WormholeXTreme plugin = WormholeXTreme.getThisPlugin();
                if ((plugin != null) && plugin.isLoggable(Level.FINE))
                {
                    plugin.prettyLog(Level.FINE, "Could not look at " + gate.getGateName() + " for a ripple", e);
                }
            }
        }
        NEXT.keySet().retainAll(watched);
    }

    /**
     * One gate's turn in the sweep: its wait started, or its ripple begun once the wait is over.
     *
     * @param watched
     *            added to if somebody is near it open
     */
    private static void tickGate(final Stargate gate, final long now, final Set<String> watched)
    {
        final String name = gate.getGateName();
        if ((name == null) || !showing(gate) || StargateBlockSetup.playersNear(gate, reference(gate)).isEmpty())
        {
            return;
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
            startQuietly(gate, waveCount.getAsInt());
        }
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
     * @param material
     *            what they were drawn as, or null for a record only put back
     */
    private record Sent(Player player, List<Location> cells, boolean inPlane, Material material)
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
        /** Set once a step has thrown: nothing more is booked or registered. */
        private boolean failed;

        Ripple(final Stargate gate, final boolean deep, final boolean icy)
        {
            this.gate = gate;
            this.deep = deep;
            this.icy = icy;
        }

        @Override
        public boolean stillValid()
        {
            return !failed && ConfigManager.isGateRipple() && showing(gate);
        }

        /**
         * Puts the last ring back and draws this one. A throw here reaches the catch round the step that
         * drew it, in {@link #runStep} or round the first ring in {@code start}, which calls the ripple off.
         */
        @Override
        public void drawRing(final List<Location> ring)
        {
            draw(ring);
        }

        /**
         * Calls this ripple off after a throw: out of the register, its step dropped, what it drew put back.
         *
         * @param thrown
         *            what was thrown, for the log
         */
        void fail(final Throwable thrown)
        {
            failed = true;
            RUNNING.remove(gate.getGateName(), this);
            if (driver != null)
            {
                driver.cancel();
            }
            putBack();
            final WormholeXTreme plugin = WormholeXTreme.getThisPlugin();
            if ((plugin != null) && plugin.isLoggable(Level.FINE))
            {
                plugin.prettyLog(Level.FINE, "Ripple on " + gate.getGateName() + " stopped", thrown);
            }
        }

        /**
         * Draws this step's rings for each viewer as they see the gate now, having put back whatever the
         * last step drew that no wave is on now.
         */
        private void draw(final List<Location> step)
        {
            final Map<UUID, Sent> next = drawingsOf(step);
            putBackAllBut(next);
            next.values().forEach(this::send);
        }

        /** What each viewer near is to be drawn of a step: nothing for a viewer of a gate with nothing to show them. */
        private Map<UUID, Sent> drawingsOf(final List<Location> step)
        {
            final Map<UUID, Sent> next = new HashMap<>();
            final List<Player> near = StargateBlockSetup.playersNear(gate, reference(gate));
            if (near.isEmpty())
            {
                return next;
            }
            final Set<Spot> taken = occupied(gate);
            final List<Location> inPlane = step.stream().filter(at -> !taken.contains(spotOf(at))).toList();
            final Material portal = gate.getEffectivePortalMaterial();
            List<Location> inFront = null;
            for (final Player player : near)
            {
                Sent drawing = null;
                // Drawn the view, their opening is clear: the horizon itself is what crosses it.
                if ((portal != Material.AIR) && (GateViews.horizonFor(gate, portal, player) == Material.AIR))
                {
                    drawing = new Sent(player, inPlane, true, portal);
                }
                else if (deep)
                {
                    if (inFront == null)
                    {
                        inFront = inFront(step, taken);
                    }
                    drawing = new Sent(player, inFront, false, portal);
                }
                else if (icy)
                {
                    drawing = new Sent(player, inPlane, true, FLAT_RING);
                }
                if ((drawing != null) && !drawing.cells().isEmpty())
                {
                    next.put(player.getUniqueId(), drawing);
                }
            }
            return next;
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

        /** Sends one viewer their drawing, and notes it to be put back. */
        private void send(final Sent drawing)
        {
            // Noted before it is sent, so a send that throws part way is still put back.
            sent.put(drawing.player().getUniqueId(), drawing);
            final BlockData data = MaterialUtils.drawnAcross(drawing.material(), gate.getGateFacing());
            for (final Location at : drawing.cells())
            {
                drawing.player().sendBlockChange(at, data);
            }
        }

        /**
         * Puts back whatever the ring showing now was drawn as: in the opening, to what that viewer
         * sees there now, if it still shows a wormhole, since anything else draws its own opening; in
         * front, to the real block.
         */
        private void putBack()
        {
            putBackAllBut(Map.of());
        }

        /**
         * Puts back whatever was drawn, except the cells each viewer is about to be drawn again in the
         * same place, which are left as they are rather than put back and drawn in the same tick.
         *
         * @param next
         *            what each viewer is to be drawn next, by viewer
         */
        private void putBackAllBut(final Map<UUID, Sent> next)
        {
            if (sent.isEmpty())
            {
                return;
            }
            // Forgotten first, so a throw part way never leaves a record to be put back twice.
            final List<Sent> was =
                sent.values().stream().map(old -> withoutKept(old, next.get(old.player().getUniqueId()))).toList();
            sent.clear();
            final World world = gate.getGateWorld();
            final Material portal = gate.getEffectivePortalMaterial();
            boolean stillShowing;
            try
            {
                stillShowing = showing(gate);
            }
            catch (final Exception | LinkageError e)
            {
                // A gate that cannot say draws its own opening, as one that has stopped showing does.
                stillShowing = false;
            }
            for (final Sent one : was)
            {
                try
                {
                    putBack(one, world, stillShowing, portal);
                }
                catch (final Exception | LinkageError e)
                {
                    // One viewer who cannot be sent to does not keep everybody else's ring up.
                }
            }
        }

        /** A viewer's last drawing less the cells their next one draws again; a cell in front is never one in the opening. */
        private static Sent withoutKept(final Sent old, final Sent next)
        {
            if (next == null)
            {
                return old;
            }
            final Set<Spot> kept = next.cells().stream().map(HorizonRipple::spotOf).collect(Collectors.toSet());
            return new Sent(old.player(), old.cells().stream().filter(at -> !kept.contains(spotOf(at))).toList(),
                old.inPlane(), null);
        }

        /** Puts back one viewer's ring. */
        private void putBack(final Sent was, final World world, final boolean stillShowing, final Material portal)
        {
            final Player player = was.player();
            if (!player.isOnline() || (world == null) || !world.equals(player.getWorld()))
            {
                return;
            }
            if (was.inPlane())
            {
                if (stillShowing)
                {
                    final BlockData horizon =
                        MaterialUtils.drawnAcross(GateViews.horizonFor(gate, portal, player), gate.getGateFacing());
                    was.cells().forEach(at -> player.sendBlockChange(at, horizon));
                }
                return;
            }
            was.cells().stream().filter(at -> world.isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4))
                .forEach(at -> player.sendBlockChange(at,
                    world.getBlockAt(at.getBlockX(), at.getBlockY(), at.getBlockZ()).getBlockData()));
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
            if (failed)
            {
                return () -> { };
            }
            final int task = WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(),
                () -> runStep(step), ticks);
            return () ->
            {
                if (WormholeXTreme.getScheduler() != null)
                {
                    WormholeXTreme.getScheduler().cancelTask(task);
                }
            };
        }

        /** Runs a booked step; a throw calls the ripple off rather than ending the task with its ring up. */
        private void runStep(final Runnable step)
        {
            try
            {
                step.run();
            }
            catch (final Exception | LinkageError e)
            {
                fail(e);
            }
        }

        @Override
        public void register(final IrisSweepDriver<Location> sweep)
        {
            if (!failed)
            {
                driver = sweep;
                RUNNING.put(gate.getGateName(), this);
            }
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
