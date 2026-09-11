package com.wormhole_xtreme.wormhole.model.mirror;

import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every quantum mirror on the server, by name and by the block a player clicks.
 *
 * <p>Two maps over the same mirrors, because the two questions asked of this registry have
 * very different costs. An admin naming one happens a handful of times; a player right-clicking
 * a block happens constantly, on every block on the server, and has to be answered without
 * walking the list. {@link #at(MirrorBlock)} is that second question and is a hash lookup.
 *
 * <p>Names are matched without regard to case, the way gate and beam names are, so an admin who
 * typed {@code Museum} once is not caught out by {@code museum} later. The mirror keeps the
 * spelling it was given for display.
 *
 * <p>Its own registry rather than an entry in {@code BeamManager}: gates, rings and beam
 * destinations each keep their own, and a mirror is a standalone mechanic rather than beaming
 * in a costume.
 */
public final class MirrorManager
{
    /** By lower-cased name. */
    private static final Map<String, QuantumMirror> BY_NAME = new ConcurrentHashMap<>();

    /** By the block clicked, for the interact handler. */
    private static final Map<MirrorBlock, QuantumMirror> BY_BLOCK = new ConcurrentHashMap<>();

    /** Static registry only. */
    private MirrorManager()
    {
    }

    private static String key(final String name)
    {
        return (name == null) ? null : name.toLowerCase(Locale.ROOT);
    }

    /**
     * Adds a mirror, replacing any of the same name.
     *
     * <p>Replacing by name also clears the old banner out of the block index. Leaving it would
     * make a banner that is no longer any mirror's still answer to a click, which is the kind
     * of bug that only shows up after somebody rebinds a name and wonders why the old banner
     * still works.
     *
     * @param mirror
     *            the mirror to register
     */
    public static void add(final QuantumMirror mirror)
    {
        if (mirror == null)
        {
            return;
        }
        final QuantumMirror replaced = BY_NAME.put(key(mirror.name()), mirror);
        if ((replaced != null) && !replaced.banner().equals(mirror.banner()))
        {
            BY_BLOCK.remove(replaced.banner());
        }
        BY_BLOCK.put(mirror.banner(), mirror);
    }

    /**
     * The mirror with this name.
     *
     * @param name
     *            the name, matched without regard to case
     * @return the mirror, or null if there is none
     */
    public static QuantumMirror byName(final String name)
    {
        return (name == null) ? null : BY_NAME.get(key(name));
    }

    /**
     * The mirror whose banner is this block.
     *
     * <p>The hot one: asked on every right-click of any block, so it is a hash lookup and
     * answers null for the overwhelming majority.
     *
     * @param block
     *            the block clicked
     * @return the mirror, or null if that block is not a mirror
     */
    public static QuantumMirror at(final MirrorBlock block)
    {
        return (block == null) ? null : BY_BLOCK.get(block);
    }

    /**
     * Removes a mirror by name.
     *
     * @param name
     *            the name, matched without regard to case
     * @return the mirror that was removed, or null if there was none
     */
    public static QuantumMirror remove(final String name)
    {
        final QuantumMirror removed = (name == null) ? null : BY_NAME.remove(key(name));
        if (removed != null)
        {
            BY_BLOCK.remove(removed.banner());
        }
        return removed;
    }

    /** @return every mirror, in no particular order */
    public static Collection<QuantumMirror> all()
    {
        return Collections.unmodifiableCollection(BY_NAME.values());
    }

    /** @return how many mirrors are registered */
    public static int count()
    {
        return BY_NAME.size();
    }

    /** Forgets everything, for a reload and for tests. */
    public static void clear()
    {
        BY_NAME.clear();
        BY_BLOCK.clear();
    }
}
