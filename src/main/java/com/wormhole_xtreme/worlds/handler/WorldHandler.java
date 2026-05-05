package com.wormhole_xtreme.worlds.handler;

import org.bukkit.Chunk;

/**
 * Minimal stub for world handler used by optional WormholeXTremeWorlds integration.
 */
public class WorldHandler {
    public void addStickyChunk(Chunk c, String owner) {
        // stub
    }

    public void removeStickyChunk(Chunk c, String owner) {
        // stub
    }

    public boolean loadWorld(String worldName) {
        return false;
    }
}
