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
 * Runs the beam sequence, matched beat for beat against the reference: a bright glow gathers
 * at the traveller's body and appears to absorb them; they and the light disappear into a
 * beam that rises and departs; at the destination the beam deposits them, with the light
 * still there, and it fades quickly.
 *
 * <ol>
 * <li><b>Envelop</b> -- a dense burst of {@code Particle.END_ROD} at body height (not the
 * tall column yet), brightening fast. The traveller is visible at first and vanishes
 * partway through -- the "absorption."</li>
 * <li><b>Rise</b> -- the envelope opens straight into the full-height column, constant
 * brightness, climbing and departing. The real {@link Player#teleport(Location)} call
 * fires mid-rise, not at the end, so the traveller has had most of it in view before
 * leaving; the remainder plays out at the origin with nobody there.</li>
 * <li><b>Descend</b> -- the same column arrives from above at full height and brightness
 * and settles into place at the destination.</li>
 * <li><b>Deposit and fade</b> -- the instant the column settles, the traveller is revealed
 * (still standing inside the light, not popping in after it), and the column collapses
 * back to nothing over a short, deliberately quick tail -- delivery reads as an arrival,
 * not a second build-up.</li>
 * </ol>
 *
 * <p>Reproducing the "disappear into a beam, then reappear out of one" read relies on a real
 * API property: invisibility is observer-relative. An invisible player still sees their own
 * surroundings and any particles normally; it only hides them from <em>other</em> players'
 * clients. So the traveller stays physically present (invisible to everyone else, frozen by
 * {@link BeamFreeze}) through the tail of the rise, then arrives partway through the descent
 * and watches the rest of it.
 *
 * <p>One asymmetry doesn't come from mirroring departure and arrival, though: the
 * destination track could in principle be staged fully independent of the player, but the
 * origin track cannot -- the teleport has to wait on it, at least partially, rather than
 * firing the moment they vanish.
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
    /** Roughly a standing player's own height -- where the envelope gathers, before it opens
     * into the taller departure column. */
    private static final double PLAYER_HEIGHT = 1.8;

    /** How tall the column stands once the envelope opens into it. */
    private static final double COLUMN_HEIGHT = 3.0;

    /** The vertical spacing between particle bursts within the column -- small enough that it
     * reads as one continuous beam rather than a stack of discrete points. */
    private static final double COLUMN_STEP = 0.4;

    /** How far the column travels while rising or descending, measured from its own height --
     * past {@link #COLUMN_HEIGHT} so the departing column visibly clears where it started
     * rather than just thickening in place. */
    private static final double TRAVEL_HEIGHT = 4.0;

    /** Particles per burst at the start of the envelope. */
    private static final int MIN_DENSITY = 1;

    /** Particles per burst once the glow has built up -- reached by the end of the envelope
     * and held constant through rise and descent; delivery and departure are not a second
     * and third build-up, only the envelope is. */
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
        return start(player, destination, destinationName, null);
    }

    /**
     * Starts the sequence, running {@code onDepart} at the exact tick the real teleport
     * fires -- not before starting, and not after the sequence finishes.
     *
     * <p>That timing is deliberate, not incidental: a cost or cooldown applied at the point
     * of starting rather than of actually leaving would be spent on a trip that had not
     * happened yet and, if the player went offline mid-sequence, might never happen at all --
     * the same reasoning gate travel already applies to when it sets its own cooldown.
     * {@code BeamAnimation} stays unaware of what it's running for; {@link BeamTravel} is
     * what supplies a hook that charges and cools down, and {@code /wormhole go}'s gate
     * branch supplies none at all, since a gate reached that way already has its own,
     * separate cooldown and economy system this was never meant to duplicate.
     *
     * @param player the traveller
     * @param destination where they are going
     * @param destinationName what to call it once they arrive
     * @param onDepart run once, the instant {@link Player#teleport(Location)} fires; may be
     *            null
     * @return true if the sequence started; false if they were already beaming somewhere
     *         (a message has already been sent in that case, and {@code onDepart} never runs)
     */
    public static boolean start(final Player player, final Location destination, final String destinationName,
        final Runnable onDepart)
    {
        if (BeamFreeze.isFrozen(player))
        {
            player.sendMessage(ConfigManager.MessageStrings.errorHeader.toString()
                + "You're already beaming somewhere.");
            return false;
        }
        WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(),
            new Sequence(player, destination, destinationName, onDepart), 1L);
        return true;
    }

    /**
     * Draws a column from the ground up to {@code height}, shifted vertically by
     * {@code yOffset} and with {@code density} particles per burst. {@code height} is what
     * separates the envelope (body height) from the departure/arrival column (full height);
     * {@code yOffset} is what rising and descending both turn out to be; {@code density} is
     * what brightening and fading both turn out to be.
     *
     * @param base where the column is rooted
     * @param height how tall the column currently is
     * @param yOffset how far the whole column is currently shifted from where it is rooted
     * @param density particles spawned per burst point -- higher reads as brighter
     */
    private static void spawnColumn(final Location base, final double height, final double yOffset,
        final int density)
    {
        if (density <= 0)
        {
            return;
        }
        final World world = base.getWorld();
        if (world == null)
        {
            return;
        }
        for (double y = 0.0; y <= height; y += COLUMN_STEP)
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
        private final Runnable onDepart;
        private final BeamTiming timing;
        private int tick;
        private boolean teleported;

        Sequence(final Player player, final Location destination, final String destinationName,
            final Runnable onDepart)
        {
            this.player = player;
            this.origin = player.getLocation();
            this.destination = destination;
            this.destinationName = destinationName;
            this.onDepart = onDepart;

            // Read once, not per tick, so a config change mid-flight cannot desync a beam
            // already running.
            this.timing = BeamTiming.resolve(
                ConfigManager.getBeamEnvelopTicks(),
                ConfigManager.getBeamVanishAtStep(),
                ConfigManager.getBeamRiseTicks(),
                ConfigManager.getBeamTeleportAtStep(),
                ConfigManager.getBeamDescendTicks(),
                ConfigManager.getBeamFadeTicks());

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

            final int envelopTicks = timing.envelopTicks();
            if (tick < envelopTicks)
            {
                final double progress = (double) tick / (double) (envelopTicks - 1);
                final int density = MIN_DENSITY + (int) Math.round((MAX_DENSITY - MIN_DENSITY) * progress);
                spawnColumn(origin, PLAYER_HEIGHT, 0.0, density);
            }

            if (tick == timing.vanishAtStep())
            {
                // Invisibility has to outlast everything from here to the deposit. The
                // remainder of the envelope plus the full rise and descent is an
                // over-estimate of when it's actually needed until, which is fine --
                // explicit removal at the deposit is what the timing actually depends on,
                // and this is only a ceiling against that removal being late.
                player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                    (envelopTicks - timing.vanishAtStep()) + timing.riseTicks() + timing.descendTicks(),
                    0, false, false));
            }

            final int riseTicks = timing.riseTicks();
            final int sinceRise = tick - envelopTicks;
            if ((sinceRise >= 0) && (sinceRise < riseTicks))
            {
                spawnColumn(origin, COLUMN_HEIGHT, TRAVEL_HEIGHT * ((double) sinceRise / (double) riseTicks),
                    MAX_DENSITY);
            }

            if (!teleported && (sinceRise == timing.teleportAtStep()))
            {
                BeamSounds.playDepart(origin);
                player.teleport(destination);
                teleported = true;
                if (onDepart != null)
                {
                    onDepart.run();
                }
            }

            if (teleported)
            {
                final int descendTicks = timing.descendTicks();
                final int sinceTeleport = sinceRise - timing.teleportAtStep();

                if (sinceTeleport < descendTicks)
                {
                    spawnColumn(destination, COLUMN_HEIGHT,
                        TRAVEL_HEIGHT * (1.0 - ((double) sinceTeleport / (double) descendTicks)), MAX_DENSITY);
                }

                if (sinceTeleport == descendTicks)
                {
                    BeamSounds.playArrive(destination);
                    player.removePotionEffect(PotionEffectType.INVISIBILITY);
                }

                final int fadeTicks = timing.fadeTicks();
                final int sinceDeposit = sinceTeleport - descendTicks;
                if ((sinceDeposit >= 0) && (sinceDeposit < fadeTicks))
                {
                    final double fadeProgress = (double) sinceDeposit / (double) fadeTicks;
                    final double height = COLUMN_HEIGHT - ((COLUMN_HEIGHT - PLAYER_HEIGHT) * fadeProgress);
                    final int density = MAX_DENSITY - (int) Math.round((MAX_DENSITY - MIN_DENSITY) * fadeProgress);
                    spawnColumn(destination, height, 0.0, density);
                }

                if (sinceDeposit >= fadeTicks)
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
