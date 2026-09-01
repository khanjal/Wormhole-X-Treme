package com.wormhole_xtreme.wormhole.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.block.Block;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

public class WorldUtilsTest {

    @Test
    public void testIsSameBlock() {
        Block b1 = mock(Block.class);
        Block b2 = mock(Block.class);

        when(b1.getX()).thenReturn(1);
        when(b1.getY()).thenReturn(2);
        when(b1.getZ()).thenReturn(3);

        when(b2.getX()).thenReturn(1);
        when(b2.getY()).thenReturn(2);
        when(b2.getZ()).thenReturn(3);

        assertTrue(WorldUtils.isSameBlock(b1, b2));

        when(b2.getZ()).thenReturn(4);
        assertFalse(WorldUtils.isSameBlock(b1, b2));
    }

    @Test
    public void testIsAdjacent() {
        Block b1 = mock(Block.class);
        Block b2 = mock(Block.class);

        when(b1.getX()).thenReturn(10);
        when(b1.getY()).thenReturn(20);
        when(b1.getZ()).thenReturn(30);

        // same block
        when(b2.getX()).thenReturn(10);
        when(b2.getY()).thenReturn(20);
        when(b2.getZ()).thenReturn(30);
        assertTrue(WorldUtils.isAdjacent(b1, b2));

        // within 1 block
        when(b2.getX()).thenReturn(9);
        when(b2.getY()).thenReturn(20);
        when(b2.getZ()).thenReturn(29);
        assertTrue(WorldUtils.isAdjacent(b1, b2));

        // outside radius
        when(b2.getX()).thenReturn(12);
        when(b2.getY()).thenReturn(20);
        when(b2.getZ()).thenReturn(30);
        assertFalse(WorldUtils.isAdjacent(b1, b2));
    }

    @Test
    public void testIsIce() {
        assertTrue(MaterialUtils.isIce(Material.ICE));
        assertTrue(MaterialUtils.isIce(Material.PACKED_ICE));
        assertTrue(MaterialUtils.isIce(Material.BLUE_ICE));
        assertTrue(MaterialUtils.isIce(Material.FROSTED_ICE));
        assertFalse(MaterialUtils.isIce(Material.AIR));
    }
}
