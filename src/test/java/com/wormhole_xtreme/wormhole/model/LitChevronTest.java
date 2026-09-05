package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.Test;

/**
 * A chevron built out of the chevron material lights as that same block switched on.
 *
 * <p>Chevrons used to be drawn as the gate's light material and nothing else, which was fine
 * while every chevron was obsidian: glowstone appearing in an obsidian frame reads as a
 * chevron lighting. Once a player can build one out of a redstone lamp it stops reading that
 * way -- a lamp that turns into glowstone for a few seconds and back again looks like the
 * block was swapped, not switched on.
 *
 * <p>The choice is per position rather than per gate on purpose. Detection accepts either the
 * frame material or the chevron material at a chevron cell, so a gate can quite legitimately
 * have lamps at three chevrons and obsidian at the other four, and each has to light as
 * whatever it actually is.
 */
public class LitChevronTest
{
    /** Stands in for the gate's light material, already drawn lit. */
    private static final BlockData LIGHT = mock(BlockData.class);

    /** Stands in for the chevron material switched on. */
    private static final BlockData FIXTURE_ON = mock(BlockData.class);

    /**
     * The case the whole feature exists for: a lamp chevron lights by coming on.
     */
    @Test
    public void aChevronBuiltFromTheChevronMaterialLightsAsThatSameFixture()
    {
        assertSame(FIXTURE_ON, StargateBlockSetup.litChevron(
            Material.REDSTONE_LAMP, Material.REDSTONE_LAMP, FIXTURE_ON, LIGHT));
    }

    /**
     * A chevron built the old way still lights the old way.
     *
     * <p>This is most gates in the world, including every gate on a server that turns the
     * chevron material on tomorrow: the palette gains a chevron material, but the obsidian
     * already standing in those positions is not the chevron material and must go on being
     * drawn as glowstone rather than as an obsidian block that never visibly changes.
     */
    @Test
    public void aChevronBuiltFromTheFrameMaterialStillLightsAsTheGatesLightMaterial()
    {
        assertSame(LIGHT, StargateBlockSetup.litChevron(
            Material.OBSIDIAN, Material.REDSTONE_LAMP, FIXTURE_ON, LIGHT));
    }

    /**
     * One gate can have both, and each position is answered on its own.
     *
     * <p>Detection accepts either material at a chevron cell, so this is not a hypothetical
     * arrangement -- it is what you get by replacing three of seven obsidian chevrons with
     * lamps and never touching the rest.
     */
    @Test
    public void aGateWithSomeOfEachLightsEachPositionAsWhateverIsThere()
    {
        assertSame(FIXTURE_ON, StargateBlockSetup.litChevron(
            Material.REDSTONE_LAMP, Material.REDSTONE_LAMP, FIXTURE_ON, LIGHT));
        assertSame(LIGHT, StargateBlockSetup.litChevron(
            Material.OBSIDIAN, Material.REDSTONE_LAMP, FIXTURE_ON, LIGHT));
    }

    /**
     * A chevron material with no lit state falls back rather than sitting there inert.
     *
     * <p>Nothing stops a palette naming GOLD_BLOCK as its chevron material -- a gold chevron in
     * an obsidian frame is a perfectly good look, and it is what the shipped Diamond palette
     * suggestion is built around. But gold has no on state, so drawing it as itself would mean
     * the chevron never appeared to light at all: the dialling sequence would run, the sounds
     * would play, and nothing on screen would change. Falling back to the light material keeps
     * the animation visible, which matters more than the fixture conceit.
     */
    @Test
    public void aChevronMaterialWithNoLitStateFallsBackToTheLightMaterial()
    {
        // fixtureOn is what litFormOf answers for a material that is not Lightable: null.
        assertSame(LIGHT, StargateBlockSetup.litChevron(
            Material.GOLD_BLOCK, Material.GOLD_BLOCK, null, LIGHT));
    }

    /**
     * A gate whose palette names no chevron material behaves exactly as it did before.
     *
     * <p>The ordinary case on any server that has not opted in, and the one that has to stay
     * boring: every position draws the light material, with no per-block question asked.
     */
    @Test
    public void aGateWithNoChevronMaterialLightsEverythingAsTheLightMaterial()
    {
        assertSame(LIGHT, StargateBlockSetup.litChevron(Material.OBSIDIAN, null, null, LIGHT));
        assertSame(LIGHT, StargateBlockSetup.litChevron(Material.REDSTONE_LAMP, null, null, LIGHT));
    }
}
