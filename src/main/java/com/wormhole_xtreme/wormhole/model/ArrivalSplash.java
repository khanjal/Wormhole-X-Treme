package com.wormhole_xtreme.wormhole.model;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * The moment of water a traveller sees as they come out of a gate (#430, #734).
 */
final class ArrivalSplash
{
    /** Static helpers only. */
    private ArrivalSplash()
    {
    }

    /** Ticks a traveller sees water as they come out of a gate: short, or the client predicts swimming. */
    static final long SPLASH_TICKS = 20L;

    /** How often the arrival splash is redrawn while it is showing, in ticks. */
    private static final long SPLASH_REDRAW_INTERVAL = 2L;

    /**
     * Shows a traveller a moment of water as they come out of a gate.
     *
     * <p>The client draws its underwater overlay from whichever block it believes its camera
     * is in, so one block sent at eye height is the whole effect: they surface out of the
     * event horizon and it clears. Nobody else sees anything, and nothing is written.
     *
     * <p>Deliberately brief, and that is not just taste. Water is physics to the client, not
     * decoration -- for as long as it believes it is submerged it predicts swimming, and the
     * server does not agree. A short flash is over before that argument can be felt. It is
     * the one drawing here that makes the client's world <em>less</em> solid than the real
     * one, which is the direction that caused trouble before, so it is kept to a moment.
     *
     * <p>Only sent where the eye is in open air. Water drawn over somebody's ceiling would be
     * a strange thing to see, and the arrival point is out in the open in any case.
     *
     * @param player
     *            the traveller who has just arrived
     */
    public static void splashArrival(final Player player)
    {
        if ((player == null) || !player.isOnline())
        {
            return;
        }
        try
        {
            final Block eye = player.getEyeLocation().getBlock();
            if (!eye.getType().isAir())
            {
                return;
            }
            final Location at = eye.getLocation();
            // Drawn again every couple of ticks rather than once. An arrival hands the
            // client a fresh copy of the chunk, and a fresh copy erases anything drawn into
            // the old one -- so a single block change lands before the chunk does and is
            // wiped by it. How long that takes is not observable from here and is not fixed:
            // a hop to a nearby gate reuses chunks the client already has, while a trip
            // across the world makes it fetch everything from scratch. The portal redraw
            // above hit exactly this and answered it the same way.
            for (long t = 0L; t < SPLASH_TICKS; t += SPLASH_REDRAW_INTERVAL)
            {
                drawSplash(player, at, true, t);
            }
            drawSplash(player, at, false, SPLASH_TICKS);
        }
        catch (final RuntimeException ignore)
        {
            // Decoration. Not worth a log line on the travel path.
        }
    }

    /**
     * Draws or clears the arrival splash after a delay.
     *
     * @param player
     *            the traveller
     * @param at
     *            the block their eye was in when they landed
     * @param water
     *            true to show water, false to put the real block back
     * @param delay
     *            how many ticks to wait
     */
    private static void drawSplash(final Player player, final Location at, final boolean water,
        final long delay)
    {
        WormholeXTreme.getScheduler().runTaskLater(WormholeXTreme.getThisPlugin(),
            () ->
            {
                if (!player.isOnline())
                {
                    return;
                }
                try
                {
                    if (water)
                    {
                        // Only while they are still in it. Somebody who has walked on is
                        // no longer surfacing, and redrawing would put water behind them.
                        if (player.getEyeLocation().getBlock().getLocation().equals(at))
                        {
                            player.sendBlockChange(at, Material.WATER.createBlockData());
                        }
                        return;
                    }
                    // Read again rather than remember: somebody may have put something
                    // there in the meantime, and the real block is always the right
                    // answer. A chunk arriving later than this shows the truth anyway,
                    // so the failure that is left over is a splash nobody saw rather
                    // than water nobody can clear.
                    final Block now = at.getWorld().getBlockAt(at.getBlockX(),
                        at.getBlockY(), at.getBlockZ());
                    player.sendBlockChange(at, now.getBlockData());
                }
                catch (final RuntimeException ignore)
                {
                    // As above.
                }
            }, Math.max(1L, delay));
    }
}
