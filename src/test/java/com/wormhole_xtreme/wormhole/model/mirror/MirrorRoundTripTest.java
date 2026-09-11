package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.File;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.command.handlers.MirrorCommand;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;

/**
 * The whole trip an operator actually makes: overworld banner, nether banner, and back.
 *
 * <p>Everything under test here is covered in pieces elsewhere -- {@code set} binds a banner,
 * {@code link} works out a front, the click handler teleports. What is <em>not</em> covered
 * anywhere else is the four of them in the order somebody does them in, in two worlds, which
 * is the only place a gap between the pieces can show up.
 *
 * <p>Written from a report of the return banner doing nothing at all when clicked. No message
 * means the click never reached the travel path, so the interesting assertion is that the
 * second banner is in the block index at all.
 */
class MirrorRoundTripTest
{
    /** Where the saves go, so nothing lands in the repository. */
    @TempDir
    File dataFolder;

    private Player player;
    private World overworld;
    private World nether;
    private Block overworldBanner;
    private Block netherBanner;

    @BeforeEach
    void setUp() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        PluginTestSupport.install(plugin);
        ConfigTestSupport.clear();
        MirrorManager.clear();

        overworld = world("world");
        nether = world("world_nether");

        overworldBanner = banner(overworld, 10, 64, 10, BlockFace.SOUTH);
        netherBanner = banner(nether, 100, 32, 100, BlockFace.NORTH);

        player = mock(Player.class);
        when(player.isOp()).thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        MirrorManager.clear();
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    /**
     * Bind one banner in each world, link them both ways, and click each.
     *
     * <p>The order is the one an operator uses: name the overworld banner, walk to the nether,
     * name that one, then tie them together.
     */
    @Test
    void bothBannersAreClickableAfterBeingLinkedToEachOther()
    {
        try (final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(overworld);
            bukkit.when(() -> Bukkit.getWorld("world_nether")).thenReturn(nether);

            standingAt(overworld, 10, 64, 12);
            lookingAt(overworldBanner);
            assertTrue(run("mirror", "set", "home"));

            standingAt(nether, 100, 32, 98);
            lookingAt(netherBanner);
            assertTrue(run("mirror", "set", "hell"));

            assertTrue(run("mirror", "link", "home", "hell"));
            assertTrue(run("mirror", "link", "hell", "home"));
        }

        // Both are registered under their own block, which is what the click handler asks.
        assertNotNull(MirrorManager.at(new MirrorBlock("world", 10, 64, 10)),
            "the overworld banner should answer to a click");
        assertNotNull(MirrorManager.at(new MirrorBlock("world_nether", 100, 32, 100)),
            "the nether banner should answer to a click -- this is the reported failure");

        // And both know where they go.
        assertEquals("world_nether", MirrorManager.byName("home").destination().worldName());
        assertEquals("world", MirrorManager.byName("hell").destination().worldName());
    }

    /**
     * One link is enough for a working pair, which is the whole point of the change.
     *
     * <p>It used to point only the named {@code from}, and the commonest way to end up with a
     * banner that did nothing when clicked was to run it once and expect a return trip. One
     * command, both directions.
     */
    @Test
    void oneLinkIsEnoughToMakeBothBannersWork()
    {
        try (final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(overworld);
            bukkit.when(() -> Bukkit.getWorld("world_nether")).thenReturn(nether);

            standingAt(overworld, 10, 64, 12);
            lookingAt(overworldBanner);
            run("mirror", "set", "home");
            standingAt(nether, 100, 32, 98);
            lookingAt(netherBanner);
            run("mirror", "set", "hell");

            assertTrue(run("mirror", "link", "home", "hell"));
        }

        assertNotNull(MirrorManager.byName("home").destination(),
            "the banner named first should open onto the other");
        assertNotNull(MirrorManager.byName("hell").destination(),
            "and so should the one named second -- this is the reported failure");
        assertEquals("world_nether", MirrorManager.byName("home").destination().worldName());
        assertEquals("world", MirrorManager.byName("hell").destination().worldName());
    }

    private boolean run(final String... args)
    {
        return new MirrorCommand().execute(player, args);
    }

    private void standingAt(final World where, final int x, final int y, final int z)
    {
        when(player.getLocation()).thenReturn(new Location(where, x + 0.5, y, z + 0.5));
    }

    private void lookingAt(final Block block)
    {
        when(player.getTargetBlockExact(6)).thenReturn(block);
    }

    private static World world(final String name)
    {
        final World created = mock(World.class);
        when(created.getName()).thenReturn(name);
        when(created.getMinHeight()).thenReturn(-64);
        when(created.getMaxHeight()).thenReturn(320);
        return created;
    }

    /** A wall banner at a spot, facing a way, with the block in front of it reachable. */
    private static Block banner(final World where, final int x, final int y, final int z,
        final BlockFace facing)
    {
        final Directional data = mock(Directional.class);
        when(data.getFacing()).thenReturn(facing);

        final Block block = mock(Block.class);
        when(block.getType()).thenReturn(Material.WHITE_WALL_BANNER);
        when(block.getWorld()).thenReturn(where);
        when(block.getX()).thenReturn(x);
        when(block.getY()).thenReturn(y);
        when(block.getZ()).thenReturn(z);
        when(block.getBlockData()).thenReturn(data);

        final int ax = x + Integer.signum(facing.getModX());
        final int az = z + Integer.signum(facing.getModZ());
        final Block ahead = mock(Block.class);
        when(ahead.getLocation()).thenReturn(new Location(where, ax, y, az));
        when(block.getRelative(Integer.signum(facing.getModX()), 0,
            Integer.signum(facing.getModZ()))).thenReturn(ahead);

        when(where.getBlockAt(x, y, z)).thenReturn(block);
        when(where.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);
        return block;
    }
}
