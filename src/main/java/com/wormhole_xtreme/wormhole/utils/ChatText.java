package com.wormhole_xtreme.wormhole.utils;

import java.util.regex.Pattern;

/**
 * The colours that pick out the parts of a chat line, each returning to the body grey.
 *
 * <p>One palette for every feature (#325): a name in aqua, something to type or a value in
 * white, a block in yellow, done in green, wrong in red.
 */
public final class ChatText
{
    /** The body colour every fragment returns to. */
    public static final String BODY = "§7";

    private static final String WHITE = "§f";
    private static final String NAME = "§b";
    private static final String MATERIAL = "§e";
    private static final String GOOD = "§a";
    private static final String BAD = "§c";

    private static final Pattern CODE = Pattern.compile("§.");

    /** Static helpers only. */
    private ChatText()
    {
    }

    /** @return something to type: a command or an option */
    public static String command(final String text)
    {
        return WHITE + text + BODY;
    }

    /** @return a value: a count, coordinates, a distance */
    public static String value(final String text)
    {
        return WHITE + text + BODY;
    }

    /** @return a name: a gate, network, mirror, shape, group or player */
    public static String name(final String text)
    {
        return NAME + text + BODY;
    }

    /** @return a block or an item, with "or" between alternatives kept in the body colour */
    public static String material(final String text)
    {
        return MATERIAL + text.replace(" or ", BODY + " or " + MATERIAL) + BODY;
    }

    /** @return something done or allowed */
    public static String good(final String text)
    {
        return GOOD + text + BODY;
    }

    /** @return something wrong or refused */
    public static String bad(final String text)
    {
        return BAD + text + BODY;
    }

    /** @return the line as a player reads it, without colour codes */
    public static String plain(final String text)
    {
        return (text == null) ? "" : CODE.matcher(text).replaceAll("");
    }
}
