# Numbers

The scenario results below reject E6 in accordance with criterion 4 and retain
assessed fees. The adopted scope reverses E7 only, refusing criterion 6's
automatic-restoration claim. Fee refunds are not pending work.
See [REJECTED.md](REJECTED.md) for this policy resolution and its alternatives.

## Money component

| Number | Source and rationale |
| --- | --- |
| AED: 2 decimals | Imposed by the requirement; halving the scale would lose the cents. |
| BHD: 3 decimals | Imposed by the requirement; a smaller scale would lose the thousandths. |
| Zero | The identity of addition and the result of an amount added to its own opposite; not a configurable threshold. |
| 1200.00, 950.00, 620.00 | The E1, E2 and E7 amounts, used in the tests. They produce 250.00 and -370.00; halving them would change the required brief. |
| 1.2, 1.2000, 1.20, 1.200 | Representations of the same value, used to verify normalization and equality. The value is illustrative; what matters is the differing scale. |
| 1.001 AED, 1.0001 BHD | Inputs not representable at their respective scales, which must be rejected. |
| 0.10 + 0.20 = 0.30 | An example of exact decimal arithmetic. Halving the values would not add a different property. |
| 0.001 + 0.002 = 0.003 BHD | Verifies operations in thousandths; halving 0.001 would produce a non-representable input. |
| 1.00 AED and 1.000 BHD | Amounts of equal numeric value, used to isolate currency incompatibility. |

## Multiplication and rounding

| Number | Source and rationale |
| --- | --- |
| 0.0004 | The exact factor for the required daily rate of 0.04%. Halving it would change the requirement. The helper accepts a caller-supplied factor; it does not own the rate. |
| 250.00 and 235.00 AED; 10.000 BHD | Arithmetic fixtures producing daily products rounded to 0.10, 0.09 and 0.004, exercising exact and rounded results at both scales. The 235.00 fixture was derived under the abandoned E6 policy and remains useful for testing rounding; it is no longer a scenario balance. |
| 12.50 and 37.50 AED; 1.250 and 3.750 BHD | At the daily rate they produce exactly 0.5 and 1.5 minor units. HALF_EVEN rounds them to zero and two minor units. Halving these balances would remove the tie. |
| 0.5 and 2 | Halving one minor unit must fail with UNNECESSARY, while doubling it is exact. Halving 2 would reduce the multiplication check to an identity. |
| 100.00, 0.000049 and 0.000051 | Products 0.0049 and 0.0051 lie on opposite sides of a half-cent. Rounding the factor to 0.00005 first loses that distinction. |
| 200.00, -200.00, 0.5, -0.5 and 0 | Reuse Auth-A's magnitude to check signed and zero products: 100.00, -100.00 and 0.00. The magnitudes are illustrative; the property is the combination of signs. |
| Sign -1, 0, +1 | Standard signum values for negative, zero and positive stored amounts; these are mathematical categories, not thresholds to tune. |

The multiplication tests also select HALF_UP, DOWN and UP explicitly to verify
that rounding is controlled by the caller. HALF_EVEN remains the documented
policy used by the daily interest calculation.

## Splitting into installments

| Number | Source and rationale |
| --- | --- |
| 10.000 BHD in 3 installments | The E10 case: 10,000 thousandths divided by three produce 3.334, 3.333, 3.333. Amount and installment count are imposed by the brief. |
| 10.00 AED in 3 installments | The same value in the two-decimal currency: 3.34, 3.33, 3.33. Verifies that the scale follows the currency. |
| 1 minor unit | The maximum difference between two installments, and the step used to distribute the remainder. Half a unit is not representable. |
| Sign -1, 0, +1 | Determines the direction of the leftover units: splitting a negative amount must compensate the corresponding positive installments. |
| 0.02 AED in 3 installments | Two minor units and three installments: 0.01, 0.01, 0.00. Verifies a remainder greater than one and a zero installment. The negative case verifies the same distribution with the opposite sign. |
| 9.00 AED in 3 installments | A case with a positive quotient and zero remainder: three installments of 3.00. |
| 1 and 2 installments | Verify the identity case and an exact division other than E10's three installments; 10.000 BHD in two gives 5.000 per installment. |
| 0 and -1 installments | The first invalid count and a negative count: both rejected. |
| 92233720368547758.08 AED | This is 2^63 minor units, just beyond the maximum of long. Verifies that the split keeps BigInteger precision; halving it would hide that boundary. |

## History and balances

| Number | Source and rationale |
| --- | --- |
| Minimum day 1 | The brief starts at Day 1. Journal postings and queries accept positive whole days; the interest policy limits closes to the six-day window. |
| Sequence 0 | The prefix with no records, holding the opening balance alone. |
| First sequence 1, increment 1 | Every accepted entry occupies one whole position in the history, regardless of how many postings it holds. Identical repetitions and rejected entries do not consume a position. |
| Opening balances 0.00 AED and 0.000 BHD | Imposed for ACC-001 and ACC-002. The synthetic test with an opening balance of 1200.00 verifies that a non-zero opening balance is included exactly once. |
| 400.00, 250.00, -370.00, 30.00, 650.00 | 400.00 comes from E4; the other values derive from E1, E2, E4, E7 and E9, before fees. They verify value day and knowledge separately. |
| Booked days 5, 6, 5 | The order of E7, E9 and E10: it shows that the booked day does not order the history. |
| 400.00 booked on 1 with value day 3 | A synthetic future-value-day case: the amount enters the balance only from Day 3. |
| 0 and -1 as days, -1 and 1 as cutoffs on an empty journal | Verify invalid bounds: days must be positive and the cutoff must identify an existing prefix. |
| 10 in the atomic rejection test | An amount representable in both currencies; it isolates account or currency errors from precision errors. Halving it would not change the property under test. |

## Authorizations

| Number | Source and rationale |
| --- | --- |
| Approval boundary 0 | Required: available funds may reach exactly zero after a new hold. A positive minimum would reject valid requests; a negative threshold would allow unfunded holds. |
| Hold amount greater than 0 | Chosen input policy: zero reserves nothing and a negative hold would increase availability. There is no arbitrary positive minimum above the currency's smallest unit. |
| 200.00 and 90.00 AED | E3 and E8 request these holds. Halving them would no longer exercise the specified authorizations. |
| 50.00 AED | 250.00 minus Auth-A's 200.00: both the expected available balance and the exact second hold that exhausts it. Half would miss the equality boundary. |
| 0.01 AED and 0.001 BHD | A request one minor unit beyond an exhausted balance must be declined. Half a minor unit is not representable. |
| 200.01 AED | A conflicting retry differs from the original 200.00 by one minor unit. The change isolates the amount comparison. |
| 400.00 AED | E4's credit also provides a synthetic hold larger than the initial 250.00 balance, and a future-value credit test. It is reused to avoid another arbitrary fixture amount. |
| -170.00 and 450.00 AED | In the authorization-only fixture, 250 + 400 - 620 - 200 and then +620. Settlements and fees are absent. These balances verify that later debits and compensation do not rewrite decisions. |
| 10.000 BHD | A synthetic funding credit reuses E10's amount; holding all of it tests the zero-available boundary at three decimals. |
| One decision, zero postings per authorization | One request has one recorded approval or decline. It changes availability without creating a ledger movement; these counts are structural, not tunable constants. |
| -570.00 AED | Day 2 as known after E7, before fees: 250 - 620 - 200. The saved original close still reports 50.00 available; the later prefix includes E7. |
| 50.00 hold and 400.00 credit on Day 5 | Reuse existing fixture amounts to distinguish an original close, a later exhausted balance, and the later funded balance. None of these later entries may enter an earlier prefix. |
| Cutoff -1 and lastSequence + 1 | The first invalid prefix on either side; zero is the valid empty prefix. All historical reads use the same bounds. |

## Settlements

| Number | Source and rationale |
| --- | --- |
| 185.00 and 180.00 AED | Required E5 and E6 amounts. E5 releases the original 200.00 hold in full, including the unused 15.00; halving these amounts would change the scenario. |
| 465.00 AED | 650.00 minus E5. Rejected E6 leaves both ledger and available balance at this amount; subtracting its 180.00 would violate criterion 4. |
| One final settlement per reference | Chosen lifecycle policy; zero would prohibit settlement. Retries reuse the same event without another debit. This count is discrete, not a fractional limit. |
| 650.01 and -0.01 AED | One cent beyond the fixture's ledger balance verifies that settlement may exceed both the hold and the funds. Half would miss the overdraft boundary. |
| 620.00 hold, 470.00 ledger, 270.00 available | A synthetic decline reuses E7's amount against 450.00 available. A subsequent 180.00 settlement leaves 650 - 180 in the ledger, with Auth-A's 200.00 still held. Half the requested hold would be approved. |
| 200.00 or 620.00 for a second authorization | With 450.00 available, one is approved and the other declined. Both recorded references must reject a second final settlement. Reusing existing amounts avoids an arbitrary fixture; halving 620.00 would lose the decline case. |
| -155.00 AED | 650 - 185 - 620 in a fixture containing E5 and E7, without E6 or fees. Backdating must not revive the settled hold. |
| 3.334 and 6.666 BHD | Reuse an E10 installment amount for a synthetic settlement against 10.000. The result verifies three-decimal arithmetic and account isolation. Halving these values adds no distinct precision check. |
| 185.01 AED | A conflicting retry differs from E5 by one minor unit, isolating the amount comparison. |
| Value days 2 and 6, booked day 4 | Place a settlement debit before and after its recording day to separate value timing from hold release. Halving Day 6 would lose the future-value case. |
| 0.00 and -185.00 AED, value day 0 | Invalid settlement boundaries: non-positive amounts and a non-positive day. Rejection must leave both ledger and hold untouched. |

## Reversals

| Number | Source and rationale |
| --- | --- |
| +620.00 AED, booked Day 6, value Day 2 | Required E9 compensation of E7's -620.00. The amount is derived, not supplied independently. Halving it would leave half the debit unreversed. |
| -155.00 and 465.00 AED | Accepted effects through E7 leave 250 + 400 - 185 - 620; E9 restores 465.00 before fees and interest. E6 creates no posting. These are scenario results, not adjustable parameters. |
| -370.00 and 250.00 AED | Day 2 before and after E9 is known, excluding fees. The original cutoff must still report -370.00 after E9 arrives. |
| -25.00, 225.00 and 440.00 AED | A synthetic single fee uses the required charge. Reversing E7 leaves 250 - 25 at Day 2 and 465 - 25 at Day 6. This test verifies no implicit fee refund; it does not simulate all daily assessments. |
| -3.334, -3.333, -3.333 BHD | The exact opposites of E10's installments. Their total is -10.000, cancelling the original credit; halving them would only partially compensate it. |
| 30.00 and -155.00 AED after settlement refunds | Refunding E5 restores -155 + 185. A separate synthetic declined authorization settles for 180.00 and its refund restores -155 - 180 + 180. Neither refund reopens a hold or erases the original diagnostic. The rejected E6 cannot be reversed. |
| Value Day 6 instead of Day 2 | A synthetic late compensation leaves earlier balances unchanged and becomes effective on Day 6. Halving the date would no longer test compensation at the end of the window. |
| One reversal, all original postings | Chosen full-reversal lifecycle. Zero would disallow reversal; partial counts would violate the operation's all-or-nothing scope. Identical retries add zero entries. |
| Days 0 and -1; changed days 5 and 3 | The first invalid value days and valid changes to E9's booked and value dates. They exercise rejection and request comparison; the core still accepts any positive whole day. |
| 10.00 AED and 10.000 BHD in one entry | Reuse existing fixture amounts to test rejection of a multi-account reference. The amounts are illustrative; account separation is what matters. |

## Daily closes and fees

| Number | Source and rationale |
| --- | --- |
| AED 25.00, at most once per account per value day | Imposed by the requirement. The posting is -25.00; halving the fee would change the required result. |
| First close Day 1, next day +1, no closed day 0 | Consecutive daily closes and their empty-history sentinel. These are discrete calendar positions, not configurable fractions. |
| Fee threshold strictly below 0 | Required negative ledger balance. Zero is not an overdraft; active holds are excluded from this calculation. |
| Value days 2, 4, 5; total 75.00 AED | The actual assessments caused by E7. Two are discovered on revisiting closed days, the third at the Day 5 close. Halving the number of assessments is not meaningful and charging only one fails the requirement. |
| 5.00, -205.00 and -230.00 AED | After E7, Day 3 is 30 - 25 = 5. Day 5 is -155 - 50 before its own fee, then -155 - 75 after close. These verify chronological fee effects and the open-day boundary. |
| 250.00, 225.00, 625.00, 415.00, 390.00, 390.00 AED | Day 1–6 balances after E9, before interest. E6 is rejected and the three fees are retained; values are derived from the complete required stream. |
| 10.000 BHD on days 5 and 6 | E10 revises Day 5 after its original zero close. This is the balance before the separate capitalization credit. |
| -10.00 on Day 1 and +20.00 on Day 2 | A synthetic late event leaves Day 2 at +10 before fees and -15 after the first 25 fee, triggering a second fee and a -40 close. Day 3 then closes at -65. The credit is twice the reused 10-unit fixture; halving it would test a zero starting balance instead of a positive one. |
| -60.00 AED | A late -10 debit plus two 25 fees, both caught up at the next close. It tests that omitting an explicit reassessment does not omit fees. |
| 0.00, +0.01 and -0.01 AED; -25.01 after fee | Zero and the nearest representable balances on either side of the eligibility boundary. Half a cent is not representable in AED. |
| 200.00 hold and zero ledger | Reuse Auth-A's amount to distinguish negative availability (-200) from a non-negative ledger. The hold cannot trigger a fee by itself. |
| -0.001 and +0.001 BHD | The smallest negative balance must fail for lack of a fee policy; an exact compensation permits a zero close using the previously rejected event ID. Halving the amounts would lose currency precision. |
| Day 3 future debit booked on Day 1 | Reuses the history fixture's timing to verify that no fee appears in days 1 or 2. Halving the value day would remove that separation. |
| 0.00 versus -25.00 AED in the counterexample | A -10 debit is fully reversed in both paths. Only a reversal after close retains the 25 fee. Halving the debit would expose the same property; 10 reuses the existing fixture. |

## Rejected attempts

| Number | Source and rationale |
| --- | --- |
| One rejection per failed attempt; zero accepted journal entries | Attempts are distinct facts, even with equal input. Zero journal writes preserve atomic rejection and leave the event ID available. Halving a discrete attempt count would lose history. |
| Processing days 5 and 6, booked Day 5, value Day 2 | Reuse the scenario's separate clocks to verify that two failed reversals retain their input while recording different processing dates. Halving dates would remove the intended late-arrival case. |
| AED 250.00, hold 200.00, settlement 0.00 then 185.00 | Reuse the brief's amounts. The rejected zero settlement leaves ledger 250 and available 50; the corrected settlement releases the hold and leaves 65. Zero is the invalid boundary; halving 185 would change the expected refund of held funds. |
| AED 185.00 settlement before its 200.00 authorization | A missing reference leaves the opening 250.00 untouched. After the authorization arrives, an explicit retry settles once and leaves 65.00. These reused amounts verify that a rejection reserves neither the reference nor the event ID. |
| AED 10.00 versus BHD 10.000 | Reuse equal numerical amounts to isolate account and currency errors in a multi-posting request. A valid first posting must not survive rejection of the second. |
| AED -10.00 and +10.00 | A reversal succeeds after its missing original arrives, restoring 250 exactly. The magnitude is illustrative and reused; halving it would exercise the same property. |
| AED 300.00 hold, settlement 50.00, later credit 300.00 | A hold above the opening 250 is declined; settling its recorded reference produces a diagnostic. A later 300 credit verifies that an accepted retry preserves the earlier decline. Halving the hold would incorrectly make it affordable. |
| Day 6 capitalization, late +250.00 on Day 5, final 500.80 | Reuse the interest fixture to test the dispatch path and recovery after a stale-accrual rejection. Submitting capitalization as booked Day 5 must fail instead of silently changing the input date. |
| Processing day 0 and settlement value day 0 | First invalid day values. The former is a caller error before execution; the latter is an expected domain rejection with a complete submitted request. Fractions are not valid calendar positions. |

## Executable scenario

| Number | Source and rationale |
| --- | --- |
| 10 input events, E1–E10 | The complete required stream, in arrival order. Omitting or halving it would change the requested exercise. All amounts, dates and the three-installment split retain the earlier documented values. |
| 2 accounts, 6 days, 12 close entries | Close both specified accounts once on every day. Closes follow E2, E3, E4, E6, E8 and E10; halving the count would leave an account or a day unclosed. |
| 3 reassessments after E7, E9 and E10 | These are the backdated changes in the prescribed stream. E9 retains the fees while adjusting accruals; E10 revises the prior BHD close. Omitting either would lose an explicit correction checkpoint. |
| 2 capitalization entries after both Day 6 closes | One per account, as required. Halving the count would omit an account's interest credit. |
| 1 rejected attempt, 9 accepted input events and 0 accepted anomalies | E6 is rejected without a debit because Auth-Z is absent. Auth-B's decline is a handled outcome. These are observed results of the ten-event stream, not configured limits. |
| Opening 0.00 AED and 0.000 BHD on each invocation | Imposed account balances. Each run creates fresh state so independent executions reproduce the same entries and final balances. |

## Daily report

| Number | Source and rationale |
| --- | --- |
| 6 snapshots and 12 account rows | One saved boundary per specified day and one row per account per boundary. Halving either count would omit required output. |
| Cutoffs 4, 7, 10, 13, 18 and 26 | Derived from the accepted input order and end-of-day operations. Final 26 is 9 accepted inputs + 12 closes + 3 reassessments + 2 capitalizations. E6 advances only the rejection log. These positions are observed, not hardcoded report constants. |
| Initial day and cutoff 0; previous index minus 1 | The empty prefix before the first saved day, then the immediately preceding snapshot. They locate newly added fees, diagnostics and rejections; fractional positions have no meaning. |
| Original AED closes 250.00, 250.00, 650.00, 465.00, -230.00, 390.93 | The six actual end-of-day balances. Day 5 includes the backdated 620 debit and all 75 in fees; Day 6 includes the 620 reversal and 0.93 capitalization. Halving an amount would no longer replay the specified events. |
| Original AED availability 250.00, 50.00, 450.00, 465.00, -230.00, 390.93 | Subtract Auth-A's 200 hold on Days 2 and 3 only. Settlement releases it; declined Auth-B reserves nothing. These outcomes verify that later states do not leak into old closes. |
| Revised AED closes 250.00, 225.00, 625.00, 415.00, 390.00, 390.93 | Use final knowledge at each value day. Only Day 6 includes the capitalization credit; earlier rows match the previously documented pre-capitalization balances. |
| Original BHD Day 5 0.000 versus revised 10.000; Day 6 10.008 | The late E10 credit belongs to value Day 5 but arrives during Day 6. The final row includes 0.008 interest. Halving either date or amount would lose the prescribed late-arrival case. |
| 11 account rows without new fees | Of 12 account rows, only ACC-001 on Day 5 receives assessments, displaying three charges at value days 2, 4 and 5. This count detects duplicate display on later days; it is not an assessment limit. |
| Error on Day 4; two identical failures in the report fixture | E6 is the stream's sole rejection. The separate fixture repeats one failed request to verify both attempts survive; halving that count would miss accidental deduplication. |
| Fixture booked Day 1, received Day 2; later changes on Day 3 | Verify that backdated errors and accepted diagnostics appear on their arrival day, and that changes after the last capture cannot alter a saved report. Dates reuse the smallest distinct days for these boundaries. |
| Fixture amounts 250.00, 200.00, 400.00, 185.00, 180.00, 300.00 and 50.00 AED | Reuse existing funding, hold, settlement, rejection and decline cases. They isolate snapshot behavior without choosing new monetary thresholds. |

## Build versions

| Version | Rationale |
| --- | --- |
| Java 21 | The JDK already available in the environment; used as the compilation release. |
| JUnit 5.10.5 | A version already cached; it allows standard tests without a custom runner. |
| Maven Resources 3.3.1, Compiler 3.13.0, Surefire 3.5.2 | Versions already cached, pinned so as not to depend on Maven's defaults. |
| POM 4.0.0 | The Maven format version, not an application parameter. |
| Project 0.1.0-SNAPSHOT | The initial development identifier. |

The versions identify tools and formats: halving them has no functional
meaning. The project is verified with Maven 3.6.3, present in the environment.

## Daily interest and capitalization

| Number | Source and rationale |
| --- | --- |
| DAILY_RATE = 0.0004 | Exactly the required 0.04% daily rate. Halving it would underpay the required interest. |
| FINAL_DAY = 6, first day 1, one capitalization per account | The prescribed window and payment count. Halving the window would omit three required days; splitting the payment would violate the single-credit rule. |
| Zero interest on balances at or below 0 | The required positive-balance threshold. Zero accruals remain explicit daily facts; a zero total still finalizes the window. |
| 0.10, 0.09, 0.25, 0.17, 0.16, 0.16 AED; total 0.93 | Final rounded accruals on the revised balances 250, 225, 625, 415, 390, 390. These are derived from the stream, with E6 rejected and fees retained. |
| 0.000 on days 1–4 and 0.004 on days 5–6 BHD; total 0.008 | E10 earns interest on two value days despite arriving on Day 6. Halving the day count would miss its backdated effect. |
| Final AED 390.93 and BHD 10.008 | Pre-capitalization balances plus their exact rounded daily sums. No other rounding or remainder adjustment applies. |
| 0.10, 0.00, 0.00, 0.00 AED after E7 | Revised days 1–4 before the Day 5 close. Day 3's balance of 5 produces 0.002, which rounds to 0.00; the earlier 0.10 Day 2 snapshot stays queryable. |
| 12.50, 37.50 AED; 1.250, 3.750 BHD | Reuse the multiplication tie fixtures. Daily results are zero or two minor units with HALF_EVEN; halving the inputs removes the tie. |
| Six times 0.02 AED = 0.12; six times 0.002 BHD = 0.012 | Constant balances 37.50 / 3.750 finish at 37.62 / 3.762. Rounding the unrounded six-day totals instead would produce 0.09 / 0.009, so this detects an incorrect order of rounding. |
| 250.00 ledger and full 250.00 hold; extra 0.01 authorization | Reuse the scenario's opening-day close. The 0.10 accrual is earned despite zero availability, but cannot fund the smallest AED hold. Halving the hold would no longer reserve the full balance. |
| 100.00 credit, late -110.00 on Day 2 and +100.00 on Day 3 | Produces daily interest 0.04, 0.00, 0.03: the 25 fee leaves Day 3 at 65. Halving the 110 debit would remove the overdraft; charging that fee on Day 1 would incorrectly change its accrual. |
| Late +250.00 on Day 5; capitalization 0.80 | Four days at 250 earn 0.40, then two days at 500 earn 0.40. Detects stale capitalization after the six closes. Halving the credit would change the expected correction. |
| Day 7 future posting; unrelated BHD 10.000 | Reuse existing amounts to verify that only postings affecting the same account within the six-day window invalidate its accrued set. Day 7 is the first excluded day; halving it would place it inside the window. |
| 0.60 on unchanged AED 250.00; +250.00 after capitalization | Six exact 0.10 accruals. A later posting cannot change an identical retry or reopen the finalized set. |
| 0.10 AED versus 0.100 BHD, days 0 and -1, cutoff last + 1 | Reuse existing boundary values to test currency validation, invalid accrual dates and invalid historical cutoffs. These isolate invalid categories; halving them adds no distinct property. |

## Acceptance audit calculations

The E6 rejection is implemented. Fee compensation is a counterfactual
calculation retained to document an alternative that was not adopted.

| Number | Source and rationale |
| --- | --- |
| AED -395.00 | Day 2 as actually known at the Day 5 close: pre-fee -370 minus its 25 fee. Separates the qualified first criterion from an inclusive closing balance. |
| AED -155.00 before fees on days 4 and 5 | 250 + 400 - 185 - 620. Days 2, 4 and 5 still require fees; rejecting E6 does not reduce the count to one. |
| AED 390.00 + 0.93 = 390.93 | Reject E6 and retain the three fees: revised balances 250, 225, 625, 415, 390, 390 yield daily accruals 0.10, 0.09, 0.25, 0.17, 0.16, 0.16. These are derived outcomes, not tunable constants. |
| AED 465.00 + 1.03 = 466.03 | Alternative not adopted: reject E6 and add three 25 fee refunds at their original value days after E9. Revised balances 250, 250, 650, 465, 465, 465 yield daily accruals 0.10, 0.10, 0.26, 0.19, 0.19, 0.19. All original fee records remain. Halving refunds would leave fees partially charged. |
