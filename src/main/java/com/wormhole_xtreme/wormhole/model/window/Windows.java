package com.wormhole_xtreme.wormhole.model.window;

import static com.wormhole_xtreme.wormhole.model.window.WindowStore.ACTIVE;
import static com.wormhole_xtreme.wormhole.model.window.WindowStore.BLIND;
import static com.wormhole_xtreme.wormhole.model.window.WindowStore.CHANGED;
import static com.wormhole_xtreme.wormhole.model.window.WindowStore.FIRST_CHANGED;
import static com.wormhole_xtreme.wormhole.model.window.WindowStore.FULL;
import static com.wormhole_xtreme.wormhole.model.window.WindowStore.OFFERED;
import static com.wormhole_xtreme.wormhole.model.window.WindowStore.STATES;
import static com.wormhole_xtreme.wormhole.model.window.WindowStore.VIEWS;
import static com.wormhole_xtreme.wormhole.model.window.WindowStore.changes;
import static com.wormhole_xtreme.wormhole.model.window.WindowStore.drawnListener;
import static com.wormhole_xtreme.wormhole.model.window.WindowStore.sweepRoomBlocks;
import static com.wormhole_xtreme.wormhole.model.window.WindowStore.sweeps;
import static com.wormhole_xtreme.wormhole.model.window.WindowStore.trimmedAt;
import static com.wormhole_xtreme.wormhole.model.window.WindowStore.workSecond;
import static com.wormhole_xtreme.wormhole.model.window.WindowStore.workSpent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorText;
import com.wormhole_xtreme.wormhole.model.mirror.QuantumMirror;
import com.wormhole_xtreme.wormhole.model.window.WindowShape.Spot;

/**
 * Drawing what is on the other side of every window mirror a player is looking into.
 *
 * <p>Every wall mirror with somewhere to go is a window, opening in the wall it hangs on. Nothing
 * is stored to say so, which is what lets mirrors made before windows existed open as one without
 * being touched.
 *
 * <p>What a window shows is its {@link Capture}: a photograph of the far side, taken once
 * and kept on disk, so the far world need not be loaded to be looked at. A mirror whose capture
 * has not been taken yet stays a banner until it has -- a few seconds, the first time.
 *
 * <p>Nothing in the world changes, the way a gate's event horizon changes nothing. A viewer is
 * sent the banner as air, the open part of the opening as barrier -- invisible, and at least as
 * solid as what it covers -- and the room behind it, held whole ({@link FixedView#fixedView}). A mirror
 * walled to the proximity distance with no neighbour is sent the room as it is, the same from
 * every eye, since the wall hides its edges. Any other is sent the room clipped to the eye
 * ({@link WindowCompose#clipWhole}): only the blocks they could see through an opening from where their eye
 * is, all but a little of whose outline lands on the opening or on wall that hides the rest, each
 * from the opening their line of sight passes through. That is what lets windows share a wall.
 * Their own world's creatures standing inside the view are hidden from them for as long as they
 * look.
 *
 * <h2>What it costs, and what keeps that down</h2>
 *
 * <ul>
 * <li>A room is built once from the capture, a few milliseconds a tick ({@link FixedBuilds}), and
 * built again only when the capture changes or a block changes behind the wall; the capture
 * already holds only the surfaces somebody at the opening could see, so a room at the render
 * distance costs what its surfaces cost.</li>
 * <li>Clipping is one cheap bound and, for the blocks that pass it, one projection each; nothing
 * is walked or occluded.</li>
 * <li>A viewer is redrawn at most ten times a second as they move, and not at all on a sweep
 * where nothing changed. Only the difference is sent -- and, after crossing into a new chunk,
 * what was drawn in the chunks the client is newly handed, since those arrive fresh -- with the
 * whole view again as a long safety net. More than a tick's worth is streamed a tick at a time,
 * nearest the eye first and a chunk section at a time. The server has a share of work a second
 * across every viewer.</li>
 * <li>The far side is read from the capture, in memory, never from the live world.</li>
 * </ul>
 *
 * <p>Blocks, lit and tinted by this world; with {@code mirror-show-entities} on, the far room's
 * creatures too, as {@link StandIns}.
 */
public final class Windows
{
    /** How often a viewer is sent their whole view again when nothing else has prompted it. */
    static final long RESEND_MILLIS = 30_000L;

    /** The unit every count in {@code mirror debug} is said in. */
    static final String BLOCKS = " blocks";

    /** How old a reading of what surrounds an opening may get. */
    static final long RESAMPLE_MILLIS = 5000L;

    /** Least time between two redraws of one viewer as they move. */
    static final long REDRAW_MILLIS = 100L;

    /** The same, in ticks, for the redraw that catches a viewer up after they stop. */
    static final long REDRAW_TICKS = 2L;

    /**
     * Least time between two redraws of a viewer at a small opening, drawn twice as often (#309).
     *
     * <p>A one or two block opening shows so little that a small head movement changes a large
     * share of what is visible, and it has only the one block of wall {@code mirror create}
     * requires to hide what the last redraw drew behind. Ten redraws a second left blocks that
     * had gone out of sight standing outside the opening for a redraw or two -- clearest as torch
     * flames and smoke hanging in the air beside a banner-wide mirror.
     */
    static final long SMALL_REDRAW_MILLIS = 50L;

    /**
     * An opening of this many blocks or fewer is a small one.
     *
     * <p>Six covers the openings a mirror one or two banners wide has: the sizes the lingering
     * was reported on, and the sizes cheap enough to draw at twice the pace.
     */
    static final int SMALL_OPEN = 6;

    /** How far in front of an opening real blocks are read for what they hide, and the least its wall is read. */
    static final int SURROUND = 8;

    /** The most of a block's outline that may land beside the opening for it to be drawn. */
    static final double MOST_BESIDE = 0.05;

    /** The same for a block already drawn, so a step does not take it away and the next give it back. */
    static final double KEPT_BESIDE = 0.15;

    /** How long a fixed view nobody looks through is kept. */
    private static final long FIXED_MILLIS = 60_000L;

    /** How far past a view's depth a changed block still counts as behind it: a door or a bed is two blocks. */
    private static final int CHANGE_SLACK = 2;

    /** Most blocks one fixed view may hold; past it the depth is cut until it fits. */
    private static final int MOST_FIXED = 250_000;

    /** The same, settable so a test can make a room not fit. */
    static int mostFixed = MOST_FIXED;

    /**
     * Most blocks one gate's view may hold (#516): four times a mirror's, some 50 MB while it is drawn.
     *
     * <p>A gate view is filled to 160 blocks through an opening up to eighteen square, and at a
     * mirror's cap open ground past a hundred or so was cut away, leaving this world showing
     * behind the far side. One view serves everybody looking into the gate.
     */
    private static final int MOST_GATE_FIXED = 1_000_000;

    /** The same, settable so a test can make a gate's view not fit. */
    static int mostGateFixed = MOST_GATE_FIXED;

    /**
     * Most block changes sent to one viewer in a tick; the rest of a redraw follows a tick apart.
     *
     * <p>A room at the render distance is some eighty thousand blocks, and sent as one batch as
     * a viewer came into range -- and taken back as one as they left -- the client re-meshed
     * every chunk section it touched before it drew another frame, a moment's freeze each way.
     * Rooms over 20,000 blocks were clipped to each eye instead, at a cost on every step. Sent a
     * tick's worth at a time, a chunk section at a time, each section is re-meshed once and no
     * frame waits for all of them.
     */
    private static final int STREAM_PER_TICK = 2500;

    /** The same, settable: a test's room goes in one batch unless the test is about the stream. */
    static int streamPerTick = STREAM_PER_TICK;

    /**
     * How far from the eye a clipped room is judged afresh on every redraw; past it, only once
     * the eye has left its cell ({@link WindowCompose#farCellFor}) or after {@link #FAR_MILLIS}.
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
    static final long FAR_MILLIS = 1000L;

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

    /** Blocks of held rooms one sweep redraws for viewers it has drawn before, by default. */
    private static final long ROOM_BLOCKS_PER_SWEEP = 250_000L;

    /**
     * The same, settable for a test.
     *
     * <p>A rebuilt room is swapped in under every viewer at once, and the next sweep redrew each of
     * them: ten viewers of a quarter-million-block room were most of a tenth of a second in one tick.
     * Once this many blocks of swapped-in rooms have been redrawn, the sweep redraws no more viewers
     * for a swap, and they keep what they see until the next: one viewer whose rooms alone are more
     * still gets its turn.
     */
    static long roomBlocksPerSweep = ROOM_BLOCKS_PER_SWEEP;

    /** What the time is; settable so a test can let a minute pass. */
    static LongSupplier clock = System::currentTimeMillis;

    /**
     * Told which windows a viewer is drawn after a redraw (#516).
     */
    @FunctionalInterface
    public interface DrawnListener
    {
        /**
         * @param viewer
         *            whose drawing it is
         * @param player
         *            that player, or null when their drawing went with them: gone, or into another world
         * @param windows
         *            the names of the windows they are drawn now; empty for none
         */
        void drawn(UUID viewer, Player player, Set<String> windows);
    }

    /** A window's far side drawn whole: the blocks, and the depth they reach from the opening. */
    record Whole(Map<Long, BlockData> blocks, int depth)
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

    /**
     * How old the capture is of a window the sweep holds.
     *
     * @param name
     *            the window's name
     * @return whole seconds, or -1 when no window of that name was found by the last sweep
     */
    public static long captureSecondsOld(final String name)
    {
        final WindowState window = ACTIVE.get(name);
        return (window == null) ? -1 : window.capture.secondsOld();
    }

    /**
     * What one redraw projected and drew.
     *
     * <p>Numbers, written out only when {@code mirror debug} asks: a sentence built on every redraw
     * was up to ten a second per viewer, for a command run once in a while.
     */
    record Redraw(int projected, int near, int fixed, int fixedDepth, Spot eye, long tookMillis)
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

    /**
     * The most blocks a window's view may hold before its depth is cut to fit.
     *
     * @return a gate's cap for a gate's window, a mirror's for a mirror's
     */
    static int mostFixedFor(final WindowState window)
    {
        return window.walkThrough() ? mostGateFixed : mostFixed;
    }

    /** Static state only. */
    private Windows()
    {
    }

    /** Forgets every view without sending anything, for a test or a reload. */
    public static void clear()
    {
        StandIns.removeEverything();
        FarChunkHolds.releaseAll();
        ACTIVE.clear();
        OFFERED.clear();
        VIEWS.clear();
        STATES.clear();
        WindowSight.clear();
        BLIND.clear();
        FULL.clear();
        Captures.clear();
        FixedBuilds.clear();
        FixedRegions.clear();
        CHANGED.clear();
        FIRST_CHANGED.clear();
        changes = 0L;
        clock = System::currentTimeMillis;
        workPerSecond = WORK_PER_SECOND;
        mostFixed = MOST_FIXED;
        mostGateFixed = MOST_GATE_FIXED;
        streamPerTick = STREAM_PER_TICK;
        nearDistance = NEAR_DISTANCE;
        restFactor = REST_FACTOR;
        workSecond = 0L;
        workSpent = 0;
        roomBlocksPerSweep = ROOM_BLOCKS_PER_SWEEP;
        sweepRoomBlocks = 0L;
        sweeps = 0L;
    }

    /**
     * Offers a window to the sweep in progress, drawn from the capture its source has in.
     *
     * <p>What was worked out about its wall and its room last sweep is kept while its shape is the same.
     *
     * @param source
     *            what is drawn as a window
     * @param capture
     *            the far side as held now, drawn until a fresh one arrives; never null
     */
    public static void offer(final WindowSource source, final Capture capture)
    {
        final String name = source.name();
        final WindowState window = new WindowState(source, capture);
        final WindowState previous = ACTIVE.get(name);
        if ((previous != null) && previous.shape.equals(window.shape))
        {
            final boolean sameFunnel = Objects.equals(previous.source.funnel(), source.funnel());
            window.solid = previous.solid;
            window.border = previous.border;
            window.margin = previous.margin;
            window.frame = previous.frame;
            window.solidAt = previous.solidAt;
            // Every window in a loaded chunk is offered each sweep, looked at or not: a fixed view
            // nobody has used for a while is let go rather than carried for the life of the chunk.
            // One from another capture or funnel is still drawn until its rebuild is whole (#609).
            if ((previous.fixed != null) && ((now() - previous.fixedUsedAt) < FIXED_MILLIS))
            {
                window.fixed = previous.fixed;
                window.fixedFrom = previous.fixedFrom;
                window.fixedAt = previous.fixedAt;
                window.fixedTook = previous.fixedTook;
                window.fixedWait = previous.fixedWait;
                window.fixedPartial = previous.fixedPartial;
                window.fixedPartialWait = previous.fixedPartialWait;
                window.fixedFor = previous.fixedFor;
                window.fixedDepth = previous.fixedDepth;
                window.fixedUsedAt = previous.fixedUsedAt;
                window.fixedChanges = previous.fixedChanges;
                window.fixedFunnel = previous.fixedFunnel;
            }
            if (FULL.containsValue(name) && sameFunnel)
            {
                window.full = previous.full;
                window.fullFrom = previous.fullFrom;
            }
        }
        OFFERED.put(name, window);
    }

    /**
     * The deepest of the views a viewer is being drawn: a gate's is its own, not a mirror's.
     *
     * @return blocks; {@code view-depth} for none
     */
    static int deepest(final List<WindowState> seeing)
    {
        return seeing.stream().mapToInt(WindowState::depth).max().orElse(ConfigManager.getViewDepth());
    }

    /**
     * Whether a window is being drawn for anybody: its capture is in and it was offered.
     *
     * @param name
     *            the name it was offered under
     * @return true if the sweep holds it as a window
     */
    public static boolean holdsWindow(final String name)
    {
        return ACTIVE.containsKey(name) || OFFERED.containsKey(name);
    }

    /**
     * Sets who is told which windows each viewer is drawn after a redraw.
     *
     * @param listener
     *            told once a redraw has settled, and as a drawing is forgotten; null for nobody
     */
    public static void onDrawn(final DrawnListener listener)
    {
        drawnListener = (listener == null) ? (viewer, player, windows) ->
        {
        } : listener;
    }

    /** Tells the listener what a viewer is drawn, without letting its failure stop the drawing. */
    static void tellDrawn(final UUID viewer, final Player player, final Set<String> windows)
    {
        try
        {
            drawnListener.drawn(viewer, player, windows);
        }
        catch (final Exception | LinkageError e)
        {
            final WormholeXTreme plugin = WormholeXTreme.getThisPlugin();
            if (plugin != null)
            {
                plugin.prettyLog(Level.WARNING, "Could not follow a viewer's windows", e);
            }
        }
    }

    /** Ends a sweep: the windows offered become the windows there are, and every view follows. */
    static void finish()
    {
        Captures.step(0);
        // Not while chunks are held: the grace of a gate that closed on its last viewer runs out here.
        if (OFFERED.isEmpty() && ACTIVE.isEmpty() && VIEWS.isEmpty() && !FarChunkHolds.holdsAny())
        {
            return;
        }
        final long now = now();
        sweepRoomBlocks = 0L;
        ACTIVE.clear();
        ACTIVE.putAll(OFFERED);
        OFFERED.clear();
        // A window not offered this sweep has nothing to build for, and no changes to hear about.
        FixedBuilds.retain(ACTIVE::containsKey);
        CHANGED.keySet().retainAll(ACTIVE.keySet());
        FIRST_CHANGED.keySet().retainAll(ACTIVE.keySet());
        FixedRegions.track(ACTIVE.values(), window -> (window.fixed != null) || FixedBuilds.building(window.name()));
        final Set<World> worlds = new LinkedHashSet<>();
        ACTIVE.values().forEach(window -> worlds.add(window.anchor().getWorld()));
        final Set<UUID> seen = new HashSet<>();
        sweeps++;
        for (final World world : worlds)
        {
            for (final Player player : inTurn(world.getPlayers()))
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
                    endView(id, null);
                }
                else
                {
                    update(player, player.getEyeLocation(), now, true);
                }
            }
        }
        StandIns.sweepStrays(VIEWS.values());
        WindowCreatures.holdFarAreas(now);
        trimStates(now);
        Captures.unloadIdle();
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
        if ((ACTIVE.isEmpty() && VIEWS.isEmpty()) || (player == null) || (to == null))
        {
            return;
        }
        final UUID id = player.getUniqueId();
        final ViewerDrawing view = VIEWS.get(id);
        if ((view == null) && !WindowViewers.nearAWindow(player, to))
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
            WindowPacing.catchUpLater(player, view);
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
        release(mirror.name());
    }

    /**
     * Takes one window out of every view it is in, by the name it was offered under: a mirror's,
     * or a gate's that has just closed.
     *
     * @param name
     *            the window no longer being drawn
     */
    public static void release(final String name)
    {
        OFFERED.remove(name);
        FixedBuilds.cancel(name);
        if (ACTIVE.remove(name) == null)
        {
            return;
        }
        final long now = now();
        for (final Map.Entry<UUID, ViewerDrawing> entry : new ArrayList<>(VIEWS.entrySet()))
        {
            // Stand-ins too: a first redraw that spawned them may have stopped before naming its windows.
            if (entry.getValue().mirrors.contains(name) || StandIns.shownThrough(entry.getValue(), name))
            {
                final Player player = Bukkit.getPlayer(entry.getKey());
                if (player == null)
                {
                    endView(entry.getKey(), null);
                }
                else
                {
                    update(player, player.getEyeLocation(), now, false);
                }
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
        if ((banner == null) || !ACTIVE.containsKey(mirror.name()))
        {
            return;
        }
        OFFERED.remove(mirror.name());
        if (!WindowSweep.offerMirror(mirror, banner))
        {
            return;
        }
        ACTIVE.put(mirror.name(), OFFERED.remove(mirror.name()));
        final long now = now();
        for (final Player player : banner.getWorld().getPlayers())
        {
            final ViewerDrawing view = VIEWS.get(player.getUniqueId());
            if ((view != null) && view.mirrors.contains(mirror.name()))
            {
                update(player, player.getEyeLocation(), now, false);
            }
        }
    }

    /**
     * A world's players, started a viewer further on each sweep, so the share of room redraws
     * ({@link #roomBlocksPerSweep}) reaches every one in turn.
     */
    private static List<Player> inTurn(final List<Player> players)
    {
        final List<Player> turned = new ArrayList<>(players);
        if (!turned.isEmpty())
        {
            Collections.rotate(turned, -(int) (sweeps % turned.size()));
        }
        return turned;
    }

    /** Takes every view back, as the plugin stops. */
    public static void restoreAll()
    {
        // First, so a send that throws below cannot leave a stand-in in the world.
        StandIns.removeEverything();
        final long now = now();
        for (final Map.Entry<UUID, ViewerDrawing> entry : VIEWS.entrySet())
        {
            final Player player = Bukkit.getPlayer(entry.getKey());
            if ((player != null) && player.getWorld().equals(entry.getValue().world))
            {
                // All of it now: no tick is coming to stream the rest.
                WindowStream.send(player, entry.getValue(), new HashMap<>(), player.getEyeLocation(), now, false, Set.of());
                WindowStream.stream(player, entry.getValue(), Integer.MAX_VALUE);
                WindowVeil.veil(player, entry.getValue(), List.of());
            }
        }
        clear();
    }

    /** Redraws one player's view from where their eye is, sending only what needs sending. */
    static void update(final Player player, final Location eye, final long now,
        final boolean fromSweep)
    {
        final UUID id = player.getUniqueId();
        ViewerDrawing view = viewInWorld(id, player);
        final List<WindowState> seeing = WindowViewers.seenBy(player, eye);
        if (view == null)
        {
            if (seeing.isEmpty())
            {
                tellDrawn(id, player, Set.of());
                return;
            }
            view = new ViewerDrawing(player.getWorld());
            VIEWS.put(id, view);
        }
        final long chunk = chunkOf(eye);
        final boolean crossed = view.chunk != chunk;
        seeing.forEach(window -> refreshSolid(window, now));
        // A viewer who starts inside the second's share finishes; later ones wait. Crossing into a
        // new chunk is never kept waiting, since the chunk arriving erases what was drawn.
        final boolean mayWork = (crossed && (view.chunk != Long.MIN_VALUE)) || !WindowPacing.overBudget(now);
        final WindowCompose.Wholes wholes = WindowCompose.wholesOf(seeing, now, mayWork, FULL.get(id));
        // Made only of windows drawn whole, the view is the same from wherever the eye is.
        final boolean eyeMatters = wholes.whole().size() < seeing.size();
        final String stamp = stampOf(seeing, wholes);
        if (!crossed && (fromSweep || !eyeMatters)
            && WindowPacing.unchanged(view, seeing, eyeMatters ? eyeKey(eye) : view.eye, stamp, now))
        {
            keepAsDrawn(player, view, eye, now, eyeMatters, fromSweep);
            // Kept as drawn, so judged against what is drawn: a creature moves, or walks in, while the viewer stands still.
            WindowCreatures.showStandIns(player, view, eye, seeing, wholes, now);
            // Still told: a gate's horizon may have cleared under a view that has not changed.
            tellDrawn(id, player, view.mirrors);
            return;
        }
        if (fromSweep && !crossed && !view.stamp.isEmpty() && !view.stamp.equals(stamp) && WindowPacing.roomShareSpent(wholes))
        {
            // Rooms enough were redrawn this sweep -- a rebuilt room swapped in under many viewers at
            // once -- so this viewer keeps what they see until the next (#609).
            WindowCreatures.followStandIns(view);
            tellDrawn(id, player, view.mirrors);
            return;
        }
        if (!mayWork)
        {
            // The server's share for this second is spent: this viewer keeps what they see a
            // moment longer, and a move is caught up once there is room.
            waitForRoom(player, view, eye, fromSweep);
            // Its rooms may not be held this time, so nothing is added or taken away on their word.
            WindowCreatures.followStandIns(view);
            return;
        }
        final List<Entity> inside = new ArrayList<>();
        final Budget budget = new Budget();
        final Map<Long, BlockData> wanted = WindowCompose.compose(view, eye, seeing, wholes, now, inside, budget);
        workSpent += budget.projected;
        WindowStream.send(player, view, wanted, eye, now, (now - view.fullAt) >= RESEND_MILLIS, WindowStream.freshChunks(player, view.chunk, chunk));
        WindowVeil.veil(player, view, inside);
        // After the send, so a stand-in's floor is judged by what the viewer has just been drawn.
        WindowCreatures.showStandIns(player, view, eye, seeing, wholes, now);
        final long took = now() - now;
        view.lastRedraw = new Redraw(budget.projected, budget.near, budget.fixed, budget.fixedDepth,
            new Spot((int) eye.getX(), (int) eye.getY(), (int) eye.getZ()), took);
        view.rest = WindowPacing.restAfter(took, WindowPacing.smallestOpen(seeing));
        view.stamp = stamp;
        view.mirrors = names(seeing);
        view.fixedNames = names(new ArrayList<>(wholes.whole().keySet()));
        view.eye = eyeKey(eye);
        view.chunk = chunk;
        view.generation = Captures.generation();
        // From when the redraw finished, not when it began, so a slow one still leaves a gap.
        view.composedAt = now();
        tellDrawn(id, player, view.mirrors);
        if (wanted.isEmpty() && view.pending.isEmpty())
        {
            endView(id, player);
        }
    }

    /**
     * A viewer's drawing, if it is of the world they are in. One of another world is ended: that
     * world's chunks have already replaced everything drawn in the old one, and its creatures are
     * all new to the client too.
     *
     * @return the drawing, or null for none
     */
    private static ViewerDrawing viewInWorld(final UUID id, final Player player)
    {
        final ViewerDrawing view = VIEWS.get(id);
        if ((view != null) && !view.world.equals(player.getWorld()))
        {
            endView(id, player);
            return null;
        }
        return view;
    }

    /** For a view that has not changed: keeps its eye current if it does not matter, and resends it when due. */
    private static void keepAsDrawn(final Player player, final ViewerDrawing view, final Location eye, final long now,
        final boolean eyeMatters, final boolean fromSweep)
    {
        if (!eyeMatters)
        {
            view.eye = eyeKey(eye);
        }
        if (fromSweep && ((now - view.fullAt) >= RESEND_MILLIS))
        {
            WindowStream.send(player, view, view.drawn, eye, now, true, Set.of());
        }
    }

    /** Leaves a viewer as drawn until there is work to spare, catching up a move later. */
    private static void waitForRoom(final Player player, final ViewerDrawing view, final Location eye,
        final boolean fromSweep)
    {
        if (!fromSweep)
        {
            view.pendingEye = eye;
            WindowPacing.catchUpLater(player, view);
        }
    }

    /**
     * Forgets a viewer's drawing.
     *
     * <p>Every ending goes through here. A view ends in four places -- the eye moving to a world
     * with no window in it, a redraw finding nothing left to draw, and either half of the stream
     * running out.
     *
     * @param id
     *            whose view it was
     * @param player
     *            that player, or null for one the server no longer has
     */
    static void endView(final UUID id, final Player player)
    {
        final ViewerDrawing view = VIEWS.remove(id);
        if (view != null)
        {
            StandIns.removeAll(view);
        }
        // In the same world the last redraw already said what they are drawn; elsewhere it is all gone.
        if ((player == null) || ((view != null) && !view.world.equals(player.getWorld())))
        {
            tellDrawn(id, null, Set.of());
        }
    }

    /**
     * Takes away the stand-ins one viewer is shown (#296), for one who has quit, died, respawned or
     * changed world; a viewer still looking is given them again on their next redraw.
     *
     * @param id
     *            the viewer
     */
    public static void dropStandIns(final UUID id)
    {
        final ViewerDrawing view = (id == null) ? null : VIEWS.get(id);
        if (view != null)
        {
            StandIns.removeAll(view);
        }
    }

    /**
     * Notes a block behind any window changing: placed, broken, blown up, pushed, burnt, flowed into
     * (#609). Each window whose view it lies inside is built again, a few milliseconds a tick, the
     * old view drawn meanwhile.
     *
     * <p>On every block change of every world, so a block nowhere near a held view is answered from
     * one map lookup by its chunk.
     *
     * @param world
     *            the block's world
     * @param x
     *            block x
     * @param y
     *            block y
     * @param z
     *            block z
     */
    public static void changed(final World world, final int x, final int y, final int z)
    {
        if (!FixedRegions.any())
        {
            return;
        }
        for (final String name : FixedRegions.at(world, x, z))
        {
            final WindowState window = ACTIVE.get(name);
            if ((window != null) && FixedView.insideFixed(window, x, y, z, FixedView.judgedDepth(window) + CHANGE_SLACK))
            {
                changes++;
                CHANGED.put(name, changes);
                FIRST_CHANGED.putIfAbsent(name, now());
            }
        }
    }

    /**
     * Whether a window's face is solid as far as the proximity distance on every side of its opening.
     *
     * <p>Then the wall hides whatever of the far side lies beside the opening, from anywhere a
     * viewer can be, and the far side can be drawn whole. A gap anywhere in that span shows it.
     */
    static boolean walled(final WindowState window)
    {
        return gapBeside(window) == null;
    }

    /** The first block of the face touching a window's opening that is not solid, or null if none. */
    static Spot gapBeside(final WindowState window)
    {
        // As far as the proximity distance: a viewer that far to one side looks at the space
        // behind the wall across the face that far out, and a block of open air there shows it.
        // One ring of wall was not enough -- a pillar two blocks wide in open air passed.
        final WindowShape shape = window.shape;
        final Set<Long> opening = new HashSet<>();
        shape.forEachOpening((x, y, z) -> opening.add(Cells.key(x, y, z)));
        final int reach = WindowFace.wallReach();
        final int[] span = WindowFace.acrossSpan(shape, reach);
        for (int across = span[0]; across <= span[1]; across++)
        {
            for (int y = shape.base().y() - reach; y <= (shape.base().y() + shape.height() + reach); y++)
            {
                final long face = WindowFace.faceKey(shape, across, y);
                if (!opening.contains(face) && !window.solid.contains(face))
                {
                    return new Spot(Cells.unpackX(face), Cells.unpackY(face), Cells.unpackZ(face));
                }
            }
        }
        return null;
    }

    /** What decides a view's fixed windows' content, so a change in any of them redraws it. */
    private static String stampOf(final List<WindowState> seeing, final WindowCompose.Wholes wholes)
    {
        final StringBuilder stamp = new StringBuilder();
        for (final WindowState window : seeing)
        {
            final Whole whole = wholes.of(window);
            // Where it opens onto too: a right-click changes that without changing anything else.
            stamp.append(window.name()).append('@').append(window.shape.far())
                .append(window.shape.mirrored() ? "~" : "").append('=')
                .append((whole == null) ? 0 : System.identityHashCode(whole.blocks())).append(';');
        }
        return stamp.toString();
    }

    /** @return true if any window holds a view a changed block could make stale: the block events' first question */
    public static boolean watchingAny()
    {
        return FixedRegions.any();
    }

    /** What one redraw did, across every window a viewer sees. */
    static final class Budget
    {
        /** Blocks of clipped rooms drawn for this eye. */
        int near;
        /** Blocks of rooms drawn whole, and the deepest they reach. */
        int fixed;
        int fixedDepth;
        /** Blocks of clipped rooms projected against the eye, which is what a redraw costs. */
        int projected;
    }

    /** A viewer's windows, nearest first, so a spent budget cuts the furthest views short. */
    static List<WindowState> nearestFirst(final List<WindowState> seeing, final Location eye)
    {
        final List<WindowState> sorted = new ArrayList<>(seeing);
        sorted.sort(Comparator
            .comparingDouble((WindowState window) -> fromBanner(window.anchor(),
                eye.getX(), eye.getY(), eye.getZ()))
            .thenComparing(WindowState::name));
        return sorted;
    }

    /** How far a point is from a banner block's corner, squared, without making a Location per ask. */
    static double fromBanner(final Block banner, final double x, final double y, final double z)
    {
        final double dx = x - banner.getX();
        final double dy = y - banner.getY();
        final double dz = z - banner.getZ();
        return (dx * dx) + (dy * dy) + (dz * dz);
    }

    /** Reads again which blocks around a window's opening are solid, if that reading is old. */
    private static void refreshSolid(final WindowState window, final long now)
    {
        if ((now - window.solidAt) < RESAMPLE_MILLIS)
        {
            return;
        }
        final WindowShape shape = window.shape;
        final World here = window.anchor().getWorld();
        final int reach = WindowFace.wallReach();
        final int[] span = WindowFace.acrossSpan(shape, reach);
        final Set<Long> solid = new HashSet<>();
        for (int across = span[0]; across <= span[1]; across++)
        {
            for (int y = shape.base().y() - reach;
                y <= (shape.base().y() + shape.height() + reach); y++)
            {
                final long face = WindowFace.faceKey(shape, across, y);
                if (here.getBlockAt(Cells.unpackX(face), y, Cells.unpackZ(face)).getBlockData().isOccluding())
                {
                    solid.add(face);
                }
            }
        }
        window.solid = solid;
        window.border = WindowFace.borderOf(shape, solid, reach);
        window.margin = WindowFace.marginOf(shape, solid, span, reach);
        window.frame = WindowFace.frameOf(shape, solid);
        window.solidAt = now;
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
    static List<Spot> openCells(final WindowShape shape, final World here)
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

    static Set<String> names(final List<WindowState> windows)
    {
        final Set<String> names = new HashSet<>();
        windows.forEach(window -> names.add(window.name()));
        return names;
    }

    /** @return the drawing's clock, which a test can set */
    static long now()
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
        return Cells.key((int) Math.floor(eye.getX() * 4.0), (int) Math.floor(eye.getY() * 4.0),
            (int) Math.floor(eye.getZ() * 4.0));
    }

    /** The chunk an eye is in, as a key. */
    private static long chunkOf(final Location eye)
    {
        return Cells.chunkKey(((int) Math.floor(eye.getX())) >> 4, ((int) Math.floor(eye.getZ())) >> 4);
    }

}
