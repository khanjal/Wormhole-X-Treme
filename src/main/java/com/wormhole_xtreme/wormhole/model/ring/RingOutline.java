/*
 *   Wormhole X-Treme Plugin for Bukkit
 *
 *   Showing one player where a ring actually is.
 */
package com.wormhole_xtreme.wormhole.model.ring;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;

/**
 * Shows one player where a ring actually is.
 *
 * <p>An idle ring is invisible, which is the point of it — the pad reads as ordinary floor
 * until it fires. That works against a player the moment a ring turns them away: they are
 * told it is recharging while standing on ground that looks like every other patch of
 * ground, with no way to tell where the thing is or how much of it they are in.
 *
 * <p>So a refusal briefly lights the pattern for that one player. It is the same drawing the
 * countdown uses, sent only to them and taken back a couple of seconds later, so nobody else
 * sees a ring flicker and nothing is written to the world.
 *
 * <p>Shown only for a ring that is <b>recharging</b>. Not for one that is mid-cycle, because
 * that pad is already lit and there is nothing to point out — and drawing over it would mean
 * putting the cycle's own lights out when this expired, which is exactly what used to happen
 * to anybody who stepped out of a ring and back in while it was counting down. Not for a
 * private pair either: somebody turned away from a ring that is not theirs has no business
 * being shown its extent, and being told plainly that it is private is enough.
 */
public final class RingOutline
{
    private RingOutline() {}

    /**
     * Lights a ring's pattern for one player, then takes it back.
     *
     * <p>Does nothing if the server has turned this off, or if there is no scheduler to take
     * the drawing down again — leaving an outline showing for good would be worse than never
     * having drawn it.
     *
     * @param player
     *            who to show it to
     * @param pair
     *            the pair it belongs to, so a running cycle is left alone
     * @param ring
     *            the ring to outline
     */
    public static void flash(final Player player, final RingPair pair, final Ring ring)
    {
        if (!ConfigManager.isRingOutlineOnRefusal())
        {
            return;
        }
        if (pair.isMidCycle())
        {
            // The pad is already lit by the cycle, so there is nothing to point out — and
            // drawing over it would mean clearing the cycle's own lights when this expires.
            return;
        }
        final World world = player.getWorld();
        final List<int[]> blocks = new java.util.ArrayList<int[]>();
        blocks.addAll(RingAnimator.openedBlocks(ring));
        blocks.addAll(RingAnimator.lightBlocks(ring));
        try
        {
            final org.bukkit.block.data.BlockData lit = ring.getLightMaterial().createBlockData();
            final org.bukkit.block.data.BlockData open = org.bukkit.Material.AIR.createBlockData();
            final int opened = RingAnimator.openedBlocks(ring).size();
            for (int i = 0; i < blocks.size(); i++)
            {
                final int[] block = blocks.get(i);
                player.sendBlockChange(new Location(world, block[0], block[1], block[2]),
                    (i < opened) ? open : lit);
            }
        }
        // Purely a courtesy. If it cannot be drawn there is nothing to undo and nothing to
        // report, and the player still has the message telling them why they are not going.
        catch (final RuntimeException ignored)
        {
            return;
        }
        clearLater(player, pair, world, blocks);
    }

    /**
     * Puts the real blocks back in view a moment later.
     *
     * @param player
     *            who was shown the outline
     * @param pair
     *            the pair, so a cycle that has since started is left alone
     * @param world
     *            the world it was drawn in
     * @param blocks
     *            what was drawn
     */
    private static void clearLater(final Player player, final RingPair pair, final World world,
        final List<int[]> blocks)
    {
        if ((WormholeXTreme.getScheduler() == null) || (WormholeXTreme.getThisPlugin() == null))
        {
            return;
        }
        WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(),
            new Runnable()
            {
                @Override
                public void run()
                {
                    if (!player.isOnline())
                    {
                        return;
                    }
                    if (pair.isMidCycle())
                    {
                        // A cycle started while this was showing and is drawing these blocks
                        // itself now. Sending the real ones back would put its lights out;
                        // it will clear up after itself when it finishes.
                        return;
                    }
                    try
                    {
                        for (final int[] block : blocks)
                        {
                            player.sendBlockChange(
                                new Location(world, block[0], block[1], block[2]),
                                world.getBlockAt(block[0], block[1], block[2]).getBlockData());
                        }
                    }
                    // A player who has changed world, or a chunk that has gone, will be sent
                    // the real blocks by the client's own refresh anyway.
                    catch (final RuntimeException ignored)
                    {
                        // deliberately silent
                    }
                }
            }, ConfigManager.getRingOutlineTicks());
    }
}
