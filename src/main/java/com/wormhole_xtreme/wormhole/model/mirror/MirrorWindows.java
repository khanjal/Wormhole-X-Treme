package com.wormhole_xtreme.wormhole.model.mirror;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.config.ConfigManager;

/**
 * Drawing a window mirror's far side for whoever stands in front of it.
 *
 * <p>Any mirror whose banner hangs on a wall is a window. Nothing is stored to say so, which is
 * what lets mirrors made before windows existed open as one without being touched.
 *
 * <p>Nothing in the world changes, the way a gate's event horizon changes nothing. Each viewer
 * is sent the banner as air, the opening as barrier -- invisible, and at least as solid as the
 * wall it covers -- and the far side's blocks in a box where the wall and whatever lies behind
 * it really are. Those are real blocks as far as the client knows, so looking in from an angle
 * shows depth the way a window does.
 *
 * <p>A prototype for #278: blocks only, no entities, and lit and tinted by this world rather
 * than the far one.
 */
public final class MirrorWindows
{
    /** How often a viewer is sent the view again, since a fresh copy of a chunk erases it. */
    private static final long RESEND_MILLIS = 3000L;

    /** How old a drawing may get before the far side is read again. */
    private static final long RESAMPLE_MILLIS = 5000L;

    /** When each viewer was last sent each mirror's view, by mirror name. */
    private static final Map<String, Map<UUID, Long>> SENT = new HashMap<>();

    /** Which mirrors each player is being shown, so a click can be matched without a scan. */
    private static final Map<UUID, Set<String>> VIEWING = new ConcurrentHashMap<>();

    /** The latest drawing of each mirror, by mirror name. */
    private static final Map<String, Drawing> DRAWINGS = new HashMap<>();

    /**
     * One mirror's view, ready to send.
     *
     * @param window
     *            the shape it was drawn for
     * @param blocks
     *            the real blocks drawn over, for taking the view back
     * @param states
     *            what each of those blocks is drawn as, in the same order
     * @param takenAt
     *            when the far side was read
     */
    private record Drawing(MirrorWindow window, List<Block> blocks, List<BlockState> states,
        long takenAt)
    {
    }

    /** Static state only. */
    private MirrorWindows()
    {
    }

    /** Forgets every view without sending anything, for a test or a reload. */
    public static void clear()
    {
        SENT.clear();
        VIEWING.clear();
        DRAWINGS.clear();
    }

    /**
     * One sweep of one window mirror whose banner is loaded.
     *
     * @param mirror
     *            the mirror
     * @param banner
     *            its banner block
     */
    static void tickOne(final QuantumMirror mirror, final Block banner)
    {
        // A wall banner only. A freestanding one facing a cardinal has no wall to open.
        final BlockFace facing =
            (banner.getBlockData() instanceof Directional wall) ? wall.getFacing() : null;
        final MirrorWindow window = MirrorWindow.of(mirror.banner(), facing, mirror.destination());
        final Drawing old = DRAWINGS.get(mirror.name());
        if ((old != null) && !old.window().equals(window))
        {
            // Re-pointed or re-hung: the old view comes back before a new one goes out.
            release(mirror);
        }
        final World far = (window == null) ? null
            : Bukkit.getWorld(mirror.destination().worldName());
        if (far == null)
        {
            release(mirror);
            return;
        }
        final World here = banner.getWorld();
        final long now = System.currentTimeMillis();
        final Map<UUID, Long> wasSent = SENT.getOrDefault(mirror.name(), Map.of());
        final Map<UUID, Long> nowSent = new HashMap<>();
        for (final Player player : here.getPlayers())
        {
            if (!sees(window, banner, player))
            {
                continue;
            }
            final Long last = wasSent.get(player.getUniqueId());
            final boolean due = (last == null) || ((now - last) >= RESEND_MILLIS);
            if (due)
            {
                player.sendBlockChanges(drawingFor(mirror, window, here, far, now).states());
            }
            nowSent.put(player.getUniqueId(), due ? now : last);
        }
        final Drawing drawing = DRAWINGS.get(mirror.name());
        for (final UUID id : wasSent.keySet())
        {
            if (!nowSent.containsKey(id))
            {
                takeBack(drawing, here, Bukkit.getPlayer(id));
            }
        }
        remember(mirror.name(), wasSent.keySet(), nowSent);
    }

    /**
     * Takes one mirror's view back from everybody shown it, and forgets it.
     *
     * @param mirror
     *            the mirror no longer being drawn
     */
    public static void release(final QuantumMirror mirror)
    {
        final Map<UUID, Long> sent = SENT.remove(mirror.name());
        final Drawing drawing = DRAWINGS.remove(mirror.name());
        if (sent == null)
        {
            return;
        }
        final World here = Bukkit.getWorld(mirror.banner().worldName());
        for (final UUID id : sent.keySet())
        {
            forgetViewing(id, mirror.name());
            if (here != null)
            {
                takeBack(drawing, here, Bukkit.getPlayer(id));
            }
        }
    }

    /** Takes every view back, as the plugin stops. */
    public static void restoreAll()
    {
        for (final QuantumMirror mirror : MirrorManager.all())
        {
            release(mirror);
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
        final Set<String> names = (id == null) ? null : VIEWING.get(id);
        if ((names == null) || (block == null))
        {
            return null;
        }
        for (final String name : names)
        {
            final QuantumMirror mirror = MirrorManager.byName(name);
            final Drawing drawing = DRAWINGS.get(name);
            if ((mirror != null) && (drawing != null)
                && drawing.window().isOpening(block.getX(), block.getY(), block.getZ())
                && mirror.banner().worldName().equals(block.getWorld().getName()))
            {
                return mirror;
            }
        }
        return null;
    }

    /** Whether a player is close enough, and on the right side of the wall, to be shown it. */
    private static boolean sees(final MirrorWindow window, final Block banner, final Player player)
    {
        final Location at = player.getLocation();
        final double radius = ConfigManager.getMirrorProximityRadius();
        return (at.distanceSquared(banner.getLocation()) <= (radius * radius))
            && window.inFront(at.getX(), at.getZ());
    }

    /** The mirror's drawing, read again from the far side if the last one is old. */
    private static Drawing drawingFor(final QuantumMirror mirror, final MirrorWindow window,
        final World here, final World far, final long now)
    {
        final Drawing cached = DRAWINGS.get(mirror.name());
        if ((cached != null) && ((now - cached.takenAt()) < RESAMPLE_MILLIS))
        {
            return cached;
        }
        final Drawing drawn = draw(mirror.banner(), window, here, far, now);
        DRAWINGS.put(mirror.name(), drawn);
        return drawn;
    }

    /** Reads the far side into a fresh drawing. */
    private static Drawing draw(final MirrorBlock banner, final MirrorWindow window,
        final World here, final World far, final long now)
    {
        final BlockData air = Bukkit.createBlockData(Material.AIR);
        final BlockData barrier = Bukkit.createBlockData(Material.BARRIER);
        final Canvas canvas = new Canvas(here);
        // Only where the banner's patterns can be sent back afterwards. On plain 1.20 it stays
        // hanging in front of the view.
        if (MirrorPackets.available())
        {
            canvas.put(banner.x(), banner.y(), banner.z(), air);
        }
        window.forEachOpening((x, y, z) -> canvas.put(x, y, z, barrier));
        final FarSide farSide = new FarSide(far, air);
        window.forEachShown((x, y, z, farX, farY, farZ) ->
            canvas.put(x, y, z, farSide.at(farX, farY, farZ)));
        return new Drawing(window, canvas.blocks, canvas.states, now);
    }

    /**
     * Whether a mirror's banner hangs on a wall, which is what makes it a window.
     *
     * @param banner
     *            the banner block
     * @return true for a wall banner; false for a freestanding one, which stays a banner
     */
    static boolean isWall(final Block banner)
    {
        return banner.getBlockData() instanceof Directional;
    }

    /** Sends one player the real blocks back, if they are still in that world to be sent them. */
    private static void takeBack(final Drawing drawing, final World here, final Player player)
    {
        if ((drawing == null) || (player == null) || !here.equals(player.getWorld()))
        {
            return;
        }
        final List<BlockState> truth = new ArrayList<>(drawing.blocks().size());
        for (final Block block : drawing.blocks())
        {
            // A chunk that has unloaded is one the client dropped too, and it gets a fresh copy.
            if (here.isChunkLoaded(block.getX() >> 4, block.getZ() >> 4))
            {
                truth.add(block.getState());
            }
        }
        player.sendBlockChanges(truth);
        // A block change carries no banner patterns or sign text, so those follow on their own.
        for (final BlockState state : truth)
        {
            if (state instanceof TileState tile)
            {
                MirrorPackets.send(player, tile.getLocation(), tile);
            }
        }
    }

    /** Replaces who a mirror was shown to, and keeps the click index in step. */
    private static void remember(final String name, final Set<UUID> was,
        final Map<UUID, Long> now)
    {
        was.forEach(id -> forgetViewing(id, name));
        now.keySet().forEach(id -> VIEWING.merge(id, Set.of(name), MirrorWindows::union));
        if (now.isEmpty())
        {
            SENT.remove(name);
        }
        else
        {
            SENT.put(name, now);
        }
    }

    private static void forgetViewing(final UUID id, final String name)
    {
        VIEWING.computeIfPresent(id, (key, names) ->
        {
            final Set<String> left = new HashSet<>(names);
            left.remove(name);
            return left.isEmpty() ? null : Set.copyOf(left);
        });
    }

    private static Set<String> union(final Set<String> one, final Set<String> other)
    {
        final Set<String> both = new HashSet<>(one);
        both.addAll(other);
        return Set.copyOf(both);
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

    /** Collects the blocks of one drawing, skipping any this world cannot draw. */
    private static final class Canvas
    {
        private final World here;
        private final int min;
        private final int max;
        private final List<Block> blocks = new ArrayList<>();
        private final List<BlockState> states = new ArrayList<>();

        Canvas(final World here)
        {
            this.here = here;
            this.min = here.getMinHeight();
            this.max = here.getMaxHeight();
        }

        void put(final int x, final int y, final int z, final BlockData data)
        {
            if ((y < min) || (y >= max) || !here.isChunkLoaded(x >> 4, z >> 4))
            {
                return;
            }
            final Block block = here.getBlockAt(x, y, z);
            final BlockState state = block.getState();
            try
            {
                state.setBlockData(data);
            }
            catch (final IllegalArgumentException refused)
            {
                // A block entity's state will not always take another block's data. That one
                // block shows as it really is.
                return;
            }
            blocks.add(block);
            states.add(state);
        }
    }
}
