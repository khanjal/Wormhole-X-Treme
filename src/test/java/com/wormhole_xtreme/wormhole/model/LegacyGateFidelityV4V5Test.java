package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
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

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.utils.DataUtils;
import com.wormhole_xtreme.wormhole.PluginTestSupport;

/**
 * What a version 4 and a version 5 gate come back as.
 *
 * <p>{@link LegacyGateFidelityTest} did this for versions 6 and 7 and said what was left:
 * "Versions 3 to 7 are read-only paths for importing old databases and nothing tests them...
 * Those readers want real fixtures -- a hand-built buffer per version." Version 3 has
 * {@code LegacySaveVersionTest}; these two had nothing at all.
 *
 * <p>The two differ by two things rather than one. Version 5 stores whether the gate's lights
 * were on, and one wave of light blocks; version 4 stores neither. Both are read from the same
 * builder with a flag so the difference is asserted rather than assumed.
 *
 * <p>A wrong answer here is not a crash. It is somebody's gate coming back from a decade-old
 * database with its exit a block out, or facing the wrong way, or its lights in the wrong wave
 * -- and nothing to say so.
 */
class LegacyGateFidelityV4V5Test
{
    private World world;

    @BeforeEach
    void setUp() throws Exception
    {
        setPlugin(mock(WormholeXTreme.class));

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
        setPlugin(null);
    }

    private static void setPlugin(final WormholeXTreme value) throws Exception
    {
        PluginTestSupport.install(value);
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
     * <p>Every value is distinguishable from every other, so a reader that puts one field where
     * another belongs cannot pass. Two structure blocks and one portal block, so a list read
     * into the wrong place shows up too.
     *
     * @param version
     *            4 or 5
     * @return the gate record
     */
    private byte[] legacyGate(final int version)
    {
        return legacyGate(version, true);
    }

    /**
     * The same record, with the lights flag set either way.
     *
     * <p>Version 5 stores the iris flag and the lights flag as two adjacent bytes. With both
     * holding 1 a reader that took them in each other's place would look identical, which is
     * the limitation {@link LegacyGateFidelityTest} noted about its own activator flags and
     * left for somebody else. This is that second record.
     *
     * @param version
     *            4 or 5
     * @param lightsOn
     *            what the lights byte says, for version 5; version 4 has no such byte
     * @return the gate record
     */
    private byte[] legacyGate(final int version, final boolean lightsOn)
    {
        final byte[] facing = "NORTH".getBytes(StandardCharsets.UTF_8);
        final byte[] idc = "letmein".getBytes(StandardCharsets.UTF_8);

        final ByteBuffer b = ByteBuffer.allocate(1024);
        b.put((byte) version);
        b.put(DataUtils.blockToBytes(blockAt(10, 64, 20)));       // dial lever
        b.put(DataUtils.blockToBytes(blockAt(11, 64, 20)));       // iris lever
        b.put(DataUtils.blockToBytes(blockAt(12, 64, 20)));       // name holder
        // Stored facing somewhere else entirely: the reader overwrites both, and a stored
        // zero could not show that it had.
        b.put(DataUtils.locationToBytes(new Location(world, 65.0, 69.0, 66.0, 33.0f, 45.0f)));

        b.put((byte) 1);                                           // sign powered
        b.put(DataUtils.blockToBytes(blockAt(13, 64, 20)));        // dial sign
        b.putInt(3);                                               // dial sign index
        b.putLong(41L);                                            // temp sign target
        b.put((byte) 1);                                           // active
        b.putLong(42L);                                            // temp target id

        b.putInt(facing.length);
        b.put(facing);
        b.putInt(idc.length);
        b.put(idc);
        b.put((byte) 1);                                           // iris active
        if (version >= 5)
        {
            b.put((byte) (lightsOn ? 1 : 0));                      // lights active
        }

        b.putInt(2);                                               // structure blocks
        b.put(DataUtils.blockToBytes(blockAt(1, 64, 1)));
        b.put(DataUtils.blockToBytes(blockAt(2, 64, 1)));
        b.putInt(1);                                               // portal blocks
        b.put(DataUtils.blockToBytes(blockAt(3, 64, 1)));

        if (version >= 5)
        {
            b.putInt(2);                                           // one light wave, two blocks
            b.put(DataUtils.blockToBytes(blockAt(4, 64, 1)));
            b.put(DataUtils.blockToBytes(blockAt(5, 64, 1)));
        }

        final byte[] out = new byte[b.position()];
        b.rewind();
        b.get(out);
        return out;
    }

    private Stargate read(final int version)
    {
        return GateSerializer.parseVersionedData(legacyGate(version), world, "old", null);
    }

    /** Every block a version 4 gate is made of comes back where it was put. */
    @Test
    void aVersionFourGateComesBackWithEveryBlockWhereItWas()
    {
        final Stargate s = read(4);

        assertNotNull(s, "the reader understood the record");
        assertEquals(4, s.getLoadedVersion(), "and knows which version it read");
        assertEquals("10,64,20", at(s.getGateDialLeverBlock()), "dial lever");
        assertEquals("11,64,20", at(s.getGateIrisLeverBlock()), "iris lever");
        assertEquals("12,64,20", at(s.getGateNameBlockHolder()), "name holder");
        assertEquals("13,64,20", at(s.getGateDialSignBlock()), "dial sign");
        assertEquals(List.of("1,64,1", "2,64,1"), allAt(s.getGateStructureBlocks()), "structure");
        assertEquals(List.of("3,64,1"), allAt(s.getGatePortalBlocks()), "portal");
    }

    /** And every flag and number it carries. */
    @Test
    void aVersionFourGateComesBackWithEveryFlagItCarried()
    {
        final Stargate s = read(4);

        assertTrue(s.isGateSignPowered(), "sign powered");
        assertEquals(3, s.getGateDialSignIndex(), "dial sign index");
        assertEquals(41L, s.getGateTempSignTarget(), "temp sign target");
        assertTrue(s.isGateActive(), "active");
        assertEquals(42L, s.getGateTempTargetId(), "temp target id");
        assertEquals(BlockFace.NORTH, s.getGateFacing(), "facing");
        assertEquals("letmein", s.getGateIrisDeactivationCode(), "iris code");
        assertTrue(s.isGateIrisActive(), "iris active");
        assertTrue(s.isGateIrisDefaultActive(), "and that is remembered as the default");
    }

    /**
     * The stored exit is the block; the arrival is one above it, facing the way the gate does.
     *
     * <p>All three adjustments happen on the way in rather than being stored. A reader that
     * dropped the {@code +1} lands travellers inside the floor they should be standing on, and
     * one that dropped the yaw faces them at whatever the last thing to touch that location
     * left behind.
     */
    @Test
    void theArrivalIsLiftedAboveTheStoredBlockAndTurnedToFaceTheGate()
    {
        final Stargate s = read(4);

        assertEquals(70.0, s.getGatePlayerTeleportLocation().getY(), 1.0e-9,
            "stored at 69, arrived at one above it");
        assertEquals(65.0, s.getGatePlayerTeleportLocation().getX(), 1.0e-9);
        assertEquals(66.0, s.getGatePlayerTeleportLocation().getZ(), 1.0e-9);
        assertEquals(180.0f, s.getGatePlayerTeleportLocation().getYaw(), 1.0e-4f,
            "north, as the gate faces -- not the 33 the record stored");
        assertEquals(0.0f, s.getGatePlayerTeleportLocation().getPitch(), 1.0e-4f,
            "and level -- not the 45 the record stored");
    }

    /** A version 5 gate reads everything a version 4 one does, in the same places. */
    @Test
    void aVersionFiveGateReadsEverythingAVersionFourOneDoes()
    {
        final Stargate s = read(5);

        assertEquals(5, s.getLoadedVersion());
        assertEquals("10,64,20", at(s.getGateDialLeverBlock()), "dial lever");
        assertEquals("13,64,20", at(s.getGateDialSignBlock()), "dial sign");
        assertEquals(BlockFace.NORTH, s.getGateFacing(), "facing");
        assertEquals("letmein", s.getGateIrisDeactivationCode(), "iris code");
        assertEquals(List.of("1,64,1", "2,64,1"), allAt(s.getGateStructureBlocks()), "structure");
        assertEquals(List.of("3,64,1"), allAt(s.getGatePortalBlocks()), "portal");
    }

    /**
     * Version 5 is the version that started remembering whether the lights were on.
     *
     * <p>Version 4 stores no such byte, so a version 4 gate comes back with its lights off
     * whatever they were doing when it was saved. That is the format's limit rather than a
     * fault, and worth stating so nobody 'fixes' the version 4 reader by inventing one.
     */
    @Test
    void onlyVersionFiveRemembersWhetherTheLightsWereOn()
    {
        assertTrue(read(5).isGateLightsActive(), "version 5 stored it, and it was on");
        assertFalse(read(4).isGateLightsActive(), "version 4 never stored it at all");
    }

    /**
     * Version 5's light blocks land in the second wave, not the first.
     *
     * <p>The reader pads the list to two and fills index 1, leaving index 0 empty. The
     * animation walks the waves in order, so a wave read into the wrong slot is a gate whose
     * lights come on in the wrong sequence -- visible in game, and nothing a stack trace would
     * ever mention.
     */
    @Test
    void versionFivesLightsLandInTheSecondWave()
    {
        final Stargate s = read(5);

        assertEquals(2, s.getGateLightBlocks().size(), "padded to two waves");
        assertNull(s.getGateLightBlocks().get(0), "the first is left empty");
        assertEquals(List.of("4,64,1", "5,64,1"), allAt(s.getGateLightBlocks().get(1)),
            "and the stored wave is the second");
    }

    /**
     * A version 5 gate's arrival is lifted and turned the same way a version 4 one is.
     *
     * <p>The two readers hold their own copy of this, so covering it once covers one of them.
     * Both are the same three lines and both could stop being.
     */
    @Test
    void aVersionFiveArrivalIsLiftedAndTurnedTheSameWay()
    {
        final Stargate s = read(5);

        assertEquals(70.0, s.getGatePlayerTeleportLocation().getY(), 1.0e-9,
            "stored at 69, arrived at one above it");
        assertEquals(180.0f, s.getGatePlayerTeleportLocation().getYaw(), 1.0e-4f,
            "north, as the gate faces -- not the 33 the record stored");
        assertEquals(0.0f, s.getGatePlayerTeleportLocation().getPitch(), 1.0e-4f,
            "and level -- not the 45 the record stored");
    }

    /**
     * The iris flag and the lights flag are not read in each other's place.
     *
     * <p>They are adjacent bytes, so a record with both set to 1 cannot tell the two apart.
     * This one has the iris on and the lights off, which only the right order produces.
     */
    @Test
    void theIrisAndLightsFlagsAreNotReadInEachOthersPlace()
    {
        final Stargate s = GateSerializer.parseVersionedData(
            legacyGate(5, false), world, "old", null);

        assertTrue(s.isGateIrisActive(), "the iris byte says on");
        assertFalse(s.isGateLightsActive(), "and the lights byte, right after it, says off");
    }

    /** A version 4 gate has no light blocks at all, rather than an empty wave. */
    @Test
    void aVersionFourGateHasNoLightWavesAtAll()
    {
        assertTrue(read(4).getGateLightBlocks().isEmpty(),
            "version 4 stored none, so none are invented");
    }

    /** A version this reader has never heard of is refused rather than guessed at. */
    @Test
    void aVersionNobodyRecognisesIsRefused()
    {
        final byte[] stored = legacyGate(4);
        stored[0] = (byte) 99;

        assertNull(GateSerializer.parseVersionedData(stored, world, "old", null),
            "a record from the future is not read as one from the past");
    }
}
