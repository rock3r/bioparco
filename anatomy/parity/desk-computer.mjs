// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
// node anatomy/parity/desk-computer.mjs /tmp/anatomy-src
// Reads the COMMITTED pages, not a regenerated build. Captures every element, its attributes,
// nesting and paint order (including clip definitions and the hidden rocker).
// H and L for a horizontal segment are the same path command; expand H without rounding.
import { readFileSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const original = resolve(process.argv[2] ?? "/tmp/anatomy-src");
const decode = value => value.replaceAll("&gt;", ">").replaceAll("&amp;", "&");
function expandHorizontal(d) {
  let y;
  return d.replace(/([MLQH])([^MLQHZ]*)/g, (_, command, args) => {
    const values = args.trim().split(/[ ,]+/).map(Number);
    if (command === "H") return `L${values[0]} ${y}`;
    y = values.at(-1);
    return command + args;
  });
}
for (const theme of ["light", "dark"]) {
  const name = `desk-computer${theme === "light" ? "-light" : ""}.html`;
  const page = readFileSync(join(original, "examples/desk-computer", name), "utf8");
  const svg = page.slice(page.indexOf("<svg"), page.indexOf("</svg>"));
  const lines = [];
  for (const match of svg.matchAll(/<(\/?)(path|circle|rect|text|g|clipPath)\b([^>]*?)\/?>(?:([^<]*)<\/text>)?/g)) {
    const [, closing, tag, raw, text] = match;
    if (closing) { lines.push(`/${tag}`); continue; }
    const attrs = Object.fromEntries([...raw.matchAll(/([\w-]+)="([^"]*)"/g)].map(m => [m[1], decode(m[2])]));
    if (attrs.d) attrs.d = expandHorizontal(attrs.d).replace(/-?\d+(?:\.\d+)?/g, value => String(Number(value)));
    if (/\bdata-free(?:\s|$)/.test(raw)) attrs["data-free"] = "";
    if (tag === "text") attrs.text = decode(text ?? "");
    lines.push([tag, ...Object.keys(attrs).sort().map(key => `${key}=${attrs[key]}`)].join("\t"));
  }
  writeFileSync(join(here, `../src/jvmTest/resources/goldens/desk-computer-${theme}.txt`), lines.join("\n") + "\n");
  console.log(`${theme}: ${lines.length} element lines`);
}
