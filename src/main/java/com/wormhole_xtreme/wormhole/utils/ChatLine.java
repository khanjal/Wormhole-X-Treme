package com.wormhole_xtreme.wormhole.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.config.ConfigManager;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;

/**
 * One chat line built from text and commands a player can click (#538), sent whichever way the
 * server can.
 *
 * <p>Paper gets Adventure components, Spigot BungeeCord's. CraftBukkit has neither, and gets
 * {@link #plain()}: the same line with each command spelled out, as it read before links
 * existed. So does a server with {@code clickable-chat} off. The missing API fails to link in a
 * nested class, inside {@link #sendClickable}'s try, as {@link ActionBar}'s does, and never at
 * plugin load.
 */
public final class ChatLine
{
    /** Set once the server has shown it has no Adventure, so it is not tried again. */
    private static volatile boolean noAdventure;

    /** Set once the server has shown it has no BungeeCord chat either. */
    private static volatile boolean noSpigot;

    private final List<Part> parts = new ArrayList<>();

    /**
     * One run of the line.
     *
     * @param label
     *            what a clickable line shows
     * @param plain
     *            what a plain line shows instead
     * @param command
     *            what a click runs or fills in, or null for text
     * @param hover
     *            what pointing at it shows
     * @param suggest
     *            true to fill the chat box in rather than run
     */
    record Part(String label, String plain, String command, String hover, boolean suggest)
    {
    }

    /** Use {@link #of}. */
    private ChatLine()
    {
    }

    /** Tests only: a test that hides a chat API must put it back for the next. */
    static void forgetUnavailable()
    {
        noAdventure = false;
        noSpigot = false;
    }

    /**
     * @param text
     *            what the line opens with, header included
     * @return a new line
     */
    public static ChatLine of(final String text)
    {
        return new ChatLine().text(text);
    }

    /**
     * @param text
     *            words nobody clicks
     * @return this line
     */
    public ChatLine text(final String text)
    {
        parts.add(new Part(text, text, null, null, false));
        return this;
    }

    /**
     * A command that runs when clicked, shown as it is typed.
     *
     * @param shown
     *            the command, coloured, as the line names it
     * @param command
     *            what a click runs, from its slash
     * @return this line
     */
    public ChatLine run(final String shown, final String command)
    {
        return link(shown, shown, command, "Click to run " + ChatText.command(command), false);
    }

    /**
     * A command still to be finished, which a click types into the chat box.
     *
     * @param shown
     *            the command, coloured, as the line names it
     * @param command
     *            what a click fills in, from its slash
     * @return this line
     */
    public ChatLine suggest(final String shown, final String command)
    {
        return link(shown, shown, command, "Click to fill in " + ChatText.command(command.trim()), true);
    }

    /**
     * A link that reads differently when it cannot be clicked, such as a button.
     *
     * @param label
     *            what a clickable line shows, as {@code [Place]}
     * @param plain
     *            what a plain line shows instead, as {@code place}
     * @param command
     *            what a click runs or fills in, from its slash
     * @param hover
     *            what pointing at it shows
     * @param suggest
     *            true to fill the chat box in, for a command that needs more typed after it
     * @return this line
     */
    public ChatLine link(final String label, final String plain, final String command, final String hover,
        final boolean suggest)
    {
        parts.add(new Part(label, plain, command, hover, suggest));
        return this;
    }

    /** @return the line as a server without clickable chat shows it */
    public String plain()
    {
        final StringBuilder out = new StringBuilder();
        for (final Part part : parts)
        {
            out.append(part.plain());
        }
        return out.toString();
    }

    /**
     * The parts as sent, each opening in the colour the one before it left off in.
     *
     * <p>A legacy colour code runs on to the end of a plain line, but not past the end of one
     * component, so the {@code .} after a white command would come out white instead of grey.
     *
     * @return the parts, each label prefixed with the colour carried into it
     */
    List<Part> pieces()
    {
        final List<Part> pieces = new ArrayList<>(parts.size());
        String colour = "";
        for (final Part part : parts)
        {
            final String label = colour + part.label();
            colour = lastColour(label, colour);
            pieces.add(new Part(label, part.plain(), part.command(), part.hover(), part.suggest()));
        }
        return pieces;
    }

    /**
     * @param text
     *            legacy text
     * @param before
     *            the colour in force before it
     * @return the last colour code in it, {@code §r} included, or {@code before} if it has none
     */
    static String lastColour(final String text, final String before)
    {
        for (int i = text.length() - 2; i >= 0; i--)
        {
            if (text.charAt(i) == '§' && ("0123456789abcdefr".indexOf(Character.toLowerCase(text.charAt(i + 1))) >= 0))
            {
                return text.substring(i, i + 2);
            }
        }
        return before;
    }

    /**
     * Sends the line, clickable if the server and the setting allow, plain otherwise.
     *
     * @param player
     *            who to tell, which may be null for somebody who has since logged out
     */
    public void send(final Player player)
    {
        if (player == null)
        {
            return;
        }
        final boolean linked = parts.stream().anyMatch(part -> part.command() != null);
        if (!linked || !ConfigManager.isClickableChat() || !sendClickable(player, pieces()))
        {
            player.sendMessage(plain());
        }
    }

    /**
     * @param player
     *            who to tell
     * @param pieces
     *            the line, from {@link #pieces()}
     * @return true once it is sent; false to send it plain instead
     */
    private static boolean sendClickable(final Player player, final List<Part> pieces)
    {
        if (!noAdventure)
        {
            try
            {
                if (PaperChat.send(player, pieces))
                {
                    return true;
                }
            }
            catch (final LinkageError notPaper)
            {
                // Spigot: no Adventure, which is expected, so it is not logged.
                noAdventure = true;
            }
            catch (final RuntimeException refused)
            {
                return false;
            }
        }
        if (noSpigot)
        {
            return false;
        }
        try
        {
            SpigotChat.send(player, pieces);
            return true;
        }
        catch (final LinkageError missing)
        {
            noSpigot = true;
            PluginLog.log(Level.INFO, "This server has neither Paper's nor Spigot's chat components, so commands"
                + " named in chat cannot be clicked. They are spelled out instead.");
            return false;
        }
        catch (final RuntimeException refused)
        {
            return false;
        }
    }

    /**
     * The only code naming Adventure, so a server without it fails to link this class, inside
     * {@link #sendClickable}'s try.
     */
    private static final class PaperChat
    {
        /** Static use only. */
        private PaperChat()
        {
        }

        /**
         * @param player
         *            who to tell
         * @param pieces
         *            the line
         * @return false if this player is not an Adventure audience, as on Spigot with another
         *         plugin's Adventure visible
         */
        static boolean send(final Player player, final List<Part> pieces)
        {
            // Object, because on Paper's API a Player already is an Audience, and Java 17 refuses
            // a pattern that cannot fail.
            final Object target = player;
            if (!(target instanceof Audience audience))
            {
                return false;
            }
            final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacySection();
            Component line = Component.empty();
            for (final Part piece : pieces)
            {
                Component bit = legacy.deserialize(piece.label());
                if (piece.command() != null)
                {
                    bit = bit.clickEvent(piece.suggest() ? ClickEvent.suggestCommand(piece.command())
                        : ClickEvent.runCommand(piece.command()))
                        .hoverEvent(HoverEvent.showText(legacy.deserialize(piece.hover())));
                }
                line = line.append(bit);
            }
            audience.sendMessage(line);
            return true;
        }
    }

    /**
     * The only code naming Spigot's chat API, so a server without it fails to link this class,
     * inside {@link #sendClickable}'s try.
     */
    private static final class SpigotChat
    {
        /** Static use only. */
        private SpigotChat()
        {
        }

        /**
         * @param player
         *            who to tell
         * @param pieces
         *            the line
         */
        static void send(final Player player, final List<Part> pieces)
        {
            final TextComponent line = new TextComponent();
            for (final Part piece : pieces)
            {
                final TextComponent bit = new TextComponent(piece.label());
                if (piece.command() != null)
                {
                    // Named in full: the simple names are Adventure's, imported for PaperChat.
                    bit.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(piece.suggest()
                        ? net.md_5.bungee.api.chat.ClickEvent.Action.SUGGEST_COMMAND
                        : net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, piece.command()));
                    bit.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                        net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, new Text(piece.hover())));
                }
                line.addExtra(bit);
            }
            player.spigot().sendMessage(line);
        }
    }
}
