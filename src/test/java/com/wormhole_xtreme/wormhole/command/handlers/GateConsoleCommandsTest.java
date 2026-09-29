package com.wormhole_xtreme.wormhole.command.handlers;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.ProxiedCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.command.Dial;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint;
import com.wormhole_xtreme.wormhole.logic.GateGrid;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.Stargate3DShape;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.StargateShape;
import com.wormhole_xtreme.wormhole.model.StargateShapeRegistry;
import com.wormhole_xtreme.wormhole.model.preview.GatePreviews;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions;

/**
 * Building a gate from a line of words, with nobody standing there.
 *
 * <p>{@code gate build <shape> <name> <world> <x> <y> <z> <facing>} is for the console, command blocks
 * and scripts, and it builds blocks into the world. So everything wrong with the line is refused before
 * anything is placed: a half-built frame left behind by a typo is worse than a refusal.
 */
class GateConsoleCommandsTest
{
    private static String[] line(final String... words)
    {
        return words;
    }

    /** Runs the line check with one 3D shape called Standard, one world, and one gate called Taken. */
    private static String refusal(final String... words)
    {
        try (MockedStatic<StargateShapeRegistry> shapes = mockStatic(StargateShapeRegistry.class);
             MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<StargateManager> gates = mockStatic(StargateManager.class))
        {
            final Stargate3DShape standard = mock(Stargate3DShape.class);
            final StargateShape flat =
                mock(StargateShape.class);
            final World world = mock(World.class);
            shapes.when(() -> StargateShapeRegistry.isStargateShape("Standard")).thenReturn(true);
            shapes.when(() -> StargateShapeRegistry.getStargateShape("Standard")).thenReturn(standard);
            shapes.when(() -> StargateShapeRegistry.isStargateShape("Flat")).thenReturn(true);
            shapes.when(() -> StargateShapeRegistry.getStargateShape("Flat")).thenReturn(flat);
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            gates.when(() -> StargateManager.getStargate("Taken")).thenReturn(new Stargate());
            return GateConsoleCommands.whyNotBuildable(mock(ConsoleCommandSender.class), words);
        }
    }

    @Test
    void theCoordinateFormNeedsSevenWordsWithWholeNumbersForXYZ()
    {
        assertTrue(GateConsoleCommands.isCoordinateBuild(line("Standard", "A", "world", "0", "-60", "0", "south")));
        assertFalse(GateConsoleCommands.isCoordinateBuild(line("Standard", "obsidian")),
            "the shape-and-group form is a player's and must still reach the player path");
        assertFalse(GateConsoleCommands.isCoordinateBuild(line("Standard", "A", "world", "0", "up", "0", "south")),
            "a word where y goes is not coordinates");
        assertTrue(GateConsoleCommands.isCoordinateBuild(line("Standard", "A", "world", "~", "~1", "~-3", "south")),
            "a command block names where to build relative to itself");
    }

    /** The console is nowhere, so a ~ from it is refused before anything is placed. */
    @Test
    void aTildeFromTheConsoleIsRefused()
    {
        assertEquals("~ counts from a command block or player; from here, give whole numbers.",
            refusal("Standard", "Abydos", "world", "~", "-60", "0", "south"));
    }

    @Test
    void aGoodLineIsAccepted()
    {
        assertNull(refusal("Standard", "Abydos", "world", "0", "-60", "0", "South", "net=Traders", "idc=open"));
    }

    @Test
    void everythingWrongWithTheLineIsRefusedBeforeAnythingIsBuilt()
    {
        assertEquals("No shape called Nope.", refusal("Nope", "A", "world", "0", "0", "0", "south"));
        assertEquals("Flat is a flat shape; only a 3D shape can be built from coordinates.",
            refusal("Flat", "A", "world", "0", "0", "0", "south"),
            "a 2D shape has no blueprint to place, and saying it does not exist would be wrong");
        assertTrue(refusal("Standard", "-A", "world", "0", "0", "0", "south").contains("cannot start with '-'"),
            "a dash word is an option, so no gate may be called one");
        assertTrue(refusal("Standard", "Averylongname", "world", "0", "0", "0", "south").contains("too long"));
        assertEquals("A gate called Taken is already here.", refusal("Standard", "Taken", "world", "0", "0", "0", "south"));
        assertEquals("No world called nether is loaded.", refusal("Standard", "A", "nether", "0", "0", "0", "south"));
        assertEquals("Facing must be north, south, east or west, not up.",
            refusal("Standard", "A", "world", "0", "0", "0", "up"));
        assertTrue(refusal("Standard", "A", "world", "0", "0", "0", "south", "owner=me").startsWith("Unknown option owner=me"),
            "only net= and idc= follow the facing");
    }

    @Test
    void theOptionsAfterTheFacingAreReadAndDefaultToEmpty()
    {
        assertArrayEquals(new String[] { "open", "Traders" },
            GateConsoleCommands.optionsOf(line("S", "A", "w", "0", "0", "0", "south", "net=Traders", "idc=open")));
        assertArrayEquals(new String[] { "", "" },
            GateConsoleCommands.optionsOf(line("S", "A", "w", "0", "0", "0", "south")));
    }

    /** The success line says where to drop something through the gate and where it comes out. */
    @Test
    void theOpeningCentreIsTheMiddleOfItsBlocks()
    {
        final Stargate gate = new Stargate();
        final List<Location> portal = gate.getGatePortalBlocks();
        portal.add(new Location(null, -2, -58, -3));
        portal.add(new Location(null, -1, -58, -3));
        portal.add(new Location(null, -2, -57, -3));
        portal.add(new Location(null, -1, -57, -3));

        assertEquals("-1.0 -57.0 -2.5", GateConsoleCommands.openingCentre(gate),
            "block centres averaged, not block corners");
        assertEquals("nothing", GateConsoleCommands.openingCentre(new Stargate()));
    }

    /** A player that may configure but not dial from that gate is refused, as /dial would refuse them. */
    @Test
    void aPlayerWithoutTheRightToDialFromTheGateIsRefused()
    {
        final Player player = mock(Player.class);
        final Stargate start = new Stargate();
        try (MockedStatic<StargateManager> gates = mockStatic(StargateManager.class);
             MockedStatic<WXPermissions> perms =
                 mockStatic(WXPermissions.class);
             MockedStatic<Dial> dial =
                 mockStatic(Dial.class))
        {
            gates.when(() -> StargateManager.getStargate("Abydos")).thenReturn(start);
            // Config granted, so it is the dial right, not the config one, that refuses.
            perms.when(() -> WXPermissions.checkWXPermissions(player, WXPermissions.PermissionType.CONFIG)).thenReturn(true);
            perms.when(() -> WXPermissions.checkWXPermissions(player, start, WXPermissions.PermissionType.DIALER)).thenReturn(false);

            GateConsoleCommands.dial(player, line("Abydos", "Chulak"));

            verify(player).sendMessage(ConfigManager.MessageStrings.PERMISSION_NO.toString());
            dial.verify(() -> Dial.dialFrom(any(), any(), any()), never());
        }
    }

    /** The counterpart: with the right to dial from it, the same player dials. */
    @Test
    void aPlayerWithTheRightToDialFromTheGateDials()
    {
        final Player player = mock(Player.class);
        final Stargate start = new Stargate();
        try (MockedStatic<StargateManager> gates = mockStatic(StargateManager.class);
             MockedStatic<WXPermissions> perms = mockStatic(WXPermissions.class);
             MockedStatic<Dial> dial =
                 mockStatic(Dial.class))
        {
            gates.when(() -> StargateManager.getStargate("Abydos")).thenReturn(start);
            perms.when(() -> WXPermissions.checkWXPermissions(player, WXPermissions.PermissionType.CONFIG)).thenReturn(true);
            perms.when(() -> WXPermissions.checkWXPermissions(player, start, WXPermissions.PermissionType.DIALER)).thenReturn(true);

            GateConsoleCommands.dial(player, line("Abydos", "Chulak"));

            dial.verify(() -> Dial.dialFrom(player, start, new String[] { "Chulak" }));
        }
    }

    /**
     * A gate already open is not dialled from: a refused dial puts its start gate out, and would cut
     * off the connection it already has.
     */
    @Test
    void aGateAlreadyOpenIsNotDialledFrom()
    {
        final CommandSender console = mock(ConsoleCommandSender.class);
        final Stargate start = mock(Stargate.class);
        when(start.isGateActive()).thenReturn(true);
        when(start.getGateName()).thenReturn("Abydos");
        try (MockedStatic<StargateManager> gates = mockStatic(StargateManager.class);
             MockedStatic<Dial> dial =
                 mockStatic(Dial.class))
        {
            gates.when(() -> StargateManager.getStargate("Abydos")).thenReturn(start);

            GateConsoleCommands.dial(console, line("Abydos", "Chulak"));

            verify(console).sendMessage(contains("Abydos is already open."));
            dial.verify(() -> Dial.dialFrom(any(), any(), any()), never());
        }
    }

    /** The counterpart: a closed gate the console names is dialled. */
    @Test
    void theConsoleDialsFromAClosedGate()
    {
        final CommandSender console = mock(ConsoleCommandSender.class);
        final Stargate start = new Stargate();
        try (MockedStatic<StargateManager> gates = mockStatic(StargateManager.class);
             MockedStatic<Dial> dial =
                 mockStatic(Dial.class))
        {
            gates.when(() -> StargateManager.getStargate("Abydos")).thenReturn(start);

            GateConsoleCommands.dial(console, line("Abydos", "Chulak"));

            dial.verify(() -> Dial.dialFrom(console, start, new String[] { "Chulak" }));
        }
    }

    /** A player that may configure but not build on the network named is refused before anything is placed. */
    @Test
    void aPlayerWithoutBuildRightsOnTheNetworkIsRefusedBeforeAnythingIsPlaced()
    {
        final Player player = mock(Player.class);
        final Stargate3DShape standard = mock(Stargate3DShape.class);
        final World world = mock(World.class);
        try (MockedStatic<StargateShapeRegistry> shapes = mockStatic(StargateShapeRegistry.class);
             MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<StargateManager> gates = mockStatic(StargateManager.class);
             MockedStatic<WXPermissions> perms =
                 mockStatic(WXPermissions.class);
             MockedStatic<GatePreviews> previews = mockStatic(GatePreviews.class))
        {
            shapes.when(() -> StargateShapeRegistry.isStargateShape("Standard")).thenReturn(true);
            shapes.when(() -> StargateShapeRegistry.getStargateShape("Standard")).thenReturn(standard);
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            perms.when(() -> WXPermissions.checkWXPermissions(player, WXPermissions.PermissionType.CONFIG)).thenReturn(true);
            perms.when(() -> WXPermissions.checkWXPermissions(player, "Private", WXPermissions.PermissionType.BUILD)).thenReturn(false);

            GateConsoleCommands.build(player, line("Standard", "A", "world", "0", "-60", "0", "south", "net=Private"));

            verify(player).sendMessage(ConfigManager.MessageStrings.PERMISSION_NO.toString());
            previews.verify(() -> GatePreviews.placeAt(any(), any(), any(), any()), never());
        }
    }

    /**
     * A gate reaching past the world's floor or ceiling is refused before anything is placed.
     *
     * <p>A block set outside the build height is silently dropped, so the rest of the frame went down
     * and the console was told no gate was found in it, with no hint why.
     */
    @Test
    void aGateOutsideTheBuildHeightIsRefusedAndOneInsideIsNot()
    {
        final World world = mock(World.class);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        final com.wormhole_xtreme.wormhole.logic.GateBlueprint.Part frame =
            com.wormhole_xtreme.wormhole.logic.GateBlueprint.Part.FRAME;

        assertEquals("part of it would be below the world's floor at -64.", GateConsoleCommands.outsideHeight(world,
            List.of(new com.wormhole_xtreme.wormhole.logic.GateBlueprint.Cell(0, -65, 0, frame, 0))));
        assertEquals("part of it would be above the world's build height of 320.", GateConsoleCommands.outsideHeight(world,
            List.of(new com.wormhole_xtreme.wormhole.logic.GateBlueprint.Cell(0, 320, 0, frame, 0))));
        assertNull(GateConsoleCommands.outsideHeight(world,
            List.of(new com.wormhole_xtreme.wormhole.logic.GateBlueprint.Cell(0, -64, 0, frame, 0),
                new com.wormhole_xtreme.wormhole.logic.GateBlueprint.Cell(0, 319, 0, frame, 0))),
            "the floor and the top block are both inside");
    }

    /**
     * A gate a player has activated but not yet dialled is not taken over: their /dial would find it
     * connected, and shut the wormhole the console opened.
     */
    @Test
    void aGateBeingDialledByAPlayerIsNotDialledFrom()
    {
        final CommandSender console = mock(ConsoleCommandSender.class);
        final Stargate start = mock(Stargate.class);
        when(start.isGateLightsActive()).thenReturn(true);
        when(start.getGateName()).thenReturn("Abydos");
        try (MockedStatic<StargateManager> gates = mockStatic(StargateManager.class);
             MockedStatic<Dial> dial =
                 mockStatic(Dial.class))
        {
            gates.when(() -> StargateManager.getStargate("Abydos")).thenReturn(start);

            GateConsoleCommands.dial(console, line("Abydos", "Chulak"));

            verify(console).sendMessage(contains("Abydos is being dialled."));
            dial.verify(() -> Dial.dialFrom(any(), any(), any()), never());
        }
    }

    /** A line of four words or more that is not quite the coordinate form is answered with that form's usage. */
    @Test
    void aNearMissCoordinateLineIsToldTheCoordinateUsage()
    {
        assertTrue(refusal("Standard", "A", "world", "0.5", "-60", "0", "south").startsWith("Usage: /wormhole gate build <shape> <name> <world>"),
            "a fraction for x is not the coordinate form, and the player form would not help");
        assertTrue(refusal("Standard", "A", "world", "0", "-60", "0").startsWith("Usage: /wormhole gate build <shape> <name>"),
            "six words is one short");
    }

    @Test
    void aRefusedPlacingSaysWhyInWords()
    {
        final GatePreviews.Placed inTheWay = new GatePreviews.Placed(GatePreviews.Outcome.IN_THE_WAY,
            List.of("stone at 0 0 0"), null, null);
        assertEquals("in the way: stone at 0 0 0.", GateConsoleCommands.why(inTheWay));
        assertEquals("part of it is in a chunk that is not loaded.", GateConsoleCommands.why(
            new GatePreviews.Placed(GatePreviews.Outcome.NOT_LOADED, List.of(), null, null)));
    }

    /** A line built with one 3D shape called Standard in a world from -64 to 320, as the console. */
    private static final class Building implements AutoCloseable
    {
        final CommandSender console;
        final Stargate3DShape standard = mock(Stargate3DShape.class);
        final World world = mock(World.class);
        final GateGrid grid = new GateGrid(0, -60, 0, BlockFace.SOUTH, BlockFace.WEST);
        final MockedStatic<StargateShapeRegistry> shapes = mockStatic(StargateShapeRegistry.class);
        final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        final MockedStatic<StargateManager> gates = mockStatic(StargateManager.class);
        final MockedStatic<GateGrid> grids = mockStatic(GateGrid.class);
        final MockedStatic<GateBlueprint> blueprints = mockStatic(GateBlueprint.class);
        final MockedStatic<GatePreviews> previews = mockStatic(GatePreviews.class);

        Building()
        {
            this(mock(ConsoleCommandSender.class));
        }

        Building(final CommandSender sender)
        {
            console = sender;
            shapes.when(() -> StargateShapeRegistry.isStargateShape("Standard")).thenReturn(true);
            shapes.when(() -> StargateShapeRegistry.getStargateShape("Standard")).thenReturn(standard);
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            when(world.getName()).thenReturn("world");
            when(world.getMinHeight()).thenReturn(-64);
            when(world.getMaxHeight()).thenReturn(320);
            grids.when(() -> GateGrid.fromActivationHolder(standard, 0, -60, 0, BlockFace.SOUTH)).thenReturn(grid);
            frameAt(-60);
        }

        /** The blueprint is one frame block at that height. */
        void frameAt(final int y)
        {
            final List<GateBlueprint.Cell> cells = List.of(new GateBlueprint.Cell(0, y, 0, GateBlueprint.Part.FRAME, 0));
            blueprints.when(() -> GateBlueprint.of(standard, grid)).thenReturn(cells);
            blueprints.when(() -> GateBlueprint.openingOf(standard, grid)).thenReturn(List.of());
        }

        void build(final String... words)
        {
            GateConsoleCommands.build(console, words);
        }

        void nothingPlaced()
        {
            previews.verify(() -> GatePreviews.placeAt(any(), any(), any(), any()), never());
        }

        @Override
        public void close()
        {
            previews.close();
            blueprints.close();
            grids.close();
            gates.close();
            bukkit.close();
            shapes.close();
        }
    }

    /** A shape with no DHD has nowhere to hang the button the coordinates name. */
    @Test
    void aShapeWithNoDhdIsRefusedBeforeAnythingIsPlaced()
    {
        try (Building building = new Building())
        {
            building.grids.when(() -> GateGrid.fromActivationHolder(any(), anyInt(), anyInt(), anyInt(), any()))
                .thenReturn(null);

            building.build("Standard", "Abydos", "world", "0", "-60", "0", "south");

            verify(building.console).sendMessage(contains("Standard has no DHD to build it from."));
            building.nothingPlaced();
        }
    }

    /** The placer writes no dial signs, so a shape dialled by sign would stand undialable. */
    @Test
    void aShapeThatDialsBySignIsRefusedBeforeAnythingIsPlaced()
    {
        try (Building building = new Building())
        {
            final List<GateBlueprint.Cell> signed =
                List.of(new GateBlueprint.Cell(0, -60, 0, GateBlueprint.Part.DIAL_SIGN, 0));
            building.blueprints.when(() -> GateBlueprint.of(building.standard, building.grid)).thenReturn(signed);

            building.build("Standard", "Abydos", "world", "0", "-60", "0", "south");

            verify(building.console).sendMessage(contains("Standard dials by sign"));
            building.nothingPlaced();
        }
    }

    /** The build height is checked against the blocks the gate will take, before any is placed. */
    @Test
    void aGateReachingBelowTheFloorIsNotBuilt()
    {
        try (Building building = new Building())
        {
            building.frameAt(-65);

            building.build("Standard", "Abydos", "world", "0", "-60", "0", "south");

            verify(building.console).sendMessage(contains("Not built: part of it would be below the world's floor at -64."));
            building.nothingPlaced();
        }
    }

    /** A placing refused is reported in words, and no gate is completed. */
    @Test
    void aRefusedPlacingIsReportedAndCompletesNoGate()
    {
        try (Building building = new Building())
        {
            final GatePreviews.Placed refused =
                new GatePreviews.Placed(GatePreviews.Outcome.OUTSIDE_BORDER, List.of(), null, null);
            building.previews.when(() -> GatePreviews.placeAt(building.world, building.standard, null, building.grid))
                .thenReturn(refused);

            building.build("Standard", "Abydos", "world", "0", "-60", "0", "south");

            verify(building.console).sendMessage(contains("Not built: part of it is outside the world border."));
            building.gates.verify(() -> StargateManager.completeStargate(any(Stargate.class), any(), any(), any(), any()),
                never());
        }
    }

    /**
     * A good line loads the chunk it builds in, places the gate, takes down a preview standing there,
     * completes the gate with no owner under its name and options, and says where it opens and lands.
     */
    @Test
    void aGoodLineBuildsAndCompletesAnOwnerlessGateAndSaysWhereItOpens()
    {
        try (Building building = new Building())
        {
            final Stargate gate = mock(Stargate.class);
            final Block button = mock(Block.class);
            when(button.getX()).thenReturn(0);
            when(button.getY()).thenReturn(-60);
            when(button.getZ()).thenReturn(0);
            final List<Location> opening = new ArrayList<>(List.of(new Location(null, -2, -58, -3)));
            when(gate.getGatePortalBlocks()).thenReturn(opening);
            when(gate.getGatePlayerTeleportLocation()).thenReturn(new Location(null, -1.5, -60, -1.5));
            final GatePreviews.Placed placed = new GatePreviews.Placed(GatePreviews.Outcome.PLACED, List.of(), gate, button);
            building.previews.when(() -> GatePreviews.placeAt(building.world, building.standard, null, building.grid))
                .thenReturn(placed);

            building.build("Standard", "Abydos", "world", "0", "-60", "0", "South", "net=Traders", "idc=open");

            verify(building.world).getChunkAt(0, 0);
            building.previews.verify(() -> GatePreviews.builtAt(building.world, 0, -60, 0));
            building.gates.verify(() -> StargateManager.completeStargate(gate, (Player) null,
                "Abydos", "open", "Traders"));
            verify(building.console).sendMessage(contains("Built Abydos at 0 -60 0 in world. Opening centred on"
                + " -1.5 -57.5 -2.5; arrivals at -1.5 -60.0 -1.5."));
        }
    }

    /** A player without wormhole.config is refused, before the line is even read. */
    @Test
    void aPlayerWithoutConfigIsRefusedBuildingOrDialling()
    {
        final Player player = mock(Player.class);
        try (MockedStatic<WXPermissions> perms =
                 mockStatic(WXPermissions.class);
             MockedStatic<StargateManager> gates = mockStatic(StargateManager.class))
        {
            GateConsoleCommands.build(player, line("Standard", "Abydos", "world", "0", "-60", "0", "south"));
            GateConsoleCommands.dial(player, line("Abydos", "Chulak"));

            verify(player, times(2)).sendMessage(
                ConfigManager.MessageStrings.PERMISSION_NO.toString());
            gates.verify(() -> StargateManager.getStargate(any()), never());
        }
    }

    /** A dial line of the wrong length gets the usage, and a gate that is not there is named. */
    @Test
    void aDialWithTheWrongWordsOrNoSuchGateIsRefused()
    {
        final CommandSender console = mock(ConsoleCommandSender.class);
        try (MockedStatic<StargateManager> gates = mockStatic(StargateManager.class);
             MockedStatic<Dial> dial =
                 mockStatic(Dial.class))
        {
            GateConsoleCommands.dial(console, line("Abydos"));
            GateConsoleCommands.dial(console, line("Abydos", "Chulak", "open", "extra"));
            GateConsoleCommands.dial(console, line("Nowhere", "Chulak"));

            verify(console, times(2)).sendMessage(contains("Usage: " + GateConsoleCommands.DIAL_USAGE));
            verify(console).sendMessage(contains("No gate called Nowhere."));
            dial.verify(() -> Dial.dialFrom(any(), any(), any()), never());
        }
    }

    /**
     * A command block's ~ counts from its own block, so an adventure map names its gate relative to
     * itself. The grid is only found at 0 -60 0, so reaching the placing proves the line was read there.
     */
    @Test
    void aCommandBlockBuildsWhereItsTildesPoint()
    {
        final BlockCommandSender commandBlock = mock(BlockCommandSender.class);
        final Block itsBlock = mock(Block.class);
        when(commandBlock.getBlock()).thenReturn(itsBlock);
        try (Building building = new Building(commandBlock))
        {
            when(itsBlock.getLocation()).thenReturn(new Location(building.world, 2, -61, 3));
            final GatePreviews.Placed refused =
                new GatePreviews.Placed(GatePreviews.Outcome.OUTSIDE_BORDER, List.of(), null, null);
            building.previews.when(() -> GatePreviews.placeAt(building.world, building.standard, null, building.grid))
                .thenReturn(refused);

            building.build("Standard", "Abydos", "world", "~-2", "~1", "~-3", "south");

            verify(commandBlock).sendMessage(contains("Not built: part of it is outside the world border."));
        }
    }

    /** A player without the right to dial from the gate does not get it through a proxy as themselves. */
    @Test
    void aProxyDoesNotLendAPlayerTheRightToDial()
    {
        final Player player = mock(Player.class);
        final ProxiedCommandSender asThemselves = mock(ProxiedCommandSender.class);
        when(asThemselves.getCaller()).thenReturn(player);
        when(asThemselves.getCallee()).thenReturn(player);
        final Stargate start = new Stargate();
        try (MockedStatic<StargateManager> gates = mockStatic(StargateManager.class);
             MockedStatic<WXPermissions> perms =
                 mockStatic(WXPermissions.class);
             MockedStatic<Dial> dial =
                 mockStatic(Dial.class))
        {
            gates.when(() -> StargateManager.getStargate("Abydos")).thenReturn(start);
            perms.when(() -> WXPermissions.checkWXPermissions(player, WXPermissions.PermissionType.CONFIG)).thenReturn(true);

            GateConsoleCommands.dial(asThemselves, line("Abydos", "Chulak"));

            verify(asThemselves).sendMessage(ConfigManager.MessageStrings.PERMISSION_NO.toString());
            dial.verify(() -> Dial.dialFrom(any(), any(), any()), never());
        }
    }
}
