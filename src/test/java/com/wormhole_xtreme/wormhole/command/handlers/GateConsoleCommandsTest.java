package com.wormhole_xtreme.wormhole.command.handlers;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

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
