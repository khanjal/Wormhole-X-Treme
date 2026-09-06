package com.wormhole_xtreme.wormhole.command;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import java.util.concurrent.Callable;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * What the command safety net is for, and what it should let past.
 *
 * <p>{@code runCommandSafe} wraps every command so a bug in one cannot take the server's
 * command handling down with it. It caught {@code Throwable}, which meant a genuinely broken
 * server was answered with the same "an internal error occurred" as a null pointer in a
 * command, and then forgotten.
 */
class CommandSafetyNetTest
{
    private Player player;

    @BeforeEach
    void installPlugin() throws Exception
    {
        final Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, mock(WormholeXTreme.class));
        player = mock(Player.class);
    }

    /** A command that throws is answered, not propagated: the rest of the server carries on. */
    @Test
    void aCommandThatThrowsIsAnsweredRatherThanPropagated()
    {
        final Callable<Boolean> boom = () -> { throw new IllegalStateException("bug"); };

        assertTrue(CommandUtilities.runCommandSafe(player, boom));

        verify(player).sendMessage(contains("internal error"));
    }

    /**
     * A checked exception is still caught, since {@code Callable.call} may throw one.
     *
     * <p>This is why the net takes Exception rather than RuntimeException: narrowing it that
     * far would not compile, and narrowing the caller instead would let a checked failure out
     * of a command.
     */
    @Test
    void aCheckedExceptionIsCaughtToo()
    {
        final Callable<Boolean> boom = () -> { throw new java.io.IOException("disk"); };

        assertTrue(CommandUtilities.runCommandSafe(player, boom));

        verify(player).sendMessage(contains("internal error"));
    }

    /**
     * An Error is not dressed up as a command bug.
     *
     * <p>Telling a player their command hit an internal error, and carrying on, is the wrong
     * answer to a server that has run out of heap. That belongs to the server.
     */
    @Test
    void anErrorIsNotReportedAsACommandBug()
    {
        final Callable<Boolean> boom = () -> { throw new OutOfMemoryError("heap"); };

        assertThrows(OutOfMemoryError.class, () -> CommandUtilities.runCommandSafe(player, boom),
            "an Error is the server's to handle, not a command's to report and swallow");
    }
}
