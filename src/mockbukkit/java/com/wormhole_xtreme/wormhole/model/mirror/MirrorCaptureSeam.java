package com.wormhole_xtreme.wormhole.model.mirror;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.ChunkSnapshot;
import org.bukkit.HeightMap;

/** Lets a MockBukkit test reach MirrorCaptures' package-private chunk reader. */
public final class MirrorCaptureSeam
{
    private MirrorCaptureSeam()
    {
    }

    /** Captures read the world's own blocks, as MockBukkit's snapshots cannot answer a height. */
    public static void readFromWorldBlocks()
    {
        MirrorCaptures.readChunksWith((world, chunkX, chunkZ) -> {
            final ChunkSnapshot snapshot = mock(ChunkSnapshot.class);
            final int baseX = chunkX << 4;
            final int baseZ = chunkZ << 4;
            when(snapshot.getHighestBlockYAt(anyInt(), anyInt())).thenAnswer(inv -> Integer.valueOf(
                world.getHighestBlockYAt(baseX + inv.getArgument(0, Integer.class).intValue(),
                    baseZ + inv.getArgument(1, Integer.class).intValue(), HeightMap.WORLD_SURFACE)));
            when(snapshot.getBlockData(anyInt(), anyInt(), anyInt())).thenAnswer(inv -> world.getBlockAt(
                baseX + inv.getArgument(0, Integer.class).intValue(), inv.getArgument(1, Integer.class).intValue(),
                baseZ + inv.getArgument(2, Integer.class).intValue()).getBlockData());
            return snapshot;
        });
    }

    /** Back to the server's own snapshots. */
    public static void readFromServer()
    {
        MirrorCaptures.readChunksWith((world, chunkX, chunkZ) -> world.getChunkAt(chunkX, chunkZ).getChunkSnapshot());
    }
}
