# risk_guard - Future Epics (Post-MVP)

This document contains epics and stories planned for Phase 2 and beyond, focused on monetization, scale, and advanced features.

## Epic 7: Subscriptions, Payments & Invoicing
**Goal:** Enable users to subscribe to paid tiers via an online payment provider and receive automated, compliant invoices.

### Story 7.1: Payment Provider Integration (Stripe/Barion)
As a User,
I want to pay for my subscription using a credit card or Apple/Google Pay,
So that I can instantly access PRO and EPR features.

**Acceptance Criteria:**

**Given** a user on the "Pricing" page
**When** they select a tier and click "Subscribe"
**Then** they are redirected to a secure hosted checkout (Stripe or Barion)
**And** upon successful payment, the backend receives a Webhook to update the tenant's tier in the database.

### Story 7.2: Automated Invoicing Integration (Számlázz.hu/Billingo)
As a System,
I want to automatically generate and send a Hungarian tax-compliant invoice upon successful payment,
So that I don't have to manually create invoices for every customer.

**Acceptance Criteria:**

**Given** a successful subscription payment
**When** the payment webhook is processed
**Then** the backend calls the external Invoicing API (Számlázz.hu or Billingo) with the user's billing data
**And** the generated PDF invoice is emailed to the user and stored in a billing_history table.

### Story 7.3: Subscription-Based Feature Gating
As a System,
I want to enforce the @TierRequired annotation across all API endpoints,
So that users can only access features they have paid for.

**Acceptance Criteria:**

**Given** a user on the ALAP (Free) tier
**When** they attempt to access /api/v1/epr/** or trigger a PDF export
**Then** the backend returns a 403 Forbidden with a RFC 7807 error urn:riskguard:error:tier-upgrade-required
**And** the frontend displays an "Upgrade Required" modal.

### Story 7.4: User Billing Dashboard
As a User,
I want a dedicated billing page where I can see my active plan and download past invoices,
So that I can manage my company's administrative records.

**Acceptance Criteria:**

**Given** the user settings menu
**When** I visit the "Billing" tab
**Then** I see my current tier, next billing date, and a list of historical invoices with download links.

---

## Non-DARAB Unit-of-Measure Support

**Context:** Epic 10 Story 10.5's invoice-driven aggregator handles only invoice lines with `unitOfMeasure='DARAB'`. Lines in other units (`KG`, `LITER`, `METER`, `M2`) are classified as `UNSUPPORTED_UNIT_OF_MEASURE` and surface in the unresolved panel for manual intervention. This is a deliberate Epic 10 scope deferral — DARAB coverage is sufficient for most common packaging scenarios and avoids per-product unit-conversion complexity in the initial product-first filing rollout.

**Scope for a future story:**
- Extend Registry components (or the `products` row) with a per-product unit-of-measure conversion: "1 product unit = N invoice units", with the semantics depending on the invoice unit (kg, meter, liter, m²).
- Extend the aggregator to multiply invoice quantity by the conversion factor before applying `items_per_parent` ratios across `wrapping_level`s.
- UI: a conversion-factor field on the Registry product editor, with a helper to explain when it's needed (based on the invoice line's unit).
- Use case: building-materials retailers (tüzép) selling cement in kg, cable in meters, insulation in m², where the invoice quantity is a weight / length / area rather than a piece count.

**Why deferred from Epic 10:** unit conversion introduces per-product mapping complexity (different products may have different conversion semantics, e.g. a single SKU sold in both kg and pieces) that's better tackled once the DARAB flow is stable and has real user feedback.
