package com.wormhole_xtreme.wormhole.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
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

    /**
     * Every entry of the sign table, written.
     *
     * <p>Stated as absolute faces rather than as a round trip: a round trip only proves the
     * two tables are inverses of each other, and they would still be inverses if both had
     * the same pair swapped. A wrong entry puts a rebuilt gate's sign on the wrong wall,
     * with nothing to notice.
     */
    @Test
    void theWholeSignTableIsWritten() {
        final BlockFace[] expected = { BlockFace.EAST, BlockFace.WEST, BlockFace.NORTH, BlockFace.SOUTH };
        for (byte data = 2; data <= 5; data++) {
            final WallSign sign = mock(WallSign.class);
            LegacyCompat.setData(blockOf(Material.OAK_WALL_SIGN, sign), data);
            verify(sign).setFacing(expected[data - 2]);
        }
    }

    /** And read. */
    @Test
    void theWholeSignTableIsRead() {
        final BlockFace[] faces = { BlockFace.EAST, BlockFace.WEST, BlockFace.NORTH, BlockFace.SOUTH };
        for (byte expected = 2; expected <= 5; expected++) {
            final WallSign sign = mock(WallSign.class);
            when(sign.getFacing()).thenReturn(faces[expected - 2]);
            assertEquals(expected, LegacyCompat.getData(blockOf(Material.OAK_WALL_SIGN, sign)),
                "sign byte " + expected);
        }
    }

    /** A byte the sign table does not name falls to south, as it always has. */
    @Test
    void anUnknownSignByteFacesSouth() {
        final WallSign sign = mock(WallSign.class);

        LegacyCompat.setData(blockOf(Material.OAK_WALL_SIGN, sign), (byte) 99);

        verify(sign).setFacing(BlockFace.SOUTH);
    }

    /** Every entry of the generic table. */
    @Test
    void theWholeGenericTableIsWritten() {
        final BlockFace[] expected = { BlockFace.SOUTH, BlockFace.NORTH, BlockFace.WEST, BlockFace.EAST };
        for (byte data = 1; data <= 4; data++) {
            final Switch lever = mock(Switch.class);
            LegacyCompat.setData(blockOf(Material.LEVER, lever), data);
            verify(lever).setFacing(expected[data - 1]);
        }
    }

    /** And read back. */
    @Test
    void theWholeGenericTableIsRead() {
        final BlockFace[] faces = { BlockFace.SOUTH, BlockFace.NORTH, BlockFace.WEST, BlockFace.EAST };
        for (byte expected = 1; expected <= 4; expected++) {
            final Switch lever = mock(Switch.class);
            when(lever.getFacing()).thenReturn(faces[expected - 1]);
            assertEquals(expected, LegacyCompat.getData(blockOf(Material.LEVER, lever)),
                "generic byte " + expected);
        }
    }

    /**
     * A byte the generic table does not name leaves the block facing where it was.
     *
     * <p>Not south, and not north: the legacy byte simply has nothing to say, and a gate
     * being rebuilt keeps whatever the world already had.
     */
    @Test
    void anUnknownGenericByteLeavesTheFacingAlone() {
        final Switch lever = mock(Switch.class);
        when(lever.getFacing()).thenReturn(BlockFace.UP);

        LegacyCompat.setData(blockOf(Material.LEVER, lever), (byte) 0);

        verify(lever).setFacing(BlockFace.UP);
    }

    /**
     * A block that cannot be read is worth nothing, not a crash.
     *
     * <p>This runs while a world is loading old gates; one unreadable block must not take the
     * rest of the gate down with it.
     */
    @Test
    void aBlockThatCannotBeReadIsZero() {
        final Block b = mock(Block.class);
        doThrow(new IllegalStateException("chunk not loaded")).when(b).getBlockData();

        assertEquals((byte) 0, LegacyCompat.getData(b));
    }

    /**
     * Nor does a redstone accessor that blows up.
     *
     * <p>This pins the outcome and not which catch produces it: getData's own catch would
     * swallow the same throw if the one inside redstonePower were taken out, so removing
     * that inner catch leaves this test passing.
     */
    @Test
    void redstoneThatWillNotAnswerIsZero() {
        final RedstoneWire wire = mock(RedstoneWire.class);
        when(wire.getPower()).thenThrow(new IllegalStateException("no"));

        assertEquals((byte) 0, LegacyCompat.getData(blockOf(Material.REDSTONE_WIRE, wire)));
    }

    /**
     * A powered block keeps its facing.
     *
     * <p>getData writes the powered bit on top of the facing, so a powered lever facing east
     * is 12, not 4. setData read the whole byte, matched no entry in the generic table, and
     * left the block facing wherever it already happened to be -- which, on a gate being
     * rebuilt from an old save, is whatever the world put there.
     *
     * <p>Stated as absolute faces per byte rather than as a round trip through getData, so a
     * matching mistake in both halves cannot pass.
     */
    @Test
    void thePowerBitDoesNotEatTheFacing() {
        final BlockFace[] expected = { BlockFace.SOUTH, BlockFace.NORTH, BlockFace.WEST, BlockFace.EAST };
        for (byte facing = 1; facing <= 4; facing++) {
            final Switch lever = mock(Switch.class);
            when(lever.getFacing()).thenReturn(BlockFace.UP);

            LegacyCompat.setData(blockOf(Material.LEVER, lever), (byte) (facing | 0x8));

            verify(lever).setFacing(expected[facing - 1]);
            verify(lever).setPowered(true);
        }
    }

    /** A byte carrying the bit and nothing else still says nothing about the facing. */
    @Test
    void thePowerBitAloneLeavesTheFacingAlone() {
        final Switch lever = mock(Switch.class);
        when(lever.getFacing()).thenReturn(BlockFace.UP);

        LegacyCompat.setData(blockOf(Material.LEVER, lever), (byte) 0x8);

        verify(lever).setFacing(BlockFace.UP);
        verify(lever).setPowered(true);
    }
}
