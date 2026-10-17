package com.wormhole_xtreme.wormhole.utils;

/**
 * The sixteen chat colours, each with its section-sign code (#476).
 *
 * <p>Stands in for Bukkit's {@code ChatColor}, which Paper deprecates. Only colours are here,
 * in Minecraft's own order, so a format name such as {@code BOLD} or {@code MAGIC} is not a
 * constant at all.
 */
public enum ChatColour
{
    /** {@code §0}. */
    BLACK('0'),
    /** {@code §1}. */
    DARK_BLUE('1'),
    /** {@code §2}. */
    DARK_GREEN('2'),
    /** {@code §3}. */
    DARK_AQUA('3'),
    /** {@code §4}. */
    DARK_RED('4'),
    /** {@code §5}. */
    DARK_PURPLE('5'),
    /** {@code §6}. */
    GOLD('6'),
    /** {@code §7}. */
    GRAY('7'),
    /** {@code §8}. */
    DARK_GRAY('8'),
    /** {@code §9}. */
    BLUE('9'),
    /** {@code §a}. */
    GREEN('a'),
    /** {@code §b}. */
    AQUA('b'),
    /** {@code §c}. */
    RED('c'),
    /** {@code §d}. */
    LIGHT_PURPLE('d'),
    /** {@code §e}. */
    YELLOW('e'),
    /** {@code §f}. */
    WHITE('f');

    /** The section sign every code opens with. */
    public static final char COLOR_CHAR = '§';

    /** The italic format code, which is not a colour and so not an enum constant. */
    public static final String ITALIC = COLOR_CHAR + "o";

    private final String code;

    ChatColour(final char code)
    {
        this.code = COLOR_CHAR + String.valueOf(code);
    }

    /** @return the section-sign code, as {@code §b} */
    @Override
    public String toString()
    {
        return code;
    }

    /**
     * @param text
     *            legacy text, may be null
     * @return the text without colour or format codes, or null when it was null
     */
    public static String stripColor(final String text)
    {
        return (text == null) ? null : ChatText.plain(text);
    }
}
