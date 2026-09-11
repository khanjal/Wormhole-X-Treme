package com.wormhole_xtreme.wormhole.utils;

import org.bukkit.entity.Player;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

/**
 * The line above the hotbar.
 *
 * <p>For anything that is a status rather than a message: something that replaces itself,
 * matters while it is happening, and should not still be there ten minutes later. Transport
 * rings count down there, and a mirror names itself there when somebody walks up to it. Both
 * would be several lines of chat per visit otherwise, scrolling away whatever the player was
 * actually reading.
 *
 * <p>Cosmetic, and treated that way: a client or a fork that will not take an action bar must
 * not break the thing the player came to do. The trip still happens, the mirror still works;
 * they simply do not get told about it.
 */
public final class ActionBar
{
    /** Static use only. */
    private ActionBar()
    {
    }

    /**
     * Sends one line to a player's action bar, or quietly does not.
     *
     * @param player
     *            who to tell, which may be null for somebody who has since logged out
     * @param message
     *            what to say
     */
    public static void send(final Player player, final String message)
    {
        if (player == null)
        {
            return;
        }
        try
        {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
        }
        catch (final RuntimeException ignored)
        {
            // deliberately silent -- see the class comment
        }
    }
}
