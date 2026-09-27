package com.wormhole_xtreme.wormhole.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.ProxiedCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.permissions.WXPermissions;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions.PermissionType;

/**
 * Whose rights a command asks for when a plugin runs it through a proxy.
 *
 * <p>A check that only asked "is this a player?" would wave a proxy through as though it were the
 * console. The rights asked are those of the proxy's caller: a player stays a player, and a command
 * block running a command as a player stays a command block.
 */
class CommandIssuerTest
{
    private static ProxiedCommandSender proxy(final org.bukkit.command.CommandSender caller,
        final org.bukkit.command.CommandSender callee)
    {
        final ProxiedCommandSender proxied = mock(ProxiedCommandSender.class);
        when(proxied.getCaller()).thenReturn(caller);
        when(proxied.getCallee()).thenReturn(callee);
        return proxied;
    }

    @Test
    void theIssuerIsWhoeverRanTheExecuteThroughAnyNumberOfProxies()
    {
        final Player player = mock(Player.class);
        final BlockCommandSender commandBlock = mock(BlockCommandSender.class);

        assertSame(player, CommandHandlerUtils.issuer(proxy(player, player)));
        assertSame(commandBlock, CommandHandlerUtils.issuer(proxy(proxy(commandBlock, player), player)));
        assertSame(player, CommandHandlerUtils.issuer(player));
    }

    /** A player without wormhole.config does not get it by running the command as themselves. */
    @Test
    void aPlayerThroughExecuteIsHeldToTheirOwnConfigRight()
    {
        final Player player = mock(Player.class);
        try (MockedStatic<WXPermissions> perms = mockStatic(WXPermissions.class))
        {
            perms.when(() -> WXPermissions.checkWXPermissions(player, PermissionType.CONFIG)).thenReturn(false);

            assertFalse(CommandHandlerUtils.hasConfigPermission(proxy(player, player)));

            perms.when(() -> WXPermissions.checkWXPermissions(player, PermissionType.CONFIG)).thenReturn(true);

            assertTrue(CommandHandlerUtils.hasConfigPermission(proxy(player, player)));
        }
    }

    /** A command block running a command as a player is trusted as a command block is. */
    @Test
    void aCommandBlockRunningAsAPlayerIsStillACommandBlock()
    {
        final Player player = mock(Player.class);
        try (MockedStatic<WXPermissions> perms = mockStatic(WXPermissions.class))
        {
            assertTrue(CommandHandlerUtils.hasConfigPermission(proxy(mock(BlockCommandSender.class), player)));
        }
    }
}
