package com.wormhole_xtreme.wormhole.config;

import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;

/** The beam sounds, timings, cooldown and cost; {@link ConfigManager} delegates here. */
final class BeamSettings
{
    /** Static helpers only. */
    private BeamSettings()
    {
    }

    /** A shipped beam duration at {@code beam-animation-speed}. */
    private static int beamTicks(final int ticks)
    {
        return ConfigManager.atSpeed(ticks, getBeamAnimationSpeed());
    }

    /**
     * How fast the beaming animation runs against its shipped timings (#589).
     *
     * @return 1.0 for the shipped timings, from {@link ConfigManager#SLOWEST_SPEED} to {@link ConfigManager#FASTEST_SPEED}
     */
    static double getBeamAnimationSpeed()
    {
        return ConfigManager.speedSetting(ConfigKeys.BEAM_ANIMATION_SPEED);
    }

    /**
     * Whether beaming makes any noise at all.
     *
     * @return true if beam sounds should play
     */
    static boolean isBeamSoundsEnabled()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.BEAM_SOUNDS_ENABLED);
        return (s == null) || s.getBooleanValue();
    }

    /**
     * How loud beam sounds are.
     *
     * @return 1.0 when unset; past 1.0 it widens the range heard rather than the loudness
     */
    static float getBeamSoundVolume()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.BEAM_SOUND_VOLUME);
        return (s == null) ? 1.0f : (float) s.getDoubleValue();
    }

    /**
     * The sound played the instant a beam sequence starts.
     *
     * @return the sound name, or empty for silence
     */
    static String getBeamSoundCharge()
    {
        return ConfigManager.soundSetting(ConfigKeys.BEAM_SOUND_CHARGE, "block.respawn_anchor.charge");
    }

    /**
     * The sound played the instant the real teleport fires, mid-rise.
     *
     * @return the sound name, or empty for silence
     */
    static String getBeamSoundDepart()
    {
        return ConfigManager.soundSetting(ConfigKeys.BEAM_SOUND_DEPART, "entity.enderman.teleport");
    }

    /**
     * The sound played once the column finishes descending at the destination.
     *
     * @return the sound name, or empty for silence
     */
    static String getBeamSoundArrive()
    {
        return ConfigManager.soundSetting(ConfigKeys.BEAM_SOUND_ARRIVE, "entity.shulker.teleport");
    }

    /**
     * How long the glow gathers at body height before opening into the departure column.
     *
     * @return ticks at {@code beam-animation-speed}, unclamped -- {@code BeamTiming} is where the
     *         relationships between this and the other beam timings are enforced
     */
    static int getBeamEnvelopTicks()
    {
        return beamTicks(12);
    }

    /**
     * How far into the envelope the traveller vanishes.
     *
     * @return ticks at {@code beam-animation-speed}, unclamped -- see {@link #getBeamEnvelopTicks()}
     */
    static int getBeamVanishAtStep()
    {
        return beamTicks(6);
    }

    /**
     * How long the column rises and departs, once the envelope opens into it.
     *
     * @return ticks at {@code beam-animation-speed}, unclamped -- see {@link #getBeamEnvelopTicks()}
     */
    static int getBeamRiseTicks()
    {
        return beamTicks(18);
    }

    /**
     * How far into the rise the real teleport fires.
     *
     * @return ticks at {@code beam-animation-speed}, unclamped -- see {@link #getBeamEnvelopTicks()}
     */
    static int getBeamTeleportAtStep()
    {
        return beamTicks(12);
    }

    /**
     * How long the column takes to descend into place at the destination.
     *
     * @return ticks at {@code beam-animation-speed}, unclamped -- see {@link #getBeamEnvelopTicks()}
     */
    static int getBeamDescendTicks()
    {
        return beamTicks(20);
    }

    /**
     * How long the column takes to fade out once it has deposited the traveller.
     *
     * @return ticks at {@code beam-animation-speed}, unclamped -- see {@link #getBeamEnvelopTicks()}
     */
    static int getBeamFadeTicks()
    {
        return beamTicks(8);
    }

    /**
     * Whether beam travel has a per-player cooldown at all.
     *
     * @return true if a cooldown should be enforced
     */
    static boolean isBeamUseCooldownEnabled()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.BEAM_USE_COOLDOWN_ENABLED);
        return (s != null) && s.getBooleanValue();
    }

    /**
     * How many seconds a player must wait between beams, when the cooldown is enabled.
     *
     * @return the cooldown in seconds
     */
    static long getBeamUseCooldownSeconds()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.BEAM_USE_COOLDOWN_SECONDS);
        return (s == null) ? 120 : s.getIntValue();
    }

    /**
     * How much a beam costs, in whatever currency Vault is connected to.
     *
     * @return the cost; 0 or below means free
     */
    static double getBeamEconomyUseCost()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.BEAM_ECONOMY_USE_COST);
        return (s == null) ? 0.0 : s.getDoubleValue();
    }
}
