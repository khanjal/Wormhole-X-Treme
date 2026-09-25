package com.wormhole_xtreme.wormhole.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.PluginTestSupport;

/**
 * What {@code /wormhole dial} tells a player when it will not connect them.
 *
 * <p>{@code doDial} is a guard pyramid five levels deep and had no test. Each rejection
 * sends a different message, and the message is the whole of what a player sees, so the
 * mapping from situation to message is the behaviour worth pinning before the shape of the
 * method changes.
 */
class DialCommandTest
{
    private Player player;
    private Command command;

    @BeforeEach
    void setUp() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        PluginTestSupport.install(plugin);

        player = mock(Player.class);
        when(player.getName()).thenReturn("dialler");
        command = mock(Command.class);
        clearGates();
    }

    @AfterEach
    void tearDown()
    {
        StargateManager.removeActivatedStargate(player);
        clearGates();
        PluginTestSupport.forgetAllGates();
    }

    private static void clearGates()
    {
        for (final Stargate s : new java.util.ArrayList<Stargate>(StargateManager.getAllGates()))
        {
            if (s != null)
            {
                StargateManager.removeStargate(s);
            }
        }
    }

    private static Stargate gate(final String name)
    {
        final Stargate s = new Stargate();
        s.setGateName(name);
        StargateManager.registerStargate(s);
        return s;
    }

    private boolean dial(final String... args)
    {
        return new Dial().onCommand(player, command, "dial", args);
    }

    /** Dialling without having lit a gate first is the commonest mistake, and it says so. */
    @Test
    void diallingWithNoActivatedGateSaysTheGateIsNotActive()
    {
        gate("far");

        dial("far");

        verify(player).sendMessage(contains("No gate activated"));
    }

    /** A gate cannot dial itself, and the attempt clears the activation rather than hanging. */
    @Test
    void aGateCannotDialItself()
    {
        final Stargate here = gate("here");
        StargateManager.addActivatedStargate(player, here);

        dial("here");

        verify(player).sendMessage(contains("own gate"));
        assertNull(StargateManager.removeActivatedStargate(player),
            "the activation should have been spent by the refusal");
    }

    /** A name nobody has built is refused, and the lit gate is put out again. */
    @Test
    void anUnknownTargetIsRefusedAndTheGateIsClosed()
    {
        final Stargate here = gate("here");
        StargateManager.addActivatedStargate(player, here);

        dial("nowhere");

        verify(player).sendMessage(contains("Invalid"));
        assertNull(StargateManager.removeActivatedStargate(player));
    }

    /**
     * Two gates on different networks cannot see one another, and the refusal says which
     * problem it is rather than reusing the plain invalid-target line alone.
     */
    @Test
    void aTargetOnAnotherNetworkIsRefusedWithTheReason()
    {
        final Stargate here = gate("here");
        final Stargate there = gate("there");
        there.setGateNetwork(StargateManager.addStargateNetwork("other"));
        StargateManager.addActivatedStargate(player, here);

        dial("there");

        verify(player).sendMessage(contains("Not on same network"));
    }

    /** A remote iris that is closed, with no IDC offered, stops the dial and explains why. */
    @Test
    void aClosedRemoteIrisStopsTheDialUnlessTheIdcIsGiven()
    {
        final Stargate here = gate("here");
        final Stargate there = gate("there");
        there.setGateIrisDeactivationCode("secret");
        there.setGateIrisActive(true);
        StargateManager.addActivatedStargate(player, here);

        dial("there");

        verify(player).sendMessage(contains("Remote Iris is active"));
    }

    /** The right IDC opens the far iris, and the player is told it was accepted. */
    @Test
    void theRightIdcOpensTheRemoteIris()
    {
        final Stargate here = gate("here");
        final Stargate there = gate("there");
        there.setGateIrisDeactivationCode("secret");
        there.setGateIrisActive(true);
        StargateManager.addActivatedStargate(player, here);

        dial("there", "secret");

        verify(player).sendMessage(contains("IDC accepted"));
    }

    /**
     * A dial refused at the far iris puts the near gate's iris back to its default.
     *
     * <p>Dialling opens the near iris before it looks at the far one. Left open, the gate sat
     * idle against its default, and the file keeps only the default: a floor gate came back
     * after a restart saying shut over an empty opening.
     */
    @Test
    void aDialRefusedAtTheFarIrisShutsTheNearIrisAgain()
    {
        final Stargate here = gate("here");
        here.setGateIrisDeactivationCode("mine");
        here.setGateIrisActive(true);
        here.setGateIrisDefaultActive(true);
        final Stargate there = gate("there");
        there.setGateIrisDeactivationCode("secret");
        there.setGateIrisActive(true);
        StargateManager.addActivatedStargate(player, here);

        dial("there");

        verify(player).sendMessage(contains("Remote Iris is active"));
        assertTrue(here.isGateIrisActive(), "the near iris was left open against its shut default");
    }

    /**
     * A dial that fails after the right IDC opened the far iris shuts that iris again.
     *
     * <p>The far gate never opened, so no shutdown of its own would put the iris back, and
     * it sat idle open against its shut default -- the state a restart turns into a floor
     * gate saying shut over an empty opening.
     */
    @Test
    void aDialThatFailsAfterTheIdcShutsTheFarIrisAgain()
    {
        final Stargate here = gateThatCannotConnect("here");
        final Stargate there = gate("there");
        there.setGateIrisDeactivationCode("secret");
        there.setGateIrisActive(true);
        there.setGateIrisDefaultActive(true);
        StargateManager.addActivatedStargate(player, here);

        dial("there", "secret");

        verify(player).sendMessage(contains("IDC accepted"));
        verify(player).sendMessage(ConfigManager.MessageStrings.TARGET_IS_ACTIVE.toString());
        assertTrue(there.isGateIrisActive(), "the far iris was left open against its shut default");
    }

    /**
     * A far gate busy dialling somewhere else gets back the iris the IDC opened.
     *
     * <p>It has a target but no wormhole yet, so the dial is refused as in use, and nothing of
     * its own is about to put the iris back.
     */
    @Test
    void aFarGateBusyDiallingGetsItsIrisBack()
    {
        final Stargate here = gateThatCannotConnect("here");
        final Stargate elsewhere = gate("elsewhere");
        final Stargate there = spy(new Stargate());
        there.setGateName("there");
        StargateManager.registerStargate(there);
        doReturn(elsewhere).when(there).getGateTarget();
        there.setGateIrisDeactivationCode("secret");
        there.setGateIrisActive(true);
        there.setGateIrisDefaultActive(true);
        StargateManager.addActivatedStargate(player, here);

        dial("there", "secret");

        verify(player).sendMessage(ConfigManager.MessageStrings.TARGET_IS_ACTIVE.toString());
        assertTrue(there.isGateIrisActive(), "the far iris was left open against its shut default");
    }

    /**
     * An open far gate keeps its iris as its own journey has it.
     *
     * <p>Its shutdown puts the iris back; shutting it now would close the wormhole somebody
     * else is travelling through.
     */
    @Test
    void anOpenFarGateKeepsItsIrisOpen()
    {
        final Stargate here = gateThatCannotConnect("here");
        final Stargate there = gate("there");
        there.setGateIrisDeactivationCode("secret");
        there.setGateIrisDefaultActive(true);
        there.setGateIrisActive(false);
        there.setGateActive(true);
        StargateManager.addActivatedStargate(player, here);

        dial("there");

        verify(player).sendMessage(ConfigManager.MessageStrings.TARGET_IS_ACTIVE.toString());
        assertFalse(there.isGateIrisActive(), "a journey through the far gate was shut on");
    }

    /** A registered gate whose dial always fails, so the refusal paths can be reached. */
    private static Stargate gateThatCannotConnect(final String name)
    {
        final Stargate s = spy(new Stargate());
        s.setGateName(name);
        StargateManager.registerStargate(s);
        doReturn(Boolean.FALSE).when(s).dialStargate(any(Stargate.class), anyBoolean());
        return s;
    }

    /**
     * A target someone else is already connected to is reported, not forced.
     *
     * <p>The recovery path exists to clear activator mappings left behind by a gate that never
     * finished closing. Running it against a live connection would cut that player off, so a
     * target in use has to stop the retry before it starts.
     */
    @Test
    void aTargetSomeoneElseIsUsingIsReportedRatherThanForced()
    {
        final Stargate here = gate("here");
        final Stargate there = gate("there");
        there.setGateActive(true);
        final Player other = mock(Player.class);
        StargateManager.addActivatedStargate(other, there);
        StargateManager.addActivatedStargate(player, here);

        dial("there");

        verify(player).sendMessage(ConfigManager.MessageStrings.TARGET_IS_ACTIVE.toString());
        assertSame(other, StargateManager.removeActivatorForStargate(there),
            "the activator mapping should survive: forcing past a live connection would drop it");
    }

    /**
     * An argument count dial cannot serve gets its own one-line usage (#325), and true, so Bukkit
     * does not follow it with plugin.yml's usage block.
     */
    @Test
    void anArgumentCountItCannotServeGetsItsUsage()
    {
        assertNotNull(new Dial());
        org.junit.jupiter.api.Assertions.assertTrue(dial(), "no arguments: answered here");
        org.junit.jupiter.api.Assertions.assertTrue(dial("a", "b", "c"), "three arguments: answered here");
        verify(player, times(2)).sendMessage(org.mockito.ArgumentMatchers.<String>argThat((String s) -> com.wormhole_xtreme.wormhole.utils.ChatText.plain(s).contains("Usage: /dial <gate> [idc]")));
    }
}
