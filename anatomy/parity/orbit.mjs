// Captures the golden OrbitParityTest compares the Kotlin orbit audit against, from the original
// Anatomy's kit/turn-audit.mjs.
//
//   git clone https://github.com/wheresryan22/anatomy.git /tmp/anatomy
//   git -C /tmp/anatomy checkout 240a9bf7c50cc6226148762a1017466fcbc08523
//   node anatomy/parity/orbit.mjs /tmp/anatomy
//
// It writes anatomy/src/jvmTest/resources/goldens/orbit.txt.gz (gzipped): the orbit audit's report
// on the `spin` scene of turn.mjs, clean and with its three planted faults (a lug sunk into the
// block, a pawl blade that swings through it, a cable climbing at the viewer), each sampled with a
// sweep and a wiggle clip in a quick run (a full run takes about 40 s a scene in Node). The report's
// last line, the run time, is left out; the failure count and the verdict follow the report.
import { gzipSync } from "node:zlib";
import { writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const ORIGINAL = resolve(process.argv[2] ?? "/tmp/anatomy");
const OUT = join(HERE, "../src/jvmTest/resources/goldens");
process.env.TURN_LIBRARY = "1";
const { spin } = await import(pathToFileURL(join(HERE, "turn.mjs")).href);
const TA = await import(pathToFileURL(join(ORIGINAL, "kit/turn-audit.mjs")).href);

const lines = [];
for (const broken of [false, true]) {
  const name = broken ? "broken" : "clean";
  const { T, R, P, wiggle } = spin(broken);
  const result = TA.orbit(T, R, P, { quick: true, clips: [{ name: "wiggle", at: wiggle, from: 0, to: 1, frame: 1 / 60 }] });
  const report = TA.orbitReport(result).split("\n");
  for (const line of report.slice(0, -1)) lines.push(`${name}| ${line}`);
  lines.push(`${name}| failures ${TA.orbitFailures(result)}`);
}

const text = lines.join("\n") + "\n";
writeFileSync(join(OUT, "orbit.txt.gz"), gzipSync(text, { level: 9 }));
console.log(`wrote ${lines.length} orbit results, ${text.length} bytes`);
