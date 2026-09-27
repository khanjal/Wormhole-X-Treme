package com.wormhole_xtreme.wormhole.command.handlers;

import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.command.CommandHandlerUtils;
import com.wormhole_xtreme.wormhole.command.Coordinates;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.ring.BukkitBlockProbe;
import com.wormhole_xtreme.wormhole.model.ring.BukkitGround;
import com.wormhole_xtreme.wormhole.model.ring.Ring;
import com.wormhole_xtreme.wormhole.model.ring.RingAccess;
import com.wormhole_xtreme.wormhole.model.ring.RingBlockage;
import com.wormhole_xtreme.wormhole.model.ring.RingIndex;
import com.wormhole_xtreme.wormhole.model.ring.RingManager;
import com.wormhole_xtreme.wormhole.model.ring.RingPair;
import com.wormhole_xtreme.wormhole.model.ring.RingPermissions;
import com.wormhole_xtreme.wormhole.model.ring.RingSurvey;
import com.wormhole_xtreme.wormhole.model.ring.RingTemplate;
import com.wormhole_xtreme.wormhole.model.ring.RingTransit;
import com.wormhole_xtreme.wormhole.model.ring.RingYamlManager;

/**
 * Pairing and firing transport rings by coordinates and id, with nobody standing in them.
 *
 * <p>For the console, command blocks and scripts: an adventure map that fires a ring from a pressure
 * plate, or a test with no player on the server. A player needs {@code wormhole.ring.admin}.
 */
public final class RingConsoleCommands
{
    static final String BUILD_USAGE = "/wormhole ring build <world> <x1> <y1> <z1> <x2> <y2> <z2>";

    static final String FIRE_USAGE = "/wormhole ring fire <id | world x y z>";

    private static final String USAGE = "Usage: ";

    private static final String RING_PAIR = "Ring pair ";

    private static final String BUILD = "build";

    private static final String FIRE = "fire";

    /** {@code ring build <world> <x1> <y1> <z1> <x2> <y2> <z2>}, with {@code ring} in front. */
    private static final int BUILD_WORDS = 9;

    /** {@code ring fire <world> <x> <y> <z>}, with {@code ring} in front. */
    private static final int FIRE_AT_WORDS = 6;

    private RingConsoleCommands()
    {
    }

    /**
     * Whether a verb is one of these.
     *
     * @param verb
     *            the verb, lower-cased
     * @return true for {@code build} and {@code fire}
     */
    static boolean handles(final String verb)
    {
        return BUILD.equals(verb) || FIRE.equals(verb);
    }

    /**
     * Runs {@code build} or {@code fire}.
     *
     * @param sender
     *            who asked
     * @param verb
     *            which, lower-cased
     * @param args
     *            the whole line, {@code ring} first
     */
    static void run(final CommandSender sender, final String verb, final String[] args)
    {
        if ((CommandHandlerUtils.issuer(sender) instanceof Player player) && !RingPermissions.has(player, RingPermissions.ADMIN))
        {
            sender.sendMessage("Building and firing rings by coordinates and id needs " + RingPermissions.ADMIN + ".");
            return;
        }
        if (BUILD.equals(verb))
        {
            build(sender, args);
        }
        else
        {
            fire(sender, args);
        }
    }

    /**
     * {@code ring build <world> <x1> <y1> <z1> <x2> <y2> <z2>}: pairs the two circles of slabs laid around
     * those blocks, each a block inside its circle where a player would stand to create it. The pair has
     * no owner, so it is public. Refused before anything changes if either circle or the pair is wrong.
     */
    private static void build(final CommandSender sender, final String[] args)
    {
        final String refused = whyNotReadable(sender, args);
        if (refused != null)
        {
            sender.sendMessage(refused);
            return;
        }
        final World world = Bukkit.getWorld(args[2]);
        final int[] first = Coordinates.resolve(sender, args[3], args[4], args[5]);
        final int[] second = Coordinates.resolve(sender, args[6], args[7], args[8]);
        final Ring a = ringAt(sender, world, first);
        final Ring b = (a == null) ? null : ringAt(sender, world, second);
        if (b == null)
        {
            return;
        }
        final List<String> notAPair = RingCommand.whyNotAPair(a, b);
        if (!notAPair.isEmpty())
        {
            sender.sendMessage(notAPair.get(0));
            return;
        }
        final RingPair pair = RingCommand.newPair(world.getName(), a, b);
        // Nobody owns it, so a private pair would let no player in at all.
        pair.setAccess(RingAccess.PUBLIC);
        RingCommand.consumeTemplate(world, a, sender.getName());
        RingCommand.consumeTemplate(world, b, sender.getName());
        RingManager.addPair(pair, ConfigManager.getRingReach());
        RingYamlManager.saveWorld(world.getName());
        sender.sendMessage(RING_PAIR + pair.getId() + " is live and public. Arrivals at " + arrival(a) + " and "
            + arrival(b) + ". Fire it with /wormhole ring fire " + pair.getId() + ".");
    }

    /** What is wrong with a build line before any block is read; null if nothing. */
    static String whyNotReadable(final CommandSender sender, final String[] args)
    {
        if (args.length != BUILD_WORDS)
        {
            return USAGE + BUILD_USAGE;
        }
        for (int i = 3; i < BUILD_WORDS; i++)
        {
            if (!Coordinates.isCoordinate(args[i]))
            {
                return USAGE + BUILD_USAGE;
            }
        }
        final World world = Bukkit.getWorld(args[2]);
        if (world == null)
        {
            return "No world called " + args[2] + " is loaded.";
        }
        final String first = Coordinates.whyNotReadable(sender, world, args[3], args[4], args[5]);
        return (first != null) ? first : Coordinates.whyNotReadable(sender, world, args[6], args[7], args[8]);
    }

    /**
     * The ring laid around a block, checked as {@code ring create} checks one, or null with the
     * reason sent.
     */
    private static Ring ringAt(final CommandSender sender, final World world, final int[] at)
    {
        final String where = " (at " + at[0] + " " + at[1] + " " + at[2] + ")";
        final RingTemplate.Result found = RingTemplate.detect(new BukkitBlockProbe(world), at[0], at[1], at[2],
            ConfigManager.getRingReach(), ConfigManager.getRingDefaultLight());
        if (!found.isSuccess())
        {
            sender.sendMessage(RingCommand.explain(found.getFailure()) + where);
            return null;
        }
        final Ring ring = found.getRing();
        final RingManager.Refusal refusal =
            RingManager.checkPlacement(ring, world.getName(), ConfigManager.getRingMinSeparation());
        if (refusal != null)
        {
            sender.sendMessage(RingCommand.explain(refusal) + where);
            return null;
        }
        if (RingCommand.touchesGate(world, ring))
        {
            sender.sendMessage("That circle overlaps a stargate. Rings and gates cannot share blocks." + where);
            return null;
        }
        final RingBlockage roomFor =
            RingSurvey.survey(new BukkitGround(world), ring, ConfigManager.getRingMaxCeilingDrop());
        if (roomFor != null)
        {
            sender.sendMessage(RingCommand.explain(roomFor) + where);
            return null;
        }
        return ring;
    }

    /** Where a ring puts whoever arrives: the middle of its pad, as "x y z". */
    static String arrival(final Ring ring)
    {
        return String.format(Locale.ROOT, "%.1f %d %.1f", ring.getAnchorX() + 0.5, ring.stackBase(),
            ring.getAnchorZ() + 0.5);
    }

    /**
     * {@code ring fire <id>} or {@code ring fire <world> <x> <y> <z>}, a block inside either end: starts
     * a pair's cycle as somebody stepping into it would. Whatever is inside either end when it flashes
     * goes to the other; with nothing in either, it stands down. By coordinates, a map's command block
     * fires the ring beside it without knowing the id the pair was given where it was built.
     */
    private static void fire(final CommandSender sender, final String[] args)
    {
        final RingPair pair = (args.length == FIRE_AT_WORDS) ? pairAt(sender, args) : pairNamed(sender, args);
        if (pair == null)
        {
            return;
        }
        if (RingTransit.start(pair, null, false))
        {
            sender.sendMessage(RING_PAIR + pair.describe() + " is counting down.");
            return;
        }
        sender.sendMessage(RING_PAIR + pair.describe() + " did not fire: it is already cycling or cooling"
            + " down, an end is blocked, or its world is not loaded.");
    }

    /** The pair {@code ring fire <id>} names, or null with the reason sent. */
    private static RingPair pairNamed(final CommandSender sender, final String[] args)
    {
        if (args.length != 3)
        {
            sender.sendMessage(USAGE + FIRE_USAGE);
            return null;
        }
        final RingPair pair = RingManager.getPair(args[2]);
        if (pair == null)
        {
            sender.sendMessage("No ring pair " + args[2] + ".");
        }
        return pair;
    }

    /** The pair with an end around {@code <world> <x> <y> <z>}, or null with the reason sent. */
    private static RingPair pairAt(final CommandSender sender, final String[] args)
    {
        final World world = Bukkit.getWorld(args[2]);
        if (world == null)
        {
            sender.sendMessage("No world called " + args[2] + " is loaded.");
            return null;
        }
        if (!Coordinates.isCoordinate(args[3]) || !Coordinates.isCoordinate(args[4]) || !Coordinates.isCoordinate(args[5]))
        {
            sender.sendMessage(USAGE + FIRE_USAGE);
            return null;
        }
        final String unreadable = Coordinates.whyNotReadable(sender, world, args[3], args[4], args[5]);
        if (unreadable != null)
        {
            sender.sendMessage(unreadable);
            return null;
        }
        final int[] at = Coordinates.resolve(sender, args[3], args[4], args[5]);
        RingIndex.RingEnd end = RingIndex.volumeAt(world.getName(), at[0], at[1], at[2]);
        if (end == null)
        {
            end = RingIndex.perimeterAt(world.getName(), at[0], at[1], at[2]);
        }
        if (end == null)
        {
            sender.sendMessage("No ring at " + at[0] + " " + at[1] + " " + at[2] + " in " + world.getName() + ".");
            return null;
        }
        return end.getPair();
    }
}
