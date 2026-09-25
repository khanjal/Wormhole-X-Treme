package com.wormhole_xtreme.wormhole.command.handlers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateDBManager;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.PluginTestSupport;

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

    // Every test mocks it: a change is saved at once, and the real save writes gate files
    // into the working directory.
    private MockedStatic<StargateDBManager> db;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));

        // Not a player, so the admin node is not asked for -- the permission branch is its
        // own concern and is covered where the permission itself is.
        sender = mock(CommandSender.class);
        clearGates();
        db = mockStatic(StargateDBManager.class);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        db.close();
        clearGates();
        PluginTestSupport.remove();
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

    /** The gate went to disk once, now, rather than waiting for shutdown. */
    private void assertSaved(final Stargate gate)
    {
        db.verify(() -> StargateDBManager.saveStargate(gate));
    }

    /** Nothing changed, so nothing was written. */
    private void assertNothingSaved()
    {
        db.verify(() -> StargateDBManager.saveStargate(any()), never());
    }

    /** Too few or too many words gets the usage, and true, since it has already said so (#325). */
    @Test
    void theWrongNumberOfArgumentsIsAUsageError()
    {
        org.junit.jupiter.api.Assertions.assertTrue(run("redstone"));
        org.junit.jupiter.api.Assertions.assertTrue(run("redstone", "alpha", "true", "extra"));

        verify(sender, org.mockito.Mockito.atLeastOnce())
            .sendMessage(contains("/wormhole redstone"));
    }

    /** A gate nobody built is refused, and nothing is changed on the way past. */
    @Test
    void anUnknownGateIsRefused()
    {
        assertTrue(run("redstone", "nowhere", "true"));

        verify(sender).sendMessage(contains("Invalid gate target"));
        assertNothingSaved();
    }

    /** Two words reads the setting back without touching it. */
    @Test
    void twoArgumentsReportRatherThanSet()
    {
        final Stargate g = gate("alpha", false);

        assertTrue(run("redstone", "alpha"));

        verify(sender).sendMessage(contains("is redstone powered: false"));
        assertFalse(g.isGateRedstonePowered(), "reading must not turn it on");
        assertNothingSaved();
    }

    /** Three words sets it, and says what it set. */
    @Test
    void threeArgumentsSetTheWiring()
    {
        final Stargate g = gate("alpha", false);

        assertTrue(run("redstone", "alpha", "true"));

        assertTrue(g.isGateRedstonePowered(), "the gate is wired now");
        assertSaved(g);
        verify(sender).sendMessage(contains("is redstone powered: true"));
    }

    /** And off again, so the setting is not one-way. */
    @Test
    void theWiringCanBeTurnedOffAgain()
    {
        final Stargate g = gate("alpha", true);

        assertTrue(run("redstone", "alpha", "false"));

        assertFalse(g.isGateRedstonePowered(), "the gate is unwired now");
        assertSaved(g);
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
        assertNothingSaved();
        verify(sender).sendMessage(contains("Invalid boolean option: yes"));
        verify(sender, never()).sendMessage(contains("is redstone powered"));
    }
}
