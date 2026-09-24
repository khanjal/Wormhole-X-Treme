package com.wormhole_xtreme.wormhole.model.preview;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
import org.bukkit.block.BlockFace;
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

import java.util.logging.Level;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.logic.DialSpinPattern;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Cell;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Palette;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Part;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Role;
import com.wormhole_xtreme.wormhole.logic.GateGrid;
import com.wormhole_xtreme.wormhole.logic.StargateHelper;
import com.wormhole_xtreme.wormhole.model.GateSounds;
import com.wormhole_xtreme.wormhole.model.DrawnHorizon;
import com.wormhole_xtreme.wormhole.model.IrisLayering;
import com.wormhole_xtreme.wormhole.model.IrisSweep;
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
        REPAIRED
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

    /** The gate a block belongs to, or null; tests stand in for the gate index. */
    interface GateAt
    {
        com.wormhole_xtreme.wormhole.model.Stargate at(World world, int x, int y, int z);
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
        preview.setGateIrisActive(!preview.isGateIrisActive());
        // The wormhole is not taken back when the iris shuts. It used to be, from when the
        // iris stood in the horizon's place rather than in front of it -- and that is what
        // replaced the water with air a beat before the first ring of the sweep arrived.
        // The opening's displays cover it now, the way a real gate's iris does.
        sound(owner, preview, preview.isGateIrisActive() ? ConfigManager.getGateSoundIrisClose() : ConfigManager.getGateSoundIrisOpen(),
            1.0f);
        // Restyles and draws for itself, a ring at a time. Doing either here as well would
        // draw the whole iris in the same tick the sweep set out to draw it gradually.
        startIrisSweep(owner, preview);
        return preview.isGateIrisActive() ? Control.IRIS_CLOSED : Control.IRIS_OPENED;
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
        return (preview == null) ? new Placed(Outcome.NOT_LOOKING, List.of(), null, null)
            : PreviewPlacer.place(preview, overAGate);
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
        gateAt = PreviewPlacer::gateAt;
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
            if (!turnRing(owner, preview))
            {
                lockNextChevron(owner, preview);
            }
            return;
        }
        // The same sequence a real gate plays, iris and all; only the drawing is the preview's.
        final int stage = preview.wooshStage();
        final int next = WooshSequence.play(stage, preview.lastWoosh(), preview, new PreviewCanvas(owner, preview));
        preview.wooshStage((next < 0) ? (stage + 1) : next);
        if (next >= 0)
        {
            next(owner, preview, preview.shape().getShapeWooshTicks());
        }
    }

    /** A preview's side of the woosh: fake blocks for whoever is watching, the sound at the preview. */
    private record PreviewCanvas(Player owner, GatePreview preview) implements WooshSequence.Canvas
    {
        @Override
        public void kawoosh()
        {
            sound(owner, preview, ConfigManager.getGateSoundKawoosh(), GateSounds.KAWOOSH_PITCH);
        }

        @Override
        public void draw(final int index)
        {
            send(owner, preview, wooshStep(preview, index));
        }

        @Override
        public void undraw(final int index)
        {
            takeBack(owner, preview, wooshStep(preview, index));
        }

        @Override
        public void undrawAll()
        {
            takeBack(owner, preview, preview.woosh());
        }

        @Override
        public void settle()
        {
            preview.open(true);
            GatePreviews.draw(owner, preview);
        }

        @Override
        public void settleBehindIris()
        {
            // Open all the same: the opening is drawn behind the iris, and its displays cover it.
            settle();
        }

        /** The woosh's cells at one step, counted from 0 as {@link WooshSequence} does; the shape's W# from 1. */
        private static List<Cell> wooshStep(final GatePreview preview, final int index)
        {
            return preview.woosh().stream().filter(cell -> cell.wave() == (index + 1)).toList();
        }
    }

    /** Locks the next chevron, with its sound, and books what follows it. */
    private static void lockNextChevron(final Player owner, final GatePreview preview)
    {
        preview.litWaves(preview.litWaves() + 1);
        sound(owner, preview, ConfigManager.getGateSoundChevron(),
            GateSounds.chevronPitch(preview.litWaves(), preview.lastWave()));
        if (preview.litWaves() == preview.lastWave())
        {
            sound(owner, preview, ConfigManager.getGateSoundLock(), GateSounds.LOCK_PITCH);
        }
        restyle(preview);
        // Held after the last chevron as a real gate holds it. With the ring turning, the turn
        // itself is the wait before the next chevron.
        final long between = spins(preview) ? 1L : preview.shape().getShapeLightTicks();
        next(owner, preview, (preview.litWaves() < preview.lastWave()) ? between : Stargate.LAST_CHEVRON_PAUSE_TICKS);
    }

    /** Whether this preview's dial shows the ring turning. */
    private static boolean spins(final GatePreview preview)
    {
        return (preview.spin() != null) && (pattern(preview) != DialSpinPattern.NONE);
    }

    /**
     * The ring pattern a preview dials with: that of the group its frame material names, else the
     * server's, as the gate built from it would be detected.
     */
    private static DialSpinPattern pattern(final GatePreview preview)
    {
        return ConfigManager.getGateDialSpinPattern(null,
            com.wormhole_xtreme.wormhole.model.MaterialGroupRegistry.getGroupByStructureMaterial(preview.palette().structure()));
    }

    /**
     * Moves the ring's light one tick along its pattern (#357), for the glyph about to lock, over
     * the chevron's own interval and any rest the pattern adds.
     *
     * @return true while the light is still travelling, false once it has arrived and the chevron may lock
     */
    private static boolean turnRing(final Player owner, final GatePreview preview)
    {
        if (!spins(preview))
        {
            // Recoloured into a group that turns none part way round: put back what the turn had lit.
            final Set<Cell> left = preview.spinCells();
            if (!left.isEmpty())
            {
                preview.spinCells(Set.of());
                preview.spinTick(0);
                restyle(preview, left);
            }
            return false;
        }
        final Set<Cell> was = preview.spinCells();
        final DialSpinPattern pattern = pattern(preview);
        final int glyph = preview.litWaves() + 1;
        final int interval = Math.max(1, preview.shape().getShapeLightTicks());
        final boolean arrived = preview.spinTick() >= preview.spin().frames(pattern, glyph, interval);
        // On arriving, what rests on the ring stays lit through the lock; the next turn takes it back.
        preview.spinCells(arrived ? preview.spin().rest(pattern, glyph, preview.lastWave())
            : preview.spin().frame(pattern, glyph, preview.spinTick(), interval));
        preview.spinTick(arrived ? 0 : (preview.spinTick() + 1));
        final Set<Cell> changed = new java.util.HashSet<>(was);
        changed.addAll(preview.spinCells());
        restyle(preview, changed);
        if (arrived)
        {
            return false;
        }
        next(owner, preview, 1L);
        return true;
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
            preview.spinTick(0);
            preview.spinCells(Set.of());
            preview.wooshStage(0);
            preview.open(false);
            takeBack(owner, preview, preview.woosh());
            takeBack(owner, preview, preview.opening());
            // Shut mid-sweep: the sweep drew the wormhole off the ring for those in front (#442).
            watching(owner, preview).stream()
                .filter(viewer -> preview.sweepSides().containsKey(viewer.getUniqueId()))
                .forEach(viewer -> handBackOffsets(viewer, preview));
            preview.sweepSides().clear();
            sound(owner, preview, ConfigManager.getGateSoundClose(), 1.0f);
            restyle(preview);
            draw(owner, preview);
            return Control.SHUT_DOWN;
        }
        sound(owner, preview, ConfigManager.getGateSoundActivate(), 1.0f);
        next(owner, preview, preview.shape().getShapeLightTicks());
        return Control.DIALLING;
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
        final BlockData portal =
            MaterialUtils.laidAcross(blockData.apply(preview.palette().portal()), preview.grid().facing());
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
        // A stacked wormhole stood a block off the ring, which the cells above do not cover.
        // Only for viewers who were drawn stacked: everybody else was only ever sent the ring.
        watching(owner, preview).stream()
            .filter(viewer -> drewOffsetsFor(preview, viewer))
            .forEach(viewer -> handBackOffsets(viewer, preview));
    }

    /**
     * Whether a viewer may have been drawn a wormhole off the ring: settled stacked, or by a sweep,
     * which draws it there with no side recorded in {@code sides()} (#442).
     */
    private static boolean drewOffsetsFor(final GatePreview preview, final Player viewer)
    {
        return preview.sides().containsKey(viewer.getUniqueId())
            || preview.sweepSides().containsKey(viewer.getUniqueId());
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
                takeBackFromViewer(online.apply(id), preview);
                preview.sides().remove(id);
            }
        }
        for (final UUID id : wanted)
        {
            if (preview.shownTo().add(id))
            {
                showToNewViewer(online.apply(id), preview);
            }
        }
    }

    /**
     * Takes a preview off one viewer who is not being shown it any more.
     *
     * @param gone
     *            the viewer, which may be null if they are offline
     * @param preview
     *            the preview
     */
    private static void takeBackFromViewer(final Player gone, final GatePreview preview)
    {
        if ((gone == null) || !preview.world().equals(gone.getWorld()))
        {
            return;
        }
        preview.standingDisplays().forEach(display -> gone.hideEntity(WormholeXTreme.getThisPlugin(), display));
        takeBackFrom(gone, preview, sentCells(preview));
        // Only if they had a side, and before it is forgotten: a viewer drawn from the front
        // had their wormhole a block off the ring, which the cells above know nothing about.
        if (drewOffsetsFor(preview, gone))
        {
            handBackOffsets(gone, preview);
        }
    }

    /**
     * Catches one viewer up on a preview they have just been shown.
     *
     * @param viewer
     *            the new viewer
     * @param preview
     *            the preview
     */
    private static void showToNewViewer(final Player viewer, final GatePreview preview)
    {
        preview.standingDisplays().forEach(display -> viewer.showEntity(WormholeXTreme.getThisPlugin(), display));
        if (stacks(preview))
        {
            // Both iris sets are standing, and showing everything would hand them both. Their
            // own side settles which one they keep, and where their wormhole goes: the ring
            // cells the audience is otherwise caught up with are the wrong ones for somebody
            // in front.
            final List<IrisLayering.Placement> layers = layersFor(preview, viewer.getLocation());
            applySideFor(viewer, preview, layers);
            sendStacked(viewer, preview, layers);
            return;
        }
        sendTo(viewer, preview, sentCells(preview));
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
        final boolean stacked = stacks(preview);
        for (int i = 0; i < preview.opening().size(); i++)
        {
            if (!preview.irisShownAt(i))
            {
                GatePreview.removeAt(preview.openingDisplays(), i);
                GatePreview.removeAt(preview.beyondDisplays(), i);
                continue;
            }
            spawnMissing(owner, preview, preview.openingDisplays(), i, world, preview.opening().get(i), openingData(preview));
            spawnBeyond(owner, preview, i, stacked);
        }
        if (preview.open() && !stacked)
        {
            // Sent whatever the iris is doing. The opening's displays stand in front of these
            // blocks, so a closed iris hides the horizon without the horizon having to be
            // taken away -- which is what a real gate does too, and is the only way a sweep
            // has anything to sweep over. Dropping it the moment the iris shut replaced the
            // water with air a beat before the first ring arrived, so the wormhole looked like
            // it had closed rather than been covered.
            send(owner, preview, preview.opening());
        }
        applySides(owner, preview, stacked);
        drawButton(owner, preview);
    }

    /**
     * Whether a preview's opening is drawn in two layers, so differently from each side.
     *
     * <p>The same question a real gate asks: a wormhole showing, an iris over it, and a gate
     * that stands upright. A preview of a horizontal shape keeps its iris in the ring, as the
     * gate does, because there is no in front or behind to stack along.
     *
     * @param preview
     *            the preview
     * @return true if its iris and wormhole are stacked per viewer
     */
    private static boolean stacks(final GatePreview preview)
    {
        final BlockFace facing = preview.grid().facing();
        // Not while a sweep is crossing. The sweep covers the wormhole ring by ring, so the
        // wormhole has to stay in the ring for it to be seen covering anything; moving it
        // behind the gate at the first ring empties the opening for the length of the sweep,
        // which is what "the water vanished a beat before the iris arrived" looked like. The
        // gate settles its layers after the sweep too.
        return preview.open() && preview.isGateIrisActive() && (facing != null)
            && (facing != BlockFace.UP) && (facing != BlockFace.DOWN)
            && !preview.opening().isEmpty() && !preview.sweeping();
    }

    /**
     * Where the layers go for one opening cell, for a viewer on the given side.
     *
     * <p>Through {@link IrisLayering}, the same decision a real gate makes, so the two cannot
     * drift apart. Only the drawing differs: a preview's iris is block displays, which show no
     * liquid, so its wormhole is the fake block and its iris is the entity.
     *
     * @param preview
     *            the preview
     * @param cell
     *            the opening cell
     * @param eye
     *            where the viewer is looking from
     * @param stacked
     *            whether the preview is layered for this viewer at all
     * @return where the iris and the wormhole go for them
     */
    private static IrisLayering.Placement placed(final GatePreview preview, final Cell cell,
        final IrisLayering.Eye eye, final boolean stacked)
    {
        return IrisLayering.place(at(cell), preview.grid().facing(), eye,
            at -> freeForLayer(preview, at), stacked);
    }

    /** An opening cell as a plain position. */
    private static IrisLayering.At at(final Cell cell)
    {
        return new IrisLayering.At(cell.x(), cell.y(), cell.z());
    }

    /**
     * Where every cell of a stacked preview's opening puts its layers, for one position.
     *
     * <p>One placement per opening cell, in the opening's own order -- so the list doubles as
     * what a viewer is holding, to be compared against on their next step.
     *
     * @param preview
     *            the preview, which should be stacked
     * @param from
     *            where it is being looked at from
     * @return the placements
     */
    private static List<IrisLayering.Placement> layersFor(final GatePreview preview, final Location from)
    {
        final IrisLayering.Eye eye = new IrisLayering.Eye(from.getX(), from.getY(), from.getZ());
        final List<IrisLayering.At> opening = new ArrayList<>();
        preview.opening().forEach(cell -> opening.add(at(cell)));
        // The whole preview is cover, not just its opening: a viewer off to one side is looking
        // past the blocks around the ring, the same as at a built gate.
        final Set<IrisLayering.At> cover = new HashSet<>(opening);
        preview.cells().forEach(cell -> cover.add(at(cell)));
        final boolean stacked =
            IrisLayering.hidesFarLayers(opening, preview.grid().facing(), eye, cover::contains);
        final List<IrisLayering.Placement> layers = new ArrayList<>();
        for (final Cell cell : preview.opening())
        {
            layers.add(placed(preview, cell, eye, stacked));
        }
        return layers;
    }

    /** Whether a layer may be drawn at a position: real air, in a chunk that is loaded. */
    private static boolean freeForLayer(final GatePreview preview, final IrisLayering.At at)
    {
        if (!preview.world().isChunkLoaded(at.x() >> 4, at.z() >> 4))
        {
            return false;
        }
        return MaterialUtils.isAirMaterial(preview.world().getBlockAt(at.x(), at.y(), at.z()).getType());
    }

    /**
     * Spawns, or clears, the iris display a block along the facing for one opening cell.
     *
     * <p>Two sets of iris displays stand while a preview is stacked -- one in the ring, one
     * beyond it -- and each viewer is shown exactly one, which is what lets the same preview
     * read correctly from both sides at once. Standing it does not decide that anybody sees
     * it: a viewer round the side of the gate is shown the ring's set however free the cell
     * beyond is, which is {@link #applySideFor}'s to work out.
     *
     * @param owner
     *            the preview's owner
     * @param preview
     *            the preview
     * @param index
     *            the opening cell's index
     * @param stacked
     *            whether the preview is stacked at all
     */
    private static void spawnBeyond(final Player owner, final GatePreview preview, final int index,
        final boolean stacked)
    {
        final Cell cell = preview.opening().get(index);
        final IrisLayering.At beyond = stacked && (preview.grid().facing() != null)
            ? at(cell).moved(preview.grid().facing(), 1) : null;
        // Nothing to stand where the preview is not stacked, or where whatever is built beyond
        // the ring leaves no room: the viewer behind sees the ring one like everybody else.
        if ((beyond == null) || !freeForLayer(preview, beyond))
        {
            GatePreview.removeAt(preview.beyondDisplays(), index);
            return;
        }
        spawnMissing(owner, preview, preview.beyondDisplays(), index, preview.world(),
            new Cell(beyond.x(), beyond.y(), beyond.z(), cell.part(), cell.wave(), cell.dhd(), cell.layer()),
            openingData(preview));
    }

    /**
     * Sends one viewer a set of placements, and remembers them as theirs.
     *
     * @param viewer
     *            the viewer
     * @param preview
     *            the preview, which should be stacked
     * @param layers
     *            the placements, one per opening cell
     */
    private static void sendStacked(final Player viewer, final GatePreview preview,
        final List<IrisLayering.Placement> layers)
    {
        final BlockFace facing = preview.grid().facing();
        for (int i = 0; i < layers.size(); i++)
        {
            final Cell cell = preview.opening().get(i);
            final IrisLayering.Placement here = layers.get(i);
            // Every cell this viewer's wormhole is not in, handed back first: the ring itself
            // when the iris has it -- leaving the sweep's water under a glass iris where a gate
            // shows none -- and the offsets in case they have just come round from the other
            // side, or stepped far enough round that the layers have collapsed into one.
            for (final IrisLayering.At back : IrisLayering.handBacks(at(cell), facing, here.horizon()))
            {
                takeBackAt(viewer, preview, back);
            }
            if (here.horizon() != null)
            {
                preview.sent().add(GatePreview.key(cell));
                viewer.sendBlockChange(new Location(preview.world(),
                    here.horizon().x(), here.horizon().y(), here.horizon().z()),
                    horizonData(preview, at(cell), here));
            }
        }
        logStacked(viewer, preview, layers);
        preview.sides().put(viewer.getUniqueId(), layers);
    }

    /**
     * What to draw one cell's wormhole in, the same way a built gate decides it.
     *
     * <p>Behind the iris it is a look-alike where the iris would hide the liquid, and in the
     * ring it is the real thing. A preview's iris is a display entity rather than a block,
     * which takes no part in block face culling -- but a translucent entity hides translucent
     * water behind it just the same, so a preview needs this exactly as a gate does.
     *
     * @param preview
     *            the preview
     * @param ring
     *            the opening cell
     * @param placed
     *            where this cell's layers went
     * @return the block data to send
     */
    private static BlockData horizonData(final GatePreview preview, final IrisLayering.At ring,
        final IrisLayering.Placement placed)
    {
        return MaterialUtils.laidAcross(blockData.apply(DrawnHorizon.materialFor(preview.palette().portal(),
            preview.palette().iris(), ring, !ring.equals(placed.horizon()))), preview.grid().facing());
    }

    /**
     * Moves the wormhole drawn behind a see-through iris, for every preview that has one.
     *
     * <p>The gate's own sweep, for previews. Only those whose iris hides the liquid, and only
     * the cells a viewer actually holds it in.
     */
    public static void tickHorizon()
    {
        for (final Map.Entry<UUID, List<GatePreview>> owned : PREVIEWS.entrySet())
        {
            final Player owner = WormholeXTreme.getThisPlugin().getServer().getPlayer(owned.getKey());
            if (owner == null)
            {
                continue;
            }
            for (final GatePreview preview : owned.getValue())
            {
                if (stacks(preview) && DrawnHorizon.standsIn(preview.palette().iris()))
                {
                    shimmerPreview(owner, preview);
                }
            }
        }
    }

    /**
     * Redraws one preview's stand-in wormhole for everybody holding one.
     *
     * @param owner
     *            the preview's owner
     * @param preview
     *            the preview, whose iris is known to hide the liquid
     */
    private static void shimmerPreview(final Player owner, final GatePreview preview)
    {
        for (final Player viewer : watching(owner, preview))
        {
            final List<IrisLayering.Placement> layers = preview.sides().get(viewer.getUniqueId());
            if (layers == null)
            {
                continue;
            }
            for (int i = 0; i < layers.size(); i++)
            {
                final IrisLayering.At ring = at(preview.opening().get(i));
                final IrisLayering.At where = layers.get(i).horizon();
                if ((where != null) && !where.equals(ring))
                {
                    viewer.sendBlockChange(new Location(preview.world(), where.x(), where.y(), where.z()),
                        horizonData(preview, ring, layers.get(i)));
                }
            }
        }
    }

    /**
     * Says what one stacked draw decided, for working out why a preview looks wrong in a world.
     *
     * <p>The gate's own {@code logLayers}, for the path a preview takes instead. One line per
     * draw at {@code log-level: FINE}.
     *
     * @param viewer
     *            who it was drawn for
     * @param preview
     *            the preview
     * @param layers
     *            what was drawn
     */
    private static void logStacked(final Player viewer, final GatePreview preview,
        final List<IrisLayering.Placement> layers)
    {
        final WormholeXTreme plugin = WormholeXTreme.getThisPlugin();
        if ((plugin == null) || !plugin.isLoggable(Level.FINE) || layers.isEmpty())
        {
            return;
        }
        final Cell first = preview.opening().get(0);
        final IrisLayering.At ring = at(first);
        final StringBuilder dbg = new StringBuilder(256);
        dbg.append("Preview layers: For=").append(viewer.getName());
        dbg.append(" Facing=").append(preview.grid().facing());
        dbg.append(" Iris=").append(preview.palette().iris());
        dbg.append(" Portal=").append(preview.palette().portal());
        dbg.append(" Cells=").append(layers.size());
        dbg.append(" WithHorizon=").append(layers.stream().filter(p -> p.horizon() != null).count());
        dbg.append(" WithIris=").append(layers.stream().filter(p -> p.iris() != null).count());
        dbg.append(" Ring=").append(ring);
        dbg.append(" FirstIris=").append(layers.get(0).iris());
        dbg.append(" FirstHorizon=").append(layers.get(0).horizon());
        dbg.append(" ViewerAt=").append(viewer.getLocation().getBlockX()).append(',')
            .append(viewer.getLocation().getBlockY()).append(',').append(viewer.getLocation().getBlockZ());
        for (int step = -1; step <= 1; step += 2)
        {
            final IrisLayering.At off = ring.moved(preview.grid().facing(), step);
            dbg.append(" Off").append(step).append('=').append(off)
                .append('/').append(freeForLayer(preview, off) ? "free" : "taken");
        }
        plugin.prettyLog(Level.FINE, dbg.toString());
    }

    /**
     * Hands one viewer back both cells a stacked wormhole could have been drawn in.
     *
     * <p>A stacked wormhole is sent a block off the ring, and everything that takes a preview's
     * blocks back works from the opening's own cells -- so nothing else knows about the cell
     * that was actually written. Called when a preview stops being stacked and when it is taken
     * away entirely.
     *
     * @param viewer
     *            the viewer
     * @param preview
     *            the preview
     */
    private static void handBackOffsets(final Player viewer, final GatePreview preview)
    {
        final BlockFace facing = preview.grid().facing();
        if (facing == null)
        {
            return;
        }
        for (final Cell cell : preview.opening())
        {
            final IrisLayering.At ring = new IrisLayering.At(cell.x(), cell.y(), cell.z());
            takeBackAt(viewer, preview, ring.moved(facing, 1));
            takeBackAt(viewer, preview, ring.moved(facing, -1));
        }
    }

    /** Shows one viewer what really stands at a layer position, where its chunk is loaded. */
    private static void takeBackAt(final Player viewer, final GatePreview preview, final IrisLayering.At at)
    {
        if (!preview.world().isChunkLoaded(at.x() >> 4, at.z() >> 4))
        {
            return;
        }
        viewer.sendBlockChange(new Location(preview.world(), at.x(), at.y(), at.z()),
            preview.world().getBlockAt(at.x(), at.y(), at.z()).getBlockData());
    }

    /**
     * Shows each viewer the preview as it looks from where they stand.
     *
     * <p>Both iris sets stand while a preview is stacked, and each viewer is shown one cell by
     * cell, along with the wormhole where it belongs for them: behind the ring from the front,
     * in the ring from behind, and in neither from far enough round the side. Unstacked, the
     * ring set is everybody's, which is what a preview has always shown.
     *
     * @param owner
     *            the preview's owner
     * @param preview
     *            the preview
     * @param stacked
     *            whether it is stacked
     */
    private static void applySides(final Player owner, final GatePreview preview, final boolean stacked)
    {
        for (final Player viewer : watching(owner, preview))
        {
            if (stacked)
            {
                final List<IrisLayering.Placement> layers = layersFor(preview, viewer.getLocation());
                applySideFor(viewer, preview, layers);
                sendStacked(viewer, preview, layers);
            }
            else if (preview.sides().containsKey(viewer.getUniqueId()))
            {
                // Stacked until a moment ago: whoever was behind it was shown the set beyond
                // the ring, and the ring's own set is everybody's again.
                showRingSet(viewer, preview);
                // The wormhole was drawn a block off the ring for them as well, and the ring
                // cells the unstacked draw sends say nothing about that one, so it is handed
                // back here -- but not in the middle of a sweep. An iris opening is unstacked
                // from its very first ring, so this handed every cell's far layer back at once
                // while most of them were still covered: a see-through iris opened onto
                // nothing, which is the closing sweep's fault read backwards. Mid-sweep the
                // rings give their own back one at a time and the settle after the last ring
                // catches whatever is left, which is why the entry stays until then.
                if (!preview.sweeping())
                {
                    preview.sides().remove(viewer.getUniqueId());
                    handBackOffsets(viewer, preview);
                }
            }
        }
    }

    /**
     * Shows one viewer one of the two iris sets and hides the other.
     *
     * @param viewer
     *            the viewer
     * @param preview
     *            the preview
     * @param layers
     *            where their layers go, from {@link #layersFor}
     */
    private static void applySideFor(final Player viewer, final GatePreview preview,
        final List<IrisLayering.Placement> layers)
    {
        // Cell by cell, because the two sets are not a viewer's to pick between wholesale. A
        // cell with nothing beyond the ring to stand in keeps its iris in the ring, and so does
        // one whose far display the opening no longer hides from where this viewer is: showing
        // the far one there is the iris seen floating beside the gate, and hiding the ring's as
        // well leaves that cell's wormhole with no iris over it, a shut gate reading as open.
        for (int i = 0; i < layers.size(); i++)
        {
            final BlockDisplay beyond = preview.beyondDisplays().get(i);
            final IrisLayering.At iris = layers.get(i).iris();
            // No iris at all where the wormhole has the ring to itself, which is what a viewer
            // behind an unlayered gate is shown: neither display is theirs.
            final boolean inRing = (iris != null) && iris.equals(at(preview.opening().get(i)));
            show(viewer, preview.openingDisplays().get(i), inRing);
            show(viewer, beyond, (iris != null) && !inRing);
        }
    }

    /** Shows one viewer the ring's own iris set, which is what an unstacked preview is. */
    private static void showRingSet(final Player viewer, final GatePreview preview)
    {
        show(viewer, preview.openingDisplays(), true);
        show(viewer, preview.beyondDisplays(), false);
    }

    /** Shows or hides a set of displays for one viewer. */
    private static void show(final Player viewer, final List<BlockDisplay> displays, final boolean shown)
    {
        for (final BlockDisplay display : displays)
        {
            show(viewer, display, shown);
        }
    }

    /** Shows or hides one display for one viewer, if it is standing. */
    private static void show(final Player viewer, final BlockDisplay display, final boolean shown)
    {
        if (display == null)
        {
            return;
        }
        if (shown)
        {
            viewer.showEntity(WormholeXTreme.getThisPlugin(), display);
        }
        else
        {
            viewer.hideEntity(WormholeXTreme.getThisPlugin(), display);
        }
    }

    /**
     * Restacks any preview whose plane a player has just crossed.
     *
     * <p>A preview redraws itself every five seconds, which cannot follow somebody walking round
     * one. This is the preview's half of what the move listener does for a real gate, and like
     * it, only a step that changes which side they are on costs anything.
     *
     * @param player
     *            the player who moved
     */
    public static void moved(final Player player, final Location to)
    {
        if ((player == null) || (to == null) || !player.isOnline())
        {
            return;
        }
        for (final Map.Entry<UUID, List<GatePreview>> owned : PREVIEWS.entrySet())
        {
            // Only a preview this player is actually shown. Without that, walking past
            // somebody's unshared preview revealed its iris and sent its wormhole -- displays
            // HiddenEntities had hidden from everybody, and blocks nothing would take back,
            // since every teardown path only reaches the people watching.
            final boolean watches = owned.getKey().equals(player.getUniqueId());
            for (final GatePreview preview : owned.getValue())
            {
                if (watches || preview.shownTo().contains(player.getUniqueId()))
                {
                    restackIfCrossed(player, preview, to);
                }
            }
        }
    }

    /** Redraws one preview for a player whose step changed what it looks like to them. */
    private static void restackIfCrossed(final Player player, final GatePreview preview, final Location to)
    {
        if (preview.sweeping() && preview.world().equals(player.getWorld()))
        {
            resweepIfCrossed(player, preview, to);
            return;
        }
        if (!stacks(preview) || !preview.world().equals(player.getWorld()))
        {
            return;
        }
        final List<IrisLayering.Placement> now = layersFor(preview, to);
        if (now.equals(preview.sides().get(player.getUniqueId())))
        {
            return;
        }
        applySideFor(player, preview, now);
        sendStacked(player, preview, now);
    }

    /**
     * Moves the preview's iris a ring at a time, the way a real gate's does.
     *
     * <p>The preview is display entities rather than blocks, so none of the care a real gate
     * needs applies -- there is nothing here to walk through, and no {@code BlockPhysicsEvent}
     * to raise. What matters is that it looks the same: the same rings in the same order at the
     * same pace, from {@link IrisSweep}, {@code gate-iris-step-ticks} and the same
     * {@code gate-iris-sweep-max-ticks} that merges a big gate's rings into bands, so a preview
     * is a rehearsal of the gate rather than an approximation of one.
     *
     * <p>Instant when the setting says so, or when there is no scheduler to book a step with.
     */
    private static void startIrisSweep(final Player owner, final GatePreview preview)
    {
        cancelIrisSweep(preview);
        final List<Cell> cells = preview.opening();
        if (!ConfigManager.isGateIrisAnimated() || (cells.size() < 2))
        {
            preview.irisShownEverywhere(preview.isGateIrisActive());
            restyle(preview);
            draw(owner, preview);
            return;
        }
        // Closing starts from nothing shown and adds rings; opening starts from all of it and
        // takes them away. Either way the set ends agreeing with the iris flag.
        preview.irisShownEverywhere(!preview.isGateIrisActive());
        restyle(preview);
        // Marked before the first ring: the layers stay where they are until the sweep ends,
        // and the first draw is part of the sweep.
        preview.sweeping(true);
        stepIrisSweep(owner, preview, ringsOfOpening(preview, preview.isGateIrisActive()), 0);
    }

    /**
     * The opening's cell indexes, grouped into rings in the order they are drawn.
     *
     * <p>Mapped back by identity: {@link IrisSweep} groups the very objects it is handed, so a
     * location found in a ring is the one built for that cell and no other. Equality would do
     * the wrong thing on an opening with two cells at the same coordinates, which a malformed
     * shape can produce.
     *
     * @param preview
     *            the preview
     * @param closing
     *            true for the closing order, rim first
     * @return one list of indexes per ring
     */
    private static List<List<Integer>> ringsOfOpening(final GatePreview preview, final boolean closing)
    {
        final List<Cell> cells = preview.opening();
        final List<Location> places = new ArrayList<>(cells.size());
        final java.util.IdentityHashMap<Location, Integer> index = new java.util.IdentityHashMap<>();
        for (int i = 0; i < cells.size(); i++)
        {
            final Cell cell = cells.get(i);
            final Location at = new Location(preview.world(), cell.x(), cell.y(), cell.z());
            places.add(at);
            index.put(at, i);
        }
        final IrisSweep.Style style = ConfigManager.getGateIrisStyle();
        final int maxSteps = ConfigManager.getGateIrisMaxSteps();
        final List<List<Location>> rings = closing
            ? IrisSweep.closingOrder(places, style, maxSteps) : IrisSweep.openingOrder(places, style, maxSteps);
        final List<List<Integer>> out = new ArrayList<>(rings.size());
        for (final List<Location> ring : rings)
        {
            final List<Integer> ringIndexes = new ArrayList<>(ring.size());
            for (final Location at : ring)
            {
                ringIndexes.add(index.get(at));
            }
            out.add(ringIndexes);
        }
        return out;
    }

    /** Draws one ring of the sweep and books the next. */
    private static void stepIrisSweep(final Player owner, final GatePreview preview,
        final List<List<Integer>> rings, final int ring)
    {
        if ((ring >= rings.size()) || !stillHeld(preview))
        {
            final boolean finished = ring >= rings.size();
            irisSweeps.remove(preview);
            preview.sweeping(false);
            if (finished && stillHeld(preview))
            {
                // Dropped from the sweep map first, so this draw is the one that stacks: the
                // wormhole moves behind the ring, and a viewer round the back gets it in the
                // ring with the iris beyond, now that there is no sweep left to cover.
                draw(owner, preview);
            }
            return;
        }
        for (final int cell : rings.get(ring))
        {
            if (preview.isGateIrisActive())
            {
                preview.irisShown().add(cell);
            }
            else
            {
                preview.irisShown().remove(cell);
            }
        }
        draw(owner, preview);
        sweepHorizon(owner, preview, rings.get(ring), preview.isGateIrisActive());
        // Through `later`, not the scheduler directly: it is the seam the dial animation books
        // its own steps with, and the one a test swaps out to drive an animation by hand.
        irisSweeps.put(preview, irisLater.after(ConfigManager.getGateIrisStepTicks(),
            () -> stepIrisSweep(owner, preview, rings, ring + 1)));
    }

    /**
     * Moves the wormhole behind the ring for the cells one sweep step has just covered.
     *
     * <p>A preview's iris is a display entity standing in the same cell as the wormhole rather
     * than a block replacing it, so an opaque one simply hides the water and needs nothing
     * here. A see-through one does not hide it -- the game declines to draw a liquid behind a
     * translucent block at all -- so the cell reads as empty, and a gate sweeping shut appeared
     * to erase its own wormhole a ring at a time.
     *
     * <p>So the wormhole moves behind the ring as each ring of iris covers it, in the ice that
     * stands in for it there, and comes back as each ring uncovers. The cells the sweep has not
     * reached keep the real water in the ring, which is what makes the iris look like it is
     * covering something.
     *
     * @param owner
     *            the preview's owner
     * @param preview
     *            the preview
     * @param ringCells
     *            the opening indexes this step covered or uncovered
     * @param covering
     *            true while the iris is closing, false while it opens
     */
    private static void sweepHorizon(final Player owner, final GatePreview preview,
        final List<Integer> ringCells, final boolean covering)
    {
        if (!preview.open() || (preview.grid().facing() == null)
            || !DrawnHorizon.standsIn(preview.palette().iris()))
        {
            return;
        }
        for (final Player viewer : watching(owner, preview))
        {
            final List<IrisLayering.Placement> layers = layersFor(preview, viewer.getLocation());
            preview.sweepSides().put(viewer.getUniqueId(), layers);
            for (final int index : ringCells)
            {
                sweepHorizonAt(viewer, preview, layers, index, covering);
            }
        }
    }

    /**
     * Moves a viewer's stand-in wormhole with them when they cross a preview mid-sweep (#442).
     *
     * <p>The sweep puts the ice for each covered ring on the far side from where the viewer
     * stood at that step. Walking round left it on their old far side, now their near side: a
     * solid block the server does not have, a pace from the preview. Every covered cell hands
     * back its old offsets and takes its ice on the new far side.
     */
    private static void resweepIfCrossed(final Player player, final GatePreview preview, final Location to)
    {
        final BlockFace facing = preview.grid().facing();
        // Not recorded: the sweep drew this viewer nothing off the ring, as for an opaque iris.
        if (!preview.open() || (facing == null) || !preview.sweepSides().containsKey(player.getUniqueId()))
        {
            return;
        }
        final List<IrisLayering.Placement> now = layersFor(preview, to);
        if (now.equals(preview.sweepSides().put(player.getUniqueId(), now)))
        {
            return;
        }
        for (final int index : preview.irisShown())
        {
            final IrisLayering.At ring = at(preview.opening().get(index));
            final IrisLayering.At where = now.get(index).horizon();
            // The ring keeps its wormhole where there is nowhere beyond it to stand in, as the
            // sweep itself does: handing it back emptied the ring until the next step.
            final IrisLayering.At kept = (where == null) ? ring : where;
            for (final IrisLayering.At back : IrisLayering.handBacks(ring, facing, kept))
            {
                takeBackAt(player, preview, back);
            }
            if ((where != null) && !where.equals(ring))
            {
                player.sendBlockChange(new Location(preview.world(), where.x(), where.y(), where.z()),
                    horizonData(preview, ring, now.get(index)));
            }
            else
            {
                // The ring keeps the wormhole here, but the sweep may have taken this viewer's back
                // when it covered the ring from their old side.
                sendTo(player, preview, List.of(preview.opening().get(index)));
            }
        }
    }

    /**
     * One cell of one viewer's sweep step.
     *
     * @param viewer
     *            the viewer
     * @param preview
     *            the preview
     * @param layers
     *            where their layers go, from {@link #layersFor}
     * @param index
     *            the opening cell's index
     * @param covering
     *            true while the iris is closing, false while it opens
     */
    private static void sweepHorizonAt(final Player viewer, final GatePreview preview,
        final List<IrisLayering.Placement> layers, final int index, final boolean covering)
    {
        final IrisLayering.At ring = at(preview.opening().get(index));
        final IrisLayering.At where = layers.get(index).horizon();
        if ((where == null) || where.equals(ring))
        {
            // A viewer behind the gate keeps the wormhole in the ring, where the sweep's own
            // draw has already put it, and the iris display goes beyond it rather than over it.
            return;
        }
        if (covering)
        {
            takeBackAt(viewer, preview, ring);
            viewer.sendBlockChange(new Location(preview.world(), where.x(), where.y(), where.z()),
                horizonData(preview, ring, layers.get(index)));
        }
        else
        {
            takeBackAt(viewer, preview, where);
        }
    }

    /**
     * Whether a preview is still one somebody holds.
     *
     * <p>A step that fired after its preview was cleared would spawn fresh displays for one
     * nobody is holding any more, and nothing left would ever take them away again.
     *
     * @param preview
     *            the preview
     * @return true if it is still in the register
     */
    private static boolean stillHeld(final GatePreview preview)
    {
        for (final List<GatePreview> held : PREVIEWS.values())
        {
            if (held.contains(preview))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Calls off a sweep running on a preview, leaving its iris where it was going.
     *
     * <p>Called when the iris is toggled again mid-sweep, and when a preview goes away -- a
     * step that fired afterwards would spawn displays for a preview nobody is holding any more.
     *
     * @param preview
     *            the preview
     */
    static void cancelIrisSweep(final GatePreview preview)
    {
        final BukkitTask task = irisSweeps.remove(preview);
        preview.sweeping(false);
        if (task != null)
        {
            task.cancel();
        }
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
        // The iris beyond the ring wears the same block: restyling only the ring's set left a
        // viewer behind the gate looking at the palette the preview had before.
        for (final BlockDisplay display : preview.beyondDisplays())
        {
            if (display != null)
            {
                display.setBlock(openingData(preview));
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

    /** Restyles only the given cells: the ring's light moves every tick, and the rest stands still. */
    private static void restyle(final GatePreview preview, final Set<Cell> cells)
    {
        for (final Cell cell : cells)
        {
            final int i = preview.cells().indexOf(cell);
            final BlockDisplay display = (i < 0) ? null : preview.displays().get(i);
            if (display != null)
            {
                display.setBlock(dataFor(preview, cell));
            }
        }
    }

    /**
     * What a frame cell shows now: lit while its wave is or the ring's light is on it, and as built
     * otherwise. Under UNIVERSE the locked chevrons ride round with the ring until the last locks,
     * so only its light counts till then.
     */
    static BlockData dataFor(final GatePreview preview, final Cell cell)
    {
        final boolean riding = spins(preview) && (pattern(preview) == DialSpinPattern.UNIVERSE)
            && (preview.litWaves() < preview.lastWave());
        final boolean lit = (!riding && (cell.wave() > 0) && (cell.wave() <= preview.litWaves()))
            || preview.spinCells().contains(cell);
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

    /** The sweep running on each preview, so a second toggle can call the first off. */
    private static final java.util.Map<GatePreview, BukkitTask> irisSweeps = new java.util.IdentityHashMap<>();

    /** What the opening's displays show: they stand only while the iris is closed. */
    static BlockData openingData(final GatePreview preview)
    {
        return MaterialUtils.laidAcross(blockData.apply(preview.palette().iris()), preview.grid().facing());
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
        // No Orientable branch here on purpose: a blueprint's cells are frame, chevron, button
        // and dial sign only -- the opening is a list of its own -- so there is nothing in this
        // one that wants laying in the gate's plane. openingData is where that happens.
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
