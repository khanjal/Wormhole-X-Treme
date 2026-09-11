package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

import com.wormhole_xtreme.wormhole.PluginTestSupport;

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
        world = mock(World.class);
        when(world.getName()).thenReturn("world");
    }

    @AfterEach
    void tearDown() throws Exception
    {
        MirrorManager.clear();
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
     * A mirror that has been named but not pointed says so rather than doing nothing.
     *
     * <p>The event is still claimed, so the click does not fall through to placing a block
     * against the banner. "Nothing happened" on a block you just clicked is the least useful
     * answer available.
     */
    @Test
    void clickingAMirrorWithNoDestinationExplainsItself()
    {
        final Block banner = block(Material.WHITE_WALL_BANNER, 5);
        MirrorManager.add(new QuantumMirror("Museum", MirrorBlock.of(banner), null));

        assertTrue(MirrorInteraction.handle(click(banner)), "a mirror claims its own click");
        verify(player, never()).teleport(any(org.bukkit.Location.class));
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
