package com.wormhole_xtreme.wormhole.model.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Cell;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Palette;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Part;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Role;

/** The guide calls a block in place exactly where gate detection would accept it. */
class BuildGuideTest
{
    private static final Palette LAMPS = new Palette(Material.OBSIDIAN, Material.REDSTONE_LAMP, Material.GLOWSTONE,
        Material.WATER, Material.STONE, Material.OAK_WALL_SIGN);

    private static final Cell FRAME = new Cell(0, 0, 0, Part.FRAME, 0);
    private static final Cell LIT = new Cell(1, 0, 0, Part.FRAME, 3);
    private static final Cell STRICT = new Cell(2, 0, 0, Part.CHEVRON, 0);
    private static final Cell BUTTON = new Cell(3, 0, 0, Part.BUTTON, 0, true);
    private static final Cell SIGN = new Cell(4, 0, 0, Part.DIAL_SIGN, 0, true);

    /** Nothing, or any of the three airs, is still to place. */
    @Test
    void anEmptyCellIsMissing()
    {
        for (final Material empty : new Material[] { null, Material.AIR, Material.CAVE_AIR, Material.VOID_AIR })
        {
            assertEquals(BuildGuide.State.MISSING, BuildGuide.of(FRAME, LAMPS, empty), String.valueOf(empty));
            assertFalse(BuildGuide.blocksOpening(empty), String.valueOf(empty));
        }
        assertTrue(BuildGuide.blocksOpening(Material.DIRT));
    }

    /** A frame cell takes the frame; a lit chevron takes the frame or the chevron block; [C] only the chevron. */
    @Test
    void framesAndChevronsTakeWhatDetectionTakes()
    {
        assertEquals(BuildGuide.State.PLACED, BuildGuide.of(FRAME, LAMPS, Material.OBSIDIAN));
        assertEquals(BuildGuide.State.WRONG, BuildGuide.of(FRAME, LAMPS, Material.REDSTONE_LAMP),
            "a chevron block away from a chevron");

        assertEquals(BuildGuide.State.PLACED, BuildGuide.of(LIT, LAMPS, Material.OBSIDIAN));
        assertEquals(BuildGuide.State.PLACED, BuildGuide.of(LIT, LAMPS, Material.REDSTONE_LAMP));
        assertEquals(BuildGuide.State.WRONG, BuildGuide.of(LIT, LAMPS, Material.DIRT));

        assertEquals(BuildGuide.State.PLACED, BuildGuide.of(STRICT, LAMPS, Material.REDSTONE_LAMP));
        assertEquals(BuildGuide.State.WRONG, BuildGuide.of(STRICT, LAMPS, Material.OBSIDIAN));
        final Palette plain = LAMPS.with(Role.CHEVRON, null);
        assertEquals(BuildGuide.State.PLACED, BuildGuide.of(STRICT, plain, Material.OBSIDIAN),
            "[C] means [S] where there is no chevron block");
        assertEquals(BuildGuide.State.WRONG, BuildGuide.of(LIT, plain, Material.REDSTONE_LAMP));
    }

    /** The DHD takes any button or a lever, and the dial sign any wall sign. */
    @Test
    void theDhdTakesAnyButtonOrLeverAndTheSignAnyWallSign()
    {
        assertEquals(BuildGuide.State.PLACED, BuildGuide.of(BUTTON, LAMPS, Material.WARPED_BUTTON));
        assertEquals(BuildGuide.State.PLACED, BuildGuide.of(BUTTON, LAMPS, Material.LEVER));
        assertEquals(BuildGuide.State.WRONG, BuildGuide.of(BUTTON, LAMPS, Material.TORCH));

        assertEquals(BuildGuide.State.PLACED, BuildGuide.of(SIGN, LAMPS, Material.BIRCH_WALL_SIGN));
        assertEquals(BuildGuide.State.WRONG, BuildGuide.of(SIGN, LAMPS, Material.BIRCH_SIGN), "not on the wall");
    }

    /**
     * The list counts each material in the order the gate first asks for it, names what to gather,
     * and counts what is not yet accepted as still to place.
     */
    @Test
    void theListCountsEachMaterialAndWhatIsStillToPlace()
    {
        final List<Cell> cells = List.of(FRAME, new Cell(9, 0, 0, Part.FRAME, 0), LIT, BUTTON, SIGN);
        final Map<Cell, Material> world = Map.of(FRAME, Material.OBSIDIAN, LIT, Material.DIRT, SIGN,
            Material.OAK_WALL_SIGN);

        assertEquals(List.of(new BuildGuide.Need("obsidian", 2, 1), new BuildGuide.Need("redstone_lamp or obsidian", 1, 1),
            new BuildGuide.Need("button or lever", 1, 1), new BuildGuide.Need("oak_sign", 1, 0)),
            BuildGuide.needs(cells, LAMPS, LAMPS, world::get));

        final Palette plain = LAMPS.with(Role.CHEVRON, null);
        assertEquals(new BuildGuide.Need("obsidian", 3, 2), BuildGuide.needs(cells, LAMPS, plain, world::get).get(0),
            "with chevrons drawn as frame, they are counted as frame");
    }
}
