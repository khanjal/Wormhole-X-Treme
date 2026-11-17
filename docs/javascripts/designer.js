// The gate designer on the User manual page: edit a shape layer by layer beside the 3D view, check
// it as the plugin would, and download or upload .shape files. Everything stays in the browser:
// nothing is sent anywhere, and file text only ever goes to the parser and into textContent.
import { parseShape, ShapeError, LIMITS } from "./shape-parser.js";
import { THREE, el, hex, loadLibs, fetchManifest, fetchShape, palette, stageWithLegend } from "./scene-kit.js";
import { GateViewer } from "./gate-viewer.js";
import * as E from "./shape-edit.js";
import { checkShape } from "./shape-check.js";
import { writeShape } from "./shape-writer.js";
import { wooshStep, wooshTint, wooshCue } from "./woosh-cue.js";
import {
  ZOOM, SPLIT, clampZoom, stepZoom, fitZoom, wheelZoom, wheelPixels, clampSplit, splitBounds, parseViewState, viewStateText,
} from "./designer-layout.js";

const DRAFT_KEY = "wxt-gate-designer-draft";
const VIEW_KEY = "wxt-gate-designer-view";
const FOCUSABLE = 'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), summary, [tabindex]:not([tabindex="-1"])';
const STACK_PX = 640;
const NARROW_PX = { main: 160, dock: 140 }; // smallest sizes when the dock sits below the grid
const WIDE_PX = { main: 220, dock: 240 }; // and when it sits beside it
const BRUSHES = [["select", "Select only"], ["I", "I  Ignored"], ["S", "S  Stargate material"], ["P", "P  Portal"], ["C", "C  Chevron material"]];
const MARKERS = [
  ["N", "Name sign"], ["EP", "Player entry"], ["EM", "Minecart entry"], ["A", "Activation switch"], ["D", "Dial sign"],
  ["IA", "Iris switch"], ["RD", "Redstone: activate"], ["RS", "Redstone: cycle sign"], ["RA", "Redstone: gate active"],
];
const MATERIALS = [
  ["PORTAL_MATERIAL", "Portal"], ["IRIS_MATERIAL", "Iris"], ["STARGATE_MATERIAL", "Stargate"], ["ACTIVE_MATERIAL", "Active (lit chevron)"],
  ["CHEVRON_MATERIAL", "Chevron"], ["SIGN_MATERIAL", "Sign"],
];
const SUMMARY_GAP_MS = 1500;
const SAVE_DELAY_MS = 400;
const SLOW_DELAY_MS = 250;

/** Browser storage, where every touch may throw (private windows, blocked site data). */
const store = {
  get() { try { return localStorage.getItem(DRAFT_KEY); } catch { return null; } },
  set(text) { try { localStorage.setItem(DRAFT_KEY, text); return true; } catch { return false; } },
  backUp(raw) { try { return E.backUpUnreadable(localStorage, DRAFT_KEY, raw); } catch { return null; } },
  remove() { try { localStorage.removeItem(DRAFT_KEY); } catch { /* nothing was kept */ } },
};

/** The view choices (cell size, split, which panels are open), kept the same careful way. */
const viewStore = {
  get() { try { return localStorage.getItem(VIEW_KEY); } catch { return null; } },
  set(text) { try { localStorage.setItem(VIEW_KEY, text); } catch { /* the choice just is not remembered */ } },
};

/** The shipped gate viewer, minus its shape picker, fed from the editor. */
class DesignerView extends GateViewer {
  buildUi() {
    this.buttons = {};
    const bar = el("div", { class: "wxt-bar", role: "group", "aria-label": "Preview the draft" });
    for (const [key, label] of [["idle", "Idle"], ["dial", "Dial"], ["open", "Open"], ["reset", "Reset view"]]) {
      const b = el("button", { type: "button", class: "md-button" }, label);
      b.addEventListener("click", () => this.command(key));
      this.buttons[key] = b;
      bar.append(b);
    }
    // Woosh cells are often Ignored in the shape, so they are drawn only when asked for (and play in order on Open).
    this.showWoosh = true;
    this.wooshToggle = el("input", { type: "checkbox", id: "wxt-show-woosh", checked: "" });
    this.wooshToggle.addEventListener("change", () => {
      this.showWoosh = this.wooshToggle.checked;
      if (this.parts) this.syncGhost();
    });
    bar.append(el("label", { class: "wxt-toggle", for: "wxt-show-woosh" }, this.wooshToggle, " Woosh cells"));
    this.stage = el("div", { class: "wxt-stage" });
    this.canvas = el("canvas", {
      tabindex: "0", role: "application", "aria-roledescription": "3D gate view", "aria-describedby": "wxt-info",
      "aria-label": "3D view of the shape you are editing. Arrow keys rotate, plus and minus zoom, 0 resets the view.",
    });
    this.stage.append(this.canvas);
    this.status = el("p", { class: "wxt-status", role: "status", "aria-live": "polite" });
    this.info = el("div", { class: "wxt-info", id: "wxt-info" });
    this.legend = el("ul", { class: "wxt-legend", "aria-label": "Legend" });
    // The picture and its buttons stay in view while the editor beside them scrolls.
    this.el = el("div", { class: "wxt-designer-view" }, el("div", { class: "wxt-pinned" }, bar, stageWithLegend(this.stage, this.legend), this.status),
      el("p", { class: "wxt-help" }, "Drag to orbit, scroll or pinch to zoom. The outlined cell is the one selected in the editor."),
      this.info);
    this.canvas.addEventListener("keydown", (e) => this.key(e));
  }

  /** Draws `shape`; the camera moves only when asked, so edits do not throw the view away. */
  show(shape, reframe) {
    this.runId++;
    this.shape = { ...shape, name: shape.name || "This shape" };
    this.disposeParts();
    this.buildParts();
    this.describe();
    if (reframe || !this.home) this.frameCamera();
    this.quiet = true; // an edit is not news; only the preview buttons are announced
    this.setState("idle");
    this.quiet = false;
  }

  say(text) {
    if (!this.quiet) super.say(text);
  }

  /** An empty shape has nothing to frame; fall back to the whole grid. */
  bounds() {
    const b = super.bounds();
    if (Number.isFinite(b.min.y)) return b;
    const box = new THREE.Box3(new THREE.Vector3(0, 0, 1), new THREE.Vector3(this.shape.width - 1, this.shape.height - 1, this.shape.layers));
    return { min: box.min, max: box.max, center: box.getCenter(new THREE.Vector3()), size: box.getSize(new THREE.Vector3()) };
  }

  /** Outlines one cell, or none. */
  mark(cell) {
    if (!this.outline) {
      this.outline = new THREE.LineSegments(new THREE.EdgesGeometry(new THREE.BoxGeometry(1.15, 1.15, 1.15)),
        new THREE.LineBasicMaterial({ depthTest: false }));
      this.outline.renderOrder = 20;
      this.scene.add(this.outline);
    }
    this.outline.visible = Boolean(cell);
    if (cell) this.outline.position.copy(this.pos(cell));
    this.outline.material.color.set(palette().fresh);
    this.requestRender();
  }

  /** The shape's legend, plus the grid's cue for a cell with extras, so the key is always in view beside the picture. */
  renderLegend() {
    super.renderLegend();
    this.legend.append(el("li", {}, el("span", { class: "wxt-cue", "aria-hidden": "true" }, "S"), " Underlined letter in the grid: the cell has extras"));
    this.legend.append(el("li", {}, el("span", { class: "wxt-swatch wxt-swatch-woosh", "aria-hidden": "true", style: `background-color:${hex(palette().woosh)}` }),
      " Striped cell in the grid, and a crossed wireframe block in the 3D view: a woosh cell, darker the later its step, with the step number in the corner of bigger cells"));
  }

  applyPalette() {
    super.applyPalette();
    if (this.outline) this.outline.material.color.set(palette().fresh);
  }
}

class Designer {
  constructor(root, view) {
    this.root = root;
    this.view = view;
    this.history = new E.History();
    this.brush = "select";
    this.layer = 1;
    this.row = 0;
    this.col = 0;
    this.reframe = true;
    this.summary = { shown: "", pending: "", timer: 0 };
    this.saveTimer = 0;
    this.derivedHandle = 0;
    this.storageOk = true;
    this.prefs = parseViewState(viewStore.get());
    this.zoom = this.prefs.zoom ?? ZOOM.start;
    this.expanded = false;
    this.inerted = [];
    this.build();
    this.watchLayout();
  }

  // --- building the page ------------------------------------------------------------------

  build() {
    const button = (label, onClick, attrs = {}) => {
      const b = el("button", { type: "button", class: "md-button", ...attrs }, label);
      b.addEventListener("click", onClick);
      return b;
    };

    // Files: start from, upload, download.
    this.start = el("select", { id: "wxt-start" }, el("option", { value: "" }, "Blank shape"));
    this.upload = el("input", { type: "file", id: "wxt-upload", accept: ".shape,.txt,text/plain" });
    this.upload.addEventListener("change", () => { const [f] = this.upload.files; if (f) this.readFile(f); this.upload.value = ""; });
    this.drop = el("div", { class: "wxt-drop" },
      el("label", { for: "wxt-upload" }, "Upload a .shape file "), this.upload, el("span", {}, " or drop one here"));
    this.setupDrop();
    this.undoButton = button("Undo", () => this.undo());
    this.redoButton = button("Redo", () => this.redo());
    this.discardButton = button("Discard draft", () => this.discard());
    this.downloadButton = button("Download .shape file", () => this.download(), { "aria-describedby": "wxt-download-note" });
    this.copyButton = button("Copy as text", () => this.copy());
    this.downloadNote = el("p", { class: "wxt-note", id: "wxt-download-note" });
    this.commandsNote = el("p", { class: "wxt-help", id: "wxt-commands" });
    this.status = el("p", { class: "wxt-status", role: "status", "aria-live": "polite" });
    this.draftNote = el("p", { class: "wxt-help" });
    const files = el("section", { class: "wxt-files", "aria-label": "Files" },
      el("div", { class: "wxt-bar" }, el("label", { for: "wxt-start" }, "Start from "), this.start,
        button("Load", () => this.loadStart().catch((e) => this.fail(e)))),
      el("div", { class: "wxt-bar" }, this.drop),
      el("div", { class: "wxt-bar" }, this.downloadButton, this.copyButton, this.undoButton, this.redoButton, this.discardButton),
      this.downloadNote, this.commandsNote, this.draftNote);

    // Editor.
    this.layerSelect = el("select", { id: "wxt-layer" });
    this.layerSelect.addEventListener("change", () => this.setLayer(Number(this.layerSelect.value)));
    this.layerBar = el("div", { class: "wxt-bar" },
      el("label", { for: "wxt-layer" }, "Layer "), this.layerSelect,
      button("Previous", () => this.setLayer(this.layer - 1), { "aria-label": "Previous layer" }),
      button("Next", () => this.setLayer(this.layer + 1), { "aria-label": "Next layer" }),
      button("Add layer after", () => this.edit((s) => E.addLayer(s, this.layer), "Added a blank layer after this one.", { move: () => { this.layer++; } })),
      button("Remove layer", () => this.edit((s) => E.removeLayer(s, this.layer), "Removed the layer.")));
    this.tools = el("div", { class: "wxt-bar", role: "group", "aria-label": "Rows and columns, at the selected cell" },
      button("Add row below", () => this.edit((s) => E.addRow(s, this.row), "Added a row below the selected one, in every layer.", { move: () => { this.row++; } })),
      button("Remove row", () => this.edit((s) => E.removeRow(s, this.row), "Removed the selected row from every layer.")),
      button("Add column right", () => this.edit((s) => E.addColumn(s, this.col), "Added a column to the right of the selected one, in every layer.", { move: () => { this.col++; } })),
      button("Remove column", () => this.edit((s) => E.removeColumn(s, this.col), "Removed the selected column from every layer.")));
    this.sizeInputs = {};
    const size = el("div", { class: "wxt-bar", role: "group", "aria-label": "Resize the grid" });
    for (const [key, label, max] of [["width", "Columns", LIMITS.side], ["height", "Rows", LIMITS.side], ["layers", "Layers", LIMITS.layers]]) {
      const input = el("input", { type: "number", id: `wxt-size-${key}`, min: "1", max: String(max), step: "1", class: "wxt-number" });
      this.sizeInputs[key] = input;
      size.append(el("label", { for: `wxt-size-${key}` }, `${label} `), input);
    }
    size.append(button("Resize", () => this.resizeGrid()));

    const brush = el("fieldset", { class: "wxt-brush" }, el("legend", {}, "Click or Enter paints a cell with"));
    BRUSHES.forEach(([value, label]) => {
      const input = el("input", { type: "radio", name: "wxt-brush", value, id: `wxt-brush-${value}` });
      input.checked = value === this.brush;
      input.addEventListener("change", () => { this.brush = value; });
      brush.append(el("label", { class: "wxt-radio", for: `wxt-brush-${value}` }, input, ` ${label}`));
    });

    this.table = el("table", { role: "grid", class: "wxt-grid" });
    this.table.addEventListener("click", (e) => { const td = e.target.closest("td"); if (td) this.clickCell(td); });
    this.table.addEventListener("keydown", (e) => this.gridKey(e));
    this.scroller = el("div", { class: "wxt-grid-scroll", role: "region", tabindex: "0", "aria-label": "Grid of the layer. Scroll to see all of it." }, this.table);
    this.scroller.addEventListener("wheel", (e) => this.wheel(e), { passive: false });
    this.gridHelp = el("p", { class: "wxt-help" },
      "Arrow keys move between cells; Enter or Space paints with the chosen kind, or press I, S, P or C. Page Up and Page Down change layer, " +
      "Ctrl+Z undoes. Plus and minus make the cells bigger or smaller, as does Ctrl and scroll over the grid; Fit to window picks the biggest size that shows the whole layer. " +
      "Small cells show just their letter, and an underlined letter marks a cell with extras. Expand fills the window, and Escape leaves it. Layer 1 is the far side; the highest layer faces the activation switch. Row 1 is the top, column 1 the left of the file.");
    this.gridBar = this.buildGridBar(button);
    this.applyZoom();

    this.cellBox = this.buildCellPanel();
    this.controls = el("div", { class: "wxt-controls", id: "wxt-controls" }, this.layerBar, this.tools, size, brush);
    // The one live region for the editor sits beside the grid, where no panel can be hidden from under it.
    // The checks' summary is read out through a second region that is never visible, so hiding the side panel cannot silence it.
    this.liveEl = el("p", { class: "wxt-sr", role: "status", "aria-live": "polite" });
    this.editEl = el("div", { class: "wxt-edit" }, this.controls, this.gridBar, this.status, this.liveEl, this.scroller, this.gridHelp, this.cellBox);
    this.side = el("div", { class: "wxt-side", id: "wxt-side" }, this.view ? this.view.el : el("p", { class: "wxt-note" }, "The 3D view is not available, but the editor works."));
    this.handle = this.buildHandle();
    this.splitEl = el("div", { class: "wxt-split" }, this.editEl, this.handle, this.side);
    this.dockPanels = el("div", { class: "wxt-dock-panels" });
    this.filesBox = files;

    // Settings.
    this.settings = this.buildSettings();

    // Checks.
    this.summaryEl = el("p", { class: "wxt-status" });
    this.issueList = el("ul", { class: "wxt-issues", "aria-label": "Problems found" });
    const checks = el("section", { class: "wxt-checks" }, el("h3", { id: "wxt-checks-title" }, "Checks"), this.summaryEl, this.issueList,
      el("p", { class: "wxt-help" }, "These are the rules the plugin applies when it reads a shape, as far as this page can tell. " +
        "The in-game /wormhole gate shapes validate <name> is the authority: run it on your file before you build with it."));

    // The text itself.
    this.preview = el("textarea", { class: "wxt-text", readonly: "", rows: "12", spellcheck: "false", "aria-label": "The shape file as text" });
    this.previewBox = el("details", {}, el("summary", {}, "Show the file text"), this.preview);
    this.previewBox.addEventListener("toggle", () => this.renderPreview());

    this.below = el("div", { class: "wxt-below" }, this.settings, checks, this.previewBox);
    this.designerEl = el("div", { class: "wxt-designer" }, files, this.splitEl, this.below);
    this.root.replaceChildren(this.designerEl);
  }

  // --- the window: expand, cell zoom, split --------------------------------------------------

  buildGridBar(button) {
    this.zoomLabel = el("span", { class: "wxt-zoom-label" });
    this.toolsButton = button("Tools", () => this.togglePane("toolsOpen"), { "aria-controls": "wxt-controls", hidden: "", title: "Show or hide the file, layer and row tools" });
    this.dockButton = button("Side panel", () => this.togglePane("dockOpen"), { "aria-controls": "wxt-side", hidden: "", title: "Show or hide the 3D view, cell extras, settings and checks" });
    this.expandButton = button("Expand", () => this.setExpanded(!this.expanded, true),
      { "aria-pressed": "false", title: "Fill the whole window. Escape leaves it." });
    return el("div", { class: "wxt-bar wxt-gridbar", role: "group", "aria-label": "Cell size and window size" },
      button("\u2212", () => this.zoomBy(-1), { "aria-label": "Smaller cells", title: "Smaller cells" }),
      this.zoomLabel,
      button("+", () => this.zoomBy(1), { "aria-label": "Bigger cells", title: "Bigger cells" }),
      button("Fit to window", () => this.fit(), { title: "The biggest cells that show the whole layer" }),
      el("span", { class: "wxt-spacer" }), this.toolsButton, this.dockButton, this.expandButton);
  }

  buildHandle() {
    const h = el("div", { class: "wxt-handle", role: "separator", tabindex: "0", "aria-label": "Resize the grid and the 3D view", "aria-controls": "wxt-side", hidden: "" });
    h.addEventListener("keydown", (e) => this.handleKey(e));
    h.addEventListener("pointerdown", (e) => {
      if (e.button) return;
      h.setPointerCapture(e.pointerId);
      this.dragging = true;
      e.preventDefault();
    });
    h.addEventListener("pointermove", (e) => { if (this.dragging) this.dragTo(e); });
    const end = () => { if (this.dragging) { this.dragging = false; this.savePrefs(); } };
    h.addEventListener("pointerup", end);
    h.addEventListener("pointercancel", end);
    h.addEventListener("dblclick", () => this.moveSplit(SPLIT.start));
    return h;
  }

  /** Watches for what changes the layout: the window's shape, and keys that leave or trap focus. */
  watchLayout() {
    if (typeof ResizeObserver === "function") new ResizeObserver(() => { if (this.expanded) this.layoutSplit(); }).observe(this.splitEl);
    document.addEventListener("keydown", (e) => this.fullKey(e));
  }

  savePrefs() {
    clearTimeout(this.prefsTimer);
    viewStore.set(viewStateText(this.prefs));
  }

  /** A run of zoom steps writes the browser's storage once, when it settles. */
  savePrefsLater() {
    clearTimeout(this.prefsTimer);
    this.prefsTimer = setTimeout(() => this.savePrefs(), 400);
  }

  /** Every part of the page that differs between the two modes, set in one place so a failure can set it all back. */
  applyExpanded(on) {
    this.expanded = on;
    this.designerEl.classList.toggle("wxt-full", on);
    this.expandButton.setAttribute("aria-pressed", String(on));
    for (const b of [this.toolsButton, this.dockButton]) b.hidden = !on;
    for (const [name, value] of [["role", "dialog"], ["aria-modal", "true"], ["aria-label", "Gate designer, filling the window"]]) {
      if (on) this.designerEl.setAttribute(name, value); else this.designerEl.removeAttribute(name);
    }
    this.place(on);
    this.lockPage(on);
    this.syncPanes();
    this.layoutSplit();
  }

  /** Fills the browser window, or gives the page back; `byUser` also puts focus on the toggle. It is not remembered: a page should open as a page. */
  setExpanded(on, byUser = false) {
    if (on === this.expanded) return;
    try {
      this.applyExpanded(on);
      if (byUser) {
        this.say(on ? "The designer fills the window. Escape leaves it." : "Back on the page.");
        this.expandButton.focus();
      }
    } catch (error) {
      // Never leave the page locked behind a half-built overlay.
      try { this.applyExpanded(false); } catch { this.lockPage(false); }
      this.say(`The window could not be filled (${error.message}).`);
    }
  }

  /** The files, extras, settings and checks move into the overlay; the page order comes back on leaving. */
  place(on) {
    if (on) {
      this.controls.prepend(this.filesBox);
      this.side.append(this.dockPanels);
      this.dockPanels.append(this.downloadNote, this.commandsNote, this.draftNote, this.gridHelp, this.cellBox, this.below);
    } else {
      this.filesBox.append(this.downloadNote, this.commandsNote, this.draftNote);
      this.designerEl.insertBefore(this.filesBox, this.splitEl);
      this.designerEl.append(this.below);
      this.editEl.append(this.gridHelp, this.cellBox);
      this.dockPanels.remove();
    }
  }

  /** Locks the page's scroll and makes everything outside the overlay unreachable while it is open. */
  lockPage(on) {
    document.documentElement.classList.toggle("wxt-no-scroll", on);
    for (const node of this.inerted) node.inert = false;
    this.inerted = [];
    if (!on) return;
    for (let node = this.designerEl; node && node !== document.body; node = node.parentElement) {
      for (const sibling of node.parentElement.children) {
        if (sibling !== node && !sibling.inert) { sibling.inert = true; this.inerted.push(sibling); }
      }
    }
  }

  /** Escape leaves; Tab goes round inside the overlay. */
  fullKey(e) {
    if (!this.expanded) return;
    if (e.key === "Escape" && !e.defaultPrevented) {
      e.preventDefault();
      // Leaving moves the fields, and some browsers drop a typed value that was not committed first.
      if (document.activeElement && document.activeElement !== document.body) document.activeElement.blur();
      this.setExpanded(false, true);
    } else if (e.key === "Tab") {
      const items = [...this.designerEl.querySelectorAll(FOCUSABLE)].filter((n) => (n.checkVisibility ? n.checkVisibility() : n.getClientRects().length));
      if (!items.length) return;
      const first = items[0];
      const last = items[items.length - 1];
      const inside = this.designerEl.contains(document.activeElement);
      if (!inside || (e.shiftKey && document.activeElement === first) || (!e.shiftKey && document.activeElement === last)) {
        e.preventDefault();
        (e.shiftKey ? last : first).focus();
      }
    }
  }

  togglePane(key) {
    this.prefs[key] = !this.prefs[key];
    this.savePrefs();
    this.syncPanes();
    this.layoutSplit();
  }

  syncPanes() {
    this.toolsButton.setAttribute("aria-expanded", String(this.prefs.toolsOpen));
    this.dockButton.setAttribute("aria-expanded", String(this.prefs.dockOpen));
    this.controls.hidden = this.expanded && !this.prefs.toolsOpen;
  }

  /** Sizes the grid pane from the remembered share, kept inside the smallest sizes; the dock takes the rest. */
  layoutSplit() {
    const open = this.expanded && this.prefs.dockOpen;
    this.handle.hidden = !open;
    this.side.hidden = this.expanded && !this.prefs.dockOpen;
    if (!open) { this.editEl.style.flex = ""; return; }
    const bottom = window.innerHeight > window.innerWidth; // a tall window docks the panel below the grid
    this.splitEl.classList.toggle("wxt-bottom", bottom);
    this.splitEl.classList.toggle("wxt-stacked", bottom && window.innerWidth < STACK_PX); // too narrow for two columns below the grid
    const grip = (bottom ? this.handle.offsetHeight : this.handle.offsetWidth) || 14;
    const total = (bottom ? this.splitEl.clientHeight : this.splitEl.clientWidth) - grip;
    const min = bottom ? NARROW_PX : WIDE_PX;
    this.shownSplit = clampSplit(this.prefs.split ?? SPLIT.start, total, min.main, min.dock);
    const basis = Math.round(this.shownSplit * total);
    this.editEl.style.flex = `0 0 ${basis}px`;
    this.splitEl.style.setProperty("--wxt-dock", `${Math.max(0, total - basis)}px`);
    const { lo, hi } = splitBounds(total, min.main, min.dock);
    this.handle.setAttribute("aria-orientation", bottom ? "horizontal" : "vertical");
    this.handle.setAttribute("aria-valuemin", String(Math.round(lo * 100)));
    this.handle.setAttribute("aria-valuemax", String(Math.round(hi * 100)));
    this.handle.setAttribute("aria-valuenow", String(Math.round(this.shownSplit * 100)));
    this.handle.setAttribute("aria-valuetext", `The grid takes ${Math.round(this.shownSplit * 100)} percent`);
  }

  moveSplit(fraction) {
    this.prefs.split = fraction;
    this.layoutSplit();
    this.prefs.split = this.shownSplit;
    this.savePrefs();
  }

  dragTo(e) {
    const rect = this.splitEl.getBoundingClientRect();
    const bottom = this.splitEl.classList.contains("wxt-bottom");
    const grip = (bottom ? this.handle.offsetHeight : this.handle.offsetWidth) || 14;
    const [pos, size] = bottom ? [e.clientY - rect.top, rect.height] : [e.clientX - rect.left, rect.width];
    this.prefs.split = (pos - grip / 2) / (size - grip);
    this.layoutSplit();
    this.prefs.split = this.shownSplit;
  }

  handleKey(e) {
    const bottom = this.splitEl.classList.contains("wxt-bottom");
    const [less, more] = bottom ? ["ArrowUp", "ArrowDown"] : ["ArrowLeft", "ArrowRight"];
    const jump = SPLIT.step * (e.shiftKey ? 5 : 1);
    let next;
    if (e.key === less) next = this.shownSplit - jump;
    else if (e.key === more) next = this.shownSplit + jump;
    else if (e.key === "Home") next = 0;
    else if (e.key === "End") next = 1;
    else return;
    e.preventDefault();
    this.moveSplit(next);
  }

  /** The cell size on the page; the letters and labels give way to colour as the cells shrink. */
  applyZoom() {
    this.table.style.setProperty("--wxt-cell", `${this.zoom}px`);
    this.table.classList.toggle("wxt-tight", this.zoom < 32);
    this.table.classList.toggle("wxt-tiny", this.zoom < 22);
    this.table.classList.toggle("wxt-w-num", wooshCue(this.zoom).number);
    this.zoomLabel.textContent = `${this.zoom} px`;
  }

  /** Sets the size, keeping the point under `anchor` (scroller pixels; the middle by default) where it was. */
  setZoom(size, anchor) {
    const next = clampZoom(size);
    const old = this.zoom;
    if (next === old) return false;
    const s = this.scroller;
    const ax = anchor?.x ?? s.clientWidth / 2;
    const ay = anchor?.y ?? s.clientHeight / 2;
    const left = (s.scrollLeft + ax) * (next / old) - ax;
    const top = (s.scrollTop + ay) * (next / old) - ay;
    this.zoom = next;
    this.applyZoom();
    s.scrollLeft = left;
    s.scrollTop = top;
    this.prefs.zoom = next;
    this.savePrefsLater();
    return true;
  }

  zoomBy(direction, anchor, quiet = false) {
    const moved = this.setZoom(stepZoom(this.zoom, direction), anchor);
    const text = moved ? `Cells are ${this.zoom} pixels.` : `The cells are as ${direction < 0 ? "small" : "big"} as they go.`;
    if (quiet) this.sayLater(text); else this.say(text);
  }

  /** The biggest size at which the whole layer shows; when it will not fit at a legible size, that size, with the rest to scroll to. */
  fit() {
    const s = this.scroller;
    const head = this.table.rows[0];
    const maxHeight = parseFloat(getComputedStyle(s).maxHeight);
    const size = fitZoom({
      width: s.offsetWidth - 2, height: Number.isFinite(maxHeight) ? maxHeight : s.offsetHeight - 2,
      columns: this.shape.width, rows: this.shape.height, gutterX: head.cells[0].offsetWidth, gutterY: head.offsetHeight,
    });
    this.setZoom(size);
    const whole = s.scrollWidth <= s.clientWidth + 1 && s.scrollHeight <= s.clientHeight + 1;
    this.say(whole ? `Fitted: cells are ${this.zoom} pixels and the whole layer is in view.`
      : `Cells are ${this.zoom} pixels, the smallest that stay easy to read; this layer is bigger than the window, so scroll for the rest.`);
  }

  /** Ctrl and scroll over the grid zooms it (and a trackpad pinch, which arrives the same way): the events add up and apply once a frame. */
  wheel(e) {
    if (!e.ctrlKey) return;
    e.preventDefault();
    const r = this.scroller.getBoundingClientRect();
    this.wheelSum = (this.wheelSum ?? 0) + wheelPixels(e.deltaY, e.deltaMode);
    this.wheelAt = { x: e.clientX - r.left, y: e.clientY - r.top };
    if (!this.wheelFrame) this.wheelFrame = requestAnimationFrame(() => this.applyWheel());
  }

  applyWheel() {
    this.wheelFrame = 0;
    const next = wheelZoom(this.zoom, this.wheelSum);
    if (next !== this.zoom) {
      this.wheelSum = 0;
      this.setZoom(next, this.wheelAt);
      this.sayLater(`Cells are ${this.zoom} pixels.`);
    } else if ((this.zoom <= ZOOM.min && this.wheelSum > 0) || (this.zoom >= ZOOM.max && this.wheelSum < 0)) {
      this.wheelSum = 0; // at the end of the range, so more of the same builds up nothing
    }
    clearTimeout(this.wheelIdle);
    this.wheelIdle = setTimeout(() => { this.wheelSum = 0; }, 400);
  }

  buildCellPanel() {
    this.cellTitle = el("legend", {}, "Selected cell");
    const box = el("fieldset", { class: "wxt-cell" }, this.cellTitle);
    this.markerBoxes = {};
    for (const [key, label] of MARKERS) {
      const input = el("input", { type: "checkbox", id: `wxt-m-${key}` });
      input.addEventListener("change", () => this.editCell((c) => E.setMarker(c, key, input.checked)));
      this.markerBoxes[key] = input;
      box.append(el("label", { class: "wxt-radio", for: `wxt-m-${key}` }, input, ` :${key} ${label}`));
    }
    this.orderBoxes = {};
    for (const [which, key, label] of [["light", "L", "Chevron light order"], ["woosh", "W", "Woosh order"]]) {
      const check = el("input", { type: "checkbox", id: `wxt-o-${which}` });
      const number = el("input", { type: "number", min: "0", max: String(LIMITS.order), step: "1", class: "wxt-number", id: `wxt-n-${which}`, "aria-label": `${label} number` });
      check.addEventListener("change", () => this.editCell((c) => E.setOrder(c, which,
        check.checked ? (which === "light" ? E.nextLightOrder(this.shape) : E.currentWooshOrder(this.shape)) : null)));
      number.addEventListener("change", () => this.editCell((c) => E.setOrder(c, which, number.value === "" ? null : Number(number.value))));
      this.orderBoxes[which] = { check, number };
      box.append(el("span", { class: "wxt-radio" }, check, el("label", { for: `wxt-o-${which}` }, ` :${key} ${label}, number `), number));
    }
    return box;
  }

  buildSettings() {
    this.fields = {};
    const field = (id, label, input) => el("div", { class: "wxt-field" }, el("label", { for: id }, label), input);
    const text = (key, attrs = {}) => {
      const input = el("input", { type: "text", id: `wxt-s-${key}`, autocomplete: "off", spellcheck: "false", ...attrs });
      this.fields[key] = input;
      return input;
    };
    const num = (key) => {
      const input = el("input", { type: "number", id: `wxt-s-${key}`, min: "0", max: "2147483647", step: "1", class: "wxt-number" });
      this.fields[key] = input;
      return input;
    };
    const redstone = el("input", { type: "checkbox", id: "wxt-s-redstone" });
    this.fields.redstone = redstone;
    const body = [
      field("wxt-s-name", "Name", text("name", { maxlength: "60" })),
      field("wxt-s-light", "LIGHT_TICKS: ticks between chevrons lighting (blank: not set)", num("light")),
      field("wxt-s-woosh", "WOOSH_TICKS: ticks between woosh steps (blank: not set)", num("woosh")),
      el("div", { class: "wxt-field" }, el("label", { class: "wxt-radio", for: "wxt-s-redstone" }, redstone, " REDSTONE_ACTIVATED: let redstone start and show this gate")),
      el("p", { class: "wxt-help" }, "Materials are best left blank: a gate takes its look from its material group in config.yml. " +
        "Set one only if the shape needs it, as a capital-letter name such as OBSIDIAN."),
    ];
    for (const [key, label] of MATERIALS) body.push(field(`wxt-s-${key}`, `${key} (${label})`, text(key, { placeholder: "not set" })));
    this.extraNote = el("p", { class: "wxt-help" });
    body.push(this.extraNote);
    for (const input of Object.values(this.fields)) input.addEventListener("change", () => this.applySettings());
    return el("details", { class: "wxt-settings", open: "" }, el("summary", {}, "Settings"), ...body);
  }

  setupDrop() {
    const stop = (e) => { e.preventDefault(); };
    this.drop.addEventListener("dragover", (e) => { stop(e); this.drop.classList.add("wxt-over"); });
    this.drop.addEventListener("dragleave", () => this.drop.classList.remove("wxt-over"));
    this.drop.addEventListener("drop", (e) => {
      stop(e);
      this.drop.classList.remove("wxt-over");
      const file = e.dataTransfer?.files?.[0];
      if (file) this.readFile(file);
    });
  }

  // --- starting up --------------------------------------------------------------------------

  async begin() {
    this.shapes = [];
    try {
      this.shapes = await fetchManifest();
      for (const s of this.shapes) this.start.append(el("option", { value: s.file }, s.name));
      this.start.value = (this.shapes.find((s) => s.name === "Standard") ?? this.shapes[0])?.file ?? "";
    } catch {
      this.say("The shipped shapes could not be listed, so you can only start from a blank one or your own file.");
    }
    const saved = store.get();
    const draft = saved ? E.readDraft(saved) : null;
    if (saved && !draft) {
      const kept = store.backUp(saved);
      this.notice = kept
        ? "A draft from an older version could not be read. It is kept in this browser under another name, and this is a fresh start."
        : "A draft from an older version could not be read, and the browser would not keep a copy; it will be replaced.";
    }
    if (draft) {
      this.shape = draft;
      this.say("Your last draft is back. Discard draft starts again from the shape chosen above.");
    } else if (this.start.value) {
      try {
        this.shape = this.shippedCopy((await this.fetchShipped(this.start.value)));
        this.say(`Loaded ${this.shape.name}, a copy of the shipped shape under a name of its own. Edit it, or pick another shape and press Load.`);
      } catch {
        this.shape = E.newShape();
        this.say("The shipped shape could not be loaded, so this is a blank one.");
      }
    } else {
      this.shape = E.newShape();
    }
    if (this.notice) this.say(this.notice);
    this.changed({ reframe: true });
    this.updateDraftNote();
  }

  // --- state changes ------------------------------------------------------------------------

  /** Replaces the shape with whatever `make` builds from it, as one undoable step. */
  edit(make, message, { move, reframe = false } = {}) {
    let next;
    try {
      next = make(this.shape);
    } catch (e) {
      if (e instanceof E.EditError) { this.say(e.message); return; }
      throw e;
    }
    if (next === this.shape) { this.renderSettings(); return; } // nothing changed, so nothing to undo
    this.history.record(this.shape);
    this.shape = next;
    move?.();
    this.changed({ reframe });
    if (message) this.say(message);
  }

  async fetchShipped(file) {
    return E.fromParsed(await fetchShape(file)).shape;
  }

  /** A shipped shape under a name of its own: a file with the stock name would replace the stock shape. */
  shippedCopy(shape) {
    return E.withSettings(shape, { name: E.customName(shape.name) });
  }

  /** Selects the first cell of the first layer, as a newly loaded shape starts. */
  home() {
    this.layer = 1;
    this.row = 0;
    this.col = 0;
  }

  editCell(fn) {
    let next;
    try {
      next = E.editCell(this.shape, this.layer, this.row, this.col, fn);
    } catch (e) {
      if (e instanceof E.EditError) { this.say(e.message); this.renderPanel(); return; }
      throw e;
    }
    if (next === this.shape) { this.renderPanel(); return; } // nothing changed, so nothing to undo
    this.history.record(this.shape);
    this.shape = next;
    this.changed({ cell: true });
    return true;
  }

  undo() {
    const prev = this.history.undo(this.shape);
    if (prev) { this.shape = prev; this.changed({ reframe: false }); this.say("Undid the last change."); } else this.say("Nothing to undo.");
  }

  redo() {
    const next = this.history.redo(this.shape);
    if (next) { this.shape = next; this.changed({ reframe: false }); this.say("Redid the change."); } else this.say("Nothing to redo.");
  }

  /** Everything on the page that shows the shape, redrawn; the slow parts wait for the next frame. */
  changed({ cell = false, reframe = false } = {}) {
    const s = this.shape;
    this.layer = Math.min(Math.max(this.layer, 1), s.layers);
    this.row = Math.min(Math.max(this.row, 0), s.height - 1);
    this.col = Math.min(Math.max(this.col, 0), s.width - 1);
    if (cell) this.paintCell(this.cellEl(this.row, this.col), E.cellAt(s, this.layer, this.row, this.col));
    else this.renderGrid();
    this.renderLayerBar();
    this.renderPanel();
    this.renderSettings();
    this.undoButton.disabled = !this.history.past.length;
    this.redoButton.disabled = !this.history.future.length;
    this.reframe ||= reframe;
    if (!this.derivedHandle) this.derivedHandle = requestAnimationFrame(() => this.derive());
    clearTimeout(this.saveTimer);
    this.saveTimer = setTimeout(() => this.save(), SAVE_DELAY_MS);
  }

  derive() {
    this.derivedHandle = 0;
    this.view?.show(this.shape, this.reframe);
    this.reframe = false;
    this.markView();
    // The checks and the file text cost more than a frame on a big shape, so they wait for a pause in the editing.
    clearTimeout(this.slowTimer);
    this.slowTimer = setTimeout(() => { this.renderIssues(); this.renderPreview(); }, this.shape.cells.length > 2000 ? SLOW_DELAY_MS : 0);
  }

  markView() {
    this.view?.mark(E.cellAt(this.shape, this.layer, this.row, this.col));
  }

  save() {
    const ok = store.set(E.draftText(this.shape));
    if (ok !== this.storageOk) { this.storageOk = ok; this.updateDraftNote(); }
  }

  updateDraftNote() {
    this.draftNote.textContent = this.storageOk
      ? "Your draft is kept in this browser as you edit, and goes nowhere else. Nothing is uploaded."
      : "This browser is not letting the page keep a draft, so download your file to keep your work. Nothing is uploaded either way.";
  }

  say(text) {
    this.status.textContent = text;
  }

  /** Says it, but a run of paints is heard once a second rather than each time. */
  sayLater(text) {
    const t = this.late ??= { timer: 0, pending: null };
    t.pending = text;
    if (t.timer) return;
    this.say(text);
    t.pending = null;
    t.timer = setTimeout(() => {
      t.timer = 0;
      if (t.pending) this.sayLater(t.pending);
    }, 1000);
  }

  fail(error) {
    this.say(error instanceof ShapeError ? `That shape cannot be used: ${error.message}` : `Something went wrong: ${error.message}`);
  }

  // --- the grid -----------------------------------------------------------------------------

  renderLayerBar() {
    const s = this.shape;
    if (this.layerSelect.options.length !== s.layers) {
      this.layerSelect.replaceChildren(...Array.from({ length: s.layers }, (_, i) =>
        el("option", { value: String(i + 1) }, `${i + 1} of ${s.layers}`)));
    }
    this.layerSelect.value = String(this.layer);
    for (const [key, input] of Object.entries(this.sizeInputs)) if (document.activeElement !== input) input.value = String(s[key]);
  }

  /** Draws the layer. The cells are kept when the grid's size is the same, and only those that differ are touched: a big grid is slow to lay out again. */
  renderGrid() {
    const s = this.shape;
    const hadFocus = this.table.contains(document.activeElement);
    const rebuilt = this.tds?.length !== s.height || this.tds[0]?.length !== s.width;
    if (rebuilt) this.buildGrid();
    for (let r = 0; r < s.height; r++) {
      for (let c = 0; c < s.width; c++) this.paintCell(this.tds[r][c], E.cellAt(s, this.layer, r, c));
    }
    this.table.setAttribute("aria-label", `Layer ${this.layer} of ${s.layers}: ${s.width} columns by ${s.height} rows`);
    // Focus stays with the selected cell however the selection moved (a resize, an undo, a jump).
    if (hadFocus) {
      const now = this.cellEl(this.row, this.col);
      if (document.activeElement !== now) now.focus();
    }
  }

  buildGrid() {
    const s = this.shape;
    const head = el("tr", { role: "row" }, el("th", { scope: "col", "aria-label": "Row" }));
    for (let c = 0; c < s.width; c++) head.append(el("th", { scope: "col" }, String(c + 1)));
    const body = el("tbody");
    this.tds = [];
    for (let r = 0; r < s.height; r++) {
      const tr = el("tr", { role: "row" }, el("th", { scope: "row" }, String(r + 1)));
      this.tds.push([]);
      for (let c = 0; c < s.width; c++) {
        const td = el("td", { role: "gridcell", "data-row": String(r), "data-col": String(c) });
        this.tds[r].push(td);
        tr.append(td);
      }
      body.append(tr);
    }
    this.table.style.setProperty("--wxt-cols", String(s.width));
    this.table.replaceChildren(el("thead", {}, head), body);
  }

  cellEl(row, col) {
    return this.tds[row]?.[col];
  }

  /** One cell's look, its label for a screen reader and its place in the tab order. */
  paintCell(td, cell) {
    const here = cell.row === this.row && cell.col === this.col;
    // A woosh cell's tint depends on the other cells' steps too, so the set of steps is part of what makes it look the same.
    const orders = this.shape.wooshOrders;
    const key = orders.join(",");
    if (td.wxtCell === cell && td.wxtHere === here && td.wxtKey === key) return; // cells are never changed in place, so the same object looks the same
    td.wxtCell = cell;
    td.wxtHere = here;
    td.wxtKey = key;
    const d = E.describeCell(cell);
    const step = wooshStep(cell.woosh, orders);
    td.className = `wxt-k-${d.kind}${here ? " wxt-sel" : ""}${d.tags.length ? " wxt-marked" : ""}${step >= 0 ? " wxt-woosh" : ""}`;
    if (step >= 0) {
      td.style.setProperty("--wxt-wt", wooshTint(step, orders.length).toFixed(2));
      td.dataset.w = String(step + 1); // the step, not the raw order, as the label says
    } else {
      td.style.removeProperty("--wxt-wt");
      delete td.dataset.w;
    }
    const text = step >= 0 ? `${d.text}, woosh step ${step + 1} of ${orders.length}`
      : cell.woosh === 0 ? `${d.text}, never played: woosh starts at order 1` : d.text;
    td.tabIndex = here ? 0 : -1;
    td.setAttribute("aria-selected", String(here));
    td.setAttribute("aria-label", `Row ${cell.row + 1}, column ${cell.col + 1}: ${text}`);
    td.title = text;
    // The letter is the cell's text and the extras a CSS label; the content is touched only when it changes, as that is what costs a layout.
    const tags = d.tags.map((t) => t.slice(1).replace("#", "")).join(" ");
    if (td.dataset.tags !== tags) td.dataset.tags = tags;
    // Where the corner badge shows the step, the label leaves out the :W tag it would repeat.
    const bare = d.tags.filter((t) => !t.startsWith(":W")).map((t) => t.slice(1).replace("#", "")).join(" ");
    if (td.dataset.tagsBare !== bare) td.dataset.tagsBare = bare;
    if (td.textContent !== d.kind) td.textContent = d.kind;
  }

  setLayer(n) {
    if (n < 1 || n > this.shape.layers || n === this.layer) return;
    this.layer = n;
    this.changed({});
    this.say(`Layer ${n} of ${this.shape.layers}.`);
  }

  select(row, col, focus) {
    const s = this.shape;
    row = Math.min(Math.max(row, 0), s.height - 1);
    col = Math.min(Math.max(col, 0), s.width - 1);
    const [oldRow, oldCol] = [this.row, this.col];
    this.row = row;
    this.col = col;
    this.paintCell(this.cellEl(oldRow, oldCol), E.cellAt(s, this.layer, oldRow, oldCol));
    const now = this.cellEl(row, col);
    this.paintCell(now, E.cellAt(s, this.layer, row, col));
    if (focus) now.focus();
    this.renderPanel();
    this.markView();
  }

  clickCell(td) {
    this.select(Number(td.dataset.row), Number(td.dataset.col), true);
    this.paintBrush(this.brush);
  }

  paintBrush(kind) {
    if (kind === "select") return;
    if (this.editCell((c) => E.setKind(c, kind))) {
      this.sayLater(`Row ${this.row + 1}, column ${this.col + 1} is now ${E.KIND_NAMES[kind]}.`);
    }
  }

  gridKey(e) {
    const s = this.shape;
    const move = { ArrowLeft: [0, -1], ArrowRight: [0, 1], ArrowUp: [-1, 0], ArrowDown: [1, 0] }[e.key];
    const key = e.key.length === 1 ? e.key.toUpperCase() : e.key;
    // The site binds bare letters (S and F search, P and N turn the page); in the grid they are ours.
    if (e.key.length === 1 && !e.ctrlKey && !e.metaKey && !e.altKey) e.stopPropagation();
    if (e.ctrlKey || e.metaKey) {
      if (key === "Z" && !e.shiftKey) this.undo();
      else if (key === "Y" || (key === "Z" && e.shiftKey)) this.redo();
      else return;
    } else if (e.altKey) {
      return;
    } else if (move) {
      this.select(this.row + move[0], this.col + move[1], true);
    } else if (key === "+" || key === "=") {
      this.zoomBy(1);
    } else if (key === "-" || key === "_") {
      this.zoomBy(-1);
    } else if (key === "Home") {
      this.select(this.row, 0, true);
    } else if (key === "End") {
      this.select(this.row, s.width - 1, true);
    } else if (key === "PageUp") {
      this.setLayer(this.layer - 1);
    } else if (key === "PageDown") {
      this.setLayer(this.layer + 1);
    } else if (key === "Enter" || key === " ") {
      if (this.brush === "select") this.say("Choose a kind to paint with, or press I, S, P or C.");
      this.paintBrush(this.brush);
    } else if (["I", "S", "P", "C"].includes(key)) {
      this.paintBrush(key);
    } else if (key === "Delete" || key === "Backspace") {
      this.paintBrush("I");
    } else {
      return;
    }
    e.preventDefault();
  }

  renderPanel() {
    const cell = E.cellAt(this.shape, this.layer, this.row, this.col);
    if (!cell) return;
    this.cellTitle.textContent = `Selected cell: layer ${cell.layer}, row ${cell.row + 1}, column ${cell.col + 1}`;
    for (const [key, box] of Object.entries(this.markerBoxes)) box.checked = cell.markers.includes(key);
    for (const [which, { check, number }] of Object.entries(this.orderBoxes)) {
      check.checked = cell[which] !== null;
      number.value = cell[which] === null ? "" : String(cell[which]);
    }
  }

  resizeGrid() {
    const [w, h, l] = ["width", "height", "layers"].map((k) => Number(this.sizeInputs[k].value));
    if (![w, h, l].every((n) => Number.isInteger(n) && n >= 1)) { this.say("Columns, rows and layers are each a whole number, 1 or more."); return; }
    this.edit((s) => E.resize(s, w, h, l), `Resized to ${w} columns by ${h} rows in ${l} layers; cells cut off at the right, bottom and top layer are gone, and Undo brings them back.`, { reframe: true });
  }

  // --- settings -----------------------------------------------------------------------------

  renderSettings() {
    const s = this.shape;
    const f = this.fields;
    f.name.value = s.name;
    f.light.value = s.lightTicks === null ? "" : String(s.lightTicks);
    f.woosh.value = s.wooshTicks === null ? "" : String(s.wooshTicks);
    f.redstone.checked = s.redstoneActivated;
    for (const [key] of MATERIALS) f[key].value = s.materials[key] ?? "";
    this.extraNote.textContent = s.extra.length ? `Also in the file, kept as written: ${s.extra.join(", ")}.` : "";
  }

  applySettings() {
    const f = this.fields;
    const ticks = (input) => (input.value.trim() === "" ? null : Number(input.value));
    const materials = {};
    for (const [key] of MATERIALS) if (f[key].value.trim()) materials[key] = f[key].value.trim();
    const patch = {
      name: f.name.value.trim(), lightTicks: ticks(f.light), wooshTicks: ticks(f.woosh), redstoneActivated: f.redstone.checked, materials,
    };
    this.edit((s) => E.withSettings(s, patch));
  }

  // --- checks -------------------------------------------------------------------------------

  renderIssues() {
    const issues = checkShape(this.shape, { shipped: (this.shapes ?? []).map((s) => s.name) });
    this.renderCommands();
    const errors = issues.filter((i) => i.level === "error").length;
    const warnings = issues.length - errors;
    const plural = (n, w) => `${n} ${w}${n === 1 ? "" : "s"}`;
    const text = issues.length ? `${plural(errors, "error")} and ${plural(warnings, "warning")}.` : `No problems found; reload should accept this.${Object.keys(this.shape.materials).length ? " Material names are only checked for safe characters here, not for existing in your server's Minecraft version; validate checks that." : ""}`;
    const note = errors
      ? "This shape has errors. You can still download it, but the plugin may refuse it or build a broken gate."
      : "";
    this.downloadNote.textContent = note;
    this.speak(note ? `${text} ${note}` : text);
    this.issueList.replaceChildren(...issues.slice(0, 100).map((issue) => {
      const label = `${issue.level === "error" ? "Error" : "Warning"}: ${issue.message}`;
      const inner = issue.cell
        ? el("button", { type: "button", class: "wxt-issue" }, label)
        : el("span", { class: "wxt-issue" }, label);
      if (issue.cell) inner.addEventListener("click", () => this.jump(issue.cell));
      return el("li", { class: `wxt-${issue.level}` }, inner);
    }));
    if (issues.length > 100) this.issueList.append(el("li", {}, `And ${issues.length - 100} more.`));
  }

  /** The commands for this shape: the file name and the Name= value are two things, and each command wants one. */
  renderCommands() {
    const name = this.shape.name.trim();
    const base = E.fileNameFor(this.shape.name).slice(0, -".shape".length);
    const parts = [`The file will be ${base}.shape. Check it with /wormhole gate shapes validate ${base} and load it with /wormhole gate shapes reload ${base}: those take the file name.`];
    if (name) parts.push(`Build it with /wormhole gate build ${name}: that takes the shape's Name.`);
    if (name && name !== base) {
      parts.push(`The name "${name}" is not a file name, so the file is called ${base}.shape instead; use ${base} for the first two commands and "${name}" for gate build.`);
    }
    this.commandsNote.textContent = parts.join(" ");
  }

  jump(cell) {
    this.layer = cell.layer;
    this.row = cell.row;
    this.col = cell.col;
    this.changed({});
    this.cellEl(cell.row, cell.col).focus();
    this.markView();
  }

  /** Says the checks' result, but no more than once every second or so while someone is editing. */
  speak(text) {
    this.summary.pending = text;
    if (!this.summary.timer) this.flushSummary();
  }

  flushSummary() {
    const s = this.summary;
    if (s.pending === s.shown) return;
    s.shown = s.pending;
    this.summaryEl.textContent = s.pending;
    this.liveEl.textContent = s.pending;
    s.timer = setTimeout(() => { s.timer = 0; this.flushSummary(); }, SUMMARY_GAP_MS);
  }

  renderPreview() {
    if (this.previewBox.open) this.preview.value = writeShape(this.shape);
  }

  // --- files --------------------------------------------------------------------------------

  async loadStart() {
    const file = this.start.value;
    if (!file) {
      this.edit(() => E.newShape(), "A blank shape: every cell Ignored, with just the player entry point. Undo brings your last one back.",
        { reframe: true, move: () => this.home() });
      return;
    }
    const shape = this.shippedCopy(await this.fetchShipped(file));
    this.edit(() => shape, `Loaded a copy of the shipped shape as ${shape.name}, so saving it does not replace the stock one. Undo brings your last one back.`, { reframe: true, move: () => this.home() });
  }

  async readFile(file) {
    if (file.size > LIMITS.bytes) { this.say(`That file is ${Math.ceil(file.size / 1024)} KB; a shape is at most ${LIMITS.bytes / 1024} KB here.`); return; }
    if (!/\.(shape|txt)$/i.test(file.name)) {
      this.say("Only .shape or .txt text files can be opened here.");
      return;
    }
    try {
      const text = await file.text();
      if (text.includes("\u0000")) throw new ShapeError("That does not look like a text file.");
      const { shape, notes } = E.fromParsed(parseShape(text));
      this.edit(() => shape, null, { reframe: true, move: () => this.home() });
      this.say(`Opened ${file.name.slice(0, 80)} as ${shape.name || "an unnamed shape"}. ${notes.join(" ")} Undo brings your last one back.`.replace(/\s+/g, " "));
    } catch (e) {
      this.fail(e);
    }
  }

  text() {
    return writeShape(this.shape);
  }

  download() {
    const name = E.fileNameFor(this.shape.name);
    const url = URL.createObjectURL(new Blob([this.text()], { type: "text/plain;charset=utf-8" }));
    const a = el("a", { href: url, download: name });
    document.body.append(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    this.say(`Downloaded ${name}. Put it in plugins/WormholeXTreme/shapes/gate/ on your server.`);
  }

  async copy() {
    try {
      await navigator.clipboard.writeText(this.text());
      this.say("Copied the shape file text.");
    } catch {
      // No clipboard (an insecure page, or refused): show the text, selected, to copy by hand.
      this.previewBox.open = true;
      this.preview.value = this.text();
      this.preview.focus();
      this.preview.select();
      this.say("The browser would not copy for the page. The text is selected below; press Ctrl+C.");
    }
  }

  /** Two presses, so one slip does not lose a draft. */
  discard() {
    if (!this.confirming) {
      this.confirming = setTimeout(() => { this.confirming = 0; this.discardButton.textContent = "Discard draft"; }, 5000);
      this.discardButton.textContent = "Really discard?";
      this.say("Press Discard draft again to throw this draft away and start again from the shape chosen above.");
      return;
    }
    clearTimeout(this.confirming);
    this.confirming = 0;
    this.discardButton.textContent = "Discard draft";
    store.remove();
    this.loadStart().then(() => {
      this.history.clear();
      this.undoButton.disabled = true;
      this.redoButton.disabled = true;
    }).catch((e) => this.fail(e));
  }
}

async function main() {
  const root = document.getElementById("designer-root");
  if (!root) return;
  // The editor works without three.js; only the picture needs it.
  const probe = document.createElement("div");
  const have3d = await loadLibs(probe);
  let view = null;
  if (have3d) view = new DesignerView(document.createElement("div"));
  try {
    const designer = new Designer(root, view);
    if (!have3d) root.prepend(...probe.childNodes);
    await designer.begin();
  } catch (error) {
    root.replaceChildren(el("p", { class: "admonition warning", role: "alert" }, `The designer could not start (${error.message}).`));
  }
}

main();
