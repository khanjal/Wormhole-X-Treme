package com.wormhole_xtreme.wormhole.model.ring;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/**
 * The frame arithmetic, which is the part of the animation that can be wrong silently.
 *
 * <p>A ring cannot rise half a block by moving, because blocks only exist at whole
 * positions. It rises half a block by changing which half of its block it fills — bottom
 * slab, then top slab, then bottom slab one block up. Every claim in here is about that
 * alternation and the stack it builds, and getting any of it wrong produces an animation
 * that still runs and simply looks wrong.
 */
public class RingAnimatorTest
{
    /** These tests describe the sequential look; the concurrent one has its own test. */
    private static final RingStyle STYLE = RingStyle.SEQUENTIAL;

    private static Ring ring(final RingOrientation orientation)
    {
        return new Ring(0, 64, 0, RingPattern.ODD, orientation, Material.STONE_SLAB, Material.GLOWSTONE);
    }

    /** The distinct y/half pairs present on a frame, as "y:top" strings. */
    private static Set<String> levels(final List<RingAnimator.Placement> placements)
    {
        final Set<String> out = new HashSet<String>();
        for (final RingAnimator.Placement placement : placements)
        {
            out.add(placement.getY() + ":" + placement.isTop());
        }
        return out;
    }

    @Test
    public void theFirstFrameIsOneRingSittingWhereTheTemplateWas()
    {
        final List<RingAnimator.Placement> frame = RingAnimator.deployFrame(ring(RingOrientation.FLOOR), STYLE, 0);

        assertEquals(RingPattern.ODD.getPerimeter().size(), frame.size(), "exactly one ring");
        assertEquals(1, levels(frame).size());
        assertEquals(64, frame.get(0).getY());
        assertFalse(frame.get(0).isTop(), "a floor ring starts as a bottom slab, where a laid slab rests");
    }

    @Test
    public void aCeilingRingStartsAsATopSlabInsteadOfABottomOne()
    {
        final List<RingAnimator.Placement> frame = RingAnimator.deployFrame(ring(RingOrientation.CEILING), STYLE, 0);
        assertTrue(frame.get(0).isTop(), "a hung slab hangs in the upper half of its block");
        assertEquals(64, frame.get(0).getY());
    }

    @Test
    public void oneHalfStepChangesTheHalfWithoutChangingTheBlock()
    {
        // This is the whole trick. Between these two frames the leading ring has risen half
        // a block while staying in exactly the same block position.
        final Ring floor = ring(RingOrientation.FLOOR);
        final RingAnimator.Placement first = RingAnimator.deployFrame(floor, STYLE, 0).get(0);
        final RingAnimator.Placement second = RingAnimator.deployFrame(floor, STYLE, 1).get(0);

        assertEquals(first.getY(), second.getY());
        assertFalse(first.isTop());
        assertTrue(second.isTop());
    }

    @Test
    public void twoHalfStepsMoveOneBlockAndReturnToTheStartingHalf()
    {
        final Ring floor = ring(RingOrientation.FLOOR);
        final RingAnimator.Placement first = RingAnimator.deployFrame(floor, STYLE, 0).get(0);
        final RingAnimator.Placement third = RingAnimator.deployFrame(floor, STYLE, 2).get(0);

        assertEquals(first.getY() + 1, third.getY());
        assertEquals(first.isTop(), third.isTop());
    }

    @Test
    public void aFloorRingRisesAndACeilingRingDescends()
    {
        final RingAnimator.Placement up = RingAnimator.deployFrame(ring(RingOrientation.FLOOR), STYLE, 2).get(0);
        final RingAnimator.Placement down = RingAnimator.deployFrame(ring(RingOrientation.CEILING), STYLE, 2).get(0);

        assertEquals(65, up.getY());
        assertEquals(63, down.getY());
    }

    @Test
    public void theFirstRingReachesTheTopAloneBeforeTheSecondEvenAppears()
    {
        // The rings go out one at a time, not together. The leader flies all the way to the
        // furthest position and stops there, and only then does the next one emerge.
        final Ring floor = ring(RingOrientation.FLOOR);
        final int perRing = RingPattern.ODD.getPerimeter().size();

        final int secondOut = RingAnimator.emergesOnFrame(STYLE, 1);
        assertEquals(perRing, RingAnimator.deployFrame(floor, STYLE, secondOut - 1).size(),
            "still only one ring in the air right up to the moment the next appears");
        assertEquals(perRing * 2, RingAnimator.deployFrame(floor, STYLE, secondOut).size(),
            "now the second one comes out");
    }

    @Test
    public void eachRingWaitsForTheOneBeforeItToStop()
    {
        for (int index = 1; index < RingAnimator.RING_COUNT; index++)
        {
            final int previousArrives =
                RingAnimator.emergesOnFrame(STYLE, index - 1) + RingAnimator.restingHalfStep(index - 1);
            assertTrue(RingAnimator.emergesOnFrame(STYLE, index) > previousArrives,
                "ring " + index + " left before ring " + (index - 1) + " had stopped");
        }
    }

    @Test
    public void ringsAppearOneMoreAtATimeAsTheSequenceRuns()
    {
        final Ring floor = ring(RingOrientation.FLOOR);
        final int perRing = RingPattern.ODD.getPerimeter().size();
        for (int index = 0; index < RingAnimator.RING_COUNT; index++)
        {
            assertEquals(perRing * (index + 1),
                RingAnimator.deployFrame(floor, STYLE, RingAnimator.emergesOnFrame(STYLE, index)).size(),
                "wrong number of rings out when ring " + index + " emerges");
        }
    }

    @Test
    public void theFinishedStackIsEveryRingWithClearSpaceBetweenThem()
    {
        final Ring floor = ring(RingOrientation.FLOOR);
        final List<RingAnimator.Placement> last =
            RingAnimator.deployFrame(floor, STYLE, RingAnimator.deployFrames(STYLE) - 1);

        assertEquals(RingAnimator.RING_COUNT, levels(last).size(), "every ring at its own level");
        assertEquals(RingPattern.ODD.getPerimeter().size() * RingAnimator.RING_COUNT, last.size());
    }

    @Test
    public void noTwoRingsEverShareABlockAndAHalf()
    {
        // Two rings in the same place would be one ring that looks wrong and, worse, one
        // restore entry claimed twice.
        for (final RingOrientation orientation : RingOrientation.values())
        {
            final Ring subject = ring(orientation);
            for (int frame = 0; frame < RingAnimator.deployFrames(STYLE); frame++)
            {
                final List<RingAnimator.Placement> placements = RingAnimator.deployFrame(subject, STYLE, frame);
                final Set<String> seen = new HashSet<String>();
                for (final RingAnimator.Placement placement : placements)
                {
                    final String key = placement.getX() + ":" + placement.getY() + ":"
                        + placement.getZ() + ":" + placement.isTop();
                    assertTrue(seen.add(key), orientation + " frame " + frame + " doubles up at " + key);
                }
            }
        }
    }

    @Test
    public void ringsThatHaveArrivedStopRatherThanCarryingOnUpward()
    {
        // Trailing rings reach their place in the stack early and have to hold it. If they
        // kept rising the stack would never form, it would just be a column leaving.
        final Ring floor = ring(RingOrientation.FLOOR);
        final List<RingAnimator.Placement> last =
            RingAnimator.deployFrame(floor, STYLE, RingAnimator.deployFrames(STYLE) - 1);

        int atTheBottom = 0;
        for (final RingAnimator.Placement placement : last)
        {
            if ((placement.getY() == 64) && placement.isTop())
            {
                atTheBottom++;
            }
        }
        assertEquals(RingPattern.ODD.getPerimeter().size(), atTheBottom,
            "the last ring out holds its place at the bottom of the stack");
    }

    @Test
    public void theStackHangsHalfABlockClearOfTheFloor()
    {
        // The lowest ring lifts rather than resting where the template was, so the whole
        // stack floats. A bottom slab there would read as part of the floor.
        final Ring floor = ring(RingOrientation.FLOOR);
        final List<RingAnimator.Placement> last =
            RingAnimator.deployFrame(floor, STYLE, RingAnimator.deployFrames(STYLE) - 1);

        for (final RingAnimator.Placement placement : last)
        {
            assertFalse((placement.getY() == 64) && !placement.isTop(),
                "nothing should still be sitting on the floor");
        }
    }

    @Test
    public void settledRingsLeaveHalfABlockOfAirBetweenThem()
    {
        // Half a block apart means one block centre to centre, because a slab is half a
        // block thick. Every neighbouring pair in the stack should be exactly that.
        final Ring floor = ring(RingOrientation.FLOOR);
        final List<RingAnimator.Placement> last =
            RingAnimator.deployFrame(floor, STYLE, RingAnimator.deployFrames(STYLE) - 1);

        final Set<Integer> heights = new HashSet<Integer>();
        for (final RingAnimator.Placement placement : last)
        {
            assertTrue(placement.isTop(), "a settled stack is all top slabs");
            heights.add(Integer.valueOf(placement.getY()));
        }
        assertEquals(RingAnimator.RING_COUNT, heights.size());
        for (int y = 64; y < (64 + RingAnimator.RING_COUNT); y++)
        {
            assertTrue(heights.contains(Integer.valueOf(y)), "no ring at height " + y);
        }
    }

    @Test
    public void retractIsDeployPlayedBackwards()
    {
        // Written as a reversal rather than a second sequence, so the two cannot drift apart
        // and leave a slab stranded on the way down.
        final Ring floor = ring(RingOrientation.FLOOR);
        final int frames = RingAnimator.deployFrames(STYLE);

        assertEquals(levels(RingAnimator.deployFrame(floor, STYLE, frames - 1)),
            levels(RingAnimator.retractFrame(floor, STYLE, 0)));
        assertEquals(levels(RingAnimator.deployFrame(floor, STYLE, 0)),
            levels(RingAnimator.retractFrame(floor, STYLE, frames - 1)));
    }

    @Test
    public void theNearestRingGoesHomeFirstAndTheHighestGoesLast()
    {
        // The order they come back in, and the reason retract is written as a reversal: a
        // sequence that went out furthest-first returns nearest-first on its own.
        final Ring floor = ring(RingOrientation.FLOOR);
        final int top = 64 + (RingAnimator.TOP_HALF_STEP / 2);

        // Not frame one. The nearest ring has its own little journey to undo first — the
        // half block it hangs clear of the floor, and the settle it made on the way in — so
        // it is gone once that many frames have played and not before.
        final List<RingAnimator.Placement> early = RingAnimator.retractFrame(floor, STYLE,
            RingAnimator.journeyFrames(RingAnimator.RING_COUNT - 1));
        assertEquals(RingAnimator.RING_COUNT - 1, levels(early).size(), "one has already gone");
        boolean lowestStillThere = false;
        boolean highestStillThere = false;
        for (final RingAnimator.Placement placement : early)
        {
            if ((placement.getY() == 64) && !placement.isTop())
            {
                lowestStillThere = true;
            }
            if (placement.getY() == top)
            {
                highestStillThere = true;
            }
        }
        assertFalse(lowestStillThere, "the nearest ring is the first to sink away");
        assertTrue(highestStillThere, "the highest is still up there");
    }

    @Test
    public void theLastRingHomeIsTheOneThatFlewHighest()
    {
        final Ring floor = ring(RingOrientation.FLOOR);
        final List<RingAnimator.Placement> lastMoment =
            RingAnimator.retractFrame(floor, STYLE, RingAnimator.deployFrames(STYLE) - 1);

        assertEquals(1, levels(lastMoment).size(), "one ring left");
        assertEquals(64, lastMoment.get(0).getY(), "back at the plane it started from");
    }

    @Test
    public void everyPlacementSitsOnTheRingsOwnPerimeter()
    {
        // The travelling rings are copies of the perimeter and must never stray into the
        // interior, which is where the passengers are standing.
        final Ring floor = ring(RingOrientation.FLOOR);
        final Set<String> interior = new HashSet<String>();
        for (final int[] block : floor.interiorBlocks())
        {
            interior.add(block[0] + ":" + block[2]);
        }

        for (int frame = 0; frame < RingAnimator.deployFrames(STYLE); frame++)
        {
            for (final RingAnimator.Placement placement : RingAnimator.deployFrame(floor, STYLE, frame))
            {
                assertFalse(interior.contains(placement.getX() + ":" + placement.getZ()),
                    "a slab landed on a passenger at frame " + frame);
            }
        }
    }

    @Test
    public void theCountdownLightsAreSetIntoTheSurfaceNotTheSpaceAboveIt()
    {
        // The slabs were laid on top of the floor, so the ring plane is the space the rings
        // rise through. Lighting that would leave the pattern hanging in mid-air; the lights
        // belong a block back, in the floor itself.
        final List<int[]> onFloor = RingAnimator.lightBlocks(ring(RingOrientation.FLOOR));
        assertEquals(RingPattern.ODD.getPerimeter().size(), onFloor.size());
        for (final int[] light : onFloor)
        {
            assertEquals(63, light[1], "a floor ring lights the floor beneath it");
        }

        final List<int[]> onCeiling = RingAnimator.lightBlocks(ring(RingOrientation.CEILING));
        for (final int[] light : onCeiling)
        {
            assertEquals(65, light[1], "a ceiling ring lights the ceiling above it");
        }
    }

    @Test
    public void theRingsStillRiseFromThePlaneTheLightsSitBehind()
    {
        // The lights move but the rings do not: they still start where the template was, so
        // they come up out of the lit pattern rather than out of it.
        final Ring floor = ring(RingOrientation.FLOOR);
        assertEquals(63, RingAnimator.lightBlocks(floor).get(0)[1]);
        assertEquals(64, RingAnimator.deployFrame(floor, STYLE, 0).get(0).getY());
    }

    @Test
    public void concurrentKeepsSeveralRingsInFlightAtOnce()
    {
        // The difference between the two styles, stated as the thing that actually differs:
        // concurrently there are several rings climbing at the same time, sequentially there
        // is never more than one. They still arrive in order, top first.
        final Ring floor = ring(RingOrientation.FLOOR);
        final int perRing = RingPattern.ODD.getPerimeter().size();
        final int whenLeaderArrives = RingAnimator.restingHalfStep(0);

        assertTrue(RingAnimator.deployFrame(floor, RingStyle.CONCURRENT, whenLeaderArrives).size()
            > perRing, "several rings should be up by the time the leader arrives");
        assertEquals(perRing,
            RingAnimator.deployFrame(floor, RingStyle.SEQUENTIAL, whenLeaderArrives).size(),
            "sequentially the leader is still alone");
        assertTrue(RingAnimator.deployFrames(RingStyle.CONCURRENT)
            < RingAnimator.deployFrames(RingStyle.SEQUENTIAL));
    }

    @Test
    public void concurrentIsShorterThanSequentialButBuildsTheSameStack()
    {
        final Ring floor = ring(RingOrientation.FLOOR);
        assertTrue(RingAnimator.deployFrames(RingStyle.CONCURRENT)
            < RingAnimator.deployFrames(RingStyle.SEQUENTIAL));

        assertEquals(
            levels(RingAnimator.deployFrame(floor, RingStyle.CONCURRENT,
                RingAnimator.deployFrames(RingStyle.CONCURRENT) - 1)),
            levels(RingAnimator.deployFrame(floor, RingStyle.SEQUENTIAL,
                RingAnimator.deployFrames(RingStyle.SEQUENTIAL) - 1)),
            "where they end up does not depend on how they got there");
    }

    @Test
    public void bothStylesBringTheNearestRingHomeFirst()
    {
        final Ring floor = ring(RingOrientation.FLOOR);
        for (final RingStyle style : RingStyle.values())
        {
            // Only sequential holds the rest still while the nearest goes — concurrent
            // brings the whole stack down together — so what both share is just the order.
            final List<RingAnimator.Placement> early = RingAnimator.retractFrame(floor, style,
                RingAnimator.journeyFrames(RingAnimator.RING_COUNT - 1));
            assertEquals(RingAnimator.RING_COUNT - 1, levels(early).size(),
                style + " should have lost exactly the nearest ring by now");
        }
    }

    /** The distinct half-step heights present on a frame, highest first. */
    private static List<Integer> heightsOf(final List<RingAnimator.Placement> placements)
    {
        final Set<Integer> seen = new HashSet<Integer>();
        for (final RingAnimator.Placement placement : placements)
        {
            // Two half-steps to a block, and a top slab is the upper half of its own block.
            seen.add(Integer.valueOf((placement.getY() * 2) + (placement.isTop() ? 1 : 0)));
        }
        final List<Integer> out = new ArrayList<Integer>(seen);
        java.util.Collections.sort(out, java.util.Collections.reverseOrder());
        return out;
    }

    @Test
    public void ringsClimbAWholeBlockApartAndFinishHalfABlockApart()
    {
        // The shape of the whole animation. On the way up there is a clear block between
        // rings; in the finished stack there is half a block. Nothing compresses them — the
        // leader stops while the others are still climbing, so the gaps close on their own.
        final Ring floor = ring(RingOrientation.FLOOR);
        final List<Integer> climbing = heightsOf(
            RingAnimator.deployFrame(floor, RingStyle.CONCURRENT, RingAnimator.TRAVEL_GAP * 2));
        for (int i = 0; i < (climbing.size() - 1); i++)
        {
            assertEquals(RingAnimator.TRAVEL_GAP,
                climbing.get(i).intValue() - climbing.get(i + 1).intValue(),
                "rings should climb a clear block apart");
        }

        final List<Integer> settled = heightsOf(RingAnimator.deployFrame(floor, RingStyle.CONCURRENT,
            RingAnimator.deployFrames(RingStyle.CONCURRENT) - 1));
        assertEquals(RingAnimator.RING_COUNT, settled.size());
        for (int i = 0; i < (settled.size() - 1); i++)
        {
            assertEquals(RingAnimator.SPACING,
                settled.get(i).intValue() - settled.get(i + 1).intValue(),
                "the finished stack should be half a block apart");
        }
    }

    @Test
    public void theGapsCloseFromTheTopDownAsEachRingArrives()
    {
        // Because the leader arrives first and stops, the topmost gap is the first to
        // narrow, and the stack tightens downward from there rather than all at once.
        final Ring floor = ring(RingOrientation.FLOOR);
        final List<Integer> midway = heightsOf(RingAnimator.deployFrame(floor, RingStyle.CONCURRENT,
            RingAnimator.restingHalfStep(0) + 1));

        assertEquals(RingAnimator.SPACING, midway.get(0).intValue() - midway.get(1).intValue(),
            "the top pair has already closed up");
        assertEquals(RingAnimator.TRAVEL_GAP, midway.get(1).intValue() - midway.get(2).intValue(),
            "the ones below are still a block apart");
    }

    @Test
    public void nothingEverRisesAboveWhereTheTopRingSettles()
    {
        // What the headroom requirement rests on. Rings stop when they arrive, so the
        // finished stack is also the highest anything ever gets.
        final Ring floor = ring(RingOrientation.FLOOR);
        final int settledTop = 64 + (RingAnimator.TOP_HALF_STEP / 2);
        for (final RingStyle style : RingStyle.values())
        {
            for (int frame = 0; frame < RingAnimator.deployFrames(style); frame++)
            {
                for (final RingAnimator.Placement placement : RingAnimator.deployFrame(floor, style, frame))
                {
                    assertTrue(placement.getY() <= settledTop,
                        style + " went above the finished stack at frame " + frame);
                }
            }
        }
    }

    @Test
    public void theFlashTouchesEveryRingExactlyOnce()
    {
        // The transport itself, given an animation rather than being an instant nobody sees.
        for (final RingFlashDirection direction : RingFlashDirection.values())
        {
            final Set<Integer> touched = new HashSet<Integer>();
            for (int frame = 0; frame < RingAnimator.flashFrames(); frame++)
            {
                assertTrue(touched.add(Integer.valueOf(RingAnimator.litRing(direction, frame))),
                    direction + " lit the same ring twice");
            }
            assertEquals(RingAnimator.RING_COUNT, touched.size());
        }
    }

    @Test
    public void theFlashRunsDownFromTheTopOrUpFromTheFloor()
    {
        final Ring floor = ring(RingOrientation.FLOOR);
        final int highest = 64 + (RingAnimator.TOP_HALF_STEP / 2);

        final int firstDown = RingAnimator.litRing(RingFlashDirection.TOP_DOWN, 0);
        assertEquals(highest, RingAnimator.ringAtRest(floor, firstDown).get(0).getY(),
            "top down starts at the ring that flew highest");

        final int firstUp = RingAnimator.litRing(RingFlashDirection.BOTTOM_UP, 0);
        assertEquals(64, RingAnimator.ringAtRest(floor, firstUp).get(0).getY(),
            "bottom up starts at the floor");
    }

    @Test
    public void theSettledStackIsWhatEveryFlashFrameIsDrawnOver()
    {
        // The lit ring is drawn over the stack, not instead of it, so nothing appears to
        // move while the light passes through.
        final Ring floor = ring(RingOrientation.FLOOR);
        for (final RingStyle style : RingStyle.values())
        {
            assertEquals(RingAnimator.RING_COUNT * RingPattern.ODD.getPerimeter().size(),
                RingAnimator.settledStack(floor, style).size(), style + " settled stack");
        }
    }

    @Test
    public void theTwoEndsOfAPairCanDeployAtDifferentSpeeds()
    {
        // Style is per end, so a base and its outpost need not match. They still have to
        // finish together, which is arranged by waiting for the slower of the two.
        assertTrue(RingAnimator.deployFrames(RingStyle.SEQUENTIAL)
            > RingAnimator.deployFrames(RingStyle.CONCURRENT));
    }

    @Test
    public void aPlayerMayTypeEitherTheRealNameOrAFriendlierOne()
    {
        // The stored value stays CONCURRENT or SEQUENTIAL, because those describe what the
        // setting does — how many rings are in the air at once — which stays true whatever
        // the tick rate is. Naming it by speed would claim the same ground as deploy-ticks
        // and be contradicted by it. Players still get to type the obvious word.
        assertEquals(RingStyle.CONCURRENT, RingStyle.parse("fast"));
        assertEquals(RingStyle.CONCURRENT, RingStyle.parse("CONCURRENT"));
        assertEquals(RingStyle.CONCURRENT, RingStyle.parse(" Quick "));
        assertEquals(RingStyle.SEQUENTIAL, RingStyle.parse("slow"));
        assertEquals(RingStyle.SEQUENTIAL, RingStyle.parse("sequential"));
        assertEquals(RingStyle.SEQUENTIAL, RingStyle.parse("STEPPED"));
    }

    @Test
    public void anythingElseIsRefusedRatherThanGuessedAt()
    {
        assertNull(RingStyle.parse("sideways"));
        assertNull(RingStyle.parse(""));
        assertNull(RingStyle.parse(null));
    }

    @Test
    public void theFriendlyNamesMatchWhichStyleIsActuallyQuicker()
    {
        // If "fast" ever stopped being the shorter of the two, the alias would be a lie.
        assertTrue(RingAnimator.deployFrames(RingStyle.parse("fast"))
            < RingAnimator.deployFrames(RingStyle.parse("slow")));
    }
}
