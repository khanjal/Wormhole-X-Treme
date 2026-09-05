package com.wormhole_xtreme.wormhole.events;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.command.CommandUtilities;
import com.wormhole_xtreme.wormhole.model.GateSpatialIndex;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * The events are raised by the operations they describe.
 *
 * <p>Declaring the event classes is the easy half. The half that actually breaks is nothing
 * ever firing them, or firing them somewhere that reads as a removal but is not one — and
 * both of those look exactly like a working feature until somebody writes a listener.
 *
 * <p>There is no server here to dispatch through, so these watch the seam the dispatcher
 * calls rather than registering a real Bukkit listener.
 */
class GateEventDispatchTest
{
    private final List<Event> raised = new ArrayList<Event>();
    private World world;

    @BeforeEach
    void setUp() throws Exception
    {
        GateSpatialIndex.clear();
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        final java.lang.reflect.Field pf = WormholeXTreme.class.getDeclaredField("thisPlugin");
        pf.setAccessible(true);
        pf.set(null, plugin);

        world = mock(World.class);
        when(world.getName()).thenReturn("w");

        GateEvents.setDispatcherForTest(raised::add);
    }

    @AfterEach
    void tearDown()
    {
        GateEvents.setDispatcherForTest(null);
        GateSpatialIndex.clear();
    }

    private Stargate registeredGate(final String name)
    {
        final Stargate gate = new Stargate();
        gate.setGateName(name);
        gate.setGateWorld(world);
        gate.getGatePortalBlocks().add(new Location(world, 1, 64, 1));
        StargateManager.registerStargate(gate);
        return gate;
    }

    private List<Event> ofType(final Class<? extends Event> type)
    {
        final List<Event> found = new ArrayList<Event>();
        for (final Event e : raised)
        {
            if (type.isInstance(e))
            {
                found.add(e);
            }
        }
        return found;
    }

    @Test
    void removingAGateAnnouncesIt()
    {
        final Stargate gate = registeredGate("doomed");
        final Player remover = mock(Player.class);

        StargateManager.removeStargate(gate, remover);

        final List<Event> events = ofType(StargateRemovedEvent.class);
        assertEquals(1, events.size(), "exactly one removal should have been announced");
        final StargateRemovedEvent event = (StargateRemovedEvent) events.get(0);
        assertSame(gate, event.getStargate());
        assertSame(remover, event.getRemover());
    }

    @Test
    void theGateIsStillReadableWhenTheRemovalIsAnnounced()
    {
        // A listener's whole reason to handle this is to act on the gate before it goes.
        // Firing after teardown would hand it a gate with nothing left on it, so the event
        // has to come first.
        final Stargate gate = registeredGate("readable");
        final int portalBlocks = gate.getGatePortalBlocks().size();

        GateEvents.setDispatcherForTest(e ->
        {
            if (e instanceof StargateRemovedEvent)
            {
                final Stargate seen = ((StargateRemovedEvent) e).getStargate();
                assertEquals("readable", seen.getGateName(), "name should still be set");
                assertEquals(portalBlocks, seen.getGatePortalBlocks().size(),
                    "blocks should not have been torn down yet");
                assertSame(seen, StargateManager.getStargate("readable"),
                    "the gate should still be findable by name");
            }
            raised.add(e);
        });

        StargateManager.removeStargate(gate);

        assertEquals(1, ofType(StargateRemovedEvent.class).size());
    }

    @Test
    void refreshingAGateDoesNotAnnounceARemoval()
    {
        // A refresh deregisters the gate and registers it again with freshly detected
        // geometry. It runs through the same removal path but the gate is still there
        // afterwards, so announcing it would have a listener discard its records for that
        // gate every single time anybody ran a refresh.
        final Stargate gate = registeredGate("refreshed");

        CommandUtilities.gateRemove(gate, false, false);

        assertTrue(ofType(StargateRemovedEvent.class).isEmpty(),
            "a re-registration is not a removal and must not be announced as one");
    }

    @Test
    void anOrdinaryGateRemovalCommandStillAnnounces()
    {
        // The control for the test above: suppressing the refresh case must not have
        // suppressed real removals, which is the entire point of the event.
        final Stargate gate = registeredGate("genuinely-removed");

        CommandUtilities.gateRemove(gate, false);

        assertEquals(1, ofType(StargateRemovedEvent.class).size());
    }
}
