package com.wormhole_xtreme.wormhole.command.handlers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.StargateShape;

/**
 * Turning a gate's custom mode on and off.
 *
 * <p>Custom mode is what lets a gate keep materials of its own rather than following its
 * shape. The command had no test, and two of its refusals are worth holding: a gate with no
 * shape loaded has nothing to base custom data on, and a word that is not true or false is
 * rejected rather than read as false.
 */
class CustomCommandTest
{
    private CommandSender sender;

    @BeforeEach
    void setUp() throws Exception
    {
        final Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, mock(WormholeXTreme.class));

        // Not a player, so the admin node is not asked for.
        sender = mock(CommandSender.class);
        clearGates();
    }

    @AfterEach
    void tearDown()
    {
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

    private static Stargate gate(final String name, final boolean withShape)
    {
        final Stargate s = new Stargate();
        s.setGateName(name);
        // A new gate already carries a default shape, so "no shape" has to be said outright.
        s.setGateShape(withShape ? new StargateShape() : null);
        StargateManager.registerStargate(s);
        return s;
    }

    private boolean run(final String... args)
    {
        return new CustomCommand().execute(sender, args);
    }

    /** The wrong number of arguments is a usage error, and says so by returning false. */
    @Test
    void theWrongArgumentCountIsAUsageError()
    {
        assertFalse(run("custom"), "returning false is what prints the usage line");

        verify(sender).sendMessage(contains("/wormhole custom"));
    }

    /** Naming a gate nobody built is refused. */
    @Test
    void anUnknownGateIsRefused()
    {
        assertTrue(run("custom", "nowhere", "true"));

        verify(sender).sendMessage(contains("Invalid"));
    }

    /** Asked without a value, it reports the gate's current setting and changes nothing. */
    @Test
    void askingWithoutAValueReportsTheCurrentSetting()
    {
        final Stargate s = gate("alpha", true);

        assertTrue(run("custom", "alpha"));

        verify(sender).sendMessage(contains("Stargate is custom: false"));
        assertFalse(s.isGateCustom(), "asking must not change it");
    }

    /** Turning it on for a gate that has a shape does turn it on. */
    @Test
    void turningItOnForAGateWithAShapeWorks()
    {
        final Stargate s = gate("alpha", true);

        assertTrue(run("custom", "alpha", "true"));

        assertTrue(s.isGateCustom(), "custom mode should be on now");
    }

    /**
     * A gate whose shape is not loaded is refused rather than half-set.
     *
     * <p>Custom mode copies from the shape, so without one there is nothing to copy and the
     * gate would be left claiming to be custom with nothing behind it.
     */
    @Test
    void aGateWithNoShapeLoadedIsRefused()
    {
        final Stargate s = gate("shapeless", false);

        assertTrue(run("custom", "shapeless", "true"));

        verify(sender).sendMessage(contains("No gate shape to base custom data off of"));
        assertFalse(s.isGateCustom(), "and it is not turned on regardless");
    }

    /**
     * A word that is neither true nor false is refused, not read as false.
     *
     * <p>Reading it as false would quietly turn custom mode off for an admin who mistyped
     * the word they meant to turn it on with.
     */
    @Test
    void aWordThatIsNotTrueOrFalseIsRefused()
    {
        final Stargate s = gate("alpha", true);

        assertTrue(run("custom", "alpha", "yes"));

        verify(sender).sendMessage(contains("Invalid boolean option: yes"));
        assertFalse(s.isGateCustom());
    }

    /** The -all form reaches every gate that has a shape. */
    @Test
    void allReachesEveryGateWithAShape()
    {
        final Stargate one = gate("alpha", true);
        final Stargate two = gate("beta", true);

        assertTrue(run("custom", "-all", "true"));

        assertTrue(one.isGateCustom(), "alpha");
        assertTrue(two.isGateCustom(), "beta");
        verify(sender, never()).sendMessage(contains("Invalid"));
    }
}
