package com.wormhole_xtreme.wormhole.model.window;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

import org.bukkit.scheduler.BukkitTask;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * Every fixed view being built, a few milliseconds of them a tick (#609).
 *
 * <p>Each build is a {@link FixedBuild}; a ticker runs while any is left, taking them in turn until
 * the tick's share of time is spent, and stops itself when none is. A finished build is handed to
 * whoever asked, which swaps it in for the old view in one go, so a viewer sees the old view until
 * the new one is whole. Without a scheduler to run on -- startup, or a test -- a build runs to the
 * end as it is asked for.
 */
final class FixedBuilds
{
    /** Time a tick may spend on builds, across every window, by default. */
    static final long NANOS_PER_TICK = 4_000_000L;

    /** The same, settable for a test. */
    static long nanosPerTick = NANOS_PER_TICK;

    /** The clock the share is measured on; settable so a test can count steps as time. */
    static LongSupplier nanos = System::nanoTime;

    /** The builds in progress, by window name, in the order they were asked for. */
    private static final Map<String, FixedBuild> BUILDS = new LinkedHashMap<>();

    /** What each build hands its view to once finished, by window name. */
    private static final Map<String, Consumer<FixedBuild>> DONE = new LinkedHashMap<>();

    /** The ticker, while there are builds; null otherwise. */
    private static BukkitTask task;

    /** Static state only. */
    private FixedBuilds()
    {
    }

    /**
     * Asks for a window's fixed view to be built, unless a build for what it now draws is under way.
     *
     * @param build
     *            the build, not yet stepped
     * @param done
     *            handed the build once finished on a later tick, on the main thread
     * @return true if it was built to the end here, for want of a scheduler, and {@code done} was not called
     */
    static boolean request(final FixedBuild build, final Consumer<FixedBuild> done)
    {
        final String name = build.window.name();
        final FixedBuild running = BUILDS.get(name);
        if ((running != null) && running.buildsFor(build.window))
        {
            return false;
        }
        BUILDS.put(name, build);
        DONE.put(name, done);
        if (schedule())
        {
            return false;
        }
        // Nothing to run it a tick at a time: all of it now.
        BUILDS.remove(name);
        DONE.remove(name);
        while (!build.step())
        {
            // Each step is one x, or one column.
        }
        return true;
    }

    /** @return true if a build for this window is under way */
    static boolean building(final String name)
    {
        return BUILDS.containsKey(name);
    }

    /** @return the build under way for a window, or null */
    static FixedBuild buildFor(final String name)
    {
        return BUILDS.get(name);
    }

    /** @return how many builds are under way */
    static int count()
    {
        return BUILDS.size();
    }

    /**
     * Drops a window's build, if it has one: it was taken down, or is no longer offered.
     *
     * @param name
     *            the window
     */
    static void cancel(final String name)
    {
        BUILDS.remove(name);
        DONE.remove(name);
        stopIfIdle();
    }

    /**
     * Drops every build but those of windows still held.
     *
     * @param held
     *            whether a window of that name is still held
     */
    static void retain(final Predicate<String> held)
    {
        BUILDS.keySet().removeIf(name -> !held.test(name));
        DONE.keySet().retainAll(BUILDS.keySet());
        stopIfIdle();
    }

    /** Forgets every build and stops the ticker, for a test or a reload. */
    static void clear()
    {
        BUILDS.clear();
        DONE.clear();
        stopIfIdle();
        nanosPerTick = NANOS_PER_TICK;
        nanos = System::nanoTime;
    }

    /** Starts the ticker if it is not running. @return false if there is no scheduler to run it on */
    private static boolean schedule()
    {
        if (task != null)
        {
            return true;
        }
        try
        {
            task = WormholeXTreme.getScheduler().runTaskTimer(WormholeXTreme.getThisPlugin(), FixedBuilds::tick, 1L, 1L);
        }
        catch (final RuntimeException noScheduler)
        {
            task = null;
        }
        return task != null;
    }

    /** Stops the ticker once there is nothing left to build. */
    private static void stopIfIdle()
    {
        if (BUILDS.isEmpty() && (task != null))
        {
            task.cancel();
            task = null;
        }
    }

    /**
     * One tick's share: builds stepped in turn, one step each round, until the time is spent or
     * none is left; each finished build handed over.
     */
    static void tick()
    {
        final long until = nanos.getAsLong() + nanosPerTick;
        final List<FixedBuild> finished = new ArrayList<>();
        boolean any = true;
        while (any && (nanos.getAsLong() < until))
        {
            any = false;
            for (final Iterator<FixedBuild> it = BUILDS.values().iterator(); it.hasNext() && (nanos.getAsLong() < until);)
            {
                final FixedBuild build = it.next();
                any = true;
                if (build.step())
                {
                    it.remove();
                    finished.add(build);
                }
            }
        }
        for (final FixedBuild build : finished)
        {
            final Consumer<FixedBuild> done = DONE.remove(build.window.name());
            if (done != null)
            {
                done.accept(build);
            }
        }
        stopIfIdle();
    }
}
