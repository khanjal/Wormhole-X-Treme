package com.wormhole_xtreme.wormhole.model.window;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;

/**
 * One viewer's drawing, as last sent.
 *
 * <p>What this client has been sent, what it is still owed while a room streams in a tick at a
 * time, which mirrors it is looking through, which creatures are hidden inside a view, and where
 * its eye was when that was worked out.
 *
 * <p>Mutable and package-private on purpose, the same as {@link WindowState}: it is the
 * scratch pad {@link Windows} keeps for one viewer. Split out of {@link Windows}.
 */
final class ViewerDrawing
{
    final World world;
    /** What the client has been sent, as sent. */
    final Map<Long, BlockData> drawn = new HashMap<>();
    /** What it is still owed, in the order to send it: a block to draw, or null for the real one. */
    final Map<Long, BlockData> pending = new LinkedHashMap<>();
    boolean streamQueued;
    Set<String> mirrors = Set.of();
    Set<String> fixedNames = Set.of();
    final Map<UUID, Entity> veiled = new HashMap<>();
    /** The stand-ins shown for far creatures, by the creature's id (#296). */
    final Map<UUID, StandIns.StandIn> standIns = new HashMap<>();
    /** When a stand-in's spawn was last refused, by the creature's id. */
    final Map<UUID, Long> refused = new HashMap<>();
    /** Why each window's far creatures were or were not shown, by window name, as last judged. */
    final Map<String, CreatureTally> tallies = new HashMap<>();
    long fullAt;
    long composedAt;
    int generation;
    int undrawable;
    long eye = Long.MIN_VALUE;
    long chunk = Long.MIN_VALUE;
    Location pendingEye;
    boolean catchUpQueued;
    Windows.Redraw lastRedraw;
    /** Each clipped window's far part as last judged, by mirror name; see {@link Windows#NEAR_DISTANCE}. */
    final Map<String, Windows.Far> far = new HashMap<>();
    /** The least time before this viewer's next redraw on a move, longer after a slow one. */
    long rest = Windows.REDRAW_MILLIS;
    String stamp = "";

    ViewerDrawing(final World world)
    {
        this.world = world;
    }
}
