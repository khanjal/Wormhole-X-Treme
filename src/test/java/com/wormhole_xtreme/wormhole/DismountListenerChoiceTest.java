package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

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
}
