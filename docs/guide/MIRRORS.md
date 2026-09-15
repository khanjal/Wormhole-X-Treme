# Quantum mirrors

A **quantum mirror** is a banner on a wall. Walk up to it and it shows its own room, as a mirror
does; right-click it to choose another mirror, and punch it to go through. Why it works the way it
does — the look library, what was tried and dropped — is in the design notes,
**[docs/MIRRORS.md](../MIRRORS.md)**.

Nothing is built: no frame, no pad, no partner.

- **Every mirror is on the network.** Nothing is pointed by hand. A right-click walks the other
  mirrors, and a punch goes to the one showing.
- **One to a world** by default (`mirror-per-world-limit`, 0 for no limit), so the list a
  right-click walks is the worlds you can reach.
- **On a wall, and it stays there.** A mirror needs solid wall a block out on every side of its
  opening — two is better, and `create` says so — and neither the banner nor that wall can be
  broken while it is a mirror.

## Contents

- [Setting one up](#setting-one-up)
- [Commands](#commands)
  - [Naming one, or just looking at it](#naming-one-or-just-looking-at-it)
  - [Renaming one](#renaming-one)
- [Making it look like where it goes](#making-it-look-like-where-it-goes)
- [Going dark, and keeping up](#going-dark-and-keeping-up)
- [Saying what it is](#saying-what-it-is)

## Setting one up

Hang a banner on a wall, look at it, and:

```
/wormhole mirror create library
```

That is the whole job. A plain white banner gets the `mirror` look; one you patterned first keeps
its patterns. Walk up to it and it shows its own room, flipped across the wall, with nobody in it.

Make a mirror in another world the same way, and the two find each other:

- **Right-click** a mirror to move it on to the next of the other mirrors, by name, round and
  round; its own room is never in the round, only what it shows before anybody clicks. With no
  others it says "No other mirrors found". Alone, click through them as fast as you like; with
  somebody else at the mirror, what it shows stays up three seconds.
- **Punch** it to go to the mirror it is showing. You land in front of that mirror's banner, facing
  out into its room.
- **Walk away** and, once nobody is near, it goes back to its own room.
- **Give it a start** to put one mirror first in its list: `/wormhole mirror start archive hub`
  has the first right-click on the mirror in an archived world open onto the main world's `hub`,
  and the next go on through the rest by name. It still shows its own room until somebody
  right-clicks it. `start archive none` takes the start away.

A mirror needs solid wall a block out on every side of its opening — a gap is refused by the block
to fill — and a banner on a post cannot be one. Two blocks out hides the room's edges better from
an angle, so a wall short of two is made and told which block is not solid. While it is a mirror,
neither the banner nor that wall can be broken; `mirror remove` takes it down.

### Two banners wide

Hang two wall banners side by side, facing the same way, and `create` on either one: the pair is
one mirror, two wide and two tall. Either banner answers a click, neither can be broken, and a
traveller lands between them.

Its wall is a column wider: solid for **four across and four tall** — the two blocks behind the
banners and one more either side, and from one above the banners to one below the opening. A wall
built for one banner is a column short, and `create` says so.

A mirror you already made one wide stays one wide: `mirror remove` it, hang the second banner, and
`create` again. A room captured before wide mirrors existed is a little narrow for one; `mirror
stamp <name> mirror` retakes it.

## Commands

| Command | What it does |
| --- | --- |
| `mirror create <name>` | Makes the banner you are looking at a mirror, or renames the one already there (`mirror set` also works) |
| `mirror start [name] <mirror\|none>` | The mirror a right-click opens onto first; `none` takes it away |
| `mirror stamp [name] [look]` | Makes the banner look like where it goes |
| `mirror display [name] <always\|proximity>` | Show its look always, or only up close |
| `mirror mode [name] <static\|dynamic>` | Keep the look, or re-read the far side |
| `mirror remove [name]` | Makes it an ordinary banner again |
| `mirror list` | Every mirror, and what each is showing |

All of them need `wormhole.config`: a mirror moves players between worlds.

### Naming one, or just looking at it

The verbs with `[name]` in brackets take the mirror on the banner you are looking at when you
leave the name out:

```
/wormhole mirror start hub          # looking at the banner: a right-click opens onto hub first
/wormhole mirror stamp cavern       # give it the cavern look
/wormhole mirror remove             # give the banner back
```

A mirror's name still wins where a word could be either. `stamp cavern` is the mirror called
`cavern` if there is one, and the *look* called `cavern` only if there is not. Setting words are
never names: `display proximity` is always the banner you are facing, and `display museum` is a
name with the setting forgotten, so it says so.

### Renaming one

`create` again, looking at the banner:

```
/wormhole mirror create old-spawn   # looking at a banner that is already a mirror
```

It keeps its start, what it looks like, and its display and mode settings — only the name
changes, and the old one is gone rather than left behind. `create` works out what you meant from
what already exists: a name it knows moves that mirror to this banner (which has to pass the wall
rules), a banner it knows renames the mirror on it, and neither makes a new one. The single case it
will not guess at is a name that belongs to a mirror elsewhere *and* a banner that is already a
different mirror, since either reading would quietly strand one of them; it says so and changes
nothing.

## What you see in one

Walk up to a mirror, on the banner's side and within `mirror-proximity-distance` blocks, and the banner
is gone: an opening its own size, one wide and two tall, running down from where it hangs, shows a
room — its own, flipped across the wall as a mirror would show it and with nobody in it, or the room
of the mirror chosen at it. Real blocks, so it has depth as you move. On plain 1.20 the banner stays
where it is, in front of the view: taking it away needs a call that arrived in 1.20.1, without which
its patterns could not be put back.

Nothing in the world changes. Only the players looking in are sent the view. A mirror set in a
solid wall — solid for `mirror-proximity-distance` blocks on every side of the opening, with no other
mirror within twice the depth — shows everything behind the wall out to its depth, the same from
wherever you stand in front of it, while its room is small enough to send at once (20,000 blocks;
a deep room is more). `create` says so when a new mirror is closer than that to another. Any
other mirror shows the same room, clipped to where you stand: only the blocks you
could actually see through the opening, so nothing shows past its edges — and the outer half of
its wall's edge is kept clear, so a wider wall shows a little more beside the opening as you move.
Either way it reaches the full depth, standing or walking. The far part of a clipped room, past
48 blocks, follows you a step late: it is judged again when you move into another block, not on
every step, since a small step swings a distant view a long way sideways.

- **What you see is a capture:** a photograph of a mirror's room, kept in
  `data/mirror/captures/` under its place's name, which `mirror list` shows; a capture no mirror
  uses is deleted at the next startup. A new mirror's is taken within a second of `create`, over a few seconds;
  another mirror's room is taken the first time somebody chooses it, if nobody has yet. After that
  its world need not be loaded at all, so a mirror in an archived world still shows. `mirror stamp`
  takes a capture again; `mode dynamic` retakes it every `mirror-dynamic-resample-seconds` while
  somebody is looking; `mode static`, the default, never does, so a reflection shows the room as it
  was when captured.
- **A capture holds what somebody at the opening could see:** within `mirror-view-depth` of the
  opening, rays a degree apart through it, and the blocks they reach, air included; everything else
  is left to the real world. So a capture is the surfaces in view, and that is all a viewer is sent.
- **The view reaches `mirror-view-depth` from the opening** (160 by default, from 4) and nothing
  is drawn past it, standing or walking, on any wall. At 160 — ten chunks, as far as a server
  usually sends — the room ends where the client has nothing to show anyway, so nothing of this
  world shows through. A room is its surfaces, so depth costs little; taking the first capture
  loads that much of the room's world, once. Lower it for a mirror onto somewhere small.
- **One-sided.** From behind, a mirror is its banner.
- **Blocks only.** No players or creatures from the room shown, and your own world's creatures
  inside the view are hidden from you while you look. Lit by this world, so a room behind a wall is
  dark except for what makes its own light.
- **For checking a mirror:** `/wormhole mirror debug <name>` says in a few lines what its capture
  is and how your view of it is drawn, and `debug <name> all` lists everything, a fact a line: green when it is drawn whole, and in red whatever trims
  it or stops it — a gap in its wall, another mirror too near, a missing capture file. It is not in
  the usage line, but tab-completes for anyone who may run it. `debug <name> full` draws the whole
  capture for you alone, wherever you stand. `mirror debug off`
  turns views off for you, so you see the world as it is; `mirror debug on` puts either back.

## Its look

A plain white banner made a mirror gets the `mirror` look: pale glass, a glint and a frame. A banner
you patterned first keeps its patterns. The look is what you see from further than
`mirror-proximity-distance`, from behind, and before a mirror's room is captured — so a corridor of
mirrors still reads as a row of doors from the far end.

```
/wormhole mirror stamp museum cavern   # a named look
/wormhole mirror stamp museum          # read the mirror's room and paint the banner from that
```

Read from the room, the biome picks the frame — rising flame for the Nether, white crests over blue
for an ocean — and the blocks around it become coarse squares in their dominant colours. **Indoors**,
the room's own blocks decide instead, so a library comes back the brown of its shelves.

Eighty-nine looks ship. Sixty-five are places, one for every biome in the game. The rest say
something about the mirror instead — `mirror`, `hub`, `exit`, `market`, `warning`, `private`, `plain`
and more. None change what a mirror does: `private` is paint, not a permission.

They are `.mirror` text files in `shapes/mirror/`. Edit one and it stays edited; delete one and it
comes back; add your own and `stamp` offers it. Every look is drawn, with its recipe, in
[docs/MIRRORS.md](../MIRRORS.md#the-library-at-a-glance).

`display proximity` shows the banner blank until somebody is within `mirror-proximity-distance` blocks,
and `mode dynamic` re-reads its look when they walk up. Both matter only where there is no view — a
mirror whose room is not captured yet.

## Saying what it is

Look at a mirror from about six blocks and it says what a click will do, above the hotbar:

```
:: museum -- right-click to choose a mirror.
:: museum -- punch to travel to hub, right-click for another.
```

It stays while you keep looking, and only one mirror speaks at a time — the one under your crosshair.
`mirror-approach-message: false` turns it off.
