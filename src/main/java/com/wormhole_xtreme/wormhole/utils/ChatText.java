package com.wormhole_xtreme.wormhole.utils;

import java.util.regex.Pattern;

import org.bukkit.ChatColor;

/**
 * The colours that pick out the parts of a chat line, each returning to the body grey.
 *
 * <p>One palette for every feature (#325): a name in aqua, something to type or a value in
 * white, a block in yellow, done in green, wrong in red.
 */
public final class ChatText
{
    /** The body colour every fragment returns to. */
    public static final String BODY_COLOUR = "§7";

    /** The {@code ::} a line opens with, and a heading over a group of them. */
    public static final String HEADING_COLOUR = "§3";

    /** Something to type. The same white serves a value read beside its label. */
    public static final String COMMAND_COLOUR = "§f";

    /** A name: a gate, network, mirror, shape, group or player. */
    public static final String NAME_COLOUR = "§b";

    /** A block or an item. */
    public static final String MATERIAL_COLOUR = "§e";

    /** Something done or allowed. */
    public static final String GOOD_COLOUR = "§a";

    /** Something wrong or refused. */
    public static final String BAD_COLOUR = "§c";

    /** What a line opens with: {@code :: } and then the body grey. */
    public static final String HEADER = HEADING_COLOUR + ":: " + BODY_COLOUR;

    /** What a refusal opens with: {@code :: error :: } and then the body grey. */
    public static final String ERROR_HEADER = HEADING_COLOUR + ":: " + ChatColor.DARK_PURPLE + "error "
        + HEADING_COLOUR + ":: " + BODY_COLOUR;

    private static final Pattern CODE = Pattern.compile("§.");

    /** Static helpers only. */
    private ChatText()
    {
    }

    /** @return a line opening with {@link #HEADER}, for a body that starts in the body grey */
    public static String info(final String body)
    {
        return HEADER + body;
    }

    /** @return a line opening with {@link #ERROR_HEADER} */
    public static String error(final String body)
    {
        return ERROR_HEADER + body;
    }

    /** @return a line opening with {@code :: } whose body sets its own colour from the first word */
    public static String status(final String body)
    {
        return HEADING_COLOUR + ":: " + body;
    }

    /** @return something to type: a command or an option */
    public static String command(final String text)
    {
        return COMMAND_COLOUR + text + BODY_COLOUR;
    }

    /** @return a value: a count, coordinates, a distance */
    public static String value(final String text)
    {
        return COMMAND_COLOUR + text + BODY_COLOUR;
    }

    /** @return a name: a gate, network, mirror, shape, group or player */
    public static String name(final String text)
    {
        return NAME_COLOUR + text + BODY_COLOUR;
    }

    /** @return a block or an item, with "or" between alternatives kept in the body colour */
    public static String material(final String text)
    {
        return MATERIAL_COLOUR + text.replace(" or ", BODY_COLOUR + " or " + MATERIAL_COLOUR) + BODY_COLOUR;
    }

    /** @return something done or allowed */
    public static String good(final String text)
    {
        return GOOD_COLOUR + text + BODY_COLOUR;
    }

    /** @return something wrong or refused */
    public static String bad(final String text)
    {
        return BAD_COLOUR + text + BODY_COLOUR;
    }

    /** @return a heading over a group of lines */
    public static String heading(final String text)
    {
        return HEADING_COLOUR + text + BODY_COLOUR;
    }

    /**
     * A usage line (#325): the words to type in white, {@code <required>} in aqua, and anything
     * inside {@code [optional]} left in the body grey, brackets included, as are a bare {@code |}
     * or {@code or} between alternatives.
     *
     * @param line
     *            the usage, as {@code /wormhole gate edit <gate> <field> [value]}
     * @return {@code Usage: } and the line, coloured, ending in the body colour
     */
    public static String usage(final String line)
    {
        return "Usage: " + usageBody(line);
    }

    /**
     * The words of a usage line coloured as {@link #usage} colours them, without the
     * {@code Usage: } in front: for a line that lists a command beside what it does.
     *
     * @param line
     *            the usage, as {@code /wormhole ring remove [id]}
     * @return the line, coloured, ending in the body colour
     */
    public static String usageBody(final String line)
    {
        final StringBuilder out = new StringBuilder();
        int optional = 0;
        for (final String word : line.trim().split("\\s+"))
        {
            if (!out.isEmpty())
            {
                out.append(' ');
            }
            final int opened = optional;
            optional += count(word, '[') - count(word, ']');
            if ((opened > 0) || word.startsWith("[") || "|".equals(word) || "or".equals(word))
            {
                out.append(word);
            }
            else if (word.startsWith("<"))
            {
                out.append(name(word));
            }
            else
            {
                out.append(command(word));
            }
        }
        return out.toString();
    }

    private static int count(final String word, final char c)
    {
        return (int) word.chars().filter(ch -> ch == c).count();
    }

    /** @return the line as a player reads it, without colour codes */
    public static String plain(final String text)
    {
        return (text == null) ? "" : CODE.matcher(text).replaceAll("");
    }
}
