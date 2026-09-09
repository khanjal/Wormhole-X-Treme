package com.wormhole_xtreme.wormhole.command.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Slab;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.GateSpatialIndex;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.ring.RingManager;
import com.wormhole_xtreme.wormhole.model.ring.RingPattern;
import com.wormhole_xtreme.wormhole.model.ring.RingPermissions;
import com.wormhole_xtreme.wormhole.model.ring.RingYamlManager;
import com.wormhole_xtreme.wormhole.PluginForTests;

/**
 * Laying the first ring of a pair, and everything that refuses to let you.
 *
 * <p>{@code RingPairingTest} covers joining a second end to a waiting first. This covers getting
 * that far: the build permission, three ways a circle of slabs is refused by the detector, a
 * spot too close to another pair, a spot on top of a stargate, and the quota.
 *
 * <p>The quota is asked at the <em>first</em> end rather than the second, which is the decision
 * worth having a test for. Asked at the second, somebody would lay two complete circles of slabs
 * before being told they were never going to be allowed the pair.
 */
class RingCreationTest
{
    private static final String WORLD = "world";
    private static final String OWNER = "069a79f4-44e9-4726-a5be-fca90e38aaf5";
    private static final int RX = 100, RY = 64, RZ = 100;

    private Player builder;
    private World world;
    private final Map<String, Block> blocks = new HashMap<>();
    private MockedStatic<ConfigManager> config;
    private MockedStatic<RingYamlManager> yaml;

    @BeforeEach
    void setUp() throws Exception
    {
        RingManager.clear();
        GateSpatialIndex.clear();
        blocks.clear();
        // StargateManager.addBlockIndex logs what it indexed, so the gate-overlap test needs a
        // plugin to log through.
        PluginForTests.install();

        world = mock(World.class);
        when(world.getName()).thenReturn(WORLD);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(inv -> blockAt(
            inv.getArgument(0, Integer.class).intValue(),
            inv.getArgument(1, Integer.class).intValue(),
            inv.getArgument(2, Integer.class).intValue()));

        builder = mock(Player.class);
        when(builder.getName()).thenReturn("Justin");
        when(builder.getUniqueId()).thenReturn(UUID.fromString(OWNER));
        when(builder.getWorld()).thenReturn(world);
        when(builder.getLocation()).thenReturn(new Location(world, RX, RY, RZ));
        when(builder.isOp()).thenReturn(Boolean.FALSE);
        when(builder.hasPermission(anyString())).thenReturn(Boolean.FALSE);
        when(builder.hasPermission(RingPermissions.BUILD)).thenReturn(Boolean.TRUE);

        config = mockStatic(ConfigManager.class);
        config.when(ConfigManager::getRingReach).thenReturn(Integer.valueOf(5));
        config.when(ConfigManager::getRingDefaultLight).thenReturn(Material.GLOWSTONE);
        config.when(ConfigManager::getRingMinSeparation).thenReturn(Integer.valueOf(0));
        // Zero is how the config turns the quota off; the tests about it set their own.
        config.when(ConfigManager::getRingMaxPairsPerPlayer).thenReturn(Integer.valueOf(0));

        yaml = mockStatic(RingYamlManager.class);

        layRingAt(RX, RY, RZ);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        yaml.close();
        config.close();
        GateSpatialIndex.clear();
        RingManager.clear();
        PluginForTests.remove();
    }

    /** Air unless something has been laid there. */
    private Block blockAt(final int x, final int y, final int z)
    {
        return blocks.computeIfAbsent(x + "," + y + "," + z, key -> {
            final Block b = mock(Block.class);
            when(b.getType()).thenReturn(Material.AIR);
            when(b.getLocation()).thenReturn(new Location(world, x, y, z));
            return b;
        });
    }

    /**
     * Lays a complete circle of bottom slabs, which is what the detector reads as a ring.
     *
     * <p>Through the real {@code BukkitBlockProbe}, so the block has to answer both what it is
     * and which half of its cube it occupies -- a double slab fills the whole block and so
     * cannot say which surface it was laid against, which is the one thing being read.
     */
    private void layRingAt(final int ax, final int ay, final int az)
    {
        for (final RingPattern.Offset offset : RingPattern.ODD.getPerimeter())
        {
            final Block b = blockAt(ax + offset.getDx(), ay, az + offset.getDz());
            final Slab slab = mock(Slab.class);
            when(slab.getType()).thenReturn(Slab.Type.BOTTOM);
            when(b.getType()).thenReturn(Material.STONE_SLAB);
            when(b.getBlockData()).thenReturn(slab);
        }
    }

    private boolean create()
    {
        return new RingCommand().execute(builder, new String[] { "ring", "create" });
    }

    private static com.wormhole_xtreme.wormhole.model.ring.RingManager.PendingRing waiting()
    {
        return RingManager.getPending(UUID.fromString(OWNER));
    }

    /** A circle of slabs becomes the first end, held until its partner is laid. */
    @Test
    void aCircleOfSlabsBecomesTheFirstEndAndIsHeld()
    {
        assertTrue(create());

        assertNotNull(waiting(), "the end is held");
        assertEquals(WORLD, waiting().worldName(), "in the world it was laid in");
        yaml.verify(RingYamlManager::savePending);
        verify(builder).sendMessage(contains("First ring noted"));
    }

    /**
     * The slabs are left where they are until the pair is finished.
     *
     * <p>Taking them now would mean a crash or a restart between the two halves cost somebody a
     * circle of slabs for a ring that never existed. Leaving them costs nothing, because an
     * unpaired ring does not work anyway.
     */
    @Test
    void theSlabsStayPutUntilThePairIsFinished()
    {
        create();

        for (final RingPattern.Offset offset : RingPattern.ODD.getPerimeter())
        {
            verify(blockAt(RX + offset.getDx(), RY, RZ + offset.getDz()), never())
                .setType(org.mockito.ArgumentMatchers.any(Material.class),
                    org.mockito.ArgumentMatchers.anyBoolean());
        }
        verify(builder).sendMessage(contains("slabs stay put"));
    }

    /** Building rings at all needs the node. */
    @Test
    void buildingARingNeedsTheBuildNode()
    {
        when(builder.hasPermission(RingPermissions.BUILD)).thenReturn(Boolean.FALSE);

        assertTrue(create());

        assertNull(waiting(), "nothing was held");
        verify(builder).sendMessage(contains("may not build transport rings"));
    }

    /**
     * Standing nowhere near a circle of slabs says so, rather than something vaguer.
     *
     * <p>Each refusal gets its own sentence on purpose: telling somebody looking straight at
     * their ring that no ring was found would send them hunting the wrong problem entirely.
     */
    @Test
    void standingNowhereNearACircleSaysNoRingWasFound()
    {
        blocks.clear();

        assertTrue(create());

        assertNull(waiting());
        verify(builder).sendMessage(contains("No ring"));
    }

    /**
     * A circle built from two kinds of slab is refused, and told which problem it has.
     *
     * <p>Each refusal gets its own sentence on purpose. Telling somebody looking straight at
     * their ring that no ring was found would send them hunting the wrong problem entirely --
     * so the mixed-materials case has to say "more than one kind of slab" rather than falling
     * through to the generic answer.
     */
    @Test
    void aCircleOfTwoKindsOfSlabIsRefusedForBeingMixed()
    {
        final RingPattern.Offset odd = RingPattern.ODD.getPerimeter().iterator().next();
        final Block b = blockAt(RX + odd.getDx(), RY, RZ + odd.getDz());
        when(b.getType()).thenReturn(Material.OAK_SLAB);

        assertTrue(create());

        assertNull(waiting(), "nothing was held");
        verify(builder).sendMessage(contains("more than one kind of slab"));
    }

    /**
     * A circle whose middle is filled in is refused, and told why.
     *
     * <p>The middle is where people stand, so a filled circle is not a ring at all -- and the
     * mistake is easy to make, since a solid disc of slabs looks more like a pad than a ring
     * does.
     */
    @Test
    void aCircleWithItsMiddleFilledInIsRefused()
    {
        final Block middle = blockAt(RX, RY, RZ);
        final Slab slab = mock(Slab.class);
        when(slab.getType()).thenReturn(Slab.Type.BOTTOM);
        when(middle.getType()).thenReturn(Material.STONE_SLAB);
        when(middle.getBlockData()).thenReturn(slab);

        assertTrue(create());

        assertNull(waiting(), "nothing was held");
        verify(builder).sendMessage(contains("filled in"));
    }

    /**
     * A ring laid too near an existing pair is refused before anything is held.
     *
     * <p>Separate from overlapping: two rings that merely sit close enough to confuse whoever
     * walks between them are refused on a configured distance, and told to move rather than
     * told they overlap.
     */
    @Test
    void aRingLaidTooNearAnExistingPairIsRefused()
    {
        config.when(ConfigManager::getRingMinSeparation).thenReturn(Integer.valueOf(50));
        final com.wormhole_xtreme.wormhole.model.ring.Ring near =
            new com.wormhole_xtreme.wormhole.model.ring.Ring(RX + 10, RY, RZ + 10,
                RingPattern.ODD, com.wormhole_xtreme.wormhole.model.ring.RingOrientation.FLOOR,
                Material.STONE_SLAB, Material.GLOWSTONE);
        final com.wormhole_xtreme.wormhole.model.ring.Ring far =
            new com.wormhole_xtreme.wormhole.model.ring.Ring(2000, RY, 2000,
                RingPattern.ODD, com.wormhole_xtreme.wormhole.model.ring.RingOrientation.FLOOR,
                Material.STONE_SLAB, Material.GLOWSTONE);
        final com.wormhole_xtreme.wormhole.model.ring.RingPair neighbour =
            new com.wormhole_xtreme.wormhole.model.ring.RingPair("yyyy0008", WORLD, near, far);
        neighbour.setOwner(UUID.randomUUID().toString());
        RingManager.addPair(neighbour, 5);

        assertTrue(create());

        assertNull(waiting(), "nothing was held");
        verify(builder).sendMessage(contains("another ring close by"));
    }

    /**
     * The quota is asked at the first end, not the second.
     *
     * <p>Asked at the second, somebody would lay two complete circles of slabs before being
     * told they were never going to be allowed the pair.
     */
    @Test
    void theQuotaIsAskedBeforeTheFirstEndIsEvenHeld()
    {
        config.when(ConfigManager::getRingMaxPairsPerPlayer).thenReturn(Integer.valueOf(1));
        givenTheBuilderAlreadyOwnsAPair();

        assertTrue(create());

        assertNull(waiting(), "nothing is held, so nothing is owed a second circle");
        verify(builder).sendMessage(contains("which is the limit"));
        yaml.verify(RingYamlManager::savePending, never());
    }

    /** The unlimited node lifts the quota. */
    @Test
    void theUnlimitedNodeLiftsTheQuota()
    {
        config.when(ConfigManager::getRingMaxPairsPerPlayer).thenReturn(Integer.valueOf(1));
        givenTheBuilderAlreadyOwnsAPair();
        when(builder.hasPermission(RingPermissions.UNLIMITED)).thenReturn(Boolean.TRUE);

        assertTrue(create());

        assertNotNull(waiting(), "the limit does not apply to them");
    }

    /** A quota of zero is no quota at all. */
    @Test
    void aQuotaOfZeroIsNoQuota()
    {
        config.when(ConfigManager::getRingMaxPairsPerPlayer).thenReturn(Integer.valueOf(0));
        givenTheBuilderAlreadyOwnsAPair();

        assertTrue(create());

        assertNotNull(waiting(), "zero turns the limit off");
    }

    /**
     * A ring may not be laid over a stargate, inside or out.
     *
     * <p>Gates and rings both act on the move path and both animate their own blocks, so they
     * are never allowed to share ground. Gates were built first, so rings give way. The
     * interior counts as well as the circle itself -- a gate standing in the middle of a ring
     * is still two things fighting over the same blocks.
     */
    @Test
    void aRingMayNotBeLaidOverAStargate()
    {
        final Block gateBlock = blockAt(RX, RY, RZ);
        final Stargate gate = new Stargate();
        gate.setGateName("inthemiddle");
        StargateManager.addBlockIndex(gateBlock, gate);
        try
        {
            assertTrue(create());

            assertNull(waiting(), "nothing was held");
            verify(builder).sendMessage(contains("overlaps a stargate"));
        }
        finally
        {
            StargateManager.removeBlockIndex(gateBlock);
        }
    }

    /** Another pair already to the builder's name, so the quota has something to count. */
    private void givenTheBuilderAlreadyOwnsAPair()
    {
        final com.wormhole_xtreme.wormhole.model.ring.Ring a =
            new com.wormhole_xtreme.wormhole.model.ring.Ring(900, 64, 900, RingPattern.ODD,
                com.wormhole_xtreme.wormhole.model.ring.RingOrientation.FLOOR,
                Material.STONE_SLAB, Material.GLOWSTONE);
        final com.wormhole_xtreme.wormhole.model.ring.Ring b =
            new com.wormhole_xtreme.wormhole.model.ring.Ring(1100, 64, 1100, RingPattern.ODD,
                com.wormhole_xtreme.wormhole.model.ring.RingOrientation.FLOOR,
                Material.STONE_SLAB, Material.GLOWSTONE);
        final com.wormhole_xtreme.wormhole.model.ring.RingPair theirs =
            new com.wormhole_xtreme.wormhole.model.ring.RingPair("zzzz0009", WORLD, a, b);
        theirs.setOwner(OWNER);
        RingManager.addPair(theirs, 5);
    }
}
