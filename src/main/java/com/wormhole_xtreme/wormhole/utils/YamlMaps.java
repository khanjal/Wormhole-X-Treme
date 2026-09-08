package com.wormhole_xtreme.wormhole.utils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The two casts every YAML reader in this plugin was making for itself.
 *
 * <p>SnakeYAML hands back {@code Object}. Every caller wanted a map or a list, so every caller
 * wrote the same three lines -- test the type, cast it, and decide what an unusable value
 * means -- and every one of them carried its own {@code @SuppressWarnings("unchecked")} to
 * quiet the cast. Thirteen of those had accumulated across five classes, and seven were
 * written at method level, wide enough to swallow any other unchecked cast that later landed
 * in the same method without anybody noticing.
 *
 * <p>Doing it once puts the suppression in one place, where the reason for it is written down
 * rather than assumed, and lets the readers say what they mean: an empty result is "nothing
 * usable here", which reads the same as "nothing here" at every call site that was already
 * treating the two alike.
 *
 * <h2>What the cast is actually promising</h2>
 *
 * <p>Not as much as its signature suggests, and deliberately no less than the code it
 * replaces. YAML permits non-string keys -- {@code 1: foo} parses to an {@code Integer} key --
 * so a hand-edited file can produce a map these methods will hand back as
 * {@code Map<String, Object>} without every key being one. Reading such a key throws
 * {@code ClassCastException} at the point of use, exactly as it did when each caller cast for
 * itself. Every caller here already runs inside a try/catch that logs the entry and skips it,
 * which is the behaviour worth keeping: one bad key costs one gate, not the whole file.
 */
public final class YamlMaps
{
    /** Static helpers only; never instantiated. */
    private YamlMaps()
    {
    }

    /**
     * A parsed YAML value as a map.
     *
     * @param value
     *            whatever the parser produced, may be null
     * @return the value as a map, or an empty map if it is not one
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMap(final Object value)
    {
        return (value instanceof Map) ? (Map<String, Object>) value : Collections.emptyMap();
    }

    /**
     * A parsed YAML value as a list.
     *
     * @param value
     *            whatever the parser produced, may be null
     * @return the value as a list, or an empty list if it is not one
     */
    @SuppressWarnings("unchecked")
    public static List<Object> asList(final Object value)
    {
        return (value instanceof List) ? (List<Object>) value : Collections.emptyList();
    }
}
