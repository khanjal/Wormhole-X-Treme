package com.wormhole_xtreme.wormhole.model.preview;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Cell;
import com.wormhole_xtreme.wormhole.model.DrawnHorizon;
import com.wormhole_xtreme.wormhole.model.IrisLayering;
import com.wormhole_xtreme.wormhole.utils.MaterialUtils;

/**
 * How a build preview's iris and wormhole are layered per viewer: which side of the gate each
 * viewer is on, the displays and blocks that side is shown, and the redraw when somebody walks
 * round to the other (#430, #734).
 */
final class PreviewLayers
{
    /** Static helpers only. */
    private PreviewLayers()
    {
    }

    /**
     * Whether a preview's opening is drawn in two layers, so differently from each side.
     *
     * <p>The same question a real gate asks: a wormhole showing, an iris over it, and a gate
     * that stands upright. A preview of a horizontal shape keeps its iris in the ring, as the
     * gate does, because there is no in front or behind to stack along.
     *
     * @return true if its iris and wormhole are stacked per viewer
     */
    static boolean stacks(final GatePreview preview)
    {
        // Not while a sweep is crossing. The sweep covers the wormhole ring by ring, so the
        // wormhole has to stay in the ring for it to be seen covering anything; moving it
        // behind the gate at the first ring empties the opening for the length of the sweep,
        // which is what "the water vanished a beat before the iris arrived" looked like. The
        // gate settles its layers after the sweep too.
        return preview.open() && preview.isGateIrisActive() && upright(preview)
            && !preview.opening().isEmpty() && !preview.sweeping();
    }

    /** Whether a preview has an in front and a behind to stack its layers along. */
    static boolean upright(final GatePreview preview)
    {
        final BlockFace facing = preview.grid().facing();
        return (facing != null) && (facing != BlockFace.UP) && (facing != BlockFace.DOWN);
    }

    /**
     * Where the layers go for one opening cell, for a viewer on the given side.
     *
     * <p>Through {@link IrisLayering}, the same decision a real gate makes, so the two cannot
     * drift apart. Only the drawing differs: a preview's iris is block displays, which show no
     * liquid, so its wormhole is the fake block and its iris is the entity.
     *
     * @param cell
     *            the opening cell
     * @param eye
     *            where the viewer is looking from
     * @param stacked
     *            whether the preview is layered for this viewer at all
     * @return where the iris and the wormhole go for them
     */
    private static IrisLayering.Placement placed(final GatePreview preview, final Cell cell,
        final IrisLayering.Eye eye, final boolean stacked)
    {
        return IrisLayering.place(at(cell), preview.grid().facing(), eye,
            at -> freeForLayer(preview, at), stacked);
    }

    /** An opening cell as a plain position. */
    static IrisLayering.At at(final Cell cell)
    {
        return new IrisLayering.At(cell.x(), cell.y(), cell.z());
    }

    /**
     * Where every cell of a stacked preview's opening puts its layers, for one position.
     *
     * <p>One placement per opening cell, in the opening's own order -- so the list doubles as
     * what a viewer is holding, to be compared against on their next step.
     *
     * @param preview
     *            the preview, which should be stacked
     * @param from
     *            where it is being looked at from
     */
    // Runs on every tick of the iris sweep, so it stays a plain loop.
    @SuppressWarnings("java:S9391")
    static List<IrisLayering.Placement> layersFor(final GatePreview preview, final Location from)
    {
        final IrisLayering.Eye eye = new IrisLayering.Eye(from.getX(), from.getY(), from.getZ());
        final List<IrisLayering.At> opening = new ArrayList<>();
        preview.opening().forEach(cell -> opening.add(at(cell)));
        // The whole preview is cover, not just its opening: a viewer off to one side is looking
        // past the blocks around the ring, the same as at a built gate.
        final Set<IrisLayering.At> cover = new HashSet<>(opening);
        preview.cells().forEach(cell -> cover.add(at(cell)));
        final boolean stacked =
            IrisLayering.hidesFarLayers(opening, preview.grid().facing(), eye, cover::contains);
        final List<IrisLayering.Placement> layers = new ArrayList<>();
        for (final Cell cell : preview.opening())
        {
            layers.add(placed(preview, cell, eye, stacked));
        }
        return layers;
    }

    /** Whether a layer may be drawn at a position: real air, in a chunk that is loaded. */
    private static boolean freeForLayer(final GatePreview preview, final IrisLayering.At at)
    {
        if (!preview.world().isChunkLoaded(at.x() >> 4, at.z() >> 4))
        {
            return false;
        }
        return MaterialUtils.isAirMaterial(preview.world().getBlockAt(at.x(), at.y(), at.z()).getType());
    }

    /**
     * Spawns, or clears, the iris display a block along the facing for one opening cell.
     *
     * <p>Two sets of iris displays stand while a preview is stacked -- one in the ring, one
     * beyond it -- and each viewer is shown exactly one, which is what lets the same preview
     * read correctly from both sides at once. Standing it does not decide that anybody sees
     * it: a viewer round the side of the gate is shown the ring's set however free the cell
     * beyond is, which is {@link #applySideFor}'s to work out.
     *
     * @param owner
     *            the preview's owner
     * @param index
     *            the opening cell's index
     * @param stacked
     *            whether the preview is stacked at all
     */
    static void spawnBeyond(final Player owner, final GatePreview preview, final int index,
        final boolean stacked)
    {
        final Cell cell = preview.opening().get(index);
        final IrisLayering.At beyond = stacked && (preview.grid().facing() != null)
            ? at(cell).moved(preview.grid().facing(), 1) : null;
        // Nothing to stand where the preview is not stacked, or where whatever is built beyond
        // the ring leaves no room: the viewer behind sees the ring one like everybody else.
        if ((beyond == null) || !freeForLayer(preview, beyond))
        {
            GatePreview.removeAt(preview.beyondDisplays(), index);
            return;
        }
        PreviewFrame.spawnMissing(owner, preview, preview.beyondDisplays(), index, preview.world(),
            new Cell(beyond.x(), beyond.y(), beyond.z(), cell.part(), cell.wave(), cell.dhd(), cell.layer()),
            PreviewFrame.openingData(preview));
    }

    /**
     * Sends one viewer a set of placements, and remembers them as theirs.
     *
     * @param preview
     *            the preview, which should be stacked
     * @param layers
     *            the placements, one per opening cell
     */
    static void sendStacked(final Player viewer, final GatePreview preview,
        final List<IrisLayering.Placement> layers)
    {
        final BlockFace facing = preview.grid().facing();
        for (int i = 0; i < layers.size(); i++)
        {
            final Cell cell = preview.opening().get(i);
            final IrisLayering.Placement here = layers.get(i);
            // Every cell this viewer's wormhole is not in, handed back first: the ring itself
            // when the iris has it -- leaving the sweep's water under a glass iris where a gate
            // shows none -- and the offsets in case they have just come round from the other
            // side, or stepped far enough round that the layers have collapsed into one.
            for (final IrisLayering.At back : IrisLayering.handBacks(at(cell), facing, here.horizon()))
            {
                takeBackAt(viewer, preview, back);
            }
            if (here.horizon() != null)
            {
                preview.sent().add(GatePreview.key(cell));
                viewer.sendBlockChange(new Location(preview.world(),
                    here.horizon().x(), here.horizon().y(), here.horizon().z()),
                    horizonData(preview, at(cell), here));
            }
        }
        logStacked(viewer, preview, layers);
        preview.sides().put(viewer.getUniqueId(), layers);
    }

    /**
     * What to draw one cell's wormhole in, the same way a built gate decides it.
     *
     * <p>Behind the iris it is a look-alike where the iris would hide the liquid, and in the
     * ring it is the real thing. A preview's iris is a display entity rather than a block,
     * which takes no part in block face culling -- but a translucent entity hides translucent
     * water behind it just the same, so a preview needs this exactly as a gate does.
     *
     * @param ring
     *            the opening cell
     * @param placed
     *            where this cell's layers went
     * @return the block data to send
     */
    static BlockData horizonData(final GatePreview preview, final IrisLayering.At ring,
        final IrisLayering.Placement placed)
    {
        return MaterialUtils.laidAcross(GatePreviews.blockData.apply(DrawnHorizon.materialFor(preview.palette().portal(),
            preview.palette().iris(), ring, !ring.equals(placed.horizon()))), preview.grid().facing());
    }

    /**
     * Moves the wormhole drawn behind a see-through iris, for every preview that has one.
     *
     * <p>The gate's own sweep, for previews. Only those whose iris hides the liquid, and only
     * the cells a viewer actually holds it in.
     */
    static void tickHorizon()
    {
        for (final Map.Entry<UUID, List<GatePreview>> owned : GatePreviews.PREVIEWS.entrySet())
        {
            final Player owner = WormholeXTreme.getThisPlugin().getServer().getPlayer(owned.getKey());
            if (owner == null)
            {
                continue;
            }
            for (final GatePreview preview : owned.getValue())
            {
                if (stacks(preview) && DrawnHorizon.standsIn(preview.palette().iris()))
                {
                    shimmerPreview(owner, preview);
                }
            }
        }
    }

    /**
     * Redraws one preview's stand-in wormhole for everybody holding one.
     *
     * @param owner
     *            the preview's owner
     * @param preview
     *            the preview, whose iris is known to hide the liquid
     */
    private static void shimmerPreview(final Player owner, final GatePreview preview)
    {
        for (final Player viewer : PreviewAudience.watching(owner, preview))
        {
            final List<IrisLayering.Placement> layers = preview.sides().get(viewer.getUniqueId());
            if (layers == null)
            {
                continue;
            }
            for (int i = 0; i < layers.size(); i++)
            {
                final IrisLayering.At ring = at(preview.opening().get(i));
                final IrisLayering.At where = layers.get(i).horizon();
                if ((where != null) && !where.equals(ring))
                {
                    viewer.sendBlockChange(new Location(preview.world(), where.x(), where.y(), where.z()),
                        horizonData(preview, ring, layers.get(i)));
                }
            }
        }
    }

    /**
     * Says what one stacked draw decided, for working out why a preview looks wrong in a world.
     *
     * <p>The gate's own {@code logLayers}, for the path a preview takes instead. One line per
     * draw at {@code log-level: FINE}.
     *
     * @param viewer
     *            who it was drawn for
     * @param layers
     *            what was drawn
     */
    private static void logStacked(final Player viewer, final GatePreview preview,
        final List<IrisLayering.Placement> layers)
    {
        final WormholeXTreme plugin = WormholeXTreme.getThisPlugin();
        if ((plugin == null) || !plugin.isLoggable(Level.FINE) || layers.isEmpty())
        {
            return;
        }
        final Cell first = preview.opening().get(0);
        final IrisLayering.At ring = at(first);
        final StringBuilder dbg = new StringBuilder(256);
        dbg.append("Preview layers: For=").append(viewer.getName());
        dbg.append(" Facing=").append(preview.grid().facing());
        dbg.append(" Iris=").append(preview.palette().iris());
        dbg.append(" Portal=").append(preview.palette().portal());
        dbg.append(" Cells=").append(layers.size());
        dbg.append(" WithHorizon=").append(layers.stream().filter(p -> p.horizon() != null).count());
        dbg.append(" WithIris=").append(layers.stream().filter(p -> p.iris() != null).count());
        dbg.append(" Ring=").append(ring);
        dbg.append(" FirstIris=").append(layers.get(0).iris());
        dbg.append(" FirstHorizon=").append(layers.get(0).horizon());
        dbg.append(" ViewerAt=").append(viewer.getLocation().getBlockX()).append(',')
            .append(viewer.getLocation().getBlockY()).append(',').append(viewer.getLocation().getBlockZ());
        for (int step = -1; step <= 1; step += 2)
        {
            final IrisLayering.At off = ring.moved(preview.grid().facing(), step);
            dbg.append(" Off").append(step).append('=').append(off)
                .append('/').append(freeForLayer(preview, off) ? "free" : "taken");
        }
        plugin.prettyLog(Level.FINE, dbg.toString());
    }

    /**
     * Hands one viewer back both cells a stacked wormhole could have been drawn in.
     *
     * <p>A stacked wormhole is sent a block off the ring, and everything that takes a preview's
     * blocks back works from the opening's own cells -- so nothing else knows about the cell
     * that was actually written. Called when a preview stops being stacked and when it is taken
     * away entirely.
     */
    static void handBackOffsets(final Player viewer, final GatePreview preview)
    {
        final BlockFace facing = preview.grid().facing();
        if (facing == null)
        {
            return;
        }
        for (final Cell cell : preview.opening())
        {
            final IrisLayering.At ring = new IrisLayering.At(cell.x(), cell.y(), cell.z());
            takeBackAt(viewer, preview, ring.moved(facing, 1));
            takeBackAt(viewer, preview, ring.moved(facing, -1));
        }
    }

    /** Shows one viewer what really stands at a layer position, where its chunk is loaded. */
    static void takeBackAt(final Player viewer, final GatePreview preview, final IrisLayering.At at)
    {
        if (!preview.world().isChunkLoaded(at.x() >> 4, at.z() >> 4))
        {
            return;
        }
        viewer.sendBlockChange(new Location(preview.world(), at.x(), at.y(), at.z()),
            preview.world().getBlockAt(at.x(), at.y(), at.z()).getBlockData());
    }

    /**
     * Shows each viewer the preview as it looks from where they stand.
     *
     * <p>Both iris sets stand while a preview is stacked, and each viewer is shown one cell by
     * cell, along with the wormhole where it belongs for them: behind the ring from the front,
     * in the ring from behind, and in neither from far enough round the side. Unstacked, the
     * ring set is everybody's, which is what a preview has always shown.
     *
     * @param owner
     *            the preview's owner
     * @param stacked
     *            whether it is stacked
     */
    static void applySides(final Player owner, final GatePreview preview, final boolean stacked)
    {
        for (final Player viewer : PreviewAudience.watching(owner, preview))
        {
            if (stacked)
            {
                final List<IrisLayering.Placement> layers = layersFor(preview, viewer.getLocation());
                applySideFor(viewer, preview, layers);
                sendStacked(viewer, preview, layers);
            }
            else if (preview.sides().containsKey(viewer.getUniqueId()))
            {
                // Stacked until a moment ago: whoever was behind it was shown the set beyond
                // the ring, and the ring's own set is everybody's again.
                showRingSet(viewer, preview);
                // The wormhole was drawn a block off the ring for them as well, and the ring
                // cells the unstacked draw sends say nothing about that one, so it is handed
                // back here -- but not in the middle of a sweep. An iris opening is unstacked
                // from its very first ring, so this handed every cell's far layer back at once
                // while most of them were still covered: a see-through iris opened onto
                // nothing, which is the closing sweep's fault read backwards. Mid-sweep the
                // rings give their own back one at a time and the settle after the last ring
                // catches whatever is left, which is why the entry stays until then.
                if (!preview.sweeping())
                {
                    preview.sides().remove(viewer.getUniqueId());
                    handBackOffsets(viewer, preview);
                }
            }
        }
    }

    /**
     * Shows one viewer one of the two iris sets and hides the other.
     *
     * @param layers
     *            where their layers go, from {@link #layersFor}
     */
    static void applySideFor(final Player viewer, final GatePreview preview,
        final List<IrisLayering.Placement> layers)
    {
        // Cell by cell, because the two sets are not a viewer's to pick between wholesale. A
        // cell with nothing beyond the ring to stand in keeps its iris in the ring, and so does
        // one whose far display the opening no longer hides from where this viewer is: showing
        // the far one there is the iris seen floating beside the gate, and hiding the ring's as
        // well leaves that cell's wormhole with no iris over it, a shut gate reading as open.
        for (int i = 0; i < layers.size(); i++)
        {
            final BlockDisplay beyond = preview.beyondDisplays().get(i);
            final IrisLayering.At iris = layers.get(i).iris();
            // No iris at all where the wormhole has the ring to itself, which is what a viewer
            // behind an unlayered gate is shown: neither display is theirs.
            final boolean inRing = (iris != null) && iris.equals(at(preview.opening().get(i)));
            show(viewer, preview.openingDisplays().get(i), inRing);
            show(viewer, beyond, (iris != null) && !inRing);
        }
    }

    /** Shows one viewer the ring's own iris set, which is what an unstacked preview is. */
    private static void showRingSet(final Player viewer, final GatePreview preview)
    {
        show(viewer, preview.openingDisplays(), true);
        show(viewer, preview.beyondDisplays(), false);
    }

    /** Shows or hides a set of displays for one viewer. */
    private static void show(final Player viewer, final List<BlockDisplay> displays, final boolean shown)
    {
        for (final BlockDisplay display : displays)
        {
            show(viewer, display, shown);
        }
    }

    /** Shows or hides one display for one viewer, if it is standing. */
    private static void show(final Player viewer, final BlockDisplay display, final boolean shown)
    {
        if (display == null)
        {
            return;
        }
        if (shown)
        {
            viewer.showEntity(WormholeXTreme.getThisPlugin(), display);
        }
        else
        {
            viewer.hideEntity(WormholeXTreme.getThisPlugin(), display);
        }
    }

    /**
     * Restacks any preview whose plane a player has just crossed.
     *
     * <p>A preview redraws itself every five seconds, which cannot follow somebody walking round
     * one. This is the preview's half of what the move listener does for a real gate, and like
     * it, only a step that changes which side they are on costs anything.
     *
     * @param player
     *            the player who moved
     */
    static void moved(final Player player, final Location to)
    {
        if ((player == null) || (to == null) || !player.isOnline())
        {
            return;
        }
        for (final Map.Entry<UUID, List<GatePreview>> owned : GatePreviews.PREVIEWS.entrySet())
        {
            // Only a preview this player is actually shown. Without that, walking past
            // somebody's unshared preview revealed its iris and sent its wormhole -- displays
            // HiddenEntities had hidden from everybody, and blocks nothing would take back,
            // since every teardown path only reaches the people watching.
            final boolean watches = owned.getKey().equals(player.getUniqueId());
            for (final GatePreview preview : owned.getValue())
            {
                if (watches || preview.shownTo().contains(player.getUniqueId()))
                {
                    restackIfCrossed(player, preview, to);
                }
            }
        }
    }

    /** Redraws one preview for a player whose step changed what it looks like to them. */
    private static void restackIfCrossed(final Player player, final GatePreview preview, final Location to)
    {
        if (preview.sweeping() && preview.world().equals(player.getWorld()))
        {
            PreviewIrisSweep.resweepIfCrossed(player, preview, to);
            return;
        }
        if (!stacks(preview) || !preview.world().equals(player.getWorld()))
        {
            return;
        }
        final List<IrisLayering.Placement> now = layersFor(preview, to);
        if (now.equals(preview.sides().get(player.getUniqueId())))
        {
            return;
        }
        applySideFor(player, preview, now);
        sendStacked(player, preview, now);
    }
}
