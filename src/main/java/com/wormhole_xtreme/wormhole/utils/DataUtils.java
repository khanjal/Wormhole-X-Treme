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
     *            the bytes
     * @param w
     *            the w
     * @return the block
     */
    public static Block blockFromBytes(final byte[] bytes, final World w)
    {
        final ByteBuffer b = ByteBuffer.wrap(bytes);
        return w.getBlockAt(b.getInt(), b.getInt(), b.getInt());
    }

    /**
     * Block location to bytes.
     * 
     * @param l
     *            the l
     * @return the byte[]
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
     * @param b
     *            the b
     * @return the byte[]
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
     *            the b
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
     *            the bytes
     * @param w
     *            the w
     * @return the location
     */
    public static Location locationFromBytes(final byte[] bytes, final World w)
    {
        final ByteBuffer b = ByteBuffer.wrap(bytes);
        final double x = b.getDouble();
        final double y = b.getDouble();
        final double z = b.getDouble();
        // Pitch comes first on disk, the writer's order since the original plugin.
        final float pitch = b.getFloat();
        final float yaw = b.getFloat();
        return new Location(w, x, y, z, yaw, pitch);
    }

    /**
     * Location to bytes.
     * 
     * @param l
     *            the l
     * @return the byte[]
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
