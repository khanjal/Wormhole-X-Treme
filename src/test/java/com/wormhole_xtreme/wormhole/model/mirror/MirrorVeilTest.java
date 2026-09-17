package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.freya.FreyaCompanion;

/**
 * A mirror leaves the companion's visibility alone.
 *
 * <p>She is hidden by default and shown to her owner. For such an entity a hide removes the
 * owner's exception and a show adds one, so a mirror veiling her took her away from her owner and
 * then, on unveiling, showed her to whichever other player had been looking.
 */
class MirrorVeilTest
{
    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        FreyaCompanion.forgetAll();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        FreyaCompanion.forgetAll();
        PluginTestSupport.remove();
    }

    @Test
    void aMirrorNeverVeilsACompanion()
    {
        final World world = mock(World.class);
        final Cat freya = mock(Cat.class);
        when(world.spawn(any(Location.class), eq(Cat.class))).thenReturn(freya);
        final Player owner = mock(Player.class);
        when(owner.getUniqueId()).thenReturn(UUID.randomUUID());
        when(owner.getLocation()).thenReturn(new Location(world, 0.0, 64.0, 0.0));
        FreyaCompanion.spawnFor(owner);

        assertFalse(MirrorWindows.veilable(freya),
            "veiling her hides her from her owner and unveiling shows her to a stranger");
    }

    @Test
    void anOrdinaryCatIsStillVeiled()
    {
        assertTrue(MirrorWindows.veilable(mock(Cat.class)),
            "only the companion is exempt; a real cat inside the view is still hidden");
    }

    @Test
    void playersAreNeverVeiled()
    {
        assertFalse(MirrorWindows.veilable(mock(Player.class)), "hiding a player drops them off the tab list");
    }
}
