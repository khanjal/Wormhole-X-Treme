package com.wormhole_xtreme.wormhole.model;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Orientable;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.window.Capture;
import com.wormhole_xtreme.wormhole.model.window.Captures;
import com.wormhole_xtreme.wormhole.model.window.Place;
import com.wormhole_xtreme.wormhole.model.window.WindowShape;
import com.wormhole_xtreme.wormhole.model.window.WindowShape.Spot;
import com.wormhole_xtreme.wormhole.model.window.WindowSource;
import com.wormhole_xtreme.wormhole.model.window.Windows;

/**
 * Vanilla nether portals drawn as windows onto where they went (#514), behind {@code nether-portal-views}.
 *
 * <p>Nothing is predicted: a portal shows a view only once somebody has gone through it, and shows
 * the portal they came out of, turned as vanilla turned them. There is no asking a portal where it
 * goes without sending something through. Its far side is captured from chunks already loaded, as a
 * traveller arrives or while somebody looks; nothing is ever loaded to draw one. Drawn behind the
 * purple sheet, which stays, so walking in is a plain vanilla trip; the drawing ends for whoever
 * steps into the portal's plane, as for a gate.
 */
public final class PortalViews
{
    /** Before a portal window's name, so the sweep tells it from a mirror's or a gate's. */
    static final String PREFIX = "portal:";

    /** Most portal windows offered server-wide each sweep. */
    static final int MOST_WINDOWS = 16;

    /** Most links remembered; the oldest learnt goes first. */
    static final int MOST_LINKS = 1024;

    /** How long after stepping into a portal a landing still counts as that trip's. */
    static final long TRIP_MILLIS = 30_000L;

    /** How long after a trip its far side is captured, as its chunks load round the traveller. */
    static final long ARRIVAL_MILLIS = 60_000L;

    /** How old a far side's capture may be before it is taken again. */
    static final long REFRESH_SECONDS = 600L;

    /**
     * Where one portal went: the portal a traveller came out of, and how far vanilla turned them.
     *
     * @param from
     *            the portal they went into
     * @param to
     *            the portal they came out of
     * @param turn
     *            quarter turns clockwise vanilla turned them, 0 to 3
     * @param learntAt
     *            when
     */
    record Link(NetherPortal from, NetherPortal to, int turn, long learntAt)
    {
    }

    /** Somebody who stepped into a portal and has not landed yet. */
    private record Trip(NetherPortal from, float yaw, long at)
    {
    }

    /** One face of a linked portal that somebody is looking into. */
    private record Choice(Link link, BlockFace face, World world, double cosine)
    {
    }

    /** Links by the id of the portal they go from. */
    private static final Map<String, Link> LINKS = new LinkedHashMap<>();

    /** Trips under way, by traveller. */
    private static final Map<UUID, Trip> TRIPS = new HashMap<>();

    /** Links learnt in the last minute, oldest first, whose far side is still being captured. */
    private static final Deque<Link> RECENT = new ArrayDeque<>();

    /** Reads a world's portal blocks; settable so a test needs no server. */
    static Function<World, NetherPortal.Cells> cellsIn = PortalViews::cellsOf;

    /** Finds a loaded world by name; settable for a test. */
    static Function<String, World> worldNamed = Bukkit::getWorld;

    /** The capture held under a key; settable for a test. */
    static Function<String, Capture> captureOf = Captures::get;

    /** Asks for a capture, from loaded chunks only; settable for a test. */
    static Requester requester = Captures::requestLoaded;

    /** Offers a window to the sweep; settable for a test. */
    static BiConsumer<WindowSource, Capture> offerer = Windows::offer;

    /** Takes a window back from whoever is drawn it; settable for a test. */
    static Consumer<String> releaser = Windows::release;

    /** {@link Captures#requestLoaded}, as a test can stand in for it. */
    @FunctionalInterface
    interface Requester
    {
        /** @return true if one was started */
        boolean request(String key, String what, Place arrival, int depth, long olderThanSeconds);
    }

    private PortalViews()
    {
    }

    /** Forgets every link and trip, for a reload, a stop or a test. */
    public static void clear()
    {
        LINKS.clear();
        TRIPS.clear();
        RECENT.clear();
    }

    /** @return the links remembered, for a test */
    static Map<String, Link> links()
    {
        return LINKS;
    }

    /** @return whether the feature is on */
    private static boolean enabled()
    {
        return ConfigManager.isNetherPortalViews();
    }

    /** @return whether a world's portals may show a view */
    static boolean allowed(final String world)
    {
        final Set<String> worlds = ConfigManager.getNetherPortalViewWorlds();
        return worlds.isEmpty() || worlds.contains(world.toLowerCase(Locale.ROOT));
    }

    /**
     * Notes somebody stepping into a nether portal, so where they land can be learnt.
     *
     * @param traveller
     *            who
     * @param from
     *            where they stood as the trip began
     * @param now
     *            the time
     */
    public static void leaving(final UUID traveller, final Location from, final long now)
    {
        final World world = from.getWorld();
        if (!enabled() || (world == null) || !allowed(world.getName()))
        {
            return;
        }
        final NetherPortal portal = near(world, from);
        if (portal == null)
        {
            TRIPS.remove(traveller);
        }
        else
        {
            TRIPS.put(traveller, new Trip(portal, from.getYaw(), now));
        }
    }

    /**
     * Learns where a portal went from where its traveller landed: another world's portal block.
     *
     * @param traveller
     *            who
     * @param to
     *            where they are now
     * @param now
     *            the time
     */
    public static void arrived(final UUID traveller, final Location to, final long now)
    {
        final Trip trip = TRIPS.remove(traveller);
        final World world = to.getWorld();
        if ((trip == null) || ((now - trip.at()) > TRIP_MILLIS) || (world == null) || !enabled()
            || !allowed(world.getName()))
        {
            return;
        }
        final NetherPortal landed = near(world, to);
        // Landed somewhere else, put there by another plugin, or back in the same world: nothing learnt.
        if ((landed == null) || landed.world().equals(trip.from().world()))
        {
            return;
        }
        final int turn = Math.floorMod(Math.round((to.getYaw() - trip.yaw()) / 90.0f), 4);
        final Link link = new Link(trip.from(), landed, turn, now);
        if (!consistent(link))
        {
            return;
        }
        LINKS.remove(link.from().id());
        LINKS.put(link.from().id(), link);
        if (LINKS.size() > MOST_LINKS)
        {
            final Iterator<String> oldest = LINKS.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
        RECENT.add(link);
    }

    /** Whether looking through a link's portal along either face comes out square to its far portal. */
    static boolean consistent(final Link link)
    {
        final Spot ahead = WindowShape.aheadOf(farYaw(link.from().faces()[0], link.turn()));
        return link.to().alongX() ? (ahead.x() == 0) : (ahead.z() == 0);
    }

    /**
     * The portal a place is in or beside: its own block, the one above, or one next to either.
     *
     * @return the portal, or null for none
     */
    static NetherPortal near(final World world, final Location at)
    {
        final NetherPortal.Cells cells = cellsIn.apply(world);
        final int x = (int) Math.floor(at.getX());
        final int y = (int) Math.floor(at.getY());
        final int z = (int) Math.floor(at.getZ());
        for (final int dy : new int[] { 0, 1 })
        {
            for (int dx = -1; dx <= 1; dx++)
            {
                for (int dz = -1; dz <= 1; dz++)
                {
                    final NetherPortal portal = NetherPortal.at(world.getName(), x + dx, y + dy, z + dz, cells);
                    if (portal != null)
                    {
                        return portal;
                    }
                }
            }
        }
        return null;
    }

    /** Forgets a player's trip under way, as they leave. */
    public static void forgetViewer(final UUID viewer)
    {
        TRIPS.remove(viewer);
    }

    /**
     * Forgets every link to or from a world being unloaded, and takes back its windows.
     *
     * @param world
     *            the world's name
     */
    public static void worldUnloaded(final String world)
    {
        TRIPS.values().removeIf(trip -> trip.from().world().equals(world));
        RECENT.removeIf(link -> link.from().world().equals(world) || link.to().world().equals(world));
        final Iterator<Link> links = LINKS.values().iterator();
        while (links.hasNext())
        {
            final Link link = links.next();
            if (link.from().world().equals(world) || link.to().world().equals(world))
            {
                releaseFaces(link.from());
                links.remove();
            }
        }
    }

    /** Offers the portals people are looking into to the window sweep; one sweep's worth, failures said once. */
    public static void offerAll()
    {
        try
        {
            offer(Bukkit.getOnlinePlayers(), System.currentTimeMillis());
        }
        catch (final Exception | LinkageError e)
        {
            GateViews.failedOnce("Could not offer nether portal views to the mirror sweep", e);
        }
    }

    /**
     * Captures the far sides of fresh trips, and offers the portal each player looks into, up to
     * {@link #MOST_WINDOWS} in all. A window not offered drops out of the sweep by itself, so turning
     * the setting off takes every view back on the next sweep.
     *
     * @param players
     *            who is online
     * @param now
     *            the time
     */
    static void offer(final Collection<? extends Player> players, final long now)
    {
        if (!enabled())
        {
            return;
        }
        captureArrivals(now);
        if (LINKS.isEmpty())
        {
            return;
        }
        final Map<String, Choice> chosen = new LinkedHashMap<>();
        for (final Player player : players)
        {
            if (chosen.size() >= MOST_WINDOWS)
            {
                break;
            }
            final Choice choice = player.hasPermission(PortalSource.PERMISSION) ? lookedAt(player) : null;
            if (choice != null)
            {
                chosen.putIfAbsent(nameOf(choice.link().from(), choice.face()), choice);
            }
        }
        chosen.values().forEach(PortalViews::offerOne);
    }

    /** Asks for the far side of each trip learnt in the last minute, from whatever has loaded round the traveller. */
    private static void captureArrivals(final long now)
    {
        while (!RECENT.isEmpty() && ((now - RECENT.peekFirst().learntAt()) > ARRIVAL_MILLIS))
        {
            RECENT.pollFirst();
        }
        for (final Link link : RECENT)
        {
            for (final BlockFace face : link.from().faces())
            {
                requester.request(captureKeyOf(link, face), whatOf(link), arrivalOf(link, face), depth(), REFRESH_SECONDS);
            }
        }
    }

    /**
     * The linked portal face a player is in front of, near and looking most straight at.
     *
     * @return it, or null for none
     */
    private static Choice lookedAt(final Player player)
    {
        final Location eye = player.getEyeLocation();
        final World world = eye.getWorld();
        if ((world == null) || !allowed(world.getName()))
        {
            return null;
        }
        final double reach = ConfigManager.getMirrorProximityDistance();
        final Vector look = eye.getDirection();
        Choice best = null;
        for (final Link link : LINKS.values())
        {
            final NetherPortal portal = link.from();
            if (!portal.world().equals(world.getName()))
            {
                continue;
            }
            final Spot low = portal.spotAt(portal.lowAcross(), portal.lowY());
            final Spot high = portal.spotAt(portal.highAcross(), portal.highY());
            final Vector to = new Vector(((low.x() + high.x()) / 2.0) + 0.5 - eye.getX(),
                ((low.y() + high.y()) / 2.0) + 0.5 - eye.getY(), ((low.z() + high.z()) / 2.0) + 0.5 - eye.getZ());
            final double length = to.length();
            if (length > reach)
            {
                continue;
            }
            final double cosine = (length == 0) ? 1.0 : (to.dot(look) / length);
            for (final BlockFace face : portal.faces())
            {
                if ((portal.outFrom(face, eye.getX(), eye.getZ()) > 0.5) && ((best == null) || (cosine > best.cosine())))
                {
                    best = new Choice(link, face, world, cosine);
                }
            }
        }
        return best;
    }

    /** Offers one portal face, once both its portals are still lit and its far side is captured. */
    private static void offerOne(final Choice choice)
    {
        final Link link = choice.link();
        final NetherPortal portal = link.from();
        if (!loaded(choice.world(), portal))
        {
            return;
        }
        if (!portal.intact(cellsIn.apply(choice.world())))
        {
            drop(portal);
            return;
        }
        final World far = worldNamed.apply(link.to().world());
        if ((far != null) && loaded(far, link.to()) && !link.to().intact(cellsIn.apply(far)))
        {
            drop(link.to());
            return;
        }
        final Place arrival = arrivalOf(link, choice.face());
        final WindowShape shape = GateViews.shapeOf(choice.face(), portal.cells(), portal.frame(), arrival);
        if (shape == null)
        {
            // Bigger than a view is drawn for: it stays a plain portal.
            return;
        }
        final String key = captureKeyOf(link, choice.face());
        requester.request(key, whatOf(link), arrival, depth(), REFRESH_SECONDS);
        final Capture capture = captureOf.apply(key);
        if (capture == null)
        {
            return;
        }
        final Spot middle = GateViews.middleOf(portal.cells());
        final Block anchor = choice.world().getBlockAt(middle.x(), middle.y(), middle.z());
        offerer.accept(new PortalSource(nameOf(portal, choice.face()), anchor, shape, portal.cells(), arrival,
            Captures.drawableReach(capture, depth())), capture);
    }

    /** Whether every chunk a portal's blocks are in is loaded, so reading them loads nothing. */
    private static boolean loaded(final World world, final NetherPortal portal)
    {
        for (int across = portal.lowAcross(); across <= portal.highAcross(); across++)
        {
            final Spot cell = portal.spotAt(across, portal.lowY());
            if (!world.isChunkLoaded(cell.x() >> 4, cell.z() >> 4))
            {
                return false;
            }
        }
        return true;
    }

    /** Forgets every link to or from a portal whose blocks have gone, and takes its views back. */
    static void drop(final NetherPortal portal)
    {
        final Iterator<Link> links = LINKS.values().iterator();
        while (links.hasNext())
        {
            final Link link = links.next();
            if (link.from().equals(portal) || link.to().equals(portal))
            {
                releaseFaces(link.from());
                links.remove();
            }
        }
        RECENT.removeIf(link -> link.from().equals(portal) || link.to().equals(portal));
    }

    /** Takes back both faces' windows of a portal. */
    private static void releaseFaces(final NetherPortal portal)
    {
        for (final BlockFace face : portal.faces())
        {
            releaser.accept(nameOf(portal, face));
        }
    }

    /** @return the name a portal face's window is offered under */
    static String nameOf(final NetherPortal portal, final BlockFace face)
    {
        return PREFIX + portal.id() + ' ' + face.name().toLowerCase(Locale.ROOT);
    }

    /** @return how far a portal's view is drawn: a gate's first step */
    private static int depth()
    {
        return ConfigManager.getGateViewFirstDepth();
    }

    /** @return what a link's capture is for, for the log */
    private static String whatOf(final Link link)
    {
        return "nether portal " + link.to().id();
    }

    /**
     * The yaw one looks along through a portal's face, once through and turned as vanilla turns a traveller.
     *
     * @param face
     *            the face looked into, which the viewer stands in front of
     * @param turn
     *            quarter turns clockwise
     * @return a Minecraft yaw: 0 is south, turning clockwise through west
     */
    static float farYaw(final BlockFace face, final int turn)
    {
        // Looking into a face is looking the other way from it: into a south face is looking north.
        final float into = switch (face)
        {
            case SOUTH -> 180.0f;
            case WEST -> 270.0f;
            case NORTH -> 0.0f;
            default -> 90.0f;
        };
        return (float) Math.floorMod(Math.round(into + (90.0f * turn)), 360);
    }

    /**
     * Where a portal face's view comes out: a block out of the far portal, at the middle of its bottom
     * row, looking away from it.
     *
     * @return the far side's arrival point
     */
    static Place arrivalOf(final Link link, final BlockFace face)
    {
        final float yaw = farYaw(face, link.turn());
        final Spot ahead = WindowShape.aheadOf(yaw);
        final Spot middle = link.to().bottomMiddle();
        return new Place(link.to().world(), middle.x() + ahead.x() + 0.5, middle.y(), middle.z() + ahead.z() + 0.5,
            yaw, 0.0f);
    }

    /** @return the key a face's far side is captured under: the far portal and the way it is looked out of */
    static String captureKeyOf(final Link link, final BlockFace face)
    {
        return Captures.gateKey("nether portal " + link.to().id() + ' ' + Math.round(farYaw(face, link.turn())),
            Captures.GATE_OPENING, Captures.GATE_OPENING);
    }

    /** A world's portal blocks, read only where their chunk is loaded. */
    static NetherPortal.Cells cellsOf(final World world)
    {
        return (x, y, z) ->
        {
            if (!world.isChunkLoaded(x >> 4, z >> 4))
            {
                return null;
            }
            final Block block = world.getBlockAt(x, y, z);
            if ((block.getType() != Material.NETHER_PORTAL) || !(block.getBlockData() instanceof final Orientable portal))
            {
                return null;
            }
            return portal.getAxis();
        };
    }
}
