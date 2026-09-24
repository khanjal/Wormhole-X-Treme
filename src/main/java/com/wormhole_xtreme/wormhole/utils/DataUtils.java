package com.wormhole_xtreme.wormhole.utils;

import java.nio.ByteBuffer;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * WormholeXTreme DataUtils.
 * 
 * @author Ben Echols (Lologarithm)
 */
public class DataUtils
{
    /** Static helpers only; never instantiated. */
    private DataUtils()
    {
    }


    /**
     * Block from bytes.
     * 
     * @param bytes
     *            twelve bytes: x, y and z as big-endian ints, as {@link #blockToBytes} writes them
     * @param w
     *            the world the coordinates belong to
     */
    public static Block blockFromBytes(final byte[] bytes, final World w)
    {
        final ByteBuffer b = ByteBuffer.wrap(bytes);
        return w.getBlockAt(b.getInt(), b.getInt(), b.getInt());
    }

    /**
     * Block location to bytes.
     * 
     * @return twelve bytes: the block x, y and z as big-endian ints
     */
    public static byte[] blockLocationToBytes(final Location l)
    {
        final ByteBuffer bb = ByteBuffer.allocate(12);

        bb.putInt(l.getBlockX());
        bb.putInt(l.getBlockY());
        bb.putInt(l.getBlockZ());

        return bb.array();
    }

    /**
     * Block to bytes.
     * 
     * @return twelve bytes: x, y and z as big-endian ints
     */
    public static byte[] blockToBytes(final Block b)
    {
        final ByteBuffer bb = ByteBuffer.allocate(12);

        bb.putInt(b.getX());
        bb.putInt(b.getY());
        bb.putInt(b.getZ());

        return bb.array();
    }

    /**
     * Byte to boolean.
     * 
     * @param b
     *            a stored flag; any value of 1 or more reads as true
     * @return true, if successful
     */
    public static final boolean byteToBoolean(final byte b)
    {
        return b >= 1;
    }

    /**
     * Location from bytes.
     * 
     * @param bytes
     *            32 bytes as {@link #locationToBytes} writes them
     * @param w
     *            the world the location belongs to
     */
    public static Location locationFromBytes(final byte[] bytes, final World w)
    {
        final ByteBuffer b = ByteBuffer.wrap(bytes);
        return new Location(w, b.getDouble(), b.getDouble(), b.getDouble(), b.getFloat(), b.getFloat());
    }

    /**
     * Location to bytes.
     * 
     * @return 32 bytes: x, y and z as doubles, then pitch and yaw as floats
     */
    public static byte[] locationToBytes(final Location l)
    {
        final ByteBuffer b = ByteBuffer.allocate(32);
        b.putDouble(l.getX());
        b.putDouble(l.getY());
        b.putDouble(l.getZ());
        b.putFloat(l.getPitch());
        b.putFloat(l.getYaw());

        return b.array();
    }
}
