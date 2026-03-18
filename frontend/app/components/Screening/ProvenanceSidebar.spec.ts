import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import ProvenanceSidebar from './ProvenanceSidebar.vue'
import type { SnapshotProvenanceResponse } from '~/types/api'

/**
 * Component tests for ProvenanceSidebar.vue.
 * Tests source list rendering, status icons, relative timestamps,
 * accordion mobile mode, and freshness indicator.
 *
 * Co-located per architecture rules.
 */

// Stub PrimeVue Accordion components
const AccordionStub = { template: '<div class="accordion-stub"><slot /></div>' }
const AccordionPanelStub = { template: '<div class="accordion-panel-stub"><slot /></div>' }
const AccordionHeaderStub = { template: '<div class="accordion-header-stub"><slot /></div>' }
const AccordionContentStub = { template: '<div class="accordion-content-stub"><slot /></div>' }

// Stub useI18n — return i18n key, but for source display names return the key itself
// so sourceDisplayName falls back to raw adapter key (matching existing assertions)
vi.stubGlobal('useI18n', () => ({
  t: (key: string) => {
    // Return actual display names for source name keys to match test assertions
    const sourceNames: Record<string, string> = {
      'screening.provenance.sources.nav-debt': 'NAV Tartozás',
      'screening.provenance.sources.e-cegjegyzek': 'e-Cégjegyzék',
      'screening.provenance.sources.cegkozlony': 'Cégközlöny',
      'screening.provenance.sources.nav-online-szamla': 'NAV Online Számla',
      'screening.provenance.sources.demo': 'Demo adatok',
    }
    return sourceNames[key] ?? key
  },
}))

// Stub useDateRelative composable
vi.mock('~/composables/formatting/useDateRelative', () => ({
  useDateRelative: () => ({
    formatRelative: (date: string | null) => date ? '2 hours ago' : '',
  }),
}))

// Stub risk-guard-tokens import (used for freshness thresholds)
vi.mock('~/risk-guard-tokens.json', () => ({
  default: {
    freshness: { freshThresholdHours: 6, staleThresholdHours: 24, unavailableThresholdHours: 48 },
  },
}))

// Use timestamps relative to "now" so source freshness checks work correctly.
// Available sources use a "just now" timestamp (< 6h threshold → checkmark icon).
// Unavailable sources get their icon from the `available: false` flag regardless of timestamp.
function buildProvenance(overrides: Partial<SnapshotProvenanceResponse> = {}): SnapshotProvenanceResponse {
  const justNow = new Date().toISOString()
  return {
    snapshotId: 'snapshot-uuid-1',
    taxNumber: '12345678',
    checkedAt: justNow,
    sources: [
      { sourceName: 'nav-debt', available: true, checkedAt: justNow, sourceUrl: null },
      { sourceName: 'e-cegjegyzek', available: true, checkedAt: justNow, sourceUrl: 'https://e-cegjegyzek.hu' },
      { sourceName: 'cegkozlony', available: false, checkedAt: justNow, sourceUrl: null },
    ],
    ...overrides,
  }
}

function mountSidebar(
  provenance: SnapshotProvenanceResponse | null,
  confidence: 'FRESH' | 'STALE' | 'UNAVAILABLE' = 'FRESH',
  mobile = false
) {
  return mount(ProvenanceSidebar, {
    props: { provenance, confidence, mobile },
    global: {
      stubs: {
        Accordion: AccordionStub,
        AccordionPanel: AccordionPanelStub,
        AccordionHeader: AccordionHeaderStub,
        AccordionContent: AccordionContentStub,
      },
    },
  })
}

describe('ProvenanceSidebar — source list rendering', () => {
  it('should render list of data sources', () => {
    const wrapper = mountSidebar(buildProvenance())
    const sourceList = wrapper.find('[data-testid="source-list"]')
    expect(sourceList.exists()).toBe(true)
    const items = wrapper.findAll('[data-testid^="source-entry-"]')
    expect(items).toHaveLength(3)
  })

  it('should render source names', () => {
    const wrapper = mountSidebar(buildProvenance())
    expect(wrapper.html()).toContain('NAV Tartozás')
    expect(wrapper.html()).toContain('e-Cégjegyzék')
    expect(wrapper.html()).toContain('Cégközlöny')
  })

  it('should render relative timestamp for sources with checkedAt', () => {
    const wrapper = mountSidebar(buildProvenance())
    expect(wrapper.html()).toContain('2 hours ago')
  })

  it('should render source URL as link with user-friendly text when available', () => {
    const wrapper = mountSidebar(buildProvenance())
    const link = wrapper.find('[data-testid="source-url-e-cegjegyzek"]')
    expect(link.exists()).toBe(true)
    expect(link.attributes('href')).toBe('https://e-cegjegyzek.hu')
    // Link text should be the i18n key for "View source", not the raw URL
    expect(link.text()).toContain('screening.provenance.viewSource')
  })

  it('should not render URL link when sourceUrl is null', () => {
    const wrapper = mountSidebar(buildProvenance())
    expect(wrapper.find('[data-testid="source-url-nav-debt"]').exists()).toBe(false)
  })
})

describe('ProvenanceSidebar — status icons', () => {
  it('should show checkmark icon for available sources', () => {
    const wrapper = mountSidebar(buildProvenance())
    const navDebtEntry = wrapper.find('[data-testid="source-entry-nav-debt"]')
    expect(navDebtEntry.find('.pi-check-circle').exists()).toBe(true)
  })

  it('should show X icon for unavailable sources', () => {
    const wrapper = mountSidebar(buildProvenance())
    const cegkozlonyEntry = wrapper.find('[data-testid="source-entry-cegkozlony"]')
    expect(cegkozlonyEntry.find('.pi-times-circle').exists()).toBe(true)
  })

  it('should show clock icon for stale sources (checkedAt exceeds freshness threshold)', () => {
    // Create a source with checkedAt > 6h ago (fresh threshold) to trigger stale icon
    const sevenHoursAgo = new Date(Date.now() - 7 * 60 * 60 * 1000).toISOString()
    const provenance = buildProvenance({
      sources: [
        { sourceName: 'nav-debt', available: true, checkedAt: sevenHoursAgo, sourceUrl: null },
      ],
    })
    const wrapper = mountSidebar(provenance)
    const navDebtEntry = wrapper.find('[data-testid="source-entry-nav-debt"]')
    expect(navDebtEntry.find('.pi-clock').exists()).toBe(true)
    expect(navDebtEntry.find('.text-amber-500').exists()).toBe(true)
  })
})

describe('ProvenanceSidebar — no sources', () => {
  it('should show no sources message when sources list is empty', () => {
    const wrapper = mountSidebar(buildProvenance({ sources: [] }))
    expect(wrapper.html()).toContain('screening.provenance.noSources')
  })

  it('should show no sources message when provenance is null', () => {
    const wrapper = mountSidebar(null)
    expect(wrapper.html()).toContain('screening.provenance.noSources')
  })
})

describe('ProvenanceSidebar — freshness indicator', () => {
  it('should show FRESH freshness indicator in emerald', () => {
    const wrapper = mountSidebar(buildProvenance(), 'FRESH')
    const indicator = wrapper.find('[data-testid="freshness-indicator"]')
    expect(indicator.exists()).toBe(true)
    expect(indicator.classes()).toContain('text-emerald-700')
    expect(wrapper.html()).toContain('screening.freshness.fresh')
  })

  it('should show STALE freshness indicator in amber', () => {
    const wrapper = mountSidebar(buildProvenance(), 'STALE')
    const indicator = wrapper.find('[data-testid="freshness-indicator"]')
    expect(indicator.classes()).toContain('text-amber-700')
    expect(wrapper.html()).toContain('screening.freshness.stale')
  })

  it('should show UNAVAILABLE freshness indicator in slate', () => {
    const wrapper = mountSidebar(buildProvenance(), 'UNAVAILABLE')
    const indicator = wrapper.find('[data-testid="freshness-indicator"]')
    expect(indicator.classes()).toContain('text-slate-600')
    expect(wrapper.html()).toContain('screening.freshness.unavailable')
  })
})

describe('ProvenanceSidebar — mobile accordion', () => {
  it('should render mobile accordion when mobile prop is true', () => {
    const wrapper = mountSidebar(buildProvenance(), 'FRESH', true)
    expect(wrapper.find('[data-testid="provenance-sidebar-mobile"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="provenance-sidebar-desktop"]').exists()).toBe(false)
  })

  it('should render desktop layout when mobile prop is false', () => {
    const wrapper = mountSidebar(buildProvenance(), 'FRESH', false)
    expect(wrapper.find('[data-testid="provenance-sidebar-desktop"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="provenance-sidebar-mobile"]').exists()).toBe(false)
  })

  it('should show accordion label for mobile', () => {
    const wrapper = mountSidebar(buildProvenance(), 'FRESH', true)
    expect(wrapper.html()).toContain('screening.provenance.dataSourceDetails')
  })

  it('should render source list entries inside the mobile accordion content', () => {
    // Verifies source entries exist within the mobile accordion content area,
    // not just that the accordion container exists.
    const wrapper = mountSidebar(buildProvenance(), 'FRESH', true)
    // The AccordionContent stub always renders its slot, so we can verify entries appear
    const mobileContainer = wrapper.find('[data-testid="provenance-sidebar-mobile"]')
    expect(mobileContainer.exists()).toBe(true)
    const sourceItems = mobileContainer.findAll('[data-testid^="source-entry-"]')
    expect(sourceItems).toHaveLength(3)
  })

  it('should render freshness indicator inside the mobile accordion content', () => {
    const wrapper = mountSidebar(buildProvenance(), 'STALE', true)
    const mobileContainer = wrapper.find('[data-testid="provenance-sidebar-mobile"]')
    const indicator = mobileContainer.find('[data-testid="freshness-indicator"]')
    expect(indicator.exists()).toBe(true)
    expect(indicator.classes()).toContain('text-amber-700')
  })
})
