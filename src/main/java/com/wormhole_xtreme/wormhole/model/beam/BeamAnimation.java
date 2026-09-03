package com.wormhole_xtreme.wormhole.model.beam;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;

/**
 * Runs the beam sequence: charge, vanish, a column grows out of where the traveller stood and
 * departs upward -- teleport fires mid-rise -- then at the destination a column descends into
 * place and shrinks back down to nothing, which is what the reveal resolves out of.
 *
 * <p>Reference sequence this follows, worked out in design discussion before any of it was
 * code: the traveller glows, vanishes, and a beam rises from where they stood; at the
 * destination a beam comes down before they are revealed there. Reproducing that means the
 * real {@link Player#teleport(Location)} call cannot sit at either end of the animation -- it
 * fires in the middle, once the traveller has had the rise in view, so they get to watch both
 * halves rather than just trigger them. That relies on a real API property: invisibility is
 * observer-relative. An invisible player still sees their own surroundings and any particles
 * normally; it only hides them from <em>other</em> players' clients. So the traveller stays
 * physically present (invisible to everyone else, frozen by {@link BeamFreeze}) through the
 * tail of the rise, then arrives partway through the descent and watches the rest of it.
 *
 * <p>Growing the column out of the traveller rather than having it simply appear, and
 * shrinking it back into them on arrival rather than having it simply stop, is what keeps this
 * reading as a beam doing the transport rather than the transport interrupting a beam --
 * departure and arrival are the same motion run in opposite directions.
 *
 * <p>One asymmetry doesn't mirror, though: the destination track could in principle be staged
 * fully independent of the player, but the origin track cannot -- the teleport has to wait on
 * it, at least partially, rather than firing the moment they vanish.
 *
 * <p>Ticks a single self-rescheduling step, the same idiom {@code StargateAnimator} and the
 * ring subsystem already use ({@code scheduleSyncDelayedTask} calling itself), rather than
 * the pure-core/Bukkit-boundary split those two eventually grew into. That split is worth
 * doing once these numbers and this shape have survived actual play-testing -- not before,
 * for a sequence whose durations are still being tuned by feel.
 */
public final class BeamAnimation
{
    /** Glow warms up, charge sound plays once. */
    private static final int CHARGE_TICKS = 20;

    /** The column grows from nothing, rooted at the traveller, up to full height. Mirrored by
     * {@link #SHRINK_TICKS} on arrival. */
    private static final int GROW_TICKS = 15;

    /** The now-full-height column climbs and departs upward at the origin. */
    private static final int RISE_TICKS = 20;

    /** How far into the rise the real teleport fires -- before it finishes, so the remainder
     * plays out at the origin with nobody there, same as the destination track does before
     * the traveller arrives into it. */
    private static final int TELEPORT_AT_STEP = 14;

    /** The column descends into place at the destination, already full height. */
    private static final int DESCEND_TICKS = 20;

    /** The column shrinks back down to nothing, rooted at the traveller -- growth in reverse,
     * and what the reveal resolves out of. */
    private static final int SHRINK_TICKS = 15;

    /** How long the traveller stays glowing (and everyone can see it) once revealed. */
    private static final int REVEAL_TICKS = 15;

    /** How tall the column stands once fully grown. */
    private static final double COLUMN_HEIGHT = 3.0;

    /** The vertical spacing between particle bursts within the column -- small enough that it
     * reads as one continuous beam rather than a stack of discrete points. */
    private static final double COLUMN_STEP = 0.4;

    /** How far the column travels while rising or descending, measured from its own full
     * height -- past {@link #COLUMN_HEIGHT} so the departing column visibly clears where it
     * started rather than just thickening in place. */
    private static final double TRAVEL_HEIGHT = 4.0;

    private BeamAnimation() {}

    /**
     * Starts the sequence, unless the player is already mid-beam -- checked and messaged
     * here, once, rather than by every caller. {@code /wormhole beam to}, {@code /wormhole
     * go} resolving to a place, and {@code /wormhole go} resolving to a gate all end up here,
     * so this is the one place that guard needs to live.
     *
     * @param player the traveller
     * @param destination where they are going
     * @param destinationName what to call it once they arrive
     * @return true if the sequence started; false if they were already beaming somewhere
     *         (a message has already been sent in that case)
     */
    public static boolean start(final Player player, final Location destination, final String destinationName)
    {
        if (BeamFreeze.isFrozen(player))
        {
            player.sendMessage(ConfigManager.MessageStrings.errorHeader.toString()
                + "You're already beaming somewhere.");
            return false;
        }
        WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(),
            new Sequence(player, destination, destinationName), 1L);
        return true;
    }

    /**
     * Draws a column from the ground up to {@code height}, shifted vertically by
     * {@code yOffset} -- {@code (height, yOffset)} of {@code (0, 0)} is nothing at all,
     * {@code (COLUMN_HEIGHT, 0)} is the full standing column, and ramping either argument is
     * what growing, shrinking, rising and descending all turn out to be.
     *
     * @param base where the column is rooted
     * @param height how tall the column currently is
     * @param yOffset how far the whole column is currently shifted from where it is rooted
     */
    private static void spawnColumn(final Location base, final double height, final double yOffset)
    {
        final World world = base.getWorld();
        if (world == null)
        {
            return;
        }
        for (double y = 0.0; y <= height; y += COLUMN_STEP)
        {
            final Location point = base.clone().add(0.0, y + yOffset, 0.0);
            world.spawnParticle(Particle.END_ROD, point, 2, 0.15, 0.05, 0.15, 0.01);
        }
    }

    /** One running sequence. A fresh instance per beam; nothing about it is shared or reused. */
    private static final class Sequence implements Runnable
    {
        private final Player player;
        private final Location origin;
        private final Location destination;
        private final String destinationName;
        private int tick;
        private boolean teleported;

        Sequence(final Player player, final Location destination, final String destinationName)
        {
            this.player = player;
            this.origin = player.getLocation();
            this.destination = destination;
            this.destinationName = destinationName;
            this.tick = 0;
            this.teleported = false;
        }

        @Override
        public void run()
        {
            if (!player.isOnline())
            {
                // They are gone; nothing left to animate, and nothing left to unfreeze --
                // BeamFreezeListener already cleared it on the way out.
                return;
            }

            if (tick == 0)
            {
                BeamFreeze.freeze(player);
                player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, CHARGE_TICKS, 0, false, false));
                BeamSounds.playCharge(origin);
                player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
                    + "Beaming to " + destinationName + "...");
            }

            if (tick == CHARGE_TICKS)
            {
                // Invisibility has to outlast everything from here to the reveal. GROW_TICKS
                // plus the full RISE_TICKS is an over-estimate of when teleport actually fires
                // (it fires TELEPORT_AT_STEP into the rise, not at the end of it), which is
                // fine -- explicit removal at the reveal is what the timing actually depends
                // on, and this is only a ceiling against that removal being late.
                player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                    GROW_TICKS + RISE_TICKS + DESCEND_TICKS + SHRINK_TICKS + REVEAL_TICKS, 0, false, false));
            }

            final int sinceGrow = tick - CHARGE_TICKS;
            if ((sinceGrow >= 0) && (sinceGrow < GROW_TICKS))
            {
                spawnColumn(origin, COLUMN_HEIGHT * ((double) sinceGrow / (double) GROW_TICKS), 0.0);
            }

            final int sinceRise = sinceGrow - GROW_TICKS;
            if ((sinceRise >= 0) && (sinceRise < RISE_TICKS))
            {
                spawnColumn(origin, COLUMN_HEIGHT, TRAVEL_HEIGHT * ((double) sinceRise / (double) RISE_TICKS));
            }

            if (!teleported && (sinceRise == TELEPORT_AT_STEP))
            {
                BeamSounds.playDepart(origin);
                player.teleport(destination);
                teleported = true;
            }

            if (teleported)
            {
                final int sinceTeleport = tick - (CHARGE_TICKS + GROW_TICKS + TELEPORT_AT_STEP);

                if (sinceTeleport < DESCEND_TICKS)
                {
                    spawnColumn(destination, COLUMN_HEIGHT,
                        TRAVEL_HEIGHT * (1.0 - ((double) sinceTeleport / (double) DESCEND_TICKS)));
                }

                if (sinceTeleport == DESCEND_TICKS)
                {
                    BeamSounds.playArrive(destination);
                }

                final int sinceShrink = sinceTeleport - DESCEND_TICKS;
                if ((sinceShrink >= 0) && (sinceShrink < SHRINK_TICKS))
                {
                    spawnColumn(destination,
                        COLUMN_HEIGHT * (1.0 - ((double) sinceShrink / (double) SHRINK_TICKS)), 0.0);
                }

                if (sinceShrink == SHRINK_TICKS)
                {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, REVEAL_TICKS, 0, false, false));
                    player.removePotionEffect(PotionEffectType.INVISIBILITY);
                }

                if (sinceShrink >= SHRINK_TICKS + REVEAL_TICKS)
                {
                    BeamFreeze.unfreeze(player);
                    player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
                        + "Beamed to " + destinationName + ".");
                    return;
                }
            }

            tick++;
            WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), this, 1L);
        }
    }
}
