package com.wormhole_xtreme.wormhole.model;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.utils.MaterialUtils;

/**
 * Where an open gate's horizon is drawn while its iris is shut, and who it is drawn for (#734).
 *
 * <p>{@link StargateBlockSetup} and the iris layering call it; the geometry here is arithmetic
 * over the gate's facing.
 */
final class PortalBackdrop
{
    /** Static helpers only. */
    private PortalBackdrop()
    {
    }

    /**
     * The cells an open gate's event horizon is shown in while its iris is shut.
     *
     * <p>A gate's opening is one block thick on every shipped shape, so a closed iris fills it
     * and there is nowhere left inside the ring for the water to be. The cell one block behind
     * each portal cell is outside the gate and ordinarily empty, and showing the horizon there
     * puts it back where somebody can see it: through a glass iris from the front, and plainly
     * from behind.
     *
     * <p>Behind rather than in front on purpose. Moving the iris forward would work as well
     * geometrically, but the iris is real blocks -- it would have to find room among whatever a
     * player has built, and every check that asks where the barrier is would have to follow it.
     * The horizon is drawn to clients and collides with nothing, so it can go somewhere the
     * iris cannot.
     *
     * @return the cells, which may be empty
     */
    static List<Location> portalBackdropCells(final Stargate gate)
    {
        return offsetCells(gate, -1);
    }

    /**
     * The cells one block in front of each portal cell, where somebody behind the gate is shown
     * the iris.
     *
     * <p>The mirror of {@link #portalBackdropCells}. From behind, the horizon takes the ring and
     * the iris goes a block further off, so the layer nearer the viewer is always the one in
     * the ring and the far one never lands on their own side of the gate.
     *
     * @return the cells, in the same order as the portal cells; empty without a facing
     */
    static List<Location> portalForecourtCells(final Stargate gate)
    {
        return offsetCells(gate, 1);
    }

    /**
     * Each portal cell moved one block along the gate's facing, or against it.
     *
     * @param sign
     *            1 for along the facing, -1 for against it
     * @return the cells, index for index with the portal cells
     */
    // Redrawn as players move near a gate, so it stays a plain loop.
    @SuppressWarnings("java:S9391")
    private static List<Location> offsetCells(final Stargate gate, final int sign)
    {
        final List<Location> cells = new ArrayList<>();
        if ((gate == null) || (gate.getGateWorld() == null) || (gate.getGateFacing() == null))
        {
            return cells;
        }
        final BlockFace facing = gate.getGateFacing();
        for (final Location portal : gate.getGatePortalBlocks())
        {
            cells.add(new Location(gate.getGateWorld(),
                (double) portal.getBlockX() + (sign * facing.getModX()),
                (double) portal.getBlockY() + (sign * facing.getModY()),
                (double) portal.getBlockZ() + (sign * facing.getModZ())));
        }
        return cells;
    }

    /**
     * Whether somebody at this eye position is looking at a gate's front, the side the woosh
     * comes out of.
     *
     * <p>Measured from the middle of the first portal cell. Every shipped opening is one
     * block thick, so any cell gives the same answer. Standing in the plane itself counts as
     * the front, the view a gate has always had.
     *
     * @param eye
     *            the viewer's eye
     * @return true in front of the gate or in its plane, false behind it
     */
    static boolean seesFront(final Stargate gate, final Location eye)
    {
        final BlockFace facing = gate.getGateFacing();
        if ((facing == null) || (eye == null) || gate.getGatePortalBlocks().isEmpty())
        {
            return true;
        }
        final Location p = gate.getGatePortalBlocks().get(0);
        return IrisLayering.seesFront(facing,
            new IrisLayering.At(p.getBlockX(), p.getBlockY(), p.getBlockZ()),
            eye.getX(), eye.getY(), eye.getZ());
    }

    /**
     * Whether a backdrop cell is free to be drawn in.
     *
     * <p>Only air. A player who has built behind a gate should see what they built, not a
     * sheet of water over it, and a cell holding part of another gate is not this one's to
     * draw in either.
     *
     * @param at
     *            the cell
     * @return true if the horizon may be shown there
     */
    static boolean backdropIsFree(final Location at)
    {
        final Block block = at.getWorld().getBlockAt(at.getBlockX(), at.getBlockY(), at.getBlockZ());
        if (!MaterialUtils.isAirMaterial(block.getType()))
        {
            return false;
        }
        // Air is not enough any more. A gate's opening is server-side air since its iris became
        // a drawing, so two gates built a block apart would draw their layers into each other's
        // rings -- and hand back air over the neighbour's iris when they were taken back.
        return !StargateManager.isPortalBlock(block);
    }

    /**
     * Shows or takes back the horizon behind a gate whose iris is shut.
     *
     * @param show
     *            true to draw it, false to hand the real blocks back
     */
    static void sendPortalBackdrop(final Stargate gate, final boolean show)
    {
        final List<Location> cells = portalBackdropCells(gate);
        if (cells.isEmpty())
        {
            return;
        }
        for (final Player player : gate.getGateWorld().getPlayers())
        {
            if (StargateBlockSetup.isNearEnoughToRedraw(gate, player.getLocation()))
            {
                sendPortalBackdropTo(player, gate, show);
            }
        }
    }

    /**
     * The same, to one player.
     *
     * @param player
     *            who to show
     * @param show
     *            true to draw the horizon, false to hand the real blocks back
     */
    static void sendPortalBackdropTo(final Player player, final Stargate gate, final boolean show)
    {
        final List<Location> cells = portalBackdropCells(gate);
        if (cells.isEmpty())
        {
            return;
        }
        final BlockData horizon = show
            ? MaterialUtils.drawnAcross(gate.getEffectivePortalMaterial(), gate.getGateFacing()) : null;
        for (final Location at : cells)
        {
            if (!backdropIsFree(at))
            {
                continue;
            }
            player.sendBlockChange(at, (horizon != null) ? horizon
                : at.getWorld().getBlockAt(at.getBlockX(), at.getBlockY(), at.getBlockZ()).getBlockData());
        }
    }
}
