package com.wormhole_xtreme.wormhole.command.handlers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
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
import com.wormhole_xtreme.wormhole.model.mirror.MirrorDisplay;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorLook;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorManager;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorMode;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorPoint;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorPreset;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorPresetRegistry;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorProximity;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorStamp;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorText;
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
 * mirror link &lt;other&gt;         join the banner you are looking at to that mirror
 * mirror stamp &lt;name&gt; [look]  make the banner look like where it goes
 * mirror remove &lt;name&gt;        forget it; the banner becomes an ordinary banner again
 * mirror list                 what exists and where each one goes
 * </pre>
 *
 * <p>{@code link} is sugar over {@code target}, applied twice: it works out where each banner
 * stands and stores two ordinary points, so nothing downstream knows a second mirror was
 * involved. Two ways rather than one, because a pair of banners is what somebody hanging two
 * of them means -- pointing only the first was the commonest way to end up with a banner that
 * did nothing when clicked.
 *
 * <p>It is a snapshot rather than a subscription -- move either banner afterwards and the
 * other still opens onto where it used to be. One-way binding is still {@code target}, which
 * is also the only way to open onto a world you would rather not put a banner in.
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
    /** How every "this banner is now a mirror" line opens. */
    private static final String MIRROR_IS = "Mirror ";

    /** How every usage line opens, outside the colouring. */
    private static final String USAGE = "Usage: ";

    /**
     * How long the bar-separated look list may get before the usage line stops printing it.
     *
     * <p>Measured on the list alone; the rest of the line is a further 39 characters. Default
     * chat fits roughly 53, so 80 here means a usage line of about two chat lines at worst,
     * which is what the ten shipped looks came to before there were seventeen. The list is
     * worth its space while somebody can take it in -- four wrapped lines of bar-separated
     * names is not a list any more, and the player reading it has already been told the one
     * thing they needed, which is that the argument exists and is optional.
     */
    private static final int LOOK_LIST_BUDGET = 80;

    /** The two verbs that point a mirror, named in the verb list, the switch and the prose. */
    private static final String TARGET = "target";

    /** @see #TARGET */
    private static final String LINK = "link";

    /** What this command answers to, for the usage line and tab completion. */
    private static final String[] VERBS =
        { "set", TARGET, LINK, "stamp", "display", "mode", "remove", "list" };

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
            case TARGET -> target(sender, args);
            case LINK -> link(sender, args);
            case "stamp" -> stamp(sender, args);
            case "display" -> display(sender, args);
            case "mode" -> mode(sender, args);
            case "remove" -> remove(sender, args);
            case "list" -> list(sender);
            default -> usage(sender);
        }
        return true;
    }

    private static void usage(final CommandSender sender)
    {
        say(sender, USAGE + MirrorText.command(
            "/wormhole mirror <" + String.join("|", VERBS) + ">"));
        say(sender, "A mirror is a banner you click to travel. Name one with "
            + MirrorText.name("set") + " while");
        say(sender, "looking at it, then point it with " + MirrorText.name(TARGET) + " or "
            + MirrorText.name(LINK) + ".");
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
            say(sender, MIRROR_IS + MirrorText.quoted(name)
                + " is this banner. It goes nowhere yet.");
            say(sender, "Hang a banner at the far end, look at it, and run");
            say(sender, MirrorText.command("/wormhole mirror link", name)
                + " -- or stand where arrivals should");
            say(sender, "land and run " + MirrorText.command("/wormhole mirror target", name));
        }
        else
        {
            say(sender, MIRROR_IS + MirrorText.quoted(name)
                + " is this banner now, still pointing at " + describe(keep) + ".");
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
            pointAndSay(sender, mirror, MirrorPoint.of(player.getLocation()));
        }
    }

    /**
     * Ties the banner you are looking at to a mirror that already exists.
     *
     * <pre>
     * mirror set nether            at the first banner, wherever you want to come back to
     * mirror link nether           at the second, in the other world -- that is the whole job
     * </pre>
     *
     * <p>One argument, because by the time you are hanging the second banner the only thing
     * you still have to say is which one it joins. The banner you are looking at becomes the
     * other half, and both are pointed at each other.
     *
     * <p>A second name is accepted for the case where both banners already exist, or where you
     * want to choose what this side is called rather than take the derived name. Naming it is
     * the only way to get something other than {@code <other>-return}.
     *
     * <p>Both ways, always. Pointing only one was the commonest way to end up with a banner
     * that did nothing when clicked, and the argument order was invisible once you had walked
     * away from it. One-way binding is still {@code target}, which is also the only way to open
     * onto a world you would rather not put a banner in.
     */
    private static void link(final CommandSender sender, final String[] args)
    {
        if (args.length < 3)
        {
            say(sender, USAGE
                + MirrorText.command("/wormhole mirror link <other> [name for this one]"));
            say(sender, "Run it looking at the banner you want to join to <other>.");
            return;
        }
        final QuantumMirror other = known(sender, args[2]);
        if (other == null)
        {
            return;
        }
        final String thisName = (args.length > 3) ? args[3] : freeNameFrom(args[2] + "-return");
        if (thisName.equalsIgnoreCase(other.name()))
        {
            say(sender, "A mirror cannot open onto itself.");
            return;
        }
        final QuantumMirror here = existingOrLookedAt(sender, thisName);
        if (here == null)
        {
            return;
        }
        // Both arrivals are worked out before either is stored, so a pair that cannot be tied
        // both ways is not left tied one way -- the state this command exists to stop people
        // ending up in.
        final Location toOther = arrivalAt(sender, other);
        final Location toHere = arrivalAt(sender, here);
        if ((toOther == null) || (toHere == null))
        {
            return;
        }
        if (point(sender, here, MirrorPoint.of(toOther))
            && point(sender, other, MirrorPoint.of(toHere)))
        {
            say(sender, MirrorText.quoted(here.name()) + " and "
                + MirrorText.quoted(other.name()) + " now open onto each other.");
        }
    }

    /**
     * A name nobody is using, starting from the one derived for this side.
     *
     * <p>Only for the derived name. A name given on purpose may well be an existing mirror --
     * that is how two banners already bound get tied together -- but a derived one silently
     * landing on somebody else's mirror would repoint a banner the operator never mentioned,
     * in a command they ran while looking at a different one entirely.
     *
     * @param wanted
     *            the name derived from the other side
     * @return that name, or the first numbered variant of it that is free
     */
    private static String freeNameFrom(final String wanted)
    {
        String candidate = wanted;
        // Two is where a human starts counting a second one of something.
        int suffix = 2;
        while (MirrorManager.byName(candidate) != null)
        {
            candidate = wanted + "-" + suffix;
            suffix++;
        }
        return candidate;
    }

    /**
     * A mirror by that name, or the banner the player is looking at bound under it.
     *
     * <p>What makes {@code link} usable from the far end of a journey. A name nobody has yet is
     * not a mistake here -- it is the second banner, and the player is standing in front of it.
     *
     * @param sender
     *            who ran it, and who gets told what went wrong
     * @param name
     *            the name for this side, given or derived
     * @return the mirror, or null with the reason already sent
     */
    private static QuantumMirror existingOrLookedAt(final CommandSender sender, final String name)
    {
        final QuantumMirror existing = MirrorManager.byName(name);
        if (existing != null)
        {
            return existing;
        }
        final Player player = asPlayer(sender);
        if (player == null)
        {
            return null;
        }
        final Block block = lookedAtBanner(player);
        if (block == null)
        {
            return null;
        }
        final QuantumMirror bound = new QuantumMirror(name, MirrorBlock.of(block), null);
        MirrorManager.add(bound);
        MirrorYamlManager.saveAll();
        say(sender, MIRROR_IS + MirrorText.quoted(name) + " is this banner.");
        return bound;
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
            say(sender, MirrorText.quoted(to.name()) + " is in "
                + MirrorText.name(banner.worldName())
                + ", which is not loaded, so where its banner faces cannot be read.");
            return null;
        }
        final Location arrival =
            MirrorArrival.atTheBanner(world.getBlockAt(banner.x(), banner.y(), banner.z()));
        if (arrival == null)
        {
            say(sender, MirrorText.quoted(to.name()) + " is no longer a banner, so there is"
                + " nowhere to arrive. Put one back, or re-run "
                + MirrorText.command("/wormhole mirror set") + " on a banner that is there.");
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
    private static boolean point(final CommandSender sender, final QuantumMirror mirror,
        final MirrorPoint destination)
    {
        final QuantumMirror pointed = mirror.withDestination(destination);
        if (pointed.isSameWorld() && !ConfigManager.isMirrorAllowSameWorld())
        {
            say(sender, "A quantum mirror connects two worlds, and both ends of "
                + MirrorText.quoted(mirror.name()) + " are in "
                + MirrorText.name(destination.worldName()) + ".");
            say(sender, "Use a gate, a ring, or a beam place for travel inside one world --");
            say(sender, "or set " + MirrorText.name("mirror-allow-same-world")
                + " to true if you want this anyway.");
            return false;
        }
        MirrorManager.add(pointed);
        MirrorYamlManager.saveAll();
        return true;
    }

    /**
     * Points one mirror and says so.
     *
     * <p>Separate from {@link #point} because {@code link} points two and wants one line about
     * the pair rather than two about the halves. The boolean {@code point} returns is not the
     * {@code true} this project took out of thirty-four helpers -- it says whether the
     * cross-world rule allowed it, which is the one thing a caller pointing two mirrors has to
     * know before it claims both worked.
     */
    private static void pointAndSay(final CommandSender sender, final QuantumMirror mirror,
        final MirrorPoint destination)
    {
        if (point(sender, mirror, destination))
        {
            say(sender, MirrorText.quoted(mirror.name()) + " now opens onto "
                + describe(destination) + ".");
        }
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
        if (args.length < 3)
        {
            stampUsage(sender);
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
            final String[] names = MirrorPresetRegistry.names();
            say(sender, (names.length == 0)
                ? "There are no looks loaded at all -- check the server log for what went"
                    + " wrong reading shapes/mirror."
                : "There is no look called " + MirrorText.quoted(presetName) + ". Try one of: "
                    + MirrorText.names(names));
            return;
        }
        if (MirrorStamp.apply(banner, preset))
        {
            remember(mirror, MirrorLook.named(preset.name()));
            say(sender, MirrorText.quoted(mirror.name()) + " looks like "
                + MirrorText.name(preset.name()) + " now.");
        }
        else
        {
            say(sender, "That banner could not be stamped.");
        }
    }

    /**
     * Writes the look down beside the banner that is already wearing it.
     *
     * <p>Both copies, on purpose. The banner keeps the patterns because they are vanilla data
     * and outlive this plugin -- disable it and the corridor an operator built is still there.
     * The mirror keeps them as data because a proximity mirror has to dress the banner again
     * after showing somebody the blank, and a dynamic one has to know what it last saw.
     *
     * <p>Re-read from the registry rather than trusting the copy this command started with, so
     * anything changed in between survives -- and checked for null, because one of the things
     * that can change in between is somebody else running {@code mirror remove}. The banner
     * keeps the look it was just given either way; there is simply no longer a mirror to write
     * it down against.
     */
    private static void remember(final QuantumMirror mirror, final MirrorLook look)
    {
        final QuantumMirror current = MirrorManager.byName(mirror.name());
        if (current == null)
        {
            return;
        }
        MirrorManager.add(current.withLook(look));
        MirrorYamlManager.saveAll();
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
            say(sender, MirrorText.quoted(mirror.name())
                + " does not go anywhere yet, so there is nothing");
            say(sender, "to look at. Point it first, or name a look: "
                + MirrorText.command("/wormhole mirror stamp",
                    mirror.name() + " " + firstPresetName()));
            return;
        }
        final MirrorView view = MirrorView.look(destination);
        if (view == null)
        {
            say(sender, MirrorText.name(destination.worldName())
                + " is not loaded, so the far side cannot be"
                + " read. Name a look instead, or try again once that world is up.");
            return;
        }
        // Asked of the look rather than worked out here. This used to be its own copy of the
        // decision -- enclosed means indoors -- which is right for a library and wrong for the
        // Nether, and the rule that knows the difference lived only in MirrorLook. So stamping
        // a mirror onto the Nether by hand dressed it as a room, while the very same mirror
        // corrected itself to the Nether's look the first time somebody walked up to a dynamic
        // one. Same banner, two appearances, depending on which code touched it last.
        final MirrorLook look = MirrorLook.seen(view);
        final MirrorPreset preset = look.preset();
        if (preset == null)
        {
            say(sender, "No mirror looks are loaded, so there is nothing to stamp with."
                + " Check the server log for what went wrong reading shapes/mirror.");
            return;
        }
        if (MirrorStamp.apply(banner, preset, view))
        {
            remember(mirror, look);
            say(sender, MirrorText.quoted(mirror.name()) + " now shows "
                + describe(view, preset) + ".");
        }
        else
        {
            say(sender, "That banner could not be stamped.");
        }
    }

    /**
     * What was found over there, as a person would say it.
     *
     * <p>The colour is read once into a local rather than asked for twice. Null-checking one
     * call and dereferencing another is only safe if the method is pure, which is true here and
     * is exactly the kind of thing that stops being true later.
     */
    private static String describe(final MirrorView view, final MirrorPreset preset)
    {
        final DyeColor dominant = view.dominant();
        final String where = whereItIs(view, preset);
        return (dominant == null)
            ? where
            : where + ", mostly " + MirrorText.dye(dominant);
    }

    /**
     * Indoors reads as indoors; otherwise the biome, or the preset's name if it has none.
     *
     * <p>"Somewhere indoors" stays in the body colour and the other two do not, because those
     * two are names -- a biome and a look -- and this one is a sentence saying there was no
     * name to give.
     *
     * <p>Asked of the preset rather than of {@code enclosed} alone, so the sentence agrees with
     * the banner. Reading it off the view meant a mirror onto the Nether was stamped with the
     * Nether's look and then described as "somewhere indoors" in the same breath.
     */
    private static String whereItIs(final MirrorView view, final MirrorPreset preset)
    {
        if (preset.readsAsARoom(view))
        {
            return "somewhere indoors";
        }
        return MirrorText.name(view.biome().isEmpty()
            ? preset.name()
            : view.biome().toLowerCase(Locale.ROOT));
    }

    /** @return a preset name to suggest, or a placeholder if none are loaded */
    private static String firstPresetName()
    {
        final String[] names = MirrorPresetRegistry.names();
        return (names.length == 0) ? "<look>" : names[0];
    }

    /**
     * The usage line for {@code stamp}, listing the looks while they fit and counting them
     * when they do not.
     *
     * <p>Listed, when it is short enough, because the looks are the interesting part of the
     * command and an operator who has added their own wants to see it offered. Counted
     * otherwise: the count says there is a real list to go and find, where a bare
     * {@code <look>} would suggest a free-form argument, and tab completion is the discovery
     * route anyway -- {@code SubCommands} offers every name at that position.
     *
     * <p>{@code <look>} with no count at all is the no-presets-loaded case. There is nothing to
     * count, and {@code stamp <name> []} would read as an empty required argument rather than
     * as an optional one nobody can currently fill.
     */
    private static void stampUsage(final CommandSender sender)
    {
        final String[] names = MirrorPresetRegistry.names();
        if (looksFitTheUsageLine(names))
        {
            say(sender, USAGE + MirrorText.command(
                "/wormhole mirror stamp <name> [" + String.join("|", names) + "]"));
            return;
        }
        say(sender, USAGE + MirrorText.command("/wormhole mirror stamp <name> [<look>]"));
        if (names.length > 0)
        {
            say(sender, names.length + " looks to choose from -- press tab for the list, or"
                + " leave it out to sample the far side.");
        }
    }

    /**
     * Whether the looks are few enough to name in the usage line.
     *
     * <p>Package-private and taking the names rather than reading the registry, so a test can
     * put it either side of the threshold. Going through the registry would only ever offer
     * whatever happens to ship, which is one answer and not the interesting one.
     *
     * @param names
     *            the loaded look names
     * @return true to list them, false to count them instead
     */
    static boolean looksFitTheUsageLine(final String[] names)
    {
        return (names.length > 0) && (String.join("|", names).length() <= LOOK_LIST_BUDGET);
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
            say(sender, MirrorText.quoted(mirror.name()) + " is in "
                + MirrorText.name(at.worldName())
                + ", which is not loaded, so its banner cannot be stamped.");
            return null;
        }
        final Block block = world.getBlockAt(at.x(), at.y(), at.z());
        if (!block.getType().name().endsWith("BANNER"))
        {
            say(sender, MirrorText.quoted(mirror.name()) + " is not a banner any more. Put one"
                + " back, or re-run " + MirrorText.command("/wormhole mirror set")
                + " on a banner that is there.");
            return null;
        }
        return block;
    }

    /**
     * Says when a mirror shows its look: always, or only to whoever comes close.
     *
     * <p>Nothing is written to the banner either way. The stamped banner stays stamped in the
     * world whatever this is set to -- banner patterns are vanilla data and outlive this
     * plugin, so turning a mirror down to proximity must not be a way to lose the look an
     * operator built. What changes is only who gets sent a blank instead.
     */
    private static void display(final CommandSender sender, final String[] args)
    {
        if (args.length < 4)
        {
            say(sender, USAGE
                + MirrorText.command("/wormhole mirror display <name> <always|proximity>"));
            return;
        }
        final QuantumMirror mirror = known(sender, args[2]);
        if (mirror == null)
        {
            return;
        }
        final MirrorDisplay wanted = MirrorDisplay.of(args[3]);
        if (wanted == null)
        {
            say(sender, "A mirror is shown " + MirrorText.quoted("always") + " or by "
                + MirrorText.quoted("proximity") + ", not " + MirrorText.quoted(args[3]) + ".");
            return;
        }
        // Only when proximity is being turned off, and before it is: the sweep will stop
        // visiting this mirror, and anybody holding the blank would keep it -- so turning
        // proximity off would hide the banner from exactly the people furthest away.
        //
        // Not on the way in, and not on a no-op. Releasing forgets who is currently near, so
        // the next sweep would read everybody as a fresh arrival -- revealing to people who
        // never moved, and asking a dynamic mirror to re-read a far side nobody walked up to.
        if ((mirror.display() == MirrorDisplay.PROXIMITY) && (wanted != MirrorDisplay.PROXIMITY))
        {
            MirrorProximity.release(mirror);
        }
        MirrorManager.add(mirror.withDisplay(wanted));
        MirrorYamlManager.saveAll();
        sayDisplay(sender, mirror.name(), wanted);
    }

    /** What changed, and the one thing about it worth warning an operator over. */
    private static void sayDisplay(final CommandSender sender, final String name,
        final MirrorDisplay wanted)
    {
        if (wanted == MirrorDisplay.ALWAYS)
        {
            say(sender, MirrorText.quoted(name)
                + " shows its look to everyone, from anywhere.");
            return;
        }
        say(sender, MirrorText.quoted(name) + " goes dark until somebody comes within "
            + ConfigManager.getMirrorProximityRadius() + " blocks.");
        if (!MirrorProximity.canHide())
        {
            say(sender, "This server has no Player.sendBlockUpdate, which arrived in 1.20.1,");
            say(sender, "so it will stay visible until you upgrade. Nothing is lost by setting");
            say(sender, "it now -- the banner keeps its look either way.");
        }
    }

    /** Says whether a mirror keeps the look it was given or re-reads the far side. */
    private static void mode(final CommandSender sender, final String[] args)
    {
        if (args.length < 4)
        {
            say(sender, USAGE
                + MirrorText.command("/wormhole mirror mode <name> <static|dynamic>"));
            return;
        }
        final QuantumMirror mirror = known(sender, args[2]);
        if (mirror == null)
        {
            return;
        }
        final MirrorMode wanted = MirrorMode.of(args[3]);
        if (wanted == null)
        {
            say(sender, "A mirror is " + MirrorText.quoted("static") + " or "
                + MirrorText.quoted("dynamic") + ", not " + MirrorText.quoted(args[3]) + ".");
            return;
        }
        MirrorManager.add(mirror.withMode(wanted));
        MirrorYamlManager.saveAll();
        if (wanted == MirrorMode.STATIC)
        {
            say(sender, MirrorText.quoted(mirror.name()) + " keeps the look it was given.");
            return;
        }
        say(sender, MirrorText.quoted(mirror.name())
            + " re-reads the far side when somebody walks up to");
        say(sender, "it, at most every " + ConfigManager.getMirrorDynamicResampleSeconds()
            + " seconds. Set it to " + MirrorText.name("proximity")
            + " as well if you want");
        say(sender, "it to go dark in between.");
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
            say(sender, "There is no mirror called " + MirrorText.quoted(args[2]) + ".");
            return;
        }
        // The same reason display() releases before it changes the setting: anybody who was
        // being shown the blank is still holding it, and nothing will visit this mirror again
        // to take it back. Without this the line below would be untrue for exactly the players
        // standing furthest away -- an ordinary banner they cannot see.
        //
        // forget rather than release, because this one really is gone: its re-sample clock has
        // nothing left to throttle, and left behind it would be inherited by whatever is named
        // after it next.
        MirrorProximity.forget(removed);
        MirrorYamlManager.saveAll();
        say(sender, MirrorText.quoted(removed.name()) + " is an ordinary banner again.");
    }

    /**
     * Every mirror and where it goes.
     *
     * <p>The rows are sent without the header the rest of this command uses, so they carry
     * their own body colour -- from the first character, indent included. An uncoloured line
     * arrives white, and a column of white would make the mirror names stop standing out at
     * exactly the moment there are several to pick between.
     */
    private static void list(final CommandSender sender)
    {
        final List<String> lines = new ArrayList<>();
        for (final QuantumMirror mirror : MirrorManager.all())
        {
            lines.add(MirrorText.BODY_COLOUR + "  " + MirrorText.name(mirror.name()) + " -- "
                + MirrorText.name(mirror.banner().worldName()) + " -> "
                + ((mirror.destination() == null) ? "nowhere yet" : describe(mirror.destination()))
                + settingsOf(mirror));
        }
        if (lines.isEmpty())
        {
            say(sender, "No mirrors yet. Look at a banner and run "
                + MirrorText.command("/wormhole mirror set <name>") + ".");
            return;
        }
        say(sender, lines.size() + " mirror(s):");
        lines.forEach(sender::sendMessage);
    }

    /**
     * The non-default settings, or nothing at all.
     *
     * <p>Silent for an ordinary mirror on purpose: a list where most entries end in
     * "(always, static)" is a list nobody reads to the end of, and those two words carry no
     * information when they are what everything says.
     *
     * @param mirror
     *            the mirror being listed
     * @return a trailing note, or an empty string
     */
    private static String settingsOf(final QuantumMirror mirror)
    {
        final List<String> notes = new ArrayList<>();
        if (mirror.display() != MirrorDisplay.ALWAYS)
        {
            notes.add(mirror.display().lower());
        }
        if (mirror.mode() != MirrorMode.STATIC)
        {
            notes.add(mirror.mode().lower());
        }
        return notes.isEmpty() ? "" : " (" + String.join(", ", notes) + ")";
    }

    /** A destination as a person would read it. */
    private static String describe(final MirrorPoint point)
    {
        return MirrorText.name(point.worldName()) + " at " + Math.round(point.x()) + ", "
            + Math.round(point.y()) + ", " + Math.round(point.z());
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
            say(player, "That is a "
                + MirrorText.name(block.getType().name().toLowerCase(Locale.ROOT))
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
            say(sender, "There is no mirror called " + MirrorText.quoted(name) + ".");
        }
        return mirror;
    }

    private static boolean named(final CommandSender sender, final String[] args, final String form)
    {
        if (args.length < 3)
        {
            say(sender, USAGE + MirrorText.command("/wormhole mirror " + form));
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
