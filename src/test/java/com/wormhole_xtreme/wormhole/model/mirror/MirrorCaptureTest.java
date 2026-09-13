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
     * A block buried on all six sides is pruned to air; one with any face open is kept.
     *
     * <p>Nothing surrounded by solid blocks can be seen through a window. A block on the box's
     * edge is kept because what lies beyond the edge is unknown.
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

        assertTrue(capture.isAir(2, 2, 2), "buried in stone on every side");
        assertSame(glass, capture.at(1, 1, 1), "glass hides nothing, so it stays");
        assertSame(stone, capture.at(2, 1, 1), "and so does the stone beside it");
        assertSame(stone, capture.at(0, 2, 2), "on the edge, with the unknown beyond it");
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
