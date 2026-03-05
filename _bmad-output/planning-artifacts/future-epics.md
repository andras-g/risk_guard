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
