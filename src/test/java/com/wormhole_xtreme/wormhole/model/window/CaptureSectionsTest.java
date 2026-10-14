package com.wormhole_xtreme.wormhole.model.window;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A capture kept by sections answers exactly as the sorted list it replaced did (#525).
 *
 * <p>Only the storage changed: the kept set, the seen air and each column's top have to be what
 * they were for the same blocks, or a view would draw a different far side than it did the day
 * before. Each test here builds the same blocks twice, once into the sections and once into
 * {@link LegacyCapture}, the list as it was, and asks both about every block of the box and a
 * block round it. A block answered wrong is a hole in a far hillside, or this world's stone where
 * the far side's air was seen.
 */
class CaptureSectionsTest
{
    private final BlockData air = state("minecraft:air", false, null);

    /** A block state; stub-only, since a big capture asks each one a few million times. */
    private static BlockData state(final String name, final boolean occluding, final Material material)
    {
        final BlockData data = mock(BlockData.class, withSettings().stubOnly());
        when(data.getAsString()).thenReturn(name);
        when(data.isOccluding()).thenReturn(occluding);
        when(data.getMaterial()).thenReturn(material);
        return data;
    }

    /** As many states, some solid, some to be seen through, some water and some leaves. */
    private static BlockData[] states(final int count)
    {
        final Material[] materials = { null, null, null, Material.WATER, null, Material.OAK_LEAVES, null };
        final BlockData[] states = new BlockData[count];
        for (int i = 0; i < count; i++)
        {
            states[i] = state("test:state_" + i, (i % 3) != 0, materials[i % materials.length]);
        }
        return states;
    }

    /** A box that starts and ends part way into sections, below and above y 0. */
    private static final Capture.Box ODD = new Capture.Box(-21, -7, 13, 45, 37, 50);

    /**
     * Blocks at random, put in a random order, some put twice, some cleared, and seen from an
     * opening at the box's low x side or not.
     */
    private Capture.Builder random(final long seed, final Capture.Box box, final int kinds, final double share,
        final boolean seen)
    {
        final Random random = new Random(seed);
        final BlockData[] palette = states(kinds);
        final Capture.Builder builder = new Capture.Builder("far", true, box, air);
        final int volume = box.sizeX() * box.sizeY() * box.sizeZ();
        final int puts = (int) (volume * share);
        for (int i = 0; i < puts; i++)
        {
            final int x = box.minX() + random.nextInt(box.sizeX());
            final int y = box.minY() + random.nextInt(box.sizeY());
            final int z = box.minZ() + random.nextInt(box.sizeZ());
            // Skewed, as a far side is: a few states are most of it.
            final int kind = (int) (kinds * random.nextDouble() * random.nextDouble());
            builder.put(x, y, z, (random.nextInt(20) == 0) ? air : palette[kind]);
        }
        for (int i = 0; i < (puts / 50); i++)
        {
            builder.clear(box.minX() + random.nextInt(box.sizeX()), box.minY() + random.nextInt(box.sizeY()),
                box.minZ() + random.nextInt(box.sizeZ()));
        }
        if (seen)
        {
            builder.keepOnlySeen(new Capture.Arrival(box.minX() + 1, box.minY() + (box.sizeY() / 2),
                box.minZ() + (box.sizeZ() / 2), 1, 0, 5, 5), Math.max(box.sizeX(), box.sizeZ()));
        }
        return builder;
    }

    /** Ground with a surface, and the air over it seen from an opening, as a hillside capture is. */
    private Capture.Builder hillside(final Capture.Box box)
    {
        final BlockData stone = state("minecraft:stone", true, null);
        final BlockData dirt = state("minecraft:dirt", true, null);
        final BlockData grass = state("minecraft:grass_block[snowy=false]", true, null);
        final BlockData flower = state("minecraft:poppy", false, null);
        final Capture.Builder builder = new Capture.Builder("far", true, box, air);
        for (int x = box.minX(); x < (box.minX() + box.sizeX()); x++)
        {
            for (int z = box.minZ(); z < (box.minZ() + box.sizeZ()); z++)
            {
                final int ground = box.minY() + 8 + (int) (6 * Math.sin(x / 5.0) * Math.cos(z / 7.0));
                for (int y = box.minY(); y <= (ground - 3); y++)
                {
                    builder.put(x, y, z, stone);
                }
                builder.put(x, ground - 2, z, dirt);
                builder.put(x, ground - 1, z, dirt);
                builder.put(x, ground, z, grass);
                if (((x * 31) + z) % 11 == 0)
                {
                    builder.put(x, ground + 1, z, flower);
                }
            }
        }
        builder.keepOnlySeen(new Capture.Arrival(box.minX() + 1, box.minY() + 16, box.minZ() + (box.sizeZ() / 2), 1, 0, 7, 7),
            box.sizeX());
        builder.prune();
        return builder;
    }

    /** The same blocks kept as a list, as before sections. */
    private static LegacyCapture listed(final Capture.Builder builder, final Capture.Box box, final Capture capture)
    {
        return new LegacyCapture(box, capture.complete(), capture.names(), builder.entries(), builder.airSeen());
    }

    /**
     * Asks both about every block of the box and a block round it: what it is, whether it is air
     * or buried, each column's top, what is kept and in what order.
     */
    private static void assertSameAnswers(final LegacyCapture expected, final Capture actual, final Capture.Box box)
    {
        final List<String> wrong = new ArrayList<>();
        for (int x = box.minX() - 1; x <= (box.minX() + box.sizeX()); x++)
        {
            for (int z = box.minZ() - 1; z <= (box.minZ() + box.sizeZ()); z++)
            {
                columnAnswers(expected, actual, box, x, z, wrong);
            }
        }
        assertTrue(wrong.isEmpty(), wrong.size() + " answers changed, first " + wrong.subList(0, Math.min(5, wrong.size())));
        assertEquals(expected.kept(), actual.kept(), "blocks kept, air included");
        assertEquals(expected.filled(), actual.filled(), "blocks kept that are not air");
        assertEquals(expected.seenAir(), actual.seenAir(), "air seen");
        assertEquals(expected.visits(), visits(actual), "every block kept, visited in the same order");
    }

    /** Adds to {@code wrong} every block of one column, and its top, that the two answer differently. */
    private static void columnAnswers(final LegacyCapture expected, final Capture actual, final Capture.Box box,
        final int x, final int z, final List<String> wrong)
    {
        if (expected.top(x, z) != actual.top(x, z))
        {
            wrong.add("top " + x + "," + z + ": " + actual.top(x, z) + ", not " + expected.top(x, z));
        }
        for (int y = box.minY() - 1; y <= (box.minY() + box.sizeY()); y++)
        {
            final String was = expected.nameAt(x, y, z) + (expected.isAir(x, y, z) ? " air" : "")
                + (expected.isBuried(x, y, z) ? " buried" : "");
            final String is = actual.nameAt(x, y, z) + (actual.isAir(x, y, z) ? " air" : "")
                + (actual.isBuried(x, y, z) ? " buried" : "");
            if (!was.equals(is))
            {
                wrong.add(x + "," + y + "," + z + ": " + is + ", not " + was);
            }
        }
    }

    private static List<String> visits(final Capture capture)
    {
        final List<String> visited = new ArrayList<>();
        capture.forEachKept((x, y, z, isAir) -> visited.add(x + "," + y + "," + z + (isAir ? " air" : "")));
        return visited;
    }

    /** Builds the sections and the list from the same builder, and checks they agree. */
    private Capture assertSameAsListed(final Capture.Builder builder, final Capture.Box box)
    {
        final Capture capture = builder.build();
        assertSameAnswers(listed(builder, box, capture), capture, box);
        return capture;
    }

    /**
     * Blocks at random answer as the list did, seen from an opening and not, in a box that starts
     * and ends part way into sections.
     *
     * <p>Put in no order, some twice and some cleared after, as a far banner is: the last word on a
     * block has to win in the sections as it did in the list, and a block outside the box must not
     * land in a section the box only partly covers.
     */
    @Test
    void blocksAtRandomAnswerAsTheListDid()
    {
        for (long seed = 1; seed <= 6; seed++)
        {
            final Capture capture = assertSameAsListed(random(seed, ODD, 40, 0.3, seed % 2 == 0), ODD);
            assertTrue(capture.kept() > 1000, "seed " + seed + " kept something: " + capture.kept());
        }
    }

    /**
     * A box nearly full of a few states is kept in full sections, an index for every block, and
     * answers as the list did.
     */
    @Test
    void aDenseVolumeIsKeptInFullSectionsAndAnswersAsTheListDid()
    {
        final Capture.Box box = new Capture.Box(-16, 0, 0, 48, 40, 48);
        final Capture capture = assertSameAsListed(random(7, box, 6, 3.0, false), box);

        assertTrue(capture.sections(true) > 0, "full sections: " + capture.sections(true));
        assertEquals(0, capture.sections(false), "and none that list their blocks");
    }

    /** A few blocks scattered through a box are kept in sections listing them, and answer as the list did. */
    @Test
    void scatteredBlocksAreListedAndAnswerAsTheListDid()
    {
        final Capture capture = assertSameAsListed(random(8, ODD, 12, 0.004, false), ODD);

        assertTrue(capture.sections(false) > 0, "listing sections: " + capture.sections(false));
        assertEquals(0, capture.sections(true), "and none full");
    }

    /**
     * A hillside seen from an opening, its ground pruned, keeps both kinds of section and the seen
     * air over it, and answers as the list did: the shape almost every capture has.
     */
    @Test
    void aHillsideSeenFromAnOpeningAnswersAsTheListDid()
    {
        final Capture.Box box = new Capture.Box(-3, 60, -40, 70, 48, 80);
        final Capture capture = assertSameAsListed(hillside(box), box);

        assertTrue(capture.seenAir() > 10_000, "the air over the hill is kept: " + capture.seenAir());
        assertTrue(capture.sections(true) > 0 && capture.sections(false) > 0,
            "both kinds: " + capture.sections(true) + " full, " + capture.sections(false) + " listed");
    }

    /**
     * A section with hundreds of states packs each index wider than a byte, across the boundary
     * between one long and the next, and answers as the list did.
     */
    @Test
    void aPaletteOfHundredsPacksAcrossLongsAndAnswersAsTheListDid()
    {
        final Capture.Box box = new Capture.Box(0, 0, 0, 16, 16, 16);
        final Capture capture = assertSameAsListed(random(9, box, 700, 2.0, false), box);

        assertTrue(capture.states() > 512, "ten bits an index: " + capture.states() + " states");
        assertEquals(1, capture.sections(true), "one full section");
    }

    /**
     * A capture comes back from the disk byte for byte, through both kinds of section, and answers
     * as the list did after it.
     */
    @Test
    void aCaptureRoundTripsByteForByteThroughBothKindsOfSection(@TempDir final File dir) throws IOException
    {
        final Capture.Box box = new Capture.Box(-3, 60, -40, 70, 48, 80);
        final Capture.Builder builder = hillside(box);
        builder.keptReach(17);
        final Capture before = builder.build();
        final File first = new File(dir, "first.view");
        final File second = new File(dir, "second.view");

        before.save(first);
        final Capture after = Capture.load(first);
        after.save(second);

        assertTrue(before.sections(true) > 0 && before.sections(false) > 0, "both kinds of section are written");
        assertArrayEquals(unzipped(first), unzipped(second), "written again, the same bytes");
        assertSameAnswers(listed(builder, box, before), after, box);
        assertEquals(before.takenAt(), after.takenAt());
        assertEquals(17, after.keptReach(), "how far it kept");
    }

    /**
     * A version 4 file, the list as the last build wrote it, reads back answering exactly as it
     * did, and is written as sections the next time, still answering the same.
     *
     * <p>Every capture on a server that upgrades is one. Refused, each mirror and gate would take
     * its far side again on the next look.
     */
    @Test
    void aVersionFourFileReadsBackIdenticallyAndIsWrittenAsSectionsNextTime(@TempDir final File dir) throws IOException
    {
        final Capture.Builder builder = random(10, ODD, 30, 0.3, true);
        final Capture built = builder.build();
        final LegacyCapture list = listed(builder, ODD, built);
        final File four = new File(dir, "four.view");
        list.save(four, 4, "far", true, 1234L, 21);

        final Capture read = Capture.load(four);

        assertSameAnswers(list, read, ODD);
        assertEquals(21, read.keptReach(), "how far it kept");
        assertEquals(1234L, read.takenAt());
        final File five = new File(dir, "five.view");
        read.save(five);
        assertEquals(5, versionOf(five), "written as sections");
        assertSameAnswers(list, Capture.load(five), ODD);
        assertEquals(21, Capture.load(five).keptReach(), "and still knows how far it kept");
    }

    /** A version 3 file, from before a capture recorded how far it kept, reads back too, as never cut. */
    @Test
    void aVersionThreeFileReadsBackIdentically(@TempDir final File dir) throws IOException
    {
        final Capture.Builder builder = random(11, ODD, 30, 0.3, true);
        final LegacyCapture list = listed(builder, ODD, builder.build());
        final File three = new File(dir, "three.view");
        list.save(three, 3, "far", true, 99L, 21);

        final Capture read = Capture.load(three);

        assertSameAnswers(list, read, ODD);
        assertEquals(-1, read.keptReach(), "never cut, as a version 3 file always reads");
    }

    /**
     * A file cut short anywhere is refused as unreadable, never with anything but an IOException.
     *
     * <p>A capture is read off the main thread now, and the read takes anything else as a failure
     * too; but a sections file says how much follows, and an array sized from a count the file
     * never finished would be gigabytes, or a negative size, before the end of the file was found.
     */
    @Test
    void aFileCutShortAnywhereIsRefused(@TempDir final File dir) throws IOException
    {
        final Capture.Builder builder = random(12, new Capture.Box(-8, 0, -8, 24, 20, 24), 20, 0.4, true);
        final File whole = new File(dir, "whole.view");
        builder.build().save(whole);
        final byte[] bytes = unzipped(whole);
        final File cut = new File(dir, "cut.view");

        for (int length = 0; length < bytes.length; length += Math.max(1, bytes.length / 400))
        {
            zip(cut, bytes, length);
            final int at = length;
            assertThrows(IOException.class, () -> Capture.load(cut), "cut at " + at + " of " + bytes.length);
        }
    }

    /**
     * A file with any one byte wrong is refused with an IOException, or reads as a capture that
     * answers for every block without throwing.
     *
     * <p>A byte flipped in a section's place, its palette or its positions could otherwise put a
     * block outside the box, or an index past the palette, and the view would throw on the main
     * thread every time it drew.
     */
    @Test
    void aFileWithAByteWrongIsRefusedOrStillAnswers(@TempDir final File dir) throws IOException
    {
        final Capture.Box box = new Capture.Box(-8, 0, -8, 24, 20, 24);
        final Capture.Builder builder = random(13, box, 20, 0.4, true);
        final File sections = new File(dir, "sections.view");
        final Capture capture = builder.build();
        capture.save(sections);
        final File list = new File(dir, "list.view");
        listed(builder, box, capture).save(list, 4, "far", true, 1L, -1);

        for (final File whole : new File[] { sections, list })
        {
            assertSomeRefusedAndTheRestAnswer(whole, box, dir);
        }
    }

    /** Changes one bit of each of many bytes of a file in turn, and reads it back each time. */
    private static void assertSomeRefusedAndTheRestAnswer(final File whole, final Capture.Box box, final File dir)
        throws IOException
    {
        final byte[] bytes = unzipped(whole);
        final File wrong = new File(dir, "wrong.view");
        final Random random = new Random(13);
        int refused = 0;

        for (int at = 8; at < bytes.length; at += Math.max(1, bytes.length / 600))
        {
            final byte[] changed = bytes.clone();
            changed[at] ^= (byte) (1 << random.nextInt(8));
            zip(wrong, changed, changed.length);
            final Capture read;
            try
            {
                read = Capture.load(wrong);
            }
            catch (final IOException expected)
            {
                refused++;
                continue;
            }
            catch (final RuntimeException thrown)
            {
                fail(whole.getName() + " byte " + at + " threw " + thrown, thrown);
                return;
            }
            answersEverywhere(read, box, at);
        }
        assertTrue(refused > 0, "some changes to " + whole.getName() + " are refused");
    }

    /** Asks a capture about every block of its box and visits what it keeps, failing on anything thrown. */
    private static void answersEverywhere(final Capture capture, final Capture.Box box, final int at)
    {
        try
        {
            for (int x = box.minX(); x < (box.minX() + box.sizeX()); x++)
            {
                for (int z = box.minZ(); z < (box.minZ() + box.sizeZ()); z++)
                {
                    capture.top(x, z);
                    for (int y = box.minY(); y < (box.minY() + box.sizeY()); y++)
                    {
                        capture.nameAt(x, y, z);
                        capture.isBuried(x, y, z);
                    }
                }
            }
            final List<String> outside = new ArrayList<>();
            capture.forEachKept((x, y, z, isAir) ->
            {
                if (!capture.contains(x, y, z))
                {
                    outside.add(x + "," + y + "," + z);
                }
            });
            assertTrue(outside.isEmpty(), "byte " + at + " read, keeping blocks outside its box: " + outside);
        }
        catch (final RuntimeException thrown)
        {
            fail("byte " + at + " read, then threw " + thrown, thrown);
        }
    }

    /** The west half of a section, x 0 to 7: a box edge running through it. */
    private static final Section.Inside WEST_HALF = new Section.Inside(0, 7, 0, 15, 0, 15);

    /**
     * A full section, one bit a block, with blocks at the given numbers, as {@code Section.write}
     * writes one; {@code said} is the count it claims.
     */
    private static DataInputStream fullSection(final int said, final int... locals) throws IOException
    {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes))
        {
            out.writeShort(said);
            out.writeShort(1);
            out.writeShort(1);
            out.writeBoolean(true);
            final long[] packed = new long[Section.BLOCKS / Long.SIZE];
            for (final int local : locals)
            {
                packed[local >>> 6] |= 1L << (local & 63);
            }
            for (final long word : packed)
            {
                out.writeLong(word);
            }
            out.writeByte(0);
        }
        return new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    }

    /**
     * A full section with a block past the box's edge is refused, where the same block inside it is read.
     *
     * <p>The section is on the world's grid and the box is not, so part of a section can lie outside
     * the box. A block kept there by a bad file is visited as kept, and a view would draw it.
     */
    @Test
    void aFullSectionWithABlockPastTheBoxsEdgeIsRefused() throws IOException
    {
        final File file = new File("edge.view");
        final int inside = Section.local(7, 3, 5);
        final int outside = Section.local(8, 3, 5);

        final Section read = Section.read(fullSection(1, inside), file, 2, WEST_HALF);

        assertEquals(1, read.value(inside), "x 7 is inside, and read");
        assertThrows(IOException.class, () -> Section.read(fullSection(1, outside), file, 2, WEST_HALF), "x 8 is past it");
    }

    /** A full section holding a different number of blocks than it says is refused. */
    @Test
    void aFullSectionHoldingOtherThanItSaysIsRefused() throws IOException
    {
        final File file = new File("count.view");
        final int one = Section.local(1, 1, 1);
        final int two = Section.local(2, 1, 1);

        assertEquals(2, Section.read(fullSection(2, one, two), file, 2, WEST_HALF).entries(), "two said, two held");
        assertThrows(IOException.class, () -> Section.read(fullSection(2, one), file, 2, WEST_HALF), "two said, one held");
        assertThrows(IOException.class, () -> Section.read(fullSection(1, one, two), file, 2, WEST_HALF), "one said, two held");
    }

    /**
     * A section the box only partly covers, said to be all seen air, is refused; a whole one is read.
     *
     * <p>All air is one byte in the file, and all 4096 blocks of the section; through part of a
     * section that would be seen air past the box's edge.
     */
    @Test
    void aSectionPartlyOutsideTheBoxIsNotAllAir() throws IOException
    {
        final File file = new File("air.view");
        final byte[] allAir = { 0, 0, 1 };
        final Section.Inside whole = new Section.Inside(0, 15, 0, 15, 0, 15);

        assertEquals(Section.BLOCKS,
            Section.read(new DataInputStream(new ByteArrayInputStream(allAir)), file, 2, whole).airCount(),
            "a whole section can be all air");
        assertThrows(IOException.class,
            () -> Section.read(new DataInputStream(new ByteArrayInputStream(allAir)), file, 2, WEST_HALF));
    }

    /**
     * A version 5 file as {@code Capture.save} writes one, of a box, with the given sections at
     * the given places in its grid, in the order given.
     */
    private static void sectionsFile(final File file, final Capture.Box box, final int[] places,
        final Section... sections) throws IOException
    {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes))
        {
            out.writeInt(0x4D495257);
            out.writeInt(5);
            out.writeInt(3);
            out.writeBytes("far");
            out.writeBoolean(true);
            out.writeBoolean(false);
            out.writeInt(box.minX());
            out.writeInt(box.minY());
            out.writeInt(box.minZ());
            out.writeInt(box.sizeX());
            out.writeInt(box.sizeY());
            out.writeInt(box.sizeZ());
            out.writeLong(0L);
            out.writeInt(-1);
            out.writeInt(1);
            out.writeInt(13);
            out.writeBytes("minecraft:air");
            out.writeInt(places.length);
            for (int i = 0; i < places.length; i++)
            {
                out.writeInt(places[i]);
                sections[i].write(out);
            }
        }
        final byte[] raw = bytes.toByteArray();
        zip(file, raw, raw.length);
    }

    /** A section all of whose blocks are seen air. */
    private static Section allAir()
    {
        final long[] air = new long[Section.BLOCKS / Long.SIZE];
        Arrays.fill(air, -1L);
        return Section.of(new short[0], new short[0], 0, 0, air, new int[1]);
    }

    /**
     * Sections written out of order, or one place twice, are refused; in order they are read.
     *
     * <p>Read anyway, the second section at a place would silently stand for the first, and the
     * capture would keep less than its file holds.
     */
    @Test
    void sectionsOutOfOrderAreRefused(@TempDir final File dir) throws IOException
    {
        final Capture.Box twoSections = new Capture.Box(0, 0, 0, 16, 32, 16);
        final File file = new File(dir, "order.view");

        sectionsFile(file, twoSections, new int[] { 0, 1 }, allAir(), allAir());
        assertEquals(2 * Section.BLOCKS, Capture.load(file).seenAir(), "in order: both sections of air");

        sectionsFile(file, twoSections, new int[] { 0, 0 }, allAir(), allAir());
        assertThrows(IOException.class, () -> Capture.load(file), "one place twice");
        sectionsFile(file, twoSections, new int[] { 1, 0 }, allAir(), allAir());
        assertThrows(IOException.class, () -> Capture.load(file), "out of order");
    }

    /**
     * A box no capture could have is refused before anything is made for it.
     *
     * <p>A capture's sections are an array the size of its box's grid. A box a block wide and two
     * billion tall passes the old check on its volume, and its grid alone would be half a gigabyte:
     * read off the main thread, that is an OutOfMemoryError on the pool, and the file read again at
     * the next look. A box near the edge of the int range wraps round, and its grid is negative.
     */
    @Test
    void aBoxNoCaptureCouldHaveIsRefused(@TempDir final File dir) throws IOException
    {
        final File file = new File(dir, "box.view");

        sectionsFile(file, new Capture.Box(0, 0, 0, 1, 384, 1), new int[0]);
        assertEquals(384, Capture.load(file).size(), "a column the height of a world is read");

        final int huge = Integer.MAX_VALUE;
        for (final Capture.Box box : new Capture.Box[] { new Capture.Box(0, 0, 0, huge, 1, 1),
            new Capture.Box(0, 0, 0, 1, huge, 1), new Capture.Box(0, 0, 0, 1, 1, huge) })
        {
            sectionsFile(file, box, new int[0]);
            final IOException refused = assertThrows(IOException.class, () -> Capture.load(file), "two billion long: " + box);
            assertTrue(refused.getMessage().contains("impossible shape"), refused.getMessage());
        }
        sectionsFile(file, new Capture.Box(Integer.MAX_VALUE - 4, 0, 0, 10, 10, 10), new int[0]);
        assertThrows(IOException.class, () -> Capture.load(file), "past the end of the int range");
    }

    /** A file's contents, gunzipped. */
    static byte[] unzipped(final File file) throws IOException
    {
        try (InputStream in = new GZIPInputStream(Files.newInputStream(file.toPath())))
        {
            return in.readAllBytes();
        }
    }

    /** Writes the first {@code length} bytes, gzipped, as a capture file is. */
    private static void zip(final File file, final byte[] bytes, final int length) throws IOException
    {
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(file.toPath())))
        {
            out.write(bytes, 0, length);
        }
    }

    /** The version a capture file says it is. */
    private static int versionOf(final File file) throws IOException
    {
        final byte[] bytes = unzipped(file);
        return ((bytes[4] & 0xFF) << 24) | ((bytes[5] & 0xFF) << 16) | ((bytes[6] & 0xFF) << 8) | (bytes[7] & 0xFF);
    }
}
