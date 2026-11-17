// The gate shape viewer on the User manual page: reads the real .shape files copied to
// assets/shapes/ by scripts/copy-docs-shapes.sh and draws them with three.js.
import { describeShape, ShapeError } from "./shape-parser.js";
import {
  THREE, SceneBase, el, hex, palette, isDark, loadLibs, fetchManifest, fetchShape, TICK_MS, reduceMotion, stageWithLegend,
} from "./scene-kit.js";

import { wooshStep, wooshTint, partitionCells, parseRgb } from "./woosh-cue.js";

const MIN_STEP_MS = 250; // real timings are too quick to follow on a page

export const MARKERS = {
  N: { label: "Name sign", color: 0xf1c40f, shape: "plate" },
  EP: { label: "Player entry point", color: 0x2ecc71, shape: "sphere" },
  EM: { label: "Minecart entry point", color: 0x1abc9c, shape: "cone" },
  A: { label: "Activation switch", color: 0xe67e22, shape: "cylinder" },
  IA: { label: "Iris switch", color: 0xe91e8c, shape: "octa" },
  D: { label: "Sign dialler", color: 0x9b59b6, shape: "plate" },
  RD: { label: "Redstone activation block", color: 0xe74c3c, shape: "cube" },
  RS: { label: "Redstone sign-cycle block", color: 0xc0392b, shape: "tetra" },
  RA: { label: "Redstone output block", color: 0xff7675, shape: "ring" },
};

/** How many leading cells (sorted by order) belong to a step at or before `order`. */
const through = (sorted, key, order) => sorted.filter((c) => c[key] <= order).length;

// The designer page builds on this one, replacing buildUi.
export class GateViewer extends SceneBase {
  constructor(root) {
    super();
    this.root = root;
    this.runId = 0;
    this.loadId = 0;
    this.state = "idle";
    this.parts = null;
    this.litUpTo = 0;
    this.buildUi();
    this.buildScene();
    this.watch(() => this.applyPalette());
  }

  buildUi() {
    this.select = el("select", { id: "wxt-shape" });
    this.buttons = {};
    const bar = el("div", { class: "wxt-bar" },
      el("label", { for: "wxt-shape" }, "Shape "), this.select);
    for (const [key, label] of [["idle", "Idle"], ["dial", "Dial"], ["open", "Open"], ["reset", "Reset"]]) {
      const b = el("button", { type: "button", class: "md-button" }, label);
      b.addEventListener("click", () => this.command(key));
      this.buttons[key] = b;
      bar.append(b);
    }
    this.stage = el("div", { class: "wxt-stage" });
    this.canvas = el("canvas", {
      // A focusable region that takes the arrow keys, described by the text beside it.
      tabindex: "0", role: "application", "aria-roledescription": "3D gate view",
      "aria-describedby": "wxt-info",
      "aria-label": "3D view of the selected gate shape. Arrow keys rotate, plus and minus zoom, 0 resets the view.",
    });
    this.stage.append(this.canvas);
    this.status = el("p", { class: "wxt-status", role: "status", "aria-live": "polite" });
    this.help = el("p", { class: "wxt-help" },
      "Drag to orbit, scroll or pinch to zoom. With the 3D view focused: arrow keys rotate, + and − zoom, 0 resets the view. " +
      "The view starts from the side the dialling switch is on, as a player standing at the gate sees it.");
    this.info = el("div", { class: "wxt-info", id: "wxt-info" });
    this.legend = el("ul", { class: "wxt-legend", "aria-label": "Legend" });
    this.root.replaceChildren(bar, stageWithLegend(this.stage, this.legend), this.status, this.help, this.info);
    this.select.addEventListener("change", () => this.load(this.select.value).catch((e) => this.fail(e)));
    this.canvas.addEventListener("keydown", (e) => this.key(e));
  }

  buildScene() {
    super.buildScene();
    this.markerGeometries = {
      plate: new THREE.BoxGeometry(0.8, 0.5, 0.15),
      sphere: new THREE.SphereGeometry(0.28, 16, 12),
      cone: new THREE.ConeGeometry(0.3, 0.6, 12),
      cylinder: new THREE.CylinderGeometry(0.2, 0.2, 0.5, 12),
      octa: new THREE.OctahedronGeometry(0.33),
      cube: new THREE.BoxGeometry(0.4, 0.4, 0.4),
      tetra: new THREE.TetrahedronGeometry(0.38),
      ring: new THREE.TorusGeometry(0.25, 0.08, 8, 16),
    };
  }

  async start() {
    const shapes = await fetchManifest();
    for (const s of shapes) this.select.append(el("option", { value: s.file }, s.name));
    let wanted = "";
    try { wanted = decodeURIComponent(location.hash.slice(1)); } catch { /* a malformed hash just means no pick */ }
    const match = shapes.find((s) => s.name === wanted);
    this.select.value = match ? match.file : (shapes.find((s) => s.name === "Standard") || shapes[0]).file;
    await this.load(this.select.value);
  }

  fail(error) {
    this.say(error instanceof ShapeError ? `This shape cannot be shown: ${error.message}` : `Could not load the shape: ${error.message}`);
  }

  async load(file) {
    const id = ++this.loadId;
    const shape = await fetchShape(file);
    if (id !== this.loadId) return; // a newer pick has overtaken this one
    this.runId++;
    this.shape = shape;
    this.disposeParts();
    this.buildParts();
    this.describe();
    this.frameCamera();
    this.setState("idle");
  }

  buildParts() {
    const cells = this.shape.cells;
    const group = new THREE.Group();
    const mats = {
      frame: new THREE.MeshStandardMaterial(),
      chevronIdle: new THREE.MeshStandardMaterial(),
      light: new THREE.MeshStandardMaterial(),
      portal: new THREE.MeshBasicMaterial({ transparent: true, opacity: 0.4, depthWrite: false }),
      // Opaque enough to tell from the portal as drawn (a delta-E of 25 or more under normal, red-weak and green-weak sight, both themes).
      woosh: new THREE.MeshBasicMaterial({ transparent: true, opacity: 0.6, depthWrite: false }),
      markers: {},
    };
    const parts = { group, mats, meshes: [] };
    const add = (mesh) => { group.add(mesh); parts.meshes.push(mesh); return mesh; };

    add(this.instanced(cells.filter((c) => c.block === "frame" && c.light === null), mats.frame));
    add(this.instanced(cells.filter((c) => c.block === "chevron" && c.light === null), mats.chevronIdle));

    // One mesh for every lit cell, coloured per instance as the steps come on.
    parts.lightCells = cells.filter((c) => c.light !== null);
    parts.lightMesh = add(this.instanced(parts.lightCells, mats.light));

    // Woosh cells sorted by step, so a step is a prefix and showing it is setting a count.
    const split = partitionCells(cells);
    parts.portal = add(this.instanced(split.portal, mats.portal));
    parts.waveCells = split.wave;
    parts.stayCells = split.stay;
    parts.wave = add(this.instanced(parts.waveCells, mats.woosh));
    // The wave's edges, so it is not told from the portal by hue alone; it shows as many cells as the wave does.
    mats.ghostWire = new THREE.MeshBasicMaterial({ wireframe: true, depthWrite: false });
    parts.waveWire = add(this.instanced(parts.waveCells, mats.ghostWire));
    parts.waveWire.frustumCulled = false;
    parts.waveWire.onBeforeRender = () => { parts.waveWire.count = parts.wave.count; };
    parts.stay = add(this.instanced(parts.stayCells, mats.portal));
    // Their counts start at 0, which a culling sphere taken then would keep.
    parts.wave.frustumCulled = false;
    parts.stay.frustumCulled = false;
    // Every woosh cell, whatever its base kind (an Ignored cell included), for the designer's "Woosh cells" view.
    // Smaller than the 0.92 cubes so it never shares a face with a frame, chevron or light cell, and crossed with
    // a wireframe so it is not told from them by hue alone.
    mats.ghost = new THREE.MeshBasicMaterial({ transparent: true, opacity: 0.6, depthWrite: false });
    parts.ghostCells = cells.filter((c) => c.woosh >= 1);
    this.ghostCube ??= new THREE.BoxGeometry(0.8, 0.8, 0.8);
    this.ghostShell ??= new THREE.BoxGeometry(0.98, 0.98, 0.98);
    parts.ghost = this.instanced(parts.ghostCells, mats.ghost);
    parts.ghostWire = this.instanced(parts.ghostCells, mats.ghostWire);
    // A woosh cell inside a frame, chevron or light block would be hidden by it, so those get their edges drawn on its outside.
    parts.ghostShell = this.instanced(parts.ghostCells.filter((c) => c.block !== "none"), mats.ghostWire);
    parts.ghost.geometry = this.ghostCube;
    parts.ghostWire.geometry = this.ghostCube;
    parts.ghostShell.geometry = this.ghostShell;
    for (const mesh of [parts.ghost, parts.ghostWire, parts.ghostShell]) {
      mesh.visible = false;
      add(mesh);
    }

    this.buildMarkers(parts, add);

    const b = this.bounds();
    const span = Math.max(this.shape.width, this.shape.layers) * 2 + 4;
    parts.grid = new THREE.GridHelper(span, span);
    parts.grid.position.set(b.center.x, b.min.y - 0.5, b.center.z);
    group.add(parts.grid);

    this.parts = parts;
    this.scene.add(group);
    this.applyPalette();
  }

  buildMarkers(parts, add) {
    const m = new THREE.Matrix4();
    for (const [key, spec] of Object.entries(MARKERS)) {
      const at = [];
      for (const c of this.shape.cells) {
        c.markers.forEach((k, i) => { if (k === key) at.push(this.pos(c).add(new THREE.Vector3(i * 0.3, 0, 0))); });
      }
      if (!at.length) continue;
      const mat = new THREE.MeshBasicMaterial({ color: spec.color, depthTest: false });
      parts.mats.markers[key] = mat;
      const mesh = new THREE.InstancedMesh(this.markerGeometries[spec.shape], mat, at.length);
      at.forEach((p, i) => mesh.setMatrixAt(i, m.setPosition(p)));
      mesh.renderOrder = 10;
      add(mesh);
    }
  }

  applyPalette() {
    if (!this.parts) return;
    const p = palette();
    const { mats, grid } = this.parts;
    mats.frame.color.set(p.frame);
    mats.chevronIdle.color.set(p.chevron);
    mats.portal.color.set(p.portal);
    mats.woosh.color.set(p.woosh);
    mats.ghostWire.color.set(p.woosh);
    grid.material.color.set(p.grid);
    this.paintLights();
    this.paintGhost();
    this.renderLegend();
    this.requestRender();
  }

  /** The colour the stage is drawn on, so the faintest ghost fades into the real background. */
  stageColor() {
    const css = this.stage ? getComputedStyle(this.stage).backgroundColor : "";
    const [r, g, b] = parseRgb(css, isDark() ? [39, 42, 53] : [245, 245, 245]);
    return new THREE.Color(`rgb(${r}, ${g}, ${b})`);
  }

  /** The woosh cells' colours: from half strength at the first band to the full colour at the last, as the grid tints them. */
  paintGhost() {
    const { ghost, ghostCells } = this.parts;
    if (!ghostCells.length) return;
    const orders = this.shape.wooshOrders;
    const full = new THREE.Color(palette().woosh);
    const stage = this.stageColor();
    ghostCells.forEach((c, i) => {
      const t = 0.5 + 0.5 * wooshTint(wooshStep(c.woosh, orders), orders.length);
      ghost.setColorAt(i, stage.clone().lerp(full, t));
    });
    if (ghost.instanceColor) ghost.instanceColor.needsUpdate = true;
  }

  /** The woosh cells show while the gate is at rest (idle, dialling, dialled); opening plays them in order instead. */
  syncGhost() {
    const { ghost, ghostWire, ghostShell } = this.parts;
    ghost.visible = Boolean(this.showWoosh) && this.state !== "opening" && this.state !== "open";
    ghostWire.visible = ghost.visible;
    ghostShell.visible = ghost.visible;
    this.requestRender();
  }

  paintLights() {
    const p = palette();
    const { lightCells, lightMesh } = this.parts;
    const lit = new THREE.Color(p.lit);
    const unlit = new THREE.Color(p.chevron);
    lightCells.forEach((c, i) => lightMesh.setColorAt(i, c.light <= this.litUpTo ? lit : unlit));
    if (lightMesh.instanceColor) lightMesh.instanceColor.needsUpdate = true;
    this.requestRender();
  }

  disposeParts() {
    if (!this.parts) return;
    this.scene.remove(this.parts.group);
    for (const mesh of this.parts.meshes) mesh.dispose();
    this.parts.grid.geometry.dispose();
    this.parts.grid.material.dispose();
    const { markers, ...own } = this.parts.mats;
    for (const m of [...Object.values(own), ...Object.values(markers)]) m.dispose();
    this.parts = null;
  }

  // --- states -------------------------------------------------------------------------------

  command(key) {
    if (!this.parts) return; // nothing is loaded yet
    if (key === "idle") this.setState("idle");
    else if (key === "dial") this.dial();
    else if (key === "open") this.open();
    else { this.setState("idle"); this.resetView(); }
  }

  setState(state, message) {
    this.runId++;
    this.state = state;
    this.litUpTo = state === "idle" ? 0 : Infinity;
    const open = state === "open";
    const { portal, wave, stay, waveCells, stayCells } = this.parts;
    portal.visible = open;
    wave.count = 0;
    stay.count = open ? stayCells.length : 0;
    this.paintLights();
    const names = { idle: "Idle: the gate is built and nothing is lit.", dialled: "Dialled: every chevron is lit.", open: "Open: the portal is filled." };
    this.syncGhost();
    this.say(message || names[state]);
    for (const [k, b] of Object.entries(this.buttons)) {
      if (k !== "reset") b.setAttribute("aria-pressed", String((k === "dial" && state === "dialled") || k === state));
    }
    this.requestRender();
  }

  say(text) {
    this.status.textContent = text;
  }

  wait(ticks) {
    return new Promise((resolve) => setTimeout(resolve, reduceMotion.matches ? 0 : Math.max(ticks * TICK_MS, MIN_STEP_MS)));
  }

  async dial() {
    this.setState("idle");
    const run = this.runId;
    const orders = this.shape.lightOrders;
    this.state = "dialling";
    for (const [index, order] of orders.entries()) {
      this.litUpTo = order;
      this.paintLights();
      this.say(`Dialling: chevron step ${index + 1} of ${orders.length} lit.`);
      await this.wait(this.shape.lightTicks ?? 3);
      if (run !== this.runId) return;
    }
    this.setState("dialled");
  }

  async open() {
    const run = ++this.runId;
    const { portal, wave, stay } = this.parts;
    const orders = this.shape.wooshOrders;
    this.litUpTo = Infinity;
    this.paintLights();
    this.state = "opening";
    this.syncGhost();
    this.say("Opening: the woosh is pushing the portal out.");
    portal.visible = true;
    stay.count = 0;
    wave.count = 0;
    this.requestRender();
    const step = (index) => {
      const order = orders[index];
      wave.count = index < 0 ? 0 : through(this.parts.waveCells, "woosh", order);
      stay.count = index < 0 ? 0 : through(this.parts.stayCells, "woosh", order);
      this.requestRender();
    };
    const ticks = this.shape.wooshTicks ?? 3;
    // The plugin plays order n at step n, so a woosh that starts at #3 waits two steps first.
    if (orders.length && orders[0] > 1) {
      await this.wait(ticks * (orders[0] - 1));
      if (run !== this.runId) return;
    }
    for (let i = 0; i < orders.length; i++) {
      step(i);
      await this.wait(ticks);
      if (run !== this.runId) return;
    }
    await this.wait(8);
    if (run !== this.runId) return;
    // The woosh retreats in reverse; the [P:W] cells stay as portal.
    for (let i = orders.length - 1; i >= 0; i--) {
      wave.count = i === 0 ? 0 : through(this.parts.waveCells, "woosh", orders[i - 1]);
      this.requestRender();
      await this.wait(ticks);
      if (run !== this.runId) return;
    }
    this.setState("open");
  }

  // --- text ---------------------------------------------------------------------------------

  describe() {
    const d = describeShape(this.shape);
    const s = this.shape;
    const plural = (n, one, many = `${one}s`) => `${n} ${n === 1 ? one : many}`;
    const ring = s.cells.filter((c) => c.block !== "none" || c.portal);
    const flat = new Set(ring.map((c) => c.y)).size === 1 && s.layers > 1 && s.width > 1;
    const lines = [
      `${d.name} is ${d.width} wide by ${d.height} high, in ${plural(d.layers, "layer")}. ` +
        (flat ? "It lies flat: the ring is laid out in plan, in a single row of every layer, and the woosh rises above it. " : "") +
        `It has ${plural(d.frame, "frame block")} and ${plural(d.chevrons, "chevron block")} that light in ${plural(d.lightSteps, "step")}` +
        (s.lightTicks ? `, ${s.lightTicks} ticks apart` : "") +
        `; ${plural(d.portal, "portal cell")}; and a woosh of ${plural(d.wooshCells, "cell")} in ${plural(d.wooshSteps, "step")}` +
        (s.wooshTicks ? `, ${s.wooshTicks} ticks apart` : "") + ".",
    ];
    const found = [];
    for (const key of Object.keys(MARKERS)) {
      const layers = [...new Set(s.cells.filter((c) => c.markers.includes(key)).map((c) => c.layer))];
      if (layers.length) found.push(`${MARKERS[key].label} (layer ${layers.join(", ")})`);
    }
    lines.push(`Markers: ${found.join("; ") || "none"}.`);
    this.info.replaceChildren(...lines.map((t) => el("p", {}, t)));
    this.canvas.setAttribute("aria-label",
      `3D view of ${d.name}: ${d.width} by ${d.height}, ${plural(d.layers, "layer")}. Arrow keys rotate, plus and minus zoom, 0 resets the view.`);
  }

  renderLegend() {
    const p = palette();
    // A glyph beside each swatch, so no entry is told from another by hue alone.
    const items = [
      ["Gate frame", p.frame, "■"], ["Chevron, unlit", p.chevron, "▣"], ["Chevron, lit", p.lit, "★"],
      ["Portal, when open", p.portal, "▒"], [this.wooshLabel ?? "Woosh, while opening", p.woosh, "░", "wxt-swatch-woosh"],
    ];
    const glyphs = { plate: "▬", sphere: "●", cone: "▲", cylinder: "▮", octa: "◆", cube: "■", tetra: "◢", ring: "◎" };
    const present = new Set(this.shape.cells.flatMap((c) => c.markers));
    for (const [key, spec] of Object.entries(MARKERS)) {
      if (present.has(key)) items.push([`${spec.label} (${key})`, spec.color, glyphs[spec.shape]]);
    }
    this.legend.replaceChildren(...items.map(([label, color, glyph, extra]) =>
      el("li", {}, el("span", { class: extra ? `wxt-swatch ${extra}` : "wxt-swatch", "aria-hidden": "true", style: `background-color:${hex(color)}` }), ` ${glyph} ${label}`)));
  }
}

async function main() {
  const root = document.getElementById("gate-viewer");
  if (!root || !(await loadLibs(root))) return;
  try {
    await new GateViewer(root).start();
  } catch (error) {
    root.replaceChildren(el("p", { class: "admonition warning", role: "alert" },
      `The 3D viewer could not start (${error.message}).`));
  }
}

main();
