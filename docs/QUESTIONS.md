# Questions

From the business analysis §8. Czech text is ready to send as-is.

**Status key:** `sent` not sent · `sent` awaiting reply · `answered` · `blocked`

---

## To the organiser contact at PSMF — data

### A1 · What form does the player database take? — `sent`
Blocks the entire roster dependency. Highest-priority question in the analysis.

> Jakou formu má vaše databáze hráčů a týmů? Jde o databázi, tabulku v Excelu,
> nebo něco jiného? Konkrétně nás zajímá, zda u každého hráče evidujete číslo RP,
> jméno a příjmení, datum narození, číslo dresu a příslušnost k týmu pro aktuální
> sezónu.

### A2 · The minimal ask — one group, ~180 rows — `sent`
Ask this **instead of** asking for database access. A favour one organiser can
grant, not a project needing permissions and the vendor. Also doubles as the test
of whether the contact decides or forwards (A21).

> Pro ověření by nám stačil jednorázový export jedné skupiny — přibližně 12 týmů,
> tedy zhruba 180 řádků. Stačilo by jméno, příjmení a číslo RP u každého hráče.
> Bylo by možné takový seznam získat?

### A3 · Is suspension state held anywhere? — `sent`
> Evidujete v databázi také aktuální stavy zákazů startu (tresty po ŽK a ČK), nebo
> se tato informace vede pouze v rozhodnutích STDK zveřejňovaných na webu?

### A4 · How long until a new player appears in the database? — `sent`
> Jak dlouho trvá, než se nově registrovaný hráč objeví v databázi? A jak často se
> v praxi stává, že hráč nastoupí dříve, než je zaregistrován — řádově kolik
> případů za kolo nebo za sezónu?

---

## To the organiser contact at PSMF — process

### A5 · Can the paper original be dropped for pilot matches? — `sent`
Blocks whether the pilot removes any work from the referee at all. If paper must
still follow, the app *adds* work rather than replacing it.

> Formulář i propozice umožňují poslat vyfocený zápis e-mailem s tím, že originál
> se doručí dodatečně. Bylo by možné u pilotních utkání od papírového originálu
> upustit, pokud bude k dispozici úplný elektronický zápis?

### A6 · How strictly is the mandatory commentary enforced? — `sent`
Decides the hardest usability problem in the product — whether ~400 characters of
prose must be typed at the pitch, in the dark and cold, before the captains sign.

> Jak přísně se vyžaduje povinný komentář rozhodčího? Musí být vyplněn ještě před
> podpisem kapitánů, nebo ho rozhodčí běžně doplňuje až po utkání? A stačí v praxi
> stručné shrnutí?

### A7 · Has a captain's signature ever been disputed? — `sent`
The entire no-accounts premise rests on the answer being no.

> Stalo se někdy, že by podpis kapitána na zápisu byl předmětem sporu nebo
> protestu? Ptáme se proto, abychom věděli, jak silné elektronické potvrzení je
> potřeba — zda stačí prosté potvrzení na displeji, nebo je nutné něco
> průkaznějšího.

### A8 · What export format suits you? — `sent`
Determines what actually gets built as the deliverable.

> V jaké podobě by pro vás bylo zpracování zápisu nejjednodušší — PDF ve stejném
> vzhledu jako dnešní papírový zápis, tabulka (CSV/Excel), nebo obojí?

### A9 · Identity checks and phone photographs — `sent`
> Soutěžní řád vyžaduje při konfrontaci fyzický registrační průkaz a výslovně
> nepřipouští fotografii v mobilu. Uvažuje PSMF do budoucna o změně tohoto
> pravidla, nebo má fyzický průkaz zůstat jediným průkazným dokladem?

---

## To the organiser contact at PSMF — arrangements

### A10 · Who is responsible for data protection? — `sent`
Blocks the legal footing of the whole pilot. Note the opening: if no processor
agreement exists with the current vendor either, you get to propose the shape
rather than inherit it.

> Kdo u vás odpovídá za ochranu osobních údajů hráčů? Máme za to, že správcem
> údajů je PSMF. Pokud bychom měli pracovat s daty hráčů, potřebovali bychom to
> ošetřit zpracovatelskou smlouvou — máte takovou smlouvu uzavřenou i s
> dodavatelem, který provozuje databázi a web?

### A11 · Who runs the website and database, and who retypes the reports? — `sent`
**Ask carefully — this is a political question, not a technical one.** The same
party plausibly loses billable work if this product succeeds.

> Kdo u vás zajišťuje provoz webu a databáze a kdo má na starosti přepis zápisů do
> systému? Rádi bychom věděli, s kým bychom případně řešili technické napojení.

### A12 · Could a pilot run in one group from spring 2027, and who decides? — `sent`
> Kdyby se pilot osvědčil, dovedete si představit jeho nasazení v jedné skupině od
> jarní sezóny 2027? A kdo by o tom rozhodoval — vy, výkonný výbor, nebo někdo
> další?

---

## To the referee contact — `sent`

Ask **A18 of several referees**, not one. It decides how much offline capability
is actually worth building.

| | Question |
|---|---|
| A13 | How long does filling in a ZoU actually take — before the match, and after it? |
| A14 | Where is the commentary written in practice: at the pitch, in the car, or at home? |
| A15 | How often does a player turn up without their card, so a date of birth is written instead? |
| A16 | How many of the squad typically turn up — is "mark the absentees" genuinely fewer taps than "select who is playing"? |
| A17 | How many jersey numbers differ from the previous match, typically? |
| A18 | **Would you use a phone for this in the rain, or keep paper and enter it afterwards?** |
| A19 | What actually goes wrong with paper reports today — what gets sent back, queried, or fined? |

**Better than asking:** watch one referee fill in a real ZoU start to finish. It
answers most of these at once.

---

## Internal — the project team

| | Question | Status |
|---|---|---|
| A20 | Who is the systems vendor? Name them before the pitch, not after. | `sent` |
| A21 | Is the organiser contact a decision-maker or a messenger? Test with A2 and watch whether he answers or forwards. | `sent` |
| A22 | Which group is the pilot target? Two of three team members play in the league — their own group is the obvious candidate. | `sent` |
| A23 | Shadow-record autumn 2026 matches alongside the paper ZoU? Needs no permission, uses only public data. Season runs to **7 December**; next window is March 2027. | `sent` |
| A24 | What if PSMF says yes and wants it league-wide? ~360 matches a week, one developer. Have a position ready. | `sent` |
| A25 | Who pays for what — Apple $99/yr, Play $25 one-off, hosting, time? Currently unfunded. | partly — company accounts exist |

---

## Legal — may need professional advice

### A26 · Controller versus processor — `sent`
PSMF has been collecting this data for decades and is the controller. Are we a
**processor** under their instructions with a *zpracovatelská smlouva*, or
operating a service holding player data in our own right? Materially different
obligations. Cheap to settle now, expensive to unpick later.

### A27 · Minors — `sent`
Minimum age is 15, so 15–17 year olds are in scope. Confirm the legal basis PSMF
relies on (unlikely to be consent — more plausibly contract or legitimate
interest) and whether anything additional applies to under-18s.

### A28 · Identifier discrepancy — MUST BE RESOLVED — `sent`
The Soutěžní řád refers to *rodné číslo*; the actual form has only `Číslo RP` with
date of birth as the fallback. These are very different from a data-protection
standpoint — *rodné číslo* is a national identifier with significantly heavier
obligations. **Confirm from one completed real ZoU which is actually written.**
The analysis assumes date of birth on the strength of the form and its worked
example.

**Largest legal exposure in the project. Settled by one photograph.**

### A29 · Photographs — deferred
Showing player photos for identity verification is a material escalation —
photographs of players including minors, cached on devices the league does not
control. Also blocked by A9: the regulations currently reject phone photographs
for konfrontace, so the feature would have no regulatory standing even if built.

### A30 · Retention and deletion — `sent`
No privacy policy is published on psmf.cz. Ask what retention periods apply and
how deletion requests are handled. Expect the answer to be that nothing is defined.
