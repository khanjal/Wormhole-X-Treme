package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A capture of a far side: what it answers, what it prunes, and that it survives the disk.
 *
 * <p>The windows draw from these and nothing else, so a capture that read back wrong would be a
 * mirror onto the wrong place, and one that pruned wrong would be a hillside with holes in it.
 */
class MirrorCaptureTest
{
    private final BlockData air = named("minecraft:air", false);
    private final BlockData stone = named("minecraft:stone", true);
    private final BlockData glass = named("minecraft:glass", false);
    private final BlockData water = fluid("minecraft:water", Material.WATER);
    private final BlockData lava = fluid("minecraft:lava", Material.LAVA);

    private static BlockData named(final String name, final boolean occluding)
    {
        final BlockData data = mock(BlockData.class);
        when(data.getAsString()).thenReturn(name);
        when(data.isOccluding()).thenReturn(occluding);
        return data;
    }

    /** A 4×4×4 box at the origin. */
    private MirrorCapture.Builder box()
    {
        return new MirrorCapture.Builder("far", true, new MirrorCapture.Box(0, 0, 0, 4, 4, 4), air);
    }

    /** A fluid, which does not occlude as Bukkit counts it, whatever a player can see through it. */
    private static BlockData fluid(final String name, final Material material)
    {
        final BlockData data = named(name, false);
        when(data.getMaterial()).thenReturn(material);
        return data;
    }

    @Test
    void answersWhatWasPutAndAirForTheRest()
    {
        final MirrorCapture.Builder builder = box();
        builder.put(1, 2, 3, stone);

        final MirrorCapture capture = builder.build();

        assertSame(stone, capture.at(1, 2, 3));
        assertTrue(capture.isAir(0, 0, 0), "never written, so air");
        assertFalse(capture.isAir(1, 2, 3));
        assertNull(capture.at(4, 0, 0), "outside the box");
        assertTrue(capture.isAir(-1, 0, 0), "outside the box reads as air too");
        assertEquals(2, capture.states(), "air and stone");
    }

    @Test
    void theTopOfAColumnIsItsHighestBlockThatIsNotAir()
    {
        final MirrorCapture.Builder builder = box();
        builder.put(2, 0, 2, stone);
        builder.put(2, 3, 2, glass);

        final MirrorCapture capture = builder.build();

        assertEquals(3, capture.top(2, 2));
        assertEquals(-1, capture.top(0, 0), "one below the box for an empty column");
        assertEquals(-1, capture.top(9, 9), "and for one outside it");
    }

    /**
     * A block buried on all six sides is marked buried, not air; one with any face open is kept.
     *
     * <p>Nothing surrounded by solid blocks can be seen through a window. A block on the box's
     * edge is kept because what lies beyond the edge is unknown. Written as air, a buried block
     * made a window in a wall carve the inside of the far hill out of the real ground.
     */
    @Test
    void pruningBlanksOnlyWhatIsBuriedOnAllSides()
    {
        final MirrorCapture.Builder builder = box();
        for (int x = 0; x < 4; x++)
        {
            for (int y = 0; y < 4; y++)
            {
                for (int z = 0; z < 4; z++)
                {
                    builder.put(x, y, z, stone);
                }
            }
        }
        builder.put(1, 1, 1, glass);
        builder.prune();

        final MirrorCapture capture = builder.build();

        assertTrue(capture.isBuried(2, 2, 2), "buried in stone on every side");
        assertFalse(capture.isAir(2, 2, 2), "and not air, which would be drawn as a hole");
        assertFalse(capture.isBuried(1, 1, 1));
        assertSame(glass, capture.at(1, 1, 1), "glass hides nothing, so it stays");
        assertSame(stone, capture.at(2, 1, 1), "and so does the stone beside it");
        assertSame(stone, capture.at(0, 2, 2), "on the edge, with the unknown beyond it");
    }

    /**
     * The layer just under an open face is kept; only what is buried two deep goes.
     *
     * <p>A capture is looked at from angles nobody chose, and a surface block that is wrong for
     * any reason -- broken since, a plant, a fence read as solid -- should have ground under
     * it, not a hole into the real world. So "one block below the surface, just in case".
     */
    @Test
    void pruningKeepsTheLayerUnderTheSurface()
    {
        final MirrorCapture.Builder builder = new MirrorCapture.Builder("far", true, new MirrorCapture.Box(0, 0, 0, 7, 7, 7), air);
        for (int x = 0; x < 7; x++)
        {
            for (int y = 0; y < 7; y++)
            {
                for (int z = 0; z < 7; z++)
                {
                    builder.put(x, y, z, stone);
                }
            }
        }
        builder.put(3, 3, 0, air);
        builder.prune();

        final MirrorCapture capture = builder.build();

        assertSame(stone, capture.at(3, 3, 1), "the surface, with a face on the air");
        assertSame(stone, capture.at(3, 3, 2), "the layer under it, kept in case");
        assertTrue(capture.isBuried(3, 3, 3), "two deep is buried");
        assertTrue(capture.isBuried(3, 3, 4), "and so is deeper");
        assertSame(stone, capture.at(3, 3, 6), "the edge itself is kept, since what lies beyond is unknown");
        assertTrue(capture.isBuried(3, 3, 5), "but the edge does not count as open, so the block inside it is buried");
        assertTrue(capture.isBuried(1, 1, 1), "however many edges it touches");
    }

    /**
     * Only what a viewer at the opening could see is kept; the rest is left to the real world.
     *
     * <p>"Smartly capture all blocks in that player's view while they're against the mirror,
     * looking up, down, left and right, and just the visible blocks." Behind a wall across the
     * far side nothing is seen, air included, and nothing is sent; the wall itself is, and so is
     * a block off to one side within the slant the hole allows -- a block sideways per block in,
     * two up. Beside the opening at a steeper slant is not, however close the eye: "we have so
     * much extra, the sides".
     */
    @Test
    void keepingOnlyWhatIsSeenLeavesWhatAWallHidesToTheRealWorld()
    {
        // A box 9 wide, 12 tall and 9 deep, arrival at (4, 2, 0) facing +z; a stone wall right across
        // at z 4, to the top.
        final MirrorCapture.Builder builder = new MirrorCapture.Builder("far", true, new MirrorCapture.Box(0, 0, 0, 9, 12, 9), air);
        for (int x = 0; x < 9; x++)
        {
            for (int y = 0; y < 12; y++)
            {
                builder.put(x, y, 4, stone);
            }
            for (int z = 0; z < 9; z++)
            {
                builder.put(x, 0, z, stone);
            }
        }
        builder.put(4, 2, 6, glass);
        builder.put(1, 3, 3, stone);
        // Eight blocks up one block in, clear of the box's top: a line through the back of a hole two
        // tall climbs under two blocks for each block in, however wide the hole.
        builder.put(1, 10, 1, stone);
        // A pane of glass in the way, with stone behind it: what a viewer sees through the pane.
        builder.put(6, 2, 2, glass);
        builder.put(6, 2, 3, stone);
        builder.keepOnlySeen(new MirrorCapture.Arrival(4, 2, 0, 0, 1), 8);

        final MirrorCapture capture = builder.build();

        assertSame(stone, capture.at(4, 2, 4), "the wall, straight ahead");
        assertSame(stone, capture.at(1, 3, 3), "to one side, within a block sideways per block in");
        assertTrue(capture.isBuried(1, 10, 1), "above the opening at a slant no line through the hole makes");
        assertSame(stone, capture.at(4, 0, 2), "the floor in front of the wall");
        assertSame(glass, capture.at(6, 2, 2), "the pane");
        assertSame(stone, capture.at(6, 2, 3), "and the stone seen through it");
        assertSame(stone, capture.at(4, 0, 4), "the wall's foot, one block behind the last floor block seen");
        assertTrue(capture.isAir(4, 2, 2), "air in front of the wall is seen, and stays air");
        assertTrue(capture.isBuried(4, 2, 6), "the glass behind the wall is left to the real world");
        assertTrue(capture.isBuried(4, 2, 5), "and so is the air behind it");
        assertTrue(capture.isBuried(4, 0, 5), "and the floor two blocks behind the last seen");
    }

    /**
     * Lava hides what is behind it; water does not.
     *
     * <p>"We can't see through lava." Bukkit counts neither fluid as occluding, so a ray went
     * through a lava lake as through a pond, and a mirror onto the Nether kept every block under
     * every lake it faced. Lava ends a ray as stone does now. Water still lets it through, so the
     * bed of a pond is seen, and the block behind the lava is left to the real world along with
     * everything past it: only the block right behind it stays, as the layer behind every kept
     * block always does.
     */
    @Test
    void lavaHidesWhatIsBehindItWhereWaterDoesNot()
    {
        for (final BlockData wall : List.of(lava, water))
        {
            // A box 9 wide, 12 tall and 9 deep, arrival at (4, 2, 0) facing +z; a wall of the fluid
            // right across at z 4, and stone two and three blocks behind it.
            final MirrorCapture.Builder builder = new MirrorCapture.Builder("far", true, new MirrorCapture.Box(0, 0, 0, 9, 12, 9), air);
            for (int x = 0; x < 9; x++)
            {
                for (int y = 0; y < 12; y++)
                {
                    builder.put(x, y, 4, wall);
                }
            }
            builder.put(4, 2, 6, stone);
            builder.put(4, 2, 7, stone);
            builder.keepOnlySeen(new MirrorCapture.Arrival(4, 2, 0, 0, 1), 8);

            final MirrorCapture capture = builder.build();

            assertSame(wall, capture.at(4, 2, 4), "the wall itself is seen either way");
            if (wall == lava)
            {
                assertTrue(capture.isBuried(4, 2, 6), "two blocks behind lava is left to the real world");
                assertTrue(capture.isBuried(4, 2, 7), "and so is everything past it");
            }
            else
            {
                assertSame(stone, capture.at(4, 2, 6), "the stone seen through the water");
                assertSame(stone, capture.at(4, 2, 7), "and the layer behind it");
            }
        }
    }

    /**
     * Water can be seen through only so far; glass has no such limit.
     *
     * <p>A ray through an ocean went on to the bed however deep, and a mirror onto a beach kept
     * the water in the whole fan of its view: millions of blocks. A player under water sees a
     * few dozen blocks and fog past that, so a ray ends after {@code WATER_SIGHT} blocks of it.
     * The box is a corridor three wide and sixty long so the straight ray is the deepest one.
     */
    @Test
    void waterIsSeenThroughOnlySoFarWhereGlassIsSeenThroughToTheEnd()
    {
        for (final BlockData fill : List.of(water, glass))
        {
            final MirrorCapture.Builder builder = new MirrorCapture.Builder("far", true, new MirrorCapture.Box(0, 0, 0, 3, 5, 60), air);
            for (int x = 0; x < 3; x++)
            {
                for (int y = 0; y < 5; y++)
                {
                    for (int z = 1; z < 60; z++)
                    {
                        builder.put(x, y, z, fill);
                    }
                }
            }
            builder.keepOnlySeen(new MirrorCapture.Arrival(1, 2, 0, 0, 1), 58);

            final MirrorCapture capture = builder.build();

            assertSame(fill, capture.at(1, 2, 20), "twenty blocks in is seen through either");
            if (fill == water)
            {
                assertSame(water, capture.at(1, 2, 30), "and thirty, within the water's sight");
                assertTrue(capture.isBuried(1, 2, 40), "forty blocks of water is past what fog lets through");
                assertTrue(capture.isBuried(1, 2, 55), "and so is the far end");
            }
            else
            {
                assertSame(glass, capture.at(1, 2, 55), "glass is seen through to the end of the reach");
            }
        }
    }

    /**
     * A capture that would keep too much is taken shorter, a quarter at a time, never below the depth.
     *
     * <p>A jungle or an ocean bed at ten chunks could keep millions of blocks. Rather than a
     * setting to lower, the reach is cut until the kept blocks fit the budget, the way a view is
     * cut shallower until it fits. It never goes below the floor, the view depth: a view drawn
     * past its capture would run out of room, so a room still too big at the depth is kept as it
     * is. A box of glass, seen through everywhere, keeps the whole fan of the view and so is the
     * worst case.
     */
    @Test
    void aCaptureThatKeepsTooMuchIsCutShorterUntilItFitsButNeverBelowTheDepth()
    {
        final MirrorCapture.Builder generous = new MirrorCapture.Builder("far", true, new MirrorCapture.Box(0, 0, 0, 21, 21, 41), air);
        generous.fillBelow(21, glass);
        assertEquals(32, generous.keepOnlySeenWithin(new MirrorCapture.Arrival(10, 2, 0, 0, 1), 32, 8, 1_000_000),
            "within a generous budget the reach asked for is the reach kept");
        assertSame(glass, generous.build().at(10, 2, 30), "and the room is seen to the end of it");

        final MirrorCapture.Builder tight = new MirrorCapture.Builder("far", true, new MirrorCapture.Box(0, 0, 0, 21, 21, 41), air);
        tight.fillBelow(21, glass);
        final int kept = tight.keepOnlySeenWithin(new MirrorCapture.Arrival(10, 2, 0, 0, 1), 32, 8, 2000);
        assertEquals(8, kept, "cut a quarter at a time until it fits, and stopped at the floor: 32, 24, 18, 13, 9, 8");
        final MirrorCapture capture = tight.build();
        assertSame(glass, capture.at(10, 2, 5), "the room to the depth is still there");
        assertTrue(capture.isBuried(10, 2, 20), "and past the shortened reach it is left to the real world");
    }

    /**
     * A mirror facing north or west sees its room too, with its own wall behind it.
     *
     * <p>"I don't see a reflection at all": a mirror facing north captured 8 blocks and no air.
     * The rays started on the boundary between the arrival block and the one behind it, and a
     * ray facing north or west rounded into the block behind -- the mirror's own wall -- and
     * stopped where it began. The test above faces south, where it rounds the other way.
     */
    @Test
    void aMirrorFacingNorthOrWestSeesItsRoomPastItsOwnWall()
    {
        // Facing north: the arrival at z 7, its wall behind at z 8, a far wall across at z 3.
        final MirrorCapture.Builder north = new MirrorCapture.Builder("far", true, new MirrorCapture.Box(0, 0, 0, 9, 9, 9), air);
        for (int x = 0; x < 9; x++)
        {
            for (int y = 0; y < 9; y++)
            {
                north.put(x, y, 8, stone);
                north.put(x, y, 3, stone);
            }
        }
        north.keepOnlySeen(new MirrorCapture.Arrival(4, 2, 7, 0, -1), 8);
        final MirrorCapture northward = north.build();

        assertTrue(northward.isAir(4, 2, 5), "the room in front of a north-facing mirror is seen");
        assertSame(stone, northward.at(4, 2, 3), "and so is the wall across it");

        // Facing west: the arrival at x 7, its wall behind at x 8, a far wall across at x 3.
        final MirrorCapture.Builder west = new MirrorCapture.Builder("far", true, new MirrorCapture.Box(0, 0, 0, 9, 9, 9), air);
        for (int z = 0; z < 9; z++)
        {
            for (int y = 0; y < 9; y++)
            {
                west.put(8, y, z, stone);
                west.put(3, y, z, stone);
            }
        }
        west.keepOnlySeen(new MirrorCapture.Arrival(7, 2, 4, -1, 0), 8);
        final MirrorCapture westward = west.build();

        assertTrue(westward.isAir(5, 2, 4), "the room in front of a west-facing mirror is seen");
        assertSame(stone, westward.at(3, 2, 4), "and so is the wall across it");
    }

    /** A buried block is still buried after the disk. */
    @Test
    void aBuriedBlockSurvivesTheDiskAsBuried(@TempDir final File dir) throws IOException
    {
        final MirrorCapture.Builder builder = box();
        for (int x = 0; x < 4; x++)
        {
            for (int y = 0; y < 4; y++)
            {
                for (int z = 0; z < 4; z++)
                {
                    builder.put(x, y, z, stone);
                }
            }
        }
        builder.prune();
        final File file = new File(dir, "buried.view");

        builder.build().save(file);
        final MirrorCapture after = MirrorCapture.load(file);

        assertTrue(after.isBuried(2, 2, 2));
        assertFalse(after.isBuried(0, 0, 0), "the edge");
    }

    /**
     * A file of an earlier kind is refused, and says why.
     *
     * <p>Earlier files were a dense grid of the whole box; this one keeps only what can be seen.
     * Refused, the mirror it is for asks for a fresh capture on the next look, the same as for a
     * file that is missing, which is what the earlier build's users get: a few seconds of banner.
     */
    @Test
    void aFileOfAnEarlierKindIsRefused(@TempDir final File dir) throws IOException
    {
        final File file = new File(dir, "old.view");
        box().build().save(file);
        rewriteVersion(file, 2);

        final IOException refused = assertThrows(IOException.class, () -> MirrorCapture.load(file));

        assertTrue(refused.getMessage().contains("version 2"), refused.getMessage());
    }

    /**
     * What is kept is visited in order of position, seen air marked as such.
     *
     * <p>A wall mirror's whole view is a walk over what is kept, not over the box, so a view at
     * the render distance costs what its surfaces cost.
     */
    @Test
    void whatIsKeptIsVisitedInOrderWithSeenAirMarked()
    {
        final MirrorCapture.Builder builder = box();
        builder.put(3, 0, 0, stone);
        builder.put(0, 0, 3, glass);
        builder.put(1, 2, 1, air);
        final MirrorCapture capture = builder.build();

        assertEquals(List.of("0,0,3", "1,2,1 air", "3,0,0"), visits(capture));
        assertEquals(3, capture.kept());
        assertEquals(2, capture.filled(), "seen air is kept but not filled");
    }

    /**
     * The last word on a block wins, whatever order blocks were put in.
     *
     * <p>A photograph puts blocks column by column, already sorted; a banner cleared afterwards
     * does not, and neither does a block put twice. Read in the wrong order, a far banner hung in
     * the middle of the view.
     */
    @Test
    void aBlockPutOutOfOrderIsWhatItWasPutAsLast()
    {
        final MirrorCapture.Builder builder = box();
        builder.put(2, 0, 0, stone);
        builder.put(0, 0, 0, glass);
        builder.put(2, 0, 0, glass);
        builder.clear(0, 0, 0);
        // Refused: a cleared block is a far banner's, and stays air whatever the second pass reads there.
        builder.put(0, 0, 0, stone);

        final MirrorCapture capture = builder.build();

        assertSame(glass, capture.at(2, 0, 0), "put twice, so the second");
        assertTrue(capture.isAir(0, 0, 0), "cleared after it was put, and not put over afterwards");
        assertEquals(List.of("0,0,0 air", "2,0,0"), visits(capture), "one entry a block, in order");
    }

    /**
     * Seen air comes back from the disk run for run, including a column it crosses twice.
     *
     * <p>Air a viewer can see is stored as runs of y a column at a time, not as entries, and it
     * is what a view carves through the real world. Read back a run short, the far side's air
     * would be the real world's stone.
     */
    @Test
    void seenAirSurvivesTheDiskRunForRun(@TempDir final File dir) throws IOException
    {
        // A corridor three wide, arrival at (1, 1, 0) facing +z, a floor, and a block hanging in
        // the middle of the view at (1, 3, 4) with air seen under it and over it.
        final MirrorCapture.Builder builder = new MirrorCapture.Builder("far", true, new MirrorCapture.Box(0, 0, 0, 3, 7, 10), air);
        for (int x = 0; x < 3; x++)
        {
            for (int z = 0; z < 10; z++)
            {
                builder.put(x, 0, z, stone);
            }
        }
        builder.put(1, 3, 4, stone);
        builder.keepOnlySeen(new MirrorCapture.Arrival(1, 1, 0, 0, 1), 8);
        builder.clear(1, 2, 4);
        final MirrorCapture before = builder.build();
        final File file = new File(dir, "air.view");

        before.save(file);
        final MirrorCapture after = MirrorCapture.load(file);

        assertTrue(before.isAir(1, 1, 4) && !before.isBuried(1, 1, 4), "seen under the hanging block");
        assertSame(stone, before.at(1, 3, 4));
        assertTrue(before.isAir(1, 4, 4) && !before.isBuried(1, 4, 4), "and seen over it");
        assertEquals(before.kept(), after.kept());
        assertEquals(before.seenAir(), after.seenAir());
        final List<String> kept = visits(before);
        assertEquals(kept, visits(after));
        assertTrue(kept.contains("1,1,4 air") && kept.contains("1,2,4 air") && kept.contains("1,4,4 air"),
            "the column's air either side of the block, the cleared block as an entry: " + kept);
        assertEquals(kept.size(), new java.util.HashSet<>(kept.stream().map(k -> k.replace(" air", "")).toList()).size(),
            "each block visited once: " + kept);
        assertTrue(kept.stream().noneMatch(k -> k.startsWith("1,7,")), "and none above the box: " + kept);
        for (int x = 0; x < 3; x++)
        {
            for (int y = 0; y < 7; y++)
            {
                for (int z = 0; z < 10; z++)
                {
                    assertEquals(before.nameAt(x, y, z), after.nameAt(x, y, z), x + "," + y + "," + z);
                }
            }
        }
    }

    /** Every block {@code forEachKept} visits, in order, seen air marked. */
    private static List<String> visits(final MirrorCapture capture)
    {
        final List<String> visited = new java.util.ArrayList<>();
        capture.forEachKept((x, y, z, isAir) -> visited.add(x + "," + y + "," + z + (isAir ? " air" : "")));
        return visited;
    }

    /** Rewrites a capture file's version number, as an older build would have written it. */
    static void rewriteVersion(final File file, final int version) throws IOException
    {
        final byte[] raw;
        try (java.util.zip.GZIPInputStream in = new java.util.zip.GZIPInputStream(Files.newInputStream(file.toPath())))
        {
            raw = in.readAllBytes();
        }
        java.nio.ByteBuffer.wrap(raw).putInt(4, version);
        try (java.util.zip.GZIPOutputStream out = new java.util.zip.GZIPOutputStream(Files.newOutputStream(file.toPath())))
        {
            out.write(raw);
        }
    }

    @Test
    void survivesTheDiskWithItsShapeNamesAndBlocks(@TempDir final File dir) throws IOException
    {
        final MirrorCapture.Builder builder = new MirrorCapture.Builder("far", false, new MirrorCapture.Box(-3, 60, 7, 3, 5, 2), air);
        builder.put(-2, 63, 8, stone);
        builder.put(-1, 60, 7, glass);
        final MirrorCapture before = builder.build();
        final File file = new File(dir, "a.view");

        before.save(file);
        final MirrorCapture after = MirrorCapture.load(file);

        assertEquals("far", after.worldName());
        assertFalse(after.hasSky());
        assertEquals(before.takenAt(), after.takenAt());
        assertEquals(before.names(), after.names(), "the palette, air first");
        assertEquals(before.size(), after.size());
        assertFalse(after.isAir(-2, 63, 8));
        assertFalse(after.isAir(-1, 60, 7));
        assertTrue(after.isAir(-3, 60, 7));
        assertEquals(63, after.top(-2, 8));
        assertEquals(59, after.top(-3, 7), "an empty column: one below the box");
        assertTrue(file.length() < 1000, "a tiny box is a tiny file: " + file.length());
    }

    @Test
    void aFileThatIsNotACaptureIsRefused(@TempDir final File dir) throws IOException
    {
        final File file = new File(dir, "not.view");
        Files.write(file.toPath(), new byte[] { 1, 2, 3 });

        assertThrows(IOException.class, () -> MirrorCapture.load(file));
    }
}
