package com.wormhole_xtreme.wormhole.model.window;

import java.util.List;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.StructureGrowEvent;

/**
 * Tells {@link Windows} which blocks changed, so a view built from the world behind a window's
 * wall is built again when that world changes, not on a timer (#609).
 *
 * <p>Each handler only passes positions on: {@link Windows#changed} answers a block nowhere near a
 * held view from one lookup. What no event reports -- a chunk generated or reloaded, a command's
 * fill -- is caught by the views' long backstop ({@link Windows#BACKSTOP_MILLIS}).
 */
public class ViewChangeListener implements Listener
{
    /**
     * @param event
     *            a block placed, a bed or door's two halves included
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(final BlockPlaceEvent event)
    {
        changed(event.getBlockPlaced());
    }

    /**
     * @param event
     *            a block broken
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(final BlockBreakEvent event)
    {
        changed(event.getBlock());
    }

    /**
     * @param event
     *            a creeper, TNT or the like
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(final EntityExplodeEvent event)
    {
        changed(event.blockList());
    }

    /**
     * @param event
     *            a bed or respawn anchor going off
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(final BlockExplodeEvent event)
    {
        changed(event.getBlock());
        changed(event.blockList());
    }

    /**
     * @param event
     *            a piston pushing: every block it moves, where it goes, and the head
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExtend(final BlockPistonExtendEvent event)
    {
        moved(event.getBlock(), event.getBlocks(), event.getDirection());
    }

    /**
     * @param event
     *            a piston pulling back
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRetract(final BlockPistonRetractEvent event)
    {
        moved(event.getBlock(), event.getBlocks(), event.getDirection());
    }

    /**
     * @param event
     *            a block burnt away
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(final BlockBurnEvent event)
    {
        changed(event.getBlock());
    }

    /**
     * @param event
     *            water or lava flowing on, or a dragon egg teleporting
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFlow(final BlockFromToEvent event)
    {
        changed(event.getToBlock());
    }

    /**
     * @param event
     *            leaves decaying
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDecay(final LeavesDecayEvent event)
    {
        changed(event.getBlock());
    }

    /**
     * @param event
     *            snow or ice forming
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onForm(final BlockFormEvent event)
    {
        changed(event.getBlock());
    }

    /**
     * @param event
     *            fire, vines or mushrooms spreading
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpread(final BlockSpreadEvent event)
    {
        changed(event.getBlock());
    }

    /**
     * @param event
     *            snow or ice melting, fire going out
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFade(final BlockFadeEvent event)
    {
        changed(event.getBlock());
    }

    /**
     * @param event
     *            sand landing or falling, an enderman, a sheep eating grass
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChange(final EntityChangeBlockEvent event)
    {
        changed(event.getBlock());
    }

    /**
     * @param event
     *            a sapling or mushroom growing into a tree
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGrow(final StructureGrowEvent event)
    {
        for (final BlockState state : event.getBlocks())
        {
            Windows.changed(state.getWorld(), state.getX(), state.getY(), state.getZ());
        }
    }

    /** Every block a piston moves, the block it moves into, and the space in front of the piston. */
    private static void moved(final Block piston, final List<Block> blocks, final BlockFace direction)
    {
        changed(piston.getRelative(direction));
        for (final Block block : blocks)
        {
            changed(block);
            changed(block.getRelative(direction));
        }
    }

    private static void changed(final List<Block> blocks)
    {
        for (final Block block : blocks)
        {
            changed(block);
        }
    }

    private static void changed(final Block block)
    {
        Windows.changed(block.getWorld(), block.getX(), block.getY(), block.getZ());
    }
}
