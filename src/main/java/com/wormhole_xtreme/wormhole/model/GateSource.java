package com.wormhole_xtreme.wormhole.model;

import java.util.List;

import org.bukkit.block.Block;

import com.wormhole_xtreme.wormhole.model.window.Capture;
import com.wormhole_xtreme.wormhole.model.window.Captures;
import com.wormhole_xtreme.wormhole.model.window.Place;
import com.wormhole_xtreme.wormhole.model.window.WindowShape;
import com.wormhole_xtreme.wormhole.model.window.WindowShape.Spot;
import com.wormhole_xtreme.wormhole.model.window.WindowSource;
import com.wormhole_xtreme.wormhole.model.window.Windows;

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
     * A gate's capture of its first step alone, older than this, is retaken as the gate is dialled or
     * opens, the old one drawn until the new is ready.
     */
    static final long GATE_CAPTURE_SECONDS = 60L;

    /**
     * The same for a capture that holds the fill (#516): ten minutes, as for somebody standing at the gate.
     *
     * <p>A minute was priced for a capture of seconds. A fill is the whole cut-to-fit loop, over open
     * sky a minute and a half of a core and two passes over some 440 chunks, so a gate dialled every
     * two minutes kept a core sifting for as long as it was used. The old capture is drawn meanwhile,
     * so a remote gate still shows its view at once.
     */
    static final long GATE_FILL_CAPTURE_SECONDS = 600L;

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
     *            {@code gate-view-depth}; drawn to that depth, until {@link #drawnTo} says otherwise
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
    private GateSource drawnTo(final int drawn)
    {
        return new GateSource(name, anchor, shape, open, destination, target, step, drawn);
    }

    @Override
    public boolean walkThrough()
    {
        return true;
    }

    /**
     * Offers an open gate's opening to the sweep in progress, as a window onto where it goes (#516).
     *
     * <p>Drawn as a mirror's is, but walked through: never barred, never punched through, and its
     * opening left to the gate's own horizon. Drawn from the capture kept for it, which survives a
     * restart, and asked for again when it is missing, shallower than the depth, or old as the gate opens.
     *
     * @param gate
     *            the gate, as the drawing sees it
     * @param opened
     *            true on the first sweep since the gate or its iris opened
     * @return true if it is a window now, false while its first capture is being taken
     */
    public static boolean offer(final GateSource gate, final boolean opened)
    {
        final Capture capture = gate.capture(opened);
        if (capture == null)
        {
            return false;
        }
        Windows.offer(gate.drawnTo(gate.drawDepth(capture)), capture);
        return true;
    }

    /**
     * Makes sure a gate's capture is on its way as the gate is dialled, before its kawoosh.
     *
     * <p>Waiting for the first sweep after the kawoosh cost that long again before anything showed,
     * and a capture of somewhere nobody had loaded starts with reading it off the disk.
     *
     * @param gate
     *            the gate, as the drawing sees it
     */
    public static void prepare(final GateSource gate)
    {
        gate.capture(true);
    }

    /**
     * The capture this gate is drawn from, asking for the next step of it: the first, out to
     * {@code gate-view-depth}, if it has none or it is shallower than that; a retake at the depth it
     * is drawn to, if the gate has just opened and it is old; otherwise the fill out to
     * {@code gate-view-full-depth}, behind the first, if it does not reach that yet.
     *
     * <p>In steps so a remote gate shows something at once: the first is some fifteen chunks, the
     * fill several times that, most of them read off the disk. A retake keeps the depth drawn, the
     * old capture shown until it lands, so an opening never shrinks the view.
     *
     * @return the capture held now, which is drawn until a fresh one arrives; null for none yet
     */
    private Capture capture(final boolean opened)
    {
        final Capture capture = Captures.get(captureKey());
        final int full = fullDepth();
        final int ask;
        if ((capture == null) || !Captures.reaches(capture, destination, step))
        {
            ask = step;
        }
        else if (opened && (capture.secondsOld() > retakeAfter(capture)))
        {
            // Retaken at the depth it is drawn to, so the view does not shrink to the first step for
            // as long as the fill takes, and pull a fogged viewer's chunks in and out with it.
            ask = drawDepth(capture);
        }
        else
        {
            ask = Captures.reaches(capture, destination, full) ? 0 : full;
        }
        if (ask > 0)
        {
            Captures.requestGate(captureKey(), target, destination, Captures.GATE_OPENING, Captures.GATE_OPENING, ask);
        }
        return capture;
    }

    /**
     * How old this gate's capture may be before an opening retakes it: a minute for one of the first
     * step alone, ten for one holding the fill.
     *
     * @return seconds
     */
    long retakeAfter(final Capture capture)
    {
        // The box, not the depth drawn: a fill cut to fit cost as much as one that was not.
        final int full = fullDepth();
        return ((full > step) && Captures.reaches(capture, destination, full)) ? GATE_FILL_CAPTURE_SECONDS
            : GATE_CAPTURE_SECONDS;
    }

    /**
     * How far this gate's view is filled in behind its first step.
     *
     * @return {@link Captures#gateFillDepth}
     */
    public int fullDepth()
    {
        return Captures.gateFillDepth(destination, step);
    }

    /**
     * How deep this gate is drawn from a capture: the full depth once the fill is in, and the first
     * step's until then; never past where a cut to fit left it.
     */
    private int drawDepth(final Capture capture)
    {
        final int full = fullDepth();
        return Captures.reaches(capture, destination, full) ? Captures.drawableReach(capture, full) : step;
    }
}
