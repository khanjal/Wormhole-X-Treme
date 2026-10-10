package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.model.GateViews;
import com.wormhole_xtreme.wormhole.model.Stargate;

/**
 * A player who changes world is no longer drawn any gate's opening cleared for its view (#516).
 *
 * <p>Somebody whose view had already ended before they left kept their place among those sent the
 * cleared opening, and on their return the next chunk-crossing redraw sent them the empty ring again
 * from behind the gate.
 */
class GateViewForgottenOnWorldChangeTest
{
    private Player player;
    private Stargate gate;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install();
        GateViews.clear();
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        gate = mock(Stargate.class);
        when(gate.getGateName()).thenReturn("Abydos");
    }

    @AfterEach
    void tearDown() throws Exception
    {
        GateViews.clear();
        PluginTestSupport.remove();
    }

    @Test
    void aViewerWhoChangesWorldIsForgotten() throws ReflectiveOperationException
    {
        final Set<String> cleared = PrivateStatics.of(GateViews.class, "CLEARED");
        cleared.add("Abydos");
        final Map<UUID, Set<String>> through = PrivateStatics.of(GateViews.class, "SEES_THROUGH");
        through.put(player.getUniqueId(), new HashSet<>(Set.of("Abydos")));
        assertEquals(Material.AIR, GateViews.horizonFor(gate, Material.WATER, player), "cleared for them before");
        // Mocked rather than constructed, as the quit event is: the listener reads only the player.
        final PlayerChangedWorldEvent event = mock(PlayerChangedWorldEvent.class);
        when(event.getPlayer()).thenReturn(player);

        new WormholeXTremePlayerListener().onPlayerChangedWorld(event);

        assertEquals(Material.WATER, GateViews.horizonFor(gate, Material.WATER, player),
            "back in the gate's world, not drawn the view: the horizon");
    }
}
