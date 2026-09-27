package com.wormhole_xtreme.wormhole.command;

import org.bukkit.Location;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ProxiedCommandSender;
import org.bukkit.entity.Entity;

/**
 * Block coordinates as typed into a command: a whole number, or {@code ~} with an optional whole number
 * after it, counted from the command block or player that typed it, as vanilla commands read them.
 */
public final class Coordinates
{
    private static final char RELATIVE = '~';

    private static final String RELATIVE_ALONE = String.valueOf(RELATIVE);

    private Coordinates()
    {
    }

    /**
     * Whether a word is a block coordinate.
     *
     * @param word
     *            the word typed
     * @return true for {@code 12}, {@code -60}, {@code ~} or {@code ~-3}
     */
    public static boolean isCoordinate(final String word)
    {
        return isWhole(isRelative(word) ? word.substring(1) : word) || RELATIVE_ALONE.equals(word);
    }

    /**
     * Whether a word counts from where the sender is.
     *
     * @param word
     *            the word typed
     * @return true when it starts with {@code ~}
     */
    public static boolean isRelative(final String word)
    {
        return !word.isEmpty() && (word.charAt(0) == RELATIVE);
    }

    /**
     * Where the sender is, for {@code ~} to count from: a command block's own block, an entity's
     * block, or whoever an {@code execute as} runs the command as. Null for the console, which is
     * nowhere.
     *
     * @param sender
     *            who typed it
     * @return where they are, or null
     */
    public static Location origin(final CommandSender sender)
    {
        if (sender instanceof BlockCommandSender block)
        {
            return block.getBlock().getLocation();
        }
        if (sender instanceof Entity entity)
        {
            return entity.getLocation();
        }
        if (sender instanceof ProxiedCommandSender proxied)
        {
            return origin(proxied.getCallee());
        }
        return null;
    }

    /**
     * Reads one coordinate, which {@link #isCoordinate} has already accepted.
     *
     * @param word
     *            the word typed
     * @param from
     *            the block coordinate on the same axis that {@code ~} counts from; unused for a
     *            whole number
     * @return the block coordinate
     */
    static int axis(final String word, final int from)
    {
        if (!isRelative(word))
        {
            return Integer.parseInt(word);
        }
        return (word.length() == 1) ? from : (from + Integer.parseInt(word.substring(1)));
    }

    /**
     * Why three coordinates cannot be read in that world, or null if they can: {@code ~} from the
     * console, which is nowhere, or from somewhere in another world.
     *
     * @param sender
     *            who typed them
     * @param world
     *            the world named with them
     * @param xyz
     *            the three words, each already accepted by {@link #isCoordinate}
     * @return the reason, or null
     */
    public static String whyNotReadable(final CommandSender sender, final org.bukkit.World world,
        final String... xyz)
    {
        if (!(isRelative(xyz[0]) || isRelative(xyz[1]) || isRelative(xyz[2])))
        {
            return null;
        }
        final Location from = origin(sender);
        if (from == null)
        {
            return "~ counts from a command block or player; from here, give whole numbers.";
        }
        // A place with no world cannot be said to be in this one, so it is refused rather than guessed at.
        if ((from.getWorld() == null) || !from.getWorld().equals(world))
        {
            return "~ counts from where the command was run, in "
                + ((from.getWorld() == null) ? "no loaded world" : from.getWorld().getName()) + ", not "
                + world.getName() + ".";
        }
        return null;
    }

    /**
     * Reads three coordinates that {@link #whyNotReadable} has passed.
     *
     * @param sender
     *            who typed them
     * @param xyz
     *            the three words
     * @return the block's x, y and z
     */
    public static int[] resolve(final CommandSender sender, final String... xyz)
    {
        final Location from = origin(sender);
        final int[] base = (from == null) ? new int[3]
            : new int[] { from.getBlockX(), from.getBlockY(), from.getBlockZ() };
        return new int[] { axis(xyz[0], base[0]), axis(xyz[1], base[1]), axis(xyz[2], base[2]) };
    }

    private static boolean isWhole(final String word)
    {
        try
        {
            Integer.parseInt(word);
            return true;
        }
        catch (final NumberFormatException notOne)
        {
            return false;
        }
    }
}
