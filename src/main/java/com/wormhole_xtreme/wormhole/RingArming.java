package com.wormhole_xtreme.wormhole;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;

import com.wormhole_xtreme.wormhole.model.ring.Ring;
import com.wormhole_xtreme.wormhole.model.ring.RingIndex;
import com.wormhole_xtreme.wormhole.model.ring.RingMessages;
import com.wormhole_xtreme.wormhole.model.ring.RingOutline;
import com.wormhole_xtreme.wormhole.model.ring.RingPair;
import com.wormhole_xtreme.wormhole.model.ring.RingPermissions;
import com.wormhole_xtreme.wormhole.model.ring.RingSounds;
import com.wormhole_xtreme.wormhole.model.ring.RingTransit;

/**
 * What stepping onto a transport ring pad does: arms the pair, or says why it will not.
 *
 * <p>Split out of {@link WormholeXTremePlayerListener}, which calls {@link #handleRingMoveEvent}
 * from {@code onPlayerMove} once no gate has claimed the step. It runs for every player on every
 * block boundary crossed, so it stays a static call with no allocation of its own.
 */
final class RingArming
{
    private RingArming() {}

    /**
     * Whether the ring will not have this player right now, having said why.
     *
     * <p>Only said to somebody who has just walked in. This runs on every block boundary
     * crossed, so a player wandering about on a pad they cannot use would otherwise be told
     * about it several times a second.
     *
     * @param player
     *            whoever stepped onto the pad
     * @param pair
     *            the pair they are standing in
     * @param end
     *            which end of it
     * @param justEntered
     *            whether this step took them in, rather than around inside
     * @return true if they were refused
     */
    private static boolean ringRefuses(final Player player, final RingPair pair,
        final RingIndex.RingEnd end, final boolean justEntered)
    {
        // Arming is a use of the ring, so the same permission governs it as governs being
        // carried. Somebody who cannot travel by a pair should not be able to set it off for
        // everybody else either.
        if (!RingPermissions.mayUse(player, pair))
        {
            if (justEntered)
            {
                RingMessages.notYours(player);
            }
            return true;
        }
        final long now = System.currentTimeMillis();
        if (pair.canFire(now))
        {
            return false;
        }
        if (justEntered)
        {
            // Two different reasons to refuse, and a player standing on a silent pad deserves
            // to know which: one of them ends by itself and the other does not.
            if (pair.getCooldownUntil() > now)
            {
                RingMessages.recharging(player, pair.getCooldownUntil() - now);
                // A recharging ring is invisible, so being told it is not ready leaves
                // somebody standing on ground that looks like any other. Show them where it
                // is. Not done for a ring that is mid-cycle: that pad is already lit, so
                // there is nothing to point out and the outline would put those lights out
                // when it expired.
                RingOutline.flash(player, pair, end.getRing());
            }
            else
            {
                RingMessages.busy(player);
            }
        }
        return true;
    }

    /**
     * Arms a transport ring if this move took the player into one.
     *
     * <p>The whole of ring detection on the move path, and it is deliberately three lines of
     * work: a hash lookup for the block, a question to the pair about whether it will fire,
     * and a permission check. Every player crossing every block boundary runs this, so it
     * cannot afford to be anything more.
     *
     * <p>Only the interior arms a ring. Standing on the edge is crossing a threshold rather
     * than standing in the thing, and firing on it would take people who were walking past.
     *
     * <p>There is no arrival guard here and none is needed. The cooldown is shared by both
     * ends of a pair, so somebody who has just landed cannot re-fire the ring they landed in
     * — the settle-move after a teleport arrives long inside the cooldown that same cycle
     * started. Somebody still standing there and still moving a full cooldown later does
     * fire it again, and travels back, which is the intended behaviour rather than a bug.
     *
     * @param event
     *            the move being considered
     */
    static void handleRingMoveEvent(final PlayerMoveEvent event)
    {
        final Location to = event.getTo();
        if ((to == null) || (to.getWorld() == null))
        {
            return;
        }
        // Deliberately not behind a "did the block change" guard, unlike gate detection: a
        // ring has to re-arm for somebody who stayed inside it after a trip, and that player
        // is crossing no block boundaries. The cost of asking is one hash lookup, and on a
        // server with no rings at all it is not even that.
        if (!RingIndex.hasAny())
        {
            return;
        }
        final RingIndex.RingEnd end =
            RingIndex.volumeAt(
                to.getWorld().getName(), to.getBlockX(), to.getBlockY(), to.getBlockZ());
        if (end == null)
        {
            return;
        }
        final RingPair pair = end.getPair();
        final Player player = event.getPlayer();

        // Whether this step took them into the ring or merely around inside it. Messages are
        // for arriving somewhere, and this path runs on every block boundary crossed, so a
        // player wandering about on a pad that is recharging would otherwise be told about it
        // several times a second. Arming still happens on any move inside, which is what lets
        // somebody who stays put after a trip be carried back once the cooldown passes.
        final Location from = event.getFrom();
        final boolean justEntered = (from.getWorld() == null)
            || (RingIndex.volumeAt(
                from.getWorld().getName(), from.getBlockX(), from.getBlockY(), from.getBlockZ()) != end);

        if (ringRefuses(player, pair, end, justEntered))
        {
            return;
        }
        // justEntered is passed on rather than gating the call: arming still has to happen on
        // any move inside, which is what carries somebody back who stayed put after a trip.
        // Only what the ring says about refusing is limited to walking in.
        if (RingTransit.start(pair, player, justEntered))
        {
            final Ring far = pair.opposite(end.getRing());
            RingMessages.engaged(
                player, far == null ? "" : far.getName());
        }
        else if (justEntered)
        {
            // It refused. Whatever the reason, the ring itself is invisible, and being told
            // something is wrong while standing on ground that looks like any other is no
            // help at all — least of all when the thing to fix is inside the ring and they
            // cannot see where it is.
            RingOutline.flash(player, pair, end.getRing());
            RingSounds.refused(player);
        }
    }
}
