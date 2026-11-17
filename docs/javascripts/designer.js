// The gate designer on the User manual page: edit a shape layer by layer beside the 3D view, check
// it as the plugin would, and download or upload .shape files. Everything stays in the browser:
// nothing is sent anywhere, and file text only ever goes to the parser and into textContent.
import { parseShape, ShapeError, LIMITS } from "./shape-parser.js";
import { THREE, el, loadLibs, fetchManifest, fetchShape, palette, stageWithLegend } from "./scene-kit.js";
import { GateViewer } from "./gate-viewer.js";
import { detailsName } from "./legend-size.js";
import * as E from "./shape-edit.js";
import { checkShape } from "./shape-check.js";
import { writeShape } from "./shape-writer.js";
import * as Sym from "./symmetry.js";
import { wooshStep, wooshTint, wooshCue, withoutWooshTag } from "./woosh-cue.js";
import {
  ZOOM, SPLIT, clampZoom, stepZoom, fitZoom, wheelZoom, wheelPixels, clampSplit, splitBounds, parseViewState, viewStateText,
} from "./designer-layout.js";

const DRAFT_KEY = "wxt-gate-designer-draft";
const VIEW_KEY = "wxt-gate-designer-view";
const FOCUSABLE = 'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), summary, [tabindex]:not([tabindex="-1"])';
const STACK_PX = 640;
const SHORT_PX = 640; // a window shorter than this folds the symmetry bar into a popover and starts expanded with the tools hidden (and the stylesheet's max-height query says the same)
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

/** "1 cell", "2 cells". */
const count = (n, one, many = `${one}s`) => `${n} ${n === 1 ? one : many}`;

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

  /**
   * The shape's legend, plus the grid's cues, so the key is always in view beside the picture. The labels are
   * short so the list stays compact; the full explanation of an entry is its title (and the Designer page).
   */
  renderLegend() {
    // A rebuild (an edit, the colour scheme) keeps the explanations a reader opened and the "?" that had the focus.
    const more = (key) => [...this.legend.querySelectorAll(".wxt-legend-more")].find((b) => b.dataset.key === key);
    const opened = [...this.legend.querySelectorAll(".wxt-legend-more[aria-expanded='true']")].map((b) => b.dataset.key);
    const focused = document.activeElement?.classList?.contains("wxt-legend-more") && this.legend.contains(document.activeElement)
      ? document.activeElement.dataset.key : null;
    this.wooshLabel = "Woosh cell";
    super.renderLegend();
    const explain = (li, label, text) => {
      const id = `wxt-legend-note-${++legendNotes}`;
      const note = el("span", { class: "wxt-legend-note", id, hidden: "" }, text);
      const button = el("button", { type: "button", class: "wxt-legend-more", "data-key": label, "aria-expanded": "false", "aria-controls": id, "aria-label": detailsName(label) }, "?");
      button.addEventListener("click", () => {
        note.hidden = !note.hidden;
        button.setAttribute("aria-expanded", String(!note.hidden));
      });
      li.setAttribute("title", text);
      li.append(" ", button, note);
    };
    explain(this.legend.children[4], this.wooshLabel, "A striped cell in the grid and a crossed wireframe block in the 3D view, stronger in up to five bands as its step goes up, with the step number in the corner of bigger cells");
    const cue = (glyph, label, text) => {
      const li = el("li", {}, el("span", { class: "wxt-cue", "aria-hidden": "true" }, glyph), ` ${label}`);
      explain(li, label, text);
      this.legend.append(li);
    };
    cue("S", "Underlined letter: extras", "Underlined letter in the grid: the cell has extras");
    cue("\u25A0", "Double border: error; dashed: warning", "Double border in the grid (a thick one with a corner mark on small cells): an error. Dashed border: a warning");
    cue("\u254E", "Dashed line: symmetry axis", "Dashed line in the grid, with Symmetry on: painting mirrors the kind across it and the order boxes mirror the number; Mirror now copies the dotted area, kinds only unless told");
    for (const key of opened) more(key)?.click();
    if (focused) more(focused)?.focus({ preventScroll: true });
  }

  applyPalette() {
    super.applyPalette();
    if (this.outline) this.outline.material.color.set(palette().fresh);
  }
}

let legendNotes = 0;

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
    this.symmetry = this.prefs.symmetry;
    this.problems = new Map(); // "layer,row,col" -> {level, messages}, from the last check
    this.issues = [];
    this.expanded = false;
    this.inerted = [];
    this.build();
    this.watchLayout();
    document.fonts?.ready?.then(() => this.placeAxes());
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
    // The mirror axes and the source side are drawn over the cells, inside the scroller so they scroll with the grid.
    this.axes = el("div", { class: "wxt-axes", "aria-hidden": "true" },
      el("div", { class: "wxt-src-area" }),
      el("div", { class: "wxt-axis wxt-axis-v" }), el("div", { class: "wxt-axis wxt-axis-h" }));
    this.scrollPad = el("div", { class: "wxt-scroll-pad", "aria-hidden": "true" });
    this.scroller.append(this.axes, this.scrollPad);
    this.flyout = this.buildFlyout();
    this.gridWrap = el("div", { class: "wxt-gridwrap" }, this.scroller, this.flyout);
    this.gridHelp = el("p", { class: "wxt-help" },
      "Arrow keys move between cells; Enter or Space paints with the chosen kind, or press I, S, P or C. Page Up and Page Down change layer, " +
      "Ctrl+Z undoes. Plus and minus make the cells bigger or smaller, as does Ctrl and scroll over the grid; Fit to window picks the biggest size that shows the whole layer. " +
      "Small cells show just their letter, and an underlined letter marks a cell with extras. Expand fills the window, and Escape leaves it. Layer 1 is the far side; the highest layer faces the activation switch. Row 1 is the top, column 1 the left of the file. " +
      "A cell with a double border, or a thick one and a corner mark on small cells, has an error and one with a dashed border a warning; the problems button above the grid lists them. " +
      "With Symmetry on, painting (I, S, P, C and clearing) mirrors the kind only, onto the cells across the dashed line in the same layer, and a cell turned Ignored loses its light number; " +
      "the light and woosh order boxes in the cell panel mirror the order number. Mirror now copies the kinds of the dotted half or quarter, including a cell's [S:C] either-material flag (painting does not copy that), and the orders only if Copy light and woosh orders is ticked. " +
      "A cell holding a one-per-gate extra is never changed by a mirror, whichever way it is made. " +
      "The one-per-gate extras (:N, :EP, :EM, :A, :D, :IA, [RD], [RS] and [RA]) are never mirrored: a new one goes on the cell you click only, and Mirror now leaves them where they are.");
    this.gridBar = this.buildGridBar(button);
    this.symBar = this.buildSymBar(button);
    this.gridWrap.prepend(this.symBar); // a bar above the grid; in a short window a popover over it
    this.applyZoom();

    this.cellBox = this.buildCellPanel();
    this.controls = el("div", { class: "wxt-controls", id: "wxt-controls" }, this.layerBar, this.tools, size, brush);
    // The one live region for the editor sits beside the grid, where no panel can be hidden from under it.
    // The checks' summary is read out through a second region that is never visible, so hiding the side panel cannot silence it.
    this.liveEl = el("p", { class: "wxt-sr", role: "status", "aria-live": "polite" });
    this.editEl = el("div", { class: "wxt-edit" }, this.controls, this.gridBar, this.status, this.liveEl, this.gridWrap, this.gridHelp, this.cellBox);
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
    this.chip = el("button", {
      type: "button", class: "md-button wxt-chip", "aria-expanded": "false", "aria-controls": "wxt-flyout",
      title: "Show the problems found in this shape",
    }, "Checking\u2026");
    this.chip.addEventListener("click", (e) => this.toggleFlyout(e.detail === 0)); // a click with no pointer is Enter or Space
    this.symToggle = button("Symmetry", () => this.toggleSym(), { class: "md-button wxt-symtoggle", "aria-expanded": "false", "aria-controls": "wxt-symbar", title: "Show or hide the symmetry controls" });
    return el("div", { class: "wxt-bar wxt-gridbar", role: "group", "aria-label": "Problems, cell size and window size" },
      this.chip,
      button("\u2212", () => this.zoomBy(-1), { "aria-label": "Smaller cells", title: "Smaller cells" }),
      this.zoomLabel,
      button("+", () => this.zoomBy(1), { "aria-label": "Bigger cells", title: "Bigger cells" }),
      button("Fit to window", () => this.fit(), { title: "The biggest cells that show the whole layer" }),
      this.symToggle, el("span", { class: "wxt-spacer" }), this.toolsButton, this.dockButton, this.expandButton);
  }

  /** The symmetry choice and its Mirror now buttons, in the toolbar over the grid. */
  buildSymBar(button) {
    this.symSelect = el("select", { id: "wxt-symmetry", "aria-describedby": "wxt-commands-sym" },
      ...Sym.MODES.map((m) => el("option", { value: m }, Sym.MODE_LABELS[m])));
    this.symSelect.value = this.symmetry;
    this.symSelect.addEventListener("change", () => this.setSymmetry(this.symSelect.value));
    this.mirrorLayerButton = button("Mirror now", () => this.mirrorNow(false),
      { title: "Copy the source half or quarter of this layer over the rest. One undo step." });
    this.mirrorAllButton = button("Mirror all layers", () => this.mirrorNow(true),
      { title: "The same, for every layer, each on its own. One undo step." });
    this.orderBox = el("input", { type: "checkbox", id: "wxt-mirror-orders" });
    this.symNote = el("span", { class: "wxt-sr", id: "wxt-commands-sym" }, "One-per-gate extras are never mirrored.");
    this.syncSymmetry();
    this.symOpen = false;
    document.addEventListener("pointerdown", (e) => {
      if (this.symOpen && !this.symBar.contains(e.target) && !this.symToggle.contains(e.target)) this.toggleSym(false);
    });
    document.addEventListener("keydown", (e) => {
      if (e.key !== "Escape" || !this.symOpen || e.defaultPrevented) return;
      const active = document.activeElement;
      if (!this.symBar.contains(active) && active !== this.symToggle) return;
      if (this.flyout.contains(active)) return;
      e.preventDefault();
      e.stopPropagation();
      this.toggleSym(false);
      this.symToggle.focus();
    }, true);
    return el("div", { class: "wxt-bar wxt-gridbar wxt-symbar", id: "wxt-symbar", role: "group", "aria-label": "Symmetry" },
      el("label", { for: "wxt-symmetry" }, "Symmetry "), this.symSelect, this.mirrorLayerButton, this.mirrorAllButton,
      el("label", { class: "wxt-toggle", for: "wxt-mirror-orders", title: "Off: only the kinds are copied, and each cell keeps its own light and woosh numbers." }, this.orderBox, " Copy light and woosh orders"),
      this.symNote);
  }

  /** In a short window the symmetry controls fold into a popover over the grid, opened by the Symmetry button. */
  toggleSym(open = !this.symOpen) {
    this.symOpen = open;
    this.symBar.classList.toggle("wxt-sym-open", open);
    this.symToggle.setAttribute("aria-expanded", String(open));
  }

  /** The problems flyout: a list over the foot of the grid (a sheet at the foot of the window when that is short), so opening it moves nothing. */
  buildFlyout() {
    this.flyoutList = el("ul", { class: "wxt-issues", "aria-label": "Problems found" });
    const close = el("button", { type: "button", class: "md-button wxt-flyout-close", "aria-label": "Close the problems list" }, "Close");
    close.addEventListener("click", () => { this.closeFlyout(); this.chip.focus({ preventScroll: true }); });
    const flyout = el("div", { class: "wxt-flyout", id: "wxt-flyout", role: "region", "aria-label": "Problems", hidden: "" },
      el("div", { class: "wxt-flyout-head" }, el("strong", {}, "Problems"), close), this.flyoutList,
      el("p", { class: "wxt-help" }, "Press an entry, or Up and Down, to select its cell; the list keeps the focus so you can step on. Escape closes it: a list opened with the keyboard hands the focus back to its button, one opened with the pointer puts you on the selected cell."));
    flyout.addEventListener("keydown", (e) => this.flyoutKey(e));
    this.flyoutOpen = false;
    // Escape and a click elsewhere close it; the Escape is taken first, so it does not also leave the expanded window.
    document.addEventListener("keydown", (e) => {
      if (e.key !== "Escape" || !this.flyoutOpen || e.defaultPrevented) return;
      // One overlay a press: the narrow Legend, when the focus is in it, answers first.
      if (document.activeElement?.closest?.(".wxt-legend-panel.wxt-open")) return;
      if (this.symOpen && (this.symBar.contains(document.activeElement) || document.activeElement === this.symToggle)) return;
      e.preventDefault();
      e.stopPropagation();
      const inList = flyout.contains(document.activeElement);
      const onChip = document.activeElement === this.chip;
      this.closeFlyout();
      if (onChip || (inList && this.flyoutByKey)) this.chip.focus({ preventScroll: true });
      else if (inList) this.cellEl(this.row, this.col)?.focus();
    }, true);
    document.addEventListener("pointerdown", (e) => {
      if (this.flyoutOpen && !flyout.contains(e.target) && !this.chip.contains(e.target)) this.closeFlyout();
    });
    return flyout;
  }

  toggleFlyout(byKey = false) {
    if (this.flyoutOpen) this.closeFlyout(); else this.openFlyout(byKey);
  }

  /** `byKey`: opened from the keyboard, so the focus goes into the list (and comes back to the button on close). */
  openFlyout(byKey = false) {
    this.flyoutOpen = true;
    this.flyoutByKey = byKey;
    this.flyout.hidden = false;
    this.chip.setAttribute("aria-expanded", "true");
    this.renderFlyout();
    this.padScroller();
    if (byKey) (this.flyoutList.querySelector("button.wxt-issue") ?? this.flyout.querySelector(".wxt-flyout-close")).focus({ preventScroll: true });
  }

  closeFlyout() {
    this.flyoutOpen = false;
    this.flyout.hidden = true;
    this.chip.setAttribute("aria-expanded", "false");
    this.padScroller();
  }

  /**
   * The flyout sits over the foot of the grid, so the grid gets room to scroll its last rows above it. The grid keeps the height it had,
   * so opening the list never moves the page: the room is scrolling room only.
   */
  padScroller() {
    const s = this.scroller;
    if (!this.flyoutOpen) {
      this.scrollPad.style.height = "0";
      s.style.height = "";
      return;
    }
    if (!this.expanded) s.style.height = `${s.offsetHeight}px`;
    const over = s.getBoundingClientRect().bottom - this.flyout.getBoundingClientRect().top;
    this.scrollPad.style.height = `${Math.max(0, Math.ceil(over))}px`;
  }

  /** Scrolls the cell into the part of the grid the headers and the flyout leave clear. */
  revealCell(td) {
    const s = this.scroller;
    const box = s.getBoundingClientRect();
    const t = td.getBoundingClientRect();
    const head = this.table.rows[0];
    const top = box.top + head.offsetHeight;
    const left = box.left + head.cells[0].offsetWidth;
    const bottom = Math.min(box.bottom, this.flyoutOpen ? this.flyout.getBoundingClientRect().top : Infinity) - 2;
    if (t.top < top) s.scrollTop -= top - t.top;
    else if (t.bottom > bottom) s.scrollTop += t.bottom - bottom;
    if (t.left < left) s.scrollLeft -= left - t.left;
    else if (t.right > box.right) s.scrollLeft += t.right - box.right;
  }

  /** Up and down walk the list, so a keyboard user can step through the problems. */
  flyoutKey(e) {
    const move = { ArrowDown: 1, ArrowUp: -1 }[e.key];
    if (!move) return;
    const items = [...this.flyoutList.querySelectorAll("button.wxt-issue")];
    const at = items.indexOf(document.activeElement);
    if (!items.length) return;
    e.preventDefault();
    const next = items[Math.min(Math.max(at + move, 0), items.length - 1)];
    next.focus({ preventScroll: true });
    next.click(); // stepping selects the cell as well; the focus stays in the list
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
    if (typeof ResizeObserver === "function") new ResizeObserver(() => { if (this.expanded) { this.syncPanes(); this.layoutSplit(); } }).observe(this.splitEl);
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

  isShort() {
    return typeof matchMedia === "function" && matchMedia(`(max-height: ${SHORT_PX}px)`).matches;
  }

  /** The file, layer and row tools: in a short window they start hidden and are not remembered, so the grid has the room. */
  toolsShown() {
    return this.isShort() ? Boolean(this.shortTools) : this.prefs.toolsOpen;
  }

  togglePane(key) {
    if (key === "toolsOpen" && this.isShort()) {
      this.shortTools = !this.shortTools;
      this.syncPanes();
      this.layoutSplit();
      return;
    }
    this.prefs[key] = !this.prefs[key];
    this.savePrefs();
    this.syncPanes();
    this.layoutSplit();
  }

  syncPanes() {
    this.toolsButton.setAttribute("aria-expanded", String(this.toolsShown()));
    this.dockButton.setAttribute("aria-expanded", String(this.prefs.dockOpen));
    this.controls.hidden = this.expanded && !this.toolsShown();
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
    this.placeAxes();
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
      // Mirrored cells take the same number, so they light together; the extras above are one per gate and never mirrored.
      check.addEventListener("change", () => this.setOrder(which,
        check.checked ? (which === "light" ? E.nextLightOrder(this.shape) : E.currentWooshOrder(this.shape)) : null));
      number.addEventListener("change", () => this.setOrder(which, number.value === "" ? null : Number(number.value)));
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

  // --- symmetry -----------------------------------------------------------------------------

  setSymmetry(mode) {
    if (!Sym.isMode(mode) || mode === this.symmetry) return;
    this.symmetry = mode;
    this.prefs.symmetry = mode;
    this.savePrefs();
    this.syncSymmetry();
    this.say(mode === "none" ? "Symmetry is off." : `Symmetry: ${Sym.MODE_LABELS[mode]}. The dashed line shows the axis; a cell you paint is mirrored across it.`);
  }

  /** The buttons and the dashed axes follow the mode; nothing in the shape depends on it. */
  syncSymmetry() {
    const off = this.symmetry === "none";
    this.mirrorLayerButton.disabled = off;
    this.mirrorAllButton.disabled = off;
    this.symSelect.value = this.symmetry;
    this.placeAxes();
  }

  /** Lays the dashed axes and the source tint over the cells, from the measured size of the header and gutter. */
  placeAxes() {
    const box = this.axes;
    if (!box || !this.tds) return;
    const mode = this.symmetry;
    box.hidden = mode === "none";
    if (box.hidden) return;
    const s = this.shape;
    const head = this.table.rows[0];
    const left = head.cells[0].offsetWidth;
    const top = head.offsetHeight;
    const { rows, cols } = Sym.sourceSize(mode, s.width, s.height);
    const sideways = mode === "lr" || mode === "both";
    const down = mode === "tb" || mode === "both";
    const [v, h] = [box.querySelector(".wxt-axis-v"), box.querySelector(".wxt-axis-h")];
    v.hidden = !sideways;
    h.hidden = !down;
    Object.assign(v.style, { left: `${left + (s.width * this.zoom) / 2}px`, top: `${top}px`, height: `${s.height * this.zoom}px` });
    Object.assign(h.style, { top: `${top + (s.height * this.zoom) / 2}px`, left: `${left}px`, width: `${s.width * this.zoom}px` });
    Object.assign(box.querySelector(".wxt-src-area").style, { left: `${left}px`, top: `${top}px`, width: `${cols * this.zoom}px`, height: `${rows * this.zoom}px` });
  }

  /** The cells a paint or an order reaches: the selected one, then its mirror images in this layer, less those holding a one-per-gate extra. */
  mirrored(row = this.row, col = this.col) {
    const { places, skipped } = Sym.targets(this.shape, this.layer, row, col, this.symmetry);
    return { places: [[row, col], ...places], skipped };
  }

  /** " (and skipped 2 cells holding :A)" for the status line, or nothing. */
  skippedNote(skipped) {
    return skipped.length ? `, and skipped ${skipped.length} ${skipped.length === 1 ? "cell" : "cells"} holding ${Sym.singlesHeld(skipped).join(", ")}` : "";
  }

  /** Copies the source half or quarter over the rest, of this layer or of all of them, as one undo step. */
  mirrorNow(all) {
    const layers = all ? Array.from({ length: this.shape.layers }, (_, i) => i + 1) : [this.layer];
    const copyOrders = this.orderBox.checked;
    const result = Sym.mirrorLayers(this.shape, layers, this.symmetry, { copyOrders });
    if (!result.changed) { this.say(`Nothing to mirror: the rest already matches the source${this.skippedNote(result.skipped).replace(", and", ";")}.`); return; }
    const what = all ? count(layers.length, "layer") : `layer ${this.layer}`;
    const held = result.overwritten ? `, ${result.overwritten} of them replacing something already drawn` + (copyOrders ? " or numbered" : "") : "";
    // Copying orders can leave a gap in the light order, which makes reload refuse the file: say so, and show it.
    let warn = "";
    let fresh = [];
    if (copyOrders) {
      const known = (this.shapes ?? []).map((s) => s.name);
      fresh = Sym.newIssues(checkShape(this.shape, { shipped: known }), checkShape(result.shape, { shipped: known }));
      if (fresh.length) warn = ` That made ${count(fresh.length, "new problem")}; the list shows them.`;
    }
    const dropped = result.dropped ? `, taking the light number off ${count(result.dropped, "cell")} that became Ignored` : "";
    this.edit(() => result.shape, `Mirrored ${what}: ${count(result.changed, "cell")} changed${held}${dropped}${this.skippedNote(result.skipped)}. ${copyOrders ? "" : "Light and woosh numbers were not copied. "}Undo puts it back.${warn}`);
    if (fresh.length) {
      this.renderIssues();
      this.openFlyout();
    }
  }

  /** Sets or clears a light or woosh order on the selected cell and, with symmetry on, on its mirror cells, and says what that did. */
  setOrder(which, order) {
    const before = this.shape;
    const { places, skipped } = this.mirrored();
    const replaced = places.slice(1).filter(([r, c]) => {
      const had = E.cellAt(before, this.layer, r, c)[which];
      return had !== null && had !== order;
    }).length;
    if (!this.editCell((c) => E.setOrder(c, which, order), { mirror: true })) return;
    const also = places.length - 1;
    if (!also && !skipped.length) return;
    const tag = which === "light" ? ":L" : ":W";
    let text = `${order === null ? `Cleared ${tag}` : `Set ${tag}#${order}`} on this cell${also ? ` and ${count(also, "mirrored cell")}` : ""}`;
    if (replaced) text += `, ${replaced === 1 ? "one" : replaced} of them replacing a number of its own`;
    text += `${this.skippedNote(skipped).replace(", and", also ? "; and" : ",")}.`;
    const known = (this.shapes ?? []).map((s) => s.name);
    const fresh = Sym.newIssues(checkShape(before, { shipped: known }), checkShape(this.shape, { shipped: known }));
    if (fresh.length) text += ` That made ${count(fresh.length, "new problem")}; the problems button lists them.`;
    this.say(text);
  }

  editCell(fn, { mirror = false } = {}) {
    let next;
    try {
      next = mirror && this.symmetry !== "none"
        ? E.editCells(this.shape, this.layer, this.mirrored().places, fn)
        : E.editCell(this.shape, this.layer, this.row, this.col, fn);
    } catch (e) {
      if (e instanceof E.EditError) { this.say(e.message); this.renderPanel(); return; }
      throw e;
    }
    if (next === this.shape) { this.renderPanel(); return; } // nothing changed, so nothing to undo
    this.history.record(this.shape);
    this.shape = next;
    this.changed({ cell: !(mirror && this.symmetry !== "none") }); // a mirrored change touches cells beyond the selected one
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
    // A row, column or layer added or removed moves every cell, so the outlines from the last check would sit on the wrong ones until the next.
    if (this.problems.size && this.dims() !== this.problemDims) this.problems = new Map();
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
    if (rebuilt) this.placeAxes();
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
    this.watchHeader();
  }

  /** The axes are measured from the header row, which changes height when the fonts arrive; they follow it. */
  watchHeader() {
    if (typeof ResizeObserver !== "function") return;
    this.headerWatch ??= new ResizeObserver(() => this.placeAxes());
    this.headerWatch.disconnect();
    const head = this.table.rows[0];
    this.headerWatch.observe(head);
    this.headerWatch.observe(head.cells[0]);
  }

  cellEl(row, col) {
    return this.tds[row]?.[col];
  }

  /** One cell's look, its label for a screen reader and its place in the tab order. */
  paintCell(td, cell) {
    const here = cell.row === this.row && cell.col === this.col;
    // A woosh cell's tint depends on the other cells' steps too, so the set of steps is part of what makes it look the same.
    const orders = this.shape.wooshOrders;
    const problem = this.problems.get(`${cell.layer},${cell.row},${cell.col}`);
    const key = `${orders.join(",")}|${problem ? problem.sig : ""}`;
    if (td.wxtCell === cell && td.wxtHere === here && td.wxtKey === key) return; // cells are never changed in place, so the same object looks the same
    td.wxtCell = cell;
    td.wxtHere = here;
    td.wxtKey = key;
    const d = E.describeCell(cell);
    const step = wooshStep(cell.woosh, orders);
    // A problem has a shape as well as a colour: errors a double border, warnings a dashed one.
    const flag = problem ? (problem.level === "error" ? " wxt-err" : " wxt-warn") : "";
    td.className = `wxt-k-${d.kind}${here ? " wxt-sel" : ""}${d.tags.length ? " wxt-marked" : ""}${step >= 0 ? " wxt-woosh" : ""}${flag}`;
    if (step >= 0) {
      td.style.setProperty("--wxt-wt", wooshTint(step, orders.length).toFixed(2));
      td.dataset.w = String(step + 1); // the step, not the raw order, as the label says
    } else {
      td.style.removeProperty("--wxt-wt");
      delete td.dataset.w;
    }
    // The :W tag and the step would say the same twice, so the label gives the step alone.
    const bare = withoutWooshTag(d.text);
    let text = step >= 0 ? `${bare}, woosh step ${step + 1} of ${orders.length}`
      : cell.woosh === 0 ? `${bare}, woosh order 0, never played` : d.text;
    if (problem) text += `. ${problem.level === "error" ? "Error" : "Warning"}: ${problem.messages.join(" ")}`;
    td.tabIndex = here ? 0 : -1;
    td.setAttribute("aria-selected", String(here));
    td.setAttribute("aria-label", `Row ${cell.row + 1}, column ${cell.col + 1}: ${text}`);
    td.title = text;
    // The letter is the cell's text and the extras a CSS label; the content is touched only when it changes, as that is what costs a layout.
    const tags = d.tags.map((t) => t.slice(1).replace("#", "")).join(" ");
    if (td.dataset.tags !== tags) td.dataset.tags = tags;
    // Where the corner badge shows the step, the label leaves out the :W tag it would repeat.
    const noW = d.tags.filter((t) => !t.startsWith(":W")).map((t) => t.slice(1).replace("#", "")).join(" ");
    if (td.dataset.tagsBare !== noW) td.dataset.tagsBare = noW;
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
    const { places, skipped } = this.mirrored();
    // A mirrored cell turned Ignored loses its light number, which the plugin would otherwise record as a light position.
    const lost = kind === "I" ? places.slice(1).filter(([r, c]) => {
      const was = E.cellAt(this.shape, this.layer, r, c);
      return was.light !== null && E.kindOf(was) !== "I";
    }).length : 0;
    const fn = (c) => {
      const mirroredCopy = c.row !== this.row || c.col !== this.col;
      const wasIgnored = E.kindOf(c) === "I";
      E.setKind(c, kind);
      if (kind === "I" && mirroredCopy && !wasIgnored) c.light = null;
    };
    if (this.editCell(fn, { mirror: true })) {
      const also = places.length - 1;
      const dropped = lost ? `, ${count(lost, "light number")} dropped from Ignored cells` : "";
      this.sayLater(`Row ${this.row + 1}, column ${this.col + 1} is now ${E.KIND_NAMES[kind]}${also ? `, and ${count(also, "mirrored cell")}` : ""}${dropped}${this.skippedNote(skipped)}.`);
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
    this.edit((s) => E.resize(s, w, h, l), `Resized to ${count(w, "column")} by ${count(h, "row")} in ${count(l, "layer")}; cells cut off at the right, bottom and top layer are gone, and Undo brings them back.`, { reframe: true });
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
    this.issues = issues;
    this.fillList(this.issueList, issues);
    this.renderChip(errors, warnings);
    this.markProblems(issues);
    if (this.flyoutOpen) this.renderFlyout();
  }

  /** The list of problems, each with a button that jumps to its cell; the side panel and the flyout both show it. */
  fillList(list, issues) {
    list.replaceChildren(...issues.slice(0, 100).map((issue) => {
      const label = `${issue.level === "error" ? "Error" : "Warning"}: ${issue.message}`;
      const inner = issue.cell
        ? el("button", { type: "button", class: "wxt-issue" }, label)
        : el("span", { class: "wxt-issue" }, label);
      if (issue.cell) inner.addEventListener("click", () => this.jump(issue.cell, list !== this.flyoutList));
      // The same shapes as the grid's borders: a double bar for an error, a dashed one for a warning.
      return el("li", { class: `wxt-${issue.level}` }, inner);
    }));
    if (issues.length > 100) list.append(el("li", {}, `And ${issues.length - 100} more.`));
  }

  /** The always-visible summary beside the grid; the live region above says it more slowly, so this one is not announced. */
  renderChip(errors, warnings) {
    const plural = (n, w) => `${n} ${w}${n === 1 ? "" : "s"}`;
    const parts = [errors ? plural(errors, "error") : "", warnings ? plural(warnings, "warning") : ""].filter(Boolean);
    this.chip.replaceChildren(
      el("span", { class: "wxt-chip-glyph", "aria-hidden": "true" }, errors ? "✕" : warnings ? "▲" : "✓"),
      document.createTextNode(` ${parts.length ? parts.join(", ") : "No problems found; reload should accept this"}`));
    this.chip.classList.toggle("wxt-chip-error", errors > 0);
    this.chip.classList.toggle("wxt-chip-warn", !errors && warnings > 0);
  }

  renderFlyout() {
    const at = [...this.flyoutList.querySelectorAll("button.wxt-issue")].indexOf(document.activeElement);
    if (this.issues.length) this.fillList(this.flyoutList, this.issues);
    else this.flyoutList.replaceChildren(el("li", {}, "No problems found; reload should accept this."));
    if (at >= 0) this.flyoutList.querySelectorAll("button.wxt-issue")[at]?.focus(); // the list is rebuilt after an edit; keep the place
  }

  /** The cells with a problem, so the grid can outline them; a cell with both an error and a warning shows the error. */
  markProblems(issues) {
    const found = new Map();
    for (const issue of issues) {
      if (!issue.cell) continue;
      const key = `${issue.cell.layer},${issue.cell.row},${issue.cell.col}`;
      const have = found.get(key);
      if (!have) found.set(key, { level: issue.level, messages: [issue.message], sig: "" });
      else {
        have.messages.push(issue.message);
        if (issue.level === "error") have.level = "error";
      }
    }
    for (const p of found.values()) p.sig = `${p.level}|${p.messages.join(" ")}`;
    // Only the cells whose problems changed are painted again.
    const old = this.problems;
    this.problems = found;
    this.problemDims = this.dims();
    for (const key of new Set([...old.keys(), ...found.keys()])) {
      if (old.get(key)?.sig === found.get(key)?.sig) continue;
      const [layer, row, col] = key.split(",").map(Number);
      if (layer === this.layer && this.tds[row]?.[col]) this.paintCell(this.tds[row][col], E.cellAt(this.shape, layer, row, col));
    }
  }

  dims() {
    return `${this.shape.width},${this.shape.height},${this.shape.layers}`;
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

  /** Selects the cell of a problem; the focus goes to it unless the list is stepping (`focus` false), and the cell is scrolled clear of the flyout. */
  jump(cell, focus = true) {
    this.layer = cell.layer;
    this.row = cell.row;
    this.col = cell.col;
    this.changed({});
    const td = this.cellEl(cell.row, cell.col);
    if (focus) td.focus();
    this.revealCell(td);
    this.markView();
    if (!focus) this.say(`Selected layer ${cell.layer}, row ${cell.row + 1}, column ${cell.col + 1}.`);
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
