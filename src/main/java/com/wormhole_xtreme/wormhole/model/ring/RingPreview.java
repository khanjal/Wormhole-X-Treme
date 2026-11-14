package com.wormhole_xtreme.wormhole.model.ring;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;

/**
 * One end of a built ring, drawn for one player and held still between steps (#332).
 *
 * <p>A real cycle runs from the countdown to the cooldown without stopping. This is the same
 * drawing with the stops put in: the pad lights, the stack rises and <em>stays</em>, the light
 * runs through it as often as asked, and the rings come home when told. Every frame is the
 * one the real cycle draws, taken from {@link RingAnimator}, so a preview shows what a trip
 * will look like rather than a copy of it that could drift.
 *
 * <p>The states are {@link State#LIT} and {@link State#DEPLOYED} and the three in between
 * that are moving. A step asked for while something is moving, or in a state it makes no sense
 * in, is refused with a {@link Result} and changes nothing: asking for the light with no stack
 * up should say so, not draw something odd.
 *
 * <p>Nothing here reaches the world except through {@link RingCycle.Surroundings}, which for a
 * preview is {@link PreviewSurroundings}: one viewer, no passengers, no travel. One scheduler
 * task is pending at a time, and {@link #clear()} cancels it before it takes the drawing back,
 * so a stack is never redrawn after it was taken away.
 */
public final class RingPreview
{
    /** Where a preview is in its steps. */
    public enum State
    {
        /** The pad is lit and nothing else is drawn. */
        LIT,
        /** The rings are rising. */
        DEPLOYING,
        /** The stack stands, still. */
        DEPLOYED,
        /** The light is running through the standing stack. */
        LIGHTING,
        /** The rings are coming home. */
        COLLAPSING
    }

    /** What happened when a step was asked for. */
    public enum Result
    {
        /** The step started. */
        DONE,
        /** The pad was already lit for this player. */
        ALREADY_SHOWING,
        /** A stack is already standing. */
        ALREADY_STANDING,
        /** There is no stack to run the light through or bring home. */
        NO_STACK,
        /** The previous step is still moving. */
        BUSY,
        /** A ceiling ring has no floor to drop its stack to. */
        NO_FLOOR,
        /** The pair is running a real cycle. */
        MID_CYCLE
    }

    /** Marks that no task is waiting. */
    private static final int NO_TASK = -1;

    /** Who sees it, and hears it. */
    private final Player viewer;

    /** The pair the end belongs to, which a real cycle on it must clear this for. */
    private final RingPair pair;

    /** The end being drawn. */
    private final Ring ring;

    /** How it is drawn. */
    private final RingCycle.Surroundings world;

    /** The travelling rings, replaced wholesale each frame. */
    private final Set<Long> drawnRings = new LinkedHashSet<>();

    /** The lit pad, which stands from the first step until the preview is cleared. */
    private final Set<Long> drawnLights = new LinkedHashSet<>();

    /** Where the preview is. */
    private State state = State.LIT;

    /** The style the stack was raised in, so a change made while it stands waits for the next one. */
    private RingStyle style;

    /** How far through a deploy or retract the preview is. */
    private int frame = 0;

    /** The pending step, or {@link #NO_TASK}. */
    private int task = NO_TASK;

    /** Why a stack could not be raised, after {@link Result#NO_FLOOR}. */
    private RingBlockage blockage;

    /** True once cleared, after which nothing more is drawn. */
    private boolean cleared = false;

    /**
     * Instantiates a preview of one end.
     *
     * @param viewer
     *            who is shown it
     * @param pair
     *            the pair the end belongs to
     * @param ring
     *            the end to draw
     * @param world
     *            how to draw it
     */
    public RingPreview(final Player viewer, final RingPair pair, final Ring ring,
        final RingCycle.Surroundings world)
    {
        this.viewer = viewer;
        this.pair = pair;
        this.ring = ring;
        this.world = world;
        this.style = ring.getStyle();
    }

    /** @return who is shown this preview */
    public Player getViewer()
    {
        return viewer;
    }

    /** @return the pair the end belongs to */
    public RingPair getPair()
    {
        return pair;
    }

    /** @return the end being drawn */
    public Ring getRing()
    {
        return ring;
    }

    /** @return where the preview is */
    public State getState()
    {
        return state;
    }

    /** @return why the last deploy found no floor, or null */
    public RingBlockage getBlockage()
    {
        return blockage;
    }

    /**
     * Lights the pad, the way the countdown does.
     *
     * <p>Called once, when the preview is made.
     */
    public void show()
    {
        for (final int[] block : RingAnimator.openedBlocks(ring))
        {
            draw(drawnLights, block, RingAnimator.OPENED_MATERIAL, false, false);
        }
        for (final int[] block : RingAnimator.lightBlocks(ring))
        {
            draw(drawnLights, block, ring.getLightMaterial(), false, false);
        }
        RingSounds.openedTo(viewer, ring);
    }

    /**
     * Raises the stack, in the end's style, and leaves it standing.
     *
     * <p>A ceiling ring is measured first, as a real cycle does, so the stack forms on the floor
     * and not under the ceiling. A ring with no floor to form on is refused for the same reason
     * a real cycle refuses it. Any other blockage is ignored: a drawing can pass through a
     * staircase, and a preview of a ring that is built over is what somebody fixing it wants.
     *
     * @return what happened
     */
    public Result deploy()
    {
        if (isMoving())
        {
            return Result.BUSY;
        }
        if (state == State.DEPLOYED)
        {
            return Result.ALREADY_STANDING;
        }
        final RingBlockage found = world.survey(ring);
        if ((found == RingBlockage.CEILING_TOO_HIGH) || (found == RingBlockage.CEILING_TOO_LOW))
        {
            blockage = found;
            return Result.NO_FLOOR;
        }
        blockage = null;
        style = ring.getStyle();
        state = State.DEPLOYING;
        frame = 0;
        drawFrame(RingAnimator.deployFrame(ring, style, 0));
        RingSounds.ringMovedTo(viewer, ring, 0, false);
        continueDeploy();
        return Result.DONE;
    }

    /**
     * Runs the transport light through the standing stack, towards the pad.
     *
     * @return what happened
     */
    public Result light()
    {
        if (isMoving())
        {
            return Result.BUSY;
        }
        if (state != State.DEPLOYED)
        {
            return Result.NO_STACK;
        }
        state = State.LIGHTING;
        RingSounds.flashedTo(viewer, ring);
        sweep(0);
        return Result.DONE;
    }

    /**
     * Brings the rings home, nearest first, and leaves the pad lit.
     *
     * @return what happened
     */
    public Result collapse()
    {
        if (isMoving())
        {
            return Result.BUSY;
        }
        if (state != State.DEPLOYED)
        {
            return Result.NO_STACK;
        }
        state = State.COLLAPSING;
        frame = 0;
        drawFrame(RingAnimator.retractFrame(ring, style, 0));
        RingSounds.ringMovedTo(viewer, ring, 0, true);
        continueCollapse();
        return Result.DONE;
    }

    /**
     * Takes everything back at once, showing the viewer the real blocks.
     *
     * <p>The pending step is cancelled first, so nothing is drawn after this has taken the
     * drawing away. Safe to call twice.
     */
    public void clear()
    {
        cleared = true;
        cancelPending();
        clearSet(drawnRings);
        clearSet(drawnLights);
    }

    /** @return whether a step is moving, which no other step may interrupt */
    private boolean isMoving()
    {
        return (state == State.DEPLOYING) || (state == State.LIGHTING)
            || (state == State.COLLAPSING);
    }

    /** Draws the next deploy frame after a pause, or stands the stack if it is all up. */
    private void continueDeploy()
    {
        if (frame >= (RingAnimator.deployFrames(ring, style) - 1))
        {
            state = State.DEPLOYED;
            return;
        }
        later(ConfigManager.getRingDeployTicks(), () ->
        {
            frame++;
            drawFrame(RingAnimator.deployFrame(ring, style, frame));
            RingSounds.ringMovedTo(viewer, ring, frame, false);
            continueDeploy();
        });
    }

    /**
     * Draws one frame of the light, then the next after a pause.
     *
     * <p>The frame after the last puts the plain stack back, as the real cycle's does, so the
     * light is gone and the stack stands as it did.
     *
     * @param step
     *            which frame, from zero
     */
    private void sweep(final int step)
    {
        drawFrame(RingAnimator.settledStack(ring, style));
        if (step >= RingAnimator.flashFrames())
        {
            state = State.DEPLOYED;
            return;
        }
        drawOver(RingAnimator.ringAtRest(ring, RingAnimator.litRing(step, false)),
            ring.getFlashMaterial());
        later(ConfigManager.getRingFlashTicks(), () -> sweep(step + 1));
    }

    /** Draws the next retract frame after a pause, or takes the last ring away. */
    private void continueCollapse()
    {
        later(ConfigManager.getRingDeployTicks(), () ->
        {
            frame++;
            if (frame >= RingAnimator.deployFrames(ring, style))
            {
                clearSet(drawnRings);
                state = State.LIT;
                return;
            }
            drawFrame(RingAnimator.retractFrame(ring, style, frame));
            RingSounds.ringMovedTo(viewer, ring, frame, true);
            continueCollapse();
        });
    }

    /**
     * Replaces what is drawn with a frame of the stack.
     *
     * <p>Everything from the previous frame comes out before anything of the new one goes in,
     * for the reason the real cycle does it that way round.
     *
     * @param placements
     *            where the slabs go
     */
    private void drawFrame(final List<RingAnimator.Placement> placements)
    {
        clearSet(drawnRings);
        drawOver(placements, ring.getRingMaterial());
    }

    /**
     * Draws slabs on top of whatever is drawn already.
     *
     * @param placements
     *            where the slabs go
     * @param material
     *            what they are made of
     */
    private void drawOver(final List<RingAnimator.Placement> placements, final Material material)
    {
        for (final RingAnimator.Placement placement : placements)
        {
            draw(drawnRings, new int[] { placement.getX(), placement.getY(), placement.getZ() },
                material, true, placement.isTop());
        }
    }

    /**
     * Shows one block to the viewer and remembers it, so it can be taken back.
     *
     * @param into
     *            which drawing it belongs to
     * @param block
     *            the position, {@code {x, y, z}}
     * @param material
     *            what to show
     * @param slab
     *            true to show a slab
     * @param top
     *            for a slab, true to fill the upper half
     */
    private void draw(final Set<Long> into, final int[] block, final Material material,
        final boolean slab, final boolean top)
    {
        into.add(Long.valueOf(RingIndex.pack(block[0], block[1], block[2])));
        if (slab)
        {
            world.showSlab(block[0], block[1], block[2], material, top);
        }
        else
        {
            world.showBlock(block[0], block[1], block[2], material);
        }
    }

    /**
     * Shows the viewer the real block everywhere in one drawing.
     *
     * @param positions
     *            what to stop drawing over
     */
    private void clearSet(final Set<Long> positions)
    {
        for (final Long key : positions)
        {
            final long packed = key.longValue();
            world.reveal(RingIndex.unpackX(packed), RingIndex.unpackY(packed),
                RingIndex.unpackZ(packed));
        }
        positions.clear();
    }

    /**
     * Schedules the next step, which does nothing if the preview has been cleared by then.
     *
     * @param ticks
     *            how long to wait
     * @param step
     *            what to do
     */
    private void later(final long ticks, final Runnable step)
    {
        final BukkitScheduler scheduler = WormholeXTreme.getScheduler();
        task = scheduler.scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), () ->
        {
            task = NO_TASK;
            if (cleared)
            {
                return;
            }
            try
            {
                step.run();
            }
            catch (final RuntimeException e)
            {
                // Taken back rather than left half drawn: a stack nobody is going to finish is
                // a wall only this player can see.
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING,
                    "A ring preview failed and was cleared", e);
                clear();
            }
        }, ticks);
    }

    /** Cancels the step that is waiting, if one is. */
    private void cancelPending()
    {
        final BukkitScheduler scheduler = WormholeXTreme.getScheduler();
        if ((task != NO_TASK) && (scheduler != null))
        {
            scheduler.cancelTask(task);
        }
        task = NO_TASK;
    }
}
