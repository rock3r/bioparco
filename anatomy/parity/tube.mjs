// Captures the golden TubeParityTest compares the Kotlin pipes against, from the original Anatomy's
// kit/tube.mjs.
//
//   git clone https://github.com/wheresryan22/anatomy.git /tmp/anatomy
//   git -C /tmp/anatomy checkout 240a9bf7c50cc6226148762a1017466fcbc08523
//   node anatomy/parity/tube.mjs /tmp/anatomy
//
// It writes anatomy/src/jvmTest/resources/goldens/tube.txt.gz (gzipped): tube.mjs's functions called
// with the cases below, one result per line. The cases here and in TubeParityTest must stay in the
// same order.
import { gzipSync } from "node:zlib";
import { writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const ORIGINAL = resolve(process.argv[2] ?? "/tmp/anatomy");
const OUT = join(HERE, "../src/jvmTest/resources/goldens");
const G = await import(pathToFileURL(join(ORIGINAL, "kit/lathe.mjs")).href);
const T = await import(pathToFileURL(join(ORIGINAL, "kit/tube.mjs")).href);

const fixed = (value, digits) => {
  const scale = 10 ** digits;
  const text = String(Math.round(value * scale) / scale);
  return text === "-0" ? "0" : text;
};
const num = (value) => fixed(value, 9);
const p3 = ([x, y, z]) => `${num(x)},${num(y)},${num(z)}`;
const points3 = (list) => list.map(p3).join(" ");

const lines = [];
const put = (name, value) => lines.push(`${name}\t${value}`);
const pieces = (name, list) => {
  put(`${name}.count`, list.length);
  list.forEach((piece, index) => {
    const at = `${name}.${index}`;
    put(`${at}.span`, `${num(piece.from)} ${num(piece.to)} ${piece.ends.join(" ")} ${piece.wide} ${piece.ringTone}`);
    put(`${at}.mid`, p3(piece.mid));
    put(`${at}.points`, points3(piece.points));
    put(`${at}.radii`, piece.radii.map(num).join(" "));
    put(`${at}.body`, piece.body);
    put(`${at}.shine`, piece.shine);
    put(`${at}.shade`, piece.shade);
    put(`${at}.edges`, piece.edges);
    piece.rings.forEach(({ d, alpha }, ring) => put(`${at}.ring${ring}`, `${fixed(alpha, 6)}\t${d}`));
    put(`${at}.ringsSvg`, T.ringsSvg(piece.rings, piece.ringTone));
  });
};

const cameras = [
  ["a45", { origin: [300, 200], scale: 2 }],
  ["a57", { origin: [310, 190], scale: 2.4, azimuth: 57 }],
  ["a30e44", { origin: [290, 210], scale: 1.8, azimuth: 30, elevation: 44 }],
];

const LEGS = [[0, 0, 0], [20, 0, 0], [20, 15, 5], [5, 25, 5], [5, 25, 30]];
const UPRIGHT = [[-10, 0, 0], [-10, 0, 18], [6, 0, 18], [6, 0, 4]];
const DUCT = G.fillet([[-30, -8, 4], [-4, -8, 4], [-4, 14, 4], [-4, 14, 26], [18, 14, 26]], 7);
const VOLUTE_F = G.frameOf([0, 0, 0], [0, 0, 1], [1, 0, 0], [0, 1, 0]);
const VOLUTE = G.spiral(VOLUTE_F, 6, 14, 22, 0.3, 5.5, 40);
const RING = G.arcPoints(G.frameAlong([2, 3, 1], [0.2, 0.1, 1]), 0, 16, 0, Math.PI * 2, 36);
const taper = (t) => 1.5 + 2 * t;
const volute = (t) => 2 + 3 * t;
const wobble = (t) => 2 + 0.6 * Math.sin(t * 9);

function routes(P, name) {
  const at = (case_) => `${name}.${case_}`;
  pieces(at("straight"), T.tubePieces([[0, 0, 0], [40, 0, 0]], 2, P));
  pieces(at("straight.y"), T.tubePieces([[0, -10, 3], [0, 30, 3]], 1.2, P, { maxLength: 12 }));
  pieces(at("straight.up"), T.tubePieces([[4, 4, 0], [4, 4, 36]], 2.5, P, { maxLength: 10 }));
  pieces(at("legs"), T.tubePieces(G.fillet(LEGS, 4), 1.5, P, { maxLength: 12 }));
  pieces(at("upright"), T.tubePieces(G.fillet(UPRIGHT, 5), 3, P, { breaks: [10, 25] }));
  pieces(at("duct"), T.tubePieces(DUCT, 3.4, P, { maxLength: 18, spacing: 1.6 }));
  pieces(at("taper"), T.tubePieces(G.fillet(LEGS, 6), taper, P, { maxLength: 15 }));
  pieces(at("wobble"), T.tubePieces(DUCT, wobble, P, { maxLength: 9, stripes: false }));
  pieces(at("thin"), T.tubePieces(G.fillet(LEGS, 3), 0.3, P));
  pieces(at("thin.striped"), T.tubePieces(G.fillet(LEGS, 3), 0.3, P, { stripes: true, maxLength: 20 }));
}

function gapsAndCaps(P, name) {
  const at = (case_) => `${name}.${case_}`;
  const route = G.fillet(LEGS, 4);
  const total = G.pathLength(G.resample(route, 2.2));
  pieces(at("gaps"), T.tubePieces(route, 1.5, P, { gaps: [[10, 12.5], [30, 31]], maxLength: 14 }));
  pieces(at("gaps.ends"), T.tubePieces(route, 1.5, P, { gaps: [[-3, 2], [total - 4, total + 5]], breaks: [20] }));
  pieces(at("gaps.overlap"), T.tubePieces(route, 1.5, P, { gaps: [[18, 14], [15, 22], [40, 40], [50, 52]] }));
  pieces(at("caps.none"), T.tubePieces(route, 1.5, P, { caps: false }));
  pieces(at("caps.end"), T.tubePieces(route, 1.5, P, { caps: [false, true], gaps: [[25, 27]] }));
  pieces(at("caps.start"), T.tubePieces(UPRIGHT, 2, P, { caps: [true, false] }));
  const V = G.viewOf(P);
  const toward = [[0, 0, 0], [0, -20, 0], G.add3([0, -20, 0], G.mul3(V, 24))];
  pieces(at("face.end"), T.tubePieces(G.fillet(toward, 6), 2.4, P, { maxLength: 12 }));
  const away = [G.add3([0, 0, 0], G.mul3(V, 20)), [0, 0, 0], [24, 0, 0]];
  pieces(at("face.start"), T.tubePieces(G.fillet(away, 6), 2.4, P, { maxLength: 12, gaps: [[10, 13]] }));
  const head = [[0, 0, 0], G.mul3(V, 30)];
  pieces(at("face.head"), T.tubePieces(head, 2, P));
  const clamps = [[[8, 0, 0], [1, 0, 0], 3], [[20, 8, 2], [0, 1, 0.3], 4], [[12, 20, 5], [-1, 0.6, 0], 3]];
  const crossings = clamps.flatMap(([centre, axis, r]) => T.crossingsOf(route, centre, axis, r));
  put(at("crossingsOf"), crossings.map(num).join(" "));
  put(at("crossingsOf.miss"), T.crossingsOf(route, [8, 0, 0], [1, 0, 0], 0.5).map(num).join(" ") + "|" + T.crossingsOf(route, [500, 0, 0], [1, 0, 0], 3).length);
  pieces(at("clamped"), T.tubePieces(route, 0.62, P, { gaps: crossings.map((a) => [a - 0.9, a + 0.9]), maxLength: 10 }));
}

function closedRoutes(P, name) {
  const at = (case_) => `${name}.${case_}`;
  pieces(at("closed"), T.tubePieces(RING, 1.4, P, { closed: true, maxLength: 20 }));
  pieces(at("closed.gaps"), T.tubePieces(RING, 1.4, P, { closed: true, maxLength: 20, gaps: [[30, 33]] }));
  pieces(at("closed.one"), T.tubePieces(RING, 1.4, P, { closed: true, maxLength: 400, rings: { pitch: 4 } }));
}

function ringsOf(P, name) {
  const at = (case_) => `${name}.${case_}`;
  const route = G.fillet(LEGS, 5);
  pieces(at("rings"), T.tubePieces(route, 1.8, P, { maxLength: 12, rings: {} }));
  pieces(at("rings.braid"), T.tubePieces(route, 1.8, P, { maxLength: 12, breaks: [33], rings: { pitch: 1.6, twist: 1.2, cross: true, tone: "mid" } }));
  pieces(at("rings.helix"), T.tubePieces(route, 1.8, P, { maxLength: 16, gaps: [[20, 23]], rings: { pitch: 2.2, twist: -0.8, phase: 0.7, steps: 10 } }));
  pieces(at("rings.hard"), T.tubePieces(route, 1.8, P, { maxLength: 16, rings: { pitch: 2.5, fade: null, tone: "faint" } }));
  pieces(at("rings.corrugated"), T.tubePieces(G.fillet(UPRIGHT, 6), 3.2, P, { maxLength: 10, rings: { pitch: 1.2, fade: [0.05, 0.4], phase: 0.3, steps: 12, tone: "hi" } }));
  pieces(at("rings.taper"), T.tubePieces(DUCT, taper, P, { maxLength: 14, rings: { pitch: 3, twist: 0.6 } }));
  pieces(at("volute"), T.tubePieces(VOLUTE, volute, P, { maxLength: 10 }));
  pieces(at("volute.rings"), T.tubePieces(VOLUTE, volute, P, { maxLength: 14, rings: { pitch: 2.5, phase: 1 } }));
}

const spans = (list) => list.map(([a, b]) => `${num(a)},${num(b)}`).join(" ");

function runs() {
  put("runsOf.none", spans(T.runsOf(50)));
  put("runsOf.gaps", spans(T.runsOf(50, [[10, 12], [30, 34.5]])));
  put("runsOf.ends", spans(T.runsOf(50, [[-2, 3], [48, 60]])));
  put("runsOf.overlap", spans(T.runsOf(50, [[20, 14], [15, 25], [24, 26], [40, 40], [44, 44.0000001]])));
  put("runsOf.all", spans(T.runsOf(50, [[0, 50]])));
}

runs();
for (const [name, options] of cameras) {
  routes(options, name);
  gapsAndCaps(options, name);
  closedRoutes(options, name);
  ringsOf(options, name);
}
const text = lines.join("\n") + "\n";
writeFileSync(join(OUT, "tube.txt.gz"), gzipSync(text, { level: 9 }));
console.log(`wrote ${lines.length} tube results, ${text.length} bytes`);
