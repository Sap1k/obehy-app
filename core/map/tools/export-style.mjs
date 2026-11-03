// Exports the web app's map style (repo/web/src/features/map/style.ts) as the app's light and
// dark base styles, so both apps draw the same map. Run after the web style changes:
//
//   node core/map/tools/export-style.mjs ../repo/web
//
// Needs Node 23.6+ (it loads the TypeScript directly). The trip arrows are left out: the app
// draws journeys without the web's arrow image.
import { writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { pathToFileURL } from "node:url";

const web = process.argv[2] ?? "../repo/web";
const { mapStyle } = await import(pathToFileURL(resolve(web, "src/features/map/style.ts")).href);
const out = resolve(import.meta.dirname, "../src/main/assets");
for (const [name, dark] of [["light", false], ["dark", true]]) {
  const style = mapStyle(dark);
  style.layers = style.layers.filter((layer) => layer.id !== "trip-arrows");
  writeFileSync(resolve(out, `map-style-${name}.json`), JSON.stringify(style, null, 1) + "\n");
}
