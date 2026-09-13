#!/usr/bin/env python3
"""Draws the ring patterns, the stack they build, and how they get there.

Run from the repository root, with no arguments:

    python scripts/render_ring_sheets.py

It writes six SVGs into docs/images/rings and rewrites three blocks in docs/RINGS.md
between the markers. Re-run it whenever the patterns or the animator's constants change --
RingGalleryTest fails until you do.

Rings have no resource file to read. There are exactly two patterns and they are hardcoded,
which RingPattern argues for at length, so the numbers here are transcribed from the Java
rather than parsed out of it. That is a duplication, and duplicated arithmetic is exactly the
thing that rots quietly -- so every drawing carries a machine-readable line saying what it
drew, and RingGalleryTest recomputes it from RingPattern and RingAnimator themselves. The
test is not a staleness check like the gate and mirror galleries' fingerprints; it runs the
real animator and compares frame for frame.

Nothing here animates. The transport flash runs three ticks a ring through a four-ring
stack, and docs/CAPTURES.md makes the case against putting that on a page as an endless
loop: it is a fast bright strobe the reader cannot pause. A filmstrip reads better anyway,
because the frames can be compared side by side rather than remembered.
"""
import io
import os

OUT = "docs/images/rings"
DOCUMENT = "docs/RINGS.md"
BLOCKS = (("patterns", "PATTERNS"), ("stack", "STACK"), ("flash", "FLASH"))

# Row widths of the filled disc, top row first -- the whole of what a pattern is. Everything
# else (which cells are outline, where the anchor sits) is derived below exactly as
# RingPattern derives it.
PROFILES = {"ODD": [3, 5, 7, 7, 7, 5, 3], "EVEN": [2, 4, 6, 6, 4, 2]}

# RingAnimator's constants. The four at the top are the ones actually chosen; the rest are
# derived here the same way the animator derives them, so a change to the first four carries
# through the drawings the way it carries through the plugin.
SPACING = 2
TRAVEL_GAP = 3
RING_COUNT = 4
BASE_HALF_STEP = 1
TOP_HALF_STEP = BASE_HALF_STEP + ((RING_COUNT - 1) * SPACING)
STACK_HEIGHT = (TOP_HALF_STEP // 2) + 1

GROUND = "#0d1420"
SLAB = "#9aa3ad"
INTERIOR = "#2f52c8"
LIT = "#f9d68f"
PAD = "#6b5a2a"
EDGE = "#0b0f1655"
LABEL = "#8ea0b8"
TEXT = "#e6edf3"
FLOOR = "#3a3026"

# An empty half-step slot. A solid colour rather than white at low alpha: eight-digit hex is
# fine in a browser, but not every SVG renderer honours it, and one that does not paints these
# opaque white and the drawing becomes a picture of the gaps.
SLOT = "#1a2130"

# An em dash, written as an escape so this file stays ASCII the way its siblings do. Inside a
# drawing it has to be the XML entity instead, which is what &#8212; is doing below.
DASH = "\u2014"


def fill(profile):
    """The filled disc, as RingPattern paints it: each row centred in a square grid."""
    size = len(profile)
    grid = [[False] * size for _ in range(size)]
    for row in range(size):
        start = (size - profile[row]) // 2
        for column in range(start, start + profile[row]):
            grid[row][column] = True
    return grid


def classify(grid):
    """Every filled cell sorted into perimeter and interior, as offsets from the anchor.

    A filled cell is on the perimeter when any orthogonal neighbour is not filled, which is
    what the outline of a shape means. The anchor is at (size - 1) // 2 on both axes: the true
    centre of the odd pattern, and the low-x, low-z block of the even one's central 2x2.
    """
    size = len(grid)
    anchor = (size - 1) // 2
    perimeter, interior = [], []
    for row in range(size):
        for column in range(size):
            if not grid[row][column]:
                continue
            edge = any(not inside(grid, row + dr, column + dc)
                       for (dr, dc) in ((-1, 0), (1, 0), (0, -1), (0, 1)))
            (perimeter if edge else interior).append((column - anchor, row - anchor))
    return perimeter, interior


def inside(grid, row, column):
    """Reads the grid, treating anything outside it as unfilled."""
    return (0 <= row < len(grid)) and (0 <= column < len(grid)) and grid[row][column]


def svg(width, height, label, data, parts):
    """One drawing on its own dark ground, with the line the test reads."""
    return ("\n".join([
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %d %d" width="%d" height="%d"'
        ' role="img" aria-label="%s">' % (width, height, width * 2, height * 2, label),
        # What this drawing claims, in a form RingGalleryTest can recompute from the plugin.
        # A picture of the geometry that has stopped agreeing with the geometry is worse than
        # no picture, and nothing about editing RingAnimator would otherwise redraw this.
        "<!-- %s -->" % data,
        '<rect x="0" y="0" width="%d" height="%d" fill="%s" rx="3"/>' % (width, height, GROUND),
        "".join(parts),
        "</svg>"]) + "\n")


def block(x, y, size, colour, height=None):
    """One block, with the hairline that keeps a run of them reading as several."""
    return ('<rect x="%.1f" y="%.1f" width="%.1f" height="%.1f" fill="%s" stroke="%s"'
            ' stroke-width="0.5"/>'
            % (x + 0.25, y + 0.25, size - 0.5, (height or size) - 0.5, colour, EDGE))


def text(x, y, size, colour, body, anchor="middle", family="sans-serif"):
    """A label."""
    return ('<text x="%.1f" y="%.1f" font-family="%s" font-size="%.1f" fill="%s"'
            ' text-anchor="%s">%s</text>' % (x, y, family, size, colour, anchor, body))


def titled(body, size, content):
    """Wide enough for a title, or for the drawing under it, whichever is wider."""
    return int(max(content, (len(body) * size * 0.55) + 20))


def pattern(name):
    """One footprint in plan, the way somebody laying slabs looks at it."""
    profile = PROFILES[name]
    grid = fill(profile)
    perimeter, interior = classify(grid)
    size, pad, cell = len(profile), 8, 16
    anchor = (size - 1) // 2
    width = (size * cell) + (pad * 2)
    height = (size * cell) + (pad * 2)

    parts = []
    for (dx, dz) in interior:
        parts.append(block(pad + ((dx + anchor) * cell), pad + ((dz + anchor) * cell),
                           cell, INTERIOR))
    for (dx, dz) in perimeter:
        parts.append(block(pad + ((dx + anchor) * cell), pad + ((dz + anchor) * cell),
                           cell, SLAB))
    # The anchor block, which is the only cell in either drawing that is not self-evident:
    # the odd pattern's is its true centre, the even one's a corner of the middle four.
    ax, ay = pad + (anchor * cell), pad + (anchor * cell)
    parts.append('<rect x="%.1f" y="%.1f" width="%.1f" height="%.1f" fill="none"'
                 ' stroke="#ffffff" stroke-width="1.4"/>'
                 % (ax + 1.2, ay + 1.2, cell - 2.4, cell - 2.4))
    data = "pattern %s perimeter=%s interior=%s" % (
        name, offsets(perimeter), offsets(interior))
    return svg(width, height, "the %s ring pattern in plan" % name.lower(), data, parts)


def offsets(cells):
    """Offsets in one canonical order, so the drawing and the plugin can be compared."""
    return ";".join("%d,%d" % (dx, dz) for (dx, dz) in sorted(cells))


def wrap(body, width=96):
    """Generated prose wrapped like the hand-written prose around it.

    The document wraps at just under a hundred columns throughout, and a generated block that
    runs to one 400-character line makes every diff of this file unreadable from then on.
    """
    lines, line = [], ""
    for word in body.split():
        if line and ((len(line) + 1 + len(word)) > width):
            lines.append(line)
            line = word
        else:
            line = (line + " " + word) if line else word
    if line:
        lines.append(line)
    return lines


def counts(name):
    """How many cells of each kind a pattern has, for the line under its drawing."""
    perimeter, interior = classify(fill(PROFILES[name]))
    return (len(perimeter), len(interior))


def resting(index):
    """Where a floor ring's given ring comes to rest, in half-steps above the stack base."""
    return BASE_HALF_STEP + ((RING_COUNT - 1 - index) * SPACING)


def journey(index):
    """How many frames one ring's whole journey takes, starting from half-step zero."""
    return resting(index) + 1


def emerges(style, index):
    """The frame a ring leaves the plane on."""
    if style == "CONCURRENT":
        return index * TRAVEL_GAP
    return sum(journey(earlier) for earlier in range(index))


def frames(style):
    """Every deploy frame, as the half-steps occupied on it."""
    total = max(emerges(style, i) + journey(i) for i in range(RING_COUNT))
    out = []
    for frame in range(total):
        present = []
        for index in range(RING_COUNT):
            travelled = frame - emerges(style, index)
            if travelled >= 0:
                present.append(min(travelled, resting(index)))
        out.append(sorted(present))
    return out


def ladder(parts, x, top, steps, half, occupied, lit=None):
    """One column of half-step slots, filled where a ring is.

    A ring is drawn as the half of its block it actually fills, because that is the whole
    trick: blocks exist only at whole positions, so a ring rises half a block by switching
    which half of its block it is.
    """
    for step in range(steps):
        y = top + ((steps - 1 - step) * half)
        if step in occupied:
            parts.append(block(x, y, half * 2, LIT if step == lit else SLAB, height=half))
        else:
            parts.append('<rect x="%.1f" y="%.1f" width="%.1f" height="%.1f" fill="%s"/>'
                         % (x + 0.25, y + 0.25, (half * 2) - 0.5, half - 0.5, SLOT))


def strip(style):
    """A filmstrip of one deploy: one column a frame, left to right."""
    sequence = frames(style)
    steps = TOP_HALF_STEP + 1
    half, column, pad, gap = 9, 26, 10, 4
    width = (pad * 2) + (len(sequence) * (column + gap))
    height = (pad * 2) + (steps * half) + 30

    title = style.title() + (" &#8212; several climbing at once" if style == "CONCURRENT"
                             else " &#8212; never more than one in flight")
    width = titled(title, 9, width)
    parts = [text(pad, pad + 8, 9, TEXT, title, anchor="start")]
    top = pad + 16
    for (frame, occupied) in enumerate(sequence):
        x = pad + (frame * (column + gap))
        ladder(parts, x, top, steps, half, set(occupied))
        parts.append(text(x + (column / 2.0), top + (steps * half) + 9, 7, LABEL, str(frame)))
    # Both strips end in the same stack, so what each footer has to say is what its own style
    # costs: the concurrent one overlaps the journeys, the sequential one queues them.
    footer = ("%d frames &#8212; the longest single journey plus the gaps. Travelling, rings"
              " are %d half-steps apart; settled, %d."
              % (len(sequence), TRAVEL_GAP, SPACING)) if style == "CONCURRENT" else (
                  "%d frames &#8212; the sum of all four journeys, because each ring waits for"
                  " the one before it to stop." % len(sequence))
    parts.append(text(pad, height - 5, 7.5, LABEL, footer, anchor="start"))

    data = "deploy %s %s" % (style, "|".join(
        ",".join(str(s) for s in occupied) for occupied in sequence))
    return svg(width, height, "the %s deploy, frame by frame" % style.lower(), data, parts)


def stack():
    """The finished stack in elevation, with a player beside it for scale."""
    half, pad, cell, labels = 16, 10, 32, 130
    steps = TOP_HALF_STEP + 1
    title = "The finished stack &#8212; %d blocks of headroom" % STACK_HEIGHT
    width = titled(title, 9, (pad * 2) + cell + labels + 40)
    height = (pad * 2) + (steps * half) + 16 + 22

    top = pad + 16
    floor = top + (steps * half)
    parts = [text(pad, pad + 8, 9, TEXT, title, anchor="start")]

    # The ladder first, so the half-block gaps between rings are as visible as the rings. They
    # are the point: rings settle SPACING half-steps apart and travel TRAVEL_GAP apart, and a
    # drawing of four bars with nothing between them says neither number.
    settled = {resting(index): index for index in range(RING_COUNT)}
    for step in range(steps):
        y = floor - ((step + 1) * half)
        if step in settled:
            parts.append(block(pad, y, cell, SLAB, height=half))
            parts.append(text(pad + cell + 8, y + half - 3, 7.5, LABEL,
                              "ring %d &#8212; %.1f blocks up"
                              % (settled[step], step / 2.0), anchor="start"))
        else:
            parts.append('<rect x="%.1f" y="%.1f" width="%.1f" height="%.1f" fill="%s"/>'
                         % (pad + 0.25, y + 0.25, cell - 0.5, half - 0.5, SLOT))

    parts.append('<rect x="%.1f" y="%.1f" width="%.1f" height="7" fill="%s"/>'
                 % (pad, floor, width - (pad * 2), FLOOR))
    parts.append(block(pad, floor, cell, PAD, height=7))
    parts.append(text(pad, floor + 18, 7.5, LABEL,
                      "The pad, and the half block the lowest ring hangs clear of it.",
                      anchor="start"))

    # A player to the same scale. The argument for four rings rather than the show's five is
    # entirely about how big a ring is beside somebody 1.8 blocks tall, and that is a
    # comparison, so the drawing has to contain both halves of it.
    person = 1.8 * 2 * half
    px = pad + cell + labels
    parts.append('<rect x="%.1f" y="%.1f" width="11" height="%.1f" rx="5.5" fill="#5c6b7f"/>'
                 % (px, floor - person, person))
    parts.append(text(px + 5.5, floor - person - 4, 7, LABEL, "1.8"))

    data = ("stack SPACING=%d TRAVEL_GAP=%d RING_COUNT=%d BASE_HALF_STEP=%d TOP_HALF_STEP=%d"
            " STACK_HEIGHT=%d resting=%s"
            % (SPACING, TRAVEL_GAP, RING_COUNT, BASE_HALF_STEP, TOP_HALF_STEP, STACK_HEIGHT,
               ",".join(str(resting(i)) for i in range(RING_COUNT))))
    return svg(width, height, "the finished ring stack in elevation", data, parts)


def flash():
    """The transport light running through the stack, one frame a ring."""
    half, pad, cell, gap = 12, 10, 26, 16
    steps = TOP_HALF_STEP + 1
    shots = RING_COUNT + 1
    title = "The transport flash &#8212; the light always runs towards the pad"
    width = titled(title, 9, (pad * 2) + (shots * (cell + gap)))
    height = (pad * 2) + (steps * half) + 20 + 18

    top = pad + 16
    floor = top + (steps * half)
    parts = [text(pad, pad + 8, 9, TEXT, title, anchor="start")]
    settled = {resting(index): index for index in range(RING_COUNT)}
    for shot in range(shots):
        x = pad + (shot * (cell + gap))
        # Ring zero is the first one out and travels furthest from its pad, so counting up
        # from it runs towards the pad -- which is why this needs no sense of direction.
        lit = next((step for (step, index) in settled.items() if index == shot), None)
        ladder(parts, x, top, steps, half, set(settled), lit=lit)
        parts.append('<rect x="%.1f" y="%.1f" width="%.1f" height="5" fill="%s"/>'
                     % (x + 0.25, floor, cell - 0.5, PAD))
        parts.append(text(x + (cell / 2.0), floor + 15, 7, LABEL,
                          ("ring %d" % shot) if shot < RING_COUNT else "hold"))
    parts.append(text(pad, height - 5, 7.5, LABEL,
                      "Drawn over the stack, not instead of it, so nothing appears to move as"
                      " the light passes.", anchor="start"))

    data = "flash order=%s" % ",".join(str(resting(index)) for index in range(RING_COUNT))
    return svg(width, height, "the transport flash, frame by frame", data, parts)


def main():
    if not os.path.isdir("src/main/java"):
        raise SystemExit("run me from the repository root")
    if not os.path.isdir(OUT):
        os.makedirs(OUT)
    for stale in os.listdir(OUT):
        if stale.endswith(".svg"):
            os.remove(os.path.join(OUT, stale))

    drawings = {
        "pattern-odd.svg": pattern("ODD"),
        "pattern-even.svg": pattern("EVEN"),
        "stack.svg": stack(),
        "deploy-concurrent.svg": strip("CONCURRENT"),
        "deploy-sequential.svg": strip("SEQUENTIAL"),
        "flash.svg": flash(),
    }
    for (name, body) in drawings.items():
        io.open(os.path.join(OUT, name), "w", encoding="utf-8", newline=chr(10)).write(body)

    patterns = [
        "| Odd | Even |", "|---|---|",
        '| <a href="images/rings/pattern-odd.svg"><img src="images/rings/pattern-odd.svg"'
        ' width="196" alt="the odd ring pattern in plan"></a>'
        ' | <a href="images/rings/pattern-even.svg"><img src="images/rings/pattern-even.svg"'
        ' width="168" alt="the even ring pattern in plan"></a> |',
        ("| 7 across %s %d perimeter, %d interior, a true centre"
         " | 6 across %s %d perimeter, %d interior, a 2x2 centre |"
         % ((DASH,) + counts("ODD") + (DASH,) + counts("EVEN"))),
        "",
    ] + wrap(
        "Grey is the perimeter, which is what the player lays in slabs and what animates. Blue"
        " is the interior: the trigger volume, and the region that travels. The outlined cell"
        " is the anchor every offset is measured from.")
    stacking = [
        "![The finished stack](images/rings/stack.svg)",
        "",
        "![The concurrent deploy, frame by frame](images/rings/deploy-concurrent.svg)",
        "",
        "![The sequential deploy, frame by frame](images/rings/deploy-sequential.svg)",
        "",
    ] + wrap(
        "Each column is one frame and each slot one half-step. The two strips end in the same"
        " stack and differ only in when a ring leaves the plane, which is what makes this one"
        " number rather than two animations.")
    flashing = [
        "![The transport flash, frame by frame](images/rings/flash.svg)",
        "",
    ] + wrap(
        "A filmstrip rather than a loop, deliberately: three ticks a ring through four rings is"
        " a fast bright flicker, and an animation on a page autoplays forever with no way to"
        " pause it.")

    document = io.open(DOCUMENT, encoding="utf-8", newline="").read()
    crlf = chr(13) + chr(10) in document
    flat = document.replace(chr(13) + chr(10), chr(10))
    for (marker, body) in (("patterns", patterns), ("stack", stacking), ("flash", flashing)):
        start, end = "<!-- %s:start -->" % marker, "<!-- %s:end -->" % marker
        head = flat.index(start) + len(start)
        tail = flat.index(end)
        flat = flat[:head] + "\n\n" + "\n".join(body).rstrip() + "\n\n" + flat[tail:]
    io.open(DOCUMENT, "w", encoding="utf-8", newline="").write(
        flat.replace(chr(10), chr(13) + chr(10)) if crlf else flat)

    print("drew", len(drawings), "ring sheets into", OUT, "and rewrote", DOCUMENT)


if __name__ == "__main__":
    main()
