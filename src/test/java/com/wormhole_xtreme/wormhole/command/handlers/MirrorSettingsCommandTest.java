package com.wormhole_xtreme.wormhole.command.handlers;

import static com.wormhole_xtreme.wormhole.utils.ChatMatchers.plainContains;
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
import com.wormhole_xtreme.wormhole.model.window.BlockPlace;
import com.wormhole_xtreme.wormhole.model.window.Captures;
import com.wormhole_xtreme.wormhole.model.window.Place;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorAccess;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorManager;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorText;
import com.wormhole_xtreme.wormhole.utils.ChatText;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorYamlManager;
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
        MirrorManager.add(new QuantumMirror("museum", new BlockPlace("world", 1, 64, 1), null));
    }

    @AfterEach
    void tearDown() throws Exception
    {
        MirrorManager.clear();
        Captures.clear();
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
        MirrorManager.add(new QuantumMirror("hub", new BlockPlace("world", 5, 64, 5), null));
        run("mirror", "set", "museum", "-start", "hub");

        MirrorManager.clear();
        MirrorYamlManager.loadAll();

        assertEquals("hub", MirrorManager.byName("museum").start());
    }

    /** A list of ordinary mirrors says nothing extra, and one with a start says so. */
    @Test
    void listsOnlyTheSettingsThatAreNotTheDefault()
    {
        MirrorManager.add(new QuantumMirror("hub", new BlockPlace("world", 5, 64, 5), null));
        run("mirror", "list");
        verify(sender, never()).sendMessage(contains("(starts on"));

        run("mirror", "set", "museum", "-start", "hub");
        run("mirror", "list");

        verify(sender, atLeastOnce()).sendMessage(contains("(starts on hub)"));
    }

    /**
     * A list row opens with the header, as every other line this command sends does (#438).
     *
     * <p>The rows were the one thing sent without it, so each had to carry its own body colour,
     * and a row that started with an uncoloured indent rendered it in the default white.
     */
    @Test
    void everyListRowOpensWithTheHeader()
    {
        run("mirror", "list");

        final ArgumentCaptor<String> said = ArgumentCaptor.forClass(String.class);
        verify(sender, atLeastOnce()).sendMessage(said.capture());
        final List<String> rows = said.getAllValues().stream()
            .filter(line -> line.contains("museum")).toList();
        assertFalse(rows.isEmpty(), "the one mirror should have been listed");
        for (final String row : rows)
        {
            assertTrue(row.startsWith(ChatText.HEADER + "  " + MirrorText.NAME_COLOUR),
                "a row opens with the header, then its indent and the mirror's name: " + row);
        }
    }

    @Test
    void doesNotNeedToBeRunInGame()
    {
        final CommandSender console = mock(CommandSender.class);
        when(console.isOp()).thenReturn(true);

        MirrorManager.add(new QuantumMirror("hub", new BlockPlace("world", 5, 64, 5), null));

        assertTrue(new MirrorCommand().execute(console,
            new String[] { "mirror", "set", "museum", "-start", "hub" }));

        assertEquals("hub", MirrorManager.byName("museum").start(),
            "a named mirror's setting does not depend on where anybody is standing");
    }

    /**
     * {@code -follow on} is saved and survives a restart, and with no capture of the room yet the
     * banner is left alone and the reply says why (#613).
     */
    @Test
    void followIsSavedAndLeavesTheBannerAloneWithoutACapture()
    {
        pointMuseum();
        run("mirror", "set", "museum", "-follow", "on");

        verify(sender, atLeastOnce()).sendMessage(plainContains("'museum's banner follows its own room now"));
        verify(sender, atLeastOnce()).sendMessage(plainContains("There is no capture of its own room to read yet"));
        MirrorManager.clear();
        MirrorYamlManager.loadAll();
        assertTrue(MirrorManager.byName("museum").follows());

        run("mirror", "set", "museum", "-follow", "off");
        assertFalse(MirrorManager.byName("museum").follows());
    }

    /** A follow with no on or off is a usage line, not a guess. */
    @Test
    void followWithoutOnOrOffChangesNothing()
    {
        run("mirror", "set", "museum", "-follow");
        run("mirror", "set", "museum", "-follow", "maybe");

        assertFalse(MirrorManager.byName("museum").follows());
        verify(sender, atLeastOnce()).sendMessage(plainContains("-follow <on|off>"));
    }

    /**
     * A new start re-stamps a following banner, and leaves an ordinary one alone.
     *
     * <p>The waiting reply is the sign a re-stamp was tried: the start's room has no capture here.
     */
    @Test
    void aNewStartReStampsOnlyAFollowingBanner()
    {
        pointMuseum();
        MirrorManager.add(new QuantumMirror("hub", new BlockPlace("world", 5, 64, 5),
            new Place("world", 5.5, 62, 6.5, 0f, 0f)));
        run("mirror", "set", "museum", "-start", "hub");
        verify(sender, never()).sendMessage(plainContains("There is no capture"));

        MirrorManager.add(MirrorManager.byName("museum").withFollows(true));
        run("mirror", "set", "museum", "-start", "hub");
        verify(sender, atLeastOnce()).sendMessage(plainContains("There is no capture of 'hub's room to read yet"));
    }

    /** The list says which banners follow. */
    @Test
    void listsAFollowingBanner()
    {
        MirrorManager.add(MirrorManager.byName("museum").withFollows(true));
        run("mirror", "list");

        verify(sender, atLeastOnce()).sendMessage(contains("(banner follows)"));
    }

    /**
     * {@code -group}, {@code -hidden} and {@code -private} are saved and survive a restart (#280).
     *
     * <p>Through the real file: a mirror that forgot its group on a restart would quietly start
     * listing every public mirror again, which is the museum wing showing the survival hub.
     */
    @Test
    void groupHiddenAndPrivateSurviveARestart()
    {
        run("mirror", "set", "museum", "-group", "Wing");
        run("mirror", "set", "museum", "-hidden", "on");
        run("mirror", "set", "museum", "-private", "on");

        MirrorManager.clear();
        MirrorYamlManager.loadAll();

        final QuantumMirror museum = MirrorManager.byName("museum");
        assertEquals("Wing", museum.access().group());
        assertTrue(museum.access().hidden(), "hidden should be read back");
        assertTrue(museum.access().restricted(), "private should be read back");
        verify(sender, atLeastOnce()).sendMessage(plainContains("'museum' is in the group 'Wing' now"));
        verify(sender, atLeastOnce()).sendMessage(plainContains("listed only for players with wormhole.mirror.private"));
    }

    /** {@code -none} takes a mirror out of its group, and off turns each flag back off. */
    @Test
    void noneAndOffPutAMirrorBackOnThePublicNetwork()
    {
        MirrorManager.add(MirrorManager.byName("museum").withAccess(new MirrorAccess("wing", true, true)));

        run("mirror", "set", "museum", "-group", "-none");
        run("mirror", "set", "museum", "-hidden", "off");
        run("mirror", "set", "museum", "-private", "off");

        assertEquals(MirrorAccess.OPEN, MirrorManager.byName("museum").access());
    }

    /**
     * A group named with a dash is refused, since a dash marks an option.
     *
     * <p>Otherwise {@code -group -hidden} would put the mirror in a group called "-hidden".
     */
    @Test
    void aGroupStartingWithADashIsRefused()
    {
        run("mirror", "set", "museum", "-group", "-hidden");

        assertEquals(MirrorAccess.OPEN, MirrorManager.byName("museum").access());
        verify(sender, atLeastOnce()).sendMessage(plainContains("A group's name cannot start with -"));
    }

    /** A flag with no on or off, or a group with no name, is a usage line, not a guess. */
    @Test
    void aFlagWithoutOnOrOffChangesNothing()
    {
        run("mirror", "set", "museum", "-hidden");
        run("mirror", "set", "museum", "-private", "maybe");
        run("mirror", "set", "museum", "-group");

        assertEquals(MirrorAccess.OPEN, MirrorManager.byName("museum").access());
        verify(sender, atLeastOnce()).sendMessage(plainContains("-hidden <on|off>"));
        verify(sender, atLeastOnce()).sendMessage(plainContains("-private <on|off>"));
        verify(sender, atLeastOnce()).sendMessage(plainContains("-group <group|-none>"));
    }

    /** The list says which group a mirror is in, and which are hidden or private. */
    @Test
    void listsTheGroupAndTheFlags()
    {
        MirrorManager.add(MirrorManager.byName("museum").withAccess(new MirrorAccess("wing", true, true)));
        run("mirror", "list");

        verify(sender, atLeastOnce()).sendMessage(contains("(group wing) (hidden) (private)"));
    }

    /** Gives museum its room, so it has a capture key to wait on. */
    private static void pointMuseum()
    {
        MirrorManager.add(MirrorManager.byName("museum").withDestination(new Place("world", 1.5, 62, 2.5, 0f, 0f)));
    }

    private boolean run(final String... args)
    {
        return new MirrorCommand().execute(sender, args);
    }
}
