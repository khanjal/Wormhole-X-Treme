package com.wormhole_xtreme.wormhole.permissions;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions.PermissionType;

/**
 * An operator may do anything with a gate.
 *
 * <p>That holds with or without a permissions plugin installed, and it holds for every
 * permission type — including any added later. The check used to be a switch naming each
 * type with {@code default: return false}, so a new type that nobody remembered to list
 * would have been silently denied to operators, looking for all the world like a
 * misconfigured permissions plugin.
 */
class OperatorPermissionTest
{
    private Player op;

    @BeforeEach
    void setUp() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        final java.lang.reflect.Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, plugin);

        op = mock(Player.class);
        when(op.getName()).thenReturn("admin");
        when(op.isOp()).thenReturn(true);
        // Explicitly denied every node, to prove op is what grants access here and not a
        // permission lookup quietly succeeding underneath.
        when(op.hasPermission(anyString())).thenReturn(false);
    }

    @Test
    void anOperatorHasEveryPermissionType()
    {
        for (final PermissionType type : PermissionType.values())
        {
            assertTrue(WXPermissions.checkWXPermissions(op, type),
                "an operator should be allowed " + type);
        }
    }

    @Test
    void anOperatorHasEveryPermissionTypeAgainstAGateTheyDoNotOwn()
    {
        final com.wormhole_xtreme.wormhole.model.Stargate gate =
            new com.wormhole_xtreme.wormhole.model.Stargate();
        gate.setGateName("someoneElses");
        gate.setGateOwner("00000000-0000-0000-0000-000000000001");

        for (final PermissionType type : PermissionType.values())
        {
            assertTrue(WXPermissions.checkWXPermissions(op, gate, type),
                "an operator should be allowed " + type + " on a gate they do not own");
        }
    }

    @Test
    void aPlainPlayerDeniedEveryNodeGetsNothingOnAnotherPlayersGate()
    {
        // The mirror case: without op and without any node, an owned gate stays closed.
        // Without this, the test above would pass even if the check allowed everyone.
        final Player plain = mock(Player.class);
        when(plain.getName()).thenReturn("visitor");
        when(plain.isOp()).thenReturn(false);
        when(plain.hasPermission(anyString())).thenReturn(false);
        // Ownership is compared by UUID, so the visitor needs one that is not the owner.
        when(plain.getUniqueId()).thenReturn(java.util.UUID.fromString("00000000-0000-0000-0000-0000000000ff"));

        final com.wormhole_xtreme.wormhole.model.Stargate gate =
            new com.wormhole_xtreme.wormhole.model.Stargate();
        gate.setGateName("someoneElses");
        gate.setGateOwner("00000000-0000-0000-0000-000000000001");

        assertFalse(WXPermissions.checkWXPermissions(plain, gate, PermissionType.REMOVE),
            "a player with no op and no nodes should not be able to remove another player's gate");
    }
}
