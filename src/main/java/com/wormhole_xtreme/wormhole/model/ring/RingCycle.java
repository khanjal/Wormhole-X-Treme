/*
 *   Wormhole X-Treme Plugin for Bukkit
 *
 *   One run of a ring pair, from arming to cooldown.
 */
package com.wormhole_xtreme.wormhole.model.ring;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Material;

/**
 * One run of a ring pair, from the moment somebody arms it to the moment it goes quiet.
 *
 * <p>Both ends run as one cycle rather than two, which is the whole point: they light, rise,
 * swap and retract together, so that people standing at either end travel in the same
 * instant and neither end is the origin.
 *
 * <p>The world is reached only through {@link Surroundings}. Everything about ordering,
 * committing, restoring and who travels can then be checked without a server, which matters
 * because the failures here — a block never put back, a passenger swapped into the set that
 * has not moved yet — are quiet ones that a live server would show only as damage after the
 * fact.
 *
 * <p><b>Nothing here changes the world.</b> The lights and the travelling rings are drawn to
 * the clients who can see them and never written to the server's blocks, the same way a gate
 * draws its portal. A ring is scenery that exists for five seconds, and making it real would
 * mean a server stopped mid-cycle keeping it for good, block loggers recording a floor being
 * replaced on every trip, and anyone able to mine the glowstone out of their own floor for
 * free while it stood there.
 *
 * <p>That also makes putting things back trivial rather than delicate: since the real blocks
 * were never touched, undoing a drawing is just showing the client what was always there.
 * There is nothing to remember and nothing to get wrong — no saved materials to restore in
 * the right order, and no need to check whether somebody changed a block underneath us.
 */
public class RingCycle
{
    /**
     * Everything the cycle needs from the world.
     *
     * <p>Deliberately small. Blocks in, blocks out, and the passengers standing in a volume —
     * anything more would be logic that had escaped into the part that cannot be tested.
     */
    public interface Surroundings
    {
        /**
         * Shows a block where there is not one, to whoever can see this ring.
         *
         * <p>Shows, not places. The server's own block is untouched.
         *
         * @param x
         *            block x
         * @param y
         *            block y
         * @param z
         *            block z
         * @param material
         *            what to show
         */
        void showBlock(int x, int y, int z, Material material);

        /**
         * Shows a slab filling one half of its block.
         *
         * @param x
         *            block x
         * @param y
         *            block y
         * @param z
         *            block z
         * @param material
         *            the slab material
         * @param top
         *            true to fill the upper half
         */
        void showSlab(int x, int y, int z, Material material, boolean top);

        /**
         * Shows what is really there, undoing a drawing.
         *
         * @param x
         *            block x
         * @param y
         *            block y
         * @param z
         *            block z
         */
        void reveal(int x, int y, int z);

        /**
         * Everyone and everything standing in a volume.
         *
         * @param blocks
         *            the volume, each entry {@code {x, y, z}}
         * @return the passengers there
         */
        List<RingPassenger> passengersIn(List<int[]> blocks);

        /**
         * Whether anything outside this plugin objects to carrying this passenger.
         *
         * <p>Asked through the seam rather than fired from here, so the cycle stays free of
         * Bukkit and the rule that a refused passenger is dropped from the trip — rather than
         * cancelling it — can still be tested.
         *
         * @param passenger
         *            who is about to travel
         * @param from
         *            the end they are standing in
         * @param to
         *            the end they would arrive at
         * @return false to leave them behind
         */
        boolean mayTravel(RingPassenger passenger, Ring from, Ring to);

        /**
         * Measures a ring against the world, and reports anything wrong with it.
         *
         * <p>Does two things at once, and says so rather than pretending to be a pure
         * question. It finds how far a ceiling ring's floor is and records it on the ring —
         * which is what lets the stack form on the ground rather than under the ceiling —
         * and it reports whether the ring is fit to receive anybody at all.
         *
         * <p>Both need the same walk through the same blocks, and the answer to the second
         * depends on the first, so splitting them would mean reading the world twice to
         * learn one thing.
         *
         * @param ring
         *            the ring to measure
         * @return why it cannot take anybody, or null if it can
         */
        RingBlockage survey(Ring ring);

        /**
         * Moves a passenger to a ring.
         *
         * @param passenger
         *            who is travelling
         * @param destination
         *            the ring they arrive at
         */
        void deliver(RingPassenger passenger, Ring destination);
    }

    /** The pair being run. */
    private final RingPair pair;

    /** How the cycle reaches the world. */
    private final Surroundings world;

    /** How deep each trigger volume runs. */
    private final int reach;

    /**
     * The travelling rings, redrawn every frame.
     *
     * <p>Kept apart from the lights because the two have completely different lifetimes: this
     * is replaced wholesale each frame, and the lights outlast all of it.
     */
    private final Set<Long> drawnRings = new LinkedHashSet<Long>();

    /**
     * The pattern lit in the floor, which stays put for the whole cycle.
     *
     * <p>The pad stays open and lit from the countdown until after the last ring has sunk
     * back into it. Putting it out the moment the rings start rising would have the pad close
     * exactly as it does the thing it opened for.
     */
    private final Set<Long> drawnLights = new LinkedHashSet<Long>();

    /** How far through the current phase we are. */
    private int frame = 0;

    /** How many travelled when this cycle flashed. */
    private int carried = 0;

    /** Whether each end had somebody to send when the light started. */
    private boolean departingA = false;

    /** Whether each end had somebody to send when the light started. */
    private boolean departingB = false;

    /** Whether each end received anybody. */
    private boolean arrivingA = false;

    /** Whether each end received anybody. */
    private boolean arrivingB = false;

    /**
     * Instantiates a cycle.
     *
     * @param pair
     *            the pair to run
     * @param world
     *            how to reach the world
     * @param reach
     *            how deep each trigger volume runs
     */
    public RingCycle(final RingPair pair, final Surroundings world, final int reach)
    {
        this.pair = pair;
        this.world = world;
        this.reach = reach;
    }

    /** @return the pair being run */
    public RingPair getPair()
    {
        return pair;
    }

    /**
     * How many this cycle actually carried.
     *
     * <p>Read when it ends, to decide whether a cooldown is owed. A cycle that moved nobody
     * has no arrival that could re-fire the rings, which is the only thing the cooldown is
     * there to prevent.
     *
     * @return the number carried, or zero before the flash
     */
    public int getCarried()
    {
        return carried;
    }

    /** @return how far through the current phase the cycle is */
    public int getFrame()
    {
        return frame;
    }

    /**
     * Lights both ends and starts the clock.
     */
    public void beginCountdown()
    {
        pair.setPhase(RingPhase.COUNTDOWN);
        frame = 0;
        showLights(pair.getEndA());
        showLights(pair.getEndB());
    }

    /**
     * Everyone standing in either end right now.
     *
     * <p>Both ends together, because the countdown is one event and the people at each end
     * are equally in it.
     *
     * @return the passengers at both ends
     */
    public List<RingPassenger> everyoneInside()
    {
        final List<RingPassenger> out = new ArrayList<RingPassenger>();
        out.addAll(occupants(pair.getEndA()));
        out.addAll(occupants(pair.getEndB()));
        return out;
    }

    /**
     * Whether the countdown should be called off.
     *
     * <p>Only while counting down. Once the rings start rising the cycle is committed and
     * runs to the end however empty it gets, which is what keeps the reversible phase
     * trivially reversible and the complicated phase uninterruptible.
     *
     * @return true if both interiors are empty and the cycle can still be stopped
     */
    public boolean shouldAbort()
    {
        if (pair.getPhase() != RingPhase.COUNTDOWN)
        {
            return false;
        }
        return occupants(pair.getEndA()).isEmpty() && occupants(pair.getEndB()).isEmpty();
    }

    /**
     * Calls the cycle off and puts everything back.
     */
    public void abort()
    {
        clearDrawing();
        pair.setPhase(RingPhase.IDLE);
        frame = 0;
    }

    /**
     * Commits: the rings start rising, and the pad stays lit under them.
     */
    public void beginDeploy()
    {
        clearRings();
        pair.setPhase(RingPhase.DEPLOY);
        frame = 0;
        drawDeployFrame(0);
    }

    /**
     * Draws the next frame of whichever travelling phase is running.
     *
     * @return true if the phase has more frames to draw
     */
    public boolean advanceFrame()
    {
        frame++;
        if (frame >= longestPhase())
        {
            return false;
        }
        if (pair.getPhase() == RingPhase.RETRACT)
        {
            drawFrame(RingAnimator.retractFrame(pair.getEndA(), pair.getEndA().getStyle(), frame),
                RingAnimator.retractFrame(pair.getEndB(), pair.getEndB().getStyle(), frame));
        }
        else
        {
            drawDeployFrame(frame);
        }
        return true;
    }

    /**
     * How long the travelling phase runs for.
     *
     * <p>The longer of the two ends. Each end deploys at its own pace, so a concurrent stack
     * finishes before a sequential one and simply stands there until the other is up: the
     * swap needs both, and waiting for the slower is the only way to have it. A ring that has
     * arrived holds its place anyway, so the wait costs nothing to draw.
     *
     * @return the number of frames
     */
    private int longestPhase()
    {
        return Math.max(
            RingAnimator.deployFrames(pair.getEndA(), pair.getEndA().getStyle()),
            RingAnimator.deployFrames(pair.getEndB(), pair.getEndB().getStyle()));
    }

    /**
     * Moves everyone, in the one instant where that can be done correctly.
     *
     * <p><b>Both ends are read before either is written.</b> Reading A, moving them, then
     * reading B would find A's arrivals standing in B and send them straight back, so the
     * two snapshots are taken together and only then acted on. This is the single most
     * important ordering in the subsystem.
     *
     * <p>Outside plugins are asked here too, in the same gap: after both ends have been
     * read and before either has been written, so a listener always sees the whole trip as it
     * was rather than a half-finished one. A refusal drops that passenger and leaves the rest
     * of the trip alone.
     *
     * <p>An end with nowhere to stand takes nobody. That is checked before the cycle starts
     * and again here, because the few seconds a cycle runs are long enough for somebody to
     * fill the far end in — and having watched the rings come up is no reason to be put
     * inside a wall.
     *
     * <p>Players who may not use this pair are dropped between the snapshot and the move.
     * A private ring is not a free ride for whoever happens to be standing in it: they stay
     * where they are while the rings close and open around them. Anything that is not a
     * player travels as cargo, because nothing but a player move can arm a ring in the first
     * place, so a private pair only ever fires because somebody permitted made it.
     *
     * @return how many passengers travelled
     */
    public int flash()
    {
        pair.setPhase(RingPhase.FLASH);

        final List<RingPassenger> fromA = occupants(pair.getEndA());
        final List<RingPassenger> fromB = occupants(pair.getEndB());

        // Asked once per end rather than per traveller: whether a ring can take anybody is
        // a fact about the ring, and asking it twenty times would give the same answer.
        final List<RingPassenger> travellingFromA = (world.survey(pair.getEndB()) == null)
            ? permitted(fromA, pair.getEndA(), pair.getEndB())
            : new ArrayList<RingPassenger>();
        final List<RingPassenger> travellingFromB = (world.survey(pair.getEndA()) == null)
            ? permitted(fromB, pair.getEndB(), pair.getEndA())
            : new ArrayList<RingPassenger>();

        for (final RingPassenger passenger : travellingFromA)
        {
            world.deliver(passenger, pair.getEndB());
        }
        for (final RingPassenger passenger : travellingFromB)
        {
            world.deliver(passenger, pair.getEndA());
        }
        // Whoever leaves A lands at B, so that is the end which will emit them.
        arrivingB = !travellingFromA.isEmpty();
        arrivingA = !travellingFromB.isEmpty();
        carried = travellingFromA.size() + travellingFromB.size();
        return carried;
    }

    /**
     * Draws one frame of the transport flash.
     *
     * <p>The stack is up and still; the light runs through it one ring at a time. The lit
     * ring is drawn over the top of the stack rather than instead of it, so the rings that
     * are not lit stay exactly where they were and nothing appears to move.
     *
     * <p>Each sweep plays only at the ends it belongs to. The first takes travellers in and
     * runs where somebody is standing; the second puts them out and runs where somebody has
     * landed. With people at both ends both sweeps play at both, which is right — every end
     * is doing both jobs at once.
     *
     * @param direction
     *            which way the light runs
     * @param step
     *            which frame, from zero
     * @param arriving
     *            true for the sweep after the swap, which plays where people landed
     */
    public void drawFlash(final RingFlashDirection direction, final int step,
        final boolean arriving)
    {
        drawSettled();
        final int fromTop = RingAnimator.litRing(direction, step);
        if (arriving ? arrivingA : departingA)
        {
            lightOneRing(pair.getEndA(), fromTop);
        }
        if (arriving ? arrivingB : departingB)
        {
            lightOneRing(pair.getEndB(), fromTop);
        }
    }

    /**
     * Lights the ring at one height of one stack.
     *
     * <p>By height rather than by ring number, because the two orientations number their
     * rings from opposite ends — lighting number n at both would run the light down one stack
     * and up the other.
     *
     * @param ring
     *            the end to light
     * @param fromTop
     *            how far down its stack, counting the top as zero
     */
    private void lightOneRing(final Ring ring, final int fromTop)
    {
        drawPlacements(RingAnimator.ringAtRest(ring, RingAnimator.ringFromTop(ring, fromTop)),
            ring.getFlashMaterial());
    }

    /**
     * Notes which ends have somebody to send, before the light starts.
     *
     * <p>The two sweeps mean different things and belong to different ends. The first takes
     * travellers in, so it plays where somebody is standing; the second puts them out, so it
     * plays where somebody has landed. An end that is only receiving should not appear to
     * swallow anybody first, and an end that is only sending should not flash again once it
     * is empty.
     *
     * <p>Noted before the swap because afterwards there is nobody left at the near end to
     * count, and the light that takes them has to know they were there.
     */
    public void markDeparture()
    {
        departingA = !occupants(pair.getEndA()).isEmpty();
        departingB = !occupants(pair.getEndB()).isEmpty();
    }

    /**
     * Draws the stack plainly, with no ring lit.
     *
     * <p>What the rings look like either side of the flash.
     */
    public void drawSettled()
    {
        clearRings();
        drawPlacements(RingAnimator.settledStack(pair.getEndA(), pair.getEndA().getStyle()),
            pair.getEndA().getRingMaterial());
        drawPlacements(RingAnimator.settledStack(pair.getEndB(), pair.getEndB().getStyle()),
            pair.getEndB().getRingMaterial());
    }

    /**
     * Holds the stack still for a beat after the swap.
     */
    public void beginHold()
    {
        pair.setPhase(RingPhase.HOLD);
    }

    /**
     * Starts bringing the rings home.
     */
    public void beginRetract()
    {
        pair.setPhase(RingPhase.RETRACT);
        frame = 0;
        drawFrame(RingAnimator.retractFrame(pair.getEndA(), pair.getEndA().getStyle(), 0),
            RingAnimator.retractFrame(pair.getEndB(), pair.getEndB().getStyle(), 0));
    }

    /**
     * Puts every block back and starts the cooldown.
     *
     * @param cooldownUntil
     *            epoch millis before which this pair will refuse to fire
     */
    public void finish(final long cooldownUntil)
    {
        clearDrawing();
        pair.setCooldownUntil(cooldownUntil);
        pair.setPhase(RingPhase.IDLE);
        frame = 0;
    }

    /**
     * Everything currently standing in one end's trigger volume.
     *
     * @param ring
     *            the end to look at
     * @return the passengers there
     */
    private List<RingPassenger> occupants(final Ring ring)
    {
        return world.passengersIn(ring.triggerVolumeBlocks(reach));
    }

    /**
     * Drops the players who are not allowed to use this pair.
     *
     * @param passengers
     *            everything found in a volume
     * @param from
     *            the end they are standing in
     * @param to
     *            the end they would arrive at
     * @return only what may travel
     */
    private List<RingPassenger> permitted(final List<RingPassenger> passengers, final Ring from,
        final Ring to)
    {
        final List<RingPassenger> allowed = new ArrayList<RingPassenger>(passengers.size());
        for (final RingPassenger passenger : passengers)
        {
            if (passenger.isPlayer() && !pair.mayUse(passenger.getUniqueId()))
            {
                continue;
            }
            if (!world.mayTravel(passenger, from, to))
            {
                continue;
            }
            allowed.add(passenger);
        }
        return riddenBySomethingElse(allowed);
    }

    /**
     * Drops anyone whose ride is going too, so they arrive on it rather than beside it.
     *
     * <p>A rider and its mount are two separate things standing in the same ring, and moving
     * them one at a time is what tips somebody off their camel: whichever goes first leaves
     * the other behind for an instant, and the game breaks the seat rather than stretching
     * it. Sending only the mount, and re-seating whoever was aboard once it lands, keeps them
     * together.
     *
     * <p>Somebody riding a thing that is <em>not</em> travelling still travels on their own
     * and dismounts, which is the right answer: their ride is staying here.
     *
     * @param travelling
     *            everyone who has passed every other check
     * @return the ones to move directly
     */
    private static List<RingPassenger> riddenBySomethingElse(final List<RingPassenger> travelling)
    {
        final java.util.Set<String> going = new java.util.HashSet<String>();
        for (final RingPassenger passenger : travelling)
        {
            // Nulls are left out deliberately. Two things that cannot say what they are would
            // otherwise match each other — "riding nothing" would look like "riding the other
            // one that is also riding nothing" — and both would be dropped as somebody's
            // passenger with nothing to carry them.
            if (passenger.getUniqueId() != null)
            {
                going.add(passenger.getUniqueId());
            }
        }
        final List<RingPassenger> out = new ArrayList<RingPassenger>(travelling.size());
        for (final RingPassenger passenger : travelling)
        {
            final String ride = passenger.getVehicleId();
            if ((ride == null) || !going.contains(ride))
            {
                out.add(passenger);
            }
        }
        return out;
    }

    /**
     * Shows one end's countdown lights.
     *
     * @param ring
     *            the end to light
     */
    private void showLights(final Ring ring)
    {
        // The surface parts first, then the light shows through the gap it leaves. Drawn as
        // one thing because they are one thing: a ring opening.
        for (final int[] block : RingAnimator.openedBlocks(ring))
        {
            draw(drawnLights, block[0], block[1], block[2],
                RingAnimator.OPENED_MATERIAL, false, false);
        }
        for (final int[] block : RingAnimator.lightBlocks(ring))
        {
            draw(drawnLights, block[0], block[1], block[2], ring.getLightMaterial(), false, false);
        }
    }

    /**
     * Draws one deploy frame at both ends.
     *
     * @param index
     *            which frame
     */
    private void drawDeployFrame(final int index)
    {
        drawFrame(RingAnimator.deployFrame(pair.getEndA(), pair.getEndA().getStyle(), index),
            RingAnimator.deployFrame(pair.getEndB(), pair.getEndB().getStyle(), index));
    }

    /**
     * Replaces what is drawn with the given frame at both ends.
     *
     * <p>Everything from the previous frame comes out before anything of the new one goes
     * in. Doing it the other way round would let a block that appears in both frames be
     * written and then immediately erased by the clean-up of the frame it came from.
     *
     * @param atA
     *            what should exist at end A
     * @param atB
     *            what should exist at end B
     */
    private void drawFrame(final List<RingAnimator.Placement> atA, final List<RingAnimator.Placement> atB)
    {
        clearRings();
        drawPlacements(atA, pair.getEndA().getRingMaterial());
        drawPlacements(atB, pair.getEndB().getRingMaterial());
    }

    /**
     * Places one end's slabs for a frame.
     *
     * @param placements
     *            where the slabs go
     * @param material
     *            what they are made of
     */
    private void drawPlacements(final List<RingAnimator.Placement> placements, final Material material)
    {
        for (final RingAnimator.Placement placement : placements)
        {
            draw(drawnRings, placement.getX(), placement.getY(), placement.getZ(), material,
                true, placement.isTop());
        }
    }

    /**
     * Shows a block to whoever can see this ring.
     *
     * <p>Drawn over whatever is really there, because nothing here is real: a light set into
     * a floor has a floor block in its way by definition, and a ring passing through
     * somebody's staircase should still look like a complete ring. Since the world is not
     * being changed, covering a block costs nothing and the ring is never drawn with holes in
     * it where the ground happened to be in the way.
     *
     * @param into
     *            which drawing this block belongs to, since the rings and the lights are
     *            taken down at different times
     * @param x
     *            block x
     * @param y
     *            block y
     * @param z
     *            block z
     * @param material
     *            what to show
     * @param slab
     *            true to show it as a slab
     * @param top
     *            for a slab, true to fill the upper half
     */
    private void draw(final Set<Long> into, final int x, final int y, final int z,
        final Material material, final boolean slab, final boolean top)
    {
        into.add(Long.valueOf(RingIndex.pack(x, y, z)));
        if (slab)
        {
            world.showSlab(x, y, z, material, top);
        }
        else
        {
            world.showBlock(x, y, z, material);
        }
    }

    /**
     * Clears everything this cycle is drawing.
     *
     * <p>Just showing each client the block that was always there. Because the world was
     * never modified there is nothing to restore in order, nothing to remember, and no way
     * for this to damage anything — even if a player changed one of these blocks while the
     * rings were up, what gets shown is simply whatever is now real.
     */
    private void clearDrawing()
    {
        clearRings();
        clearLights();
    }

    /**
     * Takes down the travelling rings, leaving the pad lit.
     *
     * <p>What happens when the last ring sinks back into the floor. The lights stay a moment
     * longer, because a pad going dark at the same instant reads as the whole thing being
     * switched off rather than as the rings finishing.
     */
    public void clearRings()
    {
        clear(drawnRings);
    }

    /**
     * Puts the pad's lights out.
     */
    public void clearLights()
    {
        clear(drawnLights);
    }

    /**
     * Shows the real block again everywhere in one drawing.
     *
     * @param positions
     *            what to stop drawing over
     */
    private void clear(final Set<Long> positions)
    {
        for (final Long key : positions)
        {
            final long packed = key.longValue();
            world.reveal(RingIndex.unpackX(packed), RingIndex.unpackY(packed),
                RingIndex.unpackZ(packed));
        }
        positions.clear();
    }
}
