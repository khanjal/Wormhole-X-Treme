package com.wormhole_xtreme.wormhole.model.mirror;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
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
 * <p>Nothing here loads a chunk. The look is read from the room's stored capture, which is what
 * the opening shows, rather than from the live world. With no capture yet, or one still being
 * read off the disk, the banner is left as it is; one being read stamps it when it lands. A banner
 * whose own chunk is not loaded has its look written down and put on when that chunk next loads.
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
        /** The banner's chunk is not loaded; it takes the look when it next is. */
        LATER,
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

    /** Mirrors whose look is chosen but whose banner's chunk was not loaded, by lower-cased name. */
    private static final Set<String> UNLOADED = new HashSet<>();

    /** Where a room's capture comes from, so a test can hand one over. */
    static Function<String, Capture> captures = Captures::get;

    /** Where a room's biome comes from, so a test need not load a world. */
    static Function<Place, String> biomes = MirrorView::biomeOf;

    /** Runs a step on the next tick, so a test can run it at once. */
    static Consumer<Runnable> nextTick = MirrorFollow::onNextTick;

    /** Static state only. */
    private MirrorFollow()
    {
    }

    /** Forgets every waiting mirror and puts the seams back, for a test or a reload. */
    public static void clear()
    {
        forgetAll();
        captures = Captures::get;
        biomes = MirrorView::biomeOf;
        nextTick = MirrorFollow::onNextTick;
    }

    /** Forgets every mirror waiting for a capture or a chunk, for the registry being emptied. */
    static void forgetAll()
    {
        WAITING.clear();
        UNLOADED.clear();
    }

    /**
     * Forgets anything a mirror was waiting for: it was removed, renamed or stopped following.
     *
     * @param name
     *            the mirror's name
     */
    public static void forget(final String name)
    {
        WAITING.remove(lower(name));
        UNLOADED.remove(lower(name));
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
        final Result result = restampUnsaved(mirror);
        if (changedTheLook(result))
        {
            MirrorYamlManager.saveAll();
        }
        return result;
    }

    /** {@link #restamp} without writing the file, for a batch that writes it once. */
    private static Result restampUnsaved(final QuantumMirror mirror)
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
        final World world = Bukkit.getWorld(mirror.banner().worldName());
        if ((preset == null) || (world == null))
        {
            return new Result(Outcome.FAILED, view, preset);
        }
        if (!loaded(world, mirror))
        {
            remember(mirror, look);
            UNLOADED.add(lower(mirror.name()));
            return new Result(Outcome.LATER, view, preset);
        }
        if (!stampEach(world, mirror, look))
        {
            return new Result(Outcome.FAILED, view, preset);
        }
        UNLOADED.remove(lower(mirror.name()));
        remember(mirror, look);
        return new Result(Outcome.STAMPED, view, preset);
    }

    /** Whether a re-stamp wrote a look down, so the file needs saving. */
    private static boolean changedTheLook(final Result result)
    {
        return (result.outcome() == Outcome.STAMPED) || (result.outcome() == Outcome.LATER);
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
        boolean save = false;
        // A copy: stamping writes the look back into the registry being walked.
        for (final QuantumMirror mirror : List.copyOf(MirrorManager.all()))
        {
            if (mirror.follows() && (taken || WAITING.contains(lower(mirror.name())))
                && key.equals(Captures.keyFor(followed(mirror))))
            {
                final Result result = restampUnsaved(mirror);
                save |= changedTheLook(result);
                stamped += (result.outcome() == Outcome.STAMPED) ? 1 : 0;
            }
        }
        if (save)
        {
            MirrorYamlManager.saveAll();
        }
        return stamped;
    }

    /**
     * A chunk loaded: see {@link #chunkLoaded(String, int, int)}. Nothing is asked of the chunk
     * while no banner is waiting, which is nearly always.
     *
     * @param chunk
     *            the chunk that loaded
     */
    public static void chunkLoaded(final Chunk chunk)
    {
        if (!UNLOADED.isEmpty())
        {
            chunkLoaded(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        }
    }

    /**
     * A chunk loaded: a following banner in it whose look was chosen while it was unloaded takes
     * that look on the next tick, rather than while the chunk is still being loaded.
     *
     * @param worldName
     *            the chunk's world
     * @param chunkX
     *            its x, in chunks
     * @param chunkZ
     *            its z, in chunks
     */
    public static void chunkLoaded(final String worldName, final int chunkX, final int chunkZ)
    {
        if (UNLOADED.isEmpty())
        {
            return;
        }
        for (final QuantumMirror mirror : List.copyOf(MirrorManager.all()))
        {
            if (UNLOADED.contains(lower(mirror.name())) && inChunk(mirror, worldName, chunkX, chunkZ))
            {
                final String name = mirror.name();
                nextTick.accept(() -> putOnRemembered(name));
            }
        }
    }

    /** Puts a waiting banner's written-down look on it, if it is still waiting and still loaded. */
    private static void putOnRemembered(final String name)
    {
        final QuantumMirror mirror = MirrorManager.byName(name);
        if ((mirror == null) || !mirror.follows() || !UNLOADED.contains(lower(name)))
        {
            forget(name);
            return;
        }
        final World world = Bukkit.getWorld(mirror.banner().worldName());
        if ((world != null) && loaded(world, mirror))
        {
            // Tried once while loaded, then dropped: a block that is no banner now never will be.
            stampEach(world, mirror, mirror.look());
            UNLOADED.remove(lower(name));
        }
    }

    /** Whether any of a mirror's banners is in a chunk. */
    private static boolean inChunk(final QuantumMirror mirror, final String worldName, final int chunkX,
        final int chunkZ)
    {
        for (final BlockPlace at : mirror.banners())
        {
            if (at.worldName().equals(worldName) && ((at.x() >> 4) == chunkX) && ((at.z() >> 4) == chunkZ))
            {
                return true;
            }
        }
        return false;
    }

    /** Whether every banner of a mirror is in a loaded chunk, so stamping it loads none. */
    private static boolean loaded(final World world, final QuantumMirror mirror)
    {
        for (final BlockPlace at : mirror.banners())
        {
            if (!world.isChunkLoaded(at.x() >> 4, at.z() >> 4))
            {
                return false;
            }
        }
        return true;
    }

    /** Stamps every banner of a mirror, both of a pair; true if each one took it. */
    private static boolean stampEach(final World world, final QuantumMirror mirror, final MirrorLook look)
    {
        boolean all = true;
        for (final BlockPlace at : mirror.banners())
        {
            all &= MirrorStamp.applyLook(world.getBlockAt(at.x(), at.y(), at.z()), look);
        }
        if (all)
        {
            WindowClicks.resendFor(mirror.name());
        }
        return all;
    }

    /** Writes a look down against the mirror as it is now, if it is still there. */
    private static void remember(final QuantumMirror mirror, final MirrorLook look)
    {
        final QuantumMirror current = MirrorManager.byName(mirror.name());
        if (current != null)
        {
            MirrorManager.add(current.withLook(look));
        }
    }

    /** Hands a step to the scheduler. */
    private static void onNextTick(final Runnable step)
    {
        WormholeXTreme.getScheduler().runTask(WormholeXTreme.getThisPlugin(), step);
    }

    /** A mirror's name as it is kept here; the registry allows for a null name, so this does too. */
    private static String lower(final String name)
    {
        return (name == null) ? "" : name.toLowerCase(Locale.ROOT);
    }
}
