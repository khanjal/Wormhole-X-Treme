package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Directional;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;

/**
 * What the name sign leaves behind in the gate's own bookkeeping, and what it is not placed over.
 *
 * <p>{@code StargateSignTest} pins the text and the facing. Seven mutations on the sign and lever
 * code survived the suite that was there: a sign not recorded as one of the gate's blocks, a
 * removed sign left recorded, the glow setting ignored, and the chevron material not counting as
 * the frame. A sign the gate does not count is left behind when the gate goes; one it keeps
 * counting after it is gone makes the gate claim a block that is somebody else's.
 */
class NameSignBookkeepingTest
{
    private WormholeXTreme plugin;
    private Stargate gate;
    private Block nameHolder;
    private Block signBlock;
    private SignSide front;
    private Location signAt;

    @BeforeEach
    void setUp() throws Exception
    {
        plugin = mock(WormholeXTreme.class);
        PluginTestSupport.install(plugin);
        gate = new Stargate();
        gate.setGateName("signed");
        gate.setGateFacing(BlockFace.NORTH);
        nameHolder = mock(Block.class);
        gate.setGateNameBlockHolder(nameHolder);
        signBlock = mock(Block.class);
        when(nameHolder.getRelative(BlockFace.NORTH)).thenReturn(signBlock);
        signAt = new Location(null, 7, 64, 9);
        when(signBlock.getLocation()).thenReturn(signAt);

        final Sign state = mock(Sign.class);
        front = mock(SignSide.class);
        when(state.getSide(Side.FRONT)).thenReturn(front);
        when(signBlock.getState()).thenReturn(state);
        when(signBlock.getBlockData()).thenReturn(mock(Directional.class));
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PluginTestSupport.forgetAllGates();
        PluginTestSupport.remove();
    }

    @Test
    void aPlacedSignIsOneOfTheGatesBlocks()
    {
        GateFittings.setupGateSign(gate, true);

        assertTrue(gate.getGateStructureBlocks().contains(signAt),
            "so removing the gate takes the sign down with it");
    }

    @Test
    void aRemovedSignIsNoLongerClaimed()
    {
        when(signBlock.getType()).thenReturn(Material.OAK_WALL_SIGN);
        gate.getGateStructureBlocks().add(signAt);

        GateFittings.setupGateSign(gate, false);

        verify(signBlock).setType(Material.AIR);
        assertFalse(gate.getGateStructureBlocks().contains(signAt),
            "the cell is air now; the gate must not claim whatever is built there next");
    }

    @Test
    void theChevronMaterialCountsAsTheFrameToo()
    {
        final StargateShape shape = new StargateShape();
        shape.setShapeChevronMaterial(Material.SEA_LANTERN);
        gate.setGateShape(shape);
        final Material chevron = gate.getEffectiveChevronMaterial();
        assertNotEquals(gate.getEffectiveStructureMaterial(), chevron,
            "the fixture needs a chevron that is not also the ring, or this proves nothing");
        when(signBlock.getType()).thenReturn(chevron);

        GateFittings.setupGateSign(gate, true);

        verify(signBlock, never()).setType(any(), anyBoolean());
        verify(plugin).prettyLog(eq(Level.WARNING), contains("frame material"));
    }

    @Test
    void theSignGlowsWhenTheServerAsksForIt()
    {
        try (MockedStatic<ConfigManager> config = mockStatic(ConfigManager.class, CALLS_REAL_METHODS))
        {
            config.when(ConfigManager::isSignGlowingText).thenReturn(Boolean.TRUE);

            GateFittings.setupGateSign(gate, true);

            verify(front).setGlowingText(true);
        }
    }

    @Test
    void theSignDoesNotGlowWhenTheServerDoesNotAsk()
    {
        try (MockedStatic<ConfigManager> config = mockStatic(ConfigManager.class, CALLS_REAL_METHODS))
        {
            config.when(ConfigManager::isSignGlowingText).thenReturn(Boolean.FALSE);

            GateFittings.setupGateSign(gate, true);

            verify(front).setGlowingText(false);
        }
    }
}
