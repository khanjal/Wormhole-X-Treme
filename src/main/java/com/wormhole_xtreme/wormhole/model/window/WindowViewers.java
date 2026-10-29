package com.wormhole_xtreme.wormhole.model.window;

import static com.wormhole_xtreme.wormhole.model.window.WindowStore.ACTIVE;
import static com.wormhole_xtreme.wormhole.model.window.WindowStore.BLIND;
import static com.wormhole_xtreme.wormhole.model.window.WindowStore.FULL;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.config.ConfigManager;

/**
 * Which windows a player can see, and whether they are near one (#430).
 *
 * <p>Moved out of {@link Windows} verbatim. Reads the sweep's registry through {@link WindowStore}.
 */
final class WindowViewers
{
    /** Static helpers only. */
    private WindowViewers()
    {
    }

    /**
     * The windows a player is close enough to, in front of, and can actually see, in a stable
     * order.
     *
     * <p>Seeing means a clear line from the eye to some open block of the opening, through the
     * real world. Without that, somebody in a corridor was "in front of" every alcove mirror on
     * the same wall, and a redraw spent its whole budget walking the cones of four mirrors the
     * corridor walls hid from them, cutting short the one they were looking at.
     */
    static List<WindowState> seenBy(final Player player, final Location eye)
    {
        final double radius = ConfigManager.getMirrorProximityDistance();
        final Location at = player.getLocation();
        final long now = Windows.now();
        final List<WindowState> seeing = new ArrayList<>();
        if (BLIND.contains(player.getUniqueId()))
        {
            return seeing;
        }
        final String fullName = FULL.get(player.getUniqueId());
        for (final WindowState window : ACTIVE.values())
        {
            // A mirror drawn whole for an admin stays drawn wherever they stand, looking or not,
            // so they can walk round what the capture holds.
            if (!window.open.isEmpty() && window.name().equals(fullName)
                && window.anchor().getWorld().equals(player.getWorld()))
            {
                seeing.add(window);
                continue;
            }
            if (!window.open.isEmpty() && window.anchor().getWorld().equals(player.getWorld())
                && (Windows.fromBanner(window.anchor(), at.getX(), at.getY(), at.getZ()) <= (radius * radius))
                && window.shape.inFront(eye.getX(), eye.getZ())
                && canSee(player.getWorld(), eye, window, now))
            {
                seeing.add(window);
            }
        }
        seeing.sort(Comparator.comparing(WindowState::name));
        return seeing;
    }

    /**
     * Most lines of sight tried to one window's opening per redraw.
     *
     * <p>A Grand gate's opening is 274 cells. Every one of them blocked -- somebody behind a wall in
     * front of a gate -- cost some 80 ms a redraw where a Large gate's 52 had cost about 14.
     */
    static final int MOST_SIGHT_LINES = 64;

    /**
     * Whether a clear line runs from an eye to any open block of a window's opening: to every block of
     * one up to {@link #MOST_SIGHT_LINES}, and to that many spread evenly over a bigger one.
     */
    static boolean canSee(final World here, final Location eye, final WindowState window,
        final long now)
    {
        final int stride = Math.max(1, (window.open.size() + MOST_SIGHT_LINES - 1) / MOST_SIGHT_LINES);
        for (int i = 0; i < window.open.size(); i += stride)
        {
            if (WindowSight.clearLine(here, eye, window.open.get(i), window.shape.into(), now))
            {
                return true;
            }
        }
        return false;
    }

    /** Whether somebody not yet looking into anything has just come within range of a window. */
    static boolean nearAWindow(final Player player, final Location to)
    {
        final double radius = ConfigManager.getMirrorProximityDistance();
        for (final WindowState window : ACTIVE.values())
        {
            if (window.anchor().getWorld().equals(player.getWorld())
                && (Windows.fromBanner(window.anchor(), to.getX(), to.getY(), to.getZ()) <= (radius * radius)))
            {
                return true;
            }
        }
        return false;
    }
}
