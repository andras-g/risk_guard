# ADR-0002: Pluggable EPR report target

- **Status:** Accepted
- **Date:** 2026-04-14
- **Deciders:** Andras (PO), Winston (Architect), Bob (SM), Amelia (Dev)
- **Related artefacts:**
  - `_bmad-output/planning-artifacts/sprint-change-proposal-2026-04-14.md` (CP-5, §4.5, §5 invariant 3)
  - `_bmad-output/planning-artifacts/research/okirkapu-and-kf-refresh-2026-04-14.md` — **supersedes CP-5 §8 on submission format**
  - `_bmad-output/planning-artifacts/epr-packaging-calculation-gap-2026-04-14.md`

## Context

Risk Guard exists to produce the artefact a Hungarian KKV producer uploads to satisfy their quarterly EPR data-service obligation. The system that *receives* that artefact, and the *format* it expects, are not stable over the planning horizon:

| Era | Receiver (today's understanding) | Format |
|---|---|---|
| 2026 (now) | OKIRkapu portal, run by Pest Vármegyei Kormányhivatal (Országos Hulladékgazdálkodási Hatóság). The producer uploads the file; the authority forwards aggregated data to MOHU by the 25th of the month following the quarter. **MOHU never receives the producer's raw file.** | XML, validated against the published `KG:KGYF-NÉ` XSD. CSV is *not* accepted. Manual web-form entry is a legally valid alternative for users who cannot generate XML. |
| Post-2029 | Single EU producer registry (PPWR Regulation 2025/40, replaces national registers). Format defined by the Commission implementing act due 2026-02-12. | TBD by the implementing act; expected to align with LUCID-style XML at minimum. |

CP-5 §4.5 originally framed Story 9.4 as a **MOHU CSV exporter**. The 2026-04-14 OKIRkapu research found that framing is wrong on both the receiver and the format:

- The receiver is OKIRkapu (the authority's portal), not MOHU directly.
- OKIRkapu accepts XML against a published XSD, not CSV.

This ADR records the corrected framing and the architectural commitment that locks in PPWR-readiness — namely, that the EPR module never names the concrete report target. CP-5 §5 invariant 3 ("the EPR report target is pluggable; MOHU is not the system of record") becomes binding in code, enforced by ArchUnit.

Without this commitment, the 2029 EU-registry transition forces a rewrite of every caller that today references "the MOHU exporter" or "the CSV writer". With it, the transition is a new strategy class plus a configuration flip.

## Decision

**`EprService` and all callers depend only on an `EprReportTarget` interface. Today's sole implementation is `OkirkapuXmlExporter`, which produces a `KG:KGYF-NÉ`-schema-validated XML file aggregated per KF code. The post-2029 EU-registry adapter ships as a sibling implementation (`EuRegistryAdapter`) when the Commission implementing act fixes the format. No service outside the report-target package may reference any concrete implementation directly. ArchUnit enforces the boundary.**

The output of an `EprReportTarget` is always a downloadable file artefact for the user to upload to the receiving system. **Risk Guard does not submit on the user's behalf.** No automated round-trip submission exists today (OKIRkapu has no REST/SOAP submission API, per the 2026-04-14 research) and none is designed for; the strategy boundary is at file generation, not at transport.

### Component shape

```
hu.riskguard.epr.report
├── EprReportTarget                // strategy interface
├── EprReportRequest               // record: tenantId, period, registryProducts, fallbackVtszMappings
├── EprReportArtifact              // record: filename, contentType, bytes, summaryReport, provenanceLines[]
├── EprReportProvenance            // record per output line: REGISTRY_MATCH | VTSZ_FALLBACK | UNMATCHED
└── EprReportFormat                // enum: OKIRKAPU_XML | EU_REGISTRY (future)

hu.riskguard.epr.report.internal
├── OkirkapuXmlExporter            // EprReportTarget impl, today's only implementation
├── KgKgyfNeAggregator             // GROUP BY kf_code, sum kg over reporting period
├── KgKgyfNeMarshaller             // JAXB-marshal to KG:KGYF-NÉ-validated XML
└── jaxb-generated/                // build-time JAXB classes from KG:KGYF-NÉ XSD
```

The XSD itself lives at `backend/src/main/resources/schemas/okirkapu/KG-KGYF-NE-vN.xsd` (downloaded into the repo, version-pinned, exactly mirroring how the NAV Online Számla XSD is handled today — see `datasource.internal.adapters.nav` for the established pattern).

### What "pluggable" means in practice

- `EprService.generateReport(period)` returns `EprReportArtifact` produced by the active `EprReportTarget` bean.
- The active target is selected by Spring profile / config property (`riskguard.epr.report.target=okirkapu-xml`). Default and only valid value at MVP is `okirkapu-xml`.
- A `EuRegistryAdapter` shipped post-2029 registers as `eu-registry` and is selected by config flip per tenant or per environment. Existing callers do not change.
- The `EprReportArtifact.summaryReport` field carries a human-readable per-line breakdown for the user (registry-match vs. VTSZ-fallback vs. unmatched). This summary is target-agnostic — the same summary text is shown whether the producer ends up uploading XML to OKIRkapu or (later) to the EU registry.

### Aggregation contract

The `KG:KGYF-NÉ` schema requires aggregated totals per KF code, **not per-invoice line items** (per 2026-04-14 research §1). The exporter therefore:

1. Pulls outbound NAV invoices for the reporting period (existing Story 8.1 infrastructure).
2. For each invoice line: resolves to packaging components via the registry (Story 9.1) or, if not found, via VTSZ-prefix fallback (Story 8.3 logic, now invoked through `KfCodeClassifierService` per ADR-0001).
3. Multiplies units × per-unit component weights, grouped by KF code.
4. Sums to per-KF-code totals in kg with 3 decimals.
5. Emits the XML with producer-identity header (adószám, KSH stat number, cégjegyzékszám, registered office — sourced from the company-profile entity owned by Story 9.1 or earlier setup story).

Per-line-item provenance is preserved in `EprReportArtifact.provenanceLines[]` for the human summary; only aggregated totals appear in the XML payload itself.

### Provenance recording

Every input line item that contributes to the report carries a provenance tag in the artefact's summary view:

- `REGISTRY_MATCH` — product was found in the registry; component weights came from the registered BoM.
- `VTSZ_FALLBACK` — product was not in the registry; weights came from VTSZ-prefix matching against the `vtszMappings` config.
- `UNMATCHED` — neither path produced a result; the line is listed in the summary with a prompt to register the product. **Unmatched lines do not silently drop from totals** — the user must decide.

Provenance tags are also persisted in `epr_calculations` (existing table, see architecture.md §Data Model) for audit traceability. The persisted form is identical regardless of which `EprReportTarget` implementation produced the artefact.

### What about MOHU?

MOHU's role today is invoicing, not data ingestion. They issue the producer's quarterly fee invoice based on data the authority forwards to them after OKIRkapu submission. Risk Guard never sends anything to MOHU. The previous codebase contains a class named `MohuExporter` (in `hu.riskguard.epr.domain`) — that class is misnamed under the corrected framing and will be either renamed/replaced as part of Story 9.4 or absorbed into `OkirkapuXmlExporter`. Either way, after Story 9.4 lands, no caller outside `hu.riskguard.epr.report.internal` references "MOHU" by name in its dependency graph.

## Consequences

### Positive

- **PPWR transition is a configuration change, not a rewrite.** When the EU registry format is fixed (likely 2027–2029), shipping `EuRegistryAdapter` is purely additive. Callers, audit fields, the `epr_calculations` table, and the user-facing summary text all stay put.
- **Schema-validated output by construction.** JAXB marshalling against the pinned `KG:KGYF-NÉ` XSD means malformed XML cannot leave the system. Mirrors the existing NAV-XSD discipline.
- **Provenance is first-class.** The `provenanceLines[]` contract means the human summary, the audit table, and the user-facing remediation prompt all draw from the same source of truth.
- **Naming reflects reality.** "MOHU CSV exporter" was wrong on both halves; "OkirkapuXmlExporter" describes what the artefact actually is and where it goes.
- **No automated submission means no automated submission failure.** OKIRkapu has no programmatic ingestion endpoint; designing for file-export only matches the channel, removes a class of integration risk, and leaves a clean upgrade path if/when the authority publishes one.

### Negative / accepted costs

- **Manual upload step remains in the user journey.** The user downloads the XML and uploads it to `kapu.okir.hu` themselves. Acceptable because no API alternative exists today; the artefact ships with copy-paste-ready upload instructions in the summary.
- **JAXB / XSD pipeline duplicated.** The build pipeline now generates JAXB classes from two unrelated schema families (NAV + OKIRkapu). Marginal extra build complexity; the pattern is identical so maintenance burden is small.
- **Renaming `MohuExporter` is a breaking change inside the EPR module.** Touched by Story 9.4. Visible only inside the module; no public API change because no external module consumed `MohuExporter` directly.
- **Adding a sibling `EprReportTarget` later is not free.** It requires re-deriving the aggregation contract (the EU registry may aggregate differently), authoring a new XSD pipeline, and writing per-target tests. The interface saves call-site churn, not the new exporter's own complexity.
- **Manual-form fallback is out of scope here.** If a producer cannot upload XML for any reason, OKIRkapu's manual web form remains legally valid — but Risk Guard's UX does not guide them through field-by-field transcription. Captured as an open question in the research doc; revisit if user feedback demands it.
- **One concrete strategy at MVP means the abstraction is partly speculative.** Until `EuRegistryAdapter` actually ships, the interface's shape is inferred from one implementation. We accept the risk that the EU implementing act forces a refinement of `EprReportTarget` when it lands; that refinement is still smaller than re-threading every caller.

## Alternatives considered

- **Bake OKIRkapu XML directly into `EprService`; abstract later when the EU registry format is known.** Smallest possible code today; biggest 2029 rewrite. Rejected because the cost of the abstraction (one interface, one router bean, one ArchUnit rule) is trivial against a known-coming format change, and CP-5 §5 already mandates this invariant.
- **Stay with "MOHU CSV" as documented in CP-5 §4.5.** The naming and the format are both wrong per the 2026-04-14 OKIRkapu research; building it would mean producing artefacts the receiving system literally rejects. Hard reject.
- **Per-tenant multi-target (one tenant on OKIRkapu XML, another on something else, simultaneously).** No business case at MVP — every Hungarian producer reports through OKIRkapu. The interface technically supports it (config selects the bean), but no work is done to surface a per-tenant selector in the UI. Revisit if cross-border or multi-jurisdiction tenancy becomes a product direction.
- **Generate XML by string templates instead of JAXB-from-XSD.** Faster to ship, but loses schema-validation-by-construction. Rejected on the same grounds the NAV adapter rejected it: schema validation at marshal time catches bugs that string templates ship to production.
- **Automated submission via screen-scraping OKIRkapu.** Re-introduces the Playwright/scraping liabilities that the 2026-03-12 course correction (CP-1) explicitly removed from architecture. Hard reject.

## Revisit triggers

Reopen this ADR if any of the following:

- **EU implementing act published.** When the Commission ships the 2026-02-12 (slipped — verify status) implementing act for the EU producer registry format, draft `EuRegistryAdapter` and revisit interface shape if the act's data model demands it.
- **OKIRkapu publishes a submission API.** A REST/SOAP endpoint would let `EprReportTarget` extend from "produce file" to "produce file + submit", changing the interface contract. Verify with OKIRkapu developer portal annually.
- **MOHU begins accepting direct submissions.** Currently they only invoice; if their role expands to ingestion, a `MohuApiTarget` becomes a candidate sibling. Unlikely on present evidence.
- **`KG:KGYF-NÉ` XSD version changes.** A new XSD version (typically tied to KF-code amendments) requires regenerating JAXB classes and re-validating the marshaller. The Magyar Közlöny watch process from ADR-0001's neighbouring concerns covers this.
- **Per-tenant multi-target requirement appears.** Cross-border tenancy or a regulator-driven dual-submission requirement makes per-tenant target selection real, requiring UI + tenant-config work beyond the current bean-level switch.
- **Manual-form-fallback UX demand surfaces.** If a meaningful fraction of users report that XML upload fails for them and they need transcription help, scope a manual-form guidance feature behind the existing `EprReportArtifact.summaryReport` channel.
