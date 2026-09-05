package com.wormhole_xtreme.wormhole.model;

import java.util.ArrayList;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
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
        final int waveCount = wooshWaveCount(gate);

        if (waveCount <= 0)
        {
            // Nothing to animate: a shape with no authored waves and no WOOSH_DEPTH to
            // derive any from. Settle straight into the open portal rather than running an
            // empty retraction, which is what the old 2D path did here by falling through
            // its own "coming back" branch with nothing ever drawn.
            gate.setGateAnimationStep3D(0);
            gate.setGateAnimationRemoving(false);
            if (gate.isGateLightsActive())
            {
                gate.fillGateInterior(wooshMaterial);
            }
            return;
        }

        // Only zero at the very start of an opening, so this fires once per wormhole rather
        // than once per frame. Here rather than where the woosh is scheduled, because there
        // are two paths into that and only one into this.
        if (!gate.isGateAnimationRemoving() && (gate.getGateAnimationStep3D() == 0))
        {
            GateSounds.kawoosh(gate);
        }

        final int step = gate.getGateAnimationStep3D();
        final ArrayList<Location> wave = wooshWave(gate, step);

        if (!gate.isGateAnimationRemoving())
        {
            if (wave != null)
            {
                // Drawn to nearby clients, not written. Nothing to remember an original
                // for, and nothing left in the world if the server stops mid-woosh.
                StargateBlockSetup.drawBlocks(gate, wave, wooshMaterial);
                for (final Location l : wave)
                {
                    gate.getGateAnimatedBlocks().add(
                        gate.getGateWorld().getBlockAt(l.getBlockX(), l.getBlockY(), l.getBlockZ()));
                }
                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, gate.getGateName() + " Woosh Adding: " + step + " Woosh Block Size: " + wave.size());
            }

            if (waveCount == (step + 1))
            {
                gate.setGateAnimationRemoving(true);
            }
            else
            {
                gate.setGateAnimationStep3D(step + 1);
            }
            WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), new StargateUpdateRunnable(gate, ActionToTake.ANIMATE_WOOSH), gate.getEffectiveWooshTicks());
        }
        else
        {
            // remove in reverse order — only clear blocks that are not portal blocks
            if (wave != null)
            {
                // Put back by showing what is really there, which needs no original and
                // cannot get one wrong.
                StargateBlockSetup.undrawBlocks(gate, wave);
                for (final Location l : wave)
                {
                    gate.getGateAnimatedBlocks().remove(
                        gate.getGateWorld().getBlockAt(l.getBlockX(), l.getBlockY(), l.getBlockZ()));
                }
                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, gate.getGateName() + " Woosh Removing: " + step + " Woosh Block Size: " + wave.size());
            }

            if (step == 0)
            {
                gate.setGateAnimationRemoving(false);
                // Checked against 0, not 1: the wave just undrawn above this tick is the one
                // at index step3D, so ending the retraction as soon as step3D reaches 1 --
                // before this tick's own undraw of index 0 has even run -- skipped undrawing
                // wave #1 (the shallowest layer, right behind the portal) every single time,
                // on every completed opening, not just an interrupted one. It stayed lit as
                // woosh material for as long as the gate stayed open: reported as "the event
                // horizon has an extra layer... in the gate."
                gate.setGateAnimationStep3D(0);
                if (gate.isGateLightsActive())
                {
                    gate.fillGateInterior(wooshMaterial);
                }
            }
            else
            {
                gate.setGateAnimationStep3D(step - 1);
                WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), new StargateUpdateRunnable(gate, ActionToTake.ANIMATE_WOOSH), gate.getEffectiveWooshTicks());
            }
        }
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
    static ArrayList<Location> wooshWave(final Stargate gate, final int index)
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
        final ArrayList<Location> wave = new ArrayList<Location>();
        for (final Location portal : gate.getGatePortalBlocks())
        {
            // Built as a plain Location rather than looked up through
            // world.getBlockAt(...).getLocation(): both drawBlocks and undrawBlocks read
            // only the block coordinates off these and resolve them against the gate's own
            // world themselves, so the round trip through a live World bought nothing and
            // put a server behind a calculation that is really just arithmetic.
            wave.add(new Location(gate.getGateWorld(),
                portal.getBlockX() + (facing.getModX() * out),
                portal.getBlockY() + (facing.getModY() * out),
                portal.getBlockZ() + (facing.getModZ() * out)));
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
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Lighting up Order: " + gate.getGateLightingCurrentIteration());
            if (gate.getGateLightingCurrentIteration() == 0)
            {
                gate.setGateLightsActive(true);
                GateSounds.activated(gate);
            }
            else if (!gate.isGateLightsActive())
            {
                lightStargate(gate, false);
                gate.setGateLightingCurrentIteration(0);
                return;
            }
            gate.setGateLightingCurrentIteration(gate.getGateLightingCurrentIteration() + 1);

            if (gate.getGateLightBlocks() != null)
            {
                if ((gate.getGateLightBlocks().size() > 0) && (gate.getGateLightBlocks().get(gate.getGateLightingCurrentIteration()) != null))
                {
                    // Drawn, not placed. A real lit chevron is an ordinary breakable
                    // glowstone block for the seconds it stands there, and a server that
                    // stops mid-dial used to leave the lit ones welded into the frame.
                    //
                    // Through drawLights rather than drawBlocks because a chevron the player
                    // built out of the chevron material lights as that same block switched on,
                    // and which positions those are is a per-block question.
                    StargateBlockSetup.drawLights(gate,
                        gate.getGateLightBlocks().get(gate.getGateLightingCurrentIteration()));
                    // Off the same counter that drives the lights, so the sound cannot drift
                    // out of step with what it is describing.
                    GateSounds.chevron(gate, gate.getGateLightingCurrentIteration(),
                        gate.getGateLightBlocks().size() - 1);
                }

                if (gate.getGateLightingCurrentIteration() >= gate.getGateLightBlocks().size() - 1)
                {
                    gate.setGateLightingCurrentIteration(0);
                    if (gate.isGateActive())
                    {
                        WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), new StargateUpdateRunnable(gate, ActionToTake.ANIMATE_WOOSH));
                    }
                }
                else
                {
                    WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), new StargateUpdateRunnable(gate, ActionToTake.LIGHTUP), gate.getEffectiveLightTicks());
                }
            }
        }
        else
        {
            gate.setGateLightsActive(false);
            if (gate.getGateLightBlocks() != null)
            {
                for (int i = 0; i < gate.getGateLightBlocks().size(); i++)
                {
                    if (gate.getGateLightBlocks().get(i) != null)
                    {
                        // Shown as whatever is really there rather than as the structure
                        // material: the frame was never changed, so this is putting a
                        // drawing away rather than rebuilding anything.
                        StargateBlockSetup.undrawBlocks(gate, gate.getGateLightBlocks().get(i));
                    }
                }
            }

            // The woosh can be mid-step when a gate closes: its own step-by-step
            // retraction is the only thing that ever undraws it, and closing does not wait
            // for that to finish first. A deep gate's woosh (Massive's thirteen steps, for
            // instance) takes long enough that an early manual close, or a partner gate
            // shutting down mid-opening, has a real window to land inside it -- leaving
            // whatever was drawn so far (the woosh material, the wave nearest the portal
            // on the very first step) showing to anyone nearby until their client
            // happens to get a fresh copy of that chunk some other way. Same principle as
            // the chevron undraw just above, extended to the animation that never had it:
            // closing reverts whatever was left showing, not just whatever it expected to
            // find. animateOpening's own gate.isGateActive() guard is what stops an
            // already-scheduled continuation from reading this reset-to-zero counter as
            // "start a fresh opening" once it fires after this.
            if (!gate.getGateAnimatedBlocks().isEmpty())
            {
                final ArrayList<Location> stillShowing = new ArrayList<Location>();
                for (final Block b : gate.getGateAnimatedBlocks())
                {
                    stillShowing.add(b.getLocation());
                }
                StargateBlockSetup.undrawBlocks(gate, stillShowing);
                gate.getGateAnimatedBlocks().clear();
            }
            gate.setGateAnimationStep3D(0);
            gate.setGateAnimationRemoving(false);
        }
    }
}
