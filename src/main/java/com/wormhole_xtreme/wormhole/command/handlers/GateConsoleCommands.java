package com.wormhole_xtreme.wormhole.command.handlers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.command.CommandHandlerUtils;
import com.wormhole_xtreme.wormhole.command.Complete;
import com.wormhole_xtreme.wormhole.command.Coordinates;
import com.wormhole_xtreme.wormhole.command.Dial;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.integration.RegionFlags;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint;
import com.wormhole_xtreme.wormhole.logic.GateGrid;
import com.wormhole_xtreme.wormhole.model.MaterialGroup;
import com.wormhole_xtreme.wormhole.model.MaterialGroupRegistry;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.Stargate3DShape;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.StargateShapeRegistry;
import com.wormhole_xtreme.wormhole.model.preview.GatePreviews;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions.PermissionType;

/**
 * Building and dialling gates by name and coordinates, with nobody standing there.
 *
 * <p>For the console, command blocks and scripts: a server set up by script, or a test with no
 * player on it. Both need {@code wormhole.config}, which the console always has.
 */
public final class GateConsoleCommands
{
    /** How many words {@code build <shape> <name> <world> <x> <y> <z> <facing>} takes. */
    public static final int BUILD_WORDS = 7;

    /** The player form takes at most a shape and a group, so four words or more mean coordinates. */
    public static final int SHORTEST_COORDINATE_ATTEMPT = 4;

    static final String BUILD_USAGE =
        "/wormhole gate build <shape> <name> <world> <x> <y> <z> <facing> [net=NET] [idc=IDC]";

    static final String DIAL_USAGE = "/wormhole gate dial <from> <to> [idc]";

    private GateConsoleCommands()
    {
    }

    /**
     * Whether {@code gate build} was given coordinates rather than a shape and group.
     *
     * @param rest
     *            the words after {@code build}
     * @return true for the coordinate form
     */
    public static boolean isCoordinateBuild(final String[] rest)
    {
        return (rest.length >= BUILD_WORDS) && Coordinates.isCoordinate(rest[3]) && Coordinates.isCoordinate(rest[4])
            && Coordinates.isCoordinate(rest[5]);
    }

    /**
     * {@code gate build <shape> <name> <world> <x> <y> <z> <facing> [net=NET] [idc=IDC]}: builds the shape
     * with its DHD button hung on the block at x y z, facing that way, and completes it under that name.
     * x y z may be {@code ~}, counted from the command block or player that ran it.
     * The gate has no owner. Refused before anything is placed if any part of the line is wrong.
     *
     * @param sender
     *            who asked
     * @param rest
     *            the words after {@code build}
     */
    public static void build(final CommandSender sender, final String[] rest)
    {
        if (CommandHandlerUtils.lacksConfigPermission(sender))
        {
            return;
        }
        final String error = ConfigManager.MessageStrings.ERROR_HEADER.toString();
        final String refused = whyNotBuildable(sender, rest);
        if (refused != null)
        {
            sender.sendMessage(error + refused);
            return;
        }
        // A player needs build rights on the network as well, as completing a gate asks; the console
        // and command blocks are not held to per-network rights.
        if ((CommandHandlerUtils.issuer(sender) instanceof Player player)
            && !WXPermissions.checkWXPermissions(player, optionsOf(rest)[1], PermissionType.BUILD))
        {
            sender.sendMessage(ConfigManager.MessageStrings.PERMISSION_NO.toString());
            return;
        }
        final Stargate3DShape shape = (Stargate3DShape) StargateShapeRegistry.getStargateShape(rest[0]);
        final World world = Bukkit.getWorld(rest[2]);
        final BlockFace facing = BlockFace.valueOf(rest[6].toUpperCase(Locale.ROOT));
        final int[] at = Coordinates.resolve(sender, rest[3], rest[4], rest[5]);
        final GateGrid grid = GateGrid.fromActivationHolder(shape, at[0], at[1], at[2], facing);
        if (grid == null)
        {
            sender.sendMessage(error + rest[0] + " has no DHD to build it from.");
            return;
        }
        if (GateBlueprint.of(shape, grid).stream().anyMatch(cell -> cell.part() == GateBlueprint.Part.DIAL_SIGN))
        {
            sender.sendMessage(error + rest[0] + " dials by sign, and a sign cannot be placed for it here. Build a"
                + " shape with a DHD button.");
            return;
        }
        MaterialGroup group = MaterialGroupRegistry.getDefaultGroup();
        if ((group != null) && !shape.acceptsMaterialGroup(group.getName()))
        {
            group = null;
        }
        final String[] options = optionsOf(rest);
        final String outOfHeight = outsideHeight(world, cellsOf(shape, grid));
        if (outOfHeight != null)
        {
            sender.sendMessage(error + "Not built: " + outOfHeight);
            return;
        }
        // A player is held to the regions it would stand in, as building it by hand would be.
        if ((CommandHandlerUtils.issuer(sender) instanceof Player player)
            && !RegionFlags.mayBuild(player, locationsOf(world, cellsOf(shape, grid))))
        {
            sender.sendMessage(RegionFlags.BUILD_REFUSED);
            return;
        }
        loadChunksUnder(world, shape, grid);
        final GatePreviews.Placed placed = GatePreviews.placeAt(world, shape, group, grid);
        if (placed.outcome() != GatePreviews.Outcome.PLACED)
        {
            sender.sendMessage(error + "Not built: " + why(placed));
            return;
        }
        // A preview anyone had standing there is now built; take it down, as a DHD press does.
        GatePreviews.builtAt(world, placed.button().getX(), placed.button().getY(), placed.button().getZ());
        StargateManager.completeStargate(placed.gate(), null, rest[1], options[0], options[1]);
        // Where to drop something through it and where it comes out, for whoever is scripting this.
        sender.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER.toString() + "Built " + rest[1] + " at "
            + at[0] + " " + at[1] + " " + at[2] + " in " + world.getName() + ". Opening centred on "
            + openingCentre(placed.gate()) + "; arrivals at " + where(placed.gate().getGatePlayerTeleportLocation())
            + ".");
    }

    /** The middle of a gate's opening, block centres averaged, as "x y z" to one decimal. */
    static String openingCentre(final Stargate gate)
    {
        final List<Location> blocks = gate.getGatePortalBlocks();
        if (blocks.isEmpty())
        {
            return "nothing";
        }
        double x = 0;
        double y = 0;
        double z = 0;
        for (final Location block : blocks)
        {
            x += block.getBlockX() + 0.5;
            y += block.getBlockY() + 0.5;
            z += block.getBlockZ() + 0.5;
        }
        return String.format(Locale.ROOT, "%.1f %.1f %.1f", x / blocks.size(), y / blocks.size(), z / blocks.size());
    }

    /** A location as "x y z" to one decimal, or "nowhere". */
    static String where(final Location at)
    {
        return (at == null) ? "nowhere"
            : String.format(Locale.ROOT, "%.1f %.1f %.1f", at.getX(), at.getY(), at.getZ());
    }

    /**
     * {@code gate dial <from> <to> [idc]}: opens a wormhole from one named gate to another, under the
     * rules {@code /dial} keeps.
     *
     * @param sender
     *            who asked
     * @param rest
     *            the words after {@code dial}
     */
    public static void dial(final CommandSender sender, final String[] rest)
    {
        if (CommandHandlerUtils.lacksConfigPermission(sender))
        {
            return;
        }
        if ((rest.length < 2) || (rest.length > 3))
        {
            sender.sendMessage(ConfigManager.MessageStrings.ERROR_HEADER.toString() + "Usage: " + DIAL_USAGE);
            return;
        }
        final Stargate start = StargateManager.getStargate(rest[0]);
        if (start == null)
        {
            sender.sendMessage(ConfigManager.MessageStrings.ERROR_HEADER.toString() + "No gate called " + rest[0] + ".");
            return;
        }
        // A refused dial puts the start gate out, which would cut off a connection it already has.
        if (start.isGateActive() || (start.getGateTarget() != null))
        {
            sender.sendMessage(ConfigManager.MessageStrings.ERROR_HEADER.toString() + start.getGateName()
                + " is already open.");
            return;
        }
        // Its DHD pressed by a player who has not dialled yet: their /dial would find it taken and shut it.
        if (start.isGateLightsActive())
        {
            sender.sendMessage(ConfigManager.MessageStrings.ERROR_HEADER.toString() + start.getGateName()
                + " is being dialled.");
            return;
        }
        // A player needs the right to dial from that gate, as /dial asks.
        if ((CommandHandlerUtils.issuer(sender) instanceof Player player)
            && !WXPermissions.checkWXPermissions(player, start, PermissionType.DIALER))
        {
            sender.sendMessage(ConfigManager.MessageStrings.PERMISSION_NO.toString());
            return;
        }
        if ((CommandHandlerUtils.issuer(sender) instanceof Player player) && RegionFlags.refusesUse(player, start))
        {
            return;
        }
        Dial.dialFrom(sender, start, Arrays.copyOfRange(rest, 1, rest.length));
    }

    /**
     * Loads the chunks the gate will stand in. Named by coordinates, often with nobody near, they are
     * usually not loaded, and a preview's placing refuses an unloaded chunk.
     */
    private static void loadChunksUnder(final World world, final Stargate3DShape shape, final GateGrid grid)
    {
        final Set<Long> done = new HashSet<>();
        for (final GateBlueprint.Cell cell : cellsOf(shape, grid))
        {
            final int cx = cell.x() >> 4;
            final int cz = cell.z() >> 4;
            if (done.add((((long) cx) << 32) ^ (cz & 0xffffffffL)))
            {
                world.getChunkAt(cx, cz);
            }
        }
    }

    /** Where each of these cells stands in the world. */
    private static List<Location> locationsOf(final World world, final List<GateBlueprint.Cell> cells)
    {
        final List<Location> out = new ArrayList<>(cells.size());
        for (final GateBlueprint.Cell cell : cells)
        {
            out.add(new Location(world, cell.x(), cell.y(), cell.z()));
        }
        return out;
    }

    /** Every block the gate will take: its frame, DHD and opening. */
    private static List<GateBlueprint.Cell> cellsOf(final Stargate3DShape shape, final GateGrid grid)
    {
        final List<GateBlueprint.Cell> cells = new ArrayList<>(GateBlueprint.of(shape, grid));
        cells.addAll(GateBlueprint.openingOf(shape, grid));
        return cells;
    }

    /**
     * Why part of the gate would fall outside the world's build height, or null if none does. A block
     * set there is silently dropped, which left a part-built frame reported as a gate not found.
     */
    static String outsideHeight(final World world, final List<GateBlueprint.Cell> cells)
    {
        for (final GateBlueprint.Cell cell : cells)
        {
            if (cell.y() < world.getMinHeight())
            {
                return "part of it would be below the world's floor at " + world.getMinHeight() + ".";
            }
            if (cell.y() >= world.getMaxHeight())
            {
                return "part of it would be above the world's build height of " + world.getMaxHeight() + ".";
            }
        }
        return null;
    }

    /** What is wrong with the line, before the world is looked at for room; null if nothing. */
    static String whyNotBuildable(final CommandSender sender, final String[] rest)
    {
        if (!isCoordinateBuild(rest))
        {
            return "Usage: " + BUILD_USAGE;
        }
        if (!StargateShapeRegistry.isStargateShape(rest[0]))
        {
            return "No shape called " + rest[0] + ".";
        }
        if (!(StargateShapeRegistry.getStargateShape(rest[0]) instanceof Stargate3DShape))
        {
            return rest[0] + " is a flat shape; only a 3D shape can be built from coordinates.";
        }
        final String badName = Complete.whyNotAName(rest[1]);
        if (badName != null)
        {
            return badName.replace(ConfigManager.MessageStrings.ERROR_HEADER.toString(), "");
        }
        if (StargateManager.getStargate(rest[1]) != null)
        {
            return "A gate called " + rest[1] + " is already here.";
        }
        final World world = Bukkit.getWorld(rest[2]);
        if (world == null)
        {
            return "No world called " + rest[2] + " is loaded.";
        }
        final String unreadable = Coordinates.whyNotReadable(sender, world, rest[3], rest[4], rest[5]);
        if (unreadable != null)
        {
            return unreadable;
        }
        final String face = rest[6].toUpperCase(Locale.ROOT);
        if (!("NORTH".equals(face) || "SOUTH".equals(face) || "EAST".equals(face) || "WEST".equals(face)))
        {
            return "Facing must be north, south, east or west, not " + rest[6] + ".";
        }
        for (int i = BUILD_WORDS; i < rest.length; i++)
        {
            if (!(rest[i].startsWith("net=") || rest[i].startsWith("idc=")))
            {
                return "Unknown option " + rest[i] + "; only net= and idc= follow the facing.";
            }
        }
        return null;
    }

    /** The idc= and net= options after the facing, as {idc, network}; each empty when absent. */
    static String[] optionsOf(final String[] rest)
    {
        String idc = "";
        String network = "";
        for (int i = BUILD_WORDS; i < rest.length; i++)
        {
            if (rest[i].startsWith("idc="))
            {
                idc = rest[i].substring("idc=".length());
            }
            else if (rest[i].startsWith("net="))
            {
                network = rest[i].substring("net=".length());
            }
        }
        return new String[] { idc, network };
    }

    /** Why a placing was refused, in words for the console. */
    static String why(final GatePreviews.Placed placed)
    {
        return switch (placed.outcome())
        {
            case NOT_FINDABLE -> "no material group uses its frame block, so the gate would not be found.";
            case NOT_LOADED -> "part of it is in a chunk that is not loaded.";
            case OUTSIDE_BORDER -> "part of it is outside the world border.";
            case IN_THE_WAY -> "in the way: " + String.join(", ", placed.inTheWay()) + ".";
            case NOT_FOUND -> "its blocks were placed, but no gate was found in them.";
            default -> "it could not be placed (" + placed.outcome() + ").";
        };
    }
}
