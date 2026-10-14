package com.wormhole_xtreme.wormhole.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Settings a config.yml may still hold that the plugin no longer reads (#589).
 *
 * <p>Left in the file as the admin wrote them, and named once at startup so nobody tunes one
 * expecting it to do something. The three view depths that became {@code view-depth} are carried
 * over to it, when the file has no {@code view-depth} yet.
 */
final class RetiredSettings
{
    private static final String VIEW_DEPTH = "view-depth";

    private static final String MIRROR_DEPTH = "mirror-view-depth";

    private static final String GATE_FULL_DEPTH = "gate-view-full-depth";

    private static final String GATE_FIRST_DEPTH = "gate-view-depth";

    /** Every setting removed in 1.11.0, as config.yml spells it. */
    static final List<String> KEYS = List.of(
        "permissions-auto-fallback", "entity-scan-interval-ticks", "mirror-proximity-ticks",
        "mirror-fog-at-depth", "gate-sound-ambient-ticks", "gate-arrival-splash-ticks",
        "gate-iris-step-ticks", "gate-iris-sweep-max-ticks", "gate-iris-horizon-ticks",
        "ring-max-link-height", "ring-default-material",
        "ring-countdown-ticks", "ring-deploy-ticks", "ring-settle-ticks", "ring-flash-ticks",
        "ring-lights-linger-ticks", "ring-hold-ticks", "ring-outline-ticks",
        "beam-envelop-ticks", "beam-vanish-at-step", "beam-rise-ticks", "beam-teleport-at-step",
        "beam-descend-ticks", "beam-fade-ticks",
        GATE_FIRST_DEPTH, GATE_FULL_DEPTH, MIRROR_DEPTH);

    /**
     * The view depth carried over from the old settings, and which of them it came from.
     *
     * @param depth
     *            blocks, 4 to 160
     * @param from
     *            the old keys it was read from
     */
    record Carried(int depth, List<String> from)
    {
    }

    /** Static helpers only; never instantiated. */
    private RetiredSettings()
    {
    }

    /**
     * The retired settings this file holds, in either spelling.
     *
     * @param map
     *            the parsed config file
     * @return each as config.yml spells it, in the order of {@link #KEYS}
     */
    static List<String> presentIn(final Map<String, Object> map)
    {
        return KEYS.stream().filter(key -> valueOf(map, key) != null).toList();
    }

    /**
     * The view depth an older file asked for, when it has no {@code view-depth} of its own.
     *
     * <p>The shallower of {@code mirror-view-depth} and {@code gate-view-full-depth}, since each was
     * lowered to make a view cheaper and the one depth now does both. A fill of 0 meant a gate's view
     * stayed at its first step, {@code gate-view-depth} or 32. {@code gate-view-depth} alone is a
     * first step only, which is fixed now, so it carries nothing over.
     *
     * @param map
     *            the parsed config file
     * @return the depth and where it came from, or null if there is nothing to carry over
     */
    static Carried carriedViewDepth(final Map<String, Object> map)
    {
        if (valueOf(map, VIEW_DEPTH) != null)
        {
            return null;
        }
        final List<String> from = new ArrayList<>();
        int depth = Integer.MAX_VALUE;
        final Integer mirror = wholeAt(map, MIRROR_DEPTH);
        if (mirror != null)
        {
            from.add(MIRROR_DEPTH);
            depth = mirror;
        }
        final Integer full = wholeAt(map, GATE_FULL_DEPTH);
        if (full != null)
        {
            from.add(GATE_FULL_DEPTH);
            depth = Math.min(depth, (full > 0) ? full : firstStep(map, from));
        }
        return from.isEmpty() ? null : new Carried(Math.max(4, Math.min(160, depth)), from);
    }

    /** A gate's old first step, for a fill of 0, noting the key when the file had one. */
    private static int firstStep(final Map<String, Object> map, final List<String> from)
    {
        final Integer first = wholeAt(map, GATE_FIRST_DEPTH);
        if (first == null)
        {
            return ConfigManager.GATE_FIRST_STEP_DEPTH;
        }
        from.add(GATE_FIRST_DEPTH);
        return first;
    }

    /** A key's value as a whole number, or null if the file lacks it or it is not one. */
    private static Integer wholeAt(final Map<String, Object> map, final String key)
    {
        final Object value = valueOf(map, key);
        return (value == null) ? null : (Integer) ConfigurationYAML.asKindOf(Integer.valueOf(0), value);
    }

    /** A key's value, under its kebab spelling or its old enum one, or null. */
    private static Object valueOf(final Map<String, Object> map, final String key)
    {
        final Object kebab = map.get(key);
        return (kebab != null) ? kebab : map.get(key.toUpperCase(Locale.ROOT).replace('-', '_'));
    }

    /**
     * What to say at startup: the carried depth, if any, and the retired settings left over.
     *
     * @param present
     *            the retired settings the file holds
     * @param carried
     *            the depth carried over, or null
     * @return the lines, none for a file with nothing retired in it
     */
    static List<String> notice(final List<String> present, final Carried carried)
    {
        final List<String> lines = new ArrayList<>();
        final List<String> ignored = new ArrayList<>(present);
        if (carried != null)
        {
            ignored.removeAll(carried.from());
            lines.add("view-depth was added at " + carried.depth() + ", carried over from "
                + String.join(" and ", carried.from()) + "; it sets how far mirrors and gate views reach.");
        }
        if (!ignored.isEmpty())
        {
            lines.add("These settings no longer exist and are ignored: " + String.join(", ", ignored));
        }
        return lines;
    }
}
