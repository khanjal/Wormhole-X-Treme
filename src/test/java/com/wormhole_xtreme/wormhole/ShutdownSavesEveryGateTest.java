package com.wormhole_xtreme.wormhole;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.logging.Level;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.events.StargateShutdownEvent;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateDBManager;

/**
 * One gate that fails on the way down does not stop the others being saved.
 *
 * <p>Stopping the server with a wormhole open threw from the open gate's shutdown, and the throw left
 * onDisable's save loop: every gate after it, and the rings, beams and mirrors, went unsaved. Each gate's
 * shutdown and each gate's save now fail on their own, said in the log, and the loop goes on.
 */
class ShutdownSavesEveryGateTest
{
    @Test
    void aGateThatThrowsWhileShuttingIsLoggedAndDoesNotThrow()
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        final Stargate gate = mock(Stargate.class);
        when(gate.isGateActive()).thenReturn(true);
        when(gate.getGateName()).thenReturn("Abydos");
        doThrow(new IllegalStateException("scheduler refused")).when(gate)
            .shutdownStargate(false, StargateShutdownEvent.Reason.PLUGIN_DISABLE);

        WormholeXTreme.shutDownForDisable(plugin, gate);

        verify(plugin).prettyLog(eq(Level.WARNING), contains("Could not shut Abydos"), any(Throwable.class));
    }

    @Test
    void aClosedGateIsNotShut()
    {
        final Stargate gate = mock(Stargate.class);

        WormholeXTreme.shutDownForDisable(mock(WormholeXTreme.class), gate);

        verify(gate, never()).shutdownStargate(false, StargateShutdownEvent.Reason.PLUGIN_DISABLE);
    }

    @Test
    void anOpenGateIsShut()
    {
        final Stargate gate = mock(Stargate.class);
        when(gate.isGateLightsActive()).thenReturn(true);

        WormholeXTreme.shutDownForDisable(mock(WormholeXTreme.class), gate);

        verify(gate).shutdownStargate(false, StargateShutdownEvent.Reason.PLUGIN_DISABLE);
    }

    @Test
    void aGateThatCannotBeWrittenIsLoggedAndDoesNotThrow()
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        final Stargate gate = mock(Stargate.class);
        when(gate.getGateName()).thenReturn("Chulak");
        try (MockedStatic<StargateDBManager> db = mockStatic(StargateDBManager.class))
        {
            db.when(() -> StargateDBManager.saveStargate(gate)).thenThrow(new IllegalStateException("disk full"));

            WormholeXTreme.saveForDisable(plugin, gate);

            verify(plugin).prettyLog(eq(Level.SEVERE), contains("Could not save Chulak"), any(Throwable.class));
        }
    }
}
