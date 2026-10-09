package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;

/**
 * The sequencing a gate's iris sweep and a build preview's share, on a canvas that only records.
 *
 * <p>The two used to run a loop each, and three fixes in one session reached one loop and not
 * the other (#429). {@code IrisSweepOrderingTest} and {@code GatePreviewsTest} pin the same rules
 * through each side's real drawing; these pin them on the driver itself, where a change to the
 * order a step does its work in would otherwise only show as a failure on one side.
 */
class IrisSweepDriverTest
{
    /** Rings deliberately out of any sorted order, so a driver that walked them by value would show. */
    private static final List<List<String>> RINGS = List.of(List.of("c"), List.of("a", "d"), List.of("b"));

    private final List<String> events = new ArrayList<>();
    /** Booked steps not yet run, by booking number; a cancelled one is really dropped. */
    private final Map<Integer, Runnable> pending = new LinkedHashMap<>();
    private final List<Long> delays = new ArrayList<>();
    /** Every sweep the canvas was asked to register, in order, as each side's registry would hold it. */
    private final List<IrisSweepDriver<String>> registered = new ArrayList<>();
    /** The booking numbers a call-off dropped. */
    private final List<Integer> cancelled = new ArrayList<>();
    private boolean valid = true;

    /** Records everything the driver asks of it, in order. */
    private final IrisSweepDriver.Canvas<String> canvas = new IrisSweepDriver.Canvas<>()
    {
        @Override
        public boolean stillValid()
        {
            return valid;
        }

        @Override
        public void drawRing(final List<String> ring)
        {
            events.add("draw" + ring);
        }

        @Override
        public void moveHorizon(final List<String> ring)
        {
            events.add("horizon" + ring);
        }

        @Override
        public IrisSweepDriver.Booking later(final long ticks, final Runnable step)
        {
            final int id = delays.size();
            delays.add(ticks);
            pending.put(id, step);
            events.add("book");
            return () ->
            {
                cancelled.add(id);
                pending.remove(id);
            };
        }

        @Override
        public void register(final IrisSweepDriver<String> sweep)
        {
            registered.add(sweep);
            events.add("register");
        }

        @Override
        public void unregister()
        {
            events.add("unregister");
        }

        @Override
        public void settle()
        {
            events.add("settle");
        }
    };

    @BeforeEach
    void setUp()
    {
        ConfigTestSupport.set(ConfigKeys.GATE_IRIS_STEP_TICKS, 7);
        ConfigTestSupport.set(ConfigKeys.GATE_IRIS_SWEEP_MAX_TICKS, 0);
    }

    @AfterEach
    void tearDown()
    {
        ConfigTestSupport.clear();
    }

    /** Runs the one booked step, failing if there is not exactly one. */
    private void runNextStep()
    {
        assertEquals(1, pending.size(), "one step booked at a time: " + pending.keySet());
        final Integer id = pending.keySet().iterator().next();
        pending.remove(id).run();
    }

    /**
     * The first ring is drawn by the toggle itself, not a step later.
     *
     * <p>A sweep whose first ring waited for the scheduler would show the toggle doing nothing for
     * a step, and on a preview the first draw is the one that marks it as sweeping.
     */
    @Test
    void theFirstRingIsDrawnAsTheSweepStartsThenItsStepIsBooked()
    {
        final IrisSweepDriver<String> sweep = new IrisSweepDriver<>(RINGS, canvas);
        sweep.start();

        assertEquals(List.of("draw[c]", "horizon[c]", "book", "register"), events,
            "the ring, then its far layer, then the next step booked and the sweep registered against it");
        assertSame(sweep, registered.get(0), "the sweep registered itself, not some other driver");
    }

    /**
     * Every ring in the order handed in, a step each, each at the configured pace.
     *
     * <p>The pace is the whole feel of the animation and the step count its length; a pace that
     * fell back to a constant would change every iris without failing anything else.
     */
    @Test
    void eachRingIsDrawnInTurnAndBooksOneStepAtTheConfiguredPace()
    {
        new IrisSweepDriver<>(RINGS, canvas).start();
        while (!pending.isEmpty())
        {
            runNextStep();
        }

        assertEquals(List.of("draw[c]", "draw[a, d]", "draw[b]"),
            events.stream().filter(e -> e.startsWith("draw")).toList(), "the rings as handed in, each once");
        assertEquals(List.of(7L, 7L, 7L), delays, "one booking per ring, seven ticks each, not the default two");
    }

    /**
     * The settle waits a full step after the last ring, and runs with the sweep already unregistered.
     *
     * <p>A gate's settle takes the real blocks away or lets a toggle start the next sweep, and a
     * preview's draws the stacked layers only for a preview that is not sweeping. Run while still
     * registered, either finds the sweep that is ending and treats it as running.
     */
    @Test
    void theSettleComesAStepAfterTheLastRingAndAfterUnregistering()
    {
        new IrisSweepDriver<>(RINGS, canvas).start();
        runNextStep();
        runNextStep();
        assertEquals("register", events.get(events.size() - 1), "the last ring is in and its step booked");
        assertFalse(events.contains("settle"), "but it has not settled with the last ring");

        events.clear();
        runNextStep();

        assertEquals(List.of("unregister", "settle"), events, "unregistered, then settled, and nothing drawn or booked");
        assertTrue(pending.isEmpty(), "nothing left booked");
    }

    /**
     * Calling a sweep off drops its booked step and settles nothing.
     *
     * <p>Whoever calls it off has already decided what the iris is to look like, and a settle
     * arriving on top would undo that. Unregistering is the caller's: both sides take the sweep out
     * of their own register before they call it off.
     *
     * <p>Called off through what the register holds, as both sides do: a step that registered some
     * other driver, or a driver that kept an earlier booking, would leave the waiting step to run.
     */
    @Test
    void callingASweepOffDropsItsStepAndSettlesNothing()
    {
        final IrisSweepDriver<String> sweep = new IrisSweepDriver<>(RINGS, canvas);
        sweep.start();
        runNextStep();
        assertEquals(1, pending.size(), "a step is waiting");
        assertEquals(2, registered.size(), "registered once per step");
        registered.forEach(held -> assertSame(sweep, held, "every step registered the same sweep"));
        events.clear();

        registered.get(registered.size() - 1).cancel();

        assertEquals(List.of(1), cancelled, "the second booking, the one waiting, and only that");
        assertTrue(pending.isEmpty(), "the booked step is dropped: " + pending.keySet());
        assertEquals(List.of(), events, "and nothing settled, drawn or unregistered by the call-off");
    }

    /**
     * A sweep whose canvas has gone part way through stops there, unregistered and unsettled.
     *
     * <p>A gate's world unloading, or a preview nobody holds any more: nothing to draw on, and a
     * settle would take blocks away in a world that is not there or spawn displays nobody removes.
     */
    @Test
    void aSweepWhoseCanvasHasGoneStopsWithoutSettling()
    {
        new IrisSweepDriver<>(RINGS, canvas).start();
        valid = false;
        events.clear();

        runNextStep();

        assertEquals(List.of("unregister"), events, "let go, without drawing, booking or settling");
        assertTrue(pending.isEmpty(), "and nothing more booked");
    }

    /**
     * Once the last ring is in, the sweep settles even if the canvas has gone since.
     *
     * <p>The rings are asked after before the canvas, as the gate's loop always did; the preview's
     * settle checks for itself whether it is still held. A driver that refused to settle here would
     * leave a gate's built iris standing after an opening sweep whose world flickered at the end.
     */
    @Test
    void aSweepWhoseLastRingIsInSettlesEvenIfItsCanvasHasGone()
    {
        new IrisSweepDriver<>(RINGS, canvas).start();
        runNextStep();
        runNextStep();
        valid = false;
        events.clear();

        runNextStep();

        assertEquals(List.of("unregister", "settle"), events, "settled, since there was no ring left to draw");
    }

    /** A three-by-three opening, with a fourth cell at the middle's own coordinates. */
    private static List<Location> openingWithADuplicateMiddle()
    {
        final List<Location> cells = new ArrayList<>();
        for (int x = -1; x <= 1; x++)
        {
            for (int y = -1; y <= 1; y++)
            {
                cells.add(new Location(null, x, y, 0));
            }
        }
        cells.add(new Location(null, 0, 0, 0));
        return cells;
    }

    /**
     * Ring indexes are mapped back by identity, so two cells at one place keep an index each.
     *
     * <p>A malformed shape can put two cells of an opening at the same coordinates. Mapped by
     * equality, both would come back as whichever was put last, and the other would never be drawn.
     */
    @Test
    void twoCellsAtTheSamePlaceKeepAnIndexEach()
    {
        final List<List<Integer>> rings = IrisSweepDriver.ringIndexes(openingWithADuplicateMiddle(), "sweep", true);

        final List<Integer> every = rings.stream().flatMap(List::stream).sorted().toList();
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), every, "each cell exactly once");
        assertEquals(new TreeSet<>(List.of(4, 9)), new TreeSet<>(rings.get(rings.size() - 1)),
            "closing ends on the middle, both cells of it");
    }

    /** Closing comes in from the rim, opening draws back from the middle. */
    @Test
    void openingIsTheClosingOrderRunBackwards()
    {
        final List<Location> cells = openingWithADuplicateMiddle();
        final List<List<Integer>> closing = IrisSweepDriver.ringIndexes(cells, "sweep", true);
        final List<List<Integer>> opening = IrisSweepDriver.ringIndexes(cells, "sweep", false);

        assertEquals(new TreeSet<>(List.of(4, 9)), new TreeSet<>(opening.get(0)), "opening starts from the middle");
        assertEquals(closing.size(), opening.size());
        for (int i = 0; i < closing.size(); i++)
        {
            assertEquals(closing.get(i), opening.get(opening.size() - 1 - i), "ring " + i + " from either end");
        }
    }
}
