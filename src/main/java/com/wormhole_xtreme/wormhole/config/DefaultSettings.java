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
        // Build restriction feature removed; permissions handled via Vault/LuckPerms
        new Setting(ConfigKeys.USE_COOLDOWN_ENABLED, false, "Enable Cooldown timers on stargate usage. Timer only activates on passage through wormholes.", "WormholeXTreme"),
        new Setting(ConfigKeys.PERMISSIONS_SUPPORT_DISABLE, false, "If set to true, Permissions plugin will not be attached to evem if available.", "WormholeXTreme"),
        new Setting(ConfigKeys.WORMHOLE_USE_IS_TELEPORT, false, "The wormhole.use (or wormhole.simple.use) permission means that a user can teleport through gate. When false a user will be able to teleport but not activate a gate. When true only users with wormhole.use (or wormhole.simple.use) can even teleport.", "WormholeXTreme"),
        new Setting(ConfigKeys.HELP_SUPPORT_DISABLE, false, "If set to true, Help plugin will not be attached to even if available.", "WormholeXTreme"),
        new Setting(ConfigKeys.WORLDS_SUPPORT_ENABLED, false, "If set to true, the Wormhole X-Treme will offload all of its Chunk and World loading functionality to Wormhole Extreme Worlds.", "WormholeXTreme"),
        new Setting(ConfigKeys.LOG_LEVEL, "INFO", "Log level to use for minecraft logging purposes. Values are SEVERE, WARNING, INFO, CONFIG, FINE, FINER, and FINEST. In order of least to most logging output.", "WormholeXTreme"),
        new Setting(ConfigKeys.ENTITY_SCAN_INTERVAL_TICKS, 20, "Tick interval for periodic non-player entity scan near gates. Higher values reduce server load.", "WormholeXTreme"),
        new Setting(ConfigKeys.STORAGE_BACKEND, "file", "Storage backend to use: file|sqlite|hsqldb|mysql|postgres", "WormholeXTreme"),
        new Setting(ConfigKeys.STORAGE_SQLITE_PATH, "plugins/WormholeXTreme/wormholes.db", "SQLite DB file path when using sqlite backend", "WormholeXTreme"),
        new Setting(ConfigKeys.STORAGE_JDBC_URL, "", "JDBC URL for remote DBs (mysql/postgres). Example: jdbc:postgresql://host:5432/dbname", "WormholeXTreme"),
        new Setting(ConfigKeys.STORAGE_JDBC_USER, "", "JDBC username for remote DBs", "WormholeXTreme"),
        new Setting(ConfigKeys.STORAGE_JDBC_PASSWORD, "", "JDBC password for remote DBs", "WormholeXTreme"),
        new Setting(ConfigKeys.ECONOMY_ENABLED, false, "Enable Vault economy integration. Requires Vault and an economy plugin (e.g. EssentialsX). When false all economy features are disabled regardless of cost settings.", "WormholeXTreme"),
        new Setting(ConfigKeys.ECONOMY_USE_COST, 0.0, "Amount charged to a player each time they walk through a gate. Set to 0.0 to disable use cost.", "WormholeXTreme"),
        new Setting(ConfigKeys.ECONOMY_BUILD_COST, 0.0, "Amount charged to a player when they successfully build a new gate. Set to 0.0 to disable build cost.", "WormholeXTreme")
    };

}

