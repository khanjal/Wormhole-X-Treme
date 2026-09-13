package com.wormhole_xtreme.wormhole.model.mirror;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Rotatable;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorWindow.Spot;

/**
 * Drawing what is on the other side of every window mirror a player is looking into.
 *
 * <p>Every mirror with somewhere to go is a window: one hung on a wall opens in the wall, and a
 * freestanding one opens in the air behind where it stands. Nothing is stored to say so, which is
 * what lets mirrors made before windows existed open as one without being touched.
 *
 * <p>Nothing in the world changes, the way a gate's event horizon changes nothing. A viewer is
 * sent the banner as air, the open part of the opening as barrier -- invisible, and as solid as
 * the wall it covers -- and far-side blocks behind the wall: only the ones they could see through
 * an opening from where their eye is.
 *
 * <p>That last part is what lets windows share a wall. A block behind it belongs to whichever
 * opening the viewer's line of sight passes through, so two windows a block apart never draw
 * over each other. The first cut gave each window a fixed box instead, and neighbours in a row
 * of alcoves took turns overwriting each other every few seconds.
 *
 * <p>Each viewer has one drawing, and only what changes is sent: as they move, as a far side is
 * re-read, and in full every few seconds because a fresh copy of a chunk erases it.
 *
 * <p>A prototype for #278: blocks only, no entities, and lit and tinted by this world.
 */
public final class MirrorWindows
{
    /** How often a viewer is sent their whole view again, since a fresh chunk erases it. */
    private static final long RESEND_MILLIS = 3000L;

    /** How old a window's reading of its far side may get before it is read again. */
    private static final long RESAMPLE_MILLIS = 5000L;

    /** Every window the last sweep found, by mirror name. */
    private static final Map<String, Window> WINDOWS = new HashMap<>();

    /** Windows found by the sweep in progress. */
    private static final Map<String, Window> OFFERED = new HashMap<>();

    /** What each viewer's client has been told, by player. */
    private static final Map<UUID, View> VIEWS = new ConcurrentHashMap<>();

    /** One window: where it is, which of its opening can be seen through, and its far side. */
    private static final class Window
    {
        private final QuantumMirror mirror;
        private final MirrorWindow shape;
        private final Block banner;
        private final List<Spot> open;
        private final Set<Long> openKeys = new HashSet<>();
        private Set<Long> solid = Set.of();
        private long[] cells;
        private BlockData[] far;
        private long takenAt;

        Window(final QuantumMirror mirror, final MirrorWindow shape, final Block banner,
            final List<Spot> open)
        {
            this.mirror = mirror;
            this.shape = shape;
            this.banner = banner;
            this.open = open;
            open.forEach(cell -> openKeys.add(key(cell.x(), cell.y(), cell.z())));
        }
    }

    /** One viewer's drawing, as last sent. */
    private static final class View
    {
        private final World world;
        private Map<Long, BlockData> drawn = new HashMap<>();
        private Set<String> mirrors = Set.of();
        private long fullAt;
        private long eye = Long.MIN_VALUE;

        View(final World world)
        {
            this.world = world;
        }
    }

    /** Static state only. */
    private MirrorWindows()
    {
    }

    /** Forgets every view without sending anything, for a test or a reload. */
    public static void clear()
    {
        WINDOWS.clear();
        OFFERED.clear();
        VIEWS.clear();
    }

    /**
     * Offers a mirror to the sweep in progress.
     *
     * @param mirror
     *            a mirror with somewhere to go
     * @param banner
     *            its loaded banner block
     * @return true if it is a window, and nothing else should be done with it this sweep
     */
    static boolean offer(final QuantumMirror mirror, final Block banner)
    {
        final BlockData data = banner.getBlockData();
        final boolean standing = data instanceof Rotatable;
        if ((!standing && !(data instanceof Directional))
            || (Bukkit.getWorld(mirror.destination().worldName()) == null))
        {
            return false;
        }
        final MirrorWindow shape = MirrorWindow.of(mirror.banner(), MirrorArrival.facingOf(data),
            standing, mirror.destination());
        if (shape == null)
        {
            return false;
        }
        final Window window = new Window(mirror, shape, banner, openCells(shape, banner.getWorld()));
        final Window previous = WINDOWS.get(mirror.name());
        if ((previous != null) && previous.shape.equals(shape)
            && previous.mirror.destination().equals(mirror.destination()))
        {
            window.cells = previous.cells;
            window.far = previous.far;
            window.solid = previous.solid;
            window.takenAt = previous.takenAt;
        }
        OFFERED.put(mirror.name(), window);
        return true;
    }

    /** Ends a sweep: the windows offered become the windows there are, and every view follows. */
    static void finish()
    {
        if (OFFERED.isEmpty() && WINDOWS.isEmpty() && VIEWS.isEmpty())
        {
            return;
        }
        WINDOWS.clear();
        WINDOWS.putAll(OFFERED);
        OFFERED.clear();
        final long now = System.currentTimeMillis();
        final Set<UUID> seen = new HashSet<>();
        final Set<World> worlds = new LinkedHashSet<>();
        WINDOWS.values().forEach(window -> worlds.add(window.banner.getWorld()));
        for (final World world : worlds)
        {
            for (final Player player : world.getPlayers())
            {
                seen.add(player.getUniqueId());
                update(player, player.getEyeLocation(), now);
            }
        }
        // Viewers in no world with a window left in it: gone, or left behind by a window.
        for (final UUID id : new ArrayList<>(VIEWS.keySet()))
        {
            if (!seen.contains(id))
            {
                final Player player = Bukkit.getPlayer(id);
                if (player == null)
                {
                    VIEWS.remove(id);
                }
                else
                {
                    update(player, player.getEyeLocation(), now);
                }
            }
        }
    }

    /**
     * Keeps a player's view in step as they move, rather than waiting for the next sweep.
     *
     * <p>On every move of every player, so a server with no windows answers from two empty maps,
     * and a viewer is redrawn only when their eye has moved half a block.
     *
     * @param player
     *            who moved
     * @param to
     *            where they are moving to
     */
    public static void moved(final Player player, final Location to)
    {
        if ((WINDOWS.isEmpty() && VIEWS.isEmpty()) || (player == null) || (to == null))
        {
            return;
        }
        final UUID id = player.getUniqueId();
        final View view = (id == null) ? null : VIEWS.get(id);
        final Location eye = to.clone().add(0.0, player.getEyeHeight(), 0.0);
        if ((view == null) && !nearAWindow(player, to))
        {
            return;
        }
        if ((view != null) && (view.eye == eyeKey(eye)))
        {
            return;
        }
        update(player, eye, System.currentTimeMillis());
    }

    /**
     * Takes one mirror out of every view it is in.
     *
     * @param mirror
     *            the mirror no longer being drawn
     */
    public static void release(final QuantumMirror mirror)
    {
        OFFERED.remove(mirror.name());
        if (WINDOWS.remove(mirror.name()) == null)
        {
            return;
        }
        final long now = System.currentTimeMillis();
        for (final Map.Entry<UUID, View> entry : new ArrayList<>(VIEWS.entrySet()))
        {
            if (entry.getValue().mirrors.contains(mirror.name()))
            {
                final Player player = Bukkit.getPlayer(entry.getKey());
                if (player == null)
                {
                    VIEWS.remove(entry.getKey());
                }
                else
                {
                    update(player, player.getEyeLocation(), now);
                }
            }
        }
    }

    /** Takes every view back, as the plugin stops. */
    public static void restoreAll()
    {
        final long now = System.currentTimeMillis();
        for (final Map.Entry<UUID, View> entry : VIEWS.entrySet())
        {
            final Player player = Bukkit.getPlayer(entry.getKey());
            if ((player != null) && player.getWorld().equals(entry.getValue().world))
            {
                send(player, entry.getValue(), new HashMap<>(), now);
            }
        }
        clear();
    }

    /**
     * The window mirror a player clicked through, if they clicked one they are looking into.
     *
     * <p>On every right-click of every block, so a player who is looking into no window is
     * answered from one map lookup, before the block is asked anything.
     *
     * @param player
     *            who clicked
     * @param block
     *            the real block behind what they clicked
     * @return the mirror, or null
     */
    static QuantumMirror clicked(final Player player, final Block block)
    {
        final UUID id = (player == null) ? null : player.getUniqueId();
        final View view = (id == null) ? null : VIEWS.get(id);
        if ((view == null) || (block == null))
        {
            return null;
        }
        final Spot at = new Spot(block.getX(), block.getY(), block.getZ());
        for (final String name : view.mirrors)
        {
            final Window window = WINDOWS.get(name);
            if ((window != null) && window.open.contains(at)
                && window.banner.getWorld().equals(block.getWorld()))
            {
                return window.mirror;
            }
        }
        return null;
    }

    /** Redraws one player's view from where their eye is, sending only what changed. */
    private static void update(final Player player, final Location eye, final long now)
    {
        final UUID id = player.getUniqueId();
        View view = VIEWS.get(id);
        if ((view != null) && !view.world.equals(player.getWorld()))
        {
            // A new world's chunks have already replaced everything drawn in the old one.
            VIEWS.remove(id);
            view = null;
        }
        final List<Window> seeing = seenBy(player, eye);
        if ((view == null) && seeing.isEmpty())
        {
            return;
        }
        seeing.forEach(window -> sample(window, now));
        final Map<Long, BlockData> wanted = compose(eye, seeing);
        if (view == null)
        {
            view = new View(player.getWorld());
            VIEWS.put(id, view);
        }
        send(player, view, wanted, now);
        final Set<String> names = new HashSet<>();
        seeing.forEach(window -> names.add(window.mirror.name()));
        view.mirrors = names;
        view.eye = eyeKey(eye);
        if (wanted.isEmpty())
        {
            VIEWS.remove(id);
        }
    }

    /** The windows a player is close enough to, and in front of, in a stable order. */
    private static List<Window> seenBy(final Player player, final Location eye)
    {
        final double radius = ConfigManager.getMirrorProximityRadius();
        final Location at = player.getLocation();
        final List<Window> seeing = new ArrayList<>();
        for (final Window window : WINDOWS.values())
        {
            if (!window.open.isEmpty() && window.banner.getWorld().equals(player.getWorld())
                && (at.distanceSquared(window.banner.getLocation()) <= (radius * radius))
                && window.shape.inFront(eye.getX(), eye.getZ()))
            {
                seeing.add(window);
            }
        }
        seeing.sort(Comparator.comparing(window -> window.mirror.name()));
        return seeing;
    }

    /** Whether somebody not yet looking into anything has just come within range of a window. */
    private static boolean nearAWindow(final Player player, final Location to)
    {
        final double radius = ConfigManager.getMirrorProximityRadius();
        for (final Window window : WINDOWS.values())
        {
            if (window.banner.getWorld().equals(player.getWorld())
                && (to.distanceSquared(window.banner.getLocation()) <= (radius * radius)))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Every block one viewer should be shown, and what as.
     *
     * <p>Openings first, so a block that is part of one opening is never drawn as another's far
     * side. Then each far-side block, if the eye can see it through that window's opening and
     * through no opening in the same wall whose middle its line of sight passes nearer.
     */
    private static Map<Long, BlockData> compose(final Location eye, final List<Window> seeing)
    {
        final BlockData air = Bukkit.createBlockData(Material.AIR);
        final BlockData barrier = Bukkit.createBlockData(Material.BARRIER);
        final Map<Long, BlockData> wanted = new HashMap<>();
        final Set<Long> allOpen = new HashSet<>();
        seeing.forEach(window -> allOpen.addAll(window.openKeys));
        for (final Window window : seeing)
        {
            // Only where the banner's patterns can be sent back afterwards. On plain 1.20 it
            // stays hanging in front of the view.
            if (MirrorPackets.available())
            {
                final MirrorBlock banner = window.mirror.banner();
                wanted.put(key(banner.x(), banner.y(), banner.z()), air);
            }
            window.open.forEach(cell -> wanted.put(key(cell.x(), cell.y(), cell.z()), barrier));
        }
        for (final Window window : seeing)
        {
            for (int i = 0; i < window.cells.length; i++)
            {
                final long cell = window.cells[i];
                if (!wanted.containsKey(cell) && showsThrough(eye, window, cell, seeing, allOpen))
                {
                    wanted.put(cell, window.far[i]);
                }
            }
        }
        return wanted;
    }

    /**
     * Whether a block behind the opening is seen through this window, and only through it.
     *
     * <p>Seen through the opening, all of it hidden by solid blocks or the opening itself --
     * never across open air or another window's opening -- and through no opening in the same
     * face whose middle the line of sight passes nearer.
     */
    private static boolean showsThrough(final Location eye, final Window window, final long cell,
        final List<Window> seeing, final Set<Long> allOpen)
    {
        final double[] rect = window.shape.projected(eye.getX(), eye.getY(), eye.getZ(),
            unpackX(cell), unpackY(cell), unpackZ(cell));
        if ((rect == null) || !window.shape.overlaps(rect, window.open)
            || !window.shape.covered(rect, (across, y) -> clear(window, across, y, allOpen)))
        {
            return false;
        }
        final double mine = window.shape.offCentre(rect);
        for (final Window other : seeing)
        {
            if ((other != window) && other.shape.sharesFace(window.shape)
                && other.shape.overlaps(rect, other.open) && (other.shape.offCentre(rect) < mine))
            {
                return false;
            }
        }
        return true;
    }

    /** Whether a block of a window's face keeps a drawn block behind it out of sight elsewhere. */
    private static boolean clear(final Window window, final int across, final int y,
        final Set<Long> allOpen)
    {
        final long face = faceKey(window.shape, across, y);
        return window.openKeys.contains(face)
            || (window.solid.contains(face) && !allOpen.contains(face));
    }

    /** The block of a window's face at a coordinate along it. */
    private static long faceKey(final MirrorWindow shape, final int across, final int y)
    {
        return (shape.into().x() != 0) ? key(shape.base().x(), y, across)
            : key(across, y, shape.base().z());
    }

    /**
     * The blocks of a window's face that hide what is behind them, over the area a block drawn
     * behind the opening can project onto.
     */
    private static Set<Long> solidFace(final MirrorWindow shape, final World here)
    {
        final int centre = (shape.into().x() != 0) ? shape.base().z() : shape.base().x();
        final int reach = MirrorWindow.SIDE + MirrorWindow.WIDTH + 2;
        final Set<Long> solid = new HashSet<>();
        for (int across = centre - reach; across <= (centre + reach); across++)
        {
            for (int y = shape.base().y() - MirrorWindow.BELOW - 2;
                y <= (shape.base().y() + MirrorWindow.HEIGHT + MirrorWindow.ABOVE + 2); y++)
            {
                final long face = faceKey(shape, across, y);
                if (here.getBlockAt(unpackX(face), y, unpackZ(face)).getBlockData().isOccluding())
                {
                    solid.add(face);
                }
            }
        }
        return solid;
    }

    /** Sends a viewer what changed since their last drawing, or all of it when it is due. */
    private static void send(final Player player, final View view,
        final Map<Long, BlockData> wanted, final long now)
    {
        final boolean full = (now - view.fullAt) >= RESEND_MILLIS;
        final List<BlockState> changes = new ArrayList<>();
        for (final Map.Entry<Long, BlockData> entry : wanted.entrySet())
        {
            if (full || !entry.getValue().equals(view.drawn.get(entry.getKey())))
            {
                drawn(changes, view.world, entry.getKey(), entry.getValue());
            }
        }
        final List<TileState> restored = new ArrayList<>();
        for (final Long cell : view.drawn.keySet())
        {
            if (!wanted.containsKey(cell))
            {
                truth(changes, restored, view.world, cell);
            }
        }
        if (!changes.isEmpty())
        {
            player.sendBlockChanges(changes);
        }
        // A block change carries no banner patterns or sign text, so those follow on their own.
        restored.forEach(tile -> MirrorPackets.send(player, tile.getLocation(), tile));
        view.drawn = wanted;
        if (full)
        {
            view.fullAt = now;
        }
    }

    /** Adds one block, drawn as something else, if this world can draw it. */
    private static void drawn(final List<BlockState> changes, final World here, final long cell,
        final BlockData data)
    {
        final int x = unpackX(cell);
        final int z = unpackZ(cell);
        if (!here.isChunkLoaded(x >> 4, z >> 4))
        {
            return;
        }
        final BlockState state = here.getBlockAt(x, unpackY(cell), z).getState();
        try
        {
            state.setBlockData(data);
        }
        catch (final IllegalArgumentException refused)
        {
            // A block entity's state will not always take another block's data. That one block
            // shows as it really is.
            return;
        }
        changes.add(state);
    }

    /** Adds one block as the world really has it. */
    private static void truth(final List<BlockState> changes, final List<TileState> restored,
        final World here, final long cell)
    {
        final int x = unpackX(cell);
        final int z = unpackZ(cell);
        // A chunk that has unloaded is one the client dropped too, and it gets a fresh copy.
        if (!here.isChunkLoaded(x >> 4, z >> 4))
        {
            return;
        }
        final BlockState state = here.getBlockAt(x, unpackY(cell), z).getState();
        changes.add(state);
        if (state instanceof TileState tile)
        {
            restored.add(tile);
        }
    }

    /** Reads a window's far side, if it has not been read recently. */
    private static void sample(final Window window, final long now)
    {
        if ((window.far != null) && ((now - window.takenAt) < RESAMPLE_MILLIS))
        {
            return;
        }
        final World here = window.banner.getWorld();
        final int min = here.getMinHeight();
        final int max = here.getMaxHeight();
        final FarSide farSide = new FarSide(Bukkit.getWorld(window.mirror.destination().worldName()),
            Bukkit.createBlockData(Material.AIR));
        final List<Long> cells = new ArrayList<>();
        final List<BlockData> far = new ArrayList<>();
        window.shape.forEachShown((x, y, z, farX, farY, farZ) ->
        {
            if ((y >= min) && (y < max))
            {
                cells.add(key(x, y, z));
                far.add(farSide.at(farX, farY, farZ));
            }
        });
        window.cells = cells.stream().mapToLong(Long::longValue).toArray();
        window.far = far.toArray(new BlockData[0]);
        window.solid = solidFace(window.shape, here);
        window.takenAt = now;
    }

    /** The blocks of a window's opening with nothing solid in front of them. */
    private static List<Spot> openCells(final MirrorWindow shape, final World here)
    {
        final List<Spot> open = new ArrayList<>();
        shape.forEachOpening((x, y, z) ->
        {
            // A pillar or a shelf in front of part of the opening closes that part: nobody sees
            // through it, and a neighbouring window may be using the space behind.
            if (here.getBlockAt(x - shape.into().x(), y, z - shape.into().z()).isPassable())
            {
                open.add(new Spot(x, y, z));
            }
        });
        return open;
    }

    /** An eye position, to half a block, as a key that can be compared cheaply. */
    private static long eyeKey(final Location eye)
    {
        return key((int) Math.floor(eye.getX() * 2.0), (int) Math.floor(eye.getY() * 2.0),
            (int) Math.floor(eye.getZ() * 2.0));
    }

    /**
     * A block position packed into one long: 26 bits of x, 26 of z, 12 of y.
     *
     * @param x
     *            x
     * @param y
     *            y
     * @param z
     *            z
     * @return the key
     */
    static long key(final int x, final int y, final int z)
    {
        return ((x & 0x3FFFFFFL) << 38) | ((z & 0x3FFFFFFL) << 12) | (y & 0xFFFL);
    }

    static int unpackX(final long key)
    {
        return (int) (key >> 38);
    }

    static int unpackY(final long key)
    {
        return (int) ((key << 52) >> 52);
    }

    static int unpackZ(final long key)
    {
        return (int) ((key << 26) >> 38);
    }

    /** Reads the far side, showing its mirrors' banners as the openings they are. */
    private static final class FarSide
    {
        private final World far;
        private final String name;
        private final int min;
        private final int max;
        private final BlockData air;

        FarSide(final World far, final BlockData air)
        {
            this.far = far;
            this.name = far.getName();
            this.min = far.getMinHeight();
            this.max = far.getMaxHeight();
            this.air = air;
        }

        BlockData at(final int x, final int y, final int z)
        {
            // A linked pair arrives in the far banner's own block, so it would otherwise hang in
            // the middle of the view.
            if ((y < min) || (y >= max) || (MirrorManager.at(new MirrorBlock(name, x, y, z)) != null))
            {
                return air;
            }
            // Loads the chunk if it has to, which only happens while somebody is looking in.
            return far.getBlockAt(x, y, z).getBlockData();
        }
    }
}
