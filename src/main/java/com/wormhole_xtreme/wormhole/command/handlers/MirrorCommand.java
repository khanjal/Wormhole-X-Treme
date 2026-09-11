package com.wormhole_xtreme.wormhole.command.handlers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
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
 * where it goes. No single command can be in both.
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
 * mirror still points where it used to.
 *
 * <p>Every verb needs the config node, the same as gate and ring management: a mirror moves
 * players between worlds, which is not something to leave open to anyone who can run
 * {@code /wormhole}.
 *
 * <p>The helpers below return {@code void} rather than the {@code true} that would let each
 * caller write {@code return helper(...)} on one line. That boolean would carry nothing, and
 * this project already went through thirty-four helpers that had it and took it out.
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

    /** True means handled, which is what Bukkit wants; every path here has handled it. */
    @SuppressWarnings("java:S3516")
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
            case "set" -> set(sender, args);
            case "target" -> target(sender, args);
            case "link" -> link(sender, args);
            case "remove" -> remove(sender, args);
            case "list" -> list(sender);
            default -> usage(sender);
        }
        return true;
    }

    private static void usage(final CommandSender sender)
    {
        say(sender, "Usage: /wormhole mirror <" + String.join("|", VERBS) + ">");
        say(sender, "A mirror is a banner you click to travel. Name one with 'set' while");
        say(sender, "looking at it, then point it with 'target' or 'link'.");
    }

    /** Names the banner the player is looking at. */
    private static void set(final CommandSender sender, final String[] args)
    {
        final Player player = asPlayer(sender);
        if ((player == null) || !named(sender, args, "set <name>"))
        {
            return;
        }
        final String name = args[2];
        final Block block = lookedAtBanner(player);
        if (block == null)
        {
            return;
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
    }

    /** Points a named mirror at where the player is standing. */
    private static void target(final CommandSender sender, final String[] args)
    {
        final Player player = asPlayer(sender);
        if ((player == null) || !named(sender, args, "target <name>"))
        {
            return;
        }
        final QuantumMirror mirror = known(sender, args[2]);
        if (mirror != null)
        {
            point(sender, mirror, MirrorPoint.of(player.getLocation()));
        }
    }

    /** Points one mirror at the spot in front of another mirror's banner. */
    private static void link(final CommandSender sender, final String[] args)
    {
        if (args.length < 4)
        {
            say(sender, "Usage: /wormhole mirror link <from> <to>");
            return;
        }
        final QuantumMirror from = known(sender, args[2]);
        final QuantumMirror to = known(sender, args[3]);
        if ((from == null) || (to == null))
        {
            return;
        }
        if (from.name().equalsIgnoreCase(to.name()))
        {
            say(sender, "A mirror cannot open onto itself.");
            return;
        }
        final Location arrival = arrivalAt(sender, to);
        if (arrival != null)
        {
            point(sender, from, MirrorPoint.of(arrival));
        }
    }

    /**
     * Where a player should land when stepping out of a mirror's banner.
     *
     * <p>Needs the banner's own world loaded, because the facing has to be read off the live
     * block -- there is nowhere else it is recorded. A mirror in an unloaded world can still
     * be the <em>source</em> of a link; it just cannot be the target of one until its world is
     * up.
     *
     * @param sender
     *            who to tell if it cannot be worked out
     * @param to
     *            the mirror being linked to
     * @return the arrival location, or null with the reason already sent
     */
    private static Location arrivalAt(final CommandSender sender, final QuantumMirror to)
    {
        final MirrorBlock banner = to.banner();
        final World world = Bukkit.getWorld(banner.worldName());
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
     * @param sender
     *            who to tell
     * @param mirror
     *            the mirror being pointed
     * @param destination
     *            where it should open onto
     */
    private static void point(final CommandSender sender, final QuantumMirror mirror,
        final MirrorPoint destination)
    {
        final QuantumMirror pointed = mirror.withDestination(destination);
        if (pointed.isSameWorld() && !ConfigManager.isMirrorAllowSameWorld())
        {
            say(sender, "A quantum mirror connects two worlds, and both ends of '"
                + mirror.name() + "' are in " + destination.worldName() + ".");
            say(sender, "Use a gate, a ring, or a beam place for travel inside one world --");
            say(sender, "or set mirror-allow-same-world to true if you want this anyway.");
            return;
        }
        MirrorManager.add(pointed);
        MirrorYamlManager.saveAll();
        say(sender, "'" + mirror.name() + "' now opens onto " + describe(destination) + ".");
    }

    private static void remove(final CommandSender sender, final String[] args)
    {
        if (!named(sender, args, "remove <name>"))
        {
            return;
        }
        final QuantumMirror removed = MirrorManager.remove(args[2]);
        if (removed == null)
        {
            say(sender, "There is no mirror called '" + args[2] + "'.");
            return;
        }
        MirrorYamlManager.saveAll();
        say(sender, "'" + removed.name() + "' is an ordinary banner again.");
    }

    private static void list(final CommandSender sender)
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
            return;
        }
        say(sender, lines.size() + " mirror(s):");
        lines.forEach(sender::sendMessage);
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
     *
     * @param player
     *            whoever is looking
     * @return the banner block, or null
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
