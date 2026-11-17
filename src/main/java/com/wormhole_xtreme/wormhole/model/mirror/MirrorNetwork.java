package com.wormhole_xtreme.wormhole.model.mirror;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.window.BlockPlace;
import com.wormhole_xtreme.wormhole.model.window.Place;
import com.wormhole_xtreme.wormhole.model.window.WindowShape;
import com.wormhole_xtreme.wormhole.utils.Messages;

/**
 * Which mirror each mirror opens onto, and how somebody standing at one changes it.
 *
 * <p>Every mirror is on the network. A mirror stores its own room -- the point in front of its
 * banner, level with the bottom of its opening, facing out -- and on its own it shows that room
 * as a reflection. Right-clicking it moves it on to the next of the other mirrors, round and
 * round, and it shows that mirror's room instead; punching it goes there. Its own room is never
 * in that round: it is what the mirror shows when nobody has turned it on, and what it goes back
 * to when nobody is near it any more.
 *
 * <p>"Every other mirror" is the ones {@link #listed} for whoever clicks: the same named group, or
 * with no group the public network; never a hidden one; a private one only for a holder of
 * {@link #SEE_PRIVATE}. A mirror's start is always first, whatever its group or flags (#280).
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

    /** The node that lists a private mirror for whoever holds it; operators by default (#280). */
    public static final String SEE_PRIVATE = "wormhole.mirror.private";

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
     * @return in front of the wall, in the banner's own column, level with the bottom of the
     *         opening and facing out; or null if the block is not a wall banner
     */
    public static Place roomOf(final Block banner)
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
    public static Place roomOf(final Block banner, final int width)
    {
        if ((banner == null) || !(banner.getBlockData() instanceof Directional directional))
        {
            return null;
        }
        final BlockFace facing = directional.getFacing();
        // Right, looking at the wall; a hair short of the boundary, so the column stays the left one.
        final double shift = (width >= 2) ? 0.49 : 0.0;
        // The banner hangs at the top of the opening; a traveller's feet go at its bottom.
        final int floor = banner.getY() - (WindowShape.BANNER_HEIGHT - 1);
        return new Place(banner.getWorld().getName(), banner.getX() + 0.5 + (shift * facing.getModZ()),
            floor, banner.getZ() + 0.5 - (shift * facing.getModX()),
            MirrorArrival.yawOf(facing), 0.0f);
    }

    /**
     * Whether a mirror's destination is its own room, which is what makes it a reflection.
     *
     * <p>A mirror saved before the network pointed somewhere else, and keeps doing so until it is
     * created again.
     *
     * @return true if it stores its own room
     */
    public static boolean isOwnRoom(final QuantumMirror mirror)
    {
        final Place room = mirror.destination();
        final BlockPlace banner = mirror.banner();
        return (room != null) && room.worldName().equals(banner.worldName())
            && ((int) Math.floor(room.x()) == banner.x()) && ((int) Math.floor(room.z()) == banner.z())
            && ((int) Math.floor(room.y()) == (banner.y() - (WindowShape.BANNER_HEIGHT - 1)));
    }

    /**
     * The mirror this one opens onto.
     *
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
     * @return true if nothing else is chosen and it stores its own room
     */
    public static boolean reflects(final QuantumMirror mirror)
    {
        return chosen(mirror).name().equalsIgnoreCase(mirror.name()) && isOwnRoom(mirror);
    }

    /**
     * {@link #order(QuantumMirror, Player)} for nobody in particular, who sees no private mirror.
     *
     * @param mirror
     *            the mirror being clicked
     * @return what a right-click on it walks through
     */
    static List<QuantumMirror> order(final QuantumMirror mirror)
    {
        return order(mirror, null);
    }

    /**
     * Every mirror a right-click walks through, in order.
     *
     * @param mirror
     *            the mirror being clicked
     * @param viewer
     *            whoever clicked, for the private mirrors; null sees none
     * @return its start if it has one, whatever its group or flags; then every other mirror with
     *         somewhere to go that is {@link #listed listed} for the viewer, by name; never the
     *         mirror itself
     */
    static List<QuantumMirror> order(final QuantumMirror mirror, final Player viewer)
    {
        final String start = mirror.start();
        final List<QuantumMirror> order = MirrorManager.all().stream()
            .filter(other -> !other.name().equalsIgnoreCase(mirror.name()) && (other.destination() != null))
            .filter(other -> other.name().equalsIgnoreCase(start) || listed(mirror, other, viewer))
            .sorted(Comparator.comparing(other -> other.name().toLowerCase(Locale.ROOT)))
            .collect(Collectors.toCollection(ArrayList::new));
        // Its start first, so the first right-click opens onto it.
        for (int i = 0; (start != null) && (i < order.size()); i++)
        {
            if (order.get(i).name().equalsIgnoreCase(start))
            {
                order.add(0, order.remove(i));
                break;
            }
        }
        return order;
    }

    /**
     * Whether a right-click on one mirror lists another for this viewer (#280).
     *
     * <p>Only the same group's, or with no group only the others with none; never a hidden one;
     * and a private one only for a holder of {@link #SEE_PRIVATE}.
     *
     * @param mirror
     *            the mirror being clicked
     * @param other
     *            the mirror that might be listed
     * @param viewer
     *            whoever clicked, or null for nobody in particular
     * @return true if it is listed
     */
    static boolean listed(final QuantumMirror mirror, final QuantumMirror other, final Player viewer)
    {
        final MirrorAccess access = other.access();
        return mirror.access().sameGroup(access) && !access.hidden()
            && (!access.restricted() || ((viewer != null) && viewer.hasPermission(SEE_PRIVATE)));
    }

    /**
     * Whether a player may go through a mirror to where it opens now.
     *
     * <p>The choice is the mirror's, not the player's: somebody allowed a private mirror may have
     * turned it there, or its group changed since. So a punch asks again what this player's own
     * right-click would reach -- its start, or a mirror listed for them.
     *
     * @param mirror
     *            the mirror punched
     * @param player
     *            whoever punched it
     * @return true if where it opens is its own room, its start, or listed for the player
     */
    public static boolean reachable(final QuantumMirror mirror, final Player player)
    {
        final QuantumMirror target = chosen(mirror);
        if (target.name().equalsIgnoreCase(mirror.name()))
        {
            return true;
        }
        return order(mirror, player).stream().anyMatch(other -> other.name().equalsIgnoreCase(target.name()));
    }

    /**
     * Moves a mirror on to the next of the other mirrors, if it may move.
     *
     * <p>Round the others and never to its own room: "we shouldn't have the mirror's own room be
     * on the right-click scroll; it should only show when approached." Walking away is what turns
     * a mirror off.
     *
     * @param mirror
     *            the mirror right-clicked
     * @param othersNear
     *            whether anybody besides whoever clicked is at it
     * @param reader
     *            whoever clicked, who reads the answer in their own language
     * @return what to tell whoever clicked, or null for a repeat of the same press
     */
    public static String scroll(final QuantumMirror mirror, final boolean othersNear, final Player reader)
    {
        return scroll(mirror, othersNear, reader, List.of());
    }

    /**
     * {@link #scroll(QuantumMirror, boolean, Player)}, skipping any mirror one of the others at it
     * could not reach.
     *
     * <p>What a mirror shows is drawn for everybody at it, so a private room chosen by a holder of
     * the node would show to whoever stands beside them (#280).
     *
     * @param mirror
     *            the mirror right-clicked
     * @param others
     *            everybody else within reach of it
     * @param reader
     *            whoever clicked
     * @return what to tell whoever clicked, or null for a repeat of the same press
     */
    public static String scroll(final QuantumMirror mirror, final List<Player> others, final Player reader)
    {
        return scroll(mirror, !others.isEmpty(), reader, others);
    }

    private static String scroll(final QuantumMirror mirror, final boolean othersNear, final Player reader,
        final List<Player> others)
    {
        final List<QuantumMirror> order = order(mirror, reader);
        order.removeIf(other -> !other.name().equalsIgnoreCase(mirror.start())
            && others.stream().anyMatch(viewer -> !listed(mirror, other, viewer)));
        if (order.isEmpty())
        {
            return Messages.get(reader, "mirror.interact.no-others");
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
                return Messages.get(reader, "mirror.interact.someone-else");
            }
        }
        final String current = chosen(mirror).name();
        // Off -- showing its own room -- is before the first; from the last, round to the first.
        int at = -1;
        for (int i = 0; i < order.size(); i++)
        {
            if (order.get(i).name().equalsIgnoreCase(current))
            {
                at = i;
            }
        }
        final int next = (at + 1) % order.size();
        CHOSEN_AT.put(key(mirror.name()), now);
        CHOSEN.put(key(mirror.name()), order.get(next).name());
        return Messages.get(reader, "mirror.interact.opens-onto", MirrorText.name(mirror.name()),
            MirrorText.name(order.get(next).name()), String.valueOf(next + 1), String.valueOf(order.size()));
    }

    /**
     * {@link #scroll(QuantumMirror, boolean, Player)}, answered in the server's language.
     *
     * @param mirror
     *            the mirror right-clicked
     * @param othersNear
     *            whether anybody besides whoever clicked is at it
     * @return what to tell whoever clicked, or null for a repeat of the same press
     */
    public static String scroll(final QuantumMirror mirror, final boolean othersNear)
    {
        return scroll(mirror, othersNear, null);
    }

    /**
     * Lets a mirror nobody is at go back to its own room.
     *
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
     * Turns a mirror back to its own room if somebody within reach could not go where it opens.
     *
     * <p>The view is shared: a holder of the private node who turned a mirror to a private room
     * would otherwise show that room to anybody who walked up afterwards (#280). Called by the
     * sweep before a mirror's view is drawn.
     *
     * @param world
     *            the banner's world
     * @return true if the choice was dropped
     */
    public static boolean keepToWhatAllMaySee(final QuantumMirror mirror, final World world)
    {
        if ((world == null) || (chosen(mirror) == mirror))
        {
            return false;
        }
        for (final Player viewer : playersNear(world, mirror.banner(), null))
        {
            if (!reachable(mirror, viewer))
            {
                forget(mirror.name());
                return true;
            }
        }
        return false;
    }

    /**
     * Everybody, other than one player, within the proximity distance of a banner.
     *
     * @param world
     *            the banner's world
     * @param except
     *            a player not to count, or null
     * @return those players, possibly none
     */
    public static List<Player> playersNear(final World world, final BlockPlace banner, final Player except)
    {
        final double reach = ConfigManager.getMirrorProximityDistance();
        final List<Player> near = new ArrayList<>();
        for (final Player player : world.getPlayers())
        {
            if (!player.equals(except) && within(player.getLocation(), banner, reach))
            {
                near.add(player);
            }
        }
        return near;
    }

    /**
     * Whether anybody, other than one player, is within the proximity distance of a banner.
     *
     * @param world
     *            the banner's world
     * @param except
     *            a player not to count, or null
     * @return true if somebody is
     */
    public static boolean anybodyNear(final World world, final BlockPlace banner, final Player except)
    {
        final double reach = ConfigManager.getMirrorProximityDistance();
        for (final Player player : world.getPlayers())
        {
            if (!player.equals(except) && within(player.getLocation(), banner, reach))
            {
                return true;
            }
        }
        return false;
    }

    /** Whether a place is within a distance of a banner; never for no place. */
    private static boolean within(final Location at, final BlockPlace banner, final double reach)
    {
        if (at == null)
        {
            return false;
        }
        final double dx = at.getX() - (banner.x() + 0.5);
        final double dy = at.getY() - banner.y();
        final double dz = at.getZ() - (banner.z() + 0.5);
        return ((dx * dx) + (dy * dy) + (dz * dz)) <= (reach * reach);
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
