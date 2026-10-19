package com.wormhole_xtreme.wormhole.model.ring;

import java.util.List;

import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.utils.ChatText;
import com.wormhole_xtreme.wormhole.utils.Messages;
import com.wormhole_xtreme.wormhole.utils.ActionBar;

/**
 * What a transport ring tells the people standing in it, each in their own language (#26).
 *
 * <p>Almost everything here goes to the <b>action bar</b> rather than to chat. A ring says
 * something every second while it counts down, and again when it fires; in chat that would be
 * six lines per trip, scrolling away whatever the player was actually reading. Above the
 * hotbar it is a status that replaces itself and then goes, which is what all of this is.
 *
 * <p>Chat is kept for the one case a player has to act on and might miss: being turned away
 * from a ring they are not allowed to use.
 *
 * <p>Messages are only sent on entering a ring, never on every step taken inside one. The
 * move path fires on each block boundary crossed, so a player walking about inside a ring
 * that is cooling down would otherwise be told so several times a second.
 */
public final class RingMessages
{
    /** Warmer colour for the moment of transport itself. */
    private static final String ACTIVE = ChatText.NAME_COLOUR;

    private RingMessages() {}

    /**
     * Sends one line to the action bar.
     *
     * @param player
     *            who to tell
     * @param message
     *            what to say
     */
    private static void status(final Player player, final String message)
    {
        ActionBar.send(player, message);
    }

    /**
     * @param passenger
     *            someone in the rings
     * @return the player they are, or null for anything else
     */
    private static Player playerOf(final RingPassenger passenger)
    {
        if ((passenger instanceof BukkitRingPassenger bukkitPassenger)
            && (bukkitPassenger.getEntity() instanceof Player player))
        {
            return player;
        }
        return null;
    }

    /**
     * Somebody has just walked into a ring and set it going.
     *
     * @param player
     *            who walked in
     * @param destination
     *            what the far end is called, or empty if it has no name
     */
    public static void engaged(final Player player, final String destination)
    {
        status(player, ChatText.info((destination == null) || destination.isEmpty()
            ? Messages.get(player, "ring.engaged")
            : Messages.get(player, "ring.engaged-to", ChatText.name(destination))));
    }

    /**
     * The countdown, once a second: one lookup per passenger, since each may read another language.
     *
     * @param passengers
     *            everyone in either end
     * @param seconds
     *            how many seconds are left
     */
    public static void counting(final List<RingPassenger> passengers, final int seconds)
    {
        final String count = ChatText.value(String.valueOf(seconds));
        for (final RingPassenger passenger : passengers)
        {
            final Player player = playerOf(passenger);
            if (player != null)
            {
                final String line;
                if (seconds == 1)
                {
                    line = Messages.get(player, "ring.counting.one", count);
                }
                else
                {
                    line = Messages.get(player, "ring.counting.other", count);
                }
                status(player, ChatText.info(line));
            }
        }
    }

    /**
     * Everyone left before the rings committed.
     *
     * @param player
     *            who to tell, if anybody is still about
     */
    public static void stoodDown(final Player player)
    {
        status(player, ChatText.info(Messages.get(player, "ring.stood-down")));
    }

    /**
     * The rings have committed and are on their way up.
     *
     * @param passengers
     *            everyone in either end
     */
    public static void committed(final List<RingPassenger> passengers)
    {
        for (final RingPassenger passenger : passengers)
        {
            final Player player = playerOf(passenger);
            if (player != null)
            {
                status(player, ChatText.status(ACTIVE + Messages.get(player, "ring.committed")));
            }
        }
    }

    /**
     * Somebody has arrived at the far end.
     *
     * @param player
     *            the traveller
     * @param destination
     *            what this end is called, or empty if it has no name
     */
    public static void arrived(final Player player, final String destination)
    {
        // Already in the name colour, so the destination needs nothing of its own.
        status(player, ChatText.status(ACTIVE + ((destination == null) || destination.isEmpty()
            ? Messages.get(player, "ring.arrived")
            : Messages.get(player, "ring.arrived-at", destination))));
    }

    /**
     * Somebody walked into a ring that has fired too recently to fire again.
     *
     * <p>The one message that has to carry a number, because "not yet" without saying how
     * long leaves a player standing on a pad wondering whether it is broken.
     *
     * @param player
     *            who walked in
     * @param millisLeft
     *            how much cooldown is left
     */
    public static void recharging(final Player player, final long millisLeft)
    {
        final long seconds = Math.max(1L, (millisLeft + 999L) / 1000L);
        final String count = ChatText.value(String.valueOf(seconds));
        final String line;
        if (seconds == 1)
        {
            line = Messages.get(player, "ring.recharging.one", count);
        }
        else
        {
            line = Messages.get(player, "ring.recharging.other", count);
        }
        status(player, ChatText.info(line));
    }

    /**
     * Somebody walked into a ring whose far end has been built over.
     *
     * <p>Chat rather than the action bar, and worded to send them looking in the right
     * place. A ring that will not fire and cannot say why is indistinguishable from a broken
     * one, and the thing to fix is usually at the other end entirely — so this names which
     * end, says which of the two problems it is, and says that only the inside counts.
     *
     * @param player
     *            who walked in
     * @param destination
     *            what the blocked end is called, or empty if it has no name
     * @param why
     *            what is wrong with it
     */
    public static void cannotReceive(final Player player, final String destination,
        final RingBlockage why)
    {
        final String end = ((destination == null) || destination.isEmpty()) ? null : ChatText.name(destination);
        player.sendMessage(ChatText.error(reason(player, why, end)));
        if ((why == RingBlockage.CEILING_TOO_HIGH) || (why == RingBlockage.CEILING_TOO_LOW))
        {
            player.sendMessage(ChatText.error(Messages.get(player, "ring.blocked.ceiling-hint")));
            return;
        }
        if (why == RingBlockage.NO_HEADROOM)
        {
            player.sendMessage(ChatText.error(Messages.get(player, "ring.blocked.headroom-hint",
                ChatText.value(String.valueOf(Ring.STACK_HEIGHT)))));
            return;
        }
        player.sendMessage(ChatText.error(Messages.get(player, "ring.blocked.clear-hint")));
    }

    /**
     * What to say about one kind of blockage.
     *
     * @param player
     *            who will read it
     * @param why
     *            what is wrong
     * @param end
     *            the far end's name, already coloured, or null if it has none
     * @return the sentence, a whole one for each way of naming the end
     */
    private static String reason(final Player player, final RingBlockage why, final String end)
    {
        if (why == RingBlockage.NO_GROUND)
        {
            return (end == null) ? Messages.get(player, "ring.blocked.no-ground")
                : Messages.get(player, "ring.blocked.no-ground-named", end);
        }
        if (why == RingBlockage.CEILING_TOO_HIGH)
        {
            return (end == null) ? Messages.get(player, "ring.blocked.ceiling-too-high")
                : Messages.get(player, "ring.blocked.ceiling-too-high-named", end);
        }
        if (why == RingBlockage.CEILING_TOO_LOW)
        {
            return (end == null) ? Messages.get(player, "ring.blocked.ceiling-too-low")
                : Messages.get(player, "ring.blocked.ceiling-too-low-named", end);
        }
        if (why == RingBlockage.NO_HEADROOM)
        {
            return (end == null) ? Messages.get(player, "ring.blocked.no-headroom")
                : Messages.get(player, "ring.blocked.no-headroom-named", end);
        }
        return (end == null) ? Messages.get(player, "ring.blocked.obstructed")
            : Messages.get(player, "ring.blocked.obstructed-named", end);
    }

    /**
     * Somebody walked into a ring that is already running a cycle.
     *
     * @param player
     *            who walked in
     */
    public static void busy(final Player player)
    {
        status(player, ChatText.info(Messages.get(player, "ring.busy")));
    }

    /**
     * Somebody walked into a ring they are not allowed to use.
     *
     * <p>Chat rather than the action bar. This is the one thing here a player has to do
     * something about, and it should still be there when they look.
     *
     * @param player
     *            who walked in
     */
    public static void notYours(final Player player)
    {
        player.sendMessage(ChatText.error(Messages.get(player, "ring.private")));
    }
}
