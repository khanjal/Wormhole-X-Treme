package com.wormhole_xtreme.wormhole.model.preview;

import java.util.List;
import java.util.stream.Collectors;

import com.wormhole_xtreme.wormhole.utils.ChatText;

/**
 * Colours for what {@code gate build} tells a player, in the palette mirror messages use. Each
 * fragment returns to the body grey, so the words after it are not coloured too.
 */
public final class PreviewText
{
    /** The grey both message headers leave behind. */
    static final String BODY = ChatText.BODY;


    private PreviewText() {}

    /** @return something to type, in white */
    public static String command(final String text)
    {
        return ChatText.command(text);
    }

    /** @return several things to type, each in white */
    public static String commands(final List<String> texts)
    {
        return texts.stream().map(PreviewText::command).collect(Collectors.joining(" "));
    }

    /** @return a shape or group name, in aqua */
    public static String name(final String text)
    {
        return ChatText.name(text);
    }

    /** @return a block to gather, in yellow; "a or b" colours each block */
    public static String material(final String text)
    {
        return ChatText.material(text);
    }

    /** @return something done, in green */
    public static String good(final String text)
    {
        return ChatText.good(text);
    }

    /** @return something wrong, in red */
    public static String bad(final String text)
    {
        return ChatText.bad(text);
    }
}
