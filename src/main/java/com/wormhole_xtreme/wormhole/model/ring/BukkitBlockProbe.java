package com.wormhole_xtreme.wormhole.model.ring;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Slab;

/**
 * Lets {@link RingTemplate} read a real world.
 *
 * <p>Two methods and no decisions. All the rules about what makes a ring — which pattern,
 * where the anchor is, whether the slabs agree with each other — live in the detector, where
 * they are tested against a map of blocks instead of a server.
 *
 * <p>The interesting half is {@link #halfAt}, which is where a double slab is deliberately
 * rejected. A double slab fills its whole block and so cannot say which surface it was laid
 * against, which is the one thing the template is being read for.
 */
public class BukkitBlockProbe implements RingTemplate.BlockProbe
{
    /** The world being read. */
    private final World world;

    /**
     * Instantiates a probe.
     *
     * @param world
     *            the world to read
     */
    public BukkitBlockProbe(final World world)
    {
        this.world = world;
    }

    /* (non-Javadoc)
     * @see RingTemplate.BlockProbe#materialAt(int, int, int)
     */
    @Override
    public Material materialAt(final int x, final int y, final int z)
    {
        return world.getBlockAt(x, y, z).getType();
    }

    /* (non-Javadoc)
     * @see RingTemplate.BlockProbe#halfAt(int, int, int)
     */
    @Override
    public RingTemplate.SlabHalf halfAt(final int x, final int y, final int z)
    {
        final BlockData data = world.getBlockAt(x, y, z).getBlockData();
        if (!(data instanceof Slab))
        {
            return null;
        }
        final Slab slab = (Slab) data;
        if (slab.getType() == Slab.Type.BOTTOM)
        {
            return RingTemplate.SlabHalf.BOTTOM;
        }
        if (slab.getType() == Slab.Type.TOP)
        {
            return RingTemplate.SlabHalf.TOP;
        }
        // A double slab is a full block. It cannot say which surface it was laid against,
        // and the orientation of the whole ring is read from exactly that, so it is not a
        // template block — refusing it here produces "no ring found" rather than a ring
        // that guesses which way it faces.
        return null;
    }
}
