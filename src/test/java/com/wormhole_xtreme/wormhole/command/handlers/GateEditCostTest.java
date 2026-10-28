package com.wormhole_xtreme.wormhole.command.handlers;

import static com.wormhole_xtreme.wormhole.utils.ChatMatchers.plainContains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.wormhole_xtreme.wormhole.model.StargateShape;

/**
 * {@code /wormhole gate edit <gate> cost} (#632): a gate sets what a trip through it costs, and
 * {@code default} hands it back to its shape and the server.
 */
class GateEditCostTest
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

    /** An amount is set and saved, 0 included; default clears it and saves that too. */
    @Test
    void anAmountIsSetAndDefaultClearsIt()
    {
        final Stargate alpha = gate("alpha");
        try (MockedStatic<StargateDBManager> db = mockStatic(StargateDBManager.class))
        {
            run("gate", "edit", "alpha", "cost", "2.5");
            assertEquals(2.5, alpha.getGateUseCost());

            run("gate", "edit", "alpha", "cost", "0");
            assertEquals(0.0, alpha.getGateUseCost(), "free is a cost, not unset");

            run("gate", "edit", "alpha", "cost", "Default");
            assertNull(alpha.getGateUseCost(), "default follows the shape and the server again");
            db.verify(() -> StargateDBManager.saveStargate(alpha), times(3));
        }
    }

    /** What is not an amount is refused and changes nothing, and nothing is saved. */
    @Test
    void whatIsNotAnAmountIsRefused()
    {
        final Stargate alpha = gate("alpha");
        alpha.setGateUseCost(4.0);
        try (MockedStatic<StargateDBManager> db = mockStatic(StargateDBManager.class))
        {
            run("gate", "edit", "alpha", "cost", "lots");
            run("gate", "edit", "alpha", "cost", "-1");
            run("gate", "edit", "alpha", "cost", "NaN");
            run("gate", "edit", "alpha", "cost", "Infinity");
            run("gate", "edit", "alpha", "cost", "1e3");
            run("gate", "edit", "alpha", "cost", "2f");

            assertEquals(4.0, alpha.getGateUseCost());
            verify(sender, times(6)).sendMessage(plainContains("is not an amount"));
            db.verify(() -> StargateDBManager.saveStargate(alpha), never());
        }
    }

    /** With no value it says what the cost is and where it comes from. */
    @Test
    void withNoValueItSaysTheCostAndWhereItComesFrom()
    {
        ConfigTestSupport.set(ConfigKeys.ECONOMY_USE_COST, 3.0);
        final Stargate alpha = gate("alpha");

        run("gate", "edit", "alpha", "cost");
        verify(sender).sendMessage(plainContains("alpha costs 3 a trip (the server default)."));

        final StargateShape shape = new StargateShape();
        shape.setShapeName("Grand");
        shape.setShapeUseCost(8.0);
        alpha.setGateShape(shape);
        run("gate", "edit", "alpha", "cost");
        verify(sender).sendMessage(plainContains("alpha costs 8 a trip (from the Grand shape)."));

        alpha.setGateUseCost(1.5);
        run("gate", "edit", "alpha", "cost");
        verify(sender).sendMessage(plainContains("alpha costs 1.5 a trip."));
    }

    /** A gate that is not there is said so. */
    @Test
    void aGateThatIsNotThereIsSaidSo()
    {
        run("gate", "edit", "nowhere", "cost", "1");

        verify(sender).sendMessage(plainContains("nowhere"));
    }
}
