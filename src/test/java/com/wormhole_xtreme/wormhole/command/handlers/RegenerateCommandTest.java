package com.wormhole_xtreme.wormhole.command.handlers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

/**
 * The part of {@code /wormhole gate regenerate -all} that decides whether a gate's exit
 * actually needed fixing.
 *
 * <p>{@code recomputeGatePlayerTeleportLocation()} overwrites the stored exit on every gate
 * it can, whether or not the new value differs from the old one -- a healthy gate recomputes
 * to the same answer it already had. Without this comparison, running it across every gate
 * would report "we recomputed N gates" for every server regardless of how many were actually
 * broken, which is not a report worth reading. This is pinned on its own because it needs no
 * live server to get right, and getting it wrong either hides real fixes or invents fake ones.
 */
class RegenerateCommandTest
{
    private static Location at(final World world, final int x, final int y, final int z)
    {
        return new Location(world, x, y, z);
    }

    @Test
    void theSamePositionInTheSameWorldIsNotAMove()
    {
        final World world = mock(World.class);
        assertFalse(RegenerateCommand.exitMoved(at(world, 5, 64, 5), at(world, 5, 64, 5)));
    }

    @Test
    void aDifferentBlockInTheSameWorldIsAMove()
    {
        final World world = mock(World.class);
        assertTrue(RegenerateCommand.exitMoved(at(world, 5, 64, 5), at(world, 6, 64, 5)));
    }

    @Test
    void theSameCoordinatesInADifferentWorldAreStillAMove()
    {
        // Two gates a plugin migration or a world rename could plausibly put in this exact
        // situation -- coordinates that happen to match, in worlds that do not.
        final World first = mock(World.class);
        final World second = mock(World.class);
        assertTrue(RegenerateCommand.exitMoved(at(first, 5, 64, 5), at(second, 5, 64, 5)));
    }

    @Test
    void goingFromNoExitToHavingOneIsAMove()
    {
        final World world = mock(World.class);
        assertTrue(RegenerateCommand.exitMoved(null, at(world, 5, 64, 5)));
    }

    @Test
    void goingFromAnExitToNoneIsAMove()
    {
        // Should not happen in practice -- recompute only ever produces null by failing
        // outright, at which point the caller does not compare at all -- but the comparison
        // itself should not quietly call this "no change" if it is ever asked.
        final World world = mock(World.class);
        assertTrue(RegenerateCommand.exitMoved(at(world, 5, 64, 5), null));
    }

    @Test
    void neverHavingHadAnExitIsNotAMove()
    {
        assertFalse(RegenerateCommand.exitMoved(null, null));
    }
}
