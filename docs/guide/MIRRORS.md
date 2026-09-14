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
- **On a wall, and it stays there.** A mirror needs solid wall two blocks out on every side of its
  opening, and neither the banner nor that wall can be broken while it is a mirror.

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

- **Right-click** a mirror to move it on to the next one — its own room first, then every other
  mirror by name, then back. With no others it says "No other mirrors found". Alone, click through
  them as fast as you like; with somebody else at the mirror, what it shows stays up three seconds.
- **Punch** it to go to the mirror it is showing. You land in front of that mirror's banner, facing
  out into its room.
- **Walk away** and, once nobody is near, it goes back to its own room.
- **Give it a start** to put one mirror first in its list: `/wormhole mirror start archive hub`
  has the first right-click on the mirror in an archived world open onto the main world's `hub`,
  and the next go on through the rest by name. It still shows its own room until somebody
  right-clicks it. `start archive none` takes the start away.

A mirror needs solid wall two blocks out on every side of its opening — a gap is refused by the
block to fill — and a banner on a post cannot be one. While it is a mirror, neither the banner nor
that wall can be broken; `mirror remove` takes it down.

## Commands

| Command | What it does |
| --- | --- |
| `mirror create <name>` | Makes the banner you are looking at a mirror, or renames the one already there (`mirror set` also works) |
| `mirror start [name] <mirror\|none>` | The mirror it opens onto when nobody has chosen one; `none` for its own room |
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

Eighty-nine ship. Sixty-five are places, one for every biome in the game, and `stamp` picks among
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
On plain 1.20 the banner stays where it is, in front of the view: taking it away needs a call that
arrived in 1.20.1, without which its patterns could not be put back.
The middle of the opening's bottom row is where a traveller lands, facing the way the mirror was
pointed. Click the opening to go through.

Nothing in the world changes. Only the players looking in are sent the view. A mirror set in a
solid wall -- solid for `mirror-proximity-radius` blocks on every side of the opening, with no
other mirror within twice the depth -- shows everything behind the wall out to its depth, the
same from wherever you stand in front of it. Any other mirror shows only the blocks you could
actually see through the opening from where you stand. That is what lets mirrors share a wall: a
row of alcoves a block apart each shows its own far side, whichever way you look in.

It is a first cut of [#278](https://github.com/khanjal/Wormhole-X-Treme/issues/278):

- **One-sided.** From behind, a mirror is its banner.
- **Only the open part of the opening opens.** Something solid in front of part of it closes that part.
- **Nothing shows past the opening's edges.** In a solid wall the wall hides them, so the far side behind it is drawn whole and does not change as you move. Anywhere else -- open air, a gap in the wall within `mirror-proximity-radius` of the opening, another mirror within twice the depth -- blocks that would reach past the edge are left out. The catch with a wall: from anywhere else you can see the space behind it, such as a doorway round the side, you see the far side there while you look in.
- **What you see is a capture**, a photograph of the far side taken once and kept in `data/mirror/captures/`. The first time anyone looks into a mirror, its capture is taken over a few seconds (the far world has to be loaded for that, and only then), and the mirror opens when it is ready. After that the far world need not be loaded at all: a mirror onto an archived world still shows it. `mirror stamp` takes the capture again; `mode dynamic` retakes it every `mirror-dynamic-resample-seconds` while somebody is looking; `mode static` (the default) never does, so a museum stays as captured.
- **The capture is the half-sphere a window can show, boxed:** `mirror-view-depth` plus two blocks ahead of the arrival point, to either side, up and down, and one block behind (`mirror-capture-radius` caps it). Inside that box it keeps only what somebody at the opening could see -- rays a degree apart from across the opening's face, in every direction a viewer in front of it could look, and the blocks they reach, air included, plus each block beside seen air; everything else is left to the real world. So a capture is the surfaces in view, and that is all a viewer is ever sent. A capture smaller than the box it needs, or from before buried blocks were marked, is taken again on the next look.
- **For checking a mirror:** `/wormhole mirror debug <name> full` draws that mirror whole and without limits for you alone -- everything its capture holds, through the opening, past the edges and into the ground, and wherever you stand, looking or not -- so you can walk round what the file holds and see how it comes through. `mirror debug off` turns views off for you, so you see the world as it is; `mirror debug on` puts either back.
- **The view reaches up to `mirror-view-depth` from the middle of the opening** (32 by default, up to 160, a server's usual view distance; a capture keeps only what can be seen, so a deeper one costs what its surfaces cost) and nothing is drawn past it, so further off you see what is really behind the mirror. A mirror in a solid wall always reaches the whole depth. A trimmed one reaches what one redraw can afford, up to that: shorter right against the mirror, where the view is widest, and shorter while you walk, growing to the full depth over a second or so when you stand still.
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
