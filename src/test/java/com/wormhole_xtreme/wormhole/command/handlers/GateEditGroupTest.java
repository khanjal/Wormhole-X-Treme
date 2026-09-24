package com.wormhole_xtreme.wormhole.command.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.MaterialGroupRegistry;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateDBManager;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * {@code /wormhole gate edit <gate> group} is saved (#441). It set the group in memory only, so
 * the gate went back to its frame's group on the next restart; {@code -clear} now hands it back
 * to the frame on purpose.
 */
class GateEditGroupTest
{
    private Player sender;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        sender = mock(Player.class);
        when(sender.getName()).thenReturn("admin");
        when(sender.isOp()).thenReturn(true);
        MaterialGroupRegistry.load(Map.of("Atlantis", Map.of("structure", "LAPIS_BLOCK")));
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
        MaterialGroupRegistry.load(null);
        PluginTestSupport.remove();
    }

    /** Choosing a group saves the gate with it; -clear forgets the choice and saves that too. */
    @Test
    void aChosenGroupIsSavedAndDefaultForgetsIt()
    {
        final Stargate alpha = new Stargate();
        alpha.setGateName("alpha");
        StargateManager.registerStargate(alpha);
        try (MockedStatic<StargateDBManager> db = mockStatic(StargateDBManager.class))
        {
            new GateEditCommand().execute(sender, new String[] { "gate", "edit", "alpha", "group", "Atlantis" });
            assertEquals(true, alpha.isGateMaterialGroupChosen(), "chosen, so it is written to the gate's file");
            assertEquals("Atlantis", alpha.getGateMaterialGroup().getName());

            new GateEditCommand().execute(sender, new String[] { "gate", "edit", "alpha", "group", "-clear" });
            assertEquals(false, alpha.isGateMaterialGroupChosen(), "-clear: read off the frame again");

            db.verify(() -> StargateDBManager.saveStargate(alpha), times(2));
        }
    }

    /** A group an admin named "Default" can be chosen: clearing takes a dash, so it cannot shadow one. */
    @Test
    void aGroupNamedDefaultCanBeChosen()
    {
        MaterialGroupRegistry.load(Map.of("Default", Map.of("structure", "DIAMOND_BLOCK")));
        final Stargate alpha = new Stargate();
        alpha.setGateName("alpha");
        StargateManager.registerStargate(alpha);
        try (MockedStatic<StargateDBManager> db = mockStatic(StargateDBManager.class))
        {
            new GateEditCommand().execute(sender, new String[] { "gate", "edit", "alpha", "group", "default" });
        }
        assertEquals(true, alpha.isGateMaterialGroupChosen());
        assertEquals("Default", alpha.getGateMaterialGroup().getName());
    }
}
