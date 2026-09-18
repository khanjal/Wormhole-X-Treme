package com.wormhole_xtreme.wormhole.model.preview;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.LongSupplier;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Cell;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Palette;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Part;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Role;
import com.wormhole_xtreme.wormhole.logic.GateGrid;
import com.wormhole_xtreme.wormhole.logic.StargateHelper;
import com.wormhole_xtreme.wormhole.model.GateSounds;
import com.wormhole_xtreme.wormhole.model.MaterialGroup;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.Stargate3DShape;
import com.wormhole_xtreme.wormhole.model.WooshSequence;
import com.wormhole_xtreme.wormhole.utils.HiddenEntities;
import com.wormhole_xtreme.wormhole.utils.MaterialUtils;
import com.wormhole_xtreme.wormhole.utils.Sounds;

/**
 * Gate shapes shown full size to the player who asked, as block displays nobody else sees.
 *
 * <p>Displays have no hitbox, so a preview can be walked through and built into. They are not
 * saved, so a crash leaves none behind, and everything here is removed on quit, world change,
 * timeout and disable. A preview can be dialled, given an iris, redressed and have its DHD hidden,
 * all drawn for its owner alone. Main thread only.
 */
public final class GatePreviews
{
    /** How far away a player can pick out a preview by looking at it. */
    private static final double LOOK_REACH = 64.0;

    /** Lit as if in daylight, so a preview reads the same underground as in the open. */
    private static final Display.Brightness FULL_BRIGHT = new Display.Brightness(15, 15);

    /** One click can arrive as two events; a second inside this is the same press. */
    private static final long PRESS_GAP_MILLIS = 300L;

    /** The size of what the preview's button answers clicks on. */
    private static final float BUTTON_SIZE = 0.6f;

    /** How big the guide draws a block still to place. */
    private static final float MISSING_SCALE = 0.5f;

    /** How big the guide draws its outline over a wrong block, just clear of the block's faces. */
    private static final float WRONG_SCALE = 1.02f;

    /** What the guide marks a block in the opening with. */
    private static final Material IN_THE_WAY = Material.RED_STAINED_GLASS;

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

    /** What a control did to the preview being looked at. */
    public enum Control
    {
        /** The player is not looking at one of their previews. */
        NOT_LOOKING,
        /** The chevrons have begun to light. */
        DIALLING,
        /** The wormhole closed and the chevrons went out. */
        SHUT_DOWN,
        /** The iris closed. */
        IRIS_CLOSED,
        /** The iris opened. */
        IRIS_OPENED,
        /** Its materials changed. */
        CHANGED,
        /** That material is not a block. */
        NOT_A_BLOCK,
        /** The shape may not be built in that group. */
        NOT_IN_GROUP,
        /** The DHD is hidden. */
        DHD_HIDDEN,
        /** The DHD is shown again. */
        DHD_SHOWN,
        /** The chevrons are drawn as frame, the way a gate built without chevron blocks looks. */
        CHEVRONS_PLAIN,
        /** The chevrons are drawn in the chevron material again. */
        CHEVRONS_SHOWN,
        /** The preview marks what is still to place and what is wrong. */
        GUIDE_ON,
        /** The preview shows the whole gate again. */
        GUIDE_OFF
    }

    /**
     * What the preview looked at takes to build.
     *
     * @param shape
     *            its shape's name
     * @param needs
     *            each material, with how many are still to place
     * @param blocked
     *            how many blocks stand in its opening
     * @param detectable
     *            whether any shape or material group builds a frame from its frame material, without
     *            which a gate built to it is never found
     * @param frame
     *            its frame material
     */
    public record Materials(String shape, List<BuildGuide.Need> needs, int blocked, boolean detectable,
        Material frame) {}

    /**
     * Which layers of the preview looked at are shown.
     *
     * @param shown
     *            how many, from the first; 0 for all of them
     * @param of
     *            how many layers have something built in them
     * @param valid
     *            false if the layer asked for is not one of them, and nothing changed
     */
    public record Layers(int shown, int of, boolean valid) {}

    /** Asks {@link #layers} for the next layer, or all of them again after the last. */
    public static final int NEXT_LAYER = -1;

    /** Asks {@link #layers} for every layer. */
    public static final int ALL_LAYERS = 0;

    /** What became of {@code gate build -place}. */
    public enum Outcome
    {
        /** The player is not looking at one of their previews. */
        NOT_LOOKING,
        /** No material group builds a frame from its frame material, so the gate would not be found. */
        NOT_FINDABLE,
        /** Part of it is in a chunk that is not loaded. */
        NOT_LOADED,
        /** Part of it is outside the world border. */
        OUTSIDE_BORDER,
        /** Something stands where it would go, and nothing was placed. */
        IN_THE_WAY,
        /** Its blocks were placed, but detection did not find a gate in them. */
        NOT_FOUND,
        /** Its blocks were placed and the gate found. */
        PLACED
    }

    /**
     * What {@code gate build -place} did.
     *
     * @param outcome
     *            what became of it
     * @param inTheWay
     *            what stands in its way, as "block at x y z", when that is why
     * @param gate
     *            the gate found, when placed
     * @param button
     *            its DHD button, when placed
     */
    public record Placed(Outcome outcome, List<String> inTheWay, com.wormhole_xtreme.wormhole.model.Stargate gate,
        org.bukkit.block.Block button) {}

    /** Finds the gate a DHD button belongs to; tests stand in for detection. */
    interface Detector
    {
        com.wormhole_xtreme.wormhole.model.Stargate find(org.bukkit.block.Block button, org.bukkit.block.BlockFace facing,
            Stargate3DShape shape);
    }

    /** Whether a block belongs to a gate or ring already there; tests stand in for the indexes. */
    interface Occupied
    {
        boolean at(World world, int x, int y, int z);
    }

    /** What {@code gate build -share} did. */
    public enum Shared
    {
        /** The player is not looking at one of their previews. */
        NOT_LOOKING,
        /** They asked to share it with themselves. */
        SELF,
        /** It is shown to that player now. */
        SHARED,
        /** It is no longer shown to that player. */
        UNSHARED,
        /** It is shown to everyone in its world now. */
        SHARED_ALL,
        /** It is no longer shown to everyone in its world. */
        UNSHARED_ALL
    }

    /**
     * Who the preview looked at is shown to.
     *
     * @param everyone
     *            whether everyone in its world sees it
     * @param names
     *            the players it is shared with by name
     */
    public record Audience(boolean everyone, List<String> names) {}

    /** Runs a step of a dial later; tests step a dial by hand instead. */
    interface Later
    {
        BukkitTask after(long ticks, Runnable step);
    }

    static LongSupplier clock = System::currentTimeMillis;
    static Function<Material, BlockData> blockData = Bukkit::createBlockData;
    static Function<UUID, Player> online = Bukkit::getPlayer;
    static Later later = GatePreviews::schedule;
    static Detector detector = StargateHelper::checkStargate;
    static Occupied occupied = PreviewPlacer::occupied;

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
        final Location at = owner.getLocation();
        return stand(owner, shape, group, at.getWorld(), GateBlueprint.inFrontOf(shape, at.getBlockX(), at.getBlockY(),
            at.getBlockZ(), GateBlueprint.facingOf(at.getYaw())));
    }

    /**
     * Shows a shape built to a DHD button or lever already placed, standing where a gate detected from
     * it would, so a build can be picked up again.
     *
     * @param owner
     *            who sees it
     * @param shape
     *            the shape
     * @param group
     *            its material group, or null for the shape's own materials
     * @param dhd
     *            the button or lever
     * @param facing
     *            the way it faces, away from the block it hangs on
     * @return what happened
     */
    public static Shown showOn(final Player owner, final Stargate3DShape shape, final MaterialGroup group,
        final org.bukkit.block.Block dhd, final org.bukkit.block.BlockFace facing)
    {
        final GateGrid grid = GateGrid.fromActivationHolder(shape, dhd.getX() - facing.getModX(), dhd.getY(),
            dhd.getZ() - facing.getModZ(), facing);
        return stand(owner, shape, group, dhd.getWorld(), grid);
    }

    private static Shown stand(final Player owner, final Stargate3DShape shape, final MaterialGroup group,
        final World world, final GateGrid grid)
    {
        // Asking counts as using build commands even when this one cannot be shown.
        touch(owner.getUniqueId());
        if (grid == null)
        {
            return Shown.NO_DHD;
        }
        final GatePreview preview = new GatePreview(world, shape, grid, Palette.of(shape, group),
            GateBlueprint.of(shape, grid), GateBlueprint.openingOf(shape, grid), GateBlueprint.wooshOf(shape, grid));
        // The classic gate: a Standard palette draws its chevrons as frame, as the original gates
        // were built, until -chevrons shows them. A shape that pins its own chevrons shows them.
        preview.plainChevrons(classic(shape, group));
        if ((blocksShown() + preview.size()) > ConfigManager.getGatePreviewMaxBlocks())
        {
            return Shown.OVER_LIMIT;
        }
        PREVIEWS.computeIfAbsent(owner.getUniqueId(), id -> new ArrayList<>()).add(preview);
        draw(owner, preview);
        touch(owner.getUniqueId());
        return Shown.SHOWN;
    }

    /**
     * Whether a preview starts with its chevrons drawn as plain frame: the Standard palette, or none
     * named, on a shape that does not pin its own chevron material.
     */
    static boolean classic(final Stargate3DShape shape, final MaterialGroup group)
    {
        return (shape.getShapeChevronMaterial() == null)
            && ((group == null) || "Standard".equalsIgnoreCase(group.getName()));
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
        touch(owner.getUniqueId());
        final GatePreview preview = lookedAt(owner);
        if (preview == null)
        {
            return false;
        }
        takeBackAll(owner, preview);
        preview.remove();
        PREVIEWS.get(owner.getUniqueId()).remove(preview);
        forgetIfEmpty(owner.getUniqueId());
        return true;
    }

    /**
     * Dials the preview a player is looking at, or shuts it down if it is dialling or open.
     *
     * @param owner
     *            whose preview
     * @return what happened
     */
    public static Control activate(final Player owner)
    {
        touch(owner.getUniqueId());
        final GatePreview preview = lookedAt(owner);
        return (preview == null) ? Control.NOT_LOOKING : toggleDial(owner, preview);
    }

    /**
     * Closes the iris of the preview a player is looking at, or opens it.
     *
     * @param owner
     *            whose preview
     * @return what happened
     */
    public static Control iris(final Player owner)
    {
        touch(owner.getUniqueId());
        final GatePreview preview = lookedAt(owner);
        if (preview == null)
        {
            return Control.NOT_LOOKING;
        }
        preview.irisClosed(!preview.irisClosed());
        if (preview.irisClosed())
        {
            takeBack(owner, preview, preview.opening());
        }
        sound(owner, preview, preview.irisClosed() ? ConfigManager.getGateSoundIrisClose() : ConfigManager.getGateSoundIrisOpen(),
            1.0f);
        restyle(preview);
        draw(owner, preview);
        return preview.irisClosed() ? Control.IRIS_CLOSED : Control.IRIS_OPENED;
    }

    /**
     * Redresses the preview a player is looking at in a material group.
     *
     * @param owner
     *            whose preview
     * @param group
     *            the group
     * @return what happened
     */
    public static Control material(final Player owner, final MaterialGroup group)
    {
        touch(owner.getUniqueId());
        final GatePreview preview = lookedAt(owner);
        if (preview == null)
        {
            return Control.NOT_LOOKING;
        }
        if (!preview.shape().acceptsMaterialGroup(group.getName()))
        {
            return Control.NOT_IN_GROUP;
        }
        preview.palette(Palette.of(preview.shape(), group));
        restyle(preview);
        draw(owner, preview);
        return Control.CHANGED;
    }

    /**
     * Changes one material of the preview a player is looking at.
     *
     * @param owner
     *            whose preview
     * @param role
     *            which material
     * @param material
     *            what it becomes
     * @return what happened
     */
    public static Control material(final Player owner, final Role role, final Material material)
    {
        touch(owner.getUniqueId());
        final GatePreview preview = lookedAt(owner);
        if (preview == null)
        {
            return Control.NOT_LOOKING;
        }
        if (!isBlock(material))
        {
            return Control.NOT_A_BLOCK;
        }
        preview.palette(preview.palette().with(role, material));
        restyle(preview);
        draw(owner, preview);
        return Control.CHANGED;
    }

    /**
     * Hides the DHD of the preview a player is looking at, or shows it again.
     *
     * @param owner
     *            whose preview
     * @return what happened
     */
    public static Control toggleDhd(final Player owner)
    {
        touch(owner.getUniqueId());
        final GatePreview preview = lookedAt(owner);
        if (preview == null)
        {
            return Control.NOT_LOOKING;
        }
        preview.dhdHidden(!preview.dhdHidden());
        draw(owner, preview);
        return preview.dhdHidden() ? Control.DHD_HIDDEN : Control.DHD_SHOWN;
    }

    /**
     * Draws the chevrons of the preview a player is looking at as frame, or in the chevron material
     * again: a group's chevron blocks are optional, and a gate can be built without them.
     *
     * @param owner
     *            whose preview
     * @return what happened
     */
    public static Control toggleChevrons(final Player owner)
    {
        touch(owner.getUniqueId());
        final GatePreview preview = lookedAt(owner);
        if (preview == null)
        {
            return Control.NOT_LOOKING;
        }
        preview.plainChevrons(!preview.plainChevrons());
        restyle(preview);
        return preview.plainChevrons() ? Control.CHEVRONS_PLAIN : Control.CHEVRONS_SHOWN;
    }

    /**
     * Turns the build guide on the preview a player is looking at on or off. With it on, a block still
     * to place is drawn small, a wrong block is outlined in red, a placed block is not drawn, and
     * anything in the opening is marked.
     *
     * @param owner
     *            whose preview
     * @return what happened
     */
    public static Control guide(final Player owner)
    {
        touch(owner.getUniqueId());
        final GatePreview preview = lookedAt(owner);
        if (preview == null)
        {
            return Control.NOT_LOOKING;
        }
        preview.guide(!preview.guide());
        preview.finished(false);
        draw(owner, preview);
        return preview.guide() ? Control.GUIDE_ON : Control.GUIDE_OFF;
    }

    /**
     * Shows the preview a player is looking at a layer at a time: the layers up to one, the next, or all
     * of them.
     *
     * @param owner
     *            whose preview
     * @param layers
     *            how many layers to show from the first, {@link #NEXT_LAYER} or {@link #ALL_LAYERS}
     * @return which are shown, or null if they are not looking at one of their previews
     */
    public static Layers layers(final Player owner, final int layers)
    {
        touch(owner.getUniqueId());
        final GatePreview preview = lookedAt(owner);
        if (preview == null)
        {
            return null;
        }
        final int of = preview.layerCount();
        if (layers > of)
        {
            return new Layers(preview.layersShown(), of, false);
        }
        if (layers == NEXT_LAYER)
        {
            preview.layersShown((preview.layersShown() >= of) ? ALL_LAYERS : preview.layersShown() + 1);
        }
        else
        {
            preview.layersShown(Math.max(ALL_LAYERS, layers));
        }
        draw(owner, preview);
        return new Layers(preview.layersShown(), of, true);
    }

    /**
     * Builds the preview a player is looking at for real: its frame, chevrons, DHD and button, in the
     * materials it shows, then finds the gate the way a pressed button does. Nothing is placed if any
     * block it needs is taken, belongs to a gate or ring, or cannot be reached.
     *
     * @param owner
     *            whose preview
     * @return what happened
     */
    public static Placed place(final Player owner)
    {
        touch(owner.getUniqueId());
        final GatePreview preview = lookedAt(owner);
        return (preview == null) ? new Placed(Outcome.NOT_LOOKING, List.of(), null, null)
            : PreviewPlacer.place(preview);
    }

    /**
     * Shows the preview a player is looking at to another player, or stops. They see it change, dial
     * and guide as its owner does, and cannot change it.
     *
     * @param owner
     *            whose preview
     * @param with
     *            who to show it to
     * @return what happened
     */
    public static Shared share(final Player owner, final Player with)
    {
        touch(owner.getUniqueId());
        final GatePreview preview = lookedAt(owner);
        if (preview == null)
        {
            return Shared.NOT_LOOKING;
        }
        if (with.getUniqueId().equals(owner.getUniqueId()))
        {
            return Shared.SELF;
        }
        final boolean sharing = !preview.sharedWith().containsKey(with.getUniqueId());
        if (sharing)
        {
            preview.sharedWith().put(with.getUniqueId(), with.getName());
        }
        else
        {
            preview.sharedWith().remove(with.getUniqueId());
        }
        showToAudience(owner, preview);
        return sharing ? Shared.SHARED : Shared.UNSHARED;
    }

    /**
     * Shows the preview a player is looking at to everyone in its world, or stops.
     *
     * @param owner
     *            whose preview
     * @return what happened
     */
    public static Shared shareAll(final Player owner)
    {
        touch(owner.getUniqueId());
        final GatePreview preview = lookedAt(owner);
        if (preview == null)
        {
            return Shared.NOT_LOOKING;
        }
        preview.sharedWithAll(!preview.sharedWithAll());
        showToAudience(owner, preview);
        return preview.sharedWithAll() ? Shared.SHARED_ALL : Shared.UNSHARED_ALL;
    }

    /**
     * @param owner
     *            whose preview
     * @return who the preview they are looking at is shown to, or null if they are not looking at one
     */
    public static Audience audience(final Player owner)
    {
        touch(owner.getUniqueId());
        final GatePreview preview = lookedAt(owner);
        return (preview == null) ? null
            : new Audience(preview.sharedWithAll(), List.copyOf(preview.sharedWith().values()));
    }

    /**
     * Counts what the preview a player is looking at takes to build, and what of it is in place.
     *
     * @param owner
     *            whose preview
     * @return the count, or null if they are not looking at one of their previews
     */
    public static Materials materials(final Player owner)
    {
        touch(owner.getUniqueId());
        final GatePreview preview = lookedAt(owner);
        if (preview == null)
        {
            return null;
        }
        final List<BuildGuide.Need> needs = BuildGuide.needs(preview.cells(), preview.palette(),
            preview.drawnPalette(), cell -> typeAt(preview.world(), cell));
        final int blocked = (int) preview.opening().stream()
            .filter(cell -> BuildGuide.blocksOpening(typeAt(preview.world(), cell))).count();
        final Material frame = preview.palette().structure();
        return new Materials(preview.shape().getShapeName(), needs, blocked,
            StargateHelper.isPossibleGateFrameMaterial(frame), frame);
    }

    /**
     * Redraws, on the next tick, every preview a changed block stands in: the guide judges it again,
     * and a real button placed on the preview's takes clicks from the preview's own.
     *
     * @param world
     *            the block's world
     * @param x
     *            its x
     * @param y
     *            its y
     * @param z
     *            its z
     */
    public static void blockChanged(final World world, final int x, final int y, final int z)
    {
        PREVIEWS.forEach((id, mine) -> mine.forEach(preview ->
        {
            if (!preview.recheckQueued() && preview.contains(world, x, y, z))
            {
                preview.recheckQueued(true);
                later.after(1L, () -> recheck(id, preview));
            }
        }));
    }

    private static void recheck(final UUID id, final GatePreview preview)
    {
        preview.recheckQueued(false);
        final Player owner = online.apply(id);
        if ((owner != null) && PREVIEWS.getOrDefault(id, List.of()).contains(preview)
            && preview.world().equals(owner.getWorld()))
        {
            draw(owner, preview);
        }
    }

    /**
     * Answers a click on a preview's button, which dials or shuts down that preview.
     *
     * @param player
     *            who clicked
     * @param clicked
     *            what they clicked
     * @return true if it was a preview's button, so the click goes no further
     */
    public static boolean pressed(final Player player, final Entity clicked)
    {
        if (!(clicked instanceof Interaction))
        {
            return false;
        }
        final List<GatePreview> mine = PREVIEWS.get(player.getUniqueId());
        final GatePreview preview = (mine == null) ? null
            : mine.stream().filter(p -> clicked.equals(p.button())).findFirst().orElse(null);
        if (preview == null)
        {
            return PREVIEWS.values().stream().flatMap(List::stream).anyMatch(p -> clicked.equals(p.button()));
        }
        final long now = clock.getAsLong();
        if ((now - preview.lastPressed()) >= PRESS_GAP_MILLIS)
        {
            preview.lastPressed(now);
            touch(player.getUniqueId());
            toggleDial(player, preview);
        }
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
        mine.forEach(preview ->
        {
            takeBackAll(owner, preview);
            preview.remove();
        });
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
            mine.forEach(preview ->
            {
                takeBackAll(null, preview);
                preview.remove();
            });
        }
        // Shown again when they are back; a relog forgets what the client was shown.
        PREVIEWS.values().forEach(theirs -> theirs.forEach(preview -> preview.shownTo().remove(owner)));
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
            final Map.Entry<UUID, List<GatePreview>> entry = owners.next();
            final List<GatePreview> mine = entry.getValue();
            mine.removeIf(preview ->
            {
                final boolean built = preview.hasButtonAt(world, x, y, z);
                if (built)
                {
                    takeBackAll(online.apply(entry.getKey()), preview);
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
                    takeBackAll(owner, preview);
                    preview.remove();
                    return true;
                }
                if ((owner != null) && preview.world().equals(owner.getWorld()))
                {
                    showToAudience(owner, preview);
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
        PREVIEWS.forEach((id, mine) -> mine.forEach(preview ->
        {
            takeBackAll(online.apply(id), preview);
            preview.remove();
        }));
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

    /** @return how many blocks every preview on the server may show between them */
    static int blocksShown()
    {
        return PREVIEWS.values().stream().flatMap(List::stream).mapToInt(GatePreview::size).sum();
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
        later = GatePreviews::schedule;
        detector = StargateHelper::checkStargate;
        occupied = PreviewPlacer::occupied;
    }

    /**
     * Plays the next step of a dial: the next chevron wave, then the kawoosh in the order
     * {@link WooshSequence} gives a real gate, then the open wormhole.
     *
     * @param owner
     *            whose preview
     * @param preview
     *            the preview dialling
     */
    static void step(final Player owner, final GatePreview preview)
    {
        preview.dialling(null);
        if (preview.litWaves() < preview.lastWave())
        {
            preview.litWaves(preview.litWaves() + 1);
            sound(owner, preview, ConfigManager.getGateSoundChevron(),
                GateSounds.chevronPitch(preview.litWaves(), preview.lastWave()));
            if (preview.litWaves() == preview.lastWave())
            {
                sound(owner, preview, ConfigManager.getGateSoundLock(), GateSounds.LOCK_PITCH);
            }
            restyle(preview);
            // Held after the last chevron as a real gate holds it.
            next(owner, preview, (preview.litWaves() < preview.lastWave())
                ? preview.shape().getShapeLightTicks() : Stargate.LAST_CHEVRON_PAUSE_TICKS);
            return;
        }
        final int stage = preview.wooshStage();
        final int steps = preview.lastWoosh();
        if ((stage == 0) && (steps > 0))
        {
            sound(owner, preview, ConfigManager.getGateSoundKawoosh(), GateSounds.KAWOOSH_PITCH);
        }
        final WooshSequence.Step now = WooshSequence.at(stage, steps);
        preview.wooshStage(stage + 1);
        if (now.move() == WooshSequence.Move.OUT)
        {
            send(owner, preview, wooshStep(preview, now.index()));
        }
        else if (now.move() == WooshSequence.Move.BACK)
        {
            takeBack(owner, preview, wooshStep(preview, now.index()));
        }
        // Settled in the same step as the shallowest woosh step is taken back, as a real gate does.
        if ((now.move() == WooshSequence.Move.SETTLE)
            || (WooshSequence.at(stage + 1, steps).move() == WooshSequence.Move.SETTLE))
        {
            preview.open(true);
            draw(owner, preview);
            return;
        }
        next(owner, preview, preview.shape().getShapeWooshTicks());
    }

    private static void next(final Player owner, final GatePreview preview, final long ticks)
    {
        preview.dialling(later.after(Math.max(1L, ticks), () -> step(owner, preview)));
    }

    private static BukkitTask schedule(final long ticks, final Runnable step)
    {
        return WormholeXTreme.getScheduler().runTaskLater(WormholeXTreme.getThisPlugin(), step, ticks);
    }

    private static Control toggleDial(final Player owner, final GatePreview preview)
    {
        if ((preview.dialling() != null) || (preview.litWaves() > 0) || preview.open())
        {
            preview.stopDialling();
            preview.litWaves(0);
            preview.wooshStage(0);
            preview.open(false);
            takeBack(owner, preview, preview.woosh());
            takeBack(owner, preview, preview.opening());
            sound(owner, preview, ConfigManager.getGateSoundClose(), 1.0f);
            restyle(preview);
            draw(owner, preview);
            return Control.SHUT_DOWN;
        }
        sound(owner, preview, ConfigManager.getGateSoundActivate(), 1.0f);
        next(owner, preview, preview.shape().getShapeLightTicks());
        return Control.DIALLING;
    }

    /** The woosh's cells at one step, counted from 0 as {@link WooshSequence} does; the shape's W# from 1. */
    private static List<Cell> wooshStep(final GatePreview preview, final int index)
    {
        return preview.woosh().stream().filter(cell -> cell.wave() == (index + 1)).toList();
    }

    /**
     * Sends everyone watching the wormhole's material at some cells, as fake blocks: a block display
     * draws no liquid, and a real gate draws its wormhole the same way.
     */
    private static void send(final Player owner, final GatePreview preview, final List<Cell> cells)
    {
        final List<Player> watching = watching(owner, preview);
        for (final Cell cell : cells)
        {
            preview.sent().add(GatePreview.key(cell));
        }
        watching.forEach(viewer -> sendTo(viewer, preview, cells));
    }

    /** Sends one viewer the wormhole at some cells. */
    private static void sendTo(final Player viewer, final GatePreview preview, final List<Cell> cells)
    {
        final BlockData portal = blockData.apply(preview.palette().portal());
        for (final Cell cell : cells)
        {
            viewer.sendBlockChange(new Location(preview.world(), cell.x(), cell.y(), cell.z()), portal);
        }
    }

    /**
     * Shows everyone watching what really stands at cells a fake block was sent to. An unloaded chunk
     * is left alone: the client gets it afresh when it loads.
     */
    private static void takeBack(final Player owner, final GatePreview preview, final List<Cell> cells)
    {
        final List<Player> watching = watching(owner, preview);
        final List<Cell> sent = cells.stream().filter(cell -> preview.sent().remove(GatePreview.key(cell))).toList();
        watching.forEach(viewer -> takeBackFrom(viewer, preview, sent));
    }

    /** Shows one viewer what really stands at some cells, where their chunk is loaded. */
    private static void takeBackFrom(final Player viewer, final GatePreview preview, final List<Cell> cells)
    {
        for (final Cell cell : cells)
        {
            if (preview.world().isChunkLoaded(cell.x() >> 4, cell.z() >> 4))
            {
                viewer.sendBlockChange(new Location(preview.world(), cell.x(), cell.y(), cell.z()),
                    preview.world().getBlockAt(cell.x(), cell.y(), cell.z()).getBlockData());
            }
        }
    }

    /** Takes back every fake block a preview sent, from whoever is still here to see them. */
    private static void takeBackAll(final Player owner, final GatePreview preview)
    {
        takeBack(owner, preview, preview.woosh());
        takeBack(owner, preview, preview.opening());
    }

    /** The cells a fake block stands at now. */
    private static List<Cell> sentCells(final GatePreview preview)
    {
        final List<Cell> cells = new ArrayList<>();
        for (final List<Cell> part : List.of(preview.woosh(), preview.opening()))
        {
            part.stream().filter(cell -> preview.sent().contains(GatePreview.key(cell))).forEach(cells::add);
        }
        return cells;
    }

    /**
     * The owner, if given, and everyone the preview is shown to, who are online and in its world.
     *
     * @param owner
     *            its owner, or null when they are not here
     */
    private static List<Player> watching(final Player owner, final GatePreview preview)
    {
        final List<Player> watching = new ArrayList<>();
        if ((owner != null) && preview.world().equals(owner.getWorld()))
        {
            watching.add(owner);
        }
        for (final UUID id : preview.shownTo())
        {
            final Player viewer = online.apply(id);
            if ((viewer != null) && preview.world().equals(viewer.getWorld()))
            {
                watching.add(viewer);
            }
        }
        return watching;
    }

    /**
     * Brings who is shown the preview in line with who it is shared with: shows everything to anybody
     * new, and takes it all back from anybody gone or no longer shared with.
     */
    static void showToAudience(final Player owner, final GatePreview preview)
    {
        final Set<UUID> wanted = new LinkedHashSet<>(preview.sharedWith().keySet());
        if (preview.sharedWithAll())
        {
            preview.world().getPlayers().forEach(player -> wanted.add(player.getUniqueId()));
        }
        wanted.remove(owner.getUniqueId());
        wanted.removeIf(id ->
        {
            final Player viewer = online.apply(id);
            return (viewer == null) || !preview.world().equals(viewer.getWorld());
        });
        for (final UUID id : new ArrayList<>(preview.shownTo()))
        {
            if (!wanted.contains(id))
            {
                preview.shownTo().remove(id);
                final Player gone = online.apply(id);
                if ((gone != null) && preview.world().equals(gone.getWorld()))
                {
                    preview.standingDisplays().forEach(display -> gone.hideEntity(WormholeXTreme.getThisPlugin(), display));
                    takeBackFrom(gone, preview, sentCells(preview));
                }
            }
        }
        for (final UUID id : wanted)
        {
            if (preview.shownTo().add(id))
            {
                final Player viewer = online.apply(id);
                preview.standingDisplays().forEach(display -> viewer.showEntity(WormholeXTreme.getThisPlugin(), display));
                sendTo(viewer, preview, sentCells(preview));
            }
        }
    }

    /** The owner's preview their line of sight meets first, or null. */
    private static GatePreview lookedAt(final Player owner)
    {
        final List<GatePreview> mine = PREVIEWS.get(owner.getUniqueId());
        if (mine == null)
        {
            return null;
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
        return nearest;
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

    private static void sound(final Player owner, final GatePreview preview, final String sound, final float pitch)
    {
        if (ConfigManager.isGateSoundsEnabled())
        {
            // Sized like the real gate this shape would build.
            final double scale = GateSounds.scaleOf(preview.shape());
            final float volume = ConfigManager.getGateSoundVolume() * GateSounds.sizeVolume(scale);
            final float scaledPitch = pitch * GateSounds.sizePitch(scale);
            watching(owner, preview).forEach(viewer -> Sounds.playTo(viewer, sound, volume, scaledPitch));
        }
    }

    /**
     * Brings the entities in line with what the preview should show: spawns what is missing where
     * its chunk is loaded, and removes what should not be there.
     */
    private static void draw(final Player owner, final GatePreview preview)
    {
        final World world = preview.world();
        final boolean built = drawFrame(owner, preview);
        final boolean clear = drawInTheWay(owner, preview);
        if (preview.guide())
        {
            if (built && clear && !preview.finished())
            {
                owner.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString()
                    + PreviewText.good(preview.shape().getShapeName() + " is built!") + " Press its button to check it.");
            }
            preview.finished(built && clear);
        }
        for (int i = 0; i < preview.opening().size(); i++)
        {
            if (!preview.openingFilled())
            {
                GatePreview.removeAt(preview.openingDisplays(), i);
                continue;
            }
            spawnMissing(owner, preview, preview.openingDisplays(), i, world, preview.opening().get(i), openingData(preview));
        }
        if (preview.open() && !preview.irisClosed())
        {
            send(owner, preview, preview.opening());
        }
        drawButton(owner, preview);
    }

    /**
     * Draws the frame, DHD and sign as the guide says, or whole without it.
     *
     * @return whether every block the gate is found by is in place
     */
    private static boolean drawFrame(final Player owner, final GatePreview preview)
    {
        boolean built = true;
        for (int i = 0; i < preview.cells().size(); i++)
        {
            final Cell cell = preview.cells().get(i);
            final BuildGuide.State state = preview.guide() ? guideState(preview, cell) : null;
            // The dial sign is optional: a gate is found without one.
            built &= (state == BuildGuide.State.PLACED) || (cell.part() == Part.DIAL_SIGN);
            if (!preview.showing(cell) || (state == BuildGuide.State.PLACED))
            {
                GatePreview.removeAt(preview.displays(), i);
                continue;
            }
            spawnMissing(owner, preview, preview.displays(), i, preview.world(), cell, dataFor(preview, cell));
            styleForGuide(preview.displays().get(i), state);
        }
        return built;
    }

    /**
     * Marks, with the guide on, whatever stands in the opening.
     *
     * @return whether the opening is clear
     */
    private static boolean drawInTheWay(final Player owner, final GatePreview preview)
    {
        boolean clear = true;
        for (int i = 0; i < preview.opening().size(); i++)
        {
            final Cell cell = preview.opening().get(i);
            if (!preview.guide() || !BuildGuide.blocksOpening(typeAt(preview.world(), cell)))
            {
                GatePreview.removeAt(preview.blockedDisplays(), i);
                continue;
            }
            clear = false;
            spawnMissing(owner, preview, preview.blockedDisplays(), i, preview.world(), cell, blockData.apply(IN_THE_WAY));
            styleForGuide(preview.blockedDisplays().get(i), BuildGuide.State.WRONG);
        }
        return clear;
    }

    private static void spawnMissing(final Player owner, final GatePreview preview, final List<BlockDisplay> shown,
        final int i, final World world, final Cell cell, final BlockData data)
    {
        final BlockDisplay existing = shown.get(i);
        if (((existing != null) && existing.isValid()) || !world.isChunkLoaded(cell.x() >> 4, cell.z() >> 4))
        {
            return;
        }
        final BlockDisplay spawned = HiddenEntities.spawnFor(WormholeXTreme.getThisPlugin(), owner,
            new Location(world, cell.x(), cell.y(), cell.z()), BlockDisplay.class, display ->
            {
                display.setBlock(data);
                display.setBrightness(FULL_BRIGHT);
            });
        shown.set(i, spawned);
        if (spawned != null)
        {
            watching(null, preview).forEach(viewer -> viewer.showEntity(WormholeXTreme.getThisPlugin(), spawned));
        }
    }

    /** The invisible box over the preview's button that a click lands on. */
    private static void drawButton(final Player owner, final GatePreview preview)
    {
        final Cell cell = preview.buttonCell();
        // A real button there is pressed to find the gate, so the box must not take its clicks.
        if ((cell == null) || !preview.showing(cell)
            || (BuildGuide.of(cell, preview.palette(), typeAt(preview.world(), cell)) == BuildGuide.State.PLACED))
        {
            preview.removeButton();
            return;
        }
        final Interaction existing = preview.button();
        if (((existing != null) && existing.isValid()) || !preview.world().isChunkLoaded(cell.x() >> 4, cell.z() >> 4))
        {
            return;
        }
        final Location centre = new Location(preview.world(), cell.x() + 0.5, cell.y() + ((1.0 - BUTTON_SIZE) / 2.0),
            cell.z() + 0.5);
        preview.button(HiddenEntities.spawnFor(WormholeXTreme.getThisPlugin(), owner, centre, Interaction.class,
            box ->
            {
                box.setInteractionWidth(BUTTON_SIZE);
                box.setInteractionHeight(BUTTON_SIZE);
                box.setResponsive(true);
            }));
    }

    /** How a cell compares with the world, or null where its chunk is not loaded to read. */
    private static BuildGuide.State guideState(final GatePreview preview, final Cell cell)
    {
        return preview.world().isChunkLoaded(cell.x() >> 4, cell.z() >> 4)
            ? BuildGuide.of(cell, preview.palette(), typeAt(preview.world(), cell))
            : null;
    }

    /** What stands at a cell, or null where its chunk is not loaded, so nothing is loaded to find out. */
    private static Material typeAt(final World world, final Cell cell)
    {
        return world.isChunkLoaded(cell.x() >> 4, cell.z() >> 4)
            ? world.getBlockAt(cell.x(), cell.y(), cell.z()).getType()
            : null;
    }

    /** Draws a display small for a block still to place, outlined in red over a wrong one, and whole otherwise. */
    static void styleForGuide(final BlockDisplay display, final BuildGuide.State state)
    {
        if (display == null)
        {
            return;
        }
        final float scale;
        if (state == BuildGuide.State.MISSING)
        {
            scale = MISSING_SCALE;
        }
        else
        {
            scale = (state == BuildGuide.State.WRONG) ? WRONG_SCALE : 1.0f;
        }
        final float offset = (1.0f - scale) / 2.0f;
        display.setTransformation(new Transformation(new Vector3f(offset, offset, offset), new AxisAngle4f(),
            new Vector3f(scale, scale, scale), new AxisAngle4f()));
        final boolean wrong = state == BuildGuide.State.WRONG;
        display.setGlowing(wrong);
        display.setGlowColorOverride(wrong ? Color.RED : null);
    }

    /** Sets every standing display to what its cell should show now. */
    private static void restyle(final GatePreview preview)
    {
        for (int i = 0; i < preview.cells().size(); i++)
        {
            final BlockDisplay display = preview.displays().get(i);
            if (display != null)
            {
                display.setBlock(dataFor(preview, preview.cells().get(i)));
            }
        }
        for (final BlockDisplay display : preview.openingDisplays())
        {
            if (display != null)
            {
                display.setBlock(openingData(preview));
            }
        }
    }

    /** What a frame cell shows now: lit while its wave is, and as built otherwise. */
    static BlockData dataFor(final GatePreview preview, final Cell cell)
    {
        final boolean lit = (cell.wave() > 0) && (cell.wave() <= preview.litWaves());
        return lit ? litData(preview.drawnPalette(), cell) : blockDataFor(preview, cell);
    }

    /**
     * A lit chevron: a chevron block with an on state switched on, and the light material for
     * everything else, as a real gate draws it.
     */
    static BlockData litData(final Palette palette, final Cell cell)
    {
        final BlockData fixtureOn = (palette.chevron() == null) ? null
            : MaterialUtils.litFormOf(blockData.apply(palette.chevron()));
        return MaterialUtils.litChevron(palette.materialOf(cell), palette.chevron(), fixtureOn,
            MaterialUtils.drawnAs(blockData.apply(palette.light())));
    }

    /** What the opening's displays show: they stand only while the iris is closed. */
    static BlockData openingData(final GatePreview preview)
    {
        return blockData.apply(preview.palette().iris());
    }

    /** What a cell is drawn as, with the button and sign turned to face the builder. */
    static BlockData blockDataFor(final GatePreview preview, final Cell cell)
    {
        final BlockData data = blockData.apply(preview.drawnPalette().materialOf(cell));
        final boolean faces = (cell.part() == Part.BUTTON) || (cell.part() == Part.DIAL_SIGN);
        if (faces && (data instanceof Directional directional)
            && directional.getFaces().contains(preview.grid().facing()))
        {
            directional.setFacing(preview.grid().facing());
        }
        return data;
    }

    /** Whether a material can stand in a display, without asking the registry before startup. */
    private static boolean isBlock(final Material material)
    {
        try
        {
            return (material != null) && (blockData.apply(material) != null);
        }
        catch (final IllegalArgumentException notABlock)
        {
            return false;
        }
    }
}
