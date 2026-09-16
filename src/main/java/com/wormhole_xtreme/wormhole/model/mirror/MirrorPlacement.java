package com.wormhole_xtreme.wormhole.model.mirror;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;

import com.wormhole_xtreme.wormhole.config.ConfigManager;

/**
 * Where a mirror may hang, and which blocks keep it working once it does.
 *
 * <p>A mirror draws its world behind the wall it hangs on. The wall is what hides that world
 * from anywhere but the opening, so a mirror needs one: a banner on a post in the open showed
 * the far world past its edges however the view was trimmed. A block of solid wall on every
 * side of the opening is the rule, and the same blocks -- the face -- cannot be broken while
 * the mirror is there. Two blocks hides the room's edges better from an angle, so a wall short
 * of two is allowed and said.
 *
 * <p>A mirror is one banner wide or two. The second of two is to the right of the first, looking
 * at the wall, and the face is a block wider for it.
 */
public final class MirrorPlacement
{
    /** How many blocks of solid wall a mirror needs on every side of its opening. */
    static final int BORDER = 1;

    /** How many it is better with; short of this, {@code create} says so and makes it anyway. */
    static final int BETTER = 2;

    /** Static helpers only. */
    private MirrorPlacement()
    {
    }

    /**
     * How far apart two mirrors must be for either to be drawn whole: twice the view depth.
     *
     * <p>Nearer, each would fill the same space behind the wall with its own room, so both are
     * trimmed to what each eye sees through them.
     *
     * @return the distance between banners, in blocks
     */
    public static double apartToDrawWhole()
    {
        return 2.0 * ConfigManager.getMirrorViewDepth();
    }

    /**
     * The nearest other mirror in the same world too close for either to be drawn whole.
     *
     * @param mirror
     *            the mirror just made or moved
     * @return that mirror, or null if there is none
     */
    public static QuantumMirror tooNear(final QuantumMirror mirror)
    {
        final double apart = apartToDrawWhole();
        double nearest = apart * apart;
        QuantumMirror found = null;
        for (final QuantumMirror other : MirrorManager.all())
        {
            final double apartSquared = squaredApart(mirror, other);
            if (!other.name().equalsIgnoreCase(mirror.name())
                && other.banner().worldName().equals(mirror.banner().worldName()) && (apartSquared < nearest))
            {
                nearest = apartSquared;
                found = other;
            }
        }
        return found;
    }

    /**
     * The squared distance between two mirrors' banners.
     *
     * @param one
     *            a mirror
     * @param other
     *            another
     * @return the distance squared, in blocks
     */
    public static double squaredApartOf(final QuantumMirror one, final QuantumMirror other)
    {
        return squaredApart(one, other);
    }

    private static double squaredApart(final QuantumMirror one, final QuantumMirror other)
    {
        final double dx = (double) one.banner().x() - other.banner().x();
        final double dy = (double) one.banner().y() - other.banner().y();
        final double dz = (double) one.banner().z() - other.banner().z();
        return (dx * dx) + (dy * dy) + (dz * dz);
    }

    /**
     * Why a banner cannot become a mirror one banner wide called {@code name}, or null if it can.
     *
     * @param banner
     *            the banner being made a mirror
     * @param name
     *            the name it would have
     * @return what to tell whoever tried, or null
     */
    public static String refusal(final Block banner, final String name)
    {
        return refusal(banner, name, 1);
    }

    /**
     * Why a banner cannot become a mirror called {@code name}, or null if it can.
     *
     * @param banner
     *            the banner being made a mirror; for two, the left one looking at the wall
     * @param name
     *            the name it would have; a mirror already called this is the one moving here,
     *            and does not count against the limit
     * @param width
     *            one banner wide or two
     * @return what to tell whoever tried, or null
     */
    public static String refusal(final Block banner, final String name, final int width)
    {
        final BlockData data = banner.getBlockData();
        if (!(data instanceof Directional directional))
        {
            return "A mirror hangs on a wall. On a post in the open its world shows past its edges.";
        }
        final World world = banner.getWorld();
        final String full = overLimit(world.getName(), MirrorBlock.of(banner), name);
        if (full != null)
        {
            return full;
        }
        final MirrorWindow.Spot gap = gapIn(world, banner.getX(), banner.getY(), banner.getZ(),
            directional.getFacing(), width, BORDER);
        if (gap != null)
        {
            // A pair says how big its wall is: a wall built for one banner is a column short of two.
            final String needs = (width >= 2)
                ? "Two banners make a mirror two wide, which needs solid wall " + (width + (2 * BORDER))
                    + " across and " + (MirrorWindow.HEIGHT + (2 * BORDER)) + " tall; the block at "
                : "A mirror needs solid wall a block out on every side of its opening, and the block at ";
            return needs + gap.x() + " " + gap.y() + " " + gap.z() + " is not.";
        }
        return null;
    }

    /**
     * What to say about a wall that is solid a block out but not two, or null if it is two.
     *
     * <p>For after a mirror is made: a block of wall hides the room, and two hides its edges from
     * a sharper angle, so a wall short of two is worth a word and not a refusal.
     *
     * @param banner
     *            the banner just made a mirror; for two, the left one looking at the wall
     * @param width
     *            one banner wide or two
     * @return the word, or null
     */
    public static String thinWall(final Block banner, final int width)
    {
        if (!(banner.getBlockData() instanceof Directional directional))
        {
            return null;
        }
        final MirrorWindow.Spot gap = gapIn(banner.getWorld(), banner.getX(), banner.getY(), banner.getZ(),
            directional.getFacing(), width, BETTER);
        if (gap == null)
        {
            return null;
        }
        return "Its wall is solid a block out, which is enough; two blocks out hides the room's edges better"
            + " from an angle, and the block at " + gap.x() + " " + gap.y() + " " + gap.z() + " is not solid.";
    }

    /**
     * Why a world has no room for another mirror, or null if it has.
     *
     * @param worldName
     *            the world
     * @param banner
     *            the banner the new mirror would be; a mirror already there is being renamed
     * @param name
     *            the name it would have
     * @return what to say, or null
     */
    static String overLimit(final String worldName, final MirrorBlock banner, final String name)
    {
        final int limit = ConfigManager.getMirrorPerWorldLimit();
        if (limit <= 0)
        {
            return null;
        }
        int others = 0;
        for (final QuantumMirror mirror : MirrorManager.all())
        {
            if (mirror.banner().worldName().equals(worldName) && !mirror.banners().contains(banner)
                && !mirror.name().equalsIgnoreCase(name))
            {
                others++;
            }
        }
        if (others < limit)
        {
            return null;
        }
        return MirrorText.name(worldName) + " already has " + others
            + ((others == 1) ? " mirror" : " mirrors") + ", which is as many as "
            + MirrorText.name("mirror-per-world-limit") + " allows.";
    }

    /**
     * The first block of a one-banner face that is not solid, or null if the face is whole.
     *
     * @param world
     *            the banner's world
     * @param x
     *            the banner, x
     * @param y
     *            the banner, y
     * @param z
     *            the banner, z
     * @param facing
     *            which way the banner faces
     * @return the gap, or null
     */
    static MirrorWindow.Spot gapIn(final World world, final int x, final int y, final int z,
        final BlockFace facing)
    {
        return gapIn(world, x, y, z, facing, 1, BORDER);
    }

    /**
     * The first block of a wall banner's face that is not solid, or null if the face is whole.
     *
     * @param world
     *            the banner's world
     * @param x
     *            the left banner, x
     * @param y
     *            the banner, y
     * @param z
     *            the left banner, z
     * @param facing
     *            which way the banner faces
     * @param width
     *            one banner or two
     * @param border
     *            how far out the face reaches
     * @return the gap, or null
     */
    static MirrorWindow.Spot gapIn(final World world, final int x, final int y, final int z,
        final BlockFace facing, final int width, final int border)
    {
        for (final MirrorWindow.Spot spot : face(x, y, z, facing, width, border))
        {
            if (!world.getBlockAt(spot.x(), spot.y(), spot.z()).getBlockData().isOccluding())
            {
                return spot;
            }
        }
        return null;
    }

    /**
     * Every block of a one-banner face, the opening included.
     *
     * @param x
     *            the banner, x
     * @param y
     *            the banner, y
     * @param z
     *            the banner, z
     * @param facing
     *            which way the banner faces
     * @return the blocks
     */
    static Set<MirrorWindow.Spot> face(final int x, final int y, final int z, final BlockFace facing)
    {
        return face(x, y, z, facing, 1);
    }

    /**
     * Every block of a wall banner's face, the opening included.
     *
     * <p>The face is the wall the banner hangs on: the opening, one or two wide and two tall running
     * down from the banner, and {@link #BORDER} blocks round it.
     *
     * @param x
     *            the left banner, x
     * @param y
     *            the banner, y
     * @param z
     *            the left banner, z
     * @param facing
     *            which way the banner faces
     * @param width
     *            one banner or two
     * @return the blocks
     */
    static Set<MirrorWindow.Spot> face(final int x, final int y, final int z, final BlockFace facing,
        final int width)
    {
        return face(x, y, z, facing, width, BORDER);
    }

    /** The same, reaching {@code border} blocks out from the opening. */
    private static Set<MirrorWindow.Spot> face(final int x, final int y, final int z, final BlockFace facing,
        final int width, final int border)
    {
        final Set<MirrorWindow.Spot> face = new HashSet<>();
        final int wallX = x - facing.getModX();
        final int wallZ = z - facing.getModZ();
        // To the right, looking at the wall.
        final int rightX = facing.getModZ();
        final int rightZ = -facing.getModX();
        final int bottom = y - (MirrorWindow.HEIGHT - 1);
        for (int across = -border; across <= ((width - 1) + border); across++)
        {
            for (int at = bottom - border; at <= (y + border); at++)
            {
                face.add(new MirrorWindow.Spot(wallX + (across * rightX), at, wallZ + (across * rightZ)));
            }
        }
        return face;
    }

    /**
     * Every block in a world that keeps a mirror working: its banners, and its face.
     *
     * <p>Read from the banners as they stand, since which way one faces is recorded nowhere
     * else. A banner in an unloaded chunk protects nothing, and nothing near it can be broken.
     *
     * @param world
     *            the world
     * @return the blocks, as keys
     */
    public static Set<MirrorBlock> protectedIn(final World world)
    {
        final Set<MirrorBlock> kept = new HashSet<>();
        final String worldName = world.getName();
        for (final QuantumMirror mirror : MirrorManager.all())
        {
            final MirrorBlock at = mirror.banner();
            if (!at.worldName().equals(worldName) || !world.isChunkLoaded(at.x() >> 4, at.z() >> 4))
            {
                continue;
            }
            kept.addAll(mirror.banners());
            final BlockData data = world.getBlockAt(at.x(), at.y(), at.z()).getBlockData();
            if (data instanceof Directional directional)
            {
                for (final MirrorWindow.Spot spot : face(at.x(), at.y(), at.z(), directional.getFacing(),
                    mirror.width()))
                {
                    kept.add(new MirrorBlock(worldName, spot.x(), spot.y(), spot.z()));
                }
            }
        }
        return kept;
    }

    /**
     * Whether breaking this block would break a mirror.
     *
     * @param block
     *            the block
     * @return true if it is one of a mirror's banners or part of its face
     */
    public static boolean isProtected(final Block block)
    {
        return (block != null) && !MirrorManager.all().isEmpty()
            && protectedIn(block.getWorld()).contains(MirrorBlock.of(block));
    }
}
