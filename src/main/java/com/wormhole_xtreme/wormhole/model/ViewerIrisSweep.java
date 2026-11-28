package com.wormhole_xtreme.wormhole.model;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.utils.MaterialUtils;

/**
 * What each viewer near a gate is drawn while its iris sweeps over a wormhole, from their own side (#429, #447, #734).
 *
 * <p>{@link StargateIrisAnimator} drives it a ring at a time; {@link StargateBlockSetup} owns the layers it
 * moves, and drops a viewer's record here when it draws or takes back the lot.
 */
final class ViewerIrisSweep
{
    /** Static helpers only. */
    private ViewerIrisSweep()
    {
    }

    /**
     * The layers each viewer has been drawn by a gate's iris sweep, per cell the sweep has covered for them (#447).
     *
     * <p>Each step is worked out from where the viewer stands then, so this is what says where the far
     * layer they already hold is. A sweep called off hands it to the next one. Dropped for one viewer by
     * a whole draw or a take-back, which replace it, and on quit or a change of world; for everybody when
     * the gate's sweep ends ({@code GateCanvas.unregister}) or the gate is removed.
     */
    private static final Map<UUID,
        Map<String, Map<Integer, IrisLayering.Placement>>> SWEPT =
            new ConcurrentHashMap<>();

    /**
     * Draws one ring of an iris sweep, for everybody near, each from their own side (#429).
     *
     * <p>A gate with no layers -- a horizontal one, or one with no wormhole open -- looks the same
     * from everywhere, and gets the one shared {@link StargateBlockSetup#sendCells} it always had.
     *
     * <p>A layered gate does not, and one block for everybody gave everybody the front's picture:
     * a viewer behind watched the iris replace their wormhole a ring at a time, then jump behind it
     * when the sweep settled. So each viewer is moved, cell by cell, from what they hold to the
     * placement {@link IrisLayering} gives them. The ring belongs to whoever's near layer it is and
     * only the far layer moves: in front, the iris comes into the ring and the wormhole goes a
     * block behind; behind, the ring keeps the wormhole and the iris arrives a block beyond it.
     *
     * <p>Every cell the sweep has covered for a viewer is checked again at each step, not only this
     * step's: whoever has walked round since is handed back what is now on their own side and drawn
     * what is now on their far side (#447).
     *
     * @param gate
     *            the gate, whose iris is sweeping
     * @param ring
     *            the ring cells this step covers or uncovers
     * @param draw
     *            what the ring is drawn as on the side it changes: the iris closing, the bare
     *            opening opening; null for the real blocks
     * @param covering
     *            true as a ring is covered, false as one is uncovered
     */
    static void sweepRing(final Stargate gate, final List<Location> ring, final Material draw, final boolean covering)
    {
        final World world = sweptWorld(gate);
        if ((world == null) || (ring == null) || ring.isEmpty())
        {
            StargateBlockSetup.sendCells(gate, ring, draw);
            return;
        }
        final Set<IrisLayering.At> wanted = new HashSet<>(StargateBlockSetup.asPositions(ring));
        // Measured from the step's own first cell, as sendCells measures it.
        final Location from = ring.get(0);
        // A set: asked once for every player in the world, on every step.
        final Set<Player> near = new HashSet<>(
            StargateBlockSetup.playersNear(gate,
                new Location(world, from.getBlockX(), from.getBlockY(), from.getBlockZ())));
        if (near.isEmpty())
        {
            // Built only once somebody is near, as sendCells does: createBlockData needs a live server.
            world.getPlayers().forEach(player -> forgetStep(player, gate, wanted));
            return;
        }
        final SweepStep step = new SweepStep(wanted,
            (draw == null) ? null : MaterialUtils.drawnAcross(draw, gate.getGateFacing()),
            MaterialUtils.drawnAcross(gate.getEffectiveIrisMaterial(), gate.getGateFacing()), covering);
        for (final Player player : world.getPlayers())
        {
            if (near.contains(player))
            {
                sweepRingFor(player, gate, ring, step);
            }
            else
            {
                // Nothing is sent out of range, and they keep what they hold until the next redraw.
                forgetStep(player, gate, wanted);
            }
        }
    }

    /**
     * Draws some of a gate's cells as the opening looked before its iris closed, for a closing sweep to uncover.
     *
     * <p>Shared on a gate with no layers, as it always was. Over a wormhole each viewer is sent the horizon,
     * which leaves their ring alone after this.
     *
     * @param cells
     *            the cells to draw
     * @param under
     *            what the opening showed, for a gate with no layers
     */
    static void sendUncovered(final Stargate gate, final List<Location> cells, final Material under)
    {
        final World world = sweptWorld(gate);
        if ((world == null) || (cells == null) || cells.isEmpty())
        {
            StargateBlockSetup.sendCells(gate, cells, under);
            return;
        }
        final Location from = cells.get(0);
        final Material portal = gate.getEffectivePortalMaterial();
        for (final Player player
            : StargateBlockSetup.playersNear(gate,
                new Location(world, from.getBlockX(), from.getBlockY(), from.getBlockZ())))
        {
            sendRingTo(player, gate, cells,
                MaterialUtils.drawnAcross(portal, gate.getGateFacing()));
        }
    }

    /**
     * One step of a layered sweep, as every viewer near it is drawn it.
     *
     * @param wanted
     *            the cells this step covers or uncovers
     * @param drawn
     *            what a ring cell the step uncovers is drawn as, or null for its real block
     * @param iris
     *            what the iris is drawn as, wherever it goes
     * @param covering
     *            true as the step covers its cells
     */
    private record SweepStep(Set<IrisLayering.At> wanted, BlockData drawn, BlockData iris, boolean covering)
    {
    }

    /**
     * The world of a gate with layers for a sweep to move: a wormhole to draw, and an iris drawn rather than built.
     *
     * <p>Not {@link StargateBlockSetup#isLayered}, which asks whether the iris is shut. An opening sweep runs with that
     * flag already cleared -- setIrisState writes it before it draws -- so asking would have refused
     * the hand-back on every ring of every open.
     *
     * <p>And a name, which what each viewer was drawn is filed under.
     *
     * @return the world, or null if there is nothing to move
     */
    private static World sweptWorld(final Stargate gate)
    {
        return ((gate != null) && (gate.getGateName() != null) && gate.isGatePortalOpen()
            && StargateBlockSetup.irisIsDrawn(gate))
            ? gate.getGateWorld() : null;
    }

    /**
     * The same for one player, whose own side decides where each layer goes.
     *
     * @param stepRing
     *            the ring cells this step covers or uncovers
     */
    private static void sweepRingFor(final Player player, final Stargate gate, final List<Location> stepRing,
        final SweepStep step)
    {
        final Map<String, Map<Integer, IrisLayering.Placement>> byGate = SWEPT.get(player.getUniqueId());
        final Map<Integer, IrisLayering.Placement> known = (byGate == null) ? null : byGate.get(gate.getGateName());
        if (!step.covering() && ((known == null) || known.isEmpty()))
        {
            // Opening with nothing covered for them, which is somebody who came near after it began: nothing
            // of theirs to move, so the ring is shown them as it always was, and the settle does the rest.
            sendRingTo(player, gate, stepRing, step.drawn());
            return;
        }
        // Filed only now: closing, this step covers its cells for them, so something goes in it.
        final Map<Integer, IrisLayering.Placement> held = (known != null) ? known : sweptFor(player, gate);
        final List<IrisLayering.Placement> layers = StargateBlockSetup.layersFor(gate, player.getLocation());
        final List<Location> opening = gate.getGatePortalBlocks();
        for (int i = 0; i < layers.size(); i++)
        {
            final IrisLayering.At cell = positionOf(opening.get(i));
            final boolean stepped = step.wanted().contains(cell);
            final IrisLayering.Placement before = held.get(i);
            // Covered once this step is done: closing, what already was and this step's cells; opening,
            // what was and is not this step's.
            final boolean covered = (before != null) ? (step.covering() || !stepped) : (step.covering() && stepped);
            final IrisLayering.Placement after = covered ? layers.get(i) : null;
            moveRingCell(player, gate, cell, irisInRing(before, cell), irisInRing(after, cell),
                stepped && !step.covering(), step);
            moveOffRing(player, gate, cell, before, after, step.iris());
            remember(held, i, before, after);
        }
    }

    /**
     * Sends one player a step's ring as one picture, the way {@link StargateBlockSetup#sendCells} sends it to everybody.
     *
     * @param drawn
     *            what to draw the cells as, or null for their real blocks
     */
    private static void sendRingTo(final Player player, final Stargate gate, final List<Location> stepRing,
        final BlockData drawn)
    {
        for (final Location bc : stepRing)
        {
            final Location at = new Location(gate.getGateWorld(), bc.getBlockX(), bc.getBlockY(), bc.getBlockZ());
            player.sendBlockChange(at, (drawn != null) ? drawn
                : gate.getGateWorld().getBlockAt(bc.getBlockX(), bc.getBlockY(), bc.getBlockZ()).getBlockData());
        }
    }

    /**
     * Whether a placement puts the iris in the ring itself.
     *
     * @param placed
     *            the placement, or null for a cell not covered
     * @return true only for a covered cell whose iris is the near layer
     */
    private static boolean irisInRing(final IrisLayering.Placement placed, final IrisLayering.At cell)
    {
        return (placed != null) && cell.equals(placed.iris());
    }

    /**
     * Redraws one ring cell for one viewer, only if the iris comes into it or leaves it.
     *
     * <p>Otherwise the ring is the wormhole's, before and after, and is not touched: that is the whole of
     * what a viewer behind the gate is owed while it sweeps.
     *
     * @param before
     *            whether the iris is in the ring for them now
     * @param after
     *            whether it is to be
     * @param uncovering
     *            true if this step uncovers the cell, which draws it as the step says; otherwise the iris
     *            leaves the ring because the viewer walked round, and the wormhole takes it
     */
    private static void moveRingCell(final Player player, final Stargate gate, final IrisLayering.At cell,
        final boolean before, final boolean after, final boolean uncovering, final SweepStep step)
    {
        if (before == after)
        {
            return;
        }
        final Location at = StargateBlockSetup.located(gate, cell);
        if (after)
        {
            player.sendBlockChange(at, step.iris());
        }
        else if (!uncovering)
        {
            player.sendBlockChange(at, MaterialUtils.drawnAcross(DrawnHorizon.materialFor(
                gate.getEffectivePortalMaterial(), gate.getEffectiveIrisMaterial(), cell, false), gate.getGateFacing()));
        }
        else
        {
            player.sendBlockChange(at, (step.drawn() != null) ? step.drawn()
                : gate.getGateWorld().getBlockAt(cell.x(), cell.y(), cell.z()).getBlockData());
        }
    }

    /** Files one cell's new placement in a viewer's sweep record, touching it only if it changed. */
    private static void remember(final Map<Integer, IrisLayering.Placement> held, final int index,
        final IrisLayering.Placement before, final IrisLayering.Placement after)
    {
        if (Objects.equals(before, after))
        {
            return;
        }
        if (after == null)
        {
            held.remove(index);
        }
        else
        {
            held.put(index, after);
        }
    }

    /**
     * Moves one cell's layers off the ring for one viewer, from one placement to the next.
     *
     * <p>The far horizon, a block behind from in front, and the far iris, a block beyond from behind, are each
     * handed back and drawn again where they now belong. They are on opposite sides of the ring, so neither
     * can land where the other is being handed back.
     *
     * @param cell
     *            the ring cell they belong to
     * @param before
     *            what they hold, or null for a cell not covered
     * @param after
     *            what they are to hold, or null for a cell not covered
     * @param iris
     *            what the iris is drawn as, or null if no placement here can put it off the ring
     */
    private static void moveOffRing(final Player player, final Stargate gate, final IrisLayering.At cell,
        final IrisLayering.Placement before, final IrisLayering.Placement after, final BlockData iris)
    {
        final IrisLayering.At irisFrom = farIrisOf(before, cell);
        final IrisLayering.At irisTo = farIrisOf(after, cell);
        if (!Objects.equals(irisFrom, irisTo))
        {
            if (irisFrom != null)
            {
                StargateBlockSetup.sendTruthIfFree(player, StargateBlockSetup.located(gate, irisFrom));
            }
            if (irisTo != null)
            {
                player.sendBlockChange(StargateBlockSetup.located(gate, irisTo), iris);
            }
        }
        moveFarLayer(player, gate, cell, farLayerOf(before, cell), farLayerOf(after, cell));
    }

    /**
     * Where a placement puts the iris off the ring.
     *
     * @param placed
     *            the placement, or null for a cell not covered
     * @return the position, or null when the iris has the ring or is not drawn
     */
    private static IrisLayering.At farIrisOf(final IrisLayering.Placement placed, final IrisLayering.At cell)
    {
        if ((placed == null) || cell.equals(placed.iris()))
        {
            return null;
        }
        return placed.iris();
    }

    /**
     * Moves one cell's far horizon for one viewer, sending nothing if it stays where it was.
     *
     * @param cell
     *            the ring cell it belongs to
     * @param from
     *            where they hold it now, or null for nowhere
     * @param to
     *            where it goes, or null for nowhere
     */
    private static void moveFarLayer(final Player player, final Stargate gate, final IrisLayering.At cell,
        final IrisLayering.At from, final IrisLayering.At to)
    {
        if (Objects.equals(from, to))
        {
            return;
        }
        if (from != null)
        {
            StargateBlockSetup.sendTruthIfFree(player, StargateBlockSetup.located(gate, from));
        }
        if (to != null)
        {
            player.sendBlockChange(StargateBlockSetup.located(gate, to),
                MaterialUtils.drawnAcross(
                    DrawnHorizon.materialFor(gate.getEffectivePortalMaterial(), gate.getEffectiveIrisMaterial(),
                        cell, true),
                    gate.getGateFacing()));
        }
    }

    /**
     * Where a placement puts the horizon off the ring.
     *
     * @param placed
     *            the placement, or null for a cell not covered
     * @return the position, or null when the horizon has the ring or is not drawn
     */
    private static IrisLayering.At farLayerOf(final IrisLayering.Placement placed, final IrisLayering.At cell)
    {
        if ((placed == null) || cell.equals(placed.horizon()))
        {
            return null;
        }
        return placed.horizon();
    }

    /**
     * Sets each viewer near a gate up for a sweep that is starting (#447).
     *
     * <p>Closing, no cell is covered yet, so any layer left by an opening called off part way is
     * handed back. Opening, every cell is, each with the layers the viewer was last drawn. Anybody out
     * of range is forgotten, as one who arrives mid-sweep is never known.
     *
     * @param closing
     *            true for a closing sweep
     */
    static void startHorizonSweep(final Stargate gate, final boolean closing)
    {
        final World world = sweptWorld(gate);
        if (world == null)
        {
            return;
        }
        for (final Player player : world.getPlayers())
        {
            final Map<String, Map<Integer, IrisLayering.Placement>> byGate = SWEPT.get(player.getUniqueId());
            final Map<Integer, IrisLayering.Placement> before =
                (byGate == null) ? null : byGate.remove(gate.getGateName());
            if (!StargateBlockSetup.isNearEnoughToRedraw(gate, player.getLocation()))
            {
                continue;
            }
            if (closing)
            {
                handBackSwept(player, gate, before);
            }
            else
            {
                sweptOf(player).put(gate.getGateName(), wholeOpening(player, gate, before));
            }
        }
    }

    /**
     * Hands back every layer off the ring a sweep knows one viewer holds.
     *
     * @param held
     *            what it drew them, or null for nothing
     */
    private static void handBackSwept(final Player player, final Stargate gate,
        final Map<Integer, IrisLayering.Placement> held)
    {
        if (held == null)
        {
            return;
        }
        final List<Location> ring = gate.getGatePortalBlocks();
        held.forEach((i, placed) ->
        {
            if (i < ring.size())
            {
                final IrisLayering.At cell = positionOf(ring.get(i));
                moveOffRing(player, gate, cell, placed, null, null);
            }
        });
    }

    /**
     * Every cell covered, each with the layer one viewer holds: from a sweep called off, else their last whole draw.
     *
     * @param before
     *            what a sweep called off drew them, or null
     * @return their record, one placement per cell
     */
    private static Map<Integer, IrisLayering.Placement> wholeOpening(final Player player, final Stargate gate,
        final Map<Integer, IrisLayering.Placement> before)
    {
        final List<Location> ring = gate.getGatePortalBlocks();
        final Map<String, List<IrisLayering.Placement>> drawnFor =
            StargateBlockSetup.LAYER_DRAWN.get(player.getUniqueId());
        final List<IrisLayering.Placement> drawn = (drawnFor == null) ? null : drawnFor.get(gate.getGateName());
        return IntStream.range(0, ring.size()).boxed().collect(Collectors.toMap(Function.identity(), i ->
        {
            final IrisLayering.Placement known = (before != null) ? before.get(i) : placementAt(drawn, i);
            // Covered with nothing behind: the call-off finished the iris over cells it had not reached.
            return (known != null) ? known : new IrisLayering.Placement(positionOf(ring.get(i)), null);
        }, (a, b) -> a, ConcurrentHashMap::new));
    }

    /**
     * One cell's placement from a whole draw.
     *
     * @param drawn
     *            the draw, or null if there was none
     * @return the placement, or null if the draw has none for that cell
     */
    private static IrisLayering.Placement placementAt(final List<IrisLayering.Placement> drawn, final int index)
    {
        return ((drawn != null) && (index < drawn.size())) ? drawn.get(index) : null;
    }

    /**
     * Forgets this step's cells from an out-of-range viewer's record, so one back mid-sweep is not drawn a
     * layer behind cells uncovered while they were away.
     *
     * @param wanted
     *            the step's cells, or null for all of them
     */
    private static void forgetStep(final Player player, final Stargate gate, final Set<IrisLayering.At> wanted)
    {
        final Map<String, Map<Integer, IrisLayering.Placement>> byGate = SWEPT.get(player.getUniqueId());
        if (byGate == null)
        {
            return;
        }
        final Map<Integer, IrisLayering.Placement> held = byGate.get(gate.getGateName());
        if (held == null)
        {
            return;
        }
        final List<Location> ring = gate.getGatePortalBlocks();
        held.keySet().removeIf(i ->
            (wanted == null) || ((i < ring.size()) && wanted.contains(positionOf(ring.get(i)))));
    }

    /** One viewer's sweep record for a gate, which has a name, created empty if there is none. */
    private static Map<Integer, IrisLayering.Placement> sweptFor(final Player player, final Stargate gate)
    {
        return sweptOf(player).computeIfAbsent(gate.getGateName(), key -> new ConcurrentHashMap<>());
    }

    /** One viewer's sweep records, by gate, created empty if there are none. */
    private static Map<String, Map<Integer, IrisLayering.Placement>> sweptOf(final Player player)
    {
        return SWEPT.computeIfAbsent(player.getUniqueId(), key -> new ConcurrentHashMap<>());
    }

    /** Drops one viewer's sweep record for a gate, once something has drawn or taken back the lot. */
    static void dropSwept(final Player player, final Stargate gate)
    {
        final Map<String, Map<Integer, IrisLayering.Placement>> byGate = SWEPT.get(player.getUniqueId());
        if ((byGate != null) && (gate.getGateName() != null))
        {
            byGate.remove(gate.getGateName());
        }
    }

    /**
     * Forgets what any sweep drew anybody for a gate: its sweep has ended, or the gate has gone.
     *
     * <p>Everybody, out of range too. A gate registered again under the same name may have another
     * shape, and an old record would be read against cells it does not have.
     *
     * @param name
     *            the gate's name, or null for none
     */
    static void forgetSwept(final String name)
    {
        if (name != null)
        {
            SWEPT.values().forEach(byGate -> byGate.remove(name));
        }
    }

    /**
     * Forgets what any sweep drew one player, whose client has dropped those chunks.
     *
     * @param uuid
     *            the player
     */
    static void forgetSwept(final UUID uuid)
    {
        SWEPT.remove(uuid);
    }

    /** A block location as plain coordinates. */
    private static IrisLayering.At positionOf(final Location block)
    {
        return new IrisLayering.At(block.getBlockX(), block.getBlockY(), block.getBlockZ());
    }
}
