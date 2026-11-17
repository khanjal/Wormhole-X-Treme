package com.wormhole_xtreme.wormhole.model.mirror;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import com.wormhole_xtreme.wormhole.model.window.Capture;
import com.wormhole_xtreme.wormhole.model.window.Place;
import com.wormhole_xtreme.wormhole.utils.MaterialUtils;

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
    public static MirrorView look(final Place point)
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

        // The world's own floor and ceiling. Sampling outside them is not merely wasteful:
        // some servers throw from getBlockAt on an out-of-range Y, and those that instead
        // answer "air" are worse, because that air counts in the sample and drags the solid
        // share down -- so a cave near bedrock would read as open sky rather than as indoors.
        final int floor = world.getMinHeight();
        final int ceiling = world.getMaxHeight() - 1;

        final Tally tally = new Tally();
        for (int x = -RADIUS; x <= RADIUS; x += STEP)
        {
            for (int dy = -BELOW; dy <= ABOVE; dy += STEP)
            {
                final int y = oy + dy;
                if ((y < floor) || (y > ceiling))
                {
                    continue;
                }
                for (int z = -RADIUS; z <= RADIUS; z += STEP)
                {
                    tally.add(world.getBlockAt(ox + x, y, oz + z).getType());
                }
            }
        }
        final int biomeY = Math.min(Math.max(oy, floor), ceiling);
        return new MirrorView(biomeNameAt(world, ox, biomeY, oz), topColours(tally.counts),
            tally.enclosed());
    }

    /**
     * The same look, read from a room's stored capture instead of the live world (#613).
     *
     * <p>The same box as {@link #look}, so a banner that follows a room reads like one stamped
     * there by hand. A block the capture kept but nobody at the opening could see counts as
     * solid with no colour: it is what makes a cellar a cellar, and its colour was never kept.
     * A block outside the capture's box is not counted at all.
     *
     * @param capture
     *            the room's capture
     * @param point
     *            the room's arrival point, in the capture's world
     * @param biome
     *            the biome there, or an empty string for the generic frame
     * @return what the capture shows around the point
     */
    public static MirrorView fromCapture(final Capture capture, final Place point, final String biome)
    {
        final int ox = (int) Math.floor(point.x());
        final int oy = (int) Math.floor(point.y());
        final int oz = (int) Math.floor(point.z());
        final Tally tally = new Tally();
        for (int x = -RADIUS; x <= RADIUS; x += STEP)
        {
            for (int dy = -BELOW; dy <= ABOVE; dy += STEP)
            {
                for (int z = -RADIUS; z <= RADIUS; z += STEP)
                {
                    tallyKept(tally, capture, ox + x, oy + dy, oz + z);
                }
            }
        }
        return new MirrorView((biome == null) ? "" : biome, topColours(tally.counts), tally.enclosed());
    }

    /** Counts one block of a capture, as {@link Tally#add} counts one of the world. */
    private static void tallyKept(final Tally tally, final Capture capture, final int x, final int y, final int z)
    {
        if (!capture.contains(x, y, z))
        {
            return;
        }
        if (capture.isBuried(x, y, z))
        {
            tally.addUnseen();
            return;
        }
        if (capture.isAir(x, y, z))
        {
            tally.add(Material.AIR);
            return;
        }
        final BlockData data = capture.at(x, y, z);
        tally.add((data == null) ? Material.AIR : data.getMaterial());
    }

    /**
     * The biome's name at a room, read only where it costs nothing.
     *
     * <p>A capture keeps no biome. Its chunk loaded, the biome is read there; not loaded, it is not
     * loaded for this, and the world's kind answers instead: the Nether's wastes, the End, or
     * nothing, which is the generic frame. Nothing at all for a world that is not loaded.
     *
     * @param point
     *            the room's arrival point
     * @return the biome's name upper-cased, or an empty string
     */
    public static String biomeOf(final Place point)
    {
        final World world = Bukkit.getWorld(point.worldName());
        if (world == null)
        {
            return "";
        }
        final int x = (int) Math.floor(point.x());
        final int z = (int) Math.floor(point.z());
        if (world.isChunkLoaded(x >> 4, z >> 4))
        {
            final int y = Math.min(Math.max((int) Math.floor(point.y()), world.getMinHeight()), world.getMaxHeight() - 1);
            return biomeNameAt(world, x, y, z);
        }
        final World.Environment kind = world.getEnvironment();
        if (kind == World.Environment.NETHER)
        {
            return "NETHER_WASTES";
        }
        return (kind == World.Environment.THE_END) ? "THE_END" : "";
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

        /**
         * Counts one block.
         *
         * <p>Air is found through {@link MaterialUtils#isAirMaterial}, which this plugin
         * already keeps for the reason that matters here: it compares the three constants
         * rather than calling {@code Material.isAir()}, which from 1.20.6 on goes through the
         * live block registry. This runs a few hundred times per stamp.
         */
        void add(final Material material)
        {
            sampled++;
            if (MaterialUtils.isAirMaterial(material))
            {
                return;
            }
            solid++;
            final DyeColor colour = MirrorPalette.of(material.name());
            if (colour != null)
            {
                counts.merge(colour, 1, Integer::sum);
            }
        }

        /** Counts a block that is there but whose colour is not known: solid, and no square. */
        void addUnseen()
        {
            sampled++;
            solid++;
        }

        /** @return true if enough of the sample was solid to call this a room */
        boolean enclosed()
        {
            return (sampled > 0) && (((double) solid / sampled) >= ENCLOSED_SHARE);
        }
    }

    /** The most common colours, most common first, at most {@link #WANTED} of them. */
    private static List<DyeColor> topColours(final Map<DyeColor, Integer> counts)
    {
        final List<Map.Entry<DyeColor, Integer>> entries = new ArrayList<>(counts.entrySet());
        // Ties broken by name so two servers with the same build stamp the same banner;
        // EnumMap iteration order would do that too, but only by accident of declaration.
        entries.sort(Comparator.<Map.Entry<DyeColor, Integer>>comparingInt(Map.Entry::getValue)
            .reversed().thenComparing(entry -> entry.getKey().name()));

        return entries.stream().limit(WANTED).map(Map.Entry::getKey).toList();
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
