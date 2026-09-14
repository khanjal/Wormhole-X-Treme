package com.wormhole_xtreme.wormhole.model.mirror;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.config.ConfigManager;

/**
 * Which mirror each mirror opens onto, and how somebody standing at one changes it.
 *
 * <p>Every mirror is on the network. A mirror stores its own room -- the point in front of its
 * banner, level with the bottom of its opening, facing out -- and on its own it shows that room
 * as a reflection. Right-clicking it moves it on to the next mirror, and it shows that mirror's
 * room instead; punching it goes there. When nobody is near it any more it goes back to itself.
 *
 * <p>The choice is kept in memory only. A restart, like walking away, leaves every mirror
 * reflecting.
 */
public final class MirrorNetwork
{
    /** How long a choice stays up while somebody else is at the mirror, before it may change. */
    static final long HOLD_MILLIS = 3000L;

    /** The least time between two changes: one press, not the same press arriving twice. */
    static final long REPEAT_MILLIS = 250L;

    /** The mirror each mirror opens onto, by lower-cased name; absent means itself. */
    private static final Map<String, String> CHOSEN = new HashMap<>();

    /** When each mirror's choice last changed, by lower-cased name. */
    private static final Map<String, Long> CHOSEN_AT = new HashMap<>();

    /** The time, so a test can move it. */
    static LongSupplier clock = System::currentTimeMillis;

    /** Static state only. */
    private MirrorNetwork()
    {
    }

    /** Forgets every choice, for a test or a reload. */
    public static void clear()
    {
        CHOSEN.clear();
        CHOSEN_AT.clear();
        clock = System::currentTimeMillis;
    }

    /**
     * A wall banner's room: where it is seen from and where somebody stepping out of it lands.
     *
     * @param banner
     *            the banner block
     * @return in front of the wall, in the banner's own column, level with the bottom of the
     *         opening and facing out; or null if the block is not a wall banner
     */
    public static MirrorPoint roomOf(final Block banner)
    {
        return roomOf(banner, 1);
    }

    /**
     * The room of a mirror one banner wide or two.
     *
     * <p>For two, between the banners, so a traveller lands in the middle of the pair -- but still
     * inside the left banner's column, which is the column the room's view is measured from.
     *
     * @param banner
     *            the banner block; for two, the left one looking at the wall
     * @param width
     *            one banner or two
     * @return the room, or null if the block is not a wall banner
     */
    public static MirrorPoint roomOf(final Block banner, final int width)
    {
        if ((banner == null) || !(banner.getBlockData() instanceof Directional directional))
        {
            return null;
        }
        final org.bukkit.block.BlockFace facing = directional.getFacing();
        // Right, looking at the wall; a hair short of the boundary, so the column stays the left one.
        final double shift = (width >= 2) ? 0.49 : 0.0;
        return new MirrorPoint(banner.getWorld().getName(), banner.getX() + 0.5 + (shift * facing.getModZ()),
            banner.getY() - (MirrorWindow.HEIGHT - 1), banner.getZ() + 0.5 - (shift * facing.getModX()),
            MirrorArrival.yawOf(facing), 0.0f);
    }

    /**
     * Whether a mirror's destination is its own room, which is what makes it a reflection.
     *
     * <p>A mirror saved before the network pointed somewhere else, and keeps doing so until it is
     * created again.
     *
     * @param mirror
     *            the mirror
     * @return true if it stores its own room
     */
    public static boolean isOwnRoom(final QuantumMirror mirror)
    {
        final MirrorPoint room = mirror.destination();
        final MirrorBlock banner = mirror.banner();
        return (room != null) && room.worldName().equals(banner.worldName())
            && ((int) Math.floor(room.x()) == banner.x()) && ((int) Math.floor(room.z()) == banner.z())
            && ((int) Math.floor(room.y()) == (banner.y() - (MirrorWindow.HEIGHT - 1)));
    }

    /**
     * The mirror this one opens onto.
     *
     * @param mirror
     *            the mirror
     * @return the one chosen, or itself if none is, or the one chosen has gone
     */
    public static QuantumMirror chosen(final QuantumMirror mirror)
    {
        // Nothing chosen is off: a mirror nobody has turned on shows its own room.
        final String name = CHOSEN.get(key(mirror.name()));
        if ((name == null) || name.equalsIgnoreCase(mirror.name()))
        {
            return mirror;
        }
        final QuantumMirror other = MirrorManager.byName(name);
        return ((other == null) || (other.destination() == null)) ? mirror : other;
    }

    /**
     * Whether a mirror is showing its own room.
     *
     * @param mirror
     *            the mirror
     * @return true if nothing else is chosen and it stores its own room
     */
    public static boolean reflects(final QuantumMirror mirror)
    {
        return chosen(mirror).name().equalsIgnoreCase(mirror.name()) && isOwnRoom(mirror);
    }

    /**
     * Every mirror a right-click walks through, in order.
     *
     * @param mirror
     *            the mirror being clicked
     * @return itself first, then every other mirror with somewhere to go, by name
     */
    static List<QuantumMirror> order(final QuantumMirror mirror)
    {
        final List<QuantumMirror> order = new ArrayList<>();
        for (final QuantumMirror other : MirrorManager.all())
        {
            if (!other.name().equalsIgnoreCase(mirror.name()) && (other.destination() != null))
            {
                order.add(other);
            }
        }
        order.sort(Comparator.comparing(other -> other.name().toLowerCase(Locale.ROOT)));
        // Its start first, so the first right-click opens onto it.
        final String start = mirror.start();
        for (int i = 0; (start != null) && (i < order.size()); i++)
        {
            if (order.get(i).name().equalsIgnoreCase(start))
            {
                order.add(0, order.remove(i));
                break;
            }
        }
        order.add(0, mirror);
        return order;
    }

    /**
     * Moves a mirror on to the next one, if it may move.
     *
     * @param mirror
     *            the mirror right-clicked
     * @param othersNear
     *            whether anybody besides whoever clicked is at it
     * @return what to tell whoever clicked, or null for a repeat of the same press
     */
    public static String scroll(final QuantumMirror mirror, final boolean othersNear)
    {
        final List<QuantumMirror> order = order(mirror);
        if (order.size() < 2)
        {
            return "No other mirrors found.";
        }
        final long now = clock.getAsLong();
        final Long changed = CHOSEN_AT.get(key(mirror.name()));
        if (changed != null)
        {
            if ((now - changed) < REPEAT_MILLIS)
            {
                return null;
            }
            if (othersNear && ((now - changed) < HOLD_MILLIS))
            {
                return "Somebody else is at this mirror. Give them a moment before changing it.";
            }
        }
        final String current = chosen(mirror).name();
        int at = 0;
        for (int i = 0; i < order.size(); i++)
        {
            if (order.get(i).name().equalsIgnoreCase(current))
            {
                at = i;
            }
        }
        final int next = (at + 1) % order.size();
        CHOSEN_AT.put(key(mirror.name()), now);
        if (next == 0)
        {
            // Round to its own room: off again, as it is when everybody leaves.
            CHOSEN.remove(key(mirror.name()));
            return MirrorText.quoted(mirror.name()) + " shows its own room again.";
        }
        CHOSEN.put(key(mirror.name()), order.get(next).name());
        return MirrorText.quoted(mirror.name()) + " opens onto " + MirrorText.quoted(order.get(next).name())
            + " (" + next + " of " + (order.size() - 1) + ").";
    }

    /**
     * Lets a mirror nobody is at go back to its own room.
     *
     * @param mirror
     *            the mirror
     * @param anybodyNear
     *            whether anybody is within reach of it
     */
    public static void settle(final QuantumMirror mirror, final boolean anybodyNear)
    {
        if (!anybodyNear)
        {
            CHOSEN.remove(key(mirror.name()));
            CHOSEN_AT.remove(key(mirror.name()));
        }
    }

    /**
     * Whether anybody, other than one player, is within the proximity radius of a banner.
     *
     * @param world
     *            the banner's world
     * @param banner
     *            the banner
     * @param except
     *            a player not to count, or null
     * @return true if somebody is
     */
    public static boolean anybodyNear(final World world, final MirrorBlock banner, final Player except)
    {
        final double reach = ConfigManager.getMirrorProximityRadius();
        for (final Player player : world.getPlayers())
        {
            if (player.equals(except))
            {
                continue;
            }
            final Location at = player.getLocation();
            if (at == null)
            {
                continue;
            }
            final double dx = at.getX() - (banner.x() + 0.5);
            final double dy = at.getY() - banner.y();
            final double dz = at.getZ() - (banner.z() + 0.5);
            if (((dx * dx) + (dy * dy) + (dz * dz)) <= (reach * reach))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Forgets a mirror's choice, for one being removed.
     *
     * @param name
     *            the mirror
     */
    public static void forget(final String name)
    {
        CHOSEN.remove(key(name));
        CHOSEN_AT.remove(key(name));
    }

    private static String key(final String name)
    {
        return name.toLowerCase(Locale.ROOT);
    }
}
