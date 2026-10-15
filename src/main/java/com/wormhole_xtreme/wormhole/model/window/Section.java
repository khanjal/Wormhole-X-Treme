package com.wormhole_xtreme.wormhole.model.window;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * One sixteen-block cube of a capture, kept the way Minecraft keeps a chunk section (#525): a
 * palette of the states in it, and an index into that palette for each block it keeps.
 *
 * <p>Blocks are numbered inside the cube x first, then z, then y, as {@code (x << 8) | (z << 4) | y},
 * which is the order a capture's blocks have always been visited in. A cube with many kept blocks
 * holds an index for all 4096 of them, packed at as few bits as its palette needs, with 0 for a
 * block not kept; one with few holds the numbers of the blocks it keeps, ascending, and an index
 * for each. Each is made whichever is smaller. The air a viewer can see in the cube is a bit a
 * block, or nothing at all where it is all air or has none.
 *
 * <p>Immutable once made.
 */
final class Section
{
    /** Blocks along each edge. */
    static final int EDGE = 16;

    /** Blocks in a cube. */
    static final int BLOCKS = EDGE * EDGE * EDGE;

    /** Longs in a bit a block. */
    private static final int AIR_WORDS = BLOCKS / Long.SIZE;

    /** Air seen in every block, shared by every cube that is all seen air; never written to. */
    private static final long[] ALL_AIR = new long[AIR_WORDS];

    /** Air seen in no block, shared by every cube with none; never written to. */
    private static final long[] NO_AIR_SEEN = new long[AIR_WORDS];

    static
    {
        Arrays.fill(ALL_AIR, -1L);
    }

    /** How the air in a cube is written: none, every block, or a bit a block. */
    private static final int NO_AIR = 0;
    private static final int EVERY_AIR = 1;
    private static final int SOME_AIR = 2;

    /** The capture's palette index of each state in this cube. */
    private final short[] palette;
    /** Bits an index is packed at. */
    private final int bits;
    /** The indices, packed; for a full cube one a block, with 0 for none and {@code n} for the palette's {@code n - 1}. */
    private final long[] packed;
    /** The blocks kept, ascending, in step with the indices; null for a full cube. */
    private final short[] positions;
    /** For a full cube, a bit for each of its 256 columns holding an entry, so a walk skips the rest; null for a listed one. */
    private final long[] columnsHeld;
    /** How many blocks it keeps an entry for. */
    private final int entries;
    /** How many of those are not air. */
    private final int filled;
    /** The air that can be seen, a bit a block; {@link #NO_AIR_SEEN} for none, {@link #ALL_AIR} for every block. */
    private final long[] air;
    /** How many blocks of it. */
    private final int airCount;

    private Section(final short[] palette, final int bits, final long[] packed, final short[] positions,
        final int entries, final long[] air)
    {
        this.palette = palette;
        this.bits = bits;
        this.packed = packed;
        this.positions = positions;
        this.entries = entries;
        this.columnsHeld = ((positions == null) && (entries > 0)) ? columnsHeld(packed, bits) : null;
        this.air = canonical(air);
        this.airCount = count(this.air);
        this.filled = filledOf(palette, bits, packed, positions, entries);
    }

    /** A cube's number for a block in it, from the block's world coordinates. */
    static int local(final int x, final int y, final int z)
    {
        return ((x & (EDGE - 1)) << 8) | ((z & (EDGE - 1)) << 4) | (y & (EDGE - 1));
    }

    /** A cube's number for a column in it, as {@link #columnEntries} takes it, from world coordinates. */
    static int column(final int x, final int z)
    {
        return ((x & (EDGE - 1)) << 4) | (z & (EDGE - 1));
    }

    /** @return a block's x in its cube, from its number */
    static int x(final int local)
    {
        return local >>> 8;
    }

    /** @return a block's y in its cube, from its number */
    static int y(final int local)
    {
        return local & (EDGE - 1);
    }

    /** @return a block's z in its cube, from its number */
    static int z(final int local)
    {
        return (local >>> 4) & (EDGE - 1);
    }

    /** Bits that hold every number below {@code values}: never fewer than one. */
    static int bitsFor(final int values)
    {
        return Math.max(1, Integer.SIZE - Integer.numberOfLeadingZeros(values - 1));
    }

    /** Bytes the indices of a full cube take, with a palette of {@code states}. */
    static int fullBytes(final int states)
    {
        return (BLOCKS * bitsFor(states + 1)) / Byte.SIZE;
    }

    /** Bytes the numbers and indices of a cube keeping {@code entries} blocks take, with a palette of {@code states}. */
    static int listedBytes(final int entries, final int states)
    {
        return (entries * Short.BYTES) + (words(entries, bitsFor(states)) * Long.BYTES);
    }

    /** Longs that hold {@code count} values of {@code bits} each. */
    private static int words(final int count, final int bits)
    {
        return (int) ((((long) count * bits) + (Long.SIZE - 1)) / Long.SIZE);
    }

    /** The value at {@code i} of an array packed at {@code bits}, a value allowed to cross from one long into the next. */
    private static int get(final long[] packed, final int bits, final int i)
    {
        final int offset = i * bits;
        final int word = offset >>> 6;
        final int shift = offset & (Long.SIZE - 1);
        long value = packed[word] >>> shift;
        if ((shift + bits) > Long.SIZE)
        {
            value |= packed[word + 1] << (Long.SIZE - shift);
        }
        return (int) (value & ((1L << bits) - 1));
    }

    private static void set(final long[] packed, final int bits, final int i, final int value)
    {
        final int offset = i * bits;
        final int word = offset >>> 6;
        final int shift = offset & (Long.SIZE - 1);
        packed[word] |= ((long) value) << shift;
        if ((shift + bits) > Long.SIZE)
        {
            packed[word + 1] |= ((long) value) >>> (Long.SIZE - shift);
        }
    }

    /** Seen air given as a bit a block, or null for none, as one of the shared arrays where it is none or all. */
    private static long[] canonical(final long[] air)
    {
        if (air == null)
        {
            return NO_AIR_SEEN;
        }
        if ((air == ALL_AIR) || (air == NO_AIR_SEEN))
        {
            return air;
        }
        boolean none = true;
        boolean every = true;
        for (final long word : air)
        {
            none &= word == 0L;
            every &= word == -1L;
        }
        if (none)
        {
            return NO_AIR_SEEN;
        }
        return every ? ALL_AIR : air;
    }

    /** Which columns of a full cube hold an entry, a bit each. */
    private static long[] columnsHeld(final long[] packed, final int bits)
    {
        final long[] held = new long[(EDGE * EDGE) / Long.SIZE];
        for (int local = 0; local < BLOCKS; local++)
        {
            if (get(packed, bits, local) != 0)
            {
                held[local >>> 10] |= 1L << ((local >>> 4) & 63);
            }
        }
        return held;
    }

    private static int count(final long[] air)
    {
        int count = 0;
        for (final long word : air)
        {
            count += Long.bitCount(word);
        }
        return count;
    }

    private static int filledOf(final short[] palette, final int bits, final long[] packed, final short[] positions,
        final int entries)
    {
        if (entries == 0)
        {
            return 0;
        }
        int filled = 0;
        final int count = (positions == null) ? BLOCKS : entries;
        for (int i = 0; i < count; i++)
        {
            final int value = get(packed, bits, i);
            if (positions == null)
            {
                if ((value != 0) && (palette[value - 1] != 0))
                {
                    filled++;
                }
            }
            else if (palette[value] != 0)
            {
                filled++;
            }
        }
        return filled;
    }

    /**
     * Makes a cube from what it keeps.
     *
     * @param locals
     *            the blocks with entries, ascending, from {@code from} to {@code to}
     * @param values
     *            each one's capture palette index, in step
     * @param air
     *            the air seen, a bit a block, or null for none; kept, not copied
     * @param slot
     *            scratch the size of the capture's palette, every element -1, and left so
     * @return the cube, or null if it keeps nothing
     */
    static Section of(final short[] locals, final short[] values, final int from, final int to, final long[] air,
        final int[] slot)
    {
        final int entries = to - from;
        if ((entries == 0) && (canonical(air) == NO_AIR_SEEN))
        {
            return null;
        }
        // The cube's palette, in the order its states are first met.
        short[] palette = new short[Math.min(entries, 16)];
        int states = 0;
        final int[] local = new int[entries];
        for (int i = 0; i < entries; i++)
        {
            final int value = values[from + i] & 0xFFFF;
            if (slot[value] < 0)
            {
                if (states == palette.length)
                {
                    palette = Arrays.copyOf(palette, states * 2);
                }
                slot[value] = states;
                palette[states] = (short) value;
                states++;
            }
            local[i] = slot[value];
        }
        for (int i = 0; i < states; i++)
        {
            slot[palette[i] & 0xFFFF] = -1;
        }
        palette = Arrays.copyOf(palette, states);
        if (entries == 0)
        {
            return new Section(palette, 1, new long[0], new short[0], 0, air);
        }
        if (fullBytes(states) < listedBytes(entries, states))
        {
            final int bits = bitsFor(states + 1);
            final long[] packed = new long[words(BLOCKS, bits)];
            for (int i = 0; i < entries; i++)
            {
                set(packed, bits, locals[from + i], local[i] + 1);
            }
            return new Section(palette, bits, packed, null, entries, air);
        }
        final int bits = bitsFor(states);
        final long[] packed = new long[words(entries, bits)];
        for (int i = 0; i < entries; i++)
        {
            set(packed, bits, i, local[i]);
        }
        return new Section(palette, bits, packed, Arrays.copyOfRange(locals, from, to), entries, air);
    }

    /** @return true if every block's index is held, false if only the blocks kept are listed */
    boolean full()
    {
        return positions == null;
    }

    /** @return how many blocks it keeps an entry for */
    int entries()
    {
        return entries;
    }

    /** @return how many of those are not air */
    int filled()
    {
        return filled;
    }

    /** @return how many blocks of air that can be seen it keeps */
    int airCount()
    {
        return airCount;
    }

    /**
     * The entry for a block.
     *
     * @param local
     *            the block's number in the cube
     * @return its capture palette index, or -1 for no entry
     */
    int value(final int local)
    {
        if (positions == null)
        {
            final int value = get(packed, bits, local);
            return (value == 0) ? -1 : (palette[value - 1] & 0xFFFF);
        }
        final int i = Arrays.binarySearch(positions, (short) local);
        return (i < 0) ? -1 : (palette[get(packed, bits, i)] & 0xFFFF);
    }

    /** Whether a block is air that can be seen. */
    boolean airAt(final int local)
    {
        return (air[local >>> 6] & (1L << (local & 63))) != 0;
    }

    /** Handed each entry of a cube. */
    @FunctionalInterface
    interface Entry
    {
        /**
         * @param local
         *            the block's number in the cube
         * @param value
         *            its capture palette index
         */
        void at(int local, int value);
    }

    /** Hands every entry to {@code entry}, ascending. */
    void forEachEntry(final Entry entry)
    {
        if (positions == null)
        {
            for (int local = 0; (entries > 0) && (local < BLOCKS); local++)
            {
                final int value = get(packed, bits, local);
                if (value != 0)
                {
                    entry.at(local, palette[value - 1] & 0xFFFF);
                }
            }
            return;
        }
        for (int i = 0; i < entries; i++)
        {
            entry.at(positions[i], palette[get(packed, bits, i)] & 0xFFFF);
        }
    }

    /**
     * Hands one column's entries to {@code kept}, upward, in world coordinates.
     *
     * @param column
     *            the column's x and z in the cube, as {@code (x << 4) | z}
     * @param from
     *            for a listed cube, where in its list to start looking; columns are asked for in order
     * @return where the next column starts in the list
     */
    int columnEntries(final int column, final int from, final int x, final int baseY, final int z,
        final Capture.Kept kept)
    {
        final int first = column << 4;
        if (positions == null)
        {
            if ((columnsHeld == null) || ((columnsHeld[column >>> 6] & (1L << (column & 63))) == 0))
            {
                return from;
            }
            for (int y = 0; y < EDGE; y++)
            {
                final int value = get(packed, bits, first + y);
                if (value != 0)
                {
                    kept.at(x, baseY + y, z, palette[value - 1] == 0);
                }
            }
            return from;
        }
        int i = from;
        while ((i < entries) && (positions[i] < first))
        {
            i++;
        }
        while ((i < entries) && (positions[i] < (first + EDGE)))
        {
            kept.at(x, baseY + (positions[i] & (EDGE - 1)), z, palette[get(packed, bits, i)] == 0);
            i++;
        }
        return i;
    }

    /** Hands one column's seen air to {@code kept}, upward, in world coordinates. */
    void columnAir(final int column, final int x, final int baseY, final int z, final Capture.Kept kept)
    {
        if (air == NO_AIR_SEEN)
        {
            return;
        }
        // The column's sixteen bits, lowest first, visited a set bit at a time.
        int inColumn = (int) ((air[column >>> 2] >>> ((column & 3) << 4)) & 0xFFFF);
        while (inColumn != 0)
        {
            kept.at(x, baseY + Integer.numberOfTrailingZeros(inColumn), z, true);
            inColumn &= inColumn - 1;
        }
    }

    /** @return about how many bytes it holds in memory, headers included */
    long bytes()
    {
        // An object header and its fields, then each array's header and contents.
        long bytes = 48L + 16L + (palette.length * 2L) + 16L + (packed.length * 8L);
        if (positions != null)
        {
            bytes += 16L + (positions.length * 2L);
        }
        if (columnsHeld != null)
        {
            bytes += 16L + (columnsHeld.length * 8L);
        }
        if ((air != NO_AIR_SEEN) && (air != ALL_AIR))
        {
            bytes += 16L + (air.length * 8L);
        }
        return bytes;
    }

    /** Writes the cube, after its place in the capture, which the caller writes. */
    void write(final DataOutputStream out) throws IOException
    {
        out.writeShort(entries);
        if (entries > 0)
        {
            out.writeShort(palette.length);
            for (final short value : palette)
            {
                out.writeShort(value);
            }
            out.writeBoolean(positions == null);
            if (positions != null)
            {
                for (final short local : positions)
                {
                    out.writeShort(local);
                }
            }
            for (final long word : packed)
            {
                out.writeLong(word);
            }
        }
        writeAir(out);
    }

    private void writeAir(final DataOutputStream out) throws IOException
    {
        if (air == NO_AIR_SEEN)
        {
            out.writeByte(NO_AIR);
        }
        else if (air == ALL_AIR)
        {
            out.writeByte(EVERY_AIR);
        }
        else
        {
            out.writeByte(SOME_AIR);
            for (final long word : air)
            {
                out.writeLong(word);
            }
        }
    }

    /**
     * Reads what {@link #write} wrote, refusing anything the box could not hold.
     *
     * @param states
     *            how many states the capture's palette has
     * @param inside
     *            which of the cube's blocks lie inside the capture's box
     * @return the cube, or null if it keeps nothing
     * @throws IOException
     *             if it is cut short, or holds what no capture could
     */
    static Section read(final DataInputStream in, final File file, final int states, final Inside inside)
        throws IOException
    {
        final int entries = in.readUnsignedShort();
        if (entries > BLOCKS)
        {
            throw new IOException(file + " has a section of " + entries + " blocks");
        }
        final Section kept = (entries > 0) ? readEntries(in, file, states, inside, entries) : null;
        final long[] air = readAir(in, file, inside);
        if (kept != null)
        {
            return new Section(kept, air);
        }
        return (air == NO_AIR_SEEN) ? null : new Section(new short[0], 1, new long[0], new short[0], 0, air);
    }

    /** The same entries as another cube, with seen air. */
    private Section(final Section kept, final long[] air)
    {
        this.palette = kept.palette;
        this.bits = kept.bits;
        this.packed = kept.packed;
        this.positions = kept.positions;
        this.entries = kept.entries;
        this.columnsHeld = kept.columnsHeld;
        this.filled = kept.filled;
        this.air = canonical(air);
        this.airCount = count(this.air);
    }

    /** Reads a cube's palette and entries, refusing what the box could not hold; the cube has no air yet. */
    private static Section readEntries(final DataInputStream in, final File file, final int states, final Inside inside,
        final int entries) throws IOException
    {
        final int size = in.readUnsignedShort();
        if ((size < 1) || (size > entries))
        {
            throw new IOException(file + " has a section palette of " + size);
        }
        final short[] palette = new short[size];
        for (int i = 0; i < size; i++)
        {
            final int value = in.readUnsignedShort();
            if (value >= states)
            {
                throw new IOException(file + " has a block of state " + value + " of " + states);
            }
            palette[i] = (short) value;
        }
        final boolean full = in.readBoolean();
        final short[] positions = full ? null : readPositions(in, file, entries, inside);
        final int bits = full ? bitsFor(size + 1) : bitsFor(size);
        final long[] packed = new long[words(full ? BLOCKS : entries, bits)];
        for (int i = 0; i < packed.length; i++)
        {
            packed[i] = in.readLong();
        }
        check(file, palette, bits, packed, positions, entries, inside);
        return new Section(palette, bits, packed, positions, entries, NO_AIR_SEEN);
    }

    private static short[] readPositions(final DataInputStream in, final File file, final int entries,
        final Inside inside) throws IOException
    {
        final short[] positions = new short[entries];
        for (int i = 0; i < entries; i++)
        {
            final int local = in.readUnsignedShort();
            if ((local >= BLOCKS) || ((i > 0) && (local <= positions[i - 1])) || !inside.has(local))
            {
                throw new IOException(file + " has a section out of order or outside its box");
            }
            positions[i] = (short) local;
        }
        return positions;
    }

    /** Refuses indices past the palette, a full cube's entries outside the box, or a count that is wrong. */
    private static void check(final File file, final short[] palette, final int bits, final long[] packed,
        final short[] positions, final int entries, final Inside inside) throws IOException
    {
        final int count = (positions == null) ? BLOCKS : entries;
        int found = 0;
        for (int i = 0; i < count; i++)
        {
            final int value = get(packed, bits, i);
            final boolean entry = (positions != null) || (value != 0);
            final int index = (positions == null) ? (value - 1) : value;
            if (entry && ((index >= palette.length) || ((positions == null) && !inside.has(i))))
            {
                throw new IOException(file + " has a section that does not add up");
            }
            if (entry)
            {
                found++;
            }
        }
        if (found != entries)
        {
            throw new IOException(file + " has a section that does not add up");
        }
    }

    private static long[] readAir(final DataInputStream in, final File file, final Inside inside) throws IOException
    {
        final int kind = in.readUnsignedByte();
        if (kind == NO_AIR)
        {
            return NO_AIR_SEEN;
        }
        if ((kind == EVERY_AIR) && inside.whole())
        {
            return ALL_AIR;
        }
        if (kind != SOME_AIR)
        {
            throw new IOException(file + " has a section's air of kind " + kind);
        }
        final long[] air = new long[AIR_WORDS];
        for (int i = 0; i < AIR_WORDS; i++)
        {
            air[i] = in.readLong();
        }
        for (int i = 0; i < AIR_WORDS; i++)
        {
            for (long word = air[i]; word != 0L; word &= word - 1)
            {
                if (!inside.has((i << 6) + Long.numberOfTrailingZeros(word)))
                {
                    throw new IOException(file + " has air outside its box");
                }
            }
        }
        return air;
    }

    /** Which of a cube's blocks lie inside a capture's box: a range along each axis, inclusive. */
    record Inside(int fromX, int toX, int fromY, int toY, int fromZ, int toZ)
    {
        /** Whether a block, by its number in the cube, is inside. */
        boolean has(final int local)
        {
            final int x = local >>> 8;
            final int z = (local >>> 4) & (EDGE - 1);
            final int y = local & (EDGE - 1);
            return (x >= fromX) && (x <= toX) && (y >= fromY) && (y <= toY) && (z >= fromZ) && (z <= toZ);
        }

        /** Whether the whole cube is. */
        boolean whole()
        {
            return (fromX == 0) && (fromY == 0) && (fromZ == 0) && (toX == (EDGE - 1)) && (toY == (EDGE - 1))
                && (toZ == (EDGE - 1));
        }
    }
}
