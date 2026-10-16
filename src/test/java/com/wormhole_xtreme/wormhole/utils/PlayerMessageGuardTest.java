package com.wormhole_xtreme.wormhole.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.config.ConfigManager;

/**
 * Every line the plugin sends a player is built by {@link ChatText} (#438): the issue's own
 * "done" check, over all of {@code src/main/java}.
 *
 * <p>Before the move, some lines had no header at all ({@code Gate: Abydos timed out}, the
 * dial sign's {@code Dialer set to:}), some pasted one on by hand, and three
 * {@code MessageStrings} were half a line with a name or number glued to the end, so the same
 * kind of message read differently by which part of the plugin said it. This reads every main
 * source file and refuses a line that starts as anything else.
 *
 * <p>A line passes if it opens with {@code ChatText.info}, {@code error} or {@code status}; is a
 * whole {@code MessageStrings} message; is a call to a <em>line helper</em> or a <em>line
 * constant</em>; is a single lower-case variable, which the reader of that file can see was built
 * from ChatText; or is one of the transport sends in {@link #TRANSPORT}. A line helper is any
 * {@code String} method whose body opens {@code return ChatText.info(} (or {@code error},
 * {@code status}), and a line constant any {@code static final String} initialised the same way:
 * both are found by reading their own definitions, so there is no list of them to keep. Each
 * passes bare only in its own file, and as {@code Class.name} elsewhere: a {@code USAGE} in one
 * command is not vouched for by another command's {@code USAGE}.
 *
 * <p>This replaces the per-area guards the earlier steps of #438 added (ring and beam, then the
 * gate commands); their wrappers and method-reference allowances are kept here.
 */
class PlayerMessageGuardTest
{
    private static final Path ROOT = Paths.get("src", "main", "java", "com", "wormhole_xtreme", "wormhole");

    /**
     * Where a line to send begins: a sendMessage, a ChatLine, the action bar, a title or a
     * broadcast; and the wrappers that send their argument as it stands -- the ring and beam
     * replies ({@code saved}, {@code status}, {@code statusAll}), a gate refusal said once
     * ({@code refuseWithReminder}) and Build's action list.
     */
    private static final Pattern SENDS = Pattern.compile(
        "(?:\\.sendMessage\\(|\\bChatLine\\.of\\(|\\bActionBar\\.send\\(\\w++,\\s*+|\\.sendTitle\\(\\s*+"
            + "|\\.sendActionBar\\(|\\.broadcastMessage\\("
            + "|(?<![.\\w])(?:saved\\(\\w++, \\w++, |status\\(\\w++, |statusAll\\(\\w++, "
            + "|refuseWithReminder\\(\\w++,\\s*+\\w++,\\s*+|actionList\\((?!final )))\\s*+");

    /** A String method whose body opens by returning a ChatText line, with its name. */
    private static final Pattern LINE_HELPER = Pattern.compile(
        "\\bString\\s++(\\w++)\\s*+\\([^)]*+\\)\\s*+\\{\\s*+return\\s++ChatText\\.(?:info|error|status)\\(");

    /** A String constant initialised to a ChatText line, with its name. */
    private static final Pattern LINE_CONSTANT = Pattern.compile(
        "\\bstatic\\s++final\\s++String\\s++([A-Z][A-Z0-9_]*+)\\s*+=\\s*+ChatText\\.(?:info|error|status)\\(");

    /**
     * The sends inside the two classes that carry a finished line to the client, by file, each
     * with what it may start with. They are the transport, not the wording: each sends the line
     * its caller built, which this test has already read at the caller.
     */
    private static final Map<String, String> TRANSPORT = Map.of(
        // ChatLine's fallback for a player with no clickable chat: its own text, without the links.
        "utils/ChatLine.java", "plain\\(\\)\\s*+\\)",
        // The Spigot action bar: the line ActionBar.send was handed, wrapped as a component.
        "utils/ActionBar.java", "ChatMessageType\\.ACTION_BAR,");

    /**
     * The collections a method reference may send, by file: both hold what
     * {@code RingCommand.whyNotAPair} returns, which builds each line with ChatText.
     */
    private static final List<String> SENT_COLLECTIONS = List.of(
        "command/handlers/RingCommand.java refused",
        "command/handlers/RingConsoleCommands.java notAPair");

    @Test
    void everyPlayerLineIsBuiltByChatText() throws IOException
    {
        final Map<String, String> sources = readAll();
        // Each as Class.name, the class being the file's own: a name means one thing in its own
        // file and only Class.name anywhere else, so a USAGE here is not vouched for by another
        // file's USAGE.
        final TreeSet<String> helpers = new TreeSet<>();
        final TreeSet<String> constants = new TreeSet<>();
        for (final Map.Entry<String, String> file : sources.entrySet())
        {
            collect(LINE_HELPER, file, helpers);
            collect(LINE_CONSTANT, file, constants);
        }
        final String common = "ChatText\\.(?:info|error|status)\\("
            + "|(?:ConfigManager\\.)?MessageStrings\\.\\w++\\.toString\\(\\)\\s*+\\)"
            + alternatives(helpers, "\\(") + alternatives(constants, "\\s*+\\)")
            + "|[a-z]\\w*+\\s*+\\)";
        final List<String> offences = new ArrayList<>();
        int sends = 0;
        for (final Map.Entry<String, String> file : sources.entrySet())
        {
            final String name = file.getKey();
            final String owner = owner(name) + ".";
            final String transport = TRANSPORT.get(name);
            final String own = alternatives(unqualified(helpers, owner), "\\(")
                + alternatives(unqualified(constants, owner), "\\s*+\\)");
            final Pattern built = Pattern.compile(common + own + ((transport == null) ? "" : ("|" + transport)));
            sends += ChatSendScan.checkArguments(name, file.getValue(), SENDS, built, offences)
                + ChatSendScan.checkReferences(name, file.getValue(), SENT_COLLECTIONS, offences);
        }
        assertTrue(sources.size() > 250, "every main source file should be read, from " + ROOT.toAbsolutePath()
            + "; read only " + sources.size());
        assertTrue(helpers.containsAll(List.of("CommandUtilities.noGateCalled", "RingCommand.helpLine",
            "MirrorText.approach", "StargateRestrictions.cooldownRefusal")),
            "the line helpers should be found by their own definitions; found only " + helpers);
        assertTrue(constants.containsAll(List.of("CommandUtilities.INTERNAL_ERROR", "RegionFlags.BUILD_REFUSED",
            "RegionFlags.USE_REFUSED")), "the line constants should be found by their own definitions; found only "
            + constants);
        assertTrue(sends > 450, "the plugin sends well over four hundred lines; found only " + sends
            + ", so the pattern no longer matches how it sends");
        assertTrue(offences.isEmpty(), "a line sent without ChatText -- build it with ChatText.info or "
            + "ChatText.error: " + offences);
    }

    /**
     * A MessageStrings constant is sent as the whole line, so each one opens with a header and
     * says something after it: none is a header alone, or half a line for a caller to finish.
     */
    @Test
    void everyMessageStringIsAWholeLine()
    {
        for (final ConfigManager.MessageStrings message : ConfigManager.MessageStrings.values())
        {
            final String line = message.toString();
            assertTrue(line.startsWith(ChatText.ERROR_HEADER) || line.startsWith(ChatText.HEADER),
                message.name() + " is sent as a whole line, so it opens with a header: " + line);
            assertFalse(ChatMatchers.body(line).isBlank(), message.name() + " says something after its header: " + line);
            assertTrue(line.endsWith(".") || line.endsWith("!"),
                message.name() + " is a finished sentence, not the start of one a caller appends to: " + line);
        }
    }

    /** Every main source file, by its path under {@link #ROOT}. */
    private static Map<String, String> readAll() throws IOException
    {
        final Map<String, String> sources = new LinkedHashMap<>();
        try (Stream<Path> walk = Files.walk(ROOT))
        {
            for (final Path file : walk.filter(p -> p.toString().endsWith(".java")).toList())
            {
                sources.put(ROOT.relativize(file).toString().replace('\\', '/'),
                    Files.readString(file, StandardCharsets.UTF_8));
            }
        }
        return sources;
    }

    /** Adds each definition in a file as {@code Class.name}, the class named by the file. */
    private static void collect(final Pattern definition, final Map.Entry<String, String> file,
        final TreeSet<String> into)
    {
        final Matcher found = definition.matcher(file.getValue());
        while (found.find())
        {
            into.add(owner(file.getKey()) + "." + found.group(1));
        }
    }

    /** @return the class a file under {@link #ROOT} declares: its name without the folders or {@code .java} */
    private static String owner(final String file)
    {
        return file.substring(file.lastIndexOf('/') + 1, file.length() - ".java".length());
    }

    /** @return the names among {@code qualified} that {@code owner} (as {@code Class.}) declares, without it */
    private static List<String> unqualified(final TreeSet<String> qualified, final String owner)
    {
        return qualified.stream().filter(q -> q.startsWith(owner)).map(q -> q.substring(owner.length())).toList();
    }

    /** @return {@code |(?:a|b)} and then {@code after}, each name's dot escaped; nothing for no names */
    private static String alternatives(final Collection<String> names, final String after)
    {
        return names.isEmpty() ? ""
            : ("|(?:" + String.join("|", names.stream().map(n -> n.replace(".", "\\.")).toList()) + ")" + after);
    }
}
