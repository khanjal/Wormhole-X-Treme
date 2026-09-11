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
import com.wormhole_xtreme.wormhole.model.mirror.MirrorPreset;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorPresetRegistry;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorStamp;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorView;
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
 * mirror stamp &lt;name&gt; [look]  make the banner look like where it goes
 * mirror remove &lt;name&gt;        forget it; the banner becomes an ordinary banner again
 * mirror list                 what exists and where each one goes
 * </pre>
 *
 * <p>{@code link} is sugar over {@code target}: it works the arrival spot out once and stores
 * an ordinary point, so nothing downstream knows a second mirror was involved. It is a
 * snapshot rather than a subscription -- move the target banner afterwards and the first
 * mirror still points where it used to.
 *
 * <p>{@code stamp} is the same kind of snapshot, and deliberately so. Named a look, it applies
 * that look and nothing else. Given no look, it goes and reads the far side -- the biome there
 * picks the frame, and the blocks around the arrival point become a few coarse squares in the
 * colours that dominate. A corridor of stamped mirrors then reads as a row of labelled doors
 * without anyone having chosen a label.
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
    private static final String[] VERBS = { "set", "target", "link", "stamp", "remove", "list" };

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
            case "stamp" -> stamp(sender, args);
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

    /**
     * Makes a mirror's banner look like where it goes.
     *
     * <p>Named a preset, it applies that one. Named nothing, it looks through: the destination's
     * biome picks the preset and the blocks around the arrival point become the squares. Both
     * end at the same place -- patterns on a banner -- so the difference is only where the
     * colours came from.
     */
    private static void stamp(final CommandSender sender, final String[] args)
    {
        if (!named(sender, args, "stamp <name> [" + String.join("|",
            MirrorPresetRegistry.names()) + "]"))
        {
            return;
        }
        final QuantumMirror mirror = known(sender, args[2]);
        if (mirror == null)
        {
            return;
        }
        final Block banner = bannerBlockOf(sender, mirror);
        if (banner == null)
        {
            return;
        }
        if (args.length > 3)
        {
            stampWith(sender, mirror, banner, args[3]);
            return;
        }
        stampFromDestination(sender, mirror, banner);
    }

    /** Applies one named preset, and says so or says why not. */
    private static void stampWith(final CommandSender sender, final QuantumMirror mirror,
        final Block banner, final String presetName)
    {
        final MirrorPreset preset = MirrorPresetRegistry.byName(presetName);
        if (preset == null)
        {
            say(sender, "There is no look called '" + presetName + "'. Try one of: "
                + String.join(", ", MirrorPresetRegistry.names()));
            return;
        }
        if (MirrorStamp.apply(banner, preset))
        {
            say(sender, "'" + mirror.name() + "' looks like " + preset.name() + " now.");
        }
        else
        {
            say(sender, "That banner could not be stamped.");
        }
    }

    /**
     * Reads the far side and stamps what it found.
     *
     * <p>Refuses on an unpointed mirror rather than stamping a default. A banner that looks
     * like somewhere when it goes nowhere is worse than one that still looks like a banner:
     * the whole point of the look is that it tells you where the thing goes.
     */
    private static void stampFromDestination(final CommandSender sender,
        final QuantumMirror mirror, final Block banner)
    {
        final MirrorPoint destination = mirror.destination();
        if (destination == null)
        {
            say(sender, "'" + mirror.name() + "' does not go anywhere yet, so there is nothing");
            say(sender, "to look at. Point it first, or name a look: /wormhole mirror stamp "
                + mirror.name() + " " + firstPresetName());
            return;
        }
        final MirrorView view = MirrorView.look(destination);
        if (view == null)
        {
            say(sender, destination.worldName() + " is not loaded, so the far side cannot be"
                + " read. Name a look instead, or try again once that world is up.");
            return;
        }
        final MirrorPreset preset = view.enclosed()
            ? MirrorPresetRegistry.indoors()
            : MirrorPresetRegistry.forBiome(view.biome());
        if (preset == null)
        {
            say(sender, "No mirror looks are loaded, so there is nothing to stamp with."
                + " Check the server log for what went wrong reading shapes/mirror.");
            return;
        }
        if (MirrorStamp.apply(banner, preset, view))
        {
            say(sender, "'" + mirror.name() + "' now shows " + describe(view, preset) + ".");
        }
        else
        {
            say(sender, "That banner could not be stamped.");
        }
    }

    /** What was found over there, as a person would say it. */
    private static String describe(final MirrorView view, final MirrorPreset preset)
    {
        final String where = view.enclosed()
            ? "somewhere indoors"
            : (view.biome().isEmpty() ? preset.name() : view.biome().toLowerCase(Locale.ROOT));
        return (view.dominant() == null)
            ? where
            : where + ", mostly " + view.dominant().name().toLowerCase(Locale.ROOT);
    }

    /** @return a preset name to suggest, or a placeholder if none are loaded */
    private static String firstPresetName()
    {
        final String[] names = MirrorPresetRegistry.names();
        return (names.length == 0) ? "<look>" : names[0];
    }

    /**
     * The live block a mirror's banner is, or null with the reason already sent.
     *
     * <p>Unlike {@code set}, this does not use what the player is looking at. Stamping is
     * named, so it can be run from anywhere -- including on a mirror in another world, which
     * is the case that makes a corridor of them worth stamping in the first place.
     */
    private static Block bannerBlockOf(final CommandSender sender, final QuantumMirror mirror)
    {
        final MirrorBlock at = mirror.banner();
        final World world = Bukkit.getWorld(at.worldName());
        if (world == null)
        {
            say(sender, "'" + mirror.name() + "' is in " + at.worldName()
                + ", which is not loaded, so its banner cannot be stamped.");
            return null;
        }
        final Block block = world.getBlockAt(at.x(), at.y(), at.z());
        if (!block.getType().name().endsWith("BANNER"))
        {
            say(sender, "'" + mirror.name() + "' is not a banner any more. Put one back, or"
                + " re-run mirror set on a banner that is there.");
            return null;
        }
        return block;
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
