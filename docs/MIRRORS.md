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

That threshold is a judgement and nothing more. At 55% a cave reads as indoors, which is
right, and a forest does not, which is also right.

### What it is not

The sample is taken **once**, when the mirror is stamped, and never again. Two reasons, and the
second is the stronger one:

- Re-reading the far side on every click would mean loading a distant chunk on a click.
- A banner that changed on its own would be worse to build with. A look an operator chose
  should stay chosen.

Rebuild the far side and the mirror still shows the old place until somebody stamps it again.
That is the same bargain `mirror link` already makes: a snapshot, not a subscription.

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

**Leniency is the design, not an oversight.** A line it cannot read is skipped, a preset with
no layers still dyes the banner, and a file with no `Base` is skipped with a warning rather
than failing the folder. These files get hand-edited on live servers, and one operator's typo
costing them every preset is a failure this project has already had once, in the shape files.

Three layers is the working budget. A banner shows six patterns before clients start dropping
the extras, and three of those six are reserved for the sampled squares. A preset with more is
cut from the end rather than refused.

### Which patterns are safe

Only the **34 pattern names present on every supported version** are used in the shipped files.
`PatternType` gained two names at 1.20.6 and lost seven along the way, so a preset copied from
a 1.21 server can name something a 1.20 server has never heard of. That layer is skipped with
a log line and the rest of the banner is stamped — the same way an unrecognised sound name is
skipped rather than silencing the plugin.

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
