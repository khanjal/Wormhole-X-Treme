package com.wormhole_xtreme.wormhole.command.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import com.wormhole_xtreme.wormhole.model.mirror.MirrorText;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorView;
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
    void appliesANamedLookWithoutLookingAtTheDestination()
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
        verify(sender, atLeastOnce())
            .sendMessage(contains("looks like " + MirrorText.NAME_COLOUR + "nether"));
    }

    /**
     * Naming a look nobody has says so, and counts the alternatives rather than listing them.
     *
     * <p>Same rule and same threshold as the usage line: with every shipped look loaded the
     * names come to more than the budget, so the refusal says how many there are. It still
     * names what was typed, which is the part that tells somebody they mistyped rather than
     * that the command is broken.
     */
    @Test
    void countsTheLooksThatExistWhenGivenOneNobodyHas()
    {
        pointedMirror();
        final int loaded = MirrorPresetRegistry.names().length;
        try (final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(bannerWorld);

            run("mirror", "stamp", "museum", "chartreuse");
        }
        verify(banner, never()).update(anyBoolean());
        verify(sender, atLeastOnce())
            .sendMessage(contains("no look called '" + MirrorText.NAME_COLOUR + "chartreuse"));
        verify(sender, atLeastOnce())
            .sendMessage(contains("There are " + loaded + " to choose from"));
        verify(sender, atLeastOnce()).sendMessage(contains("press tab"));
    }

    /**
     * One word that is a look, and the banner in front of you is what it goes on.
     *
     * <p>Stamping is the verb most often run twice -- pick a look, look at it, pick another --
     * and it was the verb that made you type the mirror's name every time, including the
     * derived name of a pair's far half.
     */
    @Test
    void stampsTheBannerBeingLookedAtWhenTheOnlyWordIsALook()
    {
        pointedMirror();
        when(bannerWorld.getName()).thenReturn("world");
        final Block inFront = mock(Block.class);
        when(inFront.getType()).thenReturn(Material.WHITE_WALL_BANNER);
        when(inFront.getWorld()).thenReturn(bannerWorld);
        when(inFront.getX()).thenReturn(1);
        when(inFront.getY()).thenReturn(64);
        when(inFront.getZ()).thenReturn(1);
        when(sender.getTargetBlockExact(6)).thenReturn(inFront);
        try (final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(bannerWorld);

            assertTrue(run("mirror", "stamp", "nether"));
        }
        // The nether preset's red, and no complaint about a mirror called nether.
        verify(banner).setBaseColor(DyeColor.RED);
        verify(sender, never()).sendMessage(contains("no mirror called"));
    }

    /**
     * A mirror named after a look is still the mirror.
     *
     * <p>Both readings of a single word exist, so one has to win, and it is the one the word
     * already had: a name. Sixty-five of the shipped looks are biomes, so a server naming its
     * mirrors after where they go -- {@code nether}, {@code badlands} -- is the likely one to
     * collide, and it should not find {@code stamp nether} quietly meaning something else than
     * it did last week.
     */
    @Test
    void aMirrorNamedAfterALookIsStillTheMirror()
    {
        MirrorManager.add(new QuantumMirror("nether", new MirrorBlock("world", 1, 64, 1),
            new MirrorPoint("far", 100, 64, 200, 0f, 0f)));
        try (final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(bannerWorld);
            bukkit.when(() -> Bukkit.getWorld("far")).thenReturn(farWorld);

            assertTrue(run("mirror", "stamp", "nether"));
        }
        // Sand, sampled from the far side, rather than the nether preset's red. The mirror was
        // read as the name it is, and the look it shares that name with was not applied.
        verify(banner).setBaseColor(DyeColor.YELLOW);
        verify(banner, never()).setBaseColor(DyeColor.RED);
    }

    @Test
    void readsTheFarSideAndSaysWhatItFoundWhenNoLookIsNamed()
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

    /**
     * Stamping a mirror onto the Nether by hand gives it the Nether's look.
     *
     * <p>The bug this guards. {@code stamp} carried its own copy of "enclosed means indoors",
     * which is right for a library and wrong for the Nether -- and the rule that knows the
     * difference lived only in {@code MirrorLook}. So stamping a Nether mirror by hand dressed
     * it as somebody's room, while the very same mirror corrected itself to the Nether's look
     * the first time a player walked up to it in dynamic mode. One banner, two appearances,
     * depending on which code touched it last.
     *
     * <p>The view is stubbed rather than sampled because producing a Nether one for real means
     * naming a {@code Biome} constant, and those resolve through a registry needing a live
     * server from 1.21.4 on. What is under test is the decision, not the sampler, and
     * {@code MirrorViewTest} covers the sampler.
     */
    @Test
    void stampingAMirrorOntoTheNetherDoesNotDressItAsARoom()
    {
        pointedMirror();
        final MirrorView nether =
            new MirrorView("NETHER_WASTES", java.util.List.of(DyeColor.BROWN), true);
        try (final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
            final MockedStatic<MirrorView> views = mockStatic(MirrorView.class))
        {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(bannerWorld);
            views.when(() -> MirrorView.look(any(MirrorPoint.class))).thenReturn(nether);

            assertTrue(run("mirror", "stamp", "museum"));
        }
        // The nether preset's own red, not the brown of whatever the sample happened to be
        // mostly made of. Both halves of the old bug land on this one assertion: the wrong
        // look was picked, and then its colour was overwritten as well.
        verify(banner).setBaseColor(DyeColor.RED);
        verify(sender, never()).sendMessage(contains("somewhere indoors"));
    }

    /** And a room in an ordinary world still reads as one, so the fix did not go too far. */
    @Test
    void stampingAMirrorOntoARoomStillDressesItAsARoom()
    {
        pointedMirror();
        final MirrorView library =
            new MirrorView("FOREST", java.util.List.of(DyeColor.BROWN), true);
        try (final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
            final MockedStatic<MirrorView> views = mockStatic(MirrorView.class))
        {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(bannerWorld);
            views.when(() -> MirrorView.look(any(MirrorPoint.class))).thenReturn(library);

            assertTrue(run("mirror", "stamp", "museum"));
        }
        verify(banner).setBaseColor(DyeColor.BROWN);
        verify(sender, atLeastOnce()).sendMessage(contains("somewhere indoors"));
    }

    @Test
    void refusesAMirrorThatGoesNowhereAndHasNothingToLookAt()
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
    void refusesRatherThanGuessWhenTheDestinationWorldIsDown()
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
    void refusesToStampOverWhatReplacedABannerTakenDown()
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
    void namesTheUnknownMirrorInItsRefusal()
    {
        run("mirror", "stamp", "nosuch", "nether");

        verify(sender, atLeastOnce())
            .sendMessage(contains("no mirror called '" + MirrorText.NAME_COLOUR + "nosuch"));
    }

    /**
     * With every shipped look loaded the list is too long to print, so it is counted.
     *
     * <p>Seventeen names bar-separated is 130 characters, and the usage line around them
     * another 39 -- four wrapped lines of chat for one usage message. The count still says
     * there is a real list to go and find, where a bare {@code <look>} would read as a
     * free-form argument, and tab completion is what actually offers the names.
     */
    @Test
    void countsTheLooksInItsUsageLineWhenThereAreTooManyToList()
    {
        final int loaded = MirrorPresetRegistry.names().length;

        assertTrue(run("mirror", "stamp"));

        verify(sender, atLeastOnce()).sendMessage(contains("stamp [<name>] [<look>]"));
        verify(sender, atLeastOnce()).sendMessage(contains(loaded + " looks to choose from"));
        verify(sender, atLeastOnce()).sendMessage(contains("press tab"));
    }

    /**
     * The threshold itself, from both sides. One rule, used by the usage line and the refusal.
     *
     * <p>Through the decision rather than the command, because the registry only ever loads
     * what ships -- one answer, and not the one that would catch the rule being inverted or
     * the budget being set somewhere useless. The two messages are then pinned end to end on
     * the long side, which is what would catch the branches being swapped.
     */
    @Test
    void listsAShortSetOfLooksAndCountsALongOne()
    {
        assertTrue(MirrorCommand.looksFitInAMessage(
            new String[] { "nether", "end", "ocean", "forest", "desert" }),
            "five short names are well inside the budget and should be listed");
        assertFalse(MirrorCommand.looksFitInAMessage(MirrorPresetRegistry.names()),
            "the shipped looks are past the budget and should be counted");
        assertFalse(MirrorCommand.looksFitInAMessage(new String[0]),
            "with none loaded there is nothing to list");

        // Either side of the boundary by one character, so a budget moved anywhere useless
        // fails here rather than quietly changing what players see.
        assertTrue(MirrorCommand.looksFitInAMessage(new String[] { "a".repeat(80) }),
            "a list exactly at the budget still fits");
        assertFalse(MirrorCommand.looksFitInAMessage(new String[] { "a".repeat(81) }),
            "one character past it does not");
    }

    /**
     * Completion offers mirrors in the third slot and looks in the fourth.
     *
     * <p>Here rather than beside the other completion tests because the fourth slot comes out
     * of the preset registry, and a class that does not load it would be asserting on an empty
     * list whatever the code did.
     */
    @Test
    void completesAMirrorAndThenALook()
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
     * A mirror removed while it is being stamped does not take the command down with it.
     *
     * <p>The look is written to the banner first and to the mirror second, and between those
     * two somebody can run {@code mirror remove}. Simulated by removing it at the moment the
     * banner's state is read, which is exactly where the gap is. The banner keeps the look --
     * it is an ordinary banner now -- and there is simply no mirror left to write it against.
     */
    @Test
    void survivesTheMirrorBeingRemovedPartWayThroughAStamp()
    {
        pointedMirror();
        final Block vanishing = mock(Block.class);
        when(vanishing.getType()).thenReturn(Material.WHITE_WALL_BANNER);
        when(vanishing.getState()).thenAnswer(invocation ->
        {
            MirrorManager.remove("museum");
            return banner;
        });
        when(bannerWorld.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(vanishing);

        try (final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(bannerWorld);

            assertTrue(run("mirror", "stamp", "museum", "nether"),
                "the command still reports itself handled rather than throwing");
        }
        verify(banner).setBaseColor(DyeColor.RED);
        assertNull(MirrorManager.byName("museum"), "it really was removed mid-stamp");
    }

    /**
     * With nothing loaded, the usage line must not read as an empty required argument.
     *
     * <p>{@code stamp <name> []} looks like a list of choices that exists and happens to be
     * empty, which is a different and more confusing thing than an optional argument.
     */
    @Test
    void saysLookRatherThanEmptyBracketsWithNoLooksLoaded() throws IOException
    {
        emptyRegistry();

        assertTrue(run("mirror", "stamp"));

        verify(sender, atLeastOnce()).sendMessage(contains("stamp [<name>] [<look>]"));
    }

    @Test
    void saysSoRatherThanListNothingWhenNamingALookWithNoneLoaded() throws IOException
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
