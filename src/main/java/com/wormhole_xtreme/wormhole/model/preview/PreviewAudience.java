package com.wormhole_xtreme.wormhole.model.preview;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Cell;
import com.wormhole_xtreme.wormhole.model.IrisLayering;
import com.wormhole_xtreme.wormhole.utils.MaterialUtils;

/**
 * Who a build preview is shown to and what they are sent: the fake blocks of the wormhole, handed
 * out and taken back per viewer, and the audience kept in line with who it is shared with
 * (#430, #734).
 */
final class PreviewAudience
{
    /** Static helpers only. */
    private PreviewAudience()
    {
    }

    /**
     * Sends everyone watching the wormhole's material at some cells, as fake blocks: a block display
     * draws no liquid, and a real gate draws its wormhole the same way.
     */
    static void send(final Player owner, final GatePreview preview, final List<Cell> cells)
    {
        final List<Player> watching = watching(owner, preview);
        for (final Cell cell : cells)
        {
            preview.sent().add(GatePreview.key(cell));
        }
        watching.forEach(viewer -> sendTo(viewer, preview, cells));
    }

    /** Sends one viewer the wormhole at some cells. */
    static void sendTo(final Player viewer, final GatePreview preview, final List<Cell> cells)
    {
        final BlockData portal =
            MaterialUtils.laidAcross(GatePreviews.blockData.apply(preview.palette().portal()), preview.grid().facing());
        for (final Cell cell : cells)
        {
            viewer.sendBlockChange(new Location(preview.world(), cell.x(), cell.y(), cell.z()), portal);
        }
    }

    /**
     * Shows everyone watching what really stands at cells a fake block was sent to. An unloaded chunk
     * is left alone: the client gets it afresh when it loads.
     */
    static void takeBack(final Player owner, final GatePreview preview, final List<Cell> cells)
    {
        final List<Player> watching = watching(owner, preview);
        final List<Cell> sent = cells.stream().filter(cell -> preview.sent().remove(GatePreview.key(cell))).toList();
        watching.forEach(viewer -> takeBackFrom(viewer, preview, sent));
    }

    /** Shows one viewer what really stands at some cells, where their chunk is loaded. */
    private static void takeBackFrom(final Player viewer, final GatePreview preview, final List<Cell> cells)
    {
        for (final Cell cell : cells)
        {
            if (preview.world().isChunkLoaded(cell.x() >> 4, cell.z() >> 4))
            {
                viewer.sendBlockChange(new Location(preview.world(), cell.x(), cell.y(), cell.z()),
                    preview.world().getBlockAt(cell.x(), cell.y(), cell.z()).getBlockData());
            }
        }
    }

    /** Takes back every fake block a preview sent, from whoever is still here to see them. */
    static void takeBackAll(final Player owner, final GatePreview preview)
    {
        takeBack(owner, preview, preview.woosh());
        takeBack(owner, preview, preview.opening());
        // A stacked wormhole stood a block off the ring, which the cells above do not cover.
        // Only for viewers who were drawn stacked: everybody else was only ever sent the ring.
        watching(owner, preview).stream()
            .filter(viewer -> drewOffsetsFor(preview, viewer))
            .forEach(viewer -> PreviewLayers.handBackOffsets(viewer, preview));
    }

    /**
     * Whether a viewer may have been drawn a wormhole off the ring: settled stacked, or by a sweep,
     * which draws it there with no side recorded in {@code sides()} (#442). A sweep records every
     * watcher, so only one holding a cell somewhere other than its ring counts.
     */
    private static boolean drewOffsetsFor(final GatePreview preview, final Player viewer)
    {
        return preview.sides().containsKey(viewer.getUniqueId())
            || preview.sweepDrawn().getOrDefault(viewer.getUniqueId(), Map.of()).entrySet().stream()
                .anyMatch(held -> !held.getValue().equals(PreviewLayers.at(preview.opening().get(held.getKey()))));
    }

    /** The cells a fake block stands at now. */
    private static List<Cell> sentCells(final GatePreview preview)
    {
        final List<Cell> cells = new ArrayList<>();
        for (final List<Cell> part : List.of(preview.woosh(), preview.opening()))
        {
            part.stream().filter(cell -> preview.sent().contains(GatePreview.key(cell))).forEach(cells::add);
        }
        return cells;
    }

    /**
     * The owner, if given, and everyone the preview is shown to, who are online and in its world.
     *
     * @param owner
     *            its owner, or null when they are not here
     */
    static List<Player> watching(final Player owner, final GatePreview preview)
    {
        final List<Player> watching = new ArrayList<>();
        if ((owner != null) && preview.world().equals(owner.getWorld()))
        {
            watching.add(owner);
        }
        for (final UUID id : preview.shownTo())
        {
            final Player viewer = GatePreviews.online.apply(id);
            if ((viewer != null) && preview.world().equals(viewer.getWorld()))
            {
                watching.add(viewer);
            }
        }
        return watching;
    }

    /**
     * Brings who is shown the preview in line with who it is shared with: shows everything to anybody
     * new, and takes it all back from anybody gone or no longer shared with.
     */
    static void showToAudience(final Player owner, final GatePreview preview)
    {
        final Set<UUID> wanted = new LinkedHashSet<>(preview.sharedWith().keySet());
        if (preview.sharedWithAll())
        {
            preview.world().getPlayers().forEach(player -> wanted.add(player.getUniqueId()));
        }
        wanted.remove(owner.getUniqueId());
        wanted.removeIf(id ->
        {
            final Player viewer = GatePreviews.online.apply(id);
            return (viewer == null) || !preview.world().equals(viewer.getWorld());
        });
        for (final UUID id : new ArrayList<>(preview.shownTo()))
        {
            if (!wanted.contains(id))
            {
                preview.shownTo().remove(id);
                takeBackFromViewer(GatePreviews.online.apply(id), preview);
                preview.sides().remove(id);
            }
        }
        for (final UUID id : wanted)
        {
            if (preview.shownTo().add(id))
            {
                showToNewViewer(GatePreviews.online.apply(id), preview);
            }
        }
    }

    /**
     * Takes a preview off one viewer who is not being shown it any more.
     *
     * @param gone
     *            the viewer, which may be null if they are offline
     */
    private static void takeBackFromViewer(final Player gone, final GatePreview preview)
    {
        if ((gone == null) || !preview.world().equals(gone.getWorld()))
        {
            return;
        }
        preview.standingDisplays().forEach(display -> gone.hideEntity(WormholeXTreme.getThisPlugin(), display));
        takeBackFrom(gone, preview, sentCells(preview));
        // Only if they had a side, and before it is forgotten: a viewer drawn from the front
        // had their wormhole a block off the ring, which the cells above know nothing about.
        if (drewOffsetsFor(preview, gone))
        {
            PreviewLayers.handBackOffsets(gone, preview);
        }
    }

    /**
     * Catches one viewer up on a preview they have just been shown.
     */
    private static void showToNewViewer(final Player viewer, final GatePreview preview)
    {
        preview.standingDisplays().forEach(display -> viewer.showEntity(WormholeXTreme.getThisPlugin(), display));
        if (PreviewLayers.stacks(preview))
        {
            // Both iris sets are standing, and showing everything would hand them both. Their
            // own side settles which one they keep, and where their wormhole goes: the ring
            // cells the audience is otherwise caught up with are the wrong ones for somebody
            // in front.
            final List<IrisLayering.Placement> layers = PreviewLayers.layersFor(preview, viewer.getLocation());
            PreviewLayers.applySideFor(viewer, preview, layers);
            PreviewLayers.sendStacked(viewer, preview, layers);
            return;
        }
        sendTo(viewer, preview, sentCells(preview));
        if (preview.sweeping())
        {
            // What they were just sent, for the sweep to draw on from; and no layers to compare
            // their first move with, or one from before they were unshared skips it.
            preview.sweepDrawn().put(viewer.getUniqueId(), PreviewIrisSweep.heldBeforeTheSweep(preview, null));
            preview.sweepSides().remove(viewer.getUniqueId());
        }
    }
}
