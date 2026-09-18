# Capturing gates, rings and beams

The design documents describe animations at length -- four rings travelling a block apart and
settling half a block apart, a beam envelope that gathers before it opens, chevrons lighting in
sequence. Prose is a poor medium for any of it. A three-second loop settles in one glance what
takes three paragraphs to say.

This document is how to produce those loops so they are consistent with each other, small enough
to live in the repository, and cut to the right length. The lengths are not guesses: every
animation in the plugin is driven by a tick constant, so the correct clip length is arithmetic.

## Format: animated WebP, with GIF as the fallback

In a Markdown file in this repository, only `![](...)` images animate. A `<video>` tag is
stripped by GitHub's sanitiser. Video files do play when uploaded through the GitHub web
interface -- an issue, a pull request, a release -- so that is a good home for long or
high-quality captures, but it cannot be relied on for a document in the repository. A store
listing has the same constraint: SpigotMC's `[IMG]` takes an image, not a video.

**Prefer animated WebP.** It is 24-bit, like APNG, so the event horizon's gradient and the beam
column's falloff do not posterise the way they do in GIF's 256-colour palette -- and unlike APNG
it compresses between frames properly. GitHub renders it; so does every browser since about 2020.
Measured on the real ring and mirror captures, for the same clip and the same source footage:

| Format | Size | Resolution |
|---|---|---|
| Animated WebP | **255 KB** | 720px |
| GIF, 64-colour palette | 827 KB | 520px |
| APNG | 2876 KB | 480px |

APNG is the largest by a wide margin: ffmpeg's APNG encoder stores whole frames, and
hand-assembling one from a shared palette in Pillow did not close the gap.

Encode with `-c:v libwebp_anim -lossless 0 -q:v 55` (`-q:v` around 55-60 is the sweet spot),
and keep GIF in reserve only if something downstream refuses WebP.

## Weight budget

This repository's entire history is about **5.6 MiB packed**. One careless five-megabyte GIF
roughly doubles that, permanently -- git keeps every blob it has ever seen, and everyone who
clones pays for it forever.

So: **nothing committed here should exceed about 800 KB**, which in WebP is comfortable for a
three-to-four-second clip at 720px and 20fps. If a capture will not fit, it is too long, too
large, or the camera moved.

**A locked camera is worth more than any encoder setting.** Measured on two takes of the same
ring cycle: the first drifted slightly, and cost 1040 KB for 2.7 spliced seconds at 440px. The
second was locked -- frame-to-frame motion of 0.007 against 0.05 -- and gave the full 2.8-second
cycle, uncut, at 520px, for 827 KB. Every format wins when most of the frame is identical to the
one before it, and none of them can rescue a shot where nothing holds still. A five-second clip
with the camera panning throughout came to 3-5 MB as a GIF and 1.8 MB even as H.264; the same
duration locked off is a few hundred KB.

For full-quality or long-form captures, attach the MP4 to a GitHub release instead. Those live on
GitHub's CDN and cost the repository nothing. Note that anything shot to show *parallax* -- a
mirror's view shifting as you move past it -- needs the camera to move by definition, so those
clips are expensive and belong either in a release or trimmed hard.

## The shot list

Every duration below is derived from the plugin's own constants. Where a shot is longer than
about five seconds, it is listed already split -- a single loop covering a whole ring cycle would
be too heavy and too slow to read.

| Shot | Length | Where the number comes from |
|---|---|---|
| A built gate | still | -- |
| Dial and kawoosh | shape-dependent | `WOOSH_TICKS = 3`, `LIGHT_TICKS = 2` per step in the `.shape` file |
| Ring countdown and deploy | 5s countdown, then ~1.5s | `ring-countdown-ticks` (100 by default), then a frame every `ring-deploy-ticks` (2) |
| Flash, hold and retract | ~3.5s | `ring-flash-ticks` 3 x 4 rings, `ring-hold-ticks` 20, `ring-lights-linger-ticks` 20 |
| A whole beam cycle | 2.6s | 52 ticks — the phases overlap, see below |

Twenty ticks is one second.

**The beam is the easy one.** Its entire cycle -- envelop, rise, descend, fade -- runs 52
ticks, so the whole thing fits one short loop with nothing cut. Record it in third person (F5)
or the traveller, who is the subject, is not in frame. Adding the four phase durations gives 58,
but the descend column starts at the *teleport* tick, 12 ticks into an 18-tick rise, so six ticks
run at both ends at once. The strip in [BEAMS.md](BEAMS.md#the-phases-overlap) shows it, and
`BeamGalleryTest` holds this document's 2.6s against `BeamFrame` itself.

**The ring cycle is the awkward one.** End to end it is over ten seconds, most of it a
countdown where very little moves. Cut it in two and trim most of the countdown: keep just
enough to establish that the pad lit and a wait began.

**The gate is shape-dependent.** Both animation steps are per-frame delays rather than totals,
and the number of frames comes from the shape's woosh depth and light layers, so a `Grand` gate
runs visibly longer than a `Standard` one. Record generously, trim to the settle.

## Before recording anything

None of this can be fixed afterwards, and a set of shots that plainly were not taken together is
a reshoot.

**In the world:**

```
/time set noon
/gamerule doDaylightCycle false
/weather clear
/gamerule doMobSpawning false
```

A shifting sun lights every shot differently, and one wandering creeper is a reshoot.

**In the client:**

- **F1**, so no HUD. A hotbar in one shot and not the next ruins a set.
- **One FOV, one render distance, one GUI scale and one resolution** across everything. FOV 70
  unless there is a reason, and a modest render distance so the background is quiet.
- **Particles on All.** The kawoosh, the beam column and the ring flash are particles.
- **Shaders and resource packs all on or all off**, not some.

**Do not move the camera.** This matters twice over. It makes the animation the only moving thing
in frame, which is what the reader should be looking at; and because most of the frame is then
identical between frames, the encoded file is a fraction of the size. A slow pan can be the
difference between 400 KB and four megabytes.

## Stills

Capture at 1920x1080. Gate shapes are compared by silhouette, so shoot them **straight on**, from
far enough back that perspective is not bowing the frame, and crop in afterwards.

Build gates without a dial sign, so there is no sign in frame. Do not break the sign off a built
gate to clear the shot: that leaves a registered gate missing one of its parts
([#54](https://github.com/khanjal/Wormhole-X-Treme/issues/54)), and it refuses to dial.

Keep the camera in the same spot between shapes, and stand in frame for scale. `Minimal` next to
`Massive` means nothing without something to measure them by.

Angle the shot instead where depth is the subject. A kawoosh shot flat on reads as a disc.

## Recording and converting

- **Record at 60fps, not 30.** Minecraft runs at 20 ticks a second and every length above is a
  tick count, so 60 decimates to 20 exactly three to one -- one frame per tick, no judder. 30 to
  20 is three to two, and it shows in a loop.
- **Cap the in-game framerate at 60** rather than unlimited, so the source is steady.
- **Keep takes to 10-15 seconds.** Long files are slow to move around, and there is nothing in
  the extra footage.
- **MP4.** Never record straight to GIF. Steam's background recordings are chunked DASH
  fragments; use its export, not the recording folder.

ffmpeg does the conversion. It is not installed by default:

```
winget install Gyan.FFmpeg
```

Animated WebP, trimming to the exact cycle with `-ss` (start) and `-t` (duration):

```
ffmpeg -ss 00:00:04 -t 2.6 -i beam.mp4 -vf "fps=20,scale=720:-1:flags=lanczos" -c:v libwebp_anim -lossless 0 -q:v 55 -loop 0 docs/images/beams/beam-up.webp
```

GIF, only if something refuses WebP. It needs a two-pass palette or it looks like 1998:

```
ffmpeg -ss 00:00:04 -t 2.6 -i beam.mp4 -vf "fps=20,scale=520:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=64[p];[b][p]paletteuse=dither=bayer:bayer_scale=3" -loop 0 beam-up.gif
```

Raw captures stay out of the repository. Keep them outside the checkout, or in a folder
`.git/info/exclude` names, so a broad `git add` cannot sweep a 60 MB MP4 into history.

Drop `fps` to 15 or `scale` to 480 if a clip comes out over budget. Losing frames is much less
noticeable than losing colours.

## One caution about looping

An animated image on a page autoplays, loops forever, and gives the reader no way to pause it.
The transport flash runs three ticks a ring through a four-ring stack -- a fast, bright, repeating
flicker, and an infinite strobe on a documentation page is genuinely unpleasant for some readers.

Keep loops short. For the flash specifically, consider a still frame that links through to the
animation rather than embedding it to run forever.

## What was shot, and where it landed

| Capture | File | Appears in |
|---|---|---|
| A gate dialling: chevrons, then the kawoosh | `gates/gate-dial.webp` | [README](../README.md), [guide/GATES.md](guide/GATES.md#dialling), [GATES.md](GATES.md#animation) |
| The six shipped shapes, idle and dialled | `gates/gate-shapes.png`, `gates/gate-shapes-active.png` | [guide/GATES.md](guide/GATES.md#shapes), [GATES.md](GATES.md#the-shapes-that-ship) |
| `Horizontal`, idle and dialled | `gates/gate-horizontal.png` | [guide/GATES.md](guide/GATES.md#shapes), [GATES.md](GATES.md#the-shapes-that-ship) |
| The four palettes: open, dialled, iris closed | `gates/standard-palettes*.png` | [guide/GATES.md](guide/GATES.md#material-groups), [GATES.md](GATES.md#palettes-are-separate-from-shapes) |
| A ring pair's whole cycle | `rings/ring-cycle.webp` | [README](../README.md), [guide/RINGS.md](guide/RINGS.md#using-rings), [RINGS.md](RINGS.md#animation) |
| A traveller leaving in a column of light | `beams/beam-up.webp` | [README](../README.md), [guide/BEAMS.md](guide/BEAMS.md), [BEAMS.md](BEAMS.md#the-sequence) |
| A round trip through a mirror | `mirrors/mirror-effects.webp` | [guide/MIRRORS.md](guide/MIRRORS.md#setting-one-up) |
| One mirror showing two rooms | `mirrors/mirror-look.webp` | [README](../README.md), [guide/MIRRORS.md](guide/MIRRORS.md#what-you-see-in-one) |
| A mirror's view shifting as you move past it | `mirrors/mirror-archway.webp` | [guide/MIRRORS.md](guide/MIRRORS.md#what-you-see-in-one) |

**Two shots are deliberately not here.** The *beam arriving* cannot be filmed by the traveller
-- you vanish six steps into a twelve-tick envelope, long before there is time to reach the far
end -- so it needs a second player at the destination. And the *iris turning somebody back* was
dropped rather than shot: the palette sheet already shows the iris closed in all four palettes,
which is what the section is actually about.

**The rings and the beam both needed spectator mode.** A third-person camera is pushed inside a
deploying ring stack, so the shot cannot be framed from outside in survival or creative. In
spectator the camera has no collision, and the countdown is long enough to arm the ring and fly
back out before anything rises.

## The diagrams are not these captures

`docs/images/gates/`, `docs/images/rings/` and `docs/images/beams/` also hold generated drawings
-- gate shapes, ring footprints, deploy filmstrips, the beam's timing. They are schematics of the
geometry, drawn from the plugin's own shape files and constants, and they say what a thing *is*.
A capture says what it *looks like*, which is a different question: flat colour keyed to a block
cannot show the event horizon's gradient, the particle column, or the way the kawoosh reads at
speed. Several sections now carry both, and that is the intent rather than duplication.

The two are also licensed differently, which is worth keeping straight. The diagrams are ours,
drawn from our files. A capture is a screenshot or a recording of Minecraft, which is Mojang's
to permit and which their terms do permit -- while the game's *textures* are not ours to
redistribute, which is precisely why the diagrams are flat colour rather than the real artwork.
