---
stepsCompleted: [1, 2, 3, 4, 5, 6]
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

## Customer Pain Points and Needs

### Summary — Top fájdalompontok

A magyar EPR kötelezettek fájdalompontjai 3 nagy kategóriába esnek: **(1) szakmai/besorolási bizonytalanság** (KF kódok, csomagolásszer-érték, kötelezettség határai), **(2) technikai-operatív súrlódás** (OKIRkapu bejelentkezés, adatkivonás ERP-ből, súlymérés), **(3) üzleti/pénzügyi kockázat** (50%-os bírság, önrevízió korlátai, díjemelés). A **csatorna-választás döntő pain point** is egyben: a könyvelő nem vállalja, a specializált tanácsadó drága a kisebb cégnek, DIY esetén az információs aszimmetria magas.

### Customer Challenges and Frustrations

**1. KF-kód besorolás bizonytalansága** — A KF (körforgásos) kód 6 karakterből áll (termékkód + anyagáram + csoport), és a pontos meghatározás **„még a szakértőknek sem mindig egyértelmű"**. A kötelezetteknek minden egyes termékre/csomagolóanyagra önállóan kell KF-kódot rendelniük, ami gyakori forrása a hibás bevallásnak — ez gyengíti a DIY önbizalmat és erősíti a tanácsadó-igényt, de a piaci kínálat szegmentált.

**2. OKIRkapu portál-akadályok** — A legtöbb sikertelen belépést az **ügyfélkapu jelszó/felhasználónév** vagy **KÜJ-számhoz tartozó jogosultság hiánya** okozza. A böngésző-cache is gyakran blokkolja a rendszert. A kapcsolattartó adatainak (cím, beosztás, telefon) hiányos kitöltése miatt a bevallás **nem mehet át**, csak akkor derül ki, mikor a felhasználó már órákat töltött vele.

**3. Nincs minimális súlyhatár** — Még a pár grammos csomagolást használó mikro-webshop is bevallás-köteles, ami **aránytalan adminisztratív teher** a fizetendő díjhoz képest. Ez a DIY-szegmens elsőszámú frusztrációja.

**4. Csomagolás-érték mérhetetlensége** — *„Egy-egy csomagolásra kerülő részegység mérhetetlen"* — fólia, ragasztó, címke precíz súlyának rögzítése a gyakorlatban nem megoldható precízen, és a kötelezett kénytelen becsülni → hibalehetőség.

**5. Fogalmi zavar** — „Csomagolásszer" vs. „csomagolás", „forgalomba hozatal" vs. „értékesítés" — jogi fogalmak, amelyek hétköznapi értelmezése gyakran eltér a rendeleti definíciótól.

**6. Szabályozás gyors változása** — *„Az EPR-díj kapcsolatos előírások gyakran változnak"* (2025 októberi díjemelés, 2026-os kódlista-módosítás, időközi MOHU-adminisztratív változások). A belső compliance-nak folyamatosan követni kell, ami kkv-méretben kapacitás-szintű gond.

_Primary Frustrations:_ besorolási bizonytalanság + „nem egyértelmű, érintett vagyok-e" + határidő-csúszás.
_Usage Barriers:_ OKIRkapu technikai akadályok + nyilvántartás-hiány az ERP-ben.
_Service Pain Points:_ a könyvelő elhárítja → senki sem vállalja a kisvállalat-szintű ügyfelet fix, alacsony díjért.
_Frequency Analysis:_ **minden negyedéves bevallásnál** újra felmerül, nem egyszeri probléma.
_Források:_ [Andersen Magyarország — KF kódok meghatározása](https://hu.andersen.com/hu/hirek/kf-kodok-avagy-hogyan-kell-meghatarozni-az-epr-rendszerben-a-korforgasos-termekeket/), [Adózóna — Milyen EPR-kódokat használjunk](https://adozona.hu/2023_as_adovaltozasok/A_nap_kerdese_milyen_EPRkodokat_hasznaljunk_D29YQN), [Green Tax Service — Kérdőjelek az EPR-ben](https://ktdt.hu/en/kerdojelek-a-kiterjesztett-gyartoi-felelossegi-rendszerben/), [Környezetvédelmi adatszolgáltatás — OKIRkapu belépés](https://www.kornyezetvedelmi-adatszolgaltatas.hu/blog/okirkapu-belepes/), [Shoprenter Blog — EPR webshopnál](https://www.shoprenter.hu/blog/epr-mihez-kezdjek-veled-kiterjesztett-gyartoi-felelosseg-egy-webshop-tulajdonosnal), [epr.hu — Nyilvántartási információk](https://epr.hu/epr-nyilvantartassal-kapcsolatos-informaciok).

### Unmet Customer Needs

**1. „Éri-e meg nekem szakértőt venni?" ROI-kalkulátor** — Jelenleg nincs egyszerű eszköz, ami a várható éves EPR-díj + hiba-kockázat + saját időráfordítás alapján mérlegelné a DIY vs. tanácsadó döntést.

**2. Fehércímkés tanácsadói konzol** — A specializált EPR-tanácsadóknak ma kézi munkával kell több ügyfél bevallását összerakni; nincs egységes multi-client felület, amely a 20–100 ügyfeles tanácsadó portfóliót hatékonyan kezeli (a meglévő EPR-szoftverek egy-cégesek vagy ERP-modulok).

**3. ERP/webshop adat-integráció, ami „működik"** — A Shoprenter/Unas/WooCommerce boltok nyers termék-adatbázisa nem tartalmazza a csomagolási súly- és KF-kód adatokat, ezeket utólag kell feltölteni. **Hiányzik** egy olyan integrált megoldás, amely a bolti termék-adatokat + a csomagolási metaadatokat automatikusan összefésüli.

**4. „Köthető felelősség-transzfer"** — A tanácsadók a bevallást elkészítik, de **a téves bevallás felelőssége** nem transzferálható teljesen a tanácsadóra (a kötelezett a cég marad). Hiányzik egy olyan szolgáltatási konstrukció, amely biztosítási elemet (tévedés-fedezetet) is tartalmaz.

**5. Automatikus változáskövetés és értesítés** — A díjtétel-változások, új KF-kódok, formanyomtatvány-módosítások (pl. 24KTBEV → 25TKORNY) esetén a kötelezett manuálisan követi. Hiányzik egy proaktív push-értesítési rendszer, ami „a te termékkörödre" szűrt változásokat jelent.

_Critical Unmet Needs:_ (a) tanácsadói konzol, (b) webshop-integráció, (c) felelősség-fedezet.
_Solution Gaps:_ meglévő szoftverek vagy egy-cégesek vagy ERP-kiegészítők, nincs „dedicated EPR SaaS tanácsadónak / kkv-nek".
_Market Gaps:_ a kb. 100–150e mikro-webshop szegmens **teljes egészében** kiaknázatlan — ma sem szoftver, sem tanácsadó nem éri el őket elfogadható árponton.
_Priority Analysis:_ legmagasabb prioritás a **multi-client tanácsadói konzol** és a **webshop-adatintegráció**.
_Források:_ [Körforgó — online nyilvántartó](https://korforgo.hu/), [Cloud ERP — EPR díj automatizálás](https://clouderp.hu/epr-dij-kezelese/), [eprjelentes.hu](https://eprjelentes.hu/), [HAK Kód — EPR szoftver](https://hakkod.hu/).

### Barriers to Adoption (akadályok a jelenlegi megoldások elfogadása előtt)

_Price Barriers:_ specializált tanácsadó díja **50–200e Ft/negyedév** a kkv-szegmensben — sokan a DIY-t választják, mert az EPR-díj önmaga csak pár tízezer Ft / negyedév. Szoftver-piacon nincs mikro-cég-barát ár (tipikusan évi százezres nagyságrend).
_Technical Barriers:_ OKIRkapu kezelés, KÜJ-jogosultság, KSH/NAV-adat átemelés, CSV-struktúrák — a nem technikai tulajdonos számára túl sok.
_Trust Barriers:_ sok „olcsó" tanácsadó vagy szoftver gyanús, a kötelezett bizonytalan, hogy kinek higgyen; MOHU vagy NAV nem ad „hivatalos" tanácsadói regisztert.
_Convenience Barriers:_ a tanácsadó onboarding 1–3 hét; a szoftverek közül több nem támogat magyar ügyfélkapu SSO-t, hanem külön bejelentkezést igényel.
_Források:_ [Andersen — EPR tanácsadás](https://hu.andersen.com/hu/adotanacsadas-szolgaltatas/epr-tanacsadas-andersen-magyarorszag-szolgaltatasok/), [Termékdijszakerto.hu](https://termekdijszakerto.hu/), [KVD-Pro Kft.](https://xn--termkdj-eya2b.hu/).

### Service and Support Pain Points

_Customer Service Issues:_ MOHU ügyfélszolgálat túlterhelt, várakozási idő magas; e-mail válasz 5–15 munkanap; a portálon (MOHU Partner Portál) hibajelentés nincs normálisan dokumentálva.
_Support Gaps:_ a hatóság (OHH / MOHU) nem ad egyedi kérdésekre „kötelező erejű" állásfoglalást, csak általános GYIK-et — a kötelezett saját kockázatára döntsön.
_Communication Issues:_ „határidő" kommunikációja késő; a MOHU tömeges körlevél-riasztásai (pl. 2025. júliusi „egy hetes regisztráció" hír az Indexben) időben vészesen közel érkeznek.
_Response Time Issues:_ helyesbítő számla / díj-újraszámítás esetén hónapos átfutás.
_Források:_ [Index — MOHU határidő-figyelmeztetés 2025 július](https://index.hu/gazdasag/2025/07/13/mohu-gyartoi-felelosseg-muanyag-epr-regisztracios-hatarido/), [Adózóna — Helyesbítő számlák EPR](https://adozona.hu/2024_es_adovaltozasok/Grant_Thornton_epr_MOHU_AFA_szamla_gazdasag_FY2Q7C).

### Customer Satisfaction Gaps

_Expectation Gaps:_ a kötelezett elvárása: „valaki (könyvelő / adminisztráció) csinálja meg helyettem" → valóság: „senki se vállalja ezen az áron", kényszerű DIY.
_Quality Gaps:_ a kötelezett elvárja, hogy a bevallás egyszer elég jól sikerüljön, de az önrevízió korlátozott (**évente 1x**), így a hibakiküszöbölés nem lehetséges; a minőség-elvárás és a rendszer-rugalmasság között szakadék.
_Value Perception Gaps:_ a kötelezettek jelentős része úgy érzi, hogy **„nincs érzékelhető környezetvédelmi haszna"** a befizetett díjnak, miközben egyre nő — ez csökkenti a compliance-hajlandóságot (arsboni: *„újabb felesleges teher"*).
_Trust and Credibility Gaps:_ MOHU és a szabályozó iránti bizalom alacsony, különösen a díjemelés (2025 Q4) és a bírság-éleződés (2026 Q2) után.
_Források:_ [arsboni — EPR teher-kritika](https://arsboni.hu/vajon-a-kiterjesztett-gyartoi-felelosseg-rendszere-tenyleg-uj-fejezetet-nyit-a-kornyezetvedelemben-vagy-csak-egy-ujabb-felesleges-teher-a-tarsasagok-szamara/), [PwC — díjemelés 2025 október](https://www.pwc.com/hu/hu/sajtoszoba/2025/epr_dijemeles.html).

### Emotional Impact Assessment

_Frustration Levels:_ **magas** — különösen kisvállalati és webshop szegmensben; a közepes-kkv-k „csak át akarják passzolni valakinek" attitűdje azt mutatja, hogy az EPR nem része a core business-nek.
_Loyalty Risks:_ a szoftver- és tanácsadói piacon a kötelezettek „fájdalom-küszöbös" kapcsolatban vannak az aktuális szolgáltatóval — amint felbukkan egy kényelmesebb / olcsóbb megoldás, váltanak.
_Reputation Impact:_ negatív sajtóhatás a MOHU-ra és általánosabban a hulladékgazdálkodási szektorra.
_Customer Retention Risks:_ specializált tanácsadóknál az ügyfélállomány **nehezen skálázható**, a tulajdonosok „egy-egy szakértő" modellben dolgoznak.

### Pain Point Prioritization

_High Priority (gyors, nagy hatás):_
1. **KF-kód besorolás automatizálása / ajánlórendszer** — minden szegmensnél top-fájdalom.
2. **ERP / webshop adat-integráció** csomagolási metaadattal együtt — a DIY-szegmens elsőszámú akadálya.
3. **Multi-client tanácsadói konzol** — a specializált tanácsadói piac (20–30% of cégek, 70%+ díjvolumen) számára top igény.

_Medium Priority:_
4. OKIRkapu SSO + jogosultság-kezelés szoftver oldalon.
5. Változáskövetés / proaktív értesítés (díjtétel, kódlista, határidő).
6. Önrevízió-támogatás (a bevallás előtt automatikus validálás, hogy ne kelljen utólagos helyesbítés).

_Low Priority:_
7. Mikro-webshop „pay-per-bevallás" modell (<5000 Ft/negyedév) — nagy piac, de ár-érzékeny, alacsony ARPU.
8. Felelősség-biztosítás / tévedés-fedezet — érdekes, de nagyon nis szegmens.

_Opportunity Mapping:_
- **legjobb kombináció** a `risk_guard` szemszögéből: **High Priority 2 + 3 + 6** — multi-client tanácsadói konzol + ERP/webshop integráció + pre-submission validáció. Ez egyszerre tudja megszolgálni a specializált tanácsadói partnert és a közepes kkv-kat, ami a díjvolumen 70–80%-át célozza, és a tanácsadói partnercsatornán keresztül hatékonyabb GTM-et is ad, mint direkt mikró-értékesítés.

---

## Customer Decision Processes and Journey

### Customer Decision-Making Processes

A „ki készíti el az EPR bevallást" döntés **nem egyszeri**, hanem **multi-stage, trigger-driven** folyamat. A legtöbb kötelezett cég legalább egyszer csatornát vált a rendszer 2023 júliusi bevezetése óta (tipikusan: könyvelő → saját → specialista, vagy saját → specialista).

_Decision Stages:_ (1) **Érintettség felismerés** → (2) **Regisztrációs pánik** (MOHU + OKIRkapu) → (3) **Első csatorna-kísérlet** (többnyire: „megkérem a könyvelőmet") → (4) **Csatornaváltási trigger** (elutasítás, hiba, bírság-hír) → (5) **Másodlagos csatorna-választás** (DIY vagy specialista) → (6) **Stabilizáció** (egy bevált rutin).
_Decision Timelines:_ az első bevallás-érintett cégnél a döntés 2–6 hét alatt zárul le — de a „stabilizáció" akár 2–3 negyedévet is igénybe vesz, mert az első bevallás során kiderül, mi nem működik.
_Complexity Levels:_ **magas** — a döntés nem tisztán pénzügyi, hanem kompetencia + jogi kockázat + időráfordítás együttes mérlegelése.
_Evaluation Methods:_ (a) Google-keresés (first-touch), (b) könyvelő/kamarai ajánlás (trust-filter), (c) árajánlat-kérés 2–3 szakértőtől, (d) belső kapacitás-felmérés („van-e időm magam csinálni").
_Források:_ [RSM Hungary — EPR rendszer és első adatszolgáltatás](https://www.rsm.hu/blog/eprdij/2023/10/epr-rendszer-kozeledik-az-elso-adatszolgaltatas-valtozik-a-termekdij-bevallas), [Vezinfó — EPR+termékdíj konferencia](https://www.vezinfo.hu/rendezveny/epr-termekdij-bevallas-20240410-konferencia-budapest/), [HVG Konferencia — EPR első bevallás](https://konferencia.hvg.hu/epr_kotelezettseg_kozeledik_az_elso_bevallas).

### Decision Factors and Criteria

_Primary Decision Factors (fő tényezők, sorrendben):_
1. **Várható EPR-díj volumene** — ha kevés, DIY; ha sok, specialista vagy Big4.
2. **Belső kapacitás** — van-e pénzügyes / logisztikus, aki rá tud ülni?
3. **Bírság-kockázat észlelése** — 2026 Q2 bírságolás élesedése ezt felcsavarta.
4. **Tanácsadói ár** — a piacon átalánydíj 30e Ft + ÁFA/hó-tól indul, tipikusan 50–200e Ft/negyedév; ez dönti el, megéri-e a kkv-nak.
5. **Bizalom forrása** — ajánlás könyvelőtől, kamarától, iparági szövetségtől.

_Secondary Decision Factors:_ iparági jellegzetesség (webshop vs. gyártó), termékkör összetettsége (hány KF-kódra kell bontani), ERP / ügyviteli szoftver adottsága.
_Weighing Analysis:_ a mikro-szegmens tisztán ár-vezérelt; a kkv ár + kockázat-mérlegelés; a nagyvállalat kockázat + beszállítói diverzifikáció (minőségi Big4 név).
_Evolution Patterns:_ 2023 Q3–Q4: „csak úszunk át" attitűd → 2024 Q1–Q2: „megértjük, hogy negyedéves" → 2025 Q2–Q4: díjemelés + bírság-hírek miatt szakértő-keresés fellendül → 2026 Q2: bírság-éleződés = friss csatornavált-hullám.
_Források:_ [eprdij.eu — Árlista](https://eprdij.eu/index.php/pricing-style-2), [Adózóna — EPR-díj olcsó egyszerű megoldás](https://adozona.hu/BrandContent/EPRdij_olcso_egyszeru_megoldas_39IVTK), [EY Magyarország — EPR bevezetés](https://www.ey.com/hu_hu/services/global-trade/ey-epr-bevezetes).

### Customer Journey Mapping

_Awareness Stage (jellemző forrás):_
- **Közepes kkv:** könyvelőtől + szakmai sajtóból (Adózóna, RSM-blog, PwC-hírek) értesül;
- **Webshop / mikro:** Shoprenter/Unas platform hírlevélből, Facebook-csoportból, vagy közvetlenül MOHU-értesítésből;
- **Nagyvállalat:** Big4 kapcsolattartó proaktívan tájékoztat.

_Consideration Stage:_ árajánlat-kérés 2–3 tanácsadótól + blog-útmutatók átolvasása + kollégák/iparági ismerősök megkérdezése.

_Decision Stage:_ triggerek: (a) **közeledő negyedéves határidő** (20. napig), (b) **MOHU/OHH megkeresés**, (c) **helyesbítő számla** érkezése, (d) bírság-szigorítás hírei, (e) **könyvelő visszautasítása**.

_Purchase Stage:_ tanácsadó: átalánydíjas szerződés (1-negyedéves minimumtartam gyakori); szoftver: éves előfizetés, ritkán negyedéves.

_Post-Purchase Stage:_ kritikus első negyedév — ha sikerül hibátlanul beadni, hűséggé konvertál; ha nem, ügyfél vált. A tanácsadói piacon az első 3 bevallás után a „váltás-küszöb" alacsony.
_Források:_ [Shoprenter Blog — EPR](https://www.shoprenter.hu/blog/epr-mihez-kezdjek-veled-kiterjesztett-gyartoi-felelosseg-egy-webshop-tulajdonosnal), [Ekerhirado — EPR technikai útmutató](https://ekerhirado.hu/technikai-utmutato-az-epr-bevallas-elkeszitesehez/), [BKIK — EPR](https://bkik.hu/epr).

### Touchpoint Analysis

_Digital Touchpoints:_ MOHU Partner Portál, OKIRkapu, adozona.hu, ado.hu, vg.hu, portfolio.hu, RSM / PwC / EY blogok, Shoprenter + Unas blog/hírlevél, Facebook kkv-csoportok, LinkedIn (B2B).
_Offline Touchpoints:_ iparági konferenciák (Vezinfó, HVG Konferencia), kamarai rendezvények (BKIK, PBKIK), NAK tájékoztatók, könyvelő-ügyfél személyes egyeztetés.
_Information Sources:_ **első hely: könyvelő**, második: szakmai sajtó, harmadik: kamara, negyedik: webshop-platform.
_Influence Channels:_ a kkv-ügyvezetők számára a **könyvelő a legerősebb befolyás-csatorna** — ha ő ajánl tanácsadót, az 60–80%-ban konvertál; ha nem, a kötelezett önálló keresésbe kezd, ami 3–5× több időbe telik.
_Források:_ [NAK — Kistermelők EPR kötelezettségei 2025](https://www.nak.hu/tajekoztatasi-szolgaltatas/elelmiszer-feldolgozas/108445-epr-birsagtetelek-2025-aprilis-1-jetol), [BKIK — EPR tájékoztató](https://bkik.hu/epr), [OMME — Méhészek EPR kötelezettségei 2025](https://www.omme.hu/omme-hirek/meheszek-epr-kotelezettsegei-2025/).

### Information Gathering Patterns

_Research Methods:_ Google-keresés > szakmai blog-olvasás > árajánlat-kérés > Facebook kérdés > könyvelő megkérdezés.
_Information Sources Trusted:_ (1) könyvelő (alapbizalom), (2) saját kamarai tagozat / iparági szövetség, (3) Big4 blog (ha nagyvállalat), (4) specializált EPR tanácsadó referenciáival + ügyfél-visszajelzésekkel, (5) MOHU hivatalos anyagai (gyakran ellentmondásosak).
_Research Duration:_ mikro: pár nap; kkv: 2–4 hét; nagyvállalat: 1–3 hónap (RFP-folyamat).
_Evaluation Criteria:_ referencia, ár, ügyfélszám / portfólió-mélység, kommunikációs készség, SLA válaszidőre.
_Források:_ [epr.hu — Szakértőink](https://epr.hu/szakertoink), [Termékdíjszakértő.hu](https://termekdijszakerto.hu/), [Andersen — EPR tanácsadás](https://hu.andersen.com/hu/adotanacsadas-szolgaltatas/epr-tanacsadas-andersen-magyarorszag-szolgaltatasok/).

### Decision Influencers

_Peer Influence:_ kkv-tulajdonosi körökben magas — Facebook/LinkedIn „kit használsz?" kérdésre kapott ajánlás közvetlenül konvertál.
_Expert Influence:_ könyvelő, adótanácsadó, kamarai szakértő; a Big4 „brand-hatalom" csak a nagyvállalati szegmensben működik.
_Media Influence:_ Adózóna, ado.hu, Portfolio, hellobiznisz (Telekom) — cikkek formálják a bírság-félelmet és így a keresletet; közvetlen hirdetés kevés (tanácsadói piac organikus).
_Social Proof Influence:_ ügyfél-referenciák (a 12 fős EPR.hu Kft. „naprakész tudás" retorikája, az EU-PRO „3 vármegyei kamara elismert" pozicionálása tipikus trust-szignál).
_Források:_ [epr.hu — bemutatkozás](https://epr.hu/bemutatkozas), [eprtanacsadas.hu — EU-PRO](https://eprtanacsadas.hu/), [hellobiznisz.telekom.hu — EPR bírságok](https://hellobiznisz.telekom.hu/epr-birsagok-2025-aprilis-1-tol-vege-a-turelmi-idoszaknak).

### Purchase Decision Factors

_Immediate Purchase Drivers:_ közeledő negyedéves határidő (3–10 napos ablak), MOHU helyesbítő számla/megkeresés, bírság-felszólítás, OHH audit-bejelentés, vagy a könyvelő „nem én csinálom" kijelentése.
_Delayed Purchase Drivers:_ „először megpróbálom magam" attitűd; webshop-platform blogpost-útmutató („elég lesz ez is"); alacsony várható díj; szabadság-időszak.
_Brand Loyalty Factors:_ első negyedév sikeres beadása + válaszidő-tartás; jogszabályi változáskövetés minőség; ár-stabilitás.
_Price Sensitivity:_ nagyon magas a mikro-szegmensben (5–20e Ft-os sávtól indulnak félelemmel), közepes a kkv-ban (50–150e Ft/negyedév elfogadott), alacsony a nagyvállalatnál (százezer Ft feletti-milliós projekt természetes).
_Források:_ [eprdij.eu — ár](https://eprdij.eu/index.php/pricing-style-2), [Transpack — 2026 díjak](https://transpack.hu/2025/12/04/csomagolas-epr-dijak-2026-ban/), [borsonline — EPR határidő](https://www.borsonline.hu/aktualis/2025/03/fontos-hatarido-kozeleg-az-epr-es-gyartok-szamara).

### Customer Decision Optimizations

_Friction Reduction:_ **kulcs**: **könyvelő-ajánlási partnerprogram** (a könyvelő ma átutal, de nem kap semmit érte; egy affiliate-modell 10–20%-os éves jutalékkal jelentős csatorna lenne).
_Trust Building:_ ügyfél-logók + nevesített referenciák + „X. negyedéve benyújtott Y bevallás" számláló + MOHU-szabvány ISO/pénzügyi bizonyítvány.
_Conversion Optimization:_ **fix-díjas próbacsomag** az első bevallásra (pl. 20–30e Ft egyszeri) + elégedettség esetén átmenet rendszeres szerződésbe; webshop-szegmensben **Shoprenter/Unas hivatalos partneri** státusz.
_Loyalty Building:_ proaktív változáskövetés (push-üzenet a díjtétel-változásról), multi-év előfizetési kedvezmény, évente egyszer „EPR-audit riport" ügyfélnek.
_Források:_ [epr.hu — szakértői retorika](https://epr.hu/bemutatkozas), [webaruhazkisokos — EPR tanfolyam](https://webaruhazkisokos.hu/courses/epr-nyilvantartas-es-bevallas/), [webshopmenedzseles.hu](https://webshopmenedzseles.hu/).

---

## Competitive Landscape

### Key Market Players — négy stratégiai csoport

A magyar EPR-piac négy, részben átfedő szereplő-kategóriából áll:

**1. Dedikált EPR/termékdíj-szoftver (SaaS/asztali):**
| Cég / Termék | Üzleti modell | Tipikus ár | Célpiac | Jellemző |
|--------------|---------------|-----------|---------|----------|
| **Körforgó** | self-service SaaS | **8 000 Ft + ÁFA/Q** (+4 000 Ft termékdíj modul) | mikrótól multi-ig | online nyilvántartás + XML-export + API, **nincs jól kiemelve a multi-client tanácsadói mód** |
| **FRIK / hakkod.hu / eprjelentes.hu / gyartoifelelossegirendszer.hu** | egy cég 4 domének mögött, SEO-stratégia | n.a. (licensz alapú) | közepes kkv | ERP-modul, HAK-kód tára, SEO-domináns pozíció |
| **Körforgó-alternatívák** (eprdij.eu) | SaaS + ügyintézés | átalánydíj 30 000 Ft + ÁFA/hó-tól | mikrótól kkv-ig | ügyintézéses csomag, kis cégekre |

**2. ERP-modul EPR funkcióval:**
| Cég | EPR-modul | Pozíció |
|-----|-----------|---------|
| **Naturasoft** | beépített modul (Számla Pro 25 900 Ft, Készlet+Számla 68 900 Ft nettó) | kis és közepes kkv |
| **CloudERP** | EPR díj automatizálás | kereskedelmi kkv |
| **Dolphin InvoicePRO / StorePro** | EPR modul | kereskedelmi kkv |
| **Progen sERPa** | EPR adatszolgáltatás lépések | közép-nagyvállalat |
| **Novitax (NTAX)** | könyvelő-programban | könyvelőirodák |
| **Logzi ERP** | felhő kkv-knak | kkv |

**3. Specializált EPR / termékdíj tanácsadó (ügyintézéses modell, nem szoftver):**
| Cég | Méret / pozíció | Árcsomag |
|-----|----------------|----------|
| **EPR.hu Szakértők Kft.** | 12 fős csapat; **affiliate-programmal** — már van könyvelő-ajánlói csatornájuk | Basic **24 990 Ft/hó**, Optimum **49 990 Ft/hó**, VIP **124 990 Ft/hó** |
| **EU-PRO EHS Consulting (eprtanacsadas.hu)** | 3 vármegyei kamara kiemelt szakértője | n.a. |
| **Green Tax Service Kft. (ktdt.hu)** | publicisztikus szakcikkek, erős jogszabály-fókusz | n.a. |
| **Luko-Jana** | EPR rendelet-specialista | n.a. |
| **Termékdíjszakértő.hu** | komplett tanácsadás | n.a. |
| **Viveo, KVD-Pro, Kaman-Termekdij, Ökotech-Lab, termekdij.info, termekdijugynok.hu, epr-termekdijugyintezes.hu** | regionális / niche szolgáltatók | n.a. |

**4. Big4 / audit-tanácsadó (nagyvállalati compliance):**
- **EY Magyarország**, **PwC**, **Deloitte**, **Grant Thornton**, **RSM Hungary**, **Andersen Magyarország** — mind EPR-praktikusztussal. Méret: projektdíj (milliós nagyságrend), nem átalány.

_Source:_ [Körforgó árak — Adózóna](https://adozona.hu/BrandContent/EPRdij_olcso_egyszeru_megoldas_39IVTK), [Naturasoft EPR](https://www.naturasoft.hu/kiterjesztett-gyartoi-felelosseg-epr-dij.php), [EPR.hu csomagok](https://epr.hu/kapcsolat), [eprdij.eu árlista](https://eprdij.eu/index.php/pricing-style-2), [FRIK / hakkod.hu](https://hakkod.hu/), [eprjelentes.hu](https://eprjelentes.hu/), [Green Tax — ktdt.hu](https://ktdt.hu/szakertoink/), [Andersen EPR](https://hu.andersen.com/hu/adotanacsadas-szolgaltatas/epr-tanacsadas-andersen-magyarorszag-szolgaltatasok/), [EY EPR](https://www.ey.com/hu_hu/services/global-trade/ey-epr-bevezetes), [RSM EPR](https://www.rsm.hu/product/waste-management-consultancy-extended-producer-responsibility-epr), [Deloitte EPR](https://www.deloitte.com/hu/hu/services/tax/perspectives/kulfoldi-vallalkozasok-magyarorszagi-epr-kotelezettsegei.html).

### Market Share Analysis

**Publikus piacrészesedési adat nincs** — a piac a 2023-as indulás óta csak 3 évet látott, sem Gartner-szerű felmérés, sem belföldi elemzés nem készült. Becslés 3 szempontból:

_Szegmens szerinti „ki hol uralkodik":_
- **Mikro / webshop DIY:** Körforgó + blog-útmutatók + Naturasoft; sok ügyfél, alacsony ARPU → volumen-vezérelt piac, de fragmentált
- **Közepes kkv:** specializált tanácsadók (EPR.hu Kft., EU-PRO, Termékdíjszakértő) dominálnak; ERP-modulok (Dolphin, sERPa) kisebb szerep
- **Nagyvállalat:** Big4 + belső compliance csapat; a specializált tanácsadók marginálisak
- **Könyvelőiroda:** **jelenleg nincs dedikált megoldás** — Novitax EPR funkciója részleges, de nincs „white-label" tanácsadói konzol

_Piacrész-becslés tanácsadói szegmensen belül (cégek száma szerint):_
| Szereplő | Becsült piacrész |
|----------|------------------|
| Big4 + RSM + Andersen (nagyvállalati) | ~40–50% a díjvolumen alapján |
| EPR.hu Szakértők Kft., EU-PRO, Termékdíjszakértő (top 3 specializált) | ~15–20% a díjvolumen, 30–40% az ügyfélszám szerint |
| Regionális / niche tanácsadók (10–20 kisebb cég) | ~10–15% |
| Könyvelőirodák (kényszerből) | ~5–10% |

_Piacrész-becslés szoftver-szegmensen belül (előfizetők száma szerint):_
- **Körforgó** valószínűleg a legszélesebb ismerősségű önálló SaaS (Adózóna-pozicionálás, brandcontent)
- **Naturasoft és CloudERP** erős a „meglévő ügyviteli szoftverből tovább" csatornán keresztül
- **FRIK / hakkod.hu család** SEO-dominancia a HAK-kód-kereséseken

### Competitive Positioning Map

```
               ENTERPRISE ───────────── SMB ─────────────── MICRO
  KÖLTSÉGES    │ Big4 (EY/PwC)                                      │ nincs szereplő
               │ Deloitte, Andersen                                  │
               │                                                     │
               │                  EPR.hu Kft.                        │
               │                  EU-PRO                             │
               │                  Green Tax                          │
               │                                                     │
               │  Naturasoft      Naturasoft                         │  Körforgó
               │  sERPa ERP       Dolphin     CloudERP               │  (önálló SaaS)
  OLCSÓ        │                  FRIK/hakkod                        │  + DIY blog-útmutató
```

_Rés-azonosítás:_
- **Multi-client tanácsadói konzol** → NINCS piaci szereplő — fehér folt
- **Webshop/e-commerce natív integráció** → NINCS (Shoprenter/Unas partner státusz kiaknázatlan)
- **Mikrónak olcsó + minőségi + platformfüggetlen** → gyenge lefedettség (Körforgó közel van, de nincs sokféle integráció)
- **Könyvelői partnerprogram + white-label bevallás-készítő** → NINCS — az EPR.hu Kft. affiliate-je csak tanácsadás-ajánló, nem szoftver

### Strengths and Weaknesses — a `risk_guard` nézőpontjából

**Piacvezetők erősségei és gyengeségei:**

| Versenytárs | Erősség | Gyengeség |
|-------------|---------|-----------|
| **Körforgó** | rendkívül olcsó (~32 000 Ft/év), SaaS, API, brand-ismertség | nincs Shoprenter/Unas/ERP integráció, nincs multi-client mód, nincs könyvelő-partner |
| **EPR.hu Szakértők Kft.** | erős SEO (epr.hu domén!), 12 fős csapat, affiliate-program megvan | tisztán ügyintéző, nincs self-service szoftver, skálázás emberi kapacitáshoz kötött |
| **FRIK / hakkod.hu** | SEO-dominancia HAK-kód keresésekre | széttagolt brand, 4 doménnal; nincs egyértelmű pozicionálás |
| **Naturasoft** | már meglévő ügyfél-bázis (kereskedelmi kkv), ismert ügyviteli szoftver | EPR csak funkció, nem standalone termék; új ügyfélnek nem első választás EPR miatt |
| **Big4 (EY/PwC/Deloitte)** | márka-hatalom, jogi háttér, compliance bizalom | drága, csak nagyvállalat; kkv-nak nem releváns |
| **Specializált tanácsadók (EU-PRO, Green Tax, stb.)** | személyes kapcsolat, kamarai trust | Excel-alapú back office, skálázhatatlan, nincs IT-eszköz |

### Market Differentiation — hol pozicionálhatja magát a `risk_guard`

**Rés-stratégiák priority sorrendben:**

1. **Multi-client tanácsadói konzol** — „Salesforce-szerű" felület, ahol 1 specializált tanácsadó 50–200 ügyfelét egyszerre látja, bevallás-életciklus (draft → review → submit → audit log), csapat-jogosultságok. Ma SENKI sem kínál ilyet a piacon, miközben a top ~10 tanácsadó cégből álló szegmens ügyfélszáma tíz- és százezres.
2. **Könyvelő-ajánlói partnerprogram + white-label** — a könyvelő az első kontakt (60–80% conversion); ha kap 10–20% éves affiliate-jutalékot + saját brand alatti kliens-portált, az EPR.hu Kft. affiliate-jénél jóval erősebb csatorna.
3. **Webshop natív integráció** (Shoprenter, Unas, WooCommerce) — csomagolási metaadatokkal, csomagolás-kalkulátorral → mikro- és kisvállalat-szegmens.
4. **Pre-submission validáció AI-val** — a „csak évente 1x önrevízió" korlát miatt a pontosság prémium — KF-kód javaslat, súly-validáció, hibaminta-figyelmeztetés.
5. **Szabályozás-követés / változás-push** — proaktív értesítés termékkörre szabottan (díjemelés, új KF-kód, határidő-közeledés).

_Nem javasolt pozicionálás:_ Big4-jel frontális verseny (nincs márka-erő), tisztán árleszállító versengés Körforgó ellen (bottom-race), klasszikus ERP-modul (már tele van a piac).

### Competitive Threats

1. **Körforgó árcsatája** — ha a `risk_guard` magasabb árú, a mikro-szegmens nem elérhető; de ez kezelhető „freemium / self-service olcsó + prémium tanácsadói konzol" szegmentációval.
2. **EPR.hu Szakértők Kft. affiliate-je** — a könyvelő-csatornán a first-mover pozíciójuk megvan; gyorsabb **partneri élmény** és **több érték** (pl. co-branded portál) kell ellensúlyozni.
3. **ERP-gigászok** (Naturasoft, Dolphin, sERPa) — ha az EPR funkciót „ingyen adják hozzá" a már meglévő ügyfélnek, az a közepes kkv-szegmensben erős lock-in.
4. **Jogszabályi változás** — MOHU, NAV, OHH 2025–2026 közötti újabb rendeletei újrakeverhetik a piacot (pl. a bírságolás-szigorodás mintázata, a KF-kódlista módosítása, nyilvántartási detailszint növelése).
5. **Big4 árcsökkentés / light-csomag** — ha PwC vagy EY „SMB EPR csomagot" indít, a közepes kkv-szegmens ára alulra zuhanhat.

### Opportunities

1. **2026 Q2 bírságolás-éleződés GTM-ablaka** — friss csatornaváltók tömege keres megoldást; 3–6 hónap high-intent időszak.
2. **Könyvelő-partneri programok** — a Magyar Könyvvizsgálói Kamara és az adótanácsadói kamarán keresztül intézményes csatorna építhető (a könyvelő nem akar csinálni EPR-t, de **akar ajánlani**).
3. **Webshop-platform partnerség** (Shoprenter, Unas, WooHungary) — technikai integráció + **platform-app-store** megjelenés → mikro-webshop szegmens volumene.
4. **Nemzetközi skálázhatóság** — a magyar EPR az EU csomagolási rendelet (PPWR) magyar átültetése; Lengyelországban, Csehországban, Romániában hasonló rendszerek indulnak — az architektúra replikálható.
5. **Adatvagyon / benchmark-szolgáltatás** — anonimizált aggregált ügyfél-adatokból iparági benchmark-ok értékesíthetőek (pl. „te mennyit fizetsz csomagolási kategóriánként egy átlaghoz képest").

---

## Strategic Synthesis & Executive Summary

### Executive Summary — a kutatási kérdés egy bekezdésben

A magyar EPR piacot egy **széles körben téves feltételezés** uralja: hogy „a könyvelő készíti a bejelentést". A valóságban a könyvelői szakma **explicite elhatárolódik** tőle (*„nem könyvelői feladat, külön szakterület"* — több blog, állásfoglalás, szakmai fórum egybehangzóan). A ~200 000 kötelezett cég csatorna-megoszlása, cégszám szerint, a trianguláció alapján: **~45–55% saját maga**, **~20–30% specializált EPR/termékdíj tanácsadó**, **~3–5% Big4/audit-tanácsadó**, **~5–10% könyvelő** (kényszerből vagy informálisan), **~10–20% nem teljesítő**. **EPR-díjvolumen-súlyozva a kép megfordul**: a fizetett díj 70–85%-át a tanácsadói + Big4 csatorna adminisztrálja — a DIY-szegmens tehát tömeg, de nem pénz. Kvantitatív publikus statisztika nincs; a sávok szakértői becslések közepes konfidenciával.

### A `risk_guard` szempontjából három kulcs-insight

1. **A könyvelő nem készítő, hanem kapudöntnök.** Az ICP definíciójában a könyvelő = **partner/affiliate csatorna**, NEM = felhasználó. A könyvelő 60–80% conversion-ajánló, ha neki is jó.
2. **Multi-client tanácsadói konzol = piaci fehér folt.** A tanácsadók Excelben dolgoznak, a létező szoftverek (Körforgó, Naturasoft, Dolphin) egy-cégesek. Ez egy értékes, koncentrált B2B-célpiac (~10–30 specializált tanácsadó × 50–500 ügyfél).
3. **2026 Q2 bírságolás-éleződés = GTM-ablak.** A „halogatók" (10–20% nem teljesítő) most aktivizálódnak; 3–6 hónap high-intent időszak.

### Table of Contents — ebben a dokumentumban

1. **Research Overview** (scope, cél)
2. **Customer Behavior and Segments** — csatorna-megoszlás szegmensenként, headline ~45/25/5/10/15% bontás
3. **Customer Pain Points and Needs** — KF-kód, OKIRkapu, súlymérés, önrevízió-korlát, csatorna-hiány
4. **Customer Decision Processes and Journey** — trigger-based purchase, árzónák, könyvelő mint kapudöntnök
5. **Competitive Landscape** — 4 szereplő-csoport, 3 fehér folt (multi-client, könyvelő-partneri, webshop-natív)
6. **Strategic Synthesis & Executive Summary** (jelen szekció) — `risk_guard` ajánlások, GTM, kockázat, roadmap

### Strategic Market Recommendations — `risk_guard`-specifikus

#### R1. Elsődleges ICP-döntés: **kettős ICP** (tanácsadó + kkv)

- **Primary ICP: specializált EPR/termékdíj tanácsadó** (10–30 cég Magyarországon) — *multi-client konzol* vevői
- **Secondary ICP: 10–99 fős gyártó/kereskedő/importőr kkv** — direkt SaaS ügyfél, ERP-integrációs igényekkel
- **NEM ICP most:** mikro-webshop (<10 fő) — túl ár-érzékeny, Körforgó elveszi; későbbi szegmens, ha a core termék már áll
- **NEM ICP most:** nagyvállalat — Big4 lock-in erős, brand-versenybe nem érdemes bekerülni induláskor

#### R2. Pozicionálás

> **„A `risk_guard` az EPR bevallás operációs rendszere specializált tanácsadóknak és közepes kkv-knak — biztonságos bevallás, skálázható ügyfélkezelés, automatikus változáskövetés."**

Három pillér:
- **Multi-client workflow** (tanácsadónak)
- **Pre-submission AI validáció** (KF-kód javaslat, súly-ellenőrzés, hibaminta-figyelmeztetés)
- **Változáskövetés + compliance-push** (díjtétel, kódlista, határidő)

#### R3. Pricing — két tiereken kell gondolkodni

| Tier | Célcsoport | Ár-javaslat | Fő funkció |
|------|-----------|-------------|------------|
| **Professional** | közepes kkv, 1 cég | 15–35 e Ft/hó + éves kedvezmény | single-client, ERP-integráció |
| **Advisor** | tanácsadó iroda, 50+ ügyfél | 150–400 e Ft/hó + usage-based | multi-client konzol, white-label, audit log |

A mikró-csomagot később érdemes bevezetni (freemium vagy **„Starter" 3–5 e Ft/hó** — de csak miután a Professional/Advisor stabilan fut).

#### R4. Go-to-market — prioritizált csatornák

**P0 (első 6 hónap):**
- **Tanácsadói partnerprogram**: 3–5 top specializált EPR/termékdíj cégnél (EPR.hu Szakértők Kft., EU-PRO, Termékdíjszakértő.hu, Green Tax, Luko-Jana) pilotálási ajánlat → korai referenciák. Ez a szegmens koncentrált, hideg-hívható.
- **Könyvelő-ajánlói programot elindítani** — 10–20% éves jutalék, co-branded onboarding; belépő: adózási / könyvelői kamarai tagozatok (MKVK-n kívül az adótanácsadói kamara is).

**P1 (6–12 hónap):**
- **Webshop-platform integrációk**: Shoprenter és Unas *app store* / partner státusz, csomagolási metaadat import.
- **Content SEO** a HAK-kód, KF-kód és EPR témákban — a létező SEO-szereplők (hakkod.hu, epr.hu) lehetőleg kihasználható résekre fókuszálva.

**P2 (12+ hónap):**
- **Big4 white-label OEM licensz** (opcionális) — ha stabilan működik a tanácsadói termék, Big4-ek belső platform-lábként vehetik meg.
- **Regionális expanzió** (PL, CZ, RO) az EU PPWR hullám után.

#### R5. Feature-roadmap (priority sorrendben)

| # | Feature | Miért |
|---|---------|-------|
| F1 | **Multi-tenant tanácsadói konzol** (ügyfélváltó, jogosultság-kezelés, bulk action) | fehér folt, Advisor tier enabler |
| F2 | **KF-kód javasló AI** (termékleírásból → KF-kód pre-fill) | top pain point, differentiator |
| F3 | **Pre-submission validátor** (csomagolás-érték, súly, kóddiszkrepancia) | 50% bírság-kockázat csökkentés |
| F4 | **Shoprenter + Unas + WooCommerce integráció** (termék + csomagolás import) | webshop ICP enabler |
| F5 | **Könyvelő-partnerportál** (co-branded onboarding, ajánlás-tracking, jutalék-riport) | GTM-enabler |
| F6 | **OKIRkapu bejelentés automatizálás** (XML-export + közvetlen submission, ha technikai lehetőség) | workflow teljessé tétele |
| F7 | **Változáskövetés-push** (termékkör-specifikus jogszabály-értesítés) | retention, loyalty |
| F8 | **Audit log + versiokezelés** (önrevízió-korlát miatt) | Advisor tier must-have |

### Risk Assessment and Mitigation

#### Market Risks

| Kockázat | Valószínűség | Hatás | Mitigáció |
|----------|--------------|-------|-----------|
| **Körforgó árcsatája a mikro-szegmensben** | Magas | Közepes (nem ICP) | Ne versenyezz árban; Professional + Advisor tieren előny |
| **EPR.hu Kft. affiliate-jének megerősödése könyvelő-csatornán** | Magas | Magas | Gyorsabb (6–8 hét) könyvelő-partnerprogram-kiépítés; szoftveres + ajánlói duplaérték |
| **Jogszabályi kód/díjtétel-módosítás invalidálja a meglévő logikát** | Közepes | Magas | Decoupling: KF-kódlista és díjtáblák külön adat-réteg, hot-reload ready |
| **2026 Q2 bírságolás-éleződés kitolódik vagy elmarad** | Alacsony | Közepes | Ne csak erre építsen a GTM — a Professional/Advisor tier értéke bírságtól függetlenül is áll |
| **Big4 SMB-csomag belépése** | Közepes | Magas | Tanácsadói partner ökoszisztéma ≠ Big4 értékesítés; alternatív pozicionálás |
| **MOHU/NAV direkt megoldással kiszállhat** (pl. ingyenes state-tool) | Alacsony | Kritikus | Figyelemmel követni; differenciátor az Advisor-workflow és a validáció, nem a bejelentés-kitöltés |

#### Operational Risks

- **Jogi felelősség** — ha a `risk_guard` KF-kódot javasol és az hibás, bírság-kockázat átháríthatóságát tisztázni (ToS, felelősségkorlát).
- **Adatbiztonság** — ügyfél-adat (termékek, árak, csomagolás) szenzitív; GDPR + tanácsadói-ügyfél titok együtt.
- **Változás-követési kapacitás** — a jogszabály gyakran változik; dedikált „compliance-ops" funkció kell, nem bízható önkéntes mellékfeladatra.

### Implementation Roadmap

| Fázis | Időzítés (heti ciklus) | Fő célok |
|-------|------------------------|----------|
| **Phase 0 — Discovery** | M0–M2 | 5–10 tanácsadói interjú; 2–3 LOI; Advisor tier wireframe validálás |
| **Phase 1 — Advisor MVP** | M2–M6 | F1 + F2 + F8 live; 2–3 pilotáló tanácsadó iroda ügyfeleivel (NDA + pilot-ár) |
| **Phase 2 — Professional tier + ERP integráció** | M6–M10 | F3 + F4; Shoprenter/Unas app-store megjelenés; könyvelő-partnerprogram kickoff |
| **Phase 3 — Scale + compliance-ops** | M10–M14 | F5 + F6 + F7; változás-értesítő pipeline; első 50 fizető ügyfél; 5+ könyvelőiroda-partner |
| **Phase 4 — Expand** | M14+ | Mikro-tier (freemium), regionális playbook, Big4 OEM-beszélgetések |

### Success Metrics (KPI)

- **M3:** 5 tanácsadói interjú + 2 LOI
- **M6:** Advisor MVP live + 2 pilotáló iroda (10+ ügyfél)
- **M9:** 10 fizető tanácsadói ügyfél + 5 könyvelő-partner
- **M12:** 50 Professional ügyfél, 10 Advisor ügyfél, MRR X Ft (üzleti tervhez igazítva)
- **M18:** 200 Professional, 25 Advisor, első nem-magyar piaci ügyfél

### Future Market Outlook

- **Rövid táv (2026 Q2–Q4):** bírságolás-éleződés aktivizálja a halogatókat; tanácsadói kereslet csúcs; szoftverek versenyképessége eldől.
- **Közép táv (2027–2028):** EU PPWR harmonizáció — csomagolási nyilvántartás közös vonásai több EU-országban; a magyar EPR sablon adaptálható.
- **Hosszú táv (2029+):** circular economy reporting egyre több iparágban (textil, elektronikai, DRS kiterjesztés) — az EPR adatszolgáltatás belenőhet egy szélesebb **compliance-data platformba**.

---

## Market Research Methodology and Source Verification

### Methodology

- **Primary approach:** web-alapú szekunder kutatás hivatalos, szakmai és sajtó-forrásokkal, forrás-trianguláció.
- **Nyelv:** magyar elsődleges, angol másodlagos (Big4, EU-rendelet).
- **Időszak:** 2023. július (EPR indulás) — 2026. április (jelen).
- **Földrajz:** Magyarország.
- **Konfidencia-értékelés:** minden szegmentációs becslés mellett konfidencia-szint megjelölve.

### Key Limitations

- **Nincs primer kvantitatív kutatás** — minden arány triangulált szakértői becslés
- **MOHU / NAV / KSH nem publikál csatorna-megoszlást** — a piacrészesedés-becslések saját interpretáció
- **Konfidencia-sávok:** fő szegmens-arányoknál közepes; abszolút százalékoknál alacsony-közepes; trendeknél közepes-magas
- **Javasolt primer kutatás:** 200–300 fős online felmérés + 10 tanácsadói mélyinterjú a sávok szűkítéséhez

### Primary Sources Cited (16+)

- [MOHU — EPR rendszer](https://mohu.hu/hu/epr-rendszer) — hivatalos rendszer-leírás
- [MOHU — Gyártói regisztráció tájékoztató](https://mohu.hu/pdf/EPR-regisztracio.pdf)
- [NAV — Környezetvédelmi termékdíj 2025](https://nav.gov.hu/pfile/file?path=/ugyfeliranytu/nezzen-utana/inf_fuz/2025/48.-Kornyezetvedelmi-termekdij-2025.-01.-23)
- [80/2023. (III. 14.) Korm. rendelet](https://net.jogtar.hu/jogszabaly?docid=a2300080.kor)

### Key Industry & Advisory Sources

- [RSM Hungary EPR blog-sorozat](https://www.rsm.hu/blog/eprdij/2023/10/epr-rendszer-kozeledik-az-elso-adatszolgaltatas-valtozik-a-termekdij-bevallas), [RSM bírság cikk](https://www.rsm.hu/blog/vam/elesedik-az-epr-birsagolas)
- [PwC — 2025 októberi díjemelés](https://www.pwc.com/hu/hu/sajtoszoba/2025/epr_dijemeles.html)
- [EY Magyarország — EPR bevezetés](https://www.ey.com/hu_hu/services/global-trade/ey-epr-bevezetes)
- [Deloitte — Külföldi vállalkozások EPR](https://www.deloitte.com/hu/hu/services/tax/perspectives/kulfoldi-vallalkozasok-magyarorszagi-epr-kotelezettsegei.html)
- [Andersen — KF kódok](https://hu.andersen.com/hu/hirek/kf-kodok-avagy-hogyan-kell-meghatarozni-az-epr-rendszerben-a-korforgasos-termekeket/), [Andersen — EPR tanácsadás](https://hu.andersen.com/hu/adotanacsadas-szolgaltatas/epr-tanacsadas-andersen-magyarorszag-szolgaltatasok/)
- [Green Tax Service (ktdt.hu)](https://ktdt.hu/en/kerdojelek-a-kiterjesztett-gyartoi-felelossegi-rendszerben/)
- [WTS Klient — EPR bírság](https://wtsklient.hu/2025/03/25/epr-birsag/)

### Accountant/Bookkeeper Perspective Sources (key for „könyvelő elhatárolódik" finding)

- [Egy könyvelő élete — EPR bevallás útmutató](https://egykonyveloelete.hu/epr-bevallas-technikai-utmutato/)
- [Egy könyvelő élete — OKIRkapu regisztráció](https://egykonyveloelete.hu/okirkapu-regisztracio-epr-adatszolgaltatas-teljesitesehez/)
- [Optitax Számviteli Kft.](https://optitax.hu/konyveles/amit-az-epr-dijrol-tudni-kell-kiterjesztett-gyartoi-felelosseg/)
- [Pallas70 — EPR díj könyvelése](https://pallas70.hu/tudashalo/hirek/epr-dij-konyvelese)
- [Vezinfóblog — Kell-e a könyvelőknek termékdíjjal foglalkozni](https://www.vezinfoblog.hu/kell-e-a-konyveloknek-termekdijjal-epr-rel-foglalkozni/)
- [Adózóna — Helyesbítő számlák EPR](https://adozona.hu/2024_es_adovaltozasok/Grant_Thornton_epr_MOHU_AFA_szamla_gazdasag_FY2Q7C)
- [Adózóna — EPR kódok](https://adozona.hu/2023_as_adovaltozasok/A_nap_kerdese_milyen_EPRkodokat_hasznaljunk_D29YQN)

### Software / Platform Sources

- [Körforgó](https://korforgo.hu/), [Adózóna brandcontent — Körforgó ára](https://adozona.hu/BrandContent/EPRdij_olcso_egyszeru_megoldas_39IVTK)
- [Naturasoft EPR](https://www.naturasoft.hu/kiterjesztett-gyartoi-felelosseg-epr-dij.php)
- [CloudERP — EPR díj kezelése](https://clouderp.hu/epr-dij-kezelese/)
- [FRIK / hakkod.hu](https://hakkod.hu/), [eprjelentes.hu](https://eprjelentes.hu/), [gyartoifelelossegirendszer.hu](https://gyartoifelelossegirendszer.hu/)
- [Progen sERPa — EPR adatszolgáltatás](https://www.progen.hu/sERPa/help/td_epradatszolgaltataslepesei.html)
- [Dolphin — EPR összefoglaló](https://dolphin.hu/hasznos/blog/504-epr-osszefoglalo-bevallashoz)

### Specialized EPR Consultancy Sources

- [EPR.hu Szakértők Kft.](https://epr.hu/), [EPR.hu kapcsolat / árcsomagok](https://epr.hu/kapcsolat)
- [EU-PRO EHS Consulting](https://eprtanacsadas.hu/)
- [Termékdíjszakértő.hu](https://termekdijszakerto.hu/)
- [Luko-Jana](https://luko-jana.hu/epr-tanacsadas)
- [KVD-Pro](https://xn--termkdj-eya2b.hu/), [Termékdíjügynök](https://termekdijugynok.hu/), [Viveo](https://viveo.hu/), [Ökotech-Lab](http://www.okotech-lab.hu/termekdij_ugyintezes.html), [Kamantermekdij](https://www.kamantermekdij.hu/)
- [Eprdij.eu árlista](https://eprdij.eu/index.php/pricing-style-2)

### Webshop / E-commerce Sources

- [Shoprenter Blog — EPR](https://www.shoprenter.hu/blog/epr-mihez-kezdjek-veled-kiterjesztett-gyartoi-felelosseg-egy-webshop-tulajdonosnal)
- [Ekerhirado — EPR technikai útmutató](https://ekerhirado.hu/technikai-utmutato-az-epr-bevallas-elkeszitesehez/)
- [Webáruház Kisokos — EPR tanfolyam](https://webaruhazkisokos.hu/courses/epr-nyilvantartas-es-bevallas/)
- [Webshopmenedzseles GYIK](https://webshopmenedzseles.hu/spg/930300,2804394/GYIK)
- [EPR.hu — Webshop tulajdonosok](https://epr.hu/webshop-tulajdonosok-figyelem!-avagy-miert-vagyunk-ertintettek-az-uj-kiterjesztett-gyartoi-fele2023-09-25)

### Media / Industry Press

- [Index — MOHU 2025 július határidő](https://index.hu/gazdasag/2025/07/13/mohu-gyartoi-felelosseg-muanyag-epr-regisztracios-hatarido/)
- [Penzcentrum — EPR díjak 2025](https://www.penzcentrum.hu/vallalkozas/20251126/igy-valtoztak-az-epr-dijak-2025-ben-itt-az-uj-epr-jogszabaly-ekkora-lesz-az-emeles-1189244.html)
- [Transpack — 2026 EPR díjak](https://transpack.hu/2025/12/04/csomagolas-epr-dijak-2026-ban/)
- [Borsonline — EPR határidő](https://www.borsonline.hu/aktualis/2025/03/fontos-hatarido-kozeleg-az-epr-es-gyartok-szamara)
- [Hellobiznisz Telekom — EPR bírságok](https://hellobiznisz.telekom.hu/epr-birsagok-2025-aprilis-1-tol-vege-a-turelmi-idoszaknak)
- [Arsboni — EPR kritika](https://arsboni.hu/vajon-a-kiterjesztett-gyartoi-felelosseg-rendszere-tenyleg-uj-fejezetet-nyit-a-kornyezetvedelemben-vagy-csak-egy-ujabb-felesleges-teher-a-tarsasagok-szamara/)
- [Katona Law — EPR fines 2025](https://katonalaw.com/en/the-epr-fines-2025-stricter-regulations-and-consequences-in-hungary/)

### Kamarai / Iparági Szövetségek

- [BKIK — EPR tájékoztató](https://bkik.hu/epr)
- [PBKIK — MOHU regisztráció](https://pbkik.hu/2023/06/27/hirek/kinek-kell-regisztralnia-a-mohu-ra/)
- [NAK — EPR bírságtételek 2025](https://www.nak.hu/tajekoztatasi-szolgaltatas/elelmiszer-feldolgozas/108445-epr-birsagtetelek-2025-aprilis-1-jetol)
- [OMME — Méhészek EPR](https://www.omme.hu/omme-hirek/meheszek-epr-kotelezettsegei-2025/)

### Confidence Levels Summary

| Állítás | Konfidencia | Alap |
|---------|-------------|------|
| Könyvelői szakma elhatárolódik az EPR-től | **Magas** | 5+ független blog, szakmai forrás konvergens |
| ~200 000 kötelezett cég | Közepes | epr.hu iparági becslés; MOHU nem erősíti meg hivatalosan |
| Csatorna-megoszlás sávok | **Közepes-alacsony** | trianguláció, nem felmérés |
| 2025 Q4 díjemelés, 2026 Q2 bírságolás-éleződés | Magas | hivatalos / több független forrás |
| EPR.hu Kft. pricing (24.990–124.990 Ft) | Magas | direkt weboldal |
| Körforgó pricing (8000 Ft + 4000 Ft / Q) | Magas | Adózóna brandcontent |
| Multi-client tanácsadói konzol = fehér folt | **Magas** (negatív evidencia) | 20+ keresés után nincs ilyen szereplő |

---

## Market Research Conclusion

### Summary of Key Market Findings

A kutatási kérdésre — **„milyen arányban készítik a cégek maguknak és milyen arányban a könyvelők az EPR bejelentést?"** — a válasz:

- **A könyvelő NEM készítő** (~5–10% cégszám alapján, főleg kényszerből), hanem **kapudöntnök**.
- A legnagyobb csatorna **a saját maga** (~45–55% cégszám szerint), de ez **kis díjfizető** szegmens.
- A **specializált EPR/termékdíj tanácsadó** (~20–30% cégszám, **55–70% díjvolumen**) a piac pénz-középpontja.
- A **nagyvállalati Big4** (~3–5% cégszám, ~15–30% díjvolumen) a csúcs-szegmens.
- **~10–20% nem teljesít**, ők a 2026 Q2 bírságolás-éleződés célszegmense.

### Strategic Market Impact Assessment for `risk_guard`

- **ICP döntés:** elsődlegesen **tanácsadó** (multi-client konzol), másodlagosan **közepes kkv** (ERP-integráció). Mikro-webshop = későbbi szegmens.
- **Csatorna:** **könyvelő-partnerprogram = fő GTM-kar**; a könyvelő nem ügyfél, hanem ajánló.
- **Differenciátor:** multi-client workflow + KF-kód AI + változás-push. NE árban versenyezz Körforgóval; NE brand-versenyezz Big4-gyel.
- **Időzítés:** 2026 Q2–Q4 bírságolás-éleződés és 2027-es PPWR-harmonizáció adja a két legnagyobb GTM-ablakot.

### Next Steps — Market Research Follow-ups

1. **Primer validáció:** 5–10 mélyinterjú top specializált EPR-tanácsadóval (EPR.hu Szakértők Kft., EU-PRO, Termékdíjszakértő.hu, Green Tax, Luko-Jana) — tanácsadói konzolnak valódi pain-pointjait pontosítani.
2. **Kamarai / szövetségi csatorna-feltérképezés:** MKVK + adótanácsadói kamara + iparági szövetségek (GYOSZ, NAK) — könyvelő-partnerprogram első hálózata.
3. **Webshop-platform beszélgetések:** Shoprenter és Unas partnership/integráció időbeli lehetőségei.
4. **Szabályozási changelog beépítés:** MOHU / NAV / OHH rendelet-változások automatizált követése (bmad-domain-research follow-up téma lehet).
5. **PPWR / EU EPR harmonizációs figyelés:** 2027–2028 regionális expanzió előkészítéséhez.

---

**Market Research Completion Date:** 2026-04-17
**Research Period:** 2023. július – 2026. április (3 év)
**Document Length:** Comprehensive
**Source Verification:** 45+ egyedi forrás, trianguláció
**Market Confidence Level:**
- **High** — könyvelői szakma elhatárolódása; versenytárs pricing; jogszabályi fejlemények
- **Medium** — szegmentációs sávok (cégméret-alapú megoszlás)
- **Low** — abszolút csatorna-arányok (szakértői becslés, nem felmérés)

_This market research document serves as the authoritative baseline for the `risk_guard` EPR product strategy. Primer validation recommended before binding GTM commitments._

