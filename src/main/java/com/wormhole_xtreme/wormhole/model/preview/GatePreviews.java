package com.wormhole_xtreme.wormhole.model.preview;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.LongSupplier;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;


import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.integration.RegionFlags;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Cell;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Palette;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Role;
import com.wormhole_xtreme.wormhole.logic.GateGrid;
import com.wormhole_xtreme.wormhole.logic.StargateHelper;
import com.wormhole_xtreme.wormhole.model.GateSounds;
import com.wormhole_xtreme.wormhole.model.GateQuota;
import com.wormhole_xtreme.wormhole.model.GateSpacing;
import com.wormhole_xtreme.wormhole.model.MaterialGroup;
import com.wormhole_xtreme.wormhole.model.MaterialGroupRegistry;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.Stargate3DShape;
import com.wormhole_xtreme.wormhole.model.StargateShapeRegistry;
import com.wormhole_xtreme.wormhole.utils.ChatText;
import com.wormhole_xtreme.wormhole.utils.Messages;
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

    /** One click can arrive as two events; a second inside this is the same press. */
    private static final long PRESS_GAP_MILLIS = 300L;

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

    /** What became of {@code gate preview place}. */
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
        PLACED,
        /** It stood over one gate already there: the blocks that gate was missing were placed. */
        REPAIRED,
        /** A WorldGuard region where it would stand refuses its owner gate building, and nothing was placed. */
        NOT_ALLOWED_HERE,
        /** It would stand closer to another gate than {@code min-gate-distance} allows, and nothing was placed; {@code inTheWay} holds why. */
        TOO_CLOSE,
        /** Its owner already owns as many gates as they may (#633), and nothing was placed; {@code inTheWay} holds why. */
        OVER_QUOTA,
        /** An admin turned its shape off since it was shown, and nothing was placed. */
        SHAPE_OFF
    }

    /**
     * What {@code gate preview place} did.
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
    public record Placed(Outcome outcome, List<String> inTheWay, Stargate gate,
        Block button) {}

    /** Finds the gate a DHD button belongs to; tests stand in for detection. */
    interface Detector
    {
        Stargate find(Block button, BlockFace facing,
            Stargate3DShape shape);
    }

    /** Whether a block belongs to a gate or ring already there; tests stand in for the indexes. */
    interface Occupied
    {
        boolean at(World world, int x, int y, int z);
    }

    /** The gate a block belongs to, or null; tests stand in for the gate index. */
    interface GateAt
    {
        Stargate at(World world, int x, int y, int z);
    }

    /** What {@code gate preview share} did. */
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

    /**
     * Where an iris sweep books its steps.
     *
     * <p>Its own seam rather than {@link #later}, which the dial uses. The two animations can
     * run on one preview at once -- a gate dialled with its iris shut is the ordinary case --
     * and a test that drives one by hand should not have to pick it out of the other's steps.
     */
    static Later irisLater = GatePreviews::schedule;
    static Detector detector = StargateHelper::checkStargate;
    static Occupied occupied = PreviewPlacer::occupied;
    static GateAt gateAt = PreviewPlacer::gateAt;

    static final Map<UUID, List<GatePreview>> PREVIEWS = new HashMap<>();

    private GatePreviews() {}

    /**
     * Shows a shape in front of a player, alongside any previews they already have.
     *
     * @param owner
     *            who sees it
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
     * @param group
     *            its material group, or null for the shape's own materials
     * @param dhd
     *            the button or lever
     * @param facing
     *            the way it faces, away from the block it hangs on
     * @return what happened
     */
    public static Shown showOn(final Player owner, final Stargate3DShape shape, final MaterialGroup group,
        final Block dhd, final BlockFace facing)
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
        PreviewAudience.takeBackAll(owner, preview);
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
        preview.setGateIrisActive(!preview.isGateIrisActive());
        // The wormhole is not taken back when the iris shuts. It used to be, from when the
        // iris stood in the horizon's place rather than in front of it -- and that is what
        // replaced the water with air a beat before the first ring of the sweep arrived.
        // The opening's displays cover it now, the way a real gate's iris does.
        sound(owner, preview, preview.isGateIrisActive() ? ConfigManager.getGateSoundIrisClose() : ConfigManager.getGateSoundIrisOpen(),
            1.0f);
        // Restyles and draws for itself, a ring at a time. Doing either here as well would
        // draw the whole iris in the same tick the sweep set out to draw it gradually.
        PreviewIrisSweep.startIrisSweep(owner, preview);
        return preview.isGateIrisActive() ? Control.IRIS_CLOSED : Control.IRIS_OPENED;
    }

    /**
     * Redresses the preview a player is looking at in a material group.
     *
     * @param owner
     *            whose preview
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
        PreviewIrisSweep.redraw(owner, preview);
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
        PreviewIrisSweep.redraw(owner, preview);
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
     * block it needs is taken, belongs to a ring or to more than one gate, or cannot be reached.
     *
     * @param owner
     *            whose preview
     * @return what happened
     */
    public static Placed place(final Player owner)
    {
        return place(owner, false);
    }

    /**
     * {@link #place(Player)}, and when {@code overAGate} is true, over one gate already standing: it
     * fills only what that gate is missing and hands the gate back as {@link Outcome#REPAIRED}, to be
     * regenerated rather than registered a second time.
     *
     * @param owner
     *            whose preview
     * @param overAGate
     *            whether the owner may change a gate that is already there
     * @return what happened
     */
    public static Placed place(final Player owner, final boolean overAGate)
    {
        touch(owner.getUniqueId());
        final GatePreview preview = lookedAt(owner);
        if (preview == null)
        {
            return new Placed(Outcome.NOT_LOOKING, List.of(), null, null);
        }
        if (!StargateShapeRegistry.isEnabled(preview.shape().getShapeName()))
        {
            return new Placed(Outcome.SHAPE_OFF, List.of(), null, null);
        }
        if (!RegionFlags.mayBuild(owner, footprintOf(preview)))
        {
            return new Placed(Outcome.NOT_ALLOWED_HERE, List.of(), null, null);
        }
        final boolean overAGateNow = standsOverAGate(preview);
        final String tooClose = overAGateNow ? null
            : GateSpacing.refusal(owner, preview.world(), openingBlocks(preview));
        if (tooClose != null)
        {
            return new Placed(Outcome.TOO_CLOSE, List.of(tooClose), null, null);
        }
        // A repair is of a gate already counted; only a new one is held to the quota.
        final String overQuota = overAGateNow ? null : GateQuota.refusal(owner);
        if (overQuota != null)
        {
            return new Placed(Outcome.OVER_QUOTA, List.of(overQuota), null, null);
        }
        return PreviewPlacer.place(preview, overAGate);
    }

    /** Whether a gate already stands where the preview would; repairing it, or being in its way, is not spacing. */
    private static boolean standsOverAGate(final GatePreview preview)
    {
        return footprintOf(preview).stream().anyMatch(at -> gateAt.at(preview.world(), at.getBlockX(), at.getBlockY(),
            at.getBlockZ()) != null);
    }

    private static List<Location> openingBlocks(final GatePreview preview)
    {
        return preview.opening().stream().map(cell -> new Location(preview.world(), cell.x(), cell.y(), cell.z())).toList();
    }

    /** Every spot a preview's gate would take: its blocks and its opening. */
    private static List<Location> footprintOf(final GatePreview preview)
    {
        final List<Location> out = new ArrayList<>(preview.cells().size() + preview.opening().size());
        for (final List<Cell> part : List.of(preview.cells(), preview.opening()))
        {
            for (final Cell cell : part)
            {
                out.add(new Location(preview.world(), cell.x(), cell.y(), cell.z()));
            }
        }
        return out;
    }

    /**
     * Builds a shape where a grid stands, with no player: for {@code gate build} given coordinates, from
     * the console or a command block. Nothing is shown or remembered; it is placed or refused exactly as
     * a preview's {@code place} would be, and never over a gate already there.
     *
     * @param group
     *            its material group, or null for the shape's own materials
     * @param grid
     *            where it stands
     * @return what happened
     */
    public static Placed placeAt(final World world, final Stargate3DShape shape, final MaterialGroup group,
        final GateGrid grid)
    {
        final GatePreview preview = new GatePreview(world, shape, grid, Palette.of(shape, group),
            GateBlueprint.of(shape, grid), GateBlueprint.openingOf(shape, grid), GateBlueprint.wooshOf(shape, grid));
        preview.plainChevrons(classic(shape, group));
        return PreviewPlacer.place(preview, false);
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
        PreviewAudience.showToAudience(owner, preview);
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
        PreviewAudience.showToAudience(owner, preview);
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
            PreviewAudience.takeBackAll(owner, preview);
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
                PreviewAudience.takeBackAll(null, preview);
                preview.remove();
            });
        }
        // Shown again when they are back; a relog forgets what the client was shown. The side
        // they were drawn from goes with it, or it is read later as "was drawn stacked" for
        // somebody who has not been drawn at all since.
        PREVIEWS.values().forEach(theirs -> theirs.forEach(preview ->
        {
            preview.shownTo().remove(owner);
            preview.sides().remove(owner);
        }));
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
                    PreviewAudience.takeBackAll(online.apply(entry.getKey()), preview);
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
                    PreviewAudience.takeBackAll(owner, preview);
                    preview.remove();
                    return true;
                }
                if ((owner != null) && preview.world().equals(owner.getWorld()))
                {
                    PreviewAudience.showToAudience(owner, preview);
                    PreviewIrisSweep.redraw(owner, preview);
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
            PreviewAudience.takeBackAll(online.apply(id), preview);
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
        gateAt = PreviewPlacer::gateAt;
    }

    /** The group the gate built from a preview would be detected as, by its frame material. */
    static MaterialGroup groupByFrame(final GatePreview preview)
    {
        return MaterialGroupRegistry.getGroupByStructureMaterial(preview.palette().structure());
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
            preview.spinTick(0);
            preview.spinCells(Set.of());
            preview.wooshStage(0);
            preview.open(false);
            PreviewAudience.takeBack(owner, preview, preview.woosh());
            PreviewAudience.takeBack(owner, preview, preview.opening());
            // Shut mid-sweep: the sweep drew the wormhole off the ring for those in front (#442).
            PreviewIrisSweep.handBackSwept(owner, preview);
            preview.sweepSides().clear();
            sound(owner, preview, ConfigManager.getGateSoundClose(), 1.0f);
            restyle(preview);
            draw(owner, preview);
            return Control.SHUT_DOWN;
        }
        sound(owner, preview, ConfigManager.getGateSoundActivate(), 1.0f);
        PreviewDial.next(owner, preview, preview.shape().getShapeLightTicks());
        return Control.DIALLING;
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

    static void sound(final Player owner, final GatePreview preview, final String sound, final float pitch)
    {
        if (ConfigManager.isGateSoundsEnabled())
        {
            // Sized like the real gate this shape would build.
            final double scale = GateSounds.scaleOf(preview.shape());
            final float volume = ConfigManager.getGateSoundVolume() * GateSounds.sizeVolume(scale);
            final float scaledPitch = pitch * GateSounds.sizePitch(scale);
            PreviewAudience.watching(owner, preview)
                .forEach(viewer -> Sounds.playTo(viewer, sound, volume, scaledPitch));
        }
    }

    /**
     * Brings the entities in line with what the preview should show: spawns what is missing where
     * its chunk is loaded, and removes what should not be there.
     */
    static void draw(final Player owner, final GatePreview preview)
    {
        final World world = preview.world();
        final boolean built = PreviewFrame.drawFrame(owner, preview);
        final boolean clear = PreviewFrame.drawInTheWay(owner, preview);
        if (preview.guide())
        {
            if (built && clear && !preview.finished())
            {
                owner.sendMessage(ChatText.info(Messages.get(owner, "preview.built", preview.shape().getShapeName())));
            }
            preview.finished(built && clear);
        }
        final boolean stacked = PreviewLayers.stacks(preview);
        for (int i = 0; i < preview.opening().size(); i++)
        {
            if (!preview.irisShownAt(i))
            {
                GatePreview.removeAt(preview.openingDisplays(), i);
                GatePreview.removeAt(preview.beyondDisplays(), i);
                continue;
            }
            PreviewFrame.spawnMissing(owner, preview, preview.openingDisplays(), i, world, preview.opening().get(i),
                PreviewFrame.openingData(preview));
            PreviewLayers.spawnBeyond(owner, preview, i, stacked);
        }
        if (preview.open() && !stacked)
        {
            // Sent whatever the iris is doing. The opening's displays stand in front of these
            // blocks, so a closed iris hides the horizon without the horizon having to be
            // taken away -- which is what a real gate does too, and is the only way a sweep
            // has anything to sweep over. Dropping it the moment the iris shut replaced the
            // water with air a beat before the first ring arrived, so the wormhole looked like
            // it had closed rather than been covered.
            if (preview.sweeping())
            {
                // Mid-sweep the wormhole is the sweep's to paint, viewer by viewer (#432).
                PreviewIrisSweep.sweepWormhole(owner, preview);
            }
            else
            {
                PreviewAudience.send(owner, preview, preview.opening());
            }
        }
        PreviewLayers.applySides(owner, preview, stacked);
        PreviewFrame.drawButton(owner, preview);
    }

    /** Moves the wormhole drawn behind a see-through iris, for every preview that has one. */
    public static void tickHorizon()
    {
        PreviewLayers.tickHorizon();
    }

    /** Restacks any preview whose plane a player has just crossed. */
    public static void moved(final Player player, final Location to)
    {
        PreviewLayers.moved(player, to);
    }

    /** What stands at a cell, or null where its chunk is not loaded, so nothing is loaded to find out. */
    static Material typeAt(final World world, final Cell cell)
    {
        return world.isChunkLoaded(cell.x() >> 4, cell.z() >> 4)
            ? world.getBlockAt(cell.x(), cell.y(), cell.z()).getType()
            : null;
    }

    /** Sets every standing display to what its cell should show now. */
    static void restyle(final GatePreview preview)
    {
        for (int i = 0; i < preview.cells().size(); i++)
        {
            final BlockDisplay display = preview.displays().get(i);
            if (display != null)
            {
                display.setBlock(PreviewFrame.dataFor(preview, preview.cells().get(i)));
            }
        }
        // The iris beyond the ring wears the same block: restyling only the ring's set left a
        // viewer behind the gate looking at the palette the preview had before.
        for (final BlockDisplay display : preview.beyondDisplays())
        {
            if (display != null)
            {
                display.setBlock(PreviewFrame.openingData(preview));
            }
        }
        for (final BlockDisplay display : preview.openingDisplays())
        {
            if (display != null)
            {
                display.setBlock(PreviewFrame.openingData(preview));
            }
        }
    }

    /** Restyles only the given cells: the ring's light moves every tick, and the rest stands still. */
    static void restyle(final GatePreview preview, final Set<Cell> cells)
    {
        for (final Cell cell : cells)
        {
            final int i = preview.cells().indexOf(cell);
            final BlockDisplay display = (i < 0) ? null : preview.displays().get(i);
            if (display != null)
            {
                display.setBlock(PreviewFrame.dataFor(preview, cell));
            }
        }
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
