package com.wormhole_xtreme.wormhole.command.handlers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import java.util.ArrayList;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * Turning a gate's redstone wiring on and off, and reading it back.
 *
 * <p>Nothing covered this command at all, which is the gap #45 describes as the command
 * layer's shape problem rather than a general one.
 *
 * <p>What makes it worth covering rather than just reshaping: the two-argument form reports
 * the current setting and the three-argument form changes it, and both answer on the same
 * message header. A reshape that crossed those two would leave a command that says it has
 * set something and has not.
 */
class RedstoneCommandTest
{
    private CommandSender sender;

    @BeforeEach
    void setUp() throws Exception
    {
        final Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, mock(WormholeXTreme.class));

        // Not a player, so the admin node is not asked for -- the permission branch is its
        // own concern and is covered where the permission itself is.
        sender = mock(CommandSender.class);
        clearGates();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        clearGates();
        final Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, null);
    }

    private static void clearGates()
    {
        for (final Stargate s : new ArrayList<>(StargateManager.getAllGates()))
        {
            if (s != null)
            {
                StargateManager.removeStargate(s);
            }
        }
    }

    private static Stargate gate(final String name, final boolean redstonePowered)
    {
        final Stargate g = new Stargate();
        g.setGateName(name);
        g.setGateRedstonePowered(redstonePowered);
        StargateManager.registerStargate(g);
        return g;
    }

    private boolean run(final String... args)
    {
        return new RedstoneCommand().execute(sender, args);
    }

    /** Too few or too many words gets the usage, and false so the caller prints it again. */
    @Test
    void theWrongNumberOfArgumentsIsAUsageError()
    {
        assertFalse(run("redstone"));
        assertFalse(run("redstone", "alpha", "true", "extra"));

        verify(sender, org.mockito.Mockito.atLeastOnce())
            .sendMessage(contains("/wormhole redstone"));
    }

    /** A gate nobody built is refused, and nothing is changed on the way past. */
    @Test
    void anUnknownGateIsRefused()
    {
        assertTrue(run("redstone", "nowhere", "true"));

        verify(sender).sendMessage(contains("Invalid gate target"));
    }

    /** Two words reads the setting back without touching it. */
    @Test
    void twoArgumentsReportRatherThanSet()
    {
        final Stargate g = gate("alpha", false);

        assertTrue(run("redstone", "alpha"));

        verify(sender).sendMessage(contains("is redstone powered: false"));
        assertFalse(g.isGateRedstonePowered(), "reading must not turn it on");
    }

    /** Three words sets it, and says what it set. */
    @Test
    void threeArgumentsSetTheWiring()
    {
        final Stargate g = gate("alpha", false);

        assertTrue(run("redstone", "alpha", "true"));

        assertTrue(g.isGateRedstonePowered(), "the gate is wired now");
        verify(sender).sendMessage(contains("is redstone powered: true"));
    }

    /** And off again, so the setting is not one-way. */
    @Test
    void theWiringCanBeTurnedOffAgain()
    {
        final Stargate g = gate("alpha", true);

        assertTrue(run("redstone", "alpha", "false"));

        assertFalse(g.isGateRedstonePowered(), "the gate is unwired now");
        verify(sender).sendMessage(contains("is redstone powered: false"));
    }

    /**
     * A word that is not a boolean changes nothing.
     *
     * <p>{@code Boolean.parseBoolean} answers false for anything it does not recognise, so
     * without the check "yes" would quietly turn the wiring off and report success.
     */
    @Test
    void aWordThatIsNotABooleanIsRefusedRatherThanReadAsFalse()
    {
        final Stargate g = gate("alpha", true);

        assertTrue(run("redstone", "alpha", "yes"));

        assertTrue(g.isGateRedstonePowered(), "still wired: nothing was set");
        verify(sender).sendMessage(contains("Invalid boolean option: yes"));
        verify(sender, never()).sendMessage(contains("is redstone powered"));
    }
}
