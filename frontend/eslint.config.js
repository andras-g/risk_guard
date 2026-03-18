import pluginVue from 'eslint-plugin-vue';
import vuejsAccessibility from 'eslint-plugin-vuejs-accessibility';
import tsParser from '@typescript-eslint/parser';
import tsPlugin from '@typescript-eslint/eslint-plugin';
import vueParser from 'vue-eslint-parser';

export default [
  {
    ignores: ['.nuxt/**', 'dist/**', 'node_modules/**'],
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
    },
  },
];
