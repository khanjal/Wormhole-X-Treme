# Changelog

All notable changes to this project are documented in this file.

One line an entry, under the subsystem it belongs to: what changed, and the setting or step an
operator or a player needs. No paragraphs, no why, no limits and no numbers; the detail lives in
[docs/](docs/) beside the code, linked from the entry when it helps. A release nobody can scroll
through is a release nobody reads.

**Releases:**

- [1.11.0 (unreleased)](#1110-unreleased)
- [1.10.0 (2026-10-12)](#1100-2026-10-12)
- [1.9.0 (2026-10-03)](#190-2026-10-03)
- [1.8.1 (2026-09-28)](#181-2026-09-28)
- [1.8.0 (2026-09-26)](#180-2026-09-26)
- [1.7.1 (2026-09-19)](#171-2026-09-19)
- [1.7.0 (2026-09-19)](#170-2026-09-19)
- [1.6.0 (2026-09-16)](#160-2026-09-16)
- [1.5.0 (2026-09-09)](#150-2026-09-09)
- [1.4.0 (2026-09-05)](#140-2026-09-05)
- [1.3.0 (2026-09-03)](#130-2026-09-03)
- [1.2.0 (2026-09-02)](#120-2026-09-02)
- [1.1.0 (2026-09-01)](#110-2026-09-01)
- [1.0.0 (2026-05-14)](#100-2026-05-14)
- [0.x (2011, upstream)](#0x-2011-upstream)

## 1.11.0 (unreleased)

### For server admins

**Added**

- Anonymous usage counts to bStats also say which gate shapes and portal materials are in use, the gate view, ripple, sound and iris settings, and which optional plugins are enabled. Nothing that names a player, gate or place. [Guide](docs/guide/SERVER.md#metrics)

**Changed**

- **`ring-animation-speed` replaces the seven ring timings**: 1.0 is the old timing, 2.0 twice as fast, 0.5 half as fast, and nothing outside that range; the settle, hold and lingering lights can no longer be 0, and are 10 ticks at 2.0.
- **`beam-animation-speed` replaces the six beaming timings** the same way.
- **`view-depth` replaces `gate-view-depth`, `gate-view-full-depth` and `mirror-view-depth`**: one depth for a mirror's room and a gate's view; a file without it takes the shallower of `mirror-view-depth` and `gate-view-full-depth`, so a lowered gate depth (0 meaning its first step) now shallows mirrors too, and a gate's first step is otherwise back at 32.
- **A `config.yml` holding a removed setting still loads**: the line is ignored and left as it is, and the log names them at startup.
- **A `config.yml` still exactly as an earlier release wrote it is replaced** with the new grouped layout and its new defaults, the old one kept as `config.yml.bak`; a value that is not one of those defaults leaves it as it is.
- The comment on `permissions-support-disable` says what it does: gates ignore their permission nodes even with a Vault permission provider installed ([#475](https://github.com/khanjal/Wormhole-X-Treme/issues/475)).

**Removed**

- **`mirror-proximity-ticks`, `entity-scan-interval-ticks` and `gate-sound-ambient-ticks`**: the sweeps keep their old default pace.
- **`gate-iris-step-ticks`, `gate-iris-sweep-max-ticks` and `gate-iris-horizon-ticks`**: an iris crosses as it did by default; `gate-iris-animation` stays.
- **`gate-arrival-splash-ticks`**: the arrival splash always shows, for as long as it did by default.
- **`ring-max-link-height` and `ring-default-material`**: rings reach a whole world's height, and a ring whose slab is unknown is drawn in smooth stone.
- **`permissions-auto-fallback`**: with no Vault permissions provider the simple fallback is always on, so a server on a Bukkit-permissions plugin without Vault that had set it `false` is now in simple mode.
- **`mirror-fog-at-depth`**: a mirror no longer pulls a viewer's fog in, and the code behind it is gone with the `fog` line in `mirror debug`.

### Stargates

**Added**

- Gates the same size, or a small gate dialling a big one, can be seen down a straight tunnel too, and every tunnel runs straight before it narrows, longer for a bigger gate ([#587](https://github.com/khanjal/Wormhole-X-Treme/issues/587)). Off until `gate-view-funnel-min-length` is set. [Details](docs/GATES.md)

**Changed**

- Every message to a player is coloured by one shared palette ([#438](https://github.com/khanjal/Wormhole-X-Treme/issues/438)): `/wx list` shows gate and network names in aqua with grey separators, a transport ring that turns you away opens with `:: error ::` like a gate does, and the lines after a valid design or locked chevrons say what to type next in the same colours, with the optional `[idc=IDC] [net=NET]` part now grey rather than gold.
- Every `gate`, `mirror`, `config` and other admin command answers with `::` or `:: error ::` and the same colours, a gate name it cannot find gets one reply saying `/wormhole list` shows them, and a list of options goes on a line of its own; `/wormhole owner` now reads `Abydos is now owned by Ada.` and `/wormhole remove` `Removed Abydos.` ([#438](https://github.com/khanjal/Wormhole-X-Treme/issues/438)).

**Fixed**

- Behind an open gate, shutting or opening its iris no longer covers your wormhole for a moment before moving the iris beyond it: the wormhole stays in the ring and the iris sweeps in a block further off ([#429](https://github.com/khanjal/Wormhole-X-Treme/issues/429)). [Guide](docs/guide/GATES.md#how-it-arrives)

### Transport rings and beaming

**Changed**

- Every `ring` and `beam` reply opens with `::` or `:: error ::` and colours names, blocks, numbers and what to type, as gates do; usage lines read `Usage: /wormhole ring fire <id>|<world> <x> <y> <z>`, beam names are no longer in quotes, and a beam on cooldown says how long is left in one line ([#438](https://github.com/khanjal/Wormhole-X-Treme/issues/438)).

### Performance

- A mirror's or gate's view no longer stalls the server while somebody looks into it ([#609](https://github.com/khanjal/Wormhole-X-Treme/issues/609)). [Details](docs/MIRRORS.md#how-the-room-is-built)
- A mirror's or gate's view no longer makes the server hitch the first time it is drawn after a restart or a while unwatched: its capture is read off the disk off the main thread, the banner or horizon showing until it is in ([#528](https://github.com/khanjal/Wormhole-X-Treme/issues/528)). [Details](docs/GATES.md#seeing-through-a-gate-experimental)
- Mirror and gate captures are kept in sections with palettes, as the game keeps chunks, so they take less memory and disk and read faster ([#525](https://github.com/khanjal/Wormhole-X-Treme/issues/525)). Older capture files still load and are rewritten when next taken; going back to an earlier release logs each new file as unreadable once and takes that view again. [Details](docs/MIRRORS.md#how-a-capture-is-kept)

### Internals

- Code nothing ran any more is gone: a flat-file settings writer and reader, empty shutdown hooks, and a gate-facing helper nothing called ([#475](https://github.com/khanjal/Wormhole-X-Treme/issues/475)).

## 1.10.0 (2026-10-12)

### For server admins

**Added**

- Gates, rings, public beam destinations and quantum mirrors show on [BlueMap](https://bluemap.bluecolored.de). Off until `bluemap-enabled` is set. [Guide](docs/guide/SERVER.md#bluemap)
- The same marks show on [squaremap](https://modrinth.com/plugin/squaremap). Off until `squaremap-enabled` is set. [Guide](docs/guide/SERVER.md#squaremap)
- The same marks show on [Pl3xMap](https://modrinth.com/plugin/pl3xmap). Off until `pl3xmap-enabled` is set. [Guide](docs/guide/SERVER.md#pl3xmap)
- Mobs in a mirror's or an open gate's far room show through it ([#296](https://github.com/khanjal/Wormhole-X-Treme/issues/296)). Off until `mirror-show-entities` is set; the chunks in front of the far side are held loaded (`mirror-entity-load-radius`). [Details](docs/MIRRORS.md#seeing-creatures)
- Players in that far room show too. Off until `mirror-show-players` is set, and it needs `mirror-show-entities`. The skin is the real one, so leave it off on a server that hides identities.

**Changed**

- A new `config.yml` is laid out commonest first, with a "start here" list and sub-headings ([#586](https://github.com/khanjal/Wormhole-X-Treme/issues/586)). An existing file keeps its own order. [Guide](docs/guide/SERVER.md#configuration)

### Stargates

**Added**

- An open gate's event horizon can ripple ([#579](https://github.com/khanjal/Wormhole-X-Treme/issues/579)). Off until `gate-ripple` is set. [Guide](docs/guide/GATES.md#the-ripple)
- An open gate can show where it goes, as an experiment ([#516](https://github.com/khanjal/Wormhole-X-Treme/issues/516)). Set `gate-view` to `behind` or `open`; the default is `horizon`.
- At `gate-view: open` the horizon clears only for whoever is drawn the view; everyone else sees the gate's own portal material.
- A big gate dialling a smaller one shows the view at the end of a tunnel ([#583](https://github.com/khanjal/Wormhole-X-Treme/issues/583)). On by default; turn off with `gate-view-funnel: false`, and `gate-view-funnel-length` sets the longest tunnel.
- Gate preview actions can be clicked. On by default; `clickable-chat: false` turns it off.
- A player coming out of a gate can be told which gate they arrived at. Off until `show-gate-welcome-message` is set.

**Changed**

- The `pegasus` and `chevron` dial spins have swapped. Set `gate-dial-spin` to the other name to keep the old look.

**Fixed**

- Walking round a gate while its iris opens or closes no longer leaves a block on your side that is not there ([#447](https://github.com/khanjal/Wormhole-X-Treme/issues/447)).
- A build preview's see-through iris no longer shows empty cells part way through a sweep ([#432](https://github.com/khanjal/Wormhole-X-Treme/issues/432)).

### Internals

- The code that draws a far side through an opening moved to `model/window`, no longer named for mirrors (#522).
- A gate's view is drawn from a window source, not as a stand-in mirror.
- The build no longer fails at random with a corrupt-zip error.

[Back to top](#changelog)

## 1.9.0 (2026-10-03)

### For server admins

**Added**

- **[WorldGuard](https://enginehub.org/worldguard) regions can refuse gates**, with two flags:
  `wormhole-build` stops gates being built there, and `wormhole-use` stops them being dialled
  from or travelled through, at either end. A flag only takes away: a player still needs the
  usual permission, and WorldGuard's region bypass gets past the flag. Off until
  `worldguard-enabled` is set; turning it on takes a restart. Rings, beams and mirrors are not covered
  yet. See [the guide](docs/guide/SERVER.md#worldguard).
- **Gates, rings, public beam destinations and quantum mirrors show on a
  [Dynmap](https://www.spigotmc.org/resources/dynmap.274/) web map**, each as its own layer. A
  gate lights up while its wormhole is open, with a line to the gate it joins. Off until
  `dynmap-enabled` is set; `map-show-gates`, `-rings`, `-beams` and `-mirrors` leave a layer
  out, and `map-show-iris-gates: false` hides gates with an iris code. Players' private beam
  places are never shown. 1.21.11 needs Dynmap 3.8, and no Dynmap runs on 26.x yet. See
  [the guide](docs/guide/SERVER.md#dynmap).
- **A newer release is announced at startup**: one console line with where to get it, and a line
  to players with `wormhole.update.notify` (ops) as they join. Nothing is downloaded, and nothing
  is said to a server running something newer. `update-check: false` turns it off.

### Server

- **Tested on Minecraft 1.21.8.** CI builds against Spigot's and Paper's 1.21.8 API and boots a
  Paper 1.21.8 server, as for the other eleven versions.

**Fixed**

- **Every setting changed with `/wormhole config` applies at once, as the guide says.** Ten waited
  for a restart: `gate-sound-ambient-ticks`, `entity-scan-interval-ticks`, `mirror-proximity-ticks`,
  `gate-iris-horizon-ticks`, `ring-reach`, `ring-max-ceiling-drop`, `economy-enabled`,
  `placeholders-enabled`, `permissions-auto-fallback` and `gate-material-groups-autodiscover`.

**Removed**

- **`help-support-disable`**, which nothing read: the Help plugin it turned off is long gone. A line
  for it in `config.yml` is ignored and left where it is.

### Stargates

**Fixed**

- **An item thrown or dispensed into an open gate goes through it.** Most flew across the opening
  between two entity sweeps and landed behind the ring; a shut iris destroys them as before.
- **The build guide takes the frame block in an `[S:C]` chevron, as detection does.** A gate built
  that way was detected but never shown as built, `gate preview needs` named only the chevron
  block, and `gate regen -fill` refused to fill a gate with one.
- **The player who pulls an iris lever sees the iris sweep in, as everyone else does.** Their own
  arm swing redrew the whole iris a tick later, over the sweep.
- **A detector rail wired to a gate dials it on Paper 1.21.11.** Paper reports a rail's press as
  a current of 15 going to 15, which read as no change, so a cart over the rail did nothing.
- **`same-world-only` keeps everything in its own world, not only players on foot.** A minecart
  carried its rider through to the Nether, and a mob, an item or an arrow went too. With it on, a
  gate in another world cannot be dialled, and `/dial` and the dial sign say why. A player walking
  in is told that before any cooldown or iris, and once, not at every block.
- **`/wormhole config` refuses a sign colour that is not one**, and names the sixteen there are.
  `sign-color-gate-name PINK` was accepted, and every sign written after it came out dark aqua.
- **A tipped arrow keeps its effect through a gate, and on Paper a Loyalty trident comes back.** The
  arrow arrived plain, and the trident unenchanted, stuck at the far side. A spectral arrow keeps how long
  it makes its target glow, a thrown potion its contents, and a crossbow firework its stars. On
  Spigot the trident keeps its enchantments but still does not come back, and a firework starts its
  flight over at the far gate.
- **A cart or boat stops at the face of an upright gate's shut iris**, from either side. It used
  to roll half into the iris before it was put back, still with its front inside.

### Transport rings

**Fixed**

- **A ring pair whose slab or pad light this server does not have loads**, drawn in
  `ring-default-material` or lit in `ring-default-light`, and is saved with the name it had. It was
  skipped as unreadable and lost at the next save, and `ring-default-material` was read by nothing.

### Performance

- **A mirror's name above the hotbar is looked for only near a mirror.** Every player in a world
  with one traced a ray each sweep; now only players within a chunk of a mirror's banner do.

### Quantum mirrors

**Fixed**

- **A mirror in a wall more than a block wide keeps its wide far part between wall readings.**
  For four seconds in five it fell back to a half-block one, and `mirror debug` said "wall 0".
- **A view sees six layers into leaves, not through a whole forest.** A capture kept every crown
  in its way; a mirror onto woodland now holds far fewer blocks. Retake one with `mirror set -capture`.
- **Two mirrors made at once on a new server both keep their view after a restart.** One of them
  logged "Could not write mirror capture", and after a restart showed its banner until the view
  was taken again.

### Internals

- **A test fails if Spigot-only API is used outside the classes that isolate it.** CraftBukkit
  has no `spigot()`, `net.md_5` or `org.spigotmc`, and no CI job builds against it.

[Back to top](#changelog)

## 1.8.1 (2026-09-28)

### For server admins

**Added**

- **Rings can be paired and fired from the console and command blocks.**
  `ring build <world> <x1> <y1> <z1> <x2> <y2> <z2>` pairs two laid circles, each named by a block
  inside it, and says where each end puts an arrival; `ring fire <id>` or
  `ring fire <world> <x> <y> <z>` sets a pair off with nobody in it. A player needs
  `wormhole.ring.admin`. A pair built this way has no owner, so it is public.
- **A mirror can be made from the console and command blocks**:
  `mirror create <name> <world> <x> <y> <z>` makes the wall banner there one.
- **Coordinates in `gate build`, `ring build`, `ring fire` and `mirror create` may be `~`**, counted
  from the command block or player that runs them, so a map's command blocks keep working wherever
  it is pasted. Under `/execute`, they still count from whoever ran it.

### Stargates

**Fixed**

- **Nothing goes through a gate until its kawoosh is over.** Players, mounts, mobs, items,
  vehicles and arrows stepping into the opening while the chevrons lock pass through the empty
  frame; the opening no longer shows water before the kawoosh to a player crossing a chunk nearby.
- **A ridden boat, cart or mount goes through a gate or ring on Paper 1.20.4.** It stayed behind
  with its rider aboard while the trip was reported and charged. One that cannot be moved now
  keeps its riders and costs nothing, and a boat or cart's rider pays once it has gone.
- **An item or mob dropped into an opening comes out below the far gate**, not thrown up to six
  blocks out of it: its fall no longer turns into a sideways speed on the way through.

### Quantum mirrors

**Fixed**

- **`mirror create` aimed off a banner no longer says a banner on a post will do.** It asks for a
  banner hung on a wall, which is the only kind `create` accepts. A mirror already on a post can
  still be renamed.

### Transport rings

**Changed**

- **The transport flash defaults to glowstone**, against the lit redstone lamp pad. A server with a
  `config.yml` keeps `ring-default-flash: REDSTONE_LAMP` until it is changed there, and existing
  rings keep theirs until `/wormhole ring edit [id] flash glowstone` or `reset`.

### Travel

**Fixed**

- **A following pet comes with its owner into another world** by gate, beam, ring or mirror,
  where it used to stay behind.

### Internals

- The travel boot test now also rides a ring pair with an item and a pig, and makes a mirror from
  the console and finds it again after a restart, on Paper 1.20.4, 1.21.11 and 26.3.

[Back to top](#changelog)

## 1.8.0 (2026-09-26)

### For server admins

**Added**

- **Anonymous usage counts go to [bStats](https://bstats.org)**: Minecraft version, server
  software, and how many gates, rings, beams and mirrors, in ranges. No names, coordinates or
  addresses. On by default; `metrics-enabled: false` stops it, and `plugins/bStats/config.yml`
  stops it for every plugin. See [the guide](docs/guide/SERVER.md#metrics).
- **Gate and ring construction can be logged to [CoreProtect](https://www.spigotmc.org/resources/coreprotect.8631/)**,
  so it can be rolled back: signs, levers, `gate remove -destroy`, `regen -fill`,
  `gate preview -place`, and the slabs `ring create` takes and `ring remove` gives back. Off
  until `coreprotect-enabled` is set; a running gate is not logged. See
  [the guide](docs/guide/SERVER.md#coreprotect).
- **Gates can be built and dialled from the console, command blocks and scripts.**
  `gate build <shape> <name> <world> <x> <y> <z> <facing>` builds and completes one with its DHD
  button on that block, and says where its opening and arrivals are; `gate dial <from> <to>` opens a
  wormhole between two named gates. Both need `wormhole.config`, and a player also the usual build or
  dial rights. A gate built this way has no owner
  until `gate edit owner` gives it one; shapes that dial by sign cannot be built this way.
- **A server upgrading from a `Settings.txt` build is told which of its settings to set again.**
  That file is still not read; the first start names only those that exist and differ from
  the default.

**Removed**

- **`plugin.yml` no longer names the 2011 `Permissions` plugin or `WormholeXTremeWorlds` as soft
  dependencies.** Neither did anything, and neither runs on a supported server. Vault and LuckPerms
  are unchanged.

**Fixed**

- **A fresh install loads the example `gate-material-groups` on its first start**, not its second.
  It used to warn that "Shapes framed in OBSIDIAN disagree on their other materials" about config
  nobody had written. A config.yml that cannot be read now falls back to the built-in obsidian
  group too. Nothing to do; an upgraded server was never affected.

### For other plugins

The plugin API is in [docs/API.md](docs/API.md).

**Added**

- **`StargateActivatedEvent` and `StargateShutdownEvent`**, raised when a gate's wormhole
  opens and when it closes, once per end. `StargateShutdownEvent.getReason()` says why:
  `TIMEOUT`, `MANUAL`, `FAR_END`, `REMOVAL` or `PLUGIN_DISABLE`.
- **A PlaceholderAPI expansion**, off until `placeholders-enabled` is set:
  `%wormhole_gates_total%`, `%wormhole_gates_open%`, `%wormhole_gates_owned%` and
  `%wormhole_nearest_gate%`. See [the guide](docs/guide/SERVER.md#placeholders).

### Stargates

**Added**

- **The iris animates**: `sweep` (a ring at a time, rim inwards), `spiral`, `rows` or
  `columns`, or `instant` for the old way, set by `gate-iris-animation`. `gate-iris-step-ticks`
  sets the pace and `gate-iris-sweep-max-ticks` caps a whole crossing (twenty ticks; `0` lifts
  it). Only the picture sweeps; the iris holds from the first tick. Previews sweep too. See
  [the guide](docs/guide/GATES.md#how-it-arrives).
- **A shut iris covers the wormhole instead of replacing it.** The wormhole is drawn a block
  behind, and whichever layer is nearer you takes the ring, swapping as you walk round.
  Behind a stained-glass iris it is drawn in ice (or purple and magenta concrete for
  `nether_portal`), because Minecraft hides a liquid behind a translucent block;
  `gate-iris-horizon-ticks` sets its shimmer and `0` holds it still. Previews do the same.
  Upright gates only.
- **Three more `gate-dial-spin` patterns**: `chase`, `universe` (Destiny's) and `overshoot`.
  See [the guide](docs/guide/GATES.md#dialling).
- **A gate or a material group can pick its own ring pattern and iris style**:
  `gate edit <gate> spin <pattern>` and `gate edit <gate> iris-animation <style>`, or
  `dial-spin:` and `iris-animation:` in a group; `default` follows the server again. New configs
  ship Atlantis on `pegasus` and Universe on `universe`; an existing `config.yml` keeps its
  groups, so add those lines by hand.
- **A gate's owner can set its iris code** with `gate edit <gate> idc`, without
  `wormhole.config`.

**Changed**

- **The default `top` dial rests on the top chevron** for half a second as each one locks, so a
  dial takes about three seconds longer. `gate-dial-spin: chevron` keeps the old pace.
- **The last chevron holds two seconds before the kawoosh**, not one.
- **An upright gate's iris is drawn on clients, not built from blocks.** The opening stays air,
  so a crash leaves nothing behind. A shut iris still refuses players, carts, arrows and dropped
  items, and nobody can build in the opening it covers. A horizontal gate's iris is still real
  blocks, since it is a floor. Gates saved with an iris shut have its blocks removed on first
  load; nothing to do.
- **`gate preview materials` is now `gate preview needs`**, one letter away from
  `gate preview material`. The old name still works.
- **Bundled shapes nobody edited are updated at startup again**, keeping the old copy as
  `<name>.shape.old`. An edited copy is left alone and named in the log.

**Fixed**

- **`gate regen` keeps every `gate edit` setting**: custom materials, woosh depth, redstone
  and an iris shut by default, which it used to drop.
- **Gate settings are saved as they are set** by `gate edit idc`, the iris lever and `owner`,
  `custom`, `portalmaterial`, `irismaterial`, `lightmaterial`, `wooshdepth` and `redstone`.
  They used to wait for shutdown, so a crash lost them. `gate edit group` is saved at all now.
- **An iris shut by default stays that way through a save taken mid-journey.** A gate already
  saved open this way needs its lever pulled once.
- **`force` and `idc -clear` open an iris for good.** Both used to leave it to shut again at the
  end of the next journey, and `idc -clear` could leave it with no way to open. A dial refused
  or failed after opening an iris, near or far, shuts it again.
- **No kawoosh through a closed iris**, on a gate or a preview.
- **`gate-sound-iris-open` and `gate-sound-iris-close` play.** They never did.
- **A `nether_portal` wormhole fills the opening on every gate**; half of them showed a sliver
  seen edge-on. Directional iris blocks, such as logs, are laid in the gate's plane too.
- **`pegasus` steps a glyph at a time and lights each chevron only as it locks.**
- **A cart turned back by a closed far iris lands in front of its own gate**, not in its frame
  or a wall.
- **A lever on the empty iris spot of a gate with no code does nothing, and can be broken.**
- **A gate with no arrival point refuses the trip** instead of throwing on every tick a cart
  rolled into it.
- **A dial sign hung on a placed preview works** once `gate complete` is run. A Horizontal frame
  completes as `HorizontalSignDial` instead of writing its name sign over the dial sign. Gates
  already completed without their sign are not changed.
- **A gate's saved minecart arrival point keeps its pitch and yaw the right way round.**
- **Stopping the server with a wormhole open saves every gate, ring, beam and mirror.** The
  open gate used to throw on its way down, and nothing after it was saved.
- **Riders can no longer dismount inside an open gate on 1.20 through 1.20.3.** Nothing stopped
  them there, and the server logged an error about `EntityDismountEvent` at startup.
- **`gate import` skips a damaged gate and says why**, instead of listing it as `null`, or
  stopping the whole import on one.

### Commands

**Added**

- **`/wormhole` on its own lists its commands by job**, with coloured usage, showing only what
  you can use. `/wormhole gate` does the same for gate verbs.
- **More tab completion**: `gate-iris-animation` styles, `wooshdepth` depths, `owner` players,
  `idc -clear`, `remove -destroy`, and the flat `build` and `regen` complete like their `gate`
  forms.
- **`gate preview material` roles take a dash**: `-frame`, `-chevron`, `-light`, `-portal`,
  `-iris`, `-sign`. The bare word still works.

**Fixed**

- **A mistyped command gets one short usage line for that command**, not the whole block from
  `plugin.yml`. `/dial` too.
- **`gate list` completes networks, not gate names**, and each `gate` verb completes the way the
  command it is short for does.
- **`gate edit <gate> idc` with no code reports the code** instead of clearing it; `-clear`
  clears it.

### For shape authors

**Added**

- **`[S:C]` marks a chevron where the frame material is also accepted**, so a shape can gain
  chevrons without breaking gates already built to it. See [docs/GATES.md](docs/GATES.md).

### Quantum mirrors

**Fixed**

- **A mirror whose capture throws partway is captured again** the next time someone looks at it,
  instead of never updating until a restart.
- **Less of a small mirror's picture lingers outside its opening as you move.** Openings of six
  blocks or fewer redraw twenty times a second rather than ten, inside the same cost cap.

### Transport rings

**Changed**

- **The transport light runs out from the pad putting a traveller back down.**

### Performance

- **Finding the nearest gate walks the list once** instead of copying and sorting it.
  `/wormhole compass` and `%wormhole_nearest_gate%` use it.

### Internals

- **The plugin runs on [MockBukkit](https://mockbukkit.org)'s simulated server** in the Paper
  1.21.11 and 26.2 CI jobs (`mockbukkit` profile, `src/mockbukkit/`, Java 21+). A player's trip
  through a gate, a beam, a ring and a mirror, a pet following through a gate and a beam, the
  iris and its code, and redstone dialling are tested end to end there.

[Back to top](#changelog)

## 1.7.1 (2026-09-19)

**Upgrading: take the new `Massive.shape`, then regenerate each `Massive` gate.**

- Delete `Massive.shape` if you did not edit it, and restart. The startup log names it.
- Run `/wormhole gate regen <gate>` on each `Massive` gate. Its name sign stood inside the ring,
  where a frame block was; regen puts the block back and hangs the sign on the front face.
- A `Massive` gate whose sign was ever taken down has a hole there instead, and regen reports it
  a block short. `/wormhole gate regen <gate> -shape Massive -fill` puts it back.

### Stargates

- **`Massive` is the same on both sides.** Its lamp rings had an extra column of frame on the
  right, and two blocks past the corner at each end of it.
- **`Massive`'s name sign hangs on the front of the gate.** Its `:N` was on the back ring, so the
  sign went into the layer in front of it.
- **`gate regen` takes any sign standing in the gate's frame down and puts the block back**
  before it reads the gate, and says where. Until then the frame came up a block short,
  `OAK_WALL_SIGN` where the ring should be, and the markers were left alone.
- **`gate regen -fill` places the frame blocks a gate is missing**, from its own frame and chevron
  materials, and names each one. At most three, or one in a hundred on a big gate, and only into
  air, water or lava; more than that, or a solid block where the frame goes, and it places nothing
  and says why.
- **Taking a name sign down from the frame puts the frame block back**, rather than leaving air
  where the ring was.
- **`gate preview place` fills in a gate it is laid over**, for a player with `wormhole.config`:
  it places only the blocks that gate is missing, never replacing one, then regenerates the gate,
  keeping its name, owner, network and IDC. Over a ring or two gates it is refused as before.
- **A name sign is never placed over the gate's frame or chevron material.** The gate goes
  without one, and the log says why.
- **`/wormhole gate shapes validate` catches a `:N` with a block in front of it**, a frame,
  chevron or portal cell, where the sign would land inside the gate. `gate shapes reload` refuses
  such a shape, 1.7.0's `Massive` among them; startup still loads it.

### Server

- **`/version WormholeXTreme` says when the jar was built**, in UTC, under the description, so two
  builds of one version can be told apart.

[Back to top](#changelog)

## 1.7.0 (2026-09-19)

**Upgrading: command keywords start with `-`, and the new gate shapes are yours to take.**

- A word that can stand where a name does is an option only with a dash, and no name may start
  with one. Scripts and command blocks need:

  | Was | Now |
  |---|---|
  | `gate remove <gate> -all` | `gate remove <gate> -destroy` |
  | `complete help` | `complete -help` |
  | `custom -clean confirm` | `custom -clean -confirm` |
  | `mirror set [name] stamp\|start\|capture` | `mirror set [name] -stamp\|-start\|-capture` |
  | `mirror set [name] start none` | `mirror set [name] -start -none` |
  | `mirror debug [name] all\|full\|on\|off` | `mirror debug [name] -all\|-full\|-on\|-off` |
  | `beam admin cost <name> default` | `beam admin cost <name> -default` |

- Shape files are never overwritten, so a server keeps 1.6's. The startup log names each bundled
  shape that differs from this version's: delete the ones you did not edit and restart to get the
  show's chevron order, the eighth chevron, the slower pace and the one-layer `Large`.
- Every gate's light order is rebuilt from its shape at startup, so the restart after that brings
  standing gates along. A gate whose frame no longer fits its shape is named in the log; run
  `/wormhole gate regen <gate>`, or `-shape <shape>` for one missing a block or two.
- A three-deep `Large` gate from 1.6 keeps working, but no longer matches `Large`. To keep building
  them, keep 1.6's `Large.shape` under another file name and another `Name=`.
- Gates the legacy importer recorded as `Standard` are found as the shape they really are by
  `gate regen <gate>`.
- `/wormhole gate regen` replaces `regenerate` and `/wormhole refresh`, which both still work.
- New settings are added to an existing `config.yml` at their defaults, under a marked block.

### In this release

- **[Stargates](#stargates)** — the show's dialling: chevrons in order at a pace you can follow,
  an eighth for another world, the inner ring turning, a lock-in sound, and sounds sized to the
  gate. Build previews. `gate regen` finds a gate's real shape.
- **[Travel](#travel)** — pets come with you.
- **[Command options](#command-options)** — keywords start with `-`.

Longer explanations are in [docs/GATES.md](docs/GATES.md) and [docs/guide/](docs/guide/).

### Stargates

**Dialling**

- **Chevrons light in the show's order**: down the right side, up the left, the top one last.
- **An eighth chevron locks when the other gate is in another world**, after the top one. `:L#8`
  marks it, at the bottom of every shipped ring gate but `Minimal`.
- **Pressing a gate's button lights every chevron at once**; `/dial` then darkens them and dials
  the ones it needs in order, with their sounds.
- **Chevrons lock at a pace you can follow**: half a second each on `Standard`, a little longer on
  bigger gates. A shape's `LIGHT_TICKS` and `WOOSH_TICKS` are read at last.
- **The last chevron holds a second and locks in with its own sound** (`gate-sound-lock`).
- **The dialling gate's inner ring turns**: before each chevron locks, a light sweeps half the
  ring to the top chevron, alternating direction. The gate being dialled only lights. `gate-dial-spin`
  picks the pattern: `top`, `chevron`, `lap`, `fill`, `pegasus` or `none`; none changes the dial's pace.
- **A sign dial opens at once** on both ends, with the lock-in sound and no chevron sequence.
- **A gate sounds its size**: deeper and louder on a bigger gate, lighter on a smaller one.
  `SOUND_SCALE` in a shape sets its own.

**Build previews**

- **`/wormhole gate build <shape> [group]` shows a shape full size in front of you**, seen by you
  alone (`wormhole.build.preview`, op by default).
- **`/wormhole gate preview <action>` does everything else to the preview you look at**;
  `gate build` only starts a gate. `clear [-all]` takes previews away.
- **A preview can be dialled, given an iris and redressed**: `activate`, `iris`,
  `material <group>|<role> <block>`, `chevrons`, `dhd`.
- **`materials` lists what it takes, `guide` marks what is left to build, `layer` shows a layer
  at a time**, and looking at a placed DHD button stands the preview on it.
- **`share <player>|-all` shows it to others** (`wormhole.build.preview.share`), and **`place`
  builds it for real** (`wormhole.build.preview.place`).
- **New settings:** `gate-preview-minutes` (10) and `gate-preview-max-blocks` (5000).
- **A Standard gate is previewed the classic way**, its chevrons drawn as frame.

**Regen**

- **`/wormhole gate regen` is the name**, and it now does what `refresh` did: detects the gate
  afresh from its frame, keeping its name, owner, iris code and network. With no gate named, it
  waits for a DHD click. An open or dialling gate is shut down first.
- **It finds the shape a gate really is** when the gate was recorded under the wrong one, and
  `-shape <shape>` takes a named shape if 90% of its frame is standing, listing what is missing.
- **A shape is laid where the gate's frame is**, searching a few blocks round the DHD's layout.
- **It relights a gate's chevrons in its shape's order**, reading no blocks, so `-all` covers
  unloaded gates too. `-all` names the gates it left alone, a line per reason.
- **`-water` clears water or lava stranded in a closed gate's opening.**
- **Its messages pick out the gate, shape, counts and coordinates in colour.**

**Shapes and other changes**

- **`Large` is one layer deep**, as `Standard` is, rather than three.
- **Startup names each bundled shape that differs from this version's**, and rebuilds every gate's
  light order from its shape.
- **A `config.yml` with no `gate-material-groups` gets the example groups** (Atlantis, Universe,
  MilkyWay beside Standard).
- **`/wormhole gate complete -cancel` cancels a waiting completion.**
- **`/wormhole gate edit <gate> owner` with no name reports the owner** instead of clearing it.
- **The entity sweep leaves display entities alone**, so holograms near an open gate stay put.
- **`/wormhole gate import` keeps each gate's owner**, who can now remove it with
  `wormhole.remove.own`, and after a restart. A gate imported by an earlier version has no owner:
  set one with `/wormhole gate edit <gate> owner <player>`.

### Travel

- **A player's pets travel with them** by gate, ring, beam or mirror: tamed wolves, cats and
  parrots within 12 blocks that are not sitting. `pets-follow-owner: false` turns this off.

### Command options

- **Keywords start with `-`** (see Upgrading), and gate, mirror, beam destination and place names
  may not. `-all` and `-clear` are recognised whatever their capitals.

### Quantum mirrors

- **A mirror's view no longer hides and re-shows display entities.**
- **`/wormhole mirror set <name> -start` with no mirror after it is refused.**

### Server

- **CraftBukkit no longer throws on every ring countdown and mirror approach.** It has no
  action bar, so the plugin says so once in the log and goes without.
- **Supported through Minecraft 26.3.** CI builds and tests against Paper at every supported
  version, and against Purpur's newest.
- **`log-level` below `INFO` reaches the console**, as lines marked `[FINE]`, and
  `/wormhole config log-level` takes effect at once.

### Documentation

- **The server guide has an [Upgrading](docs/guide/SERVER.md#upgrading) section**: what the first
  startup after a new jar moves, what it leaves alone in `config.yml` and `shapes/gate/`, and what
  a gate built by an older version wants doing to it. The README points at it, and at
  `gate import` for gates from another fork.
- **`SECURITY.md` says how to report a security problem privately**; the issue chooser links the
  private report form and the maintainer's Discord.
- **The docs show the plugin in game**, with a capture per subsystem on the README and on each
  guide page, and drawn sheets in place of ASCII sketches.
- **The docs say each thing once**: commands, settings and permissions in `docs/guide/` only.
- **The original plugin's 2011 notes are in `CHANGELOG-ORIGINAL-2011.md`.**

### Internals

- **CI fails a pull request with any open Sonar finding**, and all 41 on main are cleared.
- **The Sonar scan is retried**, so a SonarCloud outage no longer fails a pull request.
- **The mirror window tests draw on stand-ins rather than mocks**, and run in 256 MB where they
  wanted 4 GB. The test fork has a 1 GB ceiling.
- **A Claude Code web session warms the Maven cache as it starts**, so its first
  `mvn test` runs tests instead of downloading the build. Local checkouts are unaffected.
- **`gate import` is tested against a real SQLite database**, with `sqlite-jdbc` as a test-only
  dependency; the jar still uses the server's driver.

[Back to top](#changelog)

## 1.6.0 (2026-09-16)

Sixteen commits' notes had been written into 1.5.0's section as though they had shipped in it, and
they are here instead.

**Upgrading: nothing to do, with five things worth knowing.**

- Files move themselves on the first startup. Gate shapes go from `GateShapes/` to `shapes/gate/`,
  and your gates, rings and beam destinations from `WormholeXTremeDB/` into `data/`. Files are
  moved rather than copied, a file already at the destination wins, every move is logged, and
  nothing is deleted, so putting 1.5.0's jar back finds the old folders as they were.
- `WormholeXTremeDB/` keeps its name, because another fork's SQLite database lives there too and
  `/wormhole gate import` finds it by that name. Only this plugin's own files come out of it.
- If your plugin folder is not `./plugins`, this build reads `config.yml` and the gate shapes from
  the folder the server names, where 1.5.0 read them from a guess. A server that had quietly been
  running on defaults will start reading the file you have been editing.
- New defaults reach only a fresh `config.yml`. Rings now count down for 100 ticks
  (`ring-countdown-ticks`) and rest for 600 (`ring-cooldown-ticks`); an existing file keeps 60 and
  1200 until you change them.
- Minecraft 26.1 and later need the server on Java 25. The plugin itself still runs on Java 17.

### In this release

- **[Quantum mirrors](#quantum-mirrors)** — new, and most of this release. A banner on a wall
  gives way to the room it opens onto, and every mirror is on one network.
- **[Stargates](#stargates)** — `gate validate`, `Even` dropped as a duplicate of `Large`, and
  seven fixes.
- **[Transport rings](#transport-rings)** — ceiling rings in deep rooms, and a room refused when
  it is built rather than when somebody stands in it.
- **[Beaming](#beaming)** — the sequence drawn as a timing strip, which found a wrong number.
- **[Configuration and documentation](#configuration-and-documentation)** — `config.yml` written
  in groups, the four design documents opened with summaries and cut by a fifth, and galleries
  drawn for gates, rings, beams and the looks.
- **[Performance](#performance)** — six things that scaled with how much exists rather than with
  how much is happening.
- **[Internals](#internals)** — one YAML write path and one logger where there were three of
  each, and a long pass over the test suite.

Longer explanations are not here. What a subsystem does and why it is built that way is in
[docs/](docs/): [GATES.md](docs/GATES.md), [RINGS.md](docs/RINGS.md), [BEAMS.md](docs/BEAMS.md)
and [MIRRORS.md](docs/MIRRORS.md), with the how-to in [docs/guide/](docs/guide/).

### Quantum mirrors

A fourth way to travel, and the first one you can see. Design notes in
[docs/MIRRORS.md](docs/MIRRORS.md); how to build one in
[docs/guide/MIRRORS.md](docs/guide/MIRRORS.md).

**Added**

- **A mirror opens onto where it goes.** Walk up to a wall banner, on its side, and the banner is
  gone (on plain 1.20 it stays, in front of the view): an opening its own size shows a room in real
  blocks, with depth as you move, reaching `mirror-view-depth` and nothing past it.
- **Every mirror is on one network.** It shows its own room flipped until somebody right-clicks
  it; a right-click moves it on to the next mirror, a punch goes through to that mirror's banner,
  and it goes back to its own room once nobody is near.
- **One mirror to a world by default** (`mirror-per-world-limit`), on solid wall a block out on
  every side, which cannot be broken while the mirror is there.
- **Two wall banners side by side make one mirror two wide.** Either banner answers a click and a
  traveller lands between them.
- **A mirror's room can end in your own fog** (`mirror-fog-at-depth`, off by default, Paper only).
- **A capture reaches as far as the room's world sends**, so changing the depth draws more or less
  of the same capture and never takes one again.
- **A mirror keeps its far room inside its edges**, and nothing shows past them from an angle.
- **A look for every biome in the game**, and twenty-three more for what a mirror is *for* —
  `exit`, `vault`, `library` and the rest, reached only by name.
- **A mirror can look like where it goes.** `set stamp` reads the far side and paints the banner
  from it: the biome picks the frame, the blocks there become coarse squares.
- **A mirror says what it is above the hotbar** while you look at it from about six blocks, along
  with the world it opens onto (`mirror-approach-message`).
- **`mirror debug`** says one thing a line, fits on a screen of chat with `all` for the rest, and
  tab-completes for whoever may run it.
- **A capture no mirror uses is deleted at startup**, and `mirror list` names each mirror's.
- **`mirror create` says what a mirror needs**: which block of wall is missing, when the wall is a
  block short of two, and when another mirror is near enough that neither can be drawn whole.
- **Four mirror verbs take the banner you are looking at** when you leave the name out.
- **Seven banner patterns came back**, including the only round one.

**Changed**

- **`mirror set` is the one door to what a mirror has.** The top of the command is `create`, `set`,
  `remove` and `list`; `set [name] <stamp|start|capture> ...` holds the rest.
- **`stamp` is about the banner and `capture` is about the room**, and nothing changes either on
  its own. `mode` is gone with the automatic restamping it existed for.
- **A room is streamed in a tick at a time** as a viewer comes into range, and taken back the same
  way, so coming to a mirror no longer freezes a frame. The cap on a room sent whole is gone.
- **A mirror on any wall reaches the full depth**, standing or walking: its room is held whole and
  clipped to each eye.
- **A clipped room's far part is judged for a whole cell of eyes at once**, as wide as the wall
  allows, and stands between small steps; a slow redraw earns a rest before the next.
- **A mirror needs a block of wall round its opening, not two**, and `create` says so when it is
  short of two.
- **The outer half of the wall's edge is a margin** nothing is drawn onto.
- **`mirror-view-depth` is 160 by default**, and a room cut to fit says so in `mirror debug`.
- **`mirror-proximity-radius` is `mirror-proximity-distance`.** An existing `config.yml` keeps its
  value and is rewritten under the new name.
- **A right-click never comes round to a mirror's own room.**
- **A right-click leaves no real banner showing**, not even for a tick.
- **`set stamp` counts the looks** once there are too many to list.
- **Mirror messages are no longer entirely grey.**

**Removed**

- **The cone walk**, and `mirror debug save` with it.
- **Two mirror settings nothing read**: `mirror-capture-radius` and `mirror-allow-same-world`.
- **`mirror set display`**: every mirror is a view now, so it did nothing. An old `Display` line
  is ignored.

**Fixed**

- **A redraw could hang the server for fifteen seconds** while standing on a block boundary.
- **The real banner no longer flickers on a right-click.**
- **A block placed or broken through a mirror's view acted on the real world behind it**, unseen.
- **Scrolling quickly between mirrors could leave part of the last room up** for a few seconds.
- **Changing the fog setting or the depth did nothing for somebody already at a mirror** until
  they walked away and back.
- **A banner on a post could not be named** from right next to it, and naming one now says where
  it has to be clicked.
- **A linked pair of mirrors sent you straight back where you came from.**
- **A mirror another plugin refused** looked exactly like a mirror pointing at itself.
- **Renaming a mirror used to quietly take it apart**, losing its destination and look.
- **A mirror onto the Nether was still dressed as somebody's living room.**

### Stargates

Design notes in [docs/GATES.md](docs/GATES.md), how-to in
[docs/guide/GATES.md](docs/guide/GATES.md).

**Added**

- **`/wormhole gate validate <gate>`** says what a gate is missing rather than only refusing to
  dial, and `-all` sweeps every gate and names the ones with something wrong.

**Changed**

- **`Even` no longer ships, because `Large` is the same gate.** Nine shape files now. A server
  that already has `Even.shape` keeps it and its gates go on being detected.
- **`Grand`'s lower chevrons mirror its upper ones**, so the gate is no longer lopsided.
- **Gate shapes live in `shapes/gate/`** rather than `GateShapes/`.
- **The four-step order that decides what block a gate shows is written down once**, and every
  caller asks the same place.

**Fixed**

- **A block could be dropped into a gate's opening and never broken out again.**
- **A gate came back from a restart not knowing which shape it was built from**, and the next save
  wrote the loss to disk.
- **A gate built before its shape file gained a marker could never pick it up.**
- **A shape that pins a material lost it after a restart.**
- **The marker reference at the top of the shipped shape files disagreed with itself.**
- **Regenerating a gate reattaches its dial sign** when the sign is still standing but no longer bound.
- **An arrow between two facing gates crossed over and over**, and one that bounced off a player
  in front of a gate was pushed on through them.

### Transport rings

Design notes in [docs/RINGS.md](docs/RINGS.md), how-to in
[docs/guide/RINGS.md](docs/guide/RINGS.md).

**Changed**

- **A ring counts down for five seconds and rests for thirty seconds**, where it was three and
  sixty. An existing `config.yml` keeps its own values.

**Fixed**

- **A ceiling ring in a room deeper than four blocks fired over and over and took nobody.**
- **A room that could never work is refused when the ring is laid**, not when somebody stands in
  it.

### Beaming

Design notes in [docs/BEAMS.md](docs/BEAMS.md), how-to in
[docs/guide/BEAMS.md](docs/guide/BEAMS.md).

**Added**

- **The beam sequence is drawn as a timing strip in the documentation**, and drawing it found a
  number that disagreed with the code.

### Configuration and documentation

**Added**

- **The gate shapes are drawn in the documentation, idle and dialled** — six gates rather than
  nine files, with what a sign dial adds drawn once.
- **The ring patterns, the stack and both deploys are drawn** in the documentation.
- **The ninety looks are drawn**, with the recipe beside each.

**Changed**

- **`config.yml` is written in groups**, ten of them under `# --- Transport rings ---` banners,
  with each setting's comment cut to a sentence or two. 256 lines from 361, same 82 settings. A
  file you already have keeps its own layout; the grouped one is what a fresh install writes.
- **The four design documents open with a summary and are a fifth shorter.**
- **The README is a front page again**, with the how-to moved into `docs/guide/`.
- **Supported through Minecraft 26.2**: CI now also builds and tests against 1.21.11, 26.1.2
  and 26.2, and against Paper's API as well as Spigot's. Servers on 26.1 or later need Java 25.

### Performance

A pass over what this plugin costs a busy server. Nothing here changes what it does; every item
was chosen for scaling with how much exists rather than with how much is happening.

- **Two per-second sweeps scaled with how many gates exist, not how many are open.** Both read the
  open-gate set now.
- **The block index no longer allocates to answer a question.** `getGateFromBlock` and
  `isBlockInGate` are keyed by world and a packed block position rather than by `Location`.
- **A gate deleted while its wormhole was open never left the open set.**
- **Six static maps kept every player who had ever left.**
- **Asking which chunk a block is in was loading that chunk.**
- **Three log lines on hot paths were built whether or not anyone was listening.**

### Internals

- **One YAML write path and one logger**, where gates, rings and beam destinations each carried
  their own copy and three "is there a plugin to log through" checks disagreed.
- **Gates, rings and beam destinations live in `data/`** rather than sharing a folder with another
  fork's database.
- **Every store is found by asking the server for the plugin's folder**, where half were found
  that way and half by guessing at the working directory.
- **The plugin singleton is installed in one place**, where seventy-eight tests each did it
  themselves.
- **Thirty-four command helpers that returned `true` and nothing else** return `void`, and ten
  class-level `@SuppressWarnings` went with them.
- **Nine unchecked casts in the tests** are gone, and reflective field access went from 139 sites
  across 87 files to 8 across 7.
- **`MirrorWindows` is being taken apart.** `MirrorSight`, `MirrorFace`, `MirrorWindowState` and
  `MirrorDrawing` are out of it; 2,449 lines from 2,786.
- **Forty Sonar findings on the mirror code are cleared**, including two typed as bugs that
  were not: a null check read across two contracts, and an exact integer subtraction.
- **The release workflow can be rehearsed without publishing.**
- **Nothing marked for removal in newer Bukkit is called directly.** An arrow through a gate
  keeps its weapon from 1.21, and its knockback and crossbow flag before.

**Fixed**

- **A tidy-up that failed on shutdown took every save with it.**
- **The startup banner tore itself apart on some consoles and not others.**
- **Gate shapes and `config.yml` were looked for in the working directory** rather than the folder
  the server names.

[Back to top](#changelog)

## 1.5.0 (2026-09-09)

Not a feature release. Rings arrived in 1.3.0 and beaming in 1.4.0; this is the release that
went back over all of it. Twenty-odd player-facing bugs are fixed, several of them older than
this fork, and the tree went through a quality pass that took SonarCloud from 893 open issues to
none and the test suite from about 490 tests to 1326.

**Upgrading:** nothing to do. Gate, ring and beam files load unchanged and every setting keeps
its current value.

### Fixed

- Gate protection no longer stops working when another plugin cancels the event first, which
  could let lava out of a gate frame ([#53](https://github.com/khanjal/Wormhole-X-Treme/issues/53)).
- A gate whose blocks WorldEdit removed now refuses to be dialled and says why, rather than
  going quietly dead ([#54](https://github.com/khanjal/Wormhole-X-Treme/issues/54)).
- Beaming off a horse left the horse behind; your mount comes with you now.
- A chevron was invisible while a gate sat idle.
- A closing iris no longer buries whoever is standing in the gate.
- A sign gate wired to redstone dialled twice on one press.
- A sign gate ignored its own sign until somebody cycled it by hand.
- `HorizontalSignDial` could not be built at all.
- Choosing a sign dial cost the gate its iris.
- Redstone wiring inside a gate could never be taken back up without removing the gate.
- A ring pair in a room too short to hold it fired anyway.
- Tab completion after `beam to` offered only public destinations, never your own places.
- Beaming hid the traveller but left their gear standing in the column.
- A gate named with an accent came back as `Caf?` after a restart.
- A gate's owner turned into a UUID.
- One unwritable gate stopped every gate after it from being saved.
- Saving the config destroyed the rest of the file, and a hand-edited line missing its colon
  ended the parse.
- `/wormhole ring edit` with nothing after it crashed.
- Every feature permission under `/wormhole` was unreachable without `wormhole.config`.
- A rider arrived facing the wrong way after a cart trip.
- A minecart lost its own kind through a failed vehicle teleport.
- `/wormhole cooldown` reported a setting it never saved, and `/wormhole restrict` did nothing
  at all.
- `/wormhole config` rejected settings spelled the way `config.yml` spells them, worked only in
  English, and accepted values it could not read back.
- A gate file that would not delete failed silently.
- A powered lever or button lost its facing when an old gate was rebuilt.

### Changed

- The name and logo are no longer covered by the GPL — see [TRADEMARK.md](TRADEMARK.md).
- Dial signs are coloured and mark the selected destination, so it no longer looks identical to
  the two either side.
- Ring pads light as a redstone lamp rather than glowstone.
- A redstone signal on an open gate extends it up to the existing maximum, instead of closing it.
- Every sign gate takes redstone, not only the one with "Redstone" in its name, and the DHD
  accepts a signal from any component rather than only from dust.
- The kawoosh sounds like water rather than an explosion, and sign colours are quieter.
- Gate, ring and beam sounds are each configurable, including to a resource pack's own sounds.

### Documentation

- `docs/GATES.md`, `docs/RINGS.md` and `docs/BEAMS.md` explain how each subsystem works and why.
- The README gained a Features list, and its sound documentation is no longer filed inside the
  transport rings section.
- `/dial` is described correctly: it names a destination for a gate that has no dial sign, and
  is not a fourth way to dial one.

### Under the hood

None of this changes what the plugin does. It is why the list above could be written with any
confidence.

- SonarCloud: **893 open issues to 0**, with A ratings for reliability, security and
  maintainability.
- Tests: **about 490 to 1326**; line coverage 42.9% to 73%.
- Every `catch (Throwable)` is gone, every YAML file is read and written as UTF-8, and the dead
  `LegacyCompat` class plus nine unused helpers were deleted.
- Three bugs in the list above were found by writing tests rather than by anybody hitting them.
  Two more turned out to be in methods nothing called, which is how the dead code was noticed.

[Back to top](#changelog)

## 1.4.0 (2026-09-05)

Beaming: a third way to travel, with no structure to build and nothing to dial. Three large gate
shapes and a fourth palette arrived with it, and the woosh finally makes a sound.

**Upgrading:** nothing to do. Beaming is off until somebody sets a destination, and the new
shapes and palette are additions rather than changes to existing gates.

### Added

- **Beaming**, point-to-point travel alongside gates and rings: staff curate public
  destinations, each player keeps their own private places, and no structure is built for either.
- `/wormhole beam admin goto` and `send`, usable from a player, the console or a command block.
- Public beam destinations can carry their own cost, which admins may bypass.
- Three big, deep gate shapes: `Large`, `Grand` and `Massive`.
- `Even`, the first even-width gate shape.
- A fourth palette, `MilkyWay` — deepslate frame, iron iris, shroomlight chevrons.
- A gate shape can be validated and reloaded without restarting the server.

### Fixed

- A beam that failed part-way through stranded the traveller, and left them in the dark.
- The traveller's own view arrived ahead of the beam.
- A beam charged for a message with nothing behind it.
- The woosh could leave portal material stuck outside a gate, or an extra layer inside it.
- The wormhole woosh sound never actually played.
- A gate's ambient hum outlived the gate.
- A timed-out gate's chevrons could stay lit forever.
- `/wormhole go` ignored private network permission, which dialling already respected.

### Changed

- The beam effect matches its reference footage: the glow gathers at the traveller rather than
  around them.
- The traveller can move, turn and react during the envelope instead of being frozen from the
  moment the command runs.
- Beam and gate arrivals correct for ground that has been built up or dug out since the
  destination was recorded.
- Woosh depth is proportional to a gate's size rather than a fixed number.

### Under the hood

- `BeamAnimation.Sequence` was split so every phase boundary and quantity can be tested without
  a running server, the same way the ring subsystem was split earlier.

[Back to top](#changelog)

## 1.3.0 (2026-09-03)

Transport rings: local, point-to-point travel that needs no structure above the floor and
nothing to dial. Gates stay the long-haul option. Alongside them, twenty-two subcommands
became four, gates can be imported from any other Wormhole X-Treme's database, and gate
management turned out never to have been permission-gated at all.

**Upgrading:** every old command name still works -- they are registered as hidden entries
and dispatch exactly as before, so nothing in a command block or a script breaks. One
breaking change for plugin developers: `StargateMinecartTeleportEvent` moved from
`com.wormhole_xtreme.wormhole.event` to `...events`, joining every other event.

### Added

- **Transport rings** -- an invisible, permanently paired pad set into a floor or
  ceiling. Walk in, and after a countdown both ends swap everything standing in them.
- `/wormhole ring create|cancel|list|remove|edit|allow|deny|owner`, with two ring shapes,
  per-end materials and deploy style, and four permission nodes of its own.
- `/wormhole gate import` reads the SQLite database every build descended from the 2011
  original writes, and converts those gates into this fork's storage. Nothing is written back
  to the old database and nothing is deleted.
- `/wormhole gate regenerate -all` recomputes every gate's arrival point in one pass and
  reports how many needed it.
- `gate edit <gate> <field> [value]` gains a `group` field that sets a whole material group
  at once rather than three materials one at a time.
- `compass reset` puts the compass back to pointing at world spawn, and `compass` now
  explains itself when nothing will show the result.
- Ring sounds: the pad opening, one per ring as the stack builds, the transport itself at
  both ends, the pad closing, and a refusal heard only by the player it concerns.
- `RingTravelEvent`, fired once per travelling player, cancellable, after both ends are read
  and before either is written.
- Tab completion for gate shape names on `gate build`, and every setting name on `config`.
- **docs/API.md** -- the events, what each carries, when each fires, and worked examples.

### Fixed

- **Gate management was never actually permission-gated.** Any player who could run
  `/wormhole` could reconfigure or reassign *any* gate on the server, and change server-wide
  settings that are not even per-gate. All of it now requires `wormhole.config`.
- Holding forward against a gate you cannot enter no longer fills chat with identical
  lines. The move is still refused every time; only the repeated message is suppressed.
- Ring block positions are packed into a `long`, and `y` lived where a plain mask loses its
  sign -- so a ring below y=0 would have restored its blocks thousands of blocks away.
- A registry question in `Ring`'s static initialiser could fail during plugin load on
  1.20.6 and later, which would have left rings dead for the rest of that server's run.

### Changed

- **Twenty-two subcommands became four:** `gate`, `ring`, `config` and `compass`. Nothing
  was re-implemented -- every verb hands off to the handler that already owned it.
- `config <setting> [value]` reaches every setting, not the four that had commands, and
  takes effect as it is typed rather than needing a restart.
- `gate regenerate` also recomputes a gate's arrival point from its portal blocks and
  facing, so a gate that landed travellers at its side can be corrected.
- The README is now for server owners; the plugin-developer material moved to docs/API.md.

[Back to top](#changelog)

## 1.2.0 (2026-09-02)

One jar covers Minecraft 1.20 through 1.21.10, and the range is measured rather than
assumed: every published `spigot-api` version was built against to find both ends.

**Upgrading:** nothing to do. Note that **Minecraft 1.20.5 and later require the server to
run Java 21** -- that is the server's requirement, not this plugin's, and the jar remains Java
17 bytecode.

### Added

- Support for Minecraft **1.20 through 1.21.10** from a single jar. CI builds and tests the
  floor, the ceiling, and the versions between them where the API actually changed.
- A test checks every material named in `config.yml` and the shape files against whatever
  API the build targets, so a renamed or removed material surfaces at build time.
- `spigot.api.version` in `pom.xml` selects the API, so CI can point a build at a different
  server version without editing anything.

### Fixed

- A boat that failed to teleport was respawned as `EntityType.BOAT`, which stopped existing
  in 1.21.3 when boats split per wood type. It now respawns as whatever the original boat
  was, which also stops a birch boat coming back oak.

### Changed

- The jar is compiled against the **oldest** supported server rather than the newest, so the
  compiler enforces the floor instead of a player finding the gap.
- `EntityDismountEvent` changed package mid-range, so there is a small listener for each and
  only the one the running server can load is registered.

### Known limits

Nothing here has been runtime-verified on a live server of any version. CI proves the plugin
compiles and its tests pass against each API, not that a gate behaves correctly in game.

[Back to top](#changelog)

## 1.1.0 (2026-09-01)

First published release. The original project ended at 0.854; 1.0.0 was an internal
milestone of this fork and was never tagged or released.

Requires **Java 17** and a **Minecraft 1.20.4** server. Runs on CraftBukkit, Spigot, Paper,
Purpur and Pufferfish from a single jar. Folia is not supported.

Gates stop writing to the world, start making noise, and carry riders, vehicles and
projectiles again. The jar drops from 15.3 MB to under 300 KB.

**Upgrading:** gates are read from their existing storage and rewritten as one YAML file
each. `StandardAtlantis.shape` and `StandardUniverse.shape` are removed -- both were
`Standard.shape` with four lines changed, and the palettes they provided are now material
groups in `config.yml`, chosen by what a gate is built from.

### Added

- **Gate sounds:** one as a gate begins to dial, one per chevron as it locks, one as the
  wormhole establishes, one as it closes, an iris pair, and an ambient loop while it stands
  open. Configured by name, so anything the client knows works.
- **Material groups.** Shapes describe geometry; `config.yml` describes palettes, selected by
  the material a gate is actually built from. One shape file now builds a Standard, Atlantis
  or Universe gate.
- Travellers surface on arrival -- a moment of water at eye height, drawn to that player
  only. `gate-arrival-splash-ticks: 0` turns it off.
- `/wormhole custom -clean` clears material overrides snapshotted by the old custom mode.

### Fixed

- Travellers no longer drown or float in a water gate: portal interiors are server-side
  `AIR` with the material drawn to nearby clients.
- Minecarts and boats teleport again -- they had stopped entirely. Ridden animals travel
  with their rider, and riderless mounts can walk through on their own.
- Projectiles cross gates and keep flying, retaining their shooter so kills stay credited.
- Redstone gates can actually be built: the shipped shapes marked their redstone cells one
  block off, so a gate watched a wall for a signal that never came.
- Travellers can walk out of the gate they arrived at, rather than being held in the ring
  and eventually disconnected.
- Standing in a portal no longer gets a player kicked for flying.
- A closed gate could keep showing its portal to anyone who was out of range when it shut.
- Arrival points that sat inside the portal are moved clear of it on load.
- The version the server reports now comes from the build; `plugin.yml` had drifted, so a
  jar built as 1.1.0 announced itself as 1.0.0.
- An NPE on every move event for a gate that was activated but never dialled.

### Changed

- **Nothing a gate does is written to the world any more.** Chevrons and the woosh are
  drawings sent to nearby clients, like the portal already was -- so a server stopped mid-dial
  can no longer leave lit chevrons welded into a frame. The one thing given up is real light.
- **Storage is one YAML file per gate.** HSQLDB and SQLite are gone, along with the storage
  abstraction and the `mysql`/`postgres` options that were advertised but never implemented.
  Custom materials are persisted by name rather than enum ordinal.
- Permission checks go through `Player.hasPermission()`, so LuckPerms and anything else
  Bukkit-compatible works with no glue. The legacy built-in permission system is removed.
- One registry drives dispatch, tab completion and help. Nine subcommands were offered by
  completion and dispatched by nothing.
- The jar bundles nothing and has no runtime dependencies: **15.3 MB to under 300 KB**.
- Shutdown logs one summary line rather than one per gate.

[Back to top](#changelog)

## 1.0.0 (2026-05-14)

An internal milestone of this fork. Never tagged and never released -- the first published
release is 1.1.0, and much of the storage work below was removed again there.

### Fixed

- Fixed DHD activation to recognise all button material variants (oak, spruce, birch, jungle, acacia, dark oak, mangrove, cherry, bamboo, crimson, warped, stone, polished blackstone) via new `LegacyCompat.isButton()` helper.
- Fixed 2D gate exit location search in Nether/End dimensions: replaced `Material.AIR` equality check with `isAir()` so `CAVE_AIR` (present in Nether/End) is correctly treated as traversable.

### Added

- **Vault Economy integration** — optional gate use and build costs configurable via `economy-enabled`, `economy-use-cost`, and `economy-build-cost` in `config.yml`. Gracefully no-ops when Vault or an economy provider is absent.
- Added `LegacyCompat` utility class consolidating modern-Bukkit material/block compatibility shims.
- Added pluggable storage backend support and config keys (`storage-backend`, `storage-sqlite-path`, `storage-jdbc-*`).
- Implemented `StorageBackend` interface and `SqliteStorage` scaffold.
- Added `/wormhole storage` CLI: `backend` (set runtime backend) and `migrate` (DB -> YAML supported).
- Added `StorageMigrator` to export existing DB gates to per-gate YAML files (non-destructive by default).
- Added two new default gate shapes: `StandardAtlantis` and `StandardUniverse` (bundled in resources).

### Changed

- `ConfigurationYAML` migration now excludes legacy permission keys and preserves storage keys when writing `config.yml`.
- Removed legacy `SimplePermission` support; Vault/LuckPerms recommended.
- Improved startup diagnostics and storage initialization logging.
- Fixed a number of persistence and teleport UX issues (teleport bounce mitigation and gate activation mapping fixes).

[Back to top](#changelog)

## 0.x (2011, upstream)

The original plugin's releases, from January to May 2011, are in
[CHANGELOG-ORIGINAL-2011.md](CHANGELOG-ORIGINAL-2011.md). They are kept verbatim: they are somebody else's release notes
and the record of where this fork came from.

[Back to top](#changelog)
