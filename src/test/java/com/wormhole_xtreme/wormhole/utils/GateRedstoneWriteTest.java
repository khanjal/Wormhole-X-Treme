package com.wormhole_xtreme.wormhole.utils;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * The window that tells the redstone listener a change was the plugin's own doing.
 *
 * <p>Worth pinning on its own because the failure is silent in both directions. A window that
 * closes too early puts the double-dial back; a window that never closes leaves a gate deaf to
 * redstone for the rest of the server's life, with nothing in the log to say why.
 */
class GateRedstoneWriteTest
{
    @Test
    void nothingIsInProgressBeforeAnythingBegins()
    {
        assertFalse(GateRedstoneWrite.inProgress());
    }

    @Test
    void aWindowIsOpenBetweenBeginAndEnd()
    {
        GateRedstoneWrite.begin();
        try
        {
            assertTrue(GateRedstoneWrite.inProgress());
        }
        finally
        {
            GateRedstoneWrite.end();
        }
        assertFalse(GateRedstoneWrite.inProgress(), "the window must close, or the gate stays deaf for good");
    }

    /**
     * An inner window closing does not open the gate's ears again.
     *
     * <p>Opening a gate switches its dial lever and then its [RA] output, and either write can
     * end up nested inside the other's window as the calls grow. A plain boolean would have the
     * inner {@code end()} clear the guard while the outer write was still going -- which is
     * exactly the moment the listener must not be listening.
     */
    @Test
    void anInnerWindowClosingLeavesTheOuterOneOpen()
    {
        GateRedstoneWrite.begin();
        GateRedstoneWrite.begin();
        GateRedstoneWrite.end();
        assertTrue(GateRedstoneWrite.inProgress(), "the outer write is still in progress");
        GateRedstoneWrite.end();
        assertFalse(GateRedstoneWrite.inProgress());
    }

    /**
     * An unmatched close cannot bank a credit against the next real one.
     *
     * <p>The floor is what makes the depth safe to reason about. Without it a stray
     * {@code end()} would leave the count at -1, and the next genuine {@code begin()} would
     * only bring it back to zero -- so a gate really would be switching its own lever with the
     * listener wide open, and the double dial would be back with nothing to show why.
     */
    @Test
    void anUnmatchedCloseDoesNotSwallowTheNextWindow()
    {
        GateRedstoneWrite.end();
        assertFalse(GateRedstoneWrite.inProgress());

        GateRedstoneWrite.begin();
        try
        {
            assertTrue(GateRedstoneWrite.inProgress(), "the stray close must not have been banked");
        }
        finally
        {
            GateRedstoneWrite.end();
        }
        assertFalse(GateRedstoneWrite.inProgress());
    }
}
