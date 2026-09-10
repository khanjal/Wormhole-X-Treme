package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;

/**
 * When a redstone signal on an already-open gate buys it more time, and when it does not.
 *
 * <p>A signal landing on an open gate used to do nothing at all, and that was deliberate:
 * re-dialling restarts the shutdown timer, so a minecart crossing a detector rail every few
 * seconds would have held a wormhole open for ever and locked everyone else out of its
 * target. Doing nothing was safe by construction.
 *
 * <p>It does something now, and it is only safe because of the refusals below. Every one of
 * them is the difference between "a signal can buy more time" and "a signal can buy unlimited
 * time", which is the bug the old do-nothing behaviour existed to avoid.
 *
 * <p>{@link StargateDialManager#shutdownDelayTicks} -- the arithmetic that decides how long,
 * and the clamp that stops it exceeding the maximum -- is pinned separately in
 * {@code ShutdownDelayTest}. What was never covered is this method's own refusals: nothing
 * here reached the null check, the spent-maximum branch, the no-timeout branch, or the
 * cancellation of a shutdown already pending.
 */
class ExtendOpenTimeTest
{
    private Stargate gate;
    private BukkitScheduler scheduler;
    private MockedStatic<ConfigManager> config;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        scheduler = mock(BukkitScheduler.class);
        PluginTestSupport.scheduler(scheduler);

        gate = mock(Stargate.class);
        when(gate.getGateName()).thenReturn("alpha");
        when(gate.isGateActive()).thenReturn(true);

        config = mockStatic(ConfigManager.class);
        // 30s of shutdown timeout, and a maximum that is nowhere near spent, so a test that
        // does not say otherwise takes the ordinary extend-it path.
        config.when(ConfigManager::getTimeoutShutdown).thenReturn(Integer.valueOf(30));
        config.when(ConfigManager::getMaxOpenSeconds).thenReturn(Integer.valueOf(300));
        when(gate.remainingOpenMillis(anyInt())).thenReturn(Long.MAX_VALUE);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        config.close();
        PluginTestSupport.remove();
    }

    /** Nothing to extend, and nothing to throw over either. */
    @Test
    void thereIsNothingToExtendAboutANullGate()
    {
        assertFalse(StargateDialManager.extendOpenTime(null),
            "a null gate should be refused rather than reaching the scheduler");
        verify(scheduler, never()).scheduleSyncDelayedTask(any(), any(Runnable.class), anyLong());
    }

    /**
     * A closed gate is not given time it is not using.
     *
     * <p>The signal that reaches this method does not know whether the gate is open; the
     * redstone listener calls it either way. Extending a gate that is not active would
     * schedule a shutdown for a wormhole that is not there.
     */
    @Test
    void aGateThatIsNotOpenIsNotExtended()
    {
        when(gate.isGateActive()).thenReturn(false);

        assertFalse(StargateDialManager.extendOpenTime(gate),
            "only an open gate has a shutdown worth pushing back");
        verify(scheduler, never()).scheduleSyncDelayedTask(any(), any(Runnable.class), anyLong());
    }

    /**
     * A gate that has reached its maximum open time gets nothing more.
     *
     * <p>This is the refusal the whole feature rests on. Without it a signal every few
     * seconds extends the gate every few seconds, for ever, which is exactly the behaviour
     * that made "do nothing" the right answer before a maximum existed.
     */
    @Test
    void aGateAtItsMaximumOpenTimeIsNotExtended()
    {
        when(gate.remainingOpenMillis(anyInt())).thenReturn(0L);

        assertFalse(StargateDialManager.extendOpenTime(gate),
            "a spent maximum must not be extendable, or the maximum is not a maximum");
        verify(scheduler, never()).scheduleSyncDelayedTask(any(), any(Runnable.class), anyLong());
    }

    /**
     * With no shutdown timeout configured there is nothing to push back.
     *
     * <p>A gate on a server with the timeout switched off was never going to close on its
     * own, so there is no pending task to move -- and scheduling one here would make a
     * redstone signal the thing that starts closing gates on a server that had asked for
     * gates that stay open.
     */
    @Test
    void aServerWithNoShutdownTimeoutHasNothingToPushBack()
    {
        config.when(ConfigManager::getTimeoutShutdown).thenReturn(Integer.valueOf(0));

        assertFalse(StargateDialManager.extendOpenTime(gate),
            "with no timeout there is no shutdown to move, so nothing should be scheduled");
        verify(scheduler, never()).scheduleSyncDelayedTask(any(), any(Runnable.class), anyLong());
    }

    /**
     * The pending shutdown is cancelled before another is scheduled.
     *
     * <p>Without the cancel the old task survives alongside the new one, and the gate closes
     * on whichever fires first -- which is the original shutdown. The extension would appear
     * to work, log that it worked, and change nothing at all.
     */
    @Test
    void anAlreadyPendingShutdownIsCancelledRatherThanLeftToFireFirst()
    {
        when(gate.getGateShutdownTaskId()).thenReturn(77);
        when(scheduler.scheduleSyncDelayedTask(any(), any(Runnable.class), anyLong()))
            .thenReturn(88);

        assertTrue(StargateDialManager.extendOpenTime(gate), "an open gate should extend");

        verify(scheduler).cancelTask(77);
        verify(gate).setGateShutdownTaskId(88);
    }

    /** A gate with no shutdown pending has nothing to cancel, and still gets its timer. */
    @Test
    void aGateWithNoPendingShutdownIsStillGivenOne()
    {
        when(gate.getGateShutdownTaskId()).thenReturn(0);
        when(scheduler.scheduleSyncDelayedTask(any(), any(Runnable.class), anyLong()))
            .thenReturn(91);

        assertTrue(StargateDialManager.extendOpenTime(gate), "an open gate should extend");

        verify(scheduler, never()).cancelTask(0);
        verify(gate).setGateShutdownTaskId(91);
    }
}
