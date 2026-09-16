package com.wormhole_xtreme.wormhole.model.mirror;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;

import com.wormhole_xtreme.wormhole.model.mirror.MirrorWindow.Spot;

/**
 * What a viewer can see past: whether a block is solid, and where the ground ends.
 *
 * <p>Every answer here is a block read, and a block read is the expensive part of drawing a
 * room -- so each is cached per world and thrown away every few seconds
 * ({@link MirrorWindows#RESAMPLE_MILLIS}) rather than watched for changes. A wall somebody
 * knocks through is noticed within that, which is soon enough for what these answers are for:
 * whether a line of sight is clear, and whether a block is really open air.
 *
 * <p>Split out of {@link MirrorWindows}, which had grown to hold every part of drawing a room.
 * These three caches were touched by nothing else in it.
 */
final class MirrorSight
{
    /** Whether each block behind an opening is really empty, by world and block, briefly. */
    private static final Map<String, Map<Long, Boolean>> EMPTY = new HashMap<>();

    /** The highest block that is not air in each real column, by world and column, as briefly. */
    private static final Map<String, Map<Long, Integer>> TOPS = new HashMap<>();

    /** When {@link #EMPTY} was last cleared. */
    private static long emptyReadAt;

    /** Whether each real block between viewers and openings is solid, by world and block. */
    private static final Map<String, Map<Long, Boolean>> SOLID = new HashMap<>();

    /** When {@link #SOLID} was last cleared. */
    private static long solidReadAt;

    /**
     * Whether nothing solid stands between an eye and the middle of a block's front face.
     *
     * <p>The front face, not the block's middle: seen from an angle, the line to the middle
     * passes through the wall block beside the opening first, and the opening would count as
     * hidden while its face was in plain view. Walked in short steps, stopping just short of the
     * face. Solid means occluding, so glass and the banner in front do not count as in the way.
     */
    static boolean clearLine(final World here, final Location eye, final Spot cell,
        final Spot into, final long now)
    {
        final double dx = ((cell.x() + 0.5) - (0.51 * into.x())) - eye.getX();
        final double dy = (cell.y() + 0.5) - eye.getY();
        final double dz = ((cell.z() + 0.5) - (0.51 * into.z())) - eye.getZ();
        final double length = Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
        for (double along = 0.4; along < length; along += 0.4)
        {
            final double t = along / length;
            final int x = (int) Math.floor(eye.getX() + (dx * t));
            final int y = (int) Math.floor(eye.getY() + (dy * t));
            final int z = (int) Math.floor(eye.getZ() + (dz * t));
            if (solidHere(here, x, y, z, now))
            {
                return false;
            }
        }
        return true;
    }

    /** Whether the real block here hides what is behind it, remembered for a few seconds. */
    static boolean solidHere(final World here, final int x, final int y, final int z,
        final long now)
    {
        if ((now - solidReadAt) >= MirrorWindows.RESAMPLE_MILLIS)
        {
            SOLID.clear();
            solidReadAt = now;
        }
        return SOLID.computeIfAbsent(here.getName(), name -> new HashMap<>()).computeIfAbsent(
            MirrorWindows.key(x, y, z), cell -> here.isChunkLoaded(x >> 4, z >> 4)
                && here.getBlockAt(x, y, z).getBlockData().isOccluding());
    }

    /** Whether a real block is empty, read afresh: above its column's highest block it is. */
    static boolean reallyEmpty(final World here, final int x, final int y, final int z, final long now)
    {
        return (y > topHere(here, x, z, now))
            || (here.isChunkLoaded(x >> 4, z >> 4) && here.getBlockAt(x, y, z).isEmpty());
    }

    /** The highest block that is not air in a real column, remembered for a few seconds. */
    private static int topHere(final World here, final int x, final int z, final long now)
    {
        if ((now - emptyReadAt) >= MirrorWindows.RESAMPLE_MILLIS)
        {
            EMPTY.clear();
            TOPS.clear();
            emptyReadAt = now;
        }
        if (!here.isChunkLoaded(x >> 4, z >> 4))
        {
            return Integer.MAX_VALUE;
        }
        return TOPS.computeIfAbsent(here.getName(), name -> new HashMap<>()).computeIfAbsent(
            MirrorWindows.chunkKey(x, z), column -> here.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE));
    }
    /** Forgets every cached read, for a test or a reload. */
    static void clear()
    {
        SOLID.clear();
        EMPTY.clear();
        TOPS.clear();
        solidReadAt = 0L;
        emptyReadAt = 0L;
    }

    /** Static state only. */
    private MirrorSight()
    {
    }
}
