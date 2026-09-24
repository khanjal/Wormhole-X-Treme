package com.wormhole_xtreme.wormhole.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.Stargate;

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

        GateRefresh.carryOverMetadata(existing, fresh);

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
        final com.wormhole_xtreme.wormhole.model.MaterialGroup atlantis = new com.wormhole_xtreme.wormhole.model.MaterialGroup(
            "Atlantis", org.bukkit.Material.LAPIS_BLOCK, org.bukkit.Material.WATER, org.bukkit.Material.STONE,
            org.bukkit.Material.SEA_LANTERN, org.bukkit.Material.OAK_WALL_SIGN);
        final Stargate existing = new Stargate();
        existing.setGateCustom(true);
        existing.setGateCustomStructureMaterial(org.bukkit.Material.GOLD_BLOCK);
        existing.setGateCustomPortalMaterial(org.bukkit.Material.LAVA);
        existing.setGateCustomLightMaterial(org.bukkit.Material.SHROOMLIGHT);
        existing.setGateCustomIrisMaterial(org.bukkit.Material.GLASS);
        existing.setGateCustomWooshTicks(7);
        existing.setGateCustomLightTicks(9);
        existing.setGateCustomWooshDepth(5);
        existing.setGateCustomWooshDepthSquared(25);
        existing.setGateRedstonePowered(true);
        existing.setGateIrisDefaultActive(true);
        existing.chooseGateMaterialGroup(atlantis);
        final Stargate fresh = new Stargate();

        GateRefresh.carryOverSettings(existing, fresh);

        assertEquals(true, fresh.isGateCustom(), "custom");
        assertEquals(org.bukkit.Material.GOLD_BLOCK, fresh.getGateCustomStructureMaterial(), "structure");
        assertEquals(org.bukkit.Material.LAVA, fresh.getGateCustomPortalMaterial(), "portal");
        assertEquals(org.bukkit.Material.SHROOMLIGHT, fresh.getGateCustomLightMaterial(), "light");
        assertEquals(org.bukkit.Material.GLASS, fresh.getGateCustomIrisMaterial(), "iris");
        assertEquals(7, fresh.getGateCustomWooshTicks(), "woosh ticks");
        assertEquals(9, fresh.getGateCustomLightTicks(), "light ticks");
        assertEquals(5, fresh.getGateCustomWooshDepth(), "woosh depth");
        assertEquals(25, fresh.getGateCustomWooshDepthSquared(), "woosh depth squared");
        assertEquals(true, fresh.isGateRedstonePowered(), "redstone");
        assertEquals(true, fresh.isGateIrisDefaultActive(), "iris shut by default: an IDC gate must not open on regen");
        assertEquals(true, fresh.isGateMaterialGroupChosen(), "a chosen group stays chosen");
        assertEquals(atlantis, fresh.getGateMaterialGroup(), "the chosen group");
    }

    /** A gate whose iris was shut when it was regenerated comes back shut (#440). */
    @Test
    void aShutIrisStaysShutThroughARegen()
    {
        final Stargate existing = new Stargate();
        existing.setGateName("alpha");
        existing.setGateIrisActive(true);
        final Stargate fresh = org.mockito.Mockito.spy(new Stargate());
        org.mockito.Mockito.doNothing().when(fresh).toggleIrisActive(org.mockito.ArgumentMatchers.anyBoolean());

        GateRefresh.carryOverMetadata(existing, fresh);

        org.mockito.Mockito.verify(fresh).toggleIrisActive(false);
    }

    /** A gate whose iris was open is not shut by a regen. */
    @Test
    void anOpenIrisIsLeftOpen()
    {
        final Stargate existing = new Stargate();
        existing.setGateName("alpha");
        final Stargate fresh = org.mockito.Mockito.spy(new Stargate());

        GateRefresh.carryOverMetadata(existing, fresh);

        org.mockito.Mockito.verify(fresh, org.mockito.Mockito.never()).toggleIrisActive(org.mockito.ArgumentMatchers.anyBoolean());
    }
}
