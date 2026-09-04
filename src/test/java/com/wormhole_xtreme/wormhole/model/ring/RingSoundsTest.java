package com.wormhole_xtreme.wormhole.model.ring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/**
 * What the rings sound like as they move.
 *
 * <p>The pitch is the whole point of playing one sound per ring rather than one per cycle, so
 * it is worth pinning: a flat repeat says a ring moved, a climb says the stack is rising.
 */
public class RingSoundsTest
{
    private static Ring ring(final RingOrientation orientation)
    {
        final Ring ring = new Ring(0, 64, 0, RingPattern.ODD, orientation,
            Material.STONE_SLAB, Material.GLOWSTONE);
        ring.setStyle(RingStyle.SEQUENTIAL);
        return ring;
    }

    @Test
    public void thePitchClimbsWithEachRingThatLeavesThePad()
    {
        float previous = -1f;
        for (int index = 0; index < RingAnimator.RING_COUNT; index++)
        {
            final float pitch = RingSounds.pitchFor(index);
            assertTrue(pitch > previous, "ring " + index + " should be higher than the last");
            previous = pitch;
        }
    }

    @Test
    public void goingHomeFallsWithoutBeingToldTo()
    {
        // Retracting replays the same pitches in the order the rings come back, which is the
        // reverse of the order they went out. The fall is free -- nothing has to know which
        // direction the stack is moving, which is one fewer thing to get backwards.
        final Ring floor = ring(RingOrientation.FLOOR);
        float previous = Float.MAX_VALUE;
        for (int frame = 0; frame < RingAnimator.deployFrames(floor, floor.getStyle()); frame++)
        {
            for (int index = 0; index < RingAnimator.RING_COUNT; index++)
            {
                if (RingSounds.startFrame(floor, index, true) == frame)
                {
                    final float pitch = RingSounds.pitchFor(index);
                    assertTrue(pitch < previous, "coming home should keep falling");
                    previous = pitch;
                }
            }
        }
    }

    @Test
    public void bothEndsOfAPairClimbWhicheverWayTheirRingsTravel()
    {
        // A ceiling ring's first ring travels downward and a floor ring's travels up, but
        // both are the first out -- and that, not where they end up, is what sets the pitch.
        // Pitching by height in the stack would have run the sound up at one end of a pair
        // and down at the other, which is the bug the transport flash had.
        //
        // Their frames are not the same, and do not need to be: a ceiling ring's rings have
        // further to fall, so its stack takes longer to build. Each end climbs through the
        // same notes at its own pace, which is what this walks the frames to check.
        for (final RingOrientation orientation : RingOrientation.values())
        {
            final Ring ring = ring(orientation);
            float previous = -1f;
            int heard = 0;
            for (int frame = 0; frame < RingAnimator.deployFrames(ring, ring.getStyle()); frame++)
            {
                for (int index = 0; index < RingAnimator.RING_COUNT; index++)
                {
                    if (RingSounds.startFrame(ring, index, false) == frame)
                    {
                        final float pitch = RingSounds.pitchFor(index);
                        assertTrue(pitch > previous, orientation + " should keep climbing");
                        previous = pitch;
                        heard++;
                    }
                }
            }
            assertEquals(RingAnimator.RING_COUNT, heard, orientation + " lost a ring");
        }
    }

    @Test
    public void theFirstRingOutLeavesOnFrameZero()
    {
        // Worth pinning on its own, because frame zero is drawn when the phase begins rather
        // than by the frame loop -- so anything that plays sounds only as it advances skips
        // exactly this ring, and the animation loses the note it starts on. That is the bug
        // this test exists for.
        for (final RingStyle style : RingStyle.values())
        {
            final Ring ring = ring(RingOrientation.FLOOR);
            ring.setStyle(style);
            assertEquals(0, RingSounds.startFrame(ring, 0, false), style + " starts late");
        }
    }

    @Test
    public void everyRingIsHeardExactlyOnceAcrossAWholeDeploy()
    {
        // Walks the frames the way a cycle does, counting sounds. Four rings, four noises --
        // whether they leave together or one at a time, and whichever way they travel.
        for (final RingStyle style : RingStyle.values())
        {
            for (final RingOrientation orientation : RingOrientation.values())
            {
                for (final boolean retracting : new boolean[] { false, true })
                {
                    final Ring ring = ring(orientation);
                    ring.setStyle(style);
                    int heard = 0;
                    for (int frame = 0;
                        frame < RingAnimator.deployFrames(ring, style); frame++)
                    {
                        for (int index = 0; index < RingAnimator.RING_COUNT; index++)
                        {
                            if (RingSounds.startFrame(ring, index, retracting) == frame)
                            {
                                heard++;
                            }
                        }
                    }
                    assertEquals(RingAnimator.RING_COUNT, heard,
                        style + " " + orientation + (retracting ? " retracting" : " deploying"));
                }
            }
        }
    }

    @Test
    public void everyRingGetsItsOwnFrameSoNoneOfThemAreSilent()
    {
        // Sequential rings leave one at a time, so a shared frame would mean two sounds at
        // once and one ring moving without a noise.
        for (final RingOrientation orientation : RingOrientation.values())
        {
            final Ring ring = ring(orientation);
            final Set<Integer> frames = new HashSet<Integer>();
            for (int index = 0; index < RingAnimator.RING_COUNT; index++)
            {
                assertTrue(frames.add(Integer.valueOf(RingSounds.startFrame(ring, index, false))),
                    orientation + " plays two rings on one frame");
            }
        }
    }

    @Test
    public void goingHomeIsTheDeployRunBackwards()
    {
        // The first ring out is the last one home, and its frames should mirror. Otherwise a
        // retract would play its sounds at times that have nothing to do with what is moving.
        final Ring floor = ring(RingOrientation.FLOOR);
        final int last = RingAnimator.deployFrames(floor, floor.getStyle()) - 1;
        for (int index = 0; index < RingAnimator.RING_COUNT; index++)
        {
            assertEquals(last - RingSounds.startFrame(floor, index, false),
                RingSounds.startFrame(floor, index, true),
                "ring " + index + " should come home mirrored");
        }
    }

    @Test
    public void everyRingSoundsWithinWhatMinecraftWillPlay()
    {
        // Bukkit clamps pitch to 0.5 - 2.0. A ring outside that would quietly play at the
        // limit, collapsing the climb this whole class exists for -- so this is the test that
        // fails first if the ring count ever goes up.
        for (int index = 0; index < RingAnimator.RING_COUNT; index++)
        {
            final float pitch = RingSounds.pitchFor(index);
            assertTrue((pitch >= 0.5f) && (pitch <= 2.0f),
                "ring " + index + " at pitch " + pitch + " is outside what will play");
        }
    }
}
