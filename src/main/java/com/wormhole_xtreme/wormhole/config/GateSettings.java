package com.wormhole_xtreme.wormhole.config;

import java.util.Locale;

import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.logic.DialSpinPattern;
import com.wormhole_xtreme.wormhole.model.MaterialGroup;

/** The gate sound, dial, iris, view, funnel, ripple and preview settings; {@link ConfigManager} delegates here. */
final class GateSettings
{
    /** Static helpers only. */
    private GateSettings()
    {
    }

    /**
     * @return the configured value of {@code gate-iris-animation}, trimmed
     */
    private static String gateIrisAnimation()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.GATE_IRIS_ANIMATION);
        return (s == null) ? "sweep" : String.valueOf(s.getStringValue()).trim();
    }

    /**
     * How a dialling gate's inner ring light moves. A config.yml from before patterns holds
     * {@code true} or {@code false}, read as TOP and NONE; a missing or unreadable value is the
     * default, NONE.
     *
     * @return never null
     */
    static DialSpinPattern getGateDialSpinPattern()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.GATE_DIAL_SPIN);
        final DialSpinPattern pattern = (s == null) ? null
            : DialSpinPattern.parse(String.valueOf(s.getValue()));
        return (pattern == null) ? DialSpinPattern.NONE : pattern;
    }

    /**
     * The ring pattern a gate dials with (#366): its own, then its material group's, then
     * {@code gate-dial-spin}.
     *
     * @param own
     *            the gate's own pattern, or null
     * @param group
     *            its material group, or null
     * @return never null
     */
    static DialSpinPattern getGateDialSpinPattern(
        final DialSpinPattern own,
        final MaterialGroup group)
    {
        if (own != null)
        {
            return own;
        }
        if ((group != null) && (group.getDialSpin() != null))
        {
            return group.getDialSpin();
        }
        return getGateDialSpinPattern();
    }

    /**
     * Whether gates make any noise at all.
     *
     * @return true if gate sounds should play
     */
    static boolean isGateSoundsEnabled()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.GATE_SOUNDS_ENABLED);
        return (s == null) || s.getBooleanValue();
    }

    /**
     * How loud gate sounds are.
     *
     * <p>Louder than rings by default, and deliberately: a gate is a landmark somebody walks
     * towards, where a ring is something you are standing on.
     *
     * @return 1.5 when unset; past 1.0 it widens the range heard rather than the loudness
     */
    static float getGateSoundVolume()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.GATE_SOUND_VOLUME);
        return (s == null) ? 1.5f : (float) s.getDoubleValue();
    }

    /**
     * The sound a gate makes as it begins to dial.
     *
     * @return the sound name, or empty for silence
     */
    static String getGateSoundActivate()
    {
        return ConfigManager.soundSetting(ConfigKeys.GATE_SOUND_ACTIVATE, "block.conduit.activate");
    }

    /**
     * The sound each chevron makes as it locks.
     *
     * @return the sound name, or empty for silence
     */
    static String getGateSoundChevron()
    {
        return ConfigManager.soundSetting(ConfigKeys.GATE_SOUND_CHEVRON, "block.iron_trapdoor.close");
    }

    /**
     * The sound the last chevron makes as it locks, with its own chevron sound.
     *
     * @return the sound name, or empty for silence
     */
    static String getGateSoundLock()
    {
        return ConfigManager.soundSetting(ConfigKeys.GATE_SOUND_LOCK, "block.beacon.power_select");
    }

    /**
     * The sound of a wormhole establishing.
     *
     * @return the sound name, or empty for silence
     */
    static String getGateSoundKawoosh()
    {
        return ConfigManager.soundSetting(ConfigKeys.GATE_SOUND_KAWOOSH, "entity.player.splash.high_speed");
    }

    /**
     * The sound of a wormhole closing.
     *
     * @return the sound name, or empty for silence
     */
    static String getGateSoundClose()
    {
        return ConfigManager.soundSetting(ConfigKeys.GATE_SOUND_CLOSE, "block.conduit.deactivate");
    }

    /**
     * The sound the iris makes as it closes over a gate.
     *
     * @return the sound name, or empty for silence
     */
    static String getGateSoundIrisClose()
    {
        return ConfigManager.soundSetting(ConfigKeys.GATE_SOUND_IRIS_CLOSE, "block.iron_door.close");
    }

    /**
     * The sound the iris makes as it opens.
     *
     * @return the sound name, or empty for silence
     */
    static String getGateSoundIrisOpen()
    {
        return ConfigManager.soundSetting(ConfigKeys.GATE_SOUND_IRIS_OPEN, "block.iron_door.open");
    }

    /**
     * The sound an open wormhole makes while it stands there.
     *
     * <p>Running water, as in the show. An event horizon looks like water and behaves like
     * it, so it is the one ambience that needs no explaining.
     *
     * @return the sound name, or empty for silence
     */
    static String getGateSoundAmbient()
    {
        return ConfigManager.soundSetting(ConfigKeys.GATE_SOUND_AMBIENT, "ambient.underwater.loop");
    }

    /** Returns true if an open gate's horizon ripples (#579); off when the setting is missing, as it ships. */
    static boolean isGateRipple()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.GATE_RIPPLE);
        return (s != null) && s.getBooleanValue();
    }

    /**
     * @param animation
     *            an iris animation, as {@link #getGateIrisAnimation(String, com.wormhole_xtreme.wormhole.model.MaterialGroup)} answers
     * @return false only for {@code instant}
     */
    static boolean isIrisAnimated(final String animation)
    {
        return !"instant".equalsIgnoreCase(animation);
    }

    /**
     * The iris animation a gate uses (#427): its own, then its material group's, then
     * {@code gate-iris-animation}.
     *
     * @param own
     *            the gate's own animation, or null
     * @param group
     *            its material group, or null
     * @return one of {@link ConfigManager#irisAnimations()}
     */
    static String getGateIrisAnimation(final String own, final MaterialGroup group)
    {
        if (own != null)
        {
            return own;
        }
        if ((group != null) && (group.getIrisAnimation() != null))
        {
            return group.getIrisAnimation();
        }
        return gateIrisAnimation().toLowerCase(Locale.ROOT);
    }

    /**
     * What an open gate shows once its kawoosh settles (#516).
     *
     * @return horizon, behind or open; horizon for anything else
     */
    static String getGateView()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.GATE_VIEW);
        final String level = (s == null) ? null : ConfigManager.parseGateView(String.valueOf(s.getStringValue()));
        return (level == null) ? ConfigManager.GATE_VIEWS.get(0) : level;
    }

    /**
     * How far past a gate's opening its view is captured and drawn first (#516): 32, or
     * {@code view-depth} if that is shallower.
     *
     * <p>Shallower than the view because a gate's capture is taken when it is dialled, often of
     * somewhere nobody has loaded, and at 160 that was a box of some 230 chunks read from disk
     * before anything showed. The rest, out to {@code view-depth}, is filled in behind it.
     *
     * @return blocks, 4 to 32
     */
    static int getGateViewFirstDepth()
    {
        return Math.min(ConfigManager.GATE_FIRST_STEP_DEPTH, ConfigManager.getViewDepth());
    }

    /**
     * Whether a big gate's view of a smaller far gate is drawn at the end of a tunnel (#583); on when
     * the setting is missing, as it ships.
     *
     * @return true for the tunnel, false for the plain view
     */
    static boolean isGateViewFunnel()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.GATE_VIEW_FUNNEL);
        return (s == null) || s.getBooleanValue();
    }

    /**
     * The longest a big gate's tunnel to a smaller far gate may be, in blocks (#583).
     *
     * @return blocks, 1 to 64
     */
    static int getGateViewFunnelLength()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.GATE_VIEW_FUNNEL_LENGTH);
        final int blocks = (s == null) ? 16 : s.getIntValue();
        return Math.max(1, Math.min(64, blocks));
    }

    /**
     * The shortest a gate's tunnel is, in blocks (#587): above 0, a far gate as big or bigger is seen down
     * a straight tube that long.
     *
     * @return blocks, 0 to {@link #getGateViewFunnelLength()}
     */
    static int getGateViewFunnelMinLength()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.GATE_VIEW_FUNNEL_MIN_LENGTH);
        final int blocks = (s == null) ? 0 : s.getIntValue();
        return Math.max(0, Math.min(getGateViewFunnelLength(), blocks));
    }

    /**
     * How long a big gate's tunnel may be now: its length, or none with the tunnel off.
     *
     * @return blocks; 0 for the plain view
     */
    static int getGateViewFunnelLongest()
    {
        return isGateViewFunnel() ? getGateViewFunnelLength() : 0;
    }

    /**
     * How long a gate build preview lasts once its owner stops using build commands.
     *
     * @return minutes, at least 1
     */
    static int getGatePreviewMinutes()
    {
        return Math.max(1, ConfigManager.intSetting(ConfigKeys.GATE_PREVIEW_MINUTES, 10));
    }

    /**
     * The most blocks all gate build previews on the server may show at once.
     *
     * @return the block count, 0 to allow no previews
     */
    static int getGatePreviewMaxBlocks()
    {
        return Math.max(0, ConfigManager.intSetting(ConfigKeys.GATE_PREVIEW_MAX_BLOCKS, 5000));
    }
}
