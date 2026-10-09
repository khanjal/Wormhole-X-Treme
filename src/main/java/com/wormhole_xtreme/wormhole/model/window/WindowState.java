package com.wormhole_xtreme.wormhole.model.window;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.mirror.QuantumMirror;

import com.wormhole_xtreme.wormhole.model.window.WindowShape.Spot;

/**
 * One window as the server holds it: its shape, the banner it hangs on, and what it last read.
 *
 * <p>{@link WindowShape} is the shape alone, plain numbers and no server. This is that shape
 * put in a world: the mirror it belongs to, the banner block, the capture it draws from, the
 * turn and flip its far-side blocks need, and the wall face and held room as last worked out.
 *
 * <p>Mutable and package-private on purpose. It is the scratch pad {@link Windows} keeps
 * for one window between sweeps, so its fields are read and written directly by the code that
 * draws; nothing outside this package can see it. Split out of {@link Windows}, which had
 * grown to hold every part of drawing a room.
 */
final class WindowState
{
    final QuantumMirror mirror;
    final WindowShape shape;
    final Block banner;
    final List<Spot> open;
    final Set<Long> openKeys = new HashSet<>();
    final Capture capture;
    /** The turn far-side blocks need to face the right way here. */
    final StructureRotation rotation;
    /** The flip across the wall a reflection's blocks need, or none. */
    final Mirror flip;
    /** Far-side states turned by {@link #rotation}, each turned once. */
    final Map<BlockData, BlockData> turned = new IdentityHashMap<>();
    Set<Long> solid = Set.of();
    /** The outermost ring of the solid face, each with the sides it is open on ({@link WindowFace#marginOf}). */
    Map<Long, Integer> margin = Map.of();
    /** The solid blocks of the face touching the opening, corners too: its frame. */
    List<Spot> frame = List.of();
    /** How many blocks of solid wall stand on every side of the opening, as last read ({@link WindowFace#borderOf}). */
    int border;
    long solidAt;
    /** Everything behind a walled window, drawn whatever the eye; null until first wanted. */
    Map<Long, BlockData> fixed;
    Capture fixedFrom;
    long fixedAt;
    int fixedFor;
    int fixedDepth;
    long fixedUsedAt;
    /** The whole capture through this window, for an admin who asked; null until then. */
    Windows.Whole full;
    Capture fullFrom;

    /**
     * A gate's opening rather than a banner's: walked through, so never barred, and with no banner
     * to take down while it is drawn.
     */
    final boolean walkThrough;

    /** How far past the opening its view is drawn: {@code mirror-view-depth} for a mirror, {@code gate-view-depth} for a gate. */
    final int depth;

    WindowState(final QuantumMirror mirror, final WindowShape shape, final Block banner,
        final List<Spot> open, final Capture capture)
    {
        this(mirror, shape, banner, open, capture, false, ConfigManager.getMirrorViewDepth());
    }

    WindowState(final QuantumMirror mirror, final WindowShape shape, final Block banner,
        final List<Spot> open, final Capture capture, final boolean walkThrough, final int depth)
    {
        this.walkThrough = walkThrough;
        this.depth = depth;
        this.mirror = mirror;
        this.shape = shape;
        this.banner = banner;
        this.open = open;
        this.capture = capture;
        this.rotation = switch (shape.quarterTurns())
        {
            case 1 -> StructureRotation.CLOCKWISE_90;
            case 2 -> StructureRotation.CLOCKWISE_180;
            case 3 -> StructureRotation.COUNTERCLOCKWISE_90;
            default -> StructureRotation.NONE;
        };
        // A reflection is flipped across the wall, not turned: stairs and doors keep their side.
        if (!shape.mirrored())
        {
            this.flip = Mirror.NONE;
        }
        else
        {
            this.flip = (shape.into().x() != 0) ? Mirror.FRONT_BACK
                : Mirror.LEFT_RIGHT;
        }
        open.forEach(cell -> openKeys.add(Windows.key(cell.x(), cell.y(), cell.z())));
    }
}
