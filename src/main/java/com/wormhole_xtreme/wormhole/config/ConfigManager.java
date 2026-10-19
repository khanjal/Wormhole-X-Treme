package com.wormhole_xtreme.wormhole.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bukkit.Material;

import com.wormhole_xtreme.wormhole.RepeatingSweeps;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.integration.RegionFlags;
import com.wormhole_xtreme.wormhole.logic.DialSpinPattern;
import com.wormhole_xtreme.wormhole.model.HorizonRipple;
import com.wormhole_xtreme.wormhole.model.IrisSweep;
import com.wormhole_xtreme.wormhole.model.MaterialGroup;
import com.wormhole_xtreme.wormhole.model.StargateShapeRegistry;
import com.wormhole_xtreme.wormhole.model.ring.RingAccess;
import com.wormhole_xtreme.wormhole.model.ring.RingManager;
import com.wormhole_xtreme.wormhole.model.ring.RingStyle;
import com.wormhole_xtreme.wormhole.plugin.EconomySupport;
import com.wormhole_xtreme.wormhole.plugin.MetricsSupport;
import com.wormhole_xtreme.wormhole.plugin.PermissionsSupport;
import com.wormhole_xtreme.wormhole.plugin.PlaceholderSupport;
import com.wormhole_xtreme.wormhole.plugin.map.MapMarkers;
import com.wormhole_xtreme.wormhole.utils.ChatText;
import com.wormhole_xtreme.wormhole.utils.Messages;


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

        /** Whether gates stay in simple mode even with a Vault permission provider installed. */
        PERMISSIONS_SUPPORT_DISABLE,
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

        /** Whether a command named in chat can be clicked (#538). */
        CLICKABLE_CHAT,

        /** The language of the console and anyone whose client names none we have (#26). */
        SERVER_LANGUAGE,

        /** The LOG LEVEL. */
        LOG_LEVEL,

        /** Ticks a ring pair refuses to fire again after a cycle. */
        RING_COOLDOWN_TICKS,
        /** How fast every ring animation runs against its shipped timings (#589). */
        RING_ANIMATION_SPEED,
        /** Whether a ring briefly shows its outline to somebody it has turned away. */
        RING_OUTLINE_ON_REFUSAL,
        /** Block layers of passenger volume, measured from the ring plane into the room. */
        RING_REACH,
        /** Required distance between ring anchors, in blocks. */
        RING_MIN_SEPARATION,
        /** Furthest two ends of a pair may be apart on the ground. Zero means no limit. */
        RING_MAX_LINK_DISTANCE,
        /** Furthest below its plane a ceiling ring will look for the floor. */
        RING_MAX_CEILING_DROP,
        /** How many ring pairs one player may own. Zero means no limit. */
        RING_MAX_PAIRS_PER_PLAYER,
        /** What a newly built ring pair starts as: PUBLIC or PRIVATE. */
        RING_DEFAULT_ACCESS,
        /** How a ring stack deploys: CONCURRENT or SEQUENTIAL. */
        RING_DEFAULT_STYLE,
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

        /** Whether a big gate's view of a smaller far gate is drawn at the end of a tunnel (#583). */
        GATE_VIEW_FUNNEL,

        /** The longest that tunnel may be. */
        GATE_VIEW_FUNNEL_LENGTH,

        /** The shortest that tunnel is, a straight one where the far gate is as big (#587); 0 for none. */
        GATE_VIEW_FUNNEL_MIN_LENGTH,

        /** Whether an open gate's horizon ripples (#579). */
        GATE_RIPPLE,

        GATE_SOUND_AMBIENT,
        /** Whether a player arriving through a gate is told in chat which gate they arrived at (#485). */
        SHOW_GATE_WELCOME_MESSAGE,
        GATE_DIAL_SPIN,
        /** Minutes a gate build preview lasts after its owner last used a build command. */
        GATE_PREVIEW_MINUTES,
        /** Most blocks every gate build preview on the server may show between them. */
        GATE_PREVIEW_MAX_BLOCKS,
        /** Least distance between two gates in one world; 0 for none (#635). */
        MIN_GATE_DISTANCE,
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
        /** How fast the beaming animation runs against its shipped timings (#589). */
        BEAM_ANIMATION_SPEED,
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
         * How far past an opening a mirror's room or an open gate's far side is drawn, in blocks (#589).
         *
         * <p>Its render distance: past it nothing is drawn. 160 by default, ten chunks, so the room
         * ends where the client stops showing anything; a room is its surfaces, so depth costs
         * little. A gate captures a shallower first step and fills in to this behind it.
         */
        VIEW_DEPTH,

        /**
         * Whether the creatures standing in a mirror's or a gate's far room are shown through it
         * (#296), as stand-ins only the viewer can see. Off by default: they are real entities.
         */
        MIRROR_SHOW_ENTITIES,

        /**
         * Whether the players standing in a far room are shown too (#296, step 2), with
         * {@link #MIRROR_SHOW_ENTITIES} on. Off by default: it shows who is in the destination room.
         */
        MIRROR_SHOW_PLAYERS,

        /**
         * How many chunks in front of a watched window's far side are held loaded, so its creatures
         * show on the first look (#296). 0 holds nothing; read up to {@link ConfigManager#MOST_ENTITY_LOAD_RADIUS}.
         */
        MIRROR_ENTITY_LOAD_RADIUS,

        MIRROR_APPROACH_MESSAGE
    }

    /**
     * Whole lines said in more than one place, each built by {@link ChatText} with its header.
     *
     * <p>A line naming what the player typed is built where it is sent, so the name can be
     * coloured (#438), so each one here is sent as it stands, with nothing appended.
     */
    public enum MessageStrings
    {

        /** The permission no. */
        PERMISSION_NO(ChatText.error("You lack the permissions to do this.")),

        /** The target is self. */
        TARGET_IS_SELF(ChatText.error("A gate cannot dial itself.")),

        /** The target invalid. */
        TARGET_INVALID(ChatText.error("Invalid gate target.")),

        /** The target is active. */
        TARGET_IS_ACTIVE(ChatText.error("Target gate is currently active.")),

        /** The far gate is in another world, and same-world-only is on. */
        CROSS_WORLD_DISABLED(ChatText.error("Cross-world travel is disabled on this server.")),

        /** The gate not active. */
        GATE_NOT_ACTIVE(ChatText.error("No gate activated to dial. Press a DHD first.")),

        /** The gate remove active. */
        GATE_REMOVE_ACTIVE(ChatText.error("Gate remotely activated.")),

        /** The gate shutdown. */
        GATE_SHUTDOWN(ChatText.info("Gate shut down.")),

        /** The gate activated. */
        GATE_ACTIVATED(ChatText.info("Gate successfully activated.")),

        /** The gate deactivated. */
        GATE_DEACTIVATED(ChatText.info("Gate successfully deactivated.")),

        /** The gate dialed. */
        GATE_CONNECTED(ChatText.info("Stargates connected.")),

        /** The construct success. */
        CONSTRUCT_SUCCESS(ChatText.info("Gate successfully constructed.")),

        /** The gate not specified. */
        GATE_NOT_SPECIFIED(ChatText.error("No gate name specified.")),

        /** Player recently arrived at gate. */
        PLAYER_RECENT_ARRIVAL(ChatText.error("You can't enter an incoming wormhole.")),

        /** Said to a traveller, walking or riding, turned back by the far gate's shut iris. */
        REMOTE_IRIS_CLOSED(ChatText.error("The far gate's iris is closed.")),

        /** Insufficient funds message. */
        ECONOMY_INSUFFICIENT_FUNDS(ChatText.error("Insufficient funds to use this gate."));

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
     * Whether gates are kept in simple mode whatever permission provider is installed.
     *
     * @return true if gate permission nodes are never consulted
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

    /** See {@link RingSettings#getRingCountdownTicks()}. */
    public static int getRingCountdownTicks()
    {
        return RingSettings.getRingCountdownTicks();
    }

    /** See {@link RingSettings#getRingCooldownTicks()}. */
    public static int getRingCooldownTicks()
    {
        return RingSettings.getRingCooldownTicks();
    }

    /** See {@link RingSettings#getRingDeployTicks()}. */
    public static int getRingDeployTicks()
    {
        return RingSettings.getRingDeployTicks();
    }

    /** See {@link RingSettings#getRingSettleTicks()}. */
    public static int getRingSettleTicks()
    {
        return RingSettings.getRingSettleTicks();
    }

    /** See {@link RingSettings#getRingFlashTicks()}. */
    public static int getRingFlashTicks()
    {
        return RingSettings.getRingFlashTicks();
    }

    /** See {@link RingSettings#getRingLightsLingerTicks()}. */
    public static int getRingLightsLingerTicks()
    {
        return RingSettings.getRingLightsLingerTicks();
    }

    /** See {@link RingSettings#isRingOutlineOnRefusal()}. */
    public static boolean isRingOutlineOnRefusal()
    {
        return RingSettings.isRingOutlineOnRefusal();
    }

    /** See {@link RingSettings#getRingOutlineTicks()}. */
    public static int getRingOutlineTicks()
    {
        return RingSettings.getRingOutlineTicks();
    }

    /** See {@link RingSettings#getRingHoldTicks()}. */
    public static int getRingHoldTicks()
    {
        return RingSettings.getRingHoldTicks();
    }

    /** See {@link RingSettings#getRingMaxCeilingDrop()}. */
    public static int getRingMaxCeilingDrop()
    {
        return RingSettings.getRingMaxCeilingDrop();
    }

    /** See {@link RingSettings#getRingReach()}. */
    public static int getRingReach()
    {
        return RingSettings.getRingReach();
    }

    /** See {@link RingSettings#getRingMinSeparation()}. */
    public static int getRingMinSeparation()
    {
        return RingSettings.getRingMinSeparation();
    }

    /** See {@link RingSettings#getRingMaxLinkDistance()}. */
    public static int getRingMaxLinkDistance()
    {
        return RingSettings.getRingMaxLinkDistance();
    }

    /** See {@link RingSettings#getRingMaxPairsPerPlayer()}. */
    public static int getRingMaxPairsPerPlayer()
    {
        return RingSettings.getRingMaxPairsPerPlayer();
    }

    /** See {@link RingSettings#getRingDefaultAccess()}. */
    public static RingAccess getRingDefaultAccess()
    {
        return RingSettings.getRingDefaultAccess();
    }

    /** See {@link RingSettings#getRingDefaultStyle()}. */
    public static RingStyle getRingDefaultStyle()
    {
        return RingSettings.getRingDefaultStyle();
    }

    /** See {@link RingSettings#getRingDefaultLight()}. */
    public static Material getRingDefaultLight()
    {
        return RingSettings.getRingDefaultLight();
    }

    /** See {@link RingSettings#getRingDefaultFlash()}. */
    public static Material getRingDefaultFlash()
    {
        return RingSettings.getRingDefaultFlash();
    }

    /**
     * Reads an int setting, falling back when it is missing.
     *
     * @param key
     *            which setting
     * @param fallback
     *            what to use when it is absent
     */
    static int intSetting(final ConfigKeys key, final int fallback)
    {
        final Setting s = ConfigManager.getConfigurations().get(key);
        return (s != null) ? s.getIntValue() : fallback;
    }

    /** The slowest a ring or beam animation may run, against its shipped pace: twice as long. */
    static final double SLOWEST_SPEED = 0.5;

    /** The fastest: half as long. A ring's two-tick frame cannot be halved again in whole ticks. */
    static final double FASTEST_SPEED = 2.0;

    /** See {@link RingSettings#getRingAnimationSpeed()}. */
    public static double getRingAnimationSpeed()
    {
        return RingSettings.getRingAnimationSpeed();
    }

    /** See {@link BeamSettings#getBeamAnimationSpeed()}. */
    public static double getBeamAnimationSpeed()
    {
        return BeamSettings.getBeamAnimationSpeed();
    }

    /** A speed setting, inside the range that keeps every sequence whole; 1.0 when missing or not a number. */
    static double speedSetting(final ConfigKeys key)
    {
        final Setting s = ConfigManager.getConfigurations().get(key);
        return clampSpeed((s == null) ? 1.0 : s.getDoubleValue());
    }

    /**
     * A speed brought inside its range.
     *
     * @param speed
     *            as written, which a command may have made NaN
     * @return from {@link #SLOWEST_SPEED} to {@link #FASTEST_SPEED}
     */
    static double clampSpeed(final double speed)
    {
        return Double.isNaN(speed) ? 1.0 : Math.max(SLOWEST_SPEED, Math.min(FASTEST_SPEED, speed));
    }

    /**
     * A shipped duration at a speed: exactly itself at 1.0.
     *
     * @param ticks
     *            the shipped duration
     * @param speed
     *            the speed, already clamped
     * @return the duration in whole ticks
     */
    static int atSpeed(final int ticks, final double speed)
    {
        return (int) Math.round(ticks / speed);
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

    /** Returns true if an open gate's horizon ripples (#579); off when the setting is missing, as it ships. */
    public static boolean isGateRipple()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.GATE_RIPPLE);
        return (s != null) && s.getBooleanValue();
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

    /** How deep a gate's first capture step reaches, at most: the rest is filled in behind it. */
    static final int GATE_FIRST_STEP_DEPTH = 32;

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
    public static int getGateViewFirstDepth()
    {
        return Math.min(GATE_FIRST_STEP_DEPTH, getViewDepth());
    }

    /**
     * Whether a big gate's view of a smaller far gate is drawn at the end of a tunnel (#583); on when
     * the setting is missing, as it ships.
     *
     * @return true for the tunnel, false for the plain view
     */
    public static boolean isGateViewFunnel()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.GATE_VIEW_FUNNEL);
        return (s == null) || s.getBooleanValue();
    }

    /**
     * The longest a big gate's tunnel to a smaller far gate may be, in blocks (#583).
     *
     * @return blocks, 1 to 64
     */
    public static int getGateViewFunnelLength()
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
    public static int getGateViewFunnelMinLength()
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
    public static int getGateViewFunnelLongest()
    {
        return isGateViewFunnel() ? getGateViewFunnelLength() : 0;
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
     * The least distance between two gates in one world, in blocks, centre to centre.
     *
     * @return blocks, 0 for no limit
     */
    public static int getMinGateDistance()
    {
        return Math.max(0, intSetting(ConfigKeys.MIN_GATE_DISTANCE, 0));
    }

    /** See {@link RingSettings#isRingSoundsEnabled()}. */
    public static boolean isRingSoundsEnabled()
    {
        return RingSettings.isRingSoundsEnabled();
    }

    /** See {@link RingSettings#getRingSoundVolume()}. */
    public static float getRingSoundVolume()
    {
        return RingSettings.getRingSoundVolume();
    }

    /** See {@link RingSettings#getRingSoundOpen()}. */
    public static String getRingSoundOpen()
    {
        return RingSettings.getRingSoundOpen();
    }

    /** See {@link RingSettings#getRingSoundRing()}. */
    public static String getRingSoundRing()
    {
        return RingSettings.getRingSoundRing();
    }

    /** See {@link RingSettings#getRingSoundFlash()}. */
    public static String getRingSoundFlash()
    {
        return RingSettings.getRingSoundFlash();
    }

    /** See {@link RingSettings#getRingSoundClose()}. */
    public static String getRingSoundClose()
    {
        return RingSettings.getRingSoundClose();
    }

    /** See {@link RingSettings#getRingSoundRefused()}. */
    public static String getRingSoundRefused()
    {
        return RingSettings.getRingSoundRefused();
    }

    /** See {@link BeamSettings#isBeamSoundsEnabled()}. */
    public static boolean isBeamSoundsEnabled()
    {
        return BeamSettings.isBeamSoundsEnabled();
    }

    /** See {@link BeamSettings#getBeamSoundVolume()}. */
    public static float getBeamSoundVolume()
    {
        return BeamSettings.getBeamSoundVolume();
    }

    /** See {@link BeamSettings#getBeamSoundCharge()}. */
    public static String getBeamSoundCharge()
    {
        return BeamSettings.getBeamSoundCharge();
    }

    /** See {@link BeamSettings#getBeamSoundDepart()}. */
    public static String getBeamSoundDepart()
    {
        return BeamSettings.getBeamSoundDepart();
    }

    /** See {@link BeamSettings#getBeamSoundArrive()}. */
    public static String getBeamSoundArrive()
    {
        return BeamSettings.getBeamSoundArrive();
    }

    /** See {@link BeamSettings#getBeamEnvelopTicks()}. */
    public static int getBeamEnvelopTicks()
    {
        return BeamSettings.getBeamEnvelopTicks();
    }

    /** See {@link BeamSettings#getBeamVanishAtStep()}. */
    public static int getBeamVanishAtStep()
    {
        return BeamSettings.getBeamVanishAtStep();
    }

    /** See {@link BeamSettings#getBeamRiseTicks()}. */
    public static int getBeamRiseTicks()
    {
        return BeamSettings.getBeamRiseTicks();
    }

    /** See {@link BeamSettings#getBeamTeleportAtStep()}. */
    public static int getBeamTeleportAtStep()
    {
        return BeamSettings.getBeamTeleportAtStep();
    }

    /** See {@link BeamSettings#getBeamDescendTicks()}. */
    public static int getBeamDescendTicks()
    {
        return BeamSettings.getBeamDescendTicks();
    }

    /** See {@link BeamSettings#getBeamFadeTicks()}. */
    public static int getBeamFadeTicks()
    {
        return BeamSettings.getBeamFadeTicks();
    }

    /** See {@link BeamSettings#isBeamUseCooldownEnabled()}. */
    public static boolean isBeamUseCooldownEnabled()
    {
        return BeamSettings.isBeamUseCooldownEnabled();
    }

    /** See {@link BeamSettings#getBeamUseCooldownSeconds()}. */
    public static long getBeamUseCooldownSeconds()
    {
        return BeamSettings.getBeamUseCooldownSeconds();
    }

    /** See {@link BeamSettings#getBeamEconomyUseCost()}. */
    public static double getBeamEconomyUseCost()
    {
        return BeamSettings.getBeamEconomyUseCost();
    }

    /** See {@link SignSettings#isSignGlowingText()}. */
    public static boolean isSignGlowingText()
    {
        return SignSettings.isSignGlowingText();
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

    /** See {@link SignSettings#isSignDialMatchMaterial()}. */
    public static boolean isSignDialMatchMaterial()
    {
        return SignSettings.isSignDialMatchMaterial();
    }

    /** See {@link SignSettings#getSignColorGateName()}. */
    public static String getSignColorGateName()
    {
        return SignSettings.getSignColorGateName();
    }

    /** See {@link SignSettings#getSignColorNetwork()}. */
    public static String getSignColorNetwork()
    {
        return SignSettings.getSignColorNetwork();
    }

    /** See {@link SignSettings#getSignColorOwner()}. */
    public static String getSignColorOwner()
    {
        return SignSettings.getSignColorOwner();
    }

    /** See {@link SignSettings#getSignColorSelected()}. */
    public static String getSignColorSelected()
    {
        return SignSettings.getSignColorSelected();
    }

    /** See {@link SignSettings#getSignColorNeighbour()}. */
    public static String getSignColorNeighbour()
    {
        return SignSettings.getSignColorNeighbour();
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
    static String soundSetting(final ConfigKeys key, final String fallback)
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
    static Material materialSetting(final ConfigKeys key, final Material fallback)
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

    /** See {@link MirrorSettings#getMirrorPerWorldLimit()}. */
    public static int getMirrorPerWorldLimit()
    {
        return MirrorSettings.getMirrorPerWorldLimit();
    }

    /** See {@link MirrorSettings#getMirrorProximityDistance()}. */
    public static int getMirrorProximityDistance()
    {
        return MirrorSettings.getMirrorProximityDistance();
    }

    /** See {@link MirrorSettings#isMirrorApproachMessage()}. */
    public static boolean isMirrorApproachMessage()
    {
        return MirrorSettings.isMirrorApproachMessage();
    }


    /** See {@link MirrorSettings#isMirrorShowEntities()}. */
    public static boolean isMirrorShowEntities()
    {
        return MirrorSettings.isMirrorShowEntities();
    }

    /** See {@link MirrorSettings#isMirrorShowPlayers()}. */
    public static boolean isMirrorShowPlayers()
    {
        return MirrorSettings.isMirrorShowPlayers();
    }

    /** The most chunks ahead a watched window's far side is held to: 4 is 45 chunks a window. */
    public static final int MOST_ENTITY_LOAD_RADIUS = 4;

    /** What {@code mirror-entity-load-radius} is when the setting is missing, as on an upgraded config.yml. */
    static final int DEFAULT_ENTITY_LOAD_RADIUS = 2;

    /** See {@link MirrorSettings#getMirrorEntityLoadRadius()}. */
    public static int getMirrorEntityLoadRadius()
    {
        return MirrorSettings.getMirrorEntityLoadRadius();
    }

    /**
     * How far past an opening a mirror's room, and an open gate's far side once filled in, is drawn.
     *
     * @return blocks, between 4 and 160
     */
    public static int getViewDepth()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.VIEW_DEPTH);
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
     * Whether a command named in chat can be clicked, rather than only read and typed.
     *
     * @return true unless the setting turns it off
     */
    public static boolean isClickableChat()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.CLICKABLE_CHAT);
        return (s == null) || s.getBooleanValue();
    }

    /**
     * The language of messages to the console and command blocks, and to a player whose client language has no file (#26).
     *
     * @return its code, lower case; en for one that is not a plain code
     */
    public static String getServerLanguage()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.SERVER_LANGUAGE);
        final String code = (s == null) ? null : parseServerLanguage(String.valueOf(s.getValue()));
        return (code == null) ? Messages.DEFAULT_LANGUAGE : code;
    }

    /**
     * Reads a language code as typed, in the shape a client sends one: lower-case letters and {@code _},
     * with {@code -} read as {@code _}, so {@code pt-BR} is {@code pt_br}. Not true or false, which is what
     * YAML makes of a bare {@code no} or {@code off}.
     *
     * @param raw
     *            the value as typed
     * @return it trimmed and in lower case, or null if it is not a code
     */
    public static String parseServerLanguage(final String raw)
    {
        final String code = (raw == null) ? null : Messages.sanitise(raw.trim().replace('-', '_'));
        return ("true".equals(code) || "false".equals(code)) ? null : code;
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
        return getConfigurations().keySet().stream()
            .map(ConfigKeys::name)
            .sorted()
            .collect(Collectors.toCollection(ArrayList::new));
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
     * Why a value would be refused for a setting, without changing anything.
     *
     * @param name
     *            the setting name, in any case
     * @param raw
     *            the value as typed
     * @return the refusal, or null if the value would be taken or there is no such setting
     */
    public static String settingRefusal(final String name, final String raw)
    {
        final Setting setting = settingNamed(name);
        return (setting == null) ? null
            : ParsedSetting.read(setting.getName(), setting.getValue(), raw).getRefusal();
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
    static void follow(final ConfigKeys key)
    {
        switch (key)
        {
            case LOG_LEVEL -> WormholeXTreme.applyLogLevel(getLogLevel());
            case SERVER_LANGUAGE -> Messages.load();
            case METRICS_ENABLED -> followMetrics();
            case ECONOMY_ENABLED -> followEconomy();
            case PLACEHOLDERS_ENABLED -> followPlaceholders();
            case WORLDGUARD_ENABLED -> RegionFlags.follow();
            // Every ring's trigger volume is indexed at load, as deep as these two said then.
            case RING_REACH, RING_MAX_CEILING_DROP -> RingManager.reindex(getRingReach());
            case GATE_MATERIAL_GROUPS_AUTODISCOVER -> StargateShapeRegistry.followAutodiscover();
            case GATE_RIPPLE -> followRipple();
            case PERMISSIONS_SUPPORT_DISABLE -> PermissionsSupport.detectProvider();
            case DYNMAP_ENABLED, BLUEMAP_ENABLED, SQUAREMAP_ENABLED, PL3XMAP_ENABLED, MAP_SHOW_GATES,
                MAP_SHOW_RINGS, MAP_SHOW_BEAMS, MAP_SHOW_MIRRORS, MAP_SHOW_IRIS_GATES -> MapMarkers.followConfig();
            default -> RepeatingSweeps.follow(key);
        }
    }

    /** Starts or stops the ripple sweep; turned off, every ripple and wait is forgotten, so turning it on again starts afresh. */
    private static void followRipple()
    {
        if (!isGateRipple())
        {
            HorizonRipple.cancelAll();
        }
        RepeatingSweeps.follow(ConfigKeys.GATE_RIPPLE);
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
        return settingNames().stream()
            .filter(name -> name.contains(wanted))
            .collect(Collectors.toCollection(ArrayList::new));
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

    /** @return the plugin folder name config.yml was read under */
    static String configuredPluginName()
    {
        return configuredPluginName;
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

    /** See {@link MapSettings#isMapShowGates()}. */
    public static boolean isMapShowGates()
    {
        return MapSettings.isMapShowGates();
    }

    /** See {@link MapSettings#isMapShowRings()}. */
    public static boolean isMapShowRings()
    {
        return MapSettings.isMapShowRings();
    }

    /** See {@link MapSettings#isMapShowBeams()}. */
    public static boolean isMapShowBeams()
    {
        return MapSettings.isMapShowBeams();
    }

    /** See {@link MapSettings#isMapShowMirrors()}. */
    public static boolean isMapShowMirrors()
    {
        return MapSettings.isMapShowMirrors();
    }

    /** See {@link MapSettings#isMapShowIrisGates()}. */
    public static boolean isMapShowIrisGates()
    {
        return MapSettings.isMapShowIrisGates();
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
