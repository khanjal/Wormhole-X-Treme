package com.wormhole_xtreme.wormhole.config;

import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import com.wormhole_xtreme.wormhole.model.ring.RingStyle;
import com.wormhole_xtreme.wormhole.model.ring.RingAccess;
import com.wormhole_xtreme.wormhole.model.ring.RingFlashDirection;
import org.bukkit.Material;


/**
 * The Class ConfigManager.
 */
public class ConfigManager
{
    /** Plugin folder name, remembered so config.yml can be located again after load. */
    private static volatile String configuredPluginName = "WormholeXTreme";


    /**
     * The Enum ConfigKeys.
     */
    public enum ConfigKeys
    {

        /** The PERMISSION SUPPORT DISABLE. */
        PERMISSIONS_SUPPORT_DISABLE,
        /** Automatically fall back to simple permission mode when no Vault provider is detected. */
        PERMISSIONS_AUTO_FALLBACK,
        /** The WORMHOL e_ us e_ i s_ teleport. */
        WORMHOLE_USE_IS_TELEPORT,

        /** The TIMEOU t_ activate. */
        TIMEOUT_ACTIVATE,

        /** The TIMEOU t_ shutdown. */
        TIMEOUT_SHUTDOWN,
        /** Hard ceiling on how long a wormhole may stay open, however often it is re-dialled. */
        MAX_OPEN_SECONDS,

        /** The BUIL d_ restrictio n_ enabled. */
        BUILD_RESTRICTION_ENABLED,

        /** The BUIL d_ restrictio n_ grou p_ one. */
        BUILD_RESTRICTION_GROUP_ONE,

        /** The BUIL d_ restrictio n_ grou p_ two. */
        BUILD_RESTRICTION_GROUP_TWO,

        /** The BUIL d_ restrictio n_ grou p_ three. */
        BUILD_RESTRICTION_GROUP_THREE,

        /** The US e_ cooldow n_ enabled. */
        USE_COOLDOWN_ENABLED,

        /** The US e_ cooldow n_ grou p_ one. */
        USE_COOLDOWN_GROUP_ONE,

        /** The US e_ cooldow n_ grou p_ two. */
        USE_COOLDOWN_GROUP_TWO,

        /** The US e_ cooldow n_ grou p_ three. */
        USE_COOLDOWN_GROUP_THREE,

        /** The HELP SUPPORT DISABLE. */
        HELP_SUPPORT_DISABLE,

        /** Restrict teleportation to same-world gates only. */
        SAME_WORLD_ONLY,

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
        /** Which way the transport flash runs: TOP_DOWN or BOTTOM_UP. */
        RING_FLASH_DIRECTION,
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
        RING_SOUNDS_ENABLED,
        RING_SOUND_VOLUME,
        RING_SOUND_OPEN,
        RING_SOUND_RING,
        RING_SOUND_FLASH,
        RING_SOUND_CLOSE,
        RING_SOUND_REFUSED,
        /** Whether to append newly-seen shape palettes to config.yml automatically. */
        GATE_MATERIAL_GROUPS_AUTODISCOVER,
        /** Whether economy (Vault) integration is enabled. */
        ECONOMY_ENABLED,
        /** Cost in currency units charged to use (walk through) a gate. 0 = free. */
        ECONOMY_USE_COST,
        /** Cost in currency units charged to build a gate. 0 = free. */
        ECONOMY_BUILD_COST
    }

    /**
     * The Enum StringTypes.
     */
    public static enum MessageStrings
    {

        /** The error header. */
        errorHeader("\u00A73:: \u00A75error \u00A73:: \u00A77"),

        /** The normal header. */
        normalHeader("\u00A73:: \u00A77"),

        /** The permission no. */
        permissionNo(errorHeader + "You lack the permissions to do this."),

        /** The target is self. */
        targetIsSelf(errorHeader + "Can't dial own gate without solar flare"),

        /** The target invalid. */
        targetInvalid(errorHeader + "Invalid gate target."),

        /** The target is active. */
        targetIsActive(errorHeader + "Target gate is currently active."),

        /** The gate not active. */
        gateNotActive(errorHeader + "No gate activated to dial."),

        /** The gate remove active. */
        gateRemoveActive(errorHeader + "Gate remotely activated."),

        /** The gate shutdown. */
        gateShutdown(normalHeader + "Gate successfully shutdown."),

        /** The gate activated. */
        gateActivated(normalHeader + "Gate successfully activated."),

        /** The gate deactivated. */
        gateDeactivated(normalHeader + "Gate successfully deactivated."),

        /** The gate dialed. */
        gateConnected(normalHeader + "Stargates connected."),

        /** The construct success. */
        constructSuccess(normalHeader + "Gate successfully constructed."),

        /** The construct name invalid. */
        constructNameInvalid(errorHeader + "Gate name invalid: "),

        /** The construct name too long. */
        constructNameTooLong(errorHeader + "Gate name too long: "),

        /** The construct name taken. */
        constructNameTaken(errorHeader + "Gate name already taken: "),

        /** The request invalid. */
        requestInvalid(errorHeader + "Invalid Request"),

        /** The gate not specified. */
        gateNotSpecified(errorHeader + "No gate name specified."),

        /** The player build count restricted. */
        playerBuildCountRestricted(errorHeader + "You are at your max number of built gates."),

        /** The player use cooldown restricted. */
        playerUseCooldownRestricted(errorHeader + "You must wait longer before using a stargate."),

        /** The player use cooldown wait time. */
        playerUseCooldownWaitTime(errorHeader + "Current Wait (in seconds): "),

        /** Player recently arrived at gate. */
        playerRecentArrival(errorHeader + "You can't enter an incoming wormhole"),

        /** Insufficient funds message. */
        economyInsufficientFunds(errorHeader + "Insufficient funds to use this gate."),

        /** Charged for gate use message (prefix; amount and currency appended at runtime). */
        economyCharged(normalHeader + "Charged "),

        /** Charged for gate build message (prefix; amount and currency appended at runtime). */
        economyBuildCharged(normalHeader + "Gate build cost charged: ");

        /** The m. */
        private final String m;

        /**
         * Instantiates a new string types.
         * 
         * @param message
         *            the message
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
    private static final ConcurrentHashMap<ConfigKeys, Setting> configurations = new ConcurrentHashMap<ConfigKeys, Setting>();

    /**
     * Gets the builds the restriction group one.
     * 
     * @return the builds the restriction group one
     */
    public static int getBuildRestrictionGroupOne()
    {
        return isConfigurationKey(ConfigKeys.BUILD_RESTRICTION_GROUP_ONE)
            ? getSetting(ConfigKeys.BUILD_RESTRICTION_GROUP_ONE).getIntValue()
            : 1;
    }

    /**
     * Gets the builds the restriction group three.
     * 
     * @return the builds the restriction group three
     */
    public static int getBuildRestrictionGroupThree()
    {
        return isConfigurationKey(ConfigKeys.BUILD_RESTRICTION_GROUP_THREE)
            ? getSetting(ConfigKeys.BUILD_RESTRICTION_GROUP_THREE).getIntValue()
            : 3;
    }

    /**
     * Gets the builds the restriction group two.
     * 
     * @return the builds the restriction group two
     */
    public static int getBuildRestrictionGroupTwo()
    {
        return isConfigurationKey(ConfigKeys.BUILD_RESTRICTION_GROUP_TWO)
            ? getSetting(ConfigKeys.BUILD_RESTRICTION_GROUP_TWO).getIntValue()
            : 2;
    }


    /**
     * Gets the configurations.
     * 
     * @return the configurations
     */
    protected static ConcurrentHashMap<ConfigKeys, Setting> getConfigurations()
    {
        return configurations;
    }

    /**
     * Gets the Help plugin support status.
     * 
     * @return true, if Help plugin support is disabled.
     */
    public static boolean getHelpSupportDisable()
    {
        Setting hsd;
        if ((hsd = ConfigManager.getConfigurations().get(ConfigKeys.HELP_SUPPORT_DISABLE)) != null)
        {
            return hsd.getBooleanValue();
        }
        else
        {
            return false;
        }
    }

    /**
     * Get Log Level setting from ConfigKeys. Return sane Level value.
     * Return default value if key is missing or broken.
     * 
     * @return the log level
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
     * Set the runtime Permissions support disable flag. This updates the in-memory configuration
     * map; persisting to disk requires writing config.yml separately.
     */
    public static void setPermissionsSupportDisable(final boolean disabled)
    {
        configurations.put(ConfigKeys.PERMISSIONS_SUPPORT_DISABLE, new Setting(ConfigKeys.PERMISSIONS_SUPPORT_DISABLE, disabled, "Permissions support disabled (runtime)", "WormholeXTreme"));
    }

    /**
     * Gets the setting.
     * 
     * @param configKey
     *            the config key
     * @return the setting
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
     * Get Timeout Shutdown setting from ConfigKeys.
     * Return default value if key is missing or broken.
     * 
     * @return Timeout in seconds.
     */
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
        return Math.max(30, intSetting(ConfigKeys.RING_COUNTDOWN_TICKS, 60));
    }

    /**
     * How long a pair refuses to fire after a cycle.
     *
     * @return cooldown in ticks
     */
    public static int getRingCooldownTicks()
    {
        return Math.max(0, intSetting(ConfigKeys.RING_COOLDOWN_TICKS, 1200));
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
     * Which way the transport flash runs through the stack.
     *
     * <p>This is the <em>departure</em> sweep. The arrival that follows the swap always runs
     * the other way, so the two mirror each other rather than being set separately — two
     * settings could be pointed the same way, and then the landing would look like a second
     * departure.
     *
     * <p>Top down by default, which is how the show does it.
     *
     * @return the flash direction
     */
    public static RingFlashDirection getRingFlashDirection()
    {
        final Setting s = ConfigManager.getConfigurations().get(ConfigKeys.RING_FLASH_DIRECTION);
        try
        {
            return RingFlashDirection.valueOf(
                String.valueOf(s == null ? "TOP_DOWN" : s.getStringValue()).toUpperCase());
        }
        catch (final RuntimeException e)
        {
            return RingFlashDirection.TOP_DOWN;
        }
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
        return Math.max(com.wormhole_xtreme.wormhole.model.ring.Ring.MIN_CEILING_DROP,
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
                String.valueOf(s == null ? "PRIVATE" : s.getStringValue()).toUpperCase());
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
                String.valueOf(s == null ? "CONCURRENT" : s.getStringValue()).toUpperCase());
        }
        catch (final RuntimeException e)
        {
            return RingStyle.CONCURRENT;
        }
    }

    /**
     * Fallback material for the travelling rings.
     *
     * <p>Normally unused: a ring keeps whatever slab it was laid in. This only answers when
     * the template could not say.
     *
     * @return the fallback slab material
     */
    public static Material getRingDefaultMaterial()
    {
        return materialSetting(ConfigKeys.RING_DEFAULT_MATERIAL, Material.STONE_SLAB);
    }

    /**
     * What the countdown lights are made of.
     *
     * @return the light material
     */
    public static Material getRingDefaultLight()
    {
        return materialSetting(ConfigKeys.RING_DEFAULT_LIGHT, Material.GLOWSTONE);
    }

    /**
     * What a ring turns to as the transport light passes through it.
     *
     * <p>Matches the pad light by default, so an untouched ring reads as one effect rather
     * than two. Setting them apart is what makes the transport its own moment.
     *
     * @return the flash material
     */
    public static Material getRingDefaultFlash()
    {
        return materialSetting(ConfigKeys.RING_DEFAULT_FLASH, getRingDefaultLight());
    }

    /**
     * Reads an int setting, falling back when it is missing.
     *
     * @param key
     *            which setting
     * @param fallback
     *            what to use when it is absent
     * @return the value
     */
    private static int intSetting(final ConfigKeys key, final int fallback)
    {
        final Setting s = ConfigManager.getConfigurations().get(key);
        return (s != null) ? s.getIntValue() : fallback;
    }

    /**
     * Reads a material setting by name, falling back when it is missing or unknown.
     *
     * @param key
     *            which setting
     * @param fallback
     *            what to use when it cannot be read
     * @return the material
     */
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
     * @return the volume
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
        final java.util.List<com.wormhole_xtreme.wormhole.model.MaterialGroup> groups)
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
        return (s != null) ? s.getBooleanValue() : true;
    }

    /**
     * Gets the use cooldown group one.
     * 
     * @return the use cooldown group one
     */
    public static int getUseCooldownGroupOne()
    {
        return isConfigurationKey(ConfigKeys.USE_COOLDOWN_GROUP_ONE)
            ? getSetting(ConfigKeys.USE_COOLDOWN_GROUP_ONE).getIntValue()
            : 120;
    }

    /**
     * Gets the use cooldown group three.
     * 
     * @return the use cooldown group three
     */
    public static int getUseCooldownGroupThree()
    {
        return isConfigurationKey(ConfigKeys.USE_COOLDOWN_GROUP_THREE)
            ? getSetting(ConfigKeys.USE_COOLDOWN_GROUP_THREE).getIntValue()
            : 60;
    }

    /**
     * Gets the use cooldown group two.
     * 
     * @return the use cooldown group two
     */
    public static int getUseCooldownGroupTwo()
    {
        return isConfigurationKey(ConfigKeys.USE_COOLDOWN_GROUP_TWO)
            ? getSetting(ConfigKeys.USE_COOLDOWN_GROUP_TWO).getIntValue()
            : 30;
    }

    /*
     * Get Built in permissions enabled settings from ConfigKeys. Return sane boolean value.
     * Return default value if key is missing or broken.
     */
    /**
     * Gets the wormhole use is teleport.
     * 
     * @return the wormhole use is teleport
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
     * Checks if is builds the restriction enabled.
     * 
     * @return true, if is builds the restriction enabled
     */
    public static boolean isBuildRestrictionEnabled()
    {
        return ConfigManager.getConfigurations().get(ConfigKeys.BUILD_RESTRICTION_ENABLED) != null
            ? ConfigManager.getConfigurations().get(ConfigKeys.BUILD_RESTRICTION_ENABLED).getBooleanValue()
            : false;
    }

    /**
     * Checks if is configuration key.
     * 
     * @param configKey
     *            the config key
     * @return true, if is configuration key
     */
    private static boolean isConfigurationKey(final ConfigKeys configKey)
    {
        return getConfigurations().containsKey(configKey);
    }

    /**
     * Checks if is use cooldown enabled.
     * 
     * @return true, if is use cooldown enabled
     */
    public static boolean isUseCooldownEnabled()
    {
        return ConfigManager.getConfigurations().get(ConfigKeys.USE_COOLDOWN_ENABLED) != null
            ? ConfigManager.getConfigurations().get(ConfigKeys.USE_COOLDOWN_ENABLED).getBooleanValue()
            : false;
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
     * Sets the builds the restriction enabled.
     * 
     * @param b
     *            the new builds the restriction enabled
     */
    public static void setBuildRestrictionEnabled(final boolean b)
    {
        ConfigManager.setConfigValue(ConfigKeys.BUILD_RESTRICTION_ENABLED, b);
    }

    /**
     * Sets the builds the restriction group one.
     * 
     * @param count
     *            the new builds the restriction group one
     */
    public static void setBuildRestrictionGroupOne(final int count)
    {
        setConfigValue(ConfigKeys.BUILD_RESTRICTION_GROUP_ONE, count);
    }

    /**
     * Sets the builds the restriction group three.
     * 
     * @param count
     *            the new builds the restriction group three
     */
    public static void setBuildRestrictionGroupThree(final int count)
    {
        setConfigValue(ConfigKeys.BUILD_RESTRICTION_GROUP_THREE, count);
    }

    /**
     * Sets the builds the restriction group two.
     * 
     * @param count
     *            the new builds the restriction group two
     */
    public static void setBuildRestrictionGroupTwo(final int count)
    {
        setConfigValue(ConfigKeys.BUILD_RESTRICTION_GROUP_TWO, count);
    }

    /**
     * Sets the config value.
     * 
     * @param key
     *            the key
     * @param value
     *            the value
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
     * Sets the use cooldown group one.
     * 
     * @param time
     *            the new use cooldown group one
     */
    public static void setUseCooldownGroupOne(final int time)
    {
        setConfigValue(ConfigKeys.USE_COOLDOWN_GROUP_ONE, time);
    }

    /**
     * Sets the use cooldown group three.
     * 
     * @param time
     *            the new use cooldown group three
     */
    public static void setUseCooldownGroupThree(final int time)
    {
        setConfigValue(ConfigKeys.USE_COOLDOWN_GROUP_THREE, time);
    }

    /**
     * Sets the use cooldown group two.
     * 
     * @param time
     *            the new use cooldown group two
     */
    public static void setUseCooldownGroupTwo(final int time)
    {
        setConfigValue(ConfigKeys.USE_COOLDOWN_GROUP_TWO, time);
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
