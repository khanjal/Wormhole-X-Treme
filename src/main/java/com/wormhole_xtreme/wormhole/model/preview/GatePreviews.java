package com.wormhole_xtreme.wormhole.model.preview;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.LongSupplier;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Cell;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Palette;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Part;
import com.wormhole_xtreme.wormhole.logic.GateGrid;
import com.wormhole_xtreme.wormhole.model.MaterialGroup;
import com.wormhole_xtreme.wormhole.model.Stargate3DShape;
import com.wormhole_xtreme.wormhole.utils.HiddenEntities;

/**
 * Gate shapes shown full size to the player who asked, as block displays nobody else sees.
 *
 * <p>Displays have no hitbox, so a preview can be walked through and built into. They are not
 * saved, so a crash leaves none behind, and everything here is removed on quit, world change,
 * timeout and disable. Main thread only.
 */
public final class GatePreviews
{
    /** How far away a player can pick out a preview by looking at it. */
    private static final double LOOK_REACH = 64.0;

    /** Lit as if in daylight, so a preview reads the same underground as in the open. */
    private static final Display.Brightness FULL_BRIGHT = new Display.Brightness(15, 15);

    /** What happened when a preview was asked for. */
    public enum Shown
    {
        /** It stands in front of the player. */
        SHOWN,
        /** It would take the server past {@code gate-preview-max-blocks}. */
        OVER_LIMIT,
        /** The shape has no DHD to stand it by. */
        NO_DHD
    }

    static LongSupplier clock = System::currentTimeMillis;
    static Function<Material, BlockData> blockData = Bukkit::createBlockData;
    static Function<UUID, Player> online = Bukkit::getPlayer;

    private static final Map<UUID, List<GatePreview>> PREVIEWS = new HashMap<>();

    private GatePreviews() {}

    /**
     * Shows a shape in front of a player, alongside any previews they already have.
     *
     * @param owner
     *            who sees it
     * @param shape
     *            the shape
     * @param group
     *            its material group, or null for the shape's own materials
     * @return what happened
     */
    public static Shown show(final Player owner, final Stargate3DShape shape, final MaterialGroup group)
    {
        // Asking counts as using build commands even when this one cannot be shown.
        touch(owner.getUniqueId());
        final Location at = owner.getLocation();
        final GateGrid grid = GateBlueprint.inFrontOf(shape, at.getBlockX(), at.getBlockY(), at.getBlockZ(),
            GateBlueprint.facingOf(at.getYaw()));
        if (grid == null)
        {
            return Shown.NO_DHD;
        }
        final List<Cell> cells = GateBlueprint.of(shape, grid);
        if ((blocksShown() + cells.size()) > ConfigManager.getGatePreviewMaxBlocks())
        {
            return Shown.OVER_LIMIT;
        }
        final GatePreview preview = new GatePreview(at.getWorld(), grid, Palette.of(shape, group), cells);
        PREVIEWS.computeIfAbsent(owner.getUniqueId(), id -> new ArrayList<>()).add(preview);
        draw(owner, preview);
        touch(owner.getUniqueId());
        return Shown.SHOWN;
    }

    /**
     * Removes the preview a player is looking at, the nearest if the line of sight crosses several.
     *
     * @param owner
     *            whose preview
     * @return true if one was removed
     */
    public static boolean clearLookedAt(final Player owner)
    {
        final List<GatePreview> mine = PREVIEWS.get(owner.getUniqueId());
        if (mine == null)
        {
            return false;
        }
        final Location eye = owner.getEyeLocation();
        final Vector from = eye.toVector();
        final Vector direction = eye.getDirection();
        GatePreview nearest = null;
        double best = Double.MAX_VALUE;
        for (final GatePreview preview : mine)
        {
            final double distance = preview.world().equals(eye.getWorld())
                ? preview.distanceAlong(from, direction, LOOK_REACH) : -1.0;
            if ((distance >= 0.0) && (distance < best))
            {
                best = distance;
                nearest = preview;
            }
        }
        if (nearest == null)
        {
            touch(owner.getUniqueId());
            return false;
        }
        nearest.remove();
        mine.remove(nearest);
        forgetIfEmpty(owner.getUniqueId());
        touch(owner.getUniqueId());
        return true;
    }

    /**
     * Removes every preview a player has.
     *
     * @param owner
     *            whose previews
     * @return how many there were
     */
    public static int clearAll(final Player owner)
    {
        final List<GatePreview> mine = PREVIEWS.remove(owner.getUniqueId());
        if (mine == null)
        {
            return 0;
        }
        mine.forEach(GatePreview::remove);
        return mine.size();
    }

    /**
     * Removes a player's previews when they quit or change world.
     *
     * @param owner
     *            the player's id
     */
    public static void forget(final UUID owner)
    {
        final List<GatePreview> mine = PREVIEWS.remove(owner);
        if (mine != null)
        {
            mine.forEach(GatePreview::remove);
        }
    }

    /**
     * Removes any preview whose button is where a gate was just found, since the real blocks now
     * stand where it did.
     *
     * @param world
     *            the gate's world
     * @param x
     *            the pressed button's x
     * @param y
     *            its y
     * @param z
     *            its z
     */
    public static void builtAt(final World world, final int x, final int y, final int z)
    {
        final Iterator<Map.Entry<UUID, List<GatePreview>>> owners = PREVIEWS.entrySet().iterator();
        while (owners.hasNext())
        {
            final List<GatePreview> mine = owners.next().getValue();
            mine.removeIf(preview ->
            {
                final boolean built = preview.hasButtonAt(world, x, y, z);
                if (built)
                {
                    preview.remove();
                }
                return built;
            });
            if (mine.isEmpty())
            {
                owners.remove();
            }
        }
    }

    /**
     * Removes previews that have timed out, and puts back displays the server dropped with an
     * unloaded chunk once that chunk is loaded again.
     */
    public static void tick()
    {
        final long now = clock.getAsLong();
        final Iterator<Map.Entry<UUID, List<GatePreview>>> owners = PREVIEWS.entrySet().iterator();
        while (owners.hasNext())
        {
            final Map.Entry<UUID, List<GatePreview>> entry = owners.next();
            final Player owner = online.apply(entry.getKey());
            entry.getValue().removeIf(preview ->
            {
                if (now >= preview.expiresAt())
                {
                    preview.remove();
                    return true;
                }
                if ((owner != null) && preview.world().equals(owner.getWorld()))
                {
                    draw(owner, preview);
                }
                return false;
            });
            if (entry.getValue().isEmpty())
            {
                owners.remove();
            }
        }
    }

    /** Removes every preview, on disable. */
    public static void restoreAll()
    {
        PREVIEWS.values().forEach(mine -> mine.forEach(GatePreview::remove));
        PREVIEWS.clear();
    }

    /**
     * @param owner
     *            a player's id
     * @return how many previews they have
     */
    public static int countOf(final UUID owner)
    {
        final List<GatePreview> mine = PREVIEWS.get(owner);
        return (mine == null) ? 0 : mine.size();
    }

    /** @return how many blocks every preview on the server shows between them */
    static int blocksShown()
    {
        return PREVIEWS.values().stream().flatMap(List::stream).mapToInt(p -> p.cells().size()).sum();
    }

    /** @return a player's previews, oldest first, for tests */
    static List<GatePreview> of(final UUID owner)
    {
        return PREVIEWS.getOrDefault(owner, List.of());
    }

    /** Forgets everything without removing anything, and puts the seams back, for tests. */
    static void clear()
    {
        PREVIEWS.clear();
        clock = System::currentTimeMillis;
        blockData = Bukkit::createBlockData;
        online = Bukkit::getPlayer;
    }

    /** Pushes the timeout of every preview a player has back to its full length. */
    private static void touch(final UUID owner)
    {
        final long until = clock.getAsLong() + (ConfigManager.getGatePreviewMinutes() * 60_000L);
        PREVIEWS.getOrDefault(owner, List.of()).forEach(preview -> preview.expiresAt(until));
    }

    private static void forgetIfEmpty(final UUID owner)
    {
        final List<GatePreview> mine = PREVIEWS.get(owner);
        if ((mine != null) && mine.isEmpty())
        {
            PREVIEWS.remove(owner);
        }
    }

    /** Spawns each display that is missing and whose chunk is loaded. */
    private static void draw(final Player owner, final GatePreview preview)
    {
        final World world = preview.world();
        for (int i = 0; i < preview.cells().size(); i++)
        {
            final BlockDisplay existing = preview.displays().get(i);
            final Cell cell = preview.cells().get(i);
            if (((existing != null) && existing.isValid()) || !world.isChunkLoaded(cell.x() >> 4, cell.z() >> 4))
            {
                continue;
            }
            final BlockData data = blockDataFor(preview, cell);
            preview.displays().set(i, HiddenEntities.spawnFor(WormholeXTreme.getThisPlugin(), owner,
                new Location(world, cell.x(), cell.y(), cell.z()), BlockDisplay.class, display ->
                {
                    display.setBlock(data);
                    display.setBrightness(FULL_BRIGHT);
                }));
        }
    }

    /** What a cell is drawn as, with the button and sign turned to face the builder. */
    static BlockData blockDataFor(final GatePreview preview, final Cell cell)
    {
        final BlockData data = blockData.apply(preview.palette().materialOf(cell));
        final boolean faces = (cell.part() == Part.BUTTON) || (cell.part() == Part.DIAL_SIGN);
        if (faces && (data instanceof Directional directional)
            && directional.getFaces().contains(preview.grid().facing()))
        {
            directional.setFacing(preview.grid().facing());
        }
        return data;
    }
}
