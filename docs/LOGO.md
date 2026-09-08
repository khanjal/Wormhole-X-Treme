# Logo

The mark at the top of the README is a **reference, not a finished identity**. It was drawn by
hand in SVG so that a real graphic artist has something concrete to react to rather than a
paragraph of description. This file exists so the *meaning* survives a redraw -- an artist
should feel free to throw away every curve in it, as long as what comes back still says the
same three things.

![Logo anatomy](images/logo-anatomy.svg)

## What the mark has to say

The plugin is three subsystems, and the mark carries one visual element for each. That mapping
is the part worth keeping.

| Element | Subsystem | Why it is in there |
|---|---|---|
| Chevron ring | **Gates** | The oldest and largest half of the plugin. A ring with chevrons reads as "stargate" instantly, which is the whole job of the mark. |
| Column of light | **Beams** | `/wormhole beam` sends a player up a column of light. See [BEAMS.md](BEAMS.md). |
| Ascending ellipses | **Rings** | Transport rings rise around a traveller and shrink as they climb -- the envelope motion described in [BEAMS.md](BEAMS.md) and [RINGS.md](RINGS.md). |

There is no mirror, no portal-pair, no hourglass. Earlier discussion floated "mirrors" as a
fourth subsystem; it is not one. `mirror` appears in the design documents only as ordinary prose
about one thing mirroring another.

## The parts, as currently drawn

Geometry is given in the mark's own 512x512 coordinate space, centre `(256, 256)`. These are
the numbers an artist would need to match the existing proportions, or to know what they are
departing from.

**Backdrop disc** -- `r=240`, radial gradient `#141d2b` to `#070b12`, hairline `#2b3746` rim.
The mark carries its own dark ground instead of a transparent background, so one file reads
correctly on GitHub in both light and dark themes. That is a practical decision, not an
aesthetic one, and an artist may solve it differently (two files, or a mark that works
transparent).

**Gate ring band** -- circle `r=190` stroked at width 52, so the band spans `r=164` to `r=216`.
Brushed-metal gradient, light at the top left.

**Glyph ticks** -- 36 dashes on a circle at `r=205`, `stroke-dasharray="5 30.78"`. They stand in
for the glyph track without drawing actual glyphs. Deliberately faint; they are texture, not
content.

**Chevrons** -- nine, at 40 degree intervals starting at top centre. Each spans `r=156` at the
inner tip out to `r=220`, so they sit on the band and break slightly into the horizon. Amber
gradient `#ffdf9c` to `#c9741a`.

**Event horizon** -- `r=164`, filling the ring interior completely. Radial cyan `#a9f4ff` to
`#0a4a70`, off-centre toward the top. Four concentric ripples at 15% white.

**Beam column** -- a quadrilateral from `(234,92)` and `(278,92)` at the top down to `(216,400)`
and `(296,400)`, widening as it falls, fading to nothing before it reaches the bottom of the
horizon. Clipped to the horizon circle.

**Transport rings** -- three ellipses on the centre line at `cy` 342, 282 and 222, with `rx`
64, 52 and 41. Each one higher is smaller and more transparent, which is what makes the stack
read as movement rather than as three static hoops.

## Palette

| Swatch | Hex | Used for |
|---|---|---|
| Chevron amber | `#f3a52c` | Chevrons, and the "X-TREME" half of the wordmark |
| Ring light | `#ffe3ac` | Transport rings |
| Horizon cyan | `#37b0d8` | Event horizon mid-tone |
| Horizon deep | `#0a4a70` | Event horizon edge |
| Band metal | `#7d8895` | Gate ring mid-tone |
| Space | `#101826` | Backdrop, banner ground |

Amber against cyan is the only strong colour contrast in the mark, and it is doing real work:
it is what separates the gate hardware from the energy inside it at small sizes.

## Constraints

These come from where the mark actually gets used, so they hold regardless of how it is redrawn.

- **It must survive 32px.** The README badge row, a favicon, and a Spigot or Modrinth listing
  icon are all small. The current drawing holds together to about 32px and turns into a blue dot
  with a gold fringe at 16px, which is honestly its weakest point and a good thing for an artist
  to beat.
- **It must work on light and dark.** GitHub renders READMEs in both.
- **Do not reproduce the actual Stargate prop.** The franchise ring, its glyph set and its
  chevron design are somebody else's intellectual property. The mark should read as
  *stargate-like* from generic parts -- a ring, chevrons, a pool -- and must not copy the
  real design or its 39 glyphs. The tick marks in the current drawing are deliberately abstract
  for this reason.
- **The plugin ships no images.** The logo is documentation and listing art only; nothing in
  `src/` loads it, so file size and format are unconstrained by the build.

## What is open

Everything below is a decision made to get *something* on the page, not a position worth
defending:

- Chevron count, shape and whether they protrude past the band. Nine is the franchise number;
  it is not load-bearing here.
- Whether the beam and the rings both belong in the mark, or whether one of them should live
  only in a larger banner version. Three ideas in one small circle is a lot.
- The wordmark. It is currently set in a system font stack (`Segoe UI`, falling back to Arial),
  which means it renders differently on different machines. A real typeface, or lettering drawn
  as paths, would fix that.
- Whether a flat single-colour variant is needed for places that cannot take a gradient.

## What would be useful back

- Master vector, in whatever tool the artist works in, plus a plain SVG export.
- Square mark as PNG at 512, 256, 128, 64 and 32.
- Banner or lockup, vector and PNG.
- A one-colour version, for a stamp or an embroidered patch or a monochrome context.
- A favicon-sized drawing that is *designed* at that size rather than scaled down to it.

## Current files

| File | What it is |
|---|---|
| [`images/logo.svg`](images/logo.svg) | The square mark, 512x512. |
| [`images/logo-banner.svg`](images/logo-banner.svg) | Mark plus wordmark and one-line description, 1040x340. Heads the README. |
| [`images/logo-anatomy.svg`](images/logo-anatomy.svg) | The build-up sheet at the top of this file. |

There are no PNG exports. Nothing on the machine these were drawn on could rasterise SVG, and
GitHub renders SVG in Markdown directly, so none were needed yet. A store listing will need
them.
