package com.wormhole_xtreme.wormhole.model.window;

import java.util.List;

import org.bukkit.Material;
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
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.world.StructureGrowEvent;

/**
 * Tells {@link Windows} which blocks changed, so a view built from the world behind a window's
 * wall is built again when that world changes, not on a timer (#609).
 *
 * <p>Each handler asks first whether any view is held at all, before it touches the event: flowing
 * water, fading ice and spreading grass fire constantly, and a server with no mirror looked into
 * answers each from one empty check. Then {@link Windows#changed} answers a block nowhere near a
 * held view from one lookup. What no event reports -- a command's fill, WorldEdit, a dispenser, a
 * block that pops off, this plugin's own block writes, a chunk generated beside the view -- is
 * caught by the views' long backstop ({@link FixedView#BACKSTOP_MILLIS}).
 *
 * <p>{@code BlockSpreadEvent} and {@code BlockFormEvent} each have their own handler list, so the
 * subclass is heard once, by its own handler; the same holds for {@code BlockGrowEvent}.
 */
public class ViewChangeListener implements Listener
{
    /**
     * @param event
     *            a block placed; a bed or door's other half lies within the two blocks a change is judged with to spare
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(final BlockPlaceEvent event)
    {
        if (Windows.watchingAny())
        {
            changed(event.getBlockPlaced());
        }
    }

    /**
     * @param event
     *            a block broken
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(final BlockBreakEvent event)
    {
        if (Windows.watchingAny())
        {
            changed(event.getBlock());
        }
    }

    /**
     * @param event
     *            water or lava poured out, which no place event reports
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(final PlayerBucketEmptyEvent event)
    {
        if (Windows.watchingAny())
        {
            changed(event.getBlock());
        }
    }

    /**
     * @param event
     *            water, lava or snow taken up in a bucket
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketFill(final PlayerBucketFillEvent event)
    {
        if (Windows.watchingAny())
        {
            changed(event.getBlock());
        }
    }

    /**
     * @param event
     *            a creeper, TNT or the like
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(final EntityExplodeEvent event)
    {
        if (Windows.watchingAny())
        {
            changed(event.blockList());
        }
    }

    /**
     * @param event
     *            a bed or respawn anchor going off
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(final BlockExplodeEvent event)
    {
        if (Windows.watchingAny())
        {
            changed(event.getBlock());
            changed(event.blockList());
        }
    }

    /**
     * @param event
     *            a piston pushing: every block it moves, where it goes, and the head
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExtend(final BlockPistonExtendEvent event)
    {
        if (Windows.watchingAny())
        {
            moved(event.getBlock(), event.getBlocks(), event.getDirection());
        }
    }

    /**
     * @param event
     *            a piston pulling back: its direction is the way the piston faces, the blocks move the other way
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRetract(final BlockPistonRetractEvent event)
    {
        if (Windows.watchingAny())
        {
            moved(event.getBlock(), event.getBlocks(), event.getDirection());
        }
    }

    /**
     * @param event
     *            a block burnt away
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(final BlockBurnEvent event)
    {
        if (Windows.watchingAny())
        {
            changed(event.getBlock());
        }
    }

    /**
     * @param event
     *            a fire lit
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIgnite(final BlockIgniteEvent event)
    {
        if (Windows.watchingAny())
        {
            changed(event.getBlock());
        }
    }

    /**
     * @param event
     *            water or lava flowing on, or a dragon egg teleporting
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFlow(final BlockFromToEvent event)
    {
        if (Windows.watchingAny())
        {
            changed(event.getToBlock());
        }
    }

    /**
     * @param event
     *            leaves decaying
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDecay(final LeavesDecayEvent event)
    {
        if (Windows.watchingAny())
        {
            changed(event.getBlock());
        }
    }

    /**
     * @param event
     *            snow or ice forming
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onForm(final BlockFormEvent event)
    {
        if (Windows.watchingAny())
        {
            changed(event.getBlock());
        }
    }

    /**
     * @param event
     *            fire, vines or mushrooms spreading
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpread(final BlockSpreadEvent event)
    {
        if (Windows.watchingAny())
        {
            changed(event.getBlock());
        }
    }

    /**
     * @param event
     *            snow or ice melting, fire going out
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFade(final BlockFadeEvent event)
    {
        if (Windows.watchingAny())
        {
            changed(event.getBlock());
        }
    }

    /**
     * A plant growing, told only when it grows into an empty block or out of one: a crop ageing
     * changes no block from empty to not, and a farm behind a wall would otherwise keep its view
     * rebuilding.
     *
     * @param event
     *            a crop, sugar cane, a cactus or a vine growing
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGrowBlock(final BlockGrowEvent event)
    {
        if (Windows.watchingAny() && (event.getBlock().isEmpty() != empty(event.getNewState().getType())))
        {
            changed(event.getBlock());
        }
    }

    /**
     * @param event
     *            bone meal spreading grass and flowers
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFertilize(final BlockFertilizeEvent event)
    {
        if (Windows.watchingAny())
        {
            changedStates(event.getBlocks());
        }
    }

    /**
     * @param event
     *            sand landing or falling, an enderman, a sheep eating grass
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChange(final EntityChangeBlockEvent event)
    {
        if (Windows.watchingAny())
        {
            changed(event.getBlock());
        }
    }

    /**
     * @param event
     *            a sapling or mushroom growing into a tree
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGrow(final StructureGrowEvent event)
    {
        if (Windows.watchingAny())
        {
            changedStates(event.getBlocks());
        }
    }

    /** Whether a material is one of the airs, by constant: {@code Material.isAir} needs the registry from 1.20.6. */
    static boolean empty(final Material material)
    {
        return (material == Material.AIR) || (material == Material.CAVE_AIR) || (material == Material.VOID_AIR);
    }

    /**
     * Every block a piston moves, the blocks either side of it along the piston's line, and the space
     * in front of the piston. Both sides: an extending piston's direction is the way its blocks move, a
     * retracting one's the way it faces, opposite to how they move.
     */
    private static void moved(final Block piston, final List<Block> blocks, final BlockFace direction)
    {
        changed(piston.getRelative(direction));
        final BlockFace back = direction.getOppositeFace();
        for (final Block block : blocks)
        {
            changed(block);
            changed(block.getRelative(direction));
            changed(block.getRelative(back));
        }
    }

    private static void changed(final List<Block> blocks)
    {
        for (final Block block : blocks)
        {
            changed(block);
        }
    }

    private static void changedStates(final List<BlockState> states)
    {
        for (final BlockState state : states)
        {
            Windows.changed(state.getWorld(), state.getX(), state.getY(), state.getZ());
        }
    }

    private static void changed(final Block block)
    {
        Windows.changed(block.getWorld(), block.getX(), block.getY(), block.getZ());
    }
}
