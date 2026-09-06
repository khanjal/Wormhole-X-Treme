package com.wormhole_xtreme.wormhole.command;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;

import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

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
        final Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, plugin);

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

        verify(player).sendMessage(ConfigManager.MessageStrings.targetIsActive.toString());
        assertSame(other, StargateManager.removeActivatorForStargate(there),
            "the activator mapping should survive: forcing past a live connection would drop it");
    }

    /** The command only claims argument counts it can actually serve. */
    @Test
    void theCommandDeclinesArgumentCountsItDoesNotHandle()
    {
        assertNotNull(new Dial());
        org.junit.jupiter.api.Assertions.assertFalse(dial(),
            "no arguments is not a dial this command knows");
        org.junit.jupiter.api.Assertions.assertFalse(dial("a", "b", "c"),
            "three arguments is past what dial accepts");
    }
}
