package com.wormhole_xtreme.wormhole.model.mirror;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Rotatable;

import com.wormhole_xtreme.wormhole.utils.WorldUtils;

/**
 * Where a player arrives when a mirror is pointed at another mirror's banner.
 *
 * <p>{@code mirror link} is sugar: it works out the spot in front of the target banner once,
 * at link time, and stores an ordinary {@link MirrorPoint}. Nothing about the stored form
 * knows a second mirror was involved, which is deliberate -- the point stays valid if the
 * target mirror is later renamed or removed, and there is no second kind of destination for
 * the file format or the click path to understand.
 *
 * <p>The consequence, worth knowing: linking is a snapshot, not a subscription. Move the
 * target banner and the first mirror still sends people to where it used to be. Re-running
 * {@code mirror link} is how you follow it.
 */
public final class MirrorArrival
{
    /** Static helpers only. */
    private MirrorArrival()
    {
    }

    /**
     * Which way a banner faces, whichever of the two kinds it is.
     *
     * <p>The two banner families carry their facing in different interfaces and there is no
     * shared one: a wall banner is {@link Directional} and faces one of four cardinals, a
     * freestanding banner is {@link Rotatable} and faces one of sixteen. Both are accepted,
     * because requiring a wall would rule out a banner on a post in the middle of a room --
     * which is most of a museum corridor.
     *
     * @param data
     *            the banner's block data
     * @return the direction it faces, or null if this block is neither kind
     */
    public static BlockFace facingOf(final BlockData data)
    {
        if (data instanceof Directional directional)
        {
            return directional.getFacing();
        }
        if (data instanceof Rotatable rotatable)
        {
            return rotatable.getRotation();
        }
        return null;
    }

    /**
     * The spot a player should land in when stepping out of this banner.
     *
     * <p>One block out in the direction the banner faces, looking the same way -- a player
     * steps *through* a mirror and comes out facing away from it, rather than turning round to
     * look back at the banner they just left.
     *
     * <p>Handed to {@link WorldUtils#findSafePlayerLocation} afterwards, the same as a gate's
     * exit and a beam's destination: the block in front of a banner is very often a wall, a
     * drop, or the inside of whatever the banner is mounted on, and that helper is what the
     * other two mechanics already use to correct for it.
     *
     * @param banner
     *            the banner block being linked to
     * @return where to arrive, or null if the block is not a banner this can read a facing from
     */
    public static Location inFrontOf(final Block banner)
    {
        if (banner == null)
        {
            return null;
        }
        final BlockFace facing = facingOf(banner.getBlockData());
        if (facing == null)
        {
            return null;
        }
        final Block ahead = banner.getRelative(facing);
        final Location standing = ahead.getLocation().add(0.5, 0.0, 0.5);
        final Float yaw = WorldUtils.getDegreesFromBlockFace(facing);
        if (yaw != null)
        {
            standing.setYaw(yaw);
        }
        standing.setPitch(0.0f);
        final Location safe = WorldUtils.findSafePlayerLocation(standing);
        return (safe == null) ? standing : safe;
    }
}
