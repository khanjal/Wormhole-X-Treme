# Gate shapes

Look at every gate shape the plugin ships, in 3D, before you build one. Pick a shape, then drag to
orbit and scroll to zoom. **Dial** lights the chevrons one at a time in the order the shape file
names, and **Open** pushes the woosh out and fills the portal; **Idle** and **Reset** put it back.

The viewer reads the same `.shape` files the plugin loads, so what you see here is what the plugin
builds. See [building gates](../guide/GATES.md#shapes) for how to put one up in the world, and the
[gate internals](../GATES.md) for how the format is read.

<div id="gate-viewer"><noscript>The 3D viewer needs JavaScript. Each shape is also drawn in the <a href="../GATES.html#the-shapes-that-ship">gate internals</a> page.</noscript></div>

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
<script type="module" src="../javascripts/gate-viewer.js"></script>

## Reading the picture

- A shape is a stack of layers. **Layer 1 is the far side** when the viewer opens: you start where a player stands at the
  dialling switch, looking at the gate, with the file's first column on your left.
- The **chevrons** are the cells marked `:L`; they light in the order of the number after it.
- The **woosh** cells are marked `:W`; they appear in order, then retreat in reverse. A portal cell
  that is also a woosh cell (`[P:W]`) stays filled until the gate shuts.
- `Horizontal` lies flat, so its ring is laid out in plan and the woosh rises from it.
- Material colours here are only a guide: a real gate takes its look from its
  [material group](../guide/GATES.md).
