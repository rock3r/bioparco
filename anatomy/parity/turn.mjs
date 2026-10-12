// Captures the golden TurnParityTest compares the Kotlin 3D mode against, from the original
// Anatomy's kit/turn-build.mjs, kit/turn.mjs and examples/turning-dial/build.mjs.
//
//   git clone https://github.com/wheresryan22/anatomy.git /tmp/anatomy
//   git -C /tmp/anatomy checkout 240a9bf7c50cc6226148762a1017466fcbc08523
//   node anatomy/parity/turn.mjs /tmp/anatomy
//
// It writes anatomy/src/jvmTest/resources/goldens/turn.txt.gz (gzipped): two scenes built with
// `turning(P, { recorder })` (a lab scene with every kind of group and part, and the turning dial
// itself), their packed data and build report, and the runtime's frames (every role's path,
// tone level, width, transform and opacity, every box and hull) and painter's order at many turn
// angles and hinge, slide and free poses, then covers, anchors and pose uniforms, and the
// materials in both themes. The cases here and in TurnParityTest must stay in the same order.
import { gzipSync } from "node:zlib";
import { writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const ORIGINAL = resolve(process.argv[2] ?? "/tmp/anatomy");
const OUT = join(HERE, "../src/jvmTest/resources/goldens");
const load = (path) => import(pathToFileURL(join(ORIGINAL, path)).href);
const k = await load("kit/iso-kit.mjs");
const G = await load("kit/lathe.mjs");
const A = await load("kit/audit.mjs");
const { turning } = await load("kit/turn-build.mjs");
const { TURN } = await load("kit/turn.mjs");

const fixed = (value, digits) => {
  if (value === Infinity) return "Infinity";
  if (value === -Infinity) return "-Infinity";
  const scale = 10 ** digits;
  const text = String(Math.round(value * scale) / scale);
  return text === "-0" ? "0" : text;
};
const num = (value) => fixed(value, 9);
const f4 = (value) => fixed(value, 4);
const list = (values, format = num) => Array.from(values, format).join(" ");

const lines = [];
const put = (name, value) => lines.push(`${name}\t${value}`);

const VERT = (x, y) => G.frameOf([x, y, 0], [0, 0, 1], [1, 0, 0], [0, 1, 0]);
const frameOf = (o, a, u) => {
  const axis = G.unit3(a);
  const uu = u ? G.unit3(G.sub3(u, G.mul3(axis, G.dot3(u, axis)))) : G.frameAlong(o, axis).u;
  return { o, a: axis, u: uu, v: G.cross3(axis, uu) };
};
const ticks = (r, count) => Array.from({ length: count }, (_, i) => {
  const t = (i / count) * Math.PI * 2;
  return `M${(Math.cos(t) * r).toFixed(3)} ${(Math.sin(t) * r).toFixed(3)}L${(Math.cos(t) * (r - 2)).toFixed(3)} ${(Math.sin(t) * (r - 2)).toFixed(3)}`;
}).join("");

/** A lab scene: every group kind and every part kind, with details, materials and splits. */
export function lab() {
  const P = k.fitProjection([[-40, -40, -8], [40, 40, 40], [-40, 40, 0], [40, -40, 0]], 560, 420, { pad: 26, azimuth: 38, elevation: 31 });
  const R = A.recorder(P);
  const T = turning(P, { recorder: R });
  const table = T.group("table", { turn: { pivot: [4, -3] } });
  const arm = T.group("arm", { parent: table, hinge: { point: [14, 4, 22], axis: [0, 1, 0], range: [-12, 12] } });
  const slider = T.group("slider", { parent: table, slide: { direction: [1, 0.25, 0], travel: 6 } });
  const bob = T.group("bob", { parent: table, free: { origin: [-10, 12, 16] } });
  const spinner = T.group("spinner", { parent: table, hinge: { point: [-8, -16, 0], axis: [0, 0, 1], range: [-30, 30] } });
  const low = T.layer("low");
  const top = T.layer("top");
  T.stack(["back", "low", "top", "front"]);
  const base = T.round({ name: "table.base", group: table, layer: low, F: VERT(4, -3), s0: 0, s1: 6, r: 30, crease: "lo", material: "steel", details: { ribs: { s0: 0.8, s1: 5.2, count: 90, seams: true, tone: "lo" }, bolts: [{ s: 6, r: 25, count: 8, size: 0.45 }] } });
  T.plane({ part: base, o: [4, -3, 6], u: [1, 0, 0], v: [0, 1, 0], svg: `<path class="iso-line" data-tone="lo" d="${ticks(27, 24)}"/>` });
  T.prism({ name: "table.block", group: table, layer: top, plan: { x: -4, y: -24, w: 14, d: 9, r: 2 }, z: 6, h: 7, bevel: 0.6, crease: "lo", material: "gold", details: { seams: [8], dots: [{ at: [13, -1, -19.5], normal: [0, 0, 1], size: 0.5 }] } });
  T.prism({ name: "table.wedge", group: table, layer: top, F: frameOf([18, -12, 8], [0.25, 0.15, 1], [1, 0, 0]), polygon: [[-3, -2], [3, -2.5], [3.5, 2], [-2, 3]], s0: 0, s1: 4, bevel: 0.35, tone: "mid", material: "chrome" });
  const bar = T.prism({ name: "table.bar", group: table, layer: top, plan: { x: 12, y: 10, w: 12, d: 4, r: 0.8 }, z: 6, h: 3, material: "gunmetal" });
  T.split(bar, { n: [1, 0, 0], d: 18 });
  T.round({ name: "arm.link", group: arm, layer: top, F: frameOf([14, 4, 22], [1, 0, 0.15], [0, 0, 1]), s0: 0.4, s1: 12, r: 2.2, ends: ["dome", "dome"], material: "gold", details: { seams: [3, 9.2] } });
  T.round({ name: "arm.knuckle", group: arm, layer: top, F: frameOf([14, 7, 22], [0, 1, 0], [0, 0, 1]), s0: 0, s1: 1.2, r: 3, material: "chrome", details: { dots: [{ at: [1.2, 0, 0], normal: [0, 1, 0], size: 0.42 }], rings: [{ at: [1.2, 0, 0], normal: [0, 1, 0], r: 0.9 }], rules: [{ points: [[1.2, -2, 0], [1.2, 2, 0]], tone: "faint" }] } });
  T.round({ name: "arm.cone", group: arm, layer: top, F: frameOf([14, 4, 22], [-1, 0, 0.3], [0, 0, 1]), s0: 2, s1: 6, r: [2.6, 1.6], tone: "mid", material: "brass", details: { ribs: { s0: 2.4, s1: 5.6, count: 40, twist: 0.4, seams: "first", tone: "lo" } } });
  T.round({ name: "arm.cap", group: arm, layer: top, F: frameOf([14, 4, 22], [0, 0, 1], [1, 0, 0]), s0: 3, s1: 4, r: 1.6, ends: ["flat", "dome"], material: "steel" });
  T.ball({ name: "table.knob", group: table, layer: top, c: [-16, 2, 10], r: 3.2, flats: [{ n: [0, 1, 0], d: 2.4 }, { n: [0, -1, 0], d: 2.4 }], material: "chrome", details: { dots: [{ at: [2.4, 0, 0], normal: [0, 1, 0], size: 0.4 }] } });
  T.ball({ name: "table.pin", group: table, layer: top, c: [-20, -14, 8], r: 2, tone: "mid", material: "brass" });
  const gland = (s) => 2.3 + 0.55 * Math.sqrt(Math.max(0, 1 - ((s - 0) / 1.6) ** 2));
  T.lathe({ name: "table.gland", group: table, layer: top, F: frameOf([-2, 16, 12], [1, 0, 0], [0, 0, 1]), profile: [[0, gland(0)], [0.8, gland(0.8)], [1.6, gland(1.6)]], radius: gland, material: "brass", details: { bolts: [{ s: 1.6, r: 1.7, count: 5, size: 0.34 }] } });
  T.prism({ name: "table.port", group: table, layer: top, plan: { x: -27, y: 18, w: 4, d: 4, r: 0.6 }, z: 6, h: 6, material: "gunmetal" });
  const route = G.fillet([[-23, 20, 9], [-14, 20, 9], [-6, 24, 9], [6, 26, 9]], 6, 12);
  T.tube({ name: "cable.lab", group: table, layer: top, route, r: 0.8, hue: "red", material: "rubber", gaps: [[0, 0.6]] });
  T.tube({ name: "hose.lab", group: table, layer: top, route: [[8, -26, 9], [8, -8, 9]], r: 0.9, rings: { pitch: 1.5, twist: 1.6, cross: true, fade: [0.1, 0.55], tone: "lo" }, material: "steel" });
  T.prism({ name: "slider.carriage", group: slider, layer: top, plan: { x: 20, y: 14, w: 6, d: 5, r: 1 }, z: 6, h: 2.4, bevel: 0.3, material: "gunmetal" });
  T.prism({ name: "bob.nut", group: bob, layer: top, F: { o: [-10, 12, 0], a: [0, 0, 1], u: [1, 0, 0], v: [0, 1, 0] }, polygon: Array.from({ length: 6 }, (_, j) => [1.6 * Math.cos(((2 * j + 1) * Math.PI) / 6), 1.6 * Math.sin(((2 * j + 1) * Math.PI) / 6)]), s0: 16, s1: 17.6, bevel: 0.2, material: "brass" });
  T.round({ name: "bob.cap", group: bob, layer: top, F: VERT(-10, 12), s0: 17.6, s1: 18.4, r: 1, material: "chrome", details: { bolts: [{ s: 18.4, r: 0, count: 1, size: 0.4 }] } });
  T.round({ name: "spinner.post", group: spinner, layer: top, F: VERT(-8, -16), s0: 6, s1: 9, r: 1.4, material: "steel" });
  T.prism({ name: "spinner.blade", group: spinner, layer: top, plan: { x: -7, y: -17, w: 7, d: 2, r: 0.4 }, z: 9, h: 1, material: "chrome" });
  const rod = T.round({ name: "table.rod", group: table, layer: top, F: frameOf([22, -2, 10], [0, 1, 0], [0, 0, 1]), s0: 0, s1: 8, r: 1.2, ends: ["dome", "flat"], material: "steel" });
  T.split(rod, { n: [0, 1, 0], d: 2 });
  T.billboard({ part: rod, at: [22, -2, 10], normal: [0, -1, 0], svg: `<circle r="1"/>` });
  R.put({ name: "stand.floor", layer: "back", svg: "", shapes: [R.solid(A.slab({ x: -44, y: -44, w: 88, d: 88, r: 4 }, -6, 5, { name: "stand.floor" }))] });
  R.put({ name: "stand.mast", layer: "back", svg: "", shapes: [R.solid(A.cylinder(-80, -80, 3, -1, 12, { name: "stand.mast" }))] });
  const poses = () => {
    const out = [];
    for (let deg = 0; deg < 360; deg += 4) out.push({ table: deg, arm: 12 * Math.sin(deg * 0.05), slider: 3 + 3 * Math.sin(deg * 0.03), spinner: 30 * Math.sin(deg * 0.02), bob: deg % 40 < 20 ? null : { q: [0.1 * Math.sin(deg), 0.2, 0.05, 1], t: [0.5, -0.3, 1 + (deg % 7) / 7] } });
    return out;
  };
  const out = T.build({ statics: R.items, poses, strict: false });
  return { T, R, P, out };
}

/**
 * A small scene built to pass the orbit audit: a turntable with a block, a post and a pawl. With
 * [broken], three faults are planted: a lug sunk into the block, a pawl long enough to swing
 * through it, and a cable climbing straight at the viewer.
 */
export function spin(broken = false) {
  const P = k.fitProjection([[-30, -30, -4], [30, 30, 12], [-30, 30, 0], [30, -30, 0]], 360, 280, { pad: 20, azimuth: 40, elevation: 31 });
  const R = A.recorder(P);
  const T = turning(P, { recorder: R });
  const table = T.group("table", { turn: { pivot: [0, 0] } });
  const pawl = T.group("pawl", { parent: table, hinge: { point: [10, 6, 0], axis: [0, 0, 1], range: [-20, 20] } });
  const live = T.layer("live");
  T.stack(["back", "live", "front"]);
  T.round({ name: "table.disc", group: table, layer: live, F: VERT(0, 0), s0: 0, s1: 3, r: 20, crease: "lo", material: "steel", details: { ribs: { s0: 0.6, s1: 2.4, count: 60, seams: true, tone: "lo" } } });
  T.prism({ name: "table.block", group: table, layer: live, plan: { x: -9, y: -7, w: 10, d: 8, r: 1 }, z: 3, h: 5, bevel: 0.4, material: "gold", details: { seams: [5] } });
  T.round({ name: "table.post", group: table, layer: live, F: VERT(10, 6), s0: 3, s1: 9, r: 1.5, material: "chrome" });
  T.prism({ name: "pawl.arm", group: pawl, layer: live, F: { o: [0, 0, 0], a: [0, 0, 1], u: [1, 0, 0], v: [0, 1, 0] }, polygon: [[8.5, 4.5], [14, 5], [14, 7], [8.5, 7.5]], s0: 9, s1: 10, bevel: 0.2, material: "brass" });
  if (broken) {
    T.prism({ name: "table.lug", group: table, layer: live, plan: { x: -2, y: -4, w: 4, d: 3, r: 0.5 }, z: 6, h: 4, material: "gunmetal" });
    T.prism({ name: "pawl.blade", group: pawl, layer: live, F: { o: [0, 0, 0], a: [0, 0, 1], u: [1, 0, 0], v: [0, 1, 0] }, polygon: [[-2, 3], [9, 3.3], [9, 4.4], [-2, 4.4]], s0: 6, s1: 7, material: "chrome" });
    T.tube({ name: "cable.bad", group: table, layer: live, route: [[-6, -3, 8], [-6 + 8 * Math.cos(0.7), -3 + 8 * Math.sin(0.7) * 0.6, 8 + 8 * Math.sin(0.54)]], r: 0.6, material: "rubber" });
  }
  R.put({ name: "stand.floor", layer: "back", svg: "", shapes: [R.solid(A.slab({ x: -30, y: -30, w: 60, d: 60, r: 3 }, -4, 4, { name: "stand.floor", ground: true }))] });
  const wiggle = (t) => ({ table: 20 + 10 * t, pawl: 20 * Math.sin(2 * Math.PI * t) });
  const poses = () => Array.from({ length: 180 }, (_, i) => ({ table: i * 2, pawl: 20 * Math.sin(i * 0.3) }));
  const out = T.build({ statics: R.items, poses, strict: !broken });
  return { T, R, P, out, wiggle };
}

function report(name, T, out) {
  const r = out.report;
  put(`${name}.report`, `${r.parts} ${r.groups}`);
  for (const l of r.layers) put(`${name}.report.${l.name}`, `${l.parts} ${l.planar} ${l.framed} ${l.groupPairs} ${l.dynamic} ${l.skip}`);
  put(`${name}.margins`, r.margins.map((m) => `${m.a}|${m.b}|${fixed(m.margin, 6)}`).join(" "));
  put(`${name}.tight`, r.tight.map((m) => `${m.a}|${m.b}`).join(" "));
  put(`${name}.proofs`, r.proofs.map((p) => `${p.static}|${p.layer}|${p.group}|${p.rule}|${p.side}`).join(" "));
  for (const [key, plane] of out.planesByName) put(`${name}.plane.${key}`, `${list(plane.n)} ${fixed(plane.d, 6)}`);
}

const vec = (v) => (v ? list(v) : "-");
const frameText = (F) => (F ? `${vec(F.o)} ${vec(F.a)} ${vec(F.u)} ${vec(F.v)}` : "-");
function data(name, d) {
  const P = d.P;
  put(`${name}.P`, `${list(P.origin)} ${num(P.scale)} ${num(P.azimuth)} ${num(P.elevation)}`);
  d.groups.forEach((g) => put(`${name}.group.${g.name}`, `${g.parent} ${g.kind} ${vec(g.origin)} ${vec(g.pivot)} ${vec(g.direction)} ${g.travel ?? "-"} ${vec(g.point)} ${vec(g.axis)} ${vec(g.range)} ${g.upright} ${Boolean(g.loose)}`));
  d.layers.forEach((l) => put(`${name}.layer.${l.name}`, `${l.parts.join(",")} rest ${l.rest.join(",")}`));
  d.parts.forEach((p, index) => {
    const at = `${name}.part.${index}`;
    put(at, `${p.name} ${p.kind} ${p.group} ${p.layer} ${p.tone ?? "hi"} ${p.crease ?? "faint"} ${Boolean(p.lit)} ${p.mat ?? "-"}`);
    const s = p.spec;
    const slots = s.slots ? `${s.slots.bevel} ${s.slots.ribs}` : "-";
    if (p.kind === "prism") put(`${at}.spec`, `${s.up} ${list(s.ring)} ${s.up ? `${num(s.z0)} ${num(s.z1)}` : `${frameText(s.F)} ${num(s.s0)} ${num(s.s1)}`} ${num(s.bevel)} ${(s.cuts ?? []).map((c) => `${vec(c.n)}/${num(c.d)}`).join(",")} ${slots}`);
    else if (p.kind === "round") put(`${at}.spec`, `${frameText(s.F)} ${num(s.s0)} ${num(s.s1)} ${num(s.r0)} ${num(s.r1)} ${s.ends.join(",")} ${(s.cuts ?? []).map((c) => `${vec(c.n)}/${num(c.d)}`).join(",")} ${slots}`);
    else if (p.kind === "ball") put(`${at}.spec`, `${vec(s.c)} ${num(s.r)} ${s.flats.map((f) => `${vec(f.n)}/${num(f.d)}`).join(",")}`);
    else if (p.kind === "lathe") put(`${at}.spec`, `${frameText(s.F)} ${list(s.levels)} ${s.caps.join(",")} ${slots}`);
    else if (p.kind === "tube") put(`${at}.spec`, `${s.route} ${num(s.from)} ${num(s.to)} ${s.open0} ${s.open1} ${s.gap0} ${s.gap1} ${s.caps.join(",")} ${s.wide} ${s.rings ? s.rings.tone : "-"} ${slots}`);
    else if (p.kind === "fixed") put(`${at}.spec`, `${vec(s.anchor)} ${list(s.home)} ${list(s.box)} ${list(s.hull)} ${frameText(s.F)} ${slots}`);
    const dt = p.details ?? {};
    if (dt.seams) put(`${at}.seams`, list(dt.seams));
    if (dt.ribs) {
      const r = dt.ribs;
      put(`${at}.ribs`, `${num(r.s0)} ${num(r.s1)} ${r.count} ${num(r.phase ?? 0)} ${num(r.twist ?? 0)} ${vec(r.fade ?? [0.1, 0.55])} ${r.seams ?? false} ${r.tone ?? "lo"} ${r.r0 === undefined ? "-" : num(r.r0)} ${r.r1 === undefined ? "-" : num(r.r1)}`);
    }
    (p.planes ?? []).forEach((pl, j) => put(`${at}.plane${j}`, `${vec(pl.o)} ${vec(pl.u)} ${vec(pl.v)} ${vec(pl.n)} ${vec(pl.fade)} ${Boolean(pl.billboard)}`));
    (p.dots ?? []).forEach((dt2, j) => put(`${at}.dot${j}`, `${vec(dt2.p)} ${vec(dt2.n)} ${num(dt2.size)} ${dt2.tone} ${vec(dt2.fade)}`));
    if (p.cloud) put(`${at}.cloud`, list(p.cloud));
  });
  d.routes.forEach((r, index) => put(`${name}.route.${index}`, `${list(r.points)} ${num(r.r)} ${num(r.spacing)} ${r.wide} ${r.hue ?? "-"}`));
  const scene = TURN.prepare(d, { unit: 1 });
  scene.layers.forEach((l) => {
    put(`${name}.planar.${l.name}`, list(l.planar));
    put(`${name}.framed.${l.name}`, list(l.framed));
    put(`${name}.groupPairs.${l.name}`, l.groupPairs.map((gp) => `${gp.a.join(",")}/${gp.b.join(",")}/${gp.frame}/${vec(gp.n)}`).join(" "));
    put(`${name}.dynamic.${l.name}`, l.dynamic.join(","));
    put(`${name}.free.${l.name}`, l.free.join(","));
  });
  scene.runs.forEach((run) => put(`${name}.run.${run.index}`, `${run.cloud.length / 3} ${list(run.cloud.slice(0, 12))} | ${run.axes.map(vec).join(",")} | ${vec(run.centroid)}`));
}

function frame(at, run, f) {
  run.roles.forEach((role, r) => {
    const key = `${at}.${role.name}`;
    const t = role.type;
    if (t === "path" || t === "face" || t === "slot") {
      put(key, f.d[r]);
      if (t === "face" && f.d[r]) put(`${key}.q`, `${f.q[r]} ${f.w[r]}`);
      if (t === "slot") put(`${key}.o`, String(Math.round(f.o[r] * 1000) / 1000));
    } else if (t === "xform" || t === "self") put(key, f.t[r]);
    else if (t === "plane") put(key, `${f.t[r]} ${f.o[r]}`);
    else if (t === "dot") put(key, `${Math.round(f.cx[r] * 100) / 100} ${Math.round(f.cy[r] * 100) / 100} ${f.o[r]}`);
  });
  put(`${at}.box`, list(f.box, f4));
  put(`${at}.hull`, f.quadN ? f.quadList.slice(0, f.quadN).map(([buf, n]) => list(buf.slice(0, 2 * n), f4)).join(" | ") : list(f.hull.slice(0, 2 * f.hullN), f4));
  put(`${at}.levels`, `${f.levels ?? 0} ${Object.entries(f.fade ?? {}).map(([p, s]) => `${p}:${s.key.join(",")}`).join(" ")}`);
}

/** Drives a scene like the controller: emits every part, orders every layer from the last order. */
function drive(name, d, states, { full = (i) => true, exactAt = -1 } = {}) {
  const scene = TURN.prepare(d, { unit: 1 });
  const frames = scene.runs.map((run) => TURN.frame(run));
  let orders = scene.layers.map((layer) => Int32Array.from(layer.rest));
  states.forEach((values, i) => {
    TURN.exact(i === exactAt);
    const cams = TURN.cams(scene, values);
    for (const run of scene.runs) TURN.emit(run, run.group >= 0 ? cams[run.group] : cams.world, frames[run.index]);
    const stats = {};
    orders = scene.layers.map((layer, index) => TURN.order(layer, frames, cams, orders[index], stats, scene));
    const at = `${name}.state.${i}`;
    scene.layers.forEach((layer, index) => put(`${at}.order.${layer.name}`, Array.from(orders[index]).join(",")));
    put(`${at}.stats`, `${stats.edges ?? 0} ${stats.forced ?? 0} ${stats.refined ?? 0} ${stats.unseparated ?? 0} ${stats.solved ?? 0}`);
    if (full(i)) for (const run of scene.runs) frame(`${at}.${run.name}`, run, frames[run.index]);
    else for (const run of scene.runs) put(`${at}.${run.name}.box`, list(frames[run.index].box, f4));
    TURN.exact(false);
  });
  return { scene, frames, orders };
}

/** Covers, anchors, pose uniforms and angles, through a stand-in controller over [driven]. */
function extras(name, d, driven, values, { covers, anchors, poses }) {
  const { scene, frames, orders } = driven;
  const cams = TURN.cams(scene, values);
  const controller = {
    scene: () => scene,
    frames: () => frames,
    cams: () => cams,
    orderIndex: (layer) => orders[scene.layers.findIndex((l) => l.name === layer)],
  };
  covers.forEach((names, j) => {
    const c = TURN.cover(controller, names);
    put(`${name}.cover.${j}`, `${c.parts.join(",")} ${c.dropped} ${Array.from(c.uCoverCount).join(",")} ${list(c.uCoverEdge, (v) => fixed(v, 5))}`);
  });
  anchors.forEach(([group, point, direction], j) => {
    const a = TURN.anchor(scene, cams, group, point, direction);
    put(`${name}.anchor.${j}`, `${vec(a.world)} ${vec(a.screen)} ${vec(a.direction)}`);
  });
  poses.forEach((group) => {
    const u = TURN.poseUniforms(controller, group, "u");
    put(`${name}.uniforms.${group}`, `${vec(u.uX)} ${vec(u.uY)} ${vec(u.uZ)} ${vec(u.uT)}`);
  });
}

const main = !process.env.TURN_LIBRARY;

// The lab scene.
if (main) {
  const { T, out, P } = lab();
  report("lab", T, out);
  data("lab", out.data);
  const states = [];
  for (let i = 0; i < 40; i++) {
    const deg = i * 9.25 - 20;
    states.push({ table: deg, arm: -12 + (i * 24) / 39, slider: (i % 7) * 0.9, spinner: 30 * Math.sin(i * 0.4), bob: i % 3 === 0 ? null : { q: [0.3 * Math.sin(i), 0.2, 0.1 * Math.cos(i), 1], t: [0.4, -0.2, 1.5] } });
  }
  states.push({ table: 33, arm: 5, slider: 2, spinner: 0, bob: { axis: [1, 1, 0], angle: 70, t: [0, 0, 3] } });
  states.push({ table: 33, arm: 5, slider: 2, spinner: 0, bob: { R: TURN.rotation([0, 1, 0], 45), t: [1, 0, 2], world: true } });
  const driven = drive("lab", out.data, states, { full: (i) => i % 6 === 0 || i >= 40, exactAt: 6 });
  extras("lab", out.data, driven, states[states.length - 1], {
    covers: [["arm.link"], [["arm.cone"], ["table.knob"]]],
    anchors: [["arm", [26, 4, 24], [1, 0, 0]], ["bob", [-10, 12, 18], null], [null, [0, 0, 0], [0, 0, 1]]],
    poses: ["table", "arm", "bob"],
  });
  put("lab.floor", `${num(TURN.floorOf(driven.scene, "bob"))} ${num(TURN.floorOf(driven.scene, "bob", TURN.rotation([1, 0, 0], 90)))}`);
}

// A scene whose interlock the build refuses.
if (main) {
  const P = { origin: [200, 150], scale: 2, azimuth: 45, elevation: 30 };
  const T = turning(P, {});
  const g = T.group("g", { turn: { pivot: [0, 0] } });
  const live = T.layer("live");
  T.prism({ name: "a", group: g, layer: live, plan: { x: 0, y: 0, w: 10, d: 10, r: 1 }, z: 0, h: 4 });
  T.prism({ name: "b", group: g, layer: live, plan: { x: 5, y: 5, w: 10, d: 10, r: 1 }, z: 2, h: 4 });
  try {
    T.build({ poses: [{ g: 0 }, { g: 90 }] });
    put("broken.build", "built");
  } catch (error) {
    put("broken.build", error.message.replace(/\n/g, " / "));
  }
}

// The turning dial of examples/turning-dial.
if (main) {
  const dial = await load("examples/turning-dial/build.mjs");
  const { out, follow, tapAt, breakAt } = dial;
  report("dial", dial.T, out);
  data("dial", out.data);
  const states = [];
  for (let i = 0; i < 24; i++) states.push(follow({ dial: i * 15.5, lift: tapAt(i / 6) }));
  for (let i = 0; i < 12; i++) states.push(follow({ dial: 100 + 12 * (i / 5), lift: 0.3, pod: breakAt(i / 5) }));
  for (let i = 0; i < 8; i++) states.push(follow({ dial: -37 + i * 0.3, lift: tapAt(1 + i / 30) }));
  const driven = drive("dial", out.data, states, { full: (i) => [0, 3, 17, 30, 41].includes(i), exactAt: 3 });
  const last = states[states.length - 1];
  extras("dial", out.data, driven, last, {
    covers: [["hand.f2.p3"], [["hand.f2.p3"], ["hand.pod.socket"]]],
    anchors: [[dial.LED[0].group, dial.LED[0].at, dial.LED[0].normal], ["dial", dial.POD_AT, null]],
    poses: ["dial", "f2.dip", "pod"],
  });
  put("dial.floor", num(TURN.floorOf(driven.scene, "pod")));
}

// The runtime's trigonometry as Node computes it (V8's fdlibm with fused multiply-adds): the
// Kotlin side must match it bit for bit, since the runtime turns some last bits into decisions.
if (main) {
  let seed = 4242;
  const rand = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
  const bits = (v) => {
    const view = new DataView(new ArrayBuffer(8));
    view.setFloat64(0, v);
    return view.getBigUint64(0).toString(16);
  };
  for (let i = 0; i < 400; i++) {
    const x = (rand() * 2 - 1) * [0.8, 3.2, 7, 60, 400][i % 5];
    const y = rand() * 2 - 1;
    const z = rand() * 2 - 1;
    put(`math.${i}`, [x, y, z, Math.sin(x), Math.cos(x), Math.acos(y), Math.atan2(y, z)].map(bits).join(" "));
  }
}

// The materials in both themes, as their CSS sets the tokens.
if (main) {
put("materials", TURN.materials.join(" "));
put("materials.css", TURN.materialCss().split("\n").filter((rule) => rule.startsWith('.iso[data-theme="')).join(" "));

const text = lines.join("\n") + "\n";
writeFileSync(join(OUT, "turn.txt.gz"), gzipSync(text, { level: 9 }));
console.log(`wrote ${lines.length} turn results, ${text.length} bytes`);
}
