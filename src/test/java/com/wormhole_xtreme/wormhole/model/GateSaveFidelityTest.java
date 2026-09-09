package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.utils.WorldUtils;
import com.wormhole_xtreme.wormhole.PluginTestSupport;

/**
 * Everything a gate saves, and gets back.
 *
 * <p>{@link GateSerializerTest} round-trips a gate with a name, a dial lever and a teleport
 * point. That leaves most of the format unwatched: eight separate mutations of the version 8
 * and 9 reader -- dropping the dial lever, dropping the dial sign block, putting the
 * structure blocks into the portal list, taking the block off the arrival height -- all
 * survived the whole suite.
 *
 * <p>This is the format every gate on a running server is saved in today. A field the reader
 * quietly stops keeping comes back as a default, and the gate is subtly wrong with nothing
 * logged: an iris that will not close, a dial sign on the wrong block, a light wave missing
 * from the animation.
 *
 * <p>So one gate is built with every field set to something distinguishable, put through the
 * writer and the reader, and read back field by field. Structure and portal blocks get
 * different coordinates, and the light and woosh waves different shapes, so a list written
 * into the wrong place cannot pass.
 */
class GateSaveFidelityTest
{
    private World world;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));

        world = mock(World.class);
        when(world.getName()).thenReturn("gw");
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(inv ->
            blockAt(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PluginTestSupport.remove();
    }

    private Block blockAt(final int x, final int y, final int z)
    {
        final Block b = mock(Block.class);
        when(b.getX()).thenReturn(x);
        when(b.getY()).thenReturn(y);
        when(b.getZ()).thenReturn(z);
        when(b.getWorld()).thenReturn(world);
        when(b.getLocation()).thenReturn(new Location(world, x, y, z));
        return b;
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
        final List<String> out = new ArrayList<>();
        for (final Location l : locations)
        {
            out.add(at(l));
        }
        return out;
    }

    /** A gate with every field the writer knows about set to something distinguishable. */
    private Stargate fullyDressedGate()
    {
        final Stargate s = new Stargate();
        s.setGateName("fidelity");
        s.setGateWorld(world);
        s.setGateFacing(BlockFace.NORTH);

        s.setGateDialLeverBlock(blockAt(10, 64, 20));
        s.setGateIrisLeverBlock(blockAt(11, 64, 20));
        s.setGateNameBlockHolder(blockAt(12, 64, 20));
        s.setGatePlayerTeleportLocation(new Location(world, 65.0, 70.0, 66.0));
        s.setGateMinecartTeleportLocation(new Location(world, 75.0, 80.0, 76.0));

        s.setGateSignPowered(true);
        s.setGateDialSignBlock(blockAt(13, 64, 20));
        s.setGateDialSignIndex(3);

        s.setGateIrisDeactivationCode("hunter2");
        s.setGateIrisActive(true);
        s.setGateLightsActive(true);

        s.setGateRedstoneDialActivationBlock(blockAt(14, 64, 20));
        s.setGateRedstoneSignActivationBlock(blockAt(15, 64, 20));
        s.setGateRedstoneGateActivatedBlock(blockAt(16, 64, 20));
        s.setGateRedstonePowered(true);

        s.setGateCustom(true);
        s.setGateCustomStructureMaterial(Material.QUARTZ_BLOCK);
        s.setGateCustomPortalMaterial(Material.WATER);
        s.setGateCustomLightMaterial(Material.GLOWSTONE);
        s.setGateCustomIrisMaterial(Material.IRON_BLOCK);
        s.setGateCustomWooshTicks(7);
        s.setGateCustomLightTicks(9);
        s.setGateCustomWooshDepth(4);

        s.getGateStructureBlocks().add(new Location(world, 1, 64, 1));
        s.getGateStructureBlocks().add(new Location(world, 2, 64, 1));
        s.getGatePortalBlocks().add(new Location(world, 3, 64, 1));

        final List<Location> lightOne = new ArrayList<>();
        lightOne.add(new Location(world, 4, 64, 1));
        final List<Location> lightTwo = new ArrayList<>();
        lightTwo.add(new Location(world, 5, 64, 1));
        lightTwo.add(new Location(world, 6, 64, 1));
        s.getGateLightBlocks().add(lightOne);
        s.getGateLightBlocks().add(lightTwo);

        final List<Location> woosh = new ArrayList<>();
        woosh.add(new Location(world, 7, 64, 1));
        woosh.add(new Location(world, 8, 64, 1));
        woosh.add(new Location(world, 9, 64, 1));
        s.getGateWooshBlocks().add(woosh);

        return s;
    }

    private Stargate roundTrip(final Stargate s)
    {
        final byte[] data = GateSerializer.stargateToBinary(s);
        assertNotNull(data, "the gate serialised");
        final Stargate back = GateSerializer.parseVersionedData(data, world, s.getGateName(), null);
        assertNotNull(back, "and parsed back");
        return back;
    }

    /** The blocks that work the gate come back on the blocks they were on. */
    @Test
    void everyAnchorBlockSurvives()
    {
        final Stargate before = fullyDressedGate();
        final Stargate after = roundTrip(before);

        assertEquals(at(before.getGateDialLeverBlock()), at(after.getGateDialLeverBlock()), "dial lever");
        assertEquals(at(before.getGateIrisLeverBlock()), at(after.getGateIrisLeverBlock()), "iris lever");
        assertEquals(at(before.getGateNameBlockHolder()), at(after.getGateNameBlockHolder()), "name holder");
        assertEquals(at(before.getGateDialSignBlock()), at(after.getGateDialSignBlock()), "dial sign");
    }

    /** So do the three redstone activators, each on its own block. */
    @Test
    void everyRedstoneBlockSurvives()
    {
        final Stargate before = fullyDressedGate();
        final Stargate after = roundTrip(before);

        assertEquals(at(before.getGateRedstoneDialActivationBlock()),
            at(after.getGateRedstoneDialActivationBlock()), "dial activation");
        assertEquals(at(before.getGateRedstoneSignActivationBlock()),
            at(after.getGateRedstoneSignActivationBlock()), "sign activation");
        assertEquals(at(before.getGateRedstoneGateActivatedBlock()),
            at(after.getGateRedstoneGateActivatedBlock()), "gate activated");
        assertTrue(after.isGateRedstonePowered(), "and the flag itself");
    }

    /**
     * The arrival point comes back where it was, at the height a player stands.
     *
     * <p>The writer takes a block off the height and the reader puts it back, so a gate whose
     * reader stopped adding it would drop every arrival one block into the floor.
     */
    @Test
    void theArrivalPointSurvivesTheHeightAdjustment()
    {
        final Stargate before = fullyDressedGate();
        final Stargate after = roundTrip(before);

        assertEquals(at(before.getGatePlayerTeleportLocation()),
            at(after.getGatePlayerTeleportLocation()), "the player arrives where they left");
        assertEquals(at(before.getGateMinecartTeleportLocation()),
            at(after.getGateMinecartTeleportLocation()), "and so does a minecart");
    }

    /** An arrival faces out of the gate, however the location was saved. */
    @Test
    void anArrivalFacesOutOfTheGate()
    {
        final Stargate before = fullyDressedGate();
        before.getGatePlayerTeleportLocation().setYaw(123.0f);
        before.getGatePlayerTeleportLocation().setPitch(45.0f);

        final Stargate after = roundTrip(before);

        assertEquals(WorldUtils.getDegreesFromBlockFace(BlockFace.NORTH).floatValue(),
            after.getGatePlayerTeleportLocation().getYaw(), 0.001f, "yaw comes from the facing");
        assertEquals(0.0f, after.getGatePlayerTeleportLocation().getPitch(), 0.001f,
            "and nobody arrives looking at their feet");
    }

    /** The flags and the codes. */
    @Test
    void theGatesOwnSettingsSurvive()
    {
        final Stargate before = fullyDressedGate();
        final Stargate after = roundTrip(before);

        assertEquals(BlockFace.NORTH, after.getGateFacing(), "facing");
        assertEquals("hunter2", after.getGateIrisDeactivationCode(), "iris code");
        assertTrue(after.isGateIrisActive(), "iris active");
        assertTrue(after.isGateLightsActive(), "lights active");
        assertTrue(after.isGateSignPowered(), "sign powered");
        assertEquals(3, after.getGateDialSignIndex(), "dial sign index");
    }

    /**
     * An iris that was closed when the server stopped is closed by default when it starts.
     *
     * <p>Not a copy of the saved flag for its own sake: the default is what the gate returns
     * to after every use, so losing it turns a gate that was left shut into one that opens.
     */
    @Test
    void aClosedIrisComesBackClosedByDefault()
    {
        final Stargate closed = fullyDressedGate();
        assertTrue(roundTrip(closed).isGateIrisDefaultActive(), "a closed iris defaults closed");

        final Stargate open = fullyDressedGate();
        open.setGateIrisActive(false);
        assertFalse(roundTrip(open).isGateIrisDefaultActive(), "and an open one defaults open");
    }

    /** The material overrides and the animation timings. */
    @Test
    void theCustomMaterialsAndTimingsSurvive()
    {
        final Stargate before = fullyDressedGate();
        final Stargate after = roundTrip(before);

        assertTrue(after.isGateCustom(), "custom");
        assertEquals(Material.QUARTZ_BLOCK, after.getGateCustomStructureMaterial(), "structure");
        assertEquals(Material.WATER, after.getGateCustomPortalMaterial(), "portal");
        assertEquals(Material.GLOWSTONE, after.getGateCustomLightMaterial(), "light");
        assertEquals(Material.IRON_BLOCK, after.getGateCustomIrisMaterial(), "iris");
        assertEquals(7, after.getGateCustomWooshTicks(), "woosh ticks");
        assertEquals(9, after.getGateCustomLightTicks(), "light ticks");
        assertEquals(4, after.getGateCustomWooshDepth(), "woosh depth");
    }

    /**
     * The squared depth is derived on load, not stored.
     *
     * <p>It is what the woosh actually compares against, so a gate whose reader stopped
     * squaring it would woosh to the wrong distance with the right number in its file.
     */
    @Test
    void theWooshDepthIsSquaredOnTheWayIn()
    {
        assertEquals(16, roundTrip(fullyDressedGate()).getGateCustomWooshDepthSquared(),
            "four deep is sixteen squared");

        final Stargate unset = fullyDressedGate();
        unset.setGateCustomWooshDepth(-1);
        assertEquals(-1, roundTrip(unset).getGateCustomWooshDepthSquared(),
            "and a gate with no depth of its own stays at -1");
    }

    /**
     * Every block list comes back in its own list.
     *
     * <p>The four lists are written one after another with only their counts to separate
     * them, so a reader that put one into the wrong list would still consume the buffer
     * exactly and leave nothing to warn about.
     */
    @Test
    void everyBlockListComesBackWhereItBelongs()
    {
        final Stargate before = fullyDressedGate();
        final Stargate after = roundTrip(before);

        assertEquals(allAt(before.getGateStructureBlocks()), allAt(after.getGateStructureBlocks()),
            "structure blocks");
        assertEquals(allAt(before.getGatePortalBlocks()), allAt(after.getGatePortalBlocks()),
            "portal blocks");
    }

    /** And so does every wave, in its own layer. */
    @Test
    void everyWaveComesBackInItsOwnLayer()
    {
        final Stargate before = fullyDressedGate();
        final Stargate after = roundTrip(before);

        assertEquals(2, after.getGateLightBlocks().size(), "two light waves");
        assertEquals(allAt(before.getGateLightBlocks().get(0)), allAt(after.getGateLightBlocks().get(0)),
            "the first light wave");
        assertEquals(allAt(before.getGateLightBlocks().get(1)), allAt(after.getGateLightBlocks().get(1)),
            "the second, which is a different size");

        assertEquals(1, after.getGateWooshBlocks().size(), "one woosh wave");
        assertEquals(allAt(before.getGateWooshBlocks().get(0)), allAt(after.getGateWooshBlocks().get(0)),
            "and everything in it");
    }

    /** A gate with nothing optional set still reads back without inventing blocks. */
    @Test
    void theOptionalBlocksStayAbsentWhenThereAreNone()
    {
        final Stargate bare = new Stargate();
        bare.setGateName("bare");
        bare.setGateWorld(world);
        bare.setGateFacing(BlockFace.SOUTH);
        bare.setGateDialLeverBlock(blockAt(10, 64, 20));
        bare.setGatePlayerTeleportLocation(new Location(world, 65.0, 70.0, 66.0));

        final Stargate after = roundTrip(bare);

        assertNull(after.getGateDialSignBlock(), "no dial sign");
        assertNull(after.getGateRedstoneDialActivationBlock(), "no redstone dial activator");
        assertNull(after.getGateRedstoneSignActivationBlock(), "no redstone sign activator");
        assertNull(after.getGateRedstoneGateActivatedBlock(), "no redstone gate activator");
        assertFalse(after.isGateSignPowered(), "and it is not sign powered");
    }
}
