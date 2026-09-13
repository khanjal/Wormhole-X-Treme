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
| `mirror set <name>` | Makes the banner you are looking at a mirror |
| `mirror target <name>` | Points it at where you are standing |
| `mirror link <other> [name]` | Joins the banner you are looking at to that mirror, both ways |
| `mirror stamp <name> [look]` | Makes the banner look like where it goes |
| `mirror display <name> <always\|proximity>` | Show its look always, or only up close |
| `mirror mode <name> <static\|dynamic>` | Keep the look, or re-read the far side |
| `mirror remove <name>` | Makes it an ordinary banner again |
| `mirror list` | Every mirror and where it opens onto |

All of them need `wormhole.config`: a mirror moves players between worlds.

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

## Saying what it is

Look at a mirror from about six blocks and it names itself, and the world it opens onto, above the
hotbar:

```
:: museum -- click to travel to nether.
```

It stays while you keep looking, and only one mirror speaks at a time — the one under your crosshair.
Mirrors that do not go anywhere yet say nothing. `mirror-approach-message: false` turns it off.
