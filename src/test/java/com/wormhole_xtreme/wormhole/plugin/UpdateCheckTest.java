package com.wormhole_xtreme.wormhole.plugin;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;

/**
 * The startup update check (#461), with canned answers in place of the network.
 *
 * <p>The bugs it guards against: versions compared as strings, which puts 1.10 before 1.9 and
 * would stop announcing anything after 1.9; a development build, newer than any release, being
 * told to "update" to an older one; announcing a release built only for another Minecraft
 * version, or a beta; and Modrinth being down (its listing answered 404 while awaiting review)
 * silencing the check, when GitHub could still answer.
 */
class UpdateCheckTest
{
    private static final String MINECRAFT = "1.21.8";

    private static final String GITHUB_PAGE = "https://github.com/khanjal/Wormhole-X-Treme/releases/tag/v1.9.0";

    /** One Modrinth version entry. */
    private static String entry(final String number, final String type, final String... games)
    {
        return "{\"version_number\":\"" + number + "\",\"version_type\":\"" + type + "\",\"game_versions\":[\""
            + String.join("\",\"", games) + "\"]}";
    }

    private static String modrinth(final String... entries)
    {
        return "[" + String.join(",", entries) + "]";
    }

    private static String gitHub(final String tag)
    {
        return "{\"tag_name\":\"" + tag + "\",\"html_url\":\"" + GITHUB_PAGE + "\",\"draft\":false}";
    }

    /** Answers from canned bodies, a null body standing for a failed request, and notes each URL asked. */
    private static final class Canned implements UpdateCheck.Fetcher
    {
        final List<String> asked = new ArrayList<>();

        private final String modrinthBody;

        private final String gitHubBody;

        Canned(final String modrinthBody, final String gitHubBody)
        {
            this.modrinthBody = modrinthBody;
            this.gitHubBody = gitHubBody;
        }

        @Override
        public String get(final String url) throws IOException
        {
            asked.add(url);
            final String body = UpdateCheck.MODRINTH_API.equals(url) ? modrinthBody : gitHubBody;
            if (body == null)
            {
                throw new IOException("HTTP 404 from " + url);
            }
            return body;
        }
    }

    /** Compared as strings, "1.10.0" sorts before "1.9.0", and nothing after 1.9 would ever be announced. */
    @Test
    void aTenthMinorVersionIsNewerThanANinth()
    {
        assertTrue(UpdateCheck.isNewer("1.10.0", "1.9.0"), "10 is more than 9, whatever the characters say");
        assertFalse(UpdateCheck.isNewer("1.9.0", "1.10.0"));
    }

    /** GitHub tags carry a v that the plugin's own version does not. */
    @Test
    void aLeadingVIsIgnored()
    {
        assertEquals(OptionalInt.of(0), UpdateCheck.compareVersions("v1.9.0", "1.9.0"));
        assertEquals(OptionalInt.of(0), UpdateCheck.compareVersions("V1.9.0", "1.9.0"));
        assertTrue(UpdateCheck.isNewer("v1.10.0", "1.9.0"));
    }

    /** 1.9 and 1.9.0 are one release, so neither is announced over the other. */
    @Test
    void aMissingPartCountsAsZero()
    {
        assertEquals(OptionalInt.of(0), UpdateCheck.compareVersions("1.9", "1.9.0"));
        assertTrue(UpdateCheck.isNewer("1.9.1", "1.9"), "a missing part is 0, not the end of the comparison");
    }

    /** A snapshot of 1.9.0 comes before 1.9.0 itself, so 1.9.0's release is announced to it. */
    @Test
    void aSnapshotIsOlderThanItsRelease()
    {
        assertTrue(UpdateCheck.isNewer("1.9.0", "1.9.0-SNAPSHOT"));
        assertTrue(UpdateCheck.isNewer("1.9.0", "1.9.0+build.5"));
        assertFalse(UpdateCheck.isNewer("1.9.0-SNAPSHOT", "1.9.0"));
        assertTrue(UpdateCheck.isNewer("1.9.1-SNAPSHOT", "1.9.0"), "the numbers come first; the qualifier only breaks a tie");
    }

    /** A version that cannot be read is never announced, and never makes another look older. */
    @Test
    void anUnreadableVersionIsNeverNewer()
    {
        assertEquals(OptionalInt.empty(), UpdateCheck.compareVersions("latest", "1.9.0"));
        assertFalse(UpdateCheck.isNewer("latest", "1.9.0"));
        assertFalse(UpdateCheck.isNewer("2.0.0", "unknown"));
        assertFalse(UpdateCheck.isNewer("1..2", "1.0"));
        assertFalse(UpdateCheck.isNewer("", "1.0"));
        assertEquals(Optional.empty(),
            UpdateCheck.findNewer(new Canned(null, gitHub("nightly")), "1.8.1", MINECRAFT));
    }

    /** An older plugin is told about the newest release built for its Minecraft version, and where to find it. */
    @Test
    void anOlderPluginIsToldOfTheNewerRelease()
    {
        final Canned fetcher = new Canned(modrinth(entry("1.9.0", "release", MINECRAFT)), gitHub("v1.9.0"));

        assertEquals(Optional.of(new UpdateCheck.Release("1.9.0", UpdateCheck.MODRINTH_PAGE)),
            UpdateCheck.findNewer(fetcher, "1.8.1", MINECRAFT));
    }

    /** A development build, newer than any release, is not told to go back to one; nor is the release itself. */
    @Test
    void aPluginAtOrPastTheLatestReleaseHearsNothing()
    {
        final String answer = modrinth(entry("1.9.0", "release", MINECRAFT));

        assertEquals(Optional.empty(), UpdateCheck.findNewer(new Canned(answer, null), "1.10.0-SNAPSHOT", MINECRAFT));
        assertEquals(Optional.empty(), UpdateCheck.findNewer(new Canned(answer, null), "1.9.0", MINECRAFT));
        assertEquals(Optional.empty(), UpdateCheck.findNewer(new Canned(answer, null), "v1.9", MINECRAFT));
    }

    /** A newer release not built for this server's Minecraft version is passed over for one that is. */
    @Test
    void aReleaseForAnotherMinecraftVersionIsSkipped()
    {
        final String answer = modrinth(entry("2.0.0", "release", "1.21.9", "26.2"), entry("1.9.0", "release", "1.21.4", MINECRAFT));

        assertEquals("1.9.0", UpdateCheck.latestFromModrinth(answer, MINECRAFT));
        assertEquals("2.0.0", UpdateCheck.latestFromModrinth(answer, "26.2"));
    }

    /** A beta or alpha is not something to tell a server to update to. */
    @Test
    void aBetaIsSkipped()
    {
        final String answer = modrinth(entry("2.0.0", "beta", MINECRAFT), entry("1.9.5", "alpha", MINECRAFT),
            entry("1.9.0", "release", MINECRAFT));

        assertEquals("1.9.0", UpdateCheck.latestFromModrinth(answer, MINECRAFT));
    }

    /** The highest release is chosen by number, wherever it sits in Modrinth's list. */
    @Test
    void theHighestMatchingReleaseIsChosenInAnyOrder()
    {
        final String a = entry("1.9.0", "release", MINECRAFT);
        final String b = entry("1.10.0", "release", MINECRAFT);
        final String c = entry("1.9.5", "release", MINECRAFT);

        assertEquals("1.10.0", UpdateCheck.latestFromModrinth(modrinth(a, b, c), MINECRAFT));
        assertEquals("1.10.0", UpdateCheck.latestFromModrinth(modrinth(c, b, a), MINECRAFT));
        assertEquals("1.10.0", UpdateCheck.latestFromModrinth(modrinth(b, a, c), MINECRAFT));
    }

    /** An entry whose number cannot be read is passed over, not allowed to hide the readable ones. */
    @Test
    void anUnreadableModrinthNumberIsPassedOver()
    {
        final String answer = modrinth(entry("next", "release", MINECRAFT), entry("1.9.0", "release", MINECRAFT));

        assertEquals("1.9.0", UpdateCheck.latestFromModrinth(answer, MINECRAFT));
    }

    /** Modrinth answering 404, as it does while the listing awaits review, leaves GitHub to answer. */
    @Test
    void aFailedModrinthFallsBackToGitHub()
    {
        final Canned fetcher = new Canned(null, gitHub("v1.9.0"));

        assertEquals(Optional.of(new UpdateCheck.Release("v1.9.0", GITHUB_PAGE)),
            UpdateCheck.findNewer(fetcher, "1.8.1", MINECRAFT));
        assertEquals(List.of(UpdateCheck.MODRINTH_API, UpdateCheck.GITHUB_API), fetcher.asked);
    }

    /** A Modrinth body that is not a list of versions counts as a failure too. */
    @Test
    void aMalformedModrinthAnswerFallsBackToGitHub()
    {
        assertEquals(Optional.of(new UpdateCheck.Release("v1.9.0", GITHUB_PAGE)),
            UpdateCheck.findNewer(new Canned("<html>Not Found</html>", gitHub("v1.9.0")), "1.8.1", MINECRAFT));
        assertEquals(Optional.of(new UpdateCheck.Release("v1.9.0", GITHUB_PAGE)),
            UpdateCheck.findNewer(new Canned("{\"error\":\"not_found\"}", gitHub("v1.9.0")), "1.8.1", MINECRAFT));
    }

    /**
     * Modrinth saying there is nothing newer for this Minecraft version is the answer: GitHub's
     * latest may be built for another one, so it is not asked.
     */
    @Test
    void aModrinthAnswerWithNothingNewerIsFinal()
    {
        final Canned current = new Canned(modrinth(entry("1.8.1", "release", MINECRAFT)), gitHub("v9.9.9"));
        final Canned noneForThisVersion = new Canned(modrinth(entry("9.9.9", "release", "26.2")), gitHub("v9.9.9"));

        assertEquals(Optional.empty(), UpdateCheck.findNewer(current, "1.8.1", MINECRAFT));
        assertEquals(List.of(UpdateCheck.MODRINTH_API), current.asked, "GitHub was asked after Modrinth answered");
        assertEquals(Optional.empty(), UpdateCheck.findNewer(noneForThisVersion, "1.8.1", MINECRAFT));
        assertEquals(List.of(UpdateCheck.MODRINTH_API), noneForThisVersion.asked);
    }

    /** Neither source answering, or answering nonsense, is quiet: no notification and nothing thrown. */
    @Test
    void bothFailingSaysNothing()
    {
        final Canned down = new Canned(null, null);

        assertEquals(Optional.empty(), UpdateCheck.findNewer(down, "1.8.1", MINECRAFT));
        assertEquals(List.of(UpdateCheck.MODRINTH_API, UpdateCheck.GITHUB_API), down.asked, "GitHub was tried before giving up");
        assertEquals(Optional.empty(), UpdateCheck.findNewer(new Canned("[", "{"), "1.8.1", MINECRAFT));
        assertEquals(Optional.empty(), UpdateCheck.findNewer(new Canned(null, "[]"), "1.8.1", MINECRAFT));
        assertNull(UpdateCheck.latestFromGitHub("{\"message\":\"Not Found\"}"));
    }

    /** Bukkit's version carries the API revision after the Minecraft one, which Modrinth does not list. */
    @Test
    void theMinecraftVersionIsCutFromBukkits()
    {
        assertEquals("1.21.8", UpdateCheck.minecraftVersion("1.21.8-R0.1-SNAPSHOT"));
        assertEquals("26.2", UpdateCheck.minecraftVersion("26.2"));
    }

    /** Only players with wormhole.update.notify are told on joining. */
    @Test
    void onlyPlayersWhoMayBeToldAreTold()
    {
        final UpdateCheck listener = new UpdateCheck("a newer release");
        final Player op = joining(true);
        final Player guest = joining(false);

        listener.onPlayerJoin(join(op));
        listener.onPlayerJoin(join(guest));

        verify(op).sendMessage("a newer release");
        verify(guest, never()).sendMessage(anyString());
    }

    /** Off, it asks the scheduler for nothing, so no request is ever made; on, it goes off the main thread. */
    @Test
    void startsOnlyWhenTheSwitchAllowsIt() throws Exception
    {
        PluginTestSupport.install();
        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        PluginTestSupport.scheduler(scheduler);
        final JavaPlugin plugin = plugin(mock(PluginManager.class));
        ConfigTestSupport.loadDefaults();
        try
        {
            ConfigTestSupport.set(ConfigManager.ConfigKeys.UPDATE_CHECK, false);
            UpdateCheck.startIfConfigured(plugin);
            verify(scheduler, never()).runTaskAsynchronously(any(Plugin.class), any(Runnable.class));

            ConfigTestSupport.set(ConfigManager.ConfigKeys.UPDATE_CHECK, true);
            UpdateCheck.startIfConfigured(plugin);
            verify(scheduler).runTaskAsynchronously(eq(plugin), any(Runnable.class));
        }
        finally
        {
            ConfigTestSupport.clear();
            PluginTestSupport.scheduler(null);
            PluginTestSupport.remove();
        }
    }

    /** A start that fails, here with no scheduler at all, is logged and never thrown into onEnable. */
    @Test
    void aFailedStartIsNotThrown() throws Exception
    {
        PluginTestSupport.install();
        PluginTestSupport.scheduler(null);
        ConfigTestSupport.clear();
        try
        {
            assertDoesNotThrow(() -> UpdateCheck.startIfConfigured(plugin(mock(PluginManager.class))));
        }
        finally
        {
            PluginTestSupport.remove();
        }
    }

    /** A newer release found off the main thread is announced back on it, where the join listener is registered. */
    @Test
    void aNewerReleaseRegistersTheJoinMessageOnTheMainThread() throws Exception
    {
        PluginTestSupport.install();
        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        PluginTestSupport.scheduler(scheduler);
        final PluginManager plugins = mock(PluginManager.class);
        final JavaPlugin plugin = plugin(plugins);
        try
        {
            UpdateCheck.check(plugin, new Canned(null, gitHub("v1.9.0")), "1.8.1", MINECRAFT);
            final ArgumentCaptor<Runnable> onMain = ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler).runTask(eq(plugin), onMain.capture());
            verify(plugins, never()).registerEvents(any(Listener.class), any(Plugin.class));

            onMain.getValue().run();
            final ArgumentCaptor<Listener> registered = ArgumentCaptor.forClass(Listener.class);
            verify(plugins).registerEvents(registered.capture(), eq(plugin));
            final Player op = joining(true);
            ((UpdateCheck) registered.getValue()).onPlayerJoin(join(op));
            final ArgumentCaptor<String> said = ArgumentCaptor.forClass(String.class);
            verify(op).sendMessage(said.capture());
            assertTrue(said.getValue().contains("v1.9.0") && said.getValue().contains(GITHUB_PAGE)
                && said.getValue().contains("1.8.1"), "the message names the release, where to get it, and what is running: "
                + said.getValue());
        }
        finally
        {
            PluginTestSupport.scheduler(null);
            PluginTestSupport.remove();
        }
    }

    /** Nothing newer, nothing goes back to the main thread. */
    @Test
    void nothingNewerSchedulesNothing() throws Exception
    {
        PluginTestSupport.install();
        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        PluginTestSupport.scheduler(scheduler);
        try
        {
            UpdateCheck.check(plugin(mock(PluginManager.class)), new Canned(null, gitHub("v1.8.1")), "1.8.1", MINECRAFT);
            verify(scheduler, never()).runTask(any(Plugin.class), any(Runnable.class));
        }
        finally
        {
            PluginTestSupport.scheduler(null);
            PluginTestSupport.remove();
        }
    }

    private static JavaPlugin plugin(final PluginManager plugins)
    {
        final JavaPlugin plugin = mock(JavaPlugin.class);
        final Server server = mock(Server.class);
        when(server.getBukkitVersion()).thenReturn("1.21.8-R0.1-SNAPSHOT");
        when(server.getPluginManager()).thenReturn(plugins);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getDescription()).thenReturn(new PluginDescriptionFile("WormholeXTreme", "1.8.1", "Main"));
        return plugin;
    }

    private static Player joining(final boolean mayBeTold)
    {
        final Player player = mock(Player.class);
        when(player.hasPermission(UpdateCheck.NOTIFY_PERMISSION)).thenReturn(mayBeTold);
        return player;
    }

    private static PlayerJoinEvent join(final Player player)
    {
        final PlayerJoinEvent event = mock(PlayerJoinEvent.class);
        when(event.getPlayer()).thenReturn(player);
        return event;
    }
}
