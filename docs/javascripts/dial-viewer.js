// The dialling viewer on the User manual: plays a dial on a shipped shape with each ring-light
// pattern, chevrons locking in their :L order and then the woosh in its :W order. The timing and
// the light come from dial-spin.js, a port of DialSpin and StargateAnimator.
import { THREE, el, loadLibs, palette, TICK_MS } from "./scene-kit.js";
import { ManualViewer } from "./manual-viewer.js";
import {
  PATTERNS, LOCAL_CHEVRONS, OTHER_WORLD_CHEVRON, LAST_CHEVRON_PAUSE_TICKS,
  DialSpin, ringCells, dialSchedule, dialStateAt,
} from "./dial-spin.js";

const PATTERN_TEXT = {
  none: "No ring light: the chevrons lock in order, one every light interval.",
  top: "Half the ring to the top chevron before each lock, turning the other way each glyph; the light rests on the top for half a second before the ring turns again.",
  chevron: "From the top anticlockwise to the first chevron, then from each locked chevron to the next, a glyph's width at a time over the frame.",
  lap: "A whole turn clockwise before each lock, landing on the chevron: round and round.",
  fill: "As pegasus, lighting the ring behind the light as it goes.",
  pegasus: "As an Atlantis gate dials: the light runs half the ring and lands on the chevron, turning the other way each glyph.",
  chase: "A whole turn anticlockwise from chevron 1 back to it, then from each locked chevron to the next, clockwise to chevron 2 and turning the other way each time.",
  universe: "As Destiny's gate dials: the whole ring turns about a full turn for each glyph, the other way each time, and the locked chevrons ride round with it until the top one locks.",
  overshoot: "As pegasus, running a little past the chevron and backing onto it.",
};

const chevronName = (n) => (n === LOCAL_CHEVRONS ? "the top chevron" : n === OTHER_WORLD_CHEVRON ? "the eighth chevron, at the bottom" : `chevron ${n}`);
const seconds = (ticks) => (ticks * TICK_MS / 1000).toFixed(1);

class DialViewer extends ManualViewer {
  constructor(root) {
    super(root, {
      noun: "a gate dialling",
      accept: () => true,
      choices: [
        { key: "pattern", label: "Ring light", options: PATTERNS.map((p) => [p, p === "none" ? "none (default)" : p]) },
        { key: "world", label: "Dial", options: [["local", "within this world"], ["other", "to another world"]] },
      ],
    });
  }

  buildParts() {
    const group = new THREE.Group();
    const materials = {
      body: new THREE.MeshStandardMaterial({ color: 0xffffff }),
      portal: new THREE.MeshBasicMaterial({ transparent: true, opacity: 0.4, depthWrite: false }),
      woosh: new THREE.MeshBasicMaterial({ transparent: true, opacity: 0.16, depthWrite: false }),
    };
    const cells = this.shape.cells;
    const bodyCells = cells.filter((c) => c.block !== "none");
    const byOrder = (test) => cells.filter(test).sort((a, b) => a.woosh - b.woosh);
    const waveCells = byOrder((c) => c.woosh !== null && !c.portal);
    const stayCells = byOrder((c) => c.woosh !== null && c.portal);
    const body = this.instanced(bodyCells, materials.body);
    const portal = this.instanced(cells.filter((c) => c.portal && c.woosh === null), materials.portal);
    const wave = this.instanced(waveCells, materials.woosh);
    const stay = this.instanced(stayCells, materials.portal);
    for (const mesh of [body, portal, wave, stay]) group.add(mesh);
    // Where each cell of the ring is in the body mesh, so the light can be painted on it.
    const place = new Map(bodyCells.map((c, i) => [`${c.col},${c.y},${c.layer}`, i]));
    const spin = DialSpin.of(ringCells(this.shape));
    const ringAt = spin ? spin.ring.map((c) => place.get(`${c.col},${c.y},${c.layer}`)) : [];
    return {
      group, meshes: [body, portal, wave, stay], materials: Object.values(materials), mats: materials,
      body, portal, wave, stay, bodyCells, waveCells, stayCells, spin, ringAt, grid: this.addGrid(group),
    };
  }

  makePlan() {
    const { spin } = this.parts;
    const pattern = this.choice("pattern");
    const schedule = dialSchedule(this.shape, spin, pattern, this.choice("world") === "other");
    return { pattern, schedule, total: schedule.total };
  }

  paint(p) {
    const { mats, grid } = this.parts;
    mats.portal.color.set(p.portal);
    mats.woosh.color.set(p.woosh);
    grid.material.color.set(p.grid);
    this.colors = {
      frame: new THREE.Color(p.frame), idle: new THREE.Color(p.chevron), lit: new THREE.Color(p.lit), spin: new THREE.Color(p.spin),
    };
  }

  stateAt(tick) {
    const { pattern, schedule } = this.plan;
    return dialStateAt(schedule, this.parts.spin, pattern, tick);
  }

  words(tick) {
    if (!this.plan) return "";
    const s = this.stateAt(tick);
    const { schedule, pattern } = this.plan;
    switch (s.phase) {
      case "turning": return `Turning: the ring light is on its way to ${chevronName(s.glyph)} (${pattern}).`;
      case "locked": return s.locked === schedule.last
        ? `${chevronName(s.locked)} locked: all ${schedule.last} chevrons are locked.`
        : `Chevron ${s.locked} of ${schedule.last} locked.`;
      case "holding": return `Holding: all ${schedule.last} chevrons are locked, and the gate waits two seconds before the wormhole forms.`;
      case "woosh": return "The woosh: the kawoosh pushes out, then draws back.";
      case "open": return "Open: the wormhole has formed.";
      default: return "Dialling.";
    }
  }

  render(tick) {
    if (!this.plan) return;
    const s = this.stateAt(tick);
    const { body, portal, wave, stay, bodyCells, waveCells, stayCells, ringAt } = this.parts;
    const { frame, idle, lit, spin } = this.colors;
    const ringLit = new Set([...s.ring].map((i) => ringAt[i]));
    bodyCells.forEach((c, i) => {
      let color = c.light !== null || c.block === "chevron" ? idle : frame;
      if (ringLit.has(i)) color = spin;
      if (c.light !== null && c.light <= s.litInPlace) color = lit;
      body.setColorAt(i, color);
    });
    if (body.instanceColor) body.instanceColor.needsUpdate = true;
    const orders = this.shape.wooshOrders;
    const through = (cells, n) => (n <= 0 ? 0 : cells.filter((c) => c.woosh <= orders[n - 1]).length);
    portal.visible = s.open;
    stay.count = s.open ? stayCells.length : through(stayCells, s.woosh);
    wave.count = s.open ? 0 : through(waveCells, s.woosh);
    const key = tick === 0 ? "start" : s.phase === "locked" ? `lock${s.locked}` : (s.phase === "holding" || s.phase === "open") ? s.phase : null;
    this.announce(tick, key);
    this.requestRender();
  }

  describe() {
    const { schedule, pattern } = this.plan;
    const { spin } = this.parts;
    const chevrons = this.shape.cells.filter((c) => c.light !== null).length;
    const plural = (n, one, many = `${one}s`) => `${n} ${n === 1 ? one : many}`;
    const lines = [
      `${this.shape.name} has ${plural(chevrons, "chevron block")} lighting in ${plural(this.shape.lightOrders.length, "step")}, ` +
        `${schedule.interval} ticks apart. This dial locks ${plural(schedule.last, "chevron")}, in the order of the number after :L, ` +
        `and the wormhole forms ${seconds(schedule.total)} s after the dial starts (${schedule.total} ticks, the woosh included).`,
      PATTERN_TEXT[pattern],
    ];
    if (pattern !== "none" && !spin) {
      lines.push(`${this.shape.name} has no top chevron (:L#7), so its ring has nothing to turn to: it dials as none does.`);
    } else if (spin && pattern !== "none") {
      lines.push(`The light runs round the ${spin.size} frame cells of the ring, clockwise from the top chevron; the first chevron locks after ${schedule.glyphs[0].frames} ticks of turning.`);
    }
    this.info.replaceChildren(...lines.map((t) => el("p", {}, t)));
    this.renderSchedule();
  }

  /** When each chevron locks, as a table: the same story for anyone not using the 3D view. */
  renderSchedule() {
    const { schedule } = this.plan;
    const rows = schedule.glyphs.map((g) => el("tr", {},
      el("td", {}, chevronName(g.glyph)), el("td", {}, `${g.lockAt} (${seconds(g.lockAt)} s)`)));
    rows.push(el("tr", {}, el("td", {}, "Woosh begins"), el("td", {}, `${schedule.wooshStart} (${seconds(schedule.wooshStart)} s), ${LAST_CHEVRON_PAUSE_TICKS} ticks after the last lock`)));
    rows.push(el("tr", {}, el("td", {}, "Wormhole open"), el("td", {}, `${schedule.settleAt} (${seconds(schedule.settleAt)} s)`)));
    this.extra.replaceChildren(
      el("h3", {}, "When it happens"),
      el("table", { class: "wxt-times" },
        el("thead", {}, el("tr", {}, el("th", { scope: "col" }, "Event"), el("th", { scope: "col" }, "Tick from the start"))),
        el("tbody", {}, ...rows)));
  }

  legendItems() {
    const p = palette();
    return [
      ["Gate frame", p.frame, "■"], ["Chevron, not locked", p.chevron, "▣"], ["Chevron, locked", p.lit, "★"],
      ["Ring light", p.spin, "◆"], ["Portal, when open", p.portal, "▒"], ["Woosh, while opening", p.woosh, "░"],
    ];
  }
}

async function main() {
  const root = document.getElementById("dial-viewer");
  if (!root || !(await loadLibs(root))) return;
  try {
    await new DialViewer(root).start();
  } catch (error) {
    root.replaceChildren(el("p", { class: "admonition warning", role: "alert" }, `The dialling viewer could not start (${error.message}).`));
  }
}

main();
