import type { RunOptions } from 'axe-core'

/**
 * Shared axe-core configuration for WCAG 2.1 AA accessibility tests.
 *
 * Used across all a11y integration test files to ensure consistent
 * WCAG scanning configuration.
 */
export const axeOptions: RunOptions = {
  // Run against WCAG 2.1 Level AA standard
  runOnly: {
    type: 'tag',
    values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'],
  },
  rules: {
    // PrimeVue renders role="none" on some wrapper divs which is valid but
    // triggers false positives on older axe rules. Disable if noisy.
    // 'presentation-role-conflict': { enabled: false },

    // PrimeVue Drawer uses aria-hidden on backdrop which can conflict
    // with the color-contrast rule on hidden elements. Acceptable.
    // 'color-contrast': { enabled: true },

    // Region rule can be noisy in component-level tests where the full
    // page layout with landmarks isn't present. Disable for isolated components.
    region: { enabled: false },
  },
}
