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
     * How far below the arrival point a capture reaches.
     *
     * <p>Sixteen was not enough: a mirror in a library forty-seven blocks above its beach looked
     * down through the library's openings at nothing, and the beach it should have seen was
     * below the box.
     */
    private static final int BELOW = 48;

    /** How far above it. */
    private static final int ABOVE = 64;

    /** How long a capture nobody has looked at stays in memory. */
    private static final long IDLE_MILLIS = 300_000L;

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
        lines.add("8 ahead: " + capture.nameAt(x + (8 * ahead.x()), y, z + (8 * ahead.z()))
            + ", top y " + capture.top(x + (8 * ahead.x()), z + (8 * ahead.z()))
            + "; 32 ahead: " + capture.nameAt(x + (32 * ahead.x()), y, z + (32 * ahead.z()))
            + ", top y " + capture.top(x + (32 * ahead.x()), z + (32 * ahead.z())));
        return lines;
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

    /** One capture being taken, a couple of chunks a tick. */
    private static final class Job implements Runnable
    {
        private final String key;
        private final World far;
        private final MirrorCapture.Builder builder;
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;
        private final List<int[]> chunks = new ArrayList<>();
        private int next;
        private boolean done;
        private BukkitTask task;

        Job(final String key, final World far, final MirrorPoint destination)
        {
            this.key = key;
            this.far = far;
            final int radius = ConfigManager.getMirrorCaptureRadius();
            final int arrivalX = (int) Math.floor(destination.x());
            final int arrivalY = (int) Math.floor(destination.y());
            final int arrivalZ = (int) Math.floor(destination.z());
            minX = arrivalX - radius;
            maxX = arrivalX + radius;
            minZ = arrivalZ - radius;
            maxZ = arrivalZ + radius;
            minY = Math.max(far.getMinHeight(), arrivalY - BELOW);
            maxY = Math.min(far.getMaxHeight() - 1, arrivalY + ABOVE);
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
            for (int i = 0; (i < CHUNKS_PER_TICK) && !done; i++)
            {
                step();
            }
        }

        /** Reads one chunk into the box, and finishes the capture after the last. */
        void step()
        {
            if (done)
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
                finish();
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
                    final int top = Math.min(maxY, snapshot.getHighestBlockYAt(lx, lz));
                    for (int y = minY; y <= top; y++)
                    {
                        final BlockData data = snapshot.getBlockData(lx, y, lz);
                        if (!isAir(data))
                        {
                            builder.put(x, y, z, data);
                        }
                    }
                }
            }
        }

        private void finish()
        {
            done = true;
            cancel();
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
            builder.prune();
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

        private static boolean isAir(final BlockData data)
        {
            final Material material = data.getMaterial();
            return (material != null) && material.isAir();
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
