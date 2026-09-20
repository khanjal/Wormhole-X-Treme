package com.wormhole_xtreme.wormhole.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * Registering the expansion once, and surviving not being able to.
 *
 * <p>The registration call itself needs a running PlaceholderAPI, which this suite has not
 * got -- so it goes through a seam, the same way {@code GateEvents} substitutes its dispatcher
 * for the same reason. What is pinned here is everything either side of that call: that a
 * second enable does not register a second time, that a refusal is survived rather than
 * treated as success, and that a registrar which throws does not take the plugin down.
 *
 * <p>Registering twice is the one with teeth. PlaceholderAPI keeps expansions in a map keyed
 * by identifier, so a double registration does not throw -- it quietly replaces the first,
 * and the only sign is placeholders answering from an expansion nothing holds a reference to.
 */
class PlaceholderSupportTest
{
    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        PlaceholderSupport.reset();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PlaceholderSupport.setRegistrarForTest(null);
        PlaceholderSupport.reset();
        PluginTestSupport.remove();
    }

    @Test
    void aSuccessfulRegistrationIsRemembered()
    {
        PlaceholderSupport.setRegistrarForTest(() -> true);

        PlaceholderSupport.enablePlaceholders();

        assertTrue(PlaceholderSupport.isRegistered(),
            "a registration that succeeded should be reported as one");
    }

    @Test
    void theExpansionIsNotRegisteredTwice()
    {
        final AtomicInteger attempts = new AtomicInteger();
        PlaceholderSupport.setRegistrarForTest(() ->
        {
            attempts.incrementAndGet();
            return true;
        });

        PlaceholderSupport.enablePlaceholders();
        PlaceholderSupport.enablePlaceholders();
        PlaceholderSupport.enablePlaceholders();

        assertEquals(1, attempts.get(),
            "three enables, one registration: the guard is what stops PlaceholderAPI"
                + " silently replacing the live expansion with a fresh one");
    }

    @Test
    void aRefusalIsNotMistakenForSuccess()
    {
        // PlaceholderAPI can decline, and register() says so by returning false rather than
        // throwing. Reading that as success would leave the plugin claiming placeholders it
        // is not answering.
        PlaceholderSupport.setRegistrarForTest(() -> false);

        PlaceholderSupport.enablePlaceholders();

        assertFalse(PlaceholderSupport.isRegistered(),
            "a refusal should leave the expansion unregistered");
    }

    @Test
    void aRefusalLeavesTheNextEnableFreeToTryAgain()
    {
        // The guard is on having succeeded, not on having tried. A server that reloads after
        // installing PlaceholderAPI should get its placeholders without a full restart.
        final AtomicInteger attempts = new AtomicInteger();
        PlaceholderSupport.setRegistrarForTest(() -> attempts.incrementAndGet() > 1);

        PlaceholderSupport.enablePlaceholders();
        assertFalse(PlaceholderSupport.isRegistered(), "the first attempt was refused");

        PlaceholderSupport.enablePlaceholders();

        assertTrue(PlaceholderSupport.isRegistered(), "and the second was not");
        assertEquals(2, attempts.get());
    }

    @Test
    void aRegistrarThatThrowsIsSurvived()
    {
        // Whatever goes wrong inside PlaceholderAPI, a placeholder that cannot register is
        // not a reason to take the rest of the plugin down with it.
        PlaceholderSupport.setRegistrarForTest(() ->
        {
            throw new IllegalStateException("PlaceholderAPI is not running");
        });

        PlaceholderSupport.enablePlaceholders();

        assertFalse(PlaceholderSupport.isRegistered(),
            "nothing should claim to be registered after the attempt threw");
    }

    @Test
    void resetLetsARegisteredExpansionBeRegisteredAgain()
    {
        // Called on disable, so a reload starts from nothing rather than from a flag left
        // over from the previous life of the plugin.
        PlaceholderSupport.setRegistrarForTest(() -> true);
        PlaceholderSupport.enablePlaceholders();
        assertTrue(PlaceholderSupport.isRegistered());

        PlaceholderSupport.reset();

        assertFalse(PlaceholderSupport.isRegistered(), "reset should clear the flag");
    }
}
