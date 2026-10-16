package com.wormhole_xtreme.wormhole.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Every line the ring and beam commands send is built by {@link ChatText} (#438).
 *
 * <p>Before the move, a third of these lines had no header at all and the rest pasted one on by
 * hand, so a ring refusal and a gate refusal looked like two different plugins. This reads the
 * source of the files that were moved and refuses a line that starts as anything else: a bare
 * string, a name or count concatenated straight in, or a helper's text sent without a header.
 *
 * <p>A line passes if it opens with {@code ChatText.info}, {@code error} or {@code status}, is a
 * whole {@code MessageStrings} message, comes from one of the few helpers here that return a
 * finished line, or is a single variable, which the reader of that file can see was built from
 * ChatText. A {@code ::sendMessage} method reference passes only for a collection named in
 * {@link #SENT_COLLECTIONS}. {@link GateCommandChatGuardTest} covers the rest of the commands;
 * this covers only these six.
 */
class RingBeamChatGuardTest
{
    private static final Path ROOT = Paths.get("src", "main", "java", "com", "wormhole_xtreme", "wormhole");

    static final List<String> FILES = List.of(
        "command/handlers/RingCommand.java",
        "command/handlers/RingConsoleCommands.java",
        "model/ring/RingMessages.java",
        "command/handlers/BeamCommand.java",
        "model/beam/BeamTravel.java",
        "model/beam/BeamAnimation.java");

    /** Where a line to send begins: the argument to sendMessage, and to the wrappers that send one. */
    private static final Pattern SENDS = Pattern.compile(
        "(?:\\.sendMessage\\(|\\bsaved\\(\\w+, \\w+, |\\bstatus\\(\\w+, |\\bstatusAll\\(\\w+, )\\s*");

    /**
     * What that argument may start with: a line-opening ChatText call, a whole MessageStrings
     * message other than a bare header, a helper that returns a finished line, or a lone variable.
     * A header constant or a fragment ({@code ChatText.name(x) + "..."}) does not open a line.
     */
    private static final Pattern BUILT = Pattern.compile(
        "ChatText\\.(?:info|error|status)\\("
            + "|ConfigManager\\.MessageStrings\\.(?!ERROR_HEADER|NORMAL_HEADER)\\w+\\.toString\\(\\)\\s*\\)"
            + "|(?:helpLine|neverSeen|noPublic|noWorld)\\("
            + "|\\w+\\s*\\)");

    /**
     * The collections a method reference may send, by file: both hold what
     * {@code RingCommand.whyNotAPair} returns, which builds each line with ChatText.
     */
    private static final List<String> SENT_COLLECTIONS = List.of(
        "command/handlers/RingCommand.java refused",
        "command/handlers/RingConsoleCommands.java notAPair");

    @Test
    void everyRingAndBeamLineIsBuiltByChatText() throws IOException
    {
        final List<String> offences = new ArrayList<>();
        int read = 0;
        int sends = 0;
        for (final String name : FILES)
        {
            final String source = Files.readString(ROOT.resolve(name), StandardCharsets.UTF_8);
            read++;
            sends += ChatSendScan.checkArguments(name, source, SENDS, BUILT, offences)
                + ChatSendScan.checkReferences(name, source, SENT_COLLECTIONS, offences);
        }
        assertEquals(FILES.size(), read, "every file should be read, from " + ROOT.toAbsolutePath());
        assertTrue(sends > 100, "these files send well over a hundred lines between them; found only " + sends
            + ", so the pattern no longer matches how they send");
        assertTrue(offences.isEmpty(), "a line sent without ChatText -- build it with ChatText.info or "
            + "ChatText.error: " + offences);
    }
}
