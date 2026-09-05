package com.wormhole_xtreme.wormhole.utils;

import java.util.logging.Level;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * WormholeXTreme WorldUtils.
 * 
 * @author Ben Echols (Lologarithm)
 */
public class WorldUtils
{

    /**
     * Gets the degrees from block face.
     * 
     * @param blockFace
     *            the block face
     * @return the degrees from block face
     */
    public static Float getDegreesFromBlockFace(final BlockFace blockFace)
    {
        switch (blockFace)
        {
            case NORTH :
                return (float) 180;
            case EAST :
                return (float) 270;
            case SOUTH :
                return (float) 0;
            case WEST :
                return (float) 90;
            default :
                return (float) 0;
        }
    }

    /**
     * Returns true if the material is any form of ice that we care about.
     */
    // NOTE: ice-related predicates have moved to MaterialUtils

    /**
     * Gets the inverse direction.
     * 
     * @param bf
     *            the bf
     * @return the inverse direction
     */
    public static BlockFace getInverseDirection(final BlockFace bf)
    {
        switch (bf)
        {
            case NORTH :
                return BlockFace.SOUTH;
            case SOUTH :
                return BlockFace.NORTH;
            case EAST :
                return BlockFace.WEST;
            case WEST :
                return BlockFace.EAST;
            case NORTH_EAST :
                return BlockFace.SOUTH_WEST;
            case SOUTH_WEST :
                return BlockFace.NORTH_EAST;
            case NORTH_WEST :
                return BlockFace.SOUTH_EAST;
            case SOUTH_EAST :
                return BlockFace.NORTH_WEST;
            case UP :
                return BlockFace.DOWN;
            case DOWN :
                return BlockFace.UP;
            default :
                return bf;
        }
    }

    /**
     * Lever facing data from block face.
     * 
     * @param bf
     *            the bf
     * @return the byte
     */
    public static byte getLeverFacingByteFromBlockFace(final BlockFace blockFace)
    {
        switch (blockFace)
        {
            case SOUTH :
                return (byte) 0x1;
            case NORTH :
                return (byte) 0x2;
            case WEST :
                return (byte) 0x3;
            case EAST :
                return (byte) 0x4;
            default :
                return (byte) 0x0;
        }
    }

    /**
     * Gets the lever toggle byte.
     * 
     * @param leverState
     *            the lever state byte
     * @param isActive
     *            is this an active toggle?
     * @return the lever toggle byte
     */
    public static byte getLeverToggleByte(final byte leverState, final boolean isActive)
    {
        return (byte) (isActive
            ? (leverState & 0x8) != 0x8
                ? leverState ^ 0x8
                : leverState
            : (leverState & 0x8) == 0x8
                ? leverState ^ 0x8
                : leverState);
    }

    /**
     * Gets the perpendicular right direction.
     * 
     * @param bf
     *            the bf
     * @return the perpendicular right direction
     */
    public static BlockFace getPerpendicularRightDirection(final BlockFace bf)
    {
        switch (bf)
        {
            case NORTH :
            case UP :
                return BlockFace.EAST;
            case SOUTH :
            case DOWN :
                return BlockFace.WEST;
            case EAST :
                return BlockFace.SOUTH;
            case WEST :
                return BlockFace.NORTH;
            case NORTH_EAST :
                return BlockFace.SOUTH_EAST;
            case SOUTH_WEST :
                return BlockFace.NORTH_WEST;
            case NORTH_WEST :
                return BlockFace.NORTH_EAST;
            case SOUTH_EAST :
                return BlockFace.SOUTH_WEST;
            default :
                return bf;
        }
    }

    /**
     * Get the Sign facing byte data from block face.
     * If no face is up or down we default to south (same as bukkit).
     * 
     * @param bf
     *            the bf
     * @return the byte
     */
    public static byte getSignFacingByteFromBlockFace(final BlockFace blockFace)
    {
        switch (blockFace)
        {
            case EAST :
                return (byte) 0x2;
            case WEST :
                return (byte) 0x3;
            case NORTH :
                return (byte) 0x4;
            case SOUTH :
            default :
                return (byte) 0x5;
        }
    }

    /**
     * Checks if is same block.
     * 
     * @param b1
     *            the b1
     * @param b2
     *            the b2
     * @return true, if is same block
     */
    public static boolean isSameBlock(final Block b1, final Block b2)
    {
        if ((b1 == null) || (b2 == null))
        {
            return false;
        }

        return (b1.getX() == b2.getX()) && (b1.getY() == b2.getY()) && (b1.getZ() == b2.getZ());
    }

    /**
     * Returns true if two blocks are within a 1-block radius (inclusive) of each other.
     */
    /**
     * Checks whether two locations sit in different blocks.
     *
     * <p>Movement events fire many times per block travelled — a walking player or a
     * rolling minecart generates them continuously — but gate detection only has anything
     * to say when the block changes. This is the first-line guard on those handlers.
     *
     * @param from
     *            the previous location
     * @param to
     *            the new location
     * @return true if the block coordinates differ
     */
    public static boolean hasChangedBlock(final Location from, final Location to)
    {
        if (from == null || to == null)
        {
            return true;
        }
        return from.getBlockX() != to.getBlockX()
            || from.getBlockY() != to.getBlockY()
            || from.getBlockZ() != to.getBlockZ();
    }

    public static boolean isAdjacent(final Block b1, final Block b2)
    {
        if ((b1 == null) || (b2 == null))
        {
            return false;
        }
        final int dx = Math.abs(b1.getX() - b2.getX());
        final int dy = Math.abs(b1.getY() - b2.getY());
        final int dz = Math.abs(b1.getZ() - b2.getZ());
        return (dx <= 1) && (dy <= 1) && (dz <= 1);
    }

    /**
     * Force-loads the 3x3 chunk neighbourhood centred on the given location.
     * Called when a stargate connection is established so the destination terrain
     * is ready before any entity teleports through.
     *
     * @param loc the centre of the area to pre-load
     */
    public static void forceLoadDestinationChunks(final Location loc)
    {
        if (loc == null || loc.getWorld() == null)
        {
            return;
        }
        final World w = loc.getWorld();
        final int cx = loc.getBlockX() >> 4;
        final int cz = loc.getBlockZ() >> 4;
        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dz = -1; dz <= 1; dz++)
            {
                try
                {
                    if (!w.isChunkLoaded(cx + dx, cz + dz))
                    {
                        WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Pre-loading destination chunk (" + (cx + dx) + "," + (cz + dz) + ") on: " + w.getName());
                        w.loadChunk(cx + dx, cz + dz);
                    }
                }
                catch (final Throwable ignore) {}
            }
        }
    }


    /**
     * Finds the nearest place a player can actually stand near {@code preferred}, searching
     * up before down.
     *
     * <p>A stored teleport target — a gate's arrival point, a beam destination, a place — is
     * only ever as good as the terrain was the moment it was recorded. Building up or digging
     * out around it afterward does not move the stored coordinates, so an exact teleport
     * there can land somebody buried in a block that has since risen to meet them, or
     * hanging in the air a lowered ground line no longer reaches. This walks outward from the
     * stored point until it finds somewhere that is actually standable, rather than trusting
     * the record.
     *
     * <p>Up is searched first, then down, both out to 3 blocks — matching
     * {@code max-ceiling-drop}-style reasoning elsewhere in this plugin: a small, bounded
     * search that only exists to correct terrain drift since the point was set, not to go
     * hunting for ground far from where an admin actually put it.
     *
     * @param preferred the stored location; returned as-is if it or its world is null
     * @return the nearest standable location to {@code preferred}, or {@code preferred}
     *         itself (cloned) if nothing nearby qualifies
     */
    public static Location findSafePlayerLocation(final Location preferred)
    {
        if (preferred == null || preferred.getWorld() == null)
        {
            return preferred;
        }
        final World w = preferred.getWorld();
        final int x = preferred.getBlockX();
        final int z = preferred.getBlockZ();
        final int baseY = preferred.getBlockY();

        // Prefer the exact stored location if it is safe, then search upward, then down.
        for (int dy = 0; dy <= 3; dy++)
        {
            if (isStandableAt(w, x, baseY + dy, z))
            {
                return new Location(w, x + 0.5, baseY + dy, z + 0.5, preferred.getYaw(), preferred.getPitch());
            }
        }

        for (int dy = 1; dy <= 3; dy++)
        {
            final int y = baseY - dy;
            if (y < w.getMinHeight())
            {
                break;
            }
            if (isStandableAt(w, x, y, z))
            {
                return new Location(w, x + 0.5, y, z + 0.5, preferred.getYaw(), preferred.getPitch());
            }
        }

        // Fallback to the original preferred location
        return preferred.clone();
    }

    /**
     * Checks whether a player can stand at the given block: head and feet clear, solid
     * ground underneath.
     *
     * <p>The blocks are null-checked rather than wrapped in a catch. A world can return
     * null for an unloaded or out-of-range column, and "no block there" is an ordinary
     * answer meaning not standable — not an error worth swallowing.
     *
     * @param w
     *            the world
     * @param x
     *            block x
     * @param y
     *            block y of the player's feet
     * @param z
     *            block z
     * @return true if a player can stand there
     */
    public static boolean isStandableAt(final World w, final int x, final int y, final int z)
    {
        final Block feet = w.getBlockAt(x, y, z);
        final Block head = w.getBlockAt(x, y + 1, z);
        final Block below = w.getBlockAt(x, y - 1, z);
        if (feet == null || head == null || below == null)
        {
            return false;
        }
        return feet.isPassable() && head.isPassable() && !below.isPassable();
    }

    /**
     * Schedule chunk load.
     * 
     * @param b
     *            the b
     */
    public static void scheduleChunkLoad(final Block b)
    {
        final World w = b.getWorld();
        final Chunk c = b.getChunk();
        final int cX = c.getX();
        final int cZ = c.getZ();
        if ( !w.isChunkLoaded(cX, cZ))
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Loading chunk: " + c.toString() + " on: " + w.getName());
            w.loadChunk(cX, cZ);
        }
    }

    /**
     * Schedule chunk unload.
     * 
     * @param b
     *            the b
     */
    public static void scheduleChunkUnload(final Block b)
    {
        final World w = b.getWorld();
        final Chunk c = b.getChunk();
        final int cX = c.getX();
        final int cZ = c.getZ();
        if (w.isChunkLoaded(cX, cZ))
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Scheduling chunk unload: " + c.toString() + " on: " + w.getName());
            w.unloadChunkRequest(cX, cZ);
        }
    }
}
