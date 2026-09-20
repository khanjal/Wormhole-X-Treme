package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.events.GateEvents;
import com.wormhole_xtreme.wormhole.events.StargateActivatedEvent;
import com.wormhole_xtreme.wormhole.events.StargateShutdownEvent;
import com.wormhole_xtreme.wormhole.utils.WorldUtils;

/**
 * A wormhole opening and closing is announced, once, on the paths that really are one.
 *
 * <p>Declaring the two event classes is the easy half. What breaks is nothing firing them, or
 * something firing them on a path that is not really an open or a close -- and both look
 * exactly like a working feature until somebody writes a listener. Shutdown in particular is
 * called defensively all over this plugin: before a removal, on plugin disable, on a gate that
 * may or may not be running. A gate that was already shut closing "again" would have anything
 * counting wormholes count closes that never happened.
 *
 * <p>There is no server here to dispatch through, so these watch the seam the dispatcher calls
 * rather than registering a real Bukkit listener.
 */
class GateOpenCloseEventTest
{
    private final List<Event> raised = new ArrayList<>();

    private Stargate gate;

    /** Held here: a Location keeps its world weakly, so an inline mock can be collected mid-test. */
    private World world;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.scheduleSyncDelayedTask(any(Plugin.class), any(Runnable.class), anyLong()))
            .thenReturn(42);
        PluginTestSupport.scheduler(scheduler);

        world = mock(World.class);
        gate = quietGate("alpha");

        GateEvents.setDispatcherForTest(raised::add);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        GateEvents.setDispatcherForTest(null);
        PluginTestSupport.scheduler(null);
        PluginTestSupport.remove();
    }

    /** A gate with everything that would touch real blocks or a real world stubbed out. */
    private Stargate quietGate(final String name)
    {
        final Stargate quiet = spy(new Stargate());
        quiet.setGateName(name);
        doReturn(new Location(world, 0, 64, 0)).when(quiet).getGatePlayerTeleportLocation();
        doNothing().when(quiet).toggleDialLeverState(anyBoolean());
        doNothing().when(quiet).toggleRedstoneGateActivatedPower();
        doNothing().when(quiet).relightChevrons();
        doNothing().when(quiet).lightStargate(anyBoolean());
        doNothing().when(quiet).fillGateInterior(any(Material.class));
        return quiet;
    }

    private void dial(final Stargate target)
    {
        try (MockedStatic<WorldUtils> utils = mockStatic(WorldUtils.class))
        {
            StargateDialManager.dialStargate(target);
        }
    }

    private void close(final Stargate target, final StargateShutdownEvent.Reason reason)
    {
        try (MockedStatic<WorldUtils> utils = mockStatic(WorldUtils.class))
        {
            target.shutdownStargate(true, reason);
        }
    }

    private <T extends Event> List<T> ofType(final Class<T> type)
    {
        final List<T> found = new ArrayList<>();
        for (final Event e : raised)
        {
            if (type.isInstance(e))
            {
                found.add(type.cast(e));
            }
        }
        return found;
    }

    @Test
    void diallingAGateAnnouncesTheWormholeOpening()
    {
        dial(gate);

        final List<StargateActivatedEvent> events = ofType(StargateActivatedEvent.class);
        assertEquals(1, events.size(), "opening a wormhole should be announced exactly once");
        assertSame(gate, events.get(0).getStargate(), "and should carry the gate that opened");
    }

    @Test
    void theGateReadsAsOpenWhenTheOpeningIsAnnounced()
    {
        // A listener's first move is to read the gate it was handed. Firing before the state
        // was set would hand it a gate that says it is closed, in an event saying it opened.
        final List<Boolean> seen = new ArrayList<>();
        GateEvents.setDispatcherForTest(e ->
        {
            if (e instanceof StargateActivatedEvent)
            {
                seen.add(Boolean.valueOf(((StargateActivatedEvent) e).getStargate().isGateActive()));
            }
            raised.add(e);
        });

        dial(gate);

        assertEquals(List.of(Boolean.TRUE), seen,
            "the gate should already be active when its opening is announced");
    }

    @Test
    void reDiallingAnAlreadyOpenGateAnnouncesNothingFurther()
    {
        // Dialling an open gate restarts its shutdown clock and relights it; the wormhole
        // itself never closed and reopened. A second event here would have anything counting
        // openings count one per re-dial.
        dial(gate);
        dial(gate);

        assertEquals(1, ofType(StargateActivatedEvent.class).size(),
            "the wormhole opened once, so it should have been announced once");
    }

    @Test
    void closingAnOpenGateAnnouncesItWithTheReasonItClosed()
    {
        dial(gate);
        close(gate, StargateShutdownEvent.Reason.TIMEOUT);

        final List<StargateShutdownEvent> events = ofType(StargateShutdownEvent.class);
        assertEquals(1, events.size(), "closing a wormhole should be announced exactly once");
        assertSame(gate, events.get(0).getStargate());
        assertEquals(StargateShutdownEvent.Reason.TIMEOUT, events.get(0).getReason(),
            "a gate closed by its own clock should not reach a listener as anything else");
    }

    @Test
    void closingAGateThatWasNeverOpenAnnouncesNothing()
    {
        // The reason this test exists: shutdown is called defensively before a removal, on
        // plugin disable, and on gates that may or may not be running. Announcing those would
        // mean a listener sees far more closes than there were wormholes.
        assertFalse(gate.isGateActive(), "the fixture gate should start closed");

        close(gate, StargateShutdownEvent.Reason.PLUGIN_DISABLE);

        assertTrue(ofType(StargateShutdownEvent.class).isEmpty(),
            "a gate that was never open did not close, and must not say it did");
    }

    @Test
    void theGateReadsAsClosedWhenTheShutdownIsAnnounced()
    {
        // The mirror of the opening case. A listener told a wormhole closed should not find a
        // gate that still claims to be open.
        dial(gate);
        final List<Boolean> seen = new ArrayList<>();
        GateEvents.setDispatcherForTest(e ->
        {
            if (e instanceof StargateShutdownEvent)
            {
                seen.add(Boolean.valueOf(((StargateShutdownEvent) e).getStargate().isGateActive()));
            }
            raised.add(e);
        });

        close(gate, StargateShutdownEvent.Reason.MANUAL);

        assertEquals(List.of(Boolean.FALSE), seen,
            "the gate should already be closed when its shutdown is announced");
    }

    @Test
    void closingOneEndOfAPairAnnouncesBothAndSaysWhichWasTheFarEnd()
    {
        // Shutting a gate always shuts its partner. Both wormholes really did close, so both
        // are announced -- but only one of them was closed by the thing the caller named, and
        // the other closed because its partner did.
        final Stargate target = quietGate("bravo");
        dial(gate);
        dial(target);
        gate.setGateTarget(target);
        raised.clear();

        close(gate, StargateShutdownEvent.Reason.MANUAL);

        final List<StargateShutdownEvent> events = ofType(StargateShutdownEvent.class);
        assertEquals(2, events.size(), "both ends closed, so both should be announced");
        assertSame(target, events.get(0).getStargate(),
            "the far end closes first, while the near end can still see it");
        assertEquals(StargateShutdownEvent.Reason.FAR_END, events.get(0).getReason(),
            "the far end closed because its partner did, not because anybody asked it to");
        assertSame(gate, events.get(1).getStargate());
        assertEquals(StargateShutdownEvent.Reason.MANUAL, events.get(1).getReason(),
            "the end the caller named keeps the reason the caller gave");
    }

    @Test
    void theShutdownCallThatDoesNotSayWhyReportsItAsManual()
    {
        // The one-argument overload is what another plugin calls. Nobody outside this plugin
        // can know it was a timeout, and an outside caller asking a gate to close is a manual
        // close by any reading -- but it must not silently arrive as some other reason.
        dial(gate);

        try (MockedStatic<WorldUtils> utils = mockStatic(WorldUtils.class))
        {
            gate.shutdownStargate(true);
        }

        final List<StargateShutdownEvent> events = ofType(StargateShutdownEvent.class);
        assertEquals(1, events.size());
        assertEquals(StargateShutdownEvent.Reason.MANUAL, events.get(0).getReason());
    }
}
