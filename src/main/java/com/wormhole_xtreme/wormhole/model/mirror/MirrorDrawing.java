package com.wormhole_xtreme.wormhole.model.mirror;

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
 * <p>Mutable and package-private on purpose, the same as {@link MirrorWindowState}: it is the
 * scratch pad {@link MirrorWindows} keeps for one viewer. Split out of {@link MirrorWindows}.
 */
final class MirrorDrawing
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
    long fullAt;
    long composedAt;
    int generation;
    int undrawable;
    long eye = Long.MIN_VALUE;
    long chunk = Long.MIN_VALUE;
    Location pendingEye;
    boolean catchUpQueued;
    MirrorWindows.Redraw lastRedraw;
    /** Each clipped window's far part as last judged, by mirror name; see {@link MirrorWindows#NEAR_DISTANCE}. */
    final Map<String, MirrorWindows.Far> far = new HashMap<>();
    /** The least time before this viewer's next redraw on a move, longer after a slow one. */
    long rest = MirrorWindows.REDRAW_MILLIS;
    String stamp = "";

    MirrorDrawing(final World world)
    {
        this.world = world;
    }
}
