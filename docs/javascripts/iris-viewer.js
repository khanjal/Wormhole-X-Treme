// The iris viewer on the User manual: closes and opens the real opening of a shipped shape in each
// of the five animation styles, with the step order ported from IrisSweep (see iris-sweep.js).
import { THREE, el, loadLibs, palette, TICK_MS } from "./scene-kit.js";
import { ManualViewer } from "./manual-viewer.js";
import {
  STYLES, STEP_TICKS, STEP_CAP, irisCells, openingOrder, closingOrder, irisStateAt,
} from "./iris-sweep.js";

const STYLE_TEXT = {
  sweep: "Rings, in from the rim and out from the middle.",
  spiral: "A wedge turning round the middle and working outwards.",
  rows: "Rows, in from the top and bottom at once.",
  columns: "Columns, in from both sides at once.",
  instant: "No animation: the iris is simply there.",
};

// What each group's iris is made of. The colours are a guide, not the textures.
const IRIS_MATERIALS = {
  Standard: { label: "Standard (stone)", color: 0x7d7d7d, opacity: 1 },
  Atlantis: { label: "Atlantis (yellow stained glass)", color: 0xe0c040, opacity: 0.65 },
  Universe: { label: "Universe (white stained glass)", color: 0xf4f4f4, opacity: 0.6 },
  MilkyWay: { label: "MilkyWay (iron block)", color: 0xd6d6d6, opacity: 1 },
};

class IrisViewer extends ManualViewer {
  constructor(root) {
    super(root, {
      noun: "a gate's iris closing and opening",
      // Upright gates only: a flat one has an opening several layers deep and no iris sweep to show.
      accept: (shape) => new Set(irisCells(shape).map((c) => c.layer)).size === 1,
      choices: [
        { key: "style", label: "Style", options: STYLES.map((s) => [s, s]) },
        { key: "material", label: "Iris", options: Object.entries(IRIS_MATERIALS).map(([k, v]) => [k, v.label]) },
      ],
    });
  }

  buildParts() {
    const group = new THREE.Group();
    const materials = {
      frame: new THREE.MeshStandardMaterial(),
      iris: new THREE.MeshStandardMaterial({ color: 0xffffff }),
    };
    const cells = irisCells(this.shape);
    const frame = this.instanced(this.shape.cells.filter((c) => c.block !== "none" && !c.portal), materials.frame);
    const iris = this.instanced(cells, materials.iris);
    iris.frustumCulled = false; // its count changes, which the culling sphere is not told
    group.add(frame, iris);
    return { group, meshes: [frame, iris], materials: Object.values(materials), mats: materials, iris, cells, grid: this.addGrid(group) };
  }

  makePlan() {
    const { cells, iris } = this.parts;
    const style = this.choice("style");
    const steps = closingOrder(cells, style);
    const flat = steps.flat();
    const m = new THREE.Matrix4();
    // Instances are laid in closing order, so what is covered at any moment is a prefix of them.
    flat.forEach((cell, i) => iris.setMatrixAt(i, m.setPosition(this.pos(cells[cell]))));
    iris.instanceMatrix.needsUpdate = true;
    const upTo = [0];
    for (const step of steps) upTo.push(upTo[upTo.length - 1] + step.length);
    const natural = openingOrder(cells, style, 0).length;
    return { style, steps, flat, upTo, natural, total: irisStateAt(steps.length, 0).total };
  }

  paint(p) {
    const { mats } = this.parts;
    const material = IRIS_MATERIALS[this.choice("material")];
    mats.frame.color.set(p.frame);
    mats.iris.transparent = material.opacity < 1;
    mats.iris.opacity = material.opacity;
    mats.iris.depthWrite = material.opacity >= 1;
    mats.iris.needsUpdate = true;
    this.parts.grid.material.color.set(p.grid);
    this.irisColor = new THREE.Color(material.color);
    this.freshColor = new THREE.Color(p.fresh);
  }

  stateAt(tick) {
    return irisStateAt(this.plan.steps.length, tick);
  }

  words(tick) {
    if (!this.plan) return "";
    const s = this.stateAt(tick);
    const n = this.plan.steps.length;
    const cells = this.plan.upTo[s.covered];
    const of = `${cells} of ${this.parts.cells.length} cells covered`;
    switch (s.phase) {
      case "closing": return `Closing: step ${s.step} of ${n}, ${of}.`;
      case "closed": return `Shut: every cell of the opening is covered (${of}).`;
      case "opening": return `Opening: step ${s.step} of ${n}, ${of}.`;
      default: return "Open: the iris has drawn back and the opening is clear.";
    }
  }

  render(tick) {
    if (!this.plan) return;
    const s = this.stateAt(tick);
    const { iris } = this.parts;
    const count = this.plan.upTo[s.covered];
    iris.count = count;
    // The ring that has just arrived stands out, so the order can be followed.
    const from = s.arriving ? this.plan.upTo[s.covered - 1] : count;
    for (let i = 0; i < count; i++) iris.setColorAt(i, i >= from ? this.freshColor : this.irisColor);
    if (iris.instanceColor) iris.instanceColor.needsUpdate = true;
    this.announce(tick, tick === 0 ? "start" : (s.phase === "closed" || s.phase === "open") ? s.phase : null);
    this.requestRender();
  }

  describe() {
    const { cells } = this.parts;
    const { style, steps, natural } = this.plan;
    const plural = (n, one, many = `${one}s`) => `${n} ${n === 1 ? one : many}`;
    const merged = natural > STEP_CAP;
    const ticks = (steps.length - 1) * STEP_TICKS;
    const lines = [
      `${this.shape.name}'s opening has ${plural(cells.length, "cell")}. In the ${style} style it crosses in ${plural(steps.length, "step")}` +
        (steps.length > 1 ? `, a step every ${STEP_TICKS} ticks, so ${ticks} ticks (${(ticks * TICK_MS / 1000).toFixed(1)} s) from the first to the last.` : ", so there is nothing to wait for.") +
        (merged ? ` It would take ${natural} steps, so neighbours are merged into ${STEP_CAP} bands, and each band arrives together.` : ""),
      `${STYLE_TEXT[style]} Closing, the first step is the outermost; opening runs the same steps backwards.`,
    ];
    this.info.replaceChildren(...lines.map((t) => el("p", {}, t)));
    this.renderTable();
  }

  /** The step each cell of the opening closes in, as a table: the same picture for anyone not using the 3D view. */
  renderTable() {
    const { cells } = this.parts;
    const { steps } = this.plan;
    const stepOf = new Map();
    steps.forEach((step, n) => step.forEach((i) => stepOf.set(`${cells[i].col},${cells[i].y}`, n + 1)));
    const cols = [...new Set(cells.map((c) => c.col))].sort((a, b) => a - b);
    const rows = [...new Set(cells.map((c) => c.y))].sort((a, b) => b - a);
    const body = rows.map((y) => el("tr", {}, ...cols.map((x) => {
      const n = stepOf.get(`${x},${y}`);
      return el("td", n ? {} : { class: "wxt-gap" }, n ? String(n) : "");
    })));
    this.extra.replaceChildren(
      el("h3", {}, "Closing step of each cell"),
      el("p", { class: "wxt-help" }, "The number is the step that covers the cell when the iris closes, as seen from the activation switch's side. Opening runs the numbers from the highest down."),
      el("div", { class: "wxt-table-wrap", tabindex: "0", role: "region", "aria-label": "Closing step of each cell, scrollable" }, el("table", { class: "wxt-steps", "aria-label": "Closing step of each cell of the opening" }, el("tbody", {}, ...body))));
  }

  legendItems() {
    const p = palette();
    const material = IRIS_MATERIALS[this.choice("material")];
    return [
      ["Gate frame", p.frame, "■"], [`Iris, ${material.label}`, material.color, "▒"], ["Step that has just arrived, while closing", p.fresh, "◆"],
    ];
  }
}

async function main() {
  const root = document.getElementById("iris-viewer");
  if (!root || !(await loadLibs(root))) return;
  try {
    await new IrisViewer(root).start();
  } catch (error) {
    root.replaceChildren(el("p", { class: "admonition warning", role: "alert" }, `The iris viewer could not start (${error.message}).`));
  }
}

main();
