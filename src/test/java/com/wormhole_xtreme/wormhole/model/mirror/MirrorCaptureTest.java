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
        return new MirrorCapture.Builder("far", true, 0, 0, 0, 4, 4, 4, air);
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
        final MirrorCapture.Builder builder = new MirrorCapture.Builder("far", true, 0, 0, 0, 7, 7, 7, air);
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
        // A 9-block box, arrival at (4, 2, 0) facing +z; a stone wall right across at z 4.
        final MirrorCapture.Builder builder = new MirrorCapture.Builder("far", true, 0, 0, 0, 9, 9, 9, air);
        for (int x = 0; x < 9; x++)
        {
            for (int y = 0; y < 9; y++)
            {
                builder.put(x, y, 4, stone);
                builder.put(x, 0, y, stone);
            }
        }
        builder.put(4, 2, 6, glass);
        builder.put(1, 3, 3, stone);
        builder.put(0, 6, 1, stone);
        // A pane of glass in the way, with stone behind it: what a viewer sees through the pane.
        builder.put(6, 2, 2, glass);
        builder.put(6, 2, 3, stone);
        builder.keepOnlySeen(4, 2, 0, 0, 1, 8);

        final MirrorCapture capture = builder.build();

        assertSame(stone, capture.at(4, 2, 4), "the wall, straight ahead");
        assertSame(stone, capture.at(1, 3, 3), "to one side, within a block sideways per block in");
        assertTrue(capture.isBuried(0, 6, 1), "beside the opening at a slant no line through the hole makes");
        assertSame(stone, capture.at(4, 0, 2), "the floor in front of the wall");
        assertSame(glass, capture.at(6, 2, 2), "the pane");
        assertSame(stone, capture.at(6, 2, 3), "and the stone seen through it");
        assertSame(stone, capture.at(4, 0, 4), "the wall's foot, one block behind the last floor block seen");
        assertTrue(capture.isAir(4, 2, 2), "air in front of the wall is seen, and stays air");
        assertTrue(capture.isBuried(4, 2, 6), "the glass behind the wall is left to the real world");
        assertTrue(capture.isBuried(4, 2, 5), "and so is the air behind it");
        assertTrue(capture.isBuried(4, 0, 5), "and the floor two blocks behind the last seen");
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

        final List<String> visited = new java.util.ArrayList<>();
        capture.forEachKept((x, y, z, isAir) -> visited.add(x + "," + y + "," + z + (isAir ? " air" : "")));

        assertEquals(List.of("0,0,3", "1,2,1 air", "3,0,0"), visited);
        assertEquals(3, capture.kept());
        assertEquals(2, capture.filled(), "seen air is kept but not filled");
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
        final MirrorCapture.Builder builder = new MirrorCapture.Builder("far", false, -3, 60, 7,
            3, 5, 2, air);
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
