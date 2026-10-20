package com.wormhole_xtreme.wormhole.command.handlers;

import static com.wormhole_xtreme.wormhole.command.handlers.AdminReaders.reading;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.MarkedYAMLException;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.plugin.EconomySupport;
import com.wormhole_xtreme.wormhole.utils.ChatText;
import com.wormhole_xtreme.wormhole.utils.Messages;

/**
 * What {@code /wormhole config -reload} reports, read by key from {@code lang/} (#26).
 *
 * <p>Each line goes through the real command twice: to an English admin, against the concatenation
 * {@code ConfigCommand} and {@code ConfigReload} built before the move, copied from them, and to an
 * admin whose client reads the test language qq, so a line built in the server's language instead
 * of the reader's shows as English. A change applied at once and one that waits, each way a value in
 * the file is refused, and each way the file cannot be read are covered in both.
 */
class ConfigReloadMessagesTest
{
    private static final String CONFIG = "config.yml";

    /** One value of each kind refused: a parser's word, a switch, a whole number, a number and a bare switch. */
    private static final String REFUSED = "ring-default-style: banana\ngate-sounds-enabled: loud\nring-reach: 2.5\n"
        + "gate-sound-volume: loud\nlog-level: on\n";

    private static final String NOT_YAML = "economy-enabled: true\nmirror-names: [unclosed\n";

    @TempDir
    File dataFolder;

    @BeforeEach
    void setUp() throws Exception
    {
        final WormholeXTreme plugin = PluginTestSupport.install();
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        ConfigTestSupport.clear();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        ConfigTestSupport.clear();
        Messages.load();
        PluginTestSupport.remove();
    }

    private void reload(final Player player, final String yaml) throws IOException
    {
        Files.writeString(new File(dataFolder, CONFIG).toPath(), yaml, StandardCharsets.UTF_8);
        new ConfigCommand().execute(player, new String[] { "config", "-reload" });
    }

    /** A change taken at once, and one whose applying fails as it would with no Vault. */
    private void reloadTwoChanges(final Player player) throws IOException
    {
        ConfigTestSupport.set(ConfigKeys.GATE_SOUNDS_ENABLED, true);
        ConfigTestSupport.set(ConfigKeys.ECONOMY_ENABLED, false);
        try (MockedStatic<EconomySupport> economy = mockStatic(EconomySupport.class))
        {
            economy.when(EconomySupport::enableEconomy).thenThrow(new IllegalStateException("no Vault"));
            reload(player, "gate-sounds-enabled: false\neconomy-enabled: true\n");
        }
    }

    private static void setRefusedDefaults()
    {
        ConfigTestSupport.set(ConfigKeys.RING_DEFAULT_STYLE, "SEQUENTIAL");
        ConfigTestSupport.set(ConfigKeys.GATE_SOUNDS_ENABLED, true);
        ConfigTestSupport.set(ConfigKeys.RING_REACH, 4);
        ConfigTestSupport.set(ConfigKeys.GATE_SOUND_VOLUME, 1.5);
        ConfigTestSupport.set(ConfigKeys.LOG_LEVEL, "WARNING");
    }

    /** What SnakeYAML says of {@link #NOT_YAML}, and on which line, as the reload names it. */
    private static MarkedYAMLException brokenAt()
    {
        try
        {
            new Yaml().load(NOT_YAML);
        }
        catch (final MarkedYAMLException e)
        {
            return e;
        }
        throw new AssertionError("the broken file parsed");
    }

    /** The old rejection line: the parser's words, then the value kept. */
    private static String oldKept(final String refusal, final Object kept)
    {
        return ChatText.error(refusal + " Kept " + ChatText.value(String.valueOf(kept)) + ".");
    }

    private static String qqKept(final String refusal, final Object kept)
    {
        return ChatText.error("QQ " + refusal + " kept " + ChatText.value(String.valueOf(kept)) + ".");
    }

    private static String oldUnreadable(final String why)
    {
        return ChatText.error(why + "; no setting changed.");
    }

    private static String qqUnreadable(final String why)
    {
        return ChatText.error("QQ unreadable " + why + ".");
    }

    /** Each change, the one applied at once and the one waiting for a restart, as before. */
    @Test
    void changesReadAsTheyDidBefore() throws IOException
    {
        final Player player = reading("en");
        reloadTwoChanges(player);

        verify(player).sendMessage(ChatText.info(ChatText.command("GATE_SOUNDS_ENABLED") + ": "
            + ChatText.value(String.valueOf(true)) + " -> " + ChatText.value(String.valueOf(false)) + "."));
        verify(player).sendMessage(ChatText.info(ChatText.command("ECONOMY_ENABLED") + ": "
            + ChatText.value(String.valueOf(false)) + " -> " + ChatText.value(String.valueOf(true))
            + ", but it could not be applied now: " + new IllegalStateException("no Vault")
            + ". It applies at the next restart."));
    }

    /** The same changes reach a qq admin in qq. */
    @Test
    void changesAreInTheReadersLanguage() throws IOException
    {
        final Player player = reading("qq");
        reloadTwoChanges(player);

        verify(player).sendMessage(ChatText.info("QQ " + ChatText.command("GATE_SOUNDS_ENABLED") + " "
            + ChatText.value("true") + " to " + ChatText.value("false") + "."));
        verify(player).sendMessage(ChatText.info("QQ " + ChatText.command("ECONOMY_ENABLED") + " "
            + ChatText.value("false") + " to " + ChatText.value("true") + " unapplied "
            + new IllegalStateException("no Vault") + "."));
    }

    /** Each kind of refused value, and a file that changes nothing, as before. */
    @Test
    void refusalsReadAsTheyDidBefore() throws IOException
    {
        final Player player = reading("en");
        setRefusedDefaults();
        reload(player, REFUSED);
        reload(player, "ring-reach: 4\n");

        verify(player).sendMessage(oldKept("RING_DEFAULT_STYLE is CONCURRENT or SEQUENTIAL, not \"banana\". "
            + "Fast and slow work too.", "SEQUENTIAL"));
        verify(player).sendMessage(oldKept(ConfigKeys.GATE_SOUNDS_ENABLED + " is " + "true or false"
            + ", not \"" + "loud" + "\".", true));
        verify(player).sendMessage(oldKept(ConfigKeys.RING_REACH + " is " + "a whole number"
            + ", not \"" + 2.5 + "\".", 4));
        verify(player).sendMessage(oldKept(ConfigKeys.GATE_SOUND_VOLUME + " is " + "a number"
            + ", not \"" + "loud" + "\".", 1.5));
        verify(player).sendMessage(oldKept(ConfigKeys.LOG_LEVEL + " was read as a switch (" + true
            + "); write the value in quotes.", "WARNING"));
        verify(player).sendMessage(ChatText.info("Reloaded config.yml: no setting changed."));
    }

    /** The same refusals reach a qq admin in qq, each setting and value as written. */
    @Test
    void refusalsAreInTheReadersLanguage() throws IOException
    {
        final Player player = reading("qq");
        setRefusedDefaults();
        reload(player, REFUSED);
        reload(player, "ring-reach: 4\n");

        verify(player).sendMessage(qqKept("QQ RING_DEFAULT_STYLE style CONCURRENT/SEQUENTIAL not \"banana\" Fast/slow.",
            "SEQUENTIAL"));
        verify(player).sendMessage(qqKept("QQ GATE_SOUNDS_ENABLED true/false not \"loud\".", true));
        verify(player).sendMessage(qqKept("QQ RING_REACH whole not \"2.5\".", 4));
        verify(player).sendMessage(qqKept("QQ GATE_SOUND_VOLUME number not \"loud\".", 1.5));
        verify(player).sendMessage(qqKept("QQ LOG_LEVEL switch true.", "WARNING"));
        verify(player).sendMessage(ChatText.info("QQ unchanged."));
    }

    /** Each file that cannot be read, as before: empty, not settings, not YAML, and missing. */
    @Test
    void unreadableFilesReadAsTheyDidBefore() throws IOException
    {
        final Player player = reading("en");
        final MarkedYAMLException broken = brokenAt();
        reload(player, "");
        reload(player, "- not\n- settings\n");
        reload(player, NOT_YAML);
        Files.delete(new File(dataFolder, CONFIG).toPath());
        new ConfigCommand().execute(player, new String[] { "config", "-reload" });

        verify(player).sendMessage(oldUnreadable(CONFIG + " is empty"));
        verify(player).sendMessage(oldUnreadable(CONFIG + " is not a list of settings"));
        verify(player).sendMessage(oldUnreadable(CONFIG + " is not valid YAML: " + broken.getProblem() + " on line "
            + (broken.getProblemMark().getLine() + 1)));
        verify(player).sendMessage(oldUnreadable("There is no " + CONFIG + " in the plugin folder"));
    }

    /** The same files reach a qq admin in qq, SnakeYAML's own words about the break unchanged. */
    @Test
    void unreadableFilesAreInTheReadersLanguage() throws IOException
    {
        final Player player = reading("qq");
        final MarkedYAMLException broken = brokenAt();
        reload(player, "");
        reload(player, "- not\n- settings\n");
        reload(player, NOT_YAML);
        Files.delete(new File(dataFolder, CONFIG).toPath());
        new ConfigCommand().execute(player, new String[] { "config", "-reload" });

        verify(player).sendMessage(qqUnreadable("QQ config.yml empty"));
        verify(player).sendMessage(qqUnreadable("QQ config.yml not settings"));
        verify(player).sendMessage(qqUnreadable("QQ config.yml not yaml QQ " + broken.getProblem() + " line "
            + (broken.getProblemMark().getLine() + 1)));
        verify(player).sendMessage(qqUnreadable("QQ no config.yml"));
    }

    /**
     * A config.yml that cannot be opened, here a folder, says the log has why, as before and in qq.
     *
     * <p>Only where opening a folder fails: elsewhere it opens and the read fails inside SnakeYAML,
     * which is the not-YAML line instead.
     */
    @Test
    void aFileThatCannotBeOpenedSaysTheLogHasWhy() throws IOException
    {
        final File folder = new File(dataFolder, CONFIG);
        assertTrue(folder.mkdir(), "a folder where config.yml goes");
        assumeTrue(cannotOpen(folder), "this platform opens a folder as a file");
        final Player english = reading("en");
        final Player qq = reading("qq");
        new ConfigCommand().execute(english, new String[] { "config", "-reload" });
        new ConfigCommand().execute(qq, new String[] { "config", "-reload" });

        verify(english).sendMessage(oldUnreadable(CONFIG + " could not be read, as the log says"));
        verify(qq).sendMessage(qqUnreadable("QQ config.yml not read"));
    }

    /** @return whether opening the file fails for a reason other than its being missing */
    private static boolean cannotOpen(final File file)
    {
        try (InputStream in = Files.newInputStream(file.toPath()))
        {
            return in == null;
        }
        catch (final NoSuchFileException missing)
        {
            return false;
        }
        catch (final IOException e)
        {
            return true;
        }
    }
}
