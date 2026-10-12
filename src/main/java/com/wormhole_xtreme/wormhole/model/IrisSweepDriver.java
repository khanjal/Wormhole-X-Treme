package com.wormhole_xtreme.wormhole.model;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.LongSupplier;

import org.bukkit.Location;

/**
 * Runs one iris sweep a ring at a time, for a gate and for a build preview alike (#429).
 *
 * <p>The two used to have a loop each, and a fix to one kept missing the other. What they share
 * is the sequencing, which lives here: the rings in order, a step per ring at
 * {@link #STEP_TICKS}, and the order a step does its work in. What they draw with is
 * irreducibly different -- block changes for a gate, display entities for a preview -- and is
 * the {@link Canvas}.
 *
 * <p>A step does not ask whether its sweep was called off. Cancelling the booked step is the
 * whole of that guard, as it was in both loops before.
 *
 * @param <C>
 *            what a ring is made of: a gate's locations, or a preview's opening indexes
 */
public final class IrisSweepDriver<C>
{
    /**
     * What a sweep draws on, and where it is registered.
     *
     * @param <C>
     *            what a ring is made of
     */
    public interface Canvas<C>
    {
        /**
         * Asked only before a ring is drawn, never before {@link #settle()}.
         *
         * @return false once there is nothing left to draw on, which stops the sweep part way, unsettled
         */
        boolean stillValid();

        /** Covers or uncovers one ring. */
        void drawRing(List<C> ring);

        /** Moves the far layer for the ring just drawn. */
        void moveHorizon(List<C> ring);

        /** Books a step, returning how to call it off. */
        Booking later(long ticks, Runnable step);

        /** Records this sweep as the one running, after each booking. */
        void register(IrisSweepDriver<C> sweep);

        /** Forgets this sweep, before it settles or stops. */
        void unregister();

        /**
         * The work done once the last ring is in, with the sweep already unregistered.
         *
         * <p>May run after the canvas has gone, since {@link #stillValid()} is not asked first: the
         * canvas decides what that means. A gate runs its afterwards regardless; a preview checks
         * again that it is still held.
         */
        void settle();
    }

    /** A booked step that can still be dropped. */
    @FunctionalInterface
    public interface Booking
    {
        void cancel();
    }

    /** Ticks between one ring of a sweep and the next. */
    public static final int STEP_TICKS = 2;

    /** The longest a whole crossing may take, however big the gate: a wider one merges rings into bands. */
    public static final int SWEEP_MAX_TICKS = 20;

    private static int paceTicks = STEP_TICKS;

    private static int stepCap = maxSteps(SWEEP_MAX_TICKS, STEP_TICKS);

    private final List<List<C>> rings;
    private final Canvas<C> canvas;
    private final LongSupplier pace;
    private Booking booked;

    /**
     * @param rings
     *            the rings, in the order they are drawn
     */
    public IrisSweepDriver(final List<List<C>> rings, final Canvas<C> canvas)
    {
        this(rings, canvas, () -> paceTicks);
    }

    /**
     * @param rings
     *            the rings, in the order they are drawn
     * @param pace
     *            the ticks between one ring and the next, asked at each step
     */
    public IrisSweepDriver(final List<List<C>> rings, final Canvas<C> canvas, final LongSupplier pace)
    {
        this.rings = rings;
        this.canvas = canvas;
        this.pace = pace;
    }

    /** Draws the first ring now and books the rest. */
    public void start()
    {
        step(0);
    }

    /**
     * Drops the step this sweep has booked. Settles nothing and unregisters nothing: whoever calls
     * it off has already taken it out of their register.
     */
    public void cancel()
    {
        if (booked != null)
        {
            booked.cancel();
        }
    }

    /**
     * Draws one ring and books the next, or finishes.
     *
     * <p>Unregistered before it settles, so the settle work sees no sweep running. The rings are
     * asked after before the canvas, so a sweep whose last ring is in settles even if the canvas
     * has gone since; the canvas's settle decides what that means for it.
     */
    private void step(final int index)
    {
        final boolean finished = index >= rings.size();
        if (finished || !canvas.stillValid())
        {
            canvas.unregister();
            if (finished)
            {
                canvas.settle();
            }
            return;
        }
        canvas.drawRing(rings.get(index));
        canvas.moveHorizon(rings.get(index));
        // The index is carried by the step booked rather than kept here, so each booking runs its
        // own ring however late it fires.
        booked = canvas.later(pace.getAsLong(), () -> step(index + 1));
        // Booked before registered: the registry holds this sweep for its latest booking, which a call-off drops.
        canvas.register(this);
    }

    /**
     * An opening's cells in the order a sweep draws them.
     *
     * @param cells
     *            the cells the iris is made of
     * @param animation
     *            the iris animation in effect, which picks the style
     * @param closing
     *            true for the closing order, rim first
     * @return the rings
     */
    public static List<List<Location>> rings(final List<Location> cells, final String animation, final boolean closing)
    {
        final IrisSweep.Style style = IrisSweep.Style.of(animation);
        return closing
            ? IrisSweep.closingOrder(cells, style, stepCap) : IrisSweep.openingOrder(cells, style, stepCap);
    }

    /**
     * The same, as indexes into the list handed in.
     *
     * <p>Mapped back by identity: {@link IrisSweep} groups the very objects it is handed, so a
     * location found in a ring is the one at that index and no other. Equality would do the wrong
     * thing on an opening with two cells at the same coordinates, which a malformed shape can
     * produce.
     *
     * @return one list of indexes per ring
     */
    public static List<List<Integer>> ringIndexes(final List<Location> cells, final String animation,
        final boolean closing)
    {
        final IdentityHashMap<Location, Integer> index = new IdentityHashMap<>();
        for (int i = 0; i < cells.size(); i++)
        {
            index.put(cells.get(i), i);
        }
        return rings(cells, animation, closing).stream()
            .map(ring -> ring.stream().map(index::get).toList())
            .toList();
    }

    /**
     * The most steps a sweep may be drawn in, at a pace inside a limit.
     *
     * <p>Floored at two wherever there is a limit: a crossing of one step is the instant iris written
     * the long way round.
     *
     * @param sweepMaxTicks
     *            the longest a crossing may take, or 0 or less for no limit
     * @param stepTicks
     *            the ticks between steps, at least 1
     * @return the most steps, or 0 for a step per ring however many that is
     */
    static int maxSteps(final int sweepMaxTicks, final int stepTicks)
    {
        return (sweepMaxTicks <= 0) ? 0 : Math.max(2, sweepMaxTicks / Math.max(1, stepTicks));
    }

    /** @return the most steps a sweep is drawn in now, or 0 for a step per ring */
    public static int stepCap()
    {
        return stepCap;
    }

    /**
     * Sets the pace and limit every sweep after it uses, for a test; {@link #resetPaceForTest} puts them back.
     *
     * @param step
     *            ticks between steps
     * @param sweepMaxTicks
     *            the longest a crossing may take, or 0 for no limit
     */
    public static void setPaceForTest(final int step, final int sweepMaxTicks)
    {
        paceTicks = step;
        stepCap = maxSteps(sweepMaxTicks, step);
    }

    /** Puts the shipped pace and limit back. */
    public static void resetPaceForTest()
    {
        setPaceForTest(STEP_TICKS, SWEEP_MAX_TICKS);
    }
}
