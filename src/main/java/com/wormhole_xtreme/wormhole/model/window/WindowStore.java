package com.wormhole_xtreme.wormhole.model.window;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.block.BlockState;

/**
 * What {@link Windows} remembers between sweeps, in one place (#430).
 *
 * <p>Moved out of {@code Windows} so the clusters that come after it can read and write the
 * same state without being nested in a 3,000-line class. Package-private, and used through
 * static imports, so the bodies that moved with them did not change. {@link Windows#clear()}
 * still resets all of it.
 */
final class WindowStore
{
    /** Static state only. */
    private WindowStore()
    {
    }

    /** How many changes behind any window have been reported, ever: the clock {@link #CHANGED} is read on. */
    static long changes;

    /** The count {@link #changes} stood at when a block last changed behind each window, by name. */
    static final Map<String, Long> CHANGED = new HashMap<>();

    /** When a block first changed behind each window since its view was last put in, by name. */
    static final Map<String, Long> FIRST_CHANGED = new HashMap<>();

    static long workSecond;
    static int workSpent;

    /** Blocks of held rooms this sweep has redrawn so far. */
    static long sweepRoomBlocks;

    /** How many sweeps have run: where the next one starts among the viewers. */
    static long sweeps;

    /** Every window the last sweep found, by mirror name. */
    static final Map<String, WindowState> ACTIVE = new HashMap<>();

    /** Windows found by the sweep in progress. */
    static final Map<String, WindowState> OFFERED = new HashMap<>();

    /** What each viewer's client has been told, by player. */
    static final Map<UUID, ViewerDrawing> VIEWS = new ConcurrentHashMap<>();

    /** Block states reused between redraws, by world and block, rather than read again. */
    static final Map<String, Map<Long, BlockState>> STATES = new HashMap<>();

    /** When {@link #STATES} was last trimmed to the blocks still being drawn. */
    static long trimmedAt;

    /** Players who asked to see the world as it is, with no view drawn for them: admins checking. */
    static final Set<UUID> BLIND = ConcurrentHashMap.newKeySet();

    /** Players who asked for one mirror drawn whole and unlimited, by the mirror's name. */
    static final Map<UUID, String> FULL = new ConcurrentHashMap<>();

    /** Told after each redraw which windows the viewer is drawn: an open gate's horizon follows it. */
    static Windows.DrawnListener drawnListener = (viewer, player, windows) ->
    {
    };
}
