package com.wormhole_xtreme.wormhole.utils;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

public class LegacyCompatTest {

    @Test
    public void testMaterialFromId() {
        assertEquals(Material.AIR, LegacyCompat.materialFromId(0));
        assertEquals(Material.WATER, LegacyCompat.materialFromId(8));
        assertEquals(Material.WATER, LegacyCompat.materialFromId(9));
        assertEquals(Material.LAVA, LegacyCompat.materialFromId(10));
        assertEquals(Material.LEVER, LegacyCompat.materialFromId(69));
        assertEquals(Material.STONE_BUTTON, LegacyCompat.materialFromId(77));
    }
}
