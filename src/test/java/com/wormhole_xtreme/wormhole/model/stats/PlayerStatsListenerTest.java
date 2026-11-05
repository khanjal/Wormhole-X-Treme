package com.wormhole_xtreme.wormhole.model.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.events.StargateCreatedEvent;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.stats.PlayerStats.Counts;
import com.wormhole_xtreme.wormhole.utils.EntityUtils;

/**
 * Gates built are counted from {@link StargateCreatedEvent}, for its builder (#237).
 *
 * <p>The event fires once the gate exists, so there is nothing left to refuse it. What can go wrong
 * is the wiring: a handler the plugin never registers counts nothing, and does so silently.
 */
class PlayerStatsListenerTest
{
    private static final UUID BUILDER = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    private final PlayerStatsListener listener = new PlayerStatsListener();

    private Player player;

    @BeforeEach
    void setUp()
    {
        PlayerStats.clear();
        player = mock(Player.class);
        // Unstubbed, a mock Player has no UUID, and every count keys on it.
        when(player.getUniqueId()).thenReturn(BUILDER);
    }

    @AfterEach
    void tearDown()
    {
        PlayerStats.clear();
    }

    @Test
    void aGateBuiltCountsForItsBuilder()
    {
        listener.onGateBuilt(new StargateCreatedEvent(mock(Stargate.class), player));
        listener.onGateBuilt(new StargateCreatedEvent(mock(Stargate.class), player));
        listener.onGateBuilt(new StargateCreatedEvent(mock(Stargate.class), player));

        assertEquals(new Counts(0, 3, 0, 0), PlayerStats.of(BUILDER));
    }

    @Test
    void aGateBuiltByNoPlayerCountsForNobody()
    {
        listener.onGateBuilt(new StargateCreatedEvent(mock(Stargate.class), null));

        assertEquals(Counts.NONE, PlayerStats.of(BUILDER));
    }

    /** An NPC plugin's fake player is never counted, so it never tops a scoreboard. */
    @Test
    void aGateBuiltByAnNpcCountsForNobody()
    {
        when(player.hasMetadata(EntityUtils.NPC)).thenReturn(true);

        listener.onGateBuilt(new StargateCreatedEvent(mock(Stargate.class), player));

        assertEquals(Counts.NONE, PlayerStats.of(BUILDER));
    }

    /** At MONITOR, after every other plugin has heard of the gate. */
    @Test
    void theHandlerListensLast() throws Exception
    {
        final EventHandler handler = PlayerStatsListener.class
            .getMethod("onGateBuilt", StargateCreatedEvent.class).getAnnotation(EventHandler.class);

        assertNotNull(handler, "without @EventHandler Bukkit never calls it");
        assertEquals(EventPriority.MONITOR, handler.priority());
    }

    /** The plugin registers the listener with the others, or no gate built is ever counted. */
    @Test
    void thePluginRegistersTheListener() throws Exception
    {
        final WormholeXTreme plugin = PluginTestSupport.install();
        try
        {
            final Server server = mock(Server.class);
            final PluginManager manager = mock(PluginManager.class);
            when(plugin.getServer()).thenReturn(server);
            when(server.getPluginManager()).thenReturn(manager);

            WormholeXTreme.registerEvents();

            final ArgumentCaptor<Listener> registered = ArgumentCaptor.forClass(Listener.class);
            verify(manager, atLeastOnce()).registerEvents(registered.capture(), eq(plugin));
            assertTrue(registered.getAllValues().stream().anyMatch(PlayerStatsListener.class::isInstance),
                "a PlayerStatsListener among " + registered.getAllValues().size() + " registered");
        }
        finally
        {
            PluginTestSupport.remove();
        }
    }
}
