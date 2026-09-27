package com.wormhole_xtreme.wormhole.command.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Slab;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.GateSpatialIndex;
import com.wormhole_xtreme.wormhole.model.ring.Ring;
import com.wormhole_xtreme.wormhole.model.ring.RingAccess;
import com.wormhole_xtreme.wormhole.model.ring.RingManager;
import com.wormhole_xtreme.wormhole.model.ring.RingPair;
import com.wormhole_xtreme.wormhole.model.ring.RingPattern;
import com.wormhole_xtreme.wormhole.model.ring.RingPermissions;
import com.wormhole_xtreme.wormhole.model.ring.RingStyle;
import com.wormhole_xtreme.wormhole.model.ring.RingTransit;
import com.wormhole_xtreme.wormhole.model.ring.RingYamlManager;

/**
 * Pairing and firing rings by coordinates and id, with nobody standing in them.
 *
 * <p>For the console, command blocks and scripts: an adventure map that fires a ring from a pressure
 * plate, or a boot test with no player on the server. The circles are checked exactly as
 * {@code ring create} checks them, and a line that is wrong changes nothing: a pair half made, with one
 * circle's slabs taken up, is worse than a refusal.
 */
class RingConsoleCommandsTest
{
    private static final String WORLD = "world";
    private static final int X = 100;
    private static final int Y = 64;
    private static final int NEAR_Z = 100;
    private static final int FAR_Z = 120;

    private World world;
    private final Map<String, Block> blocks = new HashMap<>();
    private final CommandSender console = mock(ConsoleCommandSender.class);
    private MockedStatic<ConfigManager> config;
    private MockedStatic<RingYamlManager> yaml;
    private MockedStatic<Bukkit> bukkit;

    @BeforeEach
    void setUp() throws Exception
    {
        RingManager.clear();
        GateSpatialIndex.clear();
        PluginTestSupport.install();

        world = mock(World.class);
        when(world.getName()).thenReturn(WORLD);
        when(world.getMinHeight()).thenReturn(Integer.valueOf(-64));
        when(world.getMaxHeight()).thenReturn(Integer.valueOf(320));
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(inv -> blockAt(
            inv.getArgument(0, Integer.class).intValue(),
            inv.getArgument(1, Integer.class).intValue(),
            inv.getArgument(2, Integer.class).intValue()));

        config = mockStatic(ConfigManager.class);
        config.when(ConfigManager::getRingReach).thenReturn(Integer.valueOf(5));
        config.when(ConfigManager::getRingDefaultLight).thenReturn(Material.GLOWSTONE);
        config.when(ConfigManager::getRingMinSeparation).thenReturn(Integer.valueOf(0));
        config.when(ConfigManager::getRingMaxCeilingDrop).thenReturn(Integer.valueOf(10));
        // Private, so a pair left at the default would let no player into an ownerless pair.
        config.when(ConfigManager::getRingDefaultAccess).thenReturn(RingAccess.PRIVATE);
        config.when(ConfigManager::getRingDefaultStyle).thenReturn(RingStyle.SEQUENTIAL);
        config.when(ConfigManager::getRingDefaultFlash).thenReturn(Material.GLOWSTONE);
        yaml = mockStatic(RingYamlManager.class);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.getWorld(WORLD)).thenReturn(world);

        layRingAt(X, Y, NEAR_Z);
        layRingAt(X, Y, FAR_Z);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        bukkit.close();
        yaml.close();
        config.close();
        GateSpatialIndex.clear();
        RingManager.clear();
        PluginTestSupport.remove();
    }

    /** Air unless something has been laid there. */
    private Block blockAt(final int x, final int y, final int z)
    {
        return blocks.computeIfAbsent(x + "," + y + "," + z, key -> {
            final Block b = mock(Block.class);
            when(b.getType()).thenReturn(Material.AIR);
            when(b.isPassable()).thenReturn(Boolean.TRUE);
            when(b.getLocation()).thenReturn(new Location(world, x, y, z));
            when(b.getWorld()).thenReturn(world);
            when(b.getX()).thenReturn(Integer.valueOf(x));
            when(b.getY()).thenReturn(Integer.valueOf(y));
            when(b.getZ()).thenReturn(Integer.valueOf(z));
            return b;
        });
    }

    /** A circle of bottom slabs on solid ground, which the detector reads as a floor ring. */
    private void layRingAt(final int ax, final int ay, final int az)
    {
        for (final RingPattern.Offset offset : RingPattern.ODD.getPerimeter())
        {
            final Block b = blockAt(ax + offset.getDx(), ay, az + offset.getDz());
            final Slab slab = mock(Slab.class);
            when(slab.getType()).thenReturn(Slab.Type.BOTTOM);
            when(b.getType()).thenReturn(Material.STONE_SLAB);
            when(b.getBlockData()).thenReturn(slab);
            when(b.isPassable()).thenReturn(Boolean.FALSE);
        }
        for (final RingPattern.Offset offset : RingPattern.ODD.getInterior())
        {
            final Block below = blockAt(ax + offset.getDx(), ay - 1, az + offset.getDz());
            when(below.getType()).thenReturn(Material.STONE);
            when(below.isPassable()).thenReturn(Boolean.FALSE);
        }
    }

    /** One slab of the circle anchored there. */
    private Block slabOf(final int az)
    {
        final RingPattern.Offset first = RingPattern.ODD.getPerimeter().get(0);
        return blockAt(X + first.getDx(), Y, az + first.getDz());
    }

    private static void run(final CommandSender sender, final String... args)
    {
        new RingCommand().execute(sender, args);
    }

    private List<RingPair> pairs()
    {
        return RingManager.getPairsInWorld(WORLD);
    }

    /**
     * Two laid circles named from the console become a live pair, public because nobody owns it, with
     * both templates taken up and saved, and the reply says where each end puts an arrival.
     */
    @Test
    void theConsolePairsTwoLaidCirclesIntoAPublicOwnerlessPair()
    {
        run(console, "ring", "build", WORLD, "100", "64", "100", "100", "64", "120");

        assertEquals(1, pairs().size());
        final RingPair pair = pairs().get(0);
        assertEquals(RingAccess.PUBLIC, pair.getAccess(), "not the PRIVATE default, which would let nobody in");
        assertNull(pair.getOwner());
        verify(slabOf(NEAR_Z)).setType(Material.AIR, false);
        verify(slabOf(FAR_Z)).setType(Material.AIR, false);
        yaml.verify(() -> RingYamlManager.saveWorld(WORLD));
        verify(console).sendMessage(contains("Ring pair " + pair.getId() + " is live and public. Arrivals at"
            + " 100.5 64 100.5 and 100.5 64 120.5. Fire it with /wormhole ring fire " + pair.getId() + "."));
    }

    /** A command block's ~ counts from its own block, so a map names both circles relative to itself. */
    @Test
    void aCommandBlockNamesBothCirclesRelativeToItself()
    {
        final BlockCommandSender commandBlock = mock(BlockCommandSender.class);
        final Block itsBlock = mock(Block.class);
        when(commandBlock.getBlock()).thenReturn(itsBlock);
        when(itsBlock.getLocation()).thenReturn(new Location(world, 100, 63, 110));

        run(commandBlock, "ring", "build", WORLD, "~", "~1", "~-10", "~", "~1", "~10");

        assertEquals(1, pairs().size());
        final Ring near = pairs().get(0).getEndA();
        assertEquals(NEAR_Z, near.getAnchorZ());
    }

    /** With no circle at the second point, the first circle's slabs are left where they lie. */
    @Test
    void aSecondPointWithNoCircleIsRefusedAndTheFirstIsLeftAlone()
    {
        run(console, "ring", "build", WORLD, "100", "64", "100", "100", "64", "140");

        verify(console).sendMessage("No ring of slabs here. Lay a circle of slabs and stand inside it. (at 100 64 140)");
        assertTrue(pairs().isEmpty());
        verify(slabOf(NEAR_Z), never()).setType(any(Material.class), anyBoolean());
    }

    /** The pairing rules are the ones ring create keeps: here, how far apart the two may be. */
    @Test
    void twoCirclesFurtherApartThanRingsReachAreRefused()
    {
        config.when(ConfigManager::getRingMaxLinkDistance).thenReturn(Integer.valueOf(10));

        run(console, "ring", "build", WORLD, "100", "64", "100", "100", "64", "120");

        verify(console).sendMessage("Those two rings are 20 blocks apart on the ground, and rings reach 10.");
        assertTrue(pairs().isEmpty());
    }

    @Test
    void aLineThatCannotBeReadIsRefusedBeforeAnyBlockIsLookedAt()
    {
        run(console, "ring", "build", WORLD, "100", "64", "100", "100", "64");
        run(console, "ring", "build", "nether", "100", "64", "100", "100", "64", "120");
        run(console, "ring", "build", WORLD, "100", "64", "100", "~", "64", "120");

        verify(console).sendMessage("Usage: " + RingConsoleCommands.BUILD_USAGE);
        verify(console).sendMessage("No world called nether is loaded.");
        verify(console).sendMessage("~ counts from a command block or player; from here, give whole numbers.");
        assertTrue(pairs().isEmpty());
    }

    /** A player needs ring admin, since the pair is built anywhere, owned by nobody, and fired by id. */
    @Test
    void aPlayerNeedsRingAdminAndWithItPairs()
    {
        final Player player = mock(Player.class);
        when(player.hasPermission(anyString())).thenReturn(Boolean.FALSE);

        run(player, "ring", "build", WORLD, "100", "64", "100", "100", "64", "120");

        verify(player).sendMessage(contains("needs " + RingPermissions.ADMIN));
        assertTrue(pairs().isEmpty());

        when(player.hasPermission(RingPermissions.ADMIN)).thenReturn(Boolean.TRUE);
        run(player, "ring", "build", WORLD, "100", "64", "100", "100", "64", "120");

        assertEquals(1, pairs().size());
    }

    /** Firing starts the pair's cycle with nobody arming it, and says whether it did. */
    @Test
    void firingStartsThePairsCycleWithNobodyArmingIt()
    {
        run(console, "ring", "build", WORLD, "100", "64", "100", "100", "64", "120");
        final RingPair pair = pairs().get(0);
        try (MockedStatic<RingTransit> transit = mockStatic(RingTransit.class))
        {
            transit.when(() -> RingTransit.start(pair, null, false)).thenReturn(Boolean.TRUE);

            run(console, "ring", "fire", pair.getId());

            transit.verify(() -> RingTransit.start(pair, null, false));
            verify(console).sendMessage("Ring pair " + pair.describe() + " is counting down.");

            transit.when(() -> RingTransit.start(pair, null, false)).thenReturn(Boolean.FALSE);
            run(console, "ring", "fire", pair.getId());

            verify(console).sendMessage(contains("did not fire"));
        }
    }

    /**
     * A block inside either end, or in its circle, fires the pair, so a map's command block fires the
     * ring beside it without knowing the id it was given where it was built.
     */
    @Test
    void firingByABlockInsideEitherEndFiresThatPair()
    {
        run(console, "ring", "build", WORLD, "100", "64", "100", "100", "64", "120");
        final RingPair pair = pairs().get(0);
        final BlockCommandSender commandBlock = mock(BlockCommandSender.class);
        final Block itsBlock = mock(Block.class);
        when(commandBlock.getBlock()).thenReturn(itsBlock);
        when(itsBlock.getLocation()).thenReturn(new Location(world, 102, 63, 120));
        try (MockedStatic<RingTransit> transit = mockStatic(RingTransit.class))
        {
            transit.when(() -> RingTransit.start(pair, null, false)).thenReturn(Boolean.TRUE);

            run(console, "ring", "fire", WORLD, "101", "65", "99");
            run(commandBlock, "ring", "fire", WORLD, "~-1", "~1", "~");
            // Where a slab of the circle lay, which is not inside the ring but is still the ring.
            run(console, "ring", "fire", WORLD, "103", "64", "120");

            transit.verify(() -> RingTransit.start(pair, null, false), times(3));
        }
        run(console, "ring", "fire", WORLD, "100", "64", "110");
        verify(console).sendMessage("No ring at 100 64 110 in world.");
    }

    @Test
    void firingAPairThatIsNotThereOrWithoutAnIdIsRefused()
    {
        run(console, "ring", "fire", "nothere");
        run(console, "ring", "fire");

        verify(console).sendMessage("No ring pair nothere.");
        verify(console).sendMessage("Usage: " + RingConsoleCommands.FIRE_USAGE);
    }

    /** The other verbs are still a player's, and the console is told which ones it can use. */
    @Test
    void theConsoleIsPointedAtBuildAndFireForTheOtherVerbs()
    {
        run(console, "ring", "create");

        verify(console).sendMessage(contains(RingConsoleCommands.BUILD_USAGE));
    }

    /**
     * A player without ring admin does not get it by running the command through /execute as
     * themselves, while a command block running it as a player is still trusted.
     */
    @Test
    void executeAsDoesNotLendAPlayerRingAdmin()
    {
        final Player player = mock(Player.class);
        when(player.hasPermission(anyString())).thenReturn(Boolean.FALSE);
        final org.bukkit.command.ProxiedCommandSender asThemselves = mock(org.bukkit.command.ProxiedCommandSender.class);
        when(asThemselves.getCaller()).thenReturn(player);
        when(asThemselves.getCallee()).thenReturn(player);

        run(asThemselves, "ring", "build", WORLD, "100", "64", "100", "100", "64", "120");

        verify(asThemselves).sendMessage(contains("needs " + RingPermissions.ADMIN));
        assertTrue(pairs().isEmpty());

        final org.bukkit.command.ProxiedCommandSender fromAMap = mock(org.bukkit.command.ProxiedCommandSender.class);
        final BlockCommandSender commandBlock = mock(BlockCommandSender.class);
        when(fromAMap.getCaller()).thenReturn(commandBlock);
        when(fromAMap.getCallee()).thenReturn(player);
        run(fromAMap, "ring", "build", WORLD, "100", "64", "100", "100", "64", "120");

        assertEquals(1, pairs().size());
    }
}
