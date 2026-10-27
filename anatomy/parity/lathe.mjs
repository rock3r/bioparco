// Captures the goldens LatheParityTest compares the Kotlin round parts against, from the original
// Anatomy's kit/lathe.mjs.
//
//   git clone https://github.com/wheresryan22/anatomy.git /tmp/anatomy
//   git -C /tmp/anatomy checkout 240a9bf7c50cc6226148762a1017466fcbc08523
//   node anatomy/parity/lathe.mjs /tmp/anatomy
//
// It writes anatomy/src/jvmTest/resources/goldens/lathe.txt.gz (gzipped): lathe.mjs's functions called with the
// cases below, one result per line. The cases here and in LatheParityTest must stay in the same
// order.
import { gzipSync } from "node:zlib";
import { writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const ORIGINAL = resolve(process.argv[2] ?? "/tmp/anatomy");
const OUT = join(HERE, "../src/jvmTest/resources/goldens");
const G = await import(pathToFileURL(join(ORIGINAL, "kit/lathe.mjs")).href);

const TAU = Math.PI * 2;
const fixed = (value, digits) => {
  const scale = 10 ** digits;
  const text = String(Math.round(value * scale) / scale);
  return text === "-0" ? "0" : text;
};
const num = (value) => fixed(value, 9);
const p2 = ([x, y]) => `${fixed(x, 4)},${fixed(y, 4)}`;
const p3 = ([x, y, z]) => `${num(x)},${num(y)},${num(z)}`;
const points3 = (list) => list.map(p3).join(" ");

const lines = [];
const put = (name, value) => lines.push(`${name}\t${value}`);
const solid = (name, paths) => {
  put(`${name}.fill`, paths.fill);
  put(`${name}.outline`, paths.outline);
  put(`${name}.crease`, paths.crease);
  put(`${name}.top`, paths.top);
  paths.shades.forEach((shade, index) => put(`${name}.shade${index}`, shade));
  put(`${name}.bevel`, paths.bevel ?? "");
  put(`${name}.seamless`, Boolean(paths.seamless));
};
const faded = (name, list) => list.forEach(({ d, alpha }, index) => put(`${name}.${index}`, `${fixed(alpha, 6)}\t${d}`));
const dots = (name, list) => put(name, list.map((p) => (p.length > 2 ? `${p2(p)}@${fixed(p[2], 6)}` : p2(p))).join(" "));

const cameras = [
  ["a45", { origin: [300, 200], scale: 2 }],
  ["a57", { origin: [310, 190], scale: 2.4, azimuth: 57 }],
  ["a30e44", { origin: [290, 210], scale: 1.8, azimuth: 30, elevation: 44 }],
];
const frames = [
  ["up", G.frameOf([0, 0, 0], [0, 0, 1], [1, 0, 0], [0, 1, 0])],
  ["x", G.frameAlong([10, -5, 8], [1, 0, 0])],
  ["tilt", G.frameAlong([-6, 4, 12], [0.4, -0.7, 0.6])],
  ["down", G.frameAlong([3, 2, 20], [0.2, 0.3, -1])],
];

const BELL = [[0, 12], [3, 11.6, 1], [8, 9.8, 1], [14, 7.2, 1], [20, 5.4, 1], [26, 4.8]];
const STEPPED = [[0, 10], [5, 10], [5, 7], [8, 7], [8, 8.5], [18, 8.5]];
const WAIST = [[0, 10], [5, 7.4, 1], [10, 6, 1], [15, 7.4, 1], [20, 10]];
const throat = (s) => Math.sqrt(36 + (0.5 * (s - 10)) ** 2);
const throatSlope = (s) => (0.25 * (s - 10)) / throat(s);
const THROAT = [0, 4, 8, 12, 16, 20].map((s) => [s, throat(s)]);
const cone = (s) => 9 - 0.3 * s;
const CONE = [0, 5, 10, 15, 20].map((s) => [s, cone(s)]);
const bell = (s) => 4 + 8 * (1 - s / 30) ** 2;
const bellSlope = (s) => (-16 / 30) * (1 - s / 30);

function vectors() {
  const a = [1.5, -2, 0.75];
  const b = [-0.4, 3.2, 2.5];
  put("dot3", num(G.dot3(a, b)));
  put("add3", p3(G.add3(a, b)));
  put("sub3", p3(G.sub3(a, b)));
  put("mul3", p3(G.mul3(a, -1.7)));
  put("len3", num(G.len3(b)));
  put("unit3", p3(G.unit3(b)));
  put("unit3.zero", p3(G.unit3([0, 0, 0])));
  put("cross3", p3(G.cross3(a, b)));
  put("lerp3", p3(G.lerp3(a, b, 0.3)));
  for (const score of [-0.5, 0, 0.1, 0.2, 0.5, 0.62, 0.9]) put(`bandOf.${score}`, G.bandOf(score));
  for (const [r, least, most, scale] of [[3], [10], [40], [200], [6, 32, 160, 2], [30, 32, 160, 2.4], [2, 24]]) {
    put(`stepsFor.${r}.${least}.${most}.${scale}`, G.stepsFor(r, least, most, scale));
  }
  put("hullOf", G.hullOf([[0, 0], [4, 1], [2, 2], [5, 5], [1, 4], [3, 3], [-1, 3], [2, -1]]).map(p2).join(" "));
  put("runsOf.mixed", JSON.stringify(G.runsOf([true, true, false, true, false, false, true])));
  put("runsOf.all", JSON.stringify(G.runsOf([true, true, true])));
  put("runsOf.none", JSON.stringify(G.runsOf([false, false])));
  put("softOf", `${G.softOf(BELL)} ${G.softOf(STEPPED)}`);
  put("fadeOf", [-0.2, 0.04, 0.1, 0.25, 0.42, 0.8].map((v) => fixed(G.fadeOf(v, [0.04, 0.42]), 9)).join(" "));
  put("saddleStart", [0, 0.7, 1.6, 3].map((t) => num(G.saddleStart(10, 4, t))).join(" "));
}

function routes() {
  const legs = [[0, 0, 0], [20, 0, 0], [20, 15, 5], [5, 25, 5], [5, 25, 30]];
  const P = { origin: [300, 200], scale: 2 };
  put("fillet", points3(G.fillet(legs, 4)));
  put("fillet.tube", points3(G.fillet(legs, 3, 5, { P, tube: 2 })));
  put("fillet.short", points3(G.fillet(legs.slice(0, 2), 4)));
  put("pathLength", num(G.pathLength(legs)));
  put("resample", points3(G.resample(legs, 6.5)));
  put("cut", points3(G.cut(legs, 12, 41.5)));
  put("pointAlong", `${p3(G.pointAlong(legs, 27.3))} ${p3(G.pointAlong(legs, 500))}`);
  put("tangentAlong", `${p3(G.tangentAlong(legs, 0))} ${p3(G.tangentAlong(legs, 20))} ${p3(G.tangentAlong(legs, 63))}`);
  const F = G.frameAlong([1, 2, 3], [0.3, 0.2, 0.9]);
  put("spiral", points3(G.spiral(F, 2, 5, 9, 0.2, 4)));
  put("spiral.steps", points3(G.spiral(F, 2, 5, 9, 0.2, 4, 7)));
  put("arcPoints", points3(G.arcPoints(F, 1, 6, -0.5, 2.5)));
  put("arcPoints.steps", points3(G.arcPoints(F, 1, 6, -0.5, 2.5, 5)));
}

function profiles() {
  const meridian = [...BELL, [26, 0], [30, 0, 1], [34, 3, 1], [38, 3]];
  G.curveRuns(meridian).forEach((curve, index) => {
    const at = (t) => meridian[index][0] + (meridian[index + 1][0] - meridian[index][0]) * t;
    put(`curveRuns.${index}`, curve ? [0.1, 0.5, 0.9].map(at).map((s) => `${num(curve.radius(s))}/${num(curve.slope(s))}`).join(" ") : "-");
  });
  for (const [name, curve] of [
    ["bell", G.profileCurve(BELL)],
    ["stepped", G.profileCurve([[0, 10], [5, 10], [8, 7], [18, 8.5, 1], [24, 6, 1], [30, 6]])],
    ["analytic", G.profileCurve(THROAT, { slope: throatSlope, radius: throat })],
  ]) {
    const at = [-2, 0, 1.3, 4.9, 7.7, 12.2, 19.5, 26, 40];
    put(`profileCurve.${name}`, `${num(curve.s0)} ${num(curve.s1)} ${curve.analytic} ${at.map((s) => `${num(curve.radius(s))}/${num(curve.slope(s))}`).join(" ")}`);
  }
  try {
    G.profileCurve(STEPPED);
  } catch (error) {
    put("profileCurve.throws", error.message);
  }
}

function framesOf(P, name) {
  put(`${name}.view`, p3(G.viewOf(P)));
  put(`${name}.light`, p3(G.lightOf(P)));
  put(`${name}.scale`, num(G.scaleOf(P)));
  put(`${name}.toneOf`, [[1, 0, 0], [0, 1, 0], [0, 0, 1], [-1, -1, 0.2], [0.3, -0.8, 0.5]].map((n) => G.toneOf(n, P)).join(" "));
  for (const [frameName, F] of frames) {
    const at = `${name}.${frameName}`;
    put(`${at}.frame`, [F.o, F.a, F.u, F.v].map(p3).join(" "));
    put(`${at}.pointOf`, p3(G.pointOf(F, 3, 7, 1.1)));
    put(`${at}.radialOf`, p3(G.radialOf(F, -2.3)));
    put(`${at}.tangentOf`, p3(G.tangentOf(F, 0.4)));
    put(`${at}.angleToward`, num(G.angleToward(F, G.viewOf(P))));
    put(`${at}.facingOf`, [0, 1, 2.5, 4].map((t) => num(G.facingOf(F, t, P, 0.3))).join(" "));
  }
}

function solidsOf(P, name) {
  for (const [frameName, F] of frames) {
    const at = `${name}.${frameName}`;
    solid(`${at}.disc`, G.disc(0, 6, 10, F, P, { bevel: 1 }));
    solid(`${at}.ringBand`, G.ringBand(0, 4, 6, 14, F, P, { rims: "ends" }));
    solid(`${at}.bell`, G.solidOf(BELL, F, P));
    solid(`${at}.stepped`, G.solidOf(STEPPED, F, P, { bevel: 0.8, rims: "first" }));
    solid(`${at}.waist`, G.solidOf(WAIST, F, P, { rims: "last" }));
  }
}

function lathesOf(P, name) {
  const [, F] = frames[2];
  const [, D] = frames[3];
  const at = (case_) => `${name}.${case_}`;
  solid(at("hole"), G.lathe([[0, 6], [0, 10], [12, 10], [12, 6]], F, P, { hole: [12, 6], rims: "none" }));
  solid(at("facets"), G.lathe([[0, 9], [6, 8], [10, 5], [10, 0]], D, P, { fill: "facets", steps: 40 }));
  solid(at("hull"), G.solidOf(WAIST, F, P, { fill: "hull", steps: 48 }));
  solid(at("surface"), G.solidOf(BELL, D, P, { fill: "surface" }));
  solid(at("analytic"), G.lathe(THROAT, F, P, { smooth: { slope: throatSlope, radius: throat, rows: 4 } }));
  solid(at("slopeOnly"), G.lathe(CONE, D, P, { smooth: { slope: () => -0.3 }, rims: "first" }));
  solid(at("forcedOff"), G.solidOf(BELL, F, P, { smooth: false, steps: 36 }));
  solid(at("forcedOn"), G.solidOf(STEPPED, D, P, { smooth: true }));
  solid(at("rows"), G.solidOf(BELL, D, P, { smooth: { rows: 2, error: 0.6, depth: 5, fineness: 0.3, steps: 30 } }));
  solid(at("bandOfProfile"), G.bandOfProfile([[2, 9], [6, 8.2, 1], [10, 7.4, 1], [14, 7]], F, P, { rims: "none" }));
  const outline = G.outlineOf(WAIST, F, P, { rims: "ends" });
  put(`${at("outlineOf")}.fill`, outline.fill);
  put(`${at("outlineOf")}.outline`, outline.outline);
  put(at("silhouetteOf"), G.silhouetteOf(WAIST, F, P));
  put(at("silhouetteOf.slope"), G.silhouetteOf(THROAT, D, P, { samples: 30, slope: throatSlope, radius: throat }));
  put(at("silhouettePoints"), G.silhouettePoints(CONE, F, P, { samples: 12 }).map(points3).join(" | "));
}

function shapesOf(P, name) {
  const [, F] = frames[2];
  const [, X] = frames[1];
  const at = (case_) => `${name}.${case_}`;
  solid(at("sphere"), G.sphereOf([4, -3, 10], 7, P));
  solid(at("sphere.steps"), G.sphereOf([-2, 6, 3], 3.5, P, { steps: 24 }));
  solid(at("saddle"), G.saddleOf(X, 4, 10, 14, P));
  solid(at("saddle.start"), G.saddleOf(F, 3, (t) => 9 + 0.5 * Math.cos(t), 13, P, { steps: 30 }));
  const plate = G.stadium(8, 5, 18);
  put(at("stadium"), plate.map(p2).join(" "));
  put(at("stadium.steps"), G.stadium(3, 2, 6, 12).map(p2).join(" "));
  solid(at("prism.stadium"), G.prismOf(F, plate, 0, 4, P));
  solid(at("prism.square"), G.prismOf(X, [[-5, -3], [5, -3], [5, 3], [-5, 3]], -2, 3, P));
  const rod = G.rodOut(F, 0, 10, 2.5, P, { stroke: 6 });
  solid(at("rod"), rod.paths);
  put(at("rod.clip"), rod.clip);
  put(at("rod.numbers"), [rod.from, rod.to, rod.stroke, rod.reach].map(num).join(" "));
  solid(at("rod.still"), G.rodOut(X, 2, 9, 1.5, P, { bevel: 0.4 }).paths);
}

function bandsOf(P, name) {
  const [, F] = frames[2];
  const [, X] = frames[1];
  const at = (case_) => `${name}.${case_}`;
  const analytic = G.bandsOf([[0, bell(0)], [30, bell(30)]], [8, 16, 16, 24, 40, -3], F, P, { radius: bell, slope: bellSlope, rows: 4 });
  const fitted = G.bandsOf([[0, 12], [6, 11, 1], [12, 9, 1], [20, 8, 1], [26, 8]], [6, 14], X, P, { rims: "first", samples: 40 });
  for (const [kind, B] of [["analytic", analytic], ["fitted", fitted]]) {
    put(at(`bands.${kind}`), `${B.steps} ${num(B.s0)} ${num(B.s1)} ${num(B.radius(5))} ${num(B.slope(5))} ${B.bands.length}`);
    B.bands.forEach((band, index) => {
      const key = at(`bands.${kind}.${index}`);
      put(`${key}.span`, `${num(band.s0)} ${num(band.s1)} ${band.profile.map(([s, r, soft]) => `${num(s)}/${num(r)}${soft ? "/1" : ""}`).join(" ")}`);
      solid(key, band.paths);
      put(`${key}.ownOutline`, band.outline);
      put(`${key}.seam`, band.seam);
    });
    const sides = B.sidesOf();
    put(at(`bands.${kind}.sides`), sides.d);
    put(at(`bands.${kind}.sides.points`), sides.points.map(points3).join(" | "));
    put(at(`bands.${kind}.sides.ends`), B.sidesOf(2, 20, { ends: true, count: 24 }).d);
    put(at(`bands.${kind}.sides.first`), B.sidesOf(4, 50, { ends: "first" }).d);
    put(at(`bands.${kind}.sides.last`), B.sidesOf(undefined, 22, { ends: "last", count: 30 }).d);
  }
}

function linesOf(P, name) {
  for (const [frameName, F] of frames) {
    const at = (case_) => `${name}.${frameName}.${case_}`;
    put(at("arc"), G.arcOf(F, 4, 9, P));
    put(at("arc.slope"), G.arcOf(F, 4, 9, P, { slope: 0.4, steps: 20 }));
    put(at("arc.inward"), G.arcOf(F, 4, 9, P, { inward: true, least: -1 }));
    put(at("arc.inwardSlope"), G.arcOf(F, 4, 9, P, { inward: true, slope: -0.5 }));
    put(at("arc.whole"), G.arcOf(F, 4, 9, P, { least: -1 }));
    put(at("arc.limbs"), G.arcOf(F, 4, 9, P, { from: 0.5, to: 4 }));
    put(at("arc.limbsWide"), G.arcOf(F, 4, 9, P, { from: -2, to: 5, trim: 0.6, least: 0.1 }));
    put(at("arc.noTrim"), G.arcOf(F, 4, 9, P, { from: 1, to: 6, trim: 0 }));
    put(at("circle"), G.circleOf(F, 3, 8, P));
    put(at("circle.steps"), G.circleOf(F, 3, 8, P, 20));
    put(at("spokes"), G.spokesOf(F, 2, 3, 9, 8, P, { phase: 0.1, sweep: 0.3 }));
    put(at("spokes.range"), G.spokesOf(F, 2, 3, 9, 5, P, { from: 1, to: 3 }));
    put(at("ribs"), G.ribsOf(F, 0, 12, 8, 24, P));
    put(at("ribs.cone"), G.ribsOf(F, 0, 12, 8, 30, P, { twist: 0.4, r1: 5, least: 0.05, seams: true, phase: 0.05 }));
    faded(at("ribs.fade"), G.ribsOf(F, 0, 12, 8, 24, P, { fade: [0.1, 0.55], seams: "first" }));
    faded(at("ribs.fadeLast"), G.ribsOf(F, 2, 10, 6, 18, P, { fade: [0.1, 0.55], seams: "last", r1: 7, twist: -0.3 }));
    dots(at("dots"), G.dotsOf(F, 6, 9, 12, P));
    dots(at("dots.slope"), G.dotsOf(F, 6, 9, 16, P, { slope: 0.5, least: 0.2, phase: 0.2 }));
    dots(at("dots.all"), G.dotsOf(F, 6, 9, 8, P, { all: true }));
    dots(at("dots.fade"), G.dotsOf(F, 6, 9, 20, P, { fade: [0.04, 0.42] }));
    dots(at("dots.fadeAll"), G.dotsOf(F, 6, 9, 10, P, { fade: [0.04, 0.42], all: true }));
  }
}

vectors();
routes();
profiles();
for (const [name, options] of cameras) {
  framesOf(options, name);
  solidsOf(options, name);
  lathesOf(options, name);
  shapesOf(options, name);
  bandsOf(options, name);
  linesOf(options, name);
}
writeFileSync(join(OUT, "lathe.txt.gz"), gzipSync(lines.join("\n") + "\n", { level: 9 }));
console.log(`wrote ${lines.length} lathe results`);
