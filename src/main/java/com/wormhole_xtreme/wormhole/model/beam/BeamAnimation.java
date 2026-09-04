package com.wormhole_xtreme.wormhole.model.beam;

import java.util.logging.Level;

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
 * tall column yet), brightening fast, tracking wherever the traveller currently is rather
 * than a fixed spot -- they are not frozen yet and can still walk, turn, react, the way
 * someone in the reference footage still could before being taken. The traveller is
 * visible at first and vanishes partway through -- the "absorption," and the moment they
 * stop being free to move: {@link BeamFreeze} and the departure column's fixed root both
 * take hold on this same tick.</li>
 * <li><b>Rise</b> -- the envelope opens straight into the full-height column, rooted at
 * wherever the traveller was standing the instant they vanished, constant brightness,
 * climbing and departing. The real {@link Player#teleport(Location)} call fires mid-rise,
 * not at the end, so the traveller has had most of it in view before leaving; the
 * remainder plays out at the origin with nobody there.</li>
 * <li><b>Descend</b> -- the same column arrives from above at full height and brightness
 * and settles into place at the destination. The traveller is physically here for all of
 * this (the real teleport already fired, mid-rise), so nothing would otherwise stop them
 * seeing the destination clearly the moment they land -- except
 * {@link org.bukkit.potion.PotionEffectType#BLINDNESS} stacked with
 * {@link org.bukkit.potion.PotionEffectType#DARKNESS}, applied for exactly this stretch, so
 * what they can see stays in step with what has actually finished arriving rather than
 * running ahead of it.</li>
 * <li><b>Deposit and fade</b> -- the instant the column settles, the traveller is revealed
 * (still standing inside the light, not popping in after it) and can see again, and the
 * column collapses back to nothing over a short, deliberately quick tail -- delivery reads
 * as an arrival, not a second build-up.</li>
 * </ol>
 *
 * <p>Reproducing the "disappear into a beam, then reappear out of one" read relies on a real
 * API property: invisibility is observer-relative. An invisible player still sees their own
 * surroundings and any particles normally; it only hides them from <em>other</em> players'
 * clients. So the traveller stays physically present (invisible to everyone else, frozen by
 * {@link BeamFreeze} from the vanish tick on) through the tail of the rise.
 *
 * <p>That same property is a problem on the other side of the teleport, though: nothing
 * about invisibility (or the freeze, which only ever locked position, never camera) stops
 * the traveller from freely looking around the destination the instant they physically
 * arrive, well before the descend column has finished settling -- a clear view they already
 * have, followed by an arrival effect that then reads as arriving late. Blindness alone
 * turned out not to close that gap in play-testing: it is mostly a render-distance fog, not
 * an opaque blackout, so nearby terrain and anything bright -- daylight, torches, the beam's
 * own {@code END_ROD} particles -- still showed through it. Darkness, the real dark vignette
 * a warden or sculk shrieker applies, stacked on top of it is what actually blocks the view.
 * Both are applied the moment the real teleport fires and removed the moment the column
 * settles, the same two ticks invisibility already keys off of, so the traveller's own
 * vision resolves in sync with the visual instead of running ahead of it.
 *
 * <p>One asymmetry doesn't come from mirroring departure and arrival, though: the
 * destination track could in principle be staged fully independent of the player, but the
 * origin track cannot -- the teleport has to wait on it, at least partially, rather than
 * firing the moment they vanish.
 *
 * <p>Ticks a single self-rescheduling step, the same idiom {@code StargateAnimator} and the
 * ring subsystem already use ({@code scheduleSyncDelayedTask} calling itself). Unlike an
 * earlier version of this class, the decisions and the Bukkit calls are no longer tangled
 * together: {@link BeamFrame} computes what a tick should do, purely, and {@link Sequence}
 * (below) is left doing only the Bukkit half -- spawning particles, applying effects, playing
 * sounds, scheduling the next tick -- with no arithmetic of its own to get wrong. The same
 * split the ring subsystem eventually grew ({@code RingCycle} for the decisions,
 * {@code RingTransit} for touching a live world), worth doing once this shape had survived
 * actual play-testing rather than before, for a sequence that was still being tuned by feel.
 * Durations are read from {@code ConfigManager} at the start of each sequence (not re-read
 * every tick, so a config change mid-flight cannot desync a beam already running), the same
 * way ring timings already are.
 */
public final class BeamAnimation
{
    /** The vertical spacing between particle bursts within the column -- small enough that it
     * reads as one continuous beam rather than a stack of discrete points. The one geometry
     * constant that stays here rather than on {@link BeamFrame}: it is a rendering-resolution
     * detail of {@link #spawnColumn}, not something that varies by tick or phase. */
    private static final double COLUMN_STEP = 0.4;

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
        if (BeamFreeze.isActive(player))
        {
            // isActive, not isFrozen: the envelope runs before the traveller is frozen at
            // all, and a second beam must not be allowed to start on top of it during that
            // window either.
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
        private Location origin;
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
            // Where the departure column roots itself, once there is one -- not settled
            // until the vanish tick, since the traveller is free to move around during the
            // envelope and the column should not root anywhere until they stop being able
            // to. This value only matters for the tick-0 charge sound until then.
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
                // They are gone; nothing left to animate, and nothing left to clear --
                // BeamFreezeListener already cleared it on the way out.
                return;
            }

            // Once isVanish() has fired below, the player is frozen and invisible until
            // either isFinished() clears them or something throws first. A Bukkit call
            // throwing mid-tick (spawnParticle, teleport, addPotionEffect) would otherwise
            // die here with the freeze never lifted and nothing left running to lift it --
            // exactly the "frozen with no way out" failure BeamTiming exists to prevent,
            // reachable another way. Same shape as RingTransit's own tick-level recovery.
            try
            {
                tick();
            }
            catch (final RuntimeException e)
            {
                recover(e);
            }
        }

        private void tick()
        {
            // Everything about *what* happens this tick is decided by BeamFrame, purely
            // from the tick number and timing -- this method's only job left is *doing*
            // it: Bukkit calls, in the order BeamFrame says they apply, nothing more.
            final BeamFrame frame = BeamFrame.at(tick, timing);

            if (frame.isStart())
            {
                BeamFreeze.markActive(player);
                BeamSounds.playCharge(origin);
                player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
                    + "Beaming to " + destinationName + "...");
            }

            if (frame.isEnvelopActive())
            {
                // Not yet frozen -- tracks wherever the traveller actually is this tick,
                // rather than the fixed origin, since they are still free to walk, turn or
                // react right up until they vanish. A fixed column here would just miss
                // them the moment they stepped away from where the sequence began.
                spawnColumn(player.getLocation(), frame.playerHeight(), 0.0, frame.getEnvelopDensity());
            }

            if (frame.isVanish())
            {
                // This is the moment free movement ends: note where they are right now --
                // the departure column roots here for the rest of the rise -- then lock
                // them in place. Capturing the location before freezing, not after, is
                // what keeps the last envelope frame and the first rise frame coincident
                // rather than one tick apart.
                origin = player.getLocation();
                BeamFreeze.freeze(player);

                // Invisibility has to outlast everything from here to the deposit. The
                // remainder of the envelope plus the full rise and descent is an
                // over-estimate of when it's actually needed until, which is fine --
                // explicit removal at the deposit is what the timing actually depends on,
                // and this is only a ceiling against that removal being late.
                player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                    (timing.envelopTicks() - timing.vanishAtStep()) + timing.riseTicks() + timing.descendTicks(),
                    0, false, false));
            }

            if (frame.isRiseActive())
            {
                spawnColumn(origin, frame.columnHeight(), frame.getRiseYOffset(), BeamFrame.MAX_DENSITY);
            }

            if (!teleported && frame.isTeleport())
            {
                BeamSounds.playDepart(origin);
                player.teleport(destination);
                teleported = true;
                // The traveller is physically at the destination from this tick on -- the
                // descend column is still only starting to fall around them, but nothing
                // stops their own eyes from seeing straight through it to the terrain
                // beyond, well before the "arrival" it is meant to sell has actually
                // finished. Blindness and darkness are both applied together, not
                // blindness alone: blindness by itself is mostly a render-distance fog,
                // not an opaque blackout -- nearby terrain and anything bright (daylight,
                // torches, the beam's own END_ROD particles) still shows through it, which
                // is exactly the gap the reference report caught in play-testing. Darkness
                // is the real dark vignette (the same effect a warden/sculk shrieker
                // applies), and stacking it on top of blindness is what actually blocks
                // the view rather than just softening it. Both key off the same two ticks
                // invisibility already uses: an over-estimate covering descend plus fade,
                // with explicit removal at the arrive tick as what the timing actually
                // depends on.
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,
                    timing.descendTicks() + timing.fadeTicks(), 0, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS,
                    timing.descendTicks() + timing.fadeTicks(), 0, false, false));
                if (onDepart != null)
                {
                    onDepart.run();
                }
            }

            if (teleported)
            {
                if (frame.isDescendActive())
                {
                    spawnColumn(destination, frame.columnHeight(), frame.getDescendYOffset(), BeamFrame.MAX_DENSITY);
                }

                if (frame.isArrive())
                {
                    BeamSounds.playArrive(destination);
                    player.removePotionEffect(PotionEffectType.INVISIBILITY);
                    player.removePotionEffect(PotionEffectType.BLINDNESS);
                    player.removePotionEffect(PotionEffectType.DARKNESS);
                }

                if (frame.isFadeActive())
                {
                    spawnColumn(destination, frame.getFadeHeight(), 0.0, frame.getFadeDensity());
                }

                if (frame.isFinished())
                {
                    BeamFreeze.clear(player);
                    player.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
                        + "Beamed to " + destinationName + ".");
                    return;
                }
            }

            tick++;
            WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), this, 1L);
        }

        /**
         * Clears a traveller a failed tick would otherwise have left stuck: frozen (so they
         * cannot even move themselves out of it), invisible or blind to a degree the timing
         * expected to remove explicitly later, and permanently {@code ACTIVE} in
         * {@link BeamFreeze} -- which refuses every future beam for them until something
         * clears it. Best-effort by design, the same reasoning as {@code RingTransit}'s own
         * recovery: a second failure while cleaning up must not leave anything worse than the
         * first one already did.
         *
         * @param cause what actually threw
         */
        private void recover(final RuntimeException cause)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false,
                "Beam to \"" + destinationName + "\" failed mid-sequence for " + player.getName()
                    + ", clearing them rather than leaving them stuck: " + cause.getMessage());
            try
            {
                player.removePotionEffect(PotionEffectType.INVISIBILITY);
                player.removePotionEffect(PotionEffectType.BLINDNESS);
            }
            catch (final RuntimeException ignored) { /* best effort; BeamFreeze.clear below is what actually matters */ }
            BeamFreeze.clear(player);
            try
            {
                player.sendMessage(ConfigManager.MessageStrings.errorHeader.toString()
                    + "Something went wrong mid-beam; you have been freed rather than left stuck.");
            }
            catch (final RuntimeException ignored) { /* the freeze is already cleared either way */ }
        }
    }
}
