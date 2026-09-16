#!/usr/bin/env python3
"""Draws the beam sequence as a timing strip, and writes it into docs/BEAMS.md.

Run from the repository root, with no arguments:

    python scripts/render_beam_sheets.py

It writes docs/images/beams/timing.svg and rewrites the block in docs/BEAMS.md between the
markers. Re-run it whenever the beam timings change -- BeamGalleryTest fails until you do.

A beam is particles, not blocks. There is no geometry to draw and nothing a flat-colour
schematic could say about how it looks, which is what the capture slot in docs/CAPTURES.md is
for. What a drawing *can* say is when each phase runs, and that turns out to be worth saying,
because the four phases do not simply follow one another: the descend column starts at the
teleport tick, which is partway through the rise, so for six ticks the origin column is still
climbing while the destination column is already falling.

That overlap is why the sequence is 52 ticks rather than the 58 that adding the four
durations together gives. The strip is drawn from the same arithmetic BeamFrame runs, and
BeamGalleryTest checks it tick for tick against the real BeamFrame.at().
"""
import io
import os

OUT = "docs/images/beams"
DOCUMENT = "docs/BEAMS.md"
START = "<!-- timing:start -->"
END = "<!-- timing:end -->"

# The shipped defaults, from DefaultSettings. BeamTiming clamps these at run time; at these
# values no clamp bites, which is the case worth drawing.
ENVELOP_TICKS = 12
VANISH_AT_STEP = 6
RISE_TICKS = 18
TELEPORT_AT_STEP = 12
DESCEND_TICKS = 20
FADE_TICKS = 8

# BeamFrame's density range, which is what the envelope ramps up and the fade ramps down.
MIN_DENSITY = 1
MAX_DENSITY = 8

# Twenty ticks is one second, everywhere in this plugin.
TICKS_PER_SECOND = 20

GROUND = "#0d1420"
SLOT = "#1a2130"
TEXT = "#e6edf3"
LABEL = "#8ea0b8"
RULE = "#ffffff1f"

# One colour a phase, and the pairs share a hue by which end they play at: the envelope and
# the rise happen where the traveller was standing, the descend and the fade where they land.
ORIGIN = "#c9873a"
ORIGIN_SOFT = "#8a5c27"
FAR = "#3f7fd0"
FAR_SOFT = "#2b578e"

# The derived row: the ticks on which both columns are running. Pale rather than another hue,
# because it is not a fifth phase -- it is the two either side of it, said again.
BOTH = "#d8dee9"


def frames():
    """Every tick of the sequence, as BeamFrame computes it.

    Transcribed from BeamFrame.at rather than read from it -- there is no resource file here
    either -- so BeamGalleryTest runs the real thing and compares tick for tick.
    """
    out = []
    tick = 0
    while True:
        envelop = tick < ENVELOP_TICKS
        density = 0
        if envelop:
            # Denominator is envelopTicks - 1, so the ramp reaches MAX_DENSITY on the last
            # rendered tick. The fade below deliberately does not match it.
            progress = tick / float(ENVELOP_TICKS - 1)
            density = MIN_DENSITY + int(round((MAX_DENSITY - MIN_DENSITY) * progress))

        since_rise = tick - ENVELOP_TICKS
        rise = (since_rise >= 0) and (since_rise < RISE_TICKS)

        since_teleport = since_rise - TELEPORT_AT_STEP
        descend = (since_teleport >= 0) and (since_teleport < DESCEND_TICKS)

        since_deposit = since_teleport - DESCEND_TICKS
        fade = (since_deposit >= 0) and (since_deposit < FADE_TICKS)
        fade_density = 0
        if fade:
            fade_progress = since_deposit / float(FADE_TICKS)
            fade_density = MAX_DENSITY - int(round((MAX_DENSITY - MIN_DENSITY) * fade_progress))

        if since_deposit >= FADE_TICKS:
            return out

        out.append({
            "tick": tick, "envelop": envelop, "density": density, "rise": rise,
            "descend": descend, "fade": fade, "fadeDensity": fade_density,
            # Not a phase of its own. Drawn because it is the one thing about this sequence
            # that the four durations in a table cannot tell you.
            "both": rise and descend,
            "vanish": tick == VANISH_AT_STEP,
            "teleport": since_rise == TELEPORT_AT_STEP,
            "arrive": since_teleport == DESCEND_TICKS,
        })
        tick += 1


def bar(x, y, width, height, colour):
    """One run of ticks in one phase."""
    return ('<rect x="%.1f" y="%.1f" width="%.1f" height="%.1f" fill="%s" rx="1.5"/>'
            % (x, y, width, height, colour))


def text(x, y, size, colour, body, anchor="start", weight="normal"):
    """A label."""
    return ('<text x="%.1f" y="%.1f" font-family="sans-serif" font-size="%.1f" fill="%s"'
            ' text-anchor="%s" font-weight="%s">%s</text>'
            % (x, y, size, colour, anchor, weight, body))


def runs(sequence, key):
    """The contiguous stretches of ticks where a phase is active, as (first, last) pairs."""
    out, first = [], None
    for frame in sequence:
        if frame[key] and (first is None):
            first = frame["tick"]
        elif not frame[key] and (first is not None):
            out.append((first, frame["tick"] - 1))
            first = None
    if first is not None:
        out.append((first, sequence[-1]["tick"]))
    return out


def strip():
    """The whole sequence, one row a phase, one column a tick."""
    sequence = frames()
    total = len(sequence)
    tick, pad, gutter = 13, 12, 132
    row, gap = 22, 8
    rows = [
        ("Envelop", "envelop", ORIGIN, "density"),
        ("Rise", "rise", ORIGIN_SOFT, None),
        ("Descend", "descend", FAR_SOFT, None),
        ("Both at once", "both", BOTH, None),
        ("Fade", "fade", FAR, "fadeDensity"),
    ]
    width = (pad * 2) + gutter + (total * tick)
    height = (pad * 2) + 46 + (len(rows) * (row + gap)) + 40

    top = pad + 46
    parts = ['<rect x="0" y="0" width="%d" height="%d" fill="%s" rx="3"/>'
             % (width, height, GROUND)]
    parts.append(text(pad, pad + 12, 10, TEXT,
                      "A whole beam cycle &#8212; %d ticks, %.1f seconds" % (
                          total, total / float(TICKS_PER_SECOND)), weight="bold"))
    parts.append(text(pad, pad + 26, 7.5, LABEL,
                      "Orange plays where the traveller was standing; blue plays where they"
                      " land."))

    # A rule every second, so a reader can weigh a phase without counting ticks.
    for second in range(0, (total // TICKS_PER_SECOND) + 1):
        x = pad + gutter + (second * TICKS_PER_SECOND * tick)
        parts.append('<rect x="%.1f" y="%.1f" width="0.7" height="%.1f" fill="%s"/>'
                     % (x, top - 6, len(rows) * (row + gap), RULE))
        parts.append(text(x + 3, top - 10, 6.5, LABEL, "%ds" % second))

    for (index, (name, key, colour, density)) in enumerate(rows):
        y = top + (index * (row + gap))
        stretch = runs(sequence, key)
        # Name and duration both in the gutter. In the bar they collided with the stepped
        # density blocks, and the two rows that step are the two whose durations are least
        # obvious by eye.
        parts.append(text(pad, y + (row * 0.44), 8.5, TEXT, name))
        parts.append(text(pad, y + (row * 0.88), 6.5, LABEL,
                          "%d ticks" % sum((last - first + 1) for (first, last) in stretch)))
        parts.append('<rect x="%.1f" y="%.1f" width="%.1f" height="%.1f" fill="%s" rx="1.5"/>'
                     % (pad + gutter, y, total * tick, row, SLOT))
        for (first, last) in stretch:
            if density is None:
                parts.append(bar(pad + gutter + (first * tick), y,
                                 (last - first + 1) * tick, row, colour))
                continue
            # Drawn a tick at a time, each as tall as that tick's density. The envelope
            # brightening and the fade collapsing are the whole of what those two phases do,
            # and a flat bar would show neither.
            for frame in sequence[first:last + 1]:
                tall = row * (frame[density] / float(MAX_DENSITY))
                parts.append(bar(pad + gutter + (frame["tick"] * tick),
                                 y + (row - tall), tick - 1.0, tall, colour))

    # The five moments. These are what the phases are arranged around, and three of them fall
    # inside a phase rather than between two, which is the point of marking them at all.
    bottom = top + (len(rows) * (row + gap))
    marks = [(0, "start"), (VANISH_AT_STEP, "vanish"),
             (next(f["tick"] for f in sequence if f["teleport"]), "teleport"),
             (next(f["tick"] for f in sequence if f["arrive"]), "arrive"),
             (total, "done")]
    for (at, name) in marks:
        x = pad + gutter + (at * tick)
        parts.append('<rect x="%.1f" y="%.1f" width="1.2" height="%.1f" fill="#ffffffaa"/>'
                     % (x - 0.6, top - 6, (len(rows) * (row + gap)) + 4))
        parts.append(text(x, bottom + 14, 7.5, TEXT, "%s" % name, anchor="middle"))
        parts.append(text(x, bottom + 24, 6.5, LABEL, "t%d" % at, anchor="middle"))

    overlap = [f["tick"] for f in sequence if f["rise"] and f["descend"]]
    data = ("beam total=%d envelop=%s rise=%s descend=%s fade=%s vanish=%d teleport=%d"
            " arrive=%d overlap=%d densities=%s fades=%s"
            % (total, span_of(sequence, "envelop"), span_of(sequence, "rise"),
               span_of(sequence, "descend"), span_of(sequence, "fade"),
               VANISH_AT_STEP,
               next(f["tick"] for f in sequence if f["teleport"]),
               next(f["tick"] for f in sequence if f["arrive"]),
               len(overlap),
               ",".join(str(f["density"]) for f in sequence if f["envelop"]),
               ",".join(str(f["fadeDensity"]) for f in sequence if f["fade"])))

    return ("\n".join([
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %d %d" width="%d" height="%d"'
        ' role="img" aria-label="the beam sequence, tick by tick">'
        % (width, height, width, height),
        # What this drawing claims, in a form BeamGalleryTest can recompute by running the
        # real BeamFrame. The numbers here are transcribed, not read, so nothing about editing
        # the timings would otherwise redraw this.
        "<!-- %s -->" % data,
        "".join(parts),
        "</svg>"]) + "\n"), sequence, total, len(overlap)


def span_of(sequence, key):
    """A phase as {first}-{last}, for the line the test reads."""
    stretch = runs(sequence, key)
    return "%d-%d" % (stretch[0][0], stretch[-1][1])


def wrap(body, width=96):
    """Generated prose wrapped like the hand-written prose around it."""
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


def main():
    if not os.path.isdir("src/main/java"):
        raise SystemExit("run me from the repository root")
    if not os.path.isdir(OUT):
        os.makedirs(OUT)

    drawing, sequence, total, overlap = strip()
    io.open(os.path.join(OUT, "timing.svg"), "w", encoding="utf-8", newline=chr(10)).write(
        drawing)

    naive = ENVELOP_TICKS + RISE_TICKS + DESCEND_TICKS + FADE_TICKS
    block = (["![The beam sequence, tick by tick](images/beams/timing.svg)", ""]
             + wrap(
                 "The four phases do not simply follow one another, which is the one thing"
                 " the table above cannot show. The descend column starts at the teleport"
                 " tick, and the teleport fires %d ticks into an %d-tick rise, so for %d ticks"
                 " the origin column is still climbing while the destination column is already"
                 " falling. The two are at opposite ends of the journey, so nobody sees both."
                 % (TELEPORT_AT_STEP, RISE_TICKS, overlap))
             + [""]
             + wrap(
                 "That is why the whole cycle is **%d ticks, %.1f seconds** rather than the %d"
                 " that adding the four durations together gives. %d ticks is the number to cut"
                 " a capture to." % (total, total / float(TICKS_PER_SECOND), naive, total)))

    document = io.open(DOCUMENT, encoding="utf-8", newline="").read()
    crlf = chr(13) + chr(10) in document
    flat = document.replace(chr(13) + chr(10), chr(10))
    head = flat.index(START) + len(START)
    tail = flat.index(END)
    flat = flat[:head] + "\n\n" + "\n".join(block).rstrip() + "\n\n" + flat[tail:]
    io.open(DOCUMENT, "w", encoding="utf-8", newline="").write(
        flat.replace(chr(10), chr(13) + chr(10)) if crlf else flat)

    print("drew the beam timing strip (%d ticks, %d overlapping) into %s and rewrote %s"
          % (total, overlap, OUT, DOCUMENT))


if __name__ == "__main__":
    main()
