package com.wormhole_xtreme.wormhole.command.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.PluginForTests;

/**
 * Setting the two gate timeouts.
 *
 * <p>Activate timeout is how long a gate stays lit waiting to be dialled; shutdown timeout is
 * how long a wormhole stays open. They take different ranges, and the difference matters:
 * 0 is a legal shutdown timeout meaning "never close on its own", and an illegal activate
 * timeout, because a gate that stays lit for no time cannot be dialled at all.
 *
 * <p>Nothing covered either of them.
 */
class TimeoutsCommandTest
{
    private CommandSender sender;
    private int savedActivate;
    private int savedShutdown;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginForTests.install(mock(WormholeXTreme.class));

        // Without this the setters are silent no-ops and every assertion below would pass
        // against a value that was never stored.
        ConfigTestSupport.loadDefaults();
        savedActivate = ConfigManager.getTimeoutActivate();
        savedShutdown = ConfigManager.getTimeoutShutdown();

        // Not a player, so the admin node is not asked for.
        sender = mock(CommandSender.class);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        // The default Setting objects are shared statics, so put back what was altered.
        ConfigManager.setTimeoutActivate(savedActivate);
        ConfigManager.setTimeoutShutdown(savedShutdown);
        ConfigTestSupport.clear();

        PluginForTests.remove();
    }

    private boolean run(final String... args)
    {
        return new TimeoutsCommand().execute(sender, args);
    }

    /** No words at all is a usage error. */
    @Test
    void noArgumentsIsAUsageError()
    {
        assertFalse(run());
    }

    /** A word this command does not own is not its business. */
    @Test
    void anUnrelatedSubcommandIsNotHandled()
    {
        assertFalse(run("something_else", "30"));
    }

    /** An activate timeout inside its range is stored. */
    @Test
    void anActivateTimeoutInRangeIsStored()
    {
        assertTrue(run("activate_timeout", "45"));

        assertEquals(45, ConfigManager.getTimeoutActivate());
        verify(sender).sendMessage(contains("activate_timeout set to: 45"));
    }

    /** Below its floor is refused, and the old value stands. */
    @Test
    void anActivateTimeoutBelowTenIsRefused()
    {
        ConfigManager.setTimeoutActivate(30);

        assertFalse(run("activate_timeout", "5"),
            "returning false is what gets the usage line printed");

        assertEquals(30, ConfigManager.getTimeoutActivate());
        verify(sender).sendMessage(contains("Invalid activate_timeout: 5"));
    }

    /**
     * Zero is legal for shutdown and illegal for activate.
     *
     * <p>A wormhole with a shutdown timeout of 0 never closes on its own. A gate lit for 0
     * seconds could not be dialled at all, so the same number is refused there.
     */
    @Test
    void zeroIsAShutdownTimeoutButNotAnActivateOne()
    {
        assertTrue(run("shutdown_timeout", "0"));
        assertEquals(0, ConfigManager.getTimeoutShutdown());

        assertFalse(run("activate_timeout", "0"));
        verify(sender).sendMessage(contains("Invalid activate_timeout: 0"));
    }

    /** Above the ceiling is refused for both. */
    @Test
    void aTimeoutAboveSixtyIsRefused()
    {
        ConfigManager.setTimeoutShutdown(30);

        assertFalse(run("shutdown_timeout", "61"));

        assertEquals(30, ConfigManager.getTimeoutShutdown());
        verify(sender).sendMessage(contains("Invalid shutdown_timeout: 61"));
    }

    /** A word that is not a number is refused the same way as one out of range. */
    @Test
    void somethingThatIsNotANumberIsRefused()
    {
        ConfigManager.setTimeoutActivate(30);

        assertFalse(run("activate_timeout", "soon"));

        assertEquals(30, ConfigManager.getTimeoutActivate());
        verify(sender).sendMessage(contains("Invalid activate_timeout: soon"));
    }

    /** Asked with no value, it reports the current one and changes nothing. */
    @Test
    void askingReportsTheCurrentValue()
    {
        ConfigManager.setTimeoutActivate(25);

        assertTrue(run("activate_timeout"));

        assertEquals(25, ConfigManager.getTimeoutActivate());
        verify(sender).sendMessage(contains("Current activate_timeout is: 25"));
    }

    /** {@code timeout} is the short spelling of {@code shutdown_timeout}. */
    @Test
    void timeoutIsTheShortSpellingOfShutdownTimeout()
    {
        assertTrue(run("timeout", "42"));

        assertEquals(42, ConfigManager.getTimeoutShutdown());
        verify(sender).sendMessage(contains("shutdown_timeout set to: 42"));
    }

    /** Each timeout quotes its own range back, since they differ. */
    @Test
    void eachTimeoutQuotesItsOwnRange()
    {
        run("activate_timeout");
        verify(sender).sendMessage(contains("between 10 and 60"));

        run("shutdown_timeout");
        verify(sender).sendMessage(contains("between 0 and 60"));
    }

    /** A player without the admin node may not change either. */
    @Test
    void aPlayerWithoutTheConfigNodeIsRefused()
    {
        ConfigManager.setTimeoutActivate(30);
        final Player player = mock(Player.class);
        when(player.getName()).thenReturn("nobody");
        when(player.isOp()).thenReturn(false);
        when(player.hasPermission(anyString())).thenReturn(false);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        assertTrue(new TimeoutsCommand().execute(player, new String[] {"activate_timeout", "45"}));

        verify(player).sendMessage(contains("ermission"));
        assertEquals(30, ConfigManager.getTimeoutActivate(), "and nothing is changed");
    }
}
