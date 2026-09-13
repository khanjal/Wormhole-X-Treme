package com.wormhole_xtreme.wormhole.command.handlers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
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
 * mirror stamp [name] [look]  make the banner look like where it goes
 * mirror display [name] &lt;how&gt; show its look always, or only up close
 * mirror mode [name] &lt;how&gt;    keep the look, or re-read the far side
 * mirror remove [name]        forget it; the banner becomes an ordinary banner again
 * mirror list                 what exists and where each one goes
 * </pre>
 *
 * <p>The four verbs in brackets take the mirror on the banner you are looking at when you do
 * not name one. A name nobody chose is the reason: {@code link} derives {@code &lt;other&gt;-return}
 * for the second banner of a pair, so the commonest thing to want to restamp or take down is
 * the thing least likely to be remembered by name -- while standing right in front of it.
 *
 * <p>{@code set}, {@code target} and {@code link} keep their required names. {@code set} is
 * naming something that has no name yet; {@code target} is run from the arrival spot, which is
 * the one place the banner is not; and {@code link}'s argument is the far mirror, not this one.
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
     * How long the bar-separated look list may get before a message counts it instead.
     *
     * <p>Measured on the list alone; the usage line around it is a further 41 characters and
     * the refusal's opening about 40. Default chat fits roughly 53, so 80 here means about two
     * chat lines at worst, which is what the ten shipped looks came to before there were
     * seventeen. The list is worth its space while somebody can take it in -- four wrapped
     * lines of names is not a list any more, and the count that replaces it still says there is
     * a real list to go and find.
     *
     * <p>One number for both messages on purpose. They ask the same question -- are there few
     * enough looks to name them all here -- and two budgets would be two things to keep in step
     * and a player seeing the names in one message and a count in the other for no reason they
     * could work out.
     */
    private static final int LOOK_LIST_BUDGET = 80;

    /**
     * How far away a banner can be and still count as the one being looked at.
     *
     * <p>Comfortably past a survival player's reach, so the limit is never what stops somebody
     * naming the banner in front of them. The refusal says "six blocks" in words; both change
     * together or neither does.
     */
    private static final int REACH = 6;

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

    /**
     * Names the banner the player is looking at.
     *
     * <p>One banner is one mirror -- the block index has room for nothing else -- so what this
     * command means is settled by which of two things already exists, the name or the binding:
     *
     * <pre>
     * neither                  a new mirror, going nowhere yet
     * the name                 that mirror moves to this banner
     * the binding              this mirror is called that now -- a rename
     * both, the same mirror    nothing to do; say so
     * both, different mirrors  refused; one of them would be abandoned silently
     * </pre>
     *
     * <p>The first four all carry the whole mirror forward rather than rebuilding it from a
     * name and a block. Rebuilding was the old behaviour and it lost three things quietly: the
     * destination, the look, and whether the mirror hides itself until somebody comes close. A
     * renamed mirror came out valid and blank, and the reply said "It goes nowhere yet", which
     * reads as a next step rather than as a warning that the last one has been undone.
     *
     * <p>Renaming leaves nothing behind under the old name. It used to: the new name was added
     * beside the old one, both claiming the banner, and clearing up the orphan afterwards
     * unhooked the survivor as well, because removing a mirror takes its banner out of the
     * block index without checking whether that banner is still somebody else's.
     */
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
        final MirrorBlock here = MirrorBlock.of(block);
        final QuantumMirror byThatName = MirrorManager.byName(name);
        final QuantumMirror onThisBanner = MirrorManager.at(here);
        if ((byThatName != null) && (onThisBanner != null)
            && !byThatName.name().equalsIgnoreCase(onThisBanner.name()))
        {
            say(sender, "This banner is already " + MirrorText.quoted(onThisBanner.name())
                + ", and " + MirrorText.quoted(name) + " is a mirror somewhere else.");
            say(sender, "Renaming this one would leave that one on no banner. Remove one of"
                + " them first.");
            return;
        }
        if ((byThatName != null) && (onThisBanner != null))
        {
            say(sender, MIRROR_IS + MirrorText.quoted(onThisBanner.name()) + " is already this"
                + " banner. Nothing to do.");
            return;
        }
        sayWhereToClick(sender, block);
        setFrom(sender, (byThatName != null) ? byThatName : onThisBanner, name, here);
    }

    /**
     * Registers the mirror {@code set} has decided on, and says what happened.
     *
     * @param existing
     *            the mirror being moved or renamed, or null to make a new one
     * @param name
     *            what it should be called
     * @param here
     *            the banner it should hang on
     */
    private static void setFrom(final CommandSender sender, final QuantumMirror existing,
        final String name, final MirrorBlock here)
    {
        final String previous = (existing == null) ? null : existing.name();
        final QuantumMirror mirror = (existing == null)
            ? new QuantumMirror(name, here, null)
            : existing.withName(name).withBanner(here);
        if ((previous != null) && !previous.equalsIgnoreCase(name))
        {
            MirrorManager.remove(previous);
        }
        MirrorManager.add(mirror);
        MirrorYamlManager.saveAll();

        if (mirror.destination() == null)
        {
            say(sender, MIRROR_IS + MirrorText.quoted(name)
                + " is this banner. It goes nowhere yet.");
            say(sender, "Hang a banner at the far end, look at it, and run");
            say(sender, MirrorText.command("/wormhole mirror link", name)
                + " -- or stand where arrivals should");
            say(sender, "land and run " + MirrorText.command("/wormhole mirror target", name));
            return;
        }
        final String opening = ((previous != null) && !previous.equalsIgnoreCase(name))
            ? MIRROR_IS + MirrorText.quoted(previous) + " is " + MirrorText.quoted(name) + " now"
            : MIRROR_IS + MirrorText.quoted(name) + " is this banner now";
        say(sender, opening + ", still pointing at " + describe(mirror.destination()) + ".");
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
        sayWhereToClick(sender, block);
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
        // What one argument means: the mirror called that, if there is one, and otherwise the
        // look called that, applied to the banner in front of you. A mirror wins because that
        // is what the word meant before the name became optional -- a server where a mirror
        // and a look share a name should not find the command changing under it.
        //
        // Only when it is the last word. "stamp cavern museum" is a name and a look, as it
        // always was, and says there is no mirror called cavern rather than quietly stamping
        // something else and dropping the second word.
        final boolean look = (args.length == 3) && (MirrorManager.byName(args[2]) == null)
            && (MirrorPresetRegistry.byName(args[2]) != null);
        final QuantumMirror mirror = namedOrLookedAt(sender,
            ((args.length > 2) && !look) ? args[2] : null, () -> stampUsage(sender));
        if (mirror == null)
        {
            return;
        }
        final Block banner = bannerBlockOf(sender, mirror);
        if (banner == null)
        {
            return;
        }
        final String chosen = look ? args[2] : ((args.length > 3) ? args[3] : null);
        if (chosen != null)
        {
            stampWith(sender, mirror, banner, chosen);
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
            sayNoSuchLook(sender, presetName);
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
        if (looksFitInAMessage(names))
        {
            say(sender, USAGE + MirrorText.command(
                "/wormhole mirror stamp [<name>] [" + String.join("|", names) + "]"));
            return;
        }
        say(sender, USAGE + MirrorText.command("/wormhole mirror stamp [<name>] [<look>]"));
        if (names.length > 0)
        {
            say(sender, names.length + " looks to choose from -- press tab for the list, or"
                + " leave it out to sample the far side.");
        }
    }

    /**
     * Says that no look answers to that name, offering the others or counting them.
     *
     * <p>The same rule as the usage line, and the same threshold, because it is the same
     * question asked twice -- are there few enough looks to name them all in one message. Two
     * budgets would be two numbers to keep in step and a player seeing the list in one message
     * and a count in the other for no reason they could work out.
     *
     * <p>Worth listing here for longer than in the usage line, if anything: somebody who has
     * just named a look that does not exist is asking what does. But seventeen names is a wall
     * either way, and the count plus tab is the answer that stays readable.
     */
    private static void sayNoSuchLook(final CommandSender sender, final String presetName)
    {
        final String[] names = MirrorPresetRegistry.names();
        if (names.length == 0)
        {
            say(sender, "There are no looks loaded at all -- check the server log for what"
                + " went wrong reading shapes/mirror.");
            return;
        }
        final String opening = "There is no look called " + MirrorText.quoted(presetName) + ".";
        say(sender, looksFitInAMessage(names)
            ? opening + " Try one of: " + MirrorText.names(names)
            : opening + " There are " + names.length + " to choose from -- press tab for the"
                + " list.");
    }

    /**
     * Whether the looks are few enough to name in one message.
     *
     * <p>Package-private and taking the names rather than reading the registry, so a test can
     * put it either side of the threshold. Going through the registry would only ever offer
     * whatever happens to ship, which is one answer and not the interesting one.
     *
     * <p>Measured bar-separated, which is how the usage line renders them; the refusal joins
     * them with ", " instead and so runs a character per name longer. That slack is inside the
     * budget rather than worth a second one -- at the threshold it is the difference between
     * two chat lines and two chat lines.
     *
     * @param names
     *            the loaded look names
     * @return true to list them, false to count them instead
     */
    static boolean looksFitInAMessage(final String[] names)
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
        if (!isBanner(block))
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
        // The setting word decides which word is which. "display proximity" is the banner in
        // front of you; "display museum proximity" names one. Only the two setting words read
        // that way, so "display museum" -- a name with the setting forgotten -- still gets the
        // form rather than a complaint that "museum" is not a way to show a mirror.
        final boolean unnamed = (args.length == 3) && (MirrorDisplay.of(args[2]) != null);
        if ((args.length < 4) && !unnamed)
        {
            sayDisplayUsage(sender);
            return;
        }
        final String word = unnamed ? args[2] : args[3];
        final QuantumMirror mirror = namedOrLookedAt(sender, unnamed ? null : args[2],
            () -> sayDisplayUsage(sender));
        if (mirror == null)
        {
            return;
        }
        final MirrorDisplay wanted = MirrorDisplay.of(word);
        if (wanted == null)
        {
            say(sender, "A mirror is shown " + MirrorText.quoted("always") + " or by "
                + MirrorText.quoted("proximity") + ", not " + MirrorText.quoted(word) + ".");
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
        // By the same rule display uses: a setting word alone means the banner being looked at.
        final boolean unnamed = (args.length == 3) && (MirrorMode.of(args[2]) != null);
        if ((args.length < 4) && !unnamed)
        {
            sayModeUsage(sender);
            return;
        }
        final String word = unnamed ? args[2] : args[3];
        final QuantumMirror mirror = namedOrLookedAt(sender, unnamed ? null : args[2],
            () -> sayModeUsage(sender));
        if (mirror == null)
        {
            return;
        }
        final MirrorMode wanted = MirrorMode.of(word);
        if (wanted == null)
        {
            say(sender, "A mirror is " + MirrorText.quoted("static") + " or "
                + MirrorText.quoted("dynamic") + ", not " + MirrorText.quoted(word) + ".");
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
        final QuantumMirror mirror = namedOrLookedAt(sender,
            (args.length > 2) ? args[2] : null, () -> sayUsage(sender, "remove [<name>]"));
        if (mirror == null)
        {
            return;
        }
        // No miss to report: namedOrLookedAt has already answered for a name nobody has, and
        // a mirror it found by banner is one the registry just handed over.
        MirrorManager.remove(mirror.name());
        // The same reason display() releases before it changes the setting: anybody who was
        // being shown the blank is still holding it, and nothing will visit this mirror again
        // to take it back. Without this the line below would be untrue for exactly the players
        // standing furthest away -- an ordinary banner they cannot see.
        //
        // forget rather than release, because this one really is gone: its re-sample clock has
        // nothing left to throttle, and left behind it would be inherited by whatever is named
        // after it next.
        MirrorProximity.forget(mirror);
        MirrorYamlManager.saveAll();
        say(sender, MirrorText.quoted(mirror.name()) + " is an ordinary banner again.");
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
        final Block exact = player.getTargetBlockExact(REACH);
        final Block banner = bannerInSight(exact, player.getLineOfSight(null, REACH));
        if (banner != null)
        {
            return banner;
        }
        if (exact == null)
        {
            say(player, "Look at the banner you want to use, within six blocks.");
            return null;
        }
        say(player, "That is a "
            + MirrorText.name(exact.getType().name().toLowerCase(Locale.ROOT))
            + ", not a banner. A mirror has to be a banner -- wall-mounted or"
            + " freestanding, either is fine.");
        return null;
    }

    /**
     * The banner being looked at: the block aimed at, or the first one the line of sight
     * crosses.
     *
     * <p>Package-private and taking blocks rather than the player, so both routes can be
     * tested without a live world -- which is the only way to get at a decision made of two
     * Bukkit ray casts.
     *
     * <p>{@code getTargetBlockExact} alone is not enough, and a freestanding banner is where
     * that shows. It ray-traces against block shapes, and a banner is a thin post: standing
     * next to one and looking at it, the ray can pass by the shape entirely. On a wall banner
     * the miss is invisible, because the wall behind it is hit instead and the command says
     * "that is a stone". On a banner on a post in the open there is nothing behind it at all,
     * so the ray hits nothing, and what the player gets is "look at a banner within six
     * blocks" while they are standing right next to one. Reported exactly that way.
     *
     * <p>{@code getLineOfSight} steps through the blocks the ray passes through rather than
     * their shapes, so the banner's block is in that list either way. The aimed-at block is
     * still preferred when it is itself a banner: with two banners in a row, the one you are
     * pointing at is the one you mean.
     *
     * <p>And then the block <em>under</em> the ray, for standing banners only. A standing
     * banner occupies one block but is drawn about two tall -- the cloth, which is the part of
     * it anybody actually looks at, hangs in the block above, where there is nothing to hit.
     * Aim at the cloth and the ray goes straight through and out the other side; aim at the
     * base and it works. That is how it was reported, in those words, after the line-of-sight
     * fallback had already landed and fixed a different miss.
     *
     * <p>Standing banners only, because a wall banner is drawn inside its own block and a
     * "look one block down" rule would let somebody name a wall banner by aiming at the wall
     * above it.
     *
     * @param exact
     *            the block the player is aimed at, or null if the ray hit nothing
     * @param lineOfSight
     *            the blocks the line of sight crosses, nearest first
     * @return the banner to use, or null if there is none
     */
    static Block bannerInSight(final Block exact, final List<Block> lineOfSight)
    {
        if (isBanner(exact))
        {
            return exact;
        }
        if (lineOfSight == null)
        {
            return null;
        }
        for (final Block block : lineOfSight)
        {
            if (isBanner(block))
            {
                return block;
            }
        }
        // Second pass, and only after every block on the ray has been asked: a banner the ray
        // actually crossed beats one merely standing under it.
        for (final Block block : lineOfSight)
        {
            final Block below = (block == null) ? null : block.getRelative(BlockFace.DOWN);
            if (isStandingBanner(below))
            {
                return below;
            }
        }
        return null;
    }

    /**
     * Says where a banner on a post has to be clicked, at the moment one becomes a mirror.
     *
     * <p>A standing banner is drawn about two blocks tall and only its base can be clicked --
     * the cloth above has nothing to hit, so a right-click aimed at it passes straight through
     * to whatever is behind. The plugin never sees that click at all: there is no event to
     * answer and nothing to say at the time, which makes it exactly the sort of silence that
     * gets read as a broken mirror.
     *
     * <p>So it is said here instead, once, to somebody standing in front of the banner they
     * just named. Only for the standing family; a wall banner is drawn inside its own block and
     * can be clicked anywhere on it.
     *
     * <p>Not a refusal. A banner on a post in the middle of a room is most of what a museum
     * corridor is made of, and the mechanic is worth keeping for it -- what was missing was
     * anybody being told how it behaves.
     */
    private static void sayWhereToClick(final CommandSender sender, final Block block)
    {
        if (!isStandingBanner(block))
        {
            return;
        }
        say(sender, "That one stands on a post, so click near its base to travel -- the cloth"
            + " above it cannot be clicked. A banner on a wall works anywhere on it.");
    }

    /**
     * Whether a banner stands on the ground rather than hanging on a wall.
     *
     * <p>The two families are told apart by name, the way the rest of this feature does it:
     * sixteen {@code *_WALL_BANNER} and sixteen {@code *_BANNER}, one per dye colour.
     */
    private static boolean isStandingBanner(final Block block)
    {
        return isBanner(block) && !block.getType().name().endsWith("WALL_BANNER");
    }

    /** Whether a block is a banner of either family, which is what a mirror has to be. */
    private static boolean isBanner(final Block block)
    {
        return (block != null) && block.getType().name().endsWith("BANNER");
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
            sayUsage(sender, form);
            return false;
        }
        return true;
    }

    /**
     * Says the form of a verb.
     *
     * @param form
     *            the verb and its arguments, without the command in front of it
     */
    private static void sayUsage(final CommandSender sender, final String form)
    {
        say(sender, USAGE + MirrorText.command("/wormhole mirror " + form));
    }

    /** @see #display */
    private static void sayDisplayUsage(final CommandSender sender)
    {
        sayUsage(sender, "display [<name>] <always|proximity>");
    }

    /** @see #mode */
    private static void sayModeUsage(final CommandSender sender)
    {
        sayUsage(sender, "mode [<name>] <static|dynamic>");
    }

    /**
     * The mirror a verb is about: the one named, or the one on the banner being looked at.
     *
     * <p>Looking at it is how somebody addresses a mirror they never named. {@code link}
     * derives the second banner's name -- {@code nether-return} for the far side of
     * {@code nether} -- so the half of a pair most likely to want restamping or taking down is
     * the half whose name nobody chose, and the one place that name is certainly not needed is
     * standing in front of the banner.
     *
     * <p>A sender that is not a player gets the form rather than "that has to be run in game".
     * There is no banner in front of a console, so what it is missing is the name, and the form
     * is what says so. A player who is not looking at one gets both: why the banner could not
     * be found, and the name they could have given instead.
     *
     * <p>Only for the verbs that address an existing mirror. {@code set} is naming something
     * that has no name yet, {@code target} is run from the arrival spot -- the one place the
     * banner is not -- and {@code link}'s argument is the far mirror rather than this one.
     *
     * @param sender
     *            who ran it, and who gets told what went wrong
     * @param name
     *            the name given, or null to use the banner being looked at
     * @param usage
     *            says the form, for a sender with nowhere to look
     * @return the mirror, or null with the reason already sent
     */
    private static QuantumMirror namedOrLookedAt(final CommandSender sender, final String name,
        final Runnable usage)
    {
        if (name != null)
        {
            return known(sender, name);
        }
        if (!(sender instanceof Player player))
        {
            usage.run();
            return null;
        }
        final Block block = lookedAtBanner(player);
        if (block == null)
        {
            usage.run();
            return null;
        }
        final QuantumMirror mirror = MirrorManager.at(MirrorBlock.of(block));
        if (mirror == null)
        {
            say(sender, "That banner is not a mirror. Name it with "
                + MirrorText.command("/wormhole mirror set <name>") + " first, or");
            say(sender, "name the mirror you meant.");
        }
        return mirror;
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
