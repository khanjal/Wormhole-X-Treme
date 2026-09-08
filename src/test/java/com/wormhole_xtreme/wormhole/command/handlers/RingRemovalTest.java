package com.wormhole_xtreme.wormhole.command.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Slab;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.ring.Ring;
import com.wormhole_xtreme.wormhole.model.ring.RingManager;
import com.wormhole_xtreme.wormhole.model.ring.RingOrientation;
import com.wormhole_xtreme.wormhole.model.ring.RingPair;
import com.wormhole_xtreme.wormhole.model.ring.RingPattern;
import com.wormhole_xtreme.wormhole.model.ring.RingYamlManager;

/**
 * Removing a ring pair, and getting the slabs back.
 *
 * <p>Building a pair takes the player's slabs; removing it lays them out again as the ring they
 * were, which is also the template for building the same ring somewhere else. Nobody should have
 * to re-mine a circle they already paid for.
 *
 * <p>The rule that makes this safe rather than destructive was covered by nothing: a slab goes
 * back <em>only</em> where the block is now air. Somebody has built over half an old ring by the
 * time they remove it more often than not, and laying a slab through their wall to be tidy is a
 * far worse outcome than making them mine one more.
 */
class RingRemovalTest
{
    private static final String WORLD = "world";
    private static final String PAIR_ID = "aaaa0001";
    private static final String OWNER = "069a79f4-44e9-4726-a5be-fca90e38aaf5";

    private Player owner;
    private World world;
    private RingPair pair;
    private final Map<String, Block> blocks = new HashMap<>();
    private MockedStatic<RingYamlManager> yaml;
    private MockedStatic<ConfigManager> config;
    private MockedStatic<Bukkit> bukkit;

    @BeforeEach
    void setUp()
    {
        RingManager.clear();
        blocks.clear();

        world = mock(World.class);
        when(world.getName()).thenReturn(WORLD);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(inv -> blockAt(
            inv.getArgument(0, Integer.class).intValue(),
            inv.getArgument(1, Integer.class).intValue(),
            inv.getArgument(2, Integer.class).intValue()));

        owner = mock(Player.class);
        when(owner.getName()).thenReturn("Justin");
        when(owner.getUniqueId()).thenReturn(UUID.fromString(OWNER));
        when(owner.getWorld()).thenReturn(world);
        when(owner.getLocation()).thenReturn(new Location(world, 500, 64, 500));
        when(owner.isOp()).thenReturn(Boolean.FALSE);
        when(owner.hasPermission(anyString())).thenReturn(Boolean.FALSE);

        pair = registeredPair(RingOrientation.FLOOR);

        yaml = mockStatic(RingYamlManager.class);
        config = mockStatic(ConfigManager.class);
        config.when(ConfigManager::getRingReach).thenReturn(Integer.valueOf(4));

        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.getWorld(WORLD)).thenReturn(world);
        // Every slab the restore lays is a fresh BlockData asked of the server.
        bukkit.when(() -> Bukkit.createBlockData(Material.STONE_SLAB))
            .thenAnswer(inv -> mock(Slab.class));
    }

    @AfterEach
    void tearDown()
    {
        bukkit.close();
        config.close();
        yaml.close();
        RingManager.clear();
    }

    /** Air unless a test has put something there. */
    private Block blockAt(final int x, final int y, final int z)
    {
        return blocks.computeIfAbsent(x + "," + y + "," + z, key -> {
            final Block b = mock(Block.class);
            when(b.getType()).thenReturn(Material.AIR);
            when(b.getLocation()).thenReturn(new Location(world, x, y, z));
            return b;
        });
    }

    private RingPair registeredPair(final RingOrientation orientation)
    {
        final Ring a = new Ring(0, 64, 0, RingPattern.ODD, orientation,
            Material.STONE_SLAB, Material.GLOWSTONE);
        final Ring b = new Ring(200, 64, 200, RingPattern.ODD, orientation,
            Material.STONE_SLAB, Material.GLOWSTONE);
        final RingPair made = new RingPair(PAIR_ID, WORLD, a, b);
        made.setOwner(OWNER);
        made.setOwnerName("Justin");
        RingManager.addPair(made, 4);
        return made;
    }

    private boolean remove(final String... rest)
    {
        final String[] args = new String[rest.length + 2];
        args[0] = "ring";
        args[1] = "remove";
        System.arraycopy(rest, 0, args, 2, rest.length);
        return new RingCommand().execute(owner, args);
    }

    /** How many blocks of a ring's footprint had a slab laid into them. */
    private int slabsLaidOn(final Ring ring)
    {
        int laid = 0;
        for (final int[] b : ring.perimeterBlocks())
        {
            final Block at = blockAt(b[0], b[1], b[2]);
            if (org.mockito.Mockito.mockingDetails(at).getInvocations().stream()
                .anyMatch(i -> "setBlockData".equals(i.getMethod().getName())))
            {
                laid++;
            }
        }
        return laid;
    }

    /** Removing a pair takes it out of the registry and writes the world out. */
    @Test
    void removingAPairTakesItOutAndWritesTheWorld()
    {
        assertTrue(remove(PAIR_ID));

        assertNull(RingManager.getPair(PAIR_ID), "the pair is gone");
        yaml.verify(() -> RingYamlManager.saveWorld(WORLD));
    }

    /**
     * Both ends' slabs are laid back out, and the player is told how many.
     *
     * <p>Both, not just the one they were standing in: a pair is two circles of slabs and they
     * paid for both.
     */
    @Test
    void bothEndsSlabsComeBackAndTheCountIsReported()
    {
        remove(PAIR_ID);

        final int expected = pair.getEndA().perimeterBlocks().size()
            + pair.getEndB().perimeterBlocks().size();
        assertEquals(pair.getEndA().perimeterBlocks().size(), slabsLaidOn(pair.getEndA()),
            "the end they were standing in");
        assertEquals(pair.getEndB().perimeterBlocks().size(), slabsLaidOn(pair.getEndB()),
            "and the far one");
        verify(owner).sendMessage(contains("put " + expected + " slabs back"));
    }

    /**
     * A slab goes back only where the block is now air.
     *
     * <p>The rule that makes this safe. Somebody has usually built over part of an old ring by
     * the time they remove it, and laying a slab through their wall to be tidy is a far worse
     * outcome than making them mine one more.
     */
    @Test
    void nothingIsLaidThroughWhatSomebodyHasBuiltSince()
    {
        final int[] taken = pair.getEndA().perimeterBlocks().get(0);
        final Block built = blockAt(taken[0], taken[1], taken[2]);
        when(built.getType()).thenReturn(Material.OAK_PLANKS);

        remove(PAIR_ID);

        verify(built, never()).setBlockData(org.mockito.ArgumentMatchers.any(), anyBoolean());
        assertEquals(pair.getEndA().perimeterBlocks().size() - 1, slabsLaidOn(pair.getEndA()),
            "every other block of that end still got its slab");
    }

    /** And the count the player is told reflects what actually went back. */
    @Test
    void theCountReportedIsWhatActuallyWentBack()
    {
        final int[] taken = pair.getEndA().perimeterBlocks().get(0);
        when(blockAt(taken[0], taken[1], taken[2]).getType()).thenReturn(Material.OAK_PLANKS);

        remove(PAIR_ID);

        final int expected = pair.getEndA().perimeterBlocks().size()
            + pair.getEndB().perimeterBlocks().size() - 1;
        verify(owner).sendMessage(contains("put " + expected + " slabs back"));
    }

    /**
     * A ceiling ring's slabs go back as top slabs.
     *
     * <p>A ceiling ring hangs from the ceiling, so its template is top slabs. Laid back as
     * bottom ones they would sit on the floor of the room above, which is not where the ring
     * was and not a template for rebuilding it.
     */
    @Test
    void aCeilingRingsSlabsGoBackTheWayUpTheyWere()
    {
        RingManager.clear();
        pair = registeredPair(RingOrientation.CEILING);

        remove(PAIR_ID);

        final int[] first = pair.getEndA().perimeterBlocks().get(0);
        final Block at = blockAt(first[0], first[1], first[2]);
        final org.mockito.ArgumentCaptor<org.bukkit.block.data.BlockData> laid =
            org.mockito.ArgumentCaptor.forClass(org.bukkit.block.data.BlockData.class);
        verify(at).setBlockData(laid.capture(), eq(false));
        verify((Slab) laid.getValue()).setType(Slab.Type.TOP);
    }

    /** A floor ring's go back as bottom slabs, which is the other half of the same rule. */
    @Test
    void aFloorRingsSlabsGoBackAsBottomSlabs()
    {
        remove(PAIR_ID);

        final int[] first = pair.getEndA().perimeterBlocks().get(0);
        final Block at = blockAt(first[0], first[1], first[2]);
        final org.mockito.ArgumentCaptor<org.bukkit.block.data.BlockData> laid =
            org.mockito.ArgumentCaptor.forClass(org.bukkit.block.data.BlockData.class);
        verify(at).setBlockData(laid.capture(), eq(false));
        verify((Slab) laid.getValue()).setType(Slab.Type.BOTTOM);
    }

    /**
     * A pair whose world is not loaded is still removed, and the player told why nothing came
     * back.
     *
     * <p>Removal is by id from anywhere, so the world may well be unloaded. Loading a world as
     * a side effect of tidying up a ring would be a surprising thing for this command to do.
     */
    @Test
    void aPairInAnUnloadedWorldIsRemovedAndTheSlabsLeftWhereTheyAre()
    {
        bukkit.when(() -> Bukkit.getWorld(WORLD)).thenReturn(null);

        assertTrue(remove(PAIR_ID));

        assertNull(RingManager.getPair(PAIR_ID), "the pair is still removed");
        verify(owner).sendMessage(contains("world is not loaded"));
        // And the removal line does not claim a number: "put 0 slabs back" reads as though
        // something happened, which is the opposite of what the follow-up is there to say.
        verify(owner, never()).sendMessage(contains("slabs back"));
    }

    /** A pair somebody else owns is not theirs to remove. */
    @Test
    void aPairIsNotSomebodyElsesToRemove()
    {
        pair.setOwner(UUID.randomUUID().toString());

        assertTrue(remove(PAIR_ID));

        assertNotNull(RingManager.getPair(PAIR_ID), "still there");
        verify(owner).sendMessage(contains("not your ring pair"));
        yaml.verify(() -> RingYamlManager.saveWorld(anyString()), never());
    }

    /** Naming a pair that does not exist says so rather than removing something else. */
    @Test
    void namingAPairThatDoesNotExistRemovesNothing()
    {
        assertTrue(remove("nosuchpair"));

        assertNotNull(RingManager.getPair(PAIR_ID), "the real one is untouched");
        verify(owner).sendMessage(contains("There is no ring pair called nosuchpair"));
    }
}
