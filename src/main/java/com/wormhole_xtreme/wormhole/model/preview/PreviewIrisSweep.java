package com.wormhole_xtreme.wormhole.model.preview;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Cell;
import com.wormhole_xtreme.wormhole.model.DrawnHorizon;
import com.wormhole_xtreme.wormhole.model.IrisLayering;
import com.wormhole_xtreme.wormhole.model.IrisSweepDriver;

/**
 * How a build preview's iris crosses: the rings drawn a step at a time, the wormhole kept in step
 * for each viewer, and the sweep called off when the iris is toggled again (#430, #734).
 */
final class PreviewIrisSweep
{
    /** Static helpers only. */
    private PreviewIrisSweep()
    {
    }

    /** How a preview's iris crosses (#427): its group's animation, else the server's, as a gate's. */
    private static String irisAnimation(final GatePreview preview)
    {
        return ConfigManager.getGateIrisAnimation(null, GatePreviews.groupByFrame(preview));
    }

    /**
     * Moves the preview's iris a ring at a time, the way a real gate's does.
     *
     * <p>The preview is display entities rather than blocks, so none of the care a real gate
     * needs applies -- there is nothing here to walk through, and no {@code BlockPhysicsEvent}
     * to raise. What matters is that it looks the same: the gate's own {@link IrisSweepDriver},
     * with the same rings in the same order at the same pace and the same limit that merges a
     * big gate's rings into bands, so a preview
     * is a rehearsal of the gate rather than an approximation of one.
     *
     * <p>Instant when the setting says so, or when there is no scheduler to book a step with.
     */
    static void startIrisSweep(final Player owner, final GatePreview preview)
    {
        cancelIrisSweep(preview);
        final List<Cell> cells = preview.opening();
        if (!ConfigManager.isIrisAnimated(irisAnimation(preview)) || (cells.size() < 2))
        {
            // A sweep called off for this one may have stood wormhole off the ring, which the
            // draw below knows nothing about.
            handBackSwept(owner, preview);
            preview.irisShownEverywhere(preview.isGateIrisActive());
            GatePreviews.restyle(preview);
            GatePreviews.draw(owner, preview);
            return;
        }
        // Closing starts from nothing shown and adds rings; opening starts from all of it and
        // takes them away. Either way the set ends agreeing with the iris flag.
        preview.irisShownEverywhere(!preview.isGateIrisActive());
        GatePreviews.restyle(preview);
        // Marked before the first ring: the layers stay where they are until the sweep ends,
        // and the first draw is part of the sweep.
        preview.sweeping(true);
        // What each viewer holds now. A sweep called off for this one leaves its own record,
        // which is the truer one.
        for (final Player viewer : PreviewAudience.watching(owner, preview))
        {
            preview.sweepDrawn().computeIfAbsent(viewer.getUniqueId(),
                id -> heldBeforeTheSweep(preview, preview.sides().get(id)));
        }
        new IrisSweepDriver<>(ringsOfOpening(preview, preview.isGateIrisActive()), new IrisCanvas(owner, preview))
            .start();
    }

    /**
     * The opening's cell indexes, grouped into rings in the order they are drawn.
     *
     * @param closing
     *            true for the closing order, rim first
     * @return one list of indexes per ring
     */
    private static List<List<Integer>> ringsOfOpening(final GatePreview preview, final boolean closing)
    {
        final List<Location> places = preview.opening().stream()
            .map(cell -> new Location(preview.world(), cell.x(), cell.y(), cell.z()))
            .toList();
        return IrisSweepDriver.ringIndexes(places, irisAnimation(preview), closing);
    }

    /**
     * A preview's sweep: its displays for the rings, {@link GatePreviews#irisLater} for the steps.
     *
     * <p>The direction is read off the preview's iris at every step rather than fixed at the
     * start, as it always was.
     */
    private record IrisCanvas(Player owner, GatePreview preview) implements IrisSweepDriver.Canvas<Integer>
    {
        @Override
        public boolean stillValid()
        {
            return stillHeld(preview);
        }

        @Override
        public void drawRing(final List<Integer> ring)
        {
            for (final int cell : ring)
            {
                if (preview.isGateIrisActive())
                {
                    preview.irisShown().add(cell);
                }
                else
                {
                    preview.irisShown().remove(cell);
                }
            }
            GatePreviews.draw(owner, preview);
        }

        @Override
        public void moveHorizon(final List<Integer> ring)
        {
            // Nothing left to move: drawRing's draw has just brought every viewer's wormhole in
            // line, this ring's included.
        }

        /** Its own seam, which a test swaps out to drive an animation by hand. */
        @Override
        public IrisSweepDriver.Booking later(final long ticks, final Runnable step)
        {
            final BukkitTask task = GatePreviews.irisLater.after(ticks, step);
            return () ->
            {
                if (task != null)
                {
                    task.cancel();
                }
            };
        }

        @Override
        public void register(final IrisSweepDriver<Integer> sweep)
        {
            irisSweeps.put(preview, sweep);
        }

        @Override
        public void unregister()
        {
            irisSweeps.remove(preview);
            preview.sweeping(false);
            // The settle, or the teardown that stopped it, draws from scratch.
            preview.sweepDrawn().clear();
        }

        @Override
        public void settle()
        {
            if (stillHeld(preview))
            {
                // Unregistered first, so this draw is the one that stacks: the wormhole moves
                // behind the ring, and a viewer round the back gets it in the ring with the iris
                // beyond, now that there is no sweep left to cover.
                GatePreviews.draw(owner, preview);
            }
        }

        /**
         * Whether a preview is still one somebody holds.
         *
         * <p>A step that fired after its preview was cleared would spawn fresh displays for one
         * nobody is holding any more, and nothing left would ever take them away again.
         *
         * @return true if it is still in the register
         */
        private static boolean stillHeld(final GatePreview preview)
        {
            for (final List<GatePreview> held : GatePreviews.PREVIEWS.values())
            {
                if (held.contains(preview))
                {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Moves a viewer's stand-in wormhole with them when they cross a preview mid-sweep (#442).
     *
     * <p>The sweep stands the ice for each covered ring on the viewer's far side. Walking round left
     * it on their old far side, now their near side: a solid block the server does not have, a pace
     * from the preview.
     */
    static void resweepIfCrossed(final Player player, final GatePreview preview, final Location to)
    {
        if (!preview.open() || !movesOffTheRing(preview))
        {
            return;
        }
        final List<IrisLayering.Placement> now = PreviewLayers.layersFor(preview, to);
        if (!now.equals(preview.sweepSides().put(player.getUniqueId(), now)))
        {
            redrawSwept(player, preview, now);
        }
    }

    /**
     * Whether a sweep stands the wormhole off the ring: behind an iris that hides real water, on a
     * preview the settle will stack, which is what hands it back.
     */
    private static boolean movesOffTheRing(final GatePreview preview)
    {
        return PreviewLayers.upright(preview) && DrawnHorizon.standsIn(preview.palette().iris());
    }

    /**
     * Brings every watcher's wormhole in line with a sweep as it stands (#432).
     *
     * <p>Mid-sweep a covered ring's wormhole is in the ring for a viewer behind and a block behind
     * it for one in front. Sending the wormhole into everybody's ring at every step put water back
     * under the glass over every ring already covered, so only the newest ring looked swept.
     */
    static void sweepWormhole(final Player owner, final GatePreview preview)
    {
        preview.opening().forEach(cell -> preview.sent().add(GatePreview.key(cell)));
        final boolean offTheRing = movesOffTheRing(preview);
        for (final Player viewer : PreviewAudience.watching(owner, preview))
        {
            List<IrisLayering.Placement> layers = null;
            if (offTheRing)
            {
                layers = PreviewLayers.layersFor(preview, viewer.getLocation());
                preview.sweepSides().put(viewer.getUniqueId(), layers);
            }
            redrawSwept(viewer, preview, layers);
        }
    }

    /**
     * Sends one viewer the cells whose wormhole has moved since the sweep last drew it for them.
     *
     * @param layers
     *            where their layers go, or null where the sweep keeps every wormhole in the ring
     */
    // Runs on every sweep step and every crossing move, so it stays a plain loop.
    @SuppressWarnings("java:S9391")
    private static void redrawSwept(final Player viewer, final GatePreview preview,
        final List<IrisLayering.Placement> layers)
    {
        final Map<Integer, IrisLayering.At> held =
            preview.sweepDrawn().computeIfAbsent(viewer.getUniqueId(), id -> new HashMap<>());
        for (int i = 0; i < preview.opening().size(); i++)
        {
            final Cell cell = preview.opening().get(i);
            final IrisLayering.At ring = PreviewLayers.at(cell);
            final IrisLayering.At wanted = sweptTo(preview, i, ring, layers);
            final IrisLayering.At was = held.put(i, wanted);
            if (wanted.equals(was))
            {
                continue;
            }
            if (was != null)
            {
                PreviewLayers.takeBackAt(viewer, preview, was);
            }
            drawSweptAt(viewer, preview, cell, wanted);
        }
    }

    /** Sends one viewer one cell's wormhole where the sweep holds it: in the ring, or its stand-in off it. */
    private static void drawSweptAt(final Player viewer, final GatePreview preview, final Cell cell,
        final IrisLayering.At where)
    {
        final IrisLayering.At ring = PreviewLayers.at(cell);
        if (where.equals(ring))
        {
            PreviewAudience.sendTo(viewer, preview, List.of(cell));
            return;
        }
        viewer.sendBlockChange(new Location(preview.world(), where.x(), where.y(), where.z()),
            PreviewLayers.horizonData(preview, ring, new IrisLayering.Placement(ring, where)));
    }

    /**
     * Sends every watcher their whole wormhole as a running sweep holds it.
     *
     * <p>A step sends only what moved, so a client that lost the blocks -- a chunk reloaded, or out
     * of range when they were sent -- and a palette changed mid-sweep are put right here, by the
     * periodic redraw and by the change itself.
     */
    private static void resendSwept(final Player owner, final GatePreview preview)
    {
        if (!preview.sweeping() || !preview.open())
        {
            return;
        }
        for (final Player viewer : PreviewAudience.watching(owner, preview))
        {
            preview.sweepDrawn().getOrDefault(viewer.getUniqueId(), Map.of())
                .forEach((index, where) -> drawSweptAt(viewer, preview, preview.opening().get(index), where));
        }
    }

    /** Draws a preview, and sends a running sweep's whole picture again as well. */
    static void redraw(final Player owner, final GatePreview preview)
    {
        GatePreviews.draw(owner, preview);
        resendSwept(owner, preview);
    }

    /**
     * Where one cell's wormhole stands mid-sweep: off the ring where the iris covers it and the
     * viewer's layers put it there, in the ring everywhere else, including where there is no room.
     */
    private static IrisLayering.At sweptTo(final GatePreview preview, final int index, final IrisLayering.At ring,
        final List<IrisLayering.Placement> layers)
    {
        if ((layers == null) || !preview.irisShownAt(index))
        {
            return ring;
        }
        final IrisLayering.At where = layers.get(index).horizon();
        return (where == null) ? ring : where;
    }

    /**
     * Where a viewer's wormhole stands as a sweep starts: where their settled layers put it, or the
     * ring cells sent to everybody.
     *
     * @param settled
     *            their stacked layers, or null if they were drawn unstacked
     * @return a record the sweep may add to
     */
    static Map<Integer, IrisLayering.At> heldBeforeTheSweep(final GatePreview preview,
        final List<IrisLayering.Placement> settled)
    {
        final List<Cell> opening = preview.opening();
        return IntStream.range(0, opening.size())
            .filter(i -> preview.sent().contains(GatePreview.key(opening.get(i))))
            .filter(i -> heldAt(opening, settled, i) != null)
            .boxed()
            .collect(Collectors.toMap(Function.identity(), i -> heldAt(opening, settled, i),
                (first, second) -> first, HashMap::new));
    }

    /** Where one cell's wormhole was drawn before a sweep, or null where it was drawn nowhere. */
    private static IrisLayering.At heldAt(final List<Cell> opening, final List<IrisLayering.Placement> settled,
        final int index)
    {
        return (settled == null) ? PreviewLayers.at(opening.get(index)) : settled.get(index).horizon();
    }

    /** Hands every watcher back whatever a sweep stood off the ring for them, and forgets what it drew. */
    static void handBackSwept(final Player owner, final GatePreview preview)
    {
        for (final Player viewer : PreviewAudience.watching(owner, preview))
        {
            final Map<Integer, IrisLayering.At> held =
                preview.sweepDrawn().getOrDefault(viewer.getUniqueId(), Map.of());
            held.forEach((index, where) ->
            {
                if (!where.equals(PreviewLayers.at(preview.opening().get(index))))
                {
                    PreviewLayers.takeBackAt(viewer, preview, where);
                }
            });
        }
        preview.sweepDrawn().clear();
    }

    /**
     * Calls off a sweep running on a preview, leaving its iris where it was going.
     *
     * <p>Called when the iris is toggled again mid-sweep, and when a preview goes away -- a
     * step that fired afterwards would spawn displays for a preview nobody is holding any more.
     */
    static void cancelIrisSweep(final GatePreview preview)
    {
        final IrisSweepDriver<Integer> sweep = irisSweeps.remove(preview);
        preview.sweeping(false);
        if (sweep != null)
        {
            sweep.cancel();
        }
    }

    /** The sweep running on each preview, so a second toggle can call the first off. */
    private static final Map<GatePreview, IrisSweepDriver<Integer>> irisSweeps = new IdentityHashMap<>();
}
