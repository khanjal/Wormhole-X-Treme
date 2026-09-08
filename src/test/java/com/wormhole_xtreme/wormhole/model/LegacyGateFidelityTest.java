package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
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

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.utils.DataUtils;
import com.wormhole_xtreme.wormhole.utils.WorldUtils;

/**
 * What a version 6 and a version 7 gate come back as.
 *
 * <p>These two readers exist to import databases written by older versions of the plugin, and
 * nothing has ever tested them. #77 said so when it split them out: "Versions 3 to 7 are
 * read-only paths for importing old databases and nothing tests them... Those readers want
 * real fixtures -- a hand-built buffer per version -- and that is its own piece of work,
 * since stargatetoBinary only emits v9 and cannot generate them." This is that work.
 *
 * <p>The buffers are built with DataUtils rather than hand-rolled bytes, the way
 * {@link LegacySaveVersionTest} builds its version 3 one: the point is to pin each reader's
 * field order and sizes, not to re-derive the encoding.
 *
 * <p>Version 6 and version 7 differ by exactly one field -- version 7 also stores a minecart
 * arrival point -- so they are built from one buffer writer with a flag, and the difference
 * is asserted rather than assumed.
 *
 * <p>One thing this cannot pin, said plainly: the fixture saves the redstone dial activator
 * as present and the sign activator as absent. A reader that ignored the *absent* flag is
 * caught, because a block would appear that should not. A reader that ignored the *present*
 * one is not, because the block it would set is the block that belongs there anyway. Catching
 * that needs a second record with the flags the other way round.
 */
class LegacyGateFidelityTest
{
    private static final int VERSION_BYTE = 1;
    private static final int BLOCK_BYTES = 12;
    private static final int LOCATION_BYTES = 32;

    private World world;

    @BeforeEach
    void setUp() throws Exception
    {
        final Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, mock(WormholeXTreme.class));

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
        final Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, null);
    }

    private Block blockAt(final int x, final int y, final int z)
    {
        return world.getBlockAt(x, y, z);
    }

    private static String at(final Block b)
    {
        return b == null ? "none" : b.getX() + "," + b.getY() + "," + b.getZ();
    }

    private static String at(final Location l)
    {
        return l == null ? "none" : l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }

    private static List<String> allAt(final List<Location> locations)
    {
        final List<String> out = new ArrayList<String>();
        for (final Location l : locations)
        {
            out.add(at(l));
        }
        return out;
    }

    /**
     * One gate, in the order these two readers read it.
     *
     * <p>Every value is distinguishable from every other, so a reader that puts one field
     * where another belongs cannot pass. There are two structure blocks and one portal block,
     * two light waves of different sizes and one woosh wave, so a list read into the wrong
     * place shows up as well.
     *
     * @param version
     *            6 or 7
     * @return the gate record
     */
    private byte[] legacyGate(final int version)
    {
        final byte[] facing = "NORTH".getBytes(StandardCharsets.UTF_8);
        final byte[] idc = "letmein".getBytes(StandardCharsets.UTF_8);

        final ByteBuffer b = ByteBuffer.allocate(1024);
        b.put((byte) version);
        b.put(DataUtils.blockToBytes(blockAt(10, 64, 20)));      // dial lever
        b.put(DataUtils.blockToBytes(blockAt(11, 64, 20)));      // iris lever
        b.put(DataUtils.blockToBytes(blockAt(12, 64, 20)));      // name holder
        b.put(DataUtils.locationToBytes(new Location(world, 65.0, 69.0, 66.0)));
        if (version >= 7)
        {
            b.put(DataUtils.locationToBytes(new Location(world, 75.0, 79.0, 76.0)));
        }

        b.put((byte) 1);                                          // sign powered
        b.put(DataUtils.blockToBytes(blockAt(13, 64, 20)));       // dial sign
        b.putInt(3);                                              // dial sign index
        b.putLong(41L);                                           // temp sign target
        b.put((byte) 1);                                          // active
        b.putLong(42L);                                           // temp target id

        b.putInt(facing.length);
        b.put(facing);
        b.putInt(idc.length);
        b.put(idc);
        b.put((byte) 1);                                          // iris active
        b.put((byte) 1);                                          // lights active

        b.put((byte) 1);                                          // has redstone dial activator
        b.put(DataUtils.blockToBytes(blockAt(14, 64, 20)));
        b.put((byte) 0);                                          // no redstone sign activator
        b.put(new byte[12]);

        b.putInt(2);                                              // structure blocks
        b.put(DataUtils.blockToBytes(blockAt(1, 64, 1)));
        b.put(DataUtils.blockToBytes(blockAt(2, 64, 1)));
        b.putInt(1);                                              // portal blocks
        b.put(DataUtils.blockToBytes(blockAt(3, 64, 1)));

        b.putInt(2);                                              // two light waves
        b.putInt(1);
        b.put(DataUtils.blockToBytes(blockAt(4, 64, 1)));
        b.putInt(2);
        b.put(DataUtils.blockToBytes(blockAt(5, 64, 1)));
        b.put(DataUtils.blockToBytes(blockAt(6, 64, 1)));

        b.putInt(1);                                              // one woosh wave
        b.putInt(3);
        b.put(DataUtils.blockToBytes(blockAt(7, 64, 1)));
        b.put(DataUtils.blockToBytes(blockAt(8, 64, 1)));
        b.put(DataUtils.blockToBytes(blockAt(9, 64, 1)));

        final byte[] out = new byte[b.position()];
        b.rewind();
        b.get(out);
        return out;
    }

    private Stargate read(final int version)
    {
        final Stargate s = GateSerializer.parseVersionedData(legacyGate(version), world, "old", null);
        assertNotNull(s, "a version " + version + " gate parses");
        return s;
    }

    /** Both readers keep the blocks that work the gate. */
    @Test
    void bothVersionsKeepTheirAnchorBlocks()
    {
        for (final int version : new int[] { 6, 7 })
        {
            final Stargate s = read(version);
            assertEquals("10,64,20", at(s.getGateDialLeverBlock()), "v" + version + " dial lever");
            assertEquals("11,64,20", at(s.getGateIrisLeverBlock()), "v" + version + " iris lever");
            assertEquals("12,64,20", at(s.getGateNameBlockHolder()), "v" + version + " name holder");
            assertEquals("13,64,20", at(s.getGateDialSignBlock()), "v" + version + " dial sign");
        }
    }

    /** And the redstone activator that is there, without inventing the one that is not. */
    @Test
    void bothVersionsKeepOnlyTheRedstoneBlockThatWasSaved()
    {
        for (final int version : new int[] { 6, 7 })
        {
            final Stargate s = read(version);
            assertEquals("14,64,20", at(s.getGateRedstoneDialActivationBlock()),
                "v" + version + " has a dial activator");
            assertNull(s.getGateRedstoneSignActivationBlock(),
                "v" + version + " has no sign activator, and none is invented");
        }
    }

    /** And the flags, the codes and the target ids. */
    @Test
    void bothVersionsKeepTheirSettings()
    {
        for (final int version : new int[] { 6, 7 })
        {
            final Stargate s = read(version);
            assertEquals(BlockFace.NORTH, s.getGateFacing(), "v" + version + " facing");
            assertEquals("letmein", s.getGateIrisDeactivationCode(), "v" + version + " iris code");
            assertTrue(s.isGateIrisActive(), "v" + version + " iris active");
            assertTrue(s.isGateIrisDefaultActive(), "v" + version + " iris default");
            assertTrue(s.isGateLightsActive(), "v" + version + " lights");
            assertTrue(s.isGateSignPowered(), "v" + version + " sign powered");
            assertEquals(3, s.getGateDialSignIndex(), "v" + version + " dial sign index");
            assertEquals(41L, s.getGateTempSignTarget(), "v" + version + " sign target id");
            assertEquals(42L, s.getGateTempTargetId(), "v" + version + " dial target id");
        }
    }

    /** And every block list, in its own list. */
    @Test
    void bothVersionsKeepEveryBlockListSeparate()
    {
        for (final int version : new int[] { 6, 7 })
        {
            final Stargate s = read(version);
            assertEquals(List.of("1,64,1", "2,64,1"), allAt(s.getGateStructureBlocks()),
                "v" + version + " structure blocks");
            assertEquals(List.of("3,64,1"), allAt(s.getGatePortalBlocks()),
                "v" + version + " portal blocks");

            assertEquals(2, s.getGateLightBlocks().size(), "v" + version + " light waves");
            assertEquals(List.of("4,64,1"), allAt(s.getGateLightBlocks().get(0)),
                "v" + version + " first light wave");
            assertEquals(List.of("5,64,1", "6,64,1"), allAt(s.getGateLightBlocks().get(1)),
                "v" + version + " second light wave");

            assertEquals(1, s.getGateWooshBlocks().size(), "v" + version + " woosh waves");
            assertEquals(List.of("7,64,1", "8,64,1", "9,64,1"), allAt(s.getGateWooshBlocks().get(0)),
                "v" + version + " the woosh wave");
        }
    }

    /**
     * The player arrives a block above what was saved, looking out of the gate.
     *
     * <p>What is stored is the block; what is wanted is where somebody stands on it.
     */
    @Test
    void bothVersionsStandThePlayerOnTheSavedBlock()
    {
        for (final int version : new int[] { 6, 7 })
        {
            final Stargate s = read(version);
            assertEquals("65,70,66", at(s.getGatePlayerTeleportLocation()),
                "v" + version + " arrival is a block up from the saved 69");
            assertEquals(WorldUtils.getDegreesFromBlockFace(BlockFace.NORTH).floatValue(),
                s.getGatePlayerTeleportLocation().getYaw(), 0.001f, "v" + version + " yaw");
            assertEquals(0.0f, s.getGatePlayerTeleportLocation().getPitch(), 0.001f,
                "v" + version + " pitch");
        }
    }

    /** Version 6 has no minecart arrival point of its own. */
    @Test
    void aVersion6GateHasNoMinecartArrivalPoint()
    {
        assertNull(read(6).getGateMinecartTeleportLocation(),
            "version 6 never stored one, and none is invented");
    }

    /**
     * Version 7 does, and it is read where it was written.
     *
     * <p>It is the one field the two versions differ by, so the whole rest of a version 7
     * record is offset by thirty-two bytes if this read is dropped -- the facing would come
     * back as an unreadable string long before anything subtle went wrong.
     */
    @Test
    void aVersion7GateKeepsItsMinecartArrivalPoint()
    {
        final Location minecart = read(7).getGateMinecartTeleportLocation();
        assertNotNull(minecart, "version 7 stored one");
        assertEquals("75,79,76", at(minecart), "read where it was written, unadjusted");
    }

    /**
     * Version 7 does not stand the minecart up the way it stands the player up.
     *
     * <p>Recorded rather than endorsed: the player's arrival gets a block added to its height
     * and a yaw from the facing, and the minecart's gets neither. Version 8 adjusts both. If
     * that is a defect it predates this test, and fixing it belongs with its own change.
     */
    @Test
    void aVersion7MinecartIsNotStoodUpTheWayThePlayerIs()
    {
        final Stargate s = read(7);
        assertEquals(79, s.getGateMinecartTeleportLocation().getBlockY(),
            "the saved height, with no block added");
        assertEquals(0.0f, s.getGateMinecartTeleportLocation().getYaw(), 0.001f,
            "and no yaw from the facing");
    }

    /** A gate that was not sign powered keeps no dial sign. */
    @Test
    void aGateThatIsNotSignPoweredKeepsNoSign()
    {
        // The sign-powered flag sits after the version byte, three blocks, and version 7's two
        // locations. Named rather than spelled as a sum, so it is one place to correct if the
        // fixture above ever changes shape.
        final int signPoweredFlag = VERSION_BYTE + (3 * BLOCK_BYTES) + (2 * LOCATION_BYTES);
        final byte[] saved = legacyGate(7);
        saved[signPoweredFlag] = 0;

        final Stargate s = GateSerializer.parseVersionedData(saved, world, "old", null);

        assertNotNull(s);
        assertFalse(s.isGateSignPowered(), "not sign powered");
        assertNull(s.getGateDialSignBlock(), "so no dial sign block is kept");
    }
}
