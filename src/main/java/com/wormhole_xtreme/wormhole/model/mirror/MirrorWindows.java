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
 * <p>Nothing in the world changes, the way a gate's event horizon changes nothing. A viewer is
 * sent the banner as air, the open part of the opening as barrier -- invisible, and at least as
 * solid as what it covers -- and far-side blocks behind it: only the ones they could see through
 * an opening from where their eye is, all of whose outline is hidden by the opening or by solid
 * blocks around it, and each from the opening their line of sight passes through. That is what
 * lets windows share a wall, and what keeps a freestanding one inside its edges.
 *
 * <p>Real blocks reach {@code mirror-view-depth} from the eye. Past that a shell closes the view,
 * each of its blocks painted with what the line of sight through it meets at the far side, out
 * to {@code mirror-view-horizon}, or with sky. See {@link Pass}.
 *
 * <h2>What it costs, and what keeps that down</h2>
 *
 * <ul>
 * <li>Only the cone from the eye through the opening is walked, and only out to a radius from
 * the eye, so the work grows with what can be seen and is bounded however close the eye comes:
 * right against a mirror the cone is half a sphere, and half a sphere of a fixed radius is a
 * fixed number of blocks. The shell is that sphere's surface.</li>
 * <li>A viewer is redrawn at most a few times a second as they move, and not at all on a sweep
 * where nothing changed. Only the difference is sent, except after crossing into a new chunk --
 * which is when the client is handed fresh chunks that erase what was drawn -- and as a long
 * safety net.</li>
 * <li>The far side is read on demand into a short-lived cache, and never from a chunk that is not
 * loaded: {@link MirrorChunkLoads} fetches those without stalling the server, and loaded ones are
 * held while anybody is looking, so they are not loaded again every few seconds.</li>
 * </ul>
 *
 * <p>A prototype for #278: blocks only, no entities, and lit and tinted by this world.
 */
public final class MirrorWindows
{
    /** How often a viewer is sent their whole view again when nothing else has prompted it. */
    private static final long RESEND_MILLIS = 30_000L;

    /** How old a reading of a far side, or of what surrounds an opening, may get. */
    private static final long RESAMPLE_MILLIS = 5000L;

    /** Least time between two redraws of one viewer as they move. */
    static final long REDRAW_MILLIS = 250L;

    /** The same, in ticks, for the redraw that catches a viewer up after they stop. */
    private static final long REDRAW_TICKS = 5L;

    /** How long a far chunk stays held after anybody last looked at it. */
    private static final long HOLD_MILLIS = 30_000L;

    /** Most blocks one redraw considers across every window a viewer sees. */
    private static final int MOST_CANDIDATES = 40_000;

    /** Most steps one redraw's lines of sight into the far side may take between them. */
    private static final int MOST_RAY_STEPS = 150_000;

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

    /** When {@link #EMPTY} was last cleared. */
    private static long emptyReadAt;

    /** One window: where it is, which of its opening can be seen through, and its far side. */
    private static final class Window
    {
        private final QuantumMirror mirror;
        private final MirrorWindow shape;
        private final Block banner;
        private final List<Spot> open;
        private final Set<Long> openKeys = new HashSet<>();
        private FarSide farSide;
        private Set<Long> solid = Set.of();
        private long solidAt;

        Window(final QuantumMirror mirror, final MirrorWindow shape, final Block banner,
            final List<Spot> open)
        {
            this.mirror = mirror;
            this.shape = shape;
            this.banner = banner;
            this.open = open;
            open.forEach(cell -> openKeys.add(key(cell.x(), cell.y(), cell.z())));
        }
    }

    /** One viewer's drawing, as last sent. */
    private static final class View
    {
        private final World world;
        private Map<Long, BlockData> drawn = new HashMap<>();
        private Set<String> mirrors = Set.of();
        private long fullAt;
        private long composedAt;
        private long eye = Long.MIN_VALUE;
        private long chunk = Long.MIN_VALUE;
        private Location pendingEye;
        private boolean catchUpQueued;

        View(final World world)
        {
            this.world = world;
        }
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
        MirrorChunkLoads.clear();
    }

    /**
     * Offers a mirror to the sweep in progress.
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
        final World far = Bukkit.getWorld(mirror.destination().worldName());
        if ((!standing && !(data instanceof Directional)) || (far == null))
        {
            return false;
        }
        final MirrorWindow shape = MirrorWindow.of(mirror.banner(), MirrorArrival.facingOf(data),
            standing, mirror.destination());
        if (shape == null)
        {
            return false;
        }
        final Window window = new Window(mirror, shape, banner, openCells(shape, banner.getWorld()));
        final Window previous = WINDOWS.get(mirror.name());
        if ((previous != null) && previous.shape.equals(shape)
            && previous.mirror.destination().equals(mirror.destination()))
        {
            window.farSide = previous.farSide;
            window.solid = previous.solid;
            window.solidAt = previous.solidAt;
        }
        else
        {
            window.farSide = new FarSide(far);
        }
        OFFERED.put(mirror.name(), window);
        return true;
    }

    /** Ends a sweep: the windows offered become the windows there are, and every view follows. */
    static void finish()
    {
        if (OFFERED.isEmpty() && WINDOWS.isEmpty() && VIEWS.isEmpty())
        {
            return;
        }
        final long now = now();
        for (final Map.Entry<String, Window> entry : WINDOWS.entrySet())
        {
            final Window next = OFFERED.get(entry.getKey());
            if ((next == null) || (next.farSide != entry.getValue().farSide))
            {
                entry.getValue().farSide.release();
            }
        }
        WINDOWS.clear();
        WINDOWS.putAll(OFFERED);
        OFFERED.clear();
        MirrorChunkLoads.drain();
        final Set<World> worlds = new LinkedHashSet<>();
        for (final Window window : WINDOWS.values())
        {
            worlds.add(window.banner.getWorld());
            if ((now - window.farSide.usedAt) > HOLD_MILLIS)
            {
                window.farSide.release();
            }
        }
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
    }

    /**
     * Keeps a player's view in step as they move, rather than waiting for the next sweep.
     *
     * <p>On every move of every player, so a server with no windows answers from two empty maps.
     * A viewer is redrawn when their eye has moved half a block, and at most every
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
        final Window gone = WINDOWS.remove(mirror.name());
        if (gone == null)
        {
            return;
        }
        gone.farSide.release();
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

    /** Takes every view back and lets every held chunk go, as the plugin stops. */
    public static void restoreAll()
    {
        final long now = now();
        WINDOWS.values().forEach(window -> window.farSide.release());
        OFFERED.values().forEach(window -> window.farSide.release());
        for (final Map.Entry<UUID, View> entry : VIEWS.entrySet())
        {
            final Player player = Bukkit.getPlayer(entry.getKey());
            if ((player != null) && player.getWorld().equals(entry.getValue().world))
            {
                send(player, entry.getValue(), new HashMap<>(), now, false);
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
            // A new world's chunks have already replaced everything drawn in the old one.
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
        final Map<Long, BlockData> wanted = compose(eye, seeing, now);
        send(player, view, wanted, now, crossed || ((now - view.fullAt) >= RESEND_MILLIS));
        view.mirrors = names(seeing);
        view.eye = eyeKey(eye);
        view.chunk = chunk;
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
        if ((view.eye != eye) || ((now - view.composedAt) >= RESAMPLE_MILLIS)
            || !view.mirrors.equals(names(seeing)))
        {
            return false;
        }
        for (final Window window : seeing)
        {
            if (window.farSide.changedAt > view.composedAt)
            {
                return false;
            }
        }
        return true;
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

    /** The windows a player is close enough to, and in front of, in a stable order. */
    private static List<Window> seenBy(final Player player, final Location eye)
    {
        final double radius = ConfigManager.getMirrorProximityRadius();
        final Location at = player.getLocation();
        final List<Window> seeing = new ArrayList<>();
        for (final Window window : WINDOWS.values())
        {
            if (!window.open.isEmpty() && window.banner.getWorld().equals(player.getWorld())
                && (at.distanceSquared(window.banner.getLocation()) <= (radius * radius))
                && window.shape.inFront(eye.getX(), eye.getZ()))
            {
                seeing.add(window);
            }
        }
        seeing.sort(Comparator.comparing(window -> window.mirror.name()));
        return seeing;
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
     * Every block one viewer should be shown, and what as.
     *
     * <p>Openings first, so a block that is part of one opening is never drawn as another's far
     * side. Then, nearest window first, the blocks in the cone from the eye through each opening,
     * out to {@code mirror-view-depth} and within one budget for the whole redraw -- see
     * {@link Pass} for which of them are drawn.
     */
    private static Map<Long, BlockData> compose(final Location eye, final List<Window> seeing,
        final long now)
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
        final int radius = ConfigManager.getMirrorViewDepth();
        final Budget budget = new Budget();
        final List<Pass> passes = new ArrayList<>();
        for (final Window window : nearestFirst(seeing, eye))
        {
            refreshSolid(window, now);
            passes.add(new Pass(eye, window, seeing, allOpen, wanted, air, radius, budget, now));
        }
        // The middle of every view first, then outwards, so a spent budget costs the edges of
        // the views rather than their depth.
        for (int band = 0; band < MirrorWindow.bands(); band++)
        {
            for (final Pass pass : passes)
            {
                if (!pass.window.shape.forEachCandidate(eye.getX(), eye.getY(), eye.getZ(), radius,
                    band, pass, (x, y, z) -> pass.consider(x, y, z) && (--budget.blocks > 0)))
                {
                    return wanted;
                }
            }
        }
        return wanted;
    }

    /** What one redraw may spend, across every window a viewer sees. */
    private static final class Budget
    {
        private int blocks = MOST_CANDIDATES;
        private int raySteps = MOST_RAY_STEPS;
    }

    /**
     * What the shell shows where a line of sight into the far side finds nothing at all.
     *
     * <p>A sky-blue block reads as sky in a world that has one. The End has none, and the Nether's
     * ceiling is bedrock, so a line that finds nothing there is looking into the dark.
     */
    private static BlockData sky(final World far)
    {
        return Bukkit.createBlockData((far.getEnvironment() == World.Environment.NORMAL)
            ? Material.LIGHT_BLUE_CONCRETE : Material.BLACK_CONCRETE);
    }

    /** Whether a far-side block is nothing to see: air, or one of its kinds. */
    private static boolean isAir(final BlockData data, final BlockData air)
    {
        if (data.equals(air))
        {
            return true;
        }
        final Material material = data.getMaterial();
        return (material != null) && material.isAir();
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
        if ((rect == null) || !window.shape.overlaps(rect, window.open)
            || !window.shape.covered(rect, (across, up) -> clear(window, across, up, allOpen)))
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

    /** A viewer's windows, nearest first, so a spent budget cuts the furthest views short. */
    private static List<Window> nearestFirst(final List<Window> seeing, final Location eye)
    {
        final List<Window> sorted = new ArrayList<>(seeing);
        sorted.sort(Comparator
            .comparingDouble((Window window) -> window.banner.getLocation().distanceSquared(eye))
            .thenComparing(window -> window.mirror.name()));
        return sorted;
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


    /** Whether a block of a window's face keeps a drawn block behind it out of sight elsewhere. */
    private static boolean clear(final Window window, final int across, final int y,
        final Set<Long> allOpen)
    {
        final long face = faceKey(window.shape, across, y);
        return window.openKeys.contains(face)
            || (window.solid.contains(face) && !allOpen.contains(face));
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
        for (final Map.Entry<Long, BlockData> entry : wanted.entrySet())
        {
            if (full || !entry.getValue().equals(view.drawn.get(entry.getKey())))
            {
                final BlockState state = drawn(view.world, entry.getKey(), entry.getValue());
                if (state != null)
                {
                    changes.add(state);
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
     * <p>Within the radius of the eye, a block is drawn as the far-side block it maps to, if it
     * is seen through this window ({@link #seenThrough}), is not already hidden behind a solid
     * far-side block drawn nearer the eye, has a loaded far side, and would change what the
     * client shows -- far-side air over a block that is really empty would not. That last
     * saving is for the near volume only: a shell block is solid, and has to be painted over
     * open air as much as over anything, or the real world's own horizon shows through it.
     *
     * <p>Just past the radius lies a shell, one block thick, that closes the view: every line of
     * sight from the eye through the opening crosses it. A block there is drawn as whatever the
     * same line of sight, carried on into the far side, first meets -- or as sky if it meets
     * nothing before the horizon. Things past the radius lose their parallax that way, which at
     * that distance is little, and in return the view has no edge where the real world shows and
     * costs the same however close the eye comes.
     */
    private static final class Pass implements MirrorWindow.Limits
    {
        private final Location eye;
        private final Window window;
        private final List<Window> seeing;
        private final Set<Long> allOpen;
        private final Map<Long, BlockData> wanted;
        private final BlockData air;
        private final BlockData sky;
        private final double radius;
        private final int horizon;
        private final Budget budget;
        private final long now;
        private final World here;
        private final int min;
        private final int max;
        private final Occlusion hidden;

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
            this.sky = sky(window.farSide.far);
            this.radius = radius;
            this.horizon = ConfigManager.getMirrorViewHorizon();
            this.budget = budget;
            this.now = now;
            this.here = window.banner.getWorld();
            this.min = here.getMinHeight();
            this.max = here.getMaxHeight();
            this.hidden = new Occlusion(window);
        }

        /** @return true, always: the walk is stopped by the budget, not by one block */
        boolean consider(final int x, final int y, final int z)
        {
            final long cell = key(x, y, z);
            if ((y < min) || (y >= max) || wanted.containsKey(cell))
            {
                return true;
            }
            final double dx = (x + 0.5) - eye.getX();
            final double dy = (y + 0.5) - eye.getY();
            final double dz = (z + 0.5) - eye.getZ();
            final double distance = Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
            if (distance >= (radius + 1.0))
            {
                return true;
            }
            final double[] rect = seenThrough(eye, window, x, y, z, seeing, allOpen);
            final int layer = layerOf(x, z);
            if ((rect == null) || hidden.covers(rect, layer))
            {
                return true;
            }
            if (distance >= radius)
            {
                wanted.put(cell, shell(x, y, z, dx / distance, dy / distance, dz / distance));
                return true;
            }
            final Spot at = window.shape.farOf(x, y, z);
            final BlockData data = window.farSide.at(at.x(), at.y(), at.z(), air, now);
            if (data == null)
            {
                return true;
            }
            if (data.isOccluding())
            {
                hidden.add(rect, layer);
            }
            if (!data.equals(air) || !emptyHere(here, x, y, z, now))
            {
                wanted.put(cell, data);
            }
            return true;
        }

        /**
         * What a block of the shell shows: the first thing the line of sight through it meets
         * at the far side, or sky.
         *
         * <p>The line is followed a block at a time. Where it climbs above the highest block in
         * its far column it can only meet sky, and says so without walking there. A far chunk
         * that is not loaded yet reads as sky too; it has been asked for, and the view is drawn
         * again when it arrives.
         */
        private BlockData shell(final int x, final int y, final int z, final double dx,
            final double dy, final double dz)
        {
            final double[] dir = window.shape.farDirection(dx, dy, dz);
            final Spot start = window.shape.farOf(x, y, z);
            long last = Long.MIN_VALUE;
            for (double t = 0.0; (t <= horizon) && (budget.raySteps > 0); t += 1.0)
            {
                final int farX = (int) Math.floor(start.x() + 0.5 + (dir[0] * t));
                final int farY = (int) Math.floor(start.y() + 0.5 + (dir[1] * t));
                final int farZ = (int) Math.floor(start.z() + 0.5 + (dir[2] * t));
                final long step = key(farX, farY, farZ);
                if (step == last)
                {
                    continue;
                }
                last = step;
                budget.raySteps--;
                final int top = window.farSide.top(farX, farZ, now);
                if (farY > top)
                {
                    if (dir[1] >= 0.0)
                    {
                        return sky;
                    }
                    // Only air between here and that column's surface: skip down to it.
                    t += Math.max(0.0, ((farY - top - 1) / -dir[1]) - 1.0);
                    continue;
                }
                final BlockData data = window.farSide.at(farX, farY, farZ, air, now);
                if (data == null)
                {
                    return sky;
                }
                if (!isAir(data, air))
                {
                    return data;
                }
            }
            return sky;
        }

        @Override
        public int deepest()
        {
            return hidden.horizon();
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
     * <p>Kept on a fine grid over the opening, eight parts to a block, each remembering the layer
     * of the nearest solid block in front of it. The layer matters because the cone is walked in
     * bands, middle first: a solid block far back in the middle band must not hide one nearer the
     * eye in the next.
     */
    private static final class Occlusion
    {
        private static final int FINE = 8;

        private final int left;
        private final int bottom;
        private final int wide;
        private final int tall;
        private final int[] nearest;
        private int count;
        private int horizon = Integer.MAX_VALUE;

        Occlusion(final Window window)
        {
            final boolean alongX = window.shape.into().x() != 0;
            int acrossMin = Integer.MAX_VALUE;
            int acrossMax = Integer.MIN_VALUE;
            int yMin = Integer.MAX_VALUE;
            int yMax = Integer.MIN_VALUE;
            for (final Spot cell : window.open)
            {
                final int across = alongX ? cell.z() : cell.x();
                acrossMin = Math.min(acrossMin, across);
                acrossMax = Math.max(acrossMax, across);
                yMin = Math.min(yMin, cell.y());
                yMax = Math.max(yMax, cell.y());
            }
            left = acrossMin;
            bottom = yMin;
            wide = ((acrossMax + 1) - acrossMin) * FINE;
            tall = ((yMax + 1) - yMin) * FINE;
            nearest = new int[wide * tall];
            java.util.Arrays.fill(nearest, Integer.MAX_VALUE);
            // The parts of the opening's outline that are closed hide everything behind them.
            for (int i = 0; i < wide; i++)
            {
                for (int j = 0; j < tall; j++)
                {
                    if (!window.openKeys.contains(faceKey(window.shape, left + (i / FINE), bottom + (j / FINE))))
                    {
                        hide(i, j, 0);
                    }
                }
            }
        }

        /**
         * @return the deepest layer anything could still be seen at: past the deepest solid
         *         block hiding each part of the opening, once every part is hidden
         */
        int horizon()
        {
            if ((count < nearest.length) || (horizon != Integer.MAX_VALUE))
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

        /** Whether every part of the opening a projected block touches is hidden in front of it. */
        boolean covers(final double[] rect, final int layer)
        {
            if (count == 0)
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
                    if (nearest[(i * tall) + j] >= layer)
                    {
                        return false;
                    }
                }
            }
            return true;
        }

        /** Marks the parts of the opening a solid block hides: those whose middles it covers. */
        void add(final double[] rect, final int layer)
        {
            final int iFrom = Math.max(0, (int) Math.ceil(((rect[0] - left) * FINE) - 0.5));
            final int iTo = Math.min(wide - 1, (int) Math.floor(((rect[1] - left) * FINE) - 0.5));
            final int jFrom = Math.max(0, (int) Math.ceil(((rect[2] - bottom) * FINE) - 0.5));
            final int jTo = Math.min(tall - 1, (int) Math.floor(((rect[3] - bottom) * FINE) - 0.5));
            for (int i = iFrom; i <= iTo; i++)
            {
                for (int j = jFrom; j <= jTo; j++)
                {
                    hide(i, j, layer);
                }
            }
        }

        private void hide(final int i, final int j, final int layer)
        {
            final int at = (i * tall) + j;
            if (nearest[at] == Integer.MAX_VALUE)
            {
                count++;
            }
            if (layer < nearest[at])
            {
                nearest[at] = layer;
                horizon = Integer.MAX_VALUE;
            }
        }
    }

    /**
     * One window's far side: read on demand, cached briefly, and never from an unloaded chunk.
     *
     * <p>Chunks it reads are held loaded with a plugin ticket, so the server does not unload a
     * chunk somebody is looking at and have it loaded again on the next redraw. They are let go
     * when nobody has looked for a while, when the window goes, and when the plugin stops.
     */
    private static final class FarSide
    {
        private final World far;
        private final String name;
        private final int min;
        private final int max;
        private final Map<Long, BlockData> blocks = new HashMap<>();
        private final Map<Long, Integer> tops = new HashMap<>();
        private final Set<Long> held = new HashSet<>();
        private long readAt;
        private long usedAt;
        private volatile long changedAt;

        FarSide(final World far)
        {
            this.far = far;
            this.name = far.getName();
            this.min = far.getMinHeight();
            this.max = far.getMaxHeight();
        }

        /** The highest block that is not air in a far column, or no limit if it is not loaded. */
        int top(final int x, final int z, final long now)
        {
            expire(now);
            if (!far.isChunkLoaded(x >> 4, z >> 4))
            {
                return Integer.MAX_VALUE;
            }
            return tops.computeIfAbsent(chunkKey(x, z),
                column -> far.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE));
        }

        private void expire(final long now)
        {
            if ((now - readAt) >= RESAMPLE_MILLIS)
            {
                blocks.clear();
                tops.clear();
                readAt = now;
            }
        }

        /** The far-side block here, or null if its chunk is not loaded yet. */
        BlockData at(final int x, final int y, final int z, final BlockData air, final long now)
        {
            usedAt = now;
            expire(now);
            if ((y < min) || (y >= max))
            {
                return air;
            }
            final long cell = key(x, y, z);
            final BlockData known = blocks.get(cell);
            if (known != null)
            {
                return known;
            }
            final int chunkX = x >> 4;
            final int chunkZ = z >> 4;
            if (!far.isChunkLoaded(chunkX, chunkZ))
            {
                MirrorChunkLoads.request(far, chunkX, chunkZ, () ->
                {
                    hold(chunkX, chunkZ);
                    changedAt = now();
                });
                return null;
            }
            hold(chunkX, chunkZ);
            // A linked pair arrives in the far banner's own block, so it would otherwise hang in
            // the middle of the view.
            final BlockData read = (MirrorManager.at(new MirrorBlock(name, x, y, z)) != null) ? air
                : far.getBlockAt(x, y, z).getBlockData();
            blocks.put(cell, read);
            return read;
        }

        private void hold(final int chunkX, final int chunkZ)
        {
            if (held.add(chunkKey(chunkX, chunkZ)))
            {
                far.addPluginChunkTicket(chunkX, chunkZ, WormholeXTreme.getThisPlugin());
            }
        }

        void release()
        {
            for (final long chunk : held)
            {
                far.removePluginChunkTicket((int) (chunk >> 32), (int) chunk,
                    WormholeXTreme.getThisPlugin());
            }
            held.clear();
            blocks.clear();
            tops.clear();
        }
    }
}
