package com.wormhole_xtreme.wormhole.model.ring;

import org.bukkit.World;

/**
 * Lets {@link RingSurvey} read a world that has no ring pair yet.
 *
 * <p>{@link BukkitRingWorld} is the other implementation of {@link RingSurvey.Ground}, and it
 * needs a {@link RingPair} because it also draws to the people who can see one. A ring being
 * built has no pair -- that is the whole point of building it -- so surveying at that moment
 * needs the three block-reading methods on their own.
 *
 * <p>Three methods and no decisions, the same split every other Bukkit adapter in this package
 * keeps: what makes a ring fit to stand in is argued about in {@link RingSurvey}, where it can
 * be tested against a map of blocks instead of a server.
 */
public class BukkitGround implements RingSurvey.Ground
{
    /** The world being read. */
    private final World world;

    /**
     * Instantiates a ground reader.
     *
     * @param world
     *            the world to read
     */
    public BukkitGround(final World world)
    {
        this.world = world;
    }

    /* (non-Javadoc)
     * @see RingSurvey.Ground#isPassable(int, int, int)
     */
    @Override
    public boolean isPassable(final int x, final int y, final int z)
    {
        return world.getBlockAt(x, y, z).isPassable();
    }

    /* (non-Javadoc)
     * @see RingSurvey.Ground#minHeight()
     */
    @Override
    public int minHeight()
    {
        return world.getMinHeight();
    }

    /* (non-Javadoc)
     * @see RingSurvey.Ground#maxHeight()
     */
    @Override
    public int maxHeight()
    {
        return world.getMaxHeight();
    }
}
