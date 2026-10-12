// Captures the golden RaptorParityTest compares the Kotlin Raptor 2 (Fig 6) against, from the
// original Anatomy's examples/raptor-engine/build.mjs.
//
//   git clone https://github.com/wheresryan22/anatomy.git /tmp/anatomy
//   git -C /tmp/anatomy checkout 240a9bf7c50cc6226148762a1017466fcbc08523
//   node anatomy/parity/raptor.mjs /tmp/anatomy
//
// It runs the build's geometry without writing its page: it copies build.mjs to a scratch file in
// the system temp directory, points its imports at the original's kit, and cuts it where the page
// starts, after the build settles its painter's keys and sorts its items. That takes a couple of
// minutes. It writes anatomy/src/jvmTest/resources/goldens/raptor.txt.gz (gzipped):
//
//   back           the static art behind the engine, element by element
//   item …         every recorded item in settled order: name, piece, ride, key, lift, move,
//                  then every element of its drawing
//   route …        every recorded route: name, piece, ride, move, radius, points
//   lip            the nozzle lip the rim glow strokes
//
// An element is one line: `g` for a group (its class, seamless/lit flags, tone, opacity), `path`
// (class, tone, shade, free, opacity, d), `dot` (cx, cy, r, fill-opacity) or `text`.
import { gzipSync } from "node:zlib";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const ORIGINAL = resolve(process.argv[2] ?? "/tmp/anatomy");
const OUT = join(HERE, "../src/jvmTest/resources/goldens");

const source = readFileSync(join(ORIGINAL, "examples/raptor-engine/build.mjs"), "utf8");
const cut = source.indexOf("const groups = items.map(");
if (cut < 0) throw new Error("build.mjs changed: no `const groups` to cut at");
const kitUrl = pathToFileURL(join(ORIGINAL, "kit")).href;
const scriptsUrl = pathToFileURL(join(ORIGINAL, "scripts")).href;
const patched =
  source.slice(0, cut).replaceAll('"../../kit/', `"${kitUrl}/`).replaceAll('"../../scripts/', `"${scriptsUrl}/`) +
  "\nexport { R, items, back, LIP, P, V };\n";
const scratch = join(tmpdir(), "anatomy-raptor-parity");
mkdirSync(scratch, { recursive: true });
const file = join(scratch, "build.mjs");
writeFileSync(file, patched);
const built = await import(pathToFileURL(file).href);

const fixed = (value, digits) => {
  const scale = 10 ** digits;
  const text = String(Math.round(value * scale) / scale);
  return text === "-0" ? "0" : text;
};
const num = (value) => fixed(value, 9);
const p3 = ([x, y, z]) => `${num(x)},${num(y)},${num(z)}`;

const attr = (attrs, name) => attrs.match(new RegExp(`(?:^|\\s)${name}="([^"]*)"`))?.[1];
const has = (attrs, name) => new RegExp(`(?:^|\\s)${name}(?:=|\\s|$)`).test(attrs);
function elements(svg) {
  const out = [];
  for (const [, tag, attrs, text] of svg.matchAll(/<(path|circle|text|g)\b([^>]*?)\/?>(?:([^<]*)<\/text>)?/g)) {
    if (tag === "g") {
      out.push(["g", attr(attrs, "class") ?? "-", has(attrs, "data-seamless") ? "seamless" : "-", has(attrs, "data-lit") ? "lit" : "-", attr(attrs, "data-tone") ?? "-", attr(attrs, "opacity") ?? "-"].join("\t"));
    } else if (tag === "path") {
      const free = has(attrs, "data-free") ? attr(attrs, "data-free") || "free" : "-";
      out.push(["path", attr(attrs, "class"), attr(attrs, "data-tone") ?? "-", attr(attrs, "data-shade") ?? "-", free, attr(attrs, "opacity") ?? "-", attr(attrs, "d")].join("\t"));
    } else if (tag === "circle") {
      out.push(["dot", attr(attrs, "cx"), attr(attrs, "cy"), attr(attrs, "r"), attr(attrs, "fill-opacity") ?? "-"].join("\t"));
    } else {
      out.push(["text", attr(attrs, "transform"), attr(attrs, "font-size"), attr(attrs, "text-anchor"), text].join("\t"));
    }
  }
  return out;
}

const lines = [];
lines.push("back");
for (const svg of built.back) lines.push(...elements(svg));
for (const item of built.items) {
  const lift = item.move[0] * built.V[0] + item.move[1] * built.V[1] + item.move[2] * built.V[2];
  lines.push(["item", item.name, item.piece, item.ride, fixed(item.key, 6), fixed(lift, 6), p3(item.move)].join("\t"));
  lines.push(...elements(item.svg));
}
for (const route of built.R.routes) {
  lines.push(["route", route.name, route.piece, route.ride, p3(route.move), num(route.r), route.points.map(p3).join(" ")].join("\t"));
}
lines.push(`lip\t${built.LIP}`);
writeFileSync(join(OUT, "raptor.txt.gz"), gzipSync(lines.join("\n") + "\n", { level: 9 }));
console.log(`wrote ${built.items.length} items, ${built.R.routes.length} routes, ${lines.length} lines`);
