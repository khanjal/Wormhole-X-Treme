package com.wormhole_xtreme.wormhole.command.handlers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

/**
 * The parts of {@code beam admin goto}/{@code send}'s argument handling that need no live
 * server to get right: reading a coordinate, and describing what was resolved. The branches
 * that call {@code Bukkit.getPlayerExact}/{@code getWorld} -- {@link BeamCommand#resolveDestination}'s
 * "one token" and "three or four tokens" cases -- stay covered by manual testing only.
 *
 * <p>That was once because this codebase had no precedent for mocking Bukkit's static
 * accessors. It has since: see {@code OwnerCommandTest}. Who may run these commands at all is
 * covered by {@link BeamAdminPermissionsTest}.
 */
class BeamCommandTest
{
    private final BeamCommand command = new BeamCommand();

    @Test
    void parseCoordinateAcceptsAPlainNumber()
    {
        final CommandSender sender = mock(CommandSender.class);
        assertEquals(12.5, command.parseCoordinate(sender, "12.5"), 1e-9);
        verifyNoInteractions(sender);
    }

    @Test
    void parseCoordinateAcceptsANegativeNumber()
    {
        final CommandSender sender = mock(CommandSender.class);
        assertEquals(-64.0, command.parseCoordinate(sender, "-64"), 1e-9);
    }

    @Test
    void parseCoordinateRejectsNonsenseAndMessagesWhyRatherThanJustFailingSilently()
    {
        final CommandSender sender = mock(CommandSender.class);
        assertNull(command.parseCoordinate(sender, "not-a-number"));
        verify(sender).sendMessage(contains("not-a-number"));
    }

    @Test
    void describeDestinationNamesASinglePlayerToken()
    {
        final String[] args = { "beam", "admin", "goto", "Notch" };
        assertEquals("Notch", BeamCommand.describeDestination(args, 3));
    }

    @Test
    void describeDestinationJoinsThreeCoordinateTokens()
    {
        final String[] args = { "beam", "admin", "goto", "100", "64", "-200" };
        assertEquals("100, 64, -200", BeamCommand.describeDestination(args, 3));
    }

    @Test
    void describeDestinationIgnoresATrailingWorldTokenPastTheCoordinates()
    {
        // Only the x/y/z tokens are named -- the world (a fourth token) is where the
        // traveller ends up, not part of what identifies the spot for the chat message,
        // the same way a beam destination's name never repeats its own world either.
        final String[] args = { "beam", "admin", "send", "Notch", "100", "64", "-200", "world_nether" };
        assertEquals("100, 64, -200", BeamCommand.describeDestination(args, 4));
    }

    /**
     * Three coordinates resolve against the world the caller is already in.
     *
     * <p>Reachable without a live server because the three-token form never asks Bukkit for
     * anything -- the world is the one passed in. The four-token form, which looks a world up
     * by name, is not, and stays as described in the class comment.
     */
    @Test
    void threeCoordinatesResolveAgainstTheDefaultWorld()
    {
        final CommandSender sender = mock(CommandSender.class);
        final String[] args = { "beam", "admin", "goto", "10.5", "64", "-20" };

        final org.bukkit.Location result = command.resolveDestination(sender, args, 3, null, 90f, 45f);

        assertNotNull(result);
        assertEquals(10.5, result.getX(), 1e-9);
        assertEquals(64.0, result.getY(), 1e-9);
        assertEquals(-20.0, result.getZ(), 1e-9);
        assertEquals(90f, result.getYaw(), 1e-6, "the caller's facing is carried, not reset");
        assertEquals(45f, result.getPitch(), 1e-6);
        verifyNoInteractions(sender);
    }

    /**
     * One unreadable coordinate refuses the whole destination.
     *
     * <p>Not two out of three: a beam to a place where one axis silently became something
     * else is worse than a beam that does not happen.
     */
    @Test
    void oneBadCoordinateRefusesTheWholeDestination()
    {
        final CommandSender sender = mock(CommandSender.class);
        final String[] args = { "beam", "admin", "goto", "10", "high", "-20" };

        assertNull(command.resolveDestination(sender, args, 3, null, 0f, 0f));

        verify(sender).sendMessage(contains("high"));
    }

    @Test
    void resolveDestinationRefusesAnArgumentCountThatIsNeitherAPlayerNorCoordinates()
    {
        final CommandSender sender = mock(CommandSender.class);
        final String[] args = { "beam", "admin", "goto", "100", "64" };
        final org.bukkit.Location result = command.resolveDestination(sender, args, 3, null, 0f, 0f);
        assertNull(result, "two bare numbers are neither a player name nor a full x/y/z");
        verify(sender).sendMessage(contains("Expected a player name"));
    }
}
