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
 * @param display
 *            whether the look is in the block for everyone, or sent to whoever comes close
 * @param mode
 *            whether the look was chosen once or is re-read from the far side
 * @param look
 *            what it looks like, or null if it has never been stamped
 */
public record QuantumMirror(String name, MirrorBlock banner, MirrorPoint destination,
    MirrorDisplay display, MirrorMode mode, MirrorLook look)
{
    /**
     * A mirror with nothing chosen about how it looks.
     *
     * <p>Three arguments rather than six, because naming and pointing a mirror is what most of
     * this plugin does with one and the cosmetics are a later, optional step. Every mirror
     * created before looks existed reads as this.
     *
     * @param name
     *            what it is called
     * @param banner
     *            the block a player clicks
     * @param destination
     *            where clicking it sends them
     */
    public QuantumMirror(final String name, final MirrorBlock banner,
        final MirrorPoint destination)
    {
        this(name, banner, destination, MirrorDisplay.ALWAYS, MirrorMode.STATIC, null);
    }

    /**
     * Defaults the two settings, so a mirror read from an older file is not half-built.
     *
     * <p>A null {@code display} or {@code mode} would otherwise reach the proximity sweep and
     * the stamp, both of which switch on them. Defaulting here rather than at each use is what
     * keeps "an old mirror behaves exactly as it did" true in one place.
     */
    public QuantumMirror
    {
        display = (display == null) ? MirrorDisplay.ALWAYS : display;
        mode = (mode == null) ? MirrorMode.STATIC : mode;
    }

    /**
     * The same mirror pointing somewhere else.
     *
     * @param newDestination
     *            where it should send a player now
     * @return a new instance; this one is unchanged
     */
    public QuantumMirror withDestination(final MirrorPoint newDestination)
    {
        return new QuantumMirror(name, banner, newDestination, display, mode, look);
    }

    /**
     * The same mirror, shown a different way.
     *
     * @param newDisplay
     *            when it should show its look
     * @return a new instance; this one is unchanged
     */
    public QuantumMirror withDisplay(final MirrorDisplay newDisplay)
    {
        return new QuantumMirror(name, banner, destination, newDisplay, mode, look);
    }

    /**
     * The same mirror, told whether it may re-read the far side.
     *
     * @param newMode
     *            static or dynamic
     * @return a new instance; this one is unchanged
     */
    public QuantumMirror withMode(final MirrorMode newMode)
    {
        return new QuantumMirror(name, banner, destination, display, newMode, look);
    }

    /**
     * The same mirror wearing a different look.
     *
     * @param newLook
     *            what it should look like, or null to forget
     * @return a new instance; this one is unchanged
     */
    public QuantumMirror withLook(final MirrorLook newLook)
    {
        return new QuantumMirror(name, banner, destination, display, mode, newLook);
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
