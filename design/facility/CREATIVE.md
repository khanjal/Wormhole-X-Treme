# Facility creative pass: identity, transit by the plugin's own features, per-wing set pieces

> An internal design pass from 2026-09-29, written when every block had to exist on 1.20; the
> brief now allows blocks up to 1.21.11 and supersedes it wherever they differ. Its section 5 describes harness changes that were planned
> then; check `scripts/facility/` for what exists. The brief for designers is [BRIEF.md](BRIEF.md).

A design, not code. It sits on top of [`scripts/facility/DESIGN.md`](../../scripts/facility/DESIGN.md) (with its addenda) and on what stages 0–3
have built in `scripts/facility/` (`lib/campus.js` owns every coordinate; `lib/blueprint.js` the
geometry vocabulary; `wings/*.js` the shells; `chambers/*.js` the logic). Nothing here moves a
chamber. Everything here is either (a) decoration outside cell volumes, gate footprints and lanes,
(b) new session fixtures built the way the plugin's own users would build them, or (c) small
harness changes named in section 5.

Coordinates are the campus's: x east, z south, feet at y = 0, the flat world's quartz top layer at
y = -1. A "footprint" means the blocks a thing is built from plus what the harness writes around it.

---

## 0. The three rules every idea below obeys

The self-test (`selftest.js` `world` and `resets` sections) proves, after every build and every
reset, that each cell's interior is all air and that every anchor block is where the blueprint put
it. So:

1. **Nothing inside a cell's clear volume**, ever, not even at build time. That is `interior(box)`
   for every `kind: 'cell'` chamber, the tunnel's interior, and the shaft's 60 blocks under R3
   (`cellClear`). A cell with a session `fixture` (Relay, G2; later M1) is judged by its fixture
   instead, so fixtures may stand inside their own cell and nowhere else.
2. **Nothing on a gate's build footprint**: `geometry(shape).bounds` plus the DHD button plus the
   floor pit `GateKit.build` clears and re-lays (bounds ∪ button, from `floorY - 2` up). A floor
   inlay under a gate is replaced by plain quartz on the next build. The same for a ring's 7×7
   (ODD) or 6×6 (EVEN) footprint at the slab layer, and `resetChamber` re-lays the whole cell floor
   in plain quartz, so **floor inlays inside a cell footprint are lost on reset** unless the reset
   blueprint re-lays them (section 5 adds that hook; until then, no inlays inside cell footprints).
3. **Nothing in a lane**: the canal (z −99), rails (z −97) and run-up (z −95..−93) from x 0 to 118
   at y −1..3, the G1 cart lanes the chamber lays inside its cell, the arrow range (x 84..128,
   z −70..−64), and the straight walk-in lines each ring chamber uses (`FROM` offsets: R1 from
   the south, R2 from the east, R3 from the north, the tunnel from the west).

Section 5 turns these into a `KEEP_CLEAR` list that `validateLayout` checks every decoration op
against, so the rules are enforced by the compiler, not by care.

---

## 1. Identity

### 1.1 The look in one paragraph

A civilian physics facility built round a gate, not a bunker: white halls under glass, daylight
frozen at noon (`time set 6000`, daylight cycle off), so there are almost no visible lamps and a
wormhole's glow is the brightest thing in any room. Structure reads as **white concrete and smooth
quartz**; anything you *operate* reads as **copper** (consoles, DHD plinths, ring rims, pad
frames); anything that **glows** is sea lantern or, where the plugin's own blocks apply, the
plugin's (glowstone chevrons, the drawn portal). Department colour appears in exactly three places
per wing, always the same three: the floor stripe you follow, the band at the top of each cell's
walls (already built), and the wing's text. Everything else is neutral, so the colour means
"this way" and never "decoration".

Names on boards keep the design's chamber ids and titles. Room nicknames, used on wayfinding boards
only: Ops = **Gate Room** (the north half) and **Atrium** (the south half); Gate Dynamics =
**the Hangar**; Ring Transit = **the Concourse**; Beam Physics = **the Transporter Bay**; Mirror
Optics = **the Looking-Glass Gallery**; Menagerie = **the Yard**; Systems = **the Briefing Room**;
Range = **Forward Base**; Annex = **the Observatory**.

### 1.2 Palette

Everything below exists on 1.20.0. `PALETTE` in `campus.js` grows by the second table.

| Role | Block | Notes |
|---|---|---|
| Walls, cell walls | `white_concrete` | as built |
| Trim, mezzanine | `light_gray_concrete` | as built |
| Floor | `smooth_quartz` | as built (the world's top layer) |
| Skirting, galleries, pylon bases | `polished_deepslate` | as built |
| Cell glass, observation glass | `tinted_glass` | as built; blocks light, not sight |
| Roofs, tunnel, Range walls | `glass` | as built |
| Guide lines, glow | `sea_lantern` | as built |
| Consoles | `waxed_copper_block` | as built |

New roles:

| Role | Block | Why this one |
|---|---|---|
| Operated surfaces (plinths, ring rims, pad frames, deck consoles) | `waxed_cut_copper`, `waxed_cut_copper_stairs`, `waxed_cut_copper_slab` | one family with the consoles; waxed so it never oxidises |
| Range and Annex consoles | `waxed_oxidized_copper`, `waxed_oxidized_cut_copper` | the older-looking far sites (design 2.1) |
| Invisible light (under decks, the service corridor, the shaft) | `light[level=15]` | 1.17; unseen in survival/adventure, so the "no lamps" look holds; never inside a cell (it is not air) |
| Warm accents in the Yard | `lantern` on fence posts, `hay_block` | 1.14 / 1.6 |
| Railings on decks | `iron_bars` (as built) and `chain` for hangers | 1.16 |
| Deck floors, control-room floors | `polished_deepslate_tiles` | reads as "walkway", not "cell" |
| Gate-room floor emphasis | `polished_deepslate_bricks`, `deepslate_tile_stairs` | the SGC ramp reference, without stairs in the walk lane |
| Mirror gallery accents | `amethyst_block`, `purple_stained_glass_pane` | 1.17; the one place a coloured glass is allowed |
| Annex | `purpur_block`, `purpur_pillar`, `end_stone_bricks`, `end_rod` | as built + 1.9 |
| Range | `polished_blackstone_bricks`, `chiseled_polished_blackstone`, `shroomlight`, `soul_lantern` | 1.16 |
| Wayfinding stripes | `<colour>_concrete` at y −1, one wide | as the corridors already do |
| Hazard thresholds | `yellow_concrete` / `black_concrete` alternating | as built |
| The plugin's floor, as an ornament | `calibrated_sculk_sensor` | 1.20, the block the plugin's version floor is named for; one on the Systems desk |

**Blocks newer than 1.20 and their fallback** (none is used; listed so nobody reaches for them):

| Wanted | First in | Use instead |
|---|---|---|
| `copper_bulb` (the obvious "lit console" block) | 1.21 | `redstone_lamp[lit=true]` (stays lit when placed by `fill`) or `sea_lantern` |
| `copper_grate` | 1.21 | `iron_bars` |
| `chiseled_copper`, copper doors and trapdoors | 1.21 | `waxed_cut_copper`; `iron_door` / `iron_trapdoor` |
| `polished_tuff`, `tuff_bricks`, `chiseled_tuff` | 1.21 | `polished_deepslate`, `light_gray_concrete` |
| `crafter`, `trial_spawner`, `vault`, `heavy_core` | 1.21 | nothing; not wanted |
| `pale_oak_*`, `resin_*` | 1.21.4 | `birch_*`; `orange_terracotta` |
| `creaking_heart` | 1.21.4 | nothing |
| `test_block`, `test_instance_block` | 1.21.5 | nothing |
| `firefly_bush`, `bush`, `cactus_flower`, `wildflowers`, `leaf_litter` | 1.21.5 | nothing (the Yard uses `hay_block`) |
| `dried_ghast` | 1.21.6 | nothing |

`COPPER_BULB` chevrons remain a G1 *option* hidden below 1.21 (design 6.1), which is the plugin's
business, not the palette's.

### 1.3 Signage and wayfinding

All text is text displays (present since 1.19.4, already the board mechanism). Three tiers, so a
tester always knows which kind of sign they are reading:

- **Wing signs** (exist: `sign_<wing>`, scale 1.5, over each entrance): keep, and add the nickname
  in grey on a second line (`GATE DYNAMICS` / `the Hangar`).
- **Chamber boards** (exist, over each cell door): unchanged.
- **Wayfinding tabs**: a small display (scale 0.8) at every junction, white text, an arrow glyph
  and the target in its department colour: `▲ G1 Test Stand`, `◄ G4 Build Bench`. Placed at 2.4
  up on the near side of each stripe fork.
- **Floor stripes**: a one-wide line of the wing colour at y −1 from the wing's entrance to every
  chamber door, forking at junctions, ending on the hazard threshold. Corridors already carry the
  stripe at both edges; inside a wing the stripe is a single line down the walkway. Stripes never
  cross a lane, a gate footprint or a cell footprint; they route round (the compiler refuses one
  that does not).
- **Pylons** stay the at-a-glance state.

Rule of thumb the stripes enforce: from any door you can see the next sign.

### 1.4 Lighting

- Halls: glass roofs and fixed noon, as built. No lamps.
- Under decks, the mezzanine and the service corridor: `light[level=15]` every 4 blocks in the
  ceiling; sea lanterns only where they are also a *mark* (the guide lines, ring rims, pad frames,
  the shaft ledges).
- The far sites: shroomlight and soul lanterns in the Range; end rods in the Annex. Both already
  have a lantern cross on the floor.
- The one exception to "no lamps": the Gate Room's two copper pylons carry a sea lantern cap each
  at y 7, so the gate has a lit frame around it even from the mezzanine.

---

## 2. Arrival and transit by the plugin's features

The user's addendum: reach each area by the feature it tests; plates become a fallback. This
section fixes where each transit fixture sits, how a first-time tester learns it, and how the
self-test walks it.

### 2.1 Ops becomes the Gate Room and the Atrium

The eight plates leave the atrium's centre. What replaces them is one transit fixture per wing,
on the side of the atrium its wing lies on, so the atrium itself is the map:

```
 z −20 ┌─────────────── mezzanine (Systems, y 5) ───────────────┐
 z −15 │   ═══ service corridor under it: the eight plates ═══   │
 z −14 │        [pylon]                          [pylon]         │
 z −13 │  Ops wall    ┌─ OPS GATE ─┐     fault counter           │
       │  x −6        │ x −3..3    │     x 6                     │
 z −10 │              │ DHD (2,−9) │                             │
       │  beam pad    └──runway────┘   ring pad   (rings, east)  │
 z −6  │  (−13,−6)     x −1..1        (13,−6)                    │
       │ (beams, west)                                            │
 z  0  │                 ● welcome, compass rose                 │
       │                                                          │
 z 7   │                                  ┌─ c0 ─┐  x 11..17     │
 z 13  │                                  └──────┘                │
 z 20  │  ▣ mirror (−10,2,20)   spawn (−2.5, 12.5)   c0 gallery   │
       └──────────────── south wall, door x −2..2 ────────────────┘
```

**The Ops gate, `Ops`.** Standard shape, Standard group, built by the console form as a session
fixture (like Relay): facing **south**, opening centred on x 0 in the plane z −13, feet on y 0.
`GateKit.place('Standard','south',{cx:0, openingAt:-13, floorY:0})` gives holder (2, 0, −10),
bounds x −3..3, y −1..5, z −13..−10, the DHD button at (2, 0, −9), the arrival at (0.5, 0, −11.5)
facing south into the atrium. Its top row (y 5) is level with the mezzanine floor two blocks
behind it, so from the Briefing Room you look down over the gate into the atrium: the SGC
window. The Ops wall board (−6, 1.5, −13.5) and the fault counter (6, 1.5, −13.5) already flank
exactly this spot; each gets a copper pylon behind it at (±6, y 0..6, z −14) with a sea lantern
cap, and the boards read as hanging on their pylons.

Around it, outside the footprint: a **runway** of polished deepslate bricks x −1..1, z −9..−3 at
y −1, with a sea lantern at each end; the cyan stripe runs from the compass rose to its foot. The
runway is the walk lane and the arrival lane; nothing stands on it. A **dial console** at
(−3, 0, −8): three copper blocks (x −5..−3, z −8) each carrying a stone button on a command block
(`wormhole gate dial Ops Hall`, `... Range`, `... Annex`) with a display over each; the console
form of dialling is a plugin feature (G6/G7), so the buttons are themselves a test. The DHD is
the hands-on way: press the button on (2, 0, −9), then `/dial Hall`. With no permissions plugin
and `permissions-auto-fallback: true` (the default) a non-op may use and dial, so an adventure
tester needs no op for this; `gate dial` from the console needs none either.

The transit gates go on the **default network** (no `net=`): the design's `wormhole.network.use.
<net>` refusal (G9) stays a G1 option, and a gate on a named network would need that node for a
tester who is not an op.

**The Hall gate, `Hall`**, in the Gate hall's lobby: Standard, facing **south** so its DHD faces
the entrance a walker arrives by and an arrival faces the wing sign. Opening plane z −53, cx 0:
bounds x −3..3, z −53..−50, DHD button (2, 0, −49), arrival (0.5, 0, −51.5) facing south, four
blocks from the entrance point (0.5, 0, −43.5). Lobby is the strip z −58..−41, x −38..38 between
G4's door (x −39) and G3's door (x 39); nothing else is there. Same runway and dial console
(`gate dial Hall Ops`) as in Ops, mirrored.

**The ring pair, `Ops` ⇄ `Lab`.** ODD pattern (7 across), `polished_deepslate_slab`, laid by
`RingKit.lay` and paired by the console form `ring build world 13 0 -6 52 0 -4` at fixture time
(public, no owner, so any tester may use it). Ops end anchor (13, 0, −6): footprint x 10..16,
z −9..−3, which keeps the east door's line (z −2..2) clear so a walker from the corridor never
steps into it by accident. Ring lab end anchor (52, 0, −4): footprint x 49..55, z −7..−1, in the
Concourse just inside the entrance (43.5, 0.5), likewise off the door line. Both ends are within
256 across (39.05 blocks), well over `ring-min-separation` (8) from R1's staging anchor
(50, 0, −18) (14.1 blocks), and overlap no gate.

A paired ring's slabs are consumed, so the pad is invisible: each end gets a **rim** in the floor
at y −1, polished deepslate under the sixteen slab cells (`rings.slabsOf('ODD')` offsets from the
anchor) and a sea lantern under the centre. The floor stays solid ground, which the arrival rule
needs; nothing at y ≥ 0 inside the footprint. A board over each rim: `RING PAD · step in, stand
still · to the Concourse` / `· to the Atrium`, and `rings recharge for 30 s after a trip`. The lime
stripe leads from the compass rose to the rim's south edge.

**The beam pads, `Atrium` and `BeamLab`.** Public destinations (`beam admin set <name>` saves
where the player stands, so Probe stands on each pad facing the right way and runs it as the
fixture; the console cannot). Ops pad centre (−13, 0, −6), facing west (yaw 90); Beam lab pad
centre (−46, 0, 0), four blocks inside the entrance (−42.5, 0.5), facing west into the bay. Each
pad: 3×3 sea lantern inlay at y −1 with a one-wide cut-copper frame round it (5×5 at y −1), four
copper columns at the frame's corners (y 0..3, sea lantern on top) — the columns sit on the
frame's corners, two blocks diagonally from where the traveller stands, clear of the drawn beam.
A **console** at (−17, 0, −6) (three copper blocks x −18..−16 along z −6): the middle carries a
stone button on a command block running `wormhole beam admin send @p BeamLab`; the board says
`BEAM · say /wormhole beam to BeamLab · or press`. Two things to verify in the first smoke run
and record on the board: (a) whether `admin send` plays the envelop/rise animation like `beam to`
(if not, the button is the fallback and the typed command is the demonstration), and (b) whether
the plugin resolves `@p` in `admin send`'s target (if not, the command block runs
`execute as @p[distance=..3] run wormhole beam admin send @s BeamLab`, and if the plugin refuses
a proxied sender, the button is dropped and the board keeps the typed form). `wormhole.beam.use`
is true by default, so a non-op tester can `beam to`. The Beam lab console mirrors it
(`... send @p Atrium`), by its pad.

**The mirrors, `Ops` ⇄ `Optics`** (stage 4, with the rest of Mirror Optics). A mirror wants a solid
wall a block out on every side of its 1×2 opening, and shows the whole room only when solid for
`mirror-proximity-distance` (16) on every side. No Ops wall is 33 wide without a door, and no
wall in the facility is 33 tall, so every transit mirror is a **through-the-opening** mirror by
geometry; M1's cell is where the whole-room view is built and tested. Ops: a white wall banner at
(−10, 2, 20) on the south wall block (−10, 2, 21), facing north; wall solid from y 0 (skirting is
polished deepslate, solid) to y 3 and x −11..−9, which the wall is. Console form at fixture time:
`mirror create Ops world -10 2 20`, then `mirror set Ops -start Optics` so the first right-click
opens onto the gallery, and `mirror set Ops -stamp indoors` (a shipped look) so it is not a plain
banner. Gallery end: banner at (−10, 2, 40) on the north wall block (−10, 2, 39) of Mirror Optics,
facing south, 8 blocks west of the corridor door (x −2..2); `mirror create Optics world -10 2 40`,
`-start Ops`, `-stamp library`. Round each banner: an amethyst frame one block out in the wall
plane (x −11..−9, y 0..3 replaced: amethyst at the border, the opening's own two wall blocks kept
solid white concrete since they are the "opening" the plugin draws over), a magenta stripe to its
foot, and a board: `MIRROR · walk up · right-click to choose · punch to go`.

Two consequences for the plugin's defaults, both facility-wide settings applied at start-up
(before `config.js` takes its snapshot, so they are the baseline, not a leak): `mirror-per-world-
limit` must allow two overworld mirrors plus the gallery's test mirrors (the design already raises
it to 3 for `Wide`/`Near`/`Yours`; make it 6), and the design's M1 mirror named `Atrium` should be
renamed (`Lab`) so `Atrium` is not two things. M2's "default limit is 1" row is then tested by
lowering the limit to the current count and trying one more, or in the End, which holds only
`Annex`.

**Range and Annex** by the gate network: the Ops dial console's second and third buttons, and
`/dial Range` / `/dial Annex` from the DHD. The Range and Annex gates are stage-2 fixtures
(`chambers/relay.js`) and already face south with a DHD; each gets a runway and a one-button
console (`gate dial Range Ops`, `gate dial Annex Ops`) so the way home is obvious. They are
shared with G1's `destination` option: `gate force` in G1's stage resets them, so a tester who
left `Range` dialled to `Ops` is put right by the next run, and a tester dialling into a far gate
that G1 is using sees the plugin's "already active" refusal, which is a feature. The addendum
places these routes in stage 4; the fixtures exist now, so they can be switched on earlier if
stage 3's agent has a slot.

### 2.2 The service corridor: plates as the fallback

The eight plates move to the 41×6 strip **under the mezzanine** (x −20..20, z −20..−15, y 0..4),
which the north corridor already opens into (door at z −21, x −2..2). Plates at z −18, x −16,
−12, −8, −4, 4, 8, 12, 16 — west to east: Annex, Range, Beams, Mirrors, Gates, Rings, Menagerie,
Systems — each with its label board at y 1.3 and its command block below, four apart so
`@p[distance=..2]` never picks the wrong plate. A board at each end of the strip:
`SERVICE CORRIDOR · plates always work, even with the plugin down`. Light from `light` blocks in
the mezzanine's underside; the mezzanine floor at y 5 gets a sea lantern every fourth block along
z −17 so the strip is lit from above too.

`campus.TRANSIT` becomes `{ centre: { x: 0, z: -18 }, plates: [{ dir: 'W4', dx: -16, dz: 0, to:
'annex' }, ...] }`. `validateLayout` reads it unchanged. The self-test's `plates` section walked
from the centre on the assumption that no plate lies on the line; in a strip that is false, so it
starts each walk at `(plate.x + 0.5, 0, -15.5)` facing north and walks 2.5 blocks (section 5).

Each wing's home plate moves two blocks into its corridor from the wing side, so it is out of the
lobby: gates (2, 0, −37), rings (36, 0, 2), beams (−36, 0, 2), mirrors (2, 0, 36). The Menagerie
(no corridor from Ops), the mezzanine, the Range and the Annex keep theirs. `wings.homePlate`
gains a per-wing override.

The atrium's centre keeps the welcome board and gains a **compass rose** at y −1: sea lantern
centre, four one-wide spokes in the wing colours out to radius 6 (cyan north to the runway, lime
east to the ring rim, light blue west to the pad frame, magenta south toward the mirror, bending
west at z 12 to x −10). The spawn point (−2.5, 0, 12.5) facing north now looks up the magenta
spoke to the rose and across it to the gate.

### 2.3 How a first-time tester learns it

- The welcome board's second line becomes `follow a colour: gate north, ring east, pad west,
  mirror south · say ! for the console · plates under the balcony`.
- Every transit fixture has a two-line board within four blocks: what it is, how to use it, in
  the imperative (`press the DHD, then /dial Hall`).
- The console gains a **Transit** tab: `[Dial Hall] [Dial Range] [Dial Annex] [Beam to lab]
  [Beam home]`, each running the console form for that player (the same commands the buttons
  run), and `[Routes]` which prints the last smoke result per route.
- The Ops wall gains a `TRANSIT` block: one line per route with its last result and time, kept by
  the smoke test and by any tester's use (the fixture module listens for the plugin's own
  `Stargates connected` / ring / beam lines the way `ringtrip.listen` does).
- The handbook lectern (stage 7) gets a page per route.

### 2.4 The smoke test

A new self-test section, `transit`, run **after `fixtures` and before `matrix`** (the world is as
built and no chamber is holding a far gate). Each route is a named check with a reason; each
writes its result to the Ops wall's TRANSIT block. Routes and their predicates:

| Route | Probe does | Passes when |
|---|---|---|
| `gate ops→hall` | console `gate dial Ops Hall`, `GateKit.waitOpen`, walks in from (0.5, 0, −5) | within 1.5 of `Hall`'s arrival, facing within 15° of south, `Ops` shut within `timeout-shutdown` + 5 s |
| `gate hall→ops` | clicks the DHD button at (2, 0, −49) as a player, chats `/dial Ops`, walks in | within 1.5 of `Ops`'s arrival |
| `ring ops→lab` | steps in at (13, 0, −6) from the south (`ringtrip.stepIn`) | `Transport in N seconds` seen; within 1 of (52.5, 0, −3.5); stepping straight back in says `Rings recharging` |
| `ring lab→ops` | after the cooldown, steps in at the lab end | within 1 of the Ops end (skipped in `--quick`: it is the same pair) |
| `beam ops→lab` | presses the Ops beam button (a click on the button block) | lands within 1 of (−45.5, 0, 0.5), yaw within 5° of 90; hidden→shown transition seen on Probe's own entity if `admin send` animates |
| `beam lab→ops` | chats `/wormhole beam to Atrium` | lands within 1 of (−12.5, 0, −5.5), yaw within 5° of 90; a second `beam to` inside `beam-use-cooldown` is refused with the plugin's line |
| `mirror ops→optics` (stage 4) | walks to (−10.5, 0, 17.5), right-clicks until the action bar names `Optics` (with `-start Optics`, once), punches | in front of the `Optics` banner within 1.5, facing south |
| `mirror optics→ops` (stage 4) | the same back | in front of the `Ops` banner, facing north |
| `gate ops→range`, `range→ops` (stage 4 per the addendum) | as the hall route, in the nether; back by the Range console button | arrival checks in each world; both gates shut afterwards |
| `gate ops→annex`, `annex→ops` (stage 4) | the same for the End | the same |
| `plates` (exists) | every service-corridor plate and every home plate | as today |

About two minutes in full (one ring cycle ≈ 12 s, one 30 s cooldown, the rest seconds); the
`--quick` profile keeps one route per feature. The section also proves the fixtures' *listing*:
`gate list` shows `Ops` and `Hall` among the fixtures, `ring list` (as Probe) shows the transit
pair's id, `beam list` shows `Atrium` and `BeamLab`, `mirror list` (stage 4) shows `Ops` and
`Optics`; the `empty` section's expected lists grow by the same names.

---

## 3. Per-wing makeovers

Every item here is outside cell volumes, gate footprints and lanes, and names what it touches.
"Keep-clear" boxes are given so the compiler check in section 5 can be written from this text.

### 3.1 Ops: Gate Room, Atrium, Briefing Room

Covered in 2.1–2.2. Additionally:

- **Briefing Room** (the mezzanine, x −20..20, z −20..−15, y 5): the railing at z −15, y 6 gets a
  tinted-glass parapet 2 high at x −8..8 (`tinted_glass` at y 6..7) so the view over the gate is a
  window, iron bars elsewhere; the Systems desk at (0, 6, −19) gets a calibrated sculk sensor on
  its middle block's north neighbour (−0, 6, −20 is the wall; put it at (0, 7, −19) on the desk).
  Two more copper consoles at (−12, 6, −19) and (12, 6, −19), no logic: the room reads as a room.
- **c0 Calibration Cell** stays as built (its cell is the self-test's own reference).
- Keep-clear: the gate footprint x −3..3, y −2..5, z −13..−9; the runway x −1..1, y 0..3,
  z −12..−3 (walk lane); the ring footprint x 10..16, y 0..4, z −9..−3; the pad x −15..−11,
  y 0..3, z −8..−4; c0's footprint; the plates' strip at y 0..1.

### 3.2 Gate Dynamics: the Hangar

- **Lobby**: the `Hall` gate (2.1), runway z −49..−44 (between DHD and entrance), dial console at
  (−3, 0, −48). A cyan stripe from the entrance splits at z −56 west to G4/G5 and east to G3/Relay,
  and north up x −2 (west of the lanes' end at x 0) to G1's door outside point (0, 0, −101).
- **Observation deck and control room over G1's gallery**: a second storey on the gallery's
  outside: floor `polished_deepslate_tiles` x −29..−22, y 6, z −141..−103; railing (iron bars) at
  y 7 along x −29 and both ends; stair from the walkway at the south end (x −29..−27, z −102..−97,
  rising north, `deepslate_tile_stairs`) — clear of the lanes (x ≥ 0) and of G5's board at
  (−31.5, 4.6, −124.5), which sits just west of the deck's edge. The gallery below stays exactly
  as `cellSurrounds` builds it (seat at (−21.5, 1, −121.5) untouched; 5 blocks of headroom).
  The cell's west wall is tinted glass to y 29, so the deck looks straight at the Stand. The
  **control room** is the deck's south bay, z −110..−103: three copper consoles facing east
  (x −24, z −109/−106/−103 at y 6), a board `G1 CONTROL · the run's steps show here` fed the
  same spec as G1's board, and `light` blocks in the deck's underside every 4 blocks.
  Keep-clear: the gallery's footprint (already in `validateLayout`), the door and pylon at
  z −103..−101.
- **Lanes as a taxiway**: hazard rows at y −1 along z −101 and z −91 for x 0..70 (outside the
  canal's solid sides at z −100 and −98 and the run-up's edge at −93); a board at the corridor
  mouth (75, 2.4, −96) `MOTOR POOL LANES · canal · rails · run-up`. Nothing on x 0..118,
  z −100..−92, y −1..3.
- **G2 Shape Gallery** (fixture cell, z −168..−150): a name plaque display over each gate at its
  `cx` and y 8, z −166 (`Massive`, `Grand`, …), and a `Gallery network` board at the cell door.
  Displays only; no blocks in the cell.
- **Relay** (fixture cell, x 30..50, z −140..−120): its east gallery (x 52..54) gets a copper
  console and a plaque `RELAY · the far end of G1's trips`; a cyan stripe from the lobby.
- **G3, G4, G5** (stage 5, empty cells now): stripes and wayfinding tabs only; their interiors
  belong to stage 5.
- **The hall's own roof**: at 36 high the glass roof is a featureless sheet; add white-concrete
  roof beams 1 wide at y 36 (the roof layer itself, replacing glass) every 20 blocks along x and z
  (x −60, −40, …; z −160, −140, …), skipping none of the cells' footprints (the roof is outside
  every cell: cells top out at y 29 with their own roof at y 30). It reads as a hangar frame from
  inside and costs 7+7 strips of 131/141 blocks.

### 3.3 Ring Transit: the Concourse

- **Concourse** (z −8..4, x 44..110, between R1's door wall at z −9 and R2/R3/R4's at z 5/7/5):
  the `Lab` ring rim at (52, −4) (2.1), a lime stripe from it east along z −1 to each door and to
  the tunnel mouth (111, −4..4), and to the R5 Edit Desk (100, 0, −4), whose seat is at z −1.5:
  the stripe passes at z −1, so it runs *under* the seat position, which is fine (y −1).
- **The Shaft Window** (the set piece of item 4, "a ring shaft visible from the lobby", done from
  the Concourse since Ops cannot see it): R3's gallery is the strip x 65..73, y 0, z 16..18 south
  of the cell, seat at (69.5, 1, 16.5) looking north through the tinted south wall (z 15). Make the
  gallery floor glass (`galleryFloor: 'minecraft:glass'` on R3 in `CHAMBERS`) and carve a light
  well under it, x 65..73, z 16..18, y −60..−1 air, with the well's north face (z 15, y −60..−2,
  which is the flat world's stone, not a shell wall) replaced by glass so the well and the shaft
  are one lit volume seen from above. Sea lanterns at both ends of each existing ledge (x 65 and
  73 at y −21 and −41, z 7) and a sea lantern strip down the well's south face (z 19, x 69,
  y −60..−1). From the R3 seat the tester looks straight down 60 blocks past the rings' path.
  R3's clear volumes (x 66..72, z 8..14, y −60..−2 and the cell) are untouched, and `reset/r3`
  fills only those and re-lays the cap and ledges, so the well survives resets. The well is inside
  the Ring Transit forceload rectangle.
- **Range Tunnel markers**: at y −1 across the tunnel's width (z −4..4) a lime band at x 116 + 64,
  +128, +192, +250 and a red band at +257, each with a display `64` … `250 · inside the limit` /
  `257 · one past 256`, so the distance rows explain themselves as you walk. The tunnel's clear
  volume is y 0..5; y −1 is the floor and `reset/tunnel` clears only the interior, so the bands
  survive. Rings need solid ground: concrete is.
- **R4 Build Bench** (creative): a display checklist over the door of the refusals to try
  (from `RingMessages`, as the design's board says); nothing inside.
- Keep-clear: the four cells, the `Lab` rim's footprint x 49..55, y 0..4, z −7..−1, R1's
  walk-in line from the south of (50, −18) to z −9, R2's from the east of (51, 18), R3's from the
  north of (69, 11) across the cap, the tunnel's line from x 111 east.

### 3.4 Beam Physics: the Transporter Bay

- **Transporter room** by the entrance: the `BeamLab` pad at (−46, 0, 0) (2.1) with its frame,
  columns and console at (−46, 0, 5) (three blocks along x −47..−45, button in the middle:
  `beam admin send @p Atrium`), and a light-blue stripe from it west along z −9 to B1's door
  (x −51, z −9..−8) and south along x −44 to the B2 Dispatch Desk (−60, 0, 22), turning west at
  z 22.
- **Dispatch office** round B2: the desk is three copper blocks at x −61..−59, z 22 with its pylon
  at x −58; add a copper back wall one block behind it (x −62..−57, y 0..2, z 23) with three
  displays (`GOTO`, `SEND`, `COST`) as a fake control board, and two more consoles at (−70, 0, 22)
  and (−50, 0, 22). The seat is at (−59.5, 0, 24.5) facing north: the desk in front, the board
  above.
- **B1 Pad Array** (cell x −104..−52, z −26..8, gallery s at z 10..12): outside its south glass
  wall the gallery gets six plaque displays at y 3.5 over the gallery rail, one per pad name
  (`Pad-N` … `Pad-Trap`), at the x of each pad as B1's fixture will place them (stage 3's chamber
  decides those; the plaques are summoned from the same table). Nothing inside.
- Keep-clear: the pad x −48..−44, y 0..3, z −2..2; B1's footprint; B2's desk footprint.

### 3.5 Mirror Optics: the Looking-Glass Gallery

- **Lobby** (z 40..47): the `Optics` mirror at (−10, 2, 40) (2.1), amethyst frame, magenta stripe
  from it to M1's door (x 0..1, z 49), forking at z 46 to M2 (x −22..−21, z 75) and M3
  (x 22..23, z 75). M1 spans x −36..36 with walls at ±37 and its gallery at z 70..72, so the forks
  run along x −38 and x 38 past it, then east/west along z 74 to the M2/M3 doors. The lobby's north
  wall gets a row of
  purple stained-glass panes at y 4..5 (above head height, below the wall's top band) between
  x −30 and 30, the one coloured glass in the facility.
- **The mirror hall that is also the M2 wall test** (stage 4): M2 (x −36..−8, z 76..88, creative,
  gallery e at x −6..−4) tests banners on five kinds of wall. Build those five as M2's *stage*
  fixture, not the shell, laid out as an exhibition down the cell's west side so that from the
  gallery seat (−5.5, 1, 82.5), looking west through the tinted glass at x −7, the tester sees five
  lit niches in a row: each a polished-deepslate wall x −34, y 0..3, z (n−1)..(n+1) for n = 78,
  80, 82, 84, 86, banner on its east face at (−33, 2, n), a `light` block above, and a plaque
  display at (−32.5, 3.6, n): `solid`, `gap one out` (the block at (−34, 0, n+1) removed),
  `gap two out` (the block at (−34, 2, n+2) is beyond a 3-wide wall, so widen niche 3 to 5 wide
  and remove (−34, 2, n+2)), `on a post` (a lone `polished_deepslate_wall` at (−34, 1, n) carrying
  the banner), `two wide` (banners at (−33, 2, n) and (−33, 2, n+1) on a 5-wide, 5-tall wall).
  `Run` reads `create`'s answer for each and the plaque turns green or red with the plugin's
  sentence. Because it is the stage, `reset/m2` clears it like any other run.
- **M1 Mirror Round** (x −36..36, z 50..68): its mirrors (`Lab`, `Wide`, `Near`, `Yours`) hang on
  its own walls, which is inside the cell's clear volume, so M1 must carry a `fixture` (like G2)
  and be judged by it; stage 4 does that. Outside: the gallery at z 70..72 gets three seats
  marked by copper slabs at y 0 (x −20, 0, 20) so a tester can watch three mirrors at once, and
  plaques over the rail.
- **M3 Capture Desk** (cell x 8..36, z 76..88): stripe and tab only.
- Keep-clear: the three cells; the `Optics` banner's wall patch x −11..−9, y 0..3, z 39 (must
  stay solid white concrete: the compiler forbids replacing it) and the air in front x −11..−9,
  y 0..3, z 40..42 (the arrival lands there).

### 3.6 Menagerie and Motor Pool: the Yard

- Fence posts get a `lantern` on each pen corner (y 1 over the corner fence); pens get a
  `hay_block` in one corner inside (animals are summoned by tag into pens, so one block of hay in
  a 6×6 pen is fine); the kennel gets two `oak_log` posts and no bed (Freya's bed is the
  plugin's own affair).
- The boathouse pool (x 119..128, z −104..−99, water at y −1) gets a copper edge at y −1 one out
  (x 118 and 129, z −105 and −98) and a `SLIPWAY` board; the canal from it at z −99 stays.
- The rail yard loop gets a depot roof: light-gray concrete at y 4 over x 84..112, z −86..−76
  with copper posts one block outside the loop's corners, at (83, −87), (113, −87), (83, −75) and
  (113, −75), y 0..3 (the corners themselves are rails), and `light` blocks under it every 4.
- The armoury room (x 118..128, z −86..−76, roof at y 4): `glow_item_frame`s on its inside walls
  showing each weapon (`Tags:["wx_decor"]`, so no `kill @e[tag=wx_run_*]` touches them and the
  G1 "item frame must not travel" fixture is unaffected).
- The arrow range: hazard rows at y −1 along z −71 and z −63; the target column stays.
- Keep-clear: every pen interior, the trough (x 83..109, y −1..1, z −110..−108), the pool, the
  loop's rails, the range (x 84..128, y 0..3, z −70..−64), all three lanes.

### 3.7 The Range: Forward Base

- The gate `Range` (bounds x −3..3, y 63..69, z −20..−17, DHD (2, 64, −16)) gets a runway
  z −15..−9 in chiseled polished blackstone, a one-button console (`gate dial Range Ops`) at
  (−3, 64, −14), and two blackstone-brick blast walls flanking the runway at x ±5, y 64..67,
  z −16..−12 (outside the 12-block apron's *line*; they stand beside it, not in it).
- A **mirror pier** for stage 4's `Range` mirror: a free-standing wall of polished blackstone
  bricks x −17..−13, y 64..68, z −25, banner at (−15, 66, −24) facing south. It is north of the
  south strip (z 12..30) that `wings/range.js` keeps clear.
- Soul lanterns on the four corners of the room at y 66, and the existing shroomlight cross.
- Keep-clear: the gate footprint and apron (x −6..6, y 64..69, z −21..−4), the south strip, the
  desk at (0, 64, −8), the future pad and ring end (stage 4 places them in the south strip).

### 3.8 The Annex: the Observatory

- The gate `Annex` (bounds x 997..1003, y 59..65, z 1000..1003, DHD (1002, 60, 1004)) gets a
  purpur-pillar runway z 1005..1011, a console (`gate dial Annex Ops`) at (997, 60, 1006), and four
  end rods on purpur pillars (y 60..63) at (995, 1005), (1005, 1005), (995, 1011), (1005, 1011).
- A **mirror pier** for stage 4's `Annex` mirror: end-stone bricks x 1010..1014, y 60..64,
  z 1015, banner at (1012, 62, 1014) facing north — south of the north strip (z 980..990) that
  `wings/annex.js` keeps clear.
- Keep-clear: the gate footprint and apron (x 994..1006, y 59..65, z 999..1012), the north strip,
  the desk at (1000, 60, 992).

---

## 4. Set pieces that also test something

| Set piece | Where | What it tests, beyond looking right |
|---|---|---|
| The Gate Room | Ops north, 2.1 | `gate build` console form as a fixture, DHD + `/dial` by a non-op under `permissions-auto-fallback`, `gate dial` from a command block (G6/G7), cross-world dialling to Range/Annex (G18), `timeout-shutdown` after every trip (G11), "already active" when G1 holds the far gate (G9) |
| The Briefing Room window | mezzanine, y 5 | nothing new; it is where the Ops wall's TRANSIT block is read from |
| The ring pads | Ops east, Concourse | `ring build` console form (R9), a public pair's access (R7), the countdown and the 30 s cooldown message (R4, R6), `ring list` showing a pair nobody made in a run |
| The transporter pads | Ops west, Beam lab | `beam admin set` by a player, `admin send` from a command block (B3), `beam to` by a non-op, arrival facing (B4), the use cooldown (B6) |
| The looking-glass pair | Ops south, gallery lobby | `mirror create` console form with coordinates (M1), `-start` (M3), `-stamp <look>` (M7), the approach message (M8), the through-the-opening view on a wall that is not 16-solid (M6) |
| The observation deck and control room | over G1's west gallery | the G1 board's spec is mirrored to the control-room display, so a tester in the deck reads the run without chat; the deck is also where "the tester" launcher option waits to shoot (G1 `launcher: tester`) with a clear line down the lane |
| The Shaft Window | R3's gallery | R3's trips are seen end to end; the drawn stack on the floor (R2's check) has a visible twin here |
| The tunnel markers | Range Tunnel | the distance rows' refusal at 257 is read on the floor before it is read in chat |
| The mirror hall | M2's stage | the five wall cases of M5 as an exhibition; each plaque carries the plugin's own answer |
| The M1 three-seat gallery | M1's south gallery | the three-second hold (M3) needs two people at one mirror; three marked seats give the tester somewhere to be while Probe2 stands at the banner |
| Range blast walls and Annex pillars | the far sites | nothing; they make the arrival read as "somewhere else", which is what a cross-world trip is for |

---

## 5. Implementation notes

### 5.1 New vocabulary in `lib/blueprint.js`

All pure functions over a `Blueprint`, in the style of `room`/`corridor`/`cellShell`:

| Primitive | Signature | Emits |
|---|---|---|
| `stripe` | `(bp, points: [[x,z],...], colour, y = -1)` | axis-aligned one-wide fills between consecutive points; throws on a diagonal |
| `inlay` | `(bp, {x, z, y = -1}, cells: [[dx,dz,block],...])` | a floor pattern (compass rose, ring rim from `rings.slabsOf`, pad frame) |
| `pillar` | `(bp, x, z, y0, h, {body, cap})` | a column and its cap; registers the cap as an anchor |
| `console` | `(bp, {x, y, z}, axis, {buttons: [{command, label}], board})` | copper blocks, a stone button on an impulse command block per entry (the block one below the button's block, like `plate`), and `summonBoard` displays; the command is `TrackOutput:0b` like the plates |
| `banner` | `(bp, x, y, z, facing, colour = 'white')` | `<colour>_wall_banner[facing=…]` plus an anchor, and a check that the wall block behind and the border one out are solid in the blueprint's own ops (a mirror's wall rule, enforced at compile time) |
| `runway` | `(bp, geom, length)` | the deepslate-brick lane from a gate's DHD side outwards, derived from `shapes.geometry` so it starts one block past `geom.button` and never touches `geom.bounds` |
| `deck` | `(bp, box, y, {rail: sides, stair: {from, dir}})` | floor, railing, stair, `light` blocks underneath |
| `keepClear` | `(bp, id, box)` | records a box that no later decoration op may overlap (see 5.3) |
| `plaque` | `(version, {id, wing, at, text, colour, scale = 0.8})` | a small display; sugar over `summonBoard` |

`cellSurrounds` gains `ch.galleryFloor` (R3's glass); `cellLayout` is unchanged, so every seat,
door, board and pylon stays where it is. `PALETTE` grows by section 1.2's roles.

### 5.2 New files

- `wings/decor/<wing>.js`: one decoration function per wing, `decorate(out, w, version)`, called
  by `buildWing` after `extra.structures` and after the cells, so `keepClear` boxes from cells are
  already registered. Keeping decoration out of `wings/<wing>.js` keeps the structural files
  readable and lets `--plain` skip decoration for a fast build.
- `chambers/transit.js`: `kind: 'desk'` (no cell, no reset function; its `at` is the Ops dial
  console), with `fixture(ctx)` that builds `Ops` and `Hall` by `GateKit.build`, lays and pairs the
  ring by `RingKit.lay` + `build`, has Probe `beam admin set` the two pads, and (stage 4) `mirror
  create` the two banners with `-start` and `-stamp`; `routes(ctx)` returns the smoke checks of
  2.4 as `{ name, test }` lists per route; `options: {}` and `refuses` say "use the Transit tab".
  `facility.fixtures()` picks it up like Relay and G2. The `empty` section's fixture lists read
  `transit.gates()`, `transit.ringId`, `transit.beams()`, `transit.mirrors()`.
- `selftest.js`: the `transit` section (2.4) between `fixtures` and `matrix`; the `plates`
  section's start point (2.2).
- `lib/console.js`: the Transit tab.
- `campus.js`: `TRANSIT` (2.2), `TRANSIT_FEATURES` (the coordinates in 2.1, so chambers and
  decoration read one table), `HOME_PLATES` overrides, `KEEP_CLEAR` seeds (lanes, apron boxes),
  `galleryFloor` on R3, and the baseline settings list (`mirror-per-world-limit: 6`).

### 5.3 The guardrail

`validateLayout` already checks chamber footprints, lanes and plates against each other and every
op against the forceload rectangles. Add: every op a `decorate` function emits is checked against
(a) every cell's `cellClear` boxes, (b) every gate footprint the campus knows (`GATES.stand` for
each G1 shape's bounds ∪ button, `GATES.far`, `GATES.gallery`, the transit gates), (c) every ring
footprint (R1's anchors at the widest distance, R2, R3, the tunnel's, the transit pair), (d) the
lanes, (e) every `keepClear` box. An overlap is a sentence in the problems list and the pack is
not written. That makes rule 0 mechanical.

Two harness touches that are not decoration, both small and both needed before the ring transit
can exist:

- `lib/ringtrip.js` `cleanup` removes **every** pair `ring list` shows. It must spare the transit
  pair: `for (const id of ids) if (id !== ctx.facility.transit.ringId) await kit.remove(id)`.
  Three lines, in a file the stage-3 agent owns; land it with the transit fixture, not before.
- `resetChamber` re-lays a cell's floor in plain quartz; if a cell ever gets a floor inlay, the
  reset blueprint must call the same `decorate` for that cell. Not needed by anything in this
  document (no inlays inside cell footprints), so leave it until it is.

### 5.4 Budget

Each wing is one datapack function run in one tick. The two limits: `maxCommandChainLength`
(65,536 commands per function, a gamerule; the compiler should refuse a function over it) and the
tick itself (a `fill` of 32,768 blocks is milliseconds; a wing of 100k block changes is well under
a second, and the client hitch does not matter because the launcher builds before "join"). The
manifest already prints `N commands, M blocks` per function on every build, which is the number to
watch. Rough additions, blocks (commands):

| Wing | Today (order) | Added | Of which |
|---|---|---|---|
| Ops | ~15k | ~2.0k (~180) | runway 30, pylons 30, rose 30, rims 2×17, pad frames 2×34, consoles 12, parapet 34, service corridor plates 16, lights 60, stripes ~150, mezzanine lanterns 10 |
| Gates | ~45k | ~4.5k (~120) | deck 312 + rails 110 + stair 18, hall runway 21, hazard rows 2×71, roof beams ~1.9k (14 strips), stripes ~500, plaques 0 (displays) |
| Rings | ~20k | ~2.4k (~60) | light well 9×3×60 = 1,620 air + 540 glass + 60 lanterns, rim 17, tunnel bands 6×9, stripes ~200 |
| Beams | ~15k | ~0.7k (~50) | pad 34, columns 16, consoles 9, office wall 18, stripes ~120 |
| Mirrors | ~12k | ~0.6k (~40) | amethyst frame 10, pane row 120, stripes ~200, seats 3 (M2's hall is a stage fixture: ~120 blocks at run time) |
| Menagerie | ~10k | ~0.9k (~70) | depot roof 319, posts 16, lanterns 24, hay 6, pool edge 40, hazard rows 90 |
| Range | ~30k (air + shell) | ~0.3k (~20) | runway 21, blast walls 40, pier 25, lanterns 4 |
| Annex | ~35k (air + platform) | ~0.3k (~20) | runway 21, pillars 16, pier 25 |

Every addition is under a quarter of its wing and a few hundred commands; nothing approaches
either limit. Displays are entities, not blocks, and are summoned by `cmd` lines (one each).

**Chunks: none added.** Every box above lies inside an existing `FORCELOAD` rectangle (the light
well is under the Ring Transit rectangle; the service corridor is under Ops; the piers are inside
the Range and Annex rectangles). `forceloadChunks()` stays at its current count.

### 5.5 Staged order

Stage 3 is being built now; nothing here touches its chamber logic except the three-line
`cleanup` guard, which belongs with the transit fixture. Order after stage 3:

| Stage | Delivers | Touches | Size |
|---|---|---|---|
| **3.5 Transit** (the addendum's "gate, ring and beam transit in stage 3", as its own PR right after) | `chambers/transit.js` (gates, ring, beams), `TRANSIT` and `TRANSIT_FEATURES` in campus, the service corridor and moved home plates, the Ops wall TRANSIT block, the console Transit tab, the `transit` self-test section, the `empty` lists, the `cleanup` guard, the `plates` walk start | campus, wings/ops, blueprint (`console`, `inlay`, `runway`), selftest, console, ringtrip (3 lines) | M |
| **3.6 Identity** | `PALETTE` roles, `stripe`/`plaque`/`pillar`/`deck`/`keepClear` and the guardrail in `validateLayout`, `wings/decor/*` for Ops, Gates, Rings, Beams, Menagerie (everything in section 3 that is not stage 4), `--plain` | blueprint, wings, campus (`KEEP_CLEAR`, `galleryFloor`); no chamber | M |
| **4 Mirrors and far sites** (as planned) + | the mirror transit pair with `-start`/`-stamp`, `banner` primitive, the mirror routes and the Range/Annex gate routes in the smoke test, M2's exhibition stage, M1's `fixture`, the piers, the `mirror-per-world-limit` baseline, the `Atrium`→`Lab` rename | as stage 4 already touches | +S on stage 4 |
| **5, 6, 7** | unchanged; stage 7's handbook gets a page per route | | |

**Nothing forces moving a chamber.** Things that looked like they might, and why they do not:

- The Ops gate (z −13..−9) sits between the mezzanine (z ≤ −15) and the boards at z −13.5, x ±6,
  which are 3 blocks clear of the frame; c0 (x 11..17, z 7..13) is in the opposite quadrant.
- The Ops ring rim (x 10..16, z −9..−3) is 2 blocks north of the east door's line and clear of
  c0's pylon (9, 0, 8) and board.
- The `Lab` rim (x 49..55, z −7..−1) is 20 blocks from R1's door (x 75..76, z −9) and 14.1 from
  R1's staging anchor; R2's board at (51.5, 4.6, 4.5) is 5 blocks south of it.
- The `BeamLab` pad (x −48..−44, z −2..2) is 3 blocks east of B1's door wall (x −51) and B1's
  pylon (−50, 0, −11).
- The Hall gate (z −53..−49) is 5 blocks south of G3's and G4's door walls' southern ends
  (z −60) and 4 north of the entrance point.
- The deck (x −29..−22, y 6..8) clears G5's board (x −31.5) and the G1 gallery's footprint
  (y 0..2).
- The light well (x 65..73, z 16..18, y −60..−1) is outside R3's clear volumes and under its
  gallery, which `cellSurrounds` lays at y 0 only.

One thing that *would* force a move and is therefore not proposed: a whole-room mirror in Ops
(needs 33 blocks of solid wall each way, and Ops is 16 high). It lives in M1's cell in stage 4.
