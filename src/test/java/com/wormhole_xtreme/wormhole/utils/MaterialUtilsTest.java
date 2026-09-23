package com.wormhole_xtreme.wormhole.utils;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class MaterialUtilsTest {

    @Test
    void testIsButtonAndWallSign() {
        assertTrue(MaterialUtils.isButton(Material.OAK_BUTTON));
        assertTrue(MaterialUtils.isButton(Material.STONE_BUTTON));
        assertFalse(MaterialUtils.isButton(Material.AIR));

        assertTrue(MaterialUtils.isWallSign(Material.OAK_WALL_SIGN));
        assertFalse(MaterialUtils.isWallSign(Material.AIR));
    }

    /**
     * Which iris materials would hide a wormhole drawn right behind them.
     *
     * <p>Java Edition draws these and water in one translucent pass and skips the face between
     * two of them, so a gate has to leave a block of air between its layers for any of them.
     * Plain glass is the one that gives the rule away: it is a different pass and shows the
     * water perfectly well, so it must not be caught here or every glass gate pays for a gap
     * it does not need.
     */
    @Test
    void testCullsWaterBehindIt() {
        assertTrue(MaterialUtils.cullsWaterBehindIt(Material.YELLOW_STAINED_GLASS),
            "stained glass is what the Atlantis and Universe palettes give an iris");
        assertTrue(MaterialUtils.cullsWaterBehindIt(Material.LIME_STAINED_GLASS_PANE));
        assertTrue(MaterialUtils.cullsWaterBehindIt(Material.TINTED_GLASS));
        assertTrue(MaterialUtils.cullsWaterBehindIt(Material.ICE));
        assertTrue(MaterialUtils.cullsWaterBehindIt(Material.SLIME_BLOCK));

        assertFalse(MaterialUtils.cullsWaterBehindIt(Material.GLASS),
            "plain glass is a cutout, not a translucent: it shows the water as it is");
        assertFalse(MaterialUtils.cullsWaterBehindIt(Material.GLASS_PANE));
        assertFalse(MaterialUtils.cullsWaterBehindIt(Material.PACKED_ICE),
            "packed and blue ice are solid, whatever plain ice does");
        assertFalse(MaterialUtils.cullsWaterBehindIt(Material.BLUE_ICE));
        assertFalse(MaterialUtils.cullsWaterBehindIt(Material.IRON_BLOCK));
        assertFalse(MaterialUtils.cullsWaterBehindIt(Material.AIR));
        assertFalse(MaterialUtils.cullsWaterBehindIt(null));
    }

    /**
     * What a horizon is drawn as when it has to sit behind an iris that would hide the liquid.
     *
     * <p>Solid, not merely a different translucent. Blue glass behind a yellow iris was tried
     * in a world and is not drawn either, so the stand-in has to be something nothing can cull.
     */
    @Test
    void testShownBehindGlassAs() {
        assertEquals(Material.BLUE_ICE, MaterialUtils.shownBehindGlassAs(Material.WATER, false),
            "solid and the closest thing to water: blue glass behind glass does not render");
        assertEquals(Material.PACKED_ICE, MaterialUtils.shownBehindGlassAs(Material.WATER, true),
            "and a second one, or a flat sheet of one colour reads as ice rather than water");
        assertNotEquals(MaterialUtils.shownBehindGlassAs(Material.WATER, false),
            MaterialUtils.shownBehindGlassAs(Material.WATER, true),
            "the two squares of the checkerboard have to differ, or there is no checkerboard");

        assertEquals(Material.LAVA, MaterialUtils.shownBehindGlassAs(Material.LAVA, false),
            "lava is not drawn translucent, so it shows through a glass iris as it is");
        assertEquals(Material.LAVA, MaterialUtils.shownBehindGlassAs(Material.LAVA, true));

        assertFalse(MaterialUtils.cullsWaterBehindIt(MaterialUtils.shownBehindGlassAs(Material.WATER, false)),
            "and neither stand-in may be a thing that gets culled itself");
        assertFalse(MaterialUtils.cullsWaterBehindIt(MaterialUtils.shownBehindGlassAs(Material.WATER, true)));

        assertEquals(Material.PURPLE_CONCRETE,
            MaterialUtils.shownBehindGlassAs(Material.NETHER_PORTAL, false),
            "a nether portal is translucent too, and was hidden behind a glass iris the way water was");
        assertEquals(Material.MAGENTA_CONCRETE,
            MaterialUtils.shownBehindGlassAs(Material.NETHER_PORTAL, true),
            "and the brighter of the two, for the swirls over its darker ground");
        assertNotEquals(MaterialUtils.shownBehindGlassAs(Material.NETHER_PORTAL, false),
            MaterialUtils.shownBehindGlassAs(Material.NETHER_PORTAL, true),
            "this checkerboard has to have two squares as well");

        assertFalse(MaterialUtils.cullsWaterBehindIt(MaterialUtils.shownBehindGlassAs(Material.NETHER_PORTAL, false)),
            "and neither of these may be a thing that gets culled itself, or the fix fixes nothing");
        assertFalse(MaterialUtils.cullsWaterBehindIt(MaterialUtils.shownBehindGlassAs(Material.NETHER_PORTAL, true)));

        assertNotEquals(MaterialUtils.shownBehindGlassAs(Material.WATER, false),
            MaterialUtils.shownBehindGlassAs(Material.NETHER_PORTAL, false),
            "water and a portal do not stand in for each other: one is a blue surface, one is purple");

        assertEquals(Material.OBSIDIAN, MaterialUtils.shownBehindGlassAs(Material.OBSIDIAN, false),
            "anything opaque stands in for itself -- it was never at risk of being culled");
        assertNull(MaterialUtils.shownBehindGlassAs(null, false));
    }
}
