package com.wormhole_xtreme.wormhole.model.preview;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.logic.DialSpinPattern;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Cell;
import com.wormhole_xtreme.wormhole.model.GateSounds;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.WooshSequence;

/**
 * How a build preview dials: the chevrons locking a wave at a time, the ring's light turning to
 * each, and the woosh played on a preview's canvas (#430, #734).
 */
final class PreviewDial
{
    /** Static helpers only. */
    private PreviewDial()
    {
    }

    /**
     * Plays the next step of a dial: the next chevron wave, then the kawoosh in the order
     * {@link WooshSequence} gives a real gate, then the open wormhole.
     *
     * @param owner
     *            whose preview
     * @param preview
     *            the preview dialling
     */
    static void step(final Player owner, final GatePreview preview)
    {
        preview.dialling(null);
        if (preview.litWaves() < preview.lastWave())
        {
            if (!turnRing(owner, preview))
            {
                lockNextChevron(owner, preview);
            }
            return;
        }
        // The same sequence a real gate plays, iris and all; only the drawing is the preview's.
        final int stage = preview.wooshStage();
        final int next = WooshSequence.play(stage, preview.lastWoosh(), preview, new PreviewCanvas(owner, preview));
        preview.wooshStage((next < 0) ? (stage + 1) : next);
        if (next >= 0)
        {
            next(owner, preview, preview.shape().getShapeWooshTicks());
        }
    }

    /** A preview's side of the woosh: fake blocks for whoever is watching, the sound at the preview. */
    private record PreviewCanvas(Player owner, GatePreview preview) implements WooshSequence.Canvas
    {
        @Override
        public void kawoosh()
        {
            GatePreviews.sound(owner, preview, ConfigManager.getGateSoundKawoosh(), GateSounds.KAWOOSH_PITCH);
        }

        @Override
        public void draw(final int index)
        {
            GatePreviews.send(owner, preview, wooshStep(preview, index));
        }

        @Override
        public void undraw(final int index)
        {
            GatePreviews.takeBack(owner, preview, wooshStep(preview, index));
        }

        @Override
        public void undrawAll()
        {
            GatePreviews.takeBack(owner, preview, preview.woosh());
        }

        @Override
        public void settle()
        {
            preview.open(true);
            GatePreviews.draw(owner, preview);
        }

        @Override
        public void settleBehindIris()
        {
            // Open all the same: the opening is drawn behind the iris, and its displays cover it.
            settle();
        }

        /** The woosh's cells at one step, counted from 0 as {@link WooshSequence} does; the shape's W# from 1. */
        private static List<Cell> wooshStep(final GatePreview preview, final int index)
        {
            return preview.woosh().stream().filter(cell -> cell.wave() == (index + 1)).toList();
        }
    }

    /** Locks the next chevron, with its sound, and books what follows it. */
    private static void lockNextChevron(final Player owner, final GatePreview preview)
    {
        preview.litWaves(preview.litWaves() + 1);
        GatePreviews.sound(owner, preview, ConfigManager.getGateSoundChevron(),
            GateSounds.chevronPitch(preview.litWaves(), preview.lastWave()));
        if (preview.litWaves() == preview.lastWave())
        {
            GatePreviews.sound(owner, preview, ConfigManager.getGateSoundLock(), GateSounds.LOCK_PITCH);
        }
        GatePreviews.restyle(preview);
        // Held after the last chevron as a real gate holds it. With the ring turning, the turn
        // itself is the wait before the next chevron.
        final long between = spins(preview) ? 1L : preview.shape().getShapeLightTicks();
        next(owner, preview, (preview.litWaves() < preview.lastWave()) ? between : Stargate.LAST_CHEVRON_PAUSE_TICKS);
    }

    /** Whether this preview's dial shows the ring turning. */
    static boolean spins(final GatePreview preview)
    {
        return (preview.spin() != null) && (pattern(preview) != DialSpinPattern.NONE);
    }

    /**
     * The ring pattern a preview dials with: that of the group its frame material names, else the
     * server's, as the gate built from it would be detected.
     */
    static DialSpinPattern pattern(final GatePreview preview)
    {
        return ConfigManager.getGateDialSpinPattern(null, GatePreviews.groupByFrame(preview));
    }

    /**
     * Moves the ring's light one tick along its pattern (#357), for the glyph about to lock, over
     * the chevron's own interval and any rest the pattern adds.
     *
     * @return true while the light is still travelling, false once it has arrived and the chevron may lock
     */
    private static boolean turnRing(final Player owner, final GatePreview preview)
    {
        if (!spins(preview))
        {
            // Recoloured into a group that turns none part way round: put back what the turn had lit.
            final Set<Cell> left = preview.spinCells();
            if (!left.isEmpty())
            {
                preview.spinCells(Set.of());
                preview.spinTick(0);
                GatePreviews.restyle(preview, left);
            }
            return false;
        }
        final Set<Cell> was = preview.spinCells();
        final DialSpinPattern pattern = pattern(preview);
        final int glyph = preview.litWaves() + 1;
        final int interval = Math.max(1, preview.shape().getShapeLightTicks());
        final boolean arrived = preview.spinTick() >= preview.spin().frames(pattern, glyph, interval);
        // On arriving, what rests on the ring stays lit through the lock; the next turn takes it back.
        preview.spinCells(arrived ? preview.spin().rest(pattern, glyph, preview.lastWave())
            : preview.spin().frame(pattern, glyph, preview.spinTick(), interval));
        preview.spinTick(arrived ? 0 : (preview.spinTick() + 1));
        final Set<Cell> changed = new HashSet<>(was);
        changed.addAll(preview.spinCells());
        GatePreviews.restyle(preview, changed);
        if (arrived)
        {
            return false;
        }
        next(owner, preview, 1L);
        return true;
    }

    static void next(final Player owner, final GatePreview preview, final long ticks)
    {
        preview.dialling(GatePreviews.later.after(Math.max(1L, ticks), () -> step(owner, preview)));
    }
}
