package com.wormhole_xtreme.wormhole.model.mirror;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;

/**
 * Letting a mirror go dark until somebody walks towards it.
 *
 * <h2>The blank is the illusion, not the banner</h2>
 *
 * <p>The banner in the world stays stamped, always. Banner patterns are vanilla data, so a
 * server that disables or removes this plugin keeps the corridor its operator built rather than
 * finding a row of plain cloth -- and that is worth more than any convenience the other way
 * round would have bought. What a proximity mirror does is send the <em>blank</em> to players
 * who are too far away, and take that illusion back when they come close.
 *
 * <p>The cost of that choice is one the sweep has to carry: a player who has never been near a
 * mirror would see the real, stamped block, so being far is not a state that arranges itself.
 * Everyone in the world is blanked once, and after that only crossings are sent.
 *
 * <h2>What this sweep is careful about</h2>
 *
 * <p>It runs forever, on a timer, so the order of the checks is the design. Before anything
 * touches a block it has already ruled out mirrors that are not proximity mirrors, mirrors with
 * no look to show, worlds that are not loaded and chunks that are not loaded. A server whose
 * mirrors are all ordinary does no work here beyond walking the list.
 *
 * <p>Packets go out on crossings only, never every sweep. A corridor of mirrors with somebody
 * standing still in it sends nothing at all.
 */
public final class MirrorProximity
{
    /** Who is seeing each mirror's real, stamped block, by mirror name. */
    private static final Map<String, Set<UUID>> SHOWING = new HashMap<>();

    /** Who has been sent each mirror's blank, by mirror name. */
    private static final Map<String, Set<UUID>> HIDING = new HashMap<>();

    /** When each dynamic mirror last re-read its far side, by mirror name. */
    private static final Map<String, Long> SAMPLED = new HashMap<>();

    /** Static state only. */
    private MirrorProximity()
    {
    }

    /** @return the sweep, for the scheduler */
    public static Runnable createTicker()
    {
        return MirrorProximity::tick;
    }

    /**
     * Whether this server can hide a mirror from somebody standing far away.
     *
     * <p>False on plain 1.20, where a proximity mirror simply stays visible. Worth asking
     * before telling an operator their mirror will go dark, because on that one version it
     * will not.
     *
     * @return true if per-player block updates are available
     */
    public static boolean canHide()
    {
        return MirrorPackets.available();
    }

    /** Forgets who is being shown what, for a test or a reload. */
    public static void clear()
    {
        SHOWING.clear();
        HIDING.clear();
        SAMPLED.clear();
    }

    /**
     * Takes back one mirror's illusion, and forgets it.
     *
     * <p>For the moment a mirror stops being a proximity mirror. Without this the sweep simply
     * skips it from then on, and whoever had been sent the blank keeps it -- indefinitely, or
     * until something happens to resend that chunk. Turning the setting off would be a way of
     * making a banner disappear for exactly the people who were furthest from it.
     *
     * @param mirror
     *            the mirror no longer being hidden
     */
    public static void release(final QuantumMirror mirror)
    {
        final Set<UUID> hidden = HIDING.remove(mirror.name());
        SHOWING.remove(mirror.name());
        if ((hidden == null) || hidden.isEmpty() || !MirrorPackets.available())
        {
            return;
        }
        final Block block = bannerOf(mirror);
        if (block != null)
        {
            hidden.forEach(id -> sendTrue(block, Bukkit.getPlayer(id)));
        }
    }

    /**
     * Releases a mirror and forgets everything else about it, for one being removed.
     *
     * <p>The difference from {@link #release} is the re-sample clock, and it matters because
     * {@code mirror display} releases a mirror that is still there. Clearing the clock on that
     * path would make a dynamic mirror eligible to re-read the far side the moment its display
     * setting was touched, however recently it had read -- which is not what "at most every so
     * many seconds" says. A mirror being removed has no clock worth keeping, and leaving one
     * behind would hand it to whatever is next given that name.
     *
     * @param mirror
     *            the mirror being removed
     */
    public static void forget(final QuantumMirror mirror)
    {
        release(mirror);
        SAMPLED.remove(mirror.name());
    }

    /**
     * Takes every illusion back, so the world is what everybody sees.
     *
     * <p>Called as the plugin stops. Without it, whoever was standing far from a mirror keeps
     * a blanked banner on their client until something makes the server resend that chunk --
     * which on a plugin that has just been disabled may be a long time, and looks exactly like
     * the plugin having eaten their banners.
     *
     * <p>A mirror whose chunk is not loaded is skipped rather than loaded, and that is
     * deliberate. Plugins are disabled before worlds are unloaded, so a chunk that is already
     * gone at this point is one nobody is near -- and a client that far away has dropped the
     * chunk too, so it will be sent a fresh and correct copy when the player returns. Loading
     * chunks during shutdown to repaint banners nobody can see would be a poor trade for a
     * case that heals itself.
     */
    public static void restoreAll()
    {
        if (!MirrorPackets.available())
        {
            return;
        }
        for (final QuantumMirror mirror : MirrorManager.all())
        {
            final Set<UUID> hidden = HIDING.get(mirror.name());
            if ((hidden == null) || hidden.isEmpty())
            {
                continue;
            }
            final Block block = bannerOf(mirror);
            if (block != null)
            {
                hidden.forEach(id -> sendTrue(block, Bukkit.getPlayer(id)));
            }
        }
        clear();
    }

    /**
     * One pass over every mirror the sweep has a reason to visit.
     *
     * <p>Where {@code sendBlockUpdate} is missing -- plain 1.20 -- nothing is ever hidden, and
     * a proximity mirror is simply always visible. That does not stop a dynamic one being kept
     * current there, because re-reading writes to the banner itself and needs no packet.
     */
    static void tick()
    {
        for (final QuantumMirror mirror : MirrorManager.all())
        {
            // Two separate reasons to visit a mirror, and they are not the same reason.
            // Hiding needs per-player block updates, so it needs a server that has them.
            // Re-reading the far side needs only somebody to walk up, and writes its result
            // to the banner everybody can see -- so it works on 1.20, and on a mirror that
            // never hides. The two settings are documented as independent; this is where that
            // is either true or a lie.
            final boolean hides = (mirror.display() == MirrorDisplay.PROXIMITY)
                && MirrorPackets.available();
            if (hides || (mirror.mode() == MirrorMode.DYNAMIC))
            {
                tickOne(mirror, hides);
            }
        }
    }

    /**
     * One mirror, cheapest checks first.
     *
     * @param mirror
     *            the mirror to visit
     * @param hides
     *            whether this one sends the blank to people who are far away, as opposed to
     *            being visited only to keep its look current
     */
    private static void tickOne(final QuantumMirror mirror, final boolean hides)
    {
        if (nothingToDo(mirror))
        {
            return;
        }
        final Block block = bannerOf(mirror);
        if (block == null)
        {
            return;
        }
        final List<Player> near = new ArrayList<>();
        final List<Player> far = new ArrayList<>();
        split(block, near, far);

        final Set<UUID> wasShowing = SHOWING.getOrDefault(mirror.name(), Set.of());
        final Set<UUID> wasHiding = HIDING.getOrDefault(mirror.name(), Set.of());
        // Once per sweep rather than once per arriving player. The throttle would swallow the
        // extra calls anyway, but asking once says plainly that a re-read belongs to the
        // mirror and not to whoever happened to walk up to it.
        final QuantumMirror current = anybodyNew(near, wasShowing) ? resampled(mirror) : mirror;

        if (hides)
        {
            sendCrossings(current, block, near, far, wasShowing, wasHiding);
        }
        // Replaced rather than merged, which is what drops a player who logged out or walked
        // into another world without this needing an event to hear about it. A mirror that
        // hides nothing tracks nobody as hidden, so there is nothing to hand back later.
        put(SHOWING, mirror.name(), ids(near));
        put(HIDING, mirror.name(), hides ? ids(far) : Set.of());
    }

    /**
     * Sorts everybody in the banner's world by whether they are close enough to see it.
     *
     * @param block
     *            the banner
     * @param near
     *            filled with those within reach
     * @param far
     *            filled with everybody else
     */
    private static void split(final Block block, final List<Player> near, final List<Player> far)
    {
        final Location centre = block.getLocation();
        final double reach = reachSquared();
        for (final Player player : block.getWorld().getPlayers())
        {
            // Same world by construction, so distanceSquared cannot throw here.
            if (player.getLocation().distanceSquared(centre) <= reach)
            {
                near.add(player);
            }
            else
            {
                far.add(player);
            }
        }
    }

    /** @return true if somebody in range was not in range last sweep */
    private static boolean anybodyNew(final List<Player> near, final Set<UUID> wasShowing)
    {
        for (final Player player : near)
        {
            if (!wasShowing.contains(player.getUniqueId()))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Sends to the people who crossed the line since the last sweep, and nobody else.
     *
     * <p>This is what keeps a corridor of mirrors quiet. Standing still in front of one costs
     * nothing at all; only walking in or out of range is a packet.
     */
    private static void sendCrossings(final QuantumMirror mirror, final Block block,
        final List<Player> near, final List<Player> far, final Set<UUID> wasShowing,
        final Set<UUID> wasHiding)
    {
        for (final Player player : near)
        {
            if (!wasShowing.contains(player.getUniqueId()))
            {
                reveal(mirror, block, player);
            }
        }
        for (final Player player : far)
        {
            if (!wasHiding.contains(player.getUniqueId()))
            {
                hide(block, player);
            }
        }
    }

    /** @return the ids of those players, for the sets kept between sweeps */
    private static Set<UUID> ids(final List<Player> players)
    {
        final Set<UUID> found = new HashSet<>();
        players.forEach(player -> found.add(player.getUniqueId()));
        return found;
    }

    /**
     * Whether this mirror has nothing for the sweep to do.
     *
     * <p>A mirror with no look has nothing to hide and nothing to reveal. A dynamic one is the
     * exception: it can go and get its first look, which is what {@code mode dynamic} promises
     * when it says the mirror re-reads the far side on approach. Without the exception that
     * promise would hold only for a mirror somebody had already stamped by hand.
     *
     * @param mirror
     *            the mirror being considered
     * @return true if the sweep should leave it alone
     */
    private static boolean nothingToDo(final QuantumMirror mirror)
    {
        final MirrorLook look = mirror.look();
        final boolean unstamped = (look == null) || look.isEmpty();
        final boolean couldLearnOne =
            (mirror.mode() == MirrorMode.DYNAMIC) && (mirror.destination() != null);
        return unstamped && !couldLearnOne;
    }

    /** The live banner block, or null if it cannot be reached or is no longer a banner. */
    private static Block bannerOf(final QuantumMirror mirror)
    {
        final MirrorBlock at = mirror.banner();
        final World world = Bukkit.getWorld(at.worldName());
        if (world == null)
        {
            return null;
        }
        // Before getBlockAt, which would load the chunk. A mirror in a corner of the map
        // nobody has walked to must not be the thing keeping that corner resident.
        if (!world.isChunkLoaded(at.x() >> 4, at.z() >> 4))
        {
            return null;
        }
        final Block block = world.getBlockAt(at.x(), at.y(), at.z());
        return block.getType().name().endsWith("BANNER") ? block : null;
    }

    /** @return the proximity radius, squared, so no square root is taken per player */
    private static double reachSquared()
    {
        final double radius = ConfigManager.getMirrorProximityRadius();
        return radius * radius;
    }

    /** Keeps the map free of empty sets rather than accumulating a key per mirror ever seen. */
    private static void put(final Map<String, Set<UUID>> into, final String name,
        final Set<UUID> who)
    {
        if (who.isEmpty())
        {
            into.remove(name);
        }
        else
        {
            into.put(name, who);
        }
    }

    /**
     * Re-reads the far side, if this is a dynamic mirror and the interval has passed.
     *
     * <p>On arrival only, and throttled, because sampling loads a distant chunk. A mirror
     * nobody walks up to is never sampled however dynamic it is, and a player pacing in front
     * of one gets the same answer until the interval is up.
     *
     * <p>The new look is kept in memory and deliberately not written to disk. Saving on every
     * approach would turn a busy corridor into a stream of file writes, and a dynamic mirror
     * re-reads on the next approach anyway -- so the worst a restart costs is one sample.
     *
     * @param mirror
     *            the mirror somebody has just walked up to
     * @return the mirror to show, which is the same one unless it has just been re-read
     */
    private static QuantumMirror resampled(final QuantumMirror mirror)
    {
        if ((mirror.mode() != MirrorMode.DYNAMIC) || (mirror.destination() == null)
            || !dueASample(mirror.name()))
        {
            return mirror;
        }
        final MirrorView seen = MirrorView.look(mirror.destination());
        if (seen == null)
        {
            // Stamped only on a real reading. Recording the attempt would mean a destination
            // world that happened to be down when somebody walked up stayed stale for another
            // whole interval after it came back -- and a look that returned nothing costs
            // nothing, because look() gives up the moment it finds no world.
            return mirror;
        }
        SAMPLED.put(mirror.name(), System.currentTimeMillis());
        final QuantumMirror updated = mirror.withLook(MirrorLook.seen(seen));
        MirrorManager.add(updated);
        // The world's banner is the copy that survives this plugin, so it is kept current too.
        MirrorStamp.applyLook(bannerOf(updated), updated.look());
        return updated;
    }

    /** Whether enough time has passed to read one mirror's far side again. */
    private static boolean dueASample(final String name)
    {
        final long interval = ConfigManager.getMirrorDynamicResampleSeconds() * 1000L;
        final Long last = SAMPLED.get(name);
        return (last == null) || ((System.currentTimeMillis() - last) >= interval);
    }

    /** Gives one player the block as it really is: stamped. */
    private static void reveal(final QuantumMirror mirror, final Block block,
        final Player player)
    {
        sendTrue(block, player);
        // Built at the call site, so the concatenation is guarded rather than the log line.
        if (WormholeXTreme.getThisPlugin().isLoggable(Level.FINE))
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE,
                "Mirror '" + mirror.name() + "' lit for " + player.getName());
        }
    }

    /**
     * Sends one player the block's own state, undoing any blank they were shown.
     *
     * <p>Only if they are still in that world. The tracked players come from a set that may
     * have been built several sweeps ago -- {@link #restoreAll} and {@link #release} both work
     * from one -- and a block update names a coordinate, not a world. Sent to somebody who has
     * since walked through a gate, it would paint a banner onto whatever stands at those
     * coordinates where they are now.
     */
    private static void sendTrue(final Block block, final Player player)
    {
        if ((player == null) || !player.getWorld().equals(block.getWorld())
            || !(block.getState() instanceof Banner banner))
        {
            return;
        }
        MirrorPackets.send(player, block.getLocation(), banner);
    }

    /** Sends one player the blank, which is the only thing about a mirror that is a lie. */
    private static void hide(final Block block, final Player player)
    {
        final Banner blank = MirrorStamp.blankState(block);
        if (blank != null)
        {
            MirrorPackets.send(player, block.getLocation(), blank);
        }
    }
}
