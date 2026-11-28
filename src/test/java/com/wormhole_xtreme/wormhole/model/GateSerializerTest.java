package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;
import com.wormhole_xtreme.wormhole.PluginTestSupport;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Assertions;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

class GateSerializerTest
{
    @Test
    void roundtripSerializeAndParseProducesConsistentMinimalGate()
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
            when(b.getWorld()).thenReturn(w);
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
        when(dial.getWorld()).thenReturn(w);

        s1.setGateDialLeverBlock(dial);
        s1.setGatePlayerTeleportLocation(new Location(w, 65.0, 65.0, 65.0));
        s1.setGateFacing(BlockFace.NORTH);

        final byte[] data = GateSerializer.stargateToBinary(s1);
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
            when(b.getWorld()).thenReturn(w);
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
        when(dial.getWorld()).thenReturn(w);
        s.setGateDialLeverBlock(dial);
        s.setGatePlayerTeleportLocation(new Location(w, 65.0, 65.0, 65.0));
        s.setGateFacing(BlockFace.NORTH);
        return s;
    }

    @Test
    void customMaterialsSurviveARoundTrip()
    {
        final World w = mockWorld();
        final Stargate s1 = minimalGate(w);
        s1.setGateCustom(true);
        s1.setGateCustomStructureMaterial(Material.LAPIS_BLOCK);
        s1.setGateCustomPortalMaterial(Material.LAVA);
        s1.setGateCustomLightMaterial(Material.SEA_LANTERN);
        s1.setGateCustomIrisMaterial(Material.YELLOW_STAINED_GLASS);

        final Stargate s2 = GateSerializer.parseVersionedData(
            GateSerializer.stargateToBinary(s1), w, s1.getGateName(), null);

        assertTrue(s2.isGateCustom());
        assertEquals(Material.LAPIS_BLOCK, s2.getGateCustomStructureMaterial());
        assertEquals(Material.LAVA, s2.getGateCustomPortalMaterial());
        assertEquals(Material.SEA_LANTERN, s2.getGateCustomLightMaterial());
        assertEquals(Material.YELLOW_STAINED_GLASS, s2.getGateCustomIrisMaterial());
    }

    @Test
    void absentCustomMaterialsRoundTripAsNull()
    {
        final World w = mockWorld();
        final Stargate s1 = minimalGate(w);
        // No custom materials set — the encoder must write a zero-length name, not a
        // sentinel that resolves back to some arbitrary material.
        final Stargate s2 = GateSerializer.parseVersionedData(
            GateSerializer.stargateToBinary(s1), w, s1.getGateName(), null);

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
        s1.setGateCustomStructureMaterial(Material.LAPIS_BLOCK);

        final byte[] data = GateSerializer.stargateToBinary(s1);
        final String asLatin1 = new String(data, StandardCharsets.ISO_8859_1);

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
        final WormholeXTreme plugin =
            mock(WormholeXTreme.class);
        PluginTestSupport.install(plugin);
        try
        {
            final World w = mockWorld();

            final Stargate withMaterials = minimalGate(w);
            withMaterials.setGateCustom(true);
            withMaterials.setGateCustomStructureMaterial(Material.LAPIS_BLOCK);
            withMaterials.setGateCustomIrisMaterial(Material.YELLOW_STAINED_GLASS);
            GateSerializer.parseVersionedData(GateSerializer.stargateToBinary(withMaterials), w, "a", null);

            GateSerializer.parseVersionedData(GateSerializer.stargateToBinary(minimalGate(w)), w, "b", null);

            // Two arguments, not three. The reader logs through the two-argument prettyLog,
            // and a verification against the three-argument overload would pass here for the
            // wrong reason -- nothing calls it, so "never" would hold even if the buffer were
            // badly sized and the warning were being logged on every run.
            verify(plugin, never()).prettyLog(any(), contains("not all byte data was read"));
        }
        finally
        {
            PluginTestSupport.remove();
        }
    }

    /**
     * A garbled wave count is refused rather than grown into.
     *
     * <p>The reader grows its list of layers to the saved count before reading any of them, and
     * growing reads nothing from the buffer, so a count of two billion ran to OutOfMemoryError. No
     * per-gate catch stops that: one damaged gate file failed the whole load, and one damaged row
     * the whole import. This gate has no light or woosh blocks, so its last four bytes are the
     * woosh wave count.
     */
    @Test
    void aGarbledWaveCountIsRefusedNotAllocated()
    {
        final World w = mockWorld();
        final byte[] data = GateSerializer.stargateToBinary(minimalGate(w));
        ByteBuffer.wrap(data).putInt(data.length - 4, Integer.MAX_VALUE);

        final IllegalArgumentException refused = Assertions.assertThrows(
            IllegalArgumentException.class, () -> GateSerializer.parseVersionedData(data, w, "garbled", null));
        assertTrue(refused.getMessage().contains("claims " + Integer.MAX_VALUE + " layers"),
            "the reason should name the count that cannot be right: " + refused.getMessage());
    }

    @Test
    void writerEmitsCurrentSaveVersion()
    {
        final World w = mockWorld();
        final byte[] data = GateSerializer.stargateToBinary(minimalGate(w));

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
        s1.setGateCustomPortalMaterial(Material.WATER);
        s1.setGateCustomWooshTicks(7);
        s1.setGateCustomLightTicks(5);

        final Stargate s2 = GateSerializer.parseVersionedData(
            GateSerializer.stargateToBinary(s1), w, s1.getGateName(), null);

        assertNull(s2.getGateCustomStructureMaterial());
        assertEquals(Material.WATER, s2.getGateCustomPortalMaterial());
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
    void anEmptyWaveKeepsItsPlaceInTheNumbering()
    {
        final World w = mockWorld();
        final Stargate s1 = minimalGate(w);
        s1.getGateLightBlocks().add(null);
        s1.getGateLightBlocks().add(null);
        final ArrayList<Location> thirdWave = new ArrayList<>();
        thirdWave.add(new Location(w, 1, 2, 3));
        s1.getGateLightBlocks().add(thirdWave);

        final Stargate s2 = GateSerializer.parseVersionedData(
            GateSerializer.stargateToBinary(s1), w, s1.getGateName(), null);

        assertEquals(3, s2.getGateLightBlocks().size(), "all three slots come back");
        assertEquals(1, s2.getGateLightBlocks().get(2).size(),
            "and the one real wave is still the third, not the first");
    }

    /**
     * Where the four materials start: a gate with no blocks ends in three timing ints, then the
     * structure, portal, light-wave and woosh-wave counts, and nothing in the head depends on blocks.
     */
    private static int materialsOffset()
    {
        return GateSerializer.stargateToBinary(minimalGate(mockWorld())).length - 7 * 4 - 16;
    }

    /** The version 8 record with the four materials as ordinals, the original plugin's layout. */
    private static byte[] originalLayoutVersion8(final byte[] noMaterialRecord, final int... ordinals)
    {
        final byte[] data = noMaterialRecord.clone();
        data[0] = 8;
        final ByteBuffer b = ByteBuffer.wrap(data);
        b.position(materialsOffset());
        for (final int ordinal : ordinals)
        {
            b.putInt(ordinal);
        }
        return data;
    }

    /**
     * Dakto's layout: a version 9 record with the version byte flipped to 8. No real Dakto-saved blob
     * was to hand; the layout is from issue 800 and the export finding in issue 719 (length-prefixed
     * UTF-8 names under version 8).
     */
    private static byte[] daktoLayoutVersion8(final Stargate s)
    {
        final byte[] data = GateSerializer.stargateToBinary(s);
        data[0] = 8;
        return data;
    }

    /** A gate with structure, portal and light blocks, so a wrong layout has plenty to misread. */
    private static Stargate gateWithBlocks(final World w)
    {
        final Stargate s = minimalGate(w);
        for (int i = 0; i < 3; i++)
        {
            s.getGateStructureBlocks().add(new Location(w, i, 1, 2));
        }
        for (int i = 0; i < 2; i++)
        {
            s.getGatePortalBlocks().add(new Location(w, i, 5, 2));
        }
        final ArrayList<Location> wave = new ArrayList<>();
        wave.add(new Location(w, 9, 9, 9));
        s.getGateLightBlocks().add(wave);
        return s;
    }

    private static byte[] withTrailingBytes(final byte[] data)
    {
        final byte[] longer = java.util.Arrays.copyOf(data, data.length + 3);
        longer[data.length] = 1;
        return longer;
    }

    @Test
    void anOriginalLayoutVersion8GateReadsItsOrdinals()
    {
        final World w = mockWorld();
        final Stargate s1 = gateWithBlocks(w);
        s1.setGateCustomWooshTicks(7);
        final byte[] data = originalLayoutVersion8(GateSerializer.stargateToBinary(s1),
            Material.LAPIS_BLOCK.ordinal(), -1, Material.SEA_LANTERN.ordinal(), -1);

        final Stargate s2 = GateSerializer.parseVersionedData(data, w, "orig", null);

        assertEquals(Material.LAPIS_BLOCK, s2.getGateCustomStructureMaterial());
        assertNull(s2.getGateCustomPortalMaterial());
        assertEquals(Material.SEA_LANTERN, s2.getGateCustomLightMaterial());
        assertNull(s2.getGateCustomIrisMaterial());
        assertEquals(7, s2.getGateCustomWooshTicks());
        assertEquals(3, s2.getGateStructureBlocks().size());
    }

    /**
     * An ordinal of 8 followed by three absent ones is also a valid names layout: the "name" is the
     * next two ints. Only the leftover bytes show it is not, so this record carries three.
     */
    @Test
    void anOrdinalThatReadsAsAWholeNamesRecordButLeavesBytesOverStaysAnOrdinal() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        PluginTestSupport.install(plugin);
        try
        {
            final World w = mockWorld();
            final byte[] data = withTrailingBytes(originalLayoutVersion8(
                GateSerializer.stargateToBinary(gateWithBlocks(w)), 8, -1, -1, -1));

            final Stargate s = GateSerializer.parseVersionedData(data, w, "eight", null);

            assertEquals(Material.values()[8], s.getGateCustomStructureMaterial());
            assertEquals(3, s.getGateStructureBlocks().size());
            verify(plugin).prettyLog(eq(Level.WARNING), contains("not all byte data was read"));
        }
        finally
        {
            PluginTestSupport.remove();
        }
    }

    @Test
    void anOrdinalRecordWithBytesOverIsKeptAsOrdinalsAndWarnsOnceWhenNamesCannotRead() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        PluginTestSupport.install(plugin);
        try
        {
            final World w = mockWorld();
            final byte[] data = withTrailingBytes(originalLayoutVersion8(
                GateSerializer.stargateToBinary(gateWithBlocks(w)), Material.LAPIS_BLOCK.ordinal(), -1, -1, -1));

            final Stargate s = GateSerializer.parseVersionedData(data, w, "extra", null);

            assertEquals(Material.LAPIS_BLOCK, s.getGateCustomStructureMaterial());
            verify(plugin, times(1)).prettyLog(eq(Level.WARNING), contains("not all byte data was read"));
        }
        finally
        {
            PluginTestSupport.remove();
        }
    }

    @Test
    void readingAnOriginalLayoutGateLogsNoWarning() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        PluginTestSupport.install(plugin);
        try
        {
            final World w = mockWorld();
            final byte[] data = originalLayoutVersion8(GateSerializer.stargateToBinary(gateWithBlocks(w)),
                Material.LAPIS_BLOCK.ordinal(), Material.LAVA.ordinal(), -1, -1);

            final Stargate s = GateSerializer.parseVersionedData(data, w, "quiet", null);

            assertEquals(Material.LAVA, s.getGateCustomPortalMaterial());
            verify(plugin, never()).prettyLog(eq(Level.WARNING), anyString());
            verify(plugin, never()).prettyLog(eq(Level.WARNING), anyString(), any(Throwable.class));
        }
        finally
        {
            PluginTestSupport.remove();
        }
    }

    @Test
    void aDaktoLayoutVersion8GateReadsItsNames()
    {
        final World w = mockWorld();
        final Stargate s1 = gateWithBlocks(w);
        s1.setGateCustom(true);
        s1.setGateCustomStructureMaterial(Material.LAPIS_BLOCK);
        s1.setGateCustomPortalMaterial(Material.LAVA);
        s1.setGateCustomIrisMaterial(Material.YELLOW_STAINED_GLASS);
        s1.setGateCustomWooshTicks(7);

        final Stargate s2 = GateSerializer.parseVersionedData(daktoLayoutVersion8(s1), w, "dakto", null);

        assertEquals(Material.LAPIS_BLOCK, s2.getGateCustomStructureMaterial());
        assertEquals(Material.LAVA, s2.getGateCustomPortalMaterial());
        assertNull(s2.getGateCustomLightMaterial());
        assertEquals(Material.YELLOW_STAINED_GLASS, s2.getGateCustomIrisMaterial());
        assertEquals(7, s2.getGateCustomWooshTicks(), "the fields after the names still line up");
        assertEquals(3, s2.getGateStructureBlocks().size(), "and the block lists are not doubled by a retry");
        assertEquals(2, s2.getGatePortalBlocks().size());
    }

    @Test
    void aVersion8GateWithNoCustomMaterialsReadsAsNoneInEitherLayout()
    {
        final World w = mockWorld();
        final byte[] none = GateSerializer.stargateToBinary(minimalGate(w));

        // Dakto writes four zero lengths, which as ordinals would be the first material four times.
        final Stargate dakto = GateSerializer.parseVersionedData(daktoLayoutVersion8(minimalGate(w)), w, "d", null);
        final Stargate original = GateSerializer.parseVersionedData(
            originalLayoutVersion8(none, -1, -1, -1, -1), w, "o", null);

        for (final Stargate s : new Stargate[] {dakto, original})
        {
            assertNull(s.getGateCustomStructureMaterial());
            assertNull(s.getGateCustomPortalMaterial());
            assertNull(s.getGateCustomLightMaterial());
            assertNull(s.getGateCustomIrisMaterial());
        }
    }

    @Test
    void aVersion8RecordThatIsNeitherLayoutFailsAsItAlwaysDid()
    {
        final World w = mockWorld();
        final byte[] truncated = new byte[] {8, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

        Assertions.assertThrows(java.nio.BufferUnderflowException.class,
            () -> GateSerializer.parseVersionedData(truncated, w, "garbage", null));
    }
}
