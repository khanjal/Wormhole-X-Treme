package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;

/**
 * The repeating sweeps start at their periods, and one timed by a setting follows a change to it
 * at once.
 *
 * <p>The research facility's stage 6 set the ambient hum's period in-game and it kept its old
 * interval until a restart, though the guide says a change needs none: the period was handed to
 * the scheduler once, at enable. The hum's period is fixed now; the ripple's on-off is the setting
 * that still times a sweep.
 */
class RepeatingSweepsTest
{
    /** A task the mock scheduler handed out, with what it was asked for. */
    private record Scheduled(BukkitTask task, long delay, long period)
    {
    }

    @TempDir
    File dataFolder;

    private WormholeXTreme plugin;
    private BukkitScheduler scheduler;
    private final List<Scheduled> scheduled = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception
    {
        plugin = PluginTestSupport.install();
        // Applying a setting saves config.yml; without a data folder that is ./plugins.
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
            .thenAnswer(inv -> {
                final BukkitTask task = mock(BukkitTask.class);
                scheduled.add(new Scheduled(task, inv.getArgument(2, Long.class), inv.getArgument(3, Long.class)));
                return task;
            });
        PluginTestSupport.scheduler(scheduler);
        ConfigTestSupport.set(ConfigKeys.GATE_RIPPLE, false);
        ConfigTestSupport.set(ConfigKeys.GATE_SOUNDS_ENABLED, true);
        RepeatingSweeps.startAll();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        RepeatingSweeps.clear();
        ConfigTestSupport.clear();
        PluginTestSupport.scheduler(null);
        PluginTestSupport.remove();
    }

    /** The tasks started at this period. */
    private List<BukkitTask> startedAt(final long period)
    {
        final List<BukkitTask> found = new ArrayList<>();
        for (final Scheduled s : scheduled)
        {
            if (s.period() == period)
            {
                found.add(s.task());
            }
        }
        return found;
    }

    /**
     * Every sweep starts at its period: the entity scan, the hum, both mirror sweeps and the iris
     * horizon, at what their settings defaulted to before they were fixed. The ripple is off.
     */
    @Test
    void everySweepStartsAtItsPeriod()
    {
        assertEquals(List.of(20L, 70L, 20L, 20L, 10L), RepeatingSweeps.runningPeriods());
    }

    /** Turned on, the ripple sweep starts at once, and turned off again it stops. */
    @Test
    void theRippleFollowsItsSettingWithoutARestart()
    {
        final int before = scheduled.size();

        ConfigManager.applySetting("gate-ripple", "true");

        assertEquals(before + 1, scheduled.size(), "one ripple task: " + scheduled);
        final Scheduled ripple = scheduled.get(before);
        assertEquals(20L, ripple.period());
        assertEquals(List.of(20L, 70L, 20L, 20L, 10L, 20L), RepeatingSweeps.runningPeriods());

        ConfigManager.applySetting("gate-ripple", "false");

        verify(ripple.task()).cancel();
        assertEquals(List.of(20L, 70L, 20L, 20L, 10L), RepeatingSweeps.runningPeriods(),
            "and the ripple is not counted as running");
    }

    /** A setting that times no sweep leaves them all running as they were. */
    @Test
    void aSettingThatTimesNoSweepLeavesThemAlone()
    {
        final int before = scheduled.size();
        ConfigManager.applySetting("gate-sounds-enabled", "false");

        assertEquals(before, scheduled.size(), "nothing rescheduled: " + scheduled);
        for (final Scheduled s : scheduled)
        {
            verify(s.task(), never()).cancel();
        }
    }

    /**
     * A scheduler that refuses the new period leaves the sweeps running as they were.
     *
     * <p>Cancelling first left a refused change with no sweep at all until a restart, while the
     * reply said the change would wait for one.
     */
    @Test
    void aRefusedRescheduleLeavesTheSweepsRunning()
    {
        ConfigManager.applySetting("gate-ripple", "true");
        final BukkitTask ripple = scheduled.get(scheduled.size() - 1).task();
        when(scheduler.runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
            .thenThrow(new IllegalStateException("scheduler refused"));

        assertThrows(IllegalStateException.class, () -> RepeatingSweeps.follow(ConfigKeys.GATE_RIPPLE));

        verify(ripple, never()).cancel();
        assertEquals(List.of(20L, 70L, 20L, 20L, 10L, 20L), RepeatingSweeps.runningPeriods(), "all still running");
    }

    /** The fixed sweeps are never rescheduled, whatever setting changes. */
    @Test
    void theFixedSweepsAreNotTimedByAnySetting()
    {
        for (final ConfigKeys key : ConfigKeys.values())
        {
            if (key != ConfigKeys.GATE_RIPPLE)
            {
                assertFalse(RepeatingSweeps.follow(key), key + " times no sweep");
            }
        }
        for (final Scheduled s : scheduled)
        {
            verify(s.task(), never()).cancel();
        }
    }
}
