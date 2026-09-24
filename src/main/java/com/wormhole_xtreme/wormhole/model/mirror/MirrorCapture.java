package com.wormhole_xtreme.wormhole.model.mirror;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

/**
 * A photograph of a mirror's far side: the blocks around the arrival point that somebody at the
 * opening could see, taken once and kept on disk, which is what a window draws from.
 *
 * <p>Drawing from a photograph rather than from the live world is what makes a window reliable.
 * The live world has to be loaded to be read, loading it stalls the server or arrives late, and a
 * mirror onto a world that is not loaded at all -- the archived-snapshot museum that #22 was
 * filed for -- could never show anything. A capture is always there, instantly, and a museum
 * stays exactly as it was captured. What it costs is currency: the far side changes only when
 * the capture is taken again, by {@code mirror set [name] capture}.
 *
 * <p>Stored as a palette of block states and one entry per block kept, sorted by position, so a
 * box the render distance across costs what its visible surfaces cost and nothing for the rest.
 * A block inside the box with no entry is <em>buried</em>: nothing at the opening could see it,
 * and every view leaves the real world there. A dense grid of the box came first, and at the
 * render distance it was eighty megabytes a capture. Air that can be seen has an entry of its
 * own, since air a viewer can see is what a view carves through the real world.
 *
 * <p>A capture may instead be <em>complete</em>, holding every block that is not air, with no
 * entry meaning air: the form {@code mirror debug save} once wrote, for replaying a view away
 * from the server. Nothing writes one now; the flag is read so an old file is not misread.
 *
 * <p>Immutable once built, so it can be read by a redraw and written to disk at the same time.
 */
public final class MirrorCapture
{
    /** The file header, so a file that is not one is refused rather than misread. */
    private static final int MAGIC = 0x4D495257;

    /** 3 keeps only entries, sorted; 1 and 2 wrote a dense grid, and are taken again. */
    private static final int VERSION = 3;

    /** Most distinct block states a capture may hold; a short index per block. */
    private static final int MOST_STATES = 65_535;

    /** What {@link #nameAt} says of a block inside the box that nothing could see. */
    static final String BURIED = "wormhole:buried";

    private final String worldName;
    private final boolean hasSky;
    private final boolean complete;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final long takenAt;
    private final String[] names;
    private final BlockData[] states;
    /** Every entry's position inside the box, as {@link #cell}, ascending. */
    private final long[] cells;
    /** Each entry's palette index, in step with {@link #cells}. */
    private final short[] values;
    /** Every column with a block that is not air, as {@code (dx << 20) | dz}, ascending. */
    private final long[] columns;
    /** The highest such block in each, as an offset from the box's floor, in step. */
    private final short[] tops;
    /** The air that can be seen, which has no entries. */
    private final MirrorSeenAir air;
    private BlockData standIn;

    private MirrorCapture(final String worldName, final boolean hasSky, final boolean complete, final Box box,
        final long takenAt, final Blocks blocks, final MirrorSeenAir air)
    {
        this.air = air;
        this.worldName = worldName;
        this.hasSky = hasSky;
        this.complete = complete;
        this.minX = box.minX();
        this.minY = box.minY();
        this.minZ = box.minZ();
        this.sizeX = box.sizeX();
        this.sizeY = box.sizeY();
        this.sizeZ = box.sizeZ();
        this.takenAt = takenAt;
        this.names = blocks.names;
        this.states = blocks.states;
        this.cells = blocks.cells;
        this.values = blocks.values;
        // The highest block that is not air in each column, which the drawing skips sky above.
        final Map<Long, Integer> highest = new HashMap<>();
        for (int i = 0; i < cells.length; i++)
        {
            if (values[i] != 0)
            {
                final long column = cells[i] >>> 20;
                final int dy = (int) (cells[i] & 0xFFFFF);
                highest.merge(column, dy, Math::max);
            }
        }
        columns = highest.keySet().stream().mapToLong(Long::longValue).sorted().toArray();
        tops = new short[columns.length];
        for (int i = 0; i < columns.length; i++)
        {
            tops[i] = highest.get(columns[i]).shortValue();
        }
    }

    /** A block's place in the box as one number that sorts by x, then z, then y. */
    private static long cell(final int dx, final int dy, final int dz)
    {
        return (((long) dx) << 40) | (((long) dz) << 20) | dy;
    }

    /**
     * The box a capture covers.
     *
     * @param minX
     *            its lowest x
     * @param minY
     *            its lowest y
     * @param minZ
     *            its lowest z
     * @param sizeX
     *            blocks along x
     * @param sizeY
     *            blocks along y
     * @param sizeZ
     *            blocks along z
     */
    public record Box(int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ)
    {
    }

    /**
     * Where a traveller arrives, which the opening's bottom row shows, and the way they face.
     *
     * @param x
     *            the block arrived in
     * @param y
     *            its y
     * @param z
     *            its z
     * @param aheadX
     *            one step the way a traveller faces on arrival, x
     * @param aheadZ
     *            the same, z
     */
    public record Arrival(int x, int y, int z, int aheadX, int aheadZ)
    {
    }

    /** The palette and the entries that index into it, as a capture is made from them. */
    private static final class Blocks
    {
        private final String[] names;
        private final BlockData[] states;
        private final long[] cells;
        private final short[] values;

        Blocks(final String[] names, final BlockData[] states, final long[] cells, final short[] values)
        {
            this.names = names;
            this.states = states;
            this.cells = cells;
            this.values = values;
        }
    }

    /**
     * Builds a capture block by block.
     *
     * <p>Two ways in. {@link #put} records a block and its state, for a small box or a complete
     * one. For a capture of the far side at the render distance, the box is too big to hold every
     * block's state at once, so {@link #note} first records only whether each block is air and
     * whether it hides what is behind it -- a bit each -- {@link #keepOnlySeen} works out from
     * those what somebody at the opening could see, and {@link #put} is then called for those
     * blocks alone, on a second pass over the world.
     */
    public static final class Builder
    {
        private final String worldName;
        private final boolean hasSky;
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int sizeX;
        private final int sizeY;
        private final int sizeZ;
        private final List<String> names = new ArrayList<>();
        private final List<BlockData> states = new ArrayList<>();
        private final Map<String, Short> byName = new HashMap<>();
        /**
         * The last state recorded, its index and whether it hides what is behind it: a column of
         * stone is the same state a hundred times, and asking it each time was most of the work.
         */
        private BlockData lastData;
        private short lastIndex;
        private boolean lastOccludes;
        private boolean lastMurky;
        /** Every block that is not air. */
        private final BitSet filled;
        /** Every block that hides what is behind it. */
        private final BitSet solid;
        /** Blocks blanked to air by {@link #clear}, which {@link #put} then leaves alone. */
        private final BitSet cleared;
        /** Every block of water, which a ray sees through for {@link #WATER_SIGHT} blocks and no further. */
        private final BitSet murky;
        /** After {@link #keepOnlySeen}, the blocks it kept; before it, null: everything. */
        private BitSet kept;
        /** After {@link #keepOnlySeen}, the air it saw; before it, null: none. */
        private BitSet seenAir;
        private boolean complete = true;
        private long[] cells = new long[1024];
        private short[] values = new short[1024];
        private int count;

        /**
         * @param worldName
         *            the far world
         * @param hasSky
         *            whether the far world has a sky
         * @param box
         *            the blocks it covers
         * @param air
         *            what air is, which becomes index 0; null before the server can make block
         *            data, in which case it is made from its name the first time it is needed
         */
        public Builder(final String worldName, final boolean hasSky, final Box box, final BlockData air)
        {
            this.worldName = worldName;
            this.hasSky = hasSky;
            this.minX = box.minX();
            this.minY = box.minY();
            this.minZ = box.minZ();
            this.sizeX = box.sizeX();
            this.sizeY = box.sizeY();
            this.sizeZ = box.sizeZ();
            final int volume = sizeX * sizeY * sizeZ;
            this.filled = new BitSet(volume);
            this.solid = new BitSet(volume);
            this.cleared = new BitSet(volume);
            this.murky = new BitSet(volume);
            names.add((air == null) ? "minecraft:air" : air.getAsString());
            states.add(air);
            byName.put(names.get(0), (short) 0);
        }

        private int at(final int x, final int y, final int z)
        {
            final int dx = x - minX;
            final int dy = y - minY;
            final int dz = z - minZ;
            if ((dx < 0) || (dx >= sizeX) || (dy < 0) || (dy >= sizeY) || (dz < 0) || (dz >= sizeZ))
            {
                return -1;
            }
            return offset(dx, dy, dz, sizeY, sizeZ);
        }

        /**
         * Notes what kind of block stands here, without keeping its state.
         *
         * @param x
         *            world x
         * @param y
         *            world y
         * @param z
         *            world z
         * @param data
         *            what stands there; air need not be noted
         */
        public void note(final int x, final int y, final int z, final BlockData data)
        {
            final int at = at(x, y, z);
            if (at >= 0)
            {
                if (data != lastData)
                {
                    lastData = data;
                    lastIndex = -1;
                    lastOccludes = hides(data);
                    lastMurky = isWater(data);
                }
                filled.set(at);
                solid.set(at, lastOccludes);
                murky.set(at, lastMurky);
            }
        }

        /**
         * Records one block, if it is inside the box and wanted.
         *
         * @param x
         *            world x
         * @param y
         *            world y
         * @param z
         *            world z
         * @param data
         *            what stands there; air need not be recorded
         */
        public void put(final int x, final int y, final int z, final BlockData data)
        {
            final int at = at(x, y, z);
            if ((at < 0) || cleared.get(at) || ((kept != null) && !kept.get(at)))
            {
                return;
            }
            if ((data != lastData) || (lastIndex < 0))
            {
                lastData = data;
                lastIndex = index(data);
                lastOccludes = (lastIndex != 0) && hides(data);
                lastMurky = (lastIndex != 0) && isWater(data);
            }
            filled.set(at, lastIndex != 0);
            solid.set(at, lastOccludes);
            murky.set(at, lastMurky);
            add(x, y, z, lastIndex);
        }

        /**
         * Whether a block hides what is behind it: one that occludes, or lava.
         *
         * <p>Lava does not occlude as Bukkit counts it, so a ray went through it as through
         * water, and a mirror onto the Nether kept every block under every lava lake it faced.
         * "We can't see through lava."
         */
        private static boolean hides(final BlockData data)
        {
            return data.isOccluding() || (data.getMaterial() == Material.LAVA);
        }

        /** Whether a block is water, which can be seen through, but only so far. */
        private static boolean isWater(final BlockData data)
        {
            return data.getMaterial() == Material.WATER;
        }

        /**
         * Whether a block is wanted by {@link #put} after {@link #keepOnlySeen}: seen, and not air.
         *
         * @param x
         *            world x
         * @param y
         *            world y
         * @param z
         *            world z
         * @return true if a second pass over the world should record it
         */
        public boolean wanted(final int x, final int y, final int z)
        {
            final int at = at(x, y, z);
            return (at >= 0) && filled.get(at) && !cleared.get(at) && ((kept == null) || kept.get(at));
        }

        /** Blanks one block to air, for a mirror's banner at the far side. */
        public void clear(final int x, final int y, final int z)
        {
            final int at = at(x, y, z);
            if (at >= 0)
            {
                filled.clear(at);
                solid.clear(at);
                murky.clear(at);
                cleared.set(at);
                add(x, y, z, (short) 0);
            }
        }

        /**
         * Sets every block below a height to one state, and the rest to air.
         *
         * <p>For a test that wants ground and sky without a million calls.
         *
         * @param below
         *            the first y that stays air
         * @param data
         *            what the ground is
         */
        public void fillBelow(final int below, final BlockData data)
        {
            for (int x = minX; x < (minX + sizeX); x++)
            {
                for (int z = minZ; z < (minZ + sizeZ); z++)
                {
                    for (int y = minY; (y < below) && (y < (minY + sizeY)); y++)
                    {
                        put(x, y, z, data);
                    }
                }
            }
        }

        private void add(final int x, final int y, final int z, final short index)
        {
            if (count == cells.length)
            {
                cells = Arrays.copyOf(cells, count * 2);
                values = Arrays.copyOf(values, count * 2);
            }
            cells[count] = cell(x - minX, y - minY, z - minZ);
            values[count] = index;
            count++;
        }

        private short index(final BlockData data)
        {
            final String name = data.getAsString();
            final Short known = byName.get(name);
            if (known != null)
            {
                return known;
            }
            if (names.size() >= MOST_STATES)
            {
                // A palette this size is not a place, it is noise; the block reads as air.
                return 0;
            }
            final short next = (short) names.size();
            names.add(name);
            states.add(data);
            byName.put(name, next);
            return next;
        }

        /**
         * Drops every block buried two deep: one with no face open, and no neighbour with one.
         *
         * <p>Nothing surrounded by solid blocks can be seen through a window, and a hillside is
         * nearly all inside. The layer just under the surface is kept all the same, in case: a
         * capture is looked at from angles nobody chose, and a surface block that is wrong for
         * any reason should have ground under it, not a hole. A block on the box's edge is kept,
         * since what lies beyond the edge is unknown, and it is not counted as open either.
         */
        public void prune()
        {
            final BitSet open = withAnOpenFace();
            final BitSet keep = (kept == null) ? all() : kept;
            for (int dx = 1; dx < (sizeX - 1); dx++)
            {
                for (int dz = 1; dz < (sizeZ - 1); dz++)
                {
                    for (int dy = 1; dy < (sizeY - 1); dy++)
                    {
                        final int at = offset(dx, dy, dz, sizeY, sizeZ);
                        if (!open.get(at) && !open.get(at - 1) && !open.get(at + 1)
                            && !open.get(offset(dx - 1, dy, dz, sizeY, sizeZ))
                            && !open.get(offset(dx + 1, dy, dz, sizeY, sizeZ))
                            && !open.get(offset(dx, dy, dz - 1, sizeY, sizeZ))
                            && !open.get(offset(dx, dy, dz + 1, sizeY, sizeZ)))
                        {
                            keep.clear(at);
                        }
                    }
                }
            }
            kept = keep;
            complete = false;
        }

        /** Every block inside the box's edge that is not solid or has a neighbour that is not. */
        private BitSet withAnOpenFace()
        {
            final BitSet open = new BitSet(filled.size());
            for (int dx = 1; dx < (sizeX - 1); dx++)
            {
                for (int dz = 1; dz < (sizeZ - 1); dz++)
                {
                    for (int dy = 1; dy < (sizeY - 1); dy++)
                    {
                        final int at = offset(dx, dy, dz, sizeY, sizeZ);
                        if (!solid.get(at) || !solid.get(at - 1) || !solid.get(at + 1)
                            || !solid.get(offset(dx - 1, dy, dz, sizeY, sizeZ))
                            || !solid.get(offset(dx + 1, dy, dz, sizeY, sizeZ))
                            || !solid.get(offset(dx, dy, dz - 1, sizeY, sizeZ))
                            || !solid.get(offset(dx, dy, dz + 1, sizeY, sizeZ)))
                        {
                            open.set(at);
                        }
                    }
                }
            }
            return open;
        }

        private BitSet all()
        {
            final BitSet every = new BitSet(sizeX * sizeY * sizeZ);
            every.set(0, sizeX * sizeY * sizeZ);
            return every;
        }

        /**
         * How many blocks of water a ray sees through before the water's own fog ends it.
         *
         * <p>Water does not occlude, so a ray through an ocean went on to the bed however deep,
         * and a mirror onto a beach kept the water in the whole fan of its view. A player under
         * water sees a few dozen blocks; past that the client draws fog.
         */
        static final int WATER_SIGHT = 32;

        /** @return how many blocks that are not air this would keep, as it stands */
        public int keptCount()
        {
            return (kept == null) ? filled.cardinality() : kept.cardinality();
        }

        /**
         * Keeps only what can be seen, then less, until what is kept fits a budget.
         *
         * <p>A capture of a jungle or an ocean bed keeps a large share of the fan of its view,
         * since leaves and water are seen through: millions of blocks, tens of megabytes on
         * disk and as much again in memory while any window draws from it. Rather than a
         * setting to lower, the reach is shortened by a quarter at a time until the kept blocks
         * fit, the way a view is cut shallower until it fits ({@code MirrorWindows.MOST_FIXED}).
         * Never short of {@code floor}, the view depth: a view drawn past its capture would run
         * out of room, so a room that is still too big at the depth is kept as it is.
         *
         * @param from
         *            where a traveller arrives, and the way they face
         * @param reach
         *            how far to see, to begin with
         * @param floor
         *            how far to see at the least
         * @param budget
         *            how many blocks that are not air may be kept
         * @return how far this ended up seeing
         */
        public int keepOnlySeenWithin(final Arrival from, final int reach, final int floor, final int budget)
        {
            int to = reach;
            keepOnlySeen(from, to);
            while ((keptCount() > budget) && (to > floor))
            {
                // A shorter pass keeps a subset of the last: what a ray sees to 24 it saw to 32.
                to = Math.max(floor, (to * 3) / 4);
                keepOnlySeen(from, to);
            }
            return to;
        }

        /**
         * Keeps only what can be seen from the opening, and leaves the rest to the real world.
         *
         * <p>Rays from points across the front of the opening, in every direction that leaves
         * through its back -- the opening is a hole a block deep, so nothing steeper gets
         * through, however close the eye -- a degree apart, which at the far end of the depth
         * is under a block; each followed a block at a time until it meets something that
         * hides what is behind it, or leaves the box, or passes the depth. Every block a
         * ray passes through or ends on is seen, air included: air that a viewer can see is what
         * the view carves through the real world. A block beside anything seen that can be seen
         * through is kept too, which catches what a ray a degree wide slipped past and what
         * stands behind a fence or under a glass pane, and so is one layer behind every kept
         * block, in case. Everything else is left to the real world, whatever it was: a window
         * can never show it, and a view drawn whole would have sent it, and the inside of every
         * far hill, for nothing. Lava ends a ray as stone does, and water ends one after
         * {@link #WATER_SIGHT} blocks of it, which is about where the game's own fog would.
         *
         * @param from
         *            where a traveller arrives, and the way they face
         * @param depth
         *            how far from the opening a view reaches
         */
        public void keepOnlySeen(final Arrival from, final int depth)
        {
            final BitSet seen = new BitSet(sizeX * sizeY * sizeZ);
            castRays(seen, from, depth + 2.0);
            // A block beside anything seen that can be seen through -- air, glass, a fence, water
            // -- has a face a viewer can see; the stone behind a fence and under a glass pane
            // were dropped when only air counted.
            final BitSet faced = (BitSet) seen.clone();
            for (int at = seen.nextSetBit(0); at >= 0; at = seen.nextSetBit(at + 1))
            {
                if (!solid.get(at))
                {
                    keepBeside(faced, at);
                }
            }
            // And one layer behind every block kept, in case: a capture is looked at from angles
            // nobody chose, and a surface block that is wrong for any reason should have ground
            // behind it, not a hole. "We could probably store ground behind stuff."
            final BitSet keep = (BitSet) faced.clone();
            for (int at = faced.nextSetBit(0); at >= 0; at = faced.nextSetBit(at + 1))
            {
                if (filled.get(at))
                {
                    keepBeside(keep, at);
                }
            }
            if (kept != null)
            {
                keep.and(kept);
            }
            kept = keep;
            complete = false;
            // Seen air is kept, so a view knows to carve the real world there.
            seenAir = (BitSet) seen.clone();
            seenAir.andNot(filled);
        }

        /** Marks what every ray through the opening passes, out to {@code reach}. */
        private void castRays(final BitSet seen, final Arrival from, final double reach)
        {
            // The opening is a hole a block wide, two tall and a block deep, through the wall.
            // A line of sight goes in at its front and out at its back, so what can be seen is
            // bounded by the hole's own shape: nothing steeper than a block sideways or two up
            // per block in. The back of the hole is the back of the block behind the arrival
            // block, which is where the rays start; the front is a block further back.
            // A hair inside the arrival block, whichever way it faces. On the boundary itself a ray
            // facing north or west starts in the block behind, which for a mirror is its wall: every
            // ray stopped where it began, and the capture kept nothing.
            final int aheadX = from.aheadX();
            final int aheadZ = from.aheadZ();
            final double exitX = (from.x() + 0.5) - ((0.5 - 1.0e-6) * aheadX);
            final double exitZ = (from.z() + 0.5) - ((0.5 - 1.0e-6) * aheadZ);
            final int rightX = -aheadZ;
            final int rightZ = aheadX;
            final double step = Math.tan(Math.toRadians(1.0));
            // Filled afresh for each ray, which only reads them.
            final double[] origin = new double[3];
            final double[] direction = new double[3];
            // Three blocks wide, centred on the arrival: a mirror two banners wide sees one column more
            // than its room's, on whichever side its view turns that column to, so a room captured
            // once serves a mirror of either width looking in either way.
            for (int column = -4; column <= 4; column++)
            {
                final double across = column * 0.35;
                for (double up = 0.1; up < 2.0; up += 0.2)
                {
                    // From this point at the front of the hole, every direction out of its back.
                    for (double sideways = (-1.5 - across) + (step / 2); sideways < (1.5 - across); sideways += step)
                    {
                        for (double upward = -up + (step / 2); upward < (2.0 - up); upward += step)
                        {
                            final double length = Math.sqrt(1.0 + (sideways * sideways) + (upward * upward));
                            origin[0] = (exitX + ((across + sideways) * rightX)) - minX;
                            origin[1] = (from.y() + up + upward) - minY;
                            origin[2] = (exitZ + ((across + sideways) * rightZ)) - minZ;
                            direction[0] = (aheadX + (sideways * rightX)) / length;
                            direction[1] = upward / length;
                            direction[2] = (aheadZ + (sideways * rightZ)) / length;
                            ray(seen, origin, direction, reach);
                        }
                    }
                }
            }
        }

        /** Keeps the six blocks round one, where they are not air. */
        private void keepBeside(final BitSet keep, final int at)
        {
            final int dx = at / (sizeY * sizeZ);
            final int dz = (at / sizeY) % sizeZ;
            final int dy = at % sizeY;
            keepOne(keep, dx - 1, dy, dz);
            keepOne(keep, dx + 1, dy, dz);
            keepOne(keep, dx, dy - 1, dz);
            keepOne(keep, dx, dy + 1, dz);
            keepOne(keep, dx, dy, dz - 1);
            keepOne(keep, dx, dy, dz + 1);
        }

        private void keepOne(final BitSet keep, final int dx, final int dy, final int dz)
        {
            if ((dx >= 0) && (dx < sizeX) && (dy >= 0) && (dy < sizeY) && (dz >= 0) && (dz < sizeZ))
            {
                final int at = offset(dx, dy, dz, sizeY, sizeZ);
                if (filled.get(at))
                {
                    keep.set(at);
                }
            }
        }

        /** Which axis a ray crosses a block boundary on next: whichever crossing comes soonest. */
        private static int soonest(final double[] tMax)
        {
            if (tMax[0] < tMax[1])
            {
                return (tMax[0] < tMax[2]) ? 0 : 2;
            }
            return (tMax[1] < tMax[2]) ? 1 : 2;
        }

        /**
         * Follows one ray through the box, marking what it passes, until something solid or the edge.
         *
         * @param o
         *            where it starts, from the box's corner; not changed
         * @param d
         *            its direction, of length one; not changed
         */
        private void ray(final BitSet seen, final double[] o, final double[] d, final double reach)
        {
            final int[] c = { (int) Math.floor(o[0]), (int) Math.floor(o[1]), (int) Math.floor(o[2]) };
            final int[] size = { sizeX, sizeY, sizeZ };
            final int[] stepOf = new int[3];
            final double[] tMax = new double[3];
            final double[] tDelta = new double[3];
            for (int axis = 0; axis < 3; axis++)
            {
                stepOf[axis] = (int) Math.signum(d[axis]);
                tDelta[axis] = (stepOf[axis] == 0) ? Double.POSITIVE_INFINITY : Math.abs(1.0 / d[axis]);
                final double edge = (stepOf[axis] > 0) ? (c[axis] + 1) : c[axis];
                tMax[axis] = (stepOf[axis] == 0) ? Double.POSITIVE_INFINITY : ((edge - o[axis]) / d[axis]);
            }
            int water = 0;
            double t = 0.0;
            while (t < reach)
            {
                if ((c[0] < 0) || (c[0] >= size[0]) || (c[1] < 0) || (c[1] >= size[1]) || (c[2] < 0) || (c[2] >= size[2]))
                {
                    return;
                }
                final int at = offset(c[0], c[1], c[2], sizeY, sizeZ);
                seen.set(at);
                if (solid.get(at) || (murky.get(at) && (++water > WATER_SIGHT)))
                {
                    return;
                }
                final int next = soonest(tMax);
                t = tMax[next];
                c[next] += stepOf[next];
                tMax[next] += tDelta[next];
            }
        }

        /** @return the finished capture, taken now */
        public MirrorCapture build()
        {
            // Sorted by position, the last word on each block winning: a block put and then
            // cleared is air, and a block put twice is what it was put as last.
            final int[] byCell = byCell();
            final long[] outCells = new long[count];
            final short[] outValues = new short[count];
            int out = 0;
            for (int i = 0; i < count; i++)
            {
                final int which = byCell[i];
                final long cell = cells[which];
                if ((kept != null) && !kept.get(offsetOf(cell)))
                {
                    continue;
                }
                if ((out > 0) && (outCells[out - 1] == cell))
                {
                    outValues[out - 1] = values[which];
                }
                else
                {
                    outCells[out] = cell;
                    outValues[out] = values[which];
                    out++;
                }
            }
            return new MirrorCapture(worldName, hasSky, complete, new Box(minX, minY, minZ, sizeX, sizeY, sizeZ),
                System.currentTimeMillis(),
                new Blocks(names.toArray(new String[0]), states.toArray(new BlockData[0]),
                    Arrays.copyOf(outCells, out), Arrays.copyOf(outValues, out)),
                MirrorSeenAir.of(seenAir, cleared, sizeX, sizeY, sizeZ));
        }

        /**
         * Every entry's index in order of position, entries for one block in the order they came.
         * Blocks put in order -- a fill, or a photograph column by column -- need no sorting.
         */
        private int[] byCell()
        {
            boolean ordered = true;
            for (int i = 1; ordered && (i < count); i++)
            {
                ordered = cells[i] > cells[i - 1];
            }
            final int[] byCell = new int[count];
            for (int i = 0; i < count; i++)
            {
                byCell[i] = i;
            }
            if (!ordered)
            {
                final Integer[] boxed = new Integer[count];
                for (int i = 0; i < count; i++)
                {
                    boxed[i] = i;
                }
                Arrays.sort(boxed, (a, b) -> (cells[a] != cells[b]) ? Long.compare(cells[a], cells[b]) : Integer.compare(a, b));
                for (int i = 0; i < count; i++)
                {
                    byCell[i] = boxed[i];
                }
            }
            return byCell;
        }

        private int offsetOf(final long cell)
        {
            return offset((int) (cell >>> 40), (int) (cell & 0xFFFFF), (int) ((cell >>> 20) & 0xFFFFF), sizeY, sizeZ);
        }

        /** Where a block of the box is in its bit sets: x-major, then z, then y. */
        private static int offset(final int dx, final int dy, final int dz, final int sizeY,
            final int sizeZ)
        {
            return (((dx * sizeZ) + dz) * sizeY) + dy;
        }
    }

    /** @return the far world's name */
    public String worldName()
    {
        return worldName;
    }

    /** @return whether a line of sight that meets nothing is looking at sky */
    public boolean hasSky()
    {
        return hasSky;
    }

    /** @return when it was taken, in milliseconds since the epoch */
    public long takenAt()
    {
        return takenAt;
    }

    /** @return how many blocks the box holds */
    public int size()
    {
        return sizeX * sizeY * sizeZ;
    }

    /** @return how many distinct block states it holds, air included */
    public int states()
    {
        return names.length;
    }

    /** @return true if it holds every block that is not air, so no entry means air rather than buried */
    public boolean complete()
    {
        return complete;
    }

    /** @return how many blocks it keeps, seen air included */
    public int kept()
    {
        return cells.length + seenAir();
    }

    /** @return how many blocks of air that can be seen it keeps */
    public int seenAir()
    {
        return air.count();
    }

    /** Whether a block inside the box is air that can be seen, by the column runs. */
    private boolean seenAirAt(final int x, final int y, final int z)
    {
        return air.has(x - minX, y - minY, z - minZ);
    }

    /**
     * Whether a block is inside the box.
     *
     * @param x
     *            world x
     * @param y
     *            world y
     * @param z
     *            world z
     * @return true if {@link #at} can answer for it
     */
    public boolean contains(final int x, final int y, final int z)
    {
        return (x >= minX) && (x < (minX + sizeX)) && (y >= minY) && (y < (minY + sizeY))
            && (z >= minZ) && (z < (minZ + sizeZ));
    }

    /** The entry for a block inside the box, or a negative number for none. */
    private int entry(final int x, final int y, final int z)
    {
        return Arrays.binarySearch(cells, cell(x - minX, y - minY, z - minZ));
    }

    /**
     * What stands at a block.
     *
     * @param x
     *            world x
     * @param y
     *            world y
     * @param z
     *            world z
     * @return the block state; null outside the box; stone for a block inside it that nothing
     *         could see, which nothing should ask about
     */
    public BlockData at(final int x, final int y, final int z)
    {
        if (!contains(x, y, z))
        {
            return null;
        }
        final int entry = entry(x, y, z);
        if (entry < 0)
        {
            if (complete || seenAirAt(x, y, z))
            {
                return state((short) 0);
            }
            if (standIn == null)
            {
                standIn = Bukkit.createBlockData(Material.STONE);
            }
            return standIn;
        }
        return state(values[entry]);
    }

    /**
     * Whether a block is air.
     *
     * @param x
     *            world x
     * @param y
     *            world y
     * @param z
     *            world z
     * @return true for air that can be seen, and for anything outside the box; false for a block
     *         inside it that nothing could see
     */
    public boolean isAir(final int x, final int y, final int z)
    {
        if (!contains(x, y, z))
        {
            return true;
        }
        final int entry = entry(x, y, z);
        return (entry < 0) ? (complete || seenAirAt(x, y, z)) : (values[entry] == 0);
    }

    /**
     * Whether a block is inside the box but nothing at the opening could see it, so every view
     * leaves the real world there.
     *
     * @param x
     *            world x
     * @param y
     *            world y
     * @param z
     *            world z
     * @return true if so; never for a complete capture
     */
    public boolean isBuried(final int x, final int y, final int z)
    {
        return !complete && contains(x, y, z) && (entry(x, y, z) < 0) && !seenAirAt(x, y, z);
    }

    /**
     * The highest block that is not air in a column.
     *
     * @param x
     *            world x
     * @param z
     *            world z
     * @return its y, or one below the box for a column with none, or one outside it
     */
    public int top(final int x, final int z)
    {
        final int dx = x - minX;
        final int dz = z - minZ;
        if ((dx < 0) || (dx >= sizeX) || (dz < 0) || (dz >= sizeZ))
        {
            return minY - 1;
        }
        final int column = Arrays.binarySearch(columns, (((long) dx) << 20) | dz);
        return (column < 0) ? (minY - 1) : (minY + tops[column]);
    }

    /** @return the box's lowest y */
    public int minY()
    {
        return minY;
    }

    /** @return how many blocks the box is across, along x */
    public int across()
    {
        return sizeX;
    }

    /** @return the box's corners, {@code {minX, minY, minZ, maxX, maxY, maxZ}}, inclusive */
    public int[] bounds()
    {
        return new int[] { minX, minY, minZ, (minX + sizeX) - 1, (minY + sizeY) - 1, (minZ + sizeZ) - 1 };
    }

    /** Handed each block kept. */
    @FunctionalInterface
    public interface Kept
    {
        /**
         * @param x
         *            world x
         * @param y
         *            world y
         * @param z
         *            world z
         * @param air
         *            true for air that can be seen
         */
        void at(int x, int y, int z, boolean air);
    }

    /**
     * Visits every block kept, seen air included, in order of position.
     *
     * @param kept
     *            handed each one; {@link #at} says what it is
     */
    public void forEachKept(final Kept kept)
    {
        for (int i = 0; i < cells.length; i++)
        {
            final long cell = cells[i];
            kept.at(minX + (int) (cell >>> 40), minY + (int) (cell & 0xFFFFF), minZ + (int) ((cell >>> 20) & 0xFFFFF),
                values[i] == 0);
        }
        air.forEach(minX, minY, minZ, kept);
    }

    /**
     * Writes the capture to a file, whole or not at all.
     *
     * @param file
     *            where
     * @throws IOException
     *             if it could not be written
     */
    public void save(final File file) throws IOException
    {
        final File parent = file.getParentFile();
        if ((parent != null) && !parent.isDirectory() && !parent.mkdirs())
        {
            throw new IOException("could not create " + parent);
        }
        final File temp = new File(parent, file.getName() + ".tmp");
        try (FileOutputStream raw = new FileOutputStream(temp);
            DataOutputStream out = new DataOutputStream(new GZIPOutputStream(raw, 65_536)))
        {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            writeString(out, worldName);
            out.writeBoolean(hasSky);
            out.writeBoolean(complete);
            out.writeInt(minX);
            out.writeInt(minY);
            out.writeInt(minZ);
            out.writeInt(sizeX);
            out.writeInt(sizeY);
            out.writeInt(sizeZ);
            out.writeLong(takenAt);
            out.writeInt(names.length);
            for (final String name : names)
            {
                writeString(out, name);
            }
            out.writeInt(cells.length);
            for (int i = 0; i < cells.length; i++)
            {
                out.writeLong(cells[i]);
                out.writeShort(values[i]);
            }
            air.write(out);
        }
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Reads a capture back. Block states are made from their names the first time each is
     * asked for, which needs a running server.
     *
     * <p>A file from before captures kept only what could be seen is refused: it is a grid of
     * the whole box, and the mirror it is for takes a fresh capture on the next look.
     *
     * @throws IOException
     *             if the file is missing, not a capture, of an earlier kind, or cut short
     */
    public static MirrorCapture load(final File file) throws IOException
    {
        // The raw stream is its own resource: a file that is not gzip fails inside the gzip
        // stream's constructor, and a stream opened in the same expression would never close.
        try (FileInputStream raw = new FileInputStream(file);
            DataInputStream in = new DataInputStream(new GZIPInputStream(raw, 65_536)))
        {
            if (in.readInt() != MAGIC)
            {
                throw new IOException(file + " is not a mirror capture");
            }
            final int version = in.readInt();
            if (version != VERSION)
            {
                throw new IOException(file + " is capture version " + version + ", not " + VERSION
                    + "; it will be taken again");
            }
            final String worldName = readString(in);
            final boolean hasSky = in.readBoolean();
            final boolean complete = in.readBoolean();
            final int minX = in.readInt();
            final int minY = in.readInt();
            final int minZ = in.readInt();
            final int sizeX = in.readInt();
            final int sizeY = in.readInt();
            final int sizeZ = in.readInt();
            final long takenAt = in.readLong();
            final int count = in.readInt();
            if ((count < 1) || (count > MOST_STATES) || (sizeX < 1) || (sizeY < 1) || (sizeZ < 1)
                || (((long) sizeX * sizeY * sizeZ) > Integer.MAX_VALUE))
            {
                throw new IOException(file + " has an impossible shape");
            }
            final String[] names = new String[count];
            for (int i = 0; i < count; i++)
            {
                names[i] = readString(in);
            }
            final int entries = in.readInt();
            if ((entries < 0) || (entries > (sizeX * sizeY * sizeZ)))
            {
                throw new IOException(file + " has an impossible number of blocks");
            }
            final long[] cells = new long[entries];
            final short[] values = new short[entries];
            for (int i = 0; i < entries; i++)
            {
                cells[i] = in.readLong();
                values[i] = in.readShort();
                if ((i > 0) && (cells[i] <= cells[i - 1]))
                {
                    throw new IOException(file + " is out of order");
                }
            }
            final MirrorSeenAir air = MirrorSeenAir.read(in, file, sizeX, sizeY, sizeZ);
            return new MirrorCapture(worldName, hasSky, complete, new Box(minX, minY, minZ, sizeX, sizeY, sizeZ),
                takenAt, new Blocks(names, new BlockData[count], cells, values), air);
        }
    }

    /** @return the names of every state, air first, for a test or a listing */
    List<String> names()
    {
        return List.of(names);
    }

    /**
     * The name of what stands at a block, without making the block state.
     *
     * @param x
     *            world x
     * @param y
     *            world y
     * @param z
     *            world z
     * @return the state's name, {@value #BURIED} for a block nothing could see, or "outside"
     *         past the box
     */
    public String nameAt(final int x, final int y, final int z)
    {
        if (!contains(x, y, z))
        {
            return "outside";
        }
        final int entry = entry(x, y, z);
        if (entry < 0)
        {
            return (complete || seenAirAt(x, y, z)) ? names[0] : BURIED;
        }
        return names[values[entry]];
    }

    /** @return how many blocks kept are not air */
    public int filled()
    {
        int filled = 0;
        for (final short value : values)
        {
            if (value != 0)
            {
                filled++;
            }
        }
        return filled;
    }

    /** @return the box, its age and what it holds, in one line */
    public String describe()
    {
        return "capture of " + worldName + " x " + minX + ".." + (minX + sizeX - 1) + " y " + minY
            + ".." + (minY + sizeY - 1) + " z " + minZ + ".." + (minZ + sizeZ - 1) + ", "
            + filled() + " blocks and " + seenAir() + " air kept of " + size()
            + " in the box, " + names.length + " kinds, taken "
            + ((System.currentTimeMillis() - takenAt) / 1000L) + "s ago" + (hasSky ? ", sky" : ", no sky")
            + (complete ? ", complete" : "");
    }

    /** @return how long ago this capture was taken, in whole seconds */
    long secondsOld()
    {
        return (System.currentTimeMillis() - takenAt) / 1000L;
    }

    /**
     * What a capture holds, one {@code mirror debug} line each.
     */
    public java.util.List<String> describeLines()
    {
        return java.util.List.of(
            MirrorText.field("box", worldName + " x " + minX + ".." + (minX + sizeX - 1) + " y " + minY + ".."
                + (minY + sizeY - 1) + " z " + minZ + ".." + (minZ + sizeZ - 1)),
            MirrorText.field("kept", filled() + " blocks and " + seenAir() + " air of " + size() + ", "
                + names.length + " kinds"),
            MirrorText.field("taken", secondsOld() + "s ago, "
                + (hasSky ? "sky" : "no sky") + (complete ? ", complete" : "")));
    }

    /** The state for an index, made from its name the first time. */
    private BlockData state(final short index)
    {
        BlockData state = states[index];
        if (state == null)
        {
            state = Bukkit.createBlockData(names[index]);
            states[index] = state;
        }
        return state;
    }

    private static void writeString(final DataOutputStream out, final String value)
        throws IOException
    {
        final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readString(final DataInputStream in) throws IOException
    {
        final int length = in.readInt();
        if ((length < 0) || (length > 65_536))
        {
            throw new IOException("string of " + length + " bytes");
        }
        final byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
