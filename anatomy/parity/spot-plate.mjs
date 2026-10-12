// Original Anatomy (Ryan, MIT; anatomy/LICENSE), commit 240a9bf.
// Install: npm install --prefix .amp/in esbuild
// Run from the repo root: node anatomy/parity/spot-plate.mjs /tmp/anatomy-src
// esbuild resolves the original TS modules' extensionless imports. No translated JS is a golden.
import { resolve } from "node:path";
import { pathToFileURL } from "node:url";
import { writeFileSync } from "node:fs";

const { build } = await import(pathToFileURL(resolve(".amp/in/node_modules/esbuild/lib/main.js")));
const source = resolve(process.argv[2] ?? "/tmp/anatomy-src");
const bundle = resolve(".amp/in/spot-golden.mjs");
await build({
  stdin: {
    contents: `export * as rig from './rig'; export * as geometry from './geometry'; export * as fan from './fan'; export * as drops from './drops';`,
    resolveDir: resolve(source, "examples/spot-plate"), loader: "ts",
  },
  bundle: true, platform: "node", format: "esm", outfile: bundle,
});
const { rig: r, geometry: g, fan: f, drops: d } = await import(pathToFileURL(bundle));
const lines = [];
function put(key, value) {
  if (Array.isArray(value)) {
    lines.push(`${key}.length\t${value.length}`);
    value.forEach((v, i) => put(`${key}.${i}`, v));
  } else if (value && typeof value === "object") {
    // Normalize optional kit fields, without changing any coordinates or path strings.
    if ("fill" in value && !("depth" in value) && !("line" in value)) {
      value = { fill: value.fill, top: value.top, outline: value.outline, crease: value.crease, bevel: value.bevel ?? "", shades: value.shades ?? [] };
    }
    Object.keys(value).sort().forEach(k => put(`${key}.${k}`, value[k]));
  } else lines.push(`${key}\t${value}`);
}
put("base", g.BASE_ART); put("tray", g.TRAY_ART); put("deck", g.DECK_ART); put("bottle", g.BOTTLE_ART);
for (const merge of [0, 3.75, 4, 4.25, 10, 16.25, 20, 24, 32.25, 44, 48]) put(`fan.${merge}`, f.fanOf(merge));
const poses = [...new Set([
  ...Array.from({length: 25}, (_, i) => i * 2),
  ...r.GAPS.flatMap(gap => [-0.375, -0.25, -0.125, -0.124, 0, 0.124, 0.125, 0.25, 0.375, 0.5].map(delta => gap + delta)),
  ...r.GAPS.flatMap(gap => [gap / 2 - 0.125, gap / 2, gap / 2 + 0.125]),
])].sort((a,b) => a-b);
for (const merge of poses) { put(`key.${merge}`, d.keyOf(merge)); put(`rows.${merge}`, d.rowsOf(merge)); }
for (const merge of [0, 4, 4.5, 10, 16, 20, 24, 32, 44, 44.5, 48]) {
  put(`model.${merge}`, { readout: r.readoutOf(merge), spoken: r.spokenOf(merge), joinedList: r.joinedListOf(merge), bladesOut: r.bladesOutOf(merge) });
  for (const gap of r.GAPS) put(`field.${merge}.${gap}`, {
    neck: r.neckOf(gap, merge), seenGap: r.seenGapOf(gap, merge),
    samples: [[0,0], [17.25,-6.75], [r.RA+gap/2,0], [r.RA+gap/2,0.75], [r.spanOf(gap),0], [r.spanOf(gap)+10,15]].map(([u,v]) => r.fieldOf(gap, merge)(u,v)),
  });
}
writeFileSync("anatomy/src/jvmTest/resources/goldens/spot-plate.txt", lines.join("\n") + "\n");
console.log(`${lines.length} lines, ${poses.length} glass poses, 11 fan poses`);
