#!/usr/bin/env python3
"""Draws each shipped mirror preset, and writes the gallery in docs/MIRRORS.md.

Run from the repository root, with no arguments:

    python scripts/render_mirror_sheets.py

It reads every file in src/main/resources/shapes/mirror, writes one SVG per look into
docs/images/mirrors, and rewrites the tables in docs/MIRRORS.md between the gallery
markers. Re-run it whenever a preset changes -- MirrorGalleryTest fails until you do,
because each image carries a fingerprint of the preset it was drawn from.

The shapes are approximations of the banner patterns, not the game's textures: enough to
tell two looks apart at a glance, which is the whole job of a gallery. Anything that depends
on the real artwork should be judged in game.
"""
import hashlib
import io
import math
import os

PRESETS = "src/main/resources/shapes/mirror"
OUT = "docs/images/mirrors"
DOCUMENT = "docs/MIRRORS.md"
START = "<!-- gallery:start -->"
END = "<!-- gallery:end -->"

DYE = {
    "WHITE": "#F9FFFE", "ORANGE": "#F9801D", "MAGENTA": "#C74EBD", "LIGHT_BLUE": "#3AB3DA",
    "YELLOW": "#FED83D", "LIME": "#80C71F", "PINK": "#F38BAA", "GRAY": "#474F52",
    "LIGHT_GRAY": "#9D9D97", "CYAN": "#169C9C", "PURPLE": "#8932B8", "BLUE": "#3C44AA",
    "BROWN": "#835432", "GREEN": "#5E7C16", "RED": "#B02E26", "BLACK": "#1D1D21",
}

# Every look, grouped the way somebody looking for one would think about it.
GROUPS = [
    ("Grass and open country", [
        "plains", "sunflower_plains", "meadow", "mushroom_fields", "swamp", "mangrove_swamp",
        "river", "frozen_river", "beach", "snowy_beach", "stony_shore"]),
    ("Woodland", [
        "forest", "birch_forest", "old_growth_birch_forest", "dark_forest", "flower_forest",
        "taiga", "snowy_taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga", "jungle",
        "bamboo_jungle", "sparse_jungle", "cherry_grove", "pale_garden", "windswept_forest"]),
    ("Dry country", [
        "desert", "badlands", "eroded_badlands", "wooded_badlands", "savanna",
        "savanna_plateau", "windswept_savanna"]),
    ("Cold and high", [
        "snowy_plains", "ice_spikes", "snowy_slopes", "frozen_peaks", "jagged_peaks",
        "stony_peaks", "grove", "windswept_hills", "windswept_gravelly_hills"]),
    ("Water", [
        "ocean", "deep_ocean", "cold_ocean", "deep_cold_ocean", "lukewarm_ocean",
        "deep_lukewarm_ocean", "warm_ocean", "frozen_ocean", "deep_frozen_ocean"]),
    ("Underground", ["dripstone_caves", "lush_caves", "deep_dark"]),
    ("The Nether", [
        "nether", "crimson_forest", "warped_forest", "soul_sand_valley", "basalt_deltas"]),
    ("The End, and nowhere at all", [
        "end", "end_highlands", "end_midlands", "small_end_islands", "end_barrens",
        "the_void"]),
    ("Looks the plugin asks for by name", ["overworld", "indoors", "cavern"]),
    ("Looks you stamp yourself", [
        "plain", "portal", "hub", "spawn", "exit", "arrival", "compass", "port", "market",
        "library", "forge", "vault", "shrine", "staff", "private", "locked", "warning",
        "danger", "tomb", "arcane"]),
]

# What each stamp-only look is for, since it has no biome to name.
PURPOSE = {
    "overworld": "fallback, for a biome nothing names",
    "indoors": "a far side that turned out to be a room",
    "cavern": "generic underground",
    "plain": "a colour and a border, and nothing said",
    "portal": "a lit ring on a dark field",
    "hub": "the middle of a network",
    "spawn": "where people arrive on the server",
    "exit": "the way out",
    "arrival": "the other end of exit",
    "compass": "a direction rather than a destination",
    "port": "the mirror at the dock",
    "market": "a striped awning; reads as a shop from a distance",
    "library": "spines on a shelf",
    "forge": "stonework with a fire under it",
    "vault": "a door with a wheel in the middle",
    "shrine": "worked stone with something lit in it",
    "staff": "runs the place, rather than used by everybody",
    "private": "not for general use",
    "locked": "shut, by something other than this plugin",
    "warning": "leads somewhere worth thinking about first",
    "danger": "the charge everybody already reads correctly",
    "tomb": "a memorial, an old world, somewhere somebody died",
}


def shape(pattern, colour, uid):
    """One pattern layer, in a 20 x 40 cloth."""
    c = colour
    if pattern == "BASE":
        return '<rect x="0" y="0" width="20" height="40" fill="%s"/>' % c
    if pattern == "BORDER":
        return ('<path d="M0 0h20v40H0z M1.6 1.6v36.8h16.8V1.6z" fill="%s"'
                ' fill-rule="evenodd"/>' % c)
    if pattern == "CURLY_BORDER":
        parts = ['<g fill="%s">' % c]
        for i in range(5):
            parts.append('<circle cx="%s" cy="1.8" r="2.2"/>' % (2 + i * 4))
            parts.append('<circle cx="%s" cy="38.2" r="2.2"/>' % (2 + i * 4))
        for i in range(9):
            y = round(2.4 + i * 4.4, 2)
            parts.append('<circle cx="1.8" cy="%s" r="2.2"/>' % y)
            parts.append('<circle cx="18.2" cy="%s" r="2.2"/>' % y)
        return "".join(parts) + "</g>"
    if pattern == "BRICKS":
        parts = ['<g fill="%s">' % c]
        for r in range(8):
            parts.append('<rect x="0" y="%s" width="20" height="0.7"/>' % round(r * 5 + 4.4, 2))
            off = 0 if r % 2 else 5
            for k in range(3):
                x = off + k * 10
                if 0 < x < 20:
                    parts.append('<rect x="%s" y="%s" width="0.7" height="5"/>' % (x, r * 5))
        return "".join(parts) + "</g>"
    rects = {
        "HALF_HORIZONTAL": (0, 0, 20, 20), "HALF_HORIZONTAL_BOTTOM": (0, 20, 20, 20),
        "HALF_HORIZONTAL_MIRROR": (0, 20, 20, 20),
        "HALF_VERTICAL": (0, 0, 10, 40), "HALF_VERTICAL_RIGHT": (10, 0, 10, 40),
        "HALF_VERTICAL_MIRROR": (10, 0, 10, 40),
        "SQUARE_TOP_LEFT": (0, 0, 10, 20), "SQUARE_TOP_RIGHT": (10, 0, 10, 20),
        "SQUARE_BOTTOM_LEFT": (0, 20, 10, 20), "SQUARE_BOTTOM_RIGHT": (10, 20, 10, 20),
        "STRIPE_TOP": (0, 0, 20, 5), "STRIPE_BOTTOM": (0, 35, 20, 5),
        "STRIPE_LEFT": (0, 0, 4, 40), "STRIPE_RIGHT": (16, 0, 4, 40),
        "STRIPE_CENTER": (8, 0, 4, 40), "STRIPE_MIDDLE": (0, 17.5, 20, 5),
    }
    if pattern in rects:
        x, y, w, h = rects[pattern]
        return '<rect x="%s" y="%s" width="%s" height="%s" fill="%s"/>' % (x, y, w, h, c)
    if pattern in ("SMALL_STRIPES", "STRIPE_SMALL"):
        parts = ['<g fill="%s">' % c]
        for i in range(6):
            parts.append('<rect x="%s" y="0" width="1.6" height="40"/>' % round(1 + i * 3.2, 2))
        return "".join(parts) + "</g>"
    paths = {
        "STRIPE_DOWNRIGHT": "M0 0 L5 0 L20 34 L20 40 L15 40 L0 6 Z",
        "STRIPE_DOWNLEFT": "M20 0 L15 0 L0 34 L0 40 L5 40 L20 6 Z",
        "CROSS": ("M0 0 L5 0 L20 34 L20 40 L15 40 L0 6 Z"
                  " M20 0 L15 0 L0 34 L0 40 L5 40 L20 6 Z"),
        "STRAIGHT_CROSS": "M8 0h4v40H8z M0 17.5h20v5H0z",
        "DIAGONAL_LEFT": "M0 0 L20 0 L0 40 Z",
        "DIAGONAL_RIGHT": "M0 0 L20 0 L20 40 Z",
        "DIAGONAL_UP_LEFT": "M0 0 L0 40 L20 40 Z",
        "DIAGONAL_LEFT_MIRROR": "M0 0 L0 40 L20 40 Z",
        "DIAGONAL_UP_RIGHT": "M20 0 L20 40 L0 40 Z",
        "DIAGONAL_RIGHT_MIRROR": "M20 0 L20 40 L0 40 Z",
        "TRIANGLE_TOP": "M0 0 L20 0 L10 13 Z",
        "TRIANGLE_BOTTOM": "M0 40 L20 40 L10 27 Z",
        "RHOMBUS": "M10 11 L17 20 L10 29 L3 20 Z",
        "RHOMBUS_MIDDLE": "M10 11 L17 20 L10 29 L3 20 Z",
    }
    if pattern in paths:
        return '<path d="%s" fill="%s"/>' % (paths[pattern], c)
    if pattern in ("TRIANGLES_TOP", "TRIANGLES_BOTTOM"):
        top = pattern.endswith("TOP")
        d = ""
        for i in range(4):
            x = i * 5
            if top:
                d += "M%s 0 L%s 0 L%s 6 Z " % (x, x + 5, x + 2.5)
            else:
                d += "M%s 40 L%s 40 L%s 34 Z " % (x, x + 5, x + 2.5)
        return '<path d="%s" fill="%s"/>' % (d.strip(), c)
    if pattern in ("CIRCLE", "CIRCLE_MIDDLE"):
        return '<circle cx="10" cy="20" r="6.2" fill="%s"/>' % c
    if pattern in ("GRADIENT", "GRADIENT_UP"):
        y1, y2 = ("0", "1") if pattern == "GRADIENT" else ("1", "0")
        return ('<defs><linearGradient id="g%s" x1="0" y1="%s" x2="0" y2="%s">'
                '<stop offset="0" stop-color="%s" stop-opacity="1"/>'
                '<stop offset="1" stop-color="%s" stop-opacity="0"/></linearGradient></defs>'
                '<rect x="0" y="0" width="20" height="40" fill="url(#g%s)"/>'
                % (uid, y1, y2, c, c, uid))
    if pattern == "CREEPER":
        return ('<path d="M5 12h4v5H5z M11 12h4v5h-4z M8 18h4v5H8z M6 23h3v5H6z M11 23h3v5h-3z"'
                ' fill="%s"/>' % c)
    if pattern == "SKULL":
        return ('<path d="M4.5 11h11v12h-2.5v3h-6v-3H4.5z M6.5 14h2.5v3H6.5z M11 14h2.5v3H11z'
                ' M9 18.5h2v2H9z" fill="%s" fill-rule="evenodd"/>' % c)
    if pattern == "FLOWER":
        parts = ['<g fill="%s">' % c]
        for i in range(8):
            a = (math.pi * 2 / 8) * i
            parts.append('<circle cx="%.2f" cy="%.2f" r="2.4"/>'
                         % (10 + math.cos(a) * 4.6, 20 + math.sin(a) * 4.6))
        parts.append('<circle cx="10" cy="20" r="2.9"/></g>')
        return "".join(parts)
    if pattern == "GLOBE":
        return ('<g fill="none" stroke="%s" stroke-width="1.1"><circle cx="10" cy="20" r="6.4"/>'
                '<ellipse cx="10" cy="20" rx="2.8" ry="6.4"/>'
                '<path d="M3.8 17.6h12.4 M3.8 22.4h12.4"/></g>' % c)
    if pattern == "PIGLIN":
        return ('<g fill="%s"><ellipse cx="4.6" cy="18" rx="2.1" ry="3.2"/>'
                '<ellipse cx="15.4" cy="18" rx="2.1" ry="3.2"/></g>'
                '<path d="M6.6 14.5h6.8v10H6.6z M8.2 17.5h1.6v4H8.2z M10.6 17.5h1.6v4h-1.6z"'
                ' fill="%s" fill-rule="evenodd"/>' % (c, c))
    if pattern == "MOJANG":
        return '<ellipse cx="10" cy="20" rx="6" ry="7.5" fill="%s"/>' % c
    return ""


def read_preset(path):
    text = io.open(path, encoding="utf-8").read().replace(chr(13), "")
    body = [l.strip() for l in text.split(chr(10))
            if l.startswith(("Base=", "Layer=", "Biome=", "Sheltered="))]
    base, layers, biome = "WHITE", [], None
    for line in body:
        if line.startswith("Base="):
            base = line[5:]
        elif line.startswith("Layer="):
            bits = line[6:].split()
            layers.append((bits[0], bits[1]))
        elif line.startswith("Biome="):
            biome = line[6:]
    fingerprint = hashlib.sha1(chr(10).join(body).encode("utf-8")).hexdigest()[:10]
    return base, layers, biome, fingerprint


def draw(name, base, layers, fingerprint):
    """One banner on its own dark ground, sized for a table cell."""
    inner = ['<rect x="0" y="0" width="20" height="40" fill="%s"/>' % DYE.get(base, "#ccc")]
    for i, (colour, pattern) in enumerate(layers):
        inner.append(shape(pattern, DYE.get(colour, "#888"), "%s%s" % (name[:3], i)))
    inner.append('<rect x="0" y="0" width="20" height="40" fill="none"'
                 ' stroke="#ffffff40" stroke-width="0.6"/>')
    return ("\n".join([
        # Drawn at six times the size it is shown at. GitHub's sanitiser allows neither
        # a stylesheet nor a script in a document, so there is no hover to enlarge one with --
        # but the table can scale a big drawing down, and a link to the file opens it at its
        # own size. Vector, so the large view costs nothing but the two numbers below.
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 26 46" width="156"'
        ' height="276" role="img" aria-label="the %s mirror look">' % name,
        "<!-- fp %s %s -->" % (name, fingerprint),
        # Its own ground, because GitHub renders a document light or dark depending on the
        # reader and a white banner on a white page is not a picture of anything.
        '<rect x="0" y="0" width="26" height="46" fill="#0d1420" rx="2"/>',
        '<g transform="translate(3,3)">%s</g>' % "".join(inner),
        "</svg>"]) + "\n")


def main():
    if not os.path.isdir(PRESETS):
        raise SystemExit("run me from the repository root")
    if not os.path.isdir(OUT):
        os.makedirs(OUT)
    for stale in os.listdir(OUT):
        if stale.endswith(".svg"):
            os.remove(os.path.join(OUT, stale))

    listed = [name for (_, names) in GROUPS for name in names]
    shipped = sorted(f[:-7] for f in os.listdir(PRESETS) if f.endswith(".mirror"))
    if sorted(listed) != shipped:
        raise SystemExit("groups and presets disagree: missing %s, extra %s"
                         % (sorted(set(shipped) - set(listed)),
                            sorted(set(listed) - set(shipped))))

    tables = []
    for (heading, names) in GROUPS:
        places = any(read_preset(os.path.join(PRESETS, n + ".mirror"))[2] for n in names)
        tables.append("#### " + heading)
        tables.append("")
        tables.append("| | Look | %s | Layers, in order |"
                      % ("Answers for" if places else "For"))
        tables.append("|---|---|---|---|")
        for name in names:
            base, layers, biome, fingerprint = read_preset(os.path.join(PRESETS,
                                                                       name + ".mirror"))
            io.open(os.path.join(OUT, name + ".svg"), "w", encoding="utf-8",
                    newline=chr(10)).write(draw(name, base, layers, fingerprint))
            right = ("`" + biome + "`") if biome else PURPOSE.get(name, "")
            # The recipe, not just the picture. Somebody checking a stamped banner against
            # this page needs the thing the plugin actually applied -- the drawing is an
            # approximation and would put the blame in the wrong place on a mismatch.
            recipe = " ".join(["`%s` base" % base]
                              + ["+ `%s %s`" % (colour, pattern.lower())
                                 for (colour, pattern) in layers])
            plain = recipe.replace("`", "")
            tables.append('| <a href="images/mirrors/%s.svg" title="%s">'
                          '<img src="images/mirrors/%s.svg" width="26" alt="the %s look"></a>'
                          ' | `%s` | %s | %s |'
                          % (name, plain, name, name, name, right, recipe))
        tables.append("")

    document = io.open(DOCUMENT, encoding="utf-8", newline="").read()
    crlf = chr(13) + chr(10) in document
    flat = document.replace(chr(13) + chr(10), chr(10))
    head = flat.index(START) + len(START)
    tail = flat.index(END)
    flat = flat[:head] + "\n\n" + "\n".join(tables).rstrip() + "\n\n" + flat[tail:]
    io.open(DOCUMENT, "w", encoding="utf-8", newline="").write(
        flat.replace(chr(10), chr(13) + chr(10)) if crlf else flat)

    print("drew", len(listed), "looks into", OUT, "and rewrote the gallery in", DOCUMENT)


if __name__ == "__main__":
    main()
