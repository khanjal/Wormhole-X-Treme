package com.wormhole_xtreme.wormhole.model.mirror;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.scheduler.BukkitTask;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.utils.DataLayout;

/**
 * Every {@link MirrorCapture} the server has, and the taking of new ones.
 *
 * <p>Captures are keyed by destination rather than by mirror, so two mirrors onto the same place
 * share one, renaming a mirror changes nothing, and re-pointing one simply asks for a different
 * key. A capture is loaded from disk the first time a window wants it, and let go again when no
 * window has wanted it for a while.
 *
 * <p>Taking one reads the far side a couple of chunks a tick, from chunk snapshots, until the
 * box is covered -- a few seconds for the default box -- then blanks the far side's own mirror
 * banners, prunes what nothing could see, and writes the file off the main thread. Only the far
 * world needs to be loaded for that, and only then; the capture never needs it again.
 */
public final class MirrorCaptures
{
    /** Chunks read per tick while a capture is being taken. */
    private static final int CHUNKS_PER_TICK = 2;

    /**
     * Chunks a background capture reads a tick: a gate's fill past its first step, which nobody is
     * waiting on, and whose chunks are mostly read off the disk on the main thread.
     */
    private static final int BACKGROUND_CHUNKS_PER_TICK = 1;

    /** How long a capture nobody has looked at stays in memory. */
    private static final long IDLE_MILLIS = 300_000L;

    /** Blocks of margin a capture keeps beyond the view depth on every side. */
    private static final int MARGIN = 2;

    /**
     * The box a capture needs to hold, for a destination and a view depth.
     *
     * <p>Nothing outside a half-sphere of the depth ahead of the arrival point can be seen through
     * a window, since the depth is measured from the opening and a block behind the opening shows
     * the block the same way ahead of the arrival. So the box is that half-sphere's box: the depth
     * and a margin ahead, either side, up and down, and one layer behind. A box the capture radius
     * across in every direction was thirty-five times as much for the default depth, most of it
     * behind the arrival point where no window ever looked.
     *
     * @param destination
     *            where the mirror goes
     * @param depth
     *            how far ahead the view reaches
     * @param worldMin
     *            the far world's lowest y, or null if it is not loaded to ask
     * @param worldMax
     *            one above its highest, or null likewise
     * @return {@code {minX, minY, minZ, maxX, maxY, maxZ}}, inclusive
     */
    static int[] needed(final MirrorPoint destination, final int depth, final Integer worldMin,
        final Integer worldMax)
    {
        final MirrorWindow.Spot ahead = MirrorWindow.aheadOf(destination.yaw());
        final int arrivalX = (int) Math.floor(destination.x());
        final int arrivalY = (int) Math.floor(destination.y());
        final int arrivalZ = (int) Math.floor(destination.z());
        final int reach = depth + MARGIN;
        final int minX = arrivalX - ((ahead.x() > 0) ? 1 : reach);
        final int maxX = arrivalX + ((ahead.x() < 0) ? 1 : reach);
        final int minZ = arrivalZ - ((ahead.z() > 0) ? 1 : reach);
        final int maxZ = arrivalZ + ((ahead.z() < 0) ? 1 : reach);
        final int minY = (worldMin == null) ? (arrivalY - reach) : Math.max(worldMin, arrivalY - reach);
        final int maxY = (worldMax == null) ? (arrivalY + reach) : Math.min(worldMax - 1, arrivalY + reach);
        return new int[] { minX, minY, minZ, maxX, maxY, maxZ };
    }

    /** The furthest a capture reaches ahead of its arrival point: ten chunks, as far as a server usually sends. */
    static final int MOST_REACH = 160;

    /**
     * Most blocks that are not air a capture may keep; past it, the reach is cut until it fits.
     *
     * <p>A room of hills and trees at ten chunks keeps a few hundred thousand. A jungle or an
     * ocean bed, seen through leaves or water, could keep millions: tens of megabytes on disk,
     * as much again in memory while a window draws from it, and a walk over all of it every
     * minute to hold the room. Half a million is about five megabytes raw and a second's walk.
     */
    static final int MOST_KEPT = 500_000;

    /**
     * How far ahead of the arrival point a capture of a world is taken.
     *
     * <p>As far as that world's server sends -- its view distance, in blocks -- and never short
     * of the view depth, so a capture always holds what a view draws. A capture used to be taken
     * to the view depth and no further, which tied the two together the wrong way round: lowering
     * {@code mirror-view-depth} to make a mirror cheaper cut every capture to match, and raising
     * it again meant taking every one again, loading each room's world for a few seconds. The
     * reach is the capture's own now, and the depth says how much of it a view draws
     * ({@code MirrorWindows.fixedTo}), so the depth can change without a capture being touched.
     * Never past {@link #MOST_REACH}: a box that size is what the bits of a capture being taken
     * are sized for, and past ten chunks a client has nothing to show anyway.
     *
     * @param far
     *            the far world, or null if it is not loaded to ask; then the view depth, which any
     *            capture taken by this rule holds
     * @return the reach, in blocks
     */
    /**
     * How far a gate's view is filled in behind its first step (#516): {@code gate-view-full-depth},
     * never past what the far world's server sends, and never short of the first step.
     *
     * <p>Past the server's send distance a drawn block lands in a chunk the client does not hold,
     * and is never seen, so a capture deeper than that is disk and memory for nothing. The same rule
     * a mirror's capture follows ({@link #reach}).
     *
     * @param arrival
     *            where travellers through the gate land, in the far world
     * @param first
     *            the first step's depth, {@code gate-view-depth}
     * @return blocks
     */
    public static int gateFillDepth(final MirrorPoint arrival, final int first)
    {
        final int full = ConfigManager.getGateViewFullDepth();
        if (full <= first)
        {
            return first;
        }
        final World far = (Bukkit.getServer() == null) ? null : Bukkit.getWorld(arrival.worldName());
        final int sends = (far == null) ? full : (far.getViewDistance() * 16);
        return Math.max(first, Math.min(full, sends));
    }

    static int reach(final World far)
    {
        final int depth = ConfigManager.getMirrorViewDepth();
        if (far == null)
        {
            return depth;
        }
        return Math.max(depth, Math.min(MOST_REACH, far.getViewDistance() * 16));
    }

    /** Reads one chunk of a world, so a test can hand in chunks without a server. */
    @FunctionalInterface
    interface ChunkReader
    {
        /**
         * @param chunkX
         *            chunk x, which is block x shifted right four
         * @param chunkZ
         *            chunk z, which is block z shifted right four
         * @return a snapshot of it, loading it if it must
         */
        ChunkSnapshot read(World world, int chunkX, int chunkZ);
    }

    /** Works out what a capture keeps between its passes, so a test can make it fail. */
    @FunctionalInterface
    interface Sifter
    {
        /** @return how far the capture ended up seeing */
        int sift(MirrorCapture.Builder builder, MirrorCapture.Arrival from, int reach, int floor);
    }

    private static final Sifter SIFT = (builder, from, reach, floor) ->
    {
        final int kept = builder.keepOnlySeenWithin(from, reach, floor, MOST_KEPT);
        builder.prune();
        return kept;
    };

    /** Captures in memory, by destination key. */
    private static final Map<String, Held> LOADED = new HashMap<>();

    /** Keys whose file was tried and found missing or unreadable, so they are not tried again. */
    private static final Set<String> ABSENT = new HashSet<>();

    /** Captures being taken, by destination key. */
    private static final Map<String, Job> JOBS = new HashMap<>();

    /** Keys already warned about, so an unloaded far world is said once and not every sweep. */
    private static final Set<String> WARNED = new HashSet<>();

    /** Keys whose capture failed and was said so, so one failing every time is said once. */
    private static final Set<String> FAILED = new HashSet<>();

    /** Bumped whenever a capture arrives or changes, so every view knows to look again. */
    private static int generation;

    private static ChunkReader reader = (world, chunkX, chunkZ) ->
        world.getChunkAt(chunkX, chunkZ).getChunkSnapshot();

    private static Sifter sifter = SIFT;

    /** A capture in memory and when it was last wanted. */
    private static final class Held
    {
        private final MirrorCapture capture;
        private long usedAt;

        Held(final MirrorCapture capture, final long usedAt)
        {
            this.capture = capture;
            this.usedAt = usedAt;
        }
    }

    /** Static registry only. */
    private MirrorCaptures()
    {
    }

    /**
     * The key a mirror's destination captures under.
     *
     * @param destination
     *            where the mirror goes
     * @return a file-safe name for that place
     */
    static String keyOf(final MirrorPoint destination)
    {
        final String world = fileSafe(destination.worldName());
        return world + '_' + (int) Math.floor(destination.x()) + '_'
            + (int) Math.floor(destination.y()) + '_' + (int) Math.floor(destination.z());
    }

    /** The hole a mirror's capture is seen through: three wide, so either width of mirror is served, and two tall. */
    static final int MIRROR_HOLE_WIDTH = 3;
    static final int MIRROR_HOLE_HEIGHT = 2;

    /** Before the key of a capture kept for a gate, whose file lives with the gates (#516). */
    static final String GATE_KEY = "gate:";

    /**
     * The key a gate's capture is kept under: the gate whose front it shows, and the hole it is seen through.
     *
     * <p>Named for the gate rather than the place, so it is found again after a restart and can go
     * when the gate does. The hole is part of it because a capture holds only what its own hole
     * lets through: a small gate's served to a big one left the big one's view with holes round
     * its edges, and keyed by place alone a mirror's retake replaced a gate's.
     *
     * @param gate
     *            the gate whose front it shows
     * @param holeWidth
     *            the opening it is seen through, wide
     * @param holeHeight
     *            and tall
     * @return a key whose file is in {@link DataLayout#gateCaptureDir()}
     */
    public static String gateKey(final String gate, final int holeWidth, final int holeHeight)
    {
        return GATE_KEY + gateStem(gate) + '_' + holeWidth + 'x' + holeHeight;
    }

    /**
     * A gate's name as its capture files begin: made file-safe, with a hash of the name as typed.
     *
     * <p>File-safe alone, every character that is not a plain letter or digit became an underscore,
     * so "a b" and "a_b", or any two names in another alphabet, shared one file and each drew the
     * other's far side. Gate names are told apart without case, so the hash is of the lower case.
     */
    static String gateStem(final String gate)
    {
        final String lower = gate.toLowerCase(Locale.ROOT);
        return fileSafe(lower) + '-' + Integer.toHexString(lower.hashCode());
    }

    /** A name as a file may be called. */
    static String fileSafe(final String name)
    {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
    }

    /** What follows a gate's name in its capture's key: the hole, as {@code _<width>x<height>}. */
    private static final Pattern GATE_HOLE = Pattern.compile("_(\\d+)x(\\d+)$");

    /**
     * Every capture kept for a gate, whatever opening it was seen through: on disk, and in memory.
     *
     * @param gate
     *            the gate whose front they show
     * @return their keys
     */
    static Set<String> gateKeysFor(final String gate)
    {
        final String stem = gateStem(gate);
        final String[] files = DataLayout.gateCaptureDir().list((dir, name) -> name.endsWith(VIEW));
        final Set<String> keys = Arrays.stream((files == null) ? new String[0] : files)
            .map(name -> GATE_KEY + name.substring(0, name.length() - VIEW.length()))
            .collect(Collectors.toCollection(HashSet::new));
        keys.addAll(LOADED.keySet());
        // And one still being taken, which has neither yet: a first capture removed mid-way wrote its file anyway.
        keys.addAll(JOBS.keySet());
        keys.removeIf(key -> !key.startsWith(GATE_KEY + stem + '_') || !GATE_HOLE.matcher(key)
            .region(GATE_KEY.length() + stem.length(), key.length()).matches());
        return keys;
    }

    /**
     * Whether a capture reaches as far ahead of its arrival as a view now draws.
     *
     * @param depth
     *            how far ahead the view draws
     * @return true if the block that far ahead is inside it
     */
    static boolean reaches(final MirrorCapture capture, final MirrorPoint arrival, final int depth)
    {
        final MirrorWindow.Spot ahead = MirrorWindow.aheadOf(arrival.yaw());
        return capture.contains((int) Math.floor(arrival.x()) + (ahead.x() * depth), (int) Math.floor(arrival.y()),
            (int) Math.floor(arrival.z()) + (ahead.z() * depth));
    }

    /**
     * Retakes a gate's captures that are older than a limit or too shallow for the depth, whichever
     * opening each was seen through, while somebody is at the gate and its chunks are loaded anyway.
     *
     * <p>A capture is the base a gate is drawn from until it is taken again; this is the "when
     * able". The old one is drawn meanwhile.
     *
     * @param gate
     *            the gate whose front they show
     * @param arrival
     *            where a traveller through it lands
     * @param depth
     *            how far ahead a gate's view draws
     * @param olderThanSeconds
     *            how old a capture may be before it is retaken
     * @return how many were started
     */
    public static int refreshGate(final String gate, final MirrorPoint arrival, final int depth,
        final long olderThanSeconds)
    {
        int started = 0;
        for (final String key : gateKeysFor(gate))
        {
            final Matcher hole = GATE_HOLE.matcher(key);
            if (hole.find() && stale(key, arrival, depth, olderThanSeconds) && !JOBS.containsKey(key)
                && requestGate(key, gate, arrival, Integer.parseInt(hole.group(1)), Integer.parseInt(hole.group(2)), depth))
            {
                started++;
            }
        }
        return started;
    }

    /**
     * Whether a gate's capture should be taken again: older than the limit, or, if it is in memory,
     * shallower than the depth.
     *
     * <p>One not in memory is judged by its file's age, not loaded to be judged: a watched gate's
     * captures were read off the disk every minute to learn how old they were, and being asked for
     * kept them from ever being let go. Its depth is judged when a gate is next drawn from it.
     */
    private static boolean stale(final String key, final MirrorPoint arrival, final int depth, final long olderThanSeconds)
    {
        final Held held = LOADED.get(key);
        if (held != null)
        {
            return (held.capture.secondsOld() > olderThanSeconds) || !reaches(held.capture, arrival, depth);
        }
        final File file = fileOf(key);
        return file.isFile() && (((System.currentTimeMillis() - file.lastModified()) / 1000L) > olderThanSeconds);
    }

    /**
     * Forgets and deletes every capture of a gate being removed, whatever opening each was seen through.
     *
     * <p>At removal rather than in a sweep at startup, which counted a gate missing whenever it had
     * not loaded -- a gate in a world another plugin loads later -- and deleted what it showed. A
     * refresh that hands a gate back is not a removal, and keeps them.
     *
     * @param gate
     *            the gate whose front they show
     * @return how many files were deleted
     */
    public static int forgetGate(final String gate)
    {
        int deleted = 0;
        for (final String key : gateKeysFor(gate))
        {
            final Job job = JOBS.remove(key);
            if (job != null)
            {
                job.done = true;
                job.cancel();
            }
            LOADED.remove(key);
            ABSENT.add(key);
            // As a mirror's forget does, so a gate built again under the name is warned about afresh.
            WARNED.remove(key);
            FAILED.remove(key);
            final File file = fileOf(key);
            try
            {
                if (Files.deleteIfExists(file.toPath()))
                {
                    deleted++;
                }
            }
            catch (final IOException refused)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING,
                    "Could not delete the capture " + file.getName() + " of a removed gate", refused);
            }
        }
        changed();
        return deleted;
    }

    /**
     * The key a mirror's room is captured under, which is its capture file's name.
     *
     * @return the key, or null if the mirror has no room
     */
    public static String keyFor(final QuantumMirror mirror)
    {
        return (mirror.destination() == null) ? null : keyOf(mirror.destination());
    }

    /**
     * Deletes every capture file whose place no mirror's room is.
     *
     * <p>A capture goes with its mirror unless another mirror still uses the room, but a mirror file
     * edited or emptied by hand, or a delete that failed, left one behind for good. Run only once
     * mirrors have loaded: before, every capture is abandoned.
     *
     * @return how many were deleted
     */
    public static int sweepAbandoned()
    {
        final File[] files = DataLayout.mirrorCaptureDir().listFiles((dir, name) -> name.endsWith(VIEW));
        if (files == null)
        {
            return 0;
        }
        final Set<String> used = new HashSet<>();
        MirrorManager.all().stream().map(MirrorCaptures::keyFor).forEach(used::add);
        int deleted = 0;
        for (final File file : files)
        {
            final String key = file.getName().substring(0, file.getName().length() - VIEW.length());
            if (used.contains(key))
            {
                continue;
            }
            try
            {
                Files.delete(file.toPath());
                deleted++;
            }
            catch (final IOException refused)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING,
                    "Could not delete abandoned mirror capture " + file.getName(), refused);
            }
        }
        return deleted;
    }

    /** A capture file's extension. */
    private static final String VIEW = ".view";

    /** What the lines about a capture are headed. */
    private static final String CAPTURE = "capture";

    /**
     * The capture for a mirror's far side, if there is one.
     *
     * <p>Loaded from disk the first time. A missing or unreadable file is remembered as missing,
     * so a mirror with no capture costs one failed open rather than one per sweep.
     *
     * @param mirror
     *            a mirror with somewhere to go
     * @return its capture, or null if none has been taken yet
     */
    static MirrorCapture get(final QuantumMirror mirror)
    {
        return get(keyOf(mirror.destination()));
    }

    /**
     * The capture kept under a key, if there is one: a mirror's place, or a {@link #gateKey}.
     *
     * @return the capture, or null if none has been taken yet
     */
    static MirrorCapture get(final String key)
    {
        final long now = System.currentTimeMillis();
        final Held held = LOADED.get(key);
        if (held != null)
        {
            held.usedAt = now;
            return held.capture;
        }
        if (ABSENT.contains(key))
        {
            return null;
        }
        final File file = fileOf(key);
        if (!file.isFile())
        {
            ABSENT.add(key);
            return null;
        }
        try
        {
            final MirrorCapture capture = MirrorCapture.load(file);
            LOADED.put(key, new Held(capture, now));
            changed();
            return capture;
        }
        catch (final IOException unreadable)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING,
                "Could not read mirror capture " + file.getName(), unreadable);
            ABSENT.add(key);
            return null;
        }
    }


    /**
     * Whether a capture is smaller than one taken now would be, so it should be taken again.
     *
     * <p>The box grew twice while this was being tested, and a file from before either kept
     * its old horizon until somebody thought to run {@code mirror stamp}. Depth is judged only
     * while the far world is loaded, since its floor is part of the answer and a capture that
     * cannot be retaken anyway should not be asked for every sweep. A file of an earlier kind
     * does not load at all, and the mirror asks for a fresh one the same way.
     *
     * @param capture
     *            its capture
     * @return true if a capture taken now would reach further, or know more
     */
    static boolean outgrown(final QuantumMirror mirror, final MirrorCapture capture)
    {
        final MirrorPoint destination = mirror.destination();
        final World far = Bukkit.getWorld(destination.worldName());
        final int[] box = needed(destination, reach(far),
            (far == null) ? null : far.getMinHeight(), (far == null) ? null : far.getMaxHeight());
        final int arrivalX = (int) Math.floor(destination.x());
        final int arrivalY = (int) Math.floor(destination.y());
        final int arrivalZ = (int) Math.floor(destination.z());
        if (!capture.contains(box[0], arrivalY, box[2]) || !capture.contains(box[3], arrivalY, box[5]))
        {
            return true;
        }
        return (far != null)
            && (!capture.contains(arrivalX, box[1], arrivalZ) || !capture.contains(arrivalX, box[4], arrivalZ));
    }

    /**
     * Starts taking a mirror's capture, if the far world is loaded and none is being taken.
     *
     * @param mirror
     *            a mirror with somewhere to go
     * @return true if a capture is now being taken, or already was
     */
    public static boolean request(final QuantumMirror mirror)
    {
        return (mirror.destination() != null) && request(keyOf(mirror.destination()), "mirror '" + mirror.name() + "'",
            mirror.destination(), new int[] { MIRROR_HOLE_WIDTH, MIRROR_HOLE_HEIGHT }, 0, false);
    }

    /**
     * Starts taking a gate's capture, if the gate's world is loaded and none is being taken (#516).
     *
     * @param key
     *            its {@link #gateKey}
     * @param gate
     *            the gate whose front it shows, for the log
     * @param arrival
     *            where a traveller through it lands, facing the way they leave
     * @param holeWidth
     *            the opening it is seen through, wide
     * @param holeHeight
     *            and tall
     * @param depth
     *            how far past the opening it reaches: {@code gate-view-depth} for the first step, and
     *            anything deeper is the background fill to {@code gate-view-full-depth}, taken at half the pace
     * @return true if a capture is now being taken, or already was
     */
    public static boolean requestGate(final String key, final String gate, final MirrorPoint arrival,
        final int holeWidth, final int holeHeight, final int depth)
    {
        final boolean fill = depth > ConfigManager.getGateViewDepth();
        return request(key, "gate '" + gate + "'" + (fill ? " out to " + depth : ""), arrival,
            new int[] { holeWidth, holeHeight }, Math.max(4, depth), fill);
    }

    /**
     * Starts taking a capture, if the far world is loaded and none is being taken under its key.
     *
     * @param what
     *            what it is for, for the log
     * @param hole
     *            {@code {width, height}} of the opening it is seen through
     * @param depth
     *            how far ahead it reaches, or 0 for a mirror's: as far as the far world sends
     * @param background
     *            true for a capture nobody is waiting on, read at half the pace
     */
    private static boolean request(final String key, final String what, final MirrorPoint destination,
        final int[] hole, final int depth, final boolean background)
    {
        if (JOBS.containsKey(key))
        {
            return true;
        }
        final World far = Bukkit.getWorld(destination.worldName());
        if (far == null)
        {
            if (WARNED.add(key))
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "Cannot capture the far side"
                    + " of " + what + ": " + destination.worldName() + " is not loaded. It "
                    + (key.startsWith(GATE_KEY) ? "keeps its horizon" : "stays a banner")
                    + " until that world is loaded once.");
            }
            return false;
        }
        WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, "Capturing the far side of " + what + " in "
            + far.getName() + " around " + key);
        final int reach = (depth > 0) ? depth : reach(far);
        final int floor = (depth > 0) ? depth : ConfigManager.getMirrorViewDepth();
        final Job job = new Job(key, far, destination, hole, new int[] { reach, floor });
        job.perTick = background ? BACKGROUND_CHUNKS_PER_TICK : CHUNKS_PER_TICK;
        JOBS.put(key, job);
        job.schedule();
        return true;
    }

    /**
     * Takes a mirror's capture again, keeping the old one until the new one is ready.
     *
     * @return true if it is being taken
     */
    public static boolean retake(final QuantumMirror mirror)
    {
        if (mirror.destination() != null)
        {
            // Whoever asked hears if it fails again.
            FAILED.remove(keyOf(mirror.destination()));
        }
        return request(mirror);
    }

    /**
     * Forgets a mirror's capture, unless another mirror still looks at the same place.
     *
     * @param mirror
     *            the mirror being removed or re-pointed
     */
    public static void forget(final QuantumMirror mirror)
    {
        if (mirror.destination() == null)
        {
            return;
        }
        final String key = keyOf(mirror.destination());
        for (final QuantumMirror other : MirrorManager.all())
        {
            if ((other.destination() != null) && !other.name().equalsIgnoreCase(mirror.name())
                && keyOf(other.destination()).equals(key))
            {
                return;
            }
        }
        final Job job = JOBS.remove(key);
        if (job != null)
        {
            job.cancel();
        }
        LOADED.remove(key);
        ABSENT.remove(key);
        FAILED.remove(key);
        final File file = fileOf(key);
        if (file.isFile())
        {
            try
            {
                Files.delete(file.toPath());
            }
            catch (final IOException refused)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING,
                    "Could not delete mirror capture " + file.getName(), refused);
            }
        }
        changed();
    }

    /**
     * Advances every capture being taken, for a server with no scheduler and for tests.
     *
     * @param chunks
     *            how many chunks to read across all of them
     */
    static void step(final int chunks)
    {
        int left = chunks;
        for (final Job job : new ArrayList<>(JOBS.values()))
        {
            while ((left > 0) && !job.done)
            {
                job.step();
                left--;
            }
        }
    }

    /** Lets go of captures nobody has looked at for a while. */
    static void unloadIdle()
    {
        final long now = System.currentTimeMillis();
        LOADED.entrySet().removeIf(entry -> (now - entry.getValue().usedAt) > IDLE_MILLIS);
    }

    /** @return a number that changes whenever any capture does */
    static int generation()
    {
        return generation;
    }

    /** Notes that a capture arrived or changed, so every view knows to look again. */
    private static void changed()
    {
        generation++;
    }

    /** @return how many captures are being taken right now */
    static int taking()
    {
        return JOBS.size();
    }

    /** Forgets everything in memory, for a test or a reload. Files stay. */
    public static void clear()
    {
        JOBS.values().forEach(Job::cancel);
        JOBS.clear();
        LOADED.clear();
        ABSENT.clear();
        WARNED.clear();
        FAILED.clear();
        changed();
    }

    /**
     * What a mirror's capture is, for {@code mirror debug}.
     *
     * @return lines to say
     */
    public static List<String> describe(final QuantumMirror mirror)
    {
        final List<String> lines = new ArrayList<>();
        lines.add(MirrorText.heading(CAPTURE));
        if (mirror.destination() == null)
        {
            lines.add(MirrorText.field("room", MirrorText.bad("none, so no capture")));
            return lines;
        }
        final String key = keyOf(mirror.destination());
        final File file = fileOf(key);
        lines.add(MirrorText.field("key", key));
        lines.add(MirrorText.field("file", (file.isFile() ? (file.length() + " bytes") : MirrorText.bad("missing"))
            + (JOBS.containsKey(key) ? ", being taken now" : "")));
        lines.add(MirrorText.field("room's world",
            (Bukkit.getWorld(mirror.destination().worldName()) == null) ? "not loaded" : "loaded"));
        final Held held = LOADED.get(key);
        if (held == null)
        {
            lines.add(MirrorText.field("in memory",
                ABSENT.contains(key) ? MirrorText.bad("no, and its file was found missing") : "no"));
            return lines;
        }
        final MirrorCapture capture = held.capture;
        lines.addAll(capture.describeLines());
        final int x = (int) Math.floor(mirror.destination().x());
        final int y = (int) Math.floor(mirror.destination().y());
        final int z = (int) Math.floor(mirror.destination().z());
        lines.add(MirrorText.field("at arrival", capture.nameAt(x, y, z) + ", below it "
            + capture.nameAt(x, y - 1, z) + ", column top y " + capture.top(x, z)));
        final MirrorWindow.Spot ahead = MirrorWindow.aheadOf(mirror.destination().yaw());
        lines.add(MirrorText.field("ahead", ahead.x() + "," + ahead.z() + " (yaw " + mirror.destination().yaw() + ")"));
        for (final int far : new int[] { 8, 32 })
        {
            lines.add(MirrorText.field(far + " ahead", capture.nameAt(x + (far * ahead.x()), y, z + (far * ahead.z()))
                + ", top y " + capture.top(x + (far * ahead.x()), z + (far * ahead.z()))));
        }
        return lines;
    }

    /**
     * What a mirror's capture is, on one line, for {@code mirror debug} without {@code all}.
     *
     * @return the line
     */
    public static String summary(final QuantumMirror mirror)
    {
        if (mirror.destination() == null)
        {
            return MirrorText.field(CAPTURE, MirrorText.bad("none, the mirror has no room"));
        }
        final String key = keyOf(mirror.destination());
        final File file = fileOf(key);
        final Held held = LOADED.get(key);
        return MirrorText.field(CAPTURE, (file.isFile() ? (file.length() + " bytes") : MirrorText.bad("file missing"))
            + ", " + ((held == null) ? "not in memory"
                : ("in memory, " + held.capture.filled() + " blocks, taken " + held.capture.secondsOld() + "s ago"))
            + (JOBS.containsKey(key) ? ", being taken now" : ""));
    }

    /** Reads chunks another way, for a test. */
    static void readChunksWith(final ChunkReader other)
    {
        reader = other;
    }

    /** Sifts captures another way, for a test; null for the usual way. */
    static void siftWith(final Sifter other)
    {
        sifter = (other == null) ? SIFT : other;
    }

    /**
     * Puts a capture in memory as though it had been taken, for a test.
     *
     * @param destination
     *            the place it is of
     */
    static void install(final MirrorPoint destination, final MirrorCapture capture)
    {
        install(keyOf(destination), capture);
    }

    /**
     * Puts a capture in memory under a key as though it had been taken, for a test.
     *
     * @param key
     *            a mirror's place or a {@link #gateKey}
     */
    static void install(final String key, final MirrorCapture capture)
    {
        LOADED.put(key, new Held(capture, System.currentTimeMillis()));
        ABSENT.remove(key);
        changed();
    }

    private static File fileOf(final String key)
    {
        return key.startsWith(GATE_KEY) ? new File(DataLayout.gateCaptureDir(), key.substring(GATE_KEY.length()) + VIEW)
            : new File(DataLayout.mirrorCaptureDir(), key + VIEW);
    }

    /**
     * One capture being taken, a couple of chunks a tick.
     *
     * <p>In two passes over the far world's chunks. The first notes only what kind of block
     * stands where -- air, see-through, or solid -- a bit each, since the box at the render
     * distance is too big to hold every block's state. Off the main thread between them, the
     * builder works out what somebody at the opening could see. The second pass records the
     * state of those blocks alone.
     */
    private static final class Job implements Runnable
    {
        private final String key;
        private final World far;
        private final MirrorPoint destination;
        private final MirrorCapture.Builder builder;
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;
        private final List<int[]> chunks = new ArrayList<>();
        private int next;
        private boolean noting = true;
        private boolean sifting;
        private boolean done;
        private BukkitTask task;
        /** How far the capture was asked to see, and how far it saw once cut to fit {@link #MOST_KEPT}. */
        private int reachAsked;
        private volatile int reachKept;
        /** The hole the capture is seen through. */
        private final int holeWidth;
        private final int holeHeight;
        /** How far ahead it is taken, and the least a cut to fit may leave. */
        private final int reach;
        /** Chunks read a tick. */
        private int perTick = CHUNKS_PER_TICK;
        private final int floor;

        /**
         * @param hole
         *            {@code {width, height}} of the opening it is seen through
         * @param depths
         *            {@code {reach, floor}}: how far ahead it is taken, and the least a cut to fit may leave
         */
        Job(final String key, final World far, final MirrorPoint destination, final int[] hole, final int[] depths)
        {
            this.key = key;
            this.far = far;
            this.destination = destination;
            this.holeWidth = hole[0];
            this.holeHeight = hole[1];
            this.reach = depths[0];
            this.floor = depths[1];
            final int[] box = needed(destination, reach, far.getMinHeight(), far.getMaxHeight());
            minX = box[0];
            minY = box[1];
            minZ = box[2];
            maxX = box[3];
            maxY = box[4];
            maxZ = box[5];
            builder = new MirrorCapture.Builder(far.getName(),
                far.getEnvironment() == World.Environment.NORMAL,
                new MirrorCapture.Box(minX, minY, minZ, (maxX - minX) + 1, (maxY - minY) + 1, (maxZ - minZ) + 1),
                Bukkit.createBlockData(Material.AIR));
            for (int chunkX = minX >> 4; chunkX <= (maxX >> 4); chunkX++)
            {
                for (int chunkZ = minZ >> 4; chunkZ <= (maxZ >> 4); chunkZ++)
                {
                    chunks.add(new int[] { chunkX, chunkZ });
                }
            }
        }

        /** Runs a couple of chunks a tick, where there is a scheduler to run on. */
        void schedule()
        {
            try
            {
                task = WormholeXTreme.getScheduler().runTaskTimer(WormholeXTreme.getThisPlugin(),
                    this, 1L, 1L);
            }
            catch (final RuntimeException noScheduler)
            {
                // Startup, or a test: stepped by hand instead.
                task = null;
            }
        }

        @Override
        public void run()
        {
            for (int i = 0; (i < perTick) && !done && !sifting; i++)
            {
                step();
            }
        }

        /** Reads one chunk, and moves the capture on after the last of a pass. */
        void step()
        {
            if (done || sifting)
            {
                return;
            }
            if (next < chunks.size())
            {
                final int[] chunk = chunks.get(next++);
                try
                {
                    copy(reader.read(far, chunk[0], chunk[1]), chunk[0] << 4, chunk[1] << 4);
                }
                catch (final RuntimeException failed)
                {
                    WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "Could not read chunk "
                        + chunk[0] + "," + chunk[1] + " of " + far.getName() + " for a mirror capture",
                        failed);
                }
            }
            if (next >= chunks.size())
            {
                if (noting)
                {
                    sift();
                }
                else
                {
                    finish();
                }
            }
        }

        /** Copies the chunk's columns that fall inside the box. */
        private void copy(final ChunkSnapshot snapshot, final int baseX, final int baseZ)
        {
            final int lastX = Math.min(15, maxX - baseX);
            final int lastZ = Math.min(15, maxZ - baseZ);
            for (int lx = Math.max(0, minX - baseX); lx <= lastX; lx++)
            {
                for (int lz = Math.max(0, minZ - baseZ); lz <= lastZ; lz++)
                {
                    copyColumn(snapshot, baseX + lx, baseZ + lz, lx, lz);
                }
            }
        }

        private void copyColumn(final ChunkSnapshot snapshot, final int x, final int z, final int lx, final int lz)
        {
            // Nothing above the column's highest block but air, which needs no writing.
            final int top = Math.min(maxY, highest(far, snapshot, x, z, lx, lz));
            for (int y = minY; y <= top; y++)
            {
                if (noting)
                {
                    final BlockData data = snapshot.getBlockData(lx, y, lz);
                    if (!isAir(data))
                    {
                        builder.note(x, y, z, data);
                    }
                }
                else if (builder.wanted(x, y, z))
                {
                    builder.put(x, y, z, snapshot.getBlockData(lx, y, lz));
                }
            }
        }

        /** Between the passes: works out what can be seen, off the main thread, then reads again. */
        private void sift()
        {
            noting = false;
            sifting = true;
            // A linked pair arrives in the far banner's own block, so it would otherwise hang
            // in the middle of the view.
            for (final QuantumMirror other : MirrorManager.all())
            {
                for (final MirrorBlock banner : other.banners())
                {
                    if (banner.worldName().equals(far.getName()))
                    {
                        builder.clear(banner.x(), banner.y(), banner.z());
                    }
                }
            }
            final MirrorWindow.Spot ahead = MirrorWindow.aheadOf(destination.yaw());
            final MirrorCapture.Arrival arrival = new MirrorCapture.Arrival((int) Math.floor(destination.x()),
                (int) Math.floor(destination.y()), (int) Math.floor(destination.z()), ahead.x(), ahead.z(),
                holeWidth, holeHeight);
            final int depth = reach;
            reachAsked = depth;
            reachKept = depth;
            // A third of a million rays: off the main thread, since the box is noted and
            // nothing here reads the world again.
            final Runnable work = () -> reachKept = sifter.sift(builder, arrival, depth, floor);
            try
            {
                WormholeXTreme.getScheduler().runTaskAsynchronously(WormholeXTreme.getThisPlugin(),
                    () -> siftThen(work,
                        then -> WormholeXTreme.getScheduler().runTask(WormholeXTreme.getThisPlugin(), then)));
            }
            catch (final RuntimeException noScheduler)
            {
                siftThen(work, Runnable::run);
            }
        }

        /**
         * Sifts, then hands the main thread either the second pass or the job's end.
         *
         * <p>An OutOfMemoryError is not caught, so the pool still reports it, but the finally puts the
         * job down all the same, letting go of what it held.
         */
        private void siftThen(final Runnable work, final Consumer<Runnable> onMain)
        {
            boolean sifted = false;
            Throwable why = null;
            try
            {
                work.run();
                sifted = true;
            }
            catch (final Exception | LinkageError failed)
            {
                why = failed;
            }
            finally
            {
                final Throwable failure = why;
                onMain.accept(sifted ? this::again : () -> giveUp(failure));
            }
        }

        /** Reads the chunks again for the blocks the sift kept. */
        private void again()
        {
            next = 0;
            sifting = false;
        }

        /** Drops a job whose sift failed, so the next look at the mirror starts a fresh one. */
        private void giveUp(final Throwable failure)
        {
            done = true;
            cancel();
            // A job forgotten while it sifted says nothing, or it would hide its successor's failure.
            if (JOBS.remove(key, this) && FAILED.add(key))
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "Could not work out what the mirror"
                    + " capture of " + far.getName() + " around " + key + " can see; it is tried again when"
                    + " next wanted", failure);
            }
        }

        private void finish()
        {
            done = true;
            cancel();
            final MirrorCapture capture = builder.build();
            JOBS.remove(key);
            LOADED.put(key, new Held(capture, System.currentTimeMillis()));
            ABSENT.remove(key);
            WARNED.remove(key);
            FAILED.remove(key);
            changed();
            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, "Captured " + capture.describe()
                + ((reachKept < reachAsked) ? (", cut from " + reachAsked + " to " + reachKept
                    + " blocks ahead to keep under " + MOST_KEPT + " blocks") : ""));
            save(capture, fileOf(key));
        }

        void cancel()
        {
            if (task != null)
            {
                task.cancel();
                task = null;
            }
        }

        /**
         * Whether a block is air of any kind.
         *
         * <p>By comparing the constants: from 1.20.6 {@code Material.isAir()} asks the block
         * registry, which a server-free test cannot reach, and CI on 1.20.6 through 1.21.10
         * failed on exactly that while 1.20 through 1.20.4 passed.
         */
        static boolean isAir(final BlockData data)
        {
            final Material material = data.getMaterial();
            return (material == Material.AIR) || (material == Material.CAVE_AIR)
                || (material == Material.VOID_AIR);
        }

        /** Writes the file off the main thread, or on it where there is no other. */
        private static void save(final MirrorCapture capture, final File file)
        {
            final Runnable write = () ->
            {
                try
                {
                    capture.save(file);
                }
                catch (final IOException failed)
                {
                    WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING,
                        "Could not write mirror capture " + file.getName(), failed);
                }
            };
            try
            {
                WormholeXTreme.getScheduler().runTaskAsynchronously(WormholeXTreme.getThisPlugin(),
                    write);
            }
            catch (final RuntimeException noScheduler)
            {
                write.run();
            }
        }

        /**
         * The highest block in a column that is not air, whatever it is.
         *
         * <p>A chunk snapshot's own highest block is the highest one a player would collide with --
         * the server keeps that heightmap for movement -- so a torch on a floor under the sky, a
         * flower, a rail, or a vine hanging on an outside wall stood above it and was never read.
         * The world's surface heightmap counts every block that is not air. The higher of the two, in
         * case a world answers one and not the other.
         */
        private static int highest(final World world, final ChunkSnapshot snapshot, final int x, final int z,
            final int lx, final int lz)
        {
            return Math.max(snapshot.getHighestBlockYAt(lx, lz),
                world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE));
        }
    }
}
