package com.wormhole_xtreme.wormhole.model.ring;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * Who has a ring preview standing, and the reasons it must come down (#332).
 *
 * <p>One preview per player. A stack left standing is a wall that only its viewer can see, so
 * everything that could leave one behind has a way out here: the player quitting or changing
 * world, the ring being removed, a real cycle starting on the pair, the plugin being disabled,
 * and a timeout that any preview command starts again.
 *
 * <p>A preview and a real cycle never share a pair. One is refused on a pair that is mid-cycle
 * and a cycle that starts clears every preview of its pair first, which is the rule
 * {@link RingOutline} follows, so the two drawings cannot fight over the same blocks.
 */
public final class RingPreviews
{
    /** How long a preview stands with nothing asked of it: five minutes. */
    static final long TIMEOUT_TICKS = 20L * 60L * 5L;

    /** The standing previews by viewer. */
    private static final Map<UUID, RingPreview> SHOWN = new ConcurrentHashMap<>();

    /** The pending timeout for each, so a preview command can start it again. */
    private static final Map<UUID, Integer> TIMEOUTS = new ConcurrentHashMap<>();

    private RingPreviews() {}

    /**
     * The preview a player has standing.
     *
     * @param viewer
     *            the player
     * @return their preview, or null
     */
    public static RingPreview of(final Player viewer)
    {
        return SHOWN.get(viewer.getUniqueId());
    }

    /**
     * Lights the pad of an end for one player.
     *
     * @param viewer
     *            who is shown it
     * @param pair
     *            the pair the end belongs to
     * @param ring
     *            the end
     * @return what happened
     */
    public static RingPreview.Result show(final Player viewer, final RingPair pair, final Ring ring)
    {
        final RingPreview.Result refused = refusal(viewer, pair, ring);
        if (refused != null)
        {
            return refused;
        }
        final RingPreview existing = SHOWN.get(viewer.getUniqueId());
        touch(viewer);
        if ((existing != null) && (existing.getRing() == ring))
        {
            return RingPreview.Result.ALREADY_SHOWING;
        }
        begin(viewer, pair, ring);
        return RingPreview.Result.DONE;
    }

    /**
     * Raises the stack of an end for one player, lighting the pad first if it is not.
     *
     * @param viewer
     *            who is shown it
     * @param pair
     *            the pair the end belongs to
     * @param ring
     *            the end
     * @return what happened
     */
    public static RingPreview.Result deploy(final Player viewer, final RingPair pair,
        final Ring ring)
    {
        final RingPreview.Result refused = refusal(viewer, pair, ring);
        if (refused != null)
        {
            return refused;
        }
        RingPreview preview = SHOWN.get(viewer.getUniqueId());
        touch(viewer);
        if ((preview == null) || (preview.getRing() != ring))
        {
            preview = begin(viewer, pair, ring);
        }
        return preview.deploy();
    }

    /**
     * Runs the light through the stack a player has standing.
     *
     * @param viewer
     *            the player
     * @return what happened
     */
    public static RingPreview.Result light(final Player viewer)
    {
        final RingPreview preview = SHOWN.get(viewer.getUniqueId());
        if (preview == null)
        {
            return RingPreview.Result.NO_STACK;
        }
        if (preview.getPair().isMidCycle())
        {
            return RingPreview.Result.MID_CYCLE;
        }
        touch(viewer);
        return preview.light();
    }

    /**
     * Brings the rings of the stack a player has standing home.
     *
     * @param viewer
     *            the player
     * @return what happened
     */
    public static RingPreview.Result collapse(final Player viewer)
    {
        final RingPreview preview = SHOWN.get(viewer.getUniqueId());
        if (preview == null)
        {
            return RingPreview.Result.NO_STACK;
        }
        if (preview.getPair().isMidCycle())
        {
            return RingPreview.Result.MID_CYCLE;
        }
        touch(viewer);
        return preview.collapse();
    }

    /**
     * Takes a player's preview back, if they have one.
     *
     * <p>Also what quitting and changing world do.
     *
     * @param viewer
     *            the player
     * @return true if there was one to take back
     */
    public static boolean clear(final Player viewer)
    {
        return drop(viewer.getUniqueId());
    }

    /**
     * Takes back every preview of a pair, for everybody.
     *
     * <p>Called when the pair is removed and when a real cycle starts on it.
     *
     * @param pair
     *            the pair
     */
    public static void clearFor(final RingPair pair)
    {
        if (SHOWN.isEmpty())
        {
            return;
        }
        for (final Map.Entry<UUID, RingPreview> entry : new ArrayList<>(SHOWN.entrySet()))
        {
            if (entry.getValue().getPair() == pair)
            {
                drop(entry.getKey());
            }
        }
    }

    /**
     * Takes every preview back, for plugin disable.
     */
    public static void clearAll()
    {
        final List<UUID> viewers = new ArrayList<>(SHOWN.keySet());
        for (final UUID viewer : viewers)
        {
            drop(viewer);
        }
    }

    /**
     * Why an end cannot be drawn for this player, if it cannot.
     *
     * <p>A drawing is sent by position to whatever world the viewer is in, and a deploy measures
     * the world through {@code getBlockAt}, so an end in another world would be drawn over
     * unrelated terrain, and one in an unloaded chunk would be loaded just to be measured.
     *
     * @param viewer
     *            who would see it
     * @param pair
     *            the pair the end belongs to
     * @param ring
     *            the end
     * @return the refusal, or null if it can be drawn
     */
    private static RingPreview.Result refusal(final Player viewer, final RingPair pair, final Ring ring)
    {
        if (pair.isMidCycle())
        {
            return RingPreview.Result.MID_CYCLE;
        }
        if (!pair.getWorldName().equals(viewer.getWorld().getName()))
        {
            return RingPreview.Result.WRONG_WORLD;
        }
        if (!viewer.getWorld().isChunkLoaded(ring.getAnchorX() >> 4, ring.getAnchorZ() >> 4))
        {
            return RingPreview.Result.NOT_LOADED;
        }
        return null;
    }

    /**
     * Forgets a preview that took itself back after a failed step.
     *
     * @param id
     *            the viewer
     * @param preview
     *            the preview that failed
     */
    private static void forget(final UUID id, final RingPreview preview)
    {
        if (SHOWN.remove(id, preview))
        {
            cancelTimeout(id);
        }
    }

    /**
     * Makes a preview of an end, replacing any the player already has, and lights its pad.
     *
     * @param viewer
     *            who is shown it
     * @param pair
     *            the pair the end belongs to
     * @param ring
     *            the end
     * @return the new preview
     */
    private static RingPreview begin(final Player viewer, final RingPair pair, final Ring ring)
    {
        drop(viewer.getUniqueId());
        final RingPreview preview = new RingPreview(viewer, pair, ring,
            new PreviewSurroundings(viewer, viewer.getWorld(), pair));
        SHOWN.put(viewer.getUniqueId(), preview);
        preview.setOnFailure(() -> forget(viewer.getUniqueId(), preview));
        touch(viewer);
        preview.show();
        return preview;
    }

    /**
     * Takes one viewer's preview and timeout away.
     *
     * @param id
     *            the viewer
     * @return true if there was a preview
     */
    private static boolean drop(final UUID id)
    {
        cancelTimeout(id);
        final RingPreview preview = SHOWN.remove(id);
        if (preview == null)
        {
            return false;
        }
        preview.clear();
        return true;
    }

    /**
     * Starts a viewer's timeout again, from now.
     *
     * @param viewer
     *            the viewer
     */
    private static void touch(final Player viewer)
    {
        final UUID id = viewer.getUniqueId();
        cancelTimeout(id);
        final RingPreview preview = SHOWN.get(id);
        if (preview == null)
        {
            return;
        }
        final int pending = WormholeXTreme.getScheduler().scheduleSyncDelayedTask(
            WormholeXTreme.getThisPlugin(), () ->
            {
                TIMEOUTS.remove(id);
                if (SHOWN.remove(id, preview))
                {
                    preview.clear();
                }
            }, TIMEOUT_TICKS);
        TIMEOUTS.put(id, Integer.valueOf(pending));
    }

    /**
     * Cancels a viewer's pending timeout.
     *
     * @param id
     *            the viewer
     */
    private static void cancelTimeout(final UUID id)
    {
        final Integer pending = TIMEOUTS.remove(id);
        final BukkitScheduler scheduler = WormholeXTreme.getScheduler();
        if ((pending != null) && (scheduler != null))
        {
            scheduler.cancelTask(pending.intValue());
        }
    }
}
