# Numbers

Every number that appears in the code and the tests, and where it came from.

They fall into four kinds, and telling them apart is the point of this file:

- **Imposed** by the brief — the fee, the rate, the currencies, the ten events.
- **Chosen** by us, where the brief was silent. These are the ones open to
  argument, and [AMBIGUITIES.md](AMBIGUITIES.md) argues them.
- **Derived** — results the scenario produces. Changing one means the replay
  changed, not that a setting changed.
- **Fixtures** picked to sit exactly on a boundary a test needs to prove.

Nothing here is a tuning knob. Where a fixture looks arbitrary, the note says
what property would be lost if it moved.

## Money and currencies

| Number | Where it comes from |
| --- | --- |
| AED: 2 decimals | Imposed. Fewer would lose the cents. |
| BHD: 3 decimals | Imposed. Fewer would lose the thousandths. |
| Zero | What you get adding an amount to its own opposite. Not a configurable threshold. |
| 1200.00, 950.00, 620.00 | The E1, E2 and E7 amounts. They produce 250.00 and -370.00. |
| 1.2, 1.2000, 1.20, 1.200 | The same value written four ways, to check that equality ignores how it was written. The value is illustrative; the differing decimals are the point. |
| 1.001 AED, 1.0001 BHD | Values that do not fit their currency's precision, and so must be refused. |
| 0.10 + 0.20 = 0.30 | Decimal arithmetic that binary floating point gets wrong. |
| 0.001 + 0.002 = 0.003 BHD | The same check in thousandths. |
| 1.00 AED and 1.000 BHD | Equal numbers in different currencies, to isolate the currency check from the arithmetic. |

## Multiplication and rounding

| Number | Where it comes from |
| --- | --- |
| 0.0004 | Imposed: exactly the required 0.04% daily rate. The multiply helper takes whatever factor the caller gives it and does not own the rate. |
| 250.00 and 235.00 AED; 10.000 BHD | Fixtures giving daily products of 0.10, 0.09 and 0.004, covering exact and rounded results at both precisions. The 235.00 figure came from the abandoned E6 policy; it still works as a rounding fixture but is no longer a scenario balance. |
| 12.50 and 37.50 AED; 1.250 and 3.750 BHD | At the daily rate these give exactly 0.5 and 1.5 minor units — the ties, the only case where the rounding mode decides the answer. Half-even sends them to zero and to two minor units respectively, rounding each to the nearest even. Any other balance loses the tie. |
| 0.5 and 2 | Multiplying by a half must fail when rounding is disallowed, while doubling stays exact. A factor of 1 would make the check meaningless. |
| 100.00, 0.000049 and 0.000051 | Products of 0.0049 and 0.0051, either side of half a cent. Rounding the *factor* to 0.00005 first would erase the difference — which is what this catches. |
| 200.00, -200.00, 0.5, -0.5 and 0 | Auth-A's magnitude reused to check signed and zero products: 100.00, -100.00 and 0.00. The combination of signs is the property; the magnitude is incidental. |
| Sign -1, 0, +1 | The three possible signs of a stored amount. Mathematical categories, not thresholds. |

The multiplication tests also ask explicitly for half-up, down and up rounding,
to confirm the caller controls the mode. Half-even remains the documented choice
for daily interest.

## Splitting into installments

| Number | Where it comes from |
| --- | --- |
| 10.000 BHD in 3 | Imposed by E10. Ten thousand thousandths over three gives 3.334, 3.333, 3.333. |
| 10.00 AED in 3 | The same value at two decimals: 3.34, 3.33, 3.33. Confirms the split follows the currency. |
| 1 minor unit | The largest allowed gap between two installments, and the step used to hand out the remainder. Half a unit does not exist. |
| Sign -1, 0, +1 | Decides which way the leftover units go, so that splitting a negative amount cancels the matching positive installments exactly. |
| 0.02 AED in 3 | Two units across three installments: 0.01, 0.01, 0.00. Covers a remainder bigger than one *and* an installment of zero. The negative case checks the mirror image. |
| 9.00 AED in 3 | Divides evenly: three of 3.00, with no remainder to distribute. |
| 1 and 2 installments | The trivial case, and an even division other than E10's three — 10.000 BHD in two gives 5.000 each. |
| 0 and -1 installments | The first invalid count and a negative one. Both refused. |
| 92233720368547758.08 AED | Exactly 2^63 minor units, one past the largest value a `long` holds. Proves the split keeps full precision instead of overflowing. |

## History and balances

| Number | Where it comes from |
| --- | --- |
| Day 1 as the earliest day | Imposed. The journal accepts any positive whole day; the six-day limit belongs to the interest rules. |
| Sequence 0 | The empty history — the opening balance and nothing else. |
| Sequence starts at 1, steps by 1 | Each accepted entry takes one position, however many postings it carries. Repeats and rejections take none. |
| Opening 0.00 AED and 0.000 BHD | Imposed for ACC-001 and ACC-002. A separate test opens at 1200.00 to confirm a non-zero opening balance is counted once and not twice. |
| 400.00, 250.00, -370.00, 30.00, 650.00 | 400.00 is E4; the rest follow from E1, E2, E4, E7 and E9 before fees. They separate the value-day question from the how-much-history question. |
| Booked days 5, 6, 5 | E7, E9 and E10 in arrival order — the proof that booked day does not determine position in the history. |
| 400.00 booked Day 1, value Day 3 | A future value day: the money only counts from Day 3. |
| Days 0 and -1; cutoffs -1 and 1 on an empty journal | Invalid bounds on both sides. Days must be positive, and a cutoff must name history that exists. |
| 10 in the all-or-nothing test | An amount valid in both currencies, so an account or currency error cannot be confused with a precision error. |

## Authorizations

| Number | Where it comes from |
| --- | --- |
| Approval boundary at 0 | Imposed: available funds may land exactly on zero. Requiring more would refuse valid requests; allowing less would permit unfunded holds. |
| Hold amount above 0 | Chosen. Zero would reserve nothing, and a negative hold would *increase* availability. There is no arbitrary minimum above the currency's smallest unit. |
| 200.00 and 90.00 AED | The holds requested by E3 and E8. |
| 50.00 AED | 250.00 less Auth-A's 200.00 — both the expected availability and the exact second hold that would consume it. Anything smaller misses the equality boundary. |
| 0.01 AED and 0.001 BHD | One minor unit past an exhausted balance, which must be declined. There is nothing smaller to test with. |
| 200.01 AED | A conflicting repeat, one minor unit away from the original 200.00, so only the amount comparison can explain the conflict. |
| 400.00 AED | E4's credit, reused as a hold larger than the 250.00 balance and in a future-value credit test, rather than inventing another figure. |
| -170.00 and 450.00 AED | In a holds-only fixture: 250 + 400 - 620 - 200, then +620. No settlements or fees involved. They confirm that later debits and compensation never rewrite a recorded decision. |
| 10.000 BHD | E10's amount reused as funding, so holding all of it tests the zero-availability boundary at three decimals. |
| One decision and zero postings per authorization | One request produces one approval or decline, and moves no money. Structural, not tunable. |
| -570.00 AED | Day 2 once E7 is known, before fees: 250 - 620 - 200. The saved original close still reports 50.00 available, because it predates E7. |
| 50.00 hold and 400.00 credit on Day 5 | Existing amounts reused to tell apart an original close, a later exhausted balance and a later funded one. None of these may leak into an earlier view. |
| Cutoff -1 and last + 1 | The first invalid position on each side. Zero is valid and means empty. |

## Settlements

| Number | Where it comes from |
| --- | --- |
| 185.00 and 180.00 AED | Imposed: the E5 and E6 amounts. E5 releases the whole 200.00 hold, including the unused 15.00. |
| 465.00 AED | 650.00 less E5. The refused E6 leaves both the ledger and availability here — subtracting its 180.00 would break criterion 4. |
| One final settlement per reference | Chosen lifecycle. Repeats reuse the same event without a second debit. |
| 650.01 and -0.01 AED | One cent past the fixture's balance, confirming a settlement may exceed both the hold and the available funds. |
| 620.00 hold, 470.00 ledger, 270.00 available | A decline built from E7's amount against 450.00 available. A following 180.00 settlement leaves 650 - 180, with Auth-A's 200.00 still held. A smaller hold would have been approved and lost the case. |
| 200.00 or 620.00 as a second authorization | With 450.00 available, one is approved and the other declined — and both must refuse a second final settlement. Reusing these avoids inventing a figure, and a smaller amount would lose the decline. |
| -155.00 AED | 650 - 185 - 620, in a fixture with E5 and E7 but no E6 and no fees. Backdating must not revive the settled hold. |
| 3.334 and 6.666 BHD | An E10 installment reused for a settlement against 10.000, covering three-decimal arithmetic and account separation. |
| 185.01 AED | A conflicting repeat one minor unit from E5, isolating the amount comparison. |
| Value days 2 and 6, booked day 4 | Put the debit before and after the day it was recorded, separating when it counts from when the hold is released. |
| 0.00 and -185.00 AED; value day 0 | Invalid settlements: a non-positive amount and a non-positive day. Refusing must leave the ledger and the hold untouched. |

## Reversals

| Number | Where it comes from |
| --- | --- |
| +620.00 AED, booked Day 6, value Day 2 | Imposed: E9 compensating E7. The amount is read from the original entry, never supplied again by the caller. |
| -155.00 and 465.00 AED | Accepted effects through E7 give 250 + 400 - 185 - 620; E9 brings it back to 465.00 before fees and interest. E6 posts nothing. |
| -370.00 and 250.00 AED | Value Day 2 before and after E9 is known, fees excluded. The original view must still report -370.00 once E9 exists. |
| -25.00, 225.00 and 440.00 AED | A single fee at the required amount. Reversing E7 leaves 250 - 25 on Day 2 and 465 - 25 on Day 6 — the proof that no fee is refunded implicitly. This fixture is one fee, not a full daily assessment. |
| -3.334, -3.333, -3.333 BHD | The exact opposites of E10's installments, totalling -10.000 and cancelling the credit precisely. |
| 30.00 and -155.00 AED | Refunding E5 restores -155 + 185. A separate declined authorization settles for 180.00, and refunding that restores -155 - 180 + 180. Neither refund reopens a hold or erases the original note. The refused E6 cannot be reversed at all. |
| Value Day 6 instead of Day 2 | A late compensation, which leaves earlier balances alone and only takes effect on Day 6. |
| One reversal, all original postings | Chosen: reversal is all-or-nothing. Identical repeats add nothing. |
| Days 0 and -1; changed days 5 and 3 | Invalid value days, plus valid changes to E9's dates that must register as a conflict. The core still accepts any positive whole day. |
| 10.00 AED and 10.000 BHD in one entry | Existing amounts reused to check that a reference spanning two accounts is refused as a whole. |

## Daily closes and fees

| Number | Where it comes from |
| --- | --- |
| AED 25.00, at most once per account per value day | Imposed. The posting is -25.00. |
| First close Day 1, then +1; no day 0 | Consecutive closes, with zero standing for "nothing closed yet". |
| Fee threshold strictly below 0 | Imposed: the balance must be negative. Zero is not an overdraft, and holds play no part. |
| Value days 2, 4, 5; total AED 75.00 | The actual assessments E7 causes. Two are found when revisiting closed days, the third at the Day 5 close. One fee would not satisfy the requirement. |
| 5.00, -205.00 and -230.00 AED | After E7, Day 3 is 30 - 25 = 5. Day 5 is -155 - 50 before its own fee, then -155 - 75 once closed. These show fees cascading in order and mark the open-day boundary. |
| 250.00, 225.00, 625.00, 415.00, 390.00, 390.00 AED | Days 1–6 after E9, before interest, with E6 refused and the three fees kept. Derived from the full required stream. |
| 10.000 BHD on days 5 and 6 | E10 revising Day 5 after its original zero close, before the separate interest credit. |
| -10.00 on Day 1, +20.00 on Day 2 | A late event leaving Day 2 at +10 before fees and -15 after the first, which triggers a second fee and a -40 close; Day 3 then closes at -65. The credit is deliberately twice the debit, so the starting balance is positive rather than zero. |
| -60.00 AED | A late -10 debit plus two 25 fees, both caught at the next close. Proves that skipping an explicit reassessment does not skip the fees forever. |
| 0.00, +0.01, -0.01 AED; -25.01 after the fee | Zero and the nearest representable values either side of the eligibility boundary. |
| 200.00 hold against a zero ledger | Auth-A's amount reused to separate negative availability (-200) from a ledger that is not negative. A hold alone cannot trigger a fee. |
| -0.001 and +0.001 BHD | The smallest possible negative dinar balance must fail for want of a fee policy, and an exact compensating credit then allows a zero close — reusing the event ID that was refused. |
| A Day 3 debit booked on Day 1 | Timing reused from the history fixture, confirming no fee appears on Days 1 or 2. |
| 0.00 versus -25.00 AED | The counterexample. The same -10 debit is fully reversed on both paths, but only the reversal after the close keeps the 25 fee. |

## Rejected attempts

| Number | Where it comes from |
| --- | --- |
| One rejection per failed attempt; zero journal entries | Attempts are separate facts even when identical. Writing nothing keeps the rejection clean and leaves the event ID free. |
| Processing days 5 and 6; booked Day 5, value Day 2 | The scenario's separate clocks reused, so two failed reversals keep their input while recording different processing dates. |
| AED 250.00, hold 200.00, settlement 0.00 then 185.00 | The brief's amounts reused. The refused zero settlement leaves the ledger at 250 and availability at 50; the corrected one releases the hold and leaves 65. |
| AED 185.00 settled before its 200.00 authorization exists | A missing reference leaves the opening 250.00 untouched. Once the authorization arrives, an explicit retry settles once and leaves 65.00 — confirming the rejection reserved neither the reference nor the event ID. |
| AED 10.00 against BHD 10.000 | Equal numbers reused to isolate account and currency errors in a multi-posting request. A valid first posting must not survive the second being refused. |
| AED -10.00 and +10.00 | A reversal succeeding once its missing original arrives, restoring 250 exactly. |
| AED 300.00 hold, 50.00 settlement, later 300.00 credit | A hold above the opening 250 is declined, and settling that recorded reference produces a diagnostic. The later credit confirms an accepted repeat still preserves the earlier decline. A smaller hold would simply have been affordable. |
| Day 6 capitalization, late +250.00 on Day 5, final 500.80 | The interest fixture reused to test the dispatch path and recovery after a stale-accrual rejection. Submitting capitalization as booked Day 5 must fail rather than quietly rewrite the date. |
| Processing day 0; settlement value day 0 | The first invalid day on each path. The first is a caller mistake caught before execution; the second is an expected rejection carrying a complete request. |

## The runnable scenario

| Number | Where it comes from |
| --- | --- |
| 10 events, E1–E10 | Imposed: the complete stream in arrival order, with all its amounts, dates and the three-way split. |
| 2 accounts, 6 days, 12 close entries | Both accounts closed on every day. Closes follow E2, E3, E4, E6, E8 and E10. |
| 3 reassessments, after E7, E9 and E10 | The three backdated changes in the stream. E9 keeps the fees while adjusting accruals; E10 revises the earlier dinar close. |
| 2 capitalizations, after both Day 6 closes | One per account, as required. |
| 1 rejection, 9 accepted events, 0 accepted anomalies | E6 is refused because Auth-Z does not exist. Auth-B's decline is a handled outcome, not an error. Observed results, not configured limits. |
| Opening 0.00 AED and 0.000 BHD every run | Imposed. Each run starts fresh, so repeated executions reproduce the same entries and the same final balances. |

## Daily report

| Number | Where it comes from |
| --- | --- |
| 6 snapshots, 12 account rows | One saved boundary per day, one row per account per boundary. |
| 8 table columns; balances and held amounts at indexes 2–5 | Account, currency, closing, on hold, available, revised closing, fees and authorizations. The four numeric columns align right. These positions follow the schema; halving them would select different fields or omit information. |
| Column width = longest header or cell; 2 padding spaces | One space on each side keeps values separate from borders. Half the computed width would not fit the contents; one padding space would leave one side without a gap. |
| 6 table lines per scenario day | One header, two account rows and three borders. The report tests check that they have equal lengths even when fees or authorization states widen a column. Half would omit rows or borders. |
| Cutoffs 4, 7, 10, 13, 18 and 26 | Derived from the accepted order and the end-of-day work. The final 26 is 9 accepted events + 12 closes + 3 reassessments + 2 capitalizations. E6 moves only the rejection log. Observed, not written into the report. |
| Day 0 and cutoff 0; the previous index | The empty view before the first saved day, and the snapshot immediately before each one. Together they locate what is new: fees, diagnostics and rejections. |
| Original AED closes 250.00, 250.00, 650.00, 465.00, -230.00, 390.93 | The six real end-of-day balances. Day 5 carries the backdated 620 debit and all 75 in fees; Day 6 carries the reversal and the 0.93 interest credit. |
| Original AED availability 250.00, 50.00, 450.00, 465.00, -230.00, 390.93 | Auth-A's 200 hold subtracted on Days 2 and 3 only. Settlement releases it, and the declined Auth-B reserves nothing. These prove later states do not leak backwards. |
| On hold: AED 200.00 on Days 2 and 3; zero otherwise and throughout for BHD | Closing minus available at the same saved cutoff. Auth-A reserves its full amount until settlement; Auth-B's decline reserves nothing. Halving the displayed hold would misstate the reservation. |
| Revised AED closes 250.00, 225.00, 625.00, 415.00, 390.00, 390.93 | The same value days seen with final knowledge. Only Day 6 includes the interest credit. |
| Original BHD Day 5 0.000 against revised 10.000; Day 6 10.008 | E10 belongs to value Day 5 but arrives during Day 6 — the prescribed late-arrival case. The last figure includes 0.008 of interest. |
| 11 of 12 account rows show no new fees | Only ACC-001 on Day 5 has assessments, showing three charges at value days 2, 4 and 5. The count catches a fee being displayed again on later days. |
| One error on Day 4; two identical failures in a fixture | E6 is the stream's only rejection. A separate fixture repeats one failed request to confirm both attempts survive rather than being merged. |
| Fixture booked Day 1, received Day 2; changes on Day 3 | Confirms backdated errors and diagnostics appear on the day they arrived, and that anything after the last snapshot cannot alter a saved report. The smallest distinct days that show this. |
| Fixture amounts 250.00, 200.00, 400.00, 185.00, 180.00, 300.00, 50.00 AED | Existing funding, hold, settlement, rejection and decline cases reused, so snapshot behaviour is tested without inventing new thresholds. |

## Interest and capitalization

| Number | Where it comes from |
| --- | --- |
| Daily rate 0.0004 | Imposed: exactly 0.04% per day. |
| Days 1 to 6, one capitalization per account | Imposed: the window and the single credit. |
| No interest at or below zero | Imposed. Zero accruals are still recorded as daily facts, and a zero total still closes the window. |
| 0.10, 0.09, 0.25, 0.17, 0.16, 0.16 AED — total 0.93 | The final accruals on revised balances of 250, 225, 625, 415, 390, 390, with E6 refused and fees kept. |
| 0.000 on days 1–4, 0.004 on days 5–6 BHD — total 0.008 | E10 earns interest on two value days despite arriving on Day 6. |
| Final AED 390.93 and BHD 10.008 | The pre-interest balances plus those exact rounded sums. Nothing else is rounded or adjusted. |
| 0.10, 0.00, 0.00, 0.00 AED after E7 | Revised days 1–4 before the Day 5 close. Day 3's balance of 5 gives 0.002, which rounds to nothing, while the earlier 0.10 snapshot for Day 2 stays readable. |
| 12.50 and 37.50 AED; 1.250 and 3.750 BHD | The rounding-tie fixtures reused. Half-even gives zero or two minor units per day. |
| Six times 0.02 AED = 0.12; six times 0.002 BHD = 0.012 | Constant balances of 37.50 and 3.750 finish at 37.62 and 3.762. Adding the *unrounded* daily products and rounding once would give 0.09 and 0.009 instead — so this catches rounding done in the wrong order. |
| 250.00 ledger fully held; a further 0.01 authorization | The opening-day close reused. The 0.10 accrual is still earned with zero availability, but cannot fund even the smallest hold. |
| 100.00 credit, late -110.00 on Day 2, +100.00 on Day 3 | Daily interest of 0.04, 0.00, 0.03, because the 25 fee leaves Day 3 at 65. A smaller debit would not overdraw, and charging the fee on Day 1 would change its accrual. |
| Late +250.00 on Day 5; capitalization 0.80 | Four days at 250 earn 0.40, then two days at 500 earn another 0.40. Detects a stale capitalization after all six closes. |
| A Day 7 posting; an unrelated 10.000 BHD | Existing amounts reused to confirm only same-account postings inside the six-day window invalidate an accrued set. Day 7 is the first day outside it. |
| 0.60 on an unchanged AED 250.00; +250.00 after capitalization | Six exact 0.10 accruals. A later posting can neither change an identical repeat nor reopen the finalized set. |
| 0.10 AED against 0.100 BHD; days 0 and -1; cutoff last + 1 | Existing boundary values reused for currency validation, invalid accrual dates and invalid cutoffs. |

## Build versions

| Version | Why this one |
| --- | --- |
| Java 21 | Already available in the environment, and used as the compilation target. |
| JUnit 5.10.5 | Already cached, and runs standard tests without a custom runner. |
| Maven Resources 3.3.1, Compiler 3.13.0, Surefire 3.5.2 | Already cached, and pinned so the build does not drift with Maven's defaults. |
| POM 4.0.0 | The Maven file format, not a setting of ours. |
| Project 0.1.0-SNAPSHOT | The initial development version. |

Verified with Maven 3.6.3.

## Working for the acceptance audit

The E6 rejection is what the code does. The fee-refund row is a what-if, kept to
show the alternative that was considered and not adopted; the reasoning is in
[REJECTED.md](REJECTED.md).

| Number | Where it comes from |
| --- | --- |
| AED -395.00 | Value Day 2 as actually known at the Day 5 close: -370 before fees, less its own 25. This is what separates the qualified first criterion from a full closing balance. |
| AED -155.00 before fees, days 4 and 5 | 250 + 400 - 185 - 620. Days 2, 4 and 5 all still need fees, so refusing E6 does not reduce three assessments to one. |
| AED 390.00 + 0.93 = 390.93 | The adopted outcome. Refusing E6 and keeping the fees gives revised balances of 250, 225, 625, 415, 390, 390 and daily accruals of 0.10, 0.09, 0.25, 0.17, 0.16, 0.16. |
| AED 465.00 + 1.03 = 466.03 | The alternative. Refusing E6 *and* crediting three 25.00 refunds at their original value days gives balances of 250, 250, 650, 465, 465, 465 and accruals of 0.10, 0.10, 0.26, 0.19, 0.19, 0.19. Every original fee record would still stand. |
