package com.wormhole_xtreme.wormhole;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

import org.bukkit.scheduler.BukkitTask;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.model.GateSounds;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorProximity;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorSignpost;

/**
 * The repeating sweeps whose period is a setting, so {@code /wormhole config} can change one
 * without a restart: the task is cancelled and scheduled again at the new period.
 */
public final class RepeatingSweeps
{
    /** One sweep: the setting that times it, and the task running it now, if any. */
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

    /** Starts every sweep at its configured period, forgetting any from an earlier enable. */
    static void startAll()
    {
        sweeps.clear();
        // Loose non-player entities that drift into an open wormhole: dropped items and wandering
        // mobs, which raise no event of their own. Players and vehicles have theirs.
        sweeps.add(new Sweep(ConfigKeys.ENTITY_SCAN_INTERVAL_TICKS, 20L, GateEntityScanner.create(),
            ConfigManager::getEntityScanIntervalTicks));
        // An open wormhole hums: one sweep over the open gates rather than a task per gate.
        sweeps.add(new Sweep(ConfigKeys.GATE_SOUND_AMBIENT_TICKS, 20L, GateSounds::tickAmbient,
            ConfigManager::getGateSoundAmbientTicks));
        // A mirror on a wall is drawn as a view of its room, and names itself to whoever looks at
        // it. Two tasks, one walking the mirrors and one the players, sharing the period.
        sweeps.add(new Sweep(ConfigKeys.MIRROR_PROXIMITY_TICKS, 40L, MirrorProximity.createTicker(),
            ConfigManager::getMirrorProximityTicks));
        sweeps.add(new Sweep(ConfigKeys.MIRROR_PROXIMITY_TICKS, 40L, MirrorSignpost.createTicker(),
            ConfigManager::getMirrorProximityTicks));
        // The ice behind a see-through iris, moved for it; at 0 there is no task at all.
        sweeps.add(new Sweep(ConfigKeys.GATE_IRIS_HORIZON_TICKS, 20L, StargateManager::tickIrisHorizon,
            ConfigManager::getGateIrisHorizonTicks));
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
     * The settings timing a sweep that is running now, once per sweep.
     *
     * @return the keys, in the order the sweeps were started
     */
    static List<ConfigKeys> runningKeys()
    {
        return sweeps.stream().filter(sweep -> sweep.task != null).map(sweep -> sweep.key).toList();
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
