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
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Cell;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Palette;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Part;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Role;
import com.wormhole_xtreme.wormhole.logic.GateGrid;
import com.wormhole_xtreme.wormhole.model.GateSounds;
import com.wormhole_xtreme.wormhole.model.MaterialGroup;
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
        CHEVRONS_SHOWN
    }

    /** Runs a step of a dial later; tests step a dial by hand instead. */
    interface Later
    {
        BukkitTask after(long ticks, Runnable step);
    }

    static LongSupplier clock = System::currentTimeMillis;
    static Function<Material, BlockData> blockData = Bukkit::createBlockData;
    static Function<UUID, Player> online = Bukkit::getPlayer;
    static Later later = GatePreviews::schedule;

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
        final GatePreview preview = new GatePreview(at.getWorld(), shape, grid, Palette.of(shape, group),
            GateBlueprint.of(shape, grid), GateBlueprint.openingOf(shape, grid), GateBlueprint.wooshOf(shape, grid));
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
        sound(owner, preview.irisClosed() ? ConfigManager.getGateSoundIrisClose() : ConfigManager.getGateSoundIrisOpen(),
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
            sound(owner, ConfigManager.getGateSoundChevron(),
                GateSounds.chevronPitch(preview.litWaves(), preview.lastWave()));
            restyle(preview);
            // A real gate starts its woosh the tick after its last chevron.
            next(owner, preview, (preview.litWaves() < preview.lastWave())
                ? preview.shape().getShapeLightTicks() : 1L);
            return;
        }
        final int stage = preview.wooshStage();
        final int steps = preview.lastWoosh();
        if ((stage == 0) && (steps > 0))
        {
            sound(owner, ConfigManager.getGateSoundKawoosh(), GateSounds.KAWOOSH_PITCH);
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
            sound(owner, ConfigManager.getGateSoundClose(), 1.0f);
            restyle(preview);
            draw(owner, preview);
            return Control.SHUT_DOWN;
        }
        sound(owner, ConfigManager.getGateSoundActivate(), 1.0f);
        next(owner, preview, preview.shape().getShapeLightTicks());
        return Control.DIALLING;
    }

    /** The woosh's cells at one step, counted from 0 as {@link WooshSequence} does; the shape's W# from 1. */
    private static List<Cell> wooshStep(final GatePreview preview, final int index)
    {
        return preview.woosh().stream().filter(cell -> cell.wave() == (index + 1)).toList();
    }

    /**
     * Sends the owner the wormhole's material at some cells, as fake blocks: a block display draws no
     * liquid, and a real gate draws its wormhole the same way.
     */
    private static void send(final Player owner, final GatePreview preview, final List<Cell> cells)
    {
        final BlockData portal = blockData.apply(preview.palette().portal());
        for (final Cell cell : cells)
        {
            owner.sendBlockChange(new Location(preview.world(), cell.x(), cell.y(), cell.z()), portal);
            preview.sent().add(GatePreview.key(cell));
        }
    }

    /**
     * Shows the owner what really stands at cells a fake block was sent to. An unloaded chunk is left
     * alone: the client gets it afresh when it loads.
     */
    private static void takeBack(final Player owner, final GatePreview preview, final List<Cell> cells)
    {
        for (final Cell cell : cells)
        {
            if (preview.sent().remove(GatePreview.key(cell))
                && preview.world().isChunkLoaded(cell.x() >> 4, cell.z() >> 4))
            {
                owner.sendBlockChange(new Location(preview.world(), cell.x(), cell.y(), cell.z()),
                    preview.world().getBlockAt(cell.x(), cell.y(), cell.z()).getBlockData());
            }
        }
    }

    /** Takes back every fake block a preview sent, where its owner is still here to see them. */
    private static void takeBackAll(final Player owner, final GatePreview preview)
    {
        if ((owner != null) && preview.world().equals(owner.getWorld()))
        {
            takeBack(owner, preview, preview.woosh());
            takeBack(owner, preview, preview.opening());
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

    private static void sound(final Player owner, final String sound, final float pitch)
    {
        if (ConfigManager.isGateSoundsEnabled())
        {
            Sounds.playTo(owner, sound, ConfigManager.getGateSoundVolume(), pitch);
        }
    }

    /**
     * Brings the entities in line with what the preview should show: spawns what is missing where
     * its chunk is loaded, and removes what should not be there.
     */
    private static void draw(final Player owner, final GatePreview preview)
    {
        final World world = preview.world();
        for (int i = 0; i < preview.cells().size(); i++)
        {
            final Cell cell = preview.cells().get(i);
            if (!preview.showing(cell))
            {
                GatePreview.removeAt(preview.displays(), i);
                continue;
            }
            spawnMissing(owner, preview.displays(), i, world, cell, dataFor(preview, cell));
        }
        for (int i = 0; i < preview.opening().size(); i++)
        {
            if (!preview.openingFilled())
            {
                GatePreview.removeAt(preview.openingDisplays(), i);
                continue;
            }
            spawnMissing(owner, preview.openingDisplays(), i, world, preview.opening().get(i), openingData(preview));
        }
        if (preview.open() && !preview.irisClosed())
        {
            send(owner, preview, preview.opening());
        }
        drawButton(owner, preview);
    }

    private static void spawnMissing(final Player owner, final List<BlockDisplay> shown, final int i,
        final World world, final Cell cell, final BlockData data)
    {
        final BlockDisplay existing = shown.get(i);
        if (((existing != null) && existing.isValid()) || !world.isChunkLoaded(cell.x() >> 4, cell.z() >> 4))
        {
            return;
        }
        shown.set(i, HiddenEntities.spawnFor(WormholeXTreme.getThisPlugin(), owner,
            new Location(world, cell.x(), cell.y(), cell.z()), BlockDisplay.class, display ->
            {
                display.setBlock(data);
                display.setBrightness(FULL_BRIGHT);
            }));
    }

    /** The invisible box over the preview's button that a click lands on. */
    private static void drawButton(final Player owner, final GatePreview preview)
    {
        final Cell cell = preview.buttonCell();
        if ((cell == null) || preview.dhdHidden())
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
