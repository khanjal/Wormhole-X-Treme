package com.wormhole_xtreme.wormhole.model.mirror;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

/**
 * The air a capture keeps because a viewer can see it, as runs of y a column at a time, every
 * position an offset from the box's corner.
 *
 * <p>The air in a column is a few runs, and a desert at the render distance is a hundred thousand
 * columns rather than tens of millions of blocks of air.
 */
final class MirrorSeenAir
{
    /** Every column with air that can be seen, as {@code (dx << 20) | dz}, ascending. */
    private final long[] columns;
    /** Where each column's runs start in {@link #from} and {@link #to}, with one past the end last. */
    private final int[] first;
    /** Each run's first y, as an offset from the box's floor. */
    private final short[] from;
    /** Each run's last y, likewise. */
    private final short[] to;

    private MirrorSeenAir(final long[] columns, final int[] first, final short[] from, final short[] to)
    {
        this.columns = columns;
        this.first = first;
        this.from = from;
        this.to = to;
    }

    /**
     * The runs of seen air in a box, a column at a time.
     *
     * @param seen
     *            the air seen, by {@code MirrorCapture.Builder} offset; null for none
     * @param cleared
     *            blocks blanked to air, which are entries rather than runs
     * @return the runs
     */
    static MirrorSeenAir of(final BitSet seen, final BitSet cleared, final int sizeX, final int sizeY,
        final int sizeZ)
    {
        final Runs runs = new Runs();
        if (seen != null)
        {
            for (int dx = 0; dx < sizeX; dx++)
            {
                for (int dz = 0; dz < sizeZ; dz++)
                {
                    runs.column((((long) dx) << 20) | dz, seen, cleared, ((dx * sizeZ) + dz) * sizeY, sizeY);
                }
            }
        }
        return runs.done();
    }

    /** Collects runs column by column, in order. */
    private static final class Runs
    {
        private final List<Long> columns = new ArrayList<>();
        private final List<Integer> first = new ArrayList<>();
        private final List<Short> from = new ArrayList<>();
        private final List<Short> to = new ArrayList<>();

        /** Adds one column's runs; {@code base} is its offset at the box's floor. */
        void column(final long column, final BitSet seen, final BitSet cleared, final int base, final int sizeY)
        {
            int runFrom = -1;
            boolean any = false;
            for (int dy = 0; dy <= sizeY; dy++)
            {
                final boolean air = (dy < sizeY) && seen.get(base + dy) && !cleared.get(base + dy);
                if (air && (runFrom < 0))
                {
                    runFrom = dy;
                }
                else if (!air && (runFrom >= 0))
                {
                    if (!any)
                    {
                        columns.add(column);
                        first.add(from.size());
                        any = true;
                    }
                    from.add((short) runFrom);
                    to.add((short) (dy - 1));
                    runFrom = -1;
                }
            }
        }

        MirrorSeenAir done()
        {
            first.add(from.size());
            return new MirrorSeenAir(columns.stream().mapToLong(Long::longValue).toArray(),
                first.stream().mapToInt(Integer::intValue).toArray(), shorts(from), shorts(to));
        }
    }

    private static short[] shorts(final List<Short> list)
    {
        final short[] array = new short[list.size()];
        for (int i = 0; i < array.length; i++)
        {
            array[i] = list.get(i);
        }
        return array;
    }

    /** @return how many blocks of air it holds */
    int count()
    {
        int air = 0;
        for (int i = 0; i < from.length; i++)
        {
            air += (to[i] - from[i]) + 1;
        }
        return air;
    }

    /** Whether a block, as offsets from the box's corner, is air that can be seen. */
    boolean has(final int dx, final int dy, final int dz)
    {
        final int column = Arrays.binarySearch(columns, (((long) dx) << 20) | dz);
        if (column < 0)
        {
            return false;
        }
        for (int run = first[column]; run < first[column + 1]; run++)
        {
            if ((dy >= from[run]) && (dy <= to[run]))
            {
                return true;
            }
        }
        return false;
    }

    /** Hands every block of it to {@code kept}, in world coordinates, a column at a time. */
    void forEach(final int minX, final int minY, final int minZ, final MirrorCapture.Kept kept)
    {
        for (int column = 0; column < columns.length; column++)
        {
            final int x = minX + (int) (columns[column] >>> 20);
            final int z = minZ + (int) (columns[column] & 0xFFFFF);
            for (int run = first[column]; run < first[column + 1]; run++)
            {
                for (int dy = from[run]; dy <= to[run]; dy++)
                {
                    kept.at(x, minY + dy, z, true);
                }
            }
        }
    }

    void write(final DataOutputStream out) throws IOException
    {
        out.writeInt(columns.length);
        for (int column = 0; column < columns.length; column++)
        {
            out.writeLong(columns[column]);
            out.writeInt(first[column + 1] - first[column]);
            for (int run = first[column]; run < first[column + 1]; run++)
            {
                out.writeShort(from[run]);
                out.writeShort(to[run]);
            }
        }
    }

    /**
     * Reads what {@link #write} wrote.
     *
     * @throws IOException
     *             if it is cut short, or holds more than a box of this size could
     */
    static MirrorSeenAir read(final DataInputStream in, final File file, final int sizeX, final int sizeY,
        final int sizeZ) throws IOException
    {
        final int count = in.readInt();
        if ((count < 0) || (count > (sizeX * sizeZ)))
        {
            throw new IOException(file + " has an impossible number of columns");
        }
        final long[] columns = new long[count];
        final int[] first = new int[count + 1];
        final List<Short> from = new ArrayList<>();
        final List<Short> to = new ArrayList<>();
        for (int column = 0; column < count; column++)
        {
            columns[column] = in.readLong();
            first[column] = from.size();
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
        first[count] = from.size();
        return new MirrorSeenAir(columns, first, shorts(from), shorts(to));
    }
}
