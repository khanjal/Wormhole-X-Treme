package com.wormhole_xtreme.wormhole.command.handlers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

/**
 * The parts of {@code beam admin goto}/{@code send}'s argument handling that need no live
 * server to get right: reading a coordinate, and describing what was resolved. The branches
 * that call {@code Bukkit.getPlayerExact}/{@code getWorld} -- {@link BeamCommand#resolveDestination}'s
 * "one token" and "three or four tokens" cases -- stay covered by manual testing only, the
 * same as the rest of this class always has been; this codebase has no precedent for mocking
 * Bukkit's static accessors.
 */
public class BeamCommandTest
{
    private final BeamCommand command = new BeamCommand();

    @Test
    public void parseCoordinateAcceptsAPlainNumber()
    {
        final CommandSender sender = mock(CommandSender.class);
        assertEquals(12.5, command.parseCoordinate(sender, "12.5"), 1e-9);
        verifyNoInteractions(sender);
    }

    @Test
    public void parseCoordinateAcceptsANegativeNumber()
    {
        final CommandSender sender = mock(CommandSender.class);
        assertEquals(-64.0, command.parseCoordinate(sender, "-64"), 1e-9);
    }

    @Test
    public void parseCoordinateRejectsNonsenseAndMessagesWhyRatherThanJustFailingSilently()
    {
        final CommandSender sender = mock(CommandSender.class);
        assertNull(command.parseCoordinate(sender, "not-a-number"));
        verify(sender).sendMessage(contains("not-a-number"));
    }

    @Test
    public void describeDestinationNamesASinglePlayerToken()
    {
        final String[] args = { "beam", "admin", "goto", "Notch" };
        assertEquals("Notch", BeamCommand.describeDestination(args, 3));
    }

    @Test
    public void describeDestinationJoinsThreeCoordinateTokens()
    {
        final String[] args = { "beam", "admin", "goto", "100", "64", "-200" };
        assertEquals("100, 64, -200", BeamCommand.describeDestination(args, 3));
    }

    @Test
    public void describeDestinationIgnoresATrailingWorldTokenPastTheCoordinates()
    {
        // Only the x/y/z tokens are named -- the world (a fourth token) is where the
        // traveller ends up, not part of what identifies the spot for the chat message,
        // the same way a beam destination's name never repeats its own world either.
        final String[] args = { "beam", "admin", "send", "Notch", "100", "64", "-200", "world_nether" };
        assertEquals("100, 64, -200", BeamCommand.describeDestination(args, 4));
    }

    @Test
    public void resolveDestinationRefusesAnArgumentCountThatIsNeitherAPlayerNorCoordinates()
    {
        final CommandSender sender = mock(CommandSender.class);
        final String[] args = { "beam", "admin", "goto", "100", "64" };
        final org.bukkit.Location result = command.resolveDestination(sender, args, 3, null, 0f, 0f);
        assertNull(result, "two bare numbers are neither a player name nor a full x/y/z");
        verify(sender).sendMessage(contains("Expected a player name"));
    }
}
