---
hide:
  - toc
---

# Gate designer

Draw your own gate. Start from any shape the plugin ships, or from a blank one, change it cell by
cell, watch it take shape in 3D, and download the file to put on your server. Already have a `.shape`
file? Upload it here and keep editing.

A **shape** is the plan for a gate: a stack of layers, each a grid of cells, that says where the
frame goes, where the chevrons are and in what order they light, where the opening is, and where
the button and the entry point sit. Everything stays in your browser. Nothing is uploaded, there is
no account, and your draft is saved only on this device so a reload does not lose it.

<style>
/* The editor and the 3D view sit side by side, which wants more than the usual page width. */
.md-grid { max-width: 90rem; }
</style>

<div id="designer-root"><noscript>The designer needs JavaScript. The shape format is described in the <a href="../GATES.html">gate internals</a> page, and every shipped shape is in the <code>shapes/gate/</code> folder of the plugin.</noscript></div>

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
<script type="module" src="../javascripts/designer.js"></script>

## What the cells mean

Each cell is one block of the gate. Pick a kind, then click cells to paint them. Press **I**, **S**,
**P** or **C** over a cell to paint it from the keyboard.

| Kind | Written | What it is |
|------|---------|------------|
| **I** Ignored | `[I]` | Nothing is built here. Most of a layer is this. |
| **S** Stargate material | `[S]` | A frame block, built from the gate's material (obsidian for a Standard gate). |
| **P** Portal | `[P]` | Air that fills with the portal when the gate opens. |
| **C** Chevron material | `[C]` | A frame block built from the chevron material, so you can see it before it lights. |

On top of a kind, a cell can carry extras. Tick them in the panel under the grid:

| Extra | What it marks |
|-------|---------------|
| `:N` | The name sign. |
| `:EP` | Where players arrive. Every gate needs one. |
| `:EM` | Where minecarts arrive. |
| `:A` | The block the activation switch is on. Every gate needs one. |
| `:D` | The block a dial sign hangs from, for gates you dial by sign. |
| `:IA` | The block the iris switch is on. |
| `:L` and a number | A chevron that lights, in that order. |
| `:W` and a number | A woosh cell: the wave that pushes out when the gate opens, in that order. A portal cell with `:W` stays filled. |
| `[RD]` `[RS]` `[RA]` | Redstone blocks: one that starts the gate, one that cycles the sign, and one that shows the gate is active. |

Layer 1 is the far side of the gate. Higher layers come towards you, and the activation switch
faces the highest one.

## Putting it on your server

1. Press **Download .shape file**. It is named after the shape's name.
2. Put the file in `plugins/WormholeXTreme/shapes/gate/` on your server.
3. Run `/wormhole gate shapes validate <name>` to check it, then `/wormhole gate shapes reload <name>`
   to load it without a restart, or just restart the server. Reload refuses a file that `validate`
   reports a problem with, and a restart is more forgiving, so fix what the page lists first.
4. Build it with `/wormhole gate build <name>`.

The checks under the grid run as you edit and follow the rules the plugin uses when it reads a shape, but
**the in-game `/wormhole gate shapes validate <name>` is the authority**: run it on your file. You can
download a shape that has errors; the page tells you so, and the plugin may refuse it or build a
broken gate. A file with no `:EP` cannot be opened here again, because the plugin refuses it too.

Uploads must be `.shape` or `.txt` text files, up to 64 KB, and this page edits grids up to
64 by 64 with up to 64 layers. For how a gate is built from a shape in the world, see
[building gates](../guide/GATES.md#shapes); to look at the shipped shapes without editing,
there is [Gate shapes](gates.md).
