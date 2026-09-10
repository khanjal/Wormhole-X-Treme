package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.GateSpatialIndex;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * Nobody builds in the gate opening, and nobody is stuck with what somebody already did.
 *
 * <p>The plugin had no {@code BlockPlaceEvent} handler at all, so a player could drop a block
 * into the ring of a shut gate and nothing stopped them. Breaking it again was another matter:
 * a portal cell is indexed to its gate exactly as the frame is, so the break came back as
 * "this block is part of the registered gate" and the block stayed there for good -- in a gate
 * they very often had no permission to remove either. Place allowed, break refused, which is
 * the worst of both. See #243.
 *
 * <p>Both halves are fixed together on purpose, and they have to be: refusing the placement
 * stops it happening again, but every gate already carrying a block in its ring needs the
 * break to start working before an admin can clear it.
 *
 * <p>The distinction the break turns on is that a portal is never a real block. An open portal
 * is drawn in each nearby client and the server keeps the cell as AIR, so anything solid found
 * in one was put there by a player. The single exception is a closed iris, which is placed for
 * real precisely so that nobody can walk through it, and that is still the gate's.
 */
class GatePortalInteriorBuildTest
{
    private static final int GX = 200, GY = 64, GZ = 200;

    private World world;
    private Stargate gate;
    private Player player;
    private boolean permissionsWereDisabled;

    @BeforeEach
    void setUp() throws Exception
    {
        GateSpatialIndex.clear();
        PluginTestSupport.install(mock(WormholeXTreme.class));

        world = mock(World.class);
        when(world.getName()).thenReturn("world");

        gate = new Stargate();
        gate.setGateName("ringgate");
        gate.setGateWorld(world);
        // One frame block and one portal cell is the whole of the geometry these rules need.
        gate.getGateStructureBlocks().add(new Location(world, GX, GY, GZ));
        gate.getGatePortalBlocks().add(new Location(world, GX + 1, GY, GZ));
        StargateManager.registerStargate(gate);

        player = mock(Player.class);
        when(player.getName()).thenReturn("builder");
        // A bare mock answers null to getUniqueId, and isOwner calls it. Give it one.
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        // Denied every node explicitly, so a test that passes does so because of op or
        // ownership rather than a permission lookup quietly succeeding underneath.
        when(player.hasPermission(anyString())).thenReturn(false);

        permissionsWereDisabled = ConfigManager.getPermissionsSupportDisable();
        // Node checks are only reached with a permissions plugin present.
        ConfigManager.setPermissionsSupportDisable(false);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        ConfigManager.setPermissionsSupportDisable(permissionsWereDisabled);
        StargateManager.removeStargate(gate, null, false);
        GateSpatialIndex.clear();
        PluginTestSupport.remove();
    }

    private Block blockAt(final int x, final int y, final int z, final Material type)
    {
        final Block b = mock(Block.class);
        when(b.getLocation()).thenReturn(new Location(world, x, y, z));
        when(b.getWorld()).thenReturn(world);
        when(b.getType()).thenReturn(type);
        return b;
    }

    /** The gate's one portal cell, holding whatever a test says is standing in it. */
    private Block portalCell(final Material type)
    {
        return blockAt(GX + 1, GY, GZ, type);
    }

    /** The gate's one frame block. */
    private Block frameBlock()
    {
        return blockAt(GX, GY, GZ, Material.OBSIDIAN);
    }

    private boolean placeRefused(final Block placed)
    {
        final BlockPlaceEvent event = new BlockPlaceEvent(placed, mock(BlockState.class),
            frameBlock(), new ItemStack(Material.COBBLESTONE), player, true);
        new WormholeXTremeBlockListener().onBlockPlace(event);
        return event.isCancelled();
    }

    private boolean breakRefused(final Block broken)
    {
        final BlockBreakEvent event = new BlockBreakEvent(broken, player);
        new WormholeXTremeBlockListener().onBlockBreak(event);
        return event.isCancelled();
    }

    private boolean hitRefused(final Block hit)
    {
        final BlockDamageEvent event = new BlockDamageEvent(player, hit,
            new ItemStack(Material.DIAMOND_PICKAXE), false);
        new WormholeXTremeBlockListener().onBlockDamage(event);
        return event.isCancelled();
    }

    /**
     * A block cannot be put in the gate opening in the first place.
     *
     * <p>This is the half that was simply absent: no handler watched placement at all, so the
     * ring of a shut gate was as buildable as open air.
     */
    @Test
    void aBlockCannotBePlacedInTheGateOpening()
    {
        assertTrue(placeRefused(portalCell(Material.COBBLESTONE)),
            "the gate opening is not somewhere to build");
    }

    /**
     * And the player is told why, rather than watching the block vanish.
     *
     * <p>A cancelled placement puts the item back with no explanation, which reads as lag or a
     * broken client rather than as a rule.
     */
    @Test
    void thePlayerIsToldWhyTheirBlockWouldNotStay()
    {
        placeRefused(portalCell(Material.COBBLESTONE));

        org.mockito.Mockito.verify(player).sendMessage(org.mockito.ArgumentMatchers
            .contains("cannot build inside the gate 'ringgate'"));
    }

    /**
     * A block somebody already left in the opening can be broken out again.
     *
     * <p>This is the bug as reported. Before the fix the cell's presence in the gate index was
     * the whole answer, so the break was refused as gate structure and the only way to clear
     * the block was to remove the entire gate.
     */
    @Test
    void aStrayBlockLeftInTheOpeningCanBeBrokenOutAgain()
    {
        assertFalse(breakRefused(portalCell(Material.COBBLESTONE)),
            "a block a player put in the ring is theirs to take out, not the gate's");
    }

    /**
     * And hitting it is not stopped by the damage check either.
     *
     * <p>Without this the fix above would do nothing on a survival server: {@code onBlockDamage}
     * cancels the first swing for anyone lacking the DAMAGE node on the gate, so the break the
     * plugin now allows could never be started. The two handlers have to agree on what counts
     * as the gate's.
     */
    @Test
    void hittingAStrayBlockInTheOpeningIsNotStoppedByTheDamageCheck()
    {
        assertFalse(hitRefused(portalCell(Material.COBBLESTONE)),
            "a stray block is not the gate's, so the gate's damage node does not guard it");
    }

    /**
     * A closed iris is still the gate's, and stays protected.
     *
     * <p>The iris occupies the very same cells as the portal and, unlike the portal, is real
     * blocks. Reading "solid block in a portal cell" as "somebody's stray block" without
     * checking the iris would hand anyone a way to open a sealed gate with a pickaxe.
     */
    @Test
    void aClosedIrisCannotBeBrokenOutOfTheOpening()
    {
        gate.setGateIrisActive(true);

        assertTrue(breakRefused(portalCell(Material.IRON_BLOCK)),
            "the iris is the gate's barrier, not a block somebody left lying in it");
    }

    /** And it is not hittable either, for the same reason. */
    @Test
    void aClosedIrisCannotBeHitEither()
    {
        gate.setGateIrisActive(true);

        assertTrue(hitRefused(portalCell(Material.IRON_BLOCK)),
            "a sealed gate does not come open to a pickaxe");
    }

    /** The frame is what protection was always for, and it is untouched by any of this. */
    @Test
    void theGateFrameIsStillProtected()
    {
        assertTrue(breakRefused(frameBlock()), "the frame still cannot be broken");
        assertTrue(hitRefused(frameBlock()), "nor hit");
    }

    /**
     * Only the opening is refused, not everywhere the gate is indexed.
     *
     * <p>A gate indexes more than its ring -- the frame, the DHD, the redstone cells an admin
     * is expected to wire by hand. Refusing placement on the index rather than on the portal
     * blocks would have stopped an admin laying that wiring, and stopped anyone replacing a
     * frame block that had gone missing.
     */
    @Test
    void placingAgainstTheRestOfTheGateIsLeftAlone()
    {
        assertFalse(placeRefused(blockAt(GX, GY, GZ, Material.REDSTONE_WIRE)),
            "an indexed cell that is not part of the opening is still buildable");
    }

    /** A block placed nowhere near a gate is nothing to do with this plugin. */
    @Test
    void placingWellAwayFromAGateIsLeftAlone()
    {
        assertFalse(placeRefused(blockAt(GX + 40, GY, GZ, Material.COBBLESTONE)));
    }

    /**
     * An operator may build in the opening anyway.
     *
     * <p>Op is the final word everywhere else in this plugin, and an admin fitting a gate out
     * by hand is the reason the bypass exists at all.
     */
    @Test
    void anOperatorMayBuildInTheOpening()
    {
        when(player.isOp()).thenReturn(Boolean.TRUE);

        assertFalse(placeRefused(portalCell(Material.COBBLESTONE)),
            "op outranks the refusal here as it does everywhere else");
    }

    /** And so may the gate's owner, on their own gate. */
    @Test
    void theGateOwnerMayBuildInTheirOwnOpening()
    {
        gate.setGateOwner(player.getUniqueId().toString());

        assertFalse(placeRefused(portalCell(Material.COBBLESTONE)),
            "the owner may build in the gate they own");
    }

    /**
     * And an admin holding the config node, who is neither op nor the owner.
     *
     * <p>This is the case the other two cannot stand in for: a server that runs its
     * permissions through a plugin rather than by handing out op.
     */
    @Test
    void anAdminHoldingTheConfigNodeMayBuildInTheOpening()
    {
        gate.setGateOwner(UUID.randomUUID().toString());
        when(player.hasPermission("wormhole.config")).thenReturn(Boolean.TRUE);

        assertFalse(placeRefused(portalCell(Material.COBBLESTONE)),
            "wormhole.config is an admin on this server, op or not");
    }

    /**
     * But `wormhole.build` is not enough, and that is the whole point of choosing DAMAGE.
     *
     * <p>BUILD reads more naturally for placing a block, and was the first candidate. It is
     * the wrong set of people: BUILD is the node for raising a new gate, and on most servers
     * ordinary players hold it on the Public network -- so using it would have left the
     * opening open to nearly everyone and made the fix above meaningless.
     */
    @Test
    void theGateBuildingNodeIsNotEnoughToBuildInTheOpening()
    {
        gate.setGateOwner(UUID.randomUUID().toString());
        when(player.hasPermission("wormhole.build")).thenReturn(Boolean.TRUE);

        assertTrue(placeRefused(portalCell(Material.COBBLESTONE)),
            "building gates is not the same permission as building inside one");
    }

    /**
     * What an admin leaves in the opening is still anybody's to break.
     *
     * <p>The bypass lets the block be placed; it does not make it part of the gate. Treating
     * a placement by an admin as gate structure would put the original bug straight back, in
     * the one case where somebody deliberately meant to leave a block there.
     */
    @Test
    void whatAnAdminLeavesInTheOpeningIsStillBreakableByAnybody()
    {
        when(player.isOp()).thenReturn(Boolean.TRUE);
        assertFalse(placeRefused(portalCell(Material.COBBLESTONE)));

        final Player ordinary = mock(Player.class);
        when(ordinary.getName()).thenReturn("passerby");
        when(ordinary.getUniqueId()).thenReturn(UUID.randomUUID());
        when(ordinary.hasPermission(anyString())).thenReturn(Boolean.FALSE);

        final BlockBreakEvent event =
            new BlockBreakEvent(portalCell(Material.COBBLESTONE), ordinary);
        new WormholeXTremeBlockListener().onBlockBreak(event);

        assertFalse(event.isCancelled(),
            "an admin's block in the ring is still not gate structure");
    }
}
