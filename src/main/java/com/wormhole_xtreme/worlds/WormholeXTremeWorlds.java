package com.wormhole_xtreme.worlds;

import com.wormhole_xtreme.worlds.handler.WorldHandler;

/**
 * Minimal stub for WormholeXTremeWorlds plugin integration.
 */
public class WormholeXTremeWorlds {
    public static WorldHandler getWorldHandler() {
        return new WorldHandler();
    }
}
