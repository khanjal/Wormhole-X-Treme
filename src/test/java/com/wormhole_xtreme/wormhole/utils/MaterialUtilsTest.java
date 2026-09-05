package com.wormhole_xtreme.wormhole.utils;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class MaterialUtilsTest {

    @Test
    void testIsRail() {
        assertTrue(MaterialUtils.isRail(Material.RAIL));
    }

    @Test
    void testIsLiquid() {
        assertTrue(MaterialUtils.isLiquid(Material.WATER));
        assertTrue(MaterialUtils.isLiquid(Material.LAVA));
        assertFalse(MaterialUtils.isLiquid(Material.AIR));
    }

    @Test
    void testIsDoor() {
        assertTrue(MaterialUtils.isDoor(Material.OAK_DOOR));
        assertTrue(MaterialUtils.isDoor(Material.OAK_TRAPDOOR));
        assertFalse(MaterialUtils.isDoor(Material.AIR));
    }

    @Test
    void testIsSign() {
        assertTrue(MaterialUtils.isSign(Material.OAK_WALL_SIGN));
        assertTrue(MaterialUtils.isSign(Material.OAK_SIGN));
        assertFalse(MaterialUtils.isSign(Material.AIR));
    }

    @Test
    void testIsButtonAndWallSign() {
        assertTrue(MaterialUtils.isButton(Material.OAK_BUTTON));
        assertTrue(MaterialUtils.isButton(Material.STONE_BUTTON));
        assertFalse(MaterialUtils.isButton(Material.AIR));

        assertTrue(MaterialUtils.isWallSign(Material.OAK_WALL_SIGN));
        assertFalse(MaterialUtils.isWallSign(Material.AIR));
    }

    @Test
    void testWaterLavaHelpers() {
        assertTrue(MaterialUtils.isWater(Material.WATER));
        assertTrue(MaterialUtils.isLava(Material.LAVA));
        assertFalse(MaterialUtils.isWater(Material.AIR));
    }
}
