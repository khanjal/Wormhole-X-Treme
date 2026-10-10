package com.wormhole_xtreme.wormhole.model.window;

import java.util.EnumMap;
import java.util.Map;

/**
 * Why the far creatures one window shows a viewer were or were not given stand-ins, as last
 * judged, for {@code mirror debug} (#296): the first thing asked when a zombie does not show.
 *
 * <p>Per window: a creature standing in two windows' rooms and shown through one is counted by the
 * other as not in view or past the twenty.
 */
final class CreatureTally
{
    /** Why a creature in the far room was passed over. */
    enum Skip
    {
        /** Hidden from this viewer by something other than this view's own veil. */
        HIDDEN("hidden from you"),
        /** The viewer themselves, standing in the far room. */
        YOU("you"),
        /** A player this viewer cannot see, as a vanish plugin hides one. */
        PLAYER_HIDDEN("player(s) hidden from you"),
        /** A player gone invisible, spectating, riding, dead or offline since the room was read. */
        PLAYER_UNSHOWN("player(s) no longer to be shown"),
        /** Already shown through another window. */
        OTHER_WINDOW("shown through another window"),
        /** Outside the room as drawn, or where this world has nowhere to put it. */
        OUT_OF_ROOM("outside the room"),
        /** Standing on ground the drawn room does not have under it. */
        NO_FLOOR("no drawn floor under it");

        final String said;

        Skip(final String said)
        {
            this.said = said;
        }
    }

    /** In the far room, read from loaded chunks, of a type that is copied. */
    int found;
    /** Of those, players. */
    int players;
    /** Given a stand-in. */
    int shown;
    final Map<Skip, Integer> skipped = new EnumMap<>(Skip.class);

    void skip(final Skip why)
    {
        skipped.merge(why, 1, Integer::sum);
    }

    /** @return the line for {@code mirror debug}, with what the far room's reading passed over first */
    String line(final WindowState window)
    {
        final StringBuilder said = new StringBuilder();
        said.append(found).append(" found");
        if (players > 0)
        {
            said.append(" (").append(players).append(" player(s))");
        }
        if (window.notLoaded > 0)
        {
            said.append(", ").append(window.notLoaded).append(" chunk(s) not loaded");
        }
        if (window.entitiesNotLoaded > 0)
        {
            said.append(", ").append(window.entitiesNotLoaded).append(" chunk(s) whose creatures have not loaded");
        }
        if (window.notCopied > 0)
        {
            said.append(", ").append(window.notCopied).append(" not copied (items, excluded kinds, players unless shown and seen)");
        }
        int passed = 0;
        for (final Map.Entry<Skip, Integer> skip : skipped.entrySet())
        {
            said.append(", ").append(skip.getValue()).append(' ').append(skip.getKey().said);
            passed += skip.getValue();
        }
        final int unseen = found - passed - shown;
        if (unseen > 0)
        {
            said.append(", ").append(unseen).append(" not in view or past the ").append(FarCreatures.MOST_PER_VIEWER);
        }
        return said.append("; ").append(shown).append(" shown").toString();
    }
}
