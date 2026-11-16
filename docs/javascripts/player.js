// Playback controls for a timeline counted in game ticks: play or pause, step either way, restart,
// speed and a position slider. Every control is a native button, select or range input, so the
// keyboard and screen readers work without extra code.
import { el, TICK_MS } from "./scene-kit.js";

const SPEEDS = [["0.25", "Quarter speed"], ["0.5", "Half speed"], ["1", "Game speed"], ["2", "Double speed"]];

export class Player {
  /**
   * @param {(tick: number) => void} onTick called whenever the position changes
   * @param {(tick: number) => string} valueText the position said in words, for the slider
   */
  constructor(onTick, valueText) {
    this.onTick = onTick;
    this.valueText = valueText;
    this.tick = 0;
    this.total = 0;
    this.playing = false;
    this.speed = 0.5;
    this.carry = 0;
    this.stamp = 0;
    this.handle = 0; // the pending animation frame, so a second play cannot start a second loop
    this.onPause = null;

    this.playButton = el("button", { type: "button", class: "md-button" }, "Play");
    this.back = el("button", { type: "button", class: "md-button", "aria-label": "Step back one tick" }, "Step back");
    this.forward = el("button", { type: "button", class: "md-button", "aria-label": "Step forward one tick" }, "Step");
    this.restart = el("button", { type: "button", class: "md-button" }, "Restart");
    this.speedSelect = el("select", { id: "wxt-speed", "aria-label": "Speed" },
      ...SPEEDS.map(([v, label]) => el("option", { value: v }, label)));
    this.speedSelect.value = String(this.speed);
    this.slider = el("input", { type: "range", min: "0", max: "0", step: "1", value: "0", id: "wxt-position",
      "aria-label": "Position in the animation, in game ticks" });
    this.readout = el("span", { class: "wxt-readout", "aria-hidden": "true" });
    this.el = el("div", { class: "wxt-player", role: "group", "aria-label": "Playback" },
      el("div", { class: "wxt-bar" }, this.playButton, this.back, this.forward, this.restart, this.speedSelect),
      el("div", { class: "wxt-scrub" }, this.slider, this.readout));

    this.playButton.addEventListener("click", () => (this.playing ? this.pause() : this.play()));
    this.back.addEventListener("click", () => this.step(-1));
    this.forward.addEventListener("click", () => this.step(1));
    this.restart.addEventListener("click", () => { this.seek(0); this.play(); });
    this.speedSelect.addEventListener("change", () => { this.speed = Number(this.speedSelect.value); });
    this.slider.addEventListener("input", () => { this.pause(); this.seek(Number(this.slider.value)); });
  }

  setTotal(total) {
    this.total = Math.max(0, total);
    this.slider.max = String(this.total);
  }

  seek(tick) {
    this.tick = Math.min(this.total, Math.max(0, Math.round(tick)));
    this.slider.value = String(this.tick);
    this.slider.setAttribute("aria-valuetext", this.valueText(this.tick));
    this.readout.textContent = `tick ${this.tick} of ${this.total} (${(this.tick * TICK_MS / 1000).toFixed(1)} s of ${(this.total * TICK_MS / 1000).toFixed(1)} s)`;
    this.onTick(this.tick);
  }

  step(by) {
    this.pause();
    this.seek(this.tick + by);
  }

  play() {
    if (this.playing) return;
    if (this.tick >= this.total) this.seek(0);
    this.playing = true;
    this.carry = 0;
    this.stamp = 0;
    this.playButton.textContent = "Pause";
    this.handle = requestAnimationFrame((now) => this.frame(now));
  }

  pause() {
    const was = this.playing;
    cancelAnimationFrame(this.handle);
    this.playing = false;
    this.playButton.textContent = "Play";
    this.slider.setAttribute("aria-valuetext", this.valueText(this.tick));
    if (was && this.onPause) this.onPause();
  }

  frame(now) {
    if (!this.playing) return;
    // A page that was in a background tab does not fast-forward when it comes back.
    if (this.stamp) this.carry += (Math.min(now - this.stamp, 250) / TICK_MS) * this.speed;
    this.stamp = now;
    const whole = Math.floor(this.carry);
    if (whole > 0) {
      this.carry -= whole;
      this.seek(this.tick + whole);
      if (this.tick >= this.total) { this.pause(); return; }
    }
    this.handle = requestAnimationFrame((t) => this.frame(t));
  }
}
