package com.wormhole_xtreme.wormhole.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.command.CommandUtilities;
import com.wormhole_xtreme.wormhole.model.MaterialGroup;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateDBManager;

/**
 * What a regenerated gate keeps from the one it replaces. The fresh gate is saved straight
 * after, so anything not carried over here is lost for good.
 */
class GateRefreshCarryOverTest
{
    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PluginTestSupport.remove();
        PluginTestSupport.forgetAllGates();
    }

    /** A gate's own ring pattern (#366) survives a regen, rather than falling back to the server's. */
    @Test
    void aRegeneratedGateKeepsItsOwnRingPattern()
    {
        final Stargate existing = new Stargate();
        existing.setGateName("alpha");
        existing.setGateDialSpin(DialSpinPattern.PEGASUS);
        final Stargate fresh = new Stargate();

        GateRefresh.carryOverMetadata(existing, fresh, false);

        assertEquals("alpha", fresh.getGateName());
        assertEquals(DialSpinPattern.PEGASUS, fresh.getGateDialSpin(), "not the unset a fresh detection has");
    }

    /**
     * Every setting gate edit can make survives a regen (#440). Each is set to something a fresh
     * detection would not have, so a setting left out of the carry-over fails here by name.
     */
    @Test
    void aRegeneratedGateKeepsEverySettingGateEditMade()
    {
        final MaterialGroup atlantis = new MaterialGroup(
            "Atlantis", Material.LAPIS_BLOCK, Material.WATER, Material.STONE,
            Material.SEA_LANTERN, Material.OAK_WALL_SIGN);
        final Stargate existing = new Stargate();
        existing.setGateCustom(true);
        existing.setGateCustomStructureMaterial(Material.GOLD_BLOCK);
        existing.setGateCustomPortalMaterial(Material.LAVA);
        existing.setGateCustomLightMaterial(Material.SHROOMLIGHT);
        existing.setGateCustomIrisMaterial(Material.GLASS);
        existing.setGateCustomWooshTicks(7);
        existing.setGateCustomLightTicks(9);
        existing.setGateCustomWooshDepth(5);
        existing.setGateCustomWooshDepthSquared(25);
        existing.setGateRedstonePowered(true);
        existing.setGateIrisDefaultActive(true);
        existing.chooseGateMaterialGroup(atlantis);
        // Detected on a gold frame, so the gold the old gate carried is still what it is built from.
        final Stargate fresh = new Stargate();
        fresh.setGateMaterialGroup(new MaterialGroup("Gold", Material.GOLD_BLOCK,
            Material.WATER, Material.STONE, Material.GLOWSTONE, Material.OAK_WALL_SIGN));

        GateRefresh.carryOverSettings(existing, fresh);

        assertEquals(true, fresh.isGateCustom(), "custom");
        assertEquals(Material.GOLD_BLOCK, fresh.getGateCustomStructureMaterial(), "structure");
        assertEquals(Material.LAVA, fresh.getGateCustomPortalMaterial(), "portal");
        assertEquals(Material.SHROOMLIGHT, fresh.getGateCustomLightMaterial(), "light");
        assertEquals(Material.GLASS, fresh.getGateCustomIrisMaterial(), "iris");
        assertEquals(7, fresh.getGateCustomWooshTicks(), "woosh ticks");
        assertEquals(9, fresh.getGateCustomLightTicks(), "light ticks");
        assertEquals(5, fresh.getGateCustomWooshDepth(), "woosh depth");
        assertEquals(25, fresh.getGateCustomWooshDepthSquared(), "woosh depth squared");
        assertEquals(true, fresh.isGateRedstonePowered(), "redstone");
        assertEquals(true, fresh.isGateIrisDefaultActive(), "iris shut by default: an IDC gate must not open on regen");
        assertEquals(true, fresh.isGateMaterialGroupChosen(), "a chosen group stays chosen");
        assertEquals(atlantis, fresh.getGateMaterialGroup(), "the chosen group");
    }

    /**
     * A gate whose iris was shut when it was regenerated comes back shut (#440), through the real
     * refresh: removing an iris-coded gate opens its iris, so the carry-over has to be told what it
     * was before. Read afterwards, it always read open. Found by a Fable review.
     */
    @Test
    void aShutIrisStaysShutThroughARegen()
    {
        final Stargate existing = new Stargate();
        existing.setGateName("alpha");
        existing.setGateIrisActive(true);
        final Stargate fresh = spy(new Stargate());
        doNothing().when(fresh).toggleIrisActive(ArgumentMatchers.anyBoolean());
        final Block button = mock(Block.class);

        try (MockedStatic<StargateHelper> helper = mockStatic(StargateHelper.class);
             MockedStatic<CommandUtilities> util =
                 mockStatic(CommandUtilities.class);
             MockedStatic<StargateDBManager> db =
                 mockStatic(StargateDBManager.class))
        {
            helper.when(() -> StargateHelper.checkStargate(button, BlockFace.NORTH)).thenReturn(fresh);
            // What removal does to an iris-coded gate: opens its iris.
            util.when(() -> CommandUtilities.gateRemove(existing, false, false))
                .thenAnswer(call ->
                {
                    existing.setGateIrisActive(false);
                    return null;
                });

            GateRefresh.refresh(existing, button, BlockFace.NORTH);
        }

        verify(fresh).toggleIrisActive(false);
    }

    /** A gate whose iris was open is not shut by a regen. */
    @Test
    void anOpenIrisIsLeftOpen()
    {
        final Stargate existing = new Stargate();
        existing.setGateName("alpha");
        final Stargate fresh = spy(new Stargate());

        GateRefresh.carryOverMetadata(existing, fresh, false);

        verify(fresh, never()).toggleIrisActive(ArgumentMatchers.anyBoolean());
    }

    /**
     * A custom frame material the fresh gate is not built from is dropped rather than carried. Older
     * versions snapshotted the shape's default into it, and regen -fill would lay it into the frame.
     * Found by a Fable review.
     */
    @Test
    void aFrameMaterialTheGateIsNoLongerBuiltFromIsNotCarried()
    {
        final Stargate existing = new Stargate();
        existing.setGateCustom(true);
        existing.setGateCustomStructureMaterial(Material.OBSIDIAN);
        final Stargate fresh = new Stargate();
        fresh.setGateMaterialGroup(new MaterialGroup("Atlantis", Material.LAPIS_BLOCK,
            Material.WATER, Material.STONE, Material.SEA_LANTERN, Material.OAK_WALL_SIGN));

        GateRefresh.carryOverSettings(existing, fresh);

        Assertions.assertNull(fresh.getGateCustomStructureMaterial(), "obsidian into a lapis frame is not kept");
        assertEquals(Material.LAPIS_BLOCK, fresh.getEffectiveStructureMaterial());
    }

    /** A gate's own iris animation (#427) survives a regen. */
    @Test
    void aRegeneratedGateKeepsItsIrisAnimation()
    {
        final Stargate existing = new Stargate();
        existing.setGateIrisAnimation("spiral");
        final Stargate fresh = new Stargate();

        GateRefresh.carryOverSettings(existing, fresh);

        assertEquals("spiral", fresh.getGateIrisAnimation());
    }
}
