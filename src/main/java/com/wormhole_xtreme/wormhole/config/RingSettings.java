package com.wormhole_xtreme.wormhole.config;

import java.util.Locale;

import org.bukkit.Material;

import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.model.ring.Ring;
import com.wormhole_xtreme.wormhole.model.ring.RingAccess;
import com.wormhole_xtreme.wormhole.model.ring.RingStyle;

/** The ring timings, limits, defaults and sounds; {@link ConfigManager} delegates here. */
final class RingSettings
{
    /** Static helpers only. */
    private RingSettings()
    {
    }

    /** A shipped ring duration at {@code ring-animation-speed}. */
    private static int ringTicks(final int ticks)
    {
        return ConfigManager.atSpeed(ticks, getRingAnimationSpeed());
    }

    /**
     * How long a ring pair counts down before committing.
     *
     * <p>Floored rather than taken as written. The abort window only means anything because
     * the countdown outlasts the time it takes to walk clear of a ring, and a ring is seven
     * or eight blocks across — from the middle that is around four blocks to cover, close to
     * a second at walking pace. Set much below that and rings start taking people who were
     * only passing through.
     *
     * @return countdown in ticks, 100 at the shipped speed and never under 30
     */
    static int getRingCountdownTicks()
    {
        return Math.max(30, ringTicks(100));
    }

    /**
     * How long a pair refuses to fire after a cycle.
     *
     * @return cooldown in ticks
     */
    static int getRingCooldownTicks()
    {
        return Math.max(0, ConfigManager.intSetting(ConfigKeys.RING_COOLDOWN_TICKS, 600));
    }

    /**
     * Ticks between animation frames.
     *
     * @return frame interval in ticks, 2 at the shipped speed and at least 1
     */
    static int getRingDeployTicks()
    {
        return Math.max(1, ringTicks(2));
    }

    /**
     * How long the finished stack stands still before anybody is moved.
     *
     * <p>The rings arrive, stand a beat, and only then is anyone taken. Swapping the instant
     * the last ring stops reads as the animation being interrupted by the teleport rather
     * than completing into it.
     *
     * @return settle pause in ticks
     */
    static int getRingSettleTicks()
    {
        return ringTicks(20);
    }

    /**
     * How long each ring stays lit as the flash runs through the stack.
     *
     * @return per-ring flash time in ticks
     */
    static int getRingFlashTicks()
    {
        return Math.max(1, ringTicks(3));
    }

    /**
     * How long the pad stays lit after the last ring has gone home.
     *
     * <p>The lights are lit for the whole cycle and outlive it by this much. Putting them out
     * at the same instant the last ring sinks away reads as the whole thing being switched
     * off rather than as the rings finishing.
     *
     * @return linger time in ticks
     */
    static int getRingLightsLingerTicks()
    {
        return ringTicks(20);
    }

    /**
     * Whether a ring shows its outline to somebody it has turned away.
     *
     * <p>An idle ring is invisible, so a player told it is recharging is standing on ground
     * that looks like any other. Lighting the pattern for a moment says where it is and how
     * much of it they are in.
     *
     * @return true if refusals should light the pattern
     */
    static boolean isRingOutlineOnRefusal()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.RING_OUTLINE_ON_REFUSAL);
        return (s == null) || s.getBooleanValue();
    }

    /**
     * How long a refused player is shown the ring's outline.
     *
     * @return outline time in ticks
     */
    static int getRingOutlineTicks()
    {
        return ringTicks(40);
    }

    /**
     * How long the stack stands still once the light has finished.
     *
     * <p>After the arrival sweep and before the rings come home: a beat with the travellers
     * standing there and the rings still up around them.
     *
     * @return hold in ticks
     */
    static int getRingHoldTicks()
    {
        return ringTicks(20);
    }

    /**
     * How far below its plane a ceiling ring will look for the floor.
     *
     * <p>A ceiling ring drops its rings all the way down and they stack up from the floor, so
     * it needs a floor near enough to reach. Ten blocks covers any room somebody would
     * actually stand in; past that the ring is over a shaft rather than a room, and rings
     * that fall out of sight are not a transport.
     *
     * @return the limit in blocks
     */
    static int getRingMaxCeilingDrop()
    {
        return Math.max(Ring.MIN_CEILING_DROP,
            ConfigManager.intSetting(ConfigKeys.RING_MAX_CEILING_DROP, 10));
    }

    /**
     * How deep a ring's passenger volume runs.
     *
     * <p>Matters most for ceiling rings, where the floor people stand on may be several
     * blocks below the ring itself. At least two, so a player's feet and head both count.
     *
     * @return reach in block layers, at least 2
     */
    static int getRingReach()
    {
        return Math.max(2, ConfigManager.intSetting(ConfigKeys.RING_REACH, 4));
    }

    /**
     * Required distance between ring anchors.
     *
     * @return separation in blocks
     */
    static int getRingMinSeparation()
    {
        return Math.max(0, ConfigManager.intSetting(ConfigKeys.RING_MIN_SEPARATION, 8));
    }

    /**
     * Furthest apart the two ends of a pair may be on the ground.
     *
     * <p>Not there for any technical reason: distance costs nothing, and a teleport across
     * twenty thousand blocks is the same work as one across twenty. It is there to keep rings
     * from becoming the answer to everything. A gate is the plugin's long-haul option — it
     * takes a real structure to build, it can be dialled anywhere, and it is meant to be the
     * thing that connects distant places. Rings are the short hop at either end of that.
     *
     * <p>256 blocks is sixteen chunks: comfortably the whole of one base or settlement, and
     * nowhere near town-to-town. Set it to zero to lift the limit entirely.
     *
     * @return the limit in blocks, or zero for none
     */
    static int getRingMaxLinkDistance()
    {
        return Math.max(0, ConfigManager.intSetting(ConfigKeys.RING_MAX_LINK_DISTANCE, 256));
    }

    /**
     * How many pairs one player may own.
     *
     * @return the quota, or zero for no limit
     */
    static int getRingMaxPairsPerPlayer()
    {
        return Math.max(0, ConfigManager.intSetting(ConfigKeys.RING_MAX_PAIRS_PER_PLAYER, 10));
    }

    /**
     * What a newly built pair starts as.
     *
     * <p>Private unless a server says otherwise, and private again if the value is not one
     * this understands. Rings are personal links rather than public infrastructure, and a
     * typo here should not publish every ring somebody builds afterwards.
     *
     * @return the starting access mode
     */
    static RingAccess getRingDefaultAccess()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.RING_DEFAULT_ACCESS);
        try
        {
            return RingAccess.valueOf(
                String.valueOf(s == null ? "PRIVATE" : s.getStringValue()).toUpperCase(Locale.ROOT));
        }
        catch (final RuntimeException e)
        {
            return RingAccess.PRIVATE;
        }
    }

    /**
     * How a ring stack deploys.
     *
     * @return the starting animation style
     */
    static RingStyle getRingDefaultStyle()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.RING_DEFAULT_STYLE);
        try
        {
            return RingStyle.valueOf(
                String.valueOf(s == null ? "CONCURRENT" : s.getStringValue()).toUpperCase(Locale.ROOT));
        }
        catch (final RuntimeException e)
        {
            return RingStyle.CONCURRENT;
        }
    }

    /**
     * What the countdown lights are made of.
     *
     * @return the configured material, or redstone lamp when it is missing or unknown
     */
    static Material getRingDefaultLight()
    {
        return ConfigManager.materialSetting(ConfigKeys.RING_DEFAULT_LIGHT, Material.REDSTONE_LAMP);
    }

    /**
     * What a ring turns to as the transport light passes through it.
     *
     * <p>Glowstone against the lamp-lit pad, so the transport reads as its own moment.
     *
     * @return the configured material, or glowstone when it is missing or unknown
     */
    static Material getRingDefaultFlash()
    {
        return ConfigManager.materialSetting(ConfigKeys.RING_DEFAULT_FLASH, Material.GLOWSTONE);
    }

    /**
     * How fast ring animations run against their shipped timings (#589).
     *
     * @return 1.0 for the shipped timings, from {@link ConfigManager#SLOWEST_SPEED} to
     *         {@link ConfigManager#FASTEST_SPEED}
     */
    static double getRingAnimationSpeed()
    {
        return ConfigManager.speedSetting(ConfigKeys.RING_ANIMATION_SPEED);
    }

    /**
     * Whether rings make any noise at all.
     *
     * @return true if ring sounds should play
     */
    static boolean isRingSoundsEnabled()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.RING_SOUNDS_ENABLED);
        return (s == null) || s.getBooleanValue();
    }

    /**
     * How loud ring sounds are.
     *
     * <p>Bukkit scales audible range with volume, so this is a distance knob as much as a
     * loudness one: at 1.0 a ring is heard about sixteen blocks away.
     *
     * @return 1.0 when unset
     */
    static float getRingSoundVolume()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.RING_SOUND_VOLUME);
        return (s == null) ? 1.0f : (float) s.getDoubleValue();
    }

    /**
     * The sound a ring makes as its pad opens.
     *
     * @return the sound name, or empty for silence
     */
    static String getRingSoundOpen()
    {
        return ConfigManager.soundSetting(ConfigKeys.RING_SOUND_OPEN, "block.beacon.activate");
    }

    /**
     * The sound each ring makes as it leaves the pad or returns to it.
     *
     * @return the sound name, or empty for silence
     */
    static String getRingSoundRing()
    {
        return ConfigManager.soundSetting(ConfigKeys.RING_SOUND_RING, "block.piston.extend");
    }

    /**
     * The sound of the transport itself.
     *
     * @return the sound name, or empty for silence
     */
    static String getRingSoundFlash()
    {
        return ConfigManager.soundSetting(ConfigKeys.RING_SOUND_FLASH, "block.beacon.power_select");
    }

    /**
     * The sound a ring makes as its pad closes.
     *
     * @return the sound name, or empty for silence
     */
    static String getRingSoundClose()
    {
        return ConfigManager.soundSetting(ConfigKeys.RING_SOUND_CLOSE, "block.beacon.deactivate");
    }

    /**
     * The sound a ring makes when it turns somebody away.
     *
     * @return the sound name, or empty for silence
     */
    static String getRingSoundRefused()
    {
        return ConfigManager.soundSetting(ConfigKeys.RING_SOUND_REFUSED, "block.note_block.bass");
    }
}
