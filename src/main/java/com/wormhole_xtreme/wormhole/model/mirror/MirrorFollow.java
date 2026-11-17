package com.wormhole_xtreme.wormhole.model.mirror;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;

import com.wormhole_xtreme.wormhole.model.window.BlockPlace;
import com.wormhole_xtreme.wormhole.model.window.Capture;
import com.wormhole_xtreme.wormhole.model.window.Captures;
import com.wormhole_xtreme.wormhole.model.window.Place;
import com.wormhole_xtreme.wormhole.model.window.WindowClicks;

/**
 * A banner that follows where its mirror opens first (#613), for a mirror set to.
 *
 * <p>Opt-in, and only on two things somebody did: {@code mirror set -start}, and the room it
 * follows being captured again. Not on a right-click, which is a passing choice, and never for
 * a mirror left at the default, whose look somebody chose by hand. An earlier mode that
 * re-stamped on approach was removed because "we shouldn't update the banner automatically".
 *
 * <p>The look is read from the room's stored capture, which is what the opening shows, rather
 * than from the live world, so following never loads a far chunk. With no capture yet, or one
 * still being read off the disk, the banner is left as it is; one being read stamps it when it
 * lands.
 */
public final class MirrorFollow
{
    /** What a re-stamp came to. */
    public enum Outcome
    {
        /** The banner wears the room's look now. */
        STAMPED,
        /** No capture of the room to read yet; the banner is unchanged. */
        WAITING,
        /** The banner could not be stamped: its world or block is gone, or no looks are loaded. */
        FAILED
    }

    /**
     * What a re-stamp came to, and the look it put on when it put one on.
     *
     * @param outcome
     *            what happened
     * @param view
     *            what was read from the room, or null
     * @param preset
     *            the frame it wears, or null
     */
    public record Result(Outcome outcome, MirrorView view, MirrorPreset preset)
    {
    }

    /** Mirrors whose room was being read off the disk when they asked, by lower-cased name. */
    private static final Set<String> WAITING = new HashSet<>();

    /** Where a room's capture comes from, so a test can hand one over. */
    static Function<String, Capture> captures = Captures::get;

    /** Where a room's biome comes from, so a test need not load a world. */
    static Function<Place, String> biomes = MirrorView::biomeOf;

    /** Static state only. */
    private MirrorFollow()
    {
    }

    /** Forgets every waiting mirror and puts the seams back, for a test or a reload. */
    public static void clear()
    {
        WAITING.clear();
        captures = Captures::get;
        biomes = MirrorView::biomeOf;
    }

    /**
     * The mirror whose room a banner follows: its start, or its own room without one.
     *
     * @return the start while it is a mirror with a room, otherwise the mirror itself
     */
    public static QuantumMirror followed(final QuantumMirror mirror)
    {
        final QuantumMirror start = (mirror.start() == null) ? null : MirrorManager.byName(mirror.start());
        return ((start == null) || (start.destination() == null)) ? mirror : start;
    }

    /**
     * Stamps a following mirror's banner from the room it follows.
     *
     * @param mirror
     *            a mirror set to follow
     * @return what came of it
     */
    public static Result restamp(final QuantumMirror mirror)
    {
        final QuantumMirror room = followed(mirror);
        final String key = Captures.keyFor(room);
        final Capture capture = (key == null) ? null : captures.apply(key);
        if (capture == null)
        {
            WAITING.add(lower(mirror.name()));
            return new Result(Outcome.WAITING, null, null);
        }
        WAITING.remove(lower(mirror.name()));
        final MirrorView view = MirrorView.fromCapture(capture, room.destination(), biomes.apply(room.destination()));
        final MirrorLook look = MirrorLook.seen(view);
        final MirrorPreset preset = look.preset();
        final Block banner = bannerOf(mirror);
        if ((preset == null) || (banner == null) || !MirrorStamp.apply(banner, preset, view))
        {
            return new Result(Outcome.FAILED, view, preset);
        }
        WindowClicks.resendFor(mirror.name());
        final QuantumMirror current = MirrorManager.byName(mirror.name());
        if (current != null)
        {
            MirrorManager.add(current.withLook(look));
            MirrorYamlManager.saveAll();
        }
        return new Result(Outcome.STAMPED, view, preset);
    }

    /**
     * A room's capture was taken or read: re-stamps the banners that follow it.
     *
     * @param key
     *            the capture's key
     * @param taken
     *            true for a capture just taken, which every follower takes up; false for one
     *            read off the disk, which only a follower still waiting for it does
     * @return how many banners were stamped
     */
    public static int captured(final String key, final boolean taken)
    {
        int stamped = 0;
        // A copy: stamping writes the look back into the registry being walked.
        for (final QuantumMirror mirror : List.copyOf(MirrorManager.all()))
        {
            if (mirror.follows() && (taken || WAITING.contains(lower(mirror.name())))
                && key.equals(Captures.keyFor(followed(mirror)))
                && (restamp(mirror).outcome() == Outcome.STAMPED))
            {
                stamped++;
            }
        }
        return stamped;
    }

    /** The live block of a mirror's banner, or null where its world is not loaded. */
    private static Block bannerOf(final QuantumMirror mirror)
    {
        final BlockPlace at = mirror.banner();
        final World world = Bukkit.getWorld(at.worldName());
        return (world == null) ? null : world.getBlockAt(at.x(), at.y(), at.z());
    }

    private static String lower(final String name)
    {
        return name.toLowerCase(Locale.ROOT);
    }
}
