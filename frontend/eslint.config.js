import pluginVue from 'eslint-plugin-vue';
import vuejsAccessibility from 'eslint-plugin-vuejs-accessibility';
import tsParser from '@typescript-eslint/parser';
import tsPlugin from '@typescript-eslint/eslint-plugin';
import vueParser from 'vue-eslint-parser';

// Story 10.1 AC #12 — picker-isolation guardrail.
// After the Anyagkönyvtár /epr page is deleted (Story 10.1), epr_material_templates survives
// only as an internal building-block table referenced via material_template_id on
// product_packaging_components. Frontend access to template data must go through
// useMaterialTemplatePicker (in composables/registry/). This rule prevents future drift:
// any file OUTSIDE components/registry/* or composables/registry/* that imports the picker
// OR calls /api/v1/epr/materials directly will fail lint.
const RESTRICT_PICKER_IMPORT = {
  paths: [{
    name: '~/composables/registry/useMaterialTemplatePicker',
    message: 'Material-template access is Registry-scoped. Use via components/registry/* or composables/registry/* only.',
  }],
};

const RESTRICT_MATERIAL_TEMPLATES_URL = {
  selector: "Literal[value=/\\/api\\/v1\\/epr\\/materials(\\/|\\?|$)/]",
  message: 'Material-template endpoints are Registry-scoped. Use useMaterialTemplatePicker from components/registry/* instead.',
};

// Template literals (backtick strings) also bypass Literal-based rules.
const RESTRICT_MATERIAL_TEMPLATES_URL_TEMPLATE = {
  selector: "TemplateLiteral:has(TemplateElement[value.raw=/\\/api\\/v1\\/epr\\/materials/])",
  message: 'Material-template endpoints are Registry-scoped. Use useMaterialTemplatePicker from components/registry/* instead.',
};

export default [
  {
    ignores: ['.nuxt/**', '.output/**', 'dist/**', 'node_modules/**'],
  },
  ...pluginVue.configs['flat/recommended'],
  ...vuejsAccessibility.configs['flat/recommended'],
  {
    files: ['**/*.{ts,vue}'],
    languageOptions: {
      parser: vueParser,
      parserOptions: {
        parser: tsParser,
        sourceType: 'module',
        ecmaVersion: 'latest',
        extraFileExtensions: ['.vue'],
      },
    },
    plugins: {
      '@typescript-eslint': tsPlugin,
    },
    rules: {
      'vue/multi-word-component-names': 'off',
      '@typescript-eslint/no-unused-vars': 'warn',
      // PrimeVue wraps native inputs in components — label `for` links to the rendered input's id.
      // Accept `for` attribute alone without requiring a nested native control element.
      'vuejs-accessibility/label-has-for': ['error', {
        required: { some: ['nesting', 'id'] }
      }],
      'no-restricted-imports': ['error', RESTRICT_PICKER_IMPORT],
      'no-restricted-syntax': ['error', RESTRICT_MATERIAL_TEMPLATES_URL, RESTRICT_MATERIAL_TEMPLATES_URL_TEMPLATE],
    },
  },
  // Allow the Registry scope — and the picker's own implementation — to import itself and
  // call the underlying URL directly. Everything else must go through the composable.
  {
    files: [
      'app/components/registry/**',
      'app/composables/registry/**',
      'app/pages/registry/**',
      // Generated API types / central useApi layer are infrastructure, not direct callers.
      'app/composables/api/**',
      'app/types/**',
      // Story 10.1 transitional exception: the legacy Anyagkönyvtár-backed filing flow
      // (stores/epr.ts + the surviving pages/epr/filing.vue) still consumes template data
      // directly. Stories 10.6/10.7 rebuild this path on the product-first aggregator; the
      // exception is removed at that point. Do NOT widen beyond these two paths.
      'app/stores/epr.ts',
      'app/pages/epr/filing.vue',
    ],
    rules: {
      'no-restricted-imports': 'off',
      'no-restricted-syntax': 'off',
    },
  },
];
