package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

class GateSerializerTest
{
    @Test
    void roundtripSerializeAndParseProducesConsistentMinimalGate() throws Exception
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

    /**
     * Builds a mocked world whose getBlockAt returns coordinate-carrying block mocks.
     */
    private static World mockWorld()
    {
        final World w = mock(World.class);
        when(w.getName()).thenReturn("gw");
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
        return w;
    }

    private static Stargate minimalGate(final World w)
    {
        final Stargate s = new Stargate();
        s.setGateName("mat-gate");
        final Block dial = mock(Block.class);
        when(dial.getX()).thenReturn(10);
        when(dial.getY()).thenReturn(64);
        when(dial.getZ()).thenReturn(20);
        when(dial.getLocation()).thenReturn(new Location(w, 10, 64, 20));
        s.setGateDialLeverBlock(dial);
        s.setGatePlayerTeleportLocation(new Location(w, 65.0, 65.0, 65.0));
        s.setGateFacing(org.bukkit.block.BlockFace.NORTH);
        return s;
    }

    @Test
    void customMaterialsSurviveARoundTrip()
    {
        final World w = mockWorld();
        final Stargate s1 = minimalGate(w);
        s1.setGateCustom(true);
        s1.setGateCustomStructureMaterial(org.bukkit.Material.LAPIS_BLOCK);
        s1.setGateCustomPortalMaterial(org.bukkit.Material.LAVA);
        s1.setGateCustomLightMaterial(org.bukkit.Material.SEA_LANTERN);
        s1.setGateCustomIrisMaterial(org.bukkit.Material.YELLOW_STAINED_GLASS);

        final Stargate s2 = GateSerializer.parseVersionedData(
            GateSerializer.stargatetoBinary(s1), w, s1.getGateName(), null);

        assertTrue(s2.isGateCustom());
        assertEquals(org.bukkit.Material.LAPIS_BLOCK, s2.getGateCustomStructureMaterial());
        assertEquals(org.bukkit.Material.LAVA, s2.getGateCustomPortalMaterial());
        assertEquals(org.bukkit.Material.SEA_LANTERN, s2.getGateCustomLightMaterial());
        assertEquals(org.bukkit.Material.YELLOW_STAINED_GLASS, s2.getGateCustomIrisMaterial());
    }

    @Test
    void absentCustomMaterialsRoundTripAsNull()
    {
        final World w = mockWorld();
        final Stargate s1 = minimalGate(w);
        // No custom materials set — the encoder must write a zero-length name, not a
        // sentinel that resolves back to some arbitrary material.
        final Stargate s2 = GateSerializer.parseVersionedData(
            GateSerializer.stargatetoBinary(s1), w, s1.getGateName(), null);

        assertNull(s2.getGateCustomStructureMaterial());
        assertNull(s2.getGateCustomPortalMaterial());
        assertNull(s2.getGateCustomLightMaterial());
        assertNull(s2.getGateCustomIrisMaterial());
    }

    @Test
    void materialsAreStoredByNameNotOrdinal()
    {
        // The point of version 9: the encoded form must not depend on the enum's
        // declaration order, which shifts whenever Minecraft adds or removes a block.
        final World w = mockWorld();
        final Stargate s1 = minimalGate(w);
        s1.setGateCustom(true);
        s1.setGateCustomStructureMaterial(org.bukkit.Material.LAPIS_BLOCK);

        final byte[] data = GateSerializer.stargatetoBinary(s1);
        final String asLatin1 = new String(data, java.nio.charset.StandardCharsets.ISO_8859_1);

        assertTrue(asLatin1.contains("LAPIS_BLOCK"),
            "material should be encoded by name so it survives a Bukkit version change");
    }

    @Test
    void serializedBufferIsExactlyConsumedByTheReader() throws Exception
    {
        // The four materials are variable-length now, so the size calculation can no
        // longer be eyeballed. Over-allocating leaves trailing padding, which the reader
        // reports; this pins that it does not happen for either a gate with materials or
        // one without.
        final com.wormhole_xtreme.wormhole.WormholeXTreme plugin =
            mock(com.wormhole_xtreme.wormhole.WormholeXTreme.class);
        final java.lang.reflect.Field f =
            com.wormhole_xtreme.wormhole.WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        final Object previous = f.get(null);
        f.set(null, plugin);
        try
        {
            final World w = mockWorld();

            final Stargate withMaterials = minimalGate(w);
            withMaterials.setGateCustom(true);
            withMaterials.setGateCustomStructureMaterial(org.bukkit.Material.LAPIS_BLOCK);
            withMaterials.setGateCustomIrisMaterial(org.bukkit.Material.YELLOW_STAINED_GLASS);
            GateSerializer.parseVersionedData(GateSerializer.stargatetoBinary(withMaterials), w, "a", null);

            GateSerializer.parseVersionedData(GateSerializer.stargatetoBinary(minimalGate(w)), w, "b", null);

            // Two arguments, not three. The reader logs through the two-argument prettyLog,
            // and a verification against the three-argument overload would pass here for the
            // wrong reason -- nothing calls it, so "never" would hold even if the buffer were
            // badly sized and the warning were being logged on every run.
            verify(plugin, never()).prettyLog(any(), contains("not all byte data was read"));
        }
        finally
        {
            f.set(null, previous);
        }
    }

    @Test
    void writerEmitsCurrentSaveVersion()
    {
        final World w = mockWorld();
        final byte[] data = GateSerializer.stargatetoBinary(minimalGate(w));

        assertEquals((byte) 9, data[0], "version byte should be 9 now materials are name-encoded");
    }

    @Test
    void mixedPresentAndAbsentMaterialsStayAlignedInTheStream()
    {
        // Variable-length fields mean a miscounted length desyncs everything after it,
        // so pin a gate where only some materials are set.
        final World w = mockWorld();
        final Stargate s1 = minimalGate(w);
        s1.setGateCustom(true);
        s1.setGateCustomPortalMaterial(org.bukkit.Material.WATER);
        s1.setGateCustomWooshTicks(7);
        s1.setGateCustomLightTicks(5);

        final Stargate s2 = GateSerializer.parseVersionedData(
            GateSerializer.stargatetoBinary(s1), w, s1.getGateName(), null);

        assertNull(s2.getGateCustomStructureMaterial());
        assertEquals(org.bukkit.Material.WATER, s2.getGateCustomPortalMaterial());
        assertNull(s2.getGateCustomLightMaterial());
        assertNull(s2.getGateCustomIrisMaterial());
        // Fields after the variable-length section must still line up.
        assertEquals(7, s2.getGateCustomWooshTicks());
        assertEquals(5, s2.getGateCustomLightTicks());
    }

    /**
     * A wave nobody built still occupies its slot.
     *
     * <p>Light and woosh waves are numbered, and a shape declaring only {@code L#3} leaves
     * waves one and two as nulls in the list. The writer puts a zero count in for each, so
     * the reader hands back the same numbering. Skip them and every later wave shifts down,
     * which on a built gate means the wrong blocks lighting in the wrong order.
     */
    @Test
    void anEmptyWaveKeepsItsPlaceInTheNumbering() throws Exception
    {
        final World w = mockWorld();
        final Stargate s1 = minimalGate(w);
        s1.getGateLightBlocks().add(null);
        s1.getGateLightBlocks().add(null);
        final java.util.ArrayList<Location> thirdWave = new java.util.ArrayList<Location>();
        thirdWave.add(new Location(w, 1, 2, 3));
        s1.getGateLightBlocks().add(thirdWave);

        final Stargate s2 = GateSerializer.parseVersionedData(
            GateSerializer.stargatetoBinary(s1), w, s1.getGateName(), null);

        assertEquals(3, s2.getGateLightBlocks().size(), "all three slots come back");
        assertEquals(1, s2.getGateLightBlocks().get(2).size(),
            "and the one real wave is still the third, not the first");
    }
}
