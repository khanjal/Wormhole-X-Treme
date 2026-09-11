package com.wormhole_xtreme.wormhole.model.mirror;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.DyeColor;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * What is on the other side of a mirror, reduced to something a banner can show.
 *
 * <p>A banner is not a screen. There is no way to render a view onto one -- it is a dyed base
 * and at most six flat patterns, in sixteen colours. So this does not photograph the far side;
 * it looks at the blocks around the arrival point, works out which colours dominate, and
 * reports them. {@link MirrorStamp} lays those out as a few coarse squares, and the result
 * reads the way a glance through a doorway does: a library comes back brown, a lava field
 * orange, an ocean blue.
 *
 * <p>Three things come back, and they answer different questions:
 *
 * <ul>
 * <li>{@link #biome()} -- where this is, which picks the frame the squares sit in.</li>
 * <li>{@link #colours()} -- what is actually there, most common first.</li>
 * <li>{@link #enclosed()} -- whether it is indoors. Inside a building the biome says what the
 * roof happens to stand on, which is not what a player would see, so the blocks take over.</li>
 * </ul>
 *
 * <p>Sampled once, when the mirror is stamped, and never again. Re-reading the far side every
 * time somebody walks past would mean loading a distant chunk on a click; a banner that
 * changed on its own would also be worse to build with, because the look an operator chose
 * would not stay chosen.
 */
public record MirrorView(String biome, List<DyeColor> colours, boolean enclosed)
{
    /** Half-width of the sampled box, in blocks. */
    private static final int RADIUS = 6;

    /** How far below and above the arrival point to look. */
    private static final int BELOW = 2;

    /** Above eye height mostly samples sky, which tells you nothing about the place. */
    private static final int ABOVE = 4;

    /** Every second block. A quarter of the reads, and the answer does not change. */
    private static final int STEP = 2;

    /** Solid share of the sample above which somewhere counts as indoors. */
    private static final double ENCLOSED_SHARE = 0.55d;

    /** How many colours are worth reporting; the stamp has room for three squares. */
    private static final int WANTED = 3;

    /** @return the view with its colour list made unmodifiable */
    public MirrorView
    {
        colours = List.copyOf(colours);
    }

    /** @return the most common colour over there, or null if nothing was found */
    public DyeColor dominant()
    {
        return colours.isEmpty() ? null : colours.get(0);
    }

    /**
     * Looks at what surrounds a destination.
     *
     * <p>Returns null rather than an empty view when the world is not loaded. The difference
     * matters to the caller: "nothing is over there" is a stampable answer, "I could not look"
     * is not, and loading a world to read its colours is not worth doing.
     *
     * @param point
     *            where the mirror comes out
     * @return what a player standing there would see, or null if the world is not loaded
     */
    public static MirrorView look(final MirrorPoint point)
    {
        final Location at = (point == null) ? null : point.toLocation();
        if (at == null)
        {
            return null;
        }
        final World world = at.getWorld();
        final int ox = at.getBlockX();
        final int oy = at.getBlockY();
        final int oz = at.getBlockZ();

        final Tally tally = new Tally();
        for (int x = -RADIUS; x <= RADIUS; x += STEP)
        {
            for (int y = -BELOW; y <= ABOVE; y += STEP)
            {
                for (int z = -RADIUS; z <= RADIUS; z += STEP)
                {
                    tally.add(world.getBlockAt(ox + x, oy + y, oz + z));
                }
            }
        }
        return new MirrorView(biomeNameAt(world, ox, oy, oz), topColours(tally.counts),
            tally.enclosed());
    }

    /** What the sweep found: how much of it was solid, and what colours were in it. */
    private static final class Tally
    {
        /** How many of each colour were seen. */
        private final Map<DyeColor, Integer> counts = new EnumMap<>(DyeColor.class);

        /** How many blocks were looked at, air included. */
        private int sampled;

        /** How many of those were anything at all. */
        private int solid;

        /** Counts one block. */
        void add(final Block block)
        {
            sampled++;
            final String material = block.getType().name();
            if (isAir(material))
            {
                return;
            }
            solid++;
            final DyeColor colour = MirrorPalette.of(material);
            if (colour != null)
            {
                counts.merge(colour, 1, Integer::sum);
            }
        }

        /** @return true if enough of the sample was solid to call this a room */
        boolean enclosed()
        {
            return (sampled > 0) && (((double) solid / sampled) >= ENCLOSED_SHARE);
        }
    }

    /**
     * Whether a block is one of the three kinds of nothing.
     *
     * <p>By name, rather than {@code Material.isAir()}, and that is not style. From 1.20.6 on
     * {@code isAir()} is no longer a switch -- it goes through {@code asBlockType()} into the
     * live block registry, which is the same mechanism that made {@code Material.isBlock()}
     * throw when it was called too early in this plugin's startup. This runs a few hundred
     * times per stamp and needs no registry to answer.
     *
     * @param material
     *            the material's name
     * @return true if there is nothing there
     */
    private static boolean isAir(final String material)
    {
        return "AIR".equals(material) || "CAVE_AIR".equals(material)
            || "VOID_AIR".equals(material);
    }

    /** The most common colours, most common first, at most {@link #WANTED} of them. */
    private static List<DyeColor> topColours(final Map<DyeColor, Integer> counts)
    {
        final List<Map.Entry<DyeColor, Integer>> entries = new ArrayList<>(counts.entrySet());
        // Ties broken by name so two servers with the same build stamp the same banner;
        // EnumMap iteration order would do that too, but only by accident of declaration.
        entries.sort(Comparator.<Map.Entry<DyeColor, Integer>>comparingInt(Map.Entry::getValue)
            .reversed().thenComparing(entry -> entry.getKey().name()));

        final List<DyeColor> colours = new ArrayList<>();
        for (final Map.Entry<DyeColor, Integer> entry : entries.subList(0,
            Math.min(WANTED, entries.size())))
        {
            colours.add(entry.getKey());
        }
        return colours;
    }

    /**
     * The biome's name at a point, upper-cased, or an empty string if it cannot be read.
     *
     * <p>Deliberately roundabout. {@code Biome} is an enum through 1.21 and an interface from
     * 1.21.4, so a call compiled against the enum -- {@code name()}, {@code getKey()}, even
     * {@code toString()} -- is an invokevirtual that throws {@code IncompatibleClassChangeError}
     * on a newer server. Going through {@link Keyed}, which has been an interface the whole
     * time, is the one route that compiles here and still dispatches there. The key is the
     * lower-case of the old enum name, so upper-casing it gives back the name the preset files
     * are written in.
     */
    private static String biomeNameAt(final World world, final int x, final int y, final int z)
    {
        final Object biome = world.getBlockAt(x, y, z).getBiome();
        if (biome instanceof Keyed keyed)
        {
            return keyed.getKey().getKey().toUpperCase(Locale.ROOT);
        }
        return "";
    }
}
