package com.wormhole_xtreme.wormhole.model.mirror;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

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
         * @param world
         *            the world
         * @param chunkX
         *            chunk x
         * @param chunkZ
         *            chunk z
         * @return a snapshot of it, loading it if it must
         */
        ChunkSnapshot read(World world, int chunkX, int chunkZ);
    }

    /** Captures in memory, by destination key. */
    private static final Map<String, Held> LOADED = new HashMap<>();

    /** Keys whose file was tried and found missing or unreadable, so they are not tried again. */
    private static final Set<String> ABSENT = new HashSet<>();

    /** Captures being taken, by destination key. */
    private static final Map<String, Job> JOBS = new HashMap<>();

    /** Keys already warned about, so an unloaded far world is said once and not every sweep. */
    private static final Set<String> WARNED = new HashSet<>();

    /** Bumped whenever a capture arrives or changes, so every view knows to look again. */
    private static int generation;

    private static ChunkReader reader = (world, chunkX, chunkZ) ->
        world.getChunkAt(chunkX, chunkZ).getChunkSnapshot();

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
        final String world = destination.worldName().toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9._-]", "_");
        return world + '_' + (int) Math.floor(destination.x()) + '_'
            + (int) Math.floor(destination.y()) + '_' + (int) Math.floor(destination.z());
    }

    /**
     * The key a mirror's room is captured under, which is its capture file's name.
     *
     * @param mirror
     *            the mirror
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
        final String key = keyOf(mirror.destination());
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
     * @param mirror
     *            the mirror
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
        if (mirror.destination() == null)
        {
            return false;
        }
        final String key = keyOf(mirror.destination());
        if (JOBS.containsKey(key))
        {
            return true;
        }
        final World far = Bukkit.getWorld(mirror.destination().worldName());
        if (far == null)
        {
            if (WARNED.add(key))
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "Cannot capture the far side"
                    + " of mirror '" + mirror.name() + "': " + mirror.destination().worldName()
                    + " is not loaded. It stays a banner until that world is loaded once.");
            }
            return false;
        }
        WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, "Capturing the far side of mirror '"
            + mirror.name() + "' in " + far.getName() + " around " + key);
        final Job job = new Job(key, far, mirror.destination());
        JOBS.put(key, job);
        job.schedule();
        return true;
    }

    /**
     * Takes a mirror's capture again, keeping the old one until the new one is ready.
     *
     * @param mirror
     *            the mirror
     * @return true if it is being taken
     */
    public static boolean retake(final QuantumMirror mirror)
    {
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
        changed();
    }

    /**
     * What a mirror's capture is, for {@code mirror debug}.
     *
     * @param mirror
     *            the mirror
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
     * @param mirror
     *            the mirror
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

    /**
     * Puts a capture in memory as though it had been taken, for a test.
     *
     * @param destination
     *            the place it is of
     * @param capture
     *            the capture
     */
    static void install(final MirrorPoint destination, final MirrorCapture capture)
    {
        LOADED.put(keyOf(destination), new Held(capture, System.currentTimeMillis()));
        ABSENT.remove(keyOf(destination));
        changed();
    }

    private static File fileOf(final String key)
    {
        return new File(DataLayout.mirrorCaptureDir(), key + VIEW);
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

        Job(final String key, final World far, final MirrorPoint destination)
        {
            this.key = key;
            this.far = far;
            this.destination = destination;
            final int[] box = needed(destination, reach(far), far.getMinHeight(), far.getMaxHeight());
            minX = box[0];
            minY = box[1];
            minZ = box[2];
            maxX = box[3];
            maxY = box[4];
            maxZ = box[5];
            builder = new MirrorCapture.Builder(far.getName(),
                far.getEnvironment() == World.Environment.NORMAL, minX, minY, minZ,
                (maxX - minX) + 1, (maxY - minY) + 1, (maxZ - minZ) + 1,
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
            for (int i = 0; (i < CHUNKS_PER_TICK) && !done && !sifting; i++)
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
            final int arrivalX = (int) Math.floor(destination.x());
            final int arrivalY = (int) Math.floor(destination.y());
            final int arrivalZ = (int) Math.floor(destination.z());
            final int depth = reach(far);
            final int floor = ConfigManager.getMirrorViewDepth();
            reachAsked = depth;
            reachKept = depth;
            // A third of a million rays: off the main thread, since the box is noted and
            // nothing here reads the world again.
            final Runnable work = () ->
            {
                reachKept = builder.keepOnlySeenWithin(arrivalX, arrivalY, arrivalZ, ahead.x(), ahead.z(), depth,
                    floor, MOST_KEPT);
                builder.prune();
            };
            final Runnable again = () ->
            {
                next = 0;
                sifting = false;
            };
            try
            {
                WormholeXTreme.getScheduler().runTaskAsynchronously(WormholeXTreme.getThisPlugin(), () ->
                {
                    work.run();
                    WormholeXTreme.getScheduler().runTask(WormholeXTreme.getThisPlugin(), again);
                });
            }
            catch (final RuntimeException noScheduler)
            {
                work.run();
                again.run();
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
