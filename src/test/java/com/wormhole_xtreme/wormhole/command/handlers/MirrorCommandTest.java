package com.wormhole_xtreme.wormhole.command.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Rotatable;
import org.bukkit.command.CommandSender;
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
import com.wormhole_xtreme.wormhole.model.mirror.MirrorLook;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorManager;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorPoint;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorText;
import com.wormhole_xtreme.wormhole.model.mirror.QuantumMirror;

/**
 * Binding a banner, pointing it, and the rules that refuse.
 *
 * <p>The command layer is where a mistake is most visible to an admin and least visible to the
 * rest of the suite: nothing else calls these methods, so an error here compiles, passes every
 * other test, and only shows up as a banner that does not work.
 *
 * <p>The refusals get as much attention as the successes. A mirror that quietly does the wrong
 * thing is worse than one that says why it will not -- and the cross-world rule in particular
 * is a deliberate restriction, which means a server owner will meet it while trying to do
 * something reasonable and needs to be told which setting relaxes it.
 */
class MirrorCommandTest
{
    /**
     * Where the command's saves go.
     *
     * <p>Every verb that changes a mirror calls saveAll, so without this the plugin mock's
     * unstubbed data folder sends the file somewhere relative -- which meant these tests were
     * writing a real data/mirror.yml into whatever directory the suite was run from.
     */
    @TempDir
    File dataFolder;

    private Player player;
    private World here;
    private Location standing;

    @BeforeEach
    void setUp() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        PluginTestSupport.install(plugin);
        ConfigTestSupport.clear();
        // The too-close warning reaches twice the depth; at the default depth that is the whole world.
        ConfigTestSupport.set(com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys.MIRROR_VIEW_DEPTH, 32);
        MirrorManager.clear();

        here = mock(World.class);
        when(here.getName()).thenReturn("world");
        standing = new Location(here, 10.0, 64.0, 10.0, 0.0f, 0.0f);

        player = mock(Player.class);
        when(player.getLocation()).thenReturn(standing);
        // Every verb is behind the config node, so without this the command refuses before
        // reaching any of them and every test below asserts on a permission message instead
        // of on what it is actually about.
        when(player.isOp()).thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        MirrorManager.clear();
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    private Block banner(final Material type)
    {
        final Block block = mock(Block.class);
        when(block.getType()).thenReturn(type);
        when(block.getWorld()).thenReturn(here);
        when(block.getX()).thenReturn(1);
        when(block.getY()).thenReturn(64);
        when(block.getZ()).thenReturn(1);
        // Hung facing north on solid wall, which is where a mirror may be made.
        final Directional facing = mock(Directional.class);
        when(facing.getFacing()).thenReturn(BlockFace.NORTH);
        when(block.getBlockData()).thenReturn(facing);
        final org.bukkit.block.data.BlockData solid = mock(org.bukkit.block.data.BlockData.class);
        when(solid.isOccluding()).thenReturn(true);
        final Block wall = mock(Block.class);
        when(wall.getBlockData()).thenReturn(solid);
        when(here.getBlockAt(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyInt())).thenReturn(wall);
        return block;
    }

    private static boolean run(final CommandSender sender, final String... args)
    {
        return new MirrorCommand().execute(sender, args);
    }

    /**
     * debug says one thing a line, as a grey label and a white value, with what stops a view in red.
     *
     * <p>"Should we do dedicated lines like property: value or something like that to be more
     * structured?" It said a sentence a line in grey -- {@code key ..., file missing, far world
     * loaded} -- with the word that mattered somewhere in the middle of one.
     */
    @Test
    void debugSaysOneThingALineWithWhatStopsAViewInRed()
    {
        MirrorManager.add(new QuantumMirror("museum", new MirrorBlock("world", 1, 64, 1),
            new MirrorPoint("far", 0.5, 70.0, 0.5, 0.0f, 0.0f)));
        when(player.getEyeLocation()).thenReturn(standing);
        when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            run(player, "mirror", "debug", "museum", "all");
        }

        final String value = MirrorText.VALUE_COLOUR;
        verify(player).sendMessage(contains(MirrorText.heading("capture")));
        verify(player).sendMessage(contains(MirrorText.BODY_COLOUR + "banner: " + value));
        verify(player).sendMessage(contains(MirrorText.BODY_COLOUR + "room: " + value + MirrorText.NAME_COLOUR + "far"));
        verify(player).sendMessage(contains("file: " + value + MirrorText.BAD_COLOUR + "missing"));
        verify(player).sendMessage(contains("looking into: " + value + "no window"));
        verify(player).sendMessage(contains("your eye: " + value + "10.00,64.00,10.00"));
    }

    /**
     * debug without all fits on a screen of chat: the mirror, its capture and your view, a line each.
     *
     * <p>"The debug scrolls off the chat (I know you can scroll). Is there a more compact version?"
     * All of it was some twenty lines, and chat shows ten.
     */
    @Test
    void debugWithoutAllFitsOnAScreenOfChat()
    {
        MirrorManager.add(new QuantumMirror("museum", new MirrorBlock("world", 1, 64, 1),
            new MirrorPoint("far", 0.5, 70.0, 0.5, 0.0f, 0.0f)));
        when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        final org.mockito.ArgumentCaptor<String> said = org.mockito.ArgumentCaptor.forClass(String.class);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            run(player, "mirror", "debug", "museum");
        }

        verify(player, atLeastOnce()).sendMessage(said.capture());
        final java.util.List<String> lines = said.getAllValues();
        assertTrue(lines.size() <= 6, "chat shows ten lines, and this should leave room around it: " + lines);
        assertTrue(lines.stream().anyMatch(line -> line.contains(
            "capture: " + MirrorText.VALUE_COLOUR + MirrorText.BAD_COLOUR + "file missing")), "the capture on one line: " + lines);
        assertTrue(lines.stream().anyMatch(line -> line.contains(
            "view: " + MirrorText.VALUE_COLOUR + "you are looking into no window")), "your view: " + lines);
        assertTrue(lines.stream().anyMatch(line -> line.contains("debug museum all")), "and how to see the rest: " + lines);
    }

    /**
     * {@code create} names the banner: the word people try first, and the only one that does now.
     *
     * <p>{@code set} was the older word for it. It changes what a mirror has instead, so a
     * server owner typing {@code set museum} out of habit gets set's form, not a new mirror.
     */
    @Test
    void createNamesTheBanner()
    {
        final Block inFront = banner(Material.WHITE_WALL_BANNER);
        when(player.getTargetBlockExact(6)).thenReturn(inFront);

        assertTrue(run(player, "mirror", "create", "museum"));

        final QuantumMirror mirror = MirrorManager.byName("museum");
        assertNotNull(mirror, "create has to bind the banner, not print the form");
        assertEquals(new MirrorBlock("world", 1, 64, 1), mirror.banner());
    }

    /**
     * A mirror made too close to another is made, and says it will not be drawn whole.
     *
     * <p>"Add the create warning for mirrors too close together." Within twice the view depth,
     * neither is drawn whole: each is trimmed to what a viewer sees, which costs more.
     */
    @Test
    void aMirrorMadeTooCloseToAnotherSaysSo()
    {
        ConfigTestSupport.set(com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys.MIRROR_PER_WORLD_LIMIT, 0);
        MirrorManager.add(new QuantumMirror("hall", new MirrorBlock("world", 1, 64, 12), null));
        final Block wallBanner = banner(Material.WHITE_WALL_BANNER);
        when(player.getTargetBlockExact(6)).thenReturn(wallBanner);

        run(player, "mirror", "create", "museum");

        assertNotNull(MirrorManager.byName("museum"), "made anyway: it works, only not drawn whole");
        verify(player).sendMessage(contains("It is 11 blocks from"));
        verify(player).sendMessage(contains("nearer than 64"));
    }

    /**
     * A mirror on a wall solid a block out but not two is made, and told which block is short.
     *
     * <p>"Let's go down to 1 and then leave that the lower limit. We can do a warning if it's less
     * than 2." The banner at 1 64 1 faces north; the wall is z 2, and 3 61 2 is two out and two down.
     */
    @Test
    void aMirrorOnAWallOnlyABlockOutIsMadeAndToldSo()
    {
        final Block wallBanner = banner(Material.WHITE_WALL_BANNER);
        final Block wall = here.getBlockAt(0, 0, 0);
        final org.bukkit.block.data.BlockData open = mock(org.bukkit.block.data.BlockData.class);
        final Block gap = mock(Block.class);
        when(gap.getBlockData()).thenReturn(open);
        when(here.getBlockAt(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyInt())).thenAnswer(call ->
                ((((int) call.getArgument(0)) == 3) && (((int) call.getArgument(1)) == 61)
                    && (((int) call.getArgument(2)) == 2)) ? gap : wall);
        when(player.getTargetBlockExact(6)).thenReturn(wallBanner);

        run(player, "mirror", "create", "museum");

        assertNotNull(MirrorManager.byName("museum"), "a block of wall is enough to make it");
        verify(player).sendMessage(contains("3 61 2"));
        verify(player).sendMessage(contains("two blocks out hides"));
    }

    /** A mirror far enough away, or in another world, says nothing about it. */
    @Test
    void aMirrorFarEnoughAwayOrInAnotherWorldIsNotWarnedAbout()
    {
        ConfigTestSupport.set(com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys.MIRROR_PER_WORLD_LIMIT, 0);
        MirrorManager.add(new QuantumMirror("hall", new MirrorBlock("world", 1, 64, 66), null));
        MirrorManager.add(new QuantumMirror("nether", new MirrorBlock("world_nether", 1, 64, 2), null));
        final Block wallBanner = banner(Material.WHITE_WALL_BANNER);
        when(player.getTargetBlockExact(6)).thenReturn(wallBanner);

        run(player, "mirror", "create", "museum");

        assertNotNull(MirrorManager.byName("museum"));
        verify(player, never()).sendMessage(contains("blocks from"));
    }

    /** Naming the banner you are looking at is the first half of binding one. */
    @Test
    void setNamesTheBannerThePlayerIsLookingAt()
    {
        final Block wallBanner = banner(Material.WHITE_WALL_BANNER);
        when(player.getTargetBlockExact(6)).thenReturn(wallBanner);

        assertTrue(run(player, "mirror", "create", "museum"));

        final QuantumMirror mirror = MirrorManager.byName("museum");
        assertNotNull(mirror, "the mirror should exist after create");
        assertEquals(new MirrorBlock("world", 1, 64, 1), mirror.banner());
        assertEquals(new MirrorPoint("world", 1.5, 63, 1.5, 180f, 0f), mirror.destination(),
            "its own room: in front of the banner, level with the bottom of the opening, facing out");
    }

    /**
     * Naming a banner that is already a mirror renames it, and brings everything with it.
     *
     * <p>The museum case: a room of mirrors named after the worlds they open onto, wanting
     * names that say what is through them instead. Before this, {@code set} looked the name up
     * by the *new* name, found nothing, and built a mirror from scratch -- so the rename
     * silently dropped the destination, the look and the start, and left the old name in
     * place beside it, both claiming the banner. The reply said "It goes nowhere yet", which
     * reads as a next step rather than as a warning that the mirror has just been undone.
     */
    @Test
    void setRenamesTheMirrorOnThatBannerAndBringsEverythingWithIt()
    {
        final MirrorBlock hung = new MirrorBlock("world", 1, 64, 1);
        final MirrorPoint far = new MirrorPoint("snapshot", 8, 70, 9, 0f, 0f);
        MirrorManager.add(new QuantumMirror("world_2011_05_09", hung, far,
            MirrorLook.named("cavern"), "hub"));
        final Block wallBanner = banner(Material.WHITE_WALL_BANNER);
        when(player.getTargetBlockExact(6)).thenReturn(wallBanner);

        assertTrue(run(player, "mirror", "create", "old-spawn"));

        final QuantumMirror renamed = MirrorManager.byName("old-spawn");
        assertNotNull(renamed, "the new name should be the mirror");
        assertEquals(far, renamed.destination(), "a rename must not unpoint the mirror");
        assertEquals(MirrorLook.named("cavern"), renamed.look(), "nor forget how it looks");
        assertEquals("hub", renamed.start(), "nor the mirror it opens onto first");
        assertNull(MirrorManager.byName("world_2011_05_09"),
            "the old name should be gone, not left beside it claiming the same banner");
        assertEquals(renamed, MirrorManager.at(hung),
            "and the banner should answer to the renamed mirror");
    }

    /**
     * Naming an existing mirror while looking at a different banner moves it there.
     *
     * <p>The behaviour that was always intended, but it rebuilt the mirror from its name and
     * the block, so moving a stamped mirror to a new banner used to strip its look and settings
     * on the way.
     */
    @Test
    void setMovesAnExistingMirrorToTheBannerYouAreLookingAtWithItsLook()
    {
        final MirrorPoint far = new MirrorPoint("snapshot", 8, 70, 9, 0f, 0f);
        MirrorManager.add(new QuantumMirror("museum", new MirrorBlock("world", 40, 64, 40), far,
            MirrorLook.named("end")));
        final Block wallBanner = banner(Material.WHITE_WALL_BANNER);
        when(player.getTargetBlockExact(6)).thenReturn(wallBanner);

        assertTrue(run(player, "mirror", "create", "museum"));

        final QuantumMirror moved = MirrorManager.byName("museum");
        assertEquals(new MirrorBlock("world", 1, 64, 1), moved.banner(), "moved to this banner");
        assertEquals(new MirrorPoint("world", 1.5, 63, 1.5, 180f, 0f), moved.destination(),
            "a moved mirror's room is in front of the banner it hangs on now");
        assertEquals(MirrorLook.named("end"), moved.look(), "a move should keep the look");
        assertNull(MirrorManager.at(new MirrorBlock("world", 40, 64, 40)),
            "and should let go of the banner it came from");
    }

    /**
     * A name that belongs to another mirror, on a banner that is already one, is refused.
     *
     * <p>The one case {@code set} cannot read: taking the name would move that mirror here and
     * leave this one on no banner, and renaming this one would collide with a name in use.
     * Either way a mirror the operator did not mention stops working, so neither happens.
     */
    @Test
    void setRefusesWhenTheNameAndTheBannerBelongToDifferentMirrors()
    {
        final MirrorBlock hung = new MirrorBlock("world", 1, 64, 1);
        final MirrorBlock elsewhere = new MirrorBlock("world", 40, 64, 40);
        MirrorManager.add(new QuantumMirror("library", hung, null));
        MirrorManager.add(new QuantumMirror("museum", elsewhere, null));
        final Block wallBanner = banner(Material.WHITE_WALL_BANNER);
        when(player.getTargetBlockExact(6)).thenReturn(wallBanner);

        assertTrue(run(player, "mirror", "create", "museum"));

        verify(player, atLeastOnce()).sendMessage(contains("already"));
        assertEquals(hung, MirrorManager.byName("library").banner(), "library is where it was");
        assertEquals(elsewhere, MirrorManager.byName("museum").banner(), "and so is museum");
    }

    /** Naming a banner what it is already called is not a mistake, but it is not work either. */
    @Test
    void setSaysThereIsNothingToDoWhenTheBannerAlreadyHasThatName()
    {
        final MirrorBlock hung = new MirrorBlock("world", 1, 64, 1);
        final MirrorPoint far = new MirrorPoint("snapshot", 8, 70, 9, 0f, 0f);
        MirrorManager.add(new QuantumMirror("museum", hung, far));
        final Block wallBanner = banner(Material.WHITE_WALL_BANNER);
        when(player.getTargetBlockExact(6)).thenReturn(wallBanner);

        assertTrue(run(player, "mirror", "create", "museum"));

        verify(player, atLeastOnce()).sendMessage(contains("Nothing to do"));
        assertEquals(far, MirrorManager.byName("museum").destination(), "and nothing was done");
    }

    /**
     * Pointing a banner at a block that is not one refuses, and says what it is instead.
     *
     * <p>"That did not work" would leave an admin looking for the wrong problem -- most likely
     * assuming they were out of range rather than off-target.
     */
    @Test
    void setRefusesABlockThatIsNotABanner()
    {
        final Block notABanner = banner(Material.STONE);
        when(player.getTargetBlockExact(6)).thenReturn(notABanner);

        run(player, "mirror", "create", "museum");

        assertNull(MirrorManager.byName("museum"));
        verify(player, atLeastOnce()).sendMessage(contains("not a banner"));
    }

    /**
     * A plain white banner made a mirror is given the mirror look.
     *
     * <p>So a new mirror looks like one from across a room, without anybody choosing a look.
     */
    @Test
    void setGivesAPlainWhiteBannerTheMirrorLook()
    {
        com.wormhole_xtreme.wormhole.model.mirror.MirrorPresetRegistry.load(new java.io.File(dataFolder, "presets"));
        final Block wallBanner = banner(Material.WHITE_WALL_BANNER);
        final org.bukkit.block.Banner cloth = mock(org.bukkit.block.Banner.class);
        when(cloth.getPatterns()).thenReturn(new java.util.ArrayList<>());
        when(wallBanner.getState()).thenReturn(cloth);
        when(player.getTargetBlockExact(6)).thenReturn(wallBanner);

        assertTrue(run(player, "mirror", "create", "museum"));

        verify(cloth).setBaseColor(org.bukkit.DyeColor.LIGHT_BLUE);
        verify(cloth).update(true);
        assertEquals(MirrorLook.named("mirror"), MirrorManager.byName("museum").look(),
            "and the mirror should remember the look it was given");
    }

    /**
     * create on a wall banner with another beside it, facing the same way, makes one mirror two wide.
     *
     * <p>"How about wide support for the mirror too, for even places?" The banner faces north, so
     * looking at the wall right is west, and the one at x 0 is the right of the pair: the mirror is
     * held by the banner at x 1, and either answers a click.
     */
    @Test
    void createOnABannerWithAnotherBesideItMakesOneMirrorTwoWide()
    {
        final Block wallBanner = banner(Material.WHITE_WALL_BANNER);
        final Block beside = mock(Block.class);
        when(beside.getType()).thenReturn(Material.WHITE_WALL_BANNER);
        when(beside.getWorld()).thenReturn(here);
        when(beside.getX()).thenReturn(0);
        when(beside.getY()).thenReturn(64);
        when(beside.getZ()).thenReturn(1);
        final Directional northward = mock(Directional.class);
        when(northward.getFacing()).thenReturn(BlockFace.NORTH);
        when(beside.getBlockData()).thenReturn(northward);
        when(here.getBlockAt(0, 64, 1)).thenReturn(beside);
        when(player.getTargetBlockExact(6)).thenReturn(wallBanner);

        assertTrue(run(player, "mirror", "create", "hall"));

        final QuantumMirror hall = MirrorManager.byName("hall");
        assertEquals(2, hall.width(), "two banners, one mirror");
        assertEquals(new MirrorBlock("world", 1, 64, 1), hall.banner(), "held by the left banner, looking at the wall");
        assertEquals(hall, MirrorManager.at(new MirrorBlock("world", 0, 64, 1)), "and the other answers a click too");
    }

    /** A banner somebody already patterned keeps its patterns. */
    @Test
    void setLeavesAPatternedBannerAsItWas()
    {
        com.wormhole_xtreme.wormhole.model.mirror.MirrorPresetRegistry.load(new java.io.File(dataFolder, "presets"));
        final Block wallBanner = banner(Material.WHITE_WALL_BANNER);
        final org.bukkit.block.Banner cloth = mock(org.bukkit.block.Banner.class);
        when(cloth.getPatterns()).thenReturn(java.util.List.of(mock(org.bukkit.block.banner.Pattern.class)));
        when(wallBanner.getState()).thenReturn(cloth);
        when(player.getTargetBlockExact(6)).thenReturn(wallBanner);

        assertTrue(run(player, "mirror", "create", "museum"));

        assertNotNull(MirrorManager.byName("museum"), "it is still made a mirror");
        verify(cloth, never()).update(anyBoolean());
    }

    /** A banner on a post cannot be made a mirror, and the refusal says where one goes. */
    @Test
    void setRefusesAFreestandingBanner()
    {
        final Block post = banner(Material.WHITE_BANNER);
        final Rotatable onAPost = mock(Rotatable.class);
        when(post.getBlockData()).thenReturn(onAPost);
        when(player.getTargetBlockExact(6)).thenReturn(post);

        run(player, "mirror", "create", "museum");

        assertNull(MirrorManager.byName("museum"), "a freestanding banner shows its world past its edges");
        verify(player, atLeastOnce()).sendMessage(contains("hangs on a wall"));
    }

    /** Looking at nothing is its own message, since the fix is different. */
    @Test
    void setRefusesWhenLookingAtNothing()
    {
        when(player.getTargetBlockExact(6)).thenReturn(null);

        run(player, "mirror", "create", "museum");

        assertNull(MirrorManager.byName("museum"));
        verify(player, atLeastOnce()).sendMessage(contains("within six blocks"));
    }

    /**
     * start sets the mirror one opens onto when nobody at it has chosen, and none clears it.
     *
     * <p>"In a historical world we can default the mirror to the main server first." Saved with the
     * mirror, so it is still the start after a restart.
     */
    @Test
    void startSetsTheMirrorOneOpensOntoAndNoneClearsIt()
    {
        MirrorManager.add(new QuantumMirror("hub", new MirrorBlock("world", 40, 64, 40), null));
        MirrorManager.add(new QuantumMirror("archive", new MirrorBlock("world_2011", 1, 64, 1), null));

        assertTrue(run(player, "mirror", "set", "archive", "start", "hub"));
        assertEquals("hub", MirrorManager.byName("archive").start());
        verify(player, atLeastOnce()).sendMessage(contains("first."));

        assertTrue(run(player, "mirror", "set", "archive", "start", "none"));
        assertNull(MirrorManager.byName("archive").start(), "none is its own room again");
    }

    /** A start nobody has, or the mirror itself, is refused and changes nothing. */
    @Test
    void startRefusesAMirrorNobodyHasAndTheMirrorItself()
    {
        MirrorManager.add(new QuantumMirror("archive", new MirrorBlock("world_2011", 1, 64, 1), null));

        run(player, "mirror", "set", "archive", "start", "nowhere");
        verify(player, atLeastOnce()).sendMessage(contains("no mirror called"));

        run(player, "mirror", "set", "archive", "start", "ARCHIVE");
        verify(player, atLeastOnce()).sendMessage(contains("own room"));

        assertNull(MirrorManager.byName("archive").start(), "neither is a start");
    }

    /**
     * A named start with its value left off is refused, not read as the looked-at banner's start.
     *
     * <p>{@code set hub start} was shifted to {@code start hub}, the one-word form, so the banner
     * in front of the player quietly started on {@code hub}.
     */
    @Test
    void aNamedStartWithNoValueIsRefusedRatherThanSetOnTheBannerLookedAt()
    {
        MirrorManager.add(new QuantumMirror("hub", new MirrorBlock("world", 40, 64, 40), null));
        MirrorManager.add(new QuantumMirror("lobby", MirrorBlock.of(banner(Material.WHITE_WALL_BANNER)), null));
        final Block lookedAt = banner(Material.WHITE_WALL_BANNER);
        when(player.getTargetBlockExact(6)).thenReturn(lookedAt);

        run(player, "mirror", "set", "hub", "start");

        assertNull(MirrorManager.byName("lobby").start(), "the banner looked at is left alone");
        assertNull(MirrorManager.byName("hub").start());
        verify(player, atLeastOnce()).sendMessage(contains("start <mirror|none>"));
    }

    /** Removing gives the banner back. */
    @Test
    void removeForgetsTheMirror()
    {
        MirrorManager.add(new QuantumMirror("museum", new MirrorBlock("world", 0, 64, 0), null));

        assertTrue(run(player, "mirror", "remove", "museum"));

        assertNull(MirrorManager.byName("museum"));
        verify(player, atLeastOnce()).sendMessage(contains("ordinary banner again"));
    }

    @Test
    void removeSaysSoWhenThereIsNothingToRemove()
    {
        run(player, "mirror", "remove", "museum");

        verify(player, atLeastOnce()).sendMessage(contains("no mirror called"));
    }

    /**
     * Taking down the banner in front of you does not need its name.
     *
     * <p>The mirror this matters for is the one nobody named. {@code link} derives
     * {@code nether-return} for the second banner of a pair, so the half hardest to address by
     * name is the half somebody is standing in front of -- and before this, {@code remove} with
     * no name answered with the form instead of doing the obvious thing.
     */
    @Test
    void removeTakesTheBannerBeingLookedAtWhenNoNameIsGiven()
    {
        MirrorManager.add(new QuantumMirror("nether-return",
            new MirrorBlock("world", 1, 64, 1), null));
        final Block inFront = banner(Material.WHITE_WALL_BANNER);
        when(player.getTargetBlockExact(6)).thenReturn(inFront);

        assertTrue(run(player, "mirror", "remove"));

        assertNull(MirrorManager.byName("nether-return"),
            "the banner being looked at is the mirror the verb was about");
        verify(player, atLeastOnce()).sendMessage(contains("ordinary banner again"));
    }

    /**
     * An ordinary banner is told how to become a mirror, not that one is missing.
     *
     * <p>"There is no mirror called ''" is what a name-shaped answer would say here, and it
     * names nothing the player did. The banner is real and in front of them; what it is not is
     * a mirror yet.
     */
    @Test
    void aVerbWithNoNameOnABannerThatIsNotAMirrorSaysHowToNameIt()
    {
        final Block inFront = banner(Material.WHITE_WALL_BANNER);
        when(player.getTargetBlockExact(6)).thenReturn(inFront);

        assertTrue(run(player, "mirror", "remove"));

        verify(player, atLeastOnce()).sendMessage(contains("not a mirror"));
        verify(player, atLeastOnce()).sendMessage(contains("mirror create"));
    }

    /**
     * A console gets the form rather than being told to look at something.
     *
     * <p>There is nothing in front of a console, so what it is missing is the name. "That has to
     * be run in game" is true of the banner and useless as advice: naming the mirror is exactly
     * how a console does this.
     */
    @Test
    void aConsoleWithNoNameGetsTheForm()
    {
        final CommandSender console = mock(CommandSender.class);

        assertTrue(run(console, "mirror", "remove"));

        verify(console, atLeastOnce()).sendMessage(contains("remove [<name>]"));
        verify(console, never()).sendMessage(contains("run in game"));
    }

    /** An empty list says how to make one rather than printing nothing. */
    @Test
    void listOnAServerWithNoMirrorsExplainsHowToMakeOne()
    {
        assertTrue(run(player, "mirror", "list"));

        verify(player, atLeastOnce()).sendMessage(contains("No mirrors yet"));
        verify(player, atLeastOnce()).sendMessage(contains("/wormhole mirror create <name>"));
    }

    /** A mirror that has not been pointed lists as such rather than being hidden. */
    @Test
    void listShowsAMirrorThatGoesNowhereYet()
    {
        MirrorManager.add(new QuantumMirror("museum", new MirrorBlock("world", 0, 64, 0), null));

        run(player, "mirror", "list");

        verify(player, atLeastOnce()).sendMessage(contains("nowhere yet"));
    }

    /**
     * list names each mirror's capture key, which is its capture file's name.
     *
     * <p>"Add the capture key to mirror list too." Captures are named by place, not by mirror, so
     * a folder of them is unreadable without something saying which room is whose.
     */
    @Test
    void listNamesEachMirrorsCaptureKey()
    {
        MirrorManager.add(new QuantumMirror("museum", new MirrorBlock("world", 0, 64, 0),
            new MirrorPoint("World", 0.5, 63.0, -0.5, 180.0f, 0.0f)));

        run(player, "mirror", "list");

        verify(player, atLeastOnce()).sendMessage(contains("capture world_0_63_-1"));
    }

    /**
     * set and target need a player, because both depend on where somebody is.
     *
     * <p>Run from the console they would have no banner to look at and nowhere to stand, so
     * they say that rather than throwing a ClassCastException into the server log.
     */
    @Test
    void theVerbsThatNeedAPlayerSayWhenRunFromTheConsole()
    {
        final CommandSender console = mock(CommandSender.class);

        assertTrue(run(console, "mirror", "create", "museum"));

        verify(console, atLeastOnce()).sendMessage(contains("has to be run in game"));
    }

    /** An unknown verb, and no verb at all, both get the usage. */
    @Test
    void anUnknownVerbGetsTheUsage()
    {
        assertTrue(run(player, "mirror", "frobnicate"));
        assertTrue(run(player, "mirror"));

        verify(player, atLeastOnce())
            .sendMessage(contains("Usage: " + MirrorText.COMMAND_COLOUR + "/wormhole mirror"));
    }

    /**
     * A banner on a post, with nothing behind it, is still the banner you are looking at.
     *
     * <p>The bug this guards. {@code getTargetBlockExact} ray-traces against block shapes, and
     * a freestanding banner is a thin post: standing beside one and looking at it, the ray can
     * pass the shape entirely. Against a wall the miss is hidden, because the wall behind is
     * hit and the player is told "that is a stone". On a post in the open the ray hits nothing
     * at all, and a player standing right next to their banner was told to go and look at one
     * within six blocks.
     */
    @Test
    void aFreestandingBannerWithNothingBehindItIsStillFound()
    {
        final Block post = banner(Material.WHITE_BANNER);

        assertEquals(post, MirrorCommand.bannerInSight(null, java.util.List.of(post)),
            "a banner the ray missed but the line of sight crossed is the one meant");
    }

    /**
     * Aiming at a banner picks that one, not whichever the ray reached first.
     *
     * <p>With two banners in a row -- a corridor of them is the case this feature was built
     * for -- the one being pointed at is the one meant. Taking the first in the line of sight
     * regardless would quietly bind the near one every time.
     */
    @Test
    void theBannerYouAreAimedAtWinsOverTheOneInFront()
    {
        final Block aimed = banner(Material.WHITE_WALL_BANNER);
        final Block nearer = banner(Material.MAGENTA_BANNER);

        assertEquals(aimed, MirrorCommand.bannerInSight(aimed, java.util.List.of(nearer, aimed)));
    }

    /** A banner the ray passed through on its way to the wall behind it still counts. */
    @Test
    void aBannerInFrontOfTheBlockThatWasHitIsFound()
    {
        final Block wall = banner(Material.STONE);
        final Block hanging = banner(Material.WHITE_WALL_BANNER);

        assertEquals(hanging, MirrorCommand.bannerInSight(wall, java.util.List.of(hanging, wall)));
    }

    /**
     * Looking at no banner at all is still no banner.
     *
     * <p>The other direction, and the one that keeps the fix from becoming "any banner
     * anywhere": nothing in the line of sight means the refusal still happens. A mock with no
     * line of sight answers null for it, which is also what a player in an unloaded chunk
     * gives, so both are accepted as "nothing there".
     */
    @Test
    void lookingAtNoBannerFindsNone()
    {
        final Block stone = banner(Material.STONE);

        assertNull(MirrorCommand.bannerInSight(stone, java.util.List.of(stone)));
        assertNull(MirrorCommand.bannerInSight(null, java.util.List.of()));
        assertNull(MirrorCommand.bannerInSight(null, null));
    }

    /**
     * The whole command binds a freestanding banner the ray trace missed.
     *
     * <p>The unit test above pins the decision; this pins that {@code set} actually asks the
     * question that way. Without the line-of-sight route this refuses instead of naming
     * anything, which is exactly what was reported.
     */
    @Test
    void setFindsAFreestandingBannerTheRayTraceMissedAndRefusesIt()
    {
        final Block post = banner(Material.WHITE_BANNER);
        final Rotatable onAPost = mock(Rotatable.class);
        when(post.getBlockData()).thenReturn(onAPost);
        when(player.getTargetBlockExact(6)).thenReturn(null);
        when(player.getLineOfSight(null, 6)).thenReturn(java.util.List.of(post));

        assertTrue(run(player, "mirror", "create", "Post"));

        assertNull(MirrorManager.byName("Post"), "a banner on a post is not a mirror any more");
        verify(player, atLeastOnce()).sendMessage(contains("hangs on a wall"));
    }

    /**
     * Aiming at the cloth of a banner on a post names that banner.
     *
     * <p>The half of this the line-of-sight fallback did not fix, reported after it landed: "I
     * have to aim at the base of it to work... otherwise it goes through the banner". A
     * standing banner occupies one block and is drawn about two tall, so the cloth -- the part
     * anybody looks at -- hangs in the block above, where there is nothing to hit. The ray
     * crosses that empty block and carries on, and no pass over the blocks it crossed will ever
     * find the banner, because the banner is not on the ray at all.
     */
    @Test
    void aimingAtTheClothOfABannerOnAPostFindsIt()
    {
        final Block post = banner(Material.WHITE_BANNER);
        final Block cloth = banner(Material.AIR);
        when(cloth.getRelative(BlockFace.DOWN)).thenReturn(post);

        assertEquals(post, MirrorCommand.bannerInSight(null, java.util.List.of(cloth)),
            "the block above a banner on a post is where its cloth is drawn");
    }

    /**
     * A banner actually on the ray beats one standing under it.
     *
     * <p>Both passes can match at once -- a corridor of banners on posts is exactly that
     * arrangement -- and the one the player's ray genuinely crossed is the one they were
     * looking at. Doing the passes in the other order would quietly prefer a banner one block
     * below the aim.
     */
    @Test
    void aBannerOnTheRayBeatsOneStandingUnderIt()
    {
        final Block underfoot = banner(Material.WHITE_BANNER);
        final Block crossed = banner(Material.MAGENTA_BANNER);
        when(crossed.getRelative(BlockFace.DOWN)).thenReturn(underfoot);

        assertEquals(crossed, MirrorCommand.bannerInSight(null, java.util.List.of(crossed)));
    }

    /**
     * The cloth rule does not apply to wall banners.
     *
     * <p>A wall banner is drawn inside its own block, so there is no cloth above it to aim at.
     * Letting the rule apply to both families would mean aiming at a wall could name the banner
     * hanging below the spot -- binding a mirror the player never pointed at.
     */
    @Test
    void aWallBannerIsNotFoundByAimingAboveIt()
    {
        final Block hanging = banner(Material.WHITE_WALL_BANNER);
        final Block wallAbove = banner(Material.STONE);
        when(wallAbove.getRelative(BlockFace.DOWN)).thenReturn(hanging);

        assertNull(MirrorCommand.bannerInSight(null, java.util.List.of(wallAbove)));
    }

    /**
     * Naming a banner on a post says where it has to be clicked.
     *
     * <p>The one thing the plugin can say about a limitation it cannot fix. Only the base of a
     * standing banner can be clicked; a right-click at the cloth passes through it, and no
     * event reaches the plugin at all -- so there is no moment later at which it could explain
     * itself. This is that moment, with the player standing in front of the banner they just
     * named.
     */
    @Test
    void namingABannerOnAPostIsRefusedBeforeAnyAdviceAboutItsBase()
    {
        final Block post = banner(Material.WHITE_BANNER);
        final Rotatable onAPost = mock(Rotatable.class);
        when(post.getBlockData()).thenReturn(onAPost);
        when(player.getTargetBlockExact(6)).thenReturn(post);

        assertTrue(run(player, "mirror", "create", "Post"));

        verify(player, atLeastOnce()).sendMessage(contains("hangs on a wall"));
        verify(player, never()).sendMessage(contains("click near its base"));
    }

    /**
     * A wall banner is not given advice it does not need.
     *
     * <p>It is drawn inside its own block and can be clicked anywhere on it. A line about
     * bases on every mirror would be noise on the commonest one, and would teach people a
     * restriction that is not true of what they just built.
     */
    @Test
    void namingAWallBannerSaysNothingAboutBases()
    {
        final Block hanging = banner(Material.WHITE_WALL_BANNER);
        when(player.getTargetBlockExact(6)).thenReturn(hanging);

        assertTrue(run(player, "mirror", "create", "Hanging"));

        verify(player, never()).sendMessage(contains("click near its base"));
    }

    /** A verb that needs a name and was not given one says which form it wanted. */
    @Test
    void aVerbMissingItsNameGetsThatVerbsUsage()
    {
        final Block anyBanner = banner(Material.WHITE_WALL_BANNER);
        when(player.getTargetBlockExact(6)).thenReturn(anyBanner);

        assertTrue(run(player, "mirror", "create"));

        verify(player, atLeastOnce()).sendMessage(contains("create <name>"));
    }

    /**
     * {@code set} on its own, or with a name and nothing after it, says its form.
     *
     * <p>"Thinking getting rid of stamp, mode, display, start from the main submenu and move it
     * to an edit menu?" They are behind {@code set} now, and its form is where they are found.
     */
    @Test
    void setWithoutAPropertySaysItsForm()
    {
        MirrorManager.add(new QuantumMirror("museum", new MirrorBlock("world", 1, 64, 1), null));

        assertTrue(run(player, "mirror", "set"));
        assertTrue(run(player, "mirror", "set", "museum"));

        verify(player, atLeast(2)).sendMessage(contains("set [<name>] <stamp|start|capture>"));
    }

    /** A word that is not one of the properties says so, and then the form. */
    @Test
    void setRefusesAWordAMirrorDoesNotHave()
    {
        MirrorManager.add(new QuantumMirror("museum", new MirrorBlock("world", 1, 64, 1), null));

        assertTrue(run(player, "mirror", "set", "museum", "colour", "blue"));

        verify(player, atLeastOnce()).sendMessage(contains("is not something a mirror has"));
        verify(player, atLeastOnce()).sendMessage(contains("set [<name>] <stamp|start|capture>"));
    }

    /**
     * The properties are not verbs any more: at the top they get the top's usage, which names set.
     *
     * <p>Not silently the old behaviour. A verb that still worked unlisted would be a second way
     * to do everything, and the usage line would be lying about what the command answers to.
     */
    @Test
    void thePropertiesAreNotVerbsAtTheTop()
    {
        MirrorManager.add(new QuantumMirror("museum", new MirrorBlock("world", 1, 64, 1), null));
        MirrorManager.add(new QuantumMirror("hub", new MirrorBlock("world", 5, 64, 5), null));

        assertTrue(run(player, "mirror", "start", "museum", "hub"));

        assertNull(MirrorManager.byName("museum").start(), "nothing changed");
        verify(player, atLeastOnce()).sendMessage(contains("<create|set|remove|list>"));
    }

    /**
     * A mirror cannot be called by one of the property words, since that is how set tells a name
     * from what comes after it: {@code set start hub} would be the banner in front of you, never
     * a mirror called start.
     */
    @Test
    void createRefusesAPropertyWordAsAName()
    {
        final Block inFront = banner(Material.WHITE_WALL_BANNER);
        when(player.getTargetBlockExact(6)).thenReturn(inFront);

        assertTrue(run(player, "mirror", "create", "start"));

        assertNull(MirrorManager.byName("start"), "not made");
        verify(player, atLeastOnce()).sendMessage(contains("cannot be called that"));
    }

    /** A console has no banner in front of it, so capture with no name gets its form. */
    @Test
    void captureWithNoNameFromAConsoleSaysItsForm()
    {
        final CommandSender console = mock(CommandSender.class);
        when(console.isOp()).thenReturn(true);

        assertTrue(run(console, "mirror", "set", "capture"));

        verify(console, atLeastOnce()).sendMessage(contains("set [<name>] capture"));
    }

    /**
     * A room whose world is not loaded cannot be captured, and the reply names the world.
     *
     * <p>The one way a capture request fails: a capture already being taken counts as taken, so
     * the message must not offer that as a reason.
     */
    @Test
    void captureSaysWhichWorldIsNotLoadedWhenTheRoomCannotBeTaken()
    {
        MirrorManager.add(new QuantumMirror("museum", new MirrorBlock("world", 1, 64, 1),
            new MirrorPoint("archive", 0, 64, 0, 0f, 0f)));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("archive")).thenReturn(null);

            assertTrue(run(player, "mirror", "set", "museum", "capture"));
        }

        verify(player, atLeastOnce()).sendMessage(contains("cannot be captured now"));
        verify(player, atLeastOnce()).sendMessage(contains("archive"));
        verify(player, never()).sendMessage(contains("Capturing"));
    }
}
