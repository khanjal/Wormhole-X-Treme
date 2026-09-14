package com.wormhole_xtreme.wormhole.model.mirror;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

/**
 * A photograph of a mirror's far side: a box of blocks around the arrival point, taken once and
 * kept on disk, which is what a window draws from.
 *
 * <p>Drawing from a photograph rather than from the live world is what makes a window reliable.
 * The live world has to be loaded to be read, loading it stalls the server or arrives late, and a
 * mirror onto a world that is not loaded at all -- the archived-snapshot museum that #22 was
 * filed for -- could never show anything. A capture is always there, instantly, and a museum
 * stays exactly as it was captured. What it costs is currency: the far side changes only when
 * the capture is taken again, by {@code mirror stamp} or by {@code mode dynamic}.
 *
 * <p>Stored as a palette of block states and one index per block, gzipped; a 129×129 area, 16
 * below the arrival point to 64 above, is about 1.3 million blocks and mostly air. In memory the
 * indices are shorts, so a few megabytes per capture, and only captures somebody is looking at
 * are loaded.
 *
 * <p>Immutable once built, so it can be read by a redraw and written to disk at the same time.
 */
public final class MirrorCapture
{
    /** The file header, so a file that is not one is refused rather than misread. */
    private static final int MAGIC = 0x4D495257;

    /** 2 marks buried blocks as {@link #BURIED}; 1 wrote them as air. */
    private static final int VERSION = 2;

    /** Most distinct block states a capture may hold; a short index per block. */
    private static final int MOST_STATES = 65_535;

    /**
     * The palette name a block buried two deep is recorded as.
     *
     * <p>Not air: a window drawing the far side whole, rather than only what a line of sight
     * meets, would carve the inside of every hill out of the real ground beneath it. Buried means
     * "leave whatever is really there".
     */
    static final String BURIED = "wormhole:buried";

    private final String worldName;
    private final boolean hasSky;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final long takenAt;
    private final String[] names;
    private final BlockData[] states;
    private final short[] indices;
    private final short[] tops;
    private final int buried;
    private final boolean prunedToAir;

    private MirrorCapture(final String worldName, final boolean hasSky, final int minX,
        final int minY, final int minZ, final int sizeX, final int sizeY, final int sizeZ,
        final long takenAt, final String[] names, final BlockData[] states, final short[] indices,
        final boolean prunedToAir)
    {
        this.prunedToAir = prunedToAir;
        this.buried = List.of(names).indexOf(BURIED);
        this.worldName = worldName;
        this.hasSky = hasSky;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.takenAt = takenAt;
        this.names = names;
        this.states = states;
        this.indices = indices;
        this.tops = tops(indices, sizeX, sizeY, sizeZ, buried);
    }

    /**
     * Builds a capture block by block.
     *
     * <p>Index 0 is always air, so a block never written reads as air and the highest block in
     * a column is the highest one written as something else.
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
        private final java.util.Map<String, Short> byName = new java.util.HashMap<>();
        private final short[] indices;

        /**
         * @param worldName
         *            the far world
         * @param hasSky
         *            whether a line of sight that meets nothing there is looking at sky
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
            this.indices = new short[sizeX * sizeY * sizeZ];
            names.add((air == null) ? "minecraft:air" : air.getAsString());
            states.add(air);
            byName.put(names.get(0), (short) 0);
        }

        /**
         * Records one block, if it is inside the box.
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
            final int dx = x - minX;
            final int dy = y - minY;
            final int dz = z - minZ;
            if ((dx < 0) || (dx >= sizeX) || (dy < 0) || (dy >= sizeY) || (dz < 0) || (dz >= sizeZ))
            {
                return;
            }
            indices[offset(dx, dy, dz, sizeY, sizeZ)] = index(data);
        }

        /** Blanks one block to air, for a mirror's banner at the far side. */
        public void clear(final int x, final int y, final int z)
        {
            final int dx = x - minX;
            final int dy = y - minY;
            final int dz = z - minZ;
            if ((dx >= 0) && (dx < sizeX) && (dy >= 0) && (dy < sizeY) && (dz >= 0) && (dz < sizeZ))
            {
                indices[offset(dx, dy, dz, sizeY, sizeZ)] = 0;
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
            final short index = index(data);
            for (int dx = 0; dx < sizeX; dx++)
            {
                for (int dz = 0; dz < sizeZ; dz++)
                {
                    for (int dy = 0; dy < sizeY; dy++)
                    {
                        indices[offset(dx, dy, dz, sizeY, sizeZ)] =
                            ((minY + dy) < below) ? index : 0;
                    }
                }
            }
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
         * Marks every block buried two deep -- one with no face open, and no neighbour with one --
         * as {@link #BURIED}.
         *
         * <p>Nothing surrounded by solid blocks can be seen through a window, and a hillside is
         * nearly all inside. Marked as one palette entry, those blocks cost nearly nothing on
         * disk. The layer just under the surface is kept all the same, in case: a capture is
         * looked at from angles nobody chose, and a surface block that is wrong for any reason
         * should have ground under it, not a hole. A block on the box's edge is kept, since what
         * lies beyond the edge is unknown, and it is not counted as open either.
         */
        public void prune()
        {
            final short buriedIndex = buriedIndex();
            final boolean[] solid = new boolean[states.size()];
            for (int i = 0; i < states.size(); i++)
            {
                solid[i] = (i != 0) && (BURIED.equals(names.get(i)) || states.get(i).isOccluding());
            }
            final boolean[] open = new boolean[indices.length];
            for (int dx = 1; dx < (sizeX - 1); dx++)
            {
                for (int dz = 1; dz < (sizeZ - 1); dz++)
                {
                    for (int dy = 1; dy < (sizeY - 1); dy++)
                    {
                        final int at = offset(dx, dy, dz, sizeY, sizeZ);
                        open[at] = !solid[indices[at]] || !solid[indices[at - 1]] || !solid[indices[at + 1]]
                            || !solid[indices[offset(dx - 1, dy, dz, sizeY, sizeZ)]]
                            || !solid[indices[offset(dx + 1, dy, dz, sizeY, sizeZ)]]
                            || !solid[indices[offset(dx, dy, dz - 1, sizeY, sizeZ)]]
                            || !solid[indices[offset(dx, dy, dz + 1, sizeY, sizeZ)]];
                    }
                }
            }
            final short[] kept = indices.clone();
            for (int dx = 1; dx < (sizeX - 1); dx++)
            {
                for (int dz = 1; dz < (sizeZ - 1); dz++)
                {
                    for (int dy = 1; dy < (sizeY - 1); dy++)
                    {
                        final int at = offset(dx, dy, dz, sizeY, sizeZ);
                        if (!open[at] && !open[at - 1] && !open[at + 1]
                            && !open[offset(dx - 1, dy, dz, sizeY, sizeZ)]
                            && !open[offset(dx + 1, dy, dz, sizeY, sizeZ)]
                            && !open[offset(dx, dy, dz - 1, sizeY, sizeZ)]
                            && !open[offset(dx, dy, dz + 1, sizeY, sizeZ)])
                        {
                            kept[at] = buriedIndex;
                        }
                    }
                }
            }
            System.arraycopy(kept, 0, indices, 0, indices.length);
        }

        /**
         * Keeps only what can be seen from the opening, and marks the rest {@link #BURIED}.
         *
         * <p>Rays from points across the front of the opening, in every direction that leaves
         * through its back -- the opening is a hole a block deep, so nothing steeper gets
         * through, however close the eye -- a degree apart, which at the far end of the depth
         * is under a block; each followed a block at a time until it meets something that
         * hides what is behind it, or leaves the box, or passes the depth. Every block a
         * ray passes through or ends on is seen, air included: air that a viewer can see is what
         * the view carves through the real world. A block beside seen air is seen too, which
         * catches what a ray a degree wide slipped past. Everything else is left to the real
         * world, whatever it was: a window can never show it, and a view drawn whole would have
         * sent it, and the inside of every far hill, for nothing.
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
            final boolean[] solid = new boolean[states.size()];
            for (int i = 0; i < states.size(); i++)
            {
                solid[i] = (i != 0) && (BURIED.equals(names.get(i)) || states.get(i).isOccluding());
            }
            final boolean[] seen = new boolean[indices.length];
            // The opening is a hole a block wide, two tall and a block deep, through the wall.
            // A line of sight goes in at its front and out at its back, so what can be seen is
            // bounded by the hole's own shape: nothing steeper than a block sideways or two up
            // per block in. The back of the hole is the back of the block behind the arrival
            // block, which is where the rays start; the front is a block further back.
            final double exitX = (arrivalX + 0.5) - (0.5 * aheadX);
            final double exitZ = (arrivalZ + 0.5) - (0.5 * aheadZ);
            final int rightX = -aheadZ;
            final int rightZ = aheadX;
            final double reach = depth + 2.0;
            final double step = Math.tan(Math.toRadians(1.0));
            for (double across = -0.4; across <= 0.41; across += 0.2)
            {
                for (double up = 0.1; up < 2.0; up += 0.2)
                {
                    // From this point at the front of the hole, every direction out of its back.
                    for (double sideways = (-0.5 - across) + (step / 2); sideways < (0.5 - across); sideways += step)
                    {
                        for (double upward = -up + (step / 2); upward < (2.0 - up); upward += step)
                        {
                            final double length = Math.sqrt(1.0 + (sideways * sideways) + (upward * upward));
                            ray(seen, solid, exitX + ((across + sideways) * rightX), arrivalY + up + upward,
                                exitZ + ((across + sideways) * rightZ), (aheadX + (sideways * rightX)) / length,
                                upward / length, (aheadZ + (sideways * rightZ)) / length, reach);
                        }
                    }
                }
            }
            final short buriedIndex = buriedIndex();
            final boolean[] kept = seen.clone();
            for (int dx = 0; dx < sizeX; dx++)
            {
                for (int dz = 0; dz < sizeZ; dz++)
                {
                    for (int dy = 0; dy < sizeY; dy++)
                    {
                        final int at = offset(dx, dy, dz, sizeY, sizeZ);
                        if (seen[at] && (indices[at] == 0))
                        {
                            // A block beside seen air is a face a viewer can see.
                            keepBeside(kept, dx - 1, dy, dz);
                            keepBeside(kept, dx + 1, dy, dz);
                            keepBeside(kept, dx, dy - 1, dz);
                            keepBeside(kept, dx, dy + 1, dz);
                            keepBeside(kept, dx, dy, dz - 1);
                            keepBeside(kept, dx, dy, dz + 1);
                        }
                    }
                }
            }
            for (int at = 0; at < indices.length; at++)
            {
                if (!kept[at])
                {
                    indices[at] = buriedIndex;
                }
            }
        }

        private void keepBeside(final boolean[] kept, final int dx, final int dy, final int dz)
        {
            if ((dx >= 0) && (dx < sizeX) && (dy >= 0) && (dy < sizeY) && (dz >= 0) && (dz < sizeZ))
            {
                final int at = offset(dx, dy, dz, sizeY, sizeZ);
                if (indices[at] != 0)
                {
                    kept[at] = true;
                }
            }
        }

        /** Follows one ray through the box, marking what it passes, until something solid or the edge. */
        private void ray(final boolean[] seen, final boolean[] solid, final double ox, final double oy,
            final double oz, final double dx, final double dy, final double dz, final double reach)
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
                seen[at] = true;
                if (solid[indices[at]])
                {
                    return;
                }
                final int next = (tMax[0] < tMax[1]) ? ((tMax[0] < tMax[2]) ? 0 : 2) : ((tMax[1] < tMax[2]) ? 1 : 2);
                t = tMax[next];
                c[next] += stepOf[next];
                tMax[next] += tDelta[next];
            }
        }

        /** The palette index of {@link #BURIED}, added the first time; air if the palette is full. */
        private short buriedIndex()
        {
            final Short known = byName.get(BURIED);
            if (known != null)
            {
                return known;
            }
            if (names.size() >= MOST_STATES)
            {
                return 0;
            }
            final short next = (short) names.size();
            names.add(BURIED);
            states.add(null);
            byName.put(BURIED, next);
            return next;
        }

        /** @return the finished capture, taken now */
        public MirrorCapture build()
        {
            return new MirrorCapture(worldName, hasSky, minX, minY, minZ, sizeX, sizeY, sizeZ,
                System.currentTimeMillis(), names.toArray(new String[0]),
                states.toArray(new BlockData[0]), indices, false);
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
        return indices.length;
    }

    /** @return how many distinct block states it holds, air included */
    public int states()
    {
        return names.length;
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

    /**
     * What stands at a block.
     *
     * @param x
     *            world x
     * @param y
     *            world y
     * @param z
     *            world z
     * @return the block state, or null outside the box
     */
    public BlockData at(final int x, final int y, final int z)
    {
        if (!contains(x, y, z))
        {
            return null;
        }
        return state(indices[offset(x - minX, y - minY, z - minZ, sizeY, sizeZ)]);
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
     * @return true for air, and for anything outside the box
     */
    public boolean isAir(final int x, final int y, final int z)
    {
        return !contains(x, y, z)
            || (indices[offset(x - minX, y - minY, z - minZ, sizeY, sizeZ)] == 0);
    }

    /**
     * Whether a block is buried two deep in solid ground, and so recorded only as that.
     *
     * @param x
     *            world x
     * @param y
     *            world y
     * @param z
     *            world z
     * @return true if pruned as buried; never for a capture from before buried blocks were marked
     */
    public boolean isBuried(final int x, final int y, final int z)
    {
        return (buried >= 0) && contains(x, y, z)
            && (indices[offset(x - minX, y - minY, z - minZ, sizeY, sizeZ)] == buried);
    }

    /** @return true for a capture written before buried blocks were marked, which wrote them as air */
    public boolean prunedToAir()
    {
        return prunedToAir;
    }

    /**
     * The highest block that is not air in a column.
     *
     * @param x
     *            world x
     * @param z
     *            world z
     * @return its y, or one below the box for an empty column or one outside it
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
            for (final short index : indices)
            {
                out.writeShort(index);
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
     * @param file
     *            the file
     * @return the capture
     * @throws IOException
     *             if the file is missing, not a capture, or cut short
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
            if ((version < 1) || (version > VERSION))
            {
                throw new IOException(file + " is capture version " + version + ", not 1 to " + VERSION);
            }
            final String worldName = readString(in);
            final boolean hasSky = in.readBoolean();
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
            final short[] indices = new short[sizeX * sizeY * sizeZ];
            for (int i = 0; i < indices.length; i++)
            {
                indices[i] = in.readShort();
            }
            return new MirrorCapture(worldName, hasSky, minX, minY, minZ, sizeX, sizeY, sizeZ,
                takenAt, names, new BlockData[count], indices, version == 1);
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
     * @return the state's name, or "outside" past the box
     */
    public String nameAt(final int x, final int y, final int z)
    {
        return contains(x, y, z) ? names[indices[offset(x - minX, y - minY, z - minZ, sizeY, sizeZ)]]
            : "outside";
    }

    /** @return how many blocks of the box are not air */
    public int filled()
    {
        int filled = 0;
        for (final short index : indices)
        {
            if (index != 0)
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
            + filled() + " of " + indices.length + " blocks filled, " + names.length
            + " kinds, taken " + ((System.currentTimeMillis() - takenAt) / 1000L) + "s ago"
            + (hasSky ? ", sky" : ", no sky");
    }

    /** The state for an index, made from its name the first time. */
    private BlockData state(final short index)
    {
        BlockData state = states[index];
        if (state == null)
        {
            // Buried is not a block the server knows; anything asking what it is gets stone.
            state = (index == buried) ? Bukkit.createBlockData(Material.STONE)
                : Bukkit.createBlockData(names[index]);
            states[index] = state;
        }
        return state;
    }

    private static int offset(final int dx, final int dy, final int dz, final int sizeY,
        final int sizeZ)
    {
        return (((dx * sizeZ) + dz) * sizeY) + dy;
    }

    /** The highest non-air block in each column, as offsets from the box's floor; -1 if none. */
    /** The highest block in each column that is neither air nor buried: buried is nothing to draw. */
    private static short[] tops(final short[] indices, final int sizeX, final int sizeY,
        final int sizeZ, final int buried)
    {
        final short[] tops = new short[sizeX * sizeZ];
        for (int dx = 0; dx < sizeX; dx++)
        {
            for (int dz = 0; dz < sizeZ; dz++)
            {
                int top = -1;
                final int column = offset(dx, 0, dz, sizeY, sizeZ);
                for (int dy = sizeY - 1; dy >= 0; dy--)
                {
                    if ((indices[column + dy] != 0) && (indices[column + dy] != buried))
                    {
                        top = dy;
                        break;
                    }
                }
                tops[(dx * sizeZ) + dz] = (short) top;
            }
        }
        return tops;
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
        if ((length < 0) || (length > 4096))
        {
            throw new IOException("a string of " + length + " bytes");
        }
        final byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
