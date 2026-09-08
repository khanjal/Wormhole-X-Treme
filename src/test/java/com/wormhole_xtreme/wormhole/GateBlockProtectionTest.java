package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.model.GateSpatialIndex;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * What keeps a gate's blocks safe from the world around them.
 *
 * <p>Five event handlers, all of them uncovered: fire spreading to a gate, a block catching
 * alight near one, liquid flowing into or out of one, and a player hitting one. They are the
 * whole of this plugin's answer to somebody -- or something -- taking a gate apart, and between
 * them they were the largest block of untested behaviour left in the tree.
 *
 * <p>A lava gate is the awkward case and the reason two of these exist. Its portal is lava, and
 * lava sets fire to what it is near, so an open one would quietly burn down whatever was built
 * around it. The protection is a radius, not a block list, which is why these rules are about
 * distance rather than membership.
 */
class GateBlockProtectionTest
{
    private static final String WORLD = "world";
    private static final int GX = 100, GY = 64, GZ = 100;

    private World world;
    private Stargate gate;
    private Block gateBlock;
    private Player player;

    @BeforeEach
    void setUp() throws Exception
    {
        GateSpatialIndex.clear();
        set("thisPlugin", mock(WormholeXTreme.class));

        world = mock(World.class);
        when(world.getName()).thenReturn(WORLD);

        gateBlock = blockAt(GX, GY, GZ, Material.LAVA);

        gate = new Stargate();
        gate.setGateName("lavagate");
        gate.setGateActive(true);
        // A lava portal, set as a per-gate override so no shape or palette is needed.
        gate.setGateCustom(true);
        gate.setGateCustomPortalMaterial(Material.LAVA);
        StargateManager.addBlockIndex(gateBlock, gate);
        // The index answers "which gate is near here"; the distance is then measured against
        // the gate's own structure blocks, so a gate that is only indexed reads as infinitely
        // far from everything and protects nothing.
        gate.getGateStructureBlocks().add(new Location(world, GX, GY, GZ));

        player = mock(Player.class);
        when(player.getName()).thenReturn("griefer");
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOp()).thenReturn(Boolean.FALSE);
        when(player.hasPermission(anyString())).thenReturn(Boolean.FALSE);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        StargateManager.removeBlockIndex(gateBlock);
        GateSpatialIndex.clear();
        set("thisPlugin", null);
    }

    private static void set(final String name, final Object value) throws Exception
    {
        final Field f = WormholeXTreme.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(null, value);
    }

    private Block blockAt(final int x, final int y, final int z, final Material type)
    {
        final Block b = mock(Block.class);
        when(b.getLocation()).thenReturn(new Location(world, x, y, z));
        when(b.getWorld()).thenReturn(world);
        when(b.getType()).thenReturn(type);
        when(b.getX()).thenReturn(Integer.valueOf(x));
        when(b.getY()).thenReturn(Integer.valueOf(y));
        when(b.getZ()).thenReturn(Integer.valueOf(z));
        return b;
    }

    private static boolean ignited(final Block block)
    {
        final BlockIgniteEvent event = new BlockIgniteEvent(block,
            BlockIgniteEvent.IgniteCause.LAVA, block);
        new WormholeXTremeBlockListener().onBlockIgnite(event);
        return event.isCancelled();
    }

    private static boolean burned(final Block block, final Block by)
    {
        final BlockBurnEvent event = new BlockBurnEvent(block, by);
        new WormholeXTremeBlockListener().onBlockBurn(event);
        return event.isCancelled();
    }

    /**
     * A block two away from an open lava gate does not catch light.
     *
     * <p>The gate's own portal is lava, so without this an open gate sets fire to whatever
     * somebody built beside it -- and it is the plugin that put the lava there.
     */
    @Test
    void nothingCatchesLightBesideAnOpenLavaGate()
    {
        assertTrue(ignited(blockAt(GX + 2, GY, GZ, Material.OAK_PLANKS)),
            "a plank two blocks from the portal is not the fire's to take");
    }

    /** Nor does anything beside it burn away once alight. */
    @Test
    void nothingBurnsAwayBesideAnOpenLavaGate()
    {
        assertTrue(burned(blockAt(GX + 2, GY, GZ, Material.OAK_PLANKS), gateBlock),
            "burning is stopped on the same footing as lighting");
    }

    /**
     * Far enough away and the fire is nobody's business but the server's.
     *
     * <p>The protection is a radius. Cancelling every ignition on the map because a lava gate
     * exists somewhere would be a fire-protection plugin, which this is not.
     */
    @Test
    void aFireWellAwayFromTheGateIsLeftAlone()
    {
        assertFalse(ignited(blockAt(GX + 40, GY, GZ, Material.OAK_PLANKS)),
            "forty blocks off is not near a gate");
    }

    /**
     * A gate that is shut protects nothing, because there is no lava to protect against.
     *
     * <p>The portal material is only in the world while the gate is open. Shut, the blocks are
     * whatever the frame is made of and fire near them is ordinary fire.
     */
    @Test
    void aShutGateDoesNotStopFireNearIt()
    {
        gate.setGateActive(false);

        assertFalse(ignited(blockAt(GX + 2, GY, GZ, Material.OAK_PLANKS)),
            "a shut gate has no lava in the world to be responsible for");
    }

    /**
     * Nor does an open gate whose portal is not lava.
     *
     * <p>A water gate has nothing to set anything on fire with, so fire near it is not the
     * plugin's doing and not the plugin's to stop.
     */
    @Test
    void aWaterGateDoesNotStopFireNearIt()
    {
        gate.setGateCustomPortalMaterial(Material.WATER);

        assertFalse(ignited(blockAt(GX + 2, GY, GZ, Material.OAK_PLANKS)),
            "only a gate that put lava in the world answers for fire");
    }

    /**
     * Near enough for the gate to be found, far enough to be none of its business.
     *
     * <p>Forty blocks away tests nothing: the index lookup has a radius of ten, so no gate is
     * found at all and the distance rule is never reached. Eight blocks is inside the lookup
     * and outside the protection, which is the only place the threshold itself is visible.
     */
    @Test
    void aFireJustOutsideTheRadiusIsLeftAlone()
    {
        assertFalse(ignited(blockAt(GX + 8, GY, GZ, Material.OAK_PLANKS)),
            "sixty-four squared is past the twenty-five the gate answers for");
    }

    /**
     * The burn rule is the ignite rule again, and is tested as its own thing.
     *
     * <p>{@code onBlockBurn} and {@code onBlockIgnite} hold the same condition written out
     * twice. Testing one and trusting the other is how a change to one of them ships
     * unnoticed, so each of the three cases below is asked of both.
     */
    @Test
    void aShutGateDoesNotStopBurningEither()
    {
        gate.setGateActive(false);

        assertFalse(burned(blockAt(GX + 2, GY, GZ, Material.OAK_PLANKS), gateBlock),
            "a shut gate has no lava in the world to be responsible for");
    }

    /** And the burn rule has the same radius as the ignite one. */
    @Test
    void burningJustOutsideTheRadiusIsLeftAlone()
    {
        assertFalse(burned(blockAt(GX + 8, GY, GZ, Material.OAK_PLANKS), gateBlock),
            "the two rules have to agree about where a gate's responsibility ends");
    }

    /**
     * A player without the damage node cannot start breaking a gate block.
     *
     * <p>Stopped at the first hit rather than at the break, so nothing is ever part-broken and
     * the player is told immediately rather than after holding the button down.
     */
    @Test
    void aPlayerWithoutTheDamageNodeCannotHitAGateBlock()
    {
        final BlockDamageEvent event = new BlockDamageEvent(player, gateBlock,
            new ItemStack(Material.DIAMOND_PICKAXE), false);

        new WormholeXTremeBlockListener().onBlockDamage(event);

        assertTrue(event.isCancelled(), "the gate is not theirs to take apart");
    }

    /** An operator may, because op outranks the node everywhere else in this plugin too. */
    @Test
    void anOperatorMayHitAGateBlock()
    {
        when(player.isOp()).thenReturn(Boolean.TRUE);
        final BlockDamageEvent event = new BlockDamageEvent(player, gateBlock,
            new ItemStack(Material.DIAMOND_PICKAXE), false);

        new WormholeXTremeBlockListener().onBlockDamage(event);

        assertFalse(event.isCancelled(), "op is the final word here as everywhere else");
    }

    /** Hitting a block that is nothing to do with a gate is nothing to do with this plugin. */
    @Test
    void hittingAnOrdinaryBlockIsNotInterferedWith()
    {
        final BlockDamageEvent event = new BlockDamageEvent(player,
            blockAt(GX + 40, GY, GZ, Material.STONE),
            new ItemStack(Material.DIAMOND_PICKAXE), false);

        new WormholeXTremeBlockListener().onBlockDamage(event);

        assertFalse(event.isCancelled());
    }

    /**
     * Liquid does not flow into a gate block.
     *
     * <p>Water reaching an open portal would replace it, which leaves a gate that looks open
     * and carries nobody. The check is on the destination, so the flow is stopped before it
     * arrives rather than undone after.
     */
    @Test
    void liquidDoesNotFlowIntoAGateBlock()
    {
        final BlockFromToEvent event = new BlockFromToEvent(
            blockAt(GX + 1, GY, GZ, Material.WATER), gateBlock);

        new WormholeXTremeBlockListener().onBlockFromTo(event);

        assertTrue(event.isCancelled(), "nothing flows into a gate");
    }

    /**
     * And a gate's own portal does not flow out of it.
     *
     * <p>An open lava gate is lava in the world, and lava spreads. Without this the portal
     * pours down whatever it is standing over.
     */
    @Test
    void aGatesOwnPortalDoesNotFlowOutOfIt()
    {
        final BlockFromToEvent event = new BlockFromToEvent(gateBlock,
            blockAt(GX + 1, GY, GZ, Material.AIR));

        new WormholeXTremeBlockListener().onBlockFromTo(event);

        assertTrue(event.isCancelled(), "an open portal stays where it was put");
    }

    /** Liquid flowing between two blocks that are nothing to do with a gate is left alone. */
    @Test
    void liquidFlowingNowhereNearAGateIsLeftAlone()
    {
        final BlockFromToEvent event = new BlockFromToEvent(
            blockAt(GX + 40, GY, GZ, Material.WATER),
            blockAt(GX + 41, GY, GZ, Material.AIR));

        new WormholeXTremeBlockListener().onBlockFromTo(event);

        assertFalse(event.isCancelled());
    }
}
