package com.wormhole_xtreme.wormhole.command.handlers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.command.SubCommands;
import com.wormhole_xtreme.wormhole.model.freya.FreyaCompanion;
import com.wormhole_xtreme.wormhole.model.freya.FreyaPreferences;

/**
 * The hidden command stays hidden, and stays a toggle.
 *
 * <p>Two properties, and losing either one turns a private thing into a public one.
 *
 * <p>It must not appear in tab completion or in the help list. The registry drives all three of
 * dispatch, completion and help from one place precisely so they cannot drift apart -- which
 * normally means anything registered shows up everywhere, and here is the one case where that
 * would be wrong. {@code SubCommandsTest.whatIsAdvertisedIsTheSixNamesAndNothingElse} guards
 * the same boundary from the other side.
 *
 * <p>And it must stay a toggle rather than a spawner. A hidden command that spawns a mob every
 * time it is typed is a way to fill a world with cats; one that flips a bit is an easter egg.
 * That difference is one line in {@code wanted}, and nothing about it is obvious from reading
 * the command run once.
 *
 * <p>No world is mocked here on purpose. {@code FreyaCompanion.spawnFor} answers null for a
 * player with no location rather than throwing, so the preference half of the command can be
 * pinned without standing up a live world -- the entity half is the thin part, and it is the
 * part only a real server can exercise.
 */
class FreyaCommandTest
{
    private static final UUID PLAYER_ID = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    @TempDir
    File dataFolder;

    private FreyaCommand command;

    @BeforeEach
    void setUp() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        PluginTestSupport.install(plugin);
        FreyaPreferences.clear();
        command = new FreyaCommand();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        FreyaPreferences.clear();
        com.wormhole_xtreme.wormhole.model.freya.FreyaCompanion.forgetAll();
        PluginTestSupport.remove();
    }

    /**
     * A player who holds the node.
     *
     * <p>{@code getUniqueId} is stubbed deliberately: an unstubbed Mockito Player answers null
     * for it, and every path through this command keys on it. That exact omission has already
     * cost this suite once.
     *
     * @return the mock
     */
    private static Player player()
    {
        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(PLAYER_ID);
        when(player.hasPermission(FreyaCommand.PERMISSION)).thenReturn(true);
        return player;
    }

    @Test
    void theCommandIsNeverOfferedByTabCompletion()
    {
        assertFalse(SubCommands.namesMatching("").contains("freya"),
            "an easter egg that tab-completes is not an easter egg");
        assertFalse(SubCommands.namesMatching("fre").contains("freya"),
            "not even to somebody who has typed most of it -- the completer must not confirm "
                + "a guess");
    }

    @Test
    void theCommandStillDispatches()
    {
        assertNotNull(SubCommands.find("freya"),
            "hidden means unadvertised, not unreachable; a player who knows the word must "
                + "still get a cat");
    }

    @Test
    void theCommandIsNotInTheHelpList()
    {
        assertFalse(SubCommands.nameList(false).contains("freya"), "not in the operator's list");
        assertFalse(SubCommands.nameList(true).contains("freya"), "nor in a player's");
    }

    @Test
    void typingTheWordTwiceTurnsHerOffAgain()
    {
        final Player player = player();

        command.execute(player, new String[] { "freya" });
        assertTrue(FreyaPreferences.isEnabled(PLAYER_ID), "the first time asks for her");

        command.execute(player, new String[] { "freya" });
        assertFalse(FreyaPreferences.isEnabled(PLAYER_ID),
            "the second lets her go -- a bare word that only ever spawns is how a hidden "
                + "command becomes a way to flood a world");
    }

    @Test
    void onIsIdempotentWhereTheBareWordWouldToggle()
    {
        final Player player = player();

        command.execute(player, new String[] { "freya", "on" });
        command.execute(player, new String[] { "freya", "on" });

        assertTrue(FreyaPreferences.isEnabled(PLAYER_ID),
            "'on' said twice means on, not on and then off; the explicit words exist so "
                + "somebody unsure of the current state can be certain of the next one");
    }

    @Test
    void offTurnsHerOffWhicheverWayRound()
    {
        final Player player = player();

        command.execute(player, new String[] { "freya", "off" });
        assertFalse(FreyaPreferences.isEnabled(PLAYER_ID), "off from off stays off");

        command.execute(player, new String[] { "freya", "on" });
        command.execute(player, new String[] { "freya", "off" });
        assertFalse(FreyaPreferences.isEnabled(PLAYER_ID), "and off from on turns her off");
    }

    @Test
    void aPlayerDeniedTheNodeGetsNothingStored()
    {
        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(PLAYER_ID);
        when(player.hasPermission(FreyaCommand.PERMISSION)).thenReturn(false);

        command.execute(player, new String[] { "freya" });

        assertFalse(FreyaPreferences.isEnabled(PLAYER_ID),
            "a refused command must not leave a preference behind that a later grant would "
                + "silently honour");
    }

    @Test
    void consoleIsRefusedRatherThanStoringAPreferenceForNobody()
    {
        final CommandSender console = mock(CommandSender.class);

        assertTrue(command.execute(console, new String[] { "freya" }),
            "the command was understood, it simply has nobody to follow");
        assertFalse(dataFolder.toPath().resolve("data").resolve("freya.yml").toFile().exists(),
            "there is no player id to store, so nothing should have been written");
    }

    @Test
    void summoningHerRemembersHerYears()
    {
        final Player player = player();
        final World world = mock(World.class);
        when(player.getLocation()).thenReturn(new Location(world, 0.0, 64.0, 0.0));
        final Cat cat = mock(Cat.class);
        when(cat.isValid()).thenReturn(true);
        when(world.spawn(any(Location.class), eq(Cat.class))).thenReturn(cat);

        command.execute(player, new String[] { "freya", "on" });

        verify(player).sendMessage(contains(FreyaCompanion.YEARS));
    }
}

