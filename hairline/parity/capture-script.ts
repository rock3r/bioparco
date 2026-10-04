export type Step =
  | { adv: number } | { move: [number, number] } | { out: true } | { key: string } | { blur: true }
  | { set: number } | { cp: string };

const circle: Step[] = [];
for (let k = 0; k <= 24; k++) {
  const a = (k / 24) * Math.PI * 2;
  circle.push({ move: [Math.round((200 + 70 * Math.cos(a)) * 100) / 100, Math.round((160 + 70 * Math.sin(a)) * 100) / 100] }, { adv: 1 });
}
const sweep: Step[] = [];
for (let x = 40; x <= 360; x += 40) sweep.push({ move: [x, 176] }, { adv: 1 });

/** The pointer script every figure is put through: rests, a flick, a circle, dwells, intensity changes and leaving. */
export const SCRIPT: Step[] = [
  { adv: 30 }, { cp: "rest" },
  ...sweep, { adv: 6 }, { cp: "sweep" },
  { adv: 40 }, { cp: "sweep coast" },
  { move: [200, 160] }, { adv: 40 }, { cp: "centre" },
  ...circle, { cp: "circle" },
  { adv: 30 }, { cp: "circle settle" },
  { move: [120, 90] }, { adv: 20 }, { cp: "a" },
  { move: [280, 230] }, { adv: 20 }, { cp: "b" },
  { move: [200, 60] }, { adv: 40 }, { move: [200, 260] }, { adv: 40 }, { cp: "c" },
  { set: 0.9 }, { adv: 20 }, { cp: "set high" },
  { move: [150, 200] }, { adv: 10 }, { cp: "moving" },
  { move: [330, 120] }, { adv: 3 }, { cp: "jump" },
  { out: true }, { adv: 30 }, { cp: "leaving" },
  { adv: 300 }, { cp: "left" },
  { set: 0.1 }, { move: [220, 140] }, { adv: 60 }, { cp: "low" },
  { move: [90, 220] }, { adv: 60 }, { cp: "low b" },
  { out: true }, { adv: 400 }, { cp: "final" },
];

export const RIFFLE_KEYS: Step[] = [
  { set: 0.5 },
  { key: "ArrowLeft" }, { adv: 60 }, { cp: "arrow" },
  { key: "ArrowLeft" }, { key: "ArrowLeft" }, { adv: 10 }, { cp: "arrows in flight" },
  { key: "ArrowRight" }, { adv: 60 }, { cp: "arrow back" },
  { key: "Escape" }, { adv: 120 }, { cp: "escape" },
  { key: "ArrowUp" }, { adv: 30 }, { blur: true }, { adv: 120 }, { cp: "blur" },
];
