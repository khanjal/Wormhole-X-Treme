#!/usr/bin/env python3
"""Turns the facility gallery's reels into animated WebP, for docs/images.

    python scripts/reel_webp.py .local-server/gallery/1.21.4 docs/images/gates dial- iris-

Each reel the gallery makes leaves a GIF and, beside it, `<name>.frames/`: the same frames as
full-colour PNGs with their delays in timing.json. The WebP is made from those, not from the
GIF, so the horizon's blues are not cut down to GIF's 256 colours. Names are reels or prefixes
ending in a dash; none means every reel in the folder. Needs Pillow (`pip install Pillow`).

720 pixels wide at quality 70 is docs/CAPTURES.md's size: the dial and iris reels come to
about 1.4 MB together.
"""
import json
import os
import sys

from PIL import Image

WIDTH = 720
QUALITY = 70


def chosen(names, wanted):
    """The reels `wanted` names, in order; all of `names` when it names none."""
    if not wanted:
        return sorted(names)
    out = []
    for w in wanted:
        hit = sorted(n for n in names if n == w or (w.endswith('-') and n.startswith(w)))
        if not hit:
            sys.exit(f'no reel named {w} (there are: {", ".join(sorted(names))})')
        out += [n for n in hit if n not in out]
    return out


def convert(frames_dir, dest):
    """Writes one reel's frames to `dest`; returns (frames, seconds)."""
    with open(os.path.join(frames_dir, 'timing.json'), encoding='utf-8') as f:
        timing = json.load(f)
    images = []
    for t in timing:
        with Image.open(os.path.join(frames_dir, t['file'])) as im:
            frame = im.convert('RGB')
        if frame.width != WIDTH:
            frame = frame.resize((WIDTH, round(frame.height * WIDTH / frame.width)), Image.LANCZOS)
        images.append(frame)
    delays = [t['delay'] for t in timing]
    images[0].save(dest, save_all=True, append_images=images[1:], duration=delays, loop=0,
                   quality=QUALITY, method=6, minimize_size=True)
    return len(images), sum(delays) / 1000


def main():
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    src, out = sys.argv[1], sys.argv[2]
    names = [d[:-len('.frames')] for d in os.listdir(src) if d.endswith('.frames')]
    os.makedirs(out, exist_ok=True)
    for name in chosen(names, sys.argv[3:]):
        dest = os.path.join(out, name + '.webp')
        frames, seconds = convert(os.path.join(src, name + '.frames'), dest)
        print(f'{name}: {frames} frames, {seconds:.1f} s, {os.path.getsize(dest) // 1024} KB')


if __name__ == '__main__':
    main()
