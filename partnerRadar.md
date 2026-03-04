# PartnerRadar – Cég Screening és EPR Compliance Platform

## 1. Projekt kritériumok

| Kritérium | Elvárás | PartnerRadar válasz |
|---|---|---|
| Fejlesztési idő | ~1 hónap (MVP) | ✅ Hónap 1: Cég Screening, Hónap 2: EPR modul |
| Maintenance | <20 óra/hét | ✅ Scraper karbantartás + support, becsült ~10 óra/hét |
| Bevételi cél | ~1.5M Ft/hó | ✅ ~100 fizető ügyfél vegyes csomagokkal |
| Csapat | Szóló fejlesztő | ✅ Egy full-stack dev elegendő (Next.js + Supabase) |
| Domain tudás | Nincs expert, csak publikus források + AI | ✅ Publikus adatforrások, AI a domain gap kitöltésére |
| Piac | Csak magyar | ✅ Magyar cégjegyzék, NAV, EPR szabályozás |

**Miért pont a PartnerRadar?**

- **Publikus adatforrások** – nem kell adatot vásárolni vagy domain expertet bevonni
- **AI összefoglalók** – wow faktor és valódi differenciáló az Optenhez képest
- **Recurring model** – a monitoring folyamatos előfizetést indokol (nem egyszeri lekérdezés)
- **EPR modul** – egyedi upsell lehetőség, nincs magyar SaaS versenytárs ezen a területen
- **Alacsony marginal cost** – scraping + LLM API hívások, nincs drága adatbeszerzés

---

## 2. Termék áttekintés

> *"Tudd meg 30 másodperc alatt, hogy a partnered megbízható-e."*

### Célcsoport

Magyar KKV-k (5-50 fős cégek):
- **Cégvezetők** – gyors döntés új partnerről
- **Beszerzők** – beszállító ellenőrzés szerződéskötés előtt
- **Pénzügyi vezetők** – vevők fizetőképességének monitorozása
- **Könyvelők** – ügyfeleik partnereinek ellenőrzése

### Két modul, egy termék

| | Modul 1: Cég Screening | Modul 2: EPR Compliance |
|---|---|---|
| **Mikor** | 1. hónap (MVP) | 2. hónap (upsell) |
| **Fő érték** | Partner megbízhatóság ellenőrzés | EPR kötelezettség kezelés |
| **Adatforrás** | Publikus cégjegyzékek, NAV | MOHU, jogszabályok |
| **Versenytárs** | Opten (drága) | Nincs (tanácsadók manuálisan) |

### Pozicionálás

**"Opten lite"** – az Opten funkcióinak 20%-a, az árának 10%-áért. Nem a nagyvállalati piacot célozzuk, hanem az 5-50 fős KKV-kat, akiknek az Opten túl drága és túl bonyolult.

---

## 3. Modul 1: Cég Screening

### MVP funkciók (1. hónap)

- 🔍 **Céglekérdezés** – adószám vagy cégnév alapján azonnali "partner report"
- 📋 **Cégjegyzék adatok** – státusz, alapítás dátuma, jegyzett tőke, tulajdonosok, képviselők
- 💰 **NAV adósságlista check** – van-e adótartozása a cégnek
- ⚖️ **Felszámolás/csődeljárás check** – fut-e eljárás a cég ellen
- 🤖 **AI kockázati összefoglaló** – magyar nyelven, érthető szöveg (nem nyers adatok)
- 📂 **Partner lista** – saját partnerek hozzáadása, kategorizálás (vevő/beszállító/egyéb)
- 🔔 **Monitoring** – heti/havi automatikus ellenőrzés, email alert ha változás történik

### v2 funkciók (későbbi fejlesztés)

- 📊 Pénzügyi mutatók (éves beszámolók feldolgozása, ha elérhető)
- 🕸️ Cégháló vizualizáció (tulajdonosi kapcsolatok gráfon)
- 📄 PDF export a partner reportból
- 📥 Bulk import (CSV-ből partner lista feltöltés)
- 🔌 API hozzáférés (B2B integráció más rendszerekhez)

### Adatforrások

| Forrás | URL | Adat típus | Megjegyzés |
|---|---|---|---|
| e-cégjegyzék | https://e-cegjegyzek.hu/ | Cégjegyzék adatok, státusz | Fő adatforrás, scraping |
| NAV adósságlista | https://nav.gov.hu/adossaglista | Adótartozás | Havi frissítés, letölthető lista |
| Céginformációs szolgálat | https://www.e-cegjegyzek.hu/ | Alapadatok | Kiegészítő forrás |
| Cégközlöny | https://cegkozlony.hu/ | Csőd/felszámolás | Heti scraping |

⚠️ **Scraping kockázat:** robots.txt és ToS figyelembe vétele szükséges. Rate limiting és cache-elés alkalmazása.
## 4. Modul 2: EPR Compliance

### Kontextus

Az **EPR (kiterjesztett gyártói felelősség)** 2024-től kötelező Magyarországon. A gyártók és importőrök felelősek a termékeik (különösen csomagolásuk) életciklus végi kezeléséért. Sok KKV nem tudja, hogy érintett-e, és ha igen, mit kell tennie. Jelenleg könyvelők és tanácsadók nyújtják ezt a szolgáltatást manuálisan (50-150k Ft/alkalom).

### MVP funkciók (2. hónap – könnyű megvalósítás)

- ✅ **Kötelezettség-ellenőrző kérdőív** – döntési fa alapú
    - "Gyártasz vagy importálsz terméket?" → "Igen"
    - "A termék csomagolt?" → "Igen"
    - → "EPR kötelezett vagy. Íme a teendőid:"
- ✅ **Határidő emlékeztetők** – regisztrációs, bevallási, díjfizetési határidők naptárban
- ✅ **Termékdíj kalkulátor** – lookup tábla + számítás (anyagtípus x tömeg x díjtétel)
- ✅ **AI összefoglaló** – személyre szabott kötelezettség-összefoglaló a kérdőív válaszai alapján

### v2 funkciók (nehezebb – későbbi fejlesztés)

- 📄 Dokumentum sablon generátor (bevallás, regisztrációs űrlapok)
- 📰 Jogszabály-változás figyelő (EPR szabályozás monitoring, alert ha változik)
- 🤖 Automatikus bevallás előkészítés (adatok összegyűjtése, űrlap kitöltés)
- 📷 OCR: csomagolási címkék/számlák feldolgozása az EPR adatok kinyeréséhez

### MOHU API

A MOHU (Magyar Hulladékgazdálkodási Ügynökség) API-ja szükséges a díjtétel adatokhoz. Regisztráció szükséges – még nem történt meg. Amíg nincs API hozzáférés, a díjtételek manuálisan kerülnek be a rendszerbe a publikusan elérhető jogszabályi táblázatokból.

### ⚠️ Jogi disclaimer

A termékdíj kalkulátor és a kötelezettség-ellenőrző **tájékoztató jellegű**. Nem helyettesíti a szakértői tanácsadást. A felhasználónak ezt elfogadási feltételként kell tudomásul vennie.

---

## 5. Versenytársak

### Magyar versenytársak

| Név | Ár | Erősségek | Gyengeségek |
|---|---|---|---|
| **Opten** (opten.hu) | ~50-100k Ft/hó | Teljes magyar céginformáció, cégháló, monitoring, pénzügyi adatok, API | Drága KKV-knak, bonyolult UI, enterprise fókusz |
| **Bisnode/D&B** | Enterprise ár | Nemzetközi adatbázis, credit scoring, kockázatértékelés | Nagyon drága, nem KKV-barát, túl komplex |

### Nemzetközi versenytársak

| Név | Ár | Erősségek | Gyengeségek |
|---|---|---|---|
| **Creditsafe** (creditsafe.com) | Custom (quote) | 430M+ cég, credit scoring, monitoring, API | Drága, enterprise, nincs magyar fókusz |
| **Dun & Bradstreet** (dnb.com) | Custom (enterprise) | DUNS szám, credit reports, supply chain risk | Nagyon drága, komplex |
| **CompanyWatch** (companywatch.net) | Custom | Financial health scoring, H-Score, monitoring | UK specifikus, nincs magyar adat |

### EPR compliance terület

🎯 **Nincs igazi magyar SaaS versenytárs.** A szolgáltatást jelenleg könyvelők és környezetvédelmi tanácsadók nyújtják manuálisan, alkalmanként 50-150k Ft-ért. Ez a PartnerRadar egyedi differenciálója.

### PartnerRadar pozícionálás

| Szempont | Opten | PartnerRadar |
|---|---|---|
| Ár | 50-100k+ Ft/hó | 9.900-29.900 Ft/hó |
| Célcsoport | Nagyvállalat, enterprise | KKV (5-50 fő) |
| UI komplexitás | Bonyolult, sok funkció | Egyszerű, lényegre törő |
| AI összefoglaló | Nincs | ✅ Magyar nyelvű, érthető |
| EPR modul | Nincs | ✅ Egyedi funkció |
| Onboarding | Sales call szükséges | Self-service, azonnali |

---

## 6. Árazás

### Csomagok

| | 🟢 Alap | 🔵 Pro | 🟣 Pro+EPR |
|---|---|---|---|
| **Ár** | 9.900 Ft/hó | 19.900 Ft/hó | 29.900 Ft/hó |
| Partner limit | Max 50 | Korlátlan | Korlátlan |
| Céglekérdezés | ✅ | ✅ | ✅ |
| NAV adósságlista check | ✅ | ✅ | ✅ |
| Csőd/felszámolás check | ✅ | ✅ | ✅ |
| Monitoring gyakoriság | Heti | Napi | Napi |
| Email alertek | ✅ | ✅ | ✅ |
| AI kockázati összefoglaló | ❌ | ✅ | ✅ |
| PDF export | ❌ | ✅ | ✅ |
| Prioritásos support | ❌ | ✅ | ✅ |
| EPR kötelezettség-ellenőrző | ❌ | ❌ | ✅ |
| Termékdíj kalkulátor | ❌ | ❌ | ✅ |
| EPR határidő emlékeztetők | ❌ | ❌ | ✅ |
| AI EPR összefoglaló | ❌ | ❌ | ✅ |

### 📊 Bevételi kalkuláció (cél: 1.5M Ft/hó)

| Szcenárió | Mix | Bevétel |
|---|---|---|
| A – Csak Alap | 150 x Alap | 1.485M Ft |
| B – Alap + Pro | 50 x Alap + 50 x Pro | 1.490M Ft |
| **C – Vegyes (legvalószínűbb)** | **30 x Alap + 30 x Pro + 20 x Pro+EPR** | **1.492M Ft** |

🎯 **Cél: ~100 fizető ügyfél** vegyes csomagokkal eléri az 1.5M Ft/hó bevételt.

## 7. MVP scope és ütemterv

### 1. hónap: Core Screening modul

| Hét | Fókusz | Részletek |
|---|---|---|
| 1. hét | Projekt setup | Next.js + Supabase init, auth, adatbázis séma, UI shell (layout, navigáció) |
| 2. hét | Adatforrás integrációk | e-cégjegyzék scraper, NAV adósságlista parser, Cégközlöny scraper |
| 3. hét | Partner kezelés | Partner lista CRUD, monitoring engine (scheduler), email alert rendszer |
| 4. hét | AI + Launch | AI kockázati összefoglaló, dashboard, landing page, beta launch |

### 2. hónap: EPR modul

| Hét | Fókusz | Részletek |
|---|---|---|
| 1. hét | EPR kérdőív | Döntési fa implementáció, kötelezettség-ellenőrző UI |
| 2. hét | Kalkulátor | Termékdíj lookup tábla, kalkulátor logika és UI |
| 3. hét | Emlékeztetők + AI | Határidő emlékeztető rendszer, AI EPR összefoglaló |
| 4. hét | Launch | Árazási oldal frissítés, Pro+EPR csomag aktiválás, marketing |

### User story-k (1. hónap MVP)

**Auth & Onboarding:**
- Felhasználóként regisztrálni és bejelentkezni akarok email + jelszóval
- Felhasználóként el akarom indítani a 14 napos ingyenes próbaidőszakot

**Céglekérdezés:**
- Felhasználóként céget akarok keresni adószám vagy cégnév alapján
- Felhasználóként látni akarom a cég alapadatait (státusz, tőke, tulajdonosok, képviselők)
- Felhasználóként látni akarom, van-e a cégnek NAV adótartozása
- Felhasználóként látni akarom, van-e csőd/felszámolási eljárás a cég ellen
- Felhasználóként AI összefoglalót akarok kapni a cég kockázatairól magyarul

**Partner kezelés:**
- Felhasználóként hozzá akarom adni a céget a partner listámhoz (vevő/beszállító/egyéb)
- Felhasználóként látni akarom az összes partnerem listáját szűrőkkel
- Felhasználóként törölni akarok partnert a listámról

**Monitoring & Alertek:**
- Felhasználóként automatikus értesítést akarok kapni emailben, ha egy partnerem adataiban változás történik
- Felhasználóként dashboard-on akarom látni a partnereim összesített kockázati képét
- Felhasználóként be akarom állítani a monitoring gyakoriságát (heti/napi)

---

## 8. Előnyök, hátrányok, kockázatok

### ✅ Előnyök

- **Valós fájdalompont** – egy bedőlt partner milliókba kerülhet egy KKV-nak
- **Publikus adatforrások** – nem kell adatot vásárolni, nincs licensz költség
- **AI összefoglaló** – wow faktor és valódi differenciáló (nem nyers adatok, hanem érthető szöveg)
- **EPR modul egyedi** – nincs magyar SaaS versenytárs ezen a területen
- **Recurring revenue** – a monitoring folyamatos előfizetést indokol
- **Alacsony marginal cost** – scraping + LLM API, nincs drága infrastruktúra
- **Self-service** – nincs sales csapat szükség, az ügyfél magának regisztrál

### ❌ Hátrányok

- **Scraping törékeny** – weboldal változás = azonnali karbantartás szükséges
- **Adatfrissesség** – napi scraping szükséges, de a források nem mindig frissülnek naponta
- **Nincs domain expertise** – jogi és pénzügyi területen AI-ra és publikus forrásokra támaszkodunk
- **Opten brand erős** – nehéz egy ismert brand ellen pozicionálni, még ha olcsóbbak is vagyunk
- **EPR szabályozás változhat** – jogszabály módosítás = kalkulátor és döntési fa frissítés

### ⚠️ Kockázatok és mitigációk

| Kockázat | Súlyosság | Valószínűség | Mitigáció |
|---|---|---|---|
| Scraping jogi kérdések | Közepes | Közepes | robots.txt tiszteletben tartása, rate limiting, csak publikus adatok, ToS ellenőrzés |
| Adatforrás weboldal változás | Magas | Magas | Moduláris scraper architektúra, scraper health monitoring, gyors javítási protokoll |
| EPR kalkulátor pontatlansága | Közepes | Közepes | Jogi disclaimer, rendszeres díjtétel frissítés, felhasználói visszajelzés csatorna |
| Opten KKV csomag bevezetése | Alacsony | Alacsony | Gyorsabb iteráció, EPR modul mint differenciáló, közösség építés |
| LLM API költségek elszállnak | Alacsony | Alacsony | Agresszív cache-elés, rate limiting per user, költség monitoring és alertek |
| Alacsony konverziós ráta | Közepes | Közepes | 14 napos trial, content marketing, SEO, referral program |

---

## 9. Technikai architektúra vázlat

### Tech stack

| Réteg | Technológia | Indoklás |
|---|---|---|
| Frontend | Next.js 14 (App Router) | Gyors fejlesztés, SSR, jó DX |
| Backend | Next.js API Routes + Supabase | Egyszerű, nincs külön backend szükség |
| Adatbázis | Supabase (PostgreSQL) | Ingyenes tier, RLS, realtime |
| Auth | Supabase Auth | Beépített, email + social login |
| AI | OpenAI GPT-4o | Magyar nyelv támogatás, strukturált output |
| Email | Resend vagy SendGrid | Tranzakciós emailek, alertek |
| Hosting | Vercel + Supabase | Ingyenes/olcsó induláshoz |
| Scraping | Cheerio / Puppeteer | Node.js natív, jól integrálható |
| Scheduling | Vercel Cron / Supabase Edge Functions | Monitoring scheduler |
| Monitoring | Sentry | Error tracking, performance |
| Fizetés | Stripe vagy Barion | Stripe nemzetközi, Barion magyar |

### Architektúra diagram

```
┌─────────────┐     ┌──────────────────────────┐
│ Felhasználó │────▶│ Next.js Frontend (Vercel) │
└─────────────┘     └──────────┬───────────────┘
                               │
                    ┌──────────▼───────────────┐
                    │   Next.js API Routes      │
                    └──┬───────┬───────┬───────┘
                       │       │       │
              ┌────────▼──┐ ┌─▼─────┐ ┌▼──────────────┐
              │ Supabase  │ │  AI   │ │ Scraper Engine │
              │ DB + Auth │ │Service│ │ (Cheerio/      │
              │(PostgreSQL)│ │(GPT-4o)│ │  Puppeteer)    │
              └───────────┘ └───────┘ └───────┬────────┘
                                              │
                                   ┌──────────▼──────────┐
                                   │ Külső adatforrások   │
                                   │ - e-cégjegyzék       │
                                   │ - NAV adósságlista   │
                                   │ - Cégközlöny         │
                                   │ - MOHU (EPR, v2)     │
                                   └─────────────────────┘
                    ┌─────────────────────┐
                    │ Email Service       │
                    │ (Resend / SendGrid) │
                    └─────────────────────┘
```

### Adatforrás integrációk

| Adatforrás | Módszer | Frissítési gyakoriság | Prioritás |
|---|---|---|---|
| e-cégjegyzék | Web scraping (Cheerio) | Napi | MVP |
| NAV adósságlista | Web scraping / fájl letöltés | Napi (lista havonta frissül) | MVP |
| Cégközlöny (felszámolás) | Web scraping | Heti | MVP |
| MOHU (EPR díjtételek) | API (ha elérhető) / manuális | Negyedéves | 2. hónap |

### Adatbázis séma (fő táblák)

```
users          - id, email, plan, trial_ends_at, created_at
companies      - id, tax_number, name, status, capital, founded_at, raw_data, last_scraped_at
partners       - id, user_id, company_id, category, notes, created_at
monitoring_log - id, partner_id, field_changed, old_value, new_value, detected_at
alerts         - id, user_id, partner_id, type, message, sent_at, read_at
epr_responses  - id, user_id, answers_json, obligation_summary, created_at
epr_calculations - id, user_id, material_type, weight_kg, fee_rate, total_fee, created_at
```
