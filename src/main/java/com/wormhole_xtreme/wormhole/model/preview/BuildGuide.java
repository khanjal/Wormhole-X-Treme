package com.wormhole_xtreme.wormhole.model.preview;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import org.bukkit.Material;

import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Cell;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Palette;
import com.wormhole_xtreme.wormhole.utils.MaterialUtils;

/**
 * What a preview's cells need against what stands in the world, judged the way gate detection
 * judges a build.
 */
public final class BuildGuide
{
    /** How a blueprint cell compares with the block standing there. */
    public enum State
    {
        /** Something the gate accepts there. */
        PLACED,
        /** Nothing there yet. */
        MISSING,
        /** A block the gate does not accept there. */
        WRONG
    }

    /**
     * One material a gate needs.
     *
     * @param name
     *            what to gather, as a player would type it
     * @param count
     *            how many the gate takes
     * @param toPlace
     *            how many of those are not yet in place
     */
    public record Need(String name, int count, int toPlace) {}

    private BuildGuide() {}

    /**
     * @param cell
     *            a blueprint cell of the frame, DHD or dial sign
     * @param palette
     *            the materials the gate is built from
     * @param found
     *            what stands there, or null for nothing
     * @return how the cell compares
     */
    public static State of(final Cell cell, final Palette palette, final Material found)
    {
        if (isEmpty(found))
        {
            return State.MISSING;
        }
        return accepts(cell, palette, found) ? State.PLACED : State.WRONG;
    }

    /**
     * @param found
     *            what stands in an opening cell, or null for nothing
     * @return whether it is in the way of the wormhole
     */
    public static boolean blocksOpening(final Material found)
    {
        return !isEmpty(found);
    }

    /**
     * What a gate takes, material by material, in the order its cells first ask for each.
     *
     * @param cells
     *            the blueprint cells
     * @param palette
     *            the materials the gate is built from
     * @param drawn
     *            the materials it is drawn in, where a chevron may be shown as frame
     * @param found
     *            what stands at a cell, or null for nothing or a cell that cannot be read
     * @return each material with its count and how many are still to place
     */
    public static List<Need> needs(final List<Cell> cells, final Palette palette, final Palette drawn,
        final Function<Cell, Material> found)
    {
        final Map<String, int[]> counts = new LinkedHashMap<>();
        for (final Cell cell : cells)
        {
            final int[] count = counts.computeIfAbsent(nameOf(cell, palette, drawn), name -> new int[2]);
            count[0]++;
            if (of(cell, palette, found.apply(cell)) != State.PLACED)
            {
                count[1]++;
            }
        }
        final List<Need> out = new ArrayList<>();
        counts.forEach((name, count) -> out.add(new Need(name, count[0], count[1])));
        return out;
    }

    /** Detection's rule: a lit chevron may be frame or chevron material; a [C] cell only chevron. */
    private static boolean accepts(final Cell cell, final Palette palette, final Material found)
    {
        return switch (cell.part())
        {
            case FRAME -> (found == palette.structure())
                || ((cell.wave() > 0) && (palette.chevron() != null) && (found == palette.chevron()));
            case CHEVRON -> found == ((palette.chevron() != null) ? palette.chevron() : palette.structure());
            case BUTTON -> MaterialUtils.isButton(found) || (found == Material.LEVER);
            case DIAL_SIGN -> MaterialUtils.isWallSign(found);
            case PORTAL -> !blocksOpening(found);
        };
    }

    private static String nameOf(final Cell cell, final Palette palette, final Palette drawn)
    {
        return switch (cell.part())
        {
            case BUTTON -> "button or lever";
            // A wall sign is placed from the standing sign's item.
            case DIAL_SIGN -> word(drawn.materialOf(cell)).replace("_wall_", "_");
            // Shown in the chevron material, but frame material still counts there.
            case FRAME -> ((cell.wave() > 0) && (drawn.chevron() != null))
                ? word(drawn.chevron()) + " or " + word(palette.structure())
                : word(drawn.materialOf(cell));
            // A [C] cell takes the chevron material even while chevrons are drawn as frame.
            case CHEVRON, PORTAL -> word(palette.materialOf(cell));
        };
    }

    private static String word(final Material material)
    {
        return material.name().toLowerCase(Locale.ROOT);
    }

    private static boolean isEmpty(final Material found)
    {
        return (found == null) || MaterialUtils.isAirMaterial(found);
    }
}
