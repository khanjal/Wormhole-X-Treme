package com.wormhole_xtreme.wormhole.model;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import com.wormhole_xtreme.wormhole.utils.WorldUtils;

/**
 * Where a gate's travellers arrive: out of its portal, along its facing or up from a flat one (#430).
 *
 * <p>The three bodies are moved out of {@link Stargate} as they were, over their inputs instead of
 * the gate's fields; the gate keeps the public methods, with their documentation, and assigns what
 * these give back. Pure geometry: nothing here reads the world.
 */
final class PortalExit
{
    /** Whether a block of the world is one of the portal's own, asked by the arrival point's normaliser. */
    @FunctionalInterface
    interface Inside
    {
        boolean at(int x, int y, int z);
    }

    /** Static helpers only. */
    private PortalExit()
    {
    }

    static boolean isFlat(final List<Location> portal)
    {
        if (portal.isEmpty())
        {
            return false;
        }
        final int y = portal.get(0).getBlockY();
        for (final Location block : portal)
        {
            if (block.getBlockY() != y)
            {
                return false;
            }
        }
        return true;
    }

    static Location normalized(final Location current, final BlockFace facing, final World world,
        final List<Location> portal, final Inside inside)
    {
        if ((current == null) || (facing == null))
        {
            return null;
        }
        if (!inside.at(current.getBlockX(), current.getBlockY(), current.getBlockZ()))
        {
            return null;
        }
        // Which way is out depends on how the portal lies, and the portal blocks say that
        // without needing to know which shape built the gate. A gate standing upright is
        // left through its face, along the gate's facing. A gate lying flat is left
        // upward — stepping along a horizontal facing there would slide the traveller
        // across the portal and out through the frame ring instead of clear of it.
        final boolean portalIsFlat = isFlat(portal);
        final int stepX = portalIsFlat ? 0 : facing.getModX();
        final int stepY = portalIsFlat ? 1 : facing.getModY();
        final int stepZ = portalIsFlat ? 0 : facing.getModZ();
        if ((stepX == 0) && (stepY == 0) && (stepZ == 0))
        {
            return null;
        }

        // One step does it for a portal one block thick; the loop covers a deeper one and
        // stops rather than walking away for ever if that direction cannot get out.
        final int maxSteps = 4;
        for (int step = 1; step <= maxSteps; step++)
        {
            final double x = current.getX() + (stepX * step);
            final double y = current.getY() + (stepY * step);
            final double z = current.getZ() + (stepZ * step);
            final Location candidate = new Location(world, x, y, z,
                current.getYaw(), current.getPitch());
            if (!inside.at(candidate.getBlockX(), candidate.getBlockY(), candidate.getBlockZ()))
            {
                return candidate;
            }
        }
        return null;
    }

    static Location recomputed(final World world, final BlockFace facing, final List<Location> portal)
    {
        if ((world == null) || (facing == null))
        {
            return null;
        }
        if ((portal == null) || portal.isEmpty())
        {
            return null;
        }

        int lowest = Integer.MAX_VALUE;
        for (final Location l : portal)
        {
            lowest = Math.min(lowest, l.getBlockY());
        }
        long sumX = 0;
        long sumZ = 0;
        int counted = 0;
        for (final Location l : portal)
        {
            if (l.getBlockY() == lowest)
            {
                sumX += l.getBlockX();
                sumZ += l.getBlockZ();
                counted++;
            }
        }
        if (counted == 0)
        {
            return null;
        }

        final boolean flat = isFlat(portal);
        final int stepX = flat ? 0 : facing.getModX();
        final int stepY = flat ? 1 : 0;
        final int stepZ = flat ? 0 : facing.getModZ();
        if ((stepX == 0) && (stepY == 0) && (stepZ == 0))
        {
            return null;
        }

        // Block centres, so a traveller lands in the middle of the block rather than on its
        // corner, and at its floor rather than inside it.
        final double x = Math.round((double) sumX / counted) + 0.5 + stepX;
        final double y = (double) lowest + stepY;
        final double z = Math.round((double) sumZ / counted) + 0.5 + stepZ;
        final Location fixed = new Location(world, x, y, z);
        try
        {
            fixed.setYaw(WorldUtils
                .getDegreesFromBlockFace(facing));
            fixed.setPitch(0f);
        }
        catch (final RuntimeException ignore)
        {
            // A yaw that cannot be worked out is not worth losing the position over.
        }
        return fixed;
    }
}
