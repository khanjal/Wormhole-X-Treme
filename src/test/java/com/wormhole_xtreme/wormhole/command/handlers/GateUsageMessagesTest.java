package com.wormhole_xtreme.wormhole.command.handlers;

import static com.wormhole_xtreme.wormhole.command.handlers.AdminReaders.reading;
import static com.wormhole_xtreme.wormhole.utils.ChatText.heading;
import static com.wormhole_xtreme.wormhole.utils.ChatText.info;
import static com.wormhole_xtreme.wormhole.utils.ChatText.usageBody;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.utils.ChatText;

/**
 * The usage lines and the verb list of {@code /wormhole gate}, read by key from {@code lang/} (#26).
 *
 * <p>Each goes through the real command to an English reader, against the text the code built before
 * the move, written here from it; and to a reader of the test language qr, which holds the usage and
 * help keys, so a call site that reads the server's language or nobody's shows as English.
 */
class GateUsageMessagesTest
{
    private static final String BUILD_NEAR_MISS = "Standard A world 0.5 -60 0 south";
    private static final String QR_LEAD = "QR use ";

    /** Each verb's usage as the guide's command table gave it before the move, aliases included. */
    private static final Map<String, String> OLD_USAGES = Map.ofEntries(
        Map.entry("build", "/wormhole gate build <shape> [group]"),
        Map.entry("dial", "/wormhole gate dial <from> <to> [idc]"),
        Map.entry("preview", "/wormhole gate preview <action>"),
        Map.entry("complete", "/wormhole gate complete <name> [idc=IDC] [net=NET]"),
        Map.entry("create", "/wormhole gate complete <name> [idc=IDC] [net=NET]"),
        Map.entry("list", "/wormhole gate list [network]"),
        Map.entry("go", "/wormhole gate go <gate|destination>"),
        Map.entry("force", "/wormhole gate force <gate|-all>"),
        Map.entry("edit", "/wormhole gate edit <gate> <field> [value]"),
        Map.entry("remove", "/wormhole gate remove <gate> [-destroy]"),
        Map.entry("delete", "/wormhole gate remove <gate> [-destroy]"),
        Map.entry("regen", "/wormhole gate regen <gate|-all> [-shape <shape>] [-fill] [-water]"),
        Map.entry("regenerate", "/wormhole gate regen <gate|-all> [-shape <shape>] [-fill] [-water]"),
        Map.entry("validate", "/wormhole gate validate <gate|-all>"),
        Map.entry("shapes", "/wormhole gate shapes <reload|validate|enable|disable> [name]"));

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PluginTestSupport.remove();
    }

    private static String oldUsage(final String line)
    {
        return "Usage: " + usageBody(line);
    }

    private static String qrUsage(final String line)
    {
        return QR_LEAD + usageBody(line) + ".";
    }

    private static List<String> sentTo(final CommandSender sender)
    {
        final ArgumentCaptor<String> lines = ArgumentCaptor.forClass(String.class);
        verify(sender, atLeastOnce()).sendMessage(lines.capture());
        return new ArrayList<>(lines.getAllValues());
    }

    private static String verbs(final String separator, final String... verbs)
    {
        return Stream.of(verbs).map(ChatText::command).collect(Collectors.joining(separator));
    }

    /** The verb list as the code built it before: the usage, then each job's heading and its verbs. */
    private static List<String> oldVerbList(final String usage, final List<String> headings, final String separator)
    {
        return List.of(info(usage),
            info("  " + heading(headings.get(0)) + " " + verbs(separator, "build", "preview", "complete")),
            info("  " + heading(headings.get(1)) + " " + verbs(separator, "list", "go", "dial", "force")),
            info("  " + heading(headings.get(2)) + " " + verbs(separator, "edit", "remove", "regen", "validate")),
            info("  " + heading(headings.get(3)) + " " + verbs(separator, "shapes", "import")));
    }

    @Test
    void theVerbListReadsAsItDidBefore()
    {
        final CommandSender console = mock(CommandSender.class);
        assertTrue(new GateCommand().execute(console, new String[] { "gate" }));

        assertEquals(oldVerbList(oldUsage("/wormhole gate <verb> ..."),
            List.of("Building:", "Using gates:", "Looking after gates:", "Shapes and imports:"), ", "),
            sentTo(console));
    }

    @Test
    void theVerbListIsInTheReadersLanguage()
    {
        final Player player = reading("qr");
        assertTrue(new GateCommand().execute(player, new String[] { "gate" }));

        assertEquals(oldVerbList(qrUsage("/wormhole gate <verb> ..."),
            List.of("QR building:", "QR using:", "QR looking after:", "QR shapes:"), " / "), sentTo(player));
    }

    /** The twelve lines, aliases too, are what the guide's table gave. */
    @Test
    void everyVerbsUsageIsTheLineItWas()
    {
        OLD_USAGES.forEach((verb, line) -> assertEquals(line, GateCommand.usageOf(verb), verb));
    }

    /** A verb whose handler cannot use the line is answered with its usage, in the reader's language. */
    @Test
    void aVerbThatCannotUseTheLineIsAnsweredWithItsUsageAsBeforeAndInTheReadersLanguage()
    {
        final Player english = reading("en");
        final Player qr = reading("qr");
        new GateCommand().execute(english, new String[] { "gate", "force" });
        new GateCommand().execute(qr, new String[] { "gate", "force" });

        verify(english).sendMessage(info(oldUsage(OLD_USAGES.get("force"))));
        verify(qr).sendMessage(info(qrUsage(OLD_USAGES.get("force"))));
    }

    @Test
    void theConsoleBuildAndDialUsagesReadAsBeforeAndInTheReadersLanguage()
    {
        final ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        final Player qr = reading("qr");
        final String[] nearMiss = BUILD_NEAR_MISS.split(" ");
        final String build = "/wormhole gate build <shape> <name> <world> <x> <y> <z> <facing> [net=NET] [idc=IDC]";
        final String dial = "/wormhole gate dial <from> <to> [idc]";
        GateConsoleCommands.build(console, nearMiss);
        GateConsoleCommands.build(qr, nearMiss);
        GateConsoleCommands.dial(console, new String[] { "Abydos" });
        GateConsoleCommands.dial(qr, new String[] { "Abydos" });

        verify(console).sendMessage(info(oldUsage(build)));
        verify(qr).sendMessage(info(qrUsage(build)));
        verify(console).sendMessage(info(oldUsage(dial)));
        verify(qr).sendMessage(info(qrUsage(dial)));
    }

    /** The syntax constants stay what they were, as arguments to the label. */
    @Test
    void theConsoleUsageConstantsAreTheSyntaxAlone()
    {
        assertEquals("/wormhole gate build <shape> <name> <world> <x> <y> <z> <facing> [net=NET] [idc=IDC]",
            GateConsoleCommands.BUILD_USAGE);
        assertEquals("/wormhole gate dial <from> <to> [idc]", GateConsoleCommands.DIAL_USAGE);
    }
}
