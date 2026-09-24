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
}
