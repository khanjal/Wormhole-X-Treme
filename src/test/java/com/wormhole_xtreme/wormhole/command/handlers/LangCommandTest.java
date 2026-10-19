package com.wormhole_xtreme.wormhole.command.handlers;

import static com.wormhole_xtreme.wormhole.utils.ChatMatchers.errorBody;
import static com.wormhole_xtreme.wormhole.utils.ChatMatchers.infoBody;
import static com.wormhole_xtreme.wormhole.utils.ChatMatchers.plainContains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.command.SubCommands;

/**
 * {@code /wormhole lang export [code]}, the one thing that writes to the plugin folder's {@code lang/} (#26).
 *
 * <p>The code becomes a file name, so what reaches the disk must be a language code and nothing else:
 * a code that is not one writes nothing and makes no folder. A file the owner already has is theirs,
 * and is never replaced.
 */
class LangCommandTest
{
    @TempDir
    File dataFolder;

    private CommandSender console;

    @BeforeEach
    void setUp() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        PluginTestSupport.install(plugin);
        console = mock(CommandSender.class);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PluginTestSupport.remove();
    }

    private File lang()
    {
        return new File(dataFolder, "lang");
    }

    private void run(final CommandSender sender, final String... args)
    {
        assertTrue(new LangCommand().execute(sender, args));
    }

    private static Player player(final boolean op)
    {
        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOp()).thenReturn(op);
        when(player.hasPermission(anyString())).thenReturn(op);
        return player;
    }

    /** From the console, no code writes the commented English and says where. */
    @Test
    void theConsoleExportsTheCommentedEnglish() throws Exception
    {
        run(console, "lang", "export");

        final File file = new File(lang(), "en.yml");
        assertTrue(Files.readString(file.toPath(), StandardCharsets.UTF_8).contains("# compass.none: "),
            "en.yml should be the commented English");
        verify(console).sendMessage(infoBody("Wrote " + file.getPath()
            + ", every message commented out. Uncomment the ones to reword, then run /wormhole config -reload."));
    }

    /** A player with wormhole.config gets a code's file in English, live, to translate. */
    @Test
    void aPlayerExportsEnglishToTranslate() throws Exception
    {
        final Player player = player(true);

        run(player, "lang", "export", "de");

        final File file = new File(lang(), "de.yml");
        assertTrue(Files.readString(file.toPath(), StandardCharsets.UTF_8).contains("\ncompass.none: "),
            "de.yml should hold the English live");
        verify(player).sendMessage(infoBody("Wrote " + file.getPath()
            + " in English, ready to translate. Run /wormhole config -reload when it is done."));
    }

    /** A player without wormhole.config writes nothing. */
    @Test
    void aPlayerWithoutTheNodeWritesNothing()
    {
        run(player(false), "lang", "export", "de");

        assertFalse(lang().exists(), "a refused export should not even make the folder");
    }

    /** The code is read as server-language reads it: capitals lower-cased, a dash read as _. */
    @Test
    void aCodeIsReadAsServerLanguageIs()
    {
        run(console, "lang", "export", "pt-BR");
        run(console, "lang", "export", "DE");

        assertTrue(new File(lang(), "pt_br.yml").isFile(), "pt-BR should be pt_br, as for server-language");
        assertTrue(new File(lang(), "de.yml").isFile(), "DE should be de");
    }

    /** The owner's own file stays exactly as it was, and the reply names it. */
    @Test
    void anExistingFileIsLeftAloneAndNamed() throws Exception
    {
        assertTrue(lang().mkdirs());
        final File file = new File(lang(), "de.yml");
        Files.writeString(file.toPath(), "compass.none: \"Meins.\"\n", StandardCharsets.UTF_8);

        run(console, "lang", "export", "de");

        assertEquals("compass.none: \"Meins.\"\n", Files.readString(file.toPath(), StandardCharsets.UTF_8));
        verify(console).sendMessage(errorBody(file.getPath() + " already exists, so it was left as it is."));
    }

    /** Anything that is not a language code is refused before the disk is touched. */
    @Test
    void aCodeThatIsNotOneWritesNothingAndMakesNoFolder()
    {
        for (final String bad : new String[] { "../x", "de/../x", "..\\x", "", "abcdefghijklmnopq", "dé", "_de", "de_",
            "de1", "true", "nul", "con", "aux", "prn" })
        {
            run(console, "lang", "export", bad);

            assertFalse(lang().exists(), "'" + bad + "' should create no file and no folder");
        }
        verify(console).sendMessage(errorBody("../x is not a language code: letters and _, as a client names its"
            + " language, such as de or pt_br."));
    }

    /** A device name is refused as one, not as a code with the wrong letters in it. */
    @Test
    void aDeviceNameIsRefusedAsOne()
    {
        run(console, "lang", "export", "con");

        assertFalse(lang().exists(), "con should create no file and no folder");
        verify(console).sendMessage(errorBody("con is a name Windows keeps for a device, so it cannot name a lang file."));
    }

    /** A verb other than export, or too many words, is answered with the usage. */
    @Test
    void anythingElseIsTheUsage()
    {
        run(console, "lang");
        run(console, "lang", "check");
        run(console, "lang", "export", "de", "more");

        assertFalse(lang().exists());
        verify(console, times(3))
            .sendMessage(plainContains("/wormhole lang export [code] writes a starter lang file"));
    }

    /** Tab offers export, then the languages the jar ships. */
    @Test
    void tabOffersExportThenTheShippedLanguages()
    {
        final SubCommands.Entry entry = SubCommands.find("lang");

        assertEquals(List.of("export"), entry.completeArgs(console, new String[] { "lang", "" }));
        assertTrue(entry.completeArgs(console, new String[] { "lang", "export", "en" }).contains("en"),
            "English always ships");
        assertTrue(entry.completeArgs(console, new String[] { "lang", "other", "" }).isEmpty());
    }

    /** Tab offers nothing to a player who could not run it. */
    @Test
    void tabOffersNothingWithoutTheNode()
    {
        final SubCommands.Entry entry = SubCommands.find("lang");
        final Player player = player(false);

        assertTrue(entry.completeArgs(player, new String[] { "lang", "" }).isEmpty(), "export should not be offered");
        assertTrue(entry.completeArgs(player, new String[] { "lang", "export", "" }).isEmpty(), "nor the languages");
        assertEquals(List.of("export"), entry.completeArgs(player(true), new String[] { "lang", "" }),
            "a player with the node is offered it");
    }
}
