package com.wormhole_xtreme.wormhole.command;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.ProxiedCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/**
 * Coordinates typed into a command, with {@code ~} read as vanilla reads it: from the command block
 * or player that ran the command. An adventure map's command block names its gates and rings this
 * way, so the map still works wherever it is pasted.
 */
class CoordinatesTest
{
    private static BlockCommandSender commandBlockAt(final World world, final int x, final int y, final int z)
    {
        final BlockCommandSender sender = mock(BlockCommandSender.class);
        final Block block = mock(Block.class);
        when(sender.getBlock()).thenReturn(block);
        when(block.getLocation()).thenReturn(new Location(world, x, y, z));
        return sender;
    }

    @Test
    void wholeNumbersAndTildesAreCoordinatesAndOtherWordsAreNot()
    {
        assertTrue(Coordinates.isCoordinate("-60"));
        assertTrue(Coordinates.isCoordinate("~"));
        assertTrue(Coordinates.isCoordinate("~-3"));
        assertTrue(Coordinates.isCoordinate("~12"));
        assertFalse(Coordinates.isCoordinate("0.5"), "a block coordinate is whole");
        assertFalse(Coordinates.isCoordinate("~up"));
        assertFalse(Coordinates.isCoordinate("^1"), "local coordinates are not read");
        assertFalse(Coordinates.isCoordinate(""));
    }

    @Test
    void aTildeCountsFromTheCommandBlocksOwnBlock()
    {
        final World world = mock(World.class);
        final BlockCommandSender block = commandBlockAt(world, 10, -60, 5);

        assertArrayEquals(new int[] { 10, -57, 3 }, Coordinates.resolve(block, "~", "~3", "~-2"));
        assertArrayEquals(new int[] { 7, -57, 3 }, Coordinates.resolve(block, "7", "~3", "~-2"),
            "whole numbers and tildes mix, as in vanilla");
        assertNull(Coordinates.whyNotReadable(block, world, "~", "~3", "~-2"));
    }

    @Test
    void aTildeCountsFromAPlayerAndFromAProxysCallee()
    {
        final World world = mock(World.class);
        final Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(new Location(world, -3.7, 64.2, 8.9));
        final ProxiedCommandSender proxied = mock(ProxiedCommandSender.class);
        when(proxied.getCallee()).thenReturn(player);

        assertArrayEquals(new int[] { -4, 65, 8 }, Coordinates.resolve(player, "~", "~1", "~"),
            "the block the player stands in, not their exact position");
        assertArrayEquals(new int[] { -4, 65, 8 }, Coordinates.resolve(proxied, "~", "~1", "~"));
    }

    @Test
    void theConsoleIsNowhereSoATildeFromItIsRefused()
    {
        final World world = mock(World.class);
        final ConsoleCommandSender console = mock(ConsoleCommandSender.class);

        assertEquals("~ counts from a command block or player; from here, give whole numbers.",
            Coordinates.whyNotReadable(console, world, "0", "~", "0"));
        assertNull(Coordinates.whyNotReadable(console, world, "0", "-60", "0"));
        assertArrayEquals(new int[] { 0, -60, 0 }, Coordinates.resolve(console, "0", "-60", "0"));
    }

    @Test
    void aTildeFromAnotherWorldIsRefused()
    {
        final World here = mock(World.class);
        when(here.getName()).thenReturn("world_nether");
        final World named = mock(World.class);
        when(named.getName()).thenReturn("world");

        assertEquals("~ counts from where the command was run, in world_nether, not world.",
            Coordinates.whyNotReadable(commandBlockAt(here, 0, 0, 0), named, "~", "~", "~"));
    }

    /** A place with no world cannot be said to be in the one named, so ~ from it is refused. */
    @Test
    void aTildeFromAPlaceWithNoWorldIsRefused()
    {
        final World named = mock(World.class);
        when(named.getName()).thenReturn("world");

        assertEquals("~ counts from where the command was run, in no loaded world, not world.",
            Coordinates.whyNotReadable(commandBlockAt(null, 0, 0, 0), named, "~", "~", "~"));
    }
}
