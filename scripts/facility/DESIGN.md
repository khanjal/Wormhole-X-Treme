# The Wormhole Research Facility: a clean-slate replacement for the bot lab

The engineering design and its running addenda. The designer-facing brief is
[`design/facility/BRIEF.md`](../../design/facility/BRIEF.md).

A design, not an implementation. It replaces `scripts/player-test/lab*` and `scripts/run-lab.js`
outright: nothing below is adapted from them, and the only place they appear is the list of their
mistakes in section 6. The feature inventory comes from `src/main/java`, `config.yml`,
`plugin.yml` and the guides under `docs/guide/`, not from the old lab or its docs.

Two fixed points from the plugin itself shape everything else:

- **No lab code in `src/main`.** The facility is built and driven from outside the plugin, the
  way a server admin would do it: console commands, a datapack, a Mineflayer player. Anything
  the facility cannot do that way is a gap in the plugin's admin surface, which is itself a
  finding.
- **Minecraft 1.20 to 26.x on one server jar per run.** Every facility mechanism must work
  across that range or be switched in exactly one place. Section 6 lists which ones switch.

---

## 1. Feature inventory

Everything a tester should be able to exercise, from the source. The last column says what the
current lab does with it: **yes**, **part**, **no**, or **n/a** (needs a plugin the lab server
does not have; see the note under the tables).

### 1.1 Stargates

| # | Feature | Where in the source | Current lab |
|---|---|---|---|
| G1 | Nine shipped shapes: Minimal, Standard, Large, Grand, Massive, Horizontal, and the three `SignDial` files | `resources/shapes/gate/*.shape`, `StargateShapeRegistry` | yes |
| G2 | Four material groups, identified by frame block; `chevron:` block (REDSTONE_LAMP on Standard; COPPER_BULB on 1.21+) accepted or not | `MaterialGroupRegistry`, `config.yml` | part: groups yes, unlit chevron blocks never built |
| G3 | Autodiscovered groups from a shape framed in an unknown material; `gate-material-groups-autodiscover` | `MaterialGroupRegistry` | no |
| G4 | Building by hand: `gate build <shape> [group]`, lay the frame, click DHD, `gate complete <name> [idc=] [net=]` | `Build`, `Complete`, `StargateHelper` | part: build pad only, always via preview place |
| G5 | Previews: `activate`, `iris`, `material <group>` / `-frame|-chevron|-light|-portal|-iris|-sign`, `chevrons`, `dhd`, `needs`, `guide`, `layer`, `share`, `place`, `clear`; `gate-preview-minutes`, `gate-preview-max-blocks`; preview over a standing gate fills its gaps | `model/preview/*`, `PreviewPlacer` | part: only `place` |
| G6 | Console build: `gate build <shape> <name> <world> x y z <facing> [net=] [idc=]`, `~` coordinates, from command blocks | `GateConsoleCommands` | part: used as a fixture, never as the thing under test |
| G7 | Dialling: DHD then `/dial <gate> [idc]`; dial sign right-click forward / left-click back; redstone on `[RD]`; console `gate dial <from> <to> [idc]`; `gate force` past refusals | `StargateDialManager`, `WormholeXTremePlayerListener`, `Dial`, `Force` | part: no left-click, `force` only as a reset tool |
| G8 | Dial-spin patterns: top, chevron, lap, fill, pegasus, chase, universe, overshoot, none; per gate `edit spin`, per group `dial-spin`, `gate-dial-spin` | `DialSpin`, `DialSpinPattern`, `GateEditCommand` | part: only whatever the group implies |
| G9 | Refusals: iris shut (no code / wrong code), target already active, another gate already dialled at target, `same-world-only`, `wormhole.network.use.<net>`, use cooldown | `StargateDialManager`, `GateInteractionHandler`, `StargateRestrictions` | part: iris only |
| G10 | Wormhole is one way: entering the destination ring pushes a player out | `WormholeXTremePlayerListener` | no |
| G11 | Timeouts: `timeout-activate`, `timeout-shutdown` (0 = until used), `max-open-seconds`, `redstone-extend-open-time` | `StargateLifecycle`, `TimeoutsCommand` | no |
| G12 | Iris: code via `complete idc=` or `edit idc`; `-clear` removes the lever; lever at `:IA`; owner may set without `wormhole.config` | `GateIris`, `WXIDC` | part |
| G13 | Iris animation: sweep, spiral, rows, columns, instant; `gate-iris-step-ticks`, `gate-iris-sweep-max-ticks`; per gate / per group override | `IrisSweep`, `StargateIrisAnimator`, `IrisLayering` | no |
| G14 | Drawn iris on upright gates (two layers, swaps as you walk round), horizon look-alike in ice behind a glass iris (`gate-iris-horizon-ticks`), real-block iris on Horizontal | `DrawnHorizon`, `IrisLayering` | no |
| G15 | A shut iris holds: walker bounced, minecart pushed back, arrow/item destroyed at either end, no building in the opening | `GateInteractionHandler`, `ProjectileGateTracker`, `WormholeXTremeBlockListener` | part: walker only |
| G16 | Redstone: `[RD]` trigger by lever, button, plate, detector rail, dust/repeater/comparator; `[RA]` output lever; signal on DHD mount block; per-gate `edit redstone`; signal drop shuts a `timeout-shutdown: 0` gate; one trigger per quarter second | `WormholeXTremeRedstoneListener`, `GateRedstoneWrite` | part: lever on RD |
| G17 | Travellers: player; minecart and boat with passengers re-seated; ridden horse, camel, pig, donkey, llama, strider; tamed wolf, cat, parrot within 12 (not sitting); arrows, tridents, snowballs, eggs, ender pearls (teleport the thrower), potions, fireballs; mobs, items, XP orbs, armour stands by sweep (`entity-scan-interval-ticks`); item frames and paintings never | `RiddenTeleport`, `PetEscort`, `ProjectileGateTracker`, `GateEntityScanner`, `WormholeXTremeVehicleListener` | part: walk, boat, minecart, horse, wolf |
| G18 | Cross-world: overworld to nether and back; the End | `StargateManager`, `WorldUtils` | part: nether one way |
| G19 | Signs: name sign (name / network / owner lines) and dial sign colours, `»«` markers, `sign-glowing-text`, `sign-dial-match-material` | `SignStyle` | no |
| G20 | `gate edit` fields: portal, iris, light, group (`-clear`), woosh, redstone, custom, idc, owner, spin, iris-animation | `GateEditCommand` | part: portal, redstone, custom, idc |
| G21 | Upkeep: `remove [-destroy]`, `regen [-shape] [-fill] [-water] | -all`, `validate <gate|-all>` (WorldEdit-torn gate), `refresh`, `shapes reload|validate`, `import`, `list [net]`, `go`, `compass [reset]`, `owner` | `RegenerateCommand`, `ValidateCommand`, `GateShapesCommand`, `Compass`, `WXList` | part: remove, list |
| G22 | A custom `.shape` file dropped in `shapes/gate/`, and `[C]`, `[S:C]`, `[RS]` cells | `ShapeFileValidator`, `GateBlueprint` | no |
| G23 | Sounds: activate, chevron, lock, kawoosh, ambient (+ ticks), close, iris open/close; `gate-sound-volume`; `none`; pitch and volume scaled by shape width / `SOUND_SCALE` | `GateSounds`, `Sounds` | no (audible by accident) |
| G24 | `gate-arrival-splash-ticks`; woosh depth | `WooshSequence`, `WooshDepthCommand` | no |
| G25 | Protection: frame refuses a pickaxe; only owner / op / `wormhole.config` / `remove.all` may build in an opening; explosions | `WormholeXTremeBlockListener`, `WormholeXTremeEntityListener` | no |
| G26 | Networks: `net=`, `wormhole.network.use/build.<net>`, `list [network]` | `StargateNetwork`, `ComplexPermission` | no |
| G27 | Events: `StargateActivated/Created/Removed/Shutdown/PlayerTravel/MinecartTeleport`, `RingTravelEvent` | `events/*` | n/a (needs a listener plugin) |

### 1.2 Transport rings

| # | Feature | Source | Current lab |
|---|---|---|---|
| R1 | ODD and EVEN patterns; any slab, one kind, one facing; bottom slabs = floor ring, top slabs = ceiling ring; double slabs refused | `RingPattern`, `RingSurvey`, `RingTemplate` | part: floor only |
| R2 | `ring create` twice pairs; slabs consumed only when paired; `ring cancel` gives them back; `ring remove` lays both out again | `RingManager`, `RingPair` | part: no cancel; remove used as reset |
| R3 | Headroom 4 above a floor ring; ceiling room 4–10 (`ring-max-ceiling-drop`); not overlapping a ring or a gate; `ring-min-separation`; `ring-max-link-distance` 256 / `-height` 384 (0 = unlimited); refusal names the fault | `RingSurvey`, `RingBlockage`, `RingMessages` | part: distance 12–30 only |
| R4 | Countdown, step clear to abort; arrival must be clear with solid ground (water/lava not ground); refused trip costs nothing; `ring-outline-on-refusal` | `RingCycle`, `RingOutline`, `BukkitGround` | no |
| R5 | Everything inside travels: players, mobs, items, vehicles, ridden horse; both ends swap in one instant | `RingTransit`, `BukkitRingPassenger` | part: bot on foot only |
| R6 | Cooldown 30 s (`ring-cooldown-ticks`) and the "how long is left" message; the outline shown to a waiting player | `RingCycle` | no |
| R7 | Access: `ring-default-access`, `edit access public|private`, `allow`, `deny`, `owner`; `wormhole.ring.*` nodes; quota `ring-max-pairs-per-player` / `unlimited` | `RingAccess`, `RingPermissions` | no |
| R8 | `edit ring|light|flash|built|name|style|reset`; per-end vs per-pair scope; `name` announced at the other end | `RingCommand` | part: style only |
| R9 | Console: `ring build <world> x y z x y z`, `ring fire <id>` / `<world> x y z`, `~` relative to a command block | `RingConsoleCommands` | part: build only |
| R10 | Timing knobs: countdown, deploy, settle, flash, hold, lights-linger, reach; the drawn (not built) stack | `RingAnimator`, `RingPhase` | no |
| R11 | Sounds: open, ring (pitch climbing), flash, close, refused | `RingSounds` | no |
| R12 | Pets follow through a ring | `PetEscort` | no |

### 1.3 Beaming

| # | Feature | Source | Current lab |
|---|---|---|---|
| B1 | `beam to <name>`: own places first, then public; `/wormhole go <name>` reaches the same | `BeamManager`, `Go` | part: public only |
| B2 | Places: `place set|create|remove|list`; public: `admin set|remove|list`; `admin cost <name> <amount>|-default` | `BeamCommand`, `BeamDestination` | part: admin set/remove |
| B3 | `admin goto <player|dest|x y z [world]>`, `admin send <target> ...`, from console and command blocks; `wormhole.beam.admin.teleport` separate from `beam.admin` | `BeamCommand`, `BeamPermissions` | no |
| B4 | Arrival facing as saved; nearest safe spot if the ground changed; unloaded world refused with a message | `BeamTravel`, `BeamPoint` | part: facing only |
| B5 | Cross-world | `BeamTravel` | part: nether |
| B6 | Cooldown (`beam-use-cooldown-*`), cost (`beam-economy-use-cost`), admin bypass | `BeamCooldown` | no |
| B7 | Timing: envelop, vanish-at-step, rise, teleport-at-step, descend, fade; clamping; read at start of a beam | `BeamTiming`, `BeamAnimation`, `BeamFreeze` | no |
| B8 | Sounds: charge, depart, arrive, volume | `BeamSounds` | no |
| B9 | Horse under the traveller; pets alongside | `BeamMount`, `PetEscort` | yes |
| B10 | Hidden while enveloped (`BeamVisibility`) — visible to another player? | `BeamVisibility`, `HiddenEntities` | no |

### 1.4 Quantum mirrors

| # | Feature | Source | Current lab |
|---|---|---|---|
| M1 | `mirror create <name>` looking at a banner; console form with coordinates; rename by re-creating; move a known name to another banner; the one refusal (name elsewhere and banner already another mirror) | `MirrorCommand`, `MirrorManager` | part: console form |
| M2 | `mirror-per-world-limit` (default 1); the round of other mirrors is the worlds you can reach | `MirrorNetwork` | no: the lab sets 0 and keeps every mirror in one world |
| M3 | Right-click walks the others by name; own room never in the round; three-second hold when someone else is there; `-start <mirror>|-none` | `MirrorInteraction`, `MirrorSettle` | part: no start, no hold |
| M4 | Punch to travel; land in front of the far banner facing out; pets follow | `MirrorArrival`, `PetEscort` | part |
| M5 | Wall rules: solid one block out on every side; warning at two; a banner on a post refused; two banners wide makes one 2×2 mirror; banner and wall protected while a mirror | `MirrorPlacement`, `MirrorBlock` | no |
| M6 | Proximity view (`mirror-proximity-distance`, `-ticks`), real-block capture from the arrival point, `-capture` retake, `mirror-view-depth` 4–160, whole-room vs through-the-opening view, fog on Paper (`mirror-fog-at-depth`), creatures hidden, lava/water rules | `MirrorWindows`, `MirrorCapture`, `MirrorView`, `MirrorFog`, `MirrorSight` | no |
| M7 | Looks: 90 shipped `.mirror` files; `-stamp <look>` and `-stamp` from the room (biome frame, dominant colours); patterned banner keeps its pattern | `MirrorStamp`, `MirrorPresetRegistry`, `MirrorPalette` | part: named stamps |
| M8 | Approach message above the hotbar; `mirror-approach-message`; only near a mirror (1.9.0) | `MirrorSignpost`, `MirrorProximity` | no |
| M9 | `mirror debug [name] [-all|-full|-on|-off]`, `mirror list` | `MirrorCommand` | part: used as a wait |
| M10 | Plain 1.20: banner stays in front of the view (no `setVisibleByDefault`) | `MirrorPackets` | no |

### 1.5 Cross-cutting

| # | Feature | Source | Current lab |
|---|---|---|---|
| X1 | `/wormhole config <setting> [value]`, search by substring, both spellings, fixed-value refusal; every setting in `DefaultSettings` (about 120 keys) | `ConfigCommand`, `DefaultSettings` | part: three settings poked as fixtures |
| X2 | `pets-follow-owner` across gate, ring, beam, mirror; cats and parrots; a sitting pet stays | `PetEscort` | part: wolf by gate and beam |
| X3 | Freya, `/wormhole freya on|off`: a cat only her owner sees, kept away in bed and when hunted, travels as a pet, re-summoned if a transport drops her | `model/freya/*` | no |
| X4 | Permissions: every node in `plugin.yml`; op outranks a negated node; `permissions-support-disable`, `permissions-auto-fallback`; `wormhole-use-is-teleport` | `WXPermissions`, `PermissionsSupport` | no |
| X5 | Use cooldowns: gates (`use-cooldown-*`), beams | `StargateRestrictions`, `BeamCooldown` | no |
| X6 | Economy use/build cost and beam cost | `EconomySupport` | n/a (Vault + economy plugin) |
| X7 | Placeholders, CoreProtect, metrics switch, Help registration | `plugin/*` | n/a / no |
| X8 | Storage: `data/` YAML, legacy folder migration, `gate import` from SQLite, shape file refresh with `.old` copies | `StargateYamlManager`, `LegacyDataFolderMigration`, `LegacyDatabaseImporter`, `ShippedShapes` | no |
| X9 | Startup log: `Enable Completed`, the two allowed warnings, nothing else | `WormholeXTreme` | yes (self-test only) |
| X10 | Chunk tickets held while a wormhole is open (`ChunkTickets`); a trip into an unloaded far end | `utils/ChunkTickets` | no |

**On n/a rows.** Economy, placeholders and CoreProtect need other plugins. The facility keeps a
`plugins-extra/` drop folder that the launcher copies into the server: put Vault and an economy
plugin there and the Systems room's economy tests light up; leave it empty and they read
"needs Vault" rather than failing. Events (G27) stay with the unit tests.

**On DHD distance.** The brief says DHDs can sit at any distance from the ring. The source says
otherwise today: the DHD is the `:A` cell of the shape, and `#46` (`docs/GATES.md`, "This is
why sign-dialling should not be a shape at all") is the plan to make it a property of the gate.
The facility does not test it yet, but the gate hall's test stand is drawn with a detached DHD
plinth and a clear 12-block apron so nothing has to be rebuilt when #46 lands.

---

## 2. The facility

### 2.1 Concept

The **Wormhole Research Facility**: a single-storey campus of white laboratories round a central
operations atrium, on a flat world, above ground, with the two offworld sites (a Nether range
and an End annex) that the cross-world tests need. It is a *research* facility, not a military
base: every chamber is a glass-walled test cell with an observation gallery, labelled like an
exhibit, so a tester can see the whole of a test from outside it and read its state without
opening chat.

**Departments** (one wing each, one colour each, used on the floor stripe, the wing's text
displays and its chat messages):

| Wing | Department | Colour | Tests |
|---|---|---|---|
| North | Gate Dynamics | cyan | G1–G26 |
| East | Ring Transit | lime | R1–R12 |
| West | Beam Physics | light blue | B1–B10 |
| South | Mirror Optics | magenta | M1–M10 |
| North-east | Menagerie and Motor Pool | orange | G17, R5, B9, X2, X3: every kind of traveller, kept ready |
| Atrium mezzanine | Systems | yellow | X1, X4, X5, X6, G11, G23 and every other config knob |
| Nether | The Range | red | the far end of every cross-world test |
| End | The Annex | purple | the third world, for mirror rounds and End travel |

**Palette.** White and light-grey concrete for walls, smooth quartz floors, polished deepslate
skirting, tinted glass observation windows, glass roofs over the halls (the sky lights them, so
no lamp is needed and a wormhole's glow reads), sea lanterns set in the floor as guide lines,
waxed copper (oxidised for the older-looking Range) for consoles and trim, iron bars for
railings, and the department colour in a one-block stripe along each wing's floor and door
frame. Nothing in the palette is newer than 1.20 (copper bulbs and pale oak are avoided so the
same blueprint builds on every version).

**Every chamber has the same four things**, in the same place, so a tester learns one chamber
and knows them all:

1. **The cell**: the volume the test happens in, glass on the gallery side, hazard stripe at
   the threshold. The blueprint declares the cell's box; `reset` is defined as "put this box
   back as built", nothing more clever.
2. **The gallery**: a raised walkway behind tinted glass looking into the cell, with a seat
   position the `Watch` action teleports you to.
3. **The board**: a floating text display over the cell door — the chamber's name, its current
   settings, the last result in green or red with the reason, and the time. Readable from
   twenty blocks.
4. **The pylon**: a 1×3 column by the door whose top block is lime (idle, last run passed),
   yellow (running), red (last run failed) or grey (never run). Visible from across the atrium.

### 2.2 Floor plan

Feet at y = 0: the flat world's top layer is at y = -1 (`generator-settings` layers: bedrock 1,
deepslate 31, stone 31, smooth quartz 1), so every coordinate below is at feet level and cell
heights are "blocks above the floor". x runs east, z runs south. Interior sizes given; walls are
one block outside them.

```
                       z = -170
        +-----------------------------------------------+     +--------------------+
        |            GATE DYNAMICS HALL (N)             |     |  MENAGERIE (NE)    |
        |  x -70..70   z -170..-40   36 high            |     |  x 80..130         |
        |                                               |     |  z -120..-60       |
        |  [G5 Iris]   [G1 Test Stand]    [G2 Gallery]  |=====|  stables, kennel,  |
        |  [G4 Bench]  [Relay gate]       [G3 Automat.] |     |  boathouse, rails, |
        |                                               |     |  range for arrows  |
        +----------------------+------------------------+     +--------------------+
                               |  9-wide corridor, 12 long
   +----------------+    +-----+------+    +----------------+
   |  BEAM PHYSICS  |    |            |    |  RING TRANSIT  |
   |  (W)           |====|    OPS     |====|  (E)           |
   |  x -110..-40   |    |  atrium    |    |  x 40..110     |
   |  z -30..30     |    |  x -20..20 |    |  z -30..30     |
   |  12 high       |    |  z -20..20 |    |  14 high       |--- ring range tunnel
   |                |    |  16 high   |    |  R1 R2 R3 R4   |    x 110..320, 5 wide
   +----------------+    +-----+------+    +-------+--------+
                               |                   |
                         +-----+------+     shaft R3 down to y = -60
                         |  MIRROR    |
                         |  OPTICS(S) |
                         |  x -40..40 |
                         |  z 40..90  |
                         +------------+

   Nether "Range"  at nether  0, 64, 0 : 61 x 61 glass dome, its own gate, pad, ring, mirror
   End   "Annex"   at end  1000, 60, 1000 : 41 x 41 platform, its own gate, pad, mirror
```

Sizes, and why:

- **Gate hall 141 × 131 × 36.** Massive is the widest shipped shape and Grand the deepest;
  36 high leaves a margin over both stood on a plinth, and the hall is wide enough to hold the
  six-shape gallery (G2) in a row along the back wall with ten blocks between gates, so their
  arrival points never overlap another's frame.
- **Ring lab 71 × 61 × 14.** A ceiling ring needs a room 4–10 tall; the lab has a 12-high main
  floor for floor rings and a 6-high side room for the ceiling ring. Distances to 60 fit
  inside; the **range tunnel** east reaches 320, so both the 256 limit and a refused 257 are
  testable. The **shaft** under R3 drops 60 for the height rule, with a ledge every 20.
- **Beam lab 71 × 61 × 12.** Six pads; the size is for the "landed on the nearest safe spot"
  test, which needs a pad you can cut the floor from under.
- **Mirror gallery 81 × 51.** Long enough for a mirror to sit more than twice `mirror-view-depth`
  (at a lowered depth of 16) from its neighbours, and to have one pair deliberately closer for
  the "another mirror too near" warning.
- **Ops 41 × 41 × 16** with a mezzanine (Systems) at +6.

**Getting around.** Every wing door is within 30 blocks of the atrium centre, and:

- the **transit ring** in the atrium: eight pressure plates on a compass rose, each a
  command-block `tp` to a wing's door (vanilla, works with the bot down);
- `Go` in the chat console (section 4) or `!go gates`, `!go range`, and `Watch` on any chamber
  puts you in its gallery seat;
- the far sites are reached by their own gates: the Relay gate in the gate hall is on the same
  network as the Range and Annex gates, so a tester can also just dial there.

### 2.3 Modes for a tester

You join in adventure mode in the atrium. Three modes, switched from the console or by walking:

- **Observer** (default): adventure; the pylons, boards and bossbar tell you what the bot is
  doing; `Watch` moves you to a gallery seat.
- **Hands-on**: `Stage` builds a chamber's fixture and stops. You walk in and use it yourself:
  press the DHD, click the sign, step into the ring, punch the mirror. The facility does not
  judge what you did (it cannot see your intent); the fault pylon in Ops does judge the plugin,
  so "I did it, nothing lit red" is the pass. `Reset` when done.
- **Builder**: creative, inside a build cell only (G4 and R4). Stepping out of the cell puts you
  back in adventure. The bot notices you by position, once a second, from a scoreboard
  `wx_zone` value that a command block in the cell sets, not by polling gamemodes.

---

## 3. The chambers

Each chamber below lists: what it tests (by inventory number), its options, what `Stage`,
`Run` and `Reset` do, what the bot checks, and what the tester sees. Every chamber follows the
same verbs; `Run` is always `Stage` + the bot's trip + `Check`.

### 3.1 Gate Dynamics Hall

**G1 Test Stand** — the gate under test. A plinth 31 wide with a detached DHD apron in front,
the **Relay** gate (Standard, Standard group, fixed) 40 blocks across the hall, and rails, a
water lane and a run-up lane laid from the Motor Pool door to the stand.

| Option | Values |
|---|---|
| shape | the nine shipped shapes, plus `custom` (a `Lab.shape` the launcher writes into `shapes/gate/`, with `[C]` and `[S:C]` cells and an `[RS]`) |
| group | Standard, Atlantis, Universe, MilkyWay, Diamond (autodiscovered from `Lab.shape`, tests G3) |
| chevrons | frame (classic), chevron block (REDSTONE_LAMP / COPPER_BULB on 1.21+) |
| how built | preview place, by hand (the bot lays every block from `preview needs`), console form |
| dial | DHD + /dial, sign right-click, sign left-click, redstone lever, console `gate dial`, `gate force` |
| spin | default, and each of the nine patterns via `edit spin` |
| destination | Relay, Range (nether), Annex (End), Relay-busy (Relay already dialled elsewhere: expect refusal), self |
| traveller | walk, minecart, boat, horse, camel, pig, donkey, llama, strider, wolf, cat, parrot, sitting wolf (must stay), projectile (below), item dropped by the bot (Q at the opening), item thrown by a dispenser, item stack from a hopper-minecart spill, XP, armour stand, item frame (must not), zombie |
| projectile | arrow, spectral arrow, tipped arrow (effect must survive), crossbow bolt, piercing crossbow bolt, firework rocket from a crossbow, trident (loyalty: must come back through), snowball, egg, ender pearl (thrower teleported), splash and lingering potion (effect must survive), fireball (dispenser fire charge; ghast fireball in the Range), wind charge (1.21+, hidden below), llama spit |
| launcher | bot by hand, dispenser behind the stand (no player shooter), the tester |
| angle | square on, glancing (30° off the gate's normal), through a horizontal gate from above and below, point-blank inside the ring |
| far iris | open, shut with code, shut without code; with any projectile or dropped item |
| portal | group's, LAVA, NETHER_PORTAL, WATER |

`Stage`: clear the cell, build the gate as the option says, apply edits, set the far iris, lay
the lane the traveller needs. `Run`: dial as the option says; watch the chevrons light in the
shape's order (the bot reads the `:L` blocks from the shape file and checks each changes to the
group's light block in that order, which is the light-order test G8 needs); wait for the
kawoosh; send the traveller; check arrival. `Check` differs per traveller:

- walkers, mounts, vehicles: the bot (or its vehicle) is within 1.5 of the destination's `:EP`
  / `:EM` block, still seated if it was; for a mount, the mount is the same entity UUID (the
  brief's hard lesson about re-created entities across worlds is handled by matching the
  `Tags` the bot gave it, not the id);
- pets: the pet with the run's tag is within 6 of the bot at the far end within 5 s; a sitting
  pet is still at the near end;
- projectiles: the projectile with the run's tag exists on the far side, still moving away from
  the exit on the first tick seen there (flew through, not dropped out), and none is left on the
  near side; its owner is the launcher (kill credit), a tipped arrow or potion keeps its effect,
  a fireball keeps its direction and explodes on the far target, a loyalty trident returns to
  the bot through the gate; the pearl: the bot itself arrived; two facing connected gates hand a
  shot back at most `MOST_CROSSINGS` times; the shut-iris case: no projectile anywhere after 2 s
  and nothing hit behind the iris;
- dropped items: the stack with the run's tag, same count and NBT (a named, enchanted item),
  at the far end within the sweep interval + 2 s; with a shut iris it is gone at both ends;
- sweep travellers (zombie, item, XP, armour stand): the entity is at the far end within
  `entity-scan-interval-ticks` + 2 s; the item frame is where it was hung;
- refusals: the opening stays air and the plugin's refusal line was heard (each refusal's text
  is taken from `ConfigManager`'s message keys, not guessed);
- afterwards: the near gate shuts by itself within `timeout-shutdown` + 5 s, and the far
  gate's opening is air.

`Reset`: `gate remove Stand -destroy`, `gate force Relay|Range|Annex`, `edit ... idc -clear`,
kill tagged entities in the cell and at the far end, `function wx:reset/g1` for the blocks.

*Tester sees*: the bossbar counting the steps ("3/7 dialling Relay by sign"), the chamber
board's settings line, the gallery seat looking along the lane, the pylon.

**G2 Shape Gallery** — the six ring shapes, one each, built once in the default group along the
north wall, named `Minimal`…`Massive`, all on network `Gallery`. Permanent: nothing here is
rebuilt. It is the manual playground (dial any to any; the dial sign on `StandardSignDial`
here has five neighbours to scroll), the fixture for G19 (sign colours: the bot rewrites
`sign-color-*` via `config` and reads the sign back with `data get`), for G21 (`validate` after
the bot knocks a frame block out with `setblock`; `regen -fill` puts it back; `regen -shape`;
`-water` after the bot floods an opening), for G25 (the bot, de-opped for the run, tries to
break a frame block and to place in an opening), and for G10 (the bot walks into the
destination ring of an open pair and must be pushed out). Options: which gate, which check.

**G3 Automation Bay** — a `StandardSignDial` gate whose `[RD]` is fed by a wall of inputs, each
on its own line with a lever to select it: lever, stone button, pressure plate, detector rail
on a loop of track, repeater chain, comparator; its `[RA]` drives a lamp strip over the door;
and a command-block console with the console forms (`gate build … ~ ~ ~`, `gate dial`,
`gate force`, `ring build`, `ring fire ~`, `mirror create … ~`, `beam admin send`). Options:
input, `redstone-extend-open-time`, `timeout-shutdown` 0 / default, `max-open-seconds` short.
Checks: the gate opens once per trigger and not twice (a double pulse must be one dial), the
lamp strip lights while open and goes out on shutdown, the cart on the loop dials and rides
through, dropping the signal shuts a `timeout-shutdown: 0` gate, a steady re-trigger holds it
open until `max-open-seconds` and no longer.

**G4 Build Bench** — creative cell, bare floor, a copper plinth with a button on it. Tests G5.
`Stage` stands a preview on the plinth for *you* (the bot cannot see your preview; it runs
`gate build` as you by `execute as`? No: previews are per player and the command is yours to
run). So this chamber is hands-on first: the board lists the preview actions to try as a
checklist, and `Run` is the bot's own pass through them: build, `needs` (the bot reads the
count and lays exactly that many blocks by hand, then `guide` must report complete), `layer`,
`chevrons`, `dhd`, `material` per role, `iris`, `activate` (the bot checks its own client saw
the fake water), `share` to the tester (you say `y`/`n` in chat, the one place the facility
asks you something), `place`, `complete`; then `preview` over a gate with one block missing
fills it (G5 last row). `Reset` clears previews (`preview clear -all`) and the cell.

**G5 Iris Chamber** — two fixed Standard gates facing each other twenty apart, one in
Atlantis (glass iris) and one in Standard (stone), both with a code, on their own network so
G2's gates never dial them. Options: animation style (five), `gate-iris-step-ticks`,
`gate-iris-sweep-max-ticks`, `gate-iris-horizon-ticks`, side (front / behind / round the side),
which gate. `Run`: shut the iris at the lever, count the drawn steps the bot's client
received (block-change packets in the opening, grouped by tick) against the expected count for
the style and limit; open it; dial through and walk from the chosen side to check the
two-layer drawing (the bot samples the block it is shown in and one out from the plane, from
each side); fire an arrow at the shut iris; try to place a block in the opening while shut.
The tester's part is visual, and the gallery here has three seats: front, behind, side.

### 3.2 Ring Transit Lab

**R1 Pair Stand** — the main floor. Options: pattern, slab (six kinds plus a data-pack-ish odd
one, `cut_copper`), style, distance (8 = refused by `ring-min-separation`, 12, 40, 250, 257 =
refused, via the range tunnel), access (public; private with the bot allowed; private with the
bot denied = refused), traveller (walk, horse, zombie, item, minecart, and *with the tester*:
a swap, the one test that needs two bodies — the bot waits in one ring for you to step into
the other, or a second bot with `--probes 2`), timing preset (default, slow, instant).
`Stage` lays both circles and pairs them by `ring build`; `Run` walks in, and checks: the
countdown ran (the pad's light block was drawn to the bot's client), the bot is at the other
end within 1 of its centre, the cooldown message comes on a second attempt within 30 s, and
`ring list` shows the pair. `Reset`: `ring remove` by id, `function wx:reset/r1`.

**R2 Ceiling Room** — 6 high. Top slabs under the ceiling; the rings fall to the floor. Options:
room height (6, 10, 11 = refused), pattern. Checks as R1 plus "the stack stood on the floor"
(a block-change at floor + 1 on the bot's client).

**R3 Shaft** — a ring at the top of a 60-deep shaft and one at the bottom, with ledges: tests the
height rule and "going straight down is what they are for". Options: depth 20 / 40 / 60,
`ring-max-link-height` 384 or 30 (= refused).

**R4 Build Bench** — creative cell with two marked centres. Hands-on: lay circles, `ring create`
twice, `ring cancel` in between, mixed slabs, a double slab, a disc instead of a ring, a ring
overlapping the R1 pair's footprint, something standing in the arrival: the board lists the
refusal each should give (from `RingMessages`), and the fault pylon watches the log. `Run` has
the bot do the same list and read each refusal back.

**R5 Edit Desk** (a console beside R1, no cell): `edit name|light|flash|ring|built|reset`,
`allow`, `deny`, `owner`, then `Run` rides the pair and checks the announced far-end name and
the drawn light/flash blocks.

### 3.3 Beam Physics Lab

**B1 Pad Array** — six 3×3 pads on the floor, named `Pad-N/E/S/W` (each saved facing its
letter), `Pad-Trap` (whose floor the bot removes before beaming, for the safe-spot rule) and
`Pad-Far` in the Range; the Annex has `Pad-End`. Options: destination, kind (public, own place,
another player's place = refused, `go` command), traveller (bot, horse, wolf, cat, Freya),
cooldown on/off, cost (needs Vault), timing preset (default, long: `beam-rise-ticks 60`,
clamped: `beam-vanish-at-step 99`). `Run`: beam, check landing within 1 of the pad and yaw
within 5°, second beam inside the cooldown refused with the message, the long preset takes at
least the sum of its phases (the bot times it from the charge sound... no: from the
`hidden` → `shown` transition of its own entity, which the tester can see too).

**B2 Dispatch Desk** — `admin goto`, `admin send` of the tester or the bot to a player, a
destination, coordinates in another world; `wormhole.beam.admin.teleport` withheld for one run
(the bot de-ops itself, runs, re-ops by console) so the refusal is seen.

### 3.4 Mirror Optics Gallery

Mirrors live in three worlds, so the default per-world limit is respected: `Atrium` (overworld,
in the gallery), `Range` (nether), `Annex` (End). With `mirror-per-world-limit 3`, the gallery
adds `Wide` (two banners), `Near` (deliberately within twice the view depth of `Atrium`) and
`Yours` (a bare wall for the tester's banner).

**M1 Round** — Options: from, to, start (`-none` or a name), hold (alone / with the tester
standing there, expecting the three-second hold), look (mirror, five named looks, stamp from
room), view depth (16, 160), approach message on/off, fog on/off (Paper only; the bot checks
the packet arrived, the tester checks the sky). `Run`: walk up (the banner must give way: the
bot's client sees the opening's blocks change), right-click until `to` shows (counting clicks
against the expected order by name), punch, land in front of `to` facing out, and the
approach line must have shown above the hotbar (the bot reads action-bar packets).

**M2 Wall Bench** — creative. Hang banners on: a solid wall, a wall with a gap one out, one
with a gap two out, a post, a wall with another banner beside it. `Run` reads `create`'s
answer for each (refusal, warning, 2-wide) and then tries to break the `Atrium` banner and its
wall (must be refused) and to `remove` it (must work).

**M3 Capture Desk** — `-capture` after the bot rebuilds the room behind `Atrium` in a different
block; the bot walks up again and its client must see the new block in the opening.

### 3.5 Menagerie and Motor Pool

Not a test chamber: the supply of travellers. A stable (horse, camel, donkey, llama, pig with
carrot on a stick, strider on a lava trough), a kennel (wolves, cats, parrots, one wolf told to
sit), a boathouse on a canal that runs to the gate hall's water lane, a rail yard with a
chest minecart and a hopper cart on the loop to G1 and G3, an armoury (bow, crossbow
with multishot and piercing, loyalty trident, spectral and tipped arrows, fireworks, fire
charges, wind charges on 1.21+, snowballs, eggs, pearls, splash and lingering potions, named and
enchanted items to drop), a dispenser rack that can be aimed at each gate, and a range with a target block; item frames and an armour
stand. Every animal is summoned with a `Tags` entry, tamed by console (`data merge` of `Owner`),
and the chambers ask the Menagerie for "a horse, saddled" and get one teleported to the lane.
Freya lives here: `/wormhole freya on` as the bot, and she is one traveller option in every
chamber (X3).

### 3.6 Systems (the mezzanine over Ops)

A console, no cell. Every setting in `DefaultSettings` is listed on the board with its current
value, read back through `config <setting>` at start-up, grouped as `config.yml` groups them.
Actions: set a setting (the chat console offers each fixed-value setting's values), **snapshot**
and **restore** (the facility restores the snapshot after every run that changed a setting,
so no chamber leaks config into the next), and the **audits**: `config` search by substring
returns the right count; a bad fixed value is refused; both spellings work; `metrics-enabled
false` at once. Permissions: the bot can `deop` itself for a run and hold a named set of
`ops.json`-free expectations (what a default player may do: `wormhole.ring.use`,
`beam.use`, `beam.place` true; the rest false); with LuckPerms in `plugins-extra/` the same
runs go through it. Sounds: a "silent" preset (every `*-sounds-enabled false`) and a "loud"
one (`*-sound-volume 2`); the bot cannot hear, so the check is the sound packet count on its
client per event, which is also how "`none` goes quiet" is verified.

### 3.7 The Range and the Annex

The Range (nether) is a glass dome with a Standard gate `Range`, a beam pad `Pad-Far`, a ring
end whose partner is in the ring lab (tests "same world only": expected refusal), and the
mirror `Range`. The Annex (End) has `Annex` gate, `Pad-End`, mirror `Annex`. Both are
forceloaded at start-up and the build waits for `execute in <world> if loaded` before laying a
block. A tester dials there from the Relay or takes the mirror.

---

## 4. Control and UX model

### 4.1 Recommendation: a chat console with in-world boards

**Control from chat, state in the world.** Every control is a clickable line the facility sends
you in chat; every state you need at a glance is a text display or a pylon in the world, plus a
bossbar during a run. No signs, no lecterns, no item menus, no maps.

The console, when you say `!` or click **Console** on the welcome line:

```
 ── GATE DYNAMICS · G1 Test Stand ──────────────────── idle · last: PASS 14:02
   shape     [Standard] Large Grand Massive Minimal Horizontal Standard+sign …
   group     [Standard] Atlantis Universe MilkyWay Diamond
   dial      [DHD] sign-right sign-left redstone console force
   traveller [walk] minecart boat horse … wolf cat parrot arrow pearl zombie …
   far iris  [open] shut+code shut-no-code shut+arrow
   ▶ Run   ◇ Stage   ↺ Reset   ⌖ Watch   ⟳ Again   ✎ Matrix
 [Ops] [Gates ▸] [Rings] [Beams] [Mirrors] [Systems] [Menagerie] [Range] [Annex]
```

Each word is a click; the bracketed one is current; hovering shows what the option means and,
for a value, which inventory rows it exercises. `Matrix` runs every combination of two chosen
options (shape × dial, say) and prints a grid of PASS/FAIL at the end. Typed forms do the same
thing for anyone who prefers a keyboard: `!g1 shape Grand`, `!run g1`, `!reset r2`, `!go range`.

**How a click reaches the bot.** A click runs `/trigger wx set <code>` as you. `trigger` is
vanilla, needs no op, has existed unchanged since 1.13, and Mineflayer receives scoreboard
updates as packets (`bot.on('scoreUpdated')`), so the bot sees the code and who set it with no
command block, no `say`, no log parsing. The bot then `scoreboard players reset`s you and
re-enables the trigger. Codes are `chamber * 1000 + option * 100 + value` and one table in the
console module maps both ways. Typed `!` lines arrive as ordinary chat.

**Why this and not the alternatives:**

| Model | Verdict |
|---|---|
| Signs + buttons on command blocks | Signs are the worst text surface across the range: right-click opens the editor on every supported version unless waxed, `data merge` text changed format at 1.21.5, four lines of 15 characters cannot hold a result, and reading them back needs the bot to look at them. Buttons need the tester to walk to the panel. Rejected. |
| Lecterns with clickable books | Click events in books work on every version, but the component format and click-event key names changed at 1.21.5 and again the tester must walk to the lectern. A book is the right place for the *handbook*, so one lectern in Ops holds it; not for control. |
| Command-block consoles | Good for the fixed physical verbs (a RUN button by each cell door, a `tp` plate) because they need no bot: kept for exactly those. Not for options: every option is a block and a wire. |
| Item / chest menus | Need plugin code. Nothing of the lab ships in the plugin. Rejected. |
| Maps | Read-only, 128 px, need a map item per player. No. |
| Chat only (typed) | Works everywhere, but a tester should not have to remember a grammar. Kept as the typed form of the same console. |
| **Clickable chat + `/trigger` + text displays + bossbar** | Zero blocks to build for control, works from anywhere in the facility, tab-completion irrelevant, hover text carries the documentation, one component builder is the only version switch, non-op safe. **Chosen.** |

**What a physical control is still for.** By each cell door: a big copper button on a command
block (`execute as @p[distance=..3] run trigger wx set <run code>`) so a tester who is standing
there and watching can just press RUN, and a `tp` plate to the gallery seat. Both work with the
bot absent (the button then does nothing, and says so on the board, which the bot rewrites).

**State surfaces**, in order of distance:

- **Bossbar** (`/bossbar`, vanilla since 1.13): one per running chamber, named for the step
  (`G1 · 4/7 · walking in`), progress = step / steps, yellow while running, green or red for
  three seconds at the end, then removed. Seen from anywhere in any world.
- **Board** (text display entity, present since 1.19.4): over each cell door; also the **Ops
  wall**, one line per chamber, and the **fault counter** (`Plugin log: 0 faults`) that turns
  red on the first WARN/ERROR/stack frame the bot tails from the server log, with the line.
- **Pylon**: the colour block by the door.
- **Action bar**: the bot's running narration to whoever is watching (`title @a actionbar`).
- **Chat**: the result line with the reason, and the console.

### 4.2 Pass/fail is a fact about the world

Every `Check` is a predicate on things the bot can observe from its client (its position, its
vehicle, the entities near it, the blocks it is shown, the packets it received) or from the
server (`data get`, `wx list`, the log). A run never passes because nothing threw; it passes
because each listed predicate was true. Every chamber's `verify()` returns a list of named
checks, and the board shows the first failed one by name: `FAIL · arrived: 4.2 from Relay :EP`.

---

## 5. Architecture and implementation plan

### 5.1 Pieces

```
scripts/facility/
  run-facility.js            launcher: server folder, world settings, datapack, bot, log tail
  facility.js                the bot process: builds, listens, runs chambers, self-test
  lib/
    text.js                  THE version switch for text components (sign, display, tellraw,
                             bossbar, book, click events): one function, three output contexts
    server.js                console pipe, log tail, `execute if loaded` waits, gamerule by version
    datapack.js              blueprint -> mcfunction files; pack_format by version; writes
                             wx:build/<wing>, wx:reset/<chamber>, wx:load
    blueprint.js             the declarative geometry: box, room, window, stripe, pylon, seat
    console.js               the chat console: menus, /trigger codes, `!` grammar, scoreboard
    board.js                 text displays, pylons, bossbar, fault counter
    probe.js                 the bot's body: walk, look, click, mount, dismount, ride, fire,
                             observe packets; entity by tag, never by id
    config.js                snapshot / set / restore of plugin settings through `wx config`
    menagerie.js             summon, tame, saddle, tag; "give me a horse at (x,y,z)"
  wings/
    ops.js  gates.js  rings.js  beams.js  mirrors.js  menagerie.js  range.js  annex.js
                             each: blueprint + chambers
  chambers/
    g1-stand.js g2-gallery.js g3-automation.js g4-bench.js g5-iris.js
    r1-pair.js  r2-ceiling.js r3-shaft.js r4-bench.js r5-edit.js
    b1-pads.js  b2-dispatch.js
    m1-round.js m2-wall.js m3-capture.js
    s1-systems.js
  selftest.js                the matrix `run-facility.js --selftest` runs locally, and the expected outcome per cell
```

A chamber is one object:

```js
module.exports = {
  id: 'g1', wing: 'gates', title: 'Test Stand',
  cell: { x0, y0, z0, x1, y1, z1 },          // what reset puts back
  seat: { x, y, z, yaw, pitch },              // Watch
  options: { shape: [...], dial: [...], ... }, // with a `why` per value for the hover text
  needs: (values) => ({ config: {...}, travellers: [...] }),   // declared, restored after
  refuses: (values) => null | 'a boat cannot drop into a flat gate',
  stage:  async (ctx, values) => {},          // fixture only
  run:    async (ctx, values) => {},          // the trip; returns nothing
  checks: (ctx, values) => [ { name, test: () => boolean|Promise } ],
  reset:  'wx:reset/g1'                       // an mcfunction, plus ctx.kill(tags)
}
```

`ctx` carries `probe`, `server`, `config`, `menagerie`, `board`, the run's tag prefix, and a
`step(name)` that advances the bossbar. Nothing in a chamber sends a raw command string to the
console except through `server.run`, so every command the facility ever sends is greppable.

**Generation.** `blueprint.js` describes each wing as boxes and fixtures; `datapack.js`
compiles that to `.mcfunction` files, one per wing and one per chamber reset, into
`<world>/datapacks/wx/`, with the folder name (`functions` before 1.21, `function` after) and
`pack_format` chosen by version. The bot runs `function wx:build/ops`, waits for the sentinel
block the function sets last, and goes on to the next wing. A wing is a few thousand `fill`s
executed inside one tick, so the whole facility builds in seconds, not the minutes a stdin
pipe takes, and a `reset` is a single command a tester can run without the bot
(`/function wx:reset/g1`). Fill boxes are split at 32768 blocks by the compiler; rooms are
compiled wall by wall so a split never leaves a floor across a doorway. The Range and the Annex
are built the same way after `execute in <world> if loaded` succeeds on their corners.

**The bot** is one Mineflayer player, `Probe`, opped, creative, with `--probes 2` adding
`Probe2` for the two-body tests. The tester is never asked to be a fixture except for the one
`share` question and the optional ring swap.

**Self-test** runs the `selftest.js` matrix: for each chamber, the cells a local self-test run should
prove, with expected outcomes (`PASS`, `REFUSED:<reason>`), then every reset, then the empty
check (`wx list`, `ring list`, `mirror list`, `beam list` all show only the fixtures), then the
fault counter must read zero. It exits non-zero on any mismatch.

### 5.2 Stages

Sizes: S = one session, M = two or three, L = four to six.

| Stage | Delivers | Size |
|---|---|---|
| 0 Spike | `text.js`, `datapack.js`, `console.js` proved on Paper 1.20.4, 1.21.11 and 26.1.2: a datapack function that builds a box, a text display that reads on all three, a tellraw menu whose click reaches the bot via `/trigger`, a bossbar. Nothing else. If any of these does not cross the range, the design changes here, not later. | S |
| 1 Shell | `run-facility.js`, the flat world, Ops with transit ring, boards, pylons, fault counter, `config.js` snapshot/restore, `probe.js` walk/look/click, `selftest.js` skeleton with one trivial chamber. `--selftest` runs locally on the three versions; no CI (see the addendum). | M |
| 2 Gates | Gate hall blueprint, Menagerie, G1 with every traveller and dial, G2 as a fixture. This is the largest single piece because the traveller list is long; `probe.js` gains mount/ride/fire/pet here. | L |
| 3 Rings and beams | R1–R5, B1–B2, range tunnel and shaft. | M + M |
| 4 Mirrors and the far sites | Range and Annex built and forceloaded, M1–M3 across three worlds, cross-world rows of G1 and B1 switched on. | M |
| 5 Deep gate work | G3 automation, G4 bench, G5 iris (packet-level checks), the custom `Lab.shape`. | L |
| 6 Systems | S1 console, settings audit, permission runs with a de-opped probe, sound-packet counts, `plugins-extra/` for Vault / LuckPerms. | M |
| 7 Polish | handbook lectern, `Matrix`, docs section in `docs/DEVELOPMENT.md`, delete the old lab. | S |

Stages 2 onward can go to Sonnet as mechanical work once the chamber contract from stage 1 is
settled; stage 0 and 1, and the packet-level checks in 5, need judgment.

---

## 6. Risks, and the current lab's mistakes to avoid

### 6.1 Cross-version differences the facility must handle

| Risk | Where it bites | Handling |
|---|---|---|
| Text components: JSON strings in NBT before 1.21.5, SNBT components from 1.21.5; `clickEvent/value` became `click_event/command`; `hoverEvent` likewise | signs, text displays, tellraw, bossbar names, books | one builder, `text.js`, with a table per version; stage 0 proves it on both sides of 1.21.5 |
| Datapack layout: `functions/` → `function/` at 1.21; `pack_format` changes every release (15 at 1.20, 48 at 1.21, 71 at 1.21.5, and so on; 26.x has its own numbering); an out-of-range pack still loads from the world folder with an "outdated" warning, but `supported_formats` keeps the warning down | `datapack.js` | a version → format table, `supported_formats` as a wide range, and the launcher checks `datapack list` shows `wx` enabled before the bot builds |
| Gamerules renamed to snake_case in 1.21.11 (`doMobSpawning` → `spawn_mobs`, `commandBlockOutput` → `command_block_output`, `doDaylightCycle` → `advance_time`, `logAdminCommands` → `log_admin_commands`) | server setup | `server.js` picks by version; never send both and let one fail (the failed one is a red line in the log the fault counter has to know to ignore) |
| Boats split by wood in 1.21.2 (`boat` → `oak_boat`); Mineflayer's own dismount sends a jump from 1.21.2 | Menagerie, probe | entity ids by version in `menagerie.js`; dismount always by console `ride <name> dismount` (vanilla since 1.19.4, so on every supported version); mounting stays a right-click, which is what a player does |
| Scoreboard packets changed at 1.20.3 (`set_score` gained number formats); Mineflayer's `scoreUpdated` must fire on 26.1.2 | console | stage 0 checks it; the typed `!` form is the fallback and is always on |
| `execute if loaded`, text displays and `ride` all arrived in 1.19.4 | everything above | inside the 1.20 floor; nothing the facility relies on is newer than 1.20 except where a row above says so |
| COPPER_BULB chevrons from 1.21; `pale_oak`, `resin` and other new blocks absent on 1.20 | G1 chevron option, palette | the option is hidden below 1.21; the palette uses nothing newer than 1.20 |
| `setVisibleByDefault` absent on plain 1.20.0 (M10) | mirror checks | the "banner gives way" check is skipped on 1.20.0 and the board says why |
| Mineflayer's newest version (4.39 speaks up to 26.1.2) trails Paper | launcher default | the default version is a constant in `run-facility.js`, and `--selftest` prints it |
| Java: 21 for 1.20.5–1.21.x, 25 for 26.x; `java` on PATH here is 8 | launcher | `--java` / `JAVA_HOME`, refusing early with the version it found |
| CI compiles the plugin against the 1.20 API | not the facility's problem, but the facility's *expectations* must not assume post-1.20 plugin behaviour | expected outcomes in `selftest.js` may be keyed by version |

### 6.2 Generation and loading

- **Forceload is asynchronous on newer servers** and the nether and End are not loaded at all
  until someone is there. Every build in another world waits on `execute in <world> if loaded`
  at the box's corners, with a bounded retry, before the first fill.
- **Chunk budget.** The overworld campus is about 230 × 330 blocks: some 300 chunks
  forceloaded, plus 16 in the nether and 9 in the End. That is fine for a test server with
  `view-distance 8`, but the ring range tunnel to x = 320 adds 14 more in a strip; forceload
  only the tunnel's chunks, not the rectangle round it.
- **One tick of fills.** A wing's function is thousands of fills in one tick; Paper's watchdog
  is not a concern (it is far under 60 s), but the client that is joined during the build will
  see a hitch. The launcher builds before it says "join".
- **Entity ticking.** Forceloaded chunks tick entities, so the Menagerie's animals live; they
  are fenced and `NoAI` until a chamber asks for one, so a horse does not wander into G1.
- **Water lanes and lava troughs** flow; the blueprint lays them as source blocks in a channel
  one deep and one wide with solid sides, and `reset` re-lays the channel, not the room.
- **The End at 1000,1000** is far from the main island's dragon and its fight state; the
  platform is over the void, so every seat and pad has a lip and the bot never walks off it.

### 6.3 Mistakes in the current lab, not to repeat

Read from `scripts/player-test/lab.js`, `lab/*.js` and `run-lab.js`, listed so the new code
does not inherit them:

1. **Console by file polling.** Commands are appended to `commands.txt` and a 100 ms timer
   copies new bytes to the server's stdin; the build is a few thousand of those, one per line,
   paced by `sleep`. The facility builds by datapack function and keeps the console for
   commands that must be live.
2. **Timing by `sleep`.** `await sleep(1500)` after almost every command, with the number
   chosen by feel; a slow CI runner turns those into flaky failures. Every wait in the facility
   is a wait *for something*: a block, a packet, a log line, a scoreboard value, with a
   deadline and a message naming what did not happen.
3. **Signs as state.** Panel state lived in sign text rewritten by `data merge`, read back by
   the bot looking at the sign, truncated to 15 characters on the mission board. State lives
   in the bot; the world shows it on text displays.
4. **Command blocks that `say`, and a regex on chat.** `[@] lab:next gate shape` parsed from
   `messagestr`, with `commandBlockOutput` turned off so the block's own echo did not double
   it. `/trigger` carries a number and a player name as data.
5. **Sending both names of a renamed gamerule** and letting one fail, which puts an error in
   the log that the warning filter then has to know about.
6. **One global `busy` lock** for the whole lab: a press on another bay while one runs is
   discarded with a chat line. The facility queues runs per probe and shows the queue on the
   bossbar.
7. **Polling gamemodes every two seconds** with `tag` and `gamemode` selector commands to keep
   pad zones creative: a steady stream of console commands for the life of the session. One
   command block per build cell sets a scoreboard zone value; the bot reacts to the change.
8. **Config leakage.** `wx config pets-follow-owner true` and `mirror-per-world-limit 0` are
   set at build time and by runs, and never put back, so later runs test a server whose
   settings depend on what ran before. `config.js` snapshots at start and restores after any
   run that declared a change.
9. **All mirrors in one world with the limit at 0**, so the default "one to a world" round,
   the thing a mirror is for, is never exercised.
10. **Entities by id and by type list.** A horse ridden into the nether comes back as a new
    client entity; kill commands enumerate `boat, oak_boat, minecart, …` per version. Every
    summoned thing carries a run tag and is found and killed by tag.
11. **Coordinates copied between files.** `lab.js` re-declares the ring pad area (`{ x0: 56,
    z0: -59, … }`) that `ring-bay.js` owns. One blueprint owns every box, and chambers refer to
    them by name.
12. **A self-test of a "spread"**, not a matrix: thirteen gate scenarios chosen by hand, and no
    expected *refusals* except one. `selftest.js` is a matrix with an expected outcome per
    cell, refusals included.
13. **Vacuous resets.** The old reset once counted a failed reset as a pass (its own fix commit
    says so). A reset is followed by the same `checks` list that judged the run, expecting the
    "empty" state.
14. **Fixture-under-test confusion.** Midway and Offworld are built by the console form of
    `gate build`, so a bug in that form breaks every gate run with a misleading failure. The
    facility builds fixtures by the *same* command, but proves that command first (stage 1's
    trivial chamber is exactly "build a gate by console and `validate` it"), and every
    chamber's failure line says whether the fixture or the trip failed.
15. **The watcher's action bar re-sent every 1.5 s forever**, by a timer that never stops.
    Narration is sent when it changes and once more after the fade, then stops.
16. **Hollow `fill` splitting** left floors between slabs, so rooms were built wall by wall
    with a comment explaining the trap. The compiler does the same, but as the compiler's
    rule, not something every room author has to know.
17. **The Offworld nether site was needed by two bays but owned by neither** (`OFFWORLD` is
    declared in `world.js`, `gate-bay.js` and `beam-bay.js` separately). The Range is a wing
    with its own file and the other wings ask it for its gate, pad and mirror by name.
18. **`--selftest` flagged any plugin WARN**, but the allowed list had to be kept in sync with
    a second copy in `player-boot.sh`. One allow list, in `server.js`, used by both the fault
    counter and the self-test.

### 6.4 Open questions for the user

- **Two probes by default?** The swap, the three-second mirror hold and "another gate already
  points at it" need two bodies. A second bot doubles the connection cost and nothing else.
  Recommendation: on by default in the self-test, off by default interactively.
- **Should the facility also run the CI journeys?** `journeys.js` stays as the CI trip suite;
  the facility's self-test is a superset in coverage but slower (three worlds to build).
  Recommendation: keep both until the facility's self-test has run green on the three CI
  versions for a month, then decide.
- **`Lab.shape`**: the custom shape with `[C]`, `[S:C]` and `[RS]` is the only way to test
  those cells, and it doubles as the autodiscovery fixture. It is a test asset, so it lives
  under `scripts/facility/assets/`, never in `src/main/resources/shapes`.

---

## Addendum: local only, no CI

Decided 2026-09-29: the facility runs locally (`node scripts/facility/run-facility.js [version]`
for hand testing, `--selftest [--versions 1.20.4,1.21.11,26.1.2]` for the automated matrix). It
is not a CI job. The full matrix will take a long time to automate and to run, so it may later
become a release-only check (run before tagging a release, by hand or by a manually dispatched
workflow), never a per-PR one. The CI player journeys stay as they are. This answers the second
open question in 6.4 for now: keep both, and the facility adds no CI.

## Addendum: what stage 0 proved (feature/facility-stage-0, 2026-09-29)

All four mechanisms pass on Paper 1.20.4, 1.21.11 and 26.1.2 (`scripts/facility/spike.js`).
Corrections to sections 4.1 and 6.1:

- **The trigger objective must be in a display slot**, or the server never sends its scores to
  clients: `scoreboard objectives setdisplay sidebar.team.dark_gray wx` (no one is on that team
  colour, so no one sees it).
- **Read the raw `scoreboard_score` packet, not Mineflayer's `scoreUpdated`**: in 4.39 that event
  never fires from 1.20.3 on (it tests a removed `packet.action` field and ignores `reset_score`).
- **Wrong text formats fail silently**: old click keys are accepted but the click is dropped on
  1.21.5+, and a display in the wrong form reads empty or shows raw JSON. Judge every menu and
  board by what the client receives, never by the absence of a log error.
- **An out-of-range pack_format loads with no warning**; the guard is the version table checked
  against the vanilla jar's `version.json` (26 / [94,1] / [101,1] on the three versions).
- Paper stdout needs `-Dstdout.encoding=UTF-8` on Windows; Paper's `version` banner can interleave
  with command output; entities in unloaded chunks are not found by `data get`, so forceload stays.
- Two bots in the spike: Probe (opped) and Tester (never opped) proves a non-op can trigger.
- Confirmed by hand on 26.1.2 (`spike.js --hold`): a real client that is not an op runs
  `/trigger` from a menu click, and the bot reads each code.

## Addendum: stage 1 generates the whole campus (2026-09-29)

At the user's request, stage 1 now builds every wing as an empty, themed shell (Ops, Gate
Dynamics, Ring Transit with tunnel and shaft, Beam Physics, Mirror Optics, Menagerie/Motor Pool
structure, Systems, the Nether Range and End Annex), with every tp plate leading to a real room,
so the layout can be walked and adjusted before tests are built into it. Only the calibration
chamber has logic. Stages 2 to 6 then add chamber logic and fixtures (animals, vehicles,
armoury) into rooms that already exist; stage 1 grows from M to about L.

## Addendum: companion plugins, in stage 6 (2026-09-29)

After walking the stage 1 campus (a good start; plain, to be made more creative later), the user
asked for companion plugins. They stay in stage 6 (Systems), which grows to include:

- **`--with <list>`** on run-facility.js: downloads each companion once from its official release
  source (Hangar, Modrinth or GitHub releases), pinned to a version and checked against a recorded
  sha256, cached in `.local-server/`; `plugins-extra/` stays for anything else.
- **ViaVersion + ViaBackwards** (ViaRewind optional): join one server from other client versions
  to try client-side features (drawn iris, mirror windows) across clients. Probe keeps the
  server's native version, so the self-test is unaffected.
- **Maps, `--map dynmap|bluemap|squaremap|pl3xmap`**: installs the map plugin, prints its web URL,
  and a self-test step checks the facility's gates, rings, beams and mirrors appear as markers.
  Depends on the MapProvider work (#236, branch feature/dynmap-map-provider; #530 BlueMap, #531
  squaremap, #532 Pl3xMap), so it runs against that branch until it merges. BlueMap's
  accept-download (Mojang client assets) is left for the user to switch on, never automatic.
- **Permissions: LuckPerms + Vault** (and a small economy plugin for EconomySupport): tester groups
  visitor / builder / operator, a Systems console option to switch your own group, and self-test
  permission runs by a non-op probe against each group.

## Addendum: 1.9.0 changes waiting on the facility (2026-09-29)

Requested by the 1.9 milestone planning session. Each runs against its branch's jar via
`run-facility.js --plugin <jar>`; exact checklists come from the sessions owning each branch.

- **#491, minecart turned back by a shut iris** (fix/491-cart-shut-iris) — **stage 2**, in G1.
  An upright Massive gate, iris code set, iris shut; a minecart sent on rails at a fixed, repeatable
  speed and direction (powered-rail run-up of known length, or a summoned cart with set Motion).
  The bot observes the cart's position every tick: pass if it never enters the iris plane and
  reverses in front of it. Also a G1 option row (traveller minecart × near/far iris shut).
- **#236, Dynmap markers** (feature/dynmap-map-provider) — **stage 6** companions (`--map dynmap`).
  Build, rename and delete gates, rings and public beams, then restart the server, and read the
  markers back after each step (the Dynmap marker file under plugins/dynmap, or its web endpoint's
  marker JSON), comparing ids, labels and positions. A second run without Dynmap must load clean
  (fault counter 0). The restart needs `--keep-world` plus a server stop and start in one run.
- **#240, WorldGuard region flags** (feature/worldguard-flags) — **stage 6** companions
  (`--with worldedit,worldguard`, versions matched to the server). Define a region from the
  console (`rg define` needs a player selection, so use `/rg define` with explicit points via
  `//pos1 x,y,z` / `//pos2`, or WorldGuard's API-free region file then `rg reload`), set
  wormhole-build and wormhole-use to allow and deny, and have the non-op probe build and use a gate
  inside and outside the region; check allowed and refused. A run without WorldGuard must load
  clean.
- Every optional-plugin run pairs with a run without it: the plugin must load and pass the
  base self-test with none of its softdepends present.

**Timing decided by the user:** #236 and #240 move to a **stage 2.5**, right after stage 2: the
pinned `--with` companion loader, `--map dynmap`, WorldEdit + WorldGuard, the #236 and #240 runs,
and the paired runs without each plugin. ViaVersion, the other map plugins and LuckPerms/Vault
stay in stage 6.

**Revised (same day):** the user is testing #240, #236 and #491 by hand with a combined jar, so
stage 2.5 is dropped. Dynmap (#236), WorldEdit/WorldGuard (#240) and the `--with` loader go back
to **stage 6** with the other companions; the lab cells remain wanted there as the proper checks.
#491 stays in stage 2. Order: 2 → 3 (rings, beams) → 4 → 5 → 6 → 7.

### #491 checklist (from the 1.9 planning session; fix/491-cart-shut-iris @ 7ebc95f4, PR #535)

Upright Massive gate with an iris code, iris shut (drawn client-side over air; a Horizontal gate,
real blocks, is the control). >= 6 straight rails at right angles into the opening along its
bottom row, ending just in front, none inside; powered-rail or slope start. Runs: A empty cart
front; B with rider; C empty cart from behind; D (optional) boat on ice/water; E shut iris at the
far end of an open wormhole; F control, iris open, cart passes. Pass A–E: cart front ends flush
with the iris face (~0.01 short), never crosses the plane on any tick, ends at rest; B rider still
seated after a brief re-seat. Not failures: fallback stops on slopes or offset lanes; repeated
pushes re-seat the rider. Out of scope: a cart through an open near gate to a locked far iris is
set down in front of its own gate.

### #236 Dynmap checklist, for stage 6 (from the 1.9 planning session)

Branch feature/dynmap-map-provider @ a81df838 — local-only, in worktree
.claude/worktrees/hopeful-faraday-60e9df (the facility can build its jar from there with
`--plugin`). The owning session's full checklist, including "no marker work on the main thread",
is in memory note dynmap-pr-awaits-in-game-test.

Install: Dynmap 3.8 (3.7 should work). The checklist names SpigotMC resource 274, but SpigotMC
downloads sit behind Cloudflare and are not scriptable; the pinned loader should take Dynmap from
Modrinth or its GitHub/CI releases instead, checksum-pinned. Config `dynmap-enabled: true` and
restart; map-show-* default true. Markers update within 5 s. Four layers: Stargates, Transport
rings, Beam destinations, Quantum mirrors.

Automated from Dynmap's marker data (plugins/dynmap/markers.yml or the web marker JSON),
waiting up to 5 s after each change:
1. Gate: idle icon on the opening, labelled with its name; popup has network and owner; a small
   area marker covers the opening.
2. Dial A→B: both stay idle while chevrons lock; after the kawoosh both open and a cyan line joins
   them; shutting returns both to idle and removes the line.
3. Cross-world pair: both open, no line.
4. `gate edit <A> name X`: label changes, no leftover marker under the old name.
5. Owner or network `<b>x</b>` appears literally (escaped) in the popup.
6. Iris code set + `map-show-iris-gates: false` + restart: gate hidden; dialling it draws no line
   and leaves the other end idle.
7. Rings: `ring create` at two slab circles → two markers and a grey line; `ring edit name`
   relabels; `ring remove` removes both markers and the line.
8. Beams: `beam admin set` adds a marker; `beam place set` by a player must NEVER appear;
   `beam admin remove` removes it.
9. Mirrors: `mirror create` at a banner adds a marker, no lines; creating again at the same
   banner relabels it.
10. `gate remove` removes icon, area and line.
11. Restart (same world, `--keep-world`): every marker back exactly once; a gate removed before
    the stop does not return.
12. `/dynmap reload`: all four layers return.
13. `map-show-rings: false` + restart: that layer is gone entirely.
14. `dynmap-enabled: false`: no layers and no Dynmap logging. True with Dynmap absent: exactly one
    startup WARNING (the fault counter's known-benign list names it), everything else works.
Manual, by the tester at Dynmap's web page: 15. all five icons readable at normal zoom; the popup
rendering in 1 and 5.

## Addendum: self-test profiles (2026-09-29)

The full stage 2 matrix took ~24 min on one version. So: build and iterate on one version
(1.21.11) with the full `--selftest`; a `--quick` profile (shell checks plus a small G1 subset, a
few minutes) runs on the other versions at the end of each stage. All three versions in full only
before a release, in parallel on separate ports where possible.

## Addendum: travel by the plugin's own features (user, 2026-09-30)

The user: the facility is a good start but basic; get to each area with the feature it tests.
- **Gate Dynamics**: a permanent stargate in Ops, dialled to a gate in the Gate hall's lobby (and
  back). DHD or dial sign by it.
- **Ring Transit**: a permanent ring pair, one in Ops, one in the Ring lab's lobby.
- **Beam Physics**: a beam pad / console in Ops (a command block or button that runs the beam
  command for the player who pressed it, or a public beam place), arriving in the Beam lab.
- **Mirror Optics**: a quantum mirror in Ops paired with one in the Mirror gallery.
- The Range (nether) and Annex (End) are reached through the gate network (they already have
  gates), mirrors across worlds once stage 4 lands.
- The tp plates stay as the always-works fallback (a broken plugin must not strand a tester), but
  move to a less prominent "service corridor". The self-test walks each transit route every run as
  a standing smoke test, and the Ops board shows each route's last result.
- Lands incrementally: gate and ring and beam transit in stage 3 (their features are covered by
  then), mirror transit in stage 4, cross-world routes in stage 4.


### Companion stage moved up (user, 2026-09-30): runs in parallel with stage 5

The companion half of stage 6 (pinned `--with` loader, ViaVersion, maps with #236, LuckPerms +
Vault, WorldEdit + WorldGuard with #240) runs now, on its own branch and worktree based on stage 4,
alongside stage 5. The Systems half of stage 6 stays in stage 6.

Jars to run against (from the 1.9 planning session):
- #236 Dynmap: feature/dynmap-map-provider @ a81df838, LOCAL ONLY in worktree
  (origin stops at bef49f7a): build from that worktree.
- #240 WorldGuard: feature/worldguard-flags @ ed74b258 (also on origin; draft PR #534), worktree
  .claude\worktrees\agitated-murdock-c39b91.
- Optional extra run: combined jar (#240 + #236 + #491 on main) at
  target/WormholeXTreme.jar. Pass/fail is judged on the
  separate branches.

### #240 WorldGuard checklist (from the #240 session)

Install: WorldGuard + WorldEdit matched to the server. Compiled against WG 7.0.9 / WE 7.2.14; the
calls used are unchanged through WG 7.0.19 / WE 7.4.4. 1.20.x: WG 7.0.9 + WE 7.2.14+. 26.x: WG
7.0.19 + WE 7.4.4 (Java 25). Set `worldguard-enabled: true`, full restart (reload is not enough).
Expect the log line `Registered WorldGuard flags wormhole-build and wormhole-use.` The probe must
NOT be op (ops bypass WorldGuard); give it wormhole.build, wormhole.use and the dial/sign nodes.

Regions: G1 inside `gatetest` (box around G1 and its arrival point), `/rg flag gatetest
wormhole-use deny`. G2 with no region. Empty region `buildtest` with `/rg flag buildtest
wormhole-build deny`.

Cases:
1. Probe dials from G1's DHD/sign: refused "This region does not allow using gates.", G1 not open.
2. Dial G2→G1, probe walks into G2: G2 opens, no travel; message at most once per 2 s.
3. As 2 in a minecart: cart does not travel.
4. Op opens G1 from outside, probe shuts it: allowed.
5. Probe toggles G1's iris: allowed.
6. Probe hand-builds a gate in buildtest and presses its DHD: refused "This region does not allow
   building gates.", no completion prompt.
7. `gate preview` then `gate preview -place` in buildtest: refused "Nothing placed.", NO blocks placed.
8. Gate just outside buildtest with one corner inside: refused.
9. Gate with no region: allowed.
10. `/rg addmember gatetest <probe>`, dial G1: still refused (plain deny applies to members).
11. `/rg flag gatetest wormhole-use -g nonmembers deny`: member probe allowed, non-member refused.
12. The gate's owner dials G1 under plain deny: refused.
13. Op dials G1: allowed (WG bypass).
14. Player without wormhole nodes tries G1: the normal no-permission message, not the region one.
15. `/rg flag gatetest wormhole-use` (clears): everything allowed again.
Paired: P1 `worldguard-enabled: false` + restart: `/rg flag x wormhole-use deny` → unknown flag,
nothing refused. P2 WorldGuard absent: loads with no errors, gates work.
Not in scope: rings, beams, mirrors are not subject to the flags; redstone dialling is not refused.
Note: cases 6–8 need building by hand and by preview, which the facility lists for stage 5; the
companion stage must build that much of it (or the probe places blocks directly) for these cases.

## Addendum: never in GitHub (user, 2026-09-30)

The facility never runs in GitHub Actions, not even as a manually dispatched or release workflow.
It is a local pre-release check: before a release is made, run the full `--selftest` locally
(all three versions, in parallel), plus the companion runs. This supersedes the "manually
dispatched workflow" wording in the local-only addendum and the docs' "none should until it has
earned a place as a release check".

## Addendum: faster full runs, in stage 5 (user, 2026-09-30)

Measured at stage 4 (1.21.11, 3573 s): 189 matrix cells took 2742 s, run serially in one world;
107 cells take 15–30 s, paced by the plugin's own timings (chevron dial, kawoosh, waiting for a gate
to shut on its timeout, ring countdown, beam, mirror hold, far-world loading). Stage 5 adds:
1. **Sharding**: `--shards N` splits one version's matrix across N servers of that version on
   separate ports and folders (each generates its own campus), merges the results, and keeps every
   cell exactly as it is. Composes with `--versions` (versions × shards processes). Report peak RAM.
   Aim: a full release run of all three versions in about 20 minutes wall.
2. **Shut by command**: a cell whose purpose is not the shutdown timeout closes its gates by command
   (the plugin's own close/shutdown command) as soon as its checks are read, instead of waiting for
   `timeout-shutdown`; the cells that test the timeout keep waiting for it. The end-state checks
   (gate shut, lights off, portal gone) still run after the command.
Not done: a fast-timing config profile (the user chose to keep default timings under test).

## Option (user, 2026-09-30): a lite GitHub check, later

The full facility stays local and pre-release. The user raised a *lite* version for GitHub that
tests just the basics. Proposed, not yet decided: after the stages settle, a `--lite` profile (one
basic cell per feature, no cross-world, <= 5 min of tests) run locally until stable, then moved to
GitHub on one version, replacing `player.yml`'s player journeys rather than running beside them.
Budget ~8–12 min per run on a GitHub runner (build, Paper, campus); timing checks may need CI slack.

## Addendum: the Facility Logbook (user, 2026-09-30), stage 7

Every joining player is given a written book, the Facility Logbook, alongside the existing ways
(chat console, Transit tab, plates, feature transit):
- Page 1 is a table of contents; each entry is a `change_page` click to its section.
- Sections: Your runs (that player's), Bot runs, one page per wing, Transit status, and the
  how-to pages the design's handbook lectern would have held (the lectern is dropped).
- A run entry: time, chamber, settings, PASS / FAIL / KNOWN (#issue), first failed check by name;
  hover text carries the detail. Kept to the last N runs per list so the book stays under the
  page limit (100 pages; 1024 chars per page on older versions — check per version).
- Every wing and chamber name carries a Go click: `/trigger wx set <go code>`, and the bot moves
  the player (works without op, unlike /tp).
- A written book is fixed once given, so the facility replaces the player's copy in the same slot
  after each run ends (and on `!book` / a Logbook button in the console), rather than stacking
  copies. It never drops a copy on the floor if the slot is gone.
- Version handling in text.js: book pages are NBT JSON strings before 1.20.5, the
  written_book_content component from 1.20.5, and SNBT components with snake_case click/hover keys
  from 1.21.5. Self-test: the non-op Tester receives the book, a TOC entry's change_page and a Go
  entry's command are intact in what the client receives, a Go click moves Tester, and the book
  is replaced (not duplicated) after a run, on all three versions.

**Moved (user, 2026-09-30):** the Facility Logbook is built in **stage 5**, not stage 7, so it can be used while testing.


## Addendum: the user's plugin cache (2026-09-30)

Companion jars are already cached in a local plugin folder, one folder per
Minecraft version plus `any` (sha256 as found):
- 1.20.4/Dynmap-3.7-beta-8-spigot.jar 87c6035c2cb73bf22fc34ca738cb21a2db89ab5f30bd1071cd8090802a1c78bc
- 1.21.11/Dynmap-3.8-spigot.jar 4771dbe8cbb3ec6a3ee080141361de6d822eac55b55aad7c9e5de031423a6dd7
- 1.21.11/worldedit-bukkit-7.4.5.jar e5696a6d064b9969437a8888be91b0941148a28e0c3736de1554a00254a5d142
- 1.21.11/worldguard-bukkit-7.0.17.jar 3f14562509bf01e7680571b6f56932239157ff938f257c3226df3b4088ae54f2
- any/ViaBackwards-5.12.0.jar f902f7da7eb99e8bfaf461f80283c4e2750b7d9727e6b508ea4bb9163f55b1db
- any/ViaVersion-5.12.0.jar c4d512fa9760fa41d17abaedde12aa1f4c9bde920d0a992fe0fc016962f126be
The `--with` loader reads this cache first (the version's folder, then `any`), pins the sha256 of
whatever it uses, and downloads only what is missing, from official release sources. Missing
today: 26.1.2 Dynmap and WorldEdit 7.4.4+/WorldGuard 7.0.19 (Java 25); 1.20.4 WorldGuard 7.0.9 +
WorldEdit 7.2.14; LuckPerms and Vault everywhere. Run the #236/#240 cells on 1.21.11 first, where
everything is cached. The cache path is a launcher option (`--plugin-cache`), defaulting to this
folder only when it exists.
- Companion loader: pick Java by the highest need among server AND companion jars. WorldEdit 7.4.5 / WorldGuard 7.0.17 are class version 69 (Java 25) even though they are cached under 1.21.11; on Java 21 Paper refuses to load them and Wormhole correctly says WorldGuard is not installed. Read each jar's class version before choosing the JDK.

**Moved (user, 2026-09-30):** `--shards N` and shut-by-command are their own **stage 4.5**, done right after the review fix round and before stage 5 and the companion stage, which both branch from it, so every later run is faster.
- Dynmap web port per server (user, 2026-09-30): every server that runs Dynmap sets plugins/dynmap/configuration.txt webserver-port to its own port, derived from the game port (e.g. 8123 + (port - 25590)), so the automated runs, their shards and a hand-test lab never fight over 8123; the launcher prints each map URL. The user's hand lab (:25620) keeps 8123. A bind failure on the web port must count as a fault, not pass silently.
- No hunger or damage for players (user, 2026-09-30), in stage 4.5: infinite Saturation + Resistance 255 on join/respawn/world change, player damage gamerules off by version name; mobs still take damage.

## Addendum: a lab launcher script in the repo (user, 2026-09-30), companion stage

`scripts/facility/lab.ps1` (and a matching `lab.sh` if cheap), usable by anyone from a fresh clone:
opens the lab in its own window; default 26.1.2 with its world kept; `-Version`, `-Port`,
`-Plugin <jar>` (test a branch's build), `-Op <name>`, `-Fresh`, `-With <list>`. Paths resolved
from the script's own location; no user paths in it. Needs in run-facility.js: `--with` +
`--plugin-cache` (the loader), `--op <name>`, JDK chosen by the highest need of the server and
every plugin jar's class version, and per-server Dynmap web ports. Lessons from the hand labs:
cached WorldEdit 7.4.5 / WorldGuard 7.0.17 need Java 25 and DO work on 26.1.2 and 1.21.11;
Dynmap 3.8 works on 1.21.11 but disables itself on 26.1.2 ("unsupported platform"); the
combined-jar settings `worldguard-enabled`/`dynmap-enabled` are top-level config.yml keys and
WorldGuard needs a full restart. The maintainer's personal start-labs script stays until this lands.

## Future: the Lab Dashboard (user idea, 2026-09-30), stage 8

A local web UI that run-facility.js starts with the server (and prints the URL for):
- Console: the live server log streamed from the launcher (SSE or WebSocket), filterable, Wormhole
  lines highlighted, and a command box that goes through the same srv.run queue.
- Maps: one tab per running map plugin (Dynmap, BlueMap, squaremap, Pl3xMap, and any added later),
  each embedding that plugin's own web UI at the per-server port the launcher assigned; a new map
  plugin is a new tab, not new code.
- Tests: self-test results live, per cell with its checks, the first failure named, known plugin
  faults with issue links (the same data as the in-game Logbook).
- Runs: under --versions / --shards, one row per server: status, port, map links, memory.
- Other UI plugins get tabs the same way.
Security: bind 127.0.0.1 only, a per-run token in the URL (the command box runs console
commands). Local only, like the rest of the facility; never in GitHub. No framework needed: Node's
http module plus one static page. After stage 7, once the companion loader and map plugins exist.
- Log noise to fix (seen in the lab console): the players' shield re-applies every 5 s and logs 'Unable to apply this effect' for players who already have it; re-apply only to players missing it (or silence the output). Fence echoes ([wxfence]) and board 'Modified entity data of Text Display' also flood the console; the stage 8 dashboard should hide them by default. The read-only dashboard is now `scripts/facility/dashboard.js` (127.0.0.1:8200), started by `lab.ps1`.
- Dynmap and 26.x (checked 2026-09-30): no Dynmap build supports 26.x. Its repo's newest version helper is bukkit-helper-121-11; on 26.1.2 it fails 'bukkit version incompatible' and disables itself. Modrinth has only Forge/Fabric builds (v3.8, up to 1.21.11); GitHub releases stop in 2021. Dynmap cells (#236) run on 1.21.11; a 26.x map needs BlueMap/squaremap/Pl3xMap (#530-532) if they support it.
- Dynmap and 26.x (checked 2026-09-30): no Dynmap build supports 26.x. Its repo's newest version helper is bukkit-helper-121-11; on 26.1.2 it fails 'bukkit version incompatible' and disables itself. Modrinth has only Forge/Fabric builds (v3.8, up to 1.21.11); GitHub releases stop in 2021. Dynmap cells (#236) run on 1.21.11; a 26.x map needs BlueMap/squaremap/Pl3xMap (#530-532) if they support them. TODO: remove Dynmap-3.8-spigot.jar from facility-26.1.2-25610/plugins when that lab is stopped.
- Facility bug seen in the lab console: summoning the armoury's glow item frames logs Paper ERROR 'Block-attached entity at invalid position: null' (12 per start); summon them attached to a valid block face (Facing + a solid block behind) so Paper stops complaining.
- Mirror banners at player height (user, 2026-09-30): every facility mirror banner at feet + 1, not feet + 2 (transit Ops/Optics, far-site piers, M1, M2 niches, M3). Stage 5.

## Addendum: a world viewer, so designs can be seen (user, 2026-10-01)

Every build so far was designed blind from coordinates. Add a viewer:
- `--viewer` on run-facility.js starts prismarine-viewer (Mineflayer's companion renderer) on the
  Probe bot, served on 127.0.0.1 with a per-server port (like Dynmap's), first-person and orbit
  modes, printed in the launcher output. Pinned in package.json like the other dependencies.
- A `--shots <list>` option (and a console/self-test hook) moves Probe to named vantage points
  defined in campus.js (one or more per wing: the gate room, the hall from the observation deck,
  the shaft window, the mirror hall, the far sites) and saves a PNG per point under
  .local-server/shots/<version>/, so a design agent (Fable) can be handed real images.
- It becomes a tab of the Lab Dashboard (stage 8), and is useful on its own before that.
- Paired with schematic import for the restyle: a `.schem` set piece built by hand with WorldEdit
  can be placed during generation (the companion loader already installs WorldEdit), still
  checked by the decoration guardrail against cells, footprints, pads and lanes.
- Restyle workflow: the user sketches key rooms in-game and exports schematics; Fable designs the
  rest from viewer shots; Opus builds it; shots are retaken to compare.
Timing: before the restyle and the dashboard; small enough for its own stage (6.5) or to lead
stage 8.

## Addendum: designed decoration on 1.21 and later only (user, 2026-10-01)

A designer's build (design/facility/BRIEF.md) may use every block up to 1.21.11. It is applied as a
decoration layer on 1.21.11 and later; 1.20.4 runs keep the plain campus, so the plugin is still
tested on 1.20 and no check may depend on the decoration. Design mode (`lab.ps1 -Design`, with
`check` and `export`) runs 1.21.11 and is built after the world viewer lands, on its template and
guardrail. Older clients joining through ViaVersion see the decoration with newer blocks mapped to
older ones.
