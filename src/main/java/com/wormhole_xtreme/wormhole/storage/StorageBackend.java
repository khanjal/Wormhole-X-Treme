package com.wormhole_xtreme.wormhole.storage;

import org.bukkit.Server;

import com.wormhole_xtreme.wormhole.model.Stargate;

import java.util.List;

/**
 * Storage backend interface for pluggable storage adapters.
 */
public interface StorageBackend
{
    void initialize();

    List<Stargate> loadStargates(Server server);

    void saveStargate(Stargate s);

    void removeStargate(Stargate s);

    void shutdown();
}
