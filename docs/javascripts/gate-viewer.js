// The gate shape viewer on the User manual page: reads the real .shape files copied to
// assets/shapes/ by scripts/copy-docs-shapes.sh and draws them with three.js.
import { parseShape, describeShape, ShapeError } from "./shape-parser.js";

// Loaded in main() so a failed CDN fetch can be reported inside the viewer box.
let THREE;
let OrbitControls;

const SHAPES_URL = new URL("../assets/shapes/", import.meta.url);
const TICK_MS = 50;
const MIN_STEP_MS = 250; // real timings are too quick to follow on a page
const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)");

const PALETTES = {
  light: { frame: 0x3b2f5c, chevron: 0xa23b3b, lit: 0xf59e0b, portal: 0x2f7bff, woosh: 0x00a6d6, grid: 0xbbbbbb },
  dark: { frame: 0x9a88c8, chevron: 0xc9605a, lit: 0xffd36b, portal: 0x58a6ff, woosh: 0x7fe3ff, grid: 0x555555 },
};

const MARKERS = {
  N: { label: "Name sign", color: 0xf1c40f, shape: "plate" },
  EP: { label: "Player entry point", color: 0x2ecc71, shape: "sphere" },
  EM: { label: "Minecart entry point", color: 0x1abc9c, shape: "cone" },
  A: { label: "Activation switch", color: 0xe67e22, shape: "cylinder" },
  IA: { label: "Iris switch", color: 0xe91e8c, shape: "octa" },
  D: { label: "Sign dialler", color: 0x9b59b6, shape: "plate" },
  RD: { label: "Redstone activation block", color: 0xe74c3c, shape: "cube" },
  RS: { label: "Redstone sign-cycle block", color: 0xc0392b, shape: "cube" },
  RA: { label: "Redstone output block", color: 0xff7675, shape: "cube" },
};

const el = (tag, attrs = {}, ...children) => {
  const node = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (k === "class") node.className = v;
    else node.setAttribute(k, v);
  }
  node.append(...children);
  return node;
};

const isDark = () => document.body.getAttribute("data-md-color-scheme") === "slate";
const palette = () => (isDark() ? PALETTES.dark : PALETTES.light);
const hex = (n) => `#${n.toString(16).padStart(6, "0")}`;

/** How many leading cells (sorted by order) belong to a step at or before `order`. */
const through = (sorted, key, order) => sorted.filter((c) => c[key] <= order).length;

class GateViewer {
  constructor(root) {
    this.root = root;
    this.runId = 0;
    this.loadId = 0;
    this.state = "idle";
    this.parts = null;
    this.litUpTo = 0;
    this.buildUi();
    this.buildScene();
    new MutationObserver(() => this.applyPalette()).observe(document.body,
      { attributes: true, attributeFilter: ["data-md-color-scheme"] });
    new ResizeObserver(() => this.resize()).observe(this.stage);
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
      tabindex: "0", role: "img",
      "aria-label": "3D view of the selected gate shape. Arrow keys rotate, plus and minus zoom, 0 resets the view.",
    });
    this.stage.append(this.canvas);
    this.status = el("p", { class: "wxt-status", role: "status", "aria-live": "polite" });
    this.help = el("p", { class: "wxt-help" },
      "Drag to orbit, scroll or pinch to zoom. With the 3D view focused: arrow keys rotate, + and − zoom, 0 resets the view. " +
      "The view starts from the side the dialling switch is on, as a player standing at the gate sees it.");
    this.info = el("div", { class: "wxt-info" });
    this.legend = el("ul", { class: "wxt-legend", "aria-label": "Legend" });
    this.root.replaceChildren(bar, this.stage, this.status, this.help, this.info,
      el("h3", {}, "Legend"), this.legend);
    this.select.addEventListener("change", () => this.load(this.select.value).catch((e) => this.fail(e)));
    this.canvas.addEventListener("keydown", (e) => this.key(e));
  }

  buildScene() {
    this.renderer = new THREE.WebGLRenderer({ canvas: this.canvas, antialias: true, alpha: true });
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    this.scene = new THREE.Scene();
    this.camera = new THREE.PerspectiveCamera(40, 1, 0.1, 500);
    this.scene.add(new THREE.HemisphereLight(0xffffff, 0x666677, 1.6));
    const sun = new THREE.DirectionalLight(0xffffff, 1.6);
    sun.position.set(4, 8, 6);
    this.scene.add(sun);
    this.controls = new OrbitControls(this.camera, this.canvas);
    this.controls.enableDamping = false;
    this.controls.addEventListener("change", () => this.requestRender());
    // OrbitControls sets "none"; a vertical swipe should still scroll the page on a phone.
    this.canvas.style.touchAction = "pan-y";
    // Geometries are shared by every shape; only materials and meshes are made per shape.
    this.cube = new THREE.BoxGeometry(0.92, 0.92, 0.92);
    this.markerGeometries = {
      plate: new THREE.BoxGeometry(0.8, 0.5, 0.15),
      sphere: new THREE.SphereGeometry(0.28, 16, 12),
      cone: new THREE.ConeGeometry(0.3, 0.6, 12),
      cylinder: new THREE.CylinderGeometry(0.2, 0.2, 0.5, 12),
      octa: new THREE.OctahedronGeometry(0.33),
      cube: new THREE.BoxGeometry(0.4, 0.4, 0.4),
    };
  }

  async start() {
    const res = await fetch(new URL("manifest.json", SHAPES_URL));
    if (!res.ok) throw new Error(`the shape list answered ${res.status}`);
    const manifest = await res.json();
    for (const s of manifest.shapes) this.select.append(el("option", { value: s.file }, s.name));
    const wanted = decodeURIComponent(location.hash.slice(1));
    const match = manifest.shapes.find((s) => s.name === wanted);
    this.select.value = match ? match.file : (manifest.shapes.find((s) => s.name === "Standard") || manifest.shapes[0]).file;
    await this.load(this.select.value);
  }

  fail(error) {
    this.say(error instanceof ShapeError ? `This shape cannot be shown: ${error.message}` : `Could not load the shape: ${error.message}`);
  }

  async load(file) {
    const id = ++this.loadId;
    const res = await fetch(new URL(file, SHAPES_URL));
    if (!res.ok) throw new Error(`${file} answered ${res.status}`);
    const text = await res.text();
    if (id !== this.loadId) return; // a newer pick has overtaken this one
    const shape = parseShape(text);
    this.runId++;
    this.shape = shape;
    this.disposeParts();
    this.buildParts();
    this.describe();
    this.frameCamera();
    this.setState("idle");
  }

  // Cell position in the scene. The file reads left to right as a player sees it standing on the
  // high-layer side (where the activation switch faces), so that side is the camera's: high
  // layers are near, layer 1 is far, column 0 is on the left.
  pos(c) {
    return new THREE.Vector3(c.col, c.y, c.layer);
  }

  instanced(cells, material) {
    const mesh = new THREE.InstancedMesh(this.cube, material, Math.max(cells.length, 1));
    const m = new THREE.Matrix4();
    cells.forEach((c, i) => mesh.setMatrixAt(i, m.setPosition(this.pos(c))));
    mesh.count = cells.length;
    mesh.instanceMatrix.needsUpdate = true;
    return mesh;
  }

  buildParts() {
    const cells = this.shape.cells;
    const group = new THREE.Group();
    const byOrder = (key, test) => cells.filter(test).sort((a, b) => a[key] - b[key]);
    const mats = {
      frame: new THREE.MeshStandardMaterial(),
      chevronIdle: new THREE.MeshStandardMaterial(),
      light: new THREE.MeshStandardMaterial(),
      portal: new THREE.MeshBasicMaterial({ transparent: true, opacity: 0.4, depthWrite: false }),
      woosh: new THREE.MeshBasicMaterial({ transparent: true, opacity: 0.16, depthWrite: false }),
      markers: {},
    };
    const parts = { group, mats, meshes: [] };
    const add = (mesh) => { group.add(mesh); parts.meshes.push(mesh); return mesh; };

    add(this.instanced(cells.filter((c) => c.block === "frame" && c.light === null), mats.frame));
    add(this.instanced(cells.filter((c) => c.block === "chevron" && c.light === null), mats.chevronIdle));

    // One mesh for every lit cell, coloured per instance as the steps come on.
    parts.lightCells = cells.filter((c) => c.light !== null);
    parts.lightMesh = add(this.instanced(parts.lightCells, mats.light));

    parts.portal = add(this.instanced(cells.filter((c) => c.portal && c.woosh === null), mats.portal));
    // Woosh cells sorted by step, so a step is a prefix and showing it is setting a count.
    parts.waveCells = byOrder("woosh", (c) => c.woosh !== null && !c.portal);
    parts.stayCells = byOrder("woosh", (c) => c.woosh !== null && c.portal);
    parts.wave = add(this.instanced(parts.waveCells, mats.woosh));
    parts.stay = add(this.instanced(parts.stayCells, mats.portal));

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

  bounds() {
    const box = new THREE.Box3();
    for (const c of this.shape.cells) {
      if (c.block !== "none" || c.portal || c.woosh !== null) box.expandByPoint(this.pos(c));
    }
    return { min: box.min, max: box.max, center: box.getCenter(new THREE.Vector3()), size: box.getSize(new THREE.Vector3()) };
  }

  frameCamera() {
    const b = this.bounds();
    const radius = Math.max(b.size.length() / 2, 2);
    this.home = { target: b.center.clone(), offset: new THREE.Vector3(0.55, 0.45, 1).normalize().multiplyScalar(radius * 3) };
    this.resetView();
  }

  resetView() {
    this.controls.target.copy(this.home.target);
    this.camera.position.copy(this.home.target).add(this.home.offset);
    this.controls.update();
    this.requestRender();
  }

  key(e) {
    const offset = this.camera.position.clone().sub(this.controls.target);
    const sph = new THREE.Spherical().setFromVector3(offset);
    const step = 0.15;
    switch (e.key) {
      case "ArrowLeft": sph.theta -= step; break;
      case "ArrowRight": sph.theta += step; break;
      case "ArrowUp": sph.phi = Math.max(0.1, sph.phi - step); break;
      case "ArrowDown": sph.phi = Math.min(Math.PI - 0.1, sph.phi + step); break;
      case "+": case "=": sph.radius *= 0.85; break;
      case "-": case "_": sph.radius /= 0.85; break;
      case "0": this.resetView(); e.preventDefault(); return;
      default: return;
    }
    e.preventDefault();
    this.camera.position.copy(this.controls.target).add(offset.setFromSpherical(sph));
    this.controls.update();
    this.requestRender();
  }

  resize() {
    const w = this.stage.clientWidth;
    const h = this.stage.clientHeight;
    if (!w || !h) return;
    this.renderer.setSize(w, h, false);
    this.camera.aspect = w / h;
    this.camera.updateProjectionMatrix();
    this.requestRender();
  }

  requestRender() {
    if (this.queued) return;
    this.queued = true;
    requestAnimationFrame(() => {
      this.queued = false;
      this.renderer.render(this.scene, this.camera);
    });
  }

  applyPalette() {
    if (!this.parts) return;
    const p = palette();
    const { mats, grid } = this.parts;
    mats.frame.color.set(p.frame);
    mats.chevronIdle.color.set(p.chevron);
    mats.portal.color.set(p.portal);
    mats.woosh.color.set(p.woosh);
    grid.material.color.set(p.grid);
    this.paintLights();
    this.renderLegend();
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
      await this.wait(this.shape.lightTicks ?? 10);
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
    const items = [
      ["Gate frame", p.frame], ["Chevron, unlit", p.chevron], ["Chevron, lit", p.lit],
      ["Portal, when open", p.portal], ["Woosh, while opening", p.woosh],
    ];
    const present = new Set(this.shape.cells.flatMap((c) => c.markers));
    for (const [key, spec] of Object.entries(MARKERS)) if (present.has(key)) items.push([spec.label, spec.color]);
    this.legend.replaceChildren(...items.map(([label, color]) =>
      el("li", {}, el("span", { class: "wxt-swatch", "aria-hidden": "true", style: `background:${hex(color)}` }), ` ${label}`)));
  }
}

async function main() {
  const root = document.getElementById("gate-viewer");
  if (!root) return;
  try {
    THREE = await import("three");
    ({ OrbitControls } = await import("three/addons/controls/OrbitControls.js"));
  } catch (error) {
    root.replaceChildren(el("p", { class: "admonition warning", role: "alert" },
      "The 3D viewer could not load its drawing library (three.js from the jsDelivr CDN), so there is nothing to show here. " +
      "Check your connection or a content blocker and reload; every shape is also drawn in the gate internals pages."));
    return;
  }
  try {
    await new GateViewer(root).start();
  } catch (error) {
    root.replaceChildren(el("p", { class: "admonition warning", role: "alert" },
      `The 3D viewer could not start (${error.message}).`));
  }
}

main();
