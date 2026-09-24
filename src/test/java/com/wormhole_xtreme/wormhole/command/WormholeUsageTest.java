package com.wormhole_xtreme.wormhole.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.utils.ChatText;

/**
 * What {@code /wormhole} says when a command was mistyped, and when it is typed alone (#325).
 *
 * <p>A handler that could not use a line answered false, and Bukkit answers false by printing
 * the whole {@code usage:} block from plugin.yml, in one colour, straight after the handler's own
 * error: every subcommand listed when the player had asked about one gate. The dispatcher now
 * answers for it, with the one usage line of the subcommand that was typed.
 */
class WormholeUsageTest
{
    private final Wormhole command = new Wormhole();
    private CommandSender console;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        console = mock(CommandSender.class);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PluginTestSupport.remove();
    }

    private static String plainContaining(final String text)
    {
        return ArgumentMatchers.<String>argThat((String s) -> ChatText.plain(s).contains(text));
    }

    /**
     * A subcommand that answers false gets its own usage line, and the dispatcher answers true so
     * Bukkit prints nothing after it. {@code owner} with no gate is the case seen in game on 1.7.
     */
    @Test
    void aSubcommandThatCannotUseTheLineGetsItsOwnUsageAndNotBukkits()
    {
        assertTrue(command.onCommand(console, null, "wormhole", new String[] { "owner" }),
            "false would have Bukkit print plugin.yml's usage block after ours");

        verify(console).sendMessage(plainContaining("Usage: /wormhole owner <gate> [player]"));
    }

    /** Typed alone, it lists each subcommand under the job it is for, coloured. */
    @Test
    void typedAloneItListsTheSubcommandsByJob()
    {
        assertTrue(command.onCommand(console, null, "wormhole", new String[0]));

        verify(console).sendMessage(plainContaining("Gates: /wormhole gate <"));
        verify(console).sendMessage(plainContaining("Rings: /wormhole ring <"));
        verify(console).sendMessage(plainContaining("Settings: /wormhole config <setting> [value]"));
        verify(console).sendMessage(plainContaining("Other: /wormhole compass [reset]"));
    }

    /** A player without wormhole.config is shown only what the dispatcher would let them run. */
    @Test
    void aPlayerWithoutTheConfigNodeIsListedOnlyWhatTheyMayRun()
    {
        final Player player = mock(Player.class);
        when(player.hasPermission(anyString())).thenReturn(false);

        command.onCommand(player, null, "wormhole", new String[0]);

        verify(player).sendMessage(plainContaining("Beams: /wormhole beam"));
        verify(player, never()).sendMessage(plainContaining("Settings:"));
    }

    /** A call to a method with "usage" in its name, then {@code return false;}, with only blank or comment lines between. */
    private static final Pattern USAGE_THEN_FALSE = Pattern.compile(
        "[Uu]sage\\w*+\\([^;\\r\\n]*+\\);[ \\t]*+(?:\\R[ \\t]*+(?://[^\\r\\n]*+)?)++return false;");

    /**
     * No handler both says its usage and answers false: the dispatcher would say it a second time.
     * Three did, from when false was how a handler asked Bukkit for the usage block.
     */
    @Test
    void noHandlerSaysItsUsageAndThenAnswersFalse() throws IOException
    {
        final Path root = Paths.get("src/main/java/com/wormhole_xtreme/wormhole/command");
        final List<String> found = new ArrayList<>();
        int read = 0;
        try (Stream<Path> files = Files.walk(root))
        {
            for (final Path file : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".java"))::iterator)
            {
                read++;
                if (USAGE_THEN_FALSE.matcher(Files.readString(file)).find())
                {
                    found.add(root.relativize(file).toString());
                }
            }
        }
        assertTrue(read >= 30, "read only " + read + " command files; the walk is not reaching them");
        assertEquals(List.of(), found);
    }

    /**
     * A player who may preview but not configure runs gate build and preview, so the list shows
     * them that, and not the admin verbs. The list used to leave gate out altogether for them.
     * Found by a Sonnet review.
     */
    @Test
    void aPlayerWhoMayOnlyPreviewIsListedGateBuild()
    {
        final Player player = mock(Player.class);
        when(player.hasPermission(anyString())).thenReturn(false);
        when(player.hasPermission(com.wormhole_xtreme.wormhole.model.preview.PreviewPermissions.PREVIEW)).thenReturn(true);

        command.onCommand(player, null, "wormhole", new String[0]);

        verify(player).sendMessage(plainContaining("Gates: /wormhole gate build <shape> [group]"));
        verify(player, never()).sendMessage(plainContaining("Gates: /wormhole gate <"));
    }
}
