# Acceptance audit and rejected criteria

This review checks the **eight acceptance criteria** against the brief, separately from what the code happens to do. 

That separation matters: a passing test shows the implementation matches the policy we chose, not that the policy satisfies the requirement.

## Verdict on every criterion

| # | Criterion | Verdict |
| --- | --- | --- |
| 1 | The Day 2 balance, as known at the end of Day 5 and before fees, is AED -370.00. | **Accept**, provided "before fees" is stated. |
| 2 | E7 causes exactly one overdraft fee, on Day 2. | **Refuse.** There are three, for value days 2, 4 and 5. |
| 3 | The Day 4 settlement of Auth-A must be accepted. | **Accept.** |
| 4 | A settlement whose authorization ID is absent must be refused, with no debit. | **Accept.** Implemented and tested. |
| 5 | If Auth-B is approved, its hold reduces availability but not the ledger balance. | **Accept.** It is a conditional, and Auth-B is declined in this stream. |
| 6 | After E9, all balances and fees return to their pre-E7 values. | **Refuse as an automatic consequence of E9.** It reverses E7's principal only; the three fees were assessed separately and remain. |
| 7 | E10's three installments must each be BHD 3.334. | **Refuse.** They would total BHD 10.002. |
| 8 | Discard any difference between the rounded accruals and the capitalized interest. | **Refuse.** It contradicts the required exact equality. |

Criteria **2, 7 and 8** are plain contradictions: the arithmetic decides them, **Criterion 6 is different.** It is refused because of how we scoped reversal, not because append-only makes it impossible

## 1. Accept the pre-fee Day 2 balance

Only E1, E2 and E7 touch ACC-001 on or before value Day 2, as known at the end of Day 5:

```text
1,200.00 - 950.00 - 620.00 = -370.00 AED
```

Auth-A's hold never enters the ledger balance. E4 and the settlements have later
value days, E9 is not yet known, and E8 posts nothing.

The qualification matters, because value Day 2 has three defensible readings:

| View of value Day 2 | AED balance |
| --- | ---: |
| The original Day 2 close | 250.00 |
| With E7 known, before fees are assessed | -370.00 |
| At the actual Day 5 close, fees included | -395.00 |

The scenario test keeps the original cutoff and checks the pre-fee figure, and
the audit separately queried the real Day 5 close to confirm -395.00. Calling
-370.00 a balance *after* fee assessment would simply be wrong.

## 2. Refuse one fee per backdated event

The limit is once per **account and day**, not once per triggering event. Days
are processed in order, because an earlier fee changes later closing balances.

| Value day | Under the abandoned policy (E6 debited) | Under the current policy (E6 refused) | Assessment |
| --- | ---: | ---: | --- |
| 2 | -370.00 | -370.00 | AED 25.00 |
| 3 | 30.00 | 30.00 | None: the Day 2 fee leaves +5.00 |
| 4 | -335.00 | -155.00 | AED 25.00, negative before its own fee |
| 5 | -335.00 | -155.00 | AED 25.00, negative after the earlier fees |

Both settlement policies give three assessments totalling **AED 75.00**. They
are discovered and booked on Day 5, with value days 2, 4 and 5. E7's booked day
and the fees' value days are different things and must not be merged.

## 3. Accept Auth-A's settlement

At E3 the ledger holds AED 250.00, and the AED 200.00 hold leaves AED 50.00
available, so Auth-A is approved. By E5, E4 has lifted the ledger to AED 650.00.
The AED 185.00 settlement debits that amount and releases the whole AED 200.00
hold, leaving AED 465.00 before E6.

E7 arrives on Day 5. Backdating it cannot make the Day 4 system act on
information it did not have, nor rewrite a decision already recorded.

## 4. Accept the missing-authorization rule

This criterion supplies a settlement rule and contradicts none of the brief's
fixed rules. The brief nowhere says a settlement is an irrevocable external
debit that must be honoured despite a missing authorization. There is no
sufficient basis for refusing it.

Settlements now refuses a reference that is absent from the requested account's
history. CommandProcessor logs E6's failure on Day 4, and no posting or entry is created:
the ledger and available balance both stay at AED 465.00 after E5 and the
refused E6.

A rejection claims neither the event ID nor the authorization reference, so a
later retry can succeed once the matching authorization exists.

Note the limit of this criterion: it says nothing about an authorization that is
*present but declined*. That case follows the separate policy documented in
[AMBIGUITIES.md](AMBIGUITIES.md): the attempt is recorded and flagged, but no debit is posted.

## 5. Accept the conditional hold rule

An approved hold reduces the available balance by its amount and posts nothing
to the ledger. A decline reserves nothing at all.

The criterion is a conditional, and in this stream its condition is false:
Auth-B is declined because the account is already negative when E8 arrives,
and it is negative whether E6 is debited or refused. The rule itself is still tested
directly, on a funded account, in the authorization tests.

## 6. Refuse automatic restoration of balances and fees

E9 names E7 explicitly. Under the reversal scope we adopted, it negates E7's
postings and restores AED 620.00 of principal. The three fees live in separate
entries and keep their AED 75.00. The balance before capitalization becomes
AED 390.00, against AED 465.00 before E7 arrived.

The brief specifies how fees are assessed but gives no automatic refund policy.
We read this criterion as claiming that reversing E7 *by itself* restores
everything, and we refuse that claim rather than adding compensations to make it
true.

The important qualification: **append-only does not force this outcome.** It
would happily accept explicit compensating credits, and a different policy could
satisfy a net-refund reading while leaving the original assessments in place.
What append-only does forbid is deleting those records or altering an earlier
saved view. The refusal is a policy decision, not a technical limit, and a fee
refund component is not outstanding work.

The deliberately failing test, described in
[README.md](README.md#tests-and-the-deliberate-counterexample), exists to keep
this decision honest by showing what it costs. The daily report makes the same point: the original Day 5
close of AED -230.00 sits beside its revised AED 390.00 balance, because E9 does
not rewrite history.

## 7. Refuse three installments of BHD 3.334

```text
3 × 3.334 = 10.002 BHD
```

BHD 10.000 is 10,000 minor units, which do not divide by three. The
implementation splits it **3.334, 3.333, 3.333**, totalling exactly 10.000.
Equal installments can only mean as equal as the currency allows, with the
first installment taking the spare minor unit.

## 8. Refuse discarding an interest difference

The brief requires the rounded daily accruals to add up exactly to the
capitalized credit. So the credit *is* that sum: there is no leftover to
discard, and no second calculation to reconcile against.

The interest tests exercise the case where this matters. A constant balance of
AED 37.50 earns 0.015 a day, rounded to 0.02; six of those total 0.12, whereas
rounding the six unrounded products together gives 0.09. The second figure cannot stand in for the first. Equivalent dinar
fixtures cover the three-decimal case.

## What the two contested choices are worth

Criterion 4 and criterion 6 each change the final number. These are three
distinct outcomes, not three readings of one result:

| Behaviour | AED before capitalization | Rounded AED interest | Final AED |
| --- | ---: | ---: | ---: |
| Abandoned: debit E6, keep fees | 210.00 | 0.69 | 210.69 |
| **Current: refuse E6, keep fees** | **390.00** | **0.93** | **390.93** |
| Refuse E6, refund the three fees at their original value days | 465.00 | 1.03 | 466.03 |


