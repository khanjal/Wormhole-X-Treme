package com.wormhole_xtreme.wormhole.model.ring;

import static org.junit.jupiter.api.Assertions.*;

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
    public void theCountdownLightsAreThePerimeterInItsOwnPlane()
    {
        final Ring floor = ring(RingOrientation.FLOOR);
        final List<int[]> lights = RingAnimator.lightBlocks(floor);

        assertEquals(RingPattern.ODD.getPerimeter().size(), lights.size());
        for (final int[] light : lights)
        {
            assertEquals(64, light[1], "nothing has moved yet during the countdown");
        }
    }

    @Test
    public void concurrentRingsAreAllOnTheirWayAtOnceAndArriveTogether()
    {
        // The other look. Rings leave one gap apart rather than waiting for each other, so
        // the stack rises as a group and the whole thing is over in one ring's journey.
        final Ring floor = ring(RingOrientation.FLOOR);
        final int perRing = RingPattern.ODD.getPerimeter().size();

        assertEquals(perRing * RingAnimator.RING_COUNT,
            RingAnimator.deployFrame(floor, RingStyle.CONCURRENT, RingAnimator.TOP_HALF_STEP).size(),
            "every ring is out by the time the leader tops out");
        assertEquals(RingAnimator.journeyFrames(0), RingAnimator.deployFrames(RingStyle.CONCURRENT),
            "concurrently the whole thing lasts one ring's journey");
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

    @Test
    public void aRingOvershootsItsPlaceByHalfABlockAndDropsBackOntoIt()
    {
        // The settle. A ring rises past where it belongs, hangs there a frame, and comes
        // back down onto it — which is what stops the stack arriving like a lift stopping.
        final int resting = RingAnimator.restingHalfStep(0);
        assertEquals(resting, RingAnimator.halfStepAt(0, resting), "reaches its place");
        assertEquals(resting + RingAnimator.OVERSHOOT, RingAnimator.halfStepAt(0, resting + 1),
            "and goes half a block past it");
        assertEquals(resting, RingAnimator.halfStepAt(0, resting + 2), "then drops back");
        assertEquals(resting, RingAnimator.halfStepAt(0, resting + 50), "and stays there");
    }

    @Test
    public void theOvershootIsTheOnlyThingHigherThanTheFinishedStack()
    {
        // What the extra headroom is for. Nothing but the peak of the settle ever goes
        // above where the top ring ends up.
        final Ring floor = ring(RingOrientation.FLOOR);
        final int settledTop = 64 + (RingAnimator.TOP_HALF_STEP / 2);
        int highest = settledTop;
        for (final RingStyle style : RingStyle.values())
        {
            for (int frame = 0; frame < RingAnimator.deployFrames(style); frame++)
            {
                for (final RingAnimator.Placement placement : RingAnimator.deployFrame(floor, style, frame))
                {
                    highest = Math.max(highest, placement.getY());
                }
            }
        }
        assertEquals(settledTop + 1, highest, "half a block of overshoot, and no more");
    }
}
