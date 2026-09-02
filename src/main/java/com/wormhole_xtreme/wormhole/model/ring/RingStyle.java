/*
 *   Wormhole X-Treme Plugin for Bukkit
 *
 *   The two ways a ring stack can deploy.
 */
package com.wormhole_xtreme.wormhole.model.ring;

/**
 * How a ring stack comes out.
 *
 * <p>Both build the same stack and both come home nearest-first. The only thing that differs
 * is when each ring leaves the plane — concurrently, as soon as there is room behind the one
 * in front; sequentially, not until it has stopped — which is why one enum and one changed
 * number covers both rather than two animations existing side by side.
 *
 * <p>Style belongs to the <em>pair</em>, not to an end, for the same reason access does:
 * both ends run as one cycle and the swap happens when both stacks are up. Ends with
 * different styles would finish deploying at different moments and one would stand waiting
 * on the other, which is a worse effect than either style on its own. Materials can differ
 * per end because they are cosmetic and local; timing is shared because the event is shared.
 */
public enum RingStyle
{
    /**
     * Several rings climbing at the same time, one behind another.
     *
     * <p>A ring leaves the plane once the one in front of it is a clear block above, so what
     * rises out of the floor is an evenly spaced column rather than a single ring. They
     * arrive in order, top first, each stopping as it reaches its place. Quicker, and the
     * more common look.
     */
    CONCURRENT,

    /**
     * One ring at a time, each waiting for the one before it to stop.
     *
     * <p>The first out flies all the way to the furthest position and halts; only then does
     * the next emerge. Slower and more deliberate.
     */
    SEQUENTIAL
}
