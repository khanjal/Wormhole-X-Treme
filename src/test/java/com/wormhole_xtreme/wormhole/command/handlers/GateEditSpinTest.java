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
import com.wormhole_xtreme.wormhole.logic.DialSpinPattern;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateDBManager;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * {@code /wormhole gate edit <gate> spin} (#366): a gate picks its own ring pattern, and
 * {@code default} hands it back to its group and the server.
 */
class GateEditSpinTest
{
    private Player sender;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        sender = mock(Player.class);
        when(sender.getName()).thenReturn("admin");
        when(sender.isOp()).thenReturn(true);
        clearGates();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        clearGates();
        PluginTestSupport.remove();
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

    private void run(final String... args)
    {
        new GateEditCommand().execute(sender, args);
    }

    /** A pattern is set and saved; {@code default} clears it and saves that too. */
    @Test
    void aPatternIsSetAndDefaultClearsIt()
    {
        final Stargate alpha = gate("alpha");
        try (MockedStatic<StargateDBManager> db = mockStatic(StargateDBManager.class))
        {
            run("gate", "edit", "alpha", "spin", "Pegasus");
            assertEquals(DialSpinPattern.PEGASUS, alpha.getGateDialSpin());

            run("gate", "edit", "alpha", "spin", "default");
            assertNull(alpha.getGateDialSpin(), "default follows the group and the server again");
            db.verify(() -> StargateDBManager.saveStargate(alpha), times(2));
        }
    }

    /** A name no pattern answers to is refused, the gate keeps its pattern, and the patterns are listed. */
    @Test
    void anUnknownPatternIsRefusedAndNothingIsSaved()
    {
        final Stargate alpha = gate("alpha");
        alpha.setGateDialSpin(DialSpinPattern.LAP);
        try (MockedStatic<StargateDBManager> db = mockStatic(StargateDBManager.class))
        {
            run("gate", "edit", "alpha", "spin", "sideways");

            assertEquals(DialSpinPattern.LAP, alpha.getGateDialSpin());
            db.verify(() -> StargateDBManager.saveStargate(alpha), never());
        }
        verify(sender).sendMessage(contains("pegasus"));
    }

    /** Asked with no value, it says what the gate dials with and where that comes from. */
    @Test
    void noValueSaysWhatItDialsWith()
    {
        gate("alpha").setGateDialSpin(DialSpinPattern.CHASE);

        run("gate", "edit", "alpha", "spin");

        verify(sender).sendMessage(contains("alpha dials with chase"));
    }

    /** Tab completion offers every pattern and default. */
    @Test
    void theValuesOfferEveryPatternAndDefault()
    {
        for (final DialSpinPattern pattern : DialSpinPattern.values())
        {
            assertTrue(GateEditCommand.spinNames().contains(pattern.name().toLowerCase(java.util.Locale.ROOT)));
        }
        assertTrue(GateEditCommand.spinNames().contains("default"));
    }
}
