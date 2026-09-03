package com.wormhole_xtreme.wormhole.model.beam;

import org.bukkit.Color;
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

    /** How far the charging orb grows before it collapses -- most of the charge, so the
     * collapse into beam-up reads as quick by comparison. */
    private static final int ORB_GROW_TICKS = (CHARGE_TICKS * 7) / 10;

    private static final double ORB_MAX_RADIUS = 0.5;

    /** Roughly chest height above where a player is standing. */
    private static final double ORB_HEIGHT = 1.2;

    /**
     * The dust particle, resolved by name rather than referenced as a compile-time constant.
     *
     * <p>Confirmed against the actual API jars rather than assumed: it is {@code REDSTONE} at
     * this project's 1.20.4 floor and {@code DUST} from 1.20.6 onward through 1.21.10, the top
     * of the supported range -- there is no single name that both compiles at the floor and
     * resolves at the ceiling. {@link Particle} is a plain enum, not a registry-backed type
     * that fails to initialise before the server has started (unlike {@code Sound}, or
     * {@code Attribute} in the locator-bar investigation), so a string lookup here is safe at
     * class-load time rather than something that has to be deferred.
     */
    private static final Particle DUST_PARTICLE = resolveDustParticle();

    private static Particle resolveDustParticle()
    {
        try
        {
            return Particle.valueOf("DUST");
        }
        catch (final IllegalArgumentException newNameNotHere)
        {
            return Particle.valueOf("REDSTONE");
        }
    }

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

    /**
     * The charging orb: grows for most of the charge phase, then collapses quickly, timed to
     * finish right as beam-up takes over.
     *
     * @param center where the player is standing
     * @param step how far into the charge phase this tick is
     */
    private static void spawnOrbParticle(final Location center, final int step)
    {
        final World world = center.getWorld();
        if (world == null)
        {
            return;
        }
        final double radius = step < ORB_GROW_TICKS
            ? ORB_MAX_RADIUS * ((double) step / (double) ORB_GROW_TICKS)
            : ORB_MAX_RADIUS * (1.0 - ((double) (step - ORB_GROW_TICKS) / (double) (CHARGE_TICKS - ORB_GROW_TICKS)));
        final Location point = center.clone().add(0.0, ORB_HEIGHT, 0.0);
        world.spawnParticle(DUST_PARTICLE, point, 10, radius, radius, radius, 0.0,
            new Particle.DustOptions(orbColor(), 1.2f));
    }

    /**
     * Reads {@code beam.orb-color} as a hex triplet. White on anything that will not parse --
     * decoration, not worth failing a beam over, the same tolerance {@link BeamSounds} gives
     * an unknown sound name.
     *
     * @return the configured colour, or white
     */
    private static Color orbColor()
    {
        final String hex = ConfigManager.getBeamOrbColor();
        try
        {
            final String cleaned = hex.startsWith("#") ? hex.substring(1) : hex;
            return cleaned.length() == 6 ? Color.fromRGB(Integer.parseInt(cleaned, 16)) : Color.WHITE;
        }
        catch (final RuntimeException e)
        {
            return Color.WHITE;
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

            if (tick < CHARGE_TICKS)
            {
                spawnOrbParticle(origin, tick);
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
