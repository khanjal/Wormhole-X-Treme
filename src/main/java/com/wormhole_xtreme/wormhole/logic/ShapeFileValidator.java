package com.wormhole_xtreme.wormhole.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.wormhole_xtreme.wormhole.model.Stargate3DShape;
import com.wormhole_xtreme.wormhole.model.StargateShape;
import com.wormhole_xtreme.wormhole.model.StargateShapeLayer;

/**
 * Checks a {@code .shape} file for the problems that {@link StargateShapeFactory} does not
 * itself catch, so a shape author gets a full list of what is wrong rather than a stack trace
 * for the first thing the parser happened to trip over -- or, worse for the row-width and
 * layer-gap checks below, nothing at all.
 *
 * <p>{@link Stargate3DShape} derives one width and height from {@code Layer#1} and trusts
 * every later row and layer number to match it. A row one cell short of that width does not
 * throw; it just shifts every column after the gap. A skipped {@code Layer#N=} does not throw
 * either; it leaves a {@code null} in the middle of the layer array, a silent dead gap in the
 * woosh recession. Both mistakes shipped in this project's own gate shapes before being caught
 * by hand -- this exists so the next one is caught by running a command instead.
 *
 * <p>Pure with respect to Bukkit except for the material lookups in
 * {@link Stargate3DShape#parseMaterialName}, which need no live server to call -- everything
 * else here operates on plain file lines and the already-parsed shape object, the same split
 * this project's other shape-parsing tests already rely on.
 */
public final class ShapeFileValidator
{
    private static final Pattern CELL = Pattern.compile("\\[(.+?)\\]");
    private static final Pattern LAYER_HEADER = Pattern.compile("Layer#(\\d+)=");
    private static final Pattern MATERIAL_LINE = Pattern.compile(
        "^(PORTAL_MATERIAL|IRIS_MATERIAL|STARGATE_MATERIAL|ACTIVE_MATERIAL)=(.+)$");

    private ShapeFileValidator() {}

    /** The outcome of validating one shape file. */
    public static final class Result
    {
        private final boolean parsed;
        private final String shapeName;
        private final List<String> problems;

        private Result(final boolean parsed, final String shapeName, final List<String> problems)
        {
            this.parsed = parsed;
            this.shapeName = shapeName;
            this.problems = problems;
        }

        /** @return true if the file parsed and every check below found nothing wrong */
        public boolean isValid()
        {
            return parsed && problems.isEmpty();
        }

        /** @return true if {@link StargateShapeFactory#createShapeFromFile} did not throw */
        public boolean isParsed()
        {
            return parsed;
        }

        /** @return the shape's declared name, or null if it never parsed far enough to have one */
        public String getShapeName()
        {
            return shapeName;
        }

        /** @return every problem found, empty if none; never null */
        public List<String> getProblems()
        {
            return problems;
        }

        /**
         * A result for when the file itself could not even be read -- missing, unreadable,
         * whatever the reason -- so a caller with no lines to hand {@link #validate} still has
         * one {@link Result} type to report through.
         *
         * @param reason what went wrong, already worded for display
         * @return an invalid, unparsed result carrying just that one problem
         */
        public static Result unreadable(final String reason)
        {
            return new Result(false, null, java.util.Collections.singletonList(reason));
        }
    }

    /**
     * Validates one shape file's lines.
     *
     * @param fileLines
     *            the file, one line per element, the same input {@link StargateShapeFactory}
     *            itself takes
     * @return what was wrong, if anything
     */
    public static Result validate(final String[] fileLines)
    {
        final List<String> problems = new ArrayList<String>();
        problems.addAll(checkRowWidths(fileLines));
        problems.addAll(checkMaterialsResolve(fileLines));
        problems.addAll(checkSingletonMarkerCounts(fileLines));

        final StargateShape shape;
        try
        {
            shape = StargateShapeFactory.createShapeFromFile(fileLines);
        }
        catch (final RuntimeException e)
        {
            problems.add(0, "Failed to parse: " + e.getMessage());
            return new Result(false, null, problems);
        }

        // The 3D constructor already refuses to parse without an :EP; the 2D one does not,
        // so this only ever fires for that path -- but it costs nothing to check either way.
        if (shape.getShapeEnterPosition().length != 3)
        {
            problems.add("no :EP block -- there is nowhere for a player to arrive");
        }

        if (shape instanceof Stargate3DShape)
        {
            final Stargate3DShape shape3d = (Stargate3DShape) shape;
            problems.addAll(checkLayerGaps(shape3d));
            problems.addAll(checkOrderSequencing(shape3d));
            problems.addAll(checkRedstonePlacement(shape3d));
        }

        return new Result(true, shape.getShapeName(), problems);
    }

    /**
     * Every row within a {@code Layer#N=} block has to have as many {@code [cell]}s as the
     * width {@code Layer#1} established -- the same lazy-bracket count
     * {@link com.wormhole_xtreme.wormhole.model.Stargate3DShape}'s own parser uses, so a
     * malformed cell (a stray {@code [} merging two cells into one match) is judged the exact
     * way the real parser would see it, not by a stricter reading that would flag things the
     * game itself lets through.
     */
    private static List<String> checkRowWidths(final String[] fileLines)
    {
        final List<String> problems = new ArrayList<String>();
        Integer width = null;
        String currentLayer = null;
        int rowInLayer = 0;

        for (final String rawLine : fileLines)
        {
            final String line = rawLine.trim();
            if (line.startsWith("#"))
            {
                continue;
            }
            final Matcher headerMatch = LAYER_HEADER.matcher(line);
            if (headerMatch.matches())
            {
                currentLayer = "Layer#" + headerMatch.group(1);
                rowInLayer = 0;
                continue;
            }
            if (!line.startsWith("["))
            {
                continue;
            }
            final Matcher m = CELL.matcher(line);
            int count = 0;
            while (m.find())
            {
                count++;
            }
            if (width == null)
            {
                width = count;
            }
            else if (count != width)
            {
                problems.add((currentLayer == null ? "the ring shape" : currentLayer) + " row "
                    + rowInLayer + " has " + count + " cells, not " + width
                    + " -- a block was likely dropped or added while editing, and every "
                    + "column after the gap is shifted for the rest of that row");
            }
            rowInLayer++;
        }
        return problems;
    }

    /** No {@code Layer#N=} between 1 and the highest one declared may be missing. */
    private static List<String> checkLayerGaps(final Stargate3DShape shape)
    {
        final List<String> problems = new ArrayList<String>();
        for (int i = 1; i < shape.getShapeLayers().size(); i++)
        {
            if (shape.getShapeLayers().get(i) == null)
            {
                problems.add("Layer#" + i + " is missing -- a layer number was skipped, "
                    + "leaving a silent dead gap in the woosh recession at that depth");
            }
        }
        return problems;
    }

    /**
     * {@code :EP}, {@code :EM}, {@code :A}, {@code :IA}, {@code :D} and {@code :N} are each
     * documented as "1 per gate," but nothing enforces it -- a second one silently overwrites
     * the first with no error either way, including two on the very same layer (a single field
     * on {@code StargateShapeLayer}, last write wins). That last case is invisible to anything
     * reading the already-parsed model, since only the field's final value survives -- this
     * has to count raw occurrences in the text instead, the same reason {@link #checkRowWidths}
     * does.
     */
    private static List<String> checkSingletonMarkerCounts(final String[] fileLines)
    {
        final java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<String, Integer>();
        for (final String tag : new String[] { "EP", "EM", "A", "IA", "D", "N" })
        {
            counts.put(tag, 0);
        }

        for (final String rawLine : fileLines)
        {
            final String line = rawLine.trim();
            if (line.startsWith("#") || !line.startsWith("["))
            {
                continue;
            }
            final Matcher m = CELL.matcher(line);
            while (m.find())
            {
                for (final String token : m.group(1).split(":"))
                {
                    if (counts.containsKey(token))
                    {
                        counts.put(token, counts.get(token) + 1);
                    }
                }
            }
        }

        final List<String> problems = new ArrayList<String>();
        for (final java.util.Map.Entry<String, Integer> entry : counts.entrySet())
        {
            if (entry.getValue() > 1)
            {
                problems.add("found " + entry.getValue() + " :" + entry.getKey()
                    + " blocks, only 1 per gate is supported -- all but the last one parsed "
                    + "are silently ignored rather than rejected");
            }
        }
        if (counts.get("A") == 0)
        {
            problems.add("no :A block -- there is nowhere for the activation switch to attach, "
                + "so a gate built from this shape could never be dialed");
        }
        return problems;
    }

    /** {@code :L#} and {@code :W#} orders each have to run 1..N with no gap. */
    private static List<String> checkOrderSequencing(final Stargate3DShape shape)
    {
        final List<String> problems = new ArrayList<String>();
        final Set<Integer> lightOrders = new TreeSet<Integer>();
        final Set<Integer> wooshOrders = new TreeSet<Integer>();
        for (final StargateShapeLayer layer : shape.getShapeLayers())
        {
            if (layer == null)
            {
                continue;
            }
            collectPresentOrders(layer.getLayerLightPositions(), lightOrders);
            collectPresentOrders(layer.getLayerWooshPositions(), wooshOrders);
        }
        checkNoGap(problems, ":L", lightOrders);
        checkNoGap(problems, ":W", wooshOrders);
        return problems;
    }

    private static void collectPresentOrders(final List<ArrayList<Integer[]>> positions, final Set<Integer> into)
    {
        for (int order = 0; order < positions.size(); order++)
        {
            if (positions.get(order) != null)
            {
                into.add(order);
            }
        }
    }

    /**
     * A gap here is not fatal -- both {@code StargateAnimator.lightStargate} and
     * {@code animateOpening}'s woosh loop step through orders by plain index and just draw
     * nothing for one that has no blocks, then carry on to the next -- but it is still worth
     * flagging: a skipped order is a wasted, visibly empty tick in what should be a smooth
     * sequence, not a real second phase of anything.
     */
    private static void checkNoGap(final List<String> problems, final String label, final Set<Integer> orders)
    {
        if (orders.isEmpty())
        {
            return;
        }
        int expected = orders.iterator().next();
        for (final int order : orders)
        {
            if (order != expected)
            {
                final String missing = (order - 1 == expected)
                    ? ("#" + expected)
                    : ("#" + expected + " through #" + (order - 1));
                problems.add(label + " " + missing + " " + ((order - 1 == expected) ? "is" : "are")
                    + " never used, between #" + (expected - 1) + " and #" + order
                    + " -- that tick draws nothing rather than the sequence skipping cleanly to #" + order);
                expected = order;
            }
            expected++;
        }
    }

    /**
     * Every {@code [RD]}/{@code [RS]}/{@code [RA]} marker has to resolve to a cell nothing is
     * built in -- land it on a frame block and the redstone there can never fire, silently.
     * Mirrors {@code RedstoneBlockPlacementTest}'s check on the shipped shapes, run here for
     * one shape on demand instead of the whole directory at test time.
     */
    private static List<String> checkRedstonePlacement(final Stargate3DShape shape)
    {
        final List<String> problems = new ArrayList<String>();
        boolean hasDialMarker = false;
        boolean hasDialSign = false;

        for (final StargateShapeLayer layer : shape.getShapeLayers())
        {
            if (layer == null)
            {
                continue;
            }
            hasDialMarker |= layer.getLayerRedstoneDialActivationPosition().length >= 3;
            hasDialSign |= layer.getLayerDialSignPosition().length >= 3;

            final int[][] markers = {
                layer.getLayerRedstoneDialActivationPosition(),
                layer.getLayerRedstoneSignActivationPosition(),
                layer.getLayerRedstoneGateActivatedPosition(),
            };
            final String[] labels = { "[RD]", "[RS]", "[RA]" };
            for (int i = 0; i < markers.length; i++)
            {
                if (markers[i].length < 3)
                {
                    continue;
                }
                final int resolvedY = StargateHelper.redstoneComponentY(layer, markers[i], markers[i][1]);
                if (isFrameAt(layer, resolvedY, markers[i][2]))
                {
                    problems.add(labels[i] + " resolves to a cell the frame already occupies, "
                        + "so redstone placed there can never fire");
                }
            }
        }

        if (hasDialMarker && !hasDialSign)
        {
            problems.add("[RD] is defined but there is no :D block for it to dial");
        }
        return problems;
    }

    private static boolean isFrameAt(final StargateShapeLayer layer, final int y, final int col)
    {
        for (final Integer[] p : layer.getLayerBlockPositions())
        {
            if ((p[1].intValue() == y) && (p[2].intValue() == col))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code PORTAL_MATERIAL}, {@code IRIS_MATERIAL}, {@code STARGATE_MATERIAL} and
     * {@code ACTIVE_MATERIAL} are plain text resolved at runtime -- a name that does not exist
     * in this server's Minecraft version compiles fine and then either falls back silently or
     * fails when the gate is actually built.
     *
     * <p>Resolved through {@link Stargate3DShape#parseMaterialName}, the same method the real
     * parser uses, rather than {@link Material#matchMaterial} directly: the parser accepts the
     * legacy {@code STATIONARY_WATER}/{@code STATIONARY_LAVA} aliases pre-1.13 shape files
     * still use, and a stricter check here would reject a shape that loads and runs fine.
     */
    private static List<String> checkMaterialsResolve(final String[] fileLines)
    {
        final List<String> problems = new ArrayList<String>();
        for (final String rawLine : fileLines)
        {
            final String line = rawLine.trim();
            if (line.startsWith("#"))
            {
                continue;
            }
            final Matcher m = MATERIAL_LINE.matcher(line);
            if (!m.matches())
            {
                continue;
            }
            final String key = m.group(1);
            final String value = m.group(2).trim();
            if (Stargate3DShape.parseMaterialName(value) == null)
            {
                problems.add(key + "=" + value + " does not name a material that exists "
                    + "in this server's Minecraft version");
            }
        }
        return problems;
    }
}
