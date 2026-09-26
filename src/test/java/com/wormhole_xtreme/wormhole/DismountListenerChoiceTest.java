package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Set;

import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * The dismount listener is chosen by which event class the server has.
 *
 * <p>Found by booting the jar on a real Paper 1.20: only {@code org.spigotmc}'s
 * {@code EntityDismountEvent} exists there, but the plugin tried the {@code org.bukkit} listener
 * first. {@code registerEvents} does not throw for a missing event type; it logs an ERROR and
 * registers nothing. So the attempt looked like a success, the legacy listener was never tried,
 * and riders could dismount inside an open gate on 1.20 and 1.20.1.
 */
class DismountListenerChoiceTest
{
    private static final String NEW_EVENT = "org.bukkit.event.entity.EntityDismountEvent";
    private static final String OLD_EVENT = "org.spigotmc.event.entity.EntityDismountEvent";
    private static final String NEW_LISTENER = "com.wormhole_xtreme.wormhole.GateDismountListener";
    private static final String OLD_LISTENER = "com.wormhole_xtreme.wormhole.LegacyGateDismountListener";

    private static List<String> onAServerWith(final String... classes)
    {
        return WormholeXTreme.dismountListenersFor(Set.of(classes)::contains);
    }

    @Test
    void aServerWithOnlyTheSpigotEventGetsOnlyTheLegacyListener()
    {
        assertEquals(List.of(OLD_LISTENER), onAServerWith(OLD_EVENT),
            "1.20 and 1.20.1 have only the org.spigotmc event; offering the org.bukkit listener"
                + " there registers nothing and stops the legacy one being tried");
    }

    @Test
    void aServerWithOnlyTheBukkitEventGetsOnlyTheNewListener()
    {
        assertEquals(List.of(NEW_LISTENER), onAServerWith(NEW_EVENT),
            "1.20.6 onwards dropped the org.spigotmc event");
    }

    @Test
    void aServerWithBothEventsTriesTheNewListenerFirst()
    {
        assertEquals(List.of(NEW_LISTENER, OLD_LISTENER), onAServerWith(NEW_EVENT, OLD_EVENT),
            "1.20.4 carries both; the first to register wins, so it should be the current package");
    }

    @Test
    void aServerWithNeitherEventGetsNoListener()
    {
        assertEquals(List.of(), onAServerWith(), "with no dismount event there is nothing to register");
    }

    /** CI runs these against every API from 1.20 to 26.3, so only a class all of them have. */
    @Test
    void theProbeFindsAClassEveryServerHas()
    {
        assertTrue(WormholeXTreme.serverHasClass("org.bukkit.event.Event"),
            "a class that is there must read as present, or no listener is ever registered");
    }

    /**
     * Whichever API the tests run against, exactly one listener this server can carry is
     * registered: the wiring the pure choice above feeds, which is where 1.20 went wrong.
     */
    @Test
    void exactlyOneListenerTheServerCanCarryIsRegistered()
    {
        final PluginManager pm = mock(PluginManager.class);
        final WormholeXTreme plugin = mock(WormholeXTreme.class);

        WormholeXTreme.registerDismountListener(pm, plugin);

        final ArgumentCaptor<Listener> registered = ArgumentCaptor.forClass(Listener.class);
        verify(pm, times(1)).registerEvents(registered.capture(), eq(plugin));
        final String expected = WormholeXTreme.dismountListenersFor(WormholeXTreme::serverHasClass).get(0);
        assertEquals(expected, registered.getValue().getClass().getName(),
            "the first listener whose event this API has should be the one registered");
    }

    @Test
    void theProbeReportsAMissingClassAsAbsentRatherThanThrowing()
    {
        assertFalse(WormholeXTreme.serverHasClass("org.bukkit.event.entity.NoSuchDismountEvent"),
            "a missing class must read as absent, so the next listener is tried");
    }
}
