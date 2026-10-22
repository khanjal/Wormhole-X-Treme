package com.wormhole_xtreme.wormhole.command;

import static com.wormhole_xtreme.wormhole.command.CommandReaders.reading;
import static com.wormhole_xtreme.wormhole.utils.ChatText.error;
import static com.wormhole_xtreme.wormhole.utils.ChatText.heading;
import static com.wormhole_xtreme.wormhole.utils.ChatText.info;
import static com.wormhole_xtreme.wormhole.utils.ChatText.usageBody;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions;
import com.wormhole_xtreme.wormhole.utils.ChatText;

/**
 * The {@code /wormhole} list, its fallback usage line, and the usage lines of {@code complete} and
 * {@code dial}, read by key from {@code lang/} (#26).
 *
 * <p>Each goes through the real command to an English reader, against the text the code built before
 * the move, written here from it; and to a reader of the test language qr, which holds the usage and
 * help keys, so a call site that reads the server's language or nobody's shows as English. The
 * recipient also has to reach {@code ChatText.usage}, so a null one is a failure here, not English.
 */
class UsageHelpMessagesTest
{
    private static final String COMPLETE_USAGE = "/wormhole complete <name> [idc=<code>] [net=<network>]";
    private static final String DIAL_USAGE = "/dial <gate> [idc]";
    private static final String OWNER_USAGE = "/wormhole owner <gate> [player]";

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

    /** A usage line as the code built it before: the English label, then the coloured syntax. */
    private static String oldUsage(final String line)
    {
        return "Usage: " + usageBody(line);
    }

    /** The same line in qr, whose label is {@code QR use {0}.}. */
    private static String qrUsage(final String line)
    {
        return "QR use " + usageBody(line) + ".";
    }

    /** What {@code /wormhole} alone sent, in order. */
    private static List<String> sentTo(final CommandSender sender)
    {
        final ArgumentCaptor<String> lines = ArgumentCaptor.forClass(String.class);
        verify(sender, atLeastOnce()).sendMessage(lines.capture());
        return new ArrayList<>(lines.getAllValues());
    }

    /** The list as the code built it before: a header, then each visible subcommand under its heading. */
    private static List<String> oldList(final String header, final List<String> headings)
    {
        final String[] subcommands = { "gate", "ring", "beam", "mirror", "config", "compass" };
        final List<String> lines = new ArrayList<>();
        lines.add(info(header));
        for (int i = 0; i < subcommands.length; i++)
        {
            final SubCommands.Entry entry = SubCommands.find(subcommands[i]);
            lines.add(info(heading(headings.get(i)) + " " + usageBody(entry.getUsage())));
        }
        return lines;
    }

    @Test
    void theListReadsAsItDidBefore()
    {
        final CommandSender console = mock(CommandSender.class);
        assertTrue(new Wormhole().onCommand(console, null, "wormhole", new String[0]));

        assertEquals(oldList("Wormhole X-Treme commands:",
            List.of("Gates:", "Rings:", "Beams:", "Mirrors:", "Settings:", "Other:")), sentTo(console));
    }

    @Test
    void theListIsInTheReadersLanguage()
    {
        final Player player = reading("qr");
        assertTrue(new Wormhole().onCommand(player, null, "wormhole", new String[0]));

        assertEquals(oldList("QR commands:",
            List.of("QR gates:", "QR rings:", "QR beams:", "QR mirrors:", "QR settings:", "QR other:")), sentTo(player));
    }

    @Test
    void aPlayerWithoutTheConfigNodeIsListedGateBuildInTheirLanguage()
    {
        final Player player = mock(Player.class);
        when(player.getLocale()).thenReturn("qr");
        when(player.hasPermission(anyString())).thenReturn(false);
        when(player.hasPermission("wormhole.build.preview")).thenReturn(true);
        new Wormhole().onCommand(player, null, "wormhole", new String[0]);

        final List<String> sent = sentTo(player);
        assertTrue(sent.contains(info(heading("QR gates:") + " " + usageBody("/wormhole gate build <shape> [group]"))),
            "a builder who may only preview is shown gate build under the reader's heading: " + sent);
    }

    @Test
    void theFallbackUsageReadsAsItDidBeforeAndInTheReadersLanguage()
    {
        final CommandSender console = mock(CommandSender.class);
        final Player player = reading("qr");
        new Wormhole().onCommand(console, null, "wormhole", new String[] { "owner" });
        new Wormhole().onCommand(player, null, "wormhole", new String[] { "owner" });

        verify(console).sendMessage(info(oldUsage(OWNER_USAGE)));
        verify(player).sendMessage(info(qrUsage(OWNER_USAGE)));
    }

    @Test
    void completeHelpSaysItsUsageAsBeforeAndInTheReadersLanguage()
    {
        final Player english = reading("en");
        final Player qr = reading("qr");
        new Complete().onCommand(english, null, "wormhole", new String[] { "-help" });
        new Complete().onCommand(qr, null, "wormhole", new String[] { "-help" });

        verify(english).sendMessage(info(oldUsage(COMPLETE_USAGE)));
        verify(qr).sendMessage(info(qrUsage(COMPLETE_USAGE)));
    }

    @Test
    void completeWithArgumentsThatThrowCarriesItsUsageAsBeforeAndInTheReadersLanguage()
    {
        final Player english = reading("en");
        final Player qr = reading("qr");
        try (MockedStatic<WXPermissions> permissions = mockStatic(WXPermissions.class))
        {
            permissions.when(() -> WXPermissions.checkWXPermissions(any(Player.class), anyString(),
                any(WXPermissions.PermissionType.class))).thenThrow(new IllegalStateException("refused"));
            new Complete().onCommand(english, null, "wormhole", new String[] { "Fresh" });
            new Complete().onCommand(qr, null, "wormhole", new String[] { "Fresh" });
        }

        verify(english).sendMessage(error("Those arguments did not work. " + oldUsage(COMPLETE_USAGE)));
        verify(qr).sendMessage(error("Those arguments did not work. " + qrUsage(COMPLETE_USAGE)));
    }

    @Test
    void dialWithTheWrongNumberOfArgumentsSaysItsUsageAsBeforeAndInTheReadersLanguage()
    {
        final CommandSender console = mock(CommandSender.class);
        final Player qr = reading("qr");
        new Dial().onCommand(console, null, "dial", new String[0]);
        new Dial().onCommand(qr, null, "dial", new String[0]);

        verify(console).sendMessage(info(oldUsage(DIAL_USAGE)));
        verify(qr).sendMessage(info(qrUsage(DIAL_USAGE)));
    }

    /** The label is a key, and a null reader gets the server's. */
    @Test
    void theUsageHelperReadsItsLabelByRecipient()
    {
        final Player qr = reading("qr");

        assertEquals(oldUsage(OWNER_USAGE), ChatText.usage(OWNER_USAGE));
        assertEquals(oldUsage(OWNER_USAGE), ChatText.usage(null, OWNER_USAGE));
        assertEquals(qrUsage(OWNER_USAGE), ChatText.usage(qr, OWNER_USAGE));
        assertEquals("Usage: ", ChatText.usageLead(null));
        assertEquals("QR use ", ChatText.usageLead(qr));
    }
}
