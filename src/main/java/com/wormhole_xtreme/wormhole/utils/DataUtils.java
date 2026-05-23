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
        if (b >= 1)
        {
            return true;
        }
        else
        {
            return false;
        }
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
        return new Location(w, b.getDouble(), b.getDouble(), b.getDouble(), b.getFloat(), b.getFloat());
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
