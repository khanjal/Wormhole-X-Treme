package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Rotatable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;

/**
 * Where a mirror may hang, and what cannot be broken once it does.
 *
 * <p>A mirror draws its world behind the wall it hangs on, and only the wall hides that world from
 * anywhere but the opening. A banner on a post in the open showed the far world past its edges
 * however the view was trimmed, so a mirror needs a wall banner with solid wall a block out on
 * every side -- two is better and said -- and once it has one, neither the banner nor that wall
 * can be broken out from under it. One mirror per world by default, since right-clicking a mirror
 * scrolls through the others.
 */
class MirrorPlacementTest
{
    /** Blocks of the banner's world that are not solid. */
    private final Set<MirrorWindow.Spot> open = new HashSet<>();

    private World world;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(com.wormhole_xtreme.wormhole.WormholeXTreme.class));
        ConfigTestSupport.clear();
        MirrorManager.clear();
        world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(invocation ->
            blockAt(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
    }

    @AfterEach
    void tearDown() throws Exception
    {
        MirrorManager.clear();
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    /** A block of the world, solid unless it is in {@link #open}; the banner itself at 10 64 10. */
    private Block blockAt(final int x, final int y, final int z)
    {
        final Block block = mock(Block.class);
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(x);
        when(block.getY()).thenReturn(y);
        when(block.getZ()).thenReturn(z);
        if ((x == 10) && (y == 64) && (z == 10))
        {
            final Directional facing = mock(Directional.class);
            when(facing.getFacing()).thenReturn(BlockFace.NORTH);
            when(block.getType()).thenReturn(Material.WHITE_WALL_BANNER);
            when(block.getBlockData()).thenReturn(facing);
            return block;
        }
        final BlockData data = mock(BlockData.class);
        when(data.isOccluding()).thenReturn(!open.contains(new MirrorWindow.Spot(x, y, z)));
        when(block.getBlockData()).thenReturn(data);
        return block;
    }

    /** A wall banner facing north hangs on the wall to its south, z 11. */
    @Test
    void aWallBannerInSolidWallMayBeAMirror()
    {
        assertNull(MirrorPlacement.refusal(world.getBlockAt(10, 64, 10), "library"),
            "solid wall a block out on every side is what a mirror needs");
        assertNull(MirrorPlacement.thinWall(world.getBlockAt(10, 64, 10), 1), "and two out is nothing to remark on");
    }

    /**
     * The face is three wide and four tall: the opening, one wide and two tall running down from
     * the banner, and a block round it.
     */
    @Test
    void theFaceIsTheOpeningAndABlockRoundIt()
    {
        final Set<MirrorWindow.Spot> face = MirrorPlacement.face(10, 64, 10, BlockFace.NORTH);

        assertEquals(12, face.size());
        assertTrue(face.contains(new MirrorWindow.Spot(10, 64, 11)), "the block the banner hangs on");
        assertTrue(face.contains(new MirrorWindow.Spot(10, 63, 11)), "the opening's lower block");
        assertTrue(face.contains(new MirrorWindow.Spot(9, 62, 11)), "one out and one below the opening");
        assertTrue(face.contains(new MirrorWindow.Spot(11, 65, 11)), "one out and one above the banner");
        assertFalse(face.contains(new MirrorWindow.Spot(10, 64, 10)), "not the banner's own block, which is in front");
        assertFalse(face.contains(new MirrorWindow.Spot(12, 64, 11)), "and no further than one");
    }

    /**
     * A wall solid a block out but not two makes a mirror, with a word about the block short.
     *
     * <p>"Let's go down to 1 and then leave that the lower limit. We can do a warning if it's less
     * than 2."
     */
    @Test
    void aWallOnlyABlockOutIsAllowedAndSaid()
    {
        open.add(new MirrorWindow.Spot(12, 61, 11));

        assertNull(MirrorPlacement.refusal(world.getBlockAt(10, 64, 10), "library"), "a block of wall is enough");
        final String said = MirrorPlacement.thinWall(world.getBlockAt(10, 64, 10), 1);
        assertNotNull(said, "but short of two is worth a word");
        assertTrue(said.contains("12 61 11"), "naming the block: " + said);
    }

    /**
     * A mirror two banners wide needs a face a block wider, on its right looking at the wall, and
     * cannot be broken at either banner.
     *
     * <p>"How about wide support for the mirror too, for even places?" Facing north, the wall is to
     * the south, and looking at it, right is west.
     */
    @Test
    void aMirrorTwoWideHasAFaceABlockWiderAndBothBannersAreProtected()
    {
        final Set<MirrorWindow.Spot> face = MirrorPlacement.face(10, 64, 10, BlockFace.NORTH, 2);

        assertEquals(16, face.size(), "four across and four tall");
        assertTrue(face.contains(new MirrorWindow.Spot(9, 64, 11)), "the wall behind the second banner");
        assertTrue(face.contains(new MirrorWindow.Spot(8, 62, 11)), "one past it, and one below the opening");
        assertTrue(face.contains(new MirrorWindow.Spot(11, 65, 11)), "one past the first on its other side");
        assertFalse(face.contains(new MirrorWindow.Spot(7, 64, 11)), "no further to the right");
        assertFalse(face.contains(new MirrorWindow.Spot(12, 64, 11)), "or to the left");

        MirrorManager.add(new QuantumMirror("hall", new MirrorBlock("world", 10, 64, 10),
            new MirrorPoint("world", 10.01, 63, 10.5, 180f, 0f)).withWidth(2));
        assertTrue(MirrorPlacement.isProtected(world.getBlockAt(9, 64, 10)), "the second banner");
        assertTrue(MirrorPlacement.isProtected(world.getBlockAt(8, 64, 11)), "the wider face");
        assertFalse(MirrorPlacement.isProtected(world.getBlockAt(7, 64, 11)), "past it is ordinary wall");
    }

    /** A gap anywhere in the face refuses, and says which block. */
    @Test
    void aGapABlockOutIsRefusedByName()
    {
        open.add(new MirrorWindow.Spot(11, 62, 11));

        final String refused = MirrorPlacement.refusal(world.getBlockAt(10, 64, 10), "library");

        assertNotNull(refused, "a gap a block out lets the far world show past the wall");
        assertTrue(refused.contains("11 62 11"), "and the refusal should name the block to fill: " + refused);
    }

    /**
     * A pair short of wall says how big its wall has to be, not only which block to fill.
     *
     * <p>"It says it needs 2 blocks around it. Is the 2nd banner messing with it?" -- "it was one
     * column short." A wall built for one banner is three across; two need four, and the gap was
     * the column past the second banner.
     */
    @Test
    void aPairShortOfWallSaysHowBigItsWallHasToBe()
    {
        open.add(new MirrorWindow.Spot(8, 64, 11));

        final String refused = MirrorPlacement.refusal(world.getBlockAt(10, 64, 10), "hall", 2);

        assertNotNull(refused, "the column past the second banner is part of a pair's wall");
        assertTrue(refused.contains("4 across and 4 tall"), "and the refusal should say how big: " + refused);
        assertTrue(refused.contains("8 64 11"), "and which block: " + refused);
        assertNull(MirrorPlacement.refusal(world.getBlockAt(10, 64, 10), "hall"),
            "a single banner's wall never reaches that column");
    }

    /** A banner on a post is refused whatever is round it. */
    @Test
    void aFreestandingBannerIsRefused()
    {
        final Block post = mock(Block.class);
        when(post.getWorld()).thenReturn(world);
        final Rotatable onAPost = mock(Rotatable.class);
        when(post.getBlockData()).thenReturn(onAPost);

        final String refused = MirrorPlacement.refusal(post, "library");

        assertNotNull(refused);
        assertTrue(refused.contains("wall"), "the refusal should say where a mirror goes: " + refused);
    }

    /** One mirror per world by default: a second is refused, and the setting is named. */
    @Test
    void aSecondMirrorInOneWorldIsRefusedByDefault()
    {
        MirrorManager.add(new QuantumMirror("hall", new MirrorBlock("world", 40, 64, 40), null));

        final String refused = MirrorPlacement.refusal(world.getBlockAt(10, 64, 10), "library");

        assertNotNull(refused, "one mirror per world is the default");
        assertTrue(refused.contains("mirror-per-world-limit"), "and the refusal should name the setting: " + refused);
    }

    /** A mirror in another world, or the one being moved here, does not count. */
    @Test
    void onlyOtherMirrorsInThisWorldCountAgainstTheLimit()
    {
        MirrorManager.add(new QuantumMirror("nether", new MirrorBlock("world_nether", 40, 64, 40), null));
        MirrorManager.add(new QuantumMirror("library", new MirrorBlock("world", 40, 64, 40), null));

        assertNull(MirrorPlacement.refusal(world.getBlockAt(10, 64, 10), "library"),
            "moving library within its own world, with nether elsewhere, is still one mirror here");
    }

    /** The limit is a setting, and 0 lifts it. */
    @Test
    void theLimitIsASettingAndZeroLiftsIt()
    {
        MirrorManager.add(new QuantumMirror("hall", new MirrorBlock("world", 40, 64, 40), null));
        ConfigTestSupport.set(ConfigKeys.MIRROR_PER_WORLD_LIMIT, 2);
        assertNull(MirrorPlacement.refusal(world.getBlockAt(10, 64, 10), "library"), "two allowed, one there");

        MirrorManager.add(new QuantumMirror("gallery", new MirrorBlock("world", 70, 64, 40), null));
        assertNotNull(MirrorPlacement.refusal(world.getBlockAt(10, 64, 10), "library"), "two allowed, two there");

        ConfigTestSupport.set(ConfigKeys.MIRROR_PER_WORLD_LIMIT, 0);
        assertNull(MirrorPlacement.refusal(world.getBlockAt(10, 64, 10), "library"), "0 is no limit");
    }

    /** The banner and its face cannot be broken; the block beside the face can. */
    @Test
    void theBannerAndItsFaceAreProtectedAndNothingElse()
    {
        MirrorManager.add(new QuantumMirror("library", new MirrorBlock("world", 10, 64, 10), null));

        assertTrue(MirrorPlacement.isProtected(world.getBlockAt(10, 64, 10)), "the banner");
        assertTrue(MirrorPlacement.isProtected(world.getBlockAt(10, 64, 11)), "the block it hangs on");
        assertTrue(MirrorPlacement.isProtected(world.getBlockAt(11, 62, 11)), "the corner of the face");
        assertFalse(MirrorPlacement.isProtected(world.getBlockAt(12, 64, 11)), "one past the face is ordinary wall");
        assertFalse(MirrorPlacement.isProtected(world.getBlockAt(10, 64, 12)), "and so is the block behind the wall");
    }
}
