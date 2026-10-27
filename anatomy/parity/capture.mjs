// Captures the goldens ParityTest compares the Kotlin kit against, from the original Anatomy.
//
//   git clone https://github.com/wheresryan22/anatomy.git /tmp/anatomy
//   git -C /tmp/anatomy checkout 240a9bf7c50cc6226148762a1017466fcbc08523
//   node anatomy/parity/capture.mjs /tmp/anatomy
//
// It writes two files under anatomy/src/jvmTest/resources/goldens/:
// - kit.txt: the kit's functions called with the cases below, one result per line.
// - dial-indicator.txt: every element of the original's built light page, in paint order.
// The cases here and in ParityTest must stay in the same order.
import { readFileSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const ORIGINAL = resolve(process.argv[2] ?? "/tmp/anatomy");
const OUT = join(HERE, "../src/jvmTest/resources/goldens");
const k = await import(pathToFileURL(join(ORIGINAL, "kit/iso-kit.mjs")).href);

const fixed = (value, digits) => {
  const scale = 10 ** digits;
  const text = String(Math.round(value * scale) / scale);
  return text === "-0" ? "0" : text;
};
const flat = ([x, y]) => `${fixed(x, 4)},${fixed(y, 4)}`;

const lines = [];
const put = (name, value) => lines.push(`${name}\t${value}`);
const solid = (name, paths) => {
  put(`${name}.fill`, paths.fill);
  put(`${name}.outline`, paths.outline);
  put(`${name}.crease`, paths.crease);
  put(`${name}.top`, paths.top);
  paths.shades.forEach((shade, index) => put(`${name}.shade${index}`, shade));
  put(`${name}.bevel`, paths.bevel ?? "");
};

const box = { x: -40, y: -30, w: 80, d: 60, r: 6 };
const cameras = [
  ["a45", { azimuth: 45 }],
  ["a57", { azimuth: 57 }],
  ["a30e44", { azimuth: 30, elevation: 44 }],
];
for (const [name, options] of cameras) {
  const P = k.fitProjection(k.boxCorners(box, -10, 40), 600, 360, { pad: 28, headroom: 12, ...options });
  put(`${name}.projection`, `${fixed(P.origin[0], 4)},${fixed(P.origin[1], 4)},${fixed(P.scale, 6)}`);
  put(`${name}.iso`, flat(k.iso([12, -7, 5], P)));
  put(`${name}.depth`, fixed(k.depthOf([12, -7, 5], P), 6));
  put(`${name}.screenRise`, fixed(k.screenRise(10, P), 6));
  put(`${name}.axis`, flat(k.axisVector(P, [1, 2, 3])));
  solid(`${name}.slab`, k.slabOf(box, 0, 12, P, 8, 1.2));
  solid(`${name}.cylinder`, k.cylinder(10, -5, 9, 2, 6, P, 24, 0.8));
  solid(`${name}.flatSlab`, k.slabOf({ x: 0, y: 0, w: 10, d: 4, r: 0 }, 0, 0, P, 2));
  const ell = [[0, 0], [30, 0], [30, 10], [10, 10], [10, 30], [0, 30]];
  solid(`${name}.concave`, k.extrude(ell, 0, 8, P, { convex: false, bevel: 0.6 }));
  put(`${name}.planOutline`, k.planOutline(k.insetPlan(box, 4), 12, P, 6));
  put(`${name}.ring`, k.ring(5, 5, 3, 12, P, 16));
  put(`${name}.lineOnTop`, k.lineOnTop([0, 0], [10, 4], 3, P));
  put(`${name}.segment`, k.segment([0, 0, 0], [3, 4, 5], P));
  put(`${name}.rise`, k.rise([2, 3], 1, 9, P));
  put(`${name}.knurl`, k.knurl(0, 0, 6, 0, 4, 36, P));
  put(`${name}.sideArc`, k.sideArc(0, 0, 6, 2, P, 12));
  put(`${name}.sideSeam`, k.sideSeam(box, 6, P, 6));
  put(`${name}.ringSeam`, k.ringSeam(ell, 4, P));
  put(`${name}.sideRingX`, k.sideRing([10, 2, 8], 3, "x", P, 12));
  put(`${name}.sideRingY`, k.sideRing([10, 2, 8], 3, "y", P, 12));
  for (const axis of ["x", "y"]) {
    const c = k.coil(-10, 20, [2, 6], 4.2, 5.5, axis, P);
    put(`${name}.coil${axis}.back`, c.back);
    put(`${name}.coil${axis}.front`, c.front);
  }
  const top = k.topTicks(-31, 29, 4, 5, 20, 1, [2, 3.6], P);
  put(`${name}.topTicks.minor`, top.minor);
  put(`${name}.topTicks.major`, top.major);
  const topY = k.topTicks(-12, 12, 3, 4, -20, 1, [1.5, 3], P, "y");
  put(`${name}.topTicksY.minor`, topY.minor);
  put(`${name}.topTicksY.major`, topY.major);
  const side = k.sideTicks(-20, 20, 5, 2, 30, 10, [2, 4], P);
  put(`${name}.sideTicks.minor`, side.minor);
  put(`${name}.sideTicks.major`, side.major);
  const sideY = k.sideTicks(-9, 9, 3, 3, 40, 8, [1, 2], P, "y");
  put(`${name}.sideTicksY.minor`, sideY.minor);
  put(`${name}.sideTicksY.major`, sideY.major);
  const dial = k.radialTicks(0, 0, 18, 50, 5, 10, [1.3, 2.8], P);
  put(`${name}.radial.minor`, dial.minor);
  put(`${name}.radial.major`, dial.major);
  const arc = k.radialTicks(0, 0, 18, 12, 3, 10, [1, 2], P, -Math.PI / 3, (Math.PI * 2) / 3);
  put(`${name}.radialArc.minor`, arc.minor);
  put(`${name}.radialArc.major`, arc.major);
  put(`${name}.dotGrid`, k.dotGrid(-6, -4, 4, 3, 2.5, 7, P).map(flat).join(" "));
  put(`${name}.halo`, k.haloOf(box, -2, 4, P));
  put(`${name}.translate`, k.translateAlong(P, [12, -4, 3]));
  put(`${name}.topMatrix`, k.topMatrix([4, 5], 6, P, "x"));
  put(`${name}.topMatrixY`, k.topMatrix([4, 5], 6, P, "y"));
  put(`${name}.sideLeft`, k.sideMatrix([4, 5], 6, P, "left"));
  put(`${name}.sideRight`, k.sideMatrix([4, 5], 6, P, "right"));
}
writeFileSync(join(OUT, "kit.txt"), lines.join("\n") + "\n");

// The built page's figure, element by element, in paint order.
const page = readFileSync(join(ORIGINAL, "examples/dial-indicator/dial-indicator-light.html"), "utf8");
const svg = page.slice(page.indexOf("<svg"), page.indexOf("</svg>"));
const attr = (attrs, name) => attrs.match(new RegExp(`(?:^|\\s)${name}="([^"]*)"`))?.[1];
const figure = [];
for (const [, tag, attrs, text] of svg.matchAll(/<(path|circle|text|g)\b([^>]*?)\/?>(?:([^<]*)<\/text>)?/g)) {
  if (tag === "g") {
    const part = attr(attrs, "data-part");
    if (part) figure.push(`group\t${part}\t${attr(attrs, "transform")}`);
  } else if (tag === "path") {
    const part = attr(attrs, "data-part");
    figure.push(
      ["path", part ? `${part}:${attr(attrs, "class")}` : attr(attrs, "class"), attr(attrs, "data-tone") ?? "-", attr(attrs, "data-shade") ?? "-", attr(attrs, "d")].join("\t"),
    );
  } else if (tag === "circle") {
    figure.push(["dot", attr(attrs, "cx"), attr(attrs, "cy"), attr(attrs, "r")].join("\t"));
  } else {
    figure.push(["text", attr(attrs, "transform"), attr(attrs, "font-size"), attr(attrs, "text-anchor"), text].join("\t"));
  }
}
writeFileSync(join(OUT, "dial-indicator.txt"), figure.join("\n") + "\n");
console.log(`wrote ${lines.length} kit results and ${figure.length} figure elements`);
