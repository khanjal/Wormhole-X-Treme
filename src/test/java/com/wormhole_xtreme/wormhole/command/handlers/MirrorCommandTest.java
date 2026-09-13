package com.wormhole_xtreme.wormhole.command.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
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
        return block;
    }

    private static boolean run(final CommandSender sender, final String... args)
    {
        return new MirrorCommand().execute(sender, args);
    }

    /** Naming the banner you are looking at is the first half of binding one. */
    @Test
    void setNamesTheBannerThePlayerIsLookingAt()
    {
        final Block wallBanner = banner(Material.WHITE_WALL_BANNER);
        when(player.getTargetBlockExact(6)).thenReturn(wallBanner);

        assertTrue(run(player, "mirror", "set", "museum"));

        final QuantumMirror mirror = MirrorManager.byName("museum");
        assertNotNull(mirror, "the mirror should exist after set");
        assertEquals(new MirrorBlock("world", 1, 64, 1), mirror.banner());
        assertNull(mirror.destination(), "set names a banner; it does not point it anywhere");
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

        run(player, "mirror", "set", "museum");

        assertNull(MirrorManager.byName("museum"));
        verify(player, atLeastOnce()).sendMessage(contains("not a banner"));
    }

    /** Looking at nothing is its own message, since the fix is different. */
    @Test
    void setRefusesWhenLookingAtNothing()
    {
        when(player.getTargetBlockExact(6)).thenReturn(null);

        run(player, "mirror", "set", "museum");

        assertNull(MirrorManager.byName("museum"));
        verify(player, atLeastOnce()).sendMessage(contains("within six blocks"));
    }

    /** The second half: where you stand becomes where arrivals land. */
    @Test
    void targetPointsAMirrorAtWhereThePlayerStands()
    {
        MirrorManager.add(new QuantumMirror("museum", new MirrorBlock("snapshot", 0, 64, 0), null));

        assertTrue(run(player, "mirror", "target", "museum"));

        final MirrorPoint destination = MirrorManager.byName("museum").destination();
        assertNotNull(destination);
        assertEquals("world", destination.worldName());
        assertEquals(10.0, destination.x());
    }

    /**
     * A mirror whose two ends share a world is refused, and the message names the setting.
     *
     * <p>This is the rule an admin is most likely to hit while doing something perfectly
     * reasonable -- two points in one world is what a beam place is for -- so the refusal has
     * to say both which worlds clashed and what to change if they meant it.
     */
    @Test
    void targetRefusesWhenBothEndsAreInOneWorld()
    {
        MirrorManager.add(new QuantumMirror("museum", new MirrorBlock("world", 0, 64, 0), null));

        run(player, "mirror", "target", "museum");

        assertNull(MirrorManager.byName("museum").destination(),
            "the refusal has to leave the mirror as it was");
        verify(player, atLeastOnce()).sendMessage(contains("mirror-allow-same-world"));
    }

    /** And is allowed once an admin turns the setting on. */
    @Test
    void targetAllowsOneWorldWhenTheSettingSaysSo()
    {
        ConfigTestSupport.set(
            com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys.MIRROR_ALLOW_SAME_WORLD,
            true);
        MirrorManager.add(new QuantumMirror("museum", new MirrorBlock("world", 0, 64, 0), null));

        run(player, "mirror", "target", "museum");

        assertNotNull(MirrorManager.byName("museum").destination(),
            "the setting exists precisely so this case can be allowed");
    }

    /** Naming a mirror that does not exist says so rather than creating one. */
    @Test
    void targetRefusesAnUnknownName()
    {
        run(player, "mirror", "target", "nothing-by-that-name");

        verify(player, atLeastOnce()).sendMessage(contains("no mirror called"));
    }

    /** A mirror cannot open onto itself; the arrival would be where you already are. */
    @Test
    void linkRefusesAMirrorPointedAtItself()
    {
        MirrorManager.add(new QuantumMirror("museum", new MirrorBlock("world", 0, 64, 0), null));

        run(player, "mirror", "link", "museum", "MUSEUM");

        verify(player, atLeastOnce()).sendMessage(contains("cannot open onto itself"));
    }

    /**
     * Linking ties two mirrors together, each opening onto the front of the other's banner.
     *
     * <p>The whole reason {@code link} exists: two commands instead of walking to both ends
     * and running {@code target} at each. What it stores is a pair of ordinary points, so
     * nothing downstream knows a second mirror was involved -- which is also why moving either
     * banner afterwards does not follow.
     *
     * <p>Both ways since two-way linking replaced one-way. Pointing only the first was the
     * commonest way to end up with a banner that did nothing when clicked, because the
     * argument order is invisible once you have walked away from it.
     */
    @Test
    void linkTiesTwoMirrorsTogetherBothWays()
    {
        final World snapshot = mock(World.class);
        when(snapshot.getName()).thenReturn("snapshot");

        final Directional farFacing = mock(Directional.class);
        when(farFacing.getFacing()).thenReturn(BlockFace.SOUTH);
        final Block farBanner = mock(Block.class);
        when(farBanner.getBlockData()).thenReturn(farFacing);
        when(farBanner.getLocation()).thenReturn(new Location(snapshot, 5.0, 64.0, 5.0));
        when(snapshot.getBlockAt(5, 64, 5)).thenReturn(farBanner);

        final Directional nearFacing = mock(Directional.class);
        when(nearFacing.getFacing()).thenReturn(BlockFace.NORTH);
        final Block nearBanner = mock(Block.class);
        when(nearBanner.getBlockData()).thenReturn(nearFacing);
        when(nearBanner.getLocation()).thenReturn(new Location(here, 0.0, 64.0, 0.0));
        when(here.getBlockAt(0, 64, 0)).thenReturn(nearBanner);

        MirrorManager.add(new QuantumMirror("lobby", new MirrorBlock("world", 0, 64, 0), null));
        MirrorManager.add(new QuantumMirror("museum", new MirrorBlock("snapshot", 5, 64, 5), null));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("snapshot")).thenReturn(snapshot);
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(here);

            assertTrue(run(player, "mirror", "link", "lobby", "museum"));
        }

        final MirrorPoint outbound = MirrorManager.byName("lobby").destination();
        assertNotNull(outbound, "lobby should open onto the front of museum's banner");
        assertEquals("snapshot", outbound.worldName());
        assertEquals(5.5, outbound.x(), 0.001, "centred in the banner's own block");
        assertEquals(5.5, outbound.z(), 0.001);
        assertEquals(0.0f, outbound.yaw(), 0.01f, "facing the way that banner faces");

        final MirrorPoint back = MirrorManager.byName("museum").destination();
        assertNotNull(back, "and museum should open back onto the front of lobby's banner");
        assertEquals("world", back.worldName());
        assertEquals(0.5, back.x(), 0.001);
        assertEquals(0.5, back.z(), 0.001);
        assertEquals(180.0f, back.yaw(), 0.01f);
    }

    /**
     * Linking to a mirror whose world is not loaded says so.
     *
     * <p>The facing has to be read off the live block -- it is recorded nowhere else -- so a
     * mirror in a world that is not up cannot be the target of a link. It can still be the
     * source of one, which is why the message names the world rather than refusing the mirror.
     */
    @Test
    void linkRefusesWhenTheTargetsWorldIsNotLoaded()
    {
        MirrorManager.add(new QuantumMirror("lobby", new MirrorBlock("world", 0, 64, 0), null));
        MirrorManager.add(new QuantumMirror("museum", new MirrorBlock("archive", 5, 64, 5), null));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("archive")).thenReturn(null);

            run(player, "mirror", "link", "lobby", "museum");
        }

        assertNull(MirrorManager.byName("lobby").destination());
        verify(player, atLeastOnce()).sendMessage(contains("archive"));
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

    /** An empty list says how to make one rather than printing nothing. */
    @Test
    void listOnAServerWithNoMirrorsExplainsHowToMakeOne()
    {
        assertTrue(run(player, "mirror", "list"));

        verify(player, atLeastOnce()).sendMessage(contains("No mirrors yet"));
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
     * set and target need a player, because both depend on where somebody is.
     *
     * <p>Run from the console they would have no banner to look at and nowhere to stand, so
     * they say that rather than throwing a ClassCastException into the server log.
     */
    @Test
    void theVerbsThatNeedAPlayerSayWhenRunFromTheConsole()
    {
        final CommandSender console = mock(CommandSender.class);

        assertTrue(run(console, "mirror", "set", "museum"));
        assertTrue(run(console, "mirror", "target", "museum"));

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
    void setBindsAFreestandingBannerTheRayTraceMissed()
    {
        final Block post = banner(Material.WHITE_BANNER);
        when(player.getTargetBlockExact(6)).thenReturn(null);
        when(player.getLineOfSight(null, 6)).thenReturn(java.util.List.of(post));

        assertTrue(run(player, "mirror", "set", "Post"));

        assertNotNull(MirrorManager.byName("Post"), "the banner on a post should have been named");
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
    void namingABannerOnAPostSaysToClickItsBase()
    {
        final Block post = banner(Material.WHITE_BANNER);
        when(player.getTargetBlockExact(6)).thenReturn(post);

        assertTrue(run(player, "mirror", "set", "Post"));

        verify(player, atLeastOnce()).sendMessage(contains("click near its base"));
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

        assertTrue(run(player, "mirror", "set", "Hanging"));

        verify(player, never()).sendMessage(contains("click near its base"));
    }

    /** A verb that needs a name and was not given one says which form it wanted. */
    @Test
    void aVerbMissingItsNameGetsThatVerbsUsage()
    {
        final Block anyBanner = banner(Material.WHITE_WALL_BANNER);
        when(player.getTargetBlockExact(6)).thenReturn(anyBanner);

        assertTrue(run(player, "mirror", "set"));

        verify(player, atLeastOnce()).sendMessage(contains("set <name>"));
    }
}
