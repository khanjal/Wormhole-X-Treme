// What the User manual's 3D viewers share: the three.js loader, the theme, the scene and camera,
// and the shape loader. The pages' own scripts (gate, iris, dialling) build on it.
import { parseShape } from "./shape-parser.js";

// Bound by loadLibs(); a viewer that imports them reads them live once it has run.
export let THREE;
export let OrbitControls;

export const SHAPES_URL = new URL("../assets/shapes/", import.meta.url);
export const TICK_MS = 50;
export const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)");

export const PALETTES = {
  light: {
    frame: 0x3b2f5c, chevron: 0xa23b3b, lit: 0xf59e0b, spin: 0x0891b2, portal: 0x2f7bff, woosh: 0x1a9a3e,
    iris: 0x6b7280, fresh: 0xe11d74, grid: 0xbbbbbb,
  },
  dark: {
    frame: 0x9a88c8, chevron: 0xc9605a, lit: 0xffd36b, spin: 0x7fe3ff, portal: 0x58a6ff, woosh: 0x4cd37a,
    iris: 0x9ca3af, fresh: 0xff7ab8, grid: 0x555555,
  },
};

export const el = (tag, attrs = {}, ...children) => {
  const node = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (k === "class") node.className = v;
    else node.setAttribute(k, v);
  }
  node.append(...children);
  return node;
};

export const isDark = () => document.body.getAttribute("data-md-color-scheme") === "slate";
export const palette = () => (isDark() ? PALETTES.dark : PALETTES.light);
export const hex = (n) => `#${n.toString(16).padStart(6, "0")}`;

let legendCount = 0;

/**
 * The 3D stage with its legend: beside the stage when there is room for it, otherwise a Legend
 * button over a corner of the stage that opens the list (Escape closes it again).
 */
export function stageWithLegend(stage, legend) {
  const id = `wxt-legend-${++legendCount}`;
  const toggle = el("button", { type: "button", class: "md-button wxt-legend-toggle", "aria-expanded": "false", "aria-controls": id }, "Legend");
  const body = el("div", { class: "wxt-legend-body", id, tabindex: "0", role: "region", "aria-label": "Legend panel" },
    el("h3", { class: "wxt-legend-title" }, "Legend"), legend);
  const panel = el("div", { class: "wxt-legend-panel" }, toggle, body);
  const box = el("div", { class: "wxt-stage-box" }, el("div", { class: "wxt-stage-row" }, stage, panel));
  const setOpen = (open) => {
    panel.classList.toggle("wxt-open", open);
    toggle.setAttribute("aria-expanded", String(open));
  };
  // The button is only there in the narrow layout; when the layout turns wide an open legend is just the panel, so forget it.
  const narrow = () => toggle.getClientRects().length > 0;
  toggle.addEventListener("click", () => setOpen(!panel.classList.contains("wxt-open")));
  if (typeof ResizeObserver === "function") new ResizeObserver(() => { if (!narrow()) setOpen(false); }).observe(box);
  box.addEventListener("keydown", (e) => {
    if (e.key !== "Escape" || !panel.classList.contains("wxt-open") || !narrow()) return;
    e.stopPropagation();
    e.preventDefault();
    setOpen(false);
    if (panel.contains(document.activeElement)) toggle.focus();
  });
  return box;
}

/** Loads three.js; false (after saying so in the box) when the CDN cannot be reached. */
export async function loadLibs(root) {
  try {
    THREE = await import("three");
    ({ OrbitControls } = await import("three/addons/controls/OrbitControls.js"));
    return true;
  } catch {
    root.replaceChildren(el("p", { class: "admonition warning", role: "alert" },
      "The 3D viewer could not load its drawing library (three.js from the jsDelivr CDN), so there is nothing to show here. " +
      "Check your connection or a content blocker and reload; every shape is also drawn in the gate internals pages."));
    return false;
  }
}

/** The names and files of the shipped shapes, from the manifest the docs build copies beside them. */
export async function fetchManifest() {
  const res = await fetch(new URL("manifest.json", SHAPES_URL));
  if (!res.ok) throw new Error(`the shape list answered ${res.status}`);
  return (await res.json()).shapes;
}

/** One shape file, parsed. */
export async function fetchShape(file) {
  const res = await fetch(new URL(file, SHAPES_URL));
  if (!res.ok) throw new Error(`${file} answered ${res.status}`);
  return parseShape(await res.text());
}

/**
 * The scene, camera and orbit controls over a canvas. A subclass sets `canvas`, `stage` and
 * `shape` before use, and keeps its own meshes.
 */
export class SceneBase {
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
    // One geometry for every cube of every shape; only materials and meshes are made per shape.
    this.cube = new THREE.BoxGeometry(0.92, 0.92, 0.92);
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
    if (!this.home) return;
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

  /** Redraws when the site switches between its light and dark scheme. */
  watch(apply) {
    new MutationObserver(apply).observe(document.body,
      { attributes: true, attributeFilter: ["data-md-color-scheme"] });
    new ResizeObserver(() => this.resize()).observe(this.stage);
  }
}
