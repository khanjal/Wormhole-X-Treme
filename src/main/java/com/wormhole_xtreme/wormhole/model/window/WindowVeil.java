package com.wormhole_xtreme.wormhole.model.window;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.freya.FreyaCompanion;

/**
 * Hiding from a viewer the creatures of their own world that stand inside the view they are shown (#430).
 *
 * <p>Moved out of {@link Windows} verbatim.
 */
final class WindowVeil
{
    /** Static helpers only. */
    private WindowVeil()
    {
    }

    /**
     * The viewer's own world's creatures standing inside the view.
     *
     * <p>A drawn block hides what is behind it, but a creature is not a block: an armour stand
     * on the real side kept standing in the middle of the far side. Anything within the radius
     * whose position is seen through an opening is hidden from the viewer, and shown again when
     * it is not. Other players are left alone: hiding one would take them off the tab list too.
     */
    static void creaturesInside(final World here, final Location eye, final int radius,
        final List<WindowState> seeing, final Map<WindowState, Windows.Whole> fixed, final Set<Long> allOpen,
        final List<Entity> inside)
    {
        // Depth is from the opening, and the eye may be the proximity distance from that.
        final double reach = Math.max(radius, ConfigManager.getViewDepth())
            + ConfigManager.getMirrorProximityDistance() + 1.0;
        for (final Entity entity : here.getNearbyEntities(eye, reach, reach, reach))
        {
            if (veilable(entity) && inAnyView(eye, entity.getLocation(), seeing, fixed, allOpen))
            {
                inside.add(entity);
            }
        }
    }

    /**
     * Whether a mirror may hide this entity from a viewer.
     *
     * <p>Not a player, which would drop off the tab list. Not anything else that is already hidden
     * by default for somebody: a preview's displays and their interactions, and the companion, whom
     * a hide takes from her owner and the later show hands to a stranger.
     *
     * @param entity
     *            an entity near the viewer
     * @return true if a mirror may veil it
     */
    static boolean veilable(final Entity entity)
    {
        return !(entity instanceof Player)
            && !(entity instanceof Display)
            && !(entity instanceof Interaction)
            && !FreyaCompanion.isCompanion(entity)
            && !StandIns.isStandIn(entity);
    }

    /** Whether a place is inside the view through any of these windows, as {@link #creaturesInside} judges it. */
    static boolean inAnyView(final Location eye, final Location at, final List<WindowState> seeing,
        final Map<WindowState, Windows.Whole> fixed, final Set<Long> allOpen)
    {
        final int x = at.getBlockX();
        final int y = at.getBlockY();
        final int z = at.getBlockZ();
        for (final WindowState window : seeing)
        {
            final Windows.Whole whole = fixed.get(window);
            final boolean inView = (whole != null) ? FixedView.insideFixed(window, x, y, z, whole.depth())
                : seenAndCovered(eye, window, x, y, z, seeing, allOpen);
            if (inView)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * The looser test a stand-in already shown is kept by: seen through an opening at all, the wall
     * hiding its outline or not, so one at the edge of the view is not spawned and removed each step.
     */
    static boolean stillSeen(final Location eye, final Location at, final List<WindowState> seeing,
        final Map<WindowState, Windows.Whole> fixed)
    {
        final int x = at.getBlockX();
        final int y = at.getBlockY();
        final int z = at.getBlockZ();
        for (final WindowState window : seeing)
        {
            final Windows.Whole whole = fixed.get(window);
            final boolean seen = (whole != null) ? FixedView.insideFixed(window, x, y, z, whole.depth())
                : (WindowOcclusion.seenThrough(eye, window, x, y, z, seeing, 0.0) != WindowShape.UNSEEN);
            if (seen)
            {
                return true;
            }
        }
        return false;
    }

    /** Whether a block is seen through a clipped window from an eye and lands where the wall hides the rest. */
    private static boolean seenAndCovered(final Location eye, final WindowState window, final int x, final int y,
        final int z, final List<WindowState> seeing, final Set<Long> allOpen)
    {
        final double[] rect = WindowOcclusion.seenThrough(eye, window, x, y, z, seeing, 0.0);
        return (rect != WindowShape.UNSEEN) && WindowOcclusion.coveredBy(window, rect, allOpen, Set.of(), Windows.MOST_BESIDE);
    }

    /** Hides from a viewer what is now inside their view, and shows again what no longer is. */
    static void veil(final Player player, final ViewerDrawing view, final List<Entity> inside)
    {
        final Map<UUID, Entity> now = new HashMap<>();
        inside.forEach(entity -> now.put(entity.getUniqueId(), entity));
        for (final Map.Entry<UUID, Entity> entry : view.veiled.entrySet())
        {
            if (!now.containsKey(entry.getKey()))
            {
                try
                {
                    player.showEntity(WormholeXTreme.getThisPlugin(), entry.getValue());
                }
                catch (final RuntimeException gone)
                {
                    // An entity that has since left the world is nothing to show.
                }
            }
        }
        // Removed through the set, not the map: taking one out of the map mid-loop threw as soon as
        // a second creature came in the same redraw (#716).
        now.entrySet().removeIf(entry -> !view.veiled.containsKey(entry.getKey())
            && refusesToHide(player, entry.getValue()));
        view.veiled.clear();
        view.veiled.putAll(now);
    }

    /** Whether the server will not hide this creature from the viewer: it is then not remembered as hidden. */
    private static boolean refusesToHide(final Player player, final Entity entity)
    {
        try
        {
            player.hideEntity(WormholeXTreme.getThisPlugin(), entity);
            return false;
        }
        catch (final RuntimeException refused)
        {
            return true;
        }
    }
}
