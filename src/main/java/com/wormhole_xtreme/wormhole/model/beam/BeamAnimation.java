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
 * Runs the beam sequence: charge, beam up, teleport mid-beam-up, beam down, reveal.
 *
 * <p>Reference sequence this follows, worked out in design discussion before any of it was
 * code: the traveller glows, vanishes, and a beam rises from where they stood; at the
 * destination a beam comes down before they are revealed there. Reproducing that means the
 * real {@link Player#teleport(Location)} call cannot sit at either end of the animation -- it
 * fires in the middle, once the traveller has had the beam-up in view, so they get to watch
 * both halves rather than just trigger them. That relies on a real API property: invisibility
 * is observer-relative. An invisible player still sees their own surroundings and any
 * particles normally; it only hides them from <em>other</em> players' clients. So the
 * traveller stays physically present (invisible to everyone else, frozen by
 * {@link BeamFreeze}) through the tail of beam-up, then arrives partway through beam-down and
 * watches the rest of it.
 *
 * <p>One asymmetry falls out of that: the destination track could in principle be staged
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
    private static final int CHARGE_TICKS = 30;

    /** Invisible; a column of particles climbs at the origin. */
    private static final int BEAM_UP_TICKS = 20;

    /** How far into beam-up the real teleport fires -- before it finishes, so the remainder
     * plays out at the origin with nobody there, same as the destination track does before
     * the traveller arrives into it. */
    private static final int TELEPORT_AT_STEP = 14;

    /** A column of particles descends at the destination before the reveal. */
    private static final int BEAM_DOWN_TICKS = 20;

    /** How long the traveller stays glowing (and everyone can see it) once revealed. */
    private static final int REVEAL_TICKS = 15;

    private static final int COLUMN_HEIGHT = 3;

    private BeamAnimation() {}

    /**
     * Starts the sequence. Does nothing but log if the player is already mid-sequence --
     * {@link BeamFreeze} is the guard against a second beam stacking effects onto the first.
     *
     * @param player the traveller
     * @param destination where they are going
     * @param destinationName what to call it once they arrive
     */
    public static void start(final Player player, final Location destination, final String destinationName)
    {
        WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(),
            new Sequence(player, destination, destinationName), 1L);
    }

    private static void spawnColumnParticle(final Location base, final int step, final int totalSteps,
        final boolean rising)
    {
        final World world = base.getWorld();
        if (world == null)
        {
            return;
        }
        final double progress = (double) step / (double) totalSteps;
        final double y = (rising ? progress : (1.0 - progress)) * COLUMN_HEIGHT;
        final Location point = base.clone().add(0.0, y, 0.0);
        world.spawnParticle(Particle.END_ROD, point, 6, 0.3, 0.05, 0.3, 0.01);
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
                player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                    (BEAM_UP_TICKS - TELEPORT_AT_STEP) + BEAM_DOWN_TICKS + REVEAL_TICKS, 0, false, false));
            }

            if ((tick >= CHARGE_TICKS) && (tick < CHARGE_TICKS + BEAM_UP_TICKS))
            {
                spawnColumnParticle(origin, tick - CHARGE_TICKS, BEAM_UP_TICKS, true);
            }

            if (!teleported && (tick == CHARGE_TICKS + TELEPORT_AT_STEP))
            {
                BeamSounds.playDepart(origin);
                player.teleport(destination);
                teleported = true;
            }

            if (teleported)
            {
                final int sinceTeleport = tick - (CHARGE_TICKS + TELEPORT_AT_STEP);
                if (sinceTeleport < BEAM_DOWN_TICKS)
                {
                    spawnColumnParticle(destination, sinceTeleport, BEAM_DOWN_TICKS, false);
                }
                else if (sinceTeleport == BEAM_DOWN_TICKS)
                {
                    BeamSounds.playArrive(destination);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, REVEAL_TICKS, 0, false, false));
                    player.removePotionEffect(PotionEffectType.INVISIBILITY);
                }
                else if (sinceTeleport >= BEAM_DOWN_TICKS + REVEAL_TICKS)
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
