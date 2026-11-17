package com.wormhole_xtreme.wormhole.model.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorAccess;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorManager;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorNetwork;
import com.wormhole_xtreme.wormhole.model.mirror.QuantumMirror;

/**
 * The sweep never draws a private room for somebody who could not go there (#280).
 *
 * <p>A mirror's view is one view, drawn for everybody in range. A holder of
 * {@code wormhole.mirror.private} who turned a mirror to a private room would otherwise show it to
 * anybody who walked up afterwards; so before the view is drawn, a choice somebody near could not
 * reach is dropped and the mirror shows its own room.
 */
class WindowSweepPrivateMirrorTest
{
    /** Held for the test, since a Location keeps its World weakly. */
    private World world;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        ConfigTestSupport.clear();
        MirrorManager.clear();
        MirrorNetwork.clear();
        world = mock(World.class);
        when(world.getName()).thenReturn("world");
    }

    @AfterEach
    void tearDown() throws Exception
    {
        MirrorNetwork.clear();
        MirrorManager.clear();
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    @Test
    void aPrivateRoomIsNotDrawnWhenSomebodyWithoutTheNodeIsNear()
    {
        final QuantumMirror hub = new QuantumMirror("hub", new BlockPlace("world", 10, 64, 10),
            new Place("world", 10.5, 63, 10.5, 180f, 0f));
        MirrorManager.add(hub);
        MirrorManager.add(new QuantumMirror("staff", new BlockPlace("vault", 0, 64, 0),
            new Place("vault", 0.5, 63, 0.5, 0f, 0f)).withAccess(new MirrorAccess(null, false, true)));
        final Player holder = mock(Player.class);
        when(holder.hasPermission(MirrorNetwork.SEE_PRIVATE)).thenReturn(true);
        MirrorNetwork.scroll(hub, false, holder);
        assertEquals("staff", MirrorNetwork.chosen(hub).name(), "the holder's right-click lists it");
        final Player visitor = mock(Player.class);
        when(visitor.getLocation()).thenReturn(new Location(world, 10.5, 64, 12.5));
        when(world.getPlayers()).thenReturn(List.of(visitor));
        // A banner the sweep cannot draw, so nothing past the check needs a server.
        final Block banner = mock(Block.class);
        when(banner.getWorld()).thenReturn(world);

        assertFalse(WindowSweep.offerMirror(hub, banner));

        assertEquals("hub", MirrorNetwork.chosen(hub).name(), "the visitor sees the hub's own room, not the staff room");
    }
}
