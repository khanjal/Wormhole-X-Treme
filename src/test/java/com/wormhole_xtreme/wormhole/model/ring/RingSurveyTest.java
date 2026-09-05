package com.wormhole_xtreme.wormhole.model.ring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/**
 * Whether a ring has room to do its job, which is not the same as room to stand in.
 *
 * <p>A survey that only asked for a traveller's two blocks let a ring in a low room count
 * down, fire, and teleport — with most of its four-block stack drawn inside the ceiling. It
 * looked broken and it was, but nothing threw and nothing logged: on a live server it showed
 * up only as rings that vanished halfway up and people arriving anyway. These pin the rule
 * that a room has to be as tall as the stack, not as tall as the person.
 *
 * <p>The whole survey moved out of {@code BukkitRingWorld} to make that testable, so the
 * older rules are pinned here too — this is the first time any of them has been checked
 * without a server.
 */
class RingSurveyTest
{
    /** How far a ceiling ring is allowed to look for its floor, as the shipped default does. */
    private static final int MAX_DROP = 10;

    /** The layer a test ring's perimeter sits in. */
    private static final int PLANE = 64;

    /** A room made of nothing but the blocks a test says are solid. */
    private static final class Room implements RingSurvey.Ground
    {
        private final Set<Long> solid = new HashSet<Long>();
        private int min = -64;
        private int max = 320;

        /** Fills every interior column of a ring at one layer, which is how floors and ceilings are made here. */
        Room layer(final Ring ring, final int y)
        {
            for (final int[] block : ring.interiorBlocks())
            {
                block(block[0], y, block[2]);
            }
            return this;
        }

        /** Fills one block, for the tests about a single column spoiling it for the rest. */
        Room block(final int x, final int y, final int z)
        {
            solid.add(Long.valueOf(RingIndex.pack(x, y, z)));
            return this;
        }

        /** Takes one block back out again, for digging a hole in a floor already laid. */
        Room clear(final int x, final int y, final int z)
        {
            solid.remove(Long.valueOf(RingIndex.pack(x, y, z)));
            return this;
        }

        Room bounds(final int minHeight, final int maxHeight)
        {
            this.min = minHeight;
            this.max = maxHeight;
            return this;
        }

        @Override
        public boolean isPassable(final int x, final int y, final int z)
        {
            return !solid.contains(Long.valueOf(RingIndex.pack(x, y, z)));
        }

        @Override
        public int minHeight()
        {
            return min;
        }

        @Override
        public int maxHeight()
        {
            return max;
        }
    }

    private static Ring ring(final RingOrientation orientation, final int y)
    {
        return new Ring(0, y, 0, RingPattern.ODD, orientation, Material.STONE_SLAB,
            Material.REDSTONE_LAMP);
    }

    private static Ring floorRing()
    {
        return ring(RingOrientation.FLOOR, PLANE);
    }

    private static RingBlockage survey(final Room room, final Ring ring)
    {
        return RingSurvey.survey(room, ring, MAX_DROP);
    }

    /**
     * The room the animator was designed for: four blocks, and the stack just fits.
     *
     * <p>{@code RingAnimator}'s own note says four rings were chosen over five because four
     * "still fits a four-block room". This is that room, and a survey that refused it would
     * have made the choice pointless.
     */
    @Test
    void aFloorRingWithTheStacksOwnHeightAboveItCanReceivePeople()
    {
        final Ring ring = floorRing();
        final Room room = new Room().layer(ring, PLANE - 1).layer(ring, PLANE + Ring.STACK_HEIGHT);
        assertNull(survey(room, ring), "four clear blocks is exactly what a stack needs");
    }

    /**
     * One block short is still short.
     *
     * <p>The bug this whole change is about. A three-block room has room for a person twice
     * over, so the old two-layer check passed it and the rings fired — and the top of the
     * stack was drawn inside somebody's ceiling.
     */
    @Test
    void aFloorRingOneBlockShortOfTheStacksHeightWillNotFire()
    {
        final Ring ring = floorRing();
        final Room room = new Room().layer(ring, PLANE - 1)
            .layer(ring, PLANE + Ring.STACK_HEIGHT - 1);
        assertEquals(RingBlockage.NO_HEADROOM, survey(room, ring),
            "a room a person fits in twice over is still not a room the rings fit in");
    }

    /**
     * A crawlspace has exactly enough room for the traveller and none for the rings.
     *
     * <p>Two blocks is a player's own height, which is the number the old check used. It is
     * the clearest case of the two questions giving different answers.
     */
    @Test
    void aFloorRingInATwoBlockCrawlspaceWillNotFire()
    {
        final Ring ring = floorRing();
        final Room room = new Room().layer(ring, PLANE - 1).layer(ring, PLANE + 2);
        assertEquals(RingBlockage.NO_HEADROOM, survey(room, ring),
            "somewhere to stand is not somewhere the rings can come up");
    }

    /**
     * A low ceiling over any one column is a low ceiling.
     *
     * <p>Judged over the whole interior, exactly as obstruction always has been. A ring that
     * fired because most of it was clear would put the stack through whatever the rest of it
     * was under.
     */
    @Test
    void aLowCeilingOverASingleColumnStopsTheWholeRing()
    {
        final Ring ring = floorRing();
        final Room room = new Room().layer(ring, PLANE - 1);
        final int[] corner = ring.interiorBlocks().get(0);
        room.block(corner[0], PLANE + 2, corner[2]);
        assertEquals(RingBlockage.NO_HEADROOM, survey(room, ring),
            "one column under a low ceiling is enough to spoil the whole stack");
    }

    /**
     * Something in the traveller's own two blocks is a different complaint.
     *
     * <p>Worth telling apart because the two send a player to different places: one is
     * somebody's chest to move, the other is a ceiling to dig out. Reporting a low ceiling
     * for a block dropped at head height would send them looking for the wrong thing.
     */
    @Test
    void somethingBuiltWhereAPersonWouldStandIsAnObstructionNotALowCeiling()
    {
        final Ring ring = floorRing();
        final Room room = new Room().layer(ring, PLANE - 1);
        final int[] corner = ring.interiorBlocks().get(0);
        room.block(corner[0], PLANE + 1, corner[2]);
        assertEquals(RingBlockage.OBSTRUCTED, survey(room, ring),
            "a block at head height is something built in the ring, not a low ceiling");
    }

    /**
     * A hole under one column is nowhere to arrive.
     *
     * <p>Directly under, not somewhere below: a floor three blocks further down is still a
     * gap to fall out of.
     */
    @Test
    void aHoleUnderOneColumnMeansThereIsNoGround()
    {
        final Ring ring = floorRing();
        final Room room = new Room().layer(ring, PLANE - 1).layer(ring, PLANE + Ring.STACK_HEIGHT);
        final int[] corner = ring.interiorBlocks().get(0);
        room.clear(corner[0], PLANE - 1, corner[2]);
        assertEquals(RingBlockage.NO_GROUND, survey(room, ring),
            "one missing floor block is a hole to fall through");
    }

    /**
     * A stack that would reach past the top of the world has nowhere to form.
     *
     * <p>The world's own ceiling counts the same as one somebody built, and for the same
     * reason — there is no layer up there to draw a ring in.
     */
    @Test
    void aFloorRingTooCloseToTheTopOfTheWorldWillNotFire()
    {
        final Ring ring = floorRing();
        final Room room = new Room().bounds(-64, PLANE + Ring.STACK_HEIGHT - 1)
            .layer(ring, PLANE - 1);
        assertEquals(RingBlockage.NO_HEADROOM, survey(room, ring),
            "the top of the world is as hard a ceiling as any");
    }

    /**
     * A ring sitting on the bottom of the world has nothing under it.
     *
     * <p>The world's floor is not a block, so there is nothing there to stand on and nothing
     * to read. Asked before any column is walked, because it is a fact about the layer rather
     * than about any one square.
     */
    @Test
    void aFloorRingOnTheBottomOfTheWorldHasNoGroundUnderIt()
    {
        final Ring ring = floorRing();
        final Room room = new Room().bounds(PLANE, 320);
        assertEquals(RingBlockage.NO_GROUND, survey(room, ring),
            "the layer below the world's own floor is not somewhere to arrive");
    }

    /**
     * A ceiling ring over the void stops looking at the bottom of the world.
     *
     * <p>Without this the search would walk out past the world floor asking for blocks that
     * cannot exist. It comes out as the same refusal as a ring over a shaft, which is the
     * right answer: either way there is no floor for the rings to fall to.
     */
    @Test
    void aCeilingRingOverTheVoidStopsAtTheBottomOfTheWorld()
    {
        final Ring ring = ring(RingOrientation.CEILING, PLANE);
        final Room room = new Room().bounds(PLANE - 2, 320);
        assertEquals(RingBlockage.CEILING_TOO_HIGH, survey(room, ring),
            "the search has to end at the world floor rather than run past it");
    }

    /**
     * A ceiling ring set flush into a four-block room still works.
     *
     * <p>Its top ring settles level with the plane the ring was cut into — that is what
     * {@code MIN_CEILING_DROP} being derived from the stack height means. Somebody laying a
     * ring into their ceiling leaves the middle of it as ceiling, so demanding that layer be
     * clear would have refused the ordinary way of building one while claiming to be a
     * headroom check.
     */
    @Test
    void aCeilingRingFlushInItsOwnCeilingStillFires()
    {
        final Ring ring = ring(RingOrientation.CEILING, PLANE);
        final Room room = new Room().layer(ring, PLANE - Ring.STACK_HEIGHT).layer(ring, PLANE);
        assertNull(survey(room, ring),
            "the middle of a ceiling ring is ceiling, and that is not an obstruction");
    }

    /** A ceiling ring measures its drop and builds its stack up from the floor it found. */
    @Test
    void aCeilingRingRecordsHowFarBelowItsPlaneTheFloorIs()
    {
        final Ring ring = ring(RingOrientation.CEILING, PLANE);
        final Room room = new Room().layer(ring, PLANE - 6);
        assertNull(survey(room, ring), "a tall room is a fine place for a ceiling ring");
        assertEquals(5, ring.getDrop(),
            "the stack forms on the floor, so the drop is measured to the layer above it");
    }

    /** Too close to its own floor, and the stack has nowhere to form. */
    @Test
    void aCeilingRingTooCloseToItsFloorWillNotFire()
    {
        final Ring ring = ring(RingOrientation.CEILING, PLANE);
        final Room room = new Room().layer(ring, PLANE - Ring.MIN_CEILING_DROP);
        assertEquals(RingBlockage.CEILING_TOO_LOW, survey(room, ring),
            "a ceiling ring needs the stack's height between it and the floor");
    }

    /** Over a shaft rather than a room: the rings would fall out of sight. */
    @Test
    void aCeilingRingWithNoFloorWithinReachWillNotFire()
    {
        final Ring ring = ring(RingOrientation.CEILING, PLANE);
        assertEquals(RingBlockage.CEILING_TOO_HIGH, survey(new Room(), ring),
            "rings that fall further than the configured reach are not a transport");
    }

    /** Where a ceiling ring's travellers land is its floor, and that has to be clear too. */
    @Test
    void aCeilingRingWithSomethingBuiltOnItsFloorWillNotFire()
    {
        final Ring ring = ring(RingOrientation.CEILING, PLANE);
        final Room room = new Room().layer(ring, PLANE - 6);
        final int[] corner = ring.interiorBlocks().get(0);
        room.block(corner[0], PLANE - 5, corner[2]);
        assertEquals(RingBlockage.OBSTRUCTED, survey(room, ring),
            "arrivals stand on a ceiling ring's floor, so a chest left on it blocks the ring");
    }
}
