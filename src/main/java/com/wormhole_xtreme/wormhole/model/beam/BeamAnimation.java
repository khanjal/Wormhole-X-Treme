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
 * Runs the beam sequence: a column at the origin rises and brightens -- more particles per
 * burst as it goes, rather than growing taller -- departs, and teleport fires mid-rise; at
 * the destination the same column descends at full brightness and stops, which is the reveal.
 *
 * <p>Reference sequence this follows, worked out in design discussion before any of it was
 * code: the traveller vanishes and a beam rises from where they stood; at the destination a
 * beam comes down before they are revealed there. Reproducing that means the real
 * {@link Player#teleport(Location)} call cannot sit at either end of the animation -- it fires
 * in the middle, once the traveller has had the rise in view, so they get to watch both halves
 * rather than just trigger them. That relies on a real API property: invisibility is
 * observer-relative. An invisible player still sees their own surroundings and any particles
 * normally; it only hides them from <em>other</em> players' clients. So the traveller stays
 * physically present (invisible to everyone else, frozen by {@link BeamFreeze}) through the
 * tail of the rise, then arrives partway through the descent and watches the rest of it.
 *
 * <p>Departure and arrival are deliberately not mirrored the way an earlier version had them.
 * Rising while brightening is what reads as the beam building up to leave; arriving already at
 * full brightness and simply settling is what reads as delivery rather than a second build-up
 * nobody asked for.
 *
 * <p>One asymmetry doesn't come from that choice, though: the destination track could in
 * principle be staged fully independent of the player, but the origin track cannot -- the
 * teleport has to wait on it, at least partially, rather than firing the moment they vanish.
 *
 * <p>Ticks a single self-rescheduling step, the same idiom {@code StargateAnimator} and the
 * ring subsystem already use ({@code scheduleSyncDelayedTask} calling itself), rather than
 * the pure-core/Bukkit-boundary split those two eventually grew into. That split is worth
 * doing once this shape has survived actual play-testing -- not before, for a sequence still
 * being tuned by feel. Durations are read from {@code ConfigManager} at the start of each
 * sequence (not re-read every tick, so a config change mid-flight cannot desync a beam
 * already running), the same way ring timings already are.
 */
public final class BeamAnimation
{
    /** How tall the standing column is. */
    private static final double COLUMN_HEIGHT = 3.0;

    /** The vertical spacing between particle bursts within the column -- small enough that it
     * reads as one continuous beam rather than a stack of discrete points. */
    private static final double COLUMN_STEP = 0.4;

    /** How far the column travels while rising or descending, measured from its own height --
     * past {@link #COLUMN_HEIGHT} so the departing column visibly clears where it started
     * rather than just thickening in place. */
    private static final double TRAVEL_HEIGHT = 4.0;

    /** Particles per burst at the start of the rise. */
    private static final int MIN_DENSITY = 1;

    /** Particles per burst by the end of the rise, and throughout the descent -- the descent
     * does not ramp, since arriving is delivery, not a second build-up. */
    private static final int MAX_DENSITY = 8;

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
     * Draws the column at full height, shifted vertically by {@code yOffset} and with
     * {@code density} particles per burst -- {@code yOffset} of zero is the column standing in
     * place, and ramping it is what rising and descending both turn out to be.
     *
     * @param base where the column is rooted
     * @param yOffset how far the whole column is currently shifted from where it is rooted
     * @param density particles spawned per burst point -- higher reads as brighter
     */
    private static void spawnColumn(final Location base, final double yOffset, final int density)
    {
        final World world = base.getWorld();
        if (world == null)
        {
            return;
        }
        for (double y = 0.0; y <= COLUMN_HEIGHT; y += COLUMN_STEP)
        {
            final Location point = base.clone().add(0.0, y + yOffset, 0.0);
            world.spawnParticle(Particle.END_ROD, point, density, 0.15, 0.05, 0.15, 0.01);
        }
    }

    /** One running sequence. A fresh instance per beam; nothing about it is shared or reused. */
    private static final class Sequence implements Runnable
    {
        private final Player player;
        private final Location origin;
        private final Location destination;
        private final String destinationName;
        private final int riseTicks;
        private final int vanishAtStep;
        private final int teleportAtStep;
        private final int descendTicks;
        private int tick;
        private boolean teleported;

        Sequence(final Player player, final Location destination, final String destinationName)
        {
            this.player = player;
            this.origin = player.getLocation();
            this.destination = destination;
            this.destinationName = destinationName;

            // Read once, not per tick, so a config change mid-flight cannot desync a beam
            // already running. Clamped here rather than in ConfigManager, because the
            // relationships being enforced (teleport strictly inside the rise, vanish strictly
            // before teleport) cross three separate settings at once -- there is no single
            // setting's getter that could validate them alone.
            this.riseTicks = Math.max(2, ConfigManager.getBeamRiseTicks());
            this.descendTicks = Math.max(1, ConfigManager.getBeamDescendTicks());
            this.teleportAtStep = Math.min(Math.max(1, ConfigManager.getBeamTeleportAtStep()), riseTicks - 1);
            this.vanishAtStep = Math.min(Math.max(0, ConfigManager.getBeamVanishAtStep()), teleportAtStep - 1);

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
                BeamSounds.playCharge(origin);
                player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
                    + "Beaming to " + destinationName + "...");
            }

            if (tick == vanishAtStep)
            {
                // Invisibility has to outlast everything from here to the destination. The
                // remainder of the rise plus the full descent is an over-estimate of when
                // it's actually needed until (the descent doesn't reduce it further), which is
                // fine -- explicit removal once the column finishes descending is what the
                // timing actually depends on, and this is only a ceiling against that removal
                // being late.
                player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                    (riseTicks - vanishAtStep) + descendTicks, 0, false, false));
            }

            if (tick < riseTicks)
            {
                final double progress = (double) tick / (double) (riseTicks - 1);
                final int density = MIN_DENSITY + (int) Math.round((MAX_DENSITY - MIN_DENSITY) * progress);
                spawnColumn(origin, TRAVEL_HEIGHT * ((double) tick / (double) riseTicks), density);
            }

            if (!teleported && (tick == teleportAtStep))
            {
                BeamSounds.playDepart(origin);
                player.teleport(destination);
                teleported = true;
            }

            if (teleported)
            {
                final int sinceTeleport = tick - teleportAtStep;

                if (sinceTeleport < descendTicks)
                {
                    spawnColumn(destination,
                        TRAVEL_HEIGHT * (1.0 - ((double) sinceTeleport / (double) descendTicks)), MAX_DENSITY);
                }

                if (sinceTeleport >= descendTicks)
                {
                    BeamSounds.playArrive(destination);
                    player.removePotionEffect(PotionEffectType.INVISIBILITY);
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
