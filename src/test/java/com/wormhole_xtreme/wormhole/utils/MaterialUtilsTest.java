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
            "plain glass is a cutout, not a translucent: it shows the water and needs no gap");
        assertFalse(MaterialUtils.cullsWaterBehindIt(Material.GLASS_PANE));
        assertFalse(MaterialUtils.cullsWaterBehindIt(Material.IRON_BLOCK));
        assertFalse(MaterialUtils.cullsWaterBehindIt(Material.AIR));
        assertFalse(MaterialUtils.cullsWaterBehindIt(null));
    }
}
