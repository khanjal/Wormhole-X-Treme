# Capturing gates, rings and beams

The design documents describe animations at length -- four rings travelling a block apart and
settling half a block apart, a beam envelope that gathers before it opens, chevrons lighting in
sequence. Prose is a poor medium for any of it. A three-second loop settles in one glance what
takes three paragraphs to say.

This document is how to produce those loops so they are consistent with each other, small enough
to live in the repository, and cut to the right length. The lengths are not guesses: every
animation in the plugin is driven by a tick constant, so the correct clip length is arithmetic.

## Format: APNG, with GIF as the fallback

In a Markdown file in this repository, only `![](...)` images animate. A `<video>` tag is
stripped by GitHub's sanitiser. Video files do play when uploaded through the GitHub web
interface -- an issue, a pull request, a release -- so that is a good home for long or
high-quality captures, but it cannot be relied on for a document in the repository.

**Prefer APNG.** It is 24-bit, so the event horizon's gradient and the beam column's falloff do
not posterise the way they do in GIF's 256-colour palette, and for this kind of footage it is
frequently the smaller of the two. GitHub renders it. Give it a `.png` extension and it behaves
like any other image.

Fall back to GIF only if something in the toolchain refuses APNG.

## Weight budget

This repository's entire history is about **5.6 MiB packed**. One careless five-megabyte GIF
roughly doubles that, permanently -- git keeps every blob it has ever seen, and everyone who
clones pays for it forever.

So: **nothing committed here should exceed about 800 KB**, which is comfortable for a
three-second clip at 640px and 20fps. If a capture will not fit, it is too long, too large, or
the camera moved.

For full-quality or long-form captures, attach the MP4 to a GitHub release instead. Those live on
GitHub's CDN and cost the repository nothing.

## The shot list

Every duration below is derived from the plugin's own constants. Where a shot is longer than
about five seconds, it is listed already split -- a single loop covering a whole ring cycle would
be too heavy and too slow to read.

| Shot | Length | Where the number comes from | File |
|---|---|---|---|
| A built gate | still | -- | `gate-anatomy.png` |
| Dial and kawoosh | see below | `WOOSH_TICKS = 3`, `LIGHT_TICKS = 2` per step in the `.shape` file | `gate-dial.png` |
| Iris turning somebody back | ~2s | -- | `gate-iris.png` |
| Ring countdown and deploy | 4.5s | `RING_COUNTDOWN_TICKS` 60, deploy at `RING_DEPLOY_TICKS` 2 a frame | `ring-deploy.png` |
| Flash, hold and retract | 3.5s | flash `RING_FLASH_TICKS` 3 x 4 rings, `RING_HOLD_TICKS` 20, `RING_LIGHTS_LINGER_TICKS` 20 | `ring-flash.png` |
| A whole beam cycle | 2.9s | envelop 12 + rise 18 + descend 20 + fade 8 = 58 ticks | `beam-cycle.png` |

Twenty ticks is one second.

**The beam is the easy one.** Its entire cycle -- envelop, rise, descend, fade -- runs 58 ticks,
so the whole thing fits one short loop with nothing cut. Record it in third person (F5) or the
traveller, who is the subject, is not in frame.

**The ring cycle is the awkward one.** End to end it is around nine and a half seconds, most of
which is a 60-tick countdown where very little moves. Cut it in two and trim most of the
countdown: keep just enough to establish that the pad lit and a wait began.

**The gate is shape-dependent.** Both animation steps are per-frame delays rather than totals,
and the number of frames comes from the shape's woosh depth and light layers, so a `Grand` gate
runs visibly longer than an `Even` one. Record generously, trim to the settle.

## Setting up so takes match

Lighting that shifts between takes is the fastest way to end up with a set of clips that plainly
were not shot together.

```
/time set noon
/gamerule doDaylightCycle false
/weather clear
```

Then, in the client: F1 to hide the HUD, particles on All, brightness up, and a modest render
distance so the background is quiet.

**Do not move the camera.** This matters twice over. It makes the animation the only moving thing
in frame, which is what the reader should be looking at; and because most of the frame is then
identical between frames, the encoded file is a fraction of the size. A slow pan can be the
difference between 400 KB and four megabytes.

## Recording and converting

Record to MP4 with OBS at 30 or 60fps in a small window -- 960x540 is plenty. Never record
straight to GIF.

ffmpeg does the conversion. It is not installed by default:

```
winget install Gyan.FFmpeg
```

APNG, trimming to the exact cycle with `-ss` (start) and `-t` (duration):

```
ffmpeg -ss 00:00:04 -t 2.9 -i beam.mp4 -vf "fps=20,scale=640:-1:flags=lanczos" -plays 0 -f apng docs/images/beam-cycle.png
```

GIF, which needs a two-pass palette or it looks like 1998:

```
ffmpeg -ss 00:00:04 -t 2.9 -i beam.mp4 -vf "fps=20,scale=640:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=128[p];[b][p]paletteuse=dither=bayer:bayer_scale=3" -loop 0 docs/images/beam-cycle.gif
```

Drop `fps` to 15 or `scale` to 480 if a clip comes out over budget. Losing frames is much less
noticeable than losing colours.

## One caution about looping

An animated image on a page autoplays, loops forever, and gives the reader no way to pause it.
The transport flash runs three ticks a ring through a four-ring stack -- a fast, bright, repeating
flicker, and an infinite strobe on a documentation page is genuinely unpleasant for some readers.

Keep loops short. For the flash specifically, consider a still frame that links through to the
animation rather than embedding it to run forever.

## The placeholders

Every slot listed above currently holds a slate: `docs/images/capture-*.svg`, a dark tile naming
the shot, its length and the filename that should replace it. They are deliberately plain. A
placeholder that looks finished is worse than no placeholder, because a reader takes it for the
real thing -- which is exactly the trap `gate-placeholder.svg` fell into, and the reason the
project logo was not reused here despite being the obvious thing to hand.

To replace one:

1. Capture and convert, writing to the target filename in the table above.
2. Update the `![](...)` reference in the document from `capture-<id>.svg` to the new file.
3. Delete the slate.
4. Check the committed file is under 800 KB.

The slates are a few kilobytes each, so leaving some in place indefinitely costs nothing.

## Where the slots are

| Document | Slots |
|---|---|
| [guide/README.md](guide/README.md) | A built gate, dial and kawoosh, ring countdown and deploy |
| [GATES.md](GATES.md) | Dial and kawoosh (Animation), iris (The iris) |
| [RINGS.md](RINGS.md) | Countdown and deploy, flash and retract (Animation, The transport flash) |
| [BEAMS.md](BEAMS.md) | A whole beam cycle (The sequence) |

`gate-placeholder.png` (a single transparent pixel) and `gate-placeholder.svg` (a grey circle
labelled "Gate Placeholder") were the older, vaguer version of this same idea. The guide's slots
replaced the only reference to them, so they were deleted rather than left orphaned in the
directory.
