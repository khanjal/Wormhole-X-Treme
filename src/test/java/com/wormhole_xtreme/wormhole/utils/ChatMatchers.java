package com.wormhole_xtreme.wormhole.utils;

import org.mockito.ArgumentMatchers;

/**
 * Mockito matchers for chat lines read the way a player reads them, colour codes stripped (#438).
 *
 * <p>A message that names a gate in aqua or a count in white has codes in the middle of its
 * words' neighbourhood, so a plain {@code contains("put 4 slabs back")} stops matching the moment a
 * value is coloured. Matching on {@link ChatText#plain} lets a colour change leave the wording
 * tests alone; the few tests that pin colours compare the raw line instead.
 */
public final class ChatMatchers
{
    private ChatMatchers()
    {
    }

    /** @return a matcher for a line whose plain text contains {@code text} */
    public static String plainContains(final String text)
    {
        return ArgumentMatchers.argThat((String line) -> (line != null) && ChatText.plain(line).contains(text));
    }

    /**
     * @return a matcher for a line that opens with the {@code :: } or error header and whose plain
     *         text after it is {@code text}; a line with no header does not match
     */
    public static String plainBody(final String text)
    {
        return ArgumentMatchers.argThat((String line) -> (line != null) && text.equals(body(line)));
    }

    /** @return {@link #plainBody}, for a line under the {@code :: } header only, not the error one */
    public static String infoBody(final String text)
    {
        return ArgumentMatchers.argThat((String line) -> (line != null) && line.startsWith(ChatText.HEADER)
            && text.equals(body(line)));
    }

    /** @return {@link #plainBody}, for a line under the {@code :: error ::} header only */
    public static String errorBody(final String text)
    {
        return ArgumentMatchers.argThat((String line) -> (line != null) && line.startsWith(ChatText.ERROR_HEADER)
            && text.equals(body(line)));
    }

    /** @return the plain text of a line after its header, or null if it has none */
    public static String body(final String line)
    {
        final String plain = ChatText.plain(line);
        final String error = ChatText.plain(ChatText.ERROR_HEADER);
        if (plain.startsWith(error))
        {
            return plain.substring(error.length());
        }
        final String header = ChatText.plain(ChatText.HEADER);
        return plain.startsWith(header) ? plain.substring(header.length()) : null;
    }
}
