package com.wormhole_xtreme.wormhole.command.handlers;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.Stargate3DShape;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.StargateShapeRegistry;
import com.wormhole_xtreme.wormhole.model.preview.GatePreviews;

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
            shapes.when(() -> StargateShapeRegistry.isStargateShape("Standard")).thenReturn(true);
            shapes.when(() -> StargateShapeRegistry.getStargateShape("Standard")).thenReturn(standard);
            shapes.when(() -> StargateShapeRegistry.isStargateShape("Flat")).thenReturn(true);
            shapes.when(() -> StargateShapeRegistry.getStargateShape("Flat"))
                .thenReturn(mock(com.wormhole_xtreme.wormhole.model.StargateShape.class));
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(mock(World.class));
            gates.when(() -> StargateManager.getStargate("Taken")).thenReturn(new Stargate());
            return GateConsoleCommands.whyNotBuildable(words);
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
        final org.bukkit.entity.Player player = mock(org.bukkit.entity.Player.class);
        final Stargate start = new Stargate();
        try (MockedStatic<StargateManager> gates = mockStatic(StargateManager.class);
             MockedStatic<com.wormhole_xtreme.wormhole.permissions.WXPermissions> perms =
                 mockStatic(com.wormhole_xtreme.wormhole.permissions.WXPermissions.class);
             MockedStatic<com.wormhole_xtreme.wormhole.command.Dial> dial =
                 mockStatic(com.wormhole_xtreme.wormhole.command.Dial.class))
        {
            gates.when(() -> StargateManager.getStargate("Abydos")).thenReturn(start);
            // Config granted, so it is the dial right, not the config one, that refuses.
            perms.when(() -> com.wormhole_xtreme.wormhole.permissions.WXPermissions.checkWXPermissions(player, com.wormhole_xtreme.wormhole.permissions.WXPermissions.PermissionType.CONFIG)).thenReturn(true);
            perms.when(() -> com.wormhole_xtreme.wormhole.permissions.WXPermissions.checkWXPermissions(player, start, com.wormhole_xtreme.wormhole.permissions.WXPermissions.PermissionType.DIALER)).thenReturn(false);

            GateConsoleCommands.dial(player, line("Abydos", "Chulak"));

            verify(player).sendMessage(com.wormhole_xtreme.wormhole.config.ConfigManager.MessageStrings.PERMISSION_NO.toString());
            dial.verify(() -> com.wormhole_xtreme.wormhole.command.Dial.dialFrom(any(), any(), any()), never());
        }
    }

    /** The counterpart: with the right to dial from it, the same player dials. */
    @Test
    void aPlayerWithTheRightToDialFromTheGateDials()
    {
        final org.bukkit.entity.Player player = mock(org.bukkit.entity.Player.class);
        final Stargate start = new Stargate();
        try (MockedStatic<StargateManager> gates = mockStatic(StargateManager.class);
             MockedStatic<com.wormhole_xtreme.wormhole.permissions.WXPermissions> perms = mockStatic(com.wormhole_xtreme.wormhole.permissions.WXPermissions.class);
             MockedStatic<com.wormhole_xtreme.wormhole.command.Dial> dial =
                 mockStatic(com.wormhole_xtreme.wormhole.command.Dial.class))
        {
            gates.when(() -> StargateManager.getStargate("Abydos")).thenReturn(start);
            perms.when(() -> com.wormhole_xtreme.wormhole.permissions.WXPermissions.checkWXPermissions(player, com.wormhole_xtreme.wormhole.permissions.WXPermissions.PermissionType.CONFIG)).thenReturn(true);
            perms.when(() -> com.wormhole_xtreme.wormhole.permissions.WXPermissions.checkWXPermissions(player, start, com.wormhole_xtreme.wormhole.permissions.WXPermissions.PermissionType.DIALER)).thenReturn(true);

            GateConsoleCommands.dial(player, line("Abydos", "Chulak"));

            dial.verify(() -> com.wormhole_xtreme.wormhole.command.Dial.dialFrom(player, start, new String[] { "Chulak" }));
        }
    }

    /**
     * A gate already open is not dialled from: a refused dial puts its start gate out, and would cut
     * off the connection it already has.
     */
    @Test
    void aGateAlreadyOpenIsNotDialledFrom()
    {
        final org.bukkit.command.CommandSender console = mock(org.bukkit.command.ConsoleCommandSender.class);
        final Stargate start = mock(Stargate.class);
        when(start.isGateActive()).thenReturn(true);
        when(start.getGateName()).thenReturn("Abydos");
        try (MockedStatic<StargateManager> gates = mockStatic(StargateManager.class);
             MockedStatic<com.wormhole_xtreme.wormhole.command.Dial> dial =
                 mockStatic(com.wormhole_xtreme.wormhole.command.Dial.class))
        {
            gates.when(() -> StargateManager.getStargate("Abydos")).thenReturn(start);

            GateConsoleCommands.dial(console, line("Abydos", "Chulak"));

            verify(console).sendMessage(contains("Abydos is already open."));
            dial.verify(() -> com.wormhole_xtreme.wormhole.command.Dial.dialFrom(any(), any(), any()), never());
        }
    }

    /** The counterpart: a closed gate the console names is dialled. */
    @Test
    void theConsoleDialsFromAClosedGate()
    {
        final org.bukkit.command.CommandSender console = mock(org.bukkit.command.ConsoleCommandSender.class);
        final Stargate start = new Stargate();
        try (MockedStatic<StargateManager> gates = mockStatic(StargateManager.class);
             MockedStatic<com.wormhole_xtreme.wormhole.command.Dial> dial =
                 mockStatic(com.wormhole_xtreme.wormhole.command.Dial.class))
        {
            gates.when(() -> StargateManager.getStargate("Abydos")).thenReturn(start);

            GateConsoleCommands.dial(console, line("Abydos", "Chulak"));

            dial.verify(() -> com.wormhole_xtreme.wormhole.command.Dial.dialFrom(console, start, new String[] { "Chulak" }));
        }
    }

    /** A player that may configure but not build on the network named is refused before anything is placed. */
    @Test
    void aPlayerWithoutBuildRightsOnTheNetworkIsRefusedBeforeAnythingIsPlaced()
    {
        final org.bukkit.entity.Player player = mock(org.bukkit.entity.Player.class);
        try (MockedStatic<StargateShapeRegistry> shapes = mockStatic(StargateShapeRegistry.class);
             MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<StargateManager> gates = mockStatic(StargateManager.class);
             MockedStatic<com.wormhole_xtreme.wormhole.permissions.WXPermissions> perms =
                 mockStatic(com.wormhole_xtreme.wormhole.permissions.WXPermissions.class);
             MockedStatic<GatePreviews> previews = mockStatic(GatePreviews.class))
        {
            shapes.when(() -> StargateShapeRegistry.isStargateShape("Standard")).thenReturn(true);
            shapes.when(() -> StargateShapeRegistry.getStargateShape("Standard")).thenReturn(mock(Stargate3DShape.class));
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(mock(World.class));
            perms.when(() -> com.wormhole_xtreme.wormhole.permissions.WXPermissions.checkWXPermissions(player, com.wormhole_xtreme.wormhole.permissions.WXPermissions.PermissionType.CONFIG)).thenReturn(true);
            perms.when(() -> com.wormhole_xtreme.wormhole.permissions.WXPermissions.checkWXPermissions(player, "Private", com.wormhole_xtreme.wormhole.permissions.WXPermissions.PermissionType.BUILD)).thenReturn(false);

            GateConsoleCommands.build(player, line("Standard", "A", "world", "0", "-60", "0", "south", "net=Private"));

            verify(player).sendMessage(com.wormhole_xtreme.wormhole.config.ConfigManager.MessageStrings.PERMISSION_NO.toString());
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
        final org.bukkit.command.CommandSender console = mock(org.bukkit.command.ConsoleCommandSender.class);
        final Stargate start = mock(Stargate.class);
        when(start.isGateLightsActive()).thenReturn(true);
        when(start.getGateName()).thenReturn("Abydos");
        try (MockedStatic<StargateManager> gates = mockStatic(StargateManager.class);
             MockedStatic<com.wormhole_xtreme.wormhole.command.Dial> dial =
                 mockStatic(com.wormhole_xtreme.wormhole.command.Dial.class))
        {
            gates.when(() -> StargateManager.getStargate("Abydos")).thenReturn(start);

            GateConsoleCommands.dial(console, line("Abydos", "Chulak"));

            verify(console).sendMessage(contains("Abydos is being dialled."));
            dial.verify(() -> com.wormhole_xtreme.wormhole.command.Dial.dialFrom(any(), any(), any()), never());
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
}
