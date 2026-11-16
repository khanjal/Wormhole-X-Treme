# Iris styles

Watch an iris close and open on the real opening of a shipped gate. Pick a shape and a style, then
press **Play**: the iris sweeps shut from the rim, holds, and draws back from the middle. The pink
cells are the step that has just arrived. **Step** and the slider go a tick at a time, and a table
under the picture numbers every cell by the step that covers it.

The step order is the one the plugin works out: this page runs the same arithmetic as
`IrisSweep` over the cells of the shape file. See [the iris](../guide/GATES.md#the-iris) for what
the setting does on a server.

<div id="iris-viewer"><noscript>The 3D viewer needs JavaScript. The styles are described, and drawn as pictures, in <a href="../guide/GATES.html#how-it-arrives">the gate guide</a>.</noscript></div>

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
<script type="module" src="../javascripts/iris-viewer.js"></script>

## What the styles do

| Style | How it crosses |
|---|---|
| `sweep` *(default)* | Rings, in from the rim and out from the middle |
| `spiral` | A wedge turning round the middle and working outwards |
| `rows` | Rows, in from the top and bottom at once |
| `columns` | Columns, in from both sides at once |
| `instant` | No animation; the iris is simply there |

- A step comes every 2 ticks and a whole crossing takes at most 20, so **at most ten steps**. A
  wider opening has more rings than that, so neighbouring rings are merged into bands that arrive
  together; of the shipped shapes only `Massive` and `Grand` need it.
- `Minimal`'s opening is one ring, so it is **one step** in every style.
- Each style takes as many steps as it has pieces to cross, so `rows` and `columns` are quicker than
  `sweep` on a gate with fewer rows than rings. The `spiral` is cut into as many steps as the sweep
  makes.
- Closing goes rim first. Opening is the same steps run backwards, from the middle out.

## What this page leaves out

- Only upright gates are shown. `Horizontal` lies flat, and its opening is several layers deep.
- The `spiral` turns one way here, as a player at the activation switch sees it. In the world the
  direction follows which way the gate faces.
- A real iris is drawn for each viewer, with the wormhole behind it on a glass iris; see
  [how it arrives](../guide/GATES.md#how-it-arrives). This page draws the iris alone.
- The four iris materials are the ones the shipped [material groups](../guide/GATES.md#material-groups)
  use. The colours are a guide, not the block textures.
