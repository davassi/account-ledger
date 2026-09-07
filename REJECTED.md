# Acceptance audit and rejected criteria

This review checks the criteria against the brief, separately from the behavior
of the current implementation. A test that confirms our chosen policy does not
prove that the policy satisfies the requirement.

**Audit correction:** the earlier refusal of criterion 4 was not justified.
It is a valid requirement, and the current settlement implementation violates it.
Criterion 6 also cannot be refused solely because the ledger is append-only:
compensating credits can restore net fees without deleting their assessments.
This audit updates the assessment; it does not change the settlement or refund code.

## Verdict on every criterion

| # | Criterion | Verdict |
| --- | --- | --- |
| 1 | Day 2 balance at end-of-Day-5 knowledge, before fees, is AED -370.00. | **Accept**, with the explicit pre-fee qualification. |
| 2 | E7 causes exactly one overdraft fee, on Day 2. | **Refuse:** there are three assessments, for value days 2, 4 and 5. |
| 3 | The Day 4 settlement of Auth-A must be accepted. | **Accept.** |
| 4 | A settlement with an authorization ID absent from the ledger must be rejected without a debit. | **Accept; current implementation does not comply.** |
| 5 | If Auth-B is approved, its hold reduces availability but not ledger balance. | **Accept:** conditional statement; Auth-B is declined in this stream. |
| 6 | After E9, all balances and fees return to pre-E7 values. | **Needs a precise meaning:** possible for net economic values through compensations, impossible for erasing recorded history. The current fee-retention policy does not meet the net-refund reading. |
| 7 | E10's three installments must each be BHD 3.334. | **Refuse:** they would total BHD 10.002. |
| 8 | Discard any difference between rounded accruals and capitalized interest. | **Refuse:** this contradicts the required exact equality. |

The unqualified contradictions are **2, 7 and 8**. A different implementation
preference does not justify adding criterion 4 to that list. Criterion 6 needs
an explicit refund interpretation before claiming compliance or rejecting it.

## 1 — Accept the pre-fee Day 2 balance

Only E1, E2 and E7 affect ACC-001 on or before value Day 2, as known at the end
of Day 5:

```text
1,200.00 - 950.00 - 620.00 = -370.00 AED
```

Auth-A's hold does not enter ledger balance. E4 and the settlements have later
value days, and E9 is not yet known. E8 introduces no ledger posting either.

This is a pre-fee evaluation, not the actual final Day 5 view including fees.
The three relevant readings are:

| View of value Day 2 | AED balance |
| --- | ---: |
| Original Day 2 close | 250.00 |
| With E7 known, before fee assessment | -370.00 |
| At the actual Day 5 close, including the Day 2 fee | -395.00 |

[DailyClosingTest](src/test/java/accountledger/DailyClosingTest.java) preserves
the original cutoff and checks the pre-fee result. The audit also queried the
actual Day 5 close to verify -395.00. Calling -370.00 a balance *after* fee
assessment would be incorrect.

## 2 — Refuse one fee per backdated event

The limit is once **per account and day**, not once per triggering event.
Process the affected days chronologically because an earlier fee changes later
closing balances.

| Value day | Before all fees, current code accepts E6 | Before all fees, criterion 4 rejects E6 | Assessment |
| --- | ---: | ---: | --- |
| 2 | -370.00 | -370.00 | AED 25.00 |
| 3 | 30.00 | 30.00 | None: the Day 2 fee leaves +5.00. |
| 4 | -335.00 | -155.00 | AED 25.00; negative even before its own fee. |
| 5 | -335.00 | -155.00 | AED 25.00; negative after the earlier fees. |

Both settlement policies therefore yield three assessments totaling **AED 75.00**.
They are discovered and booked on Day 5, with value days 2, 4 and 5. E7's
booked day and the fees' value days must not be conflated.

## 3 — Accept Auth-A's settlement

At E3, the ledger is AED 250.00 and the AED 200.00 hold leaves AED 50.00
available, so Auth-A is approved. Before E5, E4 has raised the ledger to
AED 650.00. The AED 185.00 final settlement debits that amount and releases the
full AED 200.00 hold, leaving AED 465.00 before E6.

E7 arrives on Day 5. Its backdating cannot make the Day 4 system act on
information it did not yet have or rewrite the recorded authorization.
[SettlementsTest](src/test/java/accountledger/SettlementsTest.java) verifies
acceptance, full hold release and historical states.

## 4 — Accept the missing-authorization rejection rule

This criterion supplies a settlement rule and conflicts with none of the
non-negotiable rules. The brief does not state that a settlement is an
irrevocable external debit that must be honored despite a missing authorization.
There is no sufficient basis for refusing this criterion.

The current [Settlements](src/main/java/accountledger/Settlements.java) code
instead debits E6 and records MISSING_AUTHORIZATION. The test
`unmatchedAuthZPostsItsDebitAndRecordsTheAnomaly` explicitly expects that behavior.
Both encode our earlier choice; they do not establish the criterion as wrong.

**Required correction:** reject E6 through the existing rejection mechanism,
with no monetary posting. Update the settlement tests, scenario assertions,
expected output and dependent numbers. Criterion 4 says nothing about a
*declined but present* authorization; that remains a separate policy decision.

## 5 — Accept the conditional hold rule

An approved hold reduces available balance by its amount and creates no
monetary posting. A decline reserves nothing. In this stream Auth-B is declined
because the account is already negative when E8 arrives, whether E6 is accepted
or rejected.

The condition must still be tested with a funded account. Existing
[AuthorizationsTest](src/test/java/accountledger/AuthorizationsTest.java) cases
verify approved holds, unchanged ledger balances and the zero-availability boundary.

## 6 — Distinguish economic restoration from deleting history

E9 names E7. Negating E7's postings restores AED 620.00 of principal. It does
not, by itself, create compensation for fee postings in other entries. That is
why the current code retains AED 75.00 in fees.

However, append-only permits new compensating postings. It does not require
retaining fees economically. Crediting AED 25.00 at each affected value day
could restore net balances and net fees while leaving the three original fee
assessments and all original snapshots intact.

Therefore:

- If “all balances and fees” means their net economic effect before capitalization,
  a refund policy can satisfy it. Our implementation currently does not.
- If it means removing or changing the past assessment records or saved historical
  views, refuse that interpretation: it violates append-only.
- The causal fees, refund dates and audit presentation must be explicit. Retaining
  fees is a policy choice, not proof that the stated criterion is intrinsically wrong.

The existing deliberately failing test exposes this choice; it does not turn
it into a non-negotiable rule.

## 7 — Refuse three installments of BHD 3.334

```text
3 × 3.334 = 10.002 BHD
```

BHD 10.000 contains 10,000 minor units, which are not divisible by three.
The implemented split is **3.334, 3.333, 3.333**, totaling exactly 10.000.
Equal installments means as equal as the currency precision allows; the first
installment receives the remaining minor unit. [MoneySplitTest](src/test/java/accountledger/MoneySplitTest.java)
verifies conservation, precision and the corresponding negative split.

## 8 — Refuse discarding an interest difference

The brief explicitly requires the rounded daily accruals to sum exactly to
the capitalized credit. Define the credit as that sum; there is no extra
remainder to discard and no second calculation of a rounded unrounded total.

[InterestTest](src/test/java/accountledger/InterestTest.java) exercises daily
ties: six AED accruals of 0.02 total 0.12, whereas rounding their six unrounded
products together would give 0.09. The latter calculation cannot replace the
required sum. Equivalent BHD fixtures verify the three-decimal case.

## Numerical impact of the open policy issues

These are distinct outcomes, not three interchangeable expected outputs:

| Behavior | AED balance before capitalization | Rounded AED interest | Final AED balance |
| --- | ---: | ---: | ---: |
| Current code: debit E6, retain fees | 210.00 | 0.69 | 210.69 |
| Reject E6, retain fees | 390.00 | 0.93 | 390.93 |
| Reject E6, compensate the three fees at their original value days | 465.00 | 1.03 | 466.03 |

The audit verified the latter two as **counterfactual replays**, omitting E6's
financial effects and optionally adding fee credits after E9. They are not
implemented behavior. Both keep the original fee facts. BHD ends at 10.008 in
all three cases. The missing-authorization rule alone changes the AED result;
the fee-refund interpretation changes it again.

## Deliberately failing test

[FeeTimingCounterexample.java](src/test/java/accountledger/FeeTimingCounterexample.java)
asserts that reversal timing should not change the resulting balance. Run:

```sh
mvn -o -Dtest=FeeTimingCounterexample test
```

Verified during this audit: **one assertion failure, zero errors, nonzero exit**.
Expected AED 0.00, actual AED -25.00. Both paths cancel the same debit, but a fee
assessed before reversal remains charged. Its inline comment explains that
limitation. The test runs separately by name and is not disabled.

The ordinary suite passed **176 tests** during the same audit. Its success
confirms the implemented behavior; it does not establish compliance with
criterion 4 or resolve criterion 6.

## Deliverable audit

| Deliverable | Finding |
| --- | --- |
| Git history, no squashing | The local history and reflog showed 18 incremental commits at the start of this review, including a separate corrective commit. Preserve them when publishing. |
| README | Contains runnable commands, observed final output and an explicit statement that the per-day report is unfinished. |
| NUMBERS.md | Documents fee, rate, precision, window, build versions and fixture rationale. A check found all numeric string-literal magnitudes in the Java sources represented, normalizing signs and trailing zeros; this is a coverage aid, not proof of every rationale. |
| AMBIGUITIES.md | Substantial decisions are present. This audit marks the missing-authorization choice as non-compliant and the refund interpretation as unresolved. |
| REJECTED.md | This review covers all eight criteria and retains the actual abandoned approaches below. |
| Annotated failing test | Present and verified separately as described above. |
| WORKLOG.md | Contains timestamped activity records; this audit adds an entry using the observed UTC clock and actual checks. |
| Per-day output from the original Build requirement | **Missing.** Scenario prints a final summary, not daily closing balances, fee assessments, authorization states and errors. |
| GitHub repository | **Not verified.** No Git remote is configured locally. A local commit history does not demonstrate GitHub publication. |

The repository is **not ready for acceptance**: criterion 4 needs a code
correction, criterion 6 needs a precise refund interpretation, the per-day
report is unfinished, and GitHub publication remains unverified.

## Approaches abandoned during implementation

The first draft of available-balance calculation used `Stream.reduce` with
`Money::subtract`. Subtraction is not associative, so it does not satisfy the
reduction contract. Replaced it with a direct loop subtracting each approved
hold before running the tests. With the user's later stream preference, the
current implementation sums active holds using associative addition, then
subtracts that total once. It projects the latest authorization states first,
so a historical approval cannot keep reserving funds after settlement.

The first authorization component duplicated retry lookup and comparisons in
Journal and Authorizations. Replaced both paths with one comparison of immutable
Request inputs in Journal. Effects are computed only for new requests, so a
retry cannot turn a recorded decline into an approval after a later credit.

The initial available-balance API always used the latest sequence. That was
sufficient for an immediate authorization, but could not reconstruct old closes
after new postings or holds. Replaced it with an explicit cutoff shared by the
ledger balance and the recorded decisions.

Expected domain failures previously used generic IllegalArgumentException.
Replaced those with RequestRejectedException, preserving the existing exception
supertype while allowing Replay to catch only expected failures. Rejections are
recorded as attempts outside the accepted journal; they do not reserve event IDs
or become inputs to authorization, reversal or capitalization lifecycle queries.

The complete scenario originally existed only inside DailyClosingTest. Moved
its input sequence into the executable Scenario and changed that test to query
the resulting history. This keeps one scenario definition shared by execution
and numerical verification as the command-line runner is introduced.

The earlier acceptance analysis treated a compatible settlement requirement as
optional merely because the non-negotiable rules did not specify that behavior.
This audit withdraws that reasoning. A chosen policy that contradicts a valid
acceptance criterion is a compliance gap to fix, not a reason to call the
criterion wrong. The associated code has not yet been changed.
