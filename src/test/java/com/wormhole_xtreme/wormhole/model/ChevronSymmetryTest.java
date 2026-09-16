package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * An upright ring's chevrons are arranged symmetrically.
 *
 * <p>Nothing in the shape format asks for this. A {@code :L#n} marker is legal on any frame
 * cell, so a ring whose chevrons are scattered parses, loads, registers and dials exactly like
 * one whose chevrons are placed with care. The only thing that ever noticed was somebody
 * standing in front of the gate.
 *
 * <p>{@code Grand} had been wrong since it was written. Its two upper diagonals are six-block
 * wedges cut into the bevel; its two lower ones were four-block dabs, two rows further round
 * the ring, in a position the upper pair has no counterpart for. Neither is wrong read on its
 * own, and in an editor the two halves of a 22-wide ring are eighteen rows apart -- far enough
 * that nobody comparing them ever had both on screen. Drawing the shape flattened it into one
 * picture, and the gate was plainly lopsided.
 *
 * <p>So this recomputes the arrangement from the shipped files rather than trusting the eye
 * that caught it once. A chevron moved onto a cell with no opposite number fails here, at the
 * point the grid is edited, rather than surviving to be spotted in a screenshot years later --
 * or not spotted at all.
 *
 * <p>Two shapes are deliberately not checked. {@code Minimal} is two blocks wide with a single
 * chevron, so there is no pair of axes to be symmetric about; {@code Horizontal} lies flat in
 * the floor, which makes its grid a plan -- rows are depth there, not height, and reflecting
 * them would be asking the shape a question about itself that does not mean anything.
 */
class ChevronSymmetryTest
{
    /** Where the shipped shapes live in the source tree. */
    private static final Path SHAPE_DIR = Paths.get("src/main/resources/shapes/gate");

    /**
     * The rings this applies to: upright, and with chevrons enough to be arranged at all.
     *
     * <p>Named rather than globbed. A new shape should have to be added here deliberately,
     * by somebody who has decided it is an upright ring, instead of being swept in and failing
     * a rule its author never meant it to be held to.
     */
    private static final List<String> UPRIGHT_RINGS =
        List.of("Standard", "Large", "Grand", "Massive");

    /** A bracketed cell, whatever markers it carries. */
    private static final Pattern CELL = Pattern.compile("\\[([^\\]]*)\\]");

    /** The light order on a cell, which is what makes it a chevron. */
    private static final Pattern LIGHT = Pattern.compile("(?:^|:)L#(\\d+)");

    /** One cell of the grid, by row from the top and column from the left. */
    private record Cell(int row, int col) { }

    /** A shape's front face: its chevrons by light order, and the size of the grid. */
    private record Face(Map<Integer, Set<Cell>> chevrons, int height, int width)
    {
        /** Every lit cell, whichever chevron it belongs to. */
        Set<Cell> lit()
        {
            final Set<Cell> all = new LinkedHashSet<>();
            chevrons().values().forEach(all::addAll);
            return all;
        }

        /** The chevron containing the highest lit cell -- the one at the top of the ring. */
        Set<Cell> topChevron()
        {
            return chevrons().values().stream()
                .min(Comparator.comparingInt(cells -> cells.stream()
                    .mapToInt(Cell::row).min().orElse(Integer.MAX_VALUE)))
                .orElseThrow();
        }
    }

    /**
     * Reads {@code Layer#1} of a shape, which for an upright ring is the face you walk up to.
     *
     * <p>Layer 1 alone on purpose. A thick ring repeats its chevrons on the back face as well,
     * and a shape whose two faces disagree is a different defect from a shape whose one face
     * is lopsided -- this is about the arrangement, so it reads the arrangement once.
     */
    private static Face face(final String name) throws IOException
    {
        final List<String> lines =
            Files.readAllLines(SHAPE_DIR.resolve(name + ".shape"), StandardCharsets.UTF_8);

        final Map<Integer, Set<Cell>> chevrons = new TreeMap<>();
        final List<String> grid = new ArrayList<>();
        boolean inLayerOne = false;
        for (final String line : lines)
        {
            if (line.trim().equals("Layer#1="))
            {
                inLayerOne = true;
                continue;
            }
            if (inLayerOne)
            {
                if (!line.startsWith("["))
                {
                    break;
                }
                grid.add(line);
            }
        }

        assertTrue(!grid.isEmpty(), name + " has no Layer#1 grid to read");

        int width = 0;
        for (int row = 0; row < grid.size(); row++)
        {
            final Matcher cells = CELL.matcher(grid.get(row));
            int col = 0;
            while (cells.find())
            {
                final Matcher light = LIGHT.matcher(cells.group(1));
                if (light.find())
                {
                    chevrons.computeIfAbsent(Integer.valueOf(light.group(1)), k -> new LinkedHashSet<>())
                        .add(new Cell(row, col));
                }
                col++;
            }
            width = Math.max(width, col);
        }
        return new Face(chevrons, grid.size(), width);
    }

    /** The cells with no opposite number, sorted so a failure reads in grid order. */
    private static List<Cell> unmatched(final Set<Cell> lit, final Set<Cell> reflected)
    {
        final List<Cell> missing = new ArrayList<>(lit);
        missing.removeAll(reflected);
        missing.sort(Comparator.comparingInt(Cell::row).thenComparingInt(Cell::col));
        return missing;
    }

    /**
     * Every upright ring's chevrons mirror left to right.
     *
     * <p>This one admits no exception. A ring is walked through from the front, and there is
     * no reason for a chevron on the left that is not equally a reason for one on the right.
     */
    @Test
    void everyUprightRingsChevronsMirrorLeftToRight() throws IOException
    {
        for (final String name : UPRIGHT_RINGS)
        {
            final Face face = face(name);
            final Set<Cell> lit = face.lit();
            final Set<Cell> reflected = new LinkedHashSet<>();
            lit.forEach(cell -> reflected.add(new Cell(cell.row(), face.width() - 1 - cell.col())));

            assertEquals(List.of(), unmatched(lit, reflected),
                name + ": these chevron cells have nothing opposite them across the ring's"
                    + " vertical axis, so the gate lights unevenly left to right");
        }
    }

    /**
     * Every upright ring's chevrons mirror top to bottom, apart from the chevron at the top.
     *
     * <p>The exception is real rather than a concession. {@code Standard} lights seven chevrons
     * -- one at the top, then a pair of diagonals, a pair on the sides and a pair of diagonals
     * below -- and the bottom of the ring is where a gate meets the ground, which is why there
     * is nothing at the foot to answer the one at the head. {@code Massive} does carry a bottom
     * chevron, and so mirrors outright; both arrangements pass, and anything else does not.
     *
     * <p>This is the assertion {@code Grand} failed. Its lower diagonals sat where the upper
     * pair has no counterpart, so eight cells at the top of the ring were left unanswered
     * rather than the two or three a bare top chevron accounts for.
     */
    @Test
    void everyUprightRingsChevronsMirrorTopToBottomApartFromTheTopChevron() throws IOException
    {
        for (final String name : UPRIGHT_RINGS)
        {
            final Face face = face(name);
            final Set<Cell> lit = face.lit();
            final Set<Cell> reflected = new LinkedHashSet<>();
            lit.forEach(cell -> reflected.add(new Cell(face.height() - 1 - cell.row(), cell.col())));

            final List<Cell> missing = unmatched(lit, reflected);
            if (missing.isEmpty())
            {
                continue;
            }

            assertEquals(new ArrayList<>(face.topChevron()).stream()
                    .sorted(Comparator.comparingInt(Cell::row).thenComparingInt(Cell::col))
                    .toList(),
                missing,
                name + ": a ring may leave its top chevron unanswered, because the foot of the"
                    + " ring is where the gate meets the ground -- but nothing else. These are"
                    + " the cells with no opposite number across the horizontal axis, and they"
                    + " are not that chevron");
        }
    }
}
