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

## Room for a big gate

A Grand or Massive gate wants more than a page column. Press **Expand** and the designer fills your
whole window: the grid takes most of the room, and the 3D view, the extras, the settings and the
checks sit in a side panel (below the grid on a tall window) that you can hide, or drag wider with the
bar between them, or move with the arrow keys once it has focus. **Escape** puts the page back. The
**+** and **−** buttons, the plus and minus keys over the grid, and Ctrl and scroll over the grid
change the size of the cells, and **Fit to window** picks the biggest size that shows the whole layer
(or the smallest that is still easy to read, with the grid scrolling). Even the smallest cells show their
letter, and an underlined letter marks a cell with extras. A woosh cell (one with `:W`) is striped even when it is
Ignored, stronger the later its step (in up to five bands on a long woosh), with the step number in its corner once the cells are big enough;
the **Woosh cells** switch in the bar above the 3D view (on by default) shows or hides them there as crossed wireframe blocks, and **Open** plays them in order.
A `:W#0` cell is never played, and the checks say so.
The legend sits beside each 3D view, or behind its **Legend** button on a narrow screen.

## Drawing in one half

Most gates are the same on both sides, so you can draw one side and let the designer do the other. Pick
**Symmetry** above the grid: **Left-right** mirrors across a dashed line down the middle, **Top-bottom**
across one along it, and **Both** draws in a single quarter and mirrors it three ways. The dotted edge
marks the side to draw in; in a short window the Symmetry button in the toolbar opens the controls over
the grid.

What is mirrored depends on how you change a cell. **Painting** (I, S, P or C, and clearing) mirrors the
kind only, onto the cell across the line in the same layer; a mirrored cell that turns Ignored loses its
light number, as the plugin would read a light number on an Ignored cell as a light position. It does not
copy an `[S:C]` either-material flag. The **light and woosh order boxes** in the cell panel mirror the
order number, so the pair light together, and the status says how many cells that reached, how many it
renumbered, and whether it made new problems. The middle row or column of an odd-sized grid mirrors onto
itself. **Mirror now** copies the kinds of the source half or quarter of this layer over the rest in
one step, including an `[S:C]` flag (**Mirror all layers** does every layer, each on its own), says how
many cells it changed, and **Undo** takes it back whole. Layers are never mirrored into each other.

Mirror now leaves the light and woosh numbers alone by default, so each cell keeps its own: a gate's
light numbers often run round the ring, and copying them would leave gaps that make `reload` refuse the
file. Tick **Copy light and woosh orders** to copy them too; if that makes new problems the status says how
many and the list shows them.

The extras that a gate has only one of (`:N`, `:EP`, `:EM`, `:A`, `:D`, `:IA`, `[RD]`, `[RS]` and
`[RA]`) are never mirrored: ticking one marks only the cell you selected. A cell that holds one is
never changed by a mirror either, whether by painting or by Mirror now; the status says how many cells
were skipped.

## Seeing what is wrong

A button beside the grid always shows the checks' result, such as "2 errors, 1 warning". Press it for the
list, which opens over the foot of the grid. Press an entry, or use the Up and Down arrows, to select that
problem's cell: the list keeps the focus and the grid scrolls the cell clear of it, so you can step on
through the problems. Opened from the keyboard (Enter or Space on the button), the list takes the focus and
hands it back to the button when it closes; opened with the pointer, **Escape** puts you on the selected
cell. Cells with a problem are outlined in the grid itself: a double border for an error (a thick one with
a corner mark on small cells) and a dashed one for a warning, and the selected cell has a second ring
inside the first. A light number on an Ignored cell is a warning, since the plugin would record it as a
light position.

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
