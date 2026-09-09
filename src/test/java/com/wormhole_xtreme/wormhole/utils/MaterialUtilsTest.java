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
}
