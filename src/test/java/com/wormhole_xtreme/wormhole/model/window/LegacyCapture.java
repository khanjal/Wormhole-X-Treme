package com.wormhole_xtreme.wormhole.model.window;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * How a capture kept its blocks before sections (#525): one sorted list of positions, a palette
 * index in step, and the seen air as runs. Copied from {@code Capture} as it was, for its answers
 * and its file format alone.
 *
 * <p>A reference, not a second implementation to keep in step: the sectioned capture has to answer
 * every question exactly as this did for the same blocks, and still read the version 3 and 4 files
 * this writes.
 */
final class LegacyCapture
{
    private final Capture.Box box;
    private final boolean complete;
    private final String[] names;
    private final long[] cells;
    private final short[] values;
    private final long[] columns;
    private final short[] tops;
    private final LegacySeenAir air;

    /**
     * @param entries
     *            what {@code Capture.Builder.entries} found
     * @param seenAir
     *            what {@code Capture.Builder.airSeen} found
     */
    LegacyCapture(final Capture.Box box, final boolean complete, final List<String> names,
        final Capture.Entries entries, final BitSet seenAir)
    {
        this.box = box;
        this.complete = complete;
        this.names = names.toArray(new String[0]);
        this.cells = entries.cells;
        this.values = entries.values;
        this.air = LegacySeenAir.of(seenAir, new BitSet(), box.sizeX(), box.sizeY(), box.sizeZ());
        final Map<Long, Integer> highest = new HashMap<>();
        for (int i = 0; i < cells.length; i++)
        {
            if (values[i] != 0)
            {
                highest.merge(cells[i] >>> 20, (int) (cells[i] & 0xFFFFF), Math::max);
            }
        }
        columns = highest.keySet().stream().mapToLong(Long::longValue).sorted().toArray();
        tops = new short[columns.length];
        for (int i = 0; i < columns.length; i++)
        {
            tops[i] = highest.get(columns[i]).shortValue();
        }
    }

    private static long cell(final int dx, final int dy, final int dz)
    {
        return (((long) dx) << 40) | (((long) dz) << 20) | dy;
    }

    boolean contains(final int x, final int y, final int z)
    {
        return (x >= box.minX()) && (x < (box.minX() + box.sizeX())) && (y >= box.minY())
            && (y < (box.minY() + box.sizeY())) && (z >= box.minZ()) && (z < (box.minZ() + box.sizeZ()));
    }

    private int entry(final int x, final int y, final int z)
    {
        return Arrays.binarySearch(cells, cell(x - box.minX(), y - box.minY(), z - box.minZ()));
    }

    private boolean seenAirAt(final int x, final int y, final int z)
    {
        return air.has(x - box.minX(), y - box.minY(), z - box.minZ());
    }

    boolean isAir(final int x, final int y, final int z)
    {
        if (!contains(x, y, z))
        {
            return true;
        }
        final int entry = entry(x, y, z);
        return (entry < 0) ? (complete || seenAirAt(x, y, z)) : (values[entry] == 0);
    }

    boolean isBuried(final int x, final int y, final int z)
    {
        return !complete && contains(x, y, z) && (entry(x, y, z) < 0) && !seenAirAt(x, y, z);
    }

    String nameAt(final int x, final int y, final int z)
    {
        if (!contains(x, y, z))
        {
            return "outside";
        }
        final int entry = entry(x, y, z);
        if (entry < 0)
        {
            return (complete || seenAirAt(x, y, z)) ? names[0] : Capture.BURIED;
        }
        return names[values[entry]];
    }

    int top(final int x, final int z)
    {
        final int dx = x - box.minX();
        final int dz = z - box.minZ();
        if ((dx < 0) || (dx >= box.sizeX()) || (dz < 0) || (dz >= box.sizeZ()))
        {
            return box.minY() - 1;
        }
        final int column = Arrays.binarySearch(columns, (((long) dx) << 20) | dz);
        return (column < 0) ? (box.minY() - 1) : (box.minY() + tops[column]);
    }

    int kept()
    {
        return cells.length + air.count();
    }

    int seenAir()
    {
        return air.count();
    }

    int filled()
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

    void forEachKept(final Capture.Kept kept)
    {
        for (int i = 0; i < cells.length; i++)
        {
            final long cell = cells[i];
            kept.at(box.minX() + (int) (cell >>> 40), box.minY() + (int) (cell & 0xFFFFF),
                box.minZ() + (int) ((cell >>> 20) & 0xFFFFF), values[i] == 0);
        }
        air.forEach(box.minX(), box.minY(), box.minZ(), kept);
    }

    /** Every block {@link #forEachKept} visits, in order, seen air marked, as {@code CaptureTest.visits} lists them. */
    List<String> visits()
    {
        final List<String> visited = new ArrayList<>();
        forEachKept((x, y, z, isAir) -> visited.add(x + "," + y + "," + z + (isAir ? " air" : "")));
        return visited;
    }

    /** Bytes its arrays took, which is what the sections replace. */
    long bytes()
    {
        return (cells.length * 10L) + (columns.length * 10L) + air.bytes();
    }

    /**
     * Writes the file a build before sections wrote.
     *
     * @param version
     *            4, or 3 for the one before a capture recorded how far it kept
     */
    void save(final File file, final int version, final String worldName, final boolean hasSky, final long takenAt,
        final int keptReach) throws IOException
    {
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(file.toPath()))))
        {
            out.writeInt(0x4D495257);
            out.writeInt(version);
            writeString(out, worldName);
            out.writeBoolean(hasSky);
            out.writeBoolean(complete);
            out.writeInt(box.minX());
            out.writeInt(box.minY());
            out.writeInt(box.minZ());
            out.writeInt(box.sizeX());
            out.writeInt(box.sizeY());
            out.writeInt(box.sizeZ());
            out.writeLong(takenAt);
            if (version != 3)
            {
                out.writeInt(keptReach);
            }
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
    }

    private static void writeString(final DataOutputStream out, final String value) throws IOException
    {
        final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }
}
