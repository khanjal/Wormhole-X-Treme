package com.wormhole_xtreme.wormhole.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Stream;

import org.bukkit.Material;

import com.wormhole_xtreme.wormhole.RepeatingSweeps;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.integration.RegionFlags;
import com.wormhole_xtreme.wormhole.logic.DialSpinPattern;
import com.wormhole_xtreme.wormhole.model.IrisSweep;
import com.wormhole_xtreme.wormhole.model.MaterialGroup;
import com.wormhole_xtreme.wormhole.model.StargateShapeRegistry;
import com.wormhole_xtreme.wormhole.model.ring.Ring;
import com.wormhole_xtreme.wormhole.model.ring.RingAccess;
import com.wormhole_xtreme.wormhole.model.ring.RingManager;
import com.wormhole_xtreme.wormhole.model.ring.RingStyle;
import com.wormhole_xtreme.wormhole.plugin.EconomySupport;
import com.wormhole_xtreme.wormhole.plugin.MetricsSupport;
import com.wormhole_xtreme.wormhole.plugin.PermissionsSupport;
import com.wormhole_xtreme.wormhole.plugin.PlaceholderSupport;
import com.wormhole_xtreme.wormhole.plugin.map.MapMarkers;


/**
 * The Class ConfigManager.
 */
public class ConfigManager
{
    /** The section every setting is filed under. */
    private static final String SECTION = "WormholeXTreme";

    /** Plugin folder name, remembered so config.yml can be located again after load. */
    private static volatile String configuredPluginName = SECTION;


    /**
     * The Enum ConfigKeys.
     */
    public enum ConfigKeys
    {

        /** The PERMISSION SUPPORT DISABLE. */
        PERMISSIONS_SUPPORT_DISABLE,
        /** Automatically fall back to simple permission mode when no Vault provider is detected. */
        PERMISSIONS_AUTO_FALLBACK,
        /** Whether wormhole.use is required to travel, not merely to dial. */
        WORMHOLE_USE_IS_TELEPORT,

        /** Seconds a gate stays activated, awaiting a destination, before it times out. */
        TIMEOUT_ACTIVATE,

        /** Seconds a dialled gate stays open before it shuts itself down. */
        TIMEOUT_SHUTDOWN,
        /** Hard ceiling on how long a wormhole may stay open, however often it is re-dialled. */
        MAX_OPEN_SECONDS,

        /** Whether walking through a gate starts a per-player cooldown. */
        USE_COOLDOWN_ENABLED,

        /** Seconds a player waits between gate trips, when the cooldown above is enabled. */
        USE_COOLDOWN_SECONDS,

        /** Restrict teleportation to same-world gates only. */
        SAME_WORLD_ONLY,

        /** Whether a player's following pets travel with them by gate, ring, beam or mirror. */
        PETS_FOLLOW_OWNER,

        /** The LOG LEVEL. */
        LOG_LEVEL,
        /** Tick interval for periodic non-player entity gate scan. */
        ENTITY_SCAN_INTERVAL_TICKS,

        /** Ticks a ring pair counts down before it commits and the rings start rising. */
        RING_COUNTDOWN_TICKS,
        /** Ticks a ring pair refuses to fire again after a cycle. */
        RING_COOLDOWN_TICKS,
        /** Ticks between frames of the ring deploy and retract animations. */
        RING_DEPLOY_TICKS,
        /** Ticks the fully deployed stack stands still before the swap fires. */
        RING_SETTLE_TICKS,
        /** Ticks each ring stays lit as the transport flash runs through the stack. */
        RING_FLASH_TICKS,
        /** Whether a ring briefly shows its outline to somebody it has turned away. */
        RING_OUTLINE_ON_REFUSAL,
        /** How long that outline stays up, in ticks. */
        RING_OUTLINE_TICKS,
        /** Ticks the pad stays lit after the last ring has sunk back into it. */
        RING_LIGHTS_LINGER_TICKS,
        /** Ticks the ring stack stands still after the swap, before retracting. */
        RING_HOLD_TICKS,
        /** Block layers of passenger volume, measured from the ring plane into the room. */
        RING_REACH,
        /** Required distance between ring anchors, in blocks. */
        RING_MIN_SEPARATION,
        /** Furthest two ends of a pair may be apart on the ground. Zero means no limit. */
        RING_MAX_LINK_DISTANCE,
        /** Furthest two ends of a pair may be apart in height. Zero means no limit. */
        RING_MAX_LINK_HEIGHT,
        /** Furthest below its plane a ceiling ring will look for the floor. */
        RING_MAX_CEILING_DROP,
        /** How many ring pairs one player may own. Zero means no limit. */
        RING_MAX_PAIRS_PER_PLAYER,
        /** What a newly built ring pair starts as: PUBLIC or PRIVATE. */
        RING_DEFAULT_ACCESS,
        /** How a ring stack deploys: CONCURRENT or SEQUENTIAL. */
        RING_DEFAULT_STYLE,
        /** Fallback ring material, used only when the template cannot say. */
        RING_DEFAULT_MATERIAL,
        /** What the countdown lights are made of. */
        RING_DEFAULT_LIGHT,
        /** What a ring turns to as the transport light passes through it. */
        RING_DEFAULT_FLASH,
        GATE_SOUNDS_ENABLED,
        GATE_SOUND_VOLUME,
        GATE_SOUND_ACTIVATE,
        GATE_SOUND_CHEVRON,
        GATE_SOUND_LOCK,
        GATE_SOUND_KAWOOSH,
        GATE_SOUND_CLOSE,
        GATE_SOUND_IRIS_CLOSE,
        GATE_SOUND_IRIS_OPEN,

        /** Whether an iris sweeps shut a ring at a time, or arrives all at once. */
        GATE_IRIS_ANIMATION,

        /** What an open gate shows: its horizon, the far side behind it, or the far side alone (#516). */
        GATE_VIEW,

        /** How far past a gate's opening its view is captured and drawn, first. */
        GATE_VIEW_DEPTH,

        /** How far a gate's view is filled in, behind its first step. */
        GATE_VIEW_FULL_DEPTH,

        /** Ticks between one ring of an iris sweep and the next. */
        GATE_IRIS_STEP_TICKS,

        /** Ticks between frames of the wormhole drawn behind a see-through iris. */
        GATE_IRIS_HORIZON_TICKS,

        /** The longest a whole iris crossing may take, whatever the gate's size. */
        GATE_IRIS_SWEEP_MAX_TICKS,
        GATE_SOUND_AMBIENT,
        GATE_SOUND_AMBIENT_TICKS,
        GATE_ARRIVAL_SPLASH_TICKS,
        /** Whether a player arriving through a gate is told in chat which gate they arrived at (#485). */
        SHOW_GATE_WELCOME_MESSAGE,
        GATE_DIAL_SPIN,
        /** Minutes a gate build preview lasts after its owner last used a build command. */
        GATE_PREVIEW_MINUTES,
        /** Most blocks every gate build preview on the server may show between them. */
        GATE_PREVIEW_MAX_BLOCKS,
        RING_SOUNDS_ENABLED,
        RING_SOUND_VOLUME,
        RING_SOUND_OPEN,
        RING_SOUND_RING,
        RING_SOUND_FLASH,
        RING_SOUND_CLOSE,
        RING_SOUND_REFUSED,
        /** Whether to append newly-seen shape palettes to config.yml automatically. */
        GATE_MATERIAL_GROUPS_AUTODISCOVER,
        /** Whether the PlaceholderAPI expansion is registered. */
        PLACEHOLDERS_ENABLED,
        /** Whether gate and ring construction is logged to CoreProtect (#238). */
        COREPROTECT_ENABLED,
        /** Whether the WorldGuard region flags wormhole-build and wormhole-use are registered (#240). */
        WORLDGUARD_ENABLED,
        /** Whether gates, rings, public beam destinations and mirrors are drawn on Dynmap (#236). */
        DYNMAP_ENABLED,
        /** Whether gates, rings, public beam destinations and mirrors are drawn on BlueMap. */
        BLUEMAP_ENABLED,
        /** Whether gates, rings, public beam destinations and mirrors are drawn on squaremap. */
        SQUAREMAP_ENABLED,
        /** Whether gates, rings, public beam destinations and mirrors are drawn on Pl3xMap. */
        PL3XMAP_ENABLED,
        /** Whether gates and the lines between dialled pairs are a web map layer. */
        MAP_SHOW_GATES,
        /** Whether transport rings are a web map layer. */
        MAP_SHOW_RINGS,
        /** Whether public beam destinations are a web map layer. */
        MAP_SHOW_BEAMS,
        /** Whether quantum mirrors are a web map layer. */
        MAP_SHOW_MIRRORS,
        /** Whether gates with an iris code are drawn on the web map. */
        MAP_SHOW_IRIS_GATES,
        /** Whether anonymous usage counts are sent to bStats (#239). */
        METRICS_ENABLED,
        /** Whether startup looks for a newer release (#461). */
        UPDATE_CHECK,
        /** Whether economy (Vault) integration is enabled. */
        ECONOMY_ENABLED,
        /** Cost in currency units charged to use (walk through) a gate. 0 = free. */
        ECONOMY_USE_COST,
        /** Cost in currency units charged to build a gate. 0 = free. */
        ECONOMY_BUILD_COST,
        REDSTONE_EXTEND_OPEN_TIME,
        SIGN_GLOWING_TEXT,
        SIGN_DIAL_MATCH_MATERIAL,
        SIGN_COLOR_GATE_NAME,
        SIGN_COLOR_NETWORK,
        SIGN_COLOR_OWNER,
        SIGN_COLOR_SELECTED,
        SIGN_COLOR_NEIGHBOUR,
        BEAM_SOUNDS_ENABLED,
        BEAM_SOUND_VOLUME,
        BEAM_SOUND_CHARGE,
        BEAM_SOUND_DEPART,
        BEAM_SOUND_ARRIVE,
        /** How long the glow gathers at body height before opening into the departure column. */
        BEAM_ENVELOP_TICKS,
        /** How far into the envelope the traveller vanishes -- clamped inside it. */
        BEAM_VANISH_AT_STEP,
        /** How long the column rises and departs, once the envelope opens into it. */
        BEAM_RISE_TICKS,
        /** How far into the rise the real teleport fires -- clamped inside the rise. */
        BEAM_TELEPORT_AT_STEP,
        /** How long the column takes to descend into place at the destination. */
        BEAM_DESCEND_TICKS,
        /** How long the column takes to fade out once it has deposited the traveller. */
        BEAM_FADE_TICKS,
        /** Whether beam travel has a per-player cooldown at all. */
        BEAM_USE_COOLDOWN_ENABLED,
        /** Seconds a player must wait between beams, when the above is true. */
        BEAM_USE_COOLDOWN_SECONDS,
        /** Cost in currency units charged to beam. 0 = free. */
        BEAM_ECONOMY_USE_COST,

        /**
         * How many mirrors one world may hold, or 0 for no limit.
         *
         * <p>One by default: a mirror is the door into its world, and scrolling one mirror
         * through every other is only a short list while each world has one.
         */
        MIRROR_PER_WORLD_LIMIT,

        /**
         * How close a player must be for a mirror's banner to give way to its room, in blocks.
         *
         * <p>Also how far they may drift before a mirror they turned on goes off, and how far
         * out its wall is read before it can be drawn whole. Compared squared where it is a
         * distance check, so it never costs a square root. Was {@code mirror-proximity-radius}.
         */
        MIRROR_PROXIMITY_DISTANCE,

        /**
         * How often the proximity sweep runs, in ticks.
         *
         * <p>Twenty is once a second, which is fast enough that walking up to a mirror feels
         * immediate and slow enough that the sweep is not worth optimising further. The sweep
         * skips mirrors in unloaded worlds and unloaded chunks before touching anything.
         */
        MIRROR_PROXIMITY_TICKS,

        /**
         * How far from a mirror's opening its far side is drawn as real blocks, in blocks.
         *
         * <p>Its render distance: past it nothing is drawn, and a capture reaches this far and
         * no further. 160 by default, ten chunks, so the room ends where the client stops
         * showing anything; a room is its surfaces, so depth costs little.
         */
        MIRROR_VIEW_DEPTH,

        /**
         * Whether a viewer's own fog is pulled in to where a mirror's room ends.
         *
         * <p>Paper only, and off by default. The room ending is not the world ending: the client
         * draws this world past it unless it is told not to have those chunks. A radius round the
         * player rather than a direction, so it pulls the fog in everywhere, which is why it is
         * something to turn on for a shallow depth rather than the way mirrors work.
         */
        MIRROR_FOG_AT_DEPTH,

        MIRROR_APPROACH_MESSAGE
    }

    /**
     * The Enum StringTypes.
     */
    public enum MessageStrings
    {

        /** The error header. */
        ERROR_HEADER("\u00A73:: \u00A75error \u00A73:: \u00A77"),

        /** The normal header. */
        NORMAL_HEADER("\u00A73:: \u00A77"),

        /** The permission no. */
        PERMISSION_NO(ERROR_HEADER + "You lack the permissions to do this."),

        /** The target is self. */
        TARGET_IS_SELF(ERROR_HEADER + "Can't dial own gate without solar flare"),

        /** The target invalid. */
        TARGET_INVALID(ERROR_HEADER + "Invalid gate target."),

        /** The target is active. */
        TARGET_IS_ACTIVE(ERROR_HEADER + "Target gate is currently active."),

        /** The far gate is in another world, and same-world-only is on. */
        CROSS_WORLD_DISABLED(ERROR_HEADER + "Cross-world travel is disabled on this server."),

        /** The gate not active. */
        GATE_NOT_ACTIVE(ERROR_HEADER + "No gate activated to dial."),

        /** The gate remove active. */
        GATE_REMOVE_ACTIVE(ERROR_HEADER + "Gate remotely activated."),

        /** The gate shutdown. */
        GATE_SHUTDOWN(NORMAL_HEADER + "Gate successfully shutdown."),

        /** The gate activated. */
        GATE_ACTIVATED(NORMAL_HEADER + "Gate successfully activated."),

        /** The gate deactivated. */
        GATE_DEACTIVATED(NORMAL_HEADER + "Gate successfully deactivated."),

        /** The gate dialed. */
        GATE_CONNECTED(NORMAL_HEADER + "Stargates connected."),

        /** Said to a player arriving through a gate, before the destination gate's name. */
        GATE_ARRIVED(NORMAL_HEADER + "Arrived at "),

        /** The construct success. */
        CONSTRUCT_SUCCESS(NORMAL_HEADER + "Gate successfully constructed."),

        /** The construct name invalid. */
        CONSTRUCT_NAME_INVALID(ERROR_HEADER + "Gate name invalid: "),

        /** The construct name too long. */
        CONSTRUCT_NAME_TOO_LONG(ERROR_HEADER + "Gate name too long: "),

        /** The construct name taken. */
        CONSTRUCT_NAME_TAKEN(ERROR_HEADER + "Gate name already taken: "),

        /** The request invalid. */
        REQUEST_INVALID(ERROR_HEADER + "Invalid Request"),

        /** The gate not specified. */
        GATE_NOT_SPECIFIED(ERROR_HEADER + "No gate name specified."),

        /** The player use cooldown restricted. */
        PLAYER_USE_COOLDOWN_RESTRICTED(ERROR_HEADER + "You must wait longer before using a stargate."),

        /** The player use cooldown wait time. */
        PLAYER_USE_COOLDOWN_WAIT_TIME(ERROR_HEADER + "Current Wait (in seconds): "),

        /** Player recently arrived at gate. */
        PLAYER_RECENT_ARRIVAL(ERROR_HEADER + "You can't enter an incoming wormhole"),

        /** Insufficient funds message. */
        ECONOMY_INSUFFICIENT_FUNDS(ERROR_HEADER + "Insufficient funds to use this gate."),

        /** Charged for gate use message (prefix; amount and currency appended at runtime). */
        ECONOMY_CHARGED(NORMAL_HEADER + "Charged "),

        /** Charged for gate build message (prefix; amount and currency appended at runtime). */
        ECONOMY_BUILD_CHARGED(NORMAL_HEADER + "Gate build cost charged: ");

        /** The m. */
        private final String m;

        /**
         * Instantiates a new string types.
         */
        private MessageStrings(final String message)
        {
            m = message;
        }

        /* (non-Javadoc)
         * @see java.lang.Enum#toString()
         */
        @Override
        public String toString()
        {
            return m;
        }
    }

    /** The Constant configurations. */
    private static final ConcurrentHashMap<ConfigKeys, Setting> configurations = new ConcurrentHashMap<>();

    /**
     * Gets the configurations.
     * 
     * @return the live map rather than a copy
     */
    protected static ConcurrentHashMap<ConfigKeys, Setting> getConfigurations()
    {
        return configurations;
    }

    /**
     * Get Log Level setting from ConfigKeys. Return sane Level value.
     * Return default value if key is missing or broken.
     */
    public static Level getLogLevel()
    {
        Setting ll;
        if ((ll = ConfigManager.getConfigurations().get(ConfigKeys.LOG_LEVEL)) != null)
        {
            return ll.getLevel();
        }
        else
        {
            return Level.INFO;
        }
    }

    /**
     * Gets the Permissions plugin support status.
     * 
     * @return true, if Permissions plugin support is disabled.
     */
    public static boolean getPermissionsSupportDisable()
    {
        Setting psd;
        if ((psd = ConfigManager.getConfigurations().get(ConfigKeys.PERMISSIONS_SUPPORT_DISABLE)) != null)
        {
            return psd.getBooleanValue();
        }
        else
        {
            return false;
        }
    }

    /**
     * Gets whether the plugin should automatically fall back to simple permission mode when no
     * Vault/LuckPerms provider is detected. Default: true.
     */
    public static boolean getPermissionsAutoFallback()
    {
        Setting psd;
        if ((psd = ConfigManager.getConfigurations().get(ConfigKeys.PERMISSIONS_AUTO_FALLBACK)) != null)
        {
            return psd.getBooleanValue();
        }
        else
        {
            return true;
        }
    }

    /**
     * Gets the setting.
     * 
     * @return the setting, or null if the key is not registered
     */
    private static Setting getSetting(final ConfigKeys configKey)
    {
        return getConfigurations().get(configKey);
    }


    /**
     * Get Timeout Activate setting from ConfigKeys.
     * Return default value if key is missing or broken.
     * 
     * @return Timeout in seconds.
     */
    public static int getTimeoutActivate()
    {
        Setting ta;
        if ((ta = ConfigManager.getConfigurations().get(ConfigKeys.TIMEOUT_ACTIVATE)) != null)
        {
            return ta.getIntValue();
        }
        else
        {
            return 30;
        }
    }

    /**
     * The longest a wormhole may stay open, however often it is re-dialled.
     *
     * <p>Dialling restarts the shutdown timer, so anything re-dialling on a schedule — a
     * minecart crossing a detector rail, say — would hold a gate open forever and lock
     * everyone else out. This is measured from when the wormhole first formed and is not
     * reset by re-dialling. 0 disables the ceiling.
     *
     * @return the maximum open time in seconds
     */
    public static int getMaxOpenSeconds()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.MAX_OPEN_SECONDS);
        return (s != null) ? s.getIntValue() : 300;
    }

    /**
     * Get Timeout Shutdown setting from ConfigKeys.
     * Return default value if key is missing or broken.
     * 
     * @return Timeout in seconds.
     */
    public static int getTimeoutShutdown()
    {
        Setting ts;
        if ((ts = ConfigManager.getConfigurations().get(ConfigKeys.TIMEOUT_SHUTDOWN)) != null)
        {
            return ts.getIntValue();
        }
        else
        {
            return 38;
        }
    }

    /**
     * Tick interval for periodic non-player entity scan.
     * A higher value reduces server load at the cost of slightly delayed teleport detection.
     *
     * @return scan interval in ticks (minimum 5)
     */
    public static int getEntityScanIntervalTicks()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.ENTITY_SCAN_INTERVAL_TICKS);
        final int configured = (s != null) ? s.getIntValue() : 20;
        return Math.max(5, configured);
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
     * @return countdown in ticks, at least 30
     */
    public static int getRingCountdownTicks()
    {
        return Math.max(30, intSetting(ConfigKeys.RING_COUNTDOWN_TICKS, 100));
    }

    /**
     * How long a pair refuses to fire after a cycle.
     *
     * @return cooldown in ticks
     */
    public static int getRingCooldownTicks()
    {
        return Math.max(0, intSetting(ConfigKeys.RING_COOLDOWN_TICKS, 600));
    }

    /**
     * Ticks between animation frames.
     *
     * @return frame interval in ticks, at least 1
     */
    public static int getRingDeployTicks()
    {
        return Math.max(1, intSetting(ConfigKeys.RING_DEPLOY_TICKS, 2));
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
    public static int getRingSettleTicks()
    {
        return Math.max(0, intSetting(ConfigKeys.RING_SETTLE_TICKS, 20));
    }

    /**
     * How long each ring stays lit as the flash runs through the stack.
     *
     * @return per-ring flash time in ticks
     */
    public static int getRingFlashTicks()
    {
        return Math.max(1, intSetting(ConfigKeys.RING_FLASH_TICKS, 3));
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
    public static int getRingLightsLingerTicks()
    {
        return Math.max(0, intSetting(ConfigKeys.RING_LIGHTS_LINGER_TICKS, 20));
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
    public static boolean isRingOutlineOnRefusal()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.RING_OUTLINE_ON_REFUSAL);
        return (s == null) || s.getBooleanValue();
    }

    /**
     * How long a refused player is shown the ring's outline.
     *
     * @return outline time in ticks
     */
    public static int getRingOutlineTicks()
    {
        return Math.max(1, intSetting(ConfigKeys.RING_OUTLINE_TICKS, 40));
    }

    /**
     * How long the stack stands still once the light has finished.
     *
     * <p>After the arrival sweep and before the rings come home: a beat with the travellers
     * standing there and the rings still up around them.
     *
     * @return hold in ticks
     */
    public static int getRingHoldTicks()
    {
        return Math.max(0, intSetting(ConfigKeys.RING_HOLD_TICKS, 20));
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
    public static int getRingMaxCeilingDrop()
    {
        return Math.max(Ring.MIN_CEILING_DROP,
            intSetting(ConfigKeys.RING_MAX_CEILING_DROP, 10));
    }

    /**
     * How deep a ring's passenger volume runs.
     *
     * <p>Matters most for ceiling rings, where the floor people stand on may be several
     * blocks below the ring itself. At least two, so a player's feet and head both count.
     *
     * @return reach in block layers, at least 2
     */
    public static int getRingReach()
    {
        return Math.max(2, intSetting(ConfigKeys.RING_REACH, 4));
    }

    /**
     * Required distance between ring anchors.
     *
     * @return separation in blocks
     */
    public static int getRingMinSeparation()
    {
        return Math.max(0, intSetting(ConfigKeys.RING_MIN_SEPARATION, 8));
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
    public static int getRingMaxLinkDistance()
    {
        return Math.max(0, intSetting(ConfigKeys.RING_MAX_LINK_DISTANCE, 256));
    }

    /**
     * Furthest apart the two ends of a pair may be in height.
     *
     * <p>Measured separately from the ground distance, because the two are different
     * questions. Going straight down is exactly what rings are for — bedrock to the surface,
     * a mine to the hall above it — so the default is the full height of the world, and any
     * vertical link at all is allowed. Sprawling sideways is the thing being discouraged, and
     * that is the other setting.
     *
     * @return the limit in blocks, or zero for none
     */
    public static int getRingMaxLinkHeight()
    {
        return Math.max(0, intSetting(ConfigKeys.RING_MAX_LINK_HEIGHT, 384));
    }

    /**
     * How many pairs one player may own.
     *
     * @return the quota, or zero for no limit
     */
    public static int getRingMaxPairsPerPlayer()
    {
        return Math.max(0, intSetting(ConfigKeys.RING_MAX_PAIRS_PER_PLAYER, 10));
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
    public static RingAccess getRingDefaultAccess()
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
    public static RingStyle getRingDefaultStyle()
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
     * Fallback material for the travelling rings.
     *
     * <p>Normally unused: a ring keeps whatever slab it was laid in. This only answers when a
     * stored ring names a material that is not a slab on this server.
     *
     * @return the fallback slab material, smooth stone when the setting is not a slab
     */
    public static Material getRingDefaultMaterial()
    {
        final Material configured = materialSetting(ConfigKeys.RING_DEFAULT_MATERIAL, Material.SMOOTH_STONE_SLAB);
        return Ring.isUsableAsRing(configured) ? configured : Material.SMOOTH_STONE_SLAB;
    }

    /**
     * What the countdown lights are made of.
     *
     * @return the configured material, or redstone lamp when it is missing or unknown
     */
    public static Material getRingDefaultLight()
    {
        return materialSetting(ConfigKeys.RING_DEFAULT_LIGHT, Material.REDSTONE_LAMP);
    }

    /**
     * What a ring turns to as the transport light passes through it.
     *
     * <p>Glowstone against the lamp-lit pad, so the transport reads as its own moment.
     *
     * @return the configured material, or glowstone when it is missing or unknown
     */
    public static Material getRingDefaultFlash()
    {
        return materialSetting(ConfigKeys.RING_DEFAULT_FLASH, Material.GLOWSTONE);
    }

    /**
     * Reads an int setting, falling back when it is missing.
     *
     * @param key
     *            which setting
     * @param fallback
     *            what to use when it is absent
     */
    private static int intSetting(final ConfigKeys key, final int fallback)
    {
        final Setting s = ConfigManager.getConfigurations().get(key);
        return (s != null) ? s.getIntValue() : fallback;
    }

    /**
     * How a dialling gate's inner ring light moves. A config.yml from before patterns holds
     * {@code true} or {@code false}, read as TOP and NONE; a missing or unreadable value is the
     * default, TOP.
     *
     * @return never null
     */
    public static DialSpinPattern getGateDialSpinPattern()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.GATE_DIAL_SPIN);
        final DialSpinPattern pattern = (s == null) ? null
            : DialSpinPattern.parse(String.valueOf(s.getValue()));
        return (pattern == null) ? DialSpinPattern.TOP : pattern;
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
    public static DialSpinPattern getGateDialSpinPattern(
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
     * The values a setting takes, for tab completion: its choices, or true and false for a switch.
     *
     * @param typed
     *            the setting's name as typed, in either spelling
     * @return the values, empty for free text or a number
     */
    public static List<String> valuesFor(final String typed)
    {
        final ConfigKeys key;
        try
        {
            key = ConfigKeys.valueOf(String.valueOf(typed).replace('-', '_').toUpperCase(Locale.ROOT));
        }
        catch (final IllegalArgumentException notASetting)
        {
            return List.of();
        }
        switch (key)
        {
            case GATE_DIAL_SPIN:
                return Arrays.stream(DialSpinPattern.values())
                    .map(p -> p.name().toLowerCase(Locale.ROOT)).toList();
            case GATE_IRIS_ANIMATION:
                return irisAnimations();
            case GATE_VIEW:
                return GATE_VIEWS;
            case RING_DEFAULT_ACCESS:
                return List.of("public", "private");
            case RING_DEFAULT_STYLE:
                return List.of("concurrent", "sequential");
            case LOG_LEVEL:
                return List.of("SEVERE", "WARNING", "INFO", "CONFIG", "FINE", "FINER", "FINEST", "ALL", "OFF");
            default:
                final Setting s = getConfigurations().get(key);
                return ((s != null) && (s.getValue() instanceof Boolean)) ? List.of("true", "false")
                    : List.of();
        }
    }

    /**
     * Whether gates make any noise at all.
     *
     * @return true if gate sounds should play
     */
    public static boolean isGateSoundsEnabled()
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
    public static float getGateSoundVolume()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.GATE_SOUND_VOLUME);
        return (s == null) ? 1.5f : (float) s.getDoubleValue();
    }

    /**
     * The sound a gate makes as it begins to dial.
     *
     * @return the sound name, or empty for silence
     */
    public static String getGateSoundActivate()
    {
        return soundSetting(ConfigKeys.GATE_SOUND_ACTIVATE, "block.conduit.activate");
    }

    /**
     * The sound each chevron makes as it locks.
     *
     * @return the sound name, or empty for silence
     */
    public static String getGateSoundChevron()
    {
        return soundSetting(ConfigKeys.GATE_SOUND_CHEVRON, "block.iron_trapdoor.close");
    }

    /**
     * The sound the last chevron makes as it locks, with its own chevron sound.
     *
     * @return the sound name, or empty for silence
     */
    public static String getGateSoundLock()
    {
        return soundSetting(ConfigKeys.GATE_SOUND_LOCK, "block.beacon.power_select");
    }

    /**
     * The sound of a wormhole establishing.
     *
     * @return the sound name, or empty for silence
     */
    public static String getGateSoundKawoosh()
    {
        return soundSetting(ConfigKeys.GATE_SOUND_KAWOOSH, "entity.player.splash.high_speed");
    }

    /**
     * The sound of a wormhole closing.
     *
     * @return the sound name, or empty for silence
     */
    public static String getGateSoundClose()
    {
        return soundSetting(ConfigKeys.GATE_SOUND_CLOSE, "block.conduit.deactivate");
    }

    /**
     * The sound the iris makes as it closes over a gate.
     *
     * @return the sound name, or empty for silence
     */
    public static String getGateSoundIrisClose()
    {
        return soundSetting(ConfigKeys.GATE_SOUND_IRIS_CLOSE, "block.iron_door.close");
    }

    /**
     * The sound the iris makes as it opens.
     *
     * @return the sound name, or empty for silence
     */
    public static String getGateSoundIrisOpen()
    {
        return soundSetting(ConfigKeys.GATE_SOUND_IRIS_OPEN, "block.iron_door.open");
    }

    /**
     * The sound an open wormhole makes while it stands there.
     *
     * <p>Running water, as in the show. An event horizon looks like water and behaves like
     * it, so it is the one ambience that needs no explaining.
     *
     * @return the sound name, or empty for silence
     */
    public static String getGateSoundAmbient()
    {
        return soundSetting(ConfigKeys.GATE_SOUND_AMBIENT, "ambient.underwater.loop");
    }

    /**
     * How often the ambient sound repeats.
     *
     * <p>Floored at one tick rather than trusted, because a zero or negative period is not a
     * faster hum -- it is a repeating task with no delay in it.
     *
     * @return the period in ticks
     */
    public static long getGateSoundAmbientTicks()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.GATE_SOUND_AMBIENT_TICKS);
        return (s == null) ? 70L : Math.max(1L, s.getIntValue());
    }

    /**
     * How long a traveller sees water as they come out of a gate.
     *
     * <p>Zero turns it off. This is both how long the water shows and how long the plugin
     * keeps redrawing it, because an arrival hands the client a fresh copy of the chunk and
     * that erases anything drawn into the old one. A trip far enough that the client is still
     * fetching chunks when the window ends will not see it at all; widening this widens the
     * window with it.
     *
     * <p>Still kept short, because the client treats water as physics rather than decoration
     * and will predict swimming for as long as it believes it is submerged.
     *
     * @return the time in ticks, or zero for no splash
     */
    public static long getGateArrivalSplashTicks()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.GATE_ARRIVAL_SPLASH_TICKS);
        return (s == null) ? 20L : Math.max(0L, s.getIntValue());
    }

    /** Returns true if a player arriving through a gate is told its name; off when the setting is missing, as it ships. */
    public static boolean isShowGateWelcomeMessage()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.SHOW_GATE_WELCOME_MESSAGE);
        return (s != null) && s.getBooleanValue();
    }

    /**
     * @param animation
     *            an iris animation, as {@link #getGateIrisAnimation(String, com.wormhole_xtreme.wormhole.model.MaterialGroup)} answers
     * @return false only for {@code instant}
     */
    public static boolean isIrisAnimated(final String animation)
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
     * @return one of {@link #irisAnimations()}
     */
    public static String getGateIrisAnimation(final String own, final MaterialGroup group)
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

    /** @return the iris animations there are: the four sweep styles, then {@code instant} */
    public static List<String> irisAnimations()
    {
        return Stream.concat(
            Arrays.stream(IrisSweep.Style.values())
                .map(style -> style.name().toLowerCase(Locale.ROOT)),
            Stream.of("instant")).toList();
    }

    /**
     * Reads an iris animation by name, whatever its capitals.
     *
     * @param raw
     *            the value as written
     * @return it in lower case, or null if it names none
     */
    public static String parseIrisAnimation(final String raw)
    {
        if (raw == null)
        {
            return null;
        }
        final String name = raw.trim().toLowerCase(Locale.ROOT);
        return irisAnimations().contains(name) ? name : null;
    }

    /** Every level {@code gate-view} takes, the default first. */
    public static final List<String> GATE_VIEWS = List.of("horizon", "behind", "open");

    /**
     * What an open gate shows once its kawoosh settles (#516).
     *
     * @return horizon, behind or open; horizon for anything else
     */
    public static String getGateView()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.GATE_VIEW);
        final String level = (s == null) ? null : parseGateView(String.valueOf(s.getStringValue()));
        return (level == null) ? GATE_VIEWS.get(0) : level;
    }

    /**
     * How far past a gate's opening its view reaches, captured and drawn (#516).
     *
     * <p>Its own rather than {@code mirror-view-depth}: a gate's capture is taken when it is dialled,
     * often of somewhere nobody has loaded, and at a mirror's 160 that was a box of some 230 chunks
     * read from disk before anything showed.
     *
     * @return blocks, 4 to 160
     */
    public static int getGateViewDepth()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.GATE_VIEW_DEPTH);
        return (s == null) ? 32 : Math.max(4, Math.min(160, s.getIntValue()));
    }

    /**
     * How far a gate's view is filled in behind its first step, in the background (#516).
     *
     * @return blocks, 4 to 160; or 0 for no fill, the view staying at {@code gate-view-depth}
     */
    public static int getGateViewFullDepth()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.GATE_VIEW_FULL_DEPTH);
        final int depth = (s == null) ? 160 : s.getIntValue();
        return (depth <= 0) ? 0 : Math.max(4, Math.min(160, depth));
    }

    /**
     * Reads a level of {@code gate-view} as typed.
     *
     * @param raw
     *            the value as typed
     * @return it in lower case, or null if it names none
     */
    public static String parseGateView(final String raw)
    {
        final String level = (raw == null) ? null : raw.trim().toLowerCase(Locale.ROOT);
        return GATE_VIEWS.contains(level) ? level : null;
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
     * Ticks between one ring of an iris sweep and the next.
     *
     * <p>Floored at one: a sweep of zero-tick steps draws every ring in the same tick, which is
     * the instant iris written the long way round and reads as the animation having failed.
     * A ceiling too, because the sweep holds the drawn picture apart from the placed blocks for
     * as long as it runs, and a gate whose iris takes a minute to look shut is worse than one
     * that snaps.
     *
     * @return ticks per ring, between 1 and 20
     */
    public static int getGateIrisStepTicks()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.GATE_IRIS_STEP_TICKS);
        final int configured = (s != null) ? s.getIntValue() : 2;
        return Math.min(20, Math.max(1, configured));
    }

    /**
     * How often the wormhole drawn behind a see-through iris changes frame.
     *
     * <p>Only gates whose iris hides real water draw it in something else -- ice, which does
     * not move the way water does. Alternating two of them is what gives it a surface. Nothing
     * else pays for this: a gate whose iris shows the real wormhole never reaches it.
     *
     * @return ticks between frames, 0 to leave it still
     */
    public static int getGateIrisHorizonTicks()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.GATE_IRIS_HORIZON_TICKS);
        final int configured = (s != null) ? s.getIntValue() : 10;
        return Math.min(100, Math.max(0, configured));
    }

    /**
     * The longest a whole iris crossing may take, however big the gate.
     *
     * <p>{@code gate-iris-step-ticks} is a pace and not a duration, and how many steps there are
     * to pace is the opening's geometry: {@code Standard} has five rings and {@code Grand} has
     * sixty-one, so at the same setting one closes in half a second and the other in six. The
     * pace cannot answer that -- a step is a tick at the very least -- so this is the other end
     * of it, and the steps are merged into bands to keep to it.
     *
     * <p>Zero for no limit, which is what every version before this one did.
     *
     * @return ticks, 0 to 200
     */
    public static int getGateIrisSweepMaxTicks()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.GATE_IRIS_SWEEP_MAX_TICKS);
        final int configured = (s != null) ? s.getIntValue() : 20;
        return Math.min(200, Math.max(0, configured));
    }

    /**
     * The most steps an iris sweep may be drawn in at the configured pace.
     *
     * <p>Floored at two rather than one wherever there is a limit at all: a crossing of one step
     * is the instant iris written the long way round, and a server that asked for a fast sweep
     * asked for a fast sweep rather than for no sweep. That is the same reasoning
     * {@link #getGateIrisStepTicks} floors at one tick for.
     *
     * @return the most steps, or 0 for a step per ring however many that is
     */
    public static int getGateIrisMaxSteps()
    {
        final int ticks = getGateIrisSweepMaxTicks();
        if (ticks <= 0)
        {
            return 0;
        }
        return Math.max(2, ticks / getGateIrisStepTicks());
    }

    /**
     * How long a gate build preview lasts once its owner stops using build commands.
     *
     * @return minutes, at least 1
     */
    public static int getGatePreviewMinutes()
    {
        return Math.max(1, intSetting(ConfigKeys.GATE_PREVIEW_MINUTES, 10));
    }

    /**
     * The most blocks all gate build previews on the server may show at once.
     *
     * @return the block count, 0 to allow no previews
     */
    public static int getGatePreviewMaxBlocks()
    {
        return Math.max(0, intSetting(ConfigKeys.GATE_PREVIEW_MAX_BLOCKS, 5000));
    }

    /**
     * Whether rings make any noise at all.
     *
     * @return true if ring sounds should play
     */
    public static boolean isRingSoundsEnabled()
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
    public static float getRingSoundVolume()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.RING_SOUND_VOLUME);
        return (s == null) ? 1.0f : (float) s.getDoubleValue();
    }

    /**
     * The sound a ring makes as its pad opens.
     *
     * @return the sound name, or empty for silence
     */
    public static String getRingSoundOpen()
    {
        return soundSetting(ConfigKeys.RING_SOUND_OPEN, "block.beacon.activate");
    }

    /**
     * The sound each ring makes as it leaves the pad or returns to it.
     *
     * @return the sound name, or empty for silence
     */
    public static String getRingSoundRing()
    {
        return soundSetting(ConfigKeys.RING_SOUND_RING, "block.piston.extend");
    }

    /**
     * The sound of the transport itself.
     *
     * @return the sound name, or empty for silence
     */
    public static String getRingSoundFlash()
    {
        return soundSetting(ConfigKeys.RING_SOUND_FLASH, "block.beacon.power_select");
    }

    /**
     * The sound a ring makes as its pad closes.
     *
     * @return the sound name, or empty for silence
     */
    public static String getRingSoundClose()
    {
        return soundSetting(ConfigKeys.RING_SOUND_CLOSE, "block.beacon.deactivate");
    }

    /**
     * The sound a ring makes when it turns somebody away.
     *
     * @return the sound name, or empty for silence
     */
    public static String getRingSoundRefused()
    {
        return soundSetting(ConfigKeys.RING_SOUND_REFUSED, "block.note_block.bass");
    }

    /**
     * Whether beaming makes any noise at all.
     *
     * @return true if beam sounds should play
     */
    public static boolean isBeamSoundsEnabled()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.BEAM_SOUNDS_ENABLED);
        return (s == null) || s.getBooleanValue();
    }

    /**
     * How loud beam sounds are.
     *
     * @return 1.0 when unset; past 1.0 it widens the range heard rather than the loudness
     */
    public static float getBeamSoundVolume()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.BEAM_SOUND_VOLUME);
        return (s == null) ? 1.0f : (float) s.getDoubleValue();
    }

    /**
     * The sound played the instant a beam sequence starts.
     *
     * @return the sound name, or empty for silence
     */
    public static String getBeamSoundCharge()
    {
        return soundSetting(ConfigKeys.BEAM_SOUND_CHARGE, "block.respawn_anchor.charge");
    }

    /**
     * The sound played the instant the real teleport fires, mid-rise.
     *
     * @return the sound name, or empty for silence
     */
    public static String getBeamSoundDepart()
    {
        return soundSetting(ConfigKeys.BEAM_SOUND_DEPART, "entity.enderman.teleport");
    }

    /**
     * The sound played once the column finishes descending at the destination.
     *
     * @return the sound name, or empty for silence
     */
    public static String getBeamSoundArrive()
    {
        return soundSetting(ConfigKeys.BEAM_SOUND_ARRIVE, "entity.shulker.teleport");
    }

    /**
     * How long the glow gathers at body height before opening into the departure column.
     *
     * @return the configured tick count, unclamped -- {@code BeamAnimation} is where the
     *         relationships between this and the other beam timings are enforced, since
     *         clamping one setting correctly here would still need to know the others
     */
    public static int getBeamEnvelopTicks()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.BEAM_ENVELOP_TICKS);
        return (s == null) ? 12 : s.getIntValue();
    }

    /**
     * How far into the envelope the traveller vanishes.
     *
     * @return the configured tick count, unclamped -- see {@link #getBeamEnvelopTicks()}
     */
    public static int getBeamVanishAtStep()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.BEAM_VANISH_AT_STEP);
        return (s == null) ? 6 : s.getIntValue();
    }

    /**
     * How long the column rises and departs, once the envelope opens into it.
     *
     * @return the configured tick count, unclamped -- see {@link #getBeamEnvelopTicks()}
     */
    public static int getBeamRiseTicks()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.BEAM_RISE_TICKS);
        return (s == null) ? 18 : s.getIntValue();
    }

    /**
     * How far into the rise the real teleport fires.
     *
     * @return the configured tick count, unclamped -- see {@link #getBeamEnvelopTicks()}
     */
    public static int getBeamTeleportAtStep()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.BEAM_TELEPORT_AT_STEP);
        return (s == null) ? 12 : s.getIntValue();
    }

    /**
     * How long the column takes to descend into place at the destination.
     *
     * @return the configured tick count, unclamped -- see {@link #getBeamEnvelopTicks()}
     */
    public static int getBeamDescendTicks()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.BEAM_DESCEND_TICKS);
        return (s == null) ? 20 : s.getIntValue();
    }

    /**
     * How long the column takes to fade out once it has deposited the traveller.
     *
     * @return the configured tick count, unclamped -- see {@link #getBeamEnvelopTicks()}
     */
    public static int getBeamFadeTicks()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.BEAM_FADE_TICKS);
        return (s == null) ? 8 : s.getIntValue();
    }

    /**
     * Whether beam travel has a per-player cooldown at all.
     *
     * @return true if a cooldown should be enforced
     */
    public static boolean isBeamUseCooldownEnabled()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.BEAM_USE_COOLDOWN_ENABLED);
        return (s != null) && s.getBooleanValue();
    }

    /**
     * How many seconds a player must wait between beams, when the cooldown is enabled.
     *
     * @return the cooldown in seconds
     */
    public static long getBeamUseCooldownSeconds()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.BEAM_USE_COOLDOWN_SECONDS);
        return (s == null) ? 120 : s.getIntValue();
    }

    /**
     * How much a beam costs, in whatever currency Vault is connected to.
     *
     * @return the cost; 0 or below means free
     */
    public static double getBeamEconomyUseCost()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.BEAM_ECONOMY_USE_COST);
        return (s == null) ? 0.0 : s.getDoubleValue();
    }

    /**
     * Whether the plugin's own sign text glows.
     *
     * @return true if signs the plugin writes should use glowing text
     */
    public static boolean isSignGlowingText()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.SIGN_GLOWING_TEXT);
        return (s == null) || s.getBooleanValue();
    }

    /**
     * Whether a redstone signal on an already-open gate pushes its shutdown back.
     *
     * @return true if a trigger should extend an open wormhole
     */
    public static boolean isRedstoneExtendOpenTime()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.REDSTONE_EXTEND_OPEN_TIME);
        return (s == null) || s.getBooleanValue();
    }

    /**
     * Whether a player-placed dial sign is converted to the gate's own sign material.
     *
     * @return true if the dial sign should be made to match the gate's palette
     */
    public static boolean isSignDialMatchMaterial()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.SIGN_DIAL_MATCH_MATERIAL);
        return (s == null) || s.getBooleanValue();
    }

    /**
     * Reads a configured colour name, resolved by {@code SignStyle} at the point of use.
     *
     * <p>Returned as the raw name rather than a resolved colour so the fallback stays with
     * the caller that knows what this particular line should look like.
     *
     * @param key
     *            the colour setting to read
     * @return the configured name, or empty when unset
     */
    private static String colorSetting(final ConfigKeys key)
    {
        final Setting s = ConfigManager.getConfigurations().get(key);
        return s == null ? "" : String.valueOf(s.getStringValue()).trim();
    }

    /** @return colour name for a gate's own name, on either sign */
    public static String getSignColorGateName()
    {
        return colorSetting(ConfigKeys.SIGN_COLOR_GATE_NAME);
    }

    /** @return colour name for the network line on a gate's name sign */
    public static String getSignColorNetwork()
    {
        return colorSetting(ConfigKeys.SIGN_COLOR_NETWORK);
    }

    /** @return colour name for the owner line on a gate's name sign */
    public static String getSignColorOwner()
    {
        return colorSetting(ConfigKeys.SIGN_COLOR_OWNER);
    }

    /** @return colour name for the destination currently selected on a dial sign */
    public static String getSignColorSelected()
    {
        return colorSetting(ConfigKeys.SIGN_COLOR_SELECTED);
    }

    /** @return colour name for the destinations either side of the selected one */
    public static String getSignColorNeighbour()
    {
        return colorSetting(ConfigKeys.SIGN_COLOR_NEIGHBOUR);
    }

    /**
     * Reads a sound name.
     *
     * <p>Kept as text rather than resolved to a {@code Sound}, and played through the
     * overload that takes a name. Two reasons: the sound type has been moving toward a
     * registry-backed one across recent versions, which is exactly the kind of thing that
     * cannot be asked about before a server has started; and a name passes straight through
     * to the client, so a server with a resource pack can name its own sounds here.
     *
     * <p>An unknown name is silent rather than an error, which is what the client does with
     * one anyway.
     *
     * @param key
     *            the setting to read
     * @param fallback
     *            the sound to use when it is unset
     * @return the sound name, trimmed; empty means play nothing
     */
    private static String soundSetting(final ConfigKeys key, final String fallback)
    {
        final Setting s = ConfigManager.getConfigurations().get(key);
        if (s == null)
        {
            return fallback;
        }
        final String name = String.valueOf(s.getStringValue()).trim();
        return "none".equalsIgnoreCase(name) ? "" : name;
    }

    /**
     * Reads a material setting by name, falling back when it is missing or unknown.
     *
     * @param key
     *            which setting
     * @param fallback
     *            what to use when it cannot be read
     */
    private static Material materialSetting(final ConfigKeys key, final Material fallback)
    {
        final Setting s = ConfigManager.getConfigurations().get(key);
        if (s == null)
        {
            return fallback;
        }
        final Material found = Material.matchMaterial(String.valueOf(s.getStringValue()));
        return found == null ? fallback : found;
    }

    /**
     * Writes material groups discovered from gate shapes into config.yml.
     *
     * @param groups
     *            the groups to add
     */
    public static void appendDiscoveredMaterialGroups(
        final List<MaterialGroup> groups)
    {
        ConfigurationYAML.appendMaterialGroups(ConfigurationYAML.getConfigFile(configuredPluginName), groups);
    }

    /**
     * Whether an unrecognised shape palette should be appended to config.yml automatically.
     *
     * <p>Set this false if you prefer to curate the group list by hand; a group you delete
     * will then stay deleted instead of reappearing on the next restart.
     *
     * @return true to auto-append discovered palettes
     */
    public static boolean isGateMaterialGroupsAutodiscover()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.GATE_MATERIAL_GROUPS_AUTODISCOVER);
        return (s == null) || s.getBooleanValue();
    }

    /**
     * How long a player waits between gate trips, in seconds.
     *
     * <p>This replaced three separate group settings that were never registered in
     * {@link DefaultSettings}. Because they were absent, {@code isConfigurationKey} was
     * false for all three: the getter always returned its hardcoded literal and the
     * matching setter silently did nothing, so {@code /wormhole cooldown one 300} reported
     * success and changed nothing. Only group one was ever read, and only from one place,
     * so the honest shape is a single registered setting -- which is also what beaming
     * already does with {@code BEAM_USE_COOLDOWN_SECONDS}.
     *
     * @return the cooldown in seconds
     */
    public static int getUseCooldownSeconds()
    {
        return isConfigurationKey(ConfigKeys.USE_COOLDOWN_SECONDS)
            ? getSetting(ConfigKeys.USE_COOLDOWN_SECONDS).getIntValue()
            : 120;
    }

    /**
     * Gets the wormhole use is teleport.
     * 
     * @return true if travelling needs permission as well as activating; false when unset
     */
    public static boolean getWormholeUseIsTeleport()
    {
        Setting bipe;
        if ((bipe = ConfigManager.getConfigurations().get(ConfigKeys.WORMHOLE_USE_IS_TELEPORT)) != null)
        {
            return bipe.getBooleanValue();
        }
        else
        {
            return false;
        }
    }

    /**
     * Checks if is configuration key.
     * 
     * @return true if the key has a loaded setting
     */
    private static boolean isConfigurationKey(final ConfigKeys configKey)
    {
        return getConfigurations().containsKey(configKey);
    }

    /**
     * Checks if is use cooldown enabled.
     * 
     * @return true if the use cooldown is switched on; false when unset
     */
    public static boolean isUseCooldownEnabled()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.USE_COOLDOWN_ENABLED);
        return (s != null) && s.getBooleanValue();
    }

    /**
     * How many mirrors one world may hold.
     *
     * @return the most, or 0 for no limit
     */
    public static int getMirrorPerWorldLimit()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.MIRROR_PER_WORLD_LIMIT);
        return (s == null) ? 1 : Math.max(0, s.getIntValue());
    }

    /**
     * How close a player must be for a mirror's banner to give way to its room, how far they may
     * drift before a mirror they turned on goes off, and how far out its wall is read.
     *
     * @return the distance in blocks, never below one
     */
    public static int getMirrorProximityDistance()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.MIRROR_PROXIMITY_DISTANCE);
        return (s == null) ? 16 : Math.max(1, s.getIntValue());
    }

    /**
     * How often the proximity sweep runs.
     *
     * <p>Floored at one tick rather than zero: a period of zero asks Bukkit to reschedule
     * forever without advancing, which is a hung server rather than a fast mirror.
     *
     * @return the period in ticks, never below one
     */
    public static long getMirrorProximityTicks()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.MIRROR_PROXIMITY_TICKS);
        return (s == null) ? 20L : Math.max(1L, s.getIntValue());
    }

    /**
     * Whether a mirror names itself above the hotbar to whoever is looking at it.
     *
     * <p>Defaults to true when the setting is missing, which is what an existing server's
     * config.yml looks like after an upgrade. A new thing that announces itself is the right
     * default here: the whole complaint this answers is that a mirror gives no sign of being
     * anything but a banner.
     *
     * @return true if looking at a mirror says what it is
     */
    public static boolean isMirrorApproachMessage()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.MIRROR_APPROACH_MESSAGE);
        return (s == null) || s.getBooleanValue();
    }


    /**
     * Whether a viewer's own fog is pulled in to where a mirror's room ends.
     *
     * <p>Defaults to false when the setting is missing, which is what an existing server's
     * config.yml looks like after an upgrade. Off is the right default: it does nothing at all on
     * Spigot, nothing at the default depth, and where it does work it changes how the whole world
     * looks to that player and not only the mirror.
     *
     * @return true if a mirror may narrow what its viewers are sent
     */
    public static boolean isMirrorFogAtDepth()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.MIRROR_FOG_AT_DEPTH);
        return (s != null) && s.getBooleanValue();
    }

    /**
     * How far from a viewer's eye a mirror's far side is drawn as real blocks.
     *
     * @return the radius in blocks, between 4 and 160
     */
    public static int getMirrorViewDepth()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.MIRROR_VIEW_DEPTH);
        // 160 is ten chunks, a server's usual view distance. Taking a capture that deep works a
        // few bits per block over a box 325 across, tens of megabytes for a few seconds.
        return (s == null) ? 160 : Math.max(4, Math.min(160, s.getIntValue()));
    }

    /**
     * Whether a player's following pets travel with them.
     *
     * @return true unless the setting turns it off
     */
    public static boolean isPetsFollowOwner()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.PETS_FOLLOW_OWNER);
        return (s == null) || s.getBooleanValue();
    }

    /**
     * Checks if same-world-only mode is enabled.
     * When true, players may only teleport through gates whose destination is in the same world.
     * 
     * @return true if cross-world gate travel is blocked
     */
    public static boolean isSameWorldOnly()
    {
        Setting wsd;
        if ((wsd = ConfigManager.getConfigurations().get(ConfigKeys.SAME_WORLD_ONLY)) != null)
        {
            return wsd.getBooleanValue();
        }
        else
        {
            return false;
        }
    }

    /**
     * Every setting there is, by name.
     *
     * <p>The names are the enum constants: what the command echoes back, and what tab
     * completion offers. {@code config.yml} writes the same settings in kebab case, so the
     * file and this list disagree on punctuation -- {@link #settingKey(String)} is what makes
     * either spelling work.
     *
     * @return the setting names, sorted
     */
    public static List<String> settingNames()
    {
        final List<String> names = new ArrayList<String>();
        for (final ConfigKeys key : getConfigurations().keySet())
        {
            names.add(key.name());
        }
        Collections.sort(names);
        return names;
    }

    /**
     * What one setting is currently, and what it is for.
     *
     * @param name
     *            the setting name, in any case
     * @return a line describing it, or null if there is no such setting
     */
    public static String describeSetting(final String name)
    {
        final Setting setting = settingNamed(name);
        if (setting == null)
        {
            return null;
        }
        return setting.getName().name() + " = " + setting.getValue()
            + System.lineSeparator() + "  " + setting.getDescription();
    }

    /**
     * Changes one setting and writes it back to config.yml.
     *
     * <p>Typed from what is already there rather than from a declaration: every setting
     * arrives with a default, and that default's type is what the setting is. A boolean
     * stays a boolean, a number stays a number, and text with a closed set of valid values
     * -- a ring style, a material, a log level -- has to be one of them. See
     * {@link ParsedSetting} for the rules and for what is deliberately left as free text.
     *
     * <p>Takes effect immediately, because everything reads its setting when it needs it
     * rather than caching it at startup. That is the point of having this at all -- before
     * it, changing a sound or a ring timing meant editing the file and restarting the
     * server.
     *
     * @param name
     *            the setting name, in any case
     * @param raw
     *            the new value as typed
     * @return a message saying what happened
     */
    public static String applySetting(final String name, final String raw)
    {
        final Setting setting = settingNamed(name);
        if (setting == null)
        {
            return null;
        }
        final ParsedSetting parsed = ParsedSetting.read(setting.getName(), setting.getValue(), raw);
        if (!parsed.isAccepted())
        {
            return parsed.getRefusal();
        }
        setting.setValue(parsed.getValue());
        Configuration.persistCurrentConfiguration(SECTION);
        final String done = setting.getName().name() + " is now " + parsed.getValue();
        // The value is already saved; a failure to apply it must say so, not escape the command.
        try
        {
            follow(setting.getName());
        }
        catch (final RuntimeException | LinkageError e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "Could not apply " + setting.getName(), e);
            return done + ", saved, but it could not be applied now: " + e + ". It applies at the next restart.";
        }
        return done + ".";
    }

    /**
     * Applies a setting that something read once rather than where it is used, so a change takes
     * effect now rather than at the next restart.
     *
     * @param key
     *            the setting just changed
     */
    private static void follow(final ConfigKeys key)
    {
        switch (key)
        {
            case LOG_LEVEL -> WormholeXTreme.applyLogLevel(getLogLevel());
            case METRICS_ENABLED -> followMetrics();
            case ECONOMY_ENABLED -> followEconomy();
            case PLACEHOLDERS_ENABLED -> followPlaceholders();
            case WORLDGUARD_ENABLED -> RegionFlags.follow();
            // Every ring's trigger volume is indexed at load, as deep as these two said then.
            case RING_REACH, RING_MAX_CEILING_DROP -> RingManager.reindex(getRingReach());
            case GATE_MATERIAL_GROUPS_AUTODISCOVER -> StargateShapeRegistry.followAutodiscover();
            case PERMISSIONS_SUPPORT_DISABLE, PERMISSIONS_AUTO_FALLBACK -> PermissionsSupport.detectProvider();
            case DYNMAP_ENABLED, BLUEMAP_ENABLED, SQUAREMAP_ENABLED, PL3XMAP_ENABLED, MAP_SHOW_GATES,
                MAP_SHOW_RINGS, MAP_SHOW_BEAMS, MAP_SHOW_MIRRORS, MAP_SHOW_IRIS_GATES -> MapMarkers.followConfig();
            default -> RepeatingSweeps.follow(key);
        }
    }

    /** Attaches to or lets go of Vault's economy to match {@code economy-enabled}. */
    private static void followEconomy()
    {
        if (isEconomyEnabled())
        {
            EconomySupport.enableEconomy();
        }
        else
        {
            EconomySupport.disableEconomy();
        }
    }

    /** Registers the PlaceholderAPI expansion when turned on; turned off, it answers nothing. */
    private static void followPlaceholders()
    {
        if (isPlaceholdersEnabled())
        {
            PlaceholderSupport.enablePlaceholders();
        }
    }

    /** Starts or stops bStats to match {@code metrics-enabled}; a failure costs only a log line. */
    private static void followMetrics()
    {
        try
        {
            if (isMetricsEnabled())
            {
                MetricsSupport.enableMetrics(
                    WormholeXTreme.getThisPlugin());
            }
            else
            {
                MetricsSupport.disableMetrics();
            }
        }
        catch (final Exception | LinkageError e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING,
                "Could not follow metrics-enabled", e);
        }
    }

    /**
     * Finds a setting by name, however it was typed.
     *
     * @param name
     *            the setting name
     * @return the setting, or null if there is no such one
     */
    private static Setting settingNamed(final String name)
    {
        final String key = settingKey(name);
        if (key == null)
        {
            return null;
        }
        try
        {
            return getConfigurations().get(ConfigKeys.valueOf(key));
        }
        catch (final IllegalArgumentException notASetting)
        {
            return null;
        }
    }

    /**
     * The enum form of a setting name, however it was spelled.
     *
     * <p>{@code config.yml} writes its keys in kebab case -- {@code gate-sound-kawoosh} --
     * while the settings themselves are enum constants with underscores. The name a server
     * owner reads out of the file was therefore not a name this command accepted: typing the
     * key exactly as it appears there answered "No setting called gate-sound-kawoosh.",
     * which reads as the setting no longer existing rather than as the wrong punctuation
     * between two words that are otherwise identical.
     *
     * <p>Folded against {@link Locale#ROOT} rather than the server's own locale, for the
     * reason the rest of this path already is: a Turkish JVM upper-cases {@code i} to a
     * dotted capital I, and most of the names here have an {@code i} in them.
     *
     * <p>Only a null name comes back null. Blank text comes back blank and is left to fail
     * the lookup like any other name that is not a setting, rather than being given a second
     * meaning here.
     *
     * @param typed
     *            the name as typed, in either spelling, or null
     * @return the same name in enum form, or null if {@code typed} was null
     */
    static String settingKey(final String typed)
    {
        if (typed == null)
        {
            return null;
        }
        return typed.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    /**
     * The settings whose names contain what was typed.
     *
     * <p>What {@code /wormhole config <partial>} lists: somebody hunting for the ring
     * cooldown is better served by the settings with RING in the name than by being told
     * that RING does not exist. The fragment is folded the same way a whole name is, so
     * {@code gate-sound} copied out of config.yml finds what {@code gate_sound} finds.
     *
     * @param needle
     *            the text to look for, empty for everything
     * @return the matching names, sorted
     */
    public static List<String> settingNamesMatching(final String needle)
    {
        final String wanted = (needle == null) || (needle.trim().isEmpty())
            ? "" : settingKey(needle);
        final List<String> found = new ArrayList<String>();
        for (final String name : settingNames())
        {
            if (name.contains(wanted))
            {
                found.add(name);
            }
        }
        return found;
    }

    /**
     * Sets the config value.
     * 
     * @param key
     *            a loaded setting; an unknown key, like a null value, changes nothing
     */
    public static void setConfigValue(final ConfigKeys key, final Object value)
    {
        if ((key != null) && isConfigurationKey(key) && (value != null))
        {
            getConfigurations().get(key).setValue(value);
        }
    }

    /**
     * Set timeout activate setting in ConfigKeys.
     * 
     * @param i
     *            Timeout in seconds.
     */
    public static void setTimeoutActivate(final int i)
    {
        ConfigManager.setConfigValue(ConfigKeys.TIMEOUT_ACTIVATE, i);
    }

    /**
     * Set timeout shutdown setting in ConfigKeys.
     * 
     * @param i
     *            the new timeout shutdown
     */
    public static void setTimeoutShutdown(final int i)
    {
        ConfigManager.setConfigValue(ConfigKeys.TIMEOUT_SHUTDOWN, i);
    }

    /**
     * Sets the up configs.
     * 
     * @param pdf
     *            the new up configs
     */
    public static void setupConfigs(final String pluginName)
    {
        configuredPluginName = pluginName;
        Configuration.loadConfiguration(pluginName);
    }

    /**
     * Sets the use cooldown enabled.
     * 
     * @param b
     *            the new use cooldown enabled
     */
    public static void setUseCooldownEnabled(final boolean b)
    {
        ConfigManager.setConfigValue(ConfigKeys.USE_COOLDOWN_ENABLED, b);
    }

    /**
     * Sets how long a player waits between gate trips.
     *
     * @param seconds
     *            the new cooldown, in seconds
     */
    public static void setUseCooldownSeconds(final int seconds)
    {
        setConfigValue(ConfigKeys.USE_COOLDOWN_SECONDS, seconds);
    }

    /** Returns true if gate and ring construction should be logged to CoreProtect. */
    public static boolean isCoreProtectEnabled()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.COREPROTECT_ENABLED);
        return s != null && s.getBooleanValue();
    }

    /** Returns true if the WorldGuard region flags should be registered and checked. */
    public static boolean isWorldGuardEnabled()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.WORLDGUARD_ENABLED);
        return s != null && s.getBooleanValue();
    }

    /** Returns true if gates, rings, public beam destinations and mirrors should be drawn on Dynmap. */
    public static boolean isDynmapEnabled()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.DYNMAP_ENABLED);
        return s != null && s.getBooleanValue();
    }

    /** Returns true if gates, rings, public beam destinations and mirrors should be drawn on BlueMap. */
    public static boolean isBlueMapEnabled()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.BLUEMAP_ENABLED);
        return s != null && s.getBooleanValue();
    }

    /** Returns true if gates, rings, public beam destinations and mirrors should be drawn on squaremap. */
    public static boolean isSquaremapEnabled()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.SQUAREMAP_ENABLED);
        return s != null && s.getBooleanValue();
    }

    /** Returns true if gates, rings, public beam destinations and mirrors should be drawn on Pl3xMap. */
    public static boolean isPl3xMapEnabled()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.PL3XMAP_ENABLED);
        return s != null && s.getBooleanValue();
    }

    /** Returns true if gates and the lines between dialled pairs are shown on the web map; on when the setting is missing, as it ships. */
    public static boolean isMapShowGates()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.MAP_SHOW_GATES);
        return (s == null) || s.getBooleanValue();
    }

    /** Returns true if transport rings are shown on the web map; on when the setting is missing, as it ships. */
    public static boolean isMapShowRings()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.MAP_SHOW_RINGS);
        return (s == null) || s.getBooleanValue();
    }

    /** Returns true if public beam destinations are shown on the web map; on when the setting is missing, as it ships. */
    public static boolean isMapShowBeams()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.MAP_SHOW_BEAMS);
        return (s == null) || s.getBooleanValue();
    }

    /** Returns true if quantum mirrors are shown on the web map; on when the setting is missing, as it ships. */
    public static boolean isMapShowMirrors()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.MAP_SHOW_MIRRORS);
        return (s == null) || s.getBooleanValue();
    }

    /** Returns true if gates with an iris code appear on the web map; on when the setting is missing, as it ships. */
    public static boolean isMapShowIrisGates()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.MAP_SHOW_IRIS_GATES);
        return (s == null) || s.getBooleanValue();
    }

    /** Returns true if the PlaceholderAPI expansion should be registered. */
    public static boolean isPlaceholdersEnabled()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.PLACEHOLDERS_ENABLED);
        return s != null && s.getBooleanValue();
    }

    /** Returns true if anonymous usage counts may be sent to bStats; on when the setting is missing, as it ships. */
    public static boolean isMetricsEnabled()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.METRICS_ENABLED);
        return (s == null) || s.getBooleanValue();
    }

    /** Returns true if startup may look for a newer release; on when the setting is missing, as it ships. */
    public static boolean isUpdateCheckEnabled()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.UPDATE_CHECK);
        return (s == null) || s.getBooleanValue();
    }

    /** Returns true if Vault economy integration is enabled in config. */
    public static boolean isEconomyEnabled()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.ECONOMY_ENABLED);
        return s != null && s.getBooleanValue();
    }

    /** Cost charged when a player walks through a gate. 0 = free. */
    public static double getEconomyUseCost()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.ECONOMY_USE_COST);
        return s != null ? s.getDoubleValue() : 0.0;
    }

    /** Cost charged when a player builds a gate. 0 = free. */
    public static double getEconomyBuildCost()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.ECONOMY_BUILD_COST);
        return s != null ? s.getDoubleValue() : 0.0;
    }
}
