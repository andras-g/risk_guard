/**
 * Vue I18n runtime configuration.
 *
 * This file is automatically loaded by @nuxtjs/i18n v10 to configure
 * the underlying vue-i18n instance.
 *
 * - fallbackLocale: English is used when a Hungarian translation is missing.
 * - missingWarn/fallbackWarn: Suppressed in production to avoid console noise.
 *   In development, warnings help catch missing keys early.
 */
export default defineI18nConfig(() => ({
  fallbackLocale: 'en',
  missingWarn: process.env.NODE_ENV !== 'production',
  fallbackWarn: process.env.NODE_ENV !== 'production',
}))
