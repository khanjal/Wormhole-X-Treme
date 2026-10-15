package com.wormhole_xtreme.wormhole.model.window;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.logging.Level;

import org.bukkit.scheduler.BukkitTask;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * Every fixed view being built, a few milliseconds of them a tick (#609).
 *
 * <p>Each build is a {@link FixedBuild}; a ticker runs while any is left, stepping the one stepped
 * longest ago, a step at a time, until the tick's share of time is spent, and stops itself when none
 * is left. A finished build is handed to whoever asked, which swaps it in for the old view in one go,
 * so a viewer sees the old view until the new one is whole. A build that throws is dropped, said once,
 * and leaves its window's old view as it is. Without a scheduler to run on -- startup, or a test -- a
 * build runs to the end as it is asked for.
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

    /** The failures already logged, by kind: a build that fails once a tick is said once. */
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    /** What one step of a build came to. */
    private enum Step
    {
        MORE, DONE, FAILED
    }

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
        Step step = Step.MORE;
        while (step == Step.MORE)
        {
            step = stepOf(build);
        }
        return step == Step.DONE;
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
        WARNED.clear();
        stopIfIdle();
        nanosPerTick = NANOS_PER_TICK;
        nanos = System::nanoTime;
    }

    /** Starts the ticker if it is not running. @return false if there is no scheduler to run it on */
    private static boolean schedule()
    {
        if ((task != null) && !task.isCancelled())
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
     * One tick's share: the build stepped longest ago stepped once and put to the back, again and
     * again, until the time is spent or none is left; each finished build handed over.
     *
     * <p>Round the builds in turn, across ticks too: started from the first every tick, a step dearer
     * than the share moved only the first window on.
     */
    static void tick()
    {
        final long until = nanos.getAsLong() + nanosPerTick;
        final List<FixedBuild> finished = new ArrayList<>();
        while (!BUILDS.isEmpty() && (nanos.getAsLong() < until))
        {
            final Map.Entry<String, FixedBuild> head = BUILDS.entrySet().iterator().next();
            final String name = head.getKey();
            final FixedBuild build = head.getValue();
            BUILDS.remove(name);
            final Step step = stepOf(build);
            if (step == Step.DONE)
            {
                finished.add(build);
            }
            else if (step == Step.MORE)
            {
                BUILDS.put(name, build);
            }
            else
            {
                DONE.remove(name);
            }
        }
        for (final FixedBuild build : finished)
        {
            handOver(build);
        }
        stopIfIdle();
    }

    /** One step of a build, a failure caught: the build is then dropped, and its window keeps its old view. */
    private static Step stepOf(final FixedBuild build)
    {
        try
        {
            return build.step() ? Step.DONE : Step.MORE;
        }
        catch (final Exception | LinkageError failed)
        {
            warnOnce("Could not build the view through " + build.window.name() + "; its old view stays (said once)",
                failed);
            return Step.FAILED;
        }
    }

    /** Hands a finished build to whoever asked, without letting its failure keep the others' from theirs. */
    private static void handOver(final FixedBuild build)
    {
        final Consumer<FixedBuild> done = DONE.remove(build.window.name());
        if (done == null)
        {
            return;
        }
        try
        {
            done.accept(build);
        }
        catch (final Exception | LinkageError failed)
        {
            warnOnce("Could not put in the view built through " + build.window.name() + " (said once)", failed);
        }
    }

    /** Logs a failure once for each kind of it. */
    private static void warnOnce(final String what, final Throwable failed)
    {
        final WormholeXTreme plugin = WormholeXTreme.getThisPlugin();
        if ((plugin != null) && WARNED.add(failed.getClass().getName() + " " + failed.getMessage()))
        {
            plugin.prettyLog(Level.WARNING, what, failed);
        }
    }
}
