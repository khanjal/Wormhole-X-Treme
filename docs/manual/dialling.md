# Dialling and chevrons

Play a dial on a shipped gate: the chevrons lock one at a time in the order of the number after `:L`,
the gate holds for two seconds, then the woosh pushes out in `:W` order and the wormhole forms. Pick
a ring light to see what the inner ring does before each chevron locks. **Step** and the slider go a
tick at a time, and a table under the picture gives the tick each event happens on.

The timing and the ring light come from the plugin's own rules: this page runs the same arithmetic as
`DialSpin` over the cells of the shape file. See [dialling](../guide/GATES.md#dialling) for the
settings, and [gate shapes](gates.md) to look at the shapes themselves.

<div id="dial-viewer"><noscript>The 3D viewer needs JavaScript. Each ring light is described, and drawn as a picture, in <a href="../guide/GATES.html#dialling">the gate guide</a>.</noscript></div>

<script type="importmap">
{
  "imports": {
    "three": "https://cdn.jsdelivr.net/npm/three@0.170.0/build/three.module.js",
    "three/addons/": "https://cdn.jsdelivr.net/npm/three@0.170.0/examples/jsm/"
  },
  "integrity": {
    "https://cdn.jsdelivr.net/npm/three@0.170.0/build/three.module.js": "sha384-GY5FqjttLCFRt/McQbyaVdCk2O1IQtOeX8Py6NfD89BIAsIyJFRl4UgSXrk2vXAk",
    "https://cdn.jsdelivr.net/npm/three@0.170.0/examples/jsm/controls/OrbitControls.js": "sha384-aJoe4qqS/DgF2jh9njAuvA6QIveJYoCuYOfYjdFY8P3eawzmc9bEQ40jq2TvOyuP"
  }
}
</script>
<script type="module" src="../javascripts/dial-viewer.js"></script>

## The ring lights

`gate-dial-spin` (or a gate's own `spin`, or a [material group](../guide/GATES.md#material-groups)'s
`dial-spin`) picks the pattern. The ring is the front layer of frame, nearest the DHD, and the
light runs round it clockwise from the top chevron.

| Pattern | Before each chevron locks |
|---|---|
| `none` *(default)* | No ring light; a chevron locks every light interval, the first at once |
| `top` | Half the ring to the top chevron, turning the other way each glyph, then half a second resting there |
| `chevron` | From the top anticlockwise to the first chevron, then from each locked chevron to the next, a glyph's width at a time |
| `lap` | A whole turn clockwise, landing on the chevron |
| `fill` | As `pegasus`, lighting the ring behind it as it goes |
| `pegasus` | The light runs half the ring and lands on the chevron, turning the other way each glyph |
| `chase` | A whole turn anticlockwise from chevron 1 back to it, then from each locked chevron to the next |
| `universe` | The whole ring turns about a full turn for each glyph; locked chevrons ride round with it until the top one locks |
| `overshoot` | As `pegasus`, running a little past the chevron and backing onto it |

Every pattern but two takes the chevron's own interval (`LIGHT_TICKS` in the shape) to travel.
`top` adds its half-second rest after each chevron but the last, and `universe` turns at a steady
pace of its own, so it is the slow one on a small gate.

## Seven chevrons or eight

A dial within the world locks seven chevrons, the seventh being the top one. A dial to another
world locks an eighth, `:L#8` at the bottom of the ring, after the top. Choose **to another world**
to play it. `Minimal` has a single chevron, so it locks one either way.

## Nothing here is random

Every pattern is worked out from the shape file alone, so the same shape and pattern always play
the same dial. What the page leaves out:

- **Only the gate dialling out turns.** The gate being dialled lights its chevrons in order without
  the ring light, waiting as long as the other gate's turn takes.
- **A sign dial opens at once**, with every chevron lit together and no ring light.
- **Sounds** are not played.
- **The woosh** is shown with the portal filling when it ends. A `Horizontal` gate is drawn lying
  flat, its far edge being its top.
- **A gate whose ring is not where its shape says** (a damaged gate) skips the ring light in the
  plugin; a shape here never does.
