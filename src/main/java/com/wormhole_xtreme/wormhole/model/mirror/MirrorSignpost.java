package com.wormhole_xtreme.wormhole.model.mirror;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.utils.ActionBar;

/**
 * Telling somebody what the banner they are looking at is.
 *
 * <h2>Why looking at, rather than standing near</h2>
 *
 * <p>This began as a line sent once, when a player crossed into the proximity radius. That is
 * how the transport rings announce themselves, and for a ring it is right: walking in starts
 * something. A mirror is not started by arriving at it -- it is looked at, considered, and then
 * clicked -- and an action bar line fades after about three seconds, so the message had come
 * and gone by the moment it was needed. You were told there was a door while walking towards
 * it, and told nothing at all while standing in front of it deciding.
 *
 * <p>The obvious repair, re-sending to everyone in range, is worse than it sounds. A corridor
 * of mirrors puts a player within eight blocks of several at once, and they would take turns
 * in the one action bar slot, flickering once a sweep. Looking at one picks exactly one: a
 * player has a single target block, so there is nothing to arbitrate.
 *
 * <h2>What it costs</h2>
 *
 * <p>Less than the version it replaces, which is the happy part. Announcing on approach meant
 * the proximity sweep had to visit every ordinary mirror to work out who was near it -- a
 * distance check per player per mirror, and the end of the old promise that a server whose
 * mirrors are all ordinary does no work there. This asks each player one question instead,
 * regardless of how many mirrors there are, and asks nobody at all in a world that has none.
 *
 * <p>The line is re-sent every sweep rather than only when the target changes. That is the
 * point of it: the action bar fades on its own, so a steady line is a repeated one. Nothing is
 * remembered between sweeps, which is also why there is no state here to get out of step with
 * a player who logged out, changed world, or had the mirror broken in front of them.
 */
public final class MirrorSignpost
{
    /** How far a player can be and still be looking <em>at</em> a banner rather than past it. */
    private static final int REACH = 6;

    /** Static state only. */
    private MirrorSignpost()
    {
    }

    /** @return the sweep, for the scheduler */
    public static Runnable createTicker()
    {
        return MirrorSignpost::tick;
    }

    /**
     * One pass over everybody who might be looking at a mirror.
     *
     * <p>Two guards before any ray is traced, both cheap and both worth having. A server that
     * has turned this off does nothing, and a server with no mirrors in a player's world does
     * nothing for that player -- which on a big server is most of them.
     */
    static void tick()
    {
        if (!ConfigManager.isMirrorApproachMessage())
        {
            return;
        }
        final Set<String> worlds = worldsWithMirrors();
        if (worlds.isEmpty())
        {
            return;
        }
        for (final Player player : Bukkit.getOnlinePlayers())
        {
            if (worlds.contains(player.getWorld().getName()))
            {
                tell(player);
            }
        }
    }

    /**
     * The worlds any mirror stands in.
     *
     * <p>By name, because that is how a mirror records where it is and the world object may not
     * be loaded. Rebuilt each sweep rather than cached: mirrors are added and removed by
     * command, and a cache would need invalidating from four places to save a walk over a list
     * that is usually shorter than the player list.
     *
     * @return the world names, possibly empty
     */
    private static Set<String> worldsWithMirrors()
    {
        final Set<String> worlds = new HashSet<>();
        for (final QuantumMirror mirror : MirrorManager.all())
        {
            if (mirror.destination() != null)
            {
                worlds.add(mirror.banner().worldName());
            }
        }
        return worlds;
    }

    /**
     * Names the mirror this player is looking at, if they are looking at one.
     *
     * <p>Silent for a mirror that goes nowhere. Announcing one would be the plugin telling
     * whoever glanced at a half-built banner about somebody else's unfinished work; clicking it
     * already says what to do, to the one person who asked.
     *
     * @param player
     *            whoever might be looking
     */
    private static void tell(final Player player)
    {
        final Block looked = player.getTargetBlockExact(REACH);
        if ((looked == null) || !looked.getType().name().endsWith("BANNER"))
        {
            return;
        }
        final QuantumMirror mirror = MirrorManager.at(MirrorBlock.of(looked));
        if ((mirror == null) || (mirror.destination() == null))
        {
            return;
        }
        ActionBar.send(player,
            MirrorText.approach(mirror.name(), mirror.destination().worldName()));
    }
}
