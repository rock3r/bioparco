// @vitest-environment jsdom
/*
 * Captures the parity goldens for bioparco's Kotlin port of hairline. Not part of bioparco's build:
 * copy this file and capture-script.ts into the original's packages/hairline/test/ (it uses that
 * folder's dom.ts), then run `HAIRLINE_GOLDENS=<dir> npx vitest run test/capture.test.ts` and gzip
 * each <figure>.txt into hairline/src/jvmTest/resources/goldens/. Each figure runs in its own test,
 * so each starts on dom.ts's fresh clock (1000ms), as ParityTest's fake clock does.
 */
import { mkdirSync, writeFileSync } from "node:fs";
import { it, vi } from "vitest";
import { frames, host } from "./dom";
import * as all from "../src/index";
import { SCRIPT, RIFFLE_KEYS, type Step } from "./capture-script";

const OUT = process.env.HAIRLINE_GOLDENS ?? "goldens";
const IDS = ["riffle", "terrain", "exploded", "phosphor", "slow", "turntable", "keyboard", "elevator", "phone", "laptop", "terminal", "cabinet", "branches", "vault", "lockers", "padlock", "patch", "dish", "router"] as const;

const KEEP = ["d", "cx", "cy", "r", "rx", "ry", "opacity", "visibility"];

function fadeOf(svg: SVGSVGElement, url: string): string {
  const id = url.slice(5, -1);
  const lg = svg.querySelector(`#${id}g`)!;
  const a0 = lg.querySelector("stop")!.getAttribute("stop-opacity");
  return `${lg.getAttribute("y1")},${lg.getAttribute("y2")},${a0}`;
}

function dump(svg: SVGSVGElement, el: Element, depth: number, out: string[]) {
  for (const c of Array.from(el.children)) {
    if (c.tagName === "defs") continue;
    const cls = (c.getAttribute("class") ?? "").split(/\s+/).filter(Boolean).sort();
    const attrs: string[] = [];
    for (const k of KEEP) {
      const v = c.getAttribute(k);
      if (v !== null && v !== "") attrs.push(`${k}=${v}`);
    }
    const m = c.getAttribute("mask");
    if (m) attrs.push(`mask=${fadeOf(svg, m)}`);
    out.push("  ".repeat(depth) + c.tagName + (cls.length ? "." + cls.join(".") : "") + (attrs.length ? "|" + attrs.join("|") : ""));
    dump(svg, c, depth + 1, out);
  }
}

function play(id: (typeof IDS)[number], steps: Step[]) {
  const el = host();
  let read = "";
  const f = (all as Record<string, (el: HTMLElement, o?: object) => { update(o: object): void; destroy(): void }>)[id](el, {
    onRead: (t: string) => { read = t; },
  });
  const svg = el.querySelector("svg")!;
  const out: string[] = [];
  const fire = (type: string, pt: [number, number] | null) =>
    el.dispatchEvent(new PointerEvent(type, {
      pointerType: "mouse", pointerId: 1, bubbles: type !== "pointerleave",
      clientX: pt ? pt[0] : -40, clientY: pt ? pt[1] : -40,
    }));
  for (const s of steps) {
    if ("adv" in s) { out.push(`> adv ${s.adv}`); frames(s.adv); }
    else if ("move" in s) { out.push(`> move ${s.move[0]} ${s.move[1]}`); fire("pointermove", s.move); }
    else if ("out" in s) { out.push("> out"); fire("pointerleave", null); vi.runOnlyPendingTimers(); }
    else if ("key" in s) { out.push(`> key ${s.key}`); el.dispatchEvent(new KeyboardEvent("keydown", { key: s.key, bubbles: true, cancelable: true })); }
    else if ("blur" in s) { out.push("> blur"); el.dispatchEvent(new FocusEvent("blur")); }
    else if ("set" in s) { out.push(`> set ${s.set}`); f.update({ intensity: s.set }); }
    else if ("cp" in s) {
      out.push(`@ ${s.cp}`, `read ${read}`);
      dump(svg, svg, 0, out);
    }
  }
  f.destroy();
  return out.join("\n") + "\n";
}

it.each(IDS)("captures %s", (id) => {
  // jsdom has no PointerEvent
  if (!("PointerEvent" in globalThis)) {
    class PE extends MouseEvent {
      pointerType: string; pointerId: number;
      constructor(t: string, i: PointerEventInit) { super(t, i); this.pointerType = i.pointerType ?? "mouse"; this.pointerId = i.pointerId ?? 1; }
    }
    vi.stubGlobal("PointerEvent", PE);
  }
  vi.useFakeTimers({ toFake: ["setTimeout", "clearTimeout"] });
  mkdirSync(OUT, { recursive: true });
  writeFileSync(`${OUT}/${id}.txt`, play(id, id === "riffle" ? [...SCRIPT, ...RIFFLE_KEYS] : SCRIPT));
  vi.useRealTimers();
});
