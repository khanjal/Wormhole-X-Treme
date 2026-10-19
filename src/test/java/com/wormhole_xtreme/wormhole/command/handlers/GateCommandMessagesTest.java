package com.wormhole_xtreme.wormhole.command.handlers;

import static com.wormhole_xtreme.wormhole.command.handlers.AdminReaders.reading;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.LegacyDatabaseImporter;
import com.wormhole_xtreme.wormhole.utils.ChatText;

/**
 * What {@code /wormhole gate} says for an unknown verb and for {@code gate import}, read by key from
 * {@code lang/} (#26).
 *
 * <p>The importer is mocked to report each count either side of one, with and without moved exits
 * and skipped gates, and each report is sent through the real command to an English admin, against
 * the concatenation the code built before the move, and to a qq admin, so a plural chosen the wrong
 * way round or a call site reading the wrong recipient shows.
 */
class GateCommandMessagesTest
{
    private MockedStatic<LegacyDatabaseImporter> importer;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        importer = mockStatic(LegacyDatabaseImporter.class);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        importer.close();
        PluginTestSupport.remove();
    }

    private static LegacyDatabaseImporter.Result result(final int imported, final int moved, final List<String> skipped)
    {
        final LegacyDatabaseImporter.Result result = mock(LegacyDatabaseImporter.Result.class);
        when(result.getImported()).thenReturn(imported);
        when(result.getMovedExits()).thenReturn(moved);
        when(result.getSkipped()).thenReturn(skipped);
        return result;
    }

    /** Imports one gate with one moved exit, then two with three and two skipped, then none. */
    private void importThrice(final Player player)
    {
        // Each result built before its stubbing starts: a mock stubbed inside thenReturn throws.
        final LegacyDatabaseImporter.Result one = result(1, 1, List.of());
        final LegacyDatabaseImporter.Result two = result(2, 3, List.of("Abydos (no world)", "Chulak"));
        final LegacyDatabaseImporter.Result none = result(0, 0, List.of());
        importer.when(LegacyDatabaseImporter::importGates).thenReturn(one, two, none);
        for (int i = 0; i < 3; i++)
        {
            new GateCommand().execute(player, new String[] { "gate", "import" });
        }
    }

    /** The import report as the code built it before the move. */
    private static String oldImported(final int imported)
    {
        return ChatText.info("Imported " + ChatText.value(String.valueOf(imported)) + " gate"
            + (imported == 1 ? "" : "s") + ". The old database is untouched.");
    }

    private static String oldMoved(final int moved)
    {
        return ChatText.info(ChatText.value(String.valueOf(moved))
            + " of those had an arrival point inside the portal, and were moved clear of it.");
    }

    @Test
    void importReportsReadAsTheyDidBefore()
    {
        final Player player = reading("en");
        importThrice(player);

        verify(player).sendMessage(oldImported(1));
        verify(player).sendMessage(oldImported(2));
        verify(player).sendMessage(oldImported(0));
        verify(player).sendMessage(oldMoved(1));
        verify(player).sendMessage(oldMoved(3));
        verify(player).sendMessage(ChatText.info("  " + ChatText.bad("skipped") + " " + "Abydos (no world)"));
        verify(player).sendMessage(ChatText.info("  " + ChatText.bad("skipped") + " " + "Chulak"));
    }

    @Test
    void importReportsAreInTheReadersLanguage()
    {
        final Player player = reading("qq");
        importThrice(player);

        verify(player).sendMessage(ChatText.info("QQ imported " + ChatText.value("1") + " one."));
        verify(player).sendMessage(ChatText.info("QQ imported " + ChatText.value("2") + " other."));
        verify(player).sendMessage(ChatText.info("QQ imported " + ChatText.value("0") + " other."));
        verify(player).sendMessage(ChatText.info("QQ moved " + ChatText.value("1") + " one."));
        verify(player).sendMessage(ChatText.info("QQ moved " + ChatText.value("3") + " other."));
        verify(player).sendMessage(ChatText.info("QQ Abydos (no world) <" + ChatText.bad("QQskipped") + ">"));
        verify(player).sendMessage(ChatText.info("QQ Chulak <" + ChatText.bad("QQskipped") + ">"));
    }

    /** An unknown verb lists every verb, comma separated in English and as qq joins a list in qq. */
    @Test
    void anUnknownVerbReadsAsBeforeAndInTheReadersLanguage()
    {
        final Player english = reading("en");
        final Player qq = reading("qq");
        new GateCommand().execute(english, new String[] { "gate", "Frobnicate" });
        new GateCommand().execute(qq, new String[] { "gate", "Frobnicate" });

        verify(english).sendMessage(ChatText.error("No such gate command: " + ChatText.command("Frobnicate")
            + ". Try one of: " + GateCommand.verbs().stream().map(ChatText::command).collect(Collectors.joining(", "))
            + "."));
        verify(qq).sendMessage(ChatText.error("QQ no verb " + ChatText.command("Frobnicate") + ": "
            + String.join(" | ", GateCommand.verbs().stream().map(ChatText::command).toList()) + "."));
    }
}
