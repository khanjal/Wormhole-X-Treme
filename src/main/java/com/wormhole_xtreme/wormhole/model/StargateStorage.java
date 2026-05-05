package com.wormhole_xtreme.wormhole.model;

import org.bukkit.Server;

import java.util.List;

public interface StargateStorage
{
    // Load all stargates into memory (register with StargateManager)
    void loadStargates(Server server);

    // Persist a stargate
    void saveStargate(Stargate s);

    // Remove persisted stargate
    void removeStargate(Stargate s);

    // Shutdown any resources
    void shutdown();

}
