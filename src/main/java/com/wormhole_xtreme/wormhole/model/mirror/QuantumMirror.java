package com.wormhole_xtreme.wormhole.model.mirror;

/**
 * One banner on the mirror network.
 *
 * <p>A mirror stores its own room as its {@link #destination()}: the point in front of its banner
 * where anybody coming through it lands, and which it shows as a reflection. Which mirror it opens
 * onto at any moment is {@link MirrorNetwork}'s to say -- its {@link #start()} until somebody at it
 * chooses another, or its own room if it has no start.
 *
 * @param name
 *            what the mirror is called, unique across the server, and the key it is stored
 *            under
 * @param banner
 *            the block a player clicks
 * @param destination
 *            its own room, which may be in a world that is not loaded; a mirror saved before
 *            the network may still hold somewhere else it was pointed
 * @param display
 *            whether the look is in the block for everyone, or sent to whoever comes close
 * @param mode
 *            whether the look was chosen once or is re-read from the far side
 * @param look
 *            what it looks like, or null if it has never been stamped
 * @param start
 *            the mirror it opens onto when nobody at it has chosen, or null for its own room
 */
public record QuantumMirror(String name, MirrorBlock banner, MirrorPoint destination,
    MirrorDisplay display, MirrorMode mode, MirrorLook look, String start)
{
    /**
     * A mirror with nothing chosen about how it looks.
     *
     * @param name
     *            what it is called
     * @param banner
     *            the block a player clicks
     * @param destination
     *            its room
     */
    public QuantumMirror(final String name, final MirrorBlock banner,
        final MirrorPoint destination)
    {
        this(name, banner, destination, MirrorDisplay.ALWAYS, MirrorMode.STATIC, null, null);
    }

    /**
     * A mirror with no start of its own.
     *
     * @param name
     *            what it is called
     * @param banner
     *            the block a player clicks
     * @param destination
     *            its room
     * @param display
     *            how its look is shown
     * @param mode
     *            whether its look is re-read
     * @param look
     *            what it looks like, or null
     */
    public QuantumMirror(final String name, final MirrorBlock banner, final MirrorPoint destination,
        final MirrorDisplay display, final MirrorMode mode, final MirrorLook look)
    {
        this(name, banner, destination, display, mode, look, null);
    }

    /**
     * Defaults the settings, so a mirror read from an older file is not half-built.
     *
     * <p>A null {@code display} or {@code mode} would otherwise reach the proximity sweep and
     * the stamp, both of which switch on them. A blank start is no start.
     */
    public QuantumMirror
    {
        display = (display == null) ? MirrorDisplay.ALWAYS : display;
        mode = (mode == null) ? MirrorMode.STATIC : mode;
        start = ((start == null) || start.isBlank()) ? null : start;
    }

    /**
     * The same mirror with another room.
     *
     * @param newDestination
     *            its room now
     * @return a new instance; this one is unchanged
     */
    public QuantumMirror withDestination(final MirrorPoint newDestination)
    {
        return new QuantumMirror(name, banner, newDestination, display, mode, look, start);
    }

    /**
     * The same mirror under a different name.
     *
     * <p>Everything else comes with it, which is the whole point. Rebuilding a renamed mirror
     * from its name and banner alone drops its room, what it looks like and whether it hides
     * itself -- and does it silently, because the result is a perfectly valid mirror.
     *
     * @param newName
     *            what it should be called now
     * @return a new instance; this one is unchanged
     */
    public QuantumMirror withName(final String newName)
    {
        return new QuantumMirror(newName, banner, destination, display, mode, look, start);
    }

    /**
     * The same mirror hung on a different banner.
     *
     * @param newBanner
     *            the block a player should click now
     * @return a new instance; this one is unchanged
     */
    public QuantumMirror withBanner(final MirrorBlock newBanner)
    {
        return new QuantumMirror(name, newBanner, destination, display, mode, look, start);
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
        return new QuantumMirror(name, banner, destination, newDisplay, mode, look, start);
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
        return new QuantumMirror(name, banner, destination, display, newMode, look, start);
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
        return new QuantumMirror(name, banner, destination, display, mode, newLook, start);
    }

    /**
     * The same mirror, opening onto another mirror when nobody has chosen.
     *
     * @param newStart
     *            the mirror's name, or null for its own room
     * @return a new instance; this one is unchanged
     */
    public QuantumMirror withStart(final String newStart)
    {
        return new QuantumMirror(name, banner, destination, display, mode, look, newStart);
    }

    /**
     * Whether both ends of this mirror are in the same world.
     *
     * @return true if the banner and its destination share a world name
     */
    public boolean isSameWorld()
    {
        return (destination != null) && banner.worldName().equals(destination.worldName());
    }
}
