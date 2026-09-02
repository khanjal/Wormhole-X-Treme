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

        final List<RingAnimator.Placement> arriving =
            RingAnimator.deployFrame(floor, STYLE, RingAnimator.TOP_HALF_STEP);
        assertEquals(perRing, arriving.size(), "still only one ring in the air");
        assertEquals(64 + (RingAnimator.TOP_HALF_STEP / 2), arriving.get(0).getY());

        final List<RingAnimator.Placement> next =
            RingAnimator.deployFrame(floor, STYLE, RingAnimator.TOP_HALF_STEP + 1);
        assertEquals(perRing * 2, next.size(), "now the second one comes out");
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

        boolean stillAtThePlane = false;
        for (final RingAnimator.Placement placement : last)
        {
            if ((placement.getY() == 64) && !placement.isTop())
            {
                stillAtThePlane = true;
            }
        }
        assertTrue(stillAtThePlane, "the last ring out is already where it belongs");
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

        final List<RingAnimator.Placement> early = RingAnimator.retractFrame(floor, STYLE, 1);
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
        assertEquals(RingAnimator.TOP_HALF_STEP + 1, RingAnimator.deployFrames(RingStyle.CONCURRENT));
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
            final List<RingAnimator.Placement> early = RingAnimator.retractFrame(floor, style, 1);
            for (final RingAnimator.Placement placement : early)
            {
                assertFalse((placement.getY() == 64) && !placement.isTop(),
                    style + " kept the nearest ring past the first frame of retract");
            }
        }
    }
}
