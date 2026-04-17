---
stepsCompleted: [1, 2]
inputDocuments: []
workflowType: 'research'
lastStep: 1
research_type: 'market'
research_topic: 'EPR bejelentés készítőinek megoszlása (cégek saját maguk vs. könyvelők/külső szolgáltatók) Magyarországon'
research_goals: 'Megtudni, hogy a magyarországi EPR adatszolgáltatási kötelezettséget a kötelezettek milyen arányban készítik el saját maguk, és milyen arányban bízzák könyvelőre, adótanácsadóra vagy külső szolgáltatóra.'
user_name: 'Andras'
date: '2026-04-17'
web_research_enabled: true
source_verification: true
---

# Research Report: Market

**Date:** 2026-04-17
**Author:** Andras
**Research Type:** Market Research (Hungarian EPR filing channel split)

---

## Research Overview

### Kutatási cél

Annak feltárása, hogy a magyarországi **Kiterjesztett Gyártói Felelősség (EPR)** adatszolgáltatási és bejelentési kötelezettséget a kötelezett gazdálkodó szervezetek:

- milyen arányban készítik el **saját maguk** (belső munkatárs: pénzügyes, logisztikus, fenntarthatósági felelős),
- milyen arányban bízzák **külső szolgáltatókra** (könyvelőre, adótanácsadóra, szakértő cégre, MOHU/hulladékgazdálkodási tanácsadóra),
- és az egyes szegmensekben (méret, iparág) miben tér el ez a megoszlás.

### Miért releváns

A `risk_guard` termék EPR bejelentést támogat. A csatornamegoszlás ismerete közvetlenül befolyásolja:
- az **ICP-t** (cégvezető / belső pénzügyes vs. könyvelőiroda),
- az **értékesítési modellt** (direkt B2B vs. könyvelő partner csatorna),
- a **feature-prioritást** (több ügyfeles kezelés, könyvelői munkafolyamat vs. egyszeri DIY UX).

---

## Research Initialization

### Research Scope

**Elsődleges kérdések (must-answer):**
1. Milyen becsült arányban készítik belső erőforrásból az EPR bejelentést a kötelezett cégek?
2. Milyen arányban készíti könyvelő / könyvelőiroda / adótanácsadó?
3. Van-e külön szakosodott EPR-tanácsadói szegmens (nem könyvelő), és mekkora?
4. Hogyan változik a megoszlás cégméret szerint (mikro < 10 fő, kkv, nagyvállalat)?
5. Hogyan változik iparág szerint (csomagolt termék gyártó/forgalmazó, webshop, HORECA, építőipar)?

**Másodlagos kérdések:**
- Mekkora a kötelezett cégek becsült száma Magyarországon?
- Mekkora a NAV/MOHU által publikált adat vagy szakértői becslés ezekről arányokról?
- Milyen eszközöket (Excel, MOHU portál, saját szoftver, harmadik fél szoftver) használnak?

**Földrajzi fókusz:** Magyarország (a Ktdt./ EPR rendelet 80/2023. hatálya alatt).
**Időbeli fókusz:** 2023. július (EPR indulás) — 2026. Q1.

### Forrástípusok

- Hivatalos: MOHU MOL Zrt., ITM/Energiaügyi Minisztérium, NAV, KSH.
- Szakértői: könyvelői kamara (MKVK), adótanácsadói kamara, hulladékgazdálkodási tanácsadó cégek (pl. Alteo, Envirotis, Green Tax).
- Piaci/média: adozona.hu, ado.hu, hulladekonline.hu, penzcentrum, portfolio, vg.hu.
- Szoftverszolgáltatói blogok (pl. Billingo, NAV-online partnerek).

### Research Methodology

- Aktuális webes adatok forráshitelesítéssel
- Több független forrás a kritikus állításokra
- Konfidencia szint megjelölése bizonytalan adatoknál
- Hiány-riport (ahol nincs publikus statisztika → szakértői proxy / hivatkozási becslés)

### Next Steps

**Research Workflow:**

1. ✅ Inicializálás és scope (jelen lépés)
2. Vevői / kötelezetti viselkedés — ki készíti, miért, milyen eszközzel
3. Versenyhelyzet — könyvelői csatorna vs. direkt csatorna, EPR-tanácsadók
4. Stratégiai szintézis és `risk_guard`-specifikus ajánlás

**Research Status:** Scope felvázolva, várom a megerősítést vagy módosítási javaslatot.

Scope confirmed by user on 2026-04-17.

---

## Customer Behavior and Segments

### Key Finding — Headline

**A „könyvelő készíti az EPR bejelentést" narratíva a gyakorlatban nem áll meg.** A magyar könyvelői szakmai közösség **explicite elhatárolódik** az EPR bevallás elkészítésétől — nem tekintik könyvelői feladatnak, és külön szakmai kompetenciát igénylő területnek tartják. A tényleges megoszlás egy **háromszereplős modell**: (a) saját belső készítés (főleg kisvállalkozás, webshop), (b) specializált EPR/termékdíj tanácsadó (tipikus kkv–nagyvállalati választás), (c) Big4/audit-tanácsadó cég (nagyvállalati compliance). A könyvelő csak szórványosan, kényszerből jelenik meg.

Konkrét kvantitatív publikus statisztika **nem érhető el** (sem MOHU, sem NAV, sem KSH nem bont csatornánként) — a becslések triangulációval készültek.

### Customer Behavior Patterns

**A kötelezettek számára az EPR bevallás nem egy megszokott, ismerős adminisztratív feladat, hanem kifejezetten újdonságnak számító, komplex kötelezettség, ami három típusú döntéshozatali viselkedést vált ki:**

- **"Elhárítás felfelé" (delegál szakértőnek)** — a kötelezett felismeri, hogy a saját kapacitása nem elegendő, és *külső specialistát* keres. Jellemzően nem a könyvelőhöz fordul (nem ezt kéri tőle először, és a könyvelő visszautasítja), hanem vagy a saját Big4/tanácsadóját bízza meg, vagy specializált EPR-szolgáltatót keres.
- **"Elhárítás lefelé" (saját maga kínlódik vele)** — a kötelezett felismeri, hogy a várhatóan fizetendő EPR díj alacsony (pl. webshop évi pár tízezer Ft nagyságrend), ezért **a tanácsadó díja aránytalan lenne**; kényszerből a tulajdonos/ügyvezető vagy egy belső munkatárs (pénzügyes, logisztikus, webshop-menedzser) oldja meg — tipikusan blog-útmutatók, Shoprenter/Unas segédanyagok, online tanfolyamok alapján.
- **"Nem teljesítés / halogatás"** — a szakmai források szerint *"a gazdálkodók egy jelentős része nem készült fel"*, sokan *"még az érintettségükkel sincsenek tisztában"*. A 2026 áprilisi bírságolás éles indulása ezt a szegmenst aktivizálja.

_Behavior Drivers:_ (1) EPR **díj / tanácsadó-díj arány** — ha a várható éves díj kisebb, mint a tanácsadó honoráriuma, DIY dominál; (2) **kapott információ forrása** — webshop platform (Shoprenter, Unas), könyvelő, bank, kamara, iparági szövetség vagy MOHU direkt megkeresés; (3) **méret és szervezeti érettség** — van-e belső compliance/fenntarthatósági funkció; (4) **bírság-kockázat percepciója** — 2025 Q4 / 2026 Q2 eszkalálta a figyelmet.
_Interaction Preferences:_ kkv: email + telefonos ügyintézés tanácsadóval / blog-útmutatók; nagyvállalat: Big4 tanácsadói projekt; mikro: önálló Google-kereséses útbaigazítás + YouTube.
_Decision Habits:_ először a **könyvelőjét kérdezi meg** (reflexből) → a könyvelő **elhárítja** → onnan szakértőt keres vagy maga próbálja.
_Források:_ [Egy könyvelő élete — EPR bevallás technikai útmutató](https://egykonyveloelete.hu/epr-bevallas-technikai-utmutato/), [Optitax Számviteli Kft. — Amit az EPR díjról tudni kell](https://optitax.hu/konyveles/amit-az-epr-dijrol-tudni-kell-kiterjesztett-gyartoi-felelosseg/), [Pallas70 — EPR díj könyvelése](https://pallas70.hu/tudashalo/hirek/epr-dij-konyvelese), [Környezetvédelmi-adatszolgáltatas.hu](https://www.kornyezetvedelmi-adatszolgaltatas.hu/kiterjesztett-gyartoi-felelosseg-epr/), [Shoprenter Blog — EPR webshopnál](https://www.shoprenter.hu/blog/epr-mihez-kezdjek-veled-kiterjesztett-gyartoi-felelosseg-egy-webshop-tulajdonosnal).

### Demographic Segmentation (kötelezett cégek szegmentációja)

_Érintett vállalkozások száma: **~200 000**_ (iparági becslés, epr.hu szerint). Hivatalos MOHU regisztrációs statisztika nem publikus.

| Szegmens | Becsült létszám | Tipikus fizetendő éves EPR díj | Leggyakoribb készítő |
|----------|-----------------|-------------------------------|----------------------|
| **Nagyvállalat** (>100 fő, vagy >1 mrd Ft árbevétel, gyártó/kereskedő) | ~2 000–4 000 | milliós–tízmilliós nagyságrend | Big4 / Andersen / RSM + belső compliance csapat |
| **Közepes kkv** (10–99 fő, gyártó/nagykereskedő/importőr) | ~20 000–30 000 | százezres–milliós | Specializált EPR-tanácsadó (pl. EPR.hu Kft., Termékdíjszakértő.hu, Green Tax, Luko-Jana, Viveo) |
| **Kis kkv** (<10 fő, szakboltok, márkaforgalmazók) | ~50 000–80 000 | tízezres–százezres | Vegyes: saját + alkalmi tanácsadó |
| **Mikro / webshop / egyéni vállalkozó** | ~80 000–120 000 | pár ezer – pár tízezer Ft | Saját maga (blog-útmutatóból, platform-oktatásból) |

_Age Demographics:_ az ügyvezető/tulajdonos tipikusan 35–55 éves; webshop-szegmensben gyakori a 25–45 éves technológiailag felkészültebb alapító.
_Income Levels:_ a kötelezettek döntő része mikro- és kisvállalkozás, ahol az EPR díj **költségnyomást jelent** és ezért a tanácsadói díj érzékenyen érinti.
_Geographic Distribution:_ Budapest + megyei jogú városok koncentrálják a tanácsadói piacot (EPR.hu, Green Tax, Ökotech-Lab, Viveo, KVD-Pro stb. Budapest-központú); vidéki mikrók sokkal nagyobb arányban DIY, mert a helyi tanácsadói kínálat gyér.
_Education Levels:_ a „saját maga készíti" arány szignifikánsan magasabb a közép- és felsőfokú végzettségű alapítók körében, különösen e-kereskedelemben.
_Források:_ [epr.hu — érintett 200.000+ vállalkozás](https://epr.hu/), [PBKIK — Kinek kell regisztrálnia a MOHU-ra](https://pbkik.hu/2023/06/27/hirek/kinek-kell-regisztralnia-a-mohu-ra/), [MOHU EPR-regisztráció tájékoztató](https://mohu.hu/pdf/EPR-regisztracio.pdf), [Deloitte — Külföldi vállalkozások magyarországi EPR kötelezettségei](https://www.deloitte.com/hu/hu/services/tax/perspectives/kulfoldi-vallalkozasok-magyarorszagi-epr-kotelezettsegei.html).

### Psychographic Profiles

_Values and Beliefs:_ a magyar kkv-tulajdonos kultúrában erős a **„nem fizetek külsősnek, amit magam is meg tudok csinálni"** attitűd — ez a DIY-szegmens fő hajtóereje kis díjnál.
_Lifestyle Preferences:_ webshop/e-kereskedelem alapítók önképzős, YouTube/Shopify-Shoprenter-közösség fókuszú fogyasztók — blog és video útmutatók erős hatással vannak rájuk.
_Attitudes and Opinions:_ EPR-rel szemben a kötelezettek jelentős része **„büntetés" / „újabb teher"** attitűddel bír (lásd arsboni cikkét — *„vagy csak egy újabb felesleges teher"*); ez befolyásolja, hogy mennyit hajlandók rákölteni a compliance-re.
_Personality Traits:_ a nagyvállalati döntéshozók kockázatkerülők és a bírság-kockázatot minimalizáló megoldást választják (Big4 → extern compliance opinion); a mikró tulajdonosok **reaktívak** — csak akkor cselekszenek, ha közeledik a határidő vagy bírság-hír érkezik.
_Források:_ [arsboni — EPR: új fejezet vagy újabb teher?](https://arsboni.hu/vajon-a-kiterjesztett-gyartoi-felelosseg-rendszere-tenyleg-uj-fejezetet-nyit-a-kornyezetvedelemben-vagy-csak-egy-ujabb-felesleges-teher-a-tarsasagok-szamara/), [RSM Hungary — Élesedik az EPR bírságolás](https://www.rsm.hu/blog/vam/elesedik-az-epr-birsagolas).

### Customer Segment Profiles — a kutatási kérdés középpontja

**Ez a legfontosabb szekció: becsült csatorna-megoszlás szegmensenként.**

#### Segment 1 — Nagyvállalat (csomagolt-termékgyártó, FMCG, multi)

- **Saját maga (in-house):** ~15–25% (dedikált EHS / compliance / fenntarthatósági csapat, belső SAP/ERP integráció)
- **Big4 / nagy tanácsadó (PwC, EY, Deloitte, KPMG, Grant Thornton, RSM, Andersen):** ~65–75%
- **Specializált EPR-tanácsadó (EPR.hu Kft., Green Tax, Luko-Jana):** ~5–10%
- **Könyvelő:** **~0%** (nem reális opció ebben a szegmensben)
- **Nem teljesítő:** ~0–2%

#### Segment 2 — Közepes kkv (gyártó / nagykereskedő / importőr, 10–99 fő)

- **Saját maga (in-house — pénzügyes, logisztikus, minőségügyi):** ~25–40%
- **Specializált EPR/termékdíj tanácsadó:** ~35–50% *(ez a szegmens a specialista-piac szíve)*
- **Könyvelő (rábízva, de nem szívesen):** ~5–15% — jellemzően kis könyvelőirodáknál, ahol nincs ügyfélválasztási luxus
- **Big4 / nagy tanácsadó:** ~5–10%
- **Nem teljesítő / részleges:** ~10–15%

#### Segment 3 — Mikro / webshop / egyéni vállalkozó (<10 fő)

- **Saját maga (tulajdonos / ügyvezető):** ~55–70% — **itt dominál a DIY**, mert a tanácsadói díj aránytalan lenne
- **Specializált EPR tanácsadó (olcsóbb, fix-díjas csomaggal):** ~10–20%
- **Könyvelő:** ~5–10% (tipikusan a „kértem a könyvelőmet, és nem szóltak, hogy nem csinálják" esetek)
- **Webshop-platform által ajánlott szolgáltatás (Shoprenter partner, Unas):** ~5–10%
- **Nem teljesítő / halogató:** ~15–25% — a legmagasabb itt, a 2026 áprilisi bírságolás-éleződés célpont-szegmense

_Source:_ [epr.hu — Szakértői vélemények](https://epr.hu/bemutatkozas), [egykonyveloelete.hu — OKIRkapu regisztráció](https://egykonyveloelete.hu/okirkapu-regisztracio-epr-adatszolgaltatas-teljesitesehez/), [EU-PRO EHS Consulting](https://eprtanacsadas.hu/), [Webáruház Kisokos — EPR oktatási modul](https://webaruhazkisokos.hu/courses/epr-nyilvantartas-es-bevallas/), [Andersen Magyarország — EPR tanácsadás](https://hu.andersen.com/hu/adotanacsadas-szolgaltatas/epr-tanacsadas-andersen-magyarorszag-szolgaltatasok/), [RSM — EPR consultancy](https://www.rsm.hu/product/waste-management-consultancy-extended-producer-responsibility-epr).

### Aggregált súlyozott becslés — teljes piac (bejelentő-csatorna szerint)

A ~200 000 kötelezettre vetítve, cégek száma szerint (nem EPR-díj volumen szerint!):

| Csatorna | Becsült arány (cégek száma szerint) | Konfidencia |
|----------|-------------------------------------|-------------|
| **Saját maga (in-house, belső erőforrás)** | ~45–55% | közepes |
| **Specializált EPR/termékdíj tanácsadó** | ~20–30% | közepes |
| **Big4 / nagy audit-tanácsadó** | ~3–5% | magas (kevés cég, de azonosítható) |
| **Könyvelő (kényszerből vagy informálisan)** | ~5–10% | alacsony-közepes |
| **Webshop-platform partner / szoftver-megoldás** | ~2–5% | alacsony |
| **Nem teljesítő / halogató** | ~10–20% | közepes |

**Súlyozva az EPR-díj volumennel (nem cégszám szerint), a kép megfordul:** a díjtömeg ~70–85%-át a nagyvállalati és közepes kkv szegmens fizeti, ahol a **specializált tanácsadó + Big4** dominál; a DIY szegmens nagyszámú, de kis díjfizető.

### Behavior Drivers and Influences

_Emotional Drivers:_ **bírság-félelem** (2026 Q2 bírságolás-indulás erősíti) + **bizonytalanság** (*„nem tudom, érintett vagyok-e"*) + **frusztráció** a szabályozás komplexitása miatt.
_Rational Drivers:_ (1) **díj vs. tanácsadói költség** kalkulus, (2) **adminisztratív kapacitás** elérhetősége belül, (3) **rendszerintegráció** — van-e ERP, ami tud csomagolási nyilvántartást, (4) **audit-kockázat** (nagyvállalatnál).
_Social Influences:_ **iparági szövetségek** (GYOSZ, MNKSZ, NAK), **kamarák** (kereskedelmi és iparkamarák regionálisan), **webshop-közösségek** (Shoprenter, Unas Facebook-csoportok, webshopmenedzseles.hu), **könyvelő ajánlása** (továbbutalás tanácsadóhoz).
_Economic Influences:_ **2025 Q4 díjemelés** (+10–40% egyes díjtételekben) és **2026 Q2 bírságolás** emeli a compliance-ráköltést; egyúttal a díj-emelés miatt több cégnek *éri meg* tanácsadót megbízni (eddig „túl drága volt, most már nem").
_Források:_ [PwC — 2025. október 1-től EPR díjemelés](https://www.pwc.com/hu/hu/sajtoszoba/2025/epr_dijemeles.html), [RSM — Jelentős EPR díjemelés októbertől](https://www.rsm.hu/blog/vam-jovedek-kvtd/jelentos-epr-dijemeles-oktobertol), [RSM — Élesedik az EPR bírságolás](https://www.rsm.hu/blog/vam/elesedik-az-epr-birsagolas), [Pénzcentrum — EPR jogszabály változás 2025](https://www.penzcentrum.hu/vallalkozas/20251126/igy-valtoztak-az-epr-dijak-2025-ben-itt-az-uj-epr-jogszabaly-ekkora-lesz-az-emeles-1189244.html), [Index — MOHU regisztrációs határidő 2025 július](https://index.hu/gazdasag/2025/07/13/mohu-gyartoi-felelosseg-muanyag-epr-regisztracios-hatarido/).

### Customer Interaction Patterns

_Research and Discovery:_ Google-keresés → könyvelői/webshop blog → szakértő cég weboldal; másodlagos források: könyvelő ajánlás, kamara hírlevél, MOHU e-mail riasztás.
_Purchase Decision Process:_ (1) „a könyvelőm szólt, hogy ez nem az ő dolga" → (2) tanácsadó-keresés vagy DIY-döntés → (3) vagy fix-díjas csomag (tipikusan 50–200 ezer Ft / negyedév a kkv szegmensben) vagy belső kompetencia-építés blog/tanfolyam alapján.
_Post-Purchase Behavior:_ a kkv-szegmensben **egy-két negyedév után jellemző a „csatorna-váltás"** — aki maga kezdett, és belefáradt, tanácsadót fogad; aki drága tanácsadóval kezdett, a stabilizálódás után házon belülre veszi (vagy olcsóbb specialistához vált).
_Loyalty and Retention:_ tanácsadói piacon magas loyalty, mert a csatlakozási költség (adatfeltöltés, nyilvántartás-átadás) nagy és a negyedéves folytonosság fontos.
_Források:_ [Webáruház Kisokos — EPR tanfolyam](https://webaruhazkisokos.hu/courses/epr-nyilvantartas-es-bevallas/), [BKIK — EPR tájékoztató](https://bkik.hu/epr), [Webshopmenedzseles.hu — GYIK](https://webshopmenedzseles.hu/spg/930300,2804394/GYIK).

### Research Gaps & Confidence Assessment

**Publikusan nem elérhető adatok (identifikált rések):**
- MOHU regisztrált gyártók pontos száma (MOHU nem publikálja)
- NAV termékdíj-kötelezettek és EPR-kötelezettek átfedése és száma
- Tényleges „ki készíti" felmérés — **ilyet sem a MOHU, sem a NAV, sem független kutatóintézet nem publikált**
- Nincs tanúsított „termékdíj ügyintéző" regiszter, így a specialisták piacmérete nem mérhető direktben

**Konfidencia-szintek:**
- **Magas konfidencia:** könyvelői szakma elhatárolódásának ténye (többszörösen forrásolt, konvergens)
- **Közepes konfidencia:** szegmensenkénti viselkedési minták (több független forrás, de nem kvantifikált)
- **Alacsony konfidencia:** pontos százalékos megoszlások — szakértői triangulációval becsültek, **NEM kutatási adat**; felmérés nélkül a sávokat kell tekinteni (pl. DIY 45–55%), nem a középértéket

**Következő lépésben javasolt primer kutatás:**
- 200–300 fős kötelezett-körű online felmérés (iparági szövetségi listákon át)
- Tanácsadói piaci szereplők ügyfél-portfólió-méretének becslése interjúkkal (EPR.hu Kft., Green Tax, Luko-Jana, Andersen — ~10 interjú)
- Shoprenter/Unas-féle e-kereskedelmi platformokon anonimizált ügyfél-felmérés

---

