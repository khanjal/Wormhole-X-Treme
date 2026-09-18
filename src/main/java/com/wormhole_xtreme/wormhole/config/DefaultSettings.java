package com.wormhole_xtreme.wormhole.config;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;

/**
 * Every setting the plugin ships, in the order {@code config.yml} lists them.
 *
 * <p>Grouped, because there are over eighty of them and a flat list of that length is something
 * an operator scrolls rather than reads. The groups are the order and the headings at once, so
 * there is one list to keep in step rather than two: {@link #config} is flattened from them and
 * is what the rest of the plugin reads.
 *
 * <p>Descriptions are one or two sentences. They say what an operator has to know to set the
 * thing; why it works the way it does belongs in {@code docs/} and the changelog, which is where
 * it already is. A comment that runs to a dozen wrapped lines in the file they are editing is
 * not documentation, it is a wall.
 *
 * <p>Based on class "SettingsList" from MinecartMania by Afforess.
 */
class DefaultSettings
{
    /** Static helpers only; never instantiated. */
    private DefaultSettings()
    {
    }

    /**
     * The config-file section every setting below belongs to.
     *
     * <p>Named once because it is repeated on every entry, and because a typo in one of them
     * would file that setting under a section nothing reads.
     */
    private static final String SECTION = "WormholeXTreme";

    /** What every sound setting in a group has in common, said once instead of six times. */
    private static final String SOUNDS = "Any sound name the client knows works, including one"
        + " from a resource pack. Set any of these to none for silence.";

    /**
     * One heading in {@code config.yml}, and the settings the file lists under it.
     *
     * @param heading
     *            the heading, written as a {@code # --- ... ---} banner
     * @param intro
     *            a line or two under the banner about the group as a whole, or empty for none
     * @param settings
     *            its settings, in the order the file writes them
     */
    record Group(String heading, String intro, List<Setting> settings)
    {
    }

    /**
     * One group, with its settings as varargs.
     *
     * <p>So the list below reads as a list. The record holds a {@link List} rather than an array
     * because a record with an array component compares and prints by identity, which is a trap
     * for whoever first tries to compare two of them.
     *
     * @param heading
     *            the heading, written as a {@code # --- ... ---} banner
     * @param intro
     *            a line or two about the group as a whole, or empty for none
     * @param settings
     *            its settings, in the order the file writes them
     * @return the group
     */
    private static Group group(final String heading, final String intro, final Setting... settings)
    {
        return new Group(heading, intro, List.of(settings));
    }

    /** The groups, in the order {@code config.yml} lists them. */
    static final Group[] groups = {
        group("General", "",
            new Setting(ConfigKeys.LOG_LEVEL, "INFO", "How much the plugin logs: SEVERE, WARNING, INFO, CONFIG, FINE, FINER or FINEST, least to most.", SECTION),
            new Setting(ConfigKeys.PERMISSIONS_SUPPORT_DISABLE, false, "If set to true, Permissions plugin will not be attached to even if available.", SECTION),
            new Setting(ConfigKeys.PERMISSIONS_AUTO_FALLBACK, true, "If true and no Vault provider is detected, automatically fall back to simple permission mode.", SECTION),
            new Setting(ConfigKeys.HELP_SUPPORT_DISABLE, false, "If set to true, Help plugin will not be attached to even if available.", SECTION),
            new Setting(ConfigKeys.PETS_FOLLOW_OWNER, true, "Whether tamed wolves, cats and parrots that are following a player, not sitting, travel with them through gates, rings, beams and mirrors.", SECTION)),

        group("Economy", "Shared by gates and beaming; both need this enabled before any cost applies.",
            new Setting(ConfigKeys.ECONOMY_ENABLED, false, "Enable Vault economy integration. Requires Vault and an economy plugin. When false every cost below is ignored.", SECTION),
            new Setting(ConfigKeys.ECONOMY_USE_COST, 0.0, "Amount charged to a player each time they walk through a gate. 0.0 to disable.", SECTION),
            new Setting(ConfigKeys.ECONOMY_BUILD_COST, 0.0, "Amount charged to a player when they successfully build a new gate. 0.0 to disable.", SECTION)),

        group("Stargates", "",
            new Setting(ConfigKeys.TIMEOUT_ACTIVATE, 30, "Seconds a gate stays activated, waiting to be dialled, before it times out.", SECTION),
            new Setting(ConfigKeys.TIMEOUT_SHUTDOWN, 38, "Seconds a dialled gate stays open before shutting down. 0 keeps it open until something goes through.", SECTION),
            new Setting(ConfigKeys.MAX_OPEN_SECONDS, 300, "Longest a wormhole may stay open, in seconds, however often it is re-dialled. Measured from when it first opened, so a gate re-triggered on a schedule cannot hold it open forever. 0 for no limit.", SECTION),
            new Setting(ConfigKeys.USE_COOLDOWN_ENABLED, false, "Enable Cooldown timers on stargate usage. Timer only activates on passage through wormholes.", SECTION),
            new Setting(ConfigKeys.USE_COOLDOWN_SECONDS, 120, "Seconds a player must wait between gate trips, when the cooldown above is enabled.", SECTION),
            new Setting(ConfigKeys.WORMHOLE_USE_IS_TELEPORT, false, "Whether wormhole.use is needed to travel at all. False lets anyone travel but only permitted players activate a gate; true limits travel too.", SECTION),
            new Setting(ConfigKeys.SAME_WORLD_ONLY, false, "If set to true, players may only teleport through gates whose destination is in the same world.", SECTION),
            new Setting(ConfigKeys.REDSTONE_EXTEND_OPEN_TIME, true, "Whether a redstone signal on an already-open gate pushes its shutdown back. Never past max-open-seconds, so traffic can hold a gate open but not indefinitely.", SECTION),
            new Setting(ConfigKeys.ENTITY_SCAN_INTERVAL_TICKS, 20, "Tick interval for periodic non-player entity scan near gates, at least 5. Higher values reduce server load.", SECTION),
            new Setting(ConfigKeys.GATE_MATERIAL_GROUPS_AUTODISCOVER, true, "When a gate shape uses a frame material no material group claims, add that palette to gate-material-groups automatically. Ambiguous palettes are skipped. Set false to curate the list by hand.", SECTION),
            new Setting(ConfigKeys.GATE_ARRIVAL_SPLASH_TICKS, 20, "Ticks a traveller sees water as they come out of a gate, drawn to that player alone. Raise it if distant trips miss the effect while chunks are still loading; too high and the client starts predicting it is swimming. 0 turns it off.", SECTION),
            new Setting(ConfigKeys.GATE_DIAL_SPIN, true, "Whether a dialling gate shows its inner ring turning: a light travelling round the frame to the top chevron before each chevron locks, alternating direction.", SECTION),
            new Setting(ConfigKeys.GATE_PREVIEW_MINUTES, 10, "Minutes a /wormhole gate build preview stays up after its owner last used a build command. At least 1.", SECTION),
            new Setting(ConfigKeys.GATE_PREVIEW_MAX_BLOCKS, 5000, "Most blocks all build previews on the server may show at once, each one an entity its owner alone sees. Every shipped shape shown once is about 1100. 0 turns previews off.", SECTION)),

        group("Stargate sounds", SOUNDS,
            new Setting(ConfigKeys.GATE_SOUNDS_ENABLED, true, "Whether stargates make any noise. Everything below is ignored when this is false.", SECTION),
            new Setting(ConfigKeys.GATE_SOUND_VOLUME, 1.5, "How loud gate sounds are. Volume also sets audible range: 1.5 carries about twenty-four blocks.", SECTION),
            new Setting(ConfigKeys.GATE_SOUND_ACTIVATE, "block.conduit.activate", "Played as a gate begins to dial.", SECTION),
            new Setting(ConfigKeys.GATE_SOUND_CHEVRON, "block.iron_trapdoor.close", "Played once per chevron as it locks, pitch climbing through the sequence.", SECTION),
            new Setting(ConfigKeys.GATE_SOUND_LOCK, "block.beacon.power_select", "Played with the last chevron as it locks, before the wormhole forms.", SECTION),
            new Setting(ConfigKeys.GATE_SOUND_KAWOOSH, "entity.player.splash.high_speed", "Played once as the wormhole establishes: the heavy splash, pitched down for size.", SECTION),
            new Setting(ConfigKeys.GATE_SOUND_CLOSE, "block.conduit.deactivate", "Played as a wormhole closes.", SECTION),
            new Setting(ConfigKeys.GATE_SOUND_IRIS_CLOSE, "block.iron_door.close", "The iris closing over a gate, pitched down a little since it is a shield rather than a door.", SECTION),
            new Setting(ConfigKeys.GATE_SOUND_IRIS_OPEN, "block.iron_door.open", "The iris opening.", SECTION),
            new Setting(ConfigKeys.GATE_SOUND_AMBIENT, "ambient.underwater.loop", "The soft running water an open wormhole makes while it stands there.", SECTION),
            new Setting(ConfigKeys.GATE_SOUND_AMBIENT_TICKS, 70, "How often the ambient water repeats, in ticks. A little under the sound's own length, so it runs continuously.", SECTION)),

        group("Gate signs", "",
            new Setting(ConfigKeys.SIGN_GLOWING_TEXT, false, "Whether the plugin's own sign text glows. Off, because glow outlines every character and fights the colours below; true is more legible from a distance.", SECTION),
            new Setting(ConfigKeys.SIGN_DIAL_MATCH_MATERIAL, true, "Whether a player-placed dial sign is converted to the gate's own sign material, keeping its text and facing. False leaves the sign exactly as placed.", SECTION),
            new Setting(ConfigKeys.SIGN_COLOR_GATE_NAME, "DARK_AQUA", "Colour of a gate's own name, on both its name sign and its dial sign. Any Bukkit colour name.", SECTION),
            new Setting(ConfigKeys.SIGN_COLOR_NETWORK, "GRAY", "Colour of the network line on a gate's name sign.", SECTION),
            new Setting(ConfigKeys.SIGN_COLOR_OWNER, "GRAY", "Colour of the owner line on a gate's name sign.", SECTION),
            new Setting(ConfigKeys.SIGN_COLOR_SELECTED, "DARK_GREEN", "Colour of the destination currently selected on a dial sign. Keep it distinct from the neighbours either side.", SECTION),
            new Setting(ConfigKeys.SIGN_COLOR_NEIGHBOUR, "GRAY", "Colour of the destinations either side of the selected one on a dial sign.", SECTION)),

        group("Transport rings", "",
            new Setting(ConfigKeys.RING_COUNTDOWN_TICKS, 100, "Ticks a transport ring counts down before it commits. Anything below 30 is read as 30, so stepping clear stays possible.", SECTION),
            new Setting(ConfigKeys.RING_COOLDOWN_TICKS, 600, "Ticks a ring pair refuses to fire again after a cycle. Shared by both ends.", SECTION),
            new Setting(ConfigKeys.RING_DEPLOY_TICKS, 2, "Ticks between frames of the ring deploy and retract animations.", SECTION),
            new Setting(ConfigKeys.RING_SETTLE_TICKS, 20, "Ticks the fully deployed ring stack stands still before the teleport fires.", SECTION),
            new Setting(ConfigKeys.RING_FLASH_TICKS, 3, "Ticks each ring stays lit as the transport flash runs through the stack.", SECTION),
            new Setting(ConfigKeys.RING_LIGHTS_LINGER_TICKS, 20, "Ticks the ring pad stays lit after the last ring has sunk back into it.", SECTION),
            new Setting(ConfigKeys.RING_HOLD_TICKS, 20, "Ticks the ring stack stands still once the light has finished, before it retracts.", SECTION),
            new Setting(ConfigKeys.RING_OUTLINE_ON_REFUSAL, true, "Briefly light a ring's pattern for a player it turns away, so they can see where it is. Idle rings are invisible.", SECTION),
            new Setting(ConfigKeys.RING_OUTLINE_TICKS, 40, "How long that outline stays visible, in ticks.", SECTION),
            new Setting(ConfigKeys.RING_REACH, 4, "Block layers of passenger volume, from the ring plane into the room, at least 2. Matters most for ceiling rings.", SECTION),
            new Setting(ConfigKeys.RING_MIN_SEPARATION, 8, "Required distance between ring anchors, in blocks. Overlap is refused regardless of this.", SECTION),
            new Setting(ConfigKeys.RING_MAX_LINK_DISTANCE, 256, "Furthest apart the two ends of a ring pair may be on the ground, in blocks. 256 is sixteen chunks; gates are the long-haul option. 0 for no limit.", SECTION),
            new Setting(ConfigKeys.RING_MAX_LINK_HEIGHT, 384, "Furthest apart the two ends of a ring pair may be in height, in blocks. 384 is the full world height, so bedrock to build limit is always allowed. 0 for no limit.", SECTION),
            new Setting(ConfigKeys.RING_MAX_CEILING_DROP, 10, "How far below its plane a ceiling ring will look for the floor, in blocks. Its rings stack up from there, so it needs a floor near enough to reach.", SECTION),
            new Setting(ConfigKeys.RING_MAX_PAIRS_PER_PLAYER, 10, "How many ring pairs one player may own. 0 for no limit.", SECTION),
            new Setting(ConfigKeys.RING_DEFAULT_ACCESS, "PRIVATE", "What a newly built ring pair starts as: PUBLIC or PRIVATE.", SECTION),
            new Setting(ConfigKeys.RING_DEFAULT_STYLE, "CONCURRENT", "How a ring stack deploys: CONCURRENT (all at once) or SEQUENTIAL (one at a time).", SECTION),
            new Setting(ConfigKeys.RING_DEFAULT_MATERIAL, "SMOOTH_STONE_SLAB", "Fallback ring material, used only when the slab a ring was built from cannot be read. SMOOTH_STONE_SLAB is the plain stone slab, not STONE_SLAB, which is the rougher one.", SECTION),
            new Setting(ConfigKeys.RING_DEFAULT_LIGHT, "REDSTONE_LAMP", "What the ring pad lights up as while it is working.", SECTION),
            new Setting(ConfigKeys.RING_DEFAULT_FLASH, "REDSTONE_LAMP", "What a ring turns to as the transport light passes through it. Set it apart from the pad light to make the transport its own moment.", SECTION)),

        group("Transport ring sounds", SOUNDS,
            new Setting(ConfigKeys.RING_SOUNDS_ENABLED, true, "Whether rings make any noise. Everything below is ignored when this is false.", SECTION),
            new Setting(ConfigKeys.RING_SOUND_VOLUME, 1.0, "How loud ring sounds are. Volume also sets audible range: 1.0 is heard about sixteen blocks away.", SECTION),
            new Setting(ConfigKeys.RING_SOUND_OPEN, "block.beacon.activate", "Played at both ends as the pad opens and the countdown starts.", SECTION),
            new Setting(ConfigKeys.RING_SOUND_RING, "block.piston.extend", "Played once per ring as it leaves the pad and again as it returns, pitch climbing going out and falling coming back.", SECTION),
            new Setting(ConfigKeys.RING_SOUND_FLASH, "block.beacon.power_select", "Played at both ends at the moment of transport.", SECTION),
            new Setting(ConfigKeys.RING_SOUND_CLOSE, "block.beacon.deactivate", "Played at both ends as the pad closes.", SECTION),
            new Setting(ConfigKeys.RING_SOUND_REFUSED, "block.note_block.bass", "Played to a player a ring turns away, wherever they are standing. Heard by them alone.", SECTION)),

        group("Beaming", "",
            new Setting(ConfigKeys.BEAM_ENVELOP_TICKS, 12, "How long, in ticks, the glow gathers at body height before opening into the departure column.", SECTION),
            new Setting(ConfigKeys.BEAM_VANISH_AT_STEP, 6, "How far into the envelope, in ticks, the traveller vanishes. Clamped inside it at run time.", SECTION),
            new Setting(ConfigKeys.BEAM_RISE_TICKS, 18, "How long, in ticks, the column rises and departs once the envelope opens into it.", SECTION),
            new Setting(ConfigKeys.BEAM_TELEPORT_AT_STEP, 12, "How far into the rise, in ticks, the real teleport fires. Clamped inside the rise at run time.", SECTION),
            new Setting(ConfigKeys.BEAM_DESCEND_TICKS, 20, "How long, in ticks, the column takes to descend into place at the destination.", SECTION),
            new Setting(ConfigKeys.BEAM_FADE_TICKS, 8, "How long, in ticks, the column takes to fade out once it has deposited the traveller.", SECTION),
            new Setting(ConfigKeys.BEAM_USE_COOLDOWN_ENABLED, false, "Whether beam travel has a per-player cooldown at all.", SECTION),
            new Setting(ConfigKeys.BEAM_USE_COOLDOWN_SECONDS, 120, "Seconds a player must wait between beams, when the cooldown above is enabled.", SECTION),
            new Setting(ConfigKeys.BEAM_ECONOMY_USE_COST, 0.0, "Amount charged to a player each time they beam. 0.0 to disable; economy-enabled must also be true.", SECTION)),

        group("Beaming sounds", SOUNDS,
            new Setting(ConfigKeys.BEAM_SOUNDS_ENABLED, true, "Whether beaming makes any noise. Everything below is ignored when this is false.", SECTION),
            new Setting(ConfigKeys.BEAM_SOUND_VOLUME, 1.0, "How loud beam sounds are. Volume also sets audible range: 1.0 is heard about sixteen blocks away.", SECTION),
            new Setting(ConfigKeys.BEAM_SOUND_CHARGE, "block.respawn_anchor.charge", "Played the instant a beam sequence starts.", SECTION),
            new Setting(ConfigKeys.BEAM_SOUND_DEPART, "entity.enderman.teleport", "Played the instant the real teleport fires, partway through the rise.", SECTION),
            new Setting(ConfigKeys.BEAM_SOUND_ARRIVE, "entity.shulker.teleport", "Played once the column finishes descending at the destination.", SECTION)),

        group("Quantum mirrors", "",
            new Setting(ConfigKeys.MIRROR_PER_WORLD_LIMIT, 1, "How many mirrors one world may hold. 1 by default: a mirror is the door into its world, and a right-click scrolls through the mirrors of every other. 0 for no limit.", SECTION),
            new Setting(ConfigKeys.MIRROR_PROXIMITY_DISTANCE, 16, "How close a player must be, in blocks, for a mirror's banner to give way to the room it shows, and how near they must stay for it to stay on.", SECTION),
            new Setting(ConfigKeys.MIRROR_PROXIMITY_TICKS, 20, "How often the proximity sweep runs, in ticks. 20 is once a second. Mirrors whose world or chunk is not loaded are skipped.", SECTION),
            new Setting(ConfigKeys.MIRROR_VIEW_DEPTH, 160, "How far from a mirror's opening its room is drawn, 4 to 160; past it this world shows through. 160 is ten chunks, about as far as a server sends. Lower it for a mirror onto somewhere small, or to make one smoother to come to and leave. Changing it never retakes a capture. Above 160 is read as 160.", SECTION),
            new Setting(ConfigKeys.MIRROR_FOG_AT_DEPTH, false, "Whether a mirror pulls a viewer's own fog in to where its room ends, so the far edge is fog rather than this world showing past it. Paper only, and a radius round the player rather than a direction. Lower mirror-view-depth first: there is nothing to gain at 160.", SECTION),
            new Setting(ConfigKeys.MIRROR_APPROACH_MESSAGE, true, "Whether a mirror names itself and the world it opens onto above the hotbar, while a player looks at it from within about six blocks. Only mirrors that go somewhere say anything.", SECTION))
    };

    /**
     * Every setting, flattened from {@link #groups}.
     *
     * <p>What the rest of the plugin reads. The groups are the single list: this one is derived
     * from them, so a setting cannot be in the file's order without being in a group, and there
     * is no second place to forget to add it.
     */
    static final Setting[] config = flatten();

    /** The group each heading-opening key starts, so the writer knows where to put a banner. */
    private static final Map<ConfigKeys, Group> OPENS = opens();

    /**
     * The group this key opens, if it is the first setting of one.
     *
     * @param key
     *            the setting about to be written
     * @return its group when it opens one, or null when it is in the middle of one
     */
    static Group groupAt(final ConfigKeys key)
    {
        return OPENS.get(key);
    }

    /** @return every setting, in group order */
    private static Setting[] flatten()
    {
        final List<Setting> all = new ArrayList<>();
        for (final Group group : groups)
        {
            all.addAll(group.settings());
        }
        return all.toArray(new Setting[0]);
    }

    /** @return the first key of each group, mapped to that group */
    private static Map<ConfigKeys, Group> opens()
    {
        final Map<ConfigKeys, Group> first = new EnumMap<>(ConfigKeys.class);
        for (final Group group : groups)
        {
            if (!group.settings().isEmpty())
            {
                first.put(group.settings().get(0).getName(), group);
            }
        }
        return first;
    }
}
