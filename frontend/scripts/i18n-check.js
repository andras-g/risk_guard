import fs from 'fs';
import path from 'path';

const huPath = './i18n/hu.json';
const enPath = './i18n/en.json';

const hu = JSON.parse(fs.readFileSync(huPath, 'utf8'));
const en = JSON.parse(fs.readFileSync(enPath, 'utf8'));

function getAllKeys(obj, prefix = '') {
  return Object.keys(obj).reduce((res, el) => {
    if (Array.isArray(obj[el])) {
      return [...res, prefix + el];
    } else if (typeof obj[el] === 'object' && obj[el] !== null) {
      return [...res, ...getAllKeys(obj[el], prefix + el + '.')];
    } else {
      return [...res, prefix + el];
    }
  }, []);
}

const huKeys = getAllKeys(hu).sort();
const enKeys = getAllKeys(en).sort();

const huMissing = enKeys.filter(key => !huKeys.includes(key));
const enMissing = huKeys.filter(key => !enKeys.includes(key));

if (huMissing.length > 0) {
  console.error('Missing keys in hu.json:', huMissing);
}
if (enMissing.length > 0) {
  console.error('Missing keys in en.json:', enMissing);
}

if (huMissing.length > 0 || enMissing.length > 0) {
  process.exit(1);
}

console.log('✓ i18n key parity check passed');
