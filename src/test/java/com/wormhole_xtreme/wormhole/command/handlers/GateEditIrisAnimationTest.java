package com.wormhole_xtreme.wormhole.command.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateDBManager;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * {@code /wormhole gate edit <gate> iris-animation} (#427): a gate picks how its own iris crosses,
 * and {@code default} hands it back to its group and the server.
 */
class GateEditIrisAnimationTest
{
    private Player sender;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        sender = mock(Player.class);
        when(sender.getName()).thenReturn("admin");
        when(sender.isOp()).thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        for (final Stargate s : new java.util.ArrayList<Stargate>(StargateManager.getAllGates()))
        {
            if (s != null)
            {
                StargateManager.removeStargate(s);
            }
        }
        PluginTestSupport.remove();
    }

    private static Stargate gate(final String name)
    {
        final Stargate s = new Stargate();
        s.setGateName(name);
        StargateManager.registerStargate(s);
        return s;
    }

    private void run(final String... args)
    {
        new GateEditCommand().execute(sender, args);
    }

    /** An animation is set, whatever its capitals, and saved; default clears it and saves that too. */
    @Test
    void anAnimationIsSetAndDefaultClearsIt()
    {
        final Stargate alpha = gate("alpha");
        try (MockedStatic<StargateDBManager> db = mockStatic(StargateDBManager.class))
        {
            run("gate", "edit", "alpha", "iris-animation", "Spiral");
            assertEquals("spiral", alpha.getGateIrisAnimation());

            run("gate", "edit", "alpha", "iris-animation", "default");
            assertNull(alpha.getGateIrisAnimation(), "default follows the group and the server again");
            db.verify(() -> StargateDBManager.saveStargate(alpha), times(2));
        }
    }

    /** A name no animation answers to is refused, nothing is saved, and the animations are listed. */
    @Test
    void anUnknownAnimationIsRefused()
    {
        final Stargate alpha = gate("alpha");
        alpha.setGateIrisAnimation("rows");
        try (MockedStatic<StargateDBManager> db = mockStatic(StargateDBManager.class))
        {
            run("gate", "edit", "alpha", "iris-animation", "sideways");

            assertEquals("rows", alpha.getGateIrisAnimation());
            db.verify(() -> StargateDBManager.saveStargate(alpha), never());
        }
        verify(sender).sendMessage(contains("instant"));
    }

    /** Asked with no value, it says how the iris crosses and where that comes from. */
    @Test
    void noValueSaysHowItCrosses()
    {
        gate("alpha").setGateIrisAnimation("columns");

        run("gate", "edit", "alpha", "iris-animation");

        verify(sender).sendMessage(contains("alpha's iris crosses as columns"));
    }

    /** Tab completion offers the four styles, instant and default. */
    @Test
    void theValuesOfferEveryAnimationAndDefault()
    {
        for (final String name : new String[] { "sweep", "spiral", "rows", "columns", "instant", "default" })
        {
            assertTrue(GateEditCommand.irisAnimationNames().contains(name), name);
        }
    }
}
