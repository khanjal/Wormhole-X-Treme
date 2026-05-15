package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.model.GateSerializer;

public class GateSerializerTest
{
    @Test
    public void roundtripSerializeAndParseProducesConsistentMinimalGate() throws Exception
    {
        final World w = mock(World.class);
        when(w.getName()).thenReturn("gw");

        // Provide a world.getBlockAt handler used by parseVersionedData -> DataUtils.blockFromBytes
        when(w.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
            final int x = invocation.getArgument(0);
            final int y = invocation.getArgument(1);
            final int z = invocation.getArgument(2);
            final Block b = mock(Block.class);
            when(b.getX()).thenReturn(x);
            when(b.getY()).thenReturn(y);
            when(b.getZ()).thenReturn(z);
            when(b.getLocation()).thenReturn(new Location(w, x, y, z));
            return b;
        });

        // Construct a minimal Stargate with required fields
        final Stargate s1 = new Stargate();
        s1.setGateName("test-gate");

        final Block dial = mock(Block.class);
        when(dial.getX()).thenReturn(10);
        when(dial.getY()).thenReturn(64);
        when(dial.getZ()).thenReturn(20);
        when(dial.getLocation()).thenReturn(new Location(w, 10, 64, 20));

        s1.setGateDialLeverBlock(dial);
        s1.setGatePlayerTeleportLocation(new Location(w, 65.0, 65.0, 65.0));
        s1.setGateFacing(org.bukkit.block.BlockFace.NORTH);

        final byte[] data = GateSerializer.stargatetoBinary(s1);
        assertNotNull(data);

        final Stargate s2 = GateSerializer.parseVersionedData(data, w, s1.getGateName(), null);
        assertNotNull(s2);
        assertEquals(s1.getGateName(), s2.getGateName());
        assertEquals(s1.getGateFacing(), s2.getGateFacing());
        assertNotNull(s2.getGatePlayerTeleportLocation());
        assertEquals(s1.getGatePlayerTeleportLocation().getBlockX(), s2.getGatePlayerTeleportLocation().getBlockX());
        assertEquals(s1.getGatePlayerTeleportLocation().getBlockZ(), s2.getGatePlayerTeleportLocation().getBlockZ());
    }
}
