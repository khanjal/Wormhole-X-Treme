package com.wormhole_xtreme.wormhole.model.mirror;

import java.util.Locale;

import org.bukkit.DyeColor;

/**
 * Colouring for the things a mirror says.
 *
 * <p>Every line a mirror sends used to arrive in one colour: the grey that
 * {@code NORMAL_HEADER} leaves behind. A paragraph of grey containing a command to type, the
 * name of a mirror and the name of a world gives a reader no way to tell which of those three
 * is which without reading the sentence around them, which is the job colour is for.
 *
 * <p>The three colours are not new. They are what this plugin already uses when it tells
 * somebody how to finish a stargate -- {@code Type '&fF/wormhole complete &fB<name>'} -- and
 * that is the whole reason for reusing them rather than picking nicer ones: a player who has
 * learned that white means "type this" at a gate should not have to learn it again at a
 * banner.
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
    public static final String BODY_COLOUR = "§7";

    /** A name somebody typed or is about to: a mirror, a world, a look. */
    public static final String NAME_COLOUR = "§b";

    /** A line meant to be typed as it stands. */
    public static final String COMMAND_COLOUR = "§f";

    /** Static text only. */
    private MirrorText()
    {
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
        return NAME_COLOUR + name + BODY_COLOUR;
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
     * @param name
     *            the mirror's name
     * @param worldName
     *            the world its far side is in
     * @return the whole line, header and all
     */
    public static String approach(final String name, final String worldName)
    {
        return "§3:: " + NAME_COLOUR + name + BODY_COLOUR + " -- click to travel to "
            + NAME_COLOUR + worldName + BODY_COLOUR + ".";
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
        return NAME_COLOUR + String.join(BODY_COLOUR + ", " + NAME_COLOUR, names) + BODY_COLOUR;
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
        return COMMAND_COLOUR + line + BODY_COLOUR;
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
     * @param colour
     *            the dye
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
     * @param colour
     *            the dye
     * @return its colour code
     */
    private static String code(final DyeColor colour)
    {
        return switch (colour)
        {
            case WHITE -> "§f";
            case ORANGE, BROWN -> "§6";
            case MAGENTA, PINK -> "§d";
            case LIGHT_BLUE -> "§b";
            case YELLOW -> "§e";
            case LIME -> "§a";
            case GRAY, BLACK -> "§8";
            case CYAN -> "§3";
            case PURPLE -> "§5";
            case BLUE -> "§9";
            case GREEN -> "§2";
            case RED -> "§c";
            // LIGHT_GRAY lands here too, and is right to: it is the body colour already.
            default -> BODY_COLOUR;
        };
    }
}
