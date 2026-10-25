package com.wormhole_xtreme.wormhole.model.window;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.model.GateSource;
import com.wormhole_xtreme.wormhole.model.window.WindowShape.Spot;

/**
 * What a window's wall hides of a drawn block, worked out by hand on a small flat window.
 *
 * <p>The occlusion functions decide, per candidate block per redraw, whether a block is seen through
 * an opening and whether the wall hides the rest of it. They read only their arguments, so each case
 * here is a window, an eye and a block with the answer worked out on paper -- the arithmetic is never
 * asked of the code under test. The window is one banner wide and two tall at x 10, y 64..65, in the
 * face z = 20, looked at from the low-z side (so a step into the wall is +z). An eye at (10.5, 65, 19)
 * is one block in front of the face; a block at z = 20 then projects at scale 1 on its near corners
 * and one half on its far ones, which keeps every outline exact in binary.
 */
class WindowOcclusionTest
{
    private static final Spot UP_THE_WALL = new Spot(0, 0, 1);
    private static final WindowShape SHAPE = new WindowShape(new Spot(10, 64, 20), UP_THE_WALL,
        new Spot(0, 0, 0), UP_THE_WALL, false, 1, 2);
    private static final List<Spot> OPENING = List.of(new Spot(10, 64, 20), new Spot(10, 65, 20));
    private static final double MOST_BESIDE = 0.05;

    /** Stub-only mocks: a shadow read asks the world thousands of times, and a recording mock keeps every ask. */
    private World world;
    private Block anchor;
    private Block stone;
    private Block air;
    /** The solid blocks of the world {@link #world} answers for. */
    private final Set<Long> solidWorld = new HashSet<>();

    @BeforeEach
    void setUp()
    {
        WindowSight.clear();
        solidWorld.clear();
        final BlockData solidData = mock(BlockData.class, withSettings().stubOnly());
        when(solidData.isOccluding()).thenReturn(true);
        final BlockData openData = mock(BlockData.class, withSettings().stubOnly());
        when(openData.isOccluding()).thenReturn(false);
        stone = mock(Block.class, withSettings().stubOnly());
        when(stone.getBlockData()).thenReturn(solidData);
        air = mock(Block.class, withSettings().stubOnly());
        when(air.getBlockData()).thenReturn(openData);
        world = mock(World.class, withSettings().stubOnly());
        when(world.getName()).thenReturn("occlusion");
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(call ->
            solidWorld.contains(Cells.key(call.getArgument(0), call.getArgument(1), call.getArgument(2))) ? stone : air);
        anchor = mock(Block.class, withSettings().stubOnly());
        when(anchor.getWorld()).thenReturn(world);
    }

    @AfterEach
    void tearDown()
    {
        WindowSight.clear();
    }

    private WindowState window(final WindowShape shape, final List<Spot> open)
    {
        return new WindowState(new GateSource("gate:Abydos", anchor, shape, open, null, "Chulak", 16), null);
    }

    private static long face(final int across, final int y)
    {
        return WindowFace.faceKey(SHAPE, across, y);
    }

    private static WindowShape at(final int x, final int z)
    {
        return new WindowShape(new Spot(x, 64, z), UP_THE_WALL, new Spot(0, 0, 0), UP_THE_WALL, false, 1, 2);
    }

    private static Location eye(final double x, final double y, final double z)
    {
        return new Location(null, x, y, z);
    }

    // ---- cover: what a block of the face keeps out of sight ----

    /** The opening hides all of a block behind it; a wall's margin has no say over a cell that is not wall. */
    @Test
    void anOpeningCellHidesAllOfItselfWhateverMarginIsRecordedForIt()
    {
        final WindowState window = window(SHAPE, OPENING);
        window.margin = Map.of(face(10, 64), 15);

        assertArrayEquals(new double[] { 10.0, 11.0, 64.0, 65.0 },
            WindowOcclusion.cover(window, 10, 64, Set.of(), Set.of()), 0.0);
    }

    /** A solid block of the wall with no open side hides all of itself. */
    @Test
    void aSolidBlockWithNoOpenSideHidesAllOfItself()
    {
        final WindowState window = window(SHAPE, OPENING);
        window.solid = Set.of(face(12, 66));

        assertArrayEquals(new double[] { 12.0, 13.0, 66.0, 67.0 },
            WindowOcclusion.cover(window, 12, 66, Set.of(), Set.of()), 0.0);
    }

    /** A wall block open on its before side keeps back the outer half on that side: low across moves in. */
    @Test
    void aWallBlockOpenBeforeKeepsBackItsLowAcrossHalf()
    {
        assertArrayEquals(new double[] { 12.5, 13.0, 66.0, 67.0 }, ringBlock(WindowFace.OPEN_BEFORE), 0.0);
    }

    /** A wall block open on its after side keeps back the outer half on that side: high across moves in. */
    @Test
    void aWallBlockOpenAfterKeepsBackItsHighAcrossHalf()
    {
        assertArrayEquals(new double[] { 12.0, 12.5, 66.0, 67.0 }, ringBlock(WindowFace.OPEN_AFTER), 0.0);
    }

    /** A wall block open below keeps back its lower half. */
    @Test
    void aWallBlockOpenBelowKeepsBackItsLowerHalf()
    {
        assertArrayEquals(new double[] { 12.0, 13.0, 66.5, 67.0 }, ringBlock(WindowFace.OPEN_BELOW), 0.0);
    }

    /** A wall block open above keeps back its upper half. */
    @Test
    void aWallBlockOpenAboveKeepsBackItsUpperHalf()
    {
        assertArrayEquals(new double[] { 12.0, 13.0, 66.0, 66.5 }, ringBlock(WindowFace.OPEN_ABOVE), 0.0);
    }

    /** A corner block open on two sides loses a half on each, and a block open on all four is a point in the middle. */
    @Test
    void aWallBlockOpenOnSeveralSidesLosesAHalfOnEach()
    {
        assertArrayEquals(new double[] { 12.5, 13.0, 66.0, 66.5 },
            ringBlock(WindowFace.OPEN_BEFORE | WindowFace.OPEN_ABOVE), 0.0);
        assertArrayEquals(new double[] { 12.5, 12.5, 66.5, 66.5 },
            ringBlock(WindowFace.OPEN_BEFORE | WindowFace.OPEN_AFTER | WindowFace.OPEN_ABOVE | WindowFace.OPEN_BELOW), 0.0);
    }

    private double[] ringBlock(final int open)
    {
        final WindowState window = window(SHAPE, OPENING);
        window.solid = Set.of(face(12, 66));
        window.margin = Map.of(face(12, 66), open);
        return WindowOcclusion.cover(window, 12, 66, Set.of(), Set.of());
    }

    /** A block that is neither the opening nor solid hides nothing. */
    @Test
    void aBlockOfOpenAirHidesNothing()
    {
        final WindowState window = window(SHAPE, OPENING);

        assertSame(WindowShape.HIDES_NOTHING, WindowOcclusion.cover(window, 14, 66, Set.of(), Set.of()),
            "no wall and no opening there");
    }

    /** A solid block that every window's opening shows through hides nothing, margin or not. */
    @Test
    void aSolidBlockInEveryOpeningHidesNothing()
    {
        final WindowState window = window(SHAPE, OPENING);
        window.solid = Set.of(face(12, 66));
        window.margin = Map.of(face(12, 66), WindowFace.OPEN_BEFORE);

        assertSame(WindowShape.HIDES_NOTHING, WindowOcclusion.cover(window, 12, 66, Set.of(face(12, 66)), Set.of()),
            "open for all, so the wall there hides nothing");
    }

    /** A block hidden by something real in front hides all of itself, wall or not, even on the open edge of the ring. */
    @Test
    void aShieldedBlockHidesAllOfItselfEvenOnTheRing()
    {
        final WindowState window = window(SHAPE, OPENING);
        window.solid = Set.of(face(12, 66));
        window.margin = Map.of(face(12, 66), WindowFace.OPEN_BEFORE | WindowFace.OPEN_ABOVE);

        assertArrayEquals(new double[] { 12.0, 13.0, 66.0, 67.0 },
            WindowOcclusion.cover(window, 12, 66, Set.of(), Set.of(face(12, 66))), 0.0, "the ring's margin is not kept back");
        assertArrayEquals(new double[] { 14.0, 15.0, 66.0, 67.0 },
            WindowOcclusion.cover(window, 14, 66, Set.of(), Set.of(face(14, 66))), 0.0, "open air behind a real block is hidden too");
    }

    /** A shielded block that is open for every window is not hidden by the shield, since the opening shows it. */
    @Test
    void aShieldedBlockOpenForEveryWindowHidesNothing()
    {
        final WindowState window = window(SHAPE, OPENING);

        assertSame(WindowShape.HIDES_NOTHING,
            WindowOcclusion.cover(window, 14, 66, Set.of(face(14, 66)), Set.of(face(14, 66))),
            "the shield counts only where some wall stands");
    }

    /** This window's own opening hides what lands on it, even where every window's opening is open. */
    @Test
    void thisWindowsOwnOpeningHidesAllOfItselfEvenWhereEveryWindowIsOpen()
    {
        final WindowState window = window(SHAPE, OPENING);

        assertArrayEquals(new double[] { 10.0, 11.0, 65.0, 66.0 },
            WindowOcclusion.cover(window, 10, 65, Set.of(face(10, 65)), Set.of()), 0.0);
    }

    // ---- withinFace: a shadow clamped to the face that is read ----

    /** A shadow narrower than a block marks none, and the range comes back empty rather than inverted into cells. */
    @Test
    void aShadowInsideOneBlockMarksNoBlockAtAll()
    {
        final int[] span = { 0, 18 };

        assertArrayEquals(new int[] { 11, 9, 64, 64 },
            WindowOcclusion.withinFace(new double[] { 10.2, 10.8, 64.0, 65.0 }, span, 56, 82),
            "the first whole block starts past where the last one ends");
    }

    /** A shadow that ends exactly on a block edge covers the block before that edge, and that one only. */
    @Test
    void aShadowEndingOnABlockEdgeCoversTheBlockBeforeIt()
    {
        assertArrayEquals(new int[] { 10, 11, 64, 65 },
            WindowOcclusion.withinFace(new double[] { 10.0, 12.0, 64.0, 66.0 }, new int[] { 0, 18 }, 56, 82),
            "10.0 to 12.0 is blocks 10 and 11, and 12 is not touched");
    }

    /** A shadow wholly outside the face read gives a range that is empty at either end. */
    @Test
    void aShadowWhollyOutsideTheFaceReadGivesAnEmptyRange()
    {
        final int[] span = { 0, 18 };

        assertArrayEquals(new int[] { 31, 18, 70, 70 },
            WindowOcclusion.withinFace(new double[] { 30.2, 32.0, 70.0, 71.0 }, span, 56, 82), "past the high end across");
        assertArrayEquals(new int[] { 0, -3, 56, 56 },
            WindowOcclusion.withinFace(new double[] { -5.5, -2.0, 55.5, 57.0 }, span, 56, 82), "past the low end across");
        assertArrayEquals(new int[] { 3, 4, 83, 82 },
            WindowOcclusion.withinFace(new double[] { 2.5, 5.0, 83.0, 83.5 }, span, 56, 82), "above the highest row read");
    }

    /** Each bound of the clamp is its own: a shadow past only one of them is cut at that one. */
    @Test
    void aShadowPastOnlyOneBoundIsCutAtThatBoundAlone()
    {
        final int[] span = { 4, 18 };

        assertArrayEquals(new int[] { 4, 12, 60, 62 },
            WindowOcclusion.withinFace(new double[] { 1.0, 13.0, 60.0, 63.0 }, span, 56, 82), "low across cut");
        assertArrayEquals(new int[] { 6, 18, 60, 62 },
            WindowOcclusion.withinFace(new double[] { 6.0, 30.0, 60.0, 63.0 }, span, 56, 82), "high across cut");
        assertArrayEquals(new int[] { 6, 12, 56, 62 },
            WindowOcclusion.withinFace(new double[] { 6.0, 13.0, 40.0, 63.0 }, span, 56, 82), "low y cut");
        assertArrayEquals(new int[] { 6, 12, 60, 82 },
            WindowOcclusion.withinFace(new double[] { 6.0, 13.0, 60.0, 99.0 }, span, 56, 82), "high y cut");
    }

    // ---- hideUnder: the blocks of the face a shadow covers whole ----

    /** A block that throws no shadow marks nothing. */
    @Test
    void aBlockThatThrowsNoShadowHidesNothing()
    {
        final Set<Long> hidden = new HashSet<>();

        WindowOcclusion.hideUnder(hidden, SHAPE, WindowShape.UNSEEN, new int[] { 0, 18 }, 56, 82);

        assertTrue(hidden.isEmpty(), "no shadow, no cells");
    }

    /** A shadow marks exactly the face blocks wholly inside it, the last row and column included. */
    @Test
    void aShadowMarksExactlyTheFaceBlocksWhollyInsideIt()
    {
        final Set<Long> hidden = new HashSet<>();

        WindowOcclusion.hideUnder(hidden, SHAPE, new double[] { 10.0, 12.0, 64.0, 66.0 }, new int[] { 0, 18 }, 56, 82);

        assertEquals(Set.of(face(10, 64), face(10, 65), face(11, 64), face(11, 65)), hidden,
            "blocks 10 and 11 across, 64 and 65 up, and nothing beyond");
    }

    // ---- seenThrough: where a block behind the opening lands on it ----

    /** A block straight behind the opening lands on exactly the opening. */
    @Test
    void aBlockStraightBehindTheOpeningLandsOnIt()
    {
        final WindowState window = window(SHAPE, OPENING);

        assertArrayEquals(new double[] { 10.0, 11.0, 64.0, 65.0 },
            WindowOcclusion.seenThrough(eye(10.5, 65.0, 19.0), window, 10, 64, 20, List.of(window), 0.0), 0.0);
    }

    /** A block in front of the face, on the eye's side of it, is not behind the opening at all. */
    @Test
    void aBlockInFrontOfTheFaceIsNotSeenThroughIt()
    {
        final WindowState window = window(SHAPE, OPENING);

        assertSame(WindowShape.UNSEEN,
            WindowOcclusion.seenThrough(eye(10.5, 65.0, 19.0), window, 10, 64, 17, List.of(window), 0.0));
    }

    /** A block whose outline lands beside the opening, off its frame, is not seen through it. */
    @Test
    void aBlockLandingBesideTheOpeningAndItsFrameIsNotSeen()
    {
        final WindowState window = window(SHAPE, OPENING);
        window.frame = List.of(new Spot(11, 64, 20));

        assertSame(WindowShape.UNSEEN,
            WindowOcclusion.seenThrough(eye(10.5, 65.0, 19.0), window, 14, 64, 20, List.of(window), 0.0));
    }

    /** A block landing only on the frame round the opening counts as seen, so it is hidden rather than left out. */
    @Test
    void aBlockLandingOnlyOnTheFrameIsSeen()
    {
        final WindowState window = window(SHAPE, OPENING);
        final Location eye = eye(10.5, 65.0, 19.0);

        assertSame(WindowShape.UNSEEN, WindowOcclusion.seenThrough(eye, window, 12, 64, 20, List.of(window), 0.0),
            "with no frame there, nothing to land on");
        window.frame = List.of(new Spot(11, 64, 20));
        assertArrayEquals(new double[] { 11.25, 13.0, 64.0, 65.0 },
            WindowOcclusion.seenThrough(eye, window, 12, 64, 20, List.of(window), 0.0), 0.0, "the frame block holds it");
    }

    /** A far block's outline is widened by half a cell on the low across side before it is matched to the opening. */
    @Test
    void theSpreadWidensTheOutlineOnItsLowAcrossSide()
    {
        final WindowState window = window(SHAPE, OPENING);
        final Location eye = eye(10.5, 65.0, 19.0);

        assertSame(WindowShape.UNSEEN, WindowOcclusion.seenThrough(eye, window, 12, 64, 20, List.of(window), 0.0),
            "starting at 11.25 it misses the opening, which ends at 11");
        assertArrayEquals(new double[] { 10.75, 13.5, 63.5, 65.5 },
            WindowOcclusion.seenThrough(eye, window, 12, 64, 20, List.of(window), 0.5), 0.0);
    }

    /** The same on the high across side. */
    @Test
    void theSpreadWidensTheOutlineOnItsHighAcrossSide()
    {
        final WindowState window = window(SHAPE, OPENING);
        final Location eye = eye(10.5, 65.0, 19.0);

        assertSame(WindowShape.UNSEEN, WindowOcclusion.seenThrough(eye, window, 8, 64, 20, List.of(window), 0.0),
            "ending at 9.75 it misses the opening, which starts at 10");
        assertArrayEquals(new double[] { 7.5, 10.25, 63.5, 65.5 },
            WindowOcclusion.seenThrough(eye, window, 8, 64, 20, List.of(window), 0.5), 0.0);
    }

    /** The same on the low y side: an outline that starts at the opening's top is widened onto it. */
    @Test
    void theSpreadWidensTheOutlineOnItsLowYSide()
    {
        final WindowState window = window(SHAPE, OPENING);
        final Location eye = eye(10.5, 65.0, 19.0);

        assertSame(WindowShape.UNSEEN, WindowOcclusion.seenThrough(eye, window, 10, 67, 20, List.of(window), 0.0),
            "starting at 66 it misses the opening, which ends at 66");
        assertArrayEquals(new double[] { 9.5, 11.5, 65.5, 68.5 },
            WindowOcclusion.seenThrough(eye, window, 10, 67, 20, List.of(window), 0.5), 0.0);
    }

    /** The same on the high y side: an outline that ends at the opening's bottom is widened onto it. */
    @Test
    void theSpreadWidensTheOutlineOnItsHighYSide()
    {
        final WindowState window = window(SHAPE, OPENING);
        final Location eye = eye(10.5, 65.0, 19.0);

        assertSame(WindowShape.UNSEEN, WindowOcclusion.seenThrough(eye, window, 10, 62, 20, List.of(window), 0.0),
            "ending at 64 it misses the opening, which starts at 64");
        assertArrayEquals(new double[] { 9.5, 11.5, 61.5, 64.5 },
            WindowOcclusion.seenThrough(eye, window, 10, 62, 20, List.of(window), 0.5), 0.0);
    }

    /** Where two openings in one face both show a block, the one whose middle the line of sight passes nearer has it. */
    @Test
    void aNeighbouringOpeningThatTheSightPassesNearerTakesTheBlock()
    {
        final WindowState left = window(at(10, 20), List.of(new Spot(10, 64, 20)));
        final WindowState right = window(at(11, 20), List.of(new Spot(11, 64, 20)));
        final List<WindowState> seeing = List.of(left, right);
        final Location eye = eye(10.5, 65.0, 19.0);

        assertArrayEquals(new double[] { 9.5, 11.5, 63.5, 65.5 },
            WindowOcclusion.seenThrough(eye, left, 10, 64, 20, seeing, 0.5), 0.0, "its middle is dead centre of the left one");
        assertSame(WindowShape.UNSEEN, WindowOcclusion.seenThrough(eye, right, 10, 64, 20, seeing, 0.5),
            "the left opening's middle is nearer, and its opening shows the block too");
    }

    /** A tie between two openings goes to neither: the block is seen through both rather than lost to both. */
    @Test
    void aTieBetweenTwoOpeningsLeavesTheBlockSeenThroughBoth()
    {
        final WindowState left = window(at(10, 20), List.of(new Spot(10, 64, 20)));
        final WindowState right = window(at(12, 20), List.of(new Spot(12, 64, 20)));
        final List<WindowState> seeing = List.of(left, right);
        final Location eye = eye(11.5, 65.0, 19.0);
        final double[] both = { 10.5, 12.5, 63.5, 65.5 };

        assertArrayEquals(both, WindowOcclusion.seenThrough(eye, left, 11, 64, 20, seeing, 0.5), 0.0, "left, one block off");
        assertArrayEquals(both, WindowOcclusion.seenThrough(eye, right, 11, 64, 20, seeing, 0.5), 0.0, "right, one block off");
    }

    /** An opening in another face is no rival, however near its middle, since a line of sight cannot pass through both. */
    @Test
    void anOpeningInAnotherFaceDoesNotTakeTheBlock()
    {
        final WindowState mine = window(at(11, 20), List.of(new Spot(11, 64, 20)));
        final WindowState behind = window(at(10, 25), List.of(new Spot(10, 64, 25)));

        assertArrayEquals(new double[] { 9.5, 11.5, 63.5, 65.5 },
            WindowOcclusion.seenThrough(eye(10.5, 65.0, 19.0), mine, 10, 64, 20, List.of(mine, behind), 0.5), 0.0);
    }

    /** A neighbour whose own opening the outline misses is no rival either, nearer its middle or not. */
    @Test
    void aNeighbourWhoseOpeningTheOutlineMissesDoesNotTakeTheBlock()
    {
        final WindowState mine = window(at(11, 20), List.of(new Spot(11, 64, 20)));
        final WindowState elsewhere = window(at(10, 20), List.of(new Spot(30, 64, 20)));

        assertArrayEquals(new double[] { 9.5, 11.5, 63.5, 65.5 },
            WindowOcclusion.seenThrough(eye(10.5, 65.0, 19.0), mine, 10, 64, 20, List.of(mine, elsewhere), 0.5), 0.0);
    }

    // ---- coveredBy: whether the wall hides the rest of a block's outline ----

    /** An outline wholly on the opening is covered. */
    @Test
    void anOutlineWhollyOnTheOpeningIsCovered()
    {
        final WindowState window = window(SHAPE, OPENING);

        assertTrue(WindowOcclusion.coveredBy(window, new double[] { 10.0, 11.0, 64.0, 65.0 }, Set.of(), Set.of(), MOST_BESIDE));
    }

    /** The share allowed beside the opening is the one asked for: a sliver passes at a twentieth and fails at none. */
    @Test
    void theShareAllowedBesideTheOpeningIsTheOneAskedFor()
    {
        final WindowState window = window(SHAPE, OPENING);
        final double[] sliver = { 10.0, 11.04, 64.0, 65.0 };

        assertTrue(WindowOcclusion.coveredBy(window, sliver, Set.of(), Set.of(), MOST_BESIDE), "1.0 of 1.04 is over 95 percent");
        assertFalse(WindowOcclusion.coveredBy(window, sliver, Set.of(), Set.of(), 0.0), "but not all of it");
    }

    /** Half an outline on the opening is covered when half may be beside it, and not when a twentieth may. */
    @Test
    void halfAnOutlineBesideTheOpeningIsCoveredOnlyWhenHalfMayBe()
    {
        final WindowState window = window(SHAPE, OPENING);
        final double[] half = { 10.0, 12.0, 64.0, 65.0 };

        assertFalse(WindowOcclusion.coveredBy(window, half, Set.of(), Set.of(), MOST_BESIDE));
        assertTrue(WindowOcclusion.coveredBy(window, half, Set.of(), Set.of(), 0.5));
    }

    /** The blocks real things shield from the eye count towards what covers an outline. */
    @Test
    void theShieldedBlocksCountTowardsWhatCoversAnOutline()
    {
        final WindowState window = window(SHAPE, OPENING);
        final double[] beside = { 20.0, 21.0, 64.0, 65.0 };

        assertFalse(WindowOcclusion.coveredBy(window, beside, Set.of(), Set.of(), MOST_BESIDE), "open air hides nothing");
        assertTrue(WindowOcclusion.coveredBy(window, beside, Set.of(), Set.of(face(20, 64)), MOST_BESIDE), "a shield there does");
    }

    /** A wall block that every window's opening shows through stops covering an outline. */
    @Test
    void aWallBlockOpenForEveryWindowStopsCoveringAnOutline()
    {
        final WindowState window = window(SHAPE, OPENING);
        window.solid = Set.of(face(12, 64));
        final double[] onWall = { 12.0, 13.0, 64.0, 65.0 };

        assertTrue(WindowOcclusion.coveredBy(window, onWall, Set.of(), Set.of(), MOST_BESIDE), "wall there");
        assertFalse(WindowOcclusion.coveredBy(window, onWall, Set.of(face(12, 64)), Set.of(), MOST_BESIDE), "open for all");
    }

    // ---- shielded: the face blocks hidden from an eye by real blocks in front ----

    /** A solid block in the layer nearest the face throws a shadow that hides the face block it covers whole. */
    @Test
    void aSolidBlockNextToTheFaceHidesTheFaceBlockBehindIt()
    {
        solidWorld.add(Cells.key(10, 64, 19));

        assertEquals(Set.of(face(10, 64)),
            WindowOcclusion.shielded(window(SHAPE, OPENING), eye(10.5, 65.0, 10.0), 0L),
            "its shadow runs 9.94 to 11.06 across and 63.89 to 65 up: whole inside block 10, row 64 only");
    }

    /** The eighth layer in front of the face is still read, and its shadow is wide. */
    @Test
    void aSolidBlockEightLayersInFrontStillThrowsItsShadow()
    {
        solidWorld.add(Cells.key(10, 64, 12));

        final Set<Long> expected = new HashSet<>();
        for (int across = 8; across <= 12; across++)
        {
            for (int y = 60; y <= 64; y++)
            {
                expected.add(face(across, y));
            }
        }
        assertEquals(expected, WindowOcclusion.shielded(window(SHAPE, OPENING), eye(10.5, 65.0, 10.0), 0L),
            "scale 5 and 3.33 from ten blocks out: 8 to 13 across, 60 to 65 up, 25 whole blocks");
    }

    /** The ninth layer is past the reach and is not read. */
    @Test
    void aSolidBlockNineLayersInFrontIsNotRead()
    {
        solidWorld.add(Cells.key(10, 64, 11));

        assertTrue(WindowOcclusion.shielded(window(SHAPE, OPENING), eye(10.5, 65.0, 10.0), 0L).isEmpty(),
            "nothing beyond eight blocks of the face is looked at");
    }

    /** A solid block behind the eye, however near the face, throws no shadow on it. */
    @Test
    void aSolidBlockBehindTheEyeShieldsNothing()
    {
        solidWorld.add(Cells.key(10, 64, 13));

        assertTrue(WindowOcclusion.shielded(window(SHAPE, OPENING), eye(10.5, 65.0, 15.5), 0L).isEmpty(),
            "only blocks between the eye and the face hide it");
    }

    /** The rows read run to eight above the first row over the opening, so a hut's roof shields the face. */
    @Test
    void aSolidBlockEightAboveTheRowOverTheOpeningIsRead()
    {
        solidWorld.add(Cells.key(10, 73, 19));

        assertEquals(Set.of(face(10, 73), face(10, 74)),
            WindowOcclusion.shielded(window(SHAPE, OPENING), eye(10.5, 65.0, 10.0), 0L),
            "its shadow runs 73 to 75 up, and the top row read is 74");
    }

    /** The top row read, 74, is itself looked at: a block there throws its shadow, not only a block below it. */
    @Test
    void aSolidBlockInTheTopRowReadStillThrowsItsShadow()
    {
        solidWorld.add(Cells.key(10, 74, 19));

        assertEquals(Set.of(face(10, 74)),
            WindowOcclusion.shielded(window(SHAPE, OPENING), eye(10.5, 65.0, 10.0), 0L),
            "its shadow runs 74 to 76.1 up: whole inside row 74, and row 75 is past what is read");
    }

    /** The rows read run eight below the opening's foot, so a floor in front shields the face. */
    @Test
    void aSolidBlockEightBelowTheOpeningFootIsRead()
    {
        solidWorld.add(Cells.key(10, 56, 19));

        assertEquals(Set.of(face(10, 56)),
            WindowOcclusion.shielded(window(SHAPE, OPENING), eye(10.5, 65.0, 10.0), 0L),
            "eight below the foot at 64; its shadow runs 55 to 57 up, cut to row 56, the lowest read");
    }

    /** A face that looks along x is read along x: the same case turned a quarter. */
    @Test
    void aFaceLookingAlongXIsReadAlongX()
    {
        final WindowShape turned = new WindowShape(new Spot(20, 64, 10), new Spot(1, 0, 0), new Spot(0, 0, 0),
            new Spot(1, 0, 0), false, 1, 2);
        solidWorld.add(Cells.key(19, 64, 10));

        assertEquals(Set.of(WindowFace.faceKey(turned, 10, 64)),
            WindowOcclusion.shielded(window(turned, List.of(new Spot(20, 64, 10), new Spot(20, 65, 10))),
                eye(10.0, 65.0, 10.5), 0L),
            "the layer in front is x 19, and the block it hides is z 10 of the face at x 20");
    }

    /** A face whose wall is on the low side of its coordinate is read from the other way round. */
    @Test
    void aFaceLookingTheOtherWayIsReadFromTheOtherSide()
    {
        final WindowShape back = new WindowShape(new Spot(10, 64, 20), new Spot(0, 0, -1), new Spot(0, 0, 0),
            new Spot(0, 0, -1), false, 1, 2);
        solidWorld.add(Cells.key(10, 64, 21));

        assertEquals(Set.of(WindowFace.faceKey(back, 10, 64)),
            WindowOcclusion.shielded(window(back, List.of(new Spot(10, 64, 20), new Spot(10, 65, 20))),
                eye(10.5, 65.0, 30.0), 0L),
            "into -z: the layers in front are z 21 and up, read from the eye at z 30");
    }
}
