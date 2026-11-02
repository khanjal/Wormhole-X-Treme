package com.wormhole_xtreme.wormhole;

import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * Reads a gate's material group off its frame when the frame's chunk loads, so bStats counts
 * gates nobody has used since startup without loading a chunk to do it.
 */
public class GateChunkLoadListener implements Listener
{
    /**
     * Resolves the group of every gate whose first frame block is in the chunk that loaded.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(final ChunkLoadEvent event)
    {
        // A freshly generated chunk cannot hold a gate.
        if (event.isNewChunk())
        {
            return;
        }
        final Chunk chunk = event.getChunk();
        for (final Stargate gate : StargateManager.getAllGatesUnsorted())
        {
            gate.resolveGroupIfFrameIn(chunk);
        }
    }
}
