package com.wormhole_xtreme.wormhole.events;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.model.Stargate;

/**
 * The gate lifecycle events other plugins listen for.
 *
 * <p>Bukkit's event contract is easy to satisfy incorrectly in ways that compile and then
 * misbehave at runtime, so the shape of these classes is worth pinning: each concrete event
 * needs its own handler list, reachable both statically and from an instance.
 */
class GateEventTest
{
    private static Stargate namedGate()
    {
        final Stargate gate = new Stargate();
        gate.setGateName("subject");
        return gate;
    }

    @Test
    void eachEventTypeHasItsOwnHandlerList()
    {
        // The bug this prevents: a handler list declared once on the shared base class.
        // Bukkit dispatches on the list the concrete class returns, so a single shared list
        // would deliver every created event to listeners registered for removals and the
        // other way round. It compiles, registers and looks correct until it fires.
        final HandlerList created = StargateCreatedEvent.getHandlerList();
        final HandlerList removed = StargateRemovedEvent.getHandlerList();

        assertNotNull(created);
        assertNotNull(removed);
        assertNotSame(created, removed, "the two event types must not share a handler list");
    }

    @Test
    void theInstanceHandlerListMatchesTheStaticOne()
    {
        // Bukkit reads the static one to register a listener and the instance one to
        // dispatch. If they differ, registration succeeds and nothing is ever called.
        final Stargate gate = namedGate();

        assertSame(StargateCreatedEvent.getHandlerList(),
            new StargateCreatedEvent(gate, null).getHandlers());
        assertSame(StargateRemovedEvent.getHandlerList(),
            new StargateRemovedEvent(gate, null).getHandlers());
    }

    @Test
    void anEventCarriesItsGateAndTheActingPlayer()
    {
        final Stargate gate = namedGate();
        final Player player = mock(Player.class);

        final StargateCreatedEvent created = new StargateCreatedEvent(gate, player);
        assertSame(gate, created.getStargate());
        assertEquals("subject", created.getStargateName());
        assertSame(player, created.getBuilder());

        final StargateRemovedEvent removed = new StargateRemovedEvent(gate, player);
        assertSame(gate, removed.getStargate());
        assertSame(player, removed.getRemover());
    }

    @Test
    void theActingPlayerIsOptional()
    {
        // Gates are also removed by things that are not players, and a listener has to be
        // able to tell that apart rather than being handed something invented.
        final Stargate gate = namedGate();

        assertNull(new StargateCreatedEvent(gate, null).getBuilder());
        assertNull(new StargateRemovedEvent(gate, null).getRemover());
    }

    @Test
    void anEventWithoutAGateIsRefusedAtConstruction()
    {
        // Every listener will call getStargate(). Failing here names the problem; allowing
        // it would surface as an NPE inside somebody else's plugin.
        assertThrows(IllegalArgumentException.class, () -> new StargateCreatedEvent(null, null));
        assertThrows(IllegalArgumentException.class, () -> new StargateRemovedEvent(null, null));
    }

    @Test
    void firingWithNoServerRunningIsHarmless()
    {
        // Gate creation and removal are exercised without a server, and raising an event is
        // something the gate operation does on the way past. It must not be the thing that
        // makes that operation fail.
        final Stargate gate = namedGate();

        assertDoesNotThrow(() -> GateEvents.fireCreated(gate, null));
        assertDoesNotThrow(() -> GateEvents.fireRemoved(gate, null));
    }
}
