package com.wormhole_xtreme.wormhole.model.mirror;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
 * the capture is taken again, by {@code mirror stamp} or by {@code mode dynamic}.
 *
 * <p>Stored as a palette of block states and one entry per block kept, sorted by position, so a
 * box the render distance across costs what its visible surfaces cost and nothing for the rest.
 * A block inside the box with no entry is <em>buried</em>: nothing at the opening could see it,
 * and every view leaves the real world there. A dense grid of the box came first, and at the
 * render distance it was eighty megabytes a capture. Air that can be seen has an entry of its
 * own, since air a viewer can see is what a view carves through the real world.
 *
 * <p>A capture may instead be <em>complete</em>, holding every block that is not air, with no
 * entry meaning air: that is {@code mirror debug save}'s photograph of the viewer's own side,
 * for replaying a view away from the server.
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
    /**
     * Every column with air that can be seen, as {@code (dx << 20) | dz}, ascending; the air in
     * a column is a few runs, and a desert at the render distance is a hundred thousand columns
     * rather than tens of millions of blocks of air.
     */
    private final long[] airColumns;
    /** Where each column's runs start in {@link #airFrom} and {@link #airTo}, with one past the end last. */
    private final int[] airFirst;
    /** Each run's first y, as an offset from the box's floor. */
    private final short[] airFrom;
    /** Each run's last y, likewise. */
    private final short[] airTo;
    private BlockData standIn;

    private MirrorCapture(final String worldName, final boolean hasSky, final boolean complete,
        final int minX, final int minY, final int minZ, final int sizeX, final int sizeY, final int sizeZ,
        final long takenAt, final String[] names, final BlockData[] states, final long[] cells,
        final short[] values, final long[] airColumns, final int[] airFirst, final short[] airFrom,
        final short[] airTo)
    {
        this.airColumns = airColumns;
        this.airFirst = airFirst;
        this.airFrom = airFrom;
        this.airTo = airTo;
        this.worldName = worldName;
        this.hasSky = hasSky;
        this.complete = complete;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.takenAt = takenAt;
        this.names = names;
        this.states = states;
        this.cells = cells;
        this.values = values;
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
        /** Every block that is not air. */
        private final BitSet filled;
        /** Every block that hides what is behind it. */
        private final BitSet solid;
        /** Blocks blanked to air by {@link #clear}, which {@link #put} then leaves alone. */
        private final BitSet cleared;
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
         * @param minX
         *            the box's lowest x
         * @param minY
         *            the box's lowest y
         * @param minZ
         *            the box's lowest z
         * @param sizeX
         *            blocks along x
         * @param sizeY
         *            blocks along y
         * @param sizeZ
         *            blocks along z
         * @param air
         *            what air is, which becomes index 0; null before the server can make block
         *            data, in which case it is made from its name the first time it is needed
         */
        public Builder(final String worldName, final boolean hasSky, final int minX,
            final int minY, final int minZ, final int sizeX, final int sizeY, final int sizeZ,
            final BlockData air)
        {
            this.worldName = worldName;
            this.hasSky = hasSky;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            final int volume = sizeX * sizeY * sizeZ;
            this.filled = new BitSet(volume);
            this.solid = new BitSet(volume);
            this.cleared = new BitSet(volume);
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
                    lastOccludes = data.isOccluding();
                }
                filled.set(at);
                solid.set(at, lastOccludes);
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
                lastOccludes = (lastIndex != 0) && data.isOccluding();
            }
            filled.set(at, lastIndex != 0);
            solid.set(at, lastOccludes);
            add(x, y, z, lastIndex);
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

        private BitSet all()
        {
            final BitSet every = new BitSet(sizeX * sizeY * sizeZ);
            every.set(0, sizeX * sizeY * sizeZ);
            return every;
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
         * far hill, for nothing.
         *
         * @param arrivalX
         *            the block a traveller arrives in, which the opening's bottom row shows
         * @param arrivalY
         *            its y
         * @param arrivalZ
         *            its z
         * @param aheadX
         *            one step the way a traveller faces on arrival, x
         * @param aheadZ
         *            the same, z
         * @param depth
         *            how far from the opening a view reaches
         */
        public void keepOnlySeen(final int arrivalX, final int arrivalY, final int arrivalZ,
            final int aheadX, final int aheadZ, final int depth)
        {
            final int volume = sizeX * sizeY * sizeZ;
            final BitSet seen = new BitSet(volume);
            // The opening is a hole a block wide, two tall and a block deep, through the wall.
            // A line of sight goes in at its front and out at its back, so what can be seen is
            // bounded by the hole's own shape: nothing steeper than a block sideways or two up
            // per block in. The back of the hole is the back of the block behind the arrival
            // block, which is where the rays start; the front is a block further back.
            // A hair inside the arrival block, whichever way it faces. On the boundary itself a ray
            // facing north or west starts in the block behind, which for a mirror is its wall: every
            // ray stopped where it began, and the capture kept nothing.
            final double exitX = (arrivalX + 0.5) - ((0.5 - 1.0e-6) * aheadX);
            final double exitZ = (arrivalZ + 0.5) - ((0.5 - 1.0e-6) * aheadZ);
            final int rightX = -aheadZ;
            final int rightZ = aheadX;
            final double reach = depth + 2.0;
            final double step = Math.tan(Math.toRadians(1.0));
            // Three blocks wide, centred on the arrival: a mirror two banners wide sees one column more
            // than its room's, on whichever side its view turns that column to, so a room captured
            // once serves a mirror of either width looking in either way.
            for (double across = -1.4; across <= 1.41; across += 0.35)
            {
                for (double up = 0.1; up < 2.0; up += 0.2)
                {
                    // From this point at the front of the hole, every direction out of its back.
                    for (double sideways = (-1.5 - across) + (step / 2); sideways < (1.5 - across); sideways += step)
                    {
                        for (double upward = -up + (step / 2); upward < (2.0 - up); upward += step)
                        {
                            final double length = Math.sqrt(1.0 + (sideways * sideways) + (upward * upward));
                            ray(seen, exitX + ((across + sideways) * rightX), arrivalY + up + upward,
                                exitZ + ((across + sideways) * rightZ), (aheadX + (sideways * rightX)) / length,
                                upward / length, (aheadZ + (sideways * rightZ)) / length, reach);
                        }
                    }
                }
            }
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

        /** Follows one ray through the box, marking what it passes, until something solid or the edge. */
        private void ray(final BitSet seen, final double ox, final double oy, final double oz,
            final double dx, final double dy, final double dz, final double reach)
        {
            final double[] o = { ox - minX, oy - minY, oz - minZ };
            final double[] d = { dx, dy, dz };
            final int[] c = { (int) Math.floor(o[0]), (int) Math.floor(o[1]), (int) Math.floor(o[2]) };
            final int[] size = { sizeX, sizeY, sizeZ };
            final int[] stepOf = new int[3];
            final double[] tMax = new double[3];
            final double[] tDelta = new double[3];
            for (int axis = 0; axis < 3; axis++)
            {
                stepOf[axis] = (d[axis] > 0) ? 1 : (d[axis] < 0) ? -1 : 0;
                tDelta[axis] = (stepOf[axis] == 0) ? Double.POSITIVE_INFINITY : Math.abs(1.0 / d[axis]);
                final double edge = (stepOf[axis] > 0) ? (c[axis] + 1) : c[axis];
                tMax[axis] = (stepOf[axis] == 0) ? Double.POSITIVE_INFINITY : ((edge - o[axis]) / d[axis]);
            }
            for (double t = 0.0; t < reach;)
            {
                if ((c[0] < 0) || (c[0] >= size[0]) || (c[1] < 0) || (c[1] >= size[1]) || (c[2] < 0) || (c[2] >= size[2]))
                {
                    return;
                }
                final int at = offset(c[0], c[1], c[2], sizeY, sizeZ);
                seen.set(at);
                if (solid.get(at))
                {
                    return;
                }
                final int next = (tMax[0] < tMax[1]) ? ((tMax[0] < tMax[2]) ? 0 : 2) : ((tMax[1] < tMax[2]) ? 1 : 2);
                t = tMax[next];
                c[next] += stepOf[next];
                tMax[next] += tDelta[next];
            }
        }

        /** @return the finished capture, taken now */
        public MirrorCapture build()
        {
            // Sorted by position, the last word on each block winning: a block put and then
            // cleared is air, and a block put twice is what it was put as last. Blocks put in
            // order -- a fill, or a photograph column by column -- need no sorting.
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
            // Seen air, a column at a time, as runs of y.
            final List<Long> airColumns = new ArrayList<>();
            final List<Integer> airFirst = new ArrayList<>();
            final List<Short> airFrom = new ArrayList<>();
            final List<Short> airTo = new ArrayList<>();
            if (seenAir != null)
            {
                for (int dx = 0; dx < sizeX; dx++)
                {
                    for (int dz = 0; dz < sizeZ; dz++)
                    {
                        final int column = offset(dx, 0, dz, sizeY, sizeZ);
                        int runFrom = -1;
                        boolean any = false;
                        for (int dy = 0; dy <= sizeY; dy++)
                        {
                            final boolean air = (dy < sizeY) && seenAir.get(column + dy) && !cleared.get(column + dy);
                            if (air && (runFrom < 0))
                            {
                                runFrom = dy;
                            }
                            else if (!air && (runFrom >= 0))
                            {
                                if (!any)
                                {
                                    airColumns.add((((long) dx) << 20) | dz);
                                    airFirst.add(airFrom.size());
                                    any = true;
                                }
                                airFrom.add((short) runFrom);
                                airTo.add((short) (dy - 1));
                                runFrom = -1;
                            }
                        }
                    }
                }
            }
            airFirst.add(airFrom.size());
            return new MirrorCapture(worldName, hasSky, complete, minX, minY, minZ, sizeX, sizeY, sizeZ,
                System.currentTimeMillis(), names.toArray(new String[0]),
                states.toArray(new BlockData[0]), Arrays.copyOf(outCells, out), Arrays.copyOf(outValues, out),
                airColumns.stream().mapToLong(Long::longValue).toArray(),
                airFirst.stream().mapToInt(Integer::intValue).toArray(), shorts(airFrom), shorts(airTo));
        }

        static short[] shorts(final List<Short> list)
        {
            final short[] array = new short[list.size()];
            for (int i = 0; i < array.length; i++)
            {
                array[i] = list.get(i);
            }
            return array;
        }

        private int offsetOf(final long cell)
        {
            return offset((int) (cell >>> 40), (int) (cell & 0xFFFFF), (int) ((cell >>> 20) & 0xFFFFF), sizeY, sizeZ);
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
        int air = 0;
        for (int i = 0; i < airFrom.length; i++)
        {
            air += (airTo[i] - airFrom[i]) + 1;
        }
        return air;
    }

    /** Whether a block inside the box is air that can be seen, by the column runs. */
    private boolean seenAirAt(final int x, final int y, final int z)
    {
        final int column = Arrays.binarySearch(airColumns, (((long) (x - minX)) << 20) | (z - minZ));
        if (column < 0)
        {
            return false;
        }
        final int dy = y - minY;
        for (int run = airFirst[column]; run < airFirst[column + 1]; run++)
        {
            if ((dy >= airFrom[run]) && (dy <= airTo[run]))
            {
                return true;
            }
        }
        return false;
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
        for (int column = 0; column < airColumns.length; column++)
        {
            final int x = minX + (int) (airColumns[column] >>> 20);
            final int z = minZ + (int) (airColumns[column] & 0xFFFFF);
            for (int run = airFirst[column]; run < airFirst[column + 1]; run++)
            {
                for (int dy = airFrom[run]; dy <= airTo[run]; dy++)
                {
                    kept.at(x, minY + dy, z, true);
                }
            }
        }
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
            out.writeInt(airColumns.length);
            for (int column = 0; column < airColumns.length; column++)
            {
                out.writeLong(airColumns[column]);
                out.writeInt(airFirst[column + 1] - airFirst[column]);
                for (int run = airFirst[column]; run < airFirst[column + 1]; run++)
                {
                    out.writeShort(airFrom[run]);
                    out.writeShort(airTo[run]);
                }
            }
        }
        if (!temp.renameTo(file) && (!file.delete() || !temp.renameTo(file)))
        {
            throw new IOException("could not replace " + file);
        }
    }

    /**
     * Reads a capture back. Block states are made from their names the first time each is
     * asked for, which needs a running server.
     *
     * <p>A file from before captures kept only what could be seen is refused: it is a grid of
     * the whole box, and the mirror it is for takes a fresh capture on the next look.
     *
     * @param file
     *            the file
     * @return the capture
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
            final int airColumnCount = in.readInt();
            if ((airColumnCount < 0) || (airColumnCount > (sizeX * sizeZ)))
            {
                throw new IOException(file + " has an impossible number of columns");
            }
            final long[] airColumns = new long[airColumnCount];
            final int[] airFirst = new int[airColumnCount + 1];
            final List<Short> from = new ArrayList<>();
            final List<Short> to = new ArrayList<>();
            for (int column = 0; column < airColumnCount; column++)
            {
                airColumns[column] = in.readLong();
                airFirst[column] = from.size();
                final int runs = in.readInt();
                if ((runs < 0) || (runs > sizeY))
                {
                    throw new IOException(file + " has an impossible number of runs");
                }
                for (int run = 0; run < runs; run++)
                {
                    from.add(in.readShort());
                    to.add(in.readShort());
                }
            }
            airFirst[airColumnCount] = from.size();
            return new MirrorCapture(worldName, hasSky, complete, minX, minY, minZ, sizeX, sizeY, sizeZ,
                takenAt, names, new BlockData[count], cells, values, airColumns, airFirst,
                Builder.shorts(from), Builder.shorts(to));
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

    /**
     * What a capture holds, one {@code mirror debug} line each.
     *
     * @return the lines
     */
    public java.util.List<String> describeLines()
    {
        return java.util.List.of(
            MirrorText.field("box", worldName + " x " + minX + ".." + (minX + sizeX - 1) + " y " + minY + ".."
                + (minY + sizeY - 1) + " z " + minZ + ".." + (minZ + sizeZ - 1)),
            MirrorText.field("kept", filled() + " blocks and " + seenAir() + " air of " + size() + ", "
                + names.length + " kinds"),
            MirrorText.field("taken", ((System.currentTimeMillis() - takenAt) / 1000L) + "s ago, "
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

    private static int offset(final int dx, final int dy, final int dz, final int sizeY,
        final int sizeZ)
    {
        return (((dx * sizeZ) + dz) * sizeY) + dy;
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
