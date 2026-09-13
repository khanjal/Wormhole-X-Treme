# Stargates

Building, dialling and wiring gates. Why they work the way they do is in the design notes,
[docs/GATES.md](../GATES.md).

## Contents

- [Building a gate](#building-a-gate)
- [Dialling](#dialling)
- [Shapes](#shapes)
- [Material groups](#material-groups)
- [Signs](#signs)
- [The iris](#the-iris)
- [Redstone](#redstone)
- [What travels through a gate](#what-travels-through-a-gate)
- [Commands](#commands)
- [Sounds](#sounds)

## Building a gate

1. `/wormhole gate build <shape>` — pick a [shape](#shapes).
2. Lay the frame and put a button or lever on the DHD position, then click it.
3. `/wormhole gate complete <name> [idc=CODE] [net=NETWORK]` — name it. The plugin places the
   name sign and levers, and saves the gate.

The frame material decides the gate's look: build `Standard` in obsidian for a Standard gate,
in lapis for an Atlantis one. See [Material groups](#material-groups).

The DHD takes any button — every wood, stone and Nether variant — or a lever. A button is swapped
for a lever when the gate activates, so it can be held open.

Gates work in the Nether and the End.

## Dialling

| How | What happens |
|---|---|
| Click the DHD of a **sign gate** | Dials whatever the dial sign shows |
| Click the DHD of a gate **without a sign** | Lights the chevrons and waits for `/dial <gate> [idc]` |
| Redstone on a **sign gate** | Dials whatever the dial sign shows. See [Redstone](#redstone). |

`/dial` finishes a dial the button started; it cannot start one on its own.

A dial is refused when the target's iris is closed, the target is already active, or another
gate already points at it. `/wormhole gate force <gate>` dials past those.

**A wormhole runs one way.** The destination end is an exit: nothing travels back up an open
wormhole, and a player walking into the destination ring is pushed out. A gate dialled out of
your base is not a door mobs can wander in through — though a creeper in *your* gate room will
be sent along with you.

## Shapes

```
plugins/WormholeXTreme/shapes/gate/      the shapes gates are built from
plugins/WormholeXTreme/shapes/mirror/    the looks a mirror's banner can wear
```

Eleven gate shapes ship, from `Minimal` to `Massive`. The four `SignDial` shapes —
`StandardSignDial`, `EvenSignDial`, `MinimalSignDial`, `HorizontalSignDial` — have a dial sign
and take redstone; the rest are `/dial`-only.

Shipped files are written out on first run and never overwrite yours. A deleted one comes back on
the next startup, an edited one is left alone, and anything you add is loaded. Older
`GateShapes/` folders are moved here on startup, and nothing is deleted.

**To make your own**, copy an existing `.shape` file, edit the grid, give it a unique name, and
put it in `shapes/gate/`. Try it without restarting with `/wormhole gate shapes reload <name>`.

### Shape material parameters

Shapes describe geometry; appearance comes from the [material group](#material-groups), so the
shipped shapes name no materials. A shape that must look a particular way whatever palette it is
built in can still pin one — an explicit value outranks the group.

| Key | Default | What it sets |
|---|---|---|
| `STARGATE_MATERIAL=` | `OBSIDIAN` | The frame (`[S]` cells) |
| `PORTAL_MATERIAL=` | `WATER` | The open portal (`[P]` cells) |
| `IRIS_MATERIAL=` | `STONE` | The closed iris |
| `ACTIVE_MATERIAL=` | `GLOWSTONE` | Lit chevrons (`:L` markers) |
| `CHEVRON_MATERIAL=` | *(none)* | What an unlit chevron may be built from. See [Unlit chevrons](#unlit-chevrons). |
| `SIGN_MATERIAL=` | `OAK_WALL_SIGN` | The name sign. Any `*_WALL_SIGN`. |

`MATERIAL_GROUPS=Standard,Atlantis` restricts which palettes a shape accepts; without it, every
group is accepted.

## Material groups

A gate's **shape** is its geometry; its **material group** is what that geometry is built from.
Groups live in `config.yml`, and the first is the default:

```yaml
gate-material-groups:
  Standard:
    structure: OBSIDIAN
    portal: WATER
    iris: STONE
    light: GLOWSTONE
    chevron: REDSTONE_LAMP
  Atlantis:
    structure: LAPIS_BLOCK
    portal: WATER
    iris: YELLOW_STAINED_GLASS
    light: SEA_LANTERN
    sign: WARPED_WALL_SIGN
```

A gate's group is identified by its **frame** material, so every group needs a different
`structure`; one that reuses another's is rejected at load. `sign` sets the name sign's type.
Missing keys fall back to built-ins, except `chevron`, which changes what a player has to build.

Materials resolve in this order:

1. A per-gate override (`/wormhole gate edit <gate> portal|iris|light`).
2. A material named in the shape file.
3. The gate's material group.
4. The built-in defaults.

Groups cost nothing at detection time, so offer as many as you like —
[why](../GATES.md#palettes-are-separate-from-shapes).

### Unlit chevrons

By default a chevron is an ordinary frame block, so you cannot see where the chevrons are until
the gate dials. Give a group a `chevron` material and those positions may be built from it:

```yaml
    chevron: REDSTONE_LAMP
```

**Either block is accepted there**, so existing gates are unaffected and you can convert one
chevron at a time. A chevron with an on state (`REDSTONE_LAMP`, or `COPPER_BULB` on 1.21+) lights
up as itself; anything else lights as the group's `light` material.

A shape can *require* the chevron block with a `[C]` cell. No shipped shape does, since it would
make every obsidian Standard gate undetectable.

### Shapes whose materials are in no group

A shape framed in a material no group declares still works, using the materials in its own file.
When its palette is unambiguous the plugin adds a group for it to `config.yml`, taking effect
immediately:

```yaml
  # Added automatically from a gate shape using this frame material.
  Diamond:
    structure: DIAMOND_BLOCK
    portal: WATER
    iris: GLASS
    light: GOLD_BLOCK
```

When shapes sharing a frame material disagree — the stock obsidian shapes ask for three different
irises — nothing is added and the log says why. Set `gate-material-groups-autodiscover: false` to
curate the list by hand; a group you delete then stays deleted.

### Clearing snapshotted overrides

Older versions of `/wormhole custom <gate> true` copied the shape's materials onto the gate, which
stops it following its group. `/wormhole custom -clean` lists affected gates, and
`/wormhole custom -clean confirm` clears them. Only a gate whose four overrides *all* match the
built-in defaults is touched, so deliberate choices are left alone.

## Signs

A gate has up to two signs.

- The **name sign** is placed by the plugin on the shape's `:N` block: name, network, owner.
- The **dial sign** is placed by *you* on the `[D]` block, and makes it a sign gate. Write the
  gate's name on it when you build. Right-click steps through destinations, left-click back.

```
      NAME SIGN                    DIAL SIGN

      -Helios-                     -Helios-
      N:Public                     Abydos          <- previous
      O:Justin                   » Chulak «        <- selected, and what a dial will use
                                   Dakara          <- next
```

The selection is coloured *and* wrapped in `»` `«`, so it still reads for a colourblind player.

| Setting | Default | What it does |
|---|---|---|
| `sign-color-gate-name` | `DARK_AQUA` | The gate's name, on both signs |
| `sign-color-network` | `GRAY` | The network line |
| `sign-color-owner` | `GRAY` | The owner line |
| `sign-color-selected` | `DARK_GREEN` | The destination a dial will use |
| `sign-color-neighbour` | `GRAY` | The destinations either side |
| `sign-glowing-text` | `false` | Glowing text — reads worse except in a very dark room |
| `sign-dial-match-material` | `true` | Convert a player's dial sign to the gate's sign material, keeping its text |

Colours are Bukkit names such as `AQUA` or `GOLD`; an unrecognised one falls back to the default.
Signs repaint when next written — a dial sign on the next click, a name sign on
`/wormhole gate regenerate <gate>`.

## The iris

An iris closes over a gate to block travel. Anyone walking into a gate whose far end has its iris
closed is bounced back with "Remote Iris is locked!".

- Build from a shape with an `:IA` marker (most have one). The plugin places the iris lever there.
- Set an iris deactivation code so callers can open it remotely:
  - `gate complete <name> idc=<code>` when building, or
  - `gate edit <gate> idc <code>` later, and `gate edit <gate> idc -clear` to remove it.

## Redstone

A redstone gate is a **sign gate with a redstone input**. Redstone does not choose a destination —
the dial sign does. Redstone just presses the button. A gate without a dial sign cannot be dialled
by redstone at all.

All four `SignDial` shapes mark two cells:

- **`[RD]`, the dial trigger** — run redstone to it. A pulse dials whatever the sign shows. The
  plugin places dust here for you.
- **`[RA]`, the gate-is-open output** — put a lever here. The plugin switches it on when the gate
  opens, to drive doors or lamps. It never counts as a trigger, so a gate cannot re-dial itself.

A signal counts on the marked block or on **any redstone component touching it**. The same is true
of the block the DHD button is mounted on, including directly beneath it.

`gate edit <gate> redstone true|false` switches redstone per gate. When the signal drops, a gate
with `shutdown_timeout: 0` shuts down.

### What counts as a trigger

The gate listens for a redstone **change**. A redstone block or a lit torch beside the marker
never changes, so it never triggers anything. What works is something that *switches*:

- a lever, button or pressure plate
- a detector rail a cart rolls over
- dust, a repeater or a comparator carrying such a change along

One circuit is one trigger: the gate acts once, then ignores triggers for a quarter of a second.

The wiring is yours — dust and levers on the marker cells can be broken and replaced freely. Only
the frame refuses a pickaxe.

### Where the markers are

On `StandardSignDial` and `EvenSignDial`:

```
   y=2   .  .  R          #  gate frame block   .  leave empty
   y=1   .  A  D          A  activation block   D  dial sign holder
   y=0   V  #  #          R  [RD]  dial       -> redstone dust
                          V  [RA]  gate open  -> lever
```

The frame block under the activation block carries the iris lever, on its face toward the player.

- **`MinimalSignDial`** — `[RD]` on top of the activation block; `[RA]` on the ground row at the
  foot of the pillar.
- **`HorizontalSignDial`** — lies flat. `[RD]` above the activation block, `[RA]` above the iris
  block.

### A gate sunk into the ground

Gates are often built one block low so the entrance is flush. Measured from the surrounding
ground:

| Shape | Button | `[RD]` — run the dust here | `[RA]` |
|---|---|---|---|
| `HorizontalSignDial` | ground level | **ground level** | ground level |
| `StandardSignDial`, `EvenSignDial` | ground level | **one block up** | below ground |
| `MinimalSignDial` | one block up | **two blocks up** | below ground |

An underground `[RA]` can be left unused, or dug out and given a lever in the pocket. Wiring to
the block under the DHD button is often easier than reaching `[RD]`.

### Driving a gate with a minecart

Put a **detector rail** in the line and wire it to `[RD]`. A cart rolling over it dials the sign's
destination and rides straight through. A powered rail will not work: it is already energised, so
a passing cart changes nothing.

A trigger on an open gate pushes its shutdown back, so steady traffic keeps it open — up to
`max-open-seconds` from when it first opened. `redstone-extend-open-time: false` turns that off.
A trigger on a gate that is lit but never dialled deactivates it.

### An older gate that ignores redstone

A gate records its marker positions when built. One built before its shape gained `[RD]` has none,
and no wiring will fire it. `/wormhole gate regenerate <gate>` re-reads the shape and adds them.

### `[RS]` — sign cycling, custom shapes only

An `[RS]` cell advances the dial sign on each pulse. No shipped shape has one —
[why](../GATES.md#why-no-shipped-shape-carries-an-rs). Keep it more than a block from `[RD]`; the
plugin drops an `[RS]` that lands adjacent to it.

## What travels through a gate

| | How it travels |
|---|---|
| Players | Their own move event |
| Minecarts, boats | With passengers re-seated on arrival |
| Ridden horses, camels, pigs, donkeys, llamas, striders | With the rider, re-seated |
| Arrows, tridents, snowballs, eggs, ender pearls, potions, fireballs | Followed from launch, crossing the tick they reach the portal |
| Mobs, dropped items, XP orbs, armour stands | A sweep of open gates every `entity-scan-interval-ticks` (default once a second) |
| Item frames, paintings | Never |

A fast mob or item can cross the portal between two sweeps and carry on without travelling; lower
the interval to catch more, at the cost of more scanning.

A projectile is re-fired out of the far gate with the same speed, shooter, damage and effects, so a
kill through a gate is credited correctly. **An ender pearl thrown through a gate teleports its
owner across**, skipping the permission and cooldown checks a player walking through would face.

## Commands

| Command | What it does |
|---|---|
| `gate build <shape>` | Start building |
| `gate complete <name> [idc=] [net=]` | Name and register what you built |
| `gate list [network]` | Gates you can see |
| `gate remove <gate> [-all]` | Take it down |
| `gate edit <gate> <field> [value]` | Change a gate — fields below |
| `gate go <gate>` | Teleport to it |
| `gate force <gate>` | Dial past the usual refusals |
| `gate regenerate <gate\|-all>` | Recompute markers and arrival point |
| `gate validate <gate\|-all>` | Check it is still standing |
| `gate refresh` | Your next DHD click re-detects that gate from scratch |
| `gate import` | [Bring gates from another fork](SERVER.md#coming-from-another-wormhole-x-treme) |
| `gate shapes reload [name]` | Reload shape files without a restart |
| `gate shapes validate <name>` | Check a shape file |

**`gate edit` fields:** `portal`, `iris` and `light` (materials), `group` (a whole material group),
`woosh` (how far the woosh pushes out), `redstone` and `custom` (`true`/`false`), `idc` (a code, or
`-clear`), `owner`.

`group` changes what the gate *draws* — portal, lights, iris — not the frame blocks somebody built.

**`gate regenerate <gate>`** re-reads the gate's shape file and moves its redstone hookup, iris
lever and signs to match, then recomputes where travellers arrive. Use it for a gate that lands
people at its side. Markers are only added or moved, never removed; a gate that no longer matches
its shape is left alone, with the reason. It cannot fix a gate facing the wrong way — rebuild that.
**`-all`** only recomputes arrival points, and reports how many changed.

**`gate validate`** finds gates taken apart by WorldEdit, which fires nothing the plugin can see.
It reports missing frame blocks and a dial sign that is no longer a sign. A gate in an unloaded
chunk reads as fine; this never loads a chunk to check.

**`gate shapes validate`** catches mistakes that do not throw: a short row, a skipped layer, a
duplicate marker, a material this Minecraft version lacks, redstone landing on the frame.
**`reload`** runs the same checks and only replaces the loaded shape if they pass.

## Sounds

General rules — naming, volume, `none` — are in [Sounds](SERVER.md#sounds).

| Setting | Default | When it plays |
|---|---|---|
| `gate-sounds-enabled` | `true` | Everything below is ignored when off |
| `gate-sound-volume` | 1.5 | Louder than rings — a gate is a landmark |
| `gate-sound-activate` | `block.conduit.activate` | As the gate begins to dial |
| `gate-sound-chevron` | `block.iron_trapdoor.close` | Each chevron, pitch climbing |
| `gate-sound-kawoosh` | `entity.player.splash.high_speed` | As the wormhole forms, at pitch 0.7 |
| `gate-sound-ambient` | `ambient.underwater.loop` | On repeat while open, at 40% volume |
| `gate-sound-ambient-ticks` | 70 | How often it repeats; shorter layers it |
| `gate-sound-close` | `block.conduit.deactivate` | As the wormhole closes |
| `gate-sound-iris-close` | `block.iron_door.close` | As the iris seals |
| `gate-sound-iris-open` | `block.iron_door.open` | As the iris opens |
| `gate-arrival-splash-ticks` | 20 | How long a traveller sees water on arrival. `0` turns it off. |

The kawoosh default changed in 1.5.0. A `config.yml` that already has the old value keeps it —
set `gate-sound-kawoosh`, or delete the line to have it rewritten.

If a long trip shows no arrival splash, raise `gate-arrival-splash-ticks`: the chunk load can wipe
it. Not far, though — the client believes it is swimming for as long as the water shows, and that
is felt as a stumble on landing.
