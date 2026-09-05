package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Unit tests for StargateManager activation mapping behavior.
 */
class StargateManagerTest
{
    @Test
    void addAndRemoveActivatedStargate()
    {
        Player p = Mockito.mock(Player.class);
        Stargate s = Mockito.mock(Stargate.class);

        StargateManager.addActivatedStargate(p, s);
        Stargate retrieved = StargateManager.removeActivatedStargate(p);
        assertSame(s, retrieved, "Removed activated stargate should be the same instance added");
    }

    @Test
    void removeActivatorForStargate()
    {
        Player p1 = Mockito.mock(Player.class);
        Player p2 = Mockito.mock(Player.class);
        Stargate s1 = Mockito.mock(Stargate.class);
        Stargate s2 = Mockito.mock(Stargate.class);

        // add two activations
        StargateManager.addActivatedStargate(p1, s1);
        StargateManager.addActivatedStargate(p2, s2);

        // remove activator for s1
        Player removed = StargateManager.removeActivatorForStargate(s1);
        assertNotNull(removed, "Activator for s1 should be found and removed");
        // ensure mapping for removed player is gone
        Stargate now = StargateManager.removeActivatedStargate(removed);
        assertNull(now, "Activator mapping should have been removed already");

        // removing activator for a gate not in map returns null
        Player none = StargateManager.removeActivatorForStargate(s1);
        assertNull(none, "No activator should be returned for a gate with no activator");
    }
}
