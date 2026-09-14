package com.wormhole_xtreme.wormhole.model.mirror;

import java.util.List;

/**
 * One mirror on the network: one wall banner, or two side by side.
 *
 * <p>A mirror stores its own room as its {@link #destination()}: the point in front of its banner
 * where anybody coming through it lands, and which it shows as a reflection. Which mirror it opens
 * onto at any moment is {@link MirrorNetwork}'s to say -- its own room until somebody at it
 * right-clicks, and then its {@link #start()} first if it has one.
 *
 * @param name
 *            what the mirror is called, unique across the server, and the key it is stored
 *            under
 * @param banner
 *            the block a player clicks; for a mirror two banners wide, the left one of the two,
 *            looking at the wall
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
 *            the mirror a right-click opens onto first, or null for none
 * @param width
 *            how many banners wide it is, one or two
 */
public record QuantumMirror(String name, MirrorBlock banner, MirrorPoint destination,
    MirrorDisplay display, MirrorMode mode, MirrorLook look, String start, int width)
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
        this(name, banner, destination, MirrorDisplay.ALWAYS, MirrorMode.STATIC, null, null, 1);
    }

    /**
     * A mirror one banner wide with no start of its own.
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
        this(name, banner, destination, display, mode, look, null, 1);
    }

    /**
     * A mirror one banner wide.
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
     * @param start
     *            the mirror a right-click opens onto first, or null
     */
    public QuantumMirror(final String name, final MirrorBlock banner, final MirrorPoint destination,
        final MirrorDisplay display, final MirrorMode mode, final MirrorLook look, final String start)
    {
        this(name, banner, destination, display, mode, look, start, 1);
    }

    /**
     * Defaults the settings, so a mirror read from an older file is not half-built.
     *
     * <p>A null {@code display} or {@code mode} would otherwise reach the proximity sweep and
     * the stamp, both of which switch on them. A blank start is no start, and a width is one or two.
     */
    public QuantumMirror
    {
        display = (display == null) ? MirrorDisplay.ALWAYS : display;
        mode = (mode == null) ? MirrorMode.STATIC : mode;
        start = ((start == null) || start.isBlank()) ? null : start;
        width = (width >= 2) ? 2 : 1;
    }

    /**
     * Every banner the mirror is made of, the left one first.
     *
     * <p>The second of a pair is to the right of the first, looking at the wall; which way that is
     * comes from the way the mirror's room faces, since the banner's facing is recorded nowhere else.
     * A mirror with no room is one banner.
     *
     * @return one banner, or two
     */
    public List<MirrorBlock> banners()
    {
        if ((width < 2) || (destination == null))
        {
            return List.of(banner);
        }
        final MirrorWindow.Spot ahead = MirrorWindow.aheadOf(destination.yaw());
        return List.of(banner, new MirrorBlock(banner.worldName(), banner.x() + ahead.z(), banner.y(),
            banner.z() - ahead.x()));
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
        return new QuantumMirror(name, banner, newDestination, display, mode, look, start, width);
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
        return new QuantumMirror(newName, banner, destination, display, mode, look, start, width);
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
        return new QuantumMirror(name, newBanner, destination, display, mode, look, start, width);
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
        return new QuantumMirror(name, banner, destination, newDisplay, mode, look, start, width);
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
        return new QuantumMirror(name, banner, destination, display, newMode, look, start, width);
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
        return new QuantumMirror(name, banner, destination, display, mode, newLook, start, width);
    }

    /**
     * The same mirror, with another mirror first in its list.
     *
     * @param newStart
     *            the mirror's name, or null for none
     * @return a new instance; this one is unchanged
     */
    public QuantumMirror withStart(final String newStart)
    {
        return new QuantumMirror(name, banner, destination, display, mode, look, newStart, width);
    }

    /**
     * The same mirror, one or two banners wide.
     *
     * @param newWidth
     *            one or two
     * @return a new instance; this one is unchanged
     */
    public QuantumMirror withWidth(final int newWidth)
    {
        return new QuantumMirror(name, banner, destination, display, mode, look, start, newWidth);
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
