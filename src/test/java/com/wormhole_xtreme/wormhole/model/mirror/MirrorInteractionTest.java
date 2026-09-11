package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;

/**
 * Clicking a banner, and — mostly — clicking everything that is not one.
 *
 * <p>This handler runs on every right-click of every block on the server, so the case that
 * matters most is the one that happens millions of times and must do almost nothing. The
 * ordering inside it is a performance decision with a test of its own in
 * {@code InteractLoggingCostTest}, which fails if this path so much as asks a block for its
 * world; the tests here pin the behaviour that ordering has to preserve.
 */
class MirrorInteractionTest
{
    private Player player;
    private World world;

    @BeforeEach
    void setUp() throws Exception
    {
        // Reached as soon as a click lands on a bound mirror: the permission check logs
        // through the plugin singleton, so without one these tests fail on an NPE from inside
        // WXPermissions rather than on anything they are actually about.
        PluginTestSupport.install(mock(com.wormhole_xtreme.wormhole.WormholeXTreme.class));
        MirrorManager.clear();
        player = mock(Player.class);
        // Travel is behind the USE node. Without this the handler refuses on permission and
        // returns before reaching anything below -- which still claims the click and still
        // does not teleport, so a test asserting only those two would pass for the wrong
        // reason and never exercise the path it names.
        when(player.isOp()).thenReturn(true);
        world = mock(World.class);
        when(world.getName()).thenReturn("world");
    }

    @AfterEach
    void tearDown() throws Exception
    {
        MirrorManager.clear();
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    private Block block(final Material type, final int x)
    {
        final Block block = mock(Block.class);
        when(block.getType()).thenReturn(type);
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(x);
        when(block.getY()).thenReturn(64);
        when(block.getZ()).thenReturn(0);
        return block;
    }

    private PlayerInteractEvent click(final Block block)
    {
        return new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null, block, BlockFace.UP);
    }

    /**
     * An ordinary block is not a mirror, and finding that out must not touch the world.
     *
     * <p>The whole-server hot path. Asking the registry means building a key, and building a
     * key means {@code getWorld()} -- so the block's type is checked first and a stone block
     * never gets that far. Verifying the absence here is the point: it is the only way to
     * tell "answered no cheaply" from "answered no expensively".
     */
    @Test
    void clickingAnOrdinaryBlockIsNotAMirrorAndDoesNotAskItsWorld()
    {
        final Block stone = block(Material.STONE, 5);

        assertFalse(MirrorInteraction.handle(click(stone)));

        verify(stone, never()).getWorld();
        verify(player, never()).teleport(any(org.bukkit.Location.class));
    }

    /** A banner that nobody has bound is still just a banner. */
    @Test
    void clickingAnUnboundBannerDoesNothing()
    {
        assertFalse(MirrorInteraction.handle(click(block(Material.WHITE_WALL_BANNER, 5))));

        verify(player, never()).teleport(any(org.bukkit.Location.class));
    }

    /**
     * Both banner families are recognised.
     *
     * <p>Sixteen wall banners and sixteen freestanding ones, one of each per dye colour.
     * Recognising only the wall family would work on every test server built against a wall
     * and fail on every banner on a post -- which is most of a museum corridor. Checked on a
     * freestanding banner of a colour nobody would pick first, so a hardcoded list would have
     * to be genuinely complete rather than merely plausible.
     */
    @Test
    void aFreestandingBannerOfAnyColourIsRecognised()
    {
        final Block banner = block(Material.MAGENTA_BANNER, 5);
        MirrorManager.add(new QuantumMirror("Museum", MirrorBlock.of(banner), null));

        assertTrue(MirrorInteraction.handle(click(banner)),
            "a freestanding banner is a mirror as much as a wall-mounted one");
    }

    /**
     * A mirror that has been named but not pointed says so, and says what to do about it.
     *
     * <p>The event is still claimed, so the click does not fall through to placing a block
     * against the banner. "Nothing happened" on a block you just clicked is the least useful
     * answer available -- and "this does not go anywhere yet", while true, is the second least,
     * because it is said at the one moment somebody has demonstrated they want this banner to
     * work and is standing in front of it.
     *
     * <p>Both routes, because they answer different questions: {@code link} for a banner at the
     * far end, {@code target} for arriving somewhere with no banner at all. The mirror's own
     * name goes in both, so the line can be typed as it stands.
     */
    @Test
    void clickingAMirrorWithNoDestinationSaysHowToPointIt()
    {
        final Block banner = block(Material.WHITE_WALL_BANNER, 5);
        MirrorManager.add(new QuantumMirror("Museum", MirrorBlock.of(banner), null));

        assertTrue(MirrorInteraction.handle(click(banner)), "a mirror claims its own click");
        verify(player, never()).teleport(any(org.bukkit.Location.class));
        verify(player, atLeastOnce()).sendMessage(contains("does not open onto anywhere yet"));
        verify(player, atLeastOnce()).sendMessage(contains(
            MirrorText.COMMAND + "/wormhole mirror link " + MirrorText.NAME + "Museum"));
        verify(player, atLeastOnce()).sendMessage(contains(
            MirrorText.COMMAND + "/wormhole mirror target " + MirrorText.NAME + "Museum"));
    }

    /**
     * A visitor who could not run those commands is not given them.
     *
     * <p>Handing somebody two commands they have no permission for reads as the plugin telling
     * them to do something, and they would be right to try. They get the plain sentence, which
     * still beats a click that does nothing.
     *
     * <p>Simple mode on purpose: it is the arrangement where a player may travel but not
     * configure, which is exactly the split being tested. With a permissions plugin the same
     * player would fail the USE check first and never reach this line.
     */
    @Test
    void aPlayerWhoCannotConfigureIsNotToldToRunCommands()
    {
        ConfigTestSupport.set(ConfigManager.ConfigKeys.PERMISSIONS_SUPPORT_DISABLE, true);
        when(player.isOp()).thenReturn(false);
        final Block banner = block(Material.WHITE_WALL_BANNER, 5);
        MirrorManager.add(new QuantumMirror("Museum", MirrorBlock.of(banner), null));

        assertTrue(MirrorInteraction.handle(click(banner)), "a mirror still claims its click");
        verify(player, atLeastOnce()).sendMessage(contains("does not open onto anywhere yet"));
        verify(player, never()).sendMessage(contains("/wormhole mirror"));
    }

    /**
     * A left click is not a use.
     *
     * <p>Breaking a banner starts with hitting it, and a mirror that teleported on the first
     * swing would be impossible to take down.
     */
    @Test
    void hittingAMirrorIsNotClickingIt()
    {
        final Block banner = block(Material.WHITE_WALL_BANNER, 5);
        MirrorManager.add(new QuantumMirror("Museum", MirrorBlock.of(banner), null));

        assertFalse(MirrorInteraction.handle(new PlayerInteractEvent(
            player, Action.LEFT_CLICK_BLOCK, null, banner, BlockFace.UP)));
        verify(player, never()).teleport(any(org.bukkit.Location.class));
    }

    /**
     * A mirror whose far side is in an unloaded world names the world rather than failing.
     *
     * <p>The most likely way a working mirror stops working: the archive world it opens onto
     * is not started this session. Every store in this plugin resolves a world by name, so
     * this is also what a world renamed out from under it looks like -- and either way the
     * mirror stays in the registry and refuses, rather than being dropped.
     */
    @Test
    void clickingAMirrorIntoAnUnloadedWorldNamesTheWorld()
    {
        final Block banner = block(Material.WHITE_WALL_BANNER, 5);
        MirrorManager.add(new QuantumMirror("Museum", MirrorBlock.of(banner),
            new MirrorPoint("a_world_nobody_started", 0, 64, 0, 0, 0)));

        // MirrorPoint resolves its world through Bukkit, so the static has to answer for the
        // "not loaded" branch to be reachable at all -- the same idiom OwnerCommandTest uses.
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("a_world_nobody_started")).thenReturn(null);

            assertTrue(MirrorInteraction.handle(click(banner)), "the mirror still claims the click");
        }

        verify(player, never()).teleport(any(org.bukkit.Location.class));
        verify(player, atLeastOnce()).sendMessage(contains("a_world_nobody_started"));
    }

    /** Clicking the air has no block to look up. */
    @Test
    void clickingTheAirIsNotAMirror()
    {
        assertFalse(MirrorInteraction.handle(new PlayerInteractEvent(
            player, Action.RIGHT_CLICK_AIR, null, null, BlockFace.UP)));
    }

    /** Nothing to handle at all. */
    @Test
    void aNullEventIsNotAMirror()
    {
        assertFalse(MirrorInteraction.handle(null));
    }
}
