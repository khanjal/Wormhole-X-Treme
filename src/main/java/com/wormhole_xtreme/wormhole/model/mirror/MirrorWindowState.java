package com.wormhole_xtreme.wormhole.model.mirror;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.structure.StructureRotation;

import com.wormhole_xtreme.wormhole.model.mirror.MirrorWindow.Spot;

/**
 * One window as the server holds it: its shape, the banner it hangs on, and what it last read.
 *
 * <p>{@link MirrorWindow} is the shape alone, plain numbers and no server. This is that shape
 * put in a world: the mirror it belongs to, the banner block, the capture it draws from, the
 * turn and flip its far-side blocks need, and the wall face and held room as last worked out.
 *
 * <p>Mutable and package-private on purpose. It is the scratch pad {@link MirrorWindows} keeps
 * for one window between sweeps, so its fields are read and written directly by the code that
 * draws; nothing outside this package can see it. Split out of {@link MirrorWindows}, which had
 * grown to hold every part of drawing a room.
 */
final class MirrorWindowState
{
    final QuantumMirror mirror;
    final MirrorWindow shape;
    final Block banner;
    final List<Spot> open;
    final Set<Long> openKeys = new HashSet<>();
    final MirrorCapture capture;
    /** The turn far-side blocks need to face the right way here. */
    final StructureRotation rotation;
    /** The flip across the wall a reflection's blocks need, or none. */
    final org.bukkit.block.structure.Mirror flip;
    /** Far-side states turned by {@link #rotation}, each turned once. */
    final Map<BlockData, BlockData> turned = new IdentityHashMap<>();
    Set<Long> solid = Set.of();
    /** The outermost ring of the solid face, each with the sides it is open on ({@link MirrorFace#marginOf}). */
    Map<Long, Integer> margin = Map.of();
    /** The solid blocks of the face touching the opening, corners too: its frame. */
    List<Spot> frame = List.of();
    /** How many blocks of solid wall stand on every side of the opening, as last read ({@link MirrorFace#borderOf}). */
    int border;
    long solidAt;
    /** Everything behind a walled window, drawn whatever the eye; null until first wanted. */
    Map<Long, BlockData> fixed;
    MirrorCapture fixedFrom;
    long fixedAt;
    int fixedFor;
    int fixedDepth;
    long fixedUsedAt;
    /** The whole capture through this window, for an admin who asked; null until then. */
    MirrorWindows.Whole full;
    MirrorCapture fullFrom;

    MirrorWindowState(final QuantumMirror mirror, final MirrorWindow shape, final Block banner,
        final List<Spot> open, final MirrorCapture capture)
    {
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
            this.flip = org.bukkit.block.structure.Mirror.NONE;
        }
        else
        {
            this.flip = (shape.into().x() != 0) ? org.bukkit.block.structure.Mirror.FRONT_BACK
                : org.bukkit.block.structure.Mirror.LEFT_RIGHT;
        }
        open.forEach(cell -> openKeys.add(MirrorWindows.key(cell.x(), cell.y(), cell.z())));
    }
}
