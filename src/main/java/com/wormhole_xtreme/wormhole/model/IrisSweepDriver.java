package com.wormhole_xtreme.wormhole.model;

import java.util.IdentityHashMap;
import java.util.List;

import org.bukkit.Location;

import com.wormhole_xtreme.wormhole.config.ConfigManager;

/**
 * Runs one iris sweep a ring at a time, for a gate and for a build preview alike (#429).
 *
 * <p>The two used to have a loop each, and a fix to one kept missing the other. What they share
 * is the sequencing, which lives here: the rings in order, a step per ring at
 * {@code gate-iris-step-ticks}, and the order a step does its work in. What they draw with is
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
        /** @return false once there is nothing left to draw on, which stops the sweep unsettled */
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

        /** The work done once the last ring is in, with the sweep already unregistered. */
        void settle();
    }

    /** A booked step that can still be dropped. */
    @FunctionalInterface
    public interface Booking
    {
        void cancel();
    }

    private final List<List<C>> rings;
    private final Canvas<C> canvas;
    private Booking booked;

    /**
     * @param rings
     *            the rings, in the order they are drawn
     */
    public IrisSweepDriver(final List<List<C>> rings, final Canvas<C> canvas)
    {
        this.rings = rings;
        this.canvas = canvas;
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
        booked = canvas.later(ConfigManager.getGateIrisStepTicks(), () -> step(index + 1));
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
        final int maxSteps = ConfigManager.getGateIrisMaxSteps();
        return closing
            ? IrisSweep.closingOrder(cells, style, maxSteps) : IrisSweep.openingOrder(cells, style, maxSteps);
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
}
