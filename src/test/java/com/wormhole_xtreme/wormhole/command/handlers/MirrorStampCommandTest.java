package com.wormhole_xtreme.wormhole.command.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorBlock;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorManager;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorPoint;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorPresetRegistry;
import com.wormhole_xtreme.wormhole.model.mirror.QuantumMirror;

/**
 * {@code /wormhole mirror stamp}, in its own class because it needs a world to look at.
 *
 * <p>Two paths, and they fail differently. Given a preset name it never leaves the banner's own
 * world; given none it reads the destination, which means a world that is not loaded is a real
 * and reachable outcome rather than a hypothetical one.
 */
class MirrorStampCommandTest
{
    /** Where the command's saves go, so nothing lands in the repository. */
    @TempDir
    File dataFolder;

    /** The presets folder, for the same reason -- load() would write into the plugin folder. */
    @TempDir
    File presets;

    private Player sender;
    private World bannerWorld;
    private World farWorld;
    private Banner banner;

    @BeforeEach
    void setUp() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        PluginTestSupport.install(plugin);
        ConfigTestSupport.clear();
        MirrorManager.clear();
        MirrorPresetRegistry.load(presets);

        sender = mock(Player.class);
        when(sender.isOp()).thenReturn(true);

        banner = mock(Banner.class);
        final Block bannerBlock = mock(Block.class);
        when(bannerBlock.getType()).thenReturn(Material.WHITE_WALL_BANNER);
        when(bannerBlock.getState()).thenReturn(banner);

        bannerWorld = mock(World.class);
        when(bannerWorld.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(bannerBlock);

        farWorld = mock(World.class);
        // An ordinary overworld's floor and ceiling. The sampler clamps to them, so a mock
        // that answers 0 to both describes a world of no height and gets sampled nowhere.
        when(farWorld.getMinHeight()).thenReturn(-64);
        when(farWorld.getMaxHeight()).thenReturn(320);
        when(farWorld.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(invocation ->
        {
            final Block block = mock(Block.class);
            when(block.getType()).thenReturn(Material.SAND);
            return block;
        });
    }

    @AfterEach
    void tearDown() throws Exception
    {
        MirrorManager.clear();
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    @Test
    @DisplayName("a named look is applied without looking at the destination at all")
    void appliesANamedPreset()
    {
        pointedMirror();
        try (final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(bannerWorld);
            bukkit.when(() -> Bukkit.getWorld("far")).thenReturn(null);

            assertTrue(run("mirror", "stamp", "museum", "nether"));
        }
        verify(banner).setBaseColor(DyeColor.RED);
        verify(banner).update(anyBoolean());
        verify(sender, atLeastOnce()).sendMessage(contains("looks like nether"));
    }

    @Test
    @DisplayName("a look nobody has lists the ones that exist")
    void refusesAnUnknownPreset()
    {
        pointedMirror();
        try (final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(bannerWorld);

            run("mirror", "stamp", "museum", "chartreuse");
        }
        verify(banner, never()).update(anyBoolean());
        verify(sender, atLeastOnce()).sendMessage(contains("no look called 'chartreuse'"));
        verify(sender, atLeastOnce()).sendMessage(contains("nether"));
    }

    @Test
    @DisplayName("with no look named it reads the far side and says what it found")
    void readsTheDestination()
    {
        pointedMirror();
        try (final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(bannerWorld);
            bukkit.when(() -> Bukkit.getWorld("far")).thenReturn(farWorld);

            assertTrue(run("mirror", "stamp", "museum"));
        }
        // Sand everywhere: solid enough to read as enclosed, so the sand's own colour becomes
        // the cloth. What matters is that it sampled at all rather than applying a default.
        verify(banner).setBaseColor(DyeColor.YELLOW);
        verify(sender, atLeastOnce()).sendMessage(contains("now shows"));
    }

    @Test
    @DisplayName("a mirror that goes nowhere has nothing to look at, and is told so")
    void refusesAnUnpointedMirror()
    {
        MirrorManager.add(new QuantumMirror("museum", new MirrorBlock("world", 1, 64, 1), null));
        try (final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(bannerWorld);

            run("mirror", "stamp", "museum");
        }
        verify(banner, never()).update(anyBoolean());
        verify(sender, atLeastOnce()).sendMessage(contains("does not go anywhere yet"));
    }

    @Test
    @DisplayName("a destination world that is down says so rather than stamping a guess")
    void refusesWhenTheFarWorldIsNotLoaded()
    {
        pointedMirror();
        try (final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(bannerWorld);
            bukkit.when(() -> Bukkit.getWorld("far")).thenReturn(null);

            run("mirror", "stamp", "museum");
        }
        verify(banner, never()).update(anyBoolean());
        verify(sender, atLeastOnce()).sendMessage(contains("is not loaded"));
    }

    @Test
    @DisplayName("a banner somebody took down is not stamped over whatever replaced it")
    void refusesWhenTheBannerIsGone()
    {
        pointedMirror();
        final Block stone = mock(Block.class);
        when(stone.getType()).thenReturn(Material.STONE);
        when(bannerWorld.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(stone);

        try (final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(bannerWorld);

            run("mirror", "stamp", "museum", "nether");
        }
        verify(stone, never()).getState();
        verify(sender, atLeastOnce()).sendMessage(contains("not a banner any more"));
    }

    @Test
    @DisplayName("an unknown mirror is named in the refusal")
    void refusesAnUnknownMirror()
    {
        run("mirror", "stamp", "nosuch", "nether");

        verify(sender, atLeastOnce()).sendMessage(contains("no mirror called 'nosuch'"));
    }

    @Test
    @DisplayName("stamp with no mirror named offers the looks in its usage line")
    void usageListsTheLooks()
    {
        assertTrue(run("mirror", "stamp"));

        verify(sender, atLeastOnce()).sendMessage(contains("stamp <name>"));
        verify(sender, atLeastOnce()).sendMessage(contains("nether"));
    }

    /**
     * Completion offers mirrors in the third slot and looks in the fourth.
     *
     * <p>Here rather than beside the other completion tests because the fourth slot comes out
     * of the preset registry, and a class that does not load it would be asserting on an empty
     * list whatever the code did.
     */
    @Test
    @DisplayName("stamp completes a mirror, then a look")
    void completesMirrorsThenLooks()
    {
        pointedMirror();

        assertTrue(complete("mirror", "stamp", "").contains("museum"),
            "the third word is a mirror");
        assertTrue(complete("mirror", "stamp", "museum", "").contains("nether"),
            "the fourth is a look");
        assertTrue(complete("mirror", "").contains("stamp"),
            "stamp should be offered as a verb");
    }

    /**
     * With nothing loaded, the usage line must not read as an empty required argument.
     *
     * <p>{@code stamp <name> []} looks like a list of choices that exists and happens to be
     * empty, which is a different and more confusing thing than an optional argument.
     */
    @Test
    @DisplayName("with no looks loaded the usage line says <look> rather than []")
    void usageWithNoPresets() throws IOException
    {
        emptyRegistry();

        assertTrue(run("mirror", "stamp"));

        verify(sender, atLeastOnce()).sendMessage(contains("stamp <name> [<look>]"));
    }

    @Test
    @DisplayName("with no looks loaded, naming one says so rather than listing nothing")
    void namingALookWithNoPresets() throws IOException
    {
        emptyRegistry();
        pointedMirror();
        try (final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(bannerWorld);

            run("mirror", "stamp", "museum", "nether");
        }
        verify(banner, never()).update(anyBoolean());
        verify(sender, atLeastOnce()).sendMessage(contains("no looks loaded at all"));
    }

    /**
     * Leaves the registry with nothing in it.
     *
     * <p>By pointing it at a plain file, which mkdirs cannot turn into a directory -- the same
     * position the plugin is in when its folder is not writable. Deleting the files after a
     * load would not do it, because the next load restores them.
     */
    private void emptyRegistry() throws IOException
    {
        final File notADirectory = new File(presets, "in-the-way");
        Files.writeString(notADirectory.toPath(), "not a directory", StandardCharsets.UTF_8);
        assertEquals(0, MirrorPresetRegistry.load(notADirectory));
    }

    /** A mirror in "world" pointing into "far". */
    private void pointedMirror()
    {
        MirrorManager.add(new QuantumMirror("museum", new MirrorBlock("world", 1, 64, 1),
            new MirrorPoint("far", 100, 64, 200, 0f, 0f)));
    }

    private boolean run(final String... args)
    {
        return new MirrorCommand().execute(sender, args);
    }

    private static java.util.List<String> complete(final String... args)
    {
        return com.wormhole_xtreme.wormhole.command.SubCommands.find("mirror")
            .completeArgs(null, args);
    }
}
