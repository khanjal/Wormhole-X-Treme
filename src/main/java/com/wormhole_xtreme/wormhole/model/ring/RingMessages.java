package com.wormhole_xtreme.wormhole.model.ring;

import java.util.List;

import org.bukkit.entity.Player;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

/**
 * What a transport ring tells the people standing in it.
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
    /** Colour codes matching the plugin's own message headers. */
    private static final String NORMAL = "§3:: §7";

    /** Warmer colour for the moment of transport itself. */
    private static final String ACTIVE = "§3:: §b";

    /** Error colouring, as the gate messages use. */
    private static final String ERROR = "§3:: §5";

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
        try
        {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
        }
        // Cosmetic. A client or fork that will not take an action bar must not break a trip.
        catch (final RuntimeException ignored)
        {
            // deliberately silent
        }
    }

    /**
     * Sends one line to everyone in a list who is a player.
     *
     * @param passengers
     *            who is in the rings
     * @param message
     *            what to say
     */
    private static void statusAll(final List<RingPassenger> passengers, final String message)
    {
        for (final RingPassenger passenger : passengers)
        {
            if (passenger instanceof BukkitRingPassenger)
            {
                final org.bukkit.entity.Entity entity = ((BukkitRingPassenger) passenger).getEntity();
                if (entity instanceof Player)
                {
                    status((Player) entity, message);
                }
            }
        }
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
        final String where = (destination == null) || destination.isEmpty()
            ? "Transport rings engaging."
            : ("Transport rings engaging — travelling to " + destination + ".");
        status(player, NORMAL + where + " Step clear to cancel.");
    }

    /**
     * The countdown, once a second.
     *
     * @param passengers
     *            everyone in either end
     * @param seconds
     *            how many seconds are left
     */
    public static void counting(final List<RingPassenger> passengers, final int seconds)
    {
        statusAll(passengers, NORMAL + "Transport in " + seconds
            + (seconds == 1 ? " second" : " seconds") + "...");
    }

    /**
     * Everyone left before the rings committed.
     *
     * @param player
     *            who to tell, if anybody is still about
     */
    public static void stoodDown(final Player player)
    {
        status(player, NORMAL + "Transport rings powering down.");
    }

    /**
     * The rings have committed and are on their way up.
     *
     * @param passengers
     *            everyone in either end
     */
    public static void committed(final List<RingPassenger> passengers)
    {
        statusAll(passengers, ACTIVE + "Rings deploying. Hold still.");
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
        status(player, ACTIVE + ((destination == null) || destination.isEmpty()
            ? "Transport complete."
            : ("Arrived at " + destination + ".")));
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
        status(player, NORMAL + "Rings recharging. Ready in " + seconds
            + (seconds == 1 ? " second." : " seconds."));
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
        final String which = ((destination == null) || destination.isEmpty())
            ? "The other end of these rings" : ("The " + destination + " end");
        player.sendMessage(ERROR + which + reason(why));
        if ((why == RingBlockage.CEILING_TOO_HIGH) || (why == RingBlockage.CEILING_TOO_LOW))
        {
            player.sendMessage(ERROR + "A ceiling ring drops its rings to the floor and they "
                + "stack up from there, so it needs one near enough to reach.");
            return;
        }
        if (why == RingBlockage.NO_HEADROOM)
        {
            player.sendMessage(ERROR + "The rings stand " + Ring.STACK_HEIGHT + " blocks tall "
                + "around whoever arrives, so they need " + Ring.STACK_HEIGHT
                + " blocks of clear air above the pad -- more than a person needs to stand "
                + "in it.");
            return;
        }
        player.sendMessage(ERROR + "Clear the inside of that ring and try again. What is "
            + "built around it does not matter.");
    }

    /**
     * What to say about one kind of blockage.
     *
     * @param why
     *            what is wrong
     * @return the end of the sentence
     */
    private static String reason(final RingBlockage why)
    {
        if (why == RingBlockage.NO_GROUND)
        {
            return " has a hole in its floor.";
        }
        if (why == RingBlockage.CEILING_TOO_HIGH)
        {
            return " is too far above its floor.";
        }
        if (why == RingBlockage.CEILING_TOO_LOW)
        {
            return " has no room between it and the floor.";
        }
        if (why == RingBlockage.NO_HEADROOM)
        {
            return " has too low a ceiling for the rings.";
        }
        return " has something built inside it.";
    }

    /**
     * Somebody walked into a ring that is already running a cycle.
     *
     * @param player
     *            who walked in
     */
    public static void busy(final Player player)
    {
        status(player, NORMAL + "Rings are already in use.");
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
        player.sendMessage(ERROR + "These transport rings are private.");
    }
}
