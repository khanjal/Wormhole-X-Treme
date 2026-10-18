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
     * @param subGroups
     *            its settings in runs, each under its own lighter heading; only the first may
     *            have none
     */
    record Group(String heading, String intro, List<SubGroup> subGroups)
    {
        /** @return every setting in the group, in the order the file writes them */
        List<Setting> settings()
        {
            final List<Setting> all = new ArrayList<>();
            for (final SubGroup subGroup : subGroups)
            {
                all.addAll(subGroup.settings());
            }
            return all;
        }
    }

    /**
     * A run of settings inside a group, under a {@code # -- ... --} heading of its own.
     *
     * @param heading
     *            the sub-heading, or empty for the settings that open a group before any
     * @param settings
     *            its settings, in the order the file writes them
     */
    record SubGroup(String heading, List<Setting> settings)
    {
    }

    /**
     * A group with no sub-groups, its settings as varargs.
     *
     * <p>So the list below reads as a list. The records hold a {@link List} rather than an array
     * because a record with an array component compares and prints by identity, which is a trap
     * for whoever first tries to compare two of them.
     *
     * @param heading
     *            the heading, written as a {@code # --- ... ---} banner
     * @param intro
     *            a line or two about the group as a whole, or empty for none
     * @param settings
     *            its settings, in the order the file writes them
     */
    private static Group group(final String heading, final String intro, final Setting... settings)
    {
        return new Group(heading, intro, List.of(new SubGroup("", List.of(settings))));
    }

    /**
     * A group split into sub-groups.
     *
     * @param heading
     *            the heading, written as a {@code # --- ... ---} banner
     * @param intro
     *            a line or two about the group as a whole, or empty for none
     * @param subGroups
     *            its sub-groups, in the order the file writes them
     */
    private static Group group(final String heading, final String intro, final SubGroup... subGroups)
    {
        return new Group(heading, intro, List.of(subGroups));
    }

    /**
     * One sub-group, with its settings as varargs.
     *
     * @param heading
     *            the sub-heading, or empty for none
     * @param settings
     *            its settings, in the order the file writes them
     */
    private static SubGroup sub(final String heading, final Setting... settings)
    {
        return new SubGroup(heading, List.of(settings));
    }

    /** The groups, in the order {@code config.yml} lists them. */
    static final Group[] groups = {
        group("General", "",
            new Setting(ConfigKeys.LOG_LEVEL, "INFO", "How much the plugin logs: SEVERE, WARNING, INFO, CONFIG, FINE, FINER or FINEST, least to most.", SECTION),
            new Setting(ConfigKeys.PERMISSIONS_SUPPORT_DISABLE, false, "If true, gates ignore permission nodes even when a Vault permission provider is installed: anyone may use, dial and travel by gate; only operators may build one; and removing, configuring or going to one with /wormhole go is left to operators and its owner.", SECTION),
            new Setting(ConfigKeys.PETS_FOLLOW_OWNER, true, "Whether tamed wolves, cats and parrots that are following a player, not sitting, travel with them through gates, rings, beams and mirrors.", SECTION),
            new Setting(ConfigKeys.CLICKABLE_CHAT, true, "Whether a command the plugin names in chat can be clicked to run it, with hover text saying what it does. False sends plain text, as servers without Paper or Spigot do anyway.", SECTION)),

        group("Stargates", "",
            sub("Opening and closing",
                new Setting(ConfigKeys.TIMEOUT_ACTIVATE, 30, "Seconds a gate stays activated, waiting to be dialled, before it times out.", SECTION),
                new Setting(ConfigKeys.TIMEOUT_SHUTDOWN, 38, "Seconds a dialled gate stays open before shutting down. 0 keeps it open until something goes through.", SECTION),
                new Setting(ConfigKeys.MAX_OPEN_SECONDS, 300, "Longest a wormhole may stay open, in seconds, however often it is re-dialled. Measured from when it first opened, so a gate re-triggered on a schedule cannot hold it open forever. 0 for no limit.", SECTION),
                new Setting(ConfigKeys.REDSTONE_EXTEND_OPEN_TIME, true, "Whether a redstone signal on an already-open gate pushes its shutdown back. Never past max-open-seconds, so traffic can hold a gate open but not indefinitely.", SECTION)),
            sub("Travel",
                new Setting(ConfigKeys.USE_COOLDOWN_ENABLED, false, "Enable Cooldown timers on stargate usage. Timer only activates on passage through wormholes.", SECTION),
                new Setting(ConfigKeys.USE_COOLDOWN_SECONDS, 120, "Seconds a player must wait between gate trips, when the cooldown above is enabled.", SECTION),
                new Setting(ConfigKeys.WORMHOLE_USE_IS_TELEPORT, false, "Whether wormhole.use is needed to travel at all. False lets anyone travel but only permitted players activate a gate; true limits travel too.", SECTION),
                new Setting(ConfigKeys.SAME_WORLD_ONLY, false, "If set to true, a gate cannot dial a gate in another world, and nothing -- a player, a cart, a mob, an item or an arrow -- crosses between worlds by gate.", SECTION),
                new Setting(ConfigKeys.SHOW_GATE_WELCOME_MESSAGE, false, "Whether a player arriving through a gate is told in chat which gate they arrived at. Gates only: rings and beams name their destination whatever this is set to.", SECTION)),
            sub("Effects",
                new Setting(ConfigKeys.GATE_DIAL_SPIN, "TOP", "How a dialling gate's inner ring light moves before each chevron locks: TOP, CHEVRON, LAP, FILL, PEGASUS, CHASE, UNIVERSE, OVERSHOOT or NONE. Each is described in the gate guide. TOP rests half a second on the top chevron after each lock, and UNIVERSE turns at its own pace, so both dial a little slower.", SECTION),
                new Setting(ConfigKeys.GATE_RIPPLE, false, "Whether an open gate's event horizon ripples, in rings out from its middle: as something goes in or comes out, and now and then while somebody is near. Upright gates only; the gate guide says which show it and how. Off while it is new.", SECTION)),
            sub("Iris",
                new Setting(ConfigKeys.GATE_IRIS_ANIMATION, "sweep", "How an iris crosses its opening: sweep (rings, from the rim in), spiral (a wedge turning outwards), rows (top and bottom in), columns (both sides in), or instant. Only the picture moves; the blocks are placed in one go whichever it is.", SECTION)),
            sub("Building, previews and server load",
                new Setting(ConfigKeys.MIN_GATE_DISTANCE, 0, "Least distance in blocks between two gates in one world, centre to centre. Building, completing or placing a gate closer is refused, before any fare, unless the builder has wormhole.build.mindistance.bypass, which operators have by default. 0 for no limit.", SECTION),
                new Setting(ConfigKeys.GATE_MATERIAL_GROUPS_AUTODISCOVER, true, "When a gate shape uses a frame material no material group claims, add that palette to gate-material-groups automatically. Ambiguous palettes are skipped. Set false to curate the list by hand.", SECTION),
                new Setting(ConfigKeys.GATE_PREVIEW_MINUTES, 10, "Minutes a gate preview, stood up by /wormhole gate build, stays up after its owner last used a gate build or gate preview command. At least 1.", SECTION),
                new Setting(ConfigKeys.GATE_PREVIEW_MAX_BLOCKS, 5000, "Most blocks all build previews on the server may show at once, each one an entity its owner alone sees. Every shipped shape shown once is about 1100. 0 turns previews off.", SECTION))),

        group("Gate views and mirrors", "",
            sub("",
                new Setting(ConfigKeys.VIEW_DEPTH, 160, "How far past its opening a mirror's room, or an open gate's far side, is drawn, 4 to 160; past it this world shows through. 160 is ten chunks, about as far as a server sends. A gate shows the first 32 at once and fills in the rest behind. Lower it for a smoother view.", SECTION)),
            sub("Gate views",
                new Setting(ConfigKeys.GATE_VIEW, "horizon", "What an open gate shows once its kawoosh settles: horizon (the event horizon, as always), behind (where it goes, seen through the horizon), or open (where it goes, with no horizon). Upright gates with a frame round their opening, so not Minimal. An experiment, see #516.", SECTION),
                new Setting(ConfigKeys.GATE_VIEW_FUNNEL, true, "When a gate dials a smaller one, gate-view shows the far side at the end of a tunnel the small gate's size, walled in this gate's portal material, so the view does not show past the small gate's walls and ceiling. False shows the whole view, as a gate the same size would.", SECTION),
                new Setting(ConfigKeys.GATE_VIEW_FUNNEL_LENGTH, 16, "The longest that tunnel may be, 1 to 64 blocks. It is a block longer for each block narrower the far gate is, and one more for the layer behind the opening that is its own size: four from Large to Standard, thirteen from Massive to Standard, sixteen from Grand to Minimal.", SECTION),
                new Setting(ConfigKeys.GATE_VIEW_FUNNEL_MIN_LENGTH, 0, "The shortest that tunnel is, up to gate-view-funnel-length; none while gate-view-funnel is false. Above 0, a gate the same size or bigger is seen down a straight tunnel too, and every tunnel runs straight for this or three fifths of this gate's opening, the longer, before it narrows. 0 for none.", SECTION)),
            sub("Mirror views",
                new Setting(ConfigKeys.MIRROR_PROXIMITY_DISTANCE, 16, "How close a player must be, in blocks, for a mirror's banner to give way to the room it shows, and how near they must stay for it to stay on.", SECTION)),
            sub("Creatures and players in a view",
                new Setting(ConfigKeys.MIRROR_SHOW_ENTITIES, false, "Whether the creatures standing in a mirror's or an open gate's far room show through it, as stand-ins only the viewer sees. mirror-entity-load-radius keeps the far side loaded while watched; players only with mirror-show-players. The stand-ins are real entities, which other plugins count.", SECTION),
                new Setting(ConfigKeys.MIRROR_SHOW_PLAYERS, false, "With mirror-show-entities on too, whether the players in a view's far room show as well: a mannequin from 1.21.9, an armour stand wearing their head before. Off by default: it shows who is at the destination, by their real skin, not a disguise's. Never anyone hidden by hidePlayer; a hide done in packets is not seen.", SECTION),
                new Setting(ConfigKeys.MIRROR_ENTITY_LOAD_RADIUS, 2, "With mirror-show-entities on, how many chunks in front of a watched view's far side are held, as /forceload holds them, so its creatures show at once: 15 at 2, about 63 loaded with the ring round them, which can generate terrain. 0 holds nothing. Read up to 4.", SECTION)),
            sub("Quantum mirrors",
                new Setting(ConfigKeys.MIRROR_PER_WORLD_LIMIT, 1, "How many mirrors one world may hold. 1 by default: a mirror is the door into its world, and a right-click scrolls through the mirrors of every other. 0 for no limit.", SECTION),
                new Setting(ConfigKeys.MIRROR_APPROACH_MESSAGE, true, "Whether a mirror names itself and the world it opens onto above the hotbar, while a player looks at it from within about six blocks. Only mirrors that go somewhere say anything.", SECTION))),

        group("Gate signs", "",
            new Setting(ConfigKeys.SIGN_GLOWING_TEXT, false, "Whether the plugin's own sign text glows. Off, because glow outlines every character and fights the colours below; true is more legible from a distance.", SECTION),
            new Setting(ConfigKeys.SIGN_DIAL_MATCH_MATERIAL, true, "Whether a player-placed dial sign is converted to the gate's own sign material, keeping its text and facing. False leaves the sign exactly as placed.", SECTION),
            new Setting(ConfigKeys.SIGN_COLOR_GATE_NAME, "DARK_AQUA", "Colour of a gate's own name, on both its name sign and its dial sign. One of the sixteen chat colours, such as AQUA, GOLD or LIGHT_PURPLE.", SECTION),
            new Setting(ConfigKeys.SIGN_COLOR_NETWORK, "GRAY", "Colour of the network line on a gate's name sign.", SECTION),
            new Setting(ConfigKeys.SIGN_COLOR_OWNER, "GRAY", "Colour of the owner line on a gate's name sign.", SECTION),
            new Setting(ConfigKeys.SIGN_COLOR_SELECTED, "DARK_GREEN", "Colour of the destination currently selected on a dial sign. Keep it distinct from the neighbours either side.", SECTION),
            new Setting(ConfigKeys.SIGN_COLOR_NEIGHBOUR, "GRAY", "Colour of the destinations either side of the selected one on a dial sign.", SECTION)),

        group("Transport rings", "",
            sub("Timing",
                new Setting(ConfigKeys.RING_COOLDOWN_TICKS, 600, "Ticks a ring pair refuses to fire again after a cycle. Shared by both ends.", SECTION),
                new Setting(ConfigKeys.RING_ANIMATION_SPEED, 1.0, "How fast the rings run their countdown, rise, flash, return and refusal outline: 2.0 is twice as fast, 0.5 half as fast. Read from 0.5 to 2.0; the countdown never drops under thirty ticks, so stepping clear stays possible.", SECTION)),
            sub("Refusal outline",
                new Setting(ConfigKeys.RING_OUTLINE_ON_REFUSAL, true, "Briefly light a ring's pattern for a player it turns away, so they can see where it is. Idle rings are invisible.", SECTION)),
            sub("Placement and limits",
                new Setting(ConfigKeys.RING_REACH, 4, "Block layers of passenger volume, from the ring plane into the room, at least 2. Matters most for ceiling rings.", SECTION),
                new Setting(ConfigKeys.RING_MIN_SEPARATION, 8, "Required distance between ring anchors, in blocks. Overlap is refused regardless of this.", SECTION),
                new Setting(ConfigKeys.RING_MAX_LINK_DISTANCE, 256, "Furthest apart the two ends of a ring pair may be on the ground, in blocks. 256 is sixteen chunks; gates are the long-haul option. 0 for no limit.", SECTION),
                new Setting(ConfigKeys.RING_MAX_CEILING_DROP, 10, "How far below its plane a ceiling ring will look for the floor, in blocks. Its rings stack up from there, so it needs a floor near enough to reach.", SECTION),
                new Setting(ConfigKeys.RING_MAX_PAIRS_PER_PLAYER, 10, "How many ring pairs one player may own. 0 for no limit.", SECTION)),
            sub("New ring pairs",
                new Setting(ConfigKeys.RING_DEFAULT_ACCESS, "PRIVATE", "What a newly built ring pair starts as: PUBLIC or PRIVATE.", SECTION),
                new Setting(ConfigKeys.RING_DEFAULT_STYLE, "CONCURRENT", "How a ring stack deploys: CONCURRENT (all at once) or SEQUENTIAL (one at a time).", SECTION),
                new Setting(ConfigKeys.RING_DEFAULT_LIGHT, "REDSTONE_LAMP", "What the ring pad lights up as while it is working.", SECTION),
                new Setting(ConfigKeys.RING_DEFAULT_FLASH, "GLOWSTONE", "What a ring turns to as the transport light passes through it. Set it apart from the pad light to make the transport its own moment.", SECTION))),

        group("Beaming", "",
            sub("Animation",
                new Setting(ConfigKeys.BEAM_ANIMATION_SPEED, 1.0, "How fast the beam gathers, rises, descends and fades: 2.0 is twice as fast, 0.5 half as fast. Read from 0.5 to 2.0.", SECTION)),
            sub("Cooldown and cost",
                new Setting(ConfigKeys.BEAM_USE_COOLDOWN_ENABLED, false, "Whether beam travel has a per-player cooldown at all.", SECTION),
                new Setting(ConfigKeys.BEAM_USE_COOLDOWN_SECONDS, 120, "Seconds a player must wait between beams, when the cooldown above is enabled.", SECTION),
                new Setting(ConfigKeys.BEAM_ECONOMY_USE_COST, 0.0, "Amount charged to a player each time they beam. 0.0 to disable; economy-enabled must also be true.", SECTION))),

        group("Economy", "Shared by gates and beaming; both need this enabled before any cost applies.",
            new Setting(ConfigKeys.ECONOMY_ENABLED, false, "Enable Vault economy integration. Requires Vault and an economy plugin. When false every cost below is ignored.", SECTION),
            new Setting(ConfigKeys.ECONOMY_USE_COST, 0.0, "Amount charged to a player each time they walk through a gate. 0.0 to disable.", SECTION),
            new Setting(ConfigKeys.ECONOMY_BUILD_COST, 0.0, "Amount charged to a player when they successfully build a new gate. 0.0 to disable.", SECTION)),

        group("Stargate sounds", SOUNDS,
            sub("",
                new Setting(ConfigKeys.GATE_SOUNDS_ENABLED, true, "Whether stargates make any noise. Everything below is ignored when this is false.", SECTION),
                new Setting(ConfigKeys.GATE_SOUND_VOLUME, 1.5, "How loud gate sounds are. Volume also sets audible range: 1.5 carries about twenty-four blocks.", SECTION)),
            sub("Dialling",
                new Setting(ConfigKeys.GATE_SOUND_ACTIVATE, "block.conduit.activate", "Played as a gate begins to dial.", SECTION),
                new Setting(ConfigKeys.GATE_SOUND_CHEVRON, "block.iron_trapdoor.close", "Played once per chevron as it locks, pitch climbing through the sequence.", SECTION),
                new Setting(ConfigKeys.GATE_SOUND_LOCK, "block.beacon.power_select", "Played with the last chevron as it locks, before the wormhole forms.", SECTION)),
            sub("Open wormhole",
                new Setting(ConfigKeys.GATE_SOUND_KAWOOSH, "entity.player.splash.high_speed", "Played once as the wormhole establishes: the heavy splash, pitched down for size.", SECTION),
                new Setting(ConfigKeys.GATE_SOUND_AMBIENT, "ambient.underwater.loop", "The soft running water an open wormhole makes while it stands there.", SECTION),
                new Setting(ConfigKeys.GATE_SOUND_CLOSE, "block.conduit.deactivate", "Played as a wormhole closes.", SECTION)),
            sub("Iris",
                new Setting(ConfigKeys.GATE_SOUND_IRIS_CLOSE, "block.iron_door.close", "The iris closing over a gate, pitched down a little since it is a shield rather than a door.", SECTION),
                new Setting(ConfigKeys.GATE_SOUND_IRIS_OPEN, "block.iron_door.open", "The iris opening.", SECTION))),

        group("Transport ring sounds", SOUNDS,
            new Setting(ConfigKeys.RING_SOUNDS_ENABLED, true, "Whether rings make any noise. Everything below is ignored when this is false.", SECTION),
            new Setting(ConfigKeys.RING_SOUND_VOLUME, 1.0, "How loud ring sounds are. Volume also sets audible range: 1.0 is heard about sixteen blocks away.", SECTION),
            new Setting(ConfigKeys.RING_SOUND_OPEN, "block.beacon.activate", "Played at both ends as the pad opens and the countdown starts.", SECTION),
            new Setting(ConfigKeys.RING_SOUND_RING, "block.piston.extend", "Played once per ring as it leaves the pad and again as it returns, pitch climbing going out and falling coming back.", SECTION),
            new Setting(ConfigKeys.RING_SOUND_FLASH, "block.beacon.power_select", "Played at both ends at the moment of transport.", SECTION),
            new Setting(ConfigKeys.RING_SOUND_CLOSE, "block.beacon.deactivate", "Played at both ends as the pad closes.", SECTION),
            new Setting(ConfigKeys.RING_SOUND_REFUSED, "block.note_block.bass", "Played to a player a ring turns away, wherever they are standing. Heard by them alone.", SECTION)),

        group("Beaming sounds", SOUNDS,
            new Setting(ConfigKeys.BEAM_SOUNDS_ENABLED, true, "Whether beaming makes any noise. Everything below is ignored when this is false.", SECTION),
            new Setting(ConfigKeys.BEAM_SOUND_VOLUME, 1.0, "How loud beam sounds are. Volume also sets audible range: 1.0 is heard about sixteen blocks away.", SECTION),
            new Setting(ConfigKeys.BEAM_SOUND_CHARGE, "block.respawn_anchor.charge", "Played the instant a beam sequence starts.", SECTION),
            new Setting(ConfigKeys.BEAM_SOUND_DEPART, "entity.enderman.teleport", "Played the instant the real teleport fires, partway through the rise.", SECTION),
            new Setting(ConfigKeys.BEAM_SOUND_ARRIVE, "entity.shulker.teleport", "Played once the column finishes descending at the destination.", SECTION)),

        group("Integrations", "Other plugins and services this one can talk to. Each is optional.",
            sub("Protection and logging",
                new Setting(ConfigKeys.COREPROTECT_ENABLED, false, "Log the blocks that building and taking down gates and rings place and remove to CoreProtect, so they can be rolled back. Requires CoreProtect. A running gate's water, levers and iris are not logged.", SECTION),
                new Setting(ConfigKeys.WORLDGUARD_ENABLED, false, "Add WorldGuard region flags wormhole-build and wormhole-use, so a region can refuse gate building and gate use. Requires WorldGuard. Turning it off applies at once; turning it on takes a restart. Both default to allow, and WorldGuard's region bypass exempts.", SECTION)),
            sub("Web maps",
                new Setting(ConfigKeys.DYNMAP_ENABLED, false, "Show gates, rings, public beam destinations and quantum mirrors on a Dynmap web map, each as its own layer, with a line between dialled gates. Requires Dynmap. Players' private beam places are never shown.", SECTION),
                new Setting(ConfigKeys.BLUEMAP_ENABLED, false, "Show gates, rings, public beam destinations and quantum mirrors on a BlueMap web map, each as its own marker set, with a line between dialled gates. Requires BlueMap. Players' private beam places are never shown.", SECTION),
                new Setting(ConfigKeys.SQUAREMAP_ENABLED, false, "Show gates, rings, public beam destinations and quantum mirrors on a squaremap web map, each as its own layer, with a line between dialled gates. Requires squaremap, which runs on Paper. Players' private beam places are never shown.", SECTION),
                new Setting(ConfigKeys.PL3XMAP_ENABLED, false, "Show gates, rings, public beam destinations and quantum mirrors on a Pl3xMap web map, each as its own layer, with a line between dialled gates. Requires Pl3xMap, built for the server's Minecraft version. Players' private beam places are never shown.", SECTION),
                new Setting(ConfigKeys.MAP_SHOW_GATES, true, "Whether gates, and the lines between dialled gates, appear on the web map, as their own layer.", SECTION),
                new Setting(ConfigKeys.MAP_SHOW_RINGS, true, "Whether transport rings appear on the web map, as their own layer.", SECTION),
                new Setting(ConfigKeys.MAP_SHOW_BEAMS, true, "Whether public beam destinations appear on the web map, as their own layer.", SECTION),
                new Setting(ConfigKeys.MAP_SHOW_MIRRORS, true, "Whether quantum mirrors appear on the web map, as their own layer.", SECTION),
                new Setting(ConfigKeys.MAP_SHOW_IRIS_GATES, true, "Whether gates with an iris code appear on the web map. Set false on a PvP server where a gate's location is a secret.", SECTION)),
            sub("Placeholders, metrics and updates",
                new Setting(ConfigKeys.PLACEHOLDERS_ENABLED, false, "Register the %wormhole_...% PlaceholderAPI expansion. Requires PlaceholderAPI. Exposes gates_total, gates_open, gates_owned and nearest_gate.", SECTION),
                new Setting(ConfigKeys.METRICS_ENABLED, true, "Send anonymous usage counts to bstats.org: Minecraft version, server software, and how many gates, rings, beams and mirrors are in use. No names, coordinates or addresses; bStats keeps a random id so it counts each server once. Set false to stop; bStats/config.yml turns it off for every plugin.", SECTION),
                new Setting(ConfigKeys.UPDATE_CHECK, true, "At startup, look online for a newer release and say so in the log and to players with wormhole.update.notify as they join. Sends only the plugin and Minecraft versions; never downloads anything. Set false to stop.", SECTION)))
    };

    /**
     * Every setting, flattened from {@link #groups}.
     *
     * <p>What the rest of the plugin reads. The groups are the single list: this one is derived
     * from them, so a setting cannot be in the file's order without being in a group, and there
     * is no second place to forget to add it.
     */
    static final Setting[] config = flatten();

    /**
     * The settings most servers change, listed at the top of a new {@code config.yml} with the
     * group each is under, in file order.
     */
    static final List<ConfigKeys> START_HERE = List.of(
        ConfigKeys.PETS_FOLLOW_OWNER,
        ConfigKeys.TIMEOUT_SHUTDOWN,
        ConfigKeys.MAX_OPEN_SECONDS,
        ConfigKeys.USE_COOLDOWN_ENABLED,
        ConfigKeys.WORMHOLE_USE_IS_TELEPORT,
        ConfigKeys.SAME_WORLD_ONLY,
        ConfigKeys.GATE_DIAL_SPIN,
        ConfigKeys.RING_MAX_LINK_DISTANCE,
        ConfigKeys.RING_MAX_PAIRS_PER_PLAYER,
        ConfigKeys.ECONOMY_ENABLED,
        ConfigKeys.GATE_SOUNDS_ENABLED,
        ConfigKeys.GATE_SOUND_VOLUME);

    /** The group each heading-opening key starts, so the writer knows where to put a banner. */
    private static final Map<ConfigKeys, Group> OPENS = opens();

    /** The headed sub-group each key starts, so the writer knows where to put a sub-heading. */
    private static final Map<ConfigKeys, SubGroup> SUB_OPENS = subOpens();

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

    /**
     * The headed sub-group this key opens, if it is the first setting of one.
     *
     * @param key
     *            the setting about to be written
     * @return its sub-group when it opens a headed one, or null otherwise
     */
    static SubGroup subGroupAt(final ConfigKeys key)
    {
        return SUB_OPENS.get(key);
    }

    /**
     * The group a setting is listed under.
     *
     * @param key
     *            any setting
     * @return its group, or null if no group lists it
     */
    static Group groupOf(final ConfigKeys key)
    {
        for (final Group group : groups)
        {
            for (final Setting setting : group.settings())
            {
                if (setting.getName() == key)
                {
                    return group;
                }
            }
        }
        return null;
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

    /** @return the first key of each headed sub-group, mapped to that sub-group */
    private static Map<ConfigKeys, SubGroup> subOpens()
    {
        final Map<ConfigKeys, SubGroup> first = new EnumMap<>(ConfigKeys.class);
        for (final Group group : groups)
        {
            for (final SubGroup subGroup : group.subGroups())
            {
                if (!subGroup.heading().isEmpty() && !subGroup.settings().isEmpty())
                {
                    first.put(subGroup.settings().get(0).getName(), subGroup);
                }
            }
        }
        return first;
    }
}
