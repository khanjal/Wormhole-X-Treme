package com.wormhole_xtreme.wormhole.command.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.List;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorBlock;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorManager;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorText;
import com.wormhole_xtreme.wormhole.model.mirror.QuantumMirror;

/**
 * {@code mirror set start} and {@code list} -- a setting, and how it is shown.
 *
 * <p>Neither touches a block, which is the point of testing them apart from the rest: they are
 * pure registry edits, and what is worth pinning down is that they persist, that they name a
 * mirror nobody has, and that {@code list} says which mirrors are not ordinary.
 */
class MirrorSettingsCommandTest
{
    /** Where the saves go, so no test writes a mirror file into the repository. */
    @TempDir
    File dataFolder;

    private Player sender;

    @BeforeEach
    void setUp() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        PluginTestSupport.install(plugin);
        ConfigTestSupport.clear();
        MirrorManager.clear();

        sender = mock(Player.class);
        when(sender.isOp()).thenReturn(true);
        MirrorManager.add(new QuantumMirror("museum", new MirrorBlock("world", 1, 64, 1), null));
    }

    @AfterEach
    void tearDown() throws Exception
    {
        MirrorManager.clear();
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    @Test
    void namesTheUnknownMirrorRatherThanTheSetting()
    {
        run("mirror", "set", "nosuch", "-start", "-none");

        verify(sender, atLeastOnce())
            .sendMessage(contains("no mirror called '" + MirrorText.NAME_COLOUR + "nosuch"));
    }

    /**
     * The setting survives being written and read back.
     *
     * <p>Through the real file, because that is where a setting an operator chose actually has
     * to last -- one that only lives in memory is one they set again after every restart
     * without understanding why.
     */
    @Test
    void theSettingSurvivesARestart()
    {
        MirrorManager.add(new QuantumMirror("hub", new MirrorBlock("world", 5, 64, 5), null));
        run("mirror", "set", "museum", "-start", "hub");

        MirrorManager.clear();
        com.wormhole_xtreme.wormhole.model.mirror.MirrorYamlManager.loadAll();

        assertEquals("hub", MirrorManager.byName("museum").start());
    }

    /** A list of ordinary mirrors says nothing extra, and one with a start says so. */
    @Test
    void listsOnlyTheSettingsThatAreNotTheDefault()
    {
        MirrorManager.add(new QuantumMirror("hub", new MirrorBlock("world", 5, 64, 5), null));
        run("mirror", "list");
        verify(sender, never()).sendMessage(contains("(starts on"));

        run("mirror", "set", "museum", "-start", "hub");
        run("mirror", "list");

        verify(sender, atLeastOnce()).sendMessage(contains("(starts on hub)"));
    }

    /**
     * A list row carries its colour from its first character.
     *
     * <p>These rows are the one thing this command sends without the header, so nothing else
     * puts them in the body colour. A row that starts with an uncoloured indent renders those
     * first characters in the default white -- which is what the code did while its own comment
     * claimed otherwise.
     */
    @Test
    void everyListRowStartsInTheBodyColour()
    {
        run("mirror", "list");

        final ArgumentCaptor<String> said = ArgumentCaptor.forClass(String.class);
        verify(sender, atLeastOnce()).sendMessage(said.capture());
        final List<String> rows = said.getAllValues().stream()
            .filter(line -> line.contains("museum")).toList();
        assertFalse(rows.isEmpty(), "the one mirror should have been listed");
        for (final String row : rows)
        {
            assertTrue(row.startsWith(MirrorText.BODY_COLOUR),
                "a row has to open in the body colour, indent included: " + row);
        }
    }

    @Test
    void doesNotNeedToBeRunInGame()
    {
        final CommandSender console = mock(CommandSender.class);
        when(console.isOp()).thenReturn(true);

        MirrorManager.add(new QuantumMirror("hub", new MirrorBlock("world", 5, 64, 5), null));

        assertTrue(new MirrorCommand().execute(console,
            new String[] { "mirror", "set", "museum", "-start", "hub" }));

        assertEquals("hub", MirrorManager.byName("museum").start(),
            "a named mirror's setting does not depend on where anybody is standing");
    }

    private boolean run(final String... args)
    {
        return new MirrorCommand().execute(sender, args);
    }
}
