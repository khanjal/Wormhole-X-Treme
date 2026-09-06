package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.utils.DataUtils;

/**
 * Reading a gate saved by an older version of the plugin.
 *
 * <p>Save versions 3 to 7 are read-only: {@code stargatetoBinary} writes version 9 and
 * nothing else, so nothing round-trips them and there was no test that actually parsed one.
 * {@code LegacyImportTest} had a case that looked like this one and asserted, through
 * reflection, that the reader method existed.
 *
 * <p>These are the import path for databases from the forks this one descends from, so
 * losing them would break the servers most likely to want importing. The buffers below are
 * built with the same {@link DataUtils} codec the reader uses, so the fixture cannot drift
 * from the encoding while agreeing with itself.
 */
class LegacySaveVersionTest
{
    @BeforeEach
    void setUp() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        final Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, plugin);
    }

    /** A world whose getBlockAt hands back blocks that remember where they are. */
    private static World mockWorld()
    {
        final World w = mock(World.class);
        when(w.getName()).thenReturn("gw");
        when(w.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(inv -> {
            final int x = inv.getArgument(0, Integer.class).intValue();
            final int y = inv.getArgument(1, Integer.class).intValue();
            final int z = inv.getArgument(2, Integer.class).intValue();
            final Block b = mock(Block.class);
            when(b.getX()).thenReturn(Integer.valueOf(x));
            when(b.getY()).thenReturn(Integer.valueOf(y));
            when(b.getZ()).thenReturn(Integer.valueOf(z));
            when(b.getLocation()).thenReturn(new Location(w, x, y, z));
            return b;
        });
        return w;
    }

    private static Block blockAt(final World w, final int x, final int y, final int z)
    {
        return w.getBlockAt(x, y, z);
    }

    /**
     * A version 3 gate, in the order {@code readVersion3} reads it.
     *
     * <p>Written with DataUtils rather than hand-rolled bytes: the point is to pin the
     * reader's field order and sizes, not to re-derive the encoding.
     */
    private static byte[] version3Gate(final World w)
    {
        final byte[] dial = DataUtils.blockToBytes(blockAt(w, 10, 64, 20));
        final byte[] iris = DataUtils.blockToBytes(blockAt(w, 11, 64, 20));
        final byte[] nameHolder = DataUtils.blockToBytes(blockAt(w, 12, 64, 20));
        final byte[] teleport = DataUtils.locationToBytes(new Location(w, 10.5, 65.0, 21.5));
        final byte[] dialSign = DataUtils.blockToBytes(blockAt(w, 13, 64, 20));
        final byte[] facing = "NORTH".getBytes(StandardCharsets.UTF_8);
        final byte[] idc = "letmein".getBytes(StandardCharsets.UTF_8);
        final byte[] structure = DataUtils.blockToBytes(blockAt(w, 1, 64, 1));
        final byte[] portal = DataUtils.blockToBytes(blockAt(w, 2, 64, 2));

        final ByteBuffer b = ByteBuffer.allocate(512);
        b.put((byte) 3);                 // save version
        b.put(dial);
        b.put(iris);
        b.put(nameHolder);
        b.put(teleport);
        b.put((byte) 0);                 // not sign powered
        b.put(dialSign);
        b.putInt(0);                     // dial sign index
        b.putInt(0);                     // temp sign target
        b.put((byte) 0);                 // not active
        b.putInt(-1);                    // temp target id
        b.putInt(facing.length);
        b.put(facing);
        b.putInt(idc.length);
        b.put(idc);
        b.put((byte) 1);                 // iris active
        b.putInt(1);                     // one structure block
        b.put(structure);
        b.putInt(1);                     // one portal block
        b.put(portal);

        final byte[] out = new byte[b.position()];
        b.rewind();
        b.get(out);
        return out;
    }

    @Test
    void aVersion3GateIsStillReadable()
    {
        final World w = mockWorld();
        final Stargate s = GateSerializer.parseVersionedData(version3Gate(w), w, "oldgate", null);

        assertNotNull(s, "a version 3 gate should still parse; this is the import path");
        assertEquals("oldgate", s.getGateName());
        assertEquals(3, s.getLoadedVersion());
        assertEquals(BlockFace.NORTH, s.getGateFacing());
        assertEquals("letmein", s.getGateIrisDeactivationCode());
        assertTrue(s.isGateIrisActive());
    }

    /** The reader's field order is what a fixture like this actually pins. */
    @Test
    void aVersion3GateKeepsItsBlocksAndItsArrivalPoint()
    {
        final World w = mockWorld();
        final Stargate s = GateSerializer.parseVersionedData(version3Gate(w), w, "oldgate", null);
        assertNotNull(s);

        assertEquals(10, s.getGateDialLeverBlock().getX(), "the dial lever is the first block read");
        assertEquals(11, s.getGateIrisLeverBlock().getX());
        assertEquals(12, s.getGateNameBlockHolder().getX());
        assertEquals(1, s.getGateStructureBlocks().size());
        assertEquals(1, s.getGatePortalBlocks().size());

        // v3 stored the arrival at the traveller's feet; the reader raises it a block.
        assertEquals(66.0, s.getGatePlayerTeleportLocation().getY(), 1e-9);
    }

    /**
     * A version byte the reader does not know is refused rather than guessed at. Reading an
     * unknown layout would put whatever followed into whichever fields happened to line up.
     */
    @Test
    void anUnknownSaveVersionIsRefusedRatherThanGuessed()
    {
        final World w = mockWorld();
        for (final byte version : new byte[] { (byte) 0, (byte) 2, (byte) 10, (byte) 99 })
        {
            final ByteBuffer b = ByteBuffer.allocate(64);
            b.put(version);
            b.putInt(0);
            final byte[] data = new byte[b.position()];
            b.rewind();
            b.get(data);

            assertNull(GateSerializer.parseVersionedData(data, w, "unknown", null),
                "save version " + version + " is not a layout this reader knows");
        }
    }
}
