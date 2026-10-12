// Captures Fig 8 from the original Anatomy, MIT, pinned at 240a9bf7c50cc6226148762a1017466fcbc08523.
// Run: node anatomy/parity/arcade.mjs /tmp/anatomy-src
// Runs the actual build up to its page (including settle); writes no original files.
// Every item in settled order has name/piece/key/move, followed by every path, circle, rectangle
// and text in its drawing, including hidden screen modes and the two clipping paths. Face matrices
// and every route follow. SVG wrappers/CSS are not paths; curves and bitmap commands remain literal.
import { gzipSync } from "node:zlib";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
const HERE = dirname(fileURLToPath(import.meta.url));
const original = resolve(process.argv[2] ?? "/tmp/anatomy-src");
const scratch = resolve(HERE, "../../.amp/in/arcade-parity");
mkdirSync(scratch, { recursive: true });
let source = readFileSync(join(original, "examples/arcade-cabinet/build.mjs"), "utf8");
source = source.slice(0, source.indexOf("const CSS ="))
  .replaceAll('"../../kit/', `"${pathToFileURL(join(original, "kit")).href}/`)
  .replaceAll('"../../scripts/', `"${pathToFileURL(join(original, "scripts")).href}/`)
  .replaceAll('"./parts.mjs"', `"${pathToFileURL(join(original, "examples/arcade-cabinet/parts.mjs")).href}"`)
  .replaceAll('"./screen.mjs"', `"${pathToFileURL(join(original, "examples/arcade-cabinet/screen.mjs")).href}"`)
  .replace("A.settle(R, P);", "const unsorted = [...R.items];\nA.settle(R, P);");
writeFileSync(join(scratch, "build.mjs"), source + "\nexport { R, P, back, unsorted, planeMatrix, bezelMatrix, METER_FACE };\n");
const b = await import(pathToFileURL(join(scratch, "build.mjs")).href);
const fixed = (v, d) => String(Math.round(v * 10 ** d) / 10 ** d);
const p3 = (p) => p.map(v => fixed(v, 9)).join(",");
const attr = (s, n) => s.match(new RegExp(`(?:^|\\s)${n}="([^"]*)"`))?.[1];
function elements(svg) {
  return [...svg.matchAll(/<(path|circle|ellipse|rect|text)\b([^>]*?)\/?>(?:([^<]*)<\/text>)?/g)].map(([, tag, s, text]) => {
    if (tag === "path") return `path\t${attr(s, "d")}`;
    if (tag === "circle") return ["dot", attr(s, "cx") ?? "0", attr(s, "cy") ?? "0", attr(s, "r"), attr(s, "fill-opacity") ?? "-"].join("\t");
    if (tag === "ellipse") return ["ellipse", attr(s,"cx"), attr(s,"cy"), attr(s,"rx"), attr(s,"ry")].join("\t");
    if (tag === "rect") return ["rect", ...["x","y","width","height","rx"].map(n => attr(s,n))].join("\t");
    return ["text", attr(s,"x"), attr(s,"y"), attr(s,"font-size"), attr(s,"textLength"), text].join("\t");
  });
}
const lines = ["back", ...b.back.flatMap(elements)];
for (const it of b.R.items) {
  lines.push(["item", it.name, it.piece, fixed(it.key, 6), p3(it.move)].join("\t"));
  lines.push(...elements(it.svg));
}
for (const r of b.R.routes) lines.push(["route", r.name, r.piece, p3(r.move), fixed(r.r,9), r.points.map(p3).join(" ")].join("\t"));
lines.push("matrix\t" + b.planeMatrix([0,125.02,338],[1,0,0],[0,0,-1]));
lines.push("matrix\t" + b.planeMatrix(b.METER_FACE.o,b.METER_FACE.ex,b.METER_FACE.ey));
lines.push("matrix\t" + b.planeMatrix([-18,7.42,262],[1,0,0],[0,0,-1]));
lines.push("matrix\t" + b.bezelMatrix(-5));
lines.push("matrix\t" + b.bezelMatrix(-1));
const out = join(HERE, "../src/jvmTest/resources/goldens/arcade.txt.gz");
writeFileSync(out, gzipSync(lines.join("\n") + "\n", { level: 9 }));
writeFileSync(join(HERE,"../src/jvmMain/resources/arcade-order.txt"), b.R.items.map(it => `${b.unsorted.indexOf(it)}\t${it.key.toFixed(2)}`).join("\n") + "\n");
console.log(`wrote ${b.R.items.length} items, ${b.R.routes.length} routes, ${lines.length} lines`);
