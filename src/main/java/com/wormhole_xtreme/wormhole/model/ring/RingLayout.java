package com.wormhole_xtreme.wormhole.model.ring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.Material;

/**
 * Which of a ring pattern's slab squares already hold the right slab (#332).
 *
 * <p>The layout guide of a pattern preview: where the slabs go, and how many of them a player has
 * laid already. Judged the way {@link RingTemplate} reads a real ring: the same material, and the
 * half a floor ring or a ceiling ring is laid in, so a square the guide counts as in place is one
 * the template would accept.
 */
final class RingLayout
{
    /** The squares still to fill, each as {@code {x, y, z}}. */
    private final List<int[]> toPlace;

    /** How many squares the pattern has. */
    private final int total;

    /**
     * Instantiates a judged layout.
     *
     * @param toPlace
     *            the squares not holding the right slab
     * @param total
     *            how many squares there are
     */
    private RingLayout(final List<int[]> toPlace, final int total)
    {
        this.toPlace = Collections.unmodifiableList(toPlace);
        this.total = total;
    }

    /**
     * Compares a ring's slab squares with what stands in the world.
     *
     * @param ring
     *            the ring the pattern would become
     * @param slab
     *            the slab it is to be laid in
     * @param probe
     *            how to read the world
     * @return the squares still to fill, and how many there are in all
     */
    static RingLayout judge(final Ring ring, final Material slab, final RingTemplate.BlockProbe probe)
    {
        final RingTemplate.SlabHalf half = (ring.getOrientation() == RingOrientation.FLOOR)
            ? RingTemplate.SlabHalf.BOTTOM : RingTemplate.SlabHalf.TOP;
        final List<int[]> missing = new ArrayList<>();
        final List<int[]> squares = ring.perimeterBlocks();
        for (final int[] square : squares)
        {
            final boolean right = (probe.materialAt(square[0], square[1], square[2]) == slab)
                && (probe.halfAt(square[0], square[1], square[2]) == half);
            if (!right)
            {
                missing.add(square);
            }
        }
        return new RingLayout(missing, squares.size());
    }

    /** @return the squares not holding the right slab, each as {@code {x, y, z}} */
    List<int[]> getToPlace()
    {
        return toPlace;
    }

    /** @return how many squares the pattern has */
    int getTotal()
    {
        return total;
    }

    /** @return how many squares already hold the right slab */
    int getInPlace()
    {
        return total - toPlace.size();
    }
}
