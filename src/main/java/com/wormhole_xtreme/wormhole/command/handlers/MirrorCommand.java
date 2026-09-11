package com.wormhole_xtreme.wormhole.command.handlers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.command.CommandHandlerUtils;
import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorArrival;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorBlock;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorManager;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorPoint;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorYamlManager;
import com.wormhole_xtreme.wormhole.model.mirror.QuantumMirror;

/**
 * {@code /wormhole mirror} -- naming banners and pointing them somewhere.
 *
 * <p>Binding is two steps because the two pieces of information are in two places: you have to
 * be looking at the banner to say which one it is, and standing at the arrival spot to say
 * where it goes. No single command can be in both places.
 *
 * <pre>
 * mirror set &lt;name&gt;           look at a banner; it becomes a mirror by that name
 * mirror target &lt;name&gt;        stand where arrivals should land; point that mirror here
 * mirror link &lt;from&gt; &lt;to&gt;     point one mirror at the spot in front of another's banner
 * mirror remove &lt;name&gt;        forget it; the banner becomes an ordinary banner again
 * mirror list                 what exists and where each one goes
 * </pre>
 *
 * <p>{@code link} is sugar over {@code target}: it works the arrival spot out once and stores
 * an ordinary point, so nothing downstream knows a second mirror was involved. It is a
 * snapshot rather than a subscription -- move the target banner afterwards and the first
 * mirror still points where it used to be.
 *
 * <p>Every verb needs the config node, the same as gate and ring management: a mirror moves
 * players between worlds, which is not something to leave open to anyone who can run
 * {@code /wormhole}.
 */
public class MirrorCommand implements SubCommand
{
    /** What this command answers to, for the usage line and tab completion. */
    private static final String[] VERBS = { "set", "target", "link", "remove", "list" };

    /** @return the verbs, for the usage line built in SubCommands */
    public static String[] verbs()
    {
        return VERBS.clone();
    }

    @Override
    public boolean execute(final CommandSender sender, final String[] args)
    {
        if (CommandHandlerUtils.lacksConfigPermission(sender))
        {
            return true;
        }
        final String verb = (args.length > 1) ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (verb)
        {
            case "set":
                return set(sender, args);
            case "target":
                return target(sender, args);
            case "link":
                return link(sender, args);
            case "remove":
                return remove(sender, args);
            case "list":
                return list(sender);
            default:
                return usage(sender);
        }
    }

    private static boolean usage(final CommandSender sender)
    {
        say(sender, "Usage: /wormhole mirror <" + String.join("|", VERBS) + ">");
        say(sender, "A mirror is a banner you click to travel. Name one with 'set' while");
        say(sender, "looking at it, then point it with 'target' or 'link'.");
        return true;
    }

    /** Names the banner the player is looking at. */
    private static boolean set(final CommandSender sender, final String[] args)
    {
        final Player player = asPlayer(sender);
        if ((player == null) || !named(sender, args, "set <name>"))
        {
            return true;
        }
        final String name = args[2];
        final Block block = lookedAtBanner(player);
        if (block == null)
        {
            return true;
        }
        final QuantumMirror existing = MirrorManager.byName(name);
        final MirrorPoint keep = (existing == null) ? null : existing.destination();
        MirrorManager.add(new QuantumMirror(name, MirrorBlock.of(block), keep));
        MirrorYamlManager.saveAll();

        if (keep == null)
        {
            say(sender, "Mirror '" + name + "' is this banner. It goes nowhere yet -- stand where");
            say(sender, "you want arrivals to land and run /wormhole mirror target " + name);
        }
        else
        {
            say(sender, "Mirror '" + name + "' is this banner now, still pointing at "
                + describe(keep) + ".");
        }
        return true;
    }

    /** Points a named mirror at where the player is standing. */
    private static boolean target(final CommandSender sender, final String[] args)
    {
        final Player player = asPlayer(sender);
        if ((player == null) || !named(sender, args, "target <name>"))
        {
            return true;
        }
        final QuantumMirror mirror = known(sender, args[2]);
        return (mirror == null) || point(sender, mirror, MirrorPoint.of(player.getLocation()));
    }

    /** Points one mirror at the spot in front of another mirror's banner. */
    private static boolean link(final CommandSender sender, final String[] args)
    {
        if (args.length < 4)
        {
            say(sender, "Usage: /wormhole mirror link <from> <to>");
            return true;
        }
        final QuantumMirror from = known(sender, args[2]);
        final QuantumMirror to = known(sender, args[3]);
        if ((from == null) || (to == null))
        {
            return true;
        }
        if (from.name().equalsIgnoreCase(to.name()))
        {
            say(sender, "A mirror cannot open onto itself.");
            return true;
        }
        final Location arrival = arrivalAt(sender, to);
        return (arrival == null) || point(sender, from, MirrorPoint.of(arrival));
    }

    /**
     * Where a player should land when stepping out of a mirror's banner.
     *
     * <p>Needs the banner's own world loaded, because the facing has to be read off the live
     * block -- there is nowhere else it is recorded. A mirror in an unloaded world can still
     * be the *source* of a link; it just cannot be the target of one until its world is up.
     *
     * @return the arrival location, or null with the reason already sent
     */
    private static Location arrivalAt(final CommandSender sender, final QuantumMirror to)
    {
        final MirrorBlock banner = to.banner();
        final org.bukkit.World world = org.bukkit.Bukkit.getWorld(banner.worldName());
        if (world == null)
        {
            say(sender, "'" + to.name() + "' is in " + banner.worldName()
                + ", which is not loaded, so where its banner faces cannot be read.");
            return null;
        }
        final Location arrival =
            MirrorArrival.inFrontOf(world.getBlockAt(banner.x(), banner.y(), banner.z()));
        if (arrival == null)
        {
            say(sender, "'" + to.name() + "' is no longer a banner, so there is no front to"
                + " arrive in. Put one back, or re-run mirror set on a banner that is there.");
        }
        return arrival;
    }

    /**
     * Stores a destination, applying the cross-world rule.
     *
     * <p>The refusal is here rather than at {@code set} time because this is the first moment
     * both worlds are known -- a mirror named but not yet pointed has only one.
     *
     * @return always true; the command was handled either way
     */
    private static boolean point(final CommandSender sender, final QuantumMirror mirror,
        final MirrorPoint destination)
    {
        final QuantumMirror pointed = mirror.withDestination(destination);
        if (pointed.isSameWorld() && !ConfigManager.isMirrorAllowSameWorld())
        {
            say(sender, "A quantum mirror connects two worlds, and both ends of '"
                + mirror.name() + "' are in " + destination.worldName() + ".");
            say(sender, "Use a gate, a ring, or a beam place for travel inside one world --");
            say(sender, "or set mirror-allow-same-world to true if you want this anyway.");
            return true;
        }
        MirrorManager.add(pointed);
        MirrorYamlManager.saveAll();
        say(sender, "'" + mirror.name() + "' now opens onto " + describe(destination) + ".");
        return true;
    }

    private static boolean remove(final CommandSender sender, final String[] args)
    {
        if (!named(sender, args, "remove <name>"))
        {
            return true;
        }
        final QuantumMirror removed = MirrorManager.remove(args[2]);
        if (removed == null)
        {
            say(sender, "There is no mirror called '" + args[2] + "'.");
            return true;
        }
        MirrorYamlManager.saveAll();
        say(sender, "'" + removed.name() + "' is an ordinary banner again.");
        return true;
    }

    private static boolean list(final CommandSender sender)
    {
        final List<String> lines = new ArrayList<>();
        for (final QuantumMirror mirror : MirrorManager.all())
        {
            lines.add("  " + mirror.name() + " -- " + mirror.banner().worldName() + " -> "
                + ((mirror.destination() == null) ? "nowhere yet" : describe(mirror.destination())));
        }
        if (lines.isEmpty())
        {
            say(sender, "No mirrors yet. Look at a banner and run /wormhole mirror set <name>.");
            return true;
        }
        say(sender, lines.size() + " mirror(s):");
        lines.forEach(line -> sender.sendMessage(line));
        return true;
    }

    /** A destination as a person would read it. */
    private static String describe(final MirrorPoint point)
    {
        return point.worldName() + " at " + Math.round(point.x()) + ", " + Math.round(point.y())
            + ", " + Math.round(point.z());
    }

    /**
     * The banner the player is looking at, or null with the reason already sent.
     *
     * <p>Both banner families are accepted. Requiring a wall would rule out a banner on a post
     * in the middle of a room, which is most of what a museum corridor is made of.
     */
    private static Block lookedAtBanner(final Player player)
    {
        final Block block = player.getTargetBlockExact(6);
        if (block == null)
        {
            say(player, "Look at the banner you want to use, within six blocks.");
            return null;
        }
        if (!block.getType().name().endsWith("BANNER"))
        {
            say(player, "That is a " + block.getType().name().toLowerCase(Locale.ROOT)
                + ", not a banner. A mirror has to be a banner -- wall-mounted or"
                + " freestanding, either is fine.");
            return null;
        }
        return block;
    }

    private static QuantumMirror known(final CommandSender sender, final String name)
    {
        final QuantumMirror mirror = MirrorManager.byName(name);
        if (mirror == null)
        {
            say(sender, "There is no mirror called '" + name + "'.");
        }
        return mirror;
    }

    private static boolean named(final CommandSender sender, final String[] args, final String form)
    {
        if (args.length < 3)
        {
            say(sender, "Usage: /wormhole mirror " + form);
            return false;
        }
        return true;
    }

    private static Player asPlayer(final CommandSender sender)
    {
        if (sender instanceof Player player)
        {
            return player;
        }
        say(sender, "That has to be run in game -- it depends on where you are standing.");
        return null;
    }

    private static void say(final CommandSender sender, final String message)
    {
        sender.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER + message);
    }
}
