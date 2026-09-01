package com.wormhole_xtreme.wormhole.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * Unit tests for the `/wormhole complete` command parsing and robustness.
 */
public class CompleteCommandTest
{
    @BeforeEach
    public void beforeEach()
    {
        // Ensure plugin reference exists to avoid NPEs during logging
        final WormholeXTreme pluginMock = mock(WormholeXTreme.class);
        try
        {
            final java.lang.reflect.Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
            f.setAccessible(true);
            f.set(null, pluginMock);
        }
        catch (final Throwable ignore) {}
    }

    @AfterEach
    public void afterEach()
    {
        // no-op cleanup; tests remove any pending completion they create
    }

    @Test
    public void completeHandlesIdcWithSeparatedValue()
    {
        final Player player = mock(Player.class);
        when(player.isOp()).thenReturn(true);
        when(player.getName()).thenReturn("tester");
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        // Ensure no existing gate with this name
        final String gateName = "NickelUnit";
        final Stargate existing = StargateManager.getStargate(gateName);
        if (existing != null)
        {
            StargateManager.removeStargate(existing);
        }

        StargateManager.removeIncompleteStargate(player);

        final String[] args = new String[] { gateName, "idc=", "test" };

        final Complete subject = new Complete();
        final boolean result = subject.onCommand(player, null, "wormhole", args);

        assertTrue(result, "onCommand should return true for complete invocation");

        final String[] pending = Complete.getPendingCompletion(player);
        assertNotNull(pending, "Pending completion should have been registered");
        assertEquals(gateName, pending[0]);
        assertEquals("test", pending[1]);
        assertEquals("", pending[2]);

        Complete.removePendingCompletion(player);
    }

    @Test
    public void completeHandlesEmptyIdcToken()
    {
        final Player player = mock(Player.class);
        when(player.isOp()).thenReturn(true);
        when(player.getName()).thenReturn("tester2");
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        final String gateName = "NickelUnit2";
        final Stargate existing = StargateManager.getStargate(gateName);
        if (existing != null)
        {
            StargateManager.removeStargate(existing);
        }

        StargateManager.removeIncompleteStargate(player);

        final String[] args = new String[] { gateName, "idc=" };

        final Complete subject = new Complete();
        final boolean result = subject.onCommand(player, null, "wormhole", args);

        assertTrue(result);

        final String[] pending = Complete.getPendingCompletion(player);
        assertNotNull(pending);
        assertEquals(gateName, pending[0]);
        assertEquals("", pending[1]);
        assertEquals("", pending[2]);

        Complete.removePendingCompletion(player);
    }

}
