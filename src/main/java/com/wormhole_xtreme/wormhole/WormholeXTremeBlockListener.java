package com.wormhole_xtreme.wormhole;

import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions.PermissionType;
import com.wormhole_xtreme.wormhole.utils.MaterialUtils;
import com.wormhole_xtreme.wormhole.utils.WorldUtils;

/**
 * WormholeXTreme Block Listener.
 *
 * <p>Four of the seven handlers here deliberately run on events that arrive already cancelled.
 * Bukkit lets a later listener un-cancel, and these are all plain {@code @EventHandler} and so
 * NORMAL priority -- one that skipped has said nothing at all by the time a plugin at HIGH calls
 * {@code setCancelled(false)}, and the block goes through with no protection ever having run.
 * They only ever cancel, so running them on a cancelled event costs nothing and skipping costs a
 * gate. {@code onBlockBreak}, {@code onBlockDamage} and {@code onBlockPlace} carry
 * {@code ignoreCancelled} instead, because they tell the player they were refused and that
 * would be a lie. See #53.
 *
 * @author Ben Echols (Lologarithm)
 * @author Dean Bailey (alron)
 */
class WormholeXTremeBlockListener implements Listener
{
    /**
     * Whether a block is redstone wiring the admin owns rather than gate structure.
     *
     * <p>A gate's [RD], [RS] and [RA] cells are indexed as gate blocks, and they have to be:
     * a redstone event arrives with only the block it fired on, and the index is how that
     * finds the gate. Being indexed also meant being protected, so the dust and levers an
     * admin lays to wire a gate could never be taken back up again -- the plugin told them to
     * remove the whole gate first, for a redstone block sitting on top of the DHD.
     *
     * <p>These are the one part of a gate the plugin expects a person to place, change and
     * remove freely. The frame is what protection is for.
     *
     * @param stargate
     *            the gate the block belongs to
     * @param block
     *            the block being broken
     * @return true if the break should be allowed
     */
    static boolean isRemovableGateWiring(final Stargate stargate, final Block block)
    {
        if ((stargate == null) || (block == null))
        {
            return false;
        }
        return WorldUtils.isSameBlock(stargate.getGateRedstoneDialActivationBlock(), block)
            || WorldUtils.isSameBlock(stargate.getGateRedstoneSignActivationBlock(), block)
            || WorldUtils.isSameBlock(stargate.getGateRedstoneGateActivatedBlock(), block);
    }

    /**
     * Handle block break.
     * 
     * @param player
     *            the player
     * @param stargate
     *            the stargate
     * @param block
     *            the block
     * @return true, if successful
     */
    private static boolean handleBlockBreak(final Player player, final Stargate stargate, final Block block)
    {
        // Redstone wiring is the admin's to change, even though it is indexed as gate blocks.
        if (isRemovableGateWiring(stargate, block))
        {
            return false;
        }
        if (isUnusedIrisSpot(stargate, block))
        {
            return false;
        }
        if (isStrayBlockInPortal(stargate, block))
        {
            return false;
        }
        refuseBreak(player, stargate);
        return true;
    }

    /**
     * Whether a block sits in one of the gate's portal cells.
     *
     * <p>Asks the gate's own portal block list rather than the world, because an open
     * portal is a drawing in each nearby client and the server keeps the cell as AIR.
     *
     * @param stargate
     *            the gate the cell belongs to
     * @param block
     *            the block to place in the ring
     * @return true if the block is inside the gate's portal interior
     */
    static boolean isPortalInterior(final Stargate stargate, final Block block)
    {
        if ((stargate == null) || (block == null))
        {
            return false;
        }
        try
        {
            final Location at = block.getLocation();
            return stargate.isGatePortalBlockAt(at.getBlockX(), at.getBlockY(), at.getBlockZ());
        }
        catch (final RuntimeException ignore)
        {
            // on failure fall through to the protective default
            return false;
        }
    }

    /**
     * Whether a block in the portal ring is somebody's, rather than the gate's.
     *
     * <p>The portal itself is never a real block -- {@code fillGateInterior} leaves AIR on
     * the server whether the gate is open or shut. So anything solid standing in a portal
     * cell was put there by a player, and until now they could not take it back out: the
     * cell is indexed to the gate, so the break was refused as gate structure and the block
     * was stuck there for good. See #243.
     *
     * <p>The one thing that really is a gate block in these cells is a closed iris, which
     * {@code fillGateIris} places for real precisely so nobody can walk through it.
     *
     * @param stargate
     *            the gate the cell belongs to
     * @param block
     *            the block being broken
     * @return true if the break should be allowed
     */
    static boolean isStrayBlockInPortal(final Stargate stargate, final Block block)
    {
        return isPortalInterior(stargate, block) && !stargate.isGateIrisActive();
    }

    /**
     * Whether this block is only where an iris lever would go, rather than where one is.
     *
     * <p>Shape detection assigns an iris position whether or not a lever was ever placed
     * there, so the block under the DHD button is usually just a block. Once a lever is
     * really there it is part of the gate and stays protected -- otherwise a player could
     * take the iris off a gate they cannot otherwise touch.
     */
    private static boolean isUnusedIrisSpot(final Stargate stargate, final Block block)
    {
        try
        {
            if ((stargate == null) || (stargate.getGateDialLeverBlock() == null))
            {
                return false;
            }
            final Block irisBlock = placedIrisLever(stargate);
            if (WorldUtils.isSameBlock(irisBlock, block))
            {
                // A lever that is really there is part of the gate.
                return false;
            }
            final Block dial = stargate.getGateDialLeverBlock();
            return WorldUtils.isSameBlock(dial.getRelative(BlockFace.DOWN), block)
                || WorldUtils.isSameBlock(irisLeverSpot(stargate, dial), block);
        }
        catch (final RuntimeException ignore)
        {
            // on failure fall through to the protective default
            return false;
        }
    }

    /**
     * The gate's iris lever, but only if a lever is actually there.
     *
     * <p>The gate records the position from shape detection regardless, so the block has to
     * be asked what it is rather than trusted to be a lever.
     */
    private static Block placedIrisLever(final Stargate stargate)
    {
        try
        {
            final Block irisBlock = stargate.getGateIrisLeverBlock();
            if ((irisBlock != null) && (irisBlock.getType() == Material.LEVER))
            {
                return irisBlock;
            }
        }
        catch (final RuntimeException ignore)
        {
            // an unreadable iris lever just means no iris
        }
        return null;
    }

    /**
     * Where an iris lever would hang, worked back from the DHD button.
     *
     * <p>The same walk setupIrisLever makes: back from the face the button points out of,
     * down to the base of the column, then forward along the gate's facing.
     */
    private static Block irisLeverSpot(final Stargate stargate, final Block dial)
    {
        BlockFace buttonFacing = stargate.getGateFacing();
        if (dial.getBlockData() instanceof Directional buttonData)
        {
            buttonFacing = buttonData.getFacing();
        }
        final Block backing = dial.getRelative(WorldUtils.getInverseDirection(buttonFacing));
        return backing.getRelative(BlockFace.DOWN).getRelative(stargate.getGateFacing());
    }

    /** Tells whoever broke it that the gate has to be removed by command first. */
    private static void refuseBreak(final Player player, final Stargate stargate)
    {
        final String name = (stargate != null) ? stargate.getGateName() : "unknown";
        if (player == null)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "Blocked non-player block break on registered gate: " + name);
            return;
        }
        try
        {
            player.sendMessage(ConfigManager.MessageStrings.ERROR_HEADER.toString()
                + "This block is part of the registered gate '" + name + "'.");
            player.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString()
                + "Run '/wormhole remove " + name + "' to remove the gate first (use -all to also destroy blocks).");
        }
        catch (final RuntimeException ignore)
        {
            // the break is already refused; only the explanation is missing
        }
    }

    /* (non-Javadoc)
     * @see org.bukkit.event.block.BlockListener#onBlockBreak(org.bukkit.event.block.BlockBreakEvent)
     */
    // ignoreCancelled, unlike the other four: this one talks to the player. #53.
    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(final BlockBreakEvent event)
    {
        final Block block = event.getBlock();
        final Stargate stargate = StargateManager.getGateFromBlock(block);
        final Player player = event.getPlayer();
        if ((stargate != null) && handleBlockBreak(player, stargate, block))
        {
            event.setCancelled(true);
        }
    }

    /** Tells whoever placed it that the gate opening is not somewhere to build. */
    private static void refusePlace(final Player player, final Stargate stargate)
    {
        if (player == null)
        {
            return;
        }
        final String name = (stargate != null) ? stargate.getGateName() : "unknown";
        try
        {
            player.sendMessage(ConfigManager.MessageStrings.ERROR_HEADER.toString()
                + "You cannot build inside the gate '" + name + "'.");
        }
        catch (final RuntimeException ignore)
        {
            // the placement is already refused; only the explanation is missing
        }
    }

    /**
     * Keeps the gate opening clear.
     *
     * <p>Nothing stopped a block being placed in the ring before, and once there it could
     * not be broken again: the cell is indexed to the gate, so the break came back as gate
     * structure. Refusing the placement is the half of that pair that was missing. See #243.
     *
     * @param event
     *            the placement
     */
    // ignoreCancelled, unlike the other four: this one talks to the player. #53.
    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(final BlockPlaceEvent event)
    {
        final Block block = event.getBlockPlaced();
        final Stargate stargate = StargateManager.getGateFromBlock(block);
        if ((stargate != null) && isPortalInterior(stargate, block))
        {
            event.setCancelled(true);
            refusePlace(event.getPlayer(), stargate);
        }
    }

    /* (non-Javadoc)
     * @see org.bukkit.event.block.BlockListener#onBlockBurn(org.bukkit.event.block.BlockBurnEvent)
     */
    // Runs on already-cancelled events on purpose -- see the class comment. #53.
    @EventHandler
    public void onBlockBurn(final BlockBurnEvent event)
    {
        final Location current = event.getBlock().getLocation();
        // Localized lookup: scan nearby indexed gate blocks instead of iterating all gates
        final Stargate closest = StargateManager.findNearestGateByBlock(current, 10, 5);
        if ((closest != null) && (closest.isGateActive() || closest.isGateRecentlyActive()) && ((closest.getEffectivePortalMaterial()) == Material.LAVA))
        {
            final double blockDistanceSquared = StargateManager.distanceSquaredToClosestGateBlock(current, closest);
            if (((blockDistanceSquared <= (closest.getEffectiveWooshDepthSquared())) && ((closest.getEffectiveWooshDepth()) != 0)) || (blockDistanceSquared <= 25))
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "Blocked Gate: \"" + closest.getGateName() + "\" Proximity Block Burn Distance Squared: \"" + blockDistanceSquared + "\"");
                event.setCancelled(true);
            }
        }
    }

    /* (non-Javadoc)
     * @see org.bukkit.event.block.BlockListener#onBlockDamage(org.bukkit.event.block.BlockDamageEvent)
     */
    // ignoreCancelled, unlike the other four: this one talks to the player. #53.
    @EventHandler(ignoreCancelled = true)
    public void onBlockDamage(final BlockDamageEvent event)
    {
        final Stargate stargate = StargateManager.getGateFromBlock(event.getBlock());
        final Player player = event.getPlayer();
        // A stray block in the ring is not the gate's, so DAMAGE does not gate it -- and
        // without this the break onBlockBreak now allows could never be started. #243.
        if ((stargate != null) && (player != null) && !isStrayBlockInPortal(stargate, event.getBlock())
            && !WXPermissions.checkWXPermissions(player, stargate, PermissionType.DAMAGE))
        {
            event.setCancelled(true);
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "Player: " + player.getName() + " denied damage on: " + stargate.getGateName());
        }
    }

    /* (non-Javadoc)
     * @see org.bukkit.event.block.BlockListener#onBlockFlow(org.bukkit.event.block.BlockFromToEvent)
     */
    // Runs on already-cancelled events on purpose -- see the class comment. #53.
    @EventHandler
    public void onBlockFromTo(final BlockFromToEvent event)
    {
        if (StargateManager.isBlockInGate(event.getToBlock())
            || StargateManager.isBlockInGate(event.getBlock()))
        {
            event.setCancelled(true);
        }
    }

    /* (non-Javadoc)
     * @see org.bukkit.event.block.BlockListener#onBlockIgnite(org.bukkit.event.block.BlockIgniteEvent)
     */
    // Runs on already-cancelled events on purpose -- see the class comment. #53.
    @EventHandler
    public void onBlockIgnite(final BlockIgniteEvent event)
    {
        final Location current = event.getBlock().getLocation();
        // Localized lookup: scan nearby indexed gate blocks instead of iterating all gates
        final Stargate closest = StargateManager.findNearestGateByBlock(current, 10, 5);
        if ((closest != null) && (closest.isGateActive() || closest.isGateRecentlyActive()) && ((closest.getEffectivePortalMaterial()) == Material.LAVA))
        {
            final double blockDistanceSquared = StargateManager.distanceSquaredToClosestGateBlock(current, closest);
            if (((blockDistanceSquared <= (closest.getEffectiveWooshDepthSquared())) && ((closest.getEffectiveWooshDepth()) != 0)) || (blockDistanceSquared <= 25))
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "Blocked Gate: \"" + closest.getGateName() + "\" Block Type: \"" + event.getBlock().getType().toString() + "\" Proximity Block Ignite: \"" + event.getCause().toString() + "\" Distance Squared: \"" + blockDistanceSquared + "\"");
                event.setCancelled(true);
            }
        }
    }

    /* (non-Javadoc)
     * @see org.bukkit.event.block.BlockListener#onBlockPhysics(org.bukkit.event.block.BlockPhysicsEvent)
     */
    // Runs on already-cancelled events on purpose -- see the class comment. #53.
    @EventHandler
    public void onBlockPhysics(final BlockPhysicsEvent event)
    {
        final Block block = event.getBlock();
        // Protect nearby ice from melting when gates use water as their portal material
        final Material t = block.getType();
        if (MaterialUtils.isIce(t))
        {
            final Location loc = block.getLocation();
            final Stargate closest = StargateManager.findNearestGateByBlock(loc, 10, 5);
            if ((closest != null) && (closest.isGateActive() || closest.isGateRecentlyActive()))
            {
                final double d2 = StargateManager.distanceSquaredToClosestGateBlock(loc, closest);
                if (d2 <= 16)
                {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        if (StargateManager.isBlockInGate(block) && (block.getType() != org.bukkit.Material.REDSTONE_WIRE))
        {
            event.setCancelled(true);
        }
    }
}
