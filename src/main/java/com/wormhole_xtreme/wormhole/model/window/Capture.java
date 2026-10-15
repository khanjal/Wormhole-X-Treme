package com.wormhole_xtreme.wormhole.model.window;

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
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import com.wormhole_xtreme.wormhole.model.mirror.MirrorText;

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
 * <p>Stored as a palette of block states and an entry per block kept, so a box the render distance
 * across costs what its visible surfaces cost and nothing for the rest. A block inside the box with
 * no entry is <em>buried</em>: nothing at the opening could see it, and every view leaves the real
 * world there. A dense grid of the box came first, and at the render distance it was eighty
 * megabytes a capture. Air that can be seen is kept too, apart from the entries, since air a viewer
 * can see is what a view carves through the real world. The entries and the air are kept by
 * {@code Section}, sixteen blocks a side, as Minecraft keeps a chunk (#525): a sorted list of
 * positions came before, at ten bytes a block.
 *
 * <p>A capture may instead be <em>complete</em>, holding every block that is not air, with no
 * entry meaning air: the form {@code mirror debug save} once wrote, for replaying a view away
 * from the server. Nothing writes one now; the flag is read so an old file is not misread.
 *
 * <p>Immutable once built, so it can be read by a redraw and written to disk at the same time.
 */
public final class Capture
{
    /** The file header, so a file that is not one is refused rather than misread. */
    private static final int MAGIC = 0x4D495257;

    /** 5 keeps sections; 1 and 2 wrote a dense grid, and are taken again. */
    private static final int VERSION = 5;

    /** The version before sections, a sorted list of entries; still read, and written as sections next time. */
    private static final int LISTED_VERSION = 4;

    /** The version before a capture recorded how far it kept; still read, as never cut. */
    private static final int UNCUT_VERSION = 3;

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
    /** Where the sections lie. */
    private final Grid grid;
    /** Every section the box touches, by {@link Grid#index}; null where nothing is kept. */
    private final Section[] sections;
    /** The highest block that is not air in each column, as an offset from the box's floor, or -1; by {@code dx * sizeZ + dz}. */
    private final short[] tops;
    /** How many entries it keeps, how many of those are not air, and how much seen air. */
    private final int entryCount;
    private final int filledCount;
    private final int airCount;
    /** By {@code sx * acrossZ + sz}, the sections in each stack with entries, upward, for a walk. */
    private final int[][] entryStacks;
    /** The same for the sections with seen air. */
    private final int[][] airStacks;
    private BlockData standIn;

    /**
     * How far ahead of its arrival this capture kept, where a cut to fit {@code MOST_KEPT} made
     * that short of its box; -1 for a capture never cut. The box is not shrunk with it, so a view
     * does not ask again for a depth the cut will only take away again.
     */
    private int keptReach = -1;

    private Capture(final String worldName, final boolean hasSky, final boolean complete, final Box box,
        final long takenAt, final Blocks blocks, final Section[] sections)
    {
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
        this.grid = new Grid(box);
        this.sections = sections;
        // The highest block that is not air in each column, which the drawing skips sky above.
        this.tops = new short[sizeX * sizeZ];
        Arrays.fill(tops, (short) -1);
        int entries = 0;
        int filled = 0;
        int seen = 0;
        for (int index = 0; index < sections.length; index++)
        {
            final Section section = sections[index];
            if (section != null)
            {
                entries += section.entries();
                filled += section.filled();
                seen += section.airCount();
                if (section.filled() > 0)
                {
                    raiseTops(section, index);
                }
            }
        }
        this.entryCount = entries;
        this.filledCount = filled;
        this.airCount = seen;
        this.entryStacks = stacks(section -> section.entries() > 0);
        this.airStacks = stacks(section -> section.airCount() > 0);
    }

    /** Raises each column's top to the highest block that is not air in one section. */
    private void raiseTops(final Section section, final int index)
    {
        final int[] corner = grid.corner(index);
        section.forEachEntry((local, value) ->
        {
            if (value != 0)
            {
                final int column = (((corner[0] + Section.x(local)) - minX) * sizeZ) + ((corner[2] + Section.z(local)) - minZ);
                tops[column] = (short) Math.max(tops[column], (corner[1] + Section.y(local)) - minY);
            }
        });
    }

    /** A block's place in the box as one number that sorts by x, then z, then y. */
    private static long cell(final int dx, final int dy, final int dz)
    {
        return (((long) dx) << 40) | (((long) dz) << 20) | dy;
    }

    /**
     * Where a box's sections lie: each sixteen blocks a side, on the world's own chunk sections, so
     * a box that does not start on one is covered by sections that reach past it.
     */
    static final class Grid
    {
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;
        /** The first section along each axis, in sections from the world's origin. */
        private final int fromX;
        private final int fromY;
        private final int fromZ;
        /** How many sections the box spans along each axis. */
        private final int acrossX;
        private final int acrossY;
        private final int acrossZ;

        Grid(final Box box)
        {
            minX = box.minX();
            minY = box.minY();
            minZ = box.minZ();
            maxX = (minX + box.sizeX()) - 1;
            maxY = (minY + box.sizeY()) - 1;
            maxZ = (minZ + box.sizeZ()) - 1;
            fromX = minX >> 4;
            fromY = minY >> 4;
            fromZ = minZ >> 4;
            acrossX = ((maxX >> 4) - fromX) + 1;
            acrossY = ((maxY >> 4) - fromY) + 1;
            acrossZ = ((maxZ >> 4) - fromZ) + 1;
        }

        /** @return how many sections the box touches */
        int length()
        {
            return acrossX * acrossY * acrossZ;
        }

        /** The section a block inside the box is in: x, then z, then y. */
        int index(final int x, final int y, final int z)
        {
            return (((((x >> 4) - fromX) * acrossZ) + ((z >> 4) - fromZ)) * acrossY) + ((y >> 4) - fromY);
        }

        /** @return a section's lowest corner, in world coordinates, {@code {x, y, z}} */
        int[] corner(final int index)
        {
            final int x = index / (acrossY * acrossZ);
            final int z = (index / acrossY) % acrossZ;
            final int y = index % acrossY;
            return new int[] { (fromX + x) << 4, (fromY + y) << 4, (fromZ + z) << 4 };
        }

        /** Which of a section's blocks lie inside the box. */
        Section.Inside inside(final int index)
        {
            final int[] corner = corner(index);
            return new Section.Inside(Math.max(0, minX - corner[0]), Math.min(Section.EDGE - 1, maxX - corner[0]),
                Math.max(0, minY - corner[1]), Math.min(Section.EDGE - 1, maxY - corner[1]),
                Math.max(0, minZ - corner[2]), Math.min(Section.EDGE - 1, maxZ - corner[2]));
        }
    }

    /**
     * Gathers a capture's entries and seen air into sections.
     *
     * <p>Pure: a capture's file is read off the main thread, and this is how it is read.
     */
    private static final class Assembly
    {
        private final Box box;
        private final Grid grid;
        private final int states;
        /** Each section's seen air, a bit a block, made when the first is met. */
        private final long[][] air;

        Assembly(final Box box, final int states)
        {
            this.box = box;
            this.grid = new Grid(box);
            this.states = states;
            this.air = new long[grid.length()][];
        }

        /** Notes a block of air that can be seen, as offsets from the box's corner. */
        void air(final int dx, final int dy, final int dz)
        {
            final int x = box.minX() + dx;
            final int y = box.minY() + dy;
            final int z = box.minZ() + dz;
            final int index = grid.index(x, y, z);
            if (air[index] == null)
            {
                air[index] = new long[Section.BLOCKS / Long.SIZE];
            }
            final int local = Section.local(x, y, z);
            air[index][local >>> 6] |= 1L << (local & 63);
        }

        /**
         * The sections, from every entry and the air noted.
         *
         * @param cells
         *            every entry's place in the box, as {@link #cell}, ascending
         * @param values
         *            each one's palette index, in step
         */
        Section[] sections(final long[] cells, final short[] values)
        {
            // Counted out by section, keeping their order: within a section, x then z then y is the section's own order.
            final int length = grid.length();
            final int[] start = new int[length + 1];
            final int[] in = new int[cells.length];
            final short[] locals = new short[cells.length];
            for (int i = 0; i < cells.length; i++)
            {
                final int x = box.minX() + (int) (cells[i] >>> 40);
                final int y = box.minY() + (int) (cells[i] & 0xFFFFF);
                final int z = box.minZ() + (int) ((cells[i] >>> 20) & 0xFFFFF);
                in[i] = grid.index(x, y, z);
                locals[i] = (short) Section.local(x, y, z);
                start[in[i] + 1]++;
            }
            for (int index = 0; index < length; index++)
            {
                start[index + 1] += start[index];
            }
            final int[] next = Arrays.copyOf(start, length);
            final short[] sortedLocals = new short[cells.length];
            final short[] sortedValues = new short[cells.length];
            for (int i = 0; i < cells.length; i++)
            {
                final int to = next[in[i]];
                next[in[i]] = to + 1;
                sortedLocals[to] = locals[i];
                sortedValues[to] = values[i];
            }
            final int[] slot = new int[states];
            Arrays.fill(slot, -1);
            final Section[] sections = new Section[length];
            for (int index = 0; index < length; index++)
            {
                sections[index] = Section.of(sortedLocals, sortedValues, start[index], start[index + 1], air[index], slot);
            }
            return sections;
        }
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
     * @param width
     *            how wide a hole the capture is seen through
     * @param height
     *            how tall
     */
    public record Arrival(int x, int y, int z, int aheadX, int aheadZ, int width, int height)
    {
        /** Seen through a mirror's hole: three wide, so either width of mirror is served, and two tall. */
        public Arrival(final int x, final int y, final int z, final int aheadX, final int aheadZ)
        {
            this(x, y, z, aheadX, aheadZ, 3, 2);
        }
    }

    /** The palette, as a capture is made from it. */
    private static final class Blocks
    {
        private final String[] names;
        private final BlockData[] states;

        Blocks(final String[] names, final BlockData[] states)
        {
            this.names = names;
            this.states = states;
        }
    }

    /**
     * A capture's entries before they are put in sections: one a block, sorted by position, as
     * {@code Builder.build} finds them and as a version 4 file held them.
     */
    static final class Entries
    {
        /** Every entry's place in the box, as {@link #cell}, ascending. */
        final long[] cells;
        /** Each entry's palette index, in step. */
        final short[] values;

        Entries(final long[] cells, final short[] values)
        {
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
        private boolean lastLeafy;
        /** Every block that is not air. */
        private final BitSet filled;
        /** Every block that hides what is behind it. */
        private final BitSet solid;
        /** Blocks blanked to air by {@link #clear}, which {@link #put} then leaves alone. */
        private final BitSet cleared;
        /** Every block of water, which a ray sees through for {@link #WATER_SIGHT} blocks and no further. */
        private final BitSet murky;
        /** Every block of leaves, which a ray sees through for {@link #LEAF_SIGHT} blocks and no further. */
        private final BitSet leafy;
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
            this.leafy = new BitSet(volume);
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
                    lastLeafy = isLeaves(data);
                }
                filled.set(at);
                solid.set(at, lastOccludes);
                murky.set(at, lastMurky);
                leafy.set(at, lastLeafy);
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
                lastLeafy = (lastIndex != 0) && isLeaves(data);
            }
            filled.set(at, lastIndex != 0);
            solid.set(at, lastOccludes);
            murky.set(at, lastMurky);
            leafy.set(at, lastLeafy);
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
         * Whether a block is leaves, which can be seen through a few layers deep, but no more.
         *
         * <p>By name rather than {@code Tag.LEAVES}, which needs a live registry, so every kind of
         * leaves any version adds is counted without being listed.
         */
        private static boolean isLeaves(final BlockData data)
        {
            // valueOf: a stand-in block with no material reads as "null", not as leaves.
            return String.valueOf(data.getMaterial()).endsWith("_LEAVES");
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
                leafy.clear(at);
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

        /**
         * How many blocks of leaves a ray sees through before they hide the rest.
         *
         * <p>Bukkit counts no leaves as occluding, so a ray through a forest went on through every
         * crown in its way and a view onto one kept the whole canopy, though nobody can see more
         * than a few trees in. Past the last leaf a ray reaches, the real world shows through the
         * gaps in it, which is why this is not smaller.
         */
        static final int LEAF_SIGHT = 6;

        /**
         * The widest a gate capture's rays are spread, in degrees between neighbours (#516).
         *
         * <p>Spread with the hole alone, a Massive gate's rays were seven degrees apart, and a flat
         * floor 64 blocks off kept under half its blocks. With the grids staggered ({@link #STAGGER}),
         * two and a half kept every block of a wall and a floor out to 160 through every opening from
         * five to eighteen square. Wider, some sizes began to lose a few: at four, a twentieth of a wall
         * 160 blocks off through Grand's opening. A mirror's is one.
         */
        static final double WIDEST_STEP = 2.5;

        /**
         * How far each point's grid of directions is shifted, across and up, as a share of a step:
         * the plastic number's two fractions, so ninety points' grids fill in between each other.
         *
         * <p>Every point's grid used to start half a step in. Lined up like that they fell on the
         * same few directions, and the gaps between those were what a far wall or floor lost: a Large
         * gate's capture kept 40% of a wall 160 blocks off, and a Grand gate's three quarters of the
         * floor 24 out.
         */
        static final double[] STAGGER = { 0.7548776662466927, 0.5698402909980532 };

        /** Asked before each start point's rays whether the capture is still wanted; never stopped unless set. */
        private BooleanSupplier stopped = () -> false;

        /**
         * Has the sift give up between one start point's rays and the next once a capture is no longer
         * wanted, by throwing {@link CancellationException}.
         *
         * <p>A gate's sift over open sky runs a minute and a half. A gate removed, or the plugin disabled,
         * left it computing all that time for nobody; a /reload, in the old classloader.
         *
         * @param stop
         *            true once the capture is not wanted
         */
        public void stopWhen(final BooleanSupplier stop)
        {
            stopped = stop;
        }

        /** Gives up the sift if nobody wants the capture any more. */
        private void stopIfUnwanted()
        {
            if (stopped.getAsBoolean())
            {
                throw new CancellationException("the capture is no longer wanted");
            }
        }

        /** How far into its first step a mirror's every grid of directions starts: half a step, as it always has. */
        static final double MIRROR_SHIFT = 0.5;

        /**
         * @return how many cells this would keep, as it stands: blocks, and the air seen among them
         *         that a view carves through the real world
         */
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
         * fit, the way a view is cut shallower until it fits ({@code Windows.MOST_FIXED}).
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
         * through, however close the eye -- a degree apart through a mirror's hole, and at most
         * {@link #WIDEST_STEP} through a gate's, with each point's directions {@link #STAGGER staggered}
         * so the points fill in between each other at the far end of the depth; each followed a
         * block at a time until it meets something that hides what is behind it, or leaves the box,
         * or passes the depth. Every block a
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
            // The opening is a hole of the arrival's width and height and a block deep: a mirror's
            // three by two, a gate's eighteen square. A line of sight goes in at its front and out at
            // its back, so what can be seen is bounded by the hole's own shape: nothing steeper than
            // its width sideways or its height up per block in. The back of the hole is the back of the block behind the arrival
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
            // A bigger hole lets through more directions from each point; the rays are spread wider,
            // but never past WIDEST_STEP, and a gate's grids are staggered. A mirror's are as they were.
            final double step = Math.tan(Math.toRadians(Math.min(WIDEST_STEP,
                Math.max(1.0, Math.sqrt((from.width() * from.height()) / 6.0)))));
            int point = 0;
            // Filled afresh for each ray, which only reads them.
            final double[] origin = new double[3];
            final double[] direction = new double[3];
            // A mirror's is three blocks wide, centred on the arrival: a mirror two banners wide sees
            // one column more than its room's, on whichever side its view turns that column to, so a
            // room captured once serves a mirror of either width looking in either way. An even-width
            // gate's arrival is left of its middle, so its hole sits half a block right.
            final double half = from.width() / 2.0;
            final double centre = ((from.width() - 1) % 2) / 2.0;
            final double spacing = (from.width() == 3) ? 0.35 : ((half - 0.1) / 4);
            final double rise = (from.height() == 2) ? 0.2 : (from.height() / 10.0);
            for (int column = -4; column <= 4; column++)
            {
                final double across = centre + (column * spacing);
                for (double up = rise / 2; up < from.height(); up += rise)
                {
                    stopIfUnwanted();
                    point++;
                    final double shiftAcross = shiftFor(from, point, 0);
                    final double shiftUp = shiftFor(from, point, 1);
                    // From this point at the front of the hole, every direction out of its back.
                    for (double sideways = ((centre - half) - across) + (step * shiftAcross); sideways < ((centre + half) - across); sideways += step)
                    {
                        for (double upward = -up + (step * shiftUp); upward < (from.height() - up); upward += step)
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

        /**
         * How far into its first step one point's grid of directions starts, as a share of a step.
         *
         * @param from
         *            the arrival, whose hole says whether it is a mirror's
         * @param point
         *            which point across the hole, counted from one
         * @param axis
         *            0 across, 1 up
         * @return {@link #MIRROR_SHIFT} through a mirror's three-by-two hole, as it always was;
         *         {@link #gateShift} through any other
         */
        static double shiftFor(final Arrival from, final int point, final int axis)
        {
            return ((from.width() == 3) && (from.height() == 2)) ? MIRROR_SHIFT : gateShift(point, axis);
        }

        /**
         * A gate's point's own share of a step, from {@link #STAGGER}.
         *
         * @param point
         *            which point across the hole, counted from one
         * @param axis
         *            0 across, 1 up
         * @return between 0 and 1
         */
        static double gateShift(final int point, final int axis)
        {
            return (0.5 + (STAGGER[axis] * point)) % 1.0;
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

        /**
         * Whether a ray ends at a block: one that hides what is behind it, or the last of as much
         * water or leaves as can be seen through.
         *
         * @param through
         *            {@code {water, leaves}} seen through so far along this ray, counted on
         */
        private boolean ends(final int at, final int[] through)
        {
            return solid.get(at) || (murky.get(at) && (++through[0] > WATER_SIGHT))
                || (leafy.get(at) && (++through[1] > LEAF_SIGHT));
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
            // Blocks of water and of leaves seen through so far, for when either ends the ray.
            final int[] through = new int[2];
            double t = 0.0;
            while (t < reach)
            {
                if ((c[0] < 0) || (c[0] >= size[0]) || (c[1] < 0) || (c[1] >= size[1]) || (c[2] < 0) || (c[2] >= size[2]))
                {
                    return;
                }
                final int at = offset(c[0], c[1], c[2], sizeY, sizeZ);
                seen.set(at);
                if (ends(at, through))
                {
                    return;
                }
                final int next = soonest(tMax);
                t = tMax[next];
                c[next] += stepOf[next];
                tMax[next] += tDelta[next];
            }
        }

        /** How far ahead the sift kept, where it was cut short of the box; -1 if not. */
        private int keptReach = -1;

        /**
         * Records that the sift was cut short of the box, to fit.
         *
         * @param reach
         *            how far ahead of the arrival it kept
         */
        void keptReach(final int reach)
        {
            keptReach = reach;
        }

        /** @return the finished capture, taken now */
        public Capture build()
        {
            return build(System.currentTimeMillis());
        }

        /**
         * The finished capture, as though taken at a given time: for a test of what an old one does.
         *
         * @param takenAt
         *            when it counts as taken, in milliseconds
         * @return the capture
         */
        Capture build(final long takenAt)
        {
            final Box box = new Box(minX, minY, minZ, sizeX, sizeY, sizeZ);
            final Assembly assembly = new Assembly(box, names.size());
            final BitSet air = airSeen();
            for (int at = (air == null) ? -1 : air.nextSetBit(0); at >= 0; at = air.nextSetBit(at + 1))
            {
                assembly.air(at / (sizeY * sizeZ), at % sizeY, (at / sizeY) % sizeZ);
            }
            final Entries entries = entries();
            final Capture capture = new Capture(worldName, hasSky, complete, box, takenAt,
                new Blocks(names.toArray(new String[0]), states.toArray(new BlockData[0])),
                assembly.sections(entries.cells, entries.values));
            capture.keptReach = keptReach;
            return capture;
        }

        /**
         * The air a view can see, less any block blanked to air, which is an entry instead.
         *
         * @return by offset, a bit a block; null for none
         */
        BitSet airSeen()
        {
            if (seenAir == null)
            {
                return null;
            }
            final BitSet air = (BitSet) seenAir.clone();
            air.andNot(cleared);
            return air;
        }

        /** Every block's entry, sorted by position, the last word on each block winning. */
        Entries entries()
        {
            // A block put and then cleared is air, and a block put twice is what it was put as last.
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
            return new Entries(Arrays.copyOf(outCells, out), Arrays.copyOf(outValues, out));
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
        return entryCount + airCount;
    }

    /** @return how many blocks of air that can be seen it keeps */
    public int seenAir()
    {
        return airCount;
    }

    /** Whether a block inside the box is air that can be seen. */
    private boolean seenAirAt(final int x, final int y, final int z)
    {
        final Section section = sections[grid.index(x, y, z)];
        return (section != null) && section.airAt(Section.local(x, y, z));
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

    /** The palette index of a block inside the box, or a negative number for no entry. */
    private int entry(final int x, final int y, final int z)
    {
        final Section section = sections[grid.index(x, y, z)];
        return (section == null) ? -1 : section.value(Section.local(x, y, z));
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
                return state(0);
            }
            if (standIn == null)
            {
                standIn = Bukkit.createBlockData(Material.STONE);
            }
            return standIn;
        }
        return state(entry);
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
        return (entry < 0) ? (complete || seenAirAt(x, y, z)) : (entry == 0);
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
        return minY + tops[(dx * sizeZ) + dz];
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
     * Visits every block kept, in order of position, x then z then y: every entry, then the seen air.
     *
     * @param kept
     *            handed each one; {@link #at} says what it is
     */
    public void forEachKept(final Kept kept)
    {
        // Where each listed section's next column starts in its list, as its columns are met in order.
        walk(entryStacks, new int[sections.length], kept);
        walk(airStacks, null, kept);
    }

    /**
     * Visits every column of the box that has a section in a stack, x then z, and in each column
     * those sections, upward: their entries, or with no {@code next} their seen air.
     *
     * @param stacks
     *            by {@code sx * acrossZ + sz}, the sections to visit in each, upward
     * @param next
     *            where each listed section's next column starts; null for the air
     */
    private void walk(final int[][] stacks, final int[] next, final Kept kept)
    {
        for (int x = minX; x < (minX + sizeX); x++)
        {
            final int across = ((x >> 4) - grid.fromX) * grid.acrossZ;
            for (int sz = 0; sz < grid.acrossZ; sz++)
            {
                final int[] stack = stacks[across + sz];
                if (stack.length > 0)
                {
                    final int first = (grid.fromZ + sz) << 4;
                    for (int z = Math.max(minZ, first); z <= Math.min(grid.maxZ, first + (Section.EDGE - 1)); z++)
                    {
                        columnKept(x, z, stack, next, kept);
                    }
                }
            }
        }
    }

    /** Visits one column of a stack's sections, upward. */
    private void columnKept(final int x, final int z, final int[] stack, final int[] next, final Kept kept)
    {
        final int column = Section.column(x, z);
        for (final int index : stack)
        {
            final int baseY = (grid.fromY + (index % grid.acrossY)) << 4;
            if (next == null)
            {
                sections[index].columnAir(column, x, baseY, z, kept);
            }
            else
            {
                next[index] = sections[index].columnEntries(column, next[index], x, baseY, z, kept);
            }
        }
    }

    /**
     * For each stack of sections one above another, by {@code sx * acrossZ + sz}, those of its sections
     * that pass {@code holds}, upward: so a walk visits only columns with something to visit.
     */
    /** A stack with nothing to visit, shared. */
    private static final int[] NO_SECTIONS = new int[0];

    private int[][] stacks(final Predicate<Section> holds)
    {
        final int[][] stacks = new int[grid.acrossX * grid.acrossZ][];
        final int[] found = new int[grid.acrossY];
        for (int stack = 0; stack < stacks.length; stack++)
        {
            int count = 0;
            for (int sy = 0; sy < grid.acrossY; sy++)
            {
                final int index = (stack * grid.acrossY) + sy;
                if ((sections[index] != null) && holds.test(sections[index]))
                {
                    found[count] = index;
                    count++;
                }
            }
            stacks[stack] = (count == 0) ? NO_SECTIONS : Arrays.copyOf(found, count);
        }
        return stacks;
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
        makeFolder(parent);
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
            out.writeInt(keptReach);
            out.writeInt(names.length);
            for (final String name : names)
            {
                writeString(out, name);
            }
            int held = 0;
            for (final Section section : sections)
            {
                held += (section == null) ? 0 : 1;
            }
            out.writeInt(held);
            for (int index = 0; index < sections.length; index++)
            {
                if (sections[index] != null)
                {
                    out.writeInt(index);
                    sections[index].write(out);
                }
            }
        }
        try
        {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        catch (final IOException refused)
        {
            // Windows refuses to replace a file being read; the temporary one would be left for good.
            Files.deleteIfExists(temp.toPath());
            throw refused;
        }
    }

    /**
     * Makes the folder a capture is written into, if it is not there yet.
     *
     * <p>Two captures finishing together can both find it missing; the one whose {@code mkdirs}
     * loses the race gets false although the folder now exists, so it is looked for again.
     *
     * @throws IOException
     *             if there is still no folder
     */
    static void makeFolder(final File folder) throws IOException
    {
        if ((folder != null) && !folder.isDirectory() && !folder.mkdirs() && !folder.isDirectory())
        {
            throw new IOException("could not create " + folder);
        }
    }

    /**
     * Reads a capture back. Block states are made from their names the first time each is
     * asked for, which needs a running server.
     *
     * <p>A file from before captures kept only what could be seen is refused: it is a grid of
     * the whole box, and the mirror it is for takes a fresh capture on the next look. One from
     * before sections, a sorted list, is read into sections. Pure, since it runs off the main thread.
     *
     * @throws IOException
     *             if the file is missing, not a capture, of an earlier kind, or cut short
     */
    public static Capture load(final File file) throws IOException
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
            if ((version != VERSION) && (version != LISTED_VERSION) && (version != UNCUT_VERSION))
            {
                throw new IOException(file + " is capture version " + version + ", not " + VERSION
                    + "; it will be taken again");
            }
            final String worldName = readString(in);
            final boolean hasSky = in.readBoolean();
            final boolean complete = in.readBoolean();
            final Box box = new Box(in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt());
            final long takenAt = in.readLong();
            final int kept = (version == UNCUT_VERSION) ? -1 : in.readInt();
            final int count = in.readInt();
            if ((count < 1) || (count > MOST_STATES) || !possible(box))
            {
                throw new IOException(file + " has an impossible shape");
            }
            final String[] names = new String[count];
            for (int i = 0; i < count; i++)
            {
                names[i] = readString(in);
            }
            final Section[] sections = (version == VERSION) ? readSections(in, file, box, count)
                : readListed(in, file, box, count);
            final Capture capture = new Capture(worldName, hasSky, complete, box, takenAt,
                new Blocks(names, new BlockData[count]), sections);
            capture.keptReach = kept;
            return capture;
        }
    }

    /** Past this along any axis a box is no capture's; one at the render distance is about 325. */
    private static final int MOST_ACROSS = 1 << 14;

    /**
     * Past this many columns a box is no capture's, and its column tops alone would be megabytes. The
     * biggest is a reach of 162 each way, 325 by 325, about 106 thousand; this is ten times that.
     */
    private static final int MOST_COLUMNS = 1 << 20;

    /** Past this from the origin no block is in any world: the world border is thirty million out. */
    private static final int MOST_FAR = 1 << 26;

    /** Whether a box read from a file is one a capture could have, so nothing is made for one that is not. */
    private static boolean possible(final Box box)
    {
        return (box.sizeX() >= 1) && (box.sizeY() >= 1) && (box.sizeZ() >= 1) && (box.sizeX() <= MOST_ACROSS)
            && (box.sizeY() <= MOST_ACROSS) && (box.sizeZ() <= MOST_ACROSS)
            && (((long) box.sizeX() * box.sizeZ()) <= MOST_COLUMNS)
            && (((long) box.sizeX() * box.sizeY() * box.sizeZ()) <= Integer.MAX_VALUE)
            && near(box.minX()) && near(box.minY()) && near(box.minZ());
    }

    /** Whether a box's corner is within {@link #MOST_FAR}, so its far edge cannot wrap round. */
    private static boolean near(final int min)
    {
        return (min >= -MOST_FAR) && (min <= MOST_FAR);
    }

    /** Reads the sections a version 5 file holds, each after its place in the box's grid. */
    private static Section[] readSections(final DataInputStream in, final File file, final Box box, final int states)
        throws IOException
    {
        final Grid grid = new Grid(box);
        final Section[] sections = new Section[grid.length()];
        final int held = in.readInt();
        if ((held < 0) || (held > sections.length))
        {
            throw new IOException(file + " has an impossible number of sections");
        }
        int last = -1;
        for (int i = 0; i < held; i++)
        {
            final int index = in.readInt();
            if ((index <= last) || (index >= sections.length))
            {
                throw new IOException(file + " has its sections out of order");
            }
            sections[index] = Section.read(in, file, states, grid.inside(index));
            last = index;
        }
        return sections;
    }

    /** How many entries of a version 3 or 4 file room is made for before any is read. */
    private static final int FIRST_READ = 1 << 16;

    /**
     * Reads what a version 3 or 4 file holds, a sorted list of entries and the seen air a column at
     * a time, into sections: it is written as sections the next time it is saved.
     */
    private static Section[] readListed(final DataInputStream in, final File file, final Box box, final int states)
        throws IOException
    {
        final int volume = box.sizeX() * box.sizeY() * box.sizeZ();
        final int entries = in.readInt();
        if ((entries < 0) || (entries > volume))
        {
            throw new IOException(file + " has an impossible number of blocks");
        }
        // Grown as they are read, so a count the file never finishes is an end of file, not an array that size.
        long[] cells = new long[Math.min(entries, FIRST_READ)];
        short[] values = new short[cells.length];
        for (int i = 0; i < entries; i++)
        {
            if (i == cells.length)
            {
                cells = Arrays.copyOf(cells, (int) Math.min(entries, 2L * i));
                values = Arrays.copyOf(values, cells.length);
            }
            cells[i] = in.readLong();
            values[i] = in.readShort();
            if ((i > 0) && (cells[i] <= cells[i - 1]))
            {
                throw new IOException(file + " is out of order");
            }
            if (!insideBox(cells[i], box) || ((values[i] & 0xFFFF) >= states))
            {
                throw new IOException(file + " has a block outside its box or its palette");
            }
        }
        final Assembly assembly = new Assembly(box, states);
        readRuns(in, file, box, assembly);
        return assembly.sections(cells, values);
    }

    /** Whether a {@link #cell} read from a file is inside the box. */
    private static boolean insideBox(final long cell, final Box box)
    {
        return ((cell >>> 40) < box.sizeX()) && (((cell >>> 20) & 0xFFFFF) < box.sizeZ()) && ((cell & 0xFFFFF) < box.sizeY());
    }

    /** Reads the seen air a version 3 or 4 file holds, as runs of y a column at a time. */
    private static void readRuns(final DataInputStream in, final File file, final Box box, final Assembly assembly)
        throws IOException
    {
        final int columns = in.readInt();
        if ((columns < 0) || (columns > (box.sizeX() * box.sizeZ())))
        {
            throw new IOException(file + " has an impossible number of columns");
        }
        for (int i = 0; i < columns; i++)
        {
            final long column = in.readLong();
            final int runs = in.readInt();
            if ((runs < 0) || (runs > box.sizeY()) || ((column >>> 20) >= box.sizeX()) || ((column & 0xFFFFF) >= box.sizeZ()))
            {
                throw new IOException(file + " has an impossible number of runs");
            }
            for (int run = 0; run < runs; run++)
            {
                readRun(in, file, box, assembly, column);
            }
        }
    }

    /** Reads one run of seen air in a column, as {@code (dx << 20) | dz}. */
    private static void readRun(final DataInputStream in, final File file, final Box box, final Assembly assembly,
        final long column) throws IOException
    {
        final int from = in.readShort();
        final int to = in.readShort();
        if ((from < 0) || (to < from) || (to >= box.sizeY()))
        {
            throw new IOException(file + " has a run of air outside its box");
        }
        for (int dy = from; dy <= to; dy++)
        {
            assembly.air((int) (column >>> 20), dy, (int) (column & 0xFFFFF));
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
        return names[entry];
    }

    /** @return how many blocks kept are not air */
    public int filled()
    {
        return filledCount;
    }

    /**
     * About how many bytes the capture's blocks take in memory: its sections, their grid and the
     * column tops, headers included; not the palette, which is the same whatever holds the blocks.
     *
     * @return bytes
     */
    long bytes()
    {
        long bytes = 16L + (sections.length * 4L) + 16L + (tops.length * 2L);
        for (final Section section : sections)
        {
            bytes += (section == null) ? 0L : section.bytes();
        }
        for (final int[][] stacks : new int[][][] { entryStacks, airStacks })
        {
            bytes += 16L + (stacks.length * 4L);
            for (final int[] stack : stacks)
            {
                bytes += 16L + (stack.length * 4L);
            }
        }
        return bytes;
    }

    /**
     * @param full
     *            true to count the sections holding an index for every block, false those listing the blocks they keep
     * @return how many sections of that kind it holds
     */
    int sections(final boolean full)
    {
        int count = 0;
        for (final Section section : sections)
        {
            if ((section != null) && (section.entries() > 0) && (section.full() == full))
            {
                count++;
            }
        }
        return count;
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
    public long secondsOld()
    {
        return (System.currentTimeMillis() - takenAt) / 1000L;
    }

    /** @return how far ahead of its arrival this capture kept, where a cut made that short of its box; -1 if never cut */
    int keptReach()
    {
        return keptReach;
    }

    /**
     * What a capture holds, one {@code mirror debug} line each.
     */
    public List<String> describeLines()
    {
        return List.of(
            MirrorText.field("box", worldName + " x " + minX + ".." + (minX + sizeX - 1) + " y " + minY + ".."
                + (minY + sizeY - 1) + " z " + minZ + ".." + (minZ + sizeZ - 1)),
            MirrorText.field("kept", filled() + " blocks and " + seenAir() + " air of " + size() + ", "
                + names.length + " kinds"),
            MirrorText.field("taken", secondsOld() + "s ago, "
                + (hasSky ? "sky" : "no sky") + (complete ? ", complete" : "")));
    }

    /** The state for an index, made from its name the first time. */
    private BlockData state(final int index)
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
