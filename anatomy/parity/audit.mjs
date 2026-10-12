// Captures the golden AuditParityTest compares the Kotlin audit against, from the original
// Anatomy's kit/audit.mjs.
//
//   git clone https://github.com/wheresryan22/anatomy.git /tmp/anatomy
//   git -C /tmp/anatomy checkout 240a9bf7c50cc6226148762a1017466fcbc08523
//   node anatomy/parity/audit.mjs /tmp/anatomy
//
// It writes anatomy/src/jvmTest/resources/goldens/audit.txt.gz (gzipped): audit.mjs's functions
// called with the cases below, one result per line. The cases here and in AuditParityTest (and
// AuditScenes) must stay in the same order. Lines whose name starts with `approx.` hold screen boxes
// measured from SVG markup, which the original writes rounded to 0.1; the Kotlin side measures its
// art unrounded, so the test compares those within 0.06.
import { gzipSync } from "node:zlib";
import { writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const ORIGINAL = resolve(process.argv[2] ?? "/tmp/anatomy");
const OUT = join(HERE, "../src/jvmTest/resources/goldens");
const A = await import(pathToFileURL(join(ORIGINAL, "kit/audit.mjs")).href);
const G = await import(pathToFileURL(join(ORIGINAL, "kit/lathe.mjs")).href);
const K = await import(pathToFileURL(join(ORIGINAL, "kit/iso-kit.mjs")).href);

const fixed = (value, digits) => {
  if (value === Infinity) return "Infinity";
  if (value === -Infinity) return "-Infinity";
  if (Number.isNaN(value)) return "NaN";
  const scale = 10 ** digits;
  const text = String(Math.round(value * scale) / scale);
  return text === "-0" ? "0" : text;
};
const num = (value) => fixed(value, 9);
const p3 = (p) => p.map(num).join(",");
const box = (b) => (b ? b.map((v) => fixed(v, 4)).join(",") : "null");

const lines = [];
const put = (name, value) => lines.push(`${name}\t${value}`);

const P = { origin: [300, 220], scale: 2 };
const P57 = { origin: [310, 200], scale: 2.4, azimuth: 57, elevation: 36 };
const UP = G.frameOf([0, 0, 0], [0, 0, 1], [1, 0, 0], [0, 1, 0]);
const TILT = G.frameAlong([-6, 4, 12], [0.4, -0.7, 0.6]);
const SIDE = G.frameAlong([10, -5, 8], [1, 0, 0]);
const BELL = [[0, 12], [3, 11.6, 1], [8, 9.8, 1], [14, 7.2, 1], [20, 5.4, 1], [26, 4.8]];
const WOBBLY = [[0, 10], [3, 9.7, 1], [6, 8.2, 1], [9, 8.0, 1], [12, 6.2, 1], [15, 6]];
const throat = (s) => Math.sqrt(36 + (0.5 * (s - 10)) ** 2);
const THROAT = [0, 4, 8, 12, 16, 20].map((s) => [s, throat(s)]);
const LEGS = [[-8, -4, 2], [6, -4, 2], [6, 7, 9], [-3, 9, 14]];

const PROBES = Array.from({ length: 40 }, (_, i) => [((i * 7) % 13 - 6) * 1.7 + 0.3, ((i * 5) % 11 - 5) * 1.9 - 0.4, ((i * 3) % 9) * 1.6 - 1.1 + (i % 4) * 0.7]);

function shapeCases() {
  const cutter = A.ball([0, 0, 1], 3.5, { name: "cutter" });
  const plug = A.cylinder(2, 2, 1.5, -2, 10, { name: "plug" });
  return [
    ["body", A.body(TILT, [[0, 0], [0, 5], [6, 7], [10, 3], [10, -1]], { name: "body" })],
    ["lathe.ring", A.lathe(SIDE, [[0, 6], [0, 10], [12, 10], [12, 6]])],
    ["lathe.smooth", A.lathe(UP, [[0, 0], [0, 4], [3, 6, 1], [6, 7, 1], [9, 6, 1], [12, 4], [12, 0]])],
    ["solid.bell", A.solid(TILT, BELL)],
    ["solid.radius", A.solid(SIDE, THROAT, { radius: throat })],
    ["prism", A.prism(TILT, [[-5, -3], [5, -3], [5, 3], [-5, 3]], 3, -2)],
    ["extruded", A.extruded([[0, 0], [8, 0], [8, 3], [3, 3], [3, 9], [0, 9]], 2, 5)],
    ["slab", A.slab({ x: -10, y: -6, w: 20, d: 12, r: 3 }, 0, 4)],
    ["slab.steps", A.slab({ x: -4, y: -3, w: 9, d: 6, r: 2 }, 1, 2, {}, 3)],
    ["cylinder", A.cylinder(3, -2, 4, 1, 8)],
    ["disc", A.disc(TILT, -1, 1, 6)],
    ["ring", A.ring(TILT, 0, 3, 4, 7)],
    ["box", A.box([-3, -2, 0], [5, 4, 6])],
    ["orientedBox", A.orientedBox([1, 2, 3], [[1, 1, 0], [-1, 1, 0], [0, 0, 1]], [6, 1.5, 2])],
    ["ball", A.ball([2, -1, 5], 4)],
    ["ball.flats", A.ball([2, -1, 5], 4, { flats: [[0, 0, 1, 2], [1, 0, 0, 3]] })],
    ["tube", A.tube(LEGS, 1.2)],
    ["tube.function", A.tube(LEGS, (t) => 1 + t)],
    ["tube.list", A.tube(LEGS, [0.8, 1.4, 1.4, 0.6])],
    ["tube.round", A.tube(LEGS, 1, { round: [true, true] })],
    ["slide", A.slide(SIDE, 0, 8, 1.5)],
    ["cut.one", A.disc(UP, 0, 3, 5, { cut: cutter })],
    ["cut.list", A.box([-4, -4, 0], [4, 4, 6], { cut: [cutter, plug] })],
  ];
}

const OFFSET = [3, -2, 1.5];

function shapes() {
  for (const [name, shape] of shapeCases()) {
    const b = A.boundsOf(shape);
    put(`bounds.${name}`, `${p3(b.min)} ${p3(b.max)}`);
    put(`sdf.${name}`, PROBES.map((p) => num(A.sdf(shape, p))).join(" "));
    const moved = A.moved(shape, OFFSET);
    const mb = A.boundsOf(moved);
    put(`moved.${name}`, `${p3(mb.min)} ${p3(mb.max)} ${PROBES.slice(0, 12).map((p) => num(A.sdf(moved, p))).join(" ")}`);
  }
  const slide = A.slide(SIDE, 0, 8, 1.5);
  const out = A.moved(slide, [5, 1, 0]);
  put("moved.slide.stretch", `${out.poly.map(([s, r]) => `${num(s)}/${num(r)}`).join(" ")} ${num(out.slide.to)}`);
  const back = A.moved(slide, [-20, 0, 0]);
  put("moved.slide.back", `${back.poly.map(([s, r]) => `${num(s)}/${num(r)}`).join(" ")}`);
  put("moved.zero", String(A.moved(slide, [0, 0, 0]) === slide));
  put("traced.bell", A.traced(BELL).map(([s, r]) => `${num(s)}/${num(r)}`).join(" "));
  put("traced.radius", A.traced(THROAT, { radius: throat, error: 0.001, depth: 4 }).map(([s, r]) => `${num(s)}/${num(r)}`).join(" "));
  for (const [name, cam] of [["P", P], ["P57", P57]]) {
    const c = A.cameraBasis(cam);
    put(`cameraBasis.${name}`, `${p3(c.R)} ${p3(c.D)} ${p3(c.V)} ${num(c.k)}`);
  }
}

const shares = (state) => (typeof state === "number" ? num(state) : `${state.label}=${Object.entries(state.shares).map(([id, x]) => `${id}:${num(x)}`).join(",")}`);

function states() {
  const order = ["base", "arm", "lid"];
  const list = (name, states) => {
    put(`${name}.count`, states.length);
    states.forEach((state, index) => put(`${name}.${index}`, shares(state)));
  };
  list("springStates", A.springStates(order, { stride: 5, reversals: [0.05] }));
  list("springStates.custom", A.springStates(["a", "b"], { stagger: 0.05, stiffness: 80, damping: 12, substeps: 2, fps: 30, settle: 1e-2 }));
  put("uniformStates", A.uniformStates(4).map(A.labelOf).join("|"));
  put("uniformStates.default", A.uniformStates().map(A.labelOf).join("|"));
  put("reversalsOf", A.reversalsOf(order).map(num).join(" "));
  put("reversalsOf.custom", A.reversalsOf(order, 0.05, 0.01).map(num).join(" "));
  list("prefixStates", A.prefixStates(order));
  list("prefixStates.custom", A.prefixStates(order, { shares: [0.5, 1] }));
  const explode = A.explodeStates(order, {}, { uniform: 3, stride: 6 });
  put("explodeStates.counts", `${explode.depth.length} ${explode.frames.length} ${explode.apart}`);
  put("explodeStates.depth", explode.depth.map(A.labelOf).join("|"));
  const none = A.explodeStates([]);
  put("explodeStates.none", `${none.depth.join(",")} ${none.frames.length} ${none.apart}`);
  const item = { move: [2, 0, -4], piece: "arm", ride: "lid" };
  const staged = { label: "x", shares: { lid: 0.25, arm: 0.75 } };
  put("shareOf", `${num(A.shareOf(item, 0.4))} ${num(A.shareOf(item, staged))} ${num(A.shareOf({ piece: "arm" }, staged))} ${num(A.shareOf({ piece: "zz" }, staged))}`);
  put("offsetOf", `${p3(A.offsetOf(item, 0.4))} ${p3(A.offsetOf(item, staged))} ${p3(A.offsetOf({ piece: "arm" }, staged))}`);
}

// The overlap scene: a deck, a post, a pipe in two chunks, a lid that lifts with a rod sliding out
// of the post, and two bars that interlock. Keys are put in deliberately wrong.
function overlapScene() {
  const R = A.recorder(P);
  R.put({ name: "deck", key: 5, shapes: [A.slab({ x: -14, y: -10, w: 28, d: 20, r: 3 }, 0, 3)] });
  R.put({ name: "post", key: -3, shapes: [A.cylinder(0, 0, 3, 3, 12)] });
  const pipe = A.tube([[-12, 6, 8], [0, 6, 8], [10, 6, 8]], 1.2);
  R.put({ name: "pipe:0", key: -4, route: "pipe", chunk: 0, shapes: [A.tube(G.cut(pipe.points, 0, 12), 1.2)] });
  R.put({ name: "pipe:1", key: -4, route: "pipe", chunk: 1, shapes: [A.tube(G.cut(pipe.points, 12, 22), 1.2)] });
  R.put({ name: "lid", piece: "lid", move: [0, 0, 10], key: 1, shapes: [A.box([-4, -4, 15], [4, 4, 17])] });
  R.put({ name: "rod", piece: "lid", move: [0, 0, 10], key: 0.5, shapes: [A.slide(G.frameOf([0, 0, 15], [0, 0, -1], [1, 0, 0], [0, -1, 0]), 0, 6, 1)] });
  R.put({ name: "cross.a", key: 2, shapes: [A.box([4, -12, 4], [12, -10, 6])] });
  R.put({ name: "cross.b", key: 2, shapes: [A.box([7, -14, 3], [9, -8, 7])] });
  R.put({ name: "bare", key: 0 });
  return R;
}

function overlaps() {
  const R = overlapScene();
  const states = [0, 1, 0.5, { label: "custom", shares: { lid: 0.3 } }];
  const result = A.overlapsOf(R.items, P, { states, skip: A.sameRun });
  put("overlaps.count", `${result.pairs.length} ${result.least} ${result.step} ${p3(result.V)}`);
  result.pairs.forEach((pair, index) => {
    const side = (s) => `${s.all}/${s.inner}/${num(s.area)}/${num(s.depth)}/${s.at ? s.at.map(num).join(":") : "-"}`;
    put(`overlaps.${index}`, `${A.labelOf(pair.state)} ${pair.a} ${pair.b} ${side(pair.front[0])} ${side(pair.front[1])}`);
  });
  const wrong = A.depthOrder(R.items, P, { overlaps: result });
  depthLines("depthOrder.before", wrong);
  const solved = A.orderKeys(R.items, result);
  put("orderKeys", `${solved.keys.map(num).join(" ")} rules ${solved.rules} cycles ${solved.cycles.length} dropped ${solved.dropped.length}`);
  R.items.forEach((item, index) => (item.key = solved.keys[index]));
  depthLines("depthOrder.after", A.depthOrder(R.items, P, { overlaps: result, solved }));
  depthLines("depthOrder.allow", A.depthOrder(R.items, P, { overlaps: result, solved, allow: [["cross.*", "cross.b"]] }));
  put("keyAt", R.items.map((item) => num(A.keyAt(item, { label: "k", shares: { lid: 0.6 } }, result.V))).join(" "));
  const loop = {
    pairs: [
      { state: 0, a: 0, b: 1, front: [{ area: 3 }, { area: 0 }] },
      { state: 0, a: 1, b: 2, front: [{ area: 2 }, { area: 0 }] },
      { state: 0, a: 2, b: 0, front: [{ area: 1 }, { area: 0 }] },
      { state: 1, a: 3, b: 0, front: [{ area: 0 }, { area: 4 }] },
      { state: 1, a: 3, b: 0, front: [{ area: 0 }, { area: 6 }] },
    ],
    least: 0.25,
    V: A.cameraBasis(P).V,
  };
  const items = [{ key: 0 }, { key: 1 }, { key: 2 }, { key: 0.5, move: [0, 0, 5] }];
  const looped = A.orderKeys(items, loop);
  put("orderKeys.loop", `${looped.keys.map(num).join(" ")} rules ${looped.rules} dropped ${looped.dropped.map((r) => `${r.front}>${r.back}@${num(r.weight)}/${num(r.need)}`).join(",")}`);
  const limited = A.orderKeys(items, loop, { gap: 0.5, rounds: 2 });
  put("orderKeys.rounds", `${limited.keys.map(num).join(" ")} dropped ${limited.dropped.length}`);
}

function depthLines(name, result) {
  put(`${name}.count`, `${result.problems.length} ${result.passing.length} ${result.missing.join(",")}`);
  for (const [kind, list] of [["problem", result.problems], ["passing", result.passing]])
    list.forEach((d, index) => put(`${name}.${kind}.${index}`, `${d.label} ${d.front} ${d.back} ${num(d.frontKey)} ${num(d.backKey)} ${d.cells} ${num(d.area)} ${num(d.against)} ${d.cycle} ${num(d.depth)} ${d.at.map(num).join(":")} ${d.unorderable}`));
}

const WALL = () => A.box([10, -8, 4], [12, 8, 20], { name: "wall" });

// The check scene: a deck on the ground, a block with a port, a wall a pipe passes through, a
// cable through that pipe, a floating box, a sunk box, a sense line with loose ends, a pipe that
// doubles back on screen, a lid that comes down into the block apart, a seated pad, a stand-in.
function checkScene(art) {
  const R = A.recorder(P, { order: ["lid", "pipes"] });
  const put_ = (name, shapes, extra = {}) => R.put({ name, at: A.boundsOf(shapes[0]).max, shapes, ...extra });
  const deck = R.solid(A.slab({ x: -30, y: -20, w: 60, d: 40, r: 4 }, 0, 4, { name: "stand.deck" }));
  put_("stand.deck", [deck]);
  const block = R.solid(A.box([-6, -6, 4], [6, 6, 14], { name: "block" }));
  put_("block", [block]);
  const port = R.solid(A.disc(G.frameOf([0, 0, 14], [0, 0, 1], [1, 0, 0], [0, 1, 0]), 0, 1, 2, { name: "block.port" }));
  put_("block.port", [port]);
  const wall = R.solid(WALL());
  put_("wall", [wall]);
  R.solid(A.box([14, 8, 20], [18, 12, 24], { name: "floater" }));
  R.solid(A.box([-20, -15, 2], [-14, -9, 8], { name: "sunk" }));
  const lid = R.solid(A.box([-5, -5, 22], [5, 5, 24], { name: "lid" }));
  put_("lid", [lid], { piece: "lid", move: [0, 0, -12] });
  const rail = R.solid(A.box([-24, 10, 4], [-22, 12, 22], { name: "rail" }));
  put_("rail", [rail]);
  const host = R.solid(A.cylinder(-16, 0, 4, 4, 10, { name: "drum" }));
  put_("drum", [host]);
  const pad = R.solid(A.disc(G.frameOf([-11.5, 0, 9], [1, 0, 0], [0, 1, 0], [0, 0, 1]), -1, 0.5, 1.5, { name: "drum.pad", seated: true, cut: host }));
  put_("drum.pad", [pad], { piece: "pipes", move: [6, 0, 0] });
  const pad2 = R.solid(A.disc(G.frameOf([-16, 0, 13.5], [0, 0, 1], [1, 0, 0], [0, 1, 0]), -1, 1, 1.2, { name: "drum.cap", seated: true, cut: [host] }));
  put_("drum.cap", [pad2]);
  R.solid(A.solid(G.frameOf([22, -14, 4], [0, 0, 1], [1, 0, 0], [0, 1, 0]), WOBBLY, { name: "vase", curve: WOBBLY }));
  R.solid(A.solid(G.frameOf([-24, -14, 4], [0, 0, 1], [1, 0, 0], [0, 1, 0]), BELL.map(([s, r, k]) => (k ? [s / 3, r / 3, k] : [s / 3, r / 3])), { name: "bell", curve: BELL.map(([s, r, k]) => (k ? [s / 3, r / 3, k] : [s / 3, r / 3])) }));
  const routes = [
    ["feed", [[0, 0, 15], [0, 0, 18], [24, 0, 18], [24, 0, 4]], 1, {}],
    ["cable", [[5, -14, 4], [5, -14, 18], [5, 14, 18], [5, 14, 4]], 0.4, { limp: true }],
    ["sense", [[6.7, -10, 8], [6.7, 10, 8]], 0.5, {}],
    ["hose", [[-26, -4, 4], [-26, -4, 12], [-20, -4, 16]], 0.8, { free: [false, true], piece: "pipes", move: [0, -6, 0] }],
    ["drop", [[26, 18, 24], [16, 8, 24], [16, 8, 4]], 0.9, {}],
    ["tap", [[-23, 18, 4], [-23, 18, 14], [-23, 12.5, 14]], 0.6, {}],
    ["gapped", [[-8, 16, 4], [-8, 16, 12], [8, 16, 12], [8, 16, 4]], 0.7, { gaps: [[10, 12]], rims: [1.2, 1.2] }],
    ["upper", [[-10, -32, 20], [10, -32, 20]], 0.8, {}],
    ["lower", [[0, -29, 40], [0, -29, 24.2]], 0.6, { owner: "low", bundle: "harness" }],
  ];
  for (const [name, points, r, options] of routes) {
    R.route(name, points, r, options);
    R.put({ name: `${name}:0`, at: points[0], route: name, chunk: 0, shapes: [A.tube(points, r)], piece: options.piece ?? "", move: options.move ?? [0, 0, 0] });
  }
  const post = R.solid(A.box([-1, -40, 0], [1, -38, 20], { name: "post" }));
  put_("post", [post]);
  const clamp = R.solid(A.disc(G.frameAlong([-3, -32, 20], [1, 0, 0]), -0.9, 0.9, 1.4, { name: "clamp", owner: "upper", fitting: true, anchor: false }));
  put_("clamp", [clamp]);
  R.put({ name: "knot:0", at: [20, -8, 12], route: "knot", chunk: 0, shapes: [A.tube(KNOT, 2.2)] });
  R.put({ name: "standin", at: [-2, -16, 8], svg: art.standin, shapes: [A.ball([-2, -16, 8], 0.5)] });
  R.put({ name: "good", at: [2, -16, 8], svg: art.good, shapes: [A.cylinder(-12, 14, 2, 4, 6)] });
  return R;
}

const KNOT = G.fillet([[20, -8, 6], [20, -8, 12], [21.5, -6.5, 12.6], [26, -12, 13]], 0.6);

function clearanceLine(c) {
  return `${c.tube} ${c.other} ${c.kind} ${num(c.clearance)} ${num(c.need)} ${p3(c.at)} ${num(c.from)} ${num(c.to)} ${num(c.length)} ${c.state}|${Boolean(c.solid)}`;
}

function checks(art) {
  const R = checkScene(art);
  const mounts = A.mountsOf(R.tubes, R.solids);
  R.tubes.forEach((tube, index) =>
    put(`mountsOf.${index}`, `${tube.name} ${mounts.get(tube).map((m) => (m ? `${m.shape.name}/${num(m.score)}/${num(m.d)}/${num(m.high)}/${num(m.low)}/${m.landed}/${m.host ? m.host.name : "-"}` : "null")).join(" ")}`),
  );
  const list = (name, items, line) => {
    put(`${name}.count`, items.length);
    items.forEach((item, index) => put(`${name}.${index}`, line(item)));
  };
  list("clearances", A.clearances(R.tubes, R.solids, { mounts }), clearanceLine);
  list("clearances.apart", A.clearances(R.tubes, R.solids, { state: 1, mounts, only: "through" }), clearanceLine);
  list("clearances.wide", A.clearances(R.tubes, R.solids, { ratio: 2, least: 1, spacing: 1.5 }), clearanceLine);
  const term = (e) => `${e.tube} ${e.end} ${p3(e.at)} ${num(e.d)} ${num(e.high)} ${num(e.low)} ${e.nearest}`;
  list("terminals", A.terminals(R.tubes, R.solids, { mounts }), term);
  list("terminals.loose", A.terminals(R.tubes, R.solids, { tolerance: 0.6 }), term);
  const loose = (e) => `${e.tube} ${e.end} ${p3(e.at)} ${e.state} ${e.free}`;
  list("looseEnds", A.looseEnds(R.tubes, R.solids), loose);
  list("looseEnds.home", A.looseEnds(R.tubes, R.solids, { state: 0 }), loose);
  const held = A.supports(R.solids, R.tubes);
  put("supports", `${held.loose.join(",")} | ${held.hanging.join(",")} | ${held.sunk.map((d) => `${d.a}×${d.b}@${num(d.depth)}`).join(",")}`);
  const strict = A.supports(R.solids, R.tubes, { ground: (shape) => shape.name === "block", touch: 0.05, sunk: 0.1 });
  put("supports.custom", `${strict.loose.join(",")} | ${strict.hanging.join(",")} | ${strict.sunk.map((d) => `${d.a}×${d.b}@${num(d.depth)}`).join(",")}`);
  A.settle(R, P);
  put("settle.keys", R.items.map((item) => `${item.name}=${num(item.key)}`).join(" "));
  const fold = (b) => `${b.name} ${b.kind} ${num(b.flatTurn)} ${num(b.realTurn ?? 0)} ${num(b.radius ?? 0)} ${num(b.width ?? 0)} ${p3(b.at)}`;
  list("folds", A.folds(R.routes, P), fold);
  list("folds.items", A.folds(R.routes, P, { items: R.items }), fold);
  list("folds.custom", A.folds(R.routes, P57, { fold: 100, turn: 120, pinch: 4 }), fold);
  const links = A.linksOf(R.tubes, mounts);
  put("linksOf", [...links].map(([a, set]) => `${a}:${[...set].join("+")}`).join(" "));
  const rods = A.slenderOf(R.solids);
  put("slenderOf", rods.map((r) => `${r.name}/${r.solid}/${[...r.owners].join("+")}/${p3(r.points[0])}/${p3(r.points[1])}/${num(r.r)}`).join(" "));
  put("slenderOf.custom", A.slenderOf(R.solids, { ratio: 2, least: 1 }).map((r) => r.solid).join(" "));
  const crowd = (c) => `${c.a} ${c.b} ${c.where} ${num(c.gap)} ${num(c.depth)} ${num(c.need)} ${c.screen.map(num).join(":")} ${p3(c.at)} ${c.state}`;
  list("crowding.bare", A.crowding(R.routes, P), crowd);
  list("crowding", A.crowding(R.routes, P, { links, items: R.items, rods }), crowd);
  list("crowding.apart", A.crowding(R.routes, P, { links, items: R.items, rods, state: 1, allow: [["tap", "rail*"]] }), crowd);
  list("selfOverlaps", A.selfOverlaps(R.items, P), (c) => `${c.name} ${c.route} ${num(c.radius)} ${num(c.width)} ${p3(c.at)} ${c.screen.map(num).join(":")}`);
  list("solidClearances", A.solidClearances(R.solids, { state: 1 }), clearanceLine);
  list("solidClearances.home", A.solidClearances(R.solids, {}), clearanceLine);
  list("wobble", A.wobble(R.solids), (w) => `${w.name} ${w.at.join(",")} ${num(w.from)} ${num(w.to)}`);
  list("seats", A.seats(R.solids), (d) => `${d.name} ${d.host} ${d.piece} ${d.hostPiece}`);
  const cam = A.cameraBasis(P);
  R.items.forEach((item, index) => item.shapes.length && put(`approx.footprintOf.${index}`, item.shapes.map((shape) => box(A.footprintOf(shape, cam))).join(" ")));
  put("approx.markupBox.standin", box(A.markupBox(art.standin)));
  put("approx.markupBox.good", box(A.markupBox(art.good)));
  put("approx.markupBox.clipped", box(A.markupBox(art.clipped)));
  list("approx.coverage", A.coverage(R.items, P), (c) => `${c.name} ${fixed(c.share, 4)} ${box(c.drawn)} ${box(c.shapes)}`);
  const result = A.audit(R, P, { crowd: [["tap", "rail*"]] });
  put("failures", A.failures(result));
  for (const [index, line] of A.report(result).split("\n").entries()) put(`report.${index}`, line);
  const partial = { clearance: [], depth: { problems: [], missing: ["x"] }, wobble: [] };
  put("report.partial", A.report(partial).split("\n").join("¶"));
  put("failures.partial", A.failures(partial));
}

function arts() {
  const S = (paths) => K.solidSvg(paths);
  const standin = S(K.cylinder(-2.4, -16, 3, 4, 8, P)) + K.lineSvg(K.pathOf([K.iso([-2, -16, 12], P), K.iso([-2, -16, 16], P)]));
  const good = S(K.cylinder(-12, 14, 2, 4, 6, P)) + K.dotsSvg([K.iso([-12, 14, 10], P)], { size: 0.8 }) + `<path class="iso-halo" d="${K.haloOf({ x: -20, y: 6, w: 16, d: 16, r: 2 }, 4, 6, P)}"/>`;
  const clipped = `<defs><clipPath id="c"><path d="${K.pathOf([K.iso([-4, -4, 0], P), K.iso([4, -4, 0], P), K.iso([4, 4, 0], P)], true)}"/></clipPath></defs><g clip-path="url(#c)">${S(K.slabOf({ x: -6, y: -6, w: 12, d: 12, r: 2 }, 0, 6, P))}</g>${K.wireSvg(K.iso([0, 0, 0], P), K.iso([8, 0, 0], P))}`;
  return { standin, good, clipped };
}

shapes();
states();
overlaps();
checks(arts());
writeFileSync(join(OUT, "audit.txt.gz"), gzipSync(lines.join("\n") + "\n", { level: 9 }));
console.log(`wrote ${lines.length} audit results`);
