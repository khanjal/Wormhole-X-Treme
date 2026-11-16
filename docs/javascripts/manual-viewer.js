// The page shell the iris and dialling viewers share: a shape picker, their own choices, playback
// controls, the 3D view, a status line for screen readers and the text under it. A page's viewer
// extends this and fills in buildParts, plan, show, describe and legend.
import { ShapeError } from "./shape-parser.js";
import { THREE, SceneBase, el, fetchManifest, fetchShape, palette, hex, reduceMotion } from "./scene-kit.js";
import { Player } from "./player.js";

export class ManualViewer extends SceneBase {
  /**
   * @param {HTMLElement} root the box to fill
   * @param {object} opts
   * @param {(shape: object) => boolean} opts.accept which shipped shapes this page can show
   * @param {Array<{key: string, label: string, options: Array<[string, string]>}>} opts.choices extra pickers
   * @param {string} opts.noun what the 3D view is of, for its label
   */
  constructor(root, opts) {
    super();
    this.root = root;
    this.opts = opts;
    this.loadId = 0;
    this.parts = null;
    this.spoken = "";
    this.buildUi();
    this.buildScene();
    this.watch(() => this.applyPalette());
  }

  buildUi() {
    this.select = el("select", { id: "wxt-shape" });
    this.picks = {};
    const bar = el("div", { class: "wxt-bar" }, el("label", { for: "wxt-shape" }, "Shape "), this.select);
    for (const choice of this.opts.choices) {
      const select = el("select", { id: `wxt-${choice.key}` },
        ...choice.options.map(([value, text]) => el("option", { value }, text)));
      this.picks[choice.key] = select;
      select.addEventListener("change", () => this.rebuild());
      bar.append(el("label", { for: `wxt-${choice.key}` }, `${choice.label} `), select);
    }
    this.player = new Player((tick) => this.render(tick), (tick) => this.words(tick));
    // Said when playback stops, since nothing is announced while it runs.
    this.player.onPause = () => this.say(this.words(this.player.tick));
    this.stage = el("div", { class: "wxt-stage" });
    this.canvas = el("canvas", {
      // A focusable region that takes the arrow keys, described by the text beside it.
      tabindex: "0", role: "application", "aria-roledescription": "3D gate view", "aria-describedby": "wxt-info",
      "aria-label": `3D view of ${this.opts.noun}. Arrow keys rotate, plus and minus zoom, 0 resets the view, space plays or pauses.`,
    });
    this.stage.append(this.canvas);
    this.status = el("p", { class: "wxt-status", role: "status", "aria-live": "polite" });
    this.help = el("p", { class: "wxt-help" },
      "Drag to orbit, scroll or pinch to zoom. With the 3D view focused: arrow keys rotate, + and − zoom, 0 resets the view, " +
      "space plays or pauses, . and , step forward and back a tick. The view is from the side the activation switch is on.");
    this.info = el("div", { class: "wxt-info", id: "wxt-info" });
    this.extra = el("div", { class: "wxt-extra" });
    this.legend = el("ul", { class: "wxt-legend", "aria-label": "Legend" });
    this.root.replaceChildren(bar, this.player.el, this.stage, this.status, this.help, this.info, this.extra,
      el("h3", {}, "Legend"), this.legend);
    this.select.addEventListener("change", () => this.load(this.select.value).catch((e) => this.fail(e)));
    this.canvas.addEventListener("keydown", (e) => this.shortcut(e));
  }

  shortcut(e) {
    if (e.key === " ") this.player.playing ? this.player.pause() : this.player.play();
    else if (e.key === ".") this.player.step(1);
    else if (e.key === ",") this.player.step(-1);
    else { this.key(e); return; }
    e.preventDefault();
  }

  async start() {
    const listed = (await fetchManifest()).filter((s) => !/SignDial$/.test(s.name));
    // Parsed up front: which shapes a page can show is a question about their cells.
    this.shapes = new Map();
    await Promise.all(listed.map(async (s) => {
      try {
        const shape = await fetchShape(s.file);
        if (this.opts.accept(shape)) this.shapes.set(s.file, { name: s.name, shape });
      } catch { /* a shape that will not parse is not offered */ }
    }));
    const offered = listed.filter((s) => this.shapes.has(s.file));
    if (!offered.length) throw new Error("no shipped shape can be shown here");
    for (const s of offered) this.select.append(el("option", { value: s.file }, s.name));
    let wanted = "";
    try { wanted = decodeURIComponent(location.hash.slice(1)); } catch { /* a malformed hash just means no pick */ }
    this.select.value = (offered.find((s) => s.name === wanted) || offered.find((s) => s.name === "Standard") || offered[0]).file;
    await this.load(this.select.value);
  }

  fail(error) {
    this.say(error instanceof ShapeError ? `This shape cannot be shown: ${error.message}` : `Could not load the shape: ${error.message}`);
  }

  async load(file) {
    const id = ++this.loadId;
    const entry = this.shapes.get(file);
    if (!entry || id !== this.loadId) return;
    this.player.pause();
    this.shape = entry.shape;
    this.disposeParts();
    this.plan = null;
    this.parts = this.buildParts();
    this.scene.add(this.parts.group);
    this.frameCamera();
    this.rebuild();
  }

  /** The shape or a choice changed: plan the new run and, unless motion is reduced, play it. */
  rebuild() {
    if (!this.parts) return;
    this.player.pause();
    this.plan = this.makePlan();
    this.player.setTotal(this.plan.total);
    this.describe();
    this.applyPalette();
    this.spoken = "";
    this.milestone = null;
    this.lastTick = -1;
    this.player.seek(0);
    // Starts paused when the visitor asks for less motion.
    if (!reduceMotion.matches) this.player.play();
  }

  choice(key) {
    return this.picks[key].value;
  }

  applyPalette() {
    if (!this.parts || !this.plan) return;
    this.paint(palette());
    this.renderLegend();
    this.render(this.player.tick);
  }

  renderLegend() {
    this.legend.replaceChildren(...this.legendItems().map(([label, color, glyph]) =>
      el("li", {}, el("span", { class: "wxt-swatch", "aria-hidden": "true", style: `background:${hex(color)}` }), ` ${glyph} ${label}`)));
  }

  /** The floor grid under a shape, sized to it. */
  addGrid(group) {
    const b = this.bounds();
    const span = Math.max(this.shape.width, this.shape.layers) * 2 + 4;
    const grid = new THREE.GridHelper(span, span);
    grid.position.set(b.center.x, b.min.y - 0.5, b.center.z);
    group.add(grid);
    return grid;
  }

  disposeParts() {
    if (!this.parts) return;
    this.scene.remove(this.parts.group);
    for (const mesh of this.parts.meshes) mesh.dispose();
    this.parts.grid.geometry.dispose();
    this.parts.grid.material.dispose();
    for (const m of this.parts.materials) m.dispose();
    this.parts = null;
  }

  /**
   * Says the current state, but while playing only at a milestone (`key` names it, null for none):
   * a polite live region that changes every two ticks is not a status, it is noise.
   */
  announce(tick, key, text) {
    // Going back (a step back, a scrub, a restart) forgets the milestone, so passing it again is heard.
    if (tick < this.lastTick) this.milestone = null;
    this.lastTick = tick;
    if (!this.player.playing || (key !== null && key !== this.milestone)) this.say(text ?? this.words(tick));
    if (key !== null) this.milestone = key;
  }

  say(text) {
    if (text === this.spoken) return;
    this.spoken = text;
    this.status.textContent = text;
  }
}
