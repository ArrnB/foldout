// Tests the page detector in foldout-scanner.html against synthetic photos
// with known page corners (made by make_scenes.py).
//
//   python tests/make_scenes.py tests/scenes     (needs numpy and pillow)
//   node tests/detect_test.mjs tests/scenes
//
// A scene passes when every detected corner is within 2.5% of the photo's
// diagonal of the true one, in the right order (TL, TR, BR, BL), and when
// nothing is found in the scenes without a page. Scenes listed in
// KNOWN_HARD are reported but allowed to fail.
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const KNOWN_HARD = new Set(["two_pages"]); // overlapping sheets merge into one outline

const here = dirname(fileURLToPath(import.meta.url));
const html = readFileSync(join(here, "..", "foldout-scanner.html"), "utf8");
const js = html.match(/<script>([\s\S]*)<\/script>/)[1];
function grab(name) {
  const start = js.indexOf("  function " + name + "(");
  const end = js.indexOf("\n  }\n", start);
  return js.slice(start, end + 4);
}
const detectDocument = new Function(grab("detectDocument") + "return detectDocument;")();

const dir = process.argv[2] || join(here, "scenes");
const index = JSON.parse(readFileSync(join(dir, "index.json"), "utf8"));

let failures = 0;
for (const s of index) {
  const rgba = new Uint8Array(readFileSync(join(dir, `${s.name}.rgba`)));
  const t0 = performance.now();
  const r = detectDocument(rgba, s.w, s.h);
  const ms = (performance.now() - t0).toFixed(0);
  const diag = Math.hypot(s.w, s.h);
  let ok, detail = "";
  if (!s.quad) {
    ok = !r;
    detail = r ? "found a page that is not there" : "nothing found, as expected";
  } else if (!r) {
    ok = false;
    detail = "no page found";
  } else {
    const errs = s.quad.map(([x, y], i) => Math.hypot(r.corners[i].x - x, r.corners[i].y - y) / diag * 100);
    const worst = Math.max(...errs);
    ok = worst < 2.5;
    detail = `worst corner off by ${worst.toFixed(2)}% of the diagonal, score ${r.score.toFixed(2)}`;
  }
  const known = KNOWN_HARD.has(s.name);
  if (!ok && !known) failures++;
  console.log(`${ok ? "pass" : known ? "known" : "FAIL"}  ${s.name.padEnd(14)} ${ms.padStart(4)} ms  ${detail}`);
}
console.log(failures ? `${failures} failed` : "all passed");
process.exit(failures ? 1 : 0);
