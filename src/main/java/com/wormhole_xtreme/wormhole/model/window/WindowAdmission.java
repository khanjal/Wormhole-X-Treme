package com.wormhole_xtreme.wormhole.model.window;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who a window is drawn the room for when {@code mirror-max-viewers} caps it (#613): the first to look
 * keep it while they keep looking, and the rest see the banner until somebody lets go.
 *
 * <p>A place is stamped each time its holder is seen looking, and one not seen for {@link #STALE_MILLIS}
 * is taken back, so whoever logs out, changes world, goes blind or otherwise stops being asked about
 * never holds one for good.
 */
final class WindowAdmission
{
    /** How long a place is held without its holder being seen looking; the sweep asks about a viewer every second. */
    static final long STALE_MILLIS = 10_000L;

    /** The viewers each window is drawn for and when each was last seen, by window name; only filled while a cap is set. */
    private static final Map<String, Map<UUID, Long>> ADMITTED = new ConcurrentHashMap<>();

    /** Static helpers only. */
    private WindowAdmission()
    {
    }

    /**
     * Takes from the windows a viewer would see those that are already drawn for as many as the cap, and
     * lets go of any they no longer see, so somebody waiting can have it.
     *
     * @param id
     *            the viewer
     * @param seeing
     *            the windows they would be drawn; shortened in place
     * @param cap
     *            the most viewers a window is drawn for; above 0
     * @param exempt
     *            the name of a window drawn for them whatever the cap, or null
     * @param now
     *            the time
     */
    static void limit(final UUID id, final List<WindowState> seeing, final int cap, final String exempt, final long now)
    {
        ADMITTED.values().forEach(viewers -> viewers.values().removeIf(seenAt -> (now - seenAt) > STALE_MILLIS));
        // Drawn whole for them whatever the cap, so it needs no place of theirs.
        if (exempt != null)
        {
            Optional.ofNullable(ADMITTED.get(exempt)).ifPresent(viewers -> viewers.remove(id));
        }
        final Set<String> seen = new HashSet<>();
        seeing.forEach(window -> seen.add(window.name()));
        ADMITTED.forEach((name, viewers) ->
        {
            if (!seen.contains(name))
            {
                viewers.remove(id);
            }
        });
        seeing.removeIf(window -> !window.name().equals(exempt) && !admit(window.name(), id, cap, now));
    }

    /** Whether a viewer is, or now becomes, one of the viewers a window is drawn for; stamps them if so. */
    private static boolean admit(final String name, final UUID id, final int cap, final long now)
    {
        final Map<UUID, Long> viewers = ADMITTED.computeIfAbsent(name, key -> new ConcurrentHashMap<>());
        synchronized (viewers)
        {
            if (viewers.containsKey(id) || (viewers.size() < cap))
            {
                viewers.put(id, now);
                return true;
            }
            return false;
        }
    }

    /** Lets a viewer go from every window, as they leave. */
    static void forget(final UUID id)
    {
        ADMITTED.values().forEach(viewers -> viewers.remove(id));
    }

    /** Forgets a window being taken back. */
    static void forget(final String name)
    {
        ADMITTED.remove(name);
    }

    /** Forgets everyone, for a reload or a test. */
    static void clear()
    {
        ADMITTED.clear();
    }

    /** @return how many viewers a window is drawn for, for a test */
    static int count(final String name)
    {
        final Map<UUID, Long> viewers = ADMITTED.get(name);
        return (viewers == null) ? 0 : viewers.size();
    }
}
