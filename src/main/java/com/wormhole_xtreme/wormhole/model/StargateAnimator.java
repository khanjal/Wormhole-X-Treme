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
     * @param gate the gate being animated
     */
    static void animateOpening(final Stargate gate)
    {
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
        }
    }
}
