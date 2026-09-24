package com.wormhole_xtreme.wormhole.model.preview;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.FaceAttachable;

import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Cell;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Part;
import com.wormhole_xtreme.wormhole.logic.StargateHelper;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.preview.GatePreviews.Outcome;
import com.wormhole_xtreme.wormhole.model.preview.GatePreviews.Placed;
import com.wormhole_xtreme.wormhole.model.ring.RingIndex;

/**
 * Turns a preview into real blocks for {@code gate preview place}, after checking every block it would
 * take, so a refusal leaves the world as it was.
 */
final class PreviewPlacer
{
    /** How many blocks in the way are named; the guide marks the rest. */
    private static final int NAMED = 5;

    private PreviewPlacer() {}

    static Placed place(final GatePreview preview, final boolean overAGate)
    {
        if (!StargateHelper.isPossibleGateFrameMaterial(preview.palette().structure()))
        {
            return refused(Outcome.NOT_FINDABLE);
        }
        final List<Cell> all = new ArrayList<>(preview.cells());
        all.addAll(preview.opening());
        final Outcome unreachable = unreachable(preview.world(), all);
        if (unreachable != null)
        {
            return refused(unreachable);
        }
        final Stargate under = overAGate ? gateUnder(preview.world(), all) : null;
        final List<String> inTheWay = inTheWay(preview, all, under);
        if (!inTheWay.isEmpty())
        {
            return new Placed(Outcome.IN_THE_WAY, inTheWay, null, null);
        }
        final Block button = build(preview);
        if (under != null)
        {
            return (button == null) ? refused(Outcome.NOT_FOUND) : new Placed(Outcome.REPAIRED, List.of(), under, button);
        }
        final Stargate gate = (button == null) ? null
            : GatePreviews.detector.find(button, preview.grid().facing(), preview.shape());
        return (gate == null) ? refused(Outcome.NOT_FOUND) : new Placed(Outcome.PLACED, List.of(), gate, button);
    }

    /** Why some cell cannot be built in, or null if every one can. */
    private static Outcome unreachable(final World world, final List<Cell> all)
    {
        final WorldBorder border = world.getWorldBorder();
        for (final Cell cell : all)
        {
            if (!world.isChunkLoaded(cell.x() >> 4, cell.z() >> 4))
            {
                return Outcome.NOT_LOADED;
            }
            if (!border.isInside(new Location(world, cell.x(), cell.y(), cell.z())))
            {
                return Outcome.OUTSIDE_BORDER;
            }
        }
        return null;
    }

    /**
     * Places every block not already right, frame first and the button last so it has a block to hang
     * on. A dial sign is left to the builder to write.
     *
     * @return the button's block, or null for a shape without one
     */
    private static Block build(final GatePreview preview)
    {
        Block button = null;
        for (final Cell cell : preview.cells())
        {
            final Block block = preview.world().getBlockAt(cell.x(), cell.y(), cell.z());
            if (cell.part() == Part.BUTTON)
            {
                button = block;
            }
            if ((cell.part() != Part.DIAL_SIGN) && !alreadyRight(preview, cell, block))
            {
                final BlockData data = GatePreviews.blockDataFor(preview, cell);
                if (data instanceof FaceAttachable attached)
                {
                    attached.setAttachedFace(FaceAttachable.AttachedFace.WALL);
                }
                com.wormhole_xtreme.wormhole.plugin.CoreProtectLog.placing(com.wormhole_xtreme.wormhole.plugin.CoreProtectLog.PLUGIN_USER, block, data.getMaterial(), data);
                block.setBlockData(data, false);
            }
        }
        return button;
    }

    /**
     * Whether a block can stay as it is: what detection takes there, and in the button's place a button
     * or lever hung on the wall facing the builder, as a placed button would be. A lever is a DHD as
     * much as a button is; the plugin swaps one for the other itself.
     */
    private static boolean alreadyRight(final GatePreview preview, final Cell cell, final Block block)
    {
        if (BuildGuide.of(cell, preview.palette(), block.getType()) != BuildGuide.State.PLACED)
        {
            return false;
        }
        final BlockData data = block.getBlockData();
        return (cell.part() != Part.BUTTON)
            || ((data instanceof FaceAttachable attached) && (attached.getAttachedFace() == FaceAttachable.AttachedFace.WALL)
                && (data instanceof Directional directional) && (directional.getFacing() == preview.grid().facing()));
    }

    /**
     * A gate owning a block the preview covers, or null. Any block a ring or another gate owns stays
     * in the way, so a preview over more than one is still refused.
     */
    private static Stargate gateUnder(final World world, final List<Cell> all)
    {
        for (final Cell cell : all)
        {
            if (GatePreviews.occupied.at(world, cell.x(), cell.y(), cell.z()))
            {
                final Stargate gate = GatePreviews.gateAt.at(world, cell.x(), cell.y(), cell.z());
                if (gate != null)
                {
                    return gate;
                }
            }
        }
        return null;
    }

    /**
     * Every block the gate needs that holds something else, and every one a gate or ring owns, apart
     * from the gate it stands over.
     */
    private static List<String> inTheWay(final GatePreview preview, final List<Cell> all, final Stargate under)
    {
        final World world = preview.world();
        final List<String> found = new ArrayList<>();
        int more = 0;
        for (final Cell cell : all)
        {
            final Material there = world.getBlockAt(cell.x(), cell.y(), cell.z()).getType();
            final boolean owned = ownedByAnother(world, cell, under);
            final boolean taken = owned || ((cell.part() == Part.PORTAL) ? BuildGuide.blocksOpening(there)
                : (BuildGuide.of(cell, preview.palette(), there) == BuildGuide.State.WRONG));
            if (!taken)
            {
                continue;
            }
            if (found.size() < NAMED)
            {
                found.add((owned ? "a gate or ring" : there.name().toLowerCase(Locale.ROOT)) + " at " + cell.x() + " "
                    + cell.y() + " " + cell.z());
            }
            else
            {
                more++;
            }
        }
        if (more > 0)
        {
            found.add(more + " more");
        }
        return found;
    }

    /** Whether a gate or ring other than the one the preview stands over owns this block. */
    private static boolean ownedByAnother(final World world, final Cell cell, final Stargate under)
    {
        return GatePreviews.occupied.at(world, cell.x(), cell.y(), cell.z())
            && ((under == null) || (GatePreviews.gateAt.at(world, cell.x(), cell.y(), cell.z()) != under));
    }

    private static Placed refused(final Outcome outcome)
    {
        return new Placed(outcome, List.of(), null, null);
    }

    /** The gate a block belongs to, or null. */
    static Stargate gateAt(final World world, final int x, final int y, final int z)
    {
        return StargateManager.getGateFromBlock(world.getBlockAt(x, y, z));
    }

    /** Whether a block belongs to a gate or a ring already standing. */
    static boolean occupied(final World world, final int x, final int y, final int z)
    {
        return (StargateManager.getGateFromBlock(world.getBlockAt(x, y, z)) != null)
            || (RingIndex.volumeAt(world.getName(), x, y, z) != null)
            || (RingIndex.perimeterAt(world.getName(), x, y, z) != null);
    }
}
