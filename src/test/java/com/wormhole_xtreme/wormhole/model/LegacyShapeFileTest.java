package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;

import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * Shape files written before {@code Version=2}, which a different parser still reads.
 *
 * <p>{@link com.wormhole_xtreme.wormhole.logic.StargateShapeFactory} sends anything without a
 * {@code Version=2} line here instead of to {@link Stargate3DShape}. Every shipped shape is
 * version 2, so this parser only ever runs on a server operator's own older file -- and
 * nothing constructed it in a test, so all of it was uncovered.
 *
 * <p>The marker alphabet is not the one version 2 uses: {@code O} is a structure block rather
 * than {@code S}, {@code S} is the sign, {@code E} the entry point, and a light is {@code L}
 * and {@code O} together on the same block.
 */
class LegacyShapeFileTest
{
    @BeforeEach
    void installPlugin() throws Exception
    {
        final Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, mock(WormholeXTreme.class));
    }

    private static String[] shape(final String... settings)
    {
        final java.util.List<String> lines = new java.util.ArrayList<String>();
        lines.add("Name=Legacy");
        for (final String s : settings)
        {
            lines.add(s);
        }
        lines.add("GateShape=");
        lines.add("[O][O]");
        lines.add("[LO][E]");
        lines.add("");
        return lines.toArray(new String[0]);
    }

    /**
     * Each marker goes to its own collection, and a light is recorded by block number.
     *
     * <p>Lights are stored as indexes into the structure blocks rather than as coordinates,
     * so the third structure block being the lit one means the light list holds 2.
     */
    @Test
    void theMarkerAlphabetIsReadAsVersionOneMeantIt()
    {
        final StargateShape s = new StargateShape(shape());

        assertEquals(3, s.getShapeStructurePositions().length, "two O markers and the LO one");
        assertEquals(0, s.getShapePortalPositions().length, "no P markers in this grid");
        assertArrayEquals(new int[] {2}, s.getShapeLightPositions(),
            "the light names the third structure block, counted from zero");
        assertArrayEquals(new int[] {0, 0, 0}, s.getShapeEnterPosition(), "E is the entry point");
    }

    /**
     * The three button offsets reach the corner the shape asked for.
     *
     * <p>They used not to. {@code getShapeToGateCorner()} hands back a clone, so
     * {@code getShapeToGateCorner()[1] = ...} wrote into a temporary that was dropped on the
     * next line, and a legacy shape naming its own button position silently got the default.
     */
    @Test
    void theButtonOffsetsKeepTheirOwnAxes()
    {
        final StargateShape s = new StargateShape(shape(
            "BUTTON_RIGHT=2", "BUTTON_UP=1", "BUTTON_AWAY=3"));

        assertArrayEquals(new int[] {2, 1, 3}, s.getShapeToGateCorner(),
            "right, up, away -- not the order the file lists them in");
    }

    /** Woosh depth is squared once at parse time rather than at every animation step. */
    @Test
    void wooshDepthIsSquaredOnce()
    {
        final StargateShape s = new StargateShape(shape("WOOSH_DEPTH=4"));

        assertEquals(4, s.getShapeWooshDepth());
        assertEquals(16, s.getShapeWooshDepthSquared());
    }

    /**
     * Materials are read, and each key feeds its own setting.
     *
     * <p>Every value here differs from that setting's default, so a key wired to the wrong
     * setter leaves its own setting at the default and fails.
     */
    @Test
    void everyMaterialKeyReachesItsOwnSetting()
    {
        final StargateShape s = new StargateShape(shape(
            "PORTAL_MATERIAL=LAVA",
            "IRIS_MATERIAL=IRON_BLOCK",
            "STARGATE_MATERIAL=DIAMOND_BLOCK",
            "ACTIVE_MATERIAL=SEA_LANTERN",
            "CHEVRON_MATERIAL=GOLD_BLOCK",
            "SIGN_MATERIAL=SPRUCE_WALL_SIGN"));

        assertEquals(Material.LAVA, s.getShapePortalMaterial(), "PORTAL_MATERIAL");
        assertEquals(Material.IRON_BLOCK, s.getShapeIrisMaterial(), "IRIS_MATERIAL");
        assertEquals(Material.DIAMOND_BLOCK, s.getShapeStructureMaterial(), "STARGATE_MATERIAL");
        assertEquals(Material.SEA_LANTERN, s.getShapeLightMaterial(), "ACTIVE_MATERIAL");
        assertEquals(Material.GOLD_BLOCK, s.getShapeChevronMaterial(), "CHEVRON_MATERIAL");
        assertEquals(Material.SPRUCE_WALL_SIGN, s.getShapeSignMaterial(), "SIGN_MATERIAL");
    }

    /**
     * A material name this server does not know falls back, whichever key it was.
     *
     * <p>The two halves used to disagree. CHEVRON and SIGN had a guard; PORTAL, IRIS,
     * STARGATE and ACTIVE called valueOf straight, so a block renamed between Minecraft
     * versions took the whole shape down and every gate built from it with it. They all go
     * through the same tolerant parse now, which is what version 2 shapes already did.
     */
    @Test
    void anUnknownMaterialNameIsToleratedForEveryKey()
    {
        final Material defaultPortal = new StargateShape(shape()).getShapePortalMaterial();

        final StargateShape s = new StargateShape(shape(
            "PORTAL_MATERIAL=NOT_A_REAL_BLOCK", "CHEVRON_MATERIAL=ALSO_NOT_REAL"));

        assertEquals(defaultPortal, s.getShapePortalMaterial(),
            "an unreadable portal name leaves the setting as a shape without the key at all");
        assertEquals(null, s.getShapeChevronMaterial(), "chevron falls back to the palette");
    }

    /** A shape with no rows under GateShape= is refused rather than half-built. */
    @Test
    void aShapeWithNoGridIsRefused()
    {
        final String[] lines = { "Name=Broken", "GateShape=", "" };

        assertThrows(IllegalArgumentException.class, () -> new StargateShape(lines));
    }
}
