# League App — Business Analysis

**Subject:** Electronic match report (*elektronický zápis o utkání*) for Pražský svaz malého fotbalu (PSMF), Hanspaulská liga.
**Date of analysis:** 2026-08-25
**Status:** Pre-technology. No stack, storage, sync strategy or platform has been chosen, and this document deliberately does not choose one.

## How to read this document

Three sections, kept strictly apart:

- **KNOWN** — established from primary sources (PSMF regulations, the official match report form, the live competition site) or stated as fact by the project owner. Sourced.
- **ASSUMED** — working assumptions. Each carries an explicit *breaks if wrong* note.
- **ASK** — open questions. These must be answered by PSMF or by the project team before the technology decision is sound. Most are given in Czech, ready to send.

A fourth section, **INPUTS TO THE TECHNOLOGY DECISION**, gathers the facts that constrain that later choice without making it.

Anything not in KNOWN is not a fact. Where sources conflict, that is recorded as a conflict rather than resolved by guessing.

---

# 1. The product in one paragraph

PSMF runs an amateur small-football league in Prague on paper. Every match produces a handwritten two-page *Zápis o utkání* (ZoU) which is dropped into one of four collection boxes around the city, collected, roughly checked, has its score typed onto the website, and is then passed to a small crew who retype the event detail — scorers, minutes, cards. That retyping takes about a week. The proposed product replaces the act of *producing* the ZoU with a phone app used pitch-side by the referee, capturing the same information in the same scope and delivering it digitally the same evening. It changes no competition rules and no control mechanisms. The value sold to PSMF is not a better referee experience — it is the elimination of a week of transcription.

---

# 2. KNOWN

## 2.1 The organisation

| Fact | Detail | Source |
|---|---|---|
| Legal entity | Pražský svaz malého fotbalu, legal form *zapsaný ústav* | [Kontakt](https://www.psmf.cz/vyveska/kontakt/) |
| Address | U Nových vil 945/26, Praha 10 – Strašnice, 100 00 | ibid. |
| Contact | psmf@psmf.cz, +420 274 822 596; separate `vedouci@psmf.cz` for team leaders | ibid., team pages |
| Office hours | **Mondays only**, 09:00–19:00. Card payments not accepted. | ibid. |
| Governing body | Executive Committee (VV PSMF) | Soutěžní řád |
| Disciplinary body | STDK — the sole body able to impose or modify penalties | Disciplinární řád, art. 12–13 |
| Appeal body | ORK — its decision is final | Soutěžní řád |

The office being open one day a week, and that day being Monday, is not incidental: the ZoU submission deadline is Monday 19:00.

## 2.2 League structure and calendar

- **8 levels**, subdivided into groups: 1.liga (A), 2.liga (A–B), 3.liga (A–D), 4.liga (A–F), 5.liga (A–I), 6.liga (A–L), 7.liga (A–L), 8.liga (A–N) — **60 groups** in Hanspaulská liga alone.
- A sampled group (5.liga A) has **12 teams and 11 rounds**, one round per week.
- Autumn 2026 season: **31 August – 7 December 2026**. Spring 2026 ran 12 March – 25 June 2026 and was the 106th season.
- Matches are **evening fixtures**, kickoffs staggered from 19:00 to 20:45 in 15-minute steps. The sampled group used Monday, Tuesday, Thursday and Friday.
- Parallel competitions exist: Veteránská (35+), Superveteránská (45+), Ultraveteránská (55+), Futsal, Ligový pohár, tournaments.
- **~35 pitches**, all within Prague (districts 3, 4, 5, 6, 8, 9, 10), mostly third-generation artificial turf **with floodlighting** and changing facilities. Venues are referenced by short codes (METE1, MOTO1, ZAKOS, MIK, PRA, ZAK, P1).

Sources: [autumn 2026 competition pages](https://www.psmf.cz/souteze/2026-hanspaulska-liga-podzim/), [Hřiště](https://www.psmf.cz/hriste/), [propozice HL](https://www.psmf.cz/vyveska/propozice-souteze-pro-hl/).

## 2.3 Volumes (derived arithmetic)

| Quantity | Value | Derivation |
|---|---|---|
| Groups (HL) | 60 | counted from the season page |
| Teams (HL) | ~720 | 60 × 12 |
| Matches per group per half-season | 66 | 11 rounds × 6 |
| Matches per week (HL) | ~360 | 60 groups × 6 |
| Matches per half-season (HL) | ~3,960 | 60 × 66 |
| Matches per year (HL) | ~7,900 | × 2 seasons |
| Registered players (HL) | ~7,000–11,000 | 720 teams × 10–15 |
| Peak simultaneous matches | bounded at ~35 | one per pitch; realistically 20–30 |
| Records per match | ~20–40 | 16 roster lines + 6 goals + 7 cards in the worked example |
| **One pilot group, one half-season** | **66 matches, 12 teams, ~180 players** | |

This is a small-data problem. The per-match payload, without photographs, is on the order of tens of kilobytes.

## 2.4 The current process, end to end

1. The referee fills the ZoU header. Each **captain hand-writes their own team's lineup** and signs it before kickoff, confirming all players are eligible.
2. The referee records goals, cards and a mandatory match commentary during and after the match.
3. Both captains and the referee sign the completed ZoU.
4. The paper goes into one of **four collection boxes**: the window grille at the PSMF office (U Nových vil 26), pitch MIK (canteen), pitch PRA (in front of the buffet), pitch ZAK (on the fence). The form itself advises referees to **photograph the ZoU before submitting it**.
5. Alternatively the photographed ZoU may be **emailed to psmf@psmf.cz**, with the paper original to follow.
6. PSMF collects, roughly checks, and types **the score only** onto the website.
7. The report passes to a second party, whose small crew **retype the event detail** — scorers, minutes, cards.
8. Results appear at psmf.cz/souteze, typically updated Tuesday afternoons. **Unofficial results are shown in grey, confirmed results in red.**
9. Full event detail lags by roughly **one week**. The Monday deadline for the previous week's results is not always met.

Steps 1–5 come from the ZoU form and the propozice; steps 6–9 are as described by the project owner.

Two consequences constrain the product:

- A result already has a **two-state lifecycle** — provisional, then confirmed. The product must not assume that submission equals finality.
- Email delivery of the report **is already an accepted channel**. The remaining friction is that the paper original must still follow.

## 2.5 The artefact: Zápis o utkání, field by field

Source: the official form, [Zápis o utkání](https://www.psmf.cz/vyveska/zapis-o-utkani/), an .xlsx valid from 20 March 2023 containing a fully worked example match (Kominíci vs. Sp. Sumýš, 29.2.24, 19:00, pitch ZAKOS, league 8M).

### Page 1 — header, filled by the referee

| Field | Notes |
|---|---|
| `Hřiště` | pitch code |
| `Datum`, `Čas` | |
| `Liga` | group identifier, e.g. "8M" |
| `Rozhodčí, asistent` | main referee and assistant. A licensed referee hired by the delegating team writes **R** next to their name (example: "Jiří Vlk, Roman Liška ®"). If the delegated referee fails to appear, the substitute referees write **their own team**. |
| `Týmy` | **the team(s) that delegated the referees** — distinct from the two playing teams |

### Page 1 — lineups, filled by the CAPTAINS, one block per team

| Column | Notes |
|---|---|
| `Název týmu` | |
| `Barva dresů` | kit colour, e.g. "modrá", "černo-bílá" |
| `Dres č.` | jersey number |
| `Číslo RP` | registration card number — 5 digits in the example (59001, 56252, …) |
| `Příjmení a jméno` | surname first, then given name |

> **"3) Soupisky vypisují kapitáni týmů."** — the captains write the lineups, not the referee.
>
> **"U hráčů, kteří nemají k dispozici svůj registrační průkaz (RP), uvedou místo čísla RP jejich datum narození."** — a player without their registration card has their **date of birth** entered in the RP column instead.

The worked example contains one such row: `33 | 990121 | Hlok Petr` — a 6-digit value among 5-digit RP numbers, consistent with a date of birth.

Each captain signs beneath their own lineup: **"Kapitáni potvrzují, že všichni hráči startují oprávněně"** / *"podpis kapitána před zahájením utkání"*. The example shows a deputy signing as `Lepiš (zást.)`, so captaincy can be delegated.

### Page 2 — goals

`Góly:` recorded on the scoring team's side, up to **12 rows per team**.

| Column | Notes |
|---|---|
| `Čas` | minute |
| `Číslo` | jersey number |
| `Střelec` | scorer |
| `Stav` | running score after the goal |

Worked example: `5´ Poupě 0:1`, `11´ Novák 1:1`, `13´ — 2:1`, `29´ Pořízek 2:2`, `45´ Kulík 3:2`, `58´ Lovec 4:2`.

The third row carries a time and a score but **no scorer** — the scorer field is optional in practice. Overflow beyond 12 goals continues into the opponent's column or the commentary area.

### Page 2 — cards

`Osobní tresty:` — **free text**, one block for yellow and one for red, each requiring *time, number, name and reason*.

- `Žluté karty (čas, číslo, jméno a důvod)`: `20´ 13 Bača - podražení`, `30´+ Lepiš A. - nesp. chování`, `49´ 13 Bača - zakopnutí míče`
- `Červené karty (čas, číslo, jméno a důvod)`: `40´ 12 Houžev - oplácení, vražení do protihráče v přerušené hře`, `49´ 13 Bača - 2. ŽK`

Rules attached to this block:

- Cards must be entered **"vždy však před konečným podepsáním ZoU kapitány týmů"** — always before the captains' final signature.
- Cards issued at half-time are timed **`30´+`**; those issued after the final whistle but before signing, **`60´+`**. Minute is therefore not a plain integer.
- A dismissal for a second yellow is written explicitly as **`2. ŽK`**.
- The reason for a red card must be unambiguous: *"Zmaření vyložené šance soupeře" není relevantní důvod, protože mohlo být provedeno čistě!* Acceptable examples given: "Hra rukou v šanci soupeře", "Faul zezadu v šanci soupeře".
- **If no cards were issued, the referee must strike the boxes through** (`políčka proškrtne`) — an explicit "none", not a blank.

### Page 2 — result

`poločas:` (half-time score), `Konečný výsledek:`, `Vítěz utkání:` (winner of the match).

### Page 2 — referee assessment and MANDATORY commentary

> `Hodnocení a POVINNÝ komentář rozhodčího k utkání, nejlepší hráči (NH); (D = domácí, H = hosté, Čd = čekací doba, Č = čísla dresů, B = jednotná barva)`

Per team (D = home, H = away):

- `NH` — best player, by jersey number
- `Čd` — waiting time in minutes, recorded when a team is not ready to play at the official kickoff time
- `Č` — are the team's shirts properly numbered? yes / no
- `B` — is the team in uniform kit colour? yes / no

Then `Komentář:` — free prose. The form states the commentary *"může být stručný, měl by však obsahovat všechny důležité okamžiky utkání (zahrávání PK apod.)"*. The worked example runs to roughly 400 characters and covers a missed penalty, a dismissed player abusing opponents and the referee from behind the fence, and one team turning out in mismatched shirts.

The `Č` and `B` ratings feed directly into fines (§2.7). The form notes this block was introduced recently: *"7) Nově je zavedeno hodnocení disciplíny týmů…"*.

### Page 2 — signatures

`Podpis kapitána` (×2), `Podpis rozhodčího`.

### Deadline and accountability

*"Čitelně vyplněný zápis odevzdejte do nejbližšího pondělí 19:00 hod. na určené místo."* After a Sunday match this is **one day**; after a Monday match, seven.

*"10) Je-li v ZoU něco vynecháno, špatně vyplněno či je-li odevzdán pozdě, pokuta je připsána týmu, který delegoval rozhodčí."* — **the fine for an incomplete, incorrect or late report is charged to the team that delegated the referee**, not to the referee.

## 2.6 Rules that constrain the data model

From [Pravidla malého fotbalu 5+1](https://www.psmf.cz/dokumenty/pravidla-maleho-fotbalu-5-1/):

- **5+1**: maximum 6 on the field per side including the goalkeeper.
- **Substitutions are unlimited and rolling**, from a designated zone, permitted while the ball is in play.
- **2 × 30 minutes** gross time, break of at most 5 minutes. The clock **runs continuously**; the referee may add time.
- **No offside.**
- **Penalty kicks from 7 m.**
- **A dismissed player's team may bring on a replacement after 10 minutes** — a period not shortened by a goal and unaffected by further dismissals. This is a power-play timer with its own lifecycle.
- In knockout matches there is no extra time; penalties follow immediately.

From [Soutěžní řád 5+1](https://www.psmf.cz/dokumenty/soutezni-rad-5-1/):

- Minimum age for Hanspaulská liga: **15 years on the day of the match**. Veteran competitions have their own thresholds (35 / 45 / 55).
- **Win = 2 points**, draw = 1, loss = 0. Not the 3-point system.
- **Forfeit (kontumace) = 0 points and a 0:6 goal record.** The winner takes 2 points and 6:0 unless the played result was better. A *technical* forfeit (ineligible player, refused identity check, abandonment for referee assault, unauthorised relocation) voids the result on different terms.
- Three forfeits in a season, or two consecutive, removes the team from the competition and demotes it at least two levels.
- Tie-breaking: head-to-head, then goal difference in mutual matches, then goals scored, then a mini-table, then overall goal difference, then overall goals, then a play-off or coin toss.
- **Identity check (konfrontace)** may be demanded by either captain or a federation official at any time. Verification requires the **physical** registration card or a government ID. *Photographs on a phone and paper copies are explicitly insufficient.*
- Referee equipment includes a whistle, stopwatch, cards, pen **and the match report form**.
- The match report must reach PSMF by 19:00 on the first working day of the week following the match.

From [Disciplinární řád 5+1](https://www.psmf.cz/dokumenty/disciplinarni-rad-5-1/):

- Yellow cards accumulate **within a league group, per season**, and trigger an automatic suspension on **even-numbered totals** — 2nd, 4th, 6th, 8th.
- **Two yellows in one match contribute zero to that total.** A yellow followed by a straight red counts as one yellow.
- A red card carries **immediate suspension until STDK decides**; there is no fixed automatic ban.
- STDK decisions are **published within two days** on the website under "STDK a ORK".
- Proceedings cannot be opened more than one year after the offence.

> **Consequence:** the distinction between a *straight red*, a *second-yellow red*, and *two yellows in one match* is not cosmetic — it changes a player's suspension arithmetic. The model must record which yellow, if any, produced the dismissal. The handwritten form encodes this as the literal string `2. ŽK`.

## 2.7 Money

From the [fee schedule](https://www.psmf.cz/dokumenty/sazebnik-poplatku-pokut-a-odmen/) and the [HL propozice](https://www.psmf.cz/vyveska/propozice-souteze-pro-hl/):

| Item | CZK |
|---|---|
| Player entry fee, 3+ appearances | 500 |
| Player entry fee, 1–2 appearances | 400 |
| Registration card or duplicate | 50 |
| Transfer / loan notification | 100 |
| Appeal to STDK / to ORK | 100 / 200 |
| **Unsigned match report** | **100** |
| **Missing referee, per match** | **400** |
| Missing assistant referee | 200 |
| Mismatched kit, first / subsequent offence | 50 / 100 |
| Missing team ball, first / subsequent | 100 / 200 |
| Substitute referee fee, match completed / forfeit only | 200 / 100 |
| 1.liga exemption from delegating referees | 6,000 per season |

Refereeing is compensated at roughly 200 CZK per match. The economics of the role are those of a favour with a small honorarium, not a profession.

## 2.8 Roles — including the third one

**Referee.** Formally, PSMF issues a *Delegace rozhodčích* naming **teams**, and the delegated team must supply a main referee and an assistant: from its own registered members, by hiring a licensed referee, or through a referee coordinator. Failure costs 400 CZK. 1.liga teams are exempt and pay 6,000 CZK instead. In practice, per the project owner, a network of amateur referees covers multiple matches for a fee. Both pictures hold simultaneously; see ASSUMED §3.1.

**Team captain / leader.** Writes the lineup on the ZoU, signs it before kickoff attesting eligibility, signs the completed report, and may lodge a protest for the referee to note. May delegate to a deputy (`zást.`). Teams are contacted collectively via `vedouci@psmf.cz`.

**League administration — not one role but six distinct jobs:**

| Job | Who | What they do |
|---|---|---|
| Office / registration | PSMF office, Mondays only | Accepts registration forms with photographs, issues registration cards, maintains the physical card cabinet |
| Collection and first check | PSMF officials | Empty the boxes, roughly check reports, type the score to the web |
| Event transcription | A small crew under a second party | Retype scorers, minutes and cards. **~1 week of lag.** This is the work the product eliminates. |
| Discipline (STDK) | Committee | Sole authority on penalties; works from the ZoU as evidence; publishes within 2 days |
| Appeals (ORK) | Committee | Final instance |
| Systems | An external company or individual | Runs the database and the website, and oversees the match-record input |

The last row was missing from the original brief and is the single largest project risk. See §4.1.

## 2.9 What is publicly obtainable versus what is not

This split determines how much cooperation a pilot requires.

**Public, structured, scrapeable, no permission needed:**

- Full fixture list per team and per group — date, weekday, time, venue code, opponent, round number. Example: [Celtic THK](https://www.psmf.cz/souteze/2026-hanspaulska-liga-podzim/5-a/tymy/celtic-thk/), 11 matches from 31.8.26 to 7.12.26.
- Team names, group membership, **kit colour** ("zeleno-bílá").
- Standings: position, team, played, W, D, L, goals for:against, points.
- After matches are played, a per-team player table `Hráč | Zápasů | Gólů` — e.g. "Koval Oleksandr | 11 | 10" — and per-match scorer and card detail with minutes, e.g. "2., 30. Adam Zelenka; 23. Mykola Kodatskyi".
- All regulations, the fee schedule, and the blank ZoU form.

**Not public:**

- `Číslo RP` — registration card numbers. **Required on the ZoU.**
- Jersey numbers.
- Dates of birth.
- Player photographs.
- Full squad lists — only players who have actually appeared show up in the public table.
- Suspension state.
- Referee delegation lists.

> **The entire roster dependency reduces to one item: RP numbers.** Everything else needed to demonstrate the flow is already public. This has a direct practical consequence for how the ask to PSMF should be framed — see §4.2.

## 2.10 The existing app (golblok / GoalTrack)

- Native Android, Kotlin, Jetpack Compose, Hilt, Clean Architecture + MVVM. 8,825 lines of Kotlin across 37 files.
- Largest single file: `ui/MatchScreen.kt` at **1,721 lines**.
- Persistence is manual `org.json` to internal storage plus DataStore preferences. No database, no server, no accounts.
- Live on Google Play.
- **golblok remains a separate, live product with its own repository and roadmap.** The league app is *inspired by* it. Reuse is therefore at the level of concepts, logic and interaction design — not a fork, and no shared code.

## 2.11 Project reality

- Team of **2–3 people**: a project manager (originator of the match-tracking idea; coaches a youth team in a smaller town), one developer, and one IT tester/analyst. Two of the three play in the league.
- The relationship with PSMF is **live**: contact with an organiser and with a referee who oversees other referees.
- PSMF has verbally **offered access to the team/player database**.
- The engagement is a **prototype / pitch**, not a commissioned build. No agreement, no fee. Possible monetisation in roughly a year if the league is satisfied.
- There is **no deadline**. The stated posture is deliberately unhurried: the organisation is conservative and needs time to understand and adapt.
- The aim for autumn 2026 is **not a full pilot** but demonstrating that the process can be done, or improved, across a limited number of matches.

## 2.12 Decisions already taken by the project owner

Recorded so that a later session does not reopen them:

- **Android and iOS both**, for the referee app.
- **Offline is a hard requirement.**
- **Live pitch-side recording is the primary path.**
- **No accounts, no logins, no formal digital signature.** The app replaces paper; it does not introduce an identity system.
- **No mandate** on referee operating system or device.
- A web app for the team-facing side is worth validating rather than assumed.
- The captains' signature is understood to be **a formality in practice** — the project owner has never seen a dispute arise from it.
- **Post-match editing must be possible** — for recording errors, wrong goal attribution, or a match kept on paper because of rain.

---

# 3. ASSUMED

Each assumption carries what breaks if it turns out to be wrong.

## 3.1 A stable, semi-professional referee pool exists across the pilot group

The regulations describe refereeing as a duty rotated among delegated teams. The project owner reports that in practice a network of amateur referees covers multiple matches for a fee. Both are true; the ratio is unknown and probably varies by level — 1.liga buys officiating outright at 6,000 CZK a season, and lower divisions likely lean more on genuinely ad-hoc volunteers.

> **Breaks if wrong:** the app has one user profile (a repeat referee who learns it) rather than two (a repeat referee plus a one-off player who has never opened it). If lower divisions are mostly ad-hoc, first-run experience becomes the dominant design constraint, not a secondary one, and every assumption about familiarity, retained state and prior setup fails.

## 3.2 The captains' confirmation can be a lightweight tap

The paper signature is a pen mark that nobody verifies, made in the physical presence of the referee and the opposing captain. Its force comes from presence and witness, not from identity. Parity with paper is therefore achievable with no accounts: both captains confirm on the referee's device, in each other's presence.

> **Breaks if wrong:** if PSMF or STDK regard the signature as evidentially meaningful — for instance if a protest has ever turned on one — then the pilot needs something stronger, and "no accounts" becomes untenable. The whole no-identity premise rests on this.

## 3.3 The mandatory referee commentary is not written at the pitch

The form requires cards to be entered before the captains sign, but says nothing similar about the commentary. The worked example is ~400 characters of prose. It is likely written after the fact.

> **Breaks if wrong:** if the commentary must be complete before the captains sign, the app must solve on-pitch free-text entry in the dark and the cold — the hardest usability problem in the product, and the one place where a phone is plainly worse than a pen. Note this assumption is in direct tension with a pitch-side lock; see §5.3.

## 3.4 The player database held by PSMF contains RP numbers and current team membership

PSMF has offered access, so something structured exists. Its schema is unknown.

> **Breaks if wrong:** if the digital database is thinner than the physical card cabinet — say, names and teams but no RP numbers — then the one dependency that cannot be satisfied from public data is unmet, the referee is back to hand-entry, and the product degrades to a nicer golblok.

## 3.5 The systems vendor also employs or manages the transcription crew

The project owner's words: *"I guess it is the same company/guy that oversees inputting the match records."* Unconfirmed.

> **Breaks if wrong in one direction:** if they are separate parties, the political risk drops sharply and integration becomes an ordinary technical conversation. **If confirmed:** the party whose billable work the product eliminates is also the party PSMF will consult about it. See §4.1.

## 3.6 Jersey number belongs to the appearance, not to the player

Stated by the project owner: numbers change between matches. Corroborated indirectly — the ZoU carries a referee rating `Č` for whether a team's shirts are properly numbered at all, implying numbering is loose.

> **Breaks if wrong:** minor. If numbers are in fact stable, defaulting them per player is a simplification, not a defect. Low risk either way; the safe modelling choice is per-appearance with a default.

## 3.7 The organiser contact can decide

The project owner reads him as speaking with authority: *"it really is an amateur league growing a little bit over their heads."*

> **Breaks if wrong:** the pitch is being made to a messenger. Proposals get routed to the VV, to STDK, or — worst case — to the vendor for assessment, and the timeline stops being the project's to control.

## 3.8 Suspension state is derivable

STDK publishes decisions within two days on the public site. Even if the database does not carry suspension state, published decisions may be parseable.

> **Breaks if wrong:** the app cannot warn a referee that a player is banned, and one of the few genuine capability gains over paper is lost. Not fatal — paper cannot do it either.

## 3.9 Matches run seven days a week league-wide

The project owner reports Monday to Sunday. The sampled group showed Monday, Tuesday, Thursday and Friday only.

> **Breaks if wrong:** affects peak-concurrency estimates only, and only upward by a modest factor. The ceiling remains the pitch count.

## 3.10 The team count is ~720 for HL, and the brief's ~900 includes other competitions

Derived: 60 groups × 12 teams. The brief said ~900. Veteran, futsal and cup competitions plausibly account for the difference.

> **Breaks if wrong:** volume estimates shift by ~25%, which changes nothing material. Recorded for honesty rather than risk.

---

# 4. The two findings that most affect how this project should proceed

Not assumptions, and not questions — conclusions from the material above. They belong here because a later session reading only KNOWN and ASK would miss them.

## 4.1 The systems vendor is the principal risk, and it is political rather than technical

The same external party runs the database, runs the website, and oversees match-record input. Therefore:

- The transcription work the product eliminates is plausibly their billable work, or done by people who answer to them.
- They control the integration surface any result-submission feature would need.
- They are the obvious party for PSMF to ask for a technical assessment of the proposal.
- They are the obvious party to respond that they could build it themselves.

Also note what this does to the database offer: **PSMF owns the data but does not hold it.** An offer of "database access" may be one PSMF cannot deliver without asking the very person whose position the product weakens.

The project's own read — that nothing here is beyond a REST API — is technically correct and beside the point. The blocker was never going to be technical.

**The mitigation is to design the pilot so that it needs nothing from the vendor at all.** §2.9 establishes that this is achievable: fixtures, teams, kit colours and player names are public; the output channel (email to psmf@psmf.cz) already exists and is already accepted; the only gap is RP numbers, and for one group that is ~180 rows. A pilot built this way proves value before any conversation with the vendor is necessary, and moves that conversation from a request into a negotiation backed by evidence.

**Corollary for the "data integration for inputting results" idea in the original brief:** it is the one component requiring vendor cooperation, and building it first means negotiating from the weakest possible position. A generated ZoU delivered by email achieves the pilot's goal with no integration at all.

## 4.2 The ask to PSMF should be a spreadsheet, not database access

"Database access" sounds like a project. It implies permissions, liability and schema discussions, and it needs the vendor. **"A list of names and RP numbers for the twelve teams in one group"** is ~180 rows, sounds like a favour, and is something a single organiser can decide alone. The framing of this request materially affects whether it succeeds, and everything else the app needs can be bootstrapped from the public site.

---

# 5. Design tensions to resolve before building

Stated as tensions, not as answers. Each one requires a decision, and each is influenced by facts still in ASK.

## 5.1 Lineup capture without handing over the phone

The project owner's objection stands: referees will not lend their phone for minutes at a time.

The reframe that rescues it: on paper the captain writes ten names, ten RP numbers and ten jersey numbers. In the app the squad is already known, so the task becomes *mark who is present* — and since most turn up, in practice *mark who is absent*, three to five taps. That is a job the referee can do holding their own device with the captain beside them. It is less work than paper for both parties and nothing changes hands.

Jersey numbers are where it slows: they change between matches (§3.6). Defaulting to a player's last known number and correcting the exceptions is the obvious approach, but the number of corrections per match is unknown and is exactly the kind of thing the pilot should measure.

Alternatives for later, if reading names aloud proves insufficient:

- the captain's own device hands the lineup to the referee's phone offline;
- advance submission via web, with the referee pre-fetching.

Both are deferred. Note only that the QR machinery in golblok maps onto the first of these — not as roster sharing between coaches, which dies with the server, but as offline device-to-device handoff, a different job it happens to suit.

## 5.2 Pre-fetch versus offline

Offline is required because referees may not use mobile data at all — a preference, not a coverage problem (the pitches are urban, lit and inside Prague). But rosters live on a server, so they must arrive on the device before the referee reaches the pitch, and a referee with data switched off cannot fetch them there.

This is a real constraint that a later technology session must address, and it points to the volumes in §2.3: one group is 12 teams and ~180 players, tens of kilobytes without photographs. The whole group is small enough to be resident on the device at all times, which would remove per-match pre-fetch as a problem entirely. Whether that generalises past a pilot is a separate question and depends on decisions not yet made.

## 5.3 Locking versus versioning

The offer text promises the report is locked once all parties confirm: *"Po potvrzení všech stran je zápis uzavřen a uzamčen proti dalším změnám."* The project owner also requires post-match editing (§2.12). These conflict.

The resolution that satisfies both: **do not lock — version.** Snapshot the report at the moment the captains confirm, allow amendments afterwards, and deliver both the signed snapshot and the current version together with a record of what changed, when and by whom. That is the offer text's own *"auditní evidence změn a potvrzení"* doing real work.

The freeze point does not need to be invented: **Monday 19:00** is already the rule on the form. Amendable until then, frozen after — exactly like paper going into the box.

Worth noting in the pitch: this is a straight improvement on paper. Today, if a referee alters something after the captains have signed, nobody knows. In the app, PSMF sees the delta.

## 5.4 Live recording versus later entry

Live pitch-side recording is the decided primary path (§2.12). But a rain fallback exists, and any fallback that is easier will attract usage.

If a significant share of matches end up entered afterwards, three things change: pitch-side captain confirmation cannot be required (the captains are gone), offline matters much less (the referee is at home), and the team-side lineup submission becomes considerably more important (a referee reconstructing a match at 22:30 will not remember twenty names).

Both modes must exist. Which one dominates should be **measured in the pilot, not predicted** — instrument the share of matches recorded live versus entered later. That measurement is the single most informative number the pilot can produce for the eventual technology decision.

## 5.5 The recap screen carries more weight than the paper did

The captains' signature is a formality (§2.12) and there is no reason to make it heavier. But on paper a captain signs a single sheet they can see in full. In an app they will confirm a recap screen, and whatever is not on that screen is not being checked. This is a design constraint, not a process one.

---

# 6. Entity sketch

Domain entities and relationships only. Nothing here implies a storage technology, a schema, or a service boundary.

```
Season ──┬─< Division (8 levels) ──< Group (60 in HL) ──< TeamEntry
         └─< Fixture

Club ──< Team ──< SquadMembership >── Player
                                        │
Venue ──< Fixture >── Team (home, away)  │
             │                           │
             └──< Match ──< Lineup ──< Appearance >──┘
                     │
                     ├──< MatchEvent
                     ├──< RefereeAssignment >── delegating Team
                     ├──< Confirmation (captain ×2, referee)
                     ├──< ReportVersion
                     └──< Result ──> Standings (derived)

Player ──< DisciplinaryRecord ──> Suspension
```

**Notes on entities that are not obvious:**

| Entity | Note |
|---|---|
| `Group` | The unit of competition. Yellow-card accumulation is per group per season (§2.6), so it is not merely a label. |
| `TeamEntry` | A team's participation in one group in one season. Teams are promoted and relegated, so team ↔ group is season-scoped. |
| `SquadMembership` | The registered squad, ~10–15 players. Season-scoped. Subject to transfers and loans mid-season. |
| `Lineup` | Who actually turned up for one match. Distinct from the squad, and the thing the captain confirms. Typically ~8 of the squad. |
| `Appearance` | One player in one lineup. **Carries the jersey number** (§3.6) and carries the RP-number-or-date-of-birth value with a flag for which it is (§2.5). This is where the pending-registration exception lives. |
| `MatchEvent` | Goal, yellow, red. Minute is **not an integer** — `30´+` and `60´+` are valid values (§2.5). A red must record whether it was straight or a second yellow, and must carry a free-text reason. A goal may have no scorer. |
| `RefereeAssignment` | Main and assistant, each optionally flagged as a licensed hire (`R`), plus the **delegating team** — which is who gets fined for a bad report (§2.5). |
| `Confirmation` | Two captain confirmations plus one referee confirmation, each with a timestamp. Captaincy may be delegated to a deputy. |
| `ReportVersion` | Required by §5.3. The signed snapshot and any subsequent amendments, with authorship and time. |
| `Result` | Has a lifecycle: provisional → confirmed (§2.4). May be overridden by forfeit, which substitutes a 0:6 or 6:0 record independent of events. |
| `Suspension` | Derived from card accumulation (even-numbered yellow totals) or imposed by STDK after a red. Not computable from a single match. |

**Relationships that change mid-season** — these are the ones that make season-scoping mandatory rather than tidy:

- Players transfer between clubs (one transfer per season, requires both clubs' consent, blocked once the player has appeared).
- Players are loaned down one tier, maximum two per match.
- Teams withdraw, or are removed after three forfeits, and are then demoted at least two levels.
- Fixtures are rescheduled by PSMF, always before the final round.
- Suspensions come and go.

**On conflicting information between two people:** worth recording that this failure mode does not exist in the current process and should not be introduced. There is exactly one recorder — the referee — and the captains confirm what the referee wrote. Protests are noted by the referee in the report and pursued afterwards through STDK, not resolved at the pitch. A design in which two parties record independently and must be reconciled would be inventing a problem the paper process does not have.

---

# 7. Reuse audit — golblok, screen by screen

golblok is a separate product with its own repository (§2.10). "Reuse" here means reusing concepts, logic and interaction design, not code. Assessed against the ZoU spec in §2.5 and the rules in §2.6.

## Survives

| Part | Why |
|---|---|
| **Live match console** (`MatchScreen`) — the interaction model | Tap-to-log against a player list, tabbed by team, is exactly right for pitch-side use. This is the genuinely reusable asset. Note it is 1,721 lines in one file, so the asset is the design, not the implementation. |
| **Match timer + foreground service** | 2 × 30 gross time with a running clock and referee-added time matches the existing behaviour closely. Auto-pause at half-time maps to the 30-minute break. |
| **Undo of the last event** | A referee correcting a mis-tap on a phone in the cold needs this more than a coach does, not less. |
| **Crash / kill recovery of an in-progress match** | Was a nice-to-have. Here it is close to product-critical: losing a match record is the failure that ends the pilot. |
| **Match log sheet — post-hoc event correction** | Directly serves the post-match editing requirement (§2.12) and the rain-fallback path (§5.4). |
| **Left-handed layout mirroring** | Cheap, and the user population skews older and less tolerant of fiddly UI. |
| **Sent-off players visually disabled** | Correct behaviour, and now carries regulatory weight. |
| **Czech-first localisation** | The user base is Czech. Already the default. |

## Needs rework

| Part | Why |
|---|---|
| **Event model** (`MatchEventType`) | Must split red into *straight* and *second yellow* (`2. ŽK`), because suspension arithmetic depends on it (§2.6). Must add a **mandatory free-text reason** to every card. Must allow a goal with no scorer. |
| **Minute representation** | Currently numeric. Must represent `30´+` and `60´+` (§2.5). |
| **Card progression logic** | Auto-red on second yellow is already implemented and roughly right, but must now *record which yellows were involved* so that the "two yellows in one match count as zero" rule can be applied downstream. |
| **Player status model** (`ON_FIELD` / `SUBSTITUTE` / `ABSENT`) | The concept survives but its meaning changes: it becomes the lineup-confirmation mechanism attested by the captain, with an audit consequence, rather than a coach's private convenience. |
| **`Player.number`** | Must move to the appearance (§3.6). |
| **Player identity fields** | Must add `Číslo RP` or date of birth, with a flag for which, and an exception marker for pending registrations (§2.5). |
| **Substitution tracking** | Substitutions are unlimited and rolling, and — critically — **the ZoU has no substitutions section**. They are not part of the required output. Retain only if minutes-played is wanted for its own sake; it is not needed for the report. |
| **`MatchRecapScreen`** | Becomes the confirmation surface that captains sign against (§5.5), which is a higher bar than a coach's summary. |
| **Match setup** | Currently two dropdowns over local teams. Becomes fixture selection from a real schedule, plus referee identification, plus the delegating team. |
| **Sharing / export** | The formatted text report becomes a generated ZoU in PSMF's own layout, plus a machine-readable payload (§4.1). |
| **Timer** | Needs the **10-minute power-play** after a dismissal (§2.6) — a second, independent countdown the app does not currently have. |

## Dies

| Part | Why |
|---|---|
| **Local team creation** (`TeamsScreen` create/delete/archive) | Teams are league entities. A referee inventing a team is a data-integrity failure, not a feature. |
| **QR roster sharing between coaches** *(as a sharing model)* | Existed precisely because there was no backend. There is now a league authority and a roster of record. The **mechanism** may return in a different role (§5.1), but the model dies. |
| **`ScanTeamScreen` / CameraX + ZXing** *(in its current role)* | Follows the above. Retain only if device-to-device lineup handoff is chosen later. |
| **`MockTeamRepository` and its three seeded default teams** | Obviously. |
| **User-configurable match rules** (duration, periods, players per team, venue defaults) | The league sets these. 2 × 30, 5+1, and a venue from the fixture. A referee changing the half length is a defect. |
| **Assist tracking** | **There is no assist column on the ZoU.** Dead weight. |
| **Assist toggle, fixed-team-size toggle, substitutions toggle in Settings** | Follows from the above. |
| **`Match.venue` as free text** | Becomes a venue reference from the fixture (~35 known pitches with codes). |
| **Player stats calculation as the app's output** | Standings and statistics are PSMF's to produce, from confirmed results. The app's output is a report, not a league table. `CalculatePlayerStatsUseCase` survives only if minutes-played is wanted for its own sake. |
| **Home screen match history as the primary surface** | Becomes "my fixtures" — the matches this referee is assigned or has selected — rather than a local archive. |

## Absent entirely — no counterpart exists in golblok

These are new build, and two of them are not small:

- `Číslo RP` / date-of-birth capture and the pending-registration exception.
- **The mandatory referee commentary**, plus the `NH` / `Čd` / `Č` / `B` team-discipline ratings. Nothing in golblok resembles this, it is mandatory, and it is the hardest thing to enter on a phone (§3.3, §5.3).
- Captain confirmation, before kickoff and at full time.
- Report versioning and audit trail (§5.3).
- Fixture ingestion.
- ZoU generation in PSMF's layout.
- Explicit "no cards were issued" affirmation, since the paper form requires the boxes to be struck through.
- Half-time score and explicit match winner as recorded fields.
- Waiting time (`Čd`) when a team is not ready at the official kickoff.

---

# 8. ASK

Questions that must be answered before the technology decision is sound. Grouped by recipient. Czech phrasing is provided for anything going to PSMF and can be sent as-is.

## 8.1 To the organiser contact at PSMF — data

**A1. The player database.** *Blocks: the entire roster dependency (§3.4). Highest priority.*

> Jakou formu má vaše databáze hráčů a týmů? Jde o databázi, tabulku v Excelu, nebo něco jiného? Konkrétně nás zajímá, zda u každého hráče evidujete číslo RP, jméno a příjmení, datum narození, číslo dresu a příslušnost k týmu pro aktuální sezónu.

**A2. The minimal ask.** *Ask this instead of asking for access (§4.2).*

> Pro ověření by nám stačil jednorázový export jedné skupiny — přibližně 12 týmů, tedy zhruba 180 řádků. Stačilo by jméno, příjmení a číslo RP u každého hráče. Bylo by možné takový seznam získat?

**A3. Suspension state.** *Blocks §3.8 and one of the few genuine gains over paper.*

> Evidujete v databázi také aktuální stavy zákazů startu (tresty po ŽK a ČK), nebo se tato informace vede pouze v rozhodnutích STDK zveřejňovaných na webu?

**A4. Registration timing.** *Blocks sizing of the pending-registration case (§2.5).*

> Jak dlouho trvá, než se nově registrovaný hráč objeví v databázi? A jak často se v praxi stává, že hráč nastoupí dříve, než je zaregistrován — řádově kolik případů za kolo nebo za sezónu?

## 8.2 To the organiser contact at PSMF — process

**A5. The paper original.** *Blocks whether the pilot removes any work from the referee at all.*

The propozice already permits emailing a photographed ZoU, but requires the paper original to follow.

> Formulář i propozice umožňují poslat vyfocený zápis e-mailem s tím, že originál se doručí dodatečně. Bylo by možné u pilotních utkání od papírového originálu upustit, pokud bude k dispozici úplný elektronický zápis?

**A6. The mandatory commentary.** *Blocks §3.3 and §5.3 — the hardest usability problem in the product.*

> Jak přísně se vyžaduje povinný komentář rozhodčího? Musí být vyplněn ještě před podpisem kapitánů, nebo ho rozhodčí běžně doplňuje až po utkání? A stačí v praxi stručné shrnutí?

**A7. The signature.** *Blocks §3.2 — the entire no-accounts premise.*

> Stalo se někdy, že by podpis kapitána na zápisu byl předmětem sporu nebo protestu? Ptáme se proto, abychom věděli, jak silné elektronické potvrzení je potřeba — zda stačí prosté potvrzení na displeji, nebo je nutné něco průkaznějšího.

**A8. Format of the export.** *Blocks §4.1.*

> V jaké podobě by pro vás bylo zpracování zápisu nejjednodušší — PDF ve stejném vzhledu jako dnešní papírový zápis, tabulka (CSV/Excel), nebo obojí?

**A9. Identity checks.** *The regulations forbid phone photographs for konfrontace; the project has ambitions here.*

> Soutěžní řád vyžaduje při konfrontaci fyzický registrační průkaz a výslovně nepřipouští fotografii v mobilu. Uvažuje PSMF do budoucna o změně tohoto pravidla, nebo má fyzický průkaz zůstat jediným průkazným dokladem?

## 8.3 To the organiser contact at PSMF — arrangements

**A10. Data protection.** *Blocks the legal footing of the whole pilot. See §8.6.*

> Kdo u vás odpovídá za ochranu osobních údajů hráčů? Máme za to, že správcem údajů je PSMF. Pokud bychom měli pracovat s daty hráčů, potřebovali bychom to ošetřit zpracovatelskou smlouvou — máte takovou smlouvu uzavřenou i s dodavatelem, který provozuje databázi a web?

**A11. The systems vendor.** *Blocks §4.1. Ask carefully — this is a political question.*

> Kdo u vás zajišťuje provoz webu a databáze a kdo má na starosti přepis zápisů do systému? Rádi bychom věděli, s kým bychom případně řešili technické napojení.

**A12. Pilot scope and timing.** *Blocks planning.*

> Kdyby se pilot osvědčil, dovedete si představit jeho nasazení v jedné skupině od jarní sezóny 2027? A kdo by o tom rozhodoval — vy, výkonný výbor, nebo někdo další?

## 8.4 To the referee contact

**A13.** How long does filling in a ZoU actually take — before the match, and after it?

**A14.** Where is the commentary written in practice: at the pitch, in the car, or at home?

**A15.** How often does a player turn up without their registration card, so that a date of birth is written instead?

**A16.** How many of the squad typically turn up — is "mark the absentees" genuinely fewer taps than "select who is playing"?

**A17.** How many jersey numbers differ from the previous match, typically?

**A18.** Would you use a phone for this in the rain, or keep paper and enter it afterwards? *This is the §5.4 question, and it should be asked of several referees, not one.*

**A19.** What actually goes wrong with the paper reports today — what gets sent back, queried, or fined?

## 8.5 To the project team — internal

**A20.** Who is the vendor? Name them before the pitch, not after (§4.1).

**A21.** Is the organiser contact a decision-maker or a messenger (§3.7)? Test it with a small request — A2 is a good one — and watch whether he answers or forwards.

**A22.** Which group is the pilot target? Two of the three team members play in the league; their own group is the obvious candidate and makes shadow recording available immediately.

**A23.** Are you willing to shadow-record autumn 2026 matches in parallel with the paper ZoU? It needs no permission from anyone, uses only public data, and produces the side-by-side comparison that the pitch requires.

**A24.** What happens if PSMF says yes and wants it league-wide? ~360 matches a week and one developer. Have a position ready.

**A25.** Who pays for what — Apple Developer Program (~$99/yr), Google Play (one-off $25), hosting, and the time? Currently unfunded and speculative (§2.11).

## 8.6 Legal — needs an answer, possibly professional

**A26.** **Controller versus processor.** PSMF has been collecting this data for decades and is the controller. The question is whether the project acts as a **processor** under PSMF's instructions with a *zpracovatelská smlouva*, or operates a service holding player data in its own right. These carry materially different obligations. Cheap to settle now, expensive to unpick later. Note that the vendor is already in a processor position, and it is not obvious that a written agreement exists there either — which is an opening to propose the shape rather than inherit it.

**A27.** **Minors.** The minimum age is 15, so 15-, 16- and 17-year-olds are in scope. Confirm the legal basis PSMF relies on for processing (it is unlikely to be consent — league administration more plausibly rests on contract or legitimate interest) and whether anything additional applies to under-18s.

**A28.** **Identifier discrepancy — must be resolved.** The Soutěžní řád's description of required match-report content refers to birth numbers (*rodné číslo*). The actual form has only `Číslo RP`, with **date of birth** as the fallback. These are very different from a data protection standpoint — *rodné číslo* is a national identifier and a significantly heavier obligation. **Confirm from a completed real ZoU which is actually written.** This document assumes date of birth, on the strength of the form and its worked example.

**A29.** **Photographs.** The project has expressed interest in showing player photographs so that opposing teams can verify identity. This is a material escalation: photographs of players including minors, cached on personal devices the league does not control. It also runs into A9 — the regulations currently reject phone photographs for konfrontace, so the feature would have no regulatory standing even if built. Deferred, and should stay deferred until A9 and A27 are answered.

**A30.** **Retention and deletion.** No privacy policy is published on psmf.cz. Ask what retention periods apply and how deletion requests are currently handled. Expect the answer to be that nothing is defined.

---

# 9. INPUTS TO THE TECHNOLOGY DECISION

Facts relevant to the later choice, assembled without making it. No stack, storage, platform or sync strategy is implied here.

## 9.1 Constraints that are settled

| Constraint | Detail | Source |
|---|---|---|
| Platforms | Android and iOS, referee-facing | §2.12 |
| Offline | Hard requirement; driven by users who keep mobile data off, not by coverage | §2.12, §5.2 |
| Identity | No accounts, no logins, no formal digital signature | §2.12 |
| Primary path | Live pitch-side recording | §2.12 |
| Editing | Post-match amendment required | §2.12, §5.3 |
| Team-facing | Not required for the pilot — the lineup is produced at the pitch and always has been | §2.4, §5.1 |
| Build capacity | 2–3 people, one of them the developer | §2.11 |
| Codebase | Green field. golblok is a separate product; no shared code | §2.10 |
| Deadline | None. Deliberately unhurried | §2.11 |

## 9.2 Sizing

- Pilot: 1 group = 12 teams, ~180 players, 66 matches per half-season, 6 matches per week.
- League-wide (HL): ~720 teams, ~7,000–11,000 players, ~360 matches per week, ~7,900 per year.
- Peak concurrency is bounded by ~35 pitches; realistically 20–30 simultaneous matches, in a 19:00–21:00 window.
- Per match: ~20–40 records; tens of kilobytes without photographs.
- One group's full roster data is small enough to be permanently resident on a device. Whether the same holds league-wide is a question for that session.

## 9.3 Integration surface

- **Fixtures**: public, structured, per team and per group. Obtainable without cooperation.
- **Rosters**: not public. RP numbers are the sole hard dependency.
- **Result submission**: the target system is unknown; there is an admin interface nobody on the project has seen. Email to psmf@psmf.cz is an already-accepted channel and requires no integration.
- **Output artefact**: the ZoU in PSMF's own layout, specified field by field in §2.5.

## 9.4 The measurement the pilot must produce

Whatever else it does, the pilot should record the share of matches **recorded live** versus **entered afterwards** (§5.4). That single number changes the relative weight of offline capability, pitch-side confirmation and the team-facing surface — and therefore bears directly on the technology decision. It cannot be answered by reasoning; it has to be observed.

---

# 10. Source index

| Source | URL |
|---|---|
| Documents index | https://www.psmf.cz/dokumenty/ |
| Rules of the game 5+1 | https://www.psmf.cz/dokumenty/pravidla-maleho-fotbalu-5-1/ |
| Competition code 5+1 | https://www.psmf.cz/dokumenty/soutezni-rad-5-1/ |
| Disciplinary code 5+1 | https://www.psmf.cz/dokumenty/disciplinarni-rad-5-1/ |
| Fee, fine and reward schedule | https://www.psmf.cz/dokumenty/sazebnik-poplatku-pokut-a-odmen/ |
| Competition proposition, HL (15+) | https://www.psmf.cz/vyveska/propozice-souteze-pro-hl/ |
| **Match report form (.xlsx, with worked example)** | https://www.psmf.cz/vyveska/zapis-o-utkani/ |
| Autumn 2026 season | https://www.psmf.cz/souteze/2026-hanspaulska-liga-podzim/ |
| Sampled group, 5.liga A | https://www.psmf.cz/souteze/2026-hanspaulska-liga-podzim/5-a/ |
| Sampled team page, pre-season | https://www.psmf.cz/souteze/2026-hanspaulska-liga-podzim/5-a/tymy/celtic-thk/ |
| Sampled team page, played-out season | https://www.psmf.cz/souteze/2026-hanspaulska-liga-jaro/1-a/tymy/dynamo-uk/ |
| Pitches | https://www.psmf.cz/hriste/ |
| Contact | https://www.psmf.cz/vyveska/kontakt/ |

A local copy of the match report form was downloaded during this analysis for field extraction. It is not committed to this repository; it is retrievable from the URL above.
