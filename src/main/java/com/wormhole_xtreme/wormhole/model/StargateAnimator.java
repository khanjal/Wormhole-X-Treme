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
        final Material wooshMaterial = gate.isGateCustom()
            ? gate.getGateCustomPortalMaterial()
            : gate.getGateShape() != null
                ? gate.getGateShape().getShapePortalMaterial()
                : Material.WATER;
        final int wooshDepth = gate.isGateCustom()
            ? gate.getGateCustomWooshDepth()
            : gate.getGateShape() != null
                ? gate.getGateShape().getShapeWooshDepth()
                : 0;

        if ((gate.getGateWooshBlocks() != null) && (gate.getGateWooshBlocks().size() > 0))
        {
            final ArrayList<Location> wooshBlockStep = gate.getGateWooshBlocks().get(gate.getGateAnimationStep3D());
            if (!gate.isGateAnimationRemoving())
            {
                if (wooshBlockStep != null)
                {
                    for (final Location l : wooshBlockStep)
                    {
                        final Block b = gate.getGateWorld().getBlockAt(l.getBlockX(), l.getBlockY(), l.getBlockZ());
                        final Material prev = b.getType();
                        gate.getGateAnimatedBlocks().add(b);
                        final Location key = StargateManager.normalizeBlockLocation(l);
                        StargateManager.getOpeningAnimationOriginalMaterials().put(key, prev);
                        StargateManager.getOpeningAnimationBlocks().put(key, b);
                        b.setType(wooshMaterial);
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
                WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), new StargateUpdateRunnable(gate, ActionToTake.ANIMATE_WOOSH), gate.isGateCustom()
                    ? gate.getGateCustomWooshTicks()
                    : gate.getGateShape() != null
                        ? gate.getGateShape().getShapeWooshTicks()
                        : 2);
            }
            else
            {
                // remove in reverse order — only clear blocks that are not portal blocks
                if (wooshBlockStep != null)
                {
                    for (final Location l : wooshBlockStep)
                    {
                        final Block b = gate.getGateWorld().getBlockAt(l.getBlockX(), l.getBlockY(), l.getBlockZ());
                        final Location key = StargateManager.normalizeBlockLocation(l);
                        final Material original = StargateManager.getOpeningAnimationOriginalMaterials().remove(key);
                        StargateManager.getOpeningAnimationBlocks().remove(key, b);
                        gate.getGateAnimatedBlocks().remove(b);
                        if (original != null)
                        {
                            b.setType(original);
                        }
                        else if (!StargateManager.isBlockInGate(b))
                        {
                            b.setType(Material.AIR);
                        }
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
                    WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), new StargateUpdateRunnable(gate, ActionToTake.ANIMATE_WOOSH), gate.isGateCustom()
                        ? gate.getGateCustomWooshTicks()
                        : gate.getGateShape() != null
                            ? gate.getGateShape().getShapeWooshTicks()
                            : 2);
                }
            }
        }
        else
        {
            // 2D gate woosh
            if ((gate.getGateAnimationStep2D() == 0) && (wooshDepth > 0))
            {
                for (final Location block : gate.getGatePortalBlocks())
                {
                    final Block r = gate.getGateWorld().getBlockAt(block.getBlockX(), block.getBlockY(), block.getBlockZ())
                        .getRelative(gate.getGateFacing());
                    final Material prev = r.getType();
                    r.setType(wooshMaterial);
                    gate.getGateAnimatedBlocks().add(r);
                    final Location key2 = StargateManager.normalizeBlockLocation(r.getLocation());
                    StargateManager.getOpeningAnimationOriginalMaterials().put(key2, prev);
                    StargateManager.getOpeningAnimationBlocks().put(key2, r);
                }
                gate.setGateAnimationStep2D(gate.getGateAnimationStep2D() + 1);
                WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), new StargateUpdateRunnable(gate, ActionToTake.ANIMATE_WOOSH), 4);
            }
            else if (gate.getGateAnimationStep2D() < wooshDepth)
            {
                final int size = gate.getGateAnimatedBlocks().size();
                final int start = gate.getGatePortalBlocks().size();
                for (int i = (size - start); i < size; i++)
                {
                    final Block b = gate.getGateAnimatedBlocks().get(i);
                    final Block r = b.getRelative(gate.getGateFacing());
                    final Material prev = r.getType();
                    r.setType(wooshMaterial);
                    gate.getGateAnimatedBlocks().add(r);
                    final Location key3 = StargateManager.normalizeBlockLocation(r.getLocation());
                    StargateManager.getOpeningAnimationOriginalMaterials().put(key3, prev);
                    StargateManager.getOpeningAnimationBlocks().put(key3, r);
                }
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
                for (int i = 0; i < gate.getGatePortalBlocks().size(); i++)
                {
                    final int index = gate.getGateAnimatedBlocks().size() - 1;
                    if (index >= 0)
                    {
                        final Block b = gate.getGateAnimatedBlocks().get(index);
                        final Location key4 = StargateManager.normalizeBlockLocation(b.getLocation());
                        final Material original = StargateManager.getOpeningAnimationOriginalMaterials().remove(key4);
                        gate.getGateAnimatedBlocks().remove(index);
                        StargateManager.getOpeningAnimationBlocks().remove(key4);
                        if (original != null)
                        {
                            b.setType(original);
                        }
                        else if (!StargateManager.isBlockInGate(b))
                        {
                            b.setType(Material.AIR);
                        }
                    }
                }
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
                    for (final Location l : gate.getGateLightBlocks().get(gate.getGateLightingCurrentIteration()))
                    {
                        final Block b = gate.getGateWorld().getBlockAt(l.getBlockX(), l.getBlockY(), l.getBlockZ());
                        b.setType(gate.isGateCustom()
                            ? gate.getGateCustomLightMaterial()
                            : gate.getGateShape() != null
                                ? gate.getGateShape().getShapeLightMaterial()
                                : Material.GLOWSTONE);
                    }
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
                    WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), new StargateUpdateRunnable(gate, ActionToTake.LIGHTUP), gate.isGateCustom()
                        ? gate.getGateCustomLightTicks()
                        : gate.getGateShape() != null
                            ? gate.getGateShape().getShapeLightTicks()
                            : 2);
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
                        for (final Location l : gate.getGateLightBlocks().get(i))
                        {
                            final Block b = gate.getGateWorld().getBlockAt(l.getBlockX(), l.getBlockY(), l.getBlockZ());
                            b.setType(gate.isGateCustom()
                                ? gate.getGateCustomStructureMaterial()
                                : gate.getGateShape() != null
                                    ? gate.getGateShape().getShapeStructureMaterial()
                                    : Material.OBSIDIAN);
                        }
                    }
                }
            }
        }
    }
}
