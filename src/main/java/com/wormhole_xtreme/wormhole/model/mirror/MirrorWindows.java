package com.wormhole_xtreme.wormhole.model.mirror;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Rotatable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorWindow.Spot;

/**
 * Drawing what is on the other side of every window mirror a player is looking into.
 *
 * <p>Every mirror with somewhere to go is a window: one hung on a wall opens in the wall, and a
 * freestanding one opens in the air behind where it stands. Nothing is stored to say so, which is
 * what lets mirrors made before windows existed open as one without being touched.
 *
 * <p>What a window shows is its {@link MirrorCapture}: a photograph of the far side, taken once
 * and kept on disk, so the far world need not be loaded to be looked at. A mirror whose capture
 * has not been taken yet stays a banner until it has -- a few seconds, the first time.
 *
 * <p>Nothing in the world changes, the way a gate's event horizon changes nothing. A viewer is
 * sent the banner as air, the open part of the opening as barrier -- invisible, and at least as
 * solid as what it covers -- and far-side blocks behind it: only the ones they could see through
 * an opening from where their eye is, all of whose outline is hidden by the opening or by solid
 * blocks around it, and each from the opening their line of sight passes through. That is what
 * lets windows share a wall, and what keeps a freestanding one inside its edges. Their own
 * world's creatures standing inside the view are hidden from them for as long as they look.
 *
 * <p>Far-side blocks reach {@code mirror-view-depth} from the eye, and nothing is drawn past that.
 * See {@link Pass}.
 *
 * <h2>What it costs, and what keeps that down</h2>
 *
 * <ul>
 * <li>Only the cone from the eye through the opening is walked, and only out to a radius from
 * the eye, so the work grows with what can be seen and is bounded however close the eye comes:
 * right against a mirror the cone is half a sphere, and half a sphere of a fixed radius is a
 * fixed number of blocks.</li>
 * <li>A viewer is redrawn at most a few times a second as they move, and not at all on a sweep
 * where nothing changed. Only the difference is sent, except after crossing into a new chunk --
 * which is when the client is handed fresh chunks that erase what was drawn -- and as a long
 * safety net.</li>
 * <li>The far side is read from the capture, in memory, never from the live world.</li>
 * </ul>
 *
 * <p>A prototype for #278: blocks only, no entities, and lit and tinted by this world.
 */
public final class MirrorWindows
{
    /** How often a viewer is sent their whole view again when nothing else has prompted it. */
    private static final long RESEND_MILLIS = 30_000L;

    /** How old a reading of what surrounds an opening may get. */
    private static final long RESAMPLE_MILLIS = 5000L;

    /** Least time between two redraws of one viewer as they move. */
    static final long REDRAW_MILLIS = 250L;

    /** The same, in ticks, for the redraw that catches a viewer up after they stop. */
    private static final long REDRAW_TICKS = 5L;

    /** Most blocks one redraw considers across every window a viewer sees, while they move. */
    private static final int MOST_CANDIDATES = 40_000;

    /**
     * The same for a redraw while they stand still.
     *
     * <p>A view is redrawn up to four times a second while its viewer moves, so that budget is
     * kept small. Standing still, one redraw at a time grows the view towards the configured
     * depth, and each may cost more: the depth a viewer sees is what these can afford.
     */
    private static final int MOST_STILL = 120_000;

    /** How far around an opening its solid surroundings are read. */
    private static final int SURROUND = 8;

    /** Every window the last sweep found, by mirror name. */
    private static final Map<String, Window> WINDOWS = new HashMap<>();

    /** Windows found by the sweep in progress. */
    private static final Map<String, Window> OFFERED = new HashMap<>();

    /** What each viewer's client has been told, by player. */
    private static final Map<UUID, View> VIEWS = new ConcurrentHashMap<>();

    /** Block states reused between redraws, by world and block, rather than read again. */
    private static final Map<String, Map<Long, BlockState>> STATES = new HashMap<>();

    /** When {@link #STATES} was last trimmed to the blocks still being drawn. */
    private static long trimmedAt;

    /** Whether each block behind an opening is really empty, by world and block, briefly. */
    private static final Map<String, Map<Long, Boolean>> EMPTY = new HashMap<>();

    /** The highest block that is not air in each real column, by world and column, as briefly. */
    private static final Map<String, Map<Long, Integer>> TOPS = new HashMap<>();

    /** When {@link #EMPTY} was last cleared. */
    private static long emptyReadAt;

    /** Whether each real block between viewers and openings is solid, by world and block. */
    private static final Map<String, Map<Long, Boolean>> SOLID = new HashMap<>();

    /** When {@link #SOLID} was last cleared. */
    private static long solidReadAt;

    /** Told what became of each block a redraw considered, for a replay; null otherwise. */
    static Probe probe;

    /** Hears each block's verdict, for a replay. */
    @FunctionalInterface
    interface Probe
    {
        /**
         * @param x
         *            block x
         * @param y
         *            block y
         * @param z
         *            block z
         * @param verdict
         *            what became of it
         */
        void saw(int x, int y, int z, String verdict);
    }

    private static void probe(final int x, final int y, final int z, final String verdict)
    {
        if (probe != null)
        {
            probe.saw(x, y, z, verdict);
        }
    }

    /** One window: where it is, which of its opening can be seen through, and its far side. */
    private static final class Window
    {
        private final QuantumMirror mirror;
        private final MirrorWindow shape;
        private final Block banner;
        private final List<Spot> open;
        private final Set<Long> openKeys = new HashSet<>();
        private final MirrorCapture capture;
        private Set<Long> solid = Set.of();
        private long solidAt;

        Window(final QuantumMirror mirror, final MirrorWindow shape, final Block banner,
            final List<Spot> open, final MirrorCapture capture)
        {
            this.mirror = mirror;
            this.shape = shape;
            this.banner = banner;
            this.open = open;
            this.capture = capture;
            open.forEach(cell -> openKeys.add(key(cell.x(), cell.y(), cell.z())));
        }
    }

    /** One viewer's drawing, as last sent. */
    private static final class View
    {
        private final World world;
        private Map<Long, BlockData> drawn = new HashMap<>();
        private Set<String> mirrors = Set.of();
        private final Map<UUID, Entity> veiled = new HashMap<>();
        private long fullAt;
        private long composedAt;
        private int generation;
        private int undrawable;
        private long eye = Long.MIN_VALUE;
        private long chunk = Long.MIN_VALUE;
        private Location pendingEye;
        private boolean catchUpQueued;
        private String lastRedraw = "never redrawn";

        /**
         * How far this viewer's view reaches, at most the configured radius.
         *
         * <p>Shrunk when a redraw spends its budget, and grown back when there is room. A spent
         * budget used to leave the view ragged -- drawn deep in the middle and shallow at its
         * edges. A shorter radius is a whole view that is simply shallower while the eye is close.
         */
        private int radius = Integer.MAX_VALUE;

        /** Whether the last redraw had room to reach further, so the next sweep redraws. */
        private boolean growing;

        View(final World world)
        {
            this.world = world;
        }
    }

    /**
     * What a player's view is doing, for {@code mirror debug}.
     *
     * @param player
     *            the player
     * @return lines to say
     */
    public static List<String> describe(final Player player)
    {
        final List<String> lines = new ArrayList<>();
        lines.add(WINDOWS.size() + " window(s) on the server, " + VIEWS.size() + " viewer(s), radius "
            + ConfigManager.getMirrorViewDepth() + " (mirror-view-depth), capture radius "
            + ConfigManager.getMirrorCaptureRadius());
        final View view = VIEWS.get(player.getUniqueId());
        if (view == null)
        {
            lines.add("you are looking into no window");
            return lines;
        }
        lines.add("you see " + view.mirrors + ": " + view.drawn.size() + " blocks drawn, "
            + view.veiled.size() + " creature(s) hidden, reaching " + view.radius
            + ((view.undrawable > 0) ? (", " + view.undrawable
                + " block(s) the world would not let be drawn over") : ""));
        lines.add("last redraw: " + view.lastRedraw);
        return lines;
    }

    /** Static state only. */
    private MirrorWindows()
    {
    }

    /** Forgets every view without sending anything, for a test or a reload. */
    public static void clear()
    {
        WINDOWS.clear();
        OFFERED.clear();
        VIEWS.clear();
        STATES.clear();
        EMPTY.clear();
        TOPS.clear();
        SOLID.clear();
        MirrorCaptures.clear();
    }

    /**
     * Offers a mirror to the sweep in progress.
     *
     * <p>A mirror whose far side has not been captured yet is not a window until it has; the
     * capture is asked for, and the mirror stays a banner meanwhile. A dynamic mirror whose
     * capture is old enough asks for a fresh one, and keeps showing the old until it arrives.
     *
     * @param mirror
     *            a mirror with somewhere to go
     * @param banner
     *            its loaded banner block
     * @return true if it is a window, and nothing else should be done with it this sweep
     */
    static boolean offer(final QuantumMirror mirror, final Block banner)
    {
        final BlockData data = banner.getBlockData();
        final boolean standing = data instanceof Rotatable;
        if (!standing && !(data instanceof Directional))
        {
            return false;
        }
        final MirrorWindow shape = MirrorWindow.of(mirror.banner(), MirrorArrival.facingOf(data),
            standing, mirror.destination());
        if (shape == null)
        {
            return false;
        }
        final MirrorCapture capture = MirrorCaptures.get(mirror);
        if (capture == null)
        {
            MirrorCaptures.request(mirror);
            return false;
        }
        if (MirrorCaptures.due(mirror, capture) || MirrorCaptures.outgrown(mirror, capture))
        {
            MirrorCaptures.request(mirror);
        }
        final Window window = new Window(mirror, shape, banner, openCells(shape, banner.getWorld()),
            capture);
        final Window previous = WINDOWS.get(mirror.name());
        if ((previous != null) && previous.shape.equals(shape))
        {
            window.solid = previous.solid;
            window.solidAt = previous.solidAt;
        }
        OFFERED.put(mirror.name(), window);
        return true;
    }

    /** Ends a sweep: the windows offered become the windows there are, and every view follows. */
    static void finish()
    {
        MirrorCaptures.step(0);
        if (OFFERED.isEmpty() && WINDOWS.isEmpty() && VIEWS.isEmpty())
        {
            return;
        }
        final long now = now();
        WINDOWS.clear();
        WINDOWS.putAll(OFFERED);
        OFFERED.clear();
        final Set<World> worlds = new LinkedHashSet<>();
        WINDOWS.values().forEach(window -> worlds.add(window.banner.getWorld()));
        final Set<UUID> seen = new HashSet<>();
        for (final World world : worlds)
        {
            for (final Player player : world.getPlayers())
            {
                seen.add(player.getUniqueId());
                update(player, player.getEyeLocation(), now, true);
            }
        }
        // Viewers in no world with a window left in it: gone, or left behind by a window.
        for (final UUID id : new ArrayList<>(VIEWS.keySet()))
        {
            if (!seen.contains(id))
            {
                final Player player = Bukkit.getPlayer(id);
                if (player == null)
                {
                    VIEWS.remove(id);
                }
                else
                {
                    update(player, player.getEyeLocation(), now, true);
                }
            }
        }
        trimStates(now);
        MirrorCaptures.unloadIdle();
    }

    /**
     * Keeps a player's view in step as they move, rather than waiting for the next sweep.
     *
     * <p>On every move of every player, so a server with no windows answers from two empty maps.
     * A viewer is redrawn when their eye has moved a quarter of a block, and at most every
     * {@link #REDRAW_MILLIS}; a move inside that is caught up a moment later.
     *
     * @param player
     *            who moved
     * @param to
     *            where they are moving to
     */
    public static void moved(final Player player, final Location to)
    {
        if ((WINDOWS.isEmpty() && VIEWS.isEmpty()) || (player == null) || (to == null))
        {
            return;
        }
        final UUID id = player.getUniqueId();
        final View view = (id == null) ? null : VIEWS.get(id);
        if ((view == null) && !nearAWindow(player, to))
        {
            return;
        }
        final Location eye = to.clone().add(0.0, player.getEyeHeight(), 0.0);
        if ((view != null) && (view.eye == eyeKey(eye)))
        {
            return;
        }
        final long now = now();
        if ((view != null) && ((now - view.composedAt) < REDRAW_MILLIS))
        {
            view.pendingEye = eye;
            catchUpLater(player, view);
            return;
        }
        update(player, eye, now, false);
    }

    /**
     * Takes one mirror out of every view it is in.
     *
     * @param mirror
     *            the mirror no longer being drawn
     */
    public static void release(final QuantumMirror mirror)
    {
        OFFERED.remove(mirror.name());
        if (WINDOWS.remove(mirror.name()) == null)
        {
            return;
        }
        final long now = now();
        for (final Map.Entry<UUID, View> entry : new ArrayList<>(VIEWS.entrySet()))
        {
            if (entry.getValue().mirrors.contains(mirror.name()))
            {
                final Player player = Bukkit.getPlayer(entry.getKey());
                if (player == null)
                {
                    VIEWS.remove(entry.getKey());
                }
                else
                {
                    update(player, player.getEyeLocation(), now, false);
                }
            }
        }
    }

    /** Takes every view back, as the plugin stops. */
    public static void restoreAll()
    {
        final long now = now();
        for (final Map.Entry<UUID, View> entry : VIEWS.entrySet())
        {
            final Player player = Bukkit.getPlayer(entry.getKey());
            if ((player != null) && player.getWorld().equals(entry.getValue().world))
            {
                send(player, entry.getValue(), new HashMap<>(), now, false);
                veil(player, entry.getValue(), List.of());
            }
        }
        clear();
    }

    /**
     * The window mirror a player clicked through, if they clicked one they are looking into.
     *
     * <p>On every right-click of every block, so a player who is looking into no window is
     * answered from one map lookup, before the block is asked anything.
     *
     * @param player
     *            who clicked
     * @param block
     *            the real block behind what they clicked
     * @return the mirror, or null
     */
    static QuantumMirror clicked(final Player player, final Block block)
    {
        final UUID id = (player == null) ? null : player.getUniqueId();
        final View view = (id == null) ? null : VIEWS.get(id);
        if ((view == null) || (block == null))
        {
            return null;
        }
        final Spot at = new Spot(block.getX(), block.getY(), block.getZ());
        for (final String name : view.mirrors)
        {
            final Window window = WINDOWS.get(name);
            if ((window != null) && window.open.contains(at)
                && window.banner.getWorld().equals(block.getWorld()))
            {
                return window.mirror;
            }
        }
        return null;
    }

    /** Redraws one player's view from where their eye is, sending only what needs sending. */
    private static void update(final Player player, final Location eye, final long now,
        final boolean fromSweep)
    {
        final UUID id = player.getUniqueId();
        View view = VIEWS.get(id);
        if ((view != null) && !view.world.equals(player.getWorld()))
        {
            // A new world's chunks have already replaced everything drawn in the old one, and
            // its creatures are all new to the client too.
            VIEWS.remove(id);
            view = null;
        }
        final List<Window> seeing = seenBy(player, eye);
        if ((view == null) && seeing.isEmpty())
        {
            return;
        }
        if (view == null)
        {
            view = new View(player.getWorld());
            VIEWS.put(id, view);
        }
        final long chunk = chunkOf(eye);
        final boolean crossed = view.chunk != chunk;
        if (fromSweep && !crossed && unchanged(view, seeing, eyeKey(eye), now))
        {
            if ((now - view.fullAt) >= RESEND_MILLIS)
            {
                send(player, view, view.drawn, now, true);
            }
            return;
        }
        final int configured = ConfigManager.getMirrorViewDepth();
        view.radius = Math.min(configured, view.radius);
        // A sweep redraws a viewer who has not moved, to grow their view; a move redraws them
        // on the way somewhere, and may be one of four this second.
        final int most = fromSweep ? MOST_STILL : MOST_CANDIDATES;
        List<Entity> inside = new ArrayList<>();
        Budget budget = new Budget(most);
        Map<Long, BlockData> wanted = compose(eye, seeing, now, inside, budget, view.radius);
        // Too much to draw from here: draw as far as the walk got in full, which it can
        // afford by construction, until it fits. Three tries, in case the eye moved closer.
        for (int shrink = 0; (budget.blocks <= 0) && (view.radius > 4) && (shrink < 3); shrink++)
        {
            view.radius = Math.max(4, Math.min(view.radius - 1, budget.reached));
            inside = new ArrayList<>();
            budget = new Budget(most);
            wanted = compose(eye, seeing, now, inside, budget, view.radius);
        }
        final int drawnAt = view.radius;
        view.radius = grown(view.radius, configured, budget.blocks, most);
        view.growing = (view.radius < configured) && (budget.blocks > (most / 2));
        send(player, view, wanted, now, crossed || ((now - view.fullAt) >= RESEND_MILLIS));
        veil(player, view, inside);
        view.lastRedraw = (most - budget.blocks) + " of " + most + " blocks walked"
            + ((budget.blocks <= 0) ? " (budget spent)" : "") + " at radius " + drawnAt
            + ((view.radius != drawnAt) ? (", next " + view.radius) : "") + ", "
            + budget.near + " drawn, eye " + (int) eye.getX() + ","
            + (int) eye.getY() + "," + (int) eye.getZ() + ", took " + (now() - now) + " ms";
        view.mirrors = names(seeing);
        view.eye = eyeKey(eye);
        view.chunk = chunk;
        view.generation = MirrorCaptures.generation();
        // From when the redraw finished, not when it began, so a slow one still leaves a gap.
        view.composedAt = now();
        if (wanted.isEmpty())
        {
            VIEWS.remove(id);
        }
    }

    /** Whether nothing a view depends on has changed since it was last drawn. */
    private static boolean unchanged(final View view, final List<Window> seeing, final long eye,
        final long now)
    {
        return (view.eye == eye) && ((now - view.composedAt) < RESAMPLE_MILLIS)
            && (view.generation == MirrorCaptures.generation()) && view.mirrors.equals(names(seeing))
            && !(view.growing && ((now - view.composedAt) >= REDRAW_MILLIS));
    }

    /**
     * How far a view reaches next time, given what the last redraw had left.
     *
     * <p>The cost of a view grows with the cube of its radius, so the radius grows by the cube
     * root of the room to spare, at most half again, and never past the configured depth. Two
     * blocks at a time took a dozen redraws to recover from one close approach, and a viewer
     * who then stood still never recovered at all.
     *
     * @param radius
     *            the radius just drawn
     * @param configured
     *            the most allowed
     * @param left
     *            how much of the block budget the redraw left
     * @param most
     *            what that budget was
     * @return the radius for the next redraw
     */
    static int grown(final int radius, final int configured, final int left, final int most)
    {
        if ((left <= (most / 2)) || (radius >= configured))
        {
            return Math.min(radius, configured);
        }
        final double used = Math.max(1.0, most - left);
        final double scaled = radius * Math.cbrt((most / 2.0) / used);
        final int next = (int) Math.min(scaled, (radius * 3) / 2.0);
        return Math.min(configured, Math.max(radius + 2, next));
    }

    /** Queues one redraw for a viewer who moved too soon after the last, if none is queued. */
    private static void catchUpLater(final Player player, final View view)
    {
        if (view.catchUpQueued)
        {
            return;
        }
        try
        {
            WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(),
                () -> catchUp(player), REDRAW_TICKS);
            view.catchUpQueued = true;
        }
        catch (final RuntimeException noScheduler)
        {
            // No scheduler yet, during startup or in tests. The next move or sweep catches up.
        }
    }

    /** Draws a viewer from where they last moved to, if that was never drawn. */
    private static void catchUp(final Player player)
    {
        final View view = VIEWS.get(player.getUniqueId());
        if (view == null)
        {
            return;
        }
        view.catchUpQueued = false;
        final Location eye = view.pendingEye;
        view.pendingEye = null;
        if ((eye != null) && player.isOnline())
        {
            update(player, eye, now(), false);
        }
    }

    /**
     * The windows a player is close enough to, in front of, and can actually see, in a stable
     * order.
     *
     * <p>Seeing means a clear line from the eye to some open block of the opening, through the
     * real world. Without that, somebody in a corridor was "in front of" every alcove mirror on
     * the same wall, and a redraw spent its whole budget walking the cones of four mirrors the
     * corridor walls hid from them, cutting short the one they were looking at.
     */
    private static List<Window> seenBy(final Player player, final Location eye)
    {
        final double radius = ConfigManager.getMirrorProximityRadius();
        final Location at = player.getLocation();
        final long now = now();
        final List<Window> seeing = new ArrayList<>();
        for (final Window window : WINDOWS.values())
        {
            if (!window.open.isEmpty() && window.banner.getWorld().equals(player.getWorld())
                && (at.distanceSquared(window.banner.getLocation()) <= (radius * radius))
                && window.shape.inFront(eye.getX(), eye.getZ())
                && canSee(player.getWorld(), eye, window, now))
            {
                seeing.add(window);
            }
        }
        seeing.sort(Comparator.comparing(window -> window.mirror.name()));
        return seeing;
    }

    /** Whether a clear line runs from an eye to any open block of a window's opening. */
    private static boolean canSee(final World here, final Location eye, final Window window,
        final long now)
    {
        for (final Spot cell : window.open)
        {
            if (clearLine(here, eye, cell, window.shape.into(), now))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether nothing solid stands between an eye and the middle of a block's front face.
     *
     * <p>The front face, not the block's middle: seen from an angle, the line to the middle
     * passes through the wall block beside the opening first, and the opening would count as
     * hidden while its face was in plain view. Walked in short steps, stopping just short of the
     * face. Solid means occluding, so glass and the banner in front do not count as in the way.
     */
    private static boolean clearLine(final World here, final Location eye, final Spot cell,
        final Spot into, final long now)
    {
        final double dx = ((cell.x() + 0.5) - (0.51 * into.x())) - eye.getX();
        final double dy = (cell.y() + 0.5) - eye.getY();
        final double dz = ((cell.z() + 0.5) - (0.51 * into.z())) - eye.getZ();
        final double length = Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
        for (double along = 0.4; along < length; along += 0.4)
        {
            final double t = along / length;
            final int x = (int) Math.floor(eye.getX() + (dx * t));
            final int y = (int) Math.floor(eye.getY() + (dy * t));
            final int z = (int) Math.floor(eye.getZ() + (dz * t));
            if (solidHere(here, x, y, z, now))
            {
                return false;
            }
        }
        return true;
    }

    /** Whether the real block here hides what is behind it, remembered for a few seconds. */
    private static boolean solidHere(final World here, final int x, final int y, final int z,
        final long now)
    {
        if ((now - solidReadAt) >= RESAMPLE_MILLIS)
        {
            SOLID.clear();
            solidReadAt = now;
        }
        return SOLID.computeIfAbsent(here.getName(), name -> new HashMap<>()).computeIfAbsent(
            key(x, y, z), cell -> here.isChunkLoaded(x >> 4, z >> 4)
                && here.getBlockAt(x, y, z).getBlockData().isOccluding());
    }

    /** Whether somebody not yet looking into anything has just come within range of a window. */
    private static boolean nearAWindow(final Player player, final Location to)
    {
        final double radius = ConfigManager.getMirrorProximityRadius();
        for (final Window window : WINDOWS.values())
        {
            if (window.banner.getWorld().equals(player.getWorld())
                && (to.distanceSquared(window.banner.getLocation()) <= (radius * radius)))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Every block one viewer should be shown, and what as; and every creature of their own world
     * standing inside the view, to be hidden from them.
     *
     * <p>Openings first, so a block that is part of one opening is never drawn as another's far
     * side. Then, nearest window first, the blocks in the cone from the eye through each opening,
     * out to the radius and within one budget for the whole redraw -- see {@link Pass} for which
     * of them are drawn.
     */
    private static Map<Long, BlockData> compose(final Location eye, final List<Window> seeing,
        final long now, final List<Entity> inside, final Budget budget, final int radius)
    {
        final BlockData air = Bukkit.createBlockData(Material.AIR);
        final BlockData barrier = Bukkit.createBlockData(Material.BARRIER);
        final Map<Long, BlockData> wanted = new HashMap<>();
        final Set<Long> allOpen = new HashSet<>();
        seeing.forEach(window -> allOpen.addAll(window.openKeys));
        for (final Window window : seeing)
        {
            // Only where the banner's patterns can be sent back afterwards. On plain 1.20 it
            // stays hanging in front of the view.
            if (MirrorPackets.available())
            {
                final MirrorBlock banner = window.mirror.banner();
                wanted.put(key(banner.x(), banner.y(), banner.z()), air);
            }
            window.open.forEach(cell -> wanted.put(key(cell.x(), cell.y(), cell.z()), barrier));
        }
        final List<Pass> passes = new ArrayList<>();
        for (final Window window : nearestFirst(seeing, eye))
        {
            refreshSolid(window, now);
            passes.add(new Pass(eye, window, seeing, allOpen, wanted, air, radius, budget, now));
        }
        // In stages of depth, and within each stage the middle of every view before its edges,
        // so a spent budget costs what is far and oblique, never what is near. Band by band to
        // full depth, a wide radius spent the whole budget on the far middle before the near
        // sides were walked at all, and an alcove lost its own walls to a corridor beyond them.
        stages:
        for (int from = 1; from <= (radius + 1); from = nextStage(from))
        {
            final int to = Math.min(radius + 1, nextStage(from) - 1);
            for (int band = 0; band < MirrorWindow.bands(); band++)
            {
                for (final Pass pass : passes)
                {
                    pass.stage(from, to);
                    if (!pass.window.shape.forEachCandidate(eye.getX(), eye.getY(), eye.getZ(),
                        radius, band, pass, (x, y, z) -> !pass.consider(x, y, z) || (--budget.blocks > 0)))
                    {
                        break stages;
                    }
                }
            }
            budget.reached = to;
        }
        if (!seeing.isEmpty())
        {
            creaturesInside(seeing.get(0).banner.getWorld(), eye, radius, seeing, allOpen, inside);
        }
        return wanted;
    }

    /**
     * The viewer's own world's creatures standing inside the view.
     *
     * <p>A drawn block hides what is behind it, but a creature is not a block: an armour stand
     * on the real side kept standing in the middle of the far side. Anything within the radius
     * whose position is seen through an opening is hidden from the viewer, and shown again when
     * it is not. Other players are left alone: hiding one would take them off the tab list too.
     */
    private static void creaturesInside(final World here, final Location eye, final int radius,
        final List<Window> seeing, final Set<Long> allOpen, final List<Entity> inside)
    {
        final double reach = radius + 1.0;
        for (final Entity entity : here.getNearbyEntities(eye, reach, reach, reach))
        {
            if (entity instanceof Player)
            {
                continue;
            }
            final Location at = entity.getLocation();
            final int x = at.getBlockX();
            final int y = at.getBlockY();
            final int z = at.getBlockZ();
            for (final Window window : seeing)
            {
                final double[] rect = seenThrough(eye, window, x, y, z, seeing, allOpen);
                if ((rect != null) && coveredBy(window, rect, allOpen, Set.of()))
                {
                    inside.add(entity);
                    break;
                }
            }
        }
    }

    /** Hides from a viewer what is now inside their view, and shows again what no longer is. */
    private static void veil(final Player player, final View view, final List<Entity> inside)
    {
        final Map<UUID, Entity> now = new HashMap<>();
        inside.forEach(entity -> now.put(entity.getUniqueId(), entity));
        for (final Map.Entry<UUID, Entity> entry : view.veiled.entrySet())
        {
            if (!now.containsKey(entry.getKey()))
            {
                try
                {
                    player.showEntity(WormholeXTreme.getThisPlugin(), entry.getValue());
                }
                catch (final RuntimeException gone)
                {
                    // An entity that has since left the world is nothing to show.
                }
            }
        }
        for (final Map.Entry<UUID, Entity> entry : now.entrySet())
        {
            if (!view.veiled.containsKey(entry.getKey()))
            {
                try
                {
                    player.hideEntity(WormholeXTreme.getThisPlugin(), entry.getValue());
                }
                catch (final RuntimeException refused)
                {
                    now.remove(entry.getKey());
                }
            }
        }
        view.veiled.clear();
        view.veiled.putAll(now);
    }

    /**
     * The first layer of the stage after one starting here: 1, 5, 9, 17, 25, 33, 41 ...
     *
     * <p>Doubling to 17, then eight at a time: a spent budget falls back to the last stage
     * walked in full, and a stage of sixteen layers threw away half a view that fit.
     */
    private static int nextStage(final int from)
    {
        if (from < 5)
        {
            return 5;
        }
        return (from < 17) ? (((from - 1) * 2) + 1) : (from + 8);
    }

    /** What one redraw may spend, across every window a viewer sees, and what it drew. */
    private static final class Budget
    {
        private int blocks;
        private int near;
        /** The deepest layer every view was walked to in full before the budget ran out. */
        private int reached;

        Budget(final int most)
        {
            this.blocks = most;
        }
    }

    /**
     * Where a block behind the opening appears on it, if it is seen through this window and only
     * through it.
     *
     * <p>Seen through the opening, all of it hidden by solid blocks or the opening itself --
     * never across open air or another window's opening -- and through no opening in the same
     * face whose middle the line of sight passes nearer.
     *
     * @return the block's outline on the opening's face, or null if it is not seen through it
     */
    private static double[] seenThrough(final Location eye, final Window window, final int x,
        final int y, final int z, final List<Window> seeing, final Set<Long> allOpen)
    {
        final double[] rect = window.shape.projected(eye.getX(), eye.getY(), eye.getZ(), x, y, z);
        if ((rect == null) || !window.shape.overlaps(rect, window.open))
        {
            return null;
        }
        final double mine = window.shape.offCentre(rect);
        for (final Window other : seeing)
        {
            if ((other != window) && other.shape.sharesFace(window.shape)
                && other.shape.overlaps(rect, other.open) && (other.shape.offCentre(rect) < mine))
            {
                return null;
            }
        }
        return rect;
    }

    /** Whether most of a block's outline on the face lands where nothing outside can see it. */
    private static boolean coveredBy(final Window window, final double[] rect,
        final Set<Long> allOpen, final Set<Long> shielded)
    {
        return window.shape.covered(rect, (across, up) -> clear(window, across, up, allOpen, shielded));
    }

    /** A viewer's windows, nearest first, so a spent budget cuts the furthest views short. */
    private static List<Window> nearestFirst(final List<Window> seeing, final Location eye)
    {
        final List<Window> sorted = new ArrayList<>(seeing);
        sorted.sort(Comparator
            .comparingDouble((Window window) -> window.banner.getLocation().distanceSquared(eye))
            .thenComparing(window -> window.mirror.name()));
        return sorted;
    }

    /** The highest block that is not air in a real column, remembered for a few seconds. */
    private static int topHere(final World here, final int x, final int z, final long now)
    {
        if ((now - emptyReadAt) >= RESAMPLE_MILLIS)
        {
            EMPTY.clear();
            TOPS.clear();
            emptyReadAt = now;
        }
        if (!here.isChunkLoaded(x >> 4, z >> 4))
        {
            return Integer.MAX_VALUE;
        }
        return TOPS.computeIfAbsent(here.getName(), name -> new HashMap<>()).computeIfAbsent(
            chunkKey(x, z), column -> here.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE));
    }

    /** Whether the real block here is empty, remembered for a few seconds. */
    private static boolean emptyHere(final World here, final int x, final int y, final int z,
        final long now)
    {
        if ((now - emptyReadAt) >= RESAMPLE_MILLIS)
        {
            EMPTY.clear();
            emptyReadAt = now;
        }
        return EMPTY.computeIfAbsent(here.getName(), name -> new HashMap<>()).computeIfAbsent(
            key(x, y, z), cell -> here.isChunkLoaded(x >> 4, z >> 4) && here.getBlockAt(x, y, z).isEmpty());
    }

    /**
     * Whether a block of a window's face keeps a drawn block behind it out of sight elsewhere:
     * the opening itself, a solid block of the wall, or a block of the face hidden from this eye
     * by something real in front of it.
     */
    private static boolean clear(final Window window, final int across, final int y,
        final Set<Long> allOpen, final Set<Long> shielded)
    {
        final long face = faceKey(window.shape, across, y);
        return window.openKeys.contains(face)
            || ((window.solid.contains(face) || shielded.contains(face)) && !allOpen.contains(face));
    }

    /**
     * The blocks of a window's face hidden from an eye by real solid blocks in front of it.
     *
     * <p>A corridor's walls, a hut's sides and roof: every solid block on the viewer's side
     * within reach of the opening is thrown onto the face plane from the eye, and the face
     * blocks wholly inside its shadow are hidden. That is what makes a hut or a corridor work
     * like a thick wall, instead of leaving holes wherever a drawn block's outline strayed past
     * a three-block-wide wall into the open air beside it.
     */
    private static Set<Long> shielded(final Window window, final Location eye, final long now)
    {
        final MirrorWindow shape = window.shape;
        final World here = window.banner.getWorld();
        final Spot into = shape.into();
        final boolean alongX = into.x() != 0;
        final int centre = alongX ? shape.base().z() : shape.base().x();
        final int reach = SURROUND + MirrorWindow.WIDTH;
        final Set<Long> hidden = new HashSet<>();
        for (int front = 1; front <= SURROUND; front++)
        {
            final int along = (alongX ? shape.base().x() : shape.base().z()) - (front * (alongX ? into.x() : into.z()));
            for (int across = centre - reach; across <= (centre + reach); across++)
            {
                for (int y = shape.base().y() - SURROUND;
                    y <= (shape.base().y() + MirrorWindow.HEIGHT + SURROUND); y++)
                {
                    final int x = alongX ? along : across;
                    final int z = alongX ? across : along;
                    if (!solidHere(here, x, y, z, now))
                    {
                        continue;
                    }
                    final double[] rect = shape.shadow(eye.getX(), eye.getY(), eye.getZ(), x, y, z);
                    if (rect == null)
                    {
                        continue;
                    }
                    for (int a = (int) Math.ceil(rect[0]); (a + 1) <= rect[1]; a++)
                    {
                        for (int b = (int) Math.ceil(rect[2]); (b + 1) <= rect[3]; b++)
                        {
                            hidden.add(faceKey(shape, a, b));
                        }
                    }
                }
            }
        }
        return hidden;
    }

    /** The block of a window's face at a coordinate along it. */
    private static long faceKey(final MirrorWindow shape, final int across, final int y)
    {
        return (shape.into().x() != 0) ? key(shape.base().x(), y, across)
            : key(across, y, shape.base().z());
    }

    /** Reads again which blocks around a window's opening are solid, if that reading is old. */
    private static void refreshSolid(final Window window, final long now)
    {
        if ((now - window.solidAt) < RESAMPLE_MILLIS)
        {
            return;
        }
        final MirrorWindow shape = window.shape;
        final World here = window.banner.getWorld();
        final int centre = (shape.into().x() != 0) ? shape.base().z() : shape.base().x();
        final int reach = SURROUND + MirrorWindow.WIDTH;
        final Set<Long> solid = new HashSet<>();
        for (int across = centre - reach; across <= (centre + reach); across++)
        {
            for (int y = shape.base().y() - SURROUND;
                y <= (shape.base().y() + MirrorWindow.HEIGHT + SURROUND); y++)
            {
                final long face = faceKey(shape, across, y);
                if (here.getBlockAt(unpackX(face), y, unpackZ(face)).getBlockData().isOccluding())
                {
                    solid.add(face);
                }
            }
        }
        window.solid = solid;
        window.solidAt = now;
    }

    /** Sends a viewer what changed since their last drawing, or all of it when asked to. */
    private static void send(final Player player, final View view,
        final Map<Long, BlockData> wanted, final long now, final boolean full)
    {
        final List<BlockState> changes = new ArrayList<>();
        view.undrawable = 0;
        for (final Map.Entry<Long, BlockData> entry : wanted.entrySet())
        {
            if (full || !entry.getValue().equals(view.drawn.get(entry.getKey())))
            {
                final BlockState state = drawn(view.world, entry.getKey(), entry.getValue());
                if (state != null)
                {
                    changes.add(state);
                }
                else
                {
                    view.undrawable++;
                }
            }
        }
        final List<TileState> restored = new ArrayList<>();
        for (final Long cell : view.drawn.keySet())
        {
            if (!wanted.containsKey(cell))
            {
                truth(changes, restored, view.world, cell);
            }
        }
        if (!changes.isEmpty())
        {
            player.sendBlockChanges(changes);
        }
        // A block change carries no banner patterns or sign text, so those follow on their own.
        restored.forEach(tile -> MirrorPackets.send(player, tile.getLocation(), tile));
        view.drawn = wanted;
        if (full)
        {
            view.fullAt = now;
        }
    }

    /** One block, drawn as something else, or null if this world cannot draw it. */
    private static BlockState drawn(final World here, final long cell, final BlockData data)
    {
        final int x = unpackX(cell);
        final int z = unpackZ(cell);
        if (!here.isChunkLoaded(x >> 4, z >> 4))
        {
            return null;
        }
        // Reused rather than read again: a redraw as somebody walks would otherwise snapshot
        // every changed block from the world, only to overwrite what it read.
        final Map<Long, BlockState> states = STATES.computeIfAbsent(here.getName(),
            name -> new HashMap<>());
        BlockState state = states.get(cell);
        if (state == null)
        {
            state = here.getBlockAt(x, unpackY(cell), z).getState();
            states.put(cell, state);
        }
        try
        {
            state.setBlockData(data);
        }
        catch (final IllegalArgumentException refused)
        {
            // A block entity's state will not always take another block's data. That one block
            // shows as it really is.
            return null;
        }
        return state;
    }

    /** Adds one block as the world really has it. */
    private static void truth(final List<BlockState> changes, final List<TileState> restored,
        final World here, final long cell)
    {
        final int x = unpackX(cell);
        final int z = unpackZ(cell);
        // A chunk that has unloaded is one the client dropped too, and it gets a fresh copy.
        if (!here.isChunkLoaded(x >> 4, z >> 4))
        {
            return;
        }
        final BlockState state = here.getBlockAt(x, unpackY(cell), z).getState();
        changes.add(state);
        if (state instanceof TileState tile)
        {
            restored.add(tile);
        }
    }

    /** Lets go of reused block states that no view is drawing any more. */
    private static void trimStates(final long now)
    {
        if ((now - trimmedAt) < RESAMPLE_MILLIS)
        {
            return;
        }
        trimmedAt = now;
        final Map<String, Set<Long>> drawing = new HashMap<>();
        VIEWS.values().forEach(view -> drawing
            .computeIfAbsent(view.world.getName(), name -> new HashSet<>()).addAll(view.drawn.keySet()));
        STATES.entrySet().removeIf(entry ->
        {
            final Set<Long> still = drawing.get(entry.getKey());
            if (still == null)
            {
                return true;
            }
            entry.getValue().keySet().retainAll(still);
            return false;
        });
    }

    /** The blocks of a window's opening with nothing solid in front of them. */
    private static List<Spot> openCells(final MirrorWindow shape, final World here)
    {
        final List<Spot> open = new ArrayList<>();
        shape.forEachOpening((x, y, z) ->
        {
            // Something in front of part of the opening closes that part: nobody sees through
            // it, and a neighbouring window may be using the space behind.
            if (here.getBlockAt(x - shape.into().x(), y, z - shape.into().z()).isPassable())
            {
                open.add(new Spot(x, y, z));
            }
        });
        return open;
    }

    private static Set<String> names(final List<Window> windows)
    {
        final Set<String> names = new HashSet<>();
        windows.forEach(window -> names.add(window.mirror.name()));
        return names;
    }

    private static long now()
    {
        return System.currentTimeMillis();
    }

    /**
     * An eye position, to a quarter of a block, as a key that can be compared cheaply.
     *
     * <p>Half a block was too coarse right up against a mirror, where half a block nearer is twice
     * as wide a view: a player could step in and keep the narrower view drawn from further back,
     * with the real world showing round its edges.
     */
    private static long eyeKey(final Location eye)
    {
        return key((int) Math.floor(eye.getX() * 4.0), (int) Math.floor(eye.getY() * 4.0),
            (int) Math.floor(eye.getZ() * 4.0));
    }

    /** The chunk an eye is in, as a key. */
    private static long chunkOf(final Location eye)
    {
        return chunkKey(((int) Math.floor(eye.getX())) >> 4, ((int) Math.floor(eye.getZ())) >> 4);
    }

    private static long chunkKey(final int x, final int z)
    {
        return (((long) x) << 32) | (z & 0xFFFFFFFFL);
    }

    /**
     * A block position packed into one long: 26 bits of x, 26 of z, 12 of y.
     *
     * @param x
     *            x
     * @param y
     *            y
     * @param z
     *            z
     * @return the key
     */
    static long key(final int x, final int y, final int z)
    {
        return ((x & 0x3FFFFFFL) << 38) | ((z & 0x3FFFFFFL) << 12) | (y & 0xFFFL);
    }

    static int unpackX(final long key)
    {
        return (int) (key >> 38);
    }

    static int unpackY(final long key)
    {
        return (int) ((key << 52) >> 52);
    }

    static int unpackZ(final long key)
    {
        return (int) ((key << 26) >> 38);
    }

    /**
     * One window's part of one redraw: which blocks in its cone are drawn, and as what.
     *
     * <p>Within the radius of the eye, a block is drawn as the capture's block it maps to, if it
     * is seen through this window ({@link #seenThrough}), is not already hidden behind a solid
     * block drawn nearer the eye, and would change what the client shows -- far-side air over a
     * block that is really empty would not.
     *
     * <p>Past the radius nothing is drawn, so a line of sight that gets that far meets whatever
     * the real world has there. The shell that closed the view was painted, then fog, then sky,
     * and none of them looked right.
     */
    private static final class Pass implements MirrorWindow.Limits
    {
        private final Location eye;
        private final Window window;
        private final List<Window> seeing;
        private final Set<Long> allOpen;
        private final Map<Long, BlockData> wanted;
        private final BlockData air;
        private final double radius;
        private final Budget budget;
        private final long now;
        private final World here;
        private final int min;
        private final int max;
        private final Occlusion hidden;
        private final Set<Long> shielded;
        private int from = 1;
        private int to = Integer.MAX_VALUE;

        Pass(final Location eye, final Window window, final List<Window> seeing,
            final Set<Long> allOpen, final Map<Long, BlockData> wanted, final BlockData air,
            final int radius, final Budget budget, final long now)
        {
            this.eye = eye;
            this.window = window;
            this.seeing = seeing;
            this.allOpen = allOpen;
            this.wanted = wanted;
            this.air = air;
            this.radius = radius;
            this.budget = budget;
            this.now = now;
            this.here = window.banner.getWorld();
            this.min = here.getMinHeight();
            this.max = here.getMaxHeight();
            this.hidden = new Occlusion(window);
            this.shielded = shielded(window, eye, now);
        }

        /** @return true if this block cost real work, which the budget counts; false if it was cheap */
        boolean consider(final int x, final int y, final int z)
        {
            final long cell = key(x, y, z);
            if ((y < min) || (y >= max) || wanted.containsKey(cell))
            {
                probe(x, y, z, "already");
                return false;
            }
            final double dx = (x + 0.5) - eye.getX();
            final double dy = (y + 0.5) - eye.getY();
            final double dz = (z + 0.5) - eye.getZ();
            final double distance = Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
            if (distance >= radius)
            {
                probe(x, y, z, "beyond");
                return false;
            }
            final double[] rect = seenThrough(eye, window, x, y, z, seeing, allOpen);
            final int layer = layerOf(x, z);
            if (rect == null)
            {
                probe(x, y, z, "not through the opening");
                return true;
            }
            if (hidden.covers(rect, layer))
            {
                probe(x, y, z, "hidden behind nearer solid, rect " + java.util.Arrays.toString(rect));
                return true;
            }
            final Spot at = window.shape.farOf(x, y, z);
            final MirrorCapture capture = window.capture;
            final boolean farAir = capture.isAir(at.x(), at.y(), at.z());
            if (!coveredBy(window, rect, allOpen, shielded))
            {
                // A block straddling the edge, part of it where the real world can see it: left
                // alone, solid or air, since drawing it either way shows the far side past the
                // edge. Carving the air ones cut notches beside small freestanding mirrors.
                probe(x, y, z, "not covered, rect " + java.util.Arrays.toString(rect));
                return true;
            }
            final BlockData data = farAir ? air : capture.at(at.x(), at.y(), at.z());
            if (data.isOccluding())
            {
                hidden.add(rect, layer);
            }
            if (!farAir || !emptyHere(here, x, y, z, now))
            {
                wanted.put(cell, data);
                budget.near++;
                probe(x, y, z, "drawn " + data.getAsString() + " at layer " + layer + ", rect "
                    + java.util.Arrays.toString(rect));
            }
            else
            {
                probe(x, y, z, "air over air");
            }
            return true;
        }

        /** Confines the next walk to these layers. */
        void stage(final int firstLayer, final int lastLayer)
        {
            from = firstLayer;
            to = lastLayer;
        }

        @Override
        public int shallowest()
        {
            return from;
        }

        @Override
        public int deepest()
        {
            return Math.min(to, hidden.horizon());
        }

        /**
         * Above both the highest real block and the highest far one that column maps to,
         * everything is air over air.
         */
        @Override
        public int top(final int x, final int z)
        {
            final Spot column = window.shape.farOf(x, window.shape.base().y(), z);
            final int farTop = window.capture.top(column.x(), column.z());
            // The far column's top, brought back to this world's heights.
            final int farTopHere = farTop + (window.shape.base().y() - window.shape.far().y());
            return Math.max(topHere(here, x, z, now), farTopHere);
        }

        private int layerOf(final int x, final int z)
        {
            return ((x - window.shape.base().x()) * window.shape.into().x())
                + ((z - window.shape.base().z()) * window.shape.into().z());
        }
    }

    /**
     * How much of one window's opening is hidden, from one eye, by solid far-side blocks drawn in
     * front of what is being considered.
     *
     * <p>A block wholly behind solid blocks already drawn nearer the eye cannot be seen and is
     * skipped -- and once every part of the opening is hidden, nothing deeper than the deepest of
     * what hides it is walked at all. A view into a hillside stops at the hillside instead of
     * drawing the inside of the hill.
     *
     * <p>Kept on a fine grid over the opening, thirty-two parts to a block, each remembering the
     * layer of the nearest solid block in front of it. The layer matters because the cone is
     * walked in bands, middle first: a solid block far back in the middle band must not hide one
     * nearer the eye in the next.
     *
     * <p>Thirty-two rather than eight because of slivers. A corridor behind a row of pillars was
     * visible through a tenth of a block of the opening; at eight parts to a block that rounded
     * away, the grid called the whole opening hidden five layers in, the walk stopped there, and
     * through the sliver the viewer saw their own world. Marking a part hidden only when a block
     * covers it whole would keep slivers but let a lattice of parts along every block boundary
     * stay open, and draw everything behind a solid wall; finer parts keep both.
     */
    /**
     * Which parts of the opening solid far-side blocks have covered, and from how near.
     *
     * <p>A grid over the opening's face, thirty-two parts to a block, and for each part the
     * exact rectangle of it that solid blocks' outlines have covered so far, with the layer of
     * the farthest of those blocks. A block is hidden only where the whole of its outline lies
     * inside rectangles covered from nearer than it.
     *
     * <p>A part used to be marked hidden, whole, when a block's outline crossed its middle. From
     * an eye a third of a block from the opening a floor row twenty blocks in is a band far
     * thinner than a part, and once one row had marked the part every farther row in it was
     * called hidden: the floor vanished in patches, and the corridor's shelves, seen edge-on,
     * with it. Two outlines that meet in a part are joined only when the join is itself a
     * rectangle -- the same span across, one above the other, or the same span up, side by
     * side -- since the rectangle round an L claims the corner neither covers, and a shelf and
     * the floor meeting at such a corner let twenty-two rays in eighty thousand through to
     * the lake behind the mirror. Otherwise the larger of the two is kept.
     */
    static final class Occlusion
    {
        private static final int FINE = 32;
        private static final double EPSILON = 1.0e-9;

        private final int left;
        private final int bottom;
        private final int wide;
        private final int tall;
        private final int[] nearest;
        private final double[] x0;
        private final double[] x1;
        private final double[] y0;
        private final double[] y1;
        private int marked;
        private int full;
        private int horizon = Integer.MAX_VALUE;

        Occlusion(final Window window)
        {
            this(leftOf(window), bottomOf(window), widthOf(window), heightOf(window));
            // The parts of the opening's outline that are closed hide everything behind them.
            for (int i = 0; i < wide; i++)
            {
                for (int j = 0; j < tall; j++)
                {
                    if (!window.openKeys.contains(faceKey(window.shape, left + (i / FINE), bottom + (j / FINE))))
                    {
                        mark((i * tall) + j, cellX0(i), cellX1(i), cellY0(j), cellY1(j), 0);
                    }
                }
            }
        }

        /** A grid over an opening so many blocks wide and tall, all of it open. */
        Occlusion(final int left, final int bottom, final int blocksWide, final int blocksTall)
        {
            this.left = left;
            this.bottom = bottom;
            this.wide = blocksWide * FINE;
            this.tall = blocksTall * FINE;
            nearest = new int[wide * tall];
            java.util.Arrays.fill(nearest, Integer.MAX_VALUE);
            x0 = new double[wide * tall];
            x1 = new double[wide * tall];
            y0 = new double[wide * tall];
            y1 = new double[wide * tall];
        }

        private static int leftOf(final Window window)
        {
            final boolean alongX = window.shape.into().x() != 0;
            int least = Integer.MAX_VALUE;
            for (final Spot cell : window.open)
            {
                least = Math.min(least, alongX ? cell.z() : cell.x());
            }
            return least;
        }

        private static int widthOf(final Window window)
        {
            final boolean alongX = window.shape.into().x() != 0;
            int most = Integer.MIN_VALUE;
            for (final Spot cell : window.open)
            {
                most = Math.max(most, alongX ? cell.z() : cell.x());
            }
            return (most + 1) - leftOf(window);
        }

        private static int bottomOf(final Window window)
        {
            int least = Integer.MAX_VALUE;
            for (final Spot cell : window.open)
            {
                least = Math.min(least, cell.y());
            }
            return least;
        }

        private static int heightOf(final Window window)
        {
            int most = Integer.MIN_VALUE;
            for (final Spot cell : window.open)
            {
                most = Math.max(most, cell.y());
            }
            return (most + 1) - bottomOf(window);
        }

        private double cellX0(final int i)
        {
            return left + ((double) i / FINE);
        }

        private double cellX1(final int i)
        {
            return left + ((double) (i + 1) / FINE);
        }

        private double cellY0(final int j)
        {
            return bottom + ((double) j / FINE);
        }

        private double cellY1(final int j)
        {
            return bottom + ((double) (j + 1) / FINE);
        }

        /**
         * @return the deepest layer anything could still be seen at: past the deepest solid
         *         block hiding each part of the opening, once every part is hidden whole
         */
        int horizon()
        {
            if ((full < nearest.length) || (horizon != Integer.MAX_VALUE))
            {
                return horizon;
            }
            int deepest = 0;
            for (final int layer : nearest)
            {
                deepest = Math.max(deepest, layer);
            }
            horizon = deepest;
            return horizon;
        }

        /** Whether the whole of a projected block's outline lies within what nearer blocks cover. */
        boolean covers(final double[] rect, final int layer)
        {
            if (marked == 0)
            {
                return false;
            }
            final int iFrom = Math.max(0, (int) Math.floor((rect[0] - left) * FINE));
            final int iTo = Math.min(wide - 1, (int) Math.ceil((rect[1] - left) * FINE) - 1);
            final int jFrom = Math.max(0, (int) Math.floor((rect[2] - bottom) * FINE));
            final int jTo = Math.min(tall - 1, (int) Math.ceil((rect[3] - bottom) * FINE) - 1);
            for (int i = iFrom; i <= iTo; i++)
            {
                for (int j = jFrom; j <= jTo; j++)
                {
                    final int at = (i * tall) + j;
                    if (nearest[at] >= layer)
                    {
                        return false;
                    }
                    if ((Math.max(rect[0], cellX0(i)) < (x0[at] - EPSILON))
                        || (Math.min(rect[1], cellX1(i)) > (x1[at] + EPSILON))
                        || (Math.max(rect[2], cellY0(j)) < (y0[at] - EPSILON))
                        || (Math.min(rect[3], cellY1(j)) > (y1[at] + EPSILON)))
                    {
                        return false;
                    }
                }
            }
            return true;
        }

        /** Covers the parts of the opening a solid block's outline lies on, as far as it does. */
        void add(final double[] rect, final int layer)
        {
            final int iFrom = Math.max(0, (int) Math.floor((rect[0] - left) * FINE));
            final int iTo = Math.min(wide - 1, (int) Math.ceil((rect[1] - left) * FINE) - 1);
            final int jFrom = Math.max(0, (int) Math.floor((rect[2] - bottom) * FINE));
            final int jTo = Math.min(tall - 1, (int) Math.ceil((rect[3] - bottom) * FINE) - 1);
            for (int i = iFrom; i <= iTo; i++)
            {
                for (int j = jFrom; j <= jTo; j++)
                {
                    mark((i * tall) + j, Math.max(rect[0], cellX0(i)), Math.min(rect[1], cellX1(i)),
                        Math.max(rect[2], cellY0(j)), Math.min(rect[3], cellY1(j)), layer);
                }
            }
        }

        /**
         * Covers part of one part of the grid.
         *
         * <p>A rectangle that holds what was covered before replaces it, with its own layer. One
         * inside what was covered changes nothing. One that meets it along a whole side is
         * joined to it, with the farther of the two layers, since only past both is everything
         * in the join hidden. Otherwise the larger of the two stands.
         */
        private void mark(final int at, final double nx0, final double nx1, final double ny0,
            final double ny1, final int layer)
        {
            if (((nx1 - nx0) <= EPSILON) || ((ny1 - ny0) <= EPSILON))
            {
                return;
            }
            final boolean wasFull = isFull(at);
            if (nearest[at] == Integer.MAX_VALUE)
            {
                marked++;
                set(at, nx0, nx1, ny0, ny1, layer);
            }
            else if ((nx0 <= (x0[at] + EPSILON)) && (nx1 >= (x1[at] - EPSILON))
                && (ny0 <= (y0[at] + EPSILON)) && (ny1 >= (y1[at] - EPSILON)))
            {
                set(at, nx0, nx1, ny0, ny1, layer);
            }
            else if ((nx0 >= (x0[at] - EPSILON)) && (nx1 <= (x1[at] + EPSILON))
                && (ny0 >= (y0[at] - EPSILON)) && (ny1 <= (y1[at] + EPSILON)))
            {
                return;
            }
            else if (sameSpan(nx0, nx1, x0[at], x1[at]) && (ny0 <= (y1[at] + EPSILON))
                && (ny1 >= (y0[at] - EPSILON)))
            {
                set(at, x0[at], x1[at], Math.min(ny0, y0[at]), Math.max(ny1, y1[at]),
                    Math.max(layer, nearest[at]));
            }
            else if (sameSpan(ny0, ny1, y0[at], y1[at]) && (nx0 <= (x1[at] + EPSILON))
                && (nx1 >= (x0[at] - EPSILON)))
            {
                set(at, Math.min(nx0, x0[at]), Math.max(nx1, x1[at]), y0[at], y1[at],
                    Math.max(layer, nearest[at]));
            }
            else if (((nx1 - nx0) * (ny1 - ny0)) > ((x1[at] - x0[at]) * (y1[at] - y0[at])))
            {
                set(at, nx0, nx1, ny0, ny1, layer);
            }
            else
            {
                return;
            }
            if (!wasFull && isFull(at))
            {
                full++;
            }
            horizon = Integer.MAX_VALUE;
        }

        private static boolean sameSpan(final double from, final double to, final double storedFrom,
            final double storedTo)
        {
            return (Math.abs(from - storedFrom) <= EPSILON) && (Math.abs(to - storedTo) <= EPSILON);
        }

        private void set(final int at, final double nx0, final double nx1, final double ny0,
            final double ny1, final int layer)
        {
            x0[at] = nx0;
            x1[at] = nx1;
            y0[at] = ny0;
            y1[at] = ny1;
            nearest[at] = layer;
        }

        private boolean isFull(final int at)
        {
            if (nearest[at] == Integer.MAX_VALUE)
            {
                return false;
            }
            final int i = at / tall;
            final int j = at % tall;
            return (x0[at] <= (cellX0(i) + EPSILON)) && (x1[at] >= (cellX1(i) - EPSILON))
                && (y0[at] <= (cellY0(j) + EPSILON)) && (y1[at] >= (cellY1(j) - EPSILON));
        }
    }
}
