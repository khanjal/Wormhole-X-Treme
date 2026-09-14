# Quantum mirrors

A **quantum mirror** is a banner. Walk up to it, right-click, and you are in another world. Why it
works the way it does — how a banner reads its destination, the look library, what was tried and
dropped — is in the design notes, **[docs/MIRRORS.md](../MIRRORS.md)**.

Nothing is built: no frame, no pad, no partner. That is why a corridor lined with mirrors is
practical in a way a corridor of gates is not.

- **One-way by design.** A mirror sends you somewhere; that place does not know about it. To come
  back, put a mirror at the far end pointing home. That is what lets a mirror open onto an archived
  world you would rather not build in at all.
- **Cross-world, and refuses otherwise.** A beam place is for points in the same world. Set
  `mirror-allow-same-world: true` if you want one anyway.
- One world can hold as many mirrors as you like, each opening onto a different world.

## Contents

- [Setting one up](#setting-one-up)
- [Commands](#commands)
  - [Naming one, or just looking at it](#naming-one-or-just-looking-at-it)
  - [Renaming one](#renaming-one)
- [Making it look like where it goes](#making-it-look-like-where-it-goes)
- [Going dark, and keeping up](#going-dark-and-keeping-up)
- [Saying what it is](#saying-what-it-is)

## Setting one up

A pair of banners that lead to each other:

```
/wormhole mirror set nether      # looking at the banner in the overworld
/wormhole mirror link nether     # looking at the banner in the nether
```

That is the whole job. The second banner is bound for you — `nether-return`, unless you name it
(`/wormhole mirror link nether home`) — and each points at the other.

One way, to a place with no banner:

```
/wormhole mirror set museum        # looking at the banner
/wormhole mirror target museum     # standing where people should arrive
```

**You arrive at the far banner itself**, where somebody who had just touched it would stand, looking
out into the room — not a block in front, which could be a wall or a drop.

`link` is a snapshot. Move either banner afterwards and run `link` again.

Wall and standing banners both work. Clicking a mirror that is named but not pointed tells you the
commands to finish it — if you could run them; anyone else just hears it does not open onto
anywhere yet.

## Commands

| Command | What it does |
| --- | --- |
| `mirror set <name>` | Makes the banner you are looking at a mirror, or renames the one already there (`mirror create` also works) |
| `mirror target <name>` | Points it at where you are standing |
| `mirror link <other> [name]` | Joins the banner you are looking at to that mirror, both ways |
| `mirror stamp [name] [look]` | Makes the banner look like where it goes |
| `mirror display [name] <always\|proximity>` | Show its look always, or only up close |
| `mirror mode [name] <static\|dynamic>` | Keep the look, or re-read the far side |
| `mirror remove [name]` | Makes it an ordinary banner again |
| `mirror list` | Every mirror and where it opens onto |

All of them need `wormhole.config`: a mirror moves players between worlds.

### Naming one, or just looking at it

The four verbs with `[name]` in brackets take the mirror on the banner you are looking at when
you leave the name out:

```
/wormhole mirror stamp cavern       # looking at the banner: give it the cavern look
/wormhole mirror stamp              # ... or read the far side and paint it from that
/wormhole mirror display proximity  # only show its look up close
/wormhole mirror remove             # give the banner back
```

Which is mostly for the far half of a pair. `link` names it for you — `nether-return` — and that
is the name you are least likely to remember while standing in front of it.

A mirror's name still wins where a word could be either. `stamp cavern` is the mirror called
`cavern` if there is one, and the *look* called `cavern` only if there is not. Setting words are
never names: `display proximity` is always the banner you are facing, and `display museum` is a
name with the setting forgotten, so it says so.

### Renaming one

`set` again, looking at the banner:

```
/wormhole mirror set old-spawn      # looking at a banner that is already a mirror
```

It keeps where the mirror goes, what it looks like, and its display and mode settings — only the
name changes, and the old one is gone rather than left behind. `set` works out what you meant from
what already exists: a name it knows moves that mirror to this banner, a banner it knows renames
the mirror on it, and neither makes a new one. The single case it will not guess at is a name that
belongs to a mirror elsewhere *and* a banner that is already a different mirror, since either
reading would quietly strand one of them; it says so and changes nothing.

## Making it look like where it goes

A corridor of plain white banners tells you nothing. `stamp` reads the destination and paints the
banner from it:

```
/wormhole mirror stamp museum
```

The biome picks the frame — rising flame for the Nether, white crests over blue for an ocean — and
the blocks around the arrival point become coarse squares in their dominant colours. **Indoors**,
the room's own blocks decide instead, so a library comes back the brown of its shelves.

It is a snapshot, not a live window. Rebuild the far side and stamp again.

Or name a look:

```
/wormhole mirror stamp museum cavern
```

Eighty-eight ship. Sixty-five are places, one for every biome in the game, and `stamp` picks among
them on its own. The rest say something about the mirror instead — `hub`, `exit`, `market`,
`warning`, `private`, `plain` and more — and only a named `stamp` gets one. None change what a
mirror does: `private` is paint, not a permission.

They are `.mirror` text files in `shapes/mirror/`. Edit one and it stays edited; delete one and it
comes back; add your own and `stamp` offers it. Every look is drawn, with its recipe, in
[docs/MIRRORS.md](../MIRRORS.md#the-library-at-a-glance).

## Going dark, and keeping up

```
/wormhole mirror display museum proximity   # dark until somebody walks up to it
/wormhole mirror mode museum dynamic        # re-reads the far side when they do
```

**`proximity`** shows the mirror blank until a player is within `mirror-proximity-radius` blocks.
The banner in the world stays stamped — only far-away players are sent the blank — so if you ever
remove the plugin, your corridor is still painted. Needs 1.20.1; on 1.20 the mirror just stays
visible.

**`dynamic`** re-reads the destination when somebody walks up, at most once every
`mirror-dynamic-resample-seconds`. A mirror nobody visits is never re-read. Works on every version.

The two are independent; any combination works.

## Mirrors are windows (prototype)

A mirror opens onto where it goes. There is nothing to set, and mirrors made before this open the
same way.

Anybody within `mirror-proximity-radius` blocks, on the banner's side, sees the banner vanish and a
banner-sized opening, one wide and two tall, showing the destination: real blocks, so it has depth as you move. A banner hung on a
wall opens in the wall, running down from the banner. A freestanding banner opens in the air behind
it, running up from where it stands, facing whichever of north, south, east or west is nearest.
The middle of the opening's bottom row is where a traveller lands, facing the way the mirror was
pointed. Click the opening to go through.

Nothing in the world changes. Only the players looking in are sent the view, and only the blocks
they could actually see through an opening from where they stand. That is what lets mirrors share
a wall: a row of alcoves a block apart each shows its own far side, whichever way you look in.

It is a first cut of [#278](https://github.com/khanjal/Wormhole-X-Treme/issues/278):

- **One-sided.** From behind, a mirror is its banner.
- **Only the open part of the opening opens.** Something solid in front of part of it closes that part.
- **Nothing shows past the opening's edges.** In a wall the wall hides them; in open air, blocks that would reach past the edge are left out.
- **What you see is a capture**, a photograph of the far side taken once and kept in `data/mirror/captures/`. The first time anyone looks into a mirror, its capture is taken over a few seconds (the far world has to be loaded for that, and only then), and the mirror opens when it is ready. After that the far world need not be loaded at all: a mirror onto an archived world still shows it. `mirror stamp` takes the capture again; `mode dynamic` retakes it every `mirror-dynamic-resample-seconds` while somebody is looking; `mode static` (the default) never does, so a museum stays as captured.
- **The capture reaches `mirror-capture-radius` around the arrival point** (96 by default), 64 below it to 64 above. Its edge is the horizon. It keeps every block with a face open and the layer under it; what is buried deeper is left out. A capture smaller than the configured box is taken again on the next look.
- **The view reaches up to `mirror-view-depth` from your eye** (64 by default, up to 128) and nothing is drawn past it, so further off you see what is really behind the mirror. How far your own view reaches is what one redraw can afford, up to that: shorter right against the mirror, where the view is widest, and shorter while you walk, growing to the full depth over a second or so when you stand still.
- **Your own world's creatures inside the view are hidden** from you while you look. Other players are not.
- **A mirror's banner at the far end is left out of the view**, so a linked pair looks straight through.
- **Blocks only, no mobs or players from the far side**, and lit by this world rather than the far one: behind a wall that is dark, and only what makes its own light -- glowstone, lanterns -- is bright.
- **The look, `display` and `mode` show only where there is no view**: from behind, from out of range, or onto a world that is not loaded.

## Saying what it is

Look at a mirror from about six blocks and it names itself, and the world it opens onto, above the
hotbar:

```
:: museum -- click to travel to nether.
```

It stays while you keep looking, and only one mirror speaks at a time — the one under your crosshair.
Mirrors that do not go anywhere yet say nothing. `mirror-approach-message: false` turns it off.
