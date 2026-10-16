package com.wormhole_xtreme.wormhole.utils;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.config.ConfigManager;

/**
 * Every line the gate commands send is built by {@link ChatText} (#438), as the ring and beam
 * commands' are ({@link RingBeamChatGuardTest}).
 *
 * <p>Before the move, {@code gate edit group}, {@code gate shapes} and {@code config} answered
 * with no header at all, and the rest pasted one on by hand, so the same refusal read differently
 * by which command gave it. This reads every file under {@code command/}, other than the ring and
 * beam ones the other guard reads, and the DHD's {@code GateInteractionHandler}.
 *
 * <p>A line passes if it opens with {@code ChatText.info}, {@code error} or {@code status}, is a
 * whole {@code MessageStrings} message, is one of the few finished lines named in {@link #BUILT},
 * or is a single variable or constant, which the reader of that file can see was built from
 * ChatText. A {@code ChatLine} starts as a line does. No collection here is sent by method
 * reference, so any {@code ::sendMessage} fails.
 */
class GateCommandChatGuardTest
{
    private static final Path ROOT = Paths.get("src", "main", "java", "com", "wormhole_xtreme", "wormhole");

    /** Read besides everything under command/: the DHD's prompts are the gate commands' too. */
    private static final String INTERACTION = "GateInteractionHandler.java";

    /** Where a line to send begins: the argument to sendMessage, a ChatLine, or Build's action list. */
    private static final Pattern SENDS = Pattern.compile(
        "(?:\\.sendMessage\\(|\\bChatLine\\.of\\(|\\bactionList\\((?!final ))\\s*");

    /**
     * What that argument may start with: a line-opening ChatText call, a whole MessageStrings
     * message other than a bare header, one of four finished lines, or a lone name. A header
     * constant or a fragment ({@code ChatText.name(x) + "..."}) does not open a line.
     */
    private static final Pattern BUILT = Pattern.compile(
        "ChatText\\.(?:info|error|status)\\("
            + "|ConfigManager\\.MessageStrings\\.(?!ERROR_HEADER|NORMAL_HEADER)\\w+\\.toString\\(\\)\\s*\\)"
            + "|CommandUtilities\\.(?:noGateCalled\\(|INTERNAL_ERROR\\s*\\))"
            + "|RegionFlags\\.BUILD_REFUSED\\b"
            + "|completeHint\\("
            + "|\\w+\\s*\\)");

    @Test
    void everyGateCommandLineIsBuiltByChatText() throws IOException
    {
        final List<String> offences = new ArrayList<>();
        final List<String> names = filesToRead();
        int sends = 0;
        for (final String name : names)
        {
            final String source = Files.readString(ROOT.resolve(name), StandardCharsets.UTF_8);
            sends += ChatSendScan.checkArguments(name, source, SENDS, BUILT, offences)
                + ChatSendScan.checkReferences(name, source, List.of(), offences);
        }
        assertTrue(names.size() > 35, "every command file should be read, from " + ROOT.toAbsolutePath()
            + "; read only " + names);
        assertTrue(sends > 250, "these files send well over two hundred lines between them; found only " + sends
            + ", so the pattern no longer matches how they send");
        assertTrue(offences.isEmpty(), "a line sent without ChatText -- build it with ChatText.info or "
            + "ChatText.error: " + offences);
    }

    /**
     * A MessageStrings constant is sent as the whole line, so each one has its header already;
     * only the two headers themselves are bare.
     */
    @Test
    void everyMessageStringOpensWithAHeader()
    {
        for (final ConfigManager.MessageStrings message : ConfigManager.MessageStrings.values())
        {
            if ((message == ConfigManager.MessageStrings.NORMAL_HEADER)
                || (message == ConfigManager.MessageStrings.ERROR_HEADER))
            {
                continue;
            }
            final String line = message.toString();
            assertTrue(line.startsWith(ChatText.ERROR_HEADER) || line.startsWith(ChatText.HEADER),
                message.name() + " is sent as a whole line, so it opens with a header: " + line);
            assertTrue(!ChatMatchers.body(line).isBlank(), message.name() + " says something after its header: " + line);
        }
    }

    /** Every file under command/ but the ring and beam ones, and the interaction handler, relative to ROOT. */
    private static List<String> filesToRead() throws IOException
    {
        final List<String> names = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(ROOT.resolve("command")))
        {
            for (final Path file : walk.filter(p -> p.toString().endsWith(".java")).toList())
            {
                final String name = ROOT.relativize(file).toString().replace('\\', '/');
                if (!RingBeamChatGuardTest.FILES.contains(name))
                {
                    names.add(name);
                }
            }
        }
        names.add(INTERACTION);
        return names;
    }
}
