package com.wormhole_xtreme.wormhole.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.logic.DialSpin;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint;
import com.wormhole_xtreme.wormhole.logic.GateRederivation;
import com.wormhole_xtreme.wormhole.logic.StargateUpdateRunnable;
import com.wormhole_xtreme.wormhole.logic.StargateUpdateRunnable.ActionToTake;

/**
 * Handles all stargate animation: the chevron lighting sequence and the portal
 * "woosh" animation that plays when a wormhole opens.
 *
 * <p>All methods are static and operate on a {@link Stargate} instance so they
 * can be unit-tested independently of the Bukkit server lifecycle.
 */
class StargateAnimator
{
    private StargateAnimator() {}

    /**
     * Advances the woosh (portal opening) animation by one step.
     * Called repeatedly via the scheduler until the animation completes,
     * at which point the gate interior is filled with portal material.
     *
     * <p>Each step schedules its own continuation with a raw {@code scheduleSyncDelayedTask}
     * call, with no task id kept anywhere to cancel if the gate closes before that delay
     * elapses -- unlike the activation and shutdown timers, which do track theirs. A gate
     * can close mid-woosh ({@link Stargate#isGateActive()} already handles the visible mess
     * that leaves via {@link #lightStargate}'s own cleanup, called from the same shutdown),
     * but the already-scheduled continuation still fires afterward regardless. Without this
     * guard it would find the counters {@link #lightStargate} just reset to zero and read
     * that as "start a fresh opening" -- replaying the kawoosh and redrawing the first woosh
     * step on a gate that has already closed, rather than harmlessly doing nothing.
     *
     * @param gate the gate being animated
     */
    static void animateOpening(final Stargate gate)
    {
        if (!gate.isGateActive())
        {
            return;
        }
        final Material wooshMaterial = gate.getEffectivePortalMaterial();
        // A shape with no authored waves and no WOOSH_DEPTH to derive any from has none, and
        // settles straight into the open portal.
        final int waveCount = Math.max(0, wooshWaveCount(gate));
        // The gate keeps its place as a step and a direction; the sequence a build preview plays
        // too decides what that stage draws, whether the iris hides it, and what comes next.
        // With no waves a leftover counter means nothing, and would read as a negative stage.
        final int stage = (waveCount == 0) ? 0
            : WooshSequence.stageOf(gate.getGateAnimationStep3D(), gate.isGateAnimationRemoving(), waveCount);
        final int next = WooshSequence.play(stage, waveCount, new GateCanvas(gate, wooshMaterial));
        if (next < 0)
        {
            return;
        }
        final WooshSequence.Step step = WooshSequence.at(next, waveCount);
        gate.setGateAnimationStep3D(step.index());
        gate.setGateAnimationRemoving(step.move() == WooshSequence.Move.BACK);
        scheduleNextWooshTick(gate);
    }

    /** A real gate's side of the woosh: drawn to nearby clients, the sound played at the gate. */
    private record GateCanvas(Stargate gate, Material wooshMaterial) implements WooshSequence.Canvas
    {
        @Override
        public boolean irisShut()
        {
            return gate.isGateIrisActive();
        }

        @Override
        public void kawoosh()
        {
            GateSounds.kawoosh(gate);
        }

        @Override
        public void draw(final int index)
        {
            showWave(gate, index, true, wooshMaterial);
        }

        @Override
        public void undraw(final int index)
        {
            showWave(gate, index, false, wooshMaterial);
        }

        @Override
        public void undrawAll()
        {
            undrawLeftoverWoosh(gate);
        }

        @Override
        public void settle()
        {
            settleIntoOpenPortal(gate, wooshMaterial);
        }

        @Override
        public void settleBehindIris()
        {
            // Not filled: the horizon drawn over the portal cells would stand where the iris is.
            // Opening the iris later draws the portal through setIrisState.
            gate.setGateAnimationStep3D(0);
            gate.setGateAnimationRemoving(false);
        }
    }

    /**
     * Draws one woosh step out, or takes it back.
     *
     * @param gate
     *            the gate
     * @param now
     *            the stage being played
     * @param wave
     *            its blocks
     * @param wooshMaterial
     *            what the woosh is drawn as
     */
    private static void showWave(final Stargate gate, final int index, final boolean out,
        final Material wooshMaterial)
    {
        final List<Location> wave = wooshWave(gate, index);
        if (wave == null)
        {
            return;
        }
        if (out)
        {
            // Drawn to nearby clients, not written. Nothing to remember an original for, and
            // nothing left in the world if the server stops mid-woosh.
            StargateBlockSetup.drawBlocks(gate, wave, wooshMaterial);
        }
        else
        {
            // Put back by showing what is really there, which needs no original and cannot
            // get one wrong.
            StargateBlockSetup.undrawBlocks(gate, wave);
        }
        for (final Location l : wave)
        {
            final Block block = gate.getGateWorld().getBlockAt(l.getBlockX(), l.getBlockY(), l.getBlockZ());
            if (out)
            {
                gate.getGateAnimatedBlocks().add(block);
            }
            else
            {
                gate.getGateAnimatedBlocks().remove(block);
            }
        }
        WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, gate.getGateName() + (out ? " Woosh Adding: " : " Woosh Removing: ")
            + index + " Woosh Block Size: " + wave.size());
    }

    /**
     * Puts the animation counters back to the start and shows the open portal.
     *
     * <p>The end of a retraction and a gate with no waves to animate both arrive here: in
     * each case the woosh is over, or never happened, and what should be showing is the
     * portal itself.
     *
     * @param gate
     *            the gate
     * @param wooshMaterial
     *            what to fill the portal with
     */
    private static void settleIntoOpenPortal(final Stargate gate, final Material wooshMaterial)
    {
        gate.setGateAnimationStep3D(0);
        gate.setGateAnimationRemoving(false);
        if (gate.isGateLightsActive())
        {
            gate.fillGateInterior(wooshMaterial);
        }
    }

    /**
     * Books the next frame of the woosh.
     *
     * @param gate
     *            the gate
     */
    private static void scheduleNextWooshTick(final Stargate gate)
    {
        WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(),
            new StargateUpdateRunnable(gate, ActionToTake.ANIMATE_WOOSH), gate.getEffectiveWooshTicks());
    }

    /**
     * How many waves this gate's woosh has.
     *
     * <p>A shape that authors its own waves with {@code :W#N} markers says so directly. One
     * that does not falls back to {@code WOOSH_DEPTH} (or a per-gate override from
     * {@code /wormhole wooshdepth}), and its waves are derived on demand by
     * {@link #wooshWave} instead of read from the shape.
     *
     * @param gate the gate
     * @return the number of waves, 0 if this gate has no woosh at all
     */
    static int wooshWaveCount(final Stargate gate)
    {
        if ((gate.getGateWooshBlocks() != null) && !gate.getGateWooshBlocks().isEmpty())
        {
            return gate.getGateWooshBlocks().size();
        }
        return gate.getEffectiveWooshDepth();
    }

    /**
     * One wave of this gate's woosh, whether the shape authored it or not.
     *
     * <p>Authored waves come straight out of the shape. For a shape without them, wave
     * {@code index} is the portal face pushed {@code index + 1} blocks along the gate's
     * facing -- the same outward extrusion the old, separate 2D animation path performed
     * step by step, expressed as a function of the step number rather than as a second
     * state machine that had to be kept in agreement with this one. Deriving it here rather
     * than storing it at detection time keeps it out of the save file, and means a change
     * to {@code /wormhole wooshdepth} takes effect on the very next opening instead of
     * needing the gate re-detected.
     *
     * @param gate the gate
     * @param index which wave, 0 being the one nearest the portal
     * @return the wave's locations, or null if the shape authored this index as empty
     */
    // S1168 asks for an empty list. Null means "the shape authored this index as empty",
    // which an empty list cannot say, and both callers branch on it.
    @SuppressWarnings("java:S1168")
    static List<Location> wooshWave(final Stargate gate, final int index)
    {
        if ((gate.getGateWooshBlocks() != null) && !gate.getGateWooshBlocks().isEmpty())
        {
            return (index >= 0) && (index < gate.getGateWooshBlocks().size())
                ? gate.getGateWooshBlocks().get(index)
                : null;
        }

        final BlockFace facing = gate.getGateFacing();
        if ((facing == null) || (index < 0))
        {
            return null;
        }
        final int out = index + 1;
        final ArrayList<Location> wave = new ArrayList<>();
        for (final Location portal : gate.getGatePortalBlocks())
        {
            // Built as a plain Location rather than looked up through
            // world.getBlockAt(...).getLocation(): both drawBlocks and undrawBlocks read
            // only the block coordinates off these and resolve them against the gate's own
            // world themselves, so the round trip through a live World bought nothing and
            // put a server behind a calculation that is really just arithmetic.
            wave.add(new Location(gate.getGateWorld(),
                (double) portal.getBlockX() + (facing.getModX() * out),
                (double) portal.getBlockY() + (facing.getModY() * out),
                (double) portal.getBlockZ() + (facing.getModZ() * out)));
        }
        return wave;
    }

    /**
     * Lights or darkens the gate's structural light blocks and triggers the
     * woosh animation when the lighting sequence completes.
     *
     * @param gate the gate
     * @param on   {@code true} to light up; {@code false} to darken
     */
    static void lightStargate(final Stargate gate, final boolean on)
    {
        if (on)
        {
            lightNextChevron(gate);
        }
        else
        {
            darkenStargate(gate);
        }
    }

    /**
     * Draws the next wave of chevron lights and books the tick after it.
     *
     * @param gate
     *            the gate
     */
    private static void lightNextChevron(final Stargate gate)
    {
        WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "Lighting up Order: " + gate.getGateLightingCurrentIteration());
        if ((gate.getGateLightingCurrentIteration() == 0) && !gate.isGateLightsActive())
        {
            // Not when relighting: the gate already made its activation sound when it lit.
            gate.setGateLightsActive(true);
            GateSounds.activated(gate);
        }
        else if (!gate.isGateLightsActive())
        {
            // The gate went dark part-way through the sequence, so start it over rather
            // than carrying on from wherever the counter had reached.
            darkenStargate(gate);
            gate.setGateLightingCurrentIteration(0);
            return;
        }

        final List<List<Location>> waves = gate.getGateLightBlocks();
        if (waves == null)
        {
            return;
        }
        if (turnRing(gate, waves))
        {
            return;
        }
        final int step = gate.getGateLightingCurrentIteration() + 1;
        gate.setGateLightingCurrentIteration(step);
        drawLightWave(gate, waves, step);
        scheduleNextStep(gate, waves, step);
    }

    /**
     * Draws one wave of chevron lights, if there is one at this step.
     *
     * <p>Waves are numbered from one -- the shape parser pads the list up to the highest
     * order it sees, so index 0 is padding and never drawn, which is why the counter is
     * advanced before the wave is read. A shape whose lights are numbered from zero leaves
     * the counter one past the end, so the bound is checked rather than assumed.
     *
     * @param gate
     *            the gate
     * @param waves
     *            its light waves
     * @param step
     *            which wave to draw
     */
    private static void drawLightWave(final Stargate gate, final List<List<Location>> waves, final int step)
    {
        if ((step > lastWave(gate, waves)) || (waves.get(step) == null))
        {
            return;
        }
        // Drawn, not placed. A real lit chevron is an ordinary breakable glowstone block for
        // the seconds it stands there, and a server that stops mid-dial used to leave the lit
        // ones welded into the frame.
        //
        // Through drawLights rather than drawBlocks because a chevron the player built out of
        // the chevron material lights as that same block switched on, and which positions
        // those are is a per-block question.
        StargateBlockSetup.drawLights(gate, waves.get(step));
        // Off the same counter that drives the lights, so the sound cannot drift out of step
        // with what it is describing.
        GateSounds.chevron(gate, step, lastWave(gate, waves));
        if (step == lastWave(gate, waves))
        {
            GateSounds.locked(gate);
        }
    }

    /**
     * Books the next lighting tick, or hands over to the woosh once the last wave is lit.
     *
     * @param gate
     *            the gate
     * @param waves
     *            its light waves
     * @param step
     *            the wave just drawn
     */
    private static void scheduleNextStep(final Stargate gate, final List<List<Location>> waves, final int step)
    {
        if (step >= lastWave(gate, waves))
        {
            gate.setGateLightingCurrentIteration(0);
            if (gate.isGateActive())
            {
                WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(),
                    new StargateUpdateRunnable(gate, ActionToTake.ANIMATE_WOOSH), Stargate.LAST_CHEVRON_PAUSE_TICKS);
            }
        }
        else
        {
            // With the ring turning, the turn itself is the wait before the next chevron.
            final long between = turns(gate) ? 1L : gate.getEffectiveLightTicks();
            WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(),
                new StargateUpdateRunnable(gate, ActionToTake.LIGHTUP), between);
        }
    }

    /** A gate's ring, laid from its own frame, and the shape it was laid for. */
    private record LaidRing(StargateShape shape, DialSpin spin)
    {
    }

    /** Where the ring's light is on a gate dialling now, and how far through its turn. */
    private static final class Turning
    {
        private int tick;
        private List<Location> cells = List.of();
    }

    private static final Map<Stargate, LaidRing> RINGS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Stargate, Turning> TURNING = Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * The ring a gate's light travels round while it dials (#357), or null when it does not turn:
     * turned off, or no 3D shape to lay. Laid where the gate's own recorded frame is, as regen lays
     * it, and kept while the gate keeps that shape.
     */
    static DialSpin spinOf(final Stargate gate)
    {
        if (!ConfigManager.isGateDialSpin() || !(gate.getGateShape() instanceof Stargate3DShape shape))
        {
            return null;
        }
        final LaidRing laid = RINGS.get(gate);
        if ((laid != null) && (laid.shape() == shape))
        {
            return laid.spin();
        }
        final GateRederivation.Layout layout = GateRederivation.layoutFor(gate, shape);
        DialSpin spin = (layout == null) ? null
            : DialSpin.of(GateBlueprint.of(shape, layout.grid()), layout.grid());
        // A shape the gate no longer fits, such as 1.6's Large under 1.7's, would turn a ring beside it.
        if ((spin != null) && !GateRederivation.liesOnGate(gate, spin.ring()))
        {
            spin = null;
        }
        RINGS.put(gate, new LaidRing(shape, spin));
        return spin;
    }

    /**
     * Whether this gate's ring turns while it dials: only the gate dialling, which names a target.
     * The gate being dialled lights its chevrons in order without turning, as on the show.
     */
    static boolean turns(final Stargate gate)
    {
        return (gate.getGateTarget() != null) && (spinOf(gate) != null);
    }

    /**
     * Moves the ring's light one tick towards the top chevron, for the glyph about to lock: half
     * the ring, alternating direction each glyph, over the chevron's own interval.
     *
     * @return true while the light is still travelling, false once it has arrived and is gone
     */
    private static boolean turnRing(final Stargate gate, final List<List<Location>> waves)
    {
        final int glyph = gate.getGateLightingCurrentIteration() + 1;
        final DialSpin spin = turns(gate) ? spinOf(gate) : null;
        if ((spin == null) || (glyph > lastWave(gate, waves)) || (gate.getGateWorld() == null))
        {
            return false;
        }
        final Turning turning = TURNING.computeIfAbsent(gate, g -> new Turning());
        final int ticks = Math.max(1, gate.getEffectiveLightTicks());
        final List<Location> now = new ArrayList<>();
        if (turning.tick < ticks)
        {
            for (final GateBlueprint.Cell cell : spin.lit(ConfigManager.getGateDialSpinPattern(), glyph, turning.tick, ticks))
            {
                now.add(new Location(gate.getGateWorld(), cell.x(), cell.y(), cell.z()));
            }
        }
        takeBackLight(gate, turning.cells, now, lockedCells(waves, glyph - 1));
        if (turning.tick >= ticks)
        {
            TURNING.remove(gate);
            return false;
        }
        StargateBlockSetup.drawLights(gate, now);
        turning.cells = now;
        turning.tick++;
        WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(),
            new StargateUpdateRunnable(gate, ActionToTake.LIGHTUP), 1L);
        return true;
    }

    /** Puts back the cells the light has left, except chevrons already locked, which stay lit. */
    private static void takeBackLight(final Stargate gate, final List<Location> was, final List<Location> now,
        final Set<String> locked)
    {
        final Set<String> keep = new HashSet<>(locked);
        for (final Location l : now)
        {
            keep.add(key(l));
        }
        final List<Location> gone = new ArrayList<>();
        for (final Location l : was)
        {
            if (!keep.contains(key(l)))
            {
                gone.add(l);
            }
        }
        if (!gone.isEmpty())
        {
            StargateBlockSetup.undrawBlocks(gate, gone);
        }
    }

    /** The blocks of the chevrons locked so far. */
    private static Set<String> lockedCells(final List<List<Location>> waves, final int locked)
    {
        final Set<String> keys = new HashSet<>();
        for (int step = 1; (step <= locked) && (step < waves.size()); step++)
        {
            if (waves.get(step) != null)
            {
                for (final Location l : waves.get(step))
                {
                    keys.add(key(l));
                }
            }
        }
        return keys;
    }

    private static String key(final Location l)
    {
        return l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }

    /**
     * The last chevron this gate lights: the seventh, or the eighth when it is linked to a gate in
     * another world. The eighth locks after the top one, as in <i>The Fifth Race</i> (#351).
     */
    static int lastWave(final Stargate gate, final List<List<Location>> waves)
    {
        final int last = linksAnotherWorld(gate) ? Stargate.OTHER_WORLD_CHEVRON : Stargate.LOCAL_CHEVRONS;
        return Math.min(waves.size() - 1, last);
    }

    /**
     * The last chevron a client should see lit: all of them while the button has the gate waiting
     * for {@code /dial}, otherwise the ones its dial uses.
     */
    static int lastShownWave(final Stargate gate, final List<List<Location>> waves)
    {
        return (gate.isGateLightsActive() && !gate.isGateActive()) ? (waves.size() - 1) : lastWave(gate, waves);
    }

    /**
     * Whether the gate is linked to one in another world, whichever end dialled.
     *
     * <p>The dialling gate names its target; the far gate is found as the active gate naming it.
     */
    static boolean linksAnotherWorld(final Stargate gate)
    {
        Stargate other = gate.getGateTarget();
        if (other == null)
        {
            for (final Stargate s : StargateManager.getAllGatesUnsorted())
            {
                if ((s != null) && (s != gate) && (s.getGateTarget() == gate) && s.isGateActive())
                {
                    other = s;
                    break;
                }
            }
        }
        if ((other == null) || (gate.getGateWorld() == null) || (other.getGateWorld() == null))
        {
            return false;
        }
        return !gate.getGateWorld().getName().equals(other.getGateWorld().getName());
    }

    /**
     * Lights every chevron at once, the eighth included, for a gate activated by its button and
     * waiting for {@code /dial}. The dial then relights only the ones it needs.
     *
     * @param gate
     *            the gate
     */
    static void lightAll(final Stargate gate)
    {
        gate.setGateLightsActive(true);
        gate.setGateLightingCurrentIteration(0);
        GateSounds.activated(gate);
        final List<List<Location>> waves = gate.getGateLightBlocks();
        if (waves == null)
        {
            return;
        }
        for (int step = 1; step < waves.size(); step++)
        {
            if (waves.get(step) != null)
            {
                StargateBlockSetup.drawLights(gate, waves.get(step));
            }
        }
    }

    /**
     * Opens a gate at once, as a sign dial does: the chevrons its link needs light together with
     * the lock-in sound, and the wormhole forms on the next tick. No chevron runs one at a time,
     * so a gate already open plays no dialling sounds.
     *
     * @param gate
     *            the gate, active and linked
     */
    static void openAtOnce(final Stargate gate)
    {
        gate.setGateLightsActive(true);
        gate.setGateLightingCurrentIteration(0);
        final List<List<Location>> waves = gate.getGateLightBlocks();
        if (waves != null)
        {
            for (int step = 1; step <= lastWave(gate, waves); step++)
            {
                if (waves.get(step) != null)
                {
                    StargateBlockSetup.drawLights(gate, waves.get(step));
                }
            }
        }
        GateSounds.locked(gate);
        WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(),
            new StargateUpdateRunnable(gate, ActionToTake.ANIMATE_WOOSH), 1L);
    }

    /**
     * Darkens the chevrons, then relights them one at a time with their sounds; the woosh
     * follows the last as usual. Used once {@code /dial} names a destination.
     *
     * @param gate
     *            the gate, already lit
     */
    static void relightInOrder(final Stargate gate)
    {
        final List<List<Location>> waves = gate.getGateLightBlocks();
        if (waves != null)
        {
            for (final List<Location> wave : waves)
            {
                if (wave != null)
                {
                    StargateBlockSetup.undrawBlocks(gate, wave);
                }
            }
        }
        gate.setGateLightingCurrentIteration(0);
        WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(),
            new StargateUpdateRunnable(gate, ActionToTake.LIGHTUP), gate.getEffectiveLightTicks());
    }

    /**
     * Puts every chevron light away and resets what the woosh left behind.
     *
     * @param gate
     *            the gate
     */
    private static void darkenStargate(final Stargate gate)
    {
        gate.setGateLightsActive(false);
        // The ring's light may be part way round; put back whatever it was showing.
        final Turning turning = TURNING.remove(gate);
        if ((turning != null) && !turning.cells.isEmpty())
        {
            StargateBlockSetup.undrawBlocks(gate, turning.cells);
        }
        if (gate.getGateLightBlocks() != null)
        {
            for (int i = 0; i < gate.getGateLightBlocks().size(); i++)
            {
                if (gate.getGateLightBlocks().get(i) != null)
                {
                    // Shown as whatever is really there rather than as the structure
                    // material: the frame was never changed, so this is putting a drawing
                    // away rather than rebuilding anything.
                    StargateBlockSetup.undrawBlocks(gate, gate.getGateLightBlocks().get(i));
                }
            }
        }
        undrawLeftoverWoosh(gate);
        gate.setGateAnimationStep3D(0);
        gate.setGateAnimationRemoving(false);
    }

    /**
     * Puts away whatever the woosh was still showing when the gate closed.
     *
     * <p>The woosh can be mid-step when a gate closes: its own step-by-step retraction is the
     * only thing that ever undraws it, and closing does not wait for that to finish. A deep
     * gate's woosh (Massive's thirteen steps, for instance) takes long enough that an early
     * manual close, or a partner gate shutting down mid-opening, has a real window to land
     * inside it -- leaving whatever was drawn so far showing to anyone nearby until their
     * client happens to get a fresh copy of that chunk some other way.
     *
     * <p>Same principle as the chevron undraw: closing reverts whatever was left showing, not
     * just whatever it expected to find. animateOpening's own isGateActive guard is what stops
     * an already-scheduled continuation from reading this reset counter as "start a fresh
     * opening" once it fires after this.
     *
     * @param gate
     *            the gate
     */
    private static void undrawLeftoverWoosh(final Stargate gate)
    {
        if (gate.getGateAnimatedBlocks().isEmpty())
        {
            return;
        }
        final List<Location> stillShowing = new ArrayList<>();
        for (final Block b : gate.getGateAnimatedBlocks())
        {
            stillShowing.add(b.getLocation());
        }
        StargateBlockSetup.undrawBlocks(gate, stillShowing);
        gate.getGateAnimatedBlocks().clear();
    }
}
