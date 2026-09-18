package com.wormhole_xtreme.wormhole.model;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.utils.WorldUtils;

/**
 * Opening a gate once its destination is known: a gate its button lit all at once dials its
 * chevrons in order again, and one not yet lit lights them for the first time.
 */
class DialRelightTest
{
    private Stargate gate;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.scheduleSyncDelayedTask(any(Plugin.class), any(Runnable.class), anyLong())).thenReturn(42);
        PluginTestSupport.scheduler(scheduler);

        gate = spy(new Stargate());
        gate.setGateName("alpha");
        doReturn(new Location(mock(World.class), 0, 64, 0)).when(gate).getGatePlayerTeleportLocation();
        doNothing().when(gate).toggleDialLeverState(anyBoolean());
        doNothing().when(gate).toggleRedstoneGateActivatedPower();
        doNothing().when(gate).relightChevrons();
        doNothing().when(gate).lightStargate(anyBoolean());
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PluginTestSupport.scheduler(null);
        PluginTestSupport.remove();
    }

    private void dial()
    {
        try (MockedStatic<WorldUtils> world = mockStatic(WorldUtils.class))
        {
            StargateDialManager.dialStargate(gate);
        }
    }

    /** {@code /dial} after the button: the chevrons go dark and light again in order. */
    @Test
    void aGateItsButtonLitRelightsInOrder()
    {
        gate.setGateLightsActive(true);

        dial();

        verify(gate).relightChevrons();
        verify(gate, never()).lightStargate(true);
    }

    /** A sign dial, or the far end, was not lit by a button, so it lights from the first. */
    @Test
    void aGateNotYetLitLightsFromTheFirst()
    {
        dial();

        verify(gate).lightStargate(true);
        verify(gate, never()).relightChevrons();
    }
}
