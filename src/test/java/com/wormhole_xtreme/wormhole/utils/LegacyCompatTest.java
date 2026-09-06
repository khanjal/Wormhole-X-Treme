package com.wormhole_xtreme.wormhole.utils;

import static org.junit.jupiter.api.Assertions.*;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

class LegacyCompatTest {

    @Test
    void testMaterialFromId() {
        assertEquals(Material.AIR, LegacyCompat.materialFromId(0));
        assertEquals(Material.WATER, LegacyCompat.materialFromId(8));
        assertEquals(Material.WATER, LegacyCompat.materialFromId(9));
        assertEquals(Material.LAVA, LegacyCompat.materialFromId(10));
        assertEquals(Material.LEVER, LegacyCompat.materialFromId(69));
        assertEquals(Material.STONE_BUTTON, LegacyCompat.materialFromId(77));
    }

    /**
     * A server without the two-argument setType still gets its block placed.
     *
     * <p>This is why the catch here reaches past Exception. The overload is missing on some
     * of the builds this plugin supports, and a missing method arrives as a NoSuchMethodError,
     * not an exception. Narrowing this catch to RuntimeException would leave the block
     * unplaced on exactly the servers the fallback was written for.
     */
    @Test
    void aMissingSetTypeOverloadFallsBackToTheSingleArgumentOne() {
        final Block b = mock(Block.class);
        doThrow(new NoSuchMethodError("setType")).when(b).setType(any(Material.class), anyBoolean());

        LegacyCompat.setTypeIdAndData(b, 69, (byte) 0, true);

        verify(b).setType(Material.LEVER);
    }

    /** An Error that is not about a missing API is not swallowed. */
    @Test
    void anErrorThatIsNotALinkageProblemPropagates() {
        final Block b = mock(Block.class);
        doThrow(new OutOfMemoryError("heap")).when(b).setType(any(Material.class), anyBoolean());

        assertThrows(OutOfMemoryError.class,
            () -> LegacyCompat.setTypeIdAndData(b, 69, (byte) 0, true),
            "only a missing API is worth tolerating here");
    }
}
