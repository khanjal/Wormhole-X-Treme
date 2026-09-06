package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;

import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * The settings a shape file can carry, and what a broken one does.
 *
 * <p>Six {@code *_MATERIAL=} keys are parsed by six near-identical branches, and only two of
 * them were asserted anywhere. A key wired to the wrong setter would have been invisible: the
 * shipped shapes mostly leave these unset and take the palette from config instead.
 *
 * <p>The two failure paths were untested as well, and both matter more than they look. A shape
 * that cannot be measured, or one with no player exit, throws rather than returning a
 * half-built shape that would fail later at a gate somebody had already built.
 */
class ShapeFileSettingsTest
{
    @BeforeEach
    void installPlugin() throws Exception
    {
        final Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, mock(WormholeXTreme.class));
    }

    /**
     * A two-by-two shape carrying whatever settings the caller wants to try.
     *
     * @param settings
     *            the {@code KEY=value} lines to put above the grid
     */
    private static String[] shape(final String... settings)
    {
        final java.util.List<String> lines = new java.util.ArrayList<String>();
        lines.add("Name=NetTest");
        for (final String s : settings)
        {
            lines.add(s);
        }
        lines.add("GateShape=");
        lines.add("Layer#1=");
        lines.add("[S][S]");
        lines.add("[S:EP][S]");
        lines.add("");
        return lines.toArray(new String[0]);
    }

    @Test
    void everyMaterialKeyReachesItsOwnSetting()
    {
        // Every value here differs from that setting's default (WATER, STONE, OBSIDIAN,
        // GLOWSTONE, null, OAK_WALL_SIGN), so a key wired to the wrong setter leaves its own
        // setting at the default and fails. Using the defaults asserts nothing at all.
        final Stargate3DShape s = new Stargate3DShape(shape(
            "PORTAL_MATERIAL=LAVA",
            "IRIS_MATERIAL=IRON_BLOCK",
            "STARGATE_MATERIAL=DIAMOND_BLOCK",
            "ACTIVE_MATERIAL=SEA_LANTERN",
            "CHEVRON_MATERIAL=GOLD_BLOCK",
            "SIGN_MATERIAL=SPRUCE_WALL_SIGN"));

        assertEquals(Material.LAVA, s.getShapePortalMaterial(), "PORTAL_MATERIAL");
        assertEquals(Material.IRON_BLOCK, s.getShapeIrisMaterial(), "IRIS_MATERIAL");
        assertEquals(Material.DIAMOND_BLOCK, s.getShapeStructureMaterial(), "STARGATE_MATERIAL");
        assertEquals(Material.SEA_LANTERN, s.getShapeLightMaterial(), "ACTIVE_MATERIAL feeds the light");
        assertEquals(Material.GOLD_BLOCK, s.getShapeChevronMaterial(), "CHEVRON_MATERIAL");
        assertEquals(Material.SPRUCE_WALL_SIGN, s.getShapeSignMaterial(), "SIGN_MATERIAL");
    }

    @Test
    void tickCountsAndFlagsAreRead()
    {
        final Stargate3DShape s = new Stargate3DShape(shape(
            "LIGHT_TICKS=5", "WOOSH_TICKS=9", "REDSTONE_ACTIVATED=TRUE"));

        assertEquals(5, s.getShapeLightTicks());
        assertEquals(9, s.getShapeWooshTicks());
        assertTrue(s.isShapeRedstoneActivated());
    }

    /**
     * A material name the server does not know leaves the setting alone.
     *
     * <p>Shapes outlive the versions they were written for, so an unknown name has to mean
     * "fall back to the palette", not "refuse to load the gate".
     */
    @Test
    void anUnknownMaterialNameIsIgnoredRatherThanFatal()
    {
        final Material untouched = new Stargate3DShape(shape()).getShapePortalMaterial();

        final Stargate3DShape s = new Stargate3DShape(shape("PORTAL_MATERIAL=NOT_A_REAL_BLOCK"));

        assertEquals(untouched, s.getShapePortalMaterial(),
            "an unreadable name leaves the setting exactly as a shape without the key at all");
    }

    /** A shape whose grid cannot be measured refuses to load rather than half-loading. */
    @Test
    void aShapeWithNoGridIsRefused()
    {
        final String[] lines = { "Name=Broken", "GateShape=", "" };

        assertThrows(RuntimeException.class, () -> new Stargate3DShape(lines),
            "a shape with no rows cannot be built into anything");
    }

    /** A shape with no player exit is refused: every gate needs somewhere to put travellers. */
    @Test
    void aShapeWithNoPlayerExitIsRefused()
    {
        final String[] lines = { "Name=NoExit", "GateShape=", "Layer#1=", "[S][S]", "[S][S]", "" };

        final IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
            () -> new Stargate3DShape(lines));
        assertTrue(thrown.getMessage().contains("enterance"),
            "the message names the missing exit: " + thrown.getMessage());
    }
}
