package com.wormhole_xtreme.wormhole.model.mirror;

import java.io.File;
import java.io.IOException;
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
        final int maxZ = arrivalZ + ((ahead.z() > 0) ? reach : (ahead.z() < 0) ? 1 : reach);
        final int minY = (worldMin == null) ? (arrivalY - reach) : Math.max(worldMin, arrivalY - reach);
        final int maxY = (worldMax == null) ? (arrivalY + reach) : Math.min(worldMax - 1, arrivalY + reach);
        return new int[] { minX, minY, minZ, maxX, maxY, maxZ };
    }

    /** The depth a capture is taken to: the view depth, or the capture radius if that is less. */
    private static int captureDepth()
    {
        return Math.min(ConfigManager.getMirrorViewDepth(), ConfigManager.getMirrorCaptureRadius());
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
            generation++;
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
     * Whether a mirror's capture is old enough that a dynamic mirror should take it again.
     *
     * @param mirror
     *            the mirror
     * @param capture
     *            its capture
     * @return true if it is dynamic and the resample interval has passed
     */
    static boolean due(final QuantumMirror mirror, final MirrorCapture capture)
    {
        return (mirror.mode() == MirrorMode.DYNAMIC) && ((System.currentTimeMillis() - capture.takenAt())
            >= (ConfigManager.getMirrorDynamicResampleSeconds() * 1000L));
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
        final int[] box = needed(destination, captureDepth(),
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
        if (file.isFile() && !file.delete())
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING,
                "Could not delete mirror capture " + file.getName());
        }
        generation++;
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
        generation++;
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
        if (mirror.destination() == null)
        {
            lines.add("no destination, so no capture");
            return lines;
        }
        final String key = keyOf(mirror.destination());
        final File file = fileOf(key);
        lines.add("key " + key + ", file " + (file.isFile() ? (file.length() + " bytes") : "missing")
            + ", far world " + ((Bukkit.getWorld(mirror.destination().worldName()) == null)
                ? "not loaded" : "loaded")
            + (JOBS.containsKey(key) ? ", being taken now" : ""));
        final Held held = LOADED.get(key);
        if (held == null)
        {
            lines.add(ABSENT.contains(key) ? "not in memory, and its file was found missing"
                : "not in memory");
            return lines;
        }
        final MirrorCapture capture = held.capture;
        lines.add(capture.describe());
        final int x = (int) Math.floor(mirror.destination().x());
        final int y = (int) Math.floor(mirror.destination().y());
        final int z = (int) Math.floor(mirror.destination().z());
        lines.add("at the arrival point " + capture.nameAt(x, y, z) + ", below it "
            + capture.nameAt(x, y - 1, z) + ", column top y " + capture.top(x, z));
        final MirrorWindow.Spot ahead = MirrorWindow.aheadOf(mirror.destination().yaw());
        lines.add("arrival yaw " + mirror.destination().yaw() + ", so ahead is " + ahead.x() + ","
            + ahead.z());
        lines.add("8 ahead: " + capture.nameAt(x + (8 * ahead.x()), y, z + (8 * ahead.z()))
            + ", top y " + capture.top(x + (8 * ahead.x()), z + (8 * ahead.z()))
            + "; 32 ahead: " + capture.nameAt(x + (32 * ahead.x()), y, z + (32 * ahead.z()))
            + ", top y " + capture.top(x + (32 * ahead.x()), z + (32 * ahead.z())));
        return lines;
    }

    /**
     * Takes a capture of loaded chunks around a point, at once, for {@code mirror debug save}.
     *
     * <p>Synchronous and only over chunks already loaded: it is for photographing the world
     * around a mirror somebody is standing at, so the view drawn there can be reproduced away
     * from the server, alongside the far side's own capture.
     *
     * @param world
     *            the world
     * @param x
     *            centre x
     * @param y
     *            centre y
     * @param z
     *            centre z
     * @param radius
     *            blocks each way horizontally
     * @param file
     *            where to write it
     * @return one line saying what was written
     * @throws IOException
     *             if it could not be written
     */
    public static String captureAround(final World world, final int x, final int y, final int z,
        final int radius, final File file) throws IOException
    {
        final int minY = Math.max(world.getMinHeight(), y - radius);
        final int maxY = Math.min(world.getMaxHeight() - 1, y + radius);
        final MirrorCapture.Builder builder = new MirrorCapture.Builder(world.getName(),
            world.getEnvironment() == World.Environment.NORMAL, x - radius, minY, z - radius,
            (2 * radius) + 1, (maxY - minY) + 1, (2 * radius) + 1, Bukkit.createBlockData(Material.AIR));
        for (int chunkX = (x - radius) >> 4; chunkX <= ((x + radius) >> 4); chunkX++)
        {
            for (int chunkZ = (z - radius) >> 4; chunkZ <= ((z + radius) >> 4); chunkZ++)
            {
                if (!world.isChunkLoaded(chunkX, chunkZ))
                {
                    continue;
                }
                final ChunkSnapshot snapshot = reader.read(world, chunkX, chunkZ);
                for (int lx = 0; lx < 16; lx++)
                {
                    for (int lz = 0; lz < 16; lz++)
                    {
                        final int top = Math.min(maxY,
                            highest(world, snapshot, (chunkX << 4) + lx, (chunkZ << 4) + lz, lx, lz));
                        for (int by = minY; by <= top; by++)
                        {
                            final BlockData data = snapshot.getBlockData(lx, by, lz);
                            if (!Job.isAir(data))
                            {
                                builder.put((chunkX << 4) + lx, by, (chunkZ << 4) + lz, data);
                            }
                        }
                    }
                }
            }
        }
        final MirrorCapture capture = builder.build();
        capture.save(file);
        return "wrote " + file.getName() + ": " + capture.describe();
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
        return Math.max(snapshot.getHighestBlockYAt(lx, lz), world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE));
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
        generation++;
    }

    private static File fileOf(final String key)
    {
        return new File(DataLayout.mirrorCaptureDir(), key + ".view");
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

        Job(final String key, final World far, final MirrorPoint destination)
        {
            this.key = key;
            this.far = far;
            this.destination = destination;
            final int[] box = needed(destination, captureDepth(), far.getMinHeight(), far.getMaxHeight());
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

        private void copy(final ChunkSnapshot snapshot, final int baseX, final int baseZ)
        {
            for (int lx = 0; lx < 16; lx++)
            {
                final int x = baseX + lx;
                if ((x < minX) || (x > maxX))
                {
                    continue;
                }
                for (int lz = 0; lz < 16; lz++)
                {
                    final int z = baseZ + lz;
                    if ((z < minZ) || (z > maxZ))
                    {
                        continue;
                    }
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
                final MirrorBlock banner = other.banner();
                if (banner.worldName().equals(far.getName()))
                {
                    builder.clear(banner.x(), banner.y(), banner.z());
                }
            }
            final MirrorWindow.Spot ahead = MirrorWindow.aheadOf(destination.yaw());
            final int arrivalX = (int) Math.floor(destination.x());
            final int arrivalY = (int) Math.floor(destination.y());
            final int arrivalZ = (int) Math.floor(destination.z());
            final int depth = captureDepth();
            // A third of a million rays: off the main thread, since the box is noted and
            // nothing here reads the world again.
            final Runnable work = () ->
            {
                builder.keepOnlySeen(arrivalX, arrivalY, arrivalZ, ahead.x(), ahead.z(), depth);
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
            generation++;
            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, "Captured " + capture.describe());
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
    }
}
