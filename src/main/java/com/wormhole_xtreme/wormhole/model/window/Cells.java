package com.wormhole_xtreme.wormhole.model.window;

/**
 * Cell-key arithmetic: a block position packed into one long, and a chunk packed into another. Static and tiny on
 * purpose, because the sweeps call it once per cell.
 */
final class Cells
{
    private Cells()
    {
    }

    static long chunkKey(final int x, final int z)
    {
        return (((long) x) << 32) | (z & 0xFFFFFFFFL);
    }

    /**
     * A block position packed into one long: 26 bits of x, 26 of z, 12 of y.
     *
     * @param x
     *            block x; only its low 26 bits are kept
     * @param y
     *            block y; only its low 12 bits are kept
     * @param z
     *            block z; only its low 26 bits are kept
     */
    static long key(final int x, final int y, final int z)
    {
        return ((x & 0x3FFFFFFL) << 38) | ((z & 0x3FFFFFFL) << 12) | (y & 0xFFFL);
    }

    static int unpackX(final long key)
    {
        return (int) (key >> 38);
    }

    static int unpackY(final long key)
    {
        return (int) ((key << 52) >> 52);
    }

    static int unpackZ(final long key)
    {
        return (int) ((key << 26) >> 38);
    }
}
