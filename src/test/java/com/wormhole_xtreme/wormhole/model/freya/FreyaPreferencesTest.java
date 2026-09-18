package com.wormhole_xtreme.wormhole.model.freya;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.utils.DataLayout;

/**
 * The companion file exists only while somebody is using it.
 *
 * <p>Every other store in this plugin is created on startup and left in place. This one is
 * deliberately the opposite, and that is the whole point of it: {@code /wormhole freya} is not
 * advertised anywhere, so a file sitting in the data folder of a server where nobody has found
 * the command would be the one thing that gave it away. It is written when the first player
 * turns a companion on and deleted when the last one turns theirs off.
 *
 * <p>Two failures would each undo that quietly and still look like working code. Creating the
 * file on load -- which is what most Bukkit plugins do, via {@code saveDefaultConfig} -- would
 * put it on every server on earth the moment this shipped. Writing an empty list instead of
 * deleting would leave a permanent record that somebody once found the command, on a server
 * where nobody uses it any more. Neither would ever throw, so only a test catches them.
 */
class FreyaPreferencesTest
{
    private static final UUID SOMEONE = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final UUID SOMEONE_ELSE = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @TempDir
    File dataFolder;

    @BeforeEach
    void setUp() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        PluginTestSupport.install(plugin);
        FreyaPreferences.clear();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        FreyaPreferences.clear();
        PluginTestSupport.remove();
    }

    @Test
    void loadingOnAServerNobodyHasUsedCreatesNoFile()
    {
        assertEquals(0, FreyaPreferences.loadAll(), "an absent file means nobody, not a failure");
        assertFalse(DataLayout.freyaFile().exists(),
            "loading must never create the file -- doing so would put it on every server "
                + "whose players have never heard of the command");
    }

    @Test
    void theFileAppearsOnlyWhenSomebodyTurnsHerOn()
    {
        assertFalse(DataLayout.freyaFile().exists(), "nothing has happened yet");

        FreyaPreferences.setEnabled(SOMEONE, true);

        assertTrue(DataLayout.freyaFile().exists(), "the first person to ask creates the file");
    }

    @Test
    void theFileIsDeletedWhenTheLastPlayerTurnsHerOff()
    {
        FreyaPreferences.setEnabled(SOMEONE, true);
        FreyaPreferences.setEnabled(SOMEONE_ELSE, true);

        FreyaPreferences.setEnabled(SOMEONE, false);
        assertTrue(DataLayout.freyaFile().exists(),
            "one player leaving does not end it -- somebody else is still using her");

        FreyaPreferences.setEnabled(SOMEONE_ELSE, false);
        assertFalse(DataLayout.freyaFile().exists(),
            "an empty file is a permanent record that somebody once found the command; "
                + "the last one out deletes it");
    }

    @Test
    void whoAskedSurvivesARestart()
    {
        FreyaPreferences.setEnabled(SOMEONE, true);
        FreyaPreferences.clear();

        assertEquals(1, FreyaPreferences.loadAll(), "the preference is the one thing that persists");
        assertTrue(FreyaPreferences.isEnabled(SOMEONE), "and it is the same player");
        assertFalse(FreyaPreferences.isEnabled(SOMEONE_ELSE), "and only that player");
    }

    @Test
    void anUnreadableIdIsSkippedRatherThanLosingEverybodyElse() throws Exception
    {
        FreyaPreferences.setEnabled(SOMEONE, true);
        FreyaPreferences.setEnabled(SOMEONE_ELSE, true);
        final File file = DataLayout.freyaFile();
        final String mangled = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)
            .replace(SOMEONE.toString(), "not-a-uuid");
        Files.write(file.toPath(), mangled.getBytes(StandardCharsets.UTF_8));
        FreyaPreferences.clear();

        assertEquals(1, FreyaPreferences.loadAll(),
            "one hand-edited line should cost that player their cat, not everybody theirs");
        assertTrue(FreyaPreferences.isEnabled(SOMEONE_ELSE), "the readable entry still loads");
    }

    @Test
    void turningHerOnTwiceIsNotAChange()
    {
        assertTrue(FreyaPreferences.setEnabled(SOMEONE, true), "the first time is a change");
        assertFalse(FreyaPreferences.setEnabled(SOMEONE, true),
            "the second is not, so it must not rewrite the file -- a command somebody is "
                + "leaning on should not mean a disk write per keypress");
    }
}
