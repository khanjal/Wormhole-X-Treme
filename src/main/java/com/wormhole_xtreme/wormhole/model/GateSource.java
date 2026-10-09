package com.wormhole_xtreme.wormhole.model;

import java.util.List;

import org.bukkit.block.Block;

import com.wormhole_xtreme.wormhole.model.window.Captures;
import com.wormhole_xtreme.wormhole.model.window.Place;
import com.wormhole_xtreme.wormhole.model.window.WindowShape;
import com.wormhole_xtreme.wormhole.model.window.WindowShape.Spot;
import com.wormhole_xtreme.wormhole.model.window.WindowSource;

/**
 * An open gate as the window drawing sees it (#516): what the sweep calls it, where it is measured
 * from, its opening, where it goes, and how deep its view is.
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
 * @param step
 *            how far past the opening the first step of its capture reaches: {@code gate-view-depth}
 * @param depth
 *            how far past the opening its view is drawn, from the capture it has
 */
public record GateSource(String name, Block anchor, WindowShape shape, List<Spot> open, Place destination,
    String target, int step, int depth) implements WindowSource
{
    /**
     * A gate drawn to the first step of its capture.
     *
     * @param name
     *            what the sweep knows it by
     * @param anchor
     *            a block of the opening
     * @param shape
     *            the opening
     * @param open
     *            the opening's cells
     * @param destination
     *            where travellers land
     * @param target
     *            the gate they land in front of
     * @param step
     *            {@code gate-view-depth}
     */
    public GateSource(final String name, final Block anchor, final WindowShape shape, final List<Spot> open,
        final Place destination, final String target, final int step)
    {
        this(name, anchor, shape, open, destination, target, step, step);
    }

    /** @return the key its capture is kept under: the far gate's one, seen through the largest opening */
    public String captureKey()
    {
        return Captures.gateKey(target, Captures.GATE_OPENING, Captures.GATE_OPENING);
    }

    /**
     * @param drawn
     *            how far past the opening its view is drawn
     * @return the same gate drawn that far
     */
    public GateSource drawnTo(final int drawn)
    {
        return new GateSource(name, anchor, shape, open, destination, target, step, drawn);
    }

    @Override
    public boolean walkThrough()
    {
        return true;
    }
}
