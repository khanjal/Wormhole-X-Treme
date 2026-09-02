package com.wormhole_xtreme.wormhole.config;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;

/**
 * The Class DefaultSettings.
 * Based on class "SettingsList" from MinecartMania by Afforess.
 */
class DefaultSettings
{

    /** The Constant config. */
    final static Setting[] config = {
        new Setting(ConfigKeys.TIMEOUT_ACTIVATE, 30, "Number of seconds after a gate is activated, but before dialing before timing out.", "WormholeXTreme"),
        new Setting(ConfigKeys.TIMEOUT_SHUTDOWN, 38, "Number of seconds after a gate is dialed before automatically shutdown. With 0 timeout a gate won't shutdown until something goes through the gate.", "WormholeXTreme"),
        new Setting(ConfigKeys.MAX_OPEN_SECONDS, 300, "Longest a wormhole may stay open, in seconds, however often it is re-dialled. Dialling restarts the shutdown timer, so without this a gate re-triggered on a schedule (a minecart over a detector rail, for example) would stay open forever and lock everyone else out. Measured from when the wormhole first opened. Set 0 for no limit.", "WormholeXTreme"),
        // Build restriction feature removed; permissions handled via Vault/LuckPerms
        new Setting(ConfigKeys.USE_COOLDOWN_ENABLED, false, "Enable Cooldown timers on stargate usage. Timer only activates on passage through wormholes.", "WormholeXTreme"),
        new Setting(ConfigKeys.PERMISSIONS_SUPPORT_DISABLE, false, "If set to true, Permissions plugin will not be attached to even if available.", "WormholeXTreme"),
        new Setting(ConfigKeys.PERMISSIONS_AUTO_FALLBACK, true, "If true and no Vault provider is detected, automatically fall back to simple permission mode.", "WormholeXTreme"),
        new Setting(ConfigKeys.WORMHOLE_USE_IS_TELEPORT, false, "The wormhole.use (or wormhole.simple.use) permission means that a user can teleport through gate. When false a user will be able to teleport but not activate a gate. When true only users with wormhole.use (or wormhole.simple.use) can even teleport.", "WormholeXTreme"),
        new Setting(ConfigKeys.HELP_SUPPORT_DISABLE, false, "If set to true, Help plugin will not be attached to even if available.", "WormholeXTreme"),
        new Setting(ConfigKeys.SAME_WORLD_ONLY, false, "If set to true, players may only teleport through gates whose destination is in the same world. Cross-world travel will be blocked.", "WormholeXTreme"),
        new Setting(ConfigKeys.LOG_LEVEL, "INFO", "Log level to use for minecraft logging purposes. Values are SEVERE, WARNING, INFO, CONFIG, FINE, FINER, and FINEST. In order of least to most logging output.", "WormholeXTreme"),
        new Setting(ConfigKeys.ENTITY_SCAN_INTERVAL_TICKS, 20, "Tick interval for periodic non-player entity scan near gates. Higher values reduce server load.", "WormholeXTreme"),
        new Setting(ConfigKeys.RING_COUNTDOWN_TICKS, 60, "Ticks a transport ring counts down before it commits. Below about 20 the abort window stops being real and rings take people walking past.", "WormholeXTreme"),
        new Setting(ConfigKeys.RING_COOLDOWN_TICKS, 1200, "Ticks a ring pair refuses to fire again after a cycle. Shared by both ends.", "WormholeXTreme"),
        new Setting(ConfigKeys.RING_DEPLOY_TICKS, 2, "Ticks between frames of the ring deploy and retract animations.", "WormholeXTreme"),
        new Setting(ConfigKeys.RING_SETTLE_TICKS, 20, "Ticks the fully deployed ring stack stands still before the teleport fires. One second by default.", "WormholeXTreme"),
        new Setting(ConfigKeys.RING_FLASH_TICKS, 3, "Ticks each ring stays lit as the transport flash runs through the stack.", "WormholeXTreme"),
        new Setting(ConfigKeys.RING_FLASH_DIRECTION, "TOP_DOWN", "Which way the departure flash runs through the ring stack: TOP_DOWN or BOTTOM_UP. The arrival sweep always runs the other way.", "WormholeXTreme"),
        new Setting(ConfigKeys.RING_LIGHTS_LINGER_TICKS, 20, "Ticks the ring pad stays lit after the last ring has sunk back into it. One second by default.", "WormholeXTreme"),
        new Setting(ConfigKeys.RING_OUTLINE_ON_REFUSAL, true, "Briefly light a ring's pattern for a player it turns away, so they can see where it is. Idle rings are invisible.", "WormholeXTreme"),
        new Setting(ConfigKeys.RING_OUTLINE_TICKS, 40, "How long that outline stays visible, in ticks.", "WormholeXTreme"),
        new Setting(ConfigKeys.RING_HOLD_TICKS, 20, "Ticks the ring stack stands still once the light has finished, before it retracts.", "WormholeXTreme"),
        new Setting(ConfigKeys.RING_REACH, 4, "Block layers of passenger volume, from the ring plane into the room. Matters most for ceiling rings.", "WormholeXTreme"),
        new Setting(ConfigKeys.RING_MIN_SEPARATION, 8, "Required distance between ring anchors, in blocks. Overlap is refused regardless of this.", "WormholeXTreme"),
        new Setting(ConfigKeys.RING_MAX_LINK_DISTANCE, 0, "Furthest apart the two ends of a ring pair may be, in blocks. Zero means no limit; distance itself costs nothing.", "WormholeXTreme"),
        new Setting(ConfigKeys.RING_MAX_PAIRS_PER_PLAYER, 10, "How many ring pairs one player may own. Zero means no limit.", "WormholeXTreme"),
        new Setting(ConfigKeys.RING_DEFAULT_ACCESS, "PRIVATE", "What a newly built ring pair starts as: PUBLIC or PRIVATE.", "WormholeXTreme"),
        new Setting(ConfigKeys.RING_DEFAULT_STYLE, "CONCURRENT", "How a ring stack deploys: CONCURRENT (all at once) or SEQUENTIAL (one at a time).", "WormholeXTreme"),
        new Setting(ConfigKeys.RING_DEFAULT_MATERIAL, "STONE_SLAB", "Fallback ring material. Normally unused: a ring keeps whatever slab it was laid in.", "WormholeXTreme"),
        new Setting(ConfigKeys.RING_DEFAULT_LIGHT, "GLOWSTONE", "What the ring pad lights up as while it is working.", "WormholeXTreme"),
        new Setting(ConfigKeys.RING_DEFAULT_FLASH, "GLOWSTONE", "What a ring turns to as the transport light passes through it. Set it apart from the pad light to make the transport its own moment.", "WormholeXTreme"),
        new Setting(ConfigKeys.GATE_MATERIAL_GROUPS_AUTODISCOVER, true, "When a gate shape uses a frame material no material group claims, add that palette to gate-material-groups automatically. Only unambiguous palettes are added: if two shapes share a frame material but disagree on their other materials, neither is added. Set false to curate the list by hand.", "WormholeXTreme"),
        new Setting(ConfigKeys.ECONOMY_ENABLED, false, "Enable Vault economy integration. Requires Vault and an economy plugin (e.g. EssentialsX). When false all economy features are disabled regardless of cost settings.", "WormholeXTreme"),
        new Setting(ConfigKeys.ECONOMY_USE_COST, 0.0, "Amount charged to a player each time they walk through a gate. Set to 0.0 to disable use cost.", "WormholeXTreme"),
        new Setting(ConfigKeys.ECONOMY_BUILD_COST, 0.0, "Amount charged to a player when they successfully build a new gate. Set to 0.0 to disable build cost.", "WormholeXTreme")
    };

}

