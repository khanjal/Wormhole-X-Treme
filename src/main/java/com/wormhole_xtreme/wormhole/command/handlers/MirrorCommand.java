package com.wormhole_xtreme.wormhole.command.handlers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.command.CommandHandlerUtils;
import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorBlock;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorCaptures;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorDisplay;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorLook;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorManager;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorNetwork;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorPlacement;
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
 * {@code /wormhole mirror} -- making mirrors, and choosing how they start and look.
 *
 * <p>Nothing is pointed by hand. Every mirror is on the network: {@code create} stores its own
 * room, it shows that room until somebody right-clicks it, and a right-click walks the other
 * mirrors -- see {@link MirrorNetwork}.
 *
 * <pre>
 * mirror create &lt;name&gt;              look at a wall banner; it becomes a mirror by that name
 * mirror set [name] start &lt;m|none&gt;  the mirror a right-click opens onto first
 * mirror set [name] stamp [look]    give the banner a look
 * mirror set [name] display &lt;how&gt;   show its look always, or only up close
 * mirror set [name] capture         take the room's capture again
 * mirror remove [name]              forget it; the banner becomes an ordinary banner again
 * mirror list                       every mirror, and what each shows
 * </pre>
 *
 * <p>The verbs in brackets take the mirror on the banner you are looking at when you do not name
 * one, since the mirror somebody wants to change is usually the one they are standing in front of.
 * {@code create} keeps its required name: it is naming something that has no name yet.
 *
 * <p>{@code set} is the one door to what a mirror has. The four behind it were verbs of their
 * own and most of the usage line, and none of them is what somebody making a first mirror is
 * looking for; {@code create}, {@code remove} and {@code list} are.
 *
 * <p>{@code stamp} is a snapshot, and deliberately so. Named a look, it applies
 * that look and nothing else. Given no look, it goes and reads the far side -- the biome there
 * picks the frame, and the blocks around the arrival point become a few coarse squares in the
 * colours that dominate. A corridor of stamped mirrors then reads as a row of labelled doors
 * without anyone having chosen a label. It touches the banner and only the banner: the
 * capture a window draws from is taken again by {@code capture}, and by nothing else on
 * purpose. "It should be an understood command."
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

    /** The verb that makes a mirror. */
    private static final String CREATE = "create";

    /** The verb that changes one thing a mirror has. */
    private static final String SET = "set";

    /** What this command answers to, for the usage line and tab completion. */
    private static final String[] VERBS = { CREATE, SET, "remove", "list" };

    /** What {@code set} can change, and so the words {@code create} refuses as a name. */
    private static final String[] PROPERTIES = { "stamp", "display", "start", "capture" };

    /** The same four, for looking a word up without building a list each time. */
    private static final Set<String> PROPERTY_WORDS = Set.of(PROPERTIES);

    /** @return the verbs, for the usage line built in SubCommands */
    public static String[] verbs()
    {
        return VERBS.clone();
    }

    /** @return what {@code set} can change, for tab completion */
    public static String[] properties()
    {
        return PROPERTIES.clone();
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
            case CREATE -> create(sender, args);
            case SET -> set(sender, args);
            case "remove" -> remove(sender, args);
            case "list" -> list(sender);
            // Unlisted: what a window is drawing from and what it drew, for chasing a view that
            // shows the wrong thing. Not in the usage line, since it answers nothing a player
            // would ask.
            case "debug" -> debug(sender, args);
            default -> usage(sender);
        }
        return true;
    }

    private static void usage(final CommandSender sender)
    {
        say(sender, USAGE + MirrorText.command(
            "/wormhole mirror <" + String.join("|", VERBS) + ">"));
        say(sender, "A mirror is a wall banner. Make one with " + MirrorText.name(CREATE)
            + " while looking at it;");
        say(sender, "right-click it to choose another mirror, and punch it to go through.");
        say(sender, MirrorText.name(SET) + " changes one thing it has: "
            + String.join(", ", PROPERTIES) + ".");
    }

    /**
     * Changes one thing a mirror has: {@code set [name] <stamp|display|mode|start> ...}.
     *
     * <p>Which word is which is settled by the third: a property there means the banner being
     * looked at, anything else is a name and the property comes after it. That works because a
     * property word is never a mirror's name -- {@code create} refuses the four.
     *
     * <p>Each property keeps the parser it had when it was a verb of its own. The words after
     * {@code set} are put back in that shape and handed on, so what may stand where a name or a
     * look does has not changed with the move.
     */
    private static void set(final CommandSender sender, final String[] args)
    {
        final int at = ((args.length > 2) && (property(args[2]) != null)) ? 2 : 3;
        final String property = (args.length > at) ? property(args[at]) : null;
        if (property == null)
        {
            if (args.length > at)
            {
                say(sender, MirrorText.quoted(args[at]) + " is not something a mirror has.");
            }
            saySetUsage(sender);
            return;
        }
        final List<String> asVerb = new ArrayList<>();
        asVerb.add(args[0]);
        asVerb.add(property);
        if (at == 3)
        {
            asVerb.add(args[2]);
        }
        asVerb.addAll(Arrays.asList(args).subList(at + 1, args.length));
        final String[] shifted = asVerb.toArray(new String[0]);
        switch (property)
        {
            case "stamp" -> stamp(sender, shifted);
            case "display" -> display(sender, shifted);
            case "capture" -> capture(sender, shifted);
            default -> start(sender, shifted);
        }
    }

    /**
     * Whether a mirror may be called that, saying why not when it may not.
     *
     * <p>The four property words are how {@code set} tells a name from what comes after it, so
     * a mirror called {@code start} could never be addressed: {@code set start hub} would be the
     * banner in front of you.
     */
    private static boolean nameFree(final CommandSender sender, final String name)
    {
        if (property(name) == null)
        {
            return true;
        }
        say(sender, MirrorText.quoted(name) + " is a word " + MirrorText.name(SET)
            + " takes, so a mirror cannot be called that.");
        return false;
    }

    /** @return the property a word names, in lower case, or null for any other word */
    private static String property(final String word)
    {
        final String lower = word.toLowerCase(Locale.ROOT);
        return PROPERTY_WORDS.contains(lower) ? lower : null;
    }

    /** @see #set */
    private static void saySetUsage(final CommandSender sender)
    {
        sayUsage(sender, "set [<name>] <" + String.join("|", PROPERTIES) + "> ...");
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
    private static void create(final CommandSender sender, final String[] args)
    {
        final Player player = asPlayer(sender);
        if ((player == null) || !named(sender, args, "create <name>") || !nameFree(sender, args[2]))
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
        // A second wall banner beside this one, facing the same way, makes the pair one mirror two wide,
        // held by the left banner of the two, looking at the wall.
        final Block partner = (onThisBanner == null) ? partnerOf(block) : null;
        final Block left = ((partner != null) && isRightOf(partner, block)) ? block : partner;
        final Block base = (partner == null) ? block : left;
        final int width = (partner == null) ? 1 : 2;
        // Renaming the mirror a banner already is changes nothing about where it hangs.
        final String refused = (onThisBanner == null) ? MirrorPlacement.refusal(base, name, width) : null;
        if (refused != null)
        {
            say(sender, refused);
            return;
        }
        sayWhereToClick(sender, block);
        setFrom(sender, (byThatName != null) ? byThatName : onThisBanner, name,
            (onThisBanner != null) ? onThisBanner.banner() : MirrorBlock.of(base));
        if ((byThatName == null) && (onThisBanner == null))
        {
            dressPlainBanner(block, name);
            if (partner != null)
            {
                dressPlainBanner(partner, name);
            }
        }
        // Its own room, which it shows as a reflection and where anybody coming through lands.
        // The capture of it is taken by the next sweep.
        final QuantumMirror made = MirrorManager.byName(name);
        final MirrorPoint room = MirrorNetwork.roomOf(base, width);
        if ((onThisBanner == null) && (made != null) && (room != null))
        {
            MirrorManager.add(made.withDestination(room).withWidth(width));
            MirrorYamlManager.saveAll();
            // Made anyway: a block of wall is enough, and two is worth a word.
            final String thin = MirrorPlacement.thinWall(base, width);
            if (thin != null)
            {
                say(sender, thin);
            }
        }
    }

    /**
     * The wall banner beside this one that makes the two a mirror two wide, or null.
     *
     * <p>Along the wall to either side, facing the same way, and not already a mirror; the right
     * one first, looking at the wall, if there are two.
     */
    private static Block partnerOf(final Block block)
    {
        if (!(block.getBlockData() instanceof org.bukkit.block.data.Directional directional))
        {
            return null;
        }
        final BlockFace facing = directional.getFacing();
        for (final int side : new int[] { 1, -1 })
        {
            final Block beside = block.getWorld().getBlockAt(block.getX() + (side * facing.getModZ()),
                block.getY(), block.getZ() - (side * facing.getModX()));
            if ((beside != null) && (beside.getType() != null) && beside.getType().name().endsWith("WALL_BANNER")
                && (beside.getBlockData() instanceof org.bukkit.block.data.Directional other)
                && (other.getFacing() == facing) && (MirrorManager.at(MirrorBlock.of(beside)) == null))
            {
                return beside;
            }
        }
        return null;
    }

    /** Whether one wall banner is the one to the right of another, looking at the wall they hang on. */
    private static boolean isRightOf(final Block right, final Block of)
    {
        if (!(of.getBlockData() instanceof org.bukkit.block.data.Directional directional))
        {
            return false;
        }
        final BlockFace facing = directional.getFacing();
        return ((right.getX() - of.getX()) == facing.getModZ()) && ((right.getZ() - of.getZ()) == -facing.getModX());
    }

    /**
     * Gives a plain white banner that has just become a mirror the mirror look.
     *
     * <p>Only a plain one. A banner somebody patterned before hanging it keeps what they gave it,
     * and after that only {@code mirror set stamp} changes it.
     */
    private static void dressPlainBanner(final Block block, final String name)
    {
        final MirrorPreset look = MirrorPresetRegistry.byName("mirror");
        if ((look == null) || (block.getType() != Material.WHITE_WALL_BANNER)
            || !(block.getState() instanceof Banner banner) || !banner.getPatterns().isEmpty())
        {
            return;
        }
        final QuantumMirror mirror = MirrorManager.byName(name);
        if ((mirror != null) && MirrorStamp.apply(block, look))
        {
            remember(mirror, MirrorLook.named("mirror"));
        }
    }

    /**
     * Registers the mirror {@code create} has decided on, and says what happened.
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

        if ((previous != null) && !previous.equalsIgnoreCase(name))
        {
            say(sender, MIRROR_IS + MirrorText.quoted(previous) + " is " + MirrorText.quoted(name) + " now.");
            return;
        }
        say(sender, MIRROR_IS + MirrorText.quoted(name) + " is this banner. Walk up to it to see its reflection;");
        say(sender, "right-click it to choose another mirror, and punch it to go through.");
        warnIfTooNear(sender, mirror);
    }

    /**
     * Says so when a mirror just made is too close to another for either to be drawn whole.
     *
     * <p>Made anyway: it works, but each is trimmed to what a viewer sees through it, which costs
     * more as people walk past and leaves more to show at the edges.
     */
    private static void warnIfTooNear(final CommandSender sender, final QuantumMirror mirror)
    {
        final QuantumMirror near = MirrorPlacement.tooNear(mirror);
        if (near == null)
        {
            return;
        }
        final long apart = Math.round(Math.sqrt(MirrorPlacement.squaredApartOf(mirror, near)));
        say(sender, "It is " + apart + " blocks from " + MirrorText.quoted(near.name()) + ". Mirrors nearer than "
            + (long) MirrorPlacement.apartToDrawWhole() + " -- twice mirror-view-depth -- are not drawn whole:");
        say(sender, "each shows only what a viewer sees through it, which costs more as people walk past.");
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
        // Two readings of one word, never both at once: the look alone, or the look after the
        // name. Nested as one expression this was the least readable line in the command.
        final String afterTheName = (args.length > 3) ? args[3] : null;
        final String chosen = look ? args[2] : afterTheName;
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
            // The banner just written reaches every client over the view that hides it.
            com.wormhole_xtreme.wormhole.model.mirror.MirrorWindows.resendFor(mirror.name());
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
            com.wormhole_xtreme.wormhole.model.mirror.MirrorWindows.resendFor(mirror.name());
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
                "/wormhole mirror set [<name>] stamp [" + String.join("|", names) + "]"));
            return;
        }
        say(sender, USAGE + MirrorText.command("/wormhole mirror set [<name>] stamp [<look>]"));
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
            // create with a name that exists moves that mirror to the banner being looked at.
            say(sender, MirrorText.quoted(mirror.name()) + " is not a banner any more. Put one"
                + " back, or run " + MirrorText.command("/wormhole mirror create " + mirror.name())
                + " looking at a banner that is there.");
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
        final String word = args[unnamed ? 2 : 3];
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
            + ConfigManager.getMirrorProximityDistance() + " blocks.");
        if (!MirrorProximity.canHide())
        {
            say(sender, "This server has no Player.sendBlockUpdate, which arrived in 1.20.1,");
            say(sender, "so it will stay visible until you upgrade. Nothing is lost by setting");
            say(sender, "it now -- the banner keeps its look either way.");
        }
    }


    /**
     * Sets the mirror one opens onto when nobody at it has chosen.
     *
     * <p>For a mirror in an archived world, say, that should open onto the main world's mirror
     * first, with a right-click scrolling on from there. {@code none} is its own room again.
     */
    private static void start(final CommandSender sender, final String[] args)
    {
        if (args.length < 3)
        {
            sayStartUsage(sender);
            return;
        }
        // By the rule display and mode use: one word alone is the start, for the banner being looked at.
        final boolean unnamed = args.length == 3;
        final String word = args[unnamed ? 2 : 3];
        final QuantumMirror mirror = namedOrLookedAt(sender, unnamed ? null : args[2],
            () -> sayStartUsage(sender));
        if (mirror == null)
        {
            return;
        }
        if ("none".equalsIgnoreCase(word))
        {
            MirrorManager.add(mirror.withStart(null));
            MirrorYamlManager.saveAll();
            say(sender, "A right-click on " + MirrorText.quoted(mirror.name())
                + " goes through the other mirrors by name.");
            return;
        }
        final QuantumMirror first = known(sender, word);
        if (first == null)
        {
            return;
        }
        if (first.name().equalsIgnoreCase(mirror.name()))
        {
            say(sender, "A mirror starts on its own room already; " + MirrorText.name("none")
                + " is the way to say so.");
            return;
        }
        MirrorManager.add(mirror.withStart(first.name()));
        MirrorYamlManager.saveAll();
        say(sender, "A right-click on " + MirrorText.quoted(mirror.name()) + " opens onto "
            + MirrorText.quoted(first.name()) + " first.");
    }

    /** @see #start */
    private static void sayStartUsage(final CommandSender sender)
    {
        sayUsage(sender, "set [<name>] start <mirror|none>");
    }

    /**
     * Takes a mirror's capture again: the photograph of its room that every window draws from.
     *
     * <p>The one way a capture is retaken by hand. {@code stamp} used to do it as a side
     * effect, so a command about the banner changed what people saw through the opening; and
     * {@code mode dynamic} did it on approach, so the room and the banner changed under a
     * player who had asked for neither. Both are gone: what a mirror shows changes when
     * somebody says so.
     */
    private static void capture(final CommandSender sender, final String[] args)
    {
        final QuantumMirror mirror = namedOrLookedAt(sender, (args.length > 2) ? args[2] : null,
            () -> sayUsage(sender, "set [<name>] capture"));
        if (mirror == null)
        {
            return;
        }
        if (MirrorCaptures.retake(mirror))
        {
            say(sender, "Capturing " + MirrorText.quoted(mirror.name()) + "'s room again; the view"
                + " changes when the new one is ready.");
            return;
        }
        // request() answers true for a capture already being taken, so this is the one way it fails.
        say(sender, MirrorText.quoted(mirror.name()) + "'s room cannot be captured now: "
            + MirrorText.name(mirror.destination().worldName()) + " is not loaded.");
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
            final String key = MirrorCaptures.keyFor(mirror);
            lines.add(MirrorText.BODY_COLOUR + "  " + MirrorText.name(mirror.name()) + " -- "
                + MirrorText.name(mirror.banner().worldName()) + " -> " + showing(mirror)
                + settingsOf(mirror) + ((key == null) ? "" : (", capture " + key)));
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

    /** What a mirror shows right now, for the list. */
    private static String showing(final QuantumMirror mirror)
    {
        if (mirror.destination() == null)
        {
            return "nowhere yet";
        }
        if (MirrorNetwork.reflects(mirror))
        {
            return "its own reflection";
        }
        final QuantumMirror chosen = MirrorNetwork.chosen(mirror);
        return (chosen == mirror) ? describe(mirror.destination()) : MirrorText.name(chosen.name());
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
        if (mirror.start() != null)
        {
            notes.add("starts on " + mirror.start());
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
        sayUsage(sender, "set [<name>] display <always|proximity>");
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
     * <p>Only for the verbs that address an existing mirror. {@code create} is naming
     * something that has no name yet.
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
                + MirrorText.command("/wormhole mirror create <name>") + " first, or");
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

    /**
     * Says what a mirror's window draws from, and what it last drew for this sender.
     *
     * <p>{@code debug save} also photographs this world around the mirror into a file beside the
     * far side's capture, so the view can be reproduced away from the server. {@code debug off}
     * turns views off for the sender, so they see the world as it is, and {@code debug on} turns
     * them back on. {@code debug <name> full} draws that mirror whole and without limits for the
     * sender -- everything its capture holds, through the opening -- so what the file holds and
     * how it comes through can be seen; {@code debug on} stops that too.
     */
    private static void debug(final CommandSender sender, final String[] args)
    {
        final String last = (args.length > 2) ? args[args.length - 1].toLowerCase(java.util.Locale.ROOT) : "";
        if ("off".equals(last) || "on".equals(last))
        {
            final Player player = asPlayer(sender);
            if (player != null)
            {
                com.wormhole_xtreme.wormhole.model.mirror.MirrorWindows.blind(player, "off".equals(last));
                say(sender, "off".equals(last) ? "Views are off for you: mirrors are banners, and the world is as it is. "
                    + "mirror debug on turns them back on." : "Views are back on for you, as everyone sees them.");
            }
            return;
        }
        final boolean full = "full".equals(last);
        final boolean all = "all".equals(last);
        // How many words the command has with no name in it.
        final int bare = (full || all) ? 3 : 2;
        final String name = (args.length > bare) ? args[2] : null;
        final QuantumMirror mirror = namedOrLookedAt(sender, name,
            () -> sayUsage(sender, "debug [<name>] [all|full] | debug off|on"));
        if (mirror == null)
        {
            return;
        }
        if (full)
        {
            final Player player = asPlayer(sender);
            if (player != null)
            {
                com.wormhole_xtreme.wormhole.model.mirror.MirrorWindows.full(player, mirror.name());
                say(sender, MirrorText.quoted(mirror.name()) + " is drawn whole and without limits for you: "
                    + "everything its capture holds, through the opening, past the edges and into the ground. "
                    + "mirror debug on stops that.");
            }
            return;
        }
        if (!all)
        {
            sayBrief(sender, mirror);
            return;
        }
        say(sender, MirrorText.heading("mirror ") + MirrorText.quoted(mirror.name()));
        say(sender, MirrorText.field("banner", bannerOf(mirror)));
        say(sender, MirrorText.field("room", roomOf(mirror)));
        MirrorCaptures.describe(mirror).forEach(line -> say(sender, line));
        if (sender instanceof Player player)
        {
            com.wormhole_xtreme.wormhole.model.mirror.MirrorWindows.describe(player).forEach(line -> say(sender, line));
            final org.bukkit.Location eye = player.getEyeLocation();
            say(sender, MirrorText.field("your eye", String.format(Locale.ROOT, "%.2f,%.2f,%.2f, yaw %.1f, pitch %.1f",
                eye.getX(), eye.getY(), eye.getZ(), eye.getYaw(), eye.getPitch())));
        }
    }

    /**
     * {@code debug} without {@code all}: the mirror, its capture and your view, a line or so each.
     *
     * <p>All of it was some twenty lines, and chat shows ten, so it scrolled off before it was read.
     */
    private static void sayBrief(final CommandSender sender, final QuantumMirror mirror)
    {
        say(sender, MirrorText.heading("mirror ") + MirrorText.quoted(mirror.name()) + " at " + MirrorText.VALUE_COLOUR
            + bannerOf(mirror) + MirrorText.BODY_COLOUR + ", room " + MirrorText.VALUE_COLOUR + roomOf(mirror));
        say(sender, MirrorCaptures.summary(mirror));
        if (sender instanceof Player player)
        {
            com.wormhole_xtreme.wormhole.model.mirror.MirrorWindows.summary(player).forEach(line -> say(sender, line));
        }
        say(sender, "  " + MirrorText.command("/wormhole mirror debug " + mirror.name() + " all") + " for the rest.");
    }

    /** Where a mirror's banner is, for debug. */
    private static String bannerOf(final QuantumMirror mirror)
    {
        return mirror.banner().toKey() + ((mirror.width() >= 2) ? ", two wide" : "");
    }

    /** Where a mirror's room is, for debug, ending in the value colour. */
    private static String roomOf(final QuantumMirror mirror)
    {
        return (mirror.destination() == null) ? MirrorText.bad("none")
            : (MirrorText.NAME_COLOUR + mirror.destination().worldName() + MirrorText.VALUE_COLOUR + " "
                + (int) Math.floor(mirror.destination().x()) + ","
                + (int) Math.floor(mirror.destination().y()) + ","
                + (int) Math.floor(mirror.destination().z()));
    }

    private static void say(final CommandSender sender, final String message)
    {
        sender.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER + message);
    }
}
