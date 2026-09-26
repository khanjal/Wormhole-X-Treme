package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.utils.DataUtils;

/**
 * What a version 3 gate comes back as, field by field.
 *
 * <p>{@link LegacySaveVersionTest} proves a version 3 gate parses, but its record is mostly
 * defaults: not sign powered, target ids of 0 and -1, no yaw to overwrite. That was enough
 * while version 3 had a reader of its own. It shares one with versions 4 and 5 now, and the
 * one thing that sets it apart -- the two target ids are ints, not longs -- wants a record in
 * which reading them the wrong width cannot go unnoticed.
 */
class LegacyGateFidelityV3Test
{
    private World world;
    private WormholeXTreme plugin;

    @BeforeEach
    void setUp() throws Exception
    {
        plugin = mock(WormholeXTreme.class);
        PluginTestSupport.install(plugin);

        world = mock(World.class);
        when(world.getName()).thenReturn("gw");
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(inv -> {
            final int x = inv.getArgument(0, Integer.class).intValue();
            final int y = inv.getArgument(1, Integer.class).intValue();
            final int z = inv.getArgument(2, Integer.class).intValue();
            final Block b = mock(Block.class);
            when(b.getX()).thenReturn(Integer.valueOf(x));
            when(b.getY()).thenReturn(Integer.valueOf(y));
            when(b.getZ()).thenReturn(Integer.valueOf(z));
            when(b.getWorld()).thenReturn(world);
            when(b.getLocation()).thenReturn(new Location(world, x, y, z));
            return b;
        });
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PluginTestSupport.install(null);
        PluginTestSupport.forgetAllGates();
    }

    private Block blockAt(final int x, final int y, final int z)
    {
        return world.getBlockAt(x, y, z);
    }

    private static String at(final Block b)
    {
        return b == null ? "none" : b.getX() + "," + b.getY() + "," + b.getZ();
    }

    private static List<String> allAt(final List<Location> locations)
    {
        final List<String> out = new ArrayList<>();
        for (final Location l : locations)
        {
            out.add(l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ());
        }
        return out;
    }

    /**
     * One version 3 gate with every value distinguishable from every other and from its default.
     *
     * <p>The iris code is followed by the iris byte and then the structure count, so a reader
     * that also took a lights byte here would read the count from the wrong place.
     */
    private byte[] version3Gate()
    {
        return version3Gate(true, 41, 42, 0);
    }

    /**
     * The same record with the sign flag, the two ids and some trailing padding chosen.
     *
     * @param signPowered
     *            what the sign-powered byte says
     * @param signTarget
     *            the temp sign target, as the int version 3 wrote
     * @param targetId
     *            the temp target id, as the int version 3 wrote
     * @param trailing
     *            how many zero bytes follow the record
     * @return the gate record
     */
    private byte[] version3Gate(final boolean signPowered, final int signTarget, final int targetId,
        final int trailing)
    {
        final byte[] facing = "EAST".getBytes(StandardCharsets.UTF_8);
        final byte[] idc = "letmein".getBytes(StandardCharsets.UTF_8);

        final ByteBuffer b = ByteBuffer.allocate(1024);
        b.put((byte) 3);
        b.put(DataUtils.blockToBytes(blockAt(10, 64, 20)));       // dial lever
        b.put(DataUtils.blockToBytes(blockAt(11, 64, 20)));       // iris lever
        b.put(DataUtils.blockToBytes(blockAt(12, 64, 20)));       // name holder
        b.put(DataUtils.locationToBytes(new Location(world, 65.0, 69.0, 66.0, 33.0f, 45.0f)));

        b.put((byte) (signPowered ? 1 : 0));                       // sign powered
        b.put(DataUtils.blockToBytes(blockAt(13, 64, 20)));        // dial sign
        b.putInt(3);                                               // dial sign index
        b.putInt(signTarget);                                      // temp sign target, an int in v3
        b.put((byte) 1);                                           // active
        b.putInt(targetId);                                        // temp target id, an int in v3

        b.putInt(facing.length);
        b.put(facing);
        b.putInt(idc.length);
        b.put(idc);
        b.put((byte) 1);                                           // iris active

        b.putInt(2);                                               // structure blocks
        b.put(DataUtils.blockToBytes(blockAt(1, 64, 1)));
        b.put(DataUtils.blockToBytes(blockAt(2, 64, 1)));
        b.putInt(1);                                               // portal blocks
        b.put(DataUtils.blockToBytes(blockAt(3, 64, 1)));
        b.put(new byte[trailing]);

        final byte[] out = new byte[b.position()];
        b.rewind();
        b.get(out);
        return out;
    }

    private Stargate read()
    {
        final Stargate s = GateSerializer.parseVersionedData(version3Gate(), world, "old", null);
        assertNotNull(s, "the reader understood the record");
        return s;
    }

    /** Every block a version 3 gate is made of comes back where it was put. */
    @Test
    void aVersionThreeGateComesBackWithEveryBlockWhereItWas()
    {
        final Stargate s = read();

        assertEquals(3, s.getLoadedVersion(), "and knows which version it read");
        assertEquals("10,64,20", at(s.getGateDialLeverBlock()), "dial lever");
        assertEquals("11,64,20", at(s.getGateIrisLeverBlock()), "iris lever");
        assertEquals("12,64,20", at(s.getGateNameBlockHolder()), "name holder");
        assertEquals("13,64,20", at(s.getGateDialSignBlock()), "dial sign");
        assertEquals(List.of("1,64,1", "2,64,1"), allAt(s.getGateStructureBlocks()), "structure");
        assertEquals(List.of("3,64,1"), allAt(s.getGatePortalBlocks()), "portal");
    }

    /**
     * The two target ids are read as the ints version 3 wrote.
     *
     * <p>Read as longs, each would swallow the four bytes after it: the sign target would
     * come back as 41 shifted into the high word, and everything after would be misaligned.
     */
    @Test
    void aVersionThreeGateReadsItsTargetIdsAsInts()
    {
        final Stargate s = read();

        assertTrue(s.isGateSignPowered(), "sign powered");
        assertEquals(3, s.getGateDialSignIndex(), "dial sign index");
        assertEquals(41L, s.getGateTempSignTarget(), "temp sign target");
        assertTrue(s.isGateActive(), "active");
        assertEquals(42L, s.getGateTempTargetId(), "temp target id");
    }

    /** The settings that follow the ids, which only line up if the ids were the right width. */
    @Test
    void aVersionThreeGateComesBackWithItsSettings()
    {
        final Stargate s = read();

        assertEquals(BlockFace.EAST, s.getGateFacing(), "facing");
        assertEquals("letmein", s.getGateIrisDeactivationCode(), "iris code");
        assertTrue(s.isGateIrisActive(), "iris active");
        assertTrue(s.isGateIrisDefaultActive(), "and that is remembered as the default");
    }

    /** The arrival is lifted onto the stored block and turned to face out of the gate. */
    @Test
    void theArrivalIsLiftedAndTurnedToFaceTheGate()
    {
        final Location arrival = read().getGatePlayerTeleportLocation();

        assertEquals(70.0, arrival.getY(), 1.0e-9, "stored at 69, arrived at one above it");
        assertEquals(65.0, arrival.getX(), 1.0e-9);
        assertEquals(66.0, arrival.getZ(), 1.0e-9);
        assertEquals(270.0f, arrival.getYaw(), 1.0e-4f, "east, as the gate faces -- not the 33 the record stored");
        assertEquals(0.0f, arrival.getPitch(), 1.0e-4f, "and level -- not the 45 the record stored");
    }

    /**
     * Version 3 stores no lights, no light blocks and no minecart arrival, and none are invented.
     *
     * <p>Paired with the settings test: a reader that took a lights byte here would not reach
     * this point with the structure blocks intact.
     */
    @Test
    void aVersionThreeGateHasNoLightsAndNoMinecartArrival()
    {
        final Stargate s = read();

        assertFalse(s.isGateLightsActive(), "never stored, so off");
        assertTrue(s.getGateLightBlocks().isEmpty(), "no light waves");
        assertTrue(s.getGateWooshBlocks().isEmpty(), "no woosh waves");
        assertNull(s.getGateMinecartTeleportLocation(), "no minecart arrival point");
    }

    /**
     * A gate whose sign was not powered keeps no dial sign, though the slot is still read.
     *
     * <p>The slot holds a real block position here, so a reader that set it regardless of the
     * flag would come back with 13,64,20 rather than none.
     */
    @Test
    void anUnpoweredVersionThreeGateKeepsNoDialSign()
    {
        final Stargate s = GateSerializer.parseVersionedData(version3Gate(false, 41, 42, 0), world, "old", null);

        assertFalse(s.isGateSignPowered(), "the flag says unpowered");
        assertNull(s.getGateDialSignBlock(), "so the stored slot is not made its dial sign");
        assertEquals(3, s.getGateDialSignIndex(), "and the fields after the slot still line up");
    }

    /**
     * Negative ids keep their sign when the int widens to the long the gate holds.
     *
     * <p>An unsigned widening would turn -5 into 4294967291, a gate id nothing has.
     */
    @Test
    void negativeVersionThreeIdsKeepTheirSign()
    {
        final Stargate s = GateSerializer.parseVersionedData(version3Gate(true, -5, -7, 0), world, "old", null);

        assertEquals(-5L, s.getGateTempSignTarget(), "temp sign target");
        assertEquals(-7L, s.getGateTempTargetId(), "temp target id");
    }

    /**
     * Versions 3 to 5 have never warned about bytes left over, unlike the readers after them.
     *
     * <p>Kept that way on purpose: an old database with padding on each blob would otherwise log
     * a warning per gate on every load and every import.
     */
    @Test
    void trailingBytesAfterAVersionThreeGateAreNotWarnedAbout()
    {
        final Stargate s = GateSerializer.parseVersionedData(version3Gate(true, 41, 42, 3), world, "old", null);

        assertEquals(List.of("3,64,1"), allAt(s.getGatePortalBlocks()), "the record itself still reads");
        verify(plugin, never()).prettyLog(any(), contains("not all byte data was read"));
    }
}
