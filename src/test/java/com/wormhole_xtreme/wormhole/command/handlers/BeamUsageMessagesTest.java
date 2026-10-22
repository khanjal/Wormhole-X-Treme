package com.wormhole_xtreme.wormhole.command.handlers;

import static com.wormhole_xtreme.wormhole.command.handlers.AdminReaders.reading;
import static com.wormhole_xtreme.wormhole.utils.ChatText.command;
import static com.wormhole_xtreme.wormhole.utils.ChatText.error;
import static com.wormhole_xtreme.wormhole.utils.ChatText.info;
import static com.wormhole_xtreme.wormhole.utils.ChatText.usageBody;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.beam.BeamManager;
import com.wormhole_xtreme.wormhole.utils.Messages;

/**
 * The usage lines of {@code /wormhole beam}, read by key from {@code lang/} (#26).
 *
 * <p>Each goes through the real command to an English reader, against the text the code built before
 * the move, written here from it; and to a reader of the test language qr, which holds the usage keys,
 * so a call site that reads the server's language or nobody's shows as English. The lines only the
 * console is sent read the server's language, set to qr.
 */
class BeamUsageMessagesTest
{
    private static final String TO = "/wormhole beam to <name>";
    private static final String EDIT = "/wormhole beam admin set|remove|cost <name>";
    private static final String GOTO = "/wormhole beam admin goto <player|destination>|<x> <y> <z> [world]";
    private static final String SEND =
        "/wormhole beam admin send <player> <player|destination>|<x> <y> <z> [world]";
    private static final String PLACE = "/wormhole beam place <list|set|remove> [name]";
    private static final String COST = "/wormhole beam admin cost <name> <amount|-default>";
    private static final String PLACE_SET = "/wormhole beam place set <name>";
    private static final String PLACE_REMOVE = "/wormhole beam place remove <name>";
    private static final String OTHERS = "/wormhole beam list|place|admin";
    private static final String QR = "qr";
    private static final String BEAM = "beam";
    private static final String ADMIN = "admin";
    private static final String UNKNOWN = "Unknown beam command.";

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install();
        BeamManager.clear();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        BeamManager.clear();
        PluginTestSupport.remove();
    }

    private static List<String> sentTo(final CommandSender sender)
    {
        final ArgumentCaptor<String> lines = ArgumentCaptor.forClass(String.class);
        verify(sender, atLeastOnce()).sendMessage(lines.capture());
        return new ArrayList<>(lines.getAllValues());
    }

    private static void beam(final CommandSender sender, final String... rest)
    {
        final String[] args = new String[rest.length + 1];
        args[0] = BEAM;
        System.arraycopy(rest, 0, args, 1, rest.length);
        new BeamCommand().execute(sender, args);
    }

    private static void admin(final CommandSender sender, final String... rest)
    {
        final String[] args = new String[rest.length + 1];
        args[0] = ADMIN;
        System.arraycopy(rest, 0, args, 1, rest.length);
        beam(sender, args);
    }

    /** Every way a player is answered with a usage, in the order the scenario below runs them. */
    private static void tryEach(final Player player)
    {
        beam(player);
        beam(player, "to");
        beam(player, "fly");
        admin(player);
        admin(player, "set");
        admin(player, "bogus", "Dock");
        admin(player, "cost", "Dock");
        admin(player, "goto");
        admin(player, "send");
        beam(player, "place");
        beam(player, "place", "fly");
        beam(player, "place", "set");
        beam(player, "place", "remove");
    }

    /**
     * What those lines were: each usage as {@code form} builds it, and the "Or" line as {@code or}
     * builds it from the syntax it names.
     */
    private static List<String> expected(final UnaryOperator<String> form, final UnaryOperator<String> or)
    {
        return List.of(form.apply(TO), or.apply(usageBody(OTHERS)),
            form.apply(TO),
            error(UNKNOWN), form.apply(TO),
            form.apply(EDIT), form.apply(GOTO), form.apply(SEND),
            form.apply(EDIT),
            form.apply(EDIT),
            form.apply(COST),
            form.apply(GOTO),
            form.apply(SEND),
            form.apply(PLACE),
            error("Unknown place command. To travel, use " + usageBody(TO) + "."), form.apply(PLACE),
            form.apply(PLACE_SET),
            form.apply(PLACE_REMOVE));
    }

    private static String oldForm(final String line)
    {
        return info("Usage: " + usageBody(line));
    }

    private static String qrForm(final String line)
    {
        return info("QR use " + usageBody(line) + ".");
    }

    @Test
    void theUsagesReadAsTheyDidBefore()
    {
        final Player player = reading("en");
        tryEach(player);

        assertEquals(expected(BeamUsageMessagesTest::oldForm, or -> info("Or " + or + ".")), sentTo(player));
    }

    @Test
    void theUsagesAreInThePlayersLanguage()
    {
        final Player player = reading(QR);
        tryEach(player);

        assertEquals(expected(BeamUsageMessagesTest::qrForm, or -> info("QR or " + or + ".")), sentTo(player));
    }

    /** The console reads the server's language: English, then qr once set. */
    private static void consoleTries(final CommandSender console)
    {
        beam(console);
        admin(console, "goto");
        admin(console, "send");
    }

    @Test
    void theConsoleIsToldTheUsagesInTheServersLanguage()
    {
        final CommandSender english = mock(ConsoleCommandSender.class);
        final CommandSender qr = mock(ConsoleCommandSender.class);
        for (final CommandSender console : List.of(english, qr))
        {
            when(console.hasPermission(anyString())).thenReturn(Boolean.TRUE);
        }
        consoleTries(english);
        inServerLanguage(QR, () -> consoleTries(qr));

        final String notAPlayer = error(command("goto") + " moves you, and there is nowhere for console or a command block to beam from.");
        assertEquals(List.of(oldForm(TO), info("Or " + usageBody(OTHERS) + "."), notAPlayer, oldForm(SEND), oldForm(SEND)),
            sentTo(english));
        assertEquals(List.of(qrForm(TO), info("QR or " + usageBody(OTHERS) + "."), notAPlayer, qrForm(SEND), qrForm(SEND)),
            sentTo(qr));
    }

    private static void inServerLanguage(final String language, final Runnable scenario)
    {
        try (MockedStatic<ConfigManager> config = mockStatic(ConfigManager.class))
        {
            config.when(ConfigManager::getServerLanguage).thenReturn(language);
            Messages.load();
            try
            {
                scenario.run();
            }
            finally
            {
                config.when(ConfigManager::getServerLanguage).thenReturn(Messages.DEFAULT_LANGUAGE);
                Messages.load();
            }
        }
    }
}
