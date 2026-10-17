const fs = require('fs'), path = require('path');
const S = require('./smaf.js');
const files = fs.readFileSync('files.txt', 'utf8').trim().split('\n');
const lines = [];
files.forEach((f, k) => {
  const i = k + 1;
  const n = String(i).padStart(3, '0') + '_' + path.basename(f);
  const c = S.convert(new Uint8Array(fs.readFileSync(f)));
  if (!c.data) { lines.push(n + ' null'); return; }
  fs.writeFileSync(path.join('outn', n + (c.type === 'audio/midi' ? '.mid' : '.wav')), c.data);
  lines.push(n + ' ' + c.type + ' ' + c.durationMs + ' ' + c.data.length);
});
fs.writeFileSync('node.log', lines.join('\n') + '\n');
