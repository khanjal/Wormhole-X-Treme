package com.wormhole_xtreme.wormhole.model;

import org.bukkit.Material;

import com.wormhole_xtreme.wormhole.utils.MaterialUtils;

/**
 * What a wormhole is drawn in when it sits behind the iris rather than in the ring.
 *
 * <p>The real liquid, normally. Something that only looks like it where the iris would hide it:
 * Java Edition does not draw a translucent block behind another translucent block, so a
 * wormhole right behind a stained-glass iris is simply not there. Ice is solid and cannot be
 * hidden that way.
 *
 * <p>Ice does not move, though, and water does, so a single sheet of it reads as a frozen gate.
 * Two ices are laid in a checkerboard and swap places every frame, which gives the surface
 * something to do. {@link #nextFrame} is what swaps them.
 *
 * <p>Shared by a built gate and a preview on purpose. The two draw their irises differently --
 * one as block changes, the other as display entities -- and an earlier cut exempted the
 * preview on the grounds that an entity takes no part in block face culling. It does not, but
 * that was never the whole rule: a translucent entity hides translucent water behind it just
 * the same, and the preview went on showing nothing. One decision for both, so they cannot
 * drift apart again.
 */
public final class DrawnHorizon
{
    /** Which of the two ices each cell takes; flipped by {@link #nextFrame}. */
    private static int frame;

    /** Static helpers only. */
    private DrawnHorizon()
    {
    }

    /**
     * Whether a horizon drawn behind this iris has to stand in for the real liquid.
     *
     * @param iris
     *            what the iris is drawn in
     * @return true if the real liquid would not be seen there
     */
    public static boolean standsIn(final Material iris)
    {
        return MaterialUtils.cullsWaterBehindIt(iris);
    }

    /**
     * What to draw a cell's horizon in, on this frame.
     *
     * @param portal
     *            the gate's own portal material
     * @param iris
     *            what the iris is drawn in
     * @param cell
     *            the opening cell it belongs to, which fixes its square of the checkerboard
     * @param behindTheIris
     *            true when this cell's horizon sits behind the iris rather than in the ring
     * @return the material to draw
     */
    public static Material materialFor(final Material portal, final Material iris,
        final IrisLayering.At cell, final boolean behindTheIris)
    {
        if (!behindTheIris || !standsIn(iris))
        {
            return portal;
        }
        return MaterialUtils.shownBehindGlassAs(portal, (((cell.x() + cell.y() + cell.z() + frame) & 1) != 0));
    }

    /** Moves every stand-in horizon on to its next frame. */
    public static void nextFrame()
    {
        frame ^= 1;
    }
}
