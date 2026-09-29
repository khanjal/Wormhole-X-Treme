package com.wormhole_xtreme.wormhole.model.mirror;

import java.util.List;

import org.bukkit.block.Block;

import com.wormhole_xtreme.wormhole.model.mirror.MirrorWindow.Spot;

/**
 * An open gate as the window drawing sees it (#516): what the sweep calls it, where it is measured
 * from, its opening, where it goes, and how deep its view is.
 *
 * <p>The first of what #522 calls a window source: the drawing still holds a stand-in mirror
 * for it, and this is what that stand-in is made from.
 *
 * @param name
 *            what the sweep knows it by, which no mirror can be called
 * @param anchor
 *            a block of the opening, which distances to it are measured from
 * @param shape
 *            the opening, onto where travellers land
 * @param open
 *            the opening's cells a view is seen through: the gate's portal cells
 * @param destination
 *            where travellers land, facing the way they leave
 * @param target
 *            the gate they land in front of, whose capture it is
 * @param depth
 *            how far past the opening the view is captured and drawn
 */
public record GateWindow(String name, Block anchor, MirrorWindow shape, List<Spot> open, MirrorPoint destination,
    String target, int depth)
{
    /** @return the key its capture is kept under: the far gate, and this opening's size */
    String captureKey()
    {
        return MirrorCaptures.gateKey(target, shape.width(), shape.height());
    }
}
