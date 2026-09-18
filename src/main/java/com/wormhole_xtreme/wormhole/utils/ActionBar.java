package com.wormhole_xtreme.wormhole.utils;

import java.util.logging.Level;

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
 *
 * <p>CraftBukkit is such a fork: it has neither {@code Player.spigot()} nor BungeeCord's chat
 * classes, so the call fails to link rather than throwing.
 */
public final class ActionBar
{
    /** Set once the server has shown it cannot link an action bar, so it is not tried again. */
    private static volatile boolean unavailable;

    /** Static use only. */
    private ActionBar()
    {
    }

    /** Tests only: other tests read the action bar, so one that breaks it must put it back. */
    static void forgetUnavailable()
    {
        unavailable = false;
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
        if (player == null || unavailable)
        {
            return;
        }
        try
        {
            SpigotBar.send(player, message);
        }
        catch (final LinkageError missing)
        {
            unavailable = true;
            PluginLog.log(Level.INFO, "This server has no Spigot action bar, so ring countdowns and"
                + " mirror names will not show above the hotbar.");
        }
        catch (final RuntimeException ignored)
        {
            // deliberately silent -- see the class comment
        }
    }

    /**
     * The only code naming Spigot's chat API, so a server without it fails to link this class,
     * inside {@link #send}'s try, and never {@link ActionBar} in its caller.
     */
    private static final class SpigotBar
    {
        /** Static use only. */
        private SpigotBar()
        {
        }

        /**
         * @param player
         *            who to tell
         * @param message
         *            what to say
         */
        static void send(final Player player, final String message)
        {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
        }
    }
}
