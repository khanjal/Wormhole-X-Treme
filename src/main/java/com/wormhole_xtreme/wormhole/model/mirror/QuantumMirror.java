package com.wormhole_xtreme.wormhole.model.mirror;

/**
 * One banner bound to one place to arrive.
 *
 * <p>A mirror is one-way. Clicking this banner sends the player to {@link #destination()}, and
 * nothing about that destination knows a mirror points at it -- a return trip is a second
 * mirror at the far end, bound back. That is what makes the museum case work without putting a
 * banner inside a frozen snapshot world at all: the snapshot need only be somewhere to arrive.
 *
 * <p>The destination is a plain point rather than another mirror for the same reason. Binding
 * mirrors to each other would read well, but it would make a return banner mandatory on the
 * far side, which is exactly the requirement one-way exists to avoid.
 *
 * @param name
 *            what the mirror is called, unique across the server, and the key it is stored
 *            under
 * @param banner
 *            the block a player clicks
 * @param destination
 *            where clicking it sends them, which may be in a world that is not loaded
 */
public record QuantumMirror(String name, MirrorBlock banner, MirrorPoint destination)
{
    /**
     * The same mirror pointing somewhere else.
     *
     * @param newDestination
     *            where it should send a player now
     * @return a new instance; this one is unchanged
     */
    public QuantumMirror withDestination(final MirrorPoint newDestination)
    {
        return new QuantumMirror(name, banner, newDestination);
    }

    /**
     * Whether both ends of this mirror are in the same world.
     *
     * <p>Refused at bind time unless an admin has turned that refusal off. A quantum mirror is
     * named for a window into a different reality, and the cross-world default is what gives it
     * an identity separate from a beam place -- which is the mechanic for naming a point in the
     * world you are already standing in.
     *
     * @return true if the banner and its destination share a world name
     */
    public boolean isSameWorld()
    {
        return (destination != null) && banner.worldName().equals(destination.worldName());
    }
}
