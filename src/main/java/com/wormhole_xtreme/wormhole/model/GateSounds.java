package com.wormhole_xtreme.wormhole.model;

import org.bukkit.Location;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.utils.Sounds;

/**
 * What a stargate sounds like.
 *
 * <p>Gates were silent. Everything a gate does is already staged over time -- chevrons light
 * one at a time on {@code light-ticks}, the woosh rolls in over {@code woosh-ticks} -- so the
 * animation was there and only the noise was missing.
 *
 * <p>Sounds are named rather than resolved to a {@code Sound} constant; see
 * {@link Sounds} for why. Everything here fails quietly for the same reason: a gate that
 * cannot make a noise should still dial.
 */
public final class GateSounds
{
    /** The pitch the first chevron locks at. */
    private static final float FIRST_CHEVRON_PITCH = 0.8f;

    /** How far the chevrons climb by the last one. */
    private static final float CHEVRON_CLIMB = 0.7f;

    /** Static helpers only. */
    private GateSounds()
    {
    }

    /**
     * Plays a gate beginning to dial.
     *
     * @param gate
     *            the gate waking up
     */
    public static void activated(final Stargate gate)
    {
        play(gate, ConfigManager.getGateSoundActivate(), 1.0f);
    }

    /**
     * Plays one chevron locking.
     *
     * <p>Pitched by how far through the sequence it is, so a gate audibly works towards
     * something rather than clicking the same note seven times. Which chevron this is comes
     * from the lighting iteration the animator is already counting, so the sound cannot drift
     * out of step with the lights -- there is only one counter.
     *
     * @param gate
     *            the gate dialling
     * @param iteration
     *            which lighting step this is, from one
     * @param total
     *            how many steps the sequence has
     */
    public static void chevron(final Stargate gate, final int iteration, final int total)
    {
        play(gate, ConfigManager.getGateSoundChevron(), chevronPitch(iteration, total));
    }

    /**
     * What pitch a chevron locks at.
     *
     * <p>Spread across however many lighting steps a shape has rather than assuming seven:
     * shapes are configurable, and a gate with three chevrons should still climb the same
     * distance, just in bigger steps. A shape with one step gets the first pitch rather than a
     * division by zero.
     *
     * @param iteration
     *            which lighting step this is, from one
     * @param total
     *            how many steps the sequence has
     * @return the pitch
     */
    static float chevronPitch(final int iteration, final int total)
    {
        if (total <= 1)
        {
            return FIRST_CHEVRON_PITCH;
        }
        final float through = Math.min(1.0f,
            Math.max(0.0f, (iteration - 1) / (float) (total - 1)));
        return Sounds.clamp(FIRST_CHEVRON_PITCH + (CHEVRON_CLIMB * through));
    }

    /**
     * Plays the wormhole establishing.
     *
     * @param gate
     *            the gate opening
     */
    public static void kawoosh(final Stargate gate)
    {
        play(gate, ConfigManager.getGateSoundKawoosh(), 1.0f);
    }

    /**
     * Plays a wormhole closing.
     *
     * @param gate
     *            the gate shutting down
     */
    public static void closed(final Stargate gate)
    {
        play(gate, ConfigManager.getGateSoundClose(), 1.0f);
    }

    /**
     * How much quieter the hum is than the things that happen once.
     *
     * <p>An open wormhole is a background, not an event. At the volume of a kawoosh, repeating
     * every few seconds, it would be the loudest thing on the server -- and because Bukkit ties
     * range to volume, this also keeps the hum a thing you hear near the gate rather than
     * across a base.
     */
    private static final float AMBIENT_SCALE = 0.4f;

    /**
     * Plays the hum of every wormhole currently standing open.
     *
     * <p>One sweep over the open gates rather than a repeating task per gate: the work is the
     * same, and there is no per-gate task to start, cancel, or leak when a gate is removed
     * while its wormhole is up. A gate that closes simply stops being in the list.
     */
    public static void tickAmbient()
    {
        final String sound = ConfigManager.getGateSoundAmbient();
        if (!ConfigManager.isGateSoundsEnabled() || sound.isEmpty())
        {
            return;
        }
        for (final Stargate gate : StargateManager.getOpenGates())
        {
            playAt(gate, sound, 1.0f, ConfigManager.getGateSoundVolume() * AMBIENT_SCALE);
        }
    }

    /**
     * Plays one sound at a gate, if gate sounds are on.
     *
     * <p>Heard from the middle of the portal rather than from a corner of the frame, so a gate
     * sounds like it is coming from the wormhole. Falls back to the gate's own block if there
     * is no teleport point recorded -- an incomplete gate can still be shut down.
     *
     * @param gate
     *            the gate
     * @param sound
     *            the sound name
     * @param pitch
     *            the pitch
     */
    private static void play(final Stargate gate, final String sound, final float pitch)
    {
        if (!ConfigManager.isGateSoundsEnabled())
        {
            return;
        }
        playAt(gate, sound, pitch, ConfigManager.getGateSoundVolume());
    }

    /**
     * Plays one sound at a gate, at a given volume.
     *
     * @param gate
     *            the gate
     * @param sound
     *            the sound name
     * @param pitch
     *            the pitch
     * @param volume
     *            the volume, which is also the audible range
     */
    private static void playAt(final Stargate gate, final String sound, final float pitch,
        final float volume)
    {
        if (gate == null)
        {
            return;
        }
        Location where = gate.getGatePlayerTeleportLocation();
        if ((where == null) && (gate.getGateNameBlockHolder() != null))
        {
            where = gate.getGateNameBlockHolder().getLocation();
        }
        Sounds.play(gate.getGateWorld(), where, sound, volume, pitch);
    }
}
