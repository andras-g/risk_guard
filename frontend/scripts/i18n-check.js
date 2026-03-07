import fs from 'fs';
import path from 'path';

// Canonical i18n directory is under app/i18n/ (Nuxt 4 compat mode)
const i18nRoot = './app/i18n';
const languages = ['hu', 'en'];
const baseLang = 'hu';
const targetLangs = ['en'];

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

const baseModules = fs.readdirSync(path.join(i18nRoot, baseLang))
  .filter(file => file.endsWith('.json'));

let globalError = false;

baseModules.forEach(moduleFile => {
  const moduleName = path.basename(moduleFile, '.json');
  console.log(`Checking module: ${moduleName}`);
  
  const basePath = path.join(i18nRoot, baseLang, moduleFile);
  const baseContent = JSON.parse(fs.readFileSync(basePath, 'utf8'));
  const baseKeys = getAllKeys(baseContent).sort();

  targetLangs.forEach(lang => {
    const targetPath = path.join(i18nRoot, lang, moduleFile);
    if (!fs.existsSync(targetPath)) {
      console.error(`Error: Module file ${moduleFile} is missing for language ${lang}`);
      globalError = true;
      return;
    }

    const targetContent = JSON.parse(fs.readFileSync(targetPath, 'utf8'));
    const targetKeys = getAllKeys(targetContent).sort();

    const missingInTarget = baseKeys.filter(key => !targetKeys.includes(key));
    const extraInTarget = targetKeys.filter(key => !baseKeys.includes(key));

    if (missingInTarget.length > 0) {
      console.error(`Missing keys in ${lang}/${moduleFile}:`, missingInTarget);
      globalError = true;
    }
    if (extraInTarget.length > 0) {
      console.error(`Extra keys in ${lang}/${moduleFile} (not in ${baseLang}):`, extraInTarget);
      globalError = true;
    }
  });
});

if (globalError) {
  process.exit(1);
}

console.log('✓ i18n key parity check passed for all modules');
