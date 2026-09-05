package com.wormhole_xtreme.wormhole.model.beam;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/**
 * Active and frozen are deliberately two separate states, not one.
 *
 * <p>The envelope runs with the traveller free to move, before {@link BeamAnimation} ever
 * calls {@link BeamFreeze#freeze}, but a second beam still must not be allowed to start on
 * top of the first during that window. {@link BeamFreeze#markActive} exists so the
 * already-beaming guard has something true for the whole sequence to check, not just for the
 * frozen tail of it -- these tests pin that the two states really are independent, and that
 * {@link BeamFreeze#clear} is the one thing that resets both together.
 */
class BeamFreezeTest
{
    private static Player somePlayer()
    {
        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return player;
    }

    @Test
    void aPlayerWhoHasNeverBeamedIsNeitherActiveNorFrozen()
    {
        final Player player = somePlayer();
        assertFalse(BeamFreeze.isActive(player));
        assertFalse(BeamFreeze.isFrozen(player));
    }

    @Test
    void markingActiveDoesNotAlsoFreeze()
    {
        // This is the envelope: the sequence has started (the already-beaming guard must
        // now refuse a second one), but the traveller is still free to walk around.
        final Player player = somePlayer();
        BeamFreeze.markActive(player);
        try
        {
            assertTrue(BeamFreeze.isActive(player), "a started sequence must block a second one");
            assertFalse(BeamFreeze.isFrozen(player), "the envelope must not lock position");
        }
        finally
        {
            BeamFreeze.clear(player);
        }
    }

    @Test
    void freezingAPlayerWhoIsNotYetActiveStillFreezesThem()
    {
        // BeamAnimation always calls markActive before freeze, but freeze itself does not
        // depend on it -- pinned separately so the two methods stay decoupled.
        final Player player = somePlayer();
        BeamFreeze.freeze(player);
        try
        {
            assertTrue(BeamFreeze.isFrozen(player));
        }
        finally
        {
            BeamFreeze.clear(player);
        }
    }

    @Test
    void clearResetsBothStatesTogether()
    {
        final Player player = somePlayer();
        BeamFreeze.markActive(player);
        BeamFreeze.freeze(player);

        BeamFreeze.clear(player);

        assertFalse(BeamFreeze.isActive(player),
            "a stale active flag would refuse this player every future beam for good");
        assertFalse(BeamFreeze.isFrozen(player));
    }

    @Test
    void twoDifferentPlayersDoNotShareEitherState()
    {
        final Player first = somePlayer();
        final Player second = somePlayer();
        BeamFreeze.markActive(first);
        BeamFreeze.freeze(first);
        try
        {
            assertFalse(BeamFreeze.isActive(second));
            assertFalse(BeamFreeze.isFrozen(second));
        }
        finally
        {
            BeamFreeze.clear(first);
        }
    }
}
