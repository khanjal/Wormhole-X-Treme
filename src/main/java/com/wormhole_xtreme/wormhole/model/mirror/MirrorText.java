package com.wormhole_xtreme.wormhole.model.mirror;

import java.util.Locale;

import org.bukkit.DyeColor;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.utils.ChatColour;
import com.wormhole_xtreme.wormhole.utils.ChatText;
import com.wormhole_xtreme.wormhole.utils.Messages;

/**
 * Colouring for the things a mirror says.
 *
 * <p>Every line a mirror sends used to arrive in one colour: the grey that
 * {@link ChatText#HEADER} leaves behind. A paragraph of grey containing a command to type, the
 * name of a mirror and the name of a world gives a reader no way to tell which of those three
 * is which without reading the sentence around them, which is the job colour is for.
 *
 * <p>It builds on {@link ChatText}, the one palette for every feature: white for the
 * command, aqua for the name. A player who has learned that white means "type this" at a gate
 * should not have to learn it again at a banner.
 *
 * <ul>
 * <li>White -- a line meant to be typed as it stands.</li>
 * <li>Aqua -- a name: a mirror, a world, a look.</li>
 * <li>Grey -- the prose around them, which is the header's own colour.</li>
 * </ul>
 *
 * <p>Every method here ends by returning to {@link #BODY_COLOUR}, so a fragment can be dropped into
 * the middle of a sentence without the rest of that sentence inheriting its colour. Getting
 * that wrong is not a compile error and not a test failure; it is a line that looks fine until
 * somebody puts a word after it.
 */
public final class MirrorText
{
    /** The colour the header leaves behind, and the one every fragment returns to. */
    public static final String BODY_COLOUR = ChatText.BODY_COLOUR;

    /** A name somebody typed or is about to: a mirror, a world, a look. */
    public static final String NAME_COLOUR = ChatText.NAME_COLOUR;

    /** A line meant to be typed as it stands. */
    public static final String COMMAND_COLOUR = ChatText.COMMAND_COLOUR;

    /**
     * A value in {@code mirror debug}, beside its grey label.
     *
     * <p>The command's white: debug lines hold values to read rather than commands to type, and a
     * label and its value in the same grey was the paragraph this class exists to break up.
     */
    public static final String VALUE_COLOUR = ChatText.COMMAND_COLOUR;

    /** Something in {@code mirror debug} that stops a view working: a missing file, a gap, a spent budget. */
    public static final String BAD_COLOUR = ChatText.BAD_COLOUR;

    /** Something in {@code mirror debug} working as it should. */
    public static final String GOOD_COLOUR = ChatText.GOOD_COLOUR;

    /** A group of {@code mirror debug} lines. */
    public static final String HEADING_COLOUR = ChatText.HEADING_COLOUR;

    /** Static text only. */
    private MirrorText()
    {
    }

    /**
     * One {@code mirror debug} line: a label, and its value.
     *
     * @param label
     *            what the value is
     * @param value
     *            the value, which may carry {@link #bad} or {@link #good} fragments
     * @return {@code label: value}, ending back in the body colour
     */
    public static String field(final String label, final String value)
    {
        return "  " + BODY_COLOUR + label + ": " + VALUE_COLOUR + value + BODY_COLOUR;
    }

    /**
     * A heading over a group of {@code mirror debug} lines.
     *
     * @param heading
     *            what the group is about
     * @return the heading, ending back in the body colour
     */
    public static String heading(final String heading)
    {
        return ChatText.heading(heading);
    }

    /**
     * A fragment of a debug value that stops a view working.
     *
     * @param text
     *            the fragment
     * @return it in red, ending back in the value colour
     */
    public static String bad(final String text)
    {
        return BAD_COLOUR + text + VALUE_COLOUR;
    }

    /**
     * A fragment of a debug value working as it should.
     *
     * @param text
     *            the fragment
     * @return it in green, ending back in the value colour
     */
    public static String good(final String text)
    {
        return GOOD_COLOUR + text + VALUE_COLOUR;
    }

    /**
     * A name, coloured.
     *
     * @param name
     *            what it is called
     * @return the name in the name colour, ending back in the body colour
     */
    public static String name(final String name)
    {
        return ChatText.name(name);
    }

    /**
     * A name in the single quotes this plugin's mirror messages have always put round one.
     *
     * <p>The quotes stay even though the colour would now carry the emphasis on its own. They
     * are what somebody reading the server console sees, where there is no colour at all --
     * and the console is where an operator looks when a player reports that something did not
     * work.
     *
     * @param name
     *            what it is called
     * @return the quoted name, ending back in the body colour
     */
    public static String quoted(final String name)
    {
        return "'" + NAME_COLOUR + name + BODY_COLOUR + "'";
    }

    /**
     * What a mirror says above the hotbar when somebody walks up to it.
     *
     * <p>Carries its own header, unlike everything else here. This one does not go through the
     * {@code sendMessage} path that prefixes the plugin's own {@code :: }, and without it the
     * line would arrive looking like something another plugin put there -- which on a server
     * running several is the difference between a mirror and a mystery.
     *
     * <p>Says where it goes, not what it looks like. The banner is already showing what it
     * looks like; the one thing a player standing in front of it cannot see is the far end.
     *
     * @param reader
     *            who will read it, in their own language
     * @param name
     *            the mirror's name
     * @param worldName
     *            the world its far side is in
     * @return the whole line, header and all
     */
    public static String approach(final Player reader, final String name, final String worldName)
    {
        return ChatText.status(Messages.get(reader, "mirror.signpost.approach-world", ChatText.name(name),
            ChatText.name(worldName)));
    }

    /**
     * What a mirror turned on to open onto another mirror says above the hotbar, header and all.
     *
     * @param reader
     *            who will read it, in their own language
     * @param name
     *            the mirror's name
     * @param chosen
     *            the mirror it opens onto
     * @return the whole line, header and all
     */
    public static String approachMirror(final Player reader, final String name, final String chosen)
    {
        return ChatText.status(Messages.get(reader, "mirror.signpost.approach-mirror", ChatText.name(name),
            ChatText.name(chosen)));
    }

    /**
     * {@link #approach(Player, String, String)} in the server's language.
     *
     * @param name
     *            the mirror's name
     * @param worldName
     *            the world its far side is in
     * @return the whole line, header and all
     */
    public static String approach(final String name, final String worldName)
    {
        return approach(null, name, worldName);
    }

    /**
     * The action bar line for a mirror showing its own room.
     *
     * @param reader
     *            who will read it, in their own language
     * @param name
     *            the mirror
     * @return the formatted line
     */
    public static String reflection(final Player reader, final String name)
    {
        return ChatText.status(Messages.get(reader, "mirror.signpost.reflection", ChatText.name(name)));
    }

    /**
     * A list of names, with the separators left in the body colour.
     *
     * <p>The commas belong to the sentence rather than to the names, and colouring them along
     * with the names turns a list of three things into one long aqua smear.
     *
     * @param names
     *            what they are called
     * @return the names in the name colour, comma-separated, ending back in the body colour
     */
    public static String names(final String[] names)
    {
        return names(names, ", ");
    }

    /**
     * {@link #names(String[])} with the separator the reader's language puts between names.
     *
     * @param names
     *            what they are called
     * @param separator
     *            what goes between two names, left in the body colour
     * @return the names in the name colour, ending back in the body colour
     */
    public static String names(final String[] names, final String separator)
    {
        return NAME_COLOUR + String.join(BODY_COLOUR + separator + NAME_COLOUR, names) + BODY_COLOUR;
    }

    /**
     * A command line, meant to be typed as it stands.
     *
     * @param line
     *            the whole command
     * @return the command in the command colour, ending back in the body colour
     */
    public static String command(final String line)
    {
        return ChatText.command(line);
    }

    /**
     * A command line ending in the name of the mirror it is about.
     *
     * <p>Two colours in one line on purpose. The verb is fixed and the name is the part that
     * changes, so colouring them the same would hide the one word a reader has to check
     * against their own mirror.
     *
     * @param verb
     *            the command up to but not including the name
     * @param name
     *            the mirror's name
     * @return the command, ending back in the body colour
     */
    public static String command(final String verb, final String name)
    {
        return COMMAND_COLOUR + verb + " " + NAME_COLOUR + name + BODY_COLOUR;
    }

    /**
     * The name of a dye, written in something close to that dye.
     *
     * <p>Only used where the sentence is about what a banner looks like, which is the one
     * place a colour word is the subject rather than decoration. "Mostly red" is easier to
     * believe when it is red.
     *
     * <p>The two palettes do not line up -- sixteen dyes against sixteen chat colours chosen
     * for text -- so several dyes share a code and the word itself stays the thing that
     * actually carries the meaning. Black is the one deliberate lie: it is written in dark
     * grey, because true black on a chat background is a word nobody can read.
     *
     * <p>A dye this does not know about is written in the body colour rather than refusing.
     * The switch below carries a default for that reason and not for exhaustiveness: an
     * exhaustive enum switch compiled against one Minecraft version throws on a later one that
     * added a constant, and a banner sentence is not worth an exception.
     *
     * @return the dye's name in its own colour, ending back in the body colour
     */
    public static String dye(final DyeColor colour)
    {
        final String word = colour.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return code(colour) + word + BODY_COLOUR;
    }

    /**
     * The chat colour nearest one dye.
     *
     * @return its colour code
     */
    private static String code(final DyeColor colour)
    {
        return switch (colour)
        {
            case WHITE -> ChatColour.WHITE.toString();
            case ORANGE, BROWN -> ChatColour.GOLD.toString();
            case MAGENTA, PINK -> ChatColour.LIGHT_PURPLE.toString();
            case LIGHT_BLUE -> ChatColour.AQUA.toString();
            case YELLOW -> ChatColour.YELLOW.toString();
            case LIME -> ChatColour.GREEN.toString();
            case GRAY, BLACK -> ChatColour.DARK_GRAY.toString();
            case CYAN -> ChatColour.DARK_AQUA.toString();
            case PURPLE -> ChatColour.DARK_PURPLE.toString();
            case BLUE -> ChatColour.BLUE.toString();
            case GREEN -> ChatColour.DARK_GREEN.toString();
            case RED -> ChatColour.RED.toString();
            // LIGHT_GRAY lands here too, and is right to: it is the body colour already.
            default -> BODY_COLOUR;
        };
    }
}
