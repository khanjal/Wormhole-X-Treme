package com.wormhole_xtreme.wormhole;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

import org.bukkit.scheduler.BukkitTask;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.model.GateSounds;
import com.wormhole_xtreme.wormhole.model.HorizonRipple;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.window.WindowSweep;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorSignpost;

/**
 * The plugin's repeating sweeps. One whose period is a setting is rescheduled when
 * {@code /wormhole config} changes it, without a restart.
 */
public final class RepeatingSweeps
{
    /** Ticks between scans for loose entities drifting into an open wormhole. */
    static final long ENTITY_SCAN_TICKS = 20L;

    /** Ticks between ambient hums, a little under the sound's own length so it runs on. */
    static final long GATE_AMBIENT_TICKS = 70L;

    /** Ticks between mirror proximity sweeps: once a second. */
    static final long MIRROR_PROXIMITY_TICKS = 20L;

    /** Ticks between frames of the wormhole drawn behind a see-through iris. */
    static final long IRIS_HORIZON_TICKS = 10L;

    /** One sweep: the setting that times it, or null for a fixed period, and the task running it now, if any. */
    private static final class Sweep
    {
        private final ConfigKeys key;

        private final long firstDelay;

        private final Runnable work;

        private final LongSupplier period;

        private BukkitTask task;

        Sweep(final ConfigKeys key, final long firstDelay, final Runnable work, final LongSupplier period)
        {
            this.key = key;
            this.firstDelay = firstDelay;
            this.work = work;
            this.period = period;
        }
    }

    private static final List<Sweep> sweeps = new ArrayList<>();

    private RepeatingSweeps()
    {
    }

    /** Starts every sweep at its period, forgetting any from an earlier enable. */
    static void startAll()
    {
        sweeps.clear();
        // Loose non-player entities that drift into an open wormhole: dropped items and wandering
        // mobs, which raise no event of their own. Players and vehicles have theirs.
        sweeps.add(new Sweep(null, 20L, GateEntityScanner.create(), () -> ENTITY_SCAN_TICKS));
        // An open wormhole hums: one sweep over the open gates rather than a task per gate.
        sweeps.add(new Sweep(null, 20L, GateSounds::tickAmbient, () -> GATE_AMBIENT_TICKS));
        // A mirror on a wall is drawn as a view of its room, and names itself to whoever looks at
        // it. Two tasks, one walking the mirrors and one the players, sharing the period.
        sweeps.add(new Sweep(null, 40L, WindowSweep.createTicker(), () -> MIRROR_PROXIMITY_TICKS));
        sweeps.add(new Sweep(null, 40L, MirrorSignpost.createTicker(), () -> MIRROR_PROXIMITY_TICKS));
        // The ice behind a see-through iris, moved for it.
        sweeps.add(new Sweep(null, 20L, StargateManager::tickIrisHorizon, () -> IRIS_HORIZON_TICKS));
        // A ripple now and then on each open gate somebody is near; off, there is no task at all.
        sweeps.add(new Sweep(ConfigKeys.GATE_RIPPLE, 20L, HorizonRipple::tick,
            () -> ConfigManager.isGateRipple() ? 20L : 0L));
        for (final Sweep sweep : sweeps)
        {
            sweep.task = start(sweep, sweep.firstDelay);
        }
    }

    /** Forgets every sweep, for a test that started them against a mock scheduler. */
    static void clear()
    {
        sweeps.clear();
    }

    /**
     * Reschedules every sweep this setting times, at its period now.
     *
     * @param key
     *            the setting just changed
     * @return true if the setting times a sweep
     */
    public static boolean follow(final ConfigKeys key)
    {
        final List<Sweep> timed = sweeps.stream().filter(sweep -> sweep.key == key).toList();
        // Every replacement is started before any old task stops, so a scheduler that refuses
        // leaves the sweeps running as they were rather than not at all.
        final List<BukkitTask> started = new ArrayList<>();
        try
        {
            for (final Sweep sweep : timed)
            {
                started.add(start(sweep, sweep.period.getAsLong()));
            }
        }
        catch (final RuntimeException refused)
        {
            for (final BukkitTask task : started)
            {
                if (task != null)
                {
                    task.cancel();
                }
            }
            throw refused;
        }
        for (int i = 0; i < timed.size(); i++)
        {
            final Sweep sweep = timed.get(i);
            if (sweep.task != null)
            {
                sweep.task.cancel();
            }
            sweep.task = started.get(i);
        }
        return !timed.isEmpty();
    }

    /**
     * The periods of the sweeps running now, once per sweep.
     *
     * @return ticks, in the order the sweeps were started
     */
    static List<Long> runningPeriods()
    {
        return sweeps.stream().filter(sweep -> sweep.task != null).map(sweep -> sweep.period.getAsLong()).toList();
    }

    /**
     * Schedules one sweep, unless its period says it is off.
     *
     * @return the task, or null when the sweep is off
     */
    private static BukkitTask start(final Sweep sweep, final long delay)
    {
        final long period = sweep.period.getAsLong();
        if (period <= 0L)
        {
            return null;
        }
        return WormholeXTreme.getScheduler().runTaskTimer(WormholeXTreme.getThisPlugin(), sweep.work,
            Math.max(1L, delay), period);
    }
}
