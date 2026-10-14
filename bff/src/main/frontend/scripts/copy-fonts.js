// Copies ONLY the self-hosted Poppins files the stylesheet declares (latin
// subset, weights 400/500/600/700) from @fontsource/poppins into the generated
// static root, so they are served at /fonts/**. Deterministic: fixed list,
// plain byte copy, no network.
const fs = require('node:fs');
const path = require('node:path');

const WEIGHTS = [400, 500, 600, 700];
const source = path.dirname(require.resolve('@fontsource/poppins/package.json'));
const target = path.join(__dirname, '..', '..', '..', '..', 'build', 'generated', 'tailwind', 'static', 'fonts');

fs.rmSync(target, { recursive: true, force: true });
fs.mkdirSync(target, { recursive: true });
for (const weight of WEIGHTS) {
  const file = `poppins-latin-${weight}-normal.woff2`;
  fs.copyFileSync(path.join(source, 'files', file), path.join(target, file));
}
