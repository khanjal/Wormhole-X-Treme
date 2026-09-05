package com.wormhole_xtreme.wormhole.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The bounds on the arrival splash.
 *
 * <p>This is the one drawing in the plugin that makes the client's world less solid than the
 * real one: for as long as a client believes it is underwater it predicts swimming, and the
 * server does not agree. Everything else here draws something more solid, or equally solid.
 * So its length is worth holding to something short.
 */
class ArrivalSplashSettingTest
{
    /** Long enough that a client would start acting on it. */
    private static final long TOO_LONG_TO_GO_UNNOTICED = 40L;

    @Test
    void theSplashIsShortEnoughNotToBeArguedWith()
    {
        final long ticks = ConfigManager.getGateArrivalSplashTicks();
        assertTrue(ticks < TOO_LONG_TO_GO_UNNOTICED,
            "at " + ticks + " ticks the client has time to predict swimming, and the server "
                + "will pull the traveller back out of it");
    }

    @Test
    void itCanBeTurnedOffButNeverInverted()
    {
        // Zero is off. A negative would be scheduled as a delay, which Bukkit runs
        // immediately -- putting the block back on the same tick it was drawn, so the effect
        // would silently do nothing rather than being off on purpose.
        assertTrue(ConfigManager.getGateArrivalSplashTicks() >= 0L, "never negative");
    }
}
