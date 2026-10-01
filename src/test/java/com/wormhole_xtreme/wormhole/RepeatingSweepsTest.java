package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
import java.util.Collections;

/**
 * A sweep's period changed with {@code /wormhole config} takes effect at once.
 *
 * <p>The research facility's stage 6 set {@code gate-sound-ambient-ticks} in-game and the hum kept
 * its old interval until a restart, though the guide says a change needs none: the period was handed
 * to the scheduler once, at enable. Four settings time a sweep that way.
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
        ConfigTestSupport.set(ConfigKeys.ENTITY_SCAN_INTERVAL_TICKS, 20);
        ConfigTestSupport.set(ConfigKeys.GATE_SOUND_AMBIENT_TICKS, 70);
        ConfigTestSupport.set(ConfigKeys.MIRROR_PROXIMITY_TICKS, 25);
        ConfigTestSupport.set(ConfigKeys.GATE_IRIS_HORIZON_TICKS, 10);
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

    /** Every sweep a setting times is started, once each, the mirrors' period timing two. */
    @Test
    void everySweepASettingTimesIsStarted()
    {
        assertEquals(List.of(ConfigKeys.ENTITY_SCAN_INTERVAL_TICKS, ConfigKeys.GATE_SOUND_AMBIENT_TICKS,
            ConfigKeys.MIRROR_PROXIMITY_TICKS, ConfigKeys.MIRROR_PROXIMITY_TICKS, ConfigKeys.GATE_IRIS_HORIZON_TICKS),
            RepeatingSweeps.runningKeys());
    }

    /** The reported case: the hum moves to its new interval, and the old one stops. */
    @Test
    void theHumFollowsANewIntervalWithoutARestart()
    {
        final List<BukkitTask> hum = startedAt(70L);
        assertEquals(1, hum.size(), "one hum task, started at the configured 70: " + scheduled);

        ConfigManager.applySetting("gate-sound-ambient-ticks", "40");

        verify(hum.get(0)).cancel();
        verify(scheduler).runTaskTimer(eq(plugin), any(Runnable.class), eq(40L), eq(40L));
    }

    /** Both mirror sweeps share their period, so both move. */
    @Test
    void bothMirrorSweepsFollowTheProximityInterval()
    {
        final List<BukkitTask> mirrors = startedAt(25L);
        assertEquals(2, mirrors.size(), "the proximity sweep and the signpost: " + scheduled);

        ConfigManager.applySetting("mirror-proximity-ticks", "30");

        verify(mirrors.get(0)).cancel();
        verify(mirrors.get(1)).cancel();
        assertEquals(2, startedAt(30L).size(), "both started again at 30");
    }

    /** The entity sweep follows its interval too. */
    @Test
    void theEntitySweepFollowsItsInterval()
    {
        final List<BukkitTask> sweep = startedAt(20L);
        assertEquals(1, sweep.size(), scheduled.toString());

        ConfigManager.applySetting("entity-scan-interval-ticks", "60");

        verify(sweep.get(0)).cancel();
        assertEquals(1, startedAt(60L).size());
    }

    /** At 0 the iris horizon has no task at all, and set again it has one back. */
    @Test
    void theIrisHorizonStopsAtZeroAndStartsAgainAfter()
    {
        final List<BukkitTask> horizon = startedAt(10L);
        assertEquals(1, horizon.size(), scheduled.toString());

        final int before = scheduled.size();
        ConfigManager.applySetting("gate-iris-horizon-ticks", "0");
        verify(horizon.get(0)).cancel();
        assertEquals(before, scheduled.size(), "nothing scheduled at a period of 0");
        assertFalse(RepeatingSweeps.runningKeys().contains(ConfigKeys.GATE_IRIS_HORIZON_TICKS),
            "and the horizon is not counted as running");

        ConfigManager.applySetting("gate-iris-horizon-ticks", "15");
        assertEquals(1, startedAt(15L).size(), "running again at 15");
        assertTrue(RepeatingSweeps.runningKeys().contains(ConfigKeys.GATE_IRIS_HORIZON_TICKS));
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
     * A scheduler that refuses the new period leaves the sweeps running at the old one.
     *
     * <p>Cancelling first left a refused change with no sweep at all until a restart, while the
     * reply said the change would wait for one. Both mirror sweeps move together or not at all.
     */
    @Test
    void aRefusedRescheduleLeavesBothMirrorSweepsRunning()
    {
        final List<BukkitTask> mirrors = startedAt(25L);
        final int[] calls = { 0 };
        final List<BukkitTask> replacements = new ArrayList<>();
        when(scheduler.runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
            .thenAnswer(inv -> {
                if (++calls[0] == 2)
                {
                    throw new IllegalStateException("scheduler refused");
                }
                final BukkitTask task = mock(BukkitTask.class);
                replacements.add(task);
                return task;
            });

        final String said = ConfigManager.applySetting("mirror-proximity-ticks", "30");

        assertTrue(said.contains("could not be applied now"), said);
        verify(mirrors.get(0), never()).cancel();
        verify(mirrors.get(1), never()).cancel();
        verify(replacements.get(0)).cancel();
        assertEquals(2, Collections.frequency(RepeatingSweeps.runningKeys(), ConfigKeys.MIRROR_PROXIMITY_TICKS),
            "both still running");
    }
}
