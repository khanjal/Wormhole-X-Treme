package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The bounds on the arrival splash.
 *
 * <p>This is the one drawing in the plugin that makes the client's world less solid than the
 * real one: for as long as a client believes it is underwater it predicts swimming, and the
 * server does not agree. So its length is worth holding to something short, and above nothing.
 */
class ArrivalSplashLengthTest
{
    /** Long enough that a client would start acting on it. */
    private static final long TOO_LONG_TO_GO_UNNOTICED = 40L;

    @Test
    void theSplashIsShownButShortEnoughNotToBeArguedWith()
    {
        final long ticks = ArrivalSplash.SPLASH_TICKS;
        assertTrue(ticks > 0L, "a splash of " + ticks + " ticks is never seen");
        assertTrue(ticks < TOO_LONG_TO_GO_UNNOTICED,
            "at " + ticks + " ticks the client has time to predict swimming, and the server "
                + "will pull the traveller back out of it");
    }
}
