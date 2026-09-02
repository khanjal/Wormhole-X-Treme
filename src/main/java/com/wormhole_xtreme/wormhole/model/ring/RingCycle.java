/*
 *   Wormhole X-Treme Plugin for Bukkit
 *
 *   One run of a ring pair, from arming to cooldown.
 */
package com.wormhole_xtreme.wormhole.model.ring;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * <p><b>What is safe to write.</b> A block is only replaced if it is currently air, and only
 * put back if it still holds what this cycle put there. The first rule means the animation
 * never eats somebody's build; the second means it never overwrites a block somebody changed
 * while the rings were up. Anything failing either test is left alone, which is always the
 * safe answer because a ring is decoration and the block might not be.
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
         * @param x
         *            block x
         * @param y
         *            block y
         * @param z
         *            block z
         * @return the material there, never null
         */
        Material materialAt(int x, int y, int z);

        /**
         * Puts a full block down.
         *
         * @param x
         *            block x
         * @param y
         *            block y
         * @param z
         *            block z
         * @param material
         *            what to place
         */
        void setBlock(int x, int y, int z, Material material);

        /**
         * Puts a slab down, filling one half of its block.
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
        void setSlab(int x, int y, int z, Material material, boolean top);

        /**
         * Everyone and everything standing in a volume.
         *
         * @param blocks
         *            the volume, each entry {@code {x, y, z}}
         * @return the passengers there
         */
        List<RingPassenger> passengersIn(List<int[]> blocks);

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

    /** What each block held before this cycle touched it, by packed position. */
    private final Map<Long, Material> restore = new LinkedHashMap<Long, Material>();

    /** What this cycle last put in each block, so a changed block is left alone. */
    private final Map<Long, Material> placed = new HashMap<Long, Material>();

    /** How far through the current phase we are. */
    private int frame = 0;

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
        restoreEverything();
        pair.setPhase(RingPhase.IDLE);
        frame = 0;
    }

    /**
     * Commits: takes the lights down and lets the rings start rising.
     */
    public void beginDeploy()
    {
        restoreEverything();
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
        if (frame >= RingAnimator.deployFrames(pair.getStyle()))
        {
            return false;
        }
        if (pair.getPhase() == RingPhase.RETRACT)
        {
            drawFrame(RingAnimator.retractFrame(pair.getEndA(), pair.getStyle(), frame),
                RingAnimator.retractFrame(pair.getEndB(), pair.getStyle(), frame));
        }
        else
        {
            drawDeployFrame(frame);
        }
        return true;
    }

    /**
     * Moves everyone, in the one instant where that can be done correctly.
     *
     * <p><b>Both ends are read before either is written.</b> Reading A, moving them, then
     * reading B would find A's arrivals standing in B and send them straight back, so the
     * two snapshots are taken together and only then acted on. This is the single most
     * important ordering in the subsystem.
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

        final List<RingPassenger> travellingFromA = permitted(fromA);
        final List<RingPassenger> travellingFromB = permitted(fromB);

        for (final RingPassenger passenger : travellingFromA)
        {
            world.deliver(passenger, pair.getEndB());
        }
        for (final RingPassenger passenger : travellingFromB)
        {
            world.deliver(passenger, pair.getEndA());
        }
        return travellingFromA.size() + travellingFromB.size();
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
        drawFrame(RingAnimator.retractFrame(pair.getEndA(), pair.getStyle(), 0),
            RingAnimator.retractFrame(pair.getEndB(), pair.getStyle(), 0));
    }

    /**
     * Puts every block back and starts the cooldown.
     *
     * @param cooldownUntil
     *            epoch millis before which this pair will refuse to fire
     */
    public void finish(final long cooldownUntil)
    {
        restoreEverything();
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
     * @return only what may travel
     */
    private List<RingPassenger> permitted(final List<RingPassenger> passengers)
    {
        final List<RingPassenger> out = new ArrayList<RingPassenger>(passengers.size());
        for (final RingPassenger passenger : passengers)
        {
            if (!passenger.isPlayer() || pair.mayUse(passenger.getUniqueId()))
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
        for (final int[] block : RingAnimator.lightBlocks(ring))
        {
            place(block[0], block[1], block[2], ring.getLightMaterial(), false, false);
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
        drawFrame(RingAnimator.deployFrame(pair.getEndA(), pair.getStyle(), index),
            RingAnimator.deployFrame(pair.getEndB(), pair.getStyle(), index));
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
        restoreEverything();
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
            place(placement.getX(), placement.getY(), placement.getZ(), material, true,
                placement.isTop());
        }
    }

    /**
     * Puts a block down if the space is free, remembering what was there.
     *
     * <p>Only air is written over. A ring is decoration and whatever is already in the way
     * might not be, so a blocked position is simply skipped — the frame is drawn with a gap
     * in it, which is a far better outcome than a hole in somebody's wall.
     *
     * @param x
     *            block x
     * @param y
     *            block y
     * @param z
     *            block z
     * @param material
     *            what to place
     * @param slab
     *            true to place it as a slab
     * @param top
     *            for a slab, true to fill the upper half
     */
    private void place(final int x, final int y, final int z, final Material material,
        final boolean slab, final boolean top)
    {
        final long key = RingIndex.pack(x, y, z);
        if (restore.containsKey(Long.valueOf(key)))
        {
            return;
        }
        final Material existing = world.materialAt(x, y, z);
        if (existing != Material.AIR)
        {
            return;
        }
        restore.put(Long.valueOf(key), existing);
        placed.put(Long.valueOf(key), material);
        if (slab)
        {
            world.setSlab(x, y, z, material, top);
        }
        else
        {
            world.setBlock(x, y, z, material);
        }
    }

    /**
     * Puts back everything this cycle has written.
     *
     * <p>A block is only restored if it still holds what this cycle put there. Somebody may
     * have built on a ring while it was up, and undoing that would be this feature
     * vandalising the world on their behalf. Either way the record is dropped, because the
     * block is no longer ours to think about.
     */
    private void restoreEverything()
    {
        for (final Map.Entry<Long, Material> entry : restore.entrySet())
        {
            final long key = entry.getKey().longValue();
            final int x = RingIndex.unpackX(key);
            final int y = RingIndex.unpackY(key);
            final int z = RingIndex.unpackZ(key);
            final Material ours = placed.get(entry.getKey());
            if ((ours != null) && (world.materialAt(x, y, z) != ours))
            {
                continue;
            }
            world.setBlock(x, y, z, entry.getValue());
        }
        restore.clear();
        placed.clear();
    }
}
