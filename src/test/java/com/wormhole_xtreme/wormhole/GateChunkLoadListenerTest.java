package com.wormhole_xtreme.wormhole;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.bukkit.Chunk;
import org.bukkit.event.world.ChunkLoadEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * A chunk loading offers itself to every gate, so a gate whose frame is in it reads its material
 * group then, and bStats can count it rather than calling it unknown. Which gate it belongs to is
 * the gate's own check; {@code StargateEffectiveMaterialTest} covers that.
 *
 * <p>The event is mocked, as this project does for every event, since constructors differ by version.
 */
class GateChunkLoadListenerTest
{
    private final GateChunkLoadListener listener = new GateChunkLoadListener();

    private static ChunkLoadEvent load(final Chunk chunk, final boolean isNew)
    {
        final ChunkLoadEvent event = mock(ChunkLoadEvent.class);
        when(event.getChunk()).thenReturn(chunk);
        when(event.isNewChunk()).thenReturn(isNew);
        return event;
    }

    /** Every gate is offered a chunk that loads from disk. */
    @Test
    void aChunkLoadedFromDiskIsOfferedToEveryGate()
    {
        final Chunk chunk = mock(Chunk.class);
        final Stargate one = mock(Stargate.class);
        final Stargate two = mock(Stargate.class);
        try (MockedStatic<StargateManager> manager = mockStatic(StargateManager.class))
        {
            manager.when(StargateManager::getAllGatesUnsorted).thenReturn(List.of(one, two));

            listener.onChunkLoad(load(chunk, false));
        }

        verify(one).resolveGroupIfFrameIn(chunk);
        verify(two).resolveGroupIfFrameIn(chunk);
    }

    /** A chunk generated just now cannot hold a gate, so no gate is asked; this runs on every chunk load. */
    @Test
    void aNewlyGeneratedChunkIsNotOffered()
    {
        final Stargate gate = mock(Stargate.class);
        try (MockedStatic<StargateManager> manager = mockStatic(StargateManager.class))
        {
            manager.when(StargateManager::getAllGatesUnsorted).thenReturn(List.of(gate));

            listener.onChunkLoad(load(mock(Chunk.class), true));
        }

        verify(gate, never()).resolveGroupIfFrameIn(ArgumentMatchers.any());
    }
}
