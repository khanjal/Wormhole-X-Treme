package com.wormhole_xtreme.wormhole.utils;

/**
 * A block position as a single {@code long}, for use as a map or set key.
 *
 * <p>The plugin's hottest question is "is this block part of something of ours" -- asked on
 * every player move, every vehicle move, every tracked projectile every tick, and on
 * {@code BlockPhysicsEvent}, which a server raises for every water flow, every falling
 * block and every redstone update anywhere in a loaded world. Answering it with a
 * {@link org.bukkit.Location} key means allocating a Location to ask, and a Location carries
 * a world reference and two floats that a block position does not have and does not want.
 *
 * <p>26 bits of x, 26 of z, 12 of y. That covers the full world border and the whole build
 * range, so a key is one primitive and a lookup allocates nothing but the box around it --
 * and nothing at all where the caller can keep it primitive.
 *
 * <p>The world is deliberately not part of the key. Two worlds can hold the same block
 * position, so callers key by world first and by position within it, which also means a
 * per-world index can be dropped whole when a world unloads.
 *
 * <p>{@link com.wormhole_xtreme.wormhole.model.ring.RingIndex} arrived at this shape first,
 * for the ring half of the same question; this is that packing lifted out so gates use one
 * implementation of it rather than a second copy.
 */
public final class BlockKey
{
    /** Static helpers only. */
    private BlockKey() {}

    /**
     * Packs a block position into a single long.
     *
     * @param x
     *            block x
     * @param y
     *            block y
     * @param z
     *            block z
     * @return the packed position
     */
    public static long pack(final int x, final int y, final int z)
    {
        return ((x & 0x3FFFFFFL) << 38) | ((z & 0x3FFFFFFL) << 12) | (y & 0xFFFL);
    }

    /**
     * Reads the x back out of a packed position.
     *
     * <p>Shifting a signed long right brings the sign with it, which is what makes a
     * negative coordinate survive the round trip.
     *
     * @param packed
     *            a position from {@link #pack}
     * @return block x
     */
    public static int unpackX(final long packed)
    {
        return (int) (packed >> 38);
    }

    /**
     * Reads the y back out of a packed position.
     *
     * <p>Y sits in the low twelve bits, which is not enough for a plain mask to preserve its
     * sign -- masking a negative y gives a large positive number instead. Shifting it up to
     * the top of the long and arithmetically back down sign-extends it properly.
     *
     * <p>This matters more than it looks. The world runs from y=-64, so gates and rings built
     * in deepslate, in caves and on the nether floor all sit below zero, and getting it wrong
     * would resolve their blocks thousands of blocks away.
     *
     * @param packed
     *            a position from {@link #pack}
     * @return block y
     */
    public static int unpackY(final long packed)
    {
        return (int) ((packed << 52) >> 52);
    }

    /**
     * Reads the z back out of a packed position.
     *
     * @param packed
     *            a position from {@link #pack}
     * @return block z
     */
    public static int unpackZ(final long packed)
    {
        return (int) ((packed << 26) >> 38);
    }

    /**
     * Packs a chunk position into a single long.
     *
     * <p>Separate from {@link #pack} rather than reusing it with a zero y, so a chunk key and
     * a block key can never collide in the same map by accident.
     *
     * @param chunkX
     *            chunk x, which is block x shifted right four
     * @param chunkZ
     *            chunk z
     * @return the packed chunk position
     */
    public static long packChunk(final int chunkX, final int chunkZ)
    {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }
}
