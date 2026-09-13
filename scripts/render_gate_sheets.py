#!/usr/bin/env python3
"""Draws each shipped gate shape, and writes the gallery in docs/GATES.md.

Run from the repository root, with no arguments:

    python scripts/render_gate_sheets.py

It reads every file in src/main/resources/shapes/gate and the material groups in
src/main/resources/config.yml, writes one SVG per shape per state into docs/images/gates,
and rewrites the tables in docs/GATES.md between the gallery markers. Re-run it whenever a
shape or a palette changes -- GateGalleryTest fails until you do, because each image carries
a fingerprint of the file it was drawn from.

Two drawings per shape: the gate standing idle, and the same gate dialled. What changes
between them is the whole point of the pair -- the portal fills, and the chevrons light.

The drawings are flat colour keyed to each material, not the game's textures. Minecraft's
textures are Mojang's, and shipping them here would be redistributing their assets rather
than illustrating ours; a screenshot is the licensed way to show the real thing, which is
what docs/CAPTURES.md is for. Flat colour is enough to tell Grand from Massive at a glance,
which is the whole job of a gallery.
"""
import hashlib
import io
import os
import re

SHAPES = "src/main/resources/shapes/gate"
CONFIG = "src/main/resources/config.yml"
OUT = "docs/images/gates"
DOCUMENT = "docs/GATES.md"
SHAPE_START = "<!-- shapes:start -->"
SHAPE_END = "<!-- shapes:end -->"
PALETTE_START = "<!-- palettes:start -->"
PALETTE_END = "<!-- palettes:end -->"

# One [X] cell, as both shape parsers read it.
CELL = re.compile(r"\[(.+?)\]")

# The KEY=value lines that change what a gate looks like or how it animates. Anything else a
# shape file says is a comment or a setting the drawing cannot show, and is left out of the
# fingerprint so that editing it does not demand a regeneration that changes nothing.
KEYS = (
    "WOOSH_TICKS", "LIGHT_TICKS", "REDSTONE_ACTIVATED", "MATERIAL_GROUPS",
    "STARGATE_MATERIAL", "PORTAL_MATERIAL", "IRIS_MATERIAL", "ACTIVE_MATERIAL",
    "CHEVRON_MATERIAL", "SIGN_MATERIAL",
)

# Roughly what each block reads as from a few metres away. Averages, not textures -- see the
# note at the top of this file for why that is deliberate rather than a shortcut.
BLOCK = {
    "OBSIDIAN": "#15101f", "LAPIS_BLOCK": "#1d47a5", "POLISHED_BLACKSTONE": "#322d37",
    "DEEPSLATE": "#545456", "WATER": "#2f52c8", "STONE": "#7f7f7f",
    "YELLOW_STAINED_GLASS": "#e5e533", "WHITE_STAINED_GLASS": "#e8e8e8",
    "IRON_BLOCK": "#d8d8d8", "GLOWSTONE": "#f9d68f", "SEA_LANTERN": "#b6d8cf",
    "SHROOMLIGHT": "#f2913a", "REDSTONE_LAMP": "#6b3f22", "COPPER_BULB": "#7a4a2c",
    "OAK_WALL_SIGN": "#b8945f", "WARPED_WALL_SIGN": "#2b7a78", "CRIMSON_WALL_SIGN": "#8b3a4a",
    "REDSTONE_WIRE": "#a33030",
}

# A block with an off state and an on state simply switches on when the gate dials, which is
# the look the chevron key exists for. Anything else has no on state and lights as `light`
# instead -- the same rule config.yml states, applied to the picture.
SWITCHES = {"REDSTONE_LAMP": "#f0b86e", "COPPER_BULB": "#f6c17a"}

# Air: the ground the grid is laid on, so the extent a shape occupies still reads. Painted
# once behind everything rather than a cell at a time -- `Massive` is 529 cells and most of
# them are air, which cost more than the rest of the file put together.
#
# Lighter than the frame, which looks backwards for something meant to read as empty, and is
# not. Obsidian is very nearly black: on a dark ground a Standard gate is an invisible ring
# around a visible portal, which is a picture of the wrong thing.
AIR = "#2b3442"

# An em dash, written as an escape so this file stays ASCII the way its sibling does. The
# document it writes into uses them throughout, and a gallery of "--" in a page of dashes reads
# as generated, which it is, but not as a thing anybody proof-read.
DASH = "\u2014"

# What each shape is for. The geometry is in the drawing; this is the sentence the drawing
# cannot say.
PURPOSE = {
    "Minimal": "one block wide " + DASH + " the smallest gate that works",
    "Standard": "the seven-wide ring, and what most servers build",
    "Even": "eight wide, so the opening has no centre column",
    "Large": "ten wide, for a gate meant to be seen across a valley",
    "Grand": "twenty-two wide, and a build in its own right",
    "Massive": "twenty-three wide and fifteen deep " + DASH + " the largest that ships",
    "Horizontal": "lies flat in the floor, and is dropped into rather than walked through",
}

# The markers that name one block rather than collecting many, and what each one is.
MARKS = {
    "N": "name sign", "A": "activation switch", "D": "dial sign", "IA": "iris switch",
    "EP": "where a player arrives", "EM": "where a minecart arrives",
    "RD": "redstone dial", "RS": "redstone sign cycle", "RA": "redstone gate-activated",
}


def read_groups(path):
    """The material groups from config.yml, in the order they are declared.

    The first is the default, which is the palette every drawing here is in. This reads the
    block by indentation rather than with a YAML parser, because the repository has no Python
    dependencies and this is four keys deep in a file we control.
    """
    groups, name = [], None
    inside = False
    for raw in io.open(path, encoding="utf-8").read().replace(chr(13), "").split(chr(10)):
        if raw.startswith("gate-material-groups:"):
            inside = True
            continue
        if not inside:
            continue
        if raw and not raw.startswith(" "):
            break
        stripped = raw.strip()
        if not stripped or stripped.startswith("#"):
            continue
        indent = len(raw) - len(raw.lstrip(" "))
        if indent == 2 and stripped.endswith(":"):
            name = stripped[:-1]
            groups.append((name, {}))
        elif (indent == 4) and (":" in stripped) and groups:
            key, value = stripped.split(":", 1)
            groups[-1][1][key.strip()] = value.strip()
    return groups


def read_shape(path):
    """One shape file, reduced to what a picture of it needs."""
    text = io.open(path, encoding="utf-8").read().replace(chr(13), "")
    name, layers, settings, order = None, {}, {}, []
    current, body = None, []

    for raw in text.split(chr(10)):
        line = raw.strip()
        if line.startswith("Name="):
            name = line[5:].strip()
            body.append("Name=" + name)
        elif line.startswith("Layer#"):
            current = int(line[6:].split("=")[0])
            layers[current] = []
            order.append(current)
            body.append("Layer#%d=" % current)
        elif line.startswith("["):
            if current is None:
                continue
            layers[current].append([c.split(":") for c in CELL.findall(line)])
            body.append(line)
        elif ("=" in line) and not line.startswith("#"):
            key = line.split("=")[0].strip()
            if key in KEYS:
                value = line.split("=", 1)[1].strip().rstrip(";").strip()
                settings[key] = value
                body.append("%s=%s" % (key, value))

    fingerprint = hashlib.sha1(chr(10).join(body).encode("utf-8")).hexdigest()[:10]
    return name, layers, settings, fingerprint


def solid(cell):
    """Whether this cell is anything at all, or just the air a layer is mostly made of."""
    mods = [m.upper() for m in cell]
    return ("S" in mods) or ("P" in mods) or ("C" in mods) or any(m in MARKS for m in mods)


def elevation(layers):
    """The one grid a still picture of this gate should show.

    A gate that lies flat has its whole frame in a single row, spread one cell to a layer, and
    a picture of that row is a picture of nothing. What somebody laying one out needs is the
    plan: that row from each layer, stacked.

    A gate that stands up is flattened along its depth instead, layer 1 nearest -- which is
    what you see walking up to it, and is why the DHD, which every shape puts in its furthest
    layer, does not appear on a thick gate. Flattening rather than taking one layer matters for
    `Grand`, `Large` and `Massive`, whose rings are three layers thick: their frame and
    chevrons are in layer 1 and the portal is in layer 2 behind it, so either layer alone would
    be half a gate.

    Returns the grid, and whether it is a plan rather than an elevation.
    """
    holding = [n for (n, rows) in sorted(layers.items())
               if any("P" in cell for row in rows for cell in row)]
    rows_with_portal = {i for n in holding for (i, row) in enumerate(layers[n])
                        if any("P" in cell for cell in row)}

    if (len(holding) > 1) and (len(rows_with_portal) == 1):
        floor = rows_with_portal.pop()
        return [layers[n][floor] for n in sorted(layers)], True

    order = sorted(layers)
    height = len(layers[order[0]])
    width = len(layers[order[0]][0])
    grid = []
    for i in range(height):
        row = []
        for j in range(width):
            nearest = next((layers[n][i][j] for n in order if solid(layers[n][i][j])), ["I"])
            row.append(nearest)
        grid.append(row)
    return grid, False


def wave(cell, letter):
    """The #number on an :L or :W marker, or None if the cell carries neither."""
    for mod in cell:
        if mod.upper().startswith(letter) and (mod[1:2] in ("", "#")):
            return int(mod.split("#")[1]) if "#" in mod else 1
    return None


def fill(cell, group, dialled):
    """What colour one cell is, and the marker letter that belongs on top of it.

    Precedence follows the plugin's own: a cell is frame, chevron or portal first, and the
    light and woosh markers then say what happens to it when the gate dials.
    """
    mods = [m.upper() for m in cell]
    mark = next((m for m in mods if m in MARKS), None)
    lit = wave(cell, "L") is not None

    chevron = group.get("chevron")
    if "P" in mods:
        # The portal is drawn, not built: until the gate dials these cells are open air.
        return (BLOCK.get(group.get("portal", "WATER"), "#2f52c8") if dialled else AIR), mark
    if "C" in mods:
        unlit = BLOCK.get(chevron, BLOCK.get(group.get("structure"), "#333"))
        return (lamp(group, dialled) if dialled else unlit), mark
    if "S" in mods:
        if lit:
            unlit = BLOCK.get(chevron, BLOCK.get(group.get("structure"), "#333"))
            return (lamp(group, dialled) if dialled else unlit), mark
        return BLOCK.get(group.get("structure"), "#333"), mark
    if mark in ("RD", "RS", "RA"):
        # Bare, these are not frame blocks -- they are the space above one, with the redstone
        # component sitting in it.
        return BLOCK["REDSTONE_WIRE"], mark
    return AIR, mark


def lamp(group, dialled):
    """A lit chevron: the chevron block's on state where it has one, the light block where not."""
    chevron = group.get("chevron")
    if dialled and (chevron in SWITCHES):
        return SWITCHES[chevron]
    return BLOCK.get(group.get("light", "GLOWSTONE"), "#f9d68f")


def ink(colour):
    """Whether a letter on this block should be written in white or in black."""
    r, g, b = (int(colour[i:i + 2], 16) for i in (1, 3, 5))
    return "#0b0f16" if (((r * 299) + (g * 587) + (b * 114)) / 1000.0) > 140 else "#ffffffe0"


def draw(name, grid, group, dialled, fingerprint, plan):
    """One gate on its own dark ground, at ten units to the block."""
    rows, cols = len(grid), len(grid[0])
    size, pad = 10, 4
    width, height = (cols * size) + (pad * 2), (rows * size) + (pad * 2)

    cells = ['<rect x="%d" y="%d" width="%d" height="%d" fill="%s"/>'
             % (pad, pad, cols * size, rows * size, AIR)]
    for i, row in enumerate(grid):
        for j, cell in enumerate(row):
            colour, mark = fill(cell, group, dialled)
            x, y = pad + (j * size), pad + (i * size)
            if colour != AIR:
                # A hairline between blocks, so a run of one material still reads as the
                # several blocks somebody has to place rather than as one painted panel.
                cells.append('<rect x="%.1f" y="%.1f" width="%.1f" height="%.1f" fill="%s"'
                             ' stroke="#0b0f1655" stroke-width="0.5"/>'
                             % (x + 0.25, y + 0.25, size - 0.5, size - 0.5, colour))
            if mark:
                # Small, and over the block rather than beside it, because at gallery size
                # these are illegible anyway and at full size there is room for both.
                cells.append('<text x="%.1f" y="%.1f" font-family="monospace"'
                             ' font-size="%.1f" fill="%s" text-anchor="middle">%s</text>'
                             % (x + (size / 2.0), y + (size * 0.66),
                                size * (0.44 if len(mark) > 1 else 0.6), ink(colour), mark))

    state = "dialled" if dialled else "idle"
    return ("\n".join([
        # Drawn at two and a half times the size the gallery shows it at, for the same reason
        # the mirror sheets are: the table can scale a large drawing down, and the link opens
        # it at its own size. Vector, so the large view costs nothing but these two numbers.
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %d %d" width="%d" height="%d"'
        ' role="img" aria-label="the %s gate shape, %s, seen %s">'
        % (width, height, width * 3, height * 3, name, state, "in plan" if plan else "head on"),
        "<!-- fp %s %s %s -->" % (name, state, fingerprint),
        # Its own ground. GitHub renders a document light or dark depending on the reader, and
        # an obsidian frame on a dark page is not a picture of anything.
        '<rect x="0" y="0" width="%d" height="%d" fill="#0d1420" rx="2"/>' % (width, height),
        "".join(cells),
        "</svg>"]) + "\n")


def palettes(groups, fingerprint):
    """Every palette, one row each, as the blocks it is made of.

    Deliberately not crossed with the shapes. Geometry and palette are independent in the
    plugin -- any shape builds in any group -- so drawing all eleven shapes in all four groups
    would be forty-four pictures asserting a relationship that does not exist.
    """
    keys = ["structure", "portal", "iris", "light", "sign", "chevron"]
    size, pad, label, column = 22, 6, 104, 34
    width = label + (len(keys) * column) + (pad * 2)
    height = ((len(groups) + 1) * size) + (pad * 2)

    parts = ['<rect x="0" y="0" width="%d" height="%d" fill="#0d1420" rx="3"/>' % (width, height)]
    for k, key in enumerate(keys):
        parts.append('<text x="%.1f" y="%d" font-family="sans-serif" font-size="7"'
                     ' fill="#8ea0b8" text-anchor="middle">%s</text>'
                     % (pad + label + (k * column) + (column / 2.0), pad + 12, key))
    for g, (name, group) in enumerate(groups):
        y = pad + size + (g * size)
        parts.append('<text x="%d" y="%.1f" font-family="sans-serif" font-size="9"'
                     ' fill="#e6edf3">%s</text>' % (pad, y + (size * 0.68), name))
        for k, key in enumerate(keys):
            x = pad + label + (k * column) + ((column - size) / 2.0)
            material = group.get(key)
            if material is None:
                # Only `chevron` is optional, and a palette without one has no unlit chevron
                # at all: the cells are ordinary frame blocks. An empty box says that; a frame
                # -coloured box would claim the opposite.
                parts.append('<rect x="%.1f" y="%.1f" width="%d" height="%d" fill="none"'
                             ' stroke="#ffffff26" stroke-width="0.8" stroke-dasharray="2 2"/>'
                             % (x + 2, y + 2, size - 4, size - 4))
                continue
            parts.append('<rect x="%.1f" y="%.1f" width="%d" height="%d" fill="%s"'
                         ' stroke="#ffffff26" stroke-width="0.6"><title>%s %s</title></rect>'
                         % (x + 2, y + 2, size - 4, size - 4,
                            BLOCK.get(material, "#555"), name, material))
    return ("\n".join([
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %d %d" width="%d" height="%d"'
        ' role="img" aria-label="the shipped material groups, block by block">'
        % (width, height, width * 2, height * 2),
        "<!-- fp palettes %s -->" % fingerprint,
        "".join(parts),
        "</svg>"]) + "\n")


def markers(layers):
    """Every marker in the shape, with the layer it is in, nearest first.

    The elevation hides some of them: a shape puts its DHD in its furthest layer, and on
    anything thicker than one layer the frame stands in front of it. Listing them is how the
    page stays complete without the drawing having to pretend you can see through obsidian.
    """
    found = []
    for layer in sorted(layers):
        for row in layers[layer]:
            for cell in row:
                for mod in [m.upper() for m in cell]:
                    if (mod in MARKS) and (mod not in [m for (m, _) in found]):
                        found.append((mod, layer))
    return found


def note(name, settings, layers, grid):
    """The sentence beside a drawing: what the shape is for, and what it does when it dials."""
    if name in PURPOSE:
        said = PURPOSE[name]
    else:
        said = "as `%s`, plus a dial sign and redstone" % name[:-len("SignDial")]

    cells = [cell for rows in layers.values() for row in rows for cell in row]
    woosh = max([wave(cell, "W") or 0 for cell in cells] + [0])
    lights = max([wave(cell, "L") or 0 for cell in cells] + [0])
    ticks = int(settings.get("LIGHT_TICKS", "2"))
    return "%s. %s, %s, %s." % (
        said[0].upper() + said[1:],
        count(len(layers), "layer"),
        "woosh in %s" % count(woosh, "step"),
        ("1 chevron, so no sequence to light in" if lights == 1
         else "%s light %s apart" % (count(lights, "chevron"), count(ticks, "tick"))))


def count(n, noun):
    """`1 layer`, `4 layers`. Said often enough here to be worth not saying by hand."""
    return "%d %s%s" % (n, noun, "" if n == 1 else "s")


def main():
    if not os.path.isdir(SHAPES):
        raise SystemExit("run me from the repository root")
    if not os.path.isdir(OUT):
        os.makedirs(OUT)
    for stale in os.listdir(OUT):
        if stale.endswith(".svg"):
            os.remove(os.path.join(OUT, stale))

    groups = read_groups(CONFIG)
    if not groups:
        raise SystemExit("found no gate-material-groups in " + CONFIG)
    default = groups[0][1]

    rows = []
    files = sorted(f for f in os.listdir(SHAPES) if f.endswith(".shape"))
    for shapefile in files:
        name, layers, settings, fingerprint = read_shape(os.path.join(SHAPES, shapefile))
        if name != shapefile[:-len(".shape")]:
            raise SystemExit("%s calls itself %s; the gallery is keyed on the file name"
                             % (shapefile, name))
        grid, plan = elevation(layers)
        for dialled in (False, True):
            state = "dialled" if dialled else "idle"
            io.open(os.path.join(OUT, "%s-%s.svg" % (name.lower(), state)), "w",
                    encoding="utf-8", newline=chr(10)).write(
                        draw(name, grid, default, dialled, fingerprint, plan))
        rows.append('| <a href="images/gates/%s-idle.svg"><img src="images/gates/%s-idle.svg"'
                    ' width="104" alt="%s, idle"></a>'
                    ' | <a href="images/gates/%s-dialled.svg"><img'
                    ' src="images/gates/%s-dialled.svg" width="104" alt="%s, dialled"></a>'
                    ' | `%s` | %d x %d%s | %s | %s |'
                    % (name.lower(), name.lower(), name, name.lower(), name.lower(), name,
                       name, len(grid[0]), len(grid), ", in plan" if plan else "",
                       note(name, settings, layers, grid),
                       ", ".join("`%s` (layer %d)" % (mark, layer)
                                 for (mark, layer) in markers(layers))))

    shapes = ["| Idle | Dialled | Shape | Grid | What it is | Markers |",
              "|---|---|---|---|---|---|"] + rows

    body = [line for line in io.open(CONFIG, encoding="utf-8").read()
            .replace(chr(13), "").split(chr(10))
            if line.startswith("gate-material-groups:") or (line.startswith("  ")
                                                            and line.strip()
                                                            and not line.strip().startswith("#"))]
    palette_fp = hashlib.sha1(chr(10).join(body).encode("utf-8")).hexdigest()[:10]
    io.open(os.path.join(OUT, "palettes.svg"), "w", encoding="utf-8", newline=chr(10)).write(
        palettes(groups, palette_fp))

    keys = ("structure", "portal", "iris", "light", "sign", "chevron")
    table = ["| Palette | " + " | ".join(k.title() for k in keys) + " |",
             "|" + ("---|" * (len(keys) + 1))]
    for name, group in groups:
        table.append("| `%s` | %s |" % (name, " | ".join(
            ("`%s`" % group[k]) if k in group else "*(none)*" for k in keys)))
    palette = (['![The shipped palettes, block by block](images/gates/palettes.svg)', ""]
               + table)

    document = io.open(DOCUMENT, encoding="utf-8", newline="").read()
    crlf = chr(13) + chr(10) in document
    flat = document.replace(chr(13) + chr(10), chr(10))
    for (start, end, block) in ((SHAPE_START, SHAPE_END, shapes),
                                (PALETTE_START, PALETTE_END, palette)):
        head = flat.index(start) + len(start)
        tail = flat.index(end)
        flat = flat[:head] + "\n\n" + "\n".join(block).rstrip() + "\n\n" + flat[tail:]
    io.open(DOCUMENT, "w", encoding="utf-8", newline="").write(
        flat.replace(chr(10), chr(13) + chr(10)) if crlf else flat)

    print("drew", len(files), "shapes into", OUT, "and rewrote the gallery in", DOCUMENT)


if __name__ == "__main__":
    main()
