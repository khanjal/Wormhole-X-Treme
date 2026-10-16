package com.wormhole_xtreme.wormhole.utils;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a source file for the lines it sends, for the guards that hold each feature's chat to
 * {@link ChatText} (#438).
 *
 * <p>Each guard says where a line to send begins in its files and what that line may start
 * with; this finds every such place and names the ones that start with anything else.
 */
final class ChatSendScan
{
    /** Any method reference to sendMessage, so one that is not a known forEach is still seen. */
    private static final Pattern SEND_REF = Pattern.compile("::sendMessage\\b");

    /** A method reference that sends whatever a collection holds, with the collection it reads. */
    private static final Pattern SENDS_EACH = Pattern.compile("(\\w+)\\.forEach\\(\\w+::sendMessage\\)");

    private ChatSendScan()
    {
    }

    /**
     * Checks each line passed to a send.
     *
     * @param name
     *            the file, for the offence
     * @param source
     *            its text
     * @param sends
     *            where a line to send begins: the match ends where the line does
     * @param built
     *            what that line may start with
     * @param offences
     *            where each offence is added, as {@code name:line}
     * @return how many sends there were
     */
    static int checkArguments(final String name, final String source, final Pattern sends, final Pattern built,
        final List<String> offences)
    {
        int count = 0;
        final Matcher send = sends.matcher(source);
        while (send.find())
        {
            count++;
            if (!built.matcher(source).region(send.end(), source.length()).lookingAt())
            {
                offences.add(name + ":" + lineOf(source, send.start()));
            }
        }
        return count;
    }

    /**
     * Checks each {@code ::sendMessage} against the collections allowed to send that way.
     *
     * @param name
     *            the file, for the offence and the allowlist
     * @param source
     *            its text
     * @param sentCollections
     *            the allowed ones, each as {@code file collection}
     * @param offences
     *            where each offence is added
     * @return how many method references there were
     */
    static int checkReferences(final String name, final String source, final List<String> sentCollections,
        final List<String> offences)
    {
        int count = 0;
        final Matcher ref = SEND_REF.matcher(source);
        while (ref.find())
        {
            count++;
            final int from = source.lastIndexOf('\n', ref.start()) + 1;
            final int to = source.indexOf('\n', ref.end());
            final Matcher each = SENDS_EACH.matcher(source.substring(from, (to < 0) ? source.length() : to));
            if (!(each.find() && sentCollections.contains(name + " " + each.group(1))))
            {
                offences.add(name + ":" + lineOf(source, ref.start()) + " (method reference)");
            }
        }
        return count;
    }

    private static int lineOf(final String source, final int at)
    {
        return (int) source.substring(0, at).chars().filter(c -> c == '\n').count() + 1;
    }
}
