package com.wormhole_xtreme.wormhole.model.mirror;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
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
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorWindow.Spot;

/**
 * Drawing what is on the other side of every window mirror a player is looking into.
 *
 * <p>Every wall mirror with somewhere to go is a window, opening in the wall it hangs on. Nothing
 * is stored to say so, which is what lets mirrors made before windows existed open as one without
 * being touched.
 *
 * <p>What a window shows is its {@link MirrorCapture}: a photograph of the far side, taken once
 * and kept on disk, so the far world need not be loaded to be looked at. A mirror whose capture
 * has not been taken yet stays a banner until it has -- a few seconds, the first time.
 *
 * <p>Nothing in the world changes, the way a gate's event horizon changes nothing. A viewer is
 * sent the banner as air, the open part of the opening as barrier -- invisible, and at least as
 * solid as what it covers -- and the room behind it, held whole ({@link #fixedView}). A mirror
 * walled to the proximity distance with no neighbour is sent the room as it is, the same from
 * every eye, since the wall hides its edges. Any other is sent the room clipped to the eye
 * ({@link #clipWhole}): only the blocks they could see through an opening from where their eye
 * is, all but a little of whose outline lands on the opening or on wall that hides the rest, each
 * from the opening their line of sight passes through. That is what lets windows share a wall.
 * Their own world's creatures standing inside the view are hidden from them for as long as they
 * look.
 *
 * <h2>What it costs, and what keeps that down</h2>
 *
 * <ul>
 * <li>A room is built once from the capture and kept a minute; the capture already holds only the
 * surfaces somebody at the opening could see, so a room at the render distance costs what its
 * surfaces cost.</li>
 * <li>Clipping is one cheap bound and, for the blocks that pass it, one projection each; nothing
 * is walked or occluded.</li>
 * <li>A viewer is redrawn at most ten times a second as they move, and not at all on a sweep
 * where nothing changed. Only the difference is sent, except after crossing into a new chunk --
 * which is when the client is handed fresh chunks that erase what was drawn -- and as a long
 * safety net. The server has a share of work a second across every viewer.</li>
 * <li>The far side is read from the capture, in memory, never from the live world.</li>
 * </ul>
 *
 * <p>Blocks only, no entities, and lit and tinted by this world.
 */
public final class MirrorWindows
{
    /** How often a viewer is sent their whole view again when nothing else has prompted it. */
    private static final long RESEND_MILLIS = 30_000L;

    /** The unit every count in {@code mirror debug} is said in. */
    private static final String BLOCKS = " blocks";

    /** How old a reading of what surrounds an opening may get. */
    private static final long RESAMPLE_MILLIS = 5000L;

    /** Least time between two redraws of one viewer as they move. */
    static final long REDRAW_MILLIS = 100L;

    /** The same, in ticks, for the redraw that catches a viewer up after they stop. */
    private static final long REDRAW_TICKS = 2L;

    /** How far in front of an opening real blocks are read for what they hide, and the least its wall is read. */
    private static final int SURROUND = 8;

    /** The most of a block's outline that may land beside the opening for it to be drawn. */
    private static final double MOST_BESIDE = 0.05;

    /** The same for a block already drawn, so a step does not take it away and the next give it back. */
    private static final double KEPT_BESIDE = 0.15;

    /** How long a walled window's fixed view is kept before the real world behind it is read again. */
    private static final long FIXED_MILLIS = 60_000L;

    /** Most blocks one fixed view may hold; past it the depth is cut until it fits. */
    private static final int MOST_FIXED = 250_000;

    /** The same, settable so a test can make a room not fit. */
    static int mostFixed = MOST_FIXED;

    /**
     * Most blocks a room may hold to be sent whole; past it, it is clipped to each eye however
     * good its wall.
     *
     * <p>A room at the render distance is some eighty thousand blocks, and sending them all as a
     * viewer came into range -- and taking them all back as they left -- re-meshed every chunk
     * section they touched on the client, a moment's freeze each way. Clipped to an eye the same
     * room is a few thousand, and a step is a small difference.
     */
    private static final int MOST_WHOLE = 20_000;

    /** The same, settable so a test can make a small room too big. */
    static int mostWhole = MOST_WHOLE;

    /**
     * How far from the eye a clipped room is judged afresh on every redraw; past it, only once
     * the eye has left its cell ({@link #farCellFor}) or after {@link #FAR_MILLIS}.
     *
     * <p>Through a one-block opening, a tenth-of-a-block step swings the far end of a view a
     * dozen blocks sideways: thousands of blocks a hundred and more deep changed ten times a
     * second, projected here and re-meshed on the client, and a mirror at the render distance
     * stuttered. What moves with a step is what is near the eye, and must be judged each time;
     * the rest is judged for the whole cell at once. From the eye's cell, not by depth behind
     * the opening: right against the opening the whole half-sphere is in view, and the first
     * layers of a deep room were most of it.
     */
    private static final double NEAR_DISTANCE = 24.0;

    /** The same, settable so a test can push the far part close. */
    static double nearDistance = NEAR_DISTANCE;

    /** How long a clipped room's far part stands before it is judged again from the same block. */
    private static final long FAR_MILLIS = 1000L;

    /** How many times as long as a redraw took the viewer's next redraw waits, at least. */
    private static final long REST_FACTOR = 3L;

    /** The same, settable: a redraw over a test's mocks takes long enough to rest a test out. */
    static long restFactor = REST_FACTOR;

    /** The server's share of work per second, by default: blocks walked, fixed and sent, all viewers together. */
    private static final int WORK_PER_SECOND = 400_000;

    /**
     * The server's share of work per second, across every viewer.
     *
     * <p>A redraw's own budget bounds one viewer, not a server: a hundred people walking past
     * mirrors at once was a hundred budgets a quarter second. Once the second's share is spent,
     * a viewer who has not crossed into a new chunk keeps what they already see until the next.
     * Settable for a test.
     */
    static int workPerSecond = WORK_PER_SECOND;

    private static long workSecond;
    private static int workSpent;

    /** What the time is; settable so a test can let a minute pass. */
    static java.util.function.LongSupplier clock = System::currentTimeMillis;

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

    /** Players who asked to see the world as it is, with no view drawn for them: admins checking. */
    private static final Set<UUID> BLIND = ConcurrentHashMap.newKeySet();

    /** Players who asked for one mirror drawn whole and unlimited, by the mirror's name. */
    private static final Map<UUID, String> FULL = new ConcurrentHashMap<>();

    /** A window's far side drawn whole: the blocks, and the depth they reach from the opening. */
    private record Whole(Map<Long, BlockData> blocks, int depth)
    {
    }

    /**
     * Draws one mirror whole and without limits for one player: everything its capture holds,
     * through its opening, whatever its wall or neighbours, until turned off.
     *
     * <p>For an admin checking what a capture holds and how it comes through the mirror. The
     * far side shows past the opening's edges and in the real ground behind the wall, which is
     * the point: nothing is trimmed.
     *
     * @param player
     *            who
     * @param mirrorName
     *            the mirror, or null to stop
     */
    public static void full(final Player player, final String mirrorName)
    {
        if (mirrorName == null)
        {
            FULL.remove(player.getUniqueId());
        }
        else
        {
            FULL.put(player.getUniqueId(), mirrorName);
        }
    }

    /**
     * Turns views off or on for one player.
     *
     * <p>For an admin who wants to see what a mirror's view was drawn over, and what the capture
     * file holds where it was taken. Turned off, their view is taken back on the next sweep.
     *
     * @param player
     *            who
     * @param off
     *            true to draw nothing for them
     */
    public static void blind(final Player player, final boolean off)
    {
        FULL.remove(player.getUniqueId());
        if (off)
        {
            BLIND.add(player.getUniqueId());
        }
        else
        {
            BLIND.remove(player.getUniqueId());
        }
    }

    /**
     * @param player
     *            who
     * @return true if views are turned off for them
     */
    public static boolean isBlind(final Player player)
    {
        return BLIND.contains(player.getUniqueId());
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
        /** The turn far-side blocks need to face the right way here. */
        private final StructureRotation rotation;
        /** The flip across the wall a reflection's blocks need, or none. */
        private final org.bukkit.block.structure.Mirror flip;
        /** Far-side states turned by {@link #rotation}, each turned once. */
        private final Map<BlockData, BlockData> turned = new IdentityHashMap<>();
        private Set<Long> solid = Set.of();
        /** The outermost ring of the solid face, each with the sides it is open on ({@link #marginOf}). */
        private Map<Long, Integer> margin = Map.of();
        /** The solid blocks of the face touching the opening, corners too: its frame. */
        private List<Spot> frame = List.of();
        /** How many blocks of solid wall stand on every side of the opening, as last read ({@link #borderOf}). */
        private int border;
        private long solidAt;
        /** Everything behind a walled window, drawn whatever the eye; null until first wanted. */
        private Map<Long, BlockData> fixed;
        private MirrorCapture fixedFrom;
        private long fixedAt;
        private int fixedFor;
        private int fixedDepth;
        private long fixedUsedAt;
        /** The whole capture through this window, for an admin who asked; null until then. */
        private Whole full;
        private MirrorCapture fullFrom;

        Window(final QuantumMirror mirror, final MirrorWindow shape, final Block banner,
            final List<Spot> open, final MirrorCapture capture)
        {
            this.mirror = mirror;
            this.shape = shape;
            this.banner = banner;
            this.open = open;
            this.capture = capture;
            this.rotation = switch (shape.quarterTurns())
            {
                case 1 -> StructureRotation.CLOCKWISE_90;
                case 2 -> StructureRotation.CLOCKWISE_180;
                case 3 -> StructureRotation.COUNTERCLOCKWISE_90;
                default -> StructureRotation.NONE;
            };
            // A reflection is flipped across the wall, not turned: stairs and doors keep their side.
            if (!shape.mirrored())
            {
                this.flip = org.bukkit.block.structure.Mirror.NONE;
            }
            else
            {
                this.flip = (shape.into().x() != 0) ? org.bukkit.block.structure.Mirror.FRONT_BACK
                    : org.bukkit.block.structure.Mirror.LEFT_RIGHT;
            }
            open.forEach(cell -> openKeys.add(key(cell.x(), cell.y(), cell.z())));
        }
    }

    /** One viewer's drawing, as last sent. */
    private static final class View
    {
        private final World world;
        private Map<Long, BlockData> drawn = new HashMap<>();
        private Set<String> mirrors = Set.of();
        private Set<String> fixedNames = Set.of();
        private final Map<UUID, Entity> veiled = new HashMap<>();
        private long fullAt;
        private long composedAt;
        private int generation;
        private int undrawable;
        private long eye = Long.MIN_VALUE;
        private long chunk = Long.MIN_VALUE;
        private Location pendingEye;
        private boolean catchUpQueued;
        private Redraw lastRedraw;
        /** Each clipped window's far part as last judged, by mirror name; see {@link #NEAR_DISTANCE}. */
        private final Map<String, Far> far = new HashMap<>();
        /** The least time before this viewer's next redraw on a move, longer after a slow one. */
        private long rest = REDRAW_MILLIS;
        private String stamp = "";

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
        lines.add(MirrorText.heading("your view"));
        lines.add(MirrorText.field("server", WINDOWS.size() + " window(s), " + VIEWS.size() + " viewer(s)"));
        lines.add(MirrorText.field("depth", ConfigManager.getMirrorViewDepth() + " from the opening (mirror-view-depth)"));
        final View view = VIEWS.get(player.getUniqueId());
        if (BLIND.contains(player.getUniqueId()))
        {
            lines.add(MirrorText.field("views", MirrorText.bad("off for you") + ", mirror debug on turns them back on"));
        }
        final String fullName = FULL.get(player.getUniqueId());
        if (fullName != null)
        {
            lines.add(MirrorText.field("drawn full", fullName + ", mirror debug on stops that"));
        }
        if (view == null)
        {
            lines.add(MirrorText.field("looking into", "no window"));
            return lines;
        }
        lines.add(MirrorText.field("looking into", String.join(", ", view.mirrors)));
        lines.add(MirrorText.field("drawn", view.drawn.size() + BLOCKS));
        lines.add(MirrorText.field("creatures hidden", String.valueOf(view.veiled.size())));
        if (view.undrawable > 0)
        {
            lines.add(MirrorText.field("not drawn over",
                MirrorText.bad(view.undrawable + " block(s) the world would not let be drawn over")));
        }
        for (final String name : view.mirrors)
        {
            final Window window = WINDOWS.get(name);
            if (window != null)
            {
                lines.addAll(mirrorLines(view, window, name.equals(fullName)));
            }
        }
        if (view.lastRedraw == null)
        {
            lines.add(MirrorText.field("last redraw", "never"));
        }
        else
        {
            lines.addAll(view.lastRedraw.lines());
        }
        return lines;
    }

    /**
     * What a player's view is doing, a line a mirror and one for the last redraw, for
     * {@code mirror debug} without {@code all}.
     *
     * @param player
     *            the player
     * @return lines to say
     */
    public static List<String> summary(final Player player)
    {
        final List<String> lines = new ArrayList<>();
        if (BLIND.contains(player.getUniqueId()))
        {
            lines.add(MirrorText.field("views", MirrorText.bad("off for you") + ", mirror debug on turns them back on"));
        }
        final View view = VIEWS.get(player.getUniqueId());
        if (view == null)
        {
            lines.add(MirrorText.field("view", "you are looking into no window"));
            return lines;
        }
        final String fullName = FULL.get(player.getUniqueId());
        for (final String name : view.mirrors)
        {
            final Window window = WINDOWS.get(name);
            if (window != null)
            {
                lines.add(MirrorText.field(name, name.equals(fullName) ? "whole and unlimited for you"
                    : howDrawn(window, view.fixedNames.contains(name))));
            }
        }
        if (view.lastRedraw != null)
        {
            lines.add(view.lastRedraw.brief());
        }
        return lines;
    }

    /**
     * What one redraw projected and drew.
     *
     * <p>Numbers, written out only when {@code mirror debug} asks: a sentence built on every redraw
     * was up to ten a second per viewer, for a command run once in a while.
     */
    private record Redraw(int projected, int near, int fixed, int fixedDepth, Spot eye, long tookMillis)
    {
        List<String> lines()
        {
            final List<String> lines = new ArrayList<>();
            lines.add(MirrorText.field("last redraw", projected + " blocks projected, " + near + " of them drawn, took "
                + tookMillis + " ms"));
            if (fixed > 0)
            {
                lines.add(MirrorText.field("drawn whole", fixed + " blocks to depth " + fixedDepth));
            }
            lines.add(MirrorText.field("drawn from", eye.x() + "," + eye.y() + "," + eye.z()));
            return lines;
        }

        String brief()
        {
            return MirrorText.field("redraw", projected + " projected, " + near + " drawn"
                + ((fixed > 0) ? (", " + fixed + " whole") : "") + ", " + tookMillis + " ms");
        }
    }

    /** How one window is being drawn for a viewer, and for a clipped one how far its far part stands, for {@code mirror debug}. */
    private static List<String> mirrorLines(final View view, final Window window, final boolean full)
    {
        final String name = window.mirror.name();
        if (full)
        {
            return List.of(MirrorText.field(name, "whole and unlimited for you, "
                + ((window.full == null) ? 0 : window.full.blocks().size()) + BLOCKS));
        }
        final boolean fixedForViewer = view.fixedNames.contains(name);
        final List<String> lines = new ArrayList<>();
        lines.add(MirrorText.field(name, howDrawn(window, fixedForViewer)));
        if (!fixedForViewer)
        {
            final Far far = view.far.get(name);
            lines.add(MirrorText.field(name + " far part", "judged once per " + farCellFor(window.border)
                + " blocks of movement, wall " + window.border + " on every side, "
                + ((far == null) ? "not yet" : (far.blocks().size() + " blocks kept"))));
        }
        return lines;
    }

    /** How a window is being drawn for a viewer, and why, for {@code mirror debug}. */
    private static String howDrawn(final Window window, final boolean fixedForViewer)
    {
        final Spot gap = gapBeside(window);
        final String clipped = "whole " + toDepth(window) + ", clipped to each eye: ";
        if (gap != null)
        {
            final String what = window.banner.getWorld().getBlockAt(gap.x(), gap.y(), gap.z())
                .getBlockData().getAsString();
            return clipped + MirrorText.bad("wall within "
                + wallReach() + " open at " + gap.x() + "," + gap.y() + "," + gap.z()) + " (" + what + ")";
        }
        if (!fixedForViewer && (window.fixed != null) && (window.fixed.size() > mostWhole))
        {
            return clipped + "a room of " + window.fixed.size()
                + " blocks is more than " + mostWhole + " to send at once";
        }
        if (!fixedForViewer)
        {
            return clipped + MirrorText.bad("another mirror within twice the depth");
        }
        return MirrorText.good("drawn whole") + " " + toDepth(window) + ", "
            + ((window.fixed == null) ? 0 : window.fixed.size()) + BLOCKS;
    }

    /** How deep a window's held room reaches, and in red when it was cut to fit under the cap. */
    private static String toDepth(final Window window)
    {
        if ((window.fixed != null) && (window.fixedDepth < window.fixedFor))
        {
            return MirrorText.bad("cut to depth " + window.fixedDepth + " of " + window.fixedFor + " to fit " + mostFixed
                + BLOCKS);
        }
        return "to depth " + window.fixedDepth;
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
        BLIND.clear();
        FULL.clear();
        MirrorCaptures.clear();
        clock = System::currentTimeMillis;
        workPerSecond = WORK_PER_SECOND;
        mostFixed = MOST_FIXED;
        mostWhole = MOST_WHOLE;
        nearDistance = NEAR_DISTANCE;
        restFactor = REST_FACTOR;
        workSecond = 0L;
        workSpent = 0;
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
        // A banner on a post is no window: nothing hides its room past its edges, and create refuses one.
        if (!(data instanceof Directional))
        {
            return false;
        }
        // The room of the mirror chosen at it, or its own, shown as a reflection.
        final QuantumMirror chosen = MirrorNetwork.chosen(mirror);
        final QuantumMirror showing = (chosen == mirror) ? mirror : mirror.withDestination(chosen.destination());
        final MirrorWindow shape = MirrorWindow.of(mirror.banner(), MirrorArrival.facingOf(data),
            showing.destination(), MirrorNetwork.reflects(mirror), mirror.width());
        if (shape == null)
        {
            return false;
        }
        final MirrorCapture capture = MirrorCaptures.get(showing);
        if (capture == null)
        {
            MirrorCaptures.request(showing);
            return false;
        }
        if (MirrorCaptures.due(showing, capture) || MirrorCaptures.outgrown(showing, capture))
        {
            MirrorCaptures.request(showing);
        }
        final Window window = new Window(showing, shape, banner, openCells(shape, banner.getWorld()),
            capture);
        final Window previous = WINDOWS.get(mirror.name());
        if ((previous != null) && previous.shape.equals(shape))
        {
            window.solid = previous.solid;
            window.margin = previous.margin;
            window.frame = previous.frame;
            window.solidAt = previous.solidAt;
            // Every mirror in a loaded chunk is a window each sweep, looked at or not: a fixed view
            // nobody has used for a while is let go rather than carried for the life of the chunk.
            if ((previous.fixed != null) && ((now() - previous.fixedUsedAt) < FIXED_MILLIS))
            {
                window.fixed = previous.fixed;
                window.fixedFrom = previous.fixedFrom;
                window.fixedAt = previous.fixedAt;
                window.fixedFor = previous.fixedFor;
                window.fixedDepth = previous.fixedDepth;
                window.fixedUsedAt = previous.fixedUsedAt;
            }
            if (FULL.containsValue(mirror.name()))
            {
                window.full = previous.full;
                window.fullFrom = previous.fullFrom;
            }
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
        if ((view != null) && ((now - view.composedAt) < view.rest))
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

    /** How long after a click the server's own correction of the clicked blocks has gone out. */
    private static final long CORRECTION_MILLIS = 30L;

    /**
     * Sends the two blocks the server corrects after a click straight after its correction, and
     * the whole view a tick later.
     *
     * <p>A click the server refuses makes it send the clicked block and the one on the face
     * clicked as they really are, in the same tick and after every listener; the whole view a tick
     * later left the real banner showing for that tick -- "a very quick flicker of a banner when
     * right clicking". Those two are sent again as the view draws them from off the main thread a
     * few milliseconds on, which lands after the correction and before the tick is out. Nothing
     * here reads the world off the main thread: the blocks are looked up first.
     *
     * @param player
     *            who clicked
     * @param clicked
     *            the block clicked
     * @param face
     *            the face of it clicked, or null if it is not known
     */
    public static void resend(final Player player, final Block clicked, final BlockFace face)
    {
        final View view = VIEWS.get(player.getUniqueId());
        if (view == null)
        {
            return;
        }
        final List<Location> spots = new ArrayList<>();
        final List<BlockData> states = new ArrayList<>();
        final Block beside = (face == null) ? null : clicked.getRelative(face);
        for (final Block block : (beside == null) ? List.of(clicked) : List.of(clicked, beside))
        {
            final BlockData data = view.drawn.get(key(block.getX(), block.getY(), block.getZ()));
            if (data != null)
            {
                spots.add(new Location(player.getWorld(), block.getX(), block.getY(), block.getZ()));
                states.add(data);
            }
        }
        resend(player);
        if (spots.isEmpty())
        {
            return;
        }
        try
        {
            WormholeXTreme.getScheduler().runTaskAsynchronously(WormholeXTreme.getThisPlugin(), () ->
            {
                try
                {
                    Thread.sleep(CORRECTION_MILLIS);
                }
                catch (final InterruptedException interrupted)
                {
                    Thread.currentThread().interrupt();
                    return;
                }
                for (int i = 0; i < spots.size(); i++)
                {
                    player.sendBlockChange(spots.get(i), states.get(i));
                }
            });
        }
        catch (final RuntimeException noScheduler)
        {
            // No scheduler yet, during startup or in tests: the whole view a tick later is enough.
        }
    }

    /**
     * Sends a player's whole view again a tick after they click a mirror.
     *
     * <p>A click the server refuses -- a punch that does not break, a right-click that places
     * nothing -- makes it send the clicked block, and the one beside it, as they really are once
     * the click is handled. Only marking the view left the real banner and wall showing until the
     * player moved or the sweep came round.
     *
     * @param player
     *            who clicked
     */
    public static void resend(final Player player)
    {
        final View view = VIEWS.get(player.getUniqueId());
        if (view == null)
        {
            return;
        }
        view.fullAt = 0L;
        view.composedAt = 0L;
        view.eye = Long.MIN_VALUE;
        try
        {
            WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(),
                () -> sendAgain(player), 1L);
        }
        catch (final RuntimeException noScheduler)
        {
            // No scheduler yet, during startup or in tests. The next move or sweep sends it.
        }
    }

    /** Sends everything a view holds again, as it holds it. */
    private static void sendAgain(final Player player)
    {
        final View view = VIEWS.get(player.getUniqueId());
        if (view != null)
        {
            send(player, view, view.drawn, now(), true);
        }
    }

    /**
     * Sends the whole view again, at the next sweep, to everybody looking into one mirror.
     *
     * <p>For after its banner is written to the world. Stamping sends every client the real banner,
     * which lands over the view where the banner is drawn away, and the view -- believing it sent
     * air there already -- would not send it again until its next whole resend, half a minute later.
     *
     * @param mirrorName
     *            the mirror whose banner changed
     */
    public static void resendFor(final String mirrorName)
    {
        for (final View view : VIEWS.values())
        {
            if (view.mirrors.contains(mirrorName))
            {
                view.fullAt = 0L;
            }
        }
    }

    /**
     * Redraws one mirror at once for whoever is looking into it, after a right-click changed what
     * it opens onto.
     *
     * <p>The sweep would get to it within a second, and a click should not wait that long. A room
     * that is not captured yet leaves the window showing what it did until the sweep finds it ready.
     *
     * @param mirror
     *            the mirror whose choice changed
     * @param banner
     *            its loaded banner block, or null to leave it to the sweep
     */
    public static void redraw(final QuantumMirror mirror, final Block banner)
    {
        if ((banner == null) || !WINDOWS.containsKey(mirror.name()))
        {
            return;
        }
        OFFERED.remove(mirror.name());
        if (!offer(mirror, banner))
        {
            return;
        }
        WINDOWS.put(mirror.name(), OFFERED.remove(mirror.name()));
        final long now = now();
        for (final Player player : banner.getWorld().getPlayers())
        {
            final View view = VIEWS.get(player.getUniqueId());
            if ((view != null) && view.mirrors.contains(mirror.name()))
            {
                update(player, player.getEyeLocation(), now, false);
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
        seeing.forEach(window -> refreshSolid(window, now));
        // A viewer who starts inside the second's share finishes; later ones wait. Crossing into a
        // new chunk is never kept waiting, since the chunk arriving erases what was drawn.
        final boolean mayWork = (crossed && (view.chunk != Long.MIN_VALUE)) || !overBudget(now);
        final Wholes wholes = wholesOf(seeing, now, mayWork, FULL.get(id));
        // Made only of windows drawn whole, the view is the same from wherever the eye is.
        final boolean eyeMatters = wholes.whole().size() < seeing.size();
        final String stamp = stampOf(seeing, wholes);
        if (!crossed && (fromSweep || !eyeMatters)
            && unchanged(view, seeing, eyeMatters ? eyeKey(eye) : view.eye, stamp, now))
        {
            if (!eyeMatters)
            {
                view.eye = eyeKey(eye);
            }
            if (fromSweep && ((now - view.fullAt) >= RESEND_MILLIS))
            {
                send(player, view, view.drawn, now, true);
            }
            return;
        }
        if (!mayWork)
        {
            // The server's share for this second is spent: this viewer keeps what they see a
            // moment longer, and a move is caught up once there is room.
            if (!fromSweep)
            {
                view.pendingEye = eye;
                catchUpLater(player, view);
            }
            return;
        }
        final List<Entity> inside = new ArrayList<>();
        final Budget budget = new Budget();
        final Map<Long, BlockData> wanted = compose(view, eye, seeing, wholes, view.drawn.keySet(), now, inside, budget);
        workSpent += budget.projected;
        send(player, view, wanted, now, crossed || ((now - view.fullAt) >= RESEND_MILLIS));
        veil(player, view, inside);
        final long took = now() - now;
        view.lastRedraw = new Redraw(budget.projected, budget.near, budget.fixed, budget.fixedDepth,
            new Spot((int) eye.getX(), (int) eye.getY(), (int) eye.getZ()), took);
        view.rest = restAfter(took);
        view.stamp = stamp;
        view.mirrors = names(seeing);
        view.fixedNames = names(new ArrayList<>(wholes.whole().keySet()));
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

    /**
     * How long a viewer's next redraw on a move waits, after one that took this long.
     *
     * <p>A redraw right against a deep mirror projects the whole half-sphere and took sixty-five
     * milliseconds, ten times a second: two thirds of the main thread for one viewer. It rests
     * three times as long as it took, so a viewer costs at most a quarter of a tick's time, and a
     * quick redraw still comes ten times a second.
     *
     * @param tookMillis
     *            how long the last redraw took
     * @return the least time before the next, in milliseconds
     */
    static long restAfter(final long tookMillis)
    {
        return Math.max(REDRAW_MILLIS, restFactor * tookMillis);
    }

    /** Whether nothing a view depends on has changed since it was last drawn. */
    private static boolean unchanged(final View view, final List<Window> seeing, final long eye,
        final String stamp, final long now)
    {
        return (view.eye == eye) && view.stamp.equals(stamp) && ((now - view.composedAt) < RESAMPLE_MILLIS)
            && (view.generation == MirrorCaptures.generation()) && view.mirrors.equals(names(seeing));
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
            // Not before the viewer's rest is up: a slow redraw's rest is longer than two ticks.
            final long left = view.rest - (now() - view.composedAt);
            final long ticks = Math.max(REDRAW_TICKS, (left + 49L) / 50L);
            WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(),
                () -> catchUp(player), ticks);
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
        final double radius = ConfigManager.getMirrorProximityDistance();
        final Location at = player.getLocation();
        final long now = now();
        final List<Window> seeing = new ArrayList<>();
        if (BLIND.contains(player.getUniqueId()))
        {
            return seeing;
        }
        final String fullName = FULL.get(player.getUniqueId());
        for (final Window window : WINDOWS.values())
        {
            // A mirror drawn whole for an admin stays drawn wherever they stand, looking or not,
            // so they can walk round what the capture holds.
            if (!window.open.isEmpty() && window.mirror.name().equals(fullName)
                && window.banner.getWorld().equals(player.getWorld()))
            {
                seeing.add(window);
                continue;
            }
            if (!window.open.isEmpty() && window.banner.getWorld().equals(player.getWorld())
                && (fromBanner(window.banner, at.getX(), at.getY(), at.getZ()) <= (radius * radius))
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
        final double radius = ConfigManager.getMirrorProximityDistance();
        for (final Window window : WINDOWS.values())
        {
            if (window.banner.getWorld().equals(player.getWorld())
                && (fromBanner(window.banner, to.getX(), to.getY(), to.getZ()) <= (radius * radius)))
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
     * side. Then, nearest window first: a whole window's room as it is, and a clipped window's
     * room judged against this eye ({@link #clipWhole}). A window whose room is not held yet --
     * the server's share of work spent before it was built -- draws nothing until it is.
     */
    private static Map<Long, BlockData> compose(final View view, final Location eye, final List<Window> seeing,
        final Wholes wholes, final Set<Long> before, final long now, final List<Entity> inside,
        final Budget budget)
    {
        final Map<Window, Whole> fixed = wholes.whole();
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
                // Both banners of a pair: the second is to the right of the first, looking at the wall.
                final MirrorBlock banner = window.mirror.banner();
                final Spot into = window.shape.into();
                for (int across = 0; across < window.shape.width(); across++)
                {
                    wanted.put(key(banner.x() - (across * into.z()), banner.y(), banner.z() + (across * into.x())), air);
                }
            }
            window.open.forEach(cell -> wanted.put(key(cell.x(), cell.y(), cell.z()), barrier));
        }
        for (final Window window : nearestFirst(seeing, eye))
        {
            final Whole whole = fixed.get(window);
            final Whole clipped = wholes.clipped().get(window);
            if (whole != null)
            {
                whole.blocks().forEach(wanted::putIfAbsent);
                budget.fixed += whole.blocks().size();
                budget.fixedDepth = Math.max(budget.fixedDepth, whole.depth());
            }
            else if (clipped != null)
            {
                clipWhole(view, eye, window, clipped, seeing, allOpen, wanted, before, budget, now);
            }
        }
        if (!seeing.isEmpty())
        {
            creaturesInside(seeing.get(0).banner.getWorld(), eye, ConfigManager.getMirrorViewDepth(), seeing, fixed,
                allOpen, inside);
        }
        return wanted;
    }

    /**
     * A clipped room's far part as last judged: which room, from which cell the eye was in and
     * the point it was judged from ({@link #fatEye}), when, and the blocks kept.
     */
    private record Far(Whole whole, int cellX, int cellY, int cellZ, double cell, Location from, long at,
        Map<Long, BlockData> blocks)
    {
        /** A far part judged for the cell an eye is in, from a point, for a room, now. */
        static Far judged(final Whole whole, final Location eye, final double cell, final Location from,
            final long now, final Map<Long, BlockData> blocks)
        {
            return new Far(whole, cellOf(eye.getX(), cell), cellOf(eye.getY(), cell), cellOf(eye.getZ(), cell), cell,
                from, now, blocks);
        }

        /** Whether this still stands for an eye, or the far part is to be judged again. */
        boolean standsFor(final Whole room, final Location eye, final long now)
        {
            // The room's blocks, not the record round them, which every redraw makes anew.
            return (whole.blocks() == room.blocks()) && (cellX == cellOf(eye.getX(), cell))
                && (cellY == cellOf(eye.getY(), cell)) && (cellZ == cellOf(eye.getZ(), cell)) && ((now - at) < FAR_MILLIS);
        }

        /** Which cell of a size a coordinate falls in. */
        private static int cellOf(final double at, final double cell)
        {
            return (int) Math.floor(at / cell);
        }
    }

    /** The most an eye may move before a clipped room's far part is judged again, however wide the wall. */
    private static final double WIDEST_FAR_CELL = 4.0;

    /**
     * How far an eye may move before a clipped room's far part is judged again, behind a wall.
     *
     * <p>Half a block less than the wall is wide, up to four. The far part is judged for every
     * eye in the cell at once ({@link #clipWhole}), so a block drawn for an eye at one edge of
     * it lands, from the other edge, up to the cell's width along the wall, which is where the
     * wall has to hide it; and only the inner half of a wall block with open air past it counts
     * as hiding anything, so that a step out of the cell has somewhere to land before the next
     * redraw. What is left of the wall is the cell: half a block behind a wall a block wide,
     * where "there's a lot of leaking around the border". Wider cells send a wider far part for
     * the wall to hide, so four is as far as it goes.
     *
     * @param border
     *            how many blocks of solid wall stand on every side of the opening
     * @return the cell, in blocks
     */
    static double farCellFor(final int border)
    {
        return Math.min(WIDEST_FAR_CELL, Math.max(0.5, border - 0.5));
    }

    /**
     * The point a clipped room's far part is judged from for every eye in a cell: the middle of
     * the cell, kept no nearer the face than the eye itself, since a cell four blocks wide can
     * reach past the wall.
     */
    private static Location fatEye(final Location eye, final double cell, final MirrorWindow shape)
    {
        final Location from = eye.clone();
        from.setX(middleOf(eye.getX(), cell));
        from.setY(middleOf(eye.getY(), cell));
        from.setZ(middleOf(eye.getZ(), cell));
        final boolean alongX = shape.into().x() != 0;
        final double toEye = shape.face() - (alongX ? eye.getX() : eye.getZ());
        final double toMiddle = shape.face() - (alongX ? from.getX() : from.getZ());
        if (((toMiddle * toEye) <= 0.0) || (Math.abs(toMiddle) < Math.abs(toEye)))
        {
            if (alongX)
            {
                from.setX(eye.getX());
            }
            else
            {
                from.setZ(eye.getZ());
            }
        }
        return from;
    }

    /** The middle of the cell of a size a coordinate falls in. */
    private static double middleOf(final double at, final double cell)
    {
        return (Math.floor(at / cell) + 0.5) * cell;
    }

    /** The windows a viewer sees whose rooms are held whole: drawn as they are, or clipped to the eye. */
    private record Wholes(Map<Window, Whole> whole, Map<Window, Whole> clipped)
    {
        Whole of(final Window window)
        {
            final Whole held = whole.get(window);
            return (held != null) ? held : clipped.get(window);
        }
    }

    /**
     * The wall windows among those seen, their whole rooms brought up to date, sorted by how they
     * are drawn.
     *
     * <p>Whole: walled to the proximity distance, with no other window near enough for the two
     * rooms to overlap. Two walled windows seen together -- alcoves along a corridor -- would each
     * fill the same space behind the wall with a different room, and looking into one would show
     * the other's. Those, and any wall short of that, are clipped to each eye instead: the same
     * whole room, judged block by block against where the eye is ({@link #clipWhole}). Only a
     * freestanding window is left to be walked ({@link Pass}).
     */
    private static Wholes wholesOf(final List<Window> seeing, final long now,
        final boolean mayWork, final String fullName)
    {
        final Map<Window, Whole> whole = new HashMap<>();
        final Map<Window, Whole> clipped = new HashMap<>();
        if (fullName != null)
        {
            // An admin's forced view: that one mirror whole and unlimited, the rest not at all.
            for (final Window window : seeing)
            {
                if (window.mirror.name().equals(fullName))
                {
                    whole.put(window, fullView(window, now));
                }
            }
            return new Wholes(whole, clipped);
        }
        final double apart = MirrorPlacement.apartToDrawWhole();
        for (final Window window : seeing)
        {
            // Seen or not: alcoves a block apart along a wall would otherwise each fill the same
            // space behind it with a different far side, for whoever looks into either.
            boolean alone = walled(window);
            for (final Window other : WINDOWS.values())
            {
                if (alone && (other != window) && other.banner.getWorld().equals(window.banner.getWorld())
                    && (fromBanner(other.banner, window.banner.getX(), window.banner.getY(), window.banner.getZ())
                        < (apart * apart)))
                {
                    alone = false;
                }
            }
            if (!fixedIsFresh(window, now))
            {
                if (!mayWork)
                {
                    // Held whole once the server has a moment; this viewer waits meanwhile.
                    continue;
                }
                fixedView(window, now);
            }
            window.fixedUsedAt = now;
            // Whole only while it is small enough to send at once; see MOST_WHOLE.
            final boolean asIs = alone && (window.fixed.size() <= mostWhole);
            (asIs ? whole : clipped).put(window, new Whole(window.fixed, window.fixedDepth));
        }
        return new Wholes(whole, clipped);
    }

    /**
     * Draws a whole room through a window for one eye: every block of it that is seen through
     * the opening and lands where the wall hides the rest, as {@link Pass} judges a block, and
     * nothing walked, occluded or budgeted.
     *
     * <p>A wall short of the proximity distance used to be walked like a freestanding mirror,
     * reaching what one redraw could afford: shallower right against the mirror and while
     * walking, and never the render distance. The room is held whole as a walled mirror's is, and
     * each redraw keeps the blocks this eye may see. What the capture kept is already only what
     * somebody at the opening could see, so there is nothing to occlude, and a bound on where a
     * block can land ({@link MirrorWindow#mightLandOn}) spares most of the room a projection.
     *
     * <p>Only the near part every time. The far part, past {@link #NEAR_DISTANCE}, is judged for
     * the whole cell the eye is in ({@link #farCellFor}) rather than for the eye: from the
     * cell's middle ({@link #fatEye}), with each block's landing widened by half the cell on
     * every side, which is as far as it moves for any eye in the cell. A block seen through the
     * opening from anywhere in the cell is drawn, and lands from everywhere in it where the wall
     * hides it. It stands until the eye leaves the cell or {@link #FAR_MILLIS} have passed, and
     * the same blocks are sent per block walked, in a fraction of the batches.
     */
    private static void clipWhole(final View view, final Location eye, final Window window, final Whole whole,
        final List<Window> seeing, final Set<Long> allOpen, final Map<Long, BlockData> wanted,
        final Set<Long> before, final Budget budget, final long now)
    {
        final Far last = view.far.get(window.mirror.name());
        final boolean farAgain = (last == null) || !last.standsFor(whole, eye, now);
        final Map<Long, BlockData> farKept = farAgain ? new HashMap<>() : last.blocks();
        final double farCell = farCellFor(window.border);
        // Near and far are split from the point the far part is judged from, so a block does not
        // change sides as the eye moves within the cell and go unjudged by both.
        final Location from = farAgain ? fatEye(eye, farCell, window.shape) : last.from();
        final double spread = farCell / 2.0;
        // What stands in front of the opening is thrown onto the face from this eye alone.
        final Sight sight = new Sight(seeing, allOpen, shielded(window, eye, now), before, budget);
        final double[] span = spanOf(window);
        final Eye own = new Eye(eye, span, 0.0, sight);
        final Eye fat = farAgain ? new Eye(from, new double[] { span[0] - spread, span[1] + spread,
            span[2] - spread, span[3] + spread }, spread, sight) : null;
        keep(window, whole, from, own, fat, wanted, farKept);
        if (farAgain)
        {
            view.far.put(window.mirror.name(), Far.judged(whole, eye, farCell, from, now, farKept));
        }
        else
        {
            farKept.forEach(wanted::putIfAbsent);
            budget.near += farKept.size();
        }
        budget.fixedDepth = Math.max(budget.fixedDepth, whole.depth());
    }

    /**
     * Keeps every block of a room an eye sees: the near part as the viewer's own eye sees it, the
     * far part as the fat eye does, and the far part left as it stands when there is no fat eye
     * this redraw.
     *
     * @param from
     *            the point near and far are split from
     * @param fat
     *            the eye the far part is judged for, or null to leave the far part alone
     */
    private static void keep(final Window window, final Whole whole, final Location from, final Eye own,
        final Eye fat, final Map<Long, BlockData> wanted, final Map<Long, BlockData> farKept)
    {
        final double near = nearDistance * nearDistance;
        for (final Map.Entry<Long, BlockData> entry : whole.blocks().entrySet())
        {
            final long cell = entry.getKey();
            if (wanted.containsKey(cell))
            {
                continue;
            }
            final double dx = (unpackX(cell) + 0.5) - from.getX();
            final double dy = (unpackY(cell) + 0.5) - from.getY();
            final double dz = (unpackZ(cell) + 0.5) - from.getZ();
            final boolean farOff = ((dx * dx) + (dy * dy) + (dz * dz)) > near;
            if (farOff && (fat == null))
            {
                continue;
            }
            if (!(farOff ? fat : own).sees(window, cell))
            {
                continue;
            }
            wanted.put(cell, entry.getValue());
            if (farOff)
            {
                farKept.put(cell, entry.getValue());
            }
        }
    }

    /** What one redraw judges a clipped room's blocks against, the same for every eye it judges from. */
    private record Sight(List<Window> seeing, Set<Long> allOpen, Set<Long> shielded, Set<Long> before, Budget budget)
    {
    }

    /**
     * An eye a clipped room's block is judged from: the viewer's own, or the middle of their cell
     * with every landing widened by half the cell, for the far part ({@link #fatEye}).
     */
    private static final class Eye
    {
        private final Location from;
        private final double[] span;
        private final double spread;
        private final Sight sight;

        Eye(final Location from, final double[] span, final double spread, final Sight sight)
        {
            this.from = from;
            this.span = span;
            this.spread = spread;
            this.sight = sight;
        }

        /**
         * Whether a block is seen through the window from here and lands where the wall hides it,
         * counting the projection and, if it is, the block.
         */
        boolean sees(final Window window, final long cell)
        {
            final int x = unpackX(cell);
            final int y = unpackY(cell);
            final int z = unpackZ(cell);
            if (!window.shape.mightLandOn(from.getX(), from.getY(), from.getZ(), x, y, z, span))
            {
                return false;
            }
            sight.budget().projected++;
            final double[] rect = seenThrough(from, window, x, y, z, sight.seeing(), spread);
            if ((rect == null) || !coveredBy(window, rect, sight.allOpen(), sight.shielded(),
                sight.before().contains(cell) ? KEPT_BESIDE : MOST_BESIDE))
            {
                return false;
            }
            sight.budget().near++;
            return true;
        }
    }

    /** The face a block must land on to be seen through a window: its opening and frame, and a block round them. */
    private static double[] spanOf(final Window window)
    {
        final boolean alongX = window.shape.into().x() != 0;
        final double[] span = { Double.MAX_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE };
        for (final List<Spot> cells : List.of(window.open, window.frame))
        {
            for (final Spot cell : cells)
            {
                final int across = alongX ? cell.z() : cell.x();
                span[0] = Math.min(span[0], across - 1.0);
                span[1] = Math.max(span[1], across + 2.0);
                span[2] = Math.min(span[2], cell.y() - 1.0);
                span[3] = Math.max(span[3], cell.y() + 2.0);
            }
        }
        return span;
    }

    /**
     * A window's whole capture through its opening, without limits, kept until the capture changes.
     *
     * <p>Reaches every corner of the capture's box from the opening's middle, and is capped by
     * nothing: an admin asked to see all of it.
     */
    private static Whole fullView(final Window window, final long now)
    {
        if ((window.full != null) && (window.fullFrom == window.capture))
        {
            return window.full;
        }
        final int[] box = window.capture.bounds();
        final Spot far = window.shape.far();
        final int dx = Math.max(Math.abs(box[0] - far.x()), Math.abs(box[3] - far.x())) + 1;
        final int dy = Math.max(Math.abs(box[1] - far.y()), Math.abs(box[4] - far.y())) + 1;
        final int dz = Math.max(Math.abs(box[2] - far.z()), Math.abs(box[5] - far.z())) + 1;
        final int depth = (int) Math.ceil(Math.sqrt(((double) dx * dx) + ((double) dy * dy) + ((double) dz * dz))) + 1;
        final Map<Long, BlockData> blocks = fixedTo(window, depth, now, Integer.MAX_VALUE);
        window.full = new Whole((blocks == null) ? new HashMap<>() : blocks, depth);
        window.fullFrom = window.capture;
        return window.full;
    }

    /** Whether a window's fixed view is there, from its current capture and depth, and not old. */
    private static boolean fixedIsFresh(final Window window, final long now)
    {
        return (window.fixed != null) && (window.fixedFrom == window.capture)
            && (window.fixedFor == ConfigManager.getMirrorViewDepth()) && ((now - window.fixedAt) < FIXED_MILLIS);
    }

    /** Whether this second's share of work is spent, starting a new second's if one has begun. */
    private static boolean overBudget(final long now)
    {
        if ((now - workSecond) >= 1000L)
        {
            workSecond = now;
            workSpent = 0;
        }
        return workSpent >= workPerSecond;
    }

    /**
     * Whether a mirror's fixed view is held, for a test.
     *
     * @param name
     *            the mirror
     * @return true if its window holds one
     */
    static boolean holdsFixedView(final String name)
    {
        final Window window = WINDOWS.get(name);
        return (window != null) && (window.fixed != null);
    }

    /**
     * Whether a window's face is solid as far as the proximity distance on every side of its opening.
     *
     * <p>Then the wall hides whatever of the far side lies beside the opening, from anywhere a
     * viewer can be, and the far side can be drawn whole. A gap anywhere in that span shows it.
     */
    private static boolean walled(final Window window)
    {
        return gapBeside(window) == null;
    }

    /** The first block of the face touching a window's opening that is not solid, or null if none. */
    private static Spot gapBeside(final Window window)
    {
        // As far as the proximity distance: a viewer that far to one side looks at the space
        // behind the wall across the face that far out, and a block of open air there shows it.
        // One ring of wall was not enough -- a pillar two blocks wide in open air passed.
        final MirrorWindow shape = window.shape;
        final Set<Long> opening = new HashSet<>();
        shape.forEachOpening((x, y, z) -> opening.add(key(x, y, z)));
        final int reach = wallReach();
        final int[] span = acrossSpan(shape, reach);
        for (int across = span[0]; across <= span[1]; across++)
        {
            for (int y = shape.base().y() - reach; y <= (shape.base().y() + MirrorWindow.HEIGHT + reach); y++)
            {
                final long face = faceKey(shape, across, y);
                if (!opening.contains(face) && !window.solid.contains(face))
                {
                    return new Spot(unpackX(face), unpackY(face), unpackZ(face));
                }
            }
        }
        return null;
    }

    /** What decides a view's fixed windows' content, so a change in any of them redraws it. */
    private static String stampOf(final List<Window> seeing, final Wholes wholes)
    {
        final StringBuilder stamp = new StringBuilder();
        for (final Window window : seeing)
        {
            final Whole whole = wholes.of(window);
            // Where it opens onto too: a right-click changes that without changing anything else.
            stamp.append(window.mirror.name()).append('@').append(window.shape.far())
                .append(window.shape.mirrored() ? "~" : "").append('=')
                .append((whole == null) ? 0 : System.identityHashCode(whole.blocks())).append(';');
        }
        return stamp.toString();
    }

    /**
     * Brings a walled window's fixed view up to date: everything behind its wall out to the depth
     * from the middle of its opening, the same whichever eye looks.
     *
     * <p>Trimmed to each eye, a view changed with every step, and a mirror seen from close up
     * reached less far than from a step back: blocks appeared and vanished as a viewer walked.
     * In a wall there is nothing to trim -- the wall hides whatever lies beside the opening -- so
     * the whole far side is drawn once, and kept for a minute before the real world is read again.
     * The cost is that a viewer sees the far side in place of the real world behind that wall
     * from anywhere else they can see it, a doorway round the side, while they are looking in.
     * A view past {@link #MOST_FIXED} blocks is cut shallower until it fits.
     */
    private static void fixedView(final Window window, final long now)
    {
        final int configured = ConfigManager.getMirrorViewDepth();
        if (fixedIsFresh(window, now))
        {
            return;
        }
        int depth = configured;
        Map<Long, BlockData> view = fixedTo(window, depth, now, mostFixed);
        // Half a sphere of the depth is what was looked at, found in the capture or not.
        workSpent += (int) Math.min(Integer.MAX_VALUE / 2.0, 2.1 * depth * depth * depth);
        while ((view == null) && (depth > 4))
        {
            depth = Math.max(4, (depth * 3) / 4);
            view = fixedTo(window, depth, now, mostFixed);
            workSpent += (int) (2.1 * depth * depth * depth);
        }
        window.fixed = (view == null) ? new HashMap<>() : view;
        window.fixedFrom = window.capture;
        window.fixedAt = now;
        window.fixedFor = configured;
        window.fixedDepth = depth;
    }

    /**
     * A walled window's view to a depth, or null if it would hold more than {@code most}.
     *
     * <p>Every block the capture kept that lies behind the wall within the depth of the opening's
     * middle: the far side's block where it is not air, and air where the far side's air was seen
     * and the real block is not empty. The capture holds only what somebody at the opening could
     * see, so this is a walk over what is kept, not over the volume, and a view at the render
     * distance costs what its surfaces cost.
     */
    private static Map<Long, BlockData> fixedTo(final Window window, final int depth, final long now,
        final int most)
    {
        if (window.capture.complete())
        {
            // A capture that never went through the seen pass keeps nothing for air: walk the volume.
            return fixedToByVolume(window, depth, now, most);
        }
        final MirrorWindow shape = window.shape;
        final MirrorCapture capture = window.capture;
        final World here = window.banner.getWorld();
        final double[] centre = centreOf(shape);
        final int min = here.getMinHeight();
        final int max = here.getMaxHeight();
        final BlockData air = Bukkit.createBlockData(Material.AIR);
        final double reach = (double) depth * depth;
        final Map<Long, BlockData> view = new HashMap<>();
        final boolean[] over = { false };
        capture.forEachKept((fx, fy, fz, farAir) ->
        {
            if (over[0])
            {
                return;
            }
            final Spot at = shape.hereOf(fx, fy, fz);
            final int x = at.x();
            final int y = at.y();
            final int z = at.z();
            final int layer = ((x - shape.base().x()) * shape.into().x()) + ((z - shape.base().z()) * shape.into().z());
            if ((layer < 1) || (y < min) || (y >= max))
            {
                return;
            }
            final double dx = (x + 0.5) - centre[0];
            final double dy = (y + 0.5) - centre[1];
            final double dz = (z + 0.5) - centre[2];
            if (((dx * dx) + (dy * dy) + (dz * dz)) >= reach)
            {
                return;
            }
            if (!farAir)
            {
                view.put(key(x, y, z), turned(window, capture.at(fx, fy, fz)));
            }
            else if (!reallyEmpty(here, x, y, z, now))
            {
                view.put(key(x, y, z), air);
            }
            if (view.size() > most)
            {
                over[0] = true;
            }
        });
        return over[0] ? null : view;
    }

    /**
     * The same for a complete capture, whose missing entries are air: every block behind the wall
     * within the depth, walked through the volume.
     */
    private static Map<Long, BlockData> fixedToByVolume(final Window window, final int depth, final long now,
        final int most)
    {
        final MirrorWindow shape = window.shape;
        final MirrorCapture capture = window.capture;
        final World here = window.banner.getWorld();
        final boolean alongX = shape.into().x() != 0;
        final int sign = alongX ? shape.into().x() : shape.into().z();
        final int baseAlong = alongX ? shape.base().x() : shape.base().z();
        final double[] centre = centreOf(shape);
        final double centreAcross = centre[alongX ? 2 : 0];
        final int min = here.getMinHeight();
        final int max = here.getMaxHeight();
        final BlockData air = Bukkit.createBlockData(Material.AIR);
        final double reach = (double) depth * depth;
        final Map<Long, BlockData> view = new HashMap<>();
        for (int layer = 1; layer < depth; layer++)
        {
            final int along = baseAlong + (sign * layer);
            final double wide = Math.sqrt(reach - ((double) layer * layer));
            for (int across = (int) Math.floor(centreAcross - wide); across <= (int) Math.ceil(centreAcross + wide); across++)
            {
                final double offAcross = (across + 0.5) - centreAcross;
                final int yTo = Math.min(max - 1, (int) Math.ceil(centre[1] + wide));
                for (int y = Math.max(min, (int) Math.floor(centre[1] - wide)); y <= yTo; y++)
                {
                    final double offY = (y + 0.5) - centre[1];
                    if ((((double) layer * layer) + (offAcross * offAcross) + (offY * offY)) >= reach)
                    {
                        continue;
                    }
                    final int x = alongX ? along : across;
                    final int z = alongX ? across : along;
                    final Spot at = shape.farOf(x, y, z);
                    if (!capture.contains(at.x(), at.y(), at.z()))
                    {
                        continue;
                    }
                    if (!capture.isAir(at.x(), at.y(), at.z()))
                    {
                        view.put(key(x, y, z), turned(window, capture.at(at.x(), at.y(), at.z())));
                    }
                    else if (!reallyEmpty(here, x, y, z, now))
                    {
                        view.put(key(x, y, z), air);
                    }
                    if (view.size() > most)
                    {
                        return null;
                    }
                }
            }
        }
        return view;
    }

    /** Whether a real block is empty, read afresh: above its column's highest block it is. */
    private static boolean reallyEmpty(final World here, final int x, final int y, final int z, final long now)
    {
        return (y > topHere(here, x, z, now))
            || (here.isChunkLoaded(x >> 4, z >> 4) && here.getBlockAt(x, y, z).isEmpty());
    }

    /**
     * The middle of a window's opening, which its depth is measured from.
     *
     * @return {@code {x, y, z}}
     */
    private static double[] centreOf(final MirrorWindow shape)
    {
        final boolean alongX = shape.into().x() != 0;
        // Halfway along the opening: for two banners, between them, a step right being (-into.z, into.x).
        final int rightStep = alongX ? shape.into().x() : -shape.into().z();
        final double across = (alongX ? shape.base().z() : shape.base().x()) + 0.5
            + ((shape.width() - 1) * 0.5 * rightStep);
        final double y = shape.base().y() + (MirrorWindow.HEIGHT / 2.0);
        final double along = (alongX ? shape.base().x() : shape.base().z()) + 0.5;
        return alongX ? new double[] { along, y, across } : new double[] { across, y, along };
    }

    /**
     * A far-side state as it shows through a window: turned the way the window turns the far side.
     *
     * <p>A pane's connections and a stair's facing are compass directions; drawn as captured, a
     * far side turned round showed panes that did not join. Turned once per state and window.
     */
    private static BlockData turned(final Window window, final BlockData data)
    {
        if ((data == null) || ((window.rotation == StructureRotation.NONE)
            && (window.flip == org.bukkit.block.structure.Mirror.NONE)))
        {
            return data;
        }
        return window.turned.computeIfAbsent(data, original ->
        {
            final BlockData copy = original.clone();
            if (copy == null)
            {
                return original;
            }
            if (window.rotation != StructureRotation.NONE)
            {
                copy.rotate(window.rotation);
            }
            if (window.flip != org.bukkit.block.structure.Mirror.NONE)
            {
                copy.mirror(window.flip);
            }
            return copy;
        });
    }

    /** Whether a block is inside a walled window's fixed view: behind its wall, within its depth. */
    private static boolean insideFixed(final Window window, final int x, final int y, final int z,
        final int depth)
    {
        final MirrorWindow shape = window.shape;
        final int layer = ((x - shape.base().x()) * shape.into().x()) + ((z - shape.base().z()) * shape.into().z());
        if (layer < 1)
        {
            return false;
        }
        final double[] centre = centreOf(shape);
        final double dx = (x + 0.5) - centre[0];
        final double dy = (y + 0.5) - centre[1];
        final double dz = (z + 0.5) - centre[2];
        return ((dx * dx) + (dy * dy) + (dz * dz)) < ((double) depth * depth);
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
        final List<Window> seeing, final Map<Window, Whole> fixed, final Set<Long> allOpen,
        final List<Entity> inside)
    {
        // Depth is from the opening, and the eye may be the proximity distance from that.
        final double reach = Math.max(radius, ConfigManager.getMirrorViewDepth())
            + ConfigManager.getMirrorProximityDistance() + 1.0;
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
                final Whole whole = fixed.get(window);
                if (whole != null)
                {
                    if (insideFixed(window, x, y, z, whole.depth()))
                    {
                        inside.add(entity);
                        break;
                    }
                    continue;
                }
                final double[] rect = seenThrough(eye, window, x, y, z, seeing, 0.0);
                if ((rect != null) && coveredBy(window, rect, allOpen, Set.of(), MOST_BESIDE))
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

    /** What one redraw did, across every window a viewer sees. */
    private static final class Budget
    {
        /** Blocks of clipped rooms drawn for this eye. */
        private int near;
        /** Blocks of rooms drawn whole, and the deepest they reach. */
        private int fixed;
        private int fixedDepth;
        /** Blocks of clipped rooms projected against the eye, which is what a redraw costs. */
        private int projected;
    }

    /**
     * Where a block behind the opening appears on it, if it is seen through this window and only
     * through it.
     *
     * <p>Seen through the opening, all of it hidden by solid blocks or the opening itself --
     * never across open air or another window's opening -- and through no opening in the same
     * face whose middle the line of sight passes nearer.
     *
     * @param spread
     *            how far the outline is widened on every side: half the cell, for a far block
     *            judged for every eye in one ({@link #clipWhole}), else nothing
     * @return the block's outline on the opening's face, or null if it is not seen through it
     */
    private static double[] seenThrough(final Location eye, final Window window, final int x,
        final int y, final int z, final List<Window> seeing, final double spread)
    {
        final double[] rect = window.shape.projected(eye.getX(), eye.getY(), eye.getZ(), x, y, z);
        if (rect == null)
        {
            return null;
        }
        rect[0] -= spread;
        rect[1] += spread;
        rect[2] -= spread;
        rect[3] += spread;
        // On the opening, or on the frame round it, which hides it until the eye moves.
        if (!window.shape.overlaps(rect, window.open) && !window.shape.overlaps(rect, window.frame))
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
        final Set<Long> allOpen, final Set<Long> shielded, final double mostBeside)
    {
        return window.shape.covered(rect,
            (MirrorWindow.Cover) (across, up) -> cover(window, across, up, allOpen, shielded), mostBeside);
    }

    /** A viewer's windows, nearest first, so a spent budget cuts the furthest views short. */
    private static List<Window> nearestFirst(final List<Window> seeing, final Location eye)
    {
        final List<Window> sorted = new ArrayList<>(seeing);
        sorted.sort(Comparator
            .comparingDouble((Window window) -> fromBanner(window.banner, eye.getX(), eye.getY(), eye.getZ()))
            .thenComparing(window -> window.mirror.name()));
        return sorted;
    }

    /** How far a point is from a banner block's corner, squared, without making a Location per ask. */
    private static double fromBanner(final Block banner, final double x, final double y, final double z)
    {
        final double dx = x - banner.getX();
        final double dy = y - banner.getY();
        final double dz = z - banner.getZ();
        return (dx * dx) + (dy * dy) + (dz * dz);
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

    /** How much of the wall's outermost ring, from its open edge in, absorbs a step instead of hiding a block. */
    private static final double MARGIN = 0.5;

    /**
     * How much of a block of a window's face keeps a drawn block behind it out of sight elsewhere:
     * all of the opening itself, of a solid block of the wall, or of a block of the face hidden
     * from this eye by something real in front of it -- except along the wall's open edges.
     *
     * <p>A block drawn onto the wall beside the opening is hidden only from the eye it was drawn
     * for, and a step shifts where it lands before the next redraw. The outer half of the wall's
     * outermost ring, on each side it is open, is kept for that shift: with one block of wall a
     * block may spill half a block onto it and no more, and with two the inner ring is all wall.
     * A ring hidden by something real in front hides all of itself, as before.
     */
    private static double[] cover(final Window window, final int across, final int y,
        final Set<Long> allOpen, final Set<Long> shielded)
    {
        final long face = faceKey(window.shape, across, y);
        final boolean whole = window.openKeys.contains(face) || (shielded.contains(face) && !allOpen.contains(face));
        if (!whole && (!window.solid.contains(face) || allOpen.contains(face)))
        {
            return null;
        }
        final Integer open = whole ? null : window.margin.get(face);
        if (open == null)
        {
            return new double[] { across, across + 1.0, y, y + 1.0 };
        }
        return new double[] { across + (((open & OPEN_BEFORE) != 0) ? MARGIN : 0.0),
            (across + 1.0) - (((open & OPEN_AFTER) != 0) ? MARGIN : 0.0),
            y + (((open & OPEN_BELOW) != 0) ? MARGIN : 0.0), (y + 1.0) - (((open & OPEN_ABOVE) != 0) ? MARGIN : 0.0) };
    }

    /** A margin block's open sides, as bits: the next block along the face, the one before, above, below. */
    private static final int OPEN_AFTER = 1;
    private static final int OPEN_BEFORE = 2;
    private static final int OPEN_ABOVE = 4;
    private static final int OPEN_BELOW = 8;

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
        final int[] span = acrossSpan(shape, SURROUND);
        final int lowY = shape.base().y() - SURROUND;
        final int highY = shape.base().y() + MirrorWindow.HEIGHT + SURROUND;
        final Set<Long> hidden = new HashSet<>();
        for (int front = 1; front <= SURROUND; front++)
        {
            final int along = (alongX ? shape.base().x() : shape.base().z()) - (front * (alongX ? into.x() : into.z()));
            for (int across = span[0]; across <= span[1]; across++)
            {
                for (int y = lowY; y <= highY; y++)
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
                    final int[] cells = withinFace(rect, span, lowY, highY);
                    for (int a = cells[0]; a <= cells[1]; a++)
                    {
                        for (int b = cells[2]; b <= cells[3]; b++)
                        {
                            hidden.add(faceKey(shape, a, b));
                        }
                    }
                }
            }
        }
        return hidden;
    }

    /**
     * The blocks of the face a shadow covers whole, and no further than the face that is read.
     *
     * <p>A block whose near corner is almost level with the eye throws a shadow hundreds of blocks
     * across; the server hung for fifteen seconds adding its cells one by one. Nothing outside the
     * face read for this window is ever asked about, so nothing outside it is worth marking.
     *
     * @return {@code {acrossFrom, acrossTo, yFrom, yTo}}, inclusive, and empty when from is past to
     */
    static int[] withinFace(final double[] rect, final int[] span, final int lowY, final int highY)
    {
        return new int[] { Math.max(span[0], (int) Math.ceil(rect[0])), Math.min(span[1], (int) Math.floor(rect[1] - 1.0)),
            Math.max(lowY, (int) Math.ceil(rect[2])), Math.min(highY, (int) Math.floor(rect[3] - 1.0)) };
    }

    /**
     * The coordinates along a window's face that its wall is read across: the opening, one or two
     * columns, and a reach and one more on either side of it.
     *
     * @return {@code {from, to}}, both inclusive
     */
    private static int[] acrossSpan(final MirrorWindow shape, final int reach)
    {
        final boolean alongX = shape.into().x() != 0;
        // A step right, looking at the wall, is (-into.z, into.x); a pair's second column may lie below its first.
        final int rightStep = alongX ? shape.into().x() : -shape.into().z();
        final int first = alongX ? shape.base().z() : shape.base().x();
        final int low = first + Math.min(0, (shape.width() - 1) * rightStep);
        final int high = first + Math.max(0, (shape.width() - 1) * rightStep);
        return new int[] { low - (reach + 1), high + (reach + 1) };
    }

    /** How far out a window's wall is read: a viewer the proximity distance to one side looks past that much of it. */
    private static int wallReach()
    {
        return Math.max(SURROUND, ConfigManager.getMirrorProximityDistance());
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
        final int reach = wallReach();
        final int[] span = acrossSpan(shape, reach);
        final Set<Long> solid = new HashSet<>();
        for (int across = span[0]; across <= span[1]; across++)
        {
            for (int y = shape.base().y() - reach;
                y <= (shape.base().y() + MirrorWindow.HEIGHT + reach); y++)
            {
                final long face = faceKey(shape, across, y);
                if (here.getBlockAt(unpackX(face), y, unpackZ(face)).getBlockData().isOccluding())
                {
                    solid.add(face);
                }
            }
        }
        window.solid = solid;
        window.border = borderOf(shape, solid, reach);
        window.margin = marginOf(shape, solid, span, reach);
        window.frame = frameOf(shape, solid);
        window.solidAt = now;
    }

    /**
     * How many blocks of solid wall stand on every side of a window's opening.
     *
     * <p>Ring by ring out from the opening, to the first ring with a block that is not solid;
     * a wall solid to the edge of what was read counts as that far. The one-block wall is the
     * case that matters ({@link #farCellFor}).
     *
     * @param shape
     *            the window
     * @param solid
     *            the solid blocks of its face, as {@link #refreshSolid} read them
     * @param reach
     *            how far out the face was read
     * @return the border, in blocks, from 0 to {@code reach}
     */
    private static int borderOf(final MirrorWindow shape, final Set<Long> solid, final int reach)
    {
        final boolean alongX = shape.into().x() != 0;
        final int rightStep = alongX ? shape.into().x() : -shape.into().z();
        final int first = alongX ? shape.base().z() : shape.base().x();
        final int low = first + Math.min(0, (shape.width() - 1) * rightStep);
        final int high = first + Math.max(0, (shape.width() - 1) * rightStep);
        final int bottom = shape.base().y();
        final int top = (shape.base().y() + MirrorWindow.HEIGHT) - 1;
        for (int ring = 1; ring <= reach; ring++)
        {
            for (int across = low - ring; across <= (high + ring); across++)
            {
                for (int y = bottom - ring; y <= (top + ring); y++)
                {
                    final boolean onRing = (across == (low - ring)) || (across == (high + ring))
                        || (y == (bottom - ring)) || (y == (top + ring));
                    if (onRing && !solid.contains(faceKey(shape, across, y)))
                    {
                        return ring - 1;
                    }
                }
            }
        }
        return reach;
    }

    /**
     * The outermost ring of a window's solid face: every solid block, the opening aside, with a
     * neighbour in the face that is not solid, and which of its four sides those are.
     *
     * <p>Only neighbours within what was read count, so a wall solid to the edge of the reading
     * has no margin there -- it is drawn whole in any case.
     */
    private static Map<Long, Integer> marginOf(final MirrorWindow shape, final Set<Long> solid, final int[] span,
        final int reach)
    {
        final boolean alongX = shape.into().x() != 0;
        final Set<Long> opening = new HashSet<>();
        shape.forEachOpening((x, y, z) -> opening.add(key(x, y, z)));
        final int lowY = shape.base().y() - reach;
        final int highY = shape.base().y() + MirrorWindow.HEIGHT + reach;
        final int[][] steps = { { 1, 0, OPEN_AFTER }, { -1, 0, OPEN_BEFORE }, { 0, 1, OPEN_ABOVE }, { 0, -1, OPEN_BELOW } };
        final Map<Long, Integer> margin = new HashMap<>();
        for (final long face : solid)
        {
            if (opening.contains(face))
            {
                continue;
            }
            final int across = alongX ? unpackZ(face) : unpackX(face);
            final int y = unpackY(face);
            int open = 0;
            for (final int[] step : steps)
            {
                final int a = across + step[0];
                final int b = y + step[1];
                if ((a >= span[0]) && (a <= span[1]) && (b >= lowY) && (b <= highY) && !solid.contains(faceKey(shape, a, b)))
                {
                    open |= step[2];
                }
            }
            if (open != 0)
            {
                margin.put(face, open);
            }
        }
        return margin;
    }

    /**
     * The solid blocks of a window's face touching its opening, corners too.
     *
     * <p>They hide whatever lies just beside the opening, so a block landing on them from the eye
     * can be drawn before the eye moves to where it shows: sliding along a frame into view, it
     * was drawn only once it came into the opening, a step late.
     */
    private static List<Spot> frameOf(final MirrorWindow shape, final Set<Long> solid)
    {
        final boolean alongX = shape.into().x() != 0;
        final Set<Long> opening = new HashSet<>();
        final List<int[]> cells = new ArrayList<>();
        shape.forEachOpening((x, y, z) ->
        {
            opening.add(key(x, y, z));
            cells.add(new int[] { alongX ? z : x, y });
        });
        final Set<Long> frame = new LinkedHashSet<>();
        for (final int[] cell : cells)
        {
            for (int across = -1; across <= 1; across++)
            {
                for (int up = -1; up <= 1; up++)
                {
                    final long face = faceKey(shape, cell[0] + across, cell[1] + up);
                    if (!opening.contains(face) && solid.contains(face))
                    {
                        frame.add(face);
                    }
                }
            }
        }
        final List<Spot> spots = new ArrayList<>();
        frame.forEach(face -> spots.add(new Spot(unpackX(face), unpackY(face), unpackZ(face))));
        return spots;
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
            workSpent += changes.size();
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
        final BlockState state = states.computeIfAbsent(cell,
            key -> here.getBlockAt(x, unpackY(key), z).getState());
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
        return clock.getAsLong();
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

}
