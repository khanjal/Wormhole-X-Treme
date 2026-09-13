package com.wormhole_xtreme.wormhole.model.ring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/**
 * The ring diagrams in the documentation still show what the plugin actually does.
 *
 * <p>The gate and mirror galleries are drawn from resource files, so a fingerprint of the file
 * is enough to catch a drawing that has gone stale. Rings have no resource file — there are
 * exactly two patterns and they are hardcoded, which {@link RingPattern} argues for at length —
 * so {@code scripts/render_ring_sheets.py} transcribes the profiles and
 * {@link RingAnimator}'s constants rather than reading them.
 *
 * <p>Duplicated arithmetic is exactly what rots quietly, and it would rot in the direction
 * that matters least visibly: a deploy strip showing rings three half-steps apart when the
 * plugin moved to four still looks like a perfectly good diagram. Nothing about editing
 * {@code SPACING} would redraw it.
 *
 * <p>So each drawing carries a machine-readable line saying what it drew, and these tests
 * recompute that from {@code RingPattern} and {@code RingAnimator} themselves. This is not a
 * staleness check — it runs the real animator frame by frame and compares. A drawing that
 * disagrees with the plugin fails with the command that redraws it.
 */
class RingGalleryTest
{
    /** The drawings. */
    private static final Path IMAGES = Paths.get("docs/images/rings");

    /** The document they are in. */
    private static final Path DOCUMENT = Paths.get("docs/RINGS.md");

    /** How to put it right, said in the failure rather than left to be worked out. */
    private static final String REGENERATE =
        " -- re-run: python scripts/render_ring_sheets.py";

    /** The line a drawing carries, describing itself. */
    private static final Pattern CLAIM = Pattern.compile("<!-- ([a-z].*?) -->");

    /**
     * A floor ring at a round height, which is all the animator needs to be asked.
     *
     * <p>A floor ring's stack builds from its own plane, so its frames are the same wherever it
     * is. The drawings show a floor ring for the same reason.
     */
    private static Ring floorRing()
    {
        return new Ring(0, 64, 0, RingPattern.ODD, RingOrientation.FLOOR, Material.STONE_SLAB,
            Material.GLOWSTONE);
    }

    /** What one drawing says about itself. */
    private static String claim(final String file) throws IOException
    {
        final String svg = Files.readString(IMAGES.resolve(file), StandardCharsets.UTF_8);
        final Matcher m = CLAIM.matcher(svg);
        assertTrue(m.find(), file + " carries no line saying what it drew" + REGENERATE);
        return m.group(1);
    }

    /**
     * Offsets in the canonical order the renderer writes them in: by dx, then dz, as numbers.
     *
     * <p>As numbers, and this is not a detail. Sorting them as the strings they are printed as
     * puts {@code -1,-3} before {@code -2,-2}, because that is what comparing "1" to "2"
     * does — which is a perfectly stable order, just not the renderer's, and the first run of
     * this test failed on exactly that with two identical sets of cells.
     */
    private static String offsets(final List<RingPattern.Offset> cells)
    {
        return cells.stream()
            .sorted(Comparator.comparingInt(RingPattern.Offset::getDx)
                .thenComparingInt(RingPattern.Offset::getDz))
            .map(offset -> offset.getDx() + "," + offset.getDz())
            .collect(Collectors.joining(";"));
    }

    /**
     * Where each ring settles, in the order the animator counts them.
     *
     * <p>Two tests below need this and had the same loop in each of them, character for
     * character. It is also the order the flash runs in, which is not a coincidence: the lit
     * ring is its own index, so "where ring n stops" and "which ring lights nth" are the same
     * list read for two different reasons.
     */
    private static List<Integer> restingHalfSteps(final Ring ring)
    {
        final List<Integer> steps = new ArrayList<>();
        for (int index = 0; index < RingAnimator.RING_COUNT; index++)
        {
            steps.add(RingAnimator.restingHalfStep(ring, index));
        }
        return steps;
    }

    /** Half-steps as the renderer writes them into a drawing: comma-separated, in order. */
    private static String joined(final List<Integer> steps)
    {
        return steps.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    /** The half-steps a deploy frame puts rings at, low to high. */
    private static List<Integer> halfSteps(final Ring ring, final RingStyle style,
        final int frame)
    {
        final int base = ring.stackBase();
        final List<Integer> out = new ArrayList<>(new TreeSet<>(
            RingAnimator.deployFrame(ring, style, frame).stream()
                .map(p -> ((p.getY() - base) * 2) + (p.isTop() ? 1 : 0))
                .collect(Collectors.toList())));
        out.sort(null);
        return out;
    }

    /**
     * The pattern drawings are the patterns the plugin builds, cell for cell.
     *
     * <p>Not "roughly the right shape". The split between perimeter and interior is what the
     * whole subsystem is built on — the perimeter is what somebody lays in slabs, the interior
     * is what arms a cycle and what travels — and a drawing that put one cell on the wrong side
     * of that line would have somebody lay a slab where they should be able to stand.
     */
    @Test
    void thePatternDrawingsAreTheRealPatterns() throws IOException
    {
        for (final RingPattern pattern : RingPattern.values())
        {
            final String file = "pattern-" + pattern.name().toLowerCase(Locale.ROOT)
                + ".svg";
            assertEquals(
                "pattern " + pattern.name()
                    + " perimeter=" + offsets(pattern.getPerimeter())
                    + " interior=" + offsets(pattern.getInterior()),
                claim(file),
                "the " + pattern.name() + " drawing shows a different footprint from the one"
                    + " the plugin lays" + REGENERATE);
        }
    }

    /**
     * The stack drawing is built on the animator's own numbers.
     *
     * <p>{@code SPACING} and {@code BASE_HALF_STEP} are the two that would change the picture
     * without changing anything a reader could name: rings a different distance apart, and a
     * stack that sits on the floor rather than hanging clear of it. Both are the kind of thing
     * somebody tunes once and never thinks about the documentation for.
     */
    @Test
    void theStackDrawingUsesTheAnimatorsNumbers() throws IOException
    {
        final Ring ring = floorRing();

        assertEquals(
            "stack SPACING=" + RingAnimator.SPACING
                + " TRAVEL_GAP=" + RingAnimator.TRAVEL_GAP
                + " RING_COUNT=" + RingAnimator.RING_COUNT
                + " BASE_HALF_STEP=" + RingAnimator.BASE_HALF_STEP
                + " TOP_HALF_STEP=" + RingAnimator.TOP_HALF_STEP
                + " STACK_HEIGHT=" + RingAnimator.STACK_HEIGHT
                + " resting=" + joined(restingHalfSteps(ring)),
            claim("stack.svg"),
            "the stack drawing no longer matches the stack the animator builds" + REGENERATE);
    }

    /**
     * Both deploy strips are the frames the animator actually produces.
     *
     * <p>This is the one worth having. The strips claim a specific thing about each style —
     * that concurrent rings overlap and arrive together, that sequential ones queue — and that
     * claim is the whole reason the pictures are on the page. Checking it against the real
     * animator means the difference between the two strips is the difference between the two
     * styles, rather than what somebody believed it was on the day they drew it.
     */
    @Test
    void theDeployStripsAreTheFramesTheAnimatorProduces() throws IOException
    {
        final Ring ring = floorRing();
        for (final RingStyle style : RingStyle.values())
        {
            final List<String> frames = new ArrayList<>();
            for (int frame = 0; frame < RingAnimator.deployFrames(ring, style); frame++)
            {
                frames.add(halfSteps(ring, style, frame).stream().map(String::valueOf)
                    .collect(Collectors.joining(",")));
            }

            assertEquals("deploy " + style.name() + " " + String.join("|", frames),
                claim("deploy-" + style.name().toLowerCase(Locale.ROOT) + ".svg"),
                "the " + style.name() + " strip shows a deploy the animator does not run"
                    + REGENERATE);
        }
    }

    /**
     * The flash runs through the stack in the order the rings are stacked in.
     *
     * <p>The lit ring is its own index, which is what lets the sweep need no sense of
     * direction: ring zero is the first one out and travels furthest from its pad, so counting
     * up from it runs towards the pad at either orientation. A filmstrip that showed the
     * sequence the other way round would be arguing against the design note directly above it.
     */
    @Test
    void theFlashDrawingRunsTowardsThePad() throws IOException
    {
        final Ring ring = floorRing();
        final List<Integer> order = restingHalfSteps(ring);

        assertEquals("flash order=" + joined(order), claim("flash.svg"),
            "the flash filmstrip lights the rings in a different order from the cycle"
                + REGENERATE);

        for (int index = 1; index < order.size(); index++)
        {
            assertTrue(order.get(index) < order.get(index - 1),
                "on a floor ring the sweep should descend towards the pad; ring " + index
                    + " is not below ring " + (index - 1));
        }
    }

    /**
     * The document shows all of it, between the markers the renderer writes into.
     *
     * <p>Six drawings exist and are checked above; a drawing nothing references is a file
     * nobody sees, which is the quiet way a gallery half-disappears.
     */
    @Test
    void theDocumentShowsEveryDrawing() throws IOException
    {
        final String document = Files.readString(DOCUMENT, StandardCharsets.UTF_8);
        for (final String marker : List.of("patterns", "stack", "flash"))
        {
            final int start = document.indexOf("<!-- " + marker + ":start -->");
            final int end = document.indexOf("<!-- " + marker + ":end -->");
            assertTrue((start > 0) && (end > start), "the " + marker + " markers should still"
                + " be in " + DOCUMENT.getFileName() + "; the renderer writes between them");
        }

        final List<String> missing = new ArrayList<>();
        for (final String drawing : List.of("pattern-odd", "pattern-even", "stack",
            "deploy-concurrent", "deploy-sequential", "flash"))
        {
            if (!document.contains("images/rings/" + drawing + ".svg"))
            {
                missing.add(drawing);
            }
        }
        assertEquals(List.of(), missing,
            "these drawings exist but nothing on the page shows them" + REGENERATE);
    }
}
