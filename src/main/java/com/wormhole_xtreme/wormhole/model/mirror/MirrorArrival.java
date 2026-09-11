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
 * <p>{@code mirror link} is sugar: it works out where each banner is once, at link time, and
 * stores two ordinary {@link MirrorPoint}s. Nothing about the stored form knows a second
 * mirror was involved, which is deliberate -- the points stay valid if either mirror is later
 * renamed or removed, and there is no second kind of destination for the file format or the
 * click path to understand.
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
     * Where a player lands when a mirror opens onto this banner.
     *
     * <p>The banner's own block, facing the way the banner faces. A banner is passable, so a
     * player can stand in one -- and arriving there puts them exactly where somebody who had
     * just reached out and touched it would be, looking out into the room rather than at the
     * cloth.
     *
     * <p>It used to be the block in front, which reads the same in an open room and badly
     * everywhere else: one block of clearance the builder did not choose is one block that can
     * be a wall, a drop, a fence or the far side of a doorway. The banner's own block is the
     * one place somebody deliberately put something, so it is the one place known to be clear.
     *
     * <p>Handed to {@link WorldUtils#findSafePlayerLocation} afterwards all the same, the way a
     * gate's exit and a beam's destination are. A banner hung high on a wall has nothing under
     * it, and that helper is what the other two mechanics already use to correct for it.
     *
     * @param banner
     *            the banner block being arrived at
     * @return where to arrive, or null if the block is not a banner this can read a facing from
     */
    public static Location atTheBanner(final Block banner)
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
        final Location standing = banner.getLocation().add(0.5, 0.0, 0.5);
        standing.setYaw(yawOf(facing));
        standing.setPitch(0.0f);
        final Location safe = WorldUtils.findSafePlayerLocation(standing);
        return (safe == null) ? standing : safe;
    }

    /**
     * The yaw a player should face to be looking the way a banner faces.
     *
     * <p>Not {@code WorldUtils.getDegreesFromBlockFace}, which answers for the four cardinals
     * and returns 0 -- due south -- for everything else. That is fine for a gate, whose parts
     * only ever face a cardinal, and wrong here: a freestanding banner rotates through sixteen
     * positions, so twelve of them would turn an arriving player south regardless of which way
     * they had just stepped out of.
     *
     * <p>Minecraft yaw is 0 at south and increases clockwise, which is where these numbers
     * come from rather than from a compass.
     *
     * @param facing
     *            the direction the banner faces
     * @return the yaw in degrees
     */
    static float yawOf(final BlockFace facing)
    {
        switch (facing)
        {
            case SOUTH: return 0.0f;
            case SOUTH_SOUTH_WEST: return 22.5f;
            case SOUTH_WEST: return 45.0f;
            case WEST_SOUTH_WEST: return 67.5f;
            case WEST: return 90.0f;
            case WEST_NORTH_WEST: return 112.5f;
            case NORTH_WEST: return 135.0f;
            case NORTH_NORTH_WEST: return 157.5f;
            case NORTH: return 180.0f;
            case NORTH_NORTH_EAST: return 202.5f;
            case NORTH_EAST: return 225.0f;
            case EAST_NORTH_EAST: return 247.5f;
            case EAST: return 270.0f;
            case EAST_SOUTH_EAST: return 292.5f;
            case SOUTH_EAST: return 315.0f;
            case SOUTH_SOUTH_EAST: return 337.5f;
            default: return 0.0f;
        }
    }
}
