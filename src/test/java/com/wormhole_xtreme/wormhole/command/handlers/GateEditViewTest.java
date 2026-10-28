package com.wormhole_xtreme.wormhole.command.handlers;

import static com.wormhole_xtreme.wormhole.utils.ChatMatchers.plainContains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateDBManager;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * {@code /wormhole gate edit <gate> view} (#682): a gate picks what it shows once open, and
 * {@code default} hands it back to {@code gate-view}.
 */
class GateEditViewTest
{
    private Player sender;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        ConfigTestSupport.clear();
        sender = mock(Player.class);
        when(sender.getName()).thenReturn("admin");
        when(sender.isOp()).thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        for (final Stargate s : new ArrayList<>(StargateManager.getAllGates()))
        {
            if (s != null)
            {
                StargateManager.removeStargate(s);
            }
        }
        ConfigTestSupport.clear();
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

    /** A level is set, whatever its capitals, and saved; default clears it and saves that too. */
    @Test
    void aLevelIsSetAndDefaultClearsIt()
    {
        final Stargate alpha = gate("alpha");
        try (MockedStatic<StargateDBManager> db = mockStatic(StargateDBManager.class))
        {
            run("gate", "edit", "alpha", "view", "Behind");
            assertEquals("behind", alpha.getGateView());

            run("gate", "edit", "alpha", "view", "default");
            assertNull(alpha.getGateView(), "default follows gate-view again");
            db.verify(() -> StargateDBManager.saveStargate(alpha), times(2));
        }
    }

    /** A name no level answers to is refused, nothing is saved, and the levels are listed. */
    @Test
    void anUnknownLevelIsRefused()
    {
        final Stargate alpha = gate("alpha");
        alpha.setGateView("open");
        try (MockedStatic<StargateDBManager> db = mockStatic(StargateDBManager.class))
        {
            run("gate", "edit", "alpha", "view", "sideways");

            assertEquals("open", alpha.getGateView());
            db.verify(() -> StargateDBManager.saveStargate(alpha), never());
        }
        verify(sender).sendMessage(plainContains("behind"));
    }

    /** Asked with no value, it says what the gate shows and where that comes from. */
    @Test
    void noValueSaysWhatItShows()
    {
        gate("alpha").setGateView("open");

        run("gate", "edit", "alpha", "view");

        verify(sender).sendMessage(plainContains("alpha's view is open"));
    }

    /**
     * A gate's own level wins over the server's whichever way they differ: horizon over a server at
     * open as much as open over a server at horizon.
     */
    @Test
    void aGatesOwnLevelWinsOverTheServersEitherWay()
    {
        final Stargate alpha = gate("alpha");

        ConfigTestSupport.set(ConfigKeys.GATE_VIEW, "open");
        alpha.setGateView("horizon");
        assertEquals("horizon", alpha.getEffectiveView(), "its own horizon, though the server is at open");

        ConfigTestSupport.set(ConfigKeys.GATE_VIEW, "horizon");
        alpha.setGateView("open");
        assertEquals("open", alpha.getEffectiveView(), "its own open, though the server is at horizon");

        alpha.setGateView(null);
        assertEquals("horizon", alpha.getEffectiveView(), "none of its own: the server's");
    }

    /** Tab completion offers the three levels and default. */
    @Test
    void theValuesOfferEveryLevelAndDefault()
    {
        for (final String name : new String[] { "horizon", "behind", "open", "default" })
        {
            assertTrue(GateEditCommand.viewNames().contains(name), name);
        }
    }
}
