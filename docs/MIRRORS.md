# Quantum Mirrors — Design

The decisions behind quantum mirrors, and the place they are argued about. The
[README](../README.md) says what they do; this says why. Gates have their own document,
[GATES.md](GATES.md), rings have [RINGS.md](RINGS.md), and beaming has [BEAMS.md](BEAMS.md).

A mirror is a banner you click to be somewhere else. That is the whole mechanism. It has no
structure to build, no pair to keep in step, no address, no network, and no state — which is
why a corridor lined with mirrors is practical in a way a corridor of gates is not.

| | Stargate | Ring | Mirror |
|---|---|---|---|
| What it is | A built structure | A pad in a floor | One banner |
| Activation | Dial, button, redstone | Walk into it | Right-click it |
| Direction | One way per dial | Both ends fire | One way, always |
| Range | Cross-world, config permitting | Same world, always | Cross-world by default |
| Appearance | Permanent structure | Invisible until it fires | A banner, and one you can stamp |

## Looking through it

The question this feature exists to answer: can a mirror show what is on the other side?

Not literally. A banner is a dyed base plus at most six flat patterns, in sixteen colours, and
nothing in Bukkit can render a view onto one. A live window needs a map in an item frame or a
display entity — a different block, a different feature, and a different kind of cost.

What a banner *can* do is carry an impression, and an impression turns out to be enough. When a
mirror is stamped it goes and looks at its own destination, and reduces what it finds to two
things:

- **Where it is.** The biome at the arrival point picks the preset — the base colour and the
  border and shapes that frame everything else. The Nether reads as black and rising flame; an
  ocean as white crests over blue.
- **What is actually there.** The blocks in a box around the arrival point are counted, mapped
  to the nearest dye colour, and the three commonest become coarse squares laid *under* the
  frame. A lava field comes back orange whatever biome it sits in. A field of wheat comes back
  yellow. The squares are the low-resolution part of the picture, and deliberately so: three
  blocks of colour in a frame read as things seen through a doorway.

### Indoors

A destination inside a building is the case that breaks the biome half, and it is a common one —
a mirror into a library, a vault, a mineshaft. The biome there describes the ground the roof
happens to stand on, which is not what anybody standing in the room would say about it.

So the sampler also reports whether the place is *enclosed* — better than half the sampled
blocks solid — and when it is, two things change. The frame comes from `indoors.mirror` rather
than from the biome, and the commonest block in the room becomes the cloth itself rather than a
square on it. A library comes back the brown of its shelves, with the grey of its walls beside
them. A room of copper comes back orange.

**Except where being enclosed is not news.** The Nether is solid rock with a ceiling on it; a
cave is a cave. The sampler reports those as enclosed for every mirror ever pointed at them, so
the rule as first written meant no mirror into the Nether could ever wear the Nether's look —
reported from real play as "the banner doesn't look right", and quite right too. A preset says
`Sheltered=true` to mean "this kind of place is enclosed anyway" and keeps its own look; the
shipped `nether` and `cavern` both do. What is left for the indoor rule is a room somewhere it
is *not* normal to be inside one, which is the library it was written for.

That flag was added to the choice of frame and to nothing else, which fixed a third of the
problem and left it looking fixed. Being enclosed drives three decisions — the frame, whether
the commonest block replaces the cloth colour, and whether that block also gets a square — and
a fourth in the sentence the command prints. A mirror onto the Nether went on losing its red to
whatever netherrack averaged to, and `mirror stamp` kept a *second copy* of the frame rule
without the flag, so stamping by hand dressed a Nether mirror as a room while a dynamic one
corrected itself on the next approach. One banner, two appearances, depending on which code
touched it last.

All four now ask `MirrorPreset.readsAsARoom(view)`, which is the one place that knows the
difference between somewhere enclosed and somewhere that is a room. If you add a preset for a
place that is enclosed by its nature, `Sheltered=true` is the whole of what you have to say.

The threshold is a judgement and nothing more. At 55% a cellar reads as indoors, which is
right, and a forest does not, which is also right.

### Static and dynamic

A **static** mirror is sampled once, when it is stamped, and never again. Two reasons, and the
second is the stronger one:

- Re-reading the far side on every click would mean loading a distant chunk on a click.
- A banner that changed on its own would be worse to build with. A look an operator chose
  should stay chosen.

Rebuild the far side and it still shows the old place until somebody stamps it again. That is
the same bargain `mirror link` already makes: a snapshot, not a subscription.

A **dynamic** mirror re-reads the far side, but only when somebody walks up to it and only
after `mirror-dynamic-resample-seconds` have passed since the last read. That is what makes it
affordable: sampling still loads a distant chunk, so a mirror nobody visits is never sampled at
all, and a player pacing in front of one gets the same answer until the interval is up.

This is independent of `display`, and genuinely so — for a while it was not, which is worth
recording because the mistake is an easy one to make again. The sweep visited only proximity
mirrors and gave up entirely on a server without per-player block updates, so `always` plus
`dynamic` never re-read anything and `dynamic` did nothing at all on 1.20. The two are separate
because their costs are separate: hiding needs a packet per player and therefore a server that
can send one, while re-reading writes to the banner everybody already sees and needs neither.

A dynamic mirror that has never been stamped will take its first look on the first approach.
Otherwise `mode dynamic` would describe something only `stamp` could start.

The re-read look is kept in memory and written to the banner, but not saved to `mirror.yml` on
every approach — a busy corridor would otherwise be a stream of file writes, and a dynamic
mirror re-reads on the next approach anyway. The worst a restart costs is one sample.

## Always and proximity

A corridor of lit banners is a corridor of lit banners. `mirror display <name> proximity` makes
one go dark until somebody comes within `mirror-proximity-radius` blocks of it.

### The banner in the world is never the blank one

This is the part worth being careful about, and the design follows from a single fact: **banner
patterns are vanilla data.** Disable this plugin, or remove it, and a stamped banner is still a
stamped banner. So the world's block keeps the look, always, whatever `display` says — and what
a proximity mirror actually does is send the *blank* to players who are too far away, and take
that illusion back when they come close.

The other way round would have been easier. Keeping the world's block blank and sending the
look to whoever is near would be self-healing: any chunk resend shows blank, which is what a
distant player should see anyway. It was rejected because it makes this plugin the only thing
standing between an operator and a corridor of plain white cloth.

Two consequences fall out of that choice, and the sweep carries both:

- Being far away is not a state that arranges itself. Everyone in the world is sent the blank
  once, after which only crossings are sent — a corridor with somebody standing still in it
  sends nothing at all.
- The illusion has to be handed back when the plugin stops. It is, on disable: otherwise
  whoever was standing far off keeps a blanked banner on their client until something makes the
  server resend that chunk, which looks exactly like the plugin having eaten their banners.

### The version boundary

`Player.sendBlockUpdate(Location, TileState)` is the whole mechanism, and it **does not exist
on plain 1.20** — present from 1.20.1 on, checked against the jars for all seven versions the
matrix builds. On that one version a proximity mirror simply stays visible, which is a cosmetic
loss on the oldest supported server rather than a mirror that never shows anything. Setting it
there is not wasted: the banner keeps its look either way, and the setting starts working when
the server is upgraded.

It is reached reflectively for the same reason `PatternType` is. Calling it directly would
compile against the 1.20.4 target and throw `NoSuchMethodError` on 1.20 — at the moment a
player walks down a corridor, which is not when that should be discovered.

### What the sweep is careful about

It runs on a timer for the life of the server, so the order of its checks is the design. Before
anything touches a block it has ruled out mirrors it has no reason to visit, worlds that are not
loaded, and chunks that are not loaded — the chunk check comes before `getBlockAt`, which would
load one.

There are two reasons to visit a mirror and both are narrow: hiding it, and keeping a dynamic
one current. So a server whose mirrors are all ordinary does no work here beyond walking the
list.

That was briefly untrue. When a mirror first learned to name itself it did so on approach, which
meant this sweep had to visit every ordinary mirror to work out who was near it — a distance
check per player per mirror. Moving the announcement to the player's own line of sight took the
third reason away again, and with it the cost.

## Saying what it is

A stamped banner looks like scenery, and a corridor of them looks like decoration. Nothing about
one said it was a door until somebody happened to right-click it, which is a thing players do to
signs and not to wall hangings.

So a mirror that goes somewhere names itself to whoever is looking at it, from about six blocks:

```
:: museum -- click to travel to nether.
```

Three decisions in that one line, none of them arbitrary.

**Above the hotbar, not in chat.** The same call the transport rings use. It replaces itself and
then goes, where chat would leave a line behind for every banner walked past — a corridor would
cost a player their whole chat window to walk down.

**Looking at, not standing near.** This began the other way: sent once, on crossing into the
proximity radius, which is how the rings announce themselves. For a ring that is right, because
walking in starts something. A mirror is not started by arriving at it — it is looked at,
considered, and then clicked — and an action bar line fades after about three seconds, so the
message had come and gone by the moment it was wanted. You were told there was a door while
walking towards it, and told nothing while stood in front of it deciding.

Re-sending to everyone in range is worse than it sounds: a corridor puts a player within eight
blocks of several mirrors at once, and they would take turns in the one action bar slot,
flickering once a sweep. Looking at one picks exactly one, because a player has a single target
block and there is nothing to arbitrate. The line is re-sent every sweep for as long as they
keep looking, which is what a steady line means when the bar fades on its own.

It also made the plugin cheaper. Asking every mirror who is near it is a distance check per
player per mirror; asking each player what they are looking at is one question regardless of how
many mirrors there are, and no question at all in a world that has none.

**Only a mirror with a destination.** An unpointed one is a banner somebody is halfway through
setting up, and announcing it would be the plugin telling whoever glanced at it about somebody
else's unfinished work. Clicking it already says what to do, to the one person who asked.

It carries the plugin's `::` header itself, unlike everything else a mirror says, because the
action-bar path does not go through the call that prefixes it. Without that, a line appearing
above the hotbar on a server running several plugins is a line the player cannot act on — they
have no idea what put it there.

## The preset files

Presets live in `shapes/mirror/*.mirror`, beside `shapes/gate/*.shape`, and are read the same
way: the shipped ones are written out on first run so there is something to copy, a missing one
comes back on the next startup, and an edited one is never overwritten. Anything an operator
adds beside them is loaded without touching the jar.

The format is four keys, and everything it does not understand is ignored:

```
# A mirror onto the Nether.
Name=nether
Base=RED
Biome=NETHER_WASTES,CRIMSON_FOREST,WARPED_FOREST,SOUL_SAND_VALLEY,BASALT_DELTAS
Layer=BLACK TRIANGLES_BOTTOM
Layer=ORANGE GRADIENT_UP
Layer=BLACK BORDER
```

| Key | What it is |
| --- | --- |
| `Name` | What `mirror stamp` calls it. Defaults to the file name. |
| `Base` | The banner's own colour, one of the sixteen `DyeColor` names. Required. |
| `Biome` | Biomes this preset answers for, comma-separated. May repeat. Optional. |
| `Layer` | `COLOUR PATTERN`, laid on in order. May repeat. Optional. |
| `Sheltered` | `true` if this kind of place is enclosed anyway, so the indoor look must not replace it. Optional, default false. |

**Leniency is the design, not an oversight.** A line it cannot read is skipped, a preset with
no layers still dyes the banner, and a file with no `Base` is skipped with a warning rather
than failing the folder. These files get hand-edited on live servers, and one operator's typo
costing them every preset is a failure this project has already had once, in the shape files.

Three layers is the working budget. A banner shows six patterns before clients start dropping
the extras, and three of those six are reserved for the sampled squares. A preset with more is
cut from the end rather than refused.

### What ships

Seventeen files, in two groups, and the difference between them is the `Biome` line.

**Twelve places.** `nether`, `end`, `ocean`, `forest`, `sparse_jungle`, `desert`, `frozen`,
`cavern`, `mountain`, `pale_garden`, `overworld` and `indoors`. Between them they name every
biome in the game bar none, so a mirror pointed anywhere gets a look chosen for where it goes
rather than the generic one. `overworld` is still the fallback, and still earns it: a data
pack's own biome names nothing, and neither does one added in a version newer than these files.

`pale_garden` names a biome that exists only from 1.21.4 on. On an older server nothing ever
matches it, which is the same non-event as any preset naming a biome the server has not heard
of — the file loads, it just never wins.

**Five looks.** `plain`, `hub`, `warning`, `private` and `arcane` name no biome at all, so
nothing picks them automatically and `mirror stamp <name> <look>` is the only way to get one.
They are for what an operator wants said about a mirror when it is not where it goes: the middle
of a network, one that only runs one way, one that is not for general use. `plain` is the quiet
one — a colour and a border and no charge — for when the automatic look is wrong and the build
would rather the banner said nothing.

None of the five carry any behaviour. `private` is a bar painted across a banner and not a
permission; whether anybody may use that mirror is a question for the permission nodes, and a
mirror wearing `warning` is exactly as dangerous as it was before it was stamped.

### Which patterns are safe

Only the **34 pattern names present on every supported version** are used in the shipped files.
`PatternType` gained two names at 1.20.6 and lost seven along the way, so a preset copied from
a 1.21 server can name something a 1.20 server has never heard of. That layer is skipped with
a log line and the rest of the banner is stamped — the same way an unrecognised sound name is
skipped rather than silencing the plugin.

Lenient is right for an operator's own file and wrong for one of ours, because the failure is
so quiet: the banner stamps correctly on the version it was written on and comes out missing a
layer on the other half of the range, and nothing says so loudly enough to connect the two. So
the shipped files are held to the intersection by a test rather than by care — which is also
the reason a design lifted straight out of a banner gallery is not safe to ship. Those galleries
publish in Mojang's own pattern ids, on whatever version the site is running, and the seven
renamed names are exactly the ones a transcription gets wrong.

## Two version traps, both real

Worth writing down, because both compile cleanly and fail only on a server nobody tested on.

**`PatternType` changes kind at 1.21.** It is an enum through 1.20.6 and an interface from 1.21
on. `PatternType.valueOf(name)` compiled against this plugin's 1.20.4 target emits a
class-method reference that the JVM refuses against an interface, so it throws
`IncompatibleClassChangeError` on every server from 1.21 up. `Registry.BANNER_PATTERN` has the
opposite problem: it does not exist on 1.20. Reflection is the one route that works across the
whole range, and it is paid once per pattern name rather than once per stamp.

**`Biome` changes kind at 1.21.4**, the same way. Reading a biome's name through `name()`,
`getKey()` or even `toString()` is an `invokevirtual` that breaks for the same reason. The route
that survives is `Keyed`, which has been an interface the whole time — its key is the lower-case
of the old enum name, which is exactly what the preset files are written in.

A third, in the same family: **`Material.isAir()` stopped being a switch at 1.20.6** and now
goes through the live block registry. Harmless here, but it is the same mechanism that once made
`Material.isBlock()` throw when this plugin called it too early in startup, so the sampler
compares names instead — a few hundred times per stamp, and no registry needed to answer.

## Which banner you are looking at

`set` and `link` both have to turn "the banner in front of me" into a block, and one ray cast is
not enough to do it.

`getTargetBlockExact` traces against block shapes. A freestanding banner is a thin post, so from
close up the ray can pass it by. Against a wall that goes unnoticed — the wall behind is hit, the
command says "that is a stone", and the player aims again. On a post in the open there is nothing
behind it, the ray hits nothing, and the refusal reads "look at a banner within six blocks" to
somebody doing exactly that.

So the aimed-at block is tried first, and `getLineOfSight` is the fallback: it steps through the
blocks a ray crosses rather than their shapes, which is what makes a thin one findable. Order
matters — in a corridor of banners the one being pointed at wins over the nearest one crossed.

## Arriving in the banner, and the bounce that cost

Arrival is the destination banner's own block, for the reason `MirrorArrival` gives: a banner is
passable, and it is the one spot in the room a builder deliberately left clear. The cost of that
choice only showed up on a linked pair.

A player lands inside or directly under the far banner, with it filling their view. A right-click
still being delivered when they get there — a held button, or the client resolving the
interaction again at the new position — lands on that banner and fires it. With both ends bound
to each other, that is a round trip in under a second, and what the player sees is a mirror that
returned them to where they started.

So `MirrorSettle` shuts mirrors for two seconds for the player one has just carried. Every
mirror, not only the banner they arrived at: the same click can be re-resolved against whatever
is now in front of them, which on a corridor of banners need not be the one they came out of. It
is armed on an accepted teleport only — a refused trip must not also cost a wait — and the
explanation is said once per arrival rather than once per repeat.

Moving the arrival back out in front of the banner would also have stopped it, and would have
brought back every reason it stopped being the block in front: one block of clearance the
builder did not choose is one block that can be a wall, a drop, a fence, or the far side of a
doorway.

## When another plugin refuses the trip

A cancelled `PlayerTeleportEvent` leaves the player exactly where they were, and on a mirror
that is the banner they just clicked. Silently, it reads as a mirror that opens onto itself —
which is how it was first reported, on a pair whose two ends `mirror list` showed bound
correctly to each other.

So the boolean `Player.teleport` returns is checked, and a refusal names the world and the two
kinds of plugin that usually do this: world access (Multiverse intercepts other plugins'
teleports by default and applies `enforce-access` — spelled `enforceaccess` before Multiverse 5
— wanting `multiverse.access.<world>`) and land
claims. Nothing here tries to overrule the cancel. The mechanic is a banner somebody clicks, not
a permission system, and a plugin whose whole job is deciding who may enter a world should win
that argument — the bug was never that it won, only that nobody said so.

## What was considered and not done

**A real window** — a map in an item frame, rendered from the far side. This is the only thing
that would genuinely be a window, and it is a different feature: a different block, a render
budget per viewer, and a question about how often it refreshes. Worth its own issue, not worth
bolting onto a banner.

**Sampling the exact blocks in front of the banner.** Tried on paper and dropped. A mirror's
destination is a point, not a facing, so "in front of" has no meaning there — and a 13×7×13 box
around the arrival point is what a player standing there would actually see anyway.

**Colour-averaging into a gradient.** Two colours blended are muddier than two colours side by
side, and the whole point is to be readable at a glance down a corridor.
