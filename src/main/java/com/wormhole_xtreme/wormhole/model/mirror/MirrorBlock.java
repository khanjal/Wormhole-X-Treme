package com.wormhole_xtreme.wormhole.model.mirror;

import org.bukkit.Location;
import org.bukkit.block.Block;

/**
 * Which block a mirror's banner is, as a key that can be compared and stored.
 *
 * <p>A {@link Block} cannot be used as a map key across a world unload: it holds a live world,
 * and two {@code Block} objects for the same coordinates are not reliably equal. This is the
 * world name and three integers, which are, and which survive a restart.
 *
 * <p>The lookup this keys is on the hot path of every right-click on the server, so it has to
 * be a hash lookup rather than a scan over registered mirrors. That is the whole reason this
 * type exists rather than storing the banner's {@link MirrorPoint}: a point carries doubles
 * and a facing, none of which a block identity should depend on.
 *
 * @param worldName
 *            the world the banner is in
 * @param x
 *            block x
 * @param y
 *            block y
 * @param z
 *            block z
 */
public record MirrorBlock(String worldName, int x, int y, int z)
{
    /**
     * The key for a live block.
     *
     * @param block
     *            the block to key
     * @return its key
     */
    public static MirrorBlock of(final Block block)
    {
        return new MirrorBlock(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    /**
     * The key for a location's block coordinates.
     *
     * @param location
     *            the location to key
     * @return its key
     */
    public static MirrorBlock of(final Location location)
    {
        return new MirrorBlock(location.getWorld().getName(), location.getBlockX(),
            location.getBlockY(), location.getBlockZ());
    }

    /** @return the key as it is written in the mirror file, e.g. {@code world:12:64:-30} */
    public String toKey()
    {
        return worldName + ":" + x + ":" + y + ":" + z;
    }

    /**
     * Reads a key back, or null if it is not one.
     *
     * <p>A world name may itself contain a colon, so the three coordinates are taken from the
     * end rather than the name from the front -- splitting on the first colon would truncate
     * such a name and silently orphan every mirror in that world.
     *
     * @param key
     *            the stored key
     * @return the block, or null if the key is malformed
     */
    public static MirrorBlock fromKey(final String key)
    {
        if (key == null)
        {
            return null;
        }
        final int lastColon = key.lastIndexOf(':');
        final int secondColon = (lastColon < 0) ? -1 : key.lastIndexOf(':', lastColon - 1);
        final int firstColon = (secondColon < 0) ? -1 : key.lastIndexOf(':', secondColon - 1);
        if (firstColon <= 0)
        {
            return null;
        }
        try
        {
            return new MirrorBlock(key.substring(0, firstColon),
                Integer.parseInt(key.substring(firstColon + 1, secondColon)),
                Integer.parseInt(key.substring(secondColon + 1, lastColon)),
                Integer.parseInt(key.substring(lastColon + 1)));
        }
        catch (final NumberFormatException notAKey)
        {
            return null;
        }
    }
}
