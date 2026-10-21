package com.wormhole_xtreme.wormhole.command.handlers;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.UUID;

import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/** The gate admins the batch 10 message tests (#26) send commands as, and the gates they work on. */
final class AdminReaders
{
    private AdminReaders()
    {
    }

    /** @return an admin holding every permission, whose client reads {@code locale} */
    static Player reading(final String locale)
    {
        final Player player = mock(Player.class);
        when(player.getLocale()).thenReturn(locale);
        when(player.getName()).thenReturn("admin");
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOp()).thenReturn(Boolean.TRUE);
        when(player.hasPermission(anyString())).thenReturn(Boolean.TRUE);
        return player;
    }

    /** @return a registered gate called {@code name} */
    static Stargate gate(final String name)
    {
        final Stargate gate = new Stargate();
        gate.setGateName(name);
        StargateManager.registerStargate(gate);
        return gate;
    }

    /** Forgets every registered gate. */
    static void clearGates()
    {
        for (final Stargate gate : new ArrayList<>(StargateManager.getAllGates()))
        {
            if (gate != null)
            {
                StargateManager.removeStargate(gate);
            }
        }
    }
}
