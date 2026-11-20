package com.wormhole_xtreme.wormhole.model.window;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who a window is drawn the room for when {@code mirror-max-viewers} caps it (#613): the first to
 * look keep it while they keep looking, and the rest see the banner until somebody lets go.
 */
final class WindowAdmission
{
    /** The viewers each window is drawn for, by window name; only filled while a cap is set. */
    private static final Map<String, Set<UUID>> ADMITTED = new ConcurrentHashMap<>();

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
     */
    static void limit(final UUID id, final List<WindowState> seeing, final int cap, final String exempt)
    {
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
        seeing.removeIf(window -> !window.name().equals(exempt) && !admit(window.name(), id, cap));
    }

    /** Whether a viewer is, or now becomes, one of the viewers a window is drawn for. */
    private static boolean admit(final String name, final UUID id, final int cap)
    {
        final Set<UUID> viewers = ADMITTED.computeIfAbsent(name, key -> ConcurrentHashMap.newKeySet());
        synchronized (viewers)
        {
            return viewers.contains(id) || ((viewers.size() < cap) && viewers.add(id));
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
        final Set<UUID> viewers = ADMITTED.get(name);
        return (viewers == null) ? 0 : viewers.size();
    }
}
