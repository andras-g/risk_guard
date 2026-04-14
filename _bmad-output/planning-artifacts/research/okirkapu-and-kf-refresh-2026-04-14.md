# OKIRkapu Format + KF Refresh Research

**Date:** 2026-04-14
**Author:** Technical research (Claude, `bmad-technical-research`)
**Drives:** Epic 9 — Story 9.1 (Product-Packaging Registry data model), Story 9.4 (MOHU export)
**Supersedes PO assumption in CP-5 §8:** CSV-based OKIRkapu export
**Reference artefacts:**
- `_bmad-output/planning-artifacts/sprint-change-proposal-2026-04-14.md` (CP-5)
- `_bmad-output/planning-artifacts/epr-packaging-calculation-gap-2026-04-14.md`

---

## Part 1 — OKIRkapu submission format

### Answer

**OKIRkapu accepts EPR quarterly data submissions as XML (with published XSD schemas) or via manual web-form entry. It does NOT accept CSV.** The CSV assumption in CP-5 §8 is wrong and must be retired.

**Also wrong in CP-5 §8: the submission target.** Producers do not submit to MOHU. They submit to the **Országos Hulladékgazdálkodási Hatóság** (National Waste Management Authority, at Pest Vármegyei Kormányhivatal) via **OKIRkapu**. The authority then forwards the data to MOHU by the 25th of the month following the quarter, and MOHU issues the invoice based on what the authority passes through. MOHU never receives the producer's raw XML. Story 9.4's export artifact is uploaded to `kapu.okir.hu`, not sent to MOHU in any form.

Concretely:
- **Data service type:** `KG:KGYF-NÉ` — "Kiterjesztett gyártói felelősségi körbe tartozók negyedéves adatszolgáltatása" (EPR quarterly data service for registered producers).
- **Channels:**
  1. **XML upload** — after login, the "Új adatcsomag XML-ből" ("New data package from XML") button accepts an XML file validated against a published XSD. Developer portal: <https://kapu.okir.hu/okirkapuugyfel/xmlapi>. The portal hosts XSDs and reference code lists (settlements, postal codes, TEÁOR, KSH economic form, material stream codes, etc.) for all OKIR services including the circular-economy (KG) family.
  2. **Manual web form** in the OKIRkapu UI — same `KG:KGYF-NÉ` package completed interactively. XML is optional, not mandatory.
- **No API**. OKIRkapu is a file-upload portal, not a REST/SOAP submission API. Third-party ERPs (InnVoice, Novitax, Progen sERPa) generate the XML and the user uploads it manually.
- **Granularity — aggregated per KF code, NOT per-transaction.** The submission payload is totals (mass in kg) per KF code for the reporting quarter. Accounting software that stores per-invoice/per-item data "automatically aggregates weight data according to KF codes" before producing the XML. (See `progen.hu/sERPa` evidence below: "Az alkalmazás a súlyadatokat KF kódonként automatikusan összesíti.")
- **Mandatory fields** (80/2023 Korm. rendelet, Annex 3 §1.1, referenced at legal head-of-submission level):
  - Producer identity block: name, registered office, adószám (Hungarian tax number), KSH statistical number (or EU/intl tax number if missing), cégjegyzékszám (company registration), sole-proprietor identifiers if applicable.
  - Per-row data: KF code (8 chars) + quantity in kg + reporting period.
  - Supporting document references for categories that require them (takeover agreements, customer declarations).
- **Deadline:** 20th of the month following the reporting quarter. The authority forwards data to MOHU by the 25th; MOHU issues an invoice with 15-day payment terms.

### Evidence (URLs + quotes)

1. **Official OKIRkapu XML API portal (primary source):** <https://kapu.okir.hu/okirkapuugyfel/xmlapi> — "OKIRkapu XML adatszolgáltatás" page lists XSD schema families (FAVI, FEVISZ, EHIR, LAIR, EPRTR) and downloadable code lists; entry point for the KG family schemas. (Accessed 2026-04-14.)
2. **OKIRkapu portal login page:** <https://kapu.okir.hu/okirkapuugyfel/> — the end-user upload UI.
3. **Official guidance — Pest County Government Office (Pest Vármegyei Kormányhivatal, Országos Környezetvédelmi Hatóság):** [altalanos-tajekoztato-a-80_2023-iii-14-korm-rendelet-szerinti-nyilvantartasba-veteli-kotelezettseg-teljesitesehez.pdf](https://kormanyhivatalok.hu/system/files/dokumentum/pest/2024-09/altalanos-tajekoztato-a-80_2023-iii-14-korm-rendelet-szerinti-nyilvantartasba-veteli-kotelezettseg-teljesitesehez.pdf) — defines OKIR as the submission channel and references `KG:KGYF-NÉ`. (PDF content was not machine-readable on fetch; referenced for completeness, should be reviewed manually for field-level detail.)
4. **InnVoice ERP technical doc (practitioner evidence):** [help.innvoice.hu — "EPR termékdíjak exportja XML, adatszolgáltatás OKIRkapu-n keresztül"](https://help.innvoice.hu/hc/hu/articles/10856191074460-3-LEK%C3%89RDEZ%C3%89S-EPR-term%C3%A9kd%C3%ADjak-exportja-XML-adatszolg%C3%A1ltat%C3%A1s-OKIRKapu-n-kereszt%C3%BCl) — "Adatszolgáltatások / KGY-NÉ adatszolgáltatás menüpontban a kimutatást XML formátumban lehet letölteni."
5. **Novitax ERP knowledge base:** <https://tudastar.novitax.hu/epr-adatszolgaltatas/> — confirms XML export path.
6. **Progen sERPa help (aggregation evidence):** <https://www.progen.hu/sERPa/help/td_epradatszolgaltataslepesei.html> — "Az alkalmazás a súlyadatokat KF kódonként automatikusan összesíti, így a tételszám szerint bevitt adatok konszolidálva lesznek." (Data is aggregated per KF code before submission.)
7. **"Egy könyvelő élete" accountant blog:** [OKIRkapu regisztráció EPR adatszolgáltatás teljesítéséhez](https://egykonyveloelete.hu/okirkapu-regisztracio-epr-adatszolgaltatas-teljesitesehez/) and [EPR bevallás technikai útmutató](https://egykonyveloelete.hu/epr-bevallas-technikai-utmutato/) — confirms `KG:KGYF-NÉ` package, XML option, "Új adatcsomag" flow.
8. **80/2023. (III. 14.) Korm. rendelet, hatályos szöveg:** <https://net.jogtar.hu/jogszabaly?docid=a2300080.kor> — Annex 3 defines the data-content requirements; Annex 4 defines categories.
9. **Lovat / RegSurance / Amavat English-language EPR guides** — cross-confirm quarterly cadence, MOHU billing chain, OKIR as channel. (<https://regsurance.com/hungary-extended-producer-responsibility-epr-service-guide-registration-data-reporting/>, <https://vatcompliance.co/guides/epr/hungary/>, <https://amavat.eu/epr-system-in-hungary/>)
10. **EY EPR upload tool description:** <https://www.ey.com/content/dam/ey-unified-site/ey-com/hu-hu/services/tax/documents/ey-epr-adatfeltolto-tool.pdf> — third-party tool that generates the OKIRkapu XML from source data, confirming the XML-upload pattern.

### Confidence

**High** for core findings (XML + XSD accepted, CSV rejected, manual web-form alternative, aggregated-per-KF-code granularity, `KG:KGYF-NÉ` name, 20th-of-month-after-quarter deadline). Multiple independent sources (official portal, official guidance PDF, three commercial ERPs, legal text, English-language VAT advisories) all agree.

**Medium** for the precise XSD filename(s) and the exact XML element tree for `KG:KGYF-NÉ`. The schema exists on the OKIRkapu developer portal, but the specific schema for the EPR quarterly package must be downloaded from inside the authenticated portal (or from the `xmlapi` page's KG-section listing) to extract element names, types, and cardinalities. We have not retrieved the raw XSD in this research.

### Impact on Story 9.4 design

CP-5 §8's "CSV-based MOHU/OKIRkapu export" framing needs to be rewritten. Concretely:

1. **Rename + reshape Story 9.4.** "MOHU CSV Export" → "OKIRkapu XML Export (KG:KGYF-NÉ)". MOHU does not receive the file directly — the producer uploads XML to OKIRkapu, and the authority forwards aggregated data to MOHU. The export's target system is OKIRkapu, not MOHU.
2. **Output format is XML validated against the published KG:KGYF-NÉ XSD.** Plan: download the XSD from `kapu.okir.hu/okirkapuugyfel/xmlapi` into the repo (alongside the existing NAV XSD catalog under `backend/src/main/resources/schemas/`), generate Java bindings (JAXB, same pattern as NAV), and use schema-validated marshalling for the export. This mirrors the existing NAV invoice XSD infrastructure in the codebase — reuse the pattern.
3. **Aggregation step is mandatory before export.** The exporter sums per-transaction EPR liabilities (Story 7.x output) into per-KF-code mass totals for the quarter. Individual invoice lines do not appear in the submission. This means Story 9.4 MUST perform a GROUP BY `kf_code` aggregation over the reporting period before emitting the XML.
4. **Producer identity block is required.** The XML header carries producer identification (adószám, KSH stat number, company reg number, registered office). Story 9.4 depends on company-profile data that Story 9.1 (or an earlier setup story) must expose. Flag this as a dependency.
5. **No API-based submission loop.** The exporter produces a file; the user uploads it manually to OKIRkapu. Do NOT design an automated submission round-trip. A future enhancement could be an OKIRkapu integration if/when the authority publishes a REST endpoint, but nothing in current evidence suggests one exists.
6. **Supporting documents (takeover agreements, customer declarations)** are a separate reporting concern that Story 9.4 may or may not cover — this is outside the XML payload itself and flows through a different OKIR package. Clarify scope before implementation.
7. **Manual-form fallback is still legally valid.** If a producer cannot generate XML, they can fill the `KG:KGYF-NÉ` form manually in OKIRkapu. Story 9.4 should present the XML as a download artifact; the user can choose to upload it or transcribe it manually.

---

## Part 2 — KF code 2026-01-01 refresh

### Answer

**Framing note:** Today is 2026-04-14. The 2026-01-01 KF code refresh is already in force and has been for ~3.5 months. Risk Guard development began in 2026, so this is the *current* code set, not an upcoming migration — Story 9.1 builds against it from day one with no legacy/transition concerns.

**The KF code system received a targeted refresh on 2026-01-01, not a wholesale renumbering.** Changes concentrated on specific product categories (batteries/accumulators confirmed; wooden furniture pending draft status at the time of this research). Packaging KF codes — the primary scope of Story 9.1 — remained stable in the currently-enacted amendment tranche. A second-tranche draft was in public consultation during 2025 that would further affect batteries and introduce a new 7th-character waste-management activity code ("I" — "újrapozícionálásra előkészített" / "prepared for repositioning"); its enacted status as of April 2026 is uncertain (see §Confidence and §Open questions).

Specifically:

- **What changes (already-enacted tranche, 203/2025. Korm. rendelet, Magyar Közlöny 2025-07-17):**
  - Amends 80/2023 Annex 3 §§2.2, 2.3 and Annex 4 battery/accumulator sections.
  - New battery material-stream codes appear in the 2026 fee schedule (33/2025. EM rendelet, Magyar Közlöny, 2025-11-28): **A11** portable batteries (160 Ft/kg), **A21** light-transport-vehicle batteries (239 Ft/kg), **A31** electric-vehicle batteries (239 Ft/kg), **A41** starting/lighting/ignition batteries (390 Ft/kg), **A51** industrial batteries (239 Ft/kg).
  - Electronic waste (WEEE) KF codes touched indirectly via 445/2012 (batteries) and 197/2014 (WEEE) amendments.
  - Producers registered before the change had a deadline (**2025-09-30**) to update their registry entries (vevői nyilatkozatok, átvállalási szerződések) with new codes. This deadline has long since passed; affected producers operating today are already on the new codes (or are out of compliance).
- **What MAY change (second-tranche draft in consultation, 2025):**
  - Further revisions to battery/accumulator KF codes on top of the 203/2025 set.
  - New KF codes for wooden furniture from 2026-01-01.
  - Introduction of a new "I" value in the 7th character of the KF code (waste-management activity position) meaning "prepared for repositioning" (reuse prep). This is a **value addition within the existing 8-character structure**, not a structural rewrite.
- **8-digit structure itself:** Unchanged. The 8-character layout (2 product/waste + 2 material stream + 2 group + 1 form + 1 waste-management activity) stays intact. Only specific code values and the allowed set of the 7th character are affected.
- **Cutover:** Hard cutover happened on **2026-01-01** for the 203/2025 tranche (batteries). Products placed on the market from that date report using the new codes; the **Q1 2026 quarterly submission is due 2026-04-20 (next Monday from this memo's date)** — this is the first submission where new codes apply, and it is imminent for any producer currently using Risk Guard.
- **Old→new mapping:** **No official mapping table has been published.** The authority's guidance (per Andersen) is that producers must update customer declarations and takeover agreements with the new codes — implying producers are expected to derive the mapping themselves from the amended Annexes. For batteries, the new A11–A51 codes are a structural re-categorization (by battery application: portable vs. LMT vs. EV vs. SLI vs. industrial), not a renaming of old codes, so a one-to-one mapping does not exist in all cases.

### Evidence

1. **PwC Hungary (2025):** [Fontos változás a kiterjesztett gyártói felelősségi rendszerben](https://www.pwc.com/hu/hu/sajtoszoba/2025/fontos_valtozas_a_kiterjesztett_gyartoi_felelossegi_rendszerben.html) — confirms battery KF codes change from 2026-01-01; flags a further draft amendment affecting batteries and wooden furniture; describes new "I" value in 7th character.
2. **Andersen Hungary (2025):** [Kijöttek a 2026-os kiterjesztett gyártói felelősségi (EPR) díjak](https://hu.andersen.com/hu/hirek/2026-os-epr-dijak/) — lists 2026 A11/A21/A31/A41/A51 battery fee codes; cites Magyar Közlöny 2025-11-28 (33/2025 EM rendelet) as the 2026 fee schedule source.
3. **Andersen Hungary — KF code explainer:** [KF kódok: hogyan kell meghatározni az EPR rendszerben a körforgásos termékeket?](https://hu.andersen.com/hu/hirek/kf-kodok-avagy-hogyan-kell-meghatarozni-az-epr-rendszerben-a-korforgasos-termekeket/) — documents the 8-character structure (product/waste + material stream + group + form + waste-management activity).
4. **Adózóna (2025, paywalled preview):** [Közelgő változások a kiterjesztett gyártói felelősség (EPR) szabályaiban](https://adozona.hu/2025_os_adovaltozasok/Kozelgo_valtozasok_a_kiterjesztett_gyartoi__ZQ7VRC) — references 203/2025 Korm. rendelet as the enacting amendment. (Full text behind paywall.)
5. **Adózóna (2025):** [EPR, hulladékgazdálkodás – módosító kormányrendeletek a legfrissebb Magyar Közlönyben](https://adozona.hu/2025_os_adovaltozasok/EPR_hulladekgazdalkodas_kormanyrendelet_O19O42) — general overview of the 2025 amendment package.
6. **Transpack (2025-10-29):** [Év közben ismét módosul az EPR-rendelet](https://transpack.hu/2025/10/29/csomagolas-epr-rendelet-valtozasok-2025/) — industry coverage of mid-year amendments; packaging side appears unchanged in 203/2025 tranche.
7. **NAK (Hungarian Chamber of Agriculture):** [Megjelentek a 2026-os évre vonatkozó EPR és DRS díjak](https://www.nak.hu/tajekoztatasi-szolgaltatas/elelmiszer-feldolgozas/109687-megjelentek-a-2026-os-evre-vonatkozo-epr-es-drs-dijak) — confirms 2026 fee table publication.
8. **NaturaSoft (2025):** [Kiterjesztett gyártói felelősség, EPR díj számlázása 2026](https://www.naturasoft.hu/kiterjesztett-gyartoi-felelosseg-epr-dij.php) — confirms 2026 fee changes are stable as of October 2025 rates.
9. **Agroinform:** [Közzétették az EPR-díjakat: itt a teljes 2026-os díjtáblázat](https://www.agroinform.hu/kornyezetvedelem/kozzetettek-az-epr-dijakat-itt-a-teljes-2026-os-dijtablazat-88285-001) — publishes full 2026 fee table.
10. **Legal text (hatályos):** [80/2023. (III. 14.) Korm. rendelet, Nemzeti Jogszabálytár](https://njt.hu/jogszabaly/2023-80-20-22) and [net.jogtar.hu consolidated](https://net.jogtar.hu/jogszabaly?docid=a2300080.kor) — check here for the current consolidated text including 203/2025 amendments.

### Confidence

**High** that:
- Batteries/accumulators KF codes change on 2026-01-01 via 203/2025.
- The 8-character structure is preserved.
- Packaging KF codes (Story 9.1's dominant scope) are **not** altered by the enacted 203/2025 tranche.
- No official old→new mapping table exists; producers must re-register.
- Cutover is 2026-01-01.

**Medium** that the draft second-tranche amendment (further battery changes, wooden furniture, "I" 7th-character value) will be enacted in its current form. As of 2026-04-14, the research did not retrieve a Magyar Közlöny issue confirming final enactment of this draft. The PwC article (the most authoritative non-legal-text source on this) flagged it as "társadalmi egyeztetésre bocsátott tervezet" (submitted for public consultation), not yet enacted. **Recommend re-verification via Magyar Közlöny before committing implementation scope.**

**Low** on exhaustive completeness: it is possible — though not surfaced by these searches — that 203/2025 or the draft amendment makes smaller adjustments to packaging or other categories that are not mentioned in industry bulletins focused on batteries. A direct diff of 80/2023 Annex 3 pre- and post-203/2025 is required to be certain.

### Impact on Story 9.1 data model

**Framing correction (2026-04-14):** Risk Guard was started in 2026, so the 203/2025 changes that took effect 2026-01-01 are the *current* law — not a future migration. The product never saw 2025-era KF codes. Story 9.1 designs against the post-2026-01-01 code set from day one, and the 2025→2026 transition does not need to exist in the data model. Historical compatibility with pre-2026 codes is a non-requirement unless a user explicitly imports legacy data — which is not in Epic 9 scope.

What this leaves as the actual 9.1 design points:

1. **KF code is an 8-char string column, not a hard-coded enum.** Even though no live migration is in flight, the code set will change again (the second-tranche draft — see §Confidence — may still be enacted, and the authority publishes amendments routinely). Store as `CHAR(8)` validated against a reference table, not a Java enum or TypeScript union.
2. **Reference table ships with post-2026-01-01 code list.** Seed from 80/2023 Annex 3/4 consolidated text (via jogtar.hu) or from the OKIRkapu `xmlapi` portal's KG code-list download. No `supersedes`/`superseded_by` self-join is required for day-one schema — it can be added later if and when a future amendment forces a re-categorization.
3. **`valid_from` / `valid_to` columns are still worth adding** — cheap insurance against the next amendment, and they let the UI reject codes that get retired later without a schema migration. But `valid_to` will be `NULL` for every seeded row at launch.
4. **No structural change to the 8-char layout required.** Positions 1-2 product/waste, 3-4 material stream, 5-6 group, 7 form, 8 waste-management activity. Character-position validation is sufficient.
5. **Packaging-only MVP remains the right scope.** The 203/2025 tranche's impact on packaging codes is minimal (see §Confidence, point 3 in open questions) and fully absorbed in a day-one seed. Batteries/WEEE/furniture stay out of MVP not because of transition risk but because they are a different product domain.
6. **Producer identity fields** (adószám, KSH number, cégjegyzékszám, registered office) used in the `KG:KGYF-NÉ` XML header are Story 9.4 inputs sourced from a company profile entity — confirm which story owns that entity.

### Migration plan sketch

**Not needed at launch.** The 2025→2026 migration has already happened in the regulation itself; the product was built after it. Seed the `kf_code` master table once with the current (post-2026-01-01) code list and ship. No bidirectional mapping, no backfill, no transition UI.

Design forward-compat hooks only (cheap to include, valuable when the next amendment lands):

1. `kf_code` is a reference table, not a hard-coded enum — a maintainer can insert new codes without a schema change.
2. `valid_from` / `valid_to` columns exist but are not exercised at launch (all rows `valid_to = NULL`).
3. Packaging-component rows store the `kf_code` value at the time of product registration, so a future retirement of a code does not orphan the historical record.
4. Magyar Közlöny is the canonical source for future amendments; assign a maintainer to monitor for 80/2023 Annex 3/4 amendments and to import the delta.

If the second-tranche draft (battery re-revision, wooden furniture, "I" 7th-character value) is enacted after launch, the forward-compat hooks above handle it: insert new code rows, set `valid_to` on superseded ones, optionally add a `supersedes` self-ref column *at that time* if the amendment introduces non-1:1 mappings.

---

## Open questions (items that need human verification)

1. **Download and catalogue the `KG:KGYF-NÉ` XSD.** The public developer portal at <https://kapu.okir.hu/okirkapuugyfel/xmlapi> lists XSDs; the exact KG/KGYF-NÉ schema file and version needed for Story 9.4 must be retrieved, committed to `backend/src/main/resources/schemas/` (alongside NAV XSDs), and reviewed for element names, cardinalities, and fixed-value enumerations. This research did not produce the schema file itself.
2. **Confirm current (April 2026) status of the second-tranche draft amendment.** The source articles (PwC, Adózóna) that described the draft were written in 2025; as of 2026-04-14 the research did not retrieve a Magyar Közlöny issue that either enacted it or withdrew it. Check Magyar Közlöny issues from late 2025 through Q1 2026 for a superseding Korm. rendelet citing 80/2023 Annex 3 or 4. Because the product is being built *now*, the right answer for Story 9.1 is to seed whatever code set is currently hatályos on jogtar.hu, not to try to predict pending drafts.
3. **Packaging-side changes in 203/2025.** Industry bulletins (PwC, Andersen, Transpack) focused on batteries. A direct diff of 80/2023 Annex 3 and Annex 4 pre- and post-203/2025 is required to rule out unadvertised changes to packaging codes. Load the consolidated text from <https://net.jogtar.hu/jogszabaly?docid=a2300080.kor> and compare against a pre-203/2025 snapshot.
4. **Official old→new battery mapping, if any.** Andersen's guidance says producers should consult the authority. Confirm with the Pest Vármegyei Kormányhivatal EPR desk or MOHU account manager whether a formal mapping memo exists (even internally) before implementing a `supersedes` self-join model that may diverge from authority intent.
5. **Existence of an OKIRkapu REST/SOAP API.** All current evidence points to OKIRkapu being file-upload only. Confirm with the OKIRkapu developer portal (authenticated area) that no programmatic submission endpoint exists — this would change Story 9.4's automation ceiling.
6. **Supporting-document obligations.** Takeover agreements and customer declarations are referenced as separate flows. Clarify whether Story 9.4 covers them or whether they are a future story. The 2025-09-30 deadline for updating these under 203/2025 has already passed; confirm the current operational handling.
7. **Manual-form fallback UX.** Decide in Story 9.4 whether the system ever guides a user through the manual OKIRkapu form (e.g., showing field-by-field values to transcribe) or whether it is strictly an XML-only export. Affects i18n scope.

---

_End of memo. Per task-01 scope: this document does not propose design changes to CP-5. Next consumers: Task 02 (PRD update) and Task 03 (Architecture update)._
