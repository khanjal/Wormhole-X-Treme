package com.wormhole_xtreme.wormhole.model;

import java.util.ArrayList;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

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
        final int wooshDepth = gate.getEffectiveWooshDepth();

        // Both counters are only zero at the very start of an opening, so this fires once per
        // wormhole rather than once per frame. Here rather than where the woosh is scheduled,
        // because there are two paths into that and only one into this.
        if (!gate.isGateAnimationRemoving() && (gate.getGateAnimationStep2D() == 0)
            && (gate.getGateAnimationStep3D() == 0))
        {
            GateSounds.kawoosh(gate);
        }

        if ((gate.getGateWooshBlocks() != null) && (gate.getGateWooshBlocks().size() > 0))
        {
            final ArrayList<Location> wooshBlockStep = gate.getGateWooshBlocks().get(gate.getGateAnimationStep3D());
            if (!gate.isGateAnimationRemoving())
            {
                if (wooshBlockStep != null)
                {
                    // Drawn to nearby clients, not written. Nothing to remember an original
                    // for, and nothing left in the world if the server stops mid-woosh.
                    StargateBlockSetup.drawBlocks(gate, wooshBlockStep, wooshMaterial);
                    for (final Location l : wooshBlockStep)
                    {
                        gate.getGateAnimatedBlocks().add(
                            gate.getGateWorld().getBlockAt(l.getBlockX(), l.getBlockY(), l.getBlockZ()));
                    }
                    WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, gate.getGateName() + " Woosh Adding: " + gate.getGateAnimationStep3D() + " Woosh Block Size: " + wooshBlockStep.size());
                }

                if (gate.getGateWooshBlocks().size() == gate.getGateAnimationStep3D() + 1)
                {
                    gate.setGateAnimationRemoving(true);
                }
                else
                {
                    gate.setGateAnimationStep3D(gate.getGateAnimationStep3D() + 1);
                }
                WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), new StargateUpdateRunnable(gate, ActionToTake.ANIMATE_WOOSH), gate.getEffectiveWooshTicks());
            }
            else
            {
                // remove in reverse order — only clear blocks that are not portal blocks
                if (wooshBlockStep != null)
                {
                    // Put back by showing what is really there, which needs no original and
                    // cannot get one wrong.
                    StargateBlockSetup.undrawBlocks(gate, wooshBlockStep);
                    for (final Location l : wooshBlockStep)
                    {
                        gate.getGateAnimatedBlocks().remove(
                            gate.getGateWorld().getBlockAt(l.getBlockX(), l.getBlockY(), l.getBlockZ()));
                    }
                    WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, gate.getGateName() + " Woosh Removing: " + gate.getGateAnimationStep3D() + " Woosh Block Size: " + wooshBlockStep.size());
                }

                if (gate.getGateAnimationStep3D() == 1)
                {
                    gate.setGateAnimationRemoving(false);
                    // Mirrors the 2D woosh path's own reset a little further down (step2D
                    // set back to 0 once its own closing finishes) -- without this, the
                    // counter was left at 1, and every opening after a gate's first one
                    // skipped the kawoosh sound (only fires at step3D == 0) and started
                    // one woosh-depth layer late.
                    gate.setGateAnimationStep3D(0);
                    if (gate.isGateLightsActive() && gate.isGateActive())
                    {
                        gate.fillGateInterior(wooshMaterial);
                    }
                }
                else
                {
                    gate.setGateAnimationStep3D(gate.getGateAnimationStep3D() - 1);
                    WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), new StargateUpdateRunnable(gate, ActionToTake.ANIMATE_WOOSH), gate.getEffectiveWooshTicks());
                }
            }
        }
        else
        {
            // 2D gate woosh
            if ((gate.getGateAnimationStep2D() == 0) && (wooshDepth > 0))
            {
                final ArrayList<Location> firstStep = new ArrayList<Location>();
                for (final Location block : gate.getGatePortalBlocks())
                {
                    final Block r = gate.getGateWorld().getBlockAt(block.getBlockX(), block.getBlockY(), block.getBlockZ())
                        .getRelative(gate.getGateFacing());
                    gate.getGateAnimatedBlocks().add(r);
                    firstStep.add(r.getLocation());
                }
                StargateBlockSetup.drawBlocks(gate, firstStep, wooshMaterial);
                gate.setGateAnimationStep2D(gate.getGateAnimationStep2D() + 1);
                WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), new StargateUpdateRunnable(gate, ActionToTake.ANIMATE_WOOSH), 4);
            }
            else if (gate.getGateAnimationStep2D() < wooshDepth)
            {
                final int size = gate.getGateAnimatedBlocks().size();
                final int start = gate.getGatePortalBlocks().size();
                final ArrayList<Location> nextStep = new ArrayList<Location>();
                for (int i = (size - start); i < size; i++)
                {
                    final Block r = gate.getGateAnimatedBlocks().get(i).getRelative(gate.getGateFacing());
                    gate.getGateAnimatedBlocks().add(r);
                    nextStep.add(r.getLocation());
                }
                StargateBlockSetup.drawBlocks(gate, nextStep, wooshMaterial);
                gate.setGateAnimationStep2D(gate.getGateAnimationStep2D() + 1);
                if (gate.getGateAnimationStep2D() == wooshDepth)
                {
                    WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), new StargateUpdateRunnable(gate, ActionToTake.ANIMATE_WOOSH), 8);
                }
                else
                {
                    WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), new StargateUpdateRunnable(gate, ActionToTake.ANIMATE_WOOSH), 4);
                }
            }
            else if (gate.getGateAnimationStep2D() >= wooshDepth)
            {
                final ArrayList<Location> comingBack = new ArrayList<Location>();
                for (int i = 0; i < gate.getGatePortalBlocks().size(); i++)
                {
                    final int index = gate.getGateAnimatedBlocks().size() - 1;
                    if (index >= 0)
                    {
                        comingBack.add(gate.getGateAnimatedBlocks().remove(index).getLocation());
                    }
                }
                StargateBlockSetup.undrawBlocks(gate, comingBack);
                if (gate.getGateAnimationStep2D() < ((wooshDepth * 2) - 1))
                {
                    gate.setGateAnimationStep2D(gate.getGateAnimationStep2D() + 1);
                    WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), new StargateUpdateRunnable(gate, ActionToTake.ANIMATE_WOOSH), 3);
                }
                else
                {
                    gate.setGateAnimationStep2D(0);
                    if (gate.isGateActive())
                    {
                        gate.fillGateInterior(wooshMaterial);
                    }
                }
            }
        }
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
                    StargateBlockSetup.drawBlocks(gate,
                        gate.getGateLightBlocks().get(gate.getGateLightingCurrentIteration()),
                        gate.getEffectiveLightMaterial());
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
            // whatever was drawn so far (the woosh material, one block out from the portal
            // on a 2D gate's very first step) showing to anyone nearby until their client
            // happens to get a fresh copy of that chunk some other way. Same principle as
            // the chevron undraw just above, extended to the animation that never had it:
            // closing reverts whatever was left showing, not just whatever it expected to
            // find. animateOpening's own gate.isGateActive() guard is what stops an
            // already-scheduled continuation from reading these reset-to-zero counters as
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
            gate.setGateAnimationStep2D(0);
            gate.setGateAnimationStep3D(0);
            gate.setGateAnimationRemoving(false);
        }
    }
}
