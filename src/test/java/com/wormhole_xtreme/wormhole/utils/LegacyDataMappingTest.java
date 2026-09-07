package com.wormhole_xtreme.wormhole.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.RedstoneWire;
import org.bukkit.block.data.type.Switch;
import org.bukkit.block.data.type.WallSign;
import org.junit.jupiter.api.Test;

/**
 * The legacy data byte, in both directions.
 *
 * <p>Gates saved before the flattening store a facing and a power level as one byte, and
 * these two methods are the only thing that still understands it. Nothing asserted either of
 * them: a wrong table here rebuilds an old gate with its sign on the wrong wall, or its lever
 * unpowered, and there is no error to notice.
 *
 * <p>Signs use a different table to everything else -- 2/3/4/5 for east/west/north/south
 * where a lever uses 1/2/3/4 for south/north/west/east -- so the two are pinned separately
 * and with the same input byte, which the tables disagree about.
 */
class LegacyDataMappingTest {

    private Block blockOf(final Material type, final Object data) {
        final Block b = mock(Block.class);
        when(b.getType()).thenReturn(type);
        when(b.getBlockData()).thenReturn((org.bukkit.block.data.BlockData) data);
        return b;
    }

    /** A wall sign reads back through the sign table. */
    @Test
    void aWallSignsFacingReadsThroughTheSignTable() {
        final WallSign sign = mock(WallSign.class);
        when(sign.getFacing()).thenReturn(BlockFace.EAST);

        assertEquals((byte) 2, LegacyCompat.getData(blockOf(Material.OAK_WALL_SIGN, sign)),
            "east is 2 on a sign");
    }

    /** Everything else reads back through the generic one, which disagrees. */
    @Test
    void aLeversFacingReadsThroughTheGenericTable() {
        final Switch lever = mock(Switch.class);
        when(lever.getFacing()).thenReturn(BlockFace.EAST);

        assertEquals((byte) 4, LegacyCompat.getData(blockOf(Material.LEVER, lever)),
            "east is 4 on anything that is not a sign");
    }

    /** A powered block carries the high bit alongside its facing. */
    @Test
    void beingPoweredSetsTheHighBit() {
        final Switch on = mock(Switch.class);
        when(on.getFacing()).thenReturn(BlockFace.SOUTH);
        when(on.isPowered()).thenReturn(true);

        final Switch off = mock(Switch.class);
        when(off.getFacing()).thenReturn(BlockFace.SOUTH);

        assertEquals((byte) 9, LegacyCompat.getData(blockOf(Material.LEVER, on)),
            "south, and powered");
        assertEquals((byte) 1, LegacyCompat.getData(blockOf(Material.LEVER, off)),
            "south, and not");
    }

    /**
     * Redstone wire's power level is its whole byte.
     *
     * <p>It is reached by reflection because the getter is not on any interface this plugin
     * can compile against across every supported server.
     */
    @Test
    void redstoneWirePowerReadsBackAsTheByte() {
        final RedstoneWire wire = mock(RedstoneWire.class);
        when(wire.getPower()).thenReturn(7);

        assertEquals((byte) 7, LegacyCompat.getData(blockOf(Material.REDSTONE_WIRE, wire)));
    }

    /** Writing a sign's byte uses the sign table. */
    @Test
    void aWallSignsFacingIsWrittenThroughTheSignTable() {
        final WallSign sign = mock(WallSign.class);
        final Block b = blockOf(Material.OAK_WALL_SIGN, sign);

        LegacyCompat.setData(b, (byte) 2);

        verify(sign).setFacing(BlockFace.EAST);
    }

    /** The same byte on a lever means somewhere else entirely. */
    @Test
    void aLeversFacingIsWrittenThroughTheGenericTable() {
        final Switch lever = mock(Switch.class);
        final Block b = blockOf(Material.LEVER, lever);

        LegacyCompat.setData(b, (byte) 2);

        verify(lever).setFacing(BlockFace.NORTH);
    }

    /** The high bit powers the block, and its absence unpowers it. */
    @Test
    void theHighBitPowersTheBlock() {
        final Switch on = mock(Switch.class);
        LegacyCompat.setData(blockOf(Material.LEVER, on), (byte) 0x8);
        verify(on).setPowered(true);

        final Switch off = mock(Switch.class);
        LegacyCompat.setData(blockOf(Material.LEVER, off), (byte) 0x1);
        verify(off).setPowered(false);
    }

    /** And redstone wire takes its power from the low nibble. */
    @Test
    void redstoneWirePowerIsWrittenFromTheLowNibble() {
        final RedstoneWire wire = mock(RedstoneWire.class);

        LegacyCompat.setData(blockOf(Material.REDSTONE_WIRE, wire), (byte) 0x87);

        verify(wire).setPower(7);
    }
}
